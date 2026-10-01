package com.ironvellum.app.data.cloud

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import java.time.Instant
import com.ironvellum.app.domain.Lift
import com.ironvellum.app.domain.Warband
import com.ironvellum.app.domain.WarbandMember

/*
 * Wire types for the Supabase REST API. Every @SerialName must match the
 * snake_case column in supabase/migrations/0001_baseline.sql exactly. Domain types
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

/** One lifter's standing on the muster board. */
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
    // SessionAudience.wire. The server check is the same three values.
    @SerialName("audience") val audience: String = "profile",
    // When the owner amended the sealed trial; null (the column's own
    // default) for one never amended. Never sent while null, so a hosted
    // project that predates the column keeps accepting every other push.
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("edited_at") val editedAt: String? = null,
)

/** Just enough of a pushed set row to find the ones an amendment removed. */
@Serializable
data class SessionSetKeyDto(
    @SerialName("id") val id: String,
    @SerialName("session_id") val sessionId: String,
    @SerialName("exercise_name") val exerciseName: String,
    @SerialName("set_index") val setIndex: Int,
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
    /**
     * Columns `session_sets` carries beyond the conflict key. Without
     * `duration_sec` a static hold pushes as `reps = 0` with its seconds
     * nowhere, and an activity pushes with no distance or time at all. All
     * are nullable server-side.
     */
    @SerialName("duration_sec") val durationSec: Int? = null,
    @SerialName("distance_m") val distanceM: Double? = null,
    @SerialName("grade") val grade: String? = null,
    // Order of the exercise within the workout. Nullable and defaulted so a
    // row written before schema 20 still decodes; the ally workout view sorts
    // nulls last.
    @SerialName("exercise_position") val exercisePosition: Int? = null,
)

/** `lift_marks` insert shape; only tier steps leave the phone, never bodyweight. */
@Serializable
data class LiftMarkDto(
    @SerialName("user_id") val userId: String,
    @SerialName("lift") val lift: String,
    @SerialName("step") val step: Int,
    @SerialName("recent_step") val recentStep: Int? = null,
    @SerialName("recent_at") val recentAt: String? = null,
)

/** Decode-only shape of the `lift_board` view. `lift` stays a string so a lift this build lacks is skipped, not thrown. */
@Serializable
data class LiftBoardDto(
    @SerialName("user_id") val userId: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("current_title_id") val currentTitleId: String? = null,
    @SerialName("level") val level: Int = 1,
    @SerialName("lift") val lift: String,
    @SerialName("step") val step: Int,
    @SerialName("recent_step") val recentStep: Int? = null,
) {
    /** Null for a lift this build does not know. */
    internal fun toRow(): LiftBoardRow? = Lift.fromWire(lift)?.let {
        LiftBoardRow(userId, displayName, level, currentTitleId, it, step, recentStep)
    }
}

/** Decode-only projection of the `sessions` row an ally workout view reads. */
@Serializable
data class AllyWorkoutSessionDto(
    @SerialName("id") val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("label") val label: String,
    @SerialName("title") val title: String = "",
    @SerialName("note") val note: String = "",
    @SerialName("completed_at") val completedAt: String? = null,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("xp_awarded") val xpAwarded: Int = 0,
    @SerialName("strength_score") val strengthScore: Int = 0,
)

/** Decode-only projection of `session_sets` for the ally workout view. */
@Serializable
data class AllySetDto(
    @SerialName("exercise_name") val exerciseName: String,
    @SerialName("set_index") val setIndex: Int,
    @SerialName("reps") val reps: Int = 0,
    @SerialName("weight_kg") val weightKg: Double? = null,
    @SerialName("duration_sec") val durationSec: Int? = null,
    @SerialName("distance_m") val distanceM: Double? = null,
    @SerialName("grade") val grade: String? = null,
    @SerialName("modifiers") val modifiers: String = "",
    @SerialName("done") val done: Boolean = true,
    @SerialName("exercise_position") val exercisePosition: Int? = null,
)

