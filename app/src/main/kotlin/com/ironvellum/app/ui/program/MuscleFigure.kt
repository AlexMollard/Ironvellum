package com.ironvellum.app.ui.program

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ironvellum.app.domain.Muscle
import com.ironvellum.app.ui.theme.IronvellumColors
import kotlin.math.roundToInt

/**
 * Front and back figures in one canvas of [height], each muscle filled by [fill], that light the muscle
 * under a tap. [selected] glows on both sides of the body and every piece of it; tapping it again, empty
 * space or anywhere else on the figure sends null through [onSelect]. Taps never leave the figure.
 *
 * A Canvas says nothing to TalkBack, so every drawn muscle is also an invisible node over its bounds that
 * speaks [lineFor] and its selected state, and selects on activation. Hair, head and hands are no region
 * and get no node.
 *
 * Not [interactive], it is only a picture: no taps, no nodes, so a tap falls through to whatever holds it.
 */
@Composable
internal fun TappableFigure(
    figure: BodyFigure,
    height: Dp,
    fill: (Muscle) -> Color,
    selected: Muscle?,
    onSelect: (Muscle?) -> Unit,
    lineFor: (Muscle) -> String,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
) {
    val slop = HitSlop.of(LocalDensity.current)
    // The tap handler outlives recompositions, so it reads the latest selection rather than the first.
    val current by rememberUpdatedState(selected)
    val select by rememberUpdatedState(onSelect)
    Box(modifier.fillMaxWidth().height(height)) {
        Canvas(
            Modifier
                .matchParentSize()
                .clearAndSetSemantics {}
                .then(
                    if (!interactive) Modifier
                    else Modifier.pointerInput(figure) {
                        detectTapGestures { at ->
                            val g = FigureGeometry(figure, size.width.toFloat(), size.height.toFloat())
                            select(nextSelection(current, hitMuscle(g, at.x, at.y, slop)))
                        }
                    },
                ),
        ) {
            val g = FigureGeometry(figure, size.width, size.height)
            drawFigure(g, FigureView.FRONT, fill, seed = 11, selected = selected)
            drawFigure(g, FigureView.BACK, fill, seed = 23, selected = selected)
        }
        if (interactive) MuscleNodes(figure, selected, onSelect, lineFor, Modifier.matchParentSize())
    }
}

/** The accessibility nodes of [TappableFigure]: one empty box per drawn muscle, placed over its bounds. */
@Composable
private fun MuscleNodes(
    figure: BodyFigure,
    selected: Muscle?,
    onSelect: (Muscle?) -> Unit,
    lineFor: (Muscle) -> String,
    modifier: Modifier,
) {
    val muscles = Muscle.entries.filter { it in figure.drawn }
    Layout(
        content = {
            muscles.forEach { muscle ->
                Box(
                    Modifier.semantics(mergeDescendants = true) {
                        contentDescription = lineFor(muscle)
                        this.selected = muscle == selected
                        onClick(label = if (muscle == selected) "Clear ${muscle.label}" else "Select ${muscle.label}") {
                            onSelect(nextSelection(selected, muscle))
                            true
                        }
                    },
                )
            }
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val g = FigureGeometry(figure, constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        // A small muscle (the neck, a forearm) draws under the 24dp WCAG floor, so its node grows about its
        // centre to the floor, kept inside the figure. Touch is the canvas's hit test; these only steer TalkBack.
        val floor = MIN_NODE.roundToPx()
        val placed = measurables.mapIndexed { i, measurable ->
            val bounds = muscleBounds(g, muscles[i])
            val w = maxOf((bounds?.width ?: 0f).roundToInt(), floor).coerceAtMost(constraints.maxWidth)
            val h = maxOf((bounds?.height ?: 0f).roundToInt(), floor).coerceAtMost(constraints.maxHeight)
            val x = ((bounds?.center?.x ?: 0f) - w / 2f).roundToInt().coerceIn(0, constraints.maxWidth - w)
            val y = ((bounds?.center?.y ?: 0f) - h / 2f).roundToInt().coerceIn(0, constraints.maxHeight - h)
            measurable.measure(Constraints.fixed(w, h)) to IntOffset(x, y)
        }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placed.forEach { (placeable, at) -> placeable.place(at) }
        }
    }
}

/**
 * The one line under a figure: the lit muscle and a fact about it, shown only while a muscle is lit, and a
 * polite live region so a screen reader hears each change.
 */
@Composable
internal fun SelectedMuscleLine(
    selected: Muscle?,
    lineFor: (Muscle) -> String,
    modifier: Modifier = Modifier,
) {
    Text(
        selected?.let(lineFor).orEmpty(),
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        style = MaterialTheme.typography.bodyMedium,
        color = IronvellumColors.Ink,
        textAlign = TextAlign.Center,
        minLines = 1,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** The smallest a muscle's accessibility node may be: the WCAG 2.2 target floor. */
private val MIN_NODE = 24.dp
