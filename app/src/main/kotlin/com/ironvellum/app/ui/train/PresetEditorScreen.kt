package com.ironvellum.app.ui.train

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ironvellum.app.data.Repository
import com.ironvellum.app.domain.DecimalInput
import com.ironvellum.app.domain.Exercise
import com.ironvellum.app.domain.ExerciseMetric
import com.ironvellum.app.ui.components.DecimalStepper
import com.ironvellum.app.ui.components.DockedActionBar
import com.ironvellum.app.ui.components.EntryCard
import com.ironvellum.app.ui.components.ExercisePickerSheet
import com.ironvellum.app.ui.components.InkDivider
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.components.IronvellumDialog
import com.ironvellum.app.ui.components.ListRow
import com.ironvellum.app.ui.components.PushedHeader
import com.ironvellum.app.ui.components.SectionHeader
import com.ironvellum.app.ui.components.TextAction
import com.ironvellum.app.ui.components.UndoBar
import com.ironvellum.app.ui.components.WholeStepper
import com.ironvellum.app.ui.components.formatSteppedFigure
import com.ironvellum.app.ui.ironvellumRepository
import com.ironvellum.app.ui.theme.IronvellumColors
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
                                weight = entry.targetWeightKg?.let(::formatSteppedFigure) ?: "",
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

    /** Puts a removed [entry] back where it was, for Undo. */
    fun insertEntry(index: Int, entry: EditorEntry) {
        val list = _ui.value.entries.toMutableList()
        list.add(index.coerceIn(0, list.size), entry)
        _ui.value = _ui.value.copy(entries = list)
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


private val DAY_NAMES = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

/** The cycle day as the Day row shows it. */
private fun dayName(day: Int?): String = day?.let { DAY_NAMES.getOrNull(it - 1) } ?: "No day"

/** Sets cap at 30 and reps at 500 when saved, so two and three digits. */
private const val SETS_DIGITS = 2
private const val REPS_DIGITS = 3
private const val MAX_SETS = 30
private const val MAX_REPS = 500

/** How far a reorder drag travels before the row swaps with its neighbour. */
private val DRAG_STEP = 56.dp

/** The folded row's second line: the targets the metric uses, "–" where one is not typed yet. */
internal fun entrySummary(entry: EditorEntry, metric: ExerciseMetric): String {
    fun v(s: String) = s.ifBlank { "–" }
    return when (metric) {
        ExerciseMetric.REPS ->
            "${v(entry.sets)} × ${v(entry.reps)}" + if (entry.weight.isBlank()) "" else " · ${entry.weight} kg"
        ExerciseMetric.HOLD ->
            "${v(entry.sets)} × ${v(entry.reps)} s" + if (entry.weight.isBlank()) "" else " · ${entry.weight} kg"
        ExerciseMetric.DURATION -> "${v(entry.sets)} × ${v(entry.reps)} min"
        ExerciseMetric.DISTANCE_TIME -> "${v(entry.sets)} × ${v(entry.weight)} km"
        ExerciseMetric.ATTEMPTS_GRADE -> "${v(entry.sets)} × ${v(entry.reps)} attempts"
    }
}

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
    var swapping by remember { mutableStateOf<Int?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var pickingDay by remember { mutableStateOf(false) }
    // One exercise is unfolded at a time; its index follows it through reorders and removals.
    var open by remember { mutableStateOf<Int?>(null) }
    var removed by remember { mutableStateOf<Pair<Int, EditorEntry>?>(null) }
    var dragIndex by remember { mutableIntStateOf(-1) }

    // Back used to drop every edit without a word; with anything changed it
    // asks first.
    BackHandler(enabled = editorChanged(baseline, ui)) { confirmDiscard = true }

    fun move(index: Int, delta: Int) {
        val to = index + delta
        if (index !in ui.entries.indices || to !in ui.entries.indices) return
        viewModel.moveEntry(index, delta)
        open = when (open) {
            index -> to
            to -> index
            else -> open
        }
    }

    fun remove(index: Int) {
        val gone = ui.entries.getOrNull(index) ?: return
        viewModel.removeEntry(index)
        removed = index to gone
        val was = open
        open = when {
            was == null || was == index -> null
            was > index -> was - 1
            else -> was
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Spacer(Modifier.height(20.dp))
        PushedHeader(
            if (ui.presetId == null) "New rite" else "Edit rite",
            onBack = { if (editorChanged(baseline, ui)) confirmDiscard = true else onDone() },
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                UnderlineField("Rite name", ui.name, viewModel::setName)
                UnderlineField("Note", ui.note, viewModel::setNote)
                Spacer(Modifier.height(6.dp))
                ListRow(
                    label = "Day",
                    value = dayName(ui.scheduledDay),
                    icon = Icons.Outlined.CalendarMonth,
                    onClick = { pickingDay = true },
                )
                InkDivider()

                SectionHeader("Exercises", topPadding = 14.dp)
                ui.entries.forEachIndexed { index, entry ->
                    val metric = ui.exercises.firstOrNull { it.id == entry.exerciseId }?.metric ?: ExerciseMetric.REPS
                    EntryItem(
                        entry = entry,
                        metric = metric,
                        open = open == index,
                        onToggle = { open = if (open == index) null else index },
                        onEntry = { viewModel.updateEntry(index, it) },
                        onChange = { swapping = index },
                        onRemove = { remove(index) },
                        onMove = { move(index, it) },
                        onDragStart = { dragIndex = index },
                        onDragStep = { delta ->
                            if (dragIndex + delta in ui.entries.indices) {
                                move(dragIndex, delta)
                                dragIndex += delta
                            }
                        },
                    )
                }
                // Opens the picker: appending the catalogue's first row put an
                // Assault Bike in the workout, a machine most lifters do not have.
                ListRow(
                    label = "Add exercise",
                    icon = Icons.Outlined.Add,
                    onClick = if (ui.exercises.isNotEmpty()) ({ adding = true }) else null,
                )
                InkDivider()

                if (ui.name.isNotBlank() && ui.entries.isNotEmpty() && !ui.canSave()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Fill in the marked targets to save. Kg tops out at ${MAX_LOAD_KG.toInt()}.",
                        style = MaterialTheme.typography.labelSmall,
                        color = IronvellumColors.InkMuted,
                    )
                }
                if (ui.presetId != null) {
                    Spacer(Modifier.height(16.dp))
                    InkDivider()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .clickable(role = Role.Button) { confirmDelete = true }
                            .padding(horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = null, tint = IronvellumColors.DangerRed, modifier = Modifier.size(22.dp))
                        Text("Delete rite", style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.DangerRed)
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
            val pending = removed
            pending?.let { (index, gone) ->
                UndoBar(
                    message = "${gone.exerciseName.ifBlank { "Exercise" }} removed",
                    onUndo = {
                        viewModel.insertEntry(index, gone)
                        open = open?.let { if (it >= index) it + 1 else it }
                        removed = null
                    },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 8.dp),
                    onExpired = { removed = null },
                    key = pending,
                )
            }
        }
        DockedActionBar(
            primary = "Save rite",
            onPrimary = { viewModel.save(onDone) },
            primaryEnabled = ui.canSave(),
            reserveLink = false,
        )
    }

    if (adding) {
        ExercisePickerSheet(
            exercises = ui.exercises,
            onPick = { exercise ->
                viewModel.addEntry(exercise)
                open = ui.entries.size
                adding = false
            },
            onDismiss = { adding = false },
        )
    }

    swapping?.let { index ->
        val entry = ui.entries.getOrNull(index)
        if (entry == null) {
            swapping = null
        } else {
            ExercisePickerSheet(
                exercises = ui.exercises,
                onPick = { exercise ->
                    // A metric switch invalidates the old targets (10 reps ≠ 40 min).
                    val kept = if (exercise.metric == ExerciseMetric.REPS) entry else entry.copy(reps = "", weight = "")
                    viewModel.updateEntry(index, kept.copy(exerciseId = exercise.id, exerciseName = exercise.name))
                    swapping = null
                },
                onDismiss = { swapping = null },
            )
        }
    }

    if (pickingDay) {
        IronvellumDialog(
            onDismissRequest = { pickingDay = false },
            title = { Text("Day") },
            text = {
                Column {
                    (1..7).map { it to DAY_NAMES[it - 1] }.plus(null to "No day").forEach { (day, label) ->
                        val selected = ui.scheduledDay == day
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .clickable(role = Role.RadioButton) {
                                    viewModel.setScheduledDay(day)
                                    pickingDay = false
                                }
                                .semantics { this.selected = selected },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = IronvellumColors.Ink,
                                modifier = Modifier.weight(1f),
                            )
                            if (selected) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = IronvellumColors.Emerald)
                            }
                        }
                        InkDivider()
                    }
                }
            },
            confirmButton = { IronvellumButton(label = "Close", onClick = { pickingDay = false }, quiet = true) },
        )
    }

    if (confirmDiscard) {
        IronvellumDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard your changes?") },
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

    if (confirmDelete) {
        IronvellumDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete rite?") },
            text = { Text("${ui.name.ifBlank { "This rite" }} and its exercises will be removed. Trials you have already sealed stay in your history.") },
            confirmButton = {
                IronvellumButton(
                    label = "Delete rite",
                    onClick = {
                        confirmDelete = false
                        viewModel.delete(onDone)
                    },
                    danger = true,
                )
            },
            dismissButton = {
                IronvellumButton(label = "Keep rite", onClick = { confirmDelete = false }, quiet = true)
            },
        )
    }
}

