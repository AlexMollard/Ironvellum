package com.ironvellum.app.ui.program

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ironvellum.app.R
import com.ironvellum.app.domain.Muscle
import com.ironvellum.app.domain.MuscleLoad
import com.ironvellum.app.domain.PlannedPreset
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.ui.asString
import com.ironvellum.app.ui.theme.IronvellumColors

/** What the Coverage map draws: the loads and the one tally every summary on the page reads. */
internal data class CoverageMapModel(val loads: Map<Muscle, MuscleLoad>, val tally: CoverageTally)

/**
 * The single rule from presets to map state. Setup's preview, the plan builder's preview and Muscles >
 * Coverage all go through it, so the three cannot disagree about a muscle.
 */
internal fun coverageMapModel(presets: List<PlannedPreset>, pooled: Boolean, dismissed: Set<Muscle>): CoverageMapModel {
    val loads = loadsOf(presets, pooled)
    return CoverageMapModel(loads, coverageTally(loads, dismissed))
}

/**
 * The one Coverage map: the body figure with its legend, then the line owning up to sets the map cannot
 * count. Every screen that shows coverage on a figure calls this and nothing else.
 */
@Composable
internal fun CoverageMap(
    loads: Map<Muscle, MuscleLoad>,
    dismissed: Set<Muscle>,
    selection: MutableState<Muscle?>,
    unattributed: Int,
    modifier: Modifier = Modifier,
    figureHeight: Dp = 260.dp,
    interactive: Boolean = true,
) {
    Column(modifier) {
        CoverageFigure(
            loads = loads,
            dismissed = dismissed,
            modifier = Modifier.fillMaxWidth(),
            figureHeight = figureHeight,
            selection = selection,
            interactive = interactive,
        )
        if (unattributed > 0) {
            Text(
                UiText.plural(R.plurals.coverage_unattributed_sets, unattributed).asString(),
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
            )
        }
    }
}
