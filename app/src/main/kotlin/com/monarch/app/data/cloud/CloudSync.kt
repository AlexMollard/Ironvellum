package com.monarch.app.data.cloud

import com.monarch.app.data.Repository
import com.monarch.app.data.cloud.Cloud.failure
import com.monarch.app.domain.PlayerProfile
import com.monarch.app.domain.Titles
import com.monarch.app.domain.Xp
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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
    suspend fun push(): Result<SyncOutcome> {
        val me = requireAccount(account).getOrElse { return failure(it) }
        val client = Cloud.requireConfigured.getOrElse { return failure(it) }
        return runCatching {
            val profile = repo.observeProfile().first() ?: PlayerProfile()
            val history = repo.observeHistory().first()
            val titles = repo.observeUnlockedTitles().first()

            val completed = history.filter { (session, _) -> session.completedAtMs != null }
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
            val sessionDtos = completed.map { (session, _) ->
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
            val cloudIds = client.postgrest.from("sessions").select {
                filter { eq("user_id", me.userId) }
            }.decodeList<SessionIdDto>().associate { it.localId to it.id }

            var setCount = 0
            val setDtos = buildList {
                completed.forEach { (session, sets) ->
                    val cloudId = cloudIds[session.id]
                    if (cloudId == null) {
                        problems += "Session \"${session.label}\" could not be matched on the cloud"
                        return@forEach
                    }
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
            // ilike without % wildcards is a case-insensitive exact match,
            // mirroring the DB's unique index on lower(display_name).
            val matches = client.postgrest.from("profiles").select {
                filter { ilike("display_name", name) }
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
     * per scroll position and each is requested once anyway. Like taps update
     * the cached copy in place, so counts stay truthful inside the TTL.
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
                )
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
            }
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
            // Optimistic: patch the cached first page instead of refetching it.
            cache.updateFeedPage { entry ->
                if (entry.sessionId != sessionId || entry.likedByMe) {
                    entry
                } else {
                    entry.copy(likedByMe = true, likeCount = entry.likeCount + 1)
                }
            }
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
            cache.updateFeedPage { entry ->
                if (entry.sessionId != sessionId || !entry.likedByMe) {
                    entry
                } else {
                    entry.copy(likedByMe = false, likeCount = (entry.likeCount - 1).coerceAtLeast(0))
                }
            }
            Unit
        }.recoverCatching { error ->
            throw IllegalStateException(Cloud.explain(error))
        }
    }

    /**
     * Who liked a session, newest first. One PostgREST embed
     * (session_likes -> profiles) so the owner sees names without a second
     * round trip. An RLS refusal is a visibility answer, worded as such.
     */
    suspend fun likers(sessionId: String): Result<List<Liker>> {
        val client = Cloud.requireConfigured.getOrElse { return failure(it) }
        return runCatching {
            client.postgrest.from("session_likes").select(
                Columns.raw("user_id, created_at, profiles(display_name)"),
            ) {
                filter { eq("session_id", sessionId) }
                order("created_at", Order.DESCENDING)
            }.decodeList<LikerRowDto>().map { row ->
                Liker(
                    userId = row.userId,
                    displayName = row.profile?.displayName ?: "Hidden hunter",
                    likedAtMs = Instant.parse(row.createdAt).toEpochMilli(),
                )
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
}

/**
 * In-memory TTL cache with single-flight for the social reads. Deliberately
 * process-local and tiny: it exists to stop the UI spamming PostgREST on
 * every recomposition/screen entry, not to be a source of truth.
 *
 * Per-key slots hold either a timed [Entry] or an in-flight
 * [CompletableDeferred]; concurrent identical calls await the deferred, so a
 * paging trigger and a pull-to-refresh share ONE request instead of issuing N.
 */
private class CloudReadCache {
    companion object {
        const val KEY_LEADERBOARD = "leaderboard"
        const val KEY_FRIENDS = "friends"
        const val KEY_FEED_FIRST_PAGE = "feed:first"
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
                slots.clear()
                ownerUserId = userId
            }
            val existing = slots[key]
            when {
                // Someone else's request is in the air: await it below rather
                // than issuing a duplicate — that is the single-flight.
                existing is CompletableDeferred<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    joined = existing as CompletableDeferred<Any?>
                    existing
                }
                existing is Entry && !force && existing.expiresAtMs > System.currentTimeMillis() -> {
                    @Suppress("UNCHECKED_CAST")
                    return existing.value as T
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

    /** Patch the cached feed page in place (optimistic like counts). */
    suspend fun updateFeedPage(transform: (FeedEntry) -> FeedEntry) {
        lock.withLock {
            val entry = slots[KEY_FEED_FIRST_PAGE] as? Entry ?: return
            @Suppress("UNCHECKED_CAST")
            val list = entry.value as? List<FeedEntry> ?: return
            slots[KEY_FEED_FIRST_PAGE] = Entry(list.map(transform), entry.expiresAtMs)
        }
    }
}

