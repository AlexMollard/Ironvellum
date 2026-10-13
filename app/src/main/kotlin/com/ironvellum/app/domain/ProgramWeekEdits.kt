package com.ironvellum.app.domain

/** One [ChangeOp] aimed at one rite of a week, named by its index in the week's list. */
data class WeekEdit(val presetIndex: Int, val op: ChangeOp)

/**
 * Applies ops to a whole week. [ProgramChanges] works inside one rite and ignores a move to another day, and
 * the easing and gap suggestions are aimed at rites of the week, so this is the one place those land.
 *
 * Edits run in the order given, each on the week the one before left, and an edit whose target is gone is
 * skipped rather than half-applied: a trim and a swap of the same movement can both be kept, and whichever
 * runs second finds the movement already replaced and does nothing. The rites themselves never come or go,
 * so an index stays true for the whole run.
 */
object WeekEdits {

    private fun key(name: String): String = name.trim().lowercase()

    /** The index of the first rite called [name], or -1. Two rites with one name resolve to the first. */
    fun indexOf(week: List<PlannedPreset>, name: String): Int = week.indexOfFirst { it.name == name }

    fun apply(week: List<PlannedPreset>, edits: List<WeekEdit>): List<PlannedPreset> =
        edits.fold(week) { acc, edit -> applyOne(acc, edit) }

    private fun applyOne(week: List<PlannedPreset>, edit: WeekEdit): List<PlannedPreset> {
        val rite = week.getOrNull(edit.presetIndex) ?: return week
        fun with(entries: List<PlannedEntry>): List<PlannedPreset> =
            week.mapIndexed { i, p -> if (i == edit.presetIndex) p.copy(entries = entries) else p }

        fun holds(name: String) = rite.entries.any { key(it.exerciseName) == key(name) }

        return when (val op = edit.op) {
            is ChangeOp.AddEntry ->
                if (holds(op.entry.exerciseName)) week else with(rite.entries + op.entry)

            is ChangeOp.RemoveEntry ->
                if (!holds(op.exerciseName) || rite.entries.size == 1) week
                else with(rite.entries.filterNot { key(it.exerciseName) == key(op.exerciseName) })

            is ChangeOp.SwapEntry ->
                if (!holds(op.fromName) || (holds(op.to.exerciseName) && key(op.to.exerciseName) != key(op.fromName))) week
                else with(rite.entries.map { if (key(it.exerciseName) == key(op.fromName)) op.to else it })

            is ChangeOp.SetSets ->
                with(rite.entries.map { if (key(it.exerciseName) == key(op.exerciseName)) it.copy(sets = op.sets) else it })

            is ChangeOp.SetReps ->
                with(rite.entries.map { if (key(it.exerciseName) == key(op.exerciseName)) it.copy(reps = op.reps) else it })

            is ChangeOp.SetLoad ->
                with(
                    rite.entries.map {
                        if (key(it.exerciseName) == key(op.exerciseName)) {
                            it.copy(targetWeightKg = op.weightKg, reps = op.reps ?: it.reps, loadNote = op.loadNote)
                        } else it
                    },
                )

            is ChangeOp.SpaceEntry -> week

            is ChangeOp.MoveEntryToDay -> {
                val moved = rite.entries.firstOrNull { key(it.exerciseName) == key(op.exerciseName) }
                val target = week.indexOfFirst { it.scheduledDay == op.toDay && it !== rite }
                when {
                    moved == null || target < 0 || rite.entries.size == 1 -> week
                    week[target].entries.any { key(it.exerciseName) == key(op.exerciseName) } -> week
                    else -> week.mapIndexed { i, p ->
                        when (i) {
                            edit.presetIndex -> p.copy(entries = p.entries.filter { it !== moved })
                            target -> p.copy(entries = p.entries + moved)
                            else -> p
                        }
                    }
                }
            }
        }
    }
}
