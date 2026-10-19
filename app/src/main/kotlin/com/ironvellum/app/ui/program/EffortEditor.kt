package com.ironvellum.app.ui.program

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ironvellum.app.R
import com.ironvellum.app.data.Units
import com.ironvellum.app.data.Seed
import com.ironvellum.app.domain.Equipment
import com.ironvellum.app.domain.Exercise
import com.ironvellum.app.domain.ExerciseMetric
import com.ironvellum.app.domain.LoadKit
import com.ironvellum.app.domain.LoadUnit
import com.ironvellum.app.domain.PlannedEntry
import com.ironvellum.app.domain.ProgramGenerator
import com.ironvellum.app.domain.WornLoad
import com.ironvellum.app.ui.components.EntryCard
import com.ironvellum.app.ui.components.StepperRow
import com.ironvellum.app.ui.theme.IronvellumColors

/*
 * The open-movement editor the Forge's day page and first-run's cycle review share: how hard the sets are, how
 * many, a rep guide, the load, and the quiet actions. One composable, so the two cannot drift. Every control is
 * a 48dp target that names its movement.
 */

/** One load step: 2.5 kg, or 5 lb for a lifter who counts in pounds. */
internal fun loadStepKg(unit: LoadUnit): Double = if (unit == LoadUnit.KG) 2.5 else 5.0 * 0.45359237

/**
 * The kit a plan entry's load is stepped in: the lifter's vest and belt on a loaded bodyweight lift, else null.
 * The movement's catalogue flag comes from [catalogue] (custom movements too), else the seeded catalogue, so
 * a plain Pull-up is recognised here exactly as the live trial recognises it.
 */
internal fun wornFor(entry: PlannedEntry, equipment: Equipment?, catalogue: List<Exercise> = emptyList()): LoadKit? {
    val known = catalogue.firstOrNull { it.name.equals(entry.exerciseName, ignoreCase = true) }
    return WornLoad.forLift(
        equipment?.kit, entry.exerciseName,
        known?.isWeighted ?: Seed.isWeighted(entry.exerciseName),
        known?.metric ?: ExerciseMetric.REPS,
    )
}

/** A label, then - value +, in the shared stepper look; the pads say whose count they change. */
@Composable
internal fun EffortStepper(
    label: String,
    value: String,
    unit: String?,
    less: String,
    more: String,
    onLess: () -> Unit,
    onMore: () -> Unit,
    lessEnabled: Boolean = true,
    moreEnabled: Boolean = true,
) = StepperRow(
    label = label,
    value = value,
    unit = unit,
    less = less,
    more = more,
    error = false,
    decimal = false,
    maxDigits = 0,
    onValue = null,
    onLess = onLess,
    onMore = onMore,
    lessEnabled = lessEnabled,
    moreEnabled = moreEnabled,
)

/** A quiet 48dp text action. [description] says what it does when its label alone does not. */
@Composable
internal fun QuietAction(label: String, description: String = label, danger: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { if (description != label) contentDescription = description }
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (danger) IronvellumColors.DangerRed else IronvellumColors.SystemGreen,
        )
    }
}

/**
 * One open movement. [onLoad] null hides the load stepper (first-run review keeps to the sets); [onSwap]
 * null hides Swap. Stepping the load never passes the lifter's own heaviest dumbbell, and a movement sitting
 * at that cap says what to do instead.
 */
@Composable
internal fun EffortEntryEditor(
    entry: PlannedEntry,
    dayName: String,
    equipment: Equipment?,
    onFold: () -> Unit,
    onSets: (delta: Int) -> Unit,
    onRemove: () -> Unit,
    onLoad: ((delta: Int) -> Unit)? = null,
    onSwap: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    val name = entry.exerciseName
    val cap = equipment?.let { ProgramGenerator.dumbbellCapKg(name, it) }
    val capLabel = equipment?.dumbbellMaxKg?.let { Units.withUnit(it) } ?: cap?.let { Units.withUnit(it) }.orEmpty()
    EntryCard(
        name = name,
        foldLabel = stringResource(R.string.effort_editor_close_description, name),
        onFold = onFold,
        actions = {
            if (onSwap != null) QuietAction(
                stringResource(R.string.effort_editor_swap_button),
                stringResource(R.string.effort_editor_swap_description, name),
                onClick = { if (enabled) onSwap() },
            )
            else Spacer(Modifier)
            QuietAction(
                stringResource(R.string.effort_editor_remove_button),
                stringResource(R.string.effort_editor_remove_description, name, dayName),
                danger = true,
                onClick = { if (enabled) onRemove() },
            )
        },
    ) {
        Text(
            effortSentence(entry.effort),
            style = MaterialTheme.typography.bodySmall,
            color = IronvellumColors.InkMuted,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        EffortStepper(
            label = stringResource(R.string.effort_editor_sets_label),
            value = entry.sets.toString(),
            unit = null,
            less = stringResource(R.string.effort_editor_sets_less_description, name),
            more = stringResource(R.string.effort_editor_sets_more_description, name),
            onLess = { if (enabled) onSets(-1) },
            onMore = { if (enabled) onSets(1) },
            lessEnabled = enabled && entry.sets > SET_SANITY.first,
            moreEnabled = enabled && entry.sets < SET_SANITY.last,
        )
        Text(
            suggestedSets(entry),
            style = MaterialTheme.typography.labelSmall,
            color = IronvellumColors.InkMuted,
        )
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(R.string.effort_editor_rep_guide_label), style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted)
            Text(repGuide(entry), style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.Ink)
        }
        val load = entry.targetWeightKg
        if (onLoad != null && load != null) {
            EffortStepper(
                label = stringResource(R.string.effort_editor_load_label),
                value = Units.format(load),
                unit = Units.name,
                less = stringResource(R.string.effort_editor_load_less_description, name),
                more = stringResource(R.string.effort_editor_load_more_description, name),
                onLess = { if (enabled) onLoad(-1) },
                onMore = { if (enabled) onLoad(1) },
                moreEnabled = enabled && (cap == null || load < cap - 1e-9) &&
                    wornFor(entry, equipment)?.let { it.nextKg(load, Units.current) != null } != false,
            )
        }
        dumbbellNote(entry, equipment, capLabel)?.let { note ->
            Text(
                note,
                style = MaterialTheme.typography.labelSmall,
                color = IronvellumColors.InkMuted,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
    }
}
