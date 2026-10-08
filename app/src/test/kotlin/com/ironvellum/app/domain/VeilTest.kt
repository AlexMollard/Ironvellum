package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VeilTest {

    /** A lifter's Veil as the grant sees it, so it can be applied and applied again. */
    private data class Vault(
        val level: Int,
        val banked: Int,
        val relics: Int,
        val frames: Set<String>,
        val echoes: Int,
    ) {
        fun grant() = Veil.retroGrant(level, banked, relics, frames, echoes)

        fun apply(g: Veil.RetroGrant) = copy(
            banked = banked + g.inscriptions,
            relics = relics + g.relics.size,
            frames = frames + g.crestIds,
        )
    }

    @Test
    fun `the owner at level 15 with 2 relics and nothing banked gets crests, a relic and inscriptions`() {
        val owner = Vault(level = 15, banked = 0, relics = 2, frames = emptySet(), echoes = 900)
        val g = owner.grant()
        assertEquals(listOf("iron", "bronze", "silver"), g.crestIds)
        assertEquals("floor at level 15 is 3 relics, the owner holds 2", 1, g.relics.size)
        // 2 relics + 0 crests allow at most 2 + 3 * 3 = 11 spent draws of the 14 levels.
        assertEquals(3, g.inscriptions)
        val after = owner.apply(g)
        assertEquals(3, after.frames.size)
        assertEquals(3, after.relics)
        assertTrue(after.banked >= 1)
    }

    @Test
    fun `few echoes prove more draws were forgone`() {
        // 100 echoes can have come from at most 5 figure draws: 2 + 5 = 7 spent of 14.
        assertEquals(7, Vault(15, 0, 2, emptySet(), echoes = 100).grant().inscriptions)
    }

    @Test
    fun `banked inscriptions are kept and counted against what was forgone`() {
        assertEquals(0, Vault(15, banked = 3, relics = 2, frames = emptySet(), echoes = 900).grant().inscriptions)
    }

    @Test
    fun `applying the grant twice pays nothing the second time`() {
        for (level in 1..80 step 3) for (banked in listOf(0, 2, 9)) for (relics in listOf(0, 2, 6, 30)) {
            for (frames in listOf(emptySet(), setOf("iron"), setOf("gold", "jade"), Veil.CREST_LADDER.toSet())) {
                for (echoes in listOf(0, 100, 900, 50_000)) {
                    val v = Vault(level, banked, relics, frames, echoes)
                    val again = v.apply(v.grant()).grant()
                    assertTrue("not idempotent for $v: $again", again.isEmpty)
                }
            }
        }
    }

    @Test
    fun `the grant only ever adds and never exceeds a lifter's levels`() {
        for (level in 1..80) for (banked in listOf(0, 1, 5, 40)) for (relics in listOf(0, 1, 3, 12)) {
            for (echoes in listOf(0, 40, 400, 40_000)) {
                val frames = setOf("iron", "gold")
                val g = Vault(level, banked, relics, frames, echoes).grant()
                assertTrue(g.inscriptions >= 0)
                assertTrue(g.inscriptions <= level - 1)
                assertTrue(g.crestIds.none { it in frames })
                assertTrue(g.crestIds.size <= level / Veil.MILESTONE_EVERY)
                assertEquals(maxOf(0, Veil.relicFloor(level) - relics), g.relics.size)
            }
        }
    }

    @Test
    fun `a new lifter at level 1 is paid nothing`() {
        assertTrue(Vault(1, 0, 0, emptySet(), 0).grant().isEmpty)
    }

    @Test
    fun `a lifter already at the floor with every milestone crest is paid nothing`() {
        val g = Vault(15, banked = 11, relics = 3, frames = setOf("iron", "bronze", "silver"), echoes = 0).grant()
        assertTrue(g.toString(), g.isEmpty)
    }

    @Test
    fun `retro relics are deterministic and in the Rare band`() {
        val a = Gacha.stipendRelic(2)
        assertEquals(a, Gacha.stipendRelic(2))
        (0..40).forEach { assertTrue(Gacha.stipendRelic(it).multiplier in 1.15..1.35) }
    }

    @Test
    fun `a milestone pays its own rung, or the lowest unowned when that was already won`() {
        assertEquals("iron", Veil.milestoneCrest(5, emptySet()))
        assertEquals("silver", Veil.milestoneCrest(15, setOf("iron", "bronze")))
        assertEquals("iron", Veil.milestoneCrest(15, setOf("silver")))
        assertEquals("bronze", Veil.milestoneCrest(5, setOf("iron")))
        assertEquals("obsidian", Veil.milestoneCrest(35, Veil.CREST_LADDER.take(6).toSet()))
        assertNull(Veil.milestoneCrest(30, Veil.CREST_LADDER.toSet()))
        assertEquals(listOf("iron", "bronze", "silver", "gold", "jade", "crimson", "obsidian"), Veil.CREST_LADDER)
    }

    @Test
    fun `an inscription costs 5000 and each purchase adds 500`() {
        assertEquals(5_000L, Veil.offeringCost(0))
        assertEquals(5_500L, Veil.offeringCost(1))
        assertEquals(10_000L, Veil.offeringCost(10))
        assertEquals(72_500L, (0 until 10).sumOf { Veil.offeringCost(it) })
        assertEquals(5_000L, Veil.offeringCost(-3))
    }

    @Test
    fun `buying lowers essence and never the lifetime total`() {
        val paid = Veil.Balance(7_000, 7_000).buy(Veil.offeringCost(0))!!
        assertEquals(2_000L, paid.essence)
        assertEquals(7_000L, paid.lifetime)
        val b = paid.earn(300)
        assertEquals(2_300L, b.essence)
        assertEquals(7_300L, b.lifetime)
        assertNull(b.buy(Veil.offeringCost(1)))
        assertNull(Veil.Balance(4_999, 4_999).buy(5_000))
        assertEquals(Veil.Balance(0, 5_000), Veil.Balance(5_000, 5_000).buy(5_000))
    }

    @Test
    fun `lifetime essence is monotonic over any run of earning and buying`() {
        var b = Veil.Balance(0, 0)
        var made = 0
        var last = 0L
        val rng = java.util.Random(7)
        repeat(5_000) {
            b = if (rng.nextInt(3) == 0) b.buy(Veil.offeringCost(made))?.also { made++ } ?: b
            else b.earn(rng.nextInt(900).toLong())
            assertTrue("lifetime fell from $last to ${b.lifetime}", b.lifetime >= last)
            assertTrue(b.essence <= b.lifetime && b.essence >= 0)
            last = b.lifetime
        }
        assertTrue(made > 0)
    }

    @Test
    fun `a row whose lifetime lags its balance is lifted rather than lowered`() {
        val b = Veil.Balance(essence = 900, lifetime = 0).earn(100)
        assertEquals(1_000L, b.essence)
        assertEquals(1_000L, b.lifetime)
    }
}
