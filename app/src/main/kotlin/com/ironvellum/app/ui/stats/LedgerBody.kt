package com.ironvellum.app.ui.stats

import com.ironvellum.app.ui.components.PushedHeader
import com.ironvellum.app.domain.fmt
import com.ironvellum.app.ui.components.InkChip
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import com.ironvellum.app.ui.components.InkIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ironvellum.app.domain.BodyStats
import com.ironvellum.app.domain.Ledger
import com.ironvellum.app.domain.LedgerRange
import com.ironvellum.app.domain.MeasurementSite
import com.ironvellum.app.domain.Measurements
import com.ironvellum.app.domain.StatEntry
import com.ironvellum.app.ui.components.InkDivider
import com.ironvellum.app.ui.components.InkListRow
import com.ironvellum.app.ui.components.InkRowPanel
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.components.LedgerSpace
import com.ironvellum.app.ui.components.ListRowHeight
import com.ironvellum.app.ui.components.PanelLabel
import com.ironvellum.app.ui.components.RangeChips
import com.ironvellum.app.ui.components.StatSize
import com.ironvellum.app.ui.components.StatValue
import com.ironvellum.app.ui.components.UndoBar
import com.ironvellum.app.ui.components.TrendChart
import com.ironvellum.app.ui.components.formatBodyValue
import com.ironvellum.app.ui.components.formatDate
import com.ironvellum.app.ui.components.plural
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.IronvellumTracking
import com.ironvellum.app.ui.theme.inkBorder
import java.time.LocalDate
import java.time.ZoneId

/**
 * BODY: one hero panel (weight, its window delta, a dated chart, BMI and FFMI
 * chips), then the rows that lead elsewhere. Everything longer than a line is
 * one tap away on its own pane.
 */
@Composable
internal fun BodyTab(
    ui: StatsUi,
    today: LocalDate,
    zone: ZoneId,
    scroll: ScrollState,
    range: LedgerRange,
    onRange: (LedgerRange) -> Unit,
    onDrill: (String) -> Unit,
    onLogWeight: () -> Unit,
    onSetHeight: () -> Unit,
    onOpenTraining: () -> Unit,
    onOpenTape: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    val latest = ui.stats.firstOrNull()
    val plot = remember(ui.stats, today, range) { Ledger.weightPlot(ui.stats, range, today, zone) }
    val ffmi = remember(ui.stats, ui.profileHeight) { Ledger.latestFfmi(ui.stats, ui.profileHeight) }
    val bmi = latest?.let { Ledger.bmiOf(it, ui.profileHeight) }
    val heightKnown = (ui.profileHeight ?: 0.0) > 0.0 || (latest?.heightCm ?: 0.0) > 0.0
    // The calendar's week start, so the strip and the grid cut the same weeks.
    // Monday, as everywhere else in the app: the locale's Sunday split the
    // weeks differently from Today's rail.
    val weekStart = java.time.DayOfWeek.MONDAY
    val weeks = remember(ui.completedDates, today, weekStart) {
        Ledger.weeklyCounts(ui.completedDates, today, weekStart = weekStart)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = LedgerSpace.Gutter),
        verticalArrangement = Arrangement.spacedBy(LedgerSpace.Panel),
    ) {
        Spacer(Modifier.height(LedgerSpace.Panel))

        InkPanel(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PanelLabel("WEIGHT", Modifier.weight(1f))
                // One reading has no range to compare over: the chips wait for a second.
                if (ui.stats.size >= 2) {
                    RangeChips(
                        options = LedgerRange.entries.map { it to it.label },
                        selected = range,
                        onPick = onRange,
                    )
                }
            }
            if (latest == null) {
                WeightEmpty(onLogWeight)
            } else {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    StatValue(formatBodyValue(latest.weightKg), size = StatSize.Hero, unit = "kg")
                    plot?.deltaKg?.let {
                        Text(
                            "${Ledger.signed(it, "kg")} \u00B7 ${range.label}",
                            style = MaterialTheme.typography.titleSmall,
                            fontFamily = ChakraPetch,
                            color = IronvellumColors.InkMuted,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Logged ${formatDate(latest.takenAtMs, "d MMM")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = IronvellumColors.InkMuted,
                        modifier = Modifier.weight(1f),
                    )
                    // The one way to log a weight once there is one, beside the
                    // readings behind the chart.
                    InkChip("Log", "Log weight", Icons.Outlined.Add, onClick = onLogWeight)
                    InkChip("History", "Open weight history", onClick = onOpenHistory)
                }
                Spacer(Modifier.height(4.dp))
                when {
                    plot == null -> ChartNote("No reading in the last ${range.label}. Widen the range.")
                    plot.values.size < 2 -> ChartNote("Two readings draw the line.")
                    else -> TrendChart(
                        plot.values,
                        IronvellumColors.Emerald,
                        fromZero = false,
                        positions = plot.positions,
                        startLabel = shortDay(plot.startDate),
                        endLabel = shortDay(plot.endDate),
                        recordMarker = false,
                        valueText = { "%.1f kg".fmt(it) },
                        dateText = { shortDay(plot.dates[it]) },
                    )
                }
            }
            Spacer(Modifier.height(LedgerSpace.Panel))
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(LedgerSpace.Panel),
            ) {
                StatChip(
                    "BMI",
                    bmi?.let { formatBodyValue(it) } ?: "\u2014",
                    bmi?.let { BodyStats.bmiCategory(it) } ?: if (heightKnown) "needs a weight" else "tap to set your height",
                    onClick = { if (heightKnown) onDrill("BMI") else onSetHeight() },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                StatChip(
                    "FFMI",
                    ffmi?.let { formatBodyValue(it.value) } ?: "\u2014",
                    ffmi?.let { "from ${formatDate(it.takenAtMs, "d MMM")}" }
                        ?: if (heightKnown) "needs a body fat %" else "tap to set your height",
                    onClick = { if (heightKnown) onDrill("FFMI") else onSetHeight() },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }

        InkRowPanel(Modifier.fillMaxWidth()) {
            ConsistencyRow(weeks, onOpenTraining)
            InkDivider()
            val logged = Measurements.latest(ui.measurements).size
            InkListRow(
                label = "Tape readings",
                value = "$logged of ${MeasurementSite.entries.size} logged",
                supporting = "stays on this device",
                onClick = onOpenTape,
            )
        }
        Spacer(Modifier.height(LedgerSpace.Section))
    }
}

private fun shortDay(date: LocalDate): String =
    date.format(java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.getDefault()))

