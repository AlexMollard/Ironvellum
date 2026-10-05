package com.ironvellum.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.data.db.SessionEntity
import com.ironvellum.app.domain.Rank
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** A seal that clears a technique celebrates the band once, and only once. */
@RunWith(AndroidJUnit4::class)
class RankUpSealTest {

    private class MemoryStore(var value: Int? = null) : HighestBandStore {
        override fun get(): Int? = value
        override fun set(index: Int) {
            value = index
        }
    }

    private lateinit var db: IronvellumDatabase
    private lateinit var repo: Repository
    private val store = MemoryStore()

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(TEST_DB)
        db = IronvellumDatabase.create(context, TEST_DB)
        repo = Repository(db, null, store)
        repo.ensureSeeded()
    }

    @After
    fun tearDown() {
        db.close()
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(TEST_DB)
    }

    private suspend fun sealPistolTrial(): Repository.CompletionResult {
        val exercise = db.exerciseDao().byName("Pistol Squat")!!
        val id = db.sessionDao().insertSession(
            SessionEntity(
                presetId = null,
                label = "Pistol",
                startedAtMs = System.currentTimeMillis() - 600_000L,
                completedAtMs = null,
                xpAwarded = 0,
                strengthScore = 0,
                title = "",
                note = "",
                privateNote = "",
            ),
        )
        repo.addExtraSet(id, exercise.id, 8, null, "")
        val set = db.sessionDao().setsFor(id).first()
        repo.updateSet(set.id, reps = 8, weightKg = null, done = true)
        return repo.completeSession(id)
    }

    @Test
    fun clearingPistolSquatCelebratesIntermediateOnce() = runBlocking {
        val first = sealPistolTrial()
        assertEquals(Rank.INTERMEDIATE, first.rankUp)
        assertEquals(2, store.value)

        val second = sealPistolTrial()
        assertNull(second.rankUp)
        assertEquals(2, store.value)
    }

    private companion object {
        const val TEST_DB = "rank_up_seal_test.db"
    }
}
