package com.ironvellum.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.data.Repository
import com.ironvellum.app.domain.RepMax
import com.ironvellum.app.domain.SessionPeaks
import com.ironvellum.app.domain.Sex
import com.ironvellum.app.domain.Titles
import com.ironvellum.app.domain.WorkoutShare
import com.ironvellum.app.domain.Xp
import com.ironvellum.app.ui.components.XpTimeline
import com.ironvellum.app.ui.theme.IronvellumTheme
import com.ironvellum.app.ui.train.CelebrationFlow
import com.ironvellum.app.ui.train.SessionViewModel
import com.ironvellum.app.ui.train.TrialCelebration
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The full-screen XP page and where it sits in the celebration: it comes first and the
 * summary last, Skip goes straight to the summary, a level-up is told once (on the XP page
 * and nowhere after it), and with animations off the page opens finished. The flow is the
 * real one: a stage that advances through [CelebrationFlow], as the trial screen drives it.
 */
@RunWith(AndroidJUnit4::class)
class XpPageTest {

    @get:Rule
    val compose = createComposeRule()

    private val deed = Titles.ALL.first()

    private fun lift(name: String, xp: Int) = Xp.Line(Xp.Kind.EXERCISE, name, xp, hardSets = 3, topLoadKg = 7.5)

    // Level 13 -> 14: Pull-up, Chin-up and Lateral Raise fill level 13 and the Lateral Raise crosses it.
    private val breakdown = Xp.Breakdown(
        listOf(
            lift("Pull-up", 86),
            lift("Chin-up", 38),
            lift("Lateral Raise", 37),
            Xp.Line(Xp.Kind.COMPLETION, Xp.Kind.COMPLETION.label, 25),
        ),
    )

    private val result = Repository.CompletionResult(
        xpAwarded = 186,
        levelBefore = 13,
        levelAfter = 14,
        classBefore = "Adept",
        classAfter = "Adept",
        newTitles = listOf(deed),
        totalXp = 9_140,
        strengthScore = 812,
        questBonus = false,
        durationMinutes = 52,
        inscriptionsBanked = 1,
        breakdown = breakdown,
    )

    private val totals = WorkoutShare.Totals(sets = 18, reps = 142, heldSeconds = 90, movedKg = 6_140)

    private val peaks = listOf(
        SessionPeaks.Peak("Pull-up", 2, 0, 6, 17.5, false, RepMax.Kind.HEAVIER, RepMax.Mark(6, 15.0), 15),
    )

    private val visited = mutableListOf<SessionViewModel.Finish>()
    private var done = 0

    @Composable
    private fun Flow(result: Repository.CompletionResult, motion: Boolean) {
        var stage by remember { mutableStateOf(SessionViewModel.Finish.XP) }
        val hasXp = CelebrationFlow.hasXpPage(result)
        val hasLevel = CelebrationFlow.hasLevelUp(result)
        val hasDeeds = CelebrationFlow.deedsOf(result).isNotEmpty()
        val shown = CelebrationFlow.resolve(stage, hasXp, hasLevel, hasDeeds)
        if (visited.lastOrNull() != shown) visited += shown
        TrialCelebration(
            stage = shown,
            result = result,
            title = "Pull",
            peaks = peaks,
            totals = totals,
            sex = Sex.MALE,
            wornTitleId = null,
            reopenUntilMs = null,
            onContinue = {
                if (shown == SessionViewModel.Finish.SUMMARY) done++
                stage = CelebrationFlow.after(shown, hasXp, hasLevel, hasDeeds)
            },
            onSkipToSummary = { stage = SessionViewModel.Finish.SUMMARY },
            onWear = {},
            onShare = {},
            onReopen = {},
            motion = motion,
        )
    }

    private fun show(result: Repository.CompletionResult = this.result, motion: Boolean = true) {
        compose.mainClock.autoAdvance = false
        compose.setContent { IronvellumTheme { Flow(result, motion) } }
    }

