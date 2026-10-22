package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one variation rule, and what it does to every history that reads it: records, peaks, rep maxes, the
 * lift board, the strength-record bonus, the per-exercise chart and the XP lines. Only assisted, archer,
 * one-arm, incline, decline, elevated and deficit split a lift; tempo, paused, weighted and anything unknown do not.
 */
class VariationTest {

    // ------------------------------------------------------------ the key

    @Test
    fun `no modifier is the plain movement`() {
        assertEquals("", Variation.key(""))
        assertEquals("", Variation.key("   "))
        assertTrue(Variation.isPlain(""))
    }

    @Test
    fun `only the splitting tokens count`() {
        assertEquals("deficit", Variation.key("deficit"))
        assertEquals("", Variation.key("tempo"))
        assertEquals("", Variation.key("paused"))
        assertEquals("", Variation.key("weighted"))
        assertEquals("", Variation.key("tempo, paused, weighted"))
        assertEquals("deficit", Variation.key("weighted, tempo, deficit, paused"))
    }

    @Test
    fun `an unknown token never splits a history`() {
        assertEquals("", Variation.key("banded"))
        assertEquals("", Variation.key("defecit"))
        assertEquals("assisted", Variation.key("banded, assisted, hold seconds"))
        assertTrue(Variation.isPlain("tempoo"))
    }

    @Test
    fun `order casing spacing and repeats do not change the key`() {
        val canonical = "assisted,deficit"
        assertEquals(canonical, Variation.key("deficit, assisted"))
        assertEquals(canonical, Variation.key("assisted,deficit"))
        assertEquals(canonical, Variation.key("  Assisted ,  DEFICIT  "))
        assertEquals(canonical, Variation.key("deficit, assisted, deficit, Assisted"))
        assertEquals("one-arm", Variation.key("One-Arm"))
    }

    @Test
    fun `all seven tokens split and nothing else does`() {
        listOf("assisted", "archer", "one-arm", "incline", "decline", "elevated", "deficit").forEach {
            assertFalse(it, Variation.isPlain(it))
        }
        assertEquals(7, Variation.TOKENS.size)
    }

    // ------------------------------------------------------------ the lift key

    @Test
    fun `the plain lift keeps the bare name key every lookup used before`() {
        assertEquals("pull-up", Variation.liftKey("Pull-up", ""))
        assertEquals("pull-up", Variation.liftKey("  PULL-UP ", "tempo, weighted"))
        assertEquals(RepMax.key("Pull-up"), Variation.liftKey("Pull-up", "paused"))
    }

    @Test
    fun `a variation is a different lift`() {
        assertNotEquals(Variation.liftKey("Pull-up", ""), Variation.liftKey("Pull-up", "assisted"))
        assertNotEquals(Variation.liftKey("Single-Leg Calf Raise", ""), Variation.liftKey("Single-Leg Calf Raise", "deficit"))
        assertEquals(
            Variation.liftKey("Pull-up", "assisted, weighted"),
            Variation.liftKey("pull-up", "Assisted"),
        )
    }

    @Test
    fun `a variation key cannot collide with another movement's bare name`() {
        // The separator is not a character a movement name carries.
        val keys = Seed_names.map { Variation.liftKey(it, "") }.toSet()
        assertTrue(keys.none { Variation.SEPARATOR in it })
        assertFalse(Variation.liftKey("Pull-up", "assisted") in keys)
    }

    private val Seed_names get() = com.ironvellum.app.data.Seed.exercises.map { it.name }

    // ------------------------------------------------------------ the label

    @Test
    fun `a record names its variation`() {
        assertEquals("Single-Leg Calf Raise", Variation.label("Single-Leg Calf Raise", ""))
        assertEquals("Single-Leg Calf Raise · deficit", Variation.label(" Single-Leg Calf Raise ", "weighted, deficit"))
        assertEquals("Pull-up · archer, assisted", Variation.label("Pull-up", "assisted, archer"))
    }