data class LiftBoardRow(
    val userId: String,
    val displayName: String,
    val level: Int,
    val currentTitleId: String?,
    val lift: Lift,
    val step: Int,
    val recentStep: Int?,
)

data class AllyWorkout(
    val sessionId: String,
    val userId: String,
    val headline: String,
    val note: String,
    val completedAtMs: Long?,
    val startedAtMs: Long?,
    val xpAwarded: Int,
    val strengthScore: Int,
    val exercises: List<AllyExercise>,
)

/** In exercise_position order (null positions last, then name). */
data class AllyExercise(val name: String, val sets: List<AllySet>)

data class AllySet(
    val setIndex: Int,
    val reps: Int,
    val weightKg: Double?,
    val durationSec: Int?,
    val distanceM: Double?,
    val grade: String?,
    val modifiers: String,
    val done: Boolean,
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
    // Defaulted: a backend without the column still decodes.
    @SerialName("edited_at") val editedAt: String? = null,
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
    /** Seconds held across the session's static holds; 0 when it had none. */
    val heldSeconds: Int,
    val currentTitleId: String?,
    val likeCount: Int,
    val likedByMe: Boolean,
    /** Up to 3 heaviest-volume movements, " · "-joined; null for archives written before this column. */
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
    val commentCount: Int = 0,
    /** Reaction kind -> count; kinds this build does not know are left out. [likeCount] is the total. */
    val reactions: Map<Reaction, Int> = emptyMap(),
    /** The caller's own reaction; null when none (or one this build does not know). */
    val myReaction: Reaction? = null,
    /** When the owner amended the workout; null when never amended. */
    val editedAtMs: Long? = null,
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
    // Defaulted: a backend on an older schema has no such column,
    // and one missing field must not fail the whole page's decode.
    @SerialName("held_seconds") val heldSeconds: Long = 0,
    // The worn title id (null when bare).
    @SerialName("current_title_id") val currentTitleId: String? = null,
    // Server-computed like aggregates: one request per page, not per card.
    @SerialName("like_count") val likeCount: Int = 0,
    @SerialName("liked_by_me") val likedByMe: Boolean = false,
    // Session depth: what was trained. Nullable where the view can yield null.
    @SerialName("top_movements") val topMovements: String? = null,
    @SerialName("best_set") val bestSet: String? = null,
    @SerialName("duration_sec") val durationSec: Int? = null,
    @SerialName("distance_m") val distanceM: Double? = null,
    @SerialName("hardest_grade") val hardestGrade: String? = null,
    @SerialName("movement_count") val movementCount: Int = 0,
    // Later columns, all defaulted so a view from an older schema still decodes.
    @SerialName("comment_count") val commentCount: Int = 0,
    // jsonb kind -> count. Kept as a raw object and filtered in
    // reactionCounts(): a typed Map<Reaction, Int> would throw on the first
    // kind a newer server adds and blank the whole feed page.
    @SerialName("reactions") val reactions: JsonObject? = null,
    @SerialName("my_reaction") val myReaction: String? = null,
    @SerialName("edited_at") val editedAt: String? = null,
) {
    /** Known kinds with a positive count; anything else is skipped, never thrown. */
    internal fun reactionCounts(): Map<Reaction, Int> = buildMap {
        reactions?.forEach { (kind, count) ->
            val reaction = Reaction.fromWire(kind) ?: return@forEach
            val n = (count as? JsonPrimitive)?.intOrNull ?: return@forEach
            if (n > 0) put(reaction, n)
        }
    }

    internal fun toFeedEntry() = FeedEntry(
        sessionId = sessionId,
        userId = userId,
        displayName = displayName,
        level = level,
        title = title,
        note = note,
        label = label,
        completedAtMs = completedAt?.let { Instant.parse(it).toEpochMilli() },
        xpAwarded = xpAwarded,
        strengthScore = strengthScore,
        setsDone = setsDone,
        repsDone = repsDone.toInt(),
        heldSeconds = heldSeconds.toInt(),
        currentTitleId = currentTitleId,
        likeCount = likeCount,
        likedByMe = likedByMe,
        topMovements = topMovements,
        bestSet = bestSet,
        distanceM = distanceM,
        hardestGrade = hardestGrade,
        movementCount = movementCount,
        durationSec = durationSec,
        commentCount = commentCount,
        reactions = reactionCounts(),
        myReaction = Reaction.fromWire(myReaction),
        editedAtMs = editedAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() },
    )
}

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

