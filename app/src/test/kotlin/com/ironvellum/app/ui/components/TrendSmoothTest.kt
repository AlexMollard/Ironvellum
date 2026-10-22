package com.ironvellum.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The trend line: an exponentially weighted average by elapsed time, half-life 7 days, seeded by the first reading. */
class TrendSmoothTest {

    private val day = 86_400_000L

    @Test
    fun `the first reading seeds the trend and one half-life moves it halfway`() {
        val out = trendSmooth(listOf(80.0, 82.0, 82.0), listOf(0, 7 * day, 7 * day))!!
        assertEquals(80.0, out[0]!!, 1e-9)
        assertEquals(81.0, out[1]!!, 1e-9)
        // same instant as the previous reading: zero elapsed time, zero weight
        assertEquals(81.0, out[2]!!, 1e-9)
    }

    @Test
    fun `uneven dates weight by time not by count`() {
        val out = trendSmooth(listOf(80.0, 90.0, 90.0), listOf(0, day, 15 * day))!!
        val first = 80.0 + (1 - Math.pow(0.5, 1 / 7.0)) * 10.0
        assertEquals(first, out[1]!!, 1e-9)
        assertEquals(first + (1 - Math.pow(0.5, 14 / 7.0)) * (90.0 - first), out[2]!!, 1e-9)
    }

    @Test
    fun `a big gap makes the next reading nearly the trend`() {
        val out = trendSmooth(listOf(80.0, 80.0, 100.0), listOf(0, day, 400 * day))!!
        assertEquals(100.0, out[2]!!, 1e-6)
    }

    @Test
    fun `fewer than three readings give no trend`() {
        assertNull(trendSmooth(listOf(80.0), listOf(0)))
        assertNull(trendSmooth(listOf(80.0, null, 81.0), listOf(0, day, 2 * day)))
        assertNull(trendSmooth(emptyList(), emptyList()))
    }

    @Test
    fun `gaps stay gaps and unsorted input is read in time order`() {
        val out = trendSmooth(listOf(90.0, null, 80.0, 82.0), listOf(7 * day, 3 * day, 0, 7 * day))!!
        assertNull(out[1])
        assertEquals(80.0, out[2]!!, 1e-9)
        assertEquals(85.0, out[0]!!, 1e-9)
    }
}
