package com.ironvellum.app.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import com.ironvellum.app.ui.components.InkIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ironvellum.app.domain.WorkoutSession
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.components.LedgerContrast
import com.ironvellum.app.ui.components.LedgerSpace
import com.ironvellum.app.ui.components.rememberZoneId
import com.ironvellum.app.ui.components.PanelLabel
import com.ironvellum.app.ui.components.StatSize
import com.ironvellum.app.ui.components.StatValue
import com.ironvellum.app.ui.components.plural
import com.ironvellum.app.ui.theme.DotShape
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.inkBorder
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/**
 * A day cell is at least this tall: LedgerSpace.Target, so a sealed day is a
 * proper touch target. Seven columns in a phone width leave them under 48dp
 * wide; the height is what can honour the minimum. The cell grows with the
 * font size rather than clipping its date.
 */
private val CellHeight = LedgerSpace.Target

/** A week with no trial to open: no tap targets, so no 48dp to hold. */
private val QuietCellHeight = 36.dp

/**
 * First date the calendar marks a scheduled weekday: the lifter's first
 * workout, or today before there is one. The routine only exists from then
 * on, so earlier dots claimed plans for dates before the app was in use.
 */
internal fun calendarScheduleStart(completedDates: Set<LocalDate>, today: LocalDate): LocalDate =
    completedDates.minOrNull()?.coerceAtMost(today) ?: today

/**
 * The day a sealed trial is filed under: the same rule that builds the
 * Ledger's sealed-day set (finished time, or start time when none).
 */
internal fun trialDay(session: WorkoutSession, zone: ZoneId): LocalDate =
    Instant.ofEpochMilli(session.completedAtMs ?: session.startedAtMs).atZone(zone).toLocalDate()

/** Trial ids per day, oldest first, so the last id of a day is its latest trial. */
internal fun trialsByDay(sessions: List<WorkoutSession>, zone: ZoneId): Map<LocalDate, List<Long>> =
    sessions
        .sortedBy { it.completedAtMs ?: it.startedAtMs }
        .groupBy({ trialDay(it, zone) }, { it.id })

/** Trials sealed in [month], not counting any dated after [today]. Two on one day count twice. */
internal fun trialsInMonth(byDay: Map<LocalDate, List<Long>>, month: YearMonth, today: LocalDate): Int =
    byDay.entries.sumOf { (day, ids) -> if (YearMonth.from(day) == month && day <= today) ids.size else 0 }

/** The month as full weeks beginning on [weekStart]; null pads the days outside it. */
internal fun calendarWeeks(month: YearMonth, weekStart: DayOfWeek): List<List<LocalDate?>> {
    val leading = (month.atDay(1).dayOfWeek.value - weekStart.value + 7) % 7
    val days: List<LocalDate?> = List(leading) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
    return (days + List((7 - days.size % 7) % 7) { null }).chunked(7)
}

/** What a screen reader says for one day. */
internal fun daySpeech(date: String, trials: Int, rite: Boolean, isToday: Boolean, future: Boolean): String =
    buildString {
        append(date)
        when {
            trials == 1 -> append(", trial sealed")
            trials > 1 -> append(", $trials trials sealed")
            rite && future -> append(", rite day")
            rite -> append(", rite day, no trial sealed")
        }
        if (isToday) append(", today")
    }

/**
 * The calendar panel: the month's count and the oath up top, a compact grid
 * below. A day with a sealed trial opens it; every other day is plain.
 */