/** One lifter who reacted to a session, newest first. */
data class Liker(
    val userId: String,
    val displayName: String,
    val likedAtMs: Long,
    /** Null for a kind this build does not know. */
    val reaction: Reaction? = null,
)

@Serializable
data class SessionLikeDto(
    @SerialName("session_id") val sessionId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("kind") val kind: String,
)

/** Decode-only shape for the profiles embed on session_likes. */
@Serializable
data class LikerRowDto(
    @SerialName("user_id") val userId: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("kind") val kind: String? = null,
    @SerialName("profiles") val profile: LikerProfileDto? = null,
)

@Serializable
data class LikerProfileDto(
    @SerialName("display_name") val displayName: String,
)

/** `session_likes.kind`. One reaction per lifter per workout; [wire] is the column value. */
enum class Reaction(val wire: String) {
    SALUTE("salute"),
    IRON("iron"),
    FLAME("flame"),
    ;

    companion object {
        // Null, never a throw: a kind added by a later server must not crash
        // (or blank the feed of) a lifter still on this build.
        fun fromWire(value: String?): Reaction? = entries.firstOrNull { it.wire == value }
    }
}

/** `reports.reason`. */
enum class ReportReason(val wire: String) {
    SPAM("spam"),
    ABUSE("abuse"),
    CHEATING("cheating"),
    OTHER("other"),
}

/** One comment on a workout, as the thread shows it. */
data class Comment(
    val id: String,
    val sessionId: String,
    val userId: String,
    val authorName: String,
    val body: String,
    val createdAtMs: Long,
)

/** A `session_comments` row as read back; author_name and created_at are set by trigger. */
@Serializable
data class CommentDto(
    @SerialName("id") val id: String,
    @SerialName("session_id") val sessionId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("author_name") val authorName: String = "",
    @SerialName("body") val body: String,
    @SerialName("created_at") val createdAt: String,
) {
    internal fun toComment() = Comment(
        id = id,
        sessionId = sessionId,
        userId = userId,
        authorName = authorName.ifBlank { "Hidden Ironbound" },
        body = body,
        createdAtMs = Instant.parse(createdAt).toEpochMilli(),
    )
}

/** Insert shape: only what the client may state; the trigger fills the rest. */
@Serializable
data class CommentInsertDto(
    @SerialName("session_id") val sessionId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("body") val body: String,
)

/** Read-back of a comment delete: the ids that were actually removed. */
@Serializable
data class CommentIdDto(
    @SerialName("id") val id: String,
)

sealed interface InboxItem {
    val occurredAtMs: Long
    val actorId: String
    val actorName: String

    data class FriendRequest(
        override val occurredAtMs: Long,
        override val actorId: String,
        override val actorName: String,
    ) : InboxItem

    data class RequestAccepted(
        override val occurredAtMs: Long,
        override val actorId: String,
        override val actorName: String,
    ) : InboxItem

    data class NewComment(
        override val occurredAtMs: Long,
        override val actorId: String,
        override val actorName: String,
        val sessionId: String,
        val sessionHeadline: String,
        val commentId: String,
        val body: String,
    ) : InboxItem

