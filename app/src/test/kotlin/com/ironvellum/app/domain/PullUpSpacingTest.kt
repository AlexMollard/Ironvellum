package com.ironvellum.app.domain

import com.ironvellum.app.data.Seed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Owner rule: generated workouts never place two pull-up variants back to
 * back - another exercise sits between them. Checked on the spacing pass
 * itself and on every output path: generated weeks, single workouts,
 * templates, the improve pass and the seeded starter routine.
 */
class PullUpSpacingTest {

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

    /** The owner's kit: a bar, parallettes, one 24 kg dumbbell. */
    private val ownerKit = Equipment(
        fullGym = false,
        gear = setOf(Gear.PULL_UP_BAR, Gear.PARALLETTES, Gear.DUMBBELLS),
        dumbbellMaxKg = 24.0,
        dumbbellPair = false,
    )

    private val kits = listOf(
        Equipment.NOTHING,
        ownerKit,
        Equipment(fullGym = false, gear = setOf(Gear.PULL_UP_BAR, Gear.RINGS)),
        Equipment(fullGym = false, gear = Gear.entries.toSet()),
        Equipment.FULL_GYM,
    )

    private fun entry(name: String, modifiers: String = "") = PlannedEntry(name, 3, 8, null, modifiers)

    private fun names(entries: List<PlannedEntry>) = entries.map { it.exerciseName }

    /**
     * No two adjacent pull-up variants wherever the workout has enough other
     * exercises to separate them without moving its lead (a pull-only day
     * may stay as it is).
     */
    private fun assertSpaced(where: String, entries: List<PlannedEntry>) {
        if (entries.isEmpty()) return
        val rest = entries.drop(1)
        val pulls = rest.count(ProgramGenerator::isPullUpVariant)
        val separators = rest.size - pulls
        val leadIsPull = ProgramGenerator.isPullUpVariant(entries.first())
        if (pulls > separators + (if (leadIsPull) 0 else 1)) return
        entries.zipWithNext().forEach { (a, b) ->
            assertTrue(
                "$where: ${a.exerciseName} then ${b.exerciseName} in ${names(entries)}",
                !(ProgramGenerator.isPullUpVariant(a) && ProgramGenerator.isPullUpVariant(b)),
            )
        }
    }

    @Test
    fun `the pull-up family is every dynamic vertical pull and never a lever hold`() {
        listOf("Pull-up", "Chin-up", "Archer Pull-up", "L-sit Pull-up", "Assisted Pull-up", "Lat Pulldown", "Muscle-up", "Weighted Pull-up")
            .forEach { assertTrue(it, ProgramGenerator.isPullUpVariant(entry(it))) }
        listOf("Front Lever", "Tuck Front Lever", "Inverted Row", "Australian Pull-up", "Active Bar Hang", "Push-up")
            .forEach { assertTrue(it, !ProgramGenerator.isPullUpVariant(entry(it))) }
    }

    @Test
    fun `spacing moves the next other exercise between two pulls and keeps each group's order`() {
        val spaced = ProgramGenerator.spacePullUps(
            listOf(entry("Pull-up"), entry("Chin-up"), entry("Inverted Row"), entry("Push-up"), entry("Plank")),
        )
        assertEquals(listOf("Pull-up", "Inverted Row", "Chin-up", "Push-up", "Plank"), names(spaced))
    }

    @Test
    fun `spacing brings a pull forward when the separators would otherwise run out`() {
        // Row, push-up, pull-up, chin-up: taking the push-up next would leave
        // nothing between the pulls, so the pull-up comes first.
        val spaced = ProgramGenerator.spacePullUps(
            listOf(entry("Inverted Row"), entry("Push-up"), entry("Pull-up"), entry("Chin-up")),
        )
        assertEquals(listOf("Inverted Row", "Pull-up", "Push-up", "Chin-up"), names(spaced))
    }

    @Test
    fun `spacing never moves the lead exercise`() {
        val lead = listOf(entry("Inverted Row"), entry("Pull-up"), entry("Chin-up"))
        assertEquals(lead, ProgramGenerator.spacePullUps(lead))
    }

