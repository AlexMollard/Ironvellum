package com.ironvellum.app.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ironvellum.app.R
import com.ironvellum.app.data.TrialPrefs
import com.ironvellum.app.domain.RestAlert
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.ui.asString
import com.ironvellum.app.ui.components.InkSegmented
import com.ironvellum.app.ui.components.SettingsCaption
import com.ironvellum.app.ui.components.SettingsGroup
import com.ironvellum.app.ui.components.SettingsSwitchRow
import com.ironvellum.app.ui.components.SettingsValueRow

/**
 * What a live trial does between sets: whether a rest runs at all, how its end
 * is announced (and the last seconds of a timed set), and whether the screen
 * stays awake. All device settings ([TrialPrefs]); a single movement can turn
 * its rest off from its rite or from its menu in the trial.
 */
@Composable
internal fun TrialSettings(onBack: () -> Unit, onOpenLockScreen: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { TrialPrefs(context) }
    var restOn by remember { mutableStateOf(!prefs.restOff) }
    var alert by remember { mutableStateOf(prefs.restAlert) }
    var awake by remember { mutableStateOf(prefs.keepScreenOn) }
    SettingsPage(SettingsSection.DURING_TRIAL.title, onBack) {
        SettingsGroup(null, topSpace = 12.dp) {
            SettingsSwitchRow(
                label = stringResource(R.string.settings_trial_rest_label),
                caption = UiText.themed(R.string.settings_trial_rest_caption, R.string.settings_trial_rest_caption__t).asString(),
                checked = restOn,
                onCheckedChange = {
                    restOn = it
                    prefs.restOff = !it
                },
            )
        }
        SettingsGroup(stringResource(R.string.settings_trial_alert_title), topSpace = 10.dp) {
            InkSegmented(
                options = RestAlert.entries.map { it to it.label },
                selected = alert,
                onPick = {
                    alert = it
                    prefs.restAlert = it
                },
            )
            Spacer(Modifier.height(8.dp))
            SettingsCaption(stringResource(R.string.settings_trial_alert_caption, alert.caption))
        }
        SettingsGroup(null, topSpace = 10.dp) {
            SettingsSwitchRow(
                label = stringResource(R.string.settings_trial_awake_label),
                caption = UiText.themed(R.string.settings_trial_awake_caption, R.string.settings_trial_awake_caption__t).asString(),
                checked = awake,
                onCheckedChange = {
                    awake = it
                    prefs.keepScreenOn = it
                },
            )
        }
        // The hub has no row of its own for this page (it is full), so it opens from here.
        SettingsGroup(null, topSpace = 10.dp, rows = true) {
            SettingsValueRow(
                stringResource(R.string.settings_trial_lock_screen_label),
                SettingsSummaries.lockScreen(prefs.liveUpdate, prefs.showLiftsOnLock),
                onOpenLockScreen,
            )
        }
    }
}
