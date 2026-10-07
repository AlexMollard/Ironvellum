package com.ironvellum.app.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class RestClockTest {

    private var now = 1_000L

    @Before
    fun setUp() {
        RestClock.clock = { now }
        RestClock.cancel(1)
        RestClock.cancel(2)
    }

    @After
    fun tearDown() {
        RestClock.cancel(1)
        RestClock.cancel(2)
    }

    @Test
    fun `start sets a rest for that trial`() {
        RestClock.start(1, 90)
        val t = RestClock.timer.value!!
        assertEquals(1L, t.sessionId)
        assertEquals(91_000L, t.endsAtMs)
    }

    @Test
    fun `another trial can neither extend nor cancel a rest`() {
        RestClock.start(1, 90)
        RestClock.extend(2)
        RestClock.cancel(2)
        assertEquals(91_000L, RestClock.timer.value!!.endsAtMs)
    }

    @Test
    fun `extend adds seconds and cancel clears the trial's own rest`() {
        RestClock.start(1, 90)
        RestClock.extend(1)
        assertEquals(106_000L, RestClock.timer.value!!.endsAtMs)
        RestClock.cancel(1)
        assertNull(RestClock.timer.value)
    }

    @Test
    fun `shorten takes fifteen seconds off and another trial cannot shorten it`() {
        RestClock.start(1, 90)
        RestClock.shorten(2)
        assertEquals(91_000L, RestClock.timer.value!!.endsAtMs)
        RestClock.shorten(1)
        assertEquals(76_000L, RestClock.timer.value!!.endsAtMs)
        assertEquals(75_000L, RestClock.timer.value!!.totalMs)
    }

    @Test
    fun `shorten past the end clears the rest like skip`() {
        RestClock.start(1, 10)
        RestClock.shorten(1)
        assertNull(RestClock.timer.value)
        // A second tap with nothing running is harmless.
        RestClock.shorten(1)
        assertNull(RestClock.timer.value)
    }

    @Test
    fun `finish clears only the rest that ended`() {
        RestClock.start(1, 30)
        val ended = RestClock.timer.value!!
        // The lifter extended while the end was being announced: the new rest survives.
        RestClock.extend(1)
        RestClock.finish(ended)
        assertEquals(46_000L, RestClock.timer.value!!.endsAtMs)
        RestClock.finish(RestClock.timer.value!!)
        assertNull(RestClock.timer.value)
    }

    @Test
    fun `a new rest replaces the old one and a stale finish leaves it alone`() {
        RestClock.start(1, 30)
        val old = RestClock.timer.value!!
        now = 5_000L
        RestClock.start(2, 60)
        RestClock.finish(old)
        assertEquals(2L, RestClock.timer.value!!.sessionId)
        assertEquals(65_000L, RestClock.timer.value!!.endsAtMs)
    }
}
