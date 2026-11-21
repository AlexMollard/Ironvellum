package com.ironvellum.app.ui.ai

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.data.*
import com.ironvellum.app.data.cloud.*
import com.ironvellum.app.data.db.ExerciseEntity
import com.ironvellum.app.ui.ShotRecorder
import com.ironvellum.app.ui.theme.*
import io.github.jan.supabase.auth.*
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import java.io.IOException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** The rendered screen calls the normal SDK and Room repository against an isolated synthetic account. */
class McpScreensTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val id = "4452ba52-250c-4fd5-b105-a38d6c30df96"
    private val owner = "d23fd1d4-a085-456e-9ea9-b1214b464918"
    private val hash = "c".repeat(64)
    @Volatile private var state = "pending"
    @Volatile private var revoked = false
    @Volatile private var changed = false
    @Volatile private var failAck = false
    @Volatile private var missing = false
    @Volatile private var olderPage = false
    @Volatile private var failOlder = false
    private val acceptStarted = kotlinx.coroutines.CompletableDeferred<Unit>()
    private val allowAccept = kotlinx.coroutines.CompletableDeferred<Unit>()
    @Volatile private var delayAccept = false
    private val failedReceipts = java.util.concurrent.CopyOnWriteArrayList<String>()
    @Volatile private var recordFailures = false
    private val oldWords = Words.themed
    private val oldBenchUnit = Units.overrideFor("bench press")
    private lateinit var db: IronvellumDatabase
    private lateinit var accounts: AccountRepository
    private lateinit var repo: McpRepository
    private val client = createSupabaseClient("https://mcp-ui.invalid", "test-key") {
        install(Auth) {
            sessionManager = MemorySessionManager(UserSession(accessToken = "synthetic", refreshToken = "synthetic-refresh",
                expiresIn = 3600, tokenType = "bearer", user = UserInfo(aud = "authenticated", id = owner, email = "sample@example.invalid")))
            autoLoadFromStorage = false
            alwaysAutoRefresh = false
            enableLifecycleCallbacks = false
        }
        install(Postgrest)
        httpEngine = MockEngine { request ->
            val path = request.url.encodedPath.substringAfterLast('/')
            val headers = headersOf(HttpHeaders.ContentType, "application/json")
            val body = when (path) {
                "profiles" -> """[{"id":"$owner","display_name":"Sample","visibility":"private"}]"""
                "logout" -> "{}"
                "ironvellum_mcp_connections" -> {
                    if (missing) return@MockEngine respond("""{"code":"PGRST202","message":"unavailable"}""", HttpStatusCode.NotFound, headers)
                    val grants = if (revoked) "" else """{"id":"$id","clientName":"Training assistant","scopes":["training:read","workoutdrafts:create"],"createdAtMs":1795000000000,"expiresAtMs":1950000000000,"revokedAtMs":null}"""
                    ok(""""resourceUrl":"https://synthetic.example/mcp","connections":[$grants],"recentAccess":[{"requestId":"sample","clientName":"Training assistant","tool":"list_workouts","atMs":1795000000000,"result":"ok","draftId":null}]""")
                }
                "ironvellum_mcp_revoke_connection" -> {
                    check(request.body.toByteArray().decodeToString().contains(id))
                    revoked = true
                    ok(""""revoked":true""")
                }
                "ironvellum_mcp_drafts" -> ok(""""drafts":[${if (state in setOf("pending","approved")) envelope() else ""}]""")
                "ironvellum_mcp_drafts_page" -> {
                    val args = Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject
                    if (olderPage && args.getValue("p_cursor") == JsonNull) {
                        val rows = (1..50).joinToString(",") { index ->
                            envelope().replace(id, "newer-$index").replace("Upper A", "Newer $index").replace("approved", "pending")
                        }
                        ok(""""drafts":[$rows],"nextCursor":"older"""")
                    } else {
                        if (olderPage) {
                            check(args.getValue("p_cursor") == JsonPrimitive("older"))
                            if (failOlder) throw IOException("synthetic interrupted page")
                        }
                        ok(""""drafts":[${if (state in setOf("pending", "approved")) envelope() else ""}],"nextCursor":null""")
                    }
                }
                "ironvellum_mcp_draft" -> {
                    val args = Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject
                    check(args["p_draft_id"] == JsonPrimitive(id))
                    ok(""""draft":${envelope()}""")
                }
                "ironvellum_mcp_review_draft" -> {
                    val args = Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject
                    if (recordFailures && args["p_action"] == JsonPrimitive("accept")) {
                        failedReceipts.add(args.getValue("p_draft_id").jsonPrimitive.content)
                        throw IOException("synthetic accept failure")
                    }
                    check(args["p_hash"] == JsonPrimitive(hash) && args["p_draft_id"] == JsonPrimitive(id))
                    if (delayAccept && args.getValue("p_action") == JsonPrimitive("accept")) {
                        acceptStarted.complete(Unit)
                        allowAccept.await()
                    }
                    state = when (args.getValue("p_action").jsonPrimitive.content) {
                        "approve" -> "approved"
                        "reject" -> "rejected"
                        "accept" -> if (failAck) throw IOException("synthetic offline") else "accepted"
                        else -> error("Unexpected action")
                    }
                    ok(""""draft":${envelope(changed)}""")
                }
                else -> error(path)
            }
            respond(body, HttpStatusCode.OK, headers)
        }
    }

    private fun ok(fields: String) = """{"schemaVersion":1,"ok":true,$fields}"""
    private fun envelope(altered: Boolean = false) = """{"id":"$id","revision":1,"contentHash":"$hash","clientName":"Training assistant","createdAtMs":1795000000000,"expiresAtMs":1950000000000,"status":"$state",
        "draft":{"schemaVersion":1,"name":"${if (altered) "Changed workout" else "Upper A"}","warmup":"AUTO","cooldown":"NONE","entries":[{"exerciseKey":"bench press","sets":3,"reps":8,"loadKg":40},{"exerciseKey":"barbell row","sets":3,"reps":10,"loadKg":35}]}}"""

    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, IronvellumDatabase::class.java).build()
        accounts = AccountRepository(client)
        accounts.restore().getOrThrow()
        repo = McpRepository(accounts, Repository(db))
        db.exerciseDao().insertAll(listOf("Bench press", "Barbell row").mapIndexed { index, name ->
            ExerciseEntity(id = index + 1L, name = name, muscleGroup = "Chest", isWeighted = true)
        })
        Unit
    }

    @After fun stop() {
        compose.activityRule.scenario.close()
        Words.setThemed(context, oldWords)
        Units.setExercise(context, "bench press", oldBenchUnit)
        runBlocking { client.close() }
        db.close()
    }

    private fun open(connections: Boolean = false, light: Boolean = false) {
        compose.activityRule.scenario.onActivity { activity ->
            Words.setThemed(context, light)
            ShotRecorder.darkWindow(activity)
            if (light) androidx.core.view.WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
                isAppearanceLightStatusBars = true
                isAppearanceLightNavigationBars = true
            }
            activity.setContent {
                IronvellumTheme(light) {
                    Box(Modifier.fillMaxSize().background(IronvellumColors.Abyss).windowInsetsPadding(WindowInsets.safeDrawing)) {
                        McpScreen(repo, connections, configured = true, onBack = {}, onSignIn = {})
                    }
                }
            }
        }
    }

    private fun find(text: String): SemanticsNodeInteraction {
        compose.waitUntil(8_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        return compose.onNodeWithText(text)
    }
    private fun click(text: String) = find(text).performScrollTo().performClick()
    private fun shot(name: String) {
        ShotRecorder.take(compose, "mcp_$name")
        compose.mainClock.autoAdvance = true
    }
    private fun presets() = db.openHelper.readableDatabase.query("SELECT count(*) FROM presets").use { it.moveToFirst(); it.getInt(0) }

    @Test fun reviewedSaveAndAcknowledgementRetryFromEmptyList() {
        failAck = true
        open()
        find("Upper A")
        shot("drafts_dark_plain")
        click("Upper A")
        find("40 kg")
        shot("review_dark_plain")
        click("Save workout")
        find("Saved on this device")
        assertEquals(1, presets())
        assertEquals("approved", state)
        shot("saved_dark_plain")
        failAck = false
        click("Back to drafts")
        find("No drafts yet. Ask your connected AI for a suggestion.")
        assertEquals("accepted", state)
        assertEquals(1, presets())
    }

    @Test fun acceptedDraftStillShowsSavedWhenLocalAcknowledgementUpdateFails() {
        val sqlite = db.openHelper.writableDatabase
        sqlite.execSQL("""CREATE TRIGGER fail_ack BEFORE UPDATE OF ackPending ON mcp_draft_receipts
            BEGIN SELECT RAISE(ABORT, 'synthetic acknowledgement update failure'); END""")
        fun pending() = sqlite.query("SELECT ackPending FROM mcp_draft_receipts").use {
            assertTrue(it.moveToFirst())
            it.getInt(0)
        }
        open()
        click("Upper A")
        click("Save workout")
        compose.waitUntil(8_000) { state == "accepted" }
        assertEquals(1, presets())
        assertEquals(1, pending())
        find("Saved on this device")
        find("Reopen or refresh drafts to retry confirmation.")
        shot("saved_dark_plain")
        click("Back to drafts")
        find("No drafts yet. Ask your connected AI for a suggestion.")
        assertEquals(1, pending())
        sqlite.execSQL("DROP TRIGGER fail_ack")
        click("Refresh")
        compose.waitUntil(8_000) { pending() == 0 }
        find("No drafts yet. Ask your connected AI for a suggestion.")
        assertEquals(1, presets())
    }

    private fun finishOlder(light: Boolean) {
        olderPage = true
        state = "approved"
        open(light = light)
        find("Newer 1")
        find("Refresh").performScrollTo().assertIsDisplayed()
        find("Load older drafts").assertIsDisplayed()
        shot(if (light) "older_vellum_themed" else "older_dark_plain")
        failOlder = true
        click("Load older drafts")
        find(context.getString(com.ironvellum.app.R.string.ai_network_error))
        find("Newer 50")
        failOlder = false
        click("Load older drafts")
        find("Newer 1")
        find("Upper A").performScrollTo().assertIsDisplayed()
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        click(context.getString(com.ironvellum.app.R.string.ai_finish))
        find("Saved on this device")
        assertEquals("accepted", state)
        assertEquals(1, presets())
    }

    @Test fun olderApprovedDraftCanFinishInPlainMode() = finishOlder(false)
    @Test fun olderApprovedDraftCanFinishInThemedMode() = finishOlder(true)

    @Test fun boundedRetryPassesRotatePastFailedReceipts() = runBlocking {
        val sql = db.openHelper.writableDatabase
        repeat(25) { n ->
            sql.execSQL("INSERT INTO mcp_draft_receipts VALUES(?,?,?,?,?,?,1)",
                arrayOf<Any>("https://mcp-ui.invalid", owner, "receipt-$n", hash, n.toLong(), 1L))
        }
        recordFailures = true
        val scope = checkNotNull(repo.captureScope())
        assertEquals(20, repo.retryAcknowledgements(scope))
        assertEquals(20, failedReceipts.distinct().size)
        assertEquals(20, repo.retryAcknowledgements(scope))
        assertEquals(40, failedReceipts.size)
        assertEquals(25, failedReceipts.distinct().size)
        assertEquals(0, presets())
    }

    @Test fun aDelayedReceiptAllowsConnectionsAndDisconnect() {
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO mcp_draft_receipts VALUES(?,?,?,?,?,?,1)",
            arrayOf<Any>("https://mcp-ui.invalid", owner, id, hash, 123L, 1L))
        state = "approved"
        delayAccept = true
        try {
            open(connections = true)
            compose.waitUntil(8_000) { acceptStarted.isCompleted }
            click("Disconnect Training assistant")
            find("No AI apps connected.")
            assertTrue(revoked)
            assertEquals("approved", state)
        } finally { allowAccept.complete(Unit) }
        compose.waitUntil(8_000) { state == "accepted" }
        assertEquals(0, presets())
    }

    @Test fun vellumThemedConnectionCanBeRevoked() {
        open(connections = true, light = true)
        find("Disconnect Training assistant")
        shot("connections_vellum_themed")
        click("Disconnect Training assistant")
        find("No AI apps connected.")
        compose.onNodeWithText("Disconnect Training assistant").assertDoesNotExist()
        assertTrue(revoked)
    }

    @Test fun changedApprovalCannotSaveAndSignOutClearsOpenReview() {
        changed = true
        Units.setExercise(context, "bench press", com.ironvellum.app.domain.LoadUnit.LB)
        open(light = true)
        click("Upper A")
        find("Save rite")
        find(com.ironvellum.app.domain.Loads.withUnit(40.0, com.ironvellum.app.domain.LoadUnit.LB))
        find("40 kg")
        shot("review_vellum_themed")
        click("Save rite")
        find("This draft changed or is no longer available. Refresh to check its current state.")
        assertEquals(0, presets())
        runBlocking { accounts.signOut().getOrThrow() }
        find("Sign in")
        compose.onNodeWithText("Upper A").assertDoesNotExist()
    }

    @Test fun unavailableMovementBlocksWholeSaveAndPendingCanReject() {
        db.openHelper.writableDatabase.execSQL("DELETE FROM exercises WHERE id = 2")
        open()
        click("Upper A")
        click("Save workout")
        find("A movement is unavailable on this device. Ask your AI for a new draft using built-in rep movements.")
        assertEquals(0, presets())
        assertEquals("pending", state)
        click("Reject draft")
        find("Draft rejected")
        assertEquals("rejected", state)
    }

    @Test fun approvedReviewOnlyOffersResumeAndOldBackendHasItsOwnState() {
        state = "approved"
        open(light = true)
        click("Upper A")
        find("Finish saving")
        compose.onNodeWithText("Reject draft").assertDoesNotExist()
        missing = true
        click("Refresh")
        find("This cloud server hasn’t enabled AI connections.")
        compose.onNodeWithText("Finish saving").assertDoesNotExist()
    }
}
