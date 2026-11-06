package com.ironvellum.app.ui.preparation

import com.ironvellum.app.domain.*

/** Approximate display metadata, excluding readiness. Unknown/manual cadence has no estimate. */
internal fun preparationEstimatedMinutes(snapshot: PreparationSnapshot): Int? {
    if (snapshot.steps.isEmpty() || snapshot.steps.any { step ->
            step.measure != PreparationMeasure.SECONDS ||
                step.legacyTarget?.metric?.let { it != ExerciseMetric.DURATION && it != ExerciseMetric.HOLD } == true
        }) return null
    val seconds = snapshot.steps.sumOf { it.target.toLong() * it.rounds * it.repetitions }
    return ((seconds + 59L) / 60L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}
