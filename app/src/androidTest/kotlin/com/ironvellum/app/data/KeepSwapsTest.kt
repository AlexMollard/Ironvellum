package com.ironvellum.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.data.db.PresetEntity
import com.ironvellum.app.data.db.PresetEntryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** A lift swapped in a trial is offered back to the rite; keeping it changes only that entry's lift. */
@RunWith(AndroidJUnit4::class)
class KeepSwapsTest {

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

    @Test
    fun aKeptSwapReplacesTheLiftAndKeepsSetsRepsAndOrder() = runBlocking {
        val (a, b, c) = db.exerciseDao().observeAll().first()
            .filter { it.metric == "REPS" && it.category.isEmpty() }
            .take(3)
            .map { it.id }
        val presetId = db.presetDao().insertPreset(PresetEntity(name = "Core", note = "", scheduledDay = null))
        db.presetDao().insertEntries(
            listOf(
                PresetEntryEntity(presetId = presetId, exerciseId = a, targetSets = 3, targetReps = 12, targetWeightKg = null, modifiers = "", position = 0),
                PresetEntryEntity(presetId = presetId, exerciseId = b, targetSets = 3, targetReps = 10, targetWeightKg = null, modifiers = "", position = 1),
            ),
        )
        val sessionId = repo.startSessionFromPreset(presetId)
        // A is swapped for C: removed, then C added and done.
        repo.removeSessionExercise(sessionId, a)
        val extra = repo.addExtraSet(sessionId, c, reps = 12, weightKg = null, modifiers = "")
        repo.updateSet(extra, 12, null, done = true)
        db.sessionDao().setsFor(sessionId).filter { it.exerciseId == b }.forEach { repo.updateSet(it.id, 10, null, done = true) }
        repo.completeSession(sessionId)

        val offer = repo.routineUpdateFor(sessionId)!!
        val swap = offer.swaps.single()
        assertEquals(a, swap.entry.exerciseId)
        assertEquals(c, swap.toId)

        val before = db.presetDao().presetWithEntries(presetId)!!.entries.associateBy { it.position }
        repo.applyRoutineUpdate(presetId, emptyList(), offer.swaps)

        val after = db.presetDao().presetWithEntries(presetId)!!.entries.associateBy { it.position }
        assertEquals(before.getValue(0).copy(exerciseId = c), after.getValue(0))
        assertEquals(before.getValue(1), after.getValue(1))
    }

    private companion object {
        const val TEST_DB = "keep_swaps_test.db"
    }
}
