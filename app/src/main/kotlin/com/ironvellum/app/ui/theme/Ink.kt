package com.ironvellum.app.ui.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.StrokeJoin
import kotlin.random.Random

/**
 * Ink surface treatment: the chrome half of the monochrome-ink house style.
 *
 * The artwork half is generated (see tools/art.py). This file is what makes the
 * SURROUNDING UI read as ink on paper rather than a sci-fi HUD, and it is
 * deliberately procedural so it needs no assets and no image-model quota.
 *
 * Determinism matters more than it looks: every wobble and grain speck is drawn
 * from a seeded Random, keyed on the panel's own size. An unseeded Random would
 * re-roll on every recomposition and the whole UI would visibly crawl.
 */

/**
 * Whether the hand-drawn treatment is on.
 *
 * Held as observable state rather than a CompositionLocal because the ink
 * primitives are DrawScope and Modifier functions called from draw lambdas,
 * where a local is not in scope. Reading state here means a flip redraws every
 * surface without restarting the activity.
 *
 * The flag is mirrored from the stored profile at startup; see IronvellumTheme.
 */
object InkStyle {
    var enabled by mutableStateOf(false)
}

/** Segments per edge. Too few reads as a polygon; too many smooths back into a straight line. */
private const val SEGMENTS_PER_EDGE = 7

/**
 * How much of the declared corner size becomes edge wander.
 *
 * Tying wobble to the corner size rather than a fixed dp does two things: the
 * amount of "hand" scales with the size class (a panel wanders more than a
 * chip), and it is density-correct for free, because CornerBasedShape hands the
 * corner down already resolved to pixels.
 */
private const val WOBBLE_PER_CORNER = 0.22f

/**
 * Fraction of the surface's SHORT side the wander may consume.
 *
 * A wide button is only ~168px tall at 3x, so an unbounded 6px wander takes a
 * visible bite out of it and the edge reads as torn rather than drawn.
 */
private const val WOBBLE_PER_SHORT_SIDE = 0.025f

/**
 * Floor in px, so a small element still shows a hand.
 *
 * ~0.9dp at 3x. Raised from 1.4px after a 890x46px section header still read
 * dead straight at 4x magnification: the short-side cap put it at 1.15px, and
 * the old floor barely moved it. Short, wide surfaces are the hardest case -
 * they get the least wander from the cap and show it over the longest edge.
 */
private const val WOBBLE_MIN_PX = 2.6f

/** Hard ceiling in px. Past this an edge stops reading as drawn and starts reading as broken. */
private const val WOBBLE_MAX_PX = 6f

/**
 * A rectangle whose edges were drawn by hand rather than snapped to pixels.
 *
 * Extends CornerBasedShape so it can be installed directly into the Material
 * `Shapes` set - Material requires that type, so a plain `Shape` cannot be a
 * theme shape, and every control would otherwise keep its HUD corner.
 *
 * The declared corner radii are deliberately NOT drawn as corners; they are
 * read as an intensity hint. An ink edge has no radius.
 */
class InkEdgeShape(
    private val salt: Int = 0,
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
    ): Outline {
        if (!InkStyle.enabled) {
            // Clean mode is the original cut-corner silhouette, not a wobble set
            // to zero: a plain rectangle would lose the app's old geometry.
            return Outline.Generic(
                cutCornerPath(size, topStart, topEnd, bottomEnd, bottomStart),
            )
        }
        val corner = maxOf(topStart, topEnd, bottomEnd, bottomStart)
        // Bound the wander by the surface's own short side: the 6px that looks
        // drawn on a tall panel eats a noticeable slice off a 56dp button, which
        // is what made the wide buttons read as torn.
        //
        // The floor matters as much as the ceiling. A 100x28px badge came out at
        // 0.7px of drift - measured at 1px peak-to-peak on device, which is
        // straight to the eye. WOBBLE_MIN_PX keeps small filled elements visibly
        // drawn; clips with no fill show nothing either way.
        val shortSide = minOf(size.width, size.height)
        val wobble = minOf(corner * WOBBLE_PER_CORNER, shortSide * WOBBLE_PER_SHORT_SIDE)
            .coerceIn(WOBBLE_MIN_PX, maxOf(WOBBLE_MIN_PX, WOBBLE_MAX_PX))
        val pts = inkEdgePoints(size.width, size.height, wobble, salt)
        return Outline.Generic(smoothClosedPath(pts))
    }

    override fun copy(
        topStart: CornerSize,
        topEnd: CornerSize,
        bottomEnd: CornerSize,
        bottomStart: CornerSize,
    ): CornerBasedShape = InkEdgeShape(salt, topStart, topEnd, bottomEnd, bottomStart)

    override fun equals(other: Any?): Boolean = other is InkEdgeShape && other.salt == salt

    override fun hashCode(): Int = salt
}

