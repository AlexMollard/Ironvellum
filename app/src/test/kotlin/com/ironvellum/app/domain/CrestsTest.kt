package com.ironvellum.app.domain

import java.time.ZoneId
import kotlin.math.exp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The crest catalogue: what each perk pays, the swap rule, what a lifter is owed, and the words that say so. */
class CrestsTest {

    private val t0 = 1_700_000_000_000L
    private val hour = 3_600_000L
    private val state = IdleState(essence = 0, figures = 0, relicMultiplier = 1.0, lastCollectedAtMs = t0)

    /** Every relic of the catalogue at a distinct multiplier, so every vault perk has something to move. */
    private val fullVault = RelicHouses.CATALOGUE.mapIndexed { i, r -> OwnedRelic(r.id, 1.6 - i * 0.03) }

    private fun rate(
        worn: String?,
        sessions: Int = 3,
        volume: Double = 200.0,
        skills: Int = 10,
        streak: Int = 4,
        relics: List<OwnedRelic> = emptyList(),
        s: IdleState = state,
    ) = Idle.rate(s, sessions, volume, skills, streak, RelicHouses.effects(relics, worn))

    // ------------------------------------------------------------------ the catalogue

    @Test
    fun `there are 28 crests with unique ids in four groups`() {
        assertEquals(28, Crests.ALL.size)
        assertEquals(28, Crests.ALL.map { it.id }.toSet().size)
        assertEquals(12, Crests.inGroup(CrestGroup.Ladder).size)
        assertEquals(6, Crests.inGroup(CrestGroup.Deeds).size)
        assertEquals(4, Crests.inGroup(CrestGroup.Houses).size)
        assertEquals(6, Crests.inGroup(CrestGroup.Veil).size)
    }

    @Test
    fun `nothing worn, or a crest this build does not know, changes nothing`() {
        assertEquals(CrestEffects.NONE, Crests.effects(null, fullVault))
        assertEquals(CrestEffects.NONE, Crests.effects("from_a_newer_build", fullVault))
        assertEquals(rate(null).perHour, rate("from_a_newer_build").perHour, 0.0)
    }

    @Test
    fun `every crest does something to a lifter who holds a full vault`() {
        for (def in Crests.ALL) {
            val fx = Crests.effects(def.id, fullVault)
            assertEquals(def.id, fx.wornId)
            assertNotEquals("${def.id} has no effect", CrestEffects.NONE, fx.copy(wornId = null))
        }
    }

    // ------------------------------------------------------------------ perks that move the rate

    @Test
    fun `Iron lifts the rate 3 percent`() {
        assertEquals(rate(null).perHour * 1.03, rate("iron").perHour, 1e-9)
    }

    @Test
    fun `Obsidian counts each trial as 3 and a half`() {
        val r = rate("obsidian", sessions = 3, volume = 0.0, streak = 0)
        assertEquals(1.0 + 3 * 3.5 / Idle.FLOOR, r.trainingFactor, 1e-9)
    }

    @Test
    fun `Watchfire counts each oath day as 1 and a half`() {
        val r = rate("watchfire", sessions = 0, volume = 0.0, streak = 4)
        assertEquals(1.0 + 4 * 1.5 / Idle.FLOOR, r.trainingFactor, 1e-9)
    }

    @Test
    fun `Throne counts every rep 20 percent more`() {
        val r = rate("throne", sessions = 0, volume = 100.0, streak = 0)
        assertEquals(1.0 + 100 * 0.06 / Idle.FLOOR, r.trainingFactor, 1e-9)
    }

    @Test
    fun `Anvil lifts the training ceiling to 4 point 15 and the constant stays 4`() {
        val capped = rate("anvil", sessions = 20, volume = 50_000.0, streak = 60)
        assertEquals(4.15, capped.trainingFactor, 1e-9)
        assertEquals(4.0, rate(null, sessions = 20, volume = 50_000.0, streak = 60).trainingFactor, 1e-9)
        assertEquals(4.0, Idle.MAX_TRAINING_FACTOR, 0.0)
    }

