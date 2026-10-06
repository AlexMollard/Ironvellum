package com.ironvellum.app.ui.train

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironvellum.app.ui.components.animatorsOn
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.inkArc
import com.ironvellum.app.ui.theme.inkBorder
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val SLIDE_HEIGHT = 52.dp

/** The leading cap: the knob is the end of the fill, not a button on the track. */
private val CAP_WIDTH = 52.dp

/** Released before this share of the travel, the cap springs back. */
private const val SEAL_AT = 0.85f

private const val RING_MS = 700

/** The seal path starts while the flourish is still running, not after it. */
private const val SEAL_AFTER_MS = 300L

/**
 * Slide to seal: a neutral track with a centred label. The fill grows from the
 * left and its leading cap IS the knob. Emerald while dragging, gold once
 * sealed; released early it springs back, at the end it snaps and plays a ring
 * pulse, then calls [onSealed]. Without dragging, the whole control also offers
 * the accessibility action "Seal the trial". Reduced motion (system animations
 * off) skips the glide and the pulse.
 *
 * It stays at the end once sealed; bump [resetKey] to bring it back (the seal
 * was declined or failed).
 */
@Composable
internal fun SlideToSeal(
    label: String,
    onSealed: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    resetKey: Int = 0,
    trackColor: Color = IronvellumColors.Abyss,
) {
    val shape = MaterialTheme.shapes.medium
    val density = LocalDensity.current
    val motion = animatorsOn(LocalContext.current)
    val scope = rememberCoroutineScope()
    val sealedNow by rememberUpdatedState(onSealed)
    val capPx = with(density) { CAP_WIDTH.toPx() }
    val inset = with(density) { 1.dp.toPx() }
    var trackPx by remember { mutableFloatStateOf(0f) }
    val travel = (trackPx - 2 * inset - capPx).coerceAtLeast(0f)
    var offset by remember { mutableFloatStateOf(0f) }
    var sealed by remember { mutableStateOf(false) }
    val ring = remember { Animatable(0f) }

    suspend fun glideTo(target: Float, spec: AnimationSpec<Float>) {
        if (!motion) {
            offset = target
            return
        }
        Animatable(offset).animateTo(target, spec) { offset = value }
    }

    suspend fun seal() {
        if (sealed) return
        sealed = true
        glideTo(travel, tween(160))
        if (motion) {
            coroutineScope {
                launch {
                    ring.snapTo(0f)
                    ring.animateTo(1f, tween(RING_MS, easing = LinearOutSlowInEasing))
                }
                delay(SEAL_AFTER_MS)
                sealedNow()
            }
        } else {
            sealedNow()
        }
    }

    suspend fun springBack() = glideTo(0f, spring(stiffness = Spring.StiffnessMediumLow))

    LaunchedEffect(resetKey) {
        if (sealed) {
            sealed = false
            ring.snapTo(0f)
            springBack()
        }
    }

    val progress = if (travel > 0f) (offset / travel).coerceIn(0f, 1f) else 0f
    val fillBrush = if (sealed) {
        Brush.horizontalGradient(listOf(androidx.compose.ui.graphics.lerp(IronvellumColors.SovereignGold, IronvellumColors.Vault, 0.45f), IronvellumColors.SovereignGold))
    } else {
        Brush.horizontalGradient(listOf(androidx.compose.ui.graphics.lerp(IronvellumColors.Emerald, IronvellumColors.Vault, 0.65f), IronvellumColors.Emerald))
    }
    val capColor = when {
        !enabled -> IronvellumColors.Rune
        sealed -> IronvellumColors.SovereignGold
        else -> IronvellumColors.Emerald
    }

    Box(
        modifier
            .fillMaxWidth()
            .height(SLIDE_HEIGHT)
            .clip(shape)
            .background(trackColor)
            .inkBorder(IronvellumColors.Rune, shape, 1.dp)
            .onSizeChanged { trackPx = it.width.toFloat() }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                if (enabled) {
                    contentDescription = "Seal the trial"
                    onClick(label = "Seal the trial") {
                        if (!sealed) scope.launch { seal() }
                        true
                    }
                } else {
                    disabled()
                }
            },
    ) {
        Box(Modifier.fillMaxSize().padding(1.dp)) {
            Text(
                if (sealed) "Sealed" else label,
                style = MaterialTheme.typography.labelLarge,
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.SemiBold,
                // The theme's label tracking is for caps; this is a sentence.
                letterSpacing = 0.5.sp,
                color = if (sealed) MaterialTheme.colorScheme.onTertiary else IronvellumColors.InkMuted,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(start = CAP_WIDTH + 8.dp, end = 8.dp)
                    .graphicsLayer { alpha = if (sealed) 1f else (1f - progress * 1.5f).coerceIn(0f, 1f) },
            )
            Box(
                Modifier
                    .width(with(density) { (capPx + offset).toDp() })
                    .fillMaxHeight()
                    .background(fillBrush)
                    .pointerInput(enabled, travel) {
                        if (!enabled) return@pointerInput
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (!sealed) {
                                    scope.launch { if (travel > 0f && offset >= travel * SEAL_AT) seal() else springBack() }
                                }
                            },
                            onDragCancel = { if (!sealed) scope.launch { springBack() } },
                            onHorizontalDrag = { change, amount ->
                                if (!sealed) {
                                    change.consume()
                                    offset = (offset + amount).coerceIn(0f, travel)
                                }
                            },
                        )
                    },
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(
                    Modifier.width(CAP_WIDTH).fillMaxHeight().background(capColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (sealed) Icons.Filled.Check else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = if (!enabled) IronvellumColors.InkMuted else if (sealed) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.width(24.dp).height(24.dp),
                    )
                }
            }
            if (ring.value > 0f && ring.value < 1f) {
                Canvas(Modifier.fillMaxSize()) {
                    val center = Offset(size.width - capPx / 2f, size.height / 2f)
                    // Two rings, the second a beat behind the first.
                    listOf(0f, 0.2f).forEach { lag ->
                        val p = ((ring.value - lag) / (1f - lag)).coerceIn(0f, 1f)
                        if (p > 0f && p < 1f) {
                            inkArc(
                                center = center,
                                radius = 12.dp.toPx() + p * 80.dp.toPx(),
                                startDeg = 0f,
                                sweepDeg = 360f,
                                color = IronvellumColors.SovereignGold.copy(alpha = 0.9f * (1f - p)),
                                widthPx = 2.dp.toPx(),
                            )
                        }
                    }
                }
            }
        }
    }
}
