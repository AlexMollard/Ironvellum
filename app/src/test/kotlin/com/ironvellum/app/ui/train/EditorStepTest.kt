package com.ironvellum.app.ui.train

import com.ironvellum.app.domain.ExerciseMetric
import com.ironvellum.app.domain.LoadKit
import com.ironvellum.app.domain.LoadUnit
import com.ironvellum.app.ui.components.stepDecimal
import com.ironvellum.app.ui.components.stepOnGrid
import com.ironvellum.app.ui.components.stepWhole
import org.junit.Assert.assertEquals
import org.junit.Test

class EditorStepTest {
    @Test
    fun countsStayWithinOneAndTheCap() {
        assertEquals("1", stepWhole("", 1, 30))
        assertEquals("1", stepWhole("1", -1, 30))
        assertEquals("30", stepWhole("30", 1, 30))
        assertEquals("9", stepWhole("8", 1, 30))
    }

    @Test
    fun loadStepsInHalfKilosAndClearsAtZero() {
        assertEquals("2.5", stepDecimal("", 1, 2.5))
        assertEquals("62.5", stepDecimal("60", 1, 2.5))
        assertEquals("", stepDecimal("2.5", -1, 2.5))
        assertEquals("500", stepDecimal("500", 1, 2.5))
    }

    @Test
    fun anOffGridLoadStepsOntoTheGridInsteadOfOverRoundValues() {
        // A lateral raise at 11.5 kg on the 2.5 kg grid: 10, then 7.5 going down; 9 climbs to 10, never over it.
        assertEquals("10", stepOnGrid("11.5", -1, 2.5))
        assertEquals("7.5", stepOnGrid("10", -1, 2.5))
        assertEquals("10", stepOnGrid("9", 1, 2.5))
        assertEquals("12.5", stepOnGrid("11.5", 1, 2.5))
        // On the grid it is one step, as it always was; zero clears; the ceiling holds.
        assertEquals("62.5", stepOnGrid("60", 1, 2.5))
        assertEquals("57.5", stepOnGrid("60", -1, 2.5))
        assertEquals("2.5", stepOnGrid("", 1, 2.5))
        assertEquals("", stepOnGrid("2.5", -1, 2.5))
        assertEquals("", stepOnGrid("1", -1, 2.5))
        assertEquals("500", stepOnGrid("500", 1, 2.5))
        // The lb stepper walks its grid on the displayed pounds.
        assertEquals("25", stepOnGrid("27.5", -1, 5.0, max = 1000.0))
        assertEquals("30", stepOnGrid("27.5", 1, 5.0, max = 1000.0))
    }

    @Test
    fun aWornLoadStepsOntoTheKitsBlocksFromAnOffGridFigure() {
        val vest = LoadKit(vestMaxKg = 20.0, vestBlockKg = 1.0)
        assertEquals(12.0, vest.nextKg(11.5, LoadUnit.KG)!!, 1e-9)
        assertEquals(11.0, vest.previousKg(11.5, LoadUnit.KG)!!, 1e-9)
        assertEquals(10.0, vest.previousKg(11.0, LoadUnit.KG)!!, 1e-9)
    }

    @Test
    fun foldedSummaryFollowsTheMetric() {
        val entry = EditorEntry(1, "Bench press", "3", "8", "60", "")
        assertEquals("3 × 8 · 60 kg", entrySummary(entry, ExerciseMetric.REPS))
        assertEquals("3 × 8 min", entrySummary(entry, ExerciseMetric.DURATION))
        assertEquals("3 × 60 km", entrySummary(entry, ExerciseMetric.DISTANCE_TIME))
        assertEquals("3 × –", entrySummary(entry.copy(reps = "", weight = ""), ExerciseMetric.REPS))
    }
}
