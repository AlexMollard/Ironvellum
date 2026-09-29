package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The workout clock: elapsed figure, set costs and the estimate lines. */
class SessionClockTest {

    private val metrics = mapOf(1L to ExerciseMetric.REPS, 2L to ExerciseMetric.REPS, 3L to ExerciseMetric.HOLD)
    private val metricOf: (Long) -> ExerciseMetric? = { metrics[it] }

    private fun deadlift(done: Boolean = false) =
        SessionSet(exerciseId = 1, exerciseName = "Deadlift", setIndex = 0, reps = 5, done = done)

    private fun curl(done: Boolean = false) =
        SessionSet(exerciseId = 2, exerciseName = "Bicep Curl", setIndex = 0, reps = 12, done = done)

    private fun hold(seconds: Int, done: Boolean = false) =
        SessionSet(exerciseId = 3, exerciseName = "Plank", setIndex = 0, reps = 0, durationSec = seconds, done = done)

    @Test
    fun `the profiles the costs below rely on`() {
        assertTrue(MuscleMap.profile("Deadlift")!!.compound)
        assertFalse(MuscleMap.profile("Bicep Curl")!!.compound)
    }

    @Test
    fun `remaining time counts only the sets not yet done`() {
        val sets = listOf(deadlift(done = true), deadlift(), curl(done = true), curl())
        // Strength: compound 300 s rest + 40 s work, isolation 90 + 40.
        assertEquals(340 + 130, SessionClock.remainingSeconds(sets, metricOf, TrainingFocus.STRENGTH))
        assertEquals(2 * 340 + 2 * 130, SessionClock.totalSeconds(sets, metricOf, TrainingFocus.STRENGTH))
        // Muscle focus shortens compound rest to 150 s.
        assertEquals(190 + 130, SessionClock.remainingSeconds(sets, metricOf, TrainingFocus.MUSCLE))
    }

    @Test
    fun `a finished session has nothing left`() {
        val sets = listOf(deadlift(done = true), curl(done = true))
        assertEquals(0, SessionClock.remainingSeconds(sets, metricOf, TrainingFocus.STRENGTH))
        assertEquals("EST 8 MIN", SessionClock.estimateLine(340 + 130, 0))
    }

    @Test
    fun `a hold costs its own seconds plus rest, not the counted-set work time`() {
        val plankRest = ProgramRules.restSeconds(TrainingFocus.MUSCLE, MuscleMap.profile("Plank")?.compound ?: true)
        assertEquals(plankRest + 90, SessionClock.setSeconds(hold(90), ExerciseMetric.HOLD, TrainingFocus.MUSCLE))
        assertEquals(plankRest + 20, SessionClock.setSeconds(hold(20), ExerciseMetric.HOLD, TrainingFocus.MUSCLE))
        // A legacy hold keeps its seconds in reps.
        val legacy = SessionSet(exerciseId = 3, exerciseName = "Plank", setIndex = 0, reps = 45)
        assertEquals(plankRest + 45, SessionClock.setSeconds(legacy, ExerciseMetric.REPS, TrainingFocus.MUSCLE))
        // Done holds drop out of what is left.
        assertEquals(
            plankRest + 60,
            SessionClock.remainingSeconds(listOf(hold(60), hold(60, done = true)), metricOf, TrainingFocus.MUSCLE),
        )
    }

    @Test
    fun `minutes round up so a set still ahead never reads as zero`() {
        assertEquals(0, SessionClock.minutes(0))
        assertEquals(1, SessionClock.minutes(1))
        assertEquals(1, SessionClock.minutes(60))
        assertEquals(2, SessionClock.minutes(61))
        assertEquals("EST 58 MIN · ~31 LEFT", SessionClock.estimateLine(58 * 60, 30 * 60 + 5))
    }

    @Test
    fun `the plan line uses the generator's session estimate`() {
        val entries = listOf(
            PlannedEntry(exerciseName = "Deadlift", sets = 3, reps = 5, targetWeightKg = null),
            PlannedEntry(exerciseName = "Bicep Curl", sets = 2, reps = 12, targetWeightKg = null),
        )
        // 3 × 340 + 2 × 130 = 1280 s → 22 min.
        assertEquals("2 MOVES · 5 SETS · ~22 MIN", SessionClock.planLine(entries, TrainingFocus.STRENGTH))
    }

    @Test
    fun `elapsed reads m ss under an hour and h mm ss past it`() {
        assertEquals("0:00", SessionClock.elapsedLabel(0))
        assertEquals("0:59", SessionClock.elapsedLabel(59_999))
        assertEquals("4:07", SessionClock.elapsedLabel(247_000))
        assertEquals("59:59", SessionClock.elapsedLabel(3_599_000))
        assertEquals("1:00:00", SessionClock.elapsedLabel(3_600_000))
        assertEquals("1:04:07", SessionClock.elapsedLabel(3_847_000))
        assertEquals("0:00", SessionClock.elapsedLabel(-5_000))
    }

    @Test
    fun `saved focus outranks the profile mode`() {
        assertEquals(TrainingFocus.SKILL, SessionClock.focusFor(TrainingFocus.SKILL, TrainingMode.STRENGTH))
        assertEquals(TrainingFocus.STRENGTH, SessionClock.focusFor(null, TrainingMode.STRENGTH))
        assertEquals(TrainingFocus.MUSCLE, SessionClock.focusFor(null, TrainingMode.HYPERTROPHY))
        assertEquals(TrainingFocus.MUSCLE, SessionClock.focusFor(null, null))
    }
}
