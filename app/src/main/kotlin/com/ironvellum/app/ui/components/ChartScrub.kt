package com.ironvellum.app.ui.components

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.inkDot
import com.ironvellum.app.ui.theme.inkStroke
import kotlin.math.abs

/*
 * Scrubbing: press and drag across a chart to read the value under the finger.
 *
 * The gesture is a horizontal-dominant drag (detectHorizontalDragGestures), not
 * a long-press. The drag detector only claims the pointer once the finger has
 * travelled the touch slop ALONG X, so a vertical swipe that starts on a chart
 * is never claimed here and the parent verticalScroll takes it as usual. A
 * long-press would also work but makes the reader wait before anything shows.
 * If the parent scroll does win the gesture, onDragCancel fires and the cursor
 * clears, so it can never stick.
 *
 * Everything that decides WHERE things go is a plain function below, so the
 * snap and placement maths are unit-tested without Compose.
 */

/** Which point is under the finger. [NONE] hides the cursor. */
@Stable
class ScrubState {
    var index by mutableIntStateOf(NONE)

    fun clear() {
        index = NONE
    }

    companion object {
        const val NONE = -1
    }
}

/** X of point [i] in a [count]-point trend chart: by date when [positions] is given, else evenly. */
internal fun trendX(i: Int, count: Int, positions: List<Double>?, width: Float): Float = when {
    positions != null -> (positions[i] * width).toFloat()
    count == 1 -> width / 2f
    else -> i * width / (count - 1)
}

/** X of the middle of bar slot [i] of [count]. */
internal fun barCenterX(i: Int, count: Int, width: Float): Float = width / count * (i + 0.5f)

/**
 * The index whose x is nearest [touchX], skipping null slots (gaps have no
 * point to land on). Ties go to the earlier point. Null when nothing is plotted.
 */
internal fun nearestIndex(xs: List<Float?>, touchX: Float): Int? {
    var best: Int? = null
    var bestDistance = Float.MAX_VALUE
    xs.forEachIndexed { i, x ->
        if (x == null) return@forEachIndexed
        val distance = abs(x - touchX)
        if (distance < bestDistance) {
            bestDistance = distance
            best = i
        }
    }
    return best
}

/** The bar slot [touchX] falls in, clamped so a finger past either edge still lands on the end bar. */
internal fun slotIndex(touchX: Float, width: Float, count: Int): Int {
    if (count <= 0 || width <= 0f) return 0
    return (touchX / (width / count)).toInt().coerceIn(0, count - 1)
}

/**
 * Left edge of the readout. It prefers the right of the cursor and flips to the
 * left when it would overflow; if it fits neither side it is pinned inside the
 * chart. A label wider than the chart pins to 0.
 */
internal fun readoutLeft(cursorX: Float, labelWidth: Float, chartWidth: Float, gap: Float): Float {
    val right = cursorX + gap
    val left = if (right + labelWidth <= chartWidth) right else cursorX - gap - labelWidth
    return left.coerceIn(0f, (chartWidth - labelWidth).coerceAtLeast(0f))
}

/**
 * Wire the drag to [state]. [snap] maps (finger x, chart width) to a point
 * index, or null for none; the haptic tick fires only when the snapped index
 * actually changes.
 */
@Composable
internal fun Modifier.scrubGesture(state: ScrubState, snap: (x: Float, width: Float) -> Int?): Modifier {
    val haptic = LocalHapticFeedback.current
    val currentSnap by rememberUpdatedState(snap)
    return pointerInput(state) {
        fun move(x: Float) {
            val index = currentSnap(x, size.width.toFloat()) ?: return
            if (index != state.index) {
                state.index = index
                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
            }
        }
        detectHorizontalDragGestures(
            onDragStart = { move(it.x) },
            onDragEnd = { state.clear() },
            onDragCancel = { state.clear() },
            onHorizontalDrag = { change, _ ->
                change.consume()
                move(change.position.x)
            },
        )
    }
}

/**
 * The cursor: an ink line through the snapped point, a dot on it (when it has a
 * height) and the readout box, which stays inside the chart and flips sides
 * near the edges.
 */
internal fun DrawScope.drawScrub(
    measurer: TextMeasurer,
    style: TextStyle,
    text: String,
    cursorX: Float,
    dotY: Float?,
    seed: Int,
) {
    val ink = IronvellumColors.Ink
    inkStroke(Offset(cursorX, 0f), Offset(cursorX, size.height), ink.copy(alpha = 0.7f), 1.6f, seed = seed, taperEnds = false)
    if (dotY != null) inkDot(Offset(cursorX, dotY), 6.5f, ink, seed = seed)

    val label = measurer.measure(text, style)
    val padX = 6.dp.toPx()
    val padY = 4.dp.toPx()
    val boxW = label.size.width + 2 * padX
    val boxH = label.size.height + 2 * padY
    val left = readoutLeft(cursorX, boxW, size.width, gap = 8.dp.toPx())
    val top = 2.dp.toPx().coerceAtMost((size.height - boxH).coerceAtLeast(0f))
    val box = Path().apply {
        moveTo(left, top)
        lineTo(left + boxW, top)
        lineTo(left + boxW, top + boxH)
        lineTo(left, top + boxH)
        close()
    }
    drawPath(box, IronvellumColors.Vault)
    drawPath(box, ink.copy(alpha = 0.8f), style = Stroke(width = 1.dp.toPx()))
    drawText(label, color = Color.Unspecified, topLeft = Offset(left + padX, top + padY))
}
