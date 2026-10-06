package com.ironvellum.app.ui.dashboard

import com.ironvellum.app.domain.Exercise
import com.ironvellum.app.domain.MuscleGroup
import com.ironvellum.app.domain.PresetEntry
import com.ironvellum.app.domain.SessionSet
import com.ironvellum.app.domain.WorkoutPreset
import com.ironvellum.app.domain.WorkoutSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Today day card's state, its movement rows and the Veil's full-strength bar. */
class TodayStateTest {

    private val rite = WorkoutPreset(id = 1, name = "Full Body B", scheduledDay = 3)
    private val trial = WorkoutSession(id = 9, presetId = 1, label = "Full Body B", startedAtMs = 0)

    @Test
    fun `a live trial wins today, a sealed rite wins over its Begin`() {
        assertEquals(DayKind.LIVE, dayKind(true, rite, isToday = true, live = trial, sealed = trial))
        assertEquals(DayKind.SEALED, dayKind(true, rite, isToday = true, live = null, sealed = trial))
        assertEquals(DayKind.BEGIN, dayKind(true, rite, isToday = true, live = null, sealed = null))
    }

    @Test
    fun `another day only shows its rite, even with a trial live`() {
        assertEquals(DayKind.PLANNED, dayKind(true, rite, isToday = false, live = trial, sealed = null))
        assertEquals(DayKind.SEALED, dayKind(true, rite, isToday = false, live = null, sealed = trial))
    }

    @Test
    fun `no rite on the day is respite, and no cycle at all is its own state`() {
        assertEquals(DayKind.RESPITE, dayKind(true, null, isToday = true, live = null, sealed = null))
        assertEquals(DayKind.NO_CYCLE, dayKind(false, null, isToday = true, live = null, sealed = null))
        // An open trial under way still gets the Continue card on today, with no cycle.
        assertEquals(DayKind.LIVE, dayKind(false, null, isToday = true, live = trial, sealed = null))
    }

    private fun set(position: Int, index: Int, done: Boolean, kg: Double = 60.0, reps: Int = 5, warmup: Boolean = false) =
        SessionSet(exerciseId = position + 1L, exerciseName = "Lift $position", exercisePosition = position, setIndex = index, reps = reps, weightKg = kg, done = done, warmup = warmup)

    private val catalogue = (1L..3L).associateWith { Exercise(id = it, name = "Lift ${it - 1}", muscleGroup = MuscleGroup.entries.first(), isWeighted = true) }

    @Test
    fun `planned rows read sets by reps`() {
        val rows = plannedRows(listOf(PresetEntry(exerciseId = 1, exerciseName = "Back squat", targetSets = 3, targetReps = 7)))
        assertEquals(listOf(DayRow("Back squat", "3×7", 0, 3, false)), rows)
    }

    @Test
    fun `a live trial shows done, part way and untouched rows, warm-ups left out`() {
        val sets = listOf(
            set(0, 0, true), set(0, 1, true),
            set(1, 0, true), set(1, 1, false), set(1, 2, false),
            set(2, 0, false, reps = 8), set(2, 1, false, reps = 8),
            set(2, 2, false, warmup = true),
        )
        val rows = trialRows(sets, catalogue, sealed = false)
        assertEquals(listOf("2/2", "1/3", "2×8"), rows.map { it.value })
        assertEquals(listOf(true, false, false), rows.map { it.checked })
        assertEquals(listOf(false, true, false), rows.map { it.partly })
    }

    @Test
    fun `a sealed trial names each movement's best set and ticks what was logged`() {
        val sets = listOf(set(0, 0, true, kg = 80.0, reps = 5), set(0, 1, true, kg = 90.0, reps = 7), set(1, 0, false))
        val rows = trialRows(sets, catalogue, sealed = true)
        assertEquals("2/2 · 90 kg × 7", rows[0].value)
        assertTrue(rows[0].checked)
        assertEquals("0/1", rows[1].value)
        assertFalse(rows[1].checked)
    }

    @Test
    fun `the plan line reads as a sentence`() {
        assertEquals("5 exercises · 19 sets · about 54 min", sentencePlan("5 EXERCISES · 19 SETS · ~54 MIN"))
    }

    private val hour = 3_600_000L

    @Test
    fun `the Veil bar fills across the full-strength day then holds full while it tapers`() {
        val start = 1_000_000_000L
        assertEquals(0.375f, veilStrength(start, start + 9 * hour)!!.fraction, 0.001f)
        assertEquals("9 h of 24 at full strength, then it tapers", veilStrength(start, start + 9 * hour)!!.caption)
        // 48 h: 24 h into a 48 h taper, halfway down 1.0 to 0.1.
        val taper = veilStrength(start, start + 48 * hour)!!
        assertEquals(1f, taper.fraction, 0.0001f)
        assertEquals("Tapering, at 55% strength", taper.caption)
        // Past the taper it holds at a tenth.
        assertEquals("Tapering, at 10% strength", veilStrength(start, start + 200 * hour)!!.caption)
    }

    @Test
    fun `the Veil bar is absent with no baseline or a clock set backwards`() {
        assertNull(veilStrength(0L, 5 * hour))
        assertNull(veilStrength(10 * hour, 5 * hour))
        assertNotNull(veilStrength(10 * hour, 10 * hour))
    }

    @Test
    fun `the rate drops a trailing zero only when whole`() {
        assertEquals("40", rateLabel(40.0))
        assertEquals("12.5", rateLabel(12.5))
    }
}
