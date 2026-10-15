package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Bringing a preset in line with the session the lifter actually did. */
class RoutineUpdateTest {

    private val metrics = mapOf(1L to ExerciseMetric.REPS, 2L to ExerciseMetric.REPS, 3L to ExerciseMetric.HOLD, 4L to ExerciseMetric.DURATION)
    private val metricOf: (Long) -> ExerciseMetric? = { metrics[it] }

    private fun entry(id: Long, exerciseId: Long, position: Int, sets: Int, reps: Int, kg: Double?, modifiers: String = "") =
        PresetEntry(
            id = id,
            exerciseId = exerciseId,
            exerciseName = "Ex$exerciseId",
            targetSets = sets,
            targetReps = reps,
            targetWeightKg = kg,
            modifiers = modifiers,
            position = position,
        )

    private fun set(exerciseId: Long, position: Int, reps: Int, kg: Double?, done: Boolean = true, seconds: Int? = null, modifiers: String = "") =
        SessionSet(
            exerciseId = exerciseId,
            exercisePosition = position,
            setIndex = 0,
            reps = reps,
            weightKg = kg,
            done = done,
            durationSec = seconds,
            modifiers = modifiers,
        )

    @Test
    fun `sets follow the rows and reps the median of done sets, rounded down`() {
        // Planned 6, did 4 at the planned figures: the set-count guard keeps 6,
        // nothing else differs, so there is nothing to offer at all.
        val pull = entry(10, 1, 0, sets = 6, reps = 5, kg = 15.2)
        val sets = listOf(5, 5, 5, 3).map { set(1, 0, it, 15.2) } + set(1, 0, 5, 15.2, done = false) + set(1, 0, 5, 15.2, done = false)
        assertTrue(RoutineUpdate.propose(listOf(pull), sets, metricOf).isEmpty())
        // Different figures still propose; the two unticked rows keep the count.
        val heavier = listOf(5, 5, 5, 5).map { set(1, 0, it, 20.0) } + List(2) { set(1, 0, 5, 20.0, done = false) }
        val change = RoutineUpdate.propose(listOf(pull), heavier, metricOf).single()
        assertEquals(pull, change.before)
        assertEquals(pull.copy(targetSets = 6, targetWeightKg = 20.0), change.after)
        // Odd count: the middle one.
        val dips = entry(11, 2, 0, sets = 3, reps = 12, kg = null)
        val odd = RoutineUpdate.propose(listOf(dips), listOf(10, 9, 7).map { set(2, 0, it, null) }, metricOf).single()
        assertEquals(dips.copy(targetSets = 3, targetReps = 9), odd.after)
    }

    @Test
    fun `load is the one on most sets, a tie going to the heavier`() {
        val pull = entry(10, 1, 0, sets = 4, reps = 5, kg = 10.0)
        val mostly = listOf(20.0, 20.0, 20.0, 15.0).map { set(1, 0, 5, it) }
        assertEquals(20.0, RoutineUpdate.propose(listOf(pull), mostly, metricOf).single().after.targetWeightKg)
        val tie = listOf(15.0, 20.0, 15.0, 20.0).map { set(1, 0, 5, it) }
        assertEquals(20.0, RoutineUpdate.propose(listOf(pull), tie, metricOf).single().after.targetWeightKg)
        // Bodyweight stays bodyweight, and loses a tie to any load.
        val bodyweight = entry(11, 2, 0, sets = 4, reps = 10, kg = null)
        assertEquals(
            null,
            RoutineUpdate.propose(listOf(bodyweight), List(3) { set(2, 0, 8, null) } + set(2, 0, 8, null, done = false), metricOf)
                .single().after.targetWeightKg,
        )
        val bwTie = listOf(set(2, 0, 10, null), set(2, 0, 10, null), set(2, 0, 10, 5.0), set(2, 0, 10, 5.0))
        assertEquals(5.0, RoutineUpdate.propose(listOf(bodyweight), bwTie, metricOf).single().after.targetWeightKg)
    }

    @Test
    fun `skipped and unchanged entries are not listed, added movements are ignored`() {
        val pull = entry(10, 1, 0, sets = 3, reps = 5, kg = 15.0)
        val dips = entry(11, 2, 1, sets = 3, reps = 10, kg = null)
        val sets = List(3) { set(1, 0, 5, 15.0) } + // done exactly as planned
            List(3) { set(2, 1, 10, null, done = false) } + // never ticked off
            List(4) { set(99, 2, 12, 30.0) } // added mid-session
        assertTrue(RoutineUpdate.propose(listOf(pull, dips), sets, metricOf).isEmpty())
    }

    @Test
    fun `a hold proposes its seconds into targetReps`() {
        val lSit = entry(12, 3, 0, sets = 3, reps = 20, kg = null)
        val sets = listOf(30, 25, 28).map { set(3, 0, 0, null, seconds = it) }
        assertEquals(lSit.copy(targetReps = 28), RoutineUpdate.propose(listOf(lSit), sets, metricOf).single().after)
        assertTrue(RoutineUpdate.propose(listOf(lSit), sets, metricOf).single().isHold)
    }

