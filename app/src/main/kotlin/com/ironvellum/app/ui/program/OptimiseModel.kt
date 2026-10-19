package com.ironvellum.app.ui.program

import androidx.annotation.StringRes
import com.ironvellum.app.R
import com.ironvellum.app.data.Words
import com.ironvellum.app.domain.Candidate
import com.ironvellum.app.domain.ChangeOp
import com.ironvellum.app.domain.Easing
import com.ironvellum.app.domain.EasingOption
import com.ironvellum.app.domain.Exercise
import com.ironvellum.app.domain.Muscle
import com.ironvellum.app.domain.MuscleLoad
import com.ironvellum.app.domain.MuscleMap
import com.ironvellum.app.domain.OverlapFinding
import com.ironvellum.app.domain.PlannedEntry
import com.ironvellum.app.domain.PlannedPreset
import com.ironvellum.app.domain.ProgramGenerator
import com.ironvellum.app.domain.ProgramRequest
import com.ironvellum.app.domain.ProgramRules
import com.ironvellum.app.domain.Ranked
import com.ironvellum.app.domain.Reason
import com.ironvellum.app.domain.ReasonTag
import com.ironvellum.app.domain.Recovery
import com.ironvellum.app.domain.Slot
import com.ironvellum.app.domain.StrengthProfile
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.domain.WeekEdit
import com.ironvellum.app.domain.WeekEdits
import com.ironvellum.app.domain.WorkoutPreset
import kotlin.math.abs

/*
 * Optimise rites, as data. The domain already ranks movements ([ProgramGenerator.suggest]), eases heavy muscles
 * ([ProgramGenerator.easing]), finds two movements doing one job ([ProgramGenerator.overlaps]) and spaces days
 * for recovery ([Recovery]); this file turns what they return into the changes the screen offers, each one a
 * list of edits to the week, and says what a kept subset does to coverage. Pure, so every line is a JVM test.
 */

private const val EN_DASH = "–"

/** What Optimise reads: the saved rites, the lifter's answers, the catalogue and what they have logged. */
internal data class OptimiseInput(
    val saved: List<WorkoutPreset>,
    val request: ProgramRequest,
    val catalogue: List<Exercise>,
    val strength: StrengthProfile,
) {
    /** The rites Weekly coverage counts, so the numbers here are the numbers there. */
    val counted: List<WorkoutPreset> = scheduledRoutine(saved) { it.scheduledDay }
    val week: List<PlannedPreset> = counted.map { it.toPlanned() }
    val ids: List<Long> = counted.map { it.id }
}

enum class OptKind(@StringRes private val labelRes: Int) {
    TRIM(R.string.optimise_kind_trim),
    SWAP(R.string.optimise_kind_swap),
    MOVE(R.string.optimise_kind_move),
    SPREAD(R.string.optimise_kind_spread),
    ;

    val label: String get() = Words.text(UiText.res(labelRes))
}

/** A rite moved to another weekday by [to]; [from] is where it was. Index into the counted week. */
internal data class DayMove(val presetIndex: Int, val rite: String, val from: Int?, val to: Int)

/**
 * One way to make a change. A change always has at least one; the first is the best match. [edits] are applied
 * to the week in order, [dayMoves] after them.
 */
internal data class OptOption(
    val label: String,
    val reason: String,
    val edits: List<WeekEdit> = emptyList(),
    val dayMoves: List<DayMove> = emptyList(),
)

/**
 * One suggestion the lifter keeps or skips. [id] is stable across a recompute so a tick survives the screen
 * rebuilding. [title] is the one line ("Dips → Close-grip Push-up"), [detail] why. [rite] is the rite it
 * touches, null for one that touches several.
 */
internal data class OptChange(
    val id: String,
    val kind: OptKind,
    val title: String,
    val detail: String,
    val rite: String?,
    val options: List<OptOption>,
    /** Movements the change is about, each with the muscles it mainly works ("Lats · Upper back"), for an overlap. */
    val movements: List<Pair<String, String>> = emptyList(),
)

internal data class HeavyFinding(val muscle: Muscle, val load: MuscleLoad, val changes: List<OptChange>)

