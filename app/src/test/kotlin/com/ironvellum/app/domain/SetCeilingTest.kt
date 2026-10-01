package com.ironvellum.app.domain

import com.ironvellum.app.data.Seed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Owner report: the generator asked for six sets of single-leg calf raises
 * to reach the calves' weekly target. No path - week, session, improve or
 * template - may put more on one entry than [ProgramRules.maxSetsPerEntry],
 * or list one exercise twice in a session; a target the ceilings cannot
 * reach is named short in the note instead.
 */
class SetCeilingTest {

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
        listOf(
            LoggedLift("Bench Press", 75.0, 10),
            LoggedLift("Back Squat", 80.0, 10),
            LoggedLift("Barbell Row", 60.0, 10),
        ),
    )

    /** The owner's saved armoury: bar, parallettes, ab wheel, one 24 kg dumbbell. */
    private val ownerKit = Equipment(
        fullGym = false,
        gear = setOf(Gear.PULL_UP_BAR, Gear.PARALLETTES, Gear.DUMBBELLS, Gear.AB_WHEEL),
        dumbbellMaxKg = 24.0,
        dumbbellPair = false,
    )

    private val kits = listOf(
        Equipment.NOTHING,
        Equipment(fullGym = false, gear = setOf(Gear.DUMBBELLS)),
        Equipment(fullGym = false, gear = Gear.entries.toSet()),
        Equipment.FULL_GYM,
        ownerKit,
    )

    private fun assertWithinCeilings(context: String, preset: PlannedPreset) {
        preset.entries.forEach { entry ->
            assertTrue(
                "$context ${preset.name}: ${entry.exerciseName} x${entry.sets}",
                entry.sets <= ProgramRules.maxSetsPerEntry(entry.exerciseName),
            )
        }
        val names = preset.entries.map { it.exerciseName.trim().lowercase() }
        assertEquals("$context ${preset.name} lists an exercise twice: $names", names.distinct(), names)
    }

    @Test
    fun `no generated entry passes its set ceiling and no exercise repeats in a session`() {
        for (kit in kits) for (focus in TrainingFocus.entries) for (volume in VolumeLevel.entries)
            for (days in 2..6) for (compoundOnly in listOf(false, true)) {
                val request = ProgramRequest(focus, volume, kit, days, compoundOnly = compoundOnly)
                val context = "$kit $focus $volume ${days}d co=$compoundOnly"
                val week = ProgramGenerator.week(request, catalogue, strength).presets
                week.forEach { assertWithinCeilings("week $context", it) }
                if (days == 3) {
                    for (preset in week) {
                        val improved = ProgramGenerator.improve(preset, week - preset, request, catalogue, strength)
                        assertWithinCeilings("improve $context", improved.after)
                    }
                    for (kind in SessionKind.entries) {
                        ProgramGenerator.session(request, kind, 2, week, catalogue, strength)
                            ?.let { assertWithinCeilings("session $kind $context", it) }
                    }
                }
            }
    }

    @Test
    fun `no template entry passes its set ceiling and no exercise repeats in a day`() {
        for (template in ProgramTemplates.ALL) for (kit in kits) for (volume in VolumeLevel.entries)
            for (compoundOnly in listOf(false, true)) {
                val plan = ProgramTemplates.build(
                    template, volume, kit, catalogue, strength, compoundOnly,
                    maxExercises = ProgramRules.MAX_EXERCISES_RANGE.last,
                )
                plan.presets.forEach { assertWithinCeilings("${template.id} $kit $volume co=$compoundOnly", it) }
            }
    }

    @Test
    fun `single-joint and one-side work stop at four sets, bilateral compounds and trunk work at five`() {
        assertEquals(4, ProgramRules.maxSetsPerEntry("Single-Leg Calf Raise"))
        assertEquals(4, ProgramRules.maxSetsPerEntry("Standing Calf Raise"))
        assertEquals(4, ProgramRules.maxSetsPerEntry("Lateral Raise"))
        assertEquals(4, ProgramRules.maxSetsPerEntry("Bulgarian Split Squat"))
        assertEquals(4, ProgramRules.maxSetsPerEntry("Dumbbell Row"))
        assertEquals(5, ProgramRules.maxSetsPerEntry("Back Squat"))
        assertEquals(5, ProgramRules.maxSetsPerEntry("Pull-up"))
        assertEquals(5, ProgramRules.maxSetsPerEntry("Hanging Leg Raise"))
        assertEquals(5, ProgramRules.maxSetsPerEntry("A movement of my own"))
    }

    @Test
    fun `a unilateral set pays its work once per side on the clock`() {
        for (focus in TrainingFocus.entries) {
            assertEquals(
                ProgramRules.setSeconds(focus, compound = false) + ProgramRules.SET_WORK_SECONDS,
                ProgramRules.setSeconds(focus, "Single-Leg Calf Raise"),
            )
            assertEquals(
                ProgramRules.setSeconds(focus, compound = false),
                ProgramRules.setSeconds(focus, "Standing Calf Raise"),
            )
        }
        // Every name the unilateral list carries is a real profile.
        listOf(
            "Single-Leg Calf Raise", "Pistol Squat", "Bulgarian Split Squat", "Archer Push-up", "Dumbbell Row",
        ).forEach { assertTrue(it, MuscleMap.profile(it)!!.unilateral) }
        assertEquals(false, MuscleMap.profile("Back Squat")!!.unilateral)
    }

    @Test
    fun `the owner's week names calves short instead of stacking single-leg calf raises`() {
        val request = ProgramRequest(
            TrainingFocus.MUSCLE, VolumeLevel.STANDARD, ownerKit, daysPerWeek = 3,
            split = TrainingSplit.PUSH_PULL_LEGS,
        )
        val plan = ProgramGenerator.week(request, catalogue, strength)
        val calfRaises = plan.presets.flatMap { it.entries }.filter { it.exerciseName == "Single-Leg Calf Raise" }
        assertTrue("fixture broken: no calf raise in ${plan.presets}", calfRaises.isNotEmpty())
        calfRaises.forEach { assertTrue("calf raise x${it.sets}", it.sets <= 4) }
        val calves = ProgramRules.weeklyVolume(plan.presets)[Muscle.CALVES] ?: 0.0
        val floor = ProgramRules.weeklySetTarget(VolumeLevel.STANDARD, TrainingFocus.MUSCLE).start
        assertTrue("fixture broken: calves reached $calves", calves < floor)
        assertTrue("note silent on calves: ${plan.note}", "calves" in plan.note)
    }

    @Test
    fun `improve brings a six-set entry back to its ceiling and says why`() {
        val legs = PlannedPreset(
            "Legs", "", 3,
            listOf(
                PlannedEntry("Pistol Squat", 3, 5, null),
                PlannedEntry("Single-Leg Calf Raise", 6, 12, null),
            ),
        )
        val improved = ProgramGenerator.improve(
            legs, emptyList(), ProgramRequest(TrainingFocus.MUSCLE, VolumeLevel.STANDARD, ownerKit),
            catalogue, strength,
        )
        val calf = improved.after.entries.single { it.exerciseName == "Single-Leg Calf Raise" }
        assertEquals(4, calf.sets)
        val line = improved.changes.firstOrNull { it.exerciseName == calf.exerciseName && "Sets 6 → 4" in it.detail }
        assertNotNull("no sets line in ${improved.changes}", line)
    }
}
