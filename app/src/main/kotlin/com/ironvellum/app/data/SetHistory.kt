package com.ironvellum.app.data

import com.ironvellum.app.data.db.SetLogEntity
import com.ironvellum.app.domain.Variation

/** How a movement's past trials are read for progression: each variation gets its own history, not a share of one. */
internal object SetHistory {

    /** Sets of one variation the engine reads; the same depth the plain history always had. */
    const val DEPTH = 40

    /**
     * Each past trial's sets of the [nth] block of one movement, newest trial first, kept to those done in [variation]
     * ([Variation.key]) and cut once [DEPTH] of them are in (a trial is never split). [doneSets] is every done set of the movement, newest trial
     * first. The block is picked by position BEFORE the variation filter, so a rite that names a movement twice keeps
     * its blocks apart; the variation filter comes before the depth cut, so a long run of one variation cannot push
     * another out of its history.
     */
    fun blocks(doneSets: List<SetLogEntity>, nth: Int, variation: String): List<List<SetLogEntity>> {
        var taken = 0
        val out = ArrayList<List<SetLogEntity>>()
        for (rows in doneSets.groupBy { it.sessionId }.values) {
            if (taken >= DEPTH) break
            val position = rows.map { it.exercisePosition }.distinct().sorted().getOrNull(nth) ?: continue
            val block = rows.filter { it.exercisePosition == position && Variation.key(it.modifiers) == variation }
            if (block.isEmpty()) continue
            out += block
            taken += block.size
        }
        return out
    }
}
