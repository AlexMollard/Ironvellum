package com.ironvellum.app.data

import android.content.Context
import com.ironvellum.app.IronvellumApp
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Posts the daily reminder, if it is still wanted. The preferences are
 * re-read here so a disable that raced the queue can never fire a nudge.
 */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? IronvellumApp ?: return Result.success()
        Reminders.notifyIfWanted(app, app.repository)
        return Result.success()
    }
}
