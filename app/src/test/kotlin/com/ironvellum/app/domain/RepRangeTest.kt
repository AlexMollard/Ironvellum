package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A Straight or Drop-set lift's own rep (or seconds) range: stored, shown, hinted and progressed through. */
class RepRangeTest {

    private val range = SetScheme(workingReps = 10..15)
    private val kg: (Double) -> String = { (if (it == Math.floor(it)) it.toLong().toString() else it.toString()) + "kg" }

    private fun set(id: Long, kind: SetKind, done: Boolean = false) =
        SessionSet(id = id, exerciseId = 1, setIndex = id.toInt(), reps = 10, weightKg = 24.0, done = done, kind = kind)

    // ---- the stored form

    @Test
    fun `a range rides in the scheme string and an old row with one target reads as it did`() {
        assertEquals("t=straight;rr=10-15", range.encode())
        assertEquals(range, SetScheme.decode("t=straight;rr=10-15"))
        assertEquals(SetScheme(), SetScheme.decode(null))
        assertNull(SetScheme.decode("t=straight;wr=1").workingReps)
        // A single figure is not a range: the lift keeps its one target.
        assertNull(SetScheme.decode("t=straight;rr=12").workingReps)
        assertNull(SetScheme.decode("t=straight;rr=junk").workingReps)
        val drops = SetScheme(type = SchemeType.DROPS, workingReps = 8..12)
        assertEquals(drops, SetScheme.decode(drops.encode()))
    }

    @Test
    fun `only straight and drop sets keep a range`() {
        assertEquals(10..15, range.normalised().workingReps)
        assertEquals(8..12, SetScheme(type = SchemeType.DROPS, workingReps = 8..12).normalised().workingReps)
        assertNull(SetScheme(type = SchemeType.TOP_BACKOFF, workingReps = 8..12).normalised().workingReps)
        assertNull(SetScheme.decode("t=top_backoff;rr=8-12").workingReps)
        // A superset turns drops straight: the range goes with it.
        assertEquals(8..12, SetScheme(type = SchemeType.DROPS, workingReps = 8..12).withoutDrops().workingReps)
        assertNull(SetScheme.decode("t=custom;rr=10-15").workingReps)
    }

    @Test
    fun `the floor follows the entry's reps and a floor at the top ends the range`() {
        assertEquals(12..15, range.withFloor(12).workingReps)
        assertNull(range.withFloor(15).workingReps)
        assertNull(range.withFloor(20).workingReps)
        assertNull(SetScheme().withFloor(8).workingReps)
    }

    @Test
    fun `the top of the range steps from none to one wider and back`() {
        assertEquals(11, SetScheme.stepRepsUp(floor = 10, up = null, delta = 1))
        assertEquals(12, SetScheme.stepRepsUp(10, 11, 1))
        assertNull(SetScheme.stepRepsUp(10, 11, -1))
        assertNull(SetScheme.stepRepsUp(10, null, -1))
        assertEquals(500, SetScheme.stepRepsUp(499, 500, 1))
        assertNull(SetScheme.stepRepsUp(500, null, 1))
        // A 60-120 s plank is a range too.
        assertEquals(61, SetScheme.stepRepsUp(60, null, 1))
        assertEquals(60..120, SetScheme.decode(SetScheme(workingReps = 60..120).encode()).workingReps)
    }

    // ---- what it reads as

    @Test
    fun `the rite row figure and the today card show the range`() {
        assertEquals("3 × 10–15 @24kg", SchemeText.figure(range, 3, 10, 24.0, kg))
        assertEquals("3 × 10–15", SchemeText.figure(range, 3, 10, null, null))
        assertEquals("3 × 30–45s", SchemeText.figure(SetScheme(workingReps = 30..45), 3, 30, null, kg, hold = true))
        assertEquals("3 × 8–12 @20kg", SchemeText.figure(SetScheme(type = SchemeType.DROPS, workingReps = 8..12), 3, 8, 20.0, kg))
        // No range: the one target, as ever.
        assertEquals("3 × 10 @24kg", SchemeText.figure(SetScheme(), 3, 10, 24.0, kg))
    }

    @Test
    fun `the preview plans working sets and the drops after them in the range`() {
        val plan = SchemePlan.plan(SetScheme(type = SchemeType.DROPS, dropSets = 1, workingReps = 8..12), 2, 8, 20.0, LiftLoading(stepKg = 2.5))
        assertEquals(listOf(SetKind.WORKING, SetKind.WORKING, SetKind.DROP), plan.map { it.kind })
        assertEquals(8..12, plan[0].range)
        assertNull(plan[2].range)
    }

    @Test
    fun `a working set still to log is asked for the range, in reps or seconds`() {
        val sets = listOf(set(1, SetKind.WORKING, done = true), set(2, SetKind.WORKING))
        val hints = TrialHints.forLift(sets, range, null, false, LiftLoading(stepKg = 2.5), { "$it" })
        assertEquals("Aim for 10–15 reps", hints[2])
        assertNull(hints[1])
        val holds = TrialHints.forLift(sets, SetScheme(workingReps = 30..45), null, false, LiftLoading(stepKg = 2.5), { "$it" }, hold = true)
        assertEquals("Aim for 30–45 s", holds[2])
        assertTrue(TrialHints.forLift(sets, SetScheme(), null, false, LiftLoading(stepKg = 2.5), { "$it" }).isEmpty())
    }

