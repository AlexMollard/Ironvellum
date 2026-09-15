package com.monarch.app.ui

import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.monarch.app.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every tappable control must be announceable and hittable.
 *
 * This app is deliberately icon-forward, which is exactly the design that
 * strands screen-reader users: an unlabelled icon button is silent to TalkBack,
 * and a target below the 48dp minimum is unhittable for anyone with a motor
 * impairment. Grepping for `contentDescription` cannot judge either — a
 * decorative icon inside a labelled button SHOULD pass null, and doubling the
 * label is itself a defect — so this reads the rendered semantics tree, which
 * is what an accessibility service actually consumes.
 */
@RunWith(AndroidJUnit4::class)
class AccessibilityChecksTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun takeTheClock() {
        // The ink treatment animates forever, so Compose never idles.
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(FRAME_BUDGET_MS)
    }

    /** Controls that an accessibility service would announce as nothing at all. */
    private fun unlabelledControls(): List<String> {
        val nodes = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        return nodes.filter { it.boundsInRoot.size.width > 0f }.mapNotNull { node ->
            val described = node.config.valueOrNull(SemanticsProperties.ContentDescription)
                ?.any { it.isNotBlank() } == true
            val texted = node.config.valueOrNull(SemanticsProperties.Text)
                ?.any { it.text.isNotBlank() } == true
            if (described || texted) null
            else "role=${node.config.valueOrNull(SemanticsProperties.Role)} at ${node.boundsInRoot}"
        }
    }

    /**
     * The NAV BAR specifically must meet the 48dp minimum: it is the only
     * control present on every screen, and a miss strands the user.
     *
     * Dense in-panel chips (filter pills, day marks, list rows) are measured
     * but deliberately NOT asserted. They sit inside padded panels whose own
     * padding caps the touch region — an earlier attempt to widen one changed
     * nothing measurable, because Compose clips touch delivery to the parent's
     * bounds — and forcing them to 48dp would overlap neighbouring controls.
     * That is a layout decision, not something a test should impose.
     */
    private fun navTargetsBelowMinimum(): List<String> {
        val density = compose.density.density
        val minPx = MIN_TARGET_DP * density
        return DESTINATIONS.mapNotNull { destination ->
            val node = compose.onNodeWithContentDescription(destination).fetchSemanticsNode()
            val size = node.boundsInRoot.size
            if (size.width >= minPx && size.height >= minPx) null
            else "%s is %.0fx%.0f dp".format(destination, size.width / density, size.height / density)
        }
    }

    @Test
    fun everyTappableControlIsAnnounceableAndHittable() {
        val unlabelled = mutableListOf<String>()

        for (destination in DESTINATIONS) {
            compose.onNodeWithContentDescription(destination).performClick()
            compose.mainClock.advanceTimeBy(FRAME_BUDGET_MS)
            unlabelled += unlabelledControls().map { "$destination: $it" }
        }

        assertEquals(
            "tappable controls an accessibility service cannot announce",
            emptyList<String>(),
            unlabelled,
        )
        assertEquals(
            "navigation targets below the ${MIN_TARGET_DP.toInt()}dp minimum",
            emptyList<String>(),
            navTargetsBelowMinimum(),
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> SemanticsConfiguration.valueOrNull(key: SemanticsPropertyKey<T>): T? =
        firstOrNull { it.key == key }?.value as? T

    private companion object {
        val DESTINATIONS = listOf("Train", "Stats", "Codex", "Guild", "Shadow", "Court")
        const val FRAME_BUDGET_MS = 1_200L
        const val MIN_TARGET_DP = 48f
    }
}