    @Test
    fun `Sage lifts the technique ceiling to 2 point 06 and the constant stays 2`() {
        val deep = rate("sage", skills = 500)
        assertEquals(2.06, deep.skillFactor, 1e-6)
        assertTrue(rate(null, skills = 500).skillFactor < 2.0)
        assertEquals(2.0, Idle.MAX_SKILL_FACTOR, 0.0)
    }

    @Test
    fun `Verdant makes each technique count for more`() {
        val rate = Idle.SKILL_RATE * 1.15
        assertEquals(1.0 + (1.0 - exp(-rate * 10)), rate("verdant", skills = 10).skillFactor, 1e-9)
        assertTrue(rate("verdant", skills = 10).skillFactor > rate(null, skills = 10).skillFactor)
    }

    @Test
    fun `Dawnroad adds a flat hour before the multipliers`() {
        val none = rate(null, skills = 0)
        val worn = rate("dawnroad", skills = 0)
        assertEquals(none.perHour + 1.0, worn.perHour, 1e-9)
    }

    @Test
    fun `Ashen King pays 5 percent only while the oath stands at 7 days`() {
        assertEquals(rate(null, streak = 6).perHour, rate("ashenking", streak = 6).perHour, 1e-9)
        assertEquals(rate(null, streak = 7).perHour * 1.05, rate("ashenking", streak = 7).perHour, 1e-9)
    }

    @Test
    fun `Silver trims the echoes 50 percent harder and raises their ceiling`() {
        val held = state.copy(figures = 1_000)
        assertEquals(1.10, rate(null, s = held).echoFactor, 1e-9)
        assertEquals(1.15, rate("silver", s = held).echoFactor, 1e-9)
        val deep = state.copy(figures = 100_000)
        assertEquals(1.25, rate(null, s = deep).echoFactor, 1e-9)
        assertEquals(1.30, rate("silver", s = deep).echoFactor, 1e-9)
    }

    @Test
    fun `the four house crests lift only their own term`() {
        // Iron pairs would change the bonus too, so hold no relics: the term is the crest's alone.
        val sessions = 3
        val iron = rate("ironhold", sessions = sessions, volume = 0.0, streak = 0)
        assertEquals(1.0 + sessions * 3.0 * 1.06 / Idle.FLOOR, iron.trainingFactor, 1e-9)
        val vigil = rate("bellwarden", sessions = 0, volume = 0.0, streak = 5)
        assertEquals(1.0 + 5 * 1.0 * 1.15 / Idle.FLOOR, vigil.trainingFactor, 1e-9)
        val craft = rate("masterwright", skills = 10)
        assertEquals(1.0 + 1.08 * (1.0 - exp(-Idle.SKILL_RATE * 10)), craft.skillFactor, 1e-9)
        // Ironhold does nothing to the technique term, and Masterwright nothing to the lifting term.
        assertEquals(rate(null, skills = 10).skillFactor, rate("ironhold", skills = 10).skillFactor, 1e-12)
        assertEquals(rate(null).trainingFactor, rate("masterwright").trainingFactor, 1e-12)
    }

    @Test
    fun `Vellum raises the pair bonus from 5 to 8 percent on a pair only`() {
        val iron = RelicHouses.CATALOGUE.filter { it.house == RelicHouse.Iron }.map { it.id }
        val pair = listOf(OwnedRelic(iron[0], 1.2), OwnedRelic(iron[1], 1.2))
        assertEquals(1.0 + 0.08, RelicHouses.effects(pair, "vellum").term(RelicHouse.Iron), 1e-12)
        assertEquals(1.0 + RelicHouses.PAIR_BONUS, RelicHouses.effects(pair).term(RelicHouse.Iron), 1e-12)
        assertEquals(1.0, RelicHouses.effects(pair.take(1), "vellum").term(RelicHouse.Iron), 1e-12)
    }

    @Test
    fun `Sovereign pays 0 point 3 percent a relic and Ledger 2 percent a finished house`() {
        val three = fullVault.take(3)
        assertEquals(1.0 + 0.003 * 3, Crests.effects("sovereign", three).rateFactor, 1e-12)
        assertEquals(1.0 + 0.003 * 16, Crests.effects("sovereign", fullVault).rateFactor, 1e-12)
        assertEquals(1.0, Crests.effects("ledger", three).rateFactor, 1e-12)
        assertEquals(1.0 + 0.02 * 4, Crests.effects("ledger", fullVault).rateFactor, 1e-12)
    }

