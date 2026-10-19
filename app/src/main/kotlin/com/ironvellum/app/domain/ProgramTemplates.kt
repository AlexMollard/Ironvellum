package com.ironvellum.app.domain

import com.ironvellum.app.R
import com.ironvellum.app.data.Words

/**
 * Six hand-authored programs, keyed by split and goal - full body, upper/
 * lower and push/pull/legs for muscle; full body, upper/lower and a
 * heavy/light week for strength - written for a full gym and adapted on
 * [build] to any gear set ([Equipment], via [GearRequirements]) by substituting each disallowed
 * movement with the allowed movement of the closest [MuscleMap] profile and
 * the same pattern (a strength main lift becomes the closest pattern
 * equivalent, and its `why` says so). At its authored volume the weekly
 * fractional volume of each MUSCLE template covers every tracked muscle with
 * at least [ProgramRules.COVERED_MIN] hard sets and flags none Heavy
 * (Pelland 2026 / Schoenfeld 2017); [build] scales its sets into the
 * lifter's level ([ProgramRules.setRange]). Every
 * STRENGTH template practises the squat and the bench at least twice a week
 * and the deadlift at least once (Grgic 2018).
 *
 * The full-body strength block runs a small pool with a linear-progression
 * note (ACSM 2009, Moesgaard 2022: novices progress on load, not on
 * variety). The heavy/light week varies intensity across the week because
 * periodised intensity beats constant training for strength and changes
 * nothing for hypertrophy (Williams 2017, Moesgaard 2022).
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

    val ALL: List<ProgramTemplate> by Words.perMode { listOf(
        ProgramTemplate(
            id = "full_body_strength",
            split = TrainingSplit.FULL_BODY,
            authoredVolume = VolumeLevel.LOW,
            focus = TrainingFocus.STRENGTH,
            name = "Full-Body Barbell",
            summary = Words.text(UiText.themed(R.string.program_extra_summary_full_body_strength, R.string.program_extra_summary_full_body_strength__t)),
            days = listOf(
                day("Full Body A", 1, listOf(
                    entry("Back Squat", 3, 5),
                    entry("Bench Press", 3, 5),
                    entry("Barbell Row", 3, 5),
                )),
                day("Full Body B", 3, listOf(
                    entry("Deadlift", 3, 5),
                    entry("Overhead Press", 3, 5),
                    entry("Lat Pulldown", 3, 8),
                )),
                day("Full Body C", 5, listOf(
                    entry("Back Squat", 3, 5),
                    entry("Bench Press", 3, 5),
                    entry("Romanian Deadlift", 3, 5),
                )),
            ),
        ),
        ProgramTemplate(
            id = "full_body_muscle",
            split = TrainingSplit.FULL_BODY,
            authoredVolume = VolumeLevel.LOW,
            focus = TrainingFocus.MUSCLE,
            name = "Full-Body Size",
            summary = Words.text(UiText.themed(R.string.program_extra_summary_full_body_size, R.string.program_extra_summary_full_body_size__t)),
            days = listOf(
                day("Full Body A", 1, listOf(
                    entry("Back Squat", 3, 8),
                    entry("Bench Press", 3, 8),
                    entry("Lat Pulldown", 3, 10),
                    entry("Romanian Deadlift", 3, 8),
                    entry("Seated Cable Row", 3, 10),
                    entry("Standing Calf Raise", 3, 12),
                    entry("Hanging Leg Raise", 3, 10),
                    entry("Lateral Raise", 3, 12),
                )),
                day("Full Body B", 3, listOf(
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
                day("Full Body C", 5, listOf(
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
            id = "upper_lower_strength",
            split = TrainingSplit.UPPER_LOWER,
            authoredVolume = VolumeLevel.STANDARD,
            focus = TrainingFocus.STRENGTH,
            name = "Upper/Lower Strength",
            summary = "Upper/lower twice a week. Squat and bench twice a week — " +
                "strength follows practice (Grgic 2018; Pelland 2026).",
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
            id = "upper_lower_muscle",
            split = TrainingSplit.UPPER_LOWER,
            authoredVolume = VolumeLevel.STANDARD,
            focus = TrainingFocus.MUSCLE,
            name = "Upper/Lower Size",
            summary = Words.text(UiText.themed(R.string.program_extra_summary_upper_lower_size, R.string.program_extra_summary_upper_lower_size__t)),
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
                )),
                day("Lower A", 2, listOf(
                    entry("Back Squat", 4, 8),
                    entry("Romanian Deadlift", 3, 8),
                    entry("Leg Press", 4, 10),
                    entry("Seated Leg Curl", 3, 12),
                    entry("Standing Calf Raise", 4, 12),
                    entry("Seated Calf Raise", 4, 15),
                    entry("Hanging Leg Raise", 3, 10),
                    entry("Ab Wheel Rollout", 3, 10),
                )),
                day("Upper B", 4, listOf(
                    entry("Overhead Press", 4, 8),
                    entry("Pull-up", 3, 8),
                    entry("Dumbbell Bench Press", 3, 10),
                    entry("Pendlay Row", 3, 8),
                    entry("Cable Fly", 3, 12),
                    entry("Lateral Raise", 4, 12),
                    entry("Face Pull", 4, 12),
                    entry("Overhead Cable Extension", 3, 12),
                    entry("Cable Curl", 4, 12),
                )),
                day("Lower B", 5, listOf(
                    entry("Deadlift", 3, 5),
                    entry("Hack Squat", 4, 10),
                    entry("Hip Thrust", 2, 10),
                    entry("Seated Leg Curl", 3, 12),
                    entry("Standing Calf Raise", 4, 12),
                    entry("Seated Calf Raise", 4, 15),
                    entry("Hanging Knee Raise", 3, 10),
                    entry("Ab Wheel Rollout", 3, 10),
                )),
            ),
        ),
        ProgramTemplate(
            id = "heavy_light_strength",
            split = TrainingSplit.UPPER_LOWER,
            authoredVolume = VolumeLevel.HIGH,
            focus = TrainingFocus.STRENGTH,
            name = "Heavy/Light Strength",
            summary = "Heavy and light days across the week for a small strength " +
                "edge (Williams 2017; Moesgaard 2022). Squat and bench twice, " +
                "deadlift once.",
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
            id = "ppl_muscle",
            split = TrainingSplit.PUSH_PULL_LEGS,
            authoredVolume = VolumeLevel.HIGH,
            focus = TrainingFocus.MUSCLE,
            name = "Push/Pull/Legs Size",
            summary = "Push/pull/legs twice over (Pelland 2026). Hamstring and calf " +
                "work loads the stretch (Maeo 2021; Kassiano 2023).",
            days = listOf(
                day("Push A", 1, listOf(
                    entry("Bench Press", 4, 8),
                    entry("Overhead Press", 3, 8),
                    entry("Incline Dumbbell Press", 3, 10),
                    entry("Lateral Raise", 4, 12),
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
                    // Calves at four sets a block need a third block to
                    // reach this level's floor without a fifth set anywhere.
                    entry("Standing Calf Raise", 3, 12),
                )),
                day("Legs A", 4, listOf(
                    entry("Back Squat", 4, 8),
                    entry("Romanian Deadlift", 3, 8),
                    entry("Leg Press", 5, 10),
                    entry("Seated Leg Curl", 4, 12),
                    entry("Standing Calf Raise", 4, 12),
                    entry("Seated Calf Raise", 4, 15),
                    entry("Hanging Leg Raise", 4, 10),
                )),
                day("Push B", 5, listOf(
                    entry("Overhead Press", 4, 8),
                    entry("Dumbbell Bench Press", 3, 10),
                    entry("Cable Fly", 3, 12),
                    entry("Pec Deck", 3, 12),
                    entry("Lateral Raise", 4, 12),
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
                    entry("Standing Calf Raise", 4, 12),
                    entry("Seated Calf Raise", 4, 15),
                    entry("Hanging Knee Raise", 3, 10),
                )),
            ),
        ),
    ) }

    // ------------------------------------------------------------------ build

    /**
     * Adapts a template to the lifter's equipment and volume and fills loads
     * from the strength profile. A movement the equipment cannot express
     * becomes the allowed movement with the closest MuscleMap profile and the
     * same pattern - substitution is legitimate for hypertrophy (Kikuchi 2017,
     * Calatayud 2015, Plotkin 2023) and the entry's `why` says when it
     * happened. A day left with no entries is dropped honestly. With
     * [compoundOnly] an isolation entry becomes its closest same-pattern
     * compound or skill movement, or is dropped when there is none.
     *
     * Each entry's sets scale by the ratio of the chosen level's per-movement
     * set range ([ProgramRules.setRange], midpoints, compound or single-joint
     * as the entry is) to the one the template was written at, and land
     * inside the chosen range - except that a template read at or above its
     * own volume keeps sets it authored past the range top (a 5x3 bench). Scaling up never pushes a day
     * past the session [sessionMinutes] time budget or its own authored
     * length, whichever is longer, and any muscle left under four hard sets
     * is named in the routine's note.
     *
     * A day with more movements than the lifter's [maxExercises]
     * ([ProgramRules.movementCap]) gives up, one at a time, the entry
     * whose loss leaves the fewest tracked sets short once the repair
     * pass has regrown what it can - never its first, the day's primary
     * lift. The day's note says it was trimmed.
     */
    fun build(
        template: ProgramTemplate,
        volume: VolumeLevel,
        equipment: Equipment,
        catalogue: List<Exercise>,
        strength: StrengthProfile,
        compoundOnly: Boolean = false,
        maxExercises: Int = ProgramRules.DEFAULT_MAX_EXERCISES,
        sessionMinutes: Int = ProgramRules.DEFAULT_SESSION_MINUTES,
    ): RoutinePlan {
        val pool = ProgramGenerator.prescribable(
            catalogue, ProgramRequest(template.focus, volume, equipment, compoundOnly = compoundOnly),
        )
        val range = ProgramRules.COVERAGE_BAND
        val budget = ProgramRules.sessionBudgetSeconds(sessionMinutes)
        val presets = template.days.mapNotNull { day ->
            // Two gym movements must not collapse onto one substitute in the
            // same day (bench and incline press both becoming "Push-up").
            val used = mutableSetOf<String>()
            val entries = day.entries.mapNotNull { entry ->
                val profile = MuscleMap.profile(entry.exerciseName)
                // A movement an earlier entry's substitute already took is
                // substituted in turn: one exercise appears once per day.
                var exercise = pool.firstOrNull { it.name.equals(entry.exerciseName, ignoreCase = true) && it.name !in used }
                var substituted = false
                if (exercise == null) {
                    if (profile == null) return@mapNotNull entry.copy(targetWeightKg = null)
                    // Dropped for compound & skill only, an isolation entry is
                    // replaced only by a movement that really trains its
                    // muscles: a calf raise has no compound twin, and "the
                    // closest" ISOLATION-pattern skill (a bench dip) is not one.
                    val minSimilarity = if (compoundOnly && MovementDifficulty.isIsolation(entry.exerciseName)) {
                        ProgramGenerator.COMPOUND_ONLY_MIN_SIMILARITY
                    } else {
                        0.0
                    }
                    exercise = closestSubstitute(pool.filter { it.name !in used }, profile, entry.exerciseName, minSimilarity)
                        ?: return@mapNotNull null
                    substituted = true
                }
                used += exercise.name
                // The dumbbell cap lives in fillWithCap: an over-cap movement
                // becomes its harder variant when the pool has one.
                val (filledExercise, load) = ProgramGenerator.fillWithCap(
                    exercise, pool, strength, entry.reps,
                    ProgramRules.loadRir(MuscleMap.profile(exercise.name)?.compound ?: true), equipment, taken = used,
                )
                val capSwapped = filledExercise !== exercise
                if (capSwapped) used += filledExercise.name
                exercise = filledExercise
                val isMainLift = exercise.name.trim().lowercase() in
                    setOf("back squat", "bench press", "deadlift", "overhead press")
                val why = if (capSwapped) {
                    ProgramGenerator.cite(Words.text(UiText.res(R.string.program_why_dumbbell_light, entry.exerciseName, exercise.name)), "Lopez 2021")
                } else if (substituted && compoundOnly && MovementDifficulty.isIsolation(entry.exerciseName)) {
                    ProgramGenerator.cite(Words.text(UiText.themed(R.string.program_why_template_compound_only, R.string.program_why_template_compound_only__t, exercise.name, entry.exerciseName)), "Gentil 2015")
                } else if (substituted) {
                    ProgramGenerator.cite(Words.text(UiText.themed(R.string.program_why_template_nothing, R.string.program_why_template_nothing__t, entry.exerciseName, exercise.name)), "Kikuchi 2017", "Calatayud 2015")
                } else if (template.focus == TrainingFocus.STRENGTH && isMainLift) {
                    ProgramGenerator.cite(Words.text(UiText.res(R.string.program_why_main_lift)), "Buckner 2017", "TaskSpec 2025", "Lopez 2021")
                } else {
                    val profile = MuscleMap.profile(exercise.name)
                    val primary = profile?.muscles?.filterValues { it >= 0.5 }?.maxByOrNull { it.value }?.key
                    if (template.focus == TrainingFocus.MUSCLE && profile?.stretchBias == true && primary != null) {
                        ProgramGenerator.cite(
                            Words.text(UiText.res(R.string.program_why_template_stretch, ProgramGenerator.muscleListOf(exercise, primary))),
                            ProgramGenerator.longLengthEvidence(primary),
                        )
                    } else {
                        ProgramGenerator.cite(Words.text(UiText.res(R.string.program_why_template_for, ProgramGenerator.muscleListOf(exercise))), "Pelland 2026")
                    }
                }
                entry.copy(
                    exerciseName = exercise.name,
                    reps = load?.reps ?: entry.reps,
                    targetWeightKg = load?.kg,
                    loadNote = load?.note,
                    why = why,
                )
            }
            if (entries.isEmpty()) return@mapNotNull null
            // Same rest/RIR guidance the generator writes, matched to the
            // template's goal and volume; the summary stays on the template.
            val planned = entries.map { entry ->
                ProgramRules.withPlan(
                    entry.copy(sets = scaledSets(entry, template.authoredVolume, volume)),
                    volume, template.focus,
                )
            }
            val scaled = day.copy(
                entries = planned,
                note = ProgramGenerator.presetNote(volume, template.focus),
            )
            scaled to planned.map { it.setsMax }
        }
        val cap = ProgramRules.movementCap(maxExercises)
        val work = presets.map { (day, caps) -> day.entries.toMutableList() to caps.toMutableList() }
        val trimmed = mutableSetOf<Int>()
        // Tracked sets the week still lacks under the floor once the repair
        // pass below has regrown what it can: the loss a trim really costs.
        fun shortAfterRepair(days: List<Pair<List<PlannedEntry>, List<Int>>>): Double {
            val repaired = fitToRange(
                days.map { PlannedPreset("", "", null, it.first) }, days.map { it.second }, template.focus, range, budget,
            )
            val vol = ProgramRules.weeklyVolume(repaired)
            return ProgramRules.TRACKED.sumOf { maxOf(0.0, range.start - (vol[it] ?: 0.0)) }
        }
        for ((d, day) in work.withIndex()) {
            val (entries, caps) = day
            while (entries.size > cap) {
                val week = ProgramRules.weeklyVolume(work.map { PlannedPreset("", "", null, it.first) })
                val short = (1 until entries.size).associateWith { i ->
                    shortAfterRepair(
                        work.mapIndexed { o, (e, c) ->
                            if (o == d) e.filterIndexed { j, _ -> j != i } to c.filterIndexed { j, _ -> j != i } else e to c
                        },
                    )
                }
                // Never the first entry, the day's primary lift; ties go to
                // the smaller unrepaired loss, then single-joint work, then later.
                val drop = (1 until entries.size).minWithOrNull(
                    compareBy<Int> { short.getValue(it) }
                        .thenBy { ProgramGenerator.capTrimCost(entries[it], week, range) }
                        .thenBy { if (MuscleMap.profile(entries[it])?.compound == false) 0 else 1 }
                        .thenByDescending { it },
                ) ?: break
                entries.removeAt(drop)
                caps.removeAt(drop)
                trimmed += d
            }
        }
        val capped = presets.mapIndexed { d, (day, _) ->
            day.copy(
                entries = work[d].first,
                note = if (d in trimmed) day.note + " " + Words.text(UiText.res(R.string.program_note_trimmed, cap)) else day.note,
            )
        }
        val fitted = fitToRange(capped, work.map { it.second }, template.focus, range, budget)
        val shortfall = ProgramGenerator.shortfallNote(
            ProgramRules.weeklyVolume(fitted), Words.text(UiText.themed(R.string.program_advice_build_week, R.string.program_advice_build_week__t)),
        )
        return RoutinePlan(
            fitted.map { day -> day.copy(entries = ProgramGenerator.spacePullUps(day.entries)) },
            note = shortfall,
        )
    }

    private fun midpoint(range: IntRange) = (range.first + range.last) / 2.0

    /**
     * [entry]'s sets moved from the level it was authored at to [volume]:
     * scaled by the ratio of the two set ranges' midpoints for its kind of
     * movement, then held inside the chosen range. Scaling up to or past the
     * authored level keeps what the template wrote beyond the range top.
     */
    private fun scaledSets(entry: PlannedEntry, authored: VolumeLevel, volume: VolumeLevel): Int {
        val compound = MuscleMap.profile(entry.exerciseName)?.compound ?: true
        val from = ProgramRules.setRange(authored, compound)
        val to = ProgramRules.setRange(volume, compound)
        val factor = midpoint(to) / midpoint(from)
        val top = if (factor >= 1.0) maxOf(to.last, entry.sets) else to.last
        return kotlin.math.round(entry.sets * factor).toInt()
            .coerceIn(to.first, top)
            .coerceAtMost(ProgramRules.ENTRY_SANITY_MAX_SETS)
    }

    /** Upper bound on repair steps; each moves one set, so it only bounds a bug. */
    private const val MAX_FIT_STEPS = 300

    /**
     * Repairs a scaled week the way the generator builds one, one set at a
     * time: every day is trimmed to the session time budget (taking sets
     * from whatever keeps the most spare volume), then - for MUSCLE templates
     * - muscles under the floor gain sets on their most direct movement where
     * a day has time and the session's muscle ceiling allows
     * ([ProgramRules.sessionMuscleRoom]), and muscles over the top of the
     * range give sets back where that leaves every muscle at its floor.
     * [caps] bounds each entry (its own [PlannedEntry.setsMax]); [budget] is
     * the session clock in seconds. Uniform scaling alone rounded
     * 2- and 3-set entries away and left hamstrings at 11 of 12; the authored
     * upper/lower days ran 90 minutes. A day still over the budget with every
     * entry at two sets drops its last single-joint entry, and says so when
     * there is none to drop.
     */
    internal fun fitToRange(
        days: List<PlannedPreset>,
        caps: List<List<Int>>,
        focus: TrainingFocus,
        range: ClosedFloatingPointRange<Double>,
        budget: Int = ProgramRules.sessionBudgetSeconds(ProgramRules.DEFAULT_SESSION_MINUTES),
    ): List<PlannedPreset> {
        val work = days.map { it.entries.toMutableList() }
        val capsWork = caps.map { it.toMutableList() }
        val overBudget = mutableSetOf<Int>()
        fun result() = days.mapIndexed { d, day ->
            day.copy(
                entries = work[d],
                note = if (d in overBudget) day.note + " " + Words.text(UiText.plural(R.plurals.program_extra_over_budget, budget / 60)) else day.note,
            )
        }
        fun week() = ProgramRules.weeklyVolume(work.map { PlannedPreset("", "", null, it) })
        fun share(e: PlannedEntry, m: Muscle) = MuscleMap.profile(e.exerciseName)?.muscles?.get(m) ?: 0.0
        fun seconds(d: Int) = ProgramRules.sessionSeconds(work[d], focus)
        fun setCost(e: PlannedEntry) = ProgramRules.setSeconds(focus, e.exerciseName)
        fun mains(e: PlannedEntry) = ProgramRules.TRACKED.filter { share(e, it) >= 0.5 }
        // Spare volume a one-set cut leaves on the entry's most-strained muscle.
        fun surplusAfterCut(e: PlannedEntry, vol: Map<Muscle, Double>): Double =
            mains(e).minOfOrNull { (vol[it] ?: 0.0) - share(e, it) - range.start } ?: Double.MAX_VALUE
        fun change(d: Int, i: Int, delta: Int) {
            work[d][i] = work[d][i].copy(sets = work[d][i].sets + delta)
        }

        for (d in work.indices) {
            while (seconds(d) > budget) {
                val vol = week()
                val i = work[d].indices.filter { work[d][it].sets > 2 }
                    .maxWithOrNull(compareBy({ surplusAfterCut(work[d][it], vol) }, { work[d][it].sets }))
                if (i != null) {
                    change(d, i, -1)
                    continue
                }
                // Every entry is at its two-set floor. Never the first, the day's primary lift.
                val drop = (1 until work[d].size).lastOrNull { MuscleMap.profile(work[d][it])?.compound == false }
                if (drop == null) {
                    overBudget += d
                    break
                }
                work[d].removeAt(drop)
                capsWork[d].removeAt(drop)
            }
        }
        if (focus != TrainingFocus.MUSCLE) return result()

        val stuckLow = mutableSetOf<Muscle>()
        var steps = 0
        while (steps++ < MAX_FIT_STEPS) {
            val vol = week()
            val muscle = ProgramRules.TRACKED
                .filter { it !in stuckLow && (vol[it] ?: 0.0) < range.start - 1e-9 }
                .minByOrNull { vol[it] ?: 0.0 } ?: break
            fun growable(d: Int, i: Int): Boolean {
                val e = work[d][i]
                return share(e, muscle) >= 0.5 && e.sets < capsWork[d][i] &&
                    ProgramRules.sessionMuscleRoom(work[d], e.exerciseName, 1) &&
                    ProgramRules.TRACKED.none { (vol[it] ?: 0.0) + share(e, it) > range.endInclusive + 1e-9 }
            }
            val all = work.indices.flatMap { d -> work[d].indices.map { d to it } }
            val pick = all
                .filter { (d, i) -> growable(d, i) && seconds(d) + setCost(work[d][i]) <= budget }
                .maxWithOrNull(
                    compareBy<Pair<Int, Int>>({ (d, i) -> share(work[d][i], muscle) })
                        .thenByDescending { (d, i) -> work[d][i].sets }
                        .thenByDescending { (d, _) -> seconds(d) },
                )
            if (pick != null) {
                change(pick.first, pick.second, 1)
                continue
            }
            // No day has time: trade a set from a movement whose muscles all
            // stay at their floor after the cut, on the same day, for one set
            // of the short muscle. Deficits only ever shrink, so this settles.
            val swap = all.filter { (d, i) -> growable(d, i) }
                .sortedByDescending { (d, i) -> share(work[d][i], muscle) }
                .firstNotNullOfOrNull { (d, i) ->
                    work[d].indices
                        .filter { j ->
                            val cut = work[d][j]
                            j != i && cut.sets > 2 && share(cut, muscle) == 0.0 &&
                                surplusAfterCut(cut, vol) >= -1e-9 &&
                                seconds(d) - setCost(cut) + setCost(work[d][i]) <= budget
                        }
                        .maxByOrNull { surplusAfterCut(work[d][it], vol) }
                        ?.let { j -> Triple(d, i, j) }
                }
            if (swap == null) {
                stuckLow += muscle
            } else {
                change(swap.first, swap.third, -1)
                change(swap.first, swap.second, 1)
            }
        }

        val stuckHigh = mutableSetOf<Muscle>()
        steps = 0
        while (steps++ < MAX_FIT_STEPS) {
            val vol = week()
            val muscle = ProgramRules.TRACKED
                .filter { it !in stuckHigh && (vol[it] ?: 0.0) > range.endInclusive + 1e-9 }
                .maxByOrNull { vol[it] ?: 0.0 } ?: break
            val pick = work.indices.flatMap { d -> work[d].indices.map { d to it } }
                .filter { (d, i) ->
                    val e = work[d][i]
                    share(e, muscle) >= 0.5 && e.sets > 2 && surplusAfterCut(e, vol) >= -1e-9
                }
                .maxWithOrNull(
                    compareBy<Pair<Int, Int>>({ (d, i) -> share(work[d][i], muscle) })
                        .thenBy { (d, i) -> work[d][i].sets },
                )
            if (pick == null) stuckHigh += muscle else change(pick.first, pick.second, -1)
        }
        return result()
    }

    /**
     * Same pattern, most similar muscle profile (cosine over the shared
     * contribution vector), then compound, equipment fit, tier, name - all
     * deterministic. A candidate under [minSimilarity] is never picked.
     */
    private fun closestSubstitute(
        pool: List<Exercise>,
        target: ExerciseProfile,
        originalName: String,
        minSimilarity: Double,
    ): Exercise? = pool
        .filter { (MuscleMap.profile(it.name)?.pattern) == target.pattern }
        .filter { !it.name.equals(originalName, ignoreCase = true) }
        .filter { ProgramGenerator.similarity(target, MuscleMap.profile(it.name)!!) >= minSimilarity }
        .maxWithOrNull(
            compareBy(
                { ProgramGenerator.similarity(target, MuscleMap.profile(it.name)!!) },
                { if (MuscleMap.profile(it.name)!!.compound) 1 else 0 },
                { -MovementDifficulty.tier(it.name) },
                { it.name },
            ),
        )

}
