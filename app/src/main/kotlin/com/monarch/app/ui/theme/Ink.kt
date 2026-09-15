package com.monarch.app.ui.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
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
        val corner = maxOf(topStart, topEnd, bottomEnd, bottomStart)
        // Also bound the wander by the surface's own short side: the same 6px
        // that looks drawn on a tall panel eats a noticeable slice off a 56dp
        // button's edge, which is what made the wide buttons look torn.
        val shortSide = minOf(size.width, size.height)
        val wobble = minOf(corner * WOBBLE_PER_CORNER, shortSide * WOBBLE_PER_SHORT_SIDE)
            .coerceIn(0.5f, WOBBLE_MAX_PX)
        val pts = inkEdgePoints(size.width, size.height, wobble, salt)
        val path = Path()
        path.moveTo(pts[0], pts[1])
        var i = 2
        while (i < pts.size) {
            path.lineTo(pts[i], pts[i + 1])
            i += 2
        }
        path.close()
        return Outline.Generic(path)
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
    val pts = FloatArray(((across + down) * 2 + 1) * 2)
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
    for (i in 1..down) put(jitter(), height * (1f - i.toFloat() / down))
    return pts
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
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = (outline as? Outline.Generic)?.path ?: return@drawBehind
    drawPath(path, color.copy(alpha = color.alpha * 0.35f), style = Stroke(width.toPx() * 2.6f))
    drawPath(path, color, style = Stroke(width.toPx()))
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
    val length = if (vertical) size.height else size.width
    val across = (if (vertical) size.width else size.height) / 2f
    val rng = Random(seed + length.roundToInt())
    val segments = (length / 30f).toInt().coerceIn(4, 32)
    val base = thickness.toPx()
    for (i in 0 until segments) {
        val t0 = i.toFloat() / segments
        val a = length * t0
        val b = length * (i + 1).toFloat() / segments
        // Ends lift; the middle carries the ink.
        val ends = minOf(t0, 1f - t0) / 0.5f
        val weight = (0.45f + rng.nextFloat() * 0.55f) * (0.35f + 0.65f * ends)
        val drift1 = (rng.nextFloat() - 0.5f) * base * 0.6f
        val drift2 = (rng.nextFloat() - 0.5f) * base * 0.6f
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
