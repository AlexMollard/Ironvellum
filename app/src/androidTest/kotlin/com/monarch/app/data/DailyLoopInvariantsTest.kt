package com.monarch.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.monarch.app.data.db.SessionEntity
import com.monarch.app.domain.Progression
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

    private lateinit var db: MonarchDatabase
    private lateinit var repo: Repository

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(TEST_DB)
        db = MonarchDatabase.create(context, TEST_DB)
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
        val preset = savePreset("Stall Pull", listOf(exerciseId))
        val stalls = Progression.STALLS_BEFORE_DELOAD

        // Completed sessions at the same load, none clearing the 5-rep target.
        // The session start only ever read the LATEST session, so the stall
        // count stayed 0 and the hunter was told to repeat 100 kg forever.
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

        val next = repo.startSessionFromPreset(preset)
        val prescribed = db.sessionDao().setsFor(next).first().weightKg
        assertTrue(
            "after $stalls stalled sessions the load must come off, not repeat at 100 kg (got $prescribed)",
            prescribed != null && prescribed < 100.0,
        )
    }

    @Test
    fun theIdleClockStartsOnTheFirstLook() = runBlocking {
        val now = System.currentTimeMillis()
        // The seeded row carries lastCollectedAtMs = 0, which the curve reads
        // as "nothing earned" so the epoch cannot pay a 56-year absence. The
        // baseline was then never written, so essence never moved again.
        assertEquals("nothing is owed before the clock starts", 0L, repo.collectIdle(now))
        assertTrue("the first look must stamp a baseline", db.idleDao().get()!!.lastCollectedAtMs > 0L)
        assertTrue("six hours after the baseline must pay something", repo.collectIdle(now + 6 * 3_600_000L) > 0L)
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
