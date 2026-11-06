package com.ironvellum.app.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.data.*
import com.ironvellum.app.domain.*
import com.ironvellum.app.ui.preparation.PreparationRunnerViewModel
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
    @Before fun setup() = runBlocking {
        SeededDb.fresh(context, DB)
        db = IronvellumDatabase.create(context, DB); repo = Repository(db); repo.ensureSeeded()
    }
    @After fun close() { db.close(); context.deleteDatabase(DB) }
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
    @Test fun queuedDuplicateCompleteAndSkipKeepOneTransitionInRoom() = runBlocking {
        val id = session()
        val before = db.sessionDao().setsFor(id)
        coroutineScope { repeat(2) { launch { repo.preparations.act(id, PreparationKind.COOLDOWN, PreparationAction.COMPLETE_UNIT, 1000, "0:0:0") } } }
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
        repo.preparations.prepareWarmupOffer(id, true, WarmupStep.State.DONE, emptySet())
        assertEquals(PreparationStatus.DONE, repo.preparations.observeSession(id, PreparationKind.WARMUP).first()!!.progress.status)
        repo.preparations.changeSession(id, PreparationKind.WARMUP, PreparationChoice())
        db.close(); db = IronvellumDatabase.create(context, DB); repo = Repository(db)
        repo.preparations.prepareWarmupOffer(id, true, WarmupStep.State.DONE, emptySet())
        val restored = repo.preparations.observeSession(id, PreparationKind.WARMUP).first()!!
        assertEquals(PreparationProgress(), restored.progress)
    }
    @Test fun globalOffKeepsExplicitLegacySavedAndProgressButNoneHasNoGeneralSteps() = runBlocking {
        val e = repo.observeExercises().first().first { it.name == "Arm Circles" }
        val p = repo.savePreset(null, "Legacy", "", null, listOf(Repository.PresetDraftEntry(e.id, 1, 7, null, warmup = true)))
        val id = repo.startSessionFromPreset(p)
        repo.preparations.prepareWarmupOffer(id, false, WarmupStep.State.OPEN, emptySet())
        val legacy = repo.preparations.observeSession(id, PreparationKind.WARMUP).first()!!
        assertEquals(listOf(e.id), legacy.snapshot.steps.map { it.exerciseId })
        repo.preparations.changeSession(id, PreparationKind.WARMUP, PreparationChoice(PreparationMode.NONE))
        assertEquals(legacy.snapshot.steps, repo.preparations.observeSession(id, PreparationKind.WARMUP).first()!!.snapshot.steps)
        repo.preparations.changeSession(id, PreparationKind.WARMUP, PreparationChoice())
        repo.preparations.act(id, PreparationKind.WARMUP, PreparationAction.START, System.currentTimeMillis())
        val running = repo.preparations.observeSession(id, PreparationKind.WARMUP).first()!!
        repo.preparations.prepareWarmupOffer(id, false, WarmupStep.State.OPEN, emptySet())
        assertEquals(running.snapshot, repo.preparations.observeSession(id, PreparationKind.WARMUP).first()!!.snapshot)
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
    companion object { private const val DB = "preparation-resume.db" }
}
