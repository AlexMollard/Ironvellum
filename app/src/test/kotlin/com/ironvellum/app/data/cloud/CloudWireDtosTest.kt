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

    @Test
    fun `a never-amended trial does not send edited_at`() {
        // supabase-kt may encode defaults; the hosted project may predate the
        // column. Either way a null stamp must not appear in the push body.
        val strict = Json { encodeDefaults = true }
        val dto = SessionDto(
            userId = "u", localId = 1, label = "Legs",
            startedAt = "2026-09-30T08:00:00Z", completedAt = "2026-09-30T09:00:00Z",
            xpAwarded = 10, strengthScore = 0,
        )
        val sent = strict.encodeToJsonElement(dto).jsonObject
        assertEquals(false, "edited_at" in sent)
        val amended = strict.encodeToJsonElement(dto.copy(editedAt = "2026-10-01T08:00:00Z")).jsonObject
        assertEquals(true, "edited_at" in amended)
    }

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
    fun `a circle decodes its roster with the neutral-name and bare-title fallbacks`() {
        val dto = json.decodeFromString<CircleDto>(
            """{"id": "7c9e6a4e-0000-4000-8000-0000000000aa", "name": "North Gate",
                "code": "K7M2PQ4X", "owner_id": "owner-1", "week": "2026-09-28",
                "per_member": 4, "pending_per_member": 2, "goal": 8,
                "circle_total": 5, "roster": 2, "weeks_met": 3,
                "members": [
                  {"user_id": "owner-1", "display_name": "Nova", "level": 9,
                   "current_title_id": "first-blood", "days_this_week": 2, "counts": true,
                   "last_workout_at": "2026-09-28T07:45:00Z"},
                  {"user_id": "u2", "display_name": "Lifterb042", "level": 1,
                   "current_title_id": null, "days_this_week": 0, "counts": false}
                ]}""",
        )
        val circle = dto.toCircle()
        assertEquals("K7M2PQ4X", circle.code)
        assertEquals("owner-1", circle.ownerId)
        assertEquals(4, circle.perMember)
        assertEquals(2, circle.pendingPerMember)
        assertEquals(8, circle.goal)
        // The canonical total is the server's number, not the sum of what this viewer was handed.
        assertEquals(5, circle.total)
        assertEquals(2, circle.roster)
        assertEquals(3, circle.weeksMet)
        // A row with none of the week fields decodes the neutral defaults: no goal, no pending change.
        val bare = json.decodeFromString<CircleDto>(
            """{"id": "7c9e6a4e-0000-4000-8000-0000000000aa", "name": "North Gate",
                "code": "K7M2PQ4X", "owner_id": "owner-1"}""",
        ).toCircle()
        assertEquals(com.ironvellum.app.domain.Circle.DEFAULT_PER_MEMBER, bare.perMember)
        assertNull(bare.pendingPerMember)
        assertEquals(0, bare.goal)
        // A circle mid-handover has no Keeper row: the oldest member holds the keys.
        assertEquals("a", json.decodeFromString<CircleDto>(
            """{"id": "7c9e6a4e-0000-4000-8000-0000000000aa", "name": "North Gate",
                "code": "K7M2PQ4X", "owner_id": null,
                "members": [{"user_id": "a", "display_name": "A"}]}""",
        ).toCircle().ownerId)
        assertEquals(2, circle.members.size)
        assertEquals("first-blood", circle.members[0].titleId)
        assertEquals(2, circle.members[0].daysThisWeek)
        assertEquals(1790581500000L, circle.members[0].lastWorkoutAtMs)
        // A missing profile row still lists, level defaulted, no timestamp.
        assertNull(circle.members[1].titleId)
        assertNull(circle.members[1].lastWorkoutAtMs)
        assertEquals(0, circle.members[1].daysThisWeek)
        assertEquals(false, circle.members[1].counts)
        assertEquals(1, circle.members[1].level)
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
        assertNull("no edited_at column reads as never amended", old.editedAtMs)
        assertNull(entry.editedAtMs)

        // An amended workout carries Postgres's own timestamptz rendering.
        val amended = json.decodeFromString<FeedEntryDto>(
            row.removeSuffix("}") + """, "edited_at": "2026-09-30T08:00:00+00:00"}""",
        ).toFeedEntry()
        assertEquals(1790755200000L, amended.editedAtMs)
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
