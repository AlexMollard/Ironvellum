package com.monarch.app.ui

import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
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

    /**
     * Controls an accessibility service would announce as nothing useful.
     *
     * A label of "−" or "›" passes a naive non-blank check and still tells a
     * screen-reader user nothing: it is read as a punctuation character, not
     * as what the control does. An icon-forward UI has to name its glyphs.
     */
    private fun unlabelledControls(): List<String> {
        val nodes = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        return nodes.filter { it.size.width > 0 }.mapNotNull { node ->
            val described = node.config.valueOrNull(SemanticsProperties.ContentDescription)
                ?.any { it.saysSomething() } == true
            val texted = node.config.valueOrNull(SemanticsProperties.Text)
                ?.any { it.text.saysSomething() } == true
            if (described || texted) null
            else {
                val announced = node.config.valueOrNull(SemanticsProperties.Text)
                    ?.joinToString(" ") { it.text }.orEmpty()
                "role=${node.config.valueOrNull(SemanticsProperties.Role)} " +
                    "announces=\"$announced\" at ${node.boundsInRoot}"
            }
        }
    }

    /** A label has to carry a word, not just a symbol. */
    private fun String.saysSomething(): Boolean = any { it.isLetterOrDigit() }

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

    /**
     * WCAG 2.5.8 AA sets 24dp as the floor for a target; Material's 48dp is the
     * stricter platform guidance. Both are enforced where they belong: 48dp for
     * the nav bar above, 24dp for EVERY tappable control here.
     *
     * The 24-48 band is deliberately left alone: the dense chips and list rows
     * sit inside padded panels, and forcing them to 48dp would relayout the
     * information design. That is the owner's call, not a defect.
     */
    private fun controlsBelowTheAccessibleFloor(): List<String> {
        val density = compose.density.density
        val minPx = WCAG_FLOOR_DP * density
        return compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()
            .filter { it.size.width > 0 }
            .mapNotNull { node ->
                // node.size is the LAID-OUT size; boundsInRoot is clipped to the
                // visible region, so a control half-scrolled off the screen
                // measured 23dp and read as a defect. The touch target is the
                // layout size, not how much of it happens to be on screen.
                val size = node.size
                if (size.width >= minPx && size.height >= minPx) return@mapNotNull null
                val label = node.config.valueOrNull(SemanticsProperties.Text)
                    ?.joinToString(" ") { it.text }
                    ?: node.config.valueOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")
                    ?: "unnamed"
                "\"%s\" %.0fx%.0f dp".format(label, size.width / density, size.height / density)
            }
    }

    @Test
    fun everyTappableControlIsAnnounceableAndHittable() {
        val unlabelled = mutableListOf<String>()
        val tooSmall = mutableListOf<String>()

        for (destination in DESTINATIONS) {
            compose.onNodeWithContentDescription(destination).performClick()
            compose.mainClock.advanceTimeBy(FRAME_BUDGET_MS)
            unlabelled += unlabelledControls().map { "$destination: $it" }
            tooSmall += controlsBelowTheAccessibleFloor().map { "$destination: $it" }
        }

        assertEquals(
            "tappable controls an accessibility service cannot announce",
            emptyList<String>(),
            unlabelled,
        )
        assertEquals(
            "tappable controls below the WCAG floor of ${WCAG_FLOOR_DP.toInt()}dp",
            emptyList<String>(),
            tooSmall,
        )
        assertEquals(
            "navigation targets below the ${MIN_TARGET_DP.toInt()}dp minimum",
            emptyList<String>(),
            navTargetsBelowMinimum(),
        )
    }

    /**
     * Walks back until the nav bar is on screen, so one full-screen surface
     * cannot strand the rest of the sweep.
     */
    private fun returnToNavigation() {
        repeat(4) {
            val navPresent = compose.onAllNodesWithContentDescription("Court")
                .fetchSemanticsNodes().isNotEmpty()
            if (navPresent) return
            compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            compose.mainClock.advanceTimeBy(FRAME_BUDGET_MS)
        }
    }

    /**
     * Opens a sub-surface by visible text, falling back to a content
     * description for icon-only entry points. Returns false when this build
     * shows neither, so a missing feature is skipped rather than failed.
     */
    private fun openSurface(label: String): Boolean {
        compose.mainClock.advanceTimeBy(FRAME_BUDGET_MS)
        val byText = compose.onAllNodesWithText(label, substring = true)
        if (byText.fetchSemanticsNodes().isNotEmpty()) {
            byText.onFirst().performClick()
            return true
        }
        val byDescription = compose.onAllNodesWithContentDescription(label, substring = true)
        if (byDescription.fetchSemanticsNodes().isNotEmpty()) {
            byDescription.onFirst().performClick()
            return true
        }
        return false
    }

    /**
     * The six destinations are only the front door. This app is deliberately
     * icon-forward, so the screens BEHIND each tab are where an unlabelled
     * glyph hides — and a control an accessibility service cannot announce is
     * invisible to the hunter using one, however good it looks.
     */
    @Test
    fun surfacesBehindEachTabAreAnnounceableToo() {
        val unlabelled = mutableListOf<String>()
        val tooSmall = mutableListOf<String>()
        val visited = mutableListOf<String>()

        for (path in DEEPER_SURFACES) {
            // Some surfaces replace the nav bar entirely, so each iteration
            // walks back to it rather than assuming it is still there.
            returnToNavigation()
            compose.onNodeWithContentDescription(path.first()).performClick()
            // A surface is opened by whatever names it: visible text for tabs
            // and buttons, a content description for icon-only controls like
            // the settings gear. Matching text alone silently skipped the
            // settings screen, which made its assertions pass vacuously.
            if (path.drop(1).any { !openSurface(it) }) continue
            compose.mainClock.advanceTimeBy(FRAME_BUDGET_MS)
            val where = path.joinToString("/")
            visited += where
            unlabelled += unlabelledControls().map { "$where: $it" }
            tooSmall += controlsBelowTheAccessibleFloor().map { "$where: $it" }
        }

        // Skipping is deliberate for a surface a build does not show, but a
        // wholesale skip would make every assertion below pass vacuously —
        // so the sweep has to prove it actually went somewhere.
        assertEquals(
            "surfaces the sweep could not reach: " +
                "${DEEPER_SURFACES.map { it.joinToString("/") } - visited.toSet()}",
            DEEPER_SURFACES.size,
            visited.size,
        )
        assertEquals(
            "controls an accessibility service cannot announce, behind a tab",
            emptyList<String>(),
            unlabelled,
        )
        assertEquals(
            "controls below the WCAG floor of ${WCAG_FLOOR_DP.toInt()}dp, behind a tab",
            emptyList<String>(),
            tooSmall,
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> SemanticsConfiguration.valueOrNull(key: SemanticsPropertyKey<T>): T? =
        firstOrNull { it.key == key }?.value as? T

    private companion object {
        val DESTINATIONS = listOf("Train", "Stats", "Codex", "Guild", "Shadow", "Court")

        /**
         * Click paths: the destination's content description, then each label
         * to open in turn. Only surfaces that need no account, so the sweep
         * never depends on a signed-in session.
         */
        val DEEPER_SURFACES = listOf(
            listOf("Codex", "SKILL TREE"),
            listOf("Codex", "JOURNAL"),
            listOf("Stats", "DETAIL"),
            listOf("Stats", "TRAINING"),
            listOf("Stats", "ACTIVITY"),
            listOf("Train", "EXERCISE EXPLORER"),
            listOf("Train", "FULL WORKOUT LOG"),
            // Two levels down, and the densest screens in the app: the skill
            // sheet and the preset editor are wall-to-wall glyph steppers,
            // which is exactly where an unannounceable control hides.
            listOf("Codex", "SKILL TREE", "Dead Hang"),
            // The load stepper only composes once load is on: reach it through
            // its own entry point rather than leaving those glyphs unmeasured.
            listOf("Codex", "SKILL TREE", "Dead Hang", "ADD LOAD"),
            listOf("Train", "[ EDIT ]"),
            // The settings screen replaces the nav bar, so it goes late.
            listOf("Court", "System"),
            // Truly last: starting a session leaves a live trial whose abandon
            // prompt sits between the sweep and the nav bar.
            listOf("Train", "QUICK SESSION"),
        )
        const val FRAME_BUDGET_MS = 1_200L
        const val MIN_TARGET_DP = 48f
        const val WCAG_FLOOR_DP = 24f
    }
}