/** The changes found in one rite whose movements overlap. */
internal data class OverlapRite(val presetIndex: Int, val rite: String, val changes: List<OptChange>)

internal data class OptimiseReport(
    val input: OptimiseInput,
    val loads: Map<Muscle, MuscleLoad>,
    val tally: CoverageTally,
    val gaps: List<Muscle>,
    /** The three ranked movements for each gap, best first; empty for a muscle nothing in the armoury trains. */
    val gapOptions: Map<Muscle, Ranked>,
    val heavy: List<HeavyFinding>,
    val overlaps: List<OverlapRite>,
    val spread: OptChange?,
    val tight: List<Recovery.TightPair>,
) {
    /** Nothing to suggest: no gap, no heavy muscle, no overlap, no tight days. */
    val nothing: Boolean get() = gaps.isEmpty() && heavy.isEmpty() && overlaps.isEmpty() && spread == null
    val findings: Int get() = gaps.size + heavy.size + overlaps.sumOf { it.changes.size } + if (spread != null) 1 else 0
}

// ------------------------------------------------------------------------------------------- the report

internal fun buildReport(input: OptimiseInput): OptimiseReport {
    val week = input.week
    val loads = loadsOf(week)
    val tally = coverageTally(loads)
    val gaps = tally.notTrained + tally.below.sortedBy { loads[it]?.weekly ?: 0.0 }

    val heavy = ProgramGenerator.easing(week, input.request, input.catalogue, input.strength).map { suggestion ->
        HeavyFinding(suggestion.muscle, suggestion.load, suggestion.options.map { easingChange(it, suggestion.muscle, input) })
    }

    val overlaps = week.mapIndexedNotNull { index, preset ->
        val changes = ProgramGenerator.overlaps(preset, input.request, input.catalogue).map { overlapChange(index, preset, it, input) }
        if (changes.isEmpty()) null else OverlapRite(index, preset.name, changes)
    }

    val gapOptions = gaps.associateWith { gapRanked(input, it) }

    return OptimiseReport(input, loads, tally, gaps, gapOptions, heavy, overlaps, spreadChange(input), Recovery.tightPairs(week))
}

private fun reasonOf(tag: ReasonTag): String {
    val muscle = tag.muscle?.label?.lowercase()
    val other = tag.other
    return Words.text(
        when (tag.reason) {
            Reason.DIRECT_MUSCLE ->
                if (muscle != null) UiText.res(R.string.optimise_reason_direct_muscle, muscle) else UiText.res(R.string.optimise_reason_direct)
            Reason.FILLS_SHORT_MUSCLE ->
                if (muscle != null) UiText.res(R.string.optimise_reason_fills_muscle, muscle) else UiText.res(R.string.optimise_reason_fills)
            Reason.FITS_GEAR -> UiText.themed(R.string.optimise_reason_fits_gear, R.string.optimise_reason_fits_gear__t)
            Reason.YOU_LOG_THIS -> UiText.res(R.string.optimise_reason_logged)
            Reason.LOW_OVERLAP ->
                if (other != null) UiText.res(R.string.optimise_reason_low_overlap_other, other) else UiText.res(R.string.optimise_reason_low_overlap)
            Reason.SAME_PATTERN_AS_MAIN -> UiText.res(R.string.optimise_reason_same_pattern)
            Reason.STRETCH_BIASED -> UiText.res(R.string.optimise_reason_stretch)
            Reason.COMPOUND_FIRST -> UiText.res(R.string.optimise_reason_compound_first)
            Reason.RECOVERY_SPACED -> UiText.res(R.string.optimise_reason_recovery_spaced)
            Reason.FITS_DAY_LENGTH -> UiText.res(R.string.optimise_reason_fits_day)
            Reason.STAND_IN_FOR ->
                if (other != null) UiText.res(R.string.optimise_reason_stand_in_other, other) else UiText.res(R.string.optimise_reason_stand_in)
            Reason.USED_RECENTLY -> UiText.res(R.string.optimise_reason_used_recently)
            Reason.OFTEN_SKIPPED -> UiText.res(R.string.optimise_reason_often_skipped)
        },
    )
}

/** "Trains rear delts directly · fits your armoury": at most two reasons, said in words. */
internal fun reasonLine(tags: List<ReasonTag>): String =
    tags.take(2).joinToString(" · ") { reasonOf(it) }.replaceFirstChar { it.uppercase() }

