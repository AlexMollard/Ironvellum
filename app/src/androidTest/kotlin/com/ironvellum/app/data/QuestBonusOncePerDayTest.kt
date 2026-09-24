package com.ironvellum.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.data.db.SessionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * The daily quest bonus is paid once per preset per day. Repeating the same
 * day's preset used to pay the +25 again on every completion — and each payout
 * re-ran the level-up check, minting extra inscription rolls. That is the
 * exact farming path the economy rules forbid.
 */
@RunWith(AndroidJUnit4::class)
class QuestBonusOncePerDayTest {

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

    private suspend fun saveTodayPreset(name: String): Long =
        repo.savePreset(
            presetId = null,
            name = name,
            note = "",
            scheduledDay = LocalDate.now().dayOfWeek.value,
            entries = db.exerciseDao().observeAll().first()
                .filter { it.metric == "REPS" }
                .take(1)
                .map { it.id }
                .map { id ->
                    Repository.PresetDraftEntry(exerciseId = id, targetSets = 1, targetReps = 5, targetWeightKg = 40.0)
                },
        )

    /** A live session of [presetId] with its single set marked done. */
    private suspend fun startDoneSession(presetId: Long): Long {
        val sessionId = repo.startSessionFromPreset(presetId)
        val set = db.sessionDao().setsFor(sessionId).first()
        repo.updateSet(set.id, reps = 5, weightKg = 40.0, done = true)
        return sessionId
    }

    @Test
    fun repeatingTodaysPresetPaysTheBonusOnce() = runBlocking {
        val presetId = saveTodayPreset("Quest day")

        val first = repo.completeSession(startDoneSession(presetId))
        assertTrue(
            "the first scheduled-day completion must pay the quest bonus",
            first.questBonus,
        )
        val xpAfterFirst = repo.observeProfile().first()!!.totalXp

        // Same preset again, straight away: the session is new, so the
        // double-completion check does not fire — the bonus must.
        val second = repo.completeSession(startDoneSession(presetId))
        assertFalse(
            "a repeated scheduled-day preset must not pay the quest bonus again",
            second.questBonus,
        )
        assertEquals(
            "the second completion pays only the work XP",
            xpAfterFirst + second.xpAwarded,
            repo.observeProfile().first()!!.totalXp,
        )
    }

    @Test
    fun aDifferentScheduledPresetStillPaysItsOwnBonus() = runBlocking {
        val morning = saveTodayPreset("Morning quest")
        val evening = saveTodayPreset("Evening quest")

        assertTrue(repo.completeSession(startDoneSession(morning)).questBonus)
        assertTrue(
            "a different preset scheduled for today earns its own once-per-day bonus",
            repo.completeSession(startDoneSession(evening)).questBonus,
        )
    }

    @Test
    fun aCompletionWithNoConqueredSetsPaysNoBonus() = runBlocking {
        val presetId = saveTodayPreset("Quest day")
        // Started, but every set left unticked: the claim is a tap-through.
        val sessionId = repo.startSessionFromPreset(presetId)

        val result = repo.completeSession(sessionId)
        assertFalse(
            "an empty-handed completion must not pay the quest bonus",
            result.questBonus,
        )
    }

    @Test
    fun completingThePresetOnAnotherDayPaysAgain() = runBlocking {
        val presetId = saveTodayPreset("Quest day")
        assertTrue(repo.completeSession(startDoneSession(presetId)).questBonus)

        // Simulate yesterday's completed session of the same preset: the guard
        // counts completions since the day started, so it must not see this.
        db.sessionDao().insertSession(
            SessionEntity(
                presetId = presetId,
                label = "Yesterday",
                startedAtMs = System.currentTimeMillis() - 86_400_000L,
                completedAtMs = System.currentTimeMillis() - 43_200_000L,
                xpAwarded = 10,
                strengthScore = 0,
                title = "",
                note = "",
                privateNote = "",
            ),
        )
        val today = repo.completeSession(startDoneSession(presetId))
        // The preset above is already completed today, so this must NOT pay.
        assertFalse(today.questBonus)
    }

    companion object {
        private const val TEST_DB = "quest-bonus-test.db"
    }
}
