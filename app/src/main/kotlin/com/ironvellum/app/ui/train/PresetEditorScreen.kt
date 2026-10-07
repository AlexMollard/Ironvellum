package com.ironvellum.app.ui.train

import com.ironvellum.app.ui.components.PushedHeader
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import com.ironvellum.app.ui.components.IronvellumDialog
import androidx.compose.ui.text.style.TextOverflow
import com.ironvellum.app.ui.components.IronvellumButton
import androidx.compose.ui.graphics.Color
import com.ironvellum.app.ui.components.ExercisePickerSheet
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.ironvellum.app.ui.components.InkOutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ironvellum.app.data.Repository
import com.ironvellum.app.domain.Exercise
import com.ironvellum.app.domain.ExerciseMetric
import com.ironvellum.app.domain.DecimalInput
import com.ironvellum.app.ui.components.SectionHeader
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.components.decimalKeyboard
import com.ironvellum.app.ui.components.wholeKeyboard
import com.ironvellum.app.ui.ironvellumRepository
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.ironvellumFieldColors
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

    /** The workout as loaded; back with anything different asks before discarding. */
    private val _baseline = MutableStateFlow(EditorUi(presetId = presetId))
    val baseline: StateFlow<EditorUi> = _baseline.asStateFlow()

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
            _baseline.value = loaded
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

    fun addEntry(exercise: Exercise) {
        _ui.value = _ui.value.copy(entries = _ui.value.entries + newEntry(exercise))
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
        if (!current.canSave()) return
        viewModelScope.launch {
            repo.savePreset(
                presetId = current.presetId,
                name = current.name.trim(),
                note = current.note.trim(),
                scheduledDay = current.scheduledDay,
                entries = current.entries.map {
                    Repository.PresetDraftEntry(
                        exerciseId = it.exerciseId,
                        // canSave() proved every required target is typed. Distance
                        // work has no reps field, so its stored reps stay the legacy 10.
                        targetSets = DecimalInput.parseWhole(it.sets)!!.coerceIn(1, 30),
                        targetReps = DecimalInput.parseWhole(it.reps)?.coerceIn(1, 500) ?: HIDDEN_REPS,
                        targetWeightKg = DecimalInput.parse(it.weight),
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

/**
 * A freshly picked exercise's row. Sets × reps defaults only suit REPS work:
 * other metrics keep the target fields empty, as a metric switch does.
 */
internal fun newEntry(exercise: Exercise): EditorEntry =
    if (exercise.metric == ExerciseMetric.REPS) {
        EditorEntry(exercise.id, exercise.name, sets = "3", reps = "10", weight = "", modifiers = "")
    } else {
        EditorEntry(exercise.id, exercise.name, sets = "3", reps = "", weight = "", modifiers = "")
    }

/** The reps stored for distance work, whose editor has no reps field. */
private const val HIDDEN_REPS = 10

/** Which target fields of [entry] must be typed (or fixed) before the rite can be saved. */
internal enum class TargetField { SETS, REPS, WEIGHT }

/**
 * Required targets that are blank, zero or out of range for the movement's
 * [metric]. Nothing is defaulted on save, so a duration or grade exercise can
 * no longer be stored as a 10-minute target nobody typed; a kg target above the
 * session's own ceiling ([MAX_LOAD_KG]) is refused rather than saved unreachable.
 */
internal fun missingTargets(entry: EditorEntry, metric: ExerciseMetric): Set<TargetField> = buildSet {
    if ((DecimalInput.parseWhole(entry.sets) ?: 0) < 1) add(TargetField.SETS)
    if (metric != ExerciseMetric.DISTANCE_TIME && (DecimalInput.parseWhole(entry.reps) ?: 0) < 1) add(TargetField.REPS)
    val kg = DecimalInput.parse(entry.weight)
    if (entry.weight.isNotBlank() && (kg == null || kg > MAX_LOAD_KG)) add(TargetField.WEIGHT)
}

private fun EditorUi.metricOf(entry: EditorEntry): ExerciseMetric =
    exercises.firstOrNull { it.id == entry.exerciseId }?.metric ?: ExerciseMetric.REPS

/** A named rite with at least one exercise, every target of which is valid. */
internal fun EditorUi.canSave(): Boolean =
    name.isNotBlank() && entries.isNotEmpty() && entries.all { missingTargets(it, metricOf(it)).isEmpty() }

/** Whether [current] differs from the workout as loaded in anything the lifter edits. */
internal fun editorChanged(baseline: EditorUi, current: EditorUi): Boolean =
    baseline.name != current.name ||
        baseline.note != current.note ||
        baseline.scheduledDay != current.scheduledDay ||
        baseline.entries != current.entries

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
            factory = viewModelFactory { initializer { PresetEditorViewModel(ironvellumRepository(), presetId) } },
        ),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val baseline by viewModel.baseline.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }

    // Back used to drop every edit without a word; with anything changed it
    // asks first.
    BackHandler(enabled = editorChanged(baseline, ui)) { confirmDiscard = true }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        PushedHeader(
            if (ui.presetId == null) "NEW RITE" else "REFORGE THIS RITE",
            onBack = { if (editorChanged(baseline, ui)) confirmDiscard = true else onDone() },
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            shape = MaterialTheme.shapes.small,
            colors = ironvellumFieldColors(),
            value = ui.name,
            onValueChange = viewModel::setName,
            label = { Text("Rite name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            shape = MaterialTheme.shapes.small,
            colors = ironvellumFieldColors(),
            value = ui.note,
            onValueChange = viewModel::setNote,
            label = { Text("Note") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Text("Cycle day", style = MaterialTheme.typography.labelMedium, color = IronvellumColors.SystemGreen)
        Spacer(Modifier.height(4.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            DAY_OPTIONS.chunked(4).forEach { chunk ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    chunk.forEach { (day, label) ->
                        val selected = ui.scheduledDay == day
                        InkOutlinedButton(
                            // Material's button shape is a stadium; the theme's is drawn.
                            shape = MaterialTheme.shapes.small,
                            onClick = { viewModel.setScheduledDay(day) },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                // The unscheduled option is drawn as "—", which a
                                // screen reader announces as a dash. Say what it means.
                                .semantics { contentDescription = label.takeIf { day != null } ?: "No day in the cycle" },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) IronvellumColors.SovereignGold else MaterialTheme.colorScheme.onSurfaceVariant,
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
        // Opens the picker: appending the catalogue's first row put an
        // Assault Bike in the workout, a machine most lifters do not have.
        IronvellumButton(
            label = "+ Add exercise",
            onClick = { adding = true },
            enabled = ui.exercises.isNotEmpty(),
            quiet = true,
        )

        Spacer(Modifier.height(12.dp))
        if (ui.name.isNotBlank() && ui.entries.isNotEmpty() && !ui.canSave()) {
            Text(
                "Fill in the marked targets to save. Kg tops out at ${MAX_LOAD_KG.toInt()}.",
                style = MaterialTheme.typography.labelSmall,
                color = IronvellumColors.InkMuted,
            )
            Spacer(Modifier.height(8.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IronvellumButton(
                label = "Save Rite",
                onClick = { viewModel.save(onDone) },
                enabled = ui.canSave(),
                modifier = Modifier.weight(1f),
            )
            if (ui.presetId != null) {
                // Delete used to fire on the first tap; a new user prodding the
                // button lost the whole training day. Arm first, name the cost.
                var armedDelete by remember { mutableStateOf(false) }
                IronvellumButton(
                    label = if (armedDelete) "Confirm delete" else "Delete",
                    onClick = {
                        if (armedDelete) {
                            viewModel.delete(onDone)
                        } else {
                            armedDelete = true
                        }
                    },
                    quiet = true,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (adding) {
        ExercisePickerSheet(
            exercises = ui.exercises,
            onPick = { exercise ->
                viewModel.addEntry(exercise)
                adding = false
            },
            onDismiss = { adding = false },
        )
    }

    if (confirmDiscard) {
        IronvellumDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard changes?") },
            text = { Text("Unsaved edits to this rite will be lost.") },
            confirmButton = {
                IronvellumButton(
                    label = "Discard",
                    onClick = {
                        confirmDiscard = false
                        onDone()
                    },
                    danger = true,
                )
            },
            dismissButton = {
                IronvellumButton(label = "Keep editing", onClick = { confirmDiscard = false }, quiet = true)
            },
        )
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

    InkPanel(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The name yields, the controls do not: a long name ("Dumbbell
            // Shoulder Press") pushed the remove button off the card and
            // squashed the arrows.
            Box(Modifier.weight(1f)) {
                InkOutlinedButton(
                    onClick = { expanded = true },
                    shape = MaterialTheme.shapes.small,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text(
                        entry.exerciseName.ifBlank { "Pick exercise" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (expanded) {
                    ExercisePickerSheet(
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
                }
            }
            Spacer(Modifier.width(4.dp))
            RowControl("▲", "Move up", enabled = !isFirst) { onMove(-1) }
            RowControl("▼", "Move down", enabled = !isLast) { onMove(1) }
            Box(
                Modifier
                    .size(48.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .clickable(onClickLabel = "Remove exercise", onClick = onRemove)
                    .semantics {
                        contentDescription = "Remove exercise"
                        role = Role.Button
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Close, contentDescription = null)
            }
        }

        Spacer(Modifier.height(6.dp))
        // Targets follow the movement's metric — sets × reps is meaningless for a 5 km run
        // or a football match. Non-REPS targets reuse the same fields:
        // DURATION/ATTEMPTS_GRADE store whole numbers in `reps`; DISTANCE_TIME stores
        // kilometres in `weight` so save() keeps mapping to targetWeightKg untouched.
        val metric = exercises.firstOrNull { it.id == entry.exerciseId }?.metric ?: ExerciseMetric.REPS
        val bad = missingTargets(entry, metric)
        when (metric) {
            ExerciseMetric.REPS -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("Sets", entry.sets, Modifier.weight(1f), maxDigits = SETS_DIGITS, isError = TargetField.SETS in bad) { onEntry(entry.copy(sets = it)) }
                    NumberField("Reps", entry.reps, Modifier.weight(1f), maxDigits = REPS_DIGITS, isError = TargetField.REPS in bad) { onEntry(entry.copy(reps = it)) }
                    NumberField("kg", entry.weight, Modifier.weight(1f), decimal = true, isError = TargetField.WEIGHT in bad) { onEntry(entry.copy(weight = it)) }
                }
            }
            ExerciseMetric.HOLD -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("Sets", entry.sets, Modifier.weight(1f), maxDigits = SETS_DIGITS, isError = TargetField.SETS in bad) { onEntry(entry.copy(sets = it)) }
                    NumberField("Seconds", entry.reps, Modifier.weight(1f), maxDigits = REPS_DIGITS, isError = TargetField.REPS in bad) { onEntry(entry.copy(reps = it)) }
                    NumberField("kg", entry.weight, Modifier.weight(1f), decimal = true, isError = TargetField.WEIGHT in bad) { onEntry(entry.copy(weight = it)) }
                }
            }
            ExerciseMetric.DURATION -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("Sets", entry.sets, Modifier.weight(1f), maxDigits = SETS_DIGITS, isError = TargetField.SETS in bad) { onEntry(entry.copy(sets = it)) }
                    NumberField("Min (target)", entry.reps, Modifier.weight(1f), maxDigits = REPS_DIGITS, isError = TargetField.REPS in bad) { onEntry(entry.copy(reps = it)) }
                }
            }
            ExerciseMetric.DISTANCE_TIME -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("Sets", entry.sets, Modifier.weight(1f), maxDigits = SETS_DIGITS, isError = TargetField.SETS in bad) { onEntry(entry.copy(sets = it)) }
                    NumberField("Km (target)", entry.weight, Modifier.weight(1f), decimal = true, isError = TargetField.WEIGHT in bad) { onEntry(entry.copy(weight = it)) }
                }
            }
            ExerciseMetric.ATTEMPTS_GRADE -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("Sets", entry.sets, Modifier.weight(1f), maxDigits = SETS_DIGITS, isError = TargetField.SETS in bad) { onEntry(entry.copy(sets = it)) }
                    NumberField("Attempts (target)", entry.reps, Modifier.weight(1f), maxDigits = REPS_DIGITS, isError = TargetField.REPS in bad) { onEntry(entry.copy(reps = it)) }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            shape = MaterialTheme.shapes.small,
            colors = ironvellumFieldColors(),
            value = entry.modifiers,
            onValueChange = { onEntry(entry.copy(modifiers = it.take(60))) },
            // The examples used to ride in the label, so a six-movement preset
            // printed "(weighted, deficit, elevated…)" six times. They belong
            // in the field that is still empty.
            label = { Text("Modifiers") },
            placeholder = { Text("weighted, deficit, elevated…") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** One fixed 48dp reorder control; the glyph alone read to TalkBack as a triangle. */
@Composable
private fun RowControl(glyph: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(MaterialTheme.shapes.extraSmall)
            .clickable(enabled = enabled, onClickLabel = description, onClick = onClick)
            .semantics {
                contentDescription = description
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            glyph,
            color = if (enabled) IronvellumColors.SystemGreen else IronvellumColors.Rune,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

/** Sets cap at 30 and reps at 500 when saved, so two and three digits. */
private const val SETS_DIGITS = 2
private const val REPS_DIGITS = 3

/** A target figure: kg and km take a decimal, counts (sets, reps, seconds, minutes) do not. */
@Composable
private fun NumberField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    decimal: Boolean = false,
    maxDigits: Int = 3,
    isError: Boolean = false,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        shape = MaterialTheme.shapes.small,
        colors = ironvellumFieldColors(),
        value = value,
        isError = isError,
        onValueChange = { input ->
            onValueChange(
                if (decimal) DecimalInput.sanitize(input, maxDecimals = 2, maxLength = 7)
                else DecimalInput.sanitizeWhole(input, maxDigits),
            )
        },
        // One line: a wrapped label made its field taller than its neighbours.
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        singleLine = true,
        keyboardOptions = if (decimal) decimalKeyboard(ImeAction.Next) else wholeKeyboard(ImeAction.Next),
        modifier = modifier,
    )
}
