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
        assertTrue(s.rim.isEmpty() && s.rivets.isEmpty() && s.leaves.isEmpty() && s.core.isEmpty())
    }

    @Test fun rareAddsARimAndEightRivets() {
        val s = g(TitleRarity.Rare)
        assertEquals(8, s.outer.size)
        assertEquals(8, s.rim.size)
        assertEquals(8, s.rivets.size)
        assertTrue(s.leaves.isEmpty())
    }

    @Test fun fabledIsTwelvePointsScallopedWithTenLaurelLeaves() {
        val s = g(TitleRarity.Epic)
        assertEquals(24, s.outer.size) // 12 points and 12 notches
        assertEquals(12, s.rim.size)
        assertEquals(10, s.leaves.size)
        assertTrue(s.leaves.all { it.size == 4 })
        assertTrue(s.rivets.isEmpty())
    }

    @Test fun masterworkIsAFacetedStarWithAGemCutCentre() {
        val s = g(TitleRarity.Masterwork)
        assertEquals(16, s.outer.size)
        assertEquals(8, s.core.size)
        assertEquals(8, s.spokes.size)
        assertTrue(s.rivets.isEmpty() && s.leaves.isEmpty())
    }

    @Test fun everyTierHasADistinctSilhouette() {
        val shapes = TitleRarity.entries.map { r -> g(r).let { Triple(it.outer, it.rim.size, it.rivets.size) } }
        assertEquals(TitleRarity.entries.size, shapes.toSet().size)
    }

    @Test fun everyPointStaysOnTheGrid() {
        TitleRarity.entries.forEach { r ->
            val s = g(r)
            val all = s.outer + s.rim + s.rivets + s.leaves.flatten() + s.core
            assertTrue("$r", all.all { it.x in 0f..64f && it.y in 0f..64f })
        }
    }
}
