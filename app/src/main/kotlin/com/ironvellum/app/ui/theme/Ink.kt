package com.ironvellum.app.ui.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Surface primitives: the cut-corner panels, borders, rails, rules, arcs and
 * dots every screen draws with.
 *
 * Every shape here is deterministic geometry keyed on its own size, so nothing
 * changes between recompositions. The names keep their "ink" prefix from the
 * retired hand-drawn look; renaming them is a separate decision.
 *
 * This file and Theme.kt are the only places allowed to name a raw geometric
 * shape (see InkCoverageTest). UI code takes its geometry from
 * `MaterialTheme.shapes` or the named shapes here: [DotShape] for dots and
 * rings, [TileShape] for small cells and markers.
 */

/** A true circle: dots, rung markers, node rings. */
val DotShape: Shape = CircleShape

/** A 2dp-rounded square: heat-map cells and small markers. */
val TileShape: Shape = RoundedCornerShape(2.dp)

/**
 * The app's cut-corner rectangle.
 *
 * Extends CornerBasedShape so it can be installed directly into the Material
 * `Shapes` set - Material requires that type, so a plain `Shape` cannot be a
 * theme shape, and every control would otherwise keep its stock corner.
 *
 */
class InkEdgeShape(
    topStart: CornerSize = CornerSize(8.dp),
    topEnd: CornerSize = CornerSize(8.dp),
    bottomEnd: CornerSize = CornerSize(8.dp),
    bottomStart: CornerSize = CornerSize(8.dp),
) : CornerBasedShape(topStart, topEnd, bottomEnd, bottomStart) {

    override fun createOutline(
        size: Size,
        topStart: Float,
        topEnd: Float,
        bottomEnd: Float,
        bottomStart: Float,
        layoutDirection: LayoutDirection,
    ): Outline = Outline.Generic(
        cutCornerPath(size, topStart, topEnd, bottomEnd, bottomStart),
    )

    override fun copy(
        topStart: CornerSize,
        topEnd: CornerSize,
        bottomEnd: CornerSize,
        bottomStart: CornerSize,
    ): CornerBasedShape = InkEdgeShape(topStart, topEnd, bottomEnd, bottomStart)

    override fun equals(other: Any?): Boolean = other is InkEdgeShape &&
        other.topStart == topStart && other.topEnd == topEnd &&
        other.bottomEnd == bottomEnd && other.bottomStart == bottomStart

    override fun hashCode(): Int =
        ((topStart.hashCode() * 31 + topEnd.hashCode()) * 31 + bottomEnd.hashCode()) * 31 + bottomStart.hashCode()
}

/**
 * The edge with no corner size: a plain rectangle. For Today's HUD line - the
 * level chip and the XP bar - where the theme's cut on a 20dp-tall bar read as
 * pointed ends.
 */
val HudEdgeShape = InkEdgeShape(CornerSize(0.dp), CornerSize(0.dp), CornerSize(0.dp), CornerSize(0.dp))

/** Draws a tick: a round-capped line, the replacement for the HUD corner bracket. */
fun DrawScope.inkTick(
    from: Offset,
    to: Offset,
    color: Color,
    widthPx: Float,
) {
    drawLine(color, from, to, widthPx, StrokeCap.Round)
}

/**
 * Outlines a surface with a constant-width border.
 *
 * Replaces Modifier.border, which traces the shape without handling every
 * outline kind.
 */
fun Modifier.inkBorder(
    color: Color,
    shape: Shape,
    width: Dp = 1.dp,
): Modifier = inkBorder(SolidColor(color), shape, width)

/**
 * [inkBorder] in a [Brush]: a tier's metal ([Metal.bounds]) runs across the whole box, corner to corner,
 * so a tile's edge is one flat diagonal gradient.
 */
