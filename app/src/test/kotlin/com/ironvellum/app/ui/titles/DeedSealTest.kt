package com.ironvellum.app.ui.titles

import com.ironvellum.app.domain.TitleRarity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Each tier's seal has its own silhouette and ornament, so a tier reads without colour. */
class DeedSealTest {

    private fun g(r: TitleRarity) = sealGeometry(r)

    @Test fun commonIsAPlainOctagon() {
        val s = g(TitleRarity.Common)
        assertEquals(8, s.outer.size)
        assertTrue(s.rim.isEmpty() && s.leaves.isEmpty() && s.core.isEmpty())
    }

    @Test fun rareKeepsAQuietInsetRim() {
        val s = g(TitleRarity.Rare)
        assertEquals(8, s.outer.size)
        assertEquals(8, s.rim.size)
        assertTrue(s.leaves.isEmpty())
    }

    @Test fun fabledKeepsScallopsWithSixCurvedLaurelLeaves() {
        val s = g(TitleRarity.Epic)
        assertEquals(24, s.outer.size) // 12 points and 12 notches
        assertTrue(s.rim.isEmpty())
        assertEquals(6, s.leaves.size)
        assertTrue(s.leaves.all { it.contains("C") && it.endsWith("Z") })
        assertTrue(s.branches != null)
        assertEquals(26f, s.glyphSize, 0f)
    }

    @Test fun masterworkKeepsAStarAndQuietInsetWithRoomForTheGlyph() {
        val s = g(TitleRarity.Masterwork)
        assertEquals(16, s.outer.size)
        assertEquals(8, s.core.size)
        assertEquals(28f, s.glyphSize, 0f)
        assertTrue(s.leaves.isEmpty())
    }

    @Test fun everyTierHasADistinctSilhouette() {
        val shapes = TitleRarity.entries.map { r -> g(r).let { it.outer to it.rim } }
        assertEquals(TitleRarity.entries.size, shapes.toSet().size)
    }

    @Test fun everyPointStaysOnTheGrid() {
        TitleRarity.entries.forEach { r ->
            val s = g(r)
            val all = s.outer + s.rim + s.core
            assertTrue("$r", all.all { it.x in 0f..64f && it.y in 0f..64f })
        }
    }
}
