package com.ironvellum.app.ui.titles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import com.ironvellum.app.domain.SkillPractice
import com.ironvellum.app.domain.Skills
import com.ironvellum.app.ui.components.InkDivider
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.components.ListRow
import com.ironvellum.app.ui.components.SectionHeader
import com.ironvellum.app.ui.components.formatDate
import com.ironvellum.app.ui.components.plural
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
            "Every attempt at a technique is written here. Open a path, pick a technique and log an attempt.",
            style = MaterialTheme.typography.bodySmall,
            color = IronvellumColors.InkMuted,
            modifier = Modifier.padding(top = 16.dp),
        )
    }

    // ---- figures: unboxed, one row ----------------------------------------
    Row(Modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, top = 16.dp)) {
        Figure("Attempts", attempts.size.toString(), Modifier.weight(1f))
        Figure("Days kept", "${practiceStreak(byDay, today)}", Modifier.weight(1f))
        Figure(
            "Last",
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
        Text("Last 12 weeks", style = MaterialTheme.typography.titleSmall, color = IronvellumColors.Ink)
        Text(
            "$practised ${plural(practised, "day", "days")} practised",
            style = MaterialTheme.typography.labelSmall,
            color = IronvellumColors.InkMuted,
        )
    }
    Spacer(Modifier.height(10.dp))
    // Colour alone carries the cells, so the grid reads out as one summary and its cells stay
    // out of the accessibility tree.
    val summary = heatmapSummary(byDay, start, today)
    Row(
        Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = summary },
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        repeat(WEEKS) { w ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(7) { d ->
                    val date = start.plusDays((w * 7 + d).toLong())
                    // Days that have not happened draw nothing, so the corner reads as an
                    // unfinished week rather than missing cells.
                    if (date.isAfter(today)) {
                        Spacer(Modifier.fillMaxWidth().aspectRatio(1f))
                    } else {
                        Spacer(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
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

    // ---- one Paths row -----------------------------------------------------
    Spacer(Modifier.height(16.dp))
    val lines = Skills.LINES
    val more = lines.size - PATHS_NAMED
    InkPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
        ListRow(
            label = "Paths",
            subline = lines.take(PATHS_NAMED).joinToString(", ") + if (more > 0) " and $more more" else "",
            onClickLabel = "Open Paths",
            onClick = onOpenPaths,
        )
    }

    // ---- timeline ----------------------------------------------------------
    SectionHeader("Timeline", topPadding = 16.dp)
    if (log.isEmpty()) {
        Text(
            "The Journal is blank. Open a technique and log what you actually hit.",
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
                        entry.claimed -> "Claimed"
                        entry.weightKg != null -> "${def?.let { SkillGuidance.withUnit(entry.value, it) } ?: entry.value} @${formatLoad(entry.weightKg)}kg"
                        else -> def?.let { SkillGuidance.withUnit(entry.value, it) } ?: "${entry.value}"
                    },
                    valueColor = if (entry.claimed) IronvellumColors.SovereignGold else IronvellumColors.InkMuted,
                    onClickLabel = "Open ${entry.skillName}",
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
    if (days.isEmpty()) return "$WEEKS weeks: no attempts logged"
    // the latest day among those tied for most
    val top = days.entries.maxWith(compareBy({ it.value }, { it.key }))
    val on = top.key.format(DateTimeFormatter.ofPattern("d MMM", java.util.Locale.getDefault()))
    return "$WEEKS weeks: ${days.size} ${plural(days.size, "day", "days")} practised, " +
        "most ${top.value} ${plural(top.value, "attempt", "attempts")} on $on"
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
