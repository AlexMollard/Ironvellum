package com.ironvellum.app.data

import android.content.Context
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
        runCatching { WeeklyRecaps.deliverIfDue(app, app.repository) }
        // Last, because REPLACE under this work's own name cancels this run: nothing may follow it.
        WeeklyRecaps.reschedule(app)
        return Result.success()
    }
}
