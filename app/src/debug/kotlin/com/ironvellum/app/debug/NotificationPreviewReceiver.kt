package com.ironvellum.app.debug

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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
import org.json.JSONArray
import org.json.JSONObject

/** Shell-only notification previews; declared only in the debug manifest. */
class NotificationPreviewReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val requestId = intent.validDevelopmentRequestId()
        val action = when (intent.action) {
            ACTION_SHOW -> "show"
            ACTION_CLEAR -> "clear"
            else -> null
        }
        val scenario = if (intent.hasExtra("scenario")) intent.getStringExtra("scenario") else "all"
        val requestedIds = when (scenario) {
            "all" -> allIds
            else -> scenarioIds[scenario]?.let(::listOf)
        }
        val submitted = JSONArray()
        val blocked = JSONArray()
        val reply = developmentReply(requestId)
            .put("operation", "notification_preview")
            .put("action", action ?: JSONObject.NULL)
            .put("scenario", scenario?.takeIf { requestedIds != null } ?: JSONObject.NULL)
            .put("requestedIds", JSONArray())
            .put("submittedIds", submitted)
            .put("blocked", blocked)

        val error = when {
            intent.hasExtra(DEVELOPMENT_REQUEST_ID) && requestId == null -> "invalid_request"
            action == null -> "unsupported_action"
            requestedIds == null || (action == "clear" && scenario != "all") -> "unknown_scenario"
            else -> null
        }
        if (error != null) {
            reply.withDevelopmentError(error)
        } else {
            val ids = checkNotNull(requestedIds)
            reply.put("requestedIds", JSONArray(ids))
            try {
                val manager = NotificationManagerCompat.from(context)
                if (action == "clear") {
                    ids.forEach { manager.cancel(TAG, it) }
                } else {
                    show(context, manager, ids, submitted, blocked)
                }
                reply.put("ok", true)
            } catch (_: Exception) {
                reply.withDevelopmentError("status_unavailable")
            }
        }
        if (isOrderedBroadcast) resultData = reply.developmentWireData()
    }

    // Notifications.access checks the runtime grant immediately before posting.
    @SuppressLint("MissingPermission")
    private fun show(
        context: Context,
        manager: NotificationManagerCompat,
        ids: List<Int>,
        submitted: JSONArray,
        blocked: JSONArray,
    ) {
        Notifications.ensureChannels(context)
        // Construct the isolated identity before any production card builder:
        // even an unused real PendingIntent can update another alert's extras.
        val pending = PendingIntent.getActivity(
            context, 9_001,
            Intent(context, MainActivity::class.java).setAction(ACTION_SHOW)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val label = context.getString(R.string.debug_notification_preview)
        val now = System.currentTimeMillis()
        ids.forEach { id ->
            val card = buildCard(context, id, now, pending)
            val access = Notifications.access(context, card.channelId)
            val reason = when {
                !access.permissionGranted -> "permission_denied"
                !access.appEnabled -> "app_notifications_disabled"
                access.channelBlocked -> "channel_blocked"
                else -> null
            }
            if (reason != null) {
                blocked.put(JSONObject().put("id", id).put("channelId", card.channelId).put("reason", reason))
                return@forEach
            }
            // A preview tap must never tick a real set, finish a workout or mark the inbox read.
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
            manager.notify(TAG, id, preview.build())
            submitted.put(id)
        }
    }

    private fun buildCard(context: Context, id: Int, now: Long, pending: PendingIntent): Notification {
        if (id == 4) {
            return WorkoutSessionService.restOverNotification(context, -1L, listOf(
                SessionSet(id = -1L, exerciseId = -1L, exerciseName = "Overhead Press", exercisePosition = 0, setIndex = 0, reps = 8, weightKg = 40.0, done = false),
            ), pending)
        }
        val accepted = InboxItem.RequestAccepted(now, "preview-sam", "Sam")
        val comment = InboxItem.NewComment(now, "preview-sam", "Sam", "preview-workout", "Push Day", "preview-comment", "Great session! Nice work on those presses.")
        val items = when (id) {
            0 -> listOf(comment)
            1 -> listOf(InboxItem.NewReaction(now, "preview-sam", "Sam", "preview-workout", "Push Day", Reaction.FLAME))
            2 -> listOf(InboxItem.CircleGoalMet(now, "preview-sam", "Sam", "North Gate"))
            3 -> listOf(comment, accepted)
            5 -> listOf(accepted)
            else -> error("Invalid preview ID")
        }
        return InboxNotifier.build(context, items, pending)
    }

    companion object {
        const val TAG = "notification-preview"
        const val ACTION_SHOW = "com.ironvellum.app.debug.SHOW_NOTIFICATIONS"
        const val ACTION_CLEAR = "com.ironvellum.app.debug.CLEAR_NOTIFICATIONS"
        private val scenarioIds = mapOf("comment" to 0, "reaction" to 1, "group_goal" to 2, "batch" to 3, "rest" to 4, "accepted" to 5)
        private val allIds = (0..5).toList()
    }
}
