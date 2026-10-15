package com.ironvellum.app.data

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.data.db.SessionEntity
import com.ironvellum.app.data.db.SetLogEntity
import com.ironvellum.app.data.db.StatEntity
import com.ironvellum.app.domain.RepMax
import com.ironvellum.app.domain.SessionClock
import com.ironvellum.app.domain.SessionSet
import com.ironvellum.app.domain.SessionXp
import com.ironvellum.app.domain.SetRecords
import com.ironvellum.app.ui.dashboard.DashboardViewModel
import com.ironvellum.app.ui.stats.StatsViewModel
import com.ironvellum.app.ui.train.ExerciseExplorerViewModel
import com.ironvellum.app.ui.train.WorkoutDetailViewModel
import com.ironvellum.app.ui.train.WorkoutLogViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * The paths that read the whole history, timed against a fictional three-year lifter: three sessions a week
 * for 156 weeks (468 sessions, about 8,400 sets) over real catalogue movements, with a weigh-in a week.
 *
 * [FiveYearsOfTrainingTest] is the guard against a scan per row; this is the measurement the 1.8 "History at
 * scale" work was judged by. It logs every figure to logcat under [TAG] (the median of [RUNS] runs, in ms)
 * so a change can be compared with the last one, and asserts a loose ceiling so a regression that turns
 * tens of milliseconds into seconds cannot land silently.
 *
 * It takes about 45 seconds, so the gate skips it; ask for it with `timings=true`, then read logcat:
 *
 *     ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedFossDebugAndroidTest \
 *         -Pandroid.testInstrumentationRunnerArguments.class=com.ironvellum.app.data.ThreeYearHistoryTimingTest \
 *         -Pandroid.testInstrumentationRunnerArguments.timings=true
 *     adb -s emulator-5554 logcat -d -s HistoryAtScale
 */
@RunWith(AndroidJUnit4::class)
class ThreeYearHistoryTimingTest {

    private lateinit var context: Context
    private lateinit var db: IronvellumDatabase
    private lateinit var repo: Repository
    private val ids = HashMap<String, Long>()

    /** Seeded by the test itself, after the opt-in check, so a gate run pays nothing for it. */
    private fun seed() = runBlocking<Unit> {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(TEST_DB)
        db = IronvellumDatabase.create(context, TEST_DB)
        repo = Repository(db)
        repo.ensureSeeded()
        repo.setHeight(178.0)
        db.exerciseDao().observeAll().first().forEach { ids[it.name] = it.id }
        val day = DAY_MS
        val start = System.currentTimeMillis() - WEEKS * 7 * day
        db.withTransaction {
            for (week in 0 until WEEKS) {
                db.statDao().insert(
                    StatEntity(takenAtMs = start + week * 7 * day, weightKg = 80.0 + (week % 9) * 0.3, heightCm = 178.0, bodyFatPct = null),
                )
            }
            var index = 0
            for (week in 0 until WEEKS) {
                for (slot in 0 until 3) {
                    val startedAt = start + (week * 7 + slot * 2 + 1) * day + 18 * 3_600_000L
                    val template = TEMPLATES[(week * 3 + slot) % TEMPLATES.size]
                    val sessionId = db.sessionDao().insertSession(
                        SessionEntity(
                            presetId = null,
                            label = template.label,
                            startedAtMs = startedAt,
                            completedAtMs = startedAt + 4_200_000L,
                            xpAwarded = 150,
                            strengthScore = 400,
                            title = "",
                            note = "",
                            privateNote = "",
                        ),
                    )
                    db.sessionDao().insertSets(setsOf(sessionId, template, index))
                    index++
                }
            }
        }
    }

    @After
    fun tearDown() {
        // Without the opt-in the test returns before seeding, so there is no database to close.
        if (!::db.isInitialized) return
        db.close()
        context.deleteDatabase(TEST_DB)
    }

    private class Lift(val name: String, val sets: Int, val reps: Int, val kg: Double)
    private class Template(val label: String, val lifts: List<Lift>)

    /** Six movements of two to four sets: about 18 sets a session, loads creeping up the three years. */
    private fun setsOf(sessionId: Long, template: Template, index: Int): List<SetLogEntity> {
        val rows = ArrayList<SetLogEntity>()
        template.lifts.forEachIndexed { position, lift ->
            val kg = lift.kg + index * 0.1
            repeat(lift.sets) { setIndex ->
                rows += SetLogEntity(
                    sessionId = sessionId,
                    exerciseId = ids.getValue(lift.name),
                    exercisePosition = position,
                    setIndex = setIndex,
                    reps = lift.reps,
                    weightKg = if (lift.kg > 0.0) (kg / 2.5).toInt() * 2.5 else null,
                    modifiers = "",
                    done = true,
                    rpe = if (setIndex == lift.sets - 1) 8 else null,
                )
            }
        }
        return rows
    }

