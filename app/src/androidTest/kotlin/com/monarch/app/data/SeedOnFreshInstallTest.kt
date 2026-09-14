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
 * A clean install must end up with the full exercise catalogue and the four
 * day-split presets with their entries — i.e. Repository.ensureSeeded still
 * runs against the current schema.
 */
@RunWith(AndroidJUnit4::class)
class SeedOnFreshInstallTest {

    private lateinit var context: Context
    private lateinit var db: MonarchDatabase

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase("monarch.db")
        db = MonarchDatabase.create(context)
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase("monarch.db")
    }

    @Test
    fun freshInstallSeedsCatalogueAndPresets() = runTest {
        Repository(db).ensureSeeded()

        val expected = Seed.exercises.distinctBy { it.name }
        assertEquals(expected.size, db.exerciseDao().count())
        for (exercise in expected) {
            assertNotNull(
                "Seeded exercise missing from database: ${exercise.name}",
                db.exerciseDao().byName(exercise.name),
            )
        }

        val presets = db.presetDao().observePresets().first()
        assertEquals(Seed.presets.map { it.name }, presets.map { it.preset.name })
        for (spec in Seed.presets) {
            val withEntries = db.presetDao().presetWithEntries(
                presets.single { it.preset.name == spec.name }.preset.id,
            )
            assertEquals("Preset ${spec.name} entry count", spec.entries.size, withEntries?.entries?.size)
        }
    }
}
