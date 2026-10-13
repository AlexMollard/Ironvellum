package com.ironvellum.app.ui.program

import com.ironvellum.app.data.Seed
import com.ironvellum.app.domain.ChangeOp
import com.ironvellum.app.domain.Equipment
import com.ironvellum.app.domain.Exercise
import com.ironvellum.app.domain.ExerciseMetric
import com.ironvellum.app.domain.Muscle
import com.ironvellum.app.domain.MuscleGroup
import com.ironvellum.app.domain.PlannedEntry
import com.ironvellum.app.domain.PlannedPreset
import com.ironvellum.app.domain.PresetEntry
import com.ironvellum.app.domain.ProgramGenerator
import com.ironvellum.app.domain.ProgramRequest
import com.ironvellum.app.domain.ProgramRules
import com.ironvellum.app.domain.Reason
import com.ironvellum.app.domain.ReasonTag
import com.ironvellum.app.domain.Recovery
import com.ironvellum.app.domain.StrengthProfile
import com.ironvellum.app.domain.TrainingFocus
import com.ironvellum.app.domain.VolumeLevel
import com.ironvellum.app.domain.WorkoutPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Optimise rites as data: the findings read from a week, the changes they become, what keeping a subset does
 * to coverage, and the page the screen is on surviving a round trip through its saved string.
 */
class OptimiseModelTest {

    private val catalogue: List<Exercise> = Seed.exercises.map {
        Exercise(
            name = it.name,
            muscleGroup = MuscleGroup.valueOf(it.muscleGroup),
            isWeighted = it.isWeighted,
            metric = ExerciseMetric.valueOf(it.metric),
            category = it.category,
        )
    }

    private val request = ProgramRequest(TrainingFocus.MUSCLE, VolumeLevel.STANDARD, Equipment.FULL_GYM, maxExercises = 8)

    private fun entry(name: String, sets: Int) = PlannedEntry(name, sets, 8, null)

    private var nextId = 1L

    /** A saved rite for a planned one, the way the repository would hand it back. */
    private fun saved(rite: PlannedPreset): WorkoutPreset = WorkoutPreset(
        id = nextId++,
        name = rite.name,
        note = rite.note,
        scheduledDay = rite.scheduledDay,
        entries = rite.entries.mapIndexed { i, e ->
            PresetEntry(
                exerciseId = i + 1L, exerciseName = e.exerciseName, targetSets = e.sets, targetReps = e.reps,
                targetWeightKg = e.targetWeightKg, modifiers = e.modifiers, position = i,
            )
        },
    )

    private fun input(vararg week: PlannedPreset) =
        OptimiseInput(week.map(::saved), request, catalogue, StrengthProfile())

    private fun rite(name: String, day: Int?, vararg entries: PlannedEntry) = PlannedPreset(name, "", day, entries.toList())

    private val heavyChest = arrayOf(
        rite("Push A", 1, entry("Bench Press", 6)),
        rite("Push B", 3, entry("Bench Press", 6)),
        rite("Push C", 5, entry("Bench Press", 6)),
        rite("Push D", 6, entry("Bench Press", 4)),
    )

    // ---------------------------------------------------------------------------------------- heavy

    @Test
    fun `a heavy muscle gets changes that each lower it, and keeping them is what the page shows`() {
        val report = buildReport(input(*heavyChest))
        val chest = report.heavy.first { it.muscle == Muscle.MID_CHEST }
        assertTrue("heavy chest has changes", chest.changes.isNotEmpty())
        assertEquals("ids are unique so a tick survives a recompute", chest.changes.size, chest.changes.map { it.id }.distinct().size)
        val week = report.input.week
        val before = ProgramRules.coverage(week).getValue(Muscle.MID_CHEST).weekly
        for (change in chest.changes) {
            assertTrue(change.options.isNotEmpty())
            val after = resolveWeek(week, listOf(change.options.first()))
            assertTrue("${change.id} lowers chest", ProgramRules.coverage(after).getValue(Muscle.MID_CHEST).weekly < before + 1e-9)
        }
        val rows = impactRows(week, resolveWeek(week, chest.changes.map { it.options.first() }), Muscle.MID_CHEST)
        assertEquals("the muscle in focus leads", Muscle.MID_CHEST, rows.first().muscle)
        assertTrue(rows.first().after < rows.first().before)
    }

    @Test
    fun `an alternative the lifter picks replaces the best match in the same slot`() {
        val report = buildReport(input(*heavyChest))
        val swap = report.heavy.flatMap { it.changes }.firstOrNull { it.kind == OptKind.SWAP && it.options.size > 1 }
        assertNotNull("heavy chest offers a swap with alternatives", swap)
        val names = swap!!.options.map { it.label }
        assertEquals(names.distinct().size, names.size)
        val week = report.input.week
        val viaSecond = resolveWeek(week, listOf(swap.options[1]))
        assertTrue(viaSecond.flatMap { it.entries }.any { it.exerciseName == names[1] })
        assertFalse(viaSecond.flatMap { it.entries }.any { it.exerciseName == names[0] })
    }

