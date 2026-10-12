package com.ironvellum.app.domain

/**
 * Applies a ticked subset of an improve pass's [PlanChange]s to a preset. The
 * changes are independent data; this is the one place they meet, in a fixed
 * order, so any subset gives the same consistent preset whatever order the
 * lifter ticked them in.
 */
internal object ProgramChanges {

    private fun key(name: String): String = name.trim().lowercase()

    /** The changes in [selected] that still stand: a change whose requirement fell away falls with it. */
    fun standing(changes: List<PlanChange>, selected: Set<String>): List<PlanChange> {
        var live = changes.filter { it.id in selected }
        while (true) {
            val ids = live.mapTo(mutableSetOf()) { it.id }
            val next = live.filter { it.requires == null || it.requires in ids }
            if (next.size == live.size) return live
            live = next
        }
    }

    /** [id] and everything it requires, the smallest set that applies it alone. */
    fun closure(changes: List<PlanChange>, id: String): Set<String> {
        val byId = changes.associateBy { it.id }
        val out = linkedSetOf<String>()
        var next: String? = id
        while (next != null && out.add(next)) next = byId[next]?.requires
        return out
    }

    fun apply(
        before: PlannedPreset,
        changes: List<PlanChange>,
        plan: ApplyPlan,
        selected: Set<String>,
    ): PlannedPreset {
        val ops = standing(changes, selected).map { it.op }
        if (ops.isEmpty()) return before
        var entries = before.entries

        fun edit(name: String, change: (PlannedEntry) -> PlannedEntry) {
            entries = entries.map { if (key(it.exerciseName) == key(name)) change(it) else it }
        }

        // Canonical order: remove, swap, sets, reps, load, add.
        for (op in ops.filterIsInstance<ChangeOp.RemoveEntry>()) {
            entries = entries.filterNot { key(it.exerciseName) == key(op.exerciseName) }
        }
        for (op in ops.filterIsInstance<ChangeOp.SwapEntry>()) edit(op.fromName) { op.to }
        for (op in ops.filterIsInstance<ChangeOp.SetSets>()) edit(op.exerciseName) { it.copy(sets = op.sets) }
        for (op in ops.filterIsInstance<ChangeOp.SetReps>()) edit(op.exerciseName) { it.copy(reps = op.reps) }
        for (op in ops.filterIsInstance<ChangeOp.SetLoad>()) {
            edit(op.exerciseName) {
                it.copy(targetWeightKg = op.weightKg, reps = op.reps ?: it.reps, loadNote = op.loadNote)
            }
        }
        val added = ops.filterIsInstance<ChangeOp.AddEntry>().map { it.entry }
        for (entry in added) {
            if (entries.size < plan.cap && entries.none { key(it.exerciseName) == key(entry.exerciseName) }) {
                entries = entries + entry
            }
        }
        return normalize(before, entries, plan, added.map { key(it.exerciseName) })
    }

    /** Re-plans, dedupes, holds the cap and keeps pull-up variants apart. */
    private fun normalize(
        before: PlannedPreset,
        picked: List<PlannedEntry>,
        plan: ApplyPlan,
        addedKeys: List<String>,
    ): PlannedPreset {
        var entries = picked
            .map { if (key(it.exerciseName) in plan.replan) ProgramRules.withPlan(it, plan.volume, plan.focus) else it }
            .distinctBy { key(it.exerciseName) }
        // Over the cap: the entries the full pass would have dropped go first, then the latest additions.
        val victims = plan.trimOrder.map(::key) + addedKeys.reversed()
        for (victim in victims) {
            if (entries.size <= plan.cap) break
            entries = entries.filterNot { key(it.exerciseName) == victim }
        }
        return before.copy(entries = ProgramGenerator.spacePullUps(entries))
    }
}
