package com.ironvellum.app.domain

/** One workout as the fatigue model reads it: when it counts from (`completedAtMs ?: startedAtMs`, a live one is now) and its sets. */
data class FatigueSession(val sessionId: Long, val atMs: Long, val sets: List<LoggedSet>)

/** What one lift in one session did to one muscle, kept with its source so the breakdown can rank causes. */
data class FatigueContribution(
    val muscle: Muscle,
    val sessionId: Long,
    val sessionAtMs: Long,
    val exerciseName: String,
    val modifiers: String,
    val liftKey: String,
    /** Summed dose of this (session, lift, muscle), in hard-set equivalents. */
    val dose: Double,
    /** The part of [dose] from sets that were stretch-loaded or new to the lifter: what soreness is read from. */
    val qualifyingDose: Double,
    val stretched: Boolean,
    val novel: Boolean,
    /** The class half-life with the big-session and new-lifter stretches applied. */
    val halfLifeHours: Double,
)

/** Every muscle's contributions from the last seven days, and the one residual rule everything shown is read from. */
class FatigueReport(
    val nowMs: Long,
    val newLifter: Boolean,
    val contributions: Map<Muscle, List<FatigueContribution>>,
) {
    /** Residual fatigue of [muscle] at [atMs], in hard-set equivalents: each contribution halves every half-life. */
    fun residual(muscle: Muscle, atMs: Long = nowMs): Double =
        contributions[muscle].orEmpty().sumOf { it.residualAt(atMs) }
}

internal fun FatigueContribution.residualAt(atMs: Long): Double =
    dose * Math.pow(0.5, (atMs - sessionAtMs) / MS_PER_HOUR / halfLifeHours)

private const val MS_PER_HOUR = 3_600_000.0
private const val MS_PER_DAY = 24L * 3_600_000L

object FatigueModel {

    /**
     * The fatigue report at [nowMs]. [sessions] must cover the 28 days before it; only sessions in
     * `(now - 7 days, now]` are kept as contributions, the older ones feed novelty alone. [firstWorkoutMs] is the
     * earliest workout of any origin. A new lifter is one who said so ([LiftingExperience.UNDER_TWO_MONTHS]) and whose
     * first workout is under [FatigueTuning.NEW_LIFTER_WEEKS] weeks old (null, nothing logged, counts), so the
     * slowdown ends by itself. Unanswered is experienced: the first workout in the app proves nothing about the lifter.
     */
    fun estimate(
        sessions: List<FatigueSession>,
        nowMs: Long,
        firstWorkoutMs: Long?,
        experience: LiftingExperience = LiftingExperience.UNANSWERED,
    ): FatigueReport {
        val newLifter = experience == LiftingExperience.UNDER_TWO_MONTHS &&
            (firstWorkoutMs == null || firstWorkoutMs > nowMs - FatigueTuning.NEW_LIFTER_WEEKS * 7 * MS_PER_DAY)
        val exposures = exposureIndex(sessions)
        val windowStart = nowMs - FatigueTuning.WINDOW_DAYS * MS_PER_DAY
        val byMuscle = Muscle.entries.associateWith { ArrayList<FatigueContribution>() }

        for (session in sessions.filter { it.atMs > windowStart && it.atMs <= nowMs }.sortedBy { it.atMs }) {
            val perMuscle = LinkedHashMap<Muscle, MutableList<Draft>>()
            val lifts = LinkedHashMap<String, MutableList<LoggedSet>>()
            for (set in session.sets) lifts.getOrPut(Variation.liftKey(set.exerciseName, set.modifiers)) { ArrayList() } += set
            for ((liftKey, sets) in lifts) {
                val novelty = FatigueDose.novelty(countBefore(exposures[liftKey], session.atMs))
                val drafts = LinkedHashMap<Muscle, Draft>()
                for (set in sets) for (part in FatigueDose.perSet(set, novelty)) {
                    val draft = drafts.getOrPut(part.muscle) { Draft(sets.first(), liftKey, novelty) }
                    draft.dose += part.dose
                    if (part.stretched || novelty > 1.0) draft.qualifying += part.dose
                    draft.stretched = draft.stretched || part.stretched
                }
                for ((muscle, draft) in drafts) perMuscle.getOrPut(muscle) { ArrayList() } += draft
            }
            for ((muscle, drafts) in perMuscle) {
                val big = drafts.sumOf { it.dose } > FatigueTuning.BIG_SESSION_DOSE
                for (draft in drafts) {
                    var halfLife = muscle.recoveryClass().halfLifeHours
                    if (big) halfLife *= FatigueTuning.BIG_SESSION_STRETCH
                    if (newLifter) halfLife *= FatigueTuning.NEW_LIFTER_STRETCH
                    byMuscle.getValue(muscle) += FatigueContribution(
                        muscle = muscle,
                        sessionId = session.sessionId,
                        sessionAtMs = session.atMs,
                        exerciseName = draft.first.exerciseName,
                        modifiers = draft.first.modifiers,
                        liftKey = draft.liftKey,
                        dose = draft.dose,
                        qualifyingDose = draft.qualifying,
                        stretched = draft.stretched,
                        novel = draft.novelty > 1.0,
                        halfLifeHours = halfLife,
                    )
                }
            }
        }
        return FatigueReport(nowMs, newLifter, byMuscle)
    }

    /**
     * Distinct earlier sessions in the 21 days before [beforeMs] with a done, non-warm-up set of [liftKey]. A light
     * set still counts as having done the lift: novelty is about familiarity, not effort.
     */
    fun priorExposures(sessions: List<FatigueSession>, liftKey: String, beforeMs: Long): Int =
        countBefore(exposureIndex(sessions)[liftKey], beforeMs)

    private class Draft(val first: LoggedSet, val liftKey: String, val novelty: Double) {
        var dose = 0.0
        var qualifying = 0.0
        var stretched = false
    }

    /** Each lift's session times, one per session that did it, so novelty is one lookup per lift and not a history scan. */
    private fun exposureIndex(sessions: List<FatigueSession>): Map<String, List<Long>> {
        val index = HashMap<String, MutableList<Long>>()
        for (session in sessions) {
            val keys = session.sets.filter { it.done && it.kind.countsAsWork }
                .mapTo(HashSet()) { Variation.liftKey(it.exerciseName, it.modifiers) }
            for (key in keys) index.getOrPut(key) { ArrayList() } += session.atMs
        }
        return index
    }

    private fun countBefore(times: List<Long>?, beforeMs: Long): Int {
        val from = beforeMs - FatigueTuning.NOVELTY_WINDOW_DAYS * MS_PER_DAY
        return times.orEmpty().count { it < beforeMs && it > from }
    }
}
