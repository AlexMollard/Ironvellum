package com.ironvellum.app.ui.program

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.ironvellum.app.domain.Muscle
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Where the front and back figures sit in a canvas of [width] x [height] pixels: two equal slots side by
 * side, each figure as tall as the canvas unless its hands would then cross into the neighbouring slot,
 * in which case it shrinks to fit. The ONE copy of this transform, so what is drawn and what a tap lands
 * on cannot drift apart.
 */
internal class FigureGeometry(val figure: BodyFigure, val width: Float, val height: Float) {
    val slot: Float = width / 2f
    val scale: Float = minOf(height, slot * 0.96f / (2f * figure.halfWidth))
    val top: Float = (height - scale) / 2f

    fun centerX(view: FigureView): Float = if (view == FigureView.FRONT) slot * 0.5f else slot * 1.5f

    /** The view whose slot holds the canvas x. */
    fun viewAt(x: Float): FigureView = if (x < slot) FigureView.FRONT else FigureView.BACK

    /** A figure-space point, on the right half ([side] 1) or its mirror (-1), in canvas pixels. */
    fun toCanvas(view: FigureView, x: Float, y: Float, side: Float = 1f): Offset =
        Offset(centerX(view) + x * side * scale, top + y * scale)
}

/** Hit slop in pixels: how far a tap may miss and still count. */
internal class HitSlop(
    /** A tap in the gap between regions goes to the nearest region within this. */
    val gap: Float,
    /** A tap this near a tiny region goes to it, even from inside a larger one. */
    val tiny: Float,
    /** A region whose smaller side is under this is tiny (serratus slips, rotator cuff, neck). */
    val tinyBox: Float,
) {
    companion object {
        fun of(density: Density): HitSlop = with(density) { HitSlop(4.dp.toPx(), 6.dp.toPx(), 14.dp.toPx()) }
    }
}

/**
 * The muscle under the canvas point ([x], [y]), or null for empty space. Regions are tested in reverse
 * draw order, because the import lays inlays (rhomboids, brachialis, hip flexors) over their parents and
 * the tap should land on what is visible. Both sides of the body are one region (the drawn polygon is
 * the right half, mirrored), so the point is tested against it and its mirror.
 */
internal fun hitMuscle(g: FigureGeometry, x: Float, y: Float, slop: HitSlop): Muscle? {
    val view = g.viewAt(x)
    val fx = (x - g.centerX(view)) / g.scale
    val fy = (y - g.top) / g.scale
    val candidates = g.figure.regions(view).asReversed().map { region ->
        val d = minOf(
            distanceToPolygon(region.points, fx, fy),
            distanceToPolygon(region.points, -fx, fy),
        ) * g.scale
        val width = region.points.maxOf { it.first } - region.points.minOf { it.first }
        val height = region.points.maxOf { it.second } - region.points.minOf { it.second }
        Candidate(region.muscle, d, minOf(width, height) * g.scale < slop.tinyBox)
    }
    val contained = candidates.firstOrNull { it.distance == 0f }
    val tinyNear = candidates.filter { it.tiny && it.distance <= slop.tiny }.minByOrNull { it.distance }
    return when {
        contained != null && contained.tiny -> contained.muscle
        tinyNear != null -> tinyNear.muscle
        contained != null -> contained.muscle
        else -> candidates.filter { it.distance <= slop.gap }.minByOrNull { it.distance }?.muscle
    }
}

private class Candidate(val muscle: Muscle, val distance: Float, val tiny: Boolean)

/** 0 when ([x], [y]) is inside [polygon] (even-odd), else the distance to its nearest edge. */
internal fun distanceToPolygon(polygon: List<Pair<Float, Float>>, x: Float, y: Float): Float {
    var inside = false
    var best = Float.MAX_VALUE
    var j = polygon.size - 1
    for (i in polygon.indices) {
        val (xi, yi) = polygon[i]
        val (xj, yj) = polygon[j]
        if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
        best = minOf(best, distanceToSegment(x, y, xi, yi, xj, yj))
        j = i
    }
    return if (inside) 0f else best
}

private fun distanceToSegment(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
    val dx = bx - ax
    val dy = by - ay
    val len2 = dx * dx + dy * dy
    val t = if (len2 == 0f) 0f else (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0f, 1f)
    return hypot(px - (ax + t * dx), py - (ay + t * dy))
}

/**
 * Where [muscle] sits on the canvas, for the accessibility node that stands for it: the right-hand
 * piece's bounds in the front view, or the back view when the front does not show it.
 */
internal fun muscleBounds(g: FigureGeometry, muscle: Muscle): Rect? {
    for (view in FigureView.entries) {
        val points = g.figure.regionsOf(muscle, view).flatMap { it.points }
        if (points.isEmpty()) continue
        val a = g.toCanvas(view, points.minOf { abs(it.first) }, points.minOf { it.second })
        val b = g.toCanvas(view, points.maxOf { abs(it.first) }, points.maxOf { it.second })
        return Rect(a.x, a.y, maxOf(b.x, a.x + 1f), maxOf(b.y, a.y + 1f))
    }
    return null
}

/** Tap on [tapped] while [current] is selected: the same muscle again, or empty space, clears. */
internal fun nextSelection(current: Muscle?, tapped: Muscle?): Muscle? =
    if (tapped == null || tapped == current) null else tapped
