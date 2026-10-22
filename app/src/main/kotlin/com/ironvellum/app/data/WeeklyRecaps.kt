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
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.ironvellum.app.MainActivity
import com.ironvellum.app.R
import com.ironvellum.app.domain.SetRecords
import com.ironvellum.app.domain.RecapSchedule
import com.ironvellum.app.domain.WeeklyRecap
import com.ironvellum.app.domain.PeriodStats
import com.ironvellum.app.ui.asString
import com.ironvellum.app.ui.program.toPlanned
import com.ironvellum.app.ui.recap.RecapCopy
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * The weekly recap's two jobs outside the screen: reading a week into a [WeeklyRecap], and the Sunday 19:00 notification.
 *
 * The notification is not a second reminder system: it books itself the way [Reminders] does (one WorkManager run at a
 * time, which books the next as its last act, and which a clock change re-books), on a channel of its own that the phone's
 * settings can switch off. [RecapSchedule] decides what a run does; this posts it. It follows the notification permission
 * the app already asks for and never asks again.
 */
object WeeklyRecaps {
    private const val PREFS = "weekly_recap"
    private const val KEY_LAST_WEEK = "last_delivered_week"
    private const val WORK_NAME = "weekly-recap"

    /** The Monday of the last week delivered (as an epoch day); null before the first. A week is never delivered twice. */
    fun lastDelivered(context: Context): LocalDate? {
        val day = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_LAST_WEEK, Long.MIN_VALUE)
        return if (day == Long.MIN_VALUE) null else LocalDate.ofEpochDay(day)
    }

    private fun markDelivered(context: Context, week: LocalDate) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putLong(KEY_LAST_WEEK, week.toEpochDay()) }
    }

    /** The recap of the week starting [weekStart], read from the history and the plan as they are now. */
    suspend fun load(repo: Repository, weekStart: LocalDate, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): WeeklyRecap {
        val history = repo.observeHistory().first()
        val exercises = repo.observeExercises().first().associateBy { it.id }
        val plan = repo.observePresets().first().filter { it.scheduledDay != null }.map { it.toPlanned() }
        val bodyweightAt = SetRecords.bodyweightLookup(repo.observeStats().first())
        return WeeklyRecap.build(history, exercises, bodyweightAt, plan, weekStart, today, zone)
    }

    // ---- scheduling ----

    /** App start: books the next Sunday 19:00 unless a run is already queued, which a launch must not move. */
    fun ensureScheduled(context: Context) = schedule(context, ExistingWorkPolicy.KEEP)

    /** Recomputes the next Sunday 19:00 from the clock as it reads now: after each run and when the clock or zone changes. */
    fun reschedule(context: Context) = schedule(context, ExistingWorkPolicy.REPLACE)

    private fun schedule(context: Context, policy: ExistingWorkPolicy) {
        // No exact alarm: a recap a few minutes late is fine, and the permission is not worth asking for it.
        val delay = RecapSchedule.delayUntilNext(ZonedDateTime.now())
        val request = OneTimeWorkRequestBuilder<RecapWorker>()
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .addTag(WORK_NAME)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, policy, request)
    }

    // ---- delivery ----

    /**
     * The worker's whole job: what [RecapSchedule.decide] says for [now]. On a delivery the week is marked delivered
     * only once the notification is actually posted, so a refusal (permission, channel off) is never recorded as a delivery.
     */
    suspend fun deliverIfDue(context: Context, repo: Repository, now: ZonedDateTime = ZonedDateTime.now()): RecapSchedule.Verdict {
        val history = repo.observeHistory().first()
        val verdict = RecapSchedule.decide(now, lastDelivered(context)) { week ->
            PeriodStats.workoutsIn(history, week, week.plusDays(7), now.zone)
        }
        if (verdict is RecapSchedule.Verdict.Deliver && post(context, load(repo, verdict.week, now.toLocalDate(), now.zone))) {
            markDelivered(context, verdict.week)
        }
        return verdict
    }

    /**
     * Posts [recap]'s notification. The lock screen shows the public version only, with no figures
     * (VISIBILITY_PRIVATE). False when nothing could be posted: the permission, the app or the channel is off.
     */
    fun post(context: Context, recap: WeeklyRecap): Boolean {
        if (!Notifications.access(context, Notifications.CHANNEL_RECAP).canPost) return false
        // Checked inline where lint can see it; Notifications.access is the same test.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val copy = RecapCopy.of(recap)
        val accent = Notifications.accent(context)
        val open = pending(context, Notifications.REQUEST_RECAP, recap.weekStart, share = false)
        val share = pending(context, Notifications.REQUEST_RECAP_SHARE, recap.weekStart, share = true)
        val lockScreen = NotificationCompat.Builder(context, Notifications.CHANNEL_RECAP)
            .setSmallIcon(R.drawable.ic_reminder)
            .setColor(accent)
            .setContentTitle(copy.publicTitle.asString(context))
            .setContentText(copy.publicText.asString(context))
            .build()
        val notification = NotificationCompat.Builder(context, Notifications.CHANNEL_RECAP)
            .setSmallIcon(R.drawable.ic_reminder)
            .setColor(accent)
            .setContentTitle(copy.title.asString(context))
            .setContentText(copy.text.asString(context))
            .setStyle(NotificationCompat.BigTextStyle().bigText(copy.expanded.asString(context)))
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(0, context.getString(R.string.recap_notify_open), open)
            .addAction(0, context.getString(R.string.recap_notify_share), share)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(lockScreen)
            .build()
        NotificationManagerCompat.from(context).notify(Notifications.ID_RECAP, notification)
        return true
    }

    /** A tap into the app: the recap's screen for [week], or with [share] its share screen. One request code per target. */
    private fun pending(context: Context, requestCode: Int, week: LocalDate, share: Boolean): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(Notifications.EXTRA_OPEN_TAB, Notifications.TAB_RECAP)
            .putExtra(Notifications.EXTRA_RECAP_WEEK, week.toEpochDay())
            .putExtra(Notifications.EXTRA_RECAP_SHARE, share)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
