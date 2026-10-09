package com.ironvellum.app.domain

import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the no-crest case. A lifter who wears nothing must roll, earn and bank exactly what they did
 * before the crest expansion: the digests below were taken from the code as it stood before any perk
 * existed. A crest frame draw is written as "crest" because the draw pool changed on purpose (only
 * the six Veil crests can be drawn now); everything else a seed produces is pinned to the digit.
 */
class NoCrestBaselineTest {

    private fun digest(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun describe(r: RollResult): String = when (val x = r.reward) {
        is Reward.Figures -> "${r.rarity}:fig:${x.count}"
        is Reward.Relic -> "${r.rarity}:relic:${x.relicId}:${x.multiplier}:${x.outcome}:${x.echoes}"
        is Reward.CrestFrame -> "${r.rarity}:crest"
    }

    @Test
    fun `seeded rolls are what they always were`() {
        val lines = buildList {
            for (seed in 0L until 300L) {
                add(describe(Gacha.roll(seed)))
                add(describe(Gacha.roll(seed, figureStreak = 3)))
                add(describe(Gacha.roll(seed, relicStreak = 4)))
                add(describe(Gacha.roll(seed, guaranteeRelic = true)))
                add(describe(Gacha.roll(seed, ownedRelics = RelicHouses.CATALOGUE.associate { it.id to 1.2 })))
            }
        }
        assertEquals("f012bec668515813927464578981edf8f6c0e4451893961d7e43e8026d3e3a60", digest(lines.joinToString("\n")))
    }

    @Test
    fun `the rate and what an absence banks are what they always were`() {
        val lines = buildList {
            val day = 3_600_000L
            for (sessions in listOf(0, 1, 3, 5, 9)) for (volume in listOf(0.0, 150.0, 2_000.0, 40_000.0)) {
                for (skills in listOf(0, 8, 25, 90)) for (streak in listOf(0, 3, 7, 21, 60)) {
                    for (relic in listOf(1.0, 1.4, 3.2)) for (echoes in listOf(0, 800, 5_000)) {
                        val state = IdleState(0, echoes, relic, 1_000L)
                        val rate = Idle.rate(state, sessions, volume, skills, streak)
                        add("$sessions/$volume/$skills/$streak/$relic/$echoes ${rate.perHour} ${rate.trainingFactor} ${rate.skillFactor}")
                        if (sessions == 3 && skills == 25 && echoes == 800) {
                            for (h in listOf(1, 20, 24, 30, 48, 72, 80, 120, 168, 400)) {
                                add("  ${h}h ${Idle.accruedExact(state, rate, 1_000L + h * day)}")
                            }
                        }
                    }
                }
            }
        }
        assertEquals("ed52a77345c2d4ed1683572a6add787b844c58b9372d53aaf26c0c18809b5d6f", digest(lines.joinToString("\n")))
    }
}
