package com.ironvellum.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.domain.ExerciseMetric
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A trial's note on an exercise comes back as "Last time" at the next trial
 * that has the exercise, skipping any trial where it went unannotated.
 */
@RunWith(AndroidJUnit4::class)
class ExerciseNoteLastTimeTest {

    private lateinit var db: IronvellumDatabase
    private lateinit var repo: Repository
    private var squat = 0L
    private var bench = 0L

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(TEST_DB)
        db = IronvellumDatabase.create(context, TEST_DB)
        repo = Repository(db)
        repo.ensureSeeded()
        val lifts = db.exerciseDao().observeAll().first().filter { it.metric == ExerciseMetric.REPS.name }.take(2)
        squat = lifts[0].id
        bench = lifts[1].id
    }

    @After
    fun tearDown() {
        db.close()
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(TEST_DB)
    }

    /** A sealed trial of both lifts, with the given notes written during it. */
    private suspend fun sealedTrial(notes: Map<Long, String>): Long {
        val id = repo.startFreeformSession("Trial")
        listOf(squat, bench).forEach { exercise ->
            repo.addExtraSet(id, exercise, 5, 60.0, "")
            val set = db.sessionDao().setsFor(id).first { it.exerciseId == exercise }
            repo.updateSet(set.id, reps = 5, weightKg = 60.0, done = true)
        }
        notes.forEach { (exercise, note) -> repo.setExerciseNote(id, exercise, note) }
        repo.completeSession(id)
        return id
    }

    private suspend fun lastFor(exerciseId: Long, live: Long) = repo.lastExerciseNotes(live, listOf(exerciseId))[exerciseId]

    @Test
    fun theLastTrialThatLeftANoteSpeaksSkippingTheOnesThatDidNot() = runBlocking {
        sealedTrial(mapOf(squat to "Left shoulder twinged"))
        sealedTrial(mapOf(bench to "Paused reps"))
        sealedTrial(emptyMap())
        val live = repo.startFreeformSession("Live")

        assertEquals("two trials on, the squat note still speaks", "Left shoulder twinged", lastFor(squat, live))
        assertEquals("the bench note is its own", "Paused reps", lastFor(bench, live))
    }

    @Test
    fun theNewestNoteWinsOverAnOlderOne() = runBlocking {
        sealedTrial(mapOf(squat to "Old"))
        sealedTrial(mapOf(squat to "New"))
        val live = repo.startFreeformSession("Live")

        assertEquals("New", lastFor(squat, live))
    }

    @Test
    fun theLiveTrialsOwnNoteIsNeverItsLastTime() = runBlocking {
        val live = repo.startFreeformSession("Live")
        repo.addExtraSet(live, squat, 5, 60.0, "")
        repo.setExerciseNote(live, squat, "Today")

        assertNull(lastFor(squat, live))
    }

    @Test
    fun aBlankNoteClearsItAndIsNotRemembered() = runBlocking {
        val first = sealedTrial(mapOf(squat to "Keep me"))
        repo.setExerciseNote(first, squat, "   ")
        val live = repo.startFreeformSession("Live")

        assertNull(lastFor(squat, live))
        assertEquals(emptyMap<Long, String>(), repo.observeExerciseNotes(first).first())
    }

    @Test
    fun aNoteFollowsItsTrialOutOfTheArchive() = runBlocking {
        sealedTrial(mapOf(squat to "Survives a restore"))
        val archive = repo.exportJson()

        assertEquals(true, repo.importArchive(archive).isSuccess)

        val live = repo.startFreeformSession("Live")
        assertEquals("Survives a restore", lastFor(squat, live))
    }

    private companion object {
        const val TEST_DB = "exercise_note_last_time_test.db"
    }
}
