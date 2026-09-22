package com.ironvellum.app.domain

/**
 * Strength ranks — the ladder every lifter climbs.
 *
 * These are the real strength-standard bands (ExRx and every coaching text use
 * the same five words), not invented grades: a rank a lifter reads here means
 * the same thing a coach means by it. The level breakpoints are the ones the
 * ladder already used, so the top rank still lands at level 80.
 */
object Rank {

    const val UNTRAINED = "Untrained"
    const val NOVICE = "Novice"
    const val INTERMEDIATE = "Intermediate"
    const val ADVANCED = "Advanced"
    const val ELITE = "Elite"

    fun forLevel(level: Int): String = when {
        level < 10 -> UNTRAINED
        level < 30 -> NOVICE
        level < 50 -> INTERMEDIATE
        level < 80 -> ADVANCED
        else -> ELITE
    }
}
