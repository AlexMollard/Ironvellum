package com.ironvellum.app.domain

import com.ironvellum.app.domain.MuscleGroup.CARDIO
import com.ironvellum.app.domain.MuscleGroup.CORE
import com.ironvellum.app.domain.MuscleGroup.LEGS
import com.ironvellum.app.domain.MuscleGroup.MOBILITY
import com.ironvellum.app.domain.MuscleGroup.PULL
import com.ironvellum.app.domain.MuscleGroup.PUSH
import org.junit.Assert.assertEquals
import org.junit.Test

/** The wording of the warm-up reminder. */
class WarmupStepTest {

    @Test
    fun `names the main groups, most numerous first`() {
        assertEquals("5 min easy cardio, then mobility for legs", WarmupStep.text(listOf(LEGS, LEGS)))
        assertEquals("5 min easy cardio, then mobility for legs and core", WarmupStep.text(listOf(CORE, LEGS, LEGS)))
        // Ties follow the catalogue's order: pull, push, legs, core.
        assertEquals("5 min easy cardio, then mobility for pull, push and legs", WarmupStep.text(listOf(LEGS, PUSH, PULL)))
    }

    @Test
    fun `names three at most and leaves cardio and mobility out`() {
        assertEquals(
            "5 min easy cardio, then mobility for pull, push and legs",
            WarmupStep.text(listOf(PULL, PUSH, LEGS, CORE, CARDIO, MOBILITY)),
        )
    }

    @Test
    fun `a trial with nothing to name still gets the step`() {
        assertEquals("5 min easy cardio, then mobility", WarmupStep.text(emptyList()))
        assertEquals("5 min easy cardio, then mobility", WarmupStep.text(listOf(CARDIO, MOBILITY)))
    }
}
