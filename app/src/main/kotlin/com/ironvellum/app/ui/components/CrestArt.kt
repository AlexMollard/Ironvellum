package com.ironvellum.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import com.ironvellum.app.domain.Crests
import com.ironvellum.app.ui.theme.METAL_WASH
import com.ironvellum.app.ui.theme.Metal

/*
 * The drawn mark of every crest, as flat line art. The 28 marks are SVG paths in a 100 x 100 box
 * (tools/crest_art/marks.txt, baked into CrestMarks.kt by tools/crest_art/build.py): each is a stroke in the
 * crest's flat diagonal gradient with, at most, a 12% wash inside. Never a solid fill, bevel, highlight or
 * sheen. The colours are FIXED per crest (never the lifter's accent): a crest looks the same on the
 * owner's screen and on an ally's.
 */

/** How a crest is drawn: the ramp of its mark, of its rim, and whether the rim is doubled (the top crests). */
internal class CrestLook(val art: Metal, val ring: Metal, val double: Boolean)

/**
 * The look of crest [frameId], or null for an id the catalogue does not know (a crest from a newer build):
 * a caller draws nothing, or the plain avatar, rather than a stand-in. There is no fallback metal.
 */
internal fun crestLook(frameId: String): CrestLook? =
    Crests.byId(frameId)?.let { CrestLook(Metal.of(it.art), Metal.of(it.ring), it.double) }

/** Whether a mark is drawn for [frameId]; CrestArtCoverageTest holds the catalogue to it. */
internal fun hasCrestMark(frameId: String): Boolean = CREST_SHAPES.containsKey(frameId)

private val ParsedMarks: Map<String, List<Path>> by lazy {
    CREST_SHAPES.mapValues { (_, shapes) -> shapes.map { PathParser().parsePathString(it.d).toPath() } }
}

/** What a crest not yet held is drawn in: a flat muted ring and mark on a darker plate. */
internal val CrestSilhouette = Color(0xFF3A3935)
internal val CrestSilhouetteGround = Color(0xFF0F0F0E)
internal val CrestGround = Color(0xFF121211)

/**
 * Draws the mark of [frameId] in the square of [side] at [topLeft], its strokes and wash in [brush]. The
 * brush is in the mark's own 100-unit space (so a gradient runs corner to corner of the mark), and every
 * stroke width scales with the mark.
 */
internal fun DrawScope.drawCrestMark(frameId: String, topLeft: Offset, side: Float, brush: Brush) {
    val shapes = CREST_SHAPES[frameId] ?: return
    val paths = ParsedMarks[frameId] ?: return
    val k = side / 100f
    withTransform({
        translate(topLeft.x, topLeft.y)
        scale(k, k, Offset.Zero)
    }) {
        shapes.forEachIndexed { i, shape ->
            val path = paths[i]
            if (shape.wash > 0f) drawPath(path, brush, alpha = shape.wash, style = Fill)
            drawPath(
                path,
                brush,
                alpha = shape.strokeAlpha,
                style = Stroke(
                    width = shape.width,
                    cap = if (shape.roundCap) StrokeCap.Round else StrokeCap.Butt,
                    join = if (shape.roundJoin) StrokeJoin.Round else StrokeJoin.Miter,
                    pathEffect = shape.dash?.let { PathEffect.dashPathEffect(it) },
                ),
            )
        }
    }
}

/**
 * A crest medallion in the square of [side] at [topLeft]: the dark plate, the ring's 12% wash, the flat
 * gradient ring ([ringWidth] wide, doubled for the top crests) and the mark. [owned] false draws it as a
 * silhouette in flat muted grey; [drift] slides a prism along its diagonal (0 for none).
 */
internal fun DrawScope.drawCrestMedallion(
    frameId: String,
    look: CrestLook,
    topLeft: Offset,
    side: Float,
    ringWidth: Float,
    owned: Boolean = true,
    drift: Float = 0f,
) {
    val center = Offset(topLeft.x + side / 2f, topLeft.y + side / 2f)
    val rim = side * 0.48f
    val ringBrush: Brush = if (owned) look.ring.span(side, drift * side, topLeft) else SolidColor(CrestSilhouette)
    // Circles are paths here, not ruled draws: a medallion is exact geometry, not hand-inked line.
    fun disc(radius: Float) = Path().apply { addOval(Rect(center, radius)) }
    drawPath(disc(rim), SolidColor(if (owned) CrestGround else CrestSilhouetteGround))
    if (owned) drawPath(disc(rim), ringBrush, alpha = METAL_WASH, style = Fill)
    drawPath(disc(rim), ringBrush, style = Stroke(ringWidth))
    // The top crests' second ring is the SAME gradient as the first, never another colour.
    if (look.double && owned) {
        drawPath(disc(side * 0.40f), ringBrush, alpha = 0.55f, style = Stroke(ringWidth * 0.55f))
    }
    val box = side * (if (side < 80f * density) 0.68f else 0.62f)
    val markTop = Offset(center.x - box / 2f, center.y - box / 2f)
    val markBrush: Brush = if (owned) look.art.span(box, drift * box, markTop) else SolidColor(CrestSilhouette)
    drawCrestMark(frameId, markTop, box, markBrush)
}

/** Just the mark of [frameId] in its own ramp, sized by the caller. Draws nothing for an unknown id. */
@Composable
fun CrestMark(frameId: String, modifier: Modifier = Modifier) {
    val look = crestLook(frameId) ?: return
    Canvas(modifier) {
        val side = minOf(size.width, size.height)
        val top = Offset((size.width - side) / 2f, (size.height - side) / 2f)
        drawCrestMark(frameId, top, side, look.art.span(side, 0f, top))
    }
}
