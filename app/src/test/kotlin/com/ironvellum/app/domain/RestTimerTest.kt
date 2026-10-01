package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestTimerTest {

    private fun set(id: Long, done: Boolean) = SessionSet(id = id, exerciseId = 1, exerciseName = "Bench Press", setIndex = id.toInt(), reps = 8, done = done)

    @Test
    fun `a rest counts down on the monotonic clock and ends`() {
        val t = RestTimer.start(sessionId = 7, seconds = 90, nowMs = 1_000)
        assertEquals(90_000, t.remainingMs(1_000))
        assertEquals("1:30", t.label(1_000))
        assertEquals("1:00", t.label(31_000))
        // The last fraction of a second still reads as a second left.
        assertEquals("0:01", t.label(90_500))
        assertFalse(t.isOver(90_999))
        assertTrue(t.isOver(91_000))
        assertEquals(0, t.remainingMs(200_000))
    }

    @Test
    fun `plus fifteen adds to the time left, or restarts a rest that ran out`() {
        val t = RestTimer.start(sessionId = 7, seconds = 60, nowMs = 0)
        val running = t.extended(RestTimer.EXTEND_SECONDS, nowMs = 10_000)
        assertEquals(65_000, running.remainingMs(10_000))
        assertEquals(75_000, running.totalMs)
        val late = t.extended(RestTimer.EXTEND_SECONDS, nowMs = 100_000)
        assertEquals(15_000, late.remainingMs(100_000))
    }

    @Test
    fun `only a fresh tick with sets still waiting starts a rest`() {
        val sets = listOf(set(1, done = false), set(2, done = false))
        assertTrue(RestTimer.startsRest(sets, setId = 1, nowDone = true))
        // Unticking, re-saving a ticked set, or an unknown set: no rest.
        assertFalse(RestTimer.startsRest(sets, setId = 1, nowDone = false))
        assertFalse(RestTimer.startsRest(listOf(set(1, done = true), set(2, done = false)), setId = 1, nowDone = true))
        assertFalse(RestTimer.startsRest(sets, setId = 9, nowDone = true))
        // The last set of the trial: nothing left to rest for.
        assertFalse(RestTimer.startsRest(listOf(set(1, done = true), set(2, done = false)), setId = 2, nowDone = true))
    }

    @Test
    fun `rest follows the prescription for the movement and the focus`() {
        assertEquals(ProgramRules.restSeconds(TrainingFocus.STRENGTH, compound = true), RestTimer.restSeconds("Unprofiled Lift", TrainingFocus.STRENGTH))
        assertEquals(ProgramRules.restSeconds(TrainingFocus.MUSCLE, compound = true), RestTimer.restSeconds("Unprofiled Lift", TrainingFocus.MUSCLE))
    }
}
