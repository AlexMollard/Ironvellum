package com.monarch.app.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.monarch.app.data.db.SessionEntity
import com.monarch.app.domain.MuscleGroup
import com.monarch.app.domain.Sex
import com.monarch.app.domain.StrengthIndex
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `StrengthIndex.sessionScore` needs a bodyweight, so a session completed
 * before the hunter's FIRST-EVER weigh-in used to bank `strengthScore = 0`
 * permanently — the strength was lifted and the ledger never saw it.
 *
 * `rescoreUnweighedSessions` now fills those in (and only those) from the
 * `addStat` / `ensureSeeded` paths. These tests pin the behaviours that make
 * the repair honest: unweighed lifting gets scored late, activity work
 * stays at zero, banked XP is never restated, and already-scored sessions are
 * never touched again — plus the formula-change restatement, which rewrites
 * EVERY completed session exactly once under the profile.scoringVersion
 * marker, then never again.
 */
@RunWith(AndroidJUnit4::class)
class UnweighedSessionRescoreTest {

    private lateinit var context: Context
    private lateinit var db: MonarchDatabase
    private lateinit var repo: Repository

    @Before
    fun setUp() = runBlocking {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(TEST_DB)
        db = MonarchDatabase.create(context, TEST_DB)
        repo = Repository(db)
        repo.ensureSeeded()
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(TEST_DB)
    }

    private suspend fun seedSessionWithOneDoneSet(): Long {
        val exercise = db.exerciseDao().observeAll().first().first { it.metric == "REPS" }
        val id = db.sessionDao().insertSession(
            SessionEntity(
                presetId = null,
                label = "Lifted before any weigh-in",
                startedAtMs = System.currentTimeMillis() - 600_000L,
                completedAtMs = null,
                xpAwarded = 0,
                strengthScore = 0,
                title = "",
                note = "",
                privateNote = "",
            ),
        )
        repo.addExtraSet(id, exercise.id, 8, 60.0, "")
        val set = db.sessionDao().setsFor(id).first()
        repo.updateSet(set.id, reps = 8, weightKg = 60.0, done = true)
        return id
    }

    private suspend fun lifetimeStrengthSum() =
        db.sessionDao().observeCompletedWithSets().first().sumOf { it.session.strengthScore.toLong() }

    @Test
    fun aLiftingSessionCompletedBeforeTheFirstWeighInIsScoredWhenOneArrives() = runBlocking {
        val id = seedSessionWithOneDoneSet()
        repo.completeSession(id)
        assertEquals(
            "no bodyweight on record, so the session must bank zero",
            0,
            db.sessionDao().byId(id)!!.strengthScore,
        )

        repo.addStat(weightKg = 82.5, bodyFatPct = 14.0)

        val rescored = db.sessionDao().byId(id)!!.strengthScore
        assertTrue(
            "the first weigh-in must backfill the strength the session earned, got $rescored",
            rescored > 0,
        )
        // The lifetime figure is recomputed as the sum, not nudged by a delta.
        assertEquals(lifetimeStrengthSum(), db.profileDao().get()!!.lifetimeStrength)
    }

    @Test
    fun anActivityOnlySessionStillReadsZeroAfterAWeighIn() = runBlocking {
        val exercise = db.exerciseDao().observeAll().first()
            .first { it.metric == "ATTEMPTS_GRADE" || it.metric == "DISTANCE_TIME" }
        val id = db.sessionDao().insertSession(
            SessionEntity(
                presetId = null,
                label = "Climb, not a lift",
                startedAtMs = System.currentTimeMillis() - 600_000L,
                completedAtMs = null,
                xpAwarded = 0,
                strengthScore = 0,
                title = "",
                note = "",
                privateNote = "",
            ),
        )
        val setId = repo.addExtraSet(id, exercise.id, 3, null, "")
        repo.updateActivitySet(
            setId = setId,
            reps = 3,
            durationSec = null,
            distanceM = null,
            grade = "V5",
            weightKg = null,
            done = true,
        )
        repo.completeSession(id)
        assertEquals(0, db.sessionDao().byId(id)!!.strengthScore)

        // An unscored LIFTING session alongside it, or the repair's own guard
        // query short-circuits on a database with nothing to repair and this
        // test passes without the activity filter ever being exercised.
        val lift = seedSessionWithOneDoneSet()
        repo.completeSession(lift)

        repo.addStat(weightKg = 82.5, bodyFatPct = 14.0)

        assertTrue(
            "the repair must actually have run, or this test proves nothing",
            db.sessionDao().byId(lift)!!.strengthScore > 0,
        )
        assertEquals(
            "a climb or a run scores zero honestly; the repair must not fabricate strength",
            0,
            db.sessionDao().byId(id)!!.strengthScore,
        )
        assertEquals(lifetimeStrengthSum(), db.profileDao().get()!!.lifetimeStrength)
    }

