package com.ironvellum.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.data.db.SessionEntity
import com.ironvellum.app.domain.Progression
import com.ironvellum.app.domain.ProgressionSuggestion
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The four invariants the daily loop rests on. Each one is a bug that shipped:
 *
 *  - one live session at a time (a second start stranded the first forever),
 *  - progression reads real history (the deload could never fire),
 *  - the idle clock actually starts (a fresh account accrued nothing, ever),
 *  - session order is the user's, and can be changed.
 */
@RunWith(AndroidJUnit4::class)
class DailyLoopInvariantsTest {

    private lateinit var db: IronvellumDatabase
    private lateinit var repo: Repository

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        SeededDb.fresh(context, TEST_DB)
        db = IronvellumDatabase.create(context, TEST_DB)
        repo = Repository(db)
        repo.ensureSeeded()
    }

    @After
    fun tearDown() {
        db.close()
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(TEST_DB)
    }

    private suspend fun repsExerciseIds(count: Int): List<Long> =
        db.exerciseDao().observeAll().first()
            .filter { it.metric == "REPS" }
            .take(count)
            .map { it.id }

    private suspend fun savePreset(name: String, exerciseIds: List<Long>): Long =
        repo.savePreset(
            presetId = null,
            name = name,
            note = "",
            scheduledDay = null,
            entries = exerciseIds.map { id ->
                Repository.PresetDraftEntry(
                    exerciseId = id,
                    targetSets = 3,
                    targetReps = 5,
                    targetWeightKg = 40.0,
                )
            },
        )

    private suspend fun liveSessionIds(): List<Long> =
        db.sessionDao().observeRecent(50).first().filter { it.completedAtMs == null }.map { it.id }

    private suspend fun exerciseOrder(sessionId: Long): List<Long> =
        repo.observeSessionSets(sessionId).first().map { it.exerciseId }.distinct()

    @Test
    fun startingASecondSessionNeverStrandsALiveOne() = runBlocking {
        val ids = repsExerciseIds(2)
        val heavy = savePreset("Heavy Pull", ids)
        val volume = savePreset("Volume Pull", ids)

        // An accidental start with nothing ticked is discarded, not stranded.
        val abandoned = repo.startSessionFromPreset(heavy)
        val fresh = repo.startSessionFromPreset(volume)
        assertNotEquals("an empty live session must not be reused", abandoned, fresh)
        assertNull("the empty session must be deleted, not left live", db.sessionDao().byId(abandoned))
        assertEquals(listOf(fresh), liveSessionIds())

        // Once real work is logged the live session wins: starting another
        // preset lands back in it instead of minting a rival that can never
        // complete and never pays XP.
        val set = db.sessionDao().setsFor(fresh).first()
        repo.updateSet(set.id, reps = 5, weightKg = 40.0, done = true)
        assertEquals("a live session with logged work must be returned", fresh, repo.startSessionFromPreset(heavy))
        assertEquals("only ever one live session", listOf(fresh), liveSessionIds())
    }

    @Test
    fun repeatedStallsDeloadTheNextSession() = runBlocking {
        val exerciseId = repsExerciseIds(1).first()
        val preset = repo.savePreset(
            null, "Stall Pull", "", null,
            listOf(Repository.PresetDraftEntry(exerciseId, targetSets = 3, targetReps = 5, targetWeightKg = 100.0)),
        )
        val stalls = Progression.STALLS_BEFORE_DELOAD

        // Completed sessions at the same load, none clearing the 5-rep target.
        // The session start only ever read the LATEST session, so the stall
        // count stayed 0 and the lifter was told to repeat 100 kg forever.
        repeat(stalls) { i ->
            val id = db.sessionDao().insertSession(
                SessionEntity(
                    presetId = preset,
                    label = "Stall Pull",
                    startedAtMs = System.currentTimeMillis() - (stalls - i) * 86_400_000L,
                    completedAtMs = null,
                    xpAwarded = 0,
                ),
            )
            repo.addExtraSet(id, exerciseId, reps = 2, weightKg = 100.0, modifiers = "")
            val set = db.sessionDao().setsFor(id).first()
            repo.updateSet(set.id, reps = 2, weightKg = 100.0, done = true)
            repo.completeSession(id)
        }

        // The workout starts on what the routine writes; the deload is offered, not applied.
        val next = repo.startSessionFromPreset(preset)
        assertEquals(listOf(100.0, 100.0, 100.0), db.sessionDao().setsFor(next).map { it.weightKg })
        val suggestion = repo.trialSuggestions(next).values.single()
        assertEquals(ProgressionSuggestion.Kind.DELOAD, suggestion.kind)
        assertTrue(
            "after $stalls stalled sessions the load must come off, not repeat at 100 kg (got ${suggestion.weightKg})",
            suggestion.weightKg != null && suggestion.weightKg!! < 100.0,
        )
    }

    @Test
    fun theIdleClockStartsBeforeTheVeilIsOpened() = runBlocking {
        val snapshot = repo.observeIdleSnapshot().first()
        assertTrue("startup must start the home preview's clock", snapshot.state.lastCollectedAtMs > 0L)
        assertEquals(0L, snapshot.state.essence)
        val later = snapshot.state.lastCollectedAtMs + 6 * 3_600_000L
        assertTrue("Today must preview earnings without opening or collecting the Veil",
            com.ironvellum.app.domain.Idle.collect(snapshot.state, snapshot.rate, later).essence > 0L)
        assertEquals("previewing must not bank currency", 0L, db.idleDao().get()!!.essence)
    }

    @Test
    fun startupPreservesAnExistingAwayIntervalAndBalance() = runBlocking {
        val original = com.ironvellum.app.data.db.IdleStateEntity(
            essence = 42, lifetimeEssence = 100, shadows = 3, relicMultiplier = 1.25,
            lastCollectedAtMs = System.currentTimeMillis() - 30 * 3_600_000L,
        )
        db.idleDao().upsert(original)
        repo.ensureSeeded()
        repo.ensureSeeded()
        assertEquals("startup must not collect, reset tapering or lose the away report", original, db.idleDao().get())
        assertTrue(repo.collectIdle(System.currentTimeMillis()) > 0L)
    }

    @Test
    fun startupRepairsAnUnstartedLegacyClockWithoutChangingItsBank() = runBlocking {
        val original = com.ironvellum.app.data.db.IdleStateEntity(
            essence = 42, lifetimeEssence = 100, shadows = 3, relicMultiplier = 1.25,
        )
        db.idleDao().upsert(original)
        repo.ensureSeeded()
        val repaired = db.idleDao().get()!!
        assertTrue(repaired.lastCollectedAtMs > 0L)
        assertEquals(original, repaired.copy(lastCollectedAtMs = 0L))
    }

    @Test
    fun restoringAnUnstartedClockStartsHomeAccrualWithoutAnEpochPayout() = runBlocking {
        db.idleDao().upsert(com.ironvellum.app.data.db.IdleStateEntity(essence = 42, lifetimeEssence = 100))
        val archive = repo.exportArchive().json
        assertTrue(repo.importArchive(archive).isSuccess)
        val restored = db.idleDao().get()!!
        assertTrue(restored.lastCollectedAtMs > 0L)
        assertEquals(42L, restored.essence)
        assertEquals(100L, restored.lifetimeEssence)
    }

    @Test
    fun activityHiddenRepsAndInvalidExternalLoadsCannotIncreaseTheIdleVolume() = runBlocking {
        val session = db.sessionDao().insertSession(
            SessionEntity(presetId = null, label = "Mixed metrics", startedAtMs = System.currentTimeMillis(), completedAtMs = null, xpAwarded = 0),
        )
        val skipping = db.exerciseDao().byName("Weighted Skipping")!!.id
        val activity = repo.addExtraSet(session, skipping, reps = 10, weightKg = 5.0, modifiers = "")
        repo.updateActivitySet(activity, reps = 10, durationSec = 60, distanceM = null, grade = null, weightKg = 5.0, done = true)
        val bench = db.exerciseDao().byName("Bench Press")!!.id
        val valid = repo.addExtraSet(session, bench, reps = 5, weightKg = 100.0, modifiers = "")
        repo.updateSet(valid, 5, 100.0, done = true)
        val invalid = repo.addExtraSet(session, bench, reps = 5, weightKg = -20.0, modifiers = "")
        repo.updateSet(invalid, 5, -20.0, done = true)
        repo.completeSession(session)

        assertEquals(500.0, repo.observeIdleInputs().first().volumeLast7d, 0.0)
        val raw = db.sessionDao().setsFor(session)
        assertEquals(10, raw.single { it.id == activity }.reps)
        assertEquals(5.0, raw.single { it.id == activity }.weightKg!!, 0.0)
        assertEquals(-20.0, raw.single { it.id == invalid }.weightKg!!, 0.0)
    }

    @Test
    fun aSessionsMovementsCanBeReordered() = runBlocking {
        val ids = repsExerciseIds(3)
        val session = repo.startSessionFromPreset(savePreset("Ordered Pull", ids))

        assertEquals("the preset's order is the session's order", ids, exerciseOrder(session))

        repo.moveSessionExercise(session, exercisePosition = 2, up = true)
        val moved = listOf(ids[0], ids[2], ids[1])
        assertEquals(moved, exerciseOrder(session))

        // Every set of the moved movement travels with it: exercisePosition is
        // shared by a movement's whole block, so a naive two-write swap gave
        // two movements the same position and fused their blocks.
        val positions = repo.observeSessionSets(session).first()
            .groupBy { it.exerciseId }
            .mapValues { (_, sets) -> sets.map { it.exercisePosition }.distinct() }
        assertTrue("each movement keeps exactly one position", positions.values.all { it.size == 1 })
        assertEquals("no two movements share a position", 3, positions.values.flatten().distinct().size)

        // The ends refuse: nothing falls off the list.
        repo.moveSessionExercise(session, exercisePosition = 0, up = true)
        assertEquals(moved, exerciseOrder(session))
    }

    private companion object {
        const val TEST_DB = "daily_loop_invariants_test.db"
    }
}
