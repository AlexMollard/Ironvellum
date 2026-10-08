package com.ironvellum.app.ui.dashboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.ironvellum.app.domain.fmt
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.inkDot
import kotlinx.coroutines.delay
import kotlin.math.min

/*
 * The Veil's ticker and motes, shared by the Veil section on Today (TodayVeil.kt) and the Veil screen
 * (idle/IdleScreen.kt) so the two cannot drift apart. Both gate their motion the same way: the caller
 * passes `animate` from [rememberTodayMotion] (resumed, animators on, not a preview) and, with it false,
 * the motes sit still and the figure holds.
 */

/**
 * The Veil's motion clock: one infinite transition of [MOTION_LOOP_S] seconds, read only inside draw and
 * graphicsLayer lambdas. A constant 0 when [animate] is false, so nothing runs.
 */
@Composable
internal fun rememberVeilPhase(animate: Boolean): State<Float> =
    if (animate) {
        rememberInfiniteTransition(label = "veil").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(MOTION_LOOP_S * 1000, easing = LinearEasing), RepeatMode.Restart),
            label = "veilPhase",
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

/**
 * The wall clock the live essence is recomputed against, every [tickMs] while [animate]; otherwise the
 * caller's [nowMs] unchanged. Offset so a caller's fixed [nowMs] stays consistent.
 */
@Composable
internal fun rememberVeilNow(nowMs: Long, animate: Boolean, tickMs: Long): Long {
    val skew = remember { nowMs - System.currentTimeMillis() }
    val ticking by produceState(nowMs, animate) {
        if (!animate) return@produceState
        while (true) {
            value = System.currentTimeMillis() + skew
            delay(tickMs)
        }
    }
    return if (animate) ticking else nowMs
}

/**
 * The essence as a figure. When it climbs by a whole number while the Veil is live it flashes from green
 * to ink and a "+N" lifts off it, once; it never fakes a tick. The "+N" is an overlay, so it moves nothing.
 * [text] is what is drawn (whole units by default); [essence] is the whole-unit value the flash follows.
 */
@Composable
internal fun EssenceFigure(
    essence: Long,
    size: TextUnit,
    animate: Boolean,
    modifier: Modifier = Modifier,
    text: String = "%,d".fmt(essence),
) {
    val bump = remember { Animatable(1f) }
    var seen by remember { mutableLongStateOf(essence) }
    var gained by remember { mutableIntStateOf(1) }
    LaunchedEffect(essence) {
        val before = seen
        seen = essence
        if (animate && essence > before) {
            gained = (essence - before).coerceIn(1L, 999L).toInt()
            bump.snapTo(0f)
            bump.animateTo(1f, tween(1600, easing = LinearEasing))
        }
    }
    val flash = lerp(IronvellumColors.SystemGreen, IronvellumColors.Ink, (bump.value * 2f).coerceAtMost(1f))
    Box(modifier) {
        Text(
            text,
            style = MaterialTheme.typography.titleLarge,
            fontSize = size,
            fontWeight = FontWeight.Bold,
            color = flash,
            maxLines = 1,
        )
        if (bump.value < 1f) {
            Text(
                "+$gained",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = IronvellumColors.SystemGreen,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(y = (-4).dp)
                    .graphicsLayer {
                        val t = bump.value
                        alpha = if (t < 0.2f) t / 0.2f else 1f - (t - 0.2f) / 0.8f
                        translationY = -22.dp.toPx() * t
                    },
            )
        }
    }
}

/** Motes rising behind the section, each along its own slow path, faded at the top and the foot. */
@Composable
internal fun VeilMotes(motes: List<VeilMote>, phase: State<Float>, animate: Boolean, modifier: Modifier) {
    Canvas(modifier.clearAndSetSemantics {}) {
        val loop = phase.value
        val w = size.width
        val h = size.height
        motes.forEach { m ->
            val p = if (animate) moteProgress(m, loop) else 0f
            val y = h - m.y * 0.5f * h - p * m.rise * h
            val x = m.x * w + m.driftDp.dp.toPx() * p
            val edge = min(y / h / 0.3f, (1f - y / h) / 0.2f).coerceIn(0f, 1f)
            val alpha = (if (animate) moteAlpha(p) else 0.35f) * edge
            if (alpha > 0.01f) {
                val tint = if (m.bright) IronvellumColors.Ink else IronvellumColors.SystemGreen
                inkDot(Offset(x, y), m.radiusDp.dp.toPx(), tint.copy(alpha = alpha))
            }
        }
    }
}
