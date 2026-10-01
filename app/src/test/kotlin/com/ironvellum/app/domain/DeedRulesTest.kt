package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeedRulesTest {

    private fun rank(g: String) = GradeRank.rank(g)

    @Test
    fun `font grades convert through the chart not a formula`() {
        val chart = mapOf(
            "5A" to "V0", "5B" to "V1", "5C" to "V2",
            "6A" to "V3", "6A+" to "V3", "6B" to "V4", "6C" to "V5",
            "7A" to "V6", "7A+" to "V7", "7B" to "V8", "7B+" to "V8", "7C" to "V9", "7C+" to "V10",
            "8A" to "V11", "8A+" to "V12", "8B" to "V13", "8C" to "V15",
        )
        chart.forEach { (font, v) -> assertEquals(font, rank(v), rank(font)) }
    }

    @Test
    fun `a font 7B send reaches the V8 deed and 8A reaches V11`() {
        assertTrue(rank("7B")!! >= rank("V8")!!)
        assertTrue(rank("8A")!! >= rank("V11")!!)
        assertTrue(rank("7A+")!! < rank("V8")!!)
    }

    @Test
    fun `grandmaster counts the whole catalogue`() {
        val grandmaster = Titles.ALL.single { it.id == "grandmaster_of_all" }
        assertEquals(TitleRule.SkillsMastered(Skills.ALL.size), grandmaster.rule)
        assertTrue(grandmaster.description.contains("${Skills.ALL.size}"))
        val top = Titles.ALL.mapNotNull { (it.rule as? TitleRule.SkillsMastered)?.count }.max()
        assertEquals(Skills.ALL.size, top)
    }

    private val exercises = mapOf(
        1L to Exercise(name = "Running", muscleGroup = MuscleGroup.LEGS, isWeighted = false, metric = ExerciseMetric.DISTANCE_TIME, category = "Cardio"),
        2L to Exercise(name = "Cycling", muscleGroup = MuscleGroup.LEGS, isWeighted = false, metric = ExerciseMetric.DISTANCE_TIME, category = "Cardio"),
        3L to Exercise(name = "Rowing", muscleGroup = MuscleGroup.PULL, isWeighted = false, metric = ExerciseMetric.DISTANCE_TIME, category = "Cardio"),
    )

    private fun ledgerOf(vararg sets: SessionSet) = Titles.ledgerOf(
        totalXp = 0,
        history = listOf(WorkoutSession(id = 1L, label = "t", startedAtMs = 1L) to sets.toList()),
        healthDays = emptyList(),
        practices = emptyList(),
        exercises = exercises,
    )

    private fun set(exerciseId: Long, km: Double) =
        SessionSet(exerciseId = exerciseId, setIndex = 0, reps = 1, distanceM = km * 1000.0, done = true)

    @Test
    fun `a long bike ride is not a run`() {
        val built = ledgerOf(set(2L, 100.0))
        assertEquals(0.0, built.bestRunKm, 0.0001)
        assertFalse(Titles.satisfied(TitleRule.LongestRun(42.2), built))
    }

    @Test
    fun `rowing beside a short run does not add to the run`() {
        val built = ledgerOf(set(1L, 5.0), set(3L, 5.0))
        assertEquals(5.0, built.bestRunKm, 0.0001)
        assertFalse(Titles.satisfied(TitleRule.LongestRun(10.0), built))
    }

    @Test
    fun `running names are matched after normalising`() {
        assertTrue(Titles.isRunName(" Trail Running "))
        assertTrue(Titles.isRunName("Treadmill"))
        assertFalse(Titles.isRunName("Walking"))
        assertFalse(Titles.isRunName(null))
    }
}
