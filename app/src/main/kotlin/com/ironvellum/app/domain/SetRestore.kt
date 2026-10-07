package com.ironvellum.app.domain

/**
 * Where each set of one movement belongs once removed sets are put back.
 *
 * [before] is every set of the movement as it stood just before the removal
 * (id to setIndex), the removed ones included; [current] is what the movement
 * holds now, the restored rows already in. Sets in [before] go back to their
 * old order; a set the lifter added during the Undo window was not in it and
 * follows them. The result numbers the whole movement 0..n-1 with no gaps and
 * no duplicates, whatever happened in between.
 */
fun restoredIndexes(before: Map<Long, Int>, current: List<Pair<Long, Int>>): Map<Long, Int> =
    current
        .sortedWith(compareBy({ (id, _) -> if (id in before) 0 else 1 }, { (id, index) -> before[id] ?: index }))
        .mapIndexed { position, (id, _) -> id to position }
        .toMap()
