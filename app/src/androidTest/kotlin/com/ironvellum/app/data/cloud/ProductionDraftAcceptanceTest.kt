package com.ironvellum.app.data.cloud

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.data.DraftCatalogueTooLarge
import com.ironvellum.app.data.IronvellumDatabase
import com.ironvellum.app.data.Repository
import com.ironvellum.app.data.db.ExerciseEntity
import io.github.jan.supabase.auth.*
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

/** Actual SDK transport, account repository and Room writer, isolated from the installed app and its identity. */
class ProductionDraftAcceptanceTest {
    private val userA = "d23fd1d4-a085-456e-9ea9-b1214b464918"
    private val userB = "1c94fbc8-0f02-4f86-9870-d870645895e1"
    private val draftId = "4452ba52-250c-4fd5-b105-a38d6c30df96"
    private val hash = "a".repeat(64)
    private val oneEntry = """{"exerciseKey":"bench press","sets":3,"reps":8,"loadKg":40}"""

    private inner class Fixture(val db: IronvellumDatabase, backend: String) {
        var user = userA
        var status = "pending"
        var contentHash = hash
        var entries = oneEntry
        var editApproval = false
        var loseApprovalResponse = false
        var failAck = false
        var missingSchema = false
        var beforeAuth: suspend () -> Unit = {}
        var beforeDelete: suspend () -> Unit = {}
        var beforeLogout: suspend () -> Unit = {}
        var beforeApprove: suspend () -> Unit = {}
        val actions = java.util.concurrent.CopyOnWriteArrayList<String>()

        private fun envelope(state: String, name: String = "Upper A") = """{"schemaVersion":1,"ok":true,"draft":{
            "id":"$draftId","revision":1,"contentHash":"$contentHash","clientName":"Assistant","createdAtMs":1,
            "expiresAtMs":2,"status":"$state","draft":{"schemaVersion":1,"name":"$name","warmup":"AUTO","cooldown":"NONE","entries":[$entries]}}}"""

        private val config = CloudConfig(backend, "test-key", false)
        private val storage = MemorySessionManager(UserSession(accessToken = "test-token", refreshToken = "test-refresh",
            expiresIn = 3600, tokenType = "bearer", user = UserInfo(aud = "authenticated", id = userA, email = "a@example.invalid")))
        private fun buildClient(config: CloudConfig) = createSupabaseClient(config.url, config.key) {
            install(Auth) {
                sessionManager = storage
                codeVerifierCache = MemoryCodeVerifierCache()
                autoLoadFromStorage = false
                alwaysAutoRefresh = false
                enableLifecycleCallbacks = false
            }
            install(Postgrest)
            httpEngine = MockEngine { req ->
                val path = req.url.encodedPath.substringAfterLast('/')
                val headers = headersOf(HttpHeaders.ContentType, "application/json")
                val body = when (path) {
                    "profiles" -> """[{"id":"$user","display_name":"Test","visibility":"private"}]"""
                    "token" -> {
                        beforeAuth()
                        """{"access_token":"test-token","refresh_token":"test-refresh","expires_in":3600,"token_type":"bearer","user":{"id":"$user","aud":"authenticated","email":"a@example.invalid"}}"""
                    }
                    "logout" -> { beforeLogout(); "{}" }
                    "delete_my_account" -> { beforeDelete(); "{}" }
                    "ironvellum_mcp_connections" -> {
                        if (missingSchema) return@MockEngine respond("""{"code":"PGRST202","message":"missing"}""", HttpStatusCode.NotFound, headers)
                        """{"schemaVersion":1,"ok":true,"resourceUrl":null,"connections":[],"recentAccess":[]}"""
                    }
                    "ironvellum_mcp_draft" -> envelope(status)
                    "ironvellum_mcp_review_draft" -> {
                        val args = Json.parseToJsonElement(req.body.toByteArray().decodeToString()).jsonObject
                        require(args["p_draft_id"] == JsonPrimitive(draftId) && args["p_hash"] == JsonPrimitive(contentHash))
                        val action = args.getValue("p_action").jsonPrimitive.content
                        actions.add(action)
                        when (action) {
                            "approve" -> {
                                beforeApprove()
                                if (status in setOf("rejected","expired")) """{"schemaVersion":1,"ok":false,"error":"conflict"}"""
                                else {
                                    if (status != "accepted") status = "approved"
                                    if (loseApprovalResponse) throw IOException("lost approval reply")
                                    envelope(status, if (editApproval) "Changed" else "Upper A")
                                }
                            }
                            "accept" -> {
                                check(status == "approved" || status == "accepted")
                                if (failAck) throw IOException("lost ack")
                                status = "accepted"
                                envelope(status)
                            }
                            "reject" -> {
                                if (status == "pending") { status = "rejected"; envelope(status) }
                                else """{"schemaVersion":1,"ok":false,"error":"conflict"}"""
                            }
                            else -> error(action)
                        }
                    }
                    else -> error(path)
                }
                respond(body, HttpStatusCode.OK, headers)
            }
        }
        private val selection = CloudClientSelection(::buildClient).also { it.initialize(config) }
        val client get() = selection.client()
        val accounts = AccountRepository { selection.requireConfigured }
        val service = McpRepository(accounts, Repository(db))
        fun scope() = checkNotNull(service.captureScope())
        suspend fun fetch() = service.draft(scope(), draftId)
        suspend fun switchBackend(beforeSwap: suspend () -> Unit = {}) {
            val old = client
            var swapped = false
            Cloud.reconfigure(selection, config, accounts, beforeSwap = {
                check(old.auth.currentSessionOrNull() == null)
                check(selection.requireConfigured.isFailure)
                beforeSwap()
            }) { swapped = true }
            check(swapped && old !== client)
        }
        fun sql(query: String) = db.openHelper.writableDatabase.execSQL(query)
        fun value(query: String): String = db.openHelper.readableDatabase.query(query).use {
            check(it.moveToFirst()); it.getString(0)
        }
        fun count(table: String) = value("SELECT count(*) FROM $table").toInt()
        fun addCustomRows(number: Int) {
            sql("""WITH RECURSIVE rows(n) AS (SELECT 1 UNION ALL SELECT n+1 FROM rows WHERE n<$number)
                INSERT INTO exercises(name,muscleGroup,isWeighted,metric,category,custom,gear,muscles)
                SELECT 'Unrelated ' || n,'Chest',0,'REPS','',1,'','' FROM rows""")
        }
        fun assertNoSave() {
            assertEquals("Only the pre-existing workout should remain", 1, count("presets"))
            for (table in listOf("preset_entries", "preset_preparations", "mcp_draft_receipts", "sessions")) assertEquals(table, 0, count(table))
        }
    }

