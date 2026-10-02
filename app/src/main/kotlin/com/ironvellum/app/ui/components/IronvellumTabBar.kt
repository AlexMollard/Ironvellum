package com.ironvellum.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.inkBorder

/** One segment of an [IronvellumTabBar]; [badge] > 0 draws a count in the corner. */
data class IronvellumTabItem(val label: String, val badge: Int = 0)

/**
 * A fixed, non-scrolling tab bar: one ink-edged trough split into equal segments,
 * the open one filled green. Sized to fit four short caps labels on a 360dp phone
 * (RECKONING, the longest, is about 5.6em wide, so 11sp with 1sp tracking leaves
 * about 5dp spare per segment). Badges overlay the segment corner and never widen it.
 *
 * Prefer this to a row of [IronvellumTabPill] for primary tabs; a pill row scrolls
 * sideways as soon as a label grows.
 */
@Composable
fun IronvellumTabBar(
    items: List<IronvellumTabItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /** Adds "2 of 4" to each tab's state, for tabs that page content rather than switch screens. */
    announcePosition: Boolean = false,
) {
    val shape = MaterialTheme.shapes.small
    val inner = MaterialTheme.shapes.extraSmall
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF141A18), Color(0xFF0E1312))))
            .inkBorder(IronvellumColors.Rune, shape, 1.dp)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items.forEachIndexed { index, item ->
            val selected = index == selectedIndex
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(inner)
                    .then(
                        if (selected) {
                            Modifier
                                .background(
                                    Brush.verticalGradient(
                                        listOf(IronvellumColors.SystemGreen, IronvellumColors.Emerald),
                                    ),
                                )
                                .inkBorder(IronvellumColors.EmeraldBright, inner, 1.dp)
                        } else {
                            Modifier
                        },
                    )
                    .selectable(
                        selected = selected,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Tab,
                        onClick = { onSelect(index) },
                    )
                    .semantics {
                        val unread = if (item.badge > 0) "${item.badge} unread" else null
                        val place = if (announcePosition) "${if (selected) "Selected" else "Not selected"}, ${index + 1} of ${items.size}" else null
                        val state = listOfNotNull(place, unread).joinToString(", ")
                        if (state.isNotEmpty()) stateDescription = state
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    item.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = ChakraPetch,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp,
                    color = if (selected) IronvellumColors.Abyss else IronvellumColors.InkMuted,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                )
                if (item.badge > 0) {
                    Text(
                        if (item.badge > 99) "99+" else item.badge.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = ChakraPetch,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        letterSpacing = 0.sp,
                        color = IronvellumColors.Ink,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 3.dp, end = 3.dp)
                            .background(IronvellumColors.DangerRed, shape = inner)
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                            // Announced through the segment's stateDescription instead.
                            .clearAndSetSemantics { },
                    )
                }
            }
        }
    }
}
