package com.ironvellum.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.domain.ExerciseMetric
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A live superset through the repository and the lock-screen path: linking and unlinking, the cycle of
 * "Set done", and rest only once the round is closed. The pure rules are in SupersetsTest; this proves the
 * database writes and [LiveTrialActions] agree with them.
 */
@RunWith(AndroidJUnit4::class)
class SupersetTrialTest {

    private lateinit var db: IronvellumDatabase
    private lateinit var repo: Repository
    private lateinit var actions: LiveTrialActions
    private lateinit var prefs: TrialPrefs
    private var sessionId = 0L
    private var a = 0L
    private var b = 0L
    private var c = 0L

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        SeededDb.fresh(context, TEST_DB)
        db = IronvellumDatabase.create(context, TEST_DB)
        repo = Repository(db)
        repo.ensureSeeded()
        repo.addStat(weightKg = 80.0, bodyFatPct = 14.0)
        prefs = TrialPrefs(context)
        prefs.restOff = false
        val lifts = db.exerciseDao().observeAll().first().filter { it.metric == ExerciseMetric.REPS.name }.take(3)
        lifts.forEach { prefs.setRestOffFor(it.name, false) }
        a = lifts[0].id
        b = lifts[1].id
        c = lifts[2].id
        sessionId = repo.startFreeformSession("Superset")
        listOf(a, b, c).forEach { id -> repeat(2) { repo.addExtraSet(sessionId, id, 8, 60.0, "") } }
        RestClock.cancel(sessionId)
        actions = LiveTrialActions(repo, context, sessionId)
    }

    @After
    fun tearDown() {
        RestClock.cancel(sessionId)
        db.close()
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(TEST_DB)
    }

    private suspend fun sets() = repo.observeSessionSets(sessionId).first()

    private fun restRunning() = RestClock.timer.value?.takeIf { it.sessionId == sessionId } != null

    @Test
    fun linkingWithTheNextExerciseSharesAGroupOnEverySetAndAddedSetsKeepIt() = runBlocking {
        assertTrue(repo.linkSupersetWithNext(sessionId, a))
        val linked = sets().filter { it.exerciseId == a || it.exerciseId == b }
        assertEquals(1, linked.mapNotNull { it.supersetGroup }.distinct().size)
        assertTrue(linked.all { it.supersetGroup != null })
        assertTrue(sets().filter { it.exerciseId == c }.all { it.supersetGroup == null })
        // A set added to a linked movement stays in its superset.
        repo.addExtraSet(sessionId, a, 8, 60.0, "")
        assertTrue(sets().filter { it.exerciseId == a }.all { it.supersetGroup == linked.first().supersetGroup })
        // Already together, and a triple takes the third but nothing more.
        assertFalse(repo.linkSupersetWithNext(sessionId, a))
        assertTrue(repo.linkSupersetWithNext(sessionId, b))
        assertEquals(1, sets().mapNotNull { it.supersetGroup }.distinct().size)
    }

    @Test
    fun unlinkingEndsTheWholeGroupAndLeavesTheOrder() = runBlocking {
        repo.linkSupersetWithNext(sessionId, a)
        repo.unlinkSuperset(sessionId, b)
        assertTrue(sets().all { it.supersetGroup == null })
        assertEquals(listOf(a, b, c), sets().sortedBy { it.exercisePosition }.map { it.exerciseId }.distinct())
    }

    @Test
    fun movingASoloExerciseStepsOverAWholeGroup() = runBlocking {
        repo.linkSupersetWithNext(sessionId, b)
        // c is last; a is first. Moving a down steps over the pair b + c.
        repo.moveSessionExercise(sessionId, exercisePosition = 0, up = false)
        assertEquals(listOf(b, c, a), sets().sortedBy { it.exercisePosition }.map { it.exerciseId }.distinct())
        assertEquals(2, sets().filter { it.supersetGroup != null }.map { it.exerciseId }.distinct().size)
    }

    @Test
    fun theLockScreenTicksTheRoundAndRestsOnlyWhenItCloses() = runBlocking {
        repo.linkSupersetWithNext(sessionId, a)
        // A1: mid-round, no rest.
        assertTrue(actions.tickNext())
        assertFalse("a tick mid-round starts no rest", restRunning())
        // B1 is next, and it closes round one.
        val next = sets().first { it.isPending && it.exerciseId == b }
        assertTrue(actions.tickNext(expectedSetId = next.id))
        assertTrue(restRunning())
        assertNotNull(RestClock.timer.value)
        RestClock.cancel(sessionId)
        // The cycle is back on the first exercise for round two.
        val round2 = sets().first { it.isPending }
        assertEquals(a, round2.exerciseId)
        assertNull(RestClock.timer.value?.takeIf { it.sessionId == sessionId })
    }

    @Test
    fun aStaleNotificationNamingTheWrongPartnerTicksNothing() = runBlocking {
        repo.linkSupersetWithNext(sessionId, a)
        assertTrue(actions.tickNext())
        // The notification was posted before A1 was ticked and still names A2 (the next in order).
        val stale = sets().first { it.exerciseId == a && it.isPending }
        assertFalse(actions.tickNext(expectedSetId = stale.id))
    }

    private companion object {
        const val TEST_DB = "superset_trial_test.db"
    }
}
