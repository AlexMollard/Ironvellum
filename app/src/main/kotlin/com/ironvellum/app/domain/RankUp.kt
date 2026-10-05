package com.ironvellum.app.domain

/**
 * The rank-up moment: a band a seal lifted the Ironbound into, above every
 * band held before.
 *
 * [check] takes the stored high-water band index (null before the first seal
 * that recorded one) and the bands before and after a seal. With no stored
 * mark, the band before the seal stands in for it, so switching scoring rules
 * is never itself a rank-up. A seal celebrates only a band it lifted the
 * lifter into, above every band held before; gains from imports or skill
 * practice between seals are stored silently at the next seal. Untrained never
 * celebrates. Bands are indices into [Rank.BANDS]; unranked is -1.
 */
object RankUp {

    data class Outcome(val highest: Int, val rankUp: String?)

    fun check(highest: Int?, before: String?, after: String?): Outcome {
        val was = index(before)
        val held = highest ?: was
        val now = index(after)
        val celebrate = now > held && now > was && now > 0
        return Outcome(maxOf(held, now), if (celebrate) after else null)
    }

    private fun index(band: String?): Int = band?.let { Rank.BANDS.indexOf(it) } ?: -1
}
