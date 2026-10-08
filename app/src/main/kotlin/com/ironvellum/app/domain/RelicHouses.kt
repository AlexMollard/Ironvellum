package com.ironvellum.app.domain

import kotlin.math.roundToInt

/**
 * The four relic houses. Each follows one habit. A relic of any house lifts the WHOLE essence rate
 * ([Relics.effectiveMultiplier]); the house only adds set bonuses, which pay while the lifter keeps
 * the habit (see [Idle.rate]). Nothing here touches XP, levels, strength or titles.
 *
 * Ids are stable: a relic is `house.form` (for example `iron.band`), its name and its art seed
 * follow from it, and neither ever changes once released. Add relics at the END of a house, never
 * reorder, and bump nothing else.
 */
enum class RelicHouse(
    val id: String,
    val label: String,
    val habit: String,
    /** What the house follows, as a clause: "Follows the weight you move." */
    val follows: String,
    /** The part of the rate the house lifts, as the vault and the reveal name it. */
    val term: String,
    /** The 2-relic bonus, after "2 relics: ". */
    val pairBonus: String,
    /** The 4-relic bonus, after "4 relics: ". */
    val fullBonus: String,
    /** What the 4-relic bonus adds, for "Two more relics add ...". */
    val fullGain: String,
) {
    Iron(
        "iron", "Iron", "Lifting", "the trials you seal and the weight you move", "lifting",
        "lifting term +5%", "full-strength window +2 h", "2 h of full strength",
    ),
    Vigil(
        "vigil", "Vigil", "Consistency", "how steadily you train", "consistency",
        "consistency term +5%", "one absence pays 4 days, not 3", "a fourth day of absence pay",
    ),
    Craft(
        "craft", "Craft", "Technique", "the techniques you claim", "technique",
        "technique term +5%", "technique ceiling ×2.0 to ×2.1", "a higher technique ceiling",
    ),
    Return(
        "return", "Return", "Coming back", "how soon you return after a gap", "comeback",
        "comeback term +5%", "taper floor 10% to 15%", "a higher taper floor",
    );

    val title: String get() = "House of $label"

    /** What a relic does at [multiplier], in the app's voice: the same for every house. */
    fun effect(multiplier: Double): String =
        "Lifts your whole essence rate by ${((multiplier - 1.0) * 100).roundToInt()}%, stacking with your other relics."

    companion object {
        fun byId(id: String?): RelicHouse? = entries.firstOrNull { it.id == id }
    }
}

/** One relic of the catalogue: house + form is its id, [tier] is fixed for ever. */
data class HouseRelic(
    val house: RelicHouse,
    /** Stable form id inside the house: "band". */
    val form: String,
    val name: String,
    val tier: RewardRarity,
) {
    val id: String get() = "${house.id}.$form"
}

/** What an inscription did with a drawn relic. */
enum class RelicOutcome {
    /** A relic the vault did not hold: it joins it. */
    New,

    /** A duplicate: the relic's multiplier rose, capped by its tier. */
    Refined,

    /** A duplicate of a relic already at its tier's cap: it pays echoes instead. */
    Maxed,
}

/** A relic the lifter holds, as the rules see it. */
data class OwnedRelic(
    val relicId: String,
    val multiplier: Double,
    val refinements: Int = 0,
    val drawnAtMs: Long = 0L,
)

/** One house's standing: how many it holds and what that lifts. */
data class HouseStanding(
    val house: RelicHouse,
    val owned: Int,
) {
    val pairReached: Boolean get() = owned >= RelicHouses.PAIR
    val fullReached: Boolean get() = owned >= RelicHouses.FULL

    /** What the house's term of the rate is multiplied by: only the 2-relic bonus. The relics themselves lift the whole rate. */
    val termMultiplier: Double get() = if (pairReached) 1.0 + RelicHouses.PAIR_BONUS else 1.0
}

/**
 * Everything the houses change in the rate, in one value so [Idle.rate] and [Idle.accrued] can never
 * disagree. [NONE] is a lifter with no relics, which is exactly the rate before houses existed.
 */
data class HouseEffects(val standings: List<HouseStanding>) {
    private fun standing(house: RelicHouse) = standings.firstOrNull { it.house == house }

    /** The multiplier on [house]'s term of the rate. */
    fun term(house: RelicHouse): Double = standing(house)?.termMultiplier ?: 1.0

