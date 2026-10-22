package com.ironvellum.app.domain

import com.ironvellum.app.domain.Progression.Lever
import com.ironvellum.app.domain.ProgressionSuggestion.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A trial starts on the routine's written figures; what the engine would have applied is a suggestion. These pin the
 * suggestion to the engine's own figures, so "what it suggests" is "what it used to apply".
 */
class ProgressionSuggestionTest {

    private fun cleared(kg: Double?, reps: Int, sets: Int = 3) = List(sets) { Progression.Attempt(kg, reps) }

    private fun suggest(
        recommendation: Progression.Recommendation,
        writtenKg: Double?,
        writtenReps: Int,
        hold: Boolean = false,
    ) = Suggestions.forRecommendation(writtenKg, writtenReps, recommendation, hold)

    @Test
    fun `a cleared top of range suggests the load the engine used to apply`() {
        val rec = Progression.fromSets(TrainingMode.HYPERTROPHY, targetReps = 10, minSets = 3, cleared(20.0, 14))
        val suggestion = suggest(rec, writtenKg = 20.0, writtenReps = 10)!!
        assertEquals(Kind.MORE_LOAD, suggestion.kind)
        assertEquals(rec.weightKg!!, suggestion.weightKg!!, 0.0)
        assertEquals(rec.reps, suggestion.reps)
        assertEquals(22.5, suggestion.weightKg!!, 0.0)
        assertEquals(rec.reason, suggestion.reason)
        assertTrue(suggestion.movesFigures)
        assertTrue(suggestion.movesLoad)
    }

    @Test
    fun `reps below the ceiling suggest more reps at the same load`() {
        val rec = Progression.fromSets(TrainingMode.HYPERTROPHY, targetReps = 10, minSets = 3, cleared(20.0, 10))
        val suggestion = suggest(rec, 20.0, 10)!!
        assertEquals(Kind.MORE_REPS, suggestion.kind)
        assertEquals(12, suggestion.reps)
        assertEquals(20.0, suggestion.weightKg!!, 0.0)
        assertFalse(suggestion.movesLoad)
    }

    @Test
    fun `a stall suggests the deload and only while the routine still holds the stalled load`() {
        val stalled = List(3) { listOf(Progression.Attempt(100.0, 2)) }
        val rec = Progression.fromSessions(TrainingMode.STRENGTH, 5, 3, stalled)
        assertTrue(rec.deload)
        val suggestion = suggest(rec, writtenKg = 100.0, writtenReps = 5)!!
        assertEquals(Kind.DELOAD, suggestion.kind)
        assertEquals(90.0, suggestion.weightKg!!, 0.0)
        // The lifter already wrote the lighter load: nothing to suggest.
        assertNull(suggest(rec, writtenKg = 90.0, writtenReps = 5))
        assertNull(suggest(rec, writtenKg = 80.0, writtenReps = 5))
    }

    @Test
    fun `nothing is suggested when the routine already holds or beats what the engine says`() {
        val rec = Progression.fromSets(TrainingMode.HYPERTROPHY, targetReps = 10, minSets = 3, cleared(20.0, 14))
        assertNull("written is what the engine says", suggest(rec, 22.5, 10))
        assertNull("written is heavier than the engine says", suggest(rec, 25.0, 10))
    }

    @Test
    fun `a repeat or a first attempt says nothing`() {
        val missed = Progression.fromSets(TrainingMode.STRENGTH, 5, 5, listOf(Progression.Attempt(20.0, 10), Progression.Attempt(20.0, 6)))
        assertNull(suggest(missed, 20.0, 5))
        val first = Progression.fromSessions(TrainingMode.STRENGTH, 5, 3, emptyList())
        assertNull(suggest(first, 20.0, 5))
    }

    @Test
    fun `a bodyweight lift climbs reps and a loaded one is not invented for it`() {
        val rec = Progression.fromSets(TrainingMode.HYPERTROPHY, 8, 3, cleared(null, 8), lever = Lever.REPS_THEN_LOAD)
        val suggestion = suggest(rec, null, 8)!!
        assertEquals(Kind.MORE_REPS, suggestion.kind)
        assertEquals(10, suggestion.reps)
        assertNull(suggestion.weightKg)
    }

    @Test
    fun `a cleared hold suggests longer`() {
        val rec = Progression.fromSets(TrainingMode.STRENGTH, 30, 3, cleared(null, 30), lever = Lever.HOLD)
        val suggestion = suggest(rec, null, 30, hold = true)!!
        assertEquals(Kind.LONGER_HOLD, suggestion.kind)
        assertEquals(35, suggestion.reps)
    }

