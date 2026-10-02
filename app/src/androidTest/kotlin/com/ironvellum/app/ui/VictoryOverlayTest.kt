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
import com.ironvellum.app.domain.WorkoutShare
import com.ironvellum.app.ui.theme.IronvellumTheme
import com.ironvellum.app.ui.train.VictoryOverlay
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The trial report: peaks lead when there are any and vanish when there are
 * none, and Share and Continue are two separate buttons that each do one thing.
 * Each case also saves a screenshot (files/victory-*.png) for a visual check.
 */
@RunWith(AndroidJUnit4::class)
class VictoryOverlayTest {

    @get:Rule
    val compose = createComposeRule()

    // Level 13 -> 14: the bar runs out and refills.
    private val result = Repository.CompletionResult(
        xpAwarded = 186,
        levelBefore = 13,
        levelAfter = 14,
        classBefore = "Adept",
        classAfter = "Adept",
        newTitles = emptyList(),
        totalXp = 9_140,
        strengthScore = 812,
        questBonus = true,
        durationMinutes = 52,
    )

    private val totals = WorkoutShare.Totals(sets = 18, reps = 142, heldSeconds = 90, movedKg = 6_140)

    private fun record(name: String, reps: Int, kg: Double?) =
        SetRecords.Record(name, 0, 100.0, reps, kg, achievedAtMs = 0, sessionId = 1)

    private val peaks = listOf(
        SessionPeaks.Peak("Bench Press", 2, 0, 6, 82.5, false, record("Bench Press", 6, 80.0), 4.2),
        SessionPeaks.Peak("Weighted Dip", 1, 1, 9, 20.0, false, record("Weighted Dip", 8, 20.0), 1.8),
        SessionPeaks.Peak("Plank", 1, 0, 45, null, true, record("Plank", 30, null), 0.9),
    )

    private var shared = 0
    private var continued = 0

    private fun show(peaks: List<SessionPeaks.Peak>) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            IronvellumTheme {
                VictoryOverlay(
                    result = result,
                    title = "Push Day",
                    peaks = peaks,
                    totals = totals,
                    onShare = { shared++ },
                    onContinue = { continued++ },
                )
            }
        }
        // Past every beat: landing, the peak ticks and the XP fill.
        compose.mainClock.advanceTimeBy(6_000)
    }

    @Test
    fun peaksLeadTheReport() {
        show(peaks)
        compose.saveDialogScreenshot("victory-peaks")
        compose.onNodeWithText("NEW PEAKS · 3").assertExists()
        // A peak row reads as one sentence to TalkBack.
        compose.onNodeWithContentDescription(
            "New peak, Bench Press, set 1, 6×82.5kg, was 6×80kg, up +4.2, 2 peaks",
        ).assertExists()
        compose.onNodeWithContentDescription("New peak, Plank, set 1, 45s, was 30s", substring = true).assertExists()
        compose.onNodeWithText("+186 XP", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("LEVEL 14", useUnmergedTree = true).assertExists()
    }

    @Test
    fun noPeaksMeansNoPeakSection() {
        show(emptyList())
        compose.saveDialogScreenshot("victory-no-peaks")
        assertEquals(0, compose.onAllNodesWithText("NEW PEAK", substring = true).fetchSemanticsNodes().size)
        compose.onNodeWithText("SEALED").assertExists()
    }

    @Test
    fun shareAndContinueAreSeparateActions() {
        show(peaks)
        compose.onNodeWithText("SHARE TRIAL").performClick()
        assertEquals(1, shared)
        assertEquals(0, continued)
        compose.onNodeWithText("CONTINUE").performClick()
        assertEquals(1, shared)
        assertEquals(1, continued)
    }
}
