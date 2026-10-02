package com.ironvellum.app.ui.program

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The figure outline is drawn along the rounded curve the body is filled
 * with. A plain vertex is cut (that is what rounds a deltoid) and a doubled
 * vertex is kept (that is what keeps the armpit crease crisp).
 */
class FigureSmoothingTest {

    private val square = listOf(Offset(0f, 0f), Offset(100f, 0f), Offset(100f, 100f), Offset(0f, 100f))

    private fun nearest(samples: List<Offset>, to: Offset) = samples.minOf { (it - to).getDistance() }

    @Test
    fun `a single vertex is rounded off`() {
        assertTrue(nearest(smoothSamples(square), Offset(100f, 0f)) > 10f)
    }

    @Test
    fun `a doubled vertex stays a corner`() {
        val doubled = listOf(square[0], square[1], square[1], square[2], square[3])
        assertEquals(0f, nearest(smoothSamples(doubled), Offset(100f, 0f)), 0.001f)
    }

    @Test
    fun `the armpit crease is doubled in the outline`() {
        val crease = HALF_OUTLINE.withIndex().filter { (i, p) -> i > 0 && p == HALF_OUTLINE[i - 1] }.map { it.value }
        assertTrue(crease.contains(0.094f to 0.228f))
    }
}
