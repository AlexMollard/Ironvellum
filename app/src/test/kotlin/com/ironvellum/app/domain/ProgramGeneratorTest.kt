package com.ironvellum.app.domain

import com.ironvellum.app.data.Seed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The generator against the REAL seed catalogue: the design doc's acceptance
 * examples, the ported RoutineBuilder invariants (equipment, milestones,
 * balance, redundancy, rest days, adjacency), and the improve pass with its
 * idempotence guarantee.
 */
class ProgramGeneratorTest {

    private val catalogue: List<Exercise> = Seed.exercises.map {
        Exercise(
            name = it.name,
            muscleGroup = MuscleGroup.valueOf(it.muscleGroup),
            isWeighted = it.isWeighted,
            metric = ExerciseMetric.valueOf(it.metric),
            category = it.category,
        )
    }

    /** Bench Press 75 kg x 10 -> e1RM 100 kg; Back Squat 80 x 10 -> 106.67. */
    private val strength = ProgramRules.strengthProfile(
        listOf(
            LoggedLift("Bench Press", 75.0, 10),
            LoggedLift("Back Squat", 80.0, 10),
            LoggedLift("Barbell Row", 60.0, 10),
        ),
    )

    private fun byName(name: String): Exercise = catalogue.first { it.name == name }

    private fun isMachine(name: String): Boolean =
        name.trim().lowercase().startsWith("assisted") ||
            MovementDifficulty.loadFactor(name) < MovementDifficulty.FREE_WEIGHT_LOAD

    private fun allPlans(): List<RoutinePlan> =
        EquipmentAccess.entries.flatMap { eq ->
            TrainingFocus.entries.map { focus ->
                ProgramGenerator.week(
                    ProgramRequest(focus, ExperienceTier.INTERMEDIATE, eq, daysPerWeek = 4),
                    catalogue,
                    strength,
                )
            }
        } + (2..6).map { days ->
            ProgramGenerator.week(
                ProgramRequest(TrainingFocus.GENERAL, ExperienceTier.ADVANCED, EquipmentAccess.FULL_GYM, days),
                catalogue,
                strength,
            )
        }

    private fun entriesOf(plan: RoutinePlan) = plan.presets.flatMap { it.entries }

    private fun volumeOf(plan: RoutinePlan) = ProgramRules.weeklyVolume(plan.presets)

    // ------------------------------------------------- acceptance example 1

    @Test
    fun `three days that cannot fit the intermediate dose name every short muscle instead of cramming`() {
        // Three full-body days share twelve muscles; inside the session time
        // budget they cannot all reach 12 sets. The plan must stay finishable
        // and say which muscles land short, rather than silently padding.
        val request = ProgramRequest(
            TrainingFocus.MUSCLE, ExperienceTier.INTERMEDIATE, EquipmentAccess.FULL_GYM,
            daysPerWeek = 3, priorities = setOf(MuscleArea.ARMS),
        )
        val plan = ProgramGenerator.week(request, catalogue, strength)
        assertEquals(listOf(1, 3, 5), plan.presets.map { it.scheduledDay })
        plan.presets.forEach { preset ->
            val seconds = ProgramRules.sessionSeconds(preset.entries, TrainingFocus.MUSCLE)
            assertTrue(
                "${preset.name} runs ${seconds / 60} min",
                seconds <= ProgramRules.SESSION_BUDGET_SECONDS,
            )
        }
        val volume = volumeOf(plan)
        val range = ProgramRules.weeklySetTarget(ExperienceTier.INTERMEDIATE, TrainingFocus.MUSCLE)
        val short = ProgramRules.TRACKED.filter { (volume[it] ?: 0.0) < range.start - 0.5 }
        assertTrue("expected a capacity-limited week", short.isNotEmpty())
        val note = plan.presets.first().note
        short.forEach { muscle ->
            assertTrue("note does not name ${muscle.label}: $note", muscle.label.lowercase() in note)
        }
        // Short, but spread fairly: nothing starved while others overflow.
        ProgramRules.TRACKED.forEach { muscle ->
            assertTrue("$muscle starved at ${volume[muscle]}", (volume[muscle] ?: 0.0) >= 0.7 * range.start)
            assertTrue("$muscle over at ${volume[muscle]}", (volume[muscle] ?: 0.0) <= range.endInclusive)
        }
        // The prioritised arms out-volume every unprioritised upper muscle.
        val arms = minOf(volume[Muscle.BICEPS]!!, volume[Muscle.TRICEPS]!!)
        listOf(Muscle.CHEST, Muscle.SIDE_DELTS, Muscle.REAR_DELTS).forEach {
            assertTrue("arms $arms did not out-volume $it ${volume[it]}", arms > (volume[it] ?: 0.0))
        }
    }

