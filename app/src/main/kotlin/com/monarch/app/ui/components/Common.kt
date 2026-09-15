package com.monarch.app.ui.components

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import com.monarch.app.ui.theme.inkArc
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monarch.app.ui.theme.ChakraPetch
import com.monarch.app.ui.theme.MonarchColors
import androidx.compose.ui.unit.Dp
import com.monarch.app.ui.theme.inkRail
import com.monarch.app.ui.theme.inkHairline
import com.monarch.app.ui.theme.inkBorder
import com.monarch.app.ui.theme.inkTick
import com.monarch.app.ui.theme.paperGrain
import com.monarch.app.ui.theme.rememberInkShape
import java.time.Instant
import java.time.ZoneId
import com.monarch.app.ui.theme.MonarchTracking
import java.time.format.DateTimeFormatter

// Warm charcoal rather than the old blue-grey: ink sits on paper, and the
// paper is what the fill represents.
private val WindowFill = Brush.verticalGradient(
    listOf(Color(0xFF1A1A18), Color(0xFF111110)),
)

/**
 * The app's primary surface, drawn as ink on paper: a hand-drawn edge, paper
 * grain, and tapered brush ticks where the old HUD had hairline brackets.
 *
 * The most reused surface in Monarch (~90 call sites), which is exactly why the
 * ink treatment lives HERE and not in the screens - every screen inherits it
 * and none of them can drift.
 */
@Composable
fun SystemWindow(
    modifier: Modifier = Modifier,
    accent: Color = MonarchColors.Rune,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Salt the wobble per accent so neighbouring panels are not traced from the
    // same hand-drawn outline.
    val shape = rememberInkShape(accent.hashCode())
    val body: @Composable () -> Unit = {
        Column(
            Modifier
                .fillMaxWidth()
                .drawBehind {
                    val tick = 12.dp.toPx()
                    val stroke = 3.dp.toPx()
                    // Accent tints only the brush ticks. Driving the whole
                    // outline with it turned an accent panel into a thick neon
                    // frame once the ink border gained its bleed pass.
                    inkTick(Offset(0f, tick), Offset(0f, 0f), accent, stroke)
                    inkTick(Offset(0f, 0f), Offset(tick, 0f), accent, stroke)
                    inkTick(
                        Offset(size.width, size.height - tick),
                        Offset(size.width, size.height),
                        accent,
                        stroke,
                    )
                    inkTick(
                        Offset(size.width, size.height),
                        Offset(size.width - tick, size.height),
                        accent,
                        stroke,
                    )
                }
                .padding(16.dp),
            content = content,
        )
    }
    val surfaceModifier = modifier
        .background(WindowFill, shape)
        .paperGrain(accent.hashCode())
        // Structure is always ink; the panel's identity comes from its ticks.
        .inkBorder(MonarchColors.Rune, shape)
    if (onClick != null) {
        Surface(
            onClick = onClick,
            shape = shape,
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = surfaceModifier,
        ) {
            body()
        }
    } else {
        Surface(
            shape = shape,
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = surfaceModifier,
        ) {
            body()
        }
    }
}

/**
 * The tab pill used by every hub (Guild, Codex, and anything added later).
 *
 * There were two private copies of this, and they had already drifted: one drew
 * a gradient with a border, the other a flat fill - while its own comment
 * claimed the two hubs "read as siblings". Converting both to the ink shape
 * would have preserved that lie, so they share one implementation instead.
 */
