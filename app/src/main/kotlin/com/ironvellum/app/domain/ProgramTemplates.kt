package com.ironvellum.app.domain

/**
 * Six hand-authored programs - {beginner, intermediate, advanced} x
 * {strength, muscle} - written for a full gym and adapted on [build] to
 * HOME_WEIGHTS and BODYWEIGHT by substituting each disallowed movement with
 * the allowed movement of the closest [MuscleMap] profile and the same
 * pattern (a strength main lift becomes the closest pattern equivalent, and
 * its `why` says so). The weekly fractional volume of each MUSCLE template
 * sits inside its tier's target range for every tracked muscle
 * ([ProgramRules.weeklySetTarget], Pelland 2026 / Schoenfeld 2017), and every
 * STRENGTH template practises the squat and the bench at least twice a week
 * and the deadlift at least once (Grgic 2018).
 *
 * Beginner blocks run a small pool with a linear-progression note (ACSM 2009,
 * Moesgaard 2022: novices progress on load, not on variety). Advanced
 * strength templates vary intensity across the week - heavy and light days -
 * because periodised intensity beats constant training for strength and
 * changes nothing for hypertrophy (Williams 2017, Moesgaard 2022).
 *
 * Loads are filled on [build] from the lifter's own e1RMs
 * ([ProgramGenerator.fillLoad]); nothing is invented, so a new lifter gets
 * unfilled loads to fill in on the first session.
 */
object ProgramTemplates {

    private fun entry(name: String, sets: Int, reps: Int) = PlannedEntry(
        exerciseName = name, sets = sets, reps = reps, targetWeightKg = null,
    )

    private fun day(name: String, scheduledDay: Int, entries: List<PlannedEntry>) =
        PlannedPreset(name = name, note = "", scheduledDay = scheduledDay, entries = entries)

