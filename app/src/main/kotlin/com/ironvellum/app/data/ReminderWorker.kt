package com.ironvellum.app.data

import android.content.Context
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
        // A run held until the next morning (phone off overnight) has missed
        // its evening; it only books the next one.
        if (Summons.isSummonsHour(LocalTime.now())) {
            runCatching { Reminders.notifyIfWanted(app, app.repository) }
        }
        // Last, because REPLACE under this work's own name cancels this run:
        // nothing may follow it.
        Reminders.reschedule(app)
        return Result.success()
    }
}
