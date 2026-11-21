package com.ironvellum.app

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Base64
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.data.db.SetLogEntity
import com.ironvellum.app.debug.DevelopmentStatusReceiver
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private const val READ_STATUS = "com.ironvellum.app.debug.READ_STATUS"
private const val REQUEST_ID = "0123456789abcdef0123456789abcdef"

internal fun developmentReply(context: Context, intent: Intent): JSONObject =
    decodeDevelopmentReply(orderedDevelopmentResult(context, intent).orEmpty())

private fun decodeDevelopmentReply(wire: String): JSONObject {
    assertTrue("Expected an IVMCP1 ordered reply", wire.startsWith("IVMCP1:"))
    return JSONObject(String(Base64.decode(wire.removePrefix("IVMCP1:"), Base64.DEFAULT), Charsets.UTF_8))
}

private fun orderedDevelopmentResult(context: Context, intent: Intent): String? {
    val replies = ArrayBlockingQueue<String>(1)
    context.sendOrderedBroadcast(intent, null, object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            replies.add(resultData.orEmpty())
        }
    }, null, 0, null, null)
    return replies.poll(10, TimeUnit.SECONDS)
}

@RunWith(AndroidJUnit4::class)
class DevelopmentStatusTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app = context.applicationContext as IronvellumApp
    private fun statusIntent(requestId: String = REQUEST_ID) =
        Intent(READ_STATUS).setPackage(context.packageName).putExtra("request_id", requestId)

    private fun reply(requestId: String? = REQUEST_ID): JSONObject {
        val requestExtra = requestId?.let { " --es request_id $it" }.orEmpty()
        return registeredReply(READ_STATUS, requestExtra)
    }

    private fun registeredReply(action: String, extras: String): JSONObject {
        val wire = shell("$registeredCommand -a $action$extras")
        val data = Regex("data=\"(IVMCP1:[A-Za-z0-9+/=]+)\"").find(wire)?.groupValues?.get(1)
        return decodeDevelopmentReply(data.orEmpty())
    }

    private val registeredCommand = "am broadcast --receiver-registered-only -p ${context.packageName}"

    private fun shell(command: String): String {
        val pipe = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(pipe).bufferedReader().use { it.readText() }
    }

    @Test fun statusHasOnlyAPrivateStartupProviderAndNoManifestReceiver() {
        val legacy = ComponentName(context, "com.ironvellum.app.debug.DevelopmentStatusReceiver")
        assertThrows(PackageManager.NameNotFoundException::class.java) {
            context.packageManager.getReceiverInfo(legacy, 0)
        }
        val provider = context.packageManager.resolveContentProvider("${context.packageName}.development-status", 0)
        assertFalse(requireNotNull(provider).exported)
        assertEquals("", orderedDevelopmentResult(context, statusIntent().setComponent(legacy)))
    }

    @Test fun onlyShellCanUseTheRegisteredStatusEndpoint() {
        assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(android.Manifest.permission.DUMP))
        assertEquals("", orderedDevelopmentResult(context, statusIntent()))
        assertTrue(reply().getBoolean("ok"))

        val missing = shell("$registeredCommand -a com.ironvellum.app.debug.NOT_STATUS --es request_id $REQUEST_ID")
        assertFalse(missing, missing.contains("IVMCP1:"))
    }

    @Test fun registeredPreviewsRequireShellPermissionAndAnswerBothActions() {
        val request = " --es request_id $REQUEST_ID"
        try {
            for (action in listOf("SHOW", "CLEAR")) {
                val name = "com.ironvellum.app.debug.${action}_NOTIFICATIONS"
                val intent = Intent(name).setPackage(context.packageName)
                    .addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY)
                    .putExtra("request_id", REQUEST_ID)
                assertEquals("", orderedDevelopmentResult(context, intent))
                val result = registeredReply(name, request)
                assertEquals(true, result.getBoolean("ok"))
                assertEquals(REQUEST_ID, result.getString("requestId"))
                assertEquals(action.lowercase(), result.getString("action"))
            }
        } finally {
            shell("$registeredCommand -a com.ironvellum.app.debug.CLEAR_NOTIFICATIONS$request")
        }
    }

    @Test fun returnsOnlyAppFieldsWhenNoWorkoutIsLive() = runBlocking {
        val dao = app.database.sessionDao()
        val previous = dao.liveSession()
        // The full instrumentation suite can leave an unfinished fixture behind.
        previous?.let { dao.updateSession(it.copy(completedAtMs = it.startedAtMs)) }
        try {
            val reply = reply()
            val pkg = context.packageManager.getPackageInfo(context.packageName, 0)
            assertEquals(setOf("schemaVersion", "requestId", "ok", "app", "activeWorkout"), reply.keys().asSequence().toSet())
            assertEquals(1, reply.getInt("schemaVersion"))
            assertEquals(REQUEST_ID, reply.getString("requestId"))
            assertTrue(reply.getBoolean("ok"))
            val metadata = reply.getJSONObject("app")
            assertEquals(setOf("package", "versionName", "versionCode", "debuggable"), metadata.keys().asSequence().toSet())
            assertEquals(context.packageName, metadata.getString("package"))
            assertEquals(pkg.versionName, metadata.getString("versionName"))
            assertEquals(pkg.longVersionCode, metadata.getLong("versionCode"))
            assertTrue(metadata.getBoolean("debuggable"))
            assertTrue(reply.isNull("activeWorkout"))
            assertEquals(null, dao.liveSession())
        } finally {
            previous?.let { dao.updateSession(it) }
        }
    }

    @Test fun returnsOnlyLiveWorkoutSummaryAndLeavesRowsUntouched() = runBlocking {
        val dao = app.database.sessionDao()
        val previous = dao.liveSession()
        previous?.let { dao.updateSession(it.copy(completedAtMs = it.startedAtMs)) }
        try {
            val id = app.repository.startFreeformSession("Fixture workout")
            try {
                app.repository.setSessionPrivateNote(id, "Private fixture")
                dao.insertSets((0..1).map { index ->
                    SetLogEntity(sessionId = id, exerciseId = -1L, setIndex = index, reps = 5,
                        weightKg = 20.0, modifiers = "", done = index == 0)
                })
                val before = requireNotNull(dao.byId(id))
                val sets = dao.setsFor(id)
                val reply = reply()
                assertTrue(reply.getBoolean("ok"))
                val summary = reply.getJSONObject("activeWorkout")
                assertEquals(setOf("id", "label", "startedAtMs", "totalSets", "completedSets"), summary.keys().asSequence().toSet())
                assertEquals(id, summary.getLong("id"))
                assertEquals(before.label, summary.getString("label"))
                assertEquals(before.startedAtMs, summary.getLong("startedAtMs"))
                assertEquals(2, summary.getInt("totalSets"))
                assertEquals(1, summary.getInt("completedSets"))
                assertEquals(before, dao.byId(id))
                assertEquals(sets, dao.setsFor(id))
            } finally {
                app.repository.abandonSession(id)
            }
        } finally {
            previous?.let { dao.updateSession(it) }
        }
    }

    @Test fun stalledReadStillRepliesAtTheWatchdogDeadline() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val receiver = DevelopmentStatusReceiver { _, reply ->
            entered.countDown()
            release.await(15, TimeUnit.SECONDS)
            reply.put("ok", true)
        }
        // A test-only URI confines delivery to the injected receiver. The
        // production action filter does not match broadcasts carrying data.
        val scheme = "ironvellum-status-test"
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(READ_STATUS).apply { addDataScheme(scheme) },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        try {
            val start = SystemClock.elapsedRealtime()
            val reply = developmentReply(context, statusIntent().setData(Uri.parse("$scheme:stall")))
            val elapsed = SystemClock.elapsedRealtime() - start
            assertEquals(0L, entered.count)
            assertEquals(1L, release.count)
            assertTrue("Elapsed ${elapsed}ms", elapsed in 4_500L..8_000L)
            assertEquals(REQUEST_ID, reply.getString("requestId"))
            assertEquals(false, reply.getBoolean("ok"))
            assertEquals("status_unavailable", reply.getJSONObject("error").getString("code"))
        } finally {
            release.countDown()
            context.unregisterReceiver(receiver)
        }
    }

    @Test fun rejectsInvalidRequestIds() {
        listOf(null, REQUEST_ID.uppercase(), "${REQUEST_ID}0").forEach { malformed ->
            val reply = reply(malformed)
            assertTrue(reply.isNull("requestId"))
            assertEquals(false, reply.getBoolean("ok"))
            assertEquals("invalid_request", reply.getJSONObject("error").getString("code"))
        }
    }
}
