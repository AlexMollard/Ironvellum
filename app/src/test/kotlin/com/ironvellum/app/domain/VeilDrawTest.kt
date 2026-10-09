package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VeilDrawTest {

    private fun vault(vararg held: Pair<String, Double>) = RelicHouses.vault(held.map { (id, m) -> OwnedRelic(id, m) })

    private fun sample(perHour: Double, echoes: Int, v: VaultState) = VeilSample(perHour, echoes, v)

    private val threeIron = vault("iron.band" to 1.10, "iron.chain" to 1.25, "iron.plate" to 1.30, "vigil.hourglass" to 1.60)
    private val fourIron = vault("iron.band" to 1.10, "iron.chain" to 1.25, "iron.plate" to 1.30, "iron.crown" to 1.50, "vigil.hourglass" to 1.60)

    private val crown = Reward.Relic(1.50, "Crown of Iron", "iron.crown")

    @Test
    fun `a relic that completes its house names the bonus it reached and the rate it lifted`() {
        val change = VeilDraw.change(
            sample(148.9, 100, threeIron),
            sample(156.3, 100, fourIron),
            RollResult(crown, RewardRarity.Epic),
            Gacha.Pity(figureStreak = 0, relicStreak = 0, draws = 9, hasRelic = true),
        )
        assertEquals(RelicHouse.Iron, change.house)
        assertEquals(listOf(RelicHouses.FULL), change.unlocked.map { it.needed })
        assertEquals("House of Iron complete · full-strength window +2 h", change.bonusLine)
        assertEquals(7.4, change.rateDelta, 1e-9)
        // The 1.60 Hourglass of Vigil is still the strongest.
        assertFalse(change.strongestChanged)
        assertEquals("vigil.hourglass", change.strongestAfter?.id)
    }

    @Test
    fun `a relic that overtakes the strongest says so`() {
        val before = vault("iron.band" to 1.10)
        val after = vault("iron.band" to 1.10, "iron.crown" to 1.50)
        val change = VeilDraw.change(sample(10.0, 0, before), sample(12.0, 0, after), RollResult(crown, RewardRarity.Epic), Gacha.Pity())
        assertTrue(change.strongestChanged)
        assertEquals("iron.band", change.strongestBefore?.id)
        assertEquals("iron.crown", change.strongestAfter?.id)
        // The second relic of Iron reaches the pair bonus.
        assertEquals(listOf(RelicHouses.PAIR), change.unlocked.map { it.needed })
        assertEquals("Set bonus reached · lifting term +5%", change.bonusLine)
    }

    @Test
    fun `the first relic has no strongest before it`() {
        val change = VeilDraw.change(
            sample(10.0, 0, vault()),
            sample(11.0, 0, vault("iron.crown" to 1.50)),
            RollResult(crown, RewardRarity.Epic),
            Gacha.Pity(draws = 1, hasRelic = true),
        )
        assertNull(change.strongestBefore)
        assertTrue(change.strongestChanged)
    }

    @Test
    fun `an echo draw carries its payout, the new total and the run pity counts`() {
        val change = VeilDraw.change(
            sample(148.9, 564, threeIron),
            sample(159.6, 648, threeIron),
            RollResult(Reward.Figures(84), RewardRarity.Rare),
            Gacha.Pity(figureStreak = 2, relicStreak = 2, draws = 12, hasRelic = true),
        )
        assertEquals(84, change.echoesPaid)
        assertEquals(648, change.echoesAfter)
        assertEquals(2, change.echoRun)
        assertEquals(10.7, change.rateDelta, 1e-9)
        assertNull(change.house)
    }

    @Test
    fun `a rate that does not move on screen reads as unchanged`() {
        val change = VeilDraw.change(
            sample(148.91, 0, threeIron), sample(148.94, 0, threeIron),
            RollResult(Reward.CrestFrame("iron", "Iron Crest"), RewardRarity.Rare), Gacha.Pity(),
        )
        assertEquals(0.0, change.rateDelta, 0.0)
    }

    @Test
    fun `the landing names a relic that joined, the rate over the run and marks it new`() {
        val change = VeilDraw.change(
            sample(148.9, 0, threeIron), sample(156.3, 0, fourIron), RollResult(crown, RewardRarity.Epic), Gacha.Pity(draws = 3, hasRelic = true),
        )
        val landing = VeilDraw.landing(listOf(change))!!
        assertEquals("Crown of Iron joined your collection", landing.line)
        assertEquals(CollectionTab.Relics, landing.tab)
        assertEquals(7.4, landing.rateDelta, 1e-9)
        assertEquals("Crown of Iron", landing.fresh)
        assertEquals("iron.crown", landing.freshRelicId)
        assertEquals(RewardRarity.Epic, landing.freshTier)
        assertEquals(setOf(RelicHouse.Iron), landing.completed)
        assertEquals("House of Iron complete · full-strength window +2 h", landing.freshNote)
    }

    @Test
    fun `a crest that joined opens the crests tab`() {
        val change = VeilDraw.change(
            sample(10.0, 0, vault()), sample(10.0, 0, vault()),
            RollResult(Reward.CrestFrame("aurora", "Aurora Crest"), RewardRarity.Epic), Gacha.Pity(draws = 4),
        )
        val landing = VeilDraw.landing(listOf(change))!!
        assertEquals("Aurora crest joined your collection", landing.line)
        assertEquals(CollectionTab.Crests, landing.tab)
        assertEquals("aurora", landing.freshCrestId)
    }

    @Test
    fun `echoes and refinements add nothing to hold, so nothing is marked new`() {
        val echoes = VeilDraw.change(
            sample(10.0, 0, vault()), sample(10.8, 84, vault()), RollResult(Reward.Figures(84), RewardRarity.Rare), Gacha.Pity(figureStreak = 1),
        )
        val landing = VeilDraw.landing(listOf(echoes))!!
        assertEquals("84 echoes added to the Veil", landing.line)
        assertNull(landing.fresh)
        assertEquals(0.8, landing.rateDelta, 1e-9)

        val refined = VeilDraw.change(
            sample(10.0, 0, fourIron), sample(10.0, 0, fourIron),
            RollResult(Reward.Relic(1.55, "Crown of Iron", "iron.crown", RelicOutcome.Refined), RewardRarity.Epic), Gacha.Pity(),
        )
        val line = VeilDraw.landing(listOf(refined))!!
        assertEquals("Crown of Iron was refined to ×1.55", line.line)
        assertNull(line.fresh)
    }

    @Test
    fun `a run of draws reports the rate over the whole run and the last thing that joined`() {
        val first = VeilDraw.change(sample(100.0, 0, vault()), sample(110.0, 0, vault("iron.crown" to 1.5)), RollResult(crown, RewardRarity.Epic), Gacha.Pity())
        val second = VeilDraw.change(
            sample(110.0, 0, vault("iron.crown" to 1.5)), sample(115.0, 50, vault("iron.crown" to 1.5)),
            RollResult(Reward.Figures(50), RewardRarity.Common), Gacha.Pity(figureStreak = 1),
        )
        val landing = VeilDraw.landing(listOf(first, second))!!
        assertEquals("Crown of Iron joined your collection", landing.line)
        assertEquals(15.0, landing.rateDelta, 1e-9)
        assertNull(VeilDraw.landing(emptyList()))
    }

    @Test
    fun `the time to afford rounds up and says nothing when nothing gathers`() {
        assertEquals(11, hoursToGather(1_580.0, 148.9))
        assertEquals(1, hoursToGather(0.5, 148.9))
        assertNull(hoursToGather(0.0, 148.9))
        assertNull(hoursToGather(100.0, 0.0))
    }

    @Test
    fun `what a draw can give is read off the drop table and sums to one`() {
        val shares = Gacha.typeShares()
        assertEquals(1.0, shares.echoes + shares.relic + shares.crest, 1e-9)
        assertEquals(50, Math.round(shares.echoes * 100).toInt())
        assertEquals(40, Math.round(shares.relic * 100).toInt())
        assertEquals(10, Math.round(shares.crest * 100).toInt())
    }

    @Test
    fun `the pity line tells the truth about the next draw`() {
        assertEquals("Your first inscription is a relic.", Gacha.pityLine(Gacha.Pity()))
        val live = Gacha.Pity(figureStreak = 1, relicStreak = 1, draws = 4, hasRelic = true)
        assertEquals("After 3 echo draws in a row, the next one is a relic or a crest.", Gacha.pityLine(live))
        assertEquals(
            "Your last 3 draws were echoes, so this one is a relic or a crest.",
            Gacha.pityLine(live.copy(figureStreak = Gacha.PITY_AFTER)),
        )
        assertEquals(
            "It has been 4 draws since your last relic, so this one is a relic.",
            Gacha.pityLine(live.copy(relicStreak = Gacha.RELIC_PITY - 1)),
        )
    }
}
