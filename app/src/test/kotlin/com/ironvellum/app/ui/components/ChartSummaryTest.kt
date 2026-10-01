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
    fun `the summary speaks the chart's own units and whole numbers`() {
        val sleep = chartSummary(listOf(430.0, 205.0, 510.0), null, null) { "${it.toInt() / 60}h ${it.toInt() % 60}m" }
        assertTrue(sleep, sleep.contains("Latest 8h 30m, low 3h 25m, high 8h 30m."))
        val steps = chartSummary(listOf(950.0, 8200.0), null, null) { "%,d steps".format(java.util.Locale.US, Math.round(it)) }
        assertTrue(steps, steps.contains("Latest 8,200 steps, low 950 steps, high 8,200 steps."))
    }

    @Test
    fun `an empty series is announced as empty`() {
        assertEquals("Chart with no data", chartSummary(listOf(null, null), null, null))
    }
}
