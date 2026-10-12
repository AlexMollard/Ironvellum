package com.ironvellum.app.domain

import com.ironvellum.app.data.Seed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Overlap detection (two rows doing one job) and heavy-muscle easing: every
 * suggestion lowers a heavy muscle's load without pushing any muscle under
 * the four-set minimum, best option first, swaps with three alternatives.
 */
class ProgramEasingTest {

    private val catalogue: List<Exercise> = Seed.exercises.map {
        Exercise(
            name = it.name,
            muscleGroup = MuscleGroup.valueOf(it.muscleGroup),
            isWeighted = it.isWeighted,
            metric = ExerciseMetric.valueOf(it.metric),
            category = it.category,
        )
    }

    private val strength = ProgramRules.strengthProfile(
        listOf(LoggedLift("Bench Press", 75.0, 10), LoggedLift("Back Squat", 80.0, 10)),
    )

    private val gym = ProgramRequest(TrainingFocus.MUSCLE, VolumeLevel.STANDARD, Equipment.FULL_GYM, maxExercises = 8)
    private val bodyweight = ProgramRequest(TrainingFocus.MUSCLE, VolumeLevel.STANDARD, Equipment.NOTHING, maxExercises = 8)

    private fun entry(name: String, sets: Int) = PlannedEntry(name, sets, 8, null)

    private fun preset(name: String, day: Int, vararg entries: PlannedEntry) = PlannedPreset(name, "", day, entries.toList())

    /** Mid chest 22 weekly sets (Bench Press), 6 or less per session. */
    private val heavyChestWeek = listOf(
        preset("Push A", 1, entry("Bench Press", 6)),
        preset("Push B", 3, entry("Bench Press", 6)),
        preset("Push C", 5, entry("Bench Press", 6)),
        preset("Push D", 6, entry("Bench Press", 4)),
    )

    /** The week with one option applied, on the preset the option names. */
    private fun applied(week: List<PlannedPreset>, option: EasingOption): List<PlannedPreset> {
        val op = option.op
        return when (op) {
            is ChangeOp.SetSets -> week.map { p ->
                if (p.name != option.presetName) p
                else p.copy(entries = p.entries.map { if (it.exerciseName == op.exerciseName) it.copy(sets = op.sets) else it })
            }
            is ChangeOp.SwapEntry -> week.map { p ->
                if (p.name != option.presetName) p
                else p.copy(entries = p.entries.map { if (it.exerciseName == op.fromName) op.to else it })
            }
            is ChangeOp.MoveEntryToDay -> {
                val moved = week.first { it.name == option.presetName }.entries.first { it.exerciseName == op.exerciseName }
                week.map { p ->
                    when {
                        p.name == option.presetName -> p.copy(entries = p.entries.filter { it !== moved })
                        p.scheduledDay == op.toDay -> p.copy(entries = p.entries + moved)
                        else -> p
                    }
                }
            }
            else -> error("unexpected $op")
        }
    }

    private fun excess(week: List<PlannedPreset>, muscle: Muscle): Double {
        val load = ProgramRules.coverage(week).getValue(muscle)
        return maxOf(0.0, load.weekly - ProgramRules.HEAVY_WEEK) + maxOf(0.0, load.maxSession - ProgramRules.HEAVY_SESSION)
    }

    private fun assertNeverBelowFour(week: List<PlannedPreset>, options: List<EasingOption>) {
        val before = ProgramRules.weeklyVolume(week)
        for (option in options) {
            val after = ProgramRules.weeklyVolume(applied(week, option))
            for ((muscle, was) in before) {
                if (was >= ProgramRules.COVERED_MIN) {
                    assertTrue(
                        "${option.id} drops $muscle to ${after[muscle]}",
                        (after[muscle] ?: 0.0) >= ProgramRules.COVERED_MIN - 1e-9,
                    )
                }
            }
        }
    }

    // -------------------------------------------------------------- overlap

    @Test
    fun `two rows doing one job are flagged`() {
        val day = preset("Pull A", 1, entry("Dumbbell Row", 3), entry("Seated Cable Row", 3), entry("Lat Pulldown", 3))
        val findings = Overlap.detect(day)
        assertEquals(1, findings.size)
        assertEquals(setOf("Dumbbell Row", "Seated Cable Row"), setOf(findings[0].a.exerciseName, findings[0].b.exerciseName))
        assertEquals(Muscle.RHOMBOIDS, findings[0].muscle)
    }

    @Test
    fun `different patterns are not flagged`() {
        val day = preset("Upper A", 1, entry("Bench Press", 3), entry("Dumbbell Row", 3), entry("Lat Pulldown", 3))
        assertTrue(Overlap.detect(day).isEmpty())
    }

    @Test
    fun `an overlap carries up to three ranked replacements that do not overlap`() {
        val day = preset("Pull A", 1, entry("Dumbbell Row", 3), entry("Seated Cable Row", 3))
        val finding = Overlap.detect(day, gym, catalogue).single()
        val options = finding.alternatives.options
        assertTrue(options.size in 1..3)
        assertTrue(options.none { it.exercise.name in setOf("Dumbbell Row", "Seated Cable Row") })
        assertEquals(options.sortedByDescending { it.score }.map { it.exercise.name }, options.map { it.exercise.name })
        assertTrue(options.all { it.tags.size <= 2 })
    }

    @Test
    fun `without a catalogue an overlap has no alternatives`() {
        val day = preset("Pull A", 1, entry("Dumbbell Row", 3), entry("Seated Cable Row", 3))
        assertTrue(Overlap.detect(day).single().alternatives.options.isEmpty())
    }

