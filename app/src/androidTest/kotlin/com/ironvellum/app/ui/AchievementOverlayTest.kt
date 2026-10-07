package com.ironvellum.app.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ironvellum.app.domain.ArmyClass
import com.ironvellum.app.domain.RewardRarity
import com.ironvellum.app.domain.Sex
import com.ironvellum.app.domain.TitleRarity
import com.ironvellum.app.domain.Titles
import com.ironvellum.app.ui.components.AchievementOverlay
import com.ironvellum.app.ui.components.CelebrationPage
import com.ironvellum.app.ui.components.LevelUp
import com.ironvellum.app.ui.components.deedPages
import com.ironvellum.app.ui.theme.IronvellumTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The celebration host: every page kind renders (each saves a screenshot,
 * files/reveal-*.png, for a visual check), Continue is the only way on, a
 * tap on the page itself does nothing, and a deed's title can be worn from the
 * dock.
 */
@RunWith(AndroidJUnit4::class)
class AchievementOverlayTest {

    @get:Rule
    val compose = createComposeRule()

    private fun deed(rarity: TitleRarity) = Titles.ALL.first { it.rarity == rarity }

    private fun deeds(rarity: TitleRarity) = deedPages(listOf(deed(rarity)), Sex.MALE)

    private fun level(before: Int, after: Int, inscription: Boolean = false) =
        CelebrationPage.Level(LevelUp(before, after, classNamed(before), classNamed(after), totalXp = 9_140, xpAwarded = 186), inscription)

    private fun classNamed(level: Int) = ArmyClass.forLevel(level).title

    private var worn: String? = null
    private var opened: String? = null
    private var done = 0

    private fun show(
        pages: List<CelebrationPage>,
        wornTitleId: String? = null,
        canWear: Boolean = true,
        canOpen: Boolean = false,
    ) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            IronvellumTheme {
                AchievementOverlay(
                    pages = pages,
                    onDone = { done++ },
                    wornTitleId = wornTitleId,
                    onWear = if (canWear) { id: String -> worn = id } else null,
                    onOpenTechnique = if (canOpen) { name: String -> opened = name } else null,
                )
            }
        }
    }

    private fun finishAndCapture(name: String) {
        compose.mainClock.advanceTimeBy(4_000)
        compose.saveDialogScreenshot("reveal-$name")
    }

    private fun count(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().size

    @Test fun common() { show(deeds(TitleRarity.Common)); finishAndCapture("common") }
    @Test fun rare() { show(deeds(TitleRarity.Rare)); finishAndCapture("rare") }
    @Test fun fabled() { show(deeds(TitleRarity.Epic)); finishAndCapture("fabled") }
    @Test fun masterwork() { show(deeds(TitleRarity.Masterwork)); finishAndCapture("masterwork") }
    @Test fun levelUp() { show(listOf(level(13, 14))); finishAndCapture("level") }
    @Test fun ascension() { show(listOf(level(4, 5, inscription = true))); finishAndCapture("ascension") }

    @Test
    fun techniqueMastered() {
        show(listOf(CelebrationPage.TechniqueMastered("Archer pull-up", "III", "Pull-up path", 120, listOf("Typewriter pull-up"))))
        finishAndCapture("technique")
        compose.onNodeWithText("Technique mastered").assertExists()
        compose.onNodeWithText("+120 XP").assertExists()
    }

    @Test
    fun inscribedRelic() {
        show(listOf(CelebrationPage.Inscribed(RewardRarity.Epic, "Ember Tooth", sigilSeed = "Ember Tooth", notes = listOf("Rate multiplier ×1.25"))))
        finishAndCapture("inscribed")
        compose.onNodeWithText("Fabled").assertExists()
        compose.onNodeWithText("Rate multiplier ×1.25").assertExists()
    }

    @Test
    fun goalMet() {
        show(listOf(CelebrationPage.GoalMet("Iron Circle", 80)))
        finishAndCapture("goal")
        compose.onNodeWithText("Iron Circle").assertExists()
        compose.onNodeWithText("+80 XP").assertExists()
    }

    @Test
    fun theLevelPageCarriesTheInscriptionLine() {
        show(listOf(level(13, 14, inscription = true)))
        compose.mainClock.advanceTimeBy(4_000)
        compose.onNodeWithText("An inscription is waiting").assertExists()
        compose.onNodeWithContentDescription("Level 14").assertExists()
    }

    @Test
    fun onlyContinueMovesOn() {
        show(deeds(TitleRarity.Masterwork) + level(4, 5))
        compose.mainClock.advanceTimeBy(4_000)
        compose.onNodeWithText("1 of 2", useUnmergedTree = true).assertExists()
        // The page is not a button: tapping it neither skips ahead nor finishes.
        compose.onNodeWithText("Deed earned").performClick()
        compose.mainClock.advanceTimeBy(50)
        compose.onNodeWithText("1 of 2", useUnmergedTree = true).assertExists()
        assertEquals(0, done)
        compose.onNodeWithText("Continue").performClick()
        compose.mainClock.advanceTimeBy(4_000)
        compose.onNodeWithText("Level up").assertExists()
        compose.onNodeWithText("2 of 2", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Continue").performClick()
        assertEquals(1, done)
    }

    @Test
    fun aLonePageHasNoStepCount() {
        show(deeds(TitleRarity.Rare))
        compose.mainClock.advanceTimeBy(4_000)
        assertEquals(0, count("1 of 1"))
        compose.onNodeWithText("Continue").assertExists()
    }

    @Test
    fun aDeedCanBeWornFromTheDock() {
        val item = deed(TitleRarity.Rare)
        show(deeds(TitleRarity.Rare))
        compose.mainClock.advanceTimeBy(4_000)
        compose.onNodeWithText("Wear title").performClick()
        assertEquals(item.id, worn)
        compose.mainClock.advanceTimeBy(50)
        compose.onNodeWithText("Wearing ✓").assertExists()
    }

    @Test
    fun aDeedAlreadyWornSaysSo() {
        show(deeds(TitleRarity.Rare), wornTitleId = deed(TitleRarity.Rare).id)
        compose.mainClock.advanceTimeBy(4_000)
        compose.onNodeWithText("Wearing ✓").assertExists()
        assertEquals(0, count("Wear title"))
    }

    @Test
    fun withoutAWearHandlerThereIsNoWearLink() {
        show(deeds(TitleRarity.Rare), canWear = false)
        compose.mainClock.advanceTimeBy(4_000)
        assertEquals(0, count("Wear title"))
        compose.onNodeWithText("Continue").assertExists()
    }

    @Test
    fun theOneTechniqueAClaimOpenedIsALinkInTheDock() {
        show(
            listOf(CelebrationPage.TechniqueMastered("Archer pull-up", "III", "Pull-up path", 120, listOf("Typewriter pull-up"))),
            canOpen = true,
        )
        compose.mainClock.advanceTimeBy(4_000)
        compose.onNodeWithText("Open Typewriter pull-up").performClick()
        assertEquals("Typewriter pull-up", opened)
        assertEquals(1, done)
    }
}
