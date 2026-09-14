package com.monarch.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.monarch.app.ui.theme.MonarchColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Procedurally generated relic art.
 *
 * Relic multipliers are CONTINUOUS, so a fixed set of drawn assets could never
 * cover them — every relic would have to share a picture. Instead the art is
 * composed from the relic's own name: the same relic always draws the same
 * sigil, a new relic gets a new one, and nothing has to ship as an asset.
 *
 * Deliberately geometric rather than pictorial: hand-drawn icon art was tried
 * in this project and read badly, whereas the seeded crest plates did not. This
 * is the crest language — layered palette geometry — applied to relics.
 */
private data class SigilSpec(
    val points: Int,
    val rotation: Float,
    val innerScale: Float,
    val rings: Int,
    val spokes: Int,
    val skew: Float,
)

/** Stable hash so a name always yields the same sigil across launches. */
private fun seedOf(name: String): Int = name.fold(0) { acc, c -> acc * 31 + c.code } and Int.MAX_VALUE

private fun specOf(name: String): SigilSpec {
    val seed = seedOf(name)
    return SigilSpec(
        // 3..8 points: a triangle reads as sharp, an octagon as ornate.
        points = 3 + (seed % 6),
        rotation = ((seed / 7) % 360).toFloat(),
        // 0.38..0.72 — how deep the star's valleys cut toward the centre.
        innerScale = 0.38f + ((seed / 11) % 35) / 100f,
        rings = 1 + ((seed / 13) % 3),
        spokes = 4 + ((seed / 17) % 5),
        skew = (((seed / 19) % 21) - 10) / 40f,
    )
}

/**
 * Draws the sigil for [name] in [accent]. Size comes from the caller's
 * modifier, so the same art serves a 40.dp vault row and a hero reveal.
 */
@Composable
fun RelicSigil(
    name: String,
    accent: Color = MonarchColors.EmeraldBright,
    modifier: Modifier = Modifier,
) {
    val spec = remember(name) { specOf(name) }
    Canvas(modifier) {
        val radius = min(size.width, size.height) / 2f
        val centre = Offset(size.width / 2f, size.height / 2f)

        rotate(degrees = spec.rotation, pivot = centre) {
            // Concentric rings: the relic's "setting".
            repeat(spec.rings) { ring ->
                val r = radius * (0.92f - ring * 0.16f)
                drawCircle(
                    color = accent.copy(alpha = 0.22f + 0.12f * ring),
                    radius = r,
                    center = centre,
                    style = Stroke(width = radius * 0.05f),
                )
            }

            // Spokes radiating to the rim, skewed so the figure is never
            // perfectly symmetrical — asymmetry is what makes each read distinct.
            repeat(spec.spokes) { i ->
                val angle = (2.0 * PI * i / spec.spokes + spec.skew).toFloat()
                drawLine(
                    color = accent.copy(alpha = 0.35f),
                    start = centre,
                    end = Offset(
                        centre.x + cos(angle) * radius * 0.86f,
                        centre.y + sin(angle) * radius * 0.86f,
                    ),
                    strokeWidth = radius * 0.04f,
                )
            }

            // The star itself: alternating outer and inner vertices.
            val path = Path()
            val vertices = spec.points * 2
            for (i in 0 until vertices) {
                val angle = (2.0 * PI * i / vertices - PI / 2).toFloat()
                val r = if (i % 2 == 0) radius * 0.78f else radius * 0.78f * spec.innerScale
                val x = centre.x + cos(angle) * r
                val y = centre.y + sin(angle) * r
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            drawPath(path, color = accent.copy(alpha = 0.85f), style = Stroke(width = radius * 0.07f))
            drawPath(path, color = accent.copy(alpha = 0.16f))

            // Core: the brightest point, scaled by how deep the star cuts.
            drawCircle(
                color = accent,
                radius = radius * 0.10f * (1f + spec.innerScale),
                center = centre,
            )
        }
    }
}
