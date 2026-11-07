package com.ironvellum.app.ui

import androidx.activity.ComponentActivity
import android.os.Bundle
import android.os.Parcel
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.R
import com.ironvellum.app.data.*
import com.ironvellum.app.domain.*
import com.ironvellum.app.ui.preparation.*
import com.ironvellum.app.ui.theme.IronvellumTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreparationLibraryFlowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var db: IronvellumDatabase
    private lateinit var repo: Repository
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(id: Int): String {
        val themed = context.resources.getIdentifier(context.resources.getResourceEntryName(id) + "__t", "string", context.packageName)
        return Words.text(UiText.themed(id, themed.takeIf { it != 0 } ?: id))
    }
    @Before fun setup() = runBlocking {
        SeededDb.fresh(context, DB)
        db = IronvellumDatabase.create(context, DB)
        repo = Repository(db)
        repo.ensureSeeded()
    }
    @After fun close() { db.close(); context.deleteDatabase(DB) }

    @Test fun starterCopyReordersEditsSavesAndReopensUnchanged() = runBlocking {
        assertTrue(repo.preparations.observeRoutines(PreparationKind.WARMUP).first().isEmpty())
        val state = SavedStateHandle()
        val vm = withContext(Dispatchers.Main) { PreparationEditorViewModel(repo, null, PreparationKind.WARMUP, 0, state) }
        val initial = withTimeout(10000) { vm.ui.first { it.loaded } }
        val template = PreparationStarters.templates(PreparationKind.WARMUP, repo.observeExercises().first())[0]
        assertEquals(template.steps, initial.steps)
        withContext(Dispatchers.Main) {
            vm.setName("My morning mobility")
            vm.move(0, 1)
            vm.update(0, vm.ui.value.steps[0].copy(target = 12, note = "Slow and comfortable"))
        }
        val expected = vm.ui.value
        val recovered = withContext(Dispatchers.Main) { PreparationEditorViewModel(repo, null, PreparationKind.WARMUP, 0, SavedStateHandle(state.keys().associateWith { state.get<Any?>(it) })) }
        withTimeout(10000) { recovered.ui.first { it.loaded } }
        assertEquals(expected.steps, recovered.ui.value.steps)
        assertTrue(recovered.ui.value.dirty)
        withContext(Dispatchers.Main) { recovered.save {} }
        val routine = withTimeout(10000) { repo.preparations.observeRoutines(PreparationKind.WARMUP).first { it.isNotEmpty() }.single() }
        val reopened = withContext(Dispatchers.Main) { PreparationEditorViewModel(repo, routine.id, PreparationKind.WARMUP, null, SavedStateHandle()) }
        val loaded = withTimeout(10000) { reopened.ui.first { it.loaded } }
        assertEquals(expected.steps, loaded.steps)
        assertFalse(loaded.dirty)
    }

    @Test fun largeFontEditorKeepsSaveAndDiscardReachable() = editorControls(2f)
    @Test fun legacyRepsCopyEditsSavesAndRunsTheNewPrescription() = editLegacyTarget(ExerciseMetric.REPS, 5, 10)
    @Test fun legacyMinutesCopyEditsSavesAndRunsTheNewSeconds() = editLegacyTarget(ExerciseMetric.DURATION, 2, 45)
    private fun editLegacyTarget(metric: ExerciseMetric, originalTarget: Int, editedTarget: Int) = runBlocking {
        val exercise = repo.observeExercises().first().first { it.metric == metric }
        val preset = repo.savePreset(null, "Original legacy", "", null,
            listOf(Repository.PresetDraftEntry(exercise.id, 1, originalTarget, null, warmup = true)),
            mapOf(PreparationKind.WARMUP to PreparationChoice(PreparationMode.NONE)))
        val originalId = repo.startSessionFromPreset(preset)
        val original = repo.preparations.observeSession(originalId, PreparationKind.WARMUP).first()!!
        val vm = withContext(Dispatchers.Main) { PreparationEditorViewModel(repo, null, PreparationKind.WARMUP, null, SavedStateHandle(), original.snapshot) }
        withTimeout(10000) { vm.ui.first { it.loaded } }
        withContext(Dispatchers.Main) {
            vm.setName("Edited legacy copy")
            vm.update(0, vm.ui.value.steps.single().copy(target = editedTarget))
            vm.save {}
        }
        val stored = withTimeout(10000) { repo.preparations.observeRoutines(PreparationKind.WARMUP).first { it.isNotEmpty() }.single() }
        val reopened = withContext(Dispatchers.Main) { PreparationEditorViewModel(repo, stored.id, PreparationKind.WARMUP, null, SavedStateHandle()) }
        val step = withTimeout(10000) { reopened.ui.first { it.loaded } }.steps.single()
        assertNull("edited prescription must not retain its superseded legacy label", step.legacyTarget)
        assertEquals(editedTarget, step.target)
        val plural = if (metric == ExerciseMetric.REPS) R.plurals.preparation_target_reps else R.plurals.preparation_target_seconds
        val themed = if (metric == ExerciseMetric.REPS) R.plurals.preparation_target_reps__t else R.plurals.preparation_target_seconds__t
        val expected = Words.text(UiText.themedPlural(plural, themed, editedTarget, editedTarget))
        compose.setContent { IronvellumTheme { androidx.compose.material3.Text(preparationTarget(step)) } }
        compose.onNodeWithText(expected).assertExists()
        assertEquals(original, repo.preparations.observeSession(originalId, PreparationKind.WARMUP).first())
        repo.abandonSession(originalId)
        val fresh = repo.startFreeformSession("No legacy prefix")
        repo.preparations.changeSession(fresh, PreparationKind.WARMUP, PreparationChoice(PreparationMode.SAVED, stored.id))
        val now = System.currentTimeMillis()
        repo.preparations.act(fresh, PreparationKind.WARMUP, PreparationAction.START, now)
        val runner = repo.preparations.observeSession(fresh, PreparationKind.WARMUP).first()!!
        assertEquals(step, runner.snapshot.steps.single())
        assertEquals(if (metric == ExerciseMetric.REPS) 0L else editedTarget * 1000L, runner.progress.remainingMs)
        assertEquals(if (metric == ExerciseMetric.REPS) null else now + 5000L + editedTarget * 1000L, runner.progress.deadlineMs)
    }
    @Test fun legacyActivityDraftMetadataSurvivesParcelRecreationUntilPrescriptionEdit() = runBlocking {
        val catalogue = repo.observeExercises().first()
        val steps = listOf(ExerciseMetric.DISTANCE_TIME, ExerciseMetric.ATTEMPTS_GRADE, ExerciseMetric.DURATION).map { metric ->
            val e = catalogue.first { it.metric == metric }
            PreparationStep(e.id, e.name, if (metric == ExerciseMetric.DURATION) PreparationMeasure.SECONDS else PreparationMeasure.REPS,
                if (metric == ExerciseMetric.DURATION) 120 else 1, legacyTarget = PreparationLegacyTarget(metric, 2, if (metric == ExerciseMetric.DISTANCE_TIME) 1.5 else null))
        }
        val snapshot = PreparationSnapshot(PreparationKind.WARMUP, "Activities", steps)
        val state = SavedStateHandle()
        val vm = withContext(Dispatchers.Main) { PreparationEditorViewModel(repo, null, PreparationKind.WARMUP, null, state, snapshot) }
        withTimeout(10000) { vm.ui.first { it.loaded } }
        withContext(Dispatchers.Main) { vm.update(0, vm.ui.value.steps[0].copy(note = "Keep the original distance", rounds = 2, repeat = PreparationRepeat.SIDES)) }
        val expected = vm.ui.value.steps
        val bundled = withContext(Dispatchers.Main) { Bundle().apply { state.keys().forEach { putBundle(it, state.get<Bundle>(it)) } } }
        val parcel = Parcel.obtain()
        val restored = try { parcel.writeBundle(bundled); parcel.setDataPosition(0); parcel.readBundle(javaClass.classLoader)!! } finally { parcel.recycle() }
        val recovered = withContext(Dispatchers.Main) { PreparationEditorViewModel(repo, null, PreparationKind.WARMUP, null,
            SavedStateHandle(restored.keySet().associateWith { restored.getBundle(it) }), snapshot) }
        val actual = withTimeout(10000) { recovered.ui.first { it.loaded } }.steps
        assertEquals(expected, actual)
        assertTrue(recovered.ui.value.dirty)
        compose.setContent { IronvellumTheme { PreparationEditorScreen(null, PreparationKind.WARMUP, onDone = {}, copySnapshot = snapshot, viewModel = recovered) } }
        actual.forEach { compose.onNodeWithText(it.legacyTargetLine(Units::withUnit)!!).assertExists() }
        capture("legacy-recreated")
        withContext(Dispatchers.Main) { recovered.update(0, actual[0].copy(measure = PreparationMeasure.SECONDS, target = 30)) }
        assertNull(recovered.ui.value.steps[0].legacyTarget)
        compose.onNodeWithText(actual[0].legacyTargetLine(Units::withUnit)!!).assertDoesNotExist()
        withContext(Dispatchers.Main) { recovered.update(1, actual[1].copy(exerciseId = actual[0].exerciseId, exerciseName = actual[0].exerciseName)) }
        assertNull(recovered.ui.value.steps[1].legacyTarget)
        assertEquals(steps[0].legacyTarget, snapshot.steps[0].legacyTarget)
    }
    @Test fun normalFontEditorKeepsSaveAndDiscardReachable() = editorControls(1f)
    @Test fun normalFontLibraryCopiesWholeStarterAndSwitchesKind() = libraryControls(1f)
    @Test fun largeFontLibraryCopiesWholeStarterAndSwitchesKind() = libraryControls(2f)
    private fun libraryControls(scale: Float) = runBlocking {
        val warm = withContext(Dispatchers.Main) { PreparationLibraryViewModel(repo, PreparationKind.WARMUP) }
        val cool = withContext(Dispatchers.Main) { PreparationLibraryViewModel(repo, PreparationKind.COOLDOWN) }
        var kind by mutableStateOf(PreparationKind.WARMUP)
        var copied: Pair<Int, PreparationKind>? = null
        var edited: Pair<Long?, PreparationKind>? = null
        compose.activityRule.scenario.onActivity { ShotRecorder.darkWindow(it) }
        compose.setContent {
            IronvellumTheme(light = scale == 2f) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                    Surface(modifier = Modifier.systemBarsPadding(), color = MaterialTheme.colorScheme.background) {
                        PreparationLibraryContent(kind, if (kind == PreparationKind.WARMUP) warm else cool, { id, k -> edited = id to k }, { i, k -> copied = i to k }, { kind = it }, {})
                    }
                }
            }
        }
        compose.waitUntil(10000) { warm.ui.value.loaded }
        compose.onNodeWithText(text(R.string.preparation_empty)).assertExists()
        capture("manager-empty-${scale}")
        val personal = PreparationRoutine(kind = PreparationKind.WARMUP, name = "My upper-body warmup", steps = PreparationStarters.templates(PreparationKind.WARMUP, repo.observeExercises().first())[0].steps)
        val personalId = repo.preparations.saveRoutine(personal)
        compose.waitUntil(10000) { warm.ui.value.routines.size == 1 }
        capture("manager-${scale}")
        compose.onNodeWithText(text(R.string.preparation_preview)).performScrollTo().performClick()
        compose.onNodeWithText("Arm Circles").assertExists()
        compose.onNodeWithText(text(R.string.preparation_edit)).performScrollTo().performClick()
        assertEquals(personalId to PreparationKind.WARMUP, edited)
        compose.onNodeWithText(text(R.string.preparation_cooldowns), ignoreCase = true).performScrollTo().performClick()
        compose.waitUntil(10000) { cool.ui.value.loaded }
        compose.onNodeWithText(text(R.string.preparation_create)).assertIsDisplayed().performClick()
        assertEquals(null to PreparationKind.COOLDOWN, edited)
        compose.onNodeWithText(text(R.string.preparation_full_cooldown)).performScrollTo().performClick()
        assertEquals(0 to PreparationKind.COOLDOWN, copied)
        assertTrue(repo.preparations.observeRoutines(PreparationKind.COOLDOWN).first().isEmpty())
        assertEquals(personal.copy(id = personalId), repo.preparations.observeRoutines(PreparationKind.WARMUP).first().single())
    }

    @Test fun deletingRequiresConfirmationAndDetachesFutureWorkout() = runBlocking {
        val catalogue = repo.observeExercises().first()
        val routine = PreparationRoutine(kind = PreparationKind.WARMUP, name = "Shared warmup", steps = PreparationStarters.templates(PreparationKind.WARMUP, catalogue)[0].steps)
        val id = repo.preparations.saveRoutine(routine)
        val presetId = repo.savePreset(null, "Using shared routine", "", null, listOf(Repository.PresetDraftEntry(catalogue.first().id, 3, 10, null)), mapOf(PreparationKind.WARMUP to PreparationChoice(PreparationMode.SAVED, id)))
        val vm = withContext(Dispatchers.Main) { PreparationEditorViewModel(repo, id, PreparationKind.WARMUP, null, SavedStateHandle()) }
        withTimeout(10000) { vm.ui.first { it.loaded } }
        var done = false
        compose.setContent { IronvellumTheme { PreparationEditorScreen(id, PreparationKind.WARMUP, onDone = { done = true }, viewModel = vm) } }
        compose.onNodeWithText(text(R.string.preparation_delete)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.preparation_delete_title)).assertExists()
        compose.onNodeWithText(text(R.string.preparation_keep_editing)).performClick()
        assertEquals(1, repo.preparations.observeRoutines(PreparationKind.WARMUP).first().size)
        compose.onNodeWithText(text(R.string.preparation_delete)).performClick()
        compose.onNode(hasText(text(R.string.preparation_delete)) and hasAnyAncestor(isDialog())).performClick()
        compose.waitUntil(10000) { done }
        assertTrue(repo.preparations.observeRoutines(PreparationKind.WARMUP).first().isEmpty())
        assertEquals(PreparationMode.AUTO, repo.preparations.choices(presetId)[PreparationKind.WARMUP]?.mode)
    }
    private fun editorControls(scale: Float) = runBlocking {
        val vm = withContext(Dispatchers.Main) { PreparationEditorViewModel(repo, null, PreparationKind.COOLDOWN, 2, SavedStateHandle()) }
        withTimeout(10000) { vm.ui.first { it.loaded } }
        var done = false
        compose.activityRule.scenario.onActivity { ShotRecorder.darkWindow(it) }
        compose.setContent {
            IronvellumTheme(light = scale == 2f) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                    Surface(modifier = Modifier.systemBarsPadding(), color = MaterialTheme.colorScheme.background) { PreparationEditorScreen(null, PreparationKind.COOLDOWN, 2, { done = true }, viewModel = vm) }
                }
            }
        }
        capture("editor-normal-$scale")
        compose.onNodeWithContentDescription(text(R.string.preparation_name)).performTextReplacement("A long personal cooldown routine name for a quiet evening")
        compose.onNodeWithText(text(R.string.preparation_save)).assertIsDisplayed()
        capture("editor-${scale}")
        val first = vm.ui.value.steps[0]
        compose.onNodeWithContentDescription(text(R.string.preparation_actions)).performScrollTo().performClick()
        compose.onNodeWithText(context.getString(R.string.preseteditor_info_button)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.preseteditor_remove_exercise)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.preparation_reps)).performClick()
        assertEquals(PreparationMeasure.REPS, vm.ui.value.steps[0].measure)
        compose.onNodeWithContentDescription(text(R.string.preparation_actions)).performClick()
        compose.onNodeWithText(text(R.string.preparation_seconds)).performClick()
        compose.onNodeWithContentDescription(text(R.string.preparation_seconds)).performScrollTo().performTextReplacement("0")
        compose.onNodeWithText(text(R.string.preparation_save)).assertIsNotEnabled()
        compose.onNodeWithContentDescription(text(R.string.preparation_seconds)).performScrollTo().performTextReplacement("47")
        compose.onNodeWithContentDescription(text(R.string.preparation_rounds)).performScrollTo().performTextReplacement("3")
        val repeatLabel = when (first.repeat) {
            PreparationRepeat.ONCE -> R.string.preparation_once
            PreparationRepeat.SIDES -> R.string.preparation_sides
            PreparationRepeat.DIRECTIONS -> R.string.preparation_directions
        }
        compose.onNodeWithText(text(repeatLabel)).performScrollTo().performClick()
        compose.onAllNodesWithText(text(R.string.preparation_once)).onLast().performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.preparation_once)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.preparation_directions)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.preparation_directions)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.preparation_sides)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.preparation_add_cue)).performScrollTo().performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.preseteditor_note_label)).performScrollTo().performClick().performTextReplacement("Slow and comfortable")
        capture("editor-keyboard-$scale")
        compose.onNodeWithText(text(R.string.preparation_reorder)).performScrollTo().performClick()
        compose.onAllNodesWithText(context.getString(R.string.preseteditor_move_down))[0].performScrollTo().performClick()
        assertEquals(first.copy(target = 47, rounds = 3, repeat = PreparationRepeat.SIDES, note = "Slow and comfortable"), vm.ui.value.steps[1])
        // Removing a different row keeps the edited movement expanded at its valid new index.
        compose.onNodeWithText(vm.ui.value.steps[0].exerciseName).performScrollTo().performClick()
        compose.onNodeWithContentDescription(text(R.string.preparation_actions)).performScrollTo().performClick()
        compose.onNodeWithText(context.getString(R.string.preseteditor_remove_exercise)).performClick()
        assertEquals(first.exerciseId, vm.ui.value.steps[0].exerciseId)
        compose.onNodeWithContentDescription(text(R.string.preparation_seconds)).assertTextEquals("47")
        capture("editor-controls-${scale}")
        compose.onNodeWithText(context.getString(R.string.pushedheader_back_chip)).performClick()
        compose.onNodeWithText(text(R.string.preparation_keep_editing)).performClick()
        assertFalse(done)
        compose.onNodeWithText(text(R.string.preparation_save)).performClick()
        compose.waitUntil(10000) { done }
        val stored = withTimeout(10000) { repo.preparations.observeRoutines(PreparationKind.COOLDOWN).first { it.isNotEmpty() }.single() }
        val reopened = withContext(Dispatchers.Main) { PreparationEditorViewModel(repo, stored.id, PreparationKind.COOLDOWN, null, SavedStateHandle()) }
        assertEquals(vm.ui.value.steps, withTimeout(10000) { reopened.ui.first { it.loaded } }.steps)
    }
    private fun capture(name: String) {
        if (InstrumentationRegistry.getArguments().getString("shots") != "true") return
        preparationCapture(compose, "refinement-task2-$name")
        compose.mainClock.autoAdvance = true
    }
    companion object { private const val DB = "preparation-library-flow.db" }
}
