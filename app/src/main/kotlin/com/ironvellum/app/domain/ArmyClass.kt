package com.ironvellum.app.domain

import com.ironvellum.app.data.Words

/**
 * The muster ladder. You begin as nothing; every class is seized by leveling.
 * It follows level (XP); [Rank] follows strength and never reads level.
 *
 * Plain military ranks on purpose: they carry the tone without borrowing any
 * one setting's vocabulary.
 */
object ArmyClass {

    /** [themedTitle] is the ascension's name; plain mode names the band "Level", so the sigil reads "Level 13". */
    data class Tier(val level: Int, private val themedTitle: String) {
        val title: String get() = Words.pick(themedTitle, "Level")
    }

    val LADDER: List<Tier> = listOf(
        Tier(1, "Acolyte"),
        Tier(5, "Adept"),
        Tier(15, "Warden"),
        Tier(25, "Magister"),
        Tier(40, "Archon"),
        Tier(55, "Exarch"),
        Tier(70, "Sovereign"),
    )

    fun forLevel(level: Int): Tier {
        var current = LADDER.first()
        LADDER.forEach { tier -> if (level >= tier.level) current = tier }
        return current
    }

    fun nextFor(level: Int): Tier? = LADDER.firstOrNull { it.level > level }
}
