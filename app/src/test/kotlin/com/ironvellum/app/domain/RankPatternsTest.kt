package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Every lift and skill has a pattern or a stated reason not to count. */
class RankPatternsTest {

    @Test
    fun `every skill is counted or deliberately left out`() {
        val unplaced = Skills.ALL.filter { s ->
            RankPatterns.forSkill(s) == null &&
                s.name !in RankPatterns.EXCLUDED_SKILLS &&
                s.line !in RankPatterns.EXCLUDED_LINES &&
                s.metric != Skills.Metric.METRES
        }
        assertEquals(emptyList<String>(), unplaced.map { "${it.line}/${it.name}" })
    }

    @Test
    fun `no counted skill is a loaded standard`() {
        val loaded = RankPatterns.COUNTED.map { it.first }.filter {
            it.standard.contains("bodyweight", ignoreCase = true) || Regex("\\d\\s*kg", RegexOption.IGNORE_CASE).containsMatchIn(it.standard)
        }
        assertEquals(emptyList<String>(), loaded.map { it.name })
    }

    @Test
    fun `every tiered lift has a pattern`() {
        val missing = Lift.entries.filter { it.kind == LiftKind.TIERED && RankPatterns.forLift(it) == null }
        assertEquals(emptyList<Lift>(), missing)
    }

    @Test
    fun `a pattern lists its tiered lifts in lift order`() {
        assertEquals(listOf(Lift.SQUAT, Lift.DEADLIFT), RankPatterns.lifts(Pattern.LEGS))
        assertEquals(listOf(Lift.DIP, Lift.BENCH, Lift.OVERHEAD_PRESS), RankPatterns.lifts(Pattern.PUSH))
        assertEquals(listOf(Lift.PULL_UP), RankPatterns.lifts(Pattern.PULL))
    }

    @Test
    fun `patterns read as a coach would`() {
        fun p(name: String) = RankPatterns.forSkill(Skills.forName(name)!!)
        assertEquals(Pattern.PUSH, p("Handstand Push-up"))
        assertEquals(Pattern.PUSH, p("Tuck Planche"))
        assertEquals(Pattern.PUSH, p("Ring Dip"))
        assertEquals(Pattern.PULL, p("Front Lever"))
        assertEquals(Pattern.PULL, p("Muscle-up"))
        assertEquals(Pattern.LEGS, p("Pistol Squat"))
        assertNull(p("Hollow Hold"))
        assertNull(p("Weighted Pull-up"))
        assertNull(p("Handstand Walk"))
        assertEquals(Pattern.PULL, RankPatterns.forLift(Lift.PULL_UP))
        assertEquals(Pattern.PUSH, RankPatterns.forLift(Lift.OVERHEAD_PRESS))
        assertEquals(Pattern.LEGS, RankPatterns.forLift(Lift.DEADLIFT))
        assertNull(RankPatterns.forLift(Lift.PLANCHE))
    }
}
