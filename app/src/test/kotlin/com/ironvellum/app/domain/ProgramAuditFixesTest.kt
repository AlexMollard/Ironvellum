package com.ironvellum.app.domain

import com.ironvellum.app.data.Seed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the audited bugs in the workout program generator, the
 * templates and the gear tables: each test names the bug it pins.
 */
class ProgramAuditFixesTest {

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

    private fun byName(name: String): Exercise = catalogue.first { it.name == name }

    private fun profileOf(name: String): ExerciseProfile = MuscleMap.profile(name)!!

    private fun groupOf(name: String): MuscleGroup = byName(name).muscleGroup

    private val barbellAndDumbbells = Equipment(
        fullGym = false,
        gear = setOf(Gear.BARBELL, Gear.BENCH, Gear.DUMBBELLS, Gear.PULL_UP_BAR),
        dumbbellMaxKg = 24.0,
    )

    private val dumbbellsOnly = Equipment(fullGym = false, gear = setOf(Gear.DUMBBELLS), dumbbellMaxKg = 24.0)

    // ------------------------------------------------ D1, D12: the dumbbell cap

    @Test
    fun `a barbell lift is never held to the dumbbell cap`() {
        // Back Squat and the bench need no dumbbell; a lifter who owns both
        // must not see their barbell work cut to a 24 kg dumbbell's ceiling.
        assertNull(ProgramGenerator.dumbbellCapKg("Back Squat", barbellAndDumbbells))
        assertNull(ProgramGenerator.dumbbellCapKg("Bench Press", barbellAndDumbbells))
        // A shared-implement lift the barbell covers is not capped either...
        assertNull(ProgramGenerator.dumbbellCapKg("Romanian Deadlift", barbellAndDumbbells))
        // ...but with dumbbells alone it is, and the dumbbell moves always are.
        assertEquals(24.0, ProgramGenerator.dumbbellCapKg("Romanian Deadlift", dumbbellsOnly)!!, 1e-9)
        assertEquals(24.0, ProgramGenerator.dumbbellCapKg("Dumbbell Row", barbellAndDumbbells)!!, 1e-9)
        assertEquals(24.0, ProgramGenerator.dumbbellCapKg("Goblet Squat", barbellAndDumbbells)!!, 1e-9)
        // Bodyweight work needs no dumbbell.
        assertNull(ProgramGenerator.dumbbellCapKg("Push-up", barbellAndDumbbells))
    }

    @Test
    fun `a heavy squat load survives owning dumbbells but a heavy dumbbell row is capped`() {
        val heavy = ProgramRules.strengthProfile(
            listOf(LoggedLift("Back Squat", 150.0, 10), LoggedLift("Dumbbell Row", 40.0, 10)),
        )
        val squat = ProgramGenerator.fillLoad(byName("Back Squat"), heavy, 5, 2, barbellAndDumbbells)!!
        assertTrue("squat cut to ${squat.kg} kg", squat.kg > 100.0)
        assertEquals(5, squat.reps)
        assertFalse("squat note: ${squat.note}", squat.note.orEmpty().contains("capped"))
        val row = ProgramGenerator.fillLoad(byName("Dumbbell Row"), heavy, 10, 2, barbellAndDumbbells)!!
        assertEquals(24.0, row.kg, 1e-9)
        assertTrue("row note: ${row.note}", row.note.orEmpty().contains("capped"))
    }

    @Test
    fun `a dumbbell max of zero or less is no cap rather than an infinite rep target`() {
        val zero = dumbbellsOnly.copy(dumbbellMaxKg = 0.0)
        assertNull(ProgramGenerator.dumbbellCapKg("Dumbbell Row", zero))
        assertNull(ProgramGenerator.dumbbellCapKg("Dumbbell Row", dumbbellsOnly.copy(dumbbellMaxKg = -5.0)))
        val heavy = ProgramRules.strengthProfile(listOf(LoggedLift("Dumbbell Row", 40.0, 10)))
        val fill = ProgramGenerator.fillLoad(byName("Dumbbell Row"), heavy, 10, 2, zero)!!
        assertEquals(10, fill.reps)
        assertTrue("capped anyway: ${fill.note}", !fill.note.orEmpty().contains("capped"))
        assertTrue(fill.kg > 24.0)
    }

