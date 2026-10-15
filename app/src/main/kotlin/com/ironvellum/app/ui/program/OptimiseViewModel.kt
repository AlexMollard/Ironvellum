package com.ironvellum.app.ui.program

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironvellum.app.data.ProgramAnswersStore
import com.ironvellum.app.data.Repository
import com.ironvellum.app.domain.PlannedPreset
import com.ironvellum.app.domain.ProgramRequest
import com.ironvellum.app.domain.ProgramRules
import com.ironvellum.app.domain.TrainingFocus
import com.ironvellum.app.domain.TrainingSplit
import com.ironvellum.app.domain.VolumeLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the Optimise screen has to show before it has a report. */
internal sealed interface OptimiseState {
    data object Loading : OptimiseState

    /** Optimise ranks movements against the armoury, and no cycle is judged on a guess. */
    data object NeedsArmoury : OptimiseState

    data object NoRites : OptimiseState

    data class Ready(val report: OptimiseReport) : OptimiseState
}

/**
 * The changes a lifter applied, kept until they leave the page so Undo can put every touched rite back as it
 * was. [before] is each touched rite's id and its saved form from before the write.
 */
internal data class Applied(
    val title: String,
    val lines: List<Pair<String, String>>,
    val skipped: Int,
    val before: List<Pair<Long, PlannedPreset>>,
    val riteNames: List<String>,
)

/**
 * Reads the saved rites, builds the [OptimiseReport] off the main thread and rebuilds it whenever the rites
 * change (after an apply or an Undo, the page is already showing the new week). Writes go through one
 * all-or-nothing call, one at a time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class OptimiseViewModel(
    private val repo: Repository,
    private val appContext: Context,
) : ViewModel() {

    val state: StateFlow<OptimiseState> =
        combine(repo.observePresets(), repo.observeExercises(), repo.observeBodyProfile().map { it.second }) { saved, catalogue, sex ->
            Triple(saved, catalogue, sex)
        }.mapLatest { (saved, catalogue, sex) ->
            if (saved.isEmpty()) return@mapLatest OptimiseState.NoRites
            if (catalogue.isEmpty()) return@mapLatest OptimiseState.Loading
            val answers = ProgramAnswersStore.get(appContext)
            val equipment = answers?.equipment ?: ProgramAnswersStore.equipment.value ?: return@mapLatest OptimiseState.NeedsArmoury
            val strength = repo.strengthProfile().copy(starter = repo.starterBasis(sex, answers?.volume ?: VolumeLevel.STANDARD))
            val request = ProgramRequest(
                focus = answers?.focus ?: TrainingFocus.GENERAL,
                volume = answers?.volume ?: VolumeLevel.STANDARD,
                equipment = equipment,
                daysPerWeek = answers?.daysPerWeek ?: saved.size.coerceIn(1, 6),
                priorities = answers?.priorities.orEmpty(),
                sex = sex,
                split = answers?.split ?: TrainingSplit.forDays(answers?.daysPerWeek ?: saved.size.coerceIn(1, 6)),
                compoundOnly = answers?.compoundOnly ?: false,
                maxExercises = answers?.maxExercises ?: ProgramRules.DEFAULT_MAX_EXERCISES,
                sessionMinutes = answers?.sessionMinutes ?: ProgramRules.DEFAULT_SESSION_MINUTES,
            )
            OptimiseState.Ready(buildReport(OptimiseInput(saved, request, catalogue, strength)))
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OptimiseState.Loading)

    private val _saving = MutableStateFlow(false)

    /** True while a write is in flight; a second tap does nothing. */
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _applied = MutableStateFlow<Applied?>(null)
    val applied: StateFlow<Applied?> = _applied.asStateFlow()

    /** Writes [updates] (rite id and its new form) and, when they landed, shows [applied]. */
    fun apply(applied: Applied, updates: List<Pair<Long, PlannedPreset>>) {
        if (updates.isEmpty() || !_saving.compareAndSet(false, true)) return
        viewModelScope.launch {
            runCatching { repo.savePlannedPresets(updates) }
                .onSuccess {
                    _error.value = null
                    _applied.value = applied
                }
                .onFailure { _error.value = "Could not apply the changes: ${it.message}. Nothing was written." }
            _saving.value = false
        }
    }

    /** Puts every rite the last apply touched back as it was. */
    fun undo() {
        val applied = _applied.value ?: return
        if (!_saving.compareAndSet(false, true)) return
        viewModelScope.launch {
            runCatching { repo.savePlannedPresets(applied.before) }
                .onSuccess {
                    _error.value = null
                    _applied.value = null
                }
                .onFailure { _error.value = "Could not undo: ${it.message}. Nothing was changed." }
            _saving.value = false
        }
    }

    fun dismissApplied() {
        _applied.value = null
    }

    fun clearError() {
        _error.value = null
    }
}
