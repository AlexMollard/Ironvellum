package com.ironvellum.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.IronvellumTracking

/**
 * A short section label over one InkPanel: the grouping Settings and Account
 * both use. A null [label] is a panel with no heading.
 */
@Composable
fun SettingsGroup(
    label: String?,
    modifier: Modifier = Modifier,
    topSpace: Dp = 20.dp,
    accent: androidx.compose.ui.graphics.Color = IronvellumColors.Rune,
    content: @Composable ColumnScope.() -> Unit,
) {
    Spacer(Modifier.height(topSpace))
    if (label != null) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = ChakraPetch,
            color = IronvellumColors.SystemGreen,
            letterSpacing = IronvellumTracking.InlineLabel,
        )
        Spacer(Modifier.height(8.dp))
    }
    InkPanel(modifier.fillMaxWidth(), accent = accent, content = content)
}

/** One tappable settings row, at least 48dp tall. */
@Composable
fun TapRow(onClickLabel: String, onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(MaterialTheme.shapes.extraSmall)
            .clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/** A hub row: what it is, its current value, and a chevron to the screen that changes it. */
@Composable
fun SettingsValueRow(label: String, value: String?, onClick: () -> Unit) {
    TapRow(onClickLabel = "Open $label", onClick = onClick) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            fontFamily = ChakraPetch,
            color = IronvellumColors.Ink,
            maxLines = 1,
        )
        Text(
            value.orEmpty(),
            style = MaterialTheme.typography.labelMedium,
            color = IronvellumColors.InkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = IronvellumColors.InkMuted,
        )
    }
}

/** The one-line explanation under a setting: what it changes, nothing more. */
@Composable
fun SettingsCaption(text: String, color: androidx.compose.ui.graphics.Color = IronvellumColors.InkMuted) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = color)
}
