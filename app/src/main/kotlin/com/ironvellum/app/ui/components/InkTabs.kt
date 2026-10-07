package com.ironvellum.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import com.ironvellum.app.ui.theme.DotShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.ironvellum.app.ui.theme.InkPressIndication
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.IronvellumTracking

private val TabPad = 10.dp

/** Four tabs (Allies) need the tighter padding to fit a 360dp phone. */
private val CrowdedTabPad = 6.dp

/** Where the visible strip ends inside the 48dp touch row: the hairline and the underline sit on this line. */
private val RuleY = 40.dp

/**
 * The app's tab strip, for switching between VIEWS or PAGES: small caps labels, left-aligned and
 * sized to their text, a hairline under the strip and a 2dp green underline under the open tab.
 * Light on purpose, so it stays secondary to the title above it.
 *
 * A tab shows a different page; a picker that chooses an option is [InkSegmented] instead.
 *
 * [indicatorPosition] is the underline's fractional tab position, e.g. a pager's
 * `currentPage + currentPageOffsetFraction`, so it slides under the finger mid-swipe. Null (the
 * default) animates the underline to [selectedIndex] on its own.
 * [badges] is a count per tab (same order as [labels]); above 0 it draws a red count at the
 * label's corner without widening the tab.
 *
 * Each tab is a 48dp tall touch target (Role.Tab, selected, "2 of 3") around about 32dp of ink.
 * The strip is pulled left by the tab padding so the first label lines up with the screen's own
 * margin; place it inside that margin and let the first tab's touch area spill into it.
 */
@Composable
fun InkTabs(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    badges: List<Int> = emptyList(),
    indicatorPosition: (() -> Float)? = null,
) {
    val tabPad = if (labels.size > 3) CrowdedTabPad else TabPad
    val widths = remember(labels.size) { mutableStateListOf(*Array(labels.size) { 0 }) }
    val settled = animateFloatAsState(selectedIndex.toFloat(), label = "inkTabsIndicator")
    Box(modifier.fillMaxWidth().height(LedgerSpace.Target)) {
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = LedgerSpace.Target - RuleY)
                .fillMaxWidth()
                .height(1.dp)
                .background(IronvellumColors.Rune),
        )
        Row(
            Modifier
                .offset(x = -tabPad)
                .drawBehind {
                    if (labels.size < 2 || widths.any { it == 0 }) return@drawBehind
                    val pos = (indicatorPosition?.invoke() ?: settled.value).coerceIn(0f, labels.lastIndex.toFloat())
                    val from = pos.toInt().coerceAtMost(labels.lastIndex - 1)
                    val t = pos - from
                    val startFrom = widths.take(from).sum().toFloat()
                    val startTo = startFrom + widths[from]
                    val pad = tabPad.toPx()
                    // A plain rect, not inkHairline: that one re-rolls its wobble as the width changes.
                    val left = lerp(startFrom, startTo, t) + pad
                    val right = lerp(startFrom + widths[from], startTo + widths[from + 1], t) - pad
                    val thick = 2.dp.toPx()
                    drawRect(IronvellumColors.Emerald, Offset(left, RuleY.toPx() - thick), Size(right - left, thick))
                },
        ) {
            labels.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                val badge = badges.getOrElse(index) { 0 }
                Box(
                    Modifier
                        .heightIn(min = LedgerSpace.Target)
                        .onSizeChanged { widths[index] = it.width }
                        .selectable(
                            selected = selected,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = InkPressIndication,
                            role = Role.Tab,
                            onClick = { onSelect(index) },
                        )
                        .semantics {
                            val unread = if (badge > 0) ", $badge unread" else ""
                            stateDescription = "${if (selected) "Selected" else "Not selected"}, ${index + 1} of ${labels.size}$unread"
                        }
                        .padding(horizontal = tabPad)
                        .padding(bottom = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = ChakraPetch,
                        // One weight for both states, so selecting never changes a tab's width.
                        fontWeight = FontWeight.SemiBold,
                        color = if (selected) IronvellumColors.Ink else IronvellumColors.InkMuted,
                        letterSpacing = IronvellumTracking.InlineLabel,
                        maxLines = 1,
                        softWrap = false,
                    )
                    if (badge > 0) {
                        Text(
                            if (badge > 99) "99+" else badge.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = ChakraPetch,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            letterSpacing = 0.sp,
                            color = IronvellumColors.Ink,
                            maxLines = 1,
                            softWrap = false,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 4.dp)
                                .background(IronvellumColors.DangerRed, DotShape)
                                .defaultMinSize(minWidth = 14.dp, minHeight = 14.dp)
                                .padding(horizontal = 3.dp, vertical = 1.dp)
                                // Announced through the tab's stateDescription instead.
                                .clearAndSetSemantics { },
                        )
                    }
                }
            }
        }
    }
}
