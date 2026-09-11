package com.monarch.app.data.cloud

import com.monarch.app.data.Repository
import com.monarch.app.data.cloud.Cloud.failure
import com.monarch.app.domain.PlayerProfile
import com.monarch.app.domain.Titles
import com.monarch.app.domain.Xp
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first

/**
 * Everything that leaves the device through PostgREST: aggregates, completed
 * measurements (StatEntry) and HealthDay rows — the cloud schema has no table
 * for them on purpose.
 */
class CloudSync(
    private val repo: Repository,
    private val account: AccountRepository,
) {
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
        }.recoverCatching { error ->
            throw IllegalStateException(Cloud.explain(error))
        }
    }

    suspend fun leaderboard(): Result<List<LeaderboardRow>> {
        val client = Cloud.requireConfigured.getOrElse { return failure(it) }
        return runCatching {
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
                )
            }
        }.recoverCatching { error ->
            throw IllegalStateException(Cloud.explain(error))
        }
    }

    suspend fun friends(): Result<List<FriendRow>> {
        val me = requireAccount(account).getOrElse { return failure(it) }
        val client = Cloud.requireConfigured.getOrElse { return failure(it) }
        return runCatching {
            // RLS already shows only rows where we are a party; the or-filter
            // just keeps the decode explicit.
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
            // Pending profiles of "friends"-visibility hunters may be hidden
            // from us — those decode as a fallback name instead of failing.
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
        }.recoverCatching { error ->
            if (error is io.github.jan.supabase.postgrest.exception.PostgrestRestException &&
                error.code == "23505"
            ) {
                throw IllegalStateException("Already requested — waiting for \"$name\" to accept")
            }
            throw IllegalStateException(Cloud.explain(error))
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
        }.recoverCatching { error ->
            throw IllegalStateException(Cloud.explain(error))
        }
    }

    suspend fun friendSessions(userId: String, limit: Int = 20): Result<List<FriendSession>> {
        val client = Cloud.requireConfigured.getOrElse { return failure(it) }
        return runCatching {
            client.postgrest.from("sessions").select(Columns.raw("*, session_sets(count)")) {
                // NULL comparison in SQL filters out uncompleted sessions.
                filter { gt("completed_at", "1970-01-02T00:00:00Z") }
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
     */
    suspend fun feed(limit: Int = 50, beforeMs: Long? = null): Result<List<FeedEntry>> {
        val client = Cloud.requireConfigured.getOrElse { return failure(it) }
        return runCatching {
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
                )
            }
        }.recoverCatching { error ->
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