    // ------------------------------------------------------------------------------------- gaps

    @Test
    fun `a gap lists the short muscles, ranks movements for each and places one in a rite`() {
        val report = buildReport(input(rite("Push A", 1, entry("Bench Press", 4)), rite("Pull A", 3, entry("Barbell Row", 3))))
        assertTrue(report.gaps.isNotEmpty())
        assertTrue("no gap is a covered muscle", report.gaps.none { report.tally.states[it] == CoverageState.COVERED })
        val withOptions = report.gaps.filter { report.gapOptions.getValue(it).options.isNotEmpty() }
        assertTrue(withOptions.isNotEmpty())
        val muscle = withOptions.first()
        val ranked = report.gapOptions.getValue(muscle).options
        assertTrue(ranked.size <= 3)
        val entry = gapEntry(report.input, ranked.first())
        assertEquals(ranked.first().exercise.name, entry.exerciseName)

        val choices = riteChoices(report.input, entry)
        assertEquals("every counted rite is a choice", report.input.week.size, choices.size)
        assertTrue(choices.first().best)
        assertEquals(1, choices.count { it.best })

        val week = report.input.week
        val after = withAdded(week, choices.first().index, entry)
        assertEquals(week[choices.first().index].entries.size + 1, after[choices.first().index].entries.size)
        val row = impactRows(week, after, muscle).first { it.muscle == muscle }
        assertTrue(row.after > row.before)
    }

    @Test
    fun `adding enough sets moves a muscle from not trained to covered`() {
        val week = listOf(rite("Push A", 1, entry("Bench Press", 4)))
        val rows = impactRows(week, week.map { it.copy(entries = it.entries + entry("Barbell Row", 12)) })
        val lats = rows.first { it.muscle == Muscle.LATS }
        assertEquals(CoverageState.NOT_TRAINED, lats.beforeState)
        assertEquals(CoverageState.COVERED, lats.afterState)
        assertEquals(CoverageState.NOT_TRAINED, lats.beforeState)
        assertTrue(lats.text.startsWith("Not trained, 0 → Covered, "))
    }

    // ------------------------------------------------------------------------------- impact text

    @Test
    fun `impact text says the state with and without a change of state`() {
        val moved = ImpactRow(Muscle.REAR_DELTS, 2.0, 5.0, CoverageState.BELOW_MINIMUM, CoverageState.COVERED)
        assertEquals("Below minimum, 2 → Covered, 5 hard sets", moved.text)
        val stays = ImpactRow(Muscle.LATS, 8.0, 5.0, CoverageState.COVERED, CoverageState.COVERED)
        assertEquals("Stays covered, 8 → 5 hard sets", stays.text)
        assertEquals("Lats: Stays covered, 8 → 5 hard sets", stays.spoken)
    }

    @Test
    fun `a change that moves nothing has no rows`() {
        val week = listOf(rite("Push A", 1, entry("Bench Press", 4)))
        assertTrue(impactRows(week, week).isEmpty())
    }

    // ----------------------------------------------------------------------------------- overlap

    @Test
    fun `two rows doing one job become a swap for the weaker, and the swap is what applies`() {
        val report = buildReport(input(rite("Pull A", 1, entry("Dumbbell Row", 4), entry("Seated Cable Row", 3), entry("Lat Pulldown", 3))))
        val rite = report.overlaps.single()
        assertEquals("Pull A", rite.rite)
        val change = rite.changes.single()
        assertEquals(setOf("Dumbbell Row", "Seated Cable Row"), change.movements.map { it.first }.toSet())
        assertTrue("each movement says what it works", change.movements.all { it.second.isNotBlank() })
        assertTrue(change.options.size in 1..3)
        // The one with fewer sets is the one that goes.
        assertTrue(change.title.startsWith("Seated Cable Row → "))
        val week = report.input.week
        val after = resolveWeek(week, listOf(change.options.first()))
        val names = after[0].entries.map { it.exerciseName }
        assertTrue("Dumbbell Row" in names && "Seated Cable Row" !in names)
        assertTrue(change.options.first().label in names)
    }

    // ------------------------------------------------------------------------------------ spread

    @Test
    fun `tight days become one spread change that leaves no pair under 48 hours`() {
        val report = buildReport(
            // Chest is hard on Mon and Tue; Upper C (Thu) has none, so the Upper rites can trade days.
            input(
                rite("Upper A", 1, entry("Bench Press", 6)),
                rite("Upper B", 2, entry("Bench Press", 6)),
                rite("Upper C", 4, entry("Barbell Row", 6)),
            ),
        )
        val spread = report.spread
        assertNotNull("chest is hit hard on two days a day apart", spread)
        assertEquals(1, spread!!.options.size)
        val moves = spread.options.single().dayMoves
        assertTrue(moves.isNotEmpty())
        val after = resolveWeek(report.input.week, listOf(spread.options.single()))
        assertTrue(Recovery.tightPairs(after).size < Recovery.tightPairs(report.input.week).size)
        assertTrue(after.map { it.name }.toSet() == report.input.week.map { it.name }.toSet())
        assertTrue(tightLine(report.tight.first()).contains("48 hours"))
    }

