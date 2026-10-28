package com.ironvellum.app.data.cloud

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.data.AppearanceStore
import com.ironvellum.app.data.IronvellumDatabase
import com.ironvellum.app.data.Repository
import com.ironvellum.app.data.SeededDb
import com.ironvellum.app.data.db.ExerciseEntity
import com.ironvellum.app.data.db.SessionEntity
import com.ironvellum.app.data.db.SetLogEntity
import com.ironvellum.app.data.db.SyncStateEntity
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The push against a small stateful fake of the cloud tables, over an isolated HTTP transport: the review
 * findings of 2026-10-28 that need the real orchestration and Room, not just a pure function.
 */
@RunWith(AndroidJUnit4::class)
class CloudPushHardeningTest {
    private class Req(val method: String, val table: String, val params: Map<String, String>, val body: String)

    /** Just enough PostgREST: keyed upserts, `in.(...)` and `eq.` filters, offset and limit, injected failures. */
    private class FakeCloud {
        val log = CopyOnWriteArrayList<Req>()
        @Volatile var schemaVersion: Int? = 31
        val sessions = ConcurrentHashMap<Long, String>()
        val importedSessions = ConcurrentHashMap<Long, String>()
        val sessionSets = CopyOnWriteArrayList<JsonObject>()
        val importedSets = CopyOnWriteArrayList<JsonObject>()
        @Volatile var snapshotHash: String? = null
        @Volatile var failWhen: (Req) -> Boolean = { false }
        @Volatile var before: suspend (Req) -> Unit = {}
        private val ids = AtomicInteger()

        private fun rows(body: String): List<JsonObject> {
            if (body.isBlank()) return emptyList()
            return when (val value = Json.parseToJsonElement(body)) {
                is JsonArray -> value.map { it.jsonObject }
                else -> listOf(value.jsonObject)
            }
        }

        private fun list(param: String?): List<String> =
            param?.removePrefix("in.(")?.removeSuffix(")")?.split(',')?.map { it.trim().trim('"') }.orEmpty()

        private fun page(all: List<JsonElement>, params: Map<String, String>): String {
            val offset = params["offset"]?.toInt() ?: 0
            val limit = params["limit"]?.toInt() ?: Int.MAX_VALUE
            return JsonArray(all.drop(offset).take(limit)).toString()
        }

        private fun String.text(key: String) = Json.parseToJsonElement(this).jsonObject.getValue(key).jsonPrimitive.content

        private fun sessionRows(store: ConcurrentHashMap<Long, String>, params: Map<String, String>): String {
            val filter = params["local_id"]
            val wanted = when {
                filter == null -> null
                filter.startsWith("eq.") -> setOf(filter.removePrefix("eq.").toLong())
                else -> list(filter).map { it.toLong() }.toSet()
            }
            val all = store.entries.filter { wanted == null || it.key in wanted }.sortedBy { it.key }.map { (local, id) ->
                buildJsonObject { put("id", JsonPrimitive(id)); put("local_id", JsonPrimitive(local)) }
            }
            return page(all, params)
        }

        private fun upsert(store: CopyOnWriteArrayList<JsonObject>, body: String) {
            for (row in rows(body)) {
                fun key(o: JsonObject) = listOf("session_id", "exercise_name", "set_index").map { o.getValue(it).jsonPrimitive.content }
                val existing = store.firstOrNull { key(it) == key(row) }
                val id = existing?.get("id") ?: JsonPrimitive("r${ids.incrementAndGet()}")
                if (existing != null) store.remove(existing)
                store += JsonObject(row + ("id" to id))
            }
        }

        private fun keyRows(store: List<JsonObject>, params: Map<String, String>): String {
            val sessions = list(params["session_id"]).toSet()
            return page(store.filter { it.getValue("session_id").jsonPrimitive.content in sessions }
                .sortedBy { it.getValue("id").jsonPrimitive.content }, params)
        }

