package com.ironvellum.app.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.currentStateAsState
import com.ironvellum.app.domain.Idle
import com.ironvellum.app.domain.fmt
import com.ironvellum.app.ui.components.InkDivider
import com.ironvellum.app.ui.components.InkRail
import com.ironvellum.app.ui.components.animatorsOn
import com.ironvellum.app.ui.components.plural
import com.ironvellum.app.ui.theme.DotShape
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.inkArc
import com.ironvellum.app.ui.theme.inkDot
import com.ironvellum.app.ui.theme.inkStroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * The Veil section of Today, and the only zone of the screen that moves continuously: drifting motes,
 * the essence ticking live, the full-strength bar draining, and (on a respite day) the rune ring turning
 * under a breathing glow.
 *
 * One motion clock drives all of it: a single infinite transition of MOTION_LOOP_S seconds, read only
 * inside draw and graphicsLayer lambdas, so a frame invalidates a layer and never recomposes. Every
 * period in play divides that loop, so it wraps without a jump. It exists only in the one live instance
 * of the section and only while Today is on screen and animations are on: the layout's measuring probes
 * never run it, and with motion off (animations disabled, the app in the background, a preview) nothing
 * animates and the figures hold still.
 */

/** Which face the Veil wears: the respite hero, the full section, or the one-line form. */
internal enum class VeilForm { HERO, FULL, COMPACT }

/** True only in the composition that is actually shown; the layout's measuring probes see false. */
internal val LocalTodayLive = staticCompositionLocalOf { false }

/** Whether Today's decoration may move at all; see [rememberTodayMotion]. */
internal val LocalTodayMotion = compositionLocalOf { false }

/**
 * Whether the Veil may move: not in a preview, only while this screen is the resumed one (so nothing
 * animates in the background or under another screen), and not when the lifter has turned system
 * animations off, the same switch the seal slider and the celebrations honour.
 */
@Composable
internal fun rememberTodayMotion(): Boolean {
    if (LocalInspectionMode.current) return false
    val context = LocalContext.current
    val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    val state by owner.lifecycle.currentStateAsState()
    val resumed = state.isAtLeast(Lifecycle.State.RESUMED)
    return resumed && remember(resumed) { animatorsOn(context) }
}

/** How often the live essence is recomputed from the domain accrual. */
private const val TICK_MS = 3_000L

/** The rune ring's diameter on a respite day. */
private val RING_SIZE = 184.dp

/** The Veil's reserved inscriptions slot: the same height whether or not any wait. */
private val SLOT_HEIGHT = 44.dp

private val RING_TICK = Color(0xFF5C5850)

@Composable
internal fun VeilSection(veil: VeilGlance?, form: VeilForm, nowMs: Long, onOpen: () -> Unit) {
    val animate = LocalTodayLive.current && LocalTodayMotion.current
    // The accrual is recomputed against the wall clock, offset so a caller's fixed `nowMs` stays consistent.
    val now = rememberVeilNow(nowMs, animate, TICK_MS)
    val phase = rememberVeilPhase(animate)

    val snapshot = veil?.snapshot
    val essence = snapshot?.let { Idle.collect(it.state, it.rate, now).essence }
    val strength = snapshot?.let { veilStrength(it.state.lastCollectedAtMs, now) }
    val motes = remember(form) {
        when (form) {
            VeilForm.HERO -> veilMotes(34, 11, maxRadiusDp = 2.5f)
            VeilForm.FULL -> veilMotes(22, 7)
            VeilForm.COMPACT -> veilMotes(8, 5)
        }
    }

    Column(Modifier.fillMaxWidth().padding(top = 8.dp).testTag("veil-section")) {
        InkDivider()
        Box(Modifier.fillMaxWidth()) {
            VeilMotes(motes, phase, animate, Modifier.matchParentSize())
            Column(Modifier.fillMaxWidth()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button, onClickLabel = "Open the Veil", onClick = onOpen)
                        .padding(top = if (form == VeilForm.COMPACT) 2.dp else 10.dp),
                ) {
                    when (form) {
                        VeilForm.COMPACT -> CompactBody(snapshot, essence, strength, phase, animate)
                        VeilForm.FULL -> FullBody(snapshot, essence, strength, phase, animate)
                        VeilForm.HERO -> HeroBody(snapshot, essence, strength, phase, animate)
                    }
                }
                InscriptionsSlot(veil?.inscriptions, onOpen)
            }
        }
    }
}