    @Test
    fun theFullHistoryPathsAtThreeYears() = runBlocking<Unit> {
        // Passes without measuring unless asked: an assumption here is reported as a failure by the runner.
        if (InstrumentationRegistry.getArguments().getString("timings") != "true") return@runBlocking
        seed()
        assertEquals("the fixture must be three years", WEEKS * 3, db.sessionDao().completedCount())
        val timings = LinkedHashMap<String, Long>()
        fun record(label: String, ms: Long) {
            timings[label] = ms
            Log.i(TAG, "$label: $ms ms")
        }
        suspend fun time(label: String, block: suspend () -> Unit) {
            val samples = (0 until RUNS).map {
                val started = System.nanoTime()
                block()
                (System.nanoTime() - started) / 1_000_000
            }
            record(label, samples.sorted()[samples.size / 2])
        }

        var tickMs = 0L
        val bench = ids.getValue("Bench Press")
        val sessionId = db.sessionDao().observeCompletedWithSets().first().first().session.id
        Log.i(TAG, "fixture: ${WEEKS * 3} sessions, ${db.sessionDao().completedSetCount()} sets")

        // The raw load every one of the callers below goes through.
        time("observeHistory().first()") { repo.observeHistory().first() }

        // Screens, opened cold: a fresh ViewModel each run, timed to the first loaded state.
        time("Today: first data (ui + rank)") {
            withContext(Dispatchers.Main) {
                val vm = DashboardViewModel(repo)
                vm.ui.first { it.loaded }
                vm.strengthRank.first { it.isNotEmpty() }
            }
        }
        time("Stats: first data") {
            withContext(Dispatchers.Main) { StatsViewModel(repo).ui.first { it.loaded } }
        }
        time("Workout log: first data") {
            withContext(Dispatchers.Main) { WorkoutLogViewModel(repo).ui.first { it.history.isNotEmpty() } }
        }
        time("Lift page (Bench Press): first data") {
            withContext(Dispatchers.Main) {
                val vm = ExerciseExplorerViewModel(repo)
                vm.pick(repo.observeExercises().first().first { it.id == bench })
                vm.ui.first { it.history != null && it.setRecords.isNotEmpty() }
            }
        }
        time("Workout detail: first data") {
            withContext(Dispatchers.Main) {
                WorkoutDetailViewModel(repo, sessionId, context).ui.first { it.loaded && it.session != null }
            }
        }

        // The single-fact reads.
        time("firstSessionEpochDay") { assertTrue(repo.firstSessionEpochDay() != null) }
        time("strengthProfile") { repo.strengthProfile() }
        time("addedLoadKg (a mid-trial extra set)") { repo.addedLoadKg(bench, 5) }
        time("recentDoneSets(bench)") { db.sessionDao().recentDoneSets(bench) }
        time("observeRecentExerciseIds") { db.sessionDao().observeRecentExerciseIds(30).first() }
        time("observeLatestWorkoutSets") { db.sessionDao().observeLatestWorkoutSets().first() }
        time("observeExerciseSets(bench)") { db.sessionDao().observeExerciseSets(bench).first() }
        time("loggedSets(bench)") { db.exerciseDao().loggedSets(bench) }
        time("currentLedger (titles over all history)") { repo.currentLedger() }
        time("observeRankBreakdown first") { repo.observeRankBreakdown().first() }

        // The live tally: 24 done sets priced prefix by prefix, as the session screen does on every tick.
        val book = SessionXp(
            repo.observeExercises().first().associateBy { it.id },
            repo.observeHistory().first(),
            SetRecords.bodyweightLookup(repo.observeStats().first()),
        )
        val live: List<SessionSet> = repo.observeHistory().first().first().second
            .let { done -> (done + done).take(24).mapIndexed { i, s -> s.copy(id = 1_000_000L + i, setIndex = i, done = true) } }
        time("tally of ${live.size} done sets (cold)") { book.tally(live, 80.0) }
        // One more tick on a trial already priced: only the new set's prefix is priced again.
        // Ticked in trial order, so the new set sorts last: the common case, and the one the memo is for.
        val inOrder = live.mapIndexed { i, set -> set.copy(exercisePosition = i / 4, setIndex = i % 4) }
        val memoised = (0 until RUNS).map {
            val memo = SessionXp.TallyMemo()
            book.tally(inOrder.dropLast(1), 80.0, memo)
            val started = System.nanoTime()
            book.tally(inOrder, 80.0, memo)
            (System.nanoTime() - started) / 1_000_000
        }
        record("tally: one more set (memoised)", memoised.sorted()[RUNS / 2])
        time("SessionXp built + peak book") {
            SessionXp(
                repo.observeExercises().first().associateBy { it.id },
                repo.observeHistory().first(),
                SetRecords.bodyweightLookup(repo.observeStats().first()),
            ).peakBook
        }

        // Ticking sets in a live trial: Room re-runs the history query on every write, and the session screen's
        // consumers (records, peak book, XP book, pace) re-price it on each emission. Five ticks two seconds apart;
        // the figure is the milliseconds those consumers spent, the count is how many emissions they were handed.
        run {
            val catalogue = repo.observeExercises().first()
            val metrics = catalogue.associate { it.id to it.metric }
            val byId = catalogue.associateBy { it.id }
            val bodyweightAt = SetRecords.bodyweightLookup(repo.observeStats().first())
            val liveId = db.sessionDao().insertSession(
                SessionEntity(
                    presetId = null, label = "Ticking", startedAtMs = System.currentTimeMillis() - 600_000L, completedAtMs = null,
                    xpAwarded = 0, strengthScore = 0, title = "", note = "", privateNote = "",
                ),
            )
            db.sessionDao().insertSets(setsOf(liveId, TEMPLATES[1], 9_999).map { it.copy(done = false) })
            val busy = AtomicLong()
            val emissions = AtomicInteger()
            val job = repo.observeHistory().onEach { history ->
                val started = System.nanoTime()
                SetRecords.records(history, bodyweightAt, excludeSessionId = liveId) { metrics[it.exerciseId] }
                RepMax.book(history, excludeSessionId = liveId) { metrics[it.exerciseId] }
                SessionXp(byId, history, bodyweightAt, excludeSessionId = liveId).peakBook
                SessionClock.pace(history)
                busy.addAndGet((System.nanoTime() - started) / 1_000_000)
                emissions.incrementAndGet()
            }.launchIn(CoroutineScope(Dispatchers.Default))
            while (emissions.get() < 1) delay(20)
            delay(500)
            busy.set(0)
            emissions.set(0)
            for (set in db.sessionDao().setsFor(liveId).take(5)) {
                repo.updateSet(set.id, set.reps, set.weightKg, true)
                delay(2_000)
            }
            delay(1_000)
            job.cancel()
            Log.i(TAG, "5 live set ticks: ${emissions.get()} history emissions re-priced by the screen")
            tickMs = busy.get()
            db.sessionDao().deleteAbandoned(liveId)
        }

        // Finishing a workout: one live session of 18 done sets, sealed through the real path.
        val finishes = (0 until RUNS).map { run ->
            val startedAt = System.currentTimeMillis() - 3_600_000L
            val id = db.sessionDao().insertSession(
                SessionEntity(
                    presetId = null, label = "Live $run", startedAtMs = startedAt, completedAtMs = null,
                    xpAwarded = 0, strengthScore = 0, title = "", note = "", privateNote = "",
                ),
            )
            db.sessionDao().insertSets(setsOf(id, TEMPLATES[0], 9_999))
            val started = System.nanoTime()
            repo.completeSession(id)
            (System.nanoTime() - started) / 1_000_000
        }
        record("completeSession (18 sets)", finishes.sorted()[finishes.size / 2])

        Log.i(TAG, "5 live set ticks: consumers' re-pricing: $tickMs ms")
        val slow = timings.filterValues { it > BUDGET_MS }
        assertEquals("paths slower than ${BUDGET_MS}ms at three years: $timings", emptyMap<String, Long>(), slow)
        assertTrue(
            "ticking sets in a live trial re-priced the history for $tickMs ms: the completed log did not change",
            tickMs < BUDGET_MS,
        )
    }

