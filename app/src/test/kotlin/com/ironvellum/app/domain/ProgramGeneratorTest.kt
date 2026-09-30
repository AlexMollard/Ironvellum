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
        listOf(Equipment.NOTHING, Equipment(fullGym = false, gear = Gear.entries.toSet()), Equipment.FULL_GYM).flatMap { eq ->
            TrainingFocus.entries.map { focus ->
                ProgramGenerator.week(
                    ProgramRequest(focus, VolumeLevel.STANDARD, eq, daysPerWeek = 4),
                    catalogue,
                    strength,
                )
            }
        } + (2..6).map { days ->
            ProgramGenerator.week(
                ProgramRequest(TrainingFocus.GENERAL, VolumeLevel.HIGH, Equipment.FULL_GYM, days),
                catalogue,
                strength,
            )
        }

    private fun entriesOf(plan: RoutinePlan) = plan.presets.flatMap { it.entries }

    private fun volumeOf(plan: RoutinePlan) = ProgramRules.weeklyVolume(plan.presets)

    @Test
    fun `every generated reason cites only registered papers`() {
        assertCitationsResolve(
            allPlans() + TrainingSplit.OPTIONS.map { (split, days) ->
                ProgramGenerator.week(
                    ProgramRequest(TrainingFocus.STRENGTH, VolumeLevel.LOW, Equipment.NOTHING, days, split = split),
                    catalogue,
                    strength,
                )
            },
        )
    }

    // ------------------------------------------------- acceptance example 1

    @Test
    fun `three days that cannot fit the intermediate dose name every short muscle instead of cramming`() {
        // Three full-body days share twelve muscles; inside the session time
        // budget they cannot all reach 12 sets. The plan must stay finishable
        // and say which muscles land short, rather than silently padding.
        // Volume reach, not the cap: the roomiest movement cap a lifter can pick.
        val request = ProgramRequest(
            TrainingFocus.MUSCLE, VolumeLevel.STANDARD, Equipment.FULL_GYM,
            daysPerWeek = 3, priorities = setOf(MuscleArea.ARMS), maxExercises = 8,
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
        val range = ProgramRules.weeklySetTarget(VolumeLevel.STANDARD, TrainingFocus.MUSCLE)
        val short = ProgramRules.TRACKED.filter { (volume[it] ?: 0.0) < range.start - 0.5 }
        assertTrue("expected a capacity-limited week", short.isNotEmpty())
        val note = plan.note
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
        listOf(Muscle.MID_CHEST, Muscle.SIDE_DELTS, Muscle.REAR_DELTS).forEach {
            assertTrue("arms $arms did not out-volume $it ${volume[it]}", arms > (volume[it] ?: 0.0))
        }
    }

    @Test
    fun `every muscle a generated week leaves under the floor is named in its routine note, never a workout's`() {
        // The coverage map calls any muscle below the floor UNDER, even 11.6
        // of 12; a plan that shows UNDER there without saying so here reads
        // as a generator bug rather than an honest capacity limit. The line
        // is routine-wide advice: saved into the first workout's note it
        // became a permanent mantra on that day.
        for (tier in VolumeLevel.entries) {
            for (equipment in listOf(Equipment.NOTHING, Equipment(fullGym = false, gear = Gear.entries.toSet()), Equipment.FULL_GYM)) {
                for ((split, days) in TrainingSplit.OPTIONS) {
                    val plan = ProgramGenerator.week(
                        ProgramRequest(TrainingFocus.MUSCLE, tier, equipment, days, split = split), catalogue, strength,
                    )
                    val volume = volumeOf(plan)
                    val floor = ProgramRules.weeklySetTarget(tier, TrainingFocus.MUSCLE).start
                    val note = plan.note
                    ProgramRules.TRACKED.filter { (volume[it] ?: 0.0) < floor }.forEach { muscle ->
                        assertTrue(
                            "$tier $equipment $split ${days}d: ${muscle.label} at ${volume[muscle]} not in note: $note",
                            muscle.label.lowercase() in note,
                        )
                    }
                    val rest = ProgramGenerator.presetNote(tier, TrainingFocus.MUSCLE)
                    plan.presets.forEach { preset ->
                        assertEquals("$tier $equipment $split ${days}d: ${preset.name} note", rest, preset.note)
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
        // Volume reach, not the cap: the roomiest movement cap a lifter can pick.
        val request = ProgramRequest(
            TrainingFocus.MUSCLE, VolumeLevel.STANDARD, Equipment.FULL_GYM, 4, maxExercises = 8,
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
            ProgramRequest(TrainingFocus.MUSCLE, VolumeLevel.HIGH, Equipment.FULL_GYM, 6),
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
        // Volume reach, not the cap: the roomiest movement cap a lifter can pick.
        val request = ProgramRequest(
            TrainingFocus.MUSCLE, VolumeLevel.STANDARD, Equipment.FULL_GYM,
            daysPerWeek = 5, priorities = setOf(MuscleArea.ARMS), maxExercises = 8,
        )
        val plan = ProgramGenerator.week(request, catalogue, strength)
        assertEquals(5, plan.presets.size)

        val volume = volumeOf(plan)
        val range = ProgramRules.weeklySetTarget(VolumeLevel.STANDARD, TrainingFocus.MUSCLE)
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
        assertTrue(bench.loadNote!!.contains("estimated 1-rep max"))
    }

    @Test
    fun `improve raises sets on a covered muscle the week leaves short and stays idempotent`() {
        val request = ProgramRequest(
            TrainingFocus.MUSCLE, VolumeLevel.STANDARD, Equipment.FULL_GYM,
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
            TrainingFocus.STRENGTH, VolumeLevel.LOW, Equipment(fullGym = false, gear = Gear.entries.toSet()),
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
            plan.presets.first().note.contains("3-5 min") && plan.presets.first().note.contains("short of failure"),
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
            TrainingFocus.MUSCLE, VolumeLevel.STANDARD, Equipment.FULL_GYM,
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
        assertTrue("fixture broken: chest short", volume[Muscle.MID_CHEST]!! >= 12.0)

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
            entries.any { (MuscleMap.profile(it.exerciseName)?.muscles?.get(Muscle.MID_CHEST) ?: 0.0) >= 0.5 },
        )
    }

    @Test
    fun `auto session tops up a real generated week that sits just under target`() {
        // Four intermediate days leave the upper muscles 1-1.5 sets short of
        // 12. Measuring a per-session share against the weekly volume called
        // that week complete and built nothing ("cannot fill this workout").
        val request = ProgramRequest(
            TrainingFocus.MUSCLE, VolumeLevel.STANDARD, Equipment.FULL_GYM, 4,
        )
        val week = ProgramGenerator.week(request, catalogue, strength).presets
        val before = ProgramRules.weeklyVolume(week)
        val range = ProgramRules.weeklySetTarget(VolumeLevel.STANDARD, TrainingFocus.MUSCLE)
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
        // Volume reach, not the cap: the roomiest movement cap a lifter can pick.
        val request = ProgramRequest(
            TrainingFocus.MUSCLE, VolumeLevel.LOW, Equipment.FULL_GYM, 5, maxExercises = 8,
        )
        // The generated week twice over: every tracked muscle past target.
        val once = ProgramGenerator.week(request, catalogue, strength).presets
        val week = once + once
        val volume = ProgramRules.weeklyVolume(week)
        val range = ProgramRules.weeklySetTarget(VolumeLevel.LOW, TrainingFocus.MUSCLE)
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
                ProgramRequest(focus, VolumeLevel.STANDARD, Equipment.NOTHING, days),
                catalogue, strength,
            )
            val names = entriesOf(plan).map { it.exerciseName }
            assertTrue("plan empty for $focus/$days", names.isNotEmpty())
            names.forEach { assertFalse("loaded $it in a no-gear plan", byName(it).isWeighted) }
        }
    }

    @Test
    fun `home weights plans never prescribe a machine`() {
        for (days in 2..6) for (focus in TrainingFocus.entries) {
            val plan = ProgramGenerator.week(
                ProgramRequest(focus, VolumeLevel.LOW, Equipment(fullGym = false, gear = Gear.entries.toSet()), days),
                catalogue, strength,
            )
            val names = entriesOf(plan).map { it.exerciseName }
            assertTrue("plan empty for $focus/$days", names.isNotEmpty())
            names.forEach { assertFalse("machine $it in a gear-set plan", isMachine(it)) }
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
        for ((split, days) in TrainingSplit.OPTIONS) {
            val plan = ProgramGenerator.week(
                ProgramRequest(TrainingFocus.MUSCLE, VolumeLevel.HIGH, Equipment.FULL_GYM, days, split = split),
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
    fun `push pull legs on three days builds one of each and says what it trades`() {
        val plan = ProgramGenerator.week(
            ProgramRequest(
                TrainingFocus.MUSCLE, VolumeLevel.STANDARD, Equipment.FULL_GYM, 3,
                split = TrainingSplit.PUSH_PULL_LEGS,
            ),
            catalogue, strength,
        )
        assertEquals(listOf("Push A", "Pull A", "Legs A"), plan.presets.map { it.name })
        assertEquals(listOf(1, 3, 5), plan.presets.map { it.scheduledDay })
        assertTrue(plan.note.contains("once a week"))
        assertFalse(plan.presets.any { it.note.contains("once a week") })
        // The default 3-day split is still full body, and it carries no such note.
        val fullBody = ProgramGenerator.week(
            ProgramRequest(TrainingFocus.MUSCLE, VolumeLevel.STANDARD, Equipment.FULL_GYM, 3),
            catalogue, strength,
        )
        assertTrue(fullBody.presets.all { it.name.startsWith("Full Body") })
        assertFalse(fullBody.note.contains("once a week"))
    }

    @Test
    fun `the week keeps a rest day before wrapping to monday`() {
        // Splits up to 5 days end the week before Sunday; the 6-day PPL x2
        // rests mid-week (Thursday) instead.
        for (days in 2..5) {
            val plan = ProgramGenerator.week(
                ProgramRequest(TrainingFocus.GENERAL, VolumeLevel.STANDARD, Equipment.FULL_GYM, days),
                catalogue, strength,
            )
            val scheduled = plan.presets.mapNotNull { it.scheduledDay }
            assertFalse("day 7 scheduled in a $days-day week", 7 in scheduled)
        }
        val six = ProgramGenerator.week(
            ProgramRequest(TrainingFocus.GENERAL, VolumeLevel.STANDARD, Equipment.FULL_GYM, 6),
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
        val fit = { name: String, equipment: Equipment ->
            val e = byName(name)
            when {
                equipment == Equipment.NOTHING -> 0
                !e.isWeighted -> 2
                MovementDifficulty.loadFactor(name) < MovementDifficulty.FREE_WEIGHT_LOAD ||
                    name.trim().lowercase().startsWith("assisted") -> 1
                else -> 0
            }
        }
        for (equipment in listOf(Equipment.NOTHING, Equipment(fullGym = false, gear = Gear.entries.toSet()), Equipment.FULL_GYM)) for (focus in TrainingFocus.entries) {
            val plan = ProgramGenerator.week(
                ProgramRequest(focus, VolumeLevel.HIGH, equipment, 6),
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
        // Only pressing movement is a pin-stack machine: no gear must drop
        // the slots rather than prescribe it.
        val fixture = listOf(
            Exercise(name = "Machine Chest Press", muscleGroup = MuscleGroup.PUSH, isWeighted = true),
            Exercise(name = "Door Sheet Row", muscleGroup = MuscleGroup.PULL, isWeighted = false),
            Exercise(name = "Glute Bridge", muscleGroup = MuscleGroup.LEGS, isWeighted = false),
            Exercise(name = "Hanging Knee Raise", muscleGroup = MuscleGroup.CORE, isWeighted = false),
        )
        val plan = ProgramGenerator.week(
            ProgramRequest(TrainingFocus.GENERAL, VolumeLevel.LOW, Equipment.NOTHING, 4),
            fixture, StrengthProfile(emptyMap()),
        )
        val names = entriesOf(plan).map { it.exerciseName }
        assertFalse("machine slipped into a BODYWEIGHT plan", "Machine Chest Press" in names)
        assertTrue("plan shrank to nothing", "Door Sheet Row" in names)
    }

    @Test
    fun `stretch-biased movements win the isolation slots`() {
        val plan = ProgramGenerator.week(
            ProgramRequest(TrainingFocus.MUSCLE, VolumeLevel.STANDARD, Equipment.FULL_GYM, 4),
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
                TrainingFocus.MUSCLE, VolumeLevel.STANDARD, Equipment.FULL_GYM, 4,
                priorities = setOf(MuscleArea.SHOULDERS),
            ),
            catalogue, strength,
        )
        val volume = volumeOf(prioritised)
        val sideDelts = volume[Muscle.SIDE_DELTS] ?: 0.0
        val chest = volume[Muscle.MID_CHEST] ?: 0.0
        assertTrue(
            "side delts ($sideDelts) did not out-volume chest ($chest) under a shoulder priority",
            sideDelts > chest,
        )
    }

    @Test
    fun `skill plans draw compounds from the skill tree at accessible tiers`() {
        // A calisthenics kit: with no gear at all the only horizontal pull
        // is the catalogue's door sheet row, which the tree does not own.
        val kit = Equipment(fullGym = false, gear = setOf(Gear.PULL_UP_BAR, Gear.RINGS, Gear.PARALLETTES, Gear.DIP_BARS))
        val plan = ProgramGenerator.week(
            ProgramRequest(TrainingFocus.SKILL, VolumeLevel.STANDARD, kit, 6),
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
            TrainingFocus.MUSCLE, VolumeLevel.STANDARD, Equipment.FULL_GYM, 4,
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
            TrainingFocus.MUSCLE, VolumeLevel.STANDARD, Equipment.FULL_GYM,
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
    fun `improve leaves profiled activities, milestones and metre skills as written`() {
        // Every catalogue row now has a muscle profile; a profile must not
        // make improve re-dose a run, a milestone lift or a metre skill.
        // Each is one set here, which improve would coerce to 2 if it dosed it.
        val request = ProgramRequest(TrainingFocus.MUSCLE, VolumeLevel.STANDARD, Equipment.FULL_GYM)
        val kept = listOf(
            PlannedEntry("Heavy Bench Press", 1, 3, null),
            PlannedEntry("Handstand Walk", 1, 10, null),
            PlannedEntry("Running", 1, 30, null),
            PlannedEntry("Plank", 1, 60, null),
        )
        val push = PlannedPreset("Push", "", 1, listOf(PlannedEntry("Bench Press", 3, 8, null)) + kept)
        val improvement = ProgramGenerator.improve(push, emptyList(), request, catalogue, strength)
        for (entry in kept) {
            assertEquals(entry, improvement.after.entries.firstOrNull { it.exerciseName == entry.exerciseName })
            assertTrue(
                "${entry.exerciseName} was changed: ${improvement.changes}",
                improvement.changes.none { it.exerciseName == entry.exerciseName },
            )
        }
    }

    @Test
    fun `improve never swaps a strength main lift and is idempotent there too`() {
        val request = ProgramRequest(
            TrainingFocus.STRENGTH, VolumeLevel.STANDARD, Equipment.FULL_GYM,
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
            TrainingFocus.MUSCLE, VolumeLevel.STANDARD, Equipment.FULL_GYM,
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
        // Volume reach, not the cap: the roomiest movement cap a lifter can pick.
        val request = ProgramRequest(
            TrainingFocus.MUSCLE, VolumeLevel.STANDARD, Equipment.FULL_GYM, 4, maxExercises = 8,
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
            TrainingFocus.MUSCLE, VolumeLevel.STANDARD, Equipment.FULL_GYM,
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

    // ----------------------------------------------------- gear toggles

    /** The owner's real kit: a bar, parallettes, one 24 kg dumbbell. */
    private val ownerKit = Equipment(
        fullGym = false,
        gear = setOf(Gear.PULL_UP_BAR, Gear.PARALLETTES, Gear.DUMBBELLS),
        dumbbellMaxKg = 24.0,
        dumbbellPair = false,
    )

    private val benchRequiring =
        setOf("Bench Press", "Incline Bench Press", "Close-Grip Bench Press",
            "Dumbbell Bench Press", "Incline Dumbbell Press", "Dumbbell Fly")

    private val pairOnlyDumbbell = setOf("Dumbbell Bench Press", "Incline Dumbbell Press", "Dumbbell Fly")

    @Test
    fun `generated weeks never call a light helper a shortfall`() {
        // A helper under its floor is light, shown and counted but never a
        // problem: the note names tracked muscles only.
        val kits = listOf(Equipment.FULL_GYM, Equipment.NOTHING, ownerKit)
        var lightSeen = 0
        for (kit in kits) for ((split, days) in TrainingSplit.OPTIONS) for (focus in TrainingFocus.entries) {
            val plan = ProgramGenerator.week(
                ProgramRequest(focus, VolumeLevel.STANDARD, kit, daysPerWeek = days, split = split),
                catalogue, strength,
            )
            val volume = volumeOf(plan)
            val notes = (plan.presets.map { it.note } + plan.note).joinToString(" ").lowercase()
            for (helper in ProgramRules.HELPERS) {
                val sets = volume[helper] ?: 0.0
                if (sets >= ProgramRules.HELPER_FLOOR_SETS - 1e-9) continue
                lightSeen++
                assertFalse(
                    "${helper.label} at $sets sets is light, yet the note names it ($kit/$split$days/$focus): $notes",
                    helper.label.lowercase() in notes,
                )
            }
        }
        assertTrue("no week left a helper light, so nothing was checked", lightSeen > 0)
    }

    @Test
    fun `generated weeks stay inside the session time budget`() {
        val kits = listOf(Equipment.FULL_GYM, Equipment.NOTHING, ownerKit)
        for (kit in kits) for ((split, days) in TrainingSplit.OPTIONS) for (focus in TrainingFocus.entries) {
            val plan = ProgramGenerator.week(
                ProgramRequest(focus, VolumeLevel.STANDARD, kit, daysPerWeek = days, split = split),
                catalogue, strength,
            )
            // Fourteen helpers must not buy their floors with overlong sessions.
            plan.presets.forEach { preset ->
                val seconds = ProgramRules.sessionSeconds(preset.entries, focus)
                assertTrue(
                    "${preset.name} runs ${seconds / 60} min ($kit/$split$days/$focus)",
                    seconds <= ProgramRules.SESSION_BUDGET_SECONDS,
                )
            }
        }
    }

    @Test
    fun `every kit has a movement that trains each helper muscle`() {
        // Reachability, not dose: with no gear at all a lifter can still
        // train the tibialis, rotator cuff, serratus and abductors.
        for (kit in listOf(Equipment.NOTHING, ownerKit, Equipment.FULL_GYM)) {
            val pool = ProgramGenerator.eligible(catalogue, kit, TrainingFocus.STRENGTH)
            val unreachable = ProgramRules.HELPERS.filter { helper ->
                pool.none { (MuscleMap.profile(it.name)!!.muscles[helper] ?: 0.0) >= 0.5 }
            }
            assertEquals("helpers with no movement on $kit", emptyList<Muscle>(), unreachable)
        }
    }

    @Test
    fun `a six-day week lifts every helper to its floor on every kit`() {
        // Six sessions leave the time the helper floors need: none may be
        // left short, whatever the gear.
        for (kit in listOf(Equipment.NOTHING, ownerKit, Equipment.FULL_GYM)) for (focus in TrainingFocus.entries) {
            // Volume reach, not the cap: the roomiest movement cap a lifter can pick.
            val plan = ProgramGenerator.week(
                ProgramRequest(
                    focus, VolumeLevel.STANDARD, kit, daysPerWeek = 6, split = TrainingSplit.PUSH_PULL_LEGS, maxExercises = 8,
                ),
                catalogue, strength,
            )
            val volume = volumeOf(plan)
            val short = ProgramRules.HELPERS.filter { (volume[it] ?: 0.0) < ProgramRules.HELPER_FLOOR_SETS - 1e-9 }
                .map { "$it=${volume[it] ?: 0.0}" }
            assertEquals("helpers under the floor ($kit/$focus)", emptyList<String>(), short)
        }
    }

    @Test
    fun `the shortfall note names short tracked muscles and never a helper`() {
        val range = 5.0..15.0
        val everyHelperLight = ProgramRules.TRACKED.associateWith { 5.0 } + ProgramRules.HELPERS.associateWith { 0.0 }
        assertEquals(
            "", ProgramGenerator.shortfallNote(everyHelperLight, range, "add a day to cover them."),
        )
        val note = ProgramGenerator.shortfallNote(
            everyHelperLight + (Muscle.QUADS to 3.0), range, "add a day to cover them.",
        )
        assertTrue(note, "Short on quads" in note)
        ProgramRules.HELPERS.forEach { helper ->
            assertFalse("light ${helper.label} named: $note", helper.label.lowercase() in note)
        }
    }

    private fun strengthPpl3(priorities: Set<MuscleArea>, focus: TrainingFocus = TrainingFocus.STRENGTH) =
        ProgramGenerator.week(
            ProgramRequest(
                focus, VolumeLevel.STANDARD, ownerKit, daysPerWeek = 3,
                priorities = priorities, split = TrainingSplit.PUSH_PULL_LEGS,
            ),
            catalogue, strength,
        )

    private fun verticalPulls(preset: PlannedPreset) =
        preset.entries.filter { MuscleMap.profile(it)?.pattern == MovementPattern.VERTICAL_PULL }

    @Test
    fun `a strength back priority practises the pull-day vertical pull again first on legs day`() {
        val plan = strengthPpl3(setOf(MuscleArea.BACK))
        val byName = plan.presets.associateBy { it.name }
        val pull = byName.getValue("Pull A")
        val legs = byName.getValue("Legs A")
        val main = verticalPulls(pull).first()
        val practice = legs.entries.first()
        assertEquals(main.exerciseName, practice.exerciseName)
        assertEquals(3, practice.sets)
        assertTrue(practice.why, "Grgic 2018" in practice.why && "Buckner 2017" in practice.why)
        assertTrue("push day pulls", verticalPulls(byName.getValue("Push A")).isEmpty())

        // No priority, no strength focus, or no compound pattern: one practice only.
        for (other in listOf(
            strengthPpl3(emptySet()),
            strengthPpl3(setOf(MuscleArea.ARMS)),
            strengthPpl3(setOf(MuscleArea.BACK), focus = TrainingFocus.MUSCLE),
        )) {
            assertEquals(
                "vertical pulls outside the pull day: ${other.presets}",
                listOf("Pull A"), other.presets.filter { verticalPulls(it).isNotEmpty() }.map { it.name },
            )
        }
        // Two days have no room for a second practice.
        val twoDays = ProgramGenerator.week(
            ProgramRequest(
                TrainingFocus.STRENGTH, VolumeLevel.STANDARD, ownerKit, daysPerWeek = 2,
                priorities = setOf(MuscleArea.BACK),
            ),
            catalogue, strength,
        )
        assertTrue(entriesOf(twoDays).none { it.why.startsWith("Second ") })
        // A shoulder priority is for the side delts: no second overhead press
        // taking the legs day's time for a front-delt lift.
        val vTaper = strengthPpl3(setOf(MuscleArea.BACK, MuscleArea.SHOULDERS))
        assertEquals(
            listOf("Second ${main.exerciseName.lowercase()} practice"),
            entriesOf(vTaper).filter { it.why.startsWith("Second ") }.map { it.why.substringBefore(":") },
        )
    }

    /** The muscle a deficit fill was added for, read from its why ("Fills tibialis: the week was 2 sets short - ..."). */
    private fun filledFor(entry: PlannedEntry): Muscle? {
        val label = Regex("^Fills (?:priority )?([a-z ]+?)(?: at full stretch)?:").find(entry.why)?.groupValues?.get(1)
        return Muscle.entries.firstOrNull { it.label.lowercase() == label }
    }

    @Test
    fun `back and shoulder priorities reach their top before a helper gets any time`() {
        // The owner's tight week: three strength days, one bar, one 24 kg
        // dumbbell. Every prioritised tracked muscle reaches the top of its
        // range, or every session that trains it is out of time for one more
        // set - and no session that could have served it spends time on a helper.
        val plan = strengthPpl3(setOf(MuscleArea.BACK, MuscleArea.SHOULDERS))
        val volume = volumeOf(plan)
        val top = ProgramRules.weeklySetTarget(VolumeLevel.STANDARD, TrainingFocus.STRENGTH).endInclusive
        assertEquals(top, volume[Muscle.LATS]!!, 1e-9)
        for (muscle in listOf(Muscle.LATS, Muscle.SIDE_DELTS)) {
            if ((volume[muscle] ?: 0.0) >= top - 1e-9) continue
            for (preset in plan.presets) {
                val training = preset.entries.filter { (MuscleMap.profile(it)?.muscles?.get(muscle) ?: 0.0) > 0.0 }
                if (training.isEmpty()) continue
                val seconds = ProgramRules.sessionSeconds(preset.entries, TrainingFocus.STRENGTH)
                training.filter { it.sets < 5 }.forEach { entry ->
                    val oneMore = ProgramRules.setSeconds(TrainingFocus.STRENGTH, MuscleMap.profile(entry)!!.compound)
                    assertTrue(
                        "${muscle.label} at ${volume[muscle]}: ${preset.name} has time for another ${entry.exerciseName} set",
                        seconds + oneMore > ProgramRules.SESSION_BUDGET_SECONDS,
                    )
                }
                val helperFills = preset.entries.filter { filledFor(it) in ProgramRules.HELPERS }
                assertEquals("${preset.name} feeds helpers while ${muscle.label} is short", emptyList<PlannedEntry>(), helperFills)
            }
        }
        // The side delts' own isolation carries the priority first: it sits
        // at its 5-set ceiling before any press takes the rest.
        assertEquals(5, entriesOf(plan).single { it.exerciseName == "Lateral Raise" }.sets)
    }

    @Test
    fun `a shoulder priority lands on the side delts and leaves the front delts at their helper dose`() {
        // Presses already cover the front delts, a helper; a V-taper wants
        // the side delts. With the days to spare they reach the top of their
        // range through lateral raises, and the front delts gain nothing.
        fun sixDays(priorities: Set<MuscleArea>) = ProgramGenerator.week(
            ProgramRequest(
                TrainingFocus.STRENGTH, VolumeLevel.STANDARD, ownerKit, daysPerWeek = 6,
                priorities = priorities, split = TrainingSplit.PUSH_PULL_LEGS,
            ),
            catalogue, strength,
        )
        val prioritised = sixDays(setOf(MuscleArea.SHOULDERS))
        val plain = sixDays(emptySet())
        val volume = volumeOf(prioritised)
        val top = ProgramRules.weeklySetTarget(VolumeLevel.STANDARD, TrainingFocus.STRENGTH).endInclusive
        // Within one lateral-raise set of the top: the next would overshoot it.
        assertTrue("side delts at ${volume[Muscle.SIDE_DELTS]}", volume[Muscle.SIDE_DELTS]!! > top - 1.0)
        assertEquals(
            10, entriesOf(prioritised).filter { it.exerciseName == "Lateral Raise" }.sumOf { it.sets },
        )
        assertEquals(volumeOf(plain)[Muscle.FRONT_DELTS]!!, volume[Muscle.FRONT_DELTS]!!, 1e-9)
        assertTrue(entriesOf(prioritised).none { filledFor(it) == Muscle.FRONT_DELTS })
    }

    /** A name the owner's kit cannot express, per the gear brief. */
    private fun forbiddenForOwnerKit(name: String): Boolean {
        val lower = name.lowercase()
        return "inverted row" in lower || "australian" in lower || Regex("\\bring\\b").containsMatchIn(lower) ||
            ("dip" in lower && lower != "bench dip") || "barbell" in lower ||
            name in benchRequiring || name in pairOnlyDumbbell || isMachine(name)
    }

    @Test
    fun `the owner kit never prescribes gear the lifter does not have`() {
        for ((split, days) in TrainingSplit.OPTIONS) {
            for (focus in TrainingFocus.entries) {
                for (volume in VolumeLevel.entries) {
                    val plan = ProgramGenerator.week(
                        ProgramRequest(focus, volume, ownerKit, daysPerWeek = days, split = split),
                        catalogue, strength,
                    )
                    assertTrue("empty plan for $split/$focus/$volume", plan.presets.isNotEmpty())
                    entriesOf(plan).forEach { entry ->
                        assertFalse(
                            "forbidden ${entry.exerciseName} for the owner kit ($split/$focus/$volume)",
                            forbiddenForOwnerKit(entry.exerciseName),
                        )
                    }
                }
            }
        }
        for (template in ProgramTemplates.ALL) {
            for (volume in VolumeLevel.entries) {
                val plan = ProgramTemplates.build(template, volume, ownerKit, catalogue, strength)
                assertTrue("empty template build ${template.id}/$volume", plan.presets.isNotEmpty())
                plan.presets.flatMap { it.entries }.forEach { entry ->
                    assertFalse(
                        "forbidden ${entry.exerciseName} in ${template.id} for the owner kit",
                        forbiddenForOwnerKit(entry.exerciseName),
                    )
                }
            }
        }
    }

    @Test
    fun `nothing gear excludes bar moves and every loaded movement`() {
        for (days in 2..6) for (focus in TrainingFocus.entries) {
            val plan = ProgramGenerator.week(
                ProgramRequest(focus, VolumeLevel.STANDARD, Equipment.NOTHING, days),
                catalogue, strength,
            )
            val names = entriesOf(plan).map { it.exerciseName }
            assertTrue("plan empty for $focus/$days", names.isNotEmpty())
            names.forEach { name ->
                assertFalse("loaded $name with no gear", byName(name).isWeighted)
                val lower = name.lowercase()
                assertFalse(
                    "bar move $name with no gear",
                    "pull-up" in lower || "chin-up" in lower || "hang" in lower ||
                        "hanging" in lower || "toes-to-bar" in lower || "muscle-up" in lower ||
                        "dip" in lower && lower != "bench dip",
                )
            }
        }
    }

    @Test
    fun `every weighted generator-eligible movement has an explicit gear row`() {
        val names = TrainingFocus.entries
            .flatMap { ProgramGenerator.eligible(catalogue, Equipment.FULL_GYM, it) }
            .filter { it.isWeighted }
            .map { it.name }
            .toSet()
        assertTrue(names.isNotEmpty())
        names.forEach { name ->
            assertTrue(
                "$name is weighted, generator-eligible and has no GearRequirements row",
                isMachine(name) || GearRequirements.hasEntry(name),
            )
        }
    }

    @Test
    fun `dumbbell cap raises reps before hitting twenty`() {
        // Dumbbell Row 30 x 10 -> e1RM 40; prescribed 26.7 kg at 8 reps over a
        // 24 kg cap, and the e1RM supports 18 reps at 2 RIR on 24 kg.
        val row = byName("Dumbbell Row")
        val profile = ProgramRules.strengthProfile(listOf(LoggedLift("Dumbbell Row", 30.0, 10)))
        val fill = ProgramGenerator.fillLoad(
            row, profile, reps = 8, rir = 2,
            equipment = ownerKit.copy(dumbbellPair = true),
        )
        assertNotNull(fill)
        assertEquals(24.0, fill!!.kg, 0.001)
        assertTrue("reps not raised to the e1RM-supported count: ${fill.reps}", fill.reps in 15..20)
        assertTrue(fill.reps > 8)
        assertTrue(fill.note!!.contains("Lopez 2021"))
        assertFalse(fill.overCap)
    }

    @Test
    fun `a single dumbbell rules out the pair-only moves`() {
        // A bench is owned, so the only thing between the lifter and a
        // dumbbell bench press is the second dumbbell.
        val pair = ownerKit.copy(gear = ownerKit.gear + Gear.BENCH, dumbbellPair = true)
        val single = pair.copy(dumbbellPair = false)
        val withPair = ProgramGenerator.eligible(catalogue, pair, TrainingFocus.MUSCLE).map { it.name }
        val withOne = ProgramGenerator.eligible(catalogue, single, TrainingFocus.MUSCLE).map { it.name }
        assertTrue("fixture broken: a pair and a bench cannot press", pairOnlyDumbbell.all { it in withPair })
        pairOnlyDumbbell.forEach { assertFalse("pair-only $it with one dumbbell", it in withOne) }
        assertTrue("one-arm work lost with one dumbbell", "Dumbbell Row" in withOne)
    }

    @Test
    fun `an over-cap goblet squat becomes a bulgarian split squat`() {
        // Goblet Squat loads estimated off the logged Back Squat dwarf the 24
        // kg dumbbell and cannot reach failure inside 20 reps on it, so the
        // harder variant takes the movement over.
        val pool = ProgramGenerator.eligible(catalogue, ownerKit, TrainingFocus.MUSCLE)
        val goblet = byName("Goblet Squat")
        val (chosen, fill) = ProgramGenerator.fillWithCap(
            goblet, pool, strength, reps = 8, rir = 2, equipment = ownerKit,
        )
        assertEquals("Bulgarian Split Squat", chosen.name)
        assertNotNull(fill)
        assertTrue(fill!!.kg <= 48.0)
    }

    @Test
    fun `a movement with no harder variant keeps the cap at twenty reps and says so`() {
        val profile = ProgramRules.strengthProfile(listOf(LoggedLift("Dumbbell Row", 40.0, 5)))
        val fill = ProgramGenerator.fillLoad(
            byName("Dumbbell Row"), profile, reps = 8, rir = 2, equipment = ownerKit,
        )
        assertNotNull(fill)
        assertEquals(24.0, fill!!.kg, 0.001)
        assertEquals(20, fill.reps)
        assertTrue(fill.overCap)
        assertTrue(fill.note!!.contains("harder variant"))
    }

    // ------------------------------------------------ compound & skill only

    private val compoundOnlyKits = listOf(
        Equipment.NOTHING, ownerKit, Equipment(fullGym = false, gear = Gear.entries.toSet()), Equipment.FULL_GYM,
    )

    /** Shoulders are served by raises and arms by curls, and BACK asks for a second practice. */
    private val isolationBait = setOf(MuscleArea.BACK, MuscleArea.SHOULDERS, MuscleArea.ARMS)

    private fun assertNoIsolation(plan: RoutinePlan, label: String) {
        plan.presets.forEach { preset ->
            assertTrue("empty day ${preset.name} ($label)", preset.entries.isNotEmpty())
            preset.entries.forEach { entry ->
                assertFalse(
                    "isolation ${entry.exerciseName} with compound & skill only ($label)",
                    MovementDifficulty.isIsolation(entry.exerciseName),
                )
            }
        }
    }

    @Test
    fun `compound and skill only weeks never select isolation work`() {
        for (kit in compoundOnlyKits) for ((split, days) in TrainingSplit.OPTIONS) {
            for (focus in TrainingFocus.entries) for (volume in VolumeLevel.entries) {
                val label = "$kit/$split/$days/$focus/$volume"
                val plan = ProgramGenerator.week(
                    ProgramRequest(
                        focus, volume, kit, days, priorities = isolationBait, split = split, compoundOnly = true,
                    ),
                    catalogue, strength,
                )
                assertTrue("empty plan $label", plan.presets.isNotEmpty())
                assertNoIsolation(plan, label)
            }
        }
    }

    @Test
    fun `compound and skill only sessions and improvements never select isolation work`() {
        val upper = PlannedPreset(
            "Upper", "", 1,
            listOf(PlannedEntry("Bench Press", 3, 8, null), PlannedEntry("Lat Pulldown", 3, 10, null)),
        )
        for (kit in compoundOnlyKits) for (focus in TrainingFocus.entries) {
            val request = ProgramRequest(
                focus, VolumeLevel.STANDARD, kit, priorities = isolationBait, compoundOnly = true,
            )
            for (kind in SessionKind.entries) {
                val preset = ProgramGenerator.session(request, kind, 1, emptyList(), catalogue, strength) ?: continue
                assertNoIsolation(RoutinePlan(listOf(preset)), "$kit/$focus/$kind")
            }
            val improved = ProgramGenerator.improve(upper, emptyList(), request, catalogue, strength).after
            assertNoIsolation(RoutinePlan(listOf(improved)), "improve $kit/$focus")
        }
    }

    @Test
    fun `compound and skill only improve takes the lifter's own isolation work out of the preset`() {
        val pull = PlannedPreset(
            "Pull", "", 3,
            listOf(
                PlannedEntry("Pull-up", 4, 6, null),
                PlannedEntry("Chin-up", 3, 8, null),
                PlannedEntry("Dumbbell Row", 3, 10, 24.0),
                PlannedEntry("Lateral Raise", 3, 15, 8.0),
                PlannedEntry("Ab Wheel Rollout", 3, 10, null),
            ),
        )
        val request = ProgramRequest(TrainingFocus.MUSCLE, VolumeLevel.STANDARD, ownerKit, compoundOnly = true)
        val improvement = ProgramGenerator.improve(pull, emptyList(), request, catalogue, strength)
        val names = improvement.after.entries.map { it.exerciseName }
        assertNoIsolation(RoutinePlan(listOf(improvement.after)), "owner pull $names")
        assertTrue("the pull-up was lost: $names", "Pull-up" in names)
        assertTrue(
            "the lateral raise left without a reason line: ${improvement.changes}",
            improvement.changes.any {
                it.detail.contains("compound & skill only", ignoreCase = true) &&
                    (it.exerciseName == "Lateral Raise" || it.detail.contains("Lateral Raise"))
            },
        )

        // Flag off, improve is untouched: the raise stays an isolation move.
        val off = ProgramGenerator.improve(pull, emptyList(), request.copy(compoundOnly = false), catalogue, strength)
        assertTrue(
            "fixture broken: improve without the flag dropped the isolation work",
            off.after.entries.any { MovementDifficulty.isIsolation(it.exerciseName) },
        )
    }

    @Test
    fun `compound and skill only improve never empties a session`() {
        // Nothing compound trains the calves, so the raise has no stand-in;
        // removing it would leave nothing to improve and nothing to train.
        val calves = PlannedPreset("Calves", "", 6, listOf(PlannedEntry("Standing Calf Raise", 3, 12, null)))
        val request = ProgramRequest(
            TrainingFocus.MUSCLE, VolumeLevel.STANDARD, Equipment.FULL_GYM, compoundOnly = true,
        )
        val after = ProgramGenerator.improve(calves, emptyList(), request, catalogue, strength).after
        assertTrue("session emptied", after.entries.isNotEmpty())
    }

    @Test
    fun `compound and skill only templates drop or replace their isolation entries`() {
        val plans = compoundOnlyKits.flatMap { kit ->
            ProgramTemplates.ALL.flatMap { template ->
                VolumeLevel.entries.map { volume ->
                    val plan = ProgramTemplates.build(template, volume, kit, catalogue, strength, compoundOnly = true)
                    assertTrue("empty template ${template.id}/$volume/$kit", plan.presets.isNotEmpty())
                    assertNoIsolation(plan, "${template.id}/$volume/$kit")
                    plan
                }
            }
        }
        assertCitationsResolve(plans)
    }

    @Test
    fun `isolation work is still selected when compound and skill only is off`() {
        // The control for the sweeps above: were the flag ignored, this same
        // request with it on would still carry these movements.
        val request = ProgramRequest(
            TrainingFocus.MUSCLE, VolumeLevel.STANDARD, Equipment.FULL_GYM, 4, priorities = isolationBait,
        )
        val off = entriesOf(ProgramGenerator.week(request, catalogue, strength)).map { it.exerciseName }
        assertTrue("fixture broken: no isolation in $off", off.any { MovementDifficulty.isIsolation(it) })
        val template = ProgramTemplates.ALL.first { it.id == "upper_lower_muscle" }
        val built = ProgramTemplates.build(template, template.authoredVolume, Equipment.FULL_GYM, catalogue, strength)
        assertTrue(
            "fixture broken: template carries no isolation",
            built.presets.flatMap { it.entries }.any { MovementDifficulty.isIsolation(it.exerciseName) },
        )
    }

    // ------------------------------------------------- movements per workout

    /** The owner's kit with the ab wheel he also owns. */
    private val ownerKitWithWheel = ownerKit.copy(gear = ownerKit.gear + Gear.AB_WHEEL)

    /** The owner's own answers: a calisthenics push/pull/legs week built around the pull-up. */
    private val ownerRequest = ProgramRequest(
        TrainingFocus.STRENGTH, VolumeLevel.STANDARD, ownerKitWithWheel, 3,
        priorities = setOf(MuscleArea.BACK, MuscleArea.SHOULDERS),
        split = TrainingSplit.PUSH_PULL_LEGS, compoundOnly = true, maxExercises = 5,
    )

    @Test
    fun `the owner's week never puts more than five movements in a workout`() {
        val plan = ProgramGenerator.week(ownerRequest, catalogue, strength)
        assertEquals(3, plan.presets.size)
        plan.presets.forEach { preset ->
            assertTrue(
                "${preset.name} holds ${preset.entries.size}: ${preset.entries.map { it.exerciseName }}",
                preset.entries.size <= 5,
            )
        }
    }

    @Test
    fun `no generated workout exceeds the lifter's movement cap`() {
        val kits = listOf(Equipment.NOTHING, ownerKitWithWheel, Equipment(fullGym = false, gear = Gear.entries.toSet()), Equipment.FULL_GYM)
        // Eight movements, none redundant with another, so only the cap trims it.
        val crowded = PlannedPreset(
            "Crowded", "", 2,
            listOf(
                PlannedEntry("Pull-up", 4, 5, null), PlannedEntry("Push-up", 3, 10, null),
                PlannedEntry("Pike Push-up", 3, 8, null), PlannedEntry("Door Sheet Row", 3, 10, null),
                PlannedEntry("Bulgarian Split Squat", 3, 8, null), PlannedEntry("Romanian Deadlift", 3, 8, null),
                PlannedEntry("Hanging Leg Raise", 3, 10, null), PlannedEntry("Bicep Curl", 3, 12, null),
            ),
        )
        for (cap in listOf(3, 5)) for (kit in kits) for (focus in TrainingFocus.entries) {
            for (volume in VolumeLevel.entries) for ((split, days) in TrainingSplit.OPTIONS) {
                val request = ProgramRequest(
                    focus, volume, kit, days, priorities = setOf(MuscleArea.BACK), split = split, maxExercises = cap,
                )
                ProgramGenerator.week(request, catalogue, strength).presets.forEach { preset ->
                    assertTrue(
                        "week $cap/$kit/$focus/$volume/$split$days ${preset.name}: ${preset.entries.size}",
                        preset.entries.size <= cap,
                    )
                }
            }
            val request = ProgramRequest(focus, VolumeLevel.HIGH, kit, maxExercises = cap)
            for (kind in SessionKind.entries) {
                val preset = ProgramGenerator.session(request, kind, 1, emptyList(), catalogue, strength) ?: continue
                assertTrue("session $cap/$kit/$focus/$kind: ${preset.entries.size}", preset.entries.size <= cap)
            }
            val improved = ProgramGenerator.improve(crowded, emptyList(), request, catalogue, strength).after
            assertTrue("improve $cap/$kit/$focus: ${improved.entries.size}", improved.entries.size <= cap)
        }
        for (cap in listOf(3, 5)) for (kit in kits) for (template in ProgramTemplates.ALL) {
            for (volume in VolumeLevel.entries) {
                val plan = ProgramTemplates.build(template, volume, kit, catalogue, strength, maxExercises = cap)
                plan.presets.forEach { day ->
                    assertTrue(
                        "template ${template.id}/$volume/$kit cap $cap ${day.name}: ${day.entries.size}",
                        day.entries.size <= cap,
                    )
                    // The day's lead lift survives the trim (full gym: nothing is substituted).
                    if (kit == Equipment.FULL_GYM) {
                        assertEquals(
                            "template ${template.id} ${day.name} lost its lead under cap $cap",
                            template.days.first { it.name == day.name }.entries.first().exerciseName,
                            day.entries.first().exerciseName,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `improve trims a preset over the cap and says what it removed`() {
        val preset = PlannedPreset(
            "Full", "", 1,
            listOf(
                PlannedEntry("Pull-up", 4, 5, null), PlannedEntry("Handstand Push-up", 3, 5, null),
                PlannedEntry("Push-up", 3, 5, null), PlannedEntry("Door Sheet Row", 3, 5, null),
                PlannedEntry("Pistol Squat", 3, 5, null), PlannedEntry("Romanian Deadlift", 3, 5, null),
                PlannedEntry("Hanging Leg Raise", 3, 10, null),
            ),
        )
        val request = ownerRequest.copy(compoundOnly = false)
        val improvement = ProgramGenerator.improve(preset, emptyList(), request, catalogue, strength)
        val after = improvement.after.entries.map { it.exerciseName }
        assertEquals("kept $after", 5, after.size)
        assertEquals("the lead lift was trimmed: $after", "Pull-up", after.first())
        val removed = improvement.changes.filter { it.kind == PlanChange.Kind.REMOVED && "5-exercise cap" in it.detail }
        assertEquals("cap removals ${improvement.changes}", 2, removed.size)
        removed.forEach { change ->
            assertFalse("${change.exerciseName} reported removed but kept", change.exerciseName in after)
            assertTrue("${change.exerciseName} was never in the preset", preset.entries.any { it.exerciseName == change.exerciseName })
        }
        // Its own output is already inside the cap: a second pass trims nothing.
        val again = ProgramGenerator.improve(improvement.after, emptyList(), request, catalogue, strength)
        assertTrue(again.changes.none { it.kind == PlanChange.Kind.REMOVED })
        // A roomier cap keeps all seven.
        val roomy = ProgramGenerator.improve(preset, emptyList(), request.copy(maxExercises = 8), catalogue, strength)
        assertTrue(roomy.changes.none { it.kind == PlanChange.Kind.REMOVED })
        assertTrue(roomy.after.entries.size >= 7)
    }

    @Test
    fun `improve leaves the lifter's holds exactly as written`() {
        // A hold's figure is seconds: a rep range, a long-length swap or a
        // set top-up would turn the owner's 4x10s tuck lever into pull-ups
        // or a 5 s hold. Its profile still counts for the rest of the day.
        val lever = PlannedEntry("Tuck Front Lever", 4, 10, null)
        val planche = PlannedEntry("Tuck Planche", 3, 10, null)
        val pull = PlannedPreset(
            "Pull", "", 1,
            listOf(PlannedEntry("Pull-up", 5, 5, 10.0, modifiers = "weighted"), lever, PlannedEntry("Door Sheet Row", 4, 10, null)),
        )
        val push = PlannedPreset("Push", "", 5, listOf(PlannedEntry("Handstand Push-up", 4, 6, null), planche))
        val request = ownerRequest.copy(compoundOnly = false)
        for ((preset, hold) in listOf(pull to lever, push to planche)) {
            val improvement = ProgramGenerator.improve(preset, emptyList(), request, catalogue, strength)
            assertTrue("${hold.exerciseName} was changed: ${improvement.after.entries}", hold in improvement.after.entries)
            assertTrue(
                "a change names ${hold.exerciseName}: ${improvement.changes}",
                improvement.changes.none { it.exerciseName == hold.exerciseName },
            )
        }
    }

    @Test
    fun `compound and skill only pulls with bodyweight and skill-tree movements where the kit allows both`() {
        // The kit holds a dumbbell row and a door sheet row, a chin-up and a
        // pull-up: the calisthenics lifter's switch must take the bodyweight
        // row and the skill-tree pull-up.
        val pool = ProgramGenerator.eligible(catalogue, ownerKitWithWheel, TrainingFocus.STRENGTH, compoundOnly = true)
            .map { it.name }
        listOf("Dumbbell Row", "Door Sheet Row", "Chin-up", "Pull-up", "Ab Wheel Rollout").forEach {
            assertTrue("fixture broken: $it not eligible", it in pool)
        }
        val plan = ProgramGenerator.week(ownerRequest, catalogue, strength)
        val pullDay = plan.presets.first { it.name.startsWith("Pull") }
        val pulls = pullDay.entries.filter {
            MuscleMap.profile(it.exerciseName)?.pattern in setOf(MovementPattern.VERTICAL_PULL, MovementPattern.HORIZONTAL_PULL)
        }
        val names = pullDay.entries.map { it.exerciseName }
        assertTrue("no row on the pull day: $names", pulls.any { MuscleMap.profile(it.exerciseName)!!.pattern == MovementPattern.HORIZONTAL_PULL })
        pulls.forEach { entry ->
            assertFalse("loaded ${entry.exerciseName} on the pull day: $names", byName(entry.exerciseName).isWeighted)
            if (MuscleMap.profile(entry.exerciseName)!!.pattern == MovementPattern.VERTICAL_PULL) {
                assertTrue(
                    "${entry.exerciseName} is not a skill-tree pull: $names",
                    Skills.ALL.any { it.name.equals(entry.exerciseName, ignoreCase = true) },
                )
            }
        }
        // Where no bodyweight compound serves the muscle, the loaded one stays.
        val legs = plan.presets.first { it.name.startsWith("Legs") }.entries.map { it.exerciseName }
        assertTrue("the hinge lost its only compound: $legs", "Romanian Deadlift" in legs)
    }

    @Test
    fun `a first year with no kit is never prescribed a bodyweight progression past tier III`() {
        // The onboarding call: no history, male, every offered split. Dragon
        // Flag (tier IV) once filled abs for LOW + NOTHING because it was the
        // only floor ab movement in the pool; Lying Leg Raise and Dead Bug
        // (tier I) now fill the abs instead.
        val floorAbs = setOf("Lying Leg Raise", "Dead Bug")
        for (focus in TrainingFocus.entries - TrainingFocus.SKILL) for ((split, days) in TrainingSplit.OPTIONS) {
            val plan = ProgramGenerator.week(
                ProgramRequest(focus, VolumeLevel.LOW, Equipment.NOTHING, days, sex = Sex.MALE, split = split),
                catalogue,
                StrengthProfile(),
            )
            assertTrue("$focus $split $days built nothing", plan.presets.isNotEmpty())
            val tooHard = entriesOf(plan)
                .filter { !byName(it.exerciseName).isWeighted && MovementDifficulty.tier(it.exerciseName) > 3 }
                .map { it.exerciseName }
            assertTrue("$focus $split $days prescribed $tooHard", tooHard.isEmpty())
        }
        val general = ProgramGenerator.week(
            ProgramRequest(TrainingFocus.GENERAL, VolumeLevel.LOW, Equipment.NOTHING, 3, sex = Sex.MALE, split = TrainingSplit.FULL_BODY),
            catalogue,
            StrengthProfile(),
        )
        assertTrue(
            "no floor ab movement:${entriesOf(general).map { it.exerciseName }}",
            entriesOf(general).any { it.exerciseName in floorAbs },
        )
    }
}
