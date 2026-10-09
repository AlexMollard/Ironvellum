package com.ironvellum.app.domain

import kotlin.math.ceil
import kotlin.math.roundToInt

/** The two pages of the collection. */
enum class CollectionTab { Relics, Crests }

/** What the Veil reads at one moment, taken before and after a draw: the rate, the echoes held and the vault. */
data class VeilSample(val perHour: Double, val echoes: Int, val vault: VaultState)

/** The rate as the screen prints it, one decimal: a change is only a change if it shows. */
private fun shown(perHour: Double): Double = (perHour * 10).roundToInt() / 10.0

/**
 * What one inscription did to the Veil, measured against the Veil just before it: the rate, the
 * strongest relic, the echoes, how many echo draws now sit in a row, and any set bonus it unlocked.
 * Pure data, built by [VeilDraw.change], so a reveal says only what the rules did.
 */
data class DrawChange(
    val result: RollResult,
    val rateBefore: Double,
    val rateAfter: Double,
    val strongestBefore: HouseRelic?,
    val strongestAfter: HouseRelic?,
    /** Echoes held after the draw. */
    val echoesAfter: Int,
    /** Echo-only draws in a row after this one; pity forces a relic or a crest at [Gacha.PITY_AFTER]. */
    val echoRun: Int,
    /** The house of a drawn relic, or null for echoes and crests. */
    val house: RelicHouse?,
    /** Set bonuses this draw reached, lowest first. */
    val unlocked: List<SetBonus>,
) {
    /** The change in the printed rate, to one decimal; 0.0 when it did not move on screen. */
    val rateDelta: Double get() = shown(rateAfter) - shown(rateBefore)

    val strongestChanged: Boolean get() = strongestAfter?.id != strongestBefore?.id

    /** Echoes this draw paid: its own payout, or a maxed relic's. */
    val echoesPaid: Int
        get() = when (val reward = result.reward) {
            is Reward.Figures -> reward.count
            is Reward.Relic -> reward.echoes
            is Reward.CrestFrame -> 0
        }

    /** The bonus this draw just reached, as "House of Iron complete · ..." or "Set bonus reached · ...", or null. */
    val bonusLine: String?
        get() {
            val bonus = unlocked.lastOrNull() ?: return null
            val of = house ?: return null
            return if (bonus.needed == RelicHouses.FULL) "${of.title} complete · ${bonus.summary}" else "Set bonus reached · ${bonus.summary}"
        }
}

/**
 * What the Veil says after the reveal is done: one line naming what joined the collection, the change
 * in rate over the draws just made, and the one item the collection card marks New. Kept until the next
 * visit, then gone.
 */
data class VeilLanding(
    val line: String,
    val tab: CollectionTab,
    /** The rate moved by this over the run; 0.0 when it did not move. */
    val rateDelta: Double,
    /** The relic or crest to mark New in the collection card, or null when the run added nothing to hold. */
    val fresh: String?,
    val freshNote: String?,
    val freshRelicId: String? = null,
    val freshCrestId: String? = null,
    val freshTier: RewardRarity? = null,
    /** Houses the run completed, which the collection card wears in gold. */
    val completed: Set<RelicHouse> = emptySet(),
)

object VeilDraw {

    /** The change [result] made between [before] and [after]; [pityAfter] is the pity once it was paid. */
    fun change(before: VeilSample, after: VeilSample, result: RollResult, pityAfter: Gacha.Pity): DrawChange {
        val relic = (result.reward as? Reward.Relic)?.let { RelicHouses.byId(it.relicId) }
        val unlocked = relic?.let { reached(before.vault, after.vault, it.house) }.orEmpty()
        return DrawChange(
            result = result,
            rateBefore = before.perHour,
            rateAfter = after.perHour,
            strongestBefore = before.vault.active?.relic,
            strongestAfter = after.vault.active?.relic,
            echoesAfter = after.echoes,
            echoRun = pityAfter.figureStreak,
            house = relic?.house,
            unlocked = unlocked,
        )
    }

    /** Bonuses of [house] that [after] has reached and [before] had not. */
    fun reached(before: VaultState, after: VaultState, house: RelicHouse): List<SetBonus> {
        val was = before.houses.firstOrNull { it.house == house }?.reached?.map { it.needed }.orEmpty().toSet()
        return after.houses.firstOrNull { it.house == house }?.reached.orEmpty().filter { it.needed !in was }
    }

    /**
     * The landing after a run of [draws] (oldest first), or null with none. The line names the last
     * draw that added something to hold: a relic or a crest that joined, a relic refined, echoes paid.
     */
    fun landing(draws: List<DrawChange>): VeilLanding? {
        val last = draws.lastOrNull() ?: return null
        val delta = shown(last.rateAfter) - shown(draws.first().rateBefore)
        val completed = draws.filter { d -> d.unlocked.any { it.needed == RelicHouses.FULL } }.mapNotNull { it.house }.toSet()
        // The newest thing that joined the collection leads; otherwise the last draw speaks for the run.
        val joined = draws.lastOrNull { joinedName(it) != null } ?: last
        val reward = joined.result.reward
        val tab = if (reward is Reward.CrestFrame) CollectionTab.Crests else CollectionTab.Relics
        val name = joinedName(joined)
        val line = name?.let { "$it joined your collection" } ?: when (reward) {
            is Reward.Relic -> when (reward.outcome) {
                RelicOutcome.Refined -> "${reward.name} was refined to ×${"%.2f".format(java.util.Locale.US, reward.multiplier)}"
                else -> "${reward.name} paid ${echoWord(reward.echoes)}"
            }
            is Reward.Figures -> "${echoWord(reward.count)} added to the Veil"
            is Reward.CrestFrame -> "${crestName(reward)} joined your collection"
        }
        return VeilLanding(
            line = line,
            tab = tab,
            rateDelta = delta,
            fresh = name,
            freshNote = joined.bonusLine,
            freshRelicId = (reward as? Reward.Relic)?.relicId?.takeIf { name != null },
            freshCrestId = (reward as? Reward.CrestFrame)?.id,
            freshTier = joined.result.rarity.takeIf { name != null },
            completed = completed,
        )
    }

    /** The name of what joined the collection in [d], or null when it only refined, paid echoes or repeated. */
    private fun joinedName(d: DrawChange): String? = when (val reward = d.result.reward) {
        is Reward.Relic -> reward.name.takeIf { reward.outcome == RelicOutcome.New }
        is Reward.CrestFrame -> crestName(reward)
        is Reward.Figures -> null
    }

    private fun crestName(crest: Reward.CrestFrame) = crest.name.replace(" Crest", " crest")

    private fun echoWord(count: Int) = "$count ${if (count == 1) "echo" else "echoes"}"
}

/** How long, in whole hours, until [short] more essence has gathered at [perHour]; null when nothing is gathering. */
fun hoursToGather(short: Double, perHour: Double): Int? =
    if (short <= 0.0 || perHour <= 0.0 || !perHour.isFinite()) null else ceil(short / perHour).toInt().coerceAtLeast(1)
