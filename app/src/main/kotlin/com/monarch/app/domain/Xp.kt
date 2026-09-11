package com.monarch.app.domain

object Xp {
    const val BASE_PER_SET = 15
    const val PER_REP = 1
    const val COMPLETION_BONUS = 25
    const val QUEST_BONUS = 25
    fun award(sets: Int, reps: Int): Int =
        sets.coerceAtLeast(0) * BASE_PER_SET + reps.coerceAtLeast(0) * PER_REP + COMPLETION_BONUS

    fun xpForNextLevel(level: Int): Long = 100L * level

    data class Progress(val level: Int, val intoLevel: Long, val needed: Long)

    fun progress(totalXp: Long): Progress {
        var remaining = totalXp.coerceAtLeast(0)
        var level = 1
        while (remaining >= xpForNextLevel(level)) {
            remaining -= xpForNextLevel(level)
            level++
        }
        return Progress(level, remaining, xpForNextLevel(level))
    }

    fun levelFor(totalXp: Long): Int = progress(totalXp).level
}