@Composable
fun MonarchTabPill(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    art: Int? = null,
    onClick: () -> Unit,
) {
    val shape = MaterialTheme.shapes.small
    Row(
        modifier
            .background(
                if (selected) {
                    Brush.verticalGradient(
                        listOf(MonarchColors.SystemGreen, MonarchColors.Emerald),
                    )
                } else {
                    Brush.verticalGradient(listOf(Color(0xFF141A18), Color(0xFF0E1312)))
                },
                shape,
            )
            .border(1.dp, if (selected) MonarchColors.EmeraldBright else MonarchColors.Rune, shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() }
            .padding(horizontal = 13.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (art != null) {
            Icon(
                painter = painterResource(art),
                contentDescription = null,
                tint = if (selected) MonarchColors.Abyss else MonarchColors.InkMuted,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = if (selected) MonarchColors.Abyss else MonarchColors.InkMuted,
            letterSpacing = MonarchTracking.InlineLabel,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * The app's progress rail, drawn as ink.
 *
 * There were four near-identical hand-rolled versions of this - Codex deed
 * progress, the dashboard factor bars, the leaderboard intensity bar and the
 * idle taper - each a track Box with a fraction-width Box inside. They are one
 * component now, so a rail cannot look machined on one screen and drawn on
 * another.
 */
@Composable
fun InkRail(
    fraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
    track: Color = MonarchColors.Rune,
    fill: Brush = Brush.horizontalGradient(
        listOf(MonarchColors.SystemGreen, MonarchColors.SovereignGold),
    ),
    seed: Int = 0,
) {
    Canvas(modifier.fillMaxWidth().height(height)) {
        inkRail(fraction = fraction, track = track, fill = fill, seed = seed)
    }
}
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(top = 28.dp, bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // Ink, not gold: a section marker decorates, it does not report
            // anything earned, and gold is reserved for what is.
            Text("\u25E0", color = MonarchColors.Bracket, fontSize = 12.sp)
            Text(
                text = text.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                fontFamily = ChakraPetch,
                color = MonarchColors.InkMuted,
                letterSpacing = MonarchTracking.SectionHeader,
            )
        }
    }
}

/**
 * Level energy meter, drawn as ink.
 *
 * Was a HUD read: a cut-corner track with ten hairline notches ruled across it.
 * Notches are the most machine-made mark in the app - perfectly even, perfectly
 * vertical - so they are gone, and the fill is the brushed rail every other
 * progress bar uses. The count still reads out inside the bar.
 */
@Composable
fun XpBar(into: Long, needed: Long, modifier: Modifier = Modifier) {
    val fraction = if (needed <= 0) 0f else (into.toFloat() / needed).coerceIn(0f, 1f)
    val animated by animateFloatAsState(fraction, tween(900), label = "xpFill")
    val shape = MaterialTheme.shapes.small

    Box(
        modifier
            .fillMaxWidth()
            .height(20.dp)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF121A16), Color(0xFF0B100E))))
            .inkBorder(MonarchColors.Rune, shape),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            inkRail(
                fraction = animated,
                track = Color.Transparent,
                fill = Brush.horizontalGradient(
                    colors = listOf(Color(0xFF1E6B4F), Color(0xFF34D399), MonarchColors.SovereignGold),
                    startX = 0f,
                    endX = size.width,
                ),
                seed = 3,
            )
        }
        // dark plate keeps the count legible wherever the fill edge lands
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 4.dp)
                .background(Color(0xCC070B09), MaterialTheme.shapes.extraSmall)
                .padding(horizontal = 6.dp, vertical = 1.dp),
        ) {
            Text(
                "$into / $needed XP",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.Bold,
                color = MonarchColors.Ink,
                letterSpacing = MonarchTracking.InlineLabel,
            )
        }
    }
}


/** Primary action: emerald-teal gradient button with press feedback. */
@Composable
fun MonarchButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    gold: Boolean = false,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, tween(90), label = "press")
    val colors = if (gold) {
        listOf(Color(0xFFF2C14E), Color(0xFFC98A2B))
    } else {
        // Moss, not teal: 0xFF0EA5A5 read as cyan on device and broke the
        // warm-green palette rule the rest of the app follows.
        listOf(Color(0xFF34D399), Color(0xFF2E7D55))
    }
    val shape = MaterialTheme.shapes.small
    Box(
        modifier
            .scale(scale)
            .clip(shape)
            .background(
                if (enabled) Brush.linearGradient(colors)
                else Brush.linearGradient(listOf(Color(0xFF1E3026), Color(0xFF16211B))),
            )
            .border(1.dp, if (enabled) Color(0x5934D399) else MonarchColors.Rune, shape)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label.uppercase(),
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            // The fills are bright emerald/gold: ink-dark type is the only
            // readable choice. Inheriting the theme colour rendered grey-on-gold.
            color = if (enabled) MonarchColors.Abyss else MonarchColors.InkMuted,
            style = MaterialTheme.typography.labelLarge,
            letterSpacing = MonarchTracking.InlineLabel,
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 18.dp),
        )
    }
}

fun formatDate(ms: Long, pattern: String = "MMM d · HH:mm"): String =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(pattern))

/**
 * Busy indicator drawn as a brushed arc instead of Material's perfect ring.
 *
 * The stock CircularProgressIndicator is a compass-struck circle with an even
 * stroke - the last machine-exact mark in the app once every other surface was
 * inked. The sweep rotates; the stroke itself is a single brushed arc.
 */
@Composable
fun InkSpinner(
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    color: Color = MonarchColors.Emerald,
) {
    val spin = rememberInfiniteTransition(label = "spin")
    val angle by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "spinAngle",
    )
    // progressSemantics() is what Material's indeterminate indicator carries;
    // a bare Canvas announces nothing to a screen reader.
    Canvas(modifier.size(size).progressSemantics()) {
        val r = this.size.minDimension / 2f - 2.dp.toPx()
        inkArc(
            center = Offset(this.size.width / 2f, this.size.height / 2f),
            radius = r,
            startDeg = angle,
            sweepDeg = 250f,
            color = color,
            widthPx = 2.5.dp.toPx(),
            seed = 67,
        )
    }
}