/** Target length of one hand-drawn facet, in px. Keeps the wander frequency constant. */
private const val FACET_PX = 55f

/** Facets per edge, clamped: below 3 an edge is a polygon, above 16 it smooths flat. */
private fun facetsFor(edge: Float): Int =
    (edge / FACET_PX).roundToInt().coerceIn(3, 16)

/**
 * The hand-drawn rectangle as flat x,y pairs.
 *
 * Facet COUNT scales with each edge's own length rather than being fixed. With
 * a fixed count, a full-width button spread 7 facets over ~1000px and the long
 * diagonals read as a torn ribbon instead of a drawn line, while a small chip
 * got the same 7 crammed into 80px. Constant facet length fixes both ends.
 *
 * Kept free of Compose and Android types on purpose: the one contract that
 * really matters here - identical input produces an identical edge - is
 * otherwise only observable by staring at a running phone. As a pure function
 * it is provable on the JVM, and a regression to an unseeded Random (which
 * would make every surface crawl during recomposition) fails a test instead of
 * shipping.
 */
fun inkEdgePoints(width: Float, height: Float, wobble: Float, salt: Int): FloatArray {
    val rng = Random(width.roundToInt() * 31 + height.roundToInt() * 17 + salt)
    val across = facetsFor(width)
    val down = facetsFor(height)
    // The ring is closed by the path, not by a repeated point. The final run up
    // the left edge must therefore STOP below the start corner: walking it to
    // i == down lands back on y == 0, which is where this ring began, and the
    // two coincident points ~1px apart (against a ~55px facet) collapsed into a
    // degenerate segment. smoothClosedPath turns each point into a quadratic
    // control point, so that pair pinched the curve into a visible kink - the
    // odd top-left corner every wobbly panel showed.
    val pts = FloatArray((across + down) * 2 * 2)
    var n = 0
    fun put(x: Float, y: Float) {
        pts[n++] = x
        pts[n++] = y
    }
    fun jitter() = (rng.nextFloat() - 0.5f) * 2f * wobble

    put(jitter(), jitter())
    for (i in 1..across) put(width * (i.toFloat() / across), jitter())
    for (i in 1..down) put(width + jitter(), height * (i.toFloat() / down))
    for (i in 1..across) put(width * (1f - i.toFloat() / across), height + jitter())
    for (i in 1 until down) put(jitter(), height * (1f - i.toFloat() / down))
    return pts
}

/**
 * Turns a ring of jittered points into a SMOOTH closed path.
 *
 * The points used to be joined with `lineTo`, which made every one of them a
 * sharp kink: at ~55px facets on a 450dpi screen that reads as a torn zig-zag
 * rather than a drawn line, and a stroked kink with a miter join throws a spike
 * — the bright notch that showed at panel corners on the phone.
 *
 * Each original point becomes a quadratic CONTROL point and the midpoints
 * become anchors, so the curve passes between the jittered points instead of
 * through them. The wander survives; the corners stop being corners.
 */
private fun smoothClosedPath(pts: FloatArray): Path {
    val n = pts.size / 2
    val path = Path()
    if (n < 3) return path
    fun x(i: Int) = pts[((i % n) + n) % n * 2]
    fun y(i: Int) = pts[((i % n) + n) % n * 2 + 1]
    var mx = (x(0) + x(1)) / 2f
    var my = (y(0) + y(1)) / 2f
    path.moveTo(mx, my)
    for (i in 1..n) {
        val nx = (x(i) + x(i + 1)) / 2f
        val ny = (y(i) + y(i + 1)) / 2f
        path.quadraticTo(x(i), y(i), nx, ny)
        mx = nx; my = ny
    }
    path.close()
    return path
}

