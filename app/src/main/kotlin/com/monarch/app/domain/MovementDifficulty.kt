package com.monarch.app.domain

import kotlin.math.pow

/**
 * How hard one rep of a movement is, and whether it is counted or timed.
 *
 * The skill tree is already this app's difficulty ordering — a handstand
 * push-up sits at tier IV and a push-up at tier II because the tree says so,
 * and [Seed] derives loggable exercises from that same list. Rather than add a
 * second, rival difficulty table, XP reads the tier. Movements that exist only
 * in the base catalogue get an explicit tier here; anything unknown (a
 * user-created exercise) falls back to [DEFAULT_TIER], so nobody can mint XP by
 * inventing a name.
 *
 * This resolves the shortcut StrengthIndex flagged ("revisit per-movement
 * coefficients") for the XP path only. StrengthIndex itself is unchanged —
 * its score feeds the leaderboard and lifetime totals, and re-weighting those
 * would silently restate history.
 */
object MovementDifficulty {

    /** A movement nobody has classified is treated as ordinary work. */
    const val DEFAULT_TIER = 2

    /**
     * XP per rep doubles per tier. Two tiers of separation (push-up II vs
     * handstand push-up IV) is 4x per rep, which is the gap the tree claims.
     */
    const val TIER_BASE = 2.0

    /**
     * Catalogue movements with no skill-tree entry. Tiers are set against the
     * nearest tree neighbour: a dip is a Parallel Bar Dip (II), an inverted row
     * is an Australian Pull-up (II), a chin-up is a Pull-up (III).
     */
    private val catalogueTiers: Map<String, Int> = mapOf(
        // Pull
        "chin-up" to 3,
        "inverted row" to 2,
        "door sheet row" to 2,
        "active bar hang" to 1,
        "wrist curl" to 1,
        "bicep curl" to 1,
        // Push
        "dip" to 2,
        "pike push-up" to 2,
        "overhead press" to 2,
        // Legs
        "back squat" to 2,
        "bulgarian split squat" to 2,
        "single-leg glute bridge" to 1,
        "single-leg calf raise" to 1,
        "knee-to-wall dorsiflexion" to 1,
        "glute bridge" to 1,
        // Core
        "ab wheel rollout" to 3,
        "weighted plank" to 2,
        "plank" to 1,
    )

    /**
     * Tree tiers that price the added load into the movement itself.
     * "Weighted Pull-up" is tier V because *five reps with +25 kg* is an elite
     * standard — but XP already multiplies by the kilos on the belt, so
     * reading the tier straight would pay for that load twice. These score as
     * their unloaded parent and let the real weight do the work: a
     * +25 kg pull-up out-earns a bare one because it is heavier, not because
     * of its name.
     */
    private val loadPricedTiers: Map<String, Int> = mapOf(
        "weighted pull-up" to 3, // Pull-up
        "weighted dip" to 2, // Parallel Bar Dip
    )

    /**
     * Seconds of a static hold worth one rep-equivalent. The ONE place this
     * conversion lives: XP and the strength score both read it, so a hold
     * cannot be priced differently by the two systems.
     */
    const val SECONDS_PER_REP_EQUIVALENT = 5.0

    /** Rep-equivalents for a hold of [seconds]. */
    fun holdRepEquivalents(seconds: Int): Double =
        seconds.coerceAtLeast(0) / SECONDS_PER_REP_EQUIVALENT

    /**
     * Movements the SEED marks [ExerciseMetric.HOLD]. The skill tree's claim
     * standard decides for tree movements; these are the catalogue-only ones.
     * Kept as names because Seed builds rows from names, and because an
     * exercise imported from an older archive still carries the old REPS
     * metric and has to be recognised anyway.
     */
    private val catalogueHolds: Set<String> = setOf(
        "active bar hang",
        "weighted plank",
        "plank",
    )

    /**
     * Legacy signal: before [ExerciseMetric.HOLD] existed, a hold was a REPS
     * exercise carrying this modifier and its seconds typed into the reps
     * box. Archives and cloud rows written then still look like that.
     */
    const val HOLD_MODIFIER = "hold"

