package com.ironvellum.app.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.data.IronvellumDatabase
import com.ironvellum.app.data.Repository
import com.ironvellum.app.data.SeededDb
import com.ironvellum.app.domain.applicableModifiers
import com.ironvellum.app.domain.modifierChips
import com.ironvellum.app.domain.toggleModifier
import com.ironvellum.app.ui.components.ModifierChipGrid
import com.ironvellum.app.ui.theme.IronvellumTheme
import com.ironvellum.app.ui.train.PresetEditorViewModel
import com.ironvellum.app.ui.train.PresetEditorScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
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
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var db: IronvellumDatabase
    private lateinit var vm: PresetEditorViewModel

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        SeededDb.fresh(context, TEST_DB)
        db = IronvellumDatabase.create(context, TEST_DB)
        val repo = Repository(db)
        repo.ensureSeeded()
        compose.activityRule.scenario.onActivity { activity ->
            val factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    PresetEditorViewModel(repo, presetId = null) as T
            }
            vm = ViewModelProvider(activity, factory)["test-preset-editor", PresetEditorViewModel::class.java]
        }
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

    private fun showEditor() {
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent { IronvellumTheme { PresetEditorScreen(null, onDone = {}, viewModel = vm) } }
        }
        compose.waitForIdle()
    }

    private fun promptAcrossRecreation(action: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("trial_prefs", Context.MODE_PRIVATE)
        val original = prefs.getStringSet("twin_kept", null)?.toSet()
        try {
            prefs.edit().remove("twin_kept").commit()
            compose.runOnUiThread {
                val exercises = vm.ui.value.exercises
                vm.addEntry(exercises.first { it.name == "Dip" })
                assertTrue("Pull-up must offer the archer modifier", "archer" in applicableModifiers(exercises.first { it.name == "Pull-up" }))
                vm.addEntry(exercises.first { it.name == "Pull-up" })
                vm.updateEntry(1, vm.ui.value.entries[1].copy(reps = "7", modifiers = "tempo"))
            }
            showEditor()
            // Tap the name itself: the merged row's centre can land on its separate sets action.
            compose.onNodeWithText("Pull-up", useUnmergedTree = true)
                .performScrollTo().performTouchInput { click() }
            compose.onNodeWithContentDescription("archer, harder, not set").performScrollTo().performClick()
            val prompt = "Archer Pull-up is its own exercise, with its own art and form guide. Switch to it?"
            compose.onNodeWithText(prompt).assertExists()
            val edited = vm.ui.value.entries.toList()

            compose.activityRule.scenario.recreate()
            compose.activityRule.scenario.onActivity { activity ->
                assertSame(vm, ViewModelProvider(activity)["test-preset-editor", PresetEditorViewModel::class.java])
            }
            showEditor()
            compose.onNodeWithText(prompt).assertExists()
            if (InstrumentationRegistry.getArguments().getString("shots") == "true") {
                val autoAdvance = compose.mainClock.autoAdvance
                try {
                    ShotRecorder.take(compose, "preset-twin-prompt-recreated")
                } finally {
                    compose.mainClock.autoAdvance = autoAdvance
                }
            }
            compose.onNodeWithText(action).performClick()
            compose.waitForIdle()
            val entries = vm.ui.value.entries
            assertEquals("the other entry is untouched", edited[0], entries[0])
            assertEquals("the target's edits survive", "7", entries[1].reps)
            if (action == "Switch") {
                assertEquals("Archer Pull-up", entries[1].exerciseName)
                assertEquals("tempo", entries[1].modifiers)
            } else {
                assertEquals(edited[1], entries[1])
                assertTrue("the choice is remembered", "pull-up|archer" in prefs.getStringSet("twin_kept", emptySet()).orEmpty())
            }
            compose.activityRule.scenario.recreate()
            showEditor()
            compose.onNodeWithText(prompt).assertDoesNotExist()
            assertEquals(entries, vm.ui.value.entries)
        } finally {
            val editor = prefs.edit()
            if (original == null) editor.remove("twin_kept") else editor.putStringSet("twin_kept", original)
            editor.commit()
        }
    }

    @Test fun switchingTheOfferedTwinAfterRecreationKeepsTheEditsOnTheSameEntry() =
        promptAcrossRecreation("Switch")

    @Test fun keepingTheOfferedTwinAfterRecreationDoesNotReplayIt() =
        promptAcrossRecreation("Keep as is")

    private companion object {
        const val TEST_DB = "ironvellum-preset-modifier-chips-test.db"
    }
}
