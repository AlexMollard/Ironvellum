package com.ironvellum.app.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.data.db.ExerciseEntity
import com.ironvellum.app.domain.ExerciseLibrary
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * An install from before the exercise library has the old catalogue and a movement of its own. Opening the app after the
 * upgrade must add every library movement by name and leave the rows it already had, ids included, exactly as they were:
 * logged sets point at those ids.
 */
@RunWith(AndroidJUnit4::class)
class LibrarySeedUpgradeTest {

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

    @Test
    fun upgradeAddsTheLibraryAndKeepsExistingRows() = runTest {
        val old = Seed.exercises.filterNot { ExerciseLibrary.isLibraryOnly(it.name) }
        db.exerciseDao().insertAll(old + ExerciseEntity(name = "My Odd Lift", muscleGroup = "PULL", isWeighted = true))
        val idsBefore = old.associate { it.name to db.exerciseDao().byName(it.name)!!.id }

        Repository(db).ensureSeeded()

        for ((name, id) in idsBefore) {
            assertEquals("$name keeps its id", id, db.exerciseDao().byName(name)!!.id)
        }
        assertNotNull(db.exerciseDao().byName("My Odd Lift"))
        for (row in ExerciseLibrary.rows) {
            assertNotNull("library movement missing after upgrade: ${row.name}", db.exerciseDao().byName(row.name))
        }
        assertEquals(Seed.exercises.distinctBy { it.name }.size + 1, db.exerciseDao().count())
    }

    private companion object {
        const val TEST_DB = "ironvellum-library-upgrade-test.db"
    }
}
