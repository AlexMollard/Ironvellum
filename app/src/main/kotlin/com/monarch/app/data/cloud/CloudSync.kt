package com.monarch.app.data.cloud

import com.monarch.app.data.Repository
import com.monarch.app.data.cloud.Cloud.failure
import com.monarch.app.domain.PlayerProfile
import com.monarch.app.domain.SessionSet
import com.monarch.app.domain.Titles
import com.monarch.app.domain.Xp
import com.monarch.app.domain.WorkoutSession
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Objects
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Everything that leaves the device through PostgREST: aggregates, completed
 * measurements (StatEntry) and HealthDay rows — the cloud schema has no table
 * for them on purpose.
 */
class CloudSync(
    private val repo: Repository,
    private val account: AccountRepository,
) {
    // Read cache + single-flight for the social reads. See CloudReadCache.
    private val cache = CloudReadCache()
    // Push watermark lives in Room (`sync_state`), read per push below.

    suspend fun push(): Result<SyncOutcome> {
        val me = requireAccount(account).getOrElse { return failure(it) }
        val client = Cloud.requireConfigured.getOrElse { return failure(it) }
        return runCatching {
            val profile = repo.observeProfile().first() ?: PlayerProfile()
            val history = repo.observeHistory().first()
            val titles = repo.observeUnlockedTitles().first()

            val completed = history.filter { (session, _) -> session.completedAtMs != null }
            // Push only what changed since the last successful sync: a new
            // session differs from "never pushed", an edited one from its old
            // fingerprint — so edits re-push without any updated-at column.
            val watermark = repo.pushWatermark()
            val pending = completed.filter { (session, sets) ->
                watermark[session.id] != pushFingerprint(session, sets)
            }
            val level = Xp.progress(profile.totalXp).level
            val streakDays = Titles.trainingStreakDays(completedDates(completed.map { it.first }))
            val lifetimeStrength = history.sumOf { (session, _) -> session.strengthScore.toLong() }

            // Aggregates only: level, xp, streak, title count, lifetime strength.
            client.postgrest.from("profiles").upsert(
                ProfileDto(
                    id = me.userId,
                    displayName = me.displayName,
                    visibility = me.visibility,
                    level = level,
                    totalXp = profile.totalXp,
                    streakDays = streakDays,
                    titlesCount = titles.size,
                    lifetimeStrength = lifetimeStrength,
                    currentTitleId = profile.currentTitleId,
                ),
            ) {
                onConflict = "id"
            }

            // Sessions first so their cloud ids exist before the sets land.
            val problems = mutableListOf<String>()
            val pushedNow = mutableListOf<Pair<WorkoutSession, List<SessionSet>>>()
            val sessionDtos = pending.map { (session, _) ->
                SessionDto(
                    userId = me.userId,
                    localId = session.id,
                    label = session.label,
                    startedAt = Instant.ofEpochMilli(session.startedAtMs).toString(),
                    completedAt = session.completedAtMs?.let { Instant.ofEpochMilli(it).toString() },
                    xpAwarded = session.xpAwarded,
                    strengthScore = session.strengthScore,
                    title = session.title,
                    note = session.note,
                )
            }
            if (sessionDtos.isNotEmpty()) {
                client.postgrest.from("sessions").upsert(
                    sessionDtos,
                ) {
                    onConflict = "user_id,local_id"
                }
            }
            // Nothing changed means no ids to resolve; an empty isIn() filter
            // is not a valid PostgREST query, so skip the round trip outright.
            val pendingIds = pending.map { it.first.id }
            val cloudIds = if (pendingIds.isEmpty()) {
                emptyMap()
            } else {
                client.postgrest.from("sessions").select {
                    filter {
                        eq("user_id", me.userId)
                        // Only the ids this push actually needs — the old
                        // unfiltered select re-downloaded every session row.
                        isIn("local_id", pendingIds)
                    }
                }.decodeList<SessionIdDto>().associate { it.localId to it.id }
            }

            var setCount = 0
            val setDtos = buildList {
                pending.forEach { (session, sets) ->
                    val cloudId = cloudIds[session.id]
                    if (cloudId == null) {
                        problems += "Session \"${session.label}\" could not be matched on the cloud"
                        return@forEach
                    }
                    pushedNow += session to sets
                    sets.forEach { set ->
                        if (set.exerciseName.isBlank()) {
                            problems += "A set in \"${session.label}\" has no exercise name and was skipped"
                            return@forEach
                        }
                        setCount++
                        add(
                            SessionSetDto(
                                sessionId = cloudId,
                                exerciseName = set.exerciseName,
                                setIndex = set.setIndex,
                                reps = set.reps,
                                weightKg = set.weightKg,
                                modifiers = set.modifiers,
                                done = set.done,
                            ),
                        )
                    }
                }
            }
            if (setDtos.isNotEmpty()) {
                client.postgrest.from("session_sets").upsert(
                    setDtos,
                ) {
                    onConflict = "session_id,exercise_name,set_index"
                }
            }

            // Watermark is PERSISTED, not process-local: an in-memory map made
            // every cold start re-upload the whole completed history. Written
            // only here, after the upserts above succeeded, so a failed push
            // re-pushes; rows for locally deleted sessions are pruned.
            val advanced = watermark + pushedNow.associate { (session, sets) ->
                session.id to pushFingerprint(session, sets)
            }
            repo.recordPushWatermark(
                advanced.filterKeys { id -> completed.any { it.first.id == id } },
            )

            val titleDtos = titles.map {
                EarnedTitleDto(
                    userId = me.userId,
                    titleId = it.titleId,
                    unlockedAt = Instant.ofEpochMilli(it.unlockedAtMs).toString(),
                )
            }
            if (titleDtos.isNotEmpty()) {
                client.postgrest.from("earned_titles").upsert(
                    titleDtos,
                ) {
                    onConflict = "user_id,title_id"
                }
            }

            SyncOutcome(
                sessions = sessionDtos.size,
                sets = setCount,
                titles = titleDtos.size,
                problems = problems,
            )
        }.onSuccess {
            // Our own aggregates just changed; cached leaderboard/feed rows
            // would now show stale level/xp/title for this hunter.
            cache.invalidate(CloudReadCache.KEY_LEADERBOARD)
            cache.invalidate(CloudReadCache.KEY_FEED_FIRST_PAGE)
        }.recoverCatching { error ->
            throw IllegalStateException(Cloud.explain(error))
        }
    }

    /**
     * TTL 60s: the leaderboard changes only when someone pushes a session or
     * levels up — never per second. Re-entering the board screen twice in a
     * minute is the common case, so one request serves it.
     */
    suspend fun leaderboard(force: Boolean = false): Result<List<LeaderboardRow>> {
        val client = Cloud.requireConfigured.getOrElse { return failure(it) }
        return runCatching {
            cache.getOrFetch(
                key = CloudReadCache.KEY_LEADERBOARD,
                ttlMs = 60_000,
                force = force,
                userId = account.account.value?.userId,
            ) {
                client.postgrest.from("leaderboard").select {
                    order("total_xp", Order.DESCENDING)
                    limit(100)
                }.decodeList<LeaderboardDto>().map {
                    LeaderboardRow(
                        userId = it.id,
                        displayName = it.displayName,
                        level = it.level,
                        totalXp = it.totalXp,
                        streakDays = it.streakDays,
                        titlesCount = it.titlesCount,
                        lifetimeStrength = it.lifetimeStrength,
                        sessionsLast7d = it.sessionsLast7d,
                        // Names resolve locally via Titles.byId — id only.
                        currentTitleId = it.currentTitleId,
                    )
                }
            }
        }.recoverCatching { error ->
            throw IllegalStateException(Cloud.explain(error))
        }
    }

    /**
     * TTL 30s: the friends list changes when someone requests/accepts —
     * events that invalidate it locally anyway — so within the TTL it can
     * only grow stale by remote actions we did not hear about. Short enough
     * that an accepted request from the other side appears promptly.
     */
    suspend fun friends(force: Boolean = false): Result<List<FriendRow>> {
        val me = requireAccount(account).getOrElse { return failure(it) }
        val client = Cloud.requireConfigured.getOrElse { return failure(it) }
        return runCatching {
            cache.getOrFetch(
                key = CloudReadCache.KEY_FRIENDS,
                ttlMs = 30_000,
                force = force,
                userId = me.userId,
            ) {
                // RLS already shows only rows where we are a party; the
                // or-filter just keeps the decode explicit.
                val rows = client.postgrest.from("friendships").select {
                    filter {
                        or {
                            eq("requester_id", me.userId)
                            eq("addressee_id", me.userId)
                        }
                    }
                }.decodeList<FriendshipDto>()

                val counterpartIds = rows.map {
                    if (it.requesterId == me.userId) it.addresseeId else it.requesterId
                }
                // Pending profiles of "friends"-visibility hunters may be
                // hidden from us — those decode as a fallback name instead
                // of failing.
                val names = if (counterpartIds.isEmpty()) {
                    emptyMap()
                } else {
                    client.postgrest.from("profiles").select {
                        filter { isIn("id", counterpartIds) }
                    }.decodeList<ProfileNameDto>().associate { it.id to it.displayName }
                }
                rows.map { row ->
                    val other = if (row.requesterId == me.userId) row.addresseeId else row.requesterId
                    FriendRow(
                        userId = other,
                        displayName = names[other] ?: "Hidden hunter",
                        accepted = row.accepted,
                        // Incoming = they asked us and it is not accepted yet.
                        incoming = row.addresseeId == me.userId && !row.accepted,
                    )
                }
            }
        }.recoverCatching { error ->
            throw IllegalStateException(Cloud.explain(error))
        }
    }

    suspend fun requestFriend(displayName: String): Result<Unit> {
        val me = requireAccount(account).getOrElse { return failure(it) }
        val client = Cloud.requireConfigured.getOrElse { return failure(it) }
        val name = displayName.trim()
        if (name.isEmpty()) {
            return Result.failure(IllegalStateException("Type a hunter's name first"))
        }
        return runCatching {
            // ilike treats % _ and \\ as wildcards/escapes; a raw name like
            // "50%er" would match half the roster — an over-broad download
            // that also lets a wildcard name probe which profiles exist.
            // Escaping keeps this a literal case-insensitive match (the exact
            // post-filter below still confirms the one intended hunter).
            val escaped = name
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_")
            val matches = client.postgrest.from("profiles").select {
                filter { ilike("display_name", escaped) }
            }.decodeList<ProfileNameDto>()
            val exact = matches.firstOrNull { it.displayName.equals(name, ignoreCase = true) }
                ?: throw IllegalStateException("No hunter is named \"$name\"")
            if (exact.id == me.userId) {
                throw IllegalStateException("You cannot send yourself a friend request")
            }
            client.postgrest.from("friendships").insert(
                FriendshipDto(requesterId = me.userId, addresseeId = exact.id, accepted = false),
            )
            Unit
        }.onSuccess {
            cache.invalidate(CloudReadCache.KEY_FRIENDS)
        }.recoverCatching { error ->
            if (error is PostgrestRestException && error.code == "23505") {
                throw IllegalStateException("Already requested — waiting for \"$name\" to accept")
            }
            throw IllegalStateException(Cloud.explain(error))
        }
    }

    /**
     * Same semantics as [requestFriend], but keyed by id for feed
     * tap-through (a row we can see always carries its user_id; its name may
     * be hidden). An existing friendship/request row is success — the tap is
     * idempotent, not an error the player must dismiss.
     */
    suspend fun requestFriendById(userId: String): Result<Unit> {
        val me = requireAccount(account).getOrElse { return failure(it) }
        val client = Cloud.requireConfigured.getOrElse { return failure(it) }
        if (userId == me.userId) {
            return Result.failure(IllegalStateException("You cannot send yourself a friend request"))
        }
        return runCatching {
            client.postgrest.from("friendships").insert(
                FriendshipDto(requesterId = me.userId, addresseeId = userId, accepted = false),
            )
            Unit
        }.onSuccess {
            cache.invalidate(CloudReadCache.KEY_FRIENDS)
        }.recoverCatching { error ->
            if (error is PostgrestRestException && error.code == "23505") {
                Unit // Already requested or already friends — nothing to do.
            } else {
                throw IllegalStateException(Cloud.explain(error))
            }
        }
    }

    suspend fun acceptFriend(userId: String): Result<Unit> {
        val me = requireAccount(account).getOrElse { return failure(it) }
        val client = Cloud.requireConfigured.getOrElse { return failure(it) }
        return runCatching {
            // RLS: only the addressee may flip accepted. Asking the rows back
            // tells us whether anything actually changed.
            val updated = client.postgrest.from("friendships").update(
                {
                    set("accepted", true)
                },
            ) {
                filter {
                    eq("requester_id", userId)
                    eq("addressee_id", me.userId)
                }
                select()
            }.decodeList<FriendshipDto>()
            if (updated.isEmpty()) {
                throw IllegalStateException("No pending request from that hunter")
            }
        }.onSuccess {
            cache.invalidate(CloudReadCache.KEY_FRIENDS)
        }.recoverCatching { error ->
            throw IllegalStateException(Cloud.explain(error))
        }
    }

    suspend fun friendSessions(userId: String, limit: Int = 20): Result<List<FriendSession>> {
        val client = Cloud.requireConfigured.getOrElse { return failure(it) }
        return runCatching {
            client.postgrest.from("sessions").select(Columns.raw("*, session_sets(count)")) {
                // NULL comparison in SQL filters out uncompleted sessions.
                // Scoped to this hunter — without the user_id term the filter
                // returned EVERY visible hunter's sessions under the tapped
                // row (the on-device defect).
                filter {
                    eq("user_id", userId)
                    gt("completed_at", "1970-01-02T00:00:00Z")
                }
                order("completed_at", Order.DESCENDING)
                limit(limit.toLong())
            }.decodeList<FriendSessionDto>()
        }.map { list ->
            // Empty under RLS = this hunter does not share with you (or truly
            // has no sessions) — that is data, not an error; the UI words it
            // as "not shared with you".
            list.map { dto ->
                FriendSession(
                    label = dto.label,
                    title = dto.title,
                    note = dto.note,
                    completedAtMs = dto.completedAt?.let { Instant.parse(it).toEpochMilli() },
                    xpAwarded = dto.xpAwarded,
                    strengthScore = dto.strengthScore,
                    sets = dto.setCounts.sumOf { it.count }.toInt(),
                )
            }
        }.recoverCatching { error ->
            throw IllegalStateException(Cloud.explain(error))
        }
    }

    /**
     * The public feed: everyone's workouts the caller's RLS allows them to
     * see, newest first. `beforeMs` paginates: fetch the page older than it.
     * Private notes never appear here — they never reach the server.
     *
     * TTL 20s on the FIRST page only: the feed is browsed by scrolling back
     * and forth, and a screen re-entry or tab switch within 20s should not
     * re-download 50 rows. Older pages are not cached — they are append-only
     * per scroll position and each is requested once anyway. Like taps are
     * recorded as an overlay applied to every handed-out page, so older
     * (paged) entries reflect the optimistic state too, not just page one.
     */
    suspend fun feed(
        limit: Int = 50,
        beforeMs: Long? = null,
        force: Boolean = false,
    ): Result<List<FeedEntry>> {
        val client = Cloud.requireConfigured.getOrElse { return failure(it) }
        val me = account.account.value
        val fetch: suspend () -> List<FeedEntry> = {
            client.postgrest.from("public_feed").select {
                if (beforeMs != null) {
                    filter { lt("completed_at", Instant.ofEpochMilli(beforeMs).toString()) }
                }
                order("completed_at", Order.DESCENDING)
                limit(limit.toLong())
            }.decodeList<FeedEntryDto>().map { dto ->
                FeedEntry(
                    sessionId = dto.sessionId,
                    userId = dto.userId,
                    displayName = dto.displayName,
                    level = dto.level,
                    title = dto.title,
                    note = dto.note,
                    label = dto.label,
                    completedAtMs = dto.completedAt?.let { Instant.parse(it).toEpochMilli() },
                    xpAwarded = dto.xpAwarded,
                    strengthScore = dto.strengthScore,
                    setsDone = dto.setsDone,
                    repsDone = dto.repsDone.toInt(),
                    currentTitleId = dto.currentTitleId,
                    likeCount = dto.likeCount,
                    likedByMe = dto.likedByMe,
                    topMovements = dto.topMovements,
                    bestSet = dto.bestSet,
                    movementCount = dto.movementCount,
                    durationSec = dto.durationSec,
                )
            }.let { fresh ->
                // The server's own values are the truth: drop any overlay
                // entry it already reflects before the page is cached.
                cache.reconcileLikes(fresh)
                fresh
            }
        }
        return runCatching {
            if (beforeMs == null) {
                cache.getOrFetch(
                    key = CloudReadCache.KEY_FEED_FIRST_PAGE,
                    ttlMs = 20_000,
                    force = force,
                    userId = me?.userId,
                    fetch = fetch,
                )
            } else {
                fetch()
            }.let { cache.applyLikes(it) }
        }.recoverCatching { error ->
            throw IllegalStateException(Cloud.explain(error))
        }
    }

    /** Idempotent: an upsert on (session_id, user_id) makes a double tap a no-op, never an error. */
    suspend fun like(sessionId: String): Result<Unit> {
        val me = requireAccount(account).getOrElse { return failure(it) }
        val client = Cloud.requireConfigured.getOrElse { return failure(it) }
        return runCatching {
            client.postgrest.from("session_likes").upsert(
                SessionLikeDto(sessionId = sessionId, userId = me.userId),
            ) {
                onConflict = "session_id,user_id"
            }
            // Optimistic: record the overlay the feed applies at hand-out —
            // this also covers older paged entries, which are never cached.
            cache.recordLike(sessionId, liked = true)
            // The owner's "WHO CHEERED" dialog must show the fresh cheer,
            // not a 30s-old cached list.
            cache.invalidate("${CloudReadCache.KEY_LIKERS}:$sessionId")
            Unit
        }.recoverCatching { error ->
            throw IllegalStateException(Cloud.explain(error))
        }
    }

    suspend fun unlike(sessionId: String): Result<Unit> {
        val me = requireAccount(account).getOrElse { return failure(it) }
        val client = Cloud.requireConfigured.getOrElse { return failure(it) }
        return runCatching {
            client.postgrest.from("session_likes").delete {
                filter {
                    eq("session_id", sessionId)
                    eq("user_id", me.userId)
                }
            }
            cache.recordLike(sessionId, liked = false)
            cache.invalidate("${CloudReadCache.KEY_LIKERS}:$sessionId")
            Unit
        }.recoverCatching { error ->
            throw IllegalStateException(Cloud.explain(error))
        }
    }

    /**
     * Who liked a session, newest first. One PostgREST embed
     * (session_likes -> profiles) so the owner sees names without a second
     * round trip. An RLS refusal is a visibility answer, worded as such.
     *
     * TTL 30s, same reasoning as [friends]: likes arrive only when someone
     * acts, and our own like/unlike invalidates the key immediately, so a
     * just-added cheer is visible the moment the owner opens the dialog
     * while repeated dialog opens inside 30s share one request.
     */
    suspend fun likers(sessionId: String): Result<List<Liker>> {
        val client = Cloud.requireConfigured.getOrElse { return failure(it) }
        return runCatching {
            cache.getOrFetch(
                key = "${CloudReadCache.KEY_LIKERS}:$sessionId",
                ttlMs = 30_000,
                force = false,
                userId = account.account.value?.userId,
            ) {
                client.postgrest.from("session_likes").select(
                    Columns.raw("user_id, created_at, profiles(display_name)"),
                ) {
                    filter { eq("session_id", sessionId) }
                    order("created_at", Order.DESCENDING)
                    // The list is a name wall, not a ledger; a cap keeps the
                    // payload bounded no matter how viral a session gets.
                    limit(50)
                }.decodeList<LikerRowDto>().map { row ->
                    Liker(
                        userId = row.userId,
                        displayName = row.profile?.displayName ?: "Hidden hunter",
                        likedAtMs = Instant.parse(row.createdAt).toEpochMilli(),
                    )
                }
            }
        }.recoverCatching { error ->
            if (error is PostgrestRestException && error.code == "42501") {
                throw IllegalStateException("These likes are not visible to you")
            }
            throw IllegalStateException(Cloud.explain(error))
        }
    }

    private fun completedDates(completed: List<com.monarch.app.domain.WorkoutSession>): Set<LocalDate> =
        completed.mapNotNull { session ->
            session.completedAtMs?.let {
                Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
            }
        }.toSet()

    /**
     * Content fingerprint of one completed session — every field the push
     * uploads, sets included. Equality with the stored watermark means "the
     * cloud already holds exactly this", so the session is skipped.
     */
    private fun pushFingerprint(session: WorkoutSession, sets: List<SessionSet>): Int = Objects.hash(
        session.label,
        session.title,
        session.note,
        session.completedAtMs,
        session.xpAwarded,
        session.strengthScore,
        sets.map { set ->
            listOf(set.exerciseName, set.setIndex, set.reps, set.weightKg, set.modifiers, set.done)
        },
    )
}

