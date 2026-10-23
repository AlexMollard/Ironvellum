package com.ironvellum.app.ui.train

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class TrainTab { PLAN, MUSCLES }

enum class MusclesView { FATIGUE, COVERAGE }

/**
 * Which Train tab and which Muscles view the lifter last had open. Held for the life of the process only:
 * never persisted, so a restart opens on Plan and Fatigue. A link always wins over what was remembered.
 */
object TrainTabMemory {
    var tab by mutableStateOf(TrainTab.PLAN)
    var view by mutableStateOf(MusclesView.FATIGUE)

    /** What every link that used to open the Coverage screen does now. */
    fun openCoverage() {
        tab = TrainTab.MUSCLES
        view = MusclesView.COVERAGE
    }

    /** What the plan builder's "Muscles right now" row does. */
    fun openFatigue() {
        tab = TrainTab.MUSCLES
        view = MusclesView.FATIGUE
    }

    internal fun reset() {
        tab = TrainTab.PLAN
        view = MusclesView.FATIGUE
    }
}
