package com.ironvellum.app.ui.titles

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.ironvellum.app.ui.theme.inkArc
import com.ironvellum.app.ui.theme.inkDot
import com.ironvellum.app.ui.theme.inkStroke

/** A family's pictogram in a single colour, stroked on a 24-unit grid. */
@Composable
internal fun SkillGlyph(family: GlyphFamily, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) { drawGlyph(family, color) }
}

private fun DrawScope.drawGlyph(family: GlyphFamily, color: Color) {
    val u = size.minDimension / 24f
    val pen = Stroke(width = 2.1f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun p(x: Float, y: Float) = Offset(x * u, y * u)
    var n = 0
    fun ln(x1: Float, y1: Float, x2: Float, y2: Float) =
        inkStroke(p(x1, y1), p(x2, y2), color, pen.width, seed = family.ordinal * 31 + n++, taperEnds = false)
    fun head(x: Float, y: Float) = inkDot(p(x, y), 2.2f * u, color, seed = family.ordinal)
    fun ring(x: Float, y: Float, r: Float) =
        inkArc(p(x, y), r * u, 0f, 360f, color, pen.width, seed = family.ordinal, taperEnds = false)

    // Every figure is a stick person drawn to one scale (head radius 2.2, pen 2.1) and
    // centred on the 24-unit grid by its own extent, so a row of them reads as one set.
    when (family) {
        // plank, arm straight down
        GlyphFamily.PUSH -> {
            ln(2.5f, 17.7f, 15.5f, 11.1f); ln(16f, 11.1f, 16f, 17.7f); head(19.3f, 8.5f)
        }
        // front view, straight arms pressing on two parallel bars
        GlyphFamily.DIP -> {
            head(12f, 4.4f); ln(12f, 8.4f, 12f, 16.6f); ln(12f, 8.6f, 7.6f, 13.4f); ln(12f, 8.6f, 16.4f, 13.4f)
            ln(3f, 13.4f, 9f, 13.4f); ln(15f, 13.4f, 21f, 13.4f); ln(4.4f, 13.4f, 4.4f, 21.8f); ln(19.6f, 13.4f, 19.6f, 21.8f)
            ln(12f, 16.6f, 9.8f, 21.8f); ln(12f, 16.6f, 14.2f, 21.8f)
        }
        // chin over the bar, elbows bent out
        GlyphFamily.PULL -> {
            ln(2.5f, 6.1f, 21.5f, 6.1f); head(12f, 3.1f)
            ln(4.8f, 6.1f, 4.6f, 12.3f); ln(4.6f, 12.3f, 9.8f, 8.9f)
            ln(19.2f, 6.1f, 19.4f, 12.3f); ln(19.4f, 12.3f, 14.2f, 8.9f)
            ln(12f, 8.5f, 12f, 18.3f); ln(12f, 18.3f, 9.6f, 23.1f); ln(12f, 18.3f, 14.4f, 23.1f)
        }
        // arms long and straight, head between them
        GlyphFamily.HANG -> {
            ln(2.5f, 1.8f, 21.5f, 1.8f); ln(6f, 1.8f, 9.6f, 10f); ln(18f, 1.8f, 14.4f, 10f); head(12f, 5.4f)
            ln(12f, 9.4f, 12f, 18f); ln(12f, 18f, 9.6f, 22.2f); ln(12f, 18f, 14.4f, 22.2f)
        }
        // upside down on the hands, legs split, head low
        GlyphFamily.INVERSION -> {
            ln(7.4f, 22f, 14.4f, 22f); ln(10.9f, 22f, 10.9f, 6.2f)
            ln(10.9f, 6.2f, 8.7f, 2f); ln(10.9f, 6.2f, 13.1f, 2f); head(14.3f, 16.8f)
        }
        // body level, held clear of the floor on straight arms
        GlyphFamily.HOLD -> {
            ln(2.6f, 18.3f, 13.1f, 18.3f); ln(9.6f, 18.3f, 7.4f, 10.3f); ln(7.4f, 10.3f, 22.1f, 10.3f); head(4.2f, 7.9f)
        }
        // an L: torso up, legs out, arm planted
        GlyphFamily.CORE -> {
            head(8.3f, 4.9f); ln(8.3f, 8.5f, 8.3f, 16.1f); ln(8.3f, 16.1f, 21.8f, 16.1f)
            ln(8.3f, 9.5f, 5f, 15.9f); ln(5f, 15.9f, 5f, 21.3f); ln(2.3f, 21.3f, 7.8f, 21.3f)
        }
        // seated deep, arms forward
        GlyphFamily.SQUAT -> {
            head(9f, 5f); ln(8.2f, 8.8f, 6.8f, 16.4f); ln(6.8f, 16.4f, 13.8f, 13.4f)
            ln(13.8f, 13.4f, 12.2f, 21.2f); ln(12.2f, 21.2f, 17.2f, 21.2f); ln(8.5f, 9.8f, 16f, 10.6f)
        }
        // folded at the hip over a loaded plate
        GlyphFamily.HINGE -> {
            head(5.5f, 5.7f); ln(8.5f, 7.3f, 17.3f, 9.7f); ln(8.5f, 7.3f, 8.5f, 13.7f); ring(8.5f, 16.5f, 3f)
            ln(17.3f, 9.7f, 15.3f, 14.7f); ln(15.3f, 14.7f, 16.5f, 19.7f); ln(16.5f, 19.7f, 20.7f, 19.7f)
        }
        // a back-bend: feet and hands down, head dropped back
        GlyphFamily.MOBILITY -> {
            val arch = Path().apply {
                moveTo(2.1f * u, 20.5f * u)
                cubicTo(1.6f * u, 3.5f * u, 15.6f * u, 3.5f * u, 17.1f * u, 14f * u)
            }
            drawPath(arch, color, style = pen)
            ln(17.1f, 14f, 17.6f, 20.5f); head(20.2f, 11.1f)
        }
        // arms out to two rings on straps: the cross
        GlyphFamily.RINGS -> {
            ln(2.5f, 1.5f, 21.5f, 1.5f); ln(4f, 1.5f, 4f, 7.9f); ln(20f, 1.5f, 20f, 7.9f)
            ring(4f, 10.1f, 2.2f); ring(20f, 10.1f, 2.2f); ln(6.4f, 10.1f, 17.6f, 10.1f)
            head(12f, 5.7f); ln(12f, 10.1f, 12f, 17.9f); ln(12f, 17.9f, 9.6f, 22.5f); ln(12f, 17.9f, 14.4f, 22.5f)
        }
    }
}
