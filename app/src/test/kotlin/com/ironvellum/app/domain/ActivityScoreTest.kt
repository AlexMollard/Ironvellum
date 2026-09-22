package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityScoreTest {

    // --- anti-double-count ---------------------------------------------------

    @Test
    fun `reps and hold score zero - lifting XP must not double count`() {
        assertEquals(0, ActivityScore.xp("Squat", "", ExerciseMetric.REPS, null, null, 100.0, 80.0))
        assertEquals(0, ActivityScore.xp("Plank", "", ExerciseMetric.HOLD, 3600, null, null, 80.0))
    }

    @Test
    fun `climbing stays flat 15 xp per attempt`() {
        val perAttempt = ActivityScore.xp("Bouldering", "Climbing", ExerciseMetric.ATTEMPTS_GRADE, null, null, null, 80.0)
        assertEquals(15, perAttempt)
    }

    // --- intensity separates movements --------------------------------------

    @Test
    fun `high-met sport out-earns yoga at equal time by roughly the met ratio`() {
        val bjj = ActivityScore.xp("BJJ", "Sport", ExerciseMetric.DURATION, 1800, null, null, 80.0)
        val yoga = ActivityScore.xp("Yoga", "Mobility", ExerciseMetric.DURATION, 1800, null, null, 80.0)
        // METs: BJJ 10.3 vs yoga 2.5 -> ratio ~4.1; allow rounding slack.
        assertEquals(4.12, bjj.toDouble() / yoga.toDouble(), 0.2)
        assertEquals(232, bjj)
        assertEquals(56, yoga)
    }

    // --- time bounds the payout ---------------------------------------------

    @Test
    fun `40 km ride no longer mints thousands - ceiling 750 xp`() {
        // Ceiling: max cycle MET (10.0) x vigorous ride (40 km at 24+ km/h
        // = 96 min) x 0.75 = 720. The old curve paid 4,000.
        val ride = ActivityScore.xp("Bike", "Cardio", ExerciseMetric.DISTANCE_TIME, 5_760, 40_000.0, null, 80.0)
        assertEquals(720, ride)
        assertTrue("must stay under 750 (one order below the old 4000)", ride <= 750)
        // Bounded by time spent: ten 30-minute swims (10 x ~236) is the scale, not level-minting rides.
        assertTrue(ride < 3 * 326) // far below 3 hard lifting sessions
    }

    @Test
    fun `duration payout scales with minutes and zero or missing duration scores zero`() {
        assertEquals(0, ActivityScore.xp("Run", "Cardio", ExerciseMetric.DURATION, 0, null, null, 80.0))
        assertEquals(0, ActivityScore.xp("Run", "Cardio", ExerciseMetric.DURATION, null, null, null, 80.0))
        assertTrue(
            ActivityScore.xp("Run", "Cardio", ExerciseMetric.DURATION, 3600, null, null, 80.0) >
                ActivityScore.xp("Run", "Cardio", ExerciseMetric.DURATION, 1800, null, null, 80.0),
        )
    }

    @Test
    fun `distance with no duration pays 0 - never fabricate a pace`() {
        assertEquals(0, ActivityScore.xp("Run", "Cardio", ExerciseMetric.DISTANCE_TIME, null, 5000.0, null, 80.0))
        assertEquals(0, ActivityScore.xp("Run", "Cardio", ExerciseMetric.DISTANCE_TIME, 0, 5000.0, null, 80.0))
        // With time present the same distance earns normally.
        assertTrue(
            ActivityScore.xp("Run", "Cardio", ExerciseMetric.DISTANCE_TIME, 1800, 5000.0, null, 80.0) > 0,
        )
    }

    // --- speed still matters (the old pace bonus, expressed by MET bands) ----
    // NOTE: "same distance, faster, earns more" is TRUE ONLY AT EQUAL TIME.
    // A slower rider spends far longer working, and MET-minutes is time-
    // proportional — the four-hour leisure ride honestly out-earns the
    // ninety-minute hard one. The swim test below pins the equal-time case.

    @Test
    fun `fast swim earns the vigorous band at equal time - impossible under the old running pace window`() {
        // Old pace bonus capped at 4:00/km (15 km/h): a swimmer could never earn it.
        // Now, in the same 30 minutes, a 4 km/h swimmer hits MET 10 vs 7.0 moderate.
        val fast = ActivityScore.xp("Swim", "Water", ExerciseMetric.DISTANCE_TIME, 1800, 2000.0, null, 80.0) // 4 km/h
        val moderate = ActivityScore.xp("Swim", "Water", ExerciseMetric.DISTANCE_TIME, 1800, 1000.0, null, 80.0) // 2 km/h
        assertEquals(225, fast)
        assertEquals(158, moderate)
        assertTrue(fast > moderate)
    }

    // --- load bonus survives --------------------------------------------------

    @Test
    fun `weighted skipping out-earns unweighted at equal duration`() {
        val weighted = ActivityScore.xp("Skipping", "Cardio", ExerciseMetric.DURATION, 1200, null, 10.0, 80.0)
        val unweighted = ActivityScore.xp("Skipping", "Cardio", ExerciseMetric.DURATION, 1200, null, null, 80.0)
        assertEquals(165, unweighted) // 11.0 MET x 20 min x 0.75
        assertEquals(182, weighted) // +10% load bonus
        assertTrue(weighted > unweighted)
    }
}
