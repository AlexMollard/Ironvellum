package com.ironvellum.app.ui.dashboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironvellum.app.domain.Muscle
import com.ironvellum.app.ui.components.InkDivider
import com.ironvellum.app.ui.components.plural
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

/*
 * The soul layer of Today: the small pieces of voice and emblem that sit in the header, the week rail
 * and the day card. Nothing here moves except the compact seal's one-off thud; the Veil owns every
 * continuous motion (TodayVeil.kt). Line art goes through the ink primitives (inkArc) or
 * drawPath, so the ruled-draw counts InkCoverageTest pins do not move.
 */

/** The muscle-focus glyph's box beside a rite's header. */
private val GLYPH_WIDTH = 52.dp
private val GLYPH_HEIGHT = 78.dp

/** A small identity crest beside the name; rank stays a separate text link. */
@Composable
internal fun HeaderCrest() {
    Canvas(Modifier.size(18.dp, 26.dp).clearAndSetSemantics {}) {
        val w = size.width
        val h = size.height
        for (inset in listOf(0.08f, 0.28f)) {
            val diamond = Path().apply {
                moveTo(w / 2f, h * inset)
                lineTo(w * (1f - inset), h / 2f)
                lineTo(w / 2f, h * (1f - inset))
                lineTo(w * inset, h / 2f)
                close()
            }
            drawPath(diamond, IronvellumColors.Emerald, style = Stroke(1.dp.toPx()))
        }
    }
}

/** The worn title takes its natural width; its rule uses the remaining space. */
@Composable
internal fun WornTitle(title: String) {
    Layout(
        modifier = Modifier.fillMaxWidth(),
        content = {
            Text(
                title, style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            InkDivider()
        },
    ) { measurables, constraints ->
        val gap = 12.dp.roundToPx()
        val reserved = 32.dp.roundToPx() + gap
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val titleP = measurables[0].measure(loose.copy(maxWidth = (constraints.maxWidth - reserved).coerceAtLeast(0)))
        val ruleWidth = (constraints.maxWidth - titleP.width - gap).coerceAtLeast(0)
        val ruleP = measurables[1].measure(loose.copy(minWidth = ruleWidth, maxWidth = ruleWidth))
        val height = maxOf(titleP.height, ruleP.height)
        layout(constraints.maxWidth, height) {
            titleP.placeRelative(0, (height - titleP.height) / 2)
            ruleP.placeRelative(titleP.width + gap, (height - ruleP.height) / 2)
        }
    }
}

/** A quiet, full-width Oath control above the week; the day card keeps the emphasis. */
@Composable
internal fun OathRow(days: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("today-oath")
            .clip(MaterialTheme.shapes.medium)
            .clickable(role = Role.Button, onClickLabel = "Explain Oath", onClick = onClick)
            .semantics { stateDescription = "$days ${plural(days, "day", "days")} kept" }
            .padding(bottom = 4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        SealMark(18.dp, earned = days > 0)
        Spacer(Modifier.width(10.dp))
        Text(
            "OATH", fontSize = 10.sp, letterSpacing = 0.8.sp,
            fontWeight = FontWeight.Medium, color = IronvellumColors.InkMuted,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            days.toString(), fontSize = 18.sp, lineHeight = 22.sp,
            fontWeight = FontWeight.Bold, color = IronvellumColors.Ink,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            if (days > 0) "${plural(days, "day", "days")} kept" else "Seal a trial to begin",
            modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
            color = IronvellumColors.InkMuted,
        )
        Spacer(Modifier.width(8.dp))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
            tint = IronvellumColors.InkMuted, modifier = Modifier.size(16.dp),
        )
    }
}

/** The week rail's gold seal for a day whose rite is sealed: two rings and a tick. */
@Composable
internal fun SealMark(side: Dp, modifier: Modifier = Modifier, earned: Boolean = true) {
    Canvas(modifier.size(side).clearAndSetSemantics {}) {
        val u = size.width / 24f
        val gold = if (earned) IronvellumColors.SovereignGold else IronvellumColors.InkMuted
        val c = Offset(12f * u, 12f * u)
        inkArc(c, 10.5f * u, 0f, 360f, gold, 1.6f * u)
        val tick = Path().apply { moveTo(7.5f * u, 12.5f * u); lineTo(10.8f * u, 15.8f * u); lineTo(16.8f * u, 9.2f * u) }
        if (earned) drawPath(tick, gold, style = Stroke(2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
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
            outlineWidth = 1.dp,
        )
    }
}

/** The sealed rite's muscle focus and a compact gold seal; only the seal thuds in. */
@Composable
internal fun SealedRiteGlyph(
    sets: Map<Muscle, Double>,
    date: String,
    thud: Boolean,
    modifier: Modifier = Modifier,
) {
    val settle = remember { Animatable(if (thud) 0f else 1f) }
    LaunchedEffect(thud) {
        if (thud) settle.animateTo(1f, tween(500, easing = CubicBezierEasing(0.2f, 1.4f, 0.4f, 1f)))
    }
    val gold = IronvellumColors.SovereignGold
    Column(modifier.width(58.dp).clearAndSetSemantics {}, horizontalAlignment = Alignment.CenterHorizontally) {
        RiteGlyph(sets)
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            SealMark(
                12.dp,
                Modifier.graphicsLayer {
                    val v = settle.value
                    val s = 1.6f - 0.6f * v
                    scaleX = s
                    scaleY = s
                    alpha = (v * 2f).coerceIn(0f, 1f)
                },
            )
            Spacer(Modifier.width(3.dp))
            Text(
                "SEALED",
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                lineHeight = 10.sp,
                letterSpacing = 0.5.sp,
                color = gold,
                maxLines = 1,
                softWrap = false,
            )
        }
        Text(
            date.uppercase(),
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Medium,
            fontSize = 7.sp,
            lineHeight = 8.sp,
            letterSpacing = 0.5.sp,
            color = gold.copy(alpha = 0.85f),
            maxLines = 1,
            softWrap = false,
        )
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
 * [meta] line, and [corner] (the muscle glyph or sealed glyph) in the top-right. [lead] sits before the
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
            Box(Modifier.padding(start = 8.dp).heightIn(min = 66.dp), contentAlignment = Alignment.TopEnd) { corner() }
        }
    }
}
