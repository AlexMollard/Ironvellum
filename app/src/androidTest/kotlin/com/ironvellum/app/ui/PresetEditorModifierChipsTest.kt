package com.ironvellum.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.data.IronvellumDatabase
import com.ironvellum.app.data.Repository
import com.ironvellum.app.domain.applicableModifiers
import com.ironvellum.app.domain.modifierChips
import com.ironvellum.app.domain.toggleModifier
import com.ironvellum.app.ui.components.ModifierChipGrid
import com.ironvellum.app.ui.theme.IronvellumTheme
import com.ironvellum.app.ui.train.PresetEditorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The rite editor's modifiers are the live trial's chips: the grid offers only what the movement offers, a token that
 * does not fit stays on screen flagged until it is tapped off, and "weighted" follows the load through the editor's
 * view model, including after Change exercise.
 */
@RunWith(AndroidJUnit4::class)
class PresetEditorModifierChipsTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var db: IronvellumDatabase
    private lateinit var vm: PresetEditorViewModel

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(TEST_DB)
        db = IronvellumDatabase.create(context, TEST_DB)
        val repo = Repository(db)
        repo.ensureSeeded()
        vm = withContext(Dispatchers.Main) { PresetEditorViewModel(repo, presetId = null) }
        val loaded = withTimeout(10_000) { vm.ui.first { it.exercises.isNotEmpty() } }
        assertTrue("the editor must load its catalogue", loaded.exercises.isNotEmpty())
    }

    @After
    fun tearDown() {
        db.close()
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(TEST_DB)
    }

    @Test
    fun theGridOffersTheMovementsChipsAndFlagsAStrayUntilItIsRemoved() {
        val bench = vm.ui.value.exercises.first { it.name == "Bench Press" }
        var modifiers by mutableStateOf("tempo, defecit")
        compose.setContent {
            IronvellumTheme {
                ModifierChipGrid(
                    chips = modifierChips(applicableModifiers(bench), modifiers),
                    onToggle = { modifiers = toggleModifier(modifiers, it) },
                )
            }
        }
        // A bench press offers tempo and paused; the typo is shown set and flagged, with its reason.
        compose.onNodeWithContentDescription("tempo, set").assertExists()
        compose.onNodeWithContentDescription("paused, not set").assertExists()
        compose.onNodeWithContentDescription("defecit, set, not a known modifier").assertExists()
        compose.onNodeWithText("defecit: not a known modifier, so it scores nothing. Tap to remove.").assertExists()

        // Setting a fitting chip adds it; the stray is never dropped by that.
        compose.onNodeWithContentDescription("paused, not set").performClick()
        assertEquals("tempo, defecit, paused", modifiers)

        // Tapping the flagged chip removes it, and the explanation goes with it.
        compose.onNodeWithContentDescription("defecit, set, not a known modifier").performClick()
        assertEquals("tempo, paused", modifiers)
        compose.onNodeWithContentDescription("defecit, set, not a known modifier").assertDoesNotExist()
    }

    @Test
    fun weightedFollowsTheLoadInTheEditorIncludingAfterChangeExercise() = runBlocking {
        val exercises = vm.ui.value.exercises
        val pullUp = exercises.first { it.name == "Pull-up" }
        val dip = exercises.first { it.name == "Dip" }
        withContext(Dispatchers.Main) { vm.addEntry(pullUp) }

        withContext(Dispatchers.Main) { vm.updateEntry(0, vm.ui.value.entries[0].copy(weight = "10")) }
        assertEquals("weighted", vm.ui.first().entries[0].modifiers)

        // Change exercise keeps the load, so the tag stays.
        withContext(Dispatchers.Main) {
            vm.updateEntry(0, vm.ui.value.entries[0].copy(exerciseId = dip.id, exerciseName = dip.name))
        }
        assertEquals("weighted", vm.ui.first().entries[0].modifiers)

        // Clearing the load removes it, with a chip the lifter set by hand left alone.
        withContext(Dispatchers.Main) { vm.updateEntry(0, vm.ui.value.entries[0].copy(modifiers = "weighted, tempo")) }
        withContext(Dispatchers.Main) { vm.updateEntry(0, vm.ui.value.entries[0].copy(weight = "")) }
        assertEquals("tempo", vm.ui.first().entries[0].modifiers)

        // A swap with no load at all never carries a stale tag along.
        withContext(Dispatchers.Main) { vm.updateEntry(0, vm.ui.value.entries[0].copy(modifiers = "weighted")) }
        withContext(Dispatchers.Main) {
            vm.updateEntry(0, vm.ui.value.entries[0].copy(exerciseId = pullUp.id, exerciseName = pullUp.name))
        }
        assertEquals("", vm.ui.first().entries[0].modifiers)
    }

    private companion object {
        const val TEST_DB = "ironvellum-preset-modifier-chips-test.db"
    }
}
