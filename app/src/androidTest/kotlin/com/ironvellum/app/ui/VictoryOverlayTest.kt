package com.ironvellum.app.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ironvellum.app.data.Repository
import com.ironvellum.app.domain.SessionPeaks
import com.ironvellum.app.domain.SetRecords
import com.ironvellum.app.domain.Sex
import com.ironvellum.app.domain.Titles
import com.ironvellum.app.domain.WorkoutShare
import com.ironvellum.app.ui.theme.IronvellumTheme
import com.ironvellum.app.ui.train.SessionViewModel
import com.ironvellum.app.ui.train.TrialCelebration
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The celebration after a seal: a level-up page and a deeds page when the trial
 * earned them, then the summary. Every page ends in the same dock (one primary,
 * one quiet link) and only those two are taps. Each case also saves a
 * screenshot (files/victory-*.png) for a visual check.
 */
@RunWith(AndroidJUnit4::class)
class VictoryOverlayTest {

    @get:Rule
    val compose = createComposeRule()

    private val deed = Titles.ALL.first()

    // Level 13 -> 14: the bar runs out and refills.
    private val result = Repository.CompletionResult(
        xpAwarded = 186,
        levelBefore = 13,
        levelAfter = 14,
        classBefore = "Adept",
        classAfter = "Adept",
        newTitles = listOf(deed),
        totalXp = 9_140,
        strengthScore = 812,
        questBonus = true,
        durationMinutes = 52,
    )

    private val quiet = result.copy(levelBefore = 14, newTitles = emptyList())

    private val totals = WorkoutShare.Totals(sets = 18, reps = 142, heldSeconds = 90, movedKg = 6_140)

    private fun record(name: String, reps: Int, kg: Double?) =
        SetRecords.Record(name, 0, 100.0, reps, kg, achievedAtMs = 0, sessionId = 1)

    private val peaks = listOf(
        SessionPeaks.Peak("Bench Press", 2, 0, 6, 82.5, false, record("Bench Press", 6, 80.0), 4.2),
        SessionPeaks.Peak("Weighted Dip", 1, 1, 9, 20.0, false, record("Weighted Dip", 8, 20.0), 1.8),
        SessionPeaks.Peak("Plank", 1, 0, 45, null, true, record("Plank", 30, null), 0.9),
    )

    private var continued = 0
    private var skipped = 0
    private var shared = 0
    private var reopened = 0
    private var worn: String? = null

    private fun show(
        stage: SessionViewModel.Finish,
        result: Repository.CompletionResult = this.result,
        peaks: List<SessionPeaks.Peak> = this.peaks,
        wornTitleId: String? = null,
        reopenUntilMs: Long? = null,
    ) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            IronvellumTheme {
                TrialCelebration(
                    stage = stage,
                    result = result,
                    title = "Push Day",
                    peaks = peaks,
                    totals = totals,
                    sex = Sex.MALE,
                    wornTitleId = wornTitleId,
                    reopenUntilMs = reopenUntilMs,
                    onContinue = { continued++ },
                    onSkipToSummary = { skipped++ },
                    onWear = { worn = it },
                    onShare = { shared++ },
                    onReopen = { reopened++ },
                )
            }
        }
        // Past every beat: the ring, the bar, the roll and the reveals.
        compose.mainClock.advanceTimeBy(6_000)
    }

    private fun count(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().size

    @Test
    fun levelPageCountsItsStepsAndOffersSkipBecauseASummaryFollows() {
        show(SessionViewModel.Finish.LEVEL)
        compose.saveDialogScreenshot("victory-level")
        compose.onNodeWithText("1 of 2", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("+186 XP").assertExists()
        compose.onNodeWithContentDescription("Level 14").assertExists()
        compose.onNodeWithText("Skip to summary").performClick()
        assertEquals(1, skipped)
        compose.onNodeWithText("Continue").performClick()
        assertEquals(1, continued)
    }

    @Test
    fun levelPageAloneHasNoSkipAndNoCount() {
        show(SessionViewModel.Finish.LEVEL, result = result.copy(newTitles = emptyList()))
        assertEquals(0, count("Skip to summary"))
        assertEquals(0, count("1 of 1"))
        compose.onNodeWithText("Continue").assertExists()
    }

    @Test
    fun deedsPageWearsTheTitleThroughTheDock() {
        show(SessionViewModel.Finish.DEEDS)
        compose.saveDialogScreenshot("victory-deed")
        compose.onNodeWithText("2 of 2", useUnmergedTree = true).assertExists()
        compose.onNodeWithText(deed.name).assertExists()
        assertEquals(0, count("Skip to summary"))
        compose.onNodeWithText("Wear title").performClick()
        assertEquals(deed.id, worn)
        compose.onNodeWithText("Wearing ✓").assertExists()
        compose.onNodeWithText("Continue").performClick()
        assertEquals(1, continued)
    }

    @Test
    fun aDeedAlreadyWornReadsWearingFromTheStart() {
        show(SessionViewModel.Finish.DEEDS, wornTitleId = deed.id)
        compose.onNodeWithText("Wearing ✓").assertExists()
        assertEquals(0, count("Wear title"))
    }

    @Test
    fun peaksLeadTheSummary() {
        show(SessionViewModel.Finish.SUMMARY, result = quiet)
        compose.saveDialogScreenshot("victory-summary")
        compose.onNodeWithText("New peaks · 3").assertExists()
        // The best peak is the hero; a peak row reads as one sentence to TalkBack.
        compose.onNodeWithContentDescription("New peak, Bench Press, set 1, 6×82.5kg, was 6×80kg, up +2.5 kg, 2 peaks").assertExists()
        compose.onNodeWithContentDescription("New peak, Plank, set 1, 45s, was 30s", substring = true).assertExists()
        compose.onNodeWithText("+186").assertExists()
        compose.onNodeWithText("18 sets · 142 reps · 90 s held · 6,140 kg moved").assertExists()
    }

    @Test
    fun noPeaksMeansNoPeakCard() {
        show(SessionViewModel.Finish.SUMMARY, result = quiet, peaks = emptyList())
        assertEquals(0, count("New peak"))
        compose.onNodeWithText("Push Day").assertExists()
    }

    @Test
    fun doneAndShareAreSeparateActions() {
        show(SessionViewModel.Finish.SUMMARY, result = quiet)
        compose.onNodeWithText("Share trial").performClick()
        assertEquals(1, shared)
        assertEquals(0, continued)
        compose.onNodeWithText("Done").performClick()
        assertEquals(1, shared)
        assertEquals(1, continued)
    }

    @Test
    fun theWayBackIsOfferedOnlyInsideTheWindow() {
        show(SessionViewModel.Finish.SUMMARY, result = quiet, reopenUntilMs = System.currentTimeMillis() + 60_000)
        compose.onNodeWithText("Sealed too soon? Keep going").performClick()
        assertEquals(1, reopened)
    }

    @Test
    fun noWayBackOnceTheWindowHasClosed() {
        show(SessionViewModel.Finish.SUMMARY, result = quiet, reopenUntilMs = System.currentTimeMillis() - 1)
        assertEquals(0, count("Sealed too soon? Keep going"))
    }
}