    @Test
    fun theWeighInDoesNotRestateTheXpTheSessionWasAwarded() = runBlocking {
        val id = seedSessionWithOneDoneSet()
        val paid = repo.completeSession(id)
        assertTrue("the fixture must have banked XP", paid.xpAwarded > 0)
        val xpBefore = db.profileDao().get()!!.totalXp

        repo.addStat(weightKg = 82.5, bodyFatPct = 14.0)

        assertEquals(
            "the rescore fills in strength only; banked XP is never revisited",
            paid.xpAwarded,
            db.sessionDao().byId(id)!!.xpAwarded,
        )
        assertEquals(xpBefore, db.profileDao().get()!!.totalXp)
    }

    @Test
    fun aSessionScoredAtCompletionIsNotDisturbedByALaterWeighIn() = runBlocking {
        repo.addStat(weightKg = 80.0, bodyFatPct = 14.0)
        val id = seedSessionWithOneDoneSet()
        repo.completeSession(id)
        val banked = db.sessionDao().byId(id)!!.strengthScore
        assertTrue("a weighed session must score at completion", banked > 0)
        val lifetimeBefore = db.profileDao().get()!!.lifetimeStrength

        repo.addStat(weightKg = 85.0, bodyFatPct = 13.0)

        assertEquals(
            "only unscored sessions are in scope; a banked score must stay exactly as banked",
            banked,
            db.sessionDao().byId(id)!!.strengthScore,
        )
        assertEquals(lifetimeBefore, db.profileDao().get()!!.lifetimeStrength)
    }

    /**
     * The formula changed: EVERY completed session must be restated from its
     * stored sets, not just the zero-score ones, and the lifetime sum must
     * follow. Emulates the shipped state — scored under the old formula, the
     * marker never stamped — by resetting the profile marker and planting an
     * old-formula score the current formula would never pay.
     */
    @Test
    fun aFormulaChangeRestatesEveryScoredSessionAndStampsTheVersion() = runBlocking {
        repo.addStat(weightKg = 82.5, bodyFatPct = 14.0)
        val id = seedSessionWithOneDoneSet()
        repo.completeSession(id)
        val exercise = db.exerciseDao().observeAll().first().first { it.metric == "REPS" }
        val fresh = StrengthIndex.sessionScore(
            listOf(
                StrengthIndex.Effort(
                    exercise.name,
                    8,
                    null,
                    60.0,
                    MuscleGroup.valueOf(exercise.muscleGroup),
                ),
            ),
            82.5,
            Sex.MALE,
        )!!
        assertTrue("the fixture must score under the current formula", fresh > 0)

        // Shipped state: scores banked under the old formula, marker unstamped.
        db.profileDao().upsert(db.profileDao().get()!!.copy(scoringVersion = 0))
        db.sessionDao().updateSession(db.sessionDao().byId(id)!!.copy(strengthScore = 999_999))
        db.profileDao().setLifetimeStrength(999_999L)

        repo.ensureSeeded()

        assertEquals(
            "the old-formula score must be replaced by a freshly computed one",
            fresh,
            db.sessionDao().byId(id)!!.strengthScore,
        )
        assertEquals(
            "the lifetime figure must be the fresh sum, not the stale total",
            lifetimeStrengthSum(),
            db.profileDao().get()!!.lifetimeStrength,
        )
        assertEquals(
            "the marker must be stamped after the restatement",
            StrengthIndex.SCORING_VERSION,
            db.profileDao().get()!!.scoringVersion,
        )
    }

    /**
     * The marker is the whole point: with versions already matched, the
     * rescore-all must NOT run — a planted wrong score stays untouched,
     * proving no per-launch restatement is hiding behind ensureSeeded.
     */
    @Test
    fun theRescoreAllDoesNotRunAgainOnceVersionsMatch() = runBlocking {
        repo.addStat(weightKg = 82.5, bodyFatPct = 14.0)
        val id = seedSessionWithOneDoneSet()
        repo.completeSession(id)
        assertEquals(
            "setUp's ensureSeeded must already have stamped the marker",
            StrengthIndex.SCORING_VERSION,
            db.profileDao().get()!!.scoringVersion,
        )
        db.sessionDao().updateSession(db.sessionDao().byId(id)!!.copy(strengthScore = 555))
        db.profileDao().setLifetimeStrength(555L)

        repo.ensureSeeded()

        assertEquals(
            "a stamped marker means the stored scores are current; nothing may rewrite them",
            555,
            db.sessionDao().byId(id)!!.strengthScore,
        )
        assertEquals(555L, db.profileDao().get()!!.lifetimeStrength)
        assertEquals(StrengthIndex.SCORING_VERSION, db.profileDao().get()!!.scoringVersion)
    }

    private companion object {
        const val TEST_DB = "unweighed_rescore_test.db"
    }
}
