package com.ironvellum.app.ui.program

import com.ironvellum.app.data.Words
import com.ironvellum.app.domain.Coverage
import com.ironvellum.app.domain.Evidence
import com.ironvellum.app.domain.Muscle
import com.ironvellum.app.domain.MuscleLoad
import com.ironvellum.app.domain.PlannedPreset
import com.ironvellum.app.domain.ProgramRules

/**
 * Coverage v2, in words and counts: where each muscle stands against the weekly hard-set minimum, and
 * whether it runs heavy. Pure, so the figure, the rows, the Forge chip and the Train card all read ONE rule
 * and cannot disagree. Every state is also a word and a mark; colour is never the only carrier.
 */
enum class CoverageState(val label: String) {
    NOT_TRAINED("Not trained"),
    BELOW_MINIMUM("Below minimum"),
    COVERED("Covered"),
    HEAVY("Heavy"),
}

/**
 * [load]'s state. Heavy rides on top of covered (a heavy muscle is by definition past the minimum); a
 * [dismissed] heavy flag reads as plain covered, which it still is.
 */
internal fun stateOf(load: MuscleLoad, dismissedHeavy: Boolean = false): CoverageState = when (load.coverage) {
    Coverage.NOT_TRAINED -> CoverageState.NOT_TRAINED
    Coverage.BELOW_MINIMUM -> CoverageState.BELOW_MINIMUM
    Coverage.COVERED -> if (load.heavy && !dismissedHeavy) CoverageState.HEAVY else CoverageState.COVERED
}

/**
 * Every muscle the page judges: the ranged majors, then the helpers. The neck is on the figure but has no
 * weekly dose, so it is drawn and never counted.
 */
internal val JUDGED_MUSCLES: List<Muscle> = ProgramRules.TRACKED + ProgramRules.HELPERS

/**
 * Per-muscle loads of [presets] for a cycle ([pooled] false) or for a pooled week such as the last seven
 * days of logging ([pooled] true). A pooled week is one big rite, so its "busiest session" is the whole week
 * and would call a perfectly ordinary 12-set week heavy; only the weekly total may flag it.
 */
internal fun loadsOf(presets: List<PlannedPreset>, pooled: Boolean = false): Map<Muscle, MuscleLoad> {
    val loads = ProgramRules.coverage(presets)
    return if (pooled) loads.mapValues { (_, load) -> load.copy(maxSession = 0.0) } else loads
}

/** The judged muscles sorted into their states. */
internal data class CoverageTally(val states: Map<Muscle, CoverageState>) {
    private fun of(state: CoverageState) = JUDGED_MUSCLES.filter { states[it] == state }

    val heavy: List<Muscle> get() = of(CoverageState.HEAVY)
    // A warning is about direct work: the muscles a lift trains as its primary mover. The helpers stay on the page
    // and the figure in their own state, but never count toward a gap (the plan's shortfall note counts the same way).
    val below: List<Muscle> get() = of(CoverageState.BELOW_MINIMUM).filter { it in ProgramRules.TRACKED }
    val notTrained: List<Muscle> get() = of(CoverageState.NOT_TRAINED).filter { it in ProgramRules.TRACKED }
    val covered: List<Muscle> get() = of(CoverageState.COVERED)

    /** Muscles short of the minimum or not trained at all: the one count every summary line gives. */
    val gaps: Int get() = below.size + notTrained.size

    val allClear: Boolean get() = heavy.isEmpty() && gaps == 0

    /** The state the one-line summary leads with: a gap first, then heavy, else covered. */
    val lead: CoverageState
        get() = when {
            below.isNotEmpty() -> CoverageState.BELOW_MINIMUM
            notTrained.isNotEmpty() -> CoverageState.NOT_TRAINED
            heavy.isNotEmpty() -> CoverageState.HEAVY
            else -> CoverageState.COVERED
        }
}

internal fun coverageTally(loads: Map<Muscle, MuscleLoad>, dismissedHeavy: Set<Muscle> = emptySet()): CoverageTally =
    CoverageTally(
        JUDGED_MUSCLES.associateWith { muscle ->
            loads[muscle]?.let { stateOf(it, muscle in dismissedHeavy) } ?: CoverageState.NOT_TRAINED
        },
    )

