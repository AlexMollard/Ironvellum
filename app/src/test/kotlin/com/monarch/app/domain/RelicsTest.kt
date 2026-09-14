package com.monarch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RelicsTest {

    @Test
    fun `an empty vault does not change the rate`() {
        assertEquals(1.0, Relics.effectiveMultiplier(emptyList()), 1e-9)
    }

    @Test
    fun `a single relic applies in full`() {
        assertEquals(1.24, Relics.effectiveMultiplier(listOf(1.24)), 1e-9)
    }

    @Test
    fun `the strongest relic always applies at full weight`() {
        assertEquals(1.5, Relics.effectiveMultiplier(listOf(1.5)), 1e-9)
        // Order of the input must not matter: the best one leads either way.
        assertEquals(
            Relics.effectiveMultiplier(listOf(1.2, 1.5)),
            Relics.effectiveMultiplier(listOf(1.5, 1.2)),
            1e-9,
        )
    }

    @Test
    fun `a second relic adds something but less than its own excess`() {
        val one = Relics.effectiveMultiplier(listOf(1.5))
        val two = Relics.effectiveMultiplier(listOf(1.5, 1.2))
        assertTrue(two > one)
        assertTrue(two < one + 0.2)
    }

    @Test
    fun `a deep vault never hides a relic behind a zero weight`() {
        // The geometric decay this replaced rounded to zero by the eighth
        // relic, so the screen told the hunter their relics did nothing.
        val vault = List(48) { 1.30 }
        assertTrue("48th relic carries no weight", Relics.weightAt(47) >= 0.02)
        val contribution = (1.30 - 1.0) * Relics.weightAt(47)
        assertTrue("contribution rounds away to +0.00", contribution >= 0.005)
        assertTrue(Relics.effectiveMultiplier(vault) > Relics.effectiveMultiplier(vault.drop(1)))
    }

    @Test
    fun `stacked excess stays under the ceiling however deep the vault`() {
        val best = 2.50
        val ceiling = 1.0 + 3.0 * (best - 1.0)
        val deep = Relics.effectiveMultiplier(List(200) { best })
        assertTrue("$deep breached the ceiling", deep < ceiling)
        // ...but a 200-relic vault should be well past what the best relic
        // alone is worth, or the ceiling has made the vault pointless.
        assertTrue("$deep barely beats one relic", deep > 1.0 + 2.5 * (best - 1.0))
    }

    @Test
    fun `every extra relic strictly increases the multiplier`() {
        var previous = Relics.effectiveMultiplier(emptyList())
        val vault = mutableListOf<Double>()
        repeat(20) {
            vault += 1.30
            val next = Relics.effectiveMultiplier(vault)
            assertTrue("relic ${vault.size} added nothing", next > previous)
            previous = next
        }
    }

    @Test
    fun `a better relic is never a downgrade`() {
        val held = listOf(1.20, 1.35)
        assertTrue(
            Relics.effectiveMultiplier(held + 2.50) > Relics.effectiveMultiplier(held + 1.40),
        )
    }

    @Test
    fun `the whole reachable catalogue stays inside a sane ceiling`() {
        // Total excess can never exceed twice the best relic's excess, so even
        // owning every relic cannot run the idle economy away.
        val all = Gacha.relicCatalogue().map { it.multiplier }
        val effective = Relics.effectiveMultiplier(all)
        val best = all.max()
        assertTrue("catalogue reachable", all.size > 20)
        assertTrue("$effective exceeded the cap", effective <= 1.0 + 3.0 * (best - 1.0) + 1e-9)
        assertTrue("$effective should still beat the best single relic", effective > best)
    }

    @Test
    fun `junk values are ignored rather than poisoning the rate`() {
        assertEquals(
            1.30,
            Relics.effectiveMultiplier(listOf(1.30, 1.0, 0.5, Double.NaN, Double.POSITIVE_INFINITY)),
            1e-9,
        )
    }
}
