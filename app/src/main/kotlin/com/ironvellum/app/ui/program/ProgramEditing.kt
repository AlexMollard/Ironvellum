package com.ironvellum.app.ui.program

import com.ironvellum.app.data.Repository
import com.ironvellum.app.domain.Equipment
import com.ironvellum.app.domain.Exercise
import com.ironvellum.app.domain.LoadKit
import com.ironvellum.app.domain.LoadUnit
import com.ironvellum.app.domain.MuscleMap
import com.ironvellum.app.domain.ProgramGenerator
import com.ironvellum.app.domain.StrengthProfile
import com.ironvellum.app.domain.VolumeLevel
import com.ironvellum.app.domain.PlannedEntry
import com.ironvellum.app.domain.ProgramRules
import com.ironvellum.app.domain.WorkoutPreset
import com.ironvellum.app.ui.train.MAX_LOAD_KG
import com.ironvellum.app.domain.RoutinePlan
import com.ironvellum.app.domain.TrainingFocus
import com.ironvellum.app.domain.TrainingMode

/*
 * What the Forge and first-run setup both do to a proposed week. One copy, so the two screens cannot
 * drift apart on set limits, edits or the progression rule.
 */

/**
 * The hard sets one movement may carry. This is a sanity range, not a dose: the lifter decides how much
 * they will do, and the Forge only refuses a number nobody could finish (the generator's own stopping
 * point is a suggestion, never a cap on the stepper).
 */
internal val SET_SANITY = 1..10

internal fun PlannedEntry.withSetsDelta(delta: Int): PlannedEntry =
    copy(sets = (sets + delta).coerceIn(SET_SANITY))

/**
 * Load stepped by [delta] steps of [stepKg], never above [capKg] (the lifter's own heaviest dumbbell) and
 * never below one step. A bodyweight movement has no load to step and is returned as it is. With a [worn]
 * kit (the vest on a loaded bodyweight lift) each step is the kit's next block or belt plate instead, and
 * the load stays put where the kit has nowhere further to go.
 */
internal fun PlannedEntry.withLoadDelta(
    delta: Int,
    stepKg: Double,
    capKg: Double? = null,
    worn: LoadKit? = null,
    unit: LoadUnit = LoadUnit.KG,
): PlannedEntry {
    val load = targetWeightKg ?: return this
    if (worn != null) {
        var next = load
        repeat(kotlin.math.abs(delta)) {
            next = (if (delta > 0) worn.nextKg(next, unit) else worn.previousKg(next, unit)) ?: next
        }
        return copy(targetWeightKg = next)
    }
    val top = capKg ?: MAX_LOAD_KG
    val next = (Math.round((load + delta * stepKg) * 4) / 4.0).coerceIn(minOf(stepKg, top), maxOf(top, load))
    return copy(targetWeightKg = next)
}

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
 * The rites Weekly coverage leaves out: those without a day, once at least one rite has one (until then the
 * whole board counts, see [scheduledRoutine]). Empty when every rite is counted.
 */
internal fun <T> uncountedRoutine(all: List<T>, dayOf: (T) -> Int?): List<T> =
    if (all.any { dayOf(it) != null }) all.filter { dayOf(it) == null } else emptyList()

/**
 * The day a new rite is ticked for when the lifter is asked: [preferred] while no rite holds it, otherwise the
 * first free weekday, and null ("No day") only when all seven are taken.
 */
internal fun defaultRiteDay(preferred: Int?, taken: Set<Int>): Int? =
    preferred?.takeIf { it !in taken } ?: firstFreeWeekday(taken)

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

/**
 * A movement the lifter picked (a swap or an addition), prescribed the way the generator would: the sets they
 * keep or the level's starting sets, the goal's rep window for the middle of its range, and a load worked from
 * what they have logged, kept under their own dumbbells. A movement with no load to give is bodyweight.
 */
internal fun newPlannedEntry(
    exercise: Exercise,
    sets: Int?,
    tier: VolumeLevel,
    focus: TrainingFocus,
    equipment: Equipment,
    strength: StrengthProfile,
): PlannedEntry {
    val compound = MuscleMap.profile(exercise.name)?.compound ?: true
    val window = ProgramRules.repWindow(focus, compound)
    val reps = window.first + (window.last - window.first) / 2
    val fill = ProgramGenerator.fillLoad(exercise, strength, reps, ProgramRules.loadRir(compound), equipment)
    val entry = PlannedEntry(
        exerciseName = exercise.name,
        sets = (sets ?: ProgramRules.startingSets(tier, compound, priority = false)).coerceIn(SET_SANITY),
        reps = fill?.reps ?: reps,
        targetWeightKg = fill?.kg,
        loadNote = fill?.note,
    )
    return ProgramRules.withPlan(entry, tier, focus)
}

