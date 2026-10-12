package com.ironvellum.app.ui.program

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.IronvellumApp
import com.ironvellum.app.data.ProgramAnswersStore
import com.ironvellum.app.domain.EffortCue
import com.ironvellum.app.domain.Equipment
import com.ironvellum.app.domain.PlannedEntry
import com.ironvellum.app.domain.PlannedPreset
import com.ironvellum.app.domain.RoutinePlan
import com.ironvellum.app.domain.TrainingSplit
import com.ironvellum.app.ui.FlowDriver
import com.ironvellum.app.ui.TestProfile
import com.ironvellum.app.ui.onboarding.SkipSetupDialog
import com.ironvellum.app.ui.theme.IronvellumTheme
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase 1 of the Forge redesign, end to end where it matters and on the stateless pages where a state is
 * hard to reach: the three-step form keeps its answers, Apply cannot be pressed while saving or after a
 * failure, coverage says its state in words, heavy appears and can be dismissed, and a days clash asks first.
 * Each test also saves a picture to the app's files dir for a human to look at.
 */
@RunWith(AndroidJUnit4::class)
class ForgeV2Test {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val driver by lazy { FlowDriver(compose) }

    private val app
        get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as IronvellumApp

    @Before
    fun setUp() {
        TestProfile.ensureSetUp()
        ProgramAnswersStore.saveEquipment(app, Equipment.FULL_GYM)
        driver.takeTheClock()
    }

    private fun content(body: @androidx.compose.runtime.Composable () -> Unit) {
        compose.activityRule.scenario.onActivity { it.setContent { IronvellumTheme { body() } } }
        driver.settle()
    }

    private fun showWeek() = content { ProgramBuilderScreen("week", null, onDone = {}) }