        val engine = MockEngine { request ->
            val table = request.url.encodedPath.substringAfterLast('/')
            val method = request.method.value
            val params = request.url.parameters.entries().associate { it.key to it.value.first() }
            val body = (request.body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString().orEmpty()
            val req = Req(method, table, params, body)
            log += req
            before(req)
            val json = headersOf(HttpHeaders.ContentType, "application/json")
            if (table == "schema_version") {
                val version = schemaVersion
                return@MockEngine if (version == null) respond("{\"message\":\"probe failure\"}", HttpStatusCode.ServiceUnavailable, json)
                else respond(version.toString(), HttpStatusCode.OK, json)
            }
            if (failWhen(req)) return@MockEngine respond("{\"code\":\"23514\",\"message\":\"rejected\"}", HttpStatusCode.BadRequest, json)
            val out = when {
                table == "sessions" && method == "POST" -> { rows(body).forEach { sessions.getOrPut(it.getValue("local_id").jsonPrimitive.content.toLong()) { "s" + it.getValue("local_id").jsonPrimitive.content } }; "" }
                table == "sessions" && method == "GET" -> sessionRows(sessions, params)
                table == "sessions" && method == "DELETE" -> { list(params["local_id"]).forEach { sessions.remove(it.toLong()) }; "" }
                table == "imported_sessions" && method == "POST" -> { rows(body).forEach { importedSessions.getOrPut(it.getValue("local_id").jsonPrimitive.content.toLong()) { "i" + it.getValue("local_id").jsonPrimitive.content } }; "" }
                table == "imported_sessions" && method == "GET" -> sessionRows(importedSessions, params)
                table == "imported_sessions" && method == "DELETE" -> { list(params["local_id"]).forEach { importedSessions.remove(it.toLong()) }; "" }
                table == "session_sets" && method == "POST" -> { upsert(sessionSets, body); "" }
                table == "session_sets" && method == "GET" -> keyRows(sessionSets, params)
                table == "session_sets" && method == "DELETE" -> { sessionSets.removeAll { it.getValue("id").jsonPrimitive.content in list(params["id"]) }; "" }
                table == "imported_session_sets" && method == "POST" -> { upsert(importedSets, body); "" }
                table == "imported_session_sets" && method == "GET" -> keyRows(importedSets, params)
                table == "imported_session_sets" && method == "DELETE" -> { importedSets.removeAll { it.getValue("id").jsonPrimitive.content in list(params["id"]) }; "" }
                table == "web_snapshots" && method == "GET" -> snapshotHash?.let { "[{\"content_hash\":\"$it\"}]" } ?: "[]"
                table == "web_snapshots" && method == "POST" -> { snapshotHash = body.let { b -> rows(b).single().getValue("content_hash").jsonPrimitive.content }; "" }
                method == "GET" -> "[]"
                else -> ""
            }
            respond(out, HttpStatusCode.OK, json)
        }

        fun count(method: String, table: String) = log.count { it.method == method && it.table == table }
    }

    private class Env(
        val context: Context, val db: IronvellumDatabase, val repo: Repository, val cloud: FakeCloud,
        val user: String, val pullUp: Long,
    ) {
        val account = Account(user, "test@example.invalid", "Test", "private")
        val sync: CloudSync by lazy { newSync() }
        fun client() = createSupabaseClient("https://hardening.invalid", "test-key") {
            install(Auth)
            install(Postgrest)
            httpEngine = cloud.engine
        }

        fun newSync(): CloudSync = CloudSync(repo, AccountRepository(), client(), AppearanceStore(context), account)

        suspend fun session(
            label: String, startedAtMs: Long, imported: Boolean = false, editedAtMs: Long? = null,
            sets: List<Triple<Long, String, Int?>> = listOf(Triple(pullUp, "", null)),
        ): Long {
            val id = db.sessionDao().insertSession(SessionEntity(
                presetId = null, label = label, startedAtMs = startedAtMs, completedAtMs = startedAtMs + 1000,
                xpAwarded = 0, imported = imported, editedAtMs = editedAtMs,
            ))
            db.sessionDao().insertSets(sets.mapIndexed { index, (exercise, modifiers, rpe) ->
                SetLogEntity(sessionId = id, exerciseId = exercise, setIndex = index, reps = 8, weightKg = null,
                    modifiers = modifiers, done = true, rpe = rpe)
            })
            return id
        }
    }

