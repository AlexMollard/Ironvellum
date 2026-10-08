package com.ironvellum.app.domain

/** How the earned deeds are laid out: by tier, rarest first, or by the day they were won. */
enum class EarnedOrder(val label: String) {
    Rarest("Rarest"),
    Newest("Newest"),
}

/** One run of earned deeds; [rarity] is the tier header, or null when the list is a single flat run. */
data class EarnedGroup(val rarity: TitleRarity?, val deeds: List<TitleDef>)

/**
 * Lays out the deeds in [earned]. Rarest groups them by tier, the top tier first, and each
 * tier runs newest first. Newest is one flat run, newest first. Ties on the day fall back to
 * the id, so the order never shuffles between recompositions.
 */
fun groupEarned(earned: List<TitleDef>, unlockedAt: Map<String, Long>, order: EarnedOrder): List<EarnedGroup> {
    val newestFirst = earned.sortedWith(
        compareByDescending<TitleDef> { unlockedAt[it.id] ?: 0L }.thenBy { it.id },
    )
    return when (order) {
        EarnedOrder.Newest -> if (newestFirst.isEmpty()) emptyList() else listOf(EarnedGroup(null, newestFirst))
        EarnedOrder.Rarest -> newestFirst
            .groupBy { it.rarity }
            .toSortedMap(compareByDescending { it.ordinal })
            .map { (rarity, deeds) -> EarnedGroup(rarity, deeds) }
    }
}