fun Modifier.inkBorder(
    brush: Brush,
    shape: Shape,
    width: Dp = 1.dp,
): Modifier = if (width <= 0.dp) this else this.drawBehind {
    // A zero width means no border. Handed to Stroke it is a hairline instead,
    // which ringed every date on the training calendar in "today" gold.
    // Every outline kind is handled on purpose. An earlier version bailed out
    // on anything that was not Outline.Generic, which meant a border on a
    // CircleShape or RoundedCornerShape silently drew NOTHING - a vanished
    // border with a green build and no warning.
    fun outlinePath(inset: Float): Path {
        val box = Size((size.width - 2 * inset).coerceAtLeast(0f), (size.height - 2 * inset).coerceAtLeast(0f))
        val path = when (val outline = shape.createOutline(box, layoutDirection, this)) {
            is Outline.Generic -> outline.path
            is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
            is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
        }
        if (inset > 0f) path.translate(Offset(inset, inset))
        return path
    }
    // Inset by half the stroke so the whole line lies inside the surface.
    // Centred on the edge, a surface clipped to its shape (most cards)
    // lost the outer half: a 1dp border drew as a faint ~1px hairline with
    // broken anti-aliasing, while unclipped surfaces showed the full width,
    // so the same border looked different from screen to screen.
    val px = width.toPx()
    drawPath(
        outlinePath(px / 2f),
        brush,
        style = Stroke(px, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

/** A progress rail: a flat track with the fill drawn over it. */
fun DrawScope.inkRail(
    fraction: Float,
    track: Color,
    fill: Brush,
) {
    drawRect(color = track, size = size)
    if (fraction > 0f) {
        // Inset to the filled part, so a gradient fill runs across the fill and not the whole track.
        inset(0f, 0f, size.width * (1f - fraction.coerceIn(0f, 1f)), 0f) { drawRect(brush = fill) }
    }
}

/** A divider: a filled box of [thickness], centred on the box's short axis. */
fun Modifier.inkHairline(
    color: Color,
    thickness: Dp = 1.dp,
): Modifier = this.drawBehind {
    // Orientation from the box itself: the same rule serves a row divider and
    // a vertical separator, so callers never pick an axis by hand.
    val vertical = size.height > size.width
    val t = thickness.toPx()
    if (vertical) drawRect(color, Offset((size.width - t) / 2f, 0f), Size(t, size.height))
    else drawRect(color, Offset(0f, (size.height - t) / 2f), Size(size.width, t))
}

/** One remembered shape per surface. */
@Composable
fun rememberInkShape(): Shape = remember { InkEdgeShape() }

/**
 * A straight run, for ruled lines inside canvases - chart grids, skill-tree
 * connectors, band markers. Unlike [inkHairline] this takes explicit endpoints,
 * so it works at any angle.
 */
fun DrawScope.inkStroke(
    from: Offset,
    to: Offset,
    color: Color,
    widthPx: Float,
) = inkStroke(from, to, SolidColor(color), widthPx)

/** [inkStroke] in a [Brush]: a metal's flat diagonal gradient. */
fun DrawScope.inkStroke(
    from: Offset,
    to: Offset,
    brush: Brush,
    widthPx: Float,
) {
    drawLine(brush, from, to, widthPx, StrokeCap.Round)
}

/** A round-capped arc stroke. */
fun DrawScope.inkArc(
    center: Offset,
    radius: Float,
    startDeg: Float,
    sweepDeg: Float,
    color: Color,
    widthPx: Float,
) = inkArc(center, radius, startDeg, sweepDeg, SolidColor(color), widthPx)

/** [inkArc] in a [Brush]: a metal's flat diagonal gradient. */
fun DrawScope.inkArc(
    center: Offset,
    radius: Float,
    startDeg: Float,
    sweepDeg: Float,
    brush: Brush,
    widthPx: Float,
) {
    if (sweepDeg == 0f || radius <= 0f) return
    drawArc(
        brush = brush,
        startAngle = startDeg,
        sweepAngle = sweepDeg,
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2, radius * 2),
        style = Stroke(width = widthPx, cap = StrokeCap.Round),
    )
}

/**
 * A rectangle with the top-start and bottom-end corners cut, which is what
 * every Ironvellum surface looks like.
 */
private fun cutCornerPath(
    size: Size,
    topStart: Float,
    topEnd: Float,
    bottomEnd: Float,
    bottomStart: Float,
): Path = Path().apply {
    moveTo(topStart, 0f)
    lineTo(size.width - topEnd, 0f)
    if (topEnd > 0f) lineTo(size.width, topEnd)
    lineTo(size.width, size.height - bottomEnd)
    if (bottomEnd > 0f) lineTo(size.width - bottomEnd, size.height)
    lineTo(bottomStart, size.height)
    if (bottomStart > 0f) lineTo(0f, size.height - bottomStart)
    lineTo(0f, topStart)
    close()
}

/** A filled dot, for the small markers - tree nodes, calendar ticks. */
fun DrawScope.inkDot(center: Offset, radius: Float, color: Color) {
    drawCircle(color, radius, center)
}

/**
 * The crest plate: a rectangle with two deeply cut corners.
 *
 * InkEdgeShape cannot serve here - it takes its cuts from the theme's corner
 * radii. `cut` is the corner depth in px, matching what CutCornerShape was given.
 */
class InkPlateShape(private val cut: Float) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val c = cut.coerceAtMost(minOf(size.width, size.height) / 2f)
        val w = size.width
        val h = size.height
        val corners = listOf(
            Offset(c, 0f), Offset(w, 0f), Offset(w, h - c), Offset(w - c, h), Offset(0f, h), Offset(0f, c),
        )
        val path = Path()
        corners.forEachIndexed { i, p -> if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
        path.close()
        return Outline.Generic(path)
    }

    override fun equals(other: Any?): Boolean =
        other is InkPlateShape && other.cut == cut

    override fun hashCode(): Int = cut.hashCode()
}
