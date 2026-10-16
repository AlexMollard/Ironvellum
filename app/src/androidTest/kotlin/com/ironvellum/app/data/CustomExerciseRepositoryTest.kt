package com.ironvellum.app.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.data.db.SessionEntity
import com.ironvellum.app.data.db.SetLogEntity
import com.ironvellum.app.domain.CustomExercises
import com.ironvellum.app.domain.CustomLogged
import com.ironvellum.app.domain.CustomNeed
import com.ironvellum.app.domain.CustomSave
import com.ironvellum.app.domain.CustomSpec
import com.ironvellum.app.domain.CustomType
import com.ironvellum.app.domain.Exercise
import com.ironvellum.app.domain.MovementDifficulty
import com.ironvellum.app.domain.Muscle
import com.ironvellum.app.domain.MuscleMap
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
 * The lifter's own exercises through Room: created with a derived weighted flag and their own muscles, a duplicate
 * name refused, deleted only when nothing holds them, restored from an export even when unused, and priced at the
 * custom tier once the catalogue has been read.
 */
@RunWith(AndroidJUnit4::class)
class CustomExerciseRepositoryTest {

    private lateinit var context: Context
    private lateinit var db: IronvellumDatabase
    private lateinit var repo: Repository

    @Before
    fun setUp() = runTest {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        SeededDb.fresh(context, TEST_DB)
        db = IronvellumDatabase.create(context, TEST_DB)
        repo = Repository(db)
        repo.ensureSeeded()
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(TEST_DB)
        CustomExercises.reset()
    }

    private fun spec(
        name: String = "Reverse hyper",
        needs: Set<CustomNeed> = setOf(CustomNeed.MACHINE),
        logged: CustomLogged = CustomLogged.REPS,
    ) = CustomSpec(
        name = name,
        type = CustomType.LEGS,
        logged = logged,
        needs = needs,
        shares = CustomExercises.shares(listOf(Muscle.GLUTES, Muscle.HAMSTRINGS), listOf(Muscle.LOWER_BACK)),
    )

    private suspend fun create(spec: CustomSpec = spec()): Exercise =
        (repo.createCustomExercise(spec) as CustomSave.Saved).exercise

    private suspend fun logSet(exerciseId: Long) {
        val sessionId = db.sessionDao().insertSession(
            SessionEntity(presetId = null, label = "custom", startedAtMs = 1L, completedAtMs = 2L, xpAwarded = 0),
        )
        db.sessionDao().insertSets(
            listOf(
                SetLogEntity(
                    sessionId = sessionId,
                    exerciseId = exerciseId,
                    setIndex = 0,
                    reps = 5,
                    weightKg = 10.0,
                    modifiers = "",
                    done = true,
                ),
            ),
        )
    }

    @Test
    fun aCreatedExerciseIsWeightedByItsGearAndProfiledByItsMuscles() = runTest {
        val made = create()
        assertTrue(made.custom)
        assertTrue("a machine means kilos", made.isWeighted)
        assertEquals("LEGS", made.muscleGroup.name)
        assertEquals(setOf(CustomNeed.MACHINE), made.needs)
        assertEquals(1.0, MuscleMap.profile("reverse hyper")!!.muscles.getValue(Muscle.GLUTES), 0.0)
        assertEquals(CustomExercises.TIER, MovementDifficulty.tier("Reverse hyper"))
        assertFalse(create(spec("Floor reach", needs = emptySet())).isWeighted)
    }

    @Test
    fun aDuplicateNameIsRefusedAgainstTheCatalogueAndYourOwn() = runTest {
        val catalogue = repo.createCustomExercise(spec("back squat"))
        assertTrue(catalogue is CustomSave.Rejected)
        create()
        val own = repo.createCustomExercise(spec("REVERSE HYPER"))
        assertTrue((own as CustomSave.Rejected).message.contains("already in the Ledger"))
    }

    @Test
    fun anEditKeepsItsOwnNameButItsMeasureIsLockedOnceSetsAreLogged() = runTest {
        val made = create()
        val renamedGear = repo.updateCustomExercise(made.id, spec(needs = setOf(CustomNeed.BARBELL)))
        assertFalse("same name is fine on the row itself", renamedGear is CustomSave.Rejected)
        assertFalse((repo.observeExercises().first().first { it.id == made.id }).needs.contains(CustomNeed.MACHINE))
        logSet(made.id)
        val measure = repo.updateCustomExercise(made.id, spec(logged = CustomLogged.TIME))
        assertTrue("sets exist, so the metric cannot change", measure is CustomSave.Rejected)
    }

    @Test
    fun deleteIsRefusedWhileAnythingHoldsTheExercise() = runTest {
        val unused = create(spec("Unused one"))
        assertEquals(0, repo.customExerciseUsage(unused.id))
        assertTrue(repo.deleteCustomExercise(unused.id))
        assertNull(db.exerciseDao().byId(unused.id))

        val used = create()
        logSet(used.id)
        assertTrue(repo.customExerciseUsage(used.id) > 0)
        assertFalse(repo.deleteCustomExercise(used.id))
        assertNotNull("history is never orphaned", db.exerciseDao().byId(used.id))
        assertFalse("a catalogue row is never deleted", db.exerciseDao().deleteCustom(db.exerciseDao().byName("Back Squat")!!.id) > 0)
    }

    @Test
    fun anExportRestoresAnUnusedCustomExerciseWithItsMusclesAndGear() = runTest {
        val made = create()
        val archive = repo.exportJson()
        db.exerciseDao().deleteCustom(made.id)
        assertNull(db.exerciseDao().byName("Reverse hyper"))

        assertTrue(repo.importArchive(archive).isSuccess)
        val back = db.exerciseDao().byName("Reverse hyper")!!
        assertTrue(back.custom)
        assertEquals(CustomExercises.encodeNeeds(setOf(CustomNeed.MACHINE)), back.gear)
        assertEquals(
            CustomExercises.encodeShares(spec().shares),
            back.muscles,
        )
        assertNotNull(MuscleMap.profile("Reverse hyper"))
    }

    @Test
    fun keepAsNewMovementFromACsvImportMakesTheSameKindOfRow() = runTest {
        val id = repo.ensureImportMovement("Imported Thing", com.ironvellum.app.domain.ExerciseMetric.DURATION)
        val row = db.exerciseDao().byId(id)!!
        assertTrue(row.custom)
        assertEquals("", row.muscles)
        assertEquals("DURATION", row.metric)
        assertNull("no muscles chosen yet, so unprofiled as before", MuscleMap.profile("Imported Thing"))
    }

    private companion object {
        const val TEST_DB = "ironvellum-custom-exercise-test.db"
    }
}
