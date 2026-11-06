package com.ironvellum.app.ui.preparation

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
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
    var preview by rememberSaveable(kind) { mutableStateOf<Int?>(null) }
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
            val subline = if (used.isEmpty()) preparationCount(R.plurals.preparation_exercise_count, R.plurals.preparation_exercise_count__t, routine.steps.size)
                else preparationText(R.string.preparation_used_by, R.string.preparation_used_by__t, used.joinToString(", "))
            ListRow(routine.name, subline = subline, onClick = { onEdit(routine.id, kind) })
            InkDivider()
        }
        SectionHeader(preparationText(R.string.preparation_starters, R.string.preparation_starters__t))
        PreparationCaption(preparationText(R.string.preparation_copy_hint, R.string.preparation_copy_hint__t))
        if (ui.loaded) PreparationStarters.templates(kind, ui.exercises).forEachIndexed { index, template ->
            ListRow(template.name, subline = preparationCount(R.plurals.preparation_exercise_count, R.plurals.preparation_exercise_count__t, template.steps.size), onClick = { preview = if (preview == index) null else index })
            InkDivider()
            if (preview == index) {
                PreparationPreview(template, ui.exercises)
                TextAction(preparationText(R.string.preparation_edit_copy, R.string.preparation_edit_copy__t), { onCopyStarter(index, kind) })
            }
        }
    }
}