/**
 * Builds one small tile of paper grain.
 *
 * Tiled through a shader rather than drawn per-pixel per-frame: a full-screen
 * speckle would be tens of thousands of draw calls every frame. One 96x96 tile
 * is built once and repeated by the GPU.
 */
private fun grainTile(seed: Int, density: Float): ImageBitmap {
    val side = 96
    val bitmap = ImageBitmap(side, side)
    val canvas = androidx.compose.ui.graphics.Canvas(bitmap)
    val rng = Random(seed)
    val paint = androidx.compose.ui.graphics.Paint()
    // Sparse, low-alpha specks: felt as texture, never seen as dots.
    val specks = (side * side * 0.06f).toInt()
    repeat(specks) {
        val x = rng.nextFloat() * side
        val y = rng.nextFloat() * side
        paint.color = Color.White.copy(alpha = 0.012f + rng.nextFloat() * 0.028f)
        canvas.drawCircle(Offset(x, y), 0.4f + rng.nextFloat() * 0.7f * density, paint)
    }
    return bitmap
}

/**
 * Lays paper grain over a surface.
 *
 * `drawWithCache` keeps the tile and its shader alive across recompositions;
 * rebuilding either per frame would allocate a bitmap on every draw.
 */
fun Modifier.paperGrain(seed: Int = 0): Modifier = this.drawWithCache {
    if (!InkStyle.enabled) return@drawWithCache onDrawBehind { }
    val tile = grainTile(seed, density)
    val brush = ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated))
    onDrawBehind { drawRect(brush) }
}

/**
 * Draws a tapered ink tick, the ink replacement for the HUD corner bracket.
 *
 * A brush stroke is heavy where it lands and light where it lifts, so this is
 * drawn as a few overlapping segments of decreasing width and alpha rather than
 * one uniform line.
 */
fun DrawScope.inkTick(
    from: Offset,
    to: Offset,
    color: Color,
    widthPx: Float,
) {
    if (!InkStyle.enabled) {
        drawLine(color, from, to, widthPx, StrokeCap.Round)
        return
    }
    val steps = 4
    repeat(steps) { i ->
        val t0 = i.toFloat() / steps
        val t1 = (i + 1).toFloat() / steps
        val a = Offset(from.x + (to.x - from.x) * t0, from.y + (to.y - from.y) * t0)
        val b = Offset(from.x + (to.x - from.x) * t1, from.y + (to.y - from.y) * t1)
        // Lift the brush along the stroke: thinner and fainter toward the tail.
        val fade = 1f - t0 * 0.75f
        drawLine(
            color = color.copy(alpha = color.alpha * fade),
            start = a,
            end = b,
            strokeWidth = widthPx * fade,
            cap = StrokeCap.Round,
        )
    }
}

/**
 * Outlines a surface with a brush edge instead of a hairline border.
 *
 * Two passes: a wide faint pass for the ink bleed, a narrow firm pass for the
 * stroke itself. That difference is what separates "drawn" from "stroked".
 */
fun Modifier.inkBorder(
    color: Color,
    shape: Shape,
    width: Dp = 1.5.dp,
): Modifier = this.drawBehind {
    // Every outline kind is handled on purpose. An earlier version bailed out
    // on anything that was not Outline.Generic, which meant a border on a
    // CircleShape or RoundedCornerShape silently drew NOTHING - a vanished
    // border with a green build and no warning.
    val path = when (val outline = shape.createOutline(size, layoutDirection, this)) {
        is Outline.Generic -> outline.path
        is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
        is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
    }
    // Round join and cap, always: a jittered outline has near-180-degree turns,
    // and the default MITER join turns those into spikes that shoot past the
    // surface — visible as a bright notch at a panel's corner on device.
    fun stroke(px: Float) = Stroke(px, cap = StrokeCap.Round, join = StrokeJoin.Round)
    if (!InkStyle.enabled) {
        drawPath(path, color, style = stroke(width.toPx()))
        return@drawBehind
    }
    drawPath(path, color.copy(alpha = color.alpha * 0.35f), style = stroke(width.toPx() * 2.6f))
    drawPath(path, color, style = stroke(width.toPx()))
}

