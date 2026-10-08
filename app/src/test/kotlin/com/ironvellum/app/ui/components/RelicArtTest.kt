package com.ironvellum.app.ui.components

import com.ironvellum.app.domain.RelicHouse
import com.ironvellum.app.domain.RelicHouses
import com.ironvellum.app.domain.RewardRarity
import com.ironvellum.app.ui.theme.RarityTint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RelicArtTest {

    @Test
    fun `every relic of the catalogue has its own drawn sigil`() {
        val ids = RelicHouses.CATALOGUE.map { it.id }
        assertEquals("a relic without a row would draw nothing", ids.toSet(), RELIC_SPECS.keys)
        val shapes = ids.map { RELIC_SPECS.getValue(it) }.map { it.points to it.inner }
        assertEquals("two relics share a star", shapes.size, shapes.toSet().size)
        assertEquals(RELIC_SPECS.size, RELIC_SPECS.values.toSet().size)
    }

    @Test
    fun `a spec stays inside what the drawing can carry`() {
        RELIC_SPECS.forEach { (id, spec) ->
            assert(spec.points in 3..8) { "$id points" }
            assert(spec.rings in 1..3) { "$id rings" }
            assert(spec.spokes in 4..8) { "$id spokes" }
            assert(spec.inner in 0.3f..0.8f) { "$id inner" }
        }
    }

    @Test
    fun `each house has an emblem of its own`() {
        assertEquals(RelicHouse.entries.toSet(), HOUSE_EMBLEMS.keys)
        assertEquals(HOUSE_EMBLEMS.size, HOUSE_EMBLEMS.values.map { it.points to it.inner }.toSet().size)
    }

    @Test
    fun `a reward wears the deed ladder of its tier`() {
        assertEquals(RewardRarity.entries.size, RewardRarity.entries.map { RarityTint.of(it) }.toSet().size)
        assertNotEquals(RarityTint.of(RewardRarity.Rare), RarityTint.of(RewardRarity.Epic))
        assert(!RarityTint.glows(RewardRarity.Rare) && RarityTint.glows(RewardRarity.Epic))
    }
}