    // ------------------------------------------------------------------ the vault stack: Deep Vault and Margin

    @Test
    fun `Deep Vault and Margin lift the stack only when relics back the strongest`() {
        val one = listOf(OwnedRelic(RelicHouses.CATALOGUE.first().id, 1.5))
        assertEquals(1.0, Crests.effects("ironvellum", one).relicFactor, 1e-12)
        assertEquals(1.0, Crests.effects("margin", one).relicFactor, 1e-12)
        val many = fullVault
        val deep = Crests.effects("ironvellum", many).relicFactor
        val margin = Crests.effects("margin", many).relicFactor
        assertTrue("Deep Vault should help a full vault: $deep", deep > 1.0)
        assertTrue("Margin should help a full vault: $margin", margin > 1.0)
        // Their ratio is exactly the two stacks' ratio: the stored multiplier is never touched.
        val ms = many.map { it.multiplier }
        assertEquals(
            Relics.effectiveMultiplier(ms, restWeight = 1.25) / Relics.effectiveMultiplier(ms),
            margin,
            1e-12,
        )
        assertEquals(
            Relics.effectiveMultiplier(ms, excessCap = 5.0) / Relics.effectiveMultiplier(ms),
            deep,
            1e-12,
        )
        // Neither is a runaway on a synthetic vault far fuller than a real one: a perk, not an engine.
        assertTrue("deep $deep", deep < 1.25)
        assertTrue("margin $margin", margin < 1.25)
    }

    // ------------------------------------------------------------------ the away curve

    private fun banked(worn: String?, hours: Int, relics: List<OwnedRelic> = emptyList()): Double {
        val r = rate(worn, relics = relics)
        return Idle.accruedExact(state, r, t0 + hours * hour)
    }

    @Test
    fun `an absence under a day banks the rate times the hours for every crest but Marathon`() {
        for (def in Crests.ALL) {
            val r = rate(def.id, relics = fullVault)
            val got = Idle.accruedExact(state, r, t0 + 10 * hour)
            val awayPay = if (def.id == "marathon") 1.05 else 1.0
            assertEquals("${def.id}: accrual disagrees with the rate shown", r.perHour * 10 * awayPay, got, 1e-6)
        }
    }

    @Test
    fun `Jade holds full strength for 26 hours`() {
        val r = rate("jade")
        assertEquals(r.perHour * 26, Idle.accruedExact(state, r, t0 + 26 * hour), 1e-6)
        assertTrue(banked("jade", 30) > banked(null, 30))
        assertEquals(26.0, r.effects.fullStrengthHours, 0.0)
    }

    @Test
    fun `Crimson floors a long absence at 13 percent`() {
        assertEquals(0.13, rate("crimson").effects.minEfficiency, 1e-12)
        assertEquals(0.13, Idle.efficiencyAtHours(500.0, rate("crimson").effects), 1e-12)
        assertEquals(0.10, Idle.efficiencyAtHours(500.0, rate(null).effects), 1e-12)
    }

    @Test
    fun `Void stretches the taper to 54 hours`() {
        val fx = rate("void").effects
        assertEquals(54.0, fx.taperWindowHours, 0.0)
        // Halfway down the longer ramp is later than halfway down the old one.
        assertTrue(Idle.efficiencyAtHours(24.0 + 36.0, fx) > Idle.efficiencyAtHours(24.0 + 36.0, rate(null).effects))
        assertEquals(Idle.MIN_EFFICIENCY, Idle.efficiencyAtHours(24.0 + 54.0, fx), 1e-12)
        assertTrue(banked("void", 60) > banked(null, 60))
    }

    @Test
    fun `Marathon banks 5 percent more of what an absence earns`() {
        assertEquals(banked(null, 40) * 1.05, banked("marathon", 40), 1e-6)
    }

    // ------------------------------------------------------------------ offerings

