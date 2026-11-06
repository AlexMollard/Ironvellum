package com.ironvellum.app.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.room.testing.MigrationTestHelper
import com.ironvellum.app.data.db.SessionEntity
import com.ironvellum.app.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreparationPersistenceTest {
    private lateinit var context: Context
    private lateinit var db: IronvellumDatabase
    private lateinit var repo: Repository
    @Before fun setup() = runTest {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        SeededDb.fresh(context, "preparation-test")
        db = IronvellumDatabase.create(context, "preparation-test")
        repo = Repository(db)
        repo.ensureSeeded()
    }
    @After fun close() { db.close(); context.deleteDatabase("preparation-test") }
    private suspend fun exercise(name: String) = repo.observeExercises().first().first { it.name == name }
    private suspend fun routine(): Long {
        val e = exercise("Arm Circles")
        return repo.preparations.saveRoutine(PreparationRoutine(kind = PreparationKind.WARMUP, name = "Personal", steps = listOf(
            PreparationStep(e.id, e.name, PreparationMeasure.SECONDS, 30, repeat = PreparationRepeat.DIRECTIONS))))
    }
    private suspend fun preset(routineId: Long? = null): Long {
        val e = exercise("Bodyweight Squat")
        return repo.savePreset(null, "Workout", "", null, listOf(Repository.PresetDraftEntry(e.id, 2, 10, null)),
            routineId?.let { mapOf(PreparationKind.WARMUP to PreparationChoice(PreparationMode.SAVED, it)) })
    }
    @Test fun upgradeLeavesPersonalLibraryEmptyAndNullSavePreservesAttachments() = runTest {
        assertTrue(repo.preparations.observeRoutines(PreparationKind.WARMUP).first().isEmpty())
        val id = routine(); val preset = preset(id)
        repo.savePreset(preset, "Updated", "", null, emptyList())
        assertEquals(id, repo.preparations.choices(preset)[PreparationKind.WARMUP]?.routineId)
        repo.savePreset(preset, "Auto", "", null, emptyList(), emptyMap())
        assertEquals(PreparationChoice(), repo.preparations.choices(preset)[PreparationKind.WARMUP])
    }
    @Test fun editingAndDeletingRoutineOrPresetCannotChangeSnapshots() = runTest {
        val id = routine(); val p = preset(id); val session = repo.startSessionFromPreset(p)
        val before = repo.preparations.observeSession(session, PreparationKind.WARMUP).first()!!
        repo.preparations.saveRoutine(PreparationRoutine(id, PreparationKind.WARMUP, "Changed", before.snapshot.steps.map { it.copy(target = 60) }))
        repo.preparations.deleteRoutine(id)
        assertEquals(PreparationMode.AUTO, repo.preparations.choices(p)[PreparationKind.WARMUP]?.mode)
        db.presetDao().deletePreset(p)
        assertEquals(before, repo.preparations.observeSession(session, PreparationKind.WARMUP).first())
    }
    @Test fun duplicateCapturedUnitKeysAdvanceOnceAndLiveReuseRetainsProgressWithoutScoringWrites() = runTest {
        val p = preset(routine()); val session = repo.startSessionFromPreset(p)
        val count = db.sessionDao().setsFor(session).size
        val profile = db.profileDao().get()
        repo.preparations.act(session, PreparationKind.WARMUP, PreparationAction.COMPLETE_UNIT, 0)
        assertEquals("0:0:0", repo.preparations.observeSession(session, PreparationKind.WARMUP).first()!!.progress.currentUnitKey)
        repeat(2) { repo.preparations.act(session, PreparationKind.WARMUP, PreparationAction.COMPLETE_UNIT, 0, "0:0:0") }
        assertEquals(session, repo.startSessionFromPreset(p))
        assertEquals("0:0:1", repo.preparations.observeSession(session, PreparationKind.WARMUP).first()!!.progress.currentUnitKey)
        assertEquals(count, db.sessionDao().setsFor(session).size)
        assertEquals(profile, db.profileDao().get())
    }
    @Test fun legacyWarmupWinsDuplicateTargetAndNoneKeepsExplicitLegacyWarmups() = runTest {
        val e = exercise("Arm Circles"); val work = exercise("Bodyweight Squat")
        val p = repo.savePreset(null, "Legacy", "", null, listOf(
            Repository.PresetDraftEntry(e.id, 2, 17, 3.0, warmup = true), Repository.PresetDraftEntry(work.id, 1, 10, null)),
            mapOf(PreparationKind.WARMUP to PreparationChoice(PreparationMode.SAVED, routine())))
        val session = repo.startSessionFromPreset(p)
        val steps = repo.preparations.observeSession(session, PreparationKind.WARMUP).first()!!.snapshot.steps
        assertEquals(1, steps.size); assertEquals(1020, steps[0].target); assertEquals(2, steps[0].rounds)
        assertEquals(17, steps[0].legacyTarget!!.targetReps)
        assertEquals(3.0, steps[0].legacyTarget!!.targetWeightKg!!, 0.0)
        assertNull(steps[0].weightKg) // Arm Circles uses minutes; its unused legacy column is not a preparation load.
        repo.deletePreset(p)
        repo.preparations.changeSession(session, PreparationKind.WARMUP, PreparationChoice(PreparationMode.NONE))
        assertEquals(steps, repo.preparations.observeSession(session, PreparationKind.WARMUP).first()!!.snapshot.steps)
    }
    @Test fun legacyOngoingSessionSnapshotsOnlyOnceAndMalformedProgressPausesSafely() = runTest {
        val p = preset(routine())
        val session = db.sessionDao().insertSession(SessionEntity(presetId = p, label = "old", startedAtMs = 1, completedAtMs = null, xpAwarded = 0))
        repo.preparations.ensureSession(session)
        val before = repo.preparations.observeSession(session, PreparationKind.WARMUP).first()!!
        repo.preparations.setChoices(p, mapOf(PreparationKind.WARMUP to PreparationChoice(PreparationMode.NONE)))
        repo.preparations.ensureSession(session)
        assertEquals(before, repo.preparations.observeSession(session, PreparationKind.WARMUP).first())
        val row = db.preparationDao().session(session, PreparationKind.WARMUP.name)!!
        db.preparationDao().putSession(row.copy(progressJson = "broken"))
        val recovered = repo.preparations.observeSession(session, PreparationKind.WARMUP).first()!!
        assertEquals(before.snapshot, recovered.snapshot)
        assertEquals(PreparationStatus.PAUSED, recovered.progress.status)
    }

    @Test fun migration46To47PreservesPresetBothSetsAndPrivateProfileFields() {
        val name = "preparation-migration-test"
        val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), IronvellumDatabase::class.java)
        try {
            helper.createDatabase(name, 46).use { old ->
                old.execSQL("INSERT INTO presets(id,name,note,scheduledDay) VALUES(7,'Kept','note',3)")
                old.execSQL("INSERT INTO preset_entries(id,presetId,exerciseId,targetSets,targetReps,targetWeightKg,modifiers,position,supersetGroup,setScheme,warmup) VALUES(9,7,1,2,11,42.5,'',0,NULL,NULL,0)")
                old.execSQL("INSERT INTO sessions(id,presetId,label,startedAtMs,completedAtMs,xpAwarded,strengthScore,title,note,privateNote,imported,audience) VALUES(5,7,'Kept',100,200,19,23,'title','public','private survives',0,'private')")
                repeat(2) { index -> old.execSQL("INSERT INTO set_logs(id,sessionId,exerciseId,exercisePosition,setIndex,reps,weightKg,modifiers,done,kind) VALUES(${index + 1},5,1,0,$index,${11 + index},42.5,'',1,'WORKING')") }
                old.execSQL("INSERT INTO profile(id,name,totalXp,currentTitleId,lifetimeStrength,trainingMode,heightCm,sex,inkStyle,scoringVersion) VALUES(1,'Owner',123,NULL,44,'STRENGTH',178.5,'FEMALE',1,0)")
            }
            helper.runMigrationsAndValidate(name, 47, true, *IronvellumDatabase.MIGRATIONS).use { migrated ->
                migrated.query("SELECT COUNT(*),SUM(reps),SUM(weightKg) FROM set_logs").use { c -> c.moveToFirst(); assertEquals(2, c.getInt(0)); assertEquals(23, c.getInt(1)); assertEquals(85.0, c.getDouble(2), 0.0) }
                migrated.query("SELECT name,note,scheduledDay FROM presets WHERE id=7").use { c -> c.moveToFirst(); assertEquals("Kept", c.getString(0)); assertEquals("note", c.getString(1)); assertEquals(3, c.getInt(2)) }
                migrated.query("SELECT privateNote,xpAwarded FROM sessions WHERE id=5").use { c -> c.moveToFirst(); assertEquals("private survives", c.getString(0)); assertEquals(19, c.getInt(1)) }
                migrated.query("SELECT totalXp,heightCm,sex FROM profile").use { c -> c.moveToFirst(); assertEquals(123, c.getInt(0)); assertEquals(178.5, c.getDouble(1), 0.0); assertEquals("FEMALE", c.getString(2)) }
                migrated.query("SELECT COUNT(*) FROM preparation_routines").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
            }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun archiveRemapsAllIdentitiesAndKeepsUnusedCustomRoutinesAndSealedProgress() = runTest {
        val customId = db.exerciseDao().insertAll(listOf(com.ironvellum.app.data.db.ExerciseEntity(name = "Prep custom", muscleGroup = "CORE", isWeighted = false, custom = true))).single()
        val routineId = repo.preparations.saveRoutine(PreparationRoutine(kind = PreparationKind.COOLDOWN, name = "Custom only", steps = listOf(PreparationStep(customId, "Prep custom", PreparationMeasure.SECONDS, 35))))
        repo.preparations.saveRoutine(PreparationRoutine(kind = PreparationKind.WARMUP, name = "Unused custom", steps = listOf(PreparationStep(customId, "Prep custom", PreparationMeasure.REPS, 7))))
        val p = preset(routineId = null)
        repo.preparations.setChoices(p, mapOf(PreparationKind.COOLDOWN to PreparationChoice(PreparationMode.SAVED, routineId)))
        val session = repo.startSessionFromPreset(p)
        val row = db.sessionDao().byId(session)!!
        db.sessionDao().updateSession(row.copy(completedAtMs = 123, xpAwarded = 17))
        repo.preparations.act(session, PreparationKind.COOLDOWN, PreparationAction.START, 1000)
        repo.preparations.act(session, PreparationKind.COOLDOWN, PreparationAction.PAUSE, 16000)
        val json = repo.exportJson()
        val restored = repo.importArchive(json).getOrThrow()
        assertTrue(restored.problems.isEmpty())
        val newPreset = repo.observePresets().first().single { it.name == "Workout" }
        val newRoutine = repo.preparations.observeRoutines(PreparationKind.COOLDOWN).first().single()
        assertEquals("Unused custom", repo.preparations.observeRoutines(PreparationKind.WARMUP).first().single().name)
        val newSession = db.sessionDao().completedSessions().single()
        assertNotEquals(p, newPreset.id); assertNotEquals(routineId, newRoutine.id); assertNotEquals(session, newSession.id)
        assertEquals(newRoutine.id, newPreset.preparation[PreparationKind.COOLDOWN]?.routineId)
        assertEquals("Prep custom", db.exerciseDao().byId(newRoutine.steps.single().exerciseId)?.name)
        val preparation = repo.preparations.observeSession(newSession.id, PreparationKind.COOLDOWN).first()!!
        assertEquals(newRoutine.id, preparation.snapshot.sourceRoutineId)
        assertEquals(25000L, preparation.progress.remainingMs)
        assertEquals(PreparationStatus.PAUSED, preparation.progress.status)
        assertTrue(repo.customExerciseUsage(customId) >= 2)
        assertFalse(repo.deleteCustomExercise(customId))
    }

    @Test fun loadedTwinConversionRemapsPersonalRoutinesAndSessionSnapshots() = runTest {
        val e = exercise("Pull-up")
        val id = repo.preparations.saveRoutine(PreparationRoutine(kind = PreparationKind.WARMUP, name = "Loaded preparation", steps = listOf(
            PreparationStep(e.id, e.name, PreparationMeasure.REPS, 5, weightKg = 10.0))))
        val p = preset(id); val session = repo.startSessionFromPreset(p)
        repo.convertTwins(backup = {})
        val twin = exercise("Weighted Pull-up")
        assertEquals(twin.id, repo.preparations.observeRoutines(PreparationKind.WARMUP).first().single().steps.single().exerciseId)
        assertEquals(twin.id, repo.preparations.observeSession(session, PreparationKind.WARMUP).first()!!.snapshot.steps.single().exerciseId)
        assertEquals(0, repo.preparations.exerciseUsage(e.id))
    }

    @Test fun archiveNumericExerciseIdCollisionResolvesByNameAndMalformedRoutineFallsBackToAuto() = runTest {
        val unrelated = routine()
        val squat = exercise("Bodyweight Squat")
        val arm = exercise("Arm Circles")
        val imported = PreparationRoutine(unrelated, PreparationKind.WARMUP, "Imported", listOf(PreparationStep(squat.id, arm.name, PreparationMeasure.SECONDS, 30)))
        fun archive(routine: PreparationRoutine) = ExportWriter.write(PlayerProfile(), TrainingMode.STRENGTH,
            listOf(WorkoutPreset(9, "Imported workout", entries = listOf(PresetEntry(exerciseId = arm.id, exerciseName = squat.name, targetSets = 2, targetReps = 11)),
                preparation = mapOf(PreparationKind.WARMUP to PreparationChoice(PreparationMode.SAVED, unrelated)))),
            emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), exportedAtMs = 1, preparationRoutines = listOf(routine))
        assertTrue(repo.importArchive(archive(imported)).getOrThrow().problems.isEmpty())
        val saved = repo.preparations.observeRoutines(PreparationKind.WARMUP).first().single()
        assertEquals(arm.id, saved.steps.single().exerciseId)
        assertEquals(squat.id, repo.observePresets().first().single().entries.single().exerciseId)
        val result = repo.importArchive(archive(imported.copy(steps = listOf(imported.steps.single().copy(exerciseName = ""))))).getOrThrow()
        assertTrue(result.problems.isNotEmpty())
        assertEquals(1, result.presets)
        assertTrue(repo.preparations.observeRoutines(PreparationKind.WARMUP).first().isEmpty())
        assertEquals(PreparationMode.AUTO, repo.observePresets().first().single().preparation[PreparationKind.WARMUP]?.mode)
    }

    @Test fun sessionProgressSurvivesDatabaseReopenAndActiveSessionIsExcludedFromArchive() = runTest {
        val session = repo.startSessionFromPreset(preset(routine()))
        repo.preparations.act(session, PreparationKind.WARMUP, PreparationAction.START, 1000)
        repo.preparations.act(session, PreparationKind.WARMUP, PreparationAction.PAUSE, 16000)
        val before = repo.preparations.observeSession(session, PreparationKind.WARMUP).first()!!
        val archive = ExportReader.read(repo.exportJson()).getOrThrow()
        assertTrue(archive.sessions.isEmpty()); assertTrue(archive.sessionPreparations.isEmpty())
        db.close(); db = IronvellumDatabase.create(context, "preparation-test"); repo = Repository(db)
        assertEquals(before, repo.preparations.observeSession(session, PreparationKind.WARMUP).first())
        assertEquals(20000L, before.progress.remainingMs)
        assertEquals(PreparationStatus.PAUSED, before.progress.status)
    }

    @Test fun legacyActivityTargetsUseTheirOriginalUnitsAndOnlyTimeMetricsStartTimers() = runTest {
        val metrics = listOf(ExerciseMetric.DURATION, ExerciseMetric.HOLD, ExerciseMetric.ATTEMPTS_GRADE, ExerciseMetric.DISTANCE_TIME)
        val ids = db.exerciseDao().insertAll(metrics.map { com.ironvellum.app.data.db.ExerciseEntity(name = "Legacy ${it.name}", muscleGroup = "CARDIO", isWeighted = false, metric = it.name, category = "Cardio") })
        val targets = listOf(5, 45, 7, 0)
        val entries = ids.mapIndexed { i, id -> Repository.PresetDraftEntry(id, 1, targets[i], if (i == 3) 3.5 else null, warmup = true) }
        val p = repo.savePreset(null, "Legacy metrics", "", null, entries, mapOf(PreparationKind.WARMUP to PreparationChoice(PreparationMode.NONE)))
        val session = repo.startSessionFromPreset(p)
        val snapshot = repo.preparations.observeSession(session, PreparationKind.WARMUP).first()!!.snapshot
        assertEquals(listOf(300, 45, 7, 1), snapshot.steps.map { it.target })
        assertEquals(listOf(PreparationMeasure.SECONDS, PreparationMeasure.SECONDS, PreparationMeasure.REPS, PreparationMeasure.REPS), snapshot.steps.map { it.measure })
        assertEquals(metrics, snapshot.steps.map { it.legacyTarget?.metric })
        assertEquals(targets, snapshot.steps.map { it.legacyTarget?.targetReps })
        assertEquals(3.5, snapshot.steps.last().legacyTarget!!.targetWeightKg!!, 0.0)
        assertNull(snapshot.steps.last().weightKg)
        snapshot.steps.forEachIndexed { i, step ->
            val original = WarmupStep.Item(ids[i], step.exerciseName, metrics[i], 1, targets[i], if (i == 3) 3.5 else null)
            assertEquals(original.line { it.toString() }, step.legacyTargetLine { it.toString() })
        }
        repo.preparations.act(session, PreparationKind.WARMUP, PreparationAction.START, 1000)
        val persisted = repo.preparations.observeSession(session, PreparationKind.WARMUP).first()!!.progress
        assertEquals(300000L, persisted.remainingMs)
        assertEquals(306000L, persisted.deadlineMs)
        var progress = PreparationProgress().advance(snapshot, PreparationAction.START, 1000)
        assertEquals(300000L, progress.remainingMs(1000))
        progress = progress.advance(snapshot, PreparationAction.COMPLETE_UNIT, 1000, progress.currentUnitKey)
        progress = progress.advance(snapshot, PreparationAction.START, 1000)
        assertEquals(45000L, progress.remainingMs(1000))
        progress = progress.advance(snapshot, PreparationAction.COMPLETE_UNIT, 1000, progress.currentUnitKey)
        progress = progress.advance(snapshot, PreparationAction.START, 1000)
        assertNull(progress.deadlineMs)
        progress = progress.advance(snapshot, PreparationAction.COMPLETE_UNIT, 1000, progress.currentUnitKey)
        assertNull(progress.advance(snapshot, PreparationAction.START, 1000).deadlineMs)
        db.sessionDao().updateSession(db.sessionDao().byId(session)!!.copy(completedAtMs = 2))
        assertTrue(repo.importArchive(repo.exportJson()).getOrThrow().problems.isEmpty())
        val restored = repo.preparations.observeSession(db.sessionDao().completedSessions().single().id, PreparationKind.WARMUP).first()!!.snapshot
        assertEquals(snapshot, restored)
    }

    @Test fun thirtyLegacyRoundsSurviveStoredSnapshotAndSealedArchive() = runTest {
        val e = exercise("Arm Circles")
        val p = repo.savePreset(null, "Thirty legacy rounds", "", null, listOf(Repository.PresetDraftEntry(e.id, 30, 17, null, warmup = true)),
            mapOf(PreparationKind.WARMUP to PreparationChoice(PreparationMode.NONE)))
        val session = repo.startSessionFromPreset(p)
        val before = repo.preparations.observeSession(session, PreparationKind.WARMUP).first()!!
        assertEquals(30, before.snapshot.steps.single().rounds)
        assertFalse(before.snapshot.steps.single().validForEditor())
        repo.preparations.act(session, PreparationKind.WARMUP, PreparationAction.COMPLETE_UNIT, 1000, "0:0:0")
        db.sessionDao().updateSession(db.sessionDao().byId(session)!!.copy(completedAtMs = 2))
        assertTrue(repo.importArchive(repo.exportJson()).getOrThrow().problems.isEmpty())
        val restoredId = db.sessionDao().completedSessions().single().id
        val restored = repo.preparations.observeSession(restoredId, PreparationKind.WARMUP).first()!!
        assertEquals(30, restored.snapshot.steps.single().rounds)
        assertEquals("0:1:0", restored.progress.currentUnitKey)
    }

    @Test fun mixedValidMalformedDuplicateArchivedRoutineIdsFallBackToAutoWithProblem() = runTest {
        val e = exercise("Arm Circles")
        val routine = PreparationRoutine(7, PreparationKind.WARMUP, "Valid duplicate", listOf(PreparationStep(e.id, e.name, PreparationMeasure.SECONDS, 30)))
        val json = ExportWriter.write(PlayerProfile(), TrainingMode.STRENGTH,
            listOf(WorkoutPreset(3, "Affected", preparation = mapOf(PreparationKind.WARMUP to PreparationChoice(PreparationMode.SAVED, 7)))),
            emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), exportedAtMs = 1, preparationRoutines = listOf(routine))
            .replace("\"preparationRoutines\":[", "\"preparationRoutines\":[{\"id\":7,\"kind\":\"WARMUP\",\"name\":\"Malformed duplicate\",\"steps\":[]},")
        val result = repo.importArchive(json).getOrThrow()
        assertTrue(result.problems.isNotEmpty())
        val preset = repo.observePresets().first().single()
        assertEquals(PreparationChoice(), repo.preparations.choices(preset.id)[PreparationKind.WARMUP])
        assertTrue(repo.preparations.observeRoutines(PreparationKind.WARMUP).first().isEmpty())
    }

    @Test fun pausedRemainingTimeIsClampedToTheCurrentStepBeforeDisplayOrExport() = runTest {
        val session = repo.startSessionFromPreset(preset(routine()))
        val row = db.preparationDao().session(session, PreparationKind.WARMUP.name)!!
        db.preparationDao().putSession(row.copy(progressJson = PreparationCodec.encodeProgress(PreparationProgress(status = PreparationStatus.PAUSED, remainingMs = 9999999))))
        val progress = repo.preparations.observeSession(session, PreparationKind.WARMUP).first()!!.progress
        assertEquals(30000L, progress.remainingMs(1000))
    }
}
