package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class RelicHousesTest {

    private val base = 1_700_000_000_000L
    private val hour = 3_600_000L

    private fun owned(vararg pairs: Pair<String, Double>) = pairs.map { (id, m) -> OwnedRelic(id, m) }

    private fun state(lastCollectedAtMs: Long = base) = IdleState(0, 0, 1.0, lastCollectedAtMs)

    private fun rate(houses: HouseEffects = HouseEffects.NONE, sessions: Int = 0, volume: Double = 0.0, skills: Int = 0, streak: Int = 0) =
        Idle.rate(state(), sessions, volume, skills, streak, houses)

    // ------------------------------------------------------------------ the catalogue

    @Test
    fun `the catalogue is 4 houses of 4 relics with unique stable ids and names`() {
        assertEquals(16, RelicHouses.CATALOGUE.size)
        assertEquals(16, RelicHouses.CATALOGUE.map { it.id }.toSet().size)
        assertEquals(16, RelicHouses.CATALOGUE.map { it.name }.toSet().size)
        RelicHouse.entries.forEach { house ->
            assertEquals(house.id, 4, RelicHouses.CATALOGUE.count { it.house == house })
        }
        RelicHouses.CATALOGUE.forEach {
            assertEquals("${it.house.id}.${it.form}", it.id)
            assertEquals("${it.form.replaceFirstChar(Char::uppercase)} of ${it.house.label}", it.name)
        }
    }

    @Test
    fun `the ids and tiers are frozen`() {
        assertEquals(
            listOf(
                "iron.band", "iron.crown", "iron.chain", "iron.plate",
                "vigil.bell", "vigil.seal", "vigil.key", "vigil.hourglass",
                "craft.quill", "craft.compass", "craft.seal", "craft.chisel",
                "return.lantern", "return.thread", "return.gate", "return.door",
            ),
            RelicHouses.CATALOGUE.map { it.id },
        )
        assertEquals(
            mapOf(RewardRarity.Common to 6, RewardRarity.Rare to 6, RewardRarity.Epic to 3, RewardRarity.Masterwork to 1),
            RelicHouses.CATALOGUE.groupingBy { it.tier }.eachCount(),
        )
    }

    @Test
    fun `no relic or house text uses a glossary-banned word`() {
        val banned = Regex("\\b(workout|session|plans?|streak|scheduled|rest day)\\b", RegexOption.IGNORE_CASE)
        val text = RelicHouse.entries.flatMap {
            listOf(it.title, it.habit, it.follows, it.term, it.pairBonus, it.fullBonus, it.fullGain, it.effect(1.4))
        } + RelicHouses.CATALOGUE.map { it.name }
        text.forEach { assertFalse(it, banned.containsMatchIn(it)) }
    }

    // ------------------------------------------------------------------ legacy relics

    private fun legacyRows(): List<RelicRow> = Gacha.relicCatalogue().mapIndexed { i, r ->
        RelicRow(i.toLong(), null, r.name, r.multiplier, drawnAtMs = 1_000L + i)
    }

    private fun weight(rows: List<PlacedRelic>) = rows.sumOf { 1 + it.refinements }

    @Test
    fun `mapping is deterministic and independent of input order`() {
        val rows = legacyRows()
        val first = RelicHouses.place(rows)
        assertEquals(first, RelicHouses.place(rows))
        assertEquals(first, RelicHouses.place(rows.shuffled(Random(7))))
        assertEquals(first, RelicHouses.place(rows.reversed()))
    }

    @Test
    fun `mapping never loses a relic, even for the whole 141 name catalogue`() {
        val rows = legacyRows()
        assertEquals(141, rows.size)
        val placed = RelicHouses.place(rows)
        assertEquals("every relic is counted once", 141, weight(placed))
        assertEquals("one row per relic id", placed.size, placed.map { it.relicId }.toSet().size)
        assertTrue(placed.all { RelicHouses.byId(it.relicId) != null })
        // Every tier fills each cell it has a relic for before anything folds.
        val perTier = rows.groupingBy { RelicHouses.tierOf(it.multiplier) }.eachCount()
        val expectedCells = RewardRarity.entries.sumOf { minOf(RelicHouses.ofTier(it).size, perTier[it] ?: 0) }
        assertEquals(expectedCells, placed.size)
    }

    @Test
    fun `a small vault keeps every relic on its own cell with its multiplier`() {
        val rows = listOf(
            RelicRow(1, null, "Fang of the Mark", 1.2, 10),
            RelicRow(2, null, "Sigil of the Margin", 1.3, 20),
            RelicRow(3, null, "Greater Crown of the Abyss", 1.5, 30),
            RelicRow(4, null, "Ember of the Abyss", 1.1, 40),
        )
        val placed = RelicHouses.place(rows)
        assertEquals(4, placed.size)
        assertEquals(4, placed.map { it.relicId }.toSet().size)
        assertEquals(listOf(1.1, 1.2, 1.3, 1.5), placed.map { it.multiplier }.sorted())
        placed.forEach { p ->
            val cell = RelicHouses.byId(p.relicId)!!
            assertEquals("tier follows the multiplier band", RelicHouses.tierOf(p.multiplier), cell.tier)
            assertEquals(0, p.refinements)
        }
        // The old house word steers the cell where the tier has one there: the Mark is Vigil, the
        // Margin Iron, the Abyss Return.
        fun houseOf(key: Long) = RelicHouses.byId(placed.first { it.key == key }.relicId)!!.house
        assertEquals(RelicHouse.Vigil, houseOf(1))
        assertEquals(RelicHouse.Iron, houseOf(2))
        assertEquals(RelicHouse.Return, houseOf(4))
    }

    @Test
    fun `the active relic stays the strongest and keeps its row`() {
        val rows = legacyRows().filterIndexed { i, _ -> i % 15 == 0 }
        val placed = RelicHouses.place(rows)
        val strongest = rows.maxByOrNull { it.multiplier }!!
        val active = RelicHouses.active(placed.map { OwnedRelic(it.relicId, it.multiplier, it.refinements, it.drawnAtMs) })!!
        assertEquals(strongest.multiplier, active.multiplier, 0.0)
        assertEquals(strongest.key, placed.first { it.relicId == active.relicId }.key)
    }

    @Test
    fun `an overflowing vault folds the extras into counted refinements`() {
        // 8 Common relics but only 6 Common cells.
        val rows = (0 until 8).map { RelicRow(it.toLong(), null, "Fang of the Mark", 1.06 + it * 0.01, it.toLong()) }
        val placed = RelicHouses.place(rows)
        assertEquals(6, placed.size)
        assertEquals("nothing lost", 8, weight(placed))
        assertEquals("the two folded relics are counted", 2, placed.sumOf { it.refinements })
        assertEquals("the strongest is never the one folded", 1.13, placed.maxOf { it.multiplier }, 1e-9)
    }

    @Test
    fun `a nameless relic and an out-of-band multiplier are placed by their multiplier`() {
        val placed = RelicHouses.place(
            listOf(
                RelicRow(1, null, "Nameless Relic", 1.4, 1),
                RelicRow(2, null, "Nameless Relic", 2.6, 2),
                RelicRow(3, null, "Nameless Relic", 1.02, 3),
            ),
        )
        assertEquals(3, placed.size)
        assertEquals(RewardRarity.Epic, RelicHouses.byId(placed.first { it.key == 1L }.relicId)!!.tier)
        assertEquals(RewardRarity.Masterwork, RelicHouses.byId(placed.first { it.key == 2L }.relicId)!!.tier)
        assertEquals(RewardRarity.Common, RelicHouses.byId(placed.first { it.key == 3L }.relicId)!!.tier)
    }

    @Test
    fun `rows that already carry an id keep it and a repeated id merges`() {
        val placed = RelicHouses.place(
            listOf(
                RelicRow(1, "iron.crown", "Crown of Iron", 1.4, 5, refinements = 1),
                RelicRow(2, "iron.crown", "Crown of Iron", 1.5, 6, refinements = 0),
                RelicRow(3, "iron.band", "Band of Iron", 1.1, 7),
            ),
        )
        assertEquals(2, placed.size)
        val crown = placed.first { it.relicId == "iron.crown" }
        assertEquals(1.5, crown.multiplier, 0.0)
        assertEquals("1 + 0 and 1 + 1 became one relic with 2 refinements", 2, crown.refinements)
        assertEquals(4, weight(placed))
    }

    @Test
    fun `an archive row with an unknown id is placed as a legacy relic`() {
        val placed = RelicHouses.place(listOf(RelicRow(1, "mystery.thing", "Fang of the Mark", 1.2, 1)))
        assertEquals(1, placed.size)
        assertNotNull(RelicHouses.byId(placed.single().relicId))
    }

    // ------------------------------------------------------------------ drawing

    @Test
    fun `draws follow the existing rarity odds and pick a cell of the rolled tier`() {
        val n = 40_000
        val seeds = Random(99)
        val draws = (0 until n).mapNotNull { Gacha.roll(seeds.nextLong(), ownedFrames = emptySet()).reward as? Reward.Relic }
        // Relic share of one draw per rarity: Common 0.198, Rare 0.15, Epic 0.045, Masterwork 0.006.
        val total = 0.198 + 0.15 + 0.045 + 0.006
        val expected = mapOf(
            RewardRarity.Common to 0.198 / total, RewardRarity.Rare to 0.15 / total,
            RewardRarity.Epic to 0.045 / total, RewardRarity.Masterwork to 0.006 / total,
        )
        val tiers = draws.groupingBy { RelicHouses.byId(it.relicId)!!.tier }.eachCount()
        expected.forEach { (tier, share) ->
            assertEquals("$tier", share, (tiers[tier] ?: 0).toDouble() / draws.size, 0.02)
        }
        draws.forEach {
            val cell = RelicHouses.byId(it.relicId)!!
            assertEquals(cell.name, it.name)
            assertTrue("${cell.id} ${it.multiplier}", it.multiplier in RelicHouses.band(cell.tier))
            assertEquals(RelicOutcome.New, it.outcome)
        }
    }

    @Test
    fun `a relic is never a duplicate while its tier has an open cell`() {
        val rng = Random(11)
        repeat(300) { lifter ->
            val owned = mutableMapOf<String, Double>()
            var pity = Gacha.Pity()
            repeat(40) {
                val result = Gacha.roll(rng.nextLong(), emptySet(), pity, owned)
                val reward = result.reward
                if (reward is Reward.Relic) {
                    val tierCells = RelicHouses.ofTier(RelicHouses.byId(reward.relicId)!!.tier)
                    if (reward.outcome != RelicOutcome.New) {
                        assertTrue("lifter $lifter duplicated while ${reward.relicId}'s tier was open", tierCells.all { it.id in owned })
                    }
                    owned[reward.relicId] = reward.multiplier
                }
                pity = pity.after(reward)
            }
        }
    }

    @Test
    fun `pacing still holds with houses - a first relic and one at least every 5 draws`() {
        val rng = Random(5)
        repeat(500) {
            val owned = mutableMapOf<String, Double>()
            var pity = Gacha.Pity()
            var sinceRelic = 0
            var relicDraws = 0
            repeat(14) { n ->
                val reward = Gacha.roll(rng.nextLong(), emptySet(), pity, owned).reward
                if (n == 0) assertTrue("the first inscription is a relic", reward is Reward.Relic)
                if (reward is Reward.Relic) {
                    sinceRelic = 0
                    relicDraws++
                    owned[reward.relicId] = reward.multiplier
                } else {
                    sinceRelic++
                }
                assertTrue("no relic in $sinceRelic draws", sinceRelic < Gacha.RELIC_PITY)
                pity = pity.after(reward)
            }
            assertTrue("14 inscriptions pay at least 3 relics, paid $relicDraws", relicDraws >= 3)
        }
    }

    @Test
    fun `a duplicate refines by a quarter of its band, capped at the tier, then pays echoes`() {
        // Hold every Common cell so the next Common relic must be a duplicate.
        val commons = RelicHouses.ofTier(RewardRarity.Common)
        val low = RelicHouses.band(RewardRarity.Common).start
        val high = RelicHouses.band(RewardRarity.Common).endInclusive
        val step = RelicHouses.refineStep(RewardRarity.Common)
        assertEquals((high - low) / 4, step, 1e-12)
        val owned = commons.associate { it.id to low }.toMutableMap()
        var refined = 0
        var maxed = 0
        val seeds = Random(3)
        repeat(600) {
            val r = Gacha.roll(seeds.nextLong(), relicStreak = Gacha.RELIC_PITY - 1, ownedRelics = owned).reward as Reward.Relic
            if (RelicHouses.byId(r.relicId)!!.tier != RewardRarity.Common) return@repeat
            val had = owned.getValue(r.relicId)
            when (r.outcome) {
                RelicOutcome.Refined -> {
                    refined++
                    assertEquals(minOf(high, had + step), r.multiplier, 1e-9)
                    assertTrue(r.multiplier > had && r.multiplier <= high + 1e-12)
                    assertEquals(0, r.echoes)
                }
                RelicOutcome.Maxed -> {
                    maxed++
                    assertEquals("a capped relic does not move", had, r.multiplier, 0.0)
                    assertEquals(Gacha.DROP_TABLE[0].figuresHigh, r.echoes)
                }
                RelicOutcome.New -> error("every Common cell was held")
            }
            owned[r.relicId] = r.multiplier
        }
        assertTrue("some refined", refined > 0)
        assertTrue("some capped", maxed > 0)
        assertTrue("never past the tier", owned.values.all { it <= high + 1e-12 })
    }

    // ------------------------------------------------------------------ the vault and the reveal

    @Test
    fun `the vault reports every cell, the active relic and set progress`() {
        val vault = RelicHouses.vault(
            owned("iron.band" to 1.1, "iron.crown" to 1.45, "vigil.seal" to 1.08, "craft.seal" to 1.12, "return.lantern" to 1.06),
        )
        assertEquals(16, vault.total)
        assertEquals(5, vault.ownedCount)
        val iron = vault.houses.first { it.house == RelicHouse.Iron }
        assertEquals(2, iron.owned)
        assertEquals(listOf("iron.band", "iron.crown", "iron.chain", "iron.plate"), iron.slots.map { it.relic.id })
        assertEquals(listOf(true, true, false, false), iron.slots.map { it.isOwned })
        assertEquals(RelicHouse.Iron.fullBonus, iron.next!!.summary)
        assertEquals(2, iron.toNext)
        assertEquals(listOf(RelicHouse.Iron.pairBonus), iron.reached.map { it.summary })
        assertEquals("iron.crown", vault.active!!.relic.id)
        assertEquals(1, vault.houses.flatMap { it.slots }.count { it.active })
        assertTrue(vault.houses.first { it.house == RelicHouse.Vigil }.reached.isEmpty())
    }

    @Test
    fun `the reveal names the relic, its tier, what it does and the set it joins`() {
        val vault = RelicHouses.vault(owned("iron.band" to 1.1, "iron.crown" to 1.45))
        val reveal = RelicHouses.reveal(Reward.Relic(1.45, "Crown of Iron", "iron.crown"), vault)!!
        assertEquals("Crown of Iron", reveal.name)
        assertEquals(RewardRarity.Epic, reveal.tier)
        assertEquals(RelicHouse.Iron, reveal.relic.house)
        assertEquals("Lifts the weight-moved part of your rate by 45%. It pays while you keep lifting.", reveal.effect)
        assertEquals("2 of 4 in House of Iron", reveal.setProgress)
        assertEquals("volume term +5%", reveal.bonusReached)
        assertEquals("Two more relics add 2 h of full strength", reveal.nextBonus)
        assertEquals("2 of 16", reveal.vaultProgress)
    }

    // ------------------------------------------------------------------ set bonus maths

    @Test
    fun `no relics is exactly the rate before houses`() {
        val r = rate(sessions = 3, volume = 200.0, skills = 5, streak = 2)
        val raw = 3 * 3.0 + 200 * 0.05 + 2 * 1.0
        assertEquals(1 + raw / 10, r.trainingFactor, 1e-12)
        assertEquals(1 + (1 - Math.exp(-0.045 * 5)), r.skillFactor, 1e-12)
        assertEquals(10 * r.trainingFactor * r.skillFactor, r.perHour, 1e-9)
    }

    @Test
    fun `a relic lifts only its own house's term`() {
        val iron = RelicHouses.effects(owned("iron.band" to 1.2))
        // Volume only: raw 10 -> training x2.0, Iron lifts all of it.
        assertEquals(1 + 10 * 1.2 / 10, rate(iron, volume = 200.0).trainingFactor, 1e-12)
        // Sessions only: Iron has no say.
        assertEquals(rate(sessions = 2).trainingFactor, rate(iron, sessions = 2).trainingFactor, 1e-12)
        val vigil = RelicHouses.effects(owned("vigil.key" to 1.3))
        assertEquals(1 + (2 * 3.0 + 1.0) * 1.3 / 10, rate(vigil, sessions = 2, streak = 1).trainingFactor, 1e-12)
        assertEquals("volume is Iron's", rate(volume = 200.0).trainingFactor, rate(vigil, volume = 200.0).trainingFactor, 1e-12)
        val craft = RelicHouses.effects(owned("craft.quill" to 1.3))
        val plain = rate(skills = 20).skillFactor
        assertEquals(1 + (plain - 1) * 1.3, rate(craft, skills = 20).skillFactor, 1e-12)
        assertEquals("no skills, nothing to lift", 1.0, rate(craft, skills = 0).skillFactor, 0.0)
        assertEquals("Return is not in the per-hour rate", rate(sessions = 2).perHour, rate(RelicHouses.effects(owned("return.gate" to 1.3)), sessions = 2).perHour, 1e-12)
    }

    @Test
    fun `two relics in a house add 5 percent to its term, a lone relic does not`() {
        val one = RelicHouses.effects(owned("iron.band" to 1.1))
        val two = RelicHouses.effects(owned("iron.band" to 1.1, "iron.chain" to 1.1))
        assertFalse(one.standings.first { it.house == RelicHouse.Iron }.pairReached)
        val stacked = Relics.effectiveMultiplier(listOf(1.1, 1.1))
        assertEquals(stacked * 1.05, two.term(RelicHouse.Iron), 1e-12)
        assertEquals(1.1, one.term(RelicHouse.Iron), 1e-12)
        assertEquals("other houses untouched", 1.0, two.term(RelicHouse.Vigil), 0.0)
        // Two relics in two different houses is not a pair.
        val split = RelicHouses.effects(owned("iron.band" to 1.1, "vigil.bell" to 1.1))
        assertEquals(1.1, split.term(RelicHouse.Iron), 1e-12)
    }

    @Test
    fun `the lift is applied after the training cap so a capped lifter still gains`() {
        val capped = rate(volume = 4_000.0, sessions = 5)
        assertEquals(1 + 30.0 / 10, capped.trainingFactor, 1e-12)
        val lifted = rate(RelicHouses.effects(owned("iron.band" to 1.2)), volume = 4_000.0, sessions = 5)
        assertTrue("${lifted.trainingFactor}", lifted.trainingFactor > capped.trainingFactor)
        // Volume is nearly all of the capped raw, so its share is lifted by 20%.
        val raw = 4_000 * 0.05 + 5 * 3.0
        val keep = 30.0 / raw
        assertEquals(1 + (4_000 * 0.05 * 1.2 + 5 * 3.0) * keep / 10, lifted.trainingFactor, 1e-12)
    }

    private fun full(house: RelicHouse): HouseEffects {
        val ids = RelicHouses.CATALOGUE.filter { it.house == house }.map { OwnedRelic(it.id, 1.0 + 1e-9) }
        return RelicHouses.effects(ids)
    }

    @Test
    fun `Iron with 4 relics adds 2 h to the full-strength window`() {
        val iron = full(RelicHouse.Iron)
        assertEquals(26.0, iron.fullStrengthHours, 0.0)
        assertEquals(24.0, HouseEffects.NONE.fullStrengthHours, 0.0)
        val r = rate(sessions = 2)
        val plain = Idle.accruedExact(state(), r, base + 26 * hour)
        val rated = r.copy(effects = iron)
        // 26 h at full strength is 26 effective hours; without the set it is 24 + 2 * the first slice of the taper.
        assertEquals(r.perHour * 26, Idle.accruedExact(state(), rated, base + 26 * hour), 1e-6)
        assertTrue(Idle.accruedExact(state(), rated, base + 26 * hour) > plain)
        assertEquals(1.0, Idle.efficiencyAtHours(25.5, iron), 0.0)
        assertTrue(Idle.efficiencyAtHours(25.5) < 1.0)
        assertEquals(0.0, Idle.fullStrengthFraction(0L, iron), 0.0)
        assertEquals(0.5, Idle.fullStrengthFraction(13 * hour, iron), 1e-12)
    }

    @Test
    fun `Vigil with 4 relics pays 4 days for one absence instead of 3`() {
        val vigil = full(RelicHouse.Vigil)
        assertEquals(96.0, vigil.maxEffectiveHours, 0.0)
        assertEquals(72.0, HouseEffects.NONE.maxEffectiveHours, 0.0)
        val r = rate(sessions = 2)
        val year = base + 365 * 24 * hour
        assertEquals(r.perHour * 72, Idle.accruedExact(state(), r, year), 1e-6)
        assertEquals(r.perHour * 96, Idle.accruedExact(state(), r.copy(effects = vigil), year), 1e-6)
    }

    @Test
    fun `Craft with 4 relics raises the technique ceiling from 2_0 to 2_1`() {
        val craft = full(RelicHouse.Craft)
        val many = 1_000_000
        assertEquals(2.0, rate(skills = many).skillFactor, 1e-9)
        // The curve's ceiling is 2.1; the house's own lift (here the 2-relic +5%) sits on top of it.
        assertEquals(1.0 + 1.1 * craft.term(RelicHouse.Craft), rate(craft, skills = many).skillFactor, 1e-6)
        assertEquals(1.1, craft.skillCeiling, 1e-12)
        assertEquals("below the ceiling the curve is the same shape", 1.0, rate(craft, skills = 0).skillFactor, 0.0)
    }

    @Test
    fun `Return with 4 relics lifts the taper floor from 10 to 15 percent`() {
        val ret = full(RelicHouse.Return)
        assertEquals(0.10, Idle.efficiencyAtHours(500.0), 1e-12)
        assertEquals(0.15, Idle.efficiencyAtHours(500.0, ret), 1e-12)
        assertEquals(1.0, Idle.efficiencyAtHours(24.0, ret), 0.0)
        // Halfway down the taper: 1 - 0.85 * 0.5.
        assertEquals(1.0 - 0.85 * 0.5, Idle.efficiencyAtHours(48.0, ret), 1e-12)
        val r = rate(sessions = 2)
        assertTrue(Idle.accruedExact(state(), r.copy(effects = ret), base + 100 * hour) > Idle.accruedExact(state(), r, base + 100 * hour))
    }

    @Test
    fun `the comeback term lifts only the time paid beyond the full-strength day`() {
        val houses = RelicHouses.effects(owned("return.gate" to 1.3, "return.door" to 1.2))
        val term = houses.term(RelicHouse.Return)
        val r = rate(sessions = 2)
        val lifted = r.copy(effects = houses)
        assertEquals("inside the full day nothing changes", Idle.accruedExact(state(), r, base + 20 * hour), Idle.accruedExact(state(), lifted, base + 20 * hour), 1e-9)
        // 48 h away is 24 + 18.6 effective hours; Return lifts the 18.6.
        assertEquals(r.perHour * (24 + 18.6 * term), Idle.accruedExact(state(), lifted, base + 48 * hour), 1e-6)
        // The cap still holds, whatever the lift.
        assertTrue(Idle.accruedExact(state(), lifted, base + 10_000 * hour) <= r.perHour * 72 + 1e-6)
    }
}
