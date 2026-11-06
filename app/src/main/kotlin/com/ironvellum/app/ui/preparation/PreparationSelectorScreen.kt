package com.ironvellum.app.ui.preparation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.saveable.rememberSaveable
import com.ironvellum.app.ui.theme.IronvellumColors
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ironvellum.app.R
import com.ironvellum.app.domain.*
import com.ironvellum.app.ui.components.*
import com.ironvellum.app.ui.ironvellumRepository

@Composable
fun PreparationSelectorScreen(
    kind: PreparationKind, presetId: Long?, sessionId: Long?, onSelected: (PreparationChoice) -> Unit, onBack: () -> Unit,
    workingExerciseIds: List<Long>? = null, initialChoice: PreparationChoice? = null,
    onCreate: (Int?) -> Unit = {}, onManage: () -> Unit = {},
    viewModel: PreparationSelectorViewModel = viewModel(key = "preparation_selector_$kind", factory = viewModelFactory {
        initializer { PreparationSelectorViewModel(ironvellumRepository(), kind, presetId, sessionId, createSavedStateHandle(), workingExerciseIds, initialChoice) }
    }),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var preview by rememberSaveable { mutableStateOf(false) }
    PreparationFrame(
        preparationText(R.string.preparation_choose, R.string.preparation_choose__t, kindLabel(kind)), onBack,
        preparationText(R.string.preparation_use, R.string.preparation_use__t), { onSelected(ui.choice) }, enabled = ui.loaded,
    ) {
        if (ui.failed) PreparationCaption(preparationText(R.string.preparation_error, R.string.preparation_error__t), error = true)
        InkPanel(contentPadding = PaddingValues(12.dp)) {
            PreparationOption(
            preparationText(R.string.preparation_auto, R.string.preparation_auto__t),
            preparationText(R.string.preparation_auto_hint, R.string.preparation_auto_hint__t),
            ui.choice.mode == PreparationMode.AUTO,
            ) { preview = false; viewModel.select(PreparationChoice()) }
        if (ui.choice.mode == PreparationMode.AUTO) ui.suggestion?.let { suggestion ->
            Text(suggestion.name, style = MaterialTheme.typography.titleMedium, color = IronvellumColors.Ink)
            PreparationCaption(preparationCount(R.plurals.preparation_exercise_count, R.plurals.preparation_exercise_count__t, suggestion.steps.size))
            TextAction(preparationText(R.string.preparation_preview, R.string.preparation_preview__t), { preview = !preview })
            if (preview) PreparationPreview(suggestion, ui.exercises)
            val templates = PreparationStarters.templates(kind, ui.exercises)
            TextAction(preparationText(R.string.preparation_edit_copy, R.string.preparation_edit_copy__t), { onCreate(templates.indexOfFirst { it.steps == suggestion.steps }) })
        }
        }
        SectionHeader(preparationText(R.string.preparation_personal, R.string.preparation_personal__t))
        if (ui.loaded && ui.routines.isEmpty()) PreparationCaption(preparationText(R.string.preparation_empty, R.string.preparation_empty__t))
        ui.routines.forEach { routine ->
            PreparationOption(routine.name, preparationCount(R.plurals.preparation_exercise_count, R.plurals.preparation_exercise_count__t, routine.steps.size), ui.choice.mode == PreparationMode.SAVED && ui.choice.routineId == routine.id) {
                preview = false; viewModel.select(PreparationChoice(PreparationMode.SAVED, routine.id))
            }
            if (ui.choice.mode == PreparationMode.SAVED && ui.choice.routineId == routine.id) {
                TextAction(preparationText(R.string.preparation_preview, R.string.preparation_preview__t), { preview = !preview })
                if (preview) PreparationPreview(PreparationSnapshot(kind, routine.name, routine.steps, routine.id), ui.exercises)
            }
        }
        ListRow(preparationText(R.string.preparation_create, R.string.preparation_create__t), onClick = { onCreate(null) })
        ListRow(preparationText(R.string.preparation_manage, R.string.preparation_manage__t), onClick = onManage)
        InkDivider()
        PreparationOption(
            preparationText(R.string.preparation_none, R.string.preparation_none__t),
            preparationText(R.string.preparation_none_hint, R.string.preparation_none_hint__t), ui.choice.mode == PreparationMode.NONE,
        ) { preview = false; viewModel.select(PreparationChoice(PreparationMode.NONE)) }
    }
}

@Composable private fun PreparationOption(name: String, hint: String, isSelected: Boolean, onClick: () -> Unit) {
    Column(Modifier.semantics { selected = isSelected }) {
        ListRow(name, subline = hint, value = if (isSelected) preparationText(R.string.preparation_selected, R.string.preparation_selected__t) else null, onClick = onClick)
        InkDivider()
    }
}