    // ------------------------------------------------------------ D2: Epley

    @Test
    fun `reps raised under the dumbbell cap use the true epley inverse of the implied e1rm`() {
        // Barbell Row 60 x 10 is logged, the dumbbell row is estimated from it.
        // The raised reps are the most the cap supports at the same implied
        // e1RM: epley(cap, raised + rir) must not exceed it, one more rep would.
        var checked = 0
        for (kg in listOf(40.0, 45.0, 50.0, 55.0, 60.0, 70.0)) {
            val logged = ProgramRules.strengthProfile(listOf(LoggedLift("Barbell Row", kg, 10)))
            val reps = 8
            val rir = 2
            val cap = 24.0
            val (base, _) = ProgramRules.estimatedLoadKg("Dumbbell Row", "PULL", logged, reps, rir) ?: continue
            if (base <= cap) continue
            val fill = ProgramGenerator.fillLoad(byName("Dumbbell Row"), logged, reps, rir, dumbbellsOnly)!!
            if (fill.overCap) continue
            val implied = ProgramRules.epley(base, reps + rir)
            assertTrue(
                "$kg: ${fill.reps} reps at $cap kg is worth ${ProgramRules.epley(cap, fill.reps + rir)} > $implied",
                ProgramRules.epley(cap, fill.reps + rir) <= implied + 1e-9,
            )
            if (fill.reps < ProgramGenerator.MAX_DUMBBELL_REPS) {
                assertTrue(
                    "$kg: one more rep still fits under the implied e1rm, so reps were left on the table",
                    ProgramRules.epley(cap, fill.reps + 1 + rir) > implied - 1e-9,
                )
            }
            checked++
        }
        assertTrue("no capped, rescuable dumbbell row was exercised", checked > 0)
    }

    // ------------------------------------------------- D3: small caps, backbone

    private fun patternsOf(plan: RoutinePlan): Set<MovementPattern> =
        plan.presets.flatMap { it.entries }.mapNotNull { MuscleMap.profile(it)?.pattern }.toSet()

    @Test
    fun `a strength week at a three exercise cap still hinges and presses overhead`() {
        for (kit in listOf(Equipment.FULL_GYM, barbellAndDumbbells)) {
            val plan = ProgramGenerator.week(
                ProgramRequest(TrainingFocus.STRENGTH, VolumeLevel.STANDARD, kit, daysPerWeek = 3, maxExercises = 3),
                catalogue, strength,
            )
            plan.presets.forEach { assertTrue("${it.name} holds ${it.entries.size}", it.entries.size <= 3) }
            val names = plan.presets.flatMap { it.entries }.map { it.exerciseName }
            assertTrue("no deadlift in $names", "Deadlift" in names)
            assertTrue("no overhead press in $names", "Overhead Press" in names)
            assertTrue("squat and bench keep their twice a week", names.count { it == "Back Squat" } >= 2 && names.count { it == "Bench Press" } >= 2)
            val patterns = patternsOf(plan)
            for (pattern in listOf(
                MovementPattern.SQUAT, MovementPattern.HINGE, MovementPattern.HORIZONTAL_PUSH,
                MovementPattern.HORIZONTAL_PULL, MovementPattern.VERTICAL_PUSH, MovementPattern.VERTICAL_PULL,
            )) {
                assertTrue("$pattern lost on $kit: $names", pattern in patterns)
            }
        }
    }

