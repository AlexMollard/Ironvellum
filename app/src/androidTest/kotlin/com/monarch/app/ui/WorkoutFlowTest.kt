package com.monarch.app.ui

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.monarch.app.MainActivity
import com.monarch.app.MonarchApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The whole point of the app, end to end: accept the day's quest, log a set,
 * claim victory, and be paid XP for it.
 *
 * Every piece of this is covered in isolation — the XP curve, the title rules,
 * the DAOs — and none of that proves the loop is wired together. This drives it
 * through the real UI against the real database, which is the only way the
 * wiring is actually exercised.
 *
 * Everything here polls rather than sampling the tree once, and that is not
 * belt-and-braces: the dashboard seeds its catalogue asynchronously, so the
 * quest button does not exist on the first frame, and the session assembles off
 * the main thread behind a "Summoning session…" placeholder. Reading once
 * reports an empty screen and looks exactly like a missing feature — several
 * failures during this test's development were that, not real defects.
 */
@RunWith(AndroidJUnit4::class)
class WorkoutFlowTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    /**
     * Clears logged sessions so the day's quest is always startable.
     *
     * Without this the test passes once and then fails on its own success: the
     * completed quest leaves the dashboard showing "QUEST COMPLETE" with no CTA.
     * It goes through the app's OWN database instance on purpose — opening a
     * second handle to the same file, or deleting the file underneath the
     * running app, is the divergence trap documented on DbSnapshot.
     */
    @Before
    fun clearLoggedSessions() {
        // Onboarding gates the whole app until a profile height exists.
        TestProfile.ensureSetUp()
        val app = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as MonarchApp
        app.database.openHelper.writableDatabase.execSQL("DELETE FROM sessions")
    }

    @Before
    fun takeTheClock() {
        // The ink treatment animates forever, so Compose never idles.
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(FRAME_BUDGET_MS)
    }

    /**
     * With the clock held, one advance is not always enough for a destination
     * swap: the navigation animation and the first layout pass each need frames
     * before a node counts as displayed.
     */
    private fun settle(times: Int = 4) {
        repeat(times) { compose.mainClock.advanceTimeBy(FRAME_BUDGET_MS) }
    }

    /**
     * Polls for a string while feeding frames AND giving the real work time to
     * land. The session is assembled off the main thread ("Summoning session…"
     * is the placeholder), so frames alone never reveal it: the clock is held,
     * but the database is not.
     */
    private fun awaitText(fragment: String, attempts: Int = 60) {
        repeat(attempts) {
            if (allText().any { it.contains(fragment) }) return
            compose.mainClock.advanceTimeBy(FRAME_BUDGET_MS)
            Thread.sleep(POLL_MS)
        }
        error("never saw \"$fragment\"; on screen: ${allText()}")
    }

    /**
     * Every string on screen, across ALL windows.
     *
     * Walking from onRoot() was wrong: the victory and achievement screens are
     * Dialogs, which compose into their own window root, so a root-anchored
     * walk reports the screen behind them and the test cannot see the thing it
     * is waiting for. A matcher query spans every root.
     */
    /**
     * Court opens on today, and today may be a rest day. The week rail carries
     * one letter per weekday, so walk it until the card offers a quest; four of
     * the seven have a seeded program.
     */
    private fun selectATrainingDay() {
        if (allText().none { it == "REST DAY" }) return
        val rail = listOf("M", "T", "W", "T", "F", "S", "S")
        for (index in rail.indices) {
            val letters = compose.onAllNodesWithText(rail[index]).fetchSemanticsNodes()
            if (index >= letters.size) continue
            compose.onAllNodesWithText(rail[index])[index.coerceAtMost(letters.size - 1)]
                .performSemanticsAction(SemanticsActions.OnClick)
            settle()
            if (allText().none { it == "REST DAY" }) return
        }
        error("no weekday offered a program; on screen: ${allText()}")
    }

    private fun allText(): List<String> =
        compose.onAllNodes(
            SemanticsMatcher("has text") { it.config.contains(SemanticsProperties.Text) },
            useUnmergedTree = true,
        ).fetchSemanticsNodes().flatMap { node ->
            node.config.firstOrNull { it.key == SemanticsProperties.Text }?.value?.let { value ->
                @Suppress("UNCHECKED_CAST")
                (value as List<androidx.compose.ui.text.AnnotatedString>).map { it.text }
            }.orEmpty()
        }

    /** The set rows: each carries the checkbox role so TalkBack announces it. */
    private fun setRows() = compose.onAllNodes(
        SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox),
        useUnmergedTree = true,
    )

    @Test
    fun acceptingTheQuestAndLoggingASetPaysXp() {
        val xpBefore = xpFromHeader()

        // The quest CTA depends on state: "Accept Quest" on a fresh day,
        // "Resume" once a session exists, "Start Anyway" on a non-scheduled day.
        // MonarchButton uppercases every label.
        //
        // The seeded programs cover four weekdays, so on the others Court shows
        // REST DAY with no quest at all — this test failed the morning the date
        // rolled into one of them. Pick a day that HAS a program rather than
        // trusting the calendar.
        selectATrainingDay()
        // The quest card arrives after seeding, so poll for the CTA rather
        // than sampling the tree once on the first frame.
        val cta = awaitAnyText { label ->
            label == "ACCEPT QUEST" || label == "START ANYWAY" || label.startsWith("RESUME")
        }
        compose.onAllNodesWithText(cta).onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)
        settle()
        awaitText("TRIAL IN PROGRESS")

        val rows = setRows().fetchSemanticsNodes().size
        assertTrue("the seeded quest must offer sets to log", rows > 0)
        val conqueredBefore = conqueredCount()

        setRows().onFirst().performSemanticsAction(SemanticsActions.OnClick)
        settle()

        // The toggle persists through the database, so the count catches up a
        // moment later rather than on the next frame.
        awaitText("${conqueredBefore + 1} /")
        assertEquals(
            "checking a set must record it against the session",
            conqueredBefore + 1,
            conqueredCount(),
        )

        // Invoke the button's own click action rather than a coordinate tap: it
        // sits at the end of a long scrolling session, so a synthetic tap after
        // scrolling lands on whatever moved under it. The wiring is the point.
        compose.onAllNodesWithText("CLAIM VICTORY").onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)
        // Completion stacks celebrations: the victory screen, then one page per
        // award earned. Drain them by their own buttons.
        drainCelebrations()

        // Completion pays XP. The dashboard header is the user-visible proof,
        // and it is what a broken award silently leaves at zero.
        val xpAfter = xpFromHeader()
        assertTrue(
            "completing a workout must award XP (before=$xpBefore after=$xpAfter)",
            xpAfter > xpBefore,
        )
    }

    /** "0 / 17 sets conquered" -> 0 */
    private fun conqueredCount(): Int {
        val line = allText().firstOrNull { it.contains("sets conquered") }
            ?: error("the session screen must show the conquered count")
        return line.substringBefore('/').trim().toInt()
    }

    /** Polls until some string matches, and returns it. */
    private fun awaitAnyText(attempts: Int = 60, predicate: (String) -> Boolean): String {
        repeat(attempts) {
            allText().firstOrNull(predicate)?.let { return it }
            compose.mainClock.advanceTimeBy(FRAME_BUDGET_MS)
            Thread.sleep(POLL_MS)
        }
        error("nothing matched; on screen: ${allText()}")
    }

    /**
     * Clicks through every celebration overlay until the dashboard is back.
     * Each overlay's advance button reads "Continue", or "Next (1/3)" while
     * more award pages remain.
     */
    private fun drainCelebrations(rounds: Int = 12) {
        repeat(rounds) {
            if (allText().any { it.endsWith("XP") && it.contains('/') }) return
            val advance = allText().firstOrNull { it == "CONTINUE" || it.startsWith("NEXT (") }
            if (advance != null) {
                compose.onAllNodesWithText(advance).onFirst()
                    .performSemanticsAction(SemanticsActions.OnClick)
            }
            settle()
            Thread.sleep(POLL_MS)
        }
    }

    /** "0 / 100 XP" on the dashboard header -> 0 */
    private fun xpFromHeader(): Int {
        // Poll rather than assume where completion lands: the victory screen
        // dismisses back to the dashboard, but only once its own work settles.
        awaitText("XP")
        val header = allText().firstOrNull { it.endsWith("XP") && it.contains('/') }
            ?: error("no XP header on screen; saw: ${allText()}")
        return header.substringBefore('/').trim().toInt()
    }

    private companion object {
        const val FRAME_BUDGET_MS = 1_200L
        const val POLL_MS = 100L
    }
}
