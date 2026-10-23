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
    // The dark tones, also what a deed seal draws on its dark enamel in either theme.
    internal val IronDark = Color(0xFF8A8F96)
    internal val BronzeDark = Color(0xFFC98B5B)
    internal val GoldDark = Color(0xFFD6A94A)
    internal val PrismaticDark = Color(0xFFBFB0F7)

    // On Vellum each rarity word is a darkened tone of its metal (4.5:1 on the page and the card).
    val Iron get() = if (IronvellumColors.light) Color(0xFF555F69) else IronDark // slate oklch(.48 .02 250)
    val Bronze get() = if (IronvellumColors.light) Color(0xFF944D24) else BronzeDark // copper oklch(.50 .11 48)
    // shortcut: the approved gold word (oklch .54 .12 75, #976200) reads 4.26:1 on the page; L .526 reaches 4.5:1.
    val Gold get() = if (IronvellumColors.light) Color(0xFF925E00) else GoldDark // gold oklch(.526 .12 75)
    val Prismatic get() = if (IronvellumColors.light) Color(0xFF6646A8) else PrismaticDark // violet oklch(.48 .15 295)

    /** The dark tone of a tier, for art drawn on dark enamel. */
    internal fun enamel(rarity: TitleRarity): Color = when (rarity) {
        TitleRarity.Common -> IronDark
        TitleRarity.Rare -> BronzeDark
        TitleRarity.Epic -> GoldDark
        TitleRarity.Masterwork -> PrismaticDark
    }

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
