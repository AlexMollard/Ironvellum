package com.ironvellum.app.domain

/**
 * The general warm-up offered before a trial's first set: a few minutes of
 * easy cardio, then mobility for what the trial works. It is a reminder, not a
 * set, so it never reaches the log, the set count or XP.
 */
object WarmupStep {

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
        val mobility = if (worked.isEmpty()) "mobility" else "mobility for ${listAnd(worked)}"
        return "5 min easy cardio, then $mobility"
    }

    private fun MuscleGroup.label(): String = when (this) {
        MuscleGroup.PULL -> "pull"
        MuscleGroup.PUSH -> "push"
        MuscleGroup.LEGS -> "legs"
        MuscleGroup.CORE -> "core"
        MuscleGroup.SPORT -> "sport"
        MuscleGroup.CLIMBING -> "climbing"
        MuscleGroup.WATER -> "water work"
        MuscleGroup.CARDIO -> "cardio"
        MuscleGroup.MOBILITY -> "mobility"
    }

    private fun listAnd(items: List<String>): String = when (items.size) {
        1 -> items[0]
        2 -> "${items[0]} and ${items[1]}"
        else -> items.dropLast(1).joinToString(", ") + " and " + items.last()
    }
}
