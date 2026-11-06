package com.ironvellum.app.ui.preparation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.MaterialTheme
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
        title = kindLabel(kind), onBack = leave,
        primary = preparationText(R.string.preparation_save, R.string.preparation_save__t),
        onPrimary = { viewModel.save(onDone) },
        enabled = ui.loaded && !ui.busy && PreparationRoutine(kind = kind, name = ui.name, steps = ui.steps).validForEditor(),
    ) {
        if (ui.failed) PreparationCaption(preparationText(R.string.preparation_error, R.string.preparation_error__t), error = true)
        if (ui.loaded) {
            UnderlineField(preparationText(R.string.preparation_name, R.string.preparation_name__t), ui.name, viewModel::setName, singleLine = false)
            PreparationCaption(preparationText(R.string.preparation_future_only, R.string.preparation_future_only__t))
            if (usedBy.isNotEmpty()) PreparationCaption(preparationText(R.string.preparation_used_by, R.string.preparation_used_by__t, usedBy.joinToString(", ")))
            SectionHeader(stringResource(R.string.preseteditor_exercises_header))
            ui.steps.forEachIndexed { index, step ->
                if (open != index) {
                    ListRow(step.exerciseName, subline = preparationTarget(step), onClick = { open = index })
                    InkDivider()
                } else {
                    EntryCard(
                        name = step.exerciseName, foldLabel = stringResource(R.string.preseteditor_fold_description, step.exerciseName), onFold = { open = -1 }, titleMaxLines = Int.MAX_VALUE,
                        actions = {
                            TextAction(stringResource(R.string.preseteditor_info_button), { inspected = step.exerciseName })
                            TextAction(stringResource(R.string.preseteditor_remove_exercise), { viewModel.remove(index); open = index.coerceAtMost(ui.steps.size - 2) })
                        },
                    ) {
                        exercises.firstOrNull { it.id == step.exerciseId }?.let { exercise ->
                            ExerciseArtSlot(exercise.name, exercise.muscleGroup, Modifier.fillMaxWidth().height(120.dp))
                        }
                        InkSegmented(
                            options = listOf(PreparationMeasure.SECONDS to preparationText(R.string.preparation_seconds, R.string.preparation_seconds__t), PreparationMeasure.REPS to preparationText(R.string.preparation_reps, R.string.preparation_reps__t)), selected = step.measure,
                            onPick = { measure -> viewModel.update(index, step.copy(measure = measure, target = if (measure == PreparationMeasure.SECONDS) 30 else 10)) },
                        )
                        WholeStepper(
                            label = preparationText(R.string.preparation_target, R.string.preparation_target__t), value = step.target.takeIf { it > 0 }?.toString().orEmpty(),
                            noun = stringResource(if (step.measure == PreparationMeasure.SECONDS) R.string.preparation_seconds else R.string.preparation_reps),
                            error = step.target !in if (step.measure == PreparationMeasure.REPS) 1..100 else 5..600,
                            maxDigits = 3, max = if (step.measure == PreparationMeasure.REPS) 100 else 600, subject = step.exerciseName,
                        ) { viewModel.update(index, step.copy(target = it.toIntOrNull() ?: 0)) }
                        WholeStepper(
                            label = preparationText(R.string.preparation_rounds, R.string.preparation_rounds__t), value = step.rounds.takeIf { it > 0 }?.toString().orEmpty(),
                            noun = preparationText(R.string.preparation_rounds, R.string.preparation_rounds__t), error = step.rounds !in 1..10,
                            maxDigits = 2, max = 10, subject = step.exerciseName,
                        ) { viewModel.update(index, step.copy(rounds = it.toIntOrNull() ?: 0)) }
                        // Stacked rows wrap at large font sizes and keep every repeat option reachable.
                        PreparationRepeat.entries.forEach { repeat ->
                            val label = when (repeat) {
                                PreparationRepeat.ONCE -> preparationText(R.string.preparation_once, R.string.preparation_once__t)
                                PreparationRepeat.SIDES -> preparationText(R.string.preparation_sides, R.string.preparation_sides__t)
                                PreparationRepeat.DIRECTIONS -> preparationText(R.string.preparation_directions, R.string.preparation_directions__t)
                            }
                            ListRow(label, value = if (step.repeat == repeat) preparationText(R.string.preparation_selected, R.string.preparation_selected__t) else null, onClick = { viewModel.update(index, step.copy(repeat = repeat)) })
                        }
                        UnderlineField(stringResource(R.string.preseteditor_note_label), step.note, { viewModel.update(index, step.copy(note = it)) }, singleLine = false)
                        if (!step.validForEditor()) PreparationCaption(preparationText(R.string.preparation_validation, R.string.preparation_validation__t), error = true)
                        Column {
                            if (index > 0) TextAction(stringResource(R.string.preseteditor_move_up), { viewModel.move(index, -1); open = index - 1 }, preparationText(R.string.preparation_move_up, R.string.preparation_move_up__t, step.exerciseName))
                            if (index < ui.steps.lastIndex) TextAction(stringResource(R.string.preseteditor_move_down), { viewModel.move(index, 1); open = index + 1 }, preparationText(R.string.preparation_move_down, R.string.preparation_move_down__t, step.exerciseName))
                        }
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
