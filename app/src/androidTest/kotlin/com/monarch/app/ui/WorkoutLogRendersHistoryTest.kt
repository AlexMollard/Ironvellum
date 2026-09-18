package com.monarch.app.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.monarch.app.MainActivity
import com.monarch.app.MonarchApp
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The workout log lists EVERY completed session, so it was converted from a
 * scrolling Column to a LazyColumn — a plain Column composes a row per workout
 * whether it is on screen or not, which is a thousand rows after a few years.
 *
 * A conversion like that compiles whatever happens; what it can break is the
 * content. This drives the real screen with a real completed session and checks
 * a month header and a row are rendered, which is what the eager version did.
 */
@RunWith(AndroidJUnit4::class)
class WorkoutLogRendersHistoryTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    /** The label of the session seeded below, which the log must show. */
    private lateinit var sessionLabel: String

    @Before
    fun seedACompletedSession() {
        // The app's own live instance, as the other flow tests do: a second
        // handle on the database while the app holds it open is the divergence
        // trap documented on DbSnapshot.
        val app = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as MonarchApp
        runBlocking {
            (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as MonarchApp)
                .database.openHelper.writableDatabase.execSQL("DELETE FROM sessions")
            val repo = app.repository
            val preset = repo.observePresets().first().first()
            val sessionId = repo.startSessionFromPreset(preset.id)
            val sets = app.database.sessionDao().setsFor(sessionId)
            repo.updateSet(sets.first().id, reps = 6, weightKg = 25.0, done = true)
            repo.completeSession(sessionId)
            sessionLabel = app.database.sessionDao().byId(sessionId)!!.label
        }
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(FRAME_BUDGET_MS)
    }

    /**
     * These tests seed the app's OWN database, so they must leave it as they
     * found it: without this every run adds sessions, the next run measures a
     * different app, and the flow tests that reason about today's quest start
     * failing for reasons nobody can see.
     */
    @After
    fun clearSeededSessions() {
        val app = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as MonarchApp
        app.database.openHelper.writableDatabase.execSQL("DELETE FROM sessions")
    }

    @Test
    fun theLogListsACompletedSessionUnderItsMonth() {
        compose.onAllNodesWithContentDescription("Train").onFirst().performClick()
        compose.mainClock.advanceTimeBy(FRAME_BUDGET_MS)
        compose.onAllNodesWithText("FULL LOG", substring = true).onFirst().performClick()
        compose.mainClock.advanceTimeBy(FRAME_BUDGET_MS)

        val onScreen = compose.onAllNodesWithText("", substring = true).fetchSemanticsNodes().size
        assertTrue("the log screen rendered nothing at all", onScreen > 0)

        // A month header proves the grouping still runs inside the lazy list,
        // and the XP stamp proves a row was actually emitted.
        val headers = compose.onAllNodesWithText("·", substring = true).fetchSemanticsNodes()
        assertTrue("no month group rendered in the lazy list", headers.isNotEmpty())
        // The row's XP is a pill of two separate text nodes ("+120" and "XP"),
        // so assert on the session's own label: that names THIS session's row
        // rather than any text that happens to contain XP.
        val rows = compose.onAllNodesWithText(sessionLabel, substring = true).fetchSemanticsNodes()
        assertTrue("the log did not render the session labelled \"$sessionLabel\"", rows.isNotEmpty())
    }

    private companion object {
        const val FRAME_BUDGET_MS = 1_200L
    }
}