    // ---- progression through it

    private fun attempts(reps: Int, load: Double? = 20.0) = List(3) { Progression.Attempt(load, reps) }

    @Test
    fun `reps climb inside the range and the load only moves from its ceiling, in either mode`() {
        for (mode in TrainingMode.entries) {
            // 3 x 10 at the floor of 10-15: same load, two more reps.
            val climb = Progression.fromSets(mode, targetReps = 8, minSets = 3, attempts(10), range = 10..15)
            assertEquals(20.0, climb.weightKg!!, 1e-9)
            assertEquals(12, climb.reps)
            // 3 x 15 is the ceiling: 2.5 kg more and back to the floor.
            val up = Progression.fromSets(mode, targetReps = 8, minSets = 3, attempts(15), range = 10..15)
            assertEquals(22.5, up.weightKg!!, 1e-9)
            assertEquals(10, up.reps)
            // A set under the floor is not cleared: repeat.
            val short = Progression.fromSets(mode, targetReps = 8, minSets = 3, listOf(Progression.Attempt(20.0, 10), Progression.Attempt(20.0, 9), Progression.Attempt(20.0, 9)), range = 10..15)
            assertEquals(20.0, short.weightKg!!, 1e-9)
            assertEquals(10, short.reps)
        }
    }

    @Test
    fun `the climb stops at the ceiling rather than overshooting it`() {
        val rec = Progression.fromSets(TrainingMode.STRENGTH, targetReps = 8, minSets = 3, attempts(14), range = 10..15)
        assertEquals(15, rec.reps)
        assertEquals(20.0, rec.weightKg!!, 1e-9)
    }

    @Test
    fun `without a range the mode's band is as it was`() {
        val strength = Progression.fromSets(TrainingMode.STRENGTH, targetReps = 5, minSets = 3, attempts(5))
        assertEquals(22.5, strength.weightKg!!, 1e-9)
        assertEquals(3, strength.reps)
    }

    @Test
    fun `an unloaded bodyweight lift climbs its range and asks for load at the top of it, not at fifteen`() {
        // A pistol at 5-8: 6 climbs to 8, 8 is the top and asks for a load rather than going to 10.
        val pistol = { reps: Int ->
            Progression.fromSets(TrainingMode.STRENGTH, 5, 3, attempts(reps, null), exerciseName = "Pistol Squat", lever = Progression.Lever.REPS_THEN_LOAD, range = 5..8)
        }
        assertEquals(8, pistol(6).reps)
        assertEquals(8, pistol(7).reps)
        assertTrue(pistol(8).reason.startsWith("Reps are high enough"))
        assertEquals(8, pistol(8).reps)
        // A push-up at 20-30 climbs to 22 rather than calling 20 "high enough" at the old fifteen.
        val pushUp = Progression.fromSets(TrainingMode.STRENGTH, 20, 3, attempts(20, null), exerciseName = "Push-up", lever = Progression.Lever.REPS_THEN_LOAD, range = 20..30)
        assertEquals(22, pushUp.reps)
        // No range: the bodyweight ceiling of 15, as ever.
        val plain = Progression.fromSets(TrainingMode.STRENGTH, 10, 3, attempts(14, null), exerciseName = "Push-up", lever = Progression.Lever.REPS_THEN_LOAD)
        assertEquals(15, plain.reps)
    }

    @Test
    fun `ending the drops keeps the lifters range in the rite`() {
        val planned = SetScheme(type = SchemeType.DROPS, workingReps = 8..12)
        val rows = listOf(set(1, SetKind.WORKING, done = true), set(2, SetKind.WORKING, done = true))
        assertEquals(8..12, RoutineUpdate.inferScheme(planned, rows).workingReps)
        assertEquals(SchemeType.STRAIGHT, RoutineUpdate.inferScheme(planned, rows).type)
    }

    @Test
    fun `a hold climbs its seconds to the top of the range and stays there`() {
        val grow = Progression.fromSets(TrainingMode.STRENGTH, 30, 3, attempts(40, null), exerciseName = "Plank", lever = Progression.Lever.HOLD, range = 30..45)
        assertEquals(45, grow.reps)
        val top = Progression.fromSets(TrainingMode.STRENGTH, 30, 3, attempts(45, null), exerciseName = "Plank", lever = Progression.Lever.HOLD, range = 30..45)
        assertEquals(45, top.reps)
        val open = Progression.fromSets(TrainingMode.STRENGTH, 30, 3, attempts(40, null), exerciseName = "Plank", lever = Progression.Lever.HOLD)
        assertEquals(45, open.reps)
        val unbounded = Progression.fromSets(TrainingMode.STRENGTH, 30, 3, attempts(45, null), exerciseName = "Plank", lever = Progression.Lever.HOLD)
        assertEquals(50, unbounded.reps)
    }
}
