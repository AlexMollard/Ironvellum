package com.ironvellum.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.inkBorder

/**
 * The app's one chip: a 1dp straight outline in SystemGreen at half strength, `labelMedium`,
 * 32dp drawn inside a 48dp tap target, an optional leading icon. It is how a header offers a
 * secondary action ("Open trial", "History", BACK) and never outweighs the screen's one filled
 * button.
 *
 * The press ripple is drawn on the outline, not on the invisible margin around it.
 * [clickLabel] is what a screen reader says pressing does; [description] replaces the spoken
 * label for a chip whose text alone says too little ("All").
 */
@Composable
fun InkChip(
    label: String,
    clickLabel: String? = null,
    icon: ImageVector? = null,
    description: String? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val press = remember { MutableInteractionSource() }
    val shape = MaterialTheme.shapes.extraSmall
    Box(
        modifier
            .padding(start = 8.dp)
            .heightIn(min = 48.dp)
            .clickable(interactionSource = press, indication = null, role = Role.Button, onClickLabel = clickLabel, onClick = onClick)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .height(32.dp)
                .clip(shape)
                .inkBorder(IronvellumColors.SystemGreen.copy(alpha = 0.55f), shape, 1.dp)
                .indication(press, ripple())
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            icon?.let { Icon(it, contentDescription = null, tint = IronvellumColors.SystemGreen, modifier = Modifier.size(16.dp)) }
            Text(label, style = MaterialTheme.typography.labelMedium, color = IronvellumColors.SystemGreen, letterSpacing = 0.5.sp)
        }
    }
}
