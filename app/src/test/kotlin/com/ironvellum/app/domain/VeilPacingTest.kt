package com.ironvellum.app.domain

import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins what a lifter holds by a given level under the pacing rules: the first
 * inscription is a relic, a relic lands at least every [Gacha.RELIC_PITY] draws,
 * and the 3-figure pity still stands. Level L has had L - 1 draws, so level 15
 * is 14 draws.
 *
 * Two independent checks of the same numbers: an exact computation over the
 * pity state that reads [Gacha.DROP_TABLE] directly, and a seeded simulation
 * through [Gacha.roll] itself, so the table, the rules and the roller cannot
 * drift apart.
 */
class VeilPacingTest {

    private data class Outcome(val expectedRelics: Double, val expectedFrames: Double, val minRelics: Int, val pBelow: (Int) -> Double)

    /** Exact distribution over (figure streak, relic streak, relics held) for [draws] inscriptions. */
    private fun exact(draws: Int): Outcome {
        data class S(val figureStreak: Int, val relicStreak: Int, val relics: Int)
        var dist = mapOf(S(0, 0, 0) to 1.0)
        var frames = 0.0
        repeat(draws) { n ->
            val next = HashMap<S, Double>()
            fun add(s: S, p: Double) { next[s] = (next[s] ?: 0.0) + p }
            dist.forEach { (s, p) ->
                val forcedRelic = n == 0 || s.relicStreak >= Gacha.RELIC_PITY - 1
                val forcedNonFigure = s.figureStreak >= Gacha.PITY_AFTER
                Gacha.DROP_TABLE.forEach { row ->
                    val q = p * row.chance
                    when {
                        forcedRelic -> add(S(0, 0, s.relics + 1), q)
                        forcedNonFigure -> {
                            val relicShare = row.relicChance / (row.relicChance + row.frameChance)
                            add(S(0, 0, s.relics + 1), q * relicShare)
                            add(S(0, s.relicStreak + 1, s.relics), q * (1 - relicShare))
                            frames += q * (1 - relicShare)
                        }
                        else -> {
                            add(S(s.figureStreak + 1, s.relicStreak + 1, s.relics), q * row.figureChance)
                            add(S(0, 0, s.relics + 1), q * row.relicChance)
                            add(S(0, s.relicStreak + 1, s.relics), q * row.frameChance)
                            frames += q * row.frameChance
                        }
                    }
                }
            }
            dist = next
        }
        return Outcome(
            expectedRelics = dist.entries.sumOf { (s, p) -> s.relics * p },
            expectedFrames = frames,
            minRelics = dist.filter { it.value > 1e-15 }.keys.minOf { it.relics },
            pBelow = { k -> dist.entries.filter { it.key.relics < k }.sumOf { it.value } },
        )
    }

    @Test
    fun `level 15 holds about 6 to 7 relics and never fewer than 3`() {
        val o = exact(14)
        assertTrue("expected relics ${o.expectedRelics}", o.expectedRelics in 6.4..6.9)
        assertEquals("a lifter at level 15 can never hold fewer than 3", 3, o.minRelics)
        assertEquals(0.0, o.pBelow(3), 1e-12)
        // Chance crests ride on top of the 3 milestone crests (levels 5, 10, 15).
        assertTrue("chance crests ${o.expectedFrames}", o.expectedFrames in 1.0..1.7)
    }

    @Test
    fun `the relic floor is exactly the fewest relics the rules allow`() {
        (2..40).forEach { level ->
            assertEquals("level $level", exact(level - 1).minRelics, Veil.relicFloor(level))
        }
        assertEquals(0, Veil.relicFloor(1))
        assertEquals(3, Veil.relicFloor(15))
    }

    @Test
    fun `a seeded simulation through the roller agrees with the exact numbers`() {
        val lifters = 20_000
        val rng = Random(2026)
        var relicTotal = 0L
        var fewest = Int.MAX_VALUE
        repeat(lifters) {
            var pity = Gacha.Pity()
            val owned = HashSet<String>()
            var relics = 0
            repeat(14) {
                val result = Gacha.roll(rng.nextLong(), owned, pity)
                (result.reward as? Reward.CrestFrame)?.let { owned += it.id }
                if (result.reward is Reward.Relic) relics++
                pity = pity.after(result.reward)
            }
            relicTotal += relics
            fewest = minOf(fewest, relics)
        }
        val mean = relicTotal.toDouble() / lifters
        assertEquals(exact(14).expectedRelics, mean, 0.05)
        assertTrue("fewest relics in $lifters lifters: $fewest", fewest >= 3)
    }

    @Test
    fun `three milestone crests by level 15 and one more every five levels`() {
        assertEquals(listOf(5, 10, 15), Veil.milestonesCrossed(0, 15))
        assertEquals(listOf(20), Veil.milestonesCrossed(15, 22))
        assertEquals(emptyList<Int>(), Veil.milestonesCrossed(15, 19))
        // A level already paid never pays its milestone again.
        assertEquals(emptyList<Int>(), Veil.milestonesCrossed(15, 15))
        assertEquals(emptyList<Int>(), Veil.milestonesCrossed(20, 15))
        // A jump across several milestones pays each.
        assertEquals(listOf(10, 15, 20), Veil.milestonesCrossed(9, 21))
    }
}