    @Test
    fun `a four day upper lower week at a three exercise cap pulls vertically on an upper day`() {
        for (focus in listOf(TrainingFocus.STRENGTH, TrainingFocus.MUSCLE, TrainingFocus.GENERAL)) {
            val plan = ProgramGenerator.week(
                ProgramRequest(focus, VolumeLevel.STANDARD, Equipment.FULL_GYM, daysPerWeek = 4, maxExercises = 3),
                catalogue, strength,
            )
            val uppers = plan.presets.filter { it.name.startsWith("Upper") }
            assertEquals(2, uppers.size)
            uppers.forEach { assertTrue("${it.name} holds ${it.entries.size}", it.entries.size <= 3) }
            val patterns = uppers.flatMap { it.entries }.mapNotNull { MuscleMap.profile(it)?.pattern }.toSet()
            assertTrue("$focus: no vertical pull in the uppers", MovementPattern.VERTICAL_PULL in patterns)
            assertTrue("$focus: no vertical push in the uppers", MovementPattern.VERTICAL_PUSH in patterns)
            assertTrue("$focus: no horizontal pull in the uppers", MovementPattern.HORIZONTAL_PULL in patterns)
            assertTrue("$focus: no horizontal push in the uppers", MovementPattern.HORIZONTAL_PUSH in patterns)
        }
    }

    // ------------------------------------------------ D4: main lift leads

    @Test
    fun `a strength session opens with the practised main lift, not a variant`() {
        for (focus in listOf(TrainingFocus.STRENGTH, TrainingFocus.GENERAL)) {
            val request = ProgramRequest(focus, VolumeLevel.STANDARD, Equipment.FULL_GYM)
            val lower = ProgramGenerator.session(request, SessionKind.LOWER, 2, emptyList(), catalogue, strength)!!
            assertEquals("$focus lower: ${lower.entries.map { it.exerciseName }}", "Back Squat", lower.entries[0].exerciseName)
            assertTrue("$focus: no deadlift", lower.entries.any { it.exerciseName == "Deadlift" })
            val upper = ProgramGenerator.session(request, SessionKind.UPPER, 1, emptyList(), catalogue, strength)!!
            assertEquals("$focus upper: ${upper.entries.map { it.exerciseName }}", "Bench Press", upper.entries[0].exerciseName)
            assertTrue("$focus: no overhead press", upper.entries.any { it.exerciseName == "Overhead Press" })
        }
    }

    // ------------------------------------------------ D5, D11: templates

    @Test
    fun `template days stay under the volume level's own exercise cap`() {
        for (template in ProgramTemplates.ALL) for (volume in VolumeLevel.entries) {
            val cap = ProgramRules.exerciseCap(volume, maxExercises = 8)
            val plan = ProgramTemplates.build(template, volume, Equipment.FULL_GYM, catalogue, StrengthProfile(), maxExercises = 8)
            plan.presets.forEach {
                assertTrue("${template.id}/$volume ${it.name} holds ${it.entries.size} > $cap", it.entries.size <= cap)
            }
        }
    }

    @Test
    fun `a novice template never gets a bodyweight progression more than a tier past their age`() {
        val hard = ProgramTemplate(
            id = "t", split = TrainingSplit.FULL_BODY, authoredVolume = VolumeLevel.LOW,
            focus = TrainingFocus.MUSCLE, name = "t", summary = "",
            days = listOf(
                PlannedPreset(
                    "Core", "", 1,
                    listOf(
                        PlannedEntry("Dragon Flag", 3, 6, null),
                        PlannedEntry("Back Extension", 3, 10, null),
                    ),
                ),
            ),
        )
        assertTrue("fixture: Dragon Flag should be past the novice ceiling", MovementDifficulty.tier("Dragon Flag") > 3)
        val kit = Equipment(fullGym = false, gear = setOf(Gear.BENCH))
        val plan = ProgramTemplates.build(hard, VolumeLevel.LOW, kit, catalogue, StrengthProfile())
        val names = plan.presets.flatMap { it.entries }.map { it.exerciseName }
        assertFalse("novice got $names", "Dragon Flag" in names)
        val standard = ProgramTemplates.build(hard, VolumeLevel.HIGH, kit, catalogue, StrengthProfile())
        assertTrue(
            "a high level keeps the progression: ${standard.presets.flatMap { it.entries }.map { it.exerciseName }}",
            "Dragon Flag" in standard.presets.flatMap { it.entries }.map { it.exerciseName },
        )
    }

    // ------------------------------------------------ D6: capped reps in improve

