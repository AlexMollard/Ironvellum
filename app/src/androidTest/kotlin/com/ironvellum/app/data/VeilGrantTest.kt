package com.ironvellum.app.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.data.db.IdleStateEntity
import com.ironvellum.app.data.db.OwnedRelicEntity
import com.ironvellum.app.domain.Veil
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Veil's one-time grant and the essence purchase, against a real database: the owner's
 * level-15 case, running it twice, and a balance that falls while the lifetime total does not.
 */
@RunWith(AndroidJUnit4::class)
class VeilGrantTest {

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

    private suspend fun ownerAtLevel15() {
        // 100 * (1 + ... + 14) = 10,500 XP is level 15; two relics, no crests, nothing banked.
        db.profileDao().addXp(10_500L)
        db.gachaDao().insertRelic(OwnedRelicEntity(name = "Fang of the Mark", multiplier = 1.2, drawnAtMs = 1))
        db.gachaDao().insertRelic(OwnedRelicEntity(name = "Sigil of the Margin", multiplier = 1.3, drawnAtMs = 2))
        db.idleDao().upsert(IdleStateEntity(essence = 0, shadows = 900))
    }

    @Test
    fun theOwnerLandsOnCrestsRelicsAndInscriptionsAndNothingIsTakenAway() = runTest {
        ownerAtLevel15()

        val grant = repo.applyVeilGrant()

        assertNotNull(grant)
        assertEquals(setOf("iron", "bronze", "silver"), db.gachaDao().ownedFrameIds().toSet())
        assertEquals(3, db.gachaDao().relicMultipliers().size)
        assertTrue("inscriptions to draw", (db.gachaDao().get()?.rolls ?: 0) >= 1)
        assertEquals(Veil.GRANT_VERSION, db.gachaDao().get()?.veilGrantVersion)
        assertTrue(db.gachaDao().relicMultipliers().containsAll(listOf(1.2, 1.3)))
        assertTrue("one celebration queued", repo.pendingVeilGrant.value?.retro == true)
    }

    @Test
    fun runningItAgainPaysNothing() = runTest {
        ownerAtLevel15()
        repo.applyVeilGrant()
        val rolls = db.gachaDao().get()?.rolls
        val relics = db.gachaDao().relicMultipliers().size
        val frames = db.gachaDao().ownedFrameIds().size

        assertNull(repo.applyVeilGrant())

        assertEquals(rolls, db.gachaDao().get()?.rolls)
        assertEquals(relics, db.gachaDao().relicMultipliers().size)
        assertEquals(frames, db.gachaDao().ownedFrameIds().size)
    }

    @Test
    fun buyingAnInscriptionSpendsEssenceKeepsLifetimeAndRaisesThePrice() = runTest {
        db.idleDao().upsert(IdleStateEntity(essence = 6_000, lifetimeEssence = 6_000))

        assertTrue(repo.buyInscription())

        val idle = db.idleDao().get()!!
        assertEquals(1_000L, idle.essence)
        assertEquals("the board number never falls", 6_000L, idle.lifetimeEssence)
        assertEquals(1, db.gachaDao().get()?.rolls)
        assertEquals(1, repo.observeOfferingsMade().first())
        assertFalse("1,000 is short of the next 5,500", repo.buyInscription())
        assertEquals(1_000L, db.idleDao().get()!!.essence)
        assertEquals(1, db.gachaDao().get()?.rolls)
    }

    private companion object {
        const val TEST_DB = "ironvellum-veil-grant-test.db"
    }
}
