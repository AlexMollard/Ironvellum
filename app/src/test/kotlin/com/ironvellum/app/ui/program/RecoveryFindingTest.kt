package com.ironvellum.app.ui.program

import com.ironvellum.app.data.Seed
import com.ironvellum.app.domain.ChangeOp
import com.ironvellum.app.domain.Equipment
import com.ironvellum.app.domain.Exercise
import com.ironvellum.app.domain.ExerciseMetric
import com.ironvellum.app.domain.FatigueAdvice
import com.ironvellum.app.domain.FatigueLevel
import com.ironvellum.app.domain.Muscle
import com.ironvellum.app.domain.MuscleGroup
import com.ironvellum.app.domain.PlannedEntry
import com.ironvellum.app.domain.PlannedPreset
import com.ironvellum.app.domain.PresetEntry
import com.ironvellum.app.domain.ProgramRequest
import com.ironvellum.app.domain.RecoveryFinding
import com.ironvellum.app.domain.SessionSet
import com.ironvellum.app.domain.StrengthProfile
import com.ironvellum.app.domain.TrainingFocus
import com.ironvellum.app.domain.VolumeLevel
import com.ironvellum.app.domain.WorkoutPreset
import com.ironvellum.app.domain.WorkoutSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Temper's recovery finding over a fictional history: "Back + Chest" on Wednesdays keeps starting after a hard
 * "Pull Strength" the night before. The 2-of-3 rule, the 6-week limit, the standing "Keep as is" and the setting.
 */
class RecoveryFindingTest {

    private val hour = 3_600_000L
    private val day = 24 * hour
    private val now = 100 * day

    private val catalogue: List<Exercise> = Seed.exercises.map {
        Exercise(
            name = it.name, muscleGroup = MuscleGroup.valueOf(it.muscleGroup), isWeighted = it.isWeighted,
            metric = ExerciseMetric.valueOf(it.metric), category = it.category,
        )
    }
    private val request = ProgramRequest(TrainingFocus.MUSCLE, VolumeLevel.STANDARD, Equipment.FULL_GYM, maxExercises = 8)

    private val tired = WorkoutPreset(
        id = 1, name = "Back + Chest", scheduledDay = 3,
        entries = listOf(PresetEntry(exerciseId = 1, exerciseName = "Barbell Row", targetSets = 3, targetReps = 8, position = 0)),
    )
    private val cause = WorkoutPreset(
        id = 2, name = "Pull Strength", scheduledDay = 1,
        entries = listOf(PresetEntry(exerciseId = 2, exerciseName = "Pull-up", targetSets = 4, targetReps = 6, position = 0)),
    )
    private val week = listOf(tired, cause)

    private var nextSession = 1L

    private fun sets(name: String, count: Int) =
        List(count) { SessionSet(exerciseId = 0, exerciseName = name, setIndex = it, reps = 6, weightKg = 20.0, done = true, rpe = 9) }

    private fun workout(presetId: Long, startedAt: Long, name: String, count: Int) =
        WorkoutSession(id = nextSession++, presetId = presetId, label = name, startedAtMs = startedAt, completedAtMs = startedAt + hour) to sets(name, count)

    /** A hard Pull Strength ten hours before each of [starts], and the Back + Chest workout itself at each. */
    private fun history(vararg starts: Long, hardBefore: Set<Long> = starts.toSet()) = buildList {
        for (start in starts) {
            if (start in hardBefore) add(workout(cause.id, start - 10 * hour, "Pull-up", 8))
            add(workout(tired.id, start, "Barbell Row", 3))
        }
    }

    private fun find(history: List<Pair<WorkoutSession, List<SessionSet>>>, kept: (Long, Muscle) -> Boolean = { _, _ -> false }) =
        RecoveryFinding.find(week, history, emptyMap(), now, isKept = kept)

    // ------------------------------------------------------------------------------------- the rule

    @Test
    fun `a routine that started tired three times is found, with the pull workout as the cause`() {
        val found = find(history(60 * day, 67 * day, 74 * day)).single()
        assertEquals("Back + Chest", found.rite)
        assertEquals(3, found.starts.size)
        assertTrue(found.starts.all { FatigueAdvice.triggers(it.level) })
        assertEquals(3, found.tiredStarts)
        assertEquals("the hard pull day is the cause", cause.id, found.cause?.presetId)
        assertEquals("Pull-up", found.cause?.exerciseName)
        assertTrue("the muscle is one the routine mainly works", found.muscle in FatigueAdvice.mainMuscles("Barbell Row"))
    }

    @Test
    fun `two tired starts out of three is enough, one of three is not`() {
        val twoOfThree = history(60 * day, 67 * day, 74 * day, hardBefore = setOf(60 * day, 74 * day))
        val found = find(twoOfThree).single()
        assertEquals(2, found.tiredStarts)
        assertFalse(FatigueAdvice.triggers(found.starts[1].level))

        val oneOfThree = history(60 * day, 67 * day, 74 * day, hardBefore = setOf(74 * day))
        assertTrue(find(oneOfThree).isEmpty())
    }

    @Test
    fun `only the last three workouts count, so an older tired start falls out`() {
        // The newest three are fresh, fresh, tired; the two tired ones before them are no longer in the last three.
        val h = history(59 * day, 64 * day, 69 * day, 74 * day, 79 * day, hardBefore = setOf(59 * day, 64 * day, 79 * day))
        assertTrue(find(h).isEmpty())
    }

    @Test
    fun `a workout older than six weeks is not counted`() {
        // Two tired starts, but one is 43 days ago: one inside the window is not two.
        val outside = now - 43 * day
        val inside = now - 10 * day
        val other = now - 20 * day
        val h = history(outside, other, inside, hardBefore = setOf(outside, inside))
        assertTrue("43 days is outside the six weeks", find(h).isEmpty())
        // The same two a day inside the limit are found.
        val h2 = history(now - 41 * day, other, inside, hardBefore = setOf(now - 41 * day, inside))
        assertEquals(1, find(h2).size)
    }