    private fun improveOne(
        entry: PlannedEntry,
        request: ProgramRequest,
        strength: StrengthProfile = StrengthProfile(),
    ): Improvement = ProgramGenerator.improve(
        PlannedPreset("Pull", "", 1, listOf(entry)), emptyList(), request, catalogue, strength,
    )

    @Test
    fun `improve keeps the reps a dumbbell cap raised`() {
        val request = ProgramRequest(TrainingFocus.STRENGTH, VolumeLevel.STANDARD, dumbbellsOnly)
        val capped = PlannedEntry(
            "Dumbbell Row", 3, 14, 24.0,
            loadNote = "capped at your 24 kg dumbbell; reps raised to stay near failure - Lopez 2021",
        )
        val result = improveOne(capped, request)
        val row = result.after.entries.first { it.exerciseName == "Dumbbell Row" }
        assertEquals(14, row.reps)
        assertEquals(24.0, row.targetWeightKg!!, 1e-9)
        assertTrue(
            "rep change reported: ${result.changes}",
            result.changes.none { it.exerciseName == "Dumbbell Row" && it.detail.startsWith("Reps set") },
        )
        // The same reps on an uncapped kit are still pulled into the goal's range.
        val gym = improveOne(capped, request.copy(equipment = Equipment.FULL_GYM))
        assertEquals(5, gym.after.entries.first { it.exerciseName == "Dumbbell Row" }.reps)
    }

    @Test
    fun `improve fills a capped load with the raised reps and is idempotent`() {
        val request = ProgramRequest(TrainingFocus.STRENGTH, VolumeLevel.STANDARD, dumbbellsOnly)
        val logged = ProgramRules.strengthProfile(listOf(LoggedLift("Dumbbell Row", 30.0, 10)))
        val result = improveOne(PlannedEntry("Dumbbell Row", 3, 5, null), request, logged)
        val row = result.after.entries.first { it.exerciseName == "Dumbbell Row" }
        assertEquals(24.0, row.targetWeightKg!!, 1e-9)
        assertTrue("reps not raised under the cap: ${row.reps}", row.reps > 5)
        val again = ProgramGenerator.improve(result.after, emptyList(), request, catalogue, logged)
        assertEquals(result.after, again.after)
    }

    @Test
    fun `an exercise improve adds under the dumbbell cap keeps the reps the cap raised`() {
        val request = ProgramRequest(TrainingFocus.STRENGTH, VolumeLevel.STANDARD, dumbbellsOnly)
        val logged = ProgramRules.strengthProfile(listOf(LoggedLift("Dumbbell Pullover", 30.0, 10)))
        val result = improveOne(PlannedEntry("Dumbbell Row", 3, 5, null), request, logged)
        val raise = result.after.entries.firstOrNull { it.exerciseName == "Dumbbell Pullover" }
        assertNotNull("no dumbbell pullover was added: ${result.changes}", raise)
        assertEquals(24.0, raise!!.targetWeightKg!!, 1e-9)
        assertTrue("reps clamped back to ${raise.reps} under a raised-reps note: ${raise.loadNote}", raise.reps > 12)
        val again = ProgramGenerator.improve(result.after, emptyList(), request, catalogue, logged)
        assertEquals(result.after, again.after)
    }

    // ------------------------------------------------ D7: push/pull fit in improve

    @Test
    fun `improving a push preset never adds a pull and a pull preset never adds a press`() {
        val request = ProgramRequest(TrainingFocus.MUSCLE, VolumeLevel.STANDARD, Equipment.FULL_GYM)
        val push = PlannedPreset(
            "Push", "", 1,
            listOf(PlannedEntry("Bench Press", 3, 8, null), PlannedEntry("Lateral Raise", 3, 12, null)),
        )
        val pushed = ProgramGenerator.improve(push, emptyList(), request, catalogue, strength)
        pushed.after.entries.forEach {
            assertTrue("push preset gained a ${groupOf(it.exerciseName)}: ${it.exerciseName}", groupOf(it.exerciseName) != MuscleGroup.PULL)
        }
        val pull = PlannedPreset(
            "Pull", "", 1,
            listOf(PlannedEntry("Barbell Row", 3, 8, null), PlannedEntry("Lat Pulldown", 3, 10, null)),
        )
        val pulled = ProgramGenerator.improve(pull, emptyList(), request, catalogue, strength)
        pulled.after.entries.forEach {
            assertTrue("pull preset gained a ${groupOf(it.exerciseName)}: ${it.exerciseName}", groupOf(it.exerciseName) != MuscleGroup.PUSH)
        }
    }

