package com.monarch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityScoreTest {

    @Test
    fun `reps scores zero - lifting XP must not double count`() {
        assertEquals(0, ActivityScore.xp(ExerciseMetric.REPS, null, null, 100.0, 80.0))
        assertEquals(0, ActivityScore.xp(ExerciseMetric.REPS, 3600, 10000.0, null, 80.0))
    }

    @Test
    fun `weighted skipping out-scores unweighted at equal duration`() {
        val weighted = ActivityScore.xp(ExerciseMetric.DURATION, 1200, null, 10.0, 80.0)
        val unweighted = ActivityScore.xp(ExerciseMetric.DURATION, 1200, null, null, 80.0)
        assertEquals(20, unweighted)
        assertEquals(22, weighted)
        assertTrue(weighted > unweighted)
    }

    @Test
    fun `duration xp scales with minutes and zero duration scores zero`() {
        assertEquals(0, ActivityScore.xp(ExerciseMetric.DURATION, 0, null, null, 80.0))
        assertEquals(0, ActivityScore.xp(ExerciseMetric.DURATION, null, null, null, 80.0))
        assertTrue(
            ActivityScore.xp(ExerciseMetric.DURATION, 3600, null, null, 80.0) >
                ActivityScore.xp(ExerciseMetric.DURATION, 1800, null, null, 80.0),
        )
    }

    @Test
    fun `faster 5k out-scores slower 5k`() {
        val fast = ActivityScore.xp(ExerciseMetric.DISTANCE_TIME, 1500, 5000.0, null, 80.0) // 5:00/km
        val slow = ActivityScore.xp(ExerciseMetric.DISTANCE_TIME, 3300, 5000.0, null, 80.0) // 11:00/km
        assertTrue(fast > slow)
    }

    @Test
    fun `pace is null on missing or zero inputs and never infinite`() {
        assertNull(ActivityScore.paceSecPerKm(null, 1500))
        assertNull(ActivityScore.paceSecPerKm(0.0, 1500))
        assertNull(ActivityScore.paceSecPerKm(-5.0, 1500))
        assertNull(ActivityScore.paceSecPerKm(5000.0, null))
        assertNull(ActivityScore.paceSecPerKm(5000.0, 0))
        assertNull(ActivityScore.paceSecPerKm(5000.0, -10))
        assertEquals(300.0, ActivityScore.paceSecPerKm(5000.0, 1500)!!, 0.001)
        // Every scoreable input yields a finite pace.
        val pace = ActivityScore.paceSecPerKm(1.0, 1)
        assertTrue(pace != null && !pace.isInfinite() && !pace.isNaN())
    }

    @Test
    fun `climbing xp scales with completed attempts`() {
        val perAttempt = ActivityScore.xp(ExerciseMetric.ATTEMPTS_GRADE, null, null, null, 80.0)
        assertEquals(15, perAttempt)
        assertEquals(45, perAttempt * 3)
        // Grade text is opaque here — "V4" and "6C" both score by attempts alone.
        assertEquals(perAttempt, ActivityScore.xp(ExerciseMetric.ATTEMPTS_GRADE, null, null, null, 80.0))
    }

    @Test
    fun `distance xp scales with distance`() {
        assertTrue(
            ActivityScore.xp(ExerciseMetric.DISTANCE_TIME, 3600, 10000.0, null, 80.0) >
                ActivityScore.xp(ExerciseMetric.DISTANCE_TIME, 3600, 5000.0, null, 80.0),
        )
        // Zero distance scores zero; distance without a duration still earns base XP (no pace bonus).
        assertEquals(0, ActivityScore.xp(ExerciseMetric.DISTANCE_TIME, 600, 0.0, null, 80.0))
        assertEquals(0, ActivityScore.xp(ExerciseMetric.DISTANCE_TIME, 600, null, null, 80.0))
        assertEquals(250, ActivityScore.xp(ExerciseMetric.DISTANCE_TIME, null, 5000.0, null, 80.0))
    }
}