    @Test
    fun `the price shown is the price charged for every crest`() {
        for (def in Crests.ALL) {
            val fx = Crests.effects(def.id, fullVault)
            for (made in 0..6) {
                val shown = Veil.offeringCost(made, fx)
                val expected = ((5_000L + fx.offeringStep * made) * fx.offeringScale).let { Math.round(it) }
                assertEquals("${def.id} made=$made", expected, shown)
            }
        }
        assertEquals(5_000L, Veil.offeringCost(0, CrestEffects.NONE))
        assertEquals(6_500L, Veil.offeringCost(3, CrestEffects.NONE))
        assertEquals(4_750L, Veil.offeringCost(0, Crests.effects("bronze", emptyList())))
        assertEquals(6_200L, Veil.offeringCost(3, Crests.effects("ember", emptyList())))
    }

    // ------------------------------------------------------------------ draws

    @Test
    fun `only the six Veil crests can be drawn`() {
        assertEquals(setOf("aurora", "void", "masterwork", "ledger", "margin", "ashenking"), Gacha.CREST_FRAMES.map { it.id }.toSet())
        val all = Gacha.CREST_FRAMES.map { it.id }.toSet()
        assertTrue(Gacha.typeShares(emptySet()).crest > 0.0)
        val done = Gacha.typeShares(all)
        assertEquals(0.0, done.crest, 0.0)
        // The share a finished set can no longer pay goes to echoes, so the shares still sum to the same.
        val open = Gacha.typeShares(emptySet())
        assertEquals(open.echoes + open.relic + open.crest, done.echoes + done.relic + done.crest, 1e-12)
    }

    @Test
    fun `Gold pays 50 percent more echoes and Aurora never pays under 60 percent of the band`() {
        val gold = Crests.effects("gold", emptyList()).draw
        assertEquals(150, gold.echoes(100, 100, 0.0))
        assertTrue(gold.echoRange(100, 200).last > DrawPerks.NONE.echoRange(100, 200).last)
        val aurora = Crests.effects("aurora", emptyList()).draw
        assertTrue(aurora.echoRange(100, 200).first >= 160)
        assertEquals(100..200, DrawPerks.NONE.echoRange(100, 200))
    }

    @Test
    fun `no perks is the roller it always was`() {
        for (seed in 0L until 200L) {
            assertEquals(Gacha.roll(seed), Gacha.roll(seed, perks = DrawPerks.NONE))
        }
    }

    @Test
    fun `Masterwork lifts a new relic and never past the top of its band`() {
        val lifted = (0L until 400L).mapNotNull { (Gacha.roll(it, perks = DrawPerks(newRelicLift = 0.10)).reward as? Reward.Relic) }
        val plain = (0L until 400L).mapNotNull { (Gacha.roll(it).reward as? Reward.Relic) }
        assertTrue(lifted.isNotEmpty())
        assertEquals(plain.size, lifted.size)
        assertTrue(lifted.zip(plain).all { (a, b) -> a.multiplier >= b.multiplier })
        assertTrue(lifted.zip(plain).any { (a, b) -> a.multiplier > b.multiplier })
    }

    // ------------------------------------------------------------------ what a lifter is owed, and the swap rule

    @Test
    fun `a lifter is owed the ladder to their level, their deeds and their finished houses`() {
        val owed = Crests.earned(12, setOf("unbroken", "not_a_deed"), setOf(RelicHouse.Iron)).map { it.id }
        assertEquals(listOf("iron", "bronze", "anvil", "ironhold"), owed)
        assertEquals(emptyList<String>(), Crests.earned(4, emptySet(), emptySet()).map { it.id })
        assertEquals(12, Crests.earned(99, emptySet(), emptySet()).size)
    }

    @Test
    fun `the crest changes once a day and the first change is free`() {
        val utc = ZoneId.of("UTC")
        val noon = 1_700_000_000_000L - (1_700_000_000_000L % 86_400_000L) + 12 * hour
        assertTrue(Crests.canChange(null, noon, utc))
        assertFalse(Crests.canChange(noon, noon + 2 * hour, utc))
        assertFalse(Crests.canChange(noon, noon + 11 * hour, utc))
        // The next calendar day, even an hour after midnight, counts as another day.
        assertTrue(Crests.canChange(noon, noon + 13 * hour, utc))
        assertTrue(Crests.canChange(noon, noon + 30 * hour, utc))
        // A day's boundary is the lifter's own, so a zone moves it.
        assertFalse(Crests.canChange(noon, noon + 13 * hour, ZoneId.of("Pacific/Kiritimati")))
    }

