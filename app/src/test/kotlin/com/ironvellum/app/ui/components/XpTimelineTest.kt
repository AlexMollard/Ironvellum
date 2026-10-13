package com.ironvellum.app.ui.components

import com.ironvellum.app.domain.Xp
import com.ironvellum.app.ui.components.XpTimeline.Phase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XpTimelineTest {
    private fun lift(name: String, xp: Int) = Xp.Line(Xp.Kind.EXERCISE, name, xp, hardSets = 3)

    private val trial = Xp.Breakdown(
        listOf(
            lift("Pull-up", 86),
            lift("Chin-up", 38),
            Xp.Line(Xp.Kind.COMPLETION, "Trial complete", 25),
        ),
    )

    /** Starts [gap] XP short of the end of level 1, so the first segment crosses it. */
    private fun crossing(gap: Long) = XpTimeline(trial, (Xp.progress(0).needed - gap) + trial.total)

    @Test
    fun aTrialInsideOneLevelNeverHoldsAndEndsDone() {
        val timeline = XpTimeline(trial, 785L + trial.total)
        assertTrue(timeline.holds.isEmpty())
        assertEquals(Phase.FILLING, timeline.stateAt(timeline.steps.first().startMs + 100).phase)
        assertEquals(Phase.DONE, timeline.stateAt(timeline.endMs).phase)
        assertEquals(trial.total.toFloat(), timeline.stateAt(timeline.endMs).counted, 0f)
    }

    @Test
    fun aClockAtItsEndIsTheFinishedPage() {
        // Animations off start the clock at endMs: that must read as done, on the last bar.
        val timeline = crossing(20)
        val state = timeline.stateAt(timeline.endMs)
        assertEquals(Phase.DONE, state.phase)
        assertEquals(timeline.bars.lastIndex, state.bar)
    }

    @Test
    fun theLevelCrossingHoldsOnceForAboutAnAndAHalfSecondsThenFillsOn() {
        val timeline = crossing(20)
        val hold = timeline.holds.single()
        assertEquals(1, hold.toBar)
        // Before the hold the first bar fills; during it the page is on the level moment; after, the fresh bar.
        assertEquals(Phase.FILLING, timeline.stateAt(hold.startMs - 1).phase)
        assertEquals(0, timeline.stateAt(hold.startMs - 1).bar)
        val during = timeline.stateAt(hold.startMs + 100)
        assertEquals(Phase.CROSSING, during.phase)
        assertEquals(0, during.hold)
        assertEquals(1, during.bar)
        assertEquals(Phase.CROSSING, timeline.stateAt(hold.startMs + XpTimeline.HOLD_MS - 1).phase)
        val after = timeline.stateAt(hold.startMs + XpTimeline.HOLD_MS + 1)
        assertEquals(Phase.FILLING, after.phase)
        assertEquals(1, after.bar)
        assertEquals(1_500L, XpTimeline.HOLD_MS)
    }

    @Test
    fun theCountOnlyGoesUp() {
        val timeline = crossing(20)
        var last = -1f
        var t = 0L
        while (t <= timeline.endMs) {
            val counted = timeline.stateAt(t).counted
            assertTrue("runs backwards at $t", counted >= last)
            last = counted
            t += 25
        }
        assertEquals(trial.total.toFloat(), last, 0f)
    }

    @Test
    fun theCarriedRestFillsInsideTheHoldAndEverythingElseWaitsForItsEnd() {
        val timeline = crossing(20)
        val hold = timeline.holds.single()
        // The 66 XP carried into level 2 fills inside the hold, so the level moment shows it.
        val carried = timeline.steps.first { it.bar == 1 && it.slice.segment == 0 }
        assertTrue(carried.startMs > hold.startMs && carried.startMs < hold.startMs + XpTimeline.HOLD_MS)
        // Everything after waits for the hold to end.
        timeline.steps.filter { it.slice.segment > 0 }.forEach { assertTrue(it.startMs >= hold.startMs + XpTimeline.HOLD_MS) }
    }

    @Test
    fun aTrialEndingExactlyOnALevelBoundaryStillOpensTheNextLevel() {
        val timeline = XpTimeline(trial, Xp.progress(0).needed)
        assertEquals(2, timeline.bars.size)
        assertEquals(1, timeline.holds.size)
        assertEquals(Xp.levelFor(Xp.progress(0).needed), timeline.bars.last().level)
    }

    @Test
    fun aLineIsDoneOnceEveryStepOfItHasFilled() {
        val timeline = crossing(20)
        val hold = timeline.holds.single()
        // Segment 0 is split in two steps: not done until the carried rest has filled too.
        assertEquals(false, timeline.lineDone(0, hold.startMs + XpReveal.FILL_MS + 10))
        val carried = timeline.steps.first { it.bar == 1 && it.slice.segment == 0 }
        assertEquals(true, timeline.lineDone(0, carried.startMs + XpReveal.FILL_MS))
        assertEquals(false, timeline.lineDone(2, carried.startMs + XpReveal.FILL_MS))
    }

    @Test
    fun spokenAndVeilWordsComeFromTheBreakdown() {
        assertEquals("Earned 149 XP, 124 effort and 25 bonus. Level up, 16 to 17.", XpReveal.pageSpoken(trial, 16, 17))
        assertEquals("Earned 149 XP, 124 effort and 25 bonus. Level 16.", XpReveal.pageSpoken(trial, 16, 16))
        assertEquals(null, XpReveal.veilLine(0))
        assertEquals("An inscription waits in the Veil", XpReveal.veilLine(1))
        assertEquals("3 inscriptions wait in the Veil", XpReveal.veilLine(3))
    }
}
