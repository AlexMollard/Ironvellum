package com.ironvellum.app.ui.dashboard

import com.ironvellum.app.R
import androidx.compose.ui.res.stringResource
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.ui.asString
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
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle as ComposeTextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironvellum.app.data.Words
import com.ironvellum.app.domain.ArmyClass
import com.ironvellum.app.domain.TitleRule
import com.ironvellum.app.domain.Titles
import com.ironvellum.app.domain.Muscle
import com.ironvellum.app.domain.Xp
import com.ironvellum.app.ui.components.InkDivider
import com.ironvellum.app.ui.components.InkRail
import com.ironvellum.app.ui.program.BodyFigures
import com.ironvellum.app.ui.program.FigureGeometry
import com.ironvellum.app.ui.program.FigureView
import com.ironvellum.app.ui.program.LocalBodySex
import com.ironvellum.app.ui.program.drawFigure
import com.ironvellum.app.ui.program.riteAlpha
import com.ironvellum.app.ui.theme.CappedFontScale
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.IronvellumTracking
import com.ironvellum.app.ui.theme.METAL_WASH
import com.ironvellum.app.ui.theme.Metal
import com.ironvellum.app.ui.theme.emeraldRamp
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
        val ramp = emeraldRamp()
        for (inset in listOf(0.08f, 0.28f)) {
            val diamond = Path().apply {
                moveTo(w / 2f, h * inset)
                lineTo(w * (1f - inset), h / 2f)
                lineTo(w / 2f, h * (1f - inset))
                lineTo(w * inset, h / 2f)
                close()
            }
            // The outer diamond holds the faint wash; both lines are the emerald ramp.
            if (inset < 0.1f) drawPath(diamond, ramp, alpha = METAL_WASH, style = Fill)
            drawPath(diamond, ramp, style = Stroke(1.dp.toPx()))
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

/** Two independent progress controls; both explain in place without navigation arrows. */
@Composable
internal fun HeaderProgress(progress: Xp.Progress, days: Int, onLevel: () -> Unit, onOath: () -> Unit) {
    // Read the existing deed catalogue so the target and its name cannot drift apart.
    val next = remember(days) {
        Titles.ALL.mapNotNull { title ->
            (title.rule as? TitleRule.TrainingStreak)?.days?.let { it to title }
        }.filter { it.first > days }.minByOrNull { it.first }
    }
    val oathKept = UiText.plural(R.plurals.today_oath_kept, days).asString()
    val oathValue = if (next != null) UiText.plural(R.plurals.today_oath_progress, next.first, days, next.first).asString() else oathKept
    // The line under each bar is not drawn (Today's height goes to the rite); a screen reader still hears it as the bar's state.
    val oathState = when {
        next == null -> UiText.res(R.string.today_oath_state_done, oathKept).asString()
        days == 0 -> UiText.themed(R.string.today_oath_state_begin, R.string.today_oath_state_begin__t, UiText.plural(R.plurals.today_oath_state_next, next.first, oathKept, next.first, next.second.name).asString()).asString()
        else -> UiText.plural(R.plurals.today_oath_state_next, next.first, oathKept, next.first, next.second.name).asString()
    }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp), verticalAlignment = Alignment.Top) {
        ProgressColumn(
            title = "${ArmyClass.forLevel(progress.level).title} ${progress.level}",
            fraction = if (progress.needed <= 0) 0f else progress.intoLevel.toFloat() / progress.needed,
            value = AnnotatedString(stringResource(R.string.today_level_value, progress.intoLevel, progress.needed)),
            fill = emeraldRamp(),
            tag = "today-level", barTag = "today-xp-bar", onClickLabel = stringResource(R.string.today_level_explain_label),
            state = stringResource(R.string.today_level_state, progress.intoLevel, progress.needed),
            modifier = Modifier.weight(1f), onClick = onLevel,
        )
        Spacer(Modifier.width(20.dp))
        ProgressColumn(
            title = Words.oath.c,
            fraction = next?.let { days.toFloat() / it.first } ?: 1f,
            value = buildAnnotatedString {
                append(oathValue)
                // Gold is for what was earned: no days kept yet reads in plain Ink. The figure leads the text in every language
                // that writes it first; indexOf finds it wherever the translation puts it.
                val figure = days.toString()
                val at = oathValue.indexOf(figure)
                if (at >= 0) {
                    addStyle(SpanStyle(color = if (days > 0) IronvellumColors.SovereignGold else IronvellumColors.Ink), at, at + figure.length)
                }
            },
            fill = Metal.Fabled.bounds(),
            tag = "today-oath", barTag = "today-oath-bar", onClickLabel = UiText.themed(R.string.today_oath_explain_label, R.string.today_oath_explain_label__t).asString(),
            state = oathState,
            modifier = Modifier.weight(1f), onClick = onOath,
        )
    }
}

