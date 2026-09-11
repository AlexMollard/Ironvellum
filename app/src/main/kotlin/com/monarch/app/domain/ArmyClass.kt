package com.monarch.app.domain

/**
 * The shadow army ladder. You begin as nothing; every class is seized by
 * leveling. Levels mirror [Rank]'s hunter grade but grow the army instead.
 */
object ArmyClass {

    data class Tier(val level: Int, val title: String)

    val LADDER: List<Tier> = listOf(
        Tier(1, "the Awakened"),
        Tier(5, "Shadow Soldier"),
        Tier(15, "Knight"),
        Tier(25, "Elite Knight"),
        Tier(40, "Commander"),
        Tier(55, "Marshal"),
        Tier(70, "Shadow Monarch"),
    )

    fun forLevel(level: Int): Tier {
        var current = LADDER.first()
        LADDER.forEach { tier -> if (level >= tier.level) current = tier }
        return current
    }

    fun nextFor(level: Int): Tier? = LADDER.firstOrNull { it.level > level }
}
