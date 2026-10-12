package com.ironvellum.app.ui.program

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.IronvellumApp
import com.ironvellum.app.data.ProgramAnswersStore
import com.ironvellum.app.domain.Equipment
import com.ironvellum.app.ui.FlowDriver
import com.ironvellum.app.ui.TestProfile
import com.ironvellum.app.ui.theme.IronvellumTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The builder's state is the view model's, not the screen's: a rotation must not rebuild the proposal over
 * her hand edits or forget there is something to discard, a double tap must write once, and a generation
 * that fails must take the save button away with the plan it belonged to.
 */
@RunWith(AndroidJUnit4::class)
class ProgramBuilderStateTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val driver by lazy { FlowDriver(compose) }

    private val app
        get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as IronvellumApp

    @Before
    fun setUp() {
        TestProfile.ensureSetUp()
        // The builder asks for an armoury before it builds anything.
        ProgramAnswersStore.saveEquipment(app, Equipment.FULL_GYM)
        driver.takeTheClock()
    }

    private fun show(mode: String, presetId: Long? = null) {
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent { IronvellumTheme { ProgramBuilderScreen(mode, presetId, onDone = {}) } }
        }
        driver.settle()
    }

    private fun builder(mode: String, presetId: Long? = null): ProgramBuilderViewModel {
        lateinit var vm: ProgramBuilderViewModel
        compose.activityRule.scenario.onActivity { activity ->
            vm = ViewModelProvider(activity)["program_builder_${mode}_$presetId", ProgramBuilderViewModel::class.java]
        }
        return vm
    }

    private fun await(what: String, attempts: Int = 80, condition: () -> Boolean) {
        repeat(attempts) {
            if (condition()) return
            compose.mainClock.advanceTimeBy(500)
            Thread.sleep(100)
        }
        error("timed out waiting for $what")
    }

    private fun onMain(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)

    @Test
    fun rotationKeepsHandEditsAndTheDiscardPrompt() {
        show("week")
        val before = builder("week")
        await("a first proposal") { before.plan.value?.presets?.isNotEmpty() == true && before.baseline.value != null }
        val generated = before.plan.value!!

        onMain { before.removeEntry(0, 0) }
        val edited = before.plan.value!!
        assertNotEquals("the edit must change the plan", generated, edited)

        compose.activityRule.scenario.recreate()
        show("week")
        val after = builder("week")
        driver.settle(8)

        assertSame("the view model survives rotation", before, after)
        assertEquals("rotation must not rebuild over her edit", edited, after.plan.value)
        assertEquals("the first proposal is still the one she was given", generated, after.generatedPlan.value)

        // Something to lose, so back asks instead of leaving.
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        driver.awaitText("Discard your changes?")
    }

    @Test
    fun aDoubleTapSavesOnce() {
        show("session")
        val vm = builder("session")
        await("a proposed rite") { vm.plan.value != null }
        driver.awaitText("Add this rite")
        val before = runBlocking { app.repository.presetCount() }

        // Two taps with no frame between them: the first has not recomposed the button away yet.
        val add = compose.onAllNodesWithText("Add this rite").onFirst()
        add.performSemanticsAction(SemanticsActions.OnClick)
        add.performSemanticsAction(SemanticsActions.OnClick)

        await("the save to land") { runBlocking { app.repository.presetCount() } > before && !vm.saving.value }
        driver.settle(4)
        assertEquals("one tap pair, one rite", before + 1, runBlocking { app.repository.presetCount() })
    }

    @Test
    fun aFailedGenerationRemovesTheSaveButtonAndTheStalePlan() {
        val rites = runBlocking { app.repository.observePresets().first() }
        val rite = rites.first().id
        show("improve", rite)
        val vm = builder("improve", rite)
        await("a tempered preview") { vm.improvement.value != null }

        // A rite that is not there: the generator refuses, and the preview must go with it.
        onMain { vm.selectedPresetId.value = Long.MAX_VALUE }
        await("the refusal") { vm.error.value != null }
        driver.settle(4)

        assertNull("a refused generation leaves no preview to apply", vm.improvement.value)
        assertTrue(compose.onAllNodesWithText("Apply changes").fetchSemanticsNodes().isEmpty())
        assertNotNull(vm.error.value)

        // And the stale preview cannot be written even if something calls for it.
        onMain { vm.applyImprovement {} }
        assertEquals(rites, runBlocking { app.repository.observePresets().first() })
    }
}
