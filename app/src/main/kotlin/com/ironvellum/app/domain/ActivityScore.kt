package com.ironvellum.app.domain

import kotlin.math.roundToInt

/**
 * XP for activity (non-lifting) work. Never feeds StrengthIndex: a 5 km run
 * must not inflate the body-scaled strength leaderboard, so these curves are
 * deliberately separate from Xp.award().
 */
object ActivityScore {

    /**
     * Calibration: a hard 45-minute activity lands just under the ~326 XP an
     * ordinary lifting session pays (45 min running = 280, 45 min football = 236).
     */
    private const val XP_PER_MET_MINUTE = 0.75

    /**
     * XP for one completed activity set, proportional to real work via
     * MET-minutes: xp = met x minutes x 0.75 x loadBonus. [Energy.metFor] is the
     * ONLY intensity model — it already speed-bands run/cycle/swim, so a run and
     * a boxing round are both intensity x time and share one expression.
     * - REPS: 0 — lifting XP already exists (Xp.award); double-counting would inflate levels.
     * - DURATION / DISTANCE_TIME: MET-minutes. A DISTANCE_TIME set with a
     *   distance but NO duration pays 0, deliberately: MET-minutes needs
     *   minutes, and fabricating a pace from distance alone would guess. This
     *   mirrors [Energy.activitySet], which returns null rather than guess; the
     *   session screen collects KM and MINUTES side by side, so a lifter sees
     *   the missing field — it is never a silent zero.
     * - ATTEMPTS_GRADE: flat 15 XP per completed attempt, intentionally NOT
     *   MET-scaled. With the MET curves brought down, 10 attempts = 150 XP now
     *   sits sensibly beside a 30-minute run at 187; rescaling it again would
     *   re-inflate climbing relative to everything else. The grade text is
     *   NEVER parsed here — grading systems (V-scale, Font, French) disagree;
     *   grade comparison for titles lives in TitleEngine.
     */
    fun xp(
        exerciseName: String,
        category: String,
        metric: ExerciseMetric,
        durationSec: Int?,
        distanceM: Double?,
        addedKg: Double?,
        bodyweightKg: Double,
    ): Int = when (metric) {
        // Both are strength work, scored by Xp.award against the movement's
        // difficulty. Paying here as well would count the same set twice.
        ExerciseMetric.REPS, ExerciseMetric.HOLD -> 0
        ExerciseMetric.DURATION, ExerciseMetric.DISTANCE_TIME -> {
            val minutes = (durationSec ?: 0) / 60.0
            if (minutes <= 0.0) {
                // Deliberate: distance without time cannot earn MET-minutes.
                0
            } else {
                // Vest/rope weight counts as effort; cap so 200 kg doesn't dominate.
                val loadBonus = 1.0 + ((addedKg ?: 0.0).coerceAtLeast(0.0).coerceAtMost(50.0) / 100.0)
                (Energy.metFor(exerciseName, category, metric, distanceM, durationSec) *
                    minutes * XP_PER_MET_MINUTE * loadBonus).roundToInt()
            }
        }
        // reps carries the completed-attempt count for climbing entries.
        ExerciseMetric.ATTEMPTS_GRADE -> 15 // per-attempt flat rate applied by caller x attempts
    }
}
