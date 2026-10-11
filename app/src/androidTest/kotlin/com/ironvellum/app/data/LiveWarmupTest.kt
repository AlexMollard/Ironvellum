package com.ironvellum.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.domain.ExerciseMetric
import com.ironvellum.app.domain.SetKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A set marked as a warm-up on the live screen is stored unticked, stays
 * unticked whatever a stale screen writes, and pays nothing when sealed.
 */
@RunWith(AndroidJUnit4::class)
class LiveWarmupTest {

    private lateinit var db: IronvellumDatabase
    private lateinit var repo: Repository
    private var sessionId = 0L
    private var sets = emptyList<Long>()

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(TEST_DB)
        db = IronvellumDatabase.create(context, TEST_DB)
        repo = Repository(db)
        repo.ensureSeeded()
        repo.addStat(weightKg = 80.0, bodyFatPct = 14.0)
        val lift = db.exerciseDao().observeAll().first().first { it.metric == ExerciseMetric.REPS.name }
        sessionId = repo.startFreeformSession("Warm-up")
        repeat(3) { repo.addExtraSet(sessionId, lift.id, 8, 60.0, "") }
        sets = db.sessionDao().setsFor(sessionId).sortedBy { it.setIndex }.map { it.id }
    }

    @After
    fun tearDown() {
        db.close()
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(TEST_DB)
    }

    private suspend fun row(id: Long) = db.sessionDao().setById(id)!!

    @Test
    fun markingALoggedSetAsAWarmUpUnticksItAndCountingItTicksItAgain() = runBlocking {
        repo.updateSet(sets[0], reps = 8, weightKg = 60.0, done = true)
        repo.setKind(sets[0], SetKind.WARMUP)
        assertEquals("WARMUP", row(sets[0]).kind)
        assertFalse("a warm-up is stored unticked", row(sets[0]).done)

        repo.setKind(sets[0], SetKind.WORKING)
        assertEquals("WORKING", row(sets[0]).kind)
        assertTrue("counted as a working set, it is logged", row(sets[0]).done)
    }

    @Test
    fun dropFailureAndRestPauseKeepTheTickAndPayLikeWorkingSets() = runBlocking {
        repo.updateSet(sets[0], reps = 8, weightKg = 60.0, done = true)
        for (kind in listOf(SetKind.DROP, SetKind.FAILURE, SetKind.REST_PAUSE, SetKind.WORKING)) {
            repo.setKind(sets[0], kind)
            assertEquals(kind.name, row(sets[0]).kind)
            assertTrue("a $kind set is still logged", row(sets[0]).done)
        }
        // An unlogged set changed to a counting kind stays unlogged.
        repo.setKind(sets[1], SetKind.DROP)
        assertFalse(row(sets[1]).done)
        // A drop set ticks like any other.
        repo.updateSet(sets[1], reps = 6, weightKg = 40.0, done = true)
        assertTrue(row(sets[1]).done)
    }

    @Test
    fun aWarmUpStaysUntickedWhateverTheScreenWrites() = runBlocking {
        repo.setKind(sets[0], SetKind.WARMUP)
        repo.updateSet(sets[0], reps = 8, weightKg = 60.0, done = true)
        assertFalse(row(sets[0]).done)
    }

    @Test
    fun aWarmUpPaysNothingWhenTheTrialIsSealed() = runBlocking {
        repo.updateSet(sets[0], reps = 8, weightKg = 100.0, done = true)
        repo.setKind(sets[0], SetKind.WARMUP)
        repo.updateSet(sets[1], reps = 8, weightKg = 60.0, done = true)

        val workingOnly = db.sessionDao().setsFor(sessionId).filter { it.id == sets[1] }
        val expected = SessionScoring(db.exerciseDao().observeAll().first()).xp(workingOnly, 80.0)
        assertEquals(expected, repo.completeSession(sessionId).xpAwarded)
    }

    private companion object {
        const val TEST_DB = "live_warmup_test.db"
    }
}
