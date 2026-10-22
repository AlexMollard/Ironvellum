package com.ironvellum.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The recent-average line: 14 days back from the newest reading, else the last three readings, none under three. */
class RecentAverageTest {

    private val day = 86_400_000L

    @Test
    fun `readings in the last 14 days are averaged, older ones ignored`() {
        // newest on day 30; day 16 is exactly 14 days back (in), day 15 is out
        val avg = recentAverage(listOf(100.0, 80.0, 78.0, 82.0), listOf(15 * day, 16 * day, 20 * day, 30 * day))
        assertEquals(80.0, avg!!, 1e-9)
    }

    @Test
    fun `fewer than three in the window falls back to the last three readings`() {
        // only days 29 and 30 are within 14 days; the last three readings are days 10, 29, 30
        val avg = recentAverage(listOf(50.0, 60.0, 70.0, 80.0), listOf(0, 10 * day, 29 * day, 30 * day))
        assertEquals((60.0 + 70.0 + 80.0) / 3, avg!!, 1e-9)
    }

    @Test
    fun `fewer than three points in all draws no line`() {
        assertNull(recentAverage(listOf(1.0, 2.0), listOf(0, day)))
        assertNull(recentAverage(listOf(1.0, null, 3.0), listOf(0, day, 2 * day)))
        assertNull(recentAverage(emptyList(), emptyList()))
    }

    @Test
    fun `uneven dates and unsorted input use time, not index`() {
        val values = listOf(90.0, 10.0, 20.0, 30.0)
        val at = listOf(40 * day, 0, 38 * day, 39 * day)
        // by time: 10 (day 0), 20, 30, 90 (day 40); window holds 20, 30, 90
        assertEquals((20.0 + 30.0 + 90.0) / 3, recentAverage(values, at)!!, 1e-9)
    }

    @Test
    fun `a gap is skipped`() {
        val avg = recentAverage(listOf(10.0, null, 20.0, 30.0), listOf(0, day, 2 * day, 3 * day))
        assertEquals(20.0, avg!!, 1e-9)
    }
}