@Composable
private fun TitleRow(trailing: @Composable RowScope.() -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "The Veil",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = IronvellumColors.Ink,
            modifier = Modifier.weight(1f),
        )
        trailing()
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = IronvellumColors.InkMuted,
            modifier = Modifier.padding(start = 6.dp).size(18.dp),
        )
    }
}

@Composable
private fun RateLabel(snapshot: com.ironvellum.app.data.IdleSnapshot?) {
    if (snapshot == null) return
    Text(
        "${rateLabel(snapshot.rate.perHour)}/h",
        style = MaterialTheme.typography.labelMedium,
        color = IronvellumColors.InkMuted,
    )
}

/** Title, essence and rate on one line, then the bar. */
@Composable
private fun CompactBody(
    snapshot: com.ironvellum.app.data.IdleSnapshot?,
    essence: Long?,
    strength: VeilStrength?,
    phase: State<Float>,
    animate: Boolean,
) {
    TitleRow(
        modifier = Modifier.heightIn(min = 40.dp),
        trailing = {
            if (snapshot != null && essence != null) {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = IronvellumColors.Ink, fontWeight = FontWeight.SemiBold)) { append("%,d".fmt(essence)) }
                        append(" essence · ${rateLabel(snapshot.rate.perHour)}/h")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = IronvellumColors.InkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
    )
    // The caption of the full forms rides the bar as its description, so a screen reader still hears the time left.
    strength?.let { VeilBar(it, phase, animate, Modifier.padding(bottom = 4.dp).semantics { contentDescription = it.caption }) }
}

/** Title with the rate, the essence as a figure, the bar and what it means. */
@Composable
private fun FullBody(
    snapshot: com.ironvellum.app.data.IdleSnapshot?,
    essence: Long?,
    strength: VeilStrength?,
    phase: State<Float>,
    animate: Boolean,
) {
    TitleRow(trailing = { RateLabel(snapshot) })
    if (essence != null) {
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.Bottom) {
            EssenceFigure(essence, 26.sp, animate)
            Text(
                "essence",
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
                modifier = Modifier.padding(start = 6.dp, bottom = 3.dp),
            )
        }
    }
    strength?.let {
        VeilBar(it, phase, animate, Modifier.padding(top = 10.dp))
        Text(it.caption, style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted, modifier = Modifier.padding(top = 5.dp))
    }
}

