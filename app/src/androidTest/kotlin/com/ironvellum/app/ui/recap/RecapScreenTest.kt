package com.ironvellum.app.ui.recap

import android.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.IronvellumApp
import com.ironvellum.app.data.Words
import com.ironvellum.app.domain.PresetEntry
import com.ironvellum.app.domain.WorkoutPreset
import com.ironvellum.app.ui.theme.IronvellumTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * The recap screen in each of its three weeks, in both vocabularies, and the share card it hands on: what is on the page,
 * what is left off a quiet week, which button an empty week offers, and that the card is the size and theme it promises.
 */
@RunWith(AndroidJUnit4::class)
class RecapScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as IronvellumApp
    private val sunday = RecapSample.monday.plusDays(6)

    @After
    fun themedAgain() {
        Words.setThemed(app, true)
    }

    private fun show(
        recap: com.ironvellum.app.domain.WeeklyRecap,
        rite: WorkoutPreset? = null,
        onShare: () -> Unit = {},
        onBegin: (Long) -> Unit = {},
        light: Boolean = false,
    ) {
        compose.setContent {
            IronvellumTheme(light = light) {
                Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                    RecapContent(recap, rite, today = sunday, onBack = {}, onShare = onShare, onBegin = onBegin)
                }
            }
        }
        compose.waitForIdle()
    }

    private fun mode(themed: Boolean) {
        compose.runOnUiThread { Words.setThemed(app, themed) }
    }

    @Test
    fun anOrdinaryWeekShowsTotalsRecordsMusclesStreakAndNext() {
        mode(themed = false)
        show(RecapSample.full())
        compose.onNodeWithText("Weekly recap").assertIsDisplayed()
        compose.onNodeWithText("Mon 12 – Sun 18 Oct 2026").assertIsDisplayed()
        compose.onNodeWithText("workouts").assertIsDisplayed()
        compose.onNodeWithText("vs 3 last week (5–11 Oct)").assertIsDisplayed()
        compose.onNodeWithText("62").assertIsDisplayed()
        compose.onNodeWithText("18,420").assertIsDisplayed()
        compose.onNodeWithText("4 h 52").assertIsDisplayed()
        compose.onNodeWithText("+11 vs last week").assertIsDisplayed()
        compose.onNodeWithText("+15%").assertIsDisplayed()
        compose.onNodeWithText("+38 min").assertIsDisplayed()
        compose.onNodeWithText("Personal records").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("3 NEW").assertExists()
        compose.onNodeWithText("Bench Press").assertExists()
        compose.onNodeWithText("82.5 kg × 6 · Mon 12 Oct").assertExists()
        compose.onNodeWithText("e1RM 99.0 kg").assertExists()
        compose.onNodeWithText("+3.0 kg").assertExists()
        compose.onNodeWithText("+12.5 kg × 7 · Tue 13 Oct").assertExists()
        compose.onNodeWithText("Back Squat · 105 kg × 5").assertExists()
        compose.onNodeWithText("Muscles hit").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("7 of 10 on aim").assertExists()
        compose.onNodeWithText("Streak 98 days").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Kept alive. Longest so far.").assertExists()
        compose.onNodeWithText("Train by Sat 24 Oct to keep it going.").assertExists()
        compose.onNodeWithText("4 workouts planned, starting with Push A on Monday.").assertExists()
        compose.onNodeWithText("Abs and calves fell short. Thursday's Legs day is where to add them.").assertExists()
    }

    @Test
    fun musclesAreListedMostSetsFirstAndTheOnesOnAimFoldAway() {
        mode(themed = false)
        show(RecapSample.full())
        // Off the aim: hamstrings (7), calves (3), abs (0), in that order.
        val tops = listOf("Hamstrings", "Calves", "Abs").map { compose.onNodeWithText(it).fetchSemanticsNode().positionInRoot.y }
        assertEquals(tops.sorted(), tops)
        compose.onNodeWithText("Not trained · aim 4–6").assertExists()
        compose.onNodeWithText("Below minimum · aim 6–8").assertExists()
        compose.onNodeWithText("Under plan · aim 8–12").assertExists()
        compose.onNodeWithText("Lats").assertDoesNotExist()
        compose.onNodeWithText("7 on aim").performScrollTo().performClick()
        compose.onNodeWithText("Lats").assertExists()
    }

    @Test
    fun theSameWeekReadsInTheThemedWords() {
        mode(themed = true)
        show(RecapSample.full())
        compose.onNodeWithText("trials").assertIsDisplayed()
        compose.onNodeWithText("Oath 98 days").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("4 trials planned, starting with Push A on Monday.").assertExists()
        compose.onNodeWithText("workouts").assertDoesNotExist()
    }

    @Test
    fun aLightWeekHasNoComparisonBarsAndSaysWhatWasDone() {
        mode(themed = false)
        show(RecapSample.light())
        compose.onNodeWithText("workout").assertIsDisplayed()
        compose.onNodeWithText("This week").assertDoesNotExist()
        compose.onNodeWithText("Last week").assertDoesNotExist()
        compose.onNodeWithText("A light week still counts.").assertIsDisplayed()
        compose.onNodeWithText("Done this week", ignoreCase = true).assertExists()
        compose.onNodeWithText("5 × 5 at 100 kg").assertExists()
        compose.onNodeWithText("3 × 15").assertExists()
        compose.onNodeWithText("Personal records").assertDoesNotExist()
    }

    @Test
    fun anEmptyWeekOffersToBeginTodaysWorkoutInsteadOfSharing() {
        mode(themed = false)
        var begun = -1L
        val rite = WorkoutPreset(id = 7, name = "Pull A", entries = listOf(PresetEntry(exerciseId = 1, exerciseName = "Row", targetSets = 3, targetReps = 8)), scheduledDay = 7)
        show(RecapSample.empty(), rite, onBegin = { begun = it })
        compose.onNodeWithText("0").assertIsDisplayed()
        compose.onNodeWithText("Nothing logged from Mon 12 Oct to Sun 18 Oct. Last week you did 3.").assertIsDisplayed()
        compose.onNodeWithText("Streak at risk").assertIsDisplayed()
        compose.onNodeWithText("Your last workout was Sun 11 Oct. Train today to keep your 91-day streak.").assertExists()
        compose.onNodeWithContentDescription("Share the recap").assertDoesNotExist()
        compose.onNodeWithText("Share recap").assertDoesNotExist()
        compose.onNodeWithText("Begin Pull A").performClick()
        assertEquals(7L, begun)
    }

    @Test
    fun anEmptyWeekWithNothingPlannedForTodayHasNoButton() {
        mode(themed = false)
        show(RecapSample.empty(), rite = null)
        compose.onNodeWithText("Begin Pull A").assertDoesNotExist()
        compose.onNodeWithText("Share recap").assertDoesNotExist()
    }

    @Test
    fun theShareButtonAndTheHeaderIconBothOpenTheShareScreen() {
        mode(themed = false)
        var shares = 0
        show(RecapSample.full(), onShare = { shares++ })
        compose.onNodeWithContentDescription("Share the recap").performClick()
        compose.onNodeWithText("Share recap").performClick()
        assertEquals(2, shares)
    }

    @Test
    fun theLightThemeComposesTheSameScreen() {
        mode(themed = false)
        show(RecapSample.full(), light = true)
        compose.onNodeWithText("Weekly recap").assertIsDisplayed()
        compose.onNodeWithText("workouts").assertIsDisplayed()
    }

    @Test
    fun theShareCardIs1080By1350AndFollowsTheTheme() {
        mode(themed = false)
        val dark = RecapCardPalette(0xFF0C0C0B.toInt(), 0xFFE8E8E4.toInt(), 0xFFA3A099.toInt(), 0xFF32302B.toInt(), 0xFF34D399.toInt(), 0xFF34D399.toInt(), 0xFF0C0C0B.toInt(), 0xFFF2C14E.toInt())
        val light = RecapCardPalette(0xFFF2E8D8.toInt(), 0xFF241B15.toInt(), 0xFF65584C.toInt(), 0xFFD4C9B8.toInt(), 0xFF208046.toInt(), 0xFF208046.toInt(), 0xFFFEFBF5.toInt(), 0xFF9B5900.toInt())
        val onDark = RecapCard.render(context, RecapSample.full(), palette = dark)
        val onLight = RecapCard.render(context, RecapSample.full(), palette = light)
        assertEquals(1080, onDark.width)
        assertEquals(1350, onDark.height)
        // Inside the frame, a corner is the page colour of the theme in force.
        assertEquals(dark.page, onDark.getPixel(40, 40))
        assertEquals(light.page, onLight.getPixel(40, 40))
        // The frame itself is the rune line.
        assertEquals(dark.rune, onDark.getPixel(1, 600))
        // Something is drawn: the card is not a flat page.
        val inked = (0 until 1350 step 6).count { y -> (0 until 1080 step 6).any { x -> onDark.getPixel(x, y) != dark.page && onDark.getPixel(x, y) != dark.rune } }
        assertTrue("the card is blank", inked > 30)
        // PNG magic bytes.
        val png = RecapCard.png(onDark)
        assertEquals(listOf(0x89, 0x50, 0x4E, 0x47), png.take(4).map { it.toInt() and 0xFF })
    }

    @Test
    fun theCardLeavesOutWhatTheLifterSwitchedOff() {
        mode(themed = false)
        val palette = RecapCardPalette(Color.BLACK, Color.WHITE, Color.GRAY, Color.DKGRAY, Color.GREEN, Color.GREEN, Color.BLACK, Color.YELLOW)
        val all = RecapCard.render(context, RecapSample.full(), palette = palette)
        val bare = RecapCard.render(context, RecapSample.full(), RecapCardOptions(records = false, streak = false), palette)
        fun yellowAndGreenRows(bitmap: android.graphics.Bitmap) =
            (0 until 1350).count { y -> (0 until 1080 step 2).any { x -> bitmap.getPixel(x, y) == Color.YELLOW } }
        assertTrue("trophies drawn with the records switched on", yellowAndGreenRows(all) > 0)
        assertEquals("no trophy with the records switched off", 0, yellowAndGreenRows(bare))
    }

    @Test
    fun theShareScreenShowsTheCardTheTwoSwitchesAndTheLockedRows() {
        mode(themed = false)
        compose.setContent {
            IronvellumTheme {
                Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) { RecapShareContent(RecapSample.full(), onBack = {}) }
            }
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescriptionCount("Preview of the share card") > 0 }
        compose.onNodeWithText("Personal records").assertIsDisplayed()
        compose.onNodeWithText("Streak").assertIsDisplayed()
        compose.onNodeWithText("Body weight and measurements").assertExists()
        compose.onNodeWithText("Progress photos").assertExists()
        compose.onNodeWithText("Save image").assertIsDisplayed()
        compose.onNodeWithText("Share").assertIsDisplayed()
    }

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesWithContentDescriptionCount(description: String): Int =
        onAllNodes(androidx.compose.ui.test.hasContentDescription(description)).fetchSemanticsNodes().size
}