    private fun env(name: String, block: suspend Env.() -> Unit) = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dbName = "hardening-$name.db"
        SeededDb.fresh(context, dbName)
        val db = IronvellumDatabase.create(context, dbName)
        val oldOwner = Cloud.tombstoneOwner
        val user = UUID.randomUUID().toString()
        try {
            Cloud.init(context)
            Cloud.tombstoneOwner = user
            val repo = Repository(db)
            repo.ensureSeeded()
            val cloud = FakeCloud()
            val pullUp = repo.exerciseIdByName("Pull-up") ?: error("Seed catalogue lacks the test exercise")
            Env(context, db, repo, cloud, user, pullUp).block()
        } finally {
            Cloud.tombstoneOwner = oldOwner
            db.close()
            context.deleteDatabase(dbName)
        }
    }

    private val morning = LocalDate.of(2026, 10, 27).atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private val hour = 3_600_000L

    @Test
    fun anUnchangedSnapshotIsNotUploadedAgainAsTheClockMoves() = env("dedup") {
        Cloud.markWebCaughtUp(user)
        session("Earlier", morning - 3 * 24 * hour)
        suspend fun pushAt(now: Long) { sync.snapshotClock = { now }; assertTrue(sync.push().getOrThrow().problems.isEmpty()) }
        pushAt(morning)
        assertEquals(1, cloud.count("POST", "web_snapshots"))
        pushAt(morning + hour)
        pushAt(morning + 2 * hour)
        assertEquals("an hour later the snapshot was uploaded again", 1, cloud.count("POST", "web_snapshots"))
        pushAt(morning + 25 * hour)
        assertEquals("a new day must refresh the fatigue figures", 2, cloud.count("POST", "web_snapshots"))
        session("New workout", morning + 25 * hour - hour / 2)
        pushAt(morning + 26 * hour)
        assertEquals("a new workout must upload", 3, cloud.count("POST", "web_snapshots"))
    }

    @Test
    fun importedNotesAndLabelsAreClampedAndNothingIsDeletedBeforeTheInsert() = env("imported") {
        Cloud.markWebCaughtUp(user)
        val longName = ExerciseEntity(name = "N".repeat(70), muscleGroup = "PULL", isWeighted = false)
        val longExercise = db.exerciseDao().insertAll(listOf(longName)).single()
        val note = "tempo three one one with a long pause at the bottom of every rep ".repeat(2)
        val imported = session("L".repeat(100), morning - 5 * 24 * hour, imported = true, sets = listOf(Triple(pullUp, note, null)))
        val oneBad = session("One bad set", morning - 4 * 24 * hour, imported = true,
            sets = listOf(Triple(pullUp, "", null), Triple(longExercise, "", null)))
        cloud.importedSets += buildJsonObject {
            put("id", JsonPrimitive("stale-1")); put("session_id", JsonPrimitive("i$imported"))
            put("exercise_name", JsonPrimitive("Old lift")); put("set_index", JsonPrimitive(0))
        }

        cloud.failWhen = { it.method == "POST" && it.table == "imported_session_sets" }
        val failed = sync.push().getOrThrow()
        assertTrue(failed.problems.isNotEmpty())
        assertEquals("the old sets were deleted before the new ones were stored", 0, cloud.count("DELETE", "imported_session_sets"))
        assertTrue("the failed workout kept its old set", cloud.importedSets.any { it.getValue("id").jsonPrimitive.content == "stale-1" })
        assertFalse(repo.pushWatermark().containsKey(imported))

        cloud.failWhen = { false }
        val secondRun = cloud.log.size
        val outcome = sync.push().getOrThrow()
        // The over-long exercise name is reported once; nothing else is.
        assertEquals(1, outcome.problems.size)
        val sessionPayload = cloud.log.last { it.method == "POST" && it.table == "imported_sessions" && it.body.contains("\"local_id\":$imported") }.body
        val label = Json.parseToJsonElement(sessionPayload).let { (it as? JsonArray)?.single()?.jsonObject ?: it.jsonObject }
            .getValue("label").jsonPrimitive.content
        assertEquals(80, label.length)
        val sets = cloud.importedSets.toList()
        assertTrue("the stale set was not removed", sets.none { it.getValue("id").jsonPrimitive.content == "stale-1" })
        assertEquals(64, sets.single { it.getValue("session_id").jsonPrimitive.content == "i$imported" }.getValue("modifiers").jsonPrimitive.content.length)
        assertEquals("only the good set of the other workout", 1, sets.count { it.getValue("session_id").jsonPrimitive.content == "i$oneBad" })
        val second = cloud.log.drop(secondRun)
        val firstWrite = second.indexOfFirst { it.method == "POST" && it.table == "imported_session_sets" }
        val delete = second.indexOfFirst { it.method == "DELETE" && it.table == "imported_session_sets" }
        assertTrue("the delete came before the write", firstWrite >= 0 && delete > firstWrite)
        assertTrue(repo.pushWatermark().containsKey(imported))
        assertTrue("a set the cloud cannot take must not wedge its workout", repo.pushWatermark().containsKey(oneBad))
    }