/**
 * One exercise: folded it is a row (handle, name, targets), unfolded a card with a stepper per
 * target, the modifiers, and Change / Remove well away from the reorder handle. One row at a time
 * is open; [onToggle] folds or unfolds it.
 *
 * Targets follow the movement's [metric] — sets × reps is meaningless for a 5 km run or a
 * football match. Non-REPS targets reuse the same fields: DURATION/ATTEMPTS_GRADE store whole
 * numbers in `reps`; DISTANCE_TIME stores kilometres in `weight` so save() keeps mapping to
 * targetWeightKg untouched.
 */
@Composable
private fun EntryItem(
    entry: EditorEntry,
    metric: ExerciseMetric,
    open: Boolean,
    onToggle: () -> Unit,
    onEntry: (EditorEntry) -> Unit,
    onChange: () -> Unit,
    onRemove: () -> Unit,
    onMove: (Int) -> Unit,
    onDragStart: () -> Unit,
    onDragStep: (Int) -> Unit,
) {
    val name = entry.exerciseName.ifBlank { "Pick exercise" }
    val bad = missingTargets(entry, metric)
    if (!open) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(onClickLabel = "Edit $name", role = Role.Button, onClick = onToggle),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ReorderHandle(name, onDragStart, onDragStep, onMove)
            Column(Modifier.weight(1f)) {
                Text(
                    name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = IronvellumColors.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    entrySummary(entry, metric),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (bad.isEmpty()) IronvellumColors.InkMuted else IronvellumColors.DangerRed,
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = IronvellumColors.InkMuted)
        }
        InkDivider()
        return
    }
    EntryCard(
        name = name,
        foldLabel = "Fold $name",
        onFold = onToggle,
        leading = { ReorderHandle(name, onDragStart, onDragStep, onMove) },
        actions = {
            TextAction("Change exercise", onChange)
            TextAction("Remove exercise", onRemove)
        },
    ) {
        WholeStepper("Sets", entry.sets, "sets", TargetField.SETS in bad, SETS_DIGITS, MAX_SETS) { onEntry(entry.copy(sets = it)) }
        when (metric) {
            ExerciseMetric.REPS ->
                WholeStepper("Reps", entry.reps, "reps", TargetField.REPS in bad, REPS_DIGITS, MAX_REPS) { onEntry(entry.copy(reps = it)) }
            ExerciseMetric.HOLD ->
                WholeStepper("Seconds", entry.reps, "seconds", TargetField.REPS in bad, REPS_DIGITS, MAX_REPS) { onEntry(entry.copy(reps = it)) }
            ExerciseMetric.DURATION ->
                WholeStepper("Minutes", entry.reps, "minutes", TargetField.REPS in bad, REPS_DIGITS, MAX_REPS) { onEntry(entry.copy(reps = it)) }
            ExerciseMetric.ATTEMPTS_GRADE ->
                WholeStepper("Attempts", entry.reps, "attempts", TargetField.REPS in bad, REPS_DIGITS, MAX_REPS) { onEntry(entry.copy(reps = it)) }
            ExerciseMetric.DISTANCE_TIME -> Unit
        }
        when (metric) {
            ExerciseMetric.REPS, ExerciseMetric.HOLD ->
                DecimalStepper("Load", entry.weight, "kg", "Lighter load", "Heavier load", 2.5, TargetField.WEIGHT in bad) { onEntry(entry.copy(weight = it)) }
            ExerciseMetric.DISTANCE_TIME ->
                DecimalStepper("Distance", entry.weight, "km", "Shorter distance", "Longer distance", 0.5, TargetField.WEIGHT in bad) { onEntry(entry.copy(weight = it)) }
            else -> Unit
        }
        UnderlineField(
            label = "Modifiers",
            value = entry.modifiers,
            onValueChange = { onEntry(entry.copy(modifiers = it.take(60))) },
            // The examples used to ride in the label, so a six-movement preset
            // printed "(weighted, deficit, elevated…)" six times. They belong
            // in the field that is still empty.
            placeholder = "weighted, deficit, elevated…",
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
    }
}