    // ------------------------------------------------------------ peaks

    private val pullUp = Exercise(id = 1, name = "Pull-up", muscleGroup = MuscleGroup.PULL, isWeighted = false, metric = ExerciseMetric.REPS)
    private val calf = Exercise(id = 2, name = "Single-Leg Calf Raise", muscleGroup = MuscleGroup.LEGS, isWeighted = false, metric = ExerciseMetric.REPS)
    private val catalogue = listOf(pullUp, calf).associateBy { it.id }

    private fun set(
        id: Long,
        name: String,
        reps: Int,
        kg: Double? = null,
        modifiers: String = "",
        index: Int = 0,
        position: Int = 0,
        kind: SetKind = SetKind.WORKING,
        done: Boolean = true,
    ) = SessionSet(
        id = id, exerciseId = if (name == calf.name) 2 else 1, exerciseName = name, exercisePosition = position,
        setIndex = index, reps = reps, weightKg = kg, modifiers = modifiers, done = done, kind = kind,
    )

    private fun session(id: Long, at: Long) = WorkoutSession(id = id, label = "s$id", startedAtMs = at, completedAtMs = at + 1_000)

    private fun book(vararg trials: List<SessionSet>): RepBook =
        RepMax.book(trials.mapIndexed { i, sets -> session(i + 1L, (i + 1) * 10_000L) to sets }) { catalogue[it.exerciseId]?.metric }

    private fun peaks(book: RepBook, vararg today: SessionSet) =
        SessionPeaks.of(today.toList(), book, 80.0) { catalogue[it.exerciseId] }

    @Test
    fun `an assisted pull-up never sets a pull-up peak`() {
        // The lifter's pull-ups: 5 reps. An assisted set of 12 is far more reps, but it is another lift.
        val history = book(listOf(set(1, "Pull-up", 5)))
        assertTrue(peaks(history, set(10, "Pull-up", 12, kg = 20.0, modifiers = "assisted")).isEmpty())
        // The same 12 reps plain is a peak.
        assertEquals(1, peaks(history, set(10, "Pull-up", 12)).size)
    }

    @Test
    fun `a pull-up never sets an assisted peak either`() {
        val history = book(listOf(set(1, "Pull-up", 8, modifiers = "assisted")))
        assertTrue(peaks(history, set(10, "Pull-up", 3)).isEmpty())
        assertEquals(1, peaks(history, set(10, "Pull-up", 12, modifiers = "assisted")).size)
    }

    @Test
    fun `the first assisted set is history starting, not a peak`() {
        val history = book(listOf(set(1, "Pull-up", 5)))
        assertTrue(peaks(history, set(10, "Pull-up", 20, modifiers = "assisted")).isEmpty())
    }

    @Test
    fun `a deficit calf raise keeps its own history apart from the flat one`() {
        val history = book(
            listOf(set(1, "Single-Leg Calf Raise", 12, kg = 16.0)),
            listOf(set(2, "Single-Leg Calf Raise", 10, kg = 8.0, modifiers = "weighted, deficit")),
        )
        // 9 reps at 12 kg beats the deficit best (10 at 8 kg) in load but not the flat best in reps... judged apart:
        val flat = peaks(history, set(10, "Single-Leg Calf Raise", 13, kg = 16.0))
        assertEquals(1, flat.size)
        assertEquals(12, flat.single().was.figure)
        assertEquals("", flat.single().variation)
        val deficit = peaks(history, set(11, "Single-Leg Calf Raise", 11, kg = 8.0, modifiers = "deficit"))
        assertEquals(1, deficit.size)
        assertEquals(10, deficit.single().was.figure)
        assertEquals("deficit", deficit.single().variation)
        assertEquals("Single-Leg Calf Raise · deficit", deficit.single().label)
        // A flat set that would have beaten the deficit history is judged against the flat history only.
        assertTrue(peaks(history, set(12, "Single-Leg Calf Raise", 11, kg = 16.0)).isEmpty())
    }

