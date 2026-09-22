package com.monarch.app.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A clean install must end up with the full exercise catalogue and NO presets.
 *
 * The four day-split presets used to be seeded here, which meant every new
 * hunter opened the app to the owner's personal training week - his movements,
 * on his weekdays. They are now a template offered during setup, so the
 * property worth pinning is the opposite of the old one: ensureSeeded stocks
 * the catalogue and leaves the week empty for its owner to fill.
 */
@RunWith(AndroidJUnit4::class)
class SeedOnFreshInstallTest {

    private lateinit var context: Context
    private lateinit var db: MonarchDatabase

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(TEST_DB)
        db = MonarchDatabase.create(context, TEST_DB)
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(TEST_DB)
    }

    @Test
    fun freshInstallSeedsCatalogueButNotAnybodyElsesWeek() = runTest {
        Repository(db).ensureSeeded()

        val expected = Seed.exercises.distinctBy { it.name }
        assertEquals(expected.size, db.exerciseDao().count())
        for (exercise in expected) {
            assertNotNull(
                "Seeded exercise missing from database: ${exercise.name}",
                db.exerciseDao().byName(exercise.name),
            )
        }

        assertEquals(
            "a fresh install must not arrive with somebody else's training week",
            0,
            db.presetDao().count(),
        )
        // The starter week still EXISTS to be chosen; it is just not imposed.
        assertEquals(4, Seed.presets.size)
    }

    private companion object {
        /** Never the app's live database: deleting that under the running
         *  Application left its open Room instance serving an empty file. */
        const val TEST_DB = "monarch-seed-test.db"
    }
}