    /**
     * A remark on someone else's trial, in a thread the caller remarked in
     * first. The thread's owner is not the caller, so it opens without one.
     */
    data class NewReply(
        override val occurredAtMs: Long,
        override val actorId: String,
        override val actorName: String,
        val sessionId: String,
        val sessionHeadline: String,
        val commentId: String,
        val body: String,
    ) : InboxItem

    data class NewReaction(
        override val occurredAtMs: Long,
        override val actorId: String,
        override val actorName: String,
        val sessionId: String,
        val sessionHeadline: String,
        val reaction: Reaction,
    ) : InboxItem

    /** A lifter joined the caller's warband; [bandName] carries the band. */
    data class NewBandmate(
        override val occurredAtMs: Long,
        override val actorId: String,
        override val actorName: String,
        val bandName: String,
    ) : InboxItem

    /**
     * The caller's circle reached its weekly goal; [actorId] sealed the trial
     * that crossed it, and [bandName] carries the circle.
     */
    data class CircleGoalMet(
        override val occurredAtMs: Long,
        override val actorId: String,
        override val actorName: String,
        val bandName: String,
    ) : InboxItem
}

data class Inbox(val items: List<InboxItem>, val seenAtMs: Long?) {
    val unread: Int get() = items.count { seenAtMs == null || it.occurredAtMs > seenAtMs }
}

/** One row returned by `my_inbox()`. */
@Serializable
data class InboxRowDto(
    @SerialName("kind") val kind: String,
    @SerialName("occurred_at") val occurredAt: String,
    @SerialName("actor_id") val actorId: String,
    @SerialName("actor_name") val actorName: String? = null,
    @SerialName("session_id") val sessionId: String? = null,
    @SerialName("session_headline") val sessionHeadline: String? = null,
    @SerialName("comment_id") val commentId: String? = null,
    @SerialName("body") val body: String? = null,
    @SerialName("reaction") val reaction: String? = null,
) {
    /**
     * Null for a row this build cannot show: an unknown kind or reaction
     * from a newer server, or a comment/reaction missing its workout. One
     * such row is dropped; it must not fail the whole inbox.
     */
    internal fun toInboxItem(): InboxItem? {
        val at = Instant.parse(occurredAt).toEpochMilli()
        val name = actorName?.takeIf { it.isNotBlank() } ?: "Hidden Ironbound"
        return when (kind) {
            "request" -> InboxItem.FriendRequest(at, actorId, name)
            "accepted" -> InboxItem.RequestAccepted(at, actorId, name)
            "comment" -> InboxItem.NewComment(
                occurredAtMs = at,
                actorId = actorId,
                actorName = name,
                sessionId = sessionId ?: return null,
                sessionHeadline = sessionHeadline.orEmpty(),
                commentId = commentId ?: return null,
                body = body.orEmpty(),
            )
            "reply" -> InboxItem.NewReply(
                occurredAtMs = at,
                actorId = actorId,
                actorName = name,
                sessionId = sessionId ?: return null,
                sessionHeadline = sessionHeadline.orEmpty(),
                commentId = commentId ?: return null,
                body = body.orEmpty(),
            )
            "reaction" -> InboxItem.NewReaction(
                occurredAtMs = at,
                actorId = actorId,
                actorName = name,
                sessionId = sessionId ?: return null,
                sessionHeadline = sessionHeadline.orEmpty(),
                reaction = Reaction.fromWire(reaction) ?: return null,
            )
            "band_join" -> InboxItem.NewBandmate(
                occurredAtMs = at,
                actorId = actorId,
                actorName = name,
                bandName = body.orEmpty(),
            )
            "band_goal" -> InboxItem.CircleGoalMet(
                occurredAtMs = at,
                actorId = actorId,
                actorName = name,
                bandName = body.orEmpty(),
            )
            else -> null
        }
    }
}

/** `inbox_seen`: when the lifter last opened the inbox (server time). */
@Serializable
data class InboxSeenDto(
    @SerialName("user_id") val userId: String? = null,
    @SerialName("seen_at") val seenAt: String,
)