    @Test
    fun `tempo and paused do not split a peak`() {
        val history = book(listOf(set(1, "Pull-up", 5, kg = 10.0)))
        // Slower reps are the same lift: the old best is the benchmark.
        val slow = peaks(history, set(10, "Pull-up", 7, kg = 10.0, modifiers = "tempo, paused, weighted"))
        assertEquals(1, slow.size)
        assertEquals(5, slow.single().was.figure)
        assertEquals("", slow.single().variation)
        // And a tempo set is itself history for the plain lift.
        val tempoHistory = book(listOf(set(1, "Pull-up", 9, kg = 10.0, modifiers = "tempo")))
        assertTrue(peaks(tempoHistory, set(10, "Pull-up", 8, kg = 10.0)).isEmpty())
    }

    @Test
    fun `an unknown token does not split a peak`() {
        val history = book(listOf(set(1, "Pull-up", 9, modifiers = "banded")))
        assertTrue(peaks(history, set(10, "Pull-up", 8)).isEmpty())
    }

    @Test
    fun `one trial with a lift in two variations is judged variation by variation`() {
        val history = book(listOf(set(1, "Pull-up", 5), set(2, "Pull-up", 8, modifiers = "assisted")))
        val today = listOf(
            set(10, "Pull-up", 6, index = 0),
            set(11, "Pull-up", 9, modifiers = "assisted", index = 0),
            set(12, "Pull-up", 7, modifiers = "assisted", index = 1),
        )
        val found = peaks(history, *today.toTypedArray())
        assertEquals(setOf("", "assisted"), found.map { it.variation }.toSet())
    }

    @Test
    fun `a top set narrows its own variation only`() {
        val block = listOf(
            set(1, "Pull-up", 4, kg = 20.0, index = 0, kind = SetKind.TOP),
            set(2, "Pull-up", 9, kg = 15.0, index = 1, kind = SetKind.BACKOFF),
            set(3, "Pull-up", 9, modifiers = "assisted", index = 0),
        )
        assertEquals(listOf(1L, 3L), PeakSets.narrowed(block).map { it.id })
    }

    @Test
    fun `Today names a recent peak by lift and variation`() {
        val history = listOf(
            session(1, 10_000) to listOf(set(1, "Pull-up", 5), set(2, "Pull-up", 6, modifiers = "assisted", index = 1)),
            session(2, 20_000) to listOf(set(3, "Pull-up", 9, modifiers = "assisted")),
        )
        val recent = RepMax.recent(history, catalogue, nowMs = 30_000, withinDays = 14, limit = 5)
        assertEquals(1, recent.size)
        assertEquals("assisted", recent.single().variation)
        assertEquals("Pull-up · assisted", recent.single().label)
        assertEquals("Pull-up", recent.single().name)
    }

    // ------------------------------------------------------------ strength records

    private fun records(vararg trials: List<SessionSet>) =
        SetRecords.records(trials.mapIndexed { i, sets -> session(i + 1L, (i + 1) * 10_000L) to sets }, { 80.0 }) { catalogue[it.exerciseId]?.metric }

    @Test
    fun `records are kept per variation and set position`() {
        val found = records(
            listOf(set(1, "Pull-up", 5), set(2, "Pull-up", 12, modifiers = "assisted")),
        )
        assertEquals(setOf("pull-up" to 0, "pull-up | assisted" to 0), found.keys)
        assertEquals("assisted", found.getValue("pull-up | assisted" to 0).variation)
        assertEquals("Pull-up · assisted", found.getValue("pull-up | assisted" to 0).label)
        assertEquals(5, found.getValue("pull-up" to 0).reps)
    }

