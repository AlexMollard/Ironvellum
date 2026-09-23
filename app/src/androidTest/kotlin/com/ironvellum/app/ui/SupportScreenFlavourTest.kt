package com.ironvellum.app.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ironvellum.app.BuildConfig
import com.ironvellum.app.MainActivity
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Support screen must match its flavour. Play policy removed apps for
 * in-app donation links, so the play build may contain no tappable
 * Sponsor/Donate/Liberapay control anywhere; the foss build is where
 * donations live and those buttons must actually exist. Asserting on both
 * branches keeps the screen from silently drifting to the wrong content.
 */
@RunWith(AndroidJUnit4::class)
class SupportScreenFlavourTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun takeTheClock() {
        TestProfile.ensureSetUp()
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(FRAME_BUDGET_MS)
    }

    /** A tappable control whose label reads [label]; buttons render their label in capitals. */
    private fun control(label: String) = hasText(label, ignoreCase = true) and hasClickAction()

    private fun openSupport() {
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.mainClock.advanceTimeBy(FRAME_BUDGET_MS)
        // The entry sits below the fold. performScrollTo animates, and this
        // test holds the clock still, so it would wait forever; the click
        // action is what TalkBack sends and needs no on-screen bounds.
        compose.onNode(
            control(if (BuildConfig.SUPPORT_LINKS) "Support Ironvellum" else "About Ironvellum"),
        ).performSemanticsAction(SemanticsActions.OnClick)
        compose.mainClock.advanceTimeBy(FRAME_BUDGET_MS)
    }

    @Test
    fun donationControlsMatchFlavour() {
        openSupport()
        val expected = if (BuildConfig.SUPPORT_LINKS) 1 else 0
        listOf("Sponsor on GitHub", "Donate on Liberapay", "View the Ledger").forEach { label ->
            compose.onAllNodes(control(label)).assertCountEquals(expected)
        }
    }

    private companion object {
        /** Matches the frame budget the other navigation tests use. */
        const val FRAME_BUDGET_MS = 1_200L
    }
}
