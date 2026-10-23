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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ironvellum.app.R
import com.ironvellum.app.data.Words
import com.ironvellum.app.domain.CauseShare
import com.ironvellum.app.domain.DayNames
import com.ironvellum.app.domain.FatigueReading
import com.ironvellum.app.domain.LightBy
import com.ironvellum.app.domain.SorenessNote
import com.ironvellum.app.domain.SorenessReason
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.domain.Variation
import com.ironvellum.app.ui.components.InfoAction
import com.ironvellum.app.ui.components.InfoChip
import com.ironvellum.app.ui.components.InfoSheet
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

/**
 * One muscle's breakdown: its level, a bar and a row for each source with its rough share, the soreness note
 * when there is one, when it is back to light, and the way to the estimate's explanation. A cause row opens
 * that lift's page, on the variation the set was logged with.
 */
@Composable
internal fun MuscleBreakdownSheet(
    reading: FatigueReading,
    nowMs: Long,
    onOpenLift: (name: String, variation: String) -> Unit,
    onExplain: () -> Unit,
    onDismiss: () -> Unit,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val lightBy = lightByLine(reading.lightBy)
    InfoSheet(
        title = reading.muscle.label,
        onDismiss = onDismiss,
        subtitle = t(R.string.fatigue_breakdown_subtitle),
        chips = listOf(InfoChip(levelWord(reading.level), IronvellumColors.Ink)),
        actions = listOf(InfoAction(t(R.string.fatigue_explainer_link), onExplain, quiet = true)),
    ) {
        if (reading.causes.isNotEmpty()) {
            section(t(R.string.fatigue_breakdown_causes_title)) {
                ShareBar(reading)
                reading.causes.forEachIndexed { index, cause ->
                    CauseRow(cause, SEGMENT_ALPHAS[index], nowMs, zone) { onOpenLift(cause.exerciseName, Variation.key(cause.modifiers)) }
                }
                if (reading.otherPercent > 0) OtherRow(reading.otherPercent)
            }
        } else {
            text(null, t(R.string.fatigue_breakdown_none), IronvellumColors.InkMuted)
        }
        reading.soreness?.let { note ->
            section(t(R.string.fatigue_soreness_title)) {
                Text(sorenessLine(note), style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.Ink)
            }
        }
        if (lightBy != null) text(null, lightBy)
    }
}

private val SEGMENT_ALPHAS = listOf(1f, 0.6f, 0.3f)

/** The sources side by side as one bar: a segment each by its percent, the rest as a quiet remainder. */
@Composable
private fun ShareBar(reading: FatigueReading) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(MaterialTheme.shapes.extraSmall)
            // The rows say every share in words; the bar is only the picture of them.
            .clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        reading.causes.forEachIndexed { index, cause ->
            Box(Modifier.weight(cause.percent.coerceAtLeast(1).toFloat()).height(10.dp).background(IronvellumColors.Emerald.copy(alpha = SEGMENT_ALPHAS[index])))
        }
        if (reading.otherPercent > 0) {
            Box(Modifier.weight(reading.otherPercent.toFloat()).height(10.dp).background(IronvellumColors.InkMuted.copy(alpha = 0.5f)))
        }
    }
}

@Composable
private fun CauseRow(cause: CauseShare, alpha: Float, nowMs: Long, zone: ZoneId, onOpen: () -> Unit) {
    val lift = Variation.label(cause.exerciseName, cause.modifiers)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(role = Role.Button, onClickLabel = t(R.string.fatigue_cause_open_hint, lift), onClick = onOpen),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(10.dp).background(IronvellumColors.Emerald.copy(alpha = alpha)))
        Column(Modifier.weight(1f)) {
            Text(
                lift,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = IronvellumColors.Ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(sessionDay(cause.sessionAtMs, nowMs, zone), style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted)
        }
        Text(shareText(cause.percent), style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.Ink)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = IronvellumColors.InkMuted)
    }
}

@Composable
private fun OtherRow(percent: Int) {
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
        Text(shareText(percent), style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.InkMuted)
        Box(Modifier.width(24.dp))
    }
}

@Composable
private fun shareText(percent: Int): String = t(R.string.fatigue_share_percent, percent)

/** "Tuesday", or "Today" for a session today. */
private fun sessionDay(atMs: Long, nowMs: Long, zone: ZoneId, locale: Locale = Locale.getDefault()): String =
    dayWord(atMs, nowMs, zone, locale).replaceFirstChar { it.titlecase(locale) }

/** A string resource as the sheet content builders need it: they are not composable, so no stringResource. */
private fun t(@StringRes id: Int, vararg args: Any): String = Words.text(UiText.res(id, *args))
