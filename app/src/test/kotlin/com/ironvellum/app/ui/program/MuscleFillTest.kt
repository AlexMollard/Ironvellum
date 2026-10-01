package com.ironvellum.app.ui.program

import com.ironvellum.app.domain.Muscle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The heat map never scolds: a muscle under its range is a green that grows
 * toward the in-range green, and a rite's figure is a share of the rite.
 */
class MuscleFillTest {

    private val target = 10.0..20.0

    @Test
    fun `under alpha grows with volume and reaches the in-range opacity at the target`() {
        val samples = listOf(0.5, 2.0, 5.0, 9.5, 10.0).map { underAlpha(it, target) }
        assertTrue(samples.zipWithNext().all { (a, b) -> a < b })
        assertEquals(0.9f, underAlpha(10.0, target), 0.0001f)
    }

    @Test
    fun `faint under stays visibly above nothing and never passes full`() {
        assertTrue(underAlpha(0.1, target) >= 0.3f)
        assertEquals(0.9f, underAlpha(40.0, target), 0.0001f)
    }

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
