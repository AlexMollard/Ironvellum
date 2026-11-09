package com.ironvellum.app.ui.preparation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Info
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ironvellum.app.R
import com.ironvellum.app.data.TrialCues
import com.ironvellum.app.data.TrialPrefs
import com.ironvellum.app.domain.*
import com.ironvellum.app.ui.components.*
import com.ironvellum.app.ui.ironvellumRepository
import com.ironvellum.app.ui.program.ExerciseInspection
import com.ironvellum.app.ui.theme.IronvellumColors
import kotlinx.coroutines.delay

private fun runnerKey(sessionId: Long, kind: PreparationKind) = "preparation_runner_" + sessionId + '_' + kind.name

@Composable
private fun unitContext(step: PreparationStep, round: Int, repeat: Int): String {
    val rounds = preparationText(R.string.preparation_round, R.string.preparation_round__t, round + 1, step.rounds)
    val direction = when (step.repeat) {
        PreparationRepeat.ONCE -> null
        PreparationRepeat.SIDES -> preparationText(if (repeat == 0) R.string.preparation_left else R.string.preparation_right,
            if (repeat == 0) R.string.preparation_left__t else R.string.preparation_right__t)
        PreparationRepeat.DIRECTIONS -> preparationText(if (repeat == 0) R.string.preparation_direction_first else R.string.preparation_direction_second,
            if (repeat == 0) R.string.preparation_direction_first__t else R.string.preparation_direction_second__t)
    }
    return listOfNotNull(direction, rounds).joinToString(" · ")
}

