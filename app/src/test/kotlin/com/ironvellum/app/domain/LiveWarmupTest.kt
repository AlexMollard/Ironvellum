package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A warm-up in a live trial is never waiting, never counted and never numbered. */
class LiveWarmupTest {

    private fun set(index: Int, exerciseId: Long = 1, done: Boolean = false, warmup: Boolean = false) =
        SessionSet(id = exerciseId * 100 + index, exerciseId = exerciseId, exerciseName = "Lift $exerciseId", setIndex = index, reps = 5, done = done, warmup = warmup)

    @Test
    fun `only an unticked working set is pending`() {
        assertTrue(set(0).isPending)
        assertFalse(set(0, done = true).isPending)
        assertFalse(set(0, warmup = true).isPending)
    }

    @Test
    fun `working sets are numbered from one after the warm-ups, per movement`() {
        val sets = listOf(
            set(0, warmup = true), set(1), set(2), set(3, warmup = true), set(4),
            set(0, exerciseId = 2), set(1, exerciseId = 2),
        )
        assertEquals(listOf(0, 1, 2, 2, 3, 1, 2), sets.map { it.workingNumber(sets) })
    }

    @Test
    fun `the time left ignores warm-ups`() {
        val metricOf = { _: Long -> ExerciseMetric.REPS }
        val working = listOf(set(1, done = true), set(2))
        val withWarmup = listOf(set(0, warmup = true)) + working
        assertEquals(
            SessionClock.remainingSeconds(working, metricOf, TrainingFocus.STRENGTH),
            SessionClock.remainingSeconds(withWarmup, metricOf, TrainingFocus.STRENGTH),
        )
    }
}
