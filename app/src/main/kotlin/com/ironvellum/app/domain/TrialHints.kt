package com.ironvellum.app.domain

import com.ironvellum.app.R
import com.ironvellum.app.data.Words

/**
 * The one muted line under a set of a lift that has a top set: what the set is for, before it is logged. Pure, so the
 * words are tested; the open card draws what it returns, keyed by set id. Only sets still to log get one.
 */
object TrialHints {

    /**
     * @param sets the lift's sets in order, warm-ups included.
     * @param scheme the lift's scheme in the rite (a default top + back-offs one for a lift made top today).
     * @param lastTop the lift's last sealed top set, or null when it never had one.
     * @param wouldPeak whether the pending top set, as it stands, would be a new peak when logged.
     * @param figure how a load reads ("22 kg"); null reads "bodyweight".
     * @param hold whether the lift is timed, so its range reads in seconds.
     */
    fun forLift(
        sets: List<SessionSet>,
        scheme: SetScheme,
        lastTop: TopMark?,
        wouldPeak: Boolean,
        lift: LiftLoading,
        figure: (Double?) -> String,
        hold: Boolean = false,
    ): Map<Long, String> {
        val top = sets.firstOrNull { it.kind == SetKind.TOP } ?: return rangeHints(sets, scheme, hold)
        val out = mutableMapOf<Long, String>()
        if (top.isPending) {
            out[top.id] = when {
                lastTop != null && wouldPeak -> Words.text(
                    UiText.themed(R.string.trialhint_beat_peak, R.string.trialhint_beat_peak__t, lastTop.reps, figure(lastTop.weightKg)),
                )
                lastTop != null -> Words.text(UiText.res(R.string.trialhint_beat, lastTop.reps, figure(lastTop.weightKg)))
                else -> Words.text(UiText.res(R.string.trialhint_top_first))
            }
        }
        sets.filter { it.kind == SetKind.BACKOFF && it.isPending }.forEach { backoff ->
            out[backoff.id] = when {
                top.isPending -> Words.text(UiText.res(R.string.trialhint_backoff_fills))
                scheme.backoffFixedKg == null && lift.bodyweightLift && (top.weightKg ?: 0.0) <= 0.0 ->
                    Words.text(UiText.res(R.string.trialhint_backoff_fewer_reps))
                else -> SchemePlan.backoff(top.weightKg, top.reps, scheme, lift).rounding?.note
                    ?: Words.text(UiText.res(R.string.trialhint_backoff_from_top))
            }
        }
        return out + rangeHints(sets, scheme, hold)
    }

    /** "Aim for 10–15 reps" under each working set still to log, when the lift has a range of its own. */
    private fun rangeHints(sets: List<SessionSet>, scheme: SetScheme, hold: Boolean): Map<Long, String> {
        val range = scheme.repsRange(SetKind.WORKING) ?: return emptyMap()
        val aim = Words.text(UiText.res(if (hold) R.string.trialhint_aim_hold else R.string.trialhint_aim_reps, SchemeText.reps(range)))
        return sets.filter { it.kind == SetKind.WORKING && it.isPending }.associate { it.id to aim }
    }
}