    private fun test(query: (String) -> Unit = {}, block: suspend Fixture.() -> Unit) = runBlocking {
        withTimeout(20_000) {
            val ctx = InstrumentationRegistry.getInstrumentation().targetContext
            val db = Room.inMemoryDatabaseBuilder(ctx, IronvellumDatabase::class.java)
                .setQueryCallback(object : RoomDatabase.QueryCallback {
                    override fun onQuery(sqlQuery: String, bindArgs: List<Any?>) = query(sqlQuery)
                }, Executor { it.run() }).build()
            val fixture = Fixture(db, "https://x.invalid")
            try {
                fixture.accounts.restore().getOrThrow()
                db.exerciseDao().insertAll(listOf(ExerciseEntity(id = 17, name = "Bench Press", muscleGroup = "Chest", isWeighted = true)))
                fixture.sql("INSERT INTO presets(name,note,scheduledDay) VALUES ('Original','keep',3)")
                fixture.block()
            } finally {
                fixture.client.close()
                db.close()
            }
        }
    }

    @Test fun doubleTapCommitsOneExactUnscheduledWorkoutAndNeverRecreatesDeletion() = test {
        val review = fetch()
        failAck = true
        val saves = coroutineScope { listOf(async { service.save(review) }, async { service.save(review) }).awaitAll() }
        assertEquals(1, saves.map { it.localPresetId }.distinct().size)
        assertTrue(saves.all { it.acknowledgementPending })
        val id = saves.first().localPresetId
        assertEquals("Upper A", value("SELECT name FROM presets WHERE id=$id AND scheduledDay IS NULL"))
        assertEquals("17/3/8/40.0/0", value("SELECT exerciseId||'/'||targetSets||'/'||targetReps||'/'||targetWeightKg||'/'||position FROM preset_entries WHERE presetId=$id"))
        assertEquals("AUTO", value("SELECT mode FROM preset_preparations WHERE presetId=$id AND kind='WARMUP'"))
        assertEquals("NONE", value("SELECT mode FROM preset_preparations WHERE presetId=$id AND kind='COOLDOWN'"))
        assertEquals(1, count("mcp_draft_receipts"))
        assertEquals("keep", value("SELECT note FROM presets WHERE name='Original' AND scheduledDay=3"))
        sql("DELETE FROM presets WHERE id=$id")
        failAck = false
        assertEquals(0, service.retryAcknowledgements(scope()))
        assertEquals("accepted", status)
        assertEquals(McpRepository.Saved(id, false), service.save(review))
        assertEquals(1, count("presets"))
        assertEquals(0, count("sessions"))
    }