/**
 * In-memory TTL cache with single-flight for the social reads. Deliberately
 * process-local and tiny: it exists to stop the UI spamming PostgREST on
 * every recomposition/screen entry, not to be a source of truth.
 *
 * Per-key slots hold either a timed [Entry] or an in-flight
 * [CompletableDeferred]; concurrent identical non-forced calls await the
 * deferred, so a paging trigger and a re-entry share ONE request instead of
 * issuing N. A forced (pull-to-refresh) caller never joins — see [getOrFetch].
 */
private class CloudReadCache {
    companion object {
        const val KEY_LEADERBOARD = "leaderboard"
        const val KEY_FRIENDS = "friends"
        const val KEY_FEED_FIRST_PAGE = "feed:first"
        const val KEY_LIKERS = "likers"
    }

    private class Entry(val value: Any?, val expiresAtMs: Long)

    private val lock = Mutex()
    // Any = Entry (settled) or CompletableDeferred<Any?> (in flight).
    private val slots = HashMap<String, Any>()
    // The account the current slots were minted under. RLS answers are
    // user-scoped, so a different (or signed-out) account must never see them.
    private var ownerUserId: String? = null

    suspend fun <T> getOrFetch(
        key: String,
        ttlMs: Long,
        force: Boolean,
        userId: String?,
        fetch: suspend () -> T,
    ): T {
        var joined: CompletableDeferred<Any?>? = null
        val mine: CompletableDeferred<Any?> = lock.withLock {
            if (ownerUserId != userId) {
                // Account changed (sign-in/sign-out/switch): wipe everything.
                // The like overlay is user-scoped optimism too — a switched
                // account must not inherit the previous one's taps.
                likeOverlay.clear()
                slots.clear()
                ownerUserId = userId
            }
            val existing = slots[key]
            when {
                existing is Entry && !force && existing.expiresAtMs > System.currentTimeMillis() -> {
                    @Suppress("UNCHECKED_CAST")
                    return existing.value as T
                }
                // A forced caller NEVER joins an in-flight request: that
                // request may predate the refresh, and joining would hand the
                // refresher exactly the pre-refresh result it came to replace
                // (the pull-to-refresh defect). Install a fresh slot; the old
                // deferred keeps serving its own waiters, and its settlement
                // no-ops below because the slot is no longer "ours".
                force -> CompletableDeferred<Any?>().also { slots[key] = it }
                // Non-forced single-flight: someone else's request is in the
                // air, so await it instead of issuing a duplicate.
                existing is CompletableDeferred<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    joined = existing as CompletableDeferred<Any?>
                    existing
                }
                else -> CompletableDeferred<Any?>().also { slots[key] = it }
            }
        }
        if (joined != null) {
            @Suppress("UNCHECKED_CAST")
            return joined.await() as T
        }
        return try {
            val value = fetch()
            lock.withLock {
                // Only settle if the slot is still ours (an account switch
                // may have cleared it while the request was in the air).
                if (slots[key] === mine) {
                    slots[key] = Entry(value, System.currentTimeMillis() + ttlMs)
                }
            }
            mine.complete(value)
            @Suppress("UNCHECKED_CAST")
            value as T
        } catch (error: Throwable) {
            mine.completeExceptionally(error)
            lock.withLock { if (slots[key] === mine) slots.remove(key) }
            throw error
        }
    }

    suspend fun invalidate(key: String) {
        lock.withLock { slots.remove(key) }
    }

    // sessionId -> (count delta, likedByMe): the optimistic like state for
    // feed entries the cache does not hold — older paged pages are never
    // cached, so the overlay is the only way their taps show immediately.
    private val likeOverlay = HashMap<String, Pair<Int, Boolean>>()

    suspend fun recordLike(sessionId: String, liked: Boolean) {
        lock.withLock {
            val current = likeOverlay[sessionId]
            val delta = when {
                // Toggling back to the server's last-seen state cancels out.
                current == null -> if (liked) 1 else -1
                current.second == liked -> current.first
                liked -> current.first + 1
                else -> current.first - 1
            }
            likeOverlay[sessionId] = delta to liked
        }
    }

    /** Drop overlay entries a fresh page already reflects: the server caught up. */
    suspend fun reconcileLikes(entries: List<FeedEntry>) {
        lock.withLock {
            entries.forEach { entry ->
                val overlay = likeOverlay[entry.sessionId] ?: return@forEach
                if (entry.likedByMe == overlay.second) likeOverlay.remove(entry.sessionId)
            }
        }
    }

    /** Apply the overlay at hand-out so paged entries mirror the taps too. */
    suspend fun applyLikes(entries: List<FeedEntry>): List<FeedEntry> {
        lock.withLock {
            if (likeOverlay.isEmpty()) return entries
            return entries.map { entry ->
                val overlay = likeOverlay[entry.sessionId]
                if (overlay == null || entry.likedByMe == overlay.second) {
                    entry
                } else {
                    entry.copy(
                        likedByMe = overlay.second,
                        likeCount = (entry.likeCount + overlay.first).coerceAtLeast(0),
                    )
                }
            }
        }
    }
}

