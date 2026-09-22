package com.ironvellum.app.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.domain.TrainingMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A training mode the app no longer recognises must not be fatal.
 *
 * `trainingMode` is a TEXT column, so the stored value can outlive the enum:
 * an archive imported from a newer build, a mode renamed or removed, or a row
 * edited out of band. `TrainingMode.valueOf` on that string threw from
 * `observeProfile()` — which every screen collects — so the app died on launch
 * with no way back in, and from `exportJson()`, which is how a lifter would
 * rescue their data.
 *
 * Written against the real database because the failure lives in the boundary
 * between a TEXT column and a Kotlin enum, which is exactly what a JVM test
 * with a hand-built entity cannot reproduce.
 */
@RunWith(AndroidJUnit4::class)
class UnknownStoredEnumTest {

    private lateinit var context: Context
    private lateinit var db: IronvellumDatabase

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(TEST_DB)
        db = IronvellumDatabase.create(context, TEST_DB)
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(TEST_DB)
    }

    private suspend fun storeProfileWithMode(mode: String) {
        val repo = Repository(db)
        repo.ensureSeeded()
        // Write the column directly: setTrainingMode only accepts the enum, so
        // the out-of-range value has to arrive the way reality delivers it.
        db.openHelper.writableDatabase.execSQL(
            "UPDATE profile SET trainingMode = ? WHERE id = 1",
            arrayOf<Any>(mode),
        )
        assertEquals(mode, db.profileDao().get()?.trainingMode)
    }

    @Test
    fun anUnrecognisedTrainingModeDoesNotKillTheProfileStream() = runTest {
        storeProfileWithMode("POWERLIFTING_2027")

        val profile = Repository(db).observeProfile().first()

        assertNotNull("observeProfile() must survive an unknown stored mode", profile)
        assertEquals(TrainingMode.STRENGTH, profile!!.trainingMode)
    }

    @Test
    fun anUnrecognisedTrainingModeStillLetsTheLifterExport() = runTest {
        storeProfileWithMode("POWERLIFTING_2027")

        val json = Repository(db).exportJson()

        // The export is the recovery path, so it must complete and carry the
        // fallback rather than propagating the bad string.
        assertEquals(true, json.contains("\"trainingMode\":\"STRENGTH\""))
    }

    private companion object {
        /** Never the app's live database: deleting that under the running
         *  Application left its open Room instance serving an empty file. */
        const val TEST_DB = "ironvellum-enum-test.db"
    }
}
