package com.ironvellum.app.domain

/** The scheme the Sets sheet marks SUGGESTED for a lift, with the one line that says why. */
data class SuggestedScheme(val type: SchemeType, val reason: String)

object SchemeSuggestion {
    /**
     * What suits [exerciseName]: a heavy compound earns a top set and back-offs, an isolation lift a drop set to
     * finish on, anything else plain straight sets. Work that is not a dose of load is straight first of all: a
     * mobility drill, a control or technique movement, and a bodyweight lift done on one limb (the single-leg squat
     * chain, the archers), whose limit is balance and technique, not how heavy the set can be taken. Judged from
     * the movement alone ([bodyweightLift] says its load is the body), so it is the same on every device and needs no
     * history; [inSuperset] steers away from drops, which a superset cannot hold.
     */
    fun forLift(exerciseName: String, inSuperset: Boolean, bodyweightLift: Boolean = false): SuggestedScheme {
        val profile = MuscleMap.profile(exerciseName)
        val oneSided = profile?.unilateral == true || profile?.pattern == MovementPattern.LUNGE
        return when {
            MuscleMap.isMobility(exerciseName) ->
                SuggestedScheme(SchemeType.STRAIGHT, "Mobility drill: the same easy sets, never taken heavy or to a drop.")
            MuscleMap.isControl(exerciseName) || MuscleMap.isTechnique(exerciseName) ->
                SuggestedScheme(SchemeType.STRAIGHT, "Technique-led: clean, even sets beat a heavy top set here.")
            bodyweightLift && oneSided ->
                SuggestedScheme(SchemeType.STRAIGHT, "Balance-limited on one side: even sets keep the form, not a heavy top set.")
            profile?.compound == true ->
                SuggestedScheme(SchemeType.TOP_BACKOFF, "Heavy compound lift: one best set, then lighter work behind it.")
            !inSuperset && MovementDifficulty.isIsolation(exerciseName) ->
                SuggestedScheme(SchemeType.DROPS, "Isolation lift: a drop set finishes the muscle without a heavy load.")
            else -> SuggestedScheme(SchemeType.STRAIGHT, "Steady, even sets keep this one simple.")
        }
    }
}
