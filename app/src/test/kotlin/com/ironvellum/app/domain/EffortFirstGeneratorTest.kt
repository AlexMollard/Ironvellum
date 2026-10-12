package com.ironvellum.app.domain

import com.ironvellum.app.data.Seed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The effort-first generator: every tracked muscle aims at four hard sets (a
 * priority at eight), each movement is dosed inside its own set range with an
 * effort cue, Heavy is avoided unless reaching four needs it, and the session
 * clock the lifter chose is never exceeded.
 */
class EffortFirstGeneratorTest {

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

    private val bodyweightOnly = Equipment.NOTHING

    private fun week(request: ProgramRequest) = ProgramGenerator.week(request, catalogue, strength)

    private fun volumeOf(plan: RoutinePlan) = ProgramRules.weeklyVolume(plan.presets)

    private fun heavy(plan: RoutinePlan) = ProgramRules.coverage(plan.presets).values.filter { it.heavy }

    private fun short(plan: RoutinePlan): List<Muscle> {
        val volume = volumeOf(plan)
        return ProgramRules.TRACKED.filter { (volume[it] ?: 0.0) < ProgramRules.COVERED_MIN - 1e-9 }
    }

    // ------------------------------------------------------------- coverage

    @Test
    fun `a full gym covers every tracked muscle with four hard sets on three to six days`() {
        for (focus in TrainingFocus.entries) for (volume in VolumeLevel.entries) for (days in 3..6) {
            val plan = week(ProgramRequest(focus, volume, Equipment.FULL_GYM, days, maxExercises = 8))
            val shortOnes = short(plan)
            assertTrue("$focus/$volume/${days}d short: ${shortOnes.map { it to volumeOf(plan)[it] }}", shortOnes.isEmpty())
            assertEquals("$focus/$volume/${days}d note", "", plan.note.replace(Regex("Each muscle once a week.*"), "").trim())
        }
    }

    @Test
    fun `a full gym covers every tracked muscle at the default movement cap on three to six days`() {
        for (focus in TrainingFocus.entries) for (days in 3..6) {
            val plan = week(ProgramRequest(focus, VolumeLevel.STANDARD, Equipment.FULL_GYM, days))
            assertTrue("$focus/${days}d short: ${short(plan)}", short(plan).isEmpty())
        }
    }

    @Test
    fun `a standard full gym week is never heavy`() {
        for (focus in TrainingFocus.entries) for (days in 3..6) {
            val plan = week(ProgramRequest(focus, VolumeLevel.STANDARD, Equipment.FULL_GYM, days))
            assertTrue("$focus/${days}d heavy: ${heavy(plan)}", heavy(plan).isEmpty())
        }
    }

    @Test
    fun `a six day ninety minute week is never heavy`() {
        for (focus in TrainingFocus.entries) for (volume in VolumeLevel.entries) {
            val plan = week(ProgramRequest(focus, volume, Equipment.FULL_GYM, 6, sessionMinutes = 90, maxExercises = 8))
            assertTrue("$focus/$volume heavy: ${heavy(plan)}", heavy(plan).isEmpty())
        }
    }

    @Test
    fun `a bodyweight only week plans without a crash and names every short muscle`() {
        for (focus in TrainingFocus.entries) for (volume in VolumeLevel.entries) for (days in 1..6) {
            val plan = week(ProgramRequest(focus, volume, bodyweightOnly, days))
            assertTrue("$focus/$volume/${days}d planned nothing", plan.presets.isNotEmpty())
            short(plan).forEach { muscle ->
                assertTrue("${muscle.label} short but not named: ${plan.note}", muscle.label.lowercase() in plan.note)
            }
        }
    }

