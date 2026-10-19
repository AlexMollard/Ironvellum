package com.ironvellum.app.ui.train

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironvellum.app.R
import com.ironvellum.app.data.TrialCues
import com.ironvellum.app.domain.ExerciseMetric
import com.ironvellum.app.domain.RestAlert
import com.ironvellum.app.domain.SetClock
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The clock under the active set of a hold or a duration or of a distance run (a stopwatch
 * that fills its time). A hold: Start opens a 5-second get-ready, then the clock counts up
 * toward the target shown as a reference. Reaching it cues once and turns the face gold with
 * "Target reached" in words; it keeps counting and logs nothing. Stop opens the adjust step:
 * the stopped time with -1 s / +1 s (hold to repeat) and Log or Discard. A stop during the
 * get-ready records nothing. The clock itself is [SetClock]: plain state on the monotonic
 * clock, held by the view model, so everything shown here is computed from it and a rotation
 * changes nothing. The cues follow the alert mode ([RestAlert]).
 */
@Composable
internal fun TimedSetControl(
    metric: ExerciseMetric,
    /** The set's own figure in seconds: the target a hold counts toward. */
    targetSec: Int,
    clock: SetClock?,
    alert: RestAlert,
    onClock: (SetClock?) -> Unit,
    /** Logs the set with these seconds (the adjust step). */
    onLog: (seconds: Int) -> Unit,
    /** Fills the set's time with these seconds, without logging it (the stopwatch). */
    onFill: (seconds: Int) -> Unit,
    modifier: Modifier = Modifier,
    /** The monotonic clock; a test swaps it. */
    nowMs: () -> Long = SystemClock::elapsedRealtime,
    /** Plays a cue; the default is the rest alert's. A test swaps it. */
    onCue: ((SetClock.Cue) -> Unit)? = null,
) {
    val countdown = SetClock.countsDown(metric)
    // A hold toward nothing has nothing to count: a hold at 0 s waits for a figure.
    if (countdown && targetSec <= 0) return
    val context = LocalContext.current
    val running = clock?.running == true
    val now = clockNow(running, nowMs)
    val current by rememberUpdatedState(clock)
    val play by rememberUpdatedState(onCue ?: { cue -> TrialCues.play(context, alert, cue) })
    // The figure changed under a clock: the target moves to it, and the count carries on.
    LaunchedEffect(targetSec, clock?.targetSec) {
        val live = current
        if (live != null && countdown && live.targetSec != targetSec) onClock(live.copy(targetSec = targetSec, targetCued = false))
    }
    val phase = clock?.phase(now) ?: SetClock.Phase.IDLE
    val readySeconds = clock?.getReadySeconds(now) ?: 0
    val reached = clock?.targetReached(now) == true
    // A tick on the last seconds of the get-ready, one per displayed second.
    LaunchedEffect(readySeconds) {
        val cue = SetClock.readyCue(readySeconds)
        if (cue != SetClock.Cue.NONE) play(cue)
    }
    // The go and the target each cue once: the clock remembers it, so a rotation cannot repeat it.
    LaunchedEffect(clock, phase, reached) {
        val live = current ?: return@LaunchedEffect
        val (cue, next) = live.cueDue(nowMs())
        if (cue != SetClock.Cue.NONE) {
            play(cue)
            onClock(next)
        }
    }
    val seconds = clock?.displaySeconds(now) ?: 0
    val target = SetClock.face(targetSec)
    val clockDescription = when {
        phase == SetClock.Phase.GET_READY -> stringResource(R.string.timedset_get_ready_description, readySeconds)
        !countdown -> stringResource(R.string.timedset_elapsed_description, SetClock.face(seconds))
        reached -> stringResource(R.string.timedset_target_reached_description, SetClock.face(seconds), target)
        else -> stringResource(R.string.timedset_target_description, SetClock.face(seconds), target)
    }
    Column(modifier.fillMaxWidth()) {
        if (phase == SetClock.Phase.ADJUST && clock != null) {
            AdjustStep(
                seconds = clock.stoppedSec ?: SetClock.MIN_SECONDS,
                onStep = { delta -> current?.let { onClock(it.adjust(delta)) } },
                onLog = {
                    val logged = current?.stoppedSec ?: return@AdjustStep
                    onClock(null)
                    onLog(logged)
                },
                onDiscard = { onClock(null) },
            )
            return@Column
        }
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                // Past the target the whole row turns gold, so reaching it reads at a glance, not only in a small label.
                .background(if (clock?.targetReached(now) == true) IronvellumColors.SovereignGold.copy(alpha = 0.12f) else Color.Transparent),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val getReady = phase == SetClock.Phase.GET_READY
            // The digits and their label share a baseline inside one group, and the group centres against the action: baselines across the whole row pushed Start off the digits' middle.
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                if (getReady) readySeconds.toString() else SetClock.face(seconds),
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = when {
                    reached -> IronvellumColors.SovereignGold
                    running -> IronvellumColors.Ink
                    else -> IronvellumColors.InkMuted
                },
                maxLines = 1,
                modifier = Modifier.alignByBaseline().semantics {
                    contentDescription = clockDescription
                    // The get-ready is announced as it counts down; the count itself never is.
                    if (getReady) liveRegion = LiveRegionMode.Polite
                },
            )
            Text(
                when {
                    getReady -> stringResource(R.string.timedset_get_ready_label)
                    reached -> stringResource(R.string.timedset_target_reached_label)
                    countdown -> stringResource(R.string.timedset_target_label, target)
                    else -> stringResource(R.string.timedset_elapsed_label)
                },
                style = MaterialTheme.typography.labelSmall,
                fontFamily = ChakraPetch,
                fontWeight = if (reached) FontWeight.Bold else null,
                color = if (reached) IronvellumColors.SovereignGold else IronvellumColors.InkMuted,
                modifier = Modifier
                    // The label sits on the digits' baseline, not at their middle.
                    .alignByBaseline()
                    // Said once when it appears: the state is in words, not only in colour.
                    .semantics { if (reached) liveRegion = LiveRegionMode.Polite },
            )
            }
            when {
                phase == SetClock.Phase.IDLE || clock == null ->
                    RowAction(
                        stringResource(if (countdown) R.string.timedset_start_button else R.string.timedset_start_stopwatch_button),
                        IronvellumColors.Ink,
                    ) {
                        onClock((if (countdown) SetClock.countdown(targetSec) else SetClock.stopwatch()).start(nowMs()))
                    }
                // A hold: Stop in the get-ready drops it; while counting it opens the adjust step.
                countdown -> RowAction(stringResource(R.string.timedset_stop_button), IronvellumColors.Ink) { onClock(clock.stop(nowMs())) }
                // A stopwatch: Stop fills the time and clears it; the lifter still ticks the set.
                else -> RowAction(stringResource(R.string.timedset_stop_button), IronvellumColors.Ink) {
                    val filled = clock.stopwatchSeconds(nowMs())
                    onClock(null)
                    onFill(filled)
                }
            }
        }
    }
}

