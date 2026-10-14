package com.ironvellum.app.domain

/**
 * Supersets: adjacent exercises done as one group, a set of each in turn, with rest only once the round is
 * done. Pure, so the live trial screen, the lock-screen "Set done", the trial notification, the rite
 * editor and the tests all ask the same rules.
 *
 * A group is stored as a number on every set (and every rite entry) of its members. It is READ as a
 * superset only while its members sit next to each other in trial order and number two or more: a stale
 * number left behind by a removal, an undo or a reorder is simply not a superset, so nothing has to clean
 * up after every edit. Linking is capped at [MAX_SIZE] members (pairs and triples); a longer run that came
 * in from an import still reads and flows as one.
 *
 * Indexes in these functions are places in trial order, not stored positions: stored positions can have
 * gaps (a removed exercise leaves one).
 */
object Supersets {

    /** Linking makes pairs and triples; nobody does four with a phone in their hand. */
    const val MAX_SIZE = 3

    /**
     * A new order after linking or moving: [order] lists the old index that now sits in each slot, and
     * [groups] is the group number of the exercise in each slot, singletons already cleared.
     */
    data class Layout(val order: List<Int>, val groups: List<Int?>)

    // ------------------------------------------------------------- groups in a list

    /** The runs of two or more neighbours sharing one group number, as inclusive index ranges. */
    fun runs(raw: List<Int?>): List<IntRange> {
        val runs = mutableListOf<IntRange>()
        var start = 0
        while (start < raw.size) {
            var end = start
            val group = raw[start]
            while (group != null && end + 1 < raw.size && raw[end + 1] == group) end++
            if (group != null && end > start) runs += start..end
            start = end + 1
        }
        return runs
    }

    /** The run holding [index], or null when that exercise is not in a superset. */
    fun runAt(raw: List<Int?>, index: Int): IntRange? = runs(raw).firstOrNull { index in it }

    /** [raw] with every number outside a run cleared: what is really linked. */
    fun effective(raw: List<Int?>): List<Int?> {
        val keep = runs(raw).flatMap { it.toList() }.toSet()
        return raw.mapIndexed { i, g -> if (i in keep) g else null }
    }

    /** "Superset 1", "Superset 2": each run's place in trial order, by slot; null outside a run. */
    fun numbers(raw: List<Int?>): List<Int?> {
        val result = MutableList<Int?>(raw.size) { null }
        runs(raw).forEachIndexed { n, run -> run.forEach { result[it] = n + 1 } }
        return result
    }

    private fun unitAt(raw: List<Int?>, index: Int): IntRange = runAt(raw, index) ?: index..index

    private val IntRange.size: Int get() = last - first + 1

    private fun nextNumber(raw: List<Int?>): Int = (raw.filterNotNull().maxOrNull() ?: 0) + 1

    // ------------------------------------------------------------- editing a list

    /** Whether [b] can be linked to [a]: two different exercises, not already together, and no more than [MAX_SIZE]. */
    fun canLink(raw: List<Int?>, a: Int, b: Int): Boolean = link(raw, a, b) != null

    /**
     * Links [b] (with its own superset, if it has one) to [a] (with its). The partner moves next to the group:
     * after it when it came later, before it when it came earlier, so the rest of the order is kept. Null when
     * either is out of range, they are already together, or the result would exceed [MAX_SIZE].
     */
    fun link(raw: List<Int?>, a: Int, b: Int): Layout? {
        if (a !in raw.indices || b !in raw.indices) return null
        val ua = unitAt(raw, a)
        val ub = unitAt(raw, b)
        if (ua == ub || ua.size + ub.size > MAX_SIZE) return null
        val without = raw.indices.filter { it !in ub }
        val at = if (ub.first > ua.last) without.indexOf(ua.last) + 1 else without.indexOf(ua.first)
        val order = without.take(at) + ub.toList() + without.drop(at)
        val eff = effective(raw)
        val group = if (ua.size > 1) raw[ua.first] else if (ub.size > 1) raw[ub.first] else nextNumber(raw)
        val moved = ua.toSet() + ub.toSet()
        return Layout(order, order.map { if (it in moved) group else eff[it] })
    }

    /** [raw] with the superset holding [index] dissolved: Unlink ends the whole group, so no exercise is left half in one. */
    fun unlink(raw: List<Int?>, index: Int): List<Int?> {
        val run = runAt(raw, index) ?: return effective(raw)
        return effective(raw).mapIndexed { i, g -> if (i in run) null else g }
    }

