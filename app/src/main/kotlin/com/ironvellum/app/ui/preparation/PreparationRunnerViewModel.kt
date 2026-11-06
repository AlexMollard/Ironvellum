package com.ironvellum.app.ui.preparation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironvellum.app.data.Repository
import com.ironvellum.app.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PreparationRunnerUi(
    val loaded: Boolean = false, val state: SessionPreparation? = null,
    val exercises: List<Exercise> = emptyList(), val busy: Boolean = false, val failed: Boolean = false,
)

/** Room owns the cursor and clock. UI ticks never write or advance anything. */
class PreparationRunnerViewModel(
    private val repo: Repository, private val sessionId: Long, private val kind: PreparationKind,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val _ui = MutableStateFlow(PreparationRunnerUi())
    val ui = _ui.asStateFlow()
    init {
        viewModelScope.launch {
            try {
                repo.preparations.ensureSession(sessionId)
                combine(repo.preparations.observeSession(sessionId, kind), repo.observeExercises()) { state, exercises ->
                    state?.forWarmupSetting(repo.preparations.warmupEnabled) to exercises
                }
                    .collect { (state, exercises) -> _ui.update { it.copy(loaded = true, state = state, exercises = exercises) } }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { _ui.update { it.copy(failed = true) } }
        }
    }
    fun act(action: PreparationAction, expectedUnitKey: String? = null, after: () -> Unit = {}) {
        if (_ui.value.busy) return
        _ui.update { it.copy(busy = true, failed = false) }
        viewModelScope.launch {
            try {
                // A route may be removed as soon as after runs: finish the transaction first.
                withContext(NonCancellable) { repo.preparations.act(sessionId, kind, action, clock(), expectedUnitKey) }
                after()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { _ui.update { it.copy(failed = true) } }
            finally { _ui.update { it.copy(busy = false) } }
        }
    }
    fun leave(after: () -> Unit) = act(PreparationAction.PAUSE, after = after)
}
