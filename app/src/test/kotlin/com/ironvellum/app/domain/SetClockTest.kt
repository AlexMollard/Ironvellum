package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The clock on a hold or duration set (get-ready, count up, adjust) and the distance stopwatch. */
class SetClockTest {

    private val start = 10_000L

    @Test
    fun `starting opens a 5 second get-ready that reads 5 down to 1 and counts nothing`() {
        val clock = SetClock.countdown(60).start(start)
        assertEquals(SetClock.Phase.GET_READY, clock.phase(start))
        assertEquals(5, clock.getReadySeconds(start))
        assertEquals(4, clock.getReadySeconds(start + 1_000))
        assertEquals(1, clock.getReadySeconds(start + 4_999))
        assertEquals("nothing is counted during it", 0L, clock.elapsedMs(start + 4_999))
        assertEquals(SetClock.Phase.GET_READY, clock.phase(start + 4_999))
    }

    @Test
    fun `after exactly 5 seconds it counts up from zero`() {
        val clock = SetClock.countdown(60).start(start)
        assertEquals(SetClock.Phase.COUNTING, clock.phase(start + 5_000))
        assertEquals(0, clock.displaySeconds(start + 5_000))
        assertEquals(0, clock.getReadySeconds(start + 5_000))
        assertEquals(1, clock.displaySeconds(start + 6_000))
        assertEquals(64, clock.displaySeconds(start + 5_000 + 64_900))
    }

    @Test
    fun `an idle clock has not started`() {
        val clock = SetClock.countdown(45)
        assertEquals(SetClock.Phase.IDLE, clock.phase(123_456))
        assertFalse(clock.running)
    }

    @Test
    fun `stopping during the get-ready records nothing and returns to idle`() {
        val clock = SetClock.countdown(60).start(start)
        assertNull(clock.stop(start + 3_000))
        assertNull(clock.stop(start + 4_999))
    }

    @Test
    fun `reaching the target changes the state once and the clock keeps counting`() {
        val clock = SetClock.countdown(60).start(start)
        val reachedAt = start + 5_000 + 60_000
        assertFalse(clock.targetReached(reachedAt - 1))
        assertTrue(clock.targetReached(reachedAt))
        assertTrue("it keeps counting past the target", clock.targetReached(reachedAt + 30_000))
        assertEquals(SetClock.Phase.COUNTING, clock.phase(reachedAt + 30_000))
        assertEquals(90, clock.displaySeconds(reachedAt + 30_000))
    }

    @Test
    fun `the go cue and the target cue each play once, however often it is asked`() {
        var clock = SetClock.countdown(10).start(start)
        val cues = mutableListOf<SetClock.Cue>()
        // Ask on every 100 ms across the get-ready, the count, and well past the target, storing what it returns.
        for (t in start..(start + 5_000 + 10_000 + 20_000) step 100) {
            while (true) {
                val (cue, next) = clock.cueDue(t)
                if (cue == SetClock.Cue.NONE) break
                cues += cue
                clock = next
            }
        }
        assertEquals(listOf(SetClock.Cue.TICK, SetClock.Cue.DONE), cues)
    }

    @Test
    fun `a rotation asking again with the stored clock does not repeat the target cue`() {
        val clock = SetClock.countdown(10).start(start)
        val (_, afterGo) = clock.cueDue(start + 5_000)
        val (cue, afterTarget) = afterGo.cueDue(start + 15_000)
        assertEquals(SetClock.Cue.DONE, cue)
        assertEquals(SetClock.Cue.NONE, afterTarget.cueDue(start + 40_000).first)
    }

    @Test
    fun `the get-ready ticks on 3, 2 and 1 and not on 5 and 4`() {
        assertEquals(SetClock.Cue.NONE, SetClock.readyCue(5))
        assertEquals(SetClock.Cue.NONE, SetClock.readyCue(4))
        assertEquals(SetClock.Cue.TICK, SetClock.readyCue(3))
        assertEquals(SetClock.Cue.TICK, SetClock.readyCue(1))
        assertEquals(SetClock.Cue.NONE, SetClock.readyCue(0))
    }

