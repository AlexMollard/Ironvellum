package com.ironvellum.app.domain

/**
 * What a preset would look like if it matched the session the lifter just
 * finished. Only the DONE sets speak: a movement he skipped says nothing about
 * the plan, and a movement he added is not part of the plan at all.
 */
object RoutineUpdate {

    /** One preset entry as planned, and as the finished session suggests; [isHold] when targetReps is seconds. */
    data class Change(val before: PresetEntry, val after: PresetEntry, val isHold: Boolean = false)

    /**
     * Changes for the entries whose done sets differ from their plan, in
     * preset order. Per entry: sets = done sets; reps (a hold's seconds, which
     * the preset keeps in targetReps) = median, rounded down; load = the one
     * used on the most sets, a tie going to the heavier, bodyweight staying
     * null; modifiers = the session's when they differ. Activities are not
     * proposed: their preset target is a passthrough, not a prescription.
     *
     * The same movement twice in a preset is matched by order: its Nth entry
     * to its Nth block of the session (blocks keep their own exercisePosition
     * even after a reorder).
     */
    fun propose(
        entries: List<PresetEntry>,
        sets: List<SessionSet>,
        metricOf: (Long) -> ExerciseMetric?,
    ): List<Change> {
        val blocksByExercise = sets.groupBy { it.exerciseId }.mapValues { (_, rows) ->
            rows.groupBy { it.exercisePosition }.toSortedMap().values.toList()
        }
        val seen = mutableMapOf<Long, Int>()
        return entries.sortedBy { it.position }.mapNotNull { entry ->
            val nth = seen.getOrDefault(entry.exerciseId, 0)
            seen[entry.exerciseId] = nth + 1
            val metric = metricOf(entry.exerciseId) ?: ExerciseMetric.REPS
            if (!metric.isStrength) return@mapNotNull null
            val done = blocksByExercise[entry.exerciseId]?.getOrNull(nth)?.filter { it.done }.orEmpty()
            if (done.isEmpty()) return@mapNotNull null
            val figures = done.map { set ->
                if (metric == ExerciseMetric.HOLD) (set.durationSec ?: set.reps) else set.reps
            }
            val modifiers = done.first().modifiers
            val after = entry.copy(
                targetSets = done.size,
                targetReps = medianDown(figures),
                targetWeightKg = mostUsedLoad(done.map { it.weightKg }),
                modifiers = if (sameModifiers(modifiers, entry.modifiers)) entry.modifiers else modifiers,
            )
            if (after == entry) null else Change(entry, after, isHold = metric == ExerciseMetric.HOLD)
        }
    }

    /** Median, the even case taking the mean of the middle pair rounded down: 5,5,5,3 -> 5; 10,9,7 -> 9. */
    fun medianDown(values: List<Int>): Int {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }

    /** The load on the most sets; a tie goes to the heavier, bodyweight (null) the lightest. */
    fun mostUsedLoad(loads: List<Double?>): Double? =
        loads.groupingBy { it }.eachCount().entries
            .maxWith(compareBy<Map.Entry<Double?, Int>> { it.value }.thenBy { it.key ?: Double.NEGATIVE_INFINITY })
            .key

    /** Modifier lists compare as sets of tags: "Deficit, Pause" is "Pause,Deficit". */
    private fun sameModifiers(a: String, b: String): Boolean = tags(a) == tags(b)

    private fun tags(modifiers: String): Set<String> =
        modifiers.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
}
