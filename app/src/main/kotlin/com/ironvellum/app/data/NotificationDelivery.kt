package com.ironvellum.app.data

import androidx.work.ListenableWorker
import kotlinx.coroutines.CancellationException

/** Leave the current occurrence queued while retrying; only a settled run may book the next one. */
internal suspend inline fun notificationDelivery(
    attempt: Int,
    deliver: () -> Unit,
    failed: (Exception) -> Unit,
    scheduleNext: () -> Unit,
): ListenableWorker.Result {
    try {
        deliver()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        failed(error)
        if (attempt < 2) return ListenableWorker.Result.retry()
    }
    // Replacing this worker cancels it, so no suspending work may follow.
    scheduleNext()
    return ListenableWorker.Result.success()
}
