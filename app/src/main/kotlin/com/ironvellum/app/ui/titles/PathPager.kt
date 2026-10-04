package com.ironvellum.app.ui.titles

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ironvellum.app.domain.Skills
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.inkBorder
import kotlinx.coroutines.flow.drop

/** What a path chip reads out loud: the name, how far along it is, and whether it is the open one. */
internal fun chipDescription(line: String, done: Int, total: Int): String = "$line path, $done of $total mastered"

/**
 * An open path: a strip of every path above a pager of their trees. The strip and a horizontal
 * swipe are two ways to the same move, so [line] stays the single source of truth: a swipe that
 * settles reports the new path through [onLine], and a changed [line] (a chip, a jump to another
 * path's technique) brings the pager to it. Swiping past the first or last path is handed on to
 * the Codex pager around this one, which is how Compose nests pagers.
 *
 * Each path keeps its own vertical scroll, saved by path name. [focus] and [onFocusHandled] are
 * [SkillTreeGraph]'s scroll-to-skill seam, offered to every composed page.
 */
@Composable
internal fun PathPager(
    line: String,
    onLine: (String) -> Unit,
    mastered: Set<String>,
    onSelect: (String) -> Unit,
    onOpenPrerequisite: (String) -> Unit,
    focus: String?,
    onFocusHandled: () -> Unit,
    best: Map<String, SkillGuidance.Effort>,
    bodyweightKg: Double?,
    female: Boolean,
    modifier: Modifier = Modifier,
) {
    val lines = Skills.LINES
    val pager = rememberPagerState(initialPage = lines.indexOf(line).coerceAtLeast(0)) { lines.size }
    val report by rememberUpdatedState(onLine)

    // A path asked for from outside: instant when the pager is simply being set up on it,
    // animated when the lifter is already looking at another path.
    val placed = remember { booleanArrayOf(false) }
    LaunchedEffect(line) {
        val target = lines.indexOf(line)
        if (target < 0 || target == pager.currentPage) {
            placed[0] = true
        } else if (placed[0]) {
            pager.animateScrollToPage(target)
        } else {
            placed[0] = true
            pager.scrollToPage(target)
        }
    }
    // The page the pager settles on is the open path. The first value is the pager's own start.
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.drop(1).collect { report(lines[it]) }
    }

    Column(modifier) {
        PathChips(
            selected = line,
            mastered = mastered,
            onPick = onLine,
        )
        HorizontalPager(
            state = pager,
            key = { lines[it] },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { page ->
            val pageLine = lines[page]
            // Saved with the page's key, so a path's scroll outlives the page leaving composition.
            val scroll = rememberScrollState()
            Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(6.dp))
                SkillTreeGraph(
                    line = pageLine,
                    mastered = mastered,
                    onSelect = onSelect,
                    modifier = Modifier.fillMaxWidth(),
                    best = best,
                    bodyweightKg = bodyweightKg,
                    female = female,
                    onOpenPrerequisite = onOpenPrerequisite,
                    focus = focus,
                    onFocusHandled = onFocusHandled,
                    pageScroll = scroll,
                    treeTopInPage = 6.dp,
                )
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

/** Every path as a chip - "Pull 5/11" - in a strip that scrolls sideways; the open one is marked. */
@Composable
internal fun PathChips(
    selected: String,
    mastered: Set<String>,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lines = Skills.LINES
    val strip = rememberLazyListState()
    val selectedIndex = lines.indexOf(selected)
    // Keep the open path's chip on screen as swipes move it along.
    LaunchedEffect(selectedIndex) {
        if (selectedIndex >= 0 && strip.layoutInfo.visibleItemsInfo.none { it.index == selectedIndex }) {
            strip.animateScrollToItem(selectedIndex)
        }
    }
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        state = strip,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        itemsIndexed(lines, key = { _, it -> it }) { _, line ->
            val (done, total) = remember(line, mastered) { SkillGuidance.lineProgress(line, mastered) }
            PathChip(line, done, total, on = line == selected) { onPick(line) }
        }
    }
}

@Composable
private fun PathChip(line: String, done: Int, total: Int, on: Boolean, onClick: () -> Unit) {
    val shape = MaterialTheme.shapes.extraSmall
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .widthIn(min = 48.dp)
            .clip(shape)
            .then(
                if (on) Modifier.background(IronvellumColors.SystemGreen.copy(alpha = 0.12f)).inkBorder(IronvellumColors.SystemGreen, shape, 1.dp)
                else Modifier,
            )
            .clickable(role = Role.Tab, onClickLabel = "Open $line path") { onClick() }
            .semantics {
                this.selected = on
                contentDescription = chipDescription(line, done, total)
            }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$line $done/$total",
            style = MaterialTheme.typography.labelMedium,
            fontFamily = ChakraPetch,
            color = if (on) IronvellumColors.SystemGreen else IronvellumColors.InkMuted,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}
