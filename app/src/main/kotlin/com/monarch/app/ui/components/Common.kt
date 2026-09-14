package com.monarch.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CutCornerShape
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
import java.time.Instant
import java.time.ZoneId
import com.monarch.app.ui.theme.MonarchTracking
import java.time.format.DateTimeFormatter

private val WindowFill = Brush.verticalGradient(
    listOf(Color(0xFF191B20), Color(0xFF101216)),
)

/**
 * A Solo-Leveling "system window": cut-corner panel with calm structural
 * brackets and a faint green energy fill. The most reused surface in Monarch.
 */
@Composable
fun SystemWindow(
    modifier: Modifier = Modifier,
    accent: Color = MonarchColors.Rune,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.small
    val border = BorderStroke(1.dp, accent)
    val body: @Composable () -> Unit = {
        Column(
            Modifier
                .fillMaxWidth()
                .drawBehind {
                    val tick = 10.dp.toPx()
                    val stroke = 2.5.dp.toPx()
                    val bracket = MonarchColors.Bracket
                    drawLine(bracket, Offset(0f, tick), Offset(0f, 0f), stroke, StrokeCap.Round)
                    drawLine(bracket, Offset(0f, 0f), Offset(tick, 0f), stroke, StrokeCap.Round)
                    drawLine(bracket, Offset(size.width, size.height - tick), Offset(size.width, size.height), stroke, StrokeCap.Round)
                    drawLine(bracket, Offset(size.width, size.height), Offset(size.width - tick, size.height), stroke, StrokeCap.Round)
                }
                .padding(16.dp),
            content = content,
        )
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            shape = shape,
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = border,
            modifier = modifier.background(WindowFill, shape),
        ) {
            body()
        }
    } else {
        Surface(
            shape = shape,
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = border,
            modifier = modifier.background(WindowFill, shape),
        ) {
            body()
        }
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(top = 28.dp, bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("\u25E0", color = MonarchColors.SovereignGold, fontSize = 12.sp)
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
 * Level energy meter: one continuous cut-corner track, emerald-to-amber fill
 * with a bright leading edge, hairline notches over the fill (not chunky gaps)
 * and the count read out inside the bar so the number always has context.
 */
@Composable
fun XpBar(into: Long, needed: Long, modifier: Modifier = Modifier) {
    val fraction = if (needed <= 0) 0f else (into.toFloat() / needed).coerceIn(0f, 1f)
    val animated by animateFloatAsState(fraction, tween(900), label = "xpFill")
    val shape = CutCornerShape(topStart = 5.dp, bottomEnd = 5.dp)

    Box(
        modifier
            .fillMaxWidth()
            .height(20.dp)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF121A16), Color(0xFF0B100E))))
            .border(1.dp, MonarchColors.Rune, shape),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val fillW = size.width * animated
            if (fillW > 0f) {
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color(0xFF1E6B4F), Color(0xFF34D399), MonarchColors.SovereignGold),
                        startX = 0f,
                        endX = size.width,
                    ),
                    size = androidx.compose.ui.geometry.Size(fillW, size.height),
                )
                // leading edge glow so progress reads as live energy
                drawRect(
                    color = Color.White,
                    topLeft = Offset((fillW - 2.5f).coerceAtLeast(0f), 0f),
                    size = androidx.compose.ui.geometry.Size(2.5f, size.height),
                    alpha = 0.55f,
                )
            }
            // hairline notches, drawn over everything for a HUD read
            val notches = 10
            repeat(notches - 1) { i ->
                val x = size.width * (i + 1) / notches
                drawLine(
                    color = Color(0xFF060908),
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 1.2f,
                    alpha = 0.75f,
                )
            }
        }
        // dark plate keeps the count legible wherever the fill edge lands
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 4.dp)
                .background(Color(0xCC070B09), CutCornerShape(topStart = 4.dp))
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
        listOf(Color(0xFF34D399), Color(0xFF0EA5A5))
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
