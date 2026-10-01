package com.ironvellum.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartSummaryTest {
    @Test
    fun `a chart with gaps says how many slots are empty and ends on the latest value`() {
        val text = chartSummary(listOf(null, 2.0, null, 8.1), "18 Sep", "1 Oct")
        assertTrue(text, text.startsWith("Chart of 2 values, 2 of 4 slots empty."))
        assertTrue(text, text.contains("Latest 8.1, low 2.0, high 8.1."))
        assertTrue(text, text.endsWith("From 18 Sep to 1 Oct."))
    }

    @Test
    fun `an empty series is announced as empty`() {
        assertEquals("Chart with no data", chartSummary(listOf(null, null), null, null))
    }
}
