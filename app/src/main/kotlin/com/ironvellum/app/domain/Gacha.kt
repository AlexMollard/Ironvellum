package com.ironvellum.app.domain

import java.util.Random

/**
 * Level-up gacha. Pure Kotlin so JVM tests can pin the drop table exactly.
 *
 * Rewards are idle-game value (figures, relic multiplier) and cosmetics (crest
 * frames) ONLY — never XP, strength, levels, or titles, so the leaderboard
 * still measures training, not luck.
 *
 * Drop table (a single uniform draw in [0, 100)):
 *   Common    60%  -> figures 20..40            | (no relic, no frame)
 *   Rare      30%  -> 60% figures 60..120, 30% relic x1.15..1.35, 10% frame
 *   Epic       9%  -> 40% figures 200..400, 40% relic x1.35..1.75, 20% frame
 *   Masterwork 1%  -> 30% figures 800..1500, 50% relic x1.75..2.50, 20% frame
 *
 * Relic band: (1.0, 2.5] — strictly above 1.0 (never a no-op) and small enough
 * that the idle rate stays sane. Payout ranges never overlap across rarities,
 * so a Masterwork always pays at least as much as the best Common of its type.
 */
enum class RewardRarity { Common, Rare, Epic, Masterwork }

sealed interface Reward {
    data class Figures(val count: Int) : Reward
    /**
     * A relic of the house catalogue ([RelicHouses]). [relicId] is `house.form`, [name] follows from
     * it, [multiplier] is the relic's strength AFTER this draw. [outcome] says whether it joined the
     * vault, refined a duplicate, or (at its tier's cap) paid [echoes] instead.
     */
    data class Relic(
        val multiplier: Double,
        val name: String,
        val relicId: String = "",
        val outcome: RelicOutcome = RelicOutcome.New,
        val echoes: Int = 0,
    ) : Reward
    data class CrestFrame(val id: String, val name: String) : Reward
}

data class RollResult(val reward: Reward, val rarity: RewardRarity)

object Gacha {

    /** The full cosmetic catalogue; frames drop only from Rare and above. */
    val CREST_FRAMES: List<Reward.CrestFrame> = listOf(
        Reward.CrestFrame("iron", "Iron Crest"),
        Reward.CrestFrame("bronze", "Bronze Crest"),
        Reward.CrestFrame("silver", "Silver Crest"),
        Reward.CrestFrame("gold", "Gold Crest"),
        Reward.CrestFrame("jade", "Jade Crest"),
        Reward.CrestFrame("crimson", "Crimson Crest"),
        Reward.CrestFrame("obsidian", "Obsidian Crest"),
        Reward.CrestFrame("aurora", "Aurora Crest"),
        Reward.CrestFrame("void", "Void Crest"),
        Reward.CrestFrame("masterwork", "Masterwork Crest"),
    )

    /**
     * The drop table, declared once. The roller AND the on-screen odds panel
     * both read this, so what a lifter is told can never drift from what the
     * roller actually does.
     *
     * [chance] is the rarity's share of a draw; [figureChance] and
     * [relicChance] are the split WITHIN that rarity, and whatever is left is
     * a crest frame.
     */
    data class Odds(
        val rarity: RewardRarity,
        val chance: Double,
        val figureChance: Double,
        val figuresLow: Int,
        val figuresHigh: Int,
        val relicChance: Double,
        val relicLow: Double,
        val relicHigh: Double,
    ) {
        val frameChance: Double get() = (1.0 - figureChance - relicChance).coerceAtLeast(0.0)
    }

    /**
     * Every rarity can now pay every reward type. Common is 60% of all draws
     * and used to sit at figureChance 1.00, so the majority of draws were
     * STRUCTURALLY incapable of paying a relic or a frame: a lifter ten
     * rank-ups in had a 1-in-7 chance of never having seen either, which is
     * exactly what happened. Overall the table moves figures 81.9% -> 66.4%
     * and frames 5.0% -> 10.0%.
     *
     * Pacing then moved figures to 50.0% and relics to 39.9% (frames stay at
     * 10.0%), so that with the first-relic guarantee and [RELIC_PITY] a lifter
     * holds about 6.7 relics at level 15 and never fewer than 3. Only the
     * splits changed: the relic bands, and with them every relic's name, did not.
     */
    val DROP_TABLE = listOf(
        Odds(RewardRarity.Common, 0.60, 0.62, 20, 40, 0.33, 1.05, 1.15),
        Odds(RewardRarity.Rare, 0.30, 0.35, 60, 120, 0.50, 1.15, 1.35),
        Odds(RewardRarity.Epic, 0.09, 0.25, 200, 400, 0.50, 1.35, 1.75),
        Odds(RewardRarity.Masterwork, 0.01, 0.15, 800, 1500, 0.60, 1.75, 2.50),
    )

