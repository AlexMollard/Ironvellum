package com.ironvellum.app.ui.program

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ironvellum.app.R
import com.ironvellum.app.data.Words
import com.ironvellum.app.domain.DayNames
import com.ironvellum.app.domain.Exercise
import com.ironvellum.app.domain.FatigueReading
import com.ironvellum.app.domain.LightBy
import com.ironvellum.app.domain.SorenessNote
import com.ironvellum.app.domain.SorenessReason
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.domain.Variation
import com.ironvellum.app.ui.components.ExerciseInfoSheet
import com.ironvellum.app.ui.components.InfoAction
import com.ironvellum.app.ui.components.InfoChip
import com.ironvellum.app.ui.components.InfoSheet
import com.ironvellum.app.ui.components.InfoSheetInline
import com.ironvellum.app.ui.components.InfoSheetScope
import com.ironvellum.app.ui.theme.IronvellumColors
import java.time.ZoneId
import java.util.Locale

/** "Back to light by Saturday"; null when the muscle is already light or fresh. [locale] names the weekday. */
internal fun lightByLine(lightBy: LightBy, locale: Locale = Locale.getDefault()): String? = when (lightBy) {
    LightBy.None -> null
    LightBy.Today -> Words.text(UiText.res(R.string.fatigue_light_by_today))
    LightBy.Tomorrow -> Words.text(UiText.res(R.string.fatigue_light_by_tomorrow))
    is LightBy.Weekday -> Words.text(UiText.res(R.string.fatigue_light_by_weekday, DayNames.full(lightBy.day.value, locale).orEmpty()))
    LightBy.OverAWeek -> Words.text(UiText.res(R.string.fatigue_light_by_over_week))
}

/** Why soreness is likely, in plain words: the lift by name and, when it applies, the stretched muscle. */
internal fun sorenessLine(note: SorenessNote): String {
    val lift = Variation.label(note.exerciseName, note.modifiers)
    val muscle = note.muscle.label.lowercase()
    val res = when (note.reason) {
        SorenessReason.NEW_AND_STRETCH -> R.string.fatigue_soreness_new_stretch
        SorenessReason.NEW -> R.string.fatigue_soreness_new
        SorenessReason.STRETCH -> R.string.fatigue_soreness_stretch
    }
    return Words.text(UiText.res(res, lift, muscle))
}

/** One source in a breakdown: what it is, where or when, its figure, and the way into it when there is one. */
internal class BreakdownSource(
    val label: String,
    val detail: String,
    val figure: String,
    /** Its width in the bar, in whatever unit the sources' figures share. */
    val weight: Float,
    val color: Color,
    val onOpen: (() -> Unit)?,
)

/** What is left after the listed sources: a quiet row and a quiet end of the bar. */
internal class BreakdownOther(val figure: String, val weight: Float)

/**
 * The one muscle breakdown, for Fatigue and for Coverage: the muscle, a subtitle and chip, a bar and a row for each
 * source with its figure, and the sections each screen adds before ([lead]) and after ([extra]). A source with
 * [BreakdownSource.onOpen] opens that lift's exercise sheet, which has its own history.
 */
@Composable
internal fun MuscleBreakdownSheet(
    title: String,
    subtitle: String,
    chips: List<InfoChip>,
    actions: List<InfoAction>,
    sourcesTitle: String,
    sources: List<BreakdownSource>,
    other: BreakdownOther?,
    none: String,
    onDismiss: () -> Unit,
    lead: InfoSheetScope.() -> Unit = {},
    extra: InfoSheetScope.() -> Unit = {},
    inline: Boolean = false,
) {
    val content: InfoSheetScope.() -> Unit = {
        lead()
        if (sources.isNotEmpty()) {
            section(sourcesTitle) {
                ShareBar(sources, other)
                sources.forEach { SourceRow(it) }
                if (other != null) OtherRow(other)
            }
        } else {
            text(null, none, IronvellumColors.InkMuted)
        }
        extra()
    }
    if (inline) {
        InfoSheetInline(title = title, subtitle = subtitle, chips = chips, actions = actions, content = content)
    } else {
        InfoSheet(title = title, onDismiss = onDismiss, subtitle = subtitle, chips = chips, actions = actions, content = content)
    }
}

/**
 * One muscle's fatigue breakdown: its level, a bar and a row for each source with its rough share, the soreness note
 * when there is one, when it is back to light, and the way to the estimate's explanation. A cause row opens
 * that lift's exercise sheet (with its own history), on the variation the set was logged with.
 */
