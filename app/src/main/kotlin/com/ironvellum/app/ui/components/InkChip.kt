package com.ironvellum.app.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.ironvellum.app.ui.theme.InkPressIndication
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ironvellum.app.ui.theme.IronvellumColors

/**
 * The app's one chip, a raised neutral button: a solid [IronvellumColors.Raised] fill (a step off both the page and a
 * card in either theme, no outline), `labelLarge` in Ink, 36dp drawn inside a 48dp tap target, an optional leading
 * icon tinted in the accent. It is how a header offers a secondary action ("Open trial", "History", BACK) and never
 * outweighs the screen's one filled button.
 *
 * [selected] turns it into a choice chip: null (the default) is a plain button; true washes the fill with the accent
 * and shows a check where an icon would be, false is the plain fill, and either is announced as a selected state.
 * The press wash is drawn on the fill, not on the invisible margin around it.
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
    /** The drawn height; 36dp by default, 48dp where the chip is the one control a card is for. */
    height: androidx.compose.ui.unit.Dp = 36.dp,
    selected: Boolean? = null,
    onClick: () -> Unit,
) {
    val press = remember { MutableInteractionSource() }
    val shape = MaterialTheme.shapes.medium
    val fill = if (selected == true) IronvellumColors.Emerald.copy(alpha = 0.28f).compositeOver(IronvellumColors.Raised) else IronvellumColors.Raised
    Box(
        modifier
            .padding(start = 8.dp)
            .heightIn(min = 48.dp)
            .clickable(interactionSource = press, indication = null, role = Role.Button, onClickLabel = clickLabel, onClick = onClick)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
            .then(if (selected != null) Modifier.semantics { this.selected = selected } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .height(height)
                .clip(shape)
                .background(fill)
                .indication(press, InkPressIndication)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            (icon ?: Icons.Filled.Check.takeIf { selected == true })?.let {
                Icon(it, contentDescription = null, tint = IronvellumColors.SystemGreen, modifier = Modifier.size(18.dp))
            }
            Text(label, style = MaterialTheme.typography.labelLarge, color = IronvellumColors.Ink)
        }
    }
}