    @Test
    fun `every muscle a generated week leaves under the floor is named in its note`() {
        // The coverage map calls any muscle below the floor UNDER, even 11.6
        // of 12; a plan that shows UNDER there without saying so here reads
        // as a generator bug rather than an honest capacity limit.
        for (tier in ExperienceTier.entries) {
            for (equipment in EquipmentAccess.entries) {
                for (days in 2..6) {
                    val plan = ProgramGenerator.week(
                        ProgramRequest(TrainingFocus.MUSCLE, tier, equipment, days), catalogue, strength,
                    )
                    val volume = volumeOf(plan)
                    val floor = ProgramRules.weeklySetTarget(tier, TrainingFocus.MUSCLE).start
                    val note = plan.presets.first().note
                    ProgramRules.TRACKED.filter { (volume[it] ?: 0.0) < floor }.forEach { muscle ->
                        assertTrue(
                            "$tier $equipment ${days}d: ${muscle.label} at ${volume[muscle]} not in note: $note",
                            muscle.label.lowercase() in note,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `four intermediate days fill the upper muscles inside the session time budget`() {
        // The session limit is clock time, not a set count: isolation sets
        // rest 90 s where compounds rest 150 s. A flat 24-set cap left five
        // upper muscles at 10.5-11 sets while the lower days ended early.
        val request = ProgramRequest(
            TrainingFocus.MUSCLE, ExperienceTier.INTERMEDIATE, EquipmentAccess.FULL_GYM, 4,
        )
        val plan = ProgramGenerator.week(request, catalogue, strength)
        plan.presets.forEach { preset ->
            val seconds = ProgramRules.sessionSeconds(preset.entries, TrainingFocus.MUSCLE)
            assertTrue("${preset.name} runs ${seconds / 60} min", seconds <= ProgramRules.SESSION_BUDGET_SECONDS)
        }
        val volume = volumeOf(plan)
        val short = ProgramRules.TRACKED.filter { (volume[it] ?: 0.0) < 11.0 }
        assertTrue("under 11 sets: ${short.map { it to volume[it] }}", short.isEmpty())
    }

    @Test
    fun `push days carry no pulling and pull days carry no pressing`() {
        val plan = ProgramGenerator.week(
            ProgramRequest(TrainingFocus.MUSCLE, ExperienceTier.ADVANCED, EquipmentAccess.FULL_GYM, 6),
            catalogue, strength,
        )
        val forbidden = mapOf("Push" to MuscleGroup.PULL, "Pull" to MuscleGroup.PUSH)
        plan.presets.forEach { preset ->
            val banned = forbidden.entries.firstOrNull { preset.name.startsWith(it.key) }?.value ?: return@forEach
            preset.entries.forEach { entry ->
                assertFalse(
                    "${entry.exerciseName} on ${preset.name}",
                    byName(entry.exerciseName).muscleGroup == banned,
                )
            }
        }
    }

    @Test
    fun `intermediate muscle five days with an arms priority lands every tracked muscle in range`() {
        val request = ProgramRequest(
            TrainingFocus.MUSCLE, ExperienceTier.INTERMEDIATE, EquipmentAccess.FULL_GYM,
            daysPerWeek = 5, priorities = setOf(MuscleArea.ARMS),
        )
        val plan = ProgramGenerator.week(request, catalogue, strength)
        assertEquals(5, plan.presets.size)

        val volume = volumeOf(plan)
        val range = ProgramRules.weeklySetTarget(ExperienceTier.INTERMEDIATE, TrainingFocus.MUSCLE)
        for (muscle in ProgramRules.TRACKED) {
            val sets = volume[muscle] ?: 0.0
            // Half a set of slack below the floor: fractional counting of
            // 0.5-share movements cannot land every muscle on an integer.
            assertTrue(
                "$muscle at $sets fractional sets/week, outside $range",
                sets >= range.start - 0.5 && sets <= range.endInclusive,
            )
        }
        // Priorities are pushed toward the TOP of the range, not just inside it.
        assertTrue("biceps ${(volume[Muscle.BICEPS])} not near the top", volume[Muscle.BICEPS]!! >= 15.0)
        assertTrue("triceps ${(volume[Muscle.TRICEPS])} not near the top", volume[Muscle.TRICEPS]!! >= 15.0)

        // Hamstrings come from dedicated long-length work, never a squat.
        val entries = entriesOf(plan)
        assertTrue(
            "no dedicated hamstring movement",
            entries.any {
                (MuscleMap.profile(it.exerciseName)?.muscles?.get(Muscle.HAMSTRINGS) ?: 0.0) >= 1.0 &&
                    setOf("Romanian Deadlift", "Seated Leg Curl").contains(it.exerciseName)
            },
        )
        entries.forEach { entry ->
            val profile = MuscleMap.profile(entry.exerciseName) ?: return@forEach
            if (profile.pattern == MovementPattern.SQUAT) {
                assertEquals(
                    "${entry.exerciseName} credits hamstrings from a squat",
                    0.0,
                    profile.muscles[Muscle.HAMSTRINGS] ?: 0.0,
                    1e-9,
                )
            }
        }
        // The long-length twin wins the triceps isolation slot.
        assertFalse(
            "Triceps Pushdown (short-length) beat a long-length alternative",
            entries.any { it.exerciseName == "Triceps Pushdown" },
        )
        // Assisted variants are regressions for people who need them, never
        // picks for a full-gym intermediate.
        assertFalse(
            "Assisted variant prescribed in a full-gym plan",
            entries.any { it.exerciseName.trim().lowercase().startsWith("assisted") },
        )
        // Citations must match the muscle: Maeo 2021 measured HAMSTRINGS.
        entries.forEach { entry ->
            val profile = MuscleMap.profile(entry.exerciseName)
            if ((profile?.muscles?.get(Muscle.HAMSTRINGS) ?: 0.0) < 0.5) {
                assertFalse(
                    "${entry.exerciseName} cites the hamstring trial (Maeo 2021): ${entry.why}",
                    entry.why.contains("Maeo 2021"),
                )
            }
        }
        // Grammar: no "1 sets", and lists join with "and".
        entries.forEach { entry ->
            assertFalse(
                "grammar slip in ${entry.exerciseName}: ${entry.why}",
                entry.why.contains("1 sets"),
            )
        }
        // Placement: fills land on days whose role trains the region.
        plan.presets.forEach { preset ->
            val upperDay = listOf("Upper", "Push", "Pull").any { preset.name.startsWith(it) }
            preset.entries.forEach { entry ->
                val group = byName(entry.exerciseName).muscleGroup
                if (upperDay) {
                    assertFalse("leg work ${entry.exerciseName} on ${preset.name}", group == MuscleGroup.LEGS)
                } else {
                    assertFalse(
                        "upper work ${entry.exerciseName} on ${preset.name}",
                        group == MuscleGroup.PUSH || group == MuscleGroup.PULL,
                    )
                }
            }
        }
        // Ordinals count per role, not one alphabet across the week.
        assertEquals(
            listOf("Push A", "Pull A", "Legs A", "Upper A", "Lower A"),
            plan.presets.map { it.name },
        )
        val fourDay = ProgramGenerator.week(request.copy(daysPerWeek = 4), catalogue, strength)
        assertEquals(listOf("Upper A", "Lower A", "Upper B", "Lower B"), fourDay.presets.map { it.name })

        // Reps sit in the 6-15 hypertrophy band; every entry explains itself.
        entries.forEach { entry ->
            assertTrue("${entry.exerciseName} reps ${entry.reps}", entry.reps in 6..15)
            assertTrue("${entry.exerciseName} has no why", entry.why.isNotBlank())
            assertTrue("${entry.exerciseName} sets ${entry.sets}", entry.sets in 2..5)
        }
        // Deficit fills say WHY in plain language: which muscle was short, by
        // how much, and the citation behind the pick.
        val deficitWhy = entries.first { it.why.contains("sets short") }
        assertTrue(
            "deficit why never names the muscle: ${deficitWhy.why}",
            ProgramRules.TRACKED.any { deficitWhy.why.contains(it.label.lowercase()) },
        )
        assertTrue(
            "deficit why carries no citation: ${deficitWhy.why}",
            Evidence.entries.any { deficitWhy.why.contains(it.label) },
        )

        // Loads come from the lifter's own PRs: bench e1RM 100 -> 8 reps at
        // 2 RIR = 66.67 -> floored to the 2.5 kg step.
        val bench = entries.first { it.exerciseName == "Bench Press" }
        assertEquals(65.0, bench.targetWeightKg!!, 1e-9)
        assertTrue(bench.loadNote!!.contains("e1RM"))
    }

    @Test
    fun `improve raises sets on a covered muscle the week leaves short and stays idempotent`() {
        val request = ProgramRequest(
            TrainingFocus.MUSCLE, ExperienceTier.INTERMEDIATE, EquipmentAccess.FULL_GYM,
        )
        val lower = PlannedPreset("Lower", "", 2, listOf(
            PlannedEntry("Back Squat", 3, 8, null),
            PlannedEntry("Romanian Deadlift", 3, 8, null),
            PlannedEntry("Standing Calf Raise", 2, 12, null),
        ))
        // The rest of the week serves every lower muscle except calves (8).
        val restOfWeek = listOf(PlannedPreset("Other", "", 4, listOf(
            PlannedEntry("Leg Press", 9, 10, null),
            PlannedEntry("Seated Leg Curl", 9, 12, null),
            PlannedEntry("Hip Thrust", 9, 10, null),
            PlannedEntry("Standing Calf Raise", 8, 12, null),
            PlannedEntry("Hanging Knee Raise", 12, 12, null),
        )))
        val improvement = ProgramGenerator.improve(lower, restOfWeek, request, catalogue, strength)
        val calf = improvement.after.entries.first { it.exerciseName == "Standing Calf Raise" }
        // 8 + 2 = 10 of a 12-set minimum: two more sets on the calf raise.
        assertEquals(4, calf.sets)
        assertTrue(
            "no set change explained: ${improvement.changes}",
            improvement.changes.any {
                it.kind == PlanChange.Kind.ADJUSTED && it.exerciseName == "Standing Calf Raise" &&
                    "calves" in it.detail
            },
        )
        val second = ProgramGenerator.improve(improvement.after, restOfWeek, request, catalogue, strength)
        assertTrue("second improve changed: ${second.changes}", second.changes.isEmpty())
    }

    // ------------------------------------------------- acceptance example 2

    @Test
    fun `beginner strength at home practises the main lifts full body`() {
        val request = ProgramRequest(
            TrainingFocus.STRENGTH, ExperienceTier.BEGINNER, EquipmentAccess.HOME_WEIGHTS,
            daysPerWeek = 3,
        )
        val plan = ProgramGenerator.week(request, catalogue, strength)
        assertEquals(3, plan.presets.size)
        assertEquals(listOf(1, 3, 5), plan.presets.mapNotNull { it.scheduledDay })

        val entries = entriesOf(plan)
        val names = entries.map { it.exerciseName }.toSet()
        // Free weights are available at home, so the lifts themselves, not
        // substitutes, are practised (Buckner 2017).
        listOf("Back Squat", "Bench Press", "Deadlift", "Overhead Press").forEach { lift ->
            assertTrue("$lift missing from a home-gym strength week", lift in names)
        }
        val daysWith = { lift: String ->
            plan.presets.count { day -> day.entries.any { it.exerciseName == lift } }
        }
        assertTrue("squat ${daysWith("Back Squat")}x/week", daysWith("Back Squat") >= 2)
        assertTrue("bench ${daysWith("Bench Press")}x/week", daysWith("Bench Press") >= 2)

        // Compounds run 3-5 reps in a strength week; isolation and core stay
        // at 8-12 (a 5-rep calf raise is poor practice - Lopez 2021), and
        // nothing is programmed to failure.
        entries.forEach { entry ->
            val compound = MuscleMap.profile(entry.exerciseName)!!.compound
            val band = if (compound) 3..5 else 8..12
            assertTrue("${entry.exerciseName} reps ${entry.reps} outside $band", entry.reps in band)
            assertTrue("${entry.exerciseName} sets ${entry.sets}", entry.sets in 2..5)
        }
        assertTrue(
            "note carries no rest guidance: ${plan.presets.first().note}",
            plan.presets.first().note.contains("3-5 min") && plan.presets.first().note.contains("reps in reserve"),
        )
        // Small pool: linear progression on few lifts (ACSM 2009).
        assertTrue(
            "pool too large for a beginner: $names",
            names.size <= 14,
        )
        // Nothing to failure: 2 RIR in the note, and every entry carries a why.
        entries.forEach { assertTrue(it.why.isNotBlank()) }
    }

    // ------------------------------------------------- acceptance example 4

    @Test
    fun `auto session builds around the most under-served tracked muscles`() {
        val request = ProgramRequest(
            TrainingFocus.MUSCLE, ExperienceTier.INTERMEDIATE, EquipmentAccess.FULL_GYM,
        )
        // A week that serves chest in full and everything else partly,
        // except hamstrings (nearly untrained) and calves (untrained).
        val existingWeek = listOf(
            PlannedPreset("Upper", "", 1, listOf(
                PlannedEntry("Bench Press", 6, 8, null),
                PlannedEntry("Cable Fly", 6, 12, null),
                PlannedEntry("Overhead Press", 6, 8, null),
                PlannedEntry("Barbell Row", 6, 8, null),
                PlannedEntry("Lat Pulldown", 6, 10, null),
                PlannedEntry("Lateral Raise", 6, 12, null),
            )),
            PlannedPreset("Lower", "", 2, listOf(
                PlannedEntry("Leg Press", 6, 10, null),
                PlannedEntry("Hip Thrust", 6, 10, null),
            )),
        )
        val volume = ProgramRules.weeklyVolume(existingWeek)
        assertNull("calves were supposed to be untrained", volume[Muscle.CALVES])
        assertTrue("fixture broken: hamstrings served", (volume[Muscle.HAMSTRINGS] ?: 0.0) < 6.0)
        assertTrue("fixture broken: chest short", volume[Muscle.CHEST]!! >= 12.0)

        val session = ProgramGenerator.session(
            request, SessionKind.AUTO, scheduledDay = 6,
            existingWeek = existingWeek, catalogue = catalogue, strength = strength,
        )
        assertNotNull(session)
        val entries = session!!.entries
        assertTrue(entries.isNotEmpty())
        // Furthest under target first: the untrained calves lead.
        assertTrue(
            "AUTO did not lead with calves: ${entries.first().exerciseName}",
            (MuscleMap.profile(entries.first().exerciseName)?.muscles?.get(Muscle.CALVES) ?: 0.0) >= 0.5,
        )
        assertTrue(
            "no hamstring work in the AUTO session",
            entries.any { (MuscleMap.profile(it.exerciseName)?.muscles?.get(Muscle.HAMSTRINGS) ?: 0.0) >= 0.5 },
        )
        assertFalse(
            "AUTO session re-trained chest although the week already covers it",
            entries.any { (MuscleMap.profile(it.exerciseName)?.muscles?.get(Muscle.CHEST) ?: 0.0) >= 0.5 },
        )
    }

    @Test
    fun `auto session tops up a real generated week that sits just under target`() {
        // Four intermediate days leave the upper muscles 1-1.5 sets short of
        // 12. Measuring a per-session share against the weekly volume called
        // that week complete and built nothing ("cannot fill this workout").
        val request = ProgramRequest(
            TrainingFocus.MUSCLE, ExperienceTier.INTERMEDIATE, EquipmentAccess.FULL_GYM, 4,
        )
        val week = ProgramGenerator.week(request, catalogue, strength).presets
        val before = ProgramRules.weeklyVolume(week)
        val range = ProgramRules.weeklySetTarget(ExperienceTier.INTERMEDIATE, TrainingFocus.MUSCLE)
        val short = ProgramRules.TRACKED.filter { (before[it] ?: 0.0) < range.start }
        assertTrue("fixture broken: the 4-day week is complete", short.isNotEmpty())

        val session = ProgramGenerator.session(request, SessionKind.AUTO, 6, week, catalogue, strength)
        assertNotNull("AUTO built nothing for a week with short muscles", session)
        val after = ProgramRules.weeklyVolume(week + session!!)
        short.forEach { muscle ->
            assertTrue("$muscle not raised: ${before[muscle]} -> ${after[muscle]}", after[muscle]!! > before[muscle]!!)
        }
        ProgramRules.TRACKED.forEach { muscle ->
            assertTrue("$muscle pushed over the range: ${after[muscle]}", (after[muscle] ?: 0.0) <= range.endInclusive)
        }
    }

    @Test
    fun `auto session builds nothing when the week already meets every target`() {
        val request = ProgramRequest(
            TrainingFocus.MUSCLE, ExperienceTier.BEGINNER, EquipmentAccess.FULL_GYM, 5,
        )
        // The generated week twice over: every tracked muscle past target.
        val once = ProgramGenerator.week(request, catalogue, strength).presets
        val week = once + once
        val volume = ProgramRules.weeklyVolume(week)
        val range = ProgramRules.weeklySetTarget(ExperienceTier.BEGINNER, TrainingFocus.MUSCLE)
        assertTrue(
            "fixture broken: $volume",
            ProgramRules.TRACKED.all { (volume[it] ?: 0.0) >= range.start },
        )
        assertNull(ProgramGenerator.session(request, SessionKind.AUTO, 6, week, catalogue, strength))
    }

    // ------------------------------------- ported RoutineBuilder invariants

    @Test
    fun `bodyweight plans never prescribe a loaded movement and never run empty`() {
        for (days in 2..6) for (focus in TrainingFocus.entries) {
            val plan = ProgramGenerator.week(
                ProgramRequest(focus, ExperienceTier.INTERMEDIATE, EquipmentAccess.BODYWEIGHT, days),
                catalogue, strength,
            )
            val names = entriesOf(plan).map { it.exerciseName }
            assertTrue("plan empty for $focus/$days", names.isNotEmpty())
            names.forEach { assertFalse("loaded $it in a BODYWEIGHT plan", byName(it).isWeighted) }
        }
    }

    @Test
    fun `home weights plans never prescribe a machine`() {
        for (days in 2..6) for (focus in TrainingFocus.entries) {
            val plan = ProgramGenerator.week(
                ProgramRequest(focus, ExperienceTier.BEGINNER, EquipmentAccess.HOME_WEIGHTS, days),
                catalogue, strength,
            )
            val names = entriesOf(plan).map { it.exerciseName }
            assertTrue("plan empty for $focus/$days", names.isNotEmpty())
            names.forEach { assertFalse("machine $it in a HOME_WEIGHTS plan", isMachine(it)) }
        }
    }

    @Test
    fun `claim-standard milestone rows are never prescribed`() {
        for (plan in allPlans()) {
            entriesOf(plan).forEach { entry ->
                assertFalse(
                    "milestone ${entry.exerciseName} prescribed",
                    MovementDifficulty.isLoadPriced(entry.exerciseName),
                )
            }
        }
    }

    @Test
    fun `every plan covers push pull and legs groups`() {
        for (plan in allPlans()) {
            val groups = entriesOf(plan).map { byName(it.exerciseName).muscleGroup }.toSet()
            listOf(MuscleGroup.PUSH, MuscleGroup.PULL, MuscleGroup.LEGS).forEach { group ->
                assertTrue("plan missed $group", group in groups)
            }
        }
    }

    @Test
    fun `day count matches the request and days never collide`() {
        for (days in 2..6) {
            val plan = ProgramGenerator.week(
                ProgramRequest(TrainingFocus.MUSCLE, ExperienceTier.ADVANCED, EquipmentAccess.FULL_GYM, days),
                catalogue, strength,
            )
            assertEquals(days, plan.presets.size)
            val scheduled = plan.presets.mapNotNull { it.scheduledDay }
            assertEquals("duplicate scheduled day", scheduled.size, scheduled.distinct().size)
            scheduled.forEach { assertTrue("day $it outside ISO week", it in 1..7) }
            assertTrue("no rest day in a $days-day week", scheduled.size < 7)
        }
    }

    @Test
    fun `the week keeps a rest day before wrapping to monday`() {
        // Splits up to 5 days end the week before Sunday; the 6-day PPL x2
        // rests mid-week (Thursday) instead.
        for (days in 2..5) {
            val plan = ProgramGenerator.week(
                ProgramRequest(TrainingFocus.GENERAL, ExperienceTier.INTERMEDIATE, EquipmentAccess.FULL_GYM, days),
                catalogue, strength,
            )
            val scheduled = plan.presets.mapNotNull { it.scheduledDay }
            assertFalse("day 7 scheduled in a $days-day week", 7 in scheduled)
        }
        val six = ProgramGenerator.week(
            ProgramRequest(TrainingFocus.GENERAL, ExperienceTier.INTERMEDIATE, EquipmentAccess.FULL_GYM, 6),
            catalogue, strength,
        )
        val scheduled = six.presets.mapNotNull { it.scheduledDay }
        assertEquals(6, scheduled.size)
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7).filterNot { it in scheduled }, listOf(4))
    }

    @Test
    fun `leg days are never adjacent`() {
        for (plan in allPlans()) {
            val sorted = plan.presets.map { it.scheduledDay!! to dominantGroup(it) }.sortedBy { it.first }
            for (i in 1 until sorted.size) {
                val (previousDay, previousGroup) = sorted[i - 1]
                val (currentDay, currentGroup) = sorted[i]
                // Adjacent means consecutive ISO weekdays; 2 and 4 have the
                // Wednesday rest between them and are fine.
                if (currentDay - previousDay == 1) {
                    assertFalse(
                        "leg days adjacent on $previousDay and $currentDay",
                        previousGroup == MuscleGroup.LEGS && currentGroup == MuscleGroup.LEGS,
                    )
                }
            }
        }
    }

    /** LEGS only when it is the UNIQUE biggest group of the session; a tie is not a leg day. */
    private fun dominantGroup(preset: PlannedPreset): MuscleGroup {
        val counts = entriesOf(RoutinePlan(listOf(preset)))
            .map { byName(it.exerciseName).muscleGroup }
            .groupBy { it }
            .mapValues { it.value.size }
        val max = counts.values.maxOrNull()!!
        val leaders = counts.filterValues { it == max }.keys
        return leaders.singleOrNull()
            ?: leaders.firstOrNull { it != MuscleGroup.LEGS }
            ?: MuscleGroup.LEGS
    }

    @Test
    fun `no movement appears twice in one session`() {
        for (plan in allPlans()) {
            plan.presets.forEach { preset ->
                val names = preset.entries.map { it.exerciseName }
                assertEquals("duplicate in ${preset.name}", names.size, names.distinct().size)
            }
        }
    }

    @Test
    fun `no movement pattern loads the same muscles at two different equipment fits`() {
        // The redundancy rule is pairwise: the same pattern loading the same
        // muscles must not appear at two implement fits in one session
        // ("Bodyweight Squat beside Back Squat"). Unrelated isolation work
        // that merely shares the pattern bucket may differ.
        val fit = { name: String, equipment: EquipmentAccess ->
            val e = byName(name)
            when {
                equipment == EquipmentAccess.BODYWEIGHT -> 0
                !e.isWeighted -> 2
                MovementDifficulty.loadFactor(name) < MovementDifficulty.FREE_WEIGHT_LOAD ||
                    name.trim().lowercase().startsWith("assisted") -> 1
                else -> 0
            }
        }
        for (equipment in EquipmentAccess.entries) for (focus in TrainingFocus.entries) {
            val plan = ProgramGenerator.week(
                ProgramRequest(focus, ExperienceTier.ADVANCED, equipment, 6),
                catalogue, strength,
            )
            plan.presets.forEach { preset ->
                val entries = preset.entries
                for (i in entries.indices) for (j in i + 1 until entries.size) {
                    val a = entries[i]
                    val b = entries[j]
                    val pa = MuscleMap.profile(a.exerciseName)!!
                    val pb = MuscleMap.profile(b.exerciseName)!!
                    val overlap = pa.muscles.keys.any { (pb.muscles[it] ?: 0.0) >= 0.5 } ||
                        pb.muscles.keys.any { (pa.muscles[it] ?: 0.0) >= 0.5 }
                    if (pa.pattern == pb.pattern && overlap) {
                        assertEquals(
                            "session ${preset.name}: ${a.exerciseName} and ${b.exerciseName} " +
                                "load the same muscles at different equipment fits",
                            fit(a.exerciseName, equipment), fit(b.exerciseName, equipment),
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `a slot the catalogue cannot fill shrinks the plan honestly`() {
        // Only pressing movement is a pin-stack machine: BODYWEIGHT must drop
        // the slots rather than prescribe it.
        val fixture = listOf(
            Exercise(name = "Machine Chest Press", muscleGroup = MuscleGroup.PUSH, isWeighted = true),
            Exercise(name = "Inverted Row", muscleGroup = MuscleGroup.PULL, isWeighted = false),
            Exercise(name = "Glute Bridge", muscleGroup = MuscleGroup.LEGS, isWeighted = false),
            Exercise(name = "Hanging Knee Raise", muscleGroup = MuscleGroup.CORE, isWeighted = false),
        )
        val plan = ProgramGenerator.week(
            ProgramRequest(TrainingFocus.GENERAL, ExperienceTier.BEGINNER, EquipmentAccess.BODYWEIGHT, 4),
            fixture, StrengthProfile(emptyMap()),
        )
        val names = entriesOf(plan).map { it.exerciseName }
        assertFalse("machine slipped into a BODYWEIGHT plan", "Machine Chest Press" in names)
        assertTrue("plan shrank to nothing", "Inverted Row" in names)
    }

    @Test
    fun `stretch-biased movements win the isolation slots`() {
        val plan = ProgramGenerator.week(
            ProgramRequest(TrainingFocus.MUSCLE, ExperienceTier.INTERMEDIATE, EquipmentAccess.FULL_GYM, 4),
            catalogue, strength,
        )
        val entries = entriesOf(plan)
        assertFalse(
            "Lying Leg Curl (short length) beat a long-length alternative (Maeo 2021)",
            entries.any { it.exerciseName == "Lying Leg Curl" },
        )
        assertTrue(
            "no seated leg curl or RDL in a full-gym muscle week",
            entries.any { it.exerciseName == "Seated Leg Curl" || it.exerciseName == "Romanian Deadlift" },
        )
    }

    @Test
    fun `prioritised muscles get more volume than unprioritised ones`() {
        val prioritised = ProgramGenerator.week(
            ProgramRequest(
                TrainingFocus.MUSCLE, ExperienceTier.INTERMEDIATE, EquipmentAccess.FULL_GYM, 4,
                priorities = setOf(MuscleArea.SHOULDERS),
            ),
            catalogue, strength,
        )
        val volume = volumeOf(prioritised)
        val sideDelts = volume[Muscle.SIDE_DELTS] ?: 0.0
        val chest = volume[Muscle.CHEST] ?: 0.0
        assertTrue(
            "side delts ($sideDelts) did not out-volume chest ($chest) under a shoulder priority",
            sideDelts > chest,
        )
    }

    @Test
    fun `skill plans draw compounds from the skill tree at accessible tiers`() {
        val plan = ProgramGenerator.week(
            ProgramRequest(TrainingFocus.SKILL, ExperienceTier.INTERMEDIATE, EquipmentAccess.BODYWEIGHT, 6),
            catalogue, strength,
        )
        val compounds = entriesOf(plan)
            .filter { MovementDifficulty.tier(it.exerciseName) >= 2 }
        assertTrue(compounds.isNotEmpty())
        compounds.forEach { entry ->
            val key = entry.exerciseName.trim().lowercase()
            assertTrue(
                "${entry.exerciseName} is not a skill-tree movement",
                MovementDifficulty.isClassified(entry.exerciseName) &&
                    key !in MovementDifficulty.catalogueOnlyKeys,
            )
        }
    }

    @Test
    fun `generator is deterministic`() {
        val request = ProgramRequest(
            TrainingFocus.MUSCLE, ExperienceTier.INTERMEDIATE, EquipmentAccess.FULL_GYM, 4,
            priorities = setOf(MuscleArea.ARMS),
        )
        val a = ProgramGenerator.week(request, catalogue, strength)
        val b = ProgramGenerator.week(request, catalogue, strength)
        assertEquals(a, b)
    }

    // ------------------------------------------------------ acceptance 3

    @Test
    fun `improve moves reps into range swaps to the long-length twin adds calves and passes unknowns through`() {
        val request = ProgramRequest(
            TrainingFocus.MUSCLE, ExperienceTier.INTERMEDIATE, EquipmentAccess.FULL_GYM,
        )
        val sissy = PlannedEntry("Spanish Squat", 3, 12, null, modifiers = "tempo", why = "CSV import")
        val legs = PlannedPreset(
            "Legs", "", 2,
            listOf(
                PlannedEntry("Back Squat", 3, 5, null),
                PlannedEntry("Lying Leg Curl", 3, 20, null),
                sissy,
            ),
        )
        // The rest of the week covers every lower tracked muscle above the
        // tier minimum EXCEPT calves, which the week never trains.
        val restOfWeek = listOf(
            PlannedPreset("Squat Day", "", 1, listOf(
                PlannedEntry("Back Squat", 6, 8, null),
                PlannedEntry("Leg Press", 6, 10, null),
                PlannedEntry("Romanian Deadlift", 8, 10, null),
                PlannedEntry("Hip Thrust", 8, 10, null),
                PlannedEntry("Hanging Leg Raise", 4, 10, null),
                PlannedEntry("Ab Wheel Rollout", 8, 10, null),
            )),
        )
        val improvement = ProgramGenerator.improve(legs, restOfWeek, request, catalogue, strength)

        // Reps move into the muscle range: squat 5 -> 6..10, curl 20 -> 10..15.
        val squat = improvement.after.entries.first { it.exerciseName == "Back Squat" }
        assertTrue("squat reps ${squat.reps} outside 6..10", squat.reps in 6..10)
        assertTrue(improvement.changes.any { it.kind == PlanChange.Kind.ADJUSTED })

        // Lying (short) leg curl swapped for Seated (long) leg curl - Maeo 2021.
        val afterNames = improvement.after.entries.map { it.exerciseName }
        assertFalse("Lying Leg Curl stayed", "Lying Leg Curl" in afterNames)
        assertTrue("Seated Leg Curl missing", "Seated Leg Curl" in afterNames)
        val swap = improvement.changes.first { it.kind == PlanChange.Kind.SWAPPED }
        assertTrue("swap detail lost the citation: ${swap.detail}", swap.detail.contains("Maeo 2021"))
        val seated = improvement.after.entries.first { it.exerciseName == "Seated Leg Curl" }
        assertTrue("seated leg curl reps ${seated.reps} outside 10..15", seated.reps in 10..15)

        // Calves added: the week trains none.
        val added = improvement.changes.filter { it.kind == PlanChange.Kind.ADDED }
        assertTrue("no calf movement added", added.any {
            (MuscleMap.profile(it.exerciseName)?.muscles?.get(Muscle.CALVES) ?: 0.0) >= 0.5
        })

        // Missing loads filled from the profile.
        assertNotNull("squat load not filled", squat.targetWeightKg)
        assertTrue(improvement.changes.any { it.kind == PlanChange.Kind.LOAD_SET })

        // The unknown CSV movement passes through untouched, modifiers intact.
        assertEquals(sissy, improvement.after.entries.first { it.exerciseName == "Spanish Squat" })

        // Name, day and note survive.
        assertEquals("Legs", improvement.after.name)
        assertEquals(2, improvement.after.scheduledDay)

        // Idempotence: improving the improvement changes nothing.
        val second = ProgramGenerator.improve(improvement.after, restOfWeek, request, catalogue, strength)
        assertTrue(
            "second improve still changed: ${second.changes}",
            second.changes.isEmpty(),
        )
    }

    @Test
    fun `improve never swaps a strength main lift and is idempotent there too`() {
        val request = ProgramRequest(
            TrainingFocus.STRENGTH, ExperienceTier.INTERMEDIATE, EquipmentAccess.FULL_GYM,
        )
        val push = PlannedPreset(
            "Push", "", 1,
            listOf(
                PlannedEntry("Bench Press", 3, 5, 70.0),
                PlannedEntry("Triceps Pushdown", 3, 12, null),
            ),
        )
        val restOfWeek = listOf(
            PlannedPreset("Squat Day", "", 2, listOf(
                PlannedEntry("Back Squat", 5, 5, 90.0),
                PlannedEntry("Deadlift", 3, 3, 120.0),
                PlannedEntry("Barbell Row", 5, 5, 60.0),
            )),
        )
        val improvement = ProgramGenerator.improve(push, restOfWeek, request, catalogue, strength)
        // Specificity: the bench stays the bench.
        assertEquals("Bench Press", improvement.after.entries.first().exerciseName)
        assertFalse(
            "no swap should be recorded for the main lift",
            improvement.changes.any { it.kind == PlanChange.Kind.SWAPPED && it.exerciseName == "Bench Press" },
        )
        // Reps move per slot: the compound stays in the strength band, the
        // isolation accessory stays hypertrophy-style (Lopez 2021).
        val pushdown = improvement.after.entries.first { it.exerciseName != "Bench Press" }
        assertTrue("accessory reps ${pushdown.reps} outside 8..12", pushdown.reps in 8..12)
        assertTrue(
            "strength adjust reason lost its own logic",
            improvement.changes.none {
                it.kind == PlanChange.Kind.ADJUSTED && it.detail.contains("builds muscle")
            },
        )
        // Second pass: nothing left to do.
        val second = ProgramGenerator.improve(improvement.after, restOfWeek, request, catalogue, strength)
        assertTrue("second improve changed: ${second.changes}", second.changes.isEmpty())
    }

    @Test
    fun `improve removes duplicates beyond need`() {
        val request = ProgramRequest(
            TrainingFocus.MUSCLE, ExperienceTier.INTERMEDIATE, EquipmentAccess.FULL_GYM,
        )
        val upper = PlannedPreset(
            "Upper", "", 1,
            listOf(
                PlannedEntry("Bench Press", 3, 8, null),
                PlannedEntry("Dumbbell Bench Press", 3, 10, null),
            ),
        )
        val restOfWeek = listOf(
            PlannedPreset("Everything Else", "", 2, listOf(
                PlannedEntry("Back Squat", 12, 8, null),
                PlannedEntry("Romanian Deadlift", 12, 8, null),
                PlannedEntry("Lat Pulldown", 12, 10, null),
                PlannedEntry("Lateral Raise", 12, 12, null),
                PlannedEntry("Face Pull", 12, 12, null),
                PlannedEntry("Cable Curl", 12, 12, null),
                PlannedEntry("Overhead Cable Extension", 12, 12, null),
                PlannedEntry("Standing Calf Raise", 12, 12, null),
                PlannedEntry("Hanging Leg Raise", 12, 10, null),
            )),
        )
        val improvement = ProgramGenerator.improve(upper, restOfWeek, request, catalogue, strength)
        val names = improvement.after.entries.map { it.exerciseName }
        assertFalse("duplicate chest work kept", names.count { it == "Dumbbell Bench Press" } > 1)
        assertTrue(
            "one of the two chest presses should be removed",
            improvement.changes.any { it.kind == PlanChange.Kind.REMOVED },
        )
    }

    @Test
    fun `improving a lower day adds no upper work and keeps a second calf variant at the ceiling`() {
        // Found on device: improving the generated Lower A added a bench press
        // and a lateral raise (leg isolation read as "upper" scope) and cut the
        // seated calf raise beside a 5-set standing one, dropping calves under.
        val request = ProgramRequest(
            TrainingFocus.MUSCLE, ExperienceTier.INTERMEDIATE, EquipmentAccess.FULL_GYM, 4,
        )
        val week = ProgramGenerator.week(request, catalogue, strength).presets
        val lower = week.first { it.name == "Lower A" }
        val calves = lower.entries.filter { (MuscleMap.profile(it.exerciseName)!!.muscles[Muscle.CALVES] ?: 0.0) > 0.0 }
        assertTrue("fixture broken: no maxed calf raise in $calves", calves.any { it.sets == 5 })
        assertTrue("fixture broken: one calf variant in $calves", calves.size >= 2)

        val improvement = ProgramGenerator.improve(lower, week - lower, request, catalogue, strength)
        improvement.after.entries.forEach { entry ->
            val group = byName(entry.exerciseName).muscleGroup
            assertFalse(
                "upper work ${entry.exerciseName} added to a lower day",
                group == MuscleGroup.PUSH || group == MuscleGroup.PULL,
            )
        }
        calves.forEach { calf ->
            assertTrue(
                "${calf.exerciseName} removed: ${improvement.changes}",
                improvement.after.entries.any { it.exerciseName == calf.exerciseName },
            )
        }
    }

    @Test
    fun `single session kinds respect the requested role`() {
        val request = ProgramRequest(
            TrainingFocus.MUSCLE, ExperienceTier.INTERMEDIATE, EquipmentAccess.FULL_GYM,
        )
        val lower = ProgramGenerator.session(
            request, SessionKind.LOWER, 5, emptyList(), catalogue, strength,
        )
        assertNotNull(lower)
        assertEquals("Lower", lower!!.name)
        val groups = lower.entries.map { byName(it.exerciseName).muscleGroup }.toSet()
        assertTrue("lower session without leg work", MuscleGroup.LEGS in groups)
        // A standalone session still closes the week's deficits, but the
        // backbone leads with the requested patterns: squat or hinge first.
        val first = MuscleMap.profile(lower.entries.first().exerciseName)!!.pattern
        assertTrue("lower session did not open with a lower-body pattern", first in setOf(
            MovementPattern.SQUAT, MovementPattern.HINGE,
        ))
    }
}
