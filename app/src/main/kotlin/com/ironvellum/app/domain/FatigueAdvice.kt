package com.ironvellum.app.domain

/**
 * What fatigue says at the moments a decision is made (see docs/superpowers/specs/2026-10-27-fatigue-integration-design.md).
 * Everything here only reads a [FatigueReport]; nothing changes a routine, and no fatigue maths lives here.
 *
 * An exercise's main muscles are those with a share of at least [FatigueTuning.TRAINS_SHARE] in [MuscleMap.profile]
 * with its modifiers (the rule [PlanFatigue.dayNotes] uses). The trigger is High or above; Moderate never triggers.
 */
object FatigueAdvice {

    /** The level that triggers every suggestion. */
    val TRIGGER = FatigueLevel.HIGH

    fun triggers(level: FatigueLevel): Boolean = level >= TRIGGER

    /** The muscles [name] with [modifiers] mainly works; empty for a movement the map does not know. */
    fun mainMuscles(name: String, modifiers: String = ""): Set<Muscle> =
        MuscleMap.profile(name, modifiers)?.muscles.orEmpty().filterValues { it >= FatigueTuning.TRAINS_SHARE }.keys

    /** The worst (highest residual) of [muscles] at [atMs], with its level; null when there are none. */
    fun worst(muscles: Collection<Muscle>, report: FatigueReport, atMs: Long): Pair<Muscle, FatigueLevel>? =
        muscles.maxWithOrNull(compareBy<Muscle> { report.residual(it, atMs) }.thenByDescending { it.ordinal })
            ?.let { it to report.levelAt(it, atMs) }

    /**
     * The worst level among the main muscles of [name] in [levels] (the picker's map: every muscle's level right now), with
     * the muscle that holds it; null when the movement is unknown or [levels] is empty. Ties go to the earlier muscle.
     */
    fun worstIn(name: String, levels: Map<Muscle, FatigueLevel>): Pair<Muscle, FatigueLevel>? =
        mainMuscles(name).mapNotNull { muscle -> levels[muscle]?.let { muscle to it } }
            .maxWithOrNull(compareBy<Pair<Muscle, FatigueLevel>> { it.second }.thenByDescending { it.first.ordinal })

    /** A working exercise of a routine that leans on [muscle], its worst main muscle, which is at [level] (High or above). */
    data class Lean(val entry: PresetEntry, val muscle: Muscle, val level: FatigueLevel)

    /** The working exercises of [entries] with a main muscle at High or above at [atMs], in routine order. */
    fun leaning(entries: List<PresetEntry>, report: FatigueReport, atMs: Long): List<Lean> =
        entries.filter { !it.warmup }.sortedBy { it.position }.mapNotNull { entry ->
            val (muscle, level) = worst(mainMuscles(entry.exerciseName, entry.modifiers), report, atMs) ?: return@mapNotNull null
            if (triggers(level)) Lean(entry, muscle, level) else null
        }

    /** What Today says about a day's routine. */
    sealed interface Today {
        /** Nothing: the setting is off, or no exercise leans on a tired muscle. */
        data object None : Today

        /** Item 1: [worst] is the most tired muscle any exercise leans on, with [leans] the exercises that do. */
        data class Adjust(val worst: Muscle, val level: FatigueLevel, val leans: List<Lean>) : Today

        /**
         * Item 4: at least half the routine's main muscles are High or above; up to three of them, worst first. [leans]
         * are the exercises that lean on a tired muscle, which the card still marks.
         */
        data class Rest(val muscles: List<Pair<Muscle, FatigueLevel>>, val leans: List<Lean>) : Today
    }

    private const val PANEL_MUSCLES = 3

    /**
     * The advice for a routine's [entries] at [atMs]. [restOffered] is true only on a BEGIN day: there the rest panel
     * replaces the adjust line when at least half of the routine's distinct main muscles are High or above (exactly
     * half counts). [enabled] is the "Fatigue advice" setting; off means [Today.None] whatever the report says.
     */
    fun today(entries: List<PresetEntry>, report: FatigueReport, atMs: Long, restOffered: Boolean, enabled: Boolean): Today {
        if (!enabled) return Today.None
        val leans = leaning(entries, report, atMs)
        if (leans.isEmpty()) return Today.None
        if (restOffered) {
            val main = entries.filter { !it.warmup }.flatMap { mainMuscles(it.exerciseName, it.modifiers) }.toSet()
            val high = main.filter { triggers(report.levelAt(it, atMs)) }
            if (main.isNotEmpty() && high.size * 2 >= main.size) {
                val worst = high.sortedWith(compareByDescending<Muscle> { report.residual(it, atMs) }.thenBy { it.ordinal })
                    .take(PANEL_MUSCLES).map { it to report.levelAt(it, atMs) }
                return Today.Rest(worst, leans)
            }
        }
        val (muscle, level) = worst(leans.map { it.muscle }, report, atMs)!!
        return Today.Adjust(muscle, level, leans)
    }

    /**
     * The routine to offer instead of [current]: another one not done this week ([doneThisWeek], routine ids) whose
     * main muscles are all Moderate or below now; the freshest (lowest worst residual) when several qualify, then the
     * earlier scheduled day, then the name. Null when none does. A routine with no known main muscle never qualifies.
     */
    fun freshestOther(
        presets: List<WorkoutPreset>,
        current: Long,
        doneThisWeek: Set<Long>,
        report: FatigueReport,
        atMs: Long,
    ): WorkoutPreset? = presets
        .filter { it.id != current && it.id !in doneThisWeek }
        .mapNotNull { preset ->
            val main = preset.working.flatMap { mainMuscles(it.exerciseName, it.modifiers) }.toSet()
            if (main.isEmpty() || main.any { triggers(report.levelAt(it, atMs)) }) null
            else preset to main.maxOf { report.residual(it, atMs) }
        }
        .minWithOrNull(compareBy<Pair<WorkoutPreset, Double>> { it.second }.thenBy { it.first.scheduledDay ?: 8 }.thenBy { it.first.name })
        ?.first
}