@Composable
internal fun TrainingCalendar(
    sessions: List<WorkoutSession>,
    completedDates: Set<LocalDate>,
    scheduledDays: Set<Int>,
    month: YearMonth,
    today: LocalDate,
    onMonth: (Int) -> Unit,
    onOpenTrial: (Long) -> Unit,
) {
    val zone = rememberZoneId()
    val locale = LocalConfiguration.current.locales[0]
    val byDay = remember(sessions, zone) { trialsByDay(sessions, zone) }
    val inMonth = remember(byDay, month, today) { trialsInMonth(byDay, month, today) }
    val scheduleStart = remember(completedDates, today) { calendarScheduleStart(completedDates, today) }
    // Monday, as on Today's rail; the locale's Sunday put the two at odds.
    val weekStart = java.time.DayOfWeek.MONDAY
    val weeks = remember(month, weekStart) { calendarWeeks(month, weekStart) }
    val weekdayLabels = remember(weekStart, locale) {
        (0L..6L).map { weekStart.plus(it).getDisplayName(TextStyle.NARROW, locale) }
    }
    val monthName = month.month.getDisplayName(TextStyle.FULL, locale)

    InkPanel(Modifier.fillMaxWidth()) {
        PanelLabel("TRAINING")
        // The oath lives on Today; the month's count is the calendar's own.
        StatValue(inMonth.toString(), size = StatSize.Hero, unit = plural(inMonth, "trial", "trials"))
        Text(
            if (month == YearMonth.from(today)) "sealed this month" else "sealed in $monthName",
            style = MaterialTheme.typography.labelSmall,
            color = IronvellumColors.InkMuted,
        )

        // Month navigation on its own row: the month name never competes with a heading for width.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            InkIconButton(onClick = { onMonth(-1) }) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Previous month",
                    tint = IronvellumColors.SystemGreen,
                )
            }
            Text(
                "$monthName ${month.year}",
                style = MaterialTheme.typography.titleSmall,
                color = IronvellumColors.Ink,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
            )
            // Unbounded, the arrow paged into empty future months forever.
            val canAdvance = month < YearMonth.from(today)
            InkIconButton(onClick = { onMonth(1) }, enabled = canAdvance) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = if (canAdvance) "Next month" else "Next month, already at the current month",
                    tint = if (canAdvance) IronvellumColors.SystemGreen else LedgerContrast.Graphic,
                )
            }
        }

        Row(Modifier.fillMaxWidth()) {
            weekdayLabels.forEach { label ->
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = IronvellumColors.InkMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        weeks.forEach { week ->
            // A week with a trial in it keeps the 48dp target its days open
            // with; a week with none has no controls, so it sits tighter.
            val rowHeight = if (week.any { it != null && byDay[it].orEmpty().isNotEmpty() }) CellHeight else QuietCellHeight
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    Box(Modifier.weight(1f)) {
                        if (date == null) {
                            Spacer(Modifier.height(rowHeight))
                        } else {
                            DayCell(
                                height = rowHeight,
                                date = date,
                                trialIds = byDay[date].orEmpty(),
                                rite = date >= scheduleStart && date.dayOfWeek.value in scheduledDays,
                                today = today,
                                locale = locale,
                                onOpenTrial = onOpenTrial,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(LedgerSpace.Panel))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            LegendItem("sealed") { SealedMark() }
            LegendItem("rite day") { RiteMark() }
            LegendItem("today") {
                Box(Modifier.size(12.dp).inkBorder(IronvellumColors.Ink, DotShape, 1.5.dp))
            }
        }
    }
}

@Composable
private fun DayCell(
    height: androidx.compose.ui.unit.Dp,
    date: LocalDate,
    trialIds: List<Long>,
    rite: Boolean,
    today: LocalDate,
    locale: Locale,
    onOpenTrial: (Long) -> Unit,
) {
    val sealed = trialIds.isNotEmpty()
    val isToday = date == today
    val future = date > today
    val spoken = daySpeech(
        date.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM", locale)),
        trialIds.size, rite, isToday, future,
    )
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = height)
            .clip(MaterialTheme.shapes.extraSmall)
            // A day with a trial opens its latest one; the rest are not controls.
            .then(
                if (sealed) {
                    Modifier.clickable(onClickLabel = "Open trial", role = Role.Button) { onOpenTrial(trialIds.last()) }
                } else {
                    Modifier
                },
            )
            .semantics(mergeDescendants = true) { contentDescription = spoken },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            // sizeIn, not size: at a large font a two-digit date widens the ring instead of overflowing it
            Modifier
                .sizeIn(minWidth = 28.dp, minHeight = 28.dp)
                .inkBorder(IronvellumColors.Ink, DotShape, if (isToday) 1.5.dp else 0.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = when {
                    sealed || isToday -> IronvellumColors.Ink
                    future -> LedgerContrast.FutureText
                    else -> IronvellumColors.InkMuted
                },
            )
        }
        Box(Modifier.height(8.dp), contentAlignment = Alignment.Center) {
            when {
                sealed -> SealedMark()
                rite -> RiteMark()
            }
        }
    }
}

/** A sealed day: a small filled ink dot. */
@Composable
private fun SealedMark() {
    Box(Modifier.size(6.dp).clip(DotShape).background(IronvellumColors.Ink))
}

/** A rite day with nothing sealed on it (yet): the same dot, hollow. */
@Composable
private fun RiteMark() {
    Box(Modifier.size(6.dp).inkBorder(IronvellumColors.InkMuted, DotShape, 1.dp))
}

@Composable
private fun LegendItem(label: String, mark: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        mark()
        Text(label, style = MaterialTheme.typography.labelSmall, color = IronvellumColors.InkMuted)
    }
}