    @Test
    fun `an assisted pull-up never beats the plain pull-up record`() {
        val found = records(listOf(set(1, "Pull-up", 5, kg = 10.0), set(2, "Pull-up", 20, modifiers = "assisted", index = 1)))
        val flat = SetRecords.delta(found, "Pull-up", 0, reps = 6, weightKg = 10.0, bodyweightKg = 80.0)
        assertTrue(flat.isRecord)
        // The assisted record at position 1 is not the plain lift's.
        assertEquals(null, SetRecords.delta(found, "Pull-up", 1, reps = 3, weightKg = null, bodyweightKg = 80.0).record)
        assertEquals(
            20,
            SetRecords.delta(found, "Pull-up", 1, reps = 3, weightKg = null, bodyweightKg = 80.0, modifiers = "assisted").record!!.reps,
        )
    }

    @Test
    fun `tempo sets share the plain lift's records`() {
        val found = records(listOf(set(1, "Pull-up", 9, modifiers = "tempo")))
        assertEquals(setOf("pull-up" to 0), found.keys)
    }

    // ------------------------------------------------------------ the lift board

    @Test
    fun `the lift board has a row per variation and names it`() {
        val sessions = listOf(session(1, 10_000), session(2, 20_000))
        val sets = mapOf(
            1L to listOf(set(1, "Single-Leg Calf Raise", 10, kg = 16.0)),
            2L to listOf(set(2, "Single-Leg Calf Raise", 10, kg = 30.0, modifiers = "weighted, deficit")),
        )
        val board = LiftRecords.board(sessions, sets, catalogue, { 0.0 }, nowMs = 30_000)
        assertEquals(setOf("Single-Leg Calf Raise", "Single-Leg Calf Raise · deficit"), board.map { it.label }.toSet())
        assertEquals(16.0 * (1 + 10 / 30.0), board.single { it.variation.isEmpty() }.bestE1rmKg, 1e-9)
    }

    // ------------------------------------------------------------ the strength-record XP bonus

    private fun effort(name: String, reps: Int, kg: Double, modifiers: String = "") =
        Xp.SetEffort(exerciseName = name, reps = reps, weightKg = kg, modifiers = modifiers, metric = ExerciseMetric.REPS, exerciseId = if (name == calf.name) 2 else 1, externalLoad = true)

    @Test
    fun `a strength record is judged against its own variation`() {
        val before = Xp.History.EMPTY.after(
            listOf(effort("Calf Raise", 10, 40.0), effort("Calf Raise", 10, 20.0, "deficit")),
            bodyweightKg = 80.0,
        )
        // 30 kg x 10 beats the deficit best, not the flat best.
        val deficit = Xp.breakdown(listOf(effort("Calf Raise", 10, 30.0, "deficit")), 80.0, before, trial = Xp.TrialFacts(earnsBonuses = true))
        assertTrue(deficit.lines.any { it.kind == Xp.Kind.RECORD })
        assertTrue(deficit.lines.single { it.kind == Xp.Kind.RECORD }.detail.contains("Calf Raise · deficit"))
        val flat = Xp.breakdown(listOf(effort("Calf Raise", 10, 30.0)), 80.0, before, trial = Xp.TrialFacts(earnsBonuses = true))
        assertFalse(flat.lines.any { it.kind == Xp.Kind.RECORD })
    }

    // ------------------------------------------------------------ the per-exercise chart

    private fun row(session: Long, at: Long, reps: Int, kg: Double? = null, modifiers: String = "") =
        ExerciseSetRow(sessionId = session, atMs = at, setIndex = 0, reps = reps, weightKg = kg, modifiers = modifiers, done = true)

    @Test
    fun `the exercise history is of one variation at a time`() {
        val rows = listOf(
            row(3, 3_000, 12, modifiers = "assisted"),
            row(2, 2_000, 6),
            row(1, 1_000, 5),
        )
        val plain = ExerciseHistoryCalculator.build(pullUp, rows, bodyweightKg = 80.0)
        assertEquals(listOf("", "assisted"), plain.variations)
        assertEquals("", plain.variation)
        assertEquals(2, plain.sessions)
        assertEquals(11, plain.totalReps)
        assertEquals(6, plain.bestSetReps)
        val assisted = ExerciseHistoryCalculator.build(pullUp, rows, bodyweightKg = 80.0, variation = "assisted")
        assertEquals("assisted", assisted.variation)
        assertEquals(1, assisted.sessions)
        assertEquals(12, assisted.bestSetReps)
    }

