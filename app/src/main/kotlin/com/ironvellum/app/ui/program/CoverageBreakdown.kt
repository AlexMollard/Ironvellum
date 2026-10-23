package com.ironvellum.app.ui.program

import androidx.compose.runtime.Composable
import com.ironvellum.app.R
import com.ironvellum.app.data.Words
import com.ironvellum.app.domain.Muscle
import com.ironvellum.app.domain.PlannedPreset
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.domain.Variation
import com.ironvellum.app.ui.components.InfoAction
import com.ironvellum.app.ui.components.InfoChip
import com.ironvellum.app.ui.theme.IronvellumColors

/**
 * Coverage's breakdown of one muscle: the sheet Fatigue uses ([MuscleBreakdownSheet]), with this week's numbers in
 * it. The chip is the week state, the first section the done and planned sets, and each source row an exercise that
 * trains the muscle, done ones solid and planned ones lighter, with the sets it credits. A heavy muscle adds why,
 * and a way to dismiss the flag. [onOpenExercise] null (a plan not saved yet has no catalogue to open) leaves the
 * rows plain.
 */
@Composable
internal fun CoverageBreakdownSheet(
    muscle: Muscle,
    week: WeekMuscle,
    state: WeekState?,
    done: List<PlannedPreset>,
    planned: List<PlannedPreset>,
    onOpenExercise: ((name: String, modifiers: String) -> Unit)?,
    onDismissHeavy: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    fun t(id: Int, vararg args: Any): String = Words.text(UiText.res(id, *args))
    val sources = buildList {
        for ((presets, color) in listOf(done to IronvellumColors.Emerald, planned to IronvellumColors.Emerald.copy(alpha = PLANNED_ALPHA))) {
            for (rite in ritesTraining(presets, muscle)) {
                for (credit in rite.exercises) {
                    add(
                        BreakdownSource(
                            label = Variation.label(credit.exerciseName, credit.modifiers),
                            detail = rite.rite,
                            figure = riteSetsLabel(credit.credited),
                            weight = credit.credited.toFloat().coerceAtLeast(0.1f),
                            color = color,
                            onOpen = onOpenExercise?.let { open -> { open(credit.exerciseName, credit.modifiers) } },
                        ),
                    )
                }
            }
        }
    }
    val heavy = state == WeekState.HEAVY
    MuscleBreakdownSheet(
        title = muscle.label,
        subtitle = t(R.string.coverage_breakdown_subtitle),
        chips = listOfNotNull(state?.let { InfoChip(it.label, IronvellumColors.Ink) }),
        actions = if (heavy && onDismissHeavy != null) {
            listOf(InfoAction(t(R.string.coverage_heavy_dismiss_button, muscle.label), onDismissHeavy, quiet = true))
        } else {
            emptyList()
        },
        sourcesTitle = t(R.string.coverage_breakdown_sources_title),
        sources = sources,
        other = null,
        none = t(R.string.coverage_filter_empty),
        onDismiss = onDismiss,
        lead = {
            text(
                t(R.string.coverage_breakdown_sets_title),
                when (state) {
                    null -> hardSetsWeekLabel(week.done)
                    // Done says it in the numbers; the other states add what the week still needs, or what is too much.
                    WeekState.DONE -> weekSetsLabel(week)
                    else -> weekSetsLabel(week) + "\n" + weekRowNote(week, state)
                },
            )
        },
        extra = {
            if (heavy) {
                val (reason, paper) = heavyReason(week.load)
                text(t(R.string.coverage_heavy_note_title, muscle.label, hardSetsWeekLabel(week.load.weekly)), reason)
                text(null, paper.label, IronvellumColors.InkMuted)
            }
        },
    )
}

/** A planned set's colour is the done colour at this strength, the same as the planned part of a row's bar. */
private const val PLANNED_ALPHA = 0.45f
