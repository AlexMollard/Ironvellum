package com.ironvellum.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.R
import com.ironvellum.app.data.FatigueAdviceSetting
import com.ironvellum.app.data.EffortSetting
import com.ironvellum.app.data.IronvellumDatabase
import com.ironvellum.app.data.Repository
import com.ironvellum.app.data.SeededDb
import com.ironvellum.app.data.TrialPrefs
import com.ironvellum.app.data.Units
import com.ironvellum.app.domain.LoadUnit
import com.ironvellum.app.domain.EffortScale
import com.ironvellum.app.domain.ExerciseGuides
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.IronvellumTheme
import com.ironvellum.app.ui.train.SessionScreen
import com.ironvellum.app.ui.train.SessionViewModel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionExpandedPaneTest {
    @get:Rule val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: IronvellumDatabase
    private lateinit var repo: Repository
    private val owner = object : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
    }
    private val sessionModel = lazy {
        ViewModelProvider(owner, viewModelFactory {
            initializer { SessionViewModel(repo, context, sessionId, SavedStateHandle()) }
        })["session", SessionViewModel::class.java]
    }
    private val showContent = mutableStateOf(true)
    private var sessionId = 0L
    private var oldEffort = false
    private lateinit var oldScale: EffortScale
    private var oldRestOff = false
    private var oldDipRestOff = false

    @Before
    fun setUp() = runBlocking {
        SeededDb.fresh(context, DB_NAME)
        db = IronvellumDatabase.create(context, DB_NAME)
        repo = Repository(db)
        repo.ensureSeeded()
        val dip = requireNotNull(repo.exerciseIdByName("Dip"))
        val pushUp = requireNotNull(repo.exerciseIdByName("Push-up"))
        val preset = repo.savePreset(null, "Pane trial", "", null, listOf(
            Repository.PresetDraftEntry(dip, 2, 6, null),
            Repository.PresetDraftEntry(pushUp, 2, 8, null),
        ))
        sessionId = repo.startSessionFromPreset(preset)
        val prefs = TrialPrefs(context)
        oldRestOff = prefs.restOff
        oldDipRestOff = prefs.restOffFor("Dip")
        prefs.restOff = false
        prefs.setRestOffFor("Dip", false)
        oldEffort = EffortSetting.enabled
        oldScale = EffortSetting.scale
        EffortSetting.setEnabled(context, true)
        EffortSetting.setScale(context, EffortScale.RPE)
    }

    @After
    fun tearDown() {
        if (sessionModel.isInitialized()) {
            compose.mainClock.autoAdvance = true
            compose.runOnUiThread { showContent.value = false }
            compose.waitForIdle()
            runBlocking { sessionModel.value.viewModelScope.coroutineContext.job.cancelAndJoin() }
        }
        compose.runOnUiThread { owner.viewModelStore.clear() }
        TrialPrefs(context).apply {
            restOff = oldRestOff
            setRestOffFor("Dip", oldDipRestOff)
        }
        EffortSetting.setEnabled(context, oldEffort)
        EffortSetting.setScale(context, oldScale)
        db.close()
        context.deleteDatabase(DB_NAME)
    }

    // Keep the real window and its pixel constraints; vary density so its actual width
    // measures as the requested dp width. This exercises both branches without an
    // oversized child clipped to a phone's physical window.
    private fun showSession(widthDp: Float) {
        val vm = sessionModel.value
        val paneLight = InstrumentationRegistry.getArguments().getString("paneLight") == "true"
        compose.setContent {
            if (showContent.value) {
                val window = LocalWindowInfo.current
                val density = Density(window.containerSize.width / widthDp, fontScale = 1f)
                CompositionLocalProvider(LocalDensity provides density) {
                    if (paneLight) {
                        IronvellumTheme(light = true) {
                            Box(Modifier.fillMaxSize().background(IronvellumColors.Abyss)) {
                                SessionScreen(sessionId, onExit = {}, onSealed = {}, viewModel = vm)
                            }
                        }
                    } else {
                        Box(Modifier.fillMaxSize().background(IronvellumColors.Abyss)) {
                            SessionScreen(sessionId, onExit = {}, onSealed = {}, viewModel = vm)
                        }
                    }
                }
            }
        }
        compose.waitUntil(15_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasContentDescription("Log set 1"))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForText(text: String) = compose.waitUntil(10_000) {
        compose.onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun logAndRate() {
        compose.onNodeWithContentDescription("Log set 1").assertIsDisplayed().performClick()
        waitForText("Skip")
        compose.onNodeWithContentDescription("RPE 8").assertIsDisplayed().performClick()
        compose.waitUntil(10_000) {
            runBlocking { db.sessionDao().setsFor(sessionId).any { it.done && it.rpe == 8 } }
        }
        compose.onNodeWithText("Skip").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Log set 2").assertIsDisplayed()
        assertEquals(1, runBlocking { db.sessionDao().setsFor(sessionId).count { it.done } })
    }

    @Test
    fun weightedDurationUnitCanBePinnedWithoutChangingItsLoad() {
        val name = "Weighted Skipping"
        val previous = Units.overrideFor(name)
        val setId = runBlocking {
            repo.addExtraSet(sessionId, requireNotNull(repo.exerciseIdByName(name)), 0, 10.0, "")
        }
        try {
            Units.setExercise(context, name, null)
            showSession(400f)
            compose.onNodeWithText(name).performScrollTo().performClick()
            val unitAction = context.getString(R.string.exercise_unit_choose_description, name)
            compose.onNode(SemanticsMatcher("exercise unit action") {
                it.config.getOrNull(SemanticsActions.OnClick)?.label == unitAction
            }).performClick()
            compose.onNodeWithText(LoadUnit.LB.label).performClick()
            assertEquals(LoadUnit.LB, Units.overrideFor(name))
            assertEquals(10.0, runBlocking { db.sessionDao().setById(setId) }!!.weightKg!!, 0.0)
            ShotRecorder.take(compose, "weighted-duration-session-units")
        } finally {
            Units.setExercise(context, name, previous)
        }
    }

    @Test
    fun exerciseRestIsEditableFromTheWorkoutMenu() {
        val prefs = TrialPrefs(context)
        val previous = prefs.exerciseRest("Dip")
        try {
            prefs.setExerciseRest("Dip", null)
            showSession(400f)
            compose.onNodeWithContentDescription("More for Dip").performClick()
            compose.onNodeWithText("Custom rest for this exercise").performClick()
            compose.onNode(hasText("Custom rest for this exercise") and hasClickAction()).performClick()
            val initial = requireNotNull(prefs.exerciseRest("Dip"))
            compose.onNodeWithContentDescription("Increase rest duration").performClick()
            assertEquals(initial + 15, prefs.exerciseRest("Dip"))
            compose.onNodeWithContentDescription("Decrease rest duration").performClick()
            assertEquals(initial, prefs.exerciseRest("Dip"))
            ShotRecorder.take(compose, "rest-exercise-workout")
            compose.mainClock.autoAdvance = true
            compose.onNode(hasText("Custom rest for this exercise") and hasClickAction()).performClick()
            assertEquals(null, prefs.exerciseRest("Dip"))
        } finally {
            prefs.setExerciseRest("Dip", previous)
        }
    }

    @Test
    fun expandedShowsSetsAlongsideRestAndSelectedExerciseForm() {
        showSession(1000f)
        val dipGuide = requireNotNull(ExerciseGuides.forName("Dip"))
        val pushGuide = requireNotNull(ExerciseGuides.forName("Push-up"))
        waitForText(dipGuide.setup)
        val setBounds = compose.onNodeWithContentDescription("Log set 1").getBoundsInRoot()
        val formBounds = compose.onNodeWithText(dipGuide.setup).assertIsDisplayed().getBoundsInRoot()
        // The footer and form share the right pane; the exercise card is left of it.
        val exerciseBounds = compose.onNodeWithText("Dip").getBoundsInRoot()
        assertTrue(exerciseBounds.right < formBounds.left)
        assertTrue(setBounds.left >= formBounds.left)
        logAndRate()
        compose.onNodeWithText("Push-up").performClick()
        waitForText(pushGuide.setup)
        compose.onNodeWithText(pushGuide.setup).assertIsDisplayed()
        compose.onNodeWithText(dipGuide.setup).assertDoesNotExist()
        if (InstrumentationRegistry.getArguments().getString("shots") == "true") {
            val autoAdvance = compose.mainClock.autoAdvance
            ShotRecorder.take(compose, "expanded-session-selected-exercise")
            compose.mainClock.autoAdvance = autoAdvance
        }
    }

    @Test
    fun phoneKeepsFooterUsableWithoutInlineFormPane() {
        showSession(400f)
        val dipGuide = requireNotNull(ExerciseGuides.forName("Dip"))
        compose.onNodeWithText(dipGuide.setup).assertDoesNotExist()
        compose.onNodeWithText("Dip").assertIsDisplayed()
        logAndRate()
        compose.onNodeWithText(dipGuide.setup).assertDoesNotExist()
    }

    @Test
    fun pickerOpensWithFatigueInformationAlreadyPresent() {
        val wasEnabled = FatigueAdviceSetting.enabled
        FatigueAdviceSetting.setEnabled(context, true)
        try {
            showSession(400f)
            compose.onNodeWithText(context.getString(R.string.session_end_add_exercise)).performScrollTo().performClick()
            waitForText(context.getString(R.string.picker_title_default))
            compose.onNodeWithText(context.getString(R.string.picker_fatigue_note)).assertIsDisplayed()
            if (InstrumentationRegistry.getArguments().getString("shots") == "true") {
                ShotRecorder.take(compose, "live-picker-fatigue-ready")
            }
        } finally {
            FatigueAdviceSetting.setEnabled(context, wasEnabled)
        }
    }

    private companion object { const val DB_NAME = "session-pane-test.db" }
}
