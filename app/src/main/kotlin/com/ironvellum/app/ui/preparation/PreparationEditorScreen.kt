package com.ironvellum.app.ui.preparation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.RadioButton
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ironvellum.app.R
import com.ironvellum.app.domain.*
import com.ironvellum.app.ui.components.*
import com.ironvellum.app.ui.ironvellumRepository
import com.ironvellum.app.ui.program.ExerciseInspection
import com.ironvellum.app.ui.train.UnderlineField
import com.ironvellum.app.ui.theme.IronvellumColors

private fun editorKey(kind: PreparationKind, routineId: Long?) = "preparation_editor_" + kind.name + '_' + routineId

@Composable
fun PreparationEditorScreen(
    routineId: Long?, kind: PreparationKind, starterIndex: Int? = null, onDone: () -> Unit,
    copySnapshot: PreparationSnapshot? = null,
    viewModel: PreparationEditorViewModel = viewModel(key = editorKey(kind, routineId), factory = viewModelFactory {
        initializer { PreparationEditorViewModel(ironvellumRepository(), routineId, kind, starterIndex, createSavedStateHandle(), copySnapshot) }
    }),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val exercises by viewModel.exercises.collectAsStateWithLifecycle()
    val usedBy by viewModel.usedBy.collectAsStateWithLifecycle()
    var adding by rememberSaveable { mutableStateOf(false) }
    var inspected by rememberSaveable { mutableStateOf<String?>(null) }
    var discard by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    var open by rememberSaveable { mutableIntStateOf(0) }
    val leave = { if (ui.dirty) discard = true else onDone() }
    PreparationFrame(
        title = preparationText(R.string.preparation_edit_kind, R.string.preparation_edit_kind__t, kindLabel(kind)), onBack = leave,
        primary = preparationText(R.string.preparation_save, R.string.preparation_save__t),
        onPrimary = { viewModel.save(onDone) },
        enabled = ui.loaded && !ui.busy && PreparationRoutine(kind = kind, name = ui.name, steps = ui.steps).validForEditor(),
    ) {
        if (ui.failed) PreparationCaption(preparationText(R.string.preparation_error, R.string.preparation_error__t), error = true)
        if (ui.loaded) {
            UnderlineField(preparationText(R.string.preparation_name, R.string.preparation_name__t), ui.name, viewModel::setName, singleLine = false)
            PreparationCaption(preparationText(R.string.preparation_future_only, R.string.preparation_future_only__t))
            if (usedBy.isNotEmpty()) PreparationCaption(preparationText(R.string.preparation_used_by, R.string.preparation_used_by__t, usedBy.joinToString(", ")))
            Spacer(Modifier.height(8.dp))
            ui.steps.forEachIndexed { index, step ->
                if (open != index) {
                    ListRow(step.exerciseName, subline = preparationTarget(step), onClick = { open = index })
                    InkDivider()
                } else {
                    var repeatOpen by rememberSaveable(open) { mutableStateOf(false) }
                    var cueOpen by rememberSaveable(open) { mutableStateOf(false) }
                    var reorderOpen by rememberSaveable(open) { mutableStateOf(false) }
                    EntryCard(
                        name = step.exerciseName, foldLabel = stringResource(R.string.preseteditor_fold_description, step.exerciseName), onFold = { open = -1 }, titleMaxLines = Int.MAX_VALUE,
                        headerActions = {
                            PreparationMenu { close ->
                                DropdownMenuItem(text = { Text(stringResource(R.string.preseteditor_info_button)) }, onClick = { close(); inspected = step.exerciseName })
                                PreparationMeasure.entries.forEach { measure ->
                                    DropdownMenuItem(text = { Text(preparationText(if (measure == PreparationMeasure.SECONDS) R.string.preparation_seconds else R.string.preparation_reps, if (measure == PreparationMeasure.SECONDS) R.string.preparation_seconds__t else R.string.preparation_reps__t)) }, onClick = {
                                        close()
                                        if (measure != step.measure) viewModel.update(index, step.copy(measure = measure, target = if (measure == PreparationMeasure.SECONDS) 30 else 10))
                                    })
                                }
                                DropdownMenuItem(text = { Text(stringResource(R.string.preseteditor_remove_exercise)) }, onClick = { close(); viewModel.remove(index); open = index.coerceAtMost(ui.steps.size - 2) })
                            }
                        },
                        showFooter = false, actions = {},
                    ) {
                        if (step.legacyTarget != null) PreparationCaption(preparationTarget(step))
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val stacked = LocalDensity.current.fontScale > 1.3f || maxWidth < 300.dp
                            val counts: @Composable () -> Unit = {
                                PreparationEditorCount(step, rounds = false) { viewModel.update(index, step.copy(target = it.toIntOrNull() ?: 0)) }
                                PreparationEditorCount(step, rounds = true) { viewModel.update(index, step.copy(rounds = it.toIntOrNull() ?: 0)) }
                            }
                            if (stacked) Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, content = { counts() })
                            else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround, content = { counts() })
                        }
                        val repeatControl: @Composable () -> Unit = { InkChip(preparationRepeatLabel(step.repeat), onClick = { repeatOpen = !repeatOpen }) }
                        if (LocalDensity.current.fontScale > 1.3f) Column {
                            Text(preparationText(R.string.preparation_repeat, R.string.preparation_repeat__t), color = IronvellumColors.Ink, style = MaterialTheme.typography.bodyMedium)
                            repeatControl()
                        } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(preparationText(R.string.preparation_repeat, R.string.preparation_repeat__t), color = IronvellumColors.Ink, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            repeatControl()
                        }
                        if (repeatOpen) Column(Modifier.selectableGroup()) {
                            PreparationRepeat.entries.forEach { repeat ->
                                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected = step.repeat == repeat, role = Role.RadioButton, onClick = { viewModel.update(index, step.copy(repeat = repeat)); repeatOpen = false }), verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = step.repeat == repeat, onClick = null)
                                    Text(preparationRepeatLabel(repeat), style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.Ink)
                                }
                            }
                        }
                        InkDivider()
                        if (cueOpen || step.note.isNotBlank()) UnderlineField(stringResource(R.string.preseteditor_note_label), step.note, { viewModel.update(index, step.copy(note = it)) }, singleLine = false)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            if (!cueOpen && step.note.isBlank()) TextAction(preparationText(R.string.preparation_add_cue, R.string.preparation_add_cue__t), { cueOpen = true })
                            else Spacer(Modifier.weight(1f))
                            TextAction(preparationText(R.string.preparation_reorder, R.string.preparation_reorder__t), { reorderOpen = !reorderOpen })
                        }
                        if (reorderOpen) Column {
                            if (index > 0) TextAction(stringResource(R.string.preseteditor_move_up), { viewModel.move(index, -1); open = index - 1 }, preparationText(R.string.preparation_move_up, R.string.preparation_move_up__t, step.exerciseName))
                            if (index < ui.steps.lastIndex) TextAction(stringResource(R.string.preseteditor_move_down), { viewModel.move(index, 1); open = index + 1 }, preparationText(R.string.preparation_move_down, R.string.preparation_move_down__t, step.exerciseName))
                        }
                        if (!step.validForEditor()) PreparationCaption(preparationText(R.string.preparation_validation, R.string.preparation_validation__t), error = true)
                    }
                }
            }
            ListRow(stringResource(R.string.preseteditor_add_exercise), icon = Icons.Outlined.Add, onClick = { adding = true })
            InkDivider()
            if (ui.steps.isEmpty()) PreparationCaption(preparationText(R.string.preparation_add_hint, R.string.preparation_add_hint__t))
            if (routineId != null) {
                Spacer(Modifier.height(16.dp))
                IronvellumButton(preparationText(R.string.preparation_delete, R.string.preparation_delete__t), { deleting = true }, danger = true, quiet = true)
            }
        }
    }
    if (adding) ExercisePickerSheet(exercises, onPick = { viewModel.add(it); open = ui.steps.size; adding = false }, onDismiss = { adding = false }, defaultMyGear = false)
    inspected?.let { name -> ExerciseInspection(exercises, name, "") { inspected = null } }
    if (discard) PreparationConfirm(
        preparationText(R.string.preparation_discard_title, R.string.preparation_discard_title__t),
        preparationText(R.string.preparation_discard_body, R.string.preparation_discard_body__t),
        preparationText(R.string.preparation_discard, R.string.preparation_discard__t), onDone, { discard = false }, danger = true,
    )
    if (deleting) PreparationConfirm(
        preparationText(R.string.preparation_delete_title, R.string.preparation_delete_title__t),
        preparationText(R.string.preparation_delete_body, R.string.preparation_delete_body__t) +
            if (usedBy.isEmpty()) "" else "\n\n" + preparationText(R.string.preparation_used_by, R.string.preparation_used_by__t, usedBy.joinToString(", ")),
        preparationText(R.string.preparation_delete, R.string.preparation_delete__t),
        { deleting = false; viewModel.delete(onDone) }, { deleting = false }, danger = true,
    )
}

