package com.ironvellum.app.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.MainActivity
import com.ironvellum.app.IronvellumApp
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
 * the main thread behind a "Summoning the trial…" placeholder. Reading once
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
            .targetContext.applicationContext as IronvellumApp
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
     * land. The session is assembled off the main thread ("Summoning the trial…"
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
     * Today opens on today, and today may be a rest day. The week rail carries
     * one letter per weekday, so walk it until the card offers a quest; four of
     * the seven have a seeded program.
     */
    private fun selectATrainingDay() {
        // Decide only once the day card has loaded. Sampled on the first
        // frame, a rest day had not rendered yet, so the walk was skipped and
        // the test then waited for a quest that never came.
        awaitAnyText { it == "Respite" || isQuestCard(it) }
        if (allText().none { it == "Respite" }) return
        val rail = listOf("M", "T", "W", "T", "F", "S", "S")
        for (index in rail.indices) {
            val letters = compose.onAllNodesWithText(rail[index]).fetchSemanticsNodes()
            if (index >= letters.size) continue
            compose.onAllNodesWithText(rail[index])[index.coerceAtMost(letters.size - 1)]
                .performSemanticsAction(SemanticsActions.OnClick)
            settle()
            if (allText().none { it == "Respite" }) return
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

    /**
     * The footer's tick for the next set ("Log set 2"): it logs the open
     * exercise's next set, and is the only control that logs one.
     */
    private fun setRows() = compose.onAllNodes(
        hasContentDescription("Log set", substring = true),
        useUnmergedTree = true,
    )

    @Test
    fun acceptingTheQuestAndLoggingASetPaysXp() {
        val xpBefore = xpFromHeader()

        // The quest CTA depends on state: "Accept Quest" on a fresh day,
        // "Start Workout" on a non-scheduled day.
        // The seeded programs cover four weekdays, so on the others Today shows
        // REST DAY with no quest at all — this test failed the morning the date
        // rolled into one of them. Pick a day that HAS a program rather than
        // trusting the calendar.
        selectATrainingDay()
        // The quest card arrives after seeding, so poll for the CTA rather
        // than sampling the tree once on the first frame.
        // The picked day may not be today, where the card's own Begin shows:
        // tapping the card opens the rite's page, which always has one.
        val plan = awaitAnyText(predicate = ::isQuestCard)
        compose.onAllNodesWithText(plan).onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)
        settle()
        val cta = awaitAnyText { it.startsWith("Begin ") || it.startsWith("Continue ") }
        compose.onAllNodesWithText(cta).onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)
        settle()
        awaitText("Trial in progress")

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

        // Invoke the slider's own "Seal the trial" action rather than dragging
        // it: it sits at the end of a long scrolling list, and the action is
        // the same route a screen-reader user takes. The wiring is the point.
        compose.onAllNodesWithContentDescription("Seal the trial").onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)
        // One logged set among many raises the unlogged-sets confirm; seal
        // through it. Exact "Seal anyway" exists only in that dialog. It
        // composes a beat after the slide ends, so poll instead of assuming it
        // is already there.
        var claimed = false
        repeat(30) {
            val node = compose.onAllNodesWithText("Seal anyway").fetchSemanticsNodes().firstOrNull()
            if (node != null) {
                compose.onAllNodesWithText("Seal anyway").onFirst()
                    .performSemanticsAction(SemanticsActions.OnClick)
                claimed = true
            }
            if (claimed) return@repeat
            compose.mainClock.advanceTimeBy(FRAME_BUDGET_MS)
            Thread.sleep(POLL_MS)
        }
        check(claimed) { "the unlogged-sets confirm never composed; on screen: ${allText()}" }
        // Completion stacks pages: level-up and deeds when earned, then the
        // summary. Drain them by their own buttons.
        drainCelebrations()

        // Completion pays XP. The dashboard header is the user-visible proof,
        // and it is what a broken award silently leaves at zero.
        val xpAfter = xpFromHeader()
        assertTrue(
            "completing a workout must award XP (before=$xpBefore after=$xpAfter)",
            xpAfter > xpBefore,
        )
    }

    /** "0 / 17 sets" -> 0 */
    private fun conqueredCount(): Int {
        val line = allText().firstOrNull { it.matches(Regex("""\d+ / \d+ sets?""")) }
            ?: error("the session screen must show the logged count")
        return line.substringBefore('/').trim().toInt()
    }

    /** The day card's plan line, "5 exercises · 19 sets · about 54 min": a scheduled rite is showing. */
    private fun isQuestCard(label: String): Boolean = label.contains(" sets · about ")

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
     * Clicks through the celebration until the dashboard is back: "Continue" on
     * the level-up and deeds pages (when the seal earned them), "Done" on the
     * summary. A session that differs from its preset then offers a routine
     * update; this test keeps the routine so the seeded preset stays as later
     * tests expect it. It is finished once something was clicked and nothing
     * left to click is on screen.
     */
    private fun drainCelebrations(rounds: Int = 24) {
        var clicked = 0
        repeat(rounds) {
            val advance = allText().firstOrNull { it == "Continue" || it == "Done" || it == "Keep rite" }
            if (advance == null && clicked > 0) return
            if (advance != null) {
                compose.onAllNodesWithText(advance).onFirst()
                    .performSemanticsAction(SemanticsActions.OnClick)
                clicked += 1
            }
            settle()
            Thread.sleep(POLL_MS)
        }
    }

    /** "⏱ 1 min · LEVEL 1 · 55 / 100 XP" on the victory screen -> 55 */
    private fun xpFromHeader(): Int {
        // Poll rather than assume where completion lands: the victory screen
        // dismisses back to the dashboard, but only once its own work settles.
        awaitText("XP")
        val header = allText().firstOrNull { it.endsWith("XP") && it.contains('/') }
            ?: error("no XP header on screen; saw: ${allText()}")
        val pair = header.substringBefore(" XP").trim().substringAfterLast("· ")
        return pair.substringBefore('/').trim().toInt()
    }

    private companion object {
        const val FRAME_BUDGET_MS = 1_200L
        const val POLL_MS = 100L
    }
}
