package com.monarch.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bottom-nav labels cap their growth because six of them share one screen
 * width. The first attempt at that divided the declared size by the font scale,
 * which rendered them at ONE physical size at every setting — measured
 * identical at 0.9x, 1.0x, 1.5x and 2.0x on device. A hunter who asks for
 * smaller text gets smaller text; only the growth is capped.
 */
class NavLabelScaleTest {

    /** The multiplier lands the label at this fraction of design size on screen. */
    private fun rendered(fontScale: Float) = navLabelScale(fontScale) * fontScale

    @Test
    fun `a smaller system font makes the labels smaller`() {
        assertEquals(0.85f, rendered(0.85f), 1e-4f)
        assertEquals(0.90f, rendered(0.90f), 1e-4f)
        assertTrue("0.85x must render smaller than 0.9x", rendered(0.85f) < rendered(0.90f))
    }

    @Test
    fun `the design size is untouched at the default scale`() {
        assertEquals(1.0f, rendered(1.0f), 1e-4f)
    }

    @Test
    fun `growth stops at the cap where six labels stop fitting`() {
        assertEquals(1.15f, rendered(1.3f), 1e-4f)
        assertEquals(1.15f, rendered(1.5f), 1e-4f)
        assertEquals(1.15f, rendered(2.0f), 1e-4f)
    }

    @Test
    fun `a zero or absent scale does not divide by zero`() {
        assertEquals(1f, navLabelScale(0f), 1e-4f)
    }
}