    /**
     * Figure draws in a row before the next one is FORCED to pay a relic or a
     * frame. Odds alone can still hand out a long grey streak - the run that
     * prompted this was ten - and a streak of the least interesting reward is
     * what makes a reward system feel broken rather than unlucky.
     */
    const val PITY_AFTER = 3

    /**
     * A relic at least every this many draws: after [RELIC_PITY] - 1 draws
     * without one, the next is forced to be a relic. Separate from
     * [PITY_AFTER], which only guarantees "a relic or a crest".
     */
    const val RELIC_PITY = 5

    /**
     * Everything pity needs to know before a draw. [draws] is how many
     * inscriptions have been spent, [hasRelic] whether the vault holds any:
     * the very first inscription of a lifter with an empty vault is a relic.
     */
    data class Pity(
        val figureStreak: Int = 0,
        val relicStreak: Int = 0,
        val draws: Int = 0,
        val hasRelic: Boolean = false,
    ) {
        val guaranteeFirstRelic: Boolean get() = draws == 0 && !hasRelic

        /** The state once [reward] has been paid. */
        fun after(reward: Reward) = Pity(
            figureStreak = if (reward is Reward.Figures) figureStreak + 1 else 0,
            relicStreak = if (reward is Reward.Relic) 0 else relicStreak + 1,
            draws = draws + 1,
            hasRelic = hasRelic || reward is Reward.Relic,
        )
    }

    /** What one inscription can pay, as shares of every draw: echoes, a relic, a crest. They sum to 1. */
    data class TypeShares(val echoes: Double, val relic: Double, val crest: Double)

    /** [TypeShares] read straight off [DROP_TABLE], so the buy sheet can never tell a lifter a different story from the roller. */
    fun typeShares(): TypeShares = TypeShares(
        echoes = DROP_TABLE.sumOf { it.chance * it.figureChance },
        relic = DROP_TABLE.sumOf { it.chance * it.relicChance },
        crest = DROP_TABLE.sumOf { it.chance * it.frameChance },
    )

    /**
     * What the pity rules say about the NEXT draw, in the app's voice: the first inscription is a relic,
     * a run of [PITY_AFTER] echo draws forces a relic or a crest, and [RELIC_PITY] draws without a relic
     * force one; otherwise how the first rule counts.
     */
    fun pityLine(pity: Pity): String = when {
        pity.guaranteeFirstRelic -> "Your first inscription is a relic."
        pity.figureStreak >= PITY_AFTER ->
            "Your last ${pity.figureStreak} draws were echoes, so this one is a relic or a crest."
        pity.relicStreak >= RELIC_PITY - 1 ->
            "It has been ${pity.relicStreak} draws since your last relic, so this one is a relic."
        else -> "After $PITY_AFTER echo draws in a row, the next one is a relic or a crest."
    }

    /** [roll] driven by a [Pity] state, the one entry point the repository and the simulations share. */
    fun roll(
        seed: Long,
        ownedFrames: Set<String>,
        pity: Pity,
        ownedRelics: Map<String, Double> = emptyMap(),
    ): RollResult = roll(
        seed = seed,
        ownedFrames = ownedFrames,
        ownedRelics = ownedRelics,
        figureStreak = pity.figureStreak,
        relicStreak = pity.relicStreak,
        guaranteeRelic = pity.guaranteeFirstRelic,
    )