    @Test fun conflictingActionableHashCannotPassReceiptPreflight() = test {
        failAck = true
        val saved = service.save(fetch())
        assertTrue(saved.acknowledgementPending)
        val callsBeforeConflict = actions.toList()
        val originalReceipt = value("SELECT contentHash||'/'||localPresetId||'/'||ackPending FROM mcp_draft_receipts")
        assertEquals("$hash/${saved.localPresetId}/1", originalReceipt)
        contentHash = "b".repeat(64)
        assertEquals("approved", status)
        val error = runCatching { service.save(fetch()) }.exceptionOrNull()
        assertEquals("Conflicting hash must fail before any remote approval", "Draft receipt conflict", error?.message)
        assertEquals(callsBeforeConflict, actions.toList())
        assertEquals(originalReceipt, value("SELECT contentHash||'/'||localPresetId||'/'||ackPending FROM mcp_draft_receipts"))
        assertEquals(2, count("presets"))
        assertEquals(1, count("preset_entries"))
        assertEquals(2, count("preset_preparations"))
        assertEquals(1, count("mcp_draft_receipts"))
    }

    @Test fun candidateQueryStopsAtOneOverflowSentinel() = test {
        addCustomRows(7000)
        assertEquals(7001, count("exercises"))
        db.draftReceiptDao().movementCandidates().use {
            assertEquals("The writer must never receive all 7001 rows", 5001, it.count)
        }
    }

    @Test fun preflightRefusesAnOversizedCatalogueBeforeApproval() = test {
        addCustomRows(5000)
        assertEquals(5001, count("exercises"))
        assertTrue("Overflow cannot establish unique movement identity",
            runCatching { service.save(fetch()) }.exceptionOrNull() is DraftCatalogueTooLarge)
        assertTrue(actions.isEmpty())
        assertNoSave()
    }

    @Test fun catalogueOverflowDuringApprovalRefusesCommitButExactLimitSaves() = test {
        addCustomRows(4999)
        assertEquals(5000, count("exercises"))
        beforeApprove = { addCustomRows(1) }
        assertTrue("Catalogue grew before COMMIT", runCatching { service.save(fetch()) }.exceptionOrNull() is DraftCatalogueTooLarge)
        assertEquals(listOf("approve"), actions.toList())
        assertNoSave()
        sql("DELETE FROM exercises WHERE id=(SELECT MAX(id) FROM exercises)")
        beforeApprove = {}
        assertEquals(5000, count("exercises"))
        assertFalse(service.save(fetch()).acknowledgementPending)
        assertEquals(1, count("mcp_draft_receipts"))
    }

    @Test fun newNormalizedCollisionDuringApprovalRefusesCommit() = test {
        beforeApprove = {
            db.exerciseDao().insertAll(listOf(ExerciseEntity(name = "  BENCH PRESS ", muscleGroup = "Chest", isWeighted = true, custom = true)))
        }
        assertTrue(runCatching { service.save(fetch()) }.isFailure)
        assertEquals(listOf("approve"), actions.toList())
        assertNoSave()
    }

    @Test fun aCustomRowShadowingTheBuiltinKeyRefusesApproval() = test {
        db.exerciseDao().insertAll(listOf(ExerciseEntity(name = "  BENCH PRESS ", muscleGroup = "Chest", isWeighted = true, custom = true)))
        val result = runCatching { service.save(fetch()) }
        assertTrue("Ambiguous local movement must not be accepted", result.isFailure)
        assertTrue("Approval must not run on an ambiguous local key", actions.isEmpty())
        assertNoSave()
    }

    @Test fun invalidLastMovementOrFailedFinalReceiptRollsBackEveryRow() = test {
        entries += "," + oneEntry.replace("bench press", "unknown")
        assertTrue(runCatching { service.save(fetch()) }.isFailure)
        assertTrue(actions.isEmpty())
        assertNoSave()
        entries = oneEntry
        beforeApprove = { sql("UPDATE exercises SET metric='HOLD' WHERE id=17") }
        assertTrue(runCatching { service.save(fetch()) }.isFailure)
        assertNoSave()
        beforeApprove = {}
        sql("UPDATE exercises SET metric='REPS' WHERE id=17")
        sql("CREATE TRIGGER refuse_receipt BEFORE INSERT ON mcp_draft_receipts BEGIN SELECT RAISE(ABORT,'final insert refused'); END")
        assertTrue(runCatching { service.save(fetch()) }.isFailure)
        assertNoSave()
        assertFalse(actions.contains("accept"))
    }

