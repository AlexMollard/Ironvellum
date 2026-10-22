package com.ironvellum.app.domain

import kotlin.math.abs

/**
 * What the progression engine would change about one block of a trial, offered to the lifter instead of applied.
 * A trial always starts on exactly what its routine writes down; this is the engine's say on top of that, shown on the
 * block with Accept and dismiss. Pure: [Suggestions] builds it from the engine's figures and the routine's, and the
 * block reads [stillOffered] to know whether to keep showing it.
 *
 * [weightKg] and [reps] are the full figures to carry (seconds, for a hold) when the lifter accepts; for a [Kind.CUE]
 * they are the routine's own, because a cue moves nothing and has no Accept.
 */
data class ProgressionSuggestion(
    val kind: Kind,
    val weightKg: Double?,
    val reps: Int,
    val hold: Boolean,
    /** The engine's own wording of why, under the headline; empty when there is none (a top set's). */
    val reason: String,
) {
    enum class Kind {
        /** The load goes up (the reps may reset to the band's floor). */
        MORE_LOAD,

        /** The same load for more reps. */
        MORE_REPS,

        /** The same load for more seconds. */
        LONGER_HOLD,

        /** Repeated stalls: the load comes off. */
        DELOAD,

        /** Advice that moves no figure: a control, mobility or skill cue. Dismiss only. */
        CUE,
    }

    /** Whether accepting changes any figure; a cue has nothing to apply. */
    val movesFigures: Boolean get() = kind != Kind.CUE

    /** Whether accepting moves the load, so the routine's load is worth saving too. */
    val movesLoad: Boolean get() = kind == Kind.MORE_LOAD || kind == Kind.DELOAD

    /** The sets accepting writes to: still to log, and plain working, top or drop sets (a back-off follows its top). */
    fun touches(set: SessionSet): Boolean =
        set.isPending && (set.kind == SetKind.WORKING || set.kind == SetKind.TOP || set.kind == SetKind.DROP)

    /** Whether [set] already carries the figures this offers. A drop's load follows the set before it, so only its reps count. */
    fun carries(set: SessionSet): Boolean {
        val figure = if (hold) set.durationSec ?: 0 else set.reps
        if (figure != reps) return false
        return set.kind == SetKind.DROP || sameLoad(set.weightKg, weightKg)
    }

    /**
     * Whether the block's [sets] still have something to accept: a figure of a pending set that differs, or, for a
     * cue, any set still to log. Gone once it is accepted, once the lifter has typed the same figures, and once the
     * block is done.
     */
    fun stillOffered(sets: List<SessionSet>): Boolean =
        if (movesFigures) sets.any { touches(it) && !carries(it) } else sets.any { it.isPending }

    companion object {
        private const val EPSILON = 0.0005

        fun sameLoad(a: Double?, b: Double?): Boolean = abs((a ?: 0.0) - (b ?: 0.0)) <= EPSILON
    }
}

/** The engine's figures turned into suggestions against what the routine writes. */
object Suggestions {
    private const val EPSILON = 0.0005

    /**
     * The suggestion for a straight lift: the engine's [rec] against the routine's own [writtenKg] x [writtenReps]
     * (seconds, for a [hold]). Null when the engine has nothing new to say, or what it prescribes is not a step on
     * from what is written (the lifter already wrote a heavier load, or already took the deload).
     */
    fun forRecommendation(
        writtenKg: Double?,
        writtenReps: Int,
        rec: Progression.Recommendation,
        hold: Boolean,
    ): ProgressionSuggestion? {
        if (!rec.changed) return null
        if (rec.cueOnly) {
            return ProgressionSuggestion(ProgressionSuggestion.Kind.CUE, writtenKg, writtenReps, hold, rec.reason)
        }
        val kg = rec.weightKg ?: writtenKg
        val delta = (kg ?: 0.0) - (writtenKg ?: 0.0)
        val kind = when {
            rec.deload -> if (delta < -EPSILON) ProgressionSuggestion.Kind.DELOAD else return null
            delta > EPSILON -> ProgressionSuggestion.Kind.MORE_LOAD
            abs(delta) <= EPSILON && rec.reps > writtenReps ->
                if (hold) ProgressionSuggestion.Kind.LONGER_HOLD else ProgressionSuggestion.Kind.MORE_REPS
            else -> return null
        }
        return ProgressionSuggestion(kind, kg, rec.reps, hold, rec.reason)
    }

    /** The suggestion for a top set: where [top] (from the lift's last top set) starts against the written top. */
    fun forTop(writtenKg: Double?, writtenReps: Int, top: TopSuggestion?): ProgressionSuggestion? {
        top ?: return null
        val delta = (top.weightKg ?: 0.0) - (writtenKg ?: 0.0)
        val kind = when {
            delta > EPSILON -> ProgressionSuggestion.Kind.MORE_LOAD
            abs(delta) <= EPSILON && top.reps > writtenReps -> ProgressionSuggestion.Kind.MORE_REPS
            else -> return null
        }
        return ProgressionSuggestion(kind, top.weightKg, top.reps, hold = false, reason = "")
    }
}
