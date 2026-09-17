package com.monarch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Assert.assertTrue
import org.junit.Test

class GachaTest {

    private val sampleSize = 200_000

    private fun sample(): List<RollResult> =
        (0L until sampleSize).map { Gacha.roll(it) }

    @Test
    fun `same seed always yields the same result`() {
        (0L until 5_000L).forEach { seed ->
            assertEquals(Gacha.roll(seed), Gacha.roll(seed))
        }
    }

    @Test
    fun `drop table holds the stated pyramid`() {
        val counts = sample().groupingBy { it.rarity }.eachCount()
        val total = sampleSize.toDouble()
        val share = { r: RewardRarity -> (counts[r] ?: 0) / total }
        // Stated table: 60 / 30 / 9 / 1. Bounds are generous sampling tolerances.
        assertTrue("Common ${(share(RewardRarity.Common))}", share(RewardRarity.Common) in 0.58..0.62)
        assertTrue("Rare ${share(RewardRarity.Rare)}", share(RewardRarity.Rare) in 0.28..0.32)
        assertTrue("Epic ${share(RewardRarity.Epic)}", share(RewardRarity.Epic) in 0.08..0.10)
        assertTrue("Sovereign ${share(RewardRarity.Sovereign)}", share(RewardRarity.Sovereign) in 0.005..0.015)
    }

    @Test
    fun `every rarity can actually drop`() {
        val seen = sample().map { it.rarity }.toSet()
        assertEquals(RewardRarity.entries.toSet(), seen)
    }

    @Test
    fun `relic multipliers stay in the sane band`() {
        val relics = sample().mapNotNull { it.reward as? Reward.Relic }
        assertTrue("sampled ${relics.size} relics", relics.size > 1_000)
        relics.forEach {
            assertTrue("multiplier ${it.multiplier} not > 1.0", it.multiplier > 1.0)
            assertTrue("multiplier ${it.multiplier} absurd", it.multiplier <= 2.5)
        }
    }

    @Test
    fun `sovereign never pays less than the best common of its type`() {
        val results = sample()
        val commons = results.filter { it.rarity == RewardRarity.Common }
        val sovereigns = results.filter { it.rarity == RewardRarity.Sovereign }
        val bestCommonShadows =
            commons.maxOf { (it.reward as Reward.Shadows).count }
        val worstSovereignShadows =
            sovereigns.mapNotNull { it.reward as? Reward.Shadows }.minOf { it.count }
        assertTrue(worstSovereignShadows > bestCommonShadows)

        val worstSovereignRelic =
            sovereigns.mapNotNull { it.reward as? Reward.Relic }.minOf { it.multiplier }
        assertTrue(worstSovereignRelic > 1.0)
    }

    @Test
    fun `frame rewards only come from the catalogue`() {
        val frames = sample().mapNotNull { it.reward as? Reward.CrestFrame }
        assertTrue(frames.isNotEmpty())
        frames.forEach { assertTrue(Gacha.CREST_FRAMES.contains(it)) }
    }

    @Test
    fun `top of a payout band is actually reachable`() {
        // The lerp used (high - low) * t with t < 1, so the advertised maximum
        // could never be paid. Sweep every Common seed and find the ceiling.
        val maxCommon = (0L until 50_000L)
            .asSequence()
            .map { Gacha.roll(it) }
            .filter { it.rarity == RewardRarity.Common }
            .map { (it.reward as Reward.Shadows).count }
            .max()
        assertEquals(40, maxCommon)
    }

    @Test
    fun `roll never returns an owned frame and pays out when all are owned`() {
        // Hunter owns everything except two frames: only those two may drop.
        val owned = Gacha.CREST_FRAMES.dropLast(2).map { it.id }.toSet()
        val remaining = Gacha.CREST_FRAMES.takeLast(2).map { it.id }.toSet()
        val seenFrames = (0L until 20_000L)
            .asSequence()
            .map { Gacha.roll(it, owned) }
            .mapNotNull { it.reward as? Reward.CrestFrame }
            .toSet()
        assertTrue("expected some frame drops", seenFrames.isNotEmpty())
        assertTrue(seenFrames.all { it.id in remaining })

        // All frames owned: the roll must still grant something of value —
        // never a frame, never a no-op.
        val allOwned = Gacha.CREST_FRAMES.map { it.id }.toSet()
        val results = (0L until 5_000L).map { Gacha.roll(it, allOwned) }
        results.forEach { r ->
            when (val reward = r.reward) {
                is Reward.CrestFrame -> fail("an owned frame was drawn: ${reward.id}")
                is Reward.Shadows -> assertTrue(reward.count > 0)
                is Reward.Relic -> assertTrue(reward.multiplier > 1.0)
            }
        }
    }
}
