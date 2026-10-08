package com.ironvellum.app.ui.theme

import androidx.compose.ui.graphics.Color
import com.ironvellum.app.domain.TitleRarity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RarityTintTest {
    @Test
    fun everyTierHasATintAndNoTwoShareOne() {
        val tints = TitleRarity.entries.map { RarityTint.of(it) }
        assertEquals(TitleRarity.entries.size, tints.toSet().size)
    }

    @Test
    fun onlyTheTopTwoTiersGlow() {
        assertEquals(listOf(false, false, true, true), TitleRarity.entries.map { RarityTint.glows(it) })
    }

    @Test
    fun tintsStayLegibleOnVaultAndAreNotTheRewardGold() {
        TitleRarity.entries.forEach {
            assertTrue("$it", contrast(RarityTint.of(it), IronvellumColors.Vault) >= 4.5)
        }
        assertNotEquals(IronvellumColors.SovereignGold, RarityTint.of(TitleRarity.Epic))
    }

    private fun lum(c: Color): Double {
        fun ch(v: Float): Double = v.toDouble().let { if (it <= 0.03928) it / 12.92 else Math.pow((it + 0.055) / 1.055, 2.4) }
        return 0.2126 * ch(c.red) + 0.7152 * ch(c.green) + 0.0722 * ch(c.blue)
    }

    private fun contrast(a: Color, b: Color): Double {
        val la = lum(a)
        val lb = lum(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }
}