    @Test
    fun `two workouts, both tired, count as two of two`() {
        assertEquals(1, find(history(67 * day, 74 * day)).size)
    }

    @Test
    fun `fewer than two workouts finds nothing`() {
        assertTrue(find(history(74 * day)).isEmpty())
        assertTrue(find(emptyList()).isEmpty())
    }

    @Test
    fun `each start is read from what came before it, never the workout itself`() {
        // Every start is fresh when nothing hard preceded it, even though the routine's own sets would be High after.
        assertTrue(find(history(60 * day, 67 * day, 74 * day, hardBefore = emptySet())).isEmpty())
    }

    @Test
    fun `keep as is hides the finding for that routine and muscle only`() {
        val h = history(60 * day, 67 * day, 74 * day)
        val muscle = find(h).single().muscle
        assertTrue(find(h) { id, m -> id == tired.id && m == muscle }.none { it.muscle == muscle })
        assertEquals("another routine's keep does not hide it", 1, find(h) { id, _ -> id == 99L }.size)
    }

    // ----------------------------------------------------------------------------- the Temper report

    private var nextId = 1L

    private fun saved(rite: PlannedPreset) = WorkoutPreset(
        id = nextId++, name = rite.name, scheduledDay = rite.scheduledDay,
        entries = rite.entries.mapIndexed { i, e ->
            PresetEntry(exerciseId = i + 1L, exerciseName = e.exerciseName, targetSets = e.sets, targetReps = e.reps, position = i)
        },
    )

    private fun rite(name: String, day: Int?, vararg entries: PlannedEntry) = PlannedPreset(name, "", day, entries.toList())

    private fun input(fatigueAdvice: Boolean = true, kept: (Long, Muscle) -> Boolean = { _, _ -> false }): OptimiseInput {
        nextId = 1L
        return OptimiseInput(
            saved = listOf(
                saved(rite("Back + Chest", 3, PlannedEntry("Barbell Row", 3, 8, null))),
                saved(rite("Pull Strength", 1, PlannedEntry("Pull-up", 4, 6, null))),
            ),
            request = request, catalogue = catalogue, strength = StrengthProfile(),
            history = history(60 * day, 67 * day, 74 * day), nowMs = now, fatigueAdvice = fatigueAdvice, keptAsIs = kept,
        )
    }

    @Test
    fun `the report offers a move to the next free day and a trim of the cause lift`() {
        val report = buildReport(input())
        val rite = report.recovery.single()
        assertEquals(OptKind.RECOVERY, rite.change.kind)
        assertEquals("Back + Chest is moved forward from Wednesday to Thursday, one more day after Pull Strength", 4, rite.move?.to)
        assertEquals(2, rite.change.options.size)
        val trim = rite.change.options[1].edits.single().op as ChangeOp.SetSets
        assertEquals("Pull-up", trim.exerciseName)
        assertEquals(3, trim.sets)
        assertNotNull(rite.trim)
        assertTrue("a trim can only lower the projection", rite.trim!!.projected <= rite.finding.starts.last().level)
        assertFalse(report.nothing)
        assertEquals(report.findings, report.recovery.size + report.gaps.size + report.heavy.size + report.overlaps.sumOf { it.changes.size } + (if (report.spread != null) 1 else 0))
    }

    @Test
    fun `with fatigue advice off the report has no recovery finding`() {
        assertTrue(buildReport(input(fatigueAdvice = false)).recovery.isEmpty())
        assertTrue(buildReport(input().copy(history = emptyList())).recovery.isEmpty())
    }

    @Test
    fun `a kept routine and muscle leaves the report`() {
        val muscle = buildReport(input()).recovery.single().finding.muscle
        assertTrue(buildReport(input(kept = { id, m -> id == 1L && m == muscle })).recovery.isEmpty())
    }

    // ------------------------------------------------------------------------------------- the move

    @Test
    fun `a move needs a free day that gives the cause more gap`() {
        val tiredRite = rite("Tired", 3)
        val causeRite = rite("Cause", 1)
        val blocked = listOf(tiredRite, causeRite, rite("Thu", 4), rite("Fri", 5), rite("Sat", 6), rite("Sun", 7))
        assertNull("Thursday to Sunday are taken", moveFor(blocked, 0, 1))
        val free = listOf(tiredRite, causeRite, rite("Thu", 4))
        assertEquals("Thursday is taken, Friday is the nearest free", 5, moveFor(free, 0, 1)?.to)
        assertNull("an unscheduled cause has no gap to widen", moveFor(listOf(tiredRite, rite("Cause", null)), 0, 1))
        assertNull("a routine is not moved off itself", moveFor(listOf(tiredRite), 0, 0))
    }

    @Test
    fun `a move never wraps past the cause coming round again`() {
        // Cause Monday, tired Saturday: gap 5, only Sunday (gap 6) is more.
        val week = listOf(rite("Tired", 6), rite("Cause", 1))
        assertEquals(7, moveFor(week, 0, 1)?.to)
        // Tired Sunday is already six days after: nothing is more.
        assertNull(moveFor(listOf(rite("Tired", 7), rite("Cause", 1)), 0, 1))
    }

    @Test
    fun `trimming the cause lowers the projected level and never raises it`() {
        val finding = buildReport(input()).recovery.single().finding
        val before = FatigueLevel.of(finding.residual)
        assertTrue(RecoveryFinding.projected(finding, 4) <= before)
        assertEquals("a single-set lift is left alone", before, RecoveryFinding.projected(finding, 1))
    }
}