    @Test
    fun anInterruptedCatchUpResumesFromTheLastSavedBatch() = env("resume") {
        repeat(150) { session("Session $it", morning - (200 - it) * hour) }
        val posts = AtomicInteger()
        cloud.failWhen = { it.method == "POST" && it.table == "sessions" && posts.incrementAndGet() == 2 }
        // A failed push surfaces as a failure, or as the exception itself where the Result is unwrapped.
        assertTrue(runCatching { sync.push().isFailure }.getOrDefault(true))
        assertEquals("the first batch of 100 was not saved", 100, repo.pushWatermark().size)

        cloud.failWhen = { false }
        val before = cloud.log.size
        assertTrue(sync.push().getOrThrow().problems.isEmpty())
        val resent = cloud.log.drop(before).filter { it.method == "POST" && it.table == "sessions" }
            .sumOf { Json.parseToJsonElement(it.body).jsonArray.size }
        assertEquals("the saved sessions were sent again", 50, resent)
        assertEquals(150, repo.pushWatermark().size)
    }

    @Test
    fun aCancelledPushStaysCancelled() = env("cancel") {
        session("One", morning - hour)
        lateinit var job: kotlinx.coroutines.Job
        var returned = false
        cloud.before = { req ->
            if (req.method == "POST" && req.table == "sessions") {
                job.cancel()
                delay(10_000)
            }
        }
        job = CoroutineScope(Dispatchers.Default).launch { sync.push(); returned = true }
        job.join()
        assertFalse("the cancellation was swallowed: push returned a result", returned)
    }

    @Test
    fun amendedSessionsWithMoreThanAThousandCloudRowsAreSwept() = env("amended") {
        val local = session("Amended", morning - 2 * hour, editedAtMs = morning - hour)
        repeat(1500) { n ->
            cloud.sessionSets += buildJsonObject {
                put("id", JsonPrimitive("gone-%04d".format(n))); put("session_id", JsonPrimitive("s$local"))
                put("exercise_name", JsonPrimitive("Gone $n")); put("set_index", JsonPrimitive(0))
            }
        }
        assertTrue(sync.push().getOrThrow().problems.isEmpty())
        assertEquals(listOf("Pull-up"), cloud.sessionSets.map { it.getValue("exercise_name").jsonPrimitive.content })
        assertTrue("the select was not paged", cloud.count("GET", "session_sets") >= 2)
        val deletes = cloud.log.filter { it.method == "DELETE" && it.table == "session_sets" }
        assertTrue(deletes.size >= 30)
        assertTrue("a delete filter ran past the URL limit", deletes.all { it.params.getValue("id").split(',').size <= 50 })
    }

