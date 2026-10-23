package com.ironvellum.app.ui.train

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ironvellum.app.R
import com.ironvellum.app.data.Words
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.ui.asString
import com.ironvellum.app.ui.components.InkChip
import com.ironvellum.app.ui.components.TabTitle
import com.ironvellum.app.ui.ironvellumRepository
import com.ironvellum.app.ui.program.MusclesView

/**
 * Train: the title and Begin chip over the plan page, which carries a "Muscles right now" card that opens
 * the Muscles screen. Train has no tab row of its own.
 */
@Composable
fun TrainScreen(
    onOpenRite: (Long) -> Unit,
    onNew: () -> Unit,
    onGenerate: (mode: String, presetId: Long?) -> Unit,
    onStartSession: (Long) -> Unit,
    onQuickSession: (Long) -> Unit,
    onOpenExercises: () -> Unit,
    onOpenLog: () -> Unit,
    onOpenMuscles: (MusclesView) -> Unit,
    onOpenWorkout: (Long) -> Unit = {},
    viewModel: PresetsViewModel =
        viewModel(factory = viewModelFactory {
            val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
            initializer { PresetsViewModel(ironvellumRepository(), appContext) }
        }),
) {
    val live by viewModel.live.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(12.dp))
            // The title's row is a fixed height: the chip leaves (a trial is under way) and the title stays put.
            TabTitle(stringResource(R.string.presets_title)) {
                // No second trial while one is under way.
                if (live == null) {
                    InkChip(
                        Words.openTrial.s.replaceFirstChar { it.uppercase() },
                        UiText.themed(R.string.presets_begin_open_description, R.string.presets_begin_open_description__t).asString(),
                        Icons.Outlined.PlayArrow,
                    ) { viewModel.beginQuick(onQuickSession) }
                }
            }
        }
        Box(Modifier.weight(1f)) {
            PresetsScreen(
                onOpenRite = onOpenRite,
                onNew = onNew,
                onGenerate = onGenerate,
                onStartSession = onStartSession,
                onQuickSession = onQuickSession,
                onOpenExercises = onOpenExercises,
                onOpenLog = onOpenLog,
                onOpenMuscles = onOpenMuscles,
                onOpenWorkout = onOpenWorkout,
                viewModel = viewModel,
            )
        }
    }
}