/**
 * A progress rail drawn as a brush stroke rather than two nested rectangles.
 *
 * A thin rail cannot use the edge wobble - at 6dp tall a 2px wander would eat a
 * third of it - so the "hand" comes from the stroke instead: the width breathes
 * along the length and both ends taper, the way a loaded brush lands and lifts.
 *
 * Drawn in segments with a seeded Random so the breathing is stable per rail.
 */
fun DrawScope.inkRail(
    fraction: Float,
    track: Color,
    fill: Brush,
    seed: Int,
) {
    val h = size.height
    val mid = h / 2f
    if (!InkStyle.enabled) {
        drawRect(color = track, size = size)
        if (fraction > 0f) {
            drawRect(brush = fill, size = Size(size.width * fraction.coerceIn(0f, 1f), h))
        }
        return
    }
    val segments = (size.width / 24f).toInt().coerceIn(6, 40)
    val rng = Random(seed)

    // Track: faint, full width, breathing slightly so it reads as drawn.
    for (i in 0 until segments) {
        val x0 = size.width * i / segments
        val x1 = size.width * (i + 1) / segments
        val breathe = 0.78f + rng.nextFloat() * 0.34f
        drawLine(
            color = track,
            start = Offset(x0, mid),
            end = Offset(x1, mid),
            strokeWidth = h * breathe,
            cap = StrokeCap.Round,
        )
    }
    if (fraction <= 0f) return

    // Fill: same breathing, but taper the final tenth so progress ends in a
    // stroke lifting off rather than a guillotined rectangle.
    val end = size.width * fraction.coerceIn(0f, 1f)
    val fillSegments = (end / 20f).toInt().coerceAtLeast(2)
    val rngFill = Random(seed * 31 + 7)
    for (i in 0 until fillSegments) {
        val t0 = i.toFloat() / fillSegments
        val x0 = end * t0
        val x1 = end * (i + 1).toFloat() / fillSegments
        val taper = if (t0 > 0.9f) (1f - (t0 - 0.9f) / 0.1f).coerceAtLeast(0.35f) else 1f
        val breathe = (0.80f + rngFill.nextFloat() * 0.30f) * taper
        drawLine(
            brush = fill,
            start = Offset(x0, mid),
            end = Offset(x1, mid),
            strokeWidth = h * breathe,
            cap = StrokeCap.Round,
        )
    }
}

/**
 * A divider drawn as one brush stroke: uneven weight, tapered ends.
 *
 * Replaces 1dp filled boxes, which are the most obviously machine-made mark
 * left in a hand-drawn UI precisely because they are perfectly even.
 */
fun Modifier.inkHairline(
    color: Color,
    seed: Int = 0,
    thickness: Dp = 1.5.dp,
): Modifier = this.drawBehind {
    // Orientation from the box itself: the same stroke serves a row divider and
    // a vertical separator, so callers never pick an axis by hand.
    val vertical = size.height > size.width
    if (!InkStyle.enabled) {
        val t = thickness.toPx()
        if (vertical) drawRect(color, Offset((size.width - t) / 2f, 0f), Size(t, size.height))
        else drawRect(color, Offset(0f, (size.height - t) / 2f), Size(size.width, t))
        return@drawBehind
    }
    val length = if (vertical) size.height else size.width
    val across = (if (vertical) size.width else size.height) / 2f
    val rng = Random(seed + length.roundToInt())
    val segments = (length / 30f).toInt().coerceIn(4, 32)
    val base = thickness.toPx()
    // Shared joint offsets, so the rule reads as one stroke rather than a row
    // of disconnected dashes.
    val drifts = FloatArray(segments + 1) { (rng.nextFloat() - 0.5f) * base * 0.6f }
    for (i in 0 until segments) {
        val t0 = i.toFloat() / segments
        val a = length * t0
        val b = length * (i + 1).toFloat() / segments
        // Ends lift; the middle carries the ink.
        val ends = minOf(t0, 1f - t0) / 0.5f
        val weight = (0.45f + rng.nextFloat() * 0.55f) * (0.35f + 0.65f * ends)
        val drift1 = drifts[i]
        val drift2 = drifts[i + 1]
        drawLine(
            color = color.copy(alpha = color.alpha * (0.5f + 0.5f * weight)),
            start = if (vertical) Offset(across + drift1, a) else Offset(a, across + drift1),
            end = if (vertical) Offset(across + drift2, b) else Offset(b, across + drift2),
            strokeWidth = base * weight,
            cap = StrokeCap.Round,
        )
    }
}

