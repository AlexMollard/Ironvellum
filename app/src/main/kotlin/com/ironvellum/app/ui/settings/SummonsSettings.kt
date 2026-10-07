package com.ironvellum.app.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ironvellum.app.data.Notifications
import com.ironvellum.app.data.Reminders
import com.ironvellum.app.ui.components.ListRowHeight
import com.ironvellum.app.ui.components.NotificationBlockedNotice
import com.ironvellum.app.ui.components.SettingsCaption
import com.ironvellum.app.ui.components.rememberNotificationAccess
import com.ironvellum.app.ui.theme.IronvellumColors

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
        // The whole row is the switch, so TalkBack reads the label and the state together.
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = ListRowHeight)
                .toggleable(value = remindersOn, role = Role.Switch, onValueChange = setOn)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text("Evening reminder", style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.Ink)
                SettingsCaption("One evening reminder on days your cycle holds a rite you have not begun.")
            }
            Switch(
                checked = remindersOn,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = IronvellumColors.Abyss,
                    checkedTrackColor = IronvellumColors.Emerald,
                    checkedBorderColor = IronvellumColors.Emerald,
                    uncheckedThumbColor = IronvellumColors.InkMuted,
                    uncheckedTrackColor = IronvellumColors.Rune,
                    uncheckedBorderColor = IronvellumColors.Rune,
                ),
            )
        }
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
