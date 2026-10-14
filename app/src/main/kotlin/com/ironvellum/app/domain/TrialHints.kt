package com.ironvellum.app.domain

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
     */
    fun forLift(
        sets: List<SessionSet>,
        scheme: SetScheme,
        lastTop: TopMark?,
        wouldPeak: Boolean,
        lift: LiftLoading,
        figure: (Double?) -> String,
    ): Map<Long, String> {
        val top = sets.firstOrNull { it.kind == SetKind.TOP } ?: return dropHints(sets)
        val out = mutableMapOf<Long, String>()
        val rep = { reps: Int, kg: Double? -> "$reps × ${figure(kg)}" }
        if (top.isPending) {
            val beat = lastTop?.let { "Beat ${rep(it.reps, it.weightKg)}" }
            out[top.id] = when {
                beat != null && wouldPeak -> "$beat · this would be a new peak"
                beat != null -> beat
                else -> "Your top set: as heavy as it goes with clean reps"
            }
        }
        sets.filter { it.kind == SetKind.BACKOFF && it.isPending }.forEach { backoff ->
            out[backoff.id] = when {
                top.isPending -> "Fills from your top set"
                scheme.backoffFixedKg == null && lift.bodyweightLift && (top.weightKg ?: 0.0) <= 0.0 ->
                    "Two fewer reps than your top set"
                else -> SchemePlan.backoff(top.weightKg, top.reps, scheme, lift).rounding?.note ?: "From your top set"
            }
        }
        return out + dropHints(sets)
    }

    private fun dropHints(sets: List<SessionSet>): Map<Long, String> =
        sets.filter { it.kind == SetKind.DROP && it.isPending }.associate { it.id to "Straight on, no rest" }
}