    @Test
    fun `changed modifiers are proposed, a reordering of the same tags is not`() {
        val hspu = entry(13, 2, 0, sets = 2, reps = 6, kg = null, modifiers = "Deficit, Pause")
        val same = List(2) { set(2, 0, 6, null, modifiers = "Pause, Deficit") }
        assertTrue(RoutineUpdate.propose(listOf(hspu), same, metricOf).isEmpty())
        val changed = List(2) { set(2, 0, 6, null, modifiers = "Deficit") }
        assertEquals(hspu.copy(modifiers = "Deficit"), RoutineUpdate.propose(listOf(hspu), changed, metricOf).single().after)
    }

    @Test
    fun `the same movement twice matches block by block, activities are left alone`() {
        val heavy = entry(20, 1, 0, sets = 3, reps = 3, kg = 25.0)
        val run = entry(21, 4, 1, sets = 1, reps = 20, kg = null)
        val backoff = entry(22, 1, 2, sets = 3, reps = 8, kg = null)
        val sets = List(3) { set(1, 0, 3, 25.0) } + // heavy as planned
            set(4, 1, 30, null, seconds = 1800) +
            List(2) { set(1, 2, 8, null) } + set(1, 2, 8, null, done = false) // back-off: one set short
        // The back-off left one set unticked; its row keeps the planned 3 sets
        // and its figures already match, so there is no proposal at all.
        assertTrue(RoutineUpdate.propose(listOf(heavy, run, backoff), sets, metricOf).isEmpty())
    }

    @Test
    fun `an unticked set never shrinks the set count and every field starts ticked`() {
        val pull = entry(10, 1, 0, sets = 6, reps = 5, kg = 15.0)
        val short = List(4) { set(1, 0, 6, 17.5) } + List(2) { set(1, 0, 6, 17.5, done = false) }
        val change = RoutineUpdate.propose(listOf(pull), short, metricOf).single()
        val sets = RoutineUpdate.Field.SETS
        val reps = RoutineUpdate.Field.REPS
        val load = RoutineUpdate.Field.LOAD
        // Four of six rows ticked: the unticked two keep the count.
        assertEquals(listOf(reps, load), change.fields)
        assertEquals(6, change.after.targetSets)
        assertEquals(setOf(reps, load), change.defaultTicks())
        // Narrowed to reps: load keeps its plan.
        assertEquals(pull.copy(targetReps = 6), change.only(setOf(reps))!!.after)
        assertEquals(pull, change.only(setOf(reps))!!.before)
        assertEquals(null, change.only(emptySet()))
        // Accepting a field the change does not move is no change at all.
        assertEquals(null, change.only(setOf(RoutineUpdate.Field.MODIFIERS)))
        // More sets than planned is proposed like any other field.
        val long = RoutineUpdate.propose(listOf(pull), List(7) { set(1, 0, 5, 15.0) }, metricOf).single()
        assertEquals(listOf(sets), long.fields)
        assertEquals(7, long.after.targetSets)
        // One ticked set of a three-set movement at the planned figures, the
        // other two left unticked, offers nothing, certainly not "3 sets -> 1".
        val trio = entry(12, 1, 0, sets = 3, reps = 8, kg = 12.5)
        val lone = listOf(set(1, 0, 8, 12.5)) + List(2) { set(1, 0, 8, 12.5, done = false) }
        assertTrue(RoutineUpdate.propose(listOf(trio), lone, metricOf).isEmpty())
    }

    @Test
    fun `the set count follows the rows - deleted drops unticked, added rises ticked`() {
        val trio = entry(12, 1, 0, sets = 3, reps = 8, kg = 12.5)
        val sets = RoutineUpdate.Field.SETS
        // A row deleted: offered as 3 -> 2, but left for the reader to tick.
        val deleted = RoutineUpdate.propose(listOf(trio), List(2) { set(1, 0, 8, 12.5) }, metricOf).single()
        assertEquals(listOf(sets), deleted.fields)
        assertEquals(2, deleted.after.targetSets)
        assertEquals(emptySet<RoutineUpdate.Field>(), deleted.defaultTicks())
        // A row left unticked: a short day, the plan stands.
        val unticked = List(2) { set(1, 0, 8, 12.5) } + set(1, 0, 8, 12.5, done = false)
        assertTrue(RoutineUpdate.propose(listOf(trio), unticked, metricOf).isEmpty())
        // A row added: 3 -> 4, ticked like any other change.
        val added = RoutineUpdate.propose(listOf(trio), List(4) { set(1, 0, 8, 12.5) }, metricOf).single()
        assertEquals(4, added.after.targetSets)
        assertEquals(setOf(sets), added.defaultTicks())
        // A deleted row beside a real change: the change starts ticked, the drop does not.
        val heavier = RoutineUpdate.propose(listOf(trio), List(2) { set(1, 0, 8, 15.0) }, metricOf).single()
        assertEquals(setOf(RoutineUpdate.Field.LOAD), heavier.defaultTicks())
        // Rows but nothing ticked: a skipped movement proposes nothing, not even its count.
        assertTrue(RoutineUpdate.propose(listOf(trio), List(2) { set(1, 0, 8, 12.5, done = false) }, metricOf).isEmpty())
    }

