package com.ironvellum.app.data

import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.data.db.SessionEntity
import com.ironvellum.app.data.db.SetLogEntity
import com.ironvellum.app.domain.LoggedLift
import com.ironvellum.app.domain.ProgramRules
import com.ironvellum.app.domain.Variation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger

/**
 * The targeted history reads the full-history load used to answer: each must give what the whole log gave. The
 * fixture has a live session, an unticked set, an unloaded set and an assisted set on purpose, since "completed",
 * "done" and "loaded" are what these queries filter on.
 */
@RunWith(AndroidJUnit4::class)
class HistoryQueriesTest {

    private lateinit var db: IronvellumDatabase
    private lateinit var repo: Repository
    private var squat = 0L
    private var bench = 0L
    private var firstId = 0L
    private var secondId = 0L
    private var liveId = 0L

    @Before
    fun setUp() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(TEST_DB)
        db = IronvellumDatabase.create(context, TEST_DB)
        repo = Repository(db)
        repo.ensureSeeded()
        val byName = db.exerciseDao().observeAll().first().associate { it.name to it.id }
        squat = byName.getValue("Back Squat")
        bench = byName.getValue("Bench Press")
        db.withTransaction {
            // Inserted out of order: the earliest session is not the first row.
            secondId = session("Second", 2_000_000L, 2_600_000L, strength = 300)
            firstId = session("First", 1_000_000L, 1_600_000L, strength = 200)
            liveId = session("Live", 3_000_000L, null, strength = 999)
            db.sessionDao().insertSets(
                listOf(
                    set(secondId, squat, 0, 5, 100.0), set(secondId, bench, 1, 5, 60.0, done = false),
                    set(firstId, squat, 0, 5, 90.0), set(firstId, bench, 1, 8, 62.5), set(firstId, bench, 2, 8, null),
                    set(firstId, bench, 3, 6, 70.0, modifiers = "Assisted"),
                    set(liveId, squat, 0, 5, 200.0),
                ),
            )
        }
    }

    @After
    fun tearDown() {
        db.close()
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(TEST_DB)
    }

    private suspend fun session(label: String, started: Long, completed: Long?, strength: Int) =
        db.sessionDao().insertSession(
            SessionEntity(
                presetId = null, label = label, startedAtMs = started, completedAtMs = completed,
                xpAwarded = 0, strengthScore = strength, title = "", note = "", privateNote = "",
            ),
        )

    private fun set(
        session: Long, exercise: Long, index: Int, reps: Int, kg: Double?, done: Boolean = true, modifiers: String = "",
    ) = SetLogEntity(
        sessionId = session, exerciseId = exercise, exercisePosition = 0, setIndex = index, reps = reps,
        weightKg = kg, modifiers = modifiers, done = done,
    )

    @Test
    fun theFirstSessionDayIsTheEarliestCompletedOne() = runBlocking {
        val expected = Instant.ofEpochMilli(1_000_000L).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
        assertEquals(expected, repo.firstSessionEpochDay())
        assertEquals(1_000_000L, db.sessionDao().firstCompletedStartMs())
        // Nothing completed: no first day, however many sessions are live.
        db.sessionDao().deleteCompleted(firstId)
        db.sessionDao().deleteCompleted(secondId)
        assertNull(repo.firstSessionEpochDay())
        assertNull(db.sessionDao().firstCompletedStartMs())
    }

    @Test
    fun lifetimeStrengthSumsCompletedSessionsOnly() = runBlocking {
        assertEquals(500L, db.sessionDao().lifetimeStrength())
        assertEquals(
            repo.observeHistory().first().sumOf { it.first.strengthScore.toLong() },
            db.sessionDao().lifetimeStrength(),
        )
        db.sessionDao().deleteCompleted(firstId)
        db.sessionDao().deleteCompleted(secondId)
        assertEquals("an empty log sums to zero, not null", 0L, db.sessionDao().lifetimeStrength())
    }

    @Test
    fun theStrengthProfileIsTheOneTheWholeLogGives() = runBlocking {
        val fromLog = ProgramRules.strengthProfile(
            repo.observeHistory().first().flatMap { (_, sets) -> sets }
                .filter { it.done && (it.weightKg ?: 0.0) > 0.0 && !Variation.eases(it.modifiers) }
                .map { LoggedLift(it.exerciseName, it.weightKg!!, it.reps) },
        )
        assertTrue("the fixture must produce a profile", fromLog.bestE1rmKg.isNotEmpty())
        assertEquals(fromLog, repo.strengthProfile())
        // Done, loaded and completed: the unticked bench, the live squat and the unloaded set are not read at all.
        assertEquals(listOf(62.5, 70.0, 90.0, 100.0), db.sessionDao().completedLoadedSets().map { it.weightKg }.sorted())
        assertEquals(fromLog.bestE1rmKg["bench press"], repo.strengthProfile().bestE1rmKg["bench press"])
    }

    @Test
    fun aCompletedSessionsListHoldsNoLiveOneAndNoSets() = runBlocking {
        val sessions = repo.completedSessions()
        assertEquals(listOf("Second", "First"), sessions.map { it.label })
        assertEquals(repo.observeHistory().first().map { it.first }, sessions)
    }

    @Test
    fun oneWorkoutIsTheSameRowTheLogHoldsForIt() = runBlocking {
        val log = repo.observeHistory().first()
        assertEquals(2, log.size)
        for ((session, sets) in log) assertEquals(session to sets, repo.observeWorkout(session.id).first())
        assertNull("a live session is not viewable", repo.observeWorkout(liveId).first())
        assertNull("nor is one that does not exist", repo.observeWorkout(9_999L).first())
    }

    @Test
    fun aTickInALiveTrialDoesNotReEmitAnUnchangedLog() = runBlocking {
        val emissions = AtomicInteger()
        val job = repo.observeHistory().onEach { emissions.incrementAndGet() }.launchIn(CoroutineScope(Dispatchers.Default))
        try {
            awaitEmissions(emissions, 1)
            val live = db.sessionDao().setsFor(liveId).single()
            repeat(5) { tick ->
                repo.updateSet(live.id, reps = 5 + tick, weightKg = 200.0, done = tick % 2 == 0)
                delay(250)
            }
            assertEquals("the completed log never changed, so nothing downstream re-prices it", 1, emissions.get())
            // ...and it still emits when the log does change.
            val sealed = db.sessionDao().setsFor(firstId).first()
            db.sessionDao().updateSet(sealed.copy(reps = 12))
            awaitEmissions(emissions, 2)
            assertEquals(2, emissions.get())
        } finally {
            job.cancel()
        }
    }

    private suspend fun awaitEmissions(count: AtomicInteger, atLeast: Int) {
        val deadline = System.currentTimeMillis() + 5_000
        while (count.get() < atLeast && System.currentTimeMillis() < deadline) delay(20)
        assertTrue("the log never emitted $atLeast times", count.get() >= atLeast)
    }

    private companion object {
        const val TEST_DB = "ironvellum-history-queries-test.db"
    }
}
