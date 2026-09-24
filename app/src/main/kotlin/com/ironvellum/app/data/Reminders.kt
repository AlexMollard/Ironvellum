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
import androidx.core.content.edit
import androidx.core.app.NotificationManagerCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.ironvellum.app.R
import com.ironvellum.app.domain.Titles
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * The one bring-back mechanism the app has: an optional daily reminder.
 *
 * Everything else about the weekly loop waits for the lifter to open the app;
 * this is the only thing that reaches out. Deliberately minimal: one toggle,
 * one fixed evening hour, no per-day schedule, no streak guilt in the copy.
 * Off until the lifter turns it on.
 */
object Reminders {
    private const val PREFS = "reminders"
    private const val KEY_ENABLED = "enabled"
    const val CHANNEL_ID = "reminders"
    private const val WORK_NAME = "daily-reminder"

    /** The hour the reminder fires. One number, one place to change it. */
    private val REMIND_AT: LocalTime = LocalTime.of(19, 30)

    fun enabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    private fun setEnabled(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_ENABLED, value)
        }
    }

    /** Idempotent; called from Application.onCreate so the channel always exists. */
    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Daily reminder",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "A once-a-day nudge that today's quest is still open."
            },
        )
    }

    /** Turns the reminder on and (re)enqueues the daily work. Safe to call again. */
    fun enable(context: Context) {
        setEnabled(context, true)
        ensureChannel(context)
        val delay = Duration.between(
            LocalDateTime.now(),
            LocalDateTime.of(LocalDate.now().plusDays(if (LocalDateTime.now().toLocalTime() < REMIND_AT) 0 else 1), REMIND_AT),
        )
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delay.toMinutes(), TimeUnit.MINUTES)
            .addTag(WORK_NAME)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            // KEEP: re-enabling must not reset the clock the lifter expects.
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun disable(context: Context) {
        setEnabled(context, false)
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    /** The worker's whole job. Returns false when it had nothing to say. */
    suspend fun notifyIfWanted(context: Context, repo: Repository): Boolean {
        if (!enabled(context)) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return false
        val pending = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // What today actually is: a quest, a rest day, or already done.
        val today = LocalDate.now()
        val history = repo.observeHistory().firstOrNull() ?: emptyList()
        val zone = java.time.ZoneId.systemDefault()
        val doneToday = history.any {
            it.first.completedAtMs?.let { ms ->
                java.time.Instant.ofEpochMilli(ms).atZone(zone).toLocalDate() == today
            } == true
        }
        // Already claimed today: the kindest notification is silence.
        if (doneToday) return false

        val dates = history.mapNotNull {
            it.first.completedAtMs?.let { ms -> java.time.Instant.ofEpochMilli(ms).atZone(zone).toLocalDate() }
        }.toSet()
        val streak = Titles.trainingStreakDays(dates)
        val next = DEED_MILESTONES.firstOrNull { it > streak }
        val days = pluralDays(streak)

        val quest = repo.observePresets().firstOrNull()
            ?.firstOrNull { it.scheduledDay == today.dayOfWeek.value }
        val (title, text, bigText) = if (quest == null) {
            // A scheduled rest day is a kept day, not a missed one: say so.
            Triple(
                "REST DAY",
                "Rest up. The streak holds at $days.",
                "Nothing scheduled today. Recovery is part of the program, " +
                    "and the $days streak sits safe until your next quest.",
            )
        } else {
            val moves = quest.entries.size
            val sets = quest.entries.sumOf { it.targetSets }
            Triple(
                "QUEST OPEN · ${quest.name.uppercase()}",
                "$moves moves · $sets sets waiting.",
                buildString {
                    append("${quest.name}: $moves moves, $sets sets today. ")
                    append(
                        if (streak == 0) "Today's session starts the streak."
                        else "$days streak. ${next ?: streak} is the next deed.",
                    )
                },
            )
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(1, notification)
        return true
    }

    /** Streak deed thresholds — keep in step with the TrainingStreak deeds. */
    private val DEED_MILESTONES = listOf(3, 7, 14, 30, 100)

    private fun pluralDays(n: Int) = if (n == 1) "1 day" else "$n days"
}
