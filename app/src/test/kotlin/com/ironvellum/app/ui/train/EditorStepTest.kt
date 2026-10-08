package com.ironvellum.app.ui.train

import com.ironvellum.app.domain.ExerciseMetric
import com.ironvellum.app.ui.components.stepDecimal
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
    fun foldedSummaryFollowsTheMetric() {
        val entry = EditorEntry(1, "Bench press", "3", "8", "60", "")
        assertEquals("3 × 8 · 60 kg", entrySummary(entry, ExerciseMetric.REPS))
        assertEquals("3 × 8 min", entrySummary(entry, ExerciseMetric.DURATION))
        assertEquals("3 × 60 km", entrySummary(entry, ExerciseMetric.DISTANCE_TIME))
        assertEquals("3 × –", entrySummary(entry.copy(reps = "", weight = ""), ExerciseMetric.REPS))
    }
}
