package com.ironvellum.app.ui.social

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironvellum.app.ui.components.InkChip
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.inkBorder

/**
 * Ally state beside a name. The one offer ("Add ally") is an [InkChip]; settled states
 * ("Pending", "Ally") draw the same chip shape in InkMuted and take no tap, so they never
 * read as a button. The row keeps the chip's 48dp height either way.
 */
@Composable
internal fun AllyChip(label: String, tappable: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    if (tappable) {
        InkChip(label = label, icon = Icons.Outlined.PersonAdd, modifier = modifier, onClick = onClick)
        return
    }
    val shape = MaterialTheme.shapes.extraSmall
    Box(
        modifier.padding(start = 8.dp).heightIn(min = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .height(32.dp)
                .clip(shape)
                .inkBorder(IronvellumColors.Rune, shape, 1.dp)
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = IronvellumColors.InkMuted, letterSpacing = 0.5.sp)
        }
    }
}
