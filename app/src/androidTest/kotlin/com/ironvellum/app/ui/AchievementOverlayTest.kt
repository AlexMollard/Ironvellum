package com.ironvellum.app.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ironvellum.app.domain.Sex
import com.ironvellum.app.domain.TitleRarity
import com.ironvellum.app.domain.Titles
import com.ironvellum.app.ui.components.Achievement
import com.ironvellum.app.ui.components.AchievementOverlay
import com.ironvellum.app.ui.components.deedAchievement
import com.ironvellum.app.ui.components.levelUpAchievement
import com.ironvellum.app.ui.theme.IronvellumTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Ledger's reveal: every rarity and both page kinds render (each saves a
 * screenshot, files/reveal-*.png, for a visual check), a tap finishes the
 * writing before it moves on, and a deed's title can be worn from the page.
 */
@RunWith(AndroidJUnit4::class)
class AchievementOverlayTest {

    @get:Rule
    val compose = createComposeRule()

    private fun deed(rarity: TitleRarity) =
        deedAchievement(Titles.ALL.first { it.rarity == rarity }, Sex.MALE)

    private var worn: String? = null
    private var done = 0

    private fun show(items: List<Achievement>, wornTitleId: String? = null) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            IronvellumTheme {
                AchievementOverlay(
                    items = items,
                    onDone = { done++ },
                    wornTitleId = wornTitleId,
                    onWear = { worn = it },
                )
            }
        }
    }

    private fun finishAndCapture(name: String) {
        compose.mainClock.advanceTimeBy(4_000)
        compose.saveDialogScreenshot("reveal-$name")
    }

    @Test fun common() { show(listOf(deed(TitleRarity.Common))); finishAndCapture("common") }
    @Test fun rare() { show(listOf(deed(TitleRarity.Rare))); finishAndCapture("rare") }
    @Test fun fabled() { show(listOf(deed(TitleRarity.Epic))); finishAndCapture("fabled") }
    @Test fun masterwork() { show(listOf(deed(TitleRarity.Masterwork))); finishAndCapture("masterwork") }
    @Test fun levelUp() { show(listOf(levelUpAchievement(13, 14, 9_140))); finishAndCapture("level") }
    @Test fun ascension() { show(listOf(levelUpAchievement(4, 5, 1_000))); finishAndCapture("ascension") }

    @Test
    fun aTapFinishesTheWritingBeforeMovingOn() {
        show(listOf(deed(TitleRarity.Masterwork), levelUpAchievement(4, 5, 1_000)))
        compose.mainClock.advanceTimeBy(200)
        compose.onNodeWithText("DEED EARNED").performClick()
        compose.mainClock.advanceTimeBy(50)
        // Still the first page: the tap only completed the reveal.
        compose.onNodeWithText("NEXT (1/2)").assertExists()
        compose.onNodeWithText("DEED EARNED").performClick()
        compose.mainClock.advanceTimeBy(50)
        compose.onNodeWithText("LEVEL UP").assertExists()
        compose.onNodeWithText("CONTINUE").performClick()
        assertEquals(1, done)
    }

    @Test
    fun aDeedCanBeWornFromItsPage() {
        val item = deed(TitleRarity.Rare)
        show(listOf(item))
        compose.mainClock.advanceTimeBy(4_000)
        compose.onNodeWithText("Wear title").performClick()
        assertEquals(item.titleId, worn)
        compose.mainClock.advanceTimeBy(50)
        compose.onNodeWithText("Worn").assertExists()
    }

    @Test
    fun aDeedAlreadyWornSaysSo() {
        val item = deed(TitleRarity.Rare)
        show(listOf(item), wornTitleId = item.titleId)
        compose.mainClock.advanceTimeBy(4_000)
        compose.onNodeWithText("Worn").assertExists()
        compose.onNodeWithText("Worn on your folio").assertExists()
    }
}
