package com.ironvellum.app.domain

/**
 * The Veil's predictable rewards, as pure rules the repository and the tests share:
 * milestone crests, the one-time retro grant, and the price of an extra inscription.
 * Like [Gacha] it never touches XP, strength, levels or titles.
 */
object Veil {

    /** A crest is granted at this level and every this many levels after: 5, 10, 15... */
    const val MILESTONE_EVERY = 5

    /** Bump to run a new one-time grant pass; stored per lifter as `veilGrantVersion`. */
    const val GRANT_VERSION = 1

    /** The first extra inscription costs this much essence... */
    const val OFFERING_BASE = 5_000L

    /** ...and every one bought since adds this much to the next. */
    const val OFFERING_STEP = 500L

    /**
     * Milestone crests in the order they are won: iron L5, bronze L10, silver L15,
     * gold L20, jade L25, crimson L30, obsidian L35. Aurora, void and masterwork
     * stay chance-only. Relics are the house catalogue ([RelicHouses]).
     */
    val CREST_LADDER: List<String> = Gacha.CREST_FRAMES.take(7).map { it.id }

    /** Essence for the next extra inscription once [made] have been bought. */
    // shortcut: linear +500 a purchase; revisit with real data if a deep Veil mints too many draws.
    fun offeringCost(made: Int): Long = OFFERING_BASE + OFFERING_STEP * made.coerceAtLeast(0)

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

    /** Milestone levels crossed by a rise to [levelAfter] when levels up to [paidThrough] have already paid. */
    fun milestonesCrossed(paidThrough: Int, levelAfter: Int): List<Int> =
        ((paidThrough.coerceAtLeast(0) / MILESTONE_EVERY + 1)..(levelAfter / MILESTONE_EVERY))
            .map { it * MILESTONE_EVERY }

    /**
     * The crest a milestone pays: its own rung of the ladder, or, when that is
     * already worn in the vault (won by chance), the lowest rung not yet owned.
     * Null once the whole ladder is owned.
     */
    fun milestoneCrest(milestoneLevel: Int, owned: Set<String>): String? {
        val own = CREST_LADDER.getOrNull(milestoneLevel / MILESTONE_EVERY - 1)
        return if (own != null && own !in owned) own else CREST_LADDER.firstOrNull { it !in owned }
    }

    /**
     * The fewest relics a lifter at [level] can hold under the pacing rules:
     * the first inscription is a relic and one lands at least every
     * [Gacha.RELIC_PITY] draws, and a lifter at level L has had L - 1 draws.
     */
    fun relicFloor(level: Int): Int = if (level < 2) 0 else 1 + (level - 2) / Gacha.RELIC_PITY

    /** What the one-time pass pays. Only ever additive. */
    data class RetroGrant(
        val inscriptions: Int,
        val crestIds: List<String>,
        val relics: List<Reward.Relic>,
    ) {
        val isEmpty: Boolean get() = inscriptions == 0 && crestIds.isEmpty() && relics.isEmpty()
    }

    /**
     * The retro grant for a lifter at [level] who holds [banked] unspent
     * inscriptions, [relicCount] relics, [ownedFrames] crests and [echoes].
     *
     * - Crests: every milestone crest up to [level] that is not owned.
     * - Relics: the difference up to [relicFloor], as real house relics: the open cells of the
     *   catalogue, Rare first ([Gacha.stipendRelics]), given [ownedRelicIds] already held.
     * - Inscriptions: levels that never paid. How many draws were really spent
     *   is not stored, so this pays only what is PROVABLY forgone: a level is
     *   paid for unless it can be accounted for by a draw. Each relic or crest
     *   is one draw, and between them pity allows at most [Gacha.PITY_AFTER]
     *   echo-only draws a run, further bounded by the echoes held (every figure
     *   draw pays at least the smallest band). The rest of the L - 1 levels,
     *   less what is banked, was forgone. It can never exceed a lifter's levels.
     */
    fun retroGrant(
        level: Int,
        banked: Int,
        relicCount: Int,
        ownedFrames: Set<String>,
        echoes: Int,
        ownedRelicIds: Set<String> = emptySet(),
    ): RetroGrant {
        val crests = CREST_LADDER.take(level / MILESTONE_EVERY).filter { it !in ownedFrames }
        val relics = Gacha.stipendRelics((relicFloor(level) - relicCount).coerceAtLeast(0), ownedRelicIds)
        val nonFigure = relicCount + ownedFrames.size
        val smallestFigure = Gacha.DROP_TABLE.minOf { it.figuresLow }
        val figureDraws = minOf(Gacha.PITY_AFTER * (nonFigure + 1), echoes.coerceAtLeast(0) / smallestFigure)
        val forgone = ((level - 1) - banked.coerceAtLeast(0) - nonFigure - figureDraws)
            .coerceIn(0, (level - 1).coerceAtLeast(0))
        return RetroGrant(forgone, crests, relics)
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
