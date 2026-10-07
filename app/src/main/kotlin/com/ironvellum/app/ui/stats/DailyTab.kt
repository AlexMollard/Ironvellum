package com.ironvellum.app.ui.stats

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ironvellum.app.domain.Energy
import com.ironvellum.app.domain.EnergyConfidence
import com.ironvellum.app.domain.Exercise
import com.ironvellum.app.domain.HealthDay
import com.ironvellum.app.domain.Ledger
import com.ironvellum.app.domain.SessionSet
import com.ironvellum.app.domain.StatEntry
import com.ironvellum.app.domain.STEP_GOAL
import com.ironvellum.app.domain.WorkoutSession
import com.ironvellum.app.domain.fmt
import com.ironvellum.app.ui.components.BarChart
import com.ironvellum.app.ui.components.InfoAction
import com.ironvellum.app.ui.components.InfoSheet
import com.ironvellum.app.ui.components.InfoSheetSize
import com.ironvellum.app.ui.components.InkDivider
import com.ironvellum.app.ui.components.InkListRow
import com.ironvellum.app.ui.components.InkRowPanel
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.components.LedgerSpace
import com.ironvellum.app.ui.components.rememberZoneId
import com.ironvellum.app.ui.components.PanelLabel
import com.ironvellum.app.ui.components.RangeChips
import com.ironvellum.app.ui.components.StatSize
import com.ironvellum.app.ui.components.StatValue
import com.ironvellum.app.ui.dashboard.stepsAsOfCaption
import com.ironvellum.app.ui.theme.IronvellumColors
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/** The chart windows DAILY offers. One choice drives every chart on the tab. */
internal enum class DailyRange(val label: String, val days: Int) { D7("7D", 7), D14("14D", 14), D30("30D", 30) }

/**
 * DAILY, slimmed to what Health Connect and the Dashboard do not already say:
 * three numbers for today, the resting heart rate, then one bar chart each for
 * steps, sleep and active energy. Every window is calendar dates ending today,
 * and an empty day is an empty slot.
 */
