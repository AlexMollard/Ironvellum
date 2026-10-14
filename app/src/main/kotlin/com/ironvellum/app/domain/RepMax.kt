package com.ironvellum.app.domain

import kotlin.math.roundToInt

/** Movement (lowercased, trimmed name) to every working set done before a trial: what [RepMax] judges against. */
typealias RepBook = Map<String, List<RepMax.Mark>>

/**
 * The one peak rule, shared by the live badges, the victory panel, the XP bonus, the share card and Today.
 * Judged against the lifter's history before the trial only: beating your own earlier set in the same
 * trial is never a peak.
 *
 * Two kinds:
 *  - HEAVIER (force per rep): the set's load beats the best load ever lifted on the movement for the same or
 *    more reps (when nothing was ever done for that many reps, the heaviest load there was), and the set has at
 *    least [MIN_PEAK_REPS] reps. 17.7 kg x 4 beats a best of 15.2 kg x 5; a heavy single or double never counts.
 *  - MORE_REPS: more reps than ever at the same or a greater load. Holds read seconds. A bodyweight-only lift has
 *    load 0, so it is judged on reps alone.
 *
 * The first time a movement is lifted is history starting, not a peak. Pure.
 */
object RepMax {

    /** One earlier set of a movement: reps (seconds for a hold) and the marked load, 0 when none. */
    data class Mark(val figure: Int, val weightKg: Double)

    enum class Kind(val label: String) { HEAVIER("heavier"), MORE_REPS("more reps") }

    /** A set that peaked, the kind of peak and the earlier best it passed. */
    data class Hit(val set: SessionSet, val kind: Kind, val was: Mark)

    /** A heavier peak needs at least this many reps: a heavy single or double never counts. */
    const val MIN_PEAK_REPS = 3

    /** Jump in total load (as a fraction) that pays the full heavier-peak bonus on a compound lift. */
    const val FULL_AT_COMPOUND = 0.025

    /** The same on an isolation or small-muscle lift (curls, raises, flies). */
    const val FULL_AT_ISOLATION = 0.05

    /** A hold's extra seconds, as a fraction of the old best, that pays the full more-reps bonus. */
    // shortcut: holds scale by a flat 10% of the old best; tune with real hold history if it pays too freely.
    const val FULL_AT_HOLD_GAIN = 0.10

    private const val LOAD_EPSILON = 0.0005

    fun key(exerciseName: String): String = exerciseName.lowercase().trim()

    fun loadOf(set: SessionSet): Double = (set.weightKg ?: 0.0).coerceAtLeast(0.0)

    fun figureOf(set: SessionSet, hold: Boolean): Int = if (hold) (set.durationSec ?: 0) else set.reps

    /**
     * Everything lifted before a trial. Completed work only, warm-ups never, activities never.
     *
     * @param excludeSessionId a trial is never its own benchmark.
     * @param beforeMs when set, only sessions completed (started, if never completed) strictly before it count.
     */
    fun book(
        history: List<Pair<WorkoutSession, List<SessionSet>>>,
        excludeSessionId: Long? = null,
        beforeMs: Long? = null,
        metricOf: (SessionSet) -> ExerciseMetric? = { null },
    ): RepBook {
        val out = HashMap<String, MutableList<Mark>>()
        for ((session, sets) in history) {
            if (excludeSessionId != null && session.id == excludeSessionId) continue
            if (beforeMs != null && (session.completedAtMs ?: session.startedAtMs) >= beforeMs) continue
            record(out, sets, metricOf)
        }
        return out
    }

    /** Adds one trial's done working strength sets to [book]. */
    private fun record(book: MutableMap<String, MutableList<Mark>>, sets: List<SessionSet>, metricOf: (SessionSet) -> ExerciseMetric?) {
        // A lift with a top set is judged on that set alone: its back-offs are lighter by design.
        for (set in PeakSets.narrowed(sets)) {
            val metric = metricOf(set)
            if (metric != null && !metric.isStrength) continue
            val hold = MovementDifficulty.isHoldSet(metric, set.exerciseName, set.modifiers)
            book.getOrPut(key(set.exerciseName)) { mutableListOf() } += Mark(figureOf(set, hold), loadOf(set))
        }
    }

    /** What kind of peak a set of [figure] at [weightKg] is against [history] (non-empty), and the earlier best it passed; null when it is none. */
    fun judge(figure: Int, weightKg: Double, hold: Boolean, history: List<Mark>): Pair<Kind, Mark>? {
        if (figure <= 0 || history.isEmpty()) return null
        if (!hold && figure >= MIN_PEAK_REPS) {
            val atReps = history.filter { it.figure >= figure }.ifEmpty { history }
            val ref = atReps.maxOf { it.weightKg }
            if (weightKg > ref + LOAD_EPSILON) {
                // Of the sets at the best load, the one with the fewest reps still enough: "the previous best at these reps".
                val was = atReps.filter { it.weightKg >= ref - LOAD_EPSILON }.minBy { it.figure }
                return Kind.HEAVIER to was
            }
        }
        val atLoad = history.filter { it.weightKg >= weightKg - LOAD_EPSILON }
        if (atLoad.isNotEmpty()) {
            val most = atLoad.maxOf { it.figure }
            if (figure > most) {
                return Kind.MORE_REPS to atLoad.filter { it.figure == most }.minBy { it.weightKg }
            }
        }
        return null
    }

