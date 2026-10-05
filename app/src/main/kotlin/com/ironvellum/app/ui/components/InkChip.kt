package com.ironvellum.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.inkBorder

/**
 * A section's action as an outlined chip: it reads as something to press,
 * where a green caption read as one more label, and it never outweighs the
 * screen's one filled button.
 */
@Composable
fun InkChip(
    label: String,
    clickLabel: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    description: String? = null,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .padding(start = 8.dp)
            .clip(MaterialTheme.shapes.extraSmall)
            .clickable(onClickLabel = clickLabel, onClick = onClick)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
            .heightIn(min = 44.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .inkBorder(IronvellumColors.Rune, MaterialTheme.shapes.extraSmall, 1.dp)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            icon?.let { Icon(it, contentDescription = null, tint = IronvellumColors.SystemGreen, modifier = Modifier.size(16.dp)) }
            Text(label, style = MaterialTheme.typography.labelMedium, color = IronvellumColors.SystemGreen, letterSpacing = 0.5.sp)
        }
    }
}
