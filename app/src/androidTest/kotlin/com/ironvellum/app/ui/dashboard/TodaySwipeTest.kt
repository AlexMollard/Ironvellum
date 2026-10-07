package com.ironvellum.app.ui.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toPixelMap
import kotlinx.coroutines.runBlocking
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ironvellum.app.ui.theme.IronvellumTheme
import com.ironvellum.app.domain.WorkoutSession
import com.ironvellum.app.domain.WorkoutPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class TodaySwipeTest {
    @get:Rule val compose = createComposeRule()
    private var day by mutableStateOf(1)
    private var selections = 0
    private var forgeOpens = 0
    private lateinit var layer: GraphicsLayer

    private fun show(startDay: Int = 1, height: Int = 760, ui: DashboardUi = DashboardUi(), motion: Boolean = false) {
        day = startDay
        compose.mainClock.autoAdvance = false
        compose.setContent {
            IronvellumTheme {
                layer = rememberGraphicsLayer()
                Box(Modifier.size(411.dp, height.dp).testTag("home").drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                    drawLayer(layer)
                }) {
                    TodayContent(
                        ui = ui, selectedDay = day, today = LocalDate.of(2026, 10, 5),
                        live = null, liveSets = emptyList(), bodyGap = BodyGap.BOTH,
                        strengthRank = "Unranked", rankBreakdown = null, veil = null, motion = motion,
                        actions = TodayActions(
                            onSelectDay = { day = it; selections++ },
                            onOpenForge = { forgeOpens++ },
                        ),
                    )
                }
            }
        }
        advance()
    }

    private fun advance() { compose.mainClock.advanceTimeBy(100); compose.waitForIdle() }
    private fun left() { compose.onNodeWithTag("home").performTouchInput { swipeLeft() }; advance() }
    private fun right() { compose.onNodeWithTag("home").performTouchInput { swipeRight() }; advance() }

    @Test fun todaySelectedAndSealedRemainDistinctFromScheduledAndRestDays() {
        show(ui = DashboardUi(
            presets = listOf(
                WorkoutPreset(id = 1, name = "Monday rite", scheduledDay = 1),
                WorkoutPreset(id = 2, name = "Tuesday rite", scheduledDay = 2),
            ),
            weekDone = mapOf(1 to WorkoutSession(id = 1, presetId = 1, label = "Monday rite", startedAtMs = 0, completedAtMs = 1)),
        ))
        compose.onNodeWithContentDescription("Monday, done").assertIsSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Today, Sealed trial"))
        compose.onNodeWithContentDescription("Tuesday")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Rite in cycle"))
        compose.onNodeWithContentDescription("Wednesday")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "No rite"))
        compose.onNodeWithContentDescription("Tuesday").performTouchInput { click() }
        advance()
        compose.onNodeWithContentDescription("Tuesday").assertIsSelected()
        compose.onNodeWithContentDescription("Monday, done")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Today, Sealed trial"))
    }

    @Test fun todayCanBeScheduledWithoutBeingSelected() {
        show(startDay = 5, ui = DashboardUi(presets = listOf(
            WorkoutPreset(id = 1, name = "Monday rite", scheduledDay = 1),
        )))
        compose.onNodeWithContentDescription("Monday")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Today, Rite in cycle"))
        compose.onNodeWithContentDescription("Friday").assertIsSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "No rite"))
    }

    @Test fun horizontalSwipesChangeOneDayAndUseTheLatestSelection() {
        show(ui = DashboardUi(presets = (1..7).map { WorkoutPreset(id = it.toLong(), name = "Rite $it", scheduledDay = it) }))
        left()
        compose.onNodeWithContentDescription("Tuesday").assertIsSelected()
        compose.onNodeWithText("RITE 2").assertIsDisplayed()
        left()
        compose.onNodeWithContentDescription("Wednesday").assertIsSelected()
        compose.onNodeWithText("RITE 3").assertIsDisplayed()
        right()
        compose.onNodeWithContentDescription("Tuesday").assertIsSelected()
        compose.runOnIdle { assertEquals(3, selections) }
    }

    @Test fun swipesWrapBothEndsOfTheWeek() {
        show(startDay = 7)
        left()
        compose.onNodeWithContentDescription("Monday").assertIsSelected()
        right()
        compose.onNodeWithContentDescription("Sunday").assertIsSelected()
    }

    @Test fun aShortDragOrCancelledSwipeDoesNotChangeTheDay() {
        show()
        compose.onNodeWithTag("home").performTouchInput {
            swipe(Offset(width * .6f, height * .8f), Offset(width * .6f - 30f, height * .8f))
        }
        advance()
        compose.onNodeWithTag("home").performTouchInput {
            down(Offset(width * .8f, height * .8f))
            moveTo(Offset(width * .2f, height * .8f))
            cancel()
        }
        advance()
        compose.runOnIdle { assertEquals(1, day); assertEquals(0, selections) }
    }

    @Test fun aMostlyVerticalDiagonalDoesNotChangeTheDay() {
        show()
        compose.onNodeWithTag("home").performTouchInput {
            swipe(Offset(width * .8f, height * .2f), Offset(width * .5f, height * .85f))
        }
        advance()
        compose.runOnIdle { assertEquals(1, day); assertEquals(0, selections) }
    }

    @Test fun verticalScrollingDoesNotChangeTheDay() {
        show(height = 320)
        val scroll = compose.onNode(hasScrollAction())
        val before = scroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        scroll.performTouchInput { swipeUp() }
        advance()
        val after = scroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertTrue("The vertical gesture must still scroll", after > before)
        compose.runOnIdle { assertEquals(1, day); assertEquals(0, selections) }
    }

    @Test fun swipingOverAButtonChangesTheDayWithoutOpeningIt() {
        show()
        compose.onNodeWithText("Forge a cycle").performTouchInput { swipeLeft() }
        advance()
        compose.runOnIdle { assertEquals(2, day); assertEquals(0, forgeOpens) }
        compose.onNodeWithText("Forge a cycle").performTouchInput { click() }
        advance()
        compose.runOnIdle { assertEquals(1, forgeOpens) }
    }

    @Test fun weekdayTapsStillSelectTheirDay() {
        show()
        compose.onNodeWithContentDescription("Friday").performTouchInput { click() }
        advance()
        compose.onNodeWithContentDescription("Friday").assertIsSelected()
    }
    @Test fun contentFollowsTheFingerAndSettlesAfterChangingTheDay() {
        show(motion = true)
        val button = compose.onNodeWithText("Forge a cycle")
        val before = button.fetchSemanticsNode().boundsInRoot.left
        val home = compose.onNodeWithTag("home")
        home.performTouchInput {
            down(Offset(width * .8f, height * .8f))
            moveTo(Offset(width * .4f, height * .8f))
        }
        compose.mainClock.advanceTimeBy(32)
        compose.waitForIdle()
        assertTrue("Content must follow a leftward drag", button.fetchSemanticsNode().boundsInRoot.left < before - 10f)
        compose.runOnIdle { assertEquals(1, day); assertEquals(0, selections) }
        home.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(2, day); assertEquals(1, selections) }
        assertEquals(before, button.fetchSemanticsNode().boundsInRoot.left, 1f)
    }

    @Test fun aCancelledAnimatedDragSpringsBackWithoutSelecting() {
        show(motion = true)
        val button = compose.onNodeWithText("Forge a cycle")
        val before = button.fetchSemanticsNode().boundsInRoot.left
        val home = compose.onNodeWithTag("home")
        home.performTouchInput {
            down(Offset(width * .8f, height * .8f))
            moveTo(Offset(width * .4f, height * .8f))
        }
        compose.mainClock.advanceTimeBy(32)
        compose.waitForIdle()
        assertTrue(button.fetchSemanticsNode().boundsInRoot.left < before - 10f)
        home.performTouchInput { cancel() }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, day); assertEquals(0, selections) }
        assertEquals(before, button.fetchSemanticsNode().boundsInRoot.left, 1f)
    }

    @Test fun aSwipeStartingOnAButtonDoesNotFlashItsPressHighlight() {
        show()
        val button = compose.onNodeWithText("Forge a cycle")
        val bounds = button.fetchSemanticsNode().boundsInRoot
        val x = bounds.left.toInt() + 10
        val y = bounds.center.y.toInt()
        val before = runBlocking { layer.toImageBitmap() }.toPixelMap()[x, y]
        button.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(32)
        compose.waitForIdle()
        assertEquals("While touch intent is unresolved, a swipe must not flash button ink", before,
            runBlocking { layer.toImageBitmap() }.toPixelMap()[x, y])
        button.performTouchInput { moveTo(Offset(0f, center.y)) }
        advance()
        assertEquals(before, runBlocking { layer.toImageBitmap() }.toPixelMap()[x, y])
        button.performTouchInput { up() }
        advance()
        compose.runOnIdle { assertEquals(2, day); assertEquals(0, forgeOpens) }
    }

    @Test fun aHeldButtonStillHighlightsAndReleasesAsOneTap() {
        show()
        val button = compose.onNodeWithText("Forge a cycle")
        val bounds = button.fetchSemanticsNode().boundsInRoot
        val x = bounds.left.toInt() + 10
        val y = bounds.center.y.toInt()
        fun ink() = runBlocking { layer.toImageBitmap() }.toPixelMap()[x, y]
        val before = ink()
        button.performTouchInput { down(center) }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.mainClock.advanceTimeBy(16)
            compose.waitForIdle()
            ink() != before
        }
        button.performTouchInput { up() }
        advance()
        assertEquals(before, ink())
        compose.runOnIdle { assertEquals(1, forgeOpens); assertEquals(0, selections) }
    }

    @Test fun aNewSwipeCanReverseDirectionWhileThePreviousDayIsSettling() {
        show(motion = true)
        val button = compose.onNodeWithText("Forge a cycle")
        val before = button.fetchSemanticsNode().boundsInRoot.left
        val home = compose.onNodeWithTag("home")
        home.performTouchInput { swipeLeft() }
        compose.mainClock.advanceTimeBy(32)
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(2, day) }
        assertTrue("The incoming day must still be sliding in", button.fetchSemanticsNode().boundsInRoot.left > before + 1f)
        home.performTouchInput { swipeRight() }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, day); assertEquals(2, selections); assertEquals(0, forgeOpens) }
        assertEquals(before, button.fetchSemanticsNode().boundsInRoot.left, 1f)
    }

    @Test fun aShortAnimatedDragReturnsToCentreWithoutChangingDays() {
        show(motion = true)
        val button = compose.onNodeWithText("Forge a cycle")
        val before = button.fetchSemanticsNode().boundsInRoot.left
        val home = compose.onNodeWithTag("home")
        home.performTouchInput {
            down(Offset(width * .6f, height * .8f))
            moveBy(Offset(-width * .1f, 0f))
        }
        compose.mainClock.advanceTimeBy(32)
        compose.waitForIdle()
        assertTrue(button.fetchSemanticsNode().boundsInRoot.left < before - 1f)
        home.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, day); assertEquals(0, selections) }
        assertEquals(before, button.fetchSemanticsNode().boundsInRoot.left, 1f)
    }

    @Test fun theHeaderAndWeekStayFixedWhileOnlyTheDayContentMoves() {
        show(motion = true)
        val monday = compose.onNodeWithContentDescription("Monday")
        val before = monday.fetchSemanticsNode().boundsInRoot
        val button = compose.onNodeWithText("Forge a cycle")
        val bodyBefore = button.fetchSemanticsNode().boundsInRoot.left
        compose.onNodeWithTag("home").performTouchInput {
            down(Offset(width * .8f, height * .8f))
            moveTo(Offset(width * .4f, height * .8f))
        }
        compose.mainClock.advanceTimeBy(32)
        compose.waitForIdle()
        assertEquals("The weekday bar must remain fixed", before, monday.fetchSemanticsNode().boundsInRoot)
        assertTrue(button.fetchSemanticsNode().boundsInRoot.left < bodyBefore - 10f)
        compose.onNodeWithTag("home").performTouchInput { cancel() }
        compose.mainClock.advanceTimeBy(1_000)
    }

    @Test fun theWeekIndicatorTracksTheFingerAndReturnsOnCancellation() {
        show(motion = true)
        val marker = compose.onNodeWithTag("today-week-indicator")
        val before = marker.fetchSemanticsNode().boundsInRoot.center.x
        val next = compose.onNodeWithContentDescription("Tuesday").fetchSemanticsNode().boundsInRoot.center.x
        val home = compose.onNodeWithTag("home")
        home.performTouchInput {
            down(Offset(width * .8f, height * .8f))
            moveTo(Offset(width * .4f, height * .8f))
        }
        compose.mainClock.advanceTimeBy(32)
        compose.waitForIdle()
        val during = marker.fetchSemanticsNode().boundsInRoot.center.x
        assertTrue("Underline must move towards Tuesday while dragging", during > before + 1f && during < next)
        compose.runOnIdle { assertEquals(1, day); assertEquals(0, selections) }
        home.performTouchInput { cancel() }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        assertEquals(before, marker.fetchSemanticsNode().boundsInRoot.center.x, 1f)
        compose.runOnIdle { assertEquals(1, day) }
    }

    @Test fun verticalFallbackScrollsOnlyTheContentBelowTheWeek() {
        show(height = 320)
        val monday = compose.onNodeWithContentDescription("Monday")
        val before = monday.fetchSemanticsNode().boundsInRoot
        compose.onNode(hasScrollAction()).performTouchInput { swipeUp() }
        advance()
        assertEquals("Scrolling the day must not scroll the header", before, monday.fetchSemanticsNode().boundsInRoot)
        monday.assertIsDisplayed().assertIsSelected()
    }

}
