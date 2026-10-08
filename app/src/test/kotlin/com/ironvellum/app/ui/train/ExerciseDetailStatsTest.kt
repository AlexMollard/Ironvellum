package com.ironvellum.app.ui.train

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The figures the exercise detail prints must say what they mean. */
class ExerciseDetailStatsTest {

    private val week = 7L * 24 * 60 * 60 * 1000

    @Test
    fun `one trial has no weekly rate`() {
        assertNull(trialsPerWeek(1, 0L, 10 * week))
        assertNull(trialsPerWeek(5, null, 10 * week))
    }

    @Test
    fun `rate is trials over weeks since the first`() {
        assertEquals(2.0, trialsPerWeek(20, 0L, 10 * week)!!, 1e-9)
    }

    @Test
    fun `a span under a week counts as a week`() {
        assertEquals(3.0, trialsPerWeek(3, 0L, week / 7)!!, 1e-9)
    }

    @Test
    fun `sets read as weight by reps, bodyweight and holds included`() {
        assertEquals("100 kg × 5", setText(5, 100.0, hold = false))
        assertEquals("BW × 12", setText(12, null, hold = false))
        assertEquals("45 s · 20 kg", setText(45, 20.0, hold = true))
        assertEquals("45 s", setText(45, null, hold = true))
        assertEquals("97.5 × 5", compactSet(5, 97.5, hold = false))
    }
}
