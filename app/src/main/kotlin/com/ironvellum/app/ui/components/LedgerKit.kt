package com.ironvellum.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.IronvellumTracking
import com.ironvellum.app.ui.theme.inkBorder

/**
 * The Ledger's layout and type tokens, in one place so a screen cannot invent
 * a seventeenth spacing. Panels sit [Panel] apart, sections [Section] apart,
 * and nothing tappable is shorter than [Target].
 */
object LedgerSpace {
    val Gutter = 16.dp
    val Panel = 12.dp
    val Section = 24.dp
    val RowV = 12.dp
    val Target = 48.dp
}

/** Three value sizes, no more: one hero per screen, tiles, and inline figures. */
enum class StatSize(val sp: Int) { Hero(34), Tile(26), Inline(20) }

/**
 * A figure. Ink by default: value colour carries meaning (green for a gain,
 * gold only for a record), so a plain number is never coloured for decoration.
 */
@Composable
fun StatValue(
    text: String,
    modifier: Modifier = Modifier,
    size: StatSize = StatSize.Tile,
    color: Color = IronvellumColors.Ink,
    unit: String? = null,
) {
    Row(modifier, verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text,
            style = MaterialTheme.typography.displaySmall.copy(
                fontSize = size.sp.sp,
                lineHeight = (size.sp * 1.15f).sp,
            ),
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
        )
        if (unit != null) {
            Text(
                unit,
                style = MaterialTheme.typography.titleMedium,
                color = IronvellumColors.InkMuted,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
    }
}

/** The one small label above a figure or a panel: muted, tracked, and a heading for screen readers. */
@Composable
fun PanelLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontFamily = ChakraPetch,
        color = IronvellumColors.InkMuted,
        letterSpacing = IronvellumTracking.InlineLabel,
        modifier = modifier.semantics { heading() },
    )
}

/** A 1dp rule between rows of one panel. */
@Composable
fun InkDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(IronvellumColors.Rune))
}

/**
 * One row inside a panel: muted label, ink value, and a green chevron when it
 * opens something. Rows are divided by [InkDivider] inside a single InkPanel
 * rather than each being a bordered box.
 */
@Composable
fun InkListRow(
    label: String,
    value: String?,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = LedgerSpace.Target)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = IronvellumColors.InkMuted,
            )
            if (supporting != null) {
                Text(supporting, style = MaterialTheme.typography.labelSmall, color = IronvellumColors.InkMuted)
            }
        }
        if (value != null) {
            Text(
                value,
                style = MaterialTheme.typography.titleSmall,
                fontFamily = ChakraPetch,
                color = IronvellumColors.Ink,
                maxLines = 1,
            )
        }
        if (onClick != null) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = IronvellumColors.SystemGreen,
            )
        }
    }
}

/** Small range switch (30D / 90D / ALL): every option a full 48dp target, state announced. */
@Composable
fun <T> RangeChips(
    options: List<Pair<T, String>>,
    selected: T,
    onPick: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.extraSmall
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { (value, label) ->
            val on = value == selected
            Box(
                Modifier
                    .heightIn(min = LedgerSpace.Target)
                    .widthIn(min = LedgerSpace.Target)
                    .clip(shape)
                    .then(if (on) Modifier.inkBorder(IronvellumColors.SystemGreen, shape, 1.dp) else Modifier)
                    .clickable(role = Role.RadioButton) { onPick(value) }
                    .semantics { this.selected = on }
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = ChakraPetch,
                    color = if (on) IronvellumColors.SystemGreen else IronvellumColors.InkMuted,
                    letterSpacing = IronvellumTracking.InlineLabel,
                )
            }
        }
    }
}
