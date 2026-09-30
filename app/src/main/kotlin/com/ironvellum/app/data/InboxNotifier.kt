package com.ironvellum.app.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import com.ironvellum.app.MainActivity
import com.ironvellum.app.R
import com.ironvellum.app.data.cloud.Inbox
import com.ironvellum.app.data.cloud.InboxItem

/**
 * Turns new ally activity into one Android notification.
 *
 * The inbox is derived on the server and only read while the app is open, so
 * without this a comment on a workout sat unseen until the lifter happened to
 * visit the Allies tab. Deliberately one replaced notification, never a stack:
 * a busy evening must not bury the shade under a dozen rows.
 */
object InboxNotifier {
    const val CHANNEL_ID = "allies"
    const val EXTRA_OPEN_TAB = "open_tab"
    const val TAB_INBOX = "inbox"

    private const val PREFS = "inbox_notifier"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_MARK = "mark_ms"
    private const val KEY_MARK_USER = "mark_user"
    private const val KEY_ASKED = "asked_permission"
    private const val NOTIFICATION_ID = 2

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Default on: the lifter opted into allies by signing in, and the OS permission is the real gate. */
    fun enabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, value: Boolean) {
        prefs(context).edit { putBoolean(KEY_ENABLED, value) }
        if (!value) cancel(context)
    }

    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** True when a notification would actually be shown: preference on, permission granted, not blocked in system settings. */
    fun canNotify(context: Context): Boolean =
        enabled(context) && hasPermission(context) && NotificationManagerCompat.from(context).areNotificationsEnabled()

    /**
     * True exactly once per install, and only when asking could change the
     * outcome. Recording the ask before the dialog shows means a denial (or a
     * dismissed dialog) is never followed by a second nag.
     */
    fun takeFirstAsk(context: Context): Boolean {
        if (prefs(context).getBoolean(KEY_ASKED, false)) return false
        prefs(context).edit { putBoolean(KEY_ASKED, true) }
        return enabled(context) && !hasPermission(context)
    }

    /** Idempotent; called from Application.onCreate so the channel exists before any post. */
    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Allies", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Requests, comments and reactions from your allies."
            },
        )
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    /** Sign-out or account switch: the next lifter must start from a clean baseline, not inherit this one's mark. */
    fun reset(context: Context) {
        prefs(context).edit {
            remove(KEY_MARK)
            remove(KEY_MARK_USER)
        }
        cancel(context)
    }

    /**
     * Posts at most one notification for items newer than both the stored
     * high-water mark and the server's seen-at. The first call for an account
     * only records the mark: notifying the whole existing backlog after
     * install or sign-in would be a flood of things the lifter already knows.
     */
    fun onInbox(context: Context, userId: String, inbox: Inbox) {
        val p = prefs(context)
        val newest = inbox.items.maxOfOrNull { it.occurredAtMs }
        if (p.getString(KEY_MARK_USER, null) != userId) {
            p.edit {
                putString(KEY_MARK_USER, userId)
                putLong(KEY_MARK, newest ?: 0L)
            }
            return
        }
        val floor = maxOf(p.getLong(KEY_MARK, 0L), inbox.seenAtMs ?: 0L)
        val fresh = inbox.items.filter { it.occurredAtMs > floor }.sortedByDescending { it.occurredAtMs }
        if (fresh.isEmpty()) return
        // Advance before posting: a crash between the two would otherwise
        // repeat the same notification every half hour.
        p.edit { putLong(KEY_MARK, maxOf(floor, fresh.first().occurredAtMs)) }
        if (!canNotify(context)) return

        val latest = line(fresh.first())
        val (title, text) = if (fresh.size == 1) "Ironvellum" to latest else "${fresh.size} new in your inbox" to latest
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_OPEN_TAB, TAB_INBOX)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        // Checked inline where lint can see it: hasPermission() is the same
        // test, but lint only trusts a checkSelfPermission in this function.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private fun line(item: InboxItem): String = when (item) {
        is InboxItem.NewComment -> "${item.actorName} commented: ${item.body}"
        is InboxItem.NewReaction -> {
            val reaction = item.reaction.name.lowercase().replaceFirstChar { it.uppercase() }
            val headline = item.sessionHeadline.ifBlank { "your workout" }
            "${item.actorName} reacted $reaction to $headline"
        }
        is InboxItem.FriendRequest -> "${item.actorName} wants to ally with you"
        is InboxItem.RequestAccepted -> "${item.actorName} accepted your request"
        is InboxItem.NewBandmate -> "${item.actorName} joined your band ${item.bandName}"
    }
}
