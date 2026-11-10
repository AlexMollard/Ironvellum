package com.ironvellum.app.ui.train

import com.ironvellum.app.domain.LoadUnit
import com.ironvellum.app.domain.Loads
import com.ironvellum.app.domain.ExerciseMetric
import com.ironvellum.app.domain.SessionSet
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

/** Every place that prints a load agrees: a set with no added weight is "BW", a real load has a space before its unit. */
class LoadFormatTest {

    @Test
    fun `no load reads BW wherever a figure is printed`() {
        assertEquals("BW", formatKg(null))
        assertEquals("BW", formatKg(0.0))
        assertEquals("BW × 5", setText(5, 0.0, hold = false, assisted = false))
        assertEquals("BW × 5", setText(5, null, hold = false, assisted = false))
        assertEquals("BW × 5", compactSet(5, 0.0, hold = false, assisted = false))
    }

    @Test
    fun `a load has a space before its unit`() {
        assertEquals("60 kg", formatKg(60.0))
        assertEquals("82.5 kg", formatKg(82.5))
        assertEquals("60 kg", Loads.label(60.0, LoadUnit.KG))
        assertEquals("132.5 lb", Loads.label(60.0, LoadUnit.LB))
    }

    @Test
    fun `folded assisted best prefers less support while ordinary lifts prefer more load`() {
        fun folded(name: String, weights: List<Double?>): String {
            val sets = weights.mapIndexed { index, kg ->
                SessionSet(exerciseId = 1, exerciseName = name, setIndex = index, reps = 8, weightKg = kg, done = true)
            }
            return foldedSubline(TrialBlock(1, 0, sets), ExerciseMetric.REPS, weighted = true)
        }
        val assisted = folded("Assisted Pull-up", listOf(70.0, 60.0))
        assertTrue(assisted.contains("60 kg"))
        assertFalse(assisted.contains("70 kg"))
        assertTrue(folded("Assisted Pull-up", listOf(null, 60.0)).contains("60 kg"))
        assertTrue(folded("Bench Press", listOf(60.0, 70.0)).contains("70 kg"))
    }

    @Test
    fun `assisted live and folded figures distinguish unknown from zero`() {
        val unknown = SessionSet(exerciseId = 1, exerciseName = "Assisted Pull-up", setIndex = 0, reps = 8, weightKg = null)
        val zero = unknown.copy(weightKg = 0.0)
        assertTrue(setFigureText(unknown, ExerciseMetric.REPS, true).contains("—"))
        assertTrue(setFigureText(zero, ExerciseMetric.REPS, true).contains("0 kg"))
        assertTrue(foldedSubline(TrialBlock(1, 0, listOf(unknown)), ExerciseMetric.REPS, true).contains("—"))
        assertTrue(foldedSubline(TrialBlock(1, 0, listOf(zero)), ExerciseMetric.REPS, true).contains("0 kg"))
    }
}
