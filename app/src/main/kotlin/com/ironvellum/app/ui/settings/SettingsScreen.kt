package com.ironvellum.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironvellum.app.BuildConfig
import com.ironvellum.app.R
import com.ironvellum.app.data.ProgramAnswersStore
import com.ironvellum.app.data.Reminders
import com.ironvellum.app.data.TrialPrefs
import com.ironvellum.app.data.Words
import com.ironvellum.app.domain.TrainingMode
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.ui.asString
import com.ironvellum.app.ui.components.PushedHeader
import com.ironvellum.app.ui.components.SettingsGroup
import com.ironvellum.app.ui.components.SettingsValueRow
import com.ironvellum.app.ui.theme.AccentPresets
import com.ironvellum.app.ui.theme.IronvellumColors

/**
 * The Settings hub: one row per setting, each showing its current value, and
 * each opening the sub-screen that changes it. Sized to fit one phone screen;
 * everything that used to stack here lives one tap down.
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onOpenSection: (SettingsSection) -> Unit,
    onOpenAccount: () -> Unit,
    onOpenSignIn: () -> Unit,
    onOpenSupport: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val appearance = viewModel.currentAppearance()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val bodyProfile by viewModel.bodyProfile.collectAsStateWithLifecycle()
    val sexUnset by com.ironvellum.app.data.FirstRunPrefs.sexUnsetFlow(context).collectAsStateWithLifecycle()
    val renameError by viewModel.renameError.collectAsStateWithLifecycle()
    val heightStatus by viewModel.heightStatus.collectAsStateWithLifecycle()
    val signedIn by viewModel.signedIn.collectAsStateWithLifecycle()
    val healthLink by viewModel.healthLink.collectAsStateWithLifecycle()
    val healthDays by viewModel.healthDays.collectAsStateWithLifecycle()
    val cloudConfig by viewModel.cloudConfig.collectAsStateWithLifecycle()
    val savedGear by ProgramAnswersStore.equipment.collectAsStateWithLifecycle()
    // The hub leaves composition while a sub-screen is open, so these are
    // re-read on every return rather than going stale behind a toggle.
    val summonsOn = remember { Reminders.enabled(context) }
    val trialPrefs = remember { TrialPrefs(context) }
    var crashCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        ProgramAnswersStore.get(context.applicationContext)
        viewModel.refreshHealthLink()
        crashCount = viewModel.crashSummary().first
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        PushedHeader(stringResource(R.string.settings_hub_title), onBack)

        SettingsGroup(stringResource(R.string.settings_hub_group_you), topSpace = 4.dp, rows = true) {
            SettingsValueRow(
                stringResource(R.string.settings_hub_profile_label),
                // A name or height refused on leaving Profile must not pass silently.
                if (renameError != null) {
                    stringResource(R.string.settings_hub_profile_name_not_saved)
                } else if (heightStatus.error != null) {
                    stringResource(R.string.settings_hub_profile_height_not_saved)
                } else {
                    SettingsSummaries.profile(profile?.name, bodyProfile.first, bodyProfile.second.takeUnless { sexUnset })
                },
            ) { onOpenSection(SettingsSection.PROFILE) }
            SettingsValueRow(
                stringResource(R.string.settings_hub_account_label),
                stringResource(if (signedIn) R.string.settings_hub_account_signed_in else R.string.settings_hub_account_signed_out),
            ) {
                // Signed out there is no account screen yet: sign-in lives on Allies.
                if (signedIn) onOpenAccount() else onOpenSignIn()
            }
        }

        SettingsGroup(stringResource(R.string.settings_hub_group_training), topSpace = 10.dp, rows = true) {
            SettingsValueRow(stringResource(R.string.settings_hub_training_mode_label), (profile?.trainingMode ?: TrainingMode.STRENGTH).label()) {
                onOpenSection(SettingsSection.TRAINING_MODE)
            }
            SettingsValueRow(
                UiText.themed(R.string.settings_hub_during_trial_label, R.string.settings_hub_during_trial_label__t).asString(),
                SettingsSummaries.trial(trialPrefs.restOff, trialPrefs.restAlert),
            ) {
                onOpenSection(SettingsSection.DURING_TRIAL)
            }
            SettingsValueRow(Words.armoury.c, SettingsSummaries.armoury(savedGear)) {
                onOpenSection(SettingsSection.ARMOURY)
            }
            SettingsValueRow(Words.theSummons.c, stringResource(if (summonsOn) R.string.common_on else R.string.common_off)) {
                onOpenSection(SettingsSection.SUMMONS)
            }
        }

        SettingsGroup(stringResource(R.string.settings_hub_group_app), topSpace = 10.dp, rows = true) {
            SettingsValueRow(
                stringResource(R.string.settings_hub_appearance_label),
                AccentPresets.forTheme(IronvellumColors.light).firstOrNull { it.palette == appearance }?.name
                    ?: stringResource(R.string.settings_hub_appearance_custom),
            ) {
                onOpenSection(SettingsSection.APPEARANCE)
            }
            SettingsValueRow(stringResource(R.string.settings_hub_health_connect_label), SettingsSummaries.health(healthLink, healthDays.size)) {
                onOpenSection(SettingsSection.HEALTH_CONNECT)
            }
            SettingsValueRow(stringResource(R.string.settings_hub_data_label), stringResource(R.string.settings_hub_data_summary)) { onOpenSection(SettingsSection.DATA) }
        }

        SettingsGroup(stringResource(R.string.settings_hub_group_about), topSpace = 10.dp, rows = true) {
            SettingsValueRow(
                stringResource(R.string.settings_hub_advanced_label),
                if (crashCount > 0) {
                    pluralStringResource(R.plurals.settings_hub_advanced_crashes, crashCount, crashCount)
                } else {
                    when (cloudConfig?.isDefault) {
                        null -> stringResource(R.string.settings_hub_cloud_none)
                        true -> stringResource(R.string.settings_hub_cloud_shared)
                        false -> stringResource(R.string.settings_hub_cloud_own)
                    }
                },
            ) { onOpenSection(SettingsSection.ADVANCED) }
            SettingsValueRow(
                stringResource(if (BuildConfig.SUPPORT_LINKS) R.string.settings_hub_support_label else R.string.settings_hub_about_label),
                null,
                onOpenSupport,
            )
        }

        Spacer(Modifier.height(10.dp))
        Text(
            UiText.themed(R.string.settings_hub_privacy_note, R.string.settings_hub_privacy_note__t).asString(),
            style = MaterialTheme.typography.bodySmall,
            color = IronvellumColors.InkMuted,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(12.dp))
    }
}