data class BlockedLifter(val userId: String, val displayName: String)

/** A `blocks` row as the BLOCKED list reads it. blocked_name is filled by trigger: a blocked profile becomes unreadable. */
@Serializable
data class BlockDto(
    @SerialName("blocked_id") val blockedId: String,
    @SerialName("blocked_name") val blockedName: String? = null,
)

/** A `mutes` row as read back; same shape as [BlockDto]. */
@Serializable
data class MuteDto(
    @SerialName("muted_id") val mutedId: String,
    @SerialName("muted_name") val mutedName: String? = null,
)

/*
 * Insert shapes: identity only. A read shape's null name default, sent by an
 * encoder that writes defaults, would land on the trigger-filled `not null`
 * name column.
 */
@Serializable
data class BlockInsertDto(
    @SerialName("blocker_id") val blockerId: String,
    @SerialName("blocked_id") val blockedId: String,
)

@Serializable
data class MuteInsertDto(
    @SerialName("muter_id") val muterId: String,
    @SerialName("muted_id") val mutedId: String,
)

/** Insert-only: no client can read `reports` back. */
@Serializable
data class ReportDto(
    @SerialName("reporter_id") val reporterId: String,
    @SerialName("target_user_id") val targetUserId: String,
    @SerialName("session_id") val sessionId: String? = null,
    @SerialName("comment_id") val commentId: String? = null,
    @SerialName("reason") val reason: String,
    @SerialName("note") val note: String,
)

data class FriendSession(
    val label: String,
    val title: String,
    val note: String,
    val completedAtMs: Long?,
    val xpAwarded: Int,
    val strengthScore: Int,
    val sets: Int,
    /** When the owner amended the workout; null when never amended. */
    val editedAtMs: Long? = null,
)

data class SyncOutcome(
    val sessions: Int,
    val sets: Int,
    val titles: Int,
    val problems: List<String>,
)

/**
 * One row of `cloud_archives` — the lifter's whole save as JSON text, one row
 * per user, always the latest (upserted on user_id). The archive never carries
 * the private note; see CloudSync.restoreArchive.
 */
@Serializable
data class ArchiveDto(
    @SerialName("user_id") val userId: String,
    @SerialName("archive") val archive: String,
    @SerialName("size_bytes") val sizeBytes: Int,
    // Decode-only: the server default fills it; the client never sends it.
    @SerialName("updated_at") val updatedAt: String? = null,
)

/**
 * The backup-status read: two columns only, so the archive text is never
 * downloaded. Decoding that select as [ArchiveDto] threw on the missing
 * user_id/archive and the account screen showed a generic failure.
 */
@Serializable
data class ArchiveStatusDto(
    @SerialName("size_bytes") val sizeBytes: Int,
    @SerialName("updated_at") val updatedAt: String? = null,
)

/*
 * RPC argument shapes. These exist so the parameter names are declared in ONE
 * place and can be checked against the function signatures in
 * supabase/migrations — a hand-built JsonObject puts the same strings at the
 * call site where nothing can verify them, and a renamed parameter fails only
 * at runtime, on a user's device, as a 404 from PostgREST.
 */

/** Arguments of `push_aggregates` (supabase/migrations/0001_baseline.sql). */
@Serializable
data class PushAggregatesArgs(
    @SerialName("p_total_xp") val totalXp: Long,
    @SerialName("p_lifetime_strength") val lifetimeStrength: Long,
    @SerialName("p_streak_days") val streakDays: Int,
    @SerialName("p_shadow_essence") val shadowEssence: Long,
    @SerialName("p_shadow_count") val shadowCount: Int,
    @SerialName("p_shadow_rate") val shadowRate: Double,
)

/** Arguments of `find_hunter` (supabase/migrations/0001_baseline.sql). */
@Serializable
data class FindHunterArgs(
    @SerialName("name") val name: String,
)