@Composable
internal fun FatigueBreakdownSheet(
    reading: FatigueReading,
    nowMs: Long,
    onOpenExercise: (name: String, modifiers: String) -> Unit,
    onExplain: () -> Unit,
    onDismiss: () -> Unit,
    zone: ZoneId = ZoneId.systemDefault(),
    inline: Boolean = false,
) {
    val lightBy = lightByLine(reading.lightBy)
    MuscleBreakdownSheet(
        title = reading.muscle.label,
        subtitle = t(R.string.fatigue_breakdown_subtitle),
        chips = listOf(InfoChip(levelWord(reading.level), IronvellumColors.Ink)),
        actions = listOf(InfoAction(t(R.string.fatigue_explainer_link), onExplain, quiet = true)),
        sourcesTitle = t(R.string.fatigue_breakdown_causes_title),
        sources = reading.causes.mapIndexed { index, cause ->
            BreakdownSource(
                label = Variation.label(cause.exerciseName, cause.modifiers),
                detail = sessionDay(cause.sessionAtMs, nowMs, zone),
                figure = shareText(cause.percent),
                weight = cause.percent.coerceAtLeast(1).toFloat(),
                color = IronvellumColors.Emerald.copy(alpha = SEGMENT_ALPHAS[index]),
                onOpen = { onOpenExercise(cause.exerciseName, cause.modifiers) },
            )
        },
        other = reading.otherPercent.takeIf { it > 0 }?.let { BreakdownOther(shareText(it), it.toFloat()) },
        none = t(R.string.fatigue_breakdown_none),
        onDismiss = onDismiss,
        inline = inline,
        extra = {
            reading.soreness?.let { note ->
                section(t(R.string.fatigue_soreness_title)) {
                    Text(sorenessLine(note), style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.Ink)
                }
            }
            if (lightBy != null) text(null, lightBy)
        },
    )
}

private val SEGMENT_ALPHAS = listOf(1f, 0.6f, 0.3f)

/** The sources side by side as one bar: a segment each by its weight, the rest as a quiet remainder. */
@Composable
private fun ShareBar(sources: List<BreakdownSource>, other: BreakdownOther?) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(MaterialTheme.shapes.extraSmall)
            // The rows say every figure in words; the bar is only the picture of them.
            .clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        sources.forEach { Box(Modifier.weight(it.weight).height(10.dp).background(it.color)) }
        if (other != null) Box(Modifier.weight(other.weight).height(10.dp).background(IronvellumColors.InkMuted.copy(alpha = 0.5f)))
    }
}

@Composable
private fun SourceRow(source: BreakdownSource) {
    val open = source.onOpen
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .then(if (open != null) Modifier.clickable(role = Role.Button, onClickLabel = t(R.string.fatigue_cause_open_hint, source.label), onClick = open) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(10.dp).background(source.color))
        Column(Modifier.weight(1f)) {
            Text(
                source.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = IronvellumColors.Ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(source.detail, style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted)
        }
        Text(source.figure, style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.Ink)
        if (open != null) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = IronvellumColors.InkMuted)
        } else {
            Box(Modifier.width(24.dp))
        }
    }
}

@Composable
private fun OtherRow(other: BreakdownOther) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(10.dp).background(IronvellumColors.InkMuted.copy(alpha = 0.5f)))
        Text(
            t(R.string.fatigue_other_label),
            style = MaterialTheme.typography.bodyMedium,
            color = IronvellumColors.InkMuted,
            modifier = Modifier.weight(1f),
        )
        Text(other.figure, style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.InkMuted)
        Box(Modifier.width(24.dp))
    }
}

@Composable
private fun shareText(percent: Int): String = t(R.string.fatigue_share_percent, percent)

/** "Tuesday", or "Today" for a session today. */
private fun sessionDay(atMs: Long, nowMs: Long, zone: ZoneId, locale: Locale = Locale.getDefault()): String =
    dayWord(atMs, nowMs, zone, locale).replaceFirstChar { it.titlecase(locale) }

/**
 * The exercise sheet for a lift named in a breakdown: the one the rest of the app opens, with its About, Form,
 * History and Fatigue pages. Gone at once when the catalogue has no exercise of that name.
 */
@Composable
internal fun ExerciseInspection(exercises: List<Exercise>, name: String, modifiers: String, onDismiss: () -> Unit) {
    val exercise = remember(exercises, name) { exercises.firstOrNull { it.name == name } }
    if (exercise == null) {
        LaunchedEffect(name) { onDismiss() }
        return
    }
    ExerciseInfoSheet(exercise = exercise, lastLine = null, onDismiss = onDismiss, onPick = null, modifiers = modifiers)
}

/** A string resource as the sheet content builders need it: they are not composable, so no stringResource. */
private fun t(@StringRes id: Int, vararg args: Any): String = Words.text(UiText.res(id, *args))
