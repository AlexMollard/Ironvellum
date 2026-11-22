package com.ironvellum.app.data

import androidx.test.platform.app.InstrumentationRegistry
import androidx.room.withTransaction
import com.ironvellum.app.data.db.*
import com.ironvellum.app.data.localconnector.*
import com.ironvellum.app.domain.LocalConnectorFiles
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.*

class LocalConnectorRepositoryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "local-files-test.db"
    private lateinit var db: IronvellumDatabase
    private lateinit var files: LocalConnectorRepository
    private var now = 10_000L
    private val sql get() = db.openHelper.writableDatabase
    @Before fun setup() {
        SeededDb.fresh(context, name)
        db = IronvellumDatabase.create(context, name)
        files = LocalConnectorRepository(db) { now }
        sql.execSQL("DELETE FROM presets")
        sql.execSQL("DELETE FROM sessions")
    }
    @After fun close() { db.close(); context.deleteDatabase(name) }
    private fun count(table: String) = sql.query("SELECT count(*) FROM " + table).use { it.moveToFirst(); it.getInt(0) }
    private suspend fun proposal(): ByteArray {
        val source = files.exportContext().bytes
        val ctx = Json.parseToJsonElement(source.decodeToString()).jsonObject
        return buildJsonObject {
            put("type","ironvellum.workout-draft"); put("schemaVersion",1)
            put("datasetId",ctx.getValue("datasetId")); put("snapshotId",ctx.getValue("snapshotId"))
            put("contextSha256", LocalConnectorFiles.sha256(source)); put("draftId","d336486f-5add-4499-ae27-278678950739")
            put("createdAtMs",now); put("expiresAtMs",now + 100); put("sender","PC")
            put("draft",Json.parseToJsonElement("""{"schemaVersion":1,"name":"Test 🏋","warmup":"AUTO","cooldown":"NONE","entries":[{"exerciseKey":"bench press","sets":3,"reps":8,"loadKg":12.345}]}"""))
        }.toString().encodeToByteArray()
    }
    @Test fun boundedExportReportsEveryOmissionAndContainsOnlyProjectedFields() = runBlocking {
        val bench = db.exerciseDao().byName("Bench Press")!!.id
        val assisted = db.exerciseDao().byName("Assisted Pull-up")!!.id
        sql.execSQL("UPDATE profile SET name='CANARY_PROFILE',heightCm=198.765")
        sql.execSQL("INSERT INTO stats(takenAtMs,weightKg,heightCm,bodyFatPct) VALUES(100,99.876,198.765,43.2109)")
        val hold = db.exerciseDao().insertAll(listOf(ExerciseEntity(name="CANARY_CUSTOM",muscleGroup="Private",isWeighted=false,custom=true))).single()
        repeat(101) { i ->
            val id = db.sessionDao().insertSession(SessionEntity(presetId=null,label="CANARY_LABEL",startedAtMs=i.toLong(),
                completedAtMs=i+1L,xpAwarded=100,note="CANARY_PUBLIC",privateNote="CANARY_PRIVATE",title="CANARY_TITLE"))
            if (i == 100) {
                fun row(n: Int) = SetLogEntity(sessionId=id,exerciseId=bench,setIndex=n,reps=8,weightKg=12.5,modifiers="",done=true)
                db.sessionDao().insertSets((1..201).map(::row) + row(202).copy(exerciseId=hold) +
                    row(203).copy(modifiers="CANARY_MODIFIER") + row(204).copy(exerciseId=assisted) +
                    row(205).copy(weightKg=-25.0) + row(206).copy(kind="CANARY_UNKNOWN"))
                sql.execSQL("INSERT INTO session_exercise_notes(sessionId,exerciseId,note) VALUES ($id,$bench,'CANARY_EXERCISE')")
            }
        }
        db.sessionDao().insertSession(SessionEntity(presetId=null,label="CANARY_OPEN",startedAtMs=120,completedAtMs=null,xpAwarded=0))
        repeat(52) { i ->
            val id = db.presetDao().insertPreset(PresetEntity(name="Plan $i",note="CANARY_PRESET",scheduledDay=2))
            db.presetDao().insertEntries(listOf(PresetEntryEntity(presetId=id,exerciseId=if(i==51) hold else bench,
                targetSets=2,targetReps=5,targetWeightKg=null,position=0,modifiers="")))
        }
        val exported = files.exportContext()
        val source = exported.bytes.decodeToString()
        assertFalse(source.contains("CANARY"))
        assertFalse(source.contains("198.765")); assertFalse(source.contains("99.876")); assertFalse(source.contains("43.2109"))
        val c = Json.parseToJsonElement(source).jsonObject
        assertEquals(setOf("type","schemaVersion","datasetId","snapshotId","exportedAtMs","catalogue","coverage","workouts","savedWorkouts","progress"), c.keys)
        val w = c.getValue("workouts").jsonArray
        assertEquals(100,w.size)
        val first = w.first().jsonObject
        assertEquals(setOf("id","startedAtMs","completedAtMs","sets"),first.keys)
        val sets = first.getValue("sets").jsonArray
        assertEquals(200,sets.size)
        assertEquals(setOf("order","exerciseKey","reps","loadKg"),sets.first().jsonObject.keys)
        val presets = c.getValue("savedWorkouts").jsonArray
        assertEquals(50,presets.size)
        assertEquals(setOf("id","name","warmup","cooldown","entries"),presets.first().jsonObject.keys)
        assertEquals(setOf("exerciseKey","sets","reps","loadKg"),presets.first().jsonObject.getValue("entries").jsonArray.first().jsonObject.keys)
        val coverage = c.getValue("coverage").jsonObject
        fun n(key: String) = coverage.getValue(key).jsonPrimitive.int
        assertEquals(1,n("workoutsTruncated")); assertEquals(1,n("setsTruncated")); assertEquals(5,n("setsUnsupported"))
        assertEquals(1,n("savedWorkoutsTruncated")); assertEquals(1,n("savedWorkoutsUnsupported"))
        val progress = c.getValue("progress").jsonObject
        assertEquals(setOf("version","scope","completedWorkouts","completedSets","completedReps","externalVolumeKg",
            "firstCompletedAtMs","lastCompletedAtMs","lifts","liftsTruncated"),progress.keys)
        assertEquals(setOf("exerciseKey","completedSets","maxLoadKg","maxReps"),progress.getValue("lifts").jsonArray.single().jsonObject.keys)
        assertEquals(1600,progress.getValue("completedReps").jsonPrimitive.int)
        assertEquals(20000.0,progress.getValue("externalVolumeKg").jsonPrimitive.double,0.0)
        val again = Json.parseToJsonElement(files.exportContext().bytes.decodeToString()).jsonObject
        assertEquals(w,again["workouts"]); assertEquals(presets,again["savedWorkouts"])
        assertEquals(c["datasetId"],again["datasetId"]); assertNotEquals(c["snapshotId"],again["snapshotId"])
    }
    @Test fun emptyContextHasDatedReceiptAndExplicitEmptyProgress() = runBlocking {
        val export = files.exportContext()
        val untouched = export.bytes
        export.bytes.fill(0)
        assertArrayEquals(untouched, export.bytes)
        val ctx = Json.parseToJsonElement(untouched.decodeToString()).jsonObject
        val progress = ctx.getValue("progress").jsonObject
        assertEquals(JsonNull,progress["firstCompletedAtMs"])
        assertEquals(JsonNull,progress["lastCompletedAtMs"])
        assertEquals(0, progress.getValue("lifts").jsonArray.size)
        sql.query("SELECT contextSha256 FROM local_connector_exports").use {
            assertTrue(it.moveToFirst()); assertEquals(LocalConnectorFiles.sha256(untouched),it.getString(0))
        }
    }
    @Test fun unsupportedRoutinesAreCountedWholeAndNamesAreNeverTruncated() = runBlocking {
        val bench = db.exerciseDao().byName("Bench Press")!!.id
        suspend fun preset(name: String, count: Int = 1, change: (PresetEntryEntity) -> PresetEntryEntity = { it }): Long {
            val id = db.presetDao().insertPreset(PresetEntity(name=name,note="CANARY_NOTE",scheduledDay=null))
            db.presetDao().insertEntries((1..count).map {
                change(PresetEntryEntity(presetId=id, exerciseId=bench, targetSets=2, targetReps=5,
                    targetWeightKg=null, position=it, modifiers=""))
            })
            return id
        }
        val permitted = "🏋".repeat(80)
        preset(permitted)
        preset("🏋".repeat(81))
        preset("too many",21)
        preset("variable") { it.copy(setScheme="variable") }
        preset("warmup entry") { it.copy(warmup=true) }
        val routine = preset("specific routine")
        sql.execSQL("INSERT INTO preset_preparations(presetId,kind,mode,routineId) VALUES($routine,'WARMUP','SAVED',123)")
        val export = files.exportContext()
        assertEquals(5L,export.summary.coverage.savedWorkoutsUnsupported)
        val row = Json.parseToJsonElement(export.bytes.decodeToString()).jsonObject.getValue("savedWorkouts").jsonArray.single()
        assertEquals(permitted,row.jsonObject.getValue("name").jsonPrimitive.content)
        sql.execSQL("UPDATE exercises SET custom=1")
        val empty = files.exportContext()
        assertEquals(0,empty.summary.coverage.savedWorkoutsIncluded)
        assertEquals(6L,empty.summary.coverage.savedWorkoutsUnsupported)
    }
    @Test fun onlyExplicitSaveWritesAndConcurrentRetriesReturnHistoricalReceipt() = runBlocking {
        val bytes = proposal()
        val review = (files.inspectDraft(bytes) as LocalInspection.Ready).review
        val original = bytes.copyOf()
        bytes.fill(0)
        assertEquals(0,count("presets"))
        val results = (1..4).map { async(Dispatchers.IO) { files.saveDraft(review) } }.awaitAll()
        assertEquals(1,results.filterIsInstance<LocalSave.Saved>().size)
        assertEquals(3,results.filterIsInstance<LocalSave.AlreadySaved>().size)
        assertEquals(1,count("presets")); assertEquals(1,count("preset_entries")); assertEquals(2,count("preset_preparations"))
        assertEquals(0,count("sessions")); assertEquals(0,count("mcp_draft_receipts"))
        sql.execSQL("DELETE FROM presets")
        now += 500
        assertTrue(files.inspectDraft(original) is LocalInspection.AlreadySaved)
        assertTrue(files.saveDraft(review) is LocalSave.AlreadySaved)
        assertEquals(LocalFailure.CONFLICT,(files.inspectDraft(original + 32.toByte()) as LocalInspection.Rejected).reason)
        assertEquals(0,count("presets"))
    }
    @Test fun restoreExpiryAndMovementEditsFenceOpenReviews() = runBlocking {
        val bytes = proposal()
        val review = (files.inspectDraft(bytes) as LocalInspection.Ready).review
        now += 101
        assertEquals(LocalFailure.EXPIRED,(files.saveDraft(review) as LocalSave.Rejected).reason)
        now -= 101
        sql.execSQL("UPDATE exercises SET metric='HOLD' WHERE name='Bench Press'")
        assertEquals(LocalFailure.MOVEMENT,(files.saveDraft(review) as LocalSave.Rejected).reason)
        sql.execSQL("UPDATE exercises SET metric='REPS' WHERE name='Bench Press'")
        db.exerciseDao().insertAll(listOf(ExerciseEntity(name=" Bench Press ",muscleGroup="",isWeighted=true,custom=true)))
        assertEquals(LocalFailure.MOVEMENT,(files.saveDraft(review) as LocalSave.Rejected).reason)
        val repo = Repository(db)
        repo.importArchive(repo.exportArchive().json).getOrThrow()
        assertEquals(LocalFailure.WRONG_DATASET,(files.saveDraft(review) as LocalSave.Rejected).reason)
        assertEquals(0,count("local_connector_exports")); assertEquals(0,count("presets"))
        assertEquals(LocalFailure.WRONG_DATASET,(files.inspectDraft(bytes) as LocalInspection.Rejected).reason)
    }
    @Test fun receiptFailureRollsBackEntriesAndOversizedCatalogueFailsClosed() = runBlocking {
        val bytes = proposal()
        val review = (files.inspectDraft(bytes) as LocalInspection.Ready).review
        sql.execSQL("CREATE TRIGGER refuse_receipt BEFORE INSERT ON local_connector_receipts BEGIN SELECT RAISE(ABORT,'fixture'); END")
        assertEquals(LocalFailure.STORAGE,(files.saveDraft(review) as LocalSave.Rejected).reason)
        assertEquals(0,count("presets")); assertEquals(0,count("preset_entries")); assertEquals(0,count("preset_preparations"))
        sql.execSQL("""WITH RECURSIVE extra(n) AS (SELECT 1 UNION ALL SELECT n+1 FROM extra WHERE n<5001)
            INSERT INTO exercises(name,muscleGroup,isWeighted,metric,category,custom,gear,muscles)
            SELECT 'x'||n,'',0,'REPS','',1,'','' FROM extra""")
        assertEquals(LocalFailure.CATALOGUE_LIMIT,(files.saveDraft(review) as LocalSave.Rejected).reason)
        assertEquals(LocalFailure.CATALOGUE_LIMIT,(files.inspectDraft(bytes) as LocalInspection.Rejected).reason)
        assertTrue(runCatching { files.exportContext() }.exceptionOrNull() is LocalExportException)
    }

    @Test fun olderExportIsRecognizedButAlteredSourceOrFutureDraftIsRefused() = runBlocking {
        val original = proposal()
        files.exportContext()
        val ready = files.inspectDraft(original) as LocalInspection.Ready
        assertFalse(ready.review.isLatestExport)
        val obj = Json.parseToJsonElement(original.decodeToString()).jsonObject
        suspend fun inspect(vararg fields: Pair<String,JsonElement>): LocalFailure {
            val bytes = JsonObject(obj + fields.toMap()).toString().encodeToByteArray()
            return (files.inspectDraft(bytes) as LocalInspection.Rejected).reason
        }
        assertEquals(LocalFailure.UNKNOWN_EXPORT,inspect("contextSha256" to JsonPrimitive("1".repeat(64))))
        assertEquals(LocalFailure.UNKNOWN_EXPORT,inspect("snapshotId" to obj.getValue("draftId")))
        assertEquals(LocalFailure.WRONG_DATASET,inspect("datasetId" to obj.getValue("draftId")))
        assertEquals(LocalFailure.FUTURE,inspect("createdAtMs" to JsonPrimitive(now+300001),"expiresAtMs" to JsonPrimitive(now+310000)))
        var reads = 0
        val changingClock = LocalConnectorRepository(db) { if (++reads == 1) now else now + 100 }
        assertEquals(LocalFailure.EXPIRED,(changingClock.saveDraft(ready.review) as LocalSave.Rejected).reason)
        assertEquals(0,count("presets")); assertEquals(0,count("local_connector_receipts"))
    }

    @Test fun queuedSaveRejectsOriginalDatasetAfterFullRestoreCommits() = runBlocking {
        val bytes = proposal()
        val review = (files.inspectDraft(bytes) as LocalInspection.Ready).review
        val priorBytes = bytes.decodeToString().replace(review.proposal.draftId,"97776cc9-d1ff-48cb-839f-0af9916859b7").encodeToByteArray()
        val prior = (files.inspectDraft(priorBytes) as LocalInspection.Ready).review
        assertTrue(files.saveDraft(prior) is LocalSave.Saved)
        files.exportContext()
        assertEquals(1,count("local_connector_objects")); assertEquals(1,count("local_connector_receipts"))
        assertEquals(1,count("local_connector_identity")); assertEquals(2,count("local_connector_exports"))
        val repo = Repository(db)
        val archive = repo.exportArchive().json
        val restored = CompletableDeferred<Unit>()
        val allowCommit = CompletableDeferred<Unit>()
        val replacing = async {
            db.withTransaction {
                repo.importArchive(archive).getOrThrow()
                restored.complete(Unit)
                allowCommit.await()
            }
        }
        restored.await()
        val queued = async(start=CoroutineStart.UNDISPATCHED) { files.saveDraft(review) }
        allowCommit.complete(Unit)
        replacing.await()
        assertEquals(LocalFailure.WRONG_DATASET,(queued.await() as LocalSave.Rejected).reason)
        assertEquals(0,count("local_connector_objects")); assertEquals(0,count("local_connector_exports"))
        assertEquals(1,count("presets")); assertEquals(0,count("local_connector_receipts")); assertEquals(0,count("local_connector_identity"))
        assertEquals(LocalFailure.WRONG_DATASET,(files.inspectDraft(priorBytes) as LocalInspection.Rejected).reason)
        assertNotEquals(review.proposal.datasetId,files.exportContext().summary.datasetId)
    }

    @Test fun allMachineAssistanceKeysAreIneligibleEvenWithoutALoad() = runBlocking {
        val source = proposal().decodeToString()
        for (key in listOf("assisted pull-up","assisted chin-up","assisted dip")) {
            for (load in listOf("12.345","null")) {
                val bytes = source.replace("bench press",key).replace("12.345",load).encodeToByteArray()
                val outcome = files.inspectDraft(bytes)
                assertEquals(LocalInspection.Rejected(LocalFailure.MOVEMENT),outcome)
            }
        }
        assertEquals(0,count("presets")); assertEquals(0,count("local_connector_receipts"))
    }

    @Test fun saveRechecksAssistancePolicyAndStillHonoursMatchingHistoricalReceipts() = runBlocking {
        val original = proposal()
        val baseline = (files.inspectDraft(original) as LocalInspection.Ready).review
        val bytes = original.decodeToString().replace("bench press","assisted dip").encodeToByteArray()
        val p = LocalConnectorFiles.proposal(bytes)
        val movement = db.exerciseDao().byName("Assisted Dip")!!.id
        // Recreate the handle the previous admission policy produced. Keep this test-only construction
        // outside the repository so the app never exposes a way to skip inspection.
        val constructor = baseline.javaClass.declaredConstructors.single().apply { isAccessible = true }
        val oldReview = constructor.newInstance(bytes,p,baseline.exportedAtMs,true,listOf(movement)) as LocalDraftReview
        assertEquals(LocalSave.Rejected(LocalFailure.MOVEMENT),files.saveDraft(oldReview))
        assertEquals(0,count("presets")); assertEquals(0,count("local_connector_receipts"))
        db.withTransaction {
            db.localConnectorDao().insert(LocalReceipt(p.datasetId,p.draftId,p.sha256,p.snapshotId,42,now))
        }
        now = p.expiresAtMs
        assertEquals(LocalInspection.AlreadySaved(42),files.inspectDraft(bytes))
        assertEquals(LocalSave.AlreadySaved(42),files.saveDraft(oldReview))
        assertEquals(0,count("presets"))
    }
}
