package com.ironvellum.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.domain.Xp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The developer XP rescore against a real database (synthetic trials only): a rescore prices what the
 * seal priced, moves a trial and the total by the same amounts, never lowers the level, pays no inscription
 * twice, leaves the trial unamended for the cloud push, and a second run changes nothing.
 */
@RunWith(AndroidJUnit4::class)
class XpRescoreRepositoryTest {

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

    private suspend fun sealedBench(): Long {
        val id = repo.startFreeformSession("Bench")
        val bench = db.exerciseDao().observeAll().first().first { it.name == "Bench Press" }.id
        repeat(3) { repo.addExtraSet(id, bench, reps = 8, weightKg = 60.0, modifiers = "") }
        db.sessionDao().setsFor(id).forEach { repo.updateSet(it.id, reps = 8, weightKg = 60.0, done = true) }
        repo.completeSession(id)
        return id
    }

    private suspend fun xpOf(id: Long) = db.sessionDao().byId(id)!!.xpAwarded
    private suspend fun totalXp() = db.profileDao().get()!!.totalXp
    private suspend fun rolls() = db.gachaDao().get()?.rolls ?: 0

    /** Make a trial look as though the old rules had paid [stored] for it, the ledger following by the same amount. */
    private suspend fun storedAs(id: Long, stored: Int) {
        val row = db.sessionDao().byId(id)!!
        db.profileDao().addXp((stored - row.xpAwarded).toLong())
        db.sessionDao().updateSession(row.copy(xpAwarded = stored))
    }

    @Test
    fun aRescoreRepricesWhatTheSealPricedAndMovesTheTotalByTheSameAmount() = runBlocking {
        val a = sealedBench()
        val b = sealedBench()
        val paidA = xpOf(a)
        val paidB = xpOf(b)
        storedAs(a, paidA + 50)
        storedAs(b, paidB + 50)
        val total = totalXp()

        val preview = repo.previewXpRescore()
        assertEquals(listOf(-50, -50), preview.changed.map { it.delta })
        // A preview writes nothing.
        assertEquals(paidA + 50, xpOf(a))
        assertEquals(total, totalXp())

        val applied = repo.applyXpRescore()
        assertEquals(-100L, applied.applied)
        assertEquals(paidA, xpOf(a))
        assertEquals(paidB, xpOf(b))
        assertEquals(total - 100, totalXp())
        // A rescore is not an amendment: the cloud sees a changed trial by its XP alone.
        assertNull(db.sessionDao().byId(a)!!.editedAtMs)
        assertNull(db.sessionDao().byId(a)!!.sealedXp)
    }

    @Test
    fun runningItTwiceChangesNothingTheSecondTime() = runBlocking {
        val a = sealedBench()
        storedAs(a, xpOf(a) + 70)
        repo.applyXpRescore()
        val total = totalXp()
        val xp = xpOf(a)

        val again = repo.previewXpRescore()
        assertTrue(again.changed.isEmpty())
        repo.applyXpRescore()
        assertEquals(total, totalXp())
        assertEquals(xp, xpOf(a))
    }

    @Test
    fun aRecordedSealedFigureMovesWithItsTrial() = runBlocking {
        val a = sealedBench()
        val real = xpOf(a)
        val row = db.sessionDao().byId(a)!!
        db.sessionDao().updateSession(row.copy(sealedXp = real + 50))
        storedAs(a, real + 50)
        repo.applyXpRescore()
        assertEquals(real, xpOf(a))
        assertEquals(real, db.sessionDao().byId(a)!!.sealedXp)
    }

    @Test
    fun theLevelNeverDropsWhateverTheDeltasSum() = runBlocking {
        val a = sealedBench()
        // The old rules paid a great deal more: a rescore would take the lifter many levels down.
        storedAs(a, xpOf(a) + 5_000)
        val level = Xp.levelFor(totalXp())
        val floor = totalXp() - Xp.progress(totalXp()).intoLevel
        val rollsBefore = rolls()

        val preview = repo.previewXpRescore()
        assertTrue(preview.clamped)
        repo.applyXpRescore()
        assertEquals(floor, totalXp())
        assertEquals(level, Xp.levelFor(totalXp()))
        assertEquals(rollsBefore, rolls())
        // The trial itself is still repriced; only the ledger is held.
        assertTrue(xpOf(a) < 5_000)
        assertTrue(repo.previewXpRescore().changed.isEmpty())
    }

    @Test
    fun aRiseThroughLevelsAlreadyPaidMintsNothing() = runBlocking {
        val a = sealedBench()
        val real = xpOf(a)
        val marks = db.gachaDao().get()?.rollLevelMark ?: 0
        val rollsBefore = rolls()
        // The old rules paid nothing for it and the ledger never held it: the rescore lifts the lifter back to
        // levels whose inscriptions the seal has already banked.
        storedAs(a, 0)
        db.profileDao().addXp(-totalXp())
        val applied = repo.applyXpRescore()
        assertEquals(real.toLong(), totalXp())
        assertTrue(applied.levelAfter >= applied.levelBefore)
        assertEquals(rollsBefore, rolls())
        assertEquals(marks, db.gachaDao().get()?.rollLevelMark ?: 0)
    }

    private companion object {
        /** Never the app's live database. */
        const val TEST_DB = "xp_rescore_test.db"
    }
}
