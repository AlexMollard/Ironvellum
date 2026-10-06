package com.ironvellum.app.ui.stats

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.semantics.heading
import com.ironvellum.app.ui.components.NavChip
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
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.components.LedgerSpace
import com.ironvellum.app.ui.components.PanelLabel
import com.ironvellum.app.ui.components.RangeChips
import com.ironvellum.app.ui.components.StatSize
import com.ironvellum.app.ui.components.StatValue
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
                if (latest != null) {
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

        InkPanel(Modifier.fillMaxWidth()) {
            ConsistencyRow(weeks, onClick = onOpenTraining)
        }

        InkPanel(Modifier.fillMaxWidth()) {
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

/** A tappable BMI / FFMI figure: label, value, one muted line. Opens the band dialog. */
@Composable
private fun StatChip(label: String, value: String, hint: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = MaterialTheme.shapes.extraSmall
    Column(
        modifier
            .clip(shape)
            .inkBorder(IronvellumColors.Rune, shape, 1.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = LedgerSpace.Target)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        PanelLabel(label)
        StatValue(value, size = StatSize.Inline)
        Text(
            hint,
            style = MaterialTheme.typography.bodySmall,
            color = IronvellumColors.InkMuted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ConsistencyRow(weeks: List<Int>, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(bottom = 12.dp),
    ) {
        // The weekly average, not this month's count: Training's calendar
        // already leads with that number.
        val perWeek = if (weeks.isEmpty()) 0.0 else weeks.sum().toDouble() / weeks.size
        InkListRow(
            label = "Consistency",
            value = if (perWeek == 0.0) "\u2014" else "${"%.1f".format(java.util.Locale.US, perWeek)} a week",
            supporting = "sealed days per week, last 12 weeks",
            onClick = null,
        )
        // Twelve flat dashes said nothing; until a week has a trial, say what fills it.
        if (weeks.any { it > 0 }) {
            WeekStrip(weeks)
        } else {
            Text(
                "Each week fills in here as you seal trials.",
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
            )
        }
    }
}

/** Twelve weeks, oldest to newest: bar height is that week's sealed days out of seven. */
@Composable
private fun WeekStrip(weeks: List<Int>) {
    val height = 28.dp
    Row(
        Modifier
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
                    .background(if (count == 0) IronvellumColors.Rune else IronvellumColors.Emerald),
            )
        }
    }
}

// ---------------------------------------------------------------- panes

/** The standard pane bar: a 48dp back target and the title. */
@Composable
internal fun LedgerTopBar(title: String, onBack: () -> Unit, backDescription: String = "Back to Ledger") {
    // Title left, BACK right: the header every pushed screen uses (PushedHeader).
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp, start = LedgerSpace.Gutter, end = LedgerSpace.Gutter),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            fontFamily = ChakraPetch,
            color = IronvellumColors.SystemGreen,
            letterSpacing = IronvellumTracking.ScreenTitle,
            modifier = Modifier.semantics { heading() },
        )
        NavChip(
            "BACK",
            Icons.AutoMirrored.Filled.ArrowBack,
            onClick = onBack,
            modifier = Modifier.semantics { contentDescription = backDescription },
        )
    }
}

/** Every weigh-in, one line each, newest first. Delete is armed per row, and Undo outlives it. */
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
        LedgerTopBar("WEIGHT HISTORY", onBack)
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
            UndoBar("Reading deleted", onUndo, onUndoExpired, key = lastDeleted.id)
        }
    }
    // The offer belongs to this page: leaving it lets the deletion stand, so
    // coming back never shows an Undo for a reading deleted long ago.
    DisposableEffect(Unit) { onDispose(onUndoExpired) }
}

@Composable
private fun HistoryRow(stat: StatEntry, profileHeight: Double?, onDelete: () -> Unit) {
    // Keyed by the reading, so arming one row can never arm its neighbour.
    var armed by remember(stat.id) { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().heightIn(min = LedgerSpace.Target),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(
                buildString {
                    append("${formatBodyValue(stat.weightKg)} kg")
                    stat.bodyFatPct?.let { append(" \u00B7 ${formatBodyValue(it)}% bf") }
                    Ledger.bmiOf(stat, profileHeight)?.let { append(" \u00B7 BMI ${formatBodyValue(it)}") }
                },
                style = MaterialTheme.typography.titleSmall,
                fontFamily = ChakraPetch,
                color = IronvellumColors.Ink,
            )
            Text(
                formatDate(stat.takenAtMs, "d MMM yyyy \u00B7 HH:mm"),
                style = MaterialTheme.typography.labelSmall,
                color = IronvellumColors.InkMuted,
            )
        }
        if (armed) {
            // One tap on a trash icon used to erase a weigh-in that feeds the
            // strength score; arm first, name the cost.
            ArmedChoice("KEEP", IronvellumColors.InkMuted) { armed = false }
            ArmedChoice("DELETE", IronvellumColors.DangerRed) {
                armed = false
                onDelete()
            }
        } else {
            IconButton(onClick = { armed = true }) {
                Icon(Icons.Outlined.Delete, contentDescription = "Delete reading", tint = IronvellumColors.InkMuted)
            }
        }
    }
}

@Composable
internal fun ArmedChoice(label: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        fontFamily = ChakraPetch,
        color = color,
        letterSpacing = IronvellumTracking.InlineLabel,
        modifier = Modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = LedgerSpace.Target)
            .padding(horizontal = 10.dp)
            .wrapContentHeight(),
    )
}

/** A bottom strip that offers Undo for a few seconds, then lets the deletion stand. */
@Composable
internal fun UndoBar(message: String, onUndo: () -> Unit, onExpired: () -> Unit, key: Any) {
    // Longer when the user's accessibility settings ask for more time to act.
    val window = undoWindowMs(androidx.compose.ui.platform.LocalContext.current)
    LaunchedEffect(key) {
        kotlinx.coroutines.delay(window)
        onExpired()
    }
    InkPanel(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = LedgerSpace.Gutter, vertical = LedgerSpace.Panel)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = IronvellumColors.Ink,
                modifier = Modifier.weight(1f),
            )
            Text(
                "UNDO",
                style = MaterialTheme.typography.labelLarge,
                fontFamily = ChakraPetch,
                color = IronvellumColors.SystemGreen,
                letterSpacing = IronvellumTracking.InlineLabel,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.extraSmall)
                    .clickable(role = Role.Button, onClick = onUndo)
                    .heightIn(min = LedgerSpace.Target)
                    .padding(horizontal = 12.dp)
                    .wrapContentHeight(),
            )
        }
    }
}

private const val UNDO_WINDOW_MS = 6_000L

/** [UNDO_WINDOW_MS], stretched to the system's recommended timeout when accessibility services are on. */
private fun undoWindowMs(context: android.content.Context): Long {
    val manager = context.getSystemService(android.view.accessibility.AccessibilityManager::class.java)
        ?: return UNDO_WINDOW_MS
    val flags = android.view.accessibility.AccessibilityManager.FLAG_CONTENT_CONTROLS or
        android.view.accessibility.AccessibilityManager.FLAG_CONTENT_TEXT
    return manager.getRecommendedTimeoutMillis(UNDO_WINDOW_MS.toInt(), flags).toLong().coerceAtLeast(UNDO_WINDOW_MS)
}