    @Test fun approvalMustMatchPayloadAndWinAgainstRejectionOrExpiry() = test {
        val opened = fetch()
        service.reject(opened)
        assertTrue(runCatching { service.save(opened) }.exceptionOrNull() is McpRpcFailure)
        status = "expired"
        assertTrue(runCatching { service.save(opened) }.exceptionOrNull() is McpRpcFailure)
        status = "pending"
        editApproval = true
        assertTrue(runCatching { service.save(opened) }.isFailure)
        assertNoSave()
        editApproval = false
        status = "pending"
        loseApprovalResponse = true
        assertTrue(runCatching { service.save(opened) }.isFailure)
        assertEquals("approved", status)
        assertNoSave()
        loseApprovalResponse = false
        val resumed = fetch()
        assertEquals(DraftStatus.APPROVED, resumed.envelope.status)
        assertFalse(service.save(resumed).acknowledgementPending)
        assertTrue(runCatching { service.reject(opened) }.isFailure)
    }

    @Test fun prepublicationAccountSwitchAndReturnToOriginalUserInvalidateOpenReview() = test {
        val old = scope()
        val opened = fetch()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        beforeAuth = { entered.complete(Unit); release.await() }
        coroutineScope {
            user = userB
            val login = async(Dispatchers.IO) { accounts.signIn("b@example.invalid","isolated") }
            try {
                entered.await()
                assertEquals(userA, accounts.account.value?.userId)
                assertNull(service.captureScope())
                assertFalse(service.isCurrent(old))
                assertTrue(runCatching { service.save(opened) }.exceptionOrNull() is CancellationException)
            } finally { release.complete(Unit) }
            login.await().getOrThrow()
        }
        assertEquals(userB, scope().userId)
        user = userA
        accounts.signIn("a@example.invalid","isolated").getOrThrow()
        assertEquals(userA, scope().userId)
        assertFalse(service.isCurrent(old))
        assertTrue(runCatching { service.save(opened) }.exceptionOrNull() is CancellationException)
        assertNoSave()
    }

    @Test fun logoutDuringApprovalSuppressesLateResponseAndAllLocalWrites() = test {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        beforeApprove = { entered.complete(Unit); withContext(NonCancellable) { release.await() } }
        val opened = fetch()
        coroutineScope {
            val save = async(Dispatchers.IO) { service.save(opened) }
            entered.await()
            val logout = async(Dispatchers.IO) { accounts.signOut() }
            try {
                while (accounts.account.value != null) yield()
            } finally { release.complete(Unit) }
            logout.await().getOrThrow()
            assertTrue(runCatching { save.await() }.exceptionOrNull() is CancellationException)
        }
        assertNoSave()
        assertFalse(actions.contains("accept"))
    }

    @Test fun backendRoundTripAcknowledgesOnlyOriginalReceiptEvenAfterExpiry() = test {
        failAck = true
        val old = scope()
        service.save(fetch())
        switchBackend()
        val y = Fixture(db, "https://y.invalid")
        try {
            y.accounts.restore().getOrThrow()
            assertEquals(0, y.service.retryAcknowledgements(y.scope()))
            assertTrue(y.actions.isEmpty())
            assertEquals(1, count("mcp_draft_receipts"))
        } finally { y.client.close() }
        user = userA
        accounts.signIn("a@example.invalid","isolated").getOrThrow()
        assertFalse(service.isCurrent(old))
        failAck = false
        assertEquals(0, service.retryAcknowledgements(scope()))
        assertEquals("0", value("SELECT ackPending FROM mcp_draft_receipts"))
        assertEquals("https://x.invalid", scope().backend)
        missingSchema = true
        assertTrue(runCatching { service.connections(scope()) }.isFailure)
    }

