package com.ironvellum.app.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.IronvellumApp
import com.ironvellum.app.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/**
 * Seal a trial that differs from its rite, tap Done on the victory screen, and
 * land on the rite-update offer without the app dying.
 *
 * The offer is an [com.ironvellum.app.ui.components.IronvellumDialog], a bottom
 * sheet whose body already scrolls; a second vertical scroll inside it is
 * measured against infinite height and throws "Vertically scrollable component
 * was measured with an infinity maximum height constraints" the moment the offer
 * composes. Nothing else exercises that path: the offer only exists when the
 * trial changed something, which the other flow tests never do.
 */
@RunWith(AndroidJUnit4::class)
class RoutineUpdateOfferFlowTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val driver = FlowDriver(compose)

    @Before
    fun setUp() {
        TestProfile.ensureSetUp()
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as IronvellumApp
        app.database.openHelper.writableDatabase.execSQL("DELETE FROM sessions")
        driver.takeTheClock()
    }

    @Test
    fun sealingAChangedTrialShowsTheRiteOfferAfterDone() {
        selectATrainingDay()
        val plan = driver.awaitAnyText(predicate = ::isQuestCard)
        compose.onAllNodesWithText(plan).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        driver.settle()
        val cta = driver.awaitAnyText { it.startsWith("Begin ", true) || it.startsWith("Continue ", true) }
        driver.click(cta)
        driver.awaitText("Trial in progress")

        compose.onAllNodes(hasContentDescription("Log set", substring = true), useUnmergedTree = true)
            .onFirst().performSemanticsAction(SemanticsActions.OnClick)
        driver.settle()
        // The logged set now beats the rite's rep target, so the seal proposes a rite change.
        // Waits for the tick to land before bumping the figure it wrote.
        var bumped = false
        repeat(60) {
            if (bumped) return@repeat
            val db = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as IronvellumApp)
                .database.openHelper.writableDatabase
            db.query("SELECT COUNT(*) FROM set_logs WHERE done = 1").use { c ->
                if (c.moveToFirst() && c.getInt(0) > 0) {
                    db.execSQL("UPDATE set_logs SET reps = reps + 2 WHERE done = 1")
                    bumped = true
                    return@repeat
                }
            }
            compose.mainClock.advanceTimeBy(1_200L)
            Thread.sleep(100L)
        }

        assertTrue("the logged set never landed in the database", bumped)
        compose.onAllNodesWithContentDescription("Seal the trial").onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)
        driver.awaitText("Seal anyway")
        driver.click("Seal anyway")

        // Level-up and deeds pages when earned, then the summary: advance to its Done.
        repeat(24) {
            if (driver.allText().any { it.equals("Update rite", true) }) return@repeat
            val advance = driver.allText().firstOrNull { it.equals("Continue", true) || it.equals("Done", true) }
            if (advance != null) driver.click(advance) else driver.settle(1)
            Thread.sleep(100L)
        }
        driver.awaitText("Update rite")
        assertTrue("the rite offer must show after Done", driver.allText().any { it.equals("Keep rite", true) })
        driver.click("Keep rite")
    }

    private fun isQuestCard(label: String) = label.contains(" sets · about ", ignoreCase = true)

    private fun selectATrainingDay() {
        driver.awaitAnyText { it.equals("Respite", ignoreCase = true) || isQuestCard(it) }
        if (driver.allText().any(::isQuestCard)) return
        for (day in DayOfWeek.values()) {
            val name = day.getDisplayName(TextStyle.FULL, Locale.getDefault())
            compose.onAllNodes(hasContentDescription(name) and hasClickAction()).onFirst()
                .performSemanticsAction(SemanticsActions.OnClick)
            driver.settle()
            if (driver.allText().any(::isQuestCard)) return
        }
        error("no weekday offered a program; on screen: ${driver.allText()}")
    }
}
