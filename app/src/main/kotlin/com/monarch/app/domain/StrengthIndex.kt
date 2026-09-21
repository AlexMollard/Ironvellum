package com.monarch.app.domain

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Body-scaled strength scoring. A rep of bodyweight work scores
 * `reps * (bodyweight + added kg) / bodyweight^0.67` — allometric exponent
 * 2/3, the standard strength-vs-mass scaling. Heavier hunters must move more
 * absolute load for the same score, so milestones compare athletes fairly.
 *
 * A static hold contributes through the SAME conversion XP uses
 * ([MovementDifficulty.SECONDS_PER_REP_EQUIVALENT]), so a minute of L-sit is
 * worth twelve rep-equivalents rather than the sixty repetitions the old
 * reps-column encoding made it.
 *
 * Per-movement weighting and the volume taper ARE applied: the rep-equivalents
 * pass through [MovementDifficulty.taperedVolume] — the same taper XP uses, so
 * the two currencies cannot disagree about volume — and the result is scaled
 * by [MovementDifficulty.strengthWeight], sqrt-damped so bodyweight scaling
 * stays dominant. The earlier version applied neither, and the flat score let
 * cheap volume (five sets of 100 bodyweight squats) farm a leaderboard that
 * sums lifetime scores.
 *
 * v1 assumptions (deliberate): every bodyweight rep moves ~full bodyweight;
 * height is not separately compensated (it correlates with mass).
 */
object StrengthIndex {

    const val MASS_EXPONENT = 0.67

    /**
     * The formula version stamp for stored scores. Bump this whenever the
     * scoring formula changes in a way that restates history, so the migration
     * layer can rescore every completed session from its stored sets exactly
     * once — scores and the lifetime sum are meaningless if old and new
     * formulas mix in one leaderboard.
     */
    const val SCORING_VERSION = 1

    fun repScore(exerciseName: String, reps: Int, addedKg: Double?, bodyweightKg: Double): Double =
        effortScore(exerciseName, reps.toDouble(), addedKg, bodyweightKg)

    /** A hold's contribution: its seconds converted to rep-equivalents. */
    fun holdScore(exerciseName: String, seconds: Int, addedKg: Double?, bodyweightKg: Double): Double =
        effortScore(exerciseName, MovementDifficulty.holdRepEquivalents(seconds), addedKg, bodyweightKg)

    private fun effortScore(
        exerciseName: String,
        repEquivalents: Double,
        addedKg: Double?,
        bodyweightKg: Double,
    ): Double {
        if (repEquivalents <= 0.0 || bodyweightKg <= 0.0) return 0.0
        val load = bodyweightKg + (addedKg ?: 0.0).coerceAtLeast(0.0)
        return MovementDifficulty.taperedVolume(repEquivalents) *
            load / bodyweightKg.pow(MASS_EXPONENT) *
            MovementDifficulty.strengthWeight(exerciseName)
    }

    /** One completed strength set: reps, or seconds when it is a hold. */
    data class Effort(
        val exerciseName: String,
        val reps: Int,
        val holdSeconds: Int?,
        val addedKg: Double?,
    )

    /** Total score for a session's done sets; null when bodyweight is unknown. */
    fun sessionScore(sets: List<Effort>, bodyweightKg: Double?): Int? {
        if (bodyweightKg == null || bodyweightKg <= 0.0) return null
        return sets.sumOf { effort ->
            val units = effort.holdSeconds
                ?.let { MovementDifficulty.holdRepEquivalents(it) }
                ?: effort.reps.toDouble()
            effortScore(effort.exerciseName, units, effort.addedKg, bodyweightKg)
        }.roundToInt()
    }
}
