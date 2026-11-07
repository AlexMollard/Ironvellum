package com.ironvellum.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.data.IronvellumDatabase
import com.ironvellum.app.data.Repository
import com.ironvellum.app.data.SeededDb
import com.ironvellum.app.domain.*
import com.ironvellum.app.ui.preparation.PreparationSelectorViewModel
import com.ironvellum.app.ui.preparation.PreparationSelectorScreen
import com.ironvellum.app.ui.theme.IronvellumTheme
import com.ironvellum.app.R
import com.ironvellum.app.ui.train.PresetEditorViewModel
import com.ironvellum.app.ui.train.editorChanged
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreparationAttachmentFlowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var db: IronvellumDatabase
    private lateinit var repo: Repository
    private fun text(id: Int, vararg args: Any): String {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val themed = context.resources.getIdentifier(context.resources.getResourceEntryName(id) + "__t", "string", context.packageName)
        return com.ironvellum.app.data.Words.text(UiText.themed(id, themed.takeIf { it != 0 } ?: id, *args))
    }
    @Before fun setup() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        SeededDb.fresh(context, DB)
        db = IronvellumDatabase.create(context, DB)
        repo = Repository(db)
        repo.ensureSeeded()
    }
    @After fun close() { db.close(); InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(DB) }

    @Test fun choicesAndEntireUnsavedWorkoutSurviveRecreationAndAnotherEdit() = runBlocking {
        val handle = SavedStateHandle()
        val vm = withContext(Dispatchers.Main) { PresetEditorViewModel(repo, null, handle) }
        val catalogue = withTimeout(10000) { vm.ui.first { it.exercises.isNotEmpty() } }.exercises
        val routineId = repo.preparations.saveRoutine(PreparationRoutine(kind = PreparationKind.WARMUP, name = "Personal", steps = PreparationStarters.templates(PreparationKind.WARMUP, catalogue)[0].steps))
        withContext(Dispatchers.Main) {
            vm.setName("Unsaved workout")
            vm.addEntry(catalogue.first { it.name == "Bench Press" })
            vm.updateEntry(0, vm.ui.value.entries[0].copy(reps = "7", weight = "22.5"))
            vm.setScheduledDay(3)
            vm.setPreparation(PreparationKind.WARMUP, PreparationChoice(PreparationMode.SAVED, routineId))
            vm.setPreparation(PreparationKind.COOLDOWN, PreparationChoice(PreparationMode.NONE))
            vm.setNote("Keep this draft")
        }
        val saved = withContext(Dispatchers.Main) { handle.keys().associateWith { handle.get<Any?>(it) } }
        val recovered = withContext(Dispatchers.Main) { PresetEditorViewModel(repo, null, SavedStateHandle(saved)) }
        withTimeout(10000) { recovered.ui.first { it.exercises.isNotEmpty() } }
        assertEquals(vm.ui.value, recovered.ui.value)
        assertTrue(editorChanged(recovered.baseline.value, recovered.ui.value))
        var done = false
        withContext(Dispatchers.Main) { recovered.save { done = true } }
        withTimeout(10000) { repo.observePresets().first { it.any { p -> p.name == "Unsaved workout" } } }
        compose.waitUntil(10000) { done }
        val preset = repo.observePresets().first().first { it.name == "Unsaved workout" }
        assertEquals(vm.ui.value.preparation, preset.preparation)
        // A non-editor/generator update deliberately omits preparation.
        repo.savePreset(preset.id, preset.name, "Updated outside editor", null, preset.entries.map { Repository.PresetDraftEntry(it.exerciseId, it.targetSets, it.targetReps, it.targetWeightKg) })
        assertEquals(preset.preparation, repo.preparations.choices(preset.id))
    }

    @Test fun autoPreviewUsesCurrentDraftAndEmptyDraftIsNotPersistedWorkout() = runBlocking {
        val catalogue = repo.observeExercises().first()
        val bench = catalogue.first { it.name == "Bench Press" }
        val squat = catalogue.first { it.name == "Back Squat" }
        val id = repo.savePreset(null, "Legs", "", null, listOf(Repository.PresetDraftEntry(squat.id, 3, 10, null)))
        suspend fun preview(ids: List<Long>?) = withContext(Dispatchers.Main) {
            PreparationSelectorViewModel(repo, PreparationKind.WARMUP, id, null, SavedStateHandle(), ids)
        }.let { withTimeout(10000) { it.ui.first { state -> state.loaded } }.suggestion!! }
        assertEquals(PreparationStarters.suggest(PreparationKind.WARMUP, listOf(bench), catalogue), preview(listOf(bench.id)))
        assertEquals(PreparationStarters.suggest(PreparationKind.WARMUP, emptyList(), catalogue), preview(emptyList()))
        assertEquals(PreparationStarters.suggest(PreparationKind.WARMUP, listOf(squat), catalogue), preview(null))
        assertTrue(repo.preparations.choices(id).values.none { it.mode == PreparationMode.SAVED })
    }
    @Test fun normalFontSelectorPreviewDoesNotAttachAndNoneRequiresUse() = selectorControls(1f)
    @Test fun largeFontSelectorPreviewDoesNotAttachAndNoneRequiresUse() = selectorControls(2f)
    private fun selectorControls(scale: Float) = runBlocking {
        val vm = withContext(Dispatchers.Main) { PreparationSelectorViewModel(repo, PreparationKind.WARMUP, null, null, SavedStateHandle(), emptyList()) }
        withTimeout(10000) { vm.ui.first { it.loaded } }
        var selected: PreparationChoice? = null
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.activityRule.scenario.onActivity { ShotRecorder.darkWindow(it) }
        compose.setContent {
            IronvellumTheme(light = scale == 2f) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                    Surface(modifier = Modifier.systemBarsPadding(), color = MaterialTheme.colorScheme.background) { PreparationSelectorScreen(PreparationKind.WARMUP, null, null, { selected = it }, {}, viewModel = vm) }
                }
            }
        }
        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).assertCountEquals(2)
        compose.onAllNodes(isSelected()).assertCountEquals(1)
        compose.onNodeWithText(text(R.string.preparation_full_warmup)).assertExists()
        if (InstrumentationRegistry.getArguments().getString("shots") == "true") {
            preparationCapture(compose, "refinement-task2-selector-$scale")
            compose.mainClock.autoAdvance = true
        }
        compose.onNodeWithText("Arm Circles").assertExists()
        assertNull(selected)
        compose.onNodeWithText(text(R.string.preparation_none)).performScrollTo().performClick()
        assertNull(selected)
        compose.onAllNodes(isSelected()).assertCountEquals(1)
        compose.onNodeWithText("Arm Circles").assertDoesNotExist()
        compose.onNodeWithText(text(R.string.preparation_use_kind, text(R.string.preparation_warmup))).assertIsDisplayed().performClick()
        assertEquals(PreparationChoice(PreparationMode.NONE), selected)
    }
    @Test fun deletingRoutineWhileWorkoutDraftIsOpenReturnsChoiceToAutoOnSave() = runBlocking {
        val vm = withContext(Dispatchers.Main) { PresetEditorViewModel(repo, null, SavedStateHandle()) }
        val catalogue = withTimeout(10000) { vm.ui.first { it.exercises.isNotEmpty() } }.exercises
        val routineId = repo.preparations.saveRoutine(PreparationRoutine(kind = PreparationKind.WARMUP, name = "Temporary", steps = PreparationStarters.templates(PreparationKind.WARMUP, catalogue)[0].steps))
        withContext(Dispatchers.Main) {
            vm.setName("Draft with deleted routine")
            vm.addEntry(catalogue.first { it.name == "Bench Press" })
            vm.setPreparation(PreparationKind.WARMUP, PreparationChoice(PreparationMode.SAVED, routineId))
        }
        repo.preparations.deleteRoutine(routineId)
        var done = false
        withContext(Dispatchers.Main) { vm.save { done = true } }
        compose.waitUntil(10000) { done }
        val preset = repo.observePresets().first().single { it.name == "Draft with deleted routine" }
        assertEquals(PreparationMode.AUTO, preset.preparation[PreparationKind.WARMUP]?.mode)
    }
    @Test fun savedSessionSelectorHasOneRadioAndPreviewWithoutWriting() = runBlocking {
        val catalogue = repo.observeExercises().first()
        val arm = catalogue.first { it.name == "Arm Circles" }
        val chest = catalogue.first { it.name == "Doorway Chest Stretch" }
        val routineId = repo.preparations.saveRoutine(PreparationRoutine(kind = PreparationKind.WARMUP, name = "My selected routine", steps = listOf(PreparationStep(chest.id, chest.name, PreparationMeasure.SECONDS, 45))))
        val session = repo.startFreeformSession("Selector inspection")
        repo.preparations.changeSession(session, PreparationKind.WARMUP, PreparationChoice(PreparationMode.SAVED, routineId))
        val before = repo.preparations.observeSession(session, PreparationKind.WARMUP).first()
        val vm = withContext(Dispatchers.Main) { PreparationSelectorViewModel(repo, PreparationKind.WARMUP, null, session, SavedStateHandle(), listOf(arm.id), PreparationChoice(PreparationMode.SAVED, routineId)) }
        withTimeout(10000) { vm.ui.first { it.loaded } }
        compose.setContent { IronvellumTheme { PreparationSelectorScreen(PreparationKind.WARMUP, null, session, {}, {}, viewModel = vm) } }
        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).assertCountEquals(3)
        compose.onAllNodes(isSelected()).assertCountEquals(1)
        compose.onNodeWithText(chest.name).assertExists()
        compose.onNodeWithText(arm.name).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.preparation_auto)).performScrollTo().performClick()
        compose.onNodeWithText(arm.name).assertExists()
        compose.onNodeWithText(chest.name).assertDoesNotExist()
        compose.onNodeWithText("My selected routine").performScrollTo().performClick()
        compose.onAllNodesWithText(chest.name).assertCountEquals(1)
        compose.onNodeWithText(text(R.string.preparation_none)).performScrollTo().performClick()
        compose.onAllNodes(isSelected()).assertCountEquals(1)
        compose.onNodeWithText(chest.name).assertDoesNotExist()
        assertEquals(before, repo.preparations.observeSession(session, PreparationKind.WARMUP).first())
    }
    companion object { private const val DB = "preparation-attachment-flow.db" }
}
