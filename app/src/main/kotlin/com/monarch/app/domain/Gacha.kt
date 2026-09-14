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

    /** Deterministic for a given seed — same seed, same result, always. */
    fun roll(seed: Long): RollResult {
        val rng = Random(seed)
        val rarityRoll = rng.nextDouble() * 100.0
        val rarity = when {
            rarityRoll < 60.0 -> RewardRarity.Common
            rarityRoll < 90.0 -> RewardRarity.Rare
            rarityRoll < 99.0 -> RewardRarity.Epic
            else -> RewardRarity.Sovereign
        }
        val typeRoll = rng.nextDouble()
        val valueRoll = rng.nextDouble()
        return when (rarity) {
            RewardRarity.Common -> RollResult(Reward.Shadows(lerp(20, 40, valueRoll)), rarity)
            RewardRarity.Rare -> when {
                typeRoll < 0.60 -> RollResult(Reward.Shadows(lerp(60, 120, valueRoll)), rarity)
                typeRoll < 0.90 -> RollResult(relic(1.15, 1.35, valueRoll), rarity)
                else -> RollResult(frame(rng.nextInt(CREST_FRAMES.size)), rarity)
            }
            RewardRarity.Epic -> when {
                typeRoll < 0.40 -> RollResult(Reward.Shadows(lerp(200, 400, valueRoll)), rarity)
                typeRoll < 0.80 -> RollResult(relic(1.35, 1.75, valueRoll), rarity)
                else -> RollResult(frame(rng.nextInt(CREST_FRAMES.size)), rarity)
            }
            RewardRarity.Sovereign -> when {
                typeRoll < 0.30 -> RollResult(Reward.Shadows(lerp(800, 1500, valueRoll)), rarity)
                typeRoll < 0.80 -> RollResult(relic(1.75, 2.50, valueRoll), rarity)
                else -> RollResult(frame(rng.nextInt(CREST_FRAMES.size)), rarity)
            }
        }
    }

    private fun frame(index: Int) = CREST_FRAMES[index]

    private fun relic(low: Double, high: Double, t: Double) =
        Reward.Relic(low + (high - low) * t, "Relic Sigil")

    /** Inclusive integer lerp driven by a pre-drawn uniform in [0, 1). */
    private fun lerp(low: Int, high: Int, t: Double) = low + ((high - low) * t).toInt()
}
