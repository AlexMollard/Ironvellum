package com.monarch.app.data.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.jsonObject

/**
 * The wire contract between these DTOs and supabase/migrations/0001_init.sql
 * is carried entirely by @SerialName. Rename a Kotlin property without
 * updating it and kotlinx silently decodes the server value into the default:
 * e.g. total_xp reading back as null/0 makes CloudSync's monotonic merge push
 * local values OVER the cloud row instead of maxing — permanent data loss on
 * a fresh install. These decodes pin the snake_case column names for the
 * fields whose loss destroys or resurrects user data.
 */
class CloudWireDtosTest {

    private val json = Json

    @Test
    fun `profile aggregates decode the snake_case server row the monotonic merge reads`() {
        val remote = json.decodeFromString<ProfileAggregatesDto>(
            """{"level": 7, "total_xp": 4200, "titles_count": 3, "lifetime_strength": 987654}""",
        )
        assertEquals(7, remote.level)
        assertEquals(4200L, remote.totalXp)
        assertEquals(3, remote.titlesCount)
        assertEquals(987654L, remote.lifetimeStrength)
    }

    @Test
    fun `a null profile aggregate column decodes as absent, not zero`() {
        // Failure mode: NULL on the server means "unknown" and CloudSync then
        // keeps its local value (?: local). A decode that produced 0 instead
        // would push LV 1 / 0 XP over a healthy cloud row — exactly the
        // destruction the max-merge exists to prevent.
        val remote = json.decodeFromString<ProfileAggregatesDto>(
            """{"level": null, "total_xp": null, "titles_count": null, "lifetime_strength": null}""",
        )
        assertNull(remote.level)
        assertNull(remote.totalXp)
        assertNull(remote.titlesCount)
        assertNull(remote.lifetimeStrength)
    }

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
    fun `the shadow push encodes exactly the three migration-0008 columns`() {
        // Shadow figures go in a separate update so a pre-migration database
        // rejects only this statement; an extra or misnamed key here would
        // either break that isolation or silently fall back to a column
        // default on the server.
        val encoded = json.encodeToJsonElement(
            ShadowPushDto(shadowEssence = 10L, shadowCount = 2, shadowRate = 1.5),
        ).jsonObject
        assertEquals(setOf("shadow_essence", "shadow_count", "shadow_rate"), encoded.keys)
        assertEquals("10", encoded["shadow_essence"].toString())
    }
}
