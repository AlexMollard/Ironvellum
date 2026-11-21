package com.ironvellum.app.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.Manifest
import android.app.PendingIntent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ironvellum.app.MainActivity
import com.ironvellum.app.R
import com.ironvellum.app.WorkoutSessionService
import com.ironvellum.app.data.InboxNotifier
import com.ironvellum.app.data.Notifications
import com.ironvellum.app.data.cloud.InboxItem
import com.ironvellum.app.data.cloud.Reaction
import com.ironvellum.app.domain.SessionSet

/** Shell-only notification previews; declared only in the debug manifest. */
class NotificationPreviewReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val manager = NotificationManagerCompat.from(context)
        if (intent.action == ACTION_CLEAR) {
            repeat(6) { manager.cancel(TAG, it) }
            return
        }
        if (intent.action != ACTION_SHOW) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        Notifications.ensureChannels(context)
        // Construct the isolated identity before any production card builder:
        // even an unused real PendingIntent can update another alert's extras.
        val pending = PendingIntent.getActivity(
            context, 9_001,
            Intent(context, MainActivity::class.java).setAction(ACTION_SHOW)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val now = System.currentTimeMillis()
        val accepted = InboxItem.RequestAccepted(now, "preview-sam", "Sam")
        val comment = InboxItem.NewComment(now, "preview-sam", "Sam", "preview-workout", "Push Day", "preview-comment", "Great session! Nice work on those presses.")
        val reaction = InboxItem.NewReaction(now, "preview-sam", "Sam", "preview-workout", "Push Day", Reaction.FLAME)
        val goal = InboxItem.CircleGoalMet(now, "preview-sam", "Sam", "North Gate")
        val cards = listOf(
            InboxNotifier.build(context, listOf(comment), pending),
            InboxNotifier.build(context, listOf(reaction), pending),
            InboxNotifier.build(context, listOf(goal), pending),
            InboxNotifier.build(context, listOf(comment, accepted), pending),
            WorkoutSessionService.restOverNotification(context, -1L, listOf(
                SessionSet(id = -1L, exerciseId = -1L, exerciseName = "Overhead Press", exercisePosition = 0, setIndex = 0, reps = 8, weightKg = 40.0, done = false),
            ), pending),
            InboxNotifier.build(context, listOf(accepted), pending),
        )
        // A separate PendingIntent identity and harmless app-open tap: a preview
        // must never tick a real set, finish a workout or mark the inbox read.
        val label = context.getString(R.string.debug_notification_preview)
        cards.forEachIndexed { index, card ->
            if (!Notifications.access(context, card.channelId).canPost) return@forEachIndexed
            val preview = NotificationCompat.Builder(context, card)
                .clearActions()
                .setContentIntent(pending)
                .setSubText(label)
                .setOngoing(false)
                .setAutoCancel(true)
                .setSilent(true)
                .setTimeoutAfter(900_000)
            card.publicVersion?.let {
                preview.setPublicVersion(NotificationCompat.Builder(context, it)
                    .clearActions().setContentIntent(pending).setSubText(label).build())
            }
            manager.notify(TAG, index, preview.build())
        }
    }

    companion object {
        const val TAG = "notification-preview"
        const val ACTION_SHOW = "com.ironvellum.app.debug.SHOW_NOTIFICATIONS"
        const val ACTION_CLEAR = "com.ironvellum.app.debug.CLEAR_NOTIFICATIONS"
    }
}
