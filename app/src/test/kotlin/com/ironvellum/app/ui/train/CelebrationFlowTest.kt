package com.ironvellum.app.ui.train

import com.ironvellum.app.domain.SessionPeaks
import com.ironvellum.app.domain.SetRecords
import com.ironvellum.app.domain.Titles
import com.ironvellum.app.ui.train.SessionViewModel.Finish
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CelebrationFlowTest {

    @Test
    fun `a quiet trial goes straight to the summary`() {
        assertEquals(Finish.SUMMARY, CelebrationFlow.resolve(Finish.LEVEL, hasLevel = false, hasDeeds = false))
        assertTrue(CelebrationFlow.steps(hasLevel = false, hasDeeds = false).isEmpty())
    }

    @Test
    fun `only the steps that exist are visited and counted`() {
        assertEquals(Finish.LEVEL, CelebrationFlow.resolve(Finish.LEVEL, hasLevel = true, hasDeeds = true))
        assertEquals(Finish.DEEDS, CelebrationFlow.after(Finish.LEVEL, hasLevel = true, hasDeeds = true))
        assertEquals(Finish.SUMMARY, CelebrationFlow.after(Finish.LEVEL, hasLevel = true, hasDeeds = false))
        assertEquals(Finish.DEEDS, CelebrationFlow.resolve(Finish.LEVEL, hasLevel = false, hasDeeds = true))
        assertEquals(listOf(Finish.LEVEL, Finish.DEEDS), CelebrationFlow.steps(hasLevel = true, hasDeeds = true))
        assertEquals(listOf(Finish.DEEDS), CelebrationFlow.steps(hasLevel = false, hasDeeds = true))
    }

    @Test
    fun `the summary leads to the routine question`() {
        assertEquals(Finish.ROUTINE, CelebrationFlow.after(Finish.SUMMARY, hasLevel = true, hasDeeds = true))
        assertEquals(Finish.ROUTINE, CelebrationFlow.after(Finish.ROUTINE, hasLevel = true, hasDeeds = true))
    }

    @Test
    fun `wear title picks the rarest deed, first on a tie`() {
        val deeds = Titles.ALL.filter { it.name.isNotBlank() }.take(40)
        val rarest = deeds.maxOf { it.rarity.ordinal }
        assertEquals(deeds.first { it.rarity.ordinal == rarest }, CelebrationFlow.wearTarget(deeds))
        assertEquals(null, CelebrationFlow.wearTarget(emptyList()))
    }

    @Test
    fun `the bar runs out, holds, then refills to the full gain`() {
        val gain = 340
        val toEnd = 200
        assertEquals(0f, xpCounted(gain, toEnd, 0), 0f)
        assertEquals(toEnd.toFloat(), xpCounted(gain, toEnd, 1_400), 0f)
        assertEquals(gain.toFloat(), xpCounted(gain, toEnd, 5_000), 0f)
        var last = -1f
        for (t in 0L..2_200L step 50) {
            val x = xpCounted(gain, toEnd, t)
            assertTrue("never runs backwards at $t", x >= last)
            last = x
        }
    }

    private fun record(reps: Int, kg: Double?) = SetRecords.Record("Pull-up", 0, 100.0, reps, kg, achievedAtMs = 0, sessionId = 1)

    @Test
    fun `the peak's gain is told in the unit that moved`() {
        assertEquals("+2.5 kg", peakGain(SessionPeaks.Peak("Pull-up", 1, 2, 6, 10.0, false, record(6, 7.5), 1.0)))
        assertEquals("+5 kg", peakGain(SessionPeaks.Peak("Pull-up", 1, 2, 6, 10.0, false, record(6, 5.0), 1.0)))
        assertEquals("+2 reps", peakGain(SessionPeaks.Peak("Pull-up", 1, 2, 8, 10.0, false, record(6, 10.0), 1.0)))
        assertEquals("+15s", peakGain(SessionPeaks.Peak("Plank", 1, 0, 45, null, true, record(30, null), 1.0)))
        assertEquals("+0.9", peakGain(SessionPeaks.Peak("Pull-up", 1, 2, 6, 10.0, false, record(6, 10.0), 0.9)))
    }
}
