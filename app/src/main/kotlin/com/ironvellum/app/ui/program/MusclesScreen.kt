package com.ironvellum.app.ui.program

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ironvellum.app.R
import com.ironvellum.app.ui.components.InkSegmented
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.train.MusclesView
import com.ironvellum.app.ui.train.TrainTabMemory

/** Train > Muscles: one Fatigue | Coverage switch over two maps of the same body figure. */
@Composable
internal fun MusclesScreen(
    onGenerateSession: () -> Unit,
    onOpenRite: (Long) -> Unit,
    onOpenRites: () -> Unit,
    onOptimise: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(8.dp))
        InkSegmented(
            options = listOf(
                MusclesView.FATIGUE to stringResource(R.string.fatigue_switch_fatigue),
                MusclesView.COVERAGE to stringResource(R.string.fatigue_switch_coverage),
            ),
            selected = TrainTabMemory.view,
            onPick = { TrainTabMemory.view = it },
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(8.dp))
        when (TrainTabMemory.view) {
            MusclesView.FATIGUE -> FatiguePane()
            MusclesView.COVERAGE -> MuscleCoverageContent(onGenerateSession, onOpenRite, onOpenRites, onOptimise)
        }
    }
}

/** Filled in by the next task: for now the caption the pane will end with. */
@Composable
private fun FatiguePane() {
    Text(
        stringResource(R.string.fatigue_window_caption),
        style = MaterialTheme.typography.bodySmall,
        color = IronvellumColors.InkMuted,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}
