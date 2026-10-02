package com.ironvellum.app.ui.program

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import com.ironvellum.app.domain.Muscle
import com.ironvellum.app.domain.MuscleMap
import com.ironvellum.app.domain.PlannedPreset
import com.ironvellum.app.domain.ProgramRules

/**
 * The one muscle a figure has lit, or none. Tapping a muscle on any figure sets it; the figure never
 * navigates anywhere, and only the coverage screen reads it to narrow a list.
 */
@Composable
internal fun rememberMuscleSelection(): MutableState<Muscle?> = rememberSaveable { mutableStateOf<Muscle?>(null) }

// ---------------------------------------------------------------- the line under a figure

/** "Triceps" alone, or "Triceps · assist in Bench Press": the muscle, then at most one fact. */
internal fun muscleLine(muscle: Muscle, fact: String? = null): String =
    if (fact.isNullOrBlank()) muscle.label else "${muscle.label} · $fact"

/**
 * One exercise's figure: how much of [muscle] the exercise works ([share] null when it is not in the
 * profile), in the sheet's own MAIN / ASSIST classification - "Lats · main in Pull-up".
 */
internal fun exerciseMuscleLine(muscle: Muscle, share: Double?, exerciseName: String): String = muscleLine(
    muscle,
    when {
        share == null || share <= 0.0 -> "not worked in $exerciseName"
        shareLevel(share) == ShareLevel.MAIN -> "main in $exerciseName"
        else -> "assist in $exerciseName"
    },
)

/** One rite's figure: the sets the rite credits [muscle] - "Lats · 4.5 sets in Pull A". */
internal fun riteMuscleLine(muscle: Muscle, sets: Double, riteName: String): String = muscleLine(
    muscle,
    // riteSetsLabel floors at a tenth of a set, so a muscle the rite never reaches is said apart.
    if (sets <= 0.0) "not worked in $riteName" else "${riteSetsLabel(sets)} in $riteName",
)

/**
 * The routine's figure: the week's sets against the muscle's own target range - "Hamstrings · 6 sets
 * this week · target 12–20". A muscle the map does not judge (the neck) has no target, so it shows just its sets.
 */
internal fun coverageMuscleLine(muscle: Muscle, sets: Double, goal: CoverageGoal): String {
    val count = trimSets(sets)
    val fact = if (muscle in JUDGED) {
        // The range, not just its floor: "20 / 8" read as a broken fraction once a muscle passed it.
        "$count ${if (count == "1") "set" else "sets"} this week · target ${targetLabel(rangeFor(muscle, goal))}"
    } else {
        "$count ${if (count == "1") "set" else "sets"} this week"
    }
    return muscleLine(muscle, fact)
}

// ---------------------------------------------------------------- the coverage filter

/** One rite's part in a muscle's weekly sets: its exercises that reach the muscle, largest first. */
internal data class RiteContribution(val rite: String, val exercises: List<ProgramRules.MuscleCredit>) {
    val credited: Double get() = exercises.sumOf { it.credited }
}

/**
 * What the coverage screen narrows to when [muscle] is lit: the rites in [presets] that train it, each
 * with the exercises behind its number, both largest first. The same credit rule as the weekly volume
 * (sets times the muscle's share of the exercise), so the figures add up to the line's total.
 */
internal fun ritesTraining(presets: List<PlannedPreset>, muscle: Muscle): List<RiteContribution> =
    presets.mapNotNull { preset ->
        val setsByExercise = linkedMapOf<Pair<String, String>, Int>()
        for (entry in preset.entries) setsByExercise.merge(entry.exerciseName to entry.modifiers, entry.sets, Int::plus)
        val exercises = setsByExercise.mapNotNull { (key, sets) ->
            val share = MuscleMap.profile(key.first, key.second)?.muscles?.get(muscle) ?: return@mapNotNull null
            if (share > 0.0) ProgramRules.MuscleCredit(key.first, key.second, sets, share) else null
        }.sortedByDescending { it.credited }
        if (exercises.isEmpty()) null else RiteContribution(preset.name, exercises)
    }.sortedByDescending { it.credited }

/** A weekly range as "12–18", or "3+" for a helper's open-ended floor (its ceiling is a huge sentinel). */
internal fun targetLabel(range: ClosedFloatingPointRange<Double>): String =
    if (range.endInclusive < OPEN_ENDED_SETS && range.endInclusive > range.start) {
        "${trimSets(range.start)}–${trimSets(range.endInclusive)}"
    } else {
        "${trimSets(range.start)}+"
    }

/** A weekly ceiling at or past this many sets is no ceiling at all. */
private const val OPEN_ENDED_SETS = 100.0
