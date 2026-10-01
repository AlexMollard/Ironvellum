package com.ironvellum.app.domain

/**
 * A sealed trial's sets as the amend screen holds them before Save: one
 * block per movement, in trial order, each with its sets in order. Nothing
 * here touches the database; [com.ironvellum.app.data.Repository.editSealedTrial]
 * turns it into rows, renumbering positions and set indices gap-free.
 */
data class TrialDraft(val blocks: List<Block>) {

    data class Block(
        val exerciseId: Long,
        val exerciseName: String,
        /** Describes the movement, so it applies to every set of the block. */
        val modifiers: String,
        val sets: List<DraftSet>,
    )

    /** One set's figures; which of them count depends on the movement's metric. */
    data class DraftSet(
        /** 0 for a hold: its figure is [durationSec]. */
        val reps: Int,
        val weightKg: Double? = null,
        val durationSec: Int? = null,
        val distanceM: Double? = null,
        val grade: String? = null,
        val done: Boolean = true,
    )

    val tickedCount: Int get() = blocks.sumOf { b -> b.sets.count { it.done } }

    /** True when two blocks name one movement and cannot be folded: it cannot be saved. */
    val hasRepeatedMovement: Boolean get() = blocks.map { it.exerciseId }.distinct().size != blocks.size

    fun updateSet(block: Int, set: Int, change: (DraftSet) -> DraftSet): TrialDraft =
        replaceBlock(block) { b -> b.copy(sets = b.sets.mapIndexed { i, s -> if (i == set) change(s) else s }) }

    /** A copy of the block's last set, unticked, so the lifter decides whether it counted. */
    fun addSet(block: Int): TrialDraft = replaceBlock(block) { b ->
        val template = b.sets.lastOrNull() ?: DraftSet(reps = 0)
        b.copy(sets = b.sets + template.copy(done = false))
    }

    /** Removing a block's last set removes the movement. */
    fun removeSet(block: Int, set: Int): TrialDraft {
        val b = blocks.getOrNull(block) ?: return this
        val left = b.sets.filterIndexed { i, _ -> i != set }
        return if (left.isEmpty()) {
            copy(blocks = blocks.filterIndexed { i, _ -> i != block })
        } else {
            copy(blocks = blocks.mapIndexed { i, x -> if (i == block) x.copy(sets = left) else x })
        }
    }

    /**
     * Puts [to] in place of the block's movement. Figures are kept when the
     * metric matches; across metrics each set is reshaped so the new
     * movement's own figure is set (a hold needs seconds, or it earns
     * nothing). Only the modifiers that fit [to] survive.
     */
    fun swapExercise(block: Int, from: Exercise?, to: Exercise): TrialDraft = replaceBlock(block) { b ->
        val fromMetric = from?.metric ?: ExerciseMetric.REPS
        val fits = applicableModifiers(to).toSet()
        val tokens = b.modifiers.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val kept = tokens.filter { it in fits || (it.equals(WEIGHTED, ignoreCase = true) && !to.isWeighted) }
        b.copy(
            exerciseId = to.id,
            exerciseName = to.name,
            modifiers = kept.joinToString(", "),
            sets = if (fromMetric == to.metric) b.sets else b.sets.map { reshape(it, to.metric) },
        )
    }

    private fun replaceBlock(block: Int, change: (Block) -> Block): TrialDraft =
        copy(blocks = blocks.mapIndexed { i, b -> if (i == block) change(b) else b })

    companion object {
        private const val WEIGHTED = "weighted"
        private const val DEFAULT_HOLD_SECONDS = 30
        private const val DEFAULT_REPS = 10

        /** Groups a trial's stored sets into blocks, in trial order. */
        fun of(sets: List<SessionSet>): TrialDraft {
            // Position alone is not an identity: archives written before
            // positions were exported restore every set at 0. A block is one
            // (position, movement) pair; the sort is stable, so equal
            // positions keep their first-seen order.
            val blocks = sets.groupBy { it.exercisePosition to it.exerciseId }
                .entries.sortedBy { it.key.first }
                .map { (_, rows) ->
                    val ordered = rows.sortedBy { it.setIndex }
                    val first = ordered.first()
                    Block(
                        exerciseId = first.exerciseId,
                        exerciseName = first.exerciseName,
                        modifiers = first.modifiers,
                        sets = ordered.map {
                            DraftSet(
                                reps = it.reps,
                                weightKg = it.weightKg,
                                durationSec = it.durationSec,
                                distanceM = it.distanceM,
                                grade = it.grade,
                                done = it.done,
                            )
                        },
                    )
                }
            // A rite may list one movement twice. The cloud keys sets on
            // (movement, index), so such entries fold into one block when they
            // are described alike; [hasRepeatedMovement] flags the rest.
            val merged = mutableListOf<Block>()
            for (b in blocks) {
                val at = merged.indexOfFirst { it.exerciseId == b.exerciseId && it.modifiers == b.modifiers }
                if (at >= 0) merged[at] = merged[at].copy(sets = merged[at].sets + b.sets) else merged += b
            }
            return TrialDraft(merged)
        }

        /** One set carried across a metric change: the new metric's figure is always set. */
        internal fun reshape(set: DraftSet, to: ExerciseMetric): DraftSet = when (to) {
            ExerciseMetric.HOLD -> DraftSet(
                reps = 0,
                weightKg = set.weightKg,
                durationSec = set.durationSec?.takeIf { it > 0 } ?: DEFAULT_HOLD_SECONDS,
                done = set.done,
            )
            ExerciseMetric.REPS -> DraftSet(
                reps = set.reps.takeIf { it > 0 } ?: DEFAULT_REPS,
                weightKg = set.weightKg,
                done = set.done,
            )
            ExerciseMetric.DURATION -> DraftSet(
                reps = set.reps,
                weightKg = set.weightKg,
                durationSec = set.durationSec,
                done = set.done,
            )
            ExerciseMetric.DISTANCE_TIME -> DraftSet(
                reps = set.reps,
                durationSec = set.durationSec,
                distanceM = set.distanceM,
                done = set.done,
            )
            ExerciseMetric.ATTEMPTS_GRADE -> DraftSet(
                reps = set.reps.takeIf { it > 0 } ?: 1,
                grade = set.grade,
                done = set.done,
            )
        }
    }
}
