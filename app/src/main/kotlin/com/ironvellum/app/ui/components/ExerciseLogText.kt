package com.ironvellum.app.ui.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.ironvellum.app.R
import com.ironvellum.app.data.Units
import com.ironvellum.app.data.Words
import com.ironvellum.app.domain.Exercise
import com.ironvellum.app.domain.ExerciseMetric
import com.ironvellum.app.domain.ExerciseLogBlock
import com.ironvellum.app.domain.ExerciseLogSet
import com.ironvellum.app.domain.UiText

/** Whether a set's load is ADDED to the body (a pull-up's plates) rather than the whole bar. Same rule as the last-logged line. */
private fun addsToBody(exercise: Exercise): Boolean =
    !exercise.isWeighted || exercise.name.trim().lowercase().startsWith("weighted")

private fun loadOf(set: ExerciseLogSet): Double? = set.weightKg?.takeIf { it > 0.0 }

private fun loadText(exercise: Exercise, kg: Double?): String =
    if (kg == null) {
        Units.label(null, exercise.isWeighted && !addsToBody(exercise))
    } else {
        (if (addsToBody(exercise)) "+" else "") + Units.withUnit(kg)
    }

/** One set's own figure with no load: "12", or "45 s" for a hold. */
private fun figureText(exercise: Exercise, set: ExerciseLogSet): String = when (exercise.metric) {
    ExerciseMetric.REPS -> set.figure.toString()
    ExerciseMetric.HOLD -> Words.text(UiText.res(R.string.explorer_set_hold, set.figure))
    else -> setFigure(exercise.metric, set.figure, set.durationSec, set.distanceM).let { fig ->
        if (exercise.metric == ExerciseMetric.DISTANCE_TIME) fig.figure else "${fig.figure} ${fig.noun.lowercase()}"
    } + (set.grade?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "")
}

/**
 * The sets of one block on one line, runs of the same load sharing it: "18 kg × 12, 10, 8 · 20 kg × 5".
 * The block's best set is bold. A hold lists its seconds ("+20 kg × 45 s, 40 s"); an activity metric lists its figures.
 */
internal fun blockLine(exercise: Exercise, block: ExerciseLogBlock): AnnotatedString = buildAnnotatedString {
    val loaded = exercise.metric == ExerciseMetric.REPS || exercise.metric == ExerciseMetric.HOLD
    var i = 0
    while (i < block.sets.size) {
        var j = i + 1
        // A run is the sets in a row that share a load; activities carry no load worth grouping on.
        while (j < block.sets.size && (!loaded || loadOf(block.sets[j]) == loadOf(block.sets[i]))) j++
        if (i > 0) append(" · ")
        val load = loadOf(block.sets[i])
        if (loaded && (exercise.metric == ExerciseMetric.REPS || load != null)) append(loadText(exercise, load) + " × ")
        for (k in i until j) {
            if (k > i) append(", ")
            val text = figureText(exercise, block.sets[k])
            if (k == block.bestIndex && block.sets.size > 1) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text) } else append(text)
        }
        i = j
    }
}

/** The modifiers a block was done with, as the lifter picked them: "weighted · incline". Blank when it had none. */
internal fun modifierText(modifiers: String): String =
    modifiers.split(",").map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" · ")
