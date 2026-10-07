package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A ticked set hands the figures the lifter moved by hand to the later sets of
 * its movement, never over a figure the lifter moved on them, and a plain tick
 * of a prescribed set carries nothing (a pyramid stays a pyramid).
 */
class CarryLoadTest {

    private fun set(i: Int, kg: Double?, reps: Int = 8, done: Boolean = false, seconds: Int? = null, warmup: Boolean = false) =
        CarrySet(id = i.toLong(), setIndex = i, weightKg = kg, done = done, reps = reps, durationSec = seconds, warmup = warmup)

    private val lifting = carryFiguresFor(ExerciseMetric.REPS)

    /** The lifter changed [before] into [after] by hand while it waited, then logged it. */
    private fun edited(edits: CarryEdits, before: CarrySet, after: CarrySet): CarrySet {
        edits.note(before, after)
        return after.copy(done = true)
    }

    /** What the later sets read after the carries are applied. */
    private fun applied(sets: List<CarrySet>, carries: List<Carry>): List<Triple<Double?, Int, Int?>> =
        sets.map { s ->
            val c = carries.firstOrNull { it.id == s.id }
            Triple(c?.weightKg ?: s.weightKg, c?.reps ?: s.reps, c?.durationSec ?: s.durationSec)
        }

    @Test
    fun `a load moved on the first set flows to the rest of a three by eight`() {
        val edits = CarryEdits()
        val plan = listOf(set(1, 80.0), set(2, 80.0), set(3, 80.0))
        val logged = edited(edits, plan[0], plan[0].copy(weightKg = 82.5))
        val carries = carriesFrom(logged, listOf(logged) + plan.drop(1), lifting, edits)
        assertEquals(listOf(Triple(82.5, 8, null), Triple(82.5, 8, null)), applied(plan.drop(1), carries))
    }

    @Test
    fun `reps moved on the first set flow on and the load stays`() {
        val edits = CarryEdits()
        val plan = listOf(set(1, 80.0), set(2, 80.0), set(3, 80.0))
        val logged = edited(edits, plan[0], plan[0].copy(reps = 6))
        val carries = carriesFrom(logged, listOf(logged) + plan.drop(1), lifting, edits)
        assertEquals(listOf(Triple(80.0, 6, null), Triple(80.0, 6, null)), applied(plan.drop(1), carries))
    }

    @Test
    fun `load and reps moved together both flow on`() {
        val edits = CarryEdits()
        val plan = listOf(set(1, 80.0), set(2, 80.0))
        val logged = edited(edits, plan[0], plan[0].copy(weightKg = 77.5, reps = 10))
        val carries = carriesFrom(logged, listOf(logged, plan[1]), lifting, edits)
        assertEquals(listOf(Triple(77.5, 10, null)), applied(listOf(plan[1]), carries))
    }

    @Test
    fun `a set the lifter edited by hand keeps its own figures`() {
        val edits = CarryEdits()
        val plan = listOf(set(1, 80.0), set(2, 80.0), set(3, 80.0))
        // Set 3 was typed to 90 x 5 earlier.
        edits.note(plan[2], plan[2].copy(weightKg = 90.0, reps = 5))
        val handSet3 = plan[2].copy(weightKg = 90.0, reps = 5)
        val logged = edited(edits, plan[0], plan[0].copy(weightKg = 82.5, reps = 6))
        val carries = carriesFrom(logged, listOf(logged, plan[1], handSet3), lifting, edits)
        assertEquals(listOf(2L), carries.map { it.id })
        assertEquals(Triple(90.0, 5, null), applied(listOf(handSet3), carries).single())
    }

    @Test
    fun `a hand edit of the load alone does not shield the reps`() {
        val edits = CarryEdits()
        val plan = listOf(set(1, 80.0), set(2, 80.0))
        edits.note(plan[1], plan[1].copy(weightKg = 85.0))
        val handSet2 = plan[1].copy(weightKg = 85.0)
        val logged = edited(edits, plan[0], plan[0].copy(weightKg = 82.5, reps = 6))
        val carries = carriesFrom(logged, listOf(logged, handSet2), lifting, edits)
        assertEquals(Triple(85.0, 6, null), applied(listOf(handSet2), carries).single())
    }

    @Test
    fun `a tick that moved nothing leaves a pyramid alone`() {
        val edits = CarryEdits()
        val pyramid = listOf(set(1, 60.0, reps = 10), set(2, 70.0, reps = 8), set(3, 80.0, reps = 6))
        val logged = pyramid[0].copy(done = true)
        assertEquals(emptyList<Carry>(), carriesFrom(logged, pyramid, lifting, edits))
    }

    @Test
    fun `a first tick still fills the sets that have no load`() {
        val edits = CarryEdits()
        val sets = listOf(set(1, 60.0, done = true), set(2, null), set(3, 0.0), set(4, 55.0))
        val carries = carriesFrom(sets[0], sets, lifting, edits)
        assertEquals(listOf(2L, 3L), carries.map { it.id })
        assertTrue(carries.all { it.weightKg == 60.0 && it.reps == null })
    }

    @Test
    fun `earlier sets, done sets and warm-ups are left alone`() {
        val edits = CarryEdits()
        val sets = listOf(
            set(0, 80.0), set(1, 80.0), set(2, 80.0, done = true), set(3, 80.0, warmup = true), set(4, 80.0),
        )
        val logged = edited(edits, sets[1], sets[1].copy(weightKg = 85.0))
        val carries = carriesFrom(logged, sets.map { if (it.id == 1L) logged else it }, lifting, edits)
        assertEquals(listOf(4L), carries.map { it.id })
    }

    @Test
    fun `a tick with no load carries no load`() {
        val edits = CarryEdits()
        val sets = listOf(set(1, null, done = true), set(2, 40.0))
        val logged = edited(edits, set(1, null), set(1, null, reps = 12))
        val carries = carriesFrom(logged, listOf(logged, sets[1]), lifting, edits)
        assertEquals(listOf(Triple(40.0, 12, null)), applied(listOf(sets[1]), carries))
    }

    @Test
    fun `a hold carries its seconds and its load`() {
        val edits = CarryEdits()
        val plan = listOf(set(1, 10.0, reps = 0, seconds = 30), set(2, 10.0, reps = 0, seconds = 30))
        val logged = edited(edits, plan[0], plan[0].copy(durationSec = 45, weightKg = 12.5))
        val carries = carriesFrom(logged, listOf(logged, plan[1]), carryFiguresFor(ExerciseMetric.HOLD), edits)
        assertEquals(listOf(Triple(12.5, 0, 45)), applied(listOf(plan[1]), carries))
    }

    @Test
    fun `activity work carries a load into unloaded sets and nothing else`() {
        val edits = CarryEdits()
        val plan = listOf(set(1, null, reps = 1, seconds = 600), set(2, null, reps = 1, seconds = 600))
        val logged = edited(edits, plan[0], plan[0].copy(weightKg = 5.0, durationSec = 900, reps = 3))
        val carries = carriesFrom(logged, listOf(logged, plan[1]), carryFiguresFor(ExerciseMetric.DURATION), edits)
        assertEquals(listOf(Triple(5.0, 1, 600)), applied(listOf(plan[1]), carries))
    }
}
