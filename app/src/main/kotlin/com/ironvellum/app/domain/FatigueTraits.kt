package com.ironvellum.app.domain

/** How fast a muscle sheds fatigue: the time for the residual to halve. */
enum class RecoveryClass(val halfLifeHours: Double) {
    FAST(FatigueTuning.HALF_LIFE_FAST_H),
    MEDIUM(FatigueTuning.HALF_LIFE_MEDIUM_H),
    SLOW(FatigueTuning.HALF_LIFE_SLOW_H),
}

fun Muscle.recoveryClass(): RecoveryClass = when (this) {
    Muscle.CALVES, Muscle.FOREARMS, Muscle.ABS, Muscle.OBLIQUES, Muscle.TIBIALIS, Muscle.SIDE_DELTS,
    Muscle.REAR_DELTS, Muscle.ROTATOR_CUFF, Muscle.NECK,
    -> RecoveryClass.FAST
    Muscle.BICEPS, Muscle.BRACHIALIS, Muscle.TRICEPS, Muscle.LATS, Muscle.RHOMBOIDS, Muscle.TRAPS,
    Muscle.FRONT_DELTS, Muscle.SERRATUS, Muscle.ADDUCTORS, Muscle.ABDUCTORS, Muscle.HIP_FLEXORS,
    -> RecoveryClass.MEDIUM
    Muscle.QUADS, Muscle.HAMSTRINGS, Muscle.GLUTES, Muscle.UPPER_CHEST, Muscle.MID_CHEST,
    Muscle.LOWER_CHEST, Muscle.LOWER_BACK,
    -> RecoveryClass.SLOW
}

/**
 * What makes a movement more fatiguing than its sets alone say. Fatigue-only data keyed by catalogue name:
 * [ExerciseProfile.stretchBias] belongs to the plan generator and is deliberately not read here, so tuning
 * fatigue never moves a plan. Custom exercises carry no traits.
 */
object FatigueTraits {

    /** Movements that load a muscle long, by lowercase catalogue name. */
    val STRETCH_TABLE: Map<String, Set<Muscle>> = mapOf(
        "romanian deadlift" to setOf(Muscle.HAMSTRINGS),
        "single-leg romanian deadlift" to setOf(Muscle.HAMSTRINGS),
        "good morning" to setOf(Muscle.HAMSTRINGS),
        "bulgarian split squat" to setOf(Muscle.QUADS, Muscle.GLUTES),
        "hack squat" to setOf(Muscle.QUADS),
        "nordic curl" to setOf(Muscle.HAMSTRINGS),
        "lying leg curl" to setOf(Muscle.HAMSTRINGS),
        "seated leg curl" to setOf(Muscle.HAMSTRINGS),
        "dumbbell pullover" to setOf(Muscle.LATS),
        "cable fly" to setOf(Muscle.MID_CHEST),
        "overhead cable extension" to setOf(Muscle.TRICEPS),
        "preacher curl" to setOf(Muscle.BICEPS),
        "standing calf raise" to setOf(Muscle.CALVES),
    )

    /** Axially loaded barbell squats and hinges, by lowercase catalogue name. */
    val HEAVY_WHOLE_BODY: Set<String> = setOf(
        "back squat", "front squat", "pause squat", "heavy squat",
        "deadlift", "sumo deadlift", "heavy deadlift", "volume deadlift",
        "romanian deadlift", "good morning",
    )

    private const val DEFICIT = "deficit"

    private fun key(exerciseName: String) = exerciseName.trim().lowercase()

    /**
     * The muscles [exerciseName] loads long: its table entry, plus the main muscles of [profile] when
     * [modifiers] carry a deficit. Empty for a custom exercise.
     */
    fun stretchedMuscles(exerciseName: String, modifiers: String, profile: ExerciseProfile): Set<Muscle> {
        if (CustomExercises.isCustom(exerciseName)) return emptySet()
        val fromTable = STRETCH_TABLE[key(exerciseName)].orEmpty()
        if (DEFICIT !in Variation.key(modifiers).split(",")) return fromTable
        val main = profile.muscles.filterValues { it >= FatigueTuning.MAIN_SHARE - 1e-9 }.keys
        return fromTable + main
    }

    /** True for a heavy whole-body lift; false for a custom exercise. */
    fun isHeavyWholeBody(exerciseName: String): Boolean =
        !CustomExercises.isCustom(exerciseName) && key(exerciseName) in HEAVY_WHOLE_BODY
}