    @Test fun reopenedDeviceRetriesStoredAckAndAnotherDatabaseCanSaveItsOwnCopy() = test {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val dbName = "production-draft-acceptance-reopen-test.db"
        suspend fun onDisk(block: suspend (IronvellumDatabase) -> Unit) {
            val disk = IronvellumDatabase.create(ctx, dbName)
            try { block(disk) } finally { disk.close() }
        }
        val original = service.save(fetch())
        assertFalse(original.acknowledgementPending)
        try {
            onDisk { secondDb ->
                val second = Fixture(secondDb, "https://x.invalid")
                try {
                    second.accounts.restore().getOrThrow()
                    secondDb.exerciseDao().insertAll(listOf(ExerciseEntity(id = 21, name = "Bench Press", muscleGroup = "Chest", isWeighted = true)))
                    val alreadyOpen = second.fetch()
                    second.status = "accepted"
                    assertTrue("A newly opened terminal draft is read-only", runCatching { second.service.save(second.fetch()) }.isFailure)
                    second.failAck = true
                    val local = second.service.save(alreadyOpen)
                    assertTrue(local.acknowledgementPending)
                    assertEquals("21", second.value("SELECT exerciseId FROM preset_entries"))
                    second.sql("DELETE FROM presets WHERE id=${local.localPresetId}")
                } finally { second.client.close() }
            }
            onDisk { reopened ->
                val afterRestart = Fixture(reopened, "https://x.invalid")
                try {
                    afterRestart.accounts.restore().getOrThrow()
                    afterRestart.status = "accepted"
                    assertEquals(0, afterRestart.service.retryAcknowledgements(afterRestart.scope()))
                    assertEquals(listOf("accept"), afterRestart.actions)
                    assertEquals("0", afterRestart.value("SELECT ackPending FROM mcp_draft_receipts"))
                    assertEquals(0, afterRestart.count("presets"))
                } finally { afterRestart.client.close() }
            }
        } finally { ctx.deleteDatabase(dbName) }
    }

    @Test fun cancelledQueuedSignInDoesNotExposeTheActivePredecessor() = test {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        beforeAuth = { entered.complete(Unit); release.await() }
        val cleanupObserved = CompletableDeferred<Unit>()
        beforeLogout = {
            assertNull("The superseded sign-in is still clearing SDK credentials", service.captureScope())
            cleanupObserved.complete(Unit)
        }
        val old = scope()
        supervisorScope {
            val first = async(Dispatchers.IO) { accounts.signIn("b@example.invalid", "isolated") }
            try {
                entered.await()
                val second = async(start = CoroutineStart.UNDISPATCHED) { accounts.signIn("c@example.invalid", "isolated") }
                second.cancelAndJoin()
                assertFalse(service.isCurrent(old))
                assertNull("The first SDK sign-in still controls the session", service.captureScope())
            } finally {
                release.complete(Unit)
                runCatching { first.await() }
            }
        }
        assertFalse(service.isCurrent(old))
        assertTrue("The superseded SDK sign-in must finish cleanup", cleanupObserved.isCompleted)
    }

    @Test fun failedTransitionsReleaseOnlyAfterTheirSdkOutcomeIsKnown() = test {
        val old = scope()
        beforeDelete = {
            assertNull(service.captureScope())
            throw IOException("deletion unavailable")
        }
        assertTrue(accounts.deleteCloudData().isFailure)
        assertFalse(service.isCurrent(old))
        assertEquals(userA, scope().userId)
        assertTrue(runCatching { switchBackend {
            assertNull(service.captureScope())
            throw IOException("watermark unavailable")
        } }.isFailure)
        assertNull(service.captureScope())
        assertNull(client.auth.currentUserOrNull())
        accounts.signIn("a@example.invalid", "isolated").getOrThrow()
        assertEquals(userA, scope().userId)
    }

    @Test fun cancelledBackendSwitchFinishesClearingSdkAndStoredSession() = test {
        val scope = scope()
        val started = CompletableDeferred<Unit>()
        val cancelling = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val cleanupFinished = CompletableDeferred<Unit>()
        val logoutAfterDrain = AtomicReference<Boolean>()
        beforeLogout = {
            logoutAfterDrain.set(cleanupFinished.isCompleted && service.captureScope() == null)
        }
        coroutineScope {
            val query = launch(Dispatchers.IO) {
                accounts.withScope(scope) {
                    try {
                        started.complete(Unit)
                        awaitCancellation()
                    } finally {
                        withContext(NonCancellable) {
                            cancelling.complete(Unit)
                            release.await()
                            cleanupFinished.complete(Unit)
                        }
                    }
                }
            }
            started.await()
            val switch = launch(Dispatchers.IO) { switchBackend() }
            try {
                cancelling.await()
                assertNull(accounts.account.value)
                assertNull(service.captureScope())
                assertEquals(userA, client.auth.currentUserOrNull()?.id)
                switch.cancel()
            } finally {
                release.complete(Unit)
            }
            switch.join()
            query.join()
        }
        assertEquals("Old-client logout must follow the completed query drain with scope still invalid", true, logoutAfterDrain.get())
        assertNull(accounts.account.value)
        assertNull(service.captureScope())
        assertNull("Cancelled switch left SDK signed in", client.auth.currentUserOrNull())
        assertNull("Cancelled switch left stored credentials", client.auth.sessionManager.loadSessionOrNull())
        val restarted = AccountRepository(client)
        restarted.restore().getOrThrow()
        assertNull("Restart resurrected the abandoned identity", restarted.account.value)
        assertNull(restarted.captureScope())
    }

