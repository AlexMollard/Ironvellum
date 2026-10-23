package com.ironvellum.app.ui.social

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.IronvellumApp
import com.ironvellum.app.data.Words
import com.ironvellum.app.data.cloud.LeaderboardRow
import com.ironvellum.app.data.cloud.ShadowBoardRow
import com.ironvellum.app.ui.ShotRecorder
import com.ironvellum.app.ui.theme.IronvellumTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The Boards tab is one list of cards, each with a one-line glance, and a card opens its own board. */
@RunWith(AndroidJUnit4::class)
class BoardCardsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val rows = listOf(
        LeaderboardRow("a", "Ada", 12, 5_000, 4, 3, 900, 3, null),
        LeaderboardRow("me", "Sam", 9, 3_200, 2, 1, 700, 2, null),
        LeaderboardRow("c", "Cy", 4, 800, 1, 0, 100, 1, null),
    )
    private val veilRows = listOf(
        ShadowBoardRow("c", "Cy", null, 4, 9_000, 2, 12.0),
        ShadowBoardRow("me", "Sam", null, 9, 4_000, 1, 6.0),
    )

    @Before
    fun plainWords() {
        Words.setThemed(InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as IronvellumApp, false)
    }

    private fun show(ui: LeaderboardUi, muster: MusterBoardUi, light: Boolean = false, onOpen: (BoardKind) -> Unit = {}) {
        compose.activityRule.scenario.onActivity {
            it.setContent {
                IronvellumTheme(light = light) {
                    Box(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) { BoardCards(ui, muster, onOpen) }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun eachCardGlancesAtYourPlaceOrTheLeaderAndOpensItsBoard() {
        val opened = ArrayList<BoardKind>()
        show(LeaderboardUi(signedIn = true, myUserId = "me", rows = rows), MusterBoardUi(rows = veilRows, loaded = true)) { opened += it }
        compose.onNodeWithText("Training").assertIsDisplayed()
        compose.onNodeWithText("Your place: 2 of 3").assertIsDisplayed()
        compose.onNodeWithText("Lifts").assertIsDisplayed()
        compose.onNodeWithText("Tiers lift by lift, this week or all time").assertIsDisplayed()
        compose.onNodeWithText("The Veil").assertIsDisplayed()
        compose.onNodeWithText("Your place: 2 of 2").assertIsDisplayed()
        ShotRecorder.take(compose, "boards-cards-dark")
        compose.onNodeWithText("Lifts").performClick()
        compose.onNodeWithText("The Veil").performClick()
        assertEquals(listOf(BoardKind.Lifts, BoardKind.Muster), opened)
    }

    @Test
    fun aLifterNotOnTheBoardSeesWhoLeadsAndANeverSetUpVeilHasNoCard() {
        show(
            LeaderboardUi(signedIn = true, myUserId = "other", rows = rows),
            MusterBoardUi(available = false),
            light = true,
        )
        compose.onNodeWithText("Top: Ada, 5,000 XP").assertIsDisplayed()
        compose.onAllNodesWithText("The Veil").assertCountEquals(0)
        ShotRecorder.take(compose, "boards-cards-light")
    }
}
