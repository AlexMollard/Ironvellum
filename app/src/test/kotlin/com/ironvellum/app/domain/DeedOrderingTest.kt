package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeedOrderingTest {
    private fun of(rarity: TitleRarity, n: Int) = Titles.ALL.filter { it.rarity == rarity }[n]

    private val common = of(TitleRarity.Common, 0)
    private val commonB = of(TitleRarity.Common, 1)
    private val rare = of(TitleRarity.Rare, 0)
    private val epic = of(TitleRarity.Epic, 0)
    private val masterwork = of(TitleRarity.Masterwork, 0)
    private val unlocked = mapOf(common.id to 10L, commonB.id to 50L, rare.id to 20L, epic.id to 30L, masterwork.id to 5L)
    private val all = listOf(common, commonB, rare, epic, masterwork)

    @Test
    fun rarestGroupsTiersTopFirstAndNewestWithinATier() {
        val groups = groupEarned(all, unlocked, EarnedOrder.Rarest)
        assertEquals(
            listOf(TitleRarity.Masterwork, TitleRarity.Epic, TitleRarity.Rare, TitleRarity.Common),
            groups.map { it.rarity },
        )
        assertEquals(listOf(commonB, common), groups.last().deeds)
    }

    @Test
    fun rarestSkipsTiersWithNothingEarned() {
        val groups = groupEarned(listOf(common, epic), unlocked, EarnedOrder.Rarest)
        assertEquals(listOf(TitleRarity.Epic, TitleRarity.Common), groups.map { it.rarity })
    }

    @Test
    fun newestIsOneFlatRunByDate() {
        val groups = groupEarned(all, unlocked, EarnedOrder.Newest)
        assertEquals(1, groups.size)
        assertNull(groups.single().rarity)
        assertEquals(listOf(commonB, epic, rare, common, masterwork), groups.single().deeds)
    }

    @Test
    fun nothingEarnedGivesNoGroups() {
        EarnedOrder.entries.forEach { assertEquals(emptyList<EarnedGroup>(), groupEarned(emptyList(), unlocked, it)) }
    }
}
