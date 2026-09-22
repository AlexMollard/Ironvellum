package com.ironvellum.app.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.domain.PlannedEntry
import com.ironvellum.app.domain.PlannedPreset
import com.ironvellum.app.domain.RoutinePlan
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * First-run setup contract: a fresh database gets the exercise catalogue and
 * ZERO presets (setup asks, it does not impose), applyRoutine swaps presets
 * atomically and idempotently, and - the test that protects the owner's real
 * data - an existing lifter's presets survive ensureSeeded on upgrade.
 */
@RunWith(AndroidJUnit4::class)
class RoutineSeedingTest {

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

    private fun samplePlan() = RoutinePlan(
        presets = listOf(
            PlannedPreset(
                name = "Upper",
                note = "generated",
                scheduledDay = 1,
                entries = listOf(
                    PlannedEntry(exerciseName = "Pull-up", sets = 4, reps = 8, targetWeightKg = null),
                    PlannedEntry(exerciseName = "Bench Press", sets = 3, reps = 5, targetWeightKg = 40.0),
                ),
            ),
            PlannedPreset(
                name = "Lower",
                note = "generated",
                scheduledDay = 3,
                entries = listOf(
                    PlannedEntry(exerciseName = "Back Squat", sets = 5, reps = 5, targetWeightKg = 60.0),
                ),
            ),
        ),
    )

    private suspend fun presetsByName() =
        db.presetDao().observePresets().first().associateBy { it.preset.name }

    @Test
    fun freshInstallSeedsCatalogueButNoPresets() = runTest {
        val repo = Repository(db)
        repo.ensureSeeded()

        assertEquals(Seed.exercises.distinctBy { it.name }.size, db.exerciseDao().count())
        assertEquals("a fresh install must not arrive with somebody else's week", 0, repo.presetCount())
    }

    @Test
    fun applyRoutineWritesThePlanExactlyAndReapplyDoesNotDuplicate() = runTest {
        val repo = Repository(db)
        repo.ensureSeeded()
        val idByName = db.exerciseDao().observeAll().first().associate { it.name to it.id }

        repo.applyRoutine(samplePlan())
        val first = presetsByName()
        assertEquals(2, first.size)
        assertEquals(1, first["Upper"]?.preset?.scheduledDay)
        assertEquals(3, first["Lower"]?.preset?.scheduledDay)
        val upper = first["Upper"]!!.entries.sortedBy { it.position }
        assertEquals(idByName.getValue("Pull-up"), upper[0].exerciseId)
        assertEquals(4, upper[0].targetSets)
        assertEquals(8, upper[0].targetReps)
        assertEquals(idByName.getValue("Bench Press"), upper[1].exerciseId)
        assertEquals(40.0, upper[1].targetWeightKg!!, 0.0)

        // Re-running setup (or a double-tap on the accept button) replaces, so
        // the board must end up with exactly the plan, never the plan twice.
        repo.applyRoutine(samplePlan())
        val second = presetsByName()
        assertEquals(2, second.size)
        assertEquals(2, second["Upper"]?.entries?.size)
        assertEquals(1, second["Lower"]?.entries?.size)
        assertEquals(2, repo.presetCount())
    }

    @Test
    fun unknownExerciseNameAbortsWholeApplyWithoutWritingAnyPreset() = runTest {
        val repo = Repository(db)
        repo.ensureSeeded()

        val broken = RoutinePlan(
            presets = listOf(
                PlannedPreset(
                    name = "Good",
                    note = "",
                    scheduledDay = 2,
                    entries = listOf(PlannedEntry("Push-up", 3, 10, null)),
                ),
                PlannedPreset(
                    name = "Bad",
                    note = "",
                    scheduledDay = 4,
                    entries = listOf(PlannedEntry("Not A Real Movement", 3, 10, null)),
                ),
            ),
        )
        val before = db.presetDao().count()
        val error = runCatching { repo.applyRoutine(broken) }.exceptionOrNull()
        assertTrue("an unresolvable plan must fail loudly", error is IllegalArgumentException)
        assertTrue(error?.message?.contains("Not A Real Movement") == true)
        // The transaction rolled back: no half-built board with one preset.
        assertEquals(before, db.presetDao().count())
    }

    @Test
    fun existingLiftersPresetsSurviveEnsureSeeded() = runTest {
        val repo = Repository(db)
        repo.ensureSeeded()
        // A lifter who has been editing her week for months: her own preset,
        // written through the normal editor path.
        val hers = repo.savePreset(
            presetId = null,
            name = "My Wednesday",
            note = "hand-tuned",
            scheduledDay = 3,
            entries = listOf(
                Repository.PresetDraftEntry(
                    exerciseId = db.exerciseDao().byName("Dip")!!.id,
                    targetSets = 3,
                    targetReps = 12,
                    targetWeightKg = null,
                ),
            ),
        )

        // Upgrade path: a later launch runs ensureSeeded again (catalogue
        // additions, scoring restatement). It must not touch her presets.
        repo.ensureSeeded()

        val surviving = presetsByName()["My Wednesday"]
        assertNotNull("ensureSeeded must leave an existing lifter's presets alone", surviving)
        assertEquals(hers, surviving?.preset?.id)
        assertEquals(1, surviving?.entries?.size)
        // A fresh install stays preset-free even though this database is no
        // longer empty - the seeding gate no longer looks at presets at all.
        repo.applyRoutine(samplePlan())
        assertEquals(2, repo.presetCount())
    }

    private companion object {
        /** Never the app's live database: deleting that under the running
         *  Application left its open Room instance serving an empty file. */
        const val TEST_DB = "ironvellum-routine-seeding-test.db"
    }
}