    private fun builder(): ProgramBuilderViewModel {
        lateinit var vm: ProgramBuilderViewModel
        compose.activityRule.scenario.onActivity { activity ->
            vm = ViewModelProvider(activity)["program_builder_week_null", ProgramBuilderViewModel::class.java]
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

    /** Best effort, like the dialog shots: a missed redraw must not fail the assertions beside it. */
    private fun shot(name: String) {
        compose.mainClock.autoAdvance = true
        val image = runCatching { compose.onRoot().captureToImage() }.getOrNull()
        if (image != null) {
            val dir = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)
            File(dir, "$name.png").outputStream().use { image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        compose.mainClock.autoAdvance = false
    }

    private fun stateNodes(state: String) = compose.onAllNodes(
        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, state),
        useUnmergedTree = true,
    )

    private val press = PlannedEntry(
        "Bench Press", sets = 3, reps = 8, targetWeightKg = 60.0,
        setsMin = 2, setsMax = 3, effort = EffortCue.RIR_0_2, repLow = 6, repHigh = 10,
    )
    private val sample = RoutinePlan(
        listOf(
            PlannedPreset("Upper A", "", 1, listOf(press, press.copy(exerciseName = "Barbell Row"))),
            PlannedPreset("Lower A", "", 3, listOf(press.copy(exerciseName = "Back Squat"))),
        ),
    )

    // ---------------------------------------------------------------- the form

    @Test
    fun theFormAdvancesThroughThreeStepsAndKeepsItsAnswersAcrossBackAndRotation() {
        showWeek()
        val vm = builder()
        driver.awaitText("Forge a cycle")
        shot("forge-1-goal")

        onMain { vm.setSplit(TrainingSplit.UPPER_LOWER, 4) }
        driver.click("Next: kit")
        driver.awaitText("Rite length")
        shot("forge-2-kit")
        driver.click("Next: focus")
        driver.awaitText("Forge cycle")
        shot("forge-3-focus")

        // Rotation: the step is saved with the screen, the answers live in the view model.
        compose.activityRule.scenario.recreate()
        showWeek()
        driver.settle(8)
        assertEquals(TrainingSplit.UPPER_LOWER, vm.split.value)
        assertEquals(4, vm.daysPerWeek.value)
        driver.awaitText("You can change every number on the next screen.")

        // Back walks the steps, and the answers are still there at the first one.
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        driver.settle()
        driver.awaitText("Step 2 of 3")
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        driver.settle()
        driver.awaitText("Step 1 of 3")
        assertEquals(TrainingSplit.UPPER_LOWER, vm.split.value)
        assertEquals(4, vm.daysPerWeek.value)
    }

    // ---------------------------------------------------------------- Apply

    @Test
    fun applyIsDisabledWhileSavingAndAfterAFailedGeneration() {
        content {
            ForgePlanPage(
                plan = sample, previousPlan = null, error = null, saving = true, catalogueLoaded = true,
                equipment = Equipment.FULL_GYM, subtitle = "4 days a week", savedRites = 0,
                coveragePresets = sample.presets, switchesProgression = false, progressionLine = "",
                scroll = rememberScrollState(), onBack = {}, onCoverage = {}, onOpenEntry = { _, _ -> },
                onTryAgain = {}, onApply = {}, onReplace = {},
            )
        }
        driver.awaitText("Applying…")
        compose.onAllNodesWithText("Applying…").onFirst().assertIsNotEnabled()
        shot("forge-saving")

        content {
            ForgePlanPage(
                plan = null, previousPlan = sample, error = "The Forge failed: no movement fits.", saving = false,
                catalogueLoaded = true, equipment = Equipment.FULL_GYM, subtitle = "4 days a week", savedRites = 0,
                coveragePresets = emptyList(), switchesProgression = false, progressionLine = "",
                scroll = rememberScrollState(), onBack = {}, onCoverage = {}, onOpenEntry = { _, _ -> },
                onTryAgain = {}, onApply = {}, onReplace = {},
            )
        }
        driver.awaitText("Try again")
        compose.onAllNodesWithText("Apply").onFirst().assertIsNotEnabled()
        shot("forge-failed")
    }

    // ---------------------------------------------------------------- coverage

    private val heavy = listOf(
        PlannedPreset(
            "Pull", "", 1,
            listOf(
                PlannedEntry("Pull-up", 30, 8, null),
                PlannedEntry("Barbell Row", 30, 8, null),
            ),
        ),
    )

    @Test
    fun coverageRowsSayTheirStateAndHeavyCanBeDismissed() {
        content { CoveragePreviewScreen(heavy) {} }
        driver.awaitText("Coverage of this cycle")
        // Not colour alone: every row carries its state as text on the node.
        assertTrue(stateNodes("Not trained").fetchSemanticsNodes().isNotEmpty())
        val before = stateNodes("Heavy").fetchSemanticsNodes().size
        assertTrue("a 60-set pull day is heavy", before > 0)
        shot("coverage-heavy")

        stateNodes("Heavy").onFirst().performSemanticsAction(SemanticsActions.OnClick)
        driver.settle()
        driver.awaitText("Dismiss for")
        shot("coverage-heavy-lit")
        driver.click(driver.allText().first { it.startsWith("Dismiss for") })
        driver.settle()
        // The flag is gone for that muscle: no note, and fewer heavy marks than before.
        assertTrue(driver.allText().none { it.startsWith("Dismiss for") })
        assertTrue(stateNodes("Heavy").fetchSemanticsNodes().size < before)
    }

    // ---------------------------------------------------------------- skip setup

    @Test
    fun skipSetupAsksBeforeItLeaves() {
        var skipped = 0
        var kept = 0
        content { SkipSetupDialog(onConfirm = { skipped++ }, onDismiss = { kept++ }) }
        driver.awaitText("Skip setup?")
        shot("setup-skip-confirm")
        driver.click("Keep going")
        assertEquals(0, skipped)
        assertEquals(1, kept)
        driver.click("Skip setup")
        assertEquals(1, skipped)
    }

    // ---------------------------------------------------------------- days taken

    @Test
    fun theDaysSheetAppearsWhenTheForgedDaysCollideWithTheCycle() {
        showWeek()
        val vm = builder()
        await("a first proposal") { vm.plan.value?.presets?.isNotEmpty() == true && vm.baseline.value != null }
        val firstDay = vm.plan.value!!.presets.first().scheduledDay!!
        val savedId = runBlocking {
            app.repository.savePlannedPreset(null, PlannedPreset("Clash fixture", "", firstDay, listOf(press)))
        }
        try {
            driver.click("Next: kit")
            driver.click("Next: focus")
            driver.click("Forge cycle")
            driver.awaitText("Proposed cycle")
            await("the saved rite to be seen") { vm.presets.value.any { it.id == savedId } }
            shot("forge-plan")
            driver.click("Apply")
            driver.awaitText("Days already taken")
            shot("forge-days-taken")
            driver.click("Cancel")
            assertTrue(compose.onAllNodesWithText("Days already taken").fetchSemanticsNodes().isEmpty())
        } finally {
            runBlocking { app.repository.deletePreset(savedId) }
        }
    }
}
