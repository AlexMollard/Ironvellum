package com.ironvellum.app.data

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ironvellum.app.IronvellumApp

/**
 * Posts the weekly recap when [WeeklyRecaps.deliverIfDue] says it is due, then books the next Sunday 19:00. A run
 * held for days (a phone left off) finds nothing due and only books the next one.
 */
class RecapWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? IronvellumApp ?: return Result.success()
        return notificationDelivery(
            attempt = runAttemptCount,
            deliver = {
                app.awaitRepositoryReady()
                WeeklyRecaps.deliverIfDue(app, app.repository)
            },
            failed = { Log.e("RecapWorker", "Weekly recap delivery failed (attempt ${runAttemptCount + 1})", it) },
            scheduleNext = { WeeklyRecaps.reschedule(app) },
        )
    }
}