@Composable
fun PreparationRunnerScreen(
    sessionId: Long, kind: PreparationKind, onBack: () -> Unit, onComplete: () -> Unit,
    onChange: (PreparationChoice) -> Unit = {}, onCopy: (PreparationSnapshot) -> Unit = {},
    viewModel: PreparationRunnerViewModel = viewModel(key = runnerKey(sessionId, kind), factory = viewModelFactory {
        initializer { PreparationRunnerViewModel(ironvellumRepository(), sessionId, kind) }
    }),
    initialPreview: Boolean = false,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var leaving by rememberSaveable { mutableStateOf(false) }
    var finishing by rememberSaveable { mutableStateOf(false) }
    var preview by rememberSaveable { mutableStateOf(initialPreview) }
    var previewReturnsToRunner by rememberSaveable { mutableStateOf(false) }
    var inspected by rememberSaveable { mutableStateOf<String?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val state = ui.state
    val progress = state?.progress
    LaunchedEffect(ui.loaded, state) { if (ui.loaded && state == null) onBack() }
    val terminal = progress?.status in listOf(PreparationStatus.DONE, PreparationStatus.SKIPPED)
    val overview = progress != null && !progress.hasProgress
    val step = state?.snapshot?.steps?.getOrNull(progress?.stepIndex ?: 0)
    val guided = state != null && !terminal && !overview && !preview && step != null
    val lifecycleOwner = LocalLifecycleOwner.current
    val androidContext = LocalContext.current
    val trialPrefs = remember(androidContext) { TrialPrefs(androidContext) }
    LaunchedEffect(progress, guided, lifecycleOwner) {
        if (guided && progress?.status == PreparationStatus.RUNNING) {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    now = System.currentTimeMillis()
                    if (progress.deadlineMs?.let { now >= it } == true) {
                        viewModel.act(PreparationAction.TIMER_EXPIRED, progress.currentUnitKey)
                    }
                    delay(100)
                }
            }
        }
    }
    var previousUnitKey by remember { mutableStateOf(progress?.currentUnitKey) }
    LaunchedEffect(progress?.currentUnitKey) {
        val previous = previousUnitKey
        previousUnitKey = progress?.currentUnitKey
        if (previous != null && previous != progress?.currentUnitKey && previous in progress?.completedUnitKeys.orEmpty()) {
            TrialCues.play(androidContext, trialPrefs.restAlert, SetClock.Cue.DONE)
        }
    }
    val returnText = preparationText(
        if (kind == PreparationKind.WARMUP) R.string.preparation_return_workout else R.string.preparation_return_summary,
        if (kind == PreparationKind.WARMUP) R.string.preparation_return_workout__t else R.string.preparation_return_summary__t)
    val leave: () -> Unit = {
        when {
            preview -> if (previewReturnsToRunner) preview = false else onBack()
            terminal || overview -> onBack()
            else -> viewModel.leave { leaving = true }
        }
    }
    // Queued actions belong to the unit on screen, never a later cursor.
    val displayedKey = progress?.currentUnitKey
    val context = if (step != null && progress != null) unitContext(step, progress.roundIndex, progress.repeatIndex) else ""
    val nextUnit = if (state != null && progress != null) {
        val units = state.snapshot.steps.flatMapIndexed { index, s -> (0 until s.rounds).flatMap { round ->
            (0 until s.repetitions).map { repeat -> Triple(index, round, repeat) }
        } }
        units.dropWhile { (s, r, p) -> "$s:$r:$p" != displayedKey }.drop(1).firstOrNull { (s, r, p) ->
            "$s:$r:$p" !in progress.completedUnitKeys && "$s:$r:$p" !in progress.skippedUnitKeys
        }
    } else null
    val next = if (nextUnit != null && state != null) {
        val nextStep = state.snapshot.steps[nextUnit.first]
        val nextContext = unitContext(nextStep, nextUnit.second, nextUnit.third)
        if (nextUnit.first == progress?.stepIndex) nextContext else listOf(nextStep.exerciseName, nextContext).joinToString(" · ")
    } else preparationText(R.string.preparation_final_next, R.string.preparation_final_next__t, kindLabel(kind))
    val primary = when {
        !ui.loaded || state == null || guided -> null
        terminal || state.snapshot.steps.isEmpty() -> returnText
        else -> preparationText(if (progress?.hasProgress == true) R.string.preparation_resume else R.string.preparation_begin,
            if (progress?.hasProgress == true) R.string.preparation_resume__t else R.string.preparation_begin__t, kindLabel(kind))
    }
    PreparationFrame(kindLabel(kind), leave, primary, {
        if (terminal || state?.snapshot?.steps?.isEmpty() == true) onComplete()
        else viewModel.act(PreparationAction.START, after = { preview = false })
    }, enabled = ui.loaded && !ui.busy && state != null, actions = {
        if (state != null) PreparationMenu { close ->
            if (guided) DropdownMenuItem(text = { Text(preparationText(R.string.preparation_skip_unit, R.string.preparation_skip_unit__t)) },
                onClick = { close(); viewModel.act(PreparationAction.SKIP_UNIT, displayedKey) }, enabled = !ui.busy)
            if (!preview && !overview) DropdownMenuItem(text = { Text(preparationText(R.string.preparation_preview, R.string.preparation_preview__t)) },
                onClick = { close(); previewReturnsToRunner = true; preview = true })
            DropdownMenuItem(text = { Text(preparationText(R.string.preparation_change, R.string.preparation_change__t)) }, onClick = {
                close()
                val snapshot = state.snapshot
                val choice = when {
                    snapshot.sourceRoutineId != null -> PreparationChoice(PreparationMode.SAVED, snapshot.sourceRoutineId)
                    snapshot.steps.size == snapshot.legacyStepCount -> PreparationChoice(PreparationMode.NONE)
                    else -> PreparationChoice()
                }
                viewModel.leave { onChange(choice) }
            })
            if (state.snapshot.steps.isNotEmpty()) DropdownMenuItem(text = { Text(preparationText(R.string.preparation_edit_copy, R.string.preparation_edit_copy__t)) },
                onClick = { close(); viewModel.leave { onCopy(state.snapshot) } })
            if (!terminal && state.snapshot.steps.isNotEmpty()) DropdownMenuItem(text = { Text(preparationText(R.string.preparation_finish, R.string.preparation_finish__t)) },
                onClick = { close(); finishing = true })
        }
    }, dock = if (guided) ({
        PreparationNextDock(next, preparationText(R.string.preparation_complete_description, R.string.preparation_complete_description__t,
            preparationText(R.string.preparation_complete_unit, R.string.preparation_complete_unit__t), step.exerciseName, context), !ui.busy) {
            viewModel.act(PreparationAction.COMPLETE_UNIT, displayedKey)
        }
    }) else null) {
        if (ui.failed) PreparationCaption(preparationText(R.string.preparation_error, R.string.preparation_error__t), error = true)
        if (state != null && progress != null) {
            val snapshot = state.snapshot
            val total = snapshot.steps.sumOf { it.rounds * it.repetitions }
            if (!preview && !overview) PreparationCaption(snapshot.name)
            if (preview || overview) {
                PreparationPreview(snapshot, ui.exercises)
                PreparationCaption(preparationText(R.string.preparation_no_scoring, R.string.preparation_no_scoring__t))
            } else if (terminal) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Check, null, tint = IronvellumColors.Emerald, modifier = Modifier.size(28.dp))
                    Text(preparationText(if (progress.status == PreparationStatus.DONE) R.string.preparation_finished else R.string.preparation_skipped,
                    if (progress.status == PreparationStatus.DONE) R.string.preparation_finished__t else R.string.preparation_skipped__t, kindLabel(kind)),
                    style = MaterialTheme.typography.headlineSmall, color = IronvellumColors.Ink)
                }
                Spacer(Modifier.height(16.dp))
                InkPanel(contentPadding = PaddingValues(16.dp)) {
                    Text(preparationText(R.string.preparation_result_counts, R.string.preparation_result_counts__t,
                        progress.completedUnitKeys.size, total, progress.skippedUnitKeys.size),
                        style = MaterialTheme.typography.titleMedium, color = IronvellumColors.Ink)
                    PreparationCaption(preparationMetadata(snapshot))
                }
                TextAction(preparationText(R.string.preparation_view, R.string.preparation_view__t), { previewReturnsToRunner = true; preview = true })
            } else if (step != null) {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    PreparationCaption(preparationText(R.string.preparation_movement_progress, R.string.preparation_movement_progress__t, progress.stepIndex + 1, snapshot.steps.size))
                    PreparationCaption(preparationText(R.string.preparation_progress, R.string.preparation_progress__t,
                        progress.completedUnitKeys.size, total))
                }
                LinearProgressIndicator(progress = { if (total == 0) 0f else (progress.completedUnitKeys.size + progress.skippedUnitKeys.size).toFloat() / total },
                    modifier = Modifier.fillMaxWidth().height(3.dp), color = IronvellumColors.Emerald)
                var folded by rememberSaveable(displayedKey) { mutableStateOf(false) }
                EntryCard(step.exerciseName, preparationText(if (folded) R.string.preparation_unfold else R.string.preparation_fold,
                    if (folded) R.string.preparation_unfold__t else R.string.preparation_fold__t, step.exerciseName),
                    { folded = !folded }, titleMaxLines = Int.MAX_VALUE, headerActions = {
                        IconButton(onClick = { inspected = step.exerciseName }) {
                            Icon(Icons.Outlined.Info, stringResource(R.string.session_info_description, step.exerciseName), tint = IronvellumColors.InkMuted)
                        }
                    }, showFooter = false, actions = {}) {
                    if (!folded) {
                        Text(context, style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                        val exercise = ui.exercises.firstOrNull { it.id == step.exerciseId }
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            ExerciseArtSlot(step.exerciseName, exercise?.muscleGroup ?: MuscleGroup.CORE, Modifier.size(180.dp))
                        }
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            if (step.measure == PreparationMeasure.SECONDS) {
                                val ready = progress.readyUntilMs?.let { ((it - now + 999) / 1000).coerceAtLeast(0) } ?: 0
                                val seconds = if (progress.status == PreparationStatus.OPEN) step.target.toLong() else (progress.remainingMs(now) + 999) / 1000
                                Text(if (ready > 0) preparationText(R.string.preparation_ready, R.string.preparation_ready__t, ready) else ClockText.minutesSeconds(seconds),
                                    style = MaterialTheme.typography.displayMedium, color = IronvellumColors.Ink)
                                PreparationCaption(preparationTarget(step))
                                if (seconds == 0L && progress.status == PreparationStatus.RUNNING) PreparationCaption(preparationText(R.string.preparation_reached, R.string.preparation_reached__t))
                                InkChip(preparationText(if (progress.status == PreparationStatus.RUNNING) R.string.preparation_pause else R.string.preparation_start_timer,
                                    if (progress.status == PreparationStatus.RUNNING) R.string.preparation_pause__t else R.string.preparation_start_timer__t)) {
                                    viewModel.act(if (progress.status == PreparationStatus.RUNNING) PreparationAction.PAUSE else PreparationAction.RESUME)
                                }
                            } else PreparationCaption(preparationTarget(step))
                        }
                        val guide = ExerciseGuides.forName(step.exerciseName)
                        val cue = step.note.takeIf { it.isNotBlank() } ?: guide?.cues?.firstOrNull() ?: guide?.steps?.firstOrNull()
                        if (cue != null) PreparationCaption(cue)
                    }
                }
                PreparationCaption(preparationText(R.string.preparation_up_next, R.string.preparation_up_next__t))
                Text(next, style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.Ink)
            }
        }
    }
    if (leaving) PreparationConfirm(preparationText(R.string.preparation_leave_title, R.string.preparation_leave_title__t),
        preparationText(R.string.preparation_leave_body, R.string.preparation_leave_body__t),
        preparationText(R.string.preparation_leave, R.string.preparation_leave__t), { viewModel.leave(onBack) }, { leaving = false },
        dismissText = stringResource(R.string.common_cancel))
    if (finishing) PreparationConfirm(preparationText(R.string.preparation_finish_title, R.string.preparation_finish_title__t),
        preparationText(R.string.preparation_finish_body, R.string.preparation_finish_body__t),
        preparationText(R.string.preparation_finish, R.string.preparation_finish__t),
        { viewModel.act(PreparationAction.SKIP_ROUTINE, after = { finishing = false; preview = false }) }, { finishing = false },
        dismissText = stringResource(R.string.common_cancel))
    inspected?.let { ExerciseInspection(ui.exercises, it, "") { inspected = null } }
}
