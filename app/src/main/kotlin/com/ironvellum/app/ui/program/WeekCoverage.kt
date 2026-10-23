package com.ironvellum.app.ui.program

import androidx.annotation.StringRes
import com.ironvellum.app.R
import com.ironvellum.app.data.Words
import com.ironvellum.app.domain.Coverage
import com.ironvellum.app.domain.Exercise
import com.ironvellum.app.domain.HardSets
import com.ironvellum.app.domain.Muscle
import com.ironvellum.app.domain.MuscleLoad
import com.ironvellum.app.domain.PlannedEntry
import com.ironvellum.app.domain.PlannedPreset
import com.ironvellum.app.domain.ProgramRules
import com.ironvellum.app.domain.SessionSet
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.domain.WorkoutPreset
import com.ironvellum.app.domain.WorkoutSession
import java.time.Instant
import java.time.ZoneId

/**
 * Where a muscle stands for the current Monday to Sunday week, with what is done and what the plan still holds
 * on one map. [DONE] has 4+ hard sets already, [PLANNED] gets there with the plan's remaining days, [SHORT]
 * stays under 4 even with the plan, [HEAVY] runs past the weekly or session ceiling, [NONE] has nothing at all.
 */
enum class WeekState(@StringRes private val labelRes: Int) {
    DONE(R.string.coverage_week_state_done),
    PLANNED(R.string.coverage_week_state_planned),
    SHORT(R.string.coverage_week_state_short),
    HEAVY(R.string.coverage_state_heavy),
    NONE(R.string.coverage_week_state_none),
    ;

    val label: String get() = Words.text(UiText.res(labelRes))
}

/** One muscle's week: hard sets [done] so far, [planned] still to come, and its busiest single session. */
data class WeekMuscle(val muscle: Muscle, val done: Double, val planned: Double, val maxSession: Double) {
    val total: Double get() = done + planned

    /** The same [MuscleLoad] the rest of coverage judges, so the 4 and 20 lines and the session ceiling are one rule. */
    val load: MuscleLoad get() = MuscleLoad(muscle, total, maxSession)
}

data class WeekCoverage(val muscles: Map<Muscle, WeekMuscle> = emptyMap()) {

    fun of(muscle: Muscle): WeekMuscle = muscles[muscle] ?: WeekMuscle(muscle, 0.0, 0.0, 0.0)

    /** A dismissed heavy flag reads as the muscle's plain state, which it still is. */
    internal fun stateOf(muscle: Muscle, dismissedHeavy: Boolean = false): WeekState {
        val week = of(muscle)
        val load = week.load
        return when {
            load.heavy && !dismissedHeavy -> WeekState.HEAVY
            load.coverage == Coverage.NOT_TRAINED -> WeekState.NONE
            load.coverage == Coverage.BELOW_MINIMUM -> WeekState.SHORT
            MuscleLoad(muscle, week.done, 0.0).coverage == Coverage.COVERED -> WeekState.DONE
            else -> WeekState.PLANNED
        }
    }

    internal fun states(dismissed: Set<Muscle> = emptySet()): Map<Muscle, WeekState> =
        JUDGED_MUSCLES.associateWith { stateOf(it, it in dismissed) }

    internal fun hasAnything(): Boolean = muscles.values.any { it.total > 0.0 }

    /** The week's counts under the older four-state words, which the chip, the explainer and the dock already read. */
    internal fun tally(dismissed: Set<Muscle> = emptySet()): CoverageTally = CoverageTally(
        states(dismissed).mapValues { (_, state) ->
            when (state) {
                WeekState.DONE, WeekState.PLANNED -> CoverageState.COVERED
                WeekState.SHORT -> CoverageState.BELOW_MINIMUM
                WeekState.HEAVY -> CoverageState.HEAVY
                WeekState.NONE -> CoverageState.NOT_TRAINED
            }
        },
    )
}

/**
 * One week from what was done and what the plan still has. [remaining] goes through [ProgramRules.coverage], the
 * planned-sets rule the cycle always used; [done] and [doneMaxSession] come from [HardSets]. With nothing done
 * (a plan being proposed) every muscle the plan reaches reads Planned, Short or Heavy and none reads Done.
 */
internal fun weekCoverage(
    done: Map<Muscle, Double>,
    doneMaxSession: Map<Muscle, Double>,
    remaining: List<PlannedPreset>,
): WeekCoverage {
    val planned = ProgramRules.coverage(remaining)
    return WeekCoverage(
        Muscle.entries.associateWith { muscle ->
            WeekMuscle(
                muscle,
                done = done[muscle] ?: 0.0,
                planned = planned[muscle]?.weekly ?: 0.0,
                maxSession = maxOf(doneMaxSession[muscle] ?: 0.0, planned[muscle]?.maxSession ?: 0.0),
            )
        },
    )
}

/** A plan with nothing done yet, as setup and the plan builder preview it. */
internal fun plannedOnlyWeek(presets: List<PlannedPreset>): WeekCoverage = weekCoverage(emptyMap(), emptyMap(), presets)

/** The hard sets logged inside one week: the total per muscle and the busiest single session per muscle. */
internal data class DoneWeek(val sets: Map<Muscle, Double>, val maxSession: Map<Muscle, Double>)

