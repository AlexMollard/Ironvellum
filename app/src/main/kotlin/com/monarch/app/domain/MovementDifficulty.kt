package com.monarch.app.domain

import kotlin.math.pow
import kotlin.math.sqrt

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
 * coefficients"). Both scoring currencies now read the tier from here — XP at
 * full [intensity], strength at the damped [strengthWeight] — so the two can
 * never price the same movement differently again.
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
     * nearest tree neighbour: a dip is a Parallel Bar Dip (III), an inverted row
     * is an Australian Pull-up (II), a chin-up is a Pull-up (III). The tier
     * states UNLOADED difficulty only: every externally-loaded lift already
     * priced here (back squat, overhead press) stays at its unloaded tier
     * because XP multiplies by the kilos on the bar, so pricing the load into
     * the tier as well would pay for the same plate twice — the trap
     * [loadPricedTiers] documents. Isolation work is tier 1 (bicep curl,
     * wrist curl).
     */
    private val catalogueTiers: Map<String, Int> = mapOf(
        // Pull
        "chin-up" to 3,
        "inverted row" to 2,
        "door sheet row" to 2,
        "active bar hang" to 1,
        "wrist curl" to 1,
        "bicep curl" to 1,
        "barbell row" to 2,
        "dumbbell row" to 2,
        "lat pulldown" to 2,
        "face pull" to 1,
        // Push
        "dip" to 3,
        "pike push-up" to 2,
        "overhead press" to 2,
        "bench press" to 2,
        "incline bench press" to 2,
        // Legs
        "back squat" to 2,
        "bulgarian split squat" to 2,
        "single-leg glute bridge" to 2,
        "single-leg calf raise" to 1,
        "knee-to-wall dorsiflexion" to 1,
        "glute bridge" to 1,
        "deadlift" to 2,
        "romanian deadlift" to 2,
        "front squat" to 2,
        "hip thrust" to 2,
        // Core
        "ab wheel rollout" to 3,
        "weighted plank" to 2,
        "plank" to 1,
        "side plank" to 1,
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
        "weighted dip" to 3, // Parallel Bar Dip
    )

    /**
     * Seconds of a static hold worth one rep-equivalent. The ONE place this
     * conversion lives: XP and the strength score both read it, so a hold
     * cannot be priced differently by the two systems.
     */
    const val SECONDS_PER_REP_EQUIVALENT = 5.0

    /** Reps in one set that pay full rate. */
    const val FULL_VALUE_REPS = 10

    /** What a unit of volume past [FULL_VALUE_REPS] pays. */
    const val TAPERED_RATE = 1.0 / 3.0

    /**
     * Volume with diminishing returns: the first [FULL_VALUE_REPS] units count
     * in full, the rest at [TAPERED_RATE].
     *
     * This lives here and not on a scoring object because BOTH currencies that
     * price a set must taper identically. XP always did; the strength index
     * did not, which is the bug this move fixes — a 100-rep bodyweight set
     * banked ten times the strength of a 10-rep set and the summed leaderboard
     * rewarded volume-farming over hard work.
     */
    fun taperedVolume(rawUnits: Double): Double {
        if (rawUnits <= FULL_VALUE_REPS) return rawUnits.coerceAtLeast(0.0)
        return FULL_VALUE_REPS + (rawUnits - FULL_VALUE_REPS) * TAPERED_RATE
    }

    /**
     * The weight [StrengthIndex] gives a movement, damped to the square root
     * of its XP intensity: tier I x1, II x1.41, III x2, IV x2.83, V x4.
     *
     * The damping is deliberate. The strength index is a body-scaled LOAD
     * figure — the bodyweight^0.67 term is its spine, and full 2^(tier-1)
     * weighting would let the name swamp the load. But with NO weighting at
     * all, a tier-I bodyweight squat and a tier-V one-arm pull-up scored
     * identically for the same reps at the same bodyweight, and cheap
     * volume farmed a summed lifetime leaderboard. sqrt breaks that 42.5-for-
     * everything tie while keeping the load scaling dominant: the hardest
     * movement caps at four times the easiest.
     */
    fun strengthWeight(exerciseName: String): Double = sqrt(intensity(exerciseName))

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
        "side plank",
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
     * Every catalogue-only key this object prices, lowercased. Skill-tree
     * names are excluded: Seed derives those rows from [Skills] itself, so
     * they cannot drift apart.
     *
     * Exposed for the test that walks these keys BACK to the catalogue. The
     * forward direction (every seeded movement is classified) missed the case
     * that actually shipped: `plank` was priced and marked a hold while no
     * Plank row existed, so the classification sat there dead.
     */
    val catalogueOnlyKeys: Set<String>
        get() = catalogueTiers.keys + catalogueHolds + loadPricedTiers.keys

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
