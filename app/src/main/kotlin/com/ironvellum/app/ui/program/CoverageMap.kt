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
import com.ironvellum.app.domain.PlannedPreset
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.ui.asString
import com.ironvellum.app.ui.theme.IronvellumColors

/** What the Coverage map draws: the week and the one tally every summary on the page reads. */
internal data class CoverageMapModel(val week: WeekCoverage, val tally: CoverageTally)

/**
 * The single rule from a week to map state. Muscles > Coverage reads the real week; setup's and the plan builder's
 * previews have no "done" yet, so they read [plannedOnlyWeek] of the proposal (the Planned state, no Done key in the
 * legend). Both go through here, so the three cannot disagree about a muscle.
 */
internal fun coverageMapModel(week: WeekCoverage, dismissed: Set<Muscle>): CoverageMapModel =
    CoverageMapModel(week, week.tally(dismissed))

internal fun coverageMapModel(presets: List<PlannedPreset>, dismissed: Set<Muscle>): CoverageMapModel =
    coverageMapModel(plannedOnlyWeek(presets), dismissed)

/**
 * The one Coverage map: the body figure with its legend, then the line owning up to sets the map cannot
 * count. Every screen that shows coverage on a figure calls this and nothing else.
 */
@Composable
internal fun CoverageMap(
    week: WeekCoverage,
    dismissed: Set<Muscle>,
    selection: MutableState<Muscle?>,
    unattributed: Int,
    modifier: Modifier = Modifier,
    figureHeight: Dp = 260.dp,
    interactive: Boolean = true,
) {
    Column(modifier) {
        CoverageFigure(
            week = week,
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
