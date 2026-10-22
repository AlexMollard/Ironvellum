package com.ironvellum.app.domain

/** One lift on the records board: its best estimated 1RM and how that has moved. */
data class LiftRecord(
    val name: String,
    /** [Variation.key] of the movement; "" for the plain lift. A deficit and a flat calf raise are two rows. */
    val variation: String = "",
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
) {
    /** The lift as the board names it: "Single-Leg Calf Raise · deficit". */
    val label: String get() = Variation.label(name, variation)
}

/**
 * The Ledger's lift-records board. Estimated one-rep maxes by the same Epley rule and 12-rep cap the strength
 * profile and the lift boards use. A barbell or machine lift is judged on its marked load. A bodyweight lift is
 * judged on the TOTAL it moved (its [BodyShare] of the body weight at the time, plus the added kilos) and shown as
 * the ADDED-load equivalent, so a pull-up's "+46 kg" reads on the scale of its plates ([BodyShare.e1rmAddedKg]).
 * Records already awarded are never re-read: only the figures shown and the comparisons made from now on. Pure.
 */
object LiftRecords {
    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** Fresh enough to wear the PR tag. */
    const val RECENT_DAYS = 14

    /** One lift's best estimated 1RM within one trial: the set that set it and how the lift is named. */
    class SessionBest(
        val display: String,
        val variation: String,
        val e1rmKg: Double,
        val set: SessionSet,
        val addsToBody: Boolean,
    )

    /**
     * Each lift's best estimated 1RM in one trial, by [Variation.liftKey]: the one rule the board and the period
     * records ([gains]) share, so what counts as a record cannot drift between them. A lift with a top set is a
     * record on that set alone. [bodyweightKg] is the lifter's weight at the time of the trial (null when unknown:
     * a bodyweight lift then falls back to its added load alone).
     */
    fun sessionBests(sets: List<SessionSet>, exercises: Map<Long, Exercise>, bodyweightKg: Double?): Map<String, SessionBest> {
        val best = LinkedHashMap<String, SessionBest>()
        for (set in PeakSets.narrowed(sets)) {
            val weight = set.weightKg ?: continue
            if (weight <= 0.0) continue
            if (set.reps < 1 || set.reps > ProgramRules.MAX_E1RM_REPS) continue
            val ex = exercises[set.exerciseId]
            if ((ex?.metric ?: ExerciseMetric.REPS) != ExerciseMetric.REPS) continue
            val name = set.exerciseName.ifEmpty { ex?.name ?: "" }.trim()
            if (name.isEmpty()) continue
            val key = Variation.liftKey(name, set.modifiers)
            val share = ex?.let { BodyShare.of(it, set.modifiers) } ?: 0.0
            val e1rm = BodyShare.e1rmAddedKg(weight, set.reps, share, bodyweightKg)
            val held = best[key]
            if (held == null || e1rm > held.e1rmKg) {
                best[key] = SessionBest(held?.display ?: name, held?.variation ?: Variation.key(set.modifiers), e1rm, set, ex?.let(BodyShare::addsToBody) == true)
            }
        }
        return best
    }

