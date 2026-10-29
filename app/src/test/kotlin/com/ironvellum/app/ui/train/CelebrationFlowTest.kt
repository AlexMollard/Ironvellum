package com.ironvellum.app.ui.train

import com.ironvellum.app.domain.RepMax
import com.ironvellum.app.domain.SessionPeaks
import com.ironvellum.app.domain.Titles
import com.ironvellum.app.ui.components.xpCounted
import com.ironvellum.app.ui.train.SessionViewModel.Finish
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CelebrationFlowTest {

    @Test
    fun `a quiet trial goes straight to the summary`() {
        assertEquals(Finish.SUMMARY, CelebrationFlow.resolve(Finish.LEVEL, hasXp = false, hasLevel = false, hasDeeds = false))
        assertTrue(CelebrationFlow.steps(hasXp = false, hasLevel = false, hasDeeds = false).isEmpty())
    }

    @Test
    fun `only the steps that exist are visited and counted`() {
        assertEquals(Finish.LEVEL, CelebrationFlow.resolve(Finish.LEVEL, hasXp = false, hasLevel = true, hasDeeds = true))
        assertEquals(Finish.DEEDS, CelebrationFlow.after(Finish.LEVEL, hasXp = false, hasLevel = true, hasDeeds = true))
        assertEquals(Finish.SUMMARY, CelebrationFlow.after(Finish.LEVEL, hasXp = false, hasLevel = true, hasDeeds = false))
        assertEquals(Finish.DEEDS, CelebrationFlow.resolve(Finish.LEVEL, hasXp = false, hasLevel = false, hasDeeds = true))
        assertEquals(listOf(Finish.LEVEL, Finish.DEEDS), CelebrationFlow.steps(hasXp = false, hasLevel = true, hasDeeds = true))
        assertEquals(listOf(Finish.DEEDS), CelebrationFlow.steps(hasXp = false, hasLevel = false, hasDeeds = true))
    }

    @Test
    fun `the xp page leads, and its level crossing replaces the level page`() {
        assertEquals(Finish.XP, CelebrationFlow.resolve(Finish.XP, hasXp = true, hasLevel = true, hasDeeds = true))
        // Levelled with an XP page: straight on to the deeds, never a second level-up.
        assertEquals(Finish.DEEDS, CelebrationFlow.after(Finish.XP, hasXp = true, hasLevel = true, hasDeeds = true))
        assertEquals(Finish.SUMMARY, CelebrationFlow.after(Finish.XP, hasXp = true, hasLevel = true, hasDeeds = false))
        assertEquals(listOf(Finish.DEEDS), CelebrationFlow.steps(hasXp = true, hasLevel = true, hasDeeds = true))
        assertTrue(CelebrationFlow.steps(hasXp = true, hasLevel = true, hasDeeds = false).isEmpty())
    }

    @Test
    fun `without xp to tell the old pages stand`() {
        assertEquals(Finish.LEVEL, CelebrationFlow.resolve(Finish.XP, hasXp = false, hasLevel = true, hasDeeds = true))
        assertEquals(Finish.SUMMARY, CelebrationFlow.resolve(Finish.XP, hasXp = false, hasLevel = false, hasDeeds = false))
    }

    @Test
    fun `the summary leads to the photo step and then the routine question`() {
        assertEquals(Finish.PHOTO, CelebrationFlow.after(Finish.SUMMARY, hasXp = false, hasLevel = true, hasDeeds = true))
        assertEquals(Finish.ROUTINE, CelebrationFlow.after(Finish.PHOTO, hasXp = false, hasLevel = true, hasDeeds = true))
        assertEquals(Finish.ROUTINE, CelebrationFlow.after(Finish.ROUTINE, hasXp = false, hasLevel = true, hasDeeds = true))
        // The photo step is never skipped by resolve: the screen decides whether it is offered.
        assertEquals(Finish.PHOTO, CelebrationFlow.resolve(Finish.PHOTO, hasXp = false, hasLevel = false, hasDeeds = false))
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

    private fun peak(name: String, figure: Int, kg: Double?, hold: Boolean, kind: RepMax.Kind, was: RepMax.Mark, xp: Int = 15) =
        SessionPeaks.Peak(name, 1, 2, figure, kg, hold, kind, was, xp)

    @Test
    fun `the peak's gain is told in the unit that moved`() {
        assertEquals("+2.5 kg", peakGain(peak("Pull-up", 6, 10.0, false, RepMax.Kind.HEAVIER, RepMax.Mark(6, 7.5))))
        assertEquals("+5 kg", peakGain(peak("Pull-up", 6, 10.0, false, RepMax.Kind.HEAVIER, RepMax.Mark(6, 5.0))))
        assertEquals("+2 reps", peakGain(peak("Pull-up", 8, 10.0, false, RepMax.Kind.MORE_REPS, RepMax.Mark(6, 10.0))))
        assertEquals("+15s", peakGain(peak("Plank", 45, null, true, RepMax.Kind.MORE_REPS, RepMax.Mark(30, 0.0))))
    }

    @Test
    fun `a peak that pays nothing says so`() {
        assertEquals("+3 XP", peakPay(peak("Bench Press", 5, 100.5, false, RepMax.Kind.HEAVIER, RepMax.Mark(5, 100.0), xp = 3)))
        assertEquals("no XP", peakPay(peak("Bench Press", 5, 100.0, false, RepMax.Kind.HEAVIER, RepMax.Mark(5, 99.9), xp = 0)))
    }
}
