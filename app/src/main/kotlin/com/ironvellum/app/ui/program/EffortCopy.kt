package com.ironvellum.app.ui.program

import com.ironvellum.app.domain.EffortCue
import com.ironvellum.app.domain.Equipment
import com.ironvellum.app.domain.PlannedEntry
import com.ironvellum.app.domain.PlannedPreset
import com.ironvellum.app.domain.ProgramGenerator
import com.ironvellum.app.domain.ProgramRules
import com.ironvellum.app.domain.VolumeLevel

/*
 * Effort-first words. A set is a HARD set - taken near failure - and that, not the rep count, is the dose.
 * Reps are only a guide to the load. Every screen that prescribes work says it through these, so the Forge,
 * first-run setup and the day editor cannot drift into three dialects. Pure, so each line has a JVM test.
 */

private const val EN_DASH = "–"

/** "1 hard set", "3 hard sets". */
internal fun hardSetsText(n: Int): String = "$n hard ${if (n == 1) "set" else "sets"}"

/** "4 movements". */
internal fun movementsText(n: Int): String = "$n ${if (n == 1) "movement" else "movements"}"

/** How close to failure the sets are taken, as a phrase that follows the set count. */
internal fun effortCue(effort: EffortCue): String = when (effort) {
    EffortCue.RIR_0_2 -> "stop 0${EN_DASH}2 reps short of failure"
    EffortCue.TO_FAILURE -> "to failure"
}

/** The same cue as a sentence of its own, with the reminder that reps only steer the load. */
internal fun effortSentence(effort: EffortCue): String = when (effort) {
    EffortCue.RIR_0_2 -> "Stop 0${EN_DASH}2 reps short of failure. Reps are a guide to load."
    EffortCue.TO_FAILURE -> "Take the last set to failure. Reps are a guide to load."
}

/** "~6–10 reps", or "~8 reps" when the window is one number. */
internal fun repGuide(entry: PlannedEntry): String =
    if (entry.repLow == entry.repHigh) "~${entry.repLow} reps" else "~${entry.repLow}$EN_DASH${entry.repHigh} reps"

/** The movement row's first line under its name: "3 hard sets · stop 0–2 reps short of failure". */
internal fun movementLine(entry: PlannedEntry): String = "${hardSetsText(entry.sets)} · ${effortCue(entry.effort)}"

/** The movement row's right-hand figure: "~6–10 reps · 60 kg", or just the reps for bodyweight work. */
internal fun movementDetail(entry: PlannedEntry, load: String?): String =
    if (load == null) repGuide(entry) else "${repGuide(entry)} · $load"

/** What the generator suggested, said under the set stepper so the lifter knows what they are changing. */
internal fun suggestedSets(entry: PlannedEntry): String {
    val range = if (entry.setsMin == entry.setsMax) "${entry.setsMin}" else "${entry.setsMin}$EN_DASH${entry.setsMax}"
    return "Suggested $range. Set it to whatever you will do."
}

internal fun dayHardSets(preset: PlannedPreset): Int = preset.entries.sumOf { it.sets }

/** "Upper A · 4 movements · 11 hard sets". */
internal fun dayHeadline(preset: PlannedPreset): String =
    "${preset.name} · ${movementsText(preset.entries.size)} · ${hardSetsText(dayHardSets(preset))}"

/**
 * The line a dumbbell-capped movement carries, or null when the load sits below the cap or no cap applies.
 * [capLabel] is the lifter's own per-dumbbell maximum in their unit.
 */
internal fun dumbbellNote(entry: PlannedEntry, equipment: Equipment?, capLabel: String): String? {
    val load = entry.targetWeightKg ?: return null
    val cap = equipment?.let { ProgramGenerator.dumbbellCapKg(entry.exerciseName, it) } ?: return null
    if (load < cap - 1e-9) return null
    return "$capLabel is the heaviest dumbbell in your armoury. " +
        "When ${entry.repHigh} reps get easy, add reps or a set instead."
}

/**
 * The starting-sets question's caption: where a new movement begins, by what the level means. Compound
 * lifts and single-joint work have their own windows ([ProgramRules.setRange]); the lifter can move every
 * number afterwards, so this is a starting point and says so.
 */
internal fun startingSetsCaption(volume: VolumeLevel): String {
    fun window(r: IntRange) = if (r.first == r.last) "${r.first}" else "${r.first}$EN_DASH${r.last}"
    val compound = window(ProgramRules.setRange(volume, compound = true))
    val single = window(ProgramRules.setRange(volume, compound = false))
    val who = when (volume) {
        VolumeLevel.LOW -> "Enough for a first year."
        VolumeLevel.STANDARD -> "After a year or so of training."
        VolumeLevel.HIGH -> "For years of training."
    }
    return "Big lifts start at $compound hard sets, single-joint work at $single. $who"
}
