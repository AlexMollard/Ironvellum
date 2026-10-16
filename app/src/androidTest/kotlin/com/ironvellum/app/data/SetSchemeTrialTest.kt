package com.ironvellum.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.domain.LoadKit
import com.ironvellum.app.domain.RoutineUpdate
import com.ironvellum.app.domain.SchemeType
import com.ironvellum.app.domain.SetKind
import com.ironvellum.app.domain.SetScheme
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
 * Set schemes in a live trial: a rite with a top scheme begins as a top set and back-offs, a logged top set hands
 * its load to the back-offs, the long rest follows it, and "Make set 1 a top set" and "Add a drop" change today's
 * trial alone. Also the owner's own plan, expressed exactly: top +22 kg x 2-4, three fixed +15 kg x 4-5 back-offs.
 */
@RunWith(AndroidJUnit4::class)
class SetSchemeTrialTest {

    private lateinit var db: IronvellumDatabase
    private lateinit var repo: Repository
    private var row = 0L
    private var pullUp = 0L

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        SeededDb.fresh(context, TEST_DB)
        db = IronvellumDatabase.create(context, TEST_DB)
        repo = Repository(db, loadKit = { LoadKit.NONE })
        repo.ensureSeeded()
        row = db.exerciseDao().byName("Barbell Row")!!.id
        pullUp = db.exerciseDao().byName("Pull-up")!!.id
    }

    @After
    fun tearDown() {
        db.close()
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(TEST_DB)
    }

    private val topBackoff = SetScheme(type = SchemeType.TOP_BACKOFF)

    private val owners = SetScheme(
        type = SchemeType.TOP_BACKOFF, topReps = 2..4, topRir = 0..1, backoffSets = 3, backoffFixedKg = 15.0, backoffReps = 4..5,
    )

    private suspend fun save(exerciseId: Long, sets: Int, reps: Int, kg: Double?, scheme: SetScheme, modifiers: String = ""): Long =
        repo.savePreset(
            null, "Pull day", "", null,
            listOf(Repository.PresetDraftEntry(exerciseId, sets, reps, kg, modifiers = modifiers, scheme = scheme)),
        )

    private suspend fun setsOf(sessionId: Long, exerciseId: Long) =
        repo.observeSessionSets(sessionId).first().filter { it.exerciseId == exerciseId }.sortedBy { it.setIndex }

    @Test
    fun aTopSchemeBeginsAsATopSetAndBackoffs() = runBlocking {
        val rite = save(row, sets = 3, reps = 4, kg = 80.0, scheme = topBackoff)
        val sets = setsOf(repo.startSessionFromPreset(rite), row)
        assertEquals(listOf(SetKind.TOP, SetKind.BACKOFF, SetKind.BACKOFF), sets.map { it.kind })
        assertEquals(80.0, sets[0].weightKg!!, 0.0)
        assertEquals(4, sets[0].reps)
        // 10% lighter, 72 kg, on the 2.5 kg grid: 70.
        assertEquals(listOf(70.0, 70.0), sets.drop(1).map { it.weightKg })
        assertEquals(listOf(7, 7), sets.drop(1).map { it.reps })
        assertTrue(sets.none { it.done })
    }

    @Test
    fun aLoggedTopSetFillsTheBackoffsFromTheLoadActuallyLifted() = runBlocking {
        val rite = save(row, 3, 4, 80.0, topBackoff)
        val session = repo.startSessionFromPreset(rite)
        val top = setsOf(session, row).first()
        repo.updateSet(top.id, reps = 5, weightKg = 100.0, done = true)
        val after = setsOf(session, row)
        // The top took no part in the carry: its back-offs are 10% under 100, not 100.
        assertEquals(listOf(100.0, 90.0, 90.0), after.map { it.weightKg })
        assertEquals(listOf(true, false, false), after.map { it.done })
        // Moving the logged top's load refills the back-offs still waiting.
        repo.updateSet(top.id, reps = 5, weightKg = 120.0, done = true)
        assertEquals(listOf(120.0, 107.5, 107.5), setsOf(session, row).map { it.weightKg })
    }

    @Test
    fun aBackoffTheLifterMovedByHandStaysWhereTheySetIt() = runBlocking {
        val rite = save(row, 3, 4, 80.0, topBackoff)
        val session = repo.startSessionFromPreset(rite)
        val sets = setsOf(session, row)
        repo.updateSet(sets[1].id, reps = 7, weightKg = 65.0, done = false)
        repo.updateSet(sets[0].id, reps = 5, weightKg = 100.0, done = true)
        assertEquals(listOf(100.0, 65.0, 90.0), setsOf(session, row).map { it.weightKg })
    }

    @Test
    fun theOwnersPlanBeginsExactlyAsWritten() = runBlocking {
        val rite = save(pullUp, sets = 4, reps = 2, kg = 22.0, scheme = owners, modifiers = "weighted")
        val saved = repo.observePresets().first().single().entries.single()
        assertEquals(owners, saved.scheme)
        val session = repo.startSessionFromPreset(rite)
        val sets = setsOf(session, pullUp)
        assertEquals(listOf(SetKind.TOP, SetKind.BACKOFF, SetKind.BACKOFF, SetKind.BACKOFF), sets.map { it.kind })
        assertEquals(listOf(22.0, 15.0, 15.0, 15.0), sets.map { it.weightKg })
        assertEquals(listOf(2, 5, 5, 5), sets.map { it.reps })
        // A top set heavier than planned leaves the fixed back-offs alone.
        repo.updateSet(sets[0].id, reps = 3, weightKg = 24.0, done = true)
        assertEquals(listOf(24.0, 15.0, 15.0, 15.0), setsOf(session, pullUp).map { it.weightKg })
        assertEquals(180, repo.restAfterTopSeconds(session, pullUp))
        // And ran as written, the trial offers the rite no change but the heavier load.
        setsOf(session, pullUp).drop(1).forEach { repo.updateSet(it.id, 5, 15.0, done = true) }
        repo.completeSession(session)
        val offer = repo.routineUpdateFor(session)!!
        assertEquals(listOf(RoutineUpdate.Field.LOAD), offer.changes.single().fields)
        assertEquals(24.0, offer.changes.single().after.targetWeightKg!!, 0.0)
    }

    @Test
    fun aTopSetStartsWhereTheLastOneLeftOff() = runBlocking {
        val rite = save(pullUp, 4, 2, 22.0, owners, "weighted")
        val first = repo.startSessionFromPreset(rite)
        setsOf(first, pullUp).forEach { repo.updateSet(it.id, if (it.kind == SetKind.TOP) 4 else 5, it.weightKg, done = true) }
        repo.completeSession(first)
        // 4 x +22 filled the reps range (2-4): the next top is one step up at the range's floor.
        val second = setsOf(repo.startSessionFromPreset(rite), pullUp)
        assertEquals(SetKind.TOP, second.first().kind)
        assertEquals(2, second.first().reps)
        assertTrue(second.first().weightKg!! > 22.0)
        assertEquals(15.0, second[1].weightKg!!, 0.0)
    }

    @Test
    fun aStraightLiftWithARepRangeClimbsItThenAddsLoadFromTheCeiling() = runBlocking {
        val ranged = SetScheme(workingReps = 10..15)
        val rite = save(row, 3, 10, 60.0, ranged)
        assertEquals(ranged, repo.observePresets().first().single().entries.single().scheme)
        val first = repo.startSessionFromPreset(rite)
        setsOf(first, row).forEach { repo.updateSet(it.id, 10, it.weightKg, done = true) }
        repo.completeSession(first)
        // 3 x 10 at the floor of 10-15: the same load, two more reps.
        val second = repo.startSessionFromPreset(rite)
        assertEquals(listOf(12, 12, 12), setsOf(second, row).map { it.reps })
        assertEquals(listOf(60.0, 60.0, 60.0), setsOf(second, row).map { it.weightKg })
        setsOf(second, row).forEach { repo.updateSet(it.id, 15, it.weightKg, done = true) }
        repo.completeSession(second)
        // 3 x 15 is the ceiling: 2.5 kg more, back to the floor.
        val third = setsOf(repo.startSessionFromPreset(rite), row)
        assertEquals(listOf(10, 10, 10), third.map { it.reps })
        assertEquals(listOf(62.5, 62.5, 62.5), third.map { it.weightKg })
    }

    @Test
    fun makingSetOneATopSetTurnsTheRestIntoBackoffsForTodayOnly() = runBlocking {
        val rite = save(row, 3, 8, 60.0, SetScheme())
        val session = repo.startSessionFromPreset(rite)
        assertTrue(repo.makeTopSet(session, row))
        val sets = setsOf(session, row)
        assertEquals(listOf(SetKind.TOP, SetKind.BACKOFF, SetKind.BACKOFF), sets.map { it.kind })
        assertEquals(listOf(60.0, 52.5, 52.5), sets.map { it.weightKg })
        // Once only, and the rite stays as it is.
        assertFalse(repo.makeTopSet(session, row))
        assertEquals(SetScheme(), repo.observePresets().first().single().entries.single().scheme)
        assertEquals(180, repo.restAfterTopSeconds(session, row))
    }

    @Test
    fun makingATopSetAfterTheTrialOffersItToTheRiteAsALiftWideChange() = runBlocking {
        val rite = save(row, 3, 8, 60.0, SetScheme())
        val session = repo.startSessionFromPreset(rite)
        repo.makeTopSet(session, row)
        setsOf(session, row).forEach { repo.updateSet(it.id, it.reps, it.weightKg, done = true) }
        repo.completeSession(session)
        val change = repo.routineUpdateFor(session)!!.changes.single()
        assertTrue(RoutineUpdate.Field.SCHEME in change.defaultTicks())
        repo.applyRoutineUpdate(rite, listOf(change.only(change.defaultTicks())!!))
        val written = repo.observePresets().first().single().entries.single()
        assertEquals(SchemeType.TOP_BACKOFF, written.scheme.type)
        assertEquals(2, written.scheme.backoffSets)
        assertEquals(3, written.targetSets)
    }

    @Test
    fun aOneOffRetagIsOfferedUnticked() = runBlocking {
        val rite = save(row, 3, 8, 60.0, SetScheme())
        val session = repo.startSessionFromPreset(rite)
        val sets = setsOf(session, row)
        repo.setKind(sets[2].id, SetKind.FAILURE)
        sets.forEach { repo.updateSet(it.id, it.reps, it.weightKg, done = true) }
        repo.completeSession(session)
        val change = repo.routineUpdateFor(session)!!.changes.single()
        assertEquals(listOf(RoutineUpdate.Field.SCHEME), change.fields)
        assertTrue(change.defaultTicks().isEmpty())
    }

    @Test
    fun aDropAddedToTheLastSetFollowsItAndRestWaitsForTheEndOfTheChain() = runBlocking {
        val rite = save(row, 3, 10, 40.0, SetScheme())
        val session = repo.startSessionFromPreset(rite)
        val last = setsOf(session, row).last()
        assertTrue(repo.addDropAfter(last.id))
        val sets = setsOf(session, row)
        assertEquals(listOf(SetKind.WORKING, SetKind.WORKING, SetKind.WORKING, SetKind.DROP), sets.map { it.kind })
        assertEquals(listOf(0, 1, 2, 3), sets.map { it.setIndex })
        // 20% lighter than 40 is 32, rounded down onto the 2.5 kg grid.
        assertEquals(30.0, sets.last().weightKg!!, 0.0)
        // A second drop continues the chain from the first.
        assertTrue(repo.addDropAfter(sets.last().id))
        assertEquals(4, setsOf(session, row).last().setIndex)
        // "End drops" takes out the drops still to do.
        repo.endDropsAfter(last.id)
        assertEquals(List(3) { SetKind.WORKING }, setsOf(session, row).map { it.kind })
    }

    @Test
    fun aSupersetTakesNoDrops() = runBlocking {
        val other = db.exerciseDao().byName("Bench Press")!!.id
        val rite = repo.savePreset(
            null, "Superset day", "", null,
            listOf(
                Repository.PresetDraftEntry(row, 3, 8, 40.0, supersetGroup = 1),
                Repository.PresetDraftEntry(other, 3, 8, 40.0, supersetGroup = 1),
            ),
        )
        val session = repo.startSessionFromPreset(rite)
        assertFalse(repo.addDropAfter(setsOf(session, row).last().id))
        assertEquals(3, setsOf(session, row).size)
    }

    @Test
    fun theForgeKeepsHowALiftIsRunWhenItRewritesTheRite() = runBlocking {
        val rite = save(row, 3, 4, 80.0, topBackoff)
        val planned = com.ironvellum.app.domain.PlannedPreset(
            name = "Pull day", note = "", scheduledDay = null,
            entries = listOf(com.ironvellum.app.domain.PlannedEntry(exerciseName = "Barbell Row", sets = 4, reps = 4, targetWeightKg = 85.0)),
        )
        repo.savePlannedPreset(rite, planned)
        val written = repo.observePresets().first().single().entries.single()
        assertEquals(SchemeType.TOP_BACKOFF, written.scheme.type)
        // The scheme was fitted to the Forge's four sets: a top and three back-offs.
        assertEquals(3, written.scheme.backoffSets)
        assertEquals(85.0, written.targetWeightKg!!, 0.0)
    }

    @Test
    fun aSchemeSurvivesTheArchive() = runBlocking {
        save(pullUp, 4, 2, 22.0, owners, "weighted")
        val json = repo.exportJson()
        assertTrue(repo.importArchive(json).isSuccess)
        assertEquals(owners, repo.observePresets().first().single().entries.single().scheme)
    }

    private companion object {
        const val TEST_DB = "set_scheme_trial_test.db"
    }
}