@Composable private fun preparationRepeatLabel(repeat: PreparationRepeat): String = when (repeat) {
    PreparationRepeat.ONCE -> preparationText(R.string.preparation_once, R.string.preparation_once__t)
    PreparationRepeat.SIDES -> preparationText(R.string.preparation_sides, R.string.preparation_sides__t)
    PreparationRepeat.DIRECTIONS -> preparationText(R.string.preparation_directions, R.string.preparation_directions__t)
}

/** Uses the same typed pad group and whole-number stepping as WholeStepper. */
@Composable private fun PreparationEditorCount(step: PreparationStep, rounds: Boolean, onValue: (String) -> Unit) {
    val label = if (rounds) preparationText(R.string.preparation_rounds, R.string.preparation_rounds__t)
        else preparationText(if (step.measure == PreparationMeasure.SECONDS) R.string.preparation_seconds else R.string.preparation_reps, if (step.measure == PreparationMeasure.SECONDS) R.string.preparation_seconds__t else R.string.preparation_reps__t)
    val value = if (rounds) step.rounds else step.target
    val max = if (rounds) 10 else if (step.measure == PreparationMeasure.REPS) 100 else 600
    val valid = value in if (rounds) 1..10 else if (step.measure == PreparationMeasure.REPS) 1..100 else 5..600
    val text = value.takeIf { it > 0 }?.toString().orEmpty()
    Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted)
        StepperGroup(label, StepperPart(text,
            stringResource(R.string.stepper_fewer_for, label, step.exerciseName), stringResource(R.string.stepper_more_for, label, step.exerciseName),
            { onValue(stepWhole(text, -1, max)) }, { onValue(stepWhole(text, 1, max)) }, onValue, if (rounds) 2 else 3), error = !valid)
    }
}
