package com.ironvellum.app.ui.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironvellum.app.domain.TrainingMode
import com.ironvellum.app.ui.components.InkSegmented
import com.ironvellum.app.ui.components.Term
import com.ironvellum.app.ui.components.TermChip
import com.ironvellum.app.ui.theme.IronvellumColors

/** The generator's goal control words, so one concept has one name. */
internal fun TrainingMode.label(): String = when (this) {
    TrainingMode.STRENGTH -> "Power"
    TrainingMode.HYPERTROPHY -> "Muscle"
}

@Composable
internal fun TrainingModeSettings(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val mode = profile?.trainingMode ?: TrainingMode.STRENGTH
    SettingsPage(SettingsSection.TRAINING_MODE.title, onBack) {
        Spacer(Modifier.height(12.dp))
        InkSegmented(
            options = TrainingMode.entries.map { it to it.label() },
            selected = mode,
            onPick = { viewModel.setMode(it) },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            when (mode) {
                TrainingMode.STRENGTH -> "Clear all sets and load rises. Three stalls: deload."
                TrainingMode.HYPERTROPHY -> "Reps climb, then load. Three stalls: deload."
            },
            style = MaterialTheme.typography.bodySmall,
            color = IronvellumColors.InkMuted,
        )
        // One labelled chip per term, so each explainer says what it explains.
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (mode != TrainingMode.STRENGTH) TermChip(Term.DOUBLE_PROGRESSION)
            TermChip(Term.DELOAD)
        }
    }
}
