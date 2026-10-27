package com.ironvellum.app.data.cloud

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.data.IronvellumDatabase
import com.ironvellum.app.data.Repository
import com.ironvellum.app.data.AppearanceStore
import com.ironvellum.app.data.SeededDb
import com.ironvellum.app.data.db.SessionEntity
import com.ironvellum.app.data.db.SetLogEntity
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.content.OutgoingContent
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises real push orchestration and Room watermarks against an isolated HTTP transport. */
@RunWith(AndroidJUnit4::class)
class WebPushIntegrationTest {
    @Test
    fun detailsAndImportedRowsRetryWithoutAdvancingFailedWatermark() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dbName = "web-push-integration.db"
        SeededDb.fresh(context, dbName)
        val db = IronvellumDatabase.create(context, dbName)
        val oldOwner = Cloud.tombstoneOwner
        val user = UUID.randomUUID().toString()
        try {
            Cloud.init(context)
            assertTrue(Cloud.markWebCaughtUp(user))
            val repo = Repository(db)
            repo.ensureSeeded()
            val exercise = repo.exerciseIdByName("Pull-up")
                ?: error("Seed catalogue lacks the test exercise")
            val normal = db.sessionDao().insertSession(SessionEntity(
                presetId = null, label = "Ordinary", startedAtMs = 1000, completedAtMs = 2000, xpAwarded = 0,
            ))
            val imported = db.sessionDao().insertSession(SessionEntity(
                presetId = null, label = "Imported", startedAtMs = 3000, completedAtMs = 4000, xpAwarded = 0, imported = true,
            ))
            db.sessionDao().insertSets(listOf(
                SetLogEntity(sessionId = normal, exerciseId = exercise, setIndex = 0, reps = 8, weightKg = null,
                    modifiers = "", done = true, kind = "TOP", rpe = 9, supersetGroup = 1),
                SetLogEntity(sessionId = imported, exerciseId = exercise, setIndex = 0, reps = 6, weightKg = null,
                    modifiers = "", done = true, kind = "WARMUP", rpe = 8),
            ))
            val requests = mutableListOf<Triple<String, String, String>>()
            var sessionsUploaded = false
            var importedUploaded = false
            var failDetails = true
            var snapshotHash: String? = null
            var schemaVersion = 31
            val engine = MockEngine { request ->
                val path = request.url.encodedPath.substringAfterLast('/')
                val method = request.method.value
                val body = (request.body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString().orEmpty()
                requests += Triple(method, path, body)
                val failed = path == "session_set_details" && method == "POST" && failDetails
                val response = when {
                    failed -> "{\"code\":\"23514\",\"message\":\"detail rejected\"}"
                    path == "schema_version" -> schemaVersion.toString()
                    path == "web_snapshots" && method == "GET" -> snapshotHash?.let { "[{\"content_hash\":\"$it\"}]" } ?: "[]"
                    path == "web_snapshots" && method == "POST" -> {
                        val value = Json.parseToJsonElement(body)
                        val row = if (value is JsonArray) value.single().jsonObject else value.jsonObject
                        snapshotHash = row.getValue("content_hash").jsonPrimitive.content
                        ""
                    }
                    path == "sessions" && method == "POST" -> { sessionsUploaded = true; "" }
                    path == "sessions" && method == "GET" -> if (sessionsUploaded) "[{\"id\":\"normal-cloud\",\"local_id\":$normal}]" else "[]"
                    path == "imported_sessions" && method == "POST" -> { importedUploaded = true; "" }
                    path == "imported_sessions" && method == "GET" -> if (importedUploaded) "[{\"id\":\"imported-cloud\",\"local_id\":$imported}]" else "[]"
                    method == "GET" -> "[]"
                    else -> ""
                }
                respond(response, if (failed) HttpStatusCode.BadRequest else HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json"))
            }
            val client = createSupabaseClient("https://web-push.invalid", "test-key") {
                install(Auth)
                install(Postgrest)
                httpEngine = engine
            }
            val sync = CloudSync(repo, AccountRepository(), client, AppearanceStore(context), Account(user, "test@example.invalid", "Test", "private"))
            sync.snapshotClock = { 1_800_000_000_000L }
            val first = sync.push().getOrThrow()
            assertTrue(first.problems.isNotEmpty())
            assertFalse(requests.any { it.first == "POST" && it.second == "web_snapshots" })
            val firstWatermark = repo.pushWatermark()
            assertFalse(firstWatermark.containsKey(normal))
            assertTrue(firstWatermark.containsKey(imported))
            val history = repo.observeHistory().first().associateBy { it.first.id }
            assertEquals(CloudSync.pushFingerprint(history.getValue(imported).first, history.getValue(imported).second), firstWatermark[imported])

            val sessionPayload = requests.single { it.first == "POST" && it.second == "sessions" }.third
            assertEquals(normal.toString(), Json.parseToJsonElement(sessionPayload).jsonArray.single().jsonObject.getValue("local_id").jsonPrimitive.content)
            val importedPayload = requests.single { it.first == "POST" && it.second == "imported_sessions" }.third
            assertTrue(importedPayload.contains("\"local_id\":$imported"))
            val setPayload = requests.single { it.first == "POST" && it.second == "imported_session_sets" }.third
            val importedSet = Json.parseToJsonElement(setPayload).jsonArray.single().jsonObject
            assertEquals("imported-cloud", importedSet.getValue("session_id").jsonPrimitive.content)
            assertEquals("warmup", importedSet.getValue("kind").jsonPrimitive.content)
            assertEquals("8", importedSet.getValue("rpe").jsonPrimitive.content)

            failDetails = false
            val second = sync.push().getOrThrow()
            assertTrue(second.problems.isEmpty())
            val detailPayload = requests.last { it.first == "POST" && it.second == "session_set_details" }.third
            val detail = Json.parseToJsonElement(detailPayload).jsonArray.single().jsonObject
            assertEquals("normal-cloud", detail.getValue("session_id").jsonPrimitive.content)
            assertEquals("top", detail.getValue("kind").jsonPrimitive.content)
            assertEquals("9", detail.getValue("rpe").jsonPrimitive.content)
            assertEquals("1", detail.getValue("superset_group").jsonPrimitive.content)
            assertEquals(CloudSync.pushFingerprint(history.getValue(normal).first, history.getValue(normal).second), repo.pushWatermark()[normal])
            assertEquals(1, requests.count { it.first == "POST" && it.second == "imported_sessions" })
            val snapshotRequest = requests.single { it.first == "POST" && it.second == "web_snapshots" }
            val wire = Json.parseToJsonElement(snapshotRequest.third)
            val row = if (wire is JsonArray) wire.single().jsonObject else wire.jsonObject
            val snapshot = row.getValue("snapshot").jsonObject
            assertEquals(user, row.getValue("user_id").jsonPrimitive.content)
            assertEquals("Test", snapshot.getValue("profile").jsonObject.getValue("name").jsonPrimitive.content)
            assertTrue(snapshot.getValue("sessions").jsonObject.containsKey(normal.toString()))
            assertTrue(snapshot.getValue("sessions").jsonObject.containsKey(imported.toString()))
            assertEquals("2", snapshot.getValue("profile").jsonObject.getValue("workouts").jsonPrimitive.content)
            assertEquals(1, requests.count { it.first == "POST" && it.second == "web_snapshots" })
            sync.push().getOrThrow()
            assertEquals(1, requests.count { it.first == "POST" && it.second == "web_snapshots" })
            sync.snapshotJsonForTest = { "x".repeat(WireLimits.SNAPSHOT_MAX_BYTES + 1) }
            val oversized = sync.push().getOrThrow()
            assertTrue(oversized.problems.isNotEmpty())
            assertEquals(1, requests.count { it.first == "POST" && it.second == "web_snapshots" })
            sync.snapshotJsonForTest = null
            schemaVersion = 30
            val oldBackend = sync.push().getOrThrow()
            assertTrue(oldBackend.problems.isNotEmpty())
            assertEquals(1, requests.count { it.first == "POST" && it.second == "web_snapshots" })
            val beforeSignedOut = requests.size
            val signedOut = CloudSync(repo, AccountRepository(), AppearanceStore(context))
            assertTrue(signedOut.push().isFailure)
            assertEquals(beforeSignedOut, requests.size)
        } finally {
            Cloud.tombstoneOwner = oldOwner
            db.close()
            context.deleteDatabase(dbName)
        }
    }
}
