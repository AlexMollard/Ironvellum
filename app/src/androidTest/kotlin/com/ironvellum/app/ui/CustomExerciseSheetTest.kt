package com.ironvellum.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.R
import com.ironvellum.app.data.IronvellumDatabase
import com.ironvellum.app.data.Repository
import com.ironvellum.app.data.SeededDb
import com.ironvellum.app.domain.CustomExercises
import com.ironvellum.app.domain.CustomLogged
import com.ironvellum.app.domain.CustomNeed
import com.ironvellum.app.domain.CustomSave
import com.ironvellum.app.domain.CustomSpec
import com.ironvellum.app.domain.CustomType
import com.ironvellum.app.domain.Exercise
import com.ironvellum.app.domain.Muscle
import com.ironvellum.app.ui.components.CustomExerciseSheet
import com.ironvellum.app.ui.theme.AccentPalette
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.IronvellumTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CustomExerciseSheetTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val driver by lazy { FlowDriver(compose) }
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: IronvellumDatabase
    private lateinit var repo: Repository
    private var editing by mutableStateOf<Exercise?>(null)
    private var open by mutableStateOf(true)
    private var saved: Exercise? = null
    private var originalPalette: AccentPalette? = null

    @Before fun setUp() = runBlocking {
        driver.takeTheClock()
        compose.activityRule.scenario.onActivity { ShotRecorder.darkWindow(it) }
        originalPalette = IronvellumColors.darkAccents
        compose.runOnIdle { IronvellumColors.darkAccents = AccentPalette(primary = 0xFFA78BFA.toInt()) }
        SeededDb.fresh(context, TEST_DB)
        db = IronvellumDatabase.create(context, TEST_DB)
        repo = Repository(db)
        repo.ensureSeeded()
        assertNull("fixture must not shadow a catalogue entry", db.exerciseDao().byName(NAME))
    }

    @After fun tearDown() {
        if (::db.isInitialized) db.close()
        context.deleteDatabase(TEST_DB)
        CustomExercises.reset()
        originalPalette?.let { palette -> compose.runOnIdle { IronvellumColors.darkAccents = palette } }
    }

    private fun text(id: Int, vararg args: Any) = context.getString(id, *args)
    private fun click(id: Int) = driver.click(text(id))
    private fun shot(name: String) {
        ShotRecorder.take(compose, "custom-exercise-$name")
        // Scroll actions await frames; ShotRecorder pauses the clock after its capture.
        compose.mainClock.autoAdvance = true
    }
    private fun role(muscle: Muscle, level: Int) = compose.onNode(
        hasContentDescription(text(R.string.muscles_selector_role_description, muscle.label, text(level))) and
            hasAnyAncestor(hasAnyDescendant(hasContentDescription(text(R.string.muscles_selector_search)))),
    )

    private fun assertReadableTitle(id: Int) {
        val layouts = ArrayList<TextLayoutResult>()
        compose.onNodeWithText(text(id)).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        if (layout.hasVisualOverflow || (' ' !in text(id) && layout.lineCount > 1)) shot("title-failure-$id")
        assertFalse("${text(id)} must not be clipped: ${layout.size}, ${layout.lineCount} lines", layout.hasVisualOverflow)
        if (' ' !in text(id)) assertEquals("a title word must not split across lines", 1, layout.lineCount)
    }

    @androidx.compose.runtime.Composable
    private fun Form() {
        val exercises by remember(repo) { repo.observeExercises() }.collectAsState(initial = emptyList())
        if (open) CustomExerciseSheet(
            editing = editing,
            prefillName = NAME,
            existingNames = exercises.map { it.name },
            onSave = { spec -> editing?.let { repo.updateCustomExercise(it.id, spec) } ?: repo.createCustomExercise(spec) },
            onDelete = { editing?.let { repo.deleteCustomExercise(it.id) } ?: false },
            usage = repo::customExerciseUsage,
            onSaved = { saved = it; open = false },
            onDismiss = { open = false },
        )
    }

    @Test fun createAndEditPersistChoicesAndMuscleRoles() = runBlocking {
        compose.setContent { IronvellumTheme { Form() } }
        driver.settle()
        compose.onNodeWithText(text(R.string.customex_save_new_button)).assertIsNotEnabled()
        compose.onNodeWithContentDescription(text(R.string.customex_name_label)).performClick().assertIsFocused()
        click(R.string.customex_type_label)
        compose.onNodeWithContentDescription(text(R.string.customex_name_label)).assertIsNotFocused()
        shot("type")
        driver.click(CustomType.PUSH.label)
        click(R.string.customex_logged_label)
        shot("logged")
        driver.click(CustomLogged.HOLD.label)
        click(R.string.customex_logged_label)
        driver.click(CustomLogged.REPS.label)
        click(R.string.customex_needs_label)
        driver.click(CustomNeed.DUMBBELLS.label)
        click(R.string.common_cancel)
        compose.onNodeWithText(CustomNeed.NOTHING.label).assertIsDisplayed()
        click(R.string.customex_needs_label)
        driver.click(CustomNeed.NOTHING.label)
        driver.click(CustomNeed.DUMBBELLS.label)
        driver.click(CustomNeed.BENCH.label)
        shot("equipment")
        click(R.string.common_done)
        val offLine = text(R.string.muscles_selector_line, Muscle.SIDE_DELTS.label, text(R.string.muscles_level_off))
        compose.onAllNodesWithContentDescription(offLine, substring = true).onFirst().performScrollTo().performTouchInput { click(center) }
        driver.settle()
        val assistLine = text(R.string.muscles_selector_line, Muscle.SIDE_DELTS.label, text(R.string.muscles_level_assist))
        compose.onAllNodesWithContentDescription(assistLine, substring = true).onFirst().performScrollTo().performTouchInput { click(center) }
        driver.settle()
        compose.onNodeWithText(text(R.string.muscles_selector_by_name)).performScrollTo()
        click(R.string.muscles_selector_by_name)
        shot("muscle-picker")
        compose.onNodeWithContentDescription(text(R.string.muscles_selector_search)).performTextInput("delt")
        driver.settle()
        role(Muscle.FRONT_DELTS, R.string.muscles_level_assist).performScrollTo().performClick()
        role(Muscle.REAR_DELTS, R.string.muscles_level_main).performScrollTo().performClick()
        driver.settle()
        role(Muscle.REAR_DELTS, R.string.muscles_level_main).assertIsSelected()
        role(Muscle.REAR_DELTS, R.string.muscles_level_off).assertIsNotSelected()
        shot("muscle-search")
        role(Muscle.REAR_DELTS, R.string.muscles_level_off).performScrollTo().performClick()
        driver.settle()
        role(Muscle.REAR_DELTS, R.string.muscles_level_off).assertIsSelected()
        click(R.string.inkpicker_close_button)
        shot("muscles")
        click(R.string.customex_save_new_button)
        compose.waitUntil(10_000) { saved != null }
        val made = repo.observeExercises().first().single { it.id == saved!!.id }
        assertEquals(CustomType.PUSH.group, made.muscleGroup)
        assertEquals(CustomLogged.REPS.metric, made.metric)
        assertEquals(setOf(CustomNeed.DUMBBELLS, CustomNeed.BENCH), made.needs)
        assertEquals(mapOf(Muscle.SIDE_DELTS to 1.0, Muscle.FRONT_DELTS to 0.5), made.shares)
        assertTrue(made.isWeighted)
        compose.runOnIdle { editing = made; saved = null; open = true }
        driver.settle()
        shot("form")
        click(R.string.customex_needs_label)
        driver.click(CustomNeed.NOTHING.label)
        click(R.string.common_done)
        click(R.string.customex_logged_label)
        driver.click(CustomLogged.HOLD.label)
        click(R.string.customex_save_edit_button)
        compose.waitUntil(10_000) { saved != null }
        val updated = repo.observeExercises().first().single { it.id == made.id }
        assertEquals(made.id, updated.id)
        assertEquals(CustomLogged.HOLD.metric, updated.metric)
        assertTrue(updated.needs.isEmpty())
        assertFalse(updated.isWeighted)
        assertEquals(made.shares, updated.shares)
    }

    @Test fun diagramAndRoleChoicesRemainUsableAcrossDisplayAndTextSizes() = runBlocking {
        editing = (repo.createCustomExercise(CustomSpec(
            name = NAME, type = CustomType.PUSH, logged = CustomLogged.REPS,
            needs = setOf(CustomNeed.DUMBBELLS, CustomNeed.BENCH),
            shares = linkedMapOf(Muscle.SIDE_DELTS to 1.0, Muscle.FRONT_DELTS to 0.5, Muscle.TRAPS to 0.5),
        )) as CustomSave.Saved).exercise
        var setting by mutableStateOf(411 to 1f)
        compose.setContent {
            val physicalWidth = context.resources.displayMetrics.widthPixels.toFloat()
            CompositionLocalProvider(LocalDensity provides Density(physicalWidth / setting.first, setting.second)) {
                IronvellumTheme { key(setting) { Form() } }
            }
        }
        for ((width, font) in listOf(411 to 1f, 448 to .85f, 411 to 2f, 320 to 2f)) {
            compose.runOnIdle { setting = width to font }
            driver.settle()
            compose.onNodeWithText(text(R.string.customex_save_edit_button)).assertIsDisplayed()
            assertReadableTitle(R.string.customex_title_edit)
            if (font > 1.3f) {
                val input = compose.onNodeWithContentDescription(text(R.string.customex_name_label)).fetchSemanticsNode().boundsInRoot
                val counter = compose.onNodeWithText("${NAME.length} / ${CustomExercises.MAX_NAME}").fetchSemanticsNode().boundsInRoot
                assertTrue("counter must not crowd the name input", counter.top >= input.bottom)
            }
            val figure = compose.onNodeWithTag("muscle_selector_figure").fetchSemanticsNode().size
            val density = context.resources.displayMetrics.widthPixels.toFloat() / width
            assertTrue("figure must grow beyond the old 180dp thumbnail: $figure", figure.height / density > 250f)
            assertTrue("figure must use the content width: $figure", figure.width / density >= width - 34f)
            shot("form-${width}-${font}")
            if (width == 320) {
                click(R.string.customex_type_label)
                compose.onNodeWithText(CustomType.MOBILITY.label).performScrollTo().assertIsDisplayed()
                shot("type-${width}-${font}")
                driver.click(CustomType.MOBILITY.label)
                click(R.string.customex_logged_label)
                compose.onNodeWithText(CustomLogged.DISTANCE.label).performScrollTo().assertIsDisplayed()
                shot("logged-${width}-${font}")
                driver.click(CustomLogged.DISTANCE.label)
                click(R.string.customex_needs_label)
                compose.onNodeWithText(text(R.string.common_done)).assertIsDisplayed()
                compose.onNodeWithText(CustomNeed.AB_WHEEL.label).performScrollTo().assertIsDisplayed()
                shot("equipment-${width}-${font}")
                click(R.string.common_cancel)
            }
            compose.onNodeWithText(text(R.string.muscles_selector_by_name)).performScrollTo()
            driver.settle()
            shot("muscles-${width}-${font}")
            click(R.string.muscles_selector_by_name)
            compose.onNodeWithContentDescription(text(R.string.muscles_selector_search)).performTextInput(Muscle.FRONT_DELTS.label)
            driver.settle()
            role(Muscle.FRONT_DELTS, R.string.muscles_level_main).performScrollTo().performClick()
            driver.settle()
            assertReadableTitle(R.string.customex_muscles_label)
            role(Muscle.FRONT_DELTS, R.string.muscles_level_main).assertIsSelected()
            shot("picker-${width}-${font}")
            click(R.string.inkpicker_close_button)
        }
    }

    private companion object {
        const val TEST_DB = "custom-exercise-sheet-test.db"
        const val NAME = "Custom lateral raise"
    }
}
