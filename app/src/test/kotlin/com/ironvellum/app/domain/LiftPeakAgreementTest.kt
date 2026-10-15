package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One notion of "best set" on the lift page: the Epley estimate of the top set (the same measure the Stats
 * records board uses), never the rep-volume score, which ranks a 90 x 7 back-off above a 100 x 4 top set.
 */
class LiftPeakAgreementTest {
    private val squat = Exercise(id = 1, name = "Back Squat", muscleGroup = MuscleGroup.LEGS, isWeighted = true)
    private val day = 86_400_000L

    private fun row(session: Long, index: Int, reps: Int, kg: Double, kind: String = "WORKING") =
        ExerciseSetRow(session, session * day, index, reps, kg, "", done = true, kind = kind)

    // Three imported sessions of 95 x 5, then a live one: 100 x 4 top set and 90 x 7 back-offs.
    private val rows = (1L..3L).flatMap { s -> (0..3).map { row(s, it, 5, 95.0) } } +
        listOf(
            row(4, 0, 4, 100.0, "TOP"),
            row(4, 1, 7, 90.0, "BACKOFF"),
            row(4, 2, 7, 90.0, "BACKOFF"),
        )

    @Test
    fun `the page peak is the top set by estimated 1RM, not the back-off with more volume`() {
        val history = ExerciseHistoryCalculator.build(squat, rows, bodyweightKg = 80.0)
        val peak = history.peak!!
        assertTrue(history.peakIsE1rm)
        assertEquals(100.0, peak.weightKg!!, 0.0)
        assertEquals(4, peak.reps)
        assertEquals(ProgramRules.epley(100.0, 4), peak.e1rmKg!!, 1e-9)
    }

    @Test
    fun `the chart does not fall when a heavier top set replaces a lighter set of more reps`() {
        val history = ExerciseHistoryCalculator.build(squat, rows, bodyweightKg = 80.0)
        val values = history.series.map { it.peakValue }
        assertEquals(4, values.size)
        assertTrue("100 x 4 estimates higher than 95 x 5: $values", values.last() > values[values.lastIndex - 1])
    }

    @Test
    fun `a bodyweight movement with no load falls back to the score`() {
        val pullUp = Exercise(id = 2, name = "Pull-up", muscleGroup = MuscleGroup.PULL, isWeighted = false)
        val history = ExerciseHistoryCalculator.build(
            pullUp, listOf(ExerciseSetRow(1, day, 0, 8, null, "", done = true)), bodyweightKg = 80.0,
        )
        assertFalse(history.peakIsE1rm)
        assertEquals(8, history.peak!!.reps)
        assertEquals(history.bestScore, history.series.single().peakValue, 1e-9)
        assertNull(history.peak!!.e1rmKg)
    }
}
