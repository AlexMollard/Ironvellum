package com.ironvellum.app.data.cloud

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.work.BackoffPolicy
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
import com.ironvellum.app.data.InboxNotifier
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import java.io.IOException
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One delivery lane for foreground and background reads; server timestamps remain the only cursor. */
object InboxPolling {
    private val lock = Mutex()
    private var lastUser: String? = null
    private var lastClient: Any? = null
    private var lastCheck = 0L
    private var lastCanNotify = false
    private var restoreSettled = false

    suspend fun check(app: IronvellumApp, throttle: Boolean): Result<Unit> = lock.withLock {
        try {
            val restored = app.accountRestore.await()
            // Retry an offline startup only until it settles. A later explicit sign-out is not a restore failure.
            if (!restoreSettled) {
                if (restored.isFailure && app.accountRepository.account.value == null) {
                    app.accountRepository.restore().getOrThrow()
                }
                restoreSettled = true
            }
            val account = app.accountRepository.account.value ?: return@withLock Result.success(Unit)
            val config = Cloud.config.value ?: return@withLock Result.success(Unit)
            val client = Cloud.client()
            val generation = InboxNotifier.generation()
            val canNotify = InboxNotifier.canNotify(app)
            val now = SystemClock.elapsedRealtime()
            if (throttle && lastUser == account.userId && lastClient === client &&
                now - lastCheck < 30_000L && !(canNotify && !lastCanNotify)
            ) return@withLock Result.success(Unit)
            val current = {
                InboxNotifier.generation() == generation &&
                    app.accountRepository.account.value?.userId == account.userId &&
                    Cloud.config.value == config && Cloud.client() === client
            }
            val inbox = app.cloudSync.inbox(force = true).getOrThrow()
            if (!current()) return@withLock Result.success(Unit)
            InboxNotifier.onInbox(app, account.userId, inbox, config.url, current)
            lastUser = account.userId
            lastClient = client
            lastCheck = now
            lastCanNotify = canNotify
            Result.success(Unit)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    internal fun transient(error: Throwable): Boolean {
        var cause: Throwable? = error
        while (cause != null) {
            if (cause is CancellationException) throw cause
            if (cause is RestException) return cause.statusCode == 429 || cause.statusCode >= 500
            if (cause is IOException || cause is HttpRequestException) return true
            cause = cause.cause
        }
        return false
    }

    internal fun shouldRetry(error: Throwable, attempt: Int): Boolean =
        transient(error) && attempt < 2
}

/** Best-effort half-hourly polling, with bounded recovery after transient failures. No push service. */
class InboxWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? IronvellumApp ?: return Result.success()
        if (Cloud.config.value == null || !InboxNotifier.enabled(app)) return Result.success()
        val error = InboxPolling.check(app, throttle = false).exceptionOrNull()
        if (error != null) {
            Log.w(TAG, "Inbox poll failed: ${error.message}")
            if (InboxPolling.shouldRetry(error, runAttemptCount)) return Result.retry()
        }
        if (app.accountRepository.account.value != null && InboxNotifier.enabled(app)) {
            try {
                app.awaitRepositoryReady()
                app.circleBonus.read(force = true)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w(TAG, "Circle poll failed: ${error.message}")
            }
        }
        return Result.success()
    }

    companion object {
        private const val TAG = "InboxWorker"
        private const val PERIODIC_NAME = "ironvellum-inbox-poll"
        private const val RETRY_NAME = "ironvellum-inbox-retry"
        private fun constraints() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<InboxWorker>(Duration.ofMinutes(30))
                .setConstraints(constraints())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun retry(context: Context) {
            val request = OneTimeWorkRequestBuilder<InboxWorker>()
                .setConstraints(constraints())
                .setInitialDelay(1, TimeUnit.MINUTES)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(RETRY_NAME, ExistingWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_NAME)
            WorkManager.getInstance(context).cancelUniqueWork(RETRY_NAME)
        }

        fun sync(context: Context, live: Boolean) {
            if (live && InboxNotifier.enabled(context)) schedule(context) else cancel(context)
        }
    }
}
