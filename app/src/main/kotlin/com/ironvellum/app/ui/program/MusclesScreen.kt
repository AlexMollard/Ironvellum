package com.ironvellum.app.ui.program

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import com.ironvellum.app.R
import com.ironvellum.app.ui.components.InkTabbedPager
import com.ironvellum.app.ui.components.PushedHeader

/** The two pages of the Muscles screen, and the route argument that names the one a link opens on. */
enum class MusclesView(val arg: String) {
    FATIGUE("fatigue"),
    COVERAGE("coverage"),
    ;

    companion object {
        fun of(arg: String?): MusclesView = entries.firstOrNull { it.arg == arg } ?: FATIGUE
    }
}

/**
 * Muscles: its own screen, opened from Train's "Muscles right now" card. Its only tab row is
 * Fatigue | Coverage over two maps of the same body figure, each a page that swipes; [initialView] is where a link lands.
 */
@Composable
internal fun MusclesScreen(
    initialView: MusclesView,
    onBack: () -> Unit,
    onGenerateSession: () -> Unit,
    onOpenRite: (Long) -> Unit,
    onOpenRites: () -> Unit,
    onOptimise: () -> Unit,
) {
    val pager = rememberPagerState(initialPage = initialView.ordinal) { MusclesView.entries.size }
    val density = LocalDensity.current
    val expanded = with(density) { LocalWindowInfo.current.containerSize.width.toDp() >= 840.dp }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(20.dp))
            PushedHeader(stringResource(R.string.muscles_title), onBack)
        }
        // Each view is a swipeable page under the strip; a page adds its own gutter so a swipe is not clipped.
        InkTabbedPager(
            labels = listOf(stringResource(R.string.fatigue_switch_fatigue), stringResource(R.string.fatigue_switch_coverage)),
            state = pager,
            modifier = Modifier.weight(1f),
            tabsModifier = (if (expanded) Modifier.fillMaxWidth(0.5f) else Modifier).padding(horizontal = 16.dp),
            tabsGap = 8.dp,
            fillTabs = true,
        ) { page ->
            when (MusclesView.entries[page]) {
                MusclesView.FATIGUE -> FatiguePane(expanded = expanded)
                MusclesView.COVERAGE -> MuscleCoverageContent(onGenerateSession, onOpenRite, onOpenRites, onOptimise, expanded = expanded)
            }
        }
    }
}
