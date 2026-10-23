package com.ironvellum.app.ui.program

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ironvellum.app.R
import com.ironvellum.app.ui.components.InkTabs
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
 * Fatigue | Coverage over two maps of the same body figure; [initialView] is where a link lands.
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
    var view by rememberSaveable { mutableStateOf(initialView) }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(20.dp))
            PushedHeader(stringResource(R.string.muscles_title), onBack)
            InkTabs(
                labels = listOf(stringResource(R.string.fatigue_switch_fatigue), stringResource(R.string.fatigue_switch_coverage)),
                selectedIndex = view.ordinal,
                onSelect = { view = MusclesView.entries[it] },
                fill = true,
            )
        }
        Spacer(Modifier.height(8.dp))
        when (view) {
            MusclesView.FATIGUE -> FatiguePane()
            MusclesView.COVERAGE -> MuscleCoverageContent(onGenerateSession, onOpenRite, onOpenRites, onOptimise)
        }
    }
}
