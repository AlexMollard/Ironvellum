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
    // The worn title id (null when bare). Names resolve locally via
    // Titles.byId — never shipped over the wire.
    @SerialName("current_title_id") val currentTitleId: String? = null,
)

/**
 * The shadow army's shareable aggregate. Kept OUT of [ProfileDto] on purpose:
 * these columns arrive with migration 0008, and pushing them in the same
 * statement as the training aggregates would make a pre-migration database
 * reject the whole profile upsert instead of just the shadow figures.
 */
@Serializable
data class ShadowPushDto(
    @SerialName("shadow_essence") val shadowEssence: Long,
    @SerialName("shadow_count") val shadowCount: Int,
    @SerialName("shadow_rate") val shadowRate: Double,
)

@Serializable
data class ShadowBoardDto(
    @SerialName("id") val id: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("current_title_id") val currentTitleId: String? = null,
    @SerialName("level") val level: Int = 1,
    @SerialName("shadow_essence") val shadowEssence: Long = 0,
    @SerialName("shadow_count") val shadowCount: Int = 0,
    @SerialName("shadow_rate") val shadowRate: Double = 0.0,
)

/** One hunter's standing on the shadow board. */
data class ShadowBoardRow(
    val userId: String,
    val displayName: String,
    val currentTitleId: String?,
    val level: Int,
    val essence: Long,
    val shadows: Int,
    val ratePerHour: Double,
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
    // The worn title id (null when bare); resolved to a name locally.
    @SerialName("current_title_id") val currentTitleId: String? = null,
)

@Serializable
data class FriendshipDto(
    @SerialName("requester_id") val requesterId: String,
    @SerialName("addressee_id") val addresseeId: String,
    @SerialName("accepted") val accepted: Boolean,
)

/**
 * Narrow profile lookup so a pending friend's hidden profile still decodes.
 * Carries the worn title and level too: the select is already `*`, so an ally's
 * crest can show its rarity without a second request.
 */
@Serializable
data class ProfileNameDto(
    @SerialName("id") val id: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("level") val level: Int? = null,
    @SerialName("current_title_id") val currentTitleId: String? = null,
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
    val currentTitleId: String?,
    val likeCount: Int,
    val likedByMe: Boolean,
    /** Up to 3 heaviest-volume movements, " · "-joined; null for pre-migration sessions. */
    val topMovements: String?,
    /**
     * Headline set like "8 x 80.0 kg" or "6 x BW". Null when the hunt had no
     * load-bearing set at all — a run or a climb, whose substance is the
     * distance or the grade below, not a set.
     */
    val bestSet: String?,
    /** Metres covered, null when the hunt covered none. */
    val distanceM: Double?,
    /** Hardest grade attempted, free text (V-scale, Font, YDS all differ). */
    val hardestGrade: String?,
    val movementCount: Int,
    /** Session duration in seconds; null when a timestamp is missing. */
    val durationSec: Int?,
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
    // The worn title id (null when bare).
    @SerialName("current_title_id") val currentTitleId: String? = null,
    // Server-computed like aggregates: one request per page, not per card.
    @SerialName("like_count") val likeCount: Int = 0,
    @SerialName("liked_by_me") val likedByMe: Boolean = false,
    // Session depth: what was trained. Nullable where the view can yield null.
    @SerialName("top_movements") val topMovements: String? = null,
    @SerialName("best_set") val bestSet: String? = null,
    @SerialName("movement_count") val movementCount: Int = 0,
    @SerialName("duration_sec") val durationSec: Int? = null,
    @SerialName("distance_m") val distanceM: Double? = null,
    @SerialName("hardest_grade") val hardestGrade: String? = null,
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
    val currentTitleId: String?,
)

data class FriendRow(
    val userId: String,
    val displayName: String,
    val accepted: Boolean,
    val incoming: Boolean,
    /** Null when their profile is hidden from us (pending, friends-only). */
    val level: Int? = null,
    val currentTitleId: String? = null,
)

/** One hunter who liked a session, newest like first. */
data class Liker(
    val userId: String,
    val displayName: String,
    val likedAtMs: Long,
)

@Serializable
data class SessionLikeDto(
    @SerialName("session_id") val sessionId: String,
    @SerialName("user_id") val userId: String,
)

/** Decode-only shape for the profiles embed on session_likes. */
@Serializable
data class LikerRowDto(
    @SerialName("user_id") val userId: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("profiles") val profile: LikerProfileDto? = null,
)

@Serializable
data class LikerProfileDto(
    @SerialName("display_name") val displayName: String,
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
