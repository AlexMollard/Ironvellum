package com.ironvellum.app.ui.settings

import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironvellum.app.BuildConfig
import com.ironvellum.app.data.ProgramAnswersStore
import com.ironvellum.app.data.Reminders
import com.ironvellum.app.domain.TrainingMode
import com.ironvellum.app.ui.components.SettingsGroup
import com.ironvellum.app.ui.components.SettingsValueRow
import com.ironvellum.app.ui.components.PushedHeader
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
    val appearance by viewModel.appearance.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val bodyProfile by viewModel.bodyProfile.collectAsStateWithLifecycle()
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
        PushedHeader("SETTINGS", onBack)

        SettingsGroup("YOU", topSpace = 4.dp, contentPadding = PaddingValues(0.dp)) {
            SettingsValueRow(
                "Profile",
                // A name or height refused on leaving Profile must not pass silently.
                if (renameError != null) {
                    "Name not saved"
                } else if (heightStatus.error != null) {
                    "Height not saved"
                } else {
                    SettingsSummaries.profile(profile?.name, bodyProfile.first, bodyProfile.second)
                },
            ) { onOpenSection(SettingsSection.PROFILE) }
            SettingsValueRow("Account", if (signedIn) "Signed in" else "Not signed in") {
                // Signed out there is no account screen yet: sign-in lives on Allies.
                if (signedIn) onOpenAccount() else onOpenSignIn()
            }
        }

        SettingsGroup("TRAINING", topSpace = 10.dp, contentPadding = PaddingValues(0.dp)) {
            SettingsValueRow("Training mode", (profile?.trainingMode ?: TrainingMode.STRENGTH).label()) {
                onOpenSection(SettingsSection.TRAINING_MODE)
            }
            SettingsValueRow("Armoury", SettingsSummaries.armoury(savedGear)) {
                onOpenSection(SettingsSection.ARMOURY)
            }
            SettingsValueRow("The Summons", if (summonsOn) "On" else "Off") {
                onOpenSection(SettingsSection.SUMMONS)
            }
        }

        SettingsGroup("APP", topSpace = 10.dp, contentPadding = PaddingValues(0.dp)) {
            SettingsValueRow("Appearance", AccentPresets.entries.firstOrNull { it.palette == appearance }?.name ?: "Custom colours") {
                onOpenSection(SettingsSection.APPEARANCE)
            }
            SettingsValueRow("Health Connect", SettingsSummaries.health(healthLink, healthDays.size)) {
                onOpenSection(SettingsSection.HEALTH_CONNECT)
            }
            SettingsValueRow("Data", "Export, import") { onOpenSection(SettingsSection.DATA) }
        }

        SettingsGroup(null, topSpace = 10.dp, contentPadding = PaddingValues(0.dp)) {
            SettingsValueRow(
                "Advanced",
                if (crashCount > 0) {
                    "$crashCount ${if (crashCount == 1) "crash" else "crashes"} logged"
                } else {
                    when (cloudConfig?.isDefault) {
                        null -> "No cloud"
                        true -> "Shared cloud"
                        false -> "Own backend"
                    }
                },
            ) { onOpenSection(SettingsSection.ADVANCED) }
            SettingsValueRow(if (BuildConfig.SUPPORT_LINKS) "Support Ironvellum" else "About Ironvellum", null, onOpenSupport)
        }

        Spacer(Modifier.height(10.dp))
        Text(
            "Ironvellum ${BuildConfig.VERSION_NAME}  ·  readings stay on this device",
            style = MaterialTheme.typography.bodySmall,
            color = IronvellumColors.InkMuted,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(12.dp))
    }
}
