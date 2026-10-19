package com.ironvellum.app.ui.components

import com.ironvellum.app.data.Units
import com.ironvellum.app.domain.Exercise
import com.ironvellum.app.domain.ExerciseMetric
import com.ironvellum.app.domain.LastLogged
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import com.ironvellum.app.R
import com.ironvellum.app.data.Words
import com.ironvellum.app.domain.UiText

/** Workouts older than this many days read as a date, not "Nd ago". */
private const val RELATIVE_DAYS = 14

/**
 * "5×+20 kg · 3d ago", "45s · yesterday", "8×7.5 kg · 12 Sep".
 *
 * Load on a bodyweight movement (or a "Weighted ..." one) is ADDED load, so
 * it prints with a plus; a barbell or dumbbell row prints the load itself.
 * Figures come from [setFigure] where it already speaks the metric.
 */
internal fun lastLoggedLine(
    last: LastLogged,
    exercise: Exercise,
    nowMs: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): String = "${lastLoggedFigure(last, exercise)} · ${lastLoggedAge(last.atMs, nowMs, zone)}"

internal fun lastLoggedFigure(last: LastLogged, exercise: Exercise): String {
    val added = !exercise.isWeighted || exercise.name.trim().lowercase().startsWith("weighted")
    val load = last.weightKg?.takeIf { it > 0.0 }?.let { (if (added) "+" else "") + Units.withUnit(it) }
    return when (exercise.metric) {
        ExerciseMetric.REPS -> "${last.reps} × ${load ?: Units.label(null, exercise.isWeighted && !added)}"
        ExerciseMetric.HOLD -> {
            val seconds = setFigure(exercise.metric, last.reps, last.durationSec, last.distanceM).figure
            if (load == null) lastLoggedSeconds(seconds) else "${lastLoggedSeconds(seconds)} × $load"
        }
        ExerciseMetric.DURATION -> Words.text(UiText.res(R.string.lastlogged_minutes, setFigure(exercise.metric, last.reps, last.durationSec, last.distanceM).figure))
        ExerciseMetric.DISTANCE_TIME -> setFigure(exercise.metric, last.reps, last.durationSec, last.distanceM).figure
        ExerciseMetric.ATTEMPTS_GRADE -> buildString {
            append(Words.text(UiText.plural(R.plurals.session_figure_attempts, last.reps)))
            last.grade?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
        }
    }
}

private fun lastLoggedSeconds(seconds: String): String = Words.text(UiText.res(R.string.lastlogged_seconds, seconds))

internal fun lastLoggedAge(atMs: Long, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val then = Instant.ofEpochMilli(atMs).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    val days = ChronoUnit.DAYS.between(then, today)
    return when {
        days <= 0 -> Words.text(UiText.res(R.string.lastlogged_today))
        days == 1L -> Words.text(UiText.res(R.string.lastlogged_yesterday))
        days < RELATIVE_DAYS -> Words.text(UiText.res(R.string.lastlogged_days_ago, days))
        else -> formatDate(atMs, "d MMM")
    }
}
