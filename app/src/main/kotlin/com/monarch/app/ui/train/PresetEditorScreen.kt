package com.monarch.app.ui.train

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.ui.graphics.Color
import com.monarch.app.ui.components.ExercisePickerPanel
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.monarch.app.data.Repository
import com.monarch.app.domain.Exercise
import com.monarch.app.domain.ExerciseMetric
import com.monarch.app.ui.components.SectionHeader
import com.monarch.app.ui.components.SystemWindow
import com.monarch.app.ui.monarchRepository
import com.monarch.app.ui.theme.MonarchColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class EditorEntry(
    val exerciseId: Long,
    val exerciseName: String,
    val sets: String,
    val reps: String,
    val weight: String,
    val modifiers: String,
)

data class EditorUi(
    val presetId: Long? = null,
    val name: String = "",
    val note: String = "",
    val scheduledDay: Int? = null,
    val entries: List<EditorEntry> = emptyList(),
    val exercises: List<Exercise> = emptyList(),
)

class PresetEditorViewModel(
    private val repo: Repository,
    private val presetId: Long?,
) : ViewModel() {

    private val _ui = MutableStateFlow(EditorUi(presetId = presetId))
    val ui: StateFlow<EditorUi> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val exercises = repo.observeExercises().first()
            val loaded = if (presetId != null) {
                val existing = repo.observePresets().first().firstOrNull { it.id == presetId }
                if (existing != null) {
                    EditorUi(
                        presetId = presetId,
                        name = existing.name,
                        note = existing.note,
                        scheduledDay = existing.scheduledDay,
                        entries = existing.entries.map { entry ->
                            EditorEntry(
                                exerciseId = entry.exerciseId,
                                exerciseName = entry.exerciseName,
                                sets = entry.targetSets.toString(),
                                reps = entry.targetReps.toString(),
                                weight = entry.targetWeightKg?.let(::formatWeight) ?: "",
                                modifiers = entry.modifiers,
                            )
                        },
                        exercises = exercises,
                    )
                } else {
                    EditorUi(presetId = presetId, exercises = exercises)
                }
            } else {
                EditorUi(exercises = exercises)
            }
            _ui.value = loaded
        }
    }

    fun setName(value: String) {
        _ui.value = _ui.value.copy(name = value)
    }

    fun setNote(value: String) {
        _ui.value = _ui.value.copy(note = value)
    }

    fun setScheduledDay(value: Int?) {
        _ui.value = _ui.value.copy(scheduledDay = value)
    }

    fun addEntry() {
        val first = _ui.value.exercises.firstOrNull() ?: return
        _ui.value = _ui.value.copy(
            entries = _ui.value.entries + EditorEntry(first.id, first.name, sets = "3", reps = "10", weight = "", modifiers = ""),
        )
    }

    fun removeEntry(index: Int) {
        _ui.value = _ui.value.copy(entries = _ui.value.entries.filterIndexed { i, _ -> i != index })
    }

    fun moveEntry(index: Int, delta: Int) {
        val target = index + delta
        val list = _ui.value.entries.toMutableList()
        // Both ends are checked: the index arrives from a rendered row, so a
        // tap queued before a removal recomposes carries an index the list no
        // longer has — which crashed on list[index] rather than doing nothing.
        if (index !in list.indices || target !in list.indices) return
        val moved = list[index]
        list[index] = list[target]
        list[target] = moved
        _ui.value = _ui.value.copy(entries = list)
    }

    fun updateEntry(index: Int, newEntry: EditorEntry) {
        _ui.value = _ui.value.copy(
            entries = _ui.value.entries.mapIndexed { i, entry -> if (i == index) newEntry else entry },
        )
    }

    fun save(onDone: () -> Unit) {
        val current = _ui.value
        if (current.name.isBlank() || current.entries.isEmpty()) return
        viewModelScope.launch {
            repo.savePreset(
                presetId = current.presetId,
                name = current.name.trim(),
                note = current.note.trim(),
                scheduledDay = current.scheduledDay,
                entries = current.entries.map {
                    Repository.PresetDraftEntry(
                        exerciseId = it.exerciseId,
                        targetSets = it.sets.toIntOrNull()?.coerceIn(1, 30) ?: 3,
                        targetReps = it.reps.toIntOrNull()?.coerceIn(1, 500) ?: 10,
                        targetWeightKg = it.weight.toDoubleOrNull(),
                        modifiers = it.modifiers.trim(),
                    )
                },
            )
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        val id = presetId ?: return
        viewModelScope.launch {
            repo.deletePreset(id)
            onDone()
        }
    }
}