    @Test
    fun `control mobility and skill levers become cues with nothing to accept`() {
        listOf(Lever.CONTROL, Lever.MOBILITY, Lever.SKILL).forEach { lever ->
            val rec = Progression.fromSets(TrainingMode.STRENGTH, 8, 3, cleared(null, 8), lever = lever)
            val suggestion = suggest(rec, null, 8)
            assertNotNull("$lever", suggestion)
            assertEquals(Kind.CUE, suggestion!!.kind)
            assertFalse(suggestion.movesFigures)
            assertEquals(rec.reason, suggestion.reason)
            // A cue is on offer while there is a set to log, and goes with the block.
            val sets = listOf(SessionSet(1, 1, setIndex = 0, reps = 8))
            assertTrue(suggestion.stillOffered(sets))
            assertFalse(suggestion.stillOffered(sets.map { it.copy(done = true) }))
        }
    }

    @Test
    fun `a top set suggests where the last top set says to start`() {
        val beat = TopMark(reps = 4, weightKg = 22.0)
        // Full range: one step up at the floor.
        val up = SchemePlan.suggestTop(beat, 2..4, riteKg = 22.0) { kg -> (kg ?: 0.0) + 2.5 }
        val more = Suggestions.forTop(22.0, 2, up)!!
        assertEquals(Kind.MORE_LOAD, more.kind)
        assertEquals(24.5, more.weightKg!!, 0.0)
        assertEquals(2, more.reps)
        // Room in the range: one more rep at the same load.
        val rep = SchemePlan.suggestTop(TopMark(3, 22.0), 2..4, 22.0) { it }
        assertEquals(Kind.MORE_REPS, Suggestions.forTop(22.0, 2, rep)!!.kind)
        assertEquals(4, Suggestions.forTop(22.0, 2, rep)!!.reps)
        // A rite load set above the last one is a bump on purpose: it is what is written, so nothing to suggest.
        val bumped = SchemePlan.suggestTop(beat, 2..4, riteKg = 25.0) { it }
        assertNull(Suggestions.forTop(25.0, 2, bumped))
        assertNull(Suggestions.forTop(22.0, 2, null))
    }

    @Test
    fun `accepting touches only the sets still to log`() {
        val suggestion = ProgressionSuggestion(Kind.MORE_LOAD, 22.5, 8, hold = false, reason = "")
        val sets = listOf(
            SessionSet(1, 1, setIndex = 0, reps = 8, weightKg = 20.0, done = true),
            SessionSet(2, 1, setIndex = 1, reps = 8, weightKg = 20.0),
            SessionSet(3, 1, setIndex = 2, reps = 8, weightKg = 20.0, kind = SetKind.WARMUP),
            SessionSet(4, 1, setIndex = 3, reps = 8, weightKg = 16.0, kind = SetKind.BACKOFF),
            SessionSet(5, 1, setIndex = 4, reps = 8, weightKg = 18.0, kind = SetKind.DROP),
            SessionSet(6, 1, setIndex = 5, reps = 8, weightKg = 20.0, kind = SetKind.TOP),
        )
        assertEquals(listOf(2L, 5L, 6L), sets.filter { suggestion.touches(it) }.map { it.id })
    }

    @Test
    fun `a suggestion stays until the pending sets carry its figures`() {
        val suggestion = ProgressionSuggestion(Kind.MORE_LOAD, 22.5, 8, hold = false, reason = "")
        val sets = listOf(
            SessionSet(1, 1, setIndex = 0, reps = 8, weightKg = 20.0, done = true),
            SessionSet(2, 1, setIndex = 1, reps = 8, weightKg = 20.0),
        )
        assertTrue(suggestion.stillOffered(sets))
        // Accepted (or typed in by hand): the pending set carries it; the logged one keeps what was done.
        assertFalse(suggestion.stillOffered(sets.map { if (it.done) it else it.copy(weightKg = 22.5) }))
        // Nothing left to log.
        assertFalse(suggestion.stillOffered(sets.map { it.copy(done = true) }))
    }

    @Test
    fun `a hold is read in seconds and a drop by its reps alone`() {
        val hold = ProgressionSuggestion(Kind.LONGER_HOLD, null, 35, hold = true, reason = "")
        assertTrue(hold.stillOffered(listOf(SessionSet(1, 1, setIndex = 0, reps = 0, durationSec = 30))))
        assertFalse(hold.stillOffered(listOf(SessionSet(1, 1, setIndex = 0, reps = 0, durationSec = 35))))
        val more = ProgressionSuggestion(Kind.MORE_LOAD, 22.5, 8, hold = false, reason = "")
        assertFalse(more.stillOffered(listOf(SessionSet(1, 1, setIndex = 0, reps = 8, weightKg = 18.0, kind = SetKind.DROP))))
    }
}
