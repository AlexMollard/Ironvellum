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

/** The stationary gesture viewport, its moving content, and the week rail's visual preview. */
internal class TodaySwipe(
    val gestures: Modifier,
    val content: Modifier,
    val dayOffset: () -> Float,
    val selectDay: (Int) -> Unit,
)

private class SwipeMotion {
    var horizontal = 0f
    var vertical = 0f
    var width = 1f
    var startOffset = 0f
    var startDayOffset = 0f
    var offset by mutableFloatStateOf(0f)
    var dayOffset by mutableFloatStateOf(0f)
    var settling: Job? = null

    fun reset() {
        settling?.cancel()
        offset = 0f
        dayOffset = 0f
    }
}

/** Native drag negotiation delays child press ink until the touch's intent is known. */
@Composable
internal fun rememberTodaySwipe(day: Int, selectDay: (Int) -> Unit, motion: Boolean): TodaySwipe {
    val currentDay by rememberUpdatedState(day)
    val onSelect by rememberUpdatedState(selectDay)
    val moves by rememberUpdatedState(motion)
    val density = LocalDensity.current
    val threshold = with(density) { 56.dp.toPx() }
    val travel = with(density) { 64.dp.toPx() }
    val interactions = remember { MutableInteractionSource() }
    val state = remember { SwipeMotion() }

    LaunchedEffect(interactions, threshold, travel) {
        interactions.interactions.collect { event ->
            when (event) {
                is DragInteraction.Start -> state.settling?.cancel()
                is DragInteraction.Stop, is DragInteraction.Cancel -> {
                    val accepted = event is DragInteraction.Stop &&
                        abs(state.horizontal) >= threshold && abs(state.horizontal) > abs(state.vertical)
                    state.settling?.cancel()
                    if (accepted) {
                        val direction = -state.horizontal.sign
                        onSelect(if (direction > 0f) currentDay % 7 + 1 else (currentDay + 5) % 7 + 1)
                        // Keep the underline at its dragged position as the selected day's origin changes.
                        state.dayOffset -= direction
                        state.offset = if (moves) direction * travel else 0f
                    }
                    if (moves) {
                        val fromContent = state.offset
                        val fromDay = state.dayOffset
                        state.settling = launch {
                            animate(0f, 1f, animationSpec = spring(dampingRatio = 0.9f, stiffness = 400f)) { value, _ ->
                                state.offset = fromContent * (1f - value)
                                state.dayOffset = fromDay * (1f - value)
                            }
                        }
                    } else state.reset()
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
                state.offset = if (moves) (state.startOffset + state.horizontal * 0.35f).coerceIn(-travel, travel) else 0f
                state.dayOffset = if (moves) (state.startDayOffset - state.horizontal / state.width).coerceIn(-1f, 1f) else 0f
            },
            orientation = Orientation.Horizontal,
            interactionSource = interactions,
            startDragImmediately = false,
            onDragStarted = {
                state.settling?.cancel()
                state.startOffset = state.offset
                state.startDayOffset = state.dayOffset
            },
        )
    return TodaySwipe(
        gestures = gestures,
        content = Modifier.graphicsLayer { translationX = if (moves) state.offset else 0f },
        dayOffset = { if (moves) state.dayOffset else 0f },
        selectDay = { state.reset(); onSelect(it) },
    )
}
