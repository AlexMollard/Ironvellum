package com.ironvellum.app.ui.dashboard

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/** Adjacent day pages share one continuous offset inside a stationary viewport. */
internal class TodaySwipe(
    val gestures: Modifier,
    val content: (Int) -> Modifier,
    val renderDay: () -> Int,
    val activePage: () -> Int,
    val dayOffset: () -> Float,
    val selectDay: (Int) -> Unit,
)

private fun wrappedDay(day: Int): Int = (day - 1 + 7) % 7 + 1

private class SwipeMotion(day: Int) {
    var horizontal = 0f
    var vertical = 0f
    var width by mutableFloatStateOf(1f)
    var startOffset = 0f
    var offset by mutableFloatStateOf(0f)
    var renderDay by mutableIntStateOf(day)
    var targetStep by mutableIntStateOf(0)
    var settling: Job? = null

    fun reset(day: Int) {
        settling?.cancel()
        renderDay = day
        offset = 0f
        targetStep = 0
    }

    fun rebase() {
        // Keep both visible pages in exactly the same places when a new drag interrupts settling.
        renderDay = wrappedDay(renderDay + targetStep)
        offset += width * targetStep
        targetStep = 0
    }
}

/** Native drag negotiation delays child press ink until the touch's intent is known. */
@Composable
internal fun rememberTodaySwipe(day: Int, selectDay: (Int) -> Unit, motion: Boolean): TodaySwipe {
    val currentDay by rememberUpdatedState(day)
    val onSelect by rememberUpdatedState(selectDay)
    val moves by rememberUpdatedState(motion)
    val threshold = with(LocalDensity.current) { 56.dp.toPx() }
    val interactions = remember { MutableInteractionSource() }
    val state = remember { SwipeMotion(day) }

    LaunchedEffect(day, motion) {
        // A link elsewhere on Today can select a day without using the rail.
        if (!motion || day != wrappedDay(state.renderDay + state.targetStep)) state.reset(day)
    }
    LaunchedEffect(interactions, threshold) {
        interactions.interactions.collect { event ->
            when (event) {
                is DragInteraction.Start -> state.settling?.cancel()
                is DragInteraction.Stop, is DragInteraction.Cancel -> {
                    val accepted = event is DragInteraction.Stop &&
                        abs(state.horizontal) >= threshold && abs(state.horizontal) > abs(state.vertical)
                    state.settling?.cancel()
                    val direction = if (accepted) -state.horizontal.sign.toInt() else 0
                    state.targetStep = direction
                    if (accepted) onSelect(wrappedDay(state.renderDay + direction))
                    if (moves) {
                        val from = state.offset
                        val destination = -direction * state.width
                        state.settling = launch {
                            animate(0f, 1f, animationSpec = spring(dampingRatio = 0.9f, stiffness = 400f)) { value, _ ->
                                val fraction = value.coerceIn(0f, 1f)
                                state.offset = from + (destination - from) * fraction
                            }
                            // The outgoing page is now offscreen. Rebase without moving the incoming page.
                            state.rebase()
                            state.offset = 0f
                        }
                    } else state.reset(wrappedDay(state.renderDay + direction))
                }
            }
        }
    }

    val gestures = Modifier
        .onSizeChanged { state.width = it.width.toFloat().coerceAtLeast(1f) }
        .pointerInput(Unit) {
            // Observe direction without consuming: vertical scrolling keeps its native negotiation.
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                state.horizontal = 0f
                state.vertical = 0f
                do {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                    state.vertical = change.position.y - down.position.y
                } while (change.pressed)
            }
        }
        .draggable(
            state = rememberDraggableState { delta ->
                state.horizontal += delta
                state.offset = if (moves) (state.startOffset + state.horizontal).coerceIn(-state.width, state.width) else 0f
            },
            orientation = Orientation.Horizontal,
            interactionSource = interactions,
            startDragImmediately = false,
            onDragStarted = {
                state.settling?.cancel()
                state.rebase()
                state.startOffset = state.offset
            },
        )
    return TodaySwipe(
        gestures = gestures,
        content = { page -> Modifier.graphicsLayer { translationX = if (moves) state.offset + page * state.width else 0f } },
        renderDay = { state.renderDay },
        activePage = { state.targetStep },
        dayOffset = { if (moves) state.renderDay - currentDay - state.offset / state.width else 0f },
        selectDay = { state.reset(it); onSelect(it) },
    )
}