    @Test
    fun `a lift only ever done in one variation shows that one`() {
        val history = ExerciseHistoryCalculator.build(pullUp, listOf(row(1, 1_000, 8, modifiers = "assisted")), bodyweightKg = 80.0)
        assertEquals("assisted", history.variation)
        assertEquals(1, history.completedSets)
        // A variation the rows do not hold falls back rather than showing an empty page.
        assertEquals("assisted", ExerciseHistoryCalculator.build(pullUp, listOf(row(1, 1_000, 8, modifiers = "assisted")), 80.0, variation = "deficit").variation)
    }

    // ------------------------------------------------------------ boards

    @Test
    fun `only an easier variation is off the boards`() {
        assertTrue(LiftBoards.offBoard(set(1, "Pull-up", 10, modifiers = "assisted")))
        assertTrue(LiftBoards.offBoard(set(1, "Push-up", 30, modifiers = "incline")))
        assertTrue(LiftBoards.offBoard(set(1, "Assisted Pull-up", 10)))
        listOf("deficit", "elevated", "decline", "archer", "one-arm", "weighted, deficit, elevated", "tempo, weighted").forEach {
            assertFalse(it, LiftBoards.offBoard(set(1, "Push-up", 30, modifiers = it)))
        }
        assertFalse(LiftBoards.offBoard(set(1, "Pull-up", 10)))
        assertTrue(Variation.eases("Assisted, deficit"))
        assertFalse(Variation.eases("deficit, banded"))
    }

    @Test
    fun `a harder push-up still clears its rung and an easier one does not`() {
        val target = LiftBoards.rungs(Lift.PUSH_UP).single { it.exercise == "Push-up" }.target
        fun marks(mods: String) = LiftBoards.marks(
            listOf(session(1, 10_000) to listOf(set(1, "Push-up", target, modifiers = mods))), { 80.0 }, Sex.MALE, nowMs = 20_000,
        ).filter { it.lift == Lift.PUSH_UP }
        val plain = marks("")
        assertEquals(1, plain.size)
        assertEquals(plain, marks("weighted, deficit, elevated"))
        assertEquals(plain, marks("archer"))
        assertTrue(marks("incline").isEmpty())
        assertTrue(marks("assisted").isEmpty())
    }

    @Test
    fun `a dip with a deficit still scores its tier and an assisted one does not`() {
        val plain = LiftBoards.tieredScore(set(1, "Dip", 8, kg = 20.0).copy(done = true), 80.0, Sex.MALE)!!
        assertEquals(plain, LiftBoards.tieredScore(set(1, "Dip", 8, kg = 20.0, modifiers = "weighted, deficit"), 80.0, Sex.MALE))
        assertEquals(null, LiftBoards.tieredScore(set(1, "Dip", 8, kg = 20.0, modifiers = "assisted"), 80.0, Sex.MALE))
    }

    @Test
    fun `rank from a history of harder variations is the same as without the modifiers`() {
        fun rank(mods: String) = Rank.breakdown(
            listOf(
                session(1, 10_000) to listOf(
                    set(1, "Push-up", 40, modifiers = mods),
                    set(2, "Dip", 8, kg = 20.0, modifiers = mods, index = 1),
                    set(3, "Pull-up", 8, kg = 10.0, modifiers = mods, index = 2),
                ),
            ),
            { 80.0 }, Sex.MALE, nowMs = 20_000,
        )
        val plain = rank("")
        assertEquals(plain, rank("weighted, deficit, elevated"))
        assertEquals(plain, rank("archer, decline"))
    }
}