    @Test
    fun `the facade finds the same overlaps`() {
        val day = preset("Pull A", 1, entry("Dumbbell Row", 3), entry("Seated Cable Row", 3))
        assertEquals(Overlap.detect(day), ProgramGenerator.overlaps(day))
    }

    // --------------------------------------------------------------- easing

    @Test
    fun `nothing heavy means nothing to ease`() {
        val week = listOf(preset("Upper A", 1, entry("Bench Press", 3), entry("Barbell Row", 3)))
        assertTrue(Easing.suggest(week, gym, catalogue, strength).isEmpty())
    }

    @Test
    fun `a heavy muscle gets a trim that removes one set from its biggest contributor`() {
        val chest = Easing.suggest(heavyChestWeek, gym, catalogue, strength).single { it.muscle == Muscle.MID_CHEST }
        val trim = chest.options.first { it.kind == EasingKind.TRIM_SET }
        // The biggest contributor ties at 6 sets on three days; the later day wins.
        assertEquals("Push C", trim.presetName)
        assertEquals(ChangeOp.SetSets("Bench Press", 5), trim.op)
        assertEquals(-1.0, trim.coverageDelta.getValue(Muscle.MID_CHEST), 1e-9)
        assertNull(trim.alternatives)
    }

    @Test
    fun `easing never drops any muscle below four hard sets`() {
        val weeks = listOf(
            heavyChestWeek,
            // Bench is a big source of triceps and front delts: trimming must keep them covered.
            listOf(
                preset("Push A", 1, entry("Bench Press", 5), entry("Seated Cable Row", 3)),
                preset("Push B", 3, entry("Bench Press", 5), entry("Seated Cable Row", 3)),
                preset("Push C", 5, entry("Bench Press", 5)),
                preset("Push D", 6, entry("Bench Press", 5)),
            ),
            // Session heavy only: 11 mid-chest sets in one day.
            listOf(preset("Push A", 1, entry("Bench Press", 11)), preset("Push B", 3, entry("Barbell Row", 4))),
        )
        for (week in weeks) for (request in listOf(gym, bodyweight)) {
            for (suggestion in Easing.suggest(week, request, catalogue, strength)) {
                assertNeverBelowFour(week, suggestion.options)
            }
        }
    }

    @Test
    fun `options are ranked by the heavy load they remove and carry coverage deltas`() {
        val chest = Easing.suggest(heavyChestWeek, gym, catalogue, strength).single { it.muscle == Muscle.MID_CHEST }
        assertTrue(chest.options.size >= 2)
        val removed = chest.options.map { excess(heavyChestWeek, Muscle.MID_CHEST) - excess(applied(heavyChestWeek, it), Muscle.MID_CHEST) }
        assertTrue(removed.all { it > 0.0 })
        assertEquals(removed.sortedDescending(), removed)
        assertTrue(chest.options.all { it.coverageDelta.isNotEmpty() })
    }

    @Test
    fun `a swap option offers three ranked alternatives and its op is the first`() {
        val chest = Easing.suggest(heavyChestWeek, gym, catalogue, strength).single { it.muscle == Muscle.MID_CHEST }
        val swap = chest.options.first { it.kind == EasingKind.SWAP }
        assertNotNull(swap.alternatives)
        val alternatives = swap.alternatives!!.options
        assertEquals(3, alternatives.size)
        assertEquals(alternatives.first().exercise.name, (swap.op as ChangeOp.SwapEntry).to.exerciseName)
        assertTrue(alternatives.all { it.tags.size <= 2 })
        assertTrue(alternatives.all { (it.coverageDelta[Muscle.MID_CHEST] ?: 0.0) < 0.0 })
    }

    @Test
    fun `a session-heavy muscle can be moved to a quieter day`() {
        val week = listOf(
            preset("Push A", 1, entry("Bench Press", 6), entry("Push-Up", 6)),
            preset("Push B", 3, entry("Barbell Row", 3)),
            preset("Push C", 5, entry("Bench Press", 4)),
        )
        val chest = Easing.suggest(week, gym, catalogue, strength).single { it.muscle == Muscle.MID_CHEST }
        val move = chest.options.first { it.kind == EasingKind.MOVE_DAY }
        assertTrue((move.op as ChangeOp.MoveEntryToDay).toDay in setOf(3, 5))
        assertTrue(move.tags.isNotEmpty())
    }

    @Test
    fun `bodyweight only offers moves the kit can do`() {
        val week = listOf(
            preset("Push A", 1, entry("Push-Up", 8)),
            preset("Push B", 3, entry("Push-Up", 8)),
            preset("Push C", 5, entry("Push-Up", 8)),
        )
        val chest = Easing.suggest(week, bodyweight, catalogue, strength).single { it.muscle == Muscle.MID_CHEST }
        assertTrue(chest.options.any { it.kind == EasingKind.TRIM_SET })
        val pool = ProgramGenerator.prescribable(catalogue, bodyweight).map { it.name }.toSet()
        for (option in chest.options) {
            val op = option.op
            if (op is ChangeOp.SwapEntry) assertTrue(op.to.exerciseName in pool)
        }
    }

    @Test
    fun `the facade suggests the same easing`() {
        assertEquals(
            Easing.suggest(heavyChestWeek, gym, catalogue, strength),
            ProgramGenerator.easing(heavyChestWeek, gym, catalogue, strength),
        )
    }
}
