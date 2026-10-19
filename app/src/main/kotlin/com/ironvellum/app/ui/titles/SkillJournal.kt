package com.ironvellum.app.ui.titles

import com.ironvellum.app.R
import com.ironvellum.app.data.Units
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ironvellum.app.data.Words
import com.ironvellum.app.domain.SkillPractice
import com.ironvellum.app.domain.Skills
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.ui.asString
import com.ironvellum.app.ui.components.InkDivider
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.components.ListRow
import com.ironvellum.app.ui.components.SectionHeader
import com.ironvellum.app.ui.components.formatDate
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.TileShape
import com.ironvellum.app.ui.theme.inkBorder
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val WEEKS = 12

/** Paths named on the Journal's one Paths row before "and N more". */
private const val PATHS_NAMED = 3

/** "Thu 8 Oct". */
private const val DAY_PATTERN = "EEE d MMM"

/**
 * The practice record, trimmed: three figures, a twelve-week heat strip, one row into the
 * Paths tab and the timeline of logged attempts grouped by day.
 */
@Composable
fun SkillJournal(
    log: List<SkillPractice>,
    onOpenPaths: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val attempts = log.filterNot { it.claimed }
    // A claim is a day of practice too, so the strip and the days kept count it.
    val byDay = log.groupingBy {
        Instant.ofEpochMilli(it.practicedAtMs).atZone(zone).toLocalDate()
    }.eachCount()
    val today = LocalDate.now()
    val start = today.minusWeeks((WEEKS - 1).toLong()).with(java.time.DayOfWeek.MONDAY)

    // An empty Journal is a row of zeros: say what fills it.
    if (log.isEmpty()) {
        Text(
            UiText.themed(R.string.skilljournal_empty, R.string.skilljournal_empty__t).asString(),
            style = MaterialTheme.typography.bodySmall,
            color = IronvellumColors.InkMuted,
            modifier = Modifier.padding(top = 16.dp),
        )
    }

    // ---- figures: unboxed, one row ----------------------------------------
    Row(Modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, top = 16.dp)) {
        Figure(UiText.res(R.string.skilljournal_figure_attempts).asString(), attempts.size.toString(), Modifier.weight(1f))
        Figure(UiText.res(R.string.skilljournal_figure_days_kept).asString(), "${practiceStreak(byDay, today)}", Modifier.weight(1f))
        Figure(
            UiText.res(R.string.skilljournal_figure_last).asString(),
            attempts.firstOrNull()?.let { formatDate(it.practicedAtMs, DAY_PATTERN) } ?: "—",
            Modifier.weight(1f),
        )
    }

    // ---- heat strip --------------------------------------------------------
    val practised = byDay.count { (date, n) -> n > 0 && !date.isBefore(start) && !date.isAfter(today) }
    Row(
        Modifier.fillMaxWidth().padding(top = 24.dp, start = 2.dp, end = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(UiText.res(R.string.skilljournal_heat_title).asString(), style = MaterialTheme.typography.titleSmall, color = IronvellumColors.Ink)
        Text(
            UiText.plural(R.plurals.skilljournal_days_practised, practised).asString(),
            style = MaterialTheme.typography.labelSmall,
            color = IronvellumColors.InkMuted,
        )
    }
    Spacer(Modifier.height(10.dp))
    // Colour alone carries the cells, so the grid reads out as one summary and its cells stay
    // out of the accessibility tree.
    val summary = heatmapSummary(byDay, start, today)
    // One cell size for the whole grid. Per-column weights and aspect ratios rounded each column's cells to
    // different pixel heights, so the columns drifted apart by a few pixels towards the bottom row.
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = 3.dp
        val cell = (maxWidth - gap * (WEEKS - 1)) / WEEKS
        Row(
            Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = summary },
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            repeat(WEEKS) { w ->
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    repeat(7) { d ->
                        val date = start.plusDays((w * 7 + d).toLong())
                        // Days that have not happened draw nothing, so the corner reads as an
                        // unfinished week rather than missing cells.
                        if (date.isAfter(today)) {
                            Spacer(Modifier.size(cell))
                        } else {
                            Spacer(
                                Modifier
                                    .size(cell)
                                    .background(heatColor(byDay[date] ?: 0), TileShape)
                                    .then(
                                        if (date == today) Modifier.inkBorder(IronvellumColors.Ink, TileShape, 1.5.dp) else Modifier,
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }

    // ---- one Paths row -----------------------------------------------------
    Spacer(Modifier.height(16.dp))
    val lines = Skills.LINES
    val more = lines.size - PATHS_NAMED
    InkPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
        ListRow(
            label = Words.paths.c,
            subline = lines.take(PATHS_NAMED).joinToString(", ").let {
                if (more > 0) UiText.plural(R.plurals.skilljournal_paths_named_more, more, it, more).asString() else it
            },
            onClickLabel = UiText.themed(
                R.string.skilljournal_open_paths_description,
                R.string.skilljournal_open_paths_description__t,
            ).asString(),
            // Inside a panel with no padding of its own, so the row pads itself off the border.
            contentPadding = PaddingValues(horizontal = 16.dp),
            onClick = onOpenPaths,
        )
    }

    // ---- timeline ----------------------------------------------------------
    SectionHeader(UiText.res(R.string.skilljournal_timeline_header).asString(), topPadding = 16.dp)
    if (log.isEmpty()) {
        Text(
            UiText.themed(R.string.skilljournal_blank, R.string.skilljournal_blank__t).asString(),
            style = MaterialTheme.typography.bodySmall,
            color = IronvellumColors.InkMuted,
            modifier = Modifier.padding(vertical = 12.dp),
        )
        return
    }

    // Recent practice days only: this journal grows for as long as the lifter trains, and it
    // renders inside a plain scrolling Column that composes every row it is handed.
    log.groupBy { Instant.ofEpochMilli(it.practicedAtMs).atZone(zone).toLocalDate() }
        .toSortedMap(compareByDescending { it })
        .entries.take(PRACTICE_DAYS)
        .forEach { (date, entries) ->
            Text(
                formatDate(entries.first().practicedAtMs, DAY_PATTERN),
                style = MaterialTheme.typography.labelSmall,
                color = IronvellumColors.InkMuted,
                modifier = Modifier.padding(start = 2.dp, top = 12.dp),
            )
            entries.forEachIndexed { i, entry ->
                if (i > 0) InkDivider()
                val def = Skills.forName(entry.skillName)
                ListRow(
                    label = entry.skillName,
                    subline = formatDate(entry.practicedAtMs, "HH:mm") +
                        (def?.let { " · ${it.line} · ${Skills.tierLabel(it.tier)}" } ?: ""),
                    value = when {
                        entry.claimed -> Words.claimed.c
                        entry.weightKg != null -> "${def?.let { SkillGuidance.withUnit(entry.value, it) } ?: entry.value} @ ${Units.withUnit(entry.weightKg)}"
                        else -> def?.let { SkillGuidance.withUnit(entry.value, it) } ?: "${entry.value}"
                    },
                    valueColor = if (entry.claimed) IronvellumColors.SovereignGold else IronvellumColors.InkMuted,
                    onClickLabel = UiText.res(R.string.titles_open_description, entry.skillName).asString(),
                    onClick = { onSelect(entry.skillName) },
                )
            }
        }
}

/** One accent ramp: 1, 2, 3 and 4+ attempts a day; an empty day is a Rune cell. */
private fun heatColor(count: Int) = when {
    count >= 4 -> IronvellumColors.Emerald
    count == 3 -> IronvellumColors.Emerald.copy(alpha = 0.75f)
    count == 2 -> IronvellumColors.Emerald.copy(alpha = 0.5f)
    count == 1 -> IronvellumColors.Emerald.copy(alpha = 0.28f)
    else -> IronvellumColors.Rune
}

@Composable
private fun Figure(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = IronvellumColors.Ink, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = IronvellumColors.InkMuted)
    }
}

/**
 * One sentence for the training heatmap: "12 weeks: 9 days practised, most 4
 * attempts on 3 Sep". Only days from [start] to [today] count.
 */
internal fun heatmapSummary(byDay: Map<LocalDate, Int>, start: LocalDate, today: LocalDate): String {
    val days = byDay.filter { (date, n) -> n > 0 && !date.isBefore(start) && !date.isAfter(today) }
    if (days.isEmpty()) return Words.text(UiText.plural(R.plurals.skilljournal_heatmap_empty, WEEKS))
    // the latest day among those tied for most
    val top = days.entries.maxWith(compareBy({ it.value }, { it.key }))
    val on = top.key.format(DateTimeFormatter.ofPattern("d MMM", java.util.Locale.getDefault()))
    return Words.text(
        UiText.plural(
            R.plurals.skilljournal_heatmap_summary,
            WEEKS,
            WEEKS,
            UiText.plural(R.plurals.skilljournal_days_practised, days.size),
            UiText.plural(R.plurals.skilljournal_attempts_count, top.value),
            on,
        ),
    )
}

/** Consecutive days with at least one logged attempt, counting back from today. */
private fun practiceStreak(byDay: Map<LocalDate, Int>, today: LocalDate): Int {
    var streak = 0
    var day = today
    if (byDay[day] == null) day = today.minusDays(1)
    while ((byDay[day] ?: 0) > 0) {
        streak++
        day = day.minusDays(1)
    }
    return streak
}

/** Recent practice days; the per-skill detail carries the full record. */
private const val PRACTICE_DAYS = 14