    private fun full(house: RelicHouse) = standing(house)?.fullReached == true

    /** Iron, 4 relics: the full-strength window gains [RelicHouses.IRON_WINDOW_HOURS]. */
    val fullStrengthHours: Double
        get() = Idle.FULL_RATE_HOURS + if (full(RelicHouse.Iron)) RelicHouses.IRON_WINDOW_HOURS else 0.0

    /** Vigil, 4 relics: one absence pays 4 days instead of 3. */
    val maxEffectiveHours: Double
        get() = Idle.MAX_EFFECTIVE_HOURS + if (full(RelicHouse.Vigil)) RelicHouses.VIGIL_EXTRA_HOURS else 0.0

    /** Craft, 4 relics: the technique ceiling goes from x2.0 to x2.1 (this is the added part, 1.0 to 1.1). */
    val skillCeiling: Double
        get() = Idle.SKILL_CEILING + if (full(RelicHouse.Craft)) RelicHouses.CRAFT_CEILING else 0.0

    /** Return, 4 relics: the taper floor goes from 10% to 15%. */
    val minEfficiency: Double
        get() = Idle.MIN_EFFICIENCY + if (full(RelicHouse.Return)) RelicHouses.RETURN_FLOOR else 0.0

    companion object {
        val NONE = HouseEffects(emptyList())
    }
}

/** A set bonus of one house, for the vault and the reveal. */
data class SetBonus(val needed: Int, val summary: String, val reached: Boolean)

/** One cell of the vault grid. [owned] is null for a relic not yet drawn. */
data class VaultSlot(val relic: HouseRelic, val owned: OwnedRelic?, val active: Boolean) {
    val isOwned: Boolean get() = owned != null
    val tier: RewardRarity get() = relic.tier
}

/** One house of the vault: its four cells, its progress and the bonuses it has reached. */
data class HouseProgress(
    val house: RelicHouse,
    val slots: List<VaultSlot>,
    val bonuses: List<SetBonus>,
) {
    val owned: Int get() = slots.count { it.isOwned }
    val size: Int get() = slots.size
    val reached: List<SetBonus> get() = bonuses.filter { it.reached }

    /** The next bonus not yet reached, or null once the house is complete. */
    val next: SetBonus? get() = bonuses.firstOrNull { !it.reached }
    val toNext: Int get() = next?.let { it.needed - owned } ?: 0
}

/** The whole vault: the grid state the UI draws. */
data class VaultState(
    val houses: List<HouseProgress>,
    val effects: HouseEffects,
) {
    val ownedCount: Int get() = houses.sumOf { it.owned }
    val total: Int get() = houses.sumOf { it.size }

    /** The relic that sets the headline relic rate: the strongest held, or null with none. */
    val active: VaultSlot? get() = houses.flatMap { it.slots }.firstOrNull { it.active }
}

/** The relic just drawn, with everything its reveal shows. */
data class RelicReveal(
    val relic: HouseRelic,
    val outcome: RelicOutcome,
    val multiplier: Double,
    /** What it does: "Lifts the weight-moved part of your rate by 45%. ..." */
    val effect: String,
    /** "2 of 4 in House of Iron". */
    val setProgress: String,
    /** The highest bonus the house has reached, as "volume term +5%", or null. */
    val bonusReached: String?,
    /** "Two more relics add 2 h of full strength", or null once the house is complete. */
    val nextBonus: String?,
    /** Echoes a maxed duplicate paid. */
    val echoes: Int,
    /** "6 of 16" for the vault line. */
    val vaultProgress: String,
) {
    val name: String get() = relic.name
    val tier: RewardRarity get() = relic.tier
}

/** A stored relic before it has a house: what the legacy mapping and a restore read. */
data class RelicRow(
    val key: Long,
    /** Null for a relic from before houses, which is placed by its old name and multiplier. */
    val relicId: String?,
    val name: String,
    val multiplier: Double,
    val drawnAtMs: Long,
    val refinements: Int = 0,
)

/** A relic placed in a house. [key] is the stored row it keeps (the strongest of any merged). */
data class PlacedRelic(
    val key: Long,
    val relicId: String,
    val multiplier: Double,
    val drawnAtMs: Long,
    val refinements: Int,
)

object RelicHouses {

    /** Relics of a house for the first set bonus, and for the second. */
    const val PAIR = 2
    const val FULL = 4

