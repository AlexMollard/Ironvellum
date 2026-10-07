package com.ironvellum.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ironvellum.app.ui.theme.IronvellumColors

/** The height of every list row; a row with a subline grows past it. */
val ListRowHeight: Dp = 52.dp

/**
 * The app's one list row: an optional leading icon, the label in `bodyMedium` Ink over an
 * optional `bodySmall` [subline], a trailing [value] in `labelMedium` InkMuted and, when the
 * row opens something, an InkMuted chevron. 52dp, growing with its subline.
 * Rows are separated by [InkDivider], not boxed.
 */
@Composable
fun ListRow(
    label: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    subline: String? = null,
    icon: ImageVector? = null,
    sublineColor: Color = IronvellumColors.InkMuted,
    onClickLabel: String? = null,
    contentPadding: PaddingValues = LocalRowPadding.current,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick) else Modifier)
            .heightIn(min = ListRowHeight)
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        icon?.let { Icon(it, contentDescription = null, tint = IronvellumColors.InkMuted, modifier = Modifier.size(22.dp)) }
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.Ink)
            if (subline != null) Text(subline, style = MaterialTheme.typography.bodySmall, color = sublineColor)
        }
        if (value != null) {
            Text(
                value,
                style = MaterialTheme.typography.labelMedium,
                color = IronvellumColors.InkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        if (onClick != null) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = IronvellumColors.InkMuted)
        }
    }
}
