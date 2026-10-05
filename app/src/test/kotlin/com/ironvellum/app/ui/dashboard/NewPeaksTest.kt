package com.ironvellum.app.ui.dashboard

import com.ironvellum.app.domain.SessionSet
import com.ironvellum.app.domain.WorkoutSession
import org.junit.Assert.assertEquals
import org.junit.Test

/** Today's NEW PEAK: a fresh best that beat an earlier trial, never a first trial. */
class NewPeaksTest {

    private val day = 24L * 60 * 60 * 1000
    private val now = 100 * day

    private fun trial(id: Long, daysAgo: Long, kg: Double, lift: String = "Back Squat") =
        WorkoutSession(id = id, label = "t$id", startedAtMs = now - daysAgo * day, completedAtMs = now - daysAgo * day + 1) to
            listOf(SessionSet(exerciseId = 1, exerciseName = lift, setIndex = 0, reps = 5, weightKg = kg, done = true))

    @Test
    fun `a heavier recent trial is a peak`() {
        val peaks = newPeaks(listOf(trial(1, daysAgo = 20, kg = 100.0), trial(2, daysAgo = 2, kg = 110.0)), emptyList(), now)
        assertEquals(listOf("Back Squat"), peaks.map { it.name })
    }

    @Test
    fun `a lift's first trial is not news`() {
        assertEquals(emptyList<String>(), newPeaks(listOf(trial(1, daysAgo = 2, kg = 100.0)), emptyList(), now).map { it.name })
    }

    @Test
    fun `matching the old best, or a best set long ago, is not a peak`() {
        val tie = listOf(trial(1, daysAgo = 20, kg = 100.0), trial(2, daysAgo = 2, kg = 100.0))
        assertEquals(emptyList<String>(), newPeaks(tie, emptyList(), now).map { it.name })
        val stale = listOf(trial(1, daysAgo = 60, kg = 100.0), trial(2, daysAgo = 40, kg = 120.0), trial(3, daysAgo = 2, kg = 90.0))
        assertEquals(emptyList<String>(), newPeaks(stale, emptyList(), now).map { it.name })
    }
}
