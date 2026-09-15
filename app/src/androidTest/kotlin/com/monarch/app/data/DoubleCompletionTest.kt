package com.monarch.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.monarch.app.data.db.SessionEntity
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Completing a session mints XP, so it must happen exactly once however hard
 * the button is pressed. `completeSession` enforces that with a `check` inside
 * the transaction — which means the second caller gets an exception rather than
 * a quiet no-op, and the UI launches it into `viewModelScope` with no catch.
 *
 * This pins both halves: the XP is paid once, and the losing caller fails in a
 * way the caller can see instead of silently double-paying.
 */
@RunWith(AndroidJUnit4::class)
class DoubleCompletionTest {

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

    private suspend fun seedSessionWithOneDoneSet(): Long {
        val exercise = db.exerciseDao().observeAll().first().first { it.metric == "REPS" }
        val id = db.sessionDao().insertSession(
            SessionEntity(
                presetId = null,
                label = "Double tap",
                startedAtMs = System.currentTimeMillis() - 600_000L,
                completedAtMs = null,
                xpAwarded = 0,
                strengthScore = 0,
                title = "",
                note = "",
                privateNote = "",
            ),
        )
        repo.addExtraSet(id, exercise.id, 8, 20.0, "")
        val set = db.sessionDao().setsFor(id).first()
        repo.updateSet(set.id, reps = 8, weightKg = 20.0, done = true)
        return id
    }

    @Test
    fun theSecondCompletionOfOneSessionPaysNothingMore() = runBlocking {
        val id = seedSessionWithOneDoneSet()
        val first = repo.completeSession(id)
        assertTrue("the first completion must pay XP", first.xpAwarded > 0)

        val second = runCatching { repo.completeSession(id) }
        assertTrue(
            "a second completion must not succeed: it would mint the XP twice",
            second.isFailure,
        )

        // The ledger is the thing that matters, not the exception.
        val xp = db.profileDao().get()!!.totalXp
        assertEquals("total XP must reflect exactly one completion", first.xpAwarded.toLong(), xp)
        assertEquals(1, db.sessionDao().completedCount())
    }

    @Test
    fun twoConcurrentCompletionsStillPayOnce() = runBlocking {
        val id = seedSessionWithOneDoneSet()
        // The real double-tap: both callers in flight before either commits.
        val results = coroutineScope {
            val a = async { runCatching { repo.completeSession(id) } }
            val b = async { runCatching { repo.completeSession(id) } }
            listOf(a.await(), b.await())
        }
        assertEquals(
            "exactly one of two concurrent completions may succeed",
            1,
            results.count { it.isSuccess },
        )
        val paid = results.first { it.isSuccess }.getOrThrow().xpAwarded.toLong()
        assertEquals("the losing caller must not have added XP", paid, db.profileDao().get()!!.totalXp)
        assertEquals(1, db.sessionDao().completedCount())
    }

    private companion object {
        const val TEST_DB = "double_completion_test.db"
    }
}
