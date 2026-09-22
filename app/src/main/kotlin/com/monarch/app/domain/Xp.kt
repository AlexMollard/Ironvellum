package com.monarch.app.domain

import kotlin.math.roundToInt

/**
 * Session XP.
 *
 * The rule is intensity x volume, not volume alone. A flat rate per rep made a
 * set of twenty push-ups beat a set of five handstand push-ups, and — because
 * a hold's seconds are typed into the reps field — made a sixty-second hollow
 * hold the most lucrative thing in the app. Three corrections:
 *
 *  1. Each rep is worth [MovementDifficulty.intensity] of the movement, so a
 *     tier IV press pays 4x a tier II press per rep.
 *  2. A hold's seconds convert to rep-equivalents at
 *     [SECONDS_PER_EFFORT_UNIT], so a minute of holding is work, not a minute
 *     of sixty reps.
 *  3. Reps past [FULL_VALUE_REPS] in one set pay [TAPERED_RATE]. Deep into a
 *     set of twenty-five the work is endurance; the app should not make
 *     grinding out easy volume the fastest way to level.
 *
 * Scaled so an ordinary session lands where it always did — this is a
 * rebalance between movements, not an inflation. Past sessions keep the XP
 * they were awarded; nothing is recomputed.
 */
object Xp {
    /**
     * Scale is anchored so an ordinary session is worth what it always was:
     * 4x8 pull-ups + 3x10 dips + 3x12 hanging leg raises scored 273 under the
     * flat rate and 278 under this one. The rebalance moves XP *between*
     * movements — hard work up, hold-farming down — rather than inflating
     * everything. It also keeps a repeatable session below a tier V skill
     * claim (600), because Skills prices claims as milestones, not sets.
     */

    /** Paid once per completed set, regardless of what was in it. */
    const val BASE_PER_SET = 5

    /** XP per unit of effort (one tier-I rep = one unit). */
    const val PER_EFFORT_UNIT = 0.8

    const val COMPLETION_BONUS = 25
    const val QUEST_BONUS = 25

    /** Reps in one set that pay full rate. */
    const val FULL_VALUE_REPS = MovementDifficulty.FULL_VALUE_REPS

    /** What a rep past [FULL_VALUE_REPS] pays. */
    const val TAPERED_RATE = MovementDifficulty.TAPERED_RATE

    /**
     * Seconds of a hold worth one rep-equivalent — the shared conversion, so
     * XP and the strength score cannot price a hold differently.
     */
    const val SECONDS_PER_EFFORT_UNIT = MovementDifficulty.SECONDS_PER_REP_EQUIVALENT

    /** Added load cannot multiply a rep beyond this, so 200 kg cannot run away. */
    const val MAX_LOAD_MULTIPLIER = 3.0

    /** Used only when no bodyweight has ever been recorded. */
    const val ASSUMED_BODYWEIGHT_KG = 75.0

    /** One completed set, as XP sees it. */
    data class SetEffort(
        val exerciseName: String,
        /** Repetitions. Always 0 for a hold. */
        val reps: Int = 0,
        /** Seconds held. Null unless this is a hold. */
        val holdSeconds: Int? = null,
        val weightKg: Double? = null,
        val modifiers: String = "",
        /** The catalogue's metric, when known; decides hold-ness outright. */
        val metric: ExerciseMetric? = null,
    )

    /**
     * Alias for [MovementDifficulty.taperedVolume], where the taper now lives:
     * both scoring currencies must taper identically, so both read one body.
     */
    fun taperedVolume(rawUnits: Double): Double = MovementDifficulty.taperedVolume(rawUnits)

    /**
     * Added kilos as a multiple of the lifter's own mass, capped.
     *
     * The marked number is converted to real load first
     * ([MovementDifficulty.loadFactor]): 200 kg on an angled sled is not
     * 200 kg hanging off a belt, and paying it as though it were made the
     * leg press the best-value movement in the catalogue.
     */
    fun loadMultiplier(exerciseName: String, addedKg: Double?, bodyweightKg: Double?): Double {
        val marked = (addedKg ?: 0.0).coerceAtLeast(0.0)
        if (marked <= 0.0) return 1.0
        val added = marked * MovementDifficulty.loadFactor(exerciseName)
        val bw = bodyweightKg?.takeIf { it > 0.0 } ?: ASSUMED_BODYWEIGHT_KG
        return ((bw + added) / bw).coerceAtMost(MAX_LOAD_MULTIPLIER)
    }

    /** Effort units for one set, before [PER_EFFORT_UNIT] converts them to XP. */
    fun effortUnits(set: SetEffort, bodyweightKg: Double?): Double {
        val hold = MovementDifficulty.isHoldSet(set.metric, set.exerciseName, set.modifiers)
        val raw = if (hold) {
            // holdSeconds is the field; falling back to reps recovers a set
            // restored from an archive written before HOLD existed, which
            // still carries its seconds in the reps column.
            MovementDifficulty.holdRepEquivalents(set.holdSeconds ?: set.reps)
        } else {
            set.reps.coerceAtLeast(0).toDouble()
        }
        if (raw <= 0.0) return 0.0
        return taperedVolume(raw) *
            MovementDifficulty.intensity(set.exerciseName) *
            loadMultiplier(set.exerciseName, set.weightKg, bodyweightKg) *
            MovementDifficulty.modifierFactor(set.modifiers)
    }

    /** XP for one completed set. An empty set pays nothing, not a base rate. */
    fun setXp(set: SetEffort, bodyweightKg: Double?): Int {
        val units = effortUnits(set, bodyweightKg)
        if (units <= 0.0) return 0
        return BASE_PER_SET + (PER_EFFORT_UNIT * units).roundToInt()
    }

    /** XP for the lifting half of a session: every completed set, plus the bonus for finishing. */
    fun award(sets: List<SetEffort>, bodyweightKg: Double?): Int =
        sets.sumOf { setXp(it, bodyweightKg) } + COMPLETION_BONUS

    fun xpForNextLevel(level: Int): Long = 100L * level

    data class Progress(val level: Int, val intoLevel: Long, val needed: Long)

    fun progress(totalXp: Long): Progress {
        var remaining = totalXp.coerceAtLeast(0)
        var level = 1
        while (remaining >= xpForNextLevel(level)) {
            remaining -= xpForNextLevel(level)
            level++
        }
        return Progress(level, remaining, xpForNextLevel(level))
    }

    fun levelFor(totalXp: Long): Int = progress(totalXp).level
}
