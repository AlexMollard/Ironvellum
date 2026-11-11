package com.ironvellum.app.data

import android.content.Context
import android.util.Log
import com.ironvellum.app.IronvellumApp
import com.ironvellum.app.domain.Summons
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.time.LocalTime

/**
 * Posts the evening Summons, if it is still wanted, then books the next one.
 * The preferences are re-read here so a disable that raced the queue can
 * never fire a nudge, and a disabled Summons books nothing.
 */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? IronvellumApp ?: return Result.success()
        return notificationDelivery(
            attempt = runAttemptCount,
            deliver = {
                // A run held until morning missed its evening; only book the next one.
                if (Summons.isSummonsHour(LocalTime.now())) {
                    app.awaitRepositoryReady()
                    Reminders.notifyIfWanted(app, app.repository)
                }
            },
            failed = { Log.e("ReminderWorker", "Summons delivery failed (attempt ${runAttemptCount + 1})", it) },
            scheduleNext = { Reminders.reschedule(app) },
        )
    }
}
