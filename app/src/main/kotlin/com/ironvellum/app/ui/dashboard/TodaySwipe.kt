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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/** Native drag negotiation delays child press ink until the touch's intent is known. */
@Composable
internal fun rememberTodaySwipe(day: Int, selectDay: (Int) -> Unit, motion: Boolean): Modifier {
    val currentDay by rememberUpdatedState(day)
    val onSelect by rememberUpdatedState(selectDay)
    val moves by rememberUpdatedState(motion)
    val density = LocalDensity.current
    val threshold = with(density) { 56.dp.toPx() }
    val travel = with(density) { 64.dp.toPx() }
    val interactions = remember { MutableInteractionSource() }
    var horizontal by remember { mutableFloatStateOf(0f) }
    var vertical by remember { mutableFloatStateOf(0f) }
    var offset by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(interactions, threshold, travel) {
        var settling: Job? = null
        interactions.interactions.collect { event ->
            when (event) {
                is DragInteraction.Start -> settling?.cancel()
                is DragInteraction.Stop, is DragInteraction.Cancel -> {
                    val accepted = event is DragInteraction.Stop &&
                        abs(horizontal) >= threshold && abs(horizontal) > abs(vertical)
                    settling?.cancel()
                    if (accepted) {
                        onSelect(if (horizontal < 0f) currentDay % 7 + 1 else (currentDay + 5) % 7 + 1)
                        // The incoming day arrives from the opposite side; only one page is live.
                        offset = if (moves) -horizontal.sign * travel else 0f
                    }
                    if (moves) {
                        settling = launch {
                            animate(offset, 0f, animationSpec = spring(dampingRatio = 0.9f, stiffness = 400f)) { value, _ -> offset = value }
                        }
                    } else offset = 0f
                }
            }
        }
    }

    return Modifier
        .pointerInput(Unit) {
            // Observe direction without consuming: vertical scrolling keeps its native negotiation.
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                horizontal = 0f
                vertical = 0f
                do {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                    vertical = change.position.y - down.position.y
                } while (change.pressed)
            }
        }
        .draggable(
            state = rememberDraggableState { delta ->
                horizontal += delta
                offset = if (moves) (horizontal * 0.35f).coerceIn(-travel, travel) else 0f
            },
            orientation = Orientation.Horizontal,
            interactionSource = interactions,
            startDragImmediately = false,
        )
        .graphicsLayer {
            translationX = if (moves) offset else 0f
            alpha = if (moves) 1f - (abs(offset) / travel).coerceIn(0f, 1f) * 0.18f else 1f
        }
}
