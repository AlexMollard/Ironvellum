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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
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
 * A crest plate carrying a single letter looked like a placeholder, and thin
 * outlines on a bare plate looked like a wireframe. This draws a badge with
 * mass: an engraved ray burst for texture, a halo behind the mark, a FILLED
 * gradient body with a bevel highlight, and rank pips at the base — all
 * composed from the frame's id so each crest carries its own figure.
 *
 * Four archetypes — crown, chevrons, orbit, rune seal — so the catalogue reads
 * as a set of different objects, not ten rotations of one shape.
 */
@Composable
fun CrestEmblem(
    seed: String,
    /**
     * Catalogue slot. The mark family is chosen by POSITION, not by hashing
     * the id: with four families over ten frames the hash put five of them on
     * a crown and the set read as repetitive.
     */
    variant: Int,
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
        // Small per-frame tilt: hashing the archetype alone collided, three of
        // ten frames drawing the same crown.
        val tilt = ((s / 23) % 17 - 8).toFloat()
        val body = Brush.verticalGradient(
            listOf(primary, primary.copy(alpha = 0.55f), secondary.copy(alpha = 0.75f)),
        )

        // 1. Engraved rays: faint radiating lines so the plate is never empty.
        val rays = 24
        repeat(rays) { i ->
            val a = (2.0 * PI * i / rays).toFloat()
            val inner = unit * 0.30f
            val outer = unit * (if (i % 2 == 0) 0.50f else 0.42f)
            drawLine(
                color = secondary.copy(alpha = 0.10f),
                start = Offset(cx + cos(a) * inner, cy + sin(a) * inner),
                end = Offset(cx + cos(a) * outer, cy + sin(a) * outer),
                strokeWidth = unit * 0.012f,
            )
        }
        // 2. Halo behind the mark: depth without brightening the whole plate.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(primary.copy(alpha = 0.20f), Color.Transparent),
                center = Offset(cx, cy),
                radius = unit * 0.40f,
            ),
            radius = unit * 0.40f,
            center = Offset(cx, cy),
        )

        // Each family has its own vertical mass: a crown rises high but carries
        // a base bar, a gate's arch reaches far above its footing. Centring the
        // CANVAS is not centring the INK, so measure the extent and shift by it.
        val slot = variant.mod(10)
        val crownPoints = 3 + (s / 5) % 3
        val crownRise = (0 until crownPoints).maxOf { 0.14f + 0.07f * (((s shr (it * 2)) and 3)) }
        val chevronRows = 2 + (s / 7) % 3
        val extent = when (slot) {
            0 -> -crownRise to 0.33f
            1 -> -0.25f to (-0.20f + 0.18f * (chevronRows - 1) + 0.20f)
            2 -> -0.355f to 0.355f
            3 -> -0.30f to 0.30f
            4 -> -0.30f to 0.30f
            5 -> -0.44f to 0.30f
            6 -> -0.22f to 0.28f
            7 -> -0.40f to 0.28f
            8 -> -0.28f to 0.28f
            else -> -0.36f to 0.30f
        }
        val inkShift = -((extent.first + extent.second) / 2f) * unit

        translate(top = inkShift) {
        rotate(degrees = tilt, pivot = Offset(cx, cy)) {
            when (slot) {
                // CROWN: a filled crown over a base bar — rank made literal.
                0 -> {
                    val points = 3 + (s / 5) % 3
                    val baseY = cy + unit * 0.24f
                    val left = cx - unit * 0.30f
                    val span = unit * 0.60f
                    val path = Path()
                    path.moveTo(left, baseY)
                    for (i in 0 until points) {
                        val x1 = left + span * (i + 0.5f) / points
                        val x2 = left + span * (i + 1f) / points
                        // Point height from seed bits, not alternation: the
                        // silhouette becomes part of the frame's identity.
                        val rise = 0.14f + 0.07f * (((s shr (i * 2)) and 3))
                        path.lineTo(x1, cy - unit * rise)
                        path.lineTo(x2, baseY)
                    }
                    path.close()
                    drawPath(path, brush = body)
                    drawPath(path, color = secondary, style = Stroke(width = line * 0.7f))
                    drawRect(
                        brush = body,
                        topLeft = Offset(left, baseY),
                        size = Size(span, unit * 0.09f),
                    )
                    // Bevel: a bright lip along the top of the base bar.
                    drawLine(
                        color = Color.White.copy(alpha = 0.28f),
                        start = Offset(left, baseY + line * 0.2f),
                        end = Offset(left + span, baseY + line * 0.2f),
                        strokeWidth = unit * 0.014f,
                    )
                }
                // CHEVRONS: filled rank insignia, stacked and bevelled.
                1 -> {
                    val rows = 2 + (s / 7) % 3
                    repeat(rows) { i ->
                        val y = cy - unit * 0.20f + unit * 0.18f * i
                        val span = unit * (0.32f - 0.05f * i)
                        val thick = unit * 0.10f
                        val path = Path()
                        path.moveTo(cx - span, y + unit * 0.10f)
                        path.lineTo(cx, y - unit * 0.05f)
                        path.lineTo(cx + span, y + unit * 0.10f)
                        path.lineTo(cx + span, y + unit * 0.10f + thick)
                        path.lineTo(cx, y - unit * 0.05f + thick)
                        path.lineTo(cx - span, y + unit * 0.10f + thick)
                        path.close()
                        drawPath(path, brush = body, alpha = 1f - 0.16f * i)
                        drawPath(path, color = secondary.copy(alpha = 0.8f), style = Stroke(width = line * 0.5f))
                    }
                }
                // ORBIT: a solid core with satellites — the army in miniature.
                2 -> {
                    val pips = 4 + (s / 11) % 4
                    val r = unit * 0.30f
                    drawCircle(
                        color = secondary.copy(alpha = 0.55f),
                        radius = r,
                        center = Offset(cx, cy),
                        style = Stroke(width = line * 0.5f),
                    )
                    repeat(pips) { i ->
                        val a = (2.0 * PI * i / pips + (s % 30) / 10.0).toFloat()
                        val c = Offset(cx + cos(a) * r, cy + sin(a) * r)
                        drawCircle(color = secondary, radius = unit * 0.055f, center = c)
                        drawCircle(
                            color = Color.White.copy(alpha = 0.22f),
                            radius = unit * 0.022f,
                            center = Offset(c.x - unit * 0.014f, c.y - unit * 0.014f),
                        )
                    }
                    drawCircle(brush = body, radius = unit * 0.17f, center = Offset(cx, cy))
                    drawCircle(
                        color = secondary,
                        radius = unit * 0.17f,
                        center = Offset(cx, cy),
                        style = Stroke(width = line * 0.5f),
                    )
                    // Specular glint: the core reads as a polished stone.
                    drawCircle(
                        color = Color.White.copy(alpha = 0.30f),
                        radius = unit * 0.05f,
                        center = Offset(cx - unit * 0.055f, cy - unit * 0.06f),
                    )
                }
                // RUNE SEAL: a filled diamond cut by engraved bars.
                3 -> {
                    val bars = 2 + (s / 13) % 3
                    val half = unit * 0.30f
                    val diamond = Path()
                    diamond.moveTo(cx, cy - half)
                    diamond.lineTo(cx + half * 0.78f, cy)
                    diamond.lineTo(cx, cy + half)
                    diamond.lineTo(cx - half * 0.78f, cy)
                    diamond.close()
                    drawPath(diamond, brush = body)
                    drawPath(diamond, color = secondary, style = Stroke(width = line * 0.6f))
                    repeat(bars) { i ->
                        val f = (i + 1f) / (bars + 1f)
                        val y = cy - half + half * 2 * f
                        val reach = half * 0.62f * (1f - kotlin.math.abs(f - 0.5f))
                        drawLine(
                            color = Color.Black.copy(alpha = 0.55f),
                            start = Offset(cx - reach, y),
                            end = Offset(cx + reach, y),
                            strokeWidth = line * 0.8f,
                        )
                    }
                    drawLine(
                        color = Color.White.copy(alpha = 0.22f),
                        start = Offset(cx, cy - half),
                        end = Offset(cx - half * 0.78f, cy),
                        strokeWidth = unit * 0.014f,
                    )
                }
                // FANGS: a facing pair of curved claws.
                4 -> {
                    listOf(-1f, 1f).forEach { dir ->
                        val p = Path()
                        p.moveTo(cx + dir * unit * 0.07f, cy - unit * 0.30f)
                        p.quadraticTo(
                            cx + dir * unit * 0.34f, cy - unit * 0.04f,
                            cx + dir * unit * 0.10f, cy + unit * 0.30f,
                        )
                        p.quadraticTo(
                            cx + dir * unit * 0.16f, cy - unit * 0.02f,
                            cx + dir * unit * 0.07f, cy - unit * 0.30f,
                        )
                        p.close()
                        drawPath(p, brush = body)
                        drawPath(p, color = secondary, style = Stroke(width = line * 0.5f))
                    }
                }
                // WREATH: mirrored laurel branches. Drawn as ANGLED OVALS on a
                // stem arc — quadratic blobs at one size ran together into a
                // featureless ring with no readable leaf.
                5 -> {
                    val leaves = 4
                    listOf(-1f, 1f).forEach { dir ->
                        drawArc(
                            color = secondary.copy(alpha = 0.55f),
                            startAngle = if (dir < 0) 118f else 242f,
                            sweepAngle = dir * 104f,
                            useCenter = false,
                            topLeft = Offset(cx - unit * 0.27f, cy - unit * 0.27f),
                            size = Size(unit * 0.54f, unit * 0.54f),
                            style = Stroke(width = line * 0.45f),
                        )
                        repeat(leaves) { i ->
                            val f = (i + 0.5f) / leaves
                            val deg = 118f + dir * 104f * f
                            val a = (deg * PI / 180.0).toFloat()
                            val px = cx + cos(a) * unit * 0.27f
                            val py = cy + sin(a) * unit * 0.27f
                            // Leaves taper toward the tip of the branch.
                            val lw = unit * (0.20f - 0.035f * i)
                            val lh = unit * (0.085f - 0.012f * i)
                            rotate(degrees = deg - 90f, pivot = Offset(px, py)) {
                                drawOval(
                                    brush = body,
                                    topLeft = Offset(px - lw / 2f, py - lh / 2f),
                                    size = Size(lw, lh),
                                )
                                drawOval(
                                    color = secondary.copy(alpha = 0.75f),
                                    topLeft = Offset(px - lw / 2f, py - lh / 2f),
                                    size = Size(lw, lh),
                                    style = Stroke(width = line * 0.3f),
                                )
                            }
                        }
                    }
                    // The stone the branches close around.
                    val gem = Path()
                    gem.moveTo(cx, cy - unit * 0.16f)
                    gem.lineTo(cx + unit * 0.11f, cy)
                    gem.lineTo(cx, cy + unit * 0.16f)
                    gem.lineTo(cx - unit * 0.11f, cy)
                    gem.close()
                    drawPath(gem, brush = body)
                    drawPath(gem, color = secondary, style = Stroke(width = line * 0.5f))
                    drawLine(
                        color = Color.White.copy(alpha = 0.26f),
                        start = Offset(cx, cy - unit * 0.14f),
                        end = Offset(cx - unit * 0.09f, cy),
                        strokeWidth = unit * 0.012f,
                    )
                }
                // WINGS: a spread pair of layered pinions.
                6 -> {
                    val rows = 3 + (s / 37) % 2
                    listOf(-1f, 1f).forEach { dir ->
                        repeat(rows) { i ->
                            val span = unit * (0.42f - 0.075f * i)
                            val y = cy - unit * 0.10f + unit * 0.11f * i
                            val p = Path()
                            p.moveTo(cx + dir * unit * 0.05f, y)
                            p.lineTo(cx + dir * span, y + unit * 0.075f)
                            p.lineTo(cx + dir * unit * 0.05f, y + unit * 0.11f)
                            p.close()
                            drawPath(p, brush = body, alpha = 1f - 0.18f * i)
                        }
                    }
                    drawRect(
                        brush = body,
                        topLeft = Offset(cx - unit * 0.042f, cy - unit * 0.22f),
                        size = Size(unit * 0.084f, unit * 0.50f),
                    )
                }
                // GATE: an arch on pillars — the thing hunters walk through.
                7 -> {
                    val half = unit * 0.26f
                    val baseY = cy + unit * 0.28f
                    val p = Path()
                    p.moveTo(cx - half, baseY)
                    p.lineTo(cx - half, cy - unit * 0.02f)
                    p.quadraticTo(cx, cy - unit * 0.40f, cx + half, cy - unit * 0.02f)
                    p.lineTo(cx + half, baseY)
                    p.close()
                    drawPath(p, brush = body)
                    drawPath(p, color = secondary, style = Stroke(width = line * 0.6f))
                    // Hollowed doorway so the arch reads as a gate, not a hill.
                    val inner = Path()
                    val ih = half * 0.52f
                    inner.moveTo(cx - ih, baseY)
                    inner.lineTo(cx - ih, cy + unit * 0.02f)
                    inner.quadraticTo(cx, cy - unit * 0.22f, cx + ih, cy + unit * 0.02f)
                    inner.lineTo(cx + ih, baseY)
                    inner.close()
                    drawPath(inner, color = Color.Black.copy(alpha = 0.62f))
                }
                // EYE: a slit pupil in a lens — the System watching.
                8 -> {
                    val p = Path()
                    p.moveTo(cx - unit * 0.34f, cy)
                    p.quadraticTo(cx, cy - unit * 0.28f, cx + unit * 0.34f, cy)
                    p.quadraticTo(cx, cy + unit * 0.28f, cx - unit * 0.34f, cy)
                    p.close()
                    drawPath(p, brush = body)
                    drawPath(p, color = secondary, style = Stroke(width = line * 0.6f))
                    drawCircle(color = Color.Black.copy(alpha = 0.72f), radius = unit * 0.10f, center = Offset(cx, cy))
                    drawCircle(color = secondary, radius = unit * 0.04f, center = Offset(cx, cy))
                    drawCircle(
                        color = Color.White.copy(alpha = 0.25f),
                        radius = unit * 0.025f,
                        center = Offset(cx - unit * 0.035f, cy - unit * 0.035f),
                    )
                }
                // SPIRE: a blade standing through a crossbar.
                else -> {
                    val p = Path()
                    p.moveTo(cx, cy - unit * 0.36f)
                    p.lineTo(cx + unit * 0.08f, cy - unit * 0.10f)
                    p.lineTo(cx + unit * 0.05f, cy + unit * 0.30f)
                    p.lineTo(cx - unit * 0.05f, cy + unit * 0.30f)
                    p.lineTo(cx - unit * 0.08f, cy - unit * 0.10f)
                    p.close()
                    drawPath(p, brush = body)
                    drawPath(p, color = secondary, style = Stroke(width = line * 0.5f))
                    val guard = unit * (0.20f + 0.04f * ((s / 41) % 3))
                    drawRect(
                        brush = body,
                        topLeft = Offset(cx - guard, cy - unit * 0.14f),
                        size = Size(guard * 2, unit * 0.055f),
                    )
                    drawLine(
                        color = Color.White.copy(alpha = 0.25f),
                        start = Offset(cx, cy - unit * 0.34f),
                        end = Offset(cx, cy + unit * 0.26f),
                        strokeWidth = unit * 0.012f,
                    )
                }
            }
        }
        }

        // 3. Rank pips under the mark: how far up the catalogue this frame sits.
        val pipCount = 1 + (s / 29) % 3
        repeat(pipCount) { i ->
            val step = unit * 0.075f
            val x = cx + (i - (pipCount - 1) / 2f) * step
            drawCircle(
                color = secondary.copy(alpha = 0.75f),
                radius = unit * 0.018f,
                center = Offset(x, cy + unit * 0.42f),
            )
        }
    }
}
