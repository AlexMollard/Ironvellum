package com.ironvellum.app.ui.theme

import com.ironvellum.app.domain.RewardRarity
import com.ironvellum.app.domain.TitleRarity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The one metal ladder: deeds, relics and crests of a tier must draw in the same metal. */
class MetalGradientTest {

    @Test
    fun `a reward and a deed of the same tier share a metal`() {
        RewardRarity.entries.forEach { reward ->
            val deed = TitleRarity.valueOf(reward.name)
            assertSame("$reward", Metal.of(deed), Metal.of(reward))
        }
    }

    @Test
    fun `each metal is named by its rarity tint`() {
        assertEquals(RarityTint.Iron, Metal.Common.tone)
        assertEquals(RarityTint.Bronze, Metal.Rare.tone)
        assertEquals(RarityTint.Gold, Metal.Fabled.tone)
        assertEquals(RarityTint.Prismatic, Metal.Masterwork.tone)
    }

    @Test
    fun `only the masterwork is a prism`() {
        assertTrue(Metal.Masterwork.prism)
        assertFalse(Metal.Common.prism || Metal.Rare.prism || Metal.Fabled.prism)
    }
}
