package com.monarch.app.data.cloud

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Wire types for the Supabase REST API. Every @SerialName must match the
 * snake_case column in supabase/migrations/0001_init.sql exactly. Domain types
 * never cross the wire; these are the only shapes PostgREST sees.
 */

@Serializable
data class ProfileDto(
    @SerialName("id") val id: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("visibility") val visibility: String = "friends",
    @SerialName("level") val level: Int = 1,
    @SerialName("total_xp") val totalXp: Long = 0,
    @SerialName("streak_days") val streakDays: Int = 0,
    @SerialName("titles_count") val titlesCount: Int = 0,
    @SerialName("lifetime_strength") val lifetimeStrength: Long = 0,
)

/** Minimal projection when we only need the generated cloud id back. */
@Serializable
data class SessionIdDto(
    @SerialName("id") val id: String,
    @SerialName("local_id") val localId: Long,
)

@Serializable
data class SessionDto(
    // Null on first push: the cloud assigns a uuid. Never sent for the upsert
    // conflict key (user_id, local_id) anyway.
    @SerialName("id") val id: String? = null,
    @SerialName("user_id") val userId: String,
    @SerialName("local_id") val localId: Long,
    @SerialName("label") val label: String,
    @SerialName("started_at") val startedAt: String,
    @SerialName("completed_at") val completedAt: String?,
    @SerialName("xp_awarded") val xpAwarded: Int,
    @SerialName("strength_score") val strengthScore: Int,
    // Public, feed-facing texts. Empty strings hit the DB default; the
    // device-only private note has no column and is never sent.
    @SerialName("title") val title: String = "",
    @SerialName("note") val note: String = "",
)

@Serializable
data class SessionSetDto(
    @SerialName("session_id") val sessionId: String,
    @SerialName("exercise_name") val exerciseName: String,
    @SerialName("set_index") val setIndex: Int,
    @SerialName("reps") val reps: Int,
    @SerialName("weight_kg") val weightKg: Double?,
    @SerialName("modifiers") val modifiers: String,
    @SerialName("done") val done: Boolean,
)

@Serializable
data class EarnedTitleDto(
    @SerialName("user_id") val userId: String,
    @SerialName("title_id") val titleId: String,
    @SerialName("unlocked_at") val unlockedAt: String,
)

@Serializable
data class LeaderboardDto(
    @SerialName("id") val id: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("level") val level: Int,
    @SerialName("total_xp") val totalXp: Long,
    @SerialName("streak_days") val streakDays: Int,
    @SerialName("titles_count") val titlesCount: Int,
    @SerialName("lifetime_strength") val lifetimeStrength: Long,
    @SerialName("sessions_last_7d") val sessionsLast7d: Int,
)

@Serializable
data class FriendshipDto(
    @SerialName("requester_id") val requesterId: String,
    @SerialName("addressee_id") val addresseeId: String,
    @SerialName("accepted") val accepted: Boolean,
)

/** Narrow name lookup so a pending friend's hidden profile still decodes. */
@Serializable
data class ProfileNameDto(
    @SerialName("id") val id: String,
    @SerialName("display_name") val displayName: String,
)

@Serializable
data class FriendSessionDto(
    @SerialName("label") val label: String,
    @SerialName("title") val title: String = "",
    @SerialName("note") val note: String = "",
    @SerialName("completed_at") val completedAt: String?,
    @SerialName("xp_awarded") val xpAwarded: Int,
    @SerialName("strength_score") val strengthScore: Int,
    // PostgREST embed: "*, session_sets(count)" — decode-only, never sent.
    @SerialName("session_sets") val setCounts: List<SetCountRow> = emptyList(),
)

data class FeedEntry(
    val sessionId: String,
    val userId: String,
    val displayName: String,
    val level: Int,
    val title: String,
    val note: String,
    val label: String,
    val completedAtMs: Long?,
    val xpAwarded: Int,
    val strengthScore: Int,
    val setsDone: Int,
    val repsDone: Int,
)

@Serializable
data class FeedEntryDto(
    @SerialName("session_id") val sessionId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("level") val level: Int,
    @SerialName("title") val title: String,
    @SerialName("note") val note: String,
    @SerialName("label") val label: String,
    @SerialName("completed_at") val completedAt: String?,
    @SerialName("xp_awarded") val xpAwarded: Int,
    @SerialName("strength_score") val strengthScore: Int,
    @SerialName("sets_done") val setsDone: Int,
    @SerialName("reps_done") val repsDone: Long,
)

@Serializable
data class SetCountRow(
    @SerialName("count") val count: Long = 0,
)

data class LeaderboardRow(
    val userId: String,
    val displayName: String,
    val level: Int,
    val totalXp: Long,
    val streakDays: Int,
    val titlesCount: Int,
    val lifetimeStrength: Long,
    val sessionsLast7d: Int,
)

data class FriendRow(
    val userId: String,
    val displayName: String,
    val accepted: Boolean,
    val incoming: Boolean,
)

data class FriendSession(
    val label: String,
    val title: String,
    val note: String,
    val completedAtMs: Long?,
    val xpAwarded: Int,
    val strengthScore: Int,
    val sets: Int,
)

data class SyncOutcome(
    val sessions: Int,
    val sets: Int,
    val titles: Int,
    val problems: List<String>,
)
