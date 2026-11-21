package com.ironvellum.app.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.util.Base64
import com.ironvellum.app.IronvellumApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Read-only status for the shell bridge, registered only by the debug initializer. */
class DevelopmentStatusReceiver internal constructor(
    private val readStatus: suspend (Context, JSONObject) -> Unit = ::readAppStatus,
) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS)
        if (!isOrderedBroadcast) return
        val requestId = intent.validDevelopmentRequestId()
        val reply = developmentReply(requestId)
        val error = when {
            intent.action != ACTION_READ_STATUS -> "unsupported_action"
            requestId == null -> "invalid_request"
            else -> null
        }
        if (error != null) {
            resultData = reply.withDevelopmentError(error).developmentWireData()
            return
        }

        val pending = goAsync()
        val timeoutReply = developmentReply(requestId)
            .withDevelopmentError("status_unavailable").developmentWireData()
        val completed = AtomicBoolean()
        fun finish(wireData: String) {
            if (!completed.compareAndSet(false, true)) return
            try {
                pending.resultData = wireData
            } finally {
                pending.finish()
            }
        }

        val scope = CoroutineScope(Dispatchers.IO)
        // A separate executor can finish even when IO is queued or stuck in
        // synchronous code that cannot observe coroutine cancellation.
        val timeout = watchdog.schedule({
            finish(timeoutReply)
            scope.cancel()
        }, (deadlineNanos - System.nanoTime()).coerceAtLeast(0), TimeUnit.NANOSECONDS)
        scope.launch {
            val wireData = try {
                readStatus(context, reply)
                reply.developmentWireData()
            } catch (_: Exception) {
                timeoutReply
            }
            finish(if (System.nanoTime() < deadlineNanos) wireData else timeoutReply)
            timeout.cancel(false)
        }
    }

    companion object {
        const val ACTION_READ_STATUS = "com.ironvellum.app.debug.READ_STATUS"
        private const val TIMEOUT_SECONDS = 5L
        private val watchdog = ScheduledThreadPoolExecutor(1) { task ->
            Thread(task, "DevelopmentStatusWatchdog").apply { isDaemon = true }
        }.apply { removeOnCancelPolicy = true }
    }
}

private suspend fun readAppStatus(context: Context, reply: JSONObject) {
    val app = context.applicationContext as IronvellumApp
    val packageInfo = app.packageManager.getPackageInfo(app.packageName, 0)
    val metadata = JSONObject()
        .put("package", app.packageName)
        .put("versionName", packageInfo.versionName ?: JSONObject.NULL)
        .put("versionCode", packageInfo.longVersionCode)
        .put("debuggable", app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0)

    val repository = app.repository
    val summary = repository.observeLiveSession().first()?.let { session ->
        val sets = repository.observeSessionSets(session.id).first()
        JSONObject()
            .put("id", session.id)
            .put("label", session.label)
            .put("startedAtMs", session.startedAtMs)
            .put("totalSets", sets.size)
            .put("completedSets", sets.count { it.done })
    }
    reply.put("ok", true).put("app", metadata).put("activeWorkout", summary ?: JSONObject.NULL)
}

internal const val DEVELOPMENT_REQUEST_ID = "request_id"
private val requestIdPattern = Regex("[0-9a-f]{32}")

internal fun Intent.validDevelopmentRequestId(): String? =
    getStringExtra(DEVELOPMENT_REQUEST_ID)?.takeIf(requestIdPattern::matches)

internal fun developmentReply(requestId: String?): JSONObject = JSONObject()
    .put("schemaVersion", 1)
    .put("requestId", requestId ?: JSONObject.NULL)

internal fun JSONObject.withDevelopmentError(code: String): JSONObject =
    put("ok", false).put("error", JSONObject().put("code", code))

internal fun JSONObject.developmentWireData(): String =
    "IVMCP1:" + Base64.encodeToString(toString().toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
