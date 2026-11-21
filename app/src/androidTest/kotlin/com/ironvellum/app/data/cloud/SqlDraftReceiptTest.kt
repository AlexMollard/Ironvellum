package com.ironvellum.app.data.cloud

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import android.util.Base64
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.data.IronvellumDatabase
import com.ironvellum.app.data.Repository
import com.ironvellum.app.data.Words
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

/** The ordinary gate replays a recorded SQL pair. The host runner supplies a fresh pair from its own database. */
class SqlDraftReceiptTest {
    private fun assertStandaloneTarget(context: Context) {
        assertEquals("com.ironvellum.app.receiptfixture", context.packageName)
        assertEquals(Application::class.java, context.applicationContext.javaClass)
        val flags = PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or
            PackageManager.GET_PROVIDERS or PackageManager.GET_RECEIVERS
        val target = context.packageManager.getPackageInfo(context.packageName, flags)
        assertTrue(target.providers.isNullOrEmpty() && target.services.isNullOrEmpty() &&
            target.activities.isNullOrEmpty() && target.receivers.isNullOrEmpty())
        assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(Manifest.permission.INTERNET))
    }

    private fun scalar(db: IronvellumDatabase, query: String): String =
        db.openHelper.readableDatabase.query(query).use { cursor ->
            check(cursor.moveToFirst())
            val value = cursor.getString(0)
            check(!cursor.moveToNext())
            value
        }

    @Test fun approvedSqlDraftCommitsOneReceiptAndRetriesAfterDeletion() = runBlocking {
        withTimeout(30_000) {
            val instrument = InstrumentationRegistry.getInstrumentation()
            val input = InstrumentationRegistry.getArguments().getString("sqlFixture")
            val context = instrument.targetContext
            if (input != null || context.packageName == "com.ironvellum.app.receiptfixture" ||
                InstrumentationRegistry.getArguments().getString("standaloneReceipt") == "true"
            ) {
                assertStandaloneTarget(context)
                // Seed uses localized labels. Only this component-free test package owns these preferences.
                Words.init(context, databaseExists = false, welcomeSeen = false)
            }
            val flags = Base64.URL_SAFE or Base64.NO_WRAP
            val bytes = input?.let { Base64.decode(it, flags) }
                ?: instrument.context.assets.open("mcp-sql-receipt.json").use { it.readBytes() }
            val fixture = Json.parseToJsonElement(bytes.decodeToString()).jsonObject
            val pending = fixture.getValue("pending").jsonObject
            val envelope = pending.getValue("draft").jsonObject
            val id = envelope.getValue("id").jsonPrimitive.content
            val hash = envelope.getValue("contentHash").jsonPrimitive.content
            val owner = fixture.getValue("owner").jsonPrimitive.content
            val backend = fixture.getValue("backend").jsonPrimitive.content
            assertEquals("https://fixture.invalid", backend)
            val approvals = mutableListOf<JsonObject>()
            val acknowledgements = mutableListOf<JsonObject>()
            val name = "mcp-receipt-${UUID.randomUUID()}.db"
            var initialPresets = -1
            var returnPending = true
            var observedCommittedSave = false
            // A separate read-only SQLite handle sees only data committed before the network request.
            fun assertLocalState(saved: Boolean) {
                SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READONLY).use { reader ->
                    fun count(sql: String, args: Array<String>? = null) =
                        reader.rawQuery(sql, args).use { it.moveToFirst(); it.getInt(0) }
                    val added = if (saved) 1 else 0
                    assertEquals("preset count at review boundary", initialPresets + added, count("SELECT count(*) FROM presets"))
                    assertEquals("receipt count at review boundary", added, count("SELECT count(*) FROM mcp_draft_receipts"))
                    assertEquals("committed pending receipt and its new unscheduled preset", added, count("""
                        SELECT count(*) FROM mcp_draft_receipts r JOIN presets p ON p.id=r.localPresetId
                        WHERE r.backend=? AND r.userId=? AND r.draftId=? AND r.contentHash=? AND r.ackPending=1
                        AND p.name='Upper A' AND p.scheduledDay IS NULL""", arrayOf(backend, owner, id, hash)))
                }
            }
            val client = createSupabaseClient(backend, "fixture-key") {
                install(Auth) {
                    codeVerifierCache = MemoryCodeVerifierCache()
                    sessionManager = MemorySessionManager(UserSession(accessToken = "fixture-session",
                        refreshToken = "fixture-refresh", expiresIn = 3600, tokenType = "bearer",
                        user = UserInfo(aud = "authenticated", id = owner, email = "fixture@example.invalid")))
                    autoLoadFromStorage = false
                    alwaysAutoRefresh = false
                    enableLifecycleCallbacks = false
                }
                install(Postgrest)
                httpEngine = MockEngine { request ->
                    val reply = when (val route = request.url.encodedPath.substringAfterLast('/')) {
                        "profiles" -> """[{"id":"$owner","display_name":"Fixture","visibility":"private"}]"""
                        "ironvellum_mcp_draft" -> pending.toString()
                        "ironvellum_mcp_review_draft" -> {
                            val args = Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject
                            assertEquals(JsonPrimitive(id), args["p_draft_id"])
                            assertEquals(JsonPrimitive(hash), args["p_hash"])
                            when (args["p_action"]?.jsonPrimitive?.content) {
                                "approve" -> {
                                    if (acknowledgements.isEmpty()) assertLocalState(saved = false)
                                    approvals.add(args)
                                    if (returnPending) pending.toString() else fixture.getValue("approved").toString()
                                }
                                "accept" -> {
                                    if (acknowledgements.isEmpty()) {
                                        assertLocalState(saved = true)
                                        observedCommittedSave = true
                                    }
                                    acknowledgements.add(args)
                                    throw IOException("offline acknowledgement")
                                }
                                else -> error("Unexpected review action")
                            }
                        }
                        else -> error("Unexpected fixture route $route")
                    }
                    respond(reply, headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
                }
            }
            var db = IronvellumDatabase.create(context, name)
            try {
                val accounts = AccountRepository(client)
                accounts.restore().getOrThrow()
                val repository = Repository(db)
                repository.ensureSeeded()
                val service = McpRepository(accounts, repository)
                val scope = checkNotNull(service.captureScope())
                assertEquals(owner, scope.userId)
                assertEquals(backend, scope.backend)
                val originalCount = scalar(db, "SELECT COUNT(*) FROM presets").toInt()
                val originalSessions = scalar(db, "SELECT COUNT(*) FROM sessions")
                db.openHelper.writableDatabase.execSQL("INSERT INTO presets(name,note,scheduledDay) VALUES ('Original','keep',3)")
                initialPresets = originalCount + 1
                val bench = scalar(db, "SELECT id FROM exercises WHERE name='Bench Press' AND custom=0 AND metric='REPS'")
                val review = service.draft(scope, id)
                // The first approve response replays the *actual* pending envelope. No write is authorized.
                try {
                    service.save(review)
                    fail("pending approval must fail before the import")
                } catch (_: IllegalArgumentException) {
                    // McpRepository requires the approval reply to be approved or accepted.
                }
                assertEquals(1, approvals.size)
                assertTrue(acknowledgements.isEmpty())
                assertLocalState(saved = false)
                returnPending = false
                val saved = service.save(review)
                assertTrue("first accept observed the committed receipt from another connection", observedCommittedSave)
                assertTrue(saved.acknowledgementPending)
                assertEquals(saved, service.save(review))
                val preset = saved.localPresetId
                assertEquals(originalCount + 2, scalar(db, "SELECT COUNT(*) FROM presets").toInt())
                assertEquals("Upper A", scalar(db, "SELECT name FROM presets WHERE id=$preset AND scheduledDay IS NULL"))
                assertEquals("$bench/3/8/40.0/0", scalar(db, """SELECT exerciseId||'/'||targetSets||'/'||
                    targetReps||'/'||targetWeightKg||'/'||position FROM preset_entries WHERE presetId=$preset"""))
                assertEquals("AUTO", scalar(db, "SELECT mode FROM preset_preparations WHERE presetId=$preset AND kind='WARMUP'"))
                assertEquals("NONE", scalar(db, "SELECT mode FROM preset_preparations WHERE presetId=$preset AND kind='COOLDOWN'"))
                db.openHelper.writableDatabase.execSQL("DELETE FROM presets WHERE id=$preset")
                assertEquals(saved, service.save(review))
                assertEquals(originalCount + 1, scalar(db, "SELECT COUNT(*) FROM presets").toInt())
                db.close()
                db = IronvellumDatabase.create(context, name)
                val reopened = McpRepository(accounts, Repository(db))
                assertEquals(1, reopened.retryAcknowledgements(checkNotNull(reopened.captureScope())))
                val receipt = checkNotNull(db.draftReceiptDao().receipt(backend, owner, id))
                assertTrue(receipt.ackPending)
                assertEquals(hash, receipt.contentHash)
                assertEquals(preset, receipt.localPresetId)
                assertEquals("1", scalar(db, "SELECT COUNT(*) FROM mcp_draft_receipts"))
                assertEquals(originalCount + 1, scalar(db, "SELECT COUNT(*) FROM presets").toInt())
                assertEquals("keep", scalar(db, "SELECT note FROM presets WHERE name='Original' AND scheduledDay=3"))
                assertEquals(originalSessions, scalar(db, "SELECT COUNT(*) FROM sessions"))
                assertEquals(4, approvals.size)
                assertEquals(4, acknowledgements.size)
                val result = buildJsonObject {
                    put("fixtureSha256", MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
                    put("approve", approvals.distinct().single())
                    put("accept", acknowledgements.distinct().single())
                }
                if (input != null) instrument.sendStatus(0, Bundle().apply {
                    putString("sqlReceipt", Base64.encodeToString(result.toString().encodeToByteArray(), flags))
                })
            } finally {
                client.close()
                db.close()
                context.deleteDatabase(name)
            }
        }
    }
}