    @Test
    fun `legacy rows count as draws and ladder, deed and house crests do not`() {
        assertTrue(CrestSource.Legacy.countsAsDraw)
        assertTrue(CrestSource.Draw.countsAsDraw)
        assertFalse(CrestSource.Ladder.countsAsDraw)
        assertFalse(CrestSource.Deed.countsAsDraw)
        assertFalse(CrestSource.House.countsAsDraw)
        assertEquals(CrestSource.Legacy, CrestSource.of(null))
        assertEquals(CrestSource.Legacy, CrestSource.of("something_new"))
        assertEquals(CrestSource.Ladder, CrestSource.awardedFor(Crests.byId("iron")!!))
        assertEquals(CrestSource.Deed, CrestSource.awardedFor(Crests.byId("anvil")!!))
        assertEquals(CrestSource.House, CrestSource.awardedFor(Crests.byId("ironhold")!!))
    }

    // ------------------------------------------------------------------ worth, words and signing

    @Test
    fun `worth measures each crest against wearing none`() {
        val worth = Crests.worth(state, 3, 200.0, 10, 4, fullVault)
        assertEquals(28, worth.size)
        val none = rate(null, relics = fullVault).perHour
        assertEquals(rate("iron", relics = fullVault).perHour - none, worth.getValue("iron"), 1e-9)
        assertEquals(0.0, worth.getValue("bronze"), 0.0)
        assertTrue(worth.getValue("throne") > 0.0)
    }

    @Test
    fun `every crest has a perk line, a source line and a progress line`() {
        for (def in Crests.ALL) {
            assertTrue(def.id, CrestCatalogue.perkLine(def).isNotBlank())
            assertTrue(def.id, CrestCatalogue.sourceLine(def).isNotBlank())
            assertFalse(def.id, CrestCatalogue.sourceLine(def).contains("null"))
            assertTrue(def.id, def.how.isNotBlank() && !def.how.contains("null"))
            val held = CrestCatalogue.progress(def, true, 1, null, emptyMap(), emptyMap())
            assertEquals(CrestCatalogue.perkLine(def), held.line)
            val away = CrestCatalogue.progress(def, false, 1, null, emptyMap(), emptyMap())
            assertTrue(def.id, away.line.isNotBlank() && !away.line.contains("null"))
        }
    }

    @Test
    fun `only the next ladder crest wears a bar`() {
        val next = Crests.byId("silver")!!
        val bar = CrestCatalogue.progress(next, false, 12, "silver", emptyMap(), emptyMap())
        assertEquals("12/15", bar.count)
        assertEquals(0.8f, bar.fraction!!, 1e-6f)
        assertEquals("Level 15 · 3 levels to go", bar.line)
        val later = CrestCatalogue.progress(Crests.byId("gold")!!, false, 12, "silver", emptyMap(), emptyMap())
        assertNull(later.fraction)
        val house = CrestCatalogue.progress(Crests.byId("ironhold")!!, false, 1, null, emptyMap(), mapOf(RelicHouse.Iron to 3))
        assertEquals("3/4", house.count)
    }

    @Test
    fun `the worth note says what it can measure and no more`() {
        assertTrue(CrestCatalogue.worthNote(Crests.byId("iron")!!, 1.234).contains("+1.2"))
        assertTrue(CrestCatalogue.worthNote(Crests.byId("iron")!!, 0.0).contains("almost nothing"))
        assertTrue(CrestCatalogue.worthNote(Crests.byId("bronze")!!, 0.0).contains("price you are shown"))
        assertTrue(CrestCatalogue.worthNote(Crests.byId("gold")!!, 0.0).contains("odds"))
        assertTrue(CrestCatalogue.worthNote(Crests.byId("jade")!!, 0.0).contains("away"))
    }

    @Test
    fun `signing a card appends the crest name and nothing else`() {
        assertEquals("Trial sealed", WorkoutShare.signed("Trial sealed", null))
        assertEquals("Trial sealed\n\nIron crest", WorkoutShare.signed("Trial sealed\n", Crests.byId("iron")))
    }
}
