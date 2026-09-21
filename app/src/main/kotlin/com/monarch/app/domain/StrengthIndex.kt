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
 * v1 assumptions (deliberate): every bodyweight rep moves ~full bodyweight;
 * height is not separately compensated (it correlates with mass).
 * Per-movement leverage coefficients live in [MovementDifficulty] and are
 * deliberately NOT applied here — this index ranks the shadow board, and
 * re-weighting it would make new scores incomparable with stored ones.
 */
object StrengthIndex {

    const val MASS_EXPONENT = 0.67

    fun repScore(reps: Int, addedKg: Double?, bodyweightKg: Double): Double =
        effortScore(reps.toDouble(), addedKg, bodyweightKg)

    /** A hold's contribution: its seconds converted to rep-equivalents. */
    fun holdScore(seconds: Int, addedKg: Double?, bodyweightKg: Double): Double =
        effortScore(MovementDifficulty.holdRepEquivalents(seconds), addedKg, bodyweightKg)

    private fun effortScore(repEquivalents: Double, addedKg: Double?, bodyweightKg: Double): Double {
        if (repEquivalents <= 0.0 || bodyweightKg <= 0.0) return 0.0
        val load = bodyweightKg + (addedKg ?: 0.0).coerceAtLeast(0.0)
        return repEquivalents * load / bodyweightKg.pow(MASS_EXPONENT)
    }

    /** One completed strength set: reps, or seconds when it is a hold. */
    data class Effort(val reps: Int, val holdSeconds: Int?, val addedKg: Double?)

    /** Total score for a session's done sets; null when bodyweight is unknown. */
    fun sessionScore(sets: List<Effort>, bodyweightKg: Double?): Int? {
        if (bodyweightKg == null || bodyweightKg <= 0.0) return null
        return sets.sumOf { effort ->
            val units = effort.holdSeconds
                ?.let { MovementDifficulty.holdRepEquivalents(it) }
                ?: effort.reps.toDouble()
            effortScore(units, effort.addedKg, bodyweightKg)
        }.roundToInt()
    }
}
