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
    @Test fun rapidDoubleTapCompletesOnlyDisplayedUnit() = runner(1f, doubleTap = true)
    private fun runner(scale: Float, doubleTap: Boolean = false) = runBlocking {
        val exercises = repo.observeExercises().first()
        val arm = exercises.single { it.name == "Arm Circles" }
        val squat = exercises.single { it.name == "Bodyweight Squat" }
        val routine = repo.preparations.saveRoutine(PreparationRoutine(kind = PreparationKind.WARMUP, name = "Preparation with a long personal name", steps = listOf(
            PreparationStep(arm.id, arm.name, PreparationMeasure.SECONDS, 30, repeat = PreparationRepeat.DIRECTIONS),
            PreparationStep(squat.id, squat.name, PreparationMeasure.REPS, 10))))
        val preset = repo.savePreset(null, "Runner", "", null, emptyList(), mapOf(PreparationKind.WARMUP to PreparationChoice(PreparationMode.SAVED, routine)))
        val id = repo.startSessionFromPreset(preset)
        val vm = withContext(Dispatchers.Main) { PreparationRunnerViewModel(repo, id, PreparationKind.WARMUP) }
        withTimeout(10000) { vm.ui.first { it.loaded } }
        var complete = false
        compose.setContent {
            IronvellumTheme(light = scale == 2f) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                    Surface(Modifier.systemBarsPadding(), color = MaterialTheme.colorScheme.background) {
                        PreparationRunnerScreen(id, PreparationKind.WARMUP, {}, { complete = true }, viewModel = vm)
                    }
                }
            }
        }
        compose.onNodeWithText(Words.text(UiText.themed(R.string.preparation_begin, R.string.preparation_begin__t, text(R.string.preparation_warmup))))
            .assertIsDisplayed().performClick()
        compose.waitUntil(10000) { vm.ui.value.state?.progress?.status == PreparationStatus.RUNNING }
        compose.onNodeWithText(text(R.string.preparation_complete_unit)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.preparation_pause)).performScrollTo().performClick()
        compose.waitUntil(10000) { vm.ui.value.state?.progress?.status == PreparationStatus.PAUSED }
        compose.onNodeWithText(ClockText.minutesSeconds(30)).assertExists()
        compose.onNodeWithText(ExerciseGuides.forName(arm.name)!!.steps.first()).assertExists()
        if (InstrumentationRegistry.getArguments().getString("shots") == "true") ShotRecorder.take(compose, "preparation-runner-$scale")
        compose.mainClock.autoAdvance = true
        if (doubleTap) compose.onNodeWithText(text(R.string.preparation_complete_unit)).performTouchInput { doubleClick(delayMillis = 100) }
        else compose.onNodeWithText(text(R.string.preparation_complete_unit)).performClick()
        compose.waitUntil(10000) { vm.ui.value.state?.progress?.currentUnitKey == "0:0:1" }
        compose.waitForIdle()
        assertEquals(setOf("0:0:0"), vm.ui.value.state!!.progress.completedUnitKeys)
        compose.onNodeWithText(text(R.string.preparation_direction_second)).assertExists()
        compose.onNodeWithText(text(R.string.preparation_skip_unit)).performScrollTo().performClick()
        compose.waitUntil(10000) { vm.ui.value.state?.progress?.currentUnitKey == "1:0:0" }
        compose.onNodeWithText(text(R.string.preparation_complete_unit)).assertIsDisplayed().performClick()
        compose.waitUntil(10000) { vm.ui.value.state?.progress?.status == PreparationStatus.DONE }
        compose.onNodeWithText(text(R.string.preparation_return_workout)).assertIsDisplayed().performClick()
        compose.waitUntil(10000) { complete }
        assertEquals(2, vm.ui.value.state!!.progress.completedUnitKeys.size)
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
        if (InstrumentationRegistry.getArguments().getString("shots") == "true") ShotRecorder.take(compose, "preparation-summary-$scale")
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText(context.getString(R.string.common_done)).performClick()
        assertTrue(done)
    }
    companion object { private const val DB = "preparation-runner-layout.db" }
}
