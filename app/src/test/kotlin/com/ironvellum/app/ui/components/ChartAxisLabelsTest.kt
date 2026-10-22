package com.ironvellum.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

/** The y-axis labels of a measurement chart: the highest and lowest value, each with its unit. */
class ChartAxisLabelsTest {

    private fun kg(decimals: Int) = { v: Double -> "+" + String.format(Locale.US, "%.${decimals}f", v) + " kg" }

    @Test
    fun `the top and bottom of the line read with their unit`() {
        // The owner's push-up line: 10 kg up to 46 kg of estimated added load.
        assertEquals("+46 kg" to "+10 kg", chartAxisLabels(listOf(10.0, 30.0, 45.95), kg(0)))
    }

    @Test
    fun `a gap is not a value`() {
        assertEquals("+46 kg" to "+20 kg", chartAxisLabels(listOf(null, 20.0, null, 46.0), kg(0)))
    }

    @Test
    fun `no span means no labels`() {
        assertNull(chartAxisLabels(listOf(12.0), kg(0)))
        assertNull(chartAxisLabels(listOf(12.0, 12.0, 12.0), kg(0)))
        assertNull(chartAxisLabels(emptyList(), kg(0)))
    }

    @Test
    fun `two values that round alike are written finer so the axis never shows one number twice`() {
        assertEquals("+45.9 kg" to "+45.6 kg", chartAxisLabels(listOf(45.6, 45.9), kg(0), kg(1)))
    }
}
