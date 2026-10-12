package com.ironvellum.app.ui.program

import com.ironvellum.app.data.Repository
import com.ironvellum.app.domain.PlannedEntry
import com.ironvellum.app.domain.ProgramRules
import com.ironvellum.app.domain.RoutinePlan
import com.ironvellum.app.domain.TrainingFocus
import com.ironvellum.app.domain.TrainingMode

/*
 * What the Forge and first-run setup both do to a proposed week. One copy, so the two screens cannot
 * drift apart on set limits, edits or the progression rule.
 */

/** Sets, with the same ceiling the generator itself stops at for this movement. */
internal fun PlannedEntry.withSetsDelta(delta: Int): PlannedEntry =
    copy(sets = (sets + delta).coerceIn(1, ProgramRules.maxSetsPerEntry(exerciseName)))

internal fun PlannedEntry.withRepsDelta(delta: Int): PlannedEntry =
    copy(reps = (reps + delta).coerceIn(1, 30))

/**
 * [transform] applied to one entry, or null when the indices point at nothing. The indices were captured
 * when the row was composed, so a plan that changed since (a rebuild, a removal) must be a no-op, not a crash.
 */
internal fun RoutinePlan.withEntryEdited(
    presetIndex: Int,
    entryIndex: Int,
    transform: (PlannedEntry) -> PlannedEntry,
): RoutinePlan? {
    val preset = presets.getOrNull(presetIndex) ?: return null
    val entry = preset.entries.getOrNull(entryIndex) ?: return null
    val entries = preset.entries.toMutableList().also { it[entryIndex] = transform(entry) }
    return copy(presets = presets.toMutableList().also { it[presetIndex] = preset.copy(entries = entries) })
}

/** The plan without one entry (and without its day when that was its last); null when the indices point at nothing. */
internal fun RoutinePlan.withEntryRemoved(presetIndex: Int, entryIndex: Int): RoutinePlan? {
    val preset = presets.getOrNull(presetIndex) ?: return null
    if (entryIndex !in preset.entries.indices) return null
    val entries = preset.entries.filterIndexed { i, _ -> i != entryIndex }
    val presetsOut = if (entries.isEmpty()) {
        presets.filterIndexed { i, _ -> i != presetIndex }
    } else {
        presets.toMutableList().also { it[presetIndex] = preset.copy(entries = entries) }
    }
    return copy(presets = presetsOut)
}

/**
 * Moves the plan's scheduled days off [occupied] ones, so adding a week to a cycle never lands two rites on
 * one day. A rite whose day is free keeps it; one that collides takes the next free weekday after its own,
 * wrapping past Sunday. Unscheduled rites stay unscheduled. Null when the week has too few free days: the
 * caller says so instead of colliding.
 */
internal fun RoutinePlan.avoidingDays(occupied: Set<Int>): RoutinePlan? {
    val taken = occupied.toMutableSet()
    val days = presets.map { it.scheduledDay }.toMutableList()
    val collides = mutableListOf<Int>()
    presets.forEachIndexed { i, preset ->
        val day = preset.scheduledDay ?: return@forEachIndexed
        if (!taken.add(day)) collides += i
    }
    for (i in collides) {
        val day = days[i]!!
        val free = (1..7).map { (day - 1 + it) % 7 + 1 }.firstOrNull { it !in taken } ?: return null
        taken += free
        days[i] = free
    }
    if (collides.isEmpty()) return this
    return copy(presets = presets.mapIndexed { i, preset -> preset.copy(scheduledDay = days[i]) })
}

/**
 * The routine a week's coverage reads: its scheduled rites, or the whole board when none is scheduled.
 * The saved Weekly Coverage page and the builder's preview both call it, so they count the same rites.
 */
internal fun <T> scheduledRoutine(all: List<T>, dayOf: (T) -> Int?): List<T> =
    all.filter { dayOf(it) != null }.ifEmpty { all }

/**
 * The progression engine must follow the goal: a hypertrophy plan left on the strength mode progressed by
 * the rep band, never by double progression. GENERAL and SKILL leave the profile mode alone.
 */
internal fun progressionModeFor(focus: TrainingFocus): TrainingMode? = when (focus) {
    TrainingFocus.STRENGTH -> TrainingMode.STRENGTH
    TrainingFocus.MUSCLE -> TrainingMode.HYPERTROPHY
    else -> null
}

internal suspend fun Repository.alignProgressionMode(focus: TrainingFocus) {
    progressionModeFor(focus)?.let { setTrainingMode(it) }
}
