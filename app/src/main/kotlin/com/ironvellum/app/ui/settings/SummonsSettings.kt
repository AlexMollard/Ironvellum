package com.ironvellum.app.ui.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.content.ContextCompat
import com.ironvellum.app.data.Reminders
import com.ironvellum.app.ui.components.InkSegmented
import com.ironvellum.app.ui.components.SettingsCaption
import com.ironvellum.app.ui.components.SettingsGroup

internal fun notificationsDenied(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED

/** The only bring-back mechanism the app has: one toggle, one evening nudge. */
@Composable
internal fun SummonsSettings(onBack: () -> Unit) {
    val context = LocalContext.current
    var remindersOn by remember { mutableStateOf(Reminders.enabled(context)) }
    var denied by remember { mutableStateOf(notificationsDenied(context)) }
    // Declined notifications must not wedge the toggle: the work is still
    // scheduled, Android just drops the posts, and the caption says so.
    val askNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { denied = notificationsDenied(context) }
    SettingsPage(SettingsSection.SUMMONS.title, onBack) {
        SettingsGroup(null, topSpace = 12.dp) {
            InkSegmented(
                options = listOf(true to "ON", false to "OFF"),
                selected = remindersOn,
                onPick = { on ->
                    if (on == remindersOn) return@InkSegmented
                    if (on) {
                        Reminders.enable(context)
                        if (denied) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        Reminders.disable(context)
                    }
                    remindersOn = on
                },
            )
            Spacer(Modifier.height(8.dp))
            SettingsCaption("One evening reminder while today's trial is unsealed.")
            if (remindersOn && denied) {
                Spacer(Modifier.height(4.dp))
                SettingsCaption("Android has notifications muted, so the Summons arrives silently.")
            }
        }
    }
}