    fun board(
        sessions: List<WorkoutSession>,
        sessionSets: Map<Long, List<SessionSet>>,
        exercises: Map<Long, Exercise>,
        bodyweightAt: (Long) -> Double,
        nowMs: Long,
        windowDays: Int = 90,
        limit: Int = 8,
    ): List<LiftRecord> {
        class Peak(val at: Long, val e1rm: Double, val set: SessionSet, val addsToBody: Boolean)
        class Acc(var display: String, val variation: String) {
            val perSession = mutableListOf<Peak>()
        }
        val byLift = LinkedHashMap<String, Acc>()
        for (session in sessions.sortedBy { it.startedAtMs }) {
            val at = session.completedAtMs ?: session.startedAtMs
            val bodyweight = bodyweightAt(session.startedAtMs).takeIf { it > 0.0 }
            for ((key, best) in sessionBests(sessionSets[session.id].orEmpty(), exercises, bodyweight)) {
                val acc = byLift.getOrPut(key) { Acc(best.display, best.variation) }
                acc.perSession += Peak(at, best.e1rmKg, best.set, best.addsToBody)
            }
        }
        val cutoff = nowMs - windowDays * DAY_MS
        return byLift.values.map { acc ->
            val best = acc.perSession.maxOf { it.e1rm }
            val peak = acc.perSession.last { it.e1rm == best }
            val before = acc.perSession.filter { it.at <= cutoff }.maxOfOrNull { it.e1rm }
            LiftRecord(
                name = acc.display,
                variation = acc.variation,
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

    /** A lift whose best estimated 1RM went up inside a period: the new best, by how much, and the set that made it. */
    data class RecordGain(
        val name: String,
        val variation: String,
        val e1rmKg: Double,
        val gainKg: Double,
        val set: SessionSet,
        val addsToBody: Boolean,
        val atMs: Long,
        /** The estimated 1RM before the period, then the best of each trial inside it, oldest first: a sparkline's points. */
        val series: List<Double> = emptyList(),
    ) {
        val label: String get() = Variation.label(name, variation)
    }

    /** A lift whose first trial fell inside a period: it has a best but nothing before it to be a gain over. */
    data class NewLift(
        val name: String,
        val variation: String,
        val e1rmKg: Double,
        val set: SessionSet,
        val addsToBody: Boolean,
        val atMs: Long,
        /** The best of each trial inside the period, oldest first. */
        val series: List<Double> = emptyList(),
    ) {
        val label: String get() = Variation.label(name, variation)
    }

    /** A rise smaller than this does not print as a gain (it would read "+0.0 kg"). */
    private const val MIN_GAIN_KG = 0.05

    private class Held(var best: SessionBest, var at: Long, val series: MutableList<Double> = mutableListOf())

    /** What [gains] and [newLifts] both read: each lift's best before [fromMs], and its trials inside [fromMs, toMs). */
    private class Scan(val before: Map<String, Double>, val inside: Map<String, Held>)

    private fun scan(
        history: List<Pair<WorkoutSession, List<SessionSet>>>,
        exercises: Map<Long, Exercise>,
        bodyweightAt: (Long) -> Double,
        fromMs: Long,
        toMs: Long,
    ): Scan {
        val before = HashMap<String, Double>()
        val inside = LinkedHashMap<String, Held>()
        for ((session, sets) in history.sortedBy { it.first.completedAtMs ?: it.first.startedAtMs }) {
            val at = session.completedAtMs ?: session.startedAtMs
            if (at >= toMs) break
            val bodyweight = bodyweightAt(session.startedAtMs).takeIf { it > 0.0 }
            for ((key, best) in sessionBests(sets, exercises, bodyweight)) {
                if (at < fromMs) {
                    if (best.e1rmKg > (before[key] ?: 0.0)) before[key] = best.e1rmKg
                } else {
                    val held = inside.getOrPut(key) { Held(best, at) }
                    held.series += best.e1rmKg
                    if (best.e1rmKg > held.best.e1rmKg) {
                        held.best = best
                        held.at = at
                    }
                }
            }
        }
        return Scan(before, inside)
    }

    /**
     * The lifts that set a new best estimated 1RM in [fromMs, toMs): the period's best beats the best of every trial
     * before [fromMs]. A lift with nothing before the period is history starting, not a record ([isFresh] says the
     * same): [newLifts] lists those. One row per lift, in the order the bests were set. A recap's "personal records" and
     * the month and year reviews read the period through this, so a "record" means one thing.
     */
    fun gains(
        history: List<Pair<WorkoutSession, List<SessionSet>>>,
        exercises: Map<Long, Exercise>,
        bodyweightAt: (Long) -> Double,
        fromMs: Long,
        toMs: Long,
    ): List<RecordGain> {
        val scan = scan(history, exercises, bodyweightAt, fromMs, toMs)
        return scan.inside.mapNotNull { (key, held) ->
            val prior = scan.before[key] ?: return@mapNotNull null
            val gain = held.best.e1rmKg - prior
            if (gain < MIN_GAIN_KG) return@mapNotNull null
            RecordGain(
                held.best.display, held.best.variation, held.best.e1rmKg, gain, held.best.set, held.best.addsToBody, held.at,
                listOf(prior) + held.series,
            )
        }.sortedBy { it.atMs }
    }

    /**
     * The lifts first trained inside [fromMs, toMs): they have a best estimated 1RM in the period and none before it,
     * the same key and the same rule [gains] uses, so a lift is a gain or new, never both. The first trial of an
     * exercise that carries no load (or a rep count past the cap) has no e1RM and is not here.
     */
    fun newLifts(
        history: List<Pair<WorkoutSession, List<SessionSet>>>,
        exercises: Map<Long, Exercise>,
        bodyweightAt: (Long) -> Double,
        fromMs: Long,
        toMs: Long,
    ): List<NewLift> {
        val scan = scan(history, exercises, bodyweightAt, fromMs, toMs)
        return scan.inside
            .filterKeys { it !in scan.before }
            .map { (_, held) ->
                NewLift(held.best.display, held.best.variation, held.best.e1rmKg, held.best.set, held.best.addsToBody, held.at, held.series.toList())
            }
            .sortedBy { it.atMs }
    }

    /**
     * Wears the PR tag: the lift's LATEST trial set a best that beats every earlier trial, inside the last
     * [RECENT_DAYS] days. A lift's first trial is history starting, and a best set a few trials back is not news.
     */
    fun isFresh(record: LiftRecord, nowMs: Long): Boolean =
        nowMs - record.bestAtMs <= RECENT_DAYS * DAY_MS &&
            record.bestAtMs == record.lastAtMs &&
            record.series.size >= 2 &&
            record.series.last() > record.series.dropLast(1).max()
}