@Composable
private fun ChartNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted)
}

@Composable
private fun WeightEmpty(onLogWeight: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = LedgerSpace.Panel),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(LedgerSpace.Panel),
    ) {
        Text(
            "The Ledger knows nothing of your frame yet.",
            style = MaterialTheme.typography.bodySmall,
            color = IronvellumColors.InkMuted,
        )
        IronvellumButton("Log weight", onClick = onLogWeight)
    }
}

/** A tappable BMI / FFMI figure: label, value, one muted line and a chevron, with no box. Opens the band sheet. */
@Composable
private fun StatChip(label: String, value: String, hint: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = LedgerSpace.Target),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = IronvellumColors.InkMuted)
            StatValue(value, size = StatSize.Inline)
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = IronvellumColors.InkMuted)
    }
}

@Composable
private fun ConsistencyRow(weeks: List<Int>, onOpen: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
    ) {
        // The weekly average, not this month's count: Training's calendar
        // already leads with that number.
        val perWeek = if (weeks.isEmpty()) 0.0 else weeks.sum().toDouble() / weeks.size
        InkListRow(
            label = "Consistency",
            value = if (perWeek == 0.0) "\u2014" else "${"%.1f".format(java.util.Locale.US, perWeek)} a week",
            supporting = "sealed days per week, last 12 weeks",
            onClick = onOpen,
        )
        // Twelve flat dashes said nothing; until a week has a trial, say what fills it.
        if (weeks.any { it > 0 }) {
            WeekStrip(weeks, Modifier.padding(horizontal = 16.dp))
        } else {
            Text(
                "Each week fills in here as you seal trials.",
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

/** Twelve weeks, oldest to newest: bar height is that week's sealed days out of seven. */
@Composable
private fun WeekStrip(weeks: List<Int>, modifier: Modifier = Modifier) {
    val height = 28.dp
    Row(
        modifier
            .fillMaxWidth()
            .height(height)
            .clearAndSetSemantics {
                contentDescription = "Sealed days per week over the last ${weeks.size} weeks, oldest first: " +
                    weeks.joinToString(", ")
            },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        weeks.forEach { count ->
            Box(
                Modifier
                    .weight(1f)
                    .height(if (count == 0) 3.dp else height * (count.coerceAtMost(7) / 7f))
                    .background(if (count == 0) IronvellumColors.Rune else IronvellumColors.Ink),
            )
        }
    }
}

// ---------------------------------------------------------------- panes

/** Every weigh-in, one line each, newest first. Delete is immediate and the bar offers Undo. */
@Composable
internal fun WeightHistoryPage(
    stats: List<StatEntry>,
    profileHeight: Double?,
    lastDeleted: StatEntry?,
    onDelete: (StatEntry) -> Unit,
    onUndo: () -> Unit,
    onUndoExpired: () -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        PushedHeader("WEIGHT HISTORY", onBack, Modifier.padding(top = 12.dp, start = LedgerSpace.Gutter, end = LedgerSpace.Gutter), backDescription = "Back to Ledger")
        Box(Modifier.weight(1f).padding(horizontal = LedgerSpace.Gutter)) {
            if (stats.isEmpty()) {
                Text(
                    "Nothing logged yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                    modifier = Modifier.padding(top = LedgerSpace.Panel),
                )
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(stats, key = { it.id }) { stat ->
                        HistoryRow(stat, profileHeight, onDelete = { onDelete(stat) })
                        InkDivider()
                    }
                    item { Spacer(Modifier.height(96.dp)) }
                }
            }
        }
        if (lastDeleted != null) {
            UndoBar(
                "Reading deleted",
                onUndo = onUndo,
                modifier = Modifier.padding(horizontal = LedgerSpace.Gutter, vertical = LedgerSpace.Panel),
                onExpired = onUndoExpired,
                key = lastDeleted.id,
            )
        }
    }
    // The offer belongs to this page: leaving it lets the deletion stand, so
    // coming back never shows an Undo for a reading deleted long ago.
    DisposableEffect(Unit) { onDispose(onUndoExpired) }
}

@Composable
private fun HistoryRow(stat: StatEntry, profileHeight: Double?, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = ListRowHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text("${formatBodyValue(stat.weightKg)} kg", style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.Ink)
            Text(
                buildString {
                    append(formatDate(stat.takenAtMs, "d MMM yyyy \u00B7 HH:mm"))
                    stat.bodyFatPct?.let { append(" \u00B7 ${formatBodyValue(it)}% bf") }
                    Ledger.bmiOf(stat, profileHeight)?.let { append(" \u00B7 BMI ${formatBodyValue(it)}") }
                },
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
            )
        }
        // Immediate: the bar under the list offers Undo, which puts the reading back as it was.
        InkIconButton(onClick = onDelete) {
            Icon(Icons.Outlined.Delete, contentDescription = "Delete reading", tint = IronvellumColors.InkMuted)
        }
    }
}
