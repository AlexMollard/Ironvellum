package com.ironvellum.app.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.data.*
import com.ironvellum.app.domain.*
import com.ironvellum.app.ui.preparation.PreparationRunnerViewModel
import com.ironvellum.app.ui.preparation.warmupOffered
import com.ironvellum.app.ui.train.SealedSummary
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreparationResumeTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: IronvellumDatabase
    private lateinit var repo: Repository
    private val prefs get() = context.getSharedPreferences("preparation-resume-legacy", 0)
    private val warmups get() = WarmupStore(prefs)
    @Before fun setup() = runBlocking {
        SeededDb.fresh(context, DB)
        prefs.edit().clear().commit()
        db = IronvellumDatabase.create(context, DB); repo = Repository(db, warmupStore = warmups); repo.ensureSeeded()
    }
    @After fun close() { db.close(); context.deleteDatabase(DB); prefs.edit().clear().commit() }
    private suspend fun session(): Long {
        val e = repo.observeExercises().first().first { it.name == "Arm Circles" }
        val routine = repo.preparations.saveRoutine(PreparationRoutine(kind = PreparationKind.COOLDOWN, name = "Recovery", steps = listOf(
            PreparationStep(e.id, e.name, PreparationMeasure.SECONDS, 30, rounds = 2, repeat = PreparationRepeat.DIRECTIONS))))
        val preset = repo.savePreset(null, "Recovery test", "", null, emptyList(), mapOf(PreparationKind.COOLDOWN to PreparationChoice(PreparationMode.SAVED, routine)))
        return repo.startSessionFromPreset(preset)
    }
    @Test fun leavingWaitsForPersistedPauseAndDatabaseReopenRecoversWithoutTransientCompletion() = runBlocking {
        val id = session()
        val row = db.sessionDao().byId(id)!!
        db.sessionDao().updateSession(row.copy(startedAtMs = 1000, completedAtMs = 121000, xpAwarded = 123, strengthScore = 456))
        val profile = repo.observeProfile().first()
        val vm = withContext(Dispatchers.Main) { PreparationRunnerViewModel(repo, id, PreparationKind.COOLDOWN) { 16000L } }
        repo.preparations.act(id, PreparationKind.COOLDOWN, PreparationAction.START, 1000)
        val departed = CompletableDeferred<PreparationProgress>()
        withContext(Dispatchers.Main) { vm.leave { departed.complete(runBlocking { repo.preparations.observeSession(id, PreparationKind.COOLDOWN).first()!!.progress }) } }
        assertEquals(20000L, withTimeout(10000) { departed.await() }.remainingMs)
        assertEquals(PreparationStatus.PAUSED, departed.await().status)
        db.close(); db = IronvellumDatabase.create(context, DB); repo = Repository(db)
        val sealed = repo.observeSession(id).first()!!
        val summary = SealedSummary.from(sealed, repo.observeSessionSets(id).first(), repo.observeExercises().first())!!
        assertEquals(123, summary.session.xpAwarded); assertEquals(456, summary.session.strengthScore)
        assertEquals(2L, summary.durationMinutes)
        assertEquals(profile, repo.observeProfile().first())
        assertEquals(20000L, repo.preparations.observeSession(id, PreparationKind.COOLDOWN).first()!!.progress.remainingMs)
    }
    @Test fun queuedDuplicateExpiryAndSkipKeepOneTransitionInRoom() = runBlocking {
        val id = session()
        val before = db.sessionDao().setsFor(id)
        repo.preparations.act(id, PreparationKind.COOLDOWN, PreparationAction.START, 1000)
        coroutineScope { repeat(2) { launch { repo.preparations.act(id, PreparationKind.COOLDOWN, PreparationAction.TIMER_EXPIRED, 36000, "0:0:0") } } }
        assertEquals("0:0:1", repo.preparations.observeSession(id, PreparationKind.COOLDOWN).first()!!.progress.currentUnitKey)
        coroutineScope { repeat(2) { launch { repo.preparations.act(id, PreparationKind.COOLDOWN, PreparationAction.SKIP_UNIT, 1000, "0:0:1") } } }
        val progress = repo.preparations.observeSession(id, PreparationKind.COOLDOWN).first()!!.progress
        assertEquals("0:1:0", progress.currentUnitKey)
        assertEquals(setOf("0:0:1"), progress.skippedUnitKeys)
        assertEquals(before, db.sessionDao().setsFor(id))
    }
    @Test fun legacyChecklistImportsOnceAndChangingRoutineDoesNotReapplyOldDone() = runBlocking {
        val id = session()
        db.openHelper.writableDatabase.execSQL("DELETE FROM session_preparations WHERE sessionId = $id")
        warmups.setState(id, WarmupStep.State.DONE)
        repo.preparations.ensureSession(id)
        assertEquals(PreparationStatus.DONE, repo.preparations.observeSession(id, PreparationKind.WARMUP).first()!!.progress.status)
        repo.preparations.changeSession(id, PreparationKind.WARMUP, PreparationChoice())
        db.close(); db = IronvellumDatabase.create(context, DB); repo = Repository(db, warmupStore = warmups)
        repo.preparations.ensureSession(id)
        val restored = repo.preparations.observeSession(id, PreparationKind.WARMUP).first()!!
        assertEquals(PreparationProgress(), restored.progress)
    }
    @Test fun globalOffKeepsExplicitLegacySavedAndProgressButNoneHasNoGeneralSteps() = runBlocking {
        val e = repo.observeExercises().first().first { it.name == "Arm Circles" }
        val p = repo.savePreset(null, "Legacy", "", null, listOf(Repository.PresetDraftEntry(e.id, 1, 7, null, warmup = true)))
        val id = repo.startSessionFromPreset(p)
        val frozen = repo.preparations.observeSession(id, PreparationKind.WARMUP).first()!!
        assertTrue(frozen.snapshot.steps.size > 1)
        warmups.enabled = false
        val legacy = frozen.forWarmupSetting(repo.preparations.warmupEnabled)
        assertEquals(listOf(e.id), legacy.snapshot.steps.map { it.exerciseId })
        warmups.enabled = true
        assertEquals(frozen, frozen.forWarmupSetting(repo.preparations.warmupEnabled))
        assertEquals(frozen, repo.preparations.observeSession(id, PreparationKind.WARMUP).first())
        val savedId = repo.preparations.saveRoutine(PreparationRoutine(kind = PreparationKind.WARMUP, name = "Explicit", steps = listOf(
            PreparationStep(e.id, e.name, PreparationMeasure.REPS, 10))))
        repo.preparations.changeSession(id, PreparationKind.WARMUP, PreparationChoice(PreparationMode.SAVED, savedId))
        val saved = repo.preparations.observeSession(id, PreparationKind.WARMUP).first()!!
        assertEquals(saved, saved.forWarmupSetting(false))
        repo.preparations.changeSession(id, PreparationKind.WARMUP, PreparationChoice(PreparationMode.NONE))
        assertEquals(legacy.snapshot.steps, repo.preparations.observeSession(id, PreparationKind.WARMUP).first()!!.snapshot.steps)
        repo.preparations.changeSession(id, PreparationKind.WARMUP, PreparationChoice())
        repo.preparations.act(id, PreparationKind.WARMUP, PreparationAction.START, System.currentTimeMillis())
        val running = repo.preparations.observeSession(id, PreparationKind.WARMUP).first()!!
        assertEquals(running, running.forWarmupSetting(false))
    }
    @Test fun globalOffSuppressesOnlyUntouchedAutoWarmupAndKeepsCooldownIndependent() = runBlocking {
        val id = session()
        val warmup = repo.preparations.observeSession(id, PreparationKind.WARMUP).first()!!
        val cooldown = repo.preparations.observeSession(id, PreparationKind.COOLDOWN).first()!!
        assertTrue(warmup.snapshot.steps.isNotEmpty())
        warmups.enabled = false
        val projected = warmup.forWarmupSetting(repo.preparations.warmupEnabled)
        assertTrue(projected.snapshot.steps.isEmpty())
        assertFalse(warmupOffered(projected, false))
        assertEquals(warmup, repo.preparations.observeSession(id, PreparationKind.WARMUP).first())
        assertTrue(cooldown.snapshot.steps.isNotEmpty())
        assertEquals(cooldown, cooldown.forWarmupSetting(false))
    }
    @Test fun presetEditBeforeSessionScreenImportsDone() = firstWriter(0, WarmupStep.State.DONE)
    @Test fun presetDeleteBeforeSessionScreenImportsNonContiguousTicks() = firstWriter(1, WarmupStep.State.OPEN)
    @Test fun routineEditBeforeSessionScreenImportsSkipped() = firstWriter(2, WarmupStep.State.SKIPPED)
    @Test fun routineDeleteBeforeSessionScreenImportsNonContiguousTicks() = firstWriter(3, WarmupStep.State.OPEN)
    private fun firstWriter(mutation: Int, state: WarmupStep.State) = runBlocking {
        val exercises = repo.observeExercises().first().take(3)
        val routine = PreparationRoutine(kind = PreparationKind.WARMUP, name = "Original", steps = listOf(
            PreparationStep(exercises[0].id, exercises[0].name, PreparationMeasure.REPS, 7)))
        val routineId = repo.preparations.saveRoutine(routine)
        val entries = exercises.map { Repository.PresetDraftEntry(it.id, 1, 7, null, warmup = true) }
        val preset = repo.savePreset(null, "Original", "", null, entries,
            mapOf(PreparationKind.WARMUP to PreparationChoice(PreparationMode.SAVED, routineId)))
        val id = repo.startSessionFromPreset(preset)
        val snapshot = repo.preparations.observeSession(id, PreparationKind.WARMUP).first()!!.snapshot
        db.openHelper.writableDatabase.execSQL("DELETE FROM session_preparations WHERE sessionId = $id")
        warmups.setState(id, state)
        warmups.setTicked(id, setOf(exercises[0].id, exercises[2].id))
        when (mutation) {
            0 -> repo.savePreset(preset, "Edited", "", null, emptyList())
            1 -> repo.deletePreset(preset)
            2 -> repo.preparations.saveRoutine(routine.copy(id = routineId, name = "Edited", steps = routine.steps.map { it.copy(target = 12) }))
            else -> repo.preparations.deleteRoutine(routineId)
        }
        val first = repo.preparations.observeSession(id, PreparationKind.WARMUP).first()!!
        assertEquals(snapshot, first.snapshot)
        assertEquals(legacyPreparationProgress(snapshot, state, setOf(exercises[0].id, exercises[2].id)), first.progress)
        if (state == WarmupStep.State.OPEN) assertEquals(setOf("0:0:0", "2:0:0"), first.progress.completedUnitKeys)
        repo.preparations.ensureSession(id) // Later SessionScreen initialization must be inert.
        assertEquals(first, repo.preparations.observeSession(id, PreparationKind.WARMUP).first())
        repo.preparations.changeSession(id, PreparationKind.WARMUP, PreparationChoice())
        db.close(); db = IronvellumDatabase.create(context, DB); repo = Repository(db, warmupStore = warmups)
        repo.preparations.ensureSession(id)
        assertEquals(PreparationProgress(), repo.preparations.observeSession(id, PreparationKind.WARMUP).first()!!.progress)
    }
    @Test fun togglingDuringLegacyOnlyExecutionKeepsItsScopeAndRawSnapshot() = runBlocking {
        val e = repo.observeExercises().first().first { it.name == "Arm Circles" }
        val preset = repo.savePreset(null, "Legacy only", "", null, listOf(Repository.PresetDraftEntry(e.id, 2, 7, null, warmup = true)))
        val id = repo.startSessionFromPreset(preset)
        val frozen = repo.preparations.observeSession(id, PreparationKind.WARMUP).first()!!.snapshot
        warmups.enabled = false
        repo.preparations.act(id, PreparationKind.WARMUP, PreparationAction.START, 1000)
        repo.preparations.act(id, PreparationKind.WARMUP, PreparationAction.COMPLETE_UNIT, 1000, "0:0:0")
        warmups.enabled = true
        db.close(); db = IronvellumDatabase.create(context, DB); repo = Repository(db, warmupStore = warmups)
        val resumed = repo.preparations.observeSession(id, PreparationKind.WARMUP).first()!!
        assertEquals(frozen, resumed.snapshot)
        assertTrue(resumed.progress.legacyOnly)
        assertEquals(1, resumed.forWarmupSetting(true).snapshot.steps.size)
        assertEquals(setOf("0:0:0"), resumed.progress.completedUnitKeys)
        repo.preparations.act(id, PreparationKind.WARMUP, PreparationAction.SKIP_UNIT, 1000, "0:1:0")
        val done = repo.preparations.observeSession(id, PreparationKind.WARMUP).first()!!
        assertEquals(PreparationStatus.DONE, done.progress.status)
        assertEquals(frozen, done.snapshot)
        assertEquals(setOf("0:1:0"), done.progress.skippedUnitKeys)
    }
    @Test fun freeformAndDeletedWorkoutKeepTheirSnapshotAndStoredSummary() = runBlocking {
        val id = repo.startFreeformSession("Empty freeform")
        repo.preparations.ensureSession(id)
        val empty = repo.preparations.observeSession(id, PreparationKind.COOLDOWN).first()!!
        assertTrue(empty.snapshot.steps.isNotEmpty())
        assertTrue(repo.observeSessionSets(id).first().isEmpty())
        assertNull(SealedSummary.from(repo.observeSession(id).first()!!, emptyList(), emptyList()))
        repo.abandonSession(id)
        val planned = session()
        val row = db.sessionDao().byId(planned)!!
        val snapshot = repo.preparations.observeSession(planned, PreparationKind.COOLDOWN).first()!!.snapshot
        db.sessionDao().updateSession(row.copy(completedAtMs = row.startedAtMs + 120000, xpAwarded = 17))
        db.openHelper.writableDatabase.execSQL("DELETE FROM presets WHERE id = ${row.presetId}")
        repo.preparations.ensureSession(planned)
        assertEquals(snapshot, repo.preparations.observeSession(planned, PreparationKind.COOLDOWN).first()!!.snapshot)
        assertEquals(17, SealedSummary.from(repo.observeSession(planned).first()!!, emptyList(), emptyList())!!.session.xpAwarded)
    }
    @Test fun startingPresetBeforeScreenImportsLegacyDoneAndClaimsOriginalSession() = claimLegacy(WarmupStep.State.DONE, false)
    @Test fun startingFreeformBeforeScreenImportsLegacySkippedAndClaimsOriginalSession() = claimLegacy(WarmupStep.State.SKIPPED, true)
    @Test fun startingPresetBeforeScreenImportsLegacyTicksAndClaimsOriginalSession() = claimLegacy(WarmupStep.State.OPEN, false)
    private fun claimLegacy(state: WarmupStep.State, freeform: Boolean) = runBlocking {
        val exercises = repo.observeExercises().first().take(3)
        val preset = repo.savePreset(null, "Upgraded warmup", "", null,
            exercises.map { Repository.PresetDraftEntry(it.id, 1, 5, null, warmup = true) })
        val id = repo.startSessionFromPreset(preset)
        val frozen = repo.preparations.observeSession(id, PreparationKind.WARMUP).first()!!.snapshot
        db.openHelper.writableDatabase.execSQL("DELETE FROM session_preparations WHERE sessionId = $id")
        warmups.setState(id, state)
        warmups.setTicked(id, setOf(exercises[0].id, exercises[2].id))
        val claimed = if (freeform) repo.startFreeformSession("New freeform") else repo.startSessionFromPreset(preset)
        assertEquals("legacy preparation is real progress before SessionViewModel exists", id, claimed)
        val imported = repo.preparations.observeSession(id, PreparationKind.WARMUP).first()!!
        assertEquals(frozen, imported.snapshot)
        assertEquals(legacyPreparationProgress(frozen, state, setOf(exercises[0].id, exercises[2].id)), imported.progress)
        assertTrue(repo.observeSessionSets(id).first().none { it.done })
        // Deliberately reset progress remains empty despite the stale legacy answers.
        repo.preparations.changeSession(id, PreparationKind.WARMUP, PreparationChoice())
        val replacement = repo.startFreeformSession("After deliberate reset")
        assertNotEquals(id, replacement)
        assertNull(repo.observeSession(id).first())
    }
    @Test fun startingBeforeScreenStillDiscardsTrulyUntouchedLegacySession() = runBlocking {
        val id = repo.startFreeformSession("Untouched")
        db.openHelper.writableDatabase.execSQL("DELETE FROM session_preparations WHERE sessionId = $id")
        val next = repo.startFreeformSession("Replacement")
        assertNotEquals(id, next)
        assertNull(repo.observeSession(id).first())
        repo.preparations.ensureSession(next)
        assertEquals(PreparationProgress(), repo.preparations.observeSession(next, PreparationKind.WARMUP).first()!!.progress)
    }
    @Test fun untouchedNoneChoiceIsStillDisposableAfterMissingRowImport() = runBlocking {
        val exercise = repo.observeExercises().first().first()
        val preset = repo.savePreset(null, "None", "", null, listOf(Repository.PresetDraftEntry(exercise.id, 1, 5, null)),
            PreparationKind.entries.associateWith { PreparationChoice(PreparationMode.NONE) })
        val id = repo.startSessionFromPreset(preset)
        db.openHelper.writableDatabase.execSQL("DELETE FROM session_preparations WHERE sessionId = $id")
        assertNotEquals(id, repo.startSessionFromPreset(preset))
        assertNull(repo.observeSession(id).first())
    }
    companion object { private const val DB = "preparation-resume.db" }
}