    /** Case-insensitive skill lookup: the catalogue and the tree share names. */
    private val skillsByKey: Map<String, Skills.SkillDef> =
        Skills.ALL.associateBy { it.name.trim().lowercase() }

    private fun key(exerciseName: String) = exerciseName.trim().lowercase()

    /**
     * A load-priced override first, then the skill tree, then the catalogue,
     * then [DEFAULT_TIER].
     */
    fun tier(exerciseName: String): Int {
        val k = key(exerciseName)
        return loadPricedTiers[k] ?: skillsByKey[k]?.tier ?: catalogueTiers[k] ?: DEFAULT_TIER
    }

    /**
     * Whether this movement has a stated difficulty rather than falling back
     * to [DEFAULT_TIER]. A shipped exercise that nobody classified is scored
     * as ordinary work whatever it costs, which is the bug this object exists
     * to fix — so a test holds the seeded catalogue to full coverage.
     */
    fun isClassified(exerciseName: String): Boolean {
        val k = key(exerciseName)
        return k in skillsByKey || k in catalogueTiers || k in loadPricedTiers
    }

    /** XP-per-rep weight for a movement: 1, 2, 4, 8, 16 across tiers I..V. */
    fun intensity(exerciseName: String): Double =
        TIER_BASE.pow(tier(exerciseName) - 1)

    /**
     * Whether a movement is held rather than counted, judged by NAME alone.
     * This is what [com.monarch.app.data.Seed] uses to stamp
     * [ExerciseMetric.HOLD] onto the catalogue, and what recognises a hold
     * that arrived from an archive or a cloud row written before the metric
     * existed. Once a row carries the metric, prefer [isHoldSet].
     */
    fun isHoldByName(exerciseName: String): Boolean {
        val k = key(exerciseName)
        skillsByKey[k]?.let { return it.metric == Skills.Metric.SECONDS }
        return k in catalogueHolds
    }

    /**
     * Whether one logged set is a hold. The exercise's metric decides; the
     * name and the legacy "hold seconds" modifier are fallbacks for rows the
     * migration never saw — a custom exercise the hunter named himself, or an
     * archive restored from an older build.
     */
    fun isHoldSet(
        metric: ExerciseMetric?,
        exerciseName: String,
        modifiers: String = "",
    ): Boolean {
        if (metric == ExerciseMetric.HOLD) return true
        // A metric that states something else is believed: a movement renamed
        // to "plank" but logged in reps must not be re-read as seconds.
        if (metric != null && metric != ExerciseMetric.REPS) return false
        if (modifiers.split(",").any { it.trim().lowercase().startsWith(HOLD_MODIFIER) }) return true
        return isHoldByName(exerciseName)
    }

    /**
     * What each modifier does to the effort of a rep. "weighted" is absent on
     * purpose: the added kilos are already counted as load, so multiplying here
     * too would pay for the same plate twice. "banded" is absent because a band
     * assists a pull-up and resists a push-up — it cannot mean one thing.
     */
    private val modifierFactors: Map<String, Double> = mapOf(
        "assisted" to 0.75,
        "incline" to 0.85,
        "elevated" to 1.08,
        "decline" to 1.08,
        "deficit" to 1.10,
        "tempo" to 1.10,
        "paused" to 1.10,
        "archer" to 1.35,
        "one-arm" to 1.70,
    )

    const val MIN_MODIFIER_FACTOR = 0.5
    const val MAX_MODIFIER_FACTOR = 2.5

    /** Combined effort multiplier for a comma-separated modifier string. */
    fun modifierFactor(modifiers: String): Double {
        if (modifiers.isBlank()) return 1.0
        val product = modifiers.split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .fold(1.0) { acc, token -> acc * (modifierFactors[token] ?: 1.0) }
        return product.coerceIn(MIN_MODIFIER_FACTOR, MAX_MODIFIER_FACTOR)
    }
}
