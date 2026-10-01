package com.ironvellum.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.data.db.GachaStateEntity
import com.ironvellum.app.domain.SealedEdit
import com.ironvellum.app.domain.Sex
import com.ironvellum.app.domain.TrialDraft
import com.ironvellum.app.domain.Xp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Amending a sealed trial is a LEDGER operation like deleting one: the sets
 * change, and the XP, the strength score and the lifetime sum must follow
 * them by exactly the settled amount, in one transaction, without ever paying
 * inscriptions or taking a deed back.
 */
@RunWith(AndroidJUnit4::class)
class EditSealedTrialTest {

    private lateinit var db: IronvellumDatabase
    private lateinit var repo: Repository

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(TEST_DB)
        db = IronvellumDatabase.create(context, TEST_DB)
        repo = Repository(db)
        repo.ensureSeeded()
        repo.addStat(weightKg = 80.0, bodyFatPct = 14.0)
    }

    @After
    fun tearDown() {
        db.close()
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(TEST_DB)
    }

    private suspend fun exerciseId(name: String) =
        db.exerciseDao().observeAll().first().first { it.name == name }.id

    private suspend fun exercise(name: String) = repo.observeExercises().first().first { it.name == name }

    /** A sealed trial of [sets] ticked bench press sets, 8 x 60 kg each. */
    private suspend fun sealedBench(sets: Int = 3): Long {
        val id = repo.startFreeformSession("Bench")
        val bench = exerciseId("Bench Press")
        repeat(sets) { repo.addExtraSet(id, bench, reps = 8, weightKg = 60.0, modifiers = "") }
        db.sessionDao().setsFor(id).forEach { repo.updateSet(it.id, reps = 8, weightKg = 60.0, done = true) }
        repo.completeSession(id)
        return id
    }

    private suspend fun draftOf(id: Long): TrialDraft =
        TrialDraft.of(repo.observeHistory().first().first { it.first.id == id }.second)

    private suspend fun totalXp() = db.profileDao().get()!!.totalXp
    private suspend fun lifetime() = db.profileDao().get()!!.lifetimeStrength
    private suspend fun rolls() = db.gachaDao().get()?.rolls ?: 0

    @Test
    fun swappingTheMovementSettlesTheXpAndRescoresStrength() = runBlocking {
        val id = sealedBench()
        val before = db.sessionDao().byId(id)!!
        val xp = totalXp()
        val plank = exercise("Plank")
        val draft = draftOf(id).swapExercise(0, exercise("Bench Press"), plank)

        val result = repo.editSealedTrial(id, draft)

        val after = db.sessionDao().byId(id)!!
        assertNotEquals("the swap must move XP", 0, result.settlement.applied)
        assertEquals(before.xpAwarded + result.settlement.applied, after.xpAwarded)
        assertEquals("the ledger moves by exactly what the trial moved", xp + result.settlement.applied, totalXp())
        val rows = db.sessionDao().setsFor(id)
        assertTrue(rows.all { it.exerciseId == plank.id && it.reps == 0 && (it.durationSec ?: 0) > 0 })
        val expected = SessionScoring(db.exerciseDao().observeAll().first()).strength(rows, 80.0, Sex.MALE)
        assertEquals("strength is rescored from the new sets", expected, after.strengthScore)
        assertNotEquals(before.strengthScore, after.strengthScore)
        assertEquals(
            "the lifetime sum follows",
            db.sessionDao().observeCompletedWithSets().first().sumOf { it.session.strengthScore.toLong() },
            lifetime(),
        )
        assertNotNull("the trial is stamped amended", after.editedAtMs)
    }

    @Test
    fun removingTheBestSetRenumbersTheRestGapFree() = runBlocking {
        val id = sealedBench(sets = 3)
        // Set 2 becomes the peak, then is taken back out.
        val raised = draftOf(id).updateSet(0, 1) { it.copy(weightKg = 100.0) }
        repo.editSealedTrial(id, raised)
        repo.editSealedTrial(id, draftOf(id).removeSet(0, 1))

        val rows = db.sessionDao().setsFor(id).sortedBy { it.setIndex }
        assertEquals(listOf(0, 1), rows.map { it.setIndex })
        assertTrue(rows.all { it.exercisePosition == 0 })
        assertTrue("the removed peak is gone", rows.none { it.weightKg == 100.0 })
    }

    @Test
    fun aCutBelowALevelDropsTheLevelPaysNoRollsAndNeverGoesNegative() = runBlocking {
        val id = sealedBench(sets = 3)
        // Park the ledger just over the first level threshold.
        val threshold = (1L..10_000L).first { Xp.levelFor(it) > Xp.levelFor(it - 1) }
        db.profileDao().addXp(threshold + 5 - totalXp())
        db.gachaDao().upsert((db.gachaDao().get() ?: GachaStateEntity()).copy(rolls = 2))
        val levelBefore = Xp.levelFor(totalXp())

        val cut = draftOf(id).let { d -> d.updateSet(0, 1) { it.copy(done = false) }.updateSet(0, 2) { it.copy(done = false) } }
        val result = repo.editSealedTrial(id, cut)

        assertTrue(result.settlement.applied < 0)
        assertTrue(totalXp() >= 0)
        assertTrue("the level may fall", Xp.levelFor(totalXp()) <= levelBefore)
        assertEquals("no amendment touches inscriptions", 2, rolls())

        // Spend the ledger to nothing: a further cut clamps at zero.
        db.profileDao().addXp(-totalXp())
        repo.editSealedTrial(id, draftOf(id).updateSet(0, 0) { it.copy(reps = 1) })
        assertEquals(0L, totalXp())
        assertEquals(2, rolls())
    }

    @Test
    fun aRaiseAfterTheWindowIsRefusedButTheSetsStillChange() = runBlocking {
        val id = sealedBench(sets = 2)
        val before = db.sessionDao().byId(id)!!
        val xp = totalXp()
        val late = before.completedAtMs!! + SealedEdit.WINDOW_MS + 60_000

        val result = repo.editSealedTrial(id, draftOf(id).updateSet(0, 0) { it.copy(reps = 20) }, nowMs = late)

        assertTrue(result.settlement.raiseRefused)
        assertEquals(0, result.settlement.applied)
        assertEquals(before.xpAwarded, db.sessionDao().byId(id)!!.xpAwarded)
        assertEquals(xp, totalXp())
        assertTrue("no deed is evaluated after the window", result.newTitles.isEmpty())
        assertEquals(20, db.sessionDao().setsFor(id).minBy { it.setIndex }.reps)
        assertEquals(late, db.sessionDao().byId(id)!!.editedAtMs)
    }

    @Test
    fun repeatedRaisesNeverTakeTheTrialPastDoubleWhatItWasSealedAt() = runBlocking {
        val id = sealedBench(sets = 2)
        val sealed = db.sessionDao().byId(id)!!.xpAwarded
        val xp = totalXp()
        repo.editSealedTrial(id, draftOf(id).updateSet(0, 0) { it.copy(reps = 60, weightKg = 140.0) })
        repo.editSealedTrial(id, draftOf(id).updateSet(0, 1) { it.copy(reps = 60, weightKg = 140.0) })
        val after = db.sessionDao().byId(id)!!
        assertEquals("the cap's base is the sealed figure", sealed, after.sealedXp)
        assertTrue("never past double", after.xpAwarded <= 2 * sealed)
        assertEquals(xp + (after.xpAwarded - sealed), totalXp())
    }

    @Test
    fun anAmendmentNeedsAtLeastOneTickedSet() = runBlocking {
        val id = sealedBench(sets = 2)
        val before = db.sessionDao().setsFor(id)
        val xp = totalXp()
        val none = draftOf(id).let { d -> d.updateSet(0, 0) { it.copy(done = false) }.updateSet(0, 1) { it.copy(done = false) } }

        val refused = runCatching { repo.editSealedTrial(id, none) }

        assertTrue(refused.isFailure)
        assertEquals("nothing is written", before, db.sessionDao().setsFor(id))
        assertEquals(xp, totalXp())
        assertNull(db.sessionDao().byId(id)!!.editedAtMs)
    }

    @Test
    fun theLiveSetEditorsRefuseASealedTrial() = runBlocking {
        val id = sealedBench(sets = 1)
        val set = db.sessionDao().setsFor(id).single()
        repo.updateSet(set.id, reps = 50, weightKg = 200.0, done = true)
        repo.updateHoldSet(set.id, seconds = 90, weightKg = null, done = true)
        repo.updateActivitySet(set.id, 3, 60, 100.0, "V2", null, done = true)
        assertEquals(set, db.sessionDao().setsFor(id).single())
    }

    private companion object {
        /** Never the app's live database. */
        const val TEST_DB = "edit_sealed_trial_test.db"
    }
}
