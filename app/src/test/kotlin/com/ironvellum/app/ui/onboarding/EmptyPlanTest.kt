package com.ironvellum.app.ui.onboarding

import com.ironvellum.app.domain.PlannedEntry
import com.ironvellum.app.domain.PlannedPreset
import com.ironvellum.app.domain.RoutinePlan
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmptyPlanTest {
    private val entry = PlannedEntry("Push-up", 3, 10, null)

    @Test
    fun `a plan with no presets cannot be taken`() {
        assertFalse(RoutinePlan(emptyList()).isTakeable())
    }

    @Test
    fun `presets without exercises cannot be taken`() {
        assertFalse(RoutinePlan(listOf(PlannedPreset("Day A", "", 1, emptyList()))).isTakeable())
    }

    @Test
    fun `a plan with one exercise can be taken`() {
        assertTrue(RoutinePlan(listOf(PlannedPreset("Day A", "", 1, listOf(entry)))).isTakeable())
    }
}