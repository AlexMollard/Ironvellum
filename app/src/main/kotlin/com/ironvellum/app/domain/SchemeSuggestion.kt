package com.ironvellum.app.domain

/** The scheme the Sets sheet marks SUGGESTED for a lift, with the one line that says why. */
data class SuggestedScheme(val type: SchemeType, val reason: String)

object SchemeSuggestion {
    /**
     * What suits [exerciseName]: a heavy compound earns a top set and back-offs, an isolation lift a drop set to
     * finish on, anything else plain straight sets. Judged from the movement alone, so it is the same on every
     * device and needs no history; [inSuperset] steers away from drops, which a superset cannot hold.
     */
    fun forLift(exerciseName: String, inSuperset: Boolean): SuggestedScheme {
        val compound = MuscleMap.profile(exerciseName)?.compound == true
        return when {
            compound -> SuggestedScheme(SchemeType.TOP_BACKOFF, "Heavy compound lift: one best set, then lighter work behind it.")
            !inSuperset && MovementDifficulty.isIsolation(exerciseName) ->
                SuggestedScheme(SchemeType.DROPS, "Isolation lift: a drop set finishes the muscle without a heavy load.")
            else -> SuggestedScheme(SchemeType.STRAIGHT, "Steady, even sets keep this one simple.")
        }
    }
}
