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
import androidx.compose.ui.unit.dp
import com.ironvellum.app.data.TrialPrefs
import com.ironvellum.app.data.Words
import com.ironvellum.app.domain.RestAlert
import com.ironvellum.app.ui.components.InkSegmented
import com.ironvellum.app.ui.components.SettingsCaption
import com.ironvellum.app.ui.components.SettingsGroup
import com.ironvellum.app.ui.components.SettingsSwitchRow

/**
 * What a live trial does between sets: whether a rest runs at all, how its end
 * is announced (and the last seconds of a timed set), and whether the screen
 * stays awake. All device settings ([TrialPrefs]); a single movement can turn
 * its rest off from its rite or from its menu in the trial.
 */
@Composable
internal fun TrialSettings(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { TrialPrefs(context) }
    var restOn by remember { mutableStateOf(!prefs.restOff) }
    var alert by remember { mutableStateOf(prefs.restAlert) }
    var awake by remember { mutableStateOf(prefs.keepScreenOn) }
    SettingsPage(SettingsSection.DURING_TRIAL.title, onBack) {
        SettingsGroup(null, topSpace = 12.dp) {
            SettingsSwitchRow(
                label = "Rest timer",
                caption = "Counts the rest down after each set. Off, a ticked set starts no rest. " +
                    "One movement can opt out from its ${Words.rite.s} or its menu in the ${Words.trial.s}.",
                checked = restOn,
                onCheckedChange = {
                    restOn = it
                    prefs.restOff = !it
                },
            )
        }
        SettingsGroup("WHEN A REST ENDS", topSpace = 10.dp) {
            InkSegmented(
                options = RestAlert.entries.map { it to it.label },
                selected = alert,
                onPick = {
                    alert = it
                    prefs.restAlert = it
                },
            )
            Spacer(Modifier.height(8.dp))
            SettingsCaption(alert.caption + " It also cues the last 3 seconds of a timed set.")
        }
        SettingsGroup(null, topSpace = 10.dp) {
            SettingsSwitchRow(
                label = "Keep the screen on",
                caption = "Stops the screen sleeping while ${Words.trial.a} is open. The pocket lock on the ${Words.trial.s} dims it.",
                checked = awake,
                onCheckedChange = {
                    awake = it
                    prefs.keepScreenOn = it
                },
            )
        }
    }
}
