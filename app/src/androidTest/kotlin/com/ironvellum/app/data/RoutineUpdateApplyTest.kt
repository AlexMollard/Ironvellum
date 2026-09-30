package com.ironvellum.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.data.db.PresetEntity
import com.ironvellum.app.data.db.PresetEntryEntity
import com.ironvellum.app.domain.RoutineUpdate
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
 * done; accepting writes only the accepted fields of the accepted entries,
 * and never the log.
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
    fun acceptedFieldsUpdateThePresetAndNothingElse() = runBlocking {
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
        // A: four of six sets, 6,6,6,4 at the planned load: sets and reps move.
        prefilled.filter { it.exerciseId == a }.sortedBy { it.setIndex }.take(4).zip(listOf(6, 6, 6, 4)).forEach { (set, reps) ->
            repo.updateSet(set.id, reps, 15.2, done = true)
        }
        // B: all three, 10,9,7 — then a fourth set above the plan, so B's
        // proposal rises to four while A's short day cannot shrink its six.
        prefilled.filter { it.exerciseId == b }.sortedBy { it.setIndex }.zip(listOf(10, 9, 7)).forEach { (set, reps) ->
            repo.updateSet(set.id, reps, null, done = true)
        }
        val extraB = repo.addExtraSet(sessionId, b, reps = 9, weightKg = null, modifiers = "")
        repo.updateSet(extraB, 9, null, done = true)
        // C: exactly as planned.
        prefilled.filter { it.exerciseId == c }.forEach { repo.updateSet(it.id, 8, 20.0, done = true) }

        // Nothing to offer while the session is live.
        assertNull(repo.routineUpdateFor(sessionId))
        repo.completeSession(sessionId)
        val offer = repo.routineUpdateFor(sessionId)!!
        assertEquals(presetId, offer.presetId)
        assertEquals(listOf(a, b), offer.changes.map { it.before.exerciseId })
        // A ran short: sets never drop, so only its reps move.
        val changeA = offer.changes.first { it.before.exerciseId == a }
        assertEquals(listOf(RoutineUpdate.Field.REPS), changeA.fields)
        // B ran long: the extra set is offered, with its reps.
        val changeB = offer.changes.first { it.before.exerciseId == b }
        assertEquals(listOf(RoutineUpdate.Field.SETS, RoutineUpdate.Field.REPS), changeB.fields)

        val loggedBefore = db.sessionDao().setsFor(sessionId)
        val entriesBefore = db.presetDao().presetWithEntries(presetId)!!.entries.associateBy { it.exerciseId }
        // The lifter ticks only A's reps and turns B off entirely.
        repo.applyRoutineUpdate(presetId, listOfNotNull(changeA.only(setOf(RoutineUpdate.Field.REPS))))

        val entries = db.presetDao().presetWithEntries(presetId)!!.entries.associateBy { it.exerciseId }
        assertEquals(entriesBefore.getValue(a).copy(targetReps = 6), entries.getValue(a))
        assertEquals(entriesBefore.getValue(b), entries.getValue(b))
        assertEquals(entriesBefore.getValue(c), entries.getValue(c))
        assertEquals(loggedBefore, db.sessionDao().setsFor(sessionId))

        // The set count was not accepted: the next start still plans six.
        val next = repo.startSessionFromPreset(presetId)
        assertEquals(6, db.sessionDao().setsFor(next).count { it.exerciseId == a })
    }

    private companion object {
        const val TEST_DB = "routine_update_test.db"
    }
}
