package com.ironvellum.app.domain

/**
 * The muster ladder. You begin as nothing; every class is seized by leveling.
 * Levels mirror [Rank]'s strength grade but grow the roll instead.
 *
 * Plain military ranks on purpose: they carry the tone without borrowing any
 * one setting's vocabulary.
 */
object ArmyClass {

    data class Tier(val level: Int, val title: String)

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
