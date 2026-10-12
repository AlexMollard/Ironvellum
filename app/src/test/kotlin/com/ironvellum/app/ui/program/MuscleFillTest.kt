package com.ironvellum.app.ui.program

import com.ironvellum.app.domain.Muscle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A rite's figure is a share of the rite: the emerald strengthens with the sets it gives a muscle, relative
 * to its most worked muscle.
 */
class MuscleFillTest {

    @Test
    fun `rite alpha is relative to the most worked muscle`() {
        assertEquals(0f, riteAlpha(0.0, 6.0), 0f)
        assertEquals(0.9f, riteAlpha(6.0, 6.0), 0.0001f)
        assertTrue(riteAlpha(3.0, 6.0) in 0.3f..0.9f)
    }

    @Test
    fun `rite sets label rounds to one decimal and never to nothing`() {
        assertEquals("6 sets", riteSetsLabel(6.0))
        assertEquals("4.4 sets", riteSetsLabel(4.4))
        // Three sets at the 0.75 step and one helper set.
        assertEquals("2.8 sets", riteSetsLabel(3 * 0.75 + 0.5))
        assertEquals("1.8 sets", riteSetsLabel(3 * 0.6))
        assertEquals("0.8 sets", riteSetsLabel(0.75))
        assertEquals("1 set", riteSetsLabel(1.0))
        assertEquals("0.1 sets", riteSetsLabel(0.01))
    }

    @Test
    fun `rite rows are sorted by sets then name and skip untouched muscles`() {
        val rows = riteMuscleRows(mapOf(Muscle.MID_CHEST to 3.0, Muscle.TRICEPS to 6.0, Muscle.BICEPS to 0.0))
        assertEquals(listOf(Muscle.TRICEPS, Muscle.MID_CHEST), rows.map { it.first })
    }
}