    @Test fun backendTransitionWinsBeforeCommit() = transitionFirst(delete = false)
    @Test fun accountDeletionWinsBeforeCommit() = transitionFirst(delete = true)

    private fun transitionFirst(delete: Boolean) = test {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val waitForRelease: suspend () -> Unit = { entered.complete(Unit); release.await() }
        if (delete) beforeDelete = waitForRelease else beforeLogout = waitForRelease
        val old = scope()
        val open = fetch()
        coroutineScope {
            val change = async(Dispatchers.IO) {
                if (delete) accounts.deleteCloudData().getOrThrow() else switchBackend()
            }
            try {
                entered.await()
                assertNull("An identity-changing request is already in flight", service.captureScope())
                assertFalse(service.isCurrent(old))
                assertTrue(runCatching { service.save(open) }.exceptionOrNull() is CancellationException)
            } finally { release.complete(Unit) }
            change.await()
        }
        assertTrue(actions.isEmpty())
        assertNoSave()
    }

    @Test fun identityRemainsPinnedAtOuterSqliteCommit() = commitFirst("logout")
    @Test fun backendSwitchCannotClearSdkBeforeOuterSqliteCommit() = commitFirst("backend")
    @Test fun deletionCannotClearSdkBeforeOuterSqliteCommit() = commitFirst("delete")

    private fun commitFirst(operation: String) {
        val ending = CountDownLatch(1)
        val release = CountDownLatch(1)
        val depth = ThreadLocal<Int>()
        val receiptWritten = ThreadLocal<Boolean>()
        test(query = { sql ->
            // Direct query executor: Room calls this on the writer immediately before its underlying endTransaction.
            when {
                sql.startsWith("BEGIN ") -> depth.set((depth.get() ?: 0) + 1)
                sql.startsWith("INSERT", ignoreCase = true) && sql.contains("mcp_draft_receipts") -> receiptWritten.set(true)
                sql == "END TRANSACTION" -> {
                    depth.set((depth.get() ?: 1) - 1)
                    if (depth.get() == 0 && receiptWritten.get() == true) {
                        receiptWritten.remove()
                        ending.countDown()
                        check(release.await(8, TimeUnit.SECONDS))
                    }
                }
            }
        }) {
            val opened = fetch()
            coroutineScope {
                val save = async(Dispatchers.IO) { runCatching { service.save(opened) } }
                check(ending.await(8, TimeUnit.SECONDS)) { "Receipt transaction did not reach outer COMMIT" }
                val thread = AtomicReference<Thread>()
                val remoteChange = CompletableDeferred<Unit>()
                beforeDelete = { remoteChange.complete(Unit) }
                beforeLogout = { remoteChange.complete(Unit) }
                val logout = async(Dispatchers.IO) {
                    thread.set(Thread.currentThread())
                    when (operation) {
                        "logout" -> accounts.signOut().getOrThrow()
                        "backend" -> switchBackend()
                        "delete" -> accounts.deleteCloudData().getOrThrow()
                        else -> error(operation)
                    }
                }
                try {
                    withTimeout(4_000) {
                        while (thread.get()?.state != Thread.State.BLOCKED && !logout.isCompleted && !remoteChange.isCompleted) yield()
                    }
                    assertFalse("Remote $operation must wait for SQLite COMMIT", remoteChange.isCompleted)
                    assertEquals("SDK user must remain pinned through COMMIT", userA, client.auth.currentUserOrNull()?.id)
                    assertEquals("Published user must remain pinned through COMMIT", userA, accounts.account.value?.userId)
                } finally { release.countDown() }
                logout.await()
                save.await()
            }
            assertEquals(2, count("presets"))
            assertEquals(1, count("mcp_draft_receipts"))
        }
    }
}