/** "3 hard sets · ~6–10 reps · 60 kg": what a movement would be prescribed, effort first. */
internal fun prescriptionLine(entry: PlannedEntry, load: String?): String =
    "${hardSetsText(entry.sets)} · ${movementDetail(entry, load)}"

// ---------------------------------------------------------------------------------------------- impact

/** One muscle's weekly hard sets and state before and after a change. */
internal data class ImpactRow(
    val muscle: Muscle,
    val before: Double,
    val after: Double,
    val beforeState: CoverageState,
    val afterState: CoverageState,
) {
    /** "Below minimum, 2 → Covered, 5 hard sets", or "Stays covered, 8 → 5 hard sets". */
    val text: String
        get() = Words.text(
            if (beforeState == afterState) {
                UiText.res(R.string.optimise_impact_stays, afterState.label.lowercase(), trimSets(before), trimSets(after))
            } else {
                UiText.res(R.string.optimise_impact_moves, beforeState.label, trimSets(before), afterState.label, trimSets(after))
            },
        )

    /** The row read aloud: the muscle, then its text. */
    val spoken: String get() = "${muscle.label}: $text"
}

/**
 * What [after] does to coverage against [before], the muscles that move only, the one in [focus] first, then
 * the ones that change state, then the biggest change. A muscle that is not judged (the neck) is left out.
 */
internal fun impactRows(
    before: List<PlannedPreset>,
    after: List<PlannedPreset>,
    focus: Muscle? = null,
    limit: Int = 5,
): List<ImpactRow> {
    val was = ProgramRules.coverage(before)
    val now = ProgramRules.coverage(after)
    return JUDGED_MUSCLES.mapNotNull { muscle ->
        val b = was.getValue(muscle)
        val a = now.getValue(muscle)
        if (abs(a.weekly - b.weekly) < 0.05 && stateOf(a) == stateOf(b)) return@mapNotNull null
        ImpactRow(muscle, b.weekly, a.weekly, stateOf(b), stateOf(a))
    }.sortedWith(
        compareByDescending<ImpactRow> { it.muscle == focus }
            .thenByDescending { it.beforeState != it.afterState }
            .thenByDescending { abs(it.after - it.before) }
            .thenBy { it.muscle.ordinal },
    ).take(limit)
}

/** Resolves picked options into the week they make: every option's edits in order, then the day moves. */
internal fun resolveWeek(week: List<PlannedPreset>, picked: List<OptOption>): List<PlannedPreset> {
    val edited = WeekEdits.apply(week, picked.flatMap { it.edits })
    val moves = picked.flatMap { it.dayMoves }
    return edited.mapIndexed { i, rite -> moves.lastOrNull { it.presetIndex == i }?.let { rite.copy(scheduledDay = it.to) } ?: rite }
}

// -------------------------------------------------------------------------------------- heavy, overlap

private fun dayName(day: Int?): String = dayLabel(day)

