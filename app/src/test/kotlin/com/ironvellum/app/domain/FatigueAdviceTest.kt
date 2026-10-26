package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** When fatigue speaks up on Today: the High trigger, the half rule, the setting, and the routine offered instead. */
class FatigueAdviceTest {

    private val now = 1_000_000_000_000L

    /** A report with each muscle at the residual given, all from a session at "now" (so it has not decayed). */
    private fun report(vararg residuals: Pair<Muscle, Double>): FatigueReport =
        FatigueReport(
            nowMs = now,
            newLifter = false,
            contributions = residuals.associate { (muscle, residual) ->
                muscle to listOf(
                    FatigueContribution(muscle, 1, now, "Test lift", "", "test lift", residual, 0.0, false, false, 24.0),
                )
            },
        )

    private var nextId = 1L

    private fun entry(name: String, position: Int, modifiers: String = "", warmup: Boolean = false) =
        PresetEntry(id = nextId++, exerciseId = nextId, exerciseName = name, targetSets = 3, targetReps = 8, modifiers = modifiers, position = position, warmup = warmup)

    private val bench = entry("Bench Press", 0)

    private val benchMuscles = FatigueAdvice.mainMuscles("Bench Press").sortedBy { it.ordinal }

    // ----------------------------------------------------------------------------------- the trigger

    @Test
    fun `high triggers, moderate never does`() {
        // 60% of the 6-set reference is exactly High; a hair under is Moderate.
        val atHigh = report(benchMuscles.first() to 3.6)
        val justUnder = report(benchMuscles.first() to 3.59)
        assertTrue(FatigueAdvice.triggers(atHigh.levelAt(benchMuscles.first(), now)))
        assertEquals(FatigueLevel.MODERATE, justUnder.levelAt(benchMuscles.first(), now))
        assertFalse(FatigueAdvice.triggers(FatigueLevel.MODERATE))
        assertTrue(FatigueAdvice.triggers(FatigueLevel.VERY_HIGH))

        assertTrue(FatigueAdvice.leaning(listOf(bench), atHigh, now).isNotEmpty())
        assertTrue(FatigueAdvice.leaning(listOf(bench), justUnder, now).isEmpty())
        assertEquals(FatigueAdvice.Today.None, FatigueAdvice.today(listOf(bench), justUnder, now, restOffered = true, enabled = true))
    }

    @Test
    fun `main muscles are the ones with a share of half or more, modifiers applied`() {
        val flat = FatigueAdvice.mainMuscles("Bench Press")
        assertTrue(Muscle.MID_CHEST in flat)
        assertTrue(Muscle.SIDE_DELTS !in flat)
        // A decline moves the chest credit to the lower chest; a minor synergist never counts.
        assertTrue(Muscle.LOWER_CHEST in FatigueAdvice.mainMuscles("Bench Press", "decline"))
        assertTrue(FatigueAdvice.mainMuscles("An exercise nobody profiled").isEmpty())
    }

    @Test
    fun `a leaning exercise names its worst main muscle and a warm-up never leans`() {
        val tired = report(Muscle.MID_CHEST to 4.0, Muscle.FRONT_DELTS to 5.0)
        val leans = FatigueAdvice.leaning(listOf(bench, entry("Bench Press", 1, warmup = true)), tired, now)
        assertEquals(1, leans.size)
        assertEquals(Muscle.FRONT_DELTS, leans.single().muscle)
        assertEquals(FatigueLevel.HIGH, leans.single().level)
    }

    // -------------------------------------------------------------------------------- the half rule

    /** The routine of the bench and overhead press, and the report with exactly [high] of its main muscles at High. */
    private fun halfCase(high: Int): Pair<List<PresetEntry>, FatigueReport> {
        val entries = listOf(entry("Bench Press", 0), entry("Overhead Press", 1))
        val main = entries.flatMap { FatigueAdvice.mainMuscles(it.exerciseName) }.toSet().sortedBy { it.ordinal }
        assertTrue("the case needs a few muscles to split", main.size >= 4)
        return entries to report(*main.mapIndexed { i, m -> m to if (i < high) 3.6 else 1.0 }.toTypedArray())
    }

    @Test
    fun `exactly half of the main muscles at High shows the rest panel, one fewer shows the adjust line`() {
        val (entries, _) = halfCase(0)
        val total = entries.flatMap { FatigueAdvice.mainMuscles(it.exerciseName) }.toSet().size
        val half = (total + 1) / 2
        val (_, atHalf) = halfCase(half)
        val (_, below) = halfCase(half - 1)

        assertTrue(FatigueAdvice.today(entries, atHalf, now, restOffered = true, enabled = true) is FatigueAdvice.Today.Rest)
        assertTrue(FatigueAdvice.today(entries, below, now, restOffered = true, enabled = true) is FatigueAdvice.Today.Adjust)
    }

