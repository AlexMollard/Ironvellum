package com.monarch.app.domain

/** Hunter ranks — the ladder every shadow climbs. */
object Rank {

    const val E = "E-Rank"
    const val D = "D-Rank"
    const val C = "C-Rank"
    const val B = "B-Rank"
    const val A = "A-Rank"
    const val S = "S-Rank"
    const val NATIONAL = "National-Level"
    const val MONARCH = "Monarch"

    fun forLevel(level: Int): String = when {
        level < 10 -> E
        level < 20 -> D
        level < 30 -> C
        level < 40 -> B
        level < 50 -> A
        level < 65 -> S
        level < 80 -> NATIONAL
        else -> MONARCH
    }
}
