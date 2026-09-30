package com.ironvellum.app.domain

/**
 * What a preset would look like if it matched the session the lifter just
 * finished. The DONE sets speak for the figures; the set count follows the
 * rows the block still has, so a set deleted mid-trial is told apart from one
 * left unticked. A movement he skipped says nothing about the plan, and a
 * movement he added is not part of the plan at all.
 */
object RoutineUpdate {

    /** The parts of a preset entry a finished session can move; the lifter accepts each on its own. */
    enum class Field { SETS, REPS, LOAD, MODIFIERS }

    /**
     * One preset entry as planned, and as the finished session suggests; [isHold]
     * when targetReps is seconds. [fields] lists what differs, each readable as
     * before/after on the two entries.
     */
    data class Change(val before: PresetEntry, val after: PresetEntry, val isHold: Boolean = false) {
        val fields: List<Field>
            get() = Field.entries.filter { field ->
                when (field) {
                    Field.SETS -> before.targetSets != after.targetSets
                    Field.REPS -> before.targetReps != after.targetReps
                    Field.LOAD -> before.targetWeightKg != after.targetWeightKg
                    Field.MODIFIERS -> before.modifiers != after.modifiers
                }
            }

    /**
     * This change narrowed to [accepted]: every other field keeps its
     * planned value, so applying it writes only what the lifter ticked.
     * Null when nothing it moves was accepted.
     */
        fun only(accepted: Set<Field>): Change? {
            val kept = fields.filter { it in accepted }.toSet()
            if (kept.isEmpty()) return null
            return copy(
                after = before.copy(
                    targetSets = if (Field.SETS in kept) after.targetSets else before.targetSets,
                    targetReps = if (Field.REPS in kept) after.targetReps else before.targetReps,
                    targetWeightKg = if (Field.LOAD in kept) after.targetWeightKg else before.targetWeightKg,
                    modifiers = if (Field.MODIFIERS in kept) after.modifiers else before.modifiers,
                ),
            )
        }

        /**
         * The fields the offer starts with ticked: every one, except a set
         * count that drops. Deleting a set mid-trial is often a bad day, not a
         * new plan, so a smaller count is offered but must be ticked on purpose.
         */
        fun defaultTicks(): Set<Field> =
            fields.filterNot { it == Field.SETS && after.targetSets < before.targetSets }.toSet()
    }

    /**
     * Changes for the entries whose finished block differs from their plan,
     * in preset order. Per entry: sets = the rows the block has, ticked or
     * not, so an unticked set (a short day) keeps the count, a deleted one
     * lowers it (see [Change.defaultTicks]) and an added one raises it; reps
     * (a hold's seconds, which the preset keeps in targetReps) = median of the
     * done sets, rounded down; load = the one used on the most done sets, a
     * tie going to the heavier, bodyweight staying null; modifiers = the
     * session's when they differ. A block with no done set proposes nothing,
     * not even its row count: an untouched movement says nothing about the
     * plan. Activities are not proposed: their preset target is a
     * passthrough, not a prescription.
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
            val block = blocksByExercise[entry.exerciseId]?.getOrNull(nth).orEmpty()
            val done = block.filter { it.done }
            if (done.isEmpty()) return@mapNotNull null
            val figures = done.map { set ->
                if (metric == ExerciseMetric.HOLD) (set.durationSec ?: set.reps) else set.reps
            }
            val modifiers = done.first().modifiers
            val after = entry.copy(
                // Rows, not done sets: a set left unticked is a short day and
                // keeps the count, while removeSet deletes the row, so a set
                // taken out on purpose is the only way the count drops.
                targetSets = block.size,
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