    @Test
    fun `a spaced week has nothing to spread`() {
        val report = buildReport(input(rite("Push A", 1, entry("Bench Press", 5)), rite("Legs", 4, entry("Back Squat", 5))))
        assertEquals(null, report.spread)
    }

    // --------------------------------------------------------------------------------- the report

    @Test
    fun `rites with no day are left out exactly as Weekly coverage leaves them out`() {
        val report = buildReport(input(rite("Push A", 1, entry("Bench Press", 4)), rite("Loose", null, entry("Barbell Row", 4))))
        assertEquals(listOf("Push A"), report.input.week.map { it.name })
        assertEquals(1, report.input.ids.size)
    }

    @Test
    fun `a week the Forge just made has no gap and nothing heavy`() {
        val plan = ProgramGenerator.week(request.copy(daysPerWeek = 4), catalogue, StrengthProfile())
        val report = buildReport(OptimiseInput(plan.presets.map(::saved), request, catalogue, StrengthProfile()))
        assertTrue(report.gaps.isEmpty())
        assertTrue(report.heavy.isEmpty())
    }

    @Test
    fun `the instrumented fixture week is heavy on front delts, has an overlap and gaps`() {
        val report = buildReport(
            input(
                rite("Upper A", 1, entry("Overhead Press", 7), entry("Incline Bench Press", 6), entry("Bench Press", 5)),
                rite("Pull A", 2, entry("Dumbbell Row", 4), entry("Seated Cable Row", 3), entry("Lat Pulldown", 3)),
                rite("Lower A", 4, entry("Back Squat", 4)),
            ),
        )
        assertTrue(report.heavy.single { it.muscle == Muscle.FRONT_DELTS }.changes.isNotEmpty())
        assertEquals(1, report.overlaps.size)
        assertTrue(Muscle.CALVES in report.gaps && report.gapOptions.getValue(Muscle.CALVES).options.isNotEmpty())
    }

    // ------------------------------------------------------------------------------------- words

    @Test
    fun `reasons are said in words and at most two`() {
        val tags = listOf(
            ReasonTag(Reason.DIRECT_MUSCLE, Muscle.REAR_DELTS),
            ReasonTag(Reason.FITS_GEAR),
            ReasonTag(Reason.YOU_LOG_THIS),
        )
        assertEquals("Trains rear delts directly · fits your armoury", reasonLine(tags))
        assertEquals("Stands in for Dips", reasonLine(listOf(ReasonTag(Reason.STAND_IN_FOR, other = "Dips"))))
        assertEquals("", reasonLine(emptyList()))
    }

    @Test
    fun `every reason has words`() {
        for (reason in Reason.entries) {
            assertTrue("$reason", reasonLine(listOf(ReasonTag(reason, Muscle.LATS, "Row", 2))).isNotBlank())
        }
    }

    // --------------------------------------------------------------------------------- the page

    @Test
    fun `every page survives its saved string`() {
        val pages = listOf(
            OptStep.Hub,
            OptStep.Gap(Muscle.REAR_DELTS),
            OptStep.Where(Muscle.CALVES, 2),
            OptStep.Heavy(Muscle.FRONT_DELTS),
            OptStep.Overlap("Upper A: the 2nd, with a colon"),
            OptStep.Spread,
            OptStep.Detail(OptStep.Heavy(Muscle.FRONT_DELTS), "ease:FRONT_DELTS:swap:Push A:Dips"),
            OptStep.Detail(OptStep.Spread, "spread"),
        )
        for (page in pages) assertEquals(page, OptStep.decode(page.encode()))
    }

    @Test
    fun `an unreadable saved page is the hub, and back climbs one page at a time`() {
        assertEquals(OptStep.Hub, OptStep.decode("where:NOT_A_MUSCLE:1"))
        assertEquals(OptStep.Hub, OptStep.decode(""))
        assertEquals(OptStep.Gap(Muscle.CALVES), OptStep.Where(Muscle.CALVES, 0).parent())
        val detail = OptStep.Detail(OptStep.Heavy(Muscle.LATS), "x")
        assertEquals(OptStep.Heavy(Muscle.LATS), detail.parent())
        assertEquals(OptStep.Hub, OptStep.Heavy(Muscle.LATS).parent())
        assertEquals(OptStep.Hub, OptStep.Hub.parent())
    }

    @Test
    fun `a swap names the option that is picked, not the best match`() {
        val change = OptChange(
            "id", OptKind.SWAP, "Dips → Close-grip Push-up", "d", "Push A",
            listOf(OptOption("Close-grip Push-up", ""), OptOption("Cable Crossover", "")),
        )
        assertEquals("Dips → Cable Crossover", titleFor(change, change.options[1]))
        val trim = OptChange("t", OptKind.TRIM, "Drop 1 set from Dips", "d", "Push A", listOf(OptOption("Drop 1 set", "")))
        assertEquals("Drop 1 set from Dips", titleFor(trim, trim.options.single()))
        assertTrue(ChangeOp.SetSets("Dips", 2) is ChangeOp)
    }
}
