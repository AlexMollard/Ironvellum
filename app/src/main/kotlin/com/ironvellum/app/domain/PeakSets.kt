package com.ironvellum.app.domain

/**
 * Which of a movement's sets in one trial (in one [Variation] of it) may set a peak or a record. Normally every done working set. On a lift
 * that had a top set, only the top set: the back-offs are lighter by design, so judging them would only ever
 * show a lift as stalled, and the top set is the one the lifter is trying to beat. Back-offs still earn their XP
 * and hard-set credit, which never ask this. One rule for the live badges, the victory panel, the XP bonus, the
 * records, the boards and the charts.
 */
object PeakSets {
    /** Select top sets when present; callers retain their own eligibility and grouping rules. */
    inline fun <T> topOnlyOrElse(items: Iterable<T>, isTop: (T) -> Boolean, otherwise: () -> List<T>): List<T> =
        items.filter(isTop).ifEmpty(otherwise)

    /** The done working sets of ONE movement in ONE trial that count towards peaks and records. */
    fun eligible(block: List<SessionSet>): List<SessionSet> {
        val worked = block.filter { it.done && !it.warmup }
        return topOnlyOrElse(worked, { it.kind == SetKind.TOP }) { worked }
    }

    /** One trial's sets with each movement narrowed by [eligible], in their original order. */
    fun narrowed(sets: List<SessionSet>): List<SessionSet> {
        val keep = RepMax.blocks(sets).flatMap { block -> eligible(block) }
        return sets.filter { set -> keep.any { it === set } }
    }

    /** True when a movement's sets include a done top set: peaks, records and charts follow it alone. */
    fun hasTop(block: List<SessionSet>): Boolean = block.any { it.done && it.kind == SetKind.TOP }
}
