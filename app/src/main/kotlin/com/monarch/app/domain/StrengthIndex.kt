package com.monarch.app.domain

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Body-scaled strength scoring. A rep of bodyweight work scores
 * `reps * (bodyweight + added kg) / bodyweight^0.67` — allometric exponent
 * 2/3, the standard strength-vs-mass scaling. Heavier hunters must move more
 * absolute load for the same score, so milestones compare athletes fairly.
 *
 * v1 assumptions (deliberate): every bodyweight rep moves ~full bodyweight;
 * height is not separately compensated (it correlates with mass).
 * Shortcut noted here; revisit per-movement coefficients if milestones feel off.
 */
object StrengthIndex {

    const val MASS_EXPONENT = 0.67

    fun repScore(reps: Int, addedKg: Double?, bodyweightKg: Double): Double {
        if (reps <= 0 || bodyweightKg <= 0.0) return 0.0
        val load = bodyweightKg + (addedKg ?: 0.0).coerceAtLeast(0.0)
        return reps * load / bodyweightKg.pow(MASS_EXPONENT)
    }

    /** Total score for a session's done sets; null when bodyweight is unknown. */
    fun sessionScore(sets: List<Pair<Int, Double?>>, bodyweightKg: Double?): Int? {
        if (bodyweightKg == null || bodyweightKg <= 0.0) return null
        return sets.sumOf { (reps, added) -> repScore(reps, added, bodyweightKg) }.roundToInt()
    }
}
