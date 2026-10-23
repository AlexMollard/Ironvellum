package com.ironvellum.app.ui.program

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ironvellum.app.R
import com.ironvellum.app.data.Words
import com.ironvellum.app.domain.DayNames
import com.ironvellum.app.domain.Muscle
import com.ironvellum.app.domain.ProgramRules
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.ui.components.ListRowHeight
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors
import java.util.Locale

/*
 * The week's furniture: swatches drawn in each state's own look, the legend, the row with its two-part bar and
 * the words around them. Every state has a word as well as a look, so nothing rests on colour alone.
 */

/** "16 done + 6 planned", "1 done" or "6 planned": the week's sets as the row and the lit line say them. */
internal fun weekSetsLabel(week: WeekMuscle): String {
    val done = trimSets(week.done)
    val planned = trimSets(week.planned)
    return Words.text(
        when {
            week.planned <= 0.0 -> UiText.res(R.string.coverage_week_sets_done, done)
            week.done <= 0.0 -> UiText.res(R.string.coverage_week_sets_planned, planned)
            else -> UiText.res(R.string.coverage_week_sets_both, done, planned)
        },
    )
}

/** The line under a lit muscle on the figure: "Hamstrings · 2 done + 6 planned · Planned"; the neck has no verdict. */
internal fun weekMuscleLine(muscle: Muscle, week: WeekMuscle, state: WeekState?): String {
    if (state == null) return muscleLine(muscle, hardSetsWeekLabel(week.done))
    return muscleLine(muscle, Words.text(UiText.res(R.string.muscles_joined_pair, weekSetsLabel(week), state.label)))
}

/** What a row says under the muscle's name: how far the week gets it, in words. */
internal fun weekRowNote(week: WeekMuscle, state: WeekState, locale: Locale = Locale.getDefault()): String {
    val sunday = DayNames.full(7, locale).orEmpty()
    val total = trimSets(week.total)
    return Words.text(
        when (state) {
            WeekState.HEAVY -> UiText.res(R.string.coverage_week_row_heavy, total, sunday)
            WeekState.PLANNED -> UiText.res(R.string.coverage_week_row_on_track, total, sunday)
            WeekState.DONE -> UiText.res(R.string.coverage_week_row_done, hardSetsLabel(week.done))
            WeekState.NONE -> UiText.res(R.string.coverage_week_row_none)
            WeekState.SHORT -> if (week.planned <= 0.0) {
                UiText.res(R.string.coverage_week_row_short_nothing)
            } else {
                val gap = trimSets(ProgramRules.COVERED_MIN - week.total)
                UiText.plural(R.plurals.coverage_week_row_short_by, if (gap == "1") 1 else 2, gap)
            }
        },
    )
}

/** A small square in the state's own look, hatching or dashes included: decorative, a word always sits beside it. */
@Composable
internal fun WeekSwatch(state: WeekState, modifier: Modifier = Modifier, size: Dp = 16.dp) {
    val look = weekLook(state)
    Canvas(modifier.size(size).clearAndSetSemantics {}) {
        val box = Path().apply { addRect(Rect(0f, 0f, this@Canvas.size.width, this@Canvas.size.height)) }
        drawPath(box, look.fill)
        drawDecor(box, look)
        drawPath(box, IronvellumColors.InkMuted, style = Stroke(1.dp.toPx()))
    }
}

/** The legend: the states this week actually has, each with its swatch, word and the hard sets it means. */
@Composable
internal fun WeekLegend(states: Set<WeekState>) {
    val floor = ProgramRules.COVERED_MIN.toInt()
    val shown = listOf(
        WeekState.DONE to "$floor+",
        WeekState.PLANNED to stringResource(R.string.coverage_week_legend_to, floor),
        WeekState.SHORT to null,
        WeekState.HEAVY to "${ProgramRules.HEAVY_WEEK.toInt()}+",
        WeekState.NONE to null,
    ).filter { (state, _) -> state in states }
    shown.forEach { (state, range) -> WeekLegendItem(state, range) }
}

@Composable
private fun WeekLegendItem(state: WeekState, range: String?) {
    val description = if (range == null) state.label else stringResource(R.string.coverage_legend_description, state.label, range)
    Row(
        Modifier.semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WeekSwatch(state, size = 14.dp)
        Spacer(Modifier.width(6.dp))
        Text(
            state.label,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            color = IronvellumColors.InkMuted,
        )
        if (range != null) {
            Spacer(Modifier.width(4.dp))
            Text(
                range,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.Bold,
                color = IronvellumColors.Ink,
            )
        }
    }
}

/** [text] with its numerals in bold, so "16 done + 6 planned" reads as numbers first. */
private fun boldNumbers(text: String): AnnotatedString = buildAnnotatedString {
    var last = 0
    for (match in Regex("[0-9]+([.,][0-9]+)?").findAll(text)) {
        append(text.substring(last, match.range.first))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = IronvellumColors.Ink)) { append(match.value) }
        last = match.range.last + 1
    }
    append(text.substring(last))
}

/**
 * One muscle's week as a row: its swatch, name and what the week does for it, the sets as "16 done + 6 planned" and
 * a two-part bar (done solid, planned lighter) scaled to the heavy line. Tapped, it lights the muscle on the figure.
 */
@Composable
internal fun WeekRow(muscle: Muscle, week: WeekMuscle, state: WeekState, onPick: () -> Unit) {
    val sets = weekSetsLabel(week)
    val note = weekRowNote(week, state)
    val showHint = stringResource(R.string.coverage_row_click_hint, muscle.label)
    val description = stringResource(R.string.coverage_row_description, muscle.label, sets, state.label)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = ListRowHeight)
            .clickable(onClickLabel = showHint, role = Role.Button, onClick = onPick)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                stateDescription = state.label
            }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        WeekSwatch(state, size = 20.dp)
        Column(Modifier.weight(1f)) {
            Text(muscle.label, style = MaterialTheme.typography.bodyLarge, color = IronvellumColors.Ink)
            Text(note, style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(boldNumbers(sets), style = MaterialTheme.typography.labelMedium, color = IronvellumColors.InkMuted)
            Spacer(Modifier.height(6.dp))
            WeekBar(week)
        }
        Text(
            "›",
            style = MaterialTheme.typography.titleMedium,
            color = IronvellumColors.InkMuted,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

/** Done and planned as two parts of one bar, full at the heavy line: the numbers beside it say the same. */
@Composable
private fun WeekBar(week: WeekMuscle) {
    val full = ProgramRules.HEAVY_WEEK.toFloat()
    val track = IronvellumColors.Rune
    val done = IronvellumColors.Emerald
    val planned = remember { IronvellumColors.Emerald.copy(alpha = 0.45f) }
    Canvas(Modifier.width(BAR_WIDTH).height(6.dp).clearAndSetSemantics {}) {
        drawRect(track, size = size)
        val doneW = (week.done.toFloat() / full).coerceIn(0f, 1f) * size.width
        val plannedW = (week.planned.toFloat() / full).coerceIn(0f, 1f - doneW / size.width) * size.width
        if (doneW > 0f) drawRect(done, Offset.Zero, Size(doneW, size.height))
        if (plannedW > 0f) drawRect(planned, Offset(doneW, 0f), Size(plannedW, size.height))
    }
}

private val BAR_WIDTH = 104.dp
