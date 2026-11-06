package com.ironvellum.app.ui

import androidx.activity.ComponentActivity
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
    @Test fun normalFontEditorKeepsSaveAndDiscardReachable() = editorControls(1f)
    @Test fun normalFontLibraryCopiesWholeStarterAndSwitchesKind() = libraryControls(1f)
    @Test fun largeFontLibraryCopiesWholeStarterAndSwitchesKind() = libraryControls(2f)
    private fun libraryControls(scale: Float) = runBlocking {
        val warm = withContext(Dispatchers.Main) { PreparationLibraryViewModel(repo, PreparationKind.WARMUP) }
        val cool = withContext(Dispatchers.Main) { PreparationLibraryViewModel(repo, PreparationKind.COOLDOWN) }
        var kind by mutableStateOf(PreparationKind.WARMUP)
        var copied: Pair<Int, PreparationKind>? = null
        compose.activityRule.scenario.onActivity { ShotRecorder.darkWindow(it) }
        compose.setContent {
            IronvellumTheme(light = scale == 2f) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                    Surface(modifier = Modifier.systemBarsPadding(), color = MaterialTheme.colorScheme.background) {
                        PreparationLibraryContent(kind, if (kind == PreparationKind.WARMUP) warm else cool, { _, _ -> }, { i, k -> copied = i to k }, { kind = it }, {})
                    }
                }
            }
        }
        compose.waitUntil(10000) { warm.ui.value.loaded }
        compose.onNodeWithText(text(R.string.preparation_empty)).assertExists()
        capture("manager-${scale}")
        compose.onNodeWithText(text(R.string.preparation_cooldowns), ignoreCase = true).performScrollTo().performClick()
        compose.waitUntil(10000) { cool.ui.value.loaded }
        compose.onNodeWithText(text(R.string.preparation_full_cooldown)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.preparation_edit_copy)).performScrollTo().performClick()
        assertEquals(0 to PreparationKind.COOLDOWN, copied)
        assertTrue(repo.preparations.observeRoutines(PreparationKind.COOLDOWN).first().isEmpty())
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
        compose.onNodeWithContentDescription(text(R.string.preparation_name)).performTextReplacement("A long personal cooldown routine name for a quiet evening")
        compose.onNodeWithText(text(R.string.preparation_save)).assertIsDisplayed()
        capture("editor-${scale}")
        compose.onAllNodesWithText(context.getString(R.string.preseteditor_move_down))[0].performScrollTo().performClick()
        capture("editor-controls-${scale}")
        compose.onNodeWithText(context.getString(R.string.pushedheader_back_chip)).performClick()
        compose.onNodeWithText(text(R.string.preparation_keep_editing)).performClick()
        assertFalse(done)
        compose.onNodeWithText(text(R.string.preparation_save)).performClick()
        compose.waitUntil(10000) { done }
    }
    private fun capture(name: String) {
        if (InstrumentationRegistry.getArguments().getString("shots") != "true") return
        ShotRecorder.take(compose, "preparation-$name")
        compose.mainClock.autoAdvance = true
    }
    companion object { private const val DB = "preparation-library-flow.db" }
}
