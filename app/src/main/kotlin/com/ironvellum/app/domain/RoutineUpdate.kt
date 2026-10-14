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
    enum class Field { SETS, REPS, LOAD, MODIFIERS, SCHEME }

    /**
     * One preset entry as planned, and as the finished session suggests; [isHold]
     * when targetReps is seconds. [fields] lists what differs, each readable as
     * before/after on the two entries.
     */
    data class Change(val before: PresetEntry, val after: PresetEntry, val isHold: Boolean = false) {
        val fields: List<Field>
            get() = Field.entries.filter { field ->
                when (field) {
                    // A scheme owns its own set count, and a top scheme's reps are a range of its own.
                    Field.SETS -> before.scheme == after.scheme && before.targetSets != after.targetSets
                    Field.REPS -> !topScheme && before.targetReps != after.targetReps
                    Field.LOAD -> before.targetWeightKg != after.targetWeightKg
                    Field.MODIFIERS -> before.modifiers != after.modifiers
                    Field.SCHEME -> before.scheme != after.scheme
                }
            }

        private val topScheme: Boolean
            get() = before.scheme.type == SchemeType.TOP_BACKOFF || after.scheme.type == SchemeType.TOP_BACKOFF

    /**
     * This change narrowed to [accepted]: every other field keeps its
     * planned value, so applying it writes only what the lifter ticked.
     * Null when nothing it moves was accepted.
     */
        fun only(accepted: Set<Field>): Change? {
            val kept = fields.filter { it in accepted }.toSet()
            if (kept.isEmpty()) return null
            // Accepting the scheme takes its set count and reps with it: they are part of how it reads.
            val scheme = Field.SCHEME in kept
            return copy(
                after = before.copy(
                    targetSets = if (scheme || Field.SETS in kept) after.targetSets else before.targetSets,
                    targetReps = if (scheme || Field.REPS in kept) after.targetReps else before.targetReps,
                    targetWeightKg = if (Field.LOAD in kept) after.targetWeightKg else before.targetWeightKg,
                    modifiers = if (Field.MODIFIERS in kept) after.modifiers else before.modifiers,
                    scheme = if (scheme) after.scheme else before.scheme,
                ),
            )
        }

        /**
         * The fields the offer starts with ticked: every one, except a set
         * count that drops. Deleting a set mid-trial is often a bad day, not a
         * new plan, so a smaller count is offered but must be ticked on purpose.
         * The sets line follows [schemeTickedByDefault]: a change to the whole
         * lift starts ticked, a one-off to a single set does not.
         */
        fun defaultTicks(): Set<Field> = fields.filterNot {
            (it == Field.SETS && after.targetSets < before.targetSets) ||
                (it == Field.SCHEME && !schemeTickedByDefault(before.scheme, after.scheme))
        }.toSet()
    }

    /**
     * Whether a changed sets line starts ticked. A coherent scheme the lifter set up lift-wide (top + back-offs, a
     * drop chain, straight sets given back their drops) starts ticked; a one-off retag of a single set, which only
     * reads as Custom, starts unticked, as does a shape that lost sets (a back-off or a drop taken out is often a
     * bad day) and a scheme undone back to straight.
     */
    fun schemeTickedByDefault(before: SetScheme, after: SetScheme): Boolean = when {
        after.type == SchemeType.CUSTOM -> false
        after.type == SchemeType.STRAIGHT -> before.type == SchemeType.STRAIGHT
        before.type == SchemeType.TOP_BACKOFF && after.type == SchemeType.TOP_BACKOFF -> after.backoffSets >= before.backoffSets
        before.type == SchemeType.DROPS && after.type == SchemeType.DROPS ->
            after.dropCount >= before.dropCount && after.dropSets >= before.dropSets
        else -> true
    }

    /**
     * The scheme a trial's [rows] of one lift read as (warm-ups left out), built on the rite's [planned] one so its
     * ranges, percentages and fixed loads survive. Straight when every set is a plain working set; top + back-offs
     * when the first is a top set and every other a back-off; drops when the extra sets are drops after the last
     * sets, the same number each; anything else (a set to failure, one back-off among working sets) is Custom.
     */
    fun inferScheme(planned: SetScheme, rows: List<SessionSet>): SetScheme {
        val ordered = rows.filter { !it.warmup }.sortedBy { it.setIndex }
        val kinds = ordered.map { it.kind }
        if (kinds.isEmpty()) return planned
        val ramp = planned.warmupRamp
        if (kinds.all { it == SetKind.WORKING }) return if (planned.isStraight) planned else SetScheme(warmupRamp = ramp)
        if (kinds.size > 1 && kinds.first() == SetKind.TOP && kinds.drop(1).all { it == SetKind.BACKOFF }) {
            val base = if (planned.type == SchemeType.TOP_BACKOFF) {
                planned
            } else {
                SetScheme(
                    type = SchemeType.TOP_BACKOFF, warmupRamp = ramp,
                    topReps = rangeAround(ordered.first().reps, SetScheme.DEFAULT.topReps),
                    backoffReps = rangeAround(ordered[1].reps, SetScheme.DEFAULT.backoffReps),
                )
            }
            return base.copy(backoffSets = (kinds.size - 1).coerceAtMost(SetScheme.MAX_BACKOFF_SETS))
        }
        dropShape(kinds)?.let { (dropSets, dropCount) ->
            val base = if (planned.type == SchemeType.DROPS) planned else SetScheme(
                type = SchemeType.DROPS, warmupRamp = ramp, dropPercent = observedDropPercent(ordered) ?: SetScheme.DEFAULT_DROP_PERCENT,
            )
            return base.copy(dropSets = dropSets, dropCount = dropCount)
        }
        return SetScheme(type = SchemeType.CUSTOM, custom = kinds.take(SetScheme.MAX_CUSTOM_SETS), warmupRamp = ramp)
    }

    /** [reps] sits inside [default] when it can, else a band of one rep either side of it. */
    private fun rangeAround(reps: Int, default: IntRange): IntRange =
        if (reps in default) default else (reps - 1).coerceAtLeast(1)..(reps + 1)

    /** (how many of the last sets drop, drops each) when [kinds] is working sets with the same drops after the last few; else null. */
    private fun dropShape(kinds: List<SetKind>): Pair<Int, Int>? {
        if (kinds.any { it != SetKind.WORKING && it != SetKind.DROP } || kinds.first() == SetKind.DROP) return null
        val drops = mutableListOf<Int>()
        for (kind in kinds) if (kind == SetKind.DROP) drops[drops.lastIndex]++ else drops += 0
        val dropping = drops.dropWhile { it == 0 }
        val each = dropping.firstOrNull() ?: return null
        if (dropping.any { it != each } || each > SetScheme.MAX_DROPS) return null
        return dropping.size to each
    }

    /** The first drop's load against the set before it, as the nearest 5%; null when either has no load. */
    private fun observedDropPercent(rows: List<SessionSet>): Int? {
        val at = rows.indexOfFirst { it.kind == SetKind.DROP }
        if (at < 1) return null
        val from = rows[at - 1].weightKg?.takeIf { it > 0.0 } ?: return null
        val to = rows[at].weightKg?.takeIf { it > 0.0 } ?: return null
        val pct = Math.round((1.0 - to / from) * 100.0 / 5.0).toInt() * 5
        return pct.takeIf { it in SetScheme.PERCENT_RANGE }
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
        // A warm-up row is not part of the rite's prescription, whatever its tick.
        val blocksByExercise = sets.filter { !it.warmup }.groupBy { it.exerciseId }.mapValues { (_, rows) ->
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
            // The prescription is read off the sets that carry it: a top set speaks for the lift, and drops and
            // back-offs are lighter by design, so they never move the rite's reps or load.
            val tops = done.filter { it.kind == SetKind.TOP }
            val base = tops.ifEmpty { done.filter { it.kind != SetKind.DROP && it.kind != SetKind.BACKOFF } }.ifEmpty { done }
            val scheme = inferScheme(entry.scheme, block)
            val figures = base.map { set ->
                if (metric == ExerciseMetric.HOLD) (set.durationSec ?: set.reps) else set.reps
            }
            val modifiers = done.first().modifiers
            val after = entry.copy(
                // Rows, not done sets: a set left unticked is a short day and
                // keeps the count, while removeSet deletes the row, so a set
                // taken out on purpose is the only way the count drops. A drop
                // is an extra set on top of the count, not one of its sets.
                targetSets = block.count { it.kind != SetKind.DROP },
                // A top scheme keeps its reps as a range of its own, led by the entry's figure.
                targetReps = if (scheme.type == SchemeType.TOP_BACKOFF) scheme.topReps.first else medianDown(figures),
                targetWeightKg = mostUsedLoad(base.map { it.weightKg }),
                modifiers = if (sameModifiers(modifiers, entry.modifiers)) entry.modifiers else modifiers,
                scheme = scheme,
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