private fun easingChange(option: EasingOption, muscle: Muscle, input: OptimiseInput): OptChange {
    val week = input.week
    val index = WeekEdits.indexOf(week, option.presetName)
    val saves = (option.coverageDelta[muscle] ?: 0.0).let { if (it < 0) -it else 0.0 }
    val savesText = if (saves > 0.0) " " + Words.text(UiText.res(R.string.optimise_saves, trimSets(saves))) else ""
    return when (val op = option.op) {
        is ChangeOp.SetSets -> {
            val was = week.getOrNull(index)?.entries?.firstOrNull { it.exerciseName.equals(op.exerciseName, true) }?.sets
            OptChange(
                id = option.id,
                kind = OptKind.TRIM,
                title = Words.text(UiText.res(R.string.optimise_trim_title, op.exerciseName)),
                detail = Words.text(
                    if (was != null) UiText.res(R.string.optimise_trim_detail, hardSetsText(was), hardSetsText(op.sets))
                    else UiText.res(R.string.optimise_trim_detail_down, hardSetsText(op.sets)),
                ) + savesText,
                rite = option.presetName,
                options = listOf(OptOption(Words.text(UiText.res(R.string.optimise_trim_option)), reasonLine(option.tags), listOf(WeekEdit(index, op)))),
            )
        }

        is ChangeOp.SwapEntry -> {
            val from = op.fromName
            val ranked = option.alternatives?.options.orEmpty()
            val original = week.getOrNull(index)?.entries?.firstOrNull { it.exerciseName.equals(from, true) }
            val options = ranked.mapIndexed { k, candidate ->
                val entry = if (k == 0) op.to else original?.let { Easing.entryFor(it, candidate.exercise, input.request, input.strength) } ?: op.to
                OptOption(
                    label = candidate.exercise.name,
                    reason = reasonLine(candidate.tags),
                    edits = listOf(WeekEdit(index, ChangeOp.SwapEntry(from, entry))),
                )
            }.ifEmpty { listOf(OptOption(op.to.exerciseName, reasonLine(option.tags), listOf(WeekEdit(index, op)))) }
            OptChange(
                id = option.id,
                kind = OptKind.SWAP,
                title = "$from → ${options.first().label}",
                detail = Words.text(UiText.res(R.string.optimise_swap_detail, muscle.label.lowercase())) + savesText,
                rite = option.presetName,
                options = options,
            )
        }

        is ChangeOp.MoveEntryToDay -> {
            val fromDay = week.getOrNull(index)?.scheduledDay
            OptChange(
                id = option.id,
                kind = OptKind.MOVE,
                title = "${op.exerciseName}, ${dayName(fromDay)} → ${dayName(op.toDay)}",
                detail = Words.text(UiText.res(R.string.optimise_move_detail, muscle.label.lowercase())),
                rite = option.presetName,
                options = listOf(OptOption(Words.text(UiText.res(R.string.optimise_move_option)), reasonLine(option.tags), listOf(WeekEdit(index, op)))),
            )
        }

        else -> error("easing offers no ${option.kind} edit as $op")
    }
}

private fun overlapChange(index: Int, preset: PlannedPreset, finding: OverlapFinding, input: OptimiseInput): OptChange {
    val (replaced, kept) = if (finding.a.sets < finding.b.sets) finding.a to finding.b else finding.b to finding.a
    val options = finding.alternatives.options.map { candidate: Candidate ->
        val entry = Easing.entryFor(replaced, candidate.exercise, input.request, input.strength)
        OptOption(candidate.exercise.name, reasonLine(candidate.tags), listOf(WeekEdit(index, ChangeOp.SwapEntry(replaced.exerciseName, entry))))
    }.ifEmpty {
        listOf(
            OptOption(
                Words.text(UiText.res(R.string.optimise_overlap_remove_option, replaced.exerciseName)),
                Words.text(UiText.res(R.string.optimise_overlap_remove_reason)),
                listOf(WeekEdit(index, ChangeOp.RemoveEntry(replaced.exerciseName))),
            ),
        )
    }
    return OptChange(
        id = "overlap:${preset.name}:${replaced.exerciseName}:${kept.exerciseName}",
        kind = OptKind.SWAP,
        title = "${replaced.exerciseName} → ${options.first().label}",
        detail = Words.text(
            UiText.res(R.string.optimise_overlap_detail, finding.a.exerciseName, finding.b.exerciseName, finding.muscle.label.lowercase()),
        ),
        rite = preset.name,
        options = options,
        movements = listOf(finding.a, finding.b).map { entry ->
            entry.exerciseName to MuscleMap.profile(entry)?.let { profile ->
                musclesAt(profile.muscles, ShareLevel.MAIN).joinToString(" · ") { it.label }
            }.orEmpty()
        },
    )
}

// ------------------------------------------------------------------------------------------------ spread

/** "Chest is hit hard on Mon and Tue, 24 hours apart. 48 hours or more lets it recover." */
internal fun tightLine(pair: Recovery.TightPair): String = Words.text(
    UiText.res(
        R.string.optimise_tight_line,
        pair.muscle.label, dayShort(pair.dayA), dayShort(pair.dayB), pair.hours, Recovery.MIN_GAP_HOURS,
    ),
)

