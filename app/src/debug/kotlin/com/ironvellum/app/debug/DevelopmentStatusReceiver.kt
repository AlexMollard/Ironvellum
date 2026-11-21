package com.ironvellum.app.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.util.Base64
import com.ironvellum.app.IronvellumApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

/** Read-only status for the shell bridge, declared only in the debug manifest. */
class DevelopmentStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
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
        CoroutineScope(Dispatchers.IO).launch {
            try {
                withTimeout(5_000L) { readStatus(context, reply) }
                pending.resultData = reply.developmentWireData()
            } catch (_: Exception) {
                pending.resultData = developmentReply(requestId)
                    .withDevelopmentError("status_unavailable").developmentWireData()
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun readStatus(context: Context, reply: JSONObject) {
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

    companion object {
        const val ACTION_READ_STATUS = "com.ironvellum.app.debug.READ_STATUS"
    }
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
