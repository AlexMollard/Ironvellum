package com.ironvellum.app.ui.dashboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironvellum.app.domain.Muscle
import com.ironvellum.app.ui.program.BodyFigures
import com.ironvellum.app.ui.program.FigureGeometry
import com.ironvellum.app.ui.program.FigureView
import com.ironvellum.app.ui.program.LocalBodySex
import com.ironvellum.app.ui.program.drawFigure
import com.ironvellum.app.ui.program.riteAlpha
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.IronvellumTracking
import com.ironvellum.app.ui.theme.inkArc
import com.ironvellum.app.ui.theme.inkDot
import kotlin.math.cos
import kotlin.math.sin

/*
 * The soul layer of Today: the small pieces of voice and emblem that sit in the header, the week rail
 * and the day card. Nothing here moves except the SEALED stamp's one-off thud; the Veil owns every
 * continuous motion (TodayVeil.kt). Line art goes through the ink primitives (inkArc, inkDot) or
 * drawPath, so the ruled-draw counts InkCoverageTest pins do not move.
 */

/** The muscle-focus glyph's box beside a rite's header. */
private val GLYPH_WIDTH = 44.dp
private val GLYPH_HEIGHT = 66.dp

/** The SEALED stamp's box, in the same corner the glyph holds. */
private val STAMP_SIZE = 68.dp

/** The single gold lozenge before the Strength Rank: earned, so it is gold. */
@Composable
internal fun RankLozenge() {
    Box(
        Modifier
            .size(7.dp)
            .graphicsLayer { rotationZ = 45f }
            .background(IronvellumColors.SovereignGold),
    )
}