@Composable
internal fun ActivityTab(
    days: List<HealthDay>,
    today: LocalDate,
    syncedAtMs: Long?,
    profileHeight: Double?,
    stats: List<StatEntry>,
    sessions: List<WorkoutSession>,
    sessionSets: Map<Long, List<SessionSet>>,
    exercises: Map<Long, Exercise>,
    onOpenSettings: () -> Unit,
    scroll: ScrollState,
    rangeIndex: Int,
    onRange: (Int) -> Unit,
) {
    var showMethod by rememberSaveable { mutableStateOf(false) }
    val range = DailyRange.entries[rangeIndex]

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = LedgerSpace.Gutter),
        verticalArrangement = Arrangement.spacedBy(LedgerSpace.Panel),
    ) {
        Spacer(Modifier.height(LedgerSpace.Panel))
        val hasHealthData = days.isNotEmpty()
        // Only the Health Connect blocks wait for Health Connect: trials still
        // have a burn estimate without it.
        if (!hasHealthData) {
            InkPanel(Modifier.fillMaxWidth()) {
                Text(
                    "Link Health Connect and your days fill in here.",
                    style = MaterialTheme.typography.titleSmall,
                    color = IronvellumColors.Ink,
                )
                Spacer(Modifier.height(LedgerSpace.Panel))
                // A ghost of what the tab will hold, so the empty tab says what
                // it is for instead of being one sentence on a black page.
                Row(horizontalArrangement = Arrangement.spacedBy(LedgerSpace.Panel)) {
                    listOf("Steps" to listOf(5, 7, 4, 8, 6, 9, 7), "Sleep" to listOf(7, 6, 8, 7, 5, 8, 7), "Resting HR" to listOf(6, 6, 5, 6, 5, 5, 4))
                        .forEach { (label, bars) -> GhostChart(label, bars, Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(LedgerSpace.Panel))
                Text(
                    "Steps, sleep and resting heart rate, read from Health Connect on this phone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                )
                Spacer(Modifier.height(10.dp))
                IronvellumButton(label = "Link Health Connect", onClick = onOpenSettings, modifier = Modifier.fillMaxWidth())
            }
        }

        val zone = rememberZoneId()
        val latest = stats.firstOrNull()
        val weightKg = latest?.weightKg
        val heightCm = latest?.let { Ledger.heightFor(it, profileHeight) } ?: profileHeight?.takeIf { it > 0.0 }
        val n = range.days
        // Derived once per data change, never per frame.
        val todayRow = Ledger.todayRow(days, today)
        val steps = remember(days, today, n) { Ledger.slots(days, today, n) { it.steps.toDouble() } }
        val sleep = remember(days, today, n) { Ledger.slots(days, today, n) { it.sleepMinutes.toDouble() } }
        val burns = remember(days, today, n, sessions, sessionSets, exercises, weightKg, heightCm) {
            Ledger.burnSlots(days, today, n, sessions, sessionSets, exercises, weightKg, heightCm, zone)
        }
        val todayBurn = remember(days, today, sessions, sessionSets, exercises, weightKg, heightCm) {
            Ledger.burnSlots(days, today, 1, sessions, sessionSets, exercises, weightKg, heightCm, zone).single()
        }
        val avgSteps = remember(days, today, n) { Ledger.average(days, today, n, includeToday = false) { it.steps.toDouble() } }
        val avgSleep = remember(days, today, n) { Ledger.average(days, today, n, includeToday = true) { it.sleepMinutes.toDouble() } }
        val avgHr = remember(days, today) {
            Ledger.average(days, today, 7, includeToday = true) { it.restingHr?.toDouble() }
        }
        val startLabel = shortDate(today.minusDays(n - 1L))
        // Slot i is the day i places from the oldest of the n shown.
        val slotDate: (Int) -> String = { shortDate(today.minusDays(n - 1L - it)) }

        // Today's figures sit in each chart's header. Steps trail the watch, so theirs carries its age.
        val todaySteps = todayRow?.steps?.takeIf { it > 0 }
        val lastNight = todayRow?.sleepMinutes?.takeIf { it > 0 }
        val anyBurn = burns.any { it != null }
        // One range switch for every chart, in the header of the first card shown.
        val firstCard = when {
            hasHealthData -> DailyCard.STEPS
            anyBurn -> DailyCard.KCAL
            else -> null
        }
        val rangeSwitch: @Composable () -> Unit = {
            RangeChips(
                options = DailyRange.entries.map { it to it.label },
                selected = range,
                onPick = { onRange(it.ordinal) },
            )
        }

        if (hasHealthData) InkPanel(Modifier.fillMaxWidth()) {
            ChartHeader("STEPS", rangeSwitch.takeIf { firstCard == DailyCard.STEPS })
            TodayFigure(
                todaySteps?.let { fmtInt(it) } ?: "\u2014",
                if (todaySteps != null) stepsAsOfCaption(syncedAtMs, today) ?: "Health Connect" else "not synced today",
            )
            Text(
                avgSteps?.let { "avg ${fmtInt(it.value.toInt())}/day \u00B7 ${it.tracked} of $n days, today excluded" }
                    ?: "no days tracked",
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
            )
            Spacer(Modifier.height(8.dp))
            BarChart(
                steps,
                IronvellumColors.Ink,
                goal = STEP_GOAL.toDouble(),
                lastColor = IronvellumColors.Emerald,
                emptyColor = IronvellumColors.Rune,
                startLabel = startLabel,
                endLabel = "today",
                valueText = { "%,d steps".fmt(Math.round(it)) },
                dateText = slotDate,
            )
            val hits = steps.count { (it ?: 0.0) >= STEP_GOAL }
            Text(
                "$hits of $n days hit ${fmtInt(STEP_GOAL)}",
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
            )
        }

        if (hasHealthData) InkPanel(Modifier.fillMaxWidth()) {
            ChartHeader("SLEEP", null)
            TodayFigure(lastNight?.let { Ledger.sleepText(it) } ?: "\u2014", if (lastNight != null) "last night" else "not synced today")
            Text(
                avgSleep?.let { "avg ${Ledger.sleepText(it.value.toInt())} \u00B7 ${it.tracked} of $n nights" } ?: "no nights tracked",
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
            )
            Spacer(Modifier.height(8.dp))
            BarChart(
                sleep,
                IronvellumColors.Ink,
                lastColor = IronvellumColors.Emerald,
                emptyColor = IronvellumColors.Rune,
                startLabel = startLabel,
                endLabel = "today",
                valueText = { Ledger.sleepText(it.toInt()) },
                dateText = slotDate,
            )
        }

        if (anyBurn) InkPanel(Modifier.fillMaxWidth()) {
            ChartHeader("ACTIVE KCAL", rangeSwitch.takeIf { firstCard == DailyCard.KCAL })
            TodayFigure(
                todayBurn?.let { fmtInt(it.kcal) } ?: "\u2014",
                todayBurn?.let { confidenceWord(it.confidence) } ?: "not synced today",
            )
            val measured = burns.count { it?.confidence == EnergyConfidence.MEASURED }
            Text(
                "${burns.count { it != null }} of $n days \u00B7 $measured measured, faded bars are estimates",
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
            )
            Spacer(Modifier.height(8.dp))
            BarChart(
                burns.map { it?.kcal?.toDouble() },
                IronvellumColors.Ink,
                faded = burns.map { it != null && it.confidence != EnergyConfidence.MEASURED },
                lastColor = IronvellumColors.Emerald,
                emptyColor = IronvellumColors.Rune,
                startLabel = startLabel,
                endLabel = "today",
                valueText = { "%,d kcal".fmt(Math.round(it)) },
                dateText = slotDate,
            )
        }

        if (hasHealthData || anyBurn) InkRowPanel(Modifier.fillMaxWidth()) {
            if (hasHealthData) {
                InkListRow(
                    label = "Resting heart rate",
                    value = avgHr?.let { "${it.value.toInt()} bpm" } ?: "\u2014",
                    supporting = avgHr?.let { "7-day average \u00B7 ${it.tracked} of ${it.of} days" } ?: "Health Connect has none yet",
                )
                InkDivider()
            }
            InkListRow(
                label = "How these are estimated",
                value = null,
                supporting = "net MET, Katch-McArdle, measured wins",
                onClick = { showMethod = true },
            )
        }
        Spacer(Modifier.height(LedgerSpace.Section))

        if (showMethod) {
            EnergyMethodDialog(stats, onDismiss = { showMethod = false })
        }
    }
}

private enum class DailyCard { STEPS, KCAL }

/** A chart card's one caps label, with the range switch on its right when this is the first card. */
@Composable
private fun ChartHeader(label: String, trailing: (@Composable () -> Unit)?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        PanelLabel(label, Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** Today's figure with its caption, folded into the chart's header. */
@Composable
private fun TodayFigure(value: String, caption: String) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatValue(value, size = StatSize.Inline)
        Text(
            caption,
            style = MaterialTheme.typography.labelSmall,
            color = IronvellumColors.InkMuted,
            modifier = Modifier.padding(bottom = 2.dp),
        )
    }
}

/** Where the estimates come from, behind a tap instead of repeated in every panel. */
@Composable
private fun EnergyMethodDialog(stats: List<StatEntry>, onDismiss: () -> Unit) {
    val bf = Ledger.latestWithBodyFat(stats)
    val resting = Energy.restingKcalPerDay(bf?.weightKg, bf?.bodyFatPct)
    InfoSheet(
        title = "How energy is estimated",
        onDismiss = onDismiss,
        size = InfoSheetSize.Compact,
        actions = listOf(InfoAction("Close", onDismiss, quiet = true)),
    ) {
        text(
            "MEASURED FIRST",
            "A measured active-calorie figure from Health Connect always wins, and an estimate is never added on top of it.",
        )
        text(
            "ESTIMATES",
            "Estimates use net MET: the Compendium MET minus 1, because Health Connect's active calories leave resting burn out. " +
                "Walking is costed at 2.5 net MET, lifting by trial time.",
        )
        text(
            "RESTING BURN",
            if (resting != null) {
                "Katch-McArdle: ${fmtInt(resting.kcal)} kcal/day, ${resting.basis}."
            } else {
                "Log body fat to see a resting burn (Katch-McArdle)."
            },
        )
    }
}

private fun confidenceWord(confidence: EnergyConfidence): String = when (confidence) {
    EnergyConfidence.MEASURED -> "measured"
    EnergyConfidence.ESTIMATED -> "estimated"
    EnergyConfidence.COARSE -> "rough estimate"
}

private fun shortDate(date: LocalDate): String =
    date.format(java.time.format.DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))

private fun fmtInt(v: Int): String = "%,d".fmt(v)

/** A faint chart with no data in it: the shape of what linking will show. */
@Composable
private fun GhostChart(label: String, bars: List<Int>, modifier: Modifier) {
    Column(
        modifier.clearAndSetSemantics { },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = IronvellumColors.InkMuted)
        Row(
            Modifier.fillMaxWidth().height(36.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            bars.forEach { h ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(36.dp * (h / 10f))
                        .background(IronvellumColors.Rune.copy(alpha = 0.7f)),
                )
            }
        }
    }
}