    /** The 2-relic bonus: +5% on the house's term. */
    const val PAIR_BONUS = 0.05

    const val IRON_WINDOW_HOURS = 2.0
    const val VIGIL_EXTRA_HOURS = 24.0
    const val CRAFT_CEILING = 0.1
    const val RETURN_FLOOR = 0.05

    /**
     * The 16 relics, 4 to a house, in their frozen order. Tiers are fixed. The layout is
     * 6 Common, 6 Rare, 3 Fabled and 1 Masterwork so that three houses can complete on the
     * relics the odds actually give (a simulation of the draw odds puts them at about 15 to 25
     * relics each, a first 2-relic bonus within 4); the single Masterwork sits in Craft,
     * which makes that house's full set the long chase.
     */
    val CATALOGUE: List<HouseRelic> = listOf(
        HouseRelic(RelicHouse.Iron, "band", "Band of Iron", RewardRarity.Common),
        HouseRelic(RelicHouse.Iron, "crown", "Crown of Iron", RewardRarity.Epic),
        HouseRelic(RelicHouse.Iron, "chain", "Chain of Iron", RewardRarity.Rare),
        HouseRelic(RelicHouse.Iron, "plate", "Plate of Iron", RewardRarity.Rare),

        HouseRelic(RelicHouse.Vigil, "bell", "Bell of Vigil", RewardRarity.Common),
        HouseRelic(RelicHouse.Vigil, "seal", "Seal of Vigil", RewardRarity.Common),
        HouseRelic(RelicHouse.Vigil, "key", "Key of Vigil", RewardRarity.Rare),
        HouseRelic(RelicHouse.Vigil, "hourglass", "Hourglass of Vigil", RewardRarity.Epic),

        HouseRelic(RelicHouse.Craft, "quill", "Quill of Craft", RewardRarity.Rare),
        HouseRelic(RelicHouse.Craft, "compass", "Compass of Craft", RewardRarity.Epic),
        HouseRelic(RelicHouse.Craft, "seal", "Seal of Craft", RewardRarity.Common),
        HouseRelic(RelicHouse.Craft, "chisel", "Chisel of Craft", RewardRarity.Masterwork),

        HouseRelic(RelicHouse.Return, "lantern", "Lantern of Return", RewardRarity.Common),
        HouseRelic(RelicHouse.Return, "thread", "Thread of Return", RewardRarity.Common),
        HouseRelic(RelicHouse.Return, "gate", "Gate of Return", RewardRarity.Rare),
        HouseRelic(RelicHouse.Return, "door", "Door of Return", RewardRarity.Rare),
    )

    private val BY_ID: Map<String, HouseRelic> = CATALOGUE.associateBy { it.id }

    fun byId(id: String?): HouseRelic? = BY_ID[id]

    fun ofTier(tier: RewardRarity): List<HouseRelic> = CATALOGUE.filter { it.tier == tier }

    /** The odds row (band, figures) of a tier. */
    private fun odds(tier: RewardRarity) = Gacha.DROP_TABLE.first { it.rarity == tier }

    /** The multiplier band of a tier: (low, high]. */
    fun band(tier: RewardRarity): ClosedFloatingPointRange<Double> = odds(tier).let { it.relicLow..it.relicHigh }

    /**
     * What one duplicate adds: a quarter of the tier's band, so four duplicates carry a relic from
     * the bottom of its band to the top.
     */
    fun refineStep(tier: RewardRarity): Double = odds(tier).let { (it.relicHigh - it.relicLow) / 4.0 }

    /** The tier a stored multiplier belongs to, by the bands the roller has always used. */
    fun tierOf(multiplier: Double): RewardRarity =
        Gacha.DROP_TABLE.lastOrNull { it.relicChance > 0.0 && multiplier >= it.relicLow }?.rarity ?: RewardRarity.Common

    // ------------------------------------------------------------------ effects and vault

    fun effects(owned: List<OwnedRelic>): HouseEffects = HouseEffects(
        RelicHouse.entries.map { house ->
            val held = owned.filter { BY_ID[it.relicId]?.house == house }
            HouseStanding(house, held.size)
        },
    )