internal fun doneInWeek(
    history: List<Pair<WorkoutSession, List<SessionSet>>>,
    exercises: Map<Long, Exercise>,
    fromMs: Long,
    toMs: Long,
): DoneWeek {
    val total = mutableMapOf<Muscle, Double>()
    val busiest = mutableMapOf<Muscle, Double>()
    for ((session, sets) in history) {
        val at = session.completedAtMs ?: session.startedAtMs
        val credit = HardSets.inRange(HardSets.logged(at, sets, exercises), fromMs, toMs)
        for ((muscle, value) in credit) {
            total.merge(muscle, value, Double::plus)
            if (value > (busiest[muscle] ?: 0.0)) busiest[muscle] = value
        }
    }
    return DoneWeek(total, busiest)
}

/**
 * The week's hard sets as one pseudo-rite (sets per movement and modifiers), so a lit muscle can name what it was
 * done with. Which sets count is [HardSets.isHard]'s call: this only groups them for display.
 */
internal fun doneRite(
    history: List<Pair<WorkoutSession, List<SessionSet>>>,
    exercises: Map<Long, Exercise>,
    fromMs: Long,
    toMs: Long,
): List<PlannedPreset> {
    val hard = history.flatMap { (session, sets) -> HardSets.logged(session.completedAtMs ?: session.startedAtMs, sets, exercises) }
        .filter { it.atMs >= fromMs && it.atMs < toMs && HardSets.isHard(it.kind, it.rpe, it.done, it.reps, it.weightKg, it.bodyweight) }
    if (hard.isEmpty()) return emptyList()
    val entries = hard.groupBy { it.exerciseName to it.modifiers }.map { (key, sets) ->
        PlannedEntry(key.first, sets.size, sets.sumOf { it.reps } / sets.size, null, key.second)
    }
    return listOf(PlannedPreset(Words.text(UiText.res(R.string.coverage_week_done_rite)), "", null, entries))
}

/**
 * The rites the rest of the week still holds: the scheduled routine ([scheduledRoutine], so unscheduled rites
 * stay out once any has a day) whose day is today or later and that no trial has sealed since [weekStartMs].
 * With no day on any rite the whole board counts unless it was sealed, as the old Planned view did.
 */
internal fun remainingRites(
    presets: List<WorkoutPreset>,
    history: List<Pair<WorkoutSession, List<SessionSet>>>,
    today: Int,
    weekStartMs: Long,
): List<PlannedPreset> {
    val sealed = history.filter { (it.first.completedAtMs ?: 0L) >= weekStartMs }.mapNotNull { it.first.presetId }.toSet()
    return scheduledRoutine(presets) { it.scheduledDay }
        .filter { (it.scheduledDay ?: today) >= today && it.id !in sealed }
        .map { it.toPlanned() }
}

/** Monday 00:00 of the week [nowMs] falls in, and the next Monday 00:00, in [zone]: a week is 167 or 169 hours at a clock change. */
internal fun weekBounds(nowMs: Long, zone: ZoneId): Pair<Long, Long> {
    val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
    return monday.atStartOfDay(zone).toInstant().toEpochMilli() to monday.plusDays(7).atStartOfDay(zone).toInstant().toEpochMilli()
}

/** The week model from the stores' own data. */
internal fun weekCoverageOf(
    history: List<Pair<WorkoutSession, List<SessionSet>>>,
    exercises: Map<Long, Exercise>,
    presets: List<WorkoutPreset>,
    nowMs: Long,
    zone: ZoneId,
): WeekCoverage {
    val (from, to) = weekBounds(nowMs, zone)
    val today = Instant.ofEpochMilli(nowMs).atZone(zone).dayOfWeek.value
    val done = doneInWeek(history, exercises, from, to)
    return weekCoverage(done.sets, done.maxSession, remainingRites(presets, history, today, from))
}

// ---------------------------------------------------------------- rows

/** The rows that need a look first: heavy, then short (nearest to 4 first, nothing at all last), then on track. */
internal data class WeekRows(val needing: List<Muscle>, val covered: List<Muscle>)

internal fun weekRows(week: WeekCoverage, states: Map<Muscle, WeekState>): WeekRows {
    fun inState(state: WeekState) = JUDGED_MUSCLES.filter { states[it] == state }
    // Helpers stay on the page when they matter but never count as a gap: the tally's own rule.
    val short = inState(WeekState.SHORT).filter { it in ProgramRules.TRACKED }.sortedByDescending { week.of(it).total }
    val none = inState(WeekState.NONE).filter { it in ProgramRules.TRACKED }
    val heavy = inState(WeekState.HEAVY).sortedByDescending { week.of(it).total }
    val planned = inState(WeekState.PLANNED).sortedByDescending { week.of(it).total }
    return WeekRows(heavy + short + none + planned, inState(WeekState.DONE))
}

/** "2 muscles short, 1 heavy", or "on track" when neither: Train's one coverage line, from the week's tally. */
internal fun weekLine(tally: CoverageTally): String {
    val parts = buildList {
        if (tally.gaps > 0) add(Words.text(UiText.plural(R.plurals.coverage_week_line_short, tally.gaps)))
        if (tally.heavy.isNotEmpty()) add(Words.text(UiText.res(R.string.coverage_week_line_heavy, tally.heavy.size)))
    }
    return if (parts.isEmpty()) Words.text(UiText.res(R.string.coverage_week_line_on_track)) else parts.joinToString(", ")
}