    /**
     * Moves the exercise at [index] one place up or down, keeping every superset whole. Inside its own group
     * it swaps with its neighbour. A lone exercise steps over the whole neighbouring group, never into the
     * middle of it; a member that moves out past its group's edge leaves the group. Null when there is
     * nowhere to move.
     */
    fun move(raw: List<Int?>, index: Int, up: Boolean): Layout? {
        if (index !in raw.indices) return null
        val eff = effective(raw)
        val step = if (up) -1 else 1
        val own = runAt(raw, index)
        val target = index + step
        if (own != null && target in own) {
            val order = raw.indices.toMutableList().also { it[index] = target; it[target] = index }
            return Layout(order, order.map { eff[it] })
        }
        if (target !in raw.indices) return null
        val over = unitAt(raw, target)
        val without = raw.indices.filter { it != index }
        val at = if (up) without.indexOf(over.first) else without.indexOf(over.last) + 1
        val order = without.take(at) + index + without.drop(at)
        // Leaving a pair strands its partner, so the numbers are read again from the new order.
        val groups = effective(order.map { if (it == index) null else eff[it] })
        return Layout(order, groups)
    }

    // ------------------------------------------------------------- the live trial

    /** One exercise of a live trial: its sets in order and the group number any of them carries. */
    class Block(val exerciseId: Long, val position: Int, val sets: List<SessionSet>) {
        val group: Int? get() = sets.firstNotNullOfOrNull { it.supersetGroup }
        val doneWorking: Int get() = sets.count { it.done && !it.warmup }
        val hasPending: Boolean get() = sets.any { it.isPending }
    }

    /** The exercises of [sets] in trial order: one block per exercise, placed by its first stored position. */
    fun blocks(sets: List<SessionSet>): List<Block> =
        sets.groupBy { it.exerciseId }
            .map { (id, rows) -> Block(id, rows.minOf { it.exercisePosition }, rows.sortedBy { it.setIndex }) }
            .sortedBy { it.position }

    /**
     * The set to log next. Without supersets that is the first set still waiting. Inside a superset it cycles:
     * the member with the fewest sets done goes next, the earlier exercise first on a tie, so A1, B1, A2, B2.
     * Judged from the sets alone, so it survives the app being killed between two sets, and the trial screen,
     * the notification and the lock-screen "Set done" can never name different sets.
     */
    fun nextSet(sets: List<SessionSet>): SessionSet? {
        val blocks = blocks(sets)
        val first = blocks.indexOfFirst { it.hasPending }
        if (first < 0) return null
        val run = runAt(blocks.map { it.group }, first) ?: first..first
        val pick = run.filter { blocks[it].hasPending }
            .minWith(compareBy({ blocks[it].doneWorking }, { it }))
        return blocks[pick].sets.first { it.isPending }
    }

    /**
     * The partner whose turn follows [exerciseId] in its superset, going round the group (the last member hands
     * back to the first): the exercise id of the next member with a set still to log, or null when [exerciseId] is
     * in no superset or nobody else has a set left. The label "Calf raise next" and the card the trial opens after
     * a tick both read this, so the two can never name different exercises, even when the lifter began the
     * superset out of order and [nextSet] (the first exercise with a set waiting) is somewhere else.
     */
    fun turnAfter(sets: List<SessionSet>, exerciseId: Long): Long? {
        val blocks = blocks(sets)
        val at = blocks.indexOfFirst { it.exerciseId == exerciseId }
        val run = runAt(blocks.map { it.group }, at) ?: return null
        return ((at + 1..run.last) + (run.first until at)).firstOrNull { blocks[it].hasPending }?.let { blocks[it].exerciseId }
    }

    /**
     * Whether ticking [setId] finishes its round: the set is in no superset, or every other member has logged
     * its set for this round (or has no set left to wait for). Only then does a rest start. [sets] are the sets
     * as they stand before the tick.
     */
    fun completesRound(sets: List<SessionSet>, setId: Long): Boolean {
        val set = sets.firstOrNull { it.id == setId } ?: return true
        val blocks = blocks(sets)
        val at = blocks.indexOfFirst { it.exerciseId == set.exerciseId }
        val run = runAt(blocks.map { it.group }, at) ?: return true
        val round = set.workingNumber(sets)
        return run.filter { it != at }.all { blocks[it].doneWorking >= round || !blocks[it].hasPending }
    }

    /**
     * True when [next] is inside a superset and a partner has already logged its set of this round: the rest
     * is held back until the round closes, which the footer says ("NO REST YET").
     */
    fun midRound(sets: List<SessionSet>, next: SessionSet): Boolean {
        val blocks = blocks(sets)
        val at = blocks.indexOfFirst { it.exerciseId == next.exerciseId }
        val run = runAt(blocks.map { it.group }, at) ?: return false
        return run.any { it != at && blocks[it].doneWorking > blocks[at].doneWorking }
    }
}