/** The week rail's gold seal for a day whose rite is sealed: two rings and a tick. */
@Composable
internal fun SealMark(side: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(side).clearAndSetSemantics {}) {
        val u = size.width / 24f
        val gold = IronvellumColors.SovereignGold
        val c = Offset(12f * u, 12f * u)
        inkArc(c, 10.5f * u, 0f, 360f, gold, 1.6f * u)
        val tick = Path().apply { moveTo(7.5f * u, 12.5f * u); lineTo(10.8f * u, 15.8f * u); lineTo(16.8f * u, 9.2f * u) }
        drawPath(tick, gold, style = Stroke(2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/**
 * The rite's muscle focus, small: the real front figure the rite pages draw, each muscle lit in
 * proportion to the sets the rite gives it ([sets], as ProgramRules.weeklyVolume reads a rite). A rite
 * whose movements the muscle map does not know draws the bare figure.
 */
@Composable
internal fun RiteGlyph(sets: Map<Muscle, Double>, modifier: Modifier = Modifier) {
    val figure = BodyFigures.of(LocalBodySex.current)
    val top = sets.values.maxOrNull() ?: 0.0
    Canvas(modifier.size(GLYPH_WIDTH, GLYPH_HEIGHT).clearAndSetSemantics {}) {
        // The figure code lays a front and a back slot side by side; asking for twice the width puts the front one in this box.
        val g = FigureGeometry(figure, size.width * 2f, size.height)
        drawFigure(
            g,
            FigureView.FRONT,
            fill = { muscle ->
                val alpha = riteAlpha(sets[muscle] ?: 0.0, top)
                if (alpha <= 0f) IronvellumColors.Rune else IronvellumColors.Emerald.copy(alpha = alpha)
            },
            seed = 11,
        )
    }
}

/**
 * The SEALED stamp: gold rings, the word and the date, set at a tilt. When [thud] it lands once from a
 * larger size, the only motion on the card; otherwise it is simply there.
 */
@Composable
internal fun SealedStamp(date: String, thud: Boolean, modifier: Modifier = Modifier) {
    val settle = remember { Animatable(if (thud) 0f else 1f) }
    LaunchedEffect(thud) {
        if (thud) settle.animateTo(1f, tween(500, easing = CubicBezierEasing(0.2f, 1.4f, 0.4f, 1f)))
    }
    val gold = IronvellumColors.SovereignGold
    Box(
        modifier
            .size(STAMP_SIZE)
            .clearAndSetSemantics {}
            .graphicsLayer {
                val v = settle.value
                val s = 1.6f - 0.6f * v
                scaleX = s
                scaleY = s
                rotationZ = -11f
                alpha = 0.95f * (v * 2f).coerceIn(0f, 1f)
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(STAMP_SIZE)) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val r = size.width / 2f
            inkArc(c, r * 0.92f, 0f, 360f, gold, 1.6.dp.toPx())
            // The dotted ring: 36 small dots.
            for (i in 0 until 36) {
                val a = Math.toRadians(i * 10.0)
                inkDot(Offset(c.x + cos(a).toFloat() * r * 0.83f, c.y + sin(a).toFloat() * r * 0.83f), 0.6.dp.toPx(), gold.copy(alpha = 0.8f))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "SEALED",
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                letterSpacing = 1.sp,
                color = gold,
                maxLines = 1,
                softWrap = false,
            )
            Text(
                date.uppercase(),
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.Medium,
                fontSize = 7.sp,
                letterSpacing = 0.5.sp,
                color = gold.copy(alpha = 0.85f),
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** A closed ledger with its ribbon, for a day the cycle leaves free. Cut corners, muted ink. */
@Composable
internal fun LedgerMotif(modifier: Modifier = Modifier) {
    Canvas(modifier.size(52.dp).clearAndSetSemantics {}) {
        val u = size.width / 60f
        val cover = Path().apply {
            moveTo(14f * u, 14f * u); lineTo(40f * u, 14f * u); lineTo(43f * u, 17f * u); lineTo(43f * u, 49f * u)
            lineTo(17f * u, 49f * u); lineTo(14f * u, 46f * u); close()
        }
        drawPath(cover, IronvellumColors.InkMuted, style = Stroke(1.6f * u, join = StrokeJoin.Round))
        val marks = Path().apply {
            moveTo(19f * u, 14f * u); lineTo(19f * u, 49f * u)
            moveTo(27f * u, 24f * u); lineTo(37f * u, 24f * u)
            moveTo(27f * u, 29f * u); lineTo(34f * u, 29f * u)
        }
        drawPath(marks, IronvellumColors.Bracket, style = Stroke(1.3f * u, cap = StrokeCap.Round))
        val ribbon = Path().apply {
            moveTo(34f * u, 14f * u); lineTo(34f * u, 54f * u); lineTo(37f * u, 51f * u); lineTo(40f * u, 54f * u); lineTo(40f * u, 14f * u)
        }
        drawPath(ribbon, IronvellumColors.SystemGreen.copy(alpha = 0.18f), style = Fill)
        drawPath(ribbon, IronvellumColors.SystemGreen, style = Stroke(1.4f * u, join = StrokeJoin.Round))
    }
}

/**
 * The header's name line: [name], then [rank]. The rank is measured first and never shortened; the name
 * takes what is left and ellipsizes only when it must.
 */
@Composable
internal fun NameRow(
    name: @Composable () -> Unit,
    rank: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Layout(
        content = {
            Box { name() }
            Box { rank() }
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val rankP = measurables[1].measure(loose)
        val nameP = measurables[0].measure(loose.copy(maxWidth = (constraints.maxWidth - rankP.width).coerceAtLeast(0)))
        val height = maxOf(nameP.height, rankP.height)
        layout(nameP.width + rankP.width, height) {
            nameP.place(0, (height - nameP.height) / 2)
            rankP.place(nameP.width, (height - rankP.height) / 2)
        }
    }
}

/**
 * A rite's header on the day card: its name in tracked display caps, the narrator's one line, a small
 * [meta] line, and [corner] (the muscle glyph or the SEALED stamp) in the top-right. [lead] sits before the
 * text (the respite ledger).
 */
@Composable
internal fun RiteHeader(
    title: String,
    narrator: String,
    meta: AnnotatedString?,
    modifier: Modifier = Modifier,
    lead: (@Composable () -> Unit)? = null,
    corner: (@Composable () -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        if (lead != null) {
            lead()
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.titleMedium,
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp,
                letterSpacing = IronvellumTracking.InlineLabel,
                color = IronvellumColors.Ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                narrator,
                style = MaterialTheme.typography.bodySmall,
                fontStyle = FontStyle.Italic,
                color = IronvellumColors.Ink.copy(alpha = 0.8f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (meta != null) {
                Text(
                    meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        if (corner != null) {
            Box(Modifier.padding(start = 8.dp).heightIn(min = GLYPH_HEIGHT), contentAlignment = Alignment.TopEnd) { corner() }
        }
    }
}
