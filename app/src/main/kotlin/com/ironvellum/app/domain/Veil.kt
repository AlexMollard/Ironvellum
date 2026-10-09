package com.ironvellum.app.domain

import kotlin.math.roundToLong

/**
 * The Veil's predictable rewards, as pure rules the repository and the tests share:
 * milestone crests, the one-time retro grant, and the price of an extra inscription.
 * Like [Gacha] it never touches XP, strength, levels or titles.
 */
object Veil {

    /** A crest is granted at this level and every this many levels after: 5, 10, 15... */
    const val MILESTONE_EVERY = 5

    /**
     * Bump to run a new one-time grant pass; stored per lifter as `veilGrantVersion`.
     *
     * 1 was the retro pass (crests, relics up to the floor, forgone inscriptions). 2 is the crest expansion:
     * a lifter already at version 1 gets ONLY the crests now owed (the new ladder rungs up to their level,
     * the crest of every deed held, of every house completed). The inscription and relic part of the retro
     * is for a lifter at version 0 alone: it is not idempotent against levels it already paid, so it must
     * never run twice.
     */
    const val GRANT_VERSION = 2

    /** The version whose pass also pays inscriptions and relics. */
    const val RETRO_VERSION = 1

    /** The first extra inscription costs this much essence... */
    const val OFFERING_BASE = 5_000L

    /** ...and every one bought since adds this much to the next. */
    const val OFFERING_STEP = 500L

    /**
     * The ladder crests in the order they are won, one every [MILESTONE_EVERY] levels, L5 to L60: iron,
     * bronze, silver, gold, jade, crimson, obsidian, verdant, ember, vellum, sovereign, ironvellum. An
     * explicit list of twelve, not a slice of any other catalogue, so adding a crest elsewhere can never
     * move a rung. The chance crests (aurora ...) are not on it. Relics are the house catalogue ([RelicHouses]).
     */
    val CREST_LADDER: List<String> = listOf(
        "iron", "bronze", "silver", "gold", "jade", "crimson", "obsidian", "verdant", "ember", "vellum", "sovereign", "ironvellum",
    )

    /**
     * Essence for the next extra inscription once [made] have been bought, at the price the WORN crest
     * sets ([CrestEffects.offeringScale], [CrestEffects.offeringStep]). The one price function: every
     * screen that shows a price and the repository that charges it call this with the same crest, so the
     * shown price is the charged price. No default [worn]: a call site must say which crest it priced with.
     */
    // shortcut: linear step per purchase; revisit with real data if a deep Veil mints too many draws.
    fun offeringCost(made: Int, worn: CrestEffects): Long =
        ((OFFERING_BASE + worn.offeringStep * made.coerceAtLeast(0)) * worn.offeringScale).roundToLong()

    /**
     * Spendable essence beside the lifetime total. [lifetime] only rises: earning
     * adds to both, buying lowers [essence] and leaves [lifetime] where it was,
     * so the board number and the server's greatest() rule never see a fall.
     */
    data class Balance(val essence: Long, val lifetime: Long) {
        fun earn(gained: Long): Balance =
            Balance(essence + gained, maxOf(lifetime, essence) + gained)

        /** The balance after paying [cost], or null when [essence] is short of it. */
        fun buy(cost: Long): Balance? =
            if (essence < cost) null else Balance(essence - cost, maxOf(lifetime, essence))
    }

    /**
     * The next ladder crest not yet held beyond [level], as its level and id, or null once the ladder is
     * held or [level] is past the last rung. What "Next crest at level N" tells.
     */
    fun nextMilestone(level: Int, owned: Set<String>): Pair<Int, String>? =
        CREST_LADDER.withIndex()
            .firstOrNull { (i, id) -> (i + 1) * MILESTONE_EVERY > level && id !in owned }
            ?.let { (it.index + 1) * MILESTONE_EVERY to it.value }

    /** Milestone levels crossed by a rise to [levelAfter] when levels up to [paidThrough] have already paid. */
    fun milestonesCrossed(paidThrough: Int, levelAfter: Int): List<Int> =
        ((paidThrough.coerceAtLeast(0) / MILESTONE_EVERY + 1)..(levelAfter / MILESTONE_EVERY))
            .map { it * MILESTONE_EVERY }

    /**
     * The fewest relics a lifter at [level] can hold under the pacing rules:
     * the first inscription is a relic and one lands at least every
     * [Gacha.RELIC_PITY] draws, and a lifter at level L has had L - 1 draws.
     */
    fun relicFloor(level: Int): Int = if (level < 2) 0 else 1 + (level - 2) / Gacha.RELIC_PITY

    /** What the retro pass pays in inscriptions and relics. Only ever additive. Crests are paid by [Crests.earned]. */
    data class RetroGrant(
        val inscriptions: Int,
        val relics: List<Reward.Relic>,
    ) {
        val isEmpty: Boolean get() = inscriptions == 0 && relics.isEmpty()
    }

    /**
     * The retro grant for a lifter at [level] who holds [banked] unspent
     * inscriptions, [relicCount] relics, [drawnCrests] crests that came from DRAWS and [echoes].
     *
     * - Only crests a draw paid count as draws. A ladder, deed or house crest is not a spent
     *   inscription, so counting it would call a level "accounted for" that never was.
     * - Relics: the difference up to [relicFloor], as real house relics: the open cells of the
     *   catalogue, Rare first ([Gacha.stipendRelics]), given [ownedRelicIds] already held.
     * - Inscriptions: levels that never paid. How many draws were really spent
     *   is not stored, so this pays only what is PROVABLY forgone: a level is
     *   paid for unless it can be accounted for by a draw. Each relic or crest
     *   is one draw (a crest only if [drawnCrests] counts it), and between them pity allows at most [Gacha.PITY_AFTER]
     *   echo-only draws a run, further bounded by the echoes held (every figure
     *   draw pays at least the smallest band). The rest of the L - 1 levels,
     *   less what is banked, was forgone. It can never exceed a lifter's levels.
     */
    fun retroGrant(
        level: Int,
        banked: Int,
        relicCount: Int,
        drawnCrests: Int,
        echoes: Int,
        ownedRelicIds: Set<String> = emptySet(),
    ): RetroGrant {
        val relics = Gacha.stipendRelics((relicFloor(level) - relicCount).coerceAtLeast(0), ownedRelicIds)
        val nonFigure = relicCount + drawnCrests.coerceAtLeast(0)
        val smallestFigure = Gacha.DROP_TABLE.minOf { it.figuresLow }
        val figureDraws = minOf(Gacha.PITY_AFTER * (nonFigure + 1), echoes.coerceAtLeast(0) / smallestFigure)
        val forgone = ((level - 1) - banked.coerceAtLeast(0) - nonFigure - figureDraws)
            .coerceIn(0, (level - 1).coerceAtLeast(0))
        return RetroGrant(forgone, relics)
    }
}

/**
 * What the Veil paid outside a draw, kept until a screen can show it. [retro]
 * marks the one-time pass, whose moment says the Veil remembered the lifter.
 */
data class VeilGrant(
    val retro: Boolean = false,
    val inscriptions: Int = 0,
    val relics: List<String> = emptyList(),
    val crests: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = inscriptions == 0 && relics.isEmpty() && crests.isEmpty()

    operator fun plus(other: VeilGrant) = VeilGrant(
        retro = retro || other.retro,
        inscriptions = inscriptions + other.inscriptions,
        relics = relics + other.relics,
        crests = crests + other.crests,
    )
}