/** Arguments of `create_warband` (supabase/migrations/0001_baseline.sql). */
@Serializable
data class CreateWarbandArgs(
    @SerialName("p_name") val name: String,
)

/** Arguments of `join_warband` (supabase/migrations/0001_baseline.sql). */
@Serializable
data class JoinWarbandArgs(
    @SerialName("p_code") val code: String,
)

/** Args of `set_warband_goal(int)`: the band's weekly workout goal, 5..50. */
@Serializable
data class SetWarbandGoalArgs(
    @SerialName("p_goal") val goal: Int,
)

/**
 * One bandmate as `my_warband()` reports them. A bandmate whose profile row is
 * missing still lists — the server already substituted the neutral
 * "Lifter" + short-id handle, so no client-side fallback is needed.
 */
@Serializable
data class WarbandMemberDto(
    @SerialName("user_id") val userId: String,
    @SerialName("display_name") val displayName: String,
    // Null when the bandmate's profile is hidden from the caller.
    @SerialName("level") val level: Int? = null,
    @SerialName("current_title_id") val currentTitleId: String? = null,
    // Days trained in the current Monday-start week (UTC anchor): distinct UTC
    // days with a trial that has a set, counted server-side, the same for every
    // viewer whatever the audience of the trials behind it.
    @SerialName("workouts_this_week") val workoutsThisWeek: Int = 0,
    @SerialName("last_workout_at") val lastWorkoutAt: String? = null,
)

/** One row of `my_warband()`: the caller's band and roster, oldest member first. */
@Serializable
data class WarbandDto(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("code") val code: String,
    @SerialName("owner_id") val ownerId: String,
    // The owner's weekly challenge for the band; server default 12.
    @SerialName("weekly_goal") val weeklyGoal: Int = 12,
    // Days trained by the whole band this week: ONE canonical number, the same
    // for every viewer. Absent only from a server older than schema 27, where
    // the members' own counts are all there is to sum.
    @SerialName("band_total") val bandTotal: Int? = null,
    @SerialName("members") val members: List<WarbandMemberDto> = emptyList(),
) {
    fun toWarband(): Warband = Warband(
        id = id,
        name = name,
        code = code,
        ownerId = ownerId,
        weeklyGoal = weeklyGoal,
        total = bandTotal ?: members.sumOf { it.workoutsThisWeek },
        members = members.map {
            WarbandMember(
                userId = it.userId,
                displayName = it.displayName,
                level = it.level,
                titleId = it.currentTitleId,
                workoutsThisWeek = it.workoutsThisWeek,
                lastWorkoutAtMs = it.lastWorkoutAt?.let { at -> Instant.parse(at).toEpochMilli() },
            )
        },
    )
}

/** RPC names, declared once so the guard test and the call sites cannot drift. */
const val RPC_PUSH_AGGREGATES = "push_aggregates"
const val RPC_FIND_HUNTER = "find_hunter"
const val RPC_MY_INBOX = "my_inbox"
const val RPC_MARK_INBOX_SEEN = "mark_inbox_seen"
const val RPC_DISPLAY_NAME_AVAILABLE = "display_name_available"
const val RPC_CREATE_WARBAND = "create_warband"
const val RPC_JOIN_WARBAND = "join_warband"
const val RPC_LEAVE_WARBAND = "leave_warband"
const val RPC_SET_WARBAND_GOAL = "set_warband_goal"
const val RPC_MY_WARBAND = "my_warband"

/**
 * Encodes a typed RPC argument shape into the JsonObject the pinned
 * postgrest-kt `rpc` overload takes. The indirection is the point: parameter
 * names live in the @Serializable class above, where the schema guard can see
 * them, instead of as loose strings at the call site.
 */
internal inline fun <reified T> rpcArgs(args: T): JsonObject =
    Json.encodeToJsonElement(args).jsonObject