    @Test
    fun `spacing leaves a spaced or pull-only workout untouched`() {
        val spaced = listOf(entry("Pull-up"), entry("Dip"), entry("Chin-up"), entry("Squat"))
        assertEquals(spaced, ProgramGenerator.spacePullUps(spaced))
        val pullOnly = listOf(entry("Pull-up"), entry("Chin-up"))
        assertEquals(pullOnly, ProgramGenerator.spacePullUps(pullOnly))
        // A weighted pull-up is still a pull-up.
        val weighted = ProgramGenerator.spacePullUps(
            listOf(entry("Pull-up", "weighted"), entry("Chin-up", "weighted"), entry("Dip")),
        )
        assertEquals(listOf("Pull-up", "Dip", "Chin-up"), names(weighted))
    }

    @Test
    fun `generated weeks never place pull-up variants back to back`() {
        for (kit in kits) for ((split, days) in TrainingSplit.OPTIONS) for (focus in TrainingFocus.entries) {
            for (priorities in listOf(emptySet(), setOf(MuscleArea.BACK, MuscleArea.ARMS))) {
                val plan = ProgramGenerator.week(
                    ProgramRequest(focus, VolumeLevel.STANDARD, kit, days, priorities = priorities, split = split, maxExercises = 8),
                    catalogue, strength,
                )
                plan.presets.forEach { assertSpaced("$kit $split$days $focus $priorities ${it.name}", it.entries) }
            }
        }
    }

    @Test
    fun `single generated workouts never place pull-up variants back to back`() {
        for (kit in kits) for (focus in TrainingFocus.entries) for (kind in SessionKind.entries) {
            val preset = ProgramGenerator.session(
                ProgramRequest(focus, VolumeLevel.STANDARD, kit, priorities = setOf(MuscleArea.BACK), maxExercises = 8),
                kind, 2, emptyList(), catalogue, strength,
            ) ?: continue
            assertSpaced("$kit $focus $kind", preset.entries)
        }
    }

    @Test
    fun `templates never place pull-up variants back to back`() {
        for (template in ProgramTemplates.ALL) for (kit in kits) for (volume in VolumeLevel.entries) {
            val plan = ProgramTemplates.build(template, volume, kit, catalogue, strength, maxExercises = 8)
            plan.presets.forEach { assertSpaced("${template.id} $kit $volume ${it.name}", it.entries) }
        }
    }

    @Test
    fun `improve spaces a stacked pull day and says what moved`() {
        // Pull-up at its 5-set ceiling, so the chin-up is not redundant and stays.
        val stacked = PlannedPreset(
            "Pull", "", 1,
            listOf(entry("Pull-up").copy(sets = 5), entry("Chin-up"), entry("Inverted Row"), entry("Hammer Curl")),
        )
        for (kit in kits.drop(1)) for (focus in TrainingFocus.entries) {
            val improvement = ProgramGenerator.improve(
                stacked, emptyList(), ProgramRequest(focus, VolumeLevel.STANDARD, kit), catalogue, strength,
            )
            assertSpaced("$kit $focus", improvement.after.entries)
            assertTrue(
                "$kit $focus: no change line for the reorder",
                improvement.changes.any { "back to back" in it.detail },
            )
        }
    }

    @Test
    fun `improving any generated workout keeps pull-up variants apart`() {
        for (kit in kits) for ((split, days) in TrainingSplit.OPTIONS) {
            val request = ProgramRequest(TrainingFocus.STRENGTH, VolumeLevel.STANDARD, kit, days, split = split)
            val week = ProgramGenerator.week(request, catalogue, strength).presets
            week.forEachIndexed { i, preset ->
                val after = ProgramGenerator.improve(preset, week.filterIndexed { o, _ -> o != i }, request, catalogue, strength).after
                assertSpaced("$kit $split$days ${preset.name}", after.entries)
            }
        }
    }

    @Test
    fun `the seeded starter routine keeps pull-up variants apart`() {
        Seed.presets.forEach { spec ->
            assertSpaced(spec.name, spec.entries.map { entry(it.exercise, it.modifiers) })
        }
    }
}
