package com.ironvellum.app.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ironvellum.app.data.Notifications
import com.ironvellum.app.data.Reminders
import com.ironvellum.app.ui.components.NotificationBlockedNotice
import com.ironvellum.app.ui.components.SettingsSwitchRow
import com.ironvellum.app.ui.components.rememberNotificationAccess

/** The only bring-back mechanism the app has: one switch, one evening nudge. */
@Composable
internal fun SummonsSettings(onBack: () -> Unit) {
    val context = LocalContext.current
    var remindersOn by remember { mutableStateOf(Reminders.enabled(context)) }
    // Re-read on every resume, so a grant or block made in Android's settings
    // shows here without leaving the screen.
    val access = rememberNotificationAccess(Notifications.CHANNEL_SUMMONS)
    // Declined notifications must not wedge the switch: the work is still
    // scheduled, and the notice below offers the way to allow them.
    val askNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    val setOn: (Boolean) -> Unit = { on ->
        if (on != remindersOn) {
            if (on) {
                Reminders.enable(context)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !access.permissionGranted) {
                    askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            } else {
                Reminders.disable(context)
            }
            remindersOn = on
        }
    }
    SettingsPage(SettingsSection.SUMMONS.title, onBack) {
        Spacer(Modifier.height(12.dp))
        SettingsSwitchRow(
            label = "Evening reminder",
            caption = "One evening reminder on days your cycle holds a rite you have not begun.",
            checked = remindersOn,
            onCheckedChange = setOn,
        )
        if (remindersOn) {
            NotificationBlockedNotice(
                channelId = Notifications.CHANNEL_SUMMONS,
                access = access,
                blockedText = "The Summons won't arrive until you allow notifications.",
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
