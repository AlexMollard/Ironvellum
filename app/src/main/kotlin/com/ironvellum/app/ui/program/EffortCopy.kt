package com.ironvellum.app.ui.program

import com.ironvellum.app.R
import com.ironvellum.app.data.Words
import com.ironvellum.app.domain.EffortCue
import com.ironvellum.app.domain.Equipment
import com.ironvellum.app.domain.PlannedEntry
import com.ironvellum.app.domain.PlannedPreset
import com.ironvellum.app.domain.ProgramGenerator
import com.ironvellum.app.domain.ProgramRules
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.domain.VolumeLevel

/*
 * Effort-first words. A set is a HARD set - taken near failure - and that, not the rep count, is the dose.
 * Reps are only a guide to the load. Every screen that prescribes work says it through these, so the Forge,
 * first-run setup and the day editor cannot drift into three dialects. Pure, so each line has a JVM test.
 */

private const val EN_DASH = "–"

/** "1 hard set", "3 hard sets". */
internal fun hardSetsText(n: Int): String = Words.text(UiText.plural(R.plurals.effort_hard_sets, n))

/** "4 movements". */
internal fun movementsText(n: Int): String = Words.text(UiText.plural(R.plurals.effort_movements, n))

/** How close to failure the sets are taken, as a phrase that follows the set count. */
internal fun effortCue(effort: EffortCue): String = Words.text(
    UiText.res(
        when (effort) {
            EffortCue.RIR_0_2 -> R.string.effort_cue_rir
            EffortCue.TO_FAILURE -> R.string.effort_cue_failure
        },
    ),
)

/** The same cue as a sentence of its own, with the reminder that reps only steer the load. */
internal fun effortSentence(effort: EffortCue): String = Words.text(
    UiText.res(
        when (effort) {
            EffortCue.RIR_0_2 -> R.string.effort_sentence_rir
            EffortCue.TO_FAILURE -> R.string.effort_sentence_failure
        },
    ),
)

/** "~6–10 reps", or "~8 reps" when the window is one number. */
internal fun repGuide(entry: PlannedEntry): String = Words.text(
    if (entry.repLow == entry.repHigh) UiText.plural(R.plurals.effort_rep_guide_single, entry.repLow)
    else UiText.res(R.string.effort_rep_guide_range, entry.repLow, entry.repHigh),
)

/** The movement row's first line under its name: "3 hard sets · stop 0–2 reps short of failure". */
internal fun movementLine(entry: PlannedEntry): String = "${hardSetsText(entry.sets)} · ${effortCue(entry.effort)}"

/** The movement row's right-hand figure: "~6–10 reps · 60 kg", or just the reps for bodyweight work. */
internal fun movementDetail(entry: PlannedEntry, load: String?): String =
    if (load == null) repGuide(entry) else "${repGuide(entry)} · $load"

/** What the generator suggested, said under the set stepper so the lifter knows what they are changing. */
internal fun suggestedSets(entry: PlannedEntry): String {
    val range = if (entry.setsMin == entry.setsMax) "${entry.setsMin}" else "${entry.setsMin}$EN_DASH${entry.setsMax}"
    return Words.text(UiText.res(R.string.effort_suggested_sets, range))
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
    return Words.text(UiText.themed(R.string.effort_dumbbell_note, R.string.effort_dumbbell_note__t, capLabel, entry.repHigh))
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
    val caption = when (volume) {
        VolumeLevel.LOW -> R.string.effort_starting_sets_low
        VolumeLevel.STANDARD -> R.string.effort_starting_sets_standard
        VolumeLevel.HIGH -> R.string.effort_starting_sets_high
    }
    return Words.text(UiText.res(caption, compound, single))
}
