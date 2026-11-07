package com.ironvellum.app.ui.preparation

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import com.ironvellum.app.ui.theme.IronvellumColors
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ironvellum.app.R
import com.ironvellum.app.domain.*
import com.ironvellum.app.ui.components.*
import com.ironvellum.app.ui.ironvellumRepository

@Composable
fun PreparationLibraryScreen(
    initialKind: PreparationKind, onEdit: (Long?, PreparationKind) -> Unit, onBack: () -> Unit,
    onCopyStarter: (Int, PreparationKind) -> Unit = { _, kind -> onEdit(null, kind) },
) {
    var kindName by rememberSaveable { mutableStateOf(initialKind.name) }
    val kind = PreparationKind.valueOf(kindName)
    val vm: PreparationLibraryViewModel = viewModel(key = "preparation_library_$kind", factory = viewModelFactory { initializer { PreparationLibraryViewModel(ironvellumRepository(), kind) } })
    PreparationLibraryContent(kind, vm, onEdit, onCopyStarter, { kindName = it.name }, onBack)
}

@Composable internal fun PreparationLibraryContent(
    kind: PreparationKind, vm: PreparationLibraryViewModel, onEdit: (Long?, PreparationKind) -> Unit,
    onCopyStarter: (Int, PreparationKind) -> Unit, onKind: (PreparationKind) -> Unit, onBack: () -> Unit,
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var preview by rememberSaveable(kind) { mutableStateOf<Long?>(null) }
    PreparationFrame(
        preparationText(R.string.preparation_library, R.string.preparation_library__t), onBack,
        preparationText(R.string.preparation_create, R.string.preparation_create__t), { onEdit(null, kind) }, enabled = ui.loaded,
    ) {
        InkTabs(
            listOf(preparationText(R.string.preparation_warmups, R.string.preparation_warmups__t), preparationText(R.string.preparation_cooldowns, R.string.preparation_cooldowns__t)),
            PreparationKind.entries.indexOf(kind), { onKind(PreparationKind.entries[it]) },
        )
        if (ui.failed) PreparationCaption(preparationText(R.string.preparation_error, R.string.preparation_error__t), error = true)
        SectionHeader(preparationText(R.string.preparation_personal, R.string.preparation_personal__t))
        if (ui.loaded && ui.routines.isEmpty()) PreparationCaption(preparationText(R.string.preparation_empty, R.string.preparation_empty__t))
        ui.routines.forEach { routine ->
            val used = ui.presets.filter { it.preparation[kind]?.routineId == routine.id }.map { it.name }
            val snapshot = PreparationSnapshot(kind, routine.name, routine.steps, routine.id)
            InkPanel(contentPadding = PaddingValues(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(routine.name, style = MaterialTheme.typography.titleMedium, color = IronvellumColors.Ink, modifier = Modifier.weight(1f))
                    PreparationMenu { close ->
                        DropdownMenuItem(text = { Text(preparationText(R.string.preparation_edit, R.string.preparation_edit__t)) }, onClick = { close(); onEdit(routine.id, kind) })
                    }
                }
                PreparationCaption(preparationMetadata(snapshot))
                if (used.isNotEmpty()) PreparationCaption(preparationText(R.string.preparation_used_by, R.string.preparation_used_by__t, used.joinToString(", ")))
                InkDivider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextAction(preparationText(R.string.preparation_preview, R.string.preparation_preview__t), { preview = if (preview == routine.id) null else routine.id })
                    TextAction(preparationText(R.string.preparation_edit, R.string.preparation_edit__t), { onEdit(routine.id, kind) })
                }
                if (preview == routine.id) PreparationPreview(snapshot, ui.exercises)
            }
            Spacer(Modifier.height(12.dp))
        }
        SectionHeader(preparationText(R.string.preparation_starters, R.string.preparation_starters__t))
        PreparationCaption(preparationText(R.string.preparation_copy_hint, R.string.preparation_copy_hint__t))
        if (ui.loaded) PreparationStarters.templates(kind, ui.exercises).forEachIndexed { index, template ->
            ListRow(template.name, subline = preparationText(R.string.preparation_edit_copy, R.string.preparation_edit_copy__t) + " · " + preparationMetadata(template), onClick = { onCopyStarter(index, kind) })
            InkDivider()
        }
    }
}
