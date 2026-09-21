package com.monarch.app.domain


/**
 * Per (exercise, set position) personal records. Set 3 is judged against the
 * best-ever set 3, never set 1 — fatigue already penalises later sets, so
 * comparing across positions would make every delta look like decline.
 */
object SetRecords {

    /** Best-ever performance of one movement at one set position. */
    data class Record(
        val exerciseName: String,
        val setIndex: Int,
        val score: Double,
        val reps: Int,
        val weightKg: Double?,
        val achievedAtMs: Long,
        val sessionId: Long,
    )

    /** How a given attempt compares with the record for that same set position. */
    data class Delta(
        val record: Record?,        // null when this set position has no history yet
        val score: Double,          // the attempt's own score
        val deltaScore: Double,     // score - record.score (0.0 when no record)
        val deltaFraction: Double,  // deltaScore / record.score (0.0 when no record)
        val isRecord: Boolean,      // attempt beats (or first-ever equals) the record
    )

    /**
     * Keyed by normalised exercise name (lowercased, trimmed) to set index,
     * so casing differences between installs or imports cannot split a
     * record. Completed sets only: an unticked prescription is not a performance.
     *
     * @param metricOf the movement's metric, so a static hold is scored on its
     *   seconds. Without it a hold records `reps = 0` and can never set a PR.
     */
    fun records(
        history: List<Pair<WorkoutSession, List<SessionSet>>>,
        bodyweightAt: (atMs: Long) -> Double,
        excludeSessionId: Long? = null,
        metricOf: (SessionSet) -> ExerciseMetric? = { null },
    ): Map<Pair<String, Int>, Record> {
        val best = mutableMapOf<Pair<String, Int>, Record>()
        // Sessions in chronological order so an equal score keeps the EARLIER
        // set as the record — the first time you hit it is when you set it.
        history
            .sortedBy { (session, _) -> session.startedAtMs }
            .forEach { (session, sets) ->
                if (excludeSessionId != null && session.id == excludeSessionId) return@forEach
                sets.filter { it.done }.forEach { set ->
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
                    val score = if (hold) {
                        StrengthIndex.holdScore(set.exerciseName, figure, set.weightKg, bodyweight)
                    } else {
                        StrengthIndex.repScore(set.exerciseName, figure, set.weightKg, bodyweight)
                    }
                    val key = set.exerciseName.lowercase().trim() to set.setIndex
                    val existing = best[key]
                    if (existing == null || score > existing.score) {
                        best[key] = Record(
                            exerciseName = set.exerciseName,
                            setIndex = set.setIndex,
                            score = score,
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

    /** Bodyweight as at [atMs]: the most recent reading at or before that moment, else the earliest known, else [fallbackKg]. */
    fun bodyweightLookup(stats: List<StatEntry>, fallbackKg: Double = 0.0): (Long) -> Double {
        if (stats.isEmpty()) return { fallbackKg }
        val sorted = stats.sortedBy { it.takenAtMs }
        val earliest = sorted.first()
        return { atMs ->
            // A set logged before any weigh-in uses the earliest reading: it is
            // the closest truth available, better than a blind fallback.
            sorted.lastOrNull { it.takenAtMs <= atMs }?.weightKg ?: earliest.weightKg
        }
    }

    // delta keeps a CONCRETE bodyweightKg on purpose: a live attempt happens
    // now, so today's reading is the correct value — records() is the only
    // time-aware side. Do not "tidy" this into a lookup.
    fun delta(
        records: Map<Pair<String, Int>, Record>,
        exerciseName: String,
        setIndex: Int,
        reps: Int,
        weightKg: Double?,
        bodyweightKg: Double,
        /** True when [reps] is really seconds held. */
        isHold: Boolean = false,
    ): Delta {
        val score = if (isHold) {
            StrengthIndex.holdScore(exerciseName, reps, weightKg, bodyweightKg)
        } else {
            StrengthIndex.repScore(exerciseName, reps, weightKg, bodyweightKg)
        }
        val record = records[exerciseName.lowercase().trim() to setIndex]
            ?: return Delta(record = null, score = score, deltaScore = 0.0, deltaFraction = 0.0, isRecord = true)
        val deltaScore = score - record.score
        // Record score can be 0.0 (e.g. bodyweight unknown at record time);
        // dividing would produce NaN, so a zero record pins the fraction at 0.
        val deltaFraction = if (record.score == 0.0) 0.0 else deltaScore / record.score
        return Delta(
            record = record,
            score = score,
            deltaScore = deltaScore,
            deltaFraction = deltaFraction,
            isRecord = deltaScore > 0.0,
        )
    }
}
