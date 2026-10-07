package com.ironvellum.app.ui.components

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.ironvellum.app.domain.ArmyClass
import com.ironvellum.app.domain.Reward
import com.ironvellum.app.domain.RewardRarity
import com.ironvellum.app.domain.RollResult
import com.ironvellum.app.domain.Sex
import com.ironvellum.app.domain.SkillClaimResult
import com.ironvellum.app.domain.Skills
import com.ironvellum.app.domain.Titles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What each caller of the celebration host hands it, as pure page lists. */
class CelebrationPagesTest {

    private val skill = Skills.ALL.first()
    private val deed = Titles.ALL.first { it.name.isNotBlank() }

    private fun claim(levelBefore: Int, levelAfter: Int, unlocked: Int = 0, deeds: Int = 0) = SkillClaimResult(
        skill = skill,
        xpAwarded = 120,
        levelBefore = levelBefore,
        levelAfter = levelAfter,
        totalXp = 1_000,
        newTitles = Titles.ALL.filter { it.name.isNotBlank() }.take(deeds),
        unlockedNext = Skills.ALL.drop(1).take(unlocked),
    )

    @Test
    fun `a claim with a level and a deed tells the technique, the level with its inscription, then the deeds`() {
        val pages = techniquePages(claim(levelBefore = 4, levelAfter = 5, unlocked = 1, deeds = 2), Sex.MALE)
        assertEquals(3, pages.size)
        val technique = pages[0] as CelebrationPage.TechniqueMastered
        assertEquals(skill.name, technique.name)
        assertEquals(Skills.tierLabel(skill.tier), technique.tier)
        assertEquals(120, technique.xp)
        assertEquals(Skills.ALL.drop(1).take(1).map { it.name }, technique.opened)
        val level = pages[1] as CelebrationPage.Level
        assertTrue("the Veil's draw is one line on the level page, not a page of its own", level.inscriptionWaiting)
        assertEquals(2, (pages[2] as CelebrationPage.Deeds).deeds.size)
    }

    @Test
    fun `a claim that stays on its level is one page`() {
        val pages = techniquePages(claim(levelBefore = 7, levelAfter = 7), Sex.FEMALE)
        assertEquals(1, pages.size)
        assertTrue(pages.single() is CelebrationPage.TechniqueMastered)
        assertNull(levelUpOf(claim(levelBefore = 7, levelAfter = 7)))
    }

    @Test
    fun `a level up names the ascension on each side of it`() {
        val up = levelUpOf(claim(levelBefore = 4, levelAfter = 5))!!
        assertEquals(ArmyClass.forLevel(4).title, up.classBefore)
        assertEquals(ArmyClass.forLevel(5).title, up.classAfter)
        assertTrue(up.classBefore != up.classAfter)
        assertEquals(1_000L, up.totalXp)
        assertEquals(120, up.xpAwarded)
        val within = levelUpOf(claim(levelBefore = 6, levelAfter = 7))!!
        assertEquals(within.classBefore, within.classAfter)
    }

    @Test
    fun `owed deeds share one page and a nameless deed is never shown`() {
        val pages = deedPages(listOf(deed, deed.copy(id = "other", name = "")), Sex.MALE)
        assertEquals(listOf(deed), (pages.single() as CelebrationPage.Deeds).deeds)
        assertTrue(deedPages(emptyList(), Sex.MALE).isEmpty())
        assertTrue(deedPages(listOf(deed.copy(name = " ")), Sex.MALE).isEmpty())
    }

    @Test
    fun `a relic shows its sigil and its rate`() {
        val page = inscribedPage(RollResult(Reward.Relic(multiplier = 1.25, name = "Ember Tooth"), RewardRarity.Epic))
        assertEquals("Ember Tooth", page.name)
        assertEquals("Ember Tooth", page.sigilSeed)
        assertEquals(listOf("Rate multiplier ×1.25"), page.notes)
        assertEquals("Fabled", rarityWord(page.rarity))
    }

    @Test
    fun `echoes and crests have no sigil`() {
        val figures = inscribedPage(RollResult(Reward.Figures(1), RewardRarity.Common))
        assertEquals("1 echo", figures.name)
        assertNull(figures.sigilSeed)
        assertEquals("40 echoes", inscribedPage(RollResult(Reward.Figures(40), RewardRarity.Common)).name)
        val crest = inscribedPage(RollResult(Reward.CrestFrame("iron", "Iron Crest"), RewardRarity.Rare))
        assertNull(crest.sigilSeed)
        assertEquals("Iron Crest", crest.name)
        assertEquals(listOf("Crest inscribed", "Wear it on your folio"), crest.notes)
    }

    @Test
    fun `inscription copy is sentence case with one narrator line each`() {
        for (rarity in RewardRarity.entries) {
            val narrator = inscribedNarrator(rarity)
            assertTrue(narrator, narrator.endsWith("."))
            assertTrue(narrator, narrator != narrator.uppercase())
            assertFalse(rarityWord(rarity), rarityWord(rarity).equals("epic", ignoreCase = true))
            assertTrue(rarityWord(rarity), rarityWord(rarity) != rarityWord(rarity).uppercase())
        }
    }

    @Test
    fun `haptics keep their beats per rarity`() {
        assertEquals(listOf(HapticFeedbackType.SegmentTick), inscribedBeats(RewardRarity.Common).map { it.second })
        assertEquals(listOf(HapticFeedbackType.Confirm), inscribedBeats(RewardRarity.Rare).map { it.second })
        assertEquals(List(2) { HapticFeedbackType.Confirm }, inscribedBeats(RewardRarity.Epic).map { it.second })
        assertEquals(listOf(HapticFeedbackType.LongPress), inscribedBeats(RewardRarity.Masterwork).map { it.second })
    }
}
