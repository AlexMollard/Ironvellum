package com.ironvellum.app.domain

import com.ironvellum.app.R
import com.ironvellum.app.data.Words

/**
 * The general warm-up offered before a trial's first set: a few minutes of
 * easy cardio, then mobility for what the trial works, and the rite's own
 * warm-up exercises ([Item]) to tick off. It is a reminder, not a set, so it
 * never reaches the log, the set count or XP.
 */
object WarmupStep {

    /**
     * One warm-up exercise of the trial's rite, as the step lists it. Ticked per movement ([exerciseId]) rather than
     * per set; the sets and reps (seconds, minutes, km) are what the rite asked.
     */
    data class Item(
        val exerciseId: Long,
        val name: String,
        val metric: ExerciseMetric,
        val sets: Int,
        val reps: Int,
        val weightKg: Double?,
    ) {
        /** "2 × 30 s", "3 × 10 · 20 kg": the targets the metric uses, as the rite editor's row reads them. */
        fun line(kg: (Double) -> String): String {
            val load = weightKg?.takeIf { it > 0.0 }?.let { " · ${kg(it)}" }.orEmpty()
            return when (metric) {
                ExerciseMetric.REPS -> "$sets × $reps$load"
                ExerciseMetric.HOLD -> Words.text(UiText.res(R.string.exercise_target_hold, sets, reps, load))
                ExerciseMetric.DURATION -> Words.text(UiText.res(R.string.exercise_target_minutes, sets, reps))
                ExerciseMetric.DISTANCE_TIME -> Words.text(UiText.res(R.string.exercise_target_km, sets, weightKg?.let(kg) ?: "?"))
                ExerciseMetric.ATTEMPTS_GRADE -> Words.text(UiText.plural(R.plurals.exercise_target_attempts, reps, sets, reps))
            }
        }
    }


    /** How the lifter answered it for one trial; [OPEN] until they do. */
    enum class State { OPEN, DONE, SKIPPED }

    /** At most this many groups are named; the rest are not "main". */
    private const val MAX_GROUPS = 3

    /**
     * "5 min easy cardio, then mobility for legs and core". The groups are
     * those of the trial's strength movements, most numerous first, ties in
     * the catalogue's order. Cardio and mobility movements are left out (the
     * step is those), and a trial with none of the rest just says "mobility".
     */
    fun text(groups: List<MuscleGroup>): String {
        val worked = groups
            .filter { it != MuscleGroup.CARDIO && it != MuscleGroup.MOBILITY }
            .groupingBy { it }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<MuscleGroup, Int>> { it.value }.thenBy { it.key.ordinal })
            .take(MAX_GROUPS)
            .map { it.key.label() }
        return Words.text(
            if (worked.isEmpty()) UiText.res(R.string.warmup_text_plain)
            else UiText.res(R.string.warmup_text_for, ListText.and(worked)),
        )
    }

    private fun MuscleGroup.label(): String = Words.text(
        UiText.res(
            when (this) {
                MuscleGroup.PULL -> R.string.warmup_group_pull
                MuscleGroup.PUSH -> R.string.warmup_group_push
                MuscleGroup.LEGS -> R.string.warmup_group_legs
                MuscleGroup.CORE -> R.string.warmup_group_core
                MuscleGroup.SPORT -> R.string.warmup_group_sport
                MuscleGroup.CLIMBING -> R.string.warmup_group_climbing
                MuscleGroup.WATER -> R.string.warmup_group_water
                MuscleGroup.CARDIO -> R.string.warmup_group_cardio
                MuscleGroup.MOBILITY -> R.string.warmup_group_mobility
            },
        ),
    )
}
