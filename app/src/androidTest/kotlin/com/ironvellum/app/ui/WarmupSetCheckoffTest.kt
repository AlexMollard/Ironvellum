package com.ironvellum.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.IronvellumApp
import com.ironvellum.app.R
import com.ironvellum.app.data.Words
import com.ironvellum.app.domain.SetKind
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.IronvellumTheme
import com.ironvellum.app.ui.train.SessionScreen
import com.ironvellum.app.ui.train.WorkoutDetailScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WarmupSetCheckoffTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as IronvellumApp
    private val repo get() = app.repository
    private var sessionId = 0L
    private val originalWording = Words.themed

    @After fun removeFixture() {
        if (sessionId != 0L) app.database.openHelper.writableDatabase.execSQL("DELETE FROM sessions WHERE id = ?", arrayOf(sessionId))
        if (Words.themed != originalWording) Words.setThemed(app, originalWording)
    }

    @Test fun checksFoldUndoAndShowInHistoryWithoutLoggingWork() {
        TestProfile.ensureSetUp()
        val plain = InstrumentationRegistry.getArguments().getString("warmupPlain") == "true"
        if (Words.themed == plain) Words.setThemed(app, !plain)
        val shotSuffix = if (plain) "-plain" else ""
        compose.activityRule.scenario.onActivity(ShotRecorder::darkWindow)
        runBlocking {
            repo.ensureSeeded()
            sessionId = repo.startFreeformSession("Warm-up checkoff fixture")
            val exercise = repo.observeExercises().first().first { it.name == "Bench Press" }
            repeat(3) { repo.addExtraSet(sessionId, exercise.id, 8, 50.0, "") }
            val sets = repo.observeSessionSets(sessionId).first()
            for (set in sets.take(2)) repo.setKind(set.id, SetKind.WARMUP)
        }
        val history = mutableStateOf(false)
        val driver = FlowDriver(compose)
        driver.takeTheClock()
        compose.setContent {
            IronvellumTheme {
                Box(Modifier.fillMaxSize().background(IronvellumColors.Abyss)) {
                    if (history.value) WorkoutDetailScreen(sessionId = sessionId, onBack = {})
                    else SessionScreen(sessionId, onExit = {}, onSealed = {})
                }
            }
        }
        val first = warmup(1)
        val second = warmup(2)
        val working = hasContentDescription("Log set 1")
        compose.waitUntil(15_000) {
            compose.mainClock.advanceTimeByFrame()
            compose.onAllNodes(working).fetchSemanticsNodes().isNotEmpty()
        }
        first.assertIsOff().assertHeightIsAtLeast(48.dp).performSemanticsAction(SemanticsActions.OnClick)
        awaitRows { it[0].warmupDone }
        awaitCheckmark(1, ToggleableState.On)
        second.assertIsOff()
        compose.onNode(working).assertExists()
        assertTrue(rows().none { it.done })
        ShotRecorder.take(compose, "warmup-checkoff-live$shotSuffix")

        second.performSemanticsAction(SemanticsActions.OnClick)
        awaitRows { it.filter { set -> set.warmup }.all { set -> set.warmupDone } }
        val folded = app.resources.getQuantityString(R.plurals.session_warmup_fold, 2, 2)
        driver.awaitText(folded)
        first.assertDoesNotExist()
        driver.click(folded)
        second.assertIsOn().performSemanticsAction(SemanticsActions.OnClick)
        awaitRows { !it[1].warmupDone }
        awaitCheckmark(2, ToggleableState.Off)

        compose.onNode(working).performSemanticsAction(SemanticsActions.OnClick)
        awaitRows { it.single { set -> !set.warmup }.done }
        second.assertIsOff()
        runBlocking {
            // Present that fixture in History without paying rewards into the shared test profile.
            val dao = app.database.sessionDao()
            val session = requireNotNull(dao.byId(sessionId))
            dao.updateSession(session.copy(completedAtMs = System.currentTimeMillis()))
        }
        compose.runOnUiThread { history.value = true }
        val completed = app.getString(R.string.session_warmup_completed)
        val planned = app.getString(R.string.session_warmup_planned)
        driver.awaitAnyText { it.startsWith("Warm-up ·") && it.endsWith(completed) }
        driver.awaitAnyText { it.startsWith("Warm-up ·") && it.endsWith(planned) }
        assertEquals(1, rows().count { it.done })
        compose.onNode(hasText(completed, substring = true) and hasText("Warm-up ·", substring = true))
            .performScrollTo().assertIsDisplayed()
        ShotRecorder.take(compose, "warmup-checkoff-history$shotSuffix")
    }

    private fun warmupLabel(number: Int) = app.getString(R.string.session_warmup_completion_description, "Bench Press", number)

    private fun warmup(number: Int) = compose.onNodeWithContentDescription(warmupLabel(number))

    private fun awaitCheckmark(number: Int, state: ToggleableState) {
        val matcher = hasContentDescription(warmupLabel(number)) and SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, state)
        compose.waitUntil(10_000) {
            compose.mainClock.advanceTimeByFrame()
            compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun rows() = runBlocking { repo.observeSessionSets(sessionId).first() }

    private fun awaitRows(predicate: (List<com.ironvellum.app.domain.SessionSet>) -> Boolean) {
        compose.waitUntil(10_000) {
            compose.mainClock.advanceTimeByFrame()
            predicate(rows())
        }
        compose.mainClock.advanceTimeBy(500)
    }
}
