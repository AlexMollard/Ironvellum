package com.ironvellum.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.data.db.SyncStateEntity
import com.ironvellum.app.domain.SealedReopen
import com.ironvellum.app.domain.TrialDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * "Sealed too soon? Keep going" is a LEDGER operation: it must undo exactly
 * what the seal paid, so that sealing again lands the ledger where a single
 * seal would have, with the quest bonus, the inscriptions and the deeds paid
 * once and only once.
 */
@RunWith(AndroidJUnit4::class)
class ReopenSealedTrialTest {

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

    private suspend fun totalXp() = db.profileDao().get()!!.totalXp
    private suspend fun lifetime() = db.profileDao().get()!!.lifetimeStrength
    private suspend fun rolls() = db.gachaDao().get()?.rolls ?: 0
    private suspend fun deeds() = db.titleDao().heldIds().toSet()

    /** A live trial of today's scheduled rite with its one set ticked: sealing it pays the quest bonus. */
    private suspend fun doneTodayTrial(): Long {
        val exerciseId = db.exerciseDao().observeAll().first().first { it.metric == "REPS" }.id
        val presetId = repo.savePreset(
            presetId = null,
            name = "Quest day",
            note = "",
            scheduledDay = LocalDate.now().dayOfWeek.value,
            entries = listOf(
                Repository.PresetDraftEntry(exerciseId = exerciseId, targetSets = 1, targetReps = 5, targetWeightKg = 40.0),
            ),
        )
        val id = repo.startSessionFromPreset(presetId)
        val set = db.sessionDao().setsFor(id).first()
        repo.updateSet(set.id, reps = 5, weightKg = 40.0, done = true)
        return id
    }

    @Test
    fun sealReopenSealAgainEndsWhereASingleSealWould() = runBlocking {
        val id = doneTodayTrial()
        val xpBefore = totalXp()
        val lifetimeBefore = lifetime()

        val first = repo.completeSession(id)
        val xpSealed = totalXp()
        val lifetimeSealed = lifetime()
        val rollsSealed = rolls()
        val deedsSealed = deeds()
        assertTrue("the setup must pay the quest bonus", first.questBonus)
        assertTrue("the setup must pay something", xpSealed > xpBefore && lifetimeSealed > lifetimeBefore)

        val decision = repo.reopenSealedTrial(id)

        assertEquals(SealedReopen.Decision.Reopen(first.xpAwarded, first.strengthScore), decision)
        val live = db.sessionDao().byId(id)!!
        assertNull(live.completedAtMs)
        assertEquals(0, live.xpAwarded)
        assertEquals(0, live.strengthScore)
        assertNull(live.sealedXp)
        assertEquals("the XP it paid is taken back", xpBefore, totalXp())
        assertEquals("and the strength", lifetimeBefore, lifetime())
        assertEquals("the trial is the live one again", id, db.sessionDao().liveSession()?.id)
        assertEquals(
            "the cloud row is queued for removal until the re-seal uploads it",
            listOf(id),
            db.syncStateDao().tombstoned(),
        )
        assertEquals("deeds are never revoked", deedsSealed, deeds())
        assertEquals("inscriptions are never taken back", rollsSealed, rolls())

        val second = repo.completeSession(id)

        assertEquals("the same XP, quest bonus paid once", first.xpAwarded, second.xpAwarded)
        assertTrue(second.questBonus)
        assertEquals(xpSealed, totalXp())
        assertEquals(lifetimeSealed, lifetime())
        assertEquals("a level paid once is not paid twice", rollsSealed, rolls())
        assertEquals(deedsSealed, deeds())
        val sealedAgain = db.sessionDao().byId(id)!!
        assertNotNull(sealedAgain.completedAtMs)
        assertEquals(first.xpAwarded, sealedAgain.xpAwarded)
        assertEquals(first.strengthScore, sealedAgain.strengthScore)
    }

    @Test
    fun aPushThatFinishesAfterTheReopenDoesNotForgetTheTombstone() = runBlocking {
        val id = doneTodayTrial()
        repo.completeSession(id)
        repo.reopenSealedTrial(id)

        repo.recordPushWatermark(mapOf(id to 42))

        assertEquals(listOf(id), db.syncStateDao().tombstoned())
        assertEquals(SyncStateEntity.TOMBSTONE, db.syncStateDao().all().single { it.sessionId == id }.fingerprint)
    }

    @Test
    fun aClosedWindowRefusesAndChangesNothing() = runBlocking {
        val id = doneTodayTrial()
        repo.completeSession(id)
        val completedAt = db.sessionDao().byId(id)!!.completedAtMs!!
        val xp = totalXp()
        val strength = lifetime()

        val late = repo.reopenSealedTrial(id, nowMs = completedAt + SealedReopen.WINDOW_MS + 1)

        assertEquals(SealedReopen.Decision.Refuse(SealedReopen.Refusal.WINDOW_CLOSED), late)
        assertEquals(completedAt, db.sessionDao().byId(id)!!.completedAtMs)
        assertEquals(xp, totalXp())
        assertEquals(strength, lifetime())
        assertTrue(db.syncStateDao().tombstoned().isEmpty())
    }

    @Test
    fun aTrialStillLiveHasNothingToReopen() = runBlocking {
        val id = doneTodayTrial()

        assertEquals(SealedReopen.Decision.Refuse(SealedReopen.Refusal.NOT_SEALED), repo.reopenSealedTrial(id))
    }

    @Test
    fun anAmendedTrialIsRefused() = runBlocking {
        val id = doneTodayTrial()
        repo.completeSession(id)
        val draft = TrialDraft.of(repo.observeHistory().first().first { it.first.id == id }.second)
        repo.editSealedTrial(id, draft.updateSet(0, 0) { it.copy(reps = 6) })
        val xp = totalXp()

        assertEquals(SealedReopen.Decision.Refuse(SealedReopen.Refusal.AMENDED), repo.reopenSealedTrial(id))
        assertNotNull(db.sessionDao().byId(id)!!.completedAtMs)
        assertEquals(xp, totalXp())
    }

    @Test
    fun anotherLiveTrialRefuses() = runBlocking {
        val id = doneTodayTrial()
        repo.completeSession(id)
        repo.startFreeformSession("Other")

        assertEquals(SealedReopen.Decision.Refuse(SealedReopen.Refusal.ANOTHER_LIVE), repo.reopenSealedTrial(id))
        assertNotNull(db.sessionDao().byId(id)!!.completedAtMs)
    }

    @Test
    fun aLedgerThatCannotPayItBackRefusesInsteadOfClamping() = runBlocking {
        val id = doneTodayTrial()
        repo.completeSession(id)
        db.profileDao().addXp(-totalXp())

        assertEquals(SealedReopen.Decision.Refuse(SealedReopen.Refusal.LEDGER_SHORT), repo.reopenSealedTrial(id))
        assertNotNull(db.sessionDao().byId(id)!!.completedAtMs)
        assertEquals(0L, totalXp())
    }

    private companion object {
        const val TEST_DB = "reopen_sealed_trial_test.db"
    }
}
