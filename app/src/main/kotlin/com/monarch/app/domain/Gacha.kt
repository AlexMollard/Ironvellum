package com.monarch.app.domain

import java.util.Random

/**
 * Level-up gacha. Pure Kotlin so JVM tests can pin the drop table exactly.
 *
 * Rewards are idle-game value (shadows, relic multiplier) and cosmetics (crest
 * frames) ONLY — never XP, strength, levels, or titles, so the leaderboard
 * still measures training, not luck.
 *
 * Drop table (a single uniform draw in [0, 100)):
 *   Common    60%  -> shadows 20..40            | (no relic, no frame)
 *   Rare      30%  -> 60% shadows 60..120, 30% relic x1.15..1.35, 10% frame
 *   Epic       9%  -> 40% shadows 200..400, 40% relic x1.35..1.75, 20% frame
 *   Sovereign  1%  -> 30% shadows 800..1500, 50% relic x1.75..2.50, 20% frame
 *
 * Relic band: (1.0, 2.5] — strictly above 1.0 (never a no-op) and small enough
 * that the idle rate stays sane. Payout ranges never overlap across rarities,
 * so a Sovereign always pays at least as much as the best Common of its type.
 */
enum class RewardRarity { Common, Rare, Epic, Sovereign }

sealed interface Reward {
    data class Shadows(val count: Int) : Reward
    data class Relic(val multiplier: Double, val name: String) : Reward
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
        Reward.CrestFrame("monarch", "Monarch Crest"),
    )

    /**
     * The drop table, declared once. The roller AND the on-screen odds panel
     * both read this, so what a hunter is told can never drift from what the
     * roller actually does.
     *
     * [chance] is the rarity's share of a draw; [shadowChance] and
     * [relicChance] are the split WITHIN that rarity, and whatever is left is
     * a crest frame.
     */
    data class Odds(
        val rarity: RewardRarity,
        val chance: Double,
        val shadowChance: Double,
        val shadowsLow: Int,
        val shadowsHigh: Int,
        val relicChance: Double,
        val relicLow: Double,
        val relicHigh: Double,
    ) {
        val frameChance: Double get() = (1.0 - shadowChance - relicChance).coerceAtLeast(0.0)
    }

    val DROP_TABLE = listOf(
        Odds(RewardRarity.Common, 0.60, 1.00, 20, 40, 0.00, 0.0, 0.0),
        Odds(RewardRarity.Rare, 0.30, 0.60, 60, 120, 0.30, 1.15, 1.35),
        Odds(RewardRarity.Epic, 0.09, 0.40, 200, 400, 0.40, 1.35, 1.75),
        Odds(RewardRarity.Sovereign, 0.01, 0.30, 800, 1500, 0.50, 1.75, 2.50),
    )

    /** Deterministic for a given seed — same seed, same result, always. */
    fun roll(seed: Long, ownedFrames: Set<String> = emptySet()): RollResult {
        val rng = Random(seed)
        val rarityRoll = rng.nextDouble()
        // Walk the cumulative rarity shares; the last row absorbs any residue
        // so a rounding gap can never fall through to no reward at all.
        var cumulative = 0.0
        val odds = DROP_TABLE.firstOrNull { row ->
            cumulative += row.chance
            rarityRoll < cumulative
        } ?: DROP_TABLE.last()
        val typeRoll = rng.nextDouble()
        val valueRoll = rng.nextDouble()
        val reward = when {
            typeRoll < odds.shadowChance ->
                Reward.Shadows(lerp(odds.shadowsLow, odds.shadowsHigh, valueRoll))
            typeRoll < odds.shadowChance + odds.relicChance ->
                relic(odds, valueRoll)
            else -> {
                // Owned frames are excluded, or the roll is consumed for
                // nothing when the repository dedupes the duplicate.
                val candidates = CREST_FRAMES.filter { it.id !in ownedFrames }
                if (candidates.isEmpty()) {
                    // All frames owned: fall back to the next-best payout —
                    // the top of this rarity's shadow band.
                    Reward.Shadows(odds.shadowsHigh)
                } else {
                    candidates[rng.nextInt(candidates.size)]
                }
            }
        }
        return RollResult(reward, odds.rarity)
    }

    /**
     * Relic names are composed, not fixed: multipliers are continuous, so every
     * relic needs its own identity. The name is DERIVED from the rolled value,
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
        "the Shadow", "the Gate", "the Monarch", "the Abyss", "the Vigil", "the Ashen King",
    )

    private fun tierWord(rarity: RewardRarity): String = when (rarity) {
        RewardRarity.Epic -> "Greater "
        RewardRarity.Sovereign -> "Sovereign "
        else -> ""
    }

    private fun relic(odds: Odds, t: Double): Reward.Relic {
        val multiplier = odds.relicLow + (odds.relicHigh - odds.relicLow) * t
        // Quantised so the name is stable for a multiplier rather than drifting
        // with floating-point noise.
        val key = (multiplier * 1000).toInt()
        val form = RELIC_FORMS[(key / 7) % RELIC_FORMS.size]
        val house = RELIC_HOUSES[(key / 13) % RELIC_HOUSES.size]
        return Reward.Relic(multiplier, "${tierWord(odds.rarity)}$form of $house")
    }

    /**
     * Every relic the roller can actually produce, strongest first. Names are
     * derived from the quantised multiplier, so the reachable set is finite and
     * enumerable — used to preview the catalogue.
     *
     * Collisions keep the STRONGER relic: an earlier version kept whichever was
     * seen first, which silently discarded every Sovereign relic whose name
     * already existed lower down.
     */
    fun relicCatalogue(): List<Reward.Relic> {
        val seen = LinkedHashMap<String, Reward.Relic>()
        DROP_TABLE.filter { it.relicChance > 0.0 }.forEach { odds ->
            // Fine sweep: the name changes on multiplier quantisation, so a
            // dense walk visits every distinct relic in the band.
            for (i in 0 until 2_000) {
                val r = relic(odds, i / 2_000.0)
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
