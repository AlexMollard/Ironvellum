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
 * The lock-screen "Set done" and the trial screen's log button write through the
 * same [LiveTrialActions]: a tick starts the same rest, honours rest off, and a
 * stale notification ticks nothing.
 */
@RunWith(AndroidJUnit4::class)
class LiveTrialActionsTest {

    private lateinit var db: IronvellumDatabase
    private lateinit var repo: Repository
    private lateinit var actions: LiveTrialActions
    private lateinit var prefs: TrialPrefs
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
        prefs = TrialPrefs(context)
        prefs.restOff = false
        val lift = db.exerciseDao().observeAll().first().first { it.metric == ExerciseMetric.REPS.name }
        prefs.setRestOffFor(lift.name, false)
        sessionId = repo.startFreeformSession("Between sets")
        repeat(3) { repo.addExtraSet(sessionId, lift.id, 8, 60.0, "") }
        sets = db.sessionDao().setsFor(sessionId).sortedBy { it.setIndex }.map { it.id }
        RestClock.cancel(sessionId)
        actions = LiveTrialActions(repo, context, sessionId)
    }

    @After
    fun tearDown() {
        RestClock.cancel(sessionId)
        prefs.restOff = false
        db.close()
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(TEST_DB)
    }

    private suspend fun row(id: Long) = db.sessionDao().setById(id)!!

    @Test
    fun setDoneTicksTheNextSetWithItsShownFiguresAndStartsTheRest() = runBlocking {
        assertTrue(actions.tickNext(expectedSetId = sets[0]))
        assertTrue(row(sets[0]).done)
        assertEquals(60.0, row(sets[0]).weightKg!!, 0.0)
        assertEquals(8, row(sets[0]).reps)
        assertFalse("only the next set is ticked", row(sets[1]).done)
        val rest = RestClock.timer.value
        assertNotNull("ticking a set with sets waiting starts a rest", rest)
        assertEquals(sessionId, rest!!.sessionId)
    }

    @Test
    fun restOffStartsNoRestFromTheLockScreenEither() = runBlocking {
        prefs.restOff = true
        assertTrue(actions.tickNext())
        assertTrue(row(sets[0]).done)
        assertNull(RestClock.timer.value?.takeIf { it.sessionId == sessionId })
    }

    @Test
    fun aStaleNotificationTicksNothing() = runBlocking {
        // The lifter already logged set 1 in the app; the notification still names it.
        repo.updateSet(sets[0], reps = 8, weightKg = 60.0, done = true)
        assertFalse(actions.tickNext(expectedSetId = sets[0]))
        assertFalse(row(sets[1]).done)
    }

    @Test
    fun nothingIsTickedWhenEverySetIsDone() = runBlocking {
        sets.forEach { repo.updateSet(it, reps = 8, weightKg = 60.0, done = true) }
        assertFalse(actions.tickNext())
    }

    private companion object {
        const val TEST_DB = "live_trial_actions_test.db"
    }
}
