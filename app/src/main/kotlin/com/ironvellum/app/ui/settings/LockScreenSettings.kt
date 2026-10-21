package com.ironvellum.app.ui.settings

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.ironvellum.app.LiveWorkoutTileService
import com.ironvellum.app.R
import com.ironvellum.app.data.TrialPrefs
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.ui.asString
import com.ironvellum.app.ui.components.InkDivider
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.components.ListRow
import com.ironvellum.app.ui.components.SettingsCaption
import com.ironvellum.app.ui.components.SettingsGroup
import com.ironvellum.app.ui.components.SettingsSwitchRow
import com.ironvellum.app.ui.theme.IronvellumColors

/**
 * Settings > Lock screen and shortcuts: the live workout as an Android 16 Live Update, whether the lifts show on the
 * lock screen, and the Quick Settings tile and app-icon shortcuts. The two switches are device settings
 * ([TrialPrefs]), like the rest of "During a workout"; Android keeps its own promotion switch, which this page shows
 * and links to when it is off (the toggle then reads off, because nothing would be promoted).
 */
@Composable
internal fun LockScreenSettings(onOpenRest: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { TrialPrefs(context) }
    var live by remember { mutableStateOf(prefs.liveUpdate) }
    var lifts by remember { mutableStateOf(prefs.showLiftsOnLock) }
    val promotable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA
    // Android's own switch can change while the lifter is in its settings: read again on every resume.
    var systemAllows by remember { mutableStateOf(!promotable || NotificationManagerCompat.from(context).canPostPromotedNotifications()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        systemAllows = !promotable || NotificationManagerCompat.from(context).canPostPromotedNotifications()
    }
    var tileAdded by remember { mutableStateOf(false) }
    // The app's own notification page, where Android 16 keeps the Live updates switch. (The platform's dedicated
    // ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS is not in the compileSdk 37 android.jar, so it cannot be used by name.)
    val openAndroidSettings = {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
    SettingsPage(SettingsSection.LOCK_SCREEN.title, onBack) {
        SettingsGroup(
            UiText.themed(R.string.settings_section_during_trial_title, R.string.settings_section_during_trial_title__t).asString(),
            topSpace = 12.dp,
        ) {
            SettingsSwitchRow(
                label = UiText.themed(R.string.settings_lock_live_label, R.string.settings_lock_live_label__t).asString(),
                caption = if (systemAllows) {
                    UiText.themed(R.string.settings_lock_live_caption, R.string.settings_lock_live_caption__t).asString()
                } else {
                    stringResource(R.string.settings_lock_live_blocked)
                },
                checked = live && systemAllows,
                onCheckedChange = {
                    if (!systemAllows) {
                        openAndroidSettings()
                    } else {
                        live = it
                        prefs.liveUpdate = it
                    }
                },
            )
            InkDivider()
            SettingsSwitchRow(
                label = stringResource(R.string.settings_lock_lifts_label),
                caption = UiText.themed(R.string.settings_lock_lifts_caption, R.string.settings_lock_lifts_caption__t).asString(),
                checked = lifts,
                onCheckedChange = {
                    lifts = it
                    prefs.showLiftsOnLock = it
                },
            )
            InkDivider()
            ListRow(
                stringResource(R.string.settings_lock_rest_alert_label),
                subline = stringResource(R.string.settings_lock_rest_alert_caption, prefs.restAlert.label),
                onClickLabel = stringResource(R.string.settings_row_open_label, stringResource(R.string.settings_lock_rest_alert_label)),
                contentPadding = PaddingValues(vertical = 8.dp),
                onClick = onOpenRest,
            )
        }
        SettingsGroup(stringResource(R.string.settings_lock_group_quick), topSpace = 10.dp) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                    Text(stringResource(R.string.settings_lock_tile_label), style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.Ink)
                    val caption = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        UiText.themed(R.string.settings_lock_tile_caption, R.string.settings_lock_tile_caption__t)
                    } else {
                        UiText.themed(R.string.settings_lock_tile_caption_manual, R.string.settings_lock_tile_caption_manual__t)
                    }
                    SettingsCaption(caption.asString())
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    IronvellumButton(
                        label = stringResource(if (tileAdded) R.string.settings_lock_tile_added else R.string.settings_lock_tile_add),
                        enabled = !tileAdded,
                        quiet = true,
                        onClick = {
                            val bar = context.getSystemService(StatusBarManager::class.java)
                            bar?.requestAddTileService(
                                ComponentName(context, LiveWorkoutTileService::class.java),
                                UiText.themed(R.string.live_tile_label, R.string.live_tile_label__t).asString(context),
                                Icon.createWithResource(context, R.drawable.ic_tile_workout),
                                ContextCompat.getMainExecutor(context),
                            ) { result ->
                                // Added now, or already there: either way the tile exists.
                                if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
                                    result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED
                                ) {
                                    tileAdded = true
                                }
                            }
                        },
                    )
                }
            }
            InkDivider()
            Column(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 8.dp)) {
                Text(stringResource(R.string.settings_lock_shortcuts_label), style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.Ink)
                SettingsCaption(stringResource(R.string.settings_lock_shortcuts_caption))
            }
        }
        Spacer(Modifier.height(10.dp))
        if (!systemAllows) {
            IronvellumButton(
                label = stringResource(R.string.settings_lock_live_open_settings),
                onClick = openAndroidSettings,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            InkPanel(Modifier.fillMaxWidth()) {
                SettingsCaption(stringResource(R.string.settings_lock_footer))
            }
        }
    }
}
