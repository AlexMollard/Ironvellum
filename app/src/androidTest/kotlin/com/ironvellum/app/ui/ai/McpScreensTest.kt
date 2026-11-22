package com.ironvellum.app.ui.ai

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.data.*
import com.ironvellum.app.data.cloud.*
import com.ironvellum.app.data.db.ExerciseEntity
import com.ironvellum.app.data.db.PresetEntity
import com.ironvellum.app.ui.ShotRecorder
import com.ironvellum.app.ui.Routes
import com.ironvellum.app.ui.connectedAiDestinations
import com.ironvellum.app.ui.train.PresetsViewModel
import com.ironvellum.app.ui.train.RiteDetailScreen
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
    @Volatile private var delayConnections = false
    private val allowConnections = kotlinx.coroutines.CompletableDeferred<Unit>()
    private val failedReceipts = java.util.concurrent.CopyOnWriteArrayList<String>()
    @Volatile private var recordFailures = false
    private val oldWords = Words.themed
    private val oldBenchUnit = Units.overrideFor("bench press")
    private lateinit var db: IronvellumDatabase
    private lateinit var accounts: AccountRepository
    private lateinit var repo: McpRepository
    @Volatile private var backendAvailable = true
    private val openedWorkouts = mutableListOf<Long>()
    private val alternateShots get() = InstrumentationRegistry.getArguments().getString("aiAlternateShots") == "true"
    private val largeText get() = InstrumentationRegistry.getArguments().getString("aiLargeText") == "true"
    private val palette get() = if (alternateShots) "vellum_themed" else "dark_plain"
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
                    if (delayConnections) allowConnections.await()
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
        accounts = AccountRepository {
            if (backendAvailable) Result.success(client) else Result.failure(IllegalStateException("Backend disconnected"))
        }
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

    private fun open(
        connections: Boolean = false,
        light: Boolean = false,
        configured: Boolean = true,
        content: @Composable () -> Unit = {
            McpScreen(repo, connections, configured, onBack = {}, onSignIn = {},
                onOpenWorkout = { openedWorkouts.add(it) })
        },
    ) {
        compose.activityRule.scenario.onActivity { activity ->
            Words.setThemed(context, light)
            ShotRecorder.darkWindow(activity)
            if (light) androidx.core.view.WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
                isAppearanceLightStatusBars = true
                isAppearanceLightNavigationBars = true
            }
            activity.setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides if (largeText) Density(density.density, 2f) else density,
                ) {
                    IronvellumTheme(light) {
                        Box(Modifier.fillMaxSize().background(IronvellumColors.Abyss).windowInsetsPadding(WindowInsets.safeDrawing)) {
                            content()
                        }
                    }
                }
            }
        }
    }

    private fun find(text: String): SemanticsNodeInteraction {
        compose.waitUntil(8_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        return compose.onNodeWithText(text)
    }
    private fun reveal(text: String) = find(text).also { if (!it.isDisplayed()) it.performScrollTo() }
    private fun click(text: String) = reveal(text).performClick()
    private fun shot(name: String) {
        if (InstrumentationRegistry.getArguments().getString("shots") != "true") return
        ShotRecorder.take(compose, "mcp_$name" + if (largeText) "_200" else "")
        compose.mainClock.autoAdvance = true
    }
    private fun presets() = db.openHelper.readableDatabase.query("SELECT count(*) FROM presets").use { it.moveToFirst(); it.getInt(0) }

    @Test fun connectedSaveOpensCommittedWorkoutDetailAndBackReturnsToDrafts() {
        runBlocking { db.presetDao().insertPreset(PresetEntity(name = "Existing workout", note = "", scheduledDay = null)) }
        open(light = alternateShots) {
            val nav = rememberNavController()
            NavHost(nav, startDestination = Routes.WORKOUT_DRAFTS) {
                connectedAiDestinations(nav, repo) { true }
                composable(Routes.RITE_DETAIL, listOf(navArgument("presetId") { type = NavType.LongType })) { entry ->
                    val model: PresetsViewModel = viewModel(factory = viewModelFactory {
                        initializer { PresetsViewModel(Repository(db), context) }
                    })
                    RiteDetailScreen(checkNotNull(entry.arguments).getLong("presetId"),
                        onBack = { nav.popBackStack() }, onEdit = {}, onStartSession = {}, viewModel = model)
                }
            }
        }
        click("Upper A")
        click(if (alternateShots) "Save rite" else "Save workout")
        find("Saved on this device")
        assertEquals(2, presets())
        click(if (alternateShots) "View rite" else "View workout")
        reveal("Bench press").assertIsDisplayed()
        reveal("Barbell row").assertIsDisplayed()
        find("Upper A").assertIsDisplayed()
        compose.onNodeWithText("Existing workout").assertDoesNotExist()
        shot("view_committed_workout_$palette")
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        find("No drafts yet").assertIsDisplayed()
        assertEquals(2, presets())
    }

    @Test fun savedViewSendsCommittedLocalIdOnlyWhileAccountIsCurrent() {
        open(light = alternateShots)
        click("Upper A")
        val save = if (alternateShots) "Save rite" else "Save workout"
        reveal(save).assertIsDisplayed()
        if (largeText) {
            reveal("Reject draft").assertIsDisplayed()
            shot("review_actions_$palette")
        }
        click(save)
        val view = reveal(if (alternateShots) "View rite" else "View workout").assertIsDisplayed()
        shot("saved_$palette")
        val localId = db.openHelper.readableDatabase.query("SELECT id FROM presets").use {
            assertTrue(it.moveToFirst())
            it.getLong(0)
        }
        val oldAction = checkNotNull(view.fetchSemanticsNode().config[
            androidx.compose.ui.semantics.SemanticsActions.OnClick].action)
        view.performClick()
        assertEquals(listOf(localId), openedWorkouts)
        val scope = checkNotNull(repo.captureScope())
        compose.runOnIdle {
            // The backend can disappear before Compose removes the old button.
            backendAvailable = false
            try {
                assertFalse(repo.isCurrent(scope))
                assertTrue(oldAction())
            } finally {
                backendAvailable = true
            }
        }
        runBlocking { accounts.signOut().getOrThrow() }
        find("Sign in").performScrollTo().assertIsDisplayed()
        shot("signed_out_$palette")
        assertEquals(listOf(localId), openedWorkouts)
        open(light = alternateShots, configured = false)
        find("Connect a cloud backend in Settings to use AI connections.")
        shot("unconfigured_$palette")
        compose.onNodeWithText("Sign in").assertDoesNotExist()
    }

    @Test fun reviewedSaveAndAcknowledgementRetryFromEmptyList() {
        failAck = true
        open(light = alternateShots)
        find("Upper A")
        shot("drafts_$palette")
        click("Upper A")
        find("40 kg").performScrollTo().assertIsDisplayed()
        shot("review_$palette")
        val save = reveal(if (alternateShots) "Save rite" else "Save workout")
        val scroll = compose.onNode(hasScrollAction())
        fun scrollOffset() = scroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        if (largeText) assertTrue("Review should be scrolled before Save", scrollOffset() > 0f)
        save.performClick()
        find("Saved on this device")
        assertEquals("Saved should start at the top", 0f, scrollOffset(), 0f)
        find("Saved on this device").assertIsDisplayed()
        shot("saved_after_scrolled_review_$palette")
        assertEquals(1, presets())
        assertEquals("approved", state)
        find("Reopen or refresh drafts to retry confirmation.").performScrollTo()
        shot("acknowledgement_pending_$palette")
        failAck = false
        click("Back to drafts")
        find("No drafts yet")
        shot("empty_$palette")
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
        click("Back to drafts")
        find("No drafts yet")
        assertEquals(1, pending())
        sqlite.execSQL("DROP TRIGGER fail_ack")
        click("Refresh")
        compose.waitUntil(8_000) { pending() == 0 }
        find("No drafts yet")
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

    @Test fun connectionAddressPermissionsAndAccessAreVisibleAndConnectionCanBeRevoked() {
        delayConnections = true
        try {
            open(connections = true, light = alternateShots)
            find("Checking your account…")
            shot("loading_$palette")
        } finally {
            allowConnections.complete(Unit)
            delayConnections = false
        }
        find("Disconnect Training assistant")
        shot("connections_$palette")
        find("Read training history").performScrollTo().assertIsDisplayed()
        click("Recent access")
        compose.onNodeWithText("Listed workouts", substring = true).performScrollTo().assertIsDisplayed()
        shot("access_$palette")
        click("Disconnect Training assistant")
        find("No AI apps connected.").performScrollTo().assertIsDisplayed()
        shot("disconnected_$palette")
        compose.onNodeWithText("Disconnect Training assistant").assertDoesNotExist()
        assertTrue(revoked)
        click("Copy connection address")
        compose.activityRule.scenario.onActivity { activity ->
            val clipboard = activity.getSystemService(android.content.ClipboardManager::class.java)
            try {
                assertEquals("https://synthetic.example/mcp", clipboard.primaryClip?.getItemAt(0)?.text?.toString())
            } finally {
                clipboard.clearPrimaryClip()
            }
        }
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
        open(light = alternateShots)
        click("Upper A")
        click(if (alternateShots) "Save rite" else "Save workout")
        find("A movement is unavailable on this device. Ask your AI for a new draft using built-in rep movements.").performScrollTo()
        shot("movement_error_$palette")
        assertEquals(0, presets())
        assertEquals("pending", state)
        click("Reject draft")
        find("Draft rejected")
        shot("rejected_$palette")
        assertEquals("rejected", state)
    }

    @Test fun approvedReviewOnlyOffersResumeAndOldBackendHasItsOwnState() {
        state = "approved"
        open(light = alternateShots)
        click("Upper A")
        find("Finish saving")
        compose.onNodeWithText("Reject draft").assertDoesNotExist()
        missing = true
        click("Refresh")
        find("This cloud server hasn’t enabled AI connections.")
        shot("unsupported_$palette")
        compose.onNodeWithText("Finish saving").assertDoesNotExist()
    }

    @Test fun acceptedEnvelopeWithoutLocalReceiptCannotOpenOrSaveAWorkout() {
        open(light = alternateShots)
        find("Upper A")
        state = "accepted"
        click("Upper A")
        find("This draft has already been accepted.")
        shot("accepted_$palette")
        compose.onNodeWithText(if (alternateShots) "View rite" else "View workout").assertDoesNotExist()
        compose.onNodeWithText(if (alternateShots) "Save rite" else "Save workout").assertDoesNotExist()
        assertEquals(0, presets())
    }

    @Test fun expiredEnvelopeCannotBeSavedOrRejected() {
        open(light = alternateShots)
        find("Upper A")
        state = "expired"
        click("Upper A")
        find("This draft expired. Ask your AI for a new suggestion.")
        shot("expired_$palette")
        compose.onNodeWithText(if (alternateShots) "Save rite" else "Save workout").assertDoesNotExist()
        compose.onNodeWithText("Reject draft").assertDoesNotExist()
        assertEquals(0, presets())
    }
}