    @Test
    fun anUnrelatedProblemDoesNotStopTheWebStats() = env("side") {
        session("One", morning - 3 * 24 * hour)
        cloud.failWhen = { it.table == "lift_marks" }
        val outcome = sync.push().getOrThrow()
        assertEquals("only the tier problem", 1, outcome.problems.size)
        assertEquals("the stats were held back by a tier problem", 1, cloud.count("POST", "web_snapshots"))
        assertTrue(Cloud.webCaughtUp(user))
    }

    @Test
    fun deletesBelowSchema30ClearTheirTombstonesAndAnUnknownVersionKeepsThem() = env("tombstones") {
        db.syncStateDao().upsertAll(listOf(SyncStateEntity(77, SyncStateEntity.TOMBSTONE)))
        cloud.schemaVersion = null
        assertTrue(sync.push().getOrThrow().problems.isNotEmpty())
        assertEquals("an unknown version must hold the deletions", listOf(77L), repo.tombstonedSessions())
        assertEquals(0, cloud.count("DELETE", "imported_sessions"))

        cloud.schemaVersion = 29
        val old = newSync()
        old.push().getOrThrow()
        assertEquals("there is no imported table below schema 30", 0, cloud.count("DELETE", "imported_sessions"))
        assertTrue(cloud.count("DELETE", "sessions") >= 1)
        assertTrue("the tombstone of a backend with no imported table stayed", repo.tombstonedSessions().isEmpty())

        db.syncStateDao().upsertAll(listOf(SyncStateEntity(78, SyncStateEntity.TOMBSTONE)))
        cloud.schemaVersion = 31
        newSync().push().getOrThrow()
        assertEquals(1, cloud.count("DELETE", "imported_sessions"))
        assertTrue(repo.tombstonedSessions().isEmpty())
    }

    @Test
    fun overlappingPushesRunOneAtATime() = env("mutex") {
        Cloud.markWebCaughtUp(user)
        session("One", morning - hour)
        cloud.before = { req -> if (req.method == "POST" && req.table == "sessions") delay(300) }
        val scope = CoroutineScope(Dispatchers.Default)
        val results = listOf(scope.async { sync.push() }, scope.async { sync.push() }).awaitAll()
        assertTrue(results.all { it.isSuccess })
        assertEquals("both pushes sent the session", 1, cloud.count("POST", "sessions"))
    }

    @Test
    fun aBeaconThatStopsAnsweringDoesNotResendEverythingEveryPush() = env("beacon") {
        Cloud.markWebCaughtUp(user)
        val id = session("One", morning - hour, sets = listOf(Triple(pullUp, "", 9)))
        assertTrue(sync.push().getOrThrow().problems.isEmpty())
        assertEquals(1, cloud.count("POST", "sessions"))

        val set = db.sessionDao().setsFor(id).single()
        db.sessionDao().updateSet(set.copy(rpe = 8))
        cloud.schemaVersion = null
        sync.push().getOrThrow()
        assertEquals(2, cloud.count("POST", "sessions"))
        assertTrue("the version it knew was not kept", CloudSync.pendingForPush(repo.observeHistory().first(), repo.pushWatermark()).isEmpty())
        sync.push().getOrThrow()
        assertEquals("a dead beacon made every push send the set again", 2, cloud.count("POST", "sessions"))

        // A phone that has never had an answer still certifies nothing.
        db.sessionDao().updateSet(set.copy(rpe = 7))
        val fresh = newSync()
        fresh.push().getOrThrow()
        assertNotNull(CloudSync.pendingForPush(repo.observeHistory().first(), repo.pushWatermark()).singleOrNull())
    }
}
