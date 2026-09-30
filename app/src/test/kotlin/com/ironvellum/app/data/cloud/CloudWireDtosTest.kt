package com.ironvellum.app.data.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.jsonObject

/**
 * The wire contract between these DTOs and the Supabase schema is carried
 * entirely by @SerialName. Rename a Kotlin property without updating it and
 * kotlinx silently decodes the server value into the default — a null id or a
 * 0kg weight rather than a loud error.
 *
 * The aggregate read-back DTOs that used to be pinned here are gone: since
 * 0011 the ranked numbers are derived inside push_aggregates(), and that
 * contract is proven in supabase/test/aggregates_probe.sql against a real
 * database rather than guessed at from a JSON literal.
 */
class CloudWireDtosTest {

    private val json = Json

    @Test
    fun `a session row decodes its generated id, local_id and nullable completed_at`() {
        val dto = json.decodeFromString<SessionDto>(
            """{"id": "7c9e6a4e-0000-4000-8000-000000000001", "user_id": "u1",
                "local_id": 42, "label": "Push day",
                "started_at": "2026-09-14T07:00:00Z",
                "completed_at": "2026-09-14T07:45:00Z",
                "xp_awarded": 120, "strength_score": 88,
                "title": "", "note": ""}""",
        )
        assertEquals("7c9e6a4e-0000-4000-8000-000000000001", dto.id)
        assertEquals(42L, dto.localId)
        assertEquals("2026-09-14T07:45:00Z", dto.completedAt)
        assertEquals(120, dto.xpAwarded)
    }

    @Test
    fun `a set row decodes a null weight_kg for bodyweight sets`() {
        // Failure mode: weight_kg null is how a bodyweight set travels; if it
        // decoded as 0.0 the restore/import side would rewrite history to 0kg.
        val dto = json.decodeFromString<SessionSetDto>(
            """{"session_id": "s1", "exercise_name": "Pull-up", "set_index": 2,
                "reps": 6, "weight_kg": null, "modifiers": "", "done": false}""",
        )
        assertNull(dto.weightKg)
        assertEquals(false, dto.done)
        assertEquals("Pull-up", dto.exerciseName)
        assertEquals(2, dto.setIndex)
    }

    @Test
    fun `a warband decodes its roster with the neutral-name and bare-title fallbacks`() {
        val dto = json.decodeFromString<WarbandDto>(
            """{"id": "7c9e6a4e-0000-4000-8000-0000000000aa", "name": "North Gate",
                "code": "K7M2PQ4X", "owner_id": "owner-1", "weekly_goal": 9,
                "members": [
                  {"user_id": "owner-1", "display_name": "Nova", "level": 9,
                   "current_title_id": "first-blood", "workouts_this_week": 2,
                   "last_workout_at": "2026-09-28T07:45:00Z"},
                  {"user_id": "u2", "display_name": "Lifterb042", "level": 1,
                   "current_title_id": null, "workouts_this_week": 0}
                ]}""",
        )
        val band = dto.toWarband()
        assertEquals("K7M2PQ4X", band.code)
        assertEquals("owner-1", band.ownerId)
        assertEquals(9, band.weeklyGoal)
        // A band row from before the goal shipped decodes the server default.
        assertEquals(12, json.decodeFromString<WarbandDto>(
            """{"id": "7c9e6a4e-0000-4000-8000-0000000000aa", "name": "North Gate",
                "code": "K7M2PQ4X", "owner_id": "owner-1"}""",
        ).toWarband().weeklyGoal)
        assertEquals(2, band.members.size)
        assertEquals("first-blood", band.members[0].titleId)
        assertEquals(2, band.members[0].workoutsThisWeek)
        assertEquals(1790581500000L, band.members[0].lastWorkoutAtMs)
        // A missing profile row still lists, level defaulted, no timestamp.
        assertNull(band.members[1].titleId)
        assertNull(band.members[1].lastWorkoutAtMs)
        assertEquals(0, band.members[1].workoutsThisWeek)
        assertEquals(1, band.members[1].level)
    }

    @Test
    fun `a reaction kind this build does not know is skipped, never thrown`() {
        // Failure mode: a later server adds a kind. A typed map or a strict
        // valueOf would throw inside decodeList and blank the whole feed page
        // for every lifter still on this build.
        val row = """{"session_id": "s1", "user_id": "u1", "display_name": "Kaida",
            "level": 7, "title": "", "note": "", "label": "Pull",
            "completed_at": "2026-09-29T07:45:00Z", "xp_awarded": 90,
            "strength_score": 40, "sets_done": 5, "reps_done": 30,
            "like_count": 6, "liked_by_me": true, "comment_count": 2,
            "reactions": {"salute": 3, "iron": 1, "thunder": 2},
            "my_reaction": "thunder"}"""
        val entry = json.decodeFromString<FeedEntryDto>(row).toFeedEntry()
        assertEquals(mapOf(Reaction.SALUTE to 3, Reaction.IRON to 1), entry.reactions)
        assertNull(entry.myReaction)
        // The total is the server's, unknown kinds included.
        assertEquals(6, entry.likeCount)
        assertEquals(2, entry.commentCount)
        assertNull(Reaction.fromWire(null))
        assertEquals(Reaction.FLAME, Reaction.fromWire("flame"))

        // A view from before 0018 has none of the new columns at all.
        val old = json.decodeFromString<FeedEntryDto>(
            row.substringBefore(""", "comment_count"""") + "}",
        ).toFeedEntry()
        assertEquals(0, old.commentCount)
        assertEquals(emptyMap<Reaction, Int>(), old.reactions)
        assertNull(old.myReaction)
    }

    @Test
    fun `an unknown lift is skipped and a pre-20 set row decodes without exercise_position`() {
        // Failure mode: a newer build adds a lift. A strict Lift decode would
        // throw inside decodeList and blank every ally's board on this build.
        val rows = json.decodeFromString<List<LiftBoardDto>>(
            """[{"user_id": "u1", "display_name": "Kaida", "level": 7, "lift": "squat", "step": 6, "recent_step": null},
                {"user_id": "u1", "display_name": "Kaida", "level": 7, "lift": "snatch", "step": 9}]""",
        ).mapNotNull { it.toRow() }
        assertEquals(1, rows.size)
        assertEquals(com.ironvellum.app.domain.Lift.SQUAT, rows.single().lift)
        assertEquals(6, rows.single().step)
        assertNull(rows.single().recentStep)

        // Failure mode: rows pushed before schema 20 have no position; a
        // required field would fail the whole ally workout read.
        val old = json.decodeFromString<AllySetDto>(
            """{"exercise_name": "Pull-up", "set_index": 0, "reps": 5, "weight_kg": 10.0,
                "modifiers": "", "done": true}""",
        )
        assertNull(old.exercisePosition)
    }
}
