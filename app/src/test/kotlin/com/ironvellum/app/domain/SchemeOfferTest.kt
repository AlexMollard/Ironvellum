package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What a trial that ran a lift differently offers to write back to the rite: the Sets line, ticked or not. */
class SchemeOfferTest {

    private val metricOf: (Long) -> ExerciseMetric? = { ExerciseMetric.REPS }

    private fun entry(sets: Int, reps: Int, kg: Double?, scheme: SetScheme = SetScheme()) = PresetEntry(
        id = 10, exerciseId = 1, exerciseName = "Chin-up", targetSets = sets, targetReps = reps, targetWeightKg = kg, position = 0, scheme = scheme,
    )

    private fun row(index: Int, kind: SetKind, reps: Int, kg: Double?, done: Boolean = true) = SessionSet(
        exerciseId = 1, exerciseName = "Chin-up", exercisePosition = 0, setIndex = index, reps = reps, weightKg = kg, done = done, kind = kind,
    )

    private val topScheme = SetScheme(type = SchemeType.TOP_BACKOFF, backoffSets = 2)

    @Test
    fun `making set one a top set for the lift is offered ticked, with the load that moved`() {
        val rite = entry(3, 8, 7.7)
        val sets = listOf(
            row(0, SetKind.TOP, 5, 10.0), row(1, SetKind.BACKOFF, 7, 8.0), row(2, SetKind.BACKOFF, 7, 8.0),
        )
        val change = RoutineUpdate.propose(listOf(rite), sets, metricOf).single()
        assertEquals(topScheme.copy(), change.after.scheme)
        assertEquals("3 straight", SchemeText.clause(change.before.scheme, change.before.targetSets))
        assertEquals("top + 2 back-offs", SchemeText.clause(change.after.scheme, change.after.targetSets))
        // The scheme owns the count and the reps, so only the sets line and the top load show.
        assertEquals(listOf(RoutineUpdate.Field.LOAD, RoutineUpdate.Field.SCHEME), change.fields)
        assertEquals(setOf(RoutineUpdate.Field.LOAD, RoutineUpdate.Field.SCHEME), change.defaultTicks())
        assertEquals(10.0, change.after.targetWeightKg!!, 0.0)
        // Accepting only the sets line writes the scheme, its count and its reps, and leaves the load.
        val narrowed = change.only(setOf(RoutineUpdate.Field.SCHEME))!!
        assertEquals(topScheme, narrowed.after.scheme)
        assertEquals(3, narrowed.after.targetSets)
        assertEquals(7.7, narrowed.after.targetWeightKg!!, 0.0)
        assertNull(change.only(emptySet()))
    }

    @Test
    fun `a one-off retag of a single set is offered unticked`() {
        val rite = entry(3, 8, 7.7)
        val sets = listOf(row(0, SetKind.WORKING, 8, 7.7), row(1, SetKind.WORKING, 8, 7.7), row(2, SetKind.FAILURE, 9, 7.7))
        val change = RoutineUpdate.propose(listOf(rite), sets, metricOf).single()
        assertEquals(SchemeType.CUSTOM, change.after.scheme.type)
        assertEquals(listOf(RoutineUpdate.Field.SCHEME), change.fields)
        assertTrue(change.defaultTicks().isEmpty())
        // One back-off among working sets is a retag too, not a scheme.
        val lone = listOf(row(0, SetKind.WORKING, 8, 7.7), row(1, SetKind.BACKOFF, 8, 7.0), row(2, SetKind.WORKING, 8, 7.7))
        assertTrue(RoutineUpdate.propose(listOf(rite), lone, metricOf).single().defaultTicks().isEmpty())
        // A lone top set on the first row, the rest left working, is not a top scheme either.
        val onlyTop = listOf(row(0, SetKind.TOP, 5, 9.0), row(1, SetKind.WORKING, 8, 7.7), row(2, SetKind.WORKING, 8, 7.7))
        assertEquals(SchemeType.CUSTOM, RoutineUpdate.propose(listOf(rite), onlyTop, metricOf).single().after.scheme.type)
    }

