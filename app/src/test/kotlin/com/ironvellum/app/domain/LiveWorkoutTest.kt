package com.ironvellum.app.domain

import com.ironvellum.app.plainText
import com.ironvellum.app.themedText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The live workout's words, segments and tile: what the Live Update, the fallback notification and the tile all paint. */
class LiveWorkoutTest {

    private fun set(id: Long, exercise: Long, name: String, index: Int, done: Boolean = false, kg: Double? = 47.5, reps: Int = 6, kind: SetKind = SetKind.WORKING, hold: Int? = null) =
        SessionSet(id = id, exerciseId = exercise, exerciseName = name, exercisePosition = exercise.toInt(), setIndex = index, reps = reps, weightKg = kg, done = done, kind = kind, durationSec = hold)

    /** Press: 3 sets (2 done), Incline: 3 sets of 10, a warm-up that never counts. */
    private val push = listOf(
        set(1, 1, "Overhead Press", 0, kind = SetKind.WARMUP, kg = 20.0),
        set(2, 1, "Overhead Press", 1, done = true),
        set(3, 1, "Overhead Press", 2, done = true),
        set(4, 1, "Overhead Press", 3),
        set(5, 2, "Incline Press", 0, kg = 30.0, reps = 10),
        set(6, 2, "Incline Press", 1, kg = 30.0, reps = 10),
        set(7, 2, "Incline Press", 2, kg = 30.0, reps = 10),
    )

    private fun content(sets: List<SessionSet> = push, routine: String = "Push A", resting: Boolean = false) =
        LiveWorkout.content(sets, routine, resting) { "${if (it % 1.0 == 0.0) it.toInt() else it} kg" }

    @Test
    fun theSetInHandNamesItsNumberAndFigure() {
        val c = content()
        assertEquals("Set 3 of 3 · 6 reps · 47.5 kg", c.body.plainText())
        assertEquals("Overhead Press", c.title.plainText())
        assertEquals(4L, c.nextSetId)
    }

    @Test
    fun theChipShowsSetProgressBetweenSetsAndNothingWhileResting() {
        assertEquals("Set 3 of 3", content().chip!!.plainText())
        assertNull("the chip counts the rest down by itself", content(resting = true).chip)
    }

    @Test
    fun restingNamesTheSetAfterItInShortForm() {
        val c = content(resting = true)
        assertTrue(c.resting)
        assertEquals("Rest · then set 3 of 3 · 6 × 47.5 kg", c.body.plainText())
    }

    @Test
    fun oneSegmentPerExerciseAndWarmupsDoNotCount() {
        val c = content()
        assertEquals(listOf(3, 3), c.segments)
        assertEquals(2, c.done)
        assertEquals(6, c.total)
    }

    @Test
    fun subTextCarriesTheRoutineNameOrFallsBackToTheConceptWord() {
        assertEquals("Push A · 2 / 6 sets", content().subText.plainText())
        assertEquals("WORKOUT · 2 / 6 sets", content(routine = "  ").subText.plainText())
        assertEquals("TRIAL · 2 / 6 sets", content(routine = "").subText.themedText())
    }

    @Test
    fun expandedAddsTheLastSetAndTheNextExerciseBeforeAndroid16() {
        val text = content(resting = true).expanded!!.plainText()
        assertEquals(
            "Rest · then set 3 of 3 · 6 × 47.5 kg\n\nLast set: 6 × 47.5 kg · set 2 of 3\nNext up: Incline Press, 3 × 10",
            text,
        )
    }

    @Test
    fun noLastSetBeforeTheFirstAndNoNextUpOnTheLastExercise() {
        val fresh = push.map { it.copy(done = false) }
        assertEquals("Next up: Incline Press, 3 × 10", content(fresh).expanded!!.plainText().substringAfter("\n\n"))
        val lastExercise = push.filter { it.exerciseId == 2L }
        val only = content(lastExercise).expanded
        assertNull("nothing before and nothing after: the body says it all", only)
    }

    @Test
    fun everySetDoneShowsDoneOnTheChip() {
        val all = push.map { if (it.warmup) it else it.copy(done = true) }
        val c = content(all)
        assertTrue(c.allDone)
        assertEquals("Done", c.chip!!.plainText())
        assertNull(c.nextSetId)
        assertEquals("Every set is done. Finish the workout.", c.body.plainText())
        assertEquals("Every set is done. Seal the Trial.", c.body.themedText())
    }

    @Test
    fun aRestIsIgnoredOnceNothingIsLeftToDo() {
        val all = push.map { if (it.warmup) it else it.copy(done = true) }
        assertFalse(content(all, resting = true).resting)
    }

    @Test
    fun theTrialOpeningHasNoSegmentsAndNoChip() {
        val c = content(emptyList())
        assertTrue(c.segments.isEmpty())
        assertNull(c.chip)
        assertEquals("Opening the workout…", c.body.plainText())
        assertEquals("Workout", c.title.plainText())
        assertEquals("Trial", c.title.themedText())
    }

    @Test
    fun heldAndBodyweightSetsReadWithoutALoad() {
        val plank = listOf(set(1, 1, "Plank", 0, kg = null, reps = 0, hold = 45), set(2, 1, "Plank", 1, kg = null, reps = 0, hold = 45))
        assertEquals("Set 1 of 2 · 45s", content(plank).body.plainText())
        val pushups = listOf(set(1, 1, "Push-up", 0, kg = null, reps = 1))
        assertEquals("Set 1 of 1 · 1 rep", content(pushups).body.plainText())
    }

    @Test
    fun theTileIsIdleUntilAWorkoutRuns() {
        val idle = LiveWorkout.tile("Pull A", null, null)
        assertFalse(idle.active)
        assertEquals("Workout", idle.label.plainText())
        assertEquals("Trial", idle.label.themedText())
        assertEquals("Pull A today", idle.subtitle.plainText())
        assertEquals("Open Today", LiveWorkout.tile(null, null, null).subtitle.plainText())
        assertEquals("Open Today", LiveWorkout.tile(" ", null, null).subtitle.plainText())
    }

    @Test
    fun theTileShowsTheSetAndCountsTheRestDown() {
        val live = LiveWorkout.tile(null, push, null)
        assertTrue(live.active)
        assertEquals("Set 3 of 3 · 2/6", live.subtitle.plainText())
        val resting = LiveWorkout.tile(null, push, "1:24")
        assertEquals("Rest 1:24", resting.label.plainText())
        assertEquals("Then set 3 of 3", resting.subtitle.plainText())
        assertTrue(resting.active)
    }

    @Test
    fun theTileSaysWhenEverySetIsDoneAndIgnoresALeftoverRest() {
        val all = push.map { if (it.warmup) it else it.copy(done = true) }
        val tile = LiveWorkout.tile(null, all, "0:10")
        assertEquals("Workout", tile.label.plainText())
        assertEquals("Every set is done", tile.subtitle.plainText())
    }
}