    @Test
    fun `a routine with four main muscles and two of them High is exactly half`() {
        // Two lifts with no main muscle in common that add up to four.
        val names = MuscleMap.keys.sorted()
        val pair = names.flatMap { a -> names.map { b -> a to b } }.first { (a, b) ->
            val ma = FatigueAdvice.mainMuscles(a)
            val mb = FatigueAdvice.mainMuscles(b)
            ma.isNotEmpty() && mb.isNotEmpty() && ma.intersect(mb).isEmpty() && (ma + mb).size == 4
        }
        val routine = listOf(entry(pair.first, 0), entry(pair.second, 1))
        val four = (FatigueAdvice.mainMuscles(pair.first) + FatigueAdvice.mainMuscles(pair.second)).sortedBy { it.ordinal }
        assertEquals(4, four.size)

        val twoHigh = report(four[0] to 3.6, four[1] to 3.6, four[2] to 1.0, four[3] to 1.0)
        val oneHigh = report(four[0] to 3.6, four[1] to 1.0, four[2] to 1.0, four[3] to 1.0)
        assertTrue(FatigueAdvice.today(routine, twoHigh, now, restOffered = true, enabled = true) is FatigueAdvice.Today.Rest)
        assertTrue(FatigueAdvice.today(routine, oneHigh, now, restOffered = true, enabled = true) is FatigueAdvice.Today.Adjust)
    }

    @Test
    fun `the rest panel is only offered where asked, and lists the worst muscles first, at most three`() {
        val (entries, atHalf) = halfCase(5)
        assertTrue(FatigueAdvice.today(entries, atHalf, now, restOffered = false, enabled = true) is FatigueAdvice.Today.Adjust)
        val rest = FatigueAdvice.today(entries, atHalf, now, restOffered = true, enabled = true) as FatigueAdvice.Today.Rest
        assertEquals(3, rest.muscles.size)
        assertTrue(rest.leans.isNotEmpty())
    }

    @Test
    fun `the adjust line counts the exercises that lean and names the worst muscle`() {
        val entries = listOf(bench, entry("Overhead Press", 1), entry("Back Squat", 2))
        val tired = report(Muscle.MID_CHEST to 4.0, Muscle.FRONT_DELTS to 4.5)
        val advice = FatigueAdvice.today(entries, tired, now, restOffered = false, enabled = true) as FatigueAdvice.Today.Adjust
        assertEquals(2, advice.leans.size)
        assertEquals(Muscle.FRONT_DELTS, advice.worst)
    }

    // ------------------------------------------------------------------------------------ the setting

    @Test
    fun `with the setting off nothing is said, however tired`() {
        val (entries, tired) = halfCase(8)
        assertEquals(FatigueAdvice.Today.None, FatigueAdvice.today(entries, tired, now, restOffered = true, enabled = false))
        assertEquals(FatigueAdvice.Today.None, FatigueAdvice.today(entries, tired, now, restOffered = false, enabled = false))
    }

    // ------------------------------------------------------------------------------ another routine

    private fun rite(id: Long, name: String, day: Int?, vararg lifts: String) =
        WorkoutPreset(id = id, name = name, scheduledDay = day, entries = lifts.mapIndexed { i, l -> entry(l, i) })

    @Test
    fun `the offered routine is one not done this week whose muscles are all Moderate or below, the freshest first`() {
        val tired = report(Muscle.QUADS to 4.0, Muscle.MID_CHEST to 3.0, Muscle.LATS to 0.4)
        val legs = rite(1, "Legs", 1, "Back Squat")
        val push = rite(2, "Push", 3, "Bench Press")
        val pull = rite(3, "Pull", 5, "Pull-up")
        val all = listOf(legs, push, pull)

        // Chest is Moderate (3.0 is under the 3.6 High line) and lats are barely worked: the lats win.
        assertEquals(pull, FatigueAdvice.freshestOther(all, legs.id, emptySet(), tired, now))
        // Pull was done this week: Push is next best.
        assertEquals(push, FatigueAdvice.freshestOther(all, legs.id, setOf(pull.id), tired, now))
        // Both done: nothing is offered, and the current routine is never its own alternative.
        assertNull(FatigueAdvice.freshestOther(all, legs.id, setOf(pull.id, push.id), tired, now))
    }

    @Test
    fun `a routine with a High main muscle is never offered, and one with no known muscle neither`() {
        val tired = report(Muscle.MID_CHEST to 3.6)
        val push = rite(2, "Push", 3, "Bench Press")
        val mystery = rite(4, "Mystery", 4, "An exercise nobody profiled")
        assertNull(FatigueAdvice.freshestOther(listOf(push, mystery), 1, emptySet(), tired, now))
    }
}