    val ALL: List<ProgramTemplate> = listOf(
        ProgramTemplate(
            id = "beginner_strength",
            tier = ExperienceTier.BEGINNER,
            focus = TrainingFocus.STRENGTH,
            name = "First Barbell",
            summary = "Three full-body days on five lifts. Learn the lifts, " +
                "add a little weight every session - linear progression is " +
                "all a first year needs (ACSM 2009).",
            days = listOf(
                day("First Barbell A", 1, listOf(
                    entry("Back Squat", 3, 5),
                    entry("Bench Press", 3, 5),
                    entry("Barbell Row", 3, 5),
                )),
                day("First Barbell B", 3, listOf(
                    entry("Deadlift", 3, 5),
                    entry("Overhead Press", 3, 5),
                    entry("Lat Pulldown", 3, 8),
                )),
                day("First Barbell C", 5, listOf(
                    entry("Back Squat", 3, 5),
                    entry("Bench Press", 3, 5),
                    entry("Romanian Deadlift", 3, 5),
                )),
            ),
        ),
        ProgramTemplate(
            id = "beginner_muscle",
            tier = ExperienceTier.BEGINNER,
            focus = TrainingFocus.MUSCLE,
            name = "First Engine",
            summary = "Three full-body days, 8-12 hard sets per muscle a " +
                "week - the low end of the dose-response curve is all a " +
                "novelty-driven first year needs (Pelland 2026; ACSM 2009). " +
                "Add load or reps every week.",
            days = listOf(
                day("First Engine A", 1, listOf(
                    entry("Back Squat", 3, 8),
                    entry("Bench Press", 3, 8),
                    entry("Lat Pulldown", 3, 10),
                    entry("Romanian Deadlift", 3, 8),
                    entry("Seated Cable Row", 3, 10),
                    entry("Standing Calf Raise", 3, 12),
                    entry("Hanging Leg Raise", 3, 10),
                    entry("Lateral Raise", 3, 12),
                )),
                day("First Engine B", 3, listOf(
                    entry("Bench Press", 3, 8),
                    entry("Barbell Row", 3, 10),
                    entry("Goblet Squat", 2, 12),
                    entry("Seated Leg Curl", 3, 12),
                    entry("Overhead Press", 3, 8),
                    entry("Reverse Fly", 3, 12),
                    entry("Overhead Cable Extension", 3, 12),
                    entry("Standing Calf Raise", 3, 12),
                    entry("Hanging Knee Raise", 3, 10),
                    entry("Lateral Raise", 3, 12),
                )),
                day("First Engine C", 5, listOf(
                    entry("Back Squat", 3, 8),
                    entry("Dumbbell Fly", 3, 12),
                    entry("Lat Pulldown", 3, 10),
                    entry("Seated Leg Curl", 3, 12),
                    entry("Standing Calf Raise", 3, 12),
                    entry("Hanging Leg Raise", 3, 10),
                    entry("Lateral Raise", 2, 12),
                    entry("Hammer Curl", 3, 12),
                )),
            ),
        ),
        ProgramTemplate(
            id = "intermediate_strength",
            tier = ExperienceTier.INTERMEDIATE,
            focus = TrainingFocus.STRENGTH,
            name = "Four-Day Strength",
            summary = "Upper/lower twice a week on the big lifts. Squat and " +
                "bench are practiced twice a week - strength tracks practice " +
                "frequency (Grgic 2018; Pelland 2026).",
            days = listOf(
                day("Upper Heavy", 1, listOf(
                    entry("Bench Press", 5, 3),
                    entry("Overhead Press", 3, 5),
                    entry("Barbell Row", 3, 5),
                )),
                day("Lower Heavy", 2, listOf(
                    entry("Back Squat", 5, 3),
                    entry("Deadlift", 3, 3),
                    entry("Hanging Leg Raise", 3, 10),
                )),
                day("Upper Light", 4, listOf(
                    entry("Bench Press", 4, 5),
                    entry("Lat Pulldown", 3, 8),
                    entry("Seated Cable Row", 3, 8),
                )),
                day("Lower Light", 5, listOf(
                    entry("Back Squat", 3, 5),
                    entry("Romanian Deadlift", 3, 5),
                    entry("Hanging Knee Raise", 3, 10),
                )),
            ),
        ),
        ProgramTemplate(
            id = "intermediate_muscle",
            tier = ExperienceTier.INTERMEDIATE,
            focus = TrainingFocus.MUSCLE,
            name = "Four-Day Size",
            summary = "Upper/lower twice a week, 12-18 fractional sets per " +
                "muscle, every session led by a compound and finished with " +
                "stretch-biased accessories (Pelland 2026; Maeo 2021).",
            days = listOf(
                day("Upper A", 1, listOf(
                    entry("Bench Press", 4, 8),
                    entry("Barbell Row", 4, 8),
                    entry("Overhead Press", 3, 8),
                    entry("Lat Pulldown", 4, 10),
                    entry("Incline Dumbbell Press", 3, 10),
                    entry("Seated Cable Row", 3, 10),
                    entry("Lateral Raise", 4, 12),
                    entry("Triceps Pushdown", 3, 12),
                    entry("Ab Wheel Rollout", 3, 10),
                )),
                day("Lower A", 2, listOf(
                    entry("Back Squat", 4, 8),
                    entry("Romanian Deadlift", 3, 8),
                    entry("Leg Press", 4, 10),
                    entry("Seated Leg Curl", 3, 12),
                    entry("Standing Calf Raise", 6, 12),
                    entry("Hanging Leg Raise", 3, 10),
                )),
                day("Upper B", 4, listOf(
                    entry("Overhead Press", 4, 8),
                    entry("Pull-up", 3, 8),
                    entry("Dumbbell Bench Press", 3, 10),
                    entry("Pendlay Row", 3, 8),
                    entry("Cable Fly", 3, 12),
                    entry("Pec Deck", 3, 12),
                    entry("Lateral Raise", 4, 12),
                    entry("Face Pull", 4, 12),
                    entry("Overhead Cable Extension", 3, 12),
                    entry("Cable Curl", 4, 12),
                    entry("Ab Wheel Rollout", 3, 10),
                )),
                day("Lower B", 5, listOf(
                    entry("Deadlift", 3, 5),
                    entry("Hack Squat", 4, 10),
                    entry("Hip Thrust", 2, 10),
                    entry("Seated Leg Curl", 3, 12),
                    entry("Standing Calf Raise", 6, 12),
                    entry("Hanging Knee Raise", 3, 10),
                )),
            ),
        ),
        ProgramTemplate(
            id = "advanced_strength",
            tier = ExperienceTier.ADVANCED,
            focus = TrainingFocus.STRENGTH,
            name = "Five-Day Strength",
            summary = "Heavy and light days across the week - periodised " +
                "intensity buys a small real strength edge over constant " +
                "training (Williams 2017; Moesgaard 2022). Squat and bench " +
                "twice, deadlift once, everything else serves the lifts.",
            days = listOf(
                day("Lower Heavy", 1, listOf(
                    entry("Back Squat", 5, 3),
                    entry("Deadlift", 3, 3),
                    entry("Barbell Row", 3, 5),
                )),
                day("Upper Heavy", 2, listOf(
                    entry("Bench Press", 5, 3),
                    entry("Overhead Press", 5, 3),
                    entry("Pull-up", 3, 5),
                )),
                day("Upper Light", 4, listOf(
                    entry("Bench Press", 4, 5),
                    entry("Lat Pulldown", 3, 8),
                    entry("Dumbbell Row", 3, 8),
                )),
                day("Lower Light", 5, listOf(
                    entry("Back Squat", 3, 5),
                    entry("Romanian Deadlift", 3, 5),
                    entry("Hanging Leg Raise", 3, 10),
                )),
                day("Upper Pump", 6, listOf(
                    entry("Overhead Press", 3, 8),
                    entry("Seated Cable Row", 3, 10),
                    entry("Face Pull", 3, 12),
                )),
            ),
        ),
        ProgramTemplate(
            id = "advanced_muscle",
            tier = ExperienceTier.ADVANCED,
            focus = TrainingFocus.MUSCLE,
            name = "Six-Day Size",
            summary = "Push/pull/legs twice over, 15-22 fractional sets per " +
                "muscle at the recovery-capped top of the curve (Pelland " +
                "2026). Every hamstring and calf slot loads the stretch - " +
                "long muscle lengths win (Maeo 2021; Kassiano 2023).",
            days = listOf(
                day("Push A", 1, listOf(
                    entry("Bench Press", 4, 8),
                    entry("Overhead Press", 3, 8),
                    entry("Incline Dumbbell Press", 3, 10),
                    entry("Lateral Raise", 5, 12),
                    entry("Triceps Pushdown", 3, 12),
                    entry("Overhead Cable Extension", 3, 12),
                    entry("Woodchop", 3, 12),
                    entry("Ab Wheel Rollout", 3, 10),
                )),
                day("Pull A", 2, listOf(
                    entry("Deadlift", 3, 5),
                    entry("Barbell Row", 4, 8),
                    entry("Lat Pulldown", 4, 10),
                    entry("Face Pull", 4, 12),
                    entry("Cable Curl", 4, 12),
                )),
                day("Legs A", 4, listOf(
                    entry("Back Squat", 4, 8),
                    entry("Romanian Deadlift", 3, 8),
                    entry("Leg Press", 5, 10),
                    entry("Seated Leg Curl", 4, 12),
                    entry("Standing Calf Raise", 5, 12),
                    entry("Seated Calf Raise", 6, 15),
                    entry("Hanging Leg Raise", 4, 10),
                )),
                day("Push B", 5, listOf(
                    entry("Overhead Press", 4, 8),
                    entry("Dumbbell Bench Press", 3, 10),
                    entry("Cable Fly", 3, 12),
                    entry("Pec Deck", 3, 12),
                    entry("Lateral Raise", 5, 12),
                    entry("Overhead Cable Extension", 3, 12),
                )),
                day("Pull B", 6, listOf(
                    entry("Pull-up", 4, 8),
                    entry("Pendlay Row", 3, 8),
                    entry("Seated Cable Row", 3, 10),
                    entry("Reverse Fly", 4, 12),
                    entry("Dumbbell Pullover", 3, 12),
                    entry("Cable Curl", 3, 12),
                    entry("Ab Wheel Rollout", 3, 10),
                )),
                day("Legs B", 7, listOf(
                    entry("Front Squat", 3, 8),
                    entry("Hip Thrust", 3, 8),
                    entry("Hack Squat", 3, 10),
                    entry("Seated Leg Curl", 4, 12),
                    entry("Standing Calf Raise", 5, 12),
                    entry("Seated Calf Raise", 6, 15),
                    entry("Hanging Knee Raise", 3, 10),
                )),
            ),
        ),
    )