/** One remembered ink shape per surface, so the wobble does not change as state updates. */
@Composable
fun rememberInkShape(salt: Int = 0): Shape = remember(salt) { InkEdgeShape(salt) }

/**
 * A straight run drawn as a brush stroke: uneven weight, tapered ends, and a
 * touch of drift off true.
 *
 * For ruled lines inside canvases - chart grids, skill-tree connectors, band
 * markers - the last perfectly straight marks in the app. Unlike [inkHairline]
 * this takes explicit endpoints, so it works at any angle.
 */
fun DrawScope.inkStroke(
    from: Offset,
    to: Offset,
    color: Color,
    widthPx: Float,
    seed: Int = 0,
    taperEnds: Boolean = true,
) {
    if (!InkStyle.enabled) {
        drawLine(color, from, to, widthPx, StrokeCap.Round)
        return
    }
    val dx = to.x - from.x
    val dy = to.y - from.y
    val len = kotlin.math.sqrt(dx * dx + dy * dy)
    if (len <= 0.5f) return
    val segments = (len / 26f).toInt().coerceIn(3, 28)
    // Drift goes perpendicular to the run, so the stroke wanders across its own
    // direction instead of stretching along it.
    val nx = -dy / len
    val ny = dx / len
    val rng = Random(seed + len.roundToInt())
    val drift = (widthPx * 0.55f).coerceAtMost(1.6f)
    // One offset per joint, shared by both segments meeting there: independent
    // endpoints leave a visible notch at every junction.
    val offs = FloatArray(segments + 1) { (rng.nextFloat() - 0.5f) * 2f * drift }
    for (i in 0 until segments) {
        val t0 = i.toFloat() / segments
        val t1 = (i + 1).toFloat() / segments
        val ends = if (taperEnds) (minOf(t0, 1f - t0) / 0.5f).coerceIn(0f, 1f) else 1f
        val weight = (0.5f + rng.nextFloat() * 0.5f) * (0.3f + 0.7f * ends)
        val o1 = offs[i]
        val o2 = offs[i + 1]
        drawLine(
            color = color.copy(alpha = color.alpha * (0.55f + 0.45f * weight)),
            start = Offset(from.x + dx * t0 + nx * o1, from.y + dy * t0 + ny * o1),
            end = Offset(from.x + dx * t1 + nx * o2, from.y + dy * t1 + ny * o2),
            strokeWidth = widthPx * (0.55f + 0.45f * weight),
            cap = StrokeCap.Round,
        )
    }
}

/**
 * An arc drawn as a brush sweep rather than a machined ring.
 *
 * A perfect circle is as obviously machine-made as a ruled line: constant
 * width, constant radius, no lift. This walks the sweep in short segments,
 * breathing the width and drifting the radius, and tapers both ends unless the
 * caller wants a hard stop.
 */
