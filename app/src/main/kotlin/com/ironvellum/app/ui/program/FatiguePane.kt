package com.ironvellum.app.ui.program

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ironvellum.app.R
import com.ironvellum.app.data.Words
import com.ironvellum.app.domain.CauseShare
import com.ironvellum.app.domain.DayNames
import com.ironvellum.app.domain.FatigueReading
import com.ironvellum.app.domain.Muscle
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.domain.Variation
import com.ironvellum.app.domain.mostWorked
import com.ironvellum.app.ui.asString
import com.ironvellum.app.ui.components.InkDivider
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.components.SectionHeader
import com.ironvellum.app.ui.ironvellumRepository
import com.ironvellum.app.ui.theme.IronvellumColors
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.launch

/** Train > Muscles > Fatigue: the map, what it is based on, and the muscles worked most. */
@Composable
internal fun FatiguePane(
    viewModel: MuscleFatigueViewModel =
        viewModel(factory = viewModelFactory {
            initializer { MuscleFatigueViewModel(ironvellumRepository()) }
        }),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    FatigueContent(ui, rememberMuscleSelection(), ZoneId.systemDefault())
}

/** The pane for a given [ui]: stateless, so a test or a preview can hand it any report. */
@Composable
internal fun FatigueContent(ui: FatigueUi, selection: MutableState<Muscle?>, zone: ZoneId) {
    val report = ui.report
    // Blank for the first read: a moment of nothing is better than a flash of an all-fresh figure.
    if (!ui.loaded || report == null) return
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val worked = remember(report, zone) { report.mostWorked(zone) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 16.dp),
    ) {
        FatigueMap(report, selection, Modifier.fillMaxWidth())
        Text(
            stringResource(R.string.fatigue_window_caption),
            style = MaterialTheme.typography.bodySmall,
            color = IronvellumColors.InkMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        when {
            !ui.hasHistory -> EmptyLine(UiText.themed(R.string.fatigue_empty_new, R.string.fatigue_empty_new__t).asString())
            worked.isEmpty() -> EmptyLine(stringResource(R.string.fatigue_empty_rested))
            else -> {
                SectionHeader(stringResource(R.string.fatigue_most_worked_title))
                InkPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp)) {
                    worked.forEachIndexed { index, reading ->
                        if (index > 0) InkDivider()
                        MostWorkedRow(reading, report.nowMs, zone) {
                            // A row does what tapping the muscle on the figure does, so the page scrolls up to show it lit.
                            selection.value = reading.muscle
                            scope.launch { scroll.animateScrollTo(0) }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun EmptyLine(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = IronvellumColors.InkMuted,
        modifier = Modifier.padding(top = 16.dp),
    )
}

/** One muscle: its name and top cause on the left, its level and when it is fresh on the right. */
@Composable
private fun MostWorkedRow(reading: FatigueReading, nowMs: Long, zone: ZoneId, onOpen: () -> Unit) {
    val level = levelWord(reading.level)
    val fresh = freshByLine(reading.freshBy)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(
                role = Role.Button,
                onClickLabel = stringResource(R.string.fatigue_row_open_hint, reading.muscle.label),
                onClick = onOpen,
            )
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(reading.muscle.label, style = MaterialTheme.typography.bodyLarge, color = IronvellumColors.Ink)
            reading.causes.firstOrNull()?.let { cause ->
                Text(
                    causeLine(cause, nowMs, zone),
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FatigueSwatch(reading.level)
                Text(level, style = MaterialTheme.typography.labelLarge, color = IronvellumColors.Ink)
            }
            if (fresh != null) {
                Text(fresh, style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted)
            }
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = IronvellumColors.InkMuted)
    }
}

/** "today" for a session today, else the weekday's full name: the word a cause line hangs "'s lift" on. */
internal fun dayWord(atMs: Long, nowMs: Long, zone: ZoneId, locale: Locale = Locale.getDefault()): String {
    val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    val day = Instant.ofEpochMilli(atMs).atZone(zone).toLocalDate()
    return if (day == today) Words.text(UiText.res(R.string.fatigue_day_today)) else DayNames.full(day.dayOfWeek.value, locale).orEmpty()
}

/**
 * "about 60% from Tuesday's Romanian Deadlift": the top cause of a muscle's fatigue, by the weekday of the
 * session ("today's" for one today). A single source reads "from Tuesday's ...", not "about 100%".
 */
internal fun causeLine(cause: CauseShare, nowMs: Long, zone: ZoneId, locale: Locale = Locale.getDefault()): String {
    val day = dayWord(cause.sessionAtMs, nowMs, zone, locale)
    val lift = Variation.label(cause.exerciseName, cause.modifiers)
    return if (cause.percent >= 100) {
        Words.text(UiText.res(R.string.fatigue_cause_line_only, day, lift))
    } else {
        Words.text(UiText.res(R.string.fatigue_cause_line, cause.percent, day, lift))
    }
}