    fun byId(id: String): ProgramTemplate? = ALL.firstOrNull { it.id == id }

    // ------------------------------------------------------------------ build

    /**
     * Adapts a template to the lifter's equipment and fills loads from the
     * strength profile. A movement the equipment cannot express becomes the
     * allowed movement with the closest MuscleMap profile and the same
     * pattern - substitution is legitimate for hypertrophy (Kikuchi 2017,
     * Calatayud 2015, Plotkin 2023) and the entry's `why` says when it
     * happened. A day left with no entries is dropped honestly.
     */
    fun build(
        template: ProgramTemplate,
        equipment: EquipmentAccess,
        catalogue: List<Exercise>,
        strength: StrengthProfile,
    ): RoutinePlan {
        val pool = ProgramGenerator.eligible(catalogue, equipment, template.focus)
        val rir = ProgramRules.targetRir(template.tier, template.focus)
        val presets = template.days.mapNotNull { day ->
            // Two gym movements must not collapse onto one substitute in the
            // same day (bench and incline press both becoming "Push-up").
            val used = mutableSetOf<String>()
            val entries = day.entries.mapNotNull { entry ->
                val profile = MuscleMap.profile(entry.exerciseName)
                var exercise = pool.firstOrNull { it.name.equals(entry.exerciseName, ignoreCase = true) }
                var substituted = false
                if (exercise == null) {
                    if (profile == null) return@mapNotNull entry.copy(targetWeightKg = null)
                    exercise = closestSubstitute(pool.filter { it.name !in used }, profile, entry.exerciseName)
                        ?: return@mapNotNull null
                    substituted = true
                }
                used += exercise.name
                val load = ProgramGenerator.fillLoad(exercise, strength, entry.reps, rir)
                val isMainLift = exercise.name.trim().lowercase() in
                    setOf("back squat", "bench press", "deadlift", "overhead press")
                val why = if (substituted) {
                    "Your equipment has no ${entry.exerciseName}, so the ${exercise.name} takes " +
                        "over: closest match in movement pattern and muscles - Kikuchi 2017; Calatayud 2015"
                } else if (template.focus == TrainingFocus.STRENGTH && isMainLift) {
                    "Practises the ${exercise.name} itself: strength is specific to the lift you " +
                        "train, moved by heavy loads - Buckner 2017; TaskSpec 2025; Lopez 2021"
                } else if (template.focus == TrainingFocus.MUSCLE &&
                    MuscleMap.profile(exercise.name)?.stretchBias == true
                ) {
                    val primary = MuscleMap.profile(exercise.name)!!.muscles
                        .filterValues { it >= 0.5 }.maxWithOrNull(compareBy { it.value })!!.key
                    "In this program for your ${ProgramGenerator.muscleListOf(exercise, primary)}, " +
                        "trained at long muscle length where they grow best - " +
                        ProgramGenerator.longLengthEvidence(primary)
                } else {
                    "In this program for your ${ProgramGenerator.muscleListOf(exercise)} - Pelland 2026"
                }
                entry.copy(
                    exerciseName = exercise.name,
                    targetWeightKg = load?.first,
                    loadNote = load?.second,
                    why = why,
                )
            }
            if (entries.isEmpty()) return@mapNotNull null
            // Same rest/RIR guidance the generator writes, matched to the
            // template's goal and tier; the summary stays on the template.
            day.copy(entries = entries, note = ProgramGenerator.presetNote(template.tier, template.focus))
        }
        return RoutinePlan(presets)
    }

