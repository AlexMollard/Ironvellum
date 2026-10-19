package com.ironvellum.app.domain

import androidx.annotation.StringRes
import com.ironvellum.app.R
import com.ironvellum.app.data.Words

/** The scheme the Sets sheet marks SUGGESTED for a lift, with the one line that says why. */
data class SuggestedScheme(val type: SchemeType, val reason: String)

private fun reason(@StringRes id: Int, @StringRes themed: Int = id): String = Words.text(UiText.themed(id, themed))

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
                SuggestedScheme(SchemeType.STRAIGHT, reason(R.string.scheme_suggestion_mobility))
            MuscleMap.isControl(exerciseName) || MuscleMap.isTechnique(exerciseName) ->
                SuggestedScheme(SchemeType.STRAIGHT, reason(R.string.scheme_suggestion_technique, R.string.scheme_suggestion_technique__t))
            bodyweightLift && oneSided ->
                SuggestedScheme(SchemeType.STRAIGHT, reason(R.string.scheme_suggestion_balance))
            profile?.compound == true ->
                SuggestedScheme(SchemeType.TOP_BACKOFF, reason(R.string.scheme_suggestion_compound))
            !inSuperset && MovementDifficulty.isIsolation(exerciseName) ->
                SuggestedScheme(SchemeType.DROPS, reason(R.string.scheme_suggestion_isolation))
            else -> SuggestedScheme(SchemeType.STRAIGHT, reason(R.string.scheme_suggestion_steady))
        }
    }
}
