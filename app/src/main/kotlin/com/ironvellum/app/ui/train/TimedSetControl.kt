package com.ironvellum.app.ui.train

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironvellum.app.data.TrialCues
import com.ironvellum.app.domain.ExerciseMetric
import com.ironvellum.app.domain.RestAlert
import com.ironvellum.app.domain.SetClock
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors
import kotlinx.coroutines.delay

/**
 * The clock under the active set of a hold or a duration (a countdown toward its figure) or
 * of a distance run (a stopwatch that fills its time). It sits on the row like the steppers
 * do: Start, then Pause and Finish; a countdown that runs out logs the set at its target,
 * Finish logs the time actually held. The last 3 seconds and the end cue as the alert mode
 * ([RestAlert]) allows. The clock itself is [SetClock]: plain state on the monotonic clock,
 * held by the view model, so everything shown here is computed from it.
 */
@Composable
internal fun TimedSetControl(
    metric: ExerciseMetric,
    /** The set's own figure in seconds: what a countdown runs toward. */
    targetSec: Int,
    clock: SetClock?,
    alert: RestAlert,
    onClock: (SetClock?) -> Unit,
    /** Logs the set with these seconds (countdowns). */
    onLog: (seconds: Int) -> Unit,
    /** Fills the set's time with these seconds, without logging it (the stopwatch). */
    onFill: (seconds: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val countdown = SetClock.countsDown(metric)
    // A countdown toward nothing has nothing to count: a hold at 0 s waits for a figure.
    if (countdown && targetSec <= 0) return
    val context = LocalContext.current
    val running = clock?.running == true
    val now = clockNow(running)
    // The figure changed under a clock: it was counting toward something that is gone.
    LaunchedEffect(targetSec, clock?.targetSec) {
        if (clock != null && countdown && clock.targetSec != targetSec) onClock(null)
    }
    val current by rememberUpdatedState(clock)
    val display = clock?.displaySeconds(now) ?: if (countdown) targetSec else 0
    // One cue per change of the displayed second, and the end logs the set at its target.
    LaunchedEffect(display, running) {
        val live = current ?: return@LaunchedEffect
        if (!countdown || !live.running) return@LaunchedEffect
        val cue = SetClock.cueAt(display)
        if (cue != SetClock.Cue.NONE) TrialCues.play(context, alert, cue)
        if (live.isDone(SystemClock.elapsedRealtime())) {
            onClock(null)
            onLog(live.targetSec ?: targetSec)
        }
    }
    Row(
        modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            SetClock.face(display),
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            color = if (running) IronvellumColors.Ink else IronvellumColors.InkMuted,
            maxLines = 1,
            modifier = Modifier.semantics {
                contentDescription = if (countdown) "${SetClock.face(display)} left" else "${SetClock.face(display)} elapsed"
            },
        )
        Text(
            if (countdown) "LEFT" else "ELAPSED",
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            color = IronvellumColors.InkMuted,
            modifier = Modifier.weight(1f),
        )
        when {
            // Idle.
            clock == null || (!running && !clock.paused) ->
                RowAction(if (countdown) "Start" else "Start stopwatch", IronvellumColors.Ink) {
                    onClock((if (countdown) SetClock.countdown(targetSec) else SetClock.stopwatch()).start(SystemClock.elapsedRealtime()))
                }
            countdown && running -> {
                RowAction("Pause", IronvellumColors.InkMuted) { onClock(clock.pause(SystemClock.elapsedRealtime())) }
                RowAction("Finish", IronvellumColors.Ink) { finishEarly(clock, onClock, onLog) }
            }
            countdown -> {
                RowAction("Resume", IronvellumColors.Ink) { onClock(clock.start(SystemClock.elapsedRealtime())) }
                RowAction("Finish", IronvellumColors.InkMuted) { finishEarly(clock, onClock, onLog) }
            }
            // A stopwatch: Stop fills the time and clears it; the lifter still ticks the set.
            else -> RowAction("Stop", IronvellumColors.Ink) {
                val seconds = clock.loggedSeconds(SystemClock.elapsedRealtime())
                onClock(null)
                onFill(seconds)
            }
        }
    }
}

private fun finishEarly(clock: SetClock, onClock: (SetClock?) -> Unit, onLog: (Int) -> Unit) {
    val seconds = clock.loggedSeconds(SystemClock.elapsedRealtime())
    onClock(null)
    // Finishing before the first second is a stop, not a 0-second hold to log.
    if (seconds > 0) onLog(seconds)
}

/** The clock of a running [SetClock]: ticks while it runs, and only then. */
@Composable
private fun clockNow(running: Boolean): Long {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(running) {
        now = SystemClock.elapsedRealtime()
        while (running) {
            delay(100)
            now = SystemClock.elapsedRealtime()
        }
    }
    return now
}
