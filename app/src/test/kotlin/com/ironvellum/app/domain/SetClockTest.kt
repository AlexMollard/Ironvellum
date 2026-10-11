package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The countdown on a hold or duration set and the stopwatch on a distance set. */
class SetClockTest {

    @Test
    fun `a countdown counts to its target from the start time and ends exactly there`() {
        val clock = SetClock.countdown(30).start(nowMs = 10_000)
        assertEquals(30_000L, clock.remainingMs(10_000))
        assertEquals(30, clock.displaySeconds(10_000))
        assertEquals(27_500L, clock.remainingMs(12_500))
        assertEquals("the last fraction still reads as a second", 1, clock.displaySeconds(39_500))
        assertFalse(clock.isDone(39_999))
        assertTrue(clock.isDone(40_000))
        assertEquals(0, clock.displaySeconds(40_000))
        assertEquals("it never goes below zero", 0L, clock.remainingMs(500_000))
    }

    @Test
    fun `an idle clock has not moved and does not run`() {
        val clock = SetClock.countdown(45)
        assertFalse(clock.running)
        assertEquals(45, clock.displaySeconds(123_456))
        assertFalse(clock.paused)
    }

    @Test
    fun `pausing banks the time and resuming carries on from it`() {
        var clock = SetClock.countdown(60).start(0)
        clock = clock.pause(20_000)
        assertTrue(clock.paused)
        assertEquals("a paused clock stands still, however long it waits", 40, clock.displaySeconds(500_000))
        clock = clock.start(100_000)
        assertEquals("20 s held, then 15 s more of the 60", 25_000L, clock.remainingMs(115_000))
    }

    @Test
    fun `finishing early logs the time actually held, to the nearest second`() {
        val clock = SetClock.countdown(60).start(0)
        assertEquals(22, clock.loggedSeconds(22_400))
        assertEquals(23, clock.loggedSeconds(22_600))
        assertEquals(0, clock.loggedSeconds(300))
    }

    @Test
    fun `finishing at zero logs the target, and a late read never logs more`() {
        val clock = SetClock.countdown(30).start(0)
        assertEquals(30, clock.loggedSeconds(30_000))
        assertEquals("the app was in the background for a minute", 30, clock.loggedSeconds(90_000))
        // Rounding up to the target before it has run out never passes it either.
        assertEquals(30, clock.loggedSeconds(29_600))
    }

    @Test
    fun `a stopwatch counts up, is never done and logs the elapsed seconds`() {
        val watch = SetClock.stopwatch().start(1_000)
        assertNull(watch.remainingMs(5_000))
        assertFalse(watch.isDone(10_000_000))
        assertEquals(0, watch.displaySeconds(1_900))
        assertEquals(65, watch.displaySeconds(66_000))
        assertEquals(65, watch.loggedSeconds(65_700))
        assertEquals(1_830, watch.loggedSeconds(1_831_000))
    }

    @Test
    fun `a tick on 3, 2 and 1 and the long cue at zero, nothing else`() {
        assertEquals(SetClock.Cue.NONE, SetClock.cueAt(30))
        assertEquals(SetClock.Cue.NONE, SetClock.cueAt(4))
        assertEquals(SetClock.Cue.TICK, SetClock.cueAt(3))
        assertEquals(SetClock.Cue.TICK, SetClock.cueAt(2))
        assertEquals(SetClock.Cue.TICK, SetClock.cueAt(1))
        assertEquals(SetClock.Cue.DONE, SetClock.cueAt(0))
    }

    @Test
    fun `the cues follow the displayed second, one each across a countdown`() {
        val clock = SetClock.countdown(5).start(0)
        val cues = (0..5_000 step 250).map { clock.displaySeconds(it.toLong()) }.distinct().map(SetClock::cueAt)
        assertEquals(
            listOf(SetClock.Cue.NONE, SetClock.Cue.NONE, SetClock.Cue.TICK, SetClock.Cue.TICK, SetClock.Cue.TICK, SetClock.Cue.DONE),
            cues,
        )
    }

    @Test
    fun `holds and durations count down, a distance run counts up, nothing else is timed`() {
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
