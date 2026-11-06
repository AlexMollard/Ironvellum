package com.ironvellum.app.ui.preparation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ironvellum.app.R
import com.ironvellum.app.data.Units
import com.ironvellum.app.domain.*
import com.ironvellum.app.ui.components.*
import com.ironvellum.app.ui.ironvellumRepository
import com.ironvellum.app.ui.program.ExerciseInspection
import com.ironvellum.app.ui.theme.IronvellumColors
import kotlinx.coroutines.delay

private fun runnerKey(sessionId: Long, kind: PreparationKind) = "preparation_runner_" + sessionId + '_' + kind.name

@Composable
fun PreparationRunnerScreen(
    sessionId: Long, kind: PreparationKind, onBack: () -> Unit, onComplete: () -> Unit,
    onChange: (PreparationChoice) -> Unit = {},
    onCopy: (PreparationSnapshot) -> Unit = {},
    viewModel: PreparationRunnerViewModel = viewModel(key = runnerKey(sessionId, kind), factory = viewModelFactory {
        initializer { PreparationRunnerViewModel(ironvellumRepository(), sessionId, kind) }
    }),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var leaving by rememberSaveable { mutableStateOf(false) }
    var preview by rememberSaveable { mutableStateOf(false) }
    var inspected by rememberSaveable { mutableStateOf<String?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val state = ui.state
    val progress = state?.progress
    LaunchedEffect(progress) {
        while (progress?.status == PreparationStatus.RUNNING) { now = System.currentTimeMillis(); delay(100) }
    }
    LaunchedEffect(ui.loaded, state) { if (ui.loaded && state == null) onBack() }
    val terminal = progress?.status in listOf(PreparationStatus.DONE, PreparationStatus.SKIPPED)
    val overview = progress != null && !progress.hasProgress
    val step = state?.snapshot?.steps?.getOrNull(progress?.stepIndex ?: 0)
    val returnText = preparationText(
        if (kind == PreparationKind.WARMUP) R.string.preparation_return_workout else R.string.preparation_return_summary,
        if (kind == PreparationKind.WARMUP) R.string.preparation_return_workout__t else R.string.preparation_return_summary__t)
    val leave = {
        if (terminal || overview) viewModel.leave(onBack)
        else viewModel.leave { leaving = true }
    }
    // Capture this composition's identity. Queued taps must never read a later cursor.
    val displayedKey = progress?.currentUnitKey
    val primary = when {
        !ui.loaded || state == null -> null
        terminal || state.snapshot.steps.isEmpty() -> returnText
        preview -> preparationText(R.string.preparation_resume, R.string.preparation_resume__t, kindLabel(kind))
        overview -> preparationText(R.string.preparation_begin, R.string.preparation_begin__t, kindLabel(kind))
        else -> preparationText(R.string.preparation_complete_unit, R.string.preparation_complete_unit__t)
    }
    PreparationFrame(kindLabel(kind), leave, primary, {
        when {
            terminal || state?.snapshot?.steps?.isEmpty() == true -> viewModel.leave(onComplete)
            preview -> preview = false
            overview -> viewModel.act(PreparationAction.START)
            else -> viewModel.act(PreparationAction.COMPLETE_UNIT, displayedKey)
        }
    }, enabled = ui.loaded && !ui.busy && state != null) {
        if (ui.failed) PreparationCaption(preparationText(R.string.preparation_error, R.string.preparation_error__t), error = true)
        if (state != null) {
            val total = state.snapshot.steps.sumOf { it.rounds * it.repetitions }
            val completed = progress!!.completedUnitKeys.size + progress.skippedUnitKeys.size
            PreparationCaption(preparationText(R.string.preparation_progress, R.string.preparation_progress__t, completed, total))
            if (overview || preview || terminal) {
                if (terminal) Text(preparationText(
                    if (progress.status == PreparationStatus.DONE) R.string.preparation_finished else R.string.preparation_skipped,
                    if (progress.status == PreparationStatus.DONE) R.string.preparation_finished__t else R.string.preparation_skipped__t, kindLabel(kind)),
                    style = MaterialTheme.typography.headlineSmall, color = IronvellumColors.Ink)
                PreparationPreview(state.snapshot, ui.exercises)
                PreparationCaption(preparationText(R.string.preparation_no_scoring, R.string.preparation_no_scoring__t))
                TextAction(preparationText(R.string.preparation_change, R.string.preparation_change__t), {
                    val snapshot = state.snapshot
                    val choice = when {
                        snapshot.sourceRoutineId != null -> PreparationChoice(PreparationMode.SAVED, snapshot.sourceRoutineId)
                        snapshot.steps.size == snapshot.legacyStepCount -> PreparationChoice(PreparationMode.NONE)
                        else -> PreparationChoice()
                    }
                    viewModel.leave { onChange(choice) }
                })
                if (state.snapshot.steps.isNotEmpty()) TextAction(preparationText(R.string.preparation_edit_copy, R.string.preparation_edit_copy__t),
                    { viewModel.leave { onCopy(state.snapshot) } })
            } else if (step != null) {
                Text(step.exerciseName, style = MaterialTheme.typography.headlineSmall, color = IronvellumColors.Ink,
                    modifier = Modifier.semantics { heading() })
                PreparationCaption(preparationText(R.string.preparation_round, R.string.preparation_round__t, progress.roundIndex + 1, step.rounds))
                when (step.repeat) {
                    PreparationRepeat.SIDES -> PreparationCaption(preparationText(
                        if (progress.repeatIndex == 0) R.string.preparation_left else R.string.preparation_right,
                        if (progress.repeatIndex == 0) R.string.preparation_left__t else R.string.preparation_right__t))
                    PreparationRepeat.DIRECTIONS -> PreparationCaption(preparationText(
                        if (progress.repeatIndex == 0) R.string.preparation_direction_first else R.string.preparation_direction_second,
                        if (progress.repeatIndex == 0) R.string.preparation_direction_first__t else R.string.preparation_direction_second__t))
                    else -> Unit
                }
                val exercise = ui.exercises.firstOrNull { it.id == step.exerciseId }
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    ExerciseArtSlot(step.exerciseName, exercise?.muscleGroup ?: MuscleGroup.CORE, Modifier.size(180.dp))
                }
                PreparationCaption(step.legacyTargetLine { Units.withUnit(it) } ?: preparationTarget(step))
                if (step.measure == PreparationMeasure.SECONDS) {
                    val ready = progress.readyUntilMs?.let { ((it - now + 999) / 1000).coerceAtLeast(0) } ?: 0
                    val seconds = if (progress.status == PreparationStatus.OPEN) step.target.toLong() else (progress.remainingMs(now) + 999) / 1000
                    Text(if (ready > 0) preparationText(R.string.preparation_ready, R.string.preparation_ready__t, ready)
                        else ClockText.minutesSeconds(seconds),
                        style = MaterialTheme.typography.headlineLarge, color = IronvellumColors.Ink)
                    if (seconds == 0L && progress.status == PreparationStatus.RUNNING) PreparationCaption(preparationText(R.string.preparation_reached, R.string.preparation_reached__t))
                    IronvellumButton(preparationText(
                        if (progress.status == PreparationStatus.RUNNING) R.string.preparation_pause else R.string.preparation_start_timer,
                        if (progress.status == PreparationStatus.RUNNING) R.string.preparation_pause__t else R.string.preparation_start_timer__t),
                        { viewModel.act(if (progress.status == PreparationStatus.RUNNING) PreparationAction.PAUSE else PreparationAction.RESUME) }, enabled = !ui.busy)
                }
                if (step.note.isNotBlank()) PreparationCaption(step.note)
                ExerciseGuides.forName(step.exerciseName)?.let { guide ->
                    guide.steps.firstOrNull()?.let { PreparationCaption(it) }
                    guide.cues.firstOrNull()?.let { PreparationCaption(it) }
                }
                TextAction(stringResource(R.string.preseteditor_info_button), { inspected = step.exerciseName })
                TextAction(preparationText(R.string.preparation_preview, R.string.preparation_preview__t), { viewModel.leave { preview = true } })
                TextAction(preparationText(R.string.preparation_skip_unit, R.string.preparation_skip_unit__t), { viewModel.act(PreparationAction.SKIP_UNIT, displayedKey) })
            }
        }
    }
    if (leaving) PreparationConfirm(
        preparationText(R.string.preparation_leave_title, R.string.preparation_leave_title__t),
        preparationText(R.string.preparation_leave_body, R.string.preparation_leave_body__t),
        preparationText(R.string.preparation_leave, R.string.preparation_leave__t),
        { viewModel.leave(onBack) }, { leaving = false },
        dismissText = stringResource(R.string.common_cancel),
    )
    inspected?.let { ExerciseInspection(ui.exercises, it, "") { inspected = null } }
}
