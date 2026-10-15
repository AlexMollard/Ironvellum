package com.ironvellum.app.domain


/**
 * Per (exercise and [Variation], set position) personal records. Set 3 is judged against the
 * best-ever set 3, never set 1 — fatigue already penalises later sets, so
 * comparing across positions would make every delta look like decline. A deficit calf raise
 * has its own records, apart from the flat one.
 */
object SetRecords {

    /** Best-ever performance of one movement at one set position. */
    data class Record(
        val exerciseName: String,
        /** [Variation.key] of the movement the record was set in; "" for the plain lift. */
        val variation: String = "",
        val setIndex: Int,
        val score: Double,
        val reps: Int,
        val weightKg: Double?,
        val achievedAtMs: Long,
        val sessionId: Long,
    ) {
        /** The lift as a screen names it: "Single-Leg Calf Raise · deficit". */
        val label: String get() = Variation.label(exerciseName, variation)
    }

    /** How a given attempt compares with the record for that same set position. */
    data class Delta(
        val record: Record?,        // null when this set position has no history yet
        val score: Double,          // the attempt's own score
        val deltaScore: Double,     // score - record.score (0.0 when no record)
        val deltaFraction: Double,  // deltaScore / record.score (0.0 when no record)
        val isRecord: Boolean,      // attempt beats a real record AND this workout's earlier sets
    )

    /**
     * The position [set] is judged at: its set index less the warm-ups of its
     * movement ahead of it in [siblings]. A ramp of warm-ups never turns working
     * set 1 into "set 5" and compares it against a record it has nothing to do
     * with; a trial with no warm-up keeps its set index exactly.
     */
    fun position(set: SessionSet, siblings: List<SessionSet>): Int =
        (set.setIndex - siblings.count { it.exerciseId == set.exerciseId && it.warmup && it.setIndex < set.setIndex })
            .coerceAtLeast(0)

    /**
     * Keyed by [Variation.liftKey] (the lowercased, trimmed name, then the variation) to
     * set index, so casing differences between installs or imports cannot split a
     * record, and an assisted or deficit set never competes with the plain lift. Completed sets only: an unticked prescription is not a performance.
     *
     * @param beforeMs when set, only sessions completed (started, if never
     *   completed) strictly before it count: a trial judged as history stood
     *   when it was done, so a later, better trial never takes its record away.
     * @param metricOf the movement's metric, so a static hold is scored on its
     *   seconds. Without it a hold records `reps = 0` and can never set a PR.
     */
    fun records(
        history: List<Pair<WorkoutSession, List<SessionSet>>>,
        bodyweightAt: (atMs: Long) -> Double,
        excludeSessionId: Long? = null,
        beforeMs: Long? = null,
        metricOf: (SessionSet) -> ExerciseMetric? = { null },
    ): Map<Pair<String, Int>, Record> {
        val best = mutableMapOf<Pair<String, Int>, Record>()
        // Sessions in chronological order so an equal score keeps the EARLIER
        // set as the record — the first time you hit it is when you set it.
        history
            .sortedBy { (session, _) -> session.startedAtMs }
            .forEach { (session, sets) ->
                if (excludeSessionId != null && session.id == excludeSessionId) return@forEach
                if (beforeMs != null && (session.completedAtMs ?: session.startedAtMs) >= beforeMs) return@forEach
                // A lift with a top set records that set alone; position still counts every set of the trial.
                PeakSets.narrowed(sets).forEach { set ->
                    // An activity metric (Bouldering's ATTEMPTS_GRADE, a run's
                    // DISTANCE_TIME) is not strength work at any set position:
                    // its figure is an attempt count or a distance, and scoring
                    // it through the strength path fabricates bodyweight-rep
                    // records. A null metric keeps the legacy path unchanged.
                    val metric = metricOf(set)
                    if (metric != null && !metric.isStrength) return@forEach
                    // Score with the bodyweight IN FORCE at the session, so a
                    // later weigh-in never re-scores (shrinks/inflates) an
                    // already-earned record.
                    val bodyweight = bodyweightAt(session.startedAtMs)
                    val hold = MovementDifficulty.isHoldSet(metricOf(set), set.exerciseName, set.modifiers)
                    val figure = if (hold) (set.durationSec ?: set.reps) else set.reps
                    val setScore = score(set.exerciseName, figure, set.weightKg, bodyweight, hold)
                    val position = position(set, sets)
                    val key = Variation.liftKey(set.exerciseName, set.modifiers) to position
                    val existing = best[key]
                    if (existing == null || setScore > existing.score) {
                        best[key] = Record(
                            exerciseName = set.exerciseName,
                            variation = Variation.key(set.modifiers),
                            setIndex = position,
                            score = setScore,
                            reps = figure,
                            weightKg = set.weightKg,
                            achievedAtMs = session.startedAtMs,
                            sessionId = session.id,
                        )
                    }
                }
            }
        return best
    }

