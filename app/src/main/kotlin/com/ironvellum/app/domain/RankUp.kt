package com.ironvellum.app.domain

/**
 * The rank-up moment: a band the Ironbound has never held before.
 *
 * [check] takes the stored high-water band index (null before the first seal
 * that recorded one) and the bands before and after a seal. With no stored
 * mark, the band before the seal stands in for it, so switching scoring rules
 * is never itself a rank-up. Bands are indices into [Rank.BANDS]; unranked
 * is -1.
 */
object RankUp {

    data class Outcome(val highest: Int, val rankUp: String?)

    fun check(highest: Int?, before: String?, after: String?): Outcome {
        val held = highest ?: index(before)
        val now = index(after)
        return if (now > held) Outcome(now, after) else Outcome(held, null)
    }

    private fun index(band: String?): Int = band?.let { Rank.BANDS.indexOf(it) } ?: -1
}