    private companion object {
        const val TAG = "HistoryAtScale"
        const val TEST_DB = "ironvellum-three-years-test.db"
        const val DAY_MS = 86_400_000L
        const val WEEKS = 156
        const val RUNS = 3

        /** A ceiling, not a target: the figures themselves are in logcat and docs/TODO.md. */
        const val BUDGET_MS = 3_000L

        val TEMPLATES = listOf(
            Template(
                "Push",
                listOf(
                    Lift("Bench Press", 4, 6, 70.0), Lift("Overhead Press", 3, 8, 40.0), Lift("Incline Bench Press", 3, 10, 50.0),
                    Lift("Dip", 3, 10, 0.0), Lift("Face Pull", 3, 15, 20.0), Lift("Push-up", 2, 20, 0.0),
                ),
            ),
            Template(
                "Pull",
                listOf(
                    Lift("Deadlift", 3, 5, 110.0), Lift("Barbell Row", 4, 8, 60.0), Lift("Lat Pulldown", 3, 10, 55.0),
                    Lift("Pull-up", 3, 8, 0.0), Lift("Bicep Curl", 3, 12, 15.0), Lift("Face Pull", 2, 15, 20.0),
                ),
            ),
            Template(
                "Legs",
                listOf(
                    Lift("Back Squat", 4, 6, 90.0), Lift("Romanian Deadlift", 3, 8, 80.0), Lift("Bulgarian Split Squat", 3, 10, 30.0),
                    Lift("Hip Thrust", 3, 10, 90.0), Lift("Single-Leg Calf Raise", 3, 15, 0.0), Lift("Hanging Leg Raise", 2, 12, 0.0),
                ),
            ),
        )
    }
}
