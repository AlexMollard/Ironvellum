package com.ironvellum.app.domain

/**
 * Which of a movement's sets in one trial may set a peak or a record. Normally every done working set. On a lift
 * that had a top set, only the top set: the back-offs are lighter by design, so judging them would only ever
 * show a lift as stalled, and the top set is the one the lifter is trying to beat. Back-offs still earn their XP
 * and hard-set credit, which never ask this. One rule for the live badges, the victory panel, the XP bonus, the
 * records, the boards and the charts.
 */
object PeakSets {

    /** The done working sets of ONE movement in ONE trial that count towards peaks and records. */
    fun eligible(block: List<SessionSet>): List<SessionSet> {
        val worked = block.filter { it.done && !it.warmup }
        val tops = worked.filter { it.kind == SetKind.TOP }
        return tops.ifEmpty { worked }
    }

    /** One trial's sets with each movement narrowed by [eligible], in their original order. */
    fun narrowed(sets: List<SessionSet>): List<SessionSet> {
        val keep = sets.groupBy { it.exerciseId }.flatMap { (_, block) -> eligible(block) }
        return sets.filter { set -> keep.any { it === set } }
    }

    /** True when a movement's sets include a done top set: peaks, records and charts follow it alone. */
    fun hasTop(block: List<SessionSet>): Boolean = block.any { it.done && it.kind == SetKind.TOP }
}
