package com.ironvellum.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.domain.LoadKit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A loaded movement added outside a plan (mid-trial, or onto a rite) starts on the plan builder's starter load, so
 * Hammer Curl no longer opens at 0 kg; a bodyweight lift stays unloaded, and so does anything with no weigh-in.
 */
@RunWith(AndroidJUnit4::class)
class AddedLoadTest {

    private lateinit var db: IronvellumDatabase
    private lateinit var repo: Repository
    private var hammerCurl = 0L
    private var pullUp = 0L

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        SeededDb.fresh(context, TEST_DB)
        db = IronvellumDatabase.create(context, TEST_DB)
        repo = Repository(db, loadKit = { LoadKit.NONE })
        repo.ensureSeeded()
        hammerCurl = db.exerciseDao().byName("Hammer Curl")!!.id
        pullUp = db.exerciseDao().byName("Pull-up")!!.id
    }

    @After
    fun tearDown() {
        db.close()
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(TEST_DB)
    }

    @Test
    fun aLoadedLiftStartsAboveZeroOnceThereIsAWeighIn() = runBlocking {
        repo.logTodaysWeight(80.0)
        val kg = repo.addedLoadKg(hammerCurl, 10)
        assertNotNull(kg)
        assertTrue("Hammer Curl started at $kg kg", kg!! > 0.0)
    }

    @Test
    fun aBodyweightLiftStaysUnloaded() = runBlocking {
        repo.logTodaysWeight(80.0)
        assertNull(repo.addedLoadKg(pullUp, 8))
    }

    @Test
    fun withNoWeighInAndNoHistoryThereIsNothingToStartFrom() = runBlocking {
        assertNull(repo.addedLoadKg(hammerCurl, 10))
    }

    @Test
    fun aMovementAddedToARiteCarriesTheStartingLoad() = runBlocking {
        repo.logTodaysWeight(80.0)
        repo.addExerciseToRite("Hammer Curl", null, targetSets = 3, targetReps = 10)
        val rite = db.presetDao().observePresets().first().single()
        val kg = rite.entries.single().targetWeightKg
        assertTrue("rite entry started at $kg kg", (kg ?: 0.0) > 0.0)
    }

    private companion object {
        const val TEST_DB = "added_load_test.db"
    }
}
