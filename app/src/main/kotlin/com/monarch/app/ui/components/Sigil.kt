package com.monarch.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
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
    /** Slow drift for the relic that is actually setting the rate. */
    spin: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val spec = remember(name) { specOf(name) }
    // 48s per revolution: perceptible as life, never as spinning.
    val drift by rememberInfiniteTransition(label = "sigil").animateFloat(
        initialValue = 0f,
        targetValue = if (spin) 360f else 0f,
        animationSpec = infiniteRepeatable(tween(48_000, easing = LinearEasing)),
        label = "drift",
    )
    Canvas(modifier) {
        val radius = min(size.width, size.height) / 2f
        val centre = Offset(size.width / 2f, size.height / 2f)

        rotate(degrees = spec.rotation + drift, pivot = centre) {
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

/**
 * Procedural heraldic emblem for a crest frame.
 *
 * A crest plate carrying a single letter looked like a placeholder, and the ten
 * catalogue frames were distinguishable only by border colour. The emblem is
 * composed from the frame's id, so each crest has its own mark while staying in
 * the palette its treatment already defines.
 *
 * Four archetypes — crown, chevrons, orbit, rune grid — keep the set visually
 * varied rather than ten rotations of one shape.
 */
@Composable
fun CrestEmblem(
    seed: String,
    primary: Color,
    secondary: Color = primary,
    modifier: Modifier = Modifier,
) {
    val s = remember(seed) { seedOf(seed) }
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val unit = min(w, h)
        val cx = w / 2f
        val cy = h / 2f
        val line = unit * 0.055f
        // A small per-frame tilt plus bit-driven detail below: hashing the
        // archetype alone collided (three of ten frames drew the same crown).
        val tilt = ((s / 23) % 17 - 8).toFloat()

        rotate(degrees = tilt, pivot = Offset(cx, cy)) {
        when (s % 4) {
            // CROWN: points rising from a base bar — rank made literal.
            0 -> {
                val points = 3 + (s / 5) % 3
                val baseY = cy + unit * 0.26f
                val path = Path()
                path.moveTo(cx - unit * 0.30f, baseY)
                for (i in 0 until points) {
                    val x0 = cx - unit * 0.30f + unit * 0.60f * i / points
                    val x1 = cx - unit * 0.30f + unit * 0.60f * (i + 0.5f) / points
                    val x2 = cx - unit * 0.30f + unit * 0.60f * (i + 1f) / points
                    // Point height from seed bits, not alternation: the crown's
                    // silhouette becomes part of the frame's identity.
                    val rise = 0.14f + 0.06f * (((s shr (i * 2)) and 3))
                    path.lineTo(x1, cy - unit * rise)
                    path.lineTo(x2, baseY)
                    if (i == 0) path.moveTo(x0, baseY)
                }
                path.close()
                drawPath(path, color = primary.copy(alpha = 0.85f), style = Stroke(width = line))
                drawPath(path, color = primary.copy(alpha = 0.14f))
                drawLine(
                    color = secondary,
                    start = Offset(cx - unit * 0.32f, baseY),
                    end = Offset(cx + unit * 0.32f, baseY),
                    strokeWidth = line,
                )
            }
            // CHEVRONS: a stacked rank insignia.
            1 -> {
                val rows = 2 + (s / 7) % 3
                repeat(rows) { i ->
                    val y = cy - unit * 0.16f + unit * 0.17f * i
                    val span = unit * (0.30f - 0.04f * i)
                    val path = Path()
                    path.moveTo(cx - span, y + unit * 0.10f)
                    path.lineTo(cx, y - unit * 0.06f)
                    path.lineTo(cx + span, y + unit * 0.10f)
                    drawPath(
                        path,
                        color = (if (i == 0) secondary else primary).copy(alpha = 0.9f - 0.18f * i),
                        style = Stroke(width = line),
                    )
                }
            }
            // ORBIT: a core with satellites — the shadow army in miniature.
            2 -> {
                val pips = 4 + (s / 11) % 4
                val r = unit * 0.28f
                drawCircle(
                    color = primary.copy(alpha = 0.45f),
                    radius = r,
                    center = Offset(cx, cy),
                    style = Stroke(width = line * 0.7f),
                )
                repeat(pips) { i ->
                    val a = (2.0 * PI * i / pips + (s % 30) / 10.0).toFloat()
                    drawCircle(
                        color = secondary,
                        radius = unit * 0.045f,
                        center = Offset(cx + cos(a) * r, cy + sin(a) * r),
                    )
                }
                drawCircle(color = primary, radius = unit * 0.09f, center = Offset(cx, cy))
            }
            // RUNE GRID: a sealed glyph, the most abstract of the four.
            else -> {
                val bars = 2 + (s / 13) % 3
                val half = unit * 0.26f
                drawRect(
                    color = primary.copy(alpha = 0.5f),
                    topLeft = Offset(cx - half, cy - half),
                    size = androidx.compose.ui.geometry.Size(half * 2, half * 2),
                    style = Stroke(width = line * 0.8f),
                )
                repeat(bars) { i ->
                    val t2 = (i + 1f) / (bars + 1f)
                    drawLine(
                        color = secondary.copy(alpha = 0.85f),
                        start = Offset(cx - half, cy - half + half * 2 * t2),
                        end = Offset(cx + half * (if (i % 2 == 0) 0.4f else 1f), cy - half + half * 2 * t2),
                        strokeWidth = line,
                    )
                }
                drawLine(
                    color = primary,
                    start = Offset(cx, cy - half * 1.25f),
                    end = Offset(cx, cy + half * 1.25f),
                    strokeWidth = line * 0.8f,
                )
            }
        }
        }
    }
}
