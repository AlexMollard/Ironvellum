package com.monarch.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.monarch.app.data.db.SyncStateEntity
import com.monarch.app.domain.ExerciseMetric
import com.monarch.app.domain.isStrength
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
 * DELETE in the full log is a LEDGER operation, not a row delete: the workout
 * paid XP and strength when it was completed, so deleting it must take both
 * back out, remove its sets, and drop its push watermark. These tests pin each
 * of those directions, because a delete that only removed the row would leave
 * a hunter paid for work the record no longer shows.
 */
@RunWith(AndroidJUnit4::class)
class DeleteWorkoutTest {

    private lateinit var db: MonarchDatabase
    private lateinit var repo: Repository

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(TEST_DB)
        db = MonarchDatabase.create(context, TEST_DB)
        repo = Repository(db)
        repo.ensureSeeded()
        repo.addStat(weightKg = 80.0, bodyFatPct = 14.0)
    }

    @After
    fun tearDown() {
        db.close()
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(TEST_DB)
    }

    /** A completed session with one done lift, returning its id. */
    private suspend fun completedSessionWithOneLift(): Long {
        val preset = db.presetDao().observePresets().first().first().preset
        val id = repo.startSessionFromPreset(preset.id)
        val set = db.sessionDao().setsFor(id).first()
        repo.updateSet(set.id, reps = 8, weightKg = 20.0, done = true)
        repo.completeSession(id)
        return id
    }

    private suspend fun totalXp() = db.profileDao().get()!!.totalXp
    private suspend fun lifetime() = db.profileDao().get()!!.lifetimeStrength

    @Test
    fun deletingARemovedWorkoutTakesItsXpAndStrengthBackOut() = runBlocking {
        val id = completedSessionWithOneLift()
        val session = db.sessionDao().byId(id)!!
        val xp = totalXp()
        val strength = lifetime()
        assertTrue("fixture must have paid XP", session.xpAwarded > 0)
        assertTrue("fixture must have banked strength", session.strengthScore > 0)

        repo.deleteWorkout(id)

        assertNull("the session row must be gone", db.sessionDao().byId(id))
        assertEquals(
            "the session's sets must go with it",
            0,
            db.sessionDao().setsFor(id).size,
        )
        assertEquals(
            "the deleted workout's XP must leave the ledger",
            xp - session.xpAwarded,
            totalXp(),
        )
        assertEquals(
            "the deleted workout's strength must leave the lifetime sum",
            strength - session.strengthScore,
            lifetime(),
        )
    }

    @Test
    fun deletingTheWatermarkLetsARestoredSessionRepush() = runBlocking {
        val id = completedSessionWithOneLift()
        // A pushed session carries a fingerprint; the delete must not leave one
        // behind pointing at a row that no longer exists.
        db.syncStateDao().upsertAll(listOf(SyncStateEntity(sessionId = id, fingerprint = 12345)))
        repo.deleteWorkout(id)
        assertTrue(
            "the watermark must go with the session",
            db.syncStateDao().all().none { it.sessionId == id },
        )
    }

    @Test
    fun xpCannotBeDrivenNegativeByDeletingMoreThanTheLedgerHolds() = runBlocking {
        val id = completedSessionWithOneLift()
        val session = db.sessionDao().byId(id)!!
        // Spend the ledger down below the workout's worth before deleting.
        db.profileDao().addXp(-totalXp())
        assertEquals(0L, totalXp())

        repo.deleteWorkout(id)

        assertEquals(
            "a clamped subtraction must not push the ledger negative",
            0L,
            totalXp(),
        )
        assertTrue(session.xpAwarded > 0)
    }

    @Test
    fun deletingAnotherWorkoutLeavesTheRestOfTheLogStanding() = runBlocking {
        val first = completedSessionWithOneLift()
        val second = completedSessionWithOneLift()
        assertEquals(2, db.sessionDao().completedCount())

        repo.deleteWorkout(first)

        assertEquals(1, db.sessionDao().completedCount())
        assertEquals(
            "the surviving workout keeps its row",
            second,
            db.sessionDao().observeCompletedWithSets().first().single().session.id,
        )
    }

    @Test
    fun aLiveSessionIsNotDeletedByTheWorkoutPath() = runBlocking {
        val preset = db.presetDao().observePresets().first().first().preset
        val live = repo.startSessionFromPreset(preset.id)
        val refused = runCatching { repo.deleteWorkout(live) }
        assertTrue("a live session must go through abandon, not delete", refused.isFailure)
        assertEquals(live, db.sessionDao().liveSession()?.id)
        // And the metric sanity the fixture leans on elsewhere.
        assertTrue(ExerciseMetric.REPS.isStrength)
    }

    private companion object {
        /** Never the app's live database. */
        const val TEST_DB = "delete_workout_test.db"
    }
}
