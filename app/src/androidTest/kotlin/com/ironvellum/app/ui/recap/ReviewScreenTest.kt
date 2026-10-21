package com.ironvellum.app.ui.recap

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.IronvellumApp
import com.ironvellum.app.data.Words
import com.ironvellum.app.domain.PeriodReview
import com.ironvellum.app.domain.Muscle
import com.ironvellum.app.ui.theme.IronvellumTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * The month and year in review: what each page holds, what a lifter who started part way through is told instead of a
 * comparison, both vocabularies, the weeks that open their recap, the Today card's two buttons, and the share card's size.
 */
@RunWith(AndroidJUnit4::class)
class ReviewScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as IronvellumApp

    @After
    fun themedAgain() {
        Words.setThemed(app, true)
    }

    private fun mode(themed: Boolean) {
        compose.runOnUiThread { Words.setThemed(app, themed) }
    }

    private fun show(review: PeriodReview, onShare: () -> Unit = {}, onOpenWeek: (Long) -> Unit = {}, light: Boolean = false) {
        compose.setContent {
            IronvellumTheme(light = light) {
                Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                    ReviewContent(review, onBack = {}, onShare = onShare, onOpenWeek = onOpenWeek)
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun aMonthShowsTotalsBestWeekLiftsMusclesVolumeDaysAndSkills() {
        mode(themed = false)
        show(ReviewSample.month())
        compose.onNodeWithText("Month in review").assertIsDisplayed()
        compose.onNodeWithText("September 2026").assertIsDisplayed()
        compose.onNodeWithText("workouts").assertIsDisplayed()
        compose.onNodeWithText("74.9").assertIsDisplayed()
        compose.onNodeWithText("233").assertIsDisplayed()
        compose.onNodeWithText("18 h 24").assertIsDisplayed()
        compose.onNodeWithText("+18% vs August").assertIsDisplayed()
        compose.onNodeWithText("14–20 Sep · 4 workouts · 17,980 kg").assertIsDisplayed()
        compose.onNodeWithText("Lifts gained").assertIsDisplayed()
        compose.onNodeWithText("Back Squat").assertExists()
        compose.onNodeWithText("e1RM 112.5 → 120.0 kg").assertExists()
        compose.onNodeWithText("+7.5 kg").assertExists()
        compose.onNodeWithText("Most trained muscles").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(Muscle.LATS.label).assertExists()
        compose.onNodeWithText("Total volume", ignoreCase = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("74,860").assertExists()
        compose.onNodeWithText("63,200 kg").assertExists()
        compose.onNodeWithText("Workouts and streak").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("3.7 a week").assertExists()
        compose.onNodeWithText("Streak 80 days at month end").assertExists()
        compose.onNodeWithText("Still going. Longest so far.").assertExists()
        compose.onNodeWithText("Skills unlocked").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Parallel Bar Dip").assertExists()
        compose.onNodeWithText("+360 XP").assertExists()
        compose.onNodeWithText("Share September").assertExists()
    }

    @Test
    fun aLiftFirstTrainedInTheMonthIsNewAndHasNoGain() {
        mode(themed = false)
        show(ReviewSample.month())
        compose.onNodeWithText("Front Squat").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("e1RM 84.0 kg").assertExists()
        compose.onNodeWithText("New").assertExists()
    }

    @Test
    fun theSameMonthReadsInTheThemedWords() {
        mode(themed = true)
        show(ReviewSample.month())
        compose.onNodeWithText("trials").assertIsDisplayed()
        compose.onNodeWithText("14–20 Sep · 4 trials · 17,980 kg").assertIsDisplayed()
        compose.onNodeWithText("Trials and oath").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Oath 80 days at month end").assertExists()
        compose.onNodeWithText("workouts").assertDoesNotExist()
    }

    @Test
    fun aYearShowsItsTwelveMonthsAndTheLongestStreakHeadline() {
        mode(themed = false)
        show(ReviewSample.fullYear())
        compose.onNodeWithText("Year in review").assertIsDisplayed()
        compose.onNodeWithText("94").assertIsDisplayed()
        compose.onNodeWithText("+18% vs 2025").assertIsDisplayed()
        compose.onNodeWithText("Workouts and streak").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Streak 98 days at year end").assertExists()
        compose.onNodeWithText("Still going.").assertExists()
        compose.onNodeWithText("The weeks behind it").assertDoesNotExist()
    }

    @Test
    fun aLifterWhoStartedInMarchIsToldSoAndNothingIsComparedWithLastYear() {
        mode(themed = false)
        show(ReviewSample.partialYear())
        compose.onNodeWithText("2026, since you started in March").assertIsDisplayed()
        compose.onNodeWithText("Since you started in March").assertIsDisplayed()
        compose.onAllNodesText("vs 2025").let { assertEquals(0, it) }
    }

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesText(fragment: String): Int =
        onAllNodes(androidx.compose.ui.test.hasText(fragment, substring = true)).fetchSemanticsNodes().size

    @Test
    fun theBestWeekAndEachWeekBehindTheMonthOpenTheirRecap() {
        mode(themed = false)
        val opened = mutableListOf<Long>()
        show(ReviewSample.month(), onOpenWeek = { opened += it })
        compose.onNodeWithText("14–20 Sep · 4 workouts · 17,980 kg").performClick()
        assertEquals(LocalDate.of(2026, 9, 14).toEpochDay(), opened.single())
        compose.onNodeWithText("The weeks behind it").performScrollTo().performClick()
        compose.onNodeWithText("7–13 Sep · 4 workouts").performScrollTo().performClick()
        assertEquals(LocalDate.of(2026, 9, 7).toEpochDay(), opened.last())
    }

    @Test
    fun theShareButtonAndTheHeaderIconBothOpenTheShareScreen() {
        mode(themed = false)
        var shares = 0
        show(ReviewSample.month(), onShare = { shares++ })
        compose.onNodeWithContentDescription("Share the review").performClick()
        compose.onNodeWithText("Share September").performClick()
        assertEquals(2, shares)
    }

    @Test
    fun theLightThemeComposesTheSameScreen() {
        mode(themed = false)
        show(ReviewSample.month(), light = true)
        compose.onNodeWithText("Month in review").assertIsDisplayed()
        compose.onNodeWithText("workouts").assertIsDisplayed()
    }

    @Test
    fun theTodayCardNamesThePeriodAndHasAnOpenAndACloseButton() {
        mode(themed = false)
        var opened = 0
        var closed = 0
        compose.setContent {
            IronvellumTheme {
                Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                    ReviewTodayCard(ReviewSample.yearSummary(), onOpen = { opened++ }, onDismiss = { closed++ })
                }
            }
        }
        compose.onNodeWithText("2026: 94 workouts · 423.0 t").assertIsDisplayed()
        compose.onNodeWithText("New · year in review", ignoreCase = true).assertIsDisplayed()
        compose.onNodeWithText("2026: 94 workouts · 423.0 t").performClick()
        compose.onNodeWithContentDescription("Dismiss the review").performClick()
        assertEquals(1, opened)
        assertEquals(1, closed)
    }

    @Test
    fun theShareCardIs1080By1350ForAMonthAndAYearAndLeavesOutWhatIsSwitchedOff() {
        mode(themed = false)
        val palette = RecapCardPalette(0xFF0C0C0B.toInt(), 0xFFE8E8E4.toInt(), 0xFFA3A099.toInt(), 0xFF32302B.toInt(), 0xFF34D399.toInt(), 0xFF34D399.toInt(), 0xFF0C0C0B.toInt(), 0xFFF2C14E.toInt())
        for (review in listOf(ReviewSample.month(), ReviewSample.fullYear(), ReviewSample.partialYear())) {
            val card = ReviewCard.render(context, review, palette = palette)
            assertEquals(1080, card.width)
            assertEquals(1350, card.height)
            assertEquals(palette.page, card.getPixel(40, 40))
            val inked = (0 until 1350 step 6).count { y -> (0 until 1080 step 6).any { x -> card.getPixel(x, y) != palette.page && card.getPixel(x, y) != palette.rune } }
            assertTrue("the card is blank", inked > 30)
        }
        // The lifts and the streak are optional: switched off, the rows between the volume and the footer are empty.
        val all = ReviewCard.render(context, ReviewSample.month(), palette = palette)
        val bare = ReviewCard.render(context, ReviewSample.month(), RecapCardOptions(records = false, streak = false), palette)
        fun inkIn(card: android.graphics.Bitmap, from: Int, to: Int) =
            (from until to).count { y -> (40 until 1040 step 2).any { x -> card.getPixel(x, y) != palette.page } }
        assertTrue(inkIn(all, 800, 1130) > 100)
        assertEquals(0, inkIn(bare, 800, 1130))
    }

    @Test
    fun theShareScreenShowsTheReviewCardAndItsTwoSwitches() {
        mode(themed = false)
        compose.setContent {
            IronvellumTheme {
                Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) { ReviewShareContent(ReviewSample.month(), onBack = {}) }
            }
        }
        compose.waitUntil(5_000) { compose.onAllNodes(hasContentDescription("Preview of the share card")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Share review").assertIsDisplayed()
        compose.onNodeWithText("Lifts gained").assertIsDisplayed()
        compose.onNodeWithText("Streak").assertIsDisplayed()
        compose.onNodeWithText("Progress photos").assertExists()
    }
}
