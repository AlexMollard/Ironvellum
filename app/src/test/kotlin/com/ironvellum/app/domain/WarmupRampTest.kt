package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The warm-up ramp's maths: which sets, at what load, and which are skipped. */
class WarmupRampTest {

    private fun ramp(kg: Double, step: Double, barbell: Boolean) =
        WarmupRamp.plan(kg, step, barbell).map { it.weightKg to it.reps }

    @Test
    fun `a barbell ramp is the bar for ten then 40, 60 and 80 percent`() {
        assertEquals(
            listOf(20.0 to 10, 40.0 to 5, 60.0 to 3, 80.0 to 2),
            ramp(100.0, 2.5, barbell = true),
        )
    }

    @Test
    fun `steps are rounded to the load step`() {
        // 40%, 60% and 80% of 60 kg are 24, 36 and 48: onto the 2.5 kg grid.
        assertEquals(
            listOf(20.0 to 10, 25.0 to 5, 35.0 to 3, 47.5 to 2),
            ramp(60.0, 2.5, barbell = true),
        )
        // A 5 kg step (legs) rounds 24 to 25, 36 to 35 and 48 to 50.
        assertEquals(
            listOf(20.0 to 10, 25.0 to 5, 35.0 to 3, 50.0 to 2),
            ramp(60.0, 5.0, barbell = true),
        )
    }

    @Test
    fun `a step that rounds to the bar or below is skipped`() {
        // 40% of 30 is 12, 60% is 18: both at or under the bar. Only 80% = 24 -> 25 survives.
        assertEquals(listOf(20.0 to 10, 25.0 to 2), ramp(30.0, 2.5, barbell = true))
    }

    @Test
    fun `a working load at or under the bar has no ramp`() {
        assertTrue(ramp(20.0, 2.5, barbell = true).isEmpty())
        assertTrue(ramp(15.0, 2.5, barbell = true).isEmpty())
        assertTrue(ramp(0.0, 2.5, barbell = false).isEmpty())
    }

    @Test
    fun `a movement that is not a barbell has no bar step and skips duplicates and nothing`() {
        assertEquals(listOf(40.0 to 5, 60.0 to 3, 80.0 to 2), ramp(100.0, 2.5, barbell = false))
        // 40%, 60% and 80% of 10 kg are 4, 6, 8: 5, 5 (a duplicate, skipped) and 7.5.
        assertEquals(listOf(5.0 to 5, 7.5 to 2), ramp(10.0, 2.5, barbell = false))
        // A load so light every step rounds to nothing leaves no ramp.
        assertTrue(ramp(2.5, 2.5, barbell = false).isEmpty())
    }

    @Test
    fun `no step reaches the working load and the loads only climb`() {
        for (kg in listOf(22.5, 25.0, 32.5, 47.5, 62.5, 87.5, 140.0, 227.5)) {
            val plan = WarmupRamp.plan(kg, 2.5, barbell = true)
            assertTrue("$kg: ${plan.map { it.weightKg }}", plan.all { it.weightKg < kg })
            assertEquals(plan.map { it.weightKg }.sorted().distinct(), plan.map { it.weightKg })
        }
    }

    @Test
    fun `only rep movements with a load are warmed up, at their own step and gear`() {
        val bench = WarmupRamp.planFor("Bench Press", "PUSH", ExerciseMetric.REPS, 100.0)
        assertEquals(20.0, bench.first().weightKg, 0.0)
        assertEquals(listOf(10, 5, 3, 2), bench.map { it.reps })
        assertTrue(WarmupRamp.planFor("Bench Press", "PUSH", ExerciseMetric.HOLD, 100.0).isEmpty())
        // No load yet: the empty bar, so a ramp switched on for a fresh rite still makes a set.
        assertEquals(listOf(20.0), WarmupRamp.planFor("Bench Press", "PUSH", ExerciseMetric.REPS, null).map { it.weightKg })
        assertTrue(WarmupRamp.planFor("Push-up", "PUSH", ExerciseMetric.REPS, null).isEmpty())
        assertTrue(WarmupRamp.isBarbell("Bench Press"))
        assertFalse(WarmupRamp.isBarbell("Push-up"))
    }
    @Test
    fun `a barbell lift with no working load yet still warms up on the bar`() {
        // A generated rite starts a loaded lift empty; a ramp switched on for it must not come out as nothing.
        assertEquals(listOf(20.0 to 10), ramp(0.0, 2.5, barbell = true))
        assertTrue(WarmupRamp.planFor("Back Squat", "LEGS", ExerciseMetric.REPS, null).isNotEmpty())
        // No bar to start on and no load to scale from: nothing to ramp.
        assertTrue(ramp(0.0, 2.5, barbell = false).isEmpty())
    }
}
