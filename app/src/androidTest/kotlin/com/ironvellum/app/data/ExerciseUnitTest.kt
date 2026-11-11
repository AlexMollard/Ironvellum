package com.ironvellum.app.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.domain.CustomLogged
import com.ironvellum.app.domain.CustomSave
import com.ironvellum.app.domain.CustomSpec
import com.ironvellum.app.domain.CustomType
import com.ironvellum.app.domain.LoadUnit
import com.ironvellum.app.domain.Loads
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExerciseUnitTest {
    private val realContext get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var context: Context
    private lateinit var db: IronvellumDatabase
    private lateinit var repo: Repository
    private val files = mutableSetOf<String>()

    @Before fun setUp() = runBlocking {
        context = object : ContextWrapper(realContext) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                files += name
                return realContext.getSharedPreferences("exercise_units_test_$name", mode)
            }
        }
        context.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit().clear().commit()
        Units.init(context)
        SeededDb.fresh(realContext, DB)
        db = IronvellumDatabase.create(realContext, DB)
        repo = Repository(db, archiveSettings = ArchiveSettings(context) { _, _ -> Units.init(context) })
    }

    @After fun tearDown() {
        if (::db.isInitialized) db.close()
        realContext.deleteDatabase(DB)
        files.forEach { realContext.getSharedPreferences("exercise_units_test_$it", Context.MODE_PRIVATE).edit().clear().commit() }
        Units.init(realContext)
    }

    @Test fun explicitUnitsPersistAndResetToFollowingTheGlobalUnit() {
        Units.set(context, LoadUnit.KG)
        Units.setExercise(context, " Leg Press ", LoadUnit.LB)
        Units.setExercise(context, "Bench Press", LoadUnit.KG)
        Units.init(context)
        assertEquals(LoadUnit.LB, Units.forExercise("leg press"))
        assertEquals(LoadUnit.KG, Units.forExercise("BENCH PRESS"))
        Units.set(context, LoadUnit.LB)
        assertEquals(LoadUnit.KG, Units.forExercise("Bench Press"))
        assertEquals(LoadUnit.LB, Units.forExercise("Pull-up"))
        Units.setExercise(context, "Bench Press", null)
        Units.init(context)
        assertNull(Units.overrideFor("Bench Press"))
        assertEquals(LoadUnit.LB, Units.forExercise("Bench Press"))
        Units.set(context, LoadUnit.KG)
        assertEquals(LoadUnit.KG, Units.forExercise("Bench Press"))
        assertEquals(LoadUnit.LB, Units.forExercise("Leg Press"))
        assertEquals(45.359, Loads.parse("100", Units.forExercise("Leg Press"), 500.0).getOrThrow(), 0.0)
    }

    @Test fun localAndCloudArchivesRestoreOverridesWhileOldSettingsRestoreTheDefault() = runBlocking {
        Units.set(context, LoadUnit.KG)
        Units.setExercise(context, "Leg Press", LoadUnit.LB)
        Units.setExercise(context, "Bench Press", LoadUnit.KG)
        val local = repo.exportArchive().json
        val cloud = repo.exportArchive(includeDeviceOnly = false).json
        for (archive in listOf(local, cloud)) {
            Units.setExercise(context, "Leg Press", null)
            Units.setExercise(context, "Bench Press", null)
            assertTrue(repo.importArchive(archive).isSuccess)
            assertEquals(LoadUnit.LB, Units.overrideFor("Leg Press"))
            assertEquals(LoadUnit.KG, Units.overrideFor("Bench Press"))
        }
        ArchiveSettings(context).restore(mapOf("appearance" to mapOf("loadUnit" to "s:KG")))
        Units.init(context)
        assertNull(Units.overrideFor("Leg Press"))
        assertEquals(LoadUnit.KG, Units.forExercise("Leg Press"))
    }

    @Test fun customRenameMovesOnlyItsOverrideAndDeleteRemovesIt() = runBlocking {
        val spec = CustomSpec(name = "Unit Test Movement", type = CustomType.PUSH, logged = CustomLogged.REPS, needs = emptySet(), shares = emptyMap())
        assertNull(db.exerciseDao().byName(spec.name))
        val saved = repo.createCustomExercise(spec) as CustomSave.Saved
        Units.setExercise(context, spec.name, LoadUnit.LB)
        Units.setExercise(context, "Bench Press", LoadUnit.KG)
        val renamed = CustomSpec("Renamed Unit Test Movement", spec.type, spec.logged, spec.needs, spec.shares)
        assertTrue(repo.updateCustomExercise(saved.exercise.id, renamed) is CustomSave.Saved)
        Units.init(context)
        assertNull(Units.overrideFor(spec.name))
        assertEquals(LoadUnit.LB, Units.overrideFor(renamed.name))
        assertEquals(LoadUnit.KG, Units.overrideFor("Bench Press"))
        assertTrue(repo.deleteCustomExercise(saved.exercise.id))
        Units.init(context)
        assertNull(Units.overrideFor(renamed.name))
        assertEquals(LoadUnit.KG, Units.overrideFor("Bench Press"))
    }

    companion object { private const val DB = "exercise_units_test.db" }
}
