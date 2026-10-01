package com.ironvellum.app.data.cloud

import com.ironvellum.app.data.Repository
import com.ironvellum.app.data.cloud.Cloud.failure
import com.ironvellum.app.domain.LiftBoards
import com.ironvellum.app.domain.PlayerProfile
import com.ironvellum.app.domain.Warband
import com.ironvellum.app.domain.isValidInviteCode
import com.ironvellum.app.domain.SetRecords
import com.ironvellum.app.domain.SessionSet
import com.ironvellum.app.domain.Titles
import com.ironvellum.app.domain.Xp
import com.ironvellum.app.domain.WorkoutSession
import com.ironvellum.app.domain.SessionAudience
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Objects
import kotlinx.serialization.json.put
import kotlinx.serialization.json.buildJsonObject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Single source of truth for which sets are cloud-eligible. A set with a blank
 * exercise name cannot satisfy the session_sets conflict key, so pushing it
 * would be refused; both the set loop and the watermark decision route through
 * here so a skipped set can never be silently marked as pushed.
 */
internal fun partitionPushable(sets: List<SessionSet>): Pair<List<SessionSet>, List<SessionSet>> =
    sets.partition { it.exerciseName.isNotBlank() }

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

    private val _inboxUnread = MutableStateFlow(0)

    /**
     * Unread inbox items for the Allies nav dot. Kept current by [inbox] and
     * [markInboxSeen]; 0 when signed out or never fetched.
     */
    val inboxUnread: StateFlow<Int> = _inboxUnread.asStateFlow()

    init {
        // Any account change (sign-out, delete, switch, backend swap) zeroes
        // the count: the next lifter on this phone must not see the previous
        // one's dot. CloudSync lives as long as the app, so does this scope.
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            account.account.map { it?.userId }.distinctUntilChanged().collect {
                _inboxUnread.value = 0
            }
        }
    }

    /**
     * Forgets the push watermark so the next [push] re-uploads everything.
     *
     * Call after anything that empties the cloud. The watermark is a claim
     * about the SERVER's contents held on the DEVICE, so a server-side wipe
     * silently invalidates it and the client would otherwise skip every row.
     */
    suspend fun forgetPushedState() = repo.clearPushWatermark()

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
            //
            // The watermark is the device's claim about what the SERVER holds,
            // and the server can lose rows without the device hearing of it: a
            // project reset, a new account on this phone, a manual delete. So
            // it only counts for sessions the server still has; the rest push.
            val onServer = serverLocalIds(client, me.userId)
            val watermark = repo.pushWatermark().filterKeys { it in onServer }
            val pending = pendingForPush(completed, watermark)
            val level = Xp.progress(profile.totalXp).level
            // Same rule as Today and the deeds: rest days are rest, so the
            // leaderboard cannot publish a different streak from the one the
            // lifter can see at home.
            val streakDays = Titles.trainingStreakDays(
                completedDates(completed.map { it.first }),
            )
            val lifetimeStrength = history.sumOf { (session, _) -> session.strengthScore.toLong() }

            val problems = mutableListOf<String>()
            // The worn title is the only profile column push owns. The name
            // and visibility have their own flows, and the row itself is made
            // by the server at sign-up: clients hold no INSERT on profiles, so
            // the upsert this used to be was refused and no workout synced.
            client.postgrest.from("profiles").update(
                {
                    set("current_title_id", profile.currentTitleId)
                },
            ) {
                filter { eq("id", me.userId) }
            }

            // The ranked numbers now go through push_aggregates(), because the
            // client no longer holds the privilege to write them: a crafted
            // PATCH is refused at the column grant rather than merged.
            //
            // XP and strength are still OUR numbers - the award includes
            // activity curves and the quest bonus, which the server cannot
            // derive without owning the whole economy - but the function bounds
            // them, derives titles_count from the rows it can see, and derives
            // level from the XP so the two can never disagree. The monotonic
            // max() that used to live in mergeAggregates lives there too.
            //
            // This runs LAST, after sessions/sets/titles, so titles_count is
            // derived from rows that have actually arrived.
            val idle = repo.idleSnapshotOnce()
            suspend fun pushDerivedAggregates() {
                runCatching {
                    client.postgrest.rpc(
                        RPC_PUSH_AGGREGATES,
                        rpcArgs(
                            PushAggregatesArgs(
                                totalXp = profile.totalXp,
                                lifetimeStrength = lifetimeStrength,
                                streakDays = streakDays,
                                shadowEssence = idle?.state?.essence ?: 0L,
                                shadowCount = idle?.state?.figures ?: 0,
                                shadowRate = idle?.rate?.perHour ?: 0.0,
                            ),
                        ),
                    )
                }.onFailure { error ->
                    // A pre-0011 database has no such function. Report it and
                    // leave the cloud row untouched rather than failing the
                    // whole sync: the training rows above are already safe.
                    problems += "Profile totals were not synced: ${Cloud.explain(error)}"
                }
            }


            // Sessions first so their cloud ids exist before the sets land.
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
                    audience = session.audience.wire,
                    editedAt = session.editedAtMs?.let { Instant.ofEpochMilli(it).toString() },
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
                        problems += "Trial \"${session.label}\" could not be matched on the cloud"
                        return@forEach
                    }
                    // A skipped set must hold the watermark back: retrying a
                    // session is idempotent (sessions conflict on
                    // user_id,local_id; sets on session_id,exercise_name,
                    // set_index), but never re-pushing it loses reps/weight
                    // forever.
                    val (pushable, skipped) = partitionPushable(sets)
                    if (skipped.isNotEmpty()) {
                        skipped.forEach {
                            problems += "A set in \"${session.label}\" has no exercise name and was skipped"
                        }
                    } else {
                        pushedNow += session to sets
                    }
                    pushable.forEach { set ->
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
                                durationSec = set.durationSec,
                                distanceM = set.distanceM,
                                grade = set.grade,
                                exercisePosition = set.exercisePosition,
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

            // The upsert above only adds and overwrites: a set an amendment
            // removed (or moved to another movement or index) would linger on
            // the cloud forever. Only amended trials can lose a set, so only
            // they pay the extra round trips. Runs BEFORE the watermark so a
            // failed delete re-pushes the trial and tries again.
            val amendedCloudIds = pushedNow
                .filter { (session, _) -> session.editedAtMs != null }
                .mapNotNull { (session, sets) -> cloudIds[session.id]?.let { it to sets } }
            if (amendedCloudIds.isNotEmpty()) {
                val stale = staleSetRowIds(
                    current = amendedCloudIds.toMap(),
                    onCloud = client.postgrest.from("session_sets").select(
                        Columns.list("id", "session_id", "exercise_name", "set_index"),
                    ) {
                        filter { isIn("session_id", amendedCloudIds.map { it.first }) }
                    }.decodeList<SessionSetKeyDto>(),
                )
                if (stale.isNotEmpty()) {
                    client.postgrest.from("session_sets").delete {
                        filter { isIn("id", stale) }
                    }
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

            // Tier steps ride after the training rows so a server below
            // schema 20 (no lift_marks table) costs one problem line, not the
            // sync. Only step integers leave the phone — never bodyweight.
            runCatching {
                val marks = LiftBoards.marks(
                    history = history,
                    bodyweightAt = SetRecords.bodyweightLookup(repo.observeStats().first()),
                    sex = repo.observeBodyProfile().first().second,
                    nowMs = System.currentTimeMillis(),
                    practices = repo.observeSkillPractices().first(),
                )
                if (marks.isNotEmpty()) {
                    client.postgrest.from("lift_marks").upsert(
                        marks.map {
                            LiftMarkDto(
                                userId = me.userId,
                                lift = it.lift.wire,
                                step = it.step,
                                recentStep = it.recentStep,
                                recentAt = it.recentAtMs?.let { at -> Instant.ofEpochMilli(at).toString() },
                            )
                        },
                    ) {
                        onConflict = "user_id,lift"
                    }
                }
                // A lift whose qualifying sets were all deleted or edited away
                // must leave the board, or allies keep seeing a tier this
                // lifter no longer holds.
                val keep = marks.map { it.lift.wire }.toSet()
                val stale = client.postgrest.from("lift_marks").select {
                    filter { eq("user_id", me.userId) }
                }.decodeList<LiftMarkDto>().map { it.lift }.filter { it !in keep }
                if (stale.isNotEmpty()) {
                    client.postgrest.from("lift_marks").delete {
                        filter {
                            eq("user_id", me.userId)
                            isIn("lift", stale)
                        }
                    }
                }
            }.onFailure { error ->
                problems += "Lift rungs were not synced: ${Cloud.explain(error)}"
            }

            // Now that every session, set and title has landed, the server has
            // what it needs to derive the ranked numbers.
            pushDerivedAggregates()

            SyncOutcome(
                sessions = sessionDtos.size,
                sets = setCount,
                titles = titleDtos.size,
                problems = problems,
            )
        }.onSuccess {
            // Our own aggregates just changed; cached leaderboard/feed rows
            // would now show stale level/xp/title for this lifter.
            cache.invalidate(CloudReadCache.KEY_LEADERBOARD)
            cache.invalidate(CloudReadCache.KEY_FEED_FIRST_PAGE)
            cache.invalidate(CloudReadCache.KEY_LIFT_BOARD)
        }.recoverCatching { error ->
            throw IllegalStateException(Cloud.explain(error))
        }
    }

    /**
     * Every local id the server holds for this lifter, paged: PostgREST caps
     * a response at 1000 rows by default, so one select would silently drop
     * the rest and re-push them forever.
     */
    private suspend fun serverLocalIds(client: io.github.jan.supabase.SupabaseClient, userId: String): Set<Long> {
        val ids = HashSet<Long>()
        var from = 0L
        while (true) {
            val page = client.postgrest.from("sessions").select(Columns.raw("id, local_id")) {
                filter { eq("user_id", userId) }
                order("local_id", Order.ASCENDING)
                range(from, from + SERVER_ID_PAGE - 1)
            }.decodeList<SessionIdDto>()
            page.mapTo(ids) { it.localId }
            if (page.size < SERVER_ID_PAGE) return ids
            from += SERVER_ID_PAGE
        }
    }

    /**
     * One row of `cloud_archives` per lifter, holding the whole save as JSON.
     * A backup is [Repository.exportArchive]'s JSON uploaded whole; a restore
     * is that JSON handed to [Repository.importArchive] — the two primitives
     * EXPORT ARCHIVE already uses, so the cloud copy and the local export can
     * never disagree about what a save is.
     */
    data class BackupInfo(val atMs: Long, val bytes: Int)

    suspend fun backupArchive(): Result<BackupInfo> {
        val me = requireAccount(account).getOrElse { return failure(it) }
        val client = Cloud.requireConfigured.getOrElse { return failure(it) }
        return runCatching {
            // The private note, body readings, measurements, height, sex and
            // Health Connect days are device-only by the app's own promise
            // (PRIVACY.md); the cloud copy omits all of them, which is the
            // entire reason for the flag rather than reusing EXPORT ARCHIVE.
            val archive = repo.exportArchive(includeDeviceOnly = false)
            val bytes = archive.json.toByteArray()
            // Refuse here, where the message can name the fix: the server
            // check would answer the same payload with an opaque 23514.
            if (bytes.size > WireLimits.ARCHIVE_MAX_BYTES) {
                throw IllegalStateException(
                    "Backup is too large for the cloud (${bytes.size} of ${WireLimits.ARCHIVE_MAX_BYTES} bytes) — export an archive file instead",
                )
            }
            client.postgrest.from("cloud_archives").upsert(
                ArchiveDto(
                    userId = me.userId,
                    archive = archive.json,
                    sizeBytes = bytes.size,
                ),
            ) {
                onConflict = "user_id"
            }
            BackupInfo(atMs = System.currentTimeMillis(), bytes = bytes.size)
        }.recoverCatching { error ->
            throw IllegalStateException(Cloud.explain(error))
        }
    }

    suspend fun latestBackup(): Result<BackupInfo?> {
        val me = requireAccount(account).getOrElse { return failure(it) }
        val client = Cloud.requireConfigured.getOrElse { return failure(it) }
        return runCatching {
            // Two columns only. Selecting * here would download the whole
            // archive text just to render a timestamp on the account screen.
            client.postgrest.from("cloud_archives").select(
                Columns.raw("size_bytes, updated_at"),
            ) {
                filter { eq("user_id", me.userId) }
                order("updated_at", Order.DESCENDING)
                limit(1)
            }.decodeList<ArchiveStatusDto>().firstOrNull()?.let {
                BackupInfo(
                    atMs = it.updatedAt?.let { at -> Instant.parse(at).toEpochMilli() }
                        ?: System.currentTimeMillis(),
                    bytes = it.sizeBytes,
                )
            }
        }.recoverCatching { error ->
            throw IllegalStateException(Cloud.explain(error))
        }
    }

    suspend fun restoreArchive(): Result<Repository.ImportResult> {
        val me = requireAccount(account).getOrElse { return failure(it) }
        val client = Cloud.requireConfigured.getOrElse { return failure(it) }
        return runCatching {
            val row = client.postgrest.from("cloud_archives").select {
                filter { eq("user_id", me.userId) }
            }.decodeList<ArchiveDto>().firstOrNull()
                ?: throw IllegalStateException("No cloud backup yet — back up on the old device first")
            // Device-only data comes back absent BY DESIGN: the archive was
            // built with includeDeviceOnly = false, so private notes are empty
            // and the importer keeps this device's own readings, measurements
            // and Health Connect days instead of clearing them.
            repo.importArchive(row.archive).getOrElse { error ->
                throw IllegalStateException(error.message ?: "The backup could not be restored")
            }
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
     * Me plus accepted allies, every lift. TTL 60s like the other boards:
     * tiers change only when someone pushes. A lift value this build does not
     * know is skipped, so a newer client's extra lift cannot blank the board.
     */
    suspend fun liftBoard(force: Boolean = false): Result<List<LiftBoardRow>> {
        val client = Cloud.requireConfigured.getOrElse { return failure(it) }
        return runCatching {
            cache.getOrFetch(
                key = CloudReadCache.KEY_LIFT_BOARD,
                ttlMs = 60_000,
                force = force,
                userId = account.account.value?.userId,
            ) {
                client.postgrest.from("lift_board").select()
                    .decodeList<LiftBoardDto>()
                    .mapNotNull { it.toRow() }
            }
        }.recoverCatching { error ->
            throw IllegalStateException(Cloud.explain(error))
        }
    }

    /**
     * One workout with its full sets. RLS decides visibility, so an
     * invisible row (not an ally, blocked, private audience) reads as an empty
     * result — reported as one clear failure rather than a blank screen.
     * TTL 15s: sets never change after completion, but reactions on the same
     * screen are refetched separately.
     */
    suspend fun workout(sessionId: String, force: Boolean = false): Result<AllyWorkout> {
        val client = Cloud.requireConfigured.getOrElse { return failure(it) }
        return runCatching {
            cache.getOrFetch(
                key = "${CloudReadCache.KEY_WORKOUT}:$sessionId",
                ttlMs = 15_000,
                force = force,
                userId = account.account.value?.userId,
            ) {
                val session = client.postgrest.from("sessions").select(
                    Columns.list(
                        "id", "user_id", "label", "title", "note",
                        "completed_at", "started_at", "xp_awarded", "strength_score",
                    ),
                ) {
                    filter { eq("id", sessionId) }
                }.decodeList<AllyWorkoutSessionDto>().firstOrNull()
                    ?: throw IllegalStateException("This trial is not visible to you")
                val sets = client.postgrest.from("session_sets").select {
                    filter { eq("session_id", sessionId) }
                    // A workout tops out far below PostgREST's 1000-row cap.
                    limit(1000)
                }.decodeList<AllySetDto>()
                // Null positions (rows pushed before schema 20) sort last, then
                // by name, so the order is stable rather than arrival-dependent.
                val exercises = sets.groupBy { it.exerciseName }
                    .entries
                    .sortedWith(
                        compareBy<Map.Entry<String, List<AllySetDto>>>(
                            { e -> e.value.mapNotNull { it.exercisePosition }.minOrNull() ?: Int.MAX_VALUE },
                        ).thenBy { it.key },
                    )
                    .map { (name, rows) ->
                        AllyExercise(
                            name = name,
                            sets = rows.sortedBy { it.setIndex }.map {
                                AllySet(
                                    setIndex = it.setIndex,
                                    reps = it.reps,
                                    weightKg = it.weightKg,
                                    durationSec = it.durationSec,
                                    distanceM = it.distanceM,
                                    grade = it.grade,
                                    modifiers = it.modifiers,
                                    done = it.done,
                                )
                            },
                        )
                    }
                AllyWorkout(
                    sessionId = session.id,
                    userId = session.userId,
                    headline = session.title.ifBlank { session.label },
                    note = session.note,
                    completedAtMs = session.completedAt?.let { Instant.parse(it).toEpochMilli() },
                    startedAtMs = session.startedAt?.let { Instant.parse(it).toEpochMilli() },
                    xpAwarded = session.xpAwarded,
                    strengthScore = session.strengthScore,
                    exercises = exercises,
                )
            }
        }.recoverCatching { error ->
            throw IllegalStateException(Cloud.explain(error))
        }
    }

    /**
     * The muster roll's board. Separate from [leaderboard] by design: idle
     * progress must never rank beside strength, or the training board starts
     * measuring patience instead of what a lifter lifted.
     *
     * TTL 60s, matching the training board — banked essence only changes when
     * someone opens the app and pushes.
     */
    suspend fun shadowBoard(force: Boolean = false): Result<List<ShadowBoardRow>> {
        val client = Cloud.requireConfigured.getOrElse { return failure(it) }
        return runCatching {
            cache.getOrFetch(
                key = CloudReadCache.KEY_SHADOW_BOARD,
                ttlMs = 60_000,
                force = force,
                userId = account.account.value?.userId,
            ) {
                client.postgrest.from("shadow_board").select {
                    order("shadow_essence", Order.DESCENDING)
                    limit(100)
                }.decodeList<ShadowBoardDto>().map {
                    ShadowBoardRow(
                        userId = it.id,
                        displayName = it.displayName,
                        currentTitleId = it.currentTitleId,
                        level = it.level,
                        essence = it.shadowEssence,
                        shadows = it.shadowCount,
                        ratePerHour = it.shadowRate,
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
                // Pending profiles of "friends"-visibility lifters may be
                // hidden from us — those decode as a fallback name instead
                // of failing.
                // Keyed by id so the crest can read the worn title's rarity;
                // a hidden profile simply yields no entry.
                val profiles = if (counterpartIds.isEmpty()) {
                    emptyMap()
                } else {
                    client.postgrest.from("profiles").select {
                        filter { isIn("id", counterpartIds) }
                    }.decodeList<ProfileNameDto>().associateBy { it.id }
                }
                // An allies-only requester's profile stays unreadable until we
                // accept, which showed every request as "Hidden Ironbound". The
                // inbox (a definer RPC) already carries their name, and names
                // are public anyway (find_hunter), so borrow it from there.
                val requesterNames = if (rows.any { !it.accepted && it.addresseeId == me.userId && profiles[it.requesterId] == null }) {
                    runCatching {
                        client.postgrest.rpc(RPC_MY_INBOX).decodeList<InboxRowDto>()
                            .filter { it.kind == "request" }
                            .associate { it.actorId to it.actorName }
                    }.getOrDefault(emptyMap())
                } else {
                    emptyMap()
                }
                rows.map { row ->
                    val other = if (row.requesterId == me.userId) row.addresseeId else row.requesterId
                    val profile = profiles[other]
                    FriendRow(
                        userId = other,
                        displayName = profile?.displayName
                            ?: requesterNames[other]?.takeIf { it.isNotBlank() }
                            ?: "Hidden Ironbound",
                        accepted = row.accepted,
                        // Incoming = they asked us and it is not accepted yet.
                        incoming = row.addresseeId == me.userId && !row.accepted,
                        level = profile?.level,
                        currentTitleId = profile?.currentTitleId,
                    )
                }
            }
        }.recoverCatching { error ->
            throw IllegalStateException(Cloud.explain(error))
        }
    }

    /**
     * The caller's warband, or null when they are in none. TTL 30s like the
     * friends list; create/join/leave invalidate it at once.
     */
    suspend fun warband(force: Boolean = false): Result<Warband?> {
        val me = requireAccount(account).getOrElse { return failure(it) }
        val client = Cloud.requireConfigured.getOrElse { return failure(it) }
        return runCatching {
            cache.getOrFetch(
                key = CloudReadCache.KEY_WARBAND,
                ttlMs = 30_000,
                force = force,
                userId = me.userId,
            ) {
                fetchWarband(client)
            }
        }.recoverCatching { error ->
            throw IllegalStateException(Cloud.explain(error))
        }
    }

    private suspend fun fetchWarband(client: io.github.jan.supabase.SupabaseClient): Warband? =
        client.postgrest.rpc(RPC_MY_WARBAND).decodeList<WarbandDto>().singleOrNull()?.toWarband()

    suspend fun createWarband(name: String): Result<Warband> {
        val me = requireAccount(account).getOrElse { return failure(it) }
        val client = Cloud.requireConfigured.getOrElse { return failure(it) }
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            return failure(IllegalArgumentException("Give the circle a name"))
        }
        return runCatching {
            client.postgrest.rpc(RPC_CREATE_WARBAND, rpcArgs(CreateWarbandArgs(name = trimmed)))
            cache.invalidate(CloudReadCache.KEY_WARBAND)
            fetchWarband(client)
                ?: throw IllegalStateException("The circle did not appear — pull to refresh")
        }.recoverCatching { error ->
            throw IllegalStateException(Cloud.explain(error))
        }
    }

    suspend fun joinWarband(code: String): Result<Warband> {
        val me = requireAccount(account).getOrElse { return failure(it) }
        val client = Cloud.requireConfigured.getOrElse { return failure(it) }
        val cleaned = code.trim().uppercase()
        if (!isValidInviteCode(cleaned)) {
            return failure(IllegalArgumentException("That code is not the right shape"))
        }
        return runCatching {
            client.postgrest.rpc(RPC_JOIN_WARBAND, rpcArgs(JoinWarbandArgs(code = cleaned)))
            cache.invalidate(CloudReadCache.KEY_WARBAND)
            fetchWarband(client)
                ?: throw IllegalStateException("The circle did not appear — pull to refresh")
        }.recoverCatching { error ->
            throw IllegalStateException(Cloud.explain(error))
        }
    }

    suspend fun leaveWarband(): Result<Unit> {
        val me = requireAccount(account).getOrElse { return failure(it) }
        val client = Cloud.requireConfigured.getOrElse { return failure(it) }
        return runCatching {
            client.postgrest.rpc(RPC_LEAVE_WARBAND)
            Unit
        }.onSuccess {
            cache.invalidate(CloudReadCache.KEY_WARBAND)
        }.recoverCatching { error ->
            throw IllegalStateException(Cloud.explain(error))
        }
    }

    /** The owner sets the band's weekly goal; the server refuses anyone else. */
    suspend fun setWarbandGoal(goal: Int): Result<Unit> {
        requireAccount(account).getOrElse { return failure(it) }
        val client = Cloud.requireConfigured.getOrElse { return failure(it) }
        if (goal !in 5..50) {
            return failure(IllegalArgumentException("A weekly goal is 5-50 trials"))
        }
        return runCatching {
            client.postgrest.rpc(RPC_SET_WARBAND_GOAL, rpcArgs(SetWarbandGoalArgs(goal = goal)))
            Unit
        }.onSuccess {
            cache.invalidate(CloudReadCache.KEY_WARBAND)
        }.recoverCatching { error ->
            throw IllegalStateException(Cloud.explain(error))
        }
    }

    suspend fun requestFriend(displayName: String): Result<Unit> {
        val me = requireAccount(account).getOrElse { return failure(it) }
        val client = Cloud.requireConfigured.getOrElse { return failure(it) }
        val name = displayName.trim()
        if (name.isEmpty()) {
            return Result.failure(IllegalStateException("Type an Ironbound's true name first"))
        }
        return runCatching {
            // Discovery goes through find_hunter(), not a select on `profiles`.
            // A select is governed by `profiles_read using (can_view(id))`, and
            // for a stranger can_view is false precisely BECAUSE you are not
            // friends yet — so the lookup returned nothing and told the lifter
            // that a real person did not exist. The RPC is an exact, trimmed,
            // case-insensitive match returning only (id, display_name), so it
            // cannot be walked to enumerate the roster and reveals nothing the
            // caller did not already type.
            val matches = client.postgrest.rpc(
                RPC_FIND_HUNTER,
                rpcArgs(FindHunterArgs(name = name)),
            ).decodeList<ProfileNameDto>()
            val exact = matches.firstOrNull { it.displayName.equals(name, ignoreCase = true) }
                ?: throw IllegalStateException("No Ironbound has the true name \"$name\"")
            if (exact.id == me.userId) {
                throw IllegalStateException("You cannot send yourself an ally request")
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
            return Result.failure(IllegalStateException("You cannot send yourself an ally request"))
        }
        return runCatching {
            client.postgrest.from("friendships").insert(
                FriendshipDto(requesterId = me.userId, addresseeId = userId, accepted = false),
            )
            Unit
        }.onSuccess {
            cache.invalidate(CloudReadCache.KEY_FRIENDS)
        }.recoverCatching { error ->
            // Already requested or already friends: nothing to do.
            if (error !is PostgrestRestException || error.code != "23505") {
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
                throw IllegalStateException("No pending request from that Ironbound")
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
                // Scoped to this lifter — without the user_id term the filter
                // returned EVERY visible lifter's sessions under the tapped
                // row (the on-device defect).
                filter {
                    eq("user_id", userId)
                    gt("completed_at", "1970-01-02T00:00:00Z")
                }
                order("completed_at", Order.DESCENDING)
                limit(limit.toLong())
            }.decodeList<FriendSessionDto>()
        }.map { list ->
            // Empty under RLS = this lifter does not share with you (or truly
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
                    editedAtMs = dto.editedAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() },
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
     * per scroll position and each is requested once anyway. Reaction taps
     * are recorded as an overlay applied to every handed-out page, so older
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
            }.decodeList<FeedEntryDto>().map { it.toFeedEntry() }.let { fresh ->
                // The server's own values are the truth: drop any overlay
                // entry it already reflects before the page is cached.
                cache.reconcileReactions(fresh)
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
            }.let { cache.applyReactions(it) }
        }.recoverCatching { error ->
            throw IllegalStateException(Cloud.explain(error))
        }
    }

    /**
     * Sets (or with null, removes) the caller's one reaction to a workout.
     * Idempotent: repeating the current state is a no-op, never an error.
     */
    suspend fun react(sessionId: String, reaction: Reaction?): Result<Unit> {
        val me = requireAccount(account).getOrElse { return failure(it) }
        val client = Cloud.requireConfigured.getOrElse { return failure(it) }
        return runCatching {
            if (reaction == null) {
                client.postgrest.from("session_likes").delete {
                    filter {
                        eq("session_id", sessionId)
                        eq("user_id", me.userId)
                    }
                }
            } else {
                client.postgrest.from("session_likes").upsert(
                    SessionLikeDto(sessionId = sessionId, userId = me.userId, kind = reaction.wire),
                ) {
                    // Merge, not DO NOTHING: since 0018 an update policy lets
                    // the row's owner change `kind`, and switching salute to
                    // flame IS the conflict path. ignoreDuplicates would keep
                    // the old kind and report success.
                    onConflict = "session_id,user_id"
                }
            }
            // Optimistic: record the overlay the feed applies at hand-out —
            // this also covers older paged entries, which are never cached.
            cache.recordReaction(sessionId, reaction)
            // The owner's reactions dialog must show the fresh one, not a
            // 30s-old cached list.
            cache.invalidate("${CloudReadCache.KEY_LIKERS}:$sessionId")
            Unit
        }.recoverCatching { error ->
            throw IllegalStateException(Cloud.explain(error))
        }
    }

    /**
     * Who reacted to a session, newest first. One PostgREST embed
     * (session_likes -> profiles) so the owner sees names without a second
     * round trip. An RLS refusal is a visibility answer, worded as such.
     *
     * TTL 30s, same reasoning as [friends]: reactions arrive only when
     * someone acts, and our own [react] invalidates the key immediately, so a
     * just-added reaction is visible the moment the owner opens the dialog
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
                    Columns.raw("user_id, kind, created_at, profiles(display_name)"),
                ) {
                    filter { eq("session_id", sessionId) }
                    order("created_at", Order.DESCENDING)
                    // The list is a name wall, not a ledger; a cap keeps the
                    // payload bounded no matter how viral a session gets.
                    limit(50)
                }.decodeList<LikerRowDto>().map { row ->
                    Liker(
                        userId = row.userId,
                        displayName = row.profile?.displayName ?: "Hidden Ironbound",
                        likedAtMs = Instant.parse(row.createdAt).toEpochMilli(),
                        reaction = Reaction.fromWire(row.kind),
                    )
                }
            }
        }.recoverCatching { error ->
            if (error is PostgrestRestException && error.code == "42501") {
                throw IllegalStateException("These tributes are not visible to you")
            }
            throw IllegalStateException(Cloud.explain(error))
        }
    }

    /**
     * A workout's comments, oldest first, capped at the server's per-workout
     * limit so the thread is always whole.
     *
     * TTL 15s: a thread is read while people are talking in it, so it must
     * catch up quickly, but re-entering it inside a few seconds should not
     * re-download it. Our own add/delete invalidate the key at once.
     */
    suspend fun comments(sessionId: String, force: Boolean = false): Result<List<Comment>> {
        val client = Cloud.requireConfigured.getOrElse { return failure(it) }
        return runCatching {
            cache.getOrFetch(
                key = "${CloudReadCache.KEY_COMMENTS}:$sessionId",
                ttlMs = 15_000,
                force = force,
                userId = account.account.value?.userId,
            ) {
                client.postgrest.from("session_comments").select(
                    Columns.raw(COMMENT_COLUMNS),
                ) {
                    filter { eq("session_id", sessionId) }
                    order("created_at", Order.ASCENDING)
                    limit(COMMENTS_PER_WORKOUT)
                }.decodeList<CommentDto>().map { it.toComment() }
            }
        }.recoverCatching { error ->
            throw IllegalStateException(Cloud.explain(error))
        }
    }

    suspend fun addComment(sessionId: String, body: String): Result<Comment> {
        val me = requireAccount(account).getOrElse { return failure(it) }
        val client = Cloud.requireConfigured.getOrElse { return failure(it) }
        val text = body.trim()
        // Refused here so the lifter reads why; the server check would
        // answer the same text with an opaque 23514.
        if (text.isEmpty()) {
            return Result.failure(IllegalStateException("Write something first"))
        }
        if (text.length > WireLimits.COMMENT_MAX) {
            return Result.failure(
                IllegalStateException("Remarks are at most ${WireLimits.COMMENT_MAX} characters"),
            )
        }
        return runCatching {
            // Read back so the thread shows the server's author_name and
            // created_at (set by trigger) rather than a local guess.
            client.postgrest.from("session_comments").insert(
                CommentInsertDto(sessionId = sessionId, userId = me.userId, body = text),
            ) {
                select(Columns.raw(COMMENT_COLUMNS))
            }.decodeSingle<CommentDto>().toComment()
        }.onSuccess {
            cache.invalidate("${CloudReadCache.KEY_COMMENTS}:$sessionId")
            // The card's comment count lives on the cached first page.
            cache.invalidate(CloudReadCache.KEY_FEED_FIRST_PAGE)
        }.recoverCatching { error ->
            throw IllegalStateException(Cloud.explain(error))
        }
    }

    suspend fun deleteComment(commentId: String): Result<Unit> {
        requireAccount(account).getOrElse { return failure(it) }
        val client = Cloud.requireConfigured.getOrElse { return failure(it) }
        return runCatching {
            // RLS turns a delete it refuses into "0 rows", not an error, so
            // ask for the rows back: otherwise a refused delete would report
            // success and the comment would reappear on the next refresh.
            val deleted = client.postgrest.from("session_comments").delete {
                filter { eq("id", commentId) }
                select(Columns.raw("id"))
            }.decodeList<CommentIdDto>()
            if (deleted.isEmpty()) {
                throw IllegalStateException("That remark is already gone or not yours to remove")
            }
        }.onSuccess {
            // The session id is not known here; threads are cheap to refetch.
            cache.invalidatePrefix("${CloudReadCache.KEY_COMMENTS}:")
            cache.invalidate(CloudReadCache.KEY_FEED_FIRST_PAGE)
            cache.invalidate(CloudReadCache.KEY_INBOX)
        }.recoverCatching { error ->
            throw IllegalStateException(Cloud.explain(error))
        }
    }

    /**
     * The caller's inbox: ally requests, accepted requests, comments and
     * reactions on their workouts, replies in threads they remarked in, and
     * circle joins and goals, newest first. Derived on the server by
     * my_inbox(), never stored, so it cannot grow with time.
     *
     * TTL 30s, like [friends]: new items only arrive when someone acts, and
     * InboxWorker's half-hourly poll refreshes the unread count in the
     * background anyway.
     */
    suspend fun inbox(force: Boolean = false): Result<Inbox> {
        val me = requireAccount(account).getOrElse { return failure(it) }
        val client = Cloud.requireConfigured.getOrElse { return failure(it) }
        return runCatching {
            cache.getOrFetch(
                key = CloudReadCache.KEY_INBOX,
                ttlMs = 30_000,
                force = force,
                userId = me.userId,
            ) {
                val items = client.postgrest.rpc(RPC_MY_INBOX)
                    .decodeList<InboxRowDto>()
                    .mapNotNull { it.toInboxItem() }
                val seenAt = client.postgrest.from("inbox_seen").select(Columns.raw("seen_at")) {
                    filter { eq("user_id", me.userId) }
                }.decodeList<InboxSeenDto>().firstOrNull()?.let { Instant.parse(it.seenAt).toEpochMilli() }
                Inbox(items = items, seenAtMs = seenAt)
            }
        }.onSuccess { inbox ->
            // A fetch that outlived its account must not light the next
            // lifter's dot.
            if (account.account.value?.userId == me.userId) _inboxUnread.value = inbox.unread
        }.recoverCatching { error ->
            throw IllegalStateException(Cloud.explain(error))
        }
    }

    suspend fun markInboxSeen(): Result<Unit> {
        val me = requireAccount(account).getOrElse { return failure(it) }
        val client = Cloud.requireConfigured.getOrElse { return failure(it) }
        return runCatching {
            // Server time, not ours: seen_at is compared with server-stamped
            // occurred_at, and a phone clock running behind would leave items
            // unread forever.
            client.postgrest.rpc(RPC_MARK_INBOX_SEEN)
            Unit
        }.onSuccess {
            cache.invalidate(CloudReadCache.KEY_INBOX)
            if (account.account.value?.userId == me.userId) _inboxUnread.value = 0
        }.recoverCatching { error ->
            throw IllegalStateException(Cloud.explain(error))
        }
    }

    /**
     * Ends an alliance, or withdraws/declines a pending request: the
     * friendships row is deleted whichever side created it. Nothing to
     * delete is success — the pair is already apart.
     */
    suspend fun removeFriend(userId: String): Result<Unit> {
        val me = requireAccount(account).getOrElse { return failure(it) }
        val client = Cloud.requireConfigured.getOrElse { return failure(it) }
        return runCatching {
            client.postgrest.from("friendships").delete {
                filter {
                    or {
                        and {
                            eq("requester_id", me.userId)
                            eq("addressee_id", userId)
                        }
                        and {
                            eq("requester_id", userId)
                            eq("addressee_id", me.userId)
                        }
                    }
                }
            }
            Unit
        }.onSuccess {
            // Friends-only workouts of theirs leave our feed with the alliance.
            cache.invalidate(CloudReadCache.KEY_FRIENDS)
            cache.invalidate(CloudReadCache.KEY_FEED_FIRST_PAGE)
        }.recoverCatching { error ->
            throw IllegalStateException(Cloud.explain(error))
        }
    }

    /** TTL 30s; our own block/unblock invalidate it at once. */
    suspend fun blocked(force: Boolean = false): Result<List<BlockedLifter>> {
        val me = requireAccount(account).getOrElse { return failure(it) }
        val client = Cloud.requireConfigured.getOrElse { return failure(it) }
        return runCatching {
            cache.getOrFetch(
                key = CloudReadCache.KEY_BLOCKED,
                ttlMs = 30_000,
                force = force,
                userId = me.userId,
            ) {
                client.postgrest.from("blocks").select(Columns.raw("blocked_id, blocked_name")) {
                    filter { eq("blocker_id", me.userId) }
                    order("created_at", Order.DESCENDING)
                }.decodeList<BlockDto>().map {
                    BlockedLifter(
                        userId = it.blockedId,
                        displayName = it.blockedName?.takeIf { name -> name.isNotBlank() } ?: "Hidden Ironbound",
                    )
                }
            }
        }.recoverCatching { error ->
            throw IllegalStateException(Cloud.explain(error))
        }
    }

    /** Blocking also ends any alliance (server trigger). Already blocked is success. */
    suspend fun block(userId: String): Result<Unit> {
        val me = requireAccount(account).getOrElse { return failure(it) }
        val client = Cloud.requireConfigured.getOrElse { return failure(it) }
        if (userId == me.userId) {
            return Result.failure(IllegalStateException("You can’t block yourself"))
        }
        return runCatching {
            client.postgrest.from("blocks").insert(BlockInsertDto(blockerId = me.userId, blockedId = userId))
            Unit
        }.recoverCatching { error ->
            if (error !is PostgrestRestException || error.code != "23505") {
                throw IllegalStateException(Cloud.explain(error))
            }
        }.onSuccess {
            // The trigger deleted the friendship and RLS now hides their
            // workouts, comments and inbox items: every cached answer that
            // could still show them is stale.
            invalidateSocialReads()
            cache.invalidate(CloudReadCache.KEY_BLOCKED)
        }
    }

    suspend fun unblock(userId: String): Result<Unit> {
        val me = requireAccount(account).getOrElse { return failure(it) }
        val client = Cloud.requireConfigured.getOrElse { return failure(it) }
        return runCatching {
            client.postgrest.from("blocks").delete {
                filter {
                    eq("blocker_id", me.userId)
                    eq("blocked_id", userId)
                }
            }
            Unit
        }.onSuccess {
            invalidateSocialReads()
            cache.invalidate(CloudReadCache.KEY_BLOCKED)
        }.recoverCatching { error ->
            throw IllegalStateException(Cloud.explain(error))
        }
    }

    /** Ids of the lifters the caller muted. TTL 30s; mute/unmute invalidate it. */
    suspend fun mutedIds(force: Boolean = false): Result<Set<String>> {
        val me = requireAccount(account).getOrElse { return failure(it) }
        val client = Cloud.requireConfigured.getOrElse { return failure(it) }
        return runCatching {
            cache.getOrFetch(
                key = CloudReadCache.KEY_MUTED,
                ttlMs = 30_000,
                force = force,
                userId = me.userId,
            ) {
                client.postgrest.from("mutes").select(Columns.raw("muted_id")) {
                    filter { eq("muter_id", me.userId) }
                }.decodeList<MuteDto>().map { it.mutedId }.toSet()
            }
        }.recoverCatching { error ->
            throw IllegalStateException(Cloud.explain(error))
        }
    }

    /** The muted lifter is not told. Already muted is success. */
    suspend fun mute(userId: String): Result<Unit> {
        val me = requireAccount(account).getOrElse { return failure(it) }
        val client = Cloud.requireConfigured.getOrElse { return failure(it) }
        if (userId == me.userId) {
            return Result.failure(IllegalStateException("You can’t mute yourself"))
        }
        return runCatching {
            client.postgrest.from("mutes").insert(MuteInsertDto(muterId = me.userId, mutedId = userId))
            Unit
        }.recoverCatching { error ->
            if (error !is PostgrestRestException || error.code != "23505") {
                throw IllegalStateException(Cloud.explain(error))
            }
        }.onSuccess {
            // The feed view and my_inbox() now drop them server-side.
            invalidateSocialReads()
            cache.invalidate(CloudReadCache.KEY_MUTED)
        }
    }

    suspend fun unmute(userId: String): Result<Unit> {
        val me = requireAccount(account).getOrElse { return failure(it) }
        val client = Cloud.requireConfigured.getOrElse { return failure(it) }
        return runCatching {
            client.postgrest.from("mutes").delete {
                filter {
                    eq("muter_id", me.userId)
                    eq("muted_id", userId)
                }
            }
            Unit
        }.onSuccess {
            invalidateSocialReads()
            cache.invalidate(CloudReadCache.KEY_MUTED)
        }.recoverCatching { error ->
            throw IllegalStateException(Cloud.explain(error))
        }
    }

    /**
     * Files a report for the owner to review in the Supabase dashboard.
     * Write-only: no client can read reports back, so nothing is selected.
     */
    suspend fun report(
        targetUserId: String,
        reason: ReportReason,
        note: String,
        sessionId: String? = null,
        commentId: String? = null,
    ): Result<Unit> {
        val me = requireAccount(account).getOrElse { return failure(it) }
        val client = Cloud.requireConfigured.getOrElse { return failure(it) }
        if (targetUserId == me.userId) {
            return Result.failure(IllegalStateException("You can’t report yourself"))
        }
        val text = note.trim()
        if (text.length > WireLimits.REPORT_NOTE_MAX) {
            return Result.failure(
                IllegalStateException("Keep the note to ${WireLimits.REPORT_NOTE_MAX} characters"),
            )
        }
        return runCatching {
            client.postgrest.from("reports").insert(
                ReportDto(
                    reporterId = me.userId,
                    targetUserId = targetUserId,
                    sessionId = sessionId,
                    commentId = commentId,
                    reason = reason.wire,
                    note = text,
                ),
            )
            Unit
        }.recoverCatching { error ->
            throw IllegalStateException(Cloud.explain(error))
        }
    }

    /** Everything a block, mute or unfriend can change the answer of. */
    private suspend fun invalidateSocialReads() {
        cache.invalidate(CloudReadCache.KEY_FRIENDS)
        cache.invalidate(CloudReadCache.KEY_FEED_FIRST_PAGE)
        cache.invalidate(CloudReadCache.KEY_INBOX)
        cache.invalidatePrefix("${CloudReadCache.KEY_COMMENTS}:")
        cache.invalidatePrefix("${CloudReadCache.KEY_LIKERS}:")
        cache.invalidate(CloudReadCache.KEY_LIFT_BOARD)
        cache.invalidatePrefix("${CloudReadCache.KEY_WORKOUT}:")
    }

    private fun completedDates(completed: List<com.ironvellum.app.domain.WorkoutSession>): Set<LocalDate> =
        completed.mapNotNull { session ->
            session.completedAtMs?.let {
                Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
            }
        }.toSet()

    // Internal (not private) so the instrumented push-selection test can call
    // pendingForPush; the fingerprint itself stays private.
    internal companion object {
        /** `session_comments` columns the thread reads; one list for select and read-back. */
        private const val COMMENT_COLUMNS = "id, session_id, user_id, author_name, body, created_at"

        /** The server's per-workout comment ceiling: a thread fetch is always whole. */
        private const val COMMENTS_PER_WORKOUT = 200L

        /** PostgREST's default max-rows: one page of the server id sweep. */
        private const val SERVER_ID_PAGE = 1000L

        /**
         * The sessions a push must upload: changed-since-last-push, and never
         * CSV-imported. Imported history reaches the cloud only inside the
         * full-archive backup (cloud_archives) — the feed would otherwise
         * flood with years of back-filled workouts the lifter never chose to
         * publish. Filtering BEFORE the watermark check also means an
         * imported session never enters the retry path: it is skipped each
         * push and simply never advances the watermark, so there is nothing
         * to retry forever.
         */
        internal fun pendingForPush(
            completed: List<Pair<WorkoutSession, List<SessionSet>>>,
            watermark: Map<Long, Int>,
        ): List<Pair<WorkoutSession, List<SessionSet>>> = completed
            .filterNot { (session, _) -> session.imported }
            .filter { (session, sets) -> pushFingerprint(session, sets) != watermark[session.id] }

        /**
         * Content fingerprint of one completed session — every field the push
         * uploads, sets included. Equality with the stored watermark means
         * "the cloud already holds exactly this", so the session is skipped.
         */
        internal fun pushFingerprint(session: WorkoutSession, sets: List<SessionSet>): Int {
            val fields = arrayOf<Any?>(
                session.label,
                session.title,
                session.note,
                session.completedAtMs,
                session.xpAwarded,
                session.strengthScore,
                sets.map { set ->
                    // Every field the push uploads must be here, or an edit that only
                    // changes a hold's seconds matches the watermark and never syncs.
                    listOf(
                        set.exerciseName, set.setIndex, set.reps, set.weightKg, set.modifiers, set.done,
                        set.durationSec, set.distanceM, set.grade, set.exercisePosition,
                    )
                },
            )
            // The audience joins the hash only when it is not the default:
            // appending it unconditionally would change every stored
            // fingerprint and re-upload the whole history once after the
            // update. The wire string, never the enum — Enum.hashCode is an
            // identity hash and would differ on every process start.
            // The amended stamp follows the same rule: it joins only once set,
            // so never-amended history keeps the fingerprint it was pushed at.
            val extra = buildList<Any> {
                if (session.audience != SessionAudience.PROFILE) add(session.audience.wire)
                session.editedAtMs?.let { add(it) }
            }
            return Objects.hash(*fields, *extra.toTypedArray())
        }

        /**
         * Cloud set rows of the amended trials that the device no longer has.
         * [current] maps each trial's cloud id to the sets just pushed for it;
         * a row is stale when its (movement, index) is not among them. Rows of
         * a trial missing from [current] are never touched.
         */
        internal fun staleSetRowIds(
            current: Map<String, List<SessionSet>>,
            onCloud: List<SessionSetKeyDto>,
        ): List<String> {
            val keep = current.mapValues { (_, sets) -> sets.map { it.exerciseName to it.setIndex }.toSet() }
            return onCloud
                .filter { row -> keep[row.sessionId]?.contains(row.exerciseName to row.setIndex) == false }
                .map { it.id }
        }
    }
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
        const val KEY_SHADOW_BOARD = "shadow_board"
        const val KEY_FRIENDS = "friends"
        const val KEY_WARBAND = "warband"
        const val KEY_FEED_FIRST_PAGE = "feed:first"
        const val KEY_LIKERS = "likers"
        const val KEY_COMMENTS = "comments"
        const val KEY_INBOX = "inbox"
        const val KEY_BLOCKED = "blocked"
        const val KEY_MUTED = "muted"
        const val KEY_LIFT_BOARD = "lift_board"
        const val KEY_WORKOUT = "workout"
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
                // The reaction overlay is user-scoped optimism too — a
                // switched account must not inherit the previous one's taps.
                reactionOverlay.clear()
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
            value
        } catch (error: Throwable) {
            mine.completeExceptionally(error)
            lock.withLock { if (slots[key] === mine) slots.remove(key) }
            throw error
        }
    }

    suspend fun invalidate(key: String) {
        lock.withLock { slots.remove(key) }
    }

    /** Drops every slot whose key starts with [prefix] (e.g. all comment threads). */
    suspend fun invalidatePrefix(prefix: String) {
        lock.withLock { slots.keys.removeAll { it.startsWith(prefix) } }
    }

    // sessionId -> the caller's reaction as last set on this device (null =
    // removed): the optimistic state for feed entries the cache does not
    // hold — older paged pages are never cached, so the overlay is the only
    // way their taps show immediately. It stores the TARGET, not a count
    // delta, and the counts are re-derived from each page's server values at
    // hand-out, so repeated taps can never drift the total.
    private val reactionOverlay = HashMap<String, Reaction?>()

    suspend fun recordReaction(sessionId: String, reaction: Reaction?) {
        lock.withLock { reactionOverlay[sessionId] = reaction }
    }

    private fun reflects(entry: FeedEntry, target: Reaction?): Boolean =
        entry.myReaction == target && entry.likedByMe == (target != null)

    /** Drop overlay entries a fresh page already reflects: the server caught up. */
    suspend fun reconcileReactions(entries: List<FeedEntry>) {
        lock.withLock {
            entries.forEach { entry ->
                if (!reactionOverlay.containsKey(entry.sessionId)) return@forEach
                if (reflects(entry, reactionOverlay[entry.sessionId])) reactionOverlay.remove(entry.sessionId)
            }
        }
    }

    /** Apply the overlay at hand-out so paged entries mirror the taps too. */
    suspend fun applyReactions(entries: List<FeedEntry>): List<FeedEntry> {
        lock.withLock {
            if (reactionOverlay.isEmpty()) return entries
            return entries.map { entry ->
                if (!reactionOverlay.containsKey(entry.sessionId)) return@map entry
                val target = reactionOverlay[entry.sessionId]
                if (reflects(entry, target)) return@map entry
                // Take the server's own reaction out, put the target in.
                // likedByMe with an unknown myReaction (a kind this build does
                // not know) still counts toward the total it is removed from.
                val reactions = entry.reactions.toMutableMap()
                entry.myReaction?.let { old ->
                    val left = (reactions[old] ?: 0) - 1
                    if (left > 0) reactions[old] = left else reactions.remove(old)
                }
                target?.let { reactions[it] = (reactions[it] ?: 0) + 1 }
                val total = entry.likeCount - (if (entry.likedByMe) 1 else 0) + (if (target != null) 1 else 0)
                entry.copy(
                    likedByMe = target != null,
                    myReaction = target,
                    reactions = reactions,
                    likeCount = total.coerceAtLeast(0),
                )
            }
        }
    }
}

