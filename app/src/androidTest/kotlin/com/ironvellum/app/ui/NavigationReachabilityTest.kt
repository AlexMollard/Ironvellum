package com.ironvellum.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import com.ironvellum.app.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every bottom-nav destination must open and render its own content.
 *
 * This defends the defect class that has actually shipped here: a screen that
 * compiles, passes the JVM suite, and is either never routed or throws the
 * moment it composes. An unused public composable is legal Kotlin, so nothing
 * else in the build catches it — it was previously found by a human looking for
 * a missing feature.
 *
 * Each destination is asserted on a heading unique to it, so a nav entry
 * pointing at the wrong screen fails rather than passing on shared chrome.
 */
@RunWith(AndroidJUnit4::class)
class NavigationReachabilityTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    /**
     * The ink treatment runs animations that never end — breathing gradients,
     * the spinner, drifting motes — so Compose is never idle and the default
     * synchronisation times out with ComposeNotIdleException. Driving the clock
     * by hand is the supported way to test a screen that always animates.
     */
    @Before
    fun takeTheClock() {
        // Onboarding gates the whole app until a profile height exists.
        TestProfile.ensureSetUp()
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(FRAME_BUDGET_MS)
    }

    /**
     * A heading can legitimately appear more than once (a tab pill plus the
     * section title, or body copy quoting it), so assert that the destination
     * rendered at least one of them rather than demanding a unique node.
     */
    private fun assertShows(heading: String) {
        compose.onAllNodesWithText(heading, substring = true, useUnmergedTree = true)
            .onFirst()
            .assertIsDisplayed()
    }

    private fun open(navLabel: String, heading: String) {
        compose.onNodeWithContentDescription(navLabel).performClick()
        compose.mainClock.advanceTimeBy(FRAME_BUDGET_MS)
        assertShows(heading)
    }

    @Test
    fun everyDestinationOpensItsOwnScreen() {
        // Today is the launch destination; assert it rendered before navigating.
        assertShows("Oath")

        open("Train", "TRAIN")
        open("Ledger", "THE LEDGER")
        open("Codex", "PATHS")
        // Signed out (the test profile is), Allies is the account screen alone.
        open("Allies", "ALLIES")

        // The Garrison left the nav bar for Today's footer.
        open("Today", "Oath")
        val veil = compose.onNode(SemanticsMatcher("Today’s Open the Veil action") {
            it.config.getOrNull(SemanticsActions.OnClick)?.label == "Open the Veil"
        })
        veil.assertIsDisplayed().performSemanticsAction(SemanticsActions.OnClick)
        val back = compose.onNodeWithText("BACK")
        compose.waitUntil(6_000) {
            compose.mainClock.advanceTimeByFrame()
            back.isDisplayed()
        }
        back.assertIsDisplayed().performClick()
        compose.waitUntil(6_000) {
            compose.mainClock.advanceTimeByFrame()
            veil.isDisplayed()
        }
        assertShows("Oath")
    }

    private companion object {
        /** Long enough for a destination swap to compose and lay out. */
        const val FRAME_BUDGET_MS = 1_200L
    }
}