/** The stopped time with -1 s and +1 s (hold to repeat), and Log or Discard. */
@Composable
private fun AdjustStep(seconds: Int, onStep: (Int) -> Unit, onLog: () -> Unit, onDiscard: () -> Unit) {
    val stoppedDescription = stringResource(R.string.timedset_stopped_description, SetClock.face(seconds))
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                SetClock.face(seconds),
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = IronvellumColors.Ink,
                maxLines = 1,
                modifier = Modifier.alignByBaseline().semantics { contentDescription = stoppedDescription },
            )
            Text(
                stringResource(R.string.timedset_stopped_label),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = ChakraPetch,
                color = IronvellumColors.InkMuted,
                modifier = Modifier.weight(1f).alignByBaseline(),
            )
            RepeatAction(
                stringResource(R.string.timedset_minus_second_button),
                stringResource(R.string.timedset_minus_second_description),
                IronvellumColors.Ink,
            ) { onStep(-1) }
            RepeatAction(
                stringResource(R.string.timedset_plus_second_button),
                stringResource(R.string.timedset_plus_second_description),
                IronvellumColors.Ink,
            ) { onStep(+1) }
        }
        // Pulled back by the actions' own 12dp padding so "Log" starts at the timer's left edge.
        Row(Modifier.fillMaxWidth().offset(x = (-12).dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            RowAction(stringResource(R.string.timedset_log_button, SetClock.face(seconds)), IronvellumColors.Emerald, onLog)
            RowAction(stringResource(R.string.timedset_discard_button), IronvellumColors.InkMuted, onDiscard)
        }
    }
}

/**
 * A text action that steps once on press and keeps stepping while held. A screen reader's
 * double tap steps once through the click action.
 */
@Composable
private fun RepeatAction(label: String, description: String, color: Color, onStep: () -> Unit) {
    val step by rememberUpdatedState(onStep)
    val scope = rememberCoroutineScope()
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .widthIn(min = 56.dp)
            .semantics {
                role = Role.Button
                contentDescription = description
                onClick(label = description) { step(); true }
            }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    step()
                    val repeat = scope.launch {
                        delay(HOLD_BEFORE_REPEAT_MS)
                        while (true) {
                            step()
                            delay(REPEAT_EVERY_MS)
                        }
                    }
                    try {
                        tryAwaitRelease()
                    } finally {
                        repeat.cancel()
                    }
                })
            }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = color)
    }
}

private const val HOLD_BEFORE_REPEAT_MS = 450L
private const val REPEAT_EVERY_MS = 90L

/** The clock of a running [SetClock]: ticks while it runs, and only then. */
@Composable
private fun clockNow(running: Boolean, nowMs: () -> Long): Long {
    var now by remember { mutableLongStateOf(nowMs()) }
    LaunchedEffect(running) {
        now = nowMs()
        while (running) {
            delay(100)
            now = nowMs()
        }
    }
    return now
}
