package com.ironvellum.app.ui.preparation

import com.ironvellum.app.domain.*
import org.junit.Assert.*
import org.junit.Test

class PreparationPresentationTest {
    private fun snapshot(vararg steps: PreparationStep) = PreparationSnapshot(PreparationKind.WARMUP, "Timed", steps.toList())
    private fun seconds(target: Int, rounds: Int = 1, repeat: PreparationRepeat = PreparationRepeat.ONCE) =
        PreparationStep(1, "Movement", PreparationMeasure.SECONDS, target, rounds, repeat)

    @Test fun estimatesEveryDirectionAndRoundsUp() {
        assertEquals(2, preparationEstimatedMinutes(snapshot(seconds(30, repeat = PreparationRepeat.DIRECTIONS), seconds(45))))
    }
    @Test fun estimatesEverySideAndRound() {
        assertEquals(2, preparationEstimatedMinutes(snapshot(seconds(30, 2, PreparationRepeat.SIDES))))
    }
    @Test fun omitsUnknownCadenceAndEmptyRoutine() {
        assertNull(preparationEstimatedMinutes(snapshot()))
        assertNull(preparationEstimatedMinutes(snapshot(seconds(30).copy(measure = PreparationMeasure.REPS))))
        assertNull(preparationEstimatedMinutes(snapshot(seconds(30).copy(legacyTarget = PreparationLegacyTarget(ExerciseMetric.DISTANCE_TIME, 5)))))
    }
    @Test fun acceptsConvertedLegacyDurationSeconds() {
        assertEquals(2, preparationEstimatedMinutes(snapshot(seconds(90).copy(legacyTarget = PreparationLegacyTarget(ExerciseMetric.DURATION, 2)))))
    }
}