    private fun grouped(exerciseId: Long, position: Int, group: Int?, reps: Int = 8, kg: Double? = 40.0) =
        set(exerciseId, position, reps, kg).copy(supersetGroup = group)

    @Test
    fun `a lift added in the trial is offered with today's figures, after the lift it followed`() {
        val rite = listOf(entry(10, 1, 0, 3, 5, 60.0), entry(11, 2, 1, 3, 8, 20.0))
        val sets = listOf(set(1, 0, 5, 60.0), set(5, 1, 12, 15.0), set(5, 1, 10, 15.0), set(2, 2, 8, 20.0))
        val adds = RoutineUpdate.additions(setOf(1L, 2L), sets, emptySet(), { ExerciseMetric.REPS })
        val add = adds.single()
        assertEquals(5L, add.entry.exerciseId)
        assertEquals(1L, add.afterExerciseId)
        assertEquals(2, add.entry.targetSets)
        assertEquals(11, add.entry.targetReps)
        assertEquals(15.0, add.entry.targetWeightKg)
        // A swap's newcomer is the swap's, a warm-up exercise of the rite is not new, a skipped add says nothing.
        assertTrue(RoutineUpdate.additions(setOf(1L, 2L), sets, setOf(5L), { ExerciseMetric.REPS }).isEmpty())
        assertTrue(RoutineUpdate.additions(setOf(1L, 2L, 5L), sets, emptySet(), { ExerciseMetric.REPS }).isEmpty())
        assertTrue(RoutineUpdate.additions(setOf(1L), listOf(set(5, 1, 10, 15.0, done = false)), emptySet(), { ExerciseMetric.REPS }).isEmpty())
        // Kept, it lands second, between the two, with positions renumbered.
        val arranged = RoutineUpdate.arrange(rite, adds, emptyList())
        assertEquals(listOf(1L, 5L, 2L), arranged.map { it.exerciseId })
        assertEquals(listOf(0, 1, 2), arranged.map { it.position })
        assertEquals(0L, arranged[1].id)
        // Unticked, nothing is passed and the rite is as it was.
        assertEquals(rite.map { it.exerciseId }, RoutineUpdate.arrange(rite, emptyList(), emptyList()).map { it.exerciseId })
    }

    @Test
    fun `a superset made in the trial is offered as a link, and linking moves the partner next to it`() {
        val rite = listOf(entry(10, 1, 0, 3, 5, 60.0), entry(11, 2, 1, 3, 8, 20.0), entry(12, 3, 2, 3, 8, 20.0))
        val sets = listOf(grouped(1, 0, 1), grouped(3, 1, 1), set(2, 2, 8, 20.0))
        val link = RoutineUpdate.links(rite, sets, emptySet()).single()
        assertEquals(listOf(1L, 3L), link.exerciseIds)
        val arranged = RoutineUpdate.arrange(rite, emptyList(), listOf(link))
        assertEquals(listOf(1L, 3L, 2L), arranged.map { it.exerciseId })
        assertEquals(arranged[0].supersetGroup, arranged[1].supersetGroup)
        assertTrue(arranged[0].supersetGroup != null)
        assertEquals(null, arranged[2].supersetGroup)
        // Already a superset in the rite: nothing to offer.
        val already = rite.map { if (it.exerciseId == 1L || it.exerciseId == 3L) it.copy(supersetGroup = 1) else it }
            .let { listOf(it[0], it[2], it[1]).mapIndexed { i, e -> e.copy(position = i) } }
        assertTrue(RoutineUpdate.links(already, sets, emptySet()).isEmpty())
        // No superset in the trial, none offered.
        assertTrue(RoutineUpdate.links(rite, listOf(set(1, 0, 5, 60.0), set(2, 1, 8, 20.0)), emptySet()).isEmpty())
    }

    @Test
    fun `a link to an added lift is offered and applies once the lift is kept`() {
        val rite = listOf(entry(10, 1, 0, 3, 5, 60.0))
        val sets = listOf(grouped(1, 0, 1), grouped(5, 1, 1))
        val adds = RoutineUpdate.additions(setOf(1L), sets, emptySet(), { ExerciseMetric.REPS })
        val link = RoutineUpdate.links(rite, sets, adds.map { it.entry.exerciseId }.toSet()).single()
        // Link without the lift: skipped. With it: both grouped.
        assertEquals(listOf(1L), RoutineUpdate.arrange(rite, emptyList(), listOf(link)).map { it.exerciseId })
        val both = RoutineUpdate.arrange(rite, adds, listOf(link))
        assertEquals(listOf(1L, 5L), both.map { it.exerciseId })
        assertTrue(both.all { it.supersetGroup != null })
        // A lift outside both the rite and the additions drops the link.
        assertTrue(RoutineUpdate.links(rite, listOf(grouped(1, 0, 1), grouped(9, 1, 1)), emptySet()).isEmpty())
    }
}
