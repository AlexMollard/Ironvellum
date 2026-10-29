package com.ironvellum.app.data.cloud

import com.ironvellum.app.domain.FeedCard
import com.ironvellum.app.domain.SessionSet
import com.ironvellum.app.domain.SetKind
import com.ironvellum.app.domain.WorkoutSession
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebUploadTest {
    private val working = SessionSet(exerciseId = 1, exerciseName = "Pull-up", setIndex = 0, reps = 8, done = true)
    private val imported = WorkoutSession(id = 1, label = "Imported", startedAtMs = 1, completedAtMs = 2, imported = true)

    @Test
    fun feedReadyNeeds32() {
        assertFalse(CloudSync.feedReady(null))
        assertFalse(CloudSync.feedReady(31))
        assertTrue(CloudSync.feedReady(32))
        assertTrue(CloudSync.feedReady(33))
    }

    @Test
    fun olderBackendSendsNoFeedCard() {
        val session = WorkoutSession(id = 5, label = "Pull", startedAtMs = 1, completedAtMs = 2)
        val encoder = Json { encodeDefaults = true }
        val old = encoder.encodeToString(CloudSync.sessionDto("u", session, null))
        assertFalse("feed_card" in old)
        val card = FeedCard(9, 600.0, emptyList(), emptyMap())
        val current = encoder.encodeToString(CloudSync.sessionDto("u", session, mapOf(5L to card)))
        assertTrue("\"feed_card\":{\"v\":1" in current)
        // A workout with no card clears a stale one with an explicit null rather than leaving the column alone.
        assertTrue("\"feed_card\":null" in encoder.encodeToString(CloudSync.sessionDto("u", session, emptyMap())))
    }

    @Test
    fun plainWorkingSetsNeedNoDetail() {
        assertTrue(CloudSync.detailRows("user", "session", listOf(working)).isEmpty())
    }

    @Test
    fun detailRowsCarryKindRpeSuperset() {
        val rows = CloudSync.detailRows("user", "session", listOf(
            working.copy(kind = SetKind.TOP, rpe = 9, supersetGroup = 1),
            working.copy(setIndex = 1, kind = SetKind.WARMUP),
        ))
        assertEquals(SessionSetDetailDto("user", "session", "Pull-up", 0, "top", 9, 1), rows[0])
        assertEquals("warmup", rows[1].kind)
    }

    @Test
    fun detailIndexesMatchTheShiftedWireIndexes() {
        val sets = listOf(working, working.copy(exercisePosition = 1, kind = SetKind.TOP))
        val wire = CloudSync.wireSetIndexes(sets)
        assertEquals(listOf(0, 1), wire)
        assertEquals(wire[1], CloudSync.detailRows("user", "session", sets).single().setIndex)
    }

    @Test
    fun importedPendingOnlyImported() {
        val normal = imported.copy(id = 2, imported = false)
        assertEquals(listOf(imported to listOf(working)), CloudSync.pendingImportedForPush(
            listOf(imported to listOf(working), normal to listOf(working)), emptyMap(),
        ))
    }

    @Test
    fun importedSkipsWatermarkedSession() {
        val sets = listOf(working)
        val watermark = mapOf(imported.id to CloudSync.pushFingerprint(imported, sets))
        assertTrue(CloudSync.pendingImportedForPush(listOf(imported to sets), watermark).isEmpty())
        assertEquals(1, CloudSync.pendingImportedForPush(listOf(imported to sets), emptyMap()).size)
    }

    @Test
    fun chunksOf500() {
        val input = (0 until 1201).toList()
        val chunks = CloudSync.chunks(input)
        assertEquals(listOf(500, 500, 201), chunks.map { it.size })
        assertEquals(input, chunks.flatten())
        assertTrue(CloudSync.chunks(emptyList<Int>()).isEmpty())
    }

    @Test
    fun outdatedBackendSkipsWebUploads() {
        assertFalse(CloudSync.webReady(null))
        assertFalse(CloudSync.webReady(29))
        assertTrue(CloudSync.webReady(30))
        assertTrue(CloudSync.webReady(31))
    }

    @Test
    fun generatedImportedIdIsNeverSentAsNullEvenWhenDefaultsAreEncoded() {
        val json = Json { encodeDefaults = true; explicitNulls = true }
        val row = ImportedSessionDto(userId = "user", localId = 1, label = "Imported", title = "", note = "",
            startedAt = "2026-01-01T00:00:00Z", completedAt = null, editedAt = null)
        val encoded = json.parseToJsonElement(json.encodeToString(row)).jsonObject
        assertFalse(encoded.containsKey("id"))
        assertTrue(encoded.containsKey("completed_at"))
        assertTrue(encoded.containsKey("edited_at"))
    }
}
