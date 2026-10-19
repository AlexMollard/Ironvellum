package com.ironvellum.app.ui.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironvellum.app.R
import com.ironvellum.app.data.EffortSetting
import com.ironvellum.app.data.Units
import com.ironvellum.app.data.WarmupStore
import com.ironvellum.app.domain.Effort
import com.ironvellum.app.domain.EffortScale
import com.ironvellum.app.domain.LoadUnit
import com.ironvellum.app.domain.TrainingFocus
import com.ironvellum.app.domain.TrainingMode
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.ui.asString
import com.ironvellum.app.ui.components.InkSegmented
import com.ironvellum.app.ui.components.SectionHeader
import com.ironvellum.app.ui.components.SettingsCaption
import com.ironvellum.app.ui.components.SettingsSwitchRow
import com.ironvellum.app.ui.components.Term
import com.ironvellum.app.ui.components.TermChip
import com.ironvellum.app.ui.theme.IronvellumColors

/** The generator's goal control words, so one concept has one name. */
internal fun TrainingMode.label(): String = when (this) {
    TrainingMode.STRENGTH -> TrainingFocus.STRENGTH.label
    TrainingMode.HYPERTROPHY -> TrainingFocus.MUSCLE.label
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
                TrainingMode.STRENGTH -> stringResource(R.string.settings_training_strength_caption)
                TrainingMode.HYPERTROPHY -> stringResource(R.string.settings_training_hypertrophy_caption)
            },
            style = MaterialTheme.typography.bodySmall,
            color = IronvellumColors.InkMuted,
        )
        // One labelled chip per term, so each explainer says what it explains.
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (mode != TrainingMode.STRENGTH) TermChip(Term.DOUBLE_PROGRESSION)
            TermChip(Term.DELOAD)
        }
        Spacer(Modifier.height(16.dp))
        val context = LocalContext.current
        var warmupOn by remember { mutableStateOf(WarmupStore(context).enabled) }
        SettingsSwitchRow(
            label = stringResource(R.string.settings_training_warmup_label),
            caption = UiText.themed(R.string.settings_training_warmup_caption, R.string.settings_training_warmup_caption__t).asString(),
            checked = warmupOn,
            onCheckedChange = {
                warmupOn = it
                WarmupStore(context).enabled = it
            },
        )
        SectionHeader(stringResource(R.string.settings_training_units_title), topPadding = 16.dp)
        InkSegmented(
            options = LoadUnit.entries.map { it to it.label },
            selected = Units.current,
            onPick = { Units.set(context.applicationContext, it) },
        )
        Spacer(Modifier.height(8.dp))
        SettingsCaption(stringResource(R.string.settings_training_units_caption))
        SectionHeader(stringResource(R.string.settings_training_effort_title), topPadding = 16.dp)
        SettingsSwitchRow(
            label = stringResource(R.string.settings_training_effort_label),
            caption = stringResource(R.string.settings_training_effort_caption),
            checked = EffortSetting.enabled,
            onCheckedChange = { EffortSetting.setEnabled(context.applicationContext, it) },
        )
        InkSegmented(
            options = EffortScale.entries.map { it to it.label },
            selected = EffortSetting.scale,
            onPick = { EffortSetting.setScale(context.applicationContext, it) },
        )
        Spacer(Modifier.height(8.dp))
        SettingsCaption(stringResource(R.string.settings_training_effort_rir_hint, Effort.hint(EffortScale.RPE)))
    }
}