    @Test
    fun `stopping while counting opens the adjust step at the whole seconds counted`() {
        val clock = SetClock.countdown(60).start(start)
        val stopped = clock.stop(start + 5_000 + 42_900)!!
        assertEquals(SetClock.Phase.ADJUST, stopped.phase(start + 999_999))
        assertEquals(42, stopped.stoppedSec)
        assertFalse("the clock no longer runs", stopped.running)
    }

    @Test
    fun `a stop before the first second still offers 1 second`() {
        val clock = SetClock.countdown(60).start(start)
        assertEquals(1, clock.stop(start + 5_000 + 300)!!.stoppedSec)
    }

    @Test
    fun `adjusting moves the figure by a second, never below 1 and with no upper limit`() {
        var clock = SetClock.countdown(60).start(start).stop(start + 5_000 + 3_000)!!
        assertEquals(3, clock.stoppedSec)
        clock = clock.adjust(+1)
        assertEquals(4, clock.stoppedSec)
        repeat(10) { clock = clock.adjust(-1) }
        assertEquals("the floor is 1 s", 1, clock.stoppedSec)
        repeat(7_200) { clock = clock.adjust(+1) }
        assertEquals(7_201, clock.stoppedSec)
    }

    @Test
    fun `adjusting outside the adjust step does nothing`() {
        val clock = SetClock.countdown(60).start(start)
        assertEquals(clock, clock.adjust(+1))
    }

    @Test
    fun `ticking a hold row with no timer logs the row's own figure`() {
        // No clock at all is represented by a fresh idle clock or by none: both log at the target.
        val idle = SetClock.countdown(45).onRowLog(start)
        assertEquals(SetClock.RowLog(null, true, null), idle)
        // The caller commits set.durationSec when seconds is null: the target, or what the lifter typed.
        assertNull(idle.seconds)
    }

    @Test
    fun `ticking the row during the get-ready drops the clock and logs the row's figure`() {
        val clock = SetClock.countdown(45).start(start)
        assertEquals(SetClock.RowLog(null, true, null), clock.onRowLog(start + 2_000))
    }

    @Test
    fun `ticking the row while counting stops into the adjust step and logs nothing yet`() {
        val clock = SetClock.countdown(45).start(start)
        val tick = clock.onRowLog(start + 5_000 + 20_000)
        assertFalse(tick.log)
        assertEquals(20, tick.clock!!.stoppedSec)
    }

    @Test
    fun `ticking the row in the adjust step logs the adjusted seconds`() {
        val adjusted = SetClock.countdown(45).start(start).stop(start + 5_000 + 20_000)!!.adjust(+1)
        assertEquals(SetClock.RowLog(null, true, 21), adjusted.onRowLog(start + 999_999))
    }

    @Test
    fun `a stopwatch counts up at once, is never at a target and logs the elapsed seconds`() {
        val watch = SetClock.stopwatch().start(1_000)
        assertEquals(SetClock.Phase.COUNTING, watch.phase(1_000))
        assertFalse(watch.targetReached(10_000_000))
        assertEquals(0, watch.displaySeconds(1_900))
        assertEquals(65, watch.displaySeconds(66_000))
        assertEquals(65, watch.stopwatchSeconds(65_700 + 1_000 - 700))
        assertEquals(66, watch.stopwatchSeconds(66_600))
        assertEquals("ticking the row with a stopwatch running is the plain log", SetClock.RowLog(null, true), watch.onRowLog(5_000))
    }

    @Test
    fun `holds and durations are timed against a target, a distance run counts up, nothing else is timed`() {
        assertTrue(SetClock.countsDown(ExerciseMetric.HOLD))
        assertTrue(SetClock.countsDown(ExerciseMetric.DURATION))
        assertTrue(SetClock.countsUp(ExerciseMetric.DISTANCE_TIME))
        assertFalse(SetClock.countsDown(ExerciseMetric.REPS) || SetClock.countsUp(ExerciseMetric.REPS))
        assertFalse(SetClock.countsDown(ExerciseMetric.ATTEMPTS_GRADE) || SetClock.countsUp(ExerciseMetric.ATTEMPTS_GRADE))
    }

    @Test
    fun `the face reads minutes and seconds`() {
        assertEquals("0:05", SetClock.face(5))
        assertEquals("1:05", SetClock.face(65))
        assertEquals("30:00", SetClock.face(1_800))
    }
}