    /**
     * Deterministic for a given seed — same seed, same result, always.
     *
     * [figureStreak] is how many figure-only draws came immediately before
     * this one. At [PITY_AFTER] the figure branch is closed off and the draw
     * pays a relic or a frame, split by this rarity's own odds between the
     * two, so pity never changes WHICH of the two is likelier — only that one
     * of them lands.
     *
     * [relicStreak] is how many draws since the last relic: at
     * [RELIC_PITY] - 1 the draw is a relic. [guaranteeRelic] forces one too,
     * from at least the Rare band so a first relic is never the weakest.
     *
     * [ownedRelics] maps each held relic id to its multiplier. The rolled rarity picks the tier of
     * the relic and the house and form are drawn from that tier's cells the lifter does not hold
     * yet (as a crest frame is drawn from the unowned ones). Only when the whole tier is held is
     * the draw a duplicate, which refines the relic (see [relicDraw]).
     */
    fun roll(
        seed: Long,
        ownedFrames: Set<String> = emptySet(),
        figureStreak: Int = 0,
        relicStreak: Int = 0,
        guaranteeRelic: Boolean = false,
        ownedRelics: Map<String, Double> = emptyMap(),
    ): RollResult {
        val rng = Random(seed)
        val rarityRoll = rng.nextDouble()
        // Walk the cumulative rarity shares; the last row absorbs any residue
        // so a rounding gap can never fall through to no reward at all.
        var cumulative = 0.0
        val rolled = DROP_TABLE.firstOrNull { row ->
            cumulative += row.chance
            rarityRoll < cumulative
        } ?: DROP_TABLE.last()
        // A guaranteed first relic is at least Rare: Common's band is x1.05-1.15.
        val odds = if (guaranteeRelic && rolled.rarity == RewardRarity.Common) DROP_TABLE[1] else rolled
        val typeRoll = rng.nextDouble()
        val valueRoll = rng.nextDouble()
        val forcedRelic = guaranteeRelic || relicStreak >= RELIC_PITY - 1
        val forced = figureStreak >= PITY_AFTER
        fun frameOrFigures(): Reward {
            // Owned frames are excluded, or the roll is consumed for
            // nothing when the repository dedupes the duplicate.
            val candidates = CREST_FRAMES.filter { it.id !in ownedFrames }
            return if (candidates.isEmpty()) {
                // All frames owned: fall back to the next-best payout —
                // the top of this rarity's figure band.
                Reward.Figures(odds.figuresHigh)
            } else {
                candidates[rng.nextInt(candidates.size)]
            }
        }
        val reward = when {
            forcedRelic -> relicDraw(odds, valueRoll, rng, ownedRelics)
            forced -> {
                // Rescale the rarity's relic/frame split to fill the whole
                // roll. A row with no frame share still pays its relic.
                val nonFigure = odds.relicChance + odds.frameChance
                val relicShare = if (nonFigure <= 0.0) 1.0 else odds.relicChance / nonFigure
                if (typeRoll < relicShare) relicDraw(odds, valueRoll, rng, ownedRelics) else frameOrFigures()
            }
            typeRoll < odds.figureChance ->
                Reward.Figures(lerp(odds.figuresLow, odds.figuresHigh, valueRoll))
            typeRoll < odds.figureChance + odds.relicChance ->
                relicDraw(odds, valueRoll, rng, ownedRelics)
            else -> frameOrFigures()
        }
        return RollResult(reward, odds.rarity)
    }

    /**
     * The relic a draw pays: a cell of the house catalogue ([RelicHouses.CATALOGUE]).
     *
     * The tier is [odds]' rarity, so the existing rarity odds are untouched. Among that tier's
     * cells the draw prefers one the lifter does not hold, so progress is never wasted while a cell
     * is open. When the whole tier is held the draw is a DUPLICATE and refines a cell chosen at
     * random: its multiplier rises by [RelicHouses.refineStep] (a quarter of the tier's band),
     * capped at the top of the tier's band. A relic already at the cap pays echoes instead, the top
     * of the tier's figure band, the same fallback a crest frame has once all are owned. Why
     * refining: a duplicate always does something, the vault stays one row per relic, and the rate
     * stays bounded by the tier.
     *
     * One rng call, after the rolls the table has always made, so every seeded result before it
     * (rarity, type, value) is exactly what it was.
     */
    private fun relicDraw(odds: Odds, t: Double, rng: Random, owned: Map<String, Double>): Reward.Relic {
        val cells = RelicHouses.ofTier(odds.rarity)
        val open = cells.filter { it.id !in owned }
        val cell = (open.ifEmpty { cells })[rng.nextInt(open.ifEmpty { cells }.size)]
        val have = owned[cell.id]
        val rolled = odds.relicLow + (odds.relicHigh - odds.relicLow) * t
        if (have == null) return Reward.Relic(rolled, cell.name, cell.id)
        val cap = odds.relicHigh
        val refined = minOf(cap, have + RelicHouses.refineStep(odds.rarity))
        return if (refined > have + 1e-9) {
            Reward.Relic(refined, cell.name, cell.id, RelicOutcome.Refined)
        } else {
            Reward.Relic(have, cell.name, cell.id, RelicOutcome.Maxed, echoes = odds.figuresHigh)
        }
    }