/**
 * The reorder grip. Dragging it swaps the row with its neighbours one [DRAG_STEP] at a time;
 * screen readers get Move up / Move down actions instead, since a drag is not available to them.
 */
@Composable
private fun ReorderHandle(
    name: String,
    onDragStart: () -> Unit,
    onDragStep: (Int) -> Unit,
    onMove: (Int) -> Unit,
) {
    val start by rememberUpdatedState(onDragStart)
    val step by rememberUpdatedState(onDragStep)
    val stepPx = with(LocalDensity.current) { DRAG_STEP.toPx() }
    Box(
        Modifier
            .size(44.dp)
            .pointerInput(Unit) {
                var travelled = 0f
                detectVerticalDragGestures(
                    onDragStart = {
                        travelled = 0f
                        start()
                    },
                    onVerticalDrag = { change, dy ->
                        change.consume()
                        travelled += dy
                        while (travelled >= stepPx) {
                            step(1)
                            travelled -= stepPx
                        }
                        while (travelled <= -stepPx) {
                            step(-1)
                            travelled += stepPx
                        }
                    },
                )
            }
            .semantics {
                contentDescription = "Drag to reorder $name"
                role = Role.Button
                customActions = listOf(
                    CustomAccessibilityAction("Move up") { onMove(-1); true },
                    CustomAccessibilityAction("Move down") { onMove(1); true },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.DragHandle, contentDescription = null, tint = IronvellumColors.InkMuted)
    }
}

/** A flat field: a small label over the text and a Rune rule underneath, no box. */
@Composable
private fun UnderlineField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
) {
    Column(modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted, modifier = Modifier.padding(top = 8.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = IronvellumColors.Ink),
            cursorBrush = SolidColor(IronvellumColors.SystemGreen),
            keyboardOptions = keyboardOptions,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            decorationBox = { inner ->
                Box(Modifier.heightIn(min = 44.dp), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = IronvellumColors.InkMuted)
                    }
                    inner()
                }
            },
        )
        InkDivider()
    }
}
