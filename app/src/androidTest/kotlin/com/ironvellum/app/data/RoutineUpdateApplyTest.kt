package com.ironvellum.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.data.db.PresetEntity
import com.ironvellum.app.data.db.PresetEntryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Finishing a preset session offers to bring the preset in line with what was
 * done; accepting writes only the accepted entries, and never the log.
 */
@RunWith(AndroidJUnit4::class)
class RoutineUpdateApplyTest {

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
    fun acceptedRowsUpdateThePresetAndNothingElse() = runBlocking {
        val (a, b, c) = db.exerciseDao().observeAll().first()
            .filter { it.metric == "REPS" && it.category.isEmpty() }
            .take(3)
            .map { it.id }
        val presetId = db.presetDao().insertPreset(PresetEntity(name = "Pull", note = "", scheduledDay = null))
        db.presetDao().insertEntries(
            listOf(
                PresetEntryEntity(presetId = presetId, exerciseId = a, targetSets = 6, targetReps = 5, targetWeightKg = 15.2, modifiers = "", position = 0),
                PresetEntryEntity(presetId = presetId, exerciseId = b, targetSets = 3, targetReps = 10, targetWeightKg = null, modifiers = "", position = 1),
                PresetEntryEntity(presetId = presetId, exerciseId = c, targetSets = 3, targetReps = 8, targetWeightKg = 20.0, modifiers = "", position = 2),
            ),
        )
        val sessionId = repo.startSessionFromPreset(presetId)
        val prefilled = db.sessionDao().setsFor(sessionId)
        // A: four of six sets, 5,5,5,3 at the planned load.
        prefilled.filter { it.exerciseId == a }.sortedBy { it.setIndex }.take(4).zip(listOf(5, 5, 5, 3)).forEach { (set, reps) ->
            repo.updateSet(set.id, reps, 15.2, done = true)
        }
        // B: all three, 10,9,7.
        prefilled.filter { it.exerciseId == b }.sortedBy { it.setIndex }.zip(listOf(10, 9, 7)).forEach { (set, reps) ->
            repo.updateSet(set.id, reps, null, done = true)
        }
        // C: exactly as planned.
        prefilled.filter { it.exerciseId == c }.forEach { repo.updateSet(it.id, 8, 20.0, done = true) }

        // Nothing to offer while the session is live.
        assertNull(repo.routineUpdateFor(sessionId))
        repo.completeSession(sessionId)
        val offer = repo.routineUpdateFor(sessionId)!!
        assertEquals(presetId, offer.presetId)
        assertEquals(listOf(a, b), offer.changes.map { it.before.exerciseId })

        val loggedBefore = db.sessionDao().setsFor(sessionId)
        val entriesBefore = db.presetDao().presetWithEntries(presetId)!!.entries.associateBy { it.exerciseId }
        // The lifter accepts A's row and turns B's off.
        repo.applyRoutineUpdate(presetId, offer.changes.filter { it.before.exerciseId == a })

        val entries = db.presetDao().presetWithEntries(presetId)!!.entries.associateBy { it.exerciseId }
        assertEquals(entriesBefore.getValue(a).copy(targetSets = 4, targetReps = 5, targetWeightKg = 15.2), entries.getValue(a))
        assertEquals(entriesBefore.getValue(b), entries.getValue(b))
        assertEquals(entriesBefore.getValue(c), entries.getValue(c))
        assertEquals(loggedBefore, db.sessionDao().setsFor(sessionId))

        // The next start takes the new set count; the load and reps come from
        // Progression, which repeats 5 at 15.2 because the last set missed 5.
        val next = repo.startSessionFromPreset(presetId)
        val nextA = db.sessionDao().setsFor(next).filter { it.exerciseId == a }
        assertEquals(4, nextA.size)
        assertEquals(List(4) { 5 }, nextA.map { it.reps })
        assertEquals(List(4) { 15.2 }, nextA.map { it.weightKg })
    }

    private companion object {
        const val TEST_DB = "routine_update_test.db"
    }
}