/**
 * What the lifter changed on a proposal since it was forged, for the discard sheet. Movements are matched by
 * name inside a rite; a movement that left and another that arrived in the same rite read as one swap.
 */
internal data class PlanEdits(
    val swapped: Int = 0,
    val setsChanged: Int = 0,
    val loadsChanged: Int = 0,
    val removed: Int = 0,
    val added: Int = 0,
) {
    val any: Boolean get() = swapped + setsChanged + loadsChanged + removed + added > 0

    /** "2 movements swapped", "3 set counts changed", "1 movement removed": one line per kind of edit. */
    fun lines(): List<String> = buildList {
        fun movements(n: Int) = "$n ${if (n == 1) "movement" else "movements"}"
        if (swapped > 0) add("${movements(swapped)} swapped")
        if (setsChanged > 0) add("$setsChanged set ${if (setsChanged == 1) "count" else "counts"} changed")
        if (loadsChanged > 0) add("$loadsChanged ${if (loadsChanged == 1) "load" else "loads"} changed")
        if (removed > 0) add("${movements(removed)} removed")
        if (added > 0) add("${movements(added)} added")
    }
}

private fun PlannedEntry.editKey(): String = "$exerciseName|$modifiers"

internal fun planEdits(generated: RoutinePlan?, current: RoutinePlan?): PlanEdits {
    if (generated == null || current == null) return PlanEdits()
    var swapped = 0
    var setsChanged = 0
    var loadsChanged = 0
    var removed = 0
    var added = 0
    for (rite in generated.presets) {
        val now = current.presets.firstOrNull { it.name == rite.name && it.scheduledDay == rite.scheduledDay }
        val before = rite.entries
        val after = now?.entries.orEmpty()
        val afterKeys = after.map { it.editKey() }
        val beforeKeys = before.map { it.editKey() }
        val gone = before.count { it.editKey() !in afterKeys }
        val arrived = after.count { it.editKey() !in beforeKeys }
        val swaps = minOf(gone, arrived)
        swapped += swaps
        removed += gone - swaps
        added += arrived - swaps
        for (entry in before) {
            val kept = after.firstOrNull { it.editKey() == entry.editKey() } ?: continue
            if (kept.sets != entry.sets) setsChanged++
            if (kept.targetWeightKg != entry.targetWeightKg) loadsChanged++
        }
    }
    return PlanEdits(swapped, setsChanged, loadsChanged, removed, added)
}

/** One new rite against the cycle it joins: its day, whether a saved rite holds it, and where it would move. */
internal data class DayPlacement(val rite: String, val day: Int, val takenBy: String?, val movedTo: Int?)

/** The days the new week asks for set against the days the saved cycle already uses. */
internal data class DayConflict(val placements: List<DayPlacement>, val fitsAround: Boolean) {
    val clashes: Int get() = placements.count { it.takenBy != null }
    val hasClash: Boolean get() = clashes > 0
}

/**
 * Where each new rite would land if it joined [saved]: free days stay, a taken day moves to the next free
 * one ([avoidingDays]). [DayConflict.fitsAround] is false when the week has too few free days, so the sheet
 * can say so instead of offering a move that cannot happen.
 */
internal fun dayConflict(plan: RoutinePlan, saved: List<WorkoutPreset>): DayConflict {
    val takenBy = saved.filter { it.scheduledDay != null }.groupBy { it.scheduledDay!! }.mapValues { it.value.first().name }
    val placed = plan.avoidingDays(takenBy.keys)
    val rows = plan.presets.mapIndexedNotNull { i, rite ->
        val day = rite.scheduledDay ?: return@mapIndexedNotNull null
        val holder = takenBy[day]
        DayPlacement(rite.name, day, holder, if (holder != null) placed?.presets?.get(i)?.scheduledDay else null)
    }
    return DayConflict(rows, fitsAround = placed != null)
}
