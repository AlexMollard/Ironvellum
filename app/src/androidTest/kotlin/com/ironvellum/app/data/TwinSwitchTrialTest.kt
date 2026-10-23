package com.ironvellum.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** "Switch" in a live trial moves only the unticked sets of a block, splitting it when some are ticked. */
@RunWith(AndroidJUnit4::class)
class TwinSwitchTrialTest {

    private lateinit var db: IronvellumDatabase
    private lateinit var repo: Repository
    private var pullUp = 0L
    private var weightedPullUp = 0L
    private var dip = 0L
    private var sessionId = 0L

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        SeededDb.fresh(context, TEST_DB)
        db = IronvellumDatabase.create(context, TEST_DB)
        repo = Repository(db)
        repo.ensureSeeded()
        pullUp = repo.exerciseIdByName("Pull-up")!!
        weightedPullUp = repo.exerciseIdByName("Weighted Pull-up")!!
        dip = repo.exerciseIdByName("Dip")!!
        sessionId = repo.startFreeformSession("Twin")
        repeat(4) { repo.addExtraSet(sessionId, pullUp, 5, 10.0, "weighted") }
        repeat(2) { repo.addExtraSet(sessionId, dip, 8, null, "") }
    }

    @After
    fun tearDown() {
        db.close()
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(TEST_DB)
    }

    private suspend fun sets() = repo.observeSessionSets(sessionId).first()

    private suspend fun tick(count: Int) {
        sets().filter { it.exerciseId == pullUp }.sortedBy { it.setIndex }.take(count)
            .forEach { repo.updateSet(it.id, it.reps, it.weightKg, done = true) }
    }

    @Test
    fun aBlockWithNothingTickedBecomesTheTwinWhole() = runBlocking {
        val result = repo.switchBlockToTwin(sessionId, repo.blockOf(sessionId, pullUp), weightedPullUp, "")
        assertEquals(Repository.TwinSwitch.MOVED, result)
        val moved = sets().filter { it.exerciseId == weightedPullUp }
        assertEquals(4, moved.size)
        assertTrue(moved.all { it.modifiers == "" && it.exercisePosition == 0 })
        assertEquals(listOf(0, 1, 2, 3), moved.map { it.setIndex }.sorted())
    }

    @Test
    fun tickedSetsKeepTheirExerciseAndTheRestBecomeABlockOfTheirOwn() = runBlocking {
        tick(2)
        val result = repo.switchBlockToTwin(sessionId, repo.blockOf(sessionId, pullUp), weightedPullUp, "")
        assertEquals(Repository.TwinSwitch.SPLIT, result)
        val all = sets()
        val kept = all.filter { it.exerciseId == pullUp }.sortedBy { it.setIndex }
        val moved = all.filter { it.exerciseId == weightedPullUp }.sortedBy { it.setIndex }
        assertEquals(listOf(0, 1), kept.map { it.setIndex })
        assertTrue(kept.all { it.done && it.modifiers == "weighted" && it.exercisePosition == 0 })
        assertEquals(listOf(0, 1), moved.map { it.setIndex })
        assertTrue(moved.none { it.done } && moved.all { it.modifiers == "" && it.exercisePosition == 1 })
        // The dip block made room: one place later, still after both.
        assertEquals(2, all.first { it.exerciseId == dip }.exercisePosition)
        assertEquals(listOf(pullUp, weightedPullUp, dip), all.sortedBy { it.exercisePosition }.map { it.exerciseId }.distinct())
    }

    @Test
    fun nothingMovesWhenEverySetIsTicked() = runBlocking {
        tick(4)
        assertEquals(Repository.TwinSwitch.NONE, repo.switchBlockToTwin(sessionId, repo.blockOf(sessionId, pullUp), weightedPullUp, ""))
        assertTrue(sets().none { it.exerciseId == weightedPullUp })
    }

    private companion object {
        const val TEST_DB = "twin_switch_test.db"
    }
}
