package com.monarch.app.domain

import kotlin.math.roundToInt

/**
 * XP for activity (non-lifting) work. Never feeds StrengthIndex: a 5 km run
 * must not inflate the body-scaled strength leaderboard, so these curves are
 * deliberately separate from Xp.award().
 */
object ActivityScore {

    /**
     * XP for one completed activity set.
     * - REPS: 0 — lifting XP already exists (Xp.award); double-counting would inflate levels.
     * - DURATION: 1 XP per minute, plus a modest load bonus (+1% per kg of added
     *   load, capped at +50%) so weighted skipping beats unweighted at equal time.
     * - DISTANCE_TIME: 50 XP per km base, plus a pace bonus scaling linearly from
     *   +50 XP/km at 4:00/km down to 0 at 12:00/km — a faster 5 km scores better.
     * - ATTEMPTS_GRADE: 15 XP per completed attempt. The grade text is NEVER
     *   parsed here — grading systems (V-scale, Font, French) disagree; grade
     *   comparison for titles lives in TitleEngine.
     */
    fun xp(
        metric: ExerciseMetric,
        durationSec: Int?,
        distanceM: Double?,
        addedKg: Double?,
        bodyweightKg: Double,
    ): Int = when (metric) {
        // Both are strength work, scored by Xp.award against the movement's
        // difficulty. Paying here as well would count the same set twice.
        ExerciseMetric.REPS, ExerciseMetric.HOLD -> 0
        ExerciseMetric.DURATION -> {
            val minutes = (durationSec ?: 0) / 60.0
            // Vest/rope weight counts as effort; cap so 200 kg doesn't dominate.
            val loadBonus = 1.0 + ((addedKg ?: 0.0).coerceAtLeast(0.0).coerceAtMost(50.0) / 100.0)
            (minutes * loadBonus).roundToInt()
        }
        ExerciseMetric.DISTANCE_TIME -> {
            val km = (distanceM ?: 0.0) / 1000.0
            if (km <= 0.0) {
                0
            } else {
                val pace = paceSecPerKm(distanceM, durationSec)
                // 240 s/km (4:00) or faster earns the full +50; 720 s/km (12:00) earns none.
                val paceBonus = pace?.let { ((720.0 - it).coerceIn(0.0, 480.0) / 480.0 * 50.0) } ?: 0.0
                (km * (50.0 + paceBonus)).roundToInt()
            }
        }
        // reps carries the completed-attempt count for climbing entries.
        ExerciseMetric.ATTEMPTS_GRADE -> 15 // per-attempt flat rate applied by caller x attempts
    }

    /** Pace in seconds per km, or null when distance or duration is missing/zero. */
    fun paceSecPerKm(distanceM: Double?, durationSec: Int?): Double? {
        if (distanceM == null || distanceM <= 0.0) return null
        if (durationSec == null || durationSec <= 0) return null
        return durationSec / (distanceM / 1000.0)
    }
}