    @Test
    fun `a two day forty five minute week says honestly which muscles stay below four`() {
        val plan = week(
            ProgramRequest(TrainingFocus.MUSCLE, VolumeLevel.STANDARD, Equipment.FULL_GYM, 2, sessionMinutes = 45),
        )
        val shortOnes = short(plan)
        assertTrue("two short days cannot cover twelve muscles, yet none is short", shortOnes.isNotEmpty())
        assertTrue(plan.note, plan.note.startsWith("${shortOnes.size} muscles below 4 hard sets ("))
        shortOnes.forEach { assertTrue("${it.label} not named: ${plan.note}", it.label.lowercase() in plan.note) }
        plan.presets.forEach {
            assertTrue(
                "${it.name} runs ${ProgramRules.dayMinutes(it, TrainingFocus.MUSCLE)} min",
                ProgramRules.sessionSeconds(it.entries, TrainingFocus.MUSCLE) <= 45 * 60,
            )
        }
    }

    @Test
    fun `every priority on its own and all together terminates and covers its muscles`() {
        val areas = MuscleArea.entries.map { setOf(it) } + listOf(MuscleArea.entries.toSet(), emptySet())
        for (priorities in areas) for (volume in VolumeLevel.entries) for (days in listOf(2, 4, 6)) {
            val request = ProgramRequest(
                TrainingFocus.MUSCLE, volume, Equipment.FULL_GYM, days, priorities = priorities, maxExercises = 8,
            )
            val plan = week(request)
            assertTrue("$priorities/$volume/${days}d planned nothing", plan.presets.isNotEmpty())
            if (days >= 4) {
                val trackedPriorities = priorities.flatMap { it.muscles }.filter { it in ProgramRules.TRACKED }
                val volumeMap = volumeOf(plan)
                trackedPriorities.forEach {
                    assertTrue("$priorities/$volume/${days}d: $it at ${volumeMap[it]}", (volumeMap[it] ?: 0.0) >= ProgramRules.COVERED_MIN - 1e-9)
                }
            }
        }
    }

    @Test
    fun `a priority aims at eight above low and at four at low`() {
        fun arms(volume: VolumeLevel): Double {
            val plan = week(
                ProgramRequest(
                    TrainingFocus.MUSCLE, volume, Equipment.FULL_GYM, 5,
                    priorities = setOf(MuscleArea.ARMS), maxExercises = 8,
                ),
            )
            return minOf(volumeOf(plan)[Muscle.BICEPS]!!, volumeOf(plan)[Muscle.TRICEPS]!!)
        }
        assertTrue(arms(VolumeLevel.STANDARD) >= 8.0 - 1e-9)
        assertTrue(arms(VolumeLevel.HIGH) >= 8.0 - 1e-9)
        assertTrue(arms(VolumeLevel.LOW) >= ProgramRules.COVERED_MIN - 1e-9)
    }

    // ----------------------------------------------------------- set ranges

    @Test
    fun `low keeps the backbone inside the low range and high starts it at the top`() {
        fun backbone(volume: VolumeLevel): List<PlannedEntry> =
            week(ProgramRequest(TrainingFocus.MUSCLE, volume, Equipment.FULL_GYM, 4))
                .presets.map { it.entries.first() }
        val low = backbone(VolumeLevel.LOW)
        val high = backbone(VolumeLevel.HIGH)
        val standard = backbone(VolumeLevel.STANDARD)
        // The fill may still raise a low backbone lift towards the top of the low range.
        low.forEach { assertTrue("${it.exerciseName} ${it.sets}", it.sets in ProgramRules.setRange(VolumeLevel.LOW, true)) }
        assertTrue("some low lift starts at the floor", low.any { it.sets == ProgramRules.setRange(VolumeLevel.LOW, true).first })
        high.forEach { assertEquals(it.exerciseName, ProgramRules.setRange(VolumeLevel.HIGH, true).last, it.sets) }
        standard.forEach { assertTrue("${it.exerciseName} ${it.sets}", it.sets in 2..4) }
        assertTrue(low.sumOf { it.sets } < standard.sumOf { it.sets })
        assertTrue(standard.sumOf { it.sets } < high.sumOf { it.sets })
    }