    /** The strongest held relic (ties go to the newer): the one that sets the relic rate. */
    fun active(owned: List<OwnedRelic>): OwnedRelic? =
        owned.filter { BY_ID.containsKey(it.relicId) }
            .maxWithOrNull(compareBy<OwnedRelic> { it.multiplier }.thenBy { it.drawnAtMs })

    fun vault(owned: List<OwnedRelic>): VaultState {
        val held = owned.filter { BY_ID.containsKey(it.relicId) }.associateBy { it.relicId }
        val top = active(owned)?.relicId
        val effects = effects(owned)
        val houses = RelicHouse.entries.map { house ->
            val slots = CATALOGUE.filter { it.house == house }.map { VaultSlot(it, held[it.id], it.id == top) }
            val count = slots.count { it.isOwned }
            HouseProgress(
                house,
                slots,
                listOf(
                    SetBonus(PAIR, house.pairBonus, count >= PAIR),
                    SetBonus(FULL, house.fullBonus, count >= FULL),
                ),
            )
        }
        return VaultState(houses, effects)
    }

    /** The reveal of [reward] against the vault AFTER it was paid. */
    fun reveal(reward: Reward.Relic, vault: VaultState): RelicReveal? {
        val relic = byId(reward.relicId) ?: return null
        val house = vault.houses.first { it.house == relic.house }
        val toGo = house.toNext
        return RelicReveal(
            relic = relic,
            outcome = reward.outcome,
            multiplier = reward.multiplier,
            effect = relic.house.effect(reward.multiplier),
            setProgress = "${house.owned} of ${house.size} in ${relic.house.title}",
            bonusReached = house.reached.lastOrNull()?.summary,
            nextBonus = house.next?.let {
                "${countWord(toGo)} more ${if (toGo == 1) "relic adds" else "relics add"} " +
                    (if (it.needed == FULL) relic.house.fullGain else relic.house.term + " term +5%")
            },
            echoes = reward.echoes,
            vaultProgress = "${vault.ownedCount} of ${vault.total}",
        )
    }

    private fun countWord(n: Int) = when (n) {
        1 -> "One"
        2 -> "Two"
        3 -> "Three"
        4 -> "Four"
        else -> n.toString()
    }

    // ------------------------------------------------------------------ placing stored relics

    /** The legacy house words and the house each one now belongs to. */
    private val LEGACY_HOUSE: Map<String, RelicHouse> = mapOf(
        "the Mark" to RelicHouse.Vigil,
        "the Vigil" to RelicHouse.Vigil,
        "the Margin" to RelicHouse.Iron,
        "the Ashen King" to RelicHouse.Iron,
        "the Ledger" to RelicHouse.Craft,
        "the Abyss" to RelicHouse.Return,
    )

    /** The eight legacy forms, only to spread a vault over a house's cells deterministically. */
    private val LEGACY_FORMS = listOf("Fang", "Sigil", "Shard", "Crown", "Chain", "Mirror", "Ember", "Thorn")

    private fun legacyHouse(name: String): RelicHouse? =
        if (" of " in name) LEGACY_HOUSE[name.substringAfterLast(" of ")] else null

    private fun legacyFormIndex(name: String): Int {
        val form = name.removePrefix("Greater ").removePrefix("Masterwork ").substringBefore(" of ")
        return LEGACY_FORMS.indexOf(form).coerceAtLeast(0)
    }

    private fun absorb(held: PlacedRelic, row: RelicRow): PlacedRelic = held.copy(
        multiplier = maxOf(held.multiplier, row.multiplier),
        refinements = held.refinements + 1 + row.refinements,
    )