    // ------------------------------------------------ D8: compounds first

    private fun planned(vararg names: String) = names.map { PlannedEntry(it, 3, 8, null) }

    @Test
    fun `a pull-up separator is a compound, not an isolation moved ahead of the compounds`() {
        val spaced = ProgramGenerator.spacePullUps(planned("Pull-up", "Chin-up", "Bicep Curl", "Barbell Row"))
        assertEquals(listOf("Pull-up", "Barbell Row", "Chin-up", "Bicep Curl"), spaced.map { it.exerciseName })
        // With nothing but isolation to split them, one still does the job.
        val bare = ProgramGenerator.spacePullUps(planned("Pull-up", "Chin-up", "Bicep Curl"))
        assertEquals(listOf("Pull-up", "Bicep Curl", "Chin-up"), bare.map { it.exerciseName })
    }

    @Test
    fun `compounds come before single-joint work and keep their order`() {
        val sorted = ProgramGenerator.compoundsFirst(planned("Bicep Curl", "Barbell Row", "Lateral Raise", "Back Squat", "Plank"))
        assertEquals(listOf("Barbell Row", "Back Squat", "Bicep Curl", "Lateral Raise", "Plank"), sorted.map { it.exerciseName })
    }

    @Test
    fun `generated sessions never put a compound after single-joint work`() {
        val kits = listOf(Equipment.FULL_GYM, barbellAndDumbbells, Equipment.NOTHING)
        var checked = 0
        for (kit in kits) for (focus in TrainingFocus.entries) for ((split, days) in TrainingSplit.OPTIONS) {
            val plan = ProgramGenerator.week(
                ProgramRequest(focus, VolumeLevel.STANDARD, kit, days, split = split, maxExercises = 8), catalogue, strength,
            )
            for (preset in plan.presets) {
                val firstSingleJoint = preset.entries.indexOfFirst { MuscleMap.profile(it)?.compound == false }
                if (firstSingleJoint < 0) continue
                // Pull-ups keep their spacing even where only isolation can split them.
                val late = preset.entries.drop(firstSingleJoint + 1)
                    .filter { MuscleMap.profile(it)?.compound != false && !ProgramGenerator.isPullUpVariant(it) }
                assertTrue(
                    "$kit/$focus/$split ${preset.name}: ${preset.entries.map { it.exerciseName }}",
                    late.isEmpty(),
                )
                checked++
            }
        }
        assertTrue(checked > 0)
    }

    // ------------------------------------------------ D9: compound only duplicates

    @Test
    fun `improve with compound only never lists a movement twice`() {
        val request = ProgramRequest(TrainingFocus.MUSCLE, VolumeLevel.STANDARD, Equipment.FULL_GYM, compoundOnly = true)
        val names = listOf(
            "Bench Press", "Barbell Row", "Back Squat", "Overhead Press", "Lat Pulldown", "Leg Press",
            "Dumbbell Row", "Incline Bench Press", "Cable Fly", "Pec Deck", "Lateral Raise", "Bicep Curl",
            "Triceps Pushdown", "Leg Extension", "Seated Leg Curl", "Face Pull", "Reverse Fly", "Standing Calf Raise",
        )
        for (a in names) for (b in names) {
            if (a == b) continue
            // At the set ceiling, so the redundancy pass cannot hide a duplicate.
            val preset = PlannedPreset("P", "", 1, listOf(PlannedEntry(a, 5, 8, null), PlannedEntry(b, 5, 8, null)))
            val after = ProgramGenerator.improve(preset, emptyList(), request, catalogue, strength).after
            val after2 = after.entries.map { it.exerciseName.lowercase() }
            assertEquals("$a + $b -> $after2", after2.distinct(), after2)
        }
    }

