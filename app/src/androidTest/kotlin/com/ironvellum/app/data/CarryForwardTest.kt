package com.ironvellum.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.domain.ExerciseMetric
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Logging a set the lifter changed carries its load and reps to the later sets
 * of that movement, through the repository's own write path: other movements
 * and sets the lifter changed by hand are left alone.
 */
@RunWith(AndroidJUnit4::class)
class CarryForwardTest {

    private lateinit var db: IronvellumDatabase
    private lateinit var repo: Repository

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(TEST_DB)
        db = IronvellumDatabase.create(context, TEST_DB)
        repo = Repository(db)
        repo.ensureSeeded()
    }

    @After
    fun tearDown() {
        db.close()
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(TEST_DB)
    }

    /** A live trial of two lifts, three sets of 8 at 80 kg each; returns the rows by lift. */
    private suspend fun trial(): Pair<List<Long>, List<Long>> {
        val lifts = db.exerciseDao().observeAll().first().filter { it.metric == ExerciseMetric.REPS.name }.take(2)
        val id = repo.startFreeformSession("Carry")
        repeat(3) { lifts.forEach { repo.addExtraSet(id, it.id, 8, 80.0, "") } }
        val rows = db.sessionDao().setsFor(id)
        fun idsOf(lift: Int) = rows.filter { it.exerciseId == lifts[lift].id }.sortedBy { it.setIndex }.map { it.id }
        return idsOf(0) to idsOf(1)
    }

    private suspend fun figures(ids: List<Long>) = ids.map { db.sessionDao().setById(it)!!.let { s -> s.reps to s.weightKg } }

    @Test
    fun aMovedLoadAndRepsReachTheRestOfThatLiftOnly() = runBlocking {
        val (squat, bench) = trial()
        // The lifter steps set 1 up, then logs it.
        repo.updateSet(squat[0], reps = 6, weightKg = 82.5, done = false)
        repo.updateSet(squat[0], reps = 6, weightKg = 82.5, done = true)
        assertEquals(listOf(6 to 82.5, 6 to 82.5, 6 to 82.5), figures(squat))
        assertEquals(listOf(8 to 80.0, 8 to 80.0, 8 to 80.0), figures(bench))
    }

    @Test
    fun aSetTheLifterEditedByHandKeepsItsFigures() = runBlocking {
        val (squat, _) = trial()
        repo.updateSet(squat[2], reps = 5, weightKg = 90.0, done = false)
        repo.updateSet(squat[0], reps = 8, weightKg = 82.5, done = false)
        repo.updateSet(squat[0], reps = 8, weightKg = 82.5, done = true)
        assertEquals(listOf(8 to 82.5, 8 to 82.5, 5 to 90.0), figures(squat))
    }

    @Test
    fun loggingASetAsPrescribedLeavesTheRestAlone() = runBlocking {
        val (squat, _) = trial()
        repo.updateSet(squat[1], reps = 8, weightKg = 85.0, done = false)
        repo.updateSet(squat[0], reps = 8, weightKg = 80.0, done = true)
        assertEquals(listOf(8 to 80.0, 8 to 85.0, 8 to 80.0), figures(squat))
    }

    private companion object {
        const val TEST_DB = "carry_forward_test.db"
    }
}
