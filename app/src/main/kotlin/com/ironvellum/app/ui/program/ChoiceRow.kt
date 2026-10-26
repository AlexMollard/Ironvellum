package com.ironvellum.app.ui.program

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ironvellum.app.ui.theme.IronvellumColors

/**
 * One choice of a radio group as a row: the ring, a [title] and the one line under it that says what it does. The chosen
 * row carries the thin Emerald bar and faint wash every active row has. Meant to sit inside a `selectableGroup`; it
 * reads title then caption to a screen reader, and 48dp is its least height.
 */
@Composable
internal fun ChoiceRow(title: String, caption: String, selected: Boolean, onSelect: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .background(if (selected) IronvellumColors.Ink.copy(alpha = 0.05f) else Color.Transparent)
            .drawBehind {
                if (selected) drawRect(IronvellumColors.Emerald, Offset.Zero, Size(2.dp.toPx(), size.height))
            }
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .semantics(mergeDescendants = true) { contentDescription = "$title. $caption" }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioMark(selected)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = IronvellumColors.Ink)
            Text(caption, style = MaterialTheme.typography.labelSmall, color = IronvellumColors.InkMuted)
        }
    }
}
