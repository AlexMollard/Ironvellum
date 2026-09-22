package com.ironvellum.app.data.cloud

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ironvellum.app.IronvellumApp
import java.time.Duration

/**
 * Pushes completed sessions and derived aggregates to the cloud once a day, so
 * a daily user's hunts reach the feed, the board and the backup without ever
 * opening the account screen — the manual button there used to be the only
 * trigger, which meant workouts silently never left the device.
 */
class CloudSyncWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? IronvellumApp ?: return Result.success()
        // A signed-out lifter has nothing to push: enqueueing work that can
        // only fail would burn a network slot every day for a refusal.
        if (app.accountRepository.account.value == null) return Result.success()
        // push() already returns a Result, so runCatching wraps a Result in a
        // Result: flatten it before either branch can read the outcome.
        return runCatching { app.cloudSync.push().getOrThrow() }
            .fold(
                onSuccess = { outcome ->
                    // A non-fatal problem (e.g. push_aggregates refused) still
                    // lands as success(); without this line nothing in logcat
                    // explains a stale leaderboard/totals row.
                    if (outcome.problems.isNotEmpty()) {
                        Log.w(TAG, "Cloud push finished with problems: ${outcome.problems.joinToString("; ")}")
                    }
                    Result.success()
                },
                // transient network errors are worth one retry, then let the
                // next day's run pick it up rather than hammering the gate
                onFailure = { error ->
                    Log.w(TAG, "Cloud push failed on attempt $runAttemptCount: ${error.message}", error)
                    if (runAttemptCount < 2) Result.retry() else Result.success()
                },
            )
    }

    companion object {
        private const val TAG = "CloudSyncWorker"
        private const val PERIODIC_NAME = "ironvellum-cloud-daily"
        private const val PUSH_NOW_NAME = "ironvellum-cloud-push-now"

        /** Idempotent: keeps the existing schedule across launches. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<CloudSyncWorker>(Duration.ofDays(1))
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        /** One-shot expedited push right after a session completes. No
         *  constraint: expedited work must run immediately, and doWork's
         *  signed-out guard plus CloudSync's failure Result make an offline
         *  run a cheap no-progress exit the daily job later covers. */
        fun pushNow(context: Context) {
            // Silent no-op when signed out: work that can only fail is never
            // enqueued. (schedule() cannot do this — onCreate runs it before
            // the persisted session is restored — so doWork re-guards.)
            val app = context.applicationContext as? IronvellumApp
            if (app?.accountRepository?.account?.value == null) return
            val request = OneTimeWorkRequestBuilder<CloudSyncWorker>()
                .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                PUSH_NOW_NAME,
                ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }
}
