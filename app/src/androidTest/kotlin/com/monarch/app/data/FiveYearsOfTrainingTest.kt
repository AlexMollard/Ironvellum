package com.monarch.app.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.monarch.app.data.db.SessionEntity
import com.monarch.app.data.db.SetLogEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What happens after five years of training?
 *
 * Every other data test uses a handful of rows, so nothing has ever shown how
 * these queries behave at the size a committed hunter actually reaches: four
 * sessions a week for five years is a bit over a thousand, each with a set per
 * movement. The screens that read them are the journal, the titles ledger and
 * the export — all of which load on a UI path, so a query that degrades to
 * seconds is a freeze rather than a slow number.
 *
 * The thresholds are deliberately loose. This is a guard against an accidental
 * N+1 or a whole-table scan per row, not a benchmark: the point is that the
 * numbers stay in the same order of magnitude as the data grows.
 */
@RunWith(AndroidJUnit4::class)
class FiveYearsOfTrainingTest {

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

        val exercises = db.exerciseDao().observeAll().first()
        val day = 86_400_000L
        val start = System.currentTimeMillis() - SESSIONS * 2 * day
        // Insert in bulk rather than through the session flow: this measures the
        // read path, and going through completeSession() a thousand times would
        // measure the writes instead.
        repeat(SESSIONS) { index ->
            val startedAt = start + index * 2 * day
            val sessionId = db.sessionDao().insertSession(
                SessionEntity(
                    presetId = null,
                    label = "Session $index",
                    startedAtMs = startedAt,
                    completedAtMs = startedAt + 3_600_000L,
                    xpAwarded = 120,
                    strengthScore = 400,
                    title = "",
                    note = "",
                    privateNote = "",
                ),
            )
            db.sessionDao().insertSets(
                (0 until SETS_PER_SESSION).map { setIndex ->
                    val exercise = exercises[(index + setIndex) % exercises.size]
                    SetLogEntity(
                        sessionId = sessionId,
                        exerciseId = exercise.id,
                        exercisePosition = setIndex,
                        setIndex = setIndex,
                        reps = 8,
                        weightKg = 20.0,
                        modifiers = "",
                        done = true,
                    )
                },
            )
        }
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(TEST_DB)
    }

    @Test
    fun theReadPathsStayUsableAtFiveYearsOfSessions() = runBlocking {
        assertEquals("the fixture must actually be large", SESSIONS, db.sessionDao().completedCount())

        val timings = LinkedHashMap<String, Long>()
        suspend fun time(label: String, block: suspend () -> Int) {
            val started = System.nanoTime()
            val size = block()
            timings[label] = (System.nanoTime() - started) / 1_000_000
            assertTrue("$label returned nothing", size > 0)
        }

        time("journal") { repo.observeHistory().first().size }
        time("titles ledger") { repo.currentLedger().workouts }
        time("export") { repo.exportJson().length }
        time("completed count") { db.sessionDao().completedCount() }

        val slow = timings.filterValues { it > BUDGET_MS }
        assertEquals(
            "read paths slower than ${BUDGET_MS}ms at $SESSIONS sessions: $timings",
            emptyMap<String, Long>(),
            slow,
        )
    }

    @Test
    fun theDailySnapshotDoesNotFreezeLaunchAtFiveYears() = runBlocking {
        // DbSnapshot.capture() runs on the MAIN thread in Application.onCreate,
        // before Room opens, and copies the database byte for byte. It is
        // throttled to one copy a day, so the cost lands on a single launch —
        // but that launch is a cold start the hunter is watching.
        val dbFile = context.getDatabasePath(TEST_DB)
        db.close()
        val sizeMb = dbFile.length() / 1048576.0
        val started = System.nanoTime()
        val snapshot = DbSnapshot.capture(context, TEST_DB)
        val tookMs = (System.nanoTime() - started) / 1_000_000

        assertTrue("nothing was copied", snapshot != null && snapshot.length() > 0)
        assertTrue(
            "copying a %.1f MB database took %d ms on the main thread at %d sessions"
                .format(sizeMb, tookMs, SESSIONS),
            tookMs < SNAPSHOT_BUDGET_MS,
        )
        snapshot?.delete()
        db = MonarchDatabase.create(context, TEST_DB)
    }

    private companion object {
        const val TEST_DB = "monarch-five-years-test.db"

        /** Four sessions a week for five years. */
        const val SESSIONS = 1_000
        const val SETS_PER_SESSION = 5

        /**
         * Measured on an emulator at this size: journal 76ms, titles ledger
         * 122ms, export 131ms, completed count 0ms. The budget leaves roughly
         * an order of magnitude of headroom for slower hardware while still
         * catching the regression that matters — an accidental N+1 or a scan
         * per row, which would land in seconds rather than tens of ms. A budget
         * with 30x headroom would never fail, which is no guard at all.
         */
        const val BUDGET_MS = 1_500L

        /**
         * Measured at this data size: a 0.3 MB database copies in 11 ms, so the
         * main-thread snapshot in `Application.onCreate` is real disk I/O but
         * not an ANR. 150ms leaves room for slower storage while still failing
         * if the copy stops being cheap — moving it off the main thread would
         * race Room's first open, so the defence is that it stays fast.
         */
        const val SNAPSHOT_BUDGET_MS = 150L
    }
}
