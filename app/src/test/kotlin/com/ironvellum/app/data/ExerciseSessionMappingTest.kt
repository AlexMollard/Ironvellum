package com.ironvellum.app.data

import androidx.health.connect.client.records.ExerciseSessionRecord
import com.ironvellum.app.domain.WorkoutSession
import com.ironvellum.app.ui.settings.WriteStatus
import com.ironvellum.app.ui.settings.writeStatus
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What a finished workout becomes in Health Connect: times, type, name, ids, versions, and what is left out. */
class ExerciseSessionMappingTest {

    private val zone = ZoneId.of("Europe/London")
    private val start = 1_760_000_000_000L
    private val end = start + 55 * 60_000L

    private fun finished(
        id: Long = 7,
        label: String = "Push A",
        title: String = "",
        note: String = "",
        imported: Boolean = false,
        completed: Long? = end,
        edited: Long? = null,
    ) = WorkoutSession(
        id = id, label = label, startedAtMs = start, completedAtMs = completed,
        title = title, note = note, imported = imported, editedAtMs = edited,
    )

    private fun spec(session: WorkoutSession, now: Long = end + 1_000) =
        ExerciseSessionMapping.specFor(session, now, zone)

    @Test
    fun `a finished workout is one strength training record over its own start and end`() {
        val s = spec(finished())!!
        assertEquals(Instant.ofEpochMilli(start), s.start)
        assertEquals(Instant.ofEpochMilli(end), s.end)
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING, s.exerciseType)
        assertEquals(zone.rules.getOffset(Instant.ofEpochMilli(start)), s.startOffset)
    }

    @Test
    fun `the offsets are the zone's own, so a workout across a clock change keeps the wall time`() {
        // 2026-03-29 00:30 UTC to 01:30 UTC: London goes from GMT to BST at 01:00 UTC.
        val from = Instant.parse("2026-03-29T00:30:00Z").toEpochMilli()
        val to = Instant.parse("2026-03-29T01:30:00Z").toEpochMilli()
        val s = ExerciseSessionMapping.specFor(
            WorkoutSession(id = 1, label = "x", startedAtMs = from, completedAtMs = to), to, zone,
        )!!
        assertEquals(ZoneOffset.UTC, s.startOffset)
        assertEquals(ZoneOffset.ofHours(1), s.endOffset)
    }

    @Test
    fun `the title is the lifter's own title, else the routine name`() {
        assertEquals("Push A", spec(finished())!!.title)
        assertEquals("Heavy day", spec(finished(title = "  Heavy day "))!!.title)
        assertNull(spec(finished(label = "  "))!!.title)
    }

    @Test
    fun `the notes are the public note, and absent when there is none`() {
        assertEquals("Felt strong", spec(finished(note = " Felt strong "))!!.notes)
        assertNull(spec(finished(note = "   "))!!.notes)
    }

    @Test
    fun `imported workouts and unfinished ones are not written`() {
        assertNull(spec(finished(imported = true)))
        assertNull(spec(finished(completed = null)))
    }

    @Test
    fun `the id is stable for a workout and differs between workouts and between installs`() {
        val a = spec(finished())!!
        assertEquals(a.clientRecordId, spec(finished(note = "edited"), now = end + 99_000)!!.clientRecordId)
        assertNotEquals(a.clientRecordId, spec(finished(id = 8))!!.clientRecordId)
        // A reinstall hands out id 7 again, to a workout that started at another time.
        val reinstalled = ExerciseSessionMapping.specFor(
            WorkoutSession(id = 7, label = "x", startedAtMs = start + 86_400_000L, completedAtMs = end + 86_400_000L),
            end, zone,
        )!!
        assertNotEquals(a.clientRecordId, reinstalled.clientRecordId)
    }

    @Test
    fun `the version rises with the seal, an amendment and a later title or note edit`() {
        val sealed = spec(finished(), now = end)!!.clientRecordVersion
        val amended = spec(finished(edited = end + 5_000), now = end + 5_000)!!.clientRecordVersion
        val retitled = spec(finished(edited = end + 5_000), now = end + 9_000)!!.clientRecordVersion
        assertEquals(end, sealed)
        assertTrue(amended > sealed)
        assertTrue(retitled > amended)
    }

    @Test
    fun `a zero length workout still ends after it starts`() {
        val s = spec(finished(completed = start))!!
        assertTrue(s.end.isAfter(s.start))
        assertNotNull(s.clientRecordId)
    }

    @Test
    fun `the switch reads off, on, or revoked when Health Connect took the permission back`() {
        assertEquals(WriteStatus.OFF, writeStatus(enabled = false, permitted = true))
        assertEquals(WriteStatus.OFF, writeStatus(enabled = false, permitted = null))
        assertEquals(WriteStatus.ON, writeStatus(enabled = true, permitted = true))
        assertEquals(WriteStatus.ON, writeStatus(enabled = true, permitted = null))
        assertEquals(WriteStatus.REVOKED, writeStatus(enabled = true, permitted = false))
    }
}