    @Test
    fun `no entry passes the top of its set range and every entry carries its range, effort and rep window`() {
        for (focus in TrainingFocus.entries) for (volume in VolumeLevel.entries) for (days in 2..6) {
            val plan = week(ProgramRequest(focus, volume, Equipment.FULL_GYM, days, maxExercises = 8))
            for (entry in plan.presets.flatMap { it.entries }) {
                val compound = MuscleMap.profile(entry)!!.compound
                val range = ProgramRules.setRange(volume, compound)
                val window = ProgramRules.repWindow(focus, compound)
                val label = "$focus/$volume/${days}d ${entry.exerciseName} ${entry.sets}x${entry.reps}"
                assertEquals(label, range.first, entry.setsMin)
                assertEquals(label, range.last, entry.setsMax)
                assertTrue(label, entry.sets in entry.setsMin..entry.setsMax)
                assertEquals(label, if (compound) EffortCue.RIR_0_2 else EffortCue.TO_FAILURE, entry.effort)
                assertEquals(label, window.first, entry.repLow)
                assertEquals(label, window.last, entry.repHigh)
                assertTrue(label, entry.reps in entry.repLow..entry.repHigh)
            }
        }
    }

    @Test
    fun `loads are computed for two reps in reserve on compounds and failure on isolation`() {
        val request = ProgramRequest(TrainingFocus.MUSCLE, VolumeLevel.STANDARD, Equipment.FULL_GYM, 4)
        val entries = week(request).presets.flatMap { it.entries }
        val isolation = entries.first { MuscleMap.profile(it)!!.compound.not() }
        val compound = entries.first { MuscleMap.profile(it)!!.compound }
        val logged = ProgramRules.strengthProfile(
            listOf(
                LoggedLift(isolation.exerciseName, 30.0, 10),
                LoggedLift(compound.exerciseName, 100.0, 10),
            ),
        )
        val again = ProgramGenerator.week(request, catalogue, logged).presets.flatMap { it.entries }
        fun groupOf(name: String) = catalogue.first { it.name == name }.muscleGroup.name
        val isolationAgain = again.first { it.exerciseName == isolation.exerciseName }
        val compoundAgain = again.first { it.exerciseName == compound.exerciseName }
        assertEquals(
            ProgramRules.workingLoadKg(isolation.exerciseName, groupOf(isolation.exerciseName), logged, isolationAgain.reps, 0)!!.first,
            isolationAgain.targetWeightKg!!, 1e-9,
        )
        assertEquals(
            ProgramRules.workingLoadKg(compound.exerciseName, groupOf(compound.exerciseName), logged, compoundAgain.reps, 2)!!.first,
            compoundAgain.targetWeightKg!!, 1e-9,
        )
    }

    // ----------------------------------------------------------- time budget

    @Test
    fun `every session stays inside the chosen time budget`() {
        for (minutes in ProgramRules.SESSION_MINUTES_OPTIONS) for (focus in TrainingFocus.entries)
            for (volume in VolumeLevel.entries) for (days in 2..6) {
                val request = ProgramRequest(focus, volume, Equipment.FULL_GYM, days, sessionMinutes = minutes, maxExercises = 8)
                for (preset in week(request).presets) {
                    val seconds = ProgramRules.sessionSeconds(preset.entries, focus)
                    assertTrue(
                        "$focus/$volume/${days}d at $minutes min: ${preset.name} runs ${seconds / 60} min",
                        seconds <= ProgramRules.sessionBudgetSeconds(minutes),
                    )
                }
            }
    }

    @Test
    fun `a single session respects the chosen time budget too`() {
        for (minutes in ProgramRules.SESSION_MINUTES_OPTIONS) for (kind in SessionKind.entries) {
            val request = ProgramRequest(TrainingFocus.MUSCLE, VolumeLevel.HIGH, Equipment.FULL_GYM, sessionMinutes = minutes, maxExercises = 8)
            val preset = ProgramGenerator.session(request, kind, 1, emptyList(), catalogue, strength) ?: continue
            val seconds = ProgramRules.sessionSeconds(preset.entries, TrainingFocus.MUSCLE)
            assertTrue("$kind at $minutes min runs ${seconds / 60} min", seconds <= ProgramRules.sessionBudgetSeconds(minutes))
        }
    }

