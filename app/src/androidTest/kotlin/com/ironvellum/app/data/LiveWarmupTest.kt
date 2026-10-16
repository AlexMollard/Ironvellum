package com.ironvellum.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.domain.ExerciseMetric
import com.ironvellum.app.domain.SetKind
import com.ironvellum.app.domain.Xp
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
        SeededDb.fresh(context, TEST_DB)
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
    fun theRampGoesInFrontOfTheWorkingSetsUntickedAndUndoTakesItBack() = runBlocking {
        val lift = db.sessionDao().setById(sets[0])!!.exerciseId
        val made = repo.addWarmupRamp(sessionId, lift)
        assertTrue("a loaded rep movement gets a ramp", made.isNotEmpty())
        val rows = db.sessionDao().setsFor(sessionId).sortedBy { it.setIndex }
        assertEquals((0 until rows.size).toList(), rows.map { it.setIndex })
        assertEquals("the ramp is first", made, rows.take(made.size).map { it.id })
        assertTrue(rows.take(made.size).all { it.kind == "WARMUP" && !it.done })
        assertEquals("the working sets follow in their order", sets, rows.drop(made.size).map { it.id })
        assertEquals(60.0, rows.last().weightKg!!, 0.0)
        assertTrue("every warm-up is lighter than the work", rows.take(made.size).all { it.weightKg!! < 60.0 })
        // A second ask adds nothing: the movement already has its warm-ups.
        assertTrue(repo.addWarmupRamp(sessionId, lift).isEmpty())

        repo.removeSets(made)
        assertEquals(sets, db.sessionDao().setsFor(sessionId).sortedBy { it.setIndex }.map { it.id })
        assertEquals(listOf(0, 1, 2), db.sessionDao().setsFor(sessionId).sortedBy { it.setIndex }.map { it.setIndex })
    }

    @Test
    fun aRampPaysNothingWhenTheTrialIsSealed() = runBlocking {
        val lift = db.sessionDao().setById(sets[0])!!.exerciseId
        repo.addWarmupRamp(sessionId, lift)
        repo.updateSet(sets[0], reps = 8, weightKg = 60.0, done = true)
        val workingOnly = db.sessionDao().setsFor(sessionId).filter { it.id == sets[0] }
        val expected = SessionScoring(db.exerciseDao().observeAll().first()).xp(workingOnly, 80.0)
        // The sets' own pay: the trial-level bonuses (the first trial of the week) are not what a ramp could change.
        assertEquals(expected - Xp.COMPLETION_BONUS, setsPay(repo.completeSession(sessionId)))
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
        assertEquals(expected - Xp.COMPLETION_BONUS, setsPay(repo.completeSession(sessionId)))
    }

    private fun setsPay(result: Repository.CompletionResult) = result.breakdown.of(Xp.Kind.EXERCISE).sumOf { it.xp }

    private companion object {
        const val TEST_DB = "live_warmup_test.db"
    }
}
