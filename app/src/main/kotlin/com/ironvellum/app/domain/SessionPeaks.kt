package com.ironvellum.app.domain

/**
 * The records one trial set, rolled up per movement for the victory screen.
 *
 * A set is a peak by exactly the rule of the live NEW PEAK badge: it beats
 * its position's record and every earlier done set of the movement today
 * ([SetRecords.delta]). Deciding it twice in two ways would let the victory
 * screen disagree with the badges the lifter just watched light up.
 */
object SessionPeaks {

    /** One movement's peaks: how many, and the set that gained most over its record. */
    data class Peak(
        val exerciseName: String,
        val count: Int,
        val setIndex: Int,
        /** Reps, or seconds when [isHold]. */
        val figure: Int,
        val weightKg: Double?,
        val isHold: Boolean,
        /** The record the headline set passed. */
        val was: SetRecords.Record,
        val deltaScore: Double,
    )

    /**
     * @param records best-ever per set position, from sessions OTHER than this one.
     * @param bodyweightKg today's weigh-in; without one nothing scores, so nothing peaks.
     */
    fun of(
        sets: List<SessionSet>,
        records: Map<Pair<String, Int>, SetRecords.Record>,
        bodyweightKg: Double?,
        metricOf: (SessionSet) -> ExerciseMetric?,
    ): List<Peak> {
        val bw = bodyweightKg?.takeIf { it > 0.0 } ?: return emptyList()
        return sets
            .groupBy { it.exerciseId }
            .values
            .sortedBy { block -> block.minOf { it.exercisePosition } }
            .mapNotNull { block ->
                val metric = metricOf(block.first()) ?: ExerciseMetric.REPS
                if (!metric.isStrength) return@mapNotNull null
                val hold = metric == ExerciseMetric.HOLD
                fun figure(set: SessionSet) = if (hold) (set.durationSec ?: 0) else set.reps
                val done = block.filter { it.done }.sortedBy { it.setIndex }
                val peaks = done.mapNotNull { set ->
                    val bestEarlier = done
                        .filter { it.setIndex < set.setIndex }
                        .maxOfOrNull { SetRecords.score(it.exerciseName, figure(it), it.weightKg, bw, hold) }
                    val delta = SetRecords.delta(
                        records, set.exerciseName, set.setIndex, figure(set), set.weightKg, bw,
                        isHold = hold,
                        bestEarlierThisWorkout = bestEarlier,
                    )
                    if (delta.isRecord) set to delta else null
                }
                val (best, delta) = peaks.maxByOrNull { (_, d) -> d.deltaScore } ?: return@mapNotNull null
                Peak(
                    exerciseName = best.exerciseName,
                    count = peaks.size,
                    setIndex = best.setIndex,
                    figure = figure(best),
                    weightKg = best.weightKg,
                    isHold = hold,
                    was = delta.record!!,
                    deltaScore = delta.deltaScore,
                )
            }
    }
}
