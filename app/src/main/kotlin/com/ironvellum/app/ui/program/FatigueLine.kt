package com.ironvellum.app.ui.program

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ironvellum.app.R
import com.ironvellum.app.data.Words
import com.ironvellum.app.domain.DayNames
import com.ironvellum.app.domain.FatigueReport
import com.ironvellum.app.domain.ListText
import com.ironvellum.app.domain.PlanFatigue
import com.ironvellum.app.domain.PlannedPreset
import com.ironvellum.app.domain.RightNowRow
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.ui.components.InkDivider
import com.ironvellum.app.ui.components.ListRowHeight
import com.ironvellum.app.ui.ironvellumRepository
import com.ironvellum.app.ui.asString
import com.ironvellum.app.ui.theme.IronvellumColors
import java.time.ZoneId
import java.util.Locale

/** The fatigue report the plan builder reads, from the same local history Muscles reads; null until the first read. */
@Composable
internal fun rememberFatigueReport(): FatigueReport? {
    val model: MuscleFatigueViewModel = viewModel(factory = viewModelFactory {
        initializer { MuscleFatigueViewModel(ironvellumRepository()) }
    })
    return model.ui.collectAsStateWithLifecycle().value.report
}

/** "Quads and hamstrings still recovering": what [row] names, sentence-cased. */
internal fun rightNowTitle(row: RightNowRow): String {
    val names = ListText.and(row.items.mapIndexed { i, item -> if (i == 0) item.muscle.label else item.muscle.label.lowercase() })
    return Words.text(UiText.res(R.string.fatigue_right_now_title, names))
}

/** "Very high and High · from Thursday's session": the levels and the session behind the worst muscle. */
internal fun rightNowSubtitle(row: RightNowRow, nowMs: Long, zone: ZoneId, locale: Locale = Locale.getDefault()): String {
    val levels = ListText.and(row.items.map { levelWord(it.level) }.distinct())
    val day = dayWord(row.items.first().cause.sessionAtMs, nowMs, zone, locale)
    return Words.text(UiText.res(R.string.fatigue_right_now_subtitle, levels, day))
}

/** "Quads: Moderate by Monday": one quiet line per muscle the day trains that is still worked at 08:00. */
internal fun dayNoteLines(preset: PlannedPreset, report: FatigueReport, zone: ZoneId, locale: Locale = Locale.getDefault()): List<String> =
    PlanFatigue.dayNotes(preset, report, zone).map {
        Words.text(UiText.res(R.string.fatigue_day_note, it.muscle.label, levelWord(it.level), DayNames.full(it.weekday.value, locale).orEmpty()))
    }

/**
 * "Muscles right now": under the coverage chip, what is still recovering and from which session, linking to
 * Muscles > Fatigue. The line under it says the plan above is untouched.
 */
@Composable
internal fun RightNowLine(row: RightNowRow, nowMs: Long, zone: ZoneId, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val title = rightNowTitle(row)
    val subtitle = rightNowSubtitle(row, nowMs, zone)
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = ListRowHeight)
                .clickable(onClickLabel = stringResource(R.string.fatigue_right_now_hint), role = Role.Button, onClick = onOpen)
                .semantics(mergeDescendants = true) { contentDescription = "$title. $subtitle" },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FatigueSwatch(row.items.first().level)
            Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.Ink)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = IronvellumColors.InkMuted)
            }
            Text(stringResource(R.string.fatigue_right_now_link), style = MaterialTheme.typography.labelMedium, color = IronvellumColors.SystemGreen)
        }
        InkDivider()
        Text(
            UiText.themed(R.string.fatigue_plan_unchanged, R.string.fatigue_plan_unchanged__t).asString(),
            style = MaterialTheme.typography.labelSmall,
            color = IronvellumColors.InkMuted,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
