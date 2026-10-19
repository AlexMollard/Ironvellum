package com.ironvellum.app.ui.train

import com.ironvellum.app.domain.LoadUnit
import com.ironvellum.app.domain.Loads
import org.junit.Assert.assertEquals
import org.junit.Test

/** Every place that prints a load agrees: a set with no added weight is "BW", a real load has a space before its unit. */
class LoadFormatTest {

    @Test
    fun `no load reads BW wherever a figure is printed`() {
        assertEquals("BW", formatKg(null))
        assertEquals("BW", formatKg(0.0))
        assertEquals("BW × 5", setText(5, 0.0, hold = false))
        assertEquals("BW × 5", setText(5, null, hold = false))
        assertEquals("BW × 5", compactSet(5, 0.0, hold = false))
    }

    @Test
    fun `a load has a space before its unit`() {
        assertEquals("60 kg", formatKg(60.0))
        assertEquals("82.5 kg", formatKg(82.5))
        assertEquals("60 kg", Loads.label(60.0, LoadUnit.KG))
        assertEquals("132.5 lb", Loads.label(60.0, LoadUnit.LB))
    }
}
