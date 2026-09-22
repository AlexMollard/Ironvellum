package com.monarch.app.ui

import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.monarch.app.MainActivity
import com.monarch.app.MonarchApp
import com.monarch.app.data.db.SessionEntity
import com.monarch.app.data.db.SetLogEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The lists that grow for as long as a hunter trains are capped, and a cap is
 * the kind of thing that reads as correct and does nothing: `take` on an
 * unsorted map keeps arbitrary rows, a cap applied to the wrong collection
 * keeps them all, and either way the screen looks fine with five sessions in
 * the database.
 *
 * So seed far more history than any cap allows and check the screens against
 * it. The Train activity log must show a bounded number of rows while the full
 * log — the one screen whose purpose is the whole record — must still show far
 * more, because it is a lazy list rather than a capped one.
 */
@RunWith(AndroidJUnit4::class)
class CappedListsHoldAtScaleTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun seedManySessions() {
        // Onboarding gates the whole app until a profile height exists.
        TestProfile.ensureSetUp()
        val app = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as MonarchApp
        runBlocking {
            (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as MonarchApp)
                .database.openHelper.writableDatabase.execSQL("DELETE FROM sessions")
            val exercises = app.database.exerciseDao().observeAll().first()
            val day = 86_400_000L
            val start = System.currentTimeMillis() - SEEDED * day
            repeat(SEEDED) { index ->
                val startedAt = start + index * day
                val sessionId = app.database.sessionDao().insertSession(
                    SessionEntity(
                        presetId = null,
                        label = "Campaign $index",
                        startedAtMs = startedAt,
                        completedAtMs = startedAt + 3_600_000L,
                        xpAwarded = 90,
                        strengthScore = 300,
                        title = "",
                        note = "",
                        privateNote = "",
                    ),
                )
                app.database.sessionDao().insertSets(
                    listOf(
                        SetLogEntity(
                            sessionId = sessionId,
                            exerciseId = exercises[index % exercises.size].id,
                            exercisePosition = 0,
                            setIndex = 0,
                            reps = 8,
                            weightKg = 20.0,
                            modifiers = "",
                            done = true,
                        ),
                    ),
                )
            }
        }
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(FRAME_BUDGET_MS)
    }

    /**
     * The clock is held because the ink treatment never stops animating, so a
     * Room flow arriving after navigation needs frames fed to it rather than a
     * single advance. Poll until the rows appear.
     */
    private fun countRowsWhenSettled(label: String): Int {
        var seen = 0
        repeat(40) {
            compose.mainClock.advanceTimeBy(200)
            seen = compose.onAllNodesWithText(label, substring = true).fetchSemanticsNodes().size
            if (seen > 0) return seen
        }
        return seen
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
    fun theActivityLogIsBoundedWhileTheFullLogIsNot() {
        compose.onAllNodesWithContentDescription("Train").onFirst().performClick()
        compose.mainClock.advanceTimeBy(FRAME_BUDGET_MS)

        // Every seeded session is labelled "Campaign N", so counting those rows
        // counts exactly the history this screen chose to render.
        val onTrain = countRowsWhenSettled("Campaign")
        assertTrue(
            "the Train activity log rendered $onTrain of $SEEDED sessions; it is supposed to be capped",
            onTrain in 1..CAP_CEILING,
        )

        compose.onAllNodesWithText("FULL LOG", substring = true).onFirst().performClick()
        compose.mainClock.advanceTimeBy(FRAME_BUDGET_MS)

        // A lazy list composes only the viewport, so counting once would say
        // "5" no matter how much history exists — that is the behaviour being
        // relied on, not a fault. Scroll and accumulate instead: the full log
        // must be able to reach far more sessions than the capped screen shows.
        assertTrue("the full log rendered nothing", countRowsWhenSettled("Campaign") > 0)
        val seen = mutableSetOf<String>()
        repeat(12) {
            compose.onAllNodesWithText("Campaign", substring = true).fetchSemanticsNodes()
                .forEach { node ->
                    node.config.firstOrNull { it.key.name == "Text" }?.value?.let { seen += it.toString() }
                }
            compose.onAllNodes(hasScrollAction()).onFirst()
                .performTouchInput { swipeUp() }
            compose.mainClock.advanceTimeBy(FRAME_BUDGET_MS)
        }
        assertTrue(
            "scrolling the full log reached ${seen.size} distinct sessions, no more than the capped screen shows",
            seen.size > CAP_CEILING,
        )
    }

    private companion object {
        /** Far more than any cap in the app. */
        const val SEEDED = 60

        /** The largest cap on a Train-side list, plus room for the layout. */
        const val CAP_CEILING = 12
        const val FRAME_BUDGET_MS = 1_200L
    }
}
