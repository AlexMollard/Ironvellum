package com.ironvellum.app

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.data.db.SetLogEntity
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

private const val REQUEST_ID = "0123456789abcdef0123456789abcdef"

internal fun developmentReply(context: Context, intent: Intent): JSONObject {
    val replies = ArrayBlockingQueue<String>(1)
    context.sendOrderedBroadcast(intent, null, object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            replies.add(resultData.orEmpty())
        }
    }, null, 0, null, null)
    val wire = replies.poll(10, TimeUnit.SECONDS).orEmpty()
    assertTrue("Expected an IVMCP1 ordered reply", wire.startsWith("IVMCP1:"))
    return JSONObject(String(Base64.decode(wire.removePrefix("IVMCP1:"), Base64.DEFAULT), Charsets.UTF_8))
}

@RunWith(AndroidJUnit4::class)
class DevelopmentStatusTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app = context.applicationContext as IronvellumApp
    private val receiver = ComponentName(context, "com.ironvellum.app.debug.DevelopmentStatusReceiver")

    private fun reply(requestId: String = REQUEST_ID, action: String = "com.ironvellum.app.debug.READ_STATUS") =
        developmentReply(context, Intent(action).setComponent(receiver).putExtra("request_id", requestId))

    @Test fun returnsOnlyAppFieldsWhenNoWorkoutIsLive() = runBlocking {
        val dao = app.database.sessionDao()
        val previous = dao.liveSession()
        // The full instrumentation suite can leave an unfinished fixture behind.
        previous?.let { dao.updateSession(it.copy(completedAtMs = it.startedAtMs)) }
        try {
            val reply = reply()
            val pkg = context.packageManager.getPackageInfo(context.packageName, 0)
            val receiverInfo = context.packageManager.getReceiverInfo(receiver, 0)
            assertEquals("android.permission.DUMP", receiverInfo.permission)
            assertTrue(receiverInfo.exported)
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

    @Test fun rejectsInvalidRequestsAndUnsupportedActions() {
        listOf("", REQUEST_ID.uppercase(), "${REQUEST_ID}0").forEach { malformed ->
            val reply = reply(malformed)
            assertTrue(reply.isNull("requestId"))
            assertEquals(false, reply.getBoolean("ok"))
            assertEquals("invalid_request", reply.getJSONObject("error").getString("code"))
        }
        val reply = reply(action = "com.ironvellum.app.debug.NOT_STATUS")
        assertEquals(false, reply.getBoolean("ok"))
        assertEquals("unsupported_action", reply.getJSONObject("error").getString("code"))
    }
}
