package com.ironvellum.app.ui.preparation

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironvellum.app.data.Repository
import com.ironvellum.app.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

internal fun PreparationStep.draftBundle() = Bundle().apply {
    putLong("id", exerciseId); putString("name", exerciseName); putString("measure", measure.name)
    putInt("target", target); putInt("rounds", rounds); putString("repeat", repeat.name); putString("note", note)
    weightKg?.let { putDouble("weight", it) }
}
internal fun Bundle.preparationStep() = PreparationStep(
    getLong("id"), getString("name").orEmpty(), PreparationMeasure.valueOf(getString("measure")!!),
    getInt("target"), getInt("rounds"), PreparationRepeat.valueOf(getString("repeat")!!), getString("note").orEmpty(),
    if (containsKey("weight")) getDouble("weight") else null,
)

data class PreparationEditorUi(
    val name: String = "", val steps: List<PreparationStep> = emptyList(), val loaded: Boolean = false,
    val dirty: Boolean = false, val busy: Boolean = false, val failed: Boolean = false,
)

class PreparationEditorViewModel(
    private val repo: Repository, private val routineId: Long?, private val kind: PreparationKind,
    private val starterIndex: Int?, private val saved: SavedStateHandle,
) : ViewModel() {
    private val _ui = MutableStateFlow(PreparationEditorUi())
    val ui = _ui.asStateFlow()
    private var baseline = PreparationEditorUi()
    private val _exercises = MutableStateFlow<List<Exercise>>(emptyList())
    val exercises = _exercises.asStateFlow()
    val usedBy = repo.observePresets().map { presets -> presets.filter { it.preparation[kind]?.routineId == routineId && routineId != null }.map { it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    init {
        viewModelScope.launch {
            try {
                _exercises.value = repo.observeExercises().first()
                val routine = routineId?.let { id -> repo.preparations.observeRoutines(kind).first().firstOrNull { it.id == id } }
                check(routineId == null || routine != null)
                val template = starterIndex?.let { PreparationStarters.templates(kind, _exercises.value).getOrNull(it) }
                val initial = PreparationEditorUi(routine?.name ?: template?.name.orEmpty(), routine?.steps ?: template?.steps.orEmpty(), loaded = true)
                baseline = saved.get<Bundle>("baseline")?.toEditor() ?: initial.also { saved["baseline"] = it.bundle() }
                val restored = saved.get<Bundle>("draft")?.toEditor() ?: initial
                edit(restored)
            } catch (e: CancellationException) { throw e } catch (_: Exception) { _ui.value = _ui.value.copy(failed = true) }
        }
    }
    private fun PreparationEditorUi.bundle() = Bundle().apply {
        putString("name", name); putParcelableArrayList("steps", ArrayList(steps.map { it.draftBundle() }))
    }
    @Suppress("DEPRECATION")
    private fun Bundle.toEditor() = PreparationEditorUi(getString("name").orEmpty(), getParcelableArrayList<Bundle>("steps").orEmpty().map { it.preparationStep() }, loaded = true)
    private fun edit(next: PreparationEditorUi) {
        val changed = next.copy(dirty = next.name != baseline.name || next.steps != baseline.steps, failed = false)
        saved["draft"] = changed.bundle()
        _ui.value = changed
    }
    fun setName(value: String) = edit(_ui.value.copy(name = value))
    fun update(index: Int, step: PreparationStep) = edit(_ui.value.copy(steps = _ui.value.steps.mapIndexed { i, old -> if (i == index) step else old }))
    fun add(exercise: Exercise) = edit(_ui.value.copy(steps = _ui.value.steps + PreparationStep(exercise.id, exercise.name, if (exercise.metric == ExerciseMetric.REPS) PreparationMeasure.REPS else PreparationMeasure.SECONDS, if (exercise.metric == ExerciseMetric.REPS) 10 else 30)))
    fun remove(index: Int) = edit(_ui.value.copy(steps = _ui.value.steps.filterIndexed { i, _ -> i != index }))
    fun move(index: Int, delta: Int) {
        val list = _ui.value.steps.toMutableList()
        val to = index + delta
        if (index !in list.indices || to !in list.indices) return
        list.add(to, list.removeAt(index))
        edit(_ui.value.copy(steps = list))
    }
    fun save(onDone: () -> Unit) {
        val state = _ui.value
        val routine = PreparationRoutine(routineId ?: 0, kind, state.name.trim(), state.steps)
        if (!state.loaded || state.busy || !routine.validForEditor()) return
        _ui.value = state.copy(busy = true, failed = false)
        viewModelScope.launch {
            try { repo.preparations.saveRoutine(routine); onDone() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { _ui.value = _ui.value.copy(failed = true) }
            finally { _ui.value = _ui.value.copy(busy = false) }
        }
    }
    fun delete(onDone: () -> Unit) {
        val id = routineId ?: return
        if (_ui.value.busy) return
        _ui.value = _ui.value.copy(busy = true, failed = false)
        viewModelScope.launch {
            try { repo.preparations.deleteRoutine(id); onDone() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { _ui.value = _ui.value.copy(failed = true) }
            finally { _ui.value = _ui.value.copy(busy = false) }
        }
    }
}

data class PreparationLibraryUi(val exercises: List<Exercise> = emptyList(), val routines: List<PreparationRoutine> = emptyList(), val presets: List<WorkoutPreset> = emptyList(), val loaded: Boolean = false, val failed: Boolean = false)
class PreparationLibraryViewModel(repo: Repository, kind: PreparationKind) : ViewModel() {
    val ui = combine(repo.observeExercises(), repo.preparations.observeRoutines(kind), repo.observePresets()) { exercises, routines, presets ->
        PreparationLibraryUi(exercises, routines, presets, loaded = true)
    }.catch { emit(PreparationLibraryUi(failed = true)) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PreparationLibraryUi())
}

data class PreparationSelectorUi(
    val loaded: Boolean = false, val suggestion: PreparationSnapshot? = null,
    val routines: List<PreparationRoutine> = emptyList(), val exercises: List<Exercise> = emptyList(),
    val choice: PreparationChoice = PreparationChoice(), val failed: Boolean = false,
)
class PreparationSelectorViewModel(
    repo: Repository, kind: PreparationKind, presetId: Long?, sessionId: Long?, private val saved: SavedStateHandle,
    workingExerciseIds: List<Long>? = null, initialChoice: PreparationChoice? = null,
) : ViewModel() {
    private val _ui = MutableStateFlow(PreparationSelectorUi())
    val ui = _ui.asStateFlow()
    init {
        viewModelScope.launch {
            try {
                val catalogue = repo.observeExercises().first()
                val preset = presetId?.let { id -> repo.observePresets().first().firstOrNull { it.id == id } }
                val workIds = workingExerciseIds ?: sessionId?.let { id -> repo.observeSessionSets(id).first().filter { it.kind != SetKind.WARMUP }.map { it.exerciseId } }
                    ?: preset?.entries?.filter { !it.warmup }?.map { it.exerciseId }.orEmpty()
                val initial = initialChoice ?: preset?.preparation?.get(kind) ?: PreparationChoice()
                repo.preparations.observeRoutines(kind).collect { routines ->
                    val selected = saved.get<String>("mode")?.let { PreparationChoice(PreparationMode.valueOf(it), saved.get<Long>("routine")) } ?: initial
                    val resolved = if (selected.mode == PreparationMode.SAVED && routines.none { it.id == selected.routineId }) PreparationChoice() else selected
                    _ui.value = PreparationSelectorUi(true, PreparationStarters.suggest(kind, catalogue.filter { it.id in workIds }, catalogue), routines, catalogue, resolved)
                }
            } catch (e: CancellationException) { throw e } catch (_: Exception) { _ui.value = _ui.value.copy(failed = true) }
        }
    }
    fun select(choice: PreparationChoice) {
        saved["mode"] = choice.mode.name; saved["routine"] = choice.routineId
        _ui.value = _ui.value.copy(choice = choice)
    }
}
