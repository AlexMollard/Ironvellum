package com.ironvellum.app.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.domain.Gacha
import com.ironvellum.app.domain.Reward
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pity is a promise about a sequence of draws, and the sequence spans app
 * launches: a lifter earns one inscription per rank-up, which can be days
 * apart. Held in memory the counter would reset every launch and the guarantee
 * would never once fire, while every JVM test of [Gacha.roll] still passed.
 *
 * So the contract proved here is the persisted one: the streak advances on
 * figures, resets on anything else, and survives the database being reopened.
 *
 * Runs against an isolated database file — deleting the live one underneath the
 * running app is the divergence trap documented on DbSnapshot.
 */
@RunWith(AndroidJUnit4::class)
class GachaPityTest {

    private lateinit var context: Context
    private lateinit var db: IronvellumDatabase
    private lateinit var repo: Repository

    @Before
    fun setUp() = runTest {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(TEST_DB)
        db = IronvellumDatabase.create(context, TEST_DB)
        repo = Repository(db)
        repo.ensureSeeded()
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(TEST_DB)
    }

    private suspend fun streak(): Int = db.gachaDao().get()?.figureStreak ?: 0

    @Test
    fun theStreakNeverOutlivesItsGuarantee() = runTest {
        // Spend a long run of inscriptions and watch the counter: it may climb,
        // but it can never reach PITY_AFTER + 1, because the draw at the
        // threshold is forced to pay something other than figures.
        repo.grantRoll(40)
        var highest = 0
        repeat(40) {
            val result = repo.spendRoll(seed = it.toLong() * 7919L)
            assertTrue("a banked roll paid nothing", result != null)
            val now = streak()
            highest = maxOf(highest, now)
            assertTrue(
                "streak $now passed the pity threshold ${Gacha.PITY_AFTER}",
                now <= Gacha.PITY_AFTER,
            )
        }
        // Without this the assertion above would pass vacuously on a build
        // where the counter is never written at all.
        assertTrue("the streak never advanced: pity is not being counted", highest > 0)
    }

    @Test
    fun aNonFigureDrawClearsTheStreak() = runTest {
        repo.grantRoll(40)
        var sawReset = false
        repeat(40) {
            val result = repo.spendRoll(seed = it.toLong() * 104_729L) ?: return@repeat
            if (result.reward !is Reward.Figures) {
                assertEquals("a relic or crest left the streak standing", 0, streak())
                sawReset = true
            }
        }
        assertTrue("no non-figure reward was drawn in 40 inscriptions", sawReset)
    }

    @Test
    fun theStreakSurvivesTheDatabaseBeingReopened() = runTest {
        repo.grantRoll(1)
        // Seed 0 pays Common figures, so one draw leaves a live streak to lose.
        repo.spendRoll(seed = 0L)
        val before = streak()
        assertTrue("expected a figure draw to open a streak", before > 0)

        db.close()
        db = IronvellumDatabase.create(context, TEST_DB)
        repo = Repository(db)

        assertEquals("the pity counter was lost on relaunch", before, streak())
    }

    private companion object {
        const val TEST_DB = "ironvellum-gacha-pity-test.db"
    }
}
