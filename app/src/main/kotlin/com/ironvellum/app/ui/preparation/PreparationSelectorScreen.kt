package com.ironvellum.app.ui.preparation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
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
import com.ironvellum.app.ui.theme.IronvellumColors

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
    val selected = when (ui.choice.mode) {
        PreparationMode.AUTO -> ui.suggestion
        PreparationMode.SAVED -> ui.routines.firstOrNull { it.id == ui.choice.routineId }?.let { PreparationSnapshot(kind, it.name, it.steps, it.id) }
        PreparationMode.NONE -> null
    }
    PreparationFrame(
        preparationText(R.string.preparation_choose, R.string.preparation_choose__t, kindLabel(kind)), onBack,
        primary = preparationText(R.string.preparation_use_kind, R.string.preparation_use_kind__t, kindLabel(kind)), onPrimary = { onSelected(ui.choice) }, enabled = ui.loaded,
    ) {
        if (ui.failed) PreparationCaption(preparationText(R.string.preparation_error, R.string.preparation_error__t), error = true)
        Column(Modifier.selectableGroup()) {
            PreparationOption(preparationText(R.string.preparation_auto, R.string.preparation_auto__t),
                preparationText(R.string.preparation_auto_hint, R.string.preparation_auto_hint__t), ui.choice.mode == PreparationMode.AUTO,
            ) { viewModel.select(PreparationChoice()) }
            ui.routines.forEach { routine ->
                PreparationOption(routine.name, preparationMetadata(PreparationSnapshot(kind, routine.name, routine.steps, routine.id)),
                    ui.choice.mode == PreparationMode.SAVED && ui.choice.routineId == routine.id,
                ) { viewModel.select(PreparationChoice(PreparationMode.SAVED, routine.id)) }
            }
            PreparationOption(preparationText(R.string.preparation_none, R.string.preparation_none__t),
                if (kind == PreparationKind.WARMUP) preparationText(R.string.preparation_none_hint, R.string.preparation_none_hint__t)
                else preparationText(R.string.preparation_none_cooldown_hint, R.string.preparation_none_cooldown_hint__t), ui.choice.mode == PreparationMode.NONE,
            ) { viewModel.select(PreparationChoice(PreparationMode.NONE)) }
        }
        if (ui.loaded && ui.routines.isEmpty()) PreparationCaption(preparationText(R.string.preparation_empty, R.string.preparation_empty__t))
        selected?.let { snapshot ->
            SectionHeader(preparationText(R.string.preparation_preview, R.string.preparation_preview__t))
            PreparationPreview(snapshot, ui.exercises)
            if (ui.choice.mode == PreparationMode.AUTO) {
                TextAction(preparationText(R.string.preparation_edit_copy, R.string.preparation_edit_copy__t), {
                    val index = PreparationStarters.templates(kind, ui.exercises).indexOfFirst { it.steps == snapshot.steps }
                    onCreate(index.takeIf { it >= 0 })
                })
            }
        }
        ListRow(preparationText(R.string.preparation_create, R.string.preparation_create__t), onClick = { onCreate(null) })
        ListRow(preparationText(R.string.preparation_manage, R.string.preparation_manage__t), onClick = onManage)
    }
}

@Composable private fun PreparationOption(name: String, hint: String, isSelected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = isSelected, onClick = null)
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, color = IronvellumColors.Ink)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted)
        }
    }
    InkDivider()
}
