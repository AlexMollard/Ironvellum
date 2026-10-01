package com.ironvellum.app.ui.train

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A logged load must be reachable exactly. The ± buttons used to add 2.5 kg
 * to whatever was there, so 7.7 kg could only ever become 10.2 or 12.7; they
 * now walk the plate grid, and the figure can be typed.
 */
class LoadEntryTest {

    @Test
    fun `steps snap an off-grid load onto the plate grid`() {
        assertEquals(17.5, stepUpKg(15.2), 1e-9)
        assertEquals(15.0, stepDownKg(15.2)!!, 1e-9)
        assertEquals(10.0, stepUpKg(7.7), 1e-9)
        assertEquals(7.5, stepDownKg(7.7)!!, 1e-9)
    }

    @Test
    fun `steps from an on-grid load move one plate, and bodyweight starts at the first plate`() {
        assertEquals(17.5, stepUpKg(15.0), 1e-9)
        assertEquals(12.5, stepDownKg(15.0)!!, 1e-9)
        assertEquals(2.5, stepUpKg(null), 1e-9)
        // Stepping below the first plate returns to bodyweight.
        assertNull(stepDownKg(2.5))
        assertNull(stepDownKg(1.0))
        assertNull(stepDownKg(null))
    }

    @Test
    fun `typed loads parse to the exact figure`() {
        assertEquals(7.7, parseLoadKg("7.7").getOrThrow(), 1e-9)
        assertEquals(15.2, parseLoadKg(" 15,2 ").getOrThrow(), 1e-9)
        assertEquals(0.0, parseLoadKg("").getOrThrow(), 1e-9)
        assertEquals(0.0, parseLoadKg("0").getOrThrow(), 1e-9)
    }

    @Test
    fun `nonsense and out-of-range loads are refused`() {
        listOf("abc", "-5", "7.7.1", "501", "NaN", ".", "1..2", "1e2").forEach { input ->
            assertTrue("accepted $input", parseLoadKg(input).isFailure)
        }
    }
}
