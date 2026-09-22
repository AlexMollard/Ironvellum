package com.monarch.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import com.monarch.app.MainActivity
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
        // Court is the launch destination; assert it rendered before navigating.
        assertShows("STEPS")

        open("Train", "TRAINING GROUNDS")
        open("Stats", "STATUS WINDOW")
        open("Codex", "SKILL TREE")
        open("Guild", "THE FRONTLINE")
        open("Shadow", "THE SHADOW ARMY")

        // Returning to the launch destination must also work: a nav graph that
        // only travels outward is a real failure mode.
        open("Court", "STEPS")
    }

    private companion object {
        /** Long enough for a destination swap to compose and lay out. */
        const val FRAME_BUDGET_MS = 1_200L
    }
}
