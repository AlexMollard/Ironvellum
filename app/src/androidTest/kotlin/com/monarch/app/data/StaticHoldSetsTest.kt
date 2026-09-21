package com.monarch.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.monarch.app.data.db.SessionEntity
import com.monarch.app.domain.ExerciseMetric
import com.monarch.app.domain.StrengthIndex
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
import kotlin.math.roundToInt

/**
 * Static holds are measured in seconds, and those seconds live in
 * `durationSec`. Two failures this pins, both of which silently destroyed
 * data before:
 *
 *  1. `updateSet` took `durationSec`/`distanceM`/`grade` as parameters
 *     defaulting to null and wrote them, while its only caller passed four
 *     arguments — so ticking a set's checkbox erased its seconds, distance
 *     and climbing grade.
 *  2. A hold catalogued as REPS put its seconds in the reps column, where
 *     XP, the strength score, personal records and rep-count titles all read
 *     them as repetitions.
 */
@RunWith(AndroidJUnit4::class)
class StaticHoldSetsTest {

    private lateinit var db: MonarchDatabase
    private lateinit var repo: Repository

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(TEST_DB)
        db = MonarchDatabase.create(context, TEST_DB)
        repo = Repository(db)
        repo.ensureSeeded()
    }

    @After
    fun tearDown() {
        db.close()
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(TEST_DB)
    }

    private suspend fun newSession(): Long = db.sessionDao().insertSession(
        SessionEntity(
            presetId = null,
            label = "Holds",
            startedAtMs = System.currentTimeMillis() - 600_000L,
            completedAtMs = null,
            xpAwarded = 0,
            strengthScore = 0,
            title = "",
            note = "",
            privateNote = "",
        ),
    )

    private suspend fun holdExercise() =
        db.exerciseDao().observeAll().first().first { it.name == "Hollow Hold" }

    private suspend fun repExercise() =
        db.exerciseDao().observeAll().first().first { it.name == "Handstand Push-up" }

    /** The catalogue itself must state that a hold is timed. */
    @Test
    fun seedMarksStaticHoldsAsHoldMetric() = runBlocking {
        assertEquals(ExerciseMetric.HOLD.name, holdExercise().metric)
        assertEquals(ExerciseMetric.HOLD.name, db.exerciseDao().observeAll().first()
            .first { it.name == "L-sit" }.metric)
        assertEquals(ExerciseMetric.REPS.name, repExercise().metric)
    }

    /**
     * The clobber. Ticking a hold's checkbox goes through the counted path,
     * which must leave the seconds alone.
     */
    @Test
    fun tickingASetDoesNotEraseItsSeconds() = runBlocking {
        val sessionId = newSession()
        repo.addExtraSet(sessionId, holdExercise().id, reps = 0, weightKg = null, modifiers = "", durationSec = 45)
        val set = db.sessionDao().setsFor(sessionId).first()
        assertEquals(45, set.durationSec)

        repo.updateSet(set.id, reps = set.reps, weightKg = null, done = true)

        val after = db.sessionDao().setById(set.id)!!
        assertTrue("the tick must land", after.done)
        assertNotNull("ticking must not erase the seconds", after.durationSec)
        assertEquals(45, after.durationSec)
    }

    /** Editing a hold writes seconds, and never puts them back in reps. */
    @Test
    fun editingAHoldWritesSecondsAndLeavesRepsAtZero() = runBlocking {
        val sessionId = newSession()
        repo.addExtraSet(sessionId, holdExercise().id, reps = 0, weightKg = null, modifiers = "", durationSec = 30)
        val set = db.sessionDao().setsFor(sessionId).first()

        repo.updateHoldSet(set.id, seconds = 75, weightKg = 5.0, done = true)

        val after = db.sessionDao().setById(set.id)!!
        assertEquals(75, after.durationSec)
        assertEquals("a hold has no repetitions", 0, after.reps)
        assertEquals(5.0, after.weightKg!!, 0.001)
    }

    /**
     * The headline bug, end to end: 60 seconds of a tier-I hold must not
     * out-earn a hard set, in XP or in strength score. Before the fix the
     * hold was the most lucrative thing in the app.
     */
    @Test
    fun aMinuteOfHoldingScoresFarUnderAHardSet() = runBlocking {
        repo.addStat(weightKg = 80.0, bodyFatPct = null)

        val holdSession = newSession()
        repo.addExtraSet(holdSession, holdExercise().id, reps = 0, weightKg = null, modifiers = "", durationSec = 60)
        db.sessionDao().setsFor(holdSession).forEach {
            repo.updateHoldSet(it.id, seconds = 60, weightKg = null, done = true)
        }
        val holdResult = repo.completeSession(holdSession)

        val repSession = newSession()
        repo.addExtraSet(repSession, repExercise().id, reps = 5, weightKg = null, modifiers = "")
        db.sessionDao().setsFor(repSession).forEach {
            repo.updateSet(it.id, reps = 5, weightKg = null, done = true)
        }
        val repResult = repo.completeSession(repSession)
        assertTrue(
            "60s hold paid ${holdResult.xpAwarded} XP; 5 HSPU paid ${repResult.xpAwarded}",
            holdResult.xpAwarded < repResult.xpAwarded,
        )
        // Weighting is per-movement but the same on both sides of each
        // comparison here (same movement name): the claim is about the unit,
        // a minute is twelve rep-equivalents, not sixty repetitions.
        val asTwelveReps = StrengthIndex.repScore("Hollow Hold", 12, null, 80.0).roundToInt()
        val asSixtyReps = StrengthIndex.repScore("Hollow Hold", 60, null, 80.0).roundToInt()
        assertEquals(
            "a minute must score as twelve rep-equivalents",
            asTwelveReps,
            holdResult.strengthScore,
        )
        // The taper narrows the old 1/5 gap (the first 10 units pay full), so
        // the bound moves to 1/2 — still nowhere near sixty reps.
        assertTrue(
            "and nowhere near the $asSixtyReps the reps column used to give it",
            holdResult.strengthScore < asSixtyReps / 2,
        )
        assertTrue("a hold must still be worth something", holdResult.xpAwarded > 0)
    }

    /** A hold contributes seconds, never reps, to what the app reports. */
    @Test
    fun aCompletedHoldReportsNoRepetitions() = runBlocking {
        val sessionId = newSession()
        repo.addExtraSet(sessionId, holdExercise().id, reps = 0, weightKg = null, modifiers = "", durationSec = 45)
        db.sessionDao().setsFor(sessionId).forEach {
            repo.updateHoldSet(it.id, seconds = 45, weightKg = null, done = true)
        }
        repo.completeSession(sessionId)

        val sets = repo.observeHistory().first().first { it.first.id == sessionId }.second
        assertEquals(1, sets.size)
        assertEquals("its figure is seconds", 45, sets.first().durationSec)
        assertEquals("and not repetitions", 0, sets.first().reps)
    }

    /** A counted movement must be untouched by any of this. */
    @Test
    fun countedSetsStillStoreRepsAndNoDuration() = runBlocking {
        val sessionId = newSession()
        repo.addExtraSet(sessionId, repExercise().id, reps = 6, weightKg = null, modifiers = "")
        val set = db.sessionDao().setsFor(sessionId).first()
        repo.updateSet(set.id, reps = 7, weightKg = null, done = true)

        val after = db.sessionDao().setById(set.id)!!
        assertEquals(7, after.reps)
        assertNull("a counted set gains no duration", after.durationSec)
    }

    private companion object {
        const val TEST_DB = "monarch-static-hold-test.db"
    }
}