    private fun count(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().size

    private fun countDescription(text: String) = compose.onAllNodesWithContentDescription(text).fetchSemanticsNodes().size

    /** What a screen reader hears at the level moment: the level-up and what it banked. */
    private val levelUpSaid = "Level up, 13 to 14. An inscription waits in the Veil"

    private val timeline get() = XpTimeline(breakdown, result.totalXp)

    @Test
    fun theXpPageComesFirstThenTheDeedsThenTheSummary() {
        show()
        compose.mainClock.advanceTimeBy(timeline.endMs + 200)
        assertEquals(listOf(SessionViewModel.Finish.XP), visited)
        compose.onNodeWithText("CONTINUE").performClick()
        compose.mainClock.advanceTimeBy(2_000)
        compose.onNodeWithText("Deed earned").assertExists()
        compose.onNodeWithText("Continue").performClick()
        compose.mainClock.advanceTimeBy(3_000)
        compose.onNodeWithText("Share trial").assertExists()
        assertEquals(
            listOf(SessionViewModel.Finish.XP, SessionViewModel.Finish.DEEDS, SessionViewModel.Finish.SUMMARY),
            visited,
        )
        compose.onNodeWithText("Done").performClick()
        assertEquals(1, done)
    }

    @Test
    fun theSummaryAfterTheXpPageShowsTheFinishedPanelWithoutReplayingIt() {
        show()
        compose.mainClock.advanceTimeBy(timeline.endMs + 200)
        compose.onNodeWithText("CONTINUE").performClick()
        compose.mainClock.advanceTimeBy(2_000)
        compose.onNodeWithText("Continue").performClick()
        // A beat in, not the 5 seconds the old reveal took: the panel is already final.
        compose.mainClock.advanceTimeBy(1_300)
        compose.onNodeWithText("+186 XP", useUnmergedTree = true).assertExists()
        // The peak card names it too, so the row is one of two.
        assert(count("Pull-up") >= 1)
        compose.onNodeWithText("Trial complete", useUnmergedTree = true).assertExists()
    }

    @Test
    fun skipGoesStraightToTheSummaryFromAnyMoment() {
        show()
        compose.mainClock.advanceTimeBy(700)
        compose.onNodeWithContentDescription("Skip to summary").performClick()
        compose.mainClock.advanceTimeBy(1_500)
        assertEquals(listOf(SessionViewModel.Finish.XP, SessionViewModel.Finish.SUMMARY), visited)
        compose.onNodeWithText("Share trial").assertExists()
        // The deed is not lost: it is a row on the summary.
        compose.onNodeWithText(deed.name).assertExists()
    }

    @Test
    fun skipIsThereDuringTheLevelMomentToo() {
        show()
        val hold = timeline.holds.single()
        compose.mainClock.advanceTimeBy(hold.startMs + 300)
        compose.onNodeWithContentDescription(levelUpSaid).assertExists()
        compose.onNodeWithContentDescription("Skip to summary").performClick()
        compose.mainClock.advanceTimeBy(1_500)
        assertEquals(SessionViewModel.Finish.SUMMARY, visited.last())
    }

    @Test
    fun theLevelUpIsToldOnceAndNeverAfterThePage() {
        show()
        // Filling: no level moment yet, no Continue, Skip is there.
        compose.mainClock.advanceTimeBy(600)
        assertEquals(0, countDescription(levelUpSaid))
        assertEquals(0, count("CONTINUE"))
        assertEquals(1, countDescription("Skip to summary"))
        // The crossing takes the page for a second and a half.
        val hold = timeline.holds.single()
        compose.mainClock.advanceTimeBy(hold.startMs + 300 - 600)
        assertEquals(1, countDescription(levelUpSaid))
        compose.onNodeWithText("An inscription waits in the Veil", useUnmergedTree = true).assertExists()
        // Held, then gone: the rest fills on a fresh bar.
        compose.mainClock.advanceTimeBy(XpTimeline.HOLD_MS)
        assertEquals(0, countDescription(levelUpSaid))
        compose.mainClock.advanceTimeBy(timeline.endMs)
        assertEquals(0, count("Skip ›"))
        compose.onNodeWithText("CONTINUE").performClick()
        compose.mainClock.advanceTimeBy(2_000)
        compose.onNodeWithText("Continue").performClick()
        compose.mainClock.advanceTimeBy(3_000)
        // On the summary: no second level-up moment, no arrow, no level page.
        assertEquals(0, countDescription(levelUpSaid))
        assertEquals(0, count("13 → 14"))
        assertEquals(0, count("Level up"))
        assertEquals(0, count("LEVEL UP"))
        assertEquals(0, count("LEVEL UP · 14"))
        assertEquals(false, visited.contains(SessionViewModel.Finish.LEVEL))
    }

    @Test
    fun animationsOffOpenTheFinishedPageAtOnce() {
        show(motion = false)
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithText("CONTINUE").assertExists()
        assertEquals(0, countDescription("Skip to summary"))
        // The level-up is said in text and to a screen reader, since the moment never plays.
        compose.onNodeWithText("LEVEL UP · 14").assertExists()
        compose.onNodeWithContentDescription("Earned 186 XP, 161 effort and 25 bonus. Level up, 13 to 14.").assertExists()
        compose.onNodeWithText("EFFORT").assertExists()
        compose.onNodeWithText("BONUSES").assertExists()
        assertEquals(listOf(SessionViewModel.Finish.XP), visited)
    }

    @Test
    fun aTrialThatEarnedNoXpHasNoXpPage() {
        show(result = result.copy(breakdown = Xp.Breakdown.EMPTY, xpAwarded = 0, levelBefore = 14, newTitles = emptyList()))
        compose.mainClock.advanceTimeBy(2_000)
        assertEquals(listOf(SessionViewModel.Finish.SUMMARY), visited)
    }

    @Test
    fun skipAndContinueAreAtLeast48dpTall() {
        show()
        compose.mainClock.advanceTimeBy(600)
        val skip = compose.onNodeWithContentDescription("Skip to summary").fetchSemanticsNode().size
        val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        assert(skip.height / density >= 48f) { "Skip is ${skip.height / density}dp tall" }
        compose.mainClock.advanceTimeBy(timeline.endMs + 500)
        val go = compose.onNodeWithText("CONTINUE").fetchSemanticsNode().size
        assert(go.height / density >= 48f) { "Continue is ${go.height / density}dp tall" }
    }
}