    /** The done working sets of ONE movement that are peaks, judged against [history] alone ([PeakSets] says which sets are judged). */
    fun hits(block: List<SessionSet>, history: List<Mark>, hold: Boolean): List<Hit> =
        PeakSets.eligible(block).sortedBy { it.setIndex }.mapNotNull { set ->
            judge(figureOf(set, hold), loadOf(set), hold, history)?.let { (kind, was) -> Hit(set, kind, was) }
        }

    /**
     * What a peak pays, [Xp.PEAK_BONUS] at most, by how meaningful the jump is, so creeping a fraction of a kilo
     * cannot farm it. A heavier peak scales linearly with the jump in TOTAL load (a body-weight lift counts the
     * body: [bodyweightKg] plus the added load) up to [FULL_AT_COMPOUND], or [FULL_AT_ISOLATION] on an isolation
     * lift. More reps: +1 pays half, +2 or more full; a hold scales with its seconds gained.
     */
    fun xp(hit: Hit, hold: Boolean, addsToBody: Boolean, isolation: Boolean, bodyweightKg: Double?): Int {
        val figure = figureOf(hit.set, hold)
        val fraction = when (hit.kind) {
            Kind.HEAVIER -> {
                val base = if (addsToBody) (bodyweightKg ?: 0.0).coerceAtLeast(0.0) else 0.0
                val before = base + hit.was.weightKg
                val jump = if (before > 0.0) (loadOf(hit.set) - hit.was.weightKg) / before else 1.0
                jump / (if (isolation) FULL_AT_ISOLATION else FULL_AT_COMPOUND)
            }
            Kind.MORE_REPS -> {
                val gain = figure - hit.was.figure
                if (hold) gain.toDouble() / hit.was.figure.coerceAtLeast(1) / FULL_AT_HOLD_GAIN else gain / 2.0
            }
        }
        return (Xp.PEAK_BONUS * fraction.coerceIn(0.0, 1.0)).roundToInt()
    }

    /** A lift's latest peak, as Today names it: the set that went furthest and the earlier best it passed. */
    data class Recent(
        val name: String,
        val atMs: Long,
        val hit: Hit,
        val isHold: Boolean,
        /** The movement is a bodyweight one, so the set's load is ADDED to the body. */
        val addsToBody: Boolean,
    )

    /** The headline of a movement's hits: the one that pays most, then the heaviest, then the longest. */
    fun headline(hits: List<Hit>, hold: Boolean, pay: (Hit) -> Int): Hit? =
        hits.maxWithOrNull(compareBy({ pay(it) }, { loadOf(it.set) }, { figureOf(it.set, hold) }))

    /**
     * The lifts whose latest peak fell inside the last [withinDays] days, newest first. Each trial is judged
     * against the trials before it only, so a later, better trial never takes an earlier peak away.
     */
    fun recent(
        history: List<Pair<WorkoutSession, List<SessionSet>>>,
        exercises: Map<Long, Exercise>,
        nowMs: Long,
        withinDays: Int,
        limit: Int,
    ): List<Recent> {
        val metricOf = { set: SessionSet -> exercises[set.exerciseId]?.metric }
        val book = HashMap<String, MutableList<Mark>>()
        val latest = LinkedHashMap<String, Recent>()
        val cutoff = nowMs - withinDays * 24L * 60 * 60 * 1000
        for ((session, sets) in history.filter { it.first.completedAtMs != null }.sortedBy { it.first.startedAtMs }) {
            val at = session.completedAtMs ?: session.startedAtMs
            if (at >= cutoff) {
                for (block in sets.groupBy { it.exerciseId }.values) {
                    val first = block.first()
                    val metric = metricOf(first) ?: ExerciseMetric.REPS
                    if (!metric.isStrength) continue
                    val hold = metric == ExerciseMetric.HOLD
                    val best = headline(hits(block, book[key(first.exerciseName)].orEmpty(), hold), hold) { 0 } ?: continue
                    latest[key(first.exerciseName)] = Recent(
                        name = first.exerciseName.trim(), atMs = at, hit = best, isHold = hold,
                        addsToBody = exercises[first.exerciseId]?.isWeighted == false,
                    )
                }
            }
            record(book, sets, metricOf)
        }
        return latest.values.sortedByDescending { it.atMs }.take(limit)
    }
}
