package com.ironvellum.app.ui.program

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironvellum.app.data.Repository
import com.ironvellum.app.domain.Exercise
import com.ironvellum.app.domain.FatigueInput
import com.ironvellum.app.domain.FatigueModel
import com.ironvellum.app.domain.FatigueReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** [report] is null until the first read finishes; [hasHistory] is false only before any workout was started. */
data class FatigueUi(val report: FatigueReport? = null, val hasHistory: Boolean = false, val loaded: Boolean = false)

/**
 * The fatigue report for the Muscles tab, from local history plus the live workout. Recomputed when the log
 * changes and every [tickMs], because decay moves the figure while nothing is logged. Nothing is written or uploaded.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MuscleFatigueViewModel(
    repo: Repository,
    nowMs: () -> Long = System::currentTimeMillis,
    tickMs: Long = 60_000L,
) : ViewModel() {

    private val liveFlow = repo.observeLiveSession().flatMapLatest { session ->
        if (session == null) flowOf(null) else repo.observeSessionSets(session.id).map { sets -> session to sets }
    }

    private val ticker = flow {
        while (true) {
            emit(nowMs())
            delay(tickMs)
        }
    }

    val ui: StateFlow<FatigueUi> = combine(
        repo.observeHistory(),
        repo.observeExercises().map { list -> list.associateBy(Exercise::id) },
        liveFlow,
        ticker,
    ) { history, exercises, live, now ->
        val sessions = FatigueInput.sessions(history, live, exercises, now)
        val first = FatigueInput.firstWorkoutMs(history, live?.first)
        FatigueUi(report = FatigueModel.estimate(sessions, now, first), hasHistory = first != null, loaded = true)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FatigueUi())
}
