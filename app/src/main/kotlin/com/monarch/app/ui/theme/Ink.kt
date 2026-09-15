package com.monarch.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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

/** How far an edge may wander from true, in dp. Past ~2dp it reads as broken, not hand-drawn. */
private const val EDGE_WOBBLE_DP = 1.4f

/** Segments per edge. Too few reads as a polygon; too many smooths back into a straight line. */
private const val SEGMENTS_PER_EDGE = 7

/**
 * A rectangle whose edges were drawn by hand rather than snapped to pixels.
 *
 * Replaces CutCornerShape for ink surfaces. The corners stay square-ish; it is
 * the slight drift ALONG each edge that sells the brush, so the wobble is
 * applied perpendicular to the edge direction.
 */
class InkEdgeShape(private val salt: Int = 0) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val wobble = with(density) { EDGE_WOBBLE_DP.dp.toPx() }
        // Seed from the size so a given panel is stable frame to frame, and two
        // differently-sized panels still get different edges.
        val rng = Random(size.width.roundToInt() * 31 + size.height.roundToInt() * 17 + salt)
        val path = Path()

        fun jitter() = (rng.nextFloat() - 0.5f) * 2f * wobble

        // Walk the four edges, emitting points that drift off the true line.
        val w = size.width
        val h = size.height
        path.moveTo(jitter(), jitter())
        for (i in 1..SEGMENTS_PER_EDGE) {
            val t = i.toFloat() / SEGMENTS_PER_EDGE
            path.lineTo(w * t, jitter())
        }
        for (i in 1..SEGMENTS_PER_EDGE) {
            val t = i.toFloat() / SEGMENTS_PER_EDGE
            path.lineTo(w + jitter(), h * t)
        }
        for (i in 1..SEGMENTS_PER_EDGE) {
            val t = i.toFloat() / SEGMENTS_PER_EDGE
            path.lineTo(w * (1f - t), h + jitter())
        }
        for (i in 1..SEGMENTS_PER_EDGE) {
            val t = i.toFloat() / SEGMENTS_PER_EDGE
            path.lineTo(jitter(), h * (1f - t))
        }
        path.close()
        return Outline.Generic(path)
    }

    override fun equals(other: Any?): Boolean = other is InkEdgeShape && other.salt == salt

    override fun hashCode(): Int = salt
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

/** One remembered ink shape per surface, so the wobble does not change as state updates. */
@Composable
fun rememberInkShape(salt: Int = 0): Shape = remember(salt) { InkEdgeShape(salt) }
