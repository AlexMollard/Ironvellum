package com.ironvellum.app.ui.train

import com.ironvellum.app.domain.ExerciseMetric
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorTargetsTest {
    private fun entry(sets: String = "3", reps: String = "10", weight: String = "") =
        EditorEntry(1, "Move", sets, reps, weight, "")

    @Test
    fun `a duration exercise with blank reps is missing its target instead of defaulting to 10`() {
        assertEquals(setOf(TargetField.REPS), missingTargets(entry(reps = ""), ExerciseMetric.DURATION))
    }

    @Test
    fun `zero sets and blank sets are both missing`() {
        assertEquals(setOf(TargetField.SETS), missingTargets(entry(sets = "0"), ExerciseMetric.REPS))
        assertEquals(setOf(TargetField.SETS), missingTargets(entry(sets = ""), ExerciseMetric.REPS))
    }

    @Test
    fun `distance work needs no reps and kg above the session ceiling is refused`() {
        assertTrue(missingTargets(entry(reps = "", weight = "5"), ExerciseMetric.DISTANCE_TIME).isEmpty())
        assertEquals(setOf(TargetField.WEIGHT), missingTargets(entry(weight = "500.5"), ExerciseMetric.REPS))
        assertTrue(missingTargets(entry(weight = "500"), ExerciseMetric.REPS).isEmpty())
    }
}