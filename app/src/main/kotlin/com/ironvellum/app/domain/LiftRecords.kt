package com.ironvellum.app.domain

/** One lift on the records board: its best estimated 1RM and how that has moved. */
data class LiftRecord(
    val name: String,
    val bestE1rmKg: Double,
    /** When the best was set (the latest session that reached it). */
    val bestAtMs: Long,
    /** Best e1RM of each session that trained the lift, oldest first. */
    val series: List<Double>,
    /** Best now minus the best as of the window start; null when the lift has no history that old. */
    val deltaKg: Double?,
    val lastAtMs: Long,
    /** The set that set [bestE1rmKg]: the actual weight and reps, for showing the peak as it was lifted. */
    val bestSet: SessionSet? = null,
    /** The lift is a bodyweight movement (catalogue not weighted), so [bestSet]'s load is ADDED to the body. */
    val bestSetAddsToBody: Boolean = false,
)

/**
 * The Ledger's lift-records board. Estimated one-rep maxes of the MARKED load
 * (a pull-up's added kilos, not bodyweight plus them), by the same Epley rule
 * and 12-rep cap the strength profile and the lift boards use. Pure.
 */
object LiftRecords {
    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** Fresh enough to wear the PR tag. */
    const val RECENT_DAYS = 14

    fun board(
        sessions: List<WorkoutSession>,
        sessionSets: Map<Long, List<SessionSet>>,
        exercises: Map<Long, Exercise>,
        nowMs: Long,
        windowDays: Int = 90,
        limit: Int = 8,
    ): List<LiftRecord> {
        class Peak(val at: Long, val e1rm: Double, val set: SessionSet, val addsToBody: Boolean)
        class Acc(var display: String) {
            val perSession = mutableListOf<Peak>()
        }
        val byLift = LinkedHashMap<String, Acc>()
        for (session in sessions.sortedBy { it.startedAtMs }) {
            val at = session.completedAtMs ?: session.startedAtMs
            val best = HashMap<String, Double>()
            val bestSet = HashMap<String, Pair<SessionSet, Boolean>>()
            val shown = HashMap<String, String>()
            for (set in sessionSets[session.id].orEmpty()) {
                if (!set.done) continue
                val weight = set.weightKg ?: continue
                if (weight <= 0.0) continue
                if (set.reps < 1 || set.reps > ProgramRules.MAX_E1RM_REPS) continue
                val ex = exercises[set.exerciseId]
                if ((ex?.metric ?: ExerciseMetric.REPS) != ExerciseMetric.REPS) continue
                val name = set.exerciseName.ifEmpty { ex?.name ?: "" }.trim()
                if (name.isEmpty()) continue
                val key = name.lowercase()
                val e1rm = ProgramRules.epley(weight, set.reps)
                if (e1rm > (best[key] ?: 0.0)) {
                    best[key] = e1rm
                    bestSet[key] = set to (ex?.isWeighted == false)
                }
                shown.putIfAbsent(key, name)
            }
            for ((key, e1rm) in best) {
                val acc = byLift.getOrPut(key) { Acc(shown.getValue(key)) }
                acc.perSession += Peak(at, e1rm, bestSet.getValue(key).first, bestSet.getValue(key).second)
            }
        }
        val cutoff = nowMs - windowDays * DAY_MS
        return byLift.values.map { acc ->
            val best = acc.perSession.maxOf { it.e1rm }
            val peak = acc.perSession.last { it.e1rm == best }
            val before = acc.perSession.filter { it.at <= cutoff }.maxOfOrNull { it.e1rm }
            LiftRecord(
                name = acc.display,
                bestE1rmKg = best,
                bestAtMs = peak.at,
                series = acc.perSession.map { it.e1rm },
                deltaKg = before?.let { best - it },
                lastAtMs = acc.perSession.last().at,
                bestSet = peak.set,
                bestSetAddsToBody = peak.addsToBody,
            )
        }.sortedByDescending { it.lastAtMs }.take(limit)
    }

    /** A record set inside the last [RECENT_DAYS] days. */
    fun isFresh(record: LiftRecord, nowMs: Long): Boolean = nowMs - record.bestAtMs <= RECENT_DAYS * DAY_MS
}
