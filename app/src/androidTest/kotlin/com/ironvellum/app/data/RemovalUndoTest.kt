package com.ironvellum.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.data.db.SetLogEntity
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
 * Removing a set or an exercise from a live trial offers Undo, and Undo puts
 * back exactly what went: the same ids, positions, logged state and loads.
 */
@RunWith(AndroidJUnit4::class)
class RemovalUndoTest {

    private lateinit var db: IronvellumDatabase
    private lateinit var repo: Repository
    private var sessionId = 0L
    private var squat = 0L
    private var bench = 0L

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(TEST_DB)
        db = IronvellumDatabase.create(context, TEST_DB)
        repo = Repository(db)
        repo.ensureSeeded()
        repo.addStat(weightKg = 80.0, bodyFatPct = 14.0)
        val lifts = db.exerciseDao().observeAll().first().filter { it.metric == ExerciseMetric.REPS.name }.take(2)
        squat = lifts[0].id
        bench = lifts[1].id
        sessionId = repo.startFreeformSession("Undo")
        repeat(3) { index -> repo.addExtraSet(sessionId, squat, 8 + index, 80.0 + index, "") }
        repeat(2) { repo.addExtraSet(sessionId, bench, 5, 60.0, "") }
    }

    @After
    fun tearDown() {
        db.close()
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(TEST_DB)
    }

    private suspend fun rows(): List<SetLogEntity> = db.sessionDao().setsFor(sessionId)

    private suspend fun squatRows() = rows().filter { it.exerciseId == squat }.sortedBy { it.setIndex }

    @Test
    fun undoingARemovedLoggedSetRestoresItAndTheNumbersAfterIt() = runBlocking {
        val middle = squatRows()[1]
        repo.updateSet(middle.id, reps = middle.reps, weightKg = middle.weightKg, done = true)
        val before = rows()

        val removed = repo.removeSet(middle.id)
        assertNotNull(removed)
        assertEquals(listOf(0, 1), squatRows().map { it.setIndex })

        assertTrue(repo.restoreSets(removed!!))
        assertEquals("every row exactly as it was, ids and logged state included", before, rows())
    }

    @Test
    fun undoingARemovedExerciseRestoresEverySet() = runBlocking {
        val before = rows()
        val removed = repo.removeSessionExercise(sessionId, squat)
        assertNotNull(removed)
        assertEquals(0, squatRows().size)

        assertTrue(repo.restoreSets(removed!!))
        assertEquals(before, rows())
    }

    @Test
    fun aSecondUndoChangesNothing() = runBlocking {
        val removed = repo.removeSet(squatRows().last().id)!!
        assertTrue(repo.restoreSets(removed))
        val after = rows()
        assertFalse(repo.restoreSets(removed))
        assertEquals(after, rows())
    }

    @Test
    fun aSetAddedInTheMeantimeFollowsTheRestoredOnes() = runBlocking {
        val removed = repo.removeSet(squatRows()[0].id)!!
        repo.addExtraSet(sessionId, squat, 10, 50.0, "")
        assertTrue(repo.restoreSets(removed))
        val now = squatRows()
        assertEquals(listOf(0, 1, 2, 3), now.map { it.setIndex })
        assertEquals(50.0, now.last().weightKg)
        assertEquals(removed.rows.single().id, now.first().id)
    }

    @Test
    fun aSealedTrialCannotBeRestoredIntoNorRemovedFrom() = runBlocking {
        val removed = repo.removeSet(squatRows().last().id)!!
        val set = rows().first()
        repo.updateSet(set.id, reps = 8, weightKg = 20.0, done = true)
        repo.completeSession(sessionId)
        val sealed = rows()
        assertFalse(repo.restoreSets(removed))
        assertNull(repo.removeSet(sealed.first().id))
        assertEquals(sealed, rows())
    }

    private companion object {
        const val TEST_DB = "removal_undo_test.db"
    }
}