    @Test
    fun `the default session length is seventy five minutes and a shorter clock never plans more work`() {
        val base = ProgramRequest(TrainingFocus.MUSCLE, VolumeLevel.STANDARD, Equipment.FULL_GYM, 4, maxExercises = 8)
        assertEquals(75, base.sessionMinutes)
        val sets = { minutes: Int ->
            week(base.copy(sessionMinutes = minutes)).presets.sumOf { day -> day.entries.sumOf { it.sets } }
        }
        assertTrue(sets(45) <= sets(60))
        assertTrue(sets(60) <= sets(75))
        assertTrue(sets(75) <= sets(90))
    }

    // --------------------------------------------------------- determinism

    @Test
    fun `two runs of the same request produce the same week`() {
        for (focus in TrainingFocus.entries) for (days in listOf(2, 4, 6)) {
            val request = ProgramRequest(
                focus, VolumeLevel.STANDARD, Equipment.FULL_GYM, days,
                priorities = setOf(MuscleArea.BACK, MuscleArea.ARMS),
            )
            assertEquals(week(request), week(request))
        }
    }

    @Test
    fun `an improved preset carries the effort cue of every entry it keeps`() {
        val request = ProgramRequest(TrainingFocus.MUSCLE, VolumeLevel.STANDARD, Equipment.FULL_GYM)
        val preset = PlannedPreset(
            "Upper", "", 1,
            listOf(
                PlannedEntry("Bench Press", 3, 8, null),
                PlannedEntry("Lateral Raise", 3, 12, null),
            ),
        )
        val after = ProgramGenerator.improve(preset, emptyList(), request, catalogue, strength).after
        for (entry in after.entries) {
            val compound = MuscleMap.profile(entry)!!.compound
            assertEquals(entry.exerciseName, if (compound) EffortCue.RIR_0_2 else EffortCue.TO_FAILURE, entry.effort)
            assertTrue(entry.exerciseName, entry.sets in entry.setsMin..entry.setsMax)
        }
    }

    // ------------------------------------------------------------ templates

    @Test
    fun `template entries carry their set range, effort and rep window at every level`() {
        for (template in ProgramTemplates.ALL) for (volume in VolumeLevel.entries) {
            val plan = ProgramTemplates.build(template, volume, Equipment.FULL_GYM, catalogue, strength)
            for (entry in plan.presets.flatMap { it.entries }) {
                val compound = MuscleMap.profile(entry)!!.compound
                val range = ProgramRules.setRange(volume, compound)
                val label = "${template.id}/$volume ${entry.exerciseName} ${entry.sets}"
                assertTrue(label, entry.sets in entry.setsMin..entry.setsMax)
                assertTrue(label, entry.setsMin <= range.first && entry.setsMax >= range.last)
                assertEquals(label, if (compound) EffortCue.RIR_0_2 else EffortCue.TO_FAILURE, entry.effort)
                assertTrue(label, entry.reps in entry.repLow..entry.repHigh)
                assertTrue(label, entry.sets <= ProgramRules.ENTRY_SANITY_MAX_SETS)
            }
        }
    }

    @Test
    fun `a lower volume never gives a template entry more sets and a higher one never fewer`() {
        for (template in ProgramTemplates.ALL) {
            fun sets(volume: VolumeLevel) = ProgramTemplates.build(template, volume, Equipment.FULL_GYM, catalogue, strength)
                .presets.flatMap { it.entries }.sumOf { it.sets }
            assertTrue(template.id, sets(VolumeLevel.LOW) <= sets(VolumeLevel.STANDARD))
            assertTrue(template.id, sets(VolumeLevel.STANDARD) <= sets(VolumeLevel.HIGH))
        }
    }

    @Test
    fun `template days honour the chosen session length`() {
        for (template in ProgramTemplates.ALL) for (minutes in listOf(45, 60)) {
            val plan = ProgramTemplates.build(
                template, VolumeLevel.STANDARD, Equipment.FULL_GYM, catalogue, strength, sessionMinutes = minutes,
            )
            for (day in plan.presets) {
                val seconds = ProgramRules.sessionSeconds(day.entries, template.focus)
                assertTrue(
                    "${template.id} ${day.name} at $minutes min runs ${seconds / 60} min",
                    seconds <= ProgramRules.sessionBudgetSeconds(minutes) || "Runs past $minutes minutes" in day.note,
                )
            }
        }
    }
}
