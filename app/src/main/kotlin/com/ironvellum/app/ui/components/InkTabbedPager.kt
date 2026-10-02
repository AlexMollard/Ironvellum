package com.ironvellum.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerScope
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * The one way a screen shows tabs over swipeable pages, so the same strip always behaves the same
 * way: swiping a page slides the underline between the tabs under the finger, tapping a tab
 * animates to its page, and the tabs stay the way to change page without a swipe.
 *
 * [state] is hoisted so a screen can jump or animate to a page itself (a deep link) and read
 * `currentPage` or `settledPage`; `rememberPagerState` is saveable, so the open page survives a
 * rotation and a trip to another route. Only the pages at or next to the open one are composed
 * (the pager's default), so a page keeps its own vertical scroll in a hoisted or saveable
 * ScrollState, never a plain `remember`. [tabsModifier] places the strip (a screen's gutter) and
 * [tabsGap] is the space between strip and pages; the pages themselves run edge to edge, so each
 * one adds its own gutter and a swipe is not clipped inside the margins.
 *
 * Give [modifier] a weight or a fixed height: the pager fills what it is given.
 */
@Composable
fun InkTabbedPager(
    labels: List<String>,
    state: PagerState,
    modifier: Modifier = Modifier,
    badges: List<Int> = emptyList(),
    tabsModifier: Modifier = Modifier,
    tabsGap: Dp = 0.dp,
    pageContent: @Composable PagerScope.(page: Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    Column(modifier) {
        InkTabs(
            labels = labels,
            badges = badges,
            selectedIndex = state.currentPage,
            onSelect = { index -> scope.launch { state.animateScrollToPage(index) } },
            modifier = tabsModifier,
            indicatorPosition = { state.currentPage + state.currentPageOffsetFraction },
        )
        if (tabsGap > 0.dp) Spacer(Modifier.height(tabsGap))
        HorizontalPager(state = state, modifier = Modifier.weight(1f).fillMaxWidth(), pageContent = pageContent)
    }
}
