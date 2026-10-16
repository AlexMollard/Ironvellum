package com.ironvellum.app.ui.theme

import androidx.compose.ui.graphics.Color
import com.ironvellum.app.domain.RewardRarity
import com.ironvellum.app.domain.TitleRarity

/**
 * The metal ladder for rarity: iron, bronze, gold, then a pale prismatic for the rarest.
 * Fixed. It is never themed by the accent pair and is used for nothing but rarity
 * (docs/DESIGN.md, "rarity, not accents"). Each tone clears 4.5:1 on Vault and Abyss.
 */
object RarityTint {
    // On Vellum each tone is a deeper shade of the same metal (4.5:1 on the panel and the page).
    val Iron get() = if (IronvellumColors.light) Color(0xFF5F656C) else Color(0xFF8A8F96)
    val Bronze get() = if (IronvellumColors.light) Color(0xFF8C5A2B) else Color(0xFFC98B5B)
    val Gold get() = if (IronvellumColors.light) Color(0xFF7A5800) else Color(0xFFD6A94A)
    val Prismatic get() = if (IronvellumColors.light) Color(0xFF5B4BB5) else Color(0xFFBFB0F7)

    fun of(rarity: TitleRarity): Color = when (rarity) {
        TitleRarity.Common -> Iron
        TitleRarity.Rare -> Bronze
        TitleRarity.Epic -> Gold
        TitleRarity.Masterwork -> Prismatic
    }

    /** The top two tiers sit on a soft ring. */
    fun glows(rarity: TitleRarity): Boolean = rarity >= TitleRarity.Epic

    /** A drawn reward (relic or crest) wears the same ladder as a deed of its tier. */
    fun of(rarity: RewardRarity): Color = when (rarity) {
        RewardRarity.Common -> Iron
        RewardRarity.Rare -> Bronze
        RewardRarity.Epic -> Gold
        RewardRarity.Masterwork -> Prismatic
    }

    fun glows(rarity: RewardRarity): Boolean = rarity >= RewardRarity.Epic
}