    /**
     * Same pattern, most similar muscle profile (cosine over the shared
     * contribution vector), then compound, equipment fit, tier, name - all
     * deterministic.
     */
    private fun closestSubstitute(
        pool: List<Exercise>,
        target: ExerciseProfile,
        originalName: String,
    ): Exercise? = pool
        .filter { (MuscleMap.profile(it.name)?.pattern) == target.pattern }
        .filter { !it.name.equals(originalName, ignoreCase = true) }
        .maxWithOrNull(
            compareBy(
                { similarity(target, MuscleMap.profile(it.name)!!) },
                { if (MuscleMap.profile(it.name)!!.compound) 1 else 0 },
                { -MovementDifficulty.tier(it.name) },
                { it.name },
            ),
        )

    private fun similarity(a: ExerciseProfile, b: ExerciseProfile): Double {
        val keys = a.muscles.keys + b.muscles.keys
        var dot = 0.0
        var na = 0.0
        var nb = 0.0
        for (k in keys) {
            val x = a.muscles[k] ?: 0.0
            val y = b.muscles[k] ?: 0.0
            dot += x * y
            na += x * x
            nb += y * y
        }
        if (na == 0.0 || nb == 0.0) return 0.0
        return dot / kotlin.math.sqrt(na * nb)
    }

}