    // ------------------------------------------------ D10: variant already taken

    @Test
    fun `a capped movement keeps itself when its harder variant is already in the session`() {
        val heavy = ProgramRules.strengthProfile(listOf(LoggedLift("Goblet Squat", 60.0, 10)))
        val light = Equipment(fullGym = false, gear = setOf(Gear.DUMBBELLS), dumbbellMaxKg = 8.0)
        val pool = ProgramGenerator.eligible(catalogue, light, TrainingFocus.MUSCLE)
        val goblet = pool.first { it.name == "Goblet Squat" }
        val swapped = ProgramGenerator.fillWithCap(goblet, pool, heavy, 10, 2, light)
        assertEquals("Bulgarian Split Squat", swapped.first.name)
        val kept = ProgramGenerator.fillWithCap(goblet, pool, heavy, 10, 2, light, taken = setOf("Bulgarian Split Squat"))
        assertEquals("Goblet Squat", kept.first.name)
        assertTrue(kept.second!!.note.orEmpty().contains("capped"))
    }

    // ------------------------------------------------ D21: a custom row with a catalogue name

    @Test
    fun `a custom row that shares a catalogue name is still prescribable, a truly custom one is not`() {
        try {
            CustomExercises.sync(
                listOf(
                    CustomExercises.Row("Push-up", MuscleGroup.PUSH, "", emptySet(), mapOf(Muscle.MID_CHEST to 1.0)),
                    CustomExercises.Row("Zottman Whatever", MuscleGroup.PUSH, "", emptySet(), mapOf(Muscle.MID_CHEST to 1.0)),
                ),
            )
            val custom = catalogue + Exercise(name = "Zottman Whatever", muscleGroup = MuscleGroup.PUSH, isWeighted = false)
            val names = ProgramGenerator.eligible(custom, Equipment.NOTHING, TrainingFocus.MUSCLE).map { it.name }
            assertTrue("Push-up shadowed by a custom row: $names", "Push-up" in names)
            assertFalse("Zottman Whatever" in names)
        } finally {
            CustomExercises.reset()
        }
    }

    // ------------------------------------------------ D13: honest notes

    @Test
    fun `an empty pool and dropped days are explained in the routine note`() {
        val request = ProgramRequest(
            TrainingFocus.MUSCLE, VolumeLevel.STANDARD, Equipment.FULL_GYM,
            daysPerWeek = 6, split = TrainingSplit.PUSH_PULL_LEGS,
        )
        val none = ProgramGenerator.week(request, emptyList(), strength)
        assertTrue(none.presets.isEmpty())
        assertEquals(ProgramGenerator.NOTHING_TO_PLAN_NOTE, none.note)

        val legsOnly = listOf(byName("Back Squat"), byName("Romanian Deadlift"))
        val partial = ProgramGenerator.week(request, legsOnly, strength)
        assertEquals(2, partial.presets.size)
        assertTrue("note: ${partial.note}", "Only 2 of 6 days" in partial.note)
    }

    // ------------------------------------------------ D14: fitToRange over budget

    private fun day(vararg names: String, sets: Int = 2) =
        PlannedPreset("D", "", 1, names.map { PlannedEntry(it, sets, 5, null) })

    private val bigLifts = arrayOf(
        "Back Squat", "Deadlift", "Bench Press", "Overhead Press", "Barbell Row", "Front Squat",
        "Romanian Deadlift", "Pull-up", "Incline Bench Press", "Hip Thrust", "Close-Grip Bench Press", "Sumo Deadlift",
    )