fun DrawScope.inkArc(
    center: Offset,
    radius: Float,
    startDeg: Float,
    sweepDeg: Float,
    color: Color,
    widthPx: Float,
    seed: Int = 0,
    taperEnds: Boolean = true,
) {
    if (sweepDeg == 0f || radius <= 0f) return
    if (!InkStyle.enabled) {
        drawArc(
            color = color,
            startAngle = startDeg,
            sweepAngle = sweepDeg,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = widthPx, cap = StrokeCap.Round),
        )
        return
    }
    val arcLen = (kotlin.math.PI / 180.0 * kotlin.math.abs(sweepDeg) * radius).toFloat()
    // Segments must be long RELATIVE TO THE STROKE, not a fixed 22px: at a
    // gauge's 21px width that made every segment a round-capped dot, and the
    // per-segment weight jitter then rendered the sweep as a chain of beads.
    val segLen = maxOf(22f, widthPx * 2.2f)
    // Floor of 2, not 4: a short sweep (a gauge at 25%) was being forced back
    // into stubby segments by a minimum count, which is what beaded it while
    // the long track beside it stayed smooth.
    val segments = (arcLen / segLen).roundToInt().coerceIn(2, 64)
    val rng = Random(seed + radius.roundToInt())
    // A thick stroke shows width jitter far more than a hairline does, so the
    // amplitude shrinks as the brush gets fatter.
    val jitter = (10f / widthPx).coerceIn(0.4f, 1f)
    val drift = (widthPx * 0.22f).coerceAtMost(2.2f) * jitter

    fun pointAt(deg: Float, r: Float): Offset {
        val rad = (deg * kotlin.math.PI / 180.0).toFloat()
        return Offset(center.x + kotlin.math.cos(rad) * r, center.y + kotlin.math.sin(rad) * r)
    }

    // One radius per JOINT, shared by the two segments that meet there. Drawing
    // each segment with its own two radii left every joint mismatched by up to
    // 2x the drift - a ring of notches rather than one stroke - and on a closed
    // 360 sweep the wrap point showed it worst. The last joint of a full sweep
    // reuses the first, so the ring closes on itself exactly.
    val closed = kotlin.math.abs(sweepDeg) >= 359.9f
    val radii = FloatArray(segments + 1) { radius + (rng.nextFloat() - 0.5f) * 2f * drift }
    if (closed) radii[segments] = radii[0]

    // Weight per JOINT too, averaged across each segment. Rolling an
    // independent weight per segment stepped the width at every joint; with a
    // round cap that reads as a bead rather than a brush drag, which is exactly
    // how the rate dial rendered. Sharing the joint halves each step.
    val weights = FloatArray(segments + 1) { 1f - rng.nextFloat() * 0.38f * jitter }
    if (closed) weights[segments] = weights[0]

    for (i in 0 until segments) {
        val t0 = i.toFloat() / segments
        val t1 = (i + 1).toFloat() / segments
        val ends = if (taperEnds) (minOf(t0, 1f - t0) / 0.5f).coerceIn(0f, 1f) else 1f
        val weight = (weights[i] + weights[i + 1]) / 2f * (0.4f + 0.6f * ends)
        drawLine(
            color = color.copy(alpha = color.alpha * (0.6f + 0.4f * weight)),
            start = pointAt(startDeg + sweepDeg * t0, radii[i]),
            end = pointAt(startDeg + sweepDeg * t1, radii[i + 1]),
            strokeWidth = widthPx * (0.6f + 0.4f * weight),
            cap = StrokeCap.Round,
        )
    }
}

/**
 * The pre-ink silhouette: a rectangle with the top-start and bottom-end corners
 * cut, which is what every Ironvellum surface looked like before the brush pass.
 * Used when the hand-drawn style is switched off.
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

/** Wobble as a fraction of radius for a drawn circle. Past this it stops reading as round. */
private const val CIRCLE_WOBBLE = 0.035f

/** Vertices around a drawn circle. Enough to stay round, few enough to stay hand-made. */
private const val CIRCLE_STEPS = 28

/**
 * A circle drawn by hand rather than struck with a compass.
 *
 * A perfect circle is as machine-made as a ruled line, so avatars, dots and
 * calendar cells kept looking like the old design even after every edge was
 * brushed. The radius breathes around the sweep; the result is still round
 * enough to read as a circle at 16dp.
 */
