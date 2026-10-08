package com.ironvellum.app.domain

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
        assertTrue("Masterwork ${share(RewardRarity.Masterwork)}", share(RewardRarity.Masterwork) in 0.005..0.015)
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
    fun `masterwork never pays less than the best common of its type`() {
        val results = sample()
        val commons = results.filter { it.rarity == RewardRarity.Common }
        val masterworks = results.filter { it.rarity == RewardRarity.Masterwork }
        val bestCommonFigures =
            commons.mapNotNull { it.reward as? Reward.Figures }.maxOf { it.count }
        val worstMasterworkFigures =
            masterworks.mapNotNull { it.reward as? Reward.Figures }.minOf { it.count }
        assertTrue(worstMasterworkFigures > bestCommonFigures)

        val worstMasterworkRelic =
            masterworks.mapNotNull { it.reward as? Reward.Relic }.minOf { it.multiplier }
        assertTrue(worstMasterworkRelic > 1.0)
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
            // Common pays relics and crests too now, so select the figure
            // draws rather than assuming every Common roll is one.
            .mapNotNull { (it.reward as? Reward.Figures)?.count }
            .max()
        assertEquals(40, maxCommon)
    }

    @Test
    fun `roll never returns an owned frame and pays out when all are owned`() {
        // Lifter owns everything except two frames: only those two may drop.
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
                is Reward.Figures -> assertTrue(reward.count > 0)
                is Reward.Relic -> assertTrue(reward.multiplier > 1.0)
            }
        }
    }


    @Test
    fun `every rarity can pay every reward type`() {
        // The old Common row sat at figureChance 1.00, and Common is 60% of
        // all draws: the majority of a lifter's inscriptions were structurally
        // incapable of paying a relic or a crest, whatever they rolled.
        Gacha.DROP_TABLE.forEach { odds ->
            assertTrue("${odds.rarity} cannot pay figures", odds.figureChance > 0.0)
            assertTrue("${odds.rarity} cannot pay a relic", odds.relicChance > 0.0)
            assertTrue("${odds.rarity} cannot pay a crest frame", odds.frameChance > 0.0)
        }
    }

    @Test
    fun `pity closes the figure branch once the streak is served`() {
        // Every seed, not a sample: pity is a guarantee, so a single seed that
        // still paid figures would be a broken promise to the lifter.
        (0L until 20_000L).forEach { seed ->
            val reward = Gacha.roll(seed, figureStreak = Gacha.PITY_AFTER).reward
            assertTrue(
                "seed $seed still paid figures at the pity threshold",
                reward !is Reward.Figures,
            )
        }
    }

    @Test
    fun `pity holds for any streak past the threshold`() {
        (0L until 2_000L).forEach { seed ->
            val reward = Gacha.roll(seed, figureStreak = Gacha.PITY_AFTER + 5).reward
            assertTrue("seed $seed escaped pity", reward !is Reward.Figures)
        }
    }

    @Test
    fun `one draw short of pity is still an ordinary roll`() {
        // Otherwise the guarantee would have quietly become "every draw", and
        // the odds table on screen would be describing something else.
        val figures = (0L until 20_000L).count { seed ->
            Gacha.roll(seed, figureStreak = Gacha.PITY_AFTER - 1).reward is Reward.Figures
        }
        assertTrue("pity fired a draw early: no figures in 20000 rolls", figures > 0)
    }

    @Test
    fun `pity keeps the rarity's own relic-to-frame preference`() {
        // A forced draw must not flatten the split: a Masterwork row leans
        // relic, and pity rescaling the two shares must preserve that.
        val forced = (0L until 100_000L)
            .map { Gacha.roll(it, figureStreak = Gacha.PITY_AFTER) }
            .filter { it.rarity == RewardRarity.Common }
        val relics = forced.count { it.reward is Reward.Relic }
        val frames = forced.size - relics
        // Common is 15 relic to 5 frame, so roughly three relics per frame.
        assertTrue("forced Common draws produced no relics", relics > 0)
        assertTrue("forced Common draws produced no frames", frames > 0)
        assertTrue("forced split ignored the rarity's own odds", relics > frames)
    }

    @Test
    fun `figures no longer dominate the table`() {
        val figures = sample().count { it.reward is Reward.Figures } / sampleSize.toDouble()
        // Was 81.9%, then 66.4%. Pacing moved the table to 50.0% so a lifter holds
        // about 6.7 relics by level 15 (see VeilPacingTest); echoes are still the
        // single most common draw.
        assertTrue("figures share drifted high: $figures", figures < 0.55)
        assertTrue("figures share drifted low: $figures", figures > 0.46)
    }

    @Test
    fun `the first inscription is always a relic of at least the Rare band`() {
        val rareFloor = Gacha.DROP_TABLE[1].relicLow
        (0L until 20_000L).forEach { seed ->
            val reward = Gacha.roll(seed, emptySet(), Gacha.Pity()).reward
            assertTrue("seed $seed paid $reward", reward is Reward.Relic)
            assertTrue("seed $seed first relic below Rare", (reward as Reward.Relic).multiplier >= rareFloor)
        }
    }

    @Test
    fun `a lifter who already holds a relic gets no first-draw guarantee`() {
        val notRelic = (0L until 5_000L).count { seed ->
            Gacha.roll(seed, emptySet(), Gacha.Pity(hasRelic = true)).reward !is Reward.Relic
        }
        assertTrue("an ordinary first draw never missed a relic", notRelic > 0)
    }

    @Test
    fun `a relic lands at least every five draws`() {
        (0L until 20_000L).forEach { seed ->
            val reward = Gacha.roll(seed, relicStreak = Gacha.RELIC_PITY - 1).reward
            assertTrue("seed $seed escaped relic pity", reward is Reward.Relic)
        }
        val short = (0L until 5_000L).count { seed ->
            Gacha.roll(seed, relicStreak = Gacha.RELIC_PITY - 2).reward !is Reward.Relic
        }
        assertTrue("relic pity fired a draw early", short > 0)
    }

    @Test
    fun `relic names and bands are untouched by pacing`() {
        // Names derive from the multiplier, so the bands are the identity of every
        // relic a lifter already owns. 141 reachable names is the audited count.
        assertEquals(
            listOf(1.05 to 1.15, 1.15 to 1.35, 1.35 to 1.75, 1.75 to 2.50),
            Gacha.DROP_TABLE.map { it.relicLow to it.relicHigh },
        )
        assertEquals(141, Gacha.relicCatalogue().size)
    }
}
