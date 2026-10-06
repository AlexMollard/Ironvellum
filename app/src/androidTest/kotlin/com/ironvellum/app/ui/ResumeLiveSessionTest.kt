package com.ironvellum.app.ui

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.IronvellumApp
import com.ironvellum.app.MainActivity
import com.ironvellum.app.data.Repository
import com.ironvellum.app.data.db.SessionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Leaving mid-workout used to land the lifter on Today with a Start button, as
 * though the trial were lost. A recent one now reopens on launch; an old one
 * is offered on Today, never forced.
 */
@RunWith(AndroidJUnit4::class)
class ResumeLiveSessionTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = InstrumentationRegistry.getInstrumentation()
        .targetContext.applicationContext as IronvellumApp

    @Before
    fun setUp() = TestProfile.ensureSetUp()

    private fun plantLiveSession(label: String, ageMs: Long) = runBlocking {
        app.database.sessionDao().insertSession(
            SessionEntity(
                presetId = app.repository.observePresets().first().first().id,
                label = label,
                startedAtMs = System.currentTimeMillis() - ageMs,
                completedAtMs = null,
                xpAwarded = 0,
                strengthScore = 0,
                title = "",
                note = "",
                privateNote = "",
            ),
        )
    }

    private fun waitForText(text: String) = compose.waitUntil(10_000) {
        compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
    }

    private fun shows(text: String) =
        compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun aRecentLiveSessionReopensOnLaunch() {
        plantLiveSession("Left Mid-Set", ageMs = 20 * 60_000L)
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForText("Trial in progress")
            assertTrue(shows("Left Mid-Set"))
        }
    }

    @Test
    fun aStaleLiveSessionIsOfferedNotForced() {
        plantLiveSession("Yesterday's Trial", ageMs = Repository.RESUME_WINDOW_MS + 60 * 60_000L)
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForText("THE VEIL")
            // The launch check reads the database off the main thread, so give
            // it the time a forced resume would take before judging.
            val forced = runCatching {
                compose.waitUntil(3_000) { shows("Trial in progress") }
            }.isSuccess
            assertTrue("a stale trial must not be forced open", !forced)
        }
    }
}