class InkCircleShape(private val salt: Int = 0) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val r = minOf(size.width, size.height) / 2f
        val cx = size.width / 2f
        val cy = size.height / 2f
        if (!InkStyle.enabled || r <= 0f) {
            return Outline.Generic(
                Path().apply { addOval(Rect(cx - r, cy - r, cx + r, cy + r)) },
            )
        }
        // Seeded on the size, like every other ink shape, so the wobble cannot
        // crawl between recompositions.
        val rng = Random(size.width.roundToInt() * 13 + salt)
        val wob = r * CIRCLE_WOBBLE
        val ring = FloatArray(CIRCLE_STEPS * 2)
        for (i in 0 until CIRCLE_STEPS) {
            val a = (2.0 * kotlin.math.PI * i / CIRCLE_STEPS).toFloat()
            val rr = r + (rng.nextFloat() - 0.5f) * 2f * wob
            ring[i * 2] = cx + kotlin.math.cos(a) * rr
            ring[i * 2 + 1] = cy + kotlin.math.sin(a) * rr
        }
        return Outline.Generic(smoothClosedPath(ring))
    }

    override fun equals(other: Any?): Boolean = other is InkCircleShape && other.salt == salt

    override fun hashCode(): Int = salt
}

/**
 * A dot laid down with a brush: slightly off-round, slightly uneven in weight.
 * For the small drawn markers - tree nodes, calendar ticks - that drawCircle
 * renders as perfect discs.
 */
fun DrawScope.inkDot(center: Offset, radius: Float, color: Color, seed: Int = 0) {
    if (!InkStyle.enabled) {
        drawCircle(color, radius, center)
        return
    }
    val rng = Random(seed + radius.roundToInt())
    val steps = 14
    val path = Path()
    val ring = FloatArray(steps * 2)
    for (i in 0 until steps) {
        val a = (2.0 * kotlin.math.PI * i / steps).toFloat()
        val rr = radius * (1f + (rng.nextFloat() - 0.5f) * 0.18f)
        ring[i * 2] = center.x + kotlin.math.cos(a) * rr
        ring[i * 2 + 1] = center.y + kotlin.math.sin(a) * rr
    }
    drawPath(smoothClosedPath(ring), color)
}

/**
 * The crest plate: a rectangle with two deeply cut corners, drawn by hand.
 *
 * InkEdgeShape cannot serve here - it ignores corner radii by design, so it
 * would flatten the plate's silhouette into a wobbly rectangle. This keeps the
 * cut geometry and jitters the vertices along it, which is why the crest still
 * reads as a diamond-cut plate rather than a box.
 *
 * `cut` is the corner depth in px, matching what CutCornerShape was given.
 */
class InkPlateShape(private val cut: Float, private val salt: Int = 0) : Shape {
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
        if (!InkStyle.enabled) {
            corners.forEachIndexed { i, p -> if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
            path.close()
            return Outline.Generic(path)
        }
        // Seeded on the salt ALONE, not on the size. Concentric plates - a crest
        // and the ring around it - must share a jitter sequence, or each wobbles
        // independently and the pair reads as a double exposure. A constant salt
        // still means a stable outline, so nothing crawls.
        val rng = Random(salt)
        val wob = (minOf(w, h) * 0.02f).coerceIn(1.2f, 4f)
        fun j() = (rng.nextFloat() - 0.5f) * 2f * wob
        // Walk each edge in a few steps so the cut sides wander too, not just
        // the vertices.
        val steps = 3
        val walk = FloatArray(corners.size * steps * 2)
        var k = 0
        corners.forEachIndexed { i, from ->
            val to = corners[(i + 1) % corners.size]
            for (s in 0 until steps) {
                val tt = s.toFloat() / steps
                walk[k++] = from.x + (to.x - from.x) * tt + j()
                walk[k++] = from.y + (to.y - from.y) * tt + j()
            }
        }
        // Smoothed for the same reason as the panels: the plate sits behind the
        // level badge at 48dp, where a kinked outline is the most visible mark
        // on the screen.
        return Outline.Generic(smoothClosedPath(walk))
    }

    override fun equals(other: Any?): Boolean =
        other is InkPlateShape && other.cut == cut && other.salt == salt

    override fun hashCode(): Int = cut.hashCode() * 31 + salt
}