/** The respite hero: the essence inside a slowly turning rune ring on a breathing glow. */
@Composable
private fun HeroBody(
    snapshot: com.ironvellum.app.data.IdleSnapshot?,
    essence: Long?,
    strength: VeilStrength?,
    phase: State<Float>,
    animate: Boolean,
) {
    TitleRow(trailing = { RateLabel(snapshot) })
    Box(Modifier.fillMaxWidth().height(RING_SIZE + 8.dp).testTag("veil-hero"), contentAlignment = Alignment.Center) {
        BreathingGlow(phase)
        RuneRing(phase, Modifier.size(RING_SIZE))
        if (essence != null && snapshot != null) {
            val state = snapshot.state
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                EssenceFigure(essence, 40.sp, animate)
                Text(
                    buildString {
                        append("essence · ${state.figures} ${plural(state.figures, "echo", "echoes")}")
                        if (state.relicMultiplier > 1.0) append(" · relic ×${"%.2f".fmt(state.relicMultiplier)}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
    strength?.let {
        VeilBar(it, phase, animate, Modifier.padding(top = 4.dp))
        Text(it.caption, style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted, modifier = Modifier.padding(top = 5.dp))
    }
}

/**
 * The reserved line under every Veil form. With inscriptions waiting it is the gold count and the link
 * to spend them; with none it is a quiet line of the same height, so the arrival of one never moves
 * anything else on the page.
 */
@Composable
private fun InscriptionsSlot(waiting: Int?, onOpen: () -> Unit) {
    val line = inscriptionsLine(waiting)
    Row(
        Modifier.fillMaxWidth().heightIn(min = SLOT_HEIGHT).testTag("veil-inscriptions"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            line.text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (line.waiting) FontWeight.SemiBold else FontWeight.Normal,
            color = if (line.waiting) IronvellumColors.SovereignGold else IronvellumColors.InkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (line.inscribe) {
            // The Veil is where inscriptions are spent: the same destination as the section.
            Text(
                "Inscribe",
                style = MaterialTheme.typography.bodyMedium,
                color = IronvellumColors.SystemGreen,
                modifier = Modifier
                    .heightIn(min = SLOT_HEIGHT)
                    .clickable(role = Role.Button, onClickLabel = "Inscribe in the Veil", onClick = onOpen)
                    .padding(start = 12.dp)
                    .wrapContentHeight(Alignment.CenterVertically),
            )
        }
    }
}

/** The full-strength bar, with a soft head that pulses while live. */
@Composable
private fun VeilBar(strength: VeilStrength, phase: State<Float>, animate: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(3.dp)) {
        InkRail(
            fraction = strength.fraction,
            height = 3.dp,
            fill = SolidColor(IronvellumColors.SystemGreen),
        )
        if (strength.fraction > 0f) {
            Canvas(Modifier.fillMaxSize().clearAndSetSemantics {}) {
                val pulse = if (animate) 0.5f + 0.5f * sin(2f * PI.toFloat() * phase.value * 90f) else 0.5f
                val head = Offset(size.width * strength.fraction, size.height / 2f)
                inkDot(head, 6.dp.toPx(), IronvellumColors.SystemGreen.copy(alpha = 0.12f + 0.2f * pulse))
                inkDot(head, 2.5.dp.toPx(), IronvellumColors.SystemGreen.copy(alpha = 0.55f + 0.45f * pulse))
            }
        }
    }
}

/** A soft green core behind the ring that swells and fades on a six second breath. */
@Composable
private fun BreathingGlow(phase: State<Float>) {
    Box(
        Modifier
            .size(112.dp)
            .graphicsLayer {
                val wave = 0.5f + 0.5f * sin(2f * PI.toFloat() * phase.value * 60f)
                alpha = 0.5f + 0.5f * wave
                val s = 0.9f + 0.18f * wave
                scaleX = s
                scaleY = s
            }
            .background(
                Brush.radialGradient(listOf(IronvellumColors.SystemGreen.copy(alpha = 0.22f), Color.Transparent)),
                DotShape,
            ),
    )
}

/**
 * Two rune rings turning against each other, a third standing still. Each is drawn once and turned by a
 * layer, so the turning costs no redraw: the outer ring takes 180 s a turn, the inner 120 s the other way.
 */
@Composable
private fun RuneRing(phase: State<Float>, modifier: Modifier = Modifier) {
    Box(modifier.clearAndSetSemantics {}) {
        Canvas(Modifier.fillMaxSize().graphicsLayer { rotationZ = phase.value * 720f }) {
            val u = size.width / 240f
            val c = Offset(120f * u, 120f * u)
            inkArc(c, 110f * u, 0f, 360f, IronvellumColors.Rune, 1.dp.toPx())
            for (i in 0 until 72) {
                val a = Math.toRadians(i * 5.0)
                val long = i % 6 == 0
                val r2 = if (long) 103f else 106f
                val s = sin(a).toFloat()
                val k = cos(a).toFloat()
                inkStroke(
                    Offset(c.x + 110f * u * s, c.y - 110f * u * k),
                    Offset(c.x + r2 * u * s, c.y - r2 * u * k),
                    if (i % 18 == 0) IronvellumColors.SystemGreen else RING_TICK,
                    (if (long) 1.4.dp else 0.9.dp).toPx(),
                )
            }
        }
        Canvas(Modifier.fillMaxSize().graphicsLayer { rotationZ = -phase.value * 1080f }) {
            val u = size.width / 240f
            val c = Offset(120f * u, 120f * u)
            for (i in 0 until 36) {
                val a = Math.toRadians(i * 10.0)
                inkDot(Offset(c.x + 86f * u * sin(a).toFloat(), c.y - 86f * u * cos(a).toFloat()), 0.7.dp.toPx(), IronvellumColors.Bracket)
            }
            for (i in 0 until 8) {
                val a = Math.toRadians(i * 45.0 + 22.5)
                val at = Offset(c.x + 92f * u * sin(a).toFloat(), c.y - 92f * u * cos(a).toFloat())
                val turn = a.toFloat()
                fun corner(x: Float, y: Float) = Offset(
                    at.x + (x * cos(turn) - y * sin(turn)) * u,
                    at.y + (x * sin(turn) + y * cos(turn)) * u,
                )
                val rune = Path().apply {
                    val top = corner(0f, -3f)
                    moveTo(top.x, top.y)
                    corner(3f, 0f).let { lineTo(it.x, it.y) }
                    corner(0f, 3f).let { lineTo(it.x, it.y) }
                    corner(-3f, 0f).let { lineTo(it.x, it.y) }
                    close()
                }
                drawPath(rune, IronvellumColors.Bracket, style = Stroke(0.9.dp.toPx(), cap = StrokeCap.Round))
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            val u = size.width / 240f
            inkArc(Offset(120f * u, 120f * u), 66f * u, 0f, 360f, IronvellumColors.Rune, 1.dp.toPx())
        }
    }
}