    @Test
    fun `a drop added to the last set is a lift-wide change and starts ticked`() {
        val rite = entry(3, 10, 12.0)
        val sets = listOf(
            row(0, SetKind.WORKING, 10, 12.0), row(1, SetKind.WORKING, 10, 12.0), row(2, SetKind.WORKING, 10, 12.0),
            row(3, SetKind.DROP, 8, 9.5),
        )
        val change = RoutineUpdate.propose(listOf(rite), sets, metricOf).single()
        assertEquals(SchemeType.DROPS, change.after.scheme.type)
        assertEquals(1, change.after.scheme.dropSets)
        assertEquals(1, change.after.scheme.dropCount)
        // 9.5 against 12.0 is about 20% off: the nearest 5%.
        assertEquals(20, change.after.scheme.dropPercent)
        // The drop is an extra set, not one of the three, and the drop's load never moves the rite's load.
        assertEquals(3, change.after.targetSets)
        assertEquals(12.0, change.after.targetWeightKg!!, 0.0)
        assertEquals(listOf(RoutineUpdate.Field.SCHEME), change.fields)
        assertEquals(setOf(RoutineUpdate.Field.SCHEME), change.defaultTicks())
    }

    @Test
    fun `a trial that ran the top scheme as planned offers nothing, and back-offs never move the load`() {
        // The owner's rite: +22 kg x 2-4, then three fixed +15 kg x 4-5 back-offs.
        val owners = SetScheme(type = SchemeType.TOP_BACKOFF, topReps = 2..4, backoffSets = 3, backoffFixedKg = 15.0, backoffReps = 4..5)
        val rite = entry(4, 2, 22.0, owners)
        val sets = listOf(
            row(0, SetKind.TOP, 3, 22.0), row(1, SetKind.BACKOFF, 5, 15.0), row(2, SetKind.BACKOFF, 5, 15.0), row(3, SetKind.BACKOFF, 4, 15.0),
        )
        assertTrue(RoutineUpdate.propose(listOf(rite), sets, metricOf).isEmpty())
        // A heavier top moves the load, from the top set alone.
        val heavier = sets.toMutableList().also { it[0] = row(0, SetKind.TOP, 3, 24.0) }
        val change = RoutineUpdate.propose(listOf(rite), heavier, metricOf).single()
        assertEquals(listOf(RoutineUpdate.Field.LOAD), change.fields)
        assertEquals(24.0, change.after.targetWeightKg!!, 0.0)
        assertEquals(owners, change.after.scheme)
    }

    @Test
    fun `a back-off taken out of a top scheme is offered unticked`() {
        val rite = entry(3, 4, 20.0, topScheme)
        val sets = listOf(row(0, SetKind.TOP, 4, 20.0), row(1, SetKind.BACKOFF, 6, 18.0))
        val change = RoutineUpdate.propose(listOf(rite), sets, metricOf).single()
        assertEquals(1, change.after.scheme.backoffSets)
        assertEquals(listOf(RoutineUpdate.Field.SCHEME), change.fields)
        assertTrue(change.defaultTicks().isEmpty())
        // An extra back-off is a bigger plan, ticked.
        val more = sets + row(2, SetKind.BACKOFF, 6, 18.0) + row(3, SetKind.BACKOFF, 6, 18.0)
        assertEquals(setOf(RoutineUpdate.Field.SCHEME), RoutineUpdate.propose(listOf(rite), more, metricOf).single().defaultTicks())
    }

    @Test
    fun `straight sets that lose their scheme in the trial offer it unticked`() {
        assertFalse(RoutineUpdate.schemeTickedByDefault(topScheme, SetScheme()))
        assertTrue(RoutineUpdate.schemeTickedByDefault(SetScheme(), topScheme))
        assertFalse(RoutineUpdate.schemeTickedByDefault(topScheme, SetScheme(type = SchemeType.CUSTOM)))
    }

    @Test
    fun `plain straight sets still offer a count and a load exactly as before`() {
        val rite = entry(3, 8, 7.7)
        val sets = List(4) { row(it, SetKind.WORKING, 8, 10.0) }
        val change = RoutineUpdate.propose(listOf(rite), sets, metricOf).single()
        assertEquals(listOf(RoutineUpdate.Field.SETS, RoutineUpdate.Field.LOAD), change.fields)
        assertEquals(SetScheme(), change.after.scheme)
    }

    @Test
    fun `a warm-up never reads as part of the scheme`() {
        val rite = entry(3, 4, 20.0, topScheme)
        val sets = listOf(row(0, SetKind.WARMUP, 5, 10.0, done = false)) +
            listOf(row(1, SetKind.TOP, 4, 20.0), row(2, SetKind.BACKOFF, 6, 18.0), row(3, SetKind.BACKOFF, 6, 18.0))
        assertTrue(RoutineUpdate.propose(listOf(rite), sets, metricOf).isEmpty())
    }
}
