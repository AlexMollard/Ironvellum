package com.ironvellum.app.data

import androidx.test.platform.app.InstrumentationRegistry
import androidx.room.withTransaction
import com.ironvellum.app.data.db.*
import com.ironvellum.app.data.localconnector.*
import com.ironvellum.app.domain.*
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
    private fun addLargeExerciseCatalogue() {
        sql.execSQL("""WITH RECURSIVE extra(n) AS (SELECT 1 UNION ALL SELECT n+1 FROM extra WHERE n<5001)
            INSERT INTO exercises(name,muscleGroup,isWeighted,metric,category,custom,gear,muscles)
            SELECT 'x'||n,'',0,'REPS','',1,'','' FROM extra""")
    }
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
    @Test fun allCompletedHistoryKeepsRecordedMetricsKindsSchemesAndFrozenPreparations() = runBlocking {
        val bench = db.exerciseDao().byName("Bench Press")!!.id
        sql.execSQL("UPDATE profile SET name='CANARY_PROFILE',heightCm=198.765")
        sql.execSQL("INSERT INTO stats(takenAtMs,weightKg,heightCm,bodyFatPct) VALUES(100,99.876,198.765,43.2109)")
        fun existing(metric: String) = sql.query("SELECT id FROM exercises WHERE metric='$metric' ORDER BY id LIMIT 1").use {
            assertTrue(it.moveToFirst()); it.getLong(0)
        }
        val assisted = db.exerciseDao().byName("Assisted Dip")!!.id
        val custom = db.exerciseDao().insertAll(listOf(ExerciseEntity(name="My rings",muscleGroup="CANARY_MUSCLE",isWeighted=true,custom=true))).single()
        val ambiguous = db.exerciseDao().insertAll(listOf(ExerciseEntity(name=" Bench Press ",muscleGroup="",isWeighted=true))).single()
        val movement = listOf(bench, existing("HOLD"), existing("DURATION"), existing("DISTANCE_TIME"), existing("ATTEMPTS_GRADE"), assisted, custom, ambiguous)
        val step = PreparationStep(custom,"Frozen rings",PreparationMeasure.SECONDS,20,2,PreparationRepeat.SIDES,
            "CANARY_STEP",2.5,PreparationLegacyTarget(ExerciseMetric.DISTANCE_TIME,7,1.25))
        val routine = db.preparationDao().insertRoutine(PreparationRoutineEntity(kind="WARMUP",name="My routine",
            stepsJson=PreparationCodec.encodeSteps(listOf(step))))
        val mods = "weighted,tempo,paused,archer,one-arm,assisted,incline,decline,elevated,deficit,banded"
        val kinds = SetKind.entries
        var previous = 0
        repeat(19) { i ->
            val id = db.sessionDao().insertSession(SessionEntity(presetId=null,label="CANARY_LABEL",startedAtMs=i.toLong(),
                completedAtMs=i+1L,xpAwarded=100,note="CANARY_PUBLIC",privateNote="CANARY_PRIVATE",title="CANARY_TITLE"))
            val count = if (i == 18) 21 else 16
            db.sessionDao().insertSets((0 until count).map { j ->
                val n = previous + j
                SetLogEntity(sessionId=id,exerciseId=movement[n % movement.size],exercisePosition=j/2,setIndex=j%2,
                    reps=if(n%3==0) 0 else 7,weightKg=if(n%4==0) null else 5.0,modifiers=mods,done=n%5!=0,
                    durationSec=if(n%3==0) 0 else if(n%3==1) 13 else null,
                    distanceM=if(n%8==3) 51.25 else null,grade=if(n%8==4) "V2" else null,
                    kind=kinds[n%kinds.size].name,supersetGroup=39842,rpe=8,warmupDone=n%2==0)
            })
            previous += count
            if (i == 0) {
                sql.execSQL("INSERT INTO session_exercise_notes(sessionId,exerciseId,note) VALUES(?,?,'CANARY_EXERCISE')",
                    arrayOf(id,bench))
                val frozen = step.copy(exerciseId=777771,exerciseName="Former exercise")
                val snap = PreparationSnapshot(PreparationKind.WARMUP,"My frozen routine",listOf(frozen),777772,1)
                val p = PreparationProgress(status=PreparationStatus.PAUSED,completedUnitKeys=setOf("0:0:0"),
                    skippedUnitKeys=setOf("0:0:1"),roundIndex=1,remainingMs=1000)
                sql.execSQL("INSERT INTO session_preparations(sessionId,kind,snapshotJson,progressJson) VALUES(?,'WARMUP',?,?)",
                    arrayOf<Any>(id,PreparationCodec.encodeSnapshot(snap),PreparationCodec.encodeProgress(p)))
            }
        }
        val schemes = listOf(SetScheme(workingReps=6..10),SetScheme(type=SchemeType.TOP_BACKOFF,backoffFixedKg=5.5,warmupRamp=true),
            SetScheme(type=SchemeType.TOP_BACKOFF),SetScheme(type=SchemeType.DROPS),SetScheme(type=SchemeType.CUSTOM,custom=kinds.filter { it!=SetKind.WARMUP }))
        repeat(7) { i ->
            val id = db.presetDao().insertPreset(PresetEntity(name="Saved $i",note="CANARY_PRESET",scheduledDay=2))
            db.presetDao().insertEntries(movement.take(listOf(8,6,6,6,5,5,5)[i]).mapIndexed { j, ex -> PresetEntryEntity(presetId=id,exerciseId=ex,targetSets=2,
                targetReps=0,targetWeightKg=1.25,position=j,modifiers=mods,
                setScheme=when {
                    i==6 && j>=3 -> null
                    i==0 && j<5 -> schemes[j].encode()
                    i==1 && j==0 -> schemes[1].encode()
                    else -> schemes[0].encode()
                }, supersetGroup=39842.takeIf { i==0 && j<2 },warmup=j==0) })
            sql.execSQL("INSERT INTO preset_preparations VALUES(?,'WARMUP','SAVED',?)",arrayOf(id,routine))
            sql.execSQL("INSERT INTO preset_preparations VALUES(?,'COOLDOWN','NONE',NULL)",arrayOf(id))
        }
        db.sessionDao().insertSession(SessionEntity(presetId=null,label="CANARY_OPEN",startedAtMs=400,completedAtMs=null,xpAwarded=0))
        val source = files.exportContext().bytes.decodeToString()
        val c = Json.parseToJsonElement(source).jsonObject
        assertEquals(2,c.getValue("schemaVersion").jsonPrimitive.int)
        assertFalse(source.contains("CANARY")); assertFalse(source.contains("198.765")); assertFalse(source.contains("99.876"))
        assertFalse(source.contains("43.2109"))
        val workouts = c.getValue("workouts").jsonArray
        assertEquals(19,workouts.size)
        val rows = workouts.flatMap { it.jsonObject.getValue("sets").jsonArray }.map { it.jsonObject }
        assertEquals(309,rows.size)
        assertEquals(kinds.map { it.name }.toSet(),rows.map { it.getValue("kind").jsonPrimitive.content }.toSet())
        assertTrue(rows.all { it.getValue("modifiers").jsonPrimitive.content == mods })
        assertEquals(rows.count { it.getValue("done").jsonPrimitive.boolean && it.getValue("kind").jsonPrimitive.content != "WARMUP" },
            c.getValue("progress").jsonObject.getValue("completedSets").jsonPrimitive.int)
        assertEquals(7,c.getValue("savedWorkouts").jsonArray.size)
        val allEntries = c.getValue("savedWorkouts").jsonArray.flatMap { it.jsonObject.getValue("entries").jsonArray }.map { it.jsonObject }
        assertEquals(41,allEntries.size)
        assertEquals(39,allEntries.count { it.getValue("schemeRaw") != JsonNull })
        assertEquals(2,allEntries.count { it.getValue("superset") != JsonNull })
        assertEquals(3,allEntries.count { it.getValue("scheme").jsonObject.getValue("type").jsonPrimitive.content == "top_backoff" })
        val firstSaved = c.getValue("savedWorkouts").jsonArray.single { it.jsonObject.getValue("name").jsonPrimitive.content == "Saved 0" }.jsonObject
        val entries = firstSaved.getValue("entries").jsonArray
        assertTrue(entries[0].jsonObject.getValue("warmup").jsonPrimitive.boolean)
        assertEquals(0,entries[0].jsonObject.getValue("superset").jsonPrimitive.int)
        assertEquals(0,entries[1].jsonObject.getValue("superset").jsonPrimitive.int)
        assertEquals("SAVED",firstSaved.getValue("warmup").jsonObject.getValue("mode").jsonPrimitive.content)
        assertEquals("NONE",firstSaved.getValue("cooldown").jsonObject.getValue("mode").jsonPrimitive.content)
        assertEquals(schemes[1].encode(),entries[1].jsonObject.getValue("schemeRaw").jsonPrimitive.content)
        assertEquals(5.5,entries[1].jsonObject.getValue("scheme").jsonObject.getValue("backoffFixedKg").jsonPrimitive.double,0.0)
        assertEquals(1.25,entries[3].jsonObject.getValue("target").jsonObject.getValue("distanceKm").jsonPrimitive.double,0.0)
        val frozen = workouts.flatMap { it.jsonObject.getValue("preparations").jsonArray }.single().jsonObject
        assertEquals("My frozen routine",frozen.getValue("name").jsonPrimitive.content)
        assertTrue(frozen.getValue("sourceRoutineId").jsonPrimitive.content.startsWith("local:"))
        assertEquals(1,frozen.getValue("legacyStepCount").jsonPrimitive.int)
        val frozenStep = frozen.getValue("steps").jsonArray.single().jsonObject
        assertEquals("Former exercise",frozenStep.getValue("name").jsonPrimitive.content)
        assertEquals(setOf("exerciseKey","name","measure","target","rounds","repeat","weightKg","legacyTarget"),frozenStep.keys)
        assertEquals("PAUSED",frozen.getValue("progress").jsonObject.getValue("status").jsonPrimitive.content)
        assertEquals("0:0:0",frozen.getValue("progress").jsonObject.getValue("completedUnitKeys").jsonArray.single().jsonPrimitive.content)
        assertEquals("0:0:1",frozen.getValue("progress").jsonObject.getValue("skippedUnitKeys").jsonArray.single().jsonPrimitive.content)
        val savedRoutine = c.getValue("routines").jsonArray.single().jsonObject
        assertEquals("My routine",savedRoutine.getValue("name").jsonPrimitive.content)
        assertEquals(firstSaved.getValue("warmup").jsonObject.getValue("routineId"),savedRoutine.getValue("id"))
        assertEquals("Frozen rings",savedRoutine.getValue("steps").jsonArray.single().jsonObject.getValue("name").jsonPrimitive.content)
        val registry = c.getValue("movements").jsonArray.map { it.jsonObject }
        assertEquals("HISTORICAL", registry.single { it.getValue("key") == frozenStep.getValue("exerciseKey") }
            .getValue("source").jsonPrimitive.content)
        assertEquals(setOf("0"),rows.map { it.getValue("superset").toString() }.toSet())
        for (name in listOf("My rings","Bench Press"," Bench Press ")) {
            assertTrue(registry.single { it.getValue("name").jsonPrimitive.content == name }.getValue("key").jsonPrimitive.content.startsWith("local:"))
        }
        val again = Json.parseToJsonElement(files.exportContext().bytes.decodeToString()).jsonObject
        assertEquals(workouts,again["workouts"]); assertEquals(c["movements"],again["movements"])
    }

    @Test fun allRowsBeyondOldReadLimitsAndEveryMovementSummaryAreAvailable() = runBlocking {
        val ids = sql.query("SELECT id FROM exercises WHERE metric='REPS' ORDER BY id LIMIT 25").use { c ->
            buildList { while(c.moveToNext()) add(c.getLong(0)) }
        }
        repeat(101) { i ->
            val id = db.sessionDao().insertSession(SessionEntity(presetId=null,label="",startedAtMs=0,completedAtMs=1,xpAwarded=0))
            if(i==0) db.sessionDao().insertSets((0..200).map { j -> SetLogEntity(sessionId=id,exerciseId=ids[j%25],
                setIndex=j,reps=8,weightKg=2.5,modifiers="",done=true) })
        }
        repeat(51) { i ->
            val id = db.presetDao().insertPreset(PresetEntity(name="Saved $i",note="",scheduledDay=null))
            db.presetDao().insertEntries((0..20).map { j -> PresetEntryEntity(presetId=id,exerciseId=ids[j],targetSets=1,
                targetReps=1,targetWeightKg=null,position=j,modifiers="") })
        }
        val ctx = Json.parseToJsonElement(files.exportContext().bytes.decodeToString()).jsonObject
        assertEquals(101,ctx.getValue("workouts").jsonArray.size)
        assertEquals(201,ctx.getValue("coverage").jsonObject.getValue("setsIncluded").jsonPrimitive.int)
        assertEquals(51,ctx.getValue("savedWorkouts").jsonArray.size)
        assertEquals(21,ctx.getValue("savedWorkouts").jsonArray.first().jsonObject.getValue("entries").jsonArray.size)
        assertEquals(25,ctx.getValue("progress").jsonObject.getValue("lifts").jsonArray.size)
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
    @Test fun importedDisplayTextIsPreservedExactlyWithinTheFileLimit() = runBlocking {
        val names = listOf("", "Legacy\nname\twith spaces ", "x".repeat(2 * 1024 * 1024 + 32))
        names.forEach { db.presetDao().insertPreset(PresetEntity(name=it,note="",scheduledDay=null)) }
        val source = files.exportContext().bytes
        val actual = Json.parseToJsonElement(source.decodeToString()).jsonObject.getValue("savedWorkouts").jsonArray
            .map { it.jsonObject.getValue("name").jsonPrimitive.content }
        assertEquals(names.toSet(),actual.toSet())
    }

    @Test fun fractionalReferencesAndTimesRefuseAtomicallyEvenWhenTheTruncatedValueExists() = runBlocking {
        val bench = db.exerciseDao().byName("Bench Press")!!.id
        val session = db.sessionDao().insertSession(SessionEntity(presetId=null,label="",startedAtMs=1,completedAtMs=5,xpAwarded=0))
        db.sessionDao().insertSets(listOf(SetLogEntity(sessionId=session,exerciseId=bench,setIndex=0,reps=1,
            weightKg=null,modifiers="",done=true)))
        val preset = db.presetDao().insertPreset(PresetEntity(name="Synthetic",note="",scheduledDay=null))
        db.presetDao().insertEntries(listOf(PresetEntryEntity(presetId=preset,exerciseId=bench,targetSets=1,targetReps=1,
            targetWeightKg=null,position=0,modifiers="")))
        val routine = db.preparationDao().insertRoutine(PreparationRoutineEntity(kind="WARMUP",name="Warm-up",
            stepsJson=PreparationCodec.encodeSteps(listOf(PreparationStep(bench,"Bench Press",PreparationMeasure.REPS,2)))))
        sql.execSQL("INSERT INTO preset_preparations VALUES(?,'WARMUP','SAVED',?)",arrayOf(preset,routine))
        val cases = listOf(Triple("sessions","startedAtMs",1L),Triple("sessions","completedAtMs",5L),
            Triple("set_logs","exerciseId",bench),Triple("preset_entries","exerciseId",bench),
            Triple("preset_preparations","routineId",routine))
        val failures = mutableListOf<String>()
        for ((table,field,valid) in cases) {
            sql.execSQL("UPDATE $table SET $field=?",arrayOf(valid + 0.5))
            val before = count("local_connector_exports")
            val reason = (runCatching { files.exportContext() }.exceptionOrNull() as? LocalExportException)?.reason
            if (reason != LocalFailure.INVALID_HISTORY || count("local_connector_exports") != before ||
                count("local_connector_identity") != 0 || count("local_connector_objects") != 0) failures += "$table.$field"
            sql.execSQL("UPDATE $table SET $field=?",arrayOf(valid))
        }
        assertEquals(emptyList<String>(),failures)
    }

    private suspend fun finishedSession() = db.sessionDao().insertSession(
        SessionEntity(presetId=null, label="", startedAtMs=0, completedAtMs=1, xpAwarded=0))

    private suspend fun refusedWithoutExport(reason: LocalFailure) {
        val tables = listOf("local_connector_identity", "local_connector_objects", "local_connector_exports")
        val before = tables.map(::count)
        val failure = runCatching { files.exportContext() }.exceptionOrNull()
        assertEquals(reason, (failure as? LocalExportException)?.reason)
        assertEquals(before, tables.map(::count))
    }

    @Test fun deletedRoutineSourcesKeepOneKindAcrossSessions() = runBlocking {
        fun insert(id: Long, kind: String) {
            val snapshot = """{"kind":"$kind","name":"Frozen","sourceRoutineId":123456,"steps":[]}"""
            sql.execSQL("INSERT INTO session_preparations VALUES(?,?,?,?)",
                arrayOf<Any>(id, kind, snapshot, """{"status":"OPEN"}"""))
        }
        insert(finishedSession(), "WARMUP")
        insert(finishedSession(), "WARMUP")
        val ctx = Json.parseToJsonElement(files.exportContext().bytes.decodeToString()).jsonObject
        val sources = ctx.getValue("workouts").jsonArray.map {
            it.jsonObject.getValue("preparations").jsonArray.single().jsonObject.getValue("sourceRoutineId")
        }
        assertEquals(1, sources.distinct().size)
        insert(finishedSession(), "COOLDOWN")
        refusedWithoutExport(LocalFailure.INVALID_HISTORY)
    }

    @Test fun noteLimitIsCheckedBeforeReadingAnotherChunk() {
        val prefix = """[{"n\u006fte":""""
        var reads = 0
        val error = runCatching {
            readPublicPreparation {
                check(++reads == 1) { "Requested the tail of an oversized private note" }
                (prefix + "💪".repeat(501)).encodeToByteArray()
            }
        }.exceptionOrNull()
        assertTrue("Expected domain note limit, got $error", error is IllegalArgumentException)
        assertEquals(1, reads)
    }

    @Test fun storedStringsHaveTheSameSyntaxAndNoteLimitAcrossChunkThreshold() = runBlocking {

        val exercise = db.exerciseDao().byName("Bench Press")!!.id
        val routine = db.preparationDao().insertRoutine(PreparationRoutineEntity(kind="WARMUP",name="Warm",stepsJson="[]"))
        val preset = db.presetDao().insertPreset(PresetEntity(name="Sample",note="",scheduledDay=null))
        sql.execSQL("INSERT INTO preset_preparations VALUES(?,'WARMUP','SAVED',?)", arrayOf(preset,routine))
        for (padding in listOf("", " ".repeat(262144))) {
            fun put(name: String, note: String) {
                val steps = """[{"exerciseId":$exercise,"exerciseName":"$name","target":1,"measure":"REPS","note":"$note"}]"""
                sql.execSQL("UPDATE preparation_routines SET stepsJson=? WHERE id=?",arrayOf<Any>(steps+padding,routine))
            }
            put("Legal\\tname", "💪".repeat(500))
            files.exportContext()
            for ((name,note) in listOf("Bad\tname" to "", "Bad\\ud800" to "", "Lift" to "\\udfff",
                "Lift" to "💪".repeat(501))) {
                put(name,note)
                refusedWithoutExport(LocalFailure.INVALID_HISTORY)
            }
            sql.execSQL("""UPDATE preparation_routines SET stepsJson=CAST(
                CAST('[{"exerciseId":$exercise,"exerciseName":"' AS BLOB) || X'FF' ||
                '","target":1,"measure":"REPS"}]' || ? AS TEXT) WHERE id=?""", arrayOf<Any>(padding,routine))
            refusedWithoutExport(LocalFailure.INVALID_HISTORY)
        }
    }

    @Test fun oversizedReferencedMovementAndRoutineNamesReportFileLimit() = runBlocking {
        val bench = db.exerciseDao().byName("Bench Press")!!.id
        val routine = db.preparationDao().insertRoutine(PreparationRoutineEntity(kind="WARMUP",name="Warm",
            stepsJson=PreparationCodec.encodeSteps(listOf(PreparationStep(bench,"Bench Press",PreparationMeasure.REPS,2)))))
        sql.execSQL("UPDATE preparation_routines SET name=hex(zeroblob(4500000)) WHERE id=?",arrayOf(routine))
        files.exportContext() // Unreferenced names are outside the public file.
        val preset = db.presetDao().insertPreset(PresetEntity(name="Sample",note="",scheduledDay=null))
        sql.execSQL("INSERT INTO preset_preparations VALUES(?,'WARMUP','SAVED',?)",arrayOf(preset,routine))
        refusedWithoutExport(LocalFailure.FILE_LIMIT)
        sql.execSQL("UPDATE preparation_routines SET name='Warm' WHERE id=?",arrayOf(routine))
        sql.execSQL("UPDATE exercises SET name=hex(zeroblob(4500000)) WHERE id=?",arrayOf(bench))
        refusedWithoutExport(LocalFailure.FILE_LIMIT)
    }

    @Test fun unusedImportedNamesCannotBlockOrHideCanonicalAmbiguity() = runBlocking {
        addLargeExerciseCatalogue()
        val bench = db.exerciseDao().byName("Bench Press")!!.id
        val id = db.sessionDao().insertSession(SessionEntity(presetId=null,label="",startedAtMs=0,completedAtMs=1,xpAwarded=0))
        db.sessionDao().insertSets(listOf(SetLogEntity(sessionId=id,exerciseId=bench,setIndex=0,reps=1,weightKg=null,modifiers="",done=true)))
        val extra = db.exerciseDao().insertAll(listOf(ExerciseEntity(name="unused",muscleGroup="",isWeighted=false,custom=true))).single()
        sql.execSQL("UPDATE exercises SET name=hex(zeroblob(4500000)) WHERE id=?",arrayOf(extra))
        suspend fun key(): String {
            val c = Json.parseToJsonElement(files.exportContext().bytes.decodeToString()).jsonObject
            return c.getValue("movements").jsonArray.single().jsonObject.getValue("key").jsonPrimitive.content
        }
        assertEquals("bench press",key())
        // Kotlin trim includes non-breaking spaces. The huge padding belongs to the unused duplicate.
        sql.execSQL("UPDATE exercises SET name=replace(hex(zeroblob(4500000)),'0',' ') || ? WHERE id=?",
            arrayOf<Any>("\u00a0BeNcH PRESS\u00a0",extra))
        assertTrue(key().startsWith("local:"))
    }

    @Test fun oversizedPublicPreparationTokensRefuseEarlyAndRollBack() = runBlocking {
        // Keep a long token open indefinitely: a bounded reader must stop before asking for its tail.
        for (prefix in listOf("{\"name\":\"", "{\"")) {
            val failure = runCatching {
                readPublicPreparation { start ->
                    check(start <= MAX_CONTEXT_BYTES + 65536) { "read past the public byte ceiling" }
                    ByteArray(65536) { 'x'.code.toByte() }.also { if (start == 1) prefix.encodeToByteArray().copyInto(it) }
                }
            }.exceptionOrNull()
            assertEquals(LocalFailure.FILE_LIMIT,(failure as? LocalExportException)?.reason)
        }
        val session = db.sessionDao().insertSession(SessionEntity(presetId=null,label="",startedAtMs=0,completedAtMs=1,xpAwarded=0))
        sql.execSQL("""INSERT INTO session_preparations VALUES(?,'WARMUP',
            '{"kind":"WARMUP","name":"' || hex(zeroblob(4500000)) || '","steps":[]}','{"status":"OPEN"}')""",arrayOf(session))
        val failure = runCatching { files.exportContext() }.exceptionOrNull() as? LocalExportException
        assertEquals(LocalFailure.FILE_LIMIT,failure?.reason)
        assertEquals(0,count("local_connector_identity")); assertEquals(0,count("local_connector_objects"))
        assertEquals(0,count("local_connector_exports"))
    }

    @Test fun privatePreparationNotesDoNotConsumeThePublicFileBudget() = runBlocking {
        val bench = db.exerciseDao().byName("Bench Press")!!.id
        val step = PreparationStep(bench,"Bench Press",PreparationMeasure.REPS,5,note="private".repeat(142))
        val stepCount = 8500
        assertTrue(step.note.encodeToByteArray().size * stepCount > MAX_CONTEXT_BYTES)
        val steps = PreparationCodec.encodeSteps(List(stepCount) { step })
        val routine = db.preparationDao().insertRoutine(PreparationRoutineEntity(kind="WARMUP",name="Long routine",stepsJson=steps))
        val preset = db.presetDao().insertPreset(PresetEntity(name="Selected",note="",scheduledDay=null))
        sql.execSQL("INSERT INTO preset_preparations VALUES(?,'WARMUP','SAVED',?)",arrayOf(preset,routine))
        val source = files.exportContext().bytes.decodeToString()
        assertFalse(source.contains("private"))
        val exported = Json.parseToJsonElement(source).jsonObject.getValue("routines").jsonArray.single().jsonObject
        assertEquals(stepCount,exported.getValue("steps").jsonArray.size)
    }

    @Test fun missingLiveReferencesAndOversizedHistoryRollBackTheEntireExport() = runBlocking {
        val id = db.presetDao().insertPreset(PresetEntity(name="Selected",note="",scheduledDay=null))
        sql.execSQL("INSERT INTO preset_preparations VALUES(?,'WARMUP','SAVED',123)",arrayOf(id))
        suspend fun refused(reason: LocalFailure) {
            val failure = runCatching { files.exportContext() }.exceptionOrNull() as? LocalExportException
            assertEquals(reason,failure?.reason)
            assertEquals(0,count("local_connector_identity")); assertEquals(0,count("local_connector_exports"))
            assertEquals(0,count("local_connector_objects"))
        }
        refused(LocalFailure.INVALID_HISTORY)
        sql.execSQL("DELETE FROM preset_preparations")
        val entry = PresetEntryEntity(presetId=id,exerciseId=987654321,targetSets=1,targetReps=1,
            targetWeightKg=null,position=0,modifiers="")
        db.presetDao().insertEntries(listOf(entry))
        refused(LocalFailure.INVALID_HISTORY)
        sql.execSQL("DELETE FROM preset_entries")
        val movement = db.exerciseDao().byName("Bench Press")!!.id
        db.presetDao().insertEntries((0 until 1024).map { entry.copy(exerciseId=movement,position=it,modifiers="v".repeat(8192)) })
        refused(LocalFailure.FILE_LIMIT)
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
        addLargeExerciseCatalogue()
        assertEquals(LocalFailure.CATALOGUE_LIMIT,(files.saveDraft(review) as LocalSave.Rejected).reason)
        assertEquals(LocalFailure.CATALOGUE_LIMIT,(files.inspectDraft(bytes) as LocalInspection.Rejected).reason)
        assertEquals(0, files.exportContext().summary.coverage.workoutsIncluded)
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