private fun formatWeight(kg: Double): String =
    if (kg == kg.toLong().toDouble()) kg.toLong().toString() else kg.toString()

private val DAY_OPTIONS = listOf(
    1 to "Mon", 2 to "Tue", 3 to "Wed", 4 to "Thu",
    5 to "Fri", 6 to "Sat", 7 to "Sun", null to "—",
)

@Composable
fun PresetEditorScreen(
    presetId: Long?,
    onDone: () -> Unit,
    viewModel: PresetEditorViewModel =
        viewModel(
            key = "preset_editor_$presetId",
            factory = viewModelFactory { initializer { PresetEditorViewModel(monarchRepository(), presetId) } },
        ),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        Text(
            if (ui.presetId == null) "FORGE NEW PRESET" else "REFORGE PRESET",
            style = MaterialTheme.typography.labelLarge,
            color = MonarchColors.SystemGreen,
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            shape = MaterialTheme.shapes.small,
            value = ui.name,
            onValueChange = viewModel::setName,
            label = { Text("Preset name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            shape = MaterialTheme.shapes.small,
            value = ui.note,
            onValueChange = viewModel::setNote,
            label = { Text("Note / mantra") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Text("Routine day", style = MaterialTheme.typography.labelMedium, color = MonarchColors.SystemGreen)
        Spacer(Modifier.height(4.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            DAY_OPTIONS.chunked(4).forEach { chunk ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    chunk.forEach { (day, label) ->
                        val selected = ui.scheduledDay == day
                        OutlinedButton(
                            // Material's button shape is a stadium; the theme's is drawn.
                            shape = MaterialTheme.shapes.small,
                            onClick = { viewModel.setScheduledDay(day) },
                            modifier = Modifier
                                .weight(1f)
                                // The unscheduled option is drawn as "—", which a
                                // screen reader announces as a dash. Say what it means.
                                .semantics { contentDescription = label.takeIf { day != null } ?: "No scheduled day" },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) MonarchColors.SovereignGold else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        SectionHeader("Exercises")
        ui.entries.forEachIndexed { index, entry ->
            EntryRow(
                entry = entry,
                exercises = ui.exercises,
                isFirst = index == 0,
                isLast = index == ui.entries.lastIndex,
                onEntry = { viewModel.updateEntry(index, it) },
                onRemove = { viewModel.removeEntry(index) },
                onMove = { viewModel.moveEntry(index, it) },
            )
        }
        TextButton(onClick = viewModel::addEntry, enabled = ui.exercises.isNotEmpty()) {
            Text("+ Add exercise")
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                // Material's button shape is a stadium; the theme's is drawn.
                shape = MaterialTheme.shapes.small,
                onClick = { viewModel.save(onDone) },
                enabled = ui.name.isNotBlank() && ui.entries.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) {
                Text("Save Preset")
            }
            if (ui.presetId != null) {
                OutlinedButton(onClick = { viewModel.delete(onDone) }, shape = MaterialTheme.shapes.small) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun EntryRow(
    entry: EditorEntry,
    exercises: List<Exercise>,
    isFirst: Boolean,
    isLast: Boolean,
    onEntry: (EditorEntry) -> Unit,
    onRemove: () -> Unit,
    onMove: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    SystemWindow(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                OutlinedButton(onClick = { expanded = true }, shape = MaterialTheme.shapes.small) {
                    Text(entry.exerciseName.ifBlank { "Pick exercise" })
                }
                if (expanded) {
                    AlertDialog(
                        // Material's dialog container is a 28dp rounded rect - the most
                        // obviously stock surface in the app. Give it the ink shape.
                        shape = MaterialTheme.shapes.medium,
                        onDismissRequest = { expanded = false },
                        containerColor = Color(0xFF0D1110),
                        title = {},
                        text = {
                            ExercisePickerPanel(
                                exercises = exercises,
                                onPick = { exercise ->
                                    // A metric switch invalidates the old targets (10 reps ≠ 40 min).
                                    val defaults = if (exercise.metric == ExerciseMetric.REPS) {
                                        entry
                                    } else {
                                        entry.copy(reps = "", weight = "")
                                    }
                                    onEntry(defaults.copy(exerciseId = exercise.id, exerciseName = exercise.name))
                                    expanded = false
                                },
                                onDismiss = { expanded = false },
                            )
                        },
                        confirmButton = {},
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "▲",
                        color = if (isFirst) MonarchColors.Rune else MonarchColors.SystemGreen,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.extraSmall)
                            .clickable(enabled = !isFirst) { onMove(-1) }
                            .padding(horizontal = 10.dp, vertical = 1.dp),
                    )
                    Text(
                        "▼",
                        color = if (isLast) MonarchColors.Rune else MonarchColors.SystemGreen,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.extraSmall)
                            .clickable(enabled = !isLast) { onMove(1) }
                            .padding(horizontal = 10.dp, vertical = 1.dp),
                    )
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Outlined.Close, contentDescription = "Remove exercise")
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        // Targets follow the movement's metric — sets × reps is meaningless for a 5 km run
        // or a football match. Non-REPS targets reuse the same fields:
        // DURATION/ATTEMPTS_GRADE store whole numbers in `reps`; DISTANCE_TIME stores
        // kilometres in `weight` so save() keeps mapping to targetWeightKg untouched.
        val metric = exercises.firstOrNull { it.id == entry.exerciseId }?.metric ?: ExerciseMetric.REPS
        when (metric) {
            ExerciseMetric.REPS -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("Sets", entry.sets, Modifier.weight(1f)) { onEntry(entry.copy(sets = it)) }
                    NumberField("Reps", entry.reps, Modifier.weight(1f)) { onEntry(entry.copy(reps = it)) }
                    NumberField("Kg (opt.)", entry.weight, Modifier.weight(1f)) { onEntry(entry.copy(weight = it)) }
                }
            }
            ExerciseMetric.DURATION -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("Sets", entry.sets, Modifier.weight(1f)) { onEntry(entry.copy(sets = it)) }
                    NumberField("Min (target)", entry.reps, Modifier.weight(1f)) { onEntry(entry.copy(reps = it)) }
                }
            }
            ExerciseMetric.DISTANCE_TIME -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("Sets", entry.sets, Modifier.weight(1f)) { onEntry(entry.copy(sets = it)) }
                    NumberField("Km (target)", entry.weight, Modifier.weight(1f)) { onEntry(entry.copy(weight = it)) }
                }
            }
            ExerciseMetric.ATTEMPTS_GRADE -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("Sets", entry.sets, Modifier.weight(1f)) { onEntry(entry.copy(sets = it)) }
                    NumberField("Attempts (target)", entry.reps, Modifier.weight(1f)) { onEntry(entry.copy(reps = it)) }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            shape = MaterialTheme.shapes.small,
            value = entry.modifiers,
            onValueChange = { onEntry(entry.copy(modifiers = it.take(60))) },
            // The examples used to ride in the label, so a six-movement preset
            // printed "(weighted, deficit, elevated…)" six times. They belong
            // in the field that is still empty.
            label = { Text("Modifiers") },
            placeholder = { Text("weighted, deficit, elevated…") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        shape = MaterialTheme.shapes.small,
        value = value,
        onValueChange = { input -> onValueChange(input.filter { it.isDigit() || it == '.' }.take(7)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}
