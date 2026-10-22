package com.ironvellum.app.domain

/** What one set adds to one muscle, in hard-set equivalents; [stretched] when the movement loaded the muscle long. */
data class MuscleDose(val muscle: Muscle, val dose: Double, val stretched: Boolean)

/**
 * The fatigue one logged set does to each muscle it works: `share x effort x stretch x systemic x novelty`.
 * Which sets count is [HardSets.isHard] exactly, so fatigue never disagrees with coverage; the shares are
 * [MuscleMap.profile] with the set's modifiers applied.
 */
object FatigueDose {

    /** How hard a set was: failure and rest-pause read highest, a rated set by reps left, an unrated one in between. */
    fun effort(set: LoggedSet): Double {
        if (set.kind == SetKind.FAILURE || set.kind == SetKind.REST_PAUSE) return FatigueTuning.EFFORT_FAILURE
        val rpe = set.rpe ?: return FatigueTuning.EFFORT_UNRATED
        return when (Effort.rir(rpe)) {
            0 -> FatigueTuning.EFFORT_RIR0
            1 -> FatigueTuning.EFFORT_RIR1
            2 -> FatigueTuning.EFFORT_RIR2
            else -> FatigueTuning.EFFORT_RIR3
        }
    }

    /** Fresh movements hit harder: none in the last 21 days 1.4, one earlier session 1.2, two or more 1.0. */
    fun novelty(priorExposures: Int): Double = when {
        priorExposures <= 0 -> FatigueTuning.NOVELTY_NEW
        priorExposures == 1 -> FatigueTuning.NOVELTY_SECOND
        else -> 1.0
    }

    fun dose(share: Double, effort: Double, stretch: Double, systemic: Double, novelty: Double): Double =
        share * effort * stretch * systemic * novelty

    /** Empty when the set is not hard or its movement has no profile. */
    fun perSet(set: LoggedSet, novelty: Double): List<MuscleDose> {
        if (!HardSets.isHard(set.kind, set.rpe, set.done, set.reps, set.weightKg, set.bodyweight)) return emptyList()
        val profile = MuscleMap.profile(set.exerciseName, set.modifiers) ?: return emptyList()
        val effort = effort(set)
        val stretchedMuscles = FatigueTraits.stretchedMuscles(set.exerciseName, set.modifiers, profile)
        val heavy = FatigueTraits.isHeavyWholeBody(set.exerciseName)
        val systemic = if (heavy) FatigueTuning.SYSTEMIC else 1.0
        val out = ArrayList<MuscleDose>()
        for ((muscle, share) in profile.muscles) {
            if (share <= 0.0) continue
            val stretched = muscle in stretchedMuscles
            val stretch = if (stretched) FatigueTuning.STRETCH else 1.0
            out += MuscleDose(muscle, dose(share, effort, stretch, systemic, novelty), stretched)
        }
        // A heavy squat or hinge loads the spine whether or not the catalogue credits the lower back.
        if (heavy && (profile.muscles[Muscle.LOWER_BACK] ?: 0.0) <= 0.0) {
            out += MuscleDose(Muscle.LOWER_BACK, dose(FatigueTuning.LOWER_BACK_BONUS_SHARE, effort, 1.0, 1.0, novelty), false)
        }
        return out
    }
}