private fun spreadChange(input: OptimiseInput): OptChange? {
    val week = input.week
    val tight = Recovery.tightPairs(week)
    if (tight.isEmpty()) return null
    // shortcut: rites are matched by name, so a week with two rites of one name is left alone;
    // upgrade if rites may share names.
    if (week.map { it.name }.distinct().size != week.size) return null
    val spaced = Recovery.spaceDays(week)
    val moves = week.mapIndexedNotNull { i, rite ->
        val to = spaced.first { it.name == rite.name }.scheduledDay
        if (to != null && to != rite.scheduledDay) DayMove(i, rite.name, rite.scheduledDay, to) else null
    }
    if (moves.isEmpty()) return null
    return OptChange(
        id = "spread",
        kind = OptKind.SPREAD,
        title = moves.joinToString(", ") { "${it.rite} ${dayShort(it.from)} → ${dayShort(it.to)}" },
        detail = tightLine(tight.first()),
        rite = null,
        options = listOf(
            OptOption(
                Words.text(UiText.res(R.string.optimise_spread_option)),
                Words.text(UiText.res(R.string.optimise_spread_reason)),
                dayMoves = moves,
            ),
        ),
    )
}

// ---------------------------------------------------------------------------------------------- gaps

/** The three ranked movements for a short muscle, best first. Empty when nothing in the armoury fits. */
internal fun gapRanked(input: OptimiseInput, muscle: Muscle): Ranked =
    ProgramGenerator.suggest(input.request, Slot.ShortMuscle(muscle), input.week, input.catalogue, input.strength)

/** [candidate] prescribed the way the Forge would, at its starting sets. */
internal fun gapEntry(input: OptimiseInput, candidate: Candidate): PlannedEntry =
    newPlannedEntry(
        candidate.exercise, null, input.request.volume, input.request.focus, input.request.equipment, input.strength,
    )

/** One rite a gap movement could go into: what it holds now and whether it is the best fit. */
internal data class RiteChoice(
    val index: Int,
    val name: String,
    val day: Int?,
    val movements: Int,
    val minutes: Int,
    val best: Boolean,
    /** Why it is the best fit, when it is. */
    val note: String?,
)

/** "Monday · Upper A": the rite and the day it is on. */
internal fun RiteChoice.title(): String = if (day != null) "${dayName(day)} · $name" else name

/** "5 movements · ~55 min". */
internal fun RiteChoice.line(): String = Words.text(UiText.res(R.string.optimise_rite_line, movementsText(movements), minutes))

/**
 * Every counted rite as a place for [entry], the best fit first. The first is the best the recovery model
 * finds (spaced days, a day that stays inside the clock); the rest follow by length, shortest day first.
 */
internal fun riteChoices(input: OptimiseInput, entry: PlannedEntry): List<RiteChoice> {
    val week = input.week
    val focus = input.request.focus
    val ranked = Recovery.bestFitDays(entry, week, input.request).options
    val bestByIndex = ranked.mapNotNull { day ->
        week.indexOfFirst { it.scheduledDay == day.day && it.name == day.presetName }.takeIf { it >= 0 }?.let { it to day }
    }
    val order = bestByIndex.map { it.first } +
        week.indices.filter { i -> bestByIndex.none { it.first == i } }.sortedBy { ProgramRules.dayMinutes(week[it], focus) }
    return order.mapIndexed { rank, i ->
        val rite = week[i]
        val tags = bestByIndex.firstOrNull { it.first == i }?.second?.tags.orEmpty()
        RiteChoice(
            index = i,
            name = rite.name,
            day = rite.scheduledDay,
            movements = rite.entries.size,
            minutes = ProgramRules.dayMinutes(rite, focus),
            best = rank == 0,
            note = when {
                rank == 0 && tags.isNotEmpty() ->
                    Words.text(UiText.res(R.string.optimise_rite_best_fit_because, reasonLine(tags).replaceFirstChar { it.lowercase() }))
                rank == 0 -> Words.text(UiText.res(R.string.optimise_rite_best_fit))
                else -> null
            },
        )
    }
}

/** The week with [entry] added to the rite at [index]. */
internal fun withAdded(week: List<PlannedPreset>, index: Int, entry: PlannedEntry): List<PlannedPreset> =
    WeekEdits.apply(week, listOf(WeekEdit(index, ChangeOp.AddEntry(entry))))
