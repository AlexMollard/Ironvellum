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
    fun head(x: Float, y: Float, r: Float = 2.1f) = inkDot(p(x, y), r * u, color, seed = family.ordinal)
    fun ring(x: Float, y: Float, r: Float) =
        inkArc(p(x, y), r * u, 0f, 360f, color, pen.width, seed = family.ordinal, taperEnds = false)

    when (family) {
        // plank on a floor, arm straight down
        GlyphFamily.PUSH -> {
            ln(4.5f, 19f, 18f, 12f); ln(16.5f, 13f, 16.5f, 21f); head(20.5f, 9.4f)
        }
        // pressing up between parallel bars
        GlyphFamily.DIP -> {
            head(12f, 4.6f); ln(12f, 8f, 12f, 16f); ln(11.5f, 9.5f, 7.5f, 12.8f); ln(12.5f, 9.5f, 16.5f, 12.8f)
            ln(4.5f, 13.2f, 10f, 13.2f); ln(14f, 13.2f, 19.5f, 13.2f)
            ln(7.5f, 13.2f, 7.5f, 21f); ln(16.5f, 13.2f, 16.5f, 21f)
        }
        // chin over the bar, elbows bent
        GlyphFamily.PULL -> {
            ln(3f, 4f, 21f, 4f); ln(6.5f, 4f, 4.5f, 9.5f); ln(4.5f, 9.5f, 9.5f, 12f); ln(17.5f, 4f, 19.5f, 9.5f)
            ln(19.5f, 9.5f, 14.5f, 12f); head(12f, 8.4f, 2f); ln(12f, 12.5f, 12f, 21.5f)
        }
        // arms long and straight, head low
        GlyphFamily.HANG -> {
            ln(3f, 3f, 21f, 3f); ln(9f, 3f, 10.5f, 10.5f); ln(15f, 3f, 13.5f, 10.5f)
            head(12f, 13.2f, 2.1f); ln(12f, 15.6f, 12f, 22f)
        }
        // upside down on the hands
        GlyphFamily.INVERSION -> {
            ln(6f, 21.5f, 18f, 21.5f); ln(12f, 21.5f, 12f, 12f); ln(12f, 12f, 12f, 4.5f)
            ln(12f, 4.5f, 9f, 1.8f); ln(12f, 4.5f, 15f, 1.8f); head(15.6f, 16.5f, 1.9f)
        }
        // body level, held on straight arms
        GlyphFamily.HOLD -> {
            ln(3f, 21f, 12f, 21f); ln(8.5f, 21f, 8.5f, 13f); ln(6.5f, 13f, 22f, 13f); head(3.8f, 11.4f, 2f)
        }
        // an L: torso up, legs out, hands planted
        GlyphFamily.CORE -> {
            head(8f, 4.5f); ln(8f, 7.8f, 8f, 15.5f); ln(8f, 15.5f, 21.5f, 15.5f)
            ln(5.5f, 10f, 5.5f, 21f); ln(2.5f, 21f, 9f, 21f)
        }
        // seated deep, arms forward
        GlyphFamily.SQUAT -> {
            head(9f, 4.5f); ln(9.5f, 7.5f, 8f, 14f); ln(8f, 14f, 14.5f, 14.5f)
            ln(14.5f, 14.5f, 13f, 21f); ln(13f, 21f, 18f, 21f); ln(10f, 9.5f, 17.5f, 10f)
        }
        // folded at the hip over a loaded bar
        GlyphFamily.HINGE -> {
            head(5.5f, 7.2f); ln(8f, 8.6f, 15.5f, 12f); ln(15.5f, 12f, 14.5f, 21f)
            ln(14.5f, 21f, 18.5f, 21f); ln(10f, 10f, 10f, 17.5f); ln(4.5f, 17.5f, 15.5f, 17.5f)
            ln(4.5f, 14.8f, 4.5f, 20.2f); ln(15.5f, 14.8f, 15.5f, 20.2f)
        }
        // a back-bend arch
        GlyphFamily.MOBILITY -> {
            val arch = Path().apply {
                moveTo(5f * u, 19f * u)
                cubicTo(5.5f * u, 1.5f * u, 18.5f * u, 1.5f * u, 19f * u, 19f * u)
            }
            drawPath(arch, color, style = pen)
            ln(2.5f, 20f, 7.5f, 20f); ln(16.5f, 20f, 21.5f, 20f)
        }
        // two rings on straps
        GlyphFamily.RINGS -> {
            ln(4f, 3f, 20f, 3f); ln(7f, 3f, 7f, 10.4f); ln(17f, 3f, 17f, 10.4f)
            ring(7f, 14.6f, 3.9f); ring(17f, 14.6f, 3.9f)
        }
    }
}
