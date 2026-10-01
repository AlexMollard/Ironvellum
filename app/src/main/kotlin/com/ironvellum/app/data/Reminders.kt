package com.ironvellum.app.data

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import androidx.core.app.NotificationManagerCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.ironvellum.app.MainActivity
import com.ironvellum.app.R
import com.ironvellum.app.domain.Streak
import com.ironvellum.app.domain.Summons
import kotlinx.coroutines.flow.firstOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * The one bring-back mechanism the app has: an optional evening Summons.
 *
 * Everything else about the weekly loop waits for the lifter to open the app;
 * this is the only thing that reaches out. Deliberately minimal: one toggle,
 * one fixed evening hour, no per-day schedule, no oath guilt in the copy.
 * Off until the lifter turns it on.
 *
 * It speaks only when there is something to summon to: a rite the cycle puts
 * on today, not yet sealed and not already under way. A respite, an
 * unscheduled cycle or a trial in progress gets silence.
 */
object Reminders {
    private const val PREFS = "reminders"
    private const val KEY_ENABLED = "enabled"
    private const val WORK_NAME = "summons"

    /**
     * The 24-hour periodic job this replaced. A periodic interval drifts off
     * the wall clock at every DST change and timezone move, so each run now
     * schedules the next local 19:30 itself; the old job is cancelled wherever
     * the new one is scheduled, or an upgraded install would fire twice.
     */
    private const val LEGACY_WORK_NAME = "daily-reminder"

    fun enabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    private fun setEnabled(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_ENABLED, value)
        }
    }

    /** Turns the Summons on and schedules the next 19:30. Safe to call again. */
    fun enable(context: Context) {
        setEnabled(context, true)
        Notifications.ensureChannels(context)
        schedule(context, ExistingWorkPolicy.REPLACE)
    }

    fun disable(context: Context) {
        setEnabled(context, false)
        val work = WorkManager.getInstance(context)
        work.cancelUniqueWork(WORK_NAME)
        work.cancelUniqueWork(LEGACY_WORK_NAME)
        NotificationManagerCompat.from(context).cancel(Notifications.ID_SUMMONS)
    }

    /**
     * App start: keeps a queued run (a launch must not move tonight's
     * Summons), and gives an install that has none, or only the legacy
     * periodic job, its first one-time run.
     */
    fun ensureScheduled(context: Context) {
        if (enabled(context)) schedule(context, ExistingWorkPolicy.KEEP)
    }

    /**
     * Recomputes the next 19:30 from the clock as it reads now: after each
     * run, and when the timezone or the time itself changes (TimeChangeReceiver).
     */
    fun reschedule(context: Context) {
        if (enabled(context)) schedule(context, ExistingWorkPolicy.REPLACE)
    }

    private fun schedule(context: Context, policy: ExistingWorkPolicy) {
        val work = WorkManager.getInstance(context)
        work.cancelUniqueWork(LEGACY_WORK_NAME)
        // No exact alarm: a Summons a few minutes late is fine, and the
        // permission is not worth asking for it.
        val delay = Summons.delayUntilNext(ZonedDateTime.now(), minLead = Summons.MIN_LEAD)
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .addTag(WORK_NAME)
            .build()
        work.enqueueUniqueWork(WORK_NAME, policy, request)
    }

    /** The worker's whole job. Returns false when it had nothing to say. */
    suspend fun notifyIfWanted(context: Context, repo: Repository): Boolean {
        if (!enabled(context)) return false
        val canPost = Notifications.access(context, Notifications.CHANNEL_SUMMONS).canPost
        if (!canPost) return false

        // What today actually is: a rite, a respite, under way, or already done.
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        fun dayOf(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

        // A respite or an unscheduled cycle has nothing to summon to. The oath
        // does not need defending on a free day either: one trial in seven
        // keeps it, and the Summons never nags about it on its own.
        val rite = repo.observePresets().firstOrNull()
            ?.firstOrNull { it.scheduledDay == today.dayOfWeek.value }

        val history = repo.observeHistory().firstOrNull() ?: emptyList()
        val dates = history.mapNotNull { it.first.completedAtMs?.let(::dayOf) }.toSet()
        // Sealed today, or mid-trial: the kindest notification is silence.
        val live = repo.observeLiveSession().firstOrNull()
        if (!Summons.wanted(
                riteScheduledToday = rite != null,
                sealedToday = today in dates,
                liveToday = live != null && dayOf(live.startedAtMs) == today,
                canPost = canPost,
            )
        ) return false
        rite ?: return false

        val oath = Streak.current(dates, today)
        val copy = Summons.copy(
            riteName = rite.name,
            exercises = rite.entries.size,
            sets = rite.entries.sumOf { it.targetSets },
            oathDays = oath,
            nextDeedDays = DEED_MILESTONES.firstOrNull { it > oath },
            oathAtRisk = Streak.atRisk(dates, today),
        )

        // To Today, where the scheduled rite is one tap from beginning.
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(Notifications.EXTRA_OPEN_TAB, Notifications.TAB_TODAY)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            context, Notifications.REQUEST_SUMMONS, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, Notifications.CHANNEL_SUMMONS)
            .setSmallIcon(R.drawable.ic_reminder)
            .setColor(Notifications.ACCENT)
            .setContentTitle(copy.title)
            .setContentText(copy.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(copy.bigText))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        // Checked inline where lint can see it; Notifications.access is the same test.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        NotificationManagerCompat.from(context).notify(Notifications.ID_SUMMONS, notification)
        return true
    }

    /** Oath deed thresholds — keep in step with the TrainingStreak deeds. */
    private val DEED_MILESTONES = listOf(3, 7, 14, 30, 100)
}
