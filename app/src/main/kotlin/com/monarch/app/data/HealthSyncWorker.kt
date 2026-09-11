package com.monarch.app.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.monarch.app.MonarchApp
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * Pulls the last fortnight of Health Connect days once a day, so steps,
 * distance, calories and sleep stay current without the app being opened.
 *
 * A short window (not the full 90 days) keeps the daily job cheap; the
 * on-launch sync in [MonarchApp] backfills deeper history.
 */
class HealthSyncWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? MonarchApp ?: return Result.success()
        if (!app.healthSync.available()) return Result.success()
        return runCatching { app.repository.syncHealthHistory(WINDOW_DAYS) }
            .fold(
                onSuccess = { Result.success() },
                // transient provider errors are worth one retry, then let the
                // next day's run pick it up rather than hammering the API
                onFailure = { if (runAttemptCount < 2) Result.retry() else Result.success() },
            )
    }

    companion object {
        private const val NAME = "monarch-health-daily"
        private const val WINDOW_DAYS = 14

        /** Idempotent: keeps the existing schedule across launches. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<HealthSyncWorker>(Duration.ofDays(1))
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .setInitialDelay(untilNextRun())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        /** Aim for ~04:00 local, when the previous day's data is settled. */
        private fun untilNextRun(): Duration {
            val now = LocalDateTime.now()
            var target = now.truncatedTo(ChronoUnit.DAYS).with(LocalTime.of(4, 0))
            if (!target.isAfter(now)) target = target.plusDays(1)
            return Duration.between(now, target)
        }
    }
}
