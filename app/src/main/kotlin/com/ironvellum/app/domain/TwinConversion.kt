package com.ironvellum.app.domain

/**
 * The one-time convert of past sets onto dedicated exercises ([ExerciseTwins]): which blocks of finished workouts
 * were logged as a base exercise with a modifier or load that a dedicated exercise stands for, and what each
 * becomes. Pure: the repository feeds it rows and writes the moves; the confirm screen lists [groups].
 *
 * A block is the sets of one exercise at one place of a workout, which share their modifiers (the trial applies
 * a modifier to the whole block). The block's first set gives the modifiers, and any kilos on it count as
 * "weighted" for a bodyweight exercise ([ExerciseTwins.effectiveModifiers]). A convert is idempotent: what it
 * writes (a dedicated exercise, with its other modifiers) is no base of a twin, so a second look finds nothing.
 */
object TwinConversion {

    /** One set row of a finished workout, as much of it as the rule reads. */
    data class Row(
        val sessionId: Long,
        val position: Int,
        val exerciseId: Long,
        val weightKg: Double?,
        val modifiers: String,
        val warmup: Boolean = false,
    )

    data class BlockRef(val sessionId: Long, val position: Int, val exerciseId: Long)

    /** [block] becomes [toId] ([to]) with [modifiers]; [sets] is how many working sets it holds. */
    data class Move(
        val block: BlockRef,
        val from: String,
        val to: String,
        val toId: Long,
        val modifiers: String,
        val sets: Int,
        /** What a "Keep as is" for this base and these modifiers is remembered under ([ExerciseTwins.Resolution.keepKey]). */
        val keepKey: String = "",
    )

    /** What the confirm screen lists: every block that goes from [from] to [to], summed. */
    data class Group(val from: String, val to: String, val sets: Int, val sessions: Int)

    fun plan(rows: List<Row>, exercises: List<Exercise>): List<Move> {
        val byId = exercises.associateBy { it.id }
        val byName = exercises.associateBy { it.name.trim().lowercase() }
        return rows.groupBy { BlockRef(it.sessionId, it.position, it.exerciseId) }.mapNotNull { (block, sets) ->
            val base = byId[block.exerciseId] ?: return@mapNotNull null
            if (base.category.isNotEmpty()) return@mapNotNull null
            val bodyweight = !base.isWeighted
            val modifiers = ExerciseTwins.effectiveModifiers(sets.first().modifiers, sets.maxOf { it.weightKg ?: 0.0 }, bodyweight)
            val found = ExerciseTwins.resolve(base.name, modifiers, bodyweight) { it.trim().lowercase() in byName } ?: return@mapNotNull null
            val twin = byName.getValue(found.twin.dedicated.lowercase())
            Move(block, base.name, twin.name, twin.id, found.modifiers, sets.count { !it.warmup }, found.keepKey(base.name))
        }.sortedWith(compareBy({ it.block.sessionId }, { it.block.position }))
    }

    fun groups(moves: List<Move>): List<Group> =
        moves.groupBy { it.from to it.to }
            .map { (pair, list) -> Group(pair.first, pair.second, list.sumOf { it.sets }, list.map { it.block.sessionId }.distinct().size) }
            .sortedWith(compareByDescending<Group> { it.sets }.thenBy { it.from }.thenBy { it.to })
}
