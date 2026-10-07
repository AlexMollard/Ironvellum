package com.ironvellum.app.domain

/**
 * A lift swapped for another during a trial, found by comparing what the rite
 * prescribed with what the trial holds. Nothing is recorded when a lift is
 * swapped (it is a removal and an addition), so the swap is read back from
 * the data: the entries whose lift has no sets left, against the lifts done
 * that the rite never named.
 */
object RiteSwap {

    /** [entry] as the rite has it, and [after] as it reads once the new lift stands in its place. */
    data class Swap(val entry: PresetEntry, val toId: Long, val toName: String, val after: PresetEntry) {
        val line: String get() = "${entry.exerciseName} → $toName"
    }

    /**
     * The swaps to offer, in rite order. A swap needs a rite entry with no
     * sets in the trial and a lift outside the rite with at least one done
     * set; the two are paired in order (rite position against trial
     * position). When the counts differ the pairing is a guess, so nothing
     * is offered: a lift swapped back is present again and drops out by
     * itself, and an entry skipped outright leaves no lift to name.
     */
    fun detect(
        entries: List<PresetEntry>,
        sets: List<SessionSet>,
        metricOf: (Long) -> ExerciseMetric?,
    ): List<Swap> {
        val inTrial = sets.map { it.exerciseId }.toSet()
        val inRite = entries.map { it.exerciseId }.toSet()
        val gone = entries.sortedBy { it.position }.filter { it.exerciseId !in inTrial }
        val added = sets.filter { it.exerciseId !in inRite }
            .groupBy { it.exerciseId }
            .filterValues { rows -> rows.any { it.done } }
            .values.sortedBy { rows -> rows.minOf { it.exercisePosition } }
        // shortcut: in-order pairing, only when the counts match; ask which lift replaced which if this proves too shy
        if (gone.isEmpty() || gone.size != added.size) return emptyList()
        return gone.zip(added).map { (entry, rows) ->
            val to = rows.first()
            Swap(entry, to.exerciseId, to.exerciseName, after(entry, rows, metricOf))
        }
    }

    /**
     * The entry with the new lift in place. The sets, reps and order stay;
     * across a change of metric the figures of the old lift mean nothing, so
     * the reps come from what was done and the load and modifiers are cleared.
     */
    private fun after(entry: PresetEntry, rows: List<SessionSet>, metricOf: (Long) -> ExerciseMetric?): PresetEntry {
        val to = rows.first()
        val swapped = entry.copy(exerciseId = to.exerciseId, exerciseName = to.exerciseName)
        val from = metricOf(entry.exerciseId) ?: ExerciseMetric.REPS
        val into = metricOf(to.exerciseId) ?: ExerciseMetric.REPS
        if (from == into) return swapped
        val figures = rows.filter { it.done }
            .map { if (into == ExerciseMetric.HOLD) (it.durationSec ?: it.reps) else it.reps }
            .filter { it > 0 }
        val reps = if (into.isStrength && figures.isNotEmpty()) RoutineUpdate.medianDown(figures) else entry.targetReps
        return swapped.copy(targetReps = reps, targetWeightKg = null, modifiers = "")
    }
}