    /**
     * THE LEGACY NAMES. Before houses a relic's identity was a name composed from its multiplier,
     * and every relic a lifter owned today still carries one. Only [relicCatalogue] and the
     * migration's reading of old names use these now.
     *
     * Relic names were composed, not fixed: multipliers are continuous, so every
     * relic needed its own identity. The name is DERIVED from the rolled value,
     * so the same roll always yields the same relic, and it doubles as the seed
     * for the sigil the UI draws.
     *
     * The tier word is part of the name because the bands overlap in wording
     * otherwise: without it the same name could mean x1.20 or x2.30, and a
     * catalogue keyed by name would silently collapse the two.
     */
    private val RELIC_FORMS = listOf(
        "Fang", "Sigil", "Shard", "Crown", "Chain", "Mirror", "Ember", "Thorn",
    )
    private val RELIC_HOUSES = listOf(
        "the Mark", "the Margin", "the Ledger", "the Abyss", "the Vigil", "the Ashen King",
    )

    private fun tierWord(rarity: RewardRarity): String = when (rarity) {
        RewardRarity.Epic -> "Greater "
        RewardRarity.Masterwork -> "Masterwork "
        else -> ""
    }

    private fun legacyRelic(odds: Odds, t: Double): Reward.Relic {
        val multiplier = odds.relicLow + (odds.relicHigh - odds.relicLow) * t
        // Quantised so the name is stable for a multiplier rather than drifting
        // with floating-point noise.
        val key = (multiplier * 1000).toInt()
        val form = RELIC_FORMS[(key / 7) % RELIC_FORMS.size]
        val house = RELIC_HOUSES[(key / 13) % RELIC_HOUSES.size]
        return Reward.Relic(multiplier, "${tierWord(odds.rarity)}$form of $house")
    }

    /** The order the retro grant fills cells in: Rare first, then Common, then the rest. */
    private val STIPEND_ORDER: List<HouseRelic> =
        listOf(RewardRarity.Rare, RewardRarity.Common, RewardRarity.Epic, RewardRarity.Masterwork)
            .flatMap { RelicHouses.ofTier(it) }

    /**
     * The [count] relics the retro grant pays to a lifter who holds [ownedIds]: the first open cells
     * in [STIPEND_ORDER], each with a deterministic multiplier inside its tier's band (so the same
     * lifter always gets the same relics). Fewer than [count] when the catalogue has no open cell.
     */
    fun stipendRelics(count: Int, ownedIds: Set<String>): List<Reward.Relic> {
        val taken = ownedIds.toMutableSet()
        val out = mutableListOf<Reward.Relic>()
        repeat(count.coerceAtLeast(0)) { i ->
            val cell = STIPEND_ORDER.firstOrNull { it.id !in taken } ?: return out
            val odds = DROP_TABLE.first { it.rarity == cell.tier }
            val t = ((ownedIds.size + i) * 0.37 + 0.13) % 1.0
            taken += cell.id
            out += Reward.Relic(odds.relicLow + (odds.relicHigh - odds.relicLow) * t, cell.name, cell.id)
        }
        return out
    }

    /**
     * Every relic the roller could produce BEFORE houses, strongest first: the 141 legacy names,
     * kept to pin what an old vault can hold. Names are
     * derived from the quantised multiplier, so the reachable set is finite and
     * enumerable — used to preview the catalogue.
     *
     * Collisions keep the STRONGER relic: an earlier version kept whichever was
     * seen first, which silently discarded every Masterwork relic whose name
     * already existed lower down.
     */
    fun relicCatalogue(): List<Reward.Relic> {
        val seen = LinkedHashMap<String, Reward.Relic>()
        DROP_TABLE.filter { it.relicChance > 0.0 }.forEach { odds ->
            // Fine sweep: the name changes on multiplier quantisation, so a
            // dense walk visits every distinct relic in the band.
            for (i in 0 until 2_000) {
                val r = legacyRelic(odds, i / 2_000.0)
                val held = seen[r.name]
                if (held == null || r.multiplier > held.multiplier) seen[r.name] = r
            }
        }
        return seen.values.sortedByDescending { it.multiplier }
    }

    /** Inclusive integer lerp driven by a pre-drawn uniform in [0, 1). */
    private fun lerp(low: Int, high: Int, t: Double) =
        (low + ((high - low + 1) * t).toInt()).coerceAtMost(high)
}
