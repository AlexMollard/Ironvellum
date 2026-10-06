package com.ironvellum.app.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationManagerCompat
import com.ironvellum.app.ui.theme.IronvellumColors

/**
 * Every notification id, channel id, PendingIntent request code and tap extra
 * the app uses, in one place.
 *
 * They used to live beside each poster, and the ally notification and the live
 * trial both picked id 2: opening Allies cancelled "its" notification and took
 * the trial's foreground notification down with it. One table makes a second
 * collision visible at a glance.
 */
object Notifications {

    // Channel ids are stored by the system together with the lifter's
    // per-channel choices (muted, blocked, sound), so they are never renamed.
    const val CHANNEL_SUMMONS = "reminders"
    const val CHANNEL_ALLIES = "allies"
    const val CHANNEL_TRIAL = "workout"
    const val CHANNEL_REST = "rest"

    const val ID_SUMMONS = 1
    const val ID_TRIAL = 2
    const val ID_ALLIES = 3
    const val ID_REST_OVER = 4

    // PendingIntent identity ignores extras: two taps aimed at MainActivity with
    // the same request code would share one PendingIntent, and FLAG_UPDATE_CURRENT
    // would rewrite the other's destination. One code per tap target.
    const val REQUEST_SUMMONS = 1
    const val REQUEST_TRIAL = 2
    const val REQUEST_ALLIES = 3
    const val REQUEST_SEAL = 4
    const val REQUEST_REST_OVER = 5

    /** Where a tap lands; read by MainActivity. */
    const val EXTRA_OPEN_TAB = "open_tab"
    const val EXTRA_SESSION_ID = "session_id"
    /** With [TAB_TRIAL]: open the trial with its seal prompt showing. */
    const val EXTRA_SEAL = "seal"
    const val TAB_INBOX = "inbox"
    const val TAB_TODAY = "today"
    const val TAB_TRIAL = "trial"

    /** The accent the shade tints the small icon and actions with: the theme's own emerald. */
    val ACCENT: Int get() = IronvellumColors.Emerald.toArgb()

    /**
     * Idempotent; called from Application.onCreate so every channel exists
     * before anything posts. A channel's importance and vibration are frozen
     * at first creation (the lifter owns them after that), so these values
     * only ever reach a fresh install.
     */
    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_SUMMONS, "The Summons", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "One evening summons while today's trial is still unsealed."
                },
                NotificationChannel(CHANNEL_ALLIES, "Allies", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Requests, remarks and tributes from your allies."
                },
                NotificationChannel(CHANNEL_TRIAL, "Trial in progress", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Live progress of the trial you have open."
                },
                // High so it can reach a locked phone on the bench; one short
                // buzz and no sound, because a gym is loud and a chime is not
                // what anyone between sets wants from their pocket.
                NotificationChannel(CHANNEL_REST, "Rest between sets", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "One buzz when the rest between sets is over."
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 350)
                    setSound(null, null)
                },
            ),
        )
    }

    /**
     * What actually stands between a post and the shade. All three are
     * separate switches: the runtime grant (Android 13+), the app-wide toggle
     * in system settings, and the one channel being blocked on its own.
     */
    data class Access(
        val permissionGranted: Boolean,
        val appEnabled: Boolean,
        val channelBlocked: Boolean,
    ) {
        val canPost: Boolean get() = permissionGranted && appEnabled && !channelBlocked
    }

    fun access(context: Context, channelId: String): Access {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val manager = context.getSystemService(NotificationManager::class.java)
        val blocked = manager?.getNotificationChannel(channelId)?.importance == NotificationManager.IMPORTANCE_NONE
        return Access(
            permissionGranted = granted,
            appEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled(),
            channelBlocked = blocked,
        )
    }

    /**
     * The system screen that can lift the block in [access]: the app's page
     * when the app as a whole is off or the grant is missing (turning
     * notifications on there grants the permission too), the channel's own
     * page when only that channel was switched off. After a second denial the
     * permission dialog no longer appears at all, so this is the only way back.
     */
    fun settingsIntent(context: Context, channelId: String, access: Access): Intent {
        val intent = if (access.permissionGranted && access.appEnabled && access.channelBlocked) {
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
        } else {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        }
        return intent
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
