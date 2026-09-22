package com.ironvellum.app.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction

/**
 * Shared driving for the UI flow tests, because every one of them hits the same
 * three facts about this app:
 *
 *  - the ink treatment animates forever, so Compose never idles and the clock
 *    has to be held and advanced by hand;
 *  - screens arrive asynchronously (catalogue seeding, session assembly), so
 *    every step polls instead of sampling the tree once;
 *  - the celebration screens are Dialogs in their own window root, so text has
 *    to be collected with a matcher query rather than walked from onRoot().
 *
 * Getting any of these wrong looks exactly like a missing feature, which is why
 * it lives in one place with the reasons attached.
 */
class FlowDriver(private val compose: ComposeTestRule) {

    fun takeTheClock() {
        compose.mainClock.autoAdvance = false
        settle()
    }

    fun settle(times: Int = 4) {
        repeat(times) { compose.mainClock.advanceTimeBy(FRAME_BUDGET_MS) }
    }

    /** Every string on screen, across all window roots. */
    fun allText(): List<String> =
        compose.onAllNodes(
            SemanticsMatcher("has text") { it.config.contains(SemanticsProperties.Text) },
            useUnmergedTree = true,
        ).fetchSemanticsNodes().flatMap { node ->
            node.config.firstOrNull { it.key == SemanticsProperties.Text }?.value?.let { value ->
                @Suppress("UNCHECKED_CAST")
                (value as List<androidx.compose.ui.text.AnnotatedString>).map { it.text }
            }.orEmpty()
        }

    /** Polls until a string matches, feeding frames and letting real work land. */
    fun awaitAnyText(attempts: Int = 60, predicate: (String) -> Boolean): String {
        repeat(attempts) {
            allText().firstOrNull(predicate)?.let { return it }
            compose.mainClock.advanceTimeBy(FRAME_BUDGET_MS)
            Thread.sleep(POLL_MS)
        }
        error("nothing matched; on screen: ${allText()}")
    }

    fun awaitText(fragment: String) = awaitAnyText { it.contains(fragment) }

    /**
     * Clicks a control by its label, targeting the MERGED node: the click
     * action lives there, while the unmerged tree exposes only the inner Text.
     * Uses the semantics action rather than a coordinate tap so a control at the
     * end of a long scroll is still reachable.
     */
    fun click(label: String) {
        compose.onAllNodesWithText(label).onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)
        settle()
    }

    fun tab(contentDescription: String) {
        compose.onAllNodes(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ContentDescription,
                listOf(contentDescription),
            ),
        ).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        settle()
    }

    private companion object {
        /** Long enough for a destination swap to compose and lay out. */
        const val FRAME_BUDGET_MS = 1_200L
        const val POLL_MS = 100L
    }
}
