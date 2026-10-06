package com.ironvellum.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ironvellum.app.ui.theme.IronvellumColors

/**
 * A short section label over one InkPanel: the grouping Settings and Account
 * both use. A null [label] is a panel with no heading.
 */
@Composable
fun SettingsGroup(
    label: String?,
    modifier: Modifier = Modifier,
    topSpace: Dp = 20.dp,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    if (label != null) {
        SectionHeader(label, topPadding = topSpace)
    } else {
        Spacer(Modifier.height(topSpace))
    }
    InkPanel(modifier.fillMaxWidth(), contentPadding = contentPadding, content = content)
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
    ListRow(label, value = value, onClickLabel = "Open $label", onClick = onClick)
}

/** The one-line explanation under a setting: what it changes, nothing more. */
@Composable
fun SettingsCaption(text: String, color: androidx.compose.ui.graphics.Color = IronvellumColors.InkMuted) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = color)
}
