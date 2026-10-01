package com.ironvellum.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChartScrubTest {
    @Test
    fun `nearest point skips gaps and ties go to the earlier point`() {
        val xs = listOf(0f, null, 100f, 200f)
        assertEquals(0, nearestIndex(xs, -30f))
        assertEquals(2, nearestIndex(xs, 90f))
        // 50 is equidistant from 0 and 100
        assertEquals(0, nearestIndex(xs, 50f))
        // the gap at index 1 can never be chosen
        assertEquals(2, nearestIndex(xs, 51f))
        assertEquals(3, nearestIndex(xs, 5_000f))
        assertNull(nearestIndex(listOf(null, null), 10f))
        assertNull(nearestIndex(emptyList(), 10f))
    }

    @Test
    fun `trend points sit by date when positions are given and evenly otherwise`() {
        assertEquals(50f, trendX(0, 1, null, 100f), 0.001f)
        assertEquals(0f, trendX(0, 5, null, 100f), 0.001f)
        assertEquals(100f, trendX(4, 5, null, 100f), 0.001f)
        assertEquals(30f, trendX(1, 3, listOf(0.0, 0.3, 1.0), 100f), 0.001f)
        // sparse weigh-ins: the finger lands on the nearest by date, not by index
        val xs = List(3) { trendX(it, 3, listOf(0.0, 0.1, 1.0), 100f) }
        assertEquals(1, nearestIndex(xs, 40f))
        assertEquals(2, nearestIndex(xs, 60f))
    }

    @Test
    fun `bar slot is clamped to the chart`() {
        assertEquals(0, slotIndex(-12f, 140f, 14))
        assertEquals(0, slotIndex(9.9f, 140f, 14))
        assertEquals(1, slotIndex(10f, 140f, 14))
        assertEquals(13, slotIndex(999f, 140f, 14))
        assertEquals(0, slotIndex(5f, 0f, 14))
        assertEquals(5f, barCenterX(0, 14, 140f), 0.001f)
        assertEquals(135f, barCenterX(13, 14, 140f), 0.001f)
    }

    @Test
    fun `readout prefers the right of the cursor and flips near the right edge`() {
        assertEquals(58f, readoutLeft(50f, 60f, 300f, 8f), 0.001f)
        // 280 + 8 + 60 > 300, so it flips to the left of the cursor
        assertEquals(212f, readoutLeft(280f, 60f, 300f, 8f), 0.001f)
        // exactly fits on the right
        assertEquals(240f, readoutLeft(232f, 60f, 300f, 8f), 0.001f)
    }

    @Test
    fun `readout never leaves the chart`() {
        // flipping would run off the left edge of a narrow chart: pinned to 0
        assertEquals(0f, readoutLeft(10f, 90f, 100f, 8f), 0.001f)
        // a label wider than the chart pins to 0 as well
        assertEquals(0f, readoutLeft(50f, 140f, 100f, 8f), 0.001f)
        // cursor at the very left edge keeps the label on the right
        assertEquals(8f, readoutLeft(0f, 60f, 300f, 8f), 0.001f)
    }

    @Test
    fun `readout text puts the date under the value`() {
        assertEquals("78.4 kg\n12 Sep", scrubReadout("78.4 kg", "12 Sep"))
        assertEquals("78.4 kg", scrubReadout("78.4 kg", null))
        // "." decimals whatever the device locale
        assertEquals("82.5", scrubNumber(82.5))
        assertEquals("12,345", scrubWhole(12_345.4))
    }
}
