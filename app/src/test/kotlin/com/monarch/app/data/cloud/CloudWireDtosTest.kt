package com.monarch.app.data.cloud

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
}