@Composable
private fun ProgressColumn(
    title: String,
    fraction: Float,
    value: AnnotatedString,
    fill: Brush,
    tag: String,
    barTag: String,
    onClickLabel: String,
    state: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier.heightIn(min = 48.dp).testTag(tag).clip(MaterialTheme.shapes.medium)
            .clickable(role = Role.Button, onClickLabel = onClickLabel, onClick = onClick)
            .semantics { stateDescription = state }
            .padding(vertical = 4.dp),
    ) {
        Text(
            title, fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium,
            color = IronvellumColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        InkRail(fraction.coerceIn(0f, 1f), Modifier.testTag(barTag), height = 4.dp, fill = fill)
        Spacer(Modifier.height(6.dp))
        // Two lines: at a large text size a deed's name ("Hundred-Day Promise") would otherwise be cut to its first word.
        Text(value, style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** The week rail's gold seal for a day whose rite is sealed: a ring on a faint wash and a tick, one gold gradient. */
@Composable
internal fun SealMark(side: Dp, modifier: Modifier = Modifier, earned: Boolean = true) {
    Canvas(modifier.size(side).clearAndSetSemantics {}) {
        val u = size.width / 24f
        val gold: Brush = if (earned) Metal.Fabled.bounds() else SolidColor(IronvellumColors.InkMuted)
        val c = Offset(12f * u, 12f * u)
        if (earned) drawOval(gold, Offset(1.5f * u, 1.5f * u), Size(21f * u, 21f * u), alpha = METAL_WASH, style = Fill)
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
internal fun RiteGlyph(sets: Map<Muscle, Double>, modifier: Modifier = Modifier, fit: Boolean = false) {
    val figure = BodyFigures.of(LocalBodySex.current)
    val top = sets.values.maxOrNull() ?: 0.0
    // With [fit] the figure takes the height it is offered (up to its own), keeping its proportions.
    val sized = if (fit) {
        modifier.layout { measurable, constraints ->
            val h = minOf(constraints.maxHeight, GLYPH_HEIGHT.roundToPx())
            val w = h * GLYPH_WIDTH.roundToPx() / GLYPH_HEIGHT.roundToPx()
            val placeable = measurable.measure(Constraints.fixed(w, h))
            layout(w, h) { placeable.place(0, 0) }
        }
    } else {
        modifier.size(GLYPH_WIDTH, GLYPH_HEIGHT)
    }
    Canvas(sized.clearAndSetSemantics { testTag = "today-glyph" }) {
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
    // A 58dp stamp of 9sp and 7sp words: it is a seal, not text to read, and cannot grow, so it keeps its design size.
    CappedFontScale(1f) {
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
                Words.sealed.u,
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
}

/** A closed ledger with its ribbon, for a day the cycle leaves free. Cut corners, muted ink. */
@Composable
internal fun LedgerMotif(modifier: Modifier = Modifier) {
    Canvas(modifier.size(52.dp).clearAndSetSemantics {}) {
        val u = size.width / 60f
        val cover = Path().apply {
            moveTo(14f * u, 14f * u); lineTo(42f * u, 14f * u); lineTo(42f * u, 49f * u)
            lineTo(17f * u, 49f * u); quadraticTo(14f * u, 49f * u, 14f * u, 46f * u); close()
        }
        drawPath(cover, IronvellumColors.InkMuted, style = Stroke(1.6f * u, join = StrokeJoin.Round))
        val marks = Path().apply {
            moveTo(20f * u, 14f * u); lineTo(20f * u, 49f * u)
            moveTo(25f * u, 25f * u); lineTo(30f * u, 25f * u)
            moveTo(25f * u, 30f * u); lineTo(29f * u, 30f * u)
        }
        drawPath(marks, IronvellumColors.InkMuted, alpha = 0.65f, style = Stroke(1.3f * u, cap = StrokeCap.Round))
        val ribbon = Path().apply {
            moveTo(34f * u, 14f * u); lineTo(34f * u, 42f * u); lineTo(37f * u, 39f * u)
            lineTo(40f * u, 42f * u); lineTo(40f * u, 14f * u)
        }
        drawPath(ribbon, Metal.Emerald.span(60f * u), style = Stroke(1.4f * u, join = StrokeJoin.Round))
    }
}

/**
 * The header's name line: [name], then [rank] beside it when both fit. When they do not, the rank drops to a
 * second line under the name, so neither is clipped; the name ellipsizes only when it alone is wider than the line.
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
        val fullName = measurables[0].measure(loose)
        if (fullName.width + rankP.width > constraints.maxWidth && rankP.width > 0) {
            // Both do not fit on one line: the rank link drops under the name rather than clipping it.
            layout(maxOf(fullName.width, rankP.width), fullName.height + rankP.height) {
                fullName.place(0, 0)
                rankP.place(0, fullName.height)
            }
        } else {
            val nameP = fullName
            val height = maxOf(nameP.height, rankP.height)
            layout(nameP.width + rankP.width, height) {
                nameP.place(0, (height - nameP.height) / 2)
                rankP.place(nameP.width, (height - rankP.height) / 2)
            }
        }
    }
}

/** The card title's largest and smallest size: it shrinks from one toward the other to stay on one line. */
private const val TITLE_MAX_SP = 22f
private const val TITLE_MIN_SP = 17f

/** The title at [size] sp: the tracking scales with the size, so a shrunk name keeps its proportions. */
private fun titleStyle(base: ComposeTextStyle, size: Float) =
    base.copy(fontSize = size.sp, letterSpacing = IronvellumTracking.InlineLabel * (size / TITLE_MAX_SP))

/** Room the glyph beside the title takes: its box (52dp, the sealed stamp 58dp) and the 8dp before it. */
private val CORNER_RESERVE = 70.dp

/** Beside the glyph the title may shrink this far before it moves above it to take the card's full width. */
private const val TITLE_BESIDE_MIN_SP = 19f

/**
 * The rite's name in tracked display caps on ONE line. It starts at [TITLE_MAX_SP] and steps down by half
 * a point until the whole name fits the width it is given, never below [TITLE_MIN_SP]; the tracking shrinks
 * with the size. A name that still does not fit at the minimum ends in an ellipsis rather than wrapping.
 * The line height stays that of the full size, so the header never changes height with the name.
 */
@Composable
private fun RiteTitle(title: String) {
    val text = title.uppercase()
    val measurer = rememberTextMeasurer()
    val base = MaterialTheme.typography.titleMedium.copy(fontFamily = ChakraPetch, fontWeight = FontWeight.SemiBold)
    fun styleAt(size: Float) = titleStyle(base, size)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val room = constraints.maxWidth
        var size = TITLE_MAX_SP
        while (size > TITLE_MIN_SP && measurer.measure(text, styleAt(size), maxLines = 1, softWrap = false).size.width > room) size -= 0.5f
        Text(
            text,
            style = styleAt(size).copy(lineHeight = base.lineHeight),
            color = IronvellumColors.Ink,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The least height a fitted corner glyph is drawn at: below this the figure stops reading as a body. */
private val GLYPH_MIN_HEIGHT = 44.dp

/**
 * The header's words with [corner] in the top-right. The corner is measured to the height of the words beside
 * it (never beyond its own 78dp, never below [GLYPH_MIN_HEIGHT]) so it does not leave a band under them.
 */
@Composable
private fun WordsAndCorner(words: @Composable () -> Unit, corner: @Composable () -> Unit) {
    Layout(content = { words(); corner() }, modifier = Modifier.fillMaxWidth()) { measurables, constraints ->
        val gap = 8.dp.roundToPx()
        val cornerWidth = GLYPH_WIDTH.roundToPx()
        val wordsP = measurables[0].measure(Constraints(maxWidth = (constraints.maxWidth - gap - cornerWidth).coerceAtLeast(0)))
        val cornerHeight = wordsP.height.coerceIn(GLYPH_MIN_HEIGHT.roundToPx(), GLYPH_HEIGHT.roundToPx())
        val cornerP = measurables[1].measure(Constraints(maxWidth = cornerWidth, maxHeight = cornerHeight))
        layout(constraints.maxWidth, maxOf(wordsP.height, cornerP.height)) {
            wordsP.placeRelative(0, 0)
            cornerP.placeRelative(constraints.maxWidth - cornerP.width, 0)
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
    cornerFits: Boolean = false,
) {
    val measurer = rememberTextMeasurer()
    val base = MaterialTheme.typography.titleMedium.copy(fontFamily = ChakraPetch, fontWeight = FontWeight.SemiBold)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // The name sits beside the glyph while it fits there at a good size. Otherwise it takes the card's full
        // width above the glyph, so a long name stays on one line at a larger size than the narrow column allows.
        val titleAbove = lead == null && corner != null &&
            measurer.measure(title.uppercase(), titleStyle(base, TITLE_BESIDE_MIN_SP), maxLines = 1, softWrap = false).size.width >
            constraints.maxWidth - with(androidx.compose.ui.platform.LocalDensity.current) { CORNER_RESERVE.roundToPx() }
        Column(Modifier.fillMaxWidth()) {
            if (titleAbove) RiteTitle(title)
            val words: @Composable (Modifier) -> Unit = { wordsModifier ->
                Column(wordsModifier) {
                    if (!titleAbove) RiteTitle(title)
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
                            // Three lines: "Today's workout · 5 exercises · 18 sets · ~126 min" needs them at 200% text.
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
            if (cornerFits && corner != null && lead == null) {
                WordsAndCorner(words = { words(Modifier) }, corner = corner)
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    if (lead != null) {
                        lead()
                        Spacer(Modifier.width(14.dp))
                    }
                    words(Modifier.weight(1f))
                    if (corner != null) {
                        Box(Modifier.padding(start = 8.dp).heightIn(min = 66.dp), contentAlignment = Alignment.TopEnd) { corner() }
                    }
                }
            }
        }
    }
}
