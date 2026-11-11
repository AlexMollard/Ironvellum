package com.ironvellum.app.widget

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.data.IronvellumDatabase
import com.ironvellum.app.data.Repository
import com.ironvellum.app.data.SeededDb
import com.ironvellum.app.data.TrialPrefs
import com.ironvellum.app.domain.CustomExercises
import com.ironvellum.app.domain.CustomLogged
import com.ironvellum.app.domain.CustomSave
import com.ironvellum.app.domain.CustomSpec
import com.ironvellum.app.domain.CustomType
import com.ironvellum.app.domain.MuscleMap
import com.ironvellum.app.ui.dashboard.DayKind
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class WidgetEstimateTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: IronvellumDatabase
    private lateinit var repo: Repository

    @Before fun setUp() = runBlocking {
        SeededDb.fresh(context, DB_NAME)
        db = IronvellumDatabase.create(context, DB_NAME)
        repo = Repository(db)
        repo.ensureSeeded()
    }

    @After fun tearDown() {
        if (::db.isInitialized) db.close()
        context.deleteDatabase(DB_NAME)
        CustomExercises.reset()
    }

    @Test fun liftingRestChangesStrengthEstimatesButNotCustomActivityEstimates() = runBlocking {
        val prefs = TrialPrefs(context)
        val previous = prefs.compoundRest
        val name = "Widget estimate custom cardio"
        assertNull(db.exerciseDao().byName(name))
        assertNull(prefs.exerciseRest(name))
        assertTrue(repo.completedSessions().isEmpty())
        val made = (repo.createCustomExercise(CustomSpec(
            name, CustomType.CARDIO, CustomLogged.TIME, emptySet(), emptyMap(),
        )) as CustomSave.Saved).exercise
        assertNull(MuscleMap.profile(name))
        val day = LocalDate.now().dayOfWeek.value
        val preset = repo.savePreset(null, "Activity estimate", "", day, listOf(
            Repository.PresetDraftEntry(made.id, 1, 10, null),
        ))
        suspend fun estimate(seconds: Int): Int {
            prefs.compoundRest = seconds
            val state = loadWidgetState(context, repo)
            assertEquals(DayKind.BEGIN, state.kind)
            assertNotNull(state.estimateMinutes)
            return requireNotNull(state.estimateMinutes)
        }
        try {
            assertEquals(estimate(60), estimate(600))
            val bench = requireNotNull(repo.exerciseIdByName("Bench Press"))
            repo.savePreset(preset, "Strength estimate", "", day, listOf(
                Repository.PresetDraftEntry(bench, 1, 5, 50.0),
            ))
            val short = estimate(60)
            val long = estimate(600)
            assertTrue("configured lifting rest must still price strength work", long > short)
        } finally {
            prefs.compoundRest = previous
        }
    }

    private companion object {
        const val DB_NAME = "widget-rest-estimate-test.db"
    }
}
