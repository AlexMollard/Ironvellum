package com.ironvellum.app.data

import android.Manifest
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
import com.ironvellum.app.data.cloud.Cloud
import com.ironvellum.app.data.cloud.Inbox
import com.ironvellum.app.data.cloud.InboxItem
import com.ironvellum.app.data.cloud.InboxWorker
import com.ironvellum.app.ui.social.displayName

/**
 * Turns new ally activity into one Android notification.
 *
 * The inbox is derived on the server and only read while the app is open, so
 * without this a comment on a workout sat unseen until the lifter happened to
 * visit the Allies tab. Deliberately one replaced notification, never a stack:
 * a busy evening must not bury the shade under a dozen rows.
 */
object InboxNotifier {
    private const val PREFS = "inbox_notifier"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_MARK = "mark_ms"
    private const val KEY_MARK_USER = "mark_user"
    private const val KEY_ASKED = "asked_permission"
    private const val KEY_TRIBUTED = "tributed"

    /** Tributes remembered for [coalesce]: a month of a busy lifter's, far more than the inbox's 100 rows. */
    internal const val TRIBUTE_MEMORY = 300

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Default on: the lifter opted into allies by signing in, and the OS permission is the real gate. */
    fun enabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)

    /**
     * The poll exists only for this setting, so it follows it: off cancels the
     * worker outright rather than leaving it to wake and return. The setting is
     * only reachable signed in, so on needs no account check here.
     */
    fun setEnabled(context: Context, value: Boolean) {
        prefs(context).edit { putBoolean(KEY_ENABLED, value) }
        if (value) {
            if (Cloud.config.value != null) InboxWorker.schedule(context)
        } else {
            cancel(context)
            InboxWorker.cancel(context)
        }
    }

    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /**
     * True when a notification would actually be shown: preference on,
     * permission granted, notifications on for the app and the Allies channel
     * not blocked on its own.
     */
    fun canNotify(context: Context): Boolean =
        enabled(context) && Notifications.access(context, Notifications.CHANNEL_ALLIES).canPost

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

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(Notifications.ID_ALLIES)
    }

    /**
     * Sign-out or account switch: the next lifter must start from a clean
     * baseline, not inherit this one's mark, and a signed-out phone has
     * nothing to poll for.
     */
    fun reset(context: Context) {
        prefs(context).edit {
            remove(KEY_MARK)
            remove(KEY_MARK_USER)
            remove(KEY_TRIBUTED)
        }
        cancel(context)
        InboxWorker.cancel(context)
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
                remove(KEY_TRIBUTED)
            }
            return
        }
        // Read on another device (or in the app): the server's seen-at has
        // passed everything, so the posted row is stale news. Taken from
        // seen-at, not the local mark, which advances as soon as we post.
        if (inbox.unread == 0) cancel(context)

        val floor = maxOf(p.getLong(KEY_MARK, 0L), inbox.seenAtMs ?: 0L)
        val newer = inbox.items.filter { it.occurredAtMs > floor }.sortedByDescending { it.occurredAtMs }
        if (newer.isEmpty()) return
        val tributed = p.getString(KEY_TRIBUTED, null).orEmpty().split('\n').filter { it.isNotEmpty() }
        val fresh = coalesce(newer, tributed.toSet())
        // Advance before posting: a crash between the two would otherwise
        // repeat the same notification every half hour.
        p.edit {
            putLong(KEY_MARK, maxOf(floor, newer.first().occurredAtMs))
            putString(KEY_TRIBUTED, rememberTributes(tributed, fresh).joinToString("\n"))
        }
        if (fresh.isEmpty() || !canNotify(context)) return

        val latest = line(fresh.first())
        val title = if (fresh.size == 1) "A missive" else "${fresh.size} new missives"
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(Notifications.EXTRA_OPEN_TAB, Notifications.TAB_INBOX)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            context, Notifications.REQUEST_ALLIES, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // The lock screen shows who and what was said to anyone holding the
        // phone; it gets only that something arrived.
        val public = NotificationCompat.Builder(context, Notifications.CHANNEL_ALLIES)
            .setSmallIcon(R.drawable.ic_reminder)
            .setColor(Notifications.ACCENT)
            .setContentTitle(title)
            .setContentText(if (fresh.size == 1) "New missive from an ally" else "New missives from your allies")
            .build()
        val notification = NotificationCompat.Builder(context, Notifications.CHANNEL_ALLIES)
            .setSmallIcon(R.drawable.ic_reminder)
            .setColor(Notifications.ACCENT)
            .setContentTitle(title)
            .setContentText(latest)
            .setStyle(NotificationCompat.BigTextStyle().bigText(latest))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
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
        NotificationManagerCompat.from(context).notify(Notifications.ID_ALLIES, notification)
    }

    /**
     * A tribute notifies once per ally per trial. Taking one back and paying
     * it again stamps a fresh row on the server, which without this read as
     * new every time: a lifter toggling Flame on and off could ring someone's
     * phone at will. [tributed] holds "trial:ally" keys already announced.
     */
    internal fun coalesce(items: List<InboxItem>, tributed: Set<String>): List<InboxItem> =
        items.filterNot { it is InboxItem.NewReaction && tributeKey(it) in tributed }

    /** [tributed] plus the tributes in [fresh], newest last, capped at [TRIBUTE_MEMORY]. */
    internal fun rememberTributes(tributed: List<String>, fresh: List<InboxItem>): List<String> =
        (tributed + fresh.filterIsInstance<InboxItem.NewReaction>().map(::tributeKey))
            .distinct()
            .takeLast(TRIBUTE_MEMORY)

    private fun tributeKey(item: InboxItem.NewReaction) = "${item.sessionId}:${item.actorId}"

    private fun line(item: InboxItem): String = when (item) {
        is InboxItem.NewComment -> "${item.actorName} left a remark: ${item.body}"
        is InboxItem.NewReply -> {
            val headline = item.sessionHeadline.ifBlank { "a trial" }
            "${item.actorName} replied on $headline: ${item.body}"
        }
        is InboxItem.NewReaction -> {
            val reaction = item.reaction.displayName()
            val headline = item.sessionHeadline.ifBlank { "your trial" }
            "${item.actorName} paid $reaction tribute to $headline"
        }
        is InboxItem.FriendRequest -> "${item.actorName} wants to ally with you"
        is InboxItem.RequestAccepted -> "${item.actorName} accepted your request"
        is InboxItem.NewCircleMember -> "${item.actorName} joined your circle ${item.circleName}"
        is InboxItem.CircleGoalMet -> "Your circle ${item.circleName} met its weekly goal"
    }
}