    @Test
    fun `a day over the budget at two sets each drops its last single-joint entry`() {
        val names = bigLifts + arrayOf("Bicep Curl", "Lateral Raise")
        val preset = day(*names)
        val limit = ProgramRules.SESSION_BUDGET_SECONDS
        assertTrue(ProgramRules.sessionSeconds(preset.entries, TrainingFocus.STRENGTH) > limit)
        val fitted = ProgramTemplates.fitToRange(
            listOf(preset), listOf(names.map { 5 }), TrainingFocus.STRENGTH, 5.0..15.0,
        ).single()
        // The compounds stay; single-joint work goes, last first, until the day fits.
        assertEquals(bigLifts.toList(), fitted.entries.map { it.exerciseName }.take(bigLifts.size))
        assertTrue(fitted.entries.size < names.size)
        assertTrue(ProgramRules.sessionSeconds(fitted.entries, TrainingFocus.STRENGTH) <= limit || fitted.note.contains("Runs past"))
    }

    @Test
    fun `a day of compounds only that cannot fit says it runs over`() {
        val preset = day(*bigLifts)
        assertTrue(ProgramRules.sessionSeconds(preset.entries, TrainingFocus.STRENGTH) > ProgramRules.SESSION_BUDGET_SECONDS)
        val fitted = ProgramTemplates.fitToRange(
            listOf(preset), listOf(bigLifts.map { 5 }), TrainingFocus.STRENGTH, 5.0..15.0,
        ).single()
        assertEquals(bigLifts.size, fitted.entries.size)
        assertTrue("note: ${fitted.note}", "Runs past 75 minutes" in fitted.note)
    }

    // ------------------------------------------------ D15: fill steps

    @Test
    fun `a six day high week with three priorities reaches its targets or names the shortfall`() {
        val priorities = setOf(MuscleArea.CHEST, MuscleArea.BACK, MuscleArea.ARMS)
        for (kit in listOf(Equipment.FULL_GYM, barbellAndDumbbells)) for (focus in TrainingFocus.entries) {
            val request = ProgramRequest(
                focus, VolumeLevel.HIGH, kit, daysPerWeek = 6, split = TrainingSplit.PUSH_PULL_LEGS,
                priorities = priorities, maxExercises = 8,
            )
            val plan = ProgramGenerator.week(request, catalogue, strength)
            val volume = ProgramRules.weeklyVolume(plan.presets)
            val range = ProgramRules.weeklySetTarget(VolumeLevel.HIGH, focus)
            val priorityMuscles = priorities.flatMap { it.muscles }.toSet()
            for (muscle in ProgramRules.TRACKED) {
                val target = if (muscle in priorityMuscles) range.endInclusive else range.start
                val sets = volume[muscle] ?: 0.0
                // Priorities aim high but are only judged against the floor.
                if (sets >= range.start - 1e-9) continue
                assertTrue(
                    "$kit/$focus: ${muscle.label} at $sets of $target, note: ${plan.note}",
                    muscle.label.lowercase() in plan.note,
                )
            }
        }
    }

    // ------------------------------------------------ D17, D18: gear rows

    @Test
    fun `a landmine t-bar row is a barbell movement, not a machine`() {
        val barbell = Equipment(fullGym = false, gear = setOf(Gear.BARBELL))
        assertTrue(GearRequirements.allows("T-Bar Row", true, barbell))
        assertFalse(GearRequirements.allows("T-Bar Row", true, Equipment(fullGym = false, gear = setOf(Gear.DUMBBELLS))))
        assertTrue(GearRequirements.fits("T-Bar Row", true, "", barbell))
        assertTrue("T-Bar Row" in ProgramGenerator.eligible(catalogue, barbell, TrainingFocus.STRENGTH).map { it.name })
        // The rest of the machine rule is untouched.
        assertFalse(GearRequirements.allows("Leg Press", true, barbell))
        assertFalse(GearRequirements.allows("Assisted Pull-up", false, barbell))
        assertTrue(GearRequirements.allows("Leg Press", true, Equipment.FULL_GYM))
    }

    @Test
    fun `the dragon flag needs a bench`() {
        assertFalse(GearRequirements.allows("Dragon Flag", false, Equipment.NOTHING))
        assertTrue(GearRequirements.allows("Dragon Flag", false, Equipment(fullGym = false, gear = setOf(Gear.BENCH))))
        assertNotNull(GearRequirements.needs("Dragon Flag").singleOrNull())
    }
}
