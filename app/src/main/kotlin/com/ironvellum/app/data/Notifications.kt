package com.ironvellum.app.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.ironvellum.app.R
import com.ironvellum.app.domain.RestAlert
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.ui.asString

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

    // A channel's sound and vibration are frozen when it is created, so each way
    // of announcing a rest's end ([RestAlert]) is a channel of its own, and
    // "rest" stays the vibrate-only one it always was.
    const val CHANNEL_REST_SOUND = "rest_sound"
    const val CHANNEL_REST_SILENT = "rest_silent"

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
    const val REQUEST_SET_DONE = 6
    const val REQUEST_SKIP_REST = 7
    const val REQUEST_EXTEND_REST = 8

    /** Where a tap lands; read by MainActivity. */
    const val EXTRA_OPEN_TAB = "open_tab"
    const val EXTRA_SESSION_ID = "session_id"
    /** With [TAB_TRIAL]: open the trial with its seal prompt showing. */
    const val EXTRA_SEAL = "seal"
    const val TAB_INBOX = "inbox"
    const val TAB_TODAY = "today"
    const val TAB_TRIAL = "trial"

    /** The home-screen widgets' button: continue the live trial, or begin today's rite, as Today does. */
    const val TAB_BEGIN = "begin"

    /** With [TAB_BEGIN]: no rite to begin, so begin an empty workout (Today's own link). */
    const val EXTRA_EMPTY = "empty"

    /** The widgets' own tap: the app on Today, and the plan builder for a lifter with no plan. */
    const val TAB_HOME = "home"
    const val TAB_FORGE = "forge"

    /** The channel a rest's end is announced on under [alert]. */
    fun restChannel(alert: RestAlert): String = when (alert) {
        RestAlert.VIBRATE -> CHANNEL_REST
        RestAlert.SOUND_VIBRATE -> CHANNEL_REST_SOUND
        RestAlert.SILENT -> CHANNEL_REST_SILENT
    }

    /** The accent the shade tints the small icon and actions with: the theme's own emerald, as the phone's mode is now. */
    fun accent(context: Context): Int = (context.applicationContext as com.ironvellum.app.IronvellumApp).appearanceStore.accent()

    /**
     * Idempotent; called from Application.onCreate so every channel exists
     * before anything posts. A channel's importance and vibration are frozen
     * at first creation (the lifter owns them after that), so these values
     * only ever reach a fresh install.
     */
    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        // Resolved now, in the mode and language in force; Words.setThemed registers the channels again to rename them.
        fun text(plain: Int, themed: Int) = UiText.themed(plain, themed).asString(context)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_SUMMONS, Words.theSummons.c, NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = text(R.string.notification_channel_summons_description, R.string.notification_channel_summons_description__t)
                },
                NotificationChannel(CHANNEL_ALLIES, Words.ally.cpl, NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = text(R.string.notification_channel_allies_description, R.string.notification_channel_allies_description__t)
                },
                NotificationChannel(
                    CHANNEL_TRIAL,
                    text(R.string.notification_channel_trial_label, R.string.notification_channel_trial_label__t),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = text(R.string.notification_channel_trial_description, R.string.notification_channel_trial_description__t)
                },
                // High so it can reach a locked phone on the bench; one short
                // buzz and no sound, because a gym is loud and a chime is not
                // what anyone between sets wants from their pocket.
                NotificationChannel(CHANNEL_REST, context.getString(R.string.notification_channel_rest_label), NotificationManager.IMPORTANCE_HIGH).apply {
                    description = context.getString(R.string.notification_channel_rest_description)
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 350)
                    setSound(null, null)
                },
                NotificationChannel(
                    CHANNEL_REST_SOUND,
                    context.getString(R.string.notification_channel_rest_sound_label),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = context.getString(R.string.notification_channel_rest_sound_description)
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 350)
                    setSound(
                        Settings.System.DEFAULT_NOTIFICATION_URI,
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                },
                NotificationChannel(
                    CHANNEL_REST_SILENT,
                    context.getString(R.string.notification_channel_rest_silent_label),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = context.getString(R.string.notification_channel_rest_silent_description)
                    enableVibration(false)
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