    /**
     * [records] for the one movement called [name] (any variation): the figures it gives for that movement, without
     * scoring every other lift in the history. A movement's records read only its own sets, so the result matches
     * the full call's entries for the name; the keys of other movements are simply absent.
     */
    fun recordsOf(
        name: String,
        history: List<Pair<WorkoutSession, List<SessionSet>>>,
        bodyweightAt: (atMs: Long) -> Double,
        metricOf: (SessionSet) -> ExerciseMetric? = { null },
    ): Map<Pair<String, Int>, Record> {
        val wanted = name.lowercase().trim()
        val own = history.mapNotNull { (session, sets) ->
            sets.filter { it.exerciseName.lowercase().trim() == wanted }.takeIf { it.isNotEmpty() }?.let { session to it }
        }
        return records(own, bodyweightAt, metricOf = metricOf)
    }

    /** Bodyweight as at [atMs]: the most recent reading at or before that moment, else the earliest known, else [fallbackKg]. */
    fun bodyweightLookup(stats: List<StatEntry>, fallbackKg: Double = 0.0): (Long) -> Double {
        if (stats.isEmpty()) return { fallbackKg }
        val sorted = stats.sortedBy { it.takenAtMs }
        val earliest = sorted.first()
        val takenAt = LongArray(sorted.size) { sorted[it].takenAtMs }
        return { atMs ->
            // How many readings were taken at or before atMs: the last of them is in force.
            var low = 0
            var high = takenAt.size
            while (low < high) {
                val mid = (low + high) ushr 1
                if (takenAt[mid] <= atMs) low = mid + 1 else high = mid
            }
            // A set logged before any weigh-in uses the earliest reading: it is
            // the closest truth available, better than a blind fallback.
            if (low == 0) earliest.weightKg else sorted[low - 1].weightKg
        }
    }

    /** One set's strength score: a hold on its seconds, anything else on its reps. */
    fun score(exerciseName: String, figure: Int, weightKg: Double?, bodyweightKg: Double, isHold: Boolean): Double =
        if (isHold) {
            StrengthIndex.holdScore(exerciseName, figure, weightKg, bodyweightKg)
        } else {
            StrengthIndex.repScore(exerciseName, figure, weightKg, bodyweightKg)
        }

    // delta keeps a CONCRETE bodyweightKg on purpose: a live attempt happens
    // now, so today's reading is the correct value — records() is the only
    // time-aware side. Do not "tidy" this into a lookup.
    /**
     * How one attempt compares with the record at its set position.
     *
     * [Delta.isRecord] is strict: there must BE a prior record to beat (a
     * first-ever set is history starting, not a PR), and the attempt must beat
     * both that record and [bestEarlierThisWorkout], the best score of the
     * exercise's earlier done sets in this same workout. Without the second
     * bar a repeat of set 2 as set 3 read NEW PR twice.
     */
    fun delta(
        records: Map<Pair<String, Int>, Record>,
        exerciseName: String,
        setIndex: Int,
        reps: Int,
        weightKg: Double?,
        bodyweightKg: Double,
        /** True when [reps] is really seconds held. */
        isHold: Boolean = false,
        bestEarlierThisWorkout: Double? = null,
        /** The attempt's modifiers: it is judged against the records of its own [Variation]. */
        modifiers: String = "",
    ): Delta {
        val attempt = score(exerciseName, reps, weightKg, bodyweightKg, isHold)
        val record = records[Variation.liftKey(exerciseName, modifiers) to setIndex]
            ?: return Delta(record = null, score = attempt, deltaScore = 0.0, deltaFraction = 0.0, isRecord = false)
        val deltaScore = attempt - record.score
        // Record score can be 0.0 (e.g. bodyweight unknown at record time);
        // dividing would produce NaN, so a zero record pins the fraction at 0.
        val deltaFraction = if (record.score == 0.0) 0.0 else deltaScore / record.score
        return Delta(
            record = record,
            score = attempt,
            deltaScore = deltaScore,
            deltaFraction = deltaFraction,
            isRecord = deltaScore > 0.0 && attempt > (bestEarlierThisWorkout ?: Double.NEGATIVE_INFINITY),
        )
    }
}
