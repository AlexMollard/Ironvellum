package com.ironvellum.app.ui

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.IronvellumApp
import com.ironvellum.app.MainActivity
import com.ironvellum.app.data.Repository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A routine that names one movement twice (the owner's dip-bar day: two Push-up entries, one incline and one
 * deficit) opens as two exercises, each with its own modifiers, not as one exercise with six sets.
 */
@RunWith(AndroidJUnit4::class)
class RepeatedMovementSessionTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = InstrumentationRegistry.getInstrumentation()
        .targetContext.applicationContext as IronvellumApp

    @Before
    fun setUp() = TestProfile.ensureSetUp()

    private fun count(text: String) =
        compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size

    private fun waitForText(text: String) = compose.waitUntil(15_000) { count(text) > 0 }

    @Test
    fun theRoutineWithPushUpTwiceStartsAsTwoPushUpBlocksWithTheirOwnModifiers() = runBlocking {
        val repo = app.repository
        val dip = repo.exerciseIdByName("Dip")!!
        val pushUp = repo.exerciseIdByName("Push-up")!!
        app.database.openHelper.writableDatabase.execSQL("DELETE FROM sessions")
        val rite = repo.savePreset(
            null, "Dip bars volume", "", null,
            listOf(
                Repository.PresetDraftEntry(dip, 3, 6, null),
                Repository.PresetDraftEntry(pushUp, 3, 8, 10.0, modifiers = "weighted, incline"),
                Repository.PresetDraftEntry(pushUp, 3, 8, 10.0, modifiers = "weighted, deficit, elevated"),
            ),
        )
        repo.startSessionFromPreset(rite)
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForText("Trial in progress")
            waitForText("Push-up")
            // The Dip card is open; each Push-up entry is a folded row of its own.
            assertEquals("two Push-up blocks, not one", 2, count("Push-up"))
            assertEquals(0, count("incline"))
            compose.onAllNodesWithText("Push-up", substring = true)[0].performClick()
            waitForText("weighted · incline")
            assertEquals(0, count("deficit"))
            // Opening the other one shows its own modifiers.
            compose.waitUntil(5_000) { count("Push-up") == 2 }
            compose.onAllNodesWithText("Push-up", substring = true)[1].performClick()
            waitForText("weighted · deficit · elevated")
            assertTrue(count("weighted · incline") == 0)
        }
    }
}
