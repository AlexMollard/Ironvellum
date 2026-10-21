package com.ironvellum.app

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ironvellum.app.data.Notifications
import com.ironvellum.app.data.TrialPrefs
import com.ironvellum.app.data.Units
import com.ironvellum.app.data.Words
import com.ironvellum.app.domain.LiveWorkout
import com.ironvellum.app.domain.RestTimer
import com.ironvellum.app.domain.SessionSet
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.ui.asString

/**
 * The one ongoing notification of a live workout, painted from [LiveWorkout.Content]. On Android 16 with the lifter's
 * switch on and Android's own promotion switch allowed it is a Live Update (a ProgressStyle card, a status-bar chip, a
 * lock-screen card); anywhere else it is the ordinary ongoing notification of every earlier version, with the same
 * words. Either way it is the same id and channel, so a switch flipped mid-workout replaces the card, never adds one.
 */
object LiveWorkoutNotification {

    /** True when this notification should ask Android to promote it: Android 16, the app's own switch, and Android's. */
    fun canPromote(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA &&
            TrialPrefs(context).liveUpdate &&
            NotificationManagerCompat.from(context).canPostPromotedNotifications()

    /**
     * The notification for [sets] as they stand. [live] and [showLifts] default to the lifter's settings; they are
     * parameters so a test can ask for either shape on any Android version.
     */
    fun build(
        context: Context,
        sessionId: Long,
        sets: List<SessionSet>,
        routine: String,
        startedAtMs: Long?,
        rest: RestTimer?,
        live: Boolean = canPromote(context),
        showLifts: Boolean = TrialPrefs(context).showLiftsOnLock,
    ): Notification {
        val resting = rest != null && !rest.isOver(SystemClock.elapsedRealtime())
        val content = LiveWorkout.content(sets, routine, resting) { Units.withUnit(it) }
        val builder = NotificationCompat.Builder(context, Notifications.CHANNEL_TRIAL)
            .setSmallIcon(R.drawable.ic_reminder)
            .setColor(Notifications.accent(context))
            .setContentTitle(content.title.asString(context))
            .setContentText(content.body.asString(context))
            .setSubText(content.subText.asString(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(trialIntent(context, sessionId, seal = false, requestCode = Notifications.REQUEST_TRIAL))

        if (live) {
            // A Live Update: one segment per exercise, the sets done as the fill. The chip counts the rest down
            // (the chronometer below) and between sets shows the short text.
            val style = NotificationCompat.ProgressStyle()
            if (content.segments.isEmpty()) {
                style.setProgressIndeterminate(true)
            } else {
                style.setProgressSegments(content.segments.map { NotificationCompat.ProgressStyle.Segment(it) })
                    .setProgress(content.done)
            }
            builder.setStyle(style).setRequestPromotedOngoing(true)
            content.chip?.let { builder.setShortCriticalText(it.asString(context)) }
        } else {
            content.expanded?.let { builder.setStyle(NotificationCompat.BigTextStyle().bigText(it.asString(context))) }
            builder.setProgress(content.total.coerceAtLeast(1), content.done, content.total == 0)
        }

        if (resting) {
            // The chronometer counts toward a wall-clock instant; the rest is kept in elapsed time, so translate
            // the time left, not the end.
            val left = rest!!.remainingMs(SystemClock.elapsedRealtime())
            builder.setWhen(System.currentTimeMillis() + left)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
        } else if (startedAtMs != null) {
            builder.setWhen(startedAtMs).setShowWhen(true).setUsesChronometer(true)
        }

        // Three actions at most show, so Finish waits for a moment with nothing else on offer. Set done, Skip rest and
        // +15 s are broadcasts: they run from the lock screen. Finish opens the trial with its seal prompt: sealing
        // pays XP and a hasty one asks first, which cannot happen in the shade.
        content.nextSetId?.let {
            builder.addAction(0, context.getString(R.string.notification_trial_action_set_done), broadcast(context, TrialActionReceiver.ACTION_SET_DONE, sessionId, it, Notifications.REQUEST_SET_DONE))
        }
        if (resting) {
            builder.addAction(0, context.getString(R.string.notification_trial_action_skip_rest), broadcast(context, TrialActionReceiver.ACTION_SKIP_REST, sessionId, -1L, Notifications.REQUEST_SKIP_REST))
            builder.addAction(
                0,
                context.getString(R.string.notification_trial_action_extend_rest, RestTimer.EXTEND_SECONDS),
                broadcast(context, TrialActionReceiver.ACTION_EXTEND_REST, sessionId, -1L, Notifications.REQUEST_EXTEND_REST),
            )
        } else if (content.done > 0) {
            builder.addAction(0, Words.seal.c, trialIntent(context, sessionId, seal = true, requestCode = Notifications.REQUEST_SEAL))
        }

        if (showLifts) {
            // Public on the lock screen: the set in hand and its load. Never a note.
            builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        } else {
            // Private: the lock screen shows this stand-in until the phone is unlocked.
            builder.setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(privateVersion(context, sessionId))
        }
        return builder.build()
    }

    /** "Workout in progress / Unlock to see your set." and a button that opens the workout. */
    private fun privateVersion(context: Context, sessionId: Long): Notification =
        NotificationCompat.Builder(context, Notifications.CHANNEL_TRIAL)
            .setSmallIcon(R.drawable.ic_reminder)
            .setColor(Notifications.accent(context))
            .setContentTitle(UiText.themed(R.string.notification_channel_trial_label, R.string.notification_channel_trial_label__t).asString(context))
            .setContentText(context.getString(R.string.live_private_text))
            .setContentIntent(trialIntent(context, sessionId, seal = false, requestCode = Notifications.REQUEST_TRIAL))
            .addAction(0, context.getString(R.string.live_action_open), trialIntent(context, sessionId, seal = false, requestCode = Notifications.REQUEST_TRIAL))
            .build()

    /** Opens the live trial; with [seal], with its seal prompt showing. */
    fun trialIntent(context: Context, sessionId: Long, seal: Boolean, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(Notifications.EXTRA_OPEN_TAB, Notifications.TAB_TRIAL)
            .putExtra(Notifications.EXTRA_SESSION_ID, sessionId)
            .putExtra(Notifications.EXTRA_SEAL, seal)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** A button that does something without opening the app. Identity ignores extras, so each action has its own code. */
    private fun broadcast(context: Context, action: String, sessionId: Long, setId: Long, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context, requestCode, TrialActionReceiver.intent(context, action, sessionId, setId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
