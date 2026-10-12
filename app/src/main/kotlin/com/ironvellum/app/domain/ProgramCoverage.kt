package com.ironvellum.app.domain

/**
 * The effort-first coverage model and the result types the ranked suggestions,
 * per-change Temper, easing and day-spacing passes share. Pure data and pure
 * arithmetic: the thresholds live in [ProgramRules].
 */

/** Where a muscle stands against the weekly minimum: none, some but short, or 4+ hard sets. */
enum class Coverage { NOT_TRAINED, BELOW_MINIMUM, COVERED }

/**
 * One muscle's load: [weekly] fractional hard sets across the week and
 * [maxSession] in its busiest session. Heavy is a soft flag, never a refusal.
 */
data class MuscleLoad(val muscle: Muscle, val weekly: Double, val maxSession: Double) {
    val coverage: Coverage
        get() = when {
            weekly <= EPS -> Coverage.NOT_TRAINED
            weekly < ProgramRules.COVERED_MIN - EPS -> Coverage.BELOW_MINIMUM
            else -> Coverage.COVERED
        }

    val heavy: Boolean
        get() = weekly > ProgramRules.HEAVY_WEEK + EPS || maxSession > ProgramRules.HEAVY_SESSION + EPS

    private companion object {
        /** Sums of 0.3-style shares drift; an exact 4.0 must still read covered. */
        const val EPS = 1e-9
    }
}

/**
 * A set as logged, the raw material of [HardSets.fromLog]. [bodyweight] is
 * true when the movement carries no external load by design (a push-up), so a
 * set without weight still counts; the repository fills it from the catalogue.
 */
data class LoggedSet(
    val exerciseName: String,
    val modifiers: String,
    val weightKg: Double?,
    val reps: Int,
    val rpe: Int?,
    val kind: SetKind,
    val done: Boolean,
    val atMs: Long,
    val bodyweight: Boolean = false,
)

/** What counts as a hard set, planned or logged. */
object HardSets {

    /** Hard sets within this many days of "now" count towards the last-7-days view. */
    private const val WINDOW_MS = 7L * 24 * 3600 * 1000

    /** Lowest RPE that still counts as hard: 7 leaves about 3 reps in reserve. */
    private const val MIN_HARD_RPE = 7

    /** Past this many reps an unrated set is treated as endurance work, not a hard set. */
    private const val MAX_UNRATED_HARD_REPS = 15

    /** Every planned set is a hard set: fractional sets per muscle (sets x MuscleMap share). */
    fun planned(presets: List<PlannedPreset>): Map<Muscle, Double> = ProgramRules.weeklyVolume(presets)

    /**
     * Whether one logged set counts. Undone sets and warm-ups never do;
     * failure and rest-pause sets always do; a rated set counts from RPE 7;
     * an unrated one (the owner's call: unrated counts) when it is a loaded
     * or bodyweight set of 15 reps or fewer. A drop set follows the rating
     * when there is one and counts when there is none.
     */
    fun isHard(
        kind: SetKind,
        rpe: Int?,
        done: Boolean,
        reps: Int,
        weightKg: Double?,
        bodyweight: Boolean,
    ): Boolean {
        if (!done || !kind.countsAsWork) return false
        if (kind == SetKind.FAILURE || kind == SetKind.REST_PAUSE) return true
        if (rpe != null) return rpe >= MIN_HARD_RPE
        if (kind == SetKind.DROP) return true
        return reps in 1..MAX_UNRATED_HARD_REPS && ((weightKg ?: 0.0) > 0.0 || bodyweight)
    }

    /**
     * Fractional hard sets per muscle in the seven days up to [nowMs]: a
     * set at exactly seven days is out, one in the future is ignored. Sets
     * of movements without a muscle profile contribute nothing.
     */
    fun fromLog(sets: List<LoggedSet>, nowMs: Long): Map<Muscle, Double> {
        val credit = mutableMapOf<Muscle, Double>()
        for (set in sets) {
            if (set.atMs > nowMs || set.atMs <= nowMs - WINDOW_MS) continue
            if (!isHard(set.kind, set.rpe, set.done, set.reps, set.weightKg, set.bodyweight)) continue
            val profile = MuscleMap.profile(set.exerciseName, set.modifiers) ?: continue
            for ((muscle, share) in profile.muscles) {
                if (share > 0.0) credit.merge(muscle, share, Double::plus)
            }
        }
        return credit
    }
}

/** How a prescribed set is taken: 0-2 reps in reserve (compounds) or to failure (isolation). */
enum class EffortCue { RIR_0_2, TO_FAILURE }

/** Why a movement, change or day is suggested; the UI owns the wording. */
enum class Reason {
    DIRECT_MUSCLE,
    FILLS_SHORT_MUSCLE,
    FITS_GEAR,
    YOU_LOG_THIS,
    LOW_OVERLAP,
    SAME_PATTERN_AS_MAIN,
    STRETCH_BIASED,
    COMPOUND_FIRST,
    RECOVERY_SPACED,
    FITS_DAY_LENGTH,
    STAND_IN_FOR,
    USED_RECENTLY,
    OFTEN_SKIPPED,
}

/** One reason with the details its sentence needs: a muscle, another movement, a count. */
data class ReasonTag(
    val reason: Reason,
    val muscle: Muscle? = null,
    val other: String? = null,
    val n: Int? = null,
)

/** A ranked movement: its [tags] (at most two), [score] for ordering, and the coverage it would add. */
data class Candidate(
    val exercise: Exercise,
    val tags: List<ReasonTag>,
    val score: Double,
    val coverageDelta: Map<Muscle, Double> = emptyMap(),
)

/** Up to three options, best first; empty when nothing fits. */
data class Ranked(val options: List<Candidate>)

/** One edit to a preset, as pure data; the order they apply in is the Temper pass's business. */
sealed class ChangeOp {
    data class AddEntry(val entry: PlannedEntry) : ChangeOp()
    data class RemoveEntry(val exerciseName: String) : ChangeOp()
    data class SwapEntry(val fromName: String, val to: PlannedEntry) : ChangeOp()
    data class SetSets(val exerciseName: String, val sets: Int) : ChangeOp()
    data class SetLoad(val exerciseName: String, val weightKg: Double?) : ChangeOp()
    data class SetReps(val exerciseName: String, val reps: Int) : ChangeOp()
    data class MoveEntryToDay(val exerciseName: String, val toDay: Int) : ChangeOp()
}