internal fun coverageTally(presets: List<PlannedPreset>, dismissedHeavy: Set<Muscle> = emptySet()): CoverageTally =
    coverageTally(loadsOf(presets), dismissedHeavy)

/**
 * The one-line summary, worded the same on every screen that shows it: "3 muscles need more sets",
 * "1 heavy · 1 muscle needs more sets", or "Every muscle covered". Below minimum and not trained are one
 * thing to a lifter, a muscle that wants more hard sets, so they are one count here; the page keeps the
 * two states apart on the figure and in its legend. A hint only, never a gate.
 */
internal fun coverageHeadline(tally: CoverageTally): String {
    val parts = buildList {
        if (tally.heavy.isNotEmpty()) add("${tally.heavy.size} heavy")
        if (tally.gaps > 0) add("${tally.gaps} ${if (tally.gaps == 1) "muscle needs" else "muscles need"} more sets")
    }
    return if (parts.isEmpty()) "Every muscle covered" else parts.joinToString(" · ")
}

/** Why a muscle "needs more sets", said once beside the summary wherever there is a gap. */
internal val COVERAGE_REASON: String get() = "About ${ProgramRules.COVERED_MIN.toInt()} hard sets a week per muscle is a sound floor."

/** Which of the coverage dock's two actions have something to do. */
internal data class DockActions(val forge: Boolean, val optimise: Boolean) {
    val any: Boolean get() = forge || optimise
}

/**
 * A gap is something to forge a rite for. A gap or a heavy muscle in the PLANNED week is something to optimise
 * the saved rites for; the last 7 days are what happened, so they offer Forge only, and with no saved rite there
 * is nothing to change.
 */
internal fun dockActions(tally: CoverageTally, planned: Boolean, hasRites: Boolean): DockActions =
    DockActions(forge = tally.gaps > 0, optimise = planned && hasRites && !tally.allClear)

/** The quiet line for rites with no day: "1 rite has no day, so it isn't counted". */
internal fun uncountedRitesLine(count: Int): String =
    if (count == 1) "1 ${Words.rite.s} has no day, so it isn't counted" else "$count ${Words.rite.pl} have no day, so they aren't counted"

/** A muscle's weekly hard sets as the page says them: "0 hard sets", "1 hard set", "3.5 hard sets". */
internal fun hardSetsLabel(sets: Double): String {
    val figure = trimSets(sets)
    return "$figure hard ${if (figure == "1") "set" else "sets"}"
}

/**
 * The muscles that need a look, in the order the list shows them: heavy first (most sets leading), then
 * untrained, then below the minimum with the least trained leading.
 */
internal fun attentionOrder(loads: Map<Muscle, MuscleLoad>, tally: CoverageTally): List<Muscle> {
    val weekly = { muscle: Muscle -> loads[muscle]?.weekly ?: 0.0 }
    return tally.heavy.sortedByDescending(weekly) + tally.notTrained + tally.below.sortedBy(weekly)
}

/** Why a muscle runs heavy, in a sentence, and the paper behind it. */
internal fun heavyReason(load: MuscleLoad): Pair<String, Evidence> =
    if (load.weekly > ProgramRules.HEAVY_WEEK) {
        "Past about ${ProgramRules.HEAVY_WEEK.toInt()} sets a week, each extra set adds less. " +
            "A hint about recovery, not a limit." to Evidence.PELLAND_2026
    } else {
        "More than ${ProgramRules.HEAVY_SESSION.toInt()} hard sets in one ${Words.rite.s} is a lot to recover from. " +
            "Spreading them over more days helps. A hint, not a limit." to Evidence.REMMERT_2025
    }

/** The line under a lit muscle on the figure: "Hamstrings · 6 hard sets this week · Below minimum". */
internal fun coverageMuscleLine(muscle: Muscle, load: MuscleLoad?, dismissedHeavy: Boolean = false): String {
    val sets = "${hardSetsLabel(load?.weekly ?: 0.0)} this week"
    // The neck is drawn but never judged: it has a count and no verdict.
    if (muscle !in JUDGED_MUSCLES) return muscleLine(muscle, sets)
    val state = load?.let { stateOf(it, dismissedHeavy) } ?: CoverageState.NOT_TRAINED
    return muscleLine(muscle, "$sets · ${state.label}")
}
