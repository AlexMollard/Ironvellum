package com.ironvellum.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.ironvellum.app.ui.theme.IronvellumColors
import kotlin.math.sin
import kotlin.random.Random

/**
 * Full-bleed animated atmosphere behind the Muster page. Purely decorative:
 * no clicks, no text, no children. Total alpha is kept low so text above
 * stays legible — near-black with life in it, never a busy wallpaper.
 */
@Composable
fun MusterBackdrop(figures: Int, active: Boolean, modifier: Modifier = Modifier) {
    // One slow driving float, reversed so nothing ever snaps.
    val breath = rememberInfiniteTransition(label = "musterBackdrop")
    val t by breath.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathT",
    )
    val drift by breath.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 16_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "driftT",
    )

    // Deterministic particle field: seeded once, stable across recomposition.
    val particles = remember {
        val rng = Random(seed = 0x5A0L)
        List(40) {
            Particle(
                fx = rng.nextFloat(),
                fy = rng.nextFloat(),
                phase = rng.nextFloat(),
                speed = 0.35f + rng.nextFloat() * 0.65f,
                radius = 0.9f + rng.nextFloat() * 2.1f,
            )
        }
    }

    // Roll growth thickens the field: 10 particles at 0 figures, ~40 at 400+.
    val visibleCount = (10 + (figures.coerceAtLeast(0)) * 30 / 400).coerceIn(10, 40)
    // Idle roll: damped life, never frozen to nothing.
    val energy = if (active) 1f else 0.45f

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // 1. Vertical depth wash: Abyss into a slightly warmer/greener dark.
        drawRect(
            brush = Brush.verticalGradient(
                listOf(
                    IronvellumColors.Abyss,
                    Color(0xFF0D1310),
                    Color(0xFF111A14),
                ),
            ),
        )

        // 2. Breathing radial glow behind the hero essence number (upper third).
        val glowAlpha = (0.05f + 0.11f * breathCurve(t)) * energy
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    IronvellumColors.EmeraldBright.copy(alpha = glowAlpha),
                    IronvellumColors.Emerald.copy(alpha = glowAlpha * 0.5f),
                    Color.Transparent,
                ),
                center = Offset(w * 0.5f, h * 0.22f),
                radius = size.minDimension * (0.52f + 0.04f * breathCurve(t)),
            ),
            radius = size.minDimension * (0.52f + 0.04f * breathCurve(t)),
            center = Offset(w * 0.5f, h * 0.22f),
        )

        // 3. Drifting ambient particles across the whole height.
        for (i in 0 until visibleCount) {
            val p = particles[i]
            val bob = sin((p.phase + drift * p.speed) * 2f * Math.PI.toFloat())
            val sway = sin((p.phase * 1.7f + drift * p.speed * 0.6f) * 2f * Math.PI.toFloat())
            val x = (p.fx + sway * 0.02f * energy).coerceIn(0f, 1f) * w
            val y = (p.fy + bob * 0.035f * energy).coerceIn(0f, 1f) * h
            val flicker = 0.5f + 0.5f * sin((p.phase * 3.1f + t) * 2f * Math.PI.toFloat())
            val alpha = (0.05f + 0.10f * flicker) * energy
            drawCircle(
                color = IronvellumColors.EmeraldBright.copy(alpha = alpha),
                radius = p.radius,
                center = Offset(x, y),
            )
        }

        // 4. Bottom vignette: separates the page from the bottom nav bar.
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, IronvellumColors.Abyss.copy(alpha = 0.9f)),
                startY = h * 0.78f,
                endY = h,
            ),
            topLeft = Offset(0f, h * 0.78f),
            size = Size(w, h * 0.22f),
        )
    }
}

/** Smooth 0..1..0 breathing curve from the reversed driving float. */
private fun breathCurve(t: Float): Float = 0.5f - 0.5f * kotlin.math.cos(t * 2f * Math.PI.toFloat())

private data class Particle(
    val fx: Float,
    val fy: Float,
    val phase: Float,
    val speed: Float,
    val radius: Float,
)
