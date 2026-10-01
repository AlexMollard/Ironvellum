package com.ironvellum.app.data.cloud

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ironvellum.app.IronvellumApp
import com.ironvellum.app.data.InboxNotifier
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration

/**
 * Polls the inbox every 30 minutes so ally activity reaches the notification
 * shade while the app is closed. There is no push channel: the backend is a
 * plain Supabase project with no FCM, and F-Droid builds cannot carry it.
 * WorkManager's 15-minute floor makes 30 the honest cadence.
 *
 * It exists only while it can do something: signed in, a cloud configured and
 * Ally activity on ([sync]). A signed-out or cloud-less install used to be
 * woken 48 times a day to find nothing to ask.
 */
class InboxWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? IronvellumApp ?: return Result.success()
        // The settled refusals first, before any wait or request: no backend,
        // or the lifter turned the setting off. Either way this poll has no
        // reason to exist, so it removes itself.
        if (Cloud.config.value == null || !InboxNotifier.enabled(app)) {
            cancel(app)
            return Result.success()
        }
        // Blocked notifications can be lifted at any time; skip, keep the schedule.
        if (!InboxNotifier.canNotify(app)) return Result.success()
        // The session is restored asynchronously in Application.onCreate, and a
        // periodic run can start in that same cold process: reading null here
        // would treat a signed-in lifter as signed out. Wait briefly; a truly
        // signed-out lifter just times out and exits with no network call.
        val account = withTimeoutOrNull(RESTORE_WAIT_MS) {
            app.accountRepository.account.first { it != null }
        } ?: return Result.success()
        app.cloudSync.inbox(force = true)
            .onSuccess { InboxNotifier.onInbox(applicationContext, account.userId, it) }
            .onFailure { Log.w(TAG, "Inbox poll failed: ${it.message}") }
        // A circle read settles the weekly bonus, so a lifter with Ally activity
        // on but sync off is still paid after the Monday reset.
        runCatching { app.circleBonus.read(force = true) }
        // A failed poll is not worth a retry storm; the next period covers it.
        return Result.success()
    }

    companion object {
        private const val TAG = "InboxWorker"
        private const val PERIODIC_NAME = "ironvellum-inbox-poll"
        private const val RESTORE_WAIT_MS = 15_000L

        /** Idempotent: keeps the existing schedule across launches. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<InboxWorker>(Duration.ofMinutes(30))
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

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_NAME)
        }

        /** Polls exactly while [live] (signed in with a cloud) and Ally activity is on. */
        fun sync(context: Context, live: Boolean) {
            if (live && InboxNotifier.enabled(context)) schedule(context) else cancel(context)
        }
    }
}
