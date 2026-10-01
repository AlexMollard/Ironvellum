package com.ironvellum.app.domain

/**
 * Strength Rank: how strong the Ironbound is now, read off the lift boards.
 *
 * The five words are the strength-standard bands (ExRx and every coaching
 * text use them), and the rank is earned the same way a coach would grade it:
 * estimated 1-rep max against bodyweight. [LiftBoards]' five tiers are Strength
 * Level's Beginner, Novice, Intermediate, Advanced and Elite, so tier N of a
 * board is band N here, with Beginner read as Untrained, the ExRx word for the
 * same band. A logged lift below the Beginner floor (step 0) is Untrained too.
 *
 * Inputs: only the TIERED boards (pull-up, dip, squat, bench press, deadlift,
 * overhead press). The LADDER boards are skill rungs, not bodyweight-ratio
 * standards, so they have no place on this scale.
 *
 * Rule: the best step on each lift within the last [WINDOW_DAYS] days, then
 * the mean step across those lifts, rounded down, then its band. The mean
 * stops one specialty lift carrying the rank, and rounding down never
 * overstates it.
 *
 * The rank can drop. Best-in-window means one bad day or a deload week moves
 * nothing, but a lift left untrained for longer than the window leaves the
 * mean, and a lifter who detrains falls back. All-time best would never fall
 * and so would stop describing current strength.
 *
 * Unranked (null) when no TIERED lift has a mark in the window: no lifts, no
 * bodyweight reading (TIERED marks need one), or only skill work.
 *
 * Level never feeds this. Level follows XP and drives [ArmyClass].
 */
object Rank {

    const val UNRANKED = "Unranked"
    const val UNTRAINED = "Untrained"
    const val NOVICE = "Novice"
    const val INTERMEDIATE = "Intermediate"
    const val ADVANCED = "Advanced"
    const val ELITE = "Elite"

    const val WINDOW_DAYS = 90

    private const val WINDOW_MS = WINDOW_DAYS * 24L * 60 * 60 * 1000

    /** Band for a board step 0..[LiftBoards.MAX_STEP]: two steps per band, step 0 with the first. */
    fun forStep(step: Int): String = when {
        step < 3 -> UNTRAINED
        step < 5 -> NOVICE
        step < 7 -> INTERMEDIATE
        step < 9 -> ADVANCED
        else -> ELITE
    }

    /** Rank from marks already limited to the window; null when no TIERED mark. */
    fun forMarks(marks: List<LiftMark>): String? {
        val steps = marks.filter { it.lift.kind == LiftKind.TIERED }.map { it.step }
        if (steps.isEmpty()) return null
        return forStep(steps.sum() / steps.size)
    }

    /** Current rank from the sealed trials in the [WINDOW_DAYS] days up to [nowMs]; null when unranked. */
    fun current(
        history: List<Pair<WorkoutSession, List<SessionSet>>>,
        bodyweightAt: (Long) -> Double,
        sex: Sex,
        nowMs: Long,
    ): String? {
        val recent = history.filter { (session, _) ->
            val at = session.completedAtMs ?: session.startedAtMs
            at <= nowMs && at > nowMs - WINDOW_MS
        }
        return forMarks(LiftBoards.marks(recent, bodyweightAt, sex, nowMs))
    }
}
