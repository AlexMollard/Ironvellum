package com.ironvellum.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.Modifier
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.R
import com.ironvellum.app.data.*
import com.ironvellum.app.domain.*
import com.ironvellum.app.ui.preparation.*
import com.ironvellum.app.ui.train.*
import com.ironvellum.app.ui.theme.IronvellumTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Bounded real-device capture: a running wall clock must not wait for Compose to become idle. */
internal fun preparationCapture(compose: androidx.compose.ui.test.junit4.ComposeTestRule, name: String) {
    if (InstrumentationRegistry.getArguments().getString("shots") != "true") return
    compose.mainClock.advanceTimeBy(250)
    Thread.sleep(300)
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val automation = instrumentation.uiAutomation
    fun shell(command: String) {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes() }
    }
    shell("input keyevent KEYCODE_WAKEUP")
    shell("mkdir -p /data/local/tmp/optimise-shots")
    val bitmap = checkNotNull(automation.takeScreenshot())
    val file = java.io.File(checkNotNull(instrumentation.targetContext.getExternalFilesDir(null)), "$name.png")
    file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    shell("cp ${file.absolutePath} /data/local/tmp/optimise-shots/$name.png")
    file.delete()
}

@RunWith(AndroidJUnit4::class)
class PreparationRunnerLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var db: IronvellumDatabase
    private lateinit var repo: Repository
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(id: Int): String {
        val themed = context.resources.getIdentifier(context.resources.getResourceEntryName(id) + "__t", "string", context.packageName)
        return Words.text(UiText.themed(id, themed.takeIf { it != 0 } ?: id))
    }
    @Before fun setup() = runBlocking {
        SeededDb.fresh(context, DB); db = IronvellumDatabase.create(context, DB); repo = Repository(db); repo.ensureSeeded()
    }
    @After fun close() { db.close(); context.deleteDatabase(DB) }
    @Test fun runnerNormalFontActionsRemainReachable() = runner(1f)
    @Test fun runnerLargeFontActionsRemainReachable() = runner(2f)
    @Test fun runnerNormalLightActionsRemainReachable() = runner(1f, light = true)
    @Test fun runnerLargeDarkActionsRemainReachable() = runner(2f, light = false)
    @Test fun rapidDoubleTapCompletesOnlyDisplayedUnit() = runner(1f, doubleTap = true)
    @Test fun expiryNeverAdvancesAndManualFinishKeepsRecordedCounts() = runBlocking {
        val arm = repo.observeExercises().first().single { it.name == "Arm Circles" }
        val routine = repo.preparations.saveRoutine(PreparationRoutine(kind = PreparationKind.WARMUP, name = "Finish honestly", steps = listOf(
            PreparationStep(arm.id, arm.name, PreparationMeasure.SECONDS, 30, rounds = 2, repeat = PreparationRepeat.DIRECTIONS))))
        val preset = repo.savePreset(null, "Finish", "", null, emptyList(), mapOf(PreparationKind.WARMUP to PreparationChoice(PreparationMode.SAVED, routine)))
        val id = repo.startSessionFromPreset(preset)
        repo.preparations.act(id, PreparationKind.WARMUP, PreparationAction.START, System.currentTimeMillis() - 60000)
        val vm = withContext(Dispatchers.Main) { PreparationRunnerViewModel(repo, id, PreparationKind.WARMUP) }
        withTimeout(10000) { vm.ui.first { it.loaded } }
        compose.setContent { IronvellumTheme { PreparationRunnerScreen(id, PreparationKind.WARMUP, {}, {}, viewModel = vm) } }
        compose.onNodeWithText(text(R.string.preparation_reached)).assertExists()
        Thread.sleep(300)
        assertEquals("0:0:0", vm.ui.value.state!!.progress.currentUnitKey)
        compose.onNode(hasContentDescription(text(R.string.preparation_complete_unit), substring = true)).assertIsEnabled().performClick()
        compose.waitUntil(10000) { vm.ui.value.state?.progress?.currentUnitKey == "0:0:1" }
        compose.onNodeWithContentDescription(text(R.string.preparation_actions)).performClick()
        compose.onNodeWithText(text(R.string.preparation_finish)).performClick()
        compose.onNodeWithText(text(R.string.preparation_finish_title)).assertExists()
        compose.onNodeWithText(text(R.string.preparation_finish)).performClick()
        compose.waitUntil(10000) { vm.ui.value.state?.progress?.status == PreparationStatus.SKIPPED }
        assertEquals(setOf("0:0:0"), vm.ui.value.state!!.progress.completedUnitKeys)
        assertTrue(vm.ui.value.state!!.progress.skippedUnitKeys.isEmpty())
        compose.onNodeWithText(Words.text(UiText.themed(R.string.preparation_result_counts, R.string.preparation_result_counts__t, 1, 4, 0))).assertExists()
        assertTrue(repo.observeSessionSets(id).first().isEmpty())
    }
    private fun runner(scale: Float, doubleTap: Boolean = false, light: Boolean = scale == 2f) = runBlocking {
        val exercises = repo.observeExercises().first()
        val arm = exercises.single { it.name == "Arm Circles" }
        val squat = exercises.single { it.name == "Bodyweight Squat" }
        val routine = repo.preparations.saveRoutine(PreparationRoutine(kind = PreparationKind.WARMUP, name = "Preparation with a long personal name", steps = listOf(
            PreparationStep(arm.id, arm.name, PreparationMeasure.SECONDS, 30, rounds = 2, repeat = PreparationRepeat.DIRECTIONS),
            PreparationStep(squat.id, "Bodyweight Squat with a long personal movement name for controlled repetitions", PreparationMeasure.REPS, 10))))
        val preset = repo.savePreset(null, "Runner", "", null, emptyList(), mapOf(PreparationKind.WARMUP to PreparationChoice(PreparationMode.SAVED, routine)))
        val id = repo.startSessionFromPreset(preset)
        repo.preparations.act(id, PreparationKind.WARMUP, PreparationAction.START, System.currentTimeMillis())
        val vm = withContext(Dispatchers.Main) { PreparationRunnerViewModel(repo, id, PreparationKind.WARMUP) }
        withTimeout(10000) { vm.ui.first { it.loaded } }
        var complete = false
        compose.setContent {
            IronvellumTheme(light = light) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                    Surface(Modifier.systemBarsPadding(), color = MaterialTheme.colorScheme.background) {
                        PreparationRunnerScreen(id, PreparationKind.WARMUP, {}, { complete = true }, viewModel = vm)
                    }
                }
            }
        }
        compose.waitUntil(10000) { vm.ui.value.state?.progress?.status == PreparationStatus.RUNNING }
        fun check() = compose.onNode(hasContentDescription(text(R.string.preparation_complete_unit), substring = true))
        fun menu() = compose.onNodeWithContentDescription(text(R.string.preparation_actions))
        check().assertIsDisplayed().assertIsEnabled()
        menu().assertIsDisplayed()
        preparationCapture(compose, "refinement-runner-$scale-$light")
        if (doubleTap) {
            val running = vm.ui.value.state!!.progress
            menu().performClick()
            compose.onNodeWithText(text(R.string.preparation_preview)).performClick()
            compose.onNodeWithText(text(R.string.preparation_no_scoring)).assertExists()
            assertEquals(running, vm.ui.value.state!!.progress)
            compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            check().assertIsDisplayed()
            assertEquals(running, vm.ui.value.state!!.progress)
        }
        compose.onNodeWithText(text(R.string.preparation_pause)).performScrollTo().performClick()
        compose.waitUntil(10000) { vm.ui.value.state?.progress?.status == PreparationStatus.PAUSED }
        compose.onNodeWithText(ClockText.minutesSeconds((vm.ui.value.state!!.progress.remainingMs + 999) / 1000)).assertExists()
        val guide = ExerciseGuides.forName(arm.name)!!
        compose.onNodeWithText(guide.cues.firstOrNull() ?: guide.steps.first()).assertExists()
        preparationCapture(compose, "refinement-paused-$scale-$light")
        compose.onNodeWithContentDescription(context.getString(R.string.session_info_description, arm.name)).performScrollTo().assertIsDisplayed()
        compose.mainClock.autoAdvance = true
        if (doubleTap) check().performTouchInput { doubleClick(delayMillis = 100) }
        else check().performClick()
        compose.waitUntil(10000) { vm.ui.value.state?.progress?.currentUnitKey == "0:0:1" }
        compose.waitForIdle()
        assertEquals(setOf("0:0:0"), vm.ui.value.state!!.progress.completedUnitKeys)
        compose.onAllNodesWithText(text(R.string.preparation_direction_second), substring = true).onFirst().assertExists()
        menu().performClick()
        compose.onNodeWithText(text(R.string.preparation_skip_unit)).performClick()
        compose.waitUntil(10000) { vm.ui.value.state?.progress?.currentUnitKey == "0:1:0" }
        check().performClick()
        compose.waitUntil(10000) { vm.ui.value.state?.progress?.currentUnitKey == "0:1:1" }
        check().performClick()
        compose.waitUntil(10000) { vm.ui.value.state?.progress?.currentUnitKey == "1:0:0" }
        preparationCapture(compose, "refinement-long-movement-$scale-$light")
        check().assertIsDisplayed().performClick()
        compose.waitUntil(10000) { vm.ui.value.state?.progress?.status == PreparationStatus.DONE }
        compose.onNodeWithText(Words.text(UiText.themed(R.string.preparation_result_counts, R.string.preparation_result_counts__t, 4, 5, 1))).assertExists()
        preparationCapture(compose, "refinement-complete-$scale-$light")
        compose.onNodeWithText(text(R.string.preparation_return_workout)).assertIsDisplayed().performClick()
        compose.waitUntil(10000) { complete }
        assertEquals(4, vm.ui.value.state!!.progress.completedUnitKeys.size)
        assertEquals(1, vm.ui.value.state!!.progress.skippedUnitKeys.size)
        assertTrue(repo.observeSessionSets(id).first().isEmpty())
    }
    @Test fun recoveredSummaryLargeFontUsesOnlyStoredFigures() = summary(2f)
    @Test fun recoveredSummaryNormalFontUsesOnlyStoredFigures() = summary(1f)
    private fun summary(scale: Float) = runBlocking {
        val id = repo.startFreeformSession("Recovered trial")
        val row = db.sessionDao().byId(id)!!
        db.sessionDao().updateSession(row.copy(startedAtMs = 1000, completedAtMs = 181000, xpAwarded = 731, strengthScore = 912))
        repo.preparations.ensureSession(id)
        val state = repo.preparations.observeSession(id, PreparationKind.COOLDOWN).first()!!
        val summary = SealedSummary.from(repo.observeSession(id).first()!!, emptyList(), repo.observeExercises().first())!!
        var done = false
        compose.setContent {
            IronvellumTheme(light = scale == 2f) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                    RecoveredSessionSummary(summary, { PreparationSummaryCard(state, {}, {}, {}) }, { done = true }, {})
                }
            }
        }
        compose.onNodeWithText("731").assertExists()
        compose.onNodeWithText("912").assertExists()
        compose.onNodeWithText(context.getString(R.string.common_done)).assertIsDisplayed()
        preparationCapture(compose, "refinement-summary-$scale")
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText(context.getString(R.string.common_done)).performClick()
        assertTrue(done)
    }
    companion object { private const val DB = "preparation-runner-layout.db" }
}