    /**
     * Gives every stored relic a house and a form. The one rule behind the 36 to 37 migration and
     * the restore of an archive written before houses:
     *
     * - Rows that already carry a known relic id keep it; two rows with one id merge.
     * - A legacy row is placed by its multiplier (its tier is the band it sits in) on the free cell
     *   of that tier nearest its old house word and form, so its multiplier is kept as it is.
     * - Rows are taken strongest first, so the active relic is always placed and stays on top.
     * - A relic NEVER disappears and the rate NEVER drops. When a tier has no free cell the row
     *   takes the nearest free cell of an adjacent tier (one tier down, then one up, then two down,
     *   and so on) and keeps its exact multiplier: a migrated relic is not held to its new tier's
     *   cap, and a later duplicate cannot lower it (a refine never moves a relic down).
     * - Only when all 16 cells are taken does a row fold into the cell it wanted: the stronger
     *   multiplier stays and the other becomes counted refinements, so the sum of 1 + refinements
     *   over the result always equals the same sum over the input.
     * - Folding drops a relic from the stacked rate, so the result is then checked against
     *   [Relics.effectiveMultiplier] over the input. If it fell short, the strongest placed relic is
     *   raised by the least amount that makes the stack equal the old one (the folded relics'
     *   strength moves into it). A vault of 16 or fewer relics never needs this.
     *
     * Deterministic and independent of the order of [rows].
     */
    fun place(rows: List<RelicRow>): List<PlacedRelic> {
        val ordered = rows.sortedWith(
            compareByDescending<RelicRow> { it.multiplier }.thenByDescending { it.drawnAtMs }.thenBy { it.key },
        )
        val placed = LinkedHashMap<String, PlacedRelic>()
        fun put(id: String, row: RelicRow) {
            val held = placed[id]
            placed[id] = if (held == null) {
                PlacedRelic(row.key, id, row.multiplier, row.drawnAtMs, row.refinements)
            } else {
                absorb(held, row)
            }
        }
        val legacyMultipliers = mutableListOf<Double>()
        val (known, legacy) = ordered.partition { BY_ID.containsKey(it.relicId) }
        // A relic is never stronger than its tier's cap, so a hand-edited archive cannot restore a Common at x2.4.
        known.forEach { put(it.relicId!!, it.copy(multiplier = minOf(it.multiplier, band(BY_ID.getValue(it.relicId!!).tier).endInclusive))) }
        val knownHeld = placed.values.map { it.multiplier }
        legacy.forEach { row ->
            val sane = row.copy(multiplier = if (row.multiplier.isFinite() && row.multiplier > 1.0) row.multiplier else 1.0 + 1e-6)
            val tier = tierOf(sane.multiplier)
            val house = legacyHouse(sane.name)
            val form = legacyFormIndex(sane.name)
            val wanted = ofTier(tier).sortedWith(
                compareBy<HouseRelic> { if (it.house == house) 0 else 1 }
                    .thenBy { (CATALOGUE.indexOf(it) + CATALOGUE.size - form) % CATALOGUE.size },
            )
            val target = nearestFree(tier, house, form, placed.keys) ?: wanted.first()
            put(target.id, sane)
            legacyMultipliers += sane.multiplier
        }
        return keepRate(placed.values.toList(), knownHeld + legacyMultipliers)
    }

    /** The free cell nearest [tier]: the tier itself, then one down, one up, two down, and so on. */
    private fun nearestFree(tier: RewardRarity, house: RelicHouse?, form: Int, taken: Set<String>): HouseRelic? {
        val tiers = RewardRarity.entries
        for (d in 0 until tiers.size) {
            for (t in listOf(tier.ordinal - d, tier.ordinal + d).distinct()) {
                val cell = tiers.getOrNull(t)?.let { candidate ->
                    ofTier(candidate).filter { it.id !in taken }.minWithOrNull(
                        compareBy<HouseRelic> { if (it.house == house) 0 else 1 }
                            .thenBy { (CATALOGUE.indexOf(it) + CATALOGUE.size - form) % CATALOGUE.size },
                    )
                }
                if (cell != null) return cell
            }
        }
        return null
    }

    /**
     * Raises the strongest placed relic, by the least amount, until the stack is at least what the
     * stored multipliers stacked to. [Relics.effectiveMultiplier] only grows with its strongest
     * relic, so a bisection finds it.
     */
    private fun keepRate(placed: List<PlacedRelic>, held: List<Double>): List<PlacedRelic> {
        val before = placed.map { it.multiplier }
        val wanted = Relics.effectiveMultiplier(held)
        if (Relics.effectiveMultiplier(before) >= wanted) return placed
        val top = placed.indices.maxByOrNull { placed[it].multiplier } ?: return placed
        fun stack(m: Double) = Relics.effectiveMultiplier(before.mapIndexed { i, v -> if (i == top) m else v })
        var lo = before[top]
        var hi = lo + 1.0
        while (stack(hi) < wanted) hi += hi - lo
        repeat(60) { val mid = (lo + hi) / 2; if (stack(mid) < wanted) lo = mid else hi = mid }
        return placed.mapIndexed { i, p -> if (i == top) p.copy(multiplier = hi) else p }
    }
}
