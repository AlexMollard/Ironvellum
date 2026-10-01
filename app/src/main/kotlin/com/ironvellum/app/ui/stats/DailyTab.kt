package com.ironvellum.app.ui.stats

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.ironvellum.app.ui.components.BarChart
import com.ironvellum.app.ui.components.InkDivider
import com.ironvellum.app.ui.components.InkListRow
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.components.LedgerSpace
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
                PanelLabel("DAILY")
                Spacer(Modifier.height(6.dp))
                Text(
                    "Steps, sleep and resting heart rate show here once Health Connect is linked in Settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                )
                Spacer(Modifier.height(10.dp))
                IronvellumButton(label = "Open Settings", onClick = onOpenSettings, quiet = true)
            }
        }

        val zone = remember { ZoneId.systemDefault() }
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

        // Three numbers for today. Steps trail the watch, so they carry their age.
        val todaySteps = todayRow?.steps?.takeIf { it > 0 }
        val lastNight = todayRow?.sleepMinutes?.takeIf { it > 0 }
        val anyBurn = burns.any { it != null }
        if (hasHealthData) InkPanel(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(LedgerSpace.Panel)) {
                TodayNumber(
                    "STEPS",
                    todaySteps?.let { fmtInt(it) } ?: "\u2014",
                    if (todaySteps != null) stepsAsOfCaption(syncedAtMs, today) ?: "Health Connect" else "not synced today",
                    Modifier.weight(1f),
                )
                TodayNumber(
                    "SLEEP",
                    lastNight?.let { Ledger.sleepText(it) } ?: "\u2014",
                    if (lastNight != null) "last night" else "not synced today",
                    Modifier.weight(1f),
                )
                TodayNumber(
                    "ACTIVE KCAL",
                    todayBurn?.let { fmtInt(it.kcal) } ?: "\u2014",
                    todayBurn?.let { confidenceWord(it.confidence) } ?: "not synced today",
                    Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(4.dp))
            InkDivider()
            InkListRow(
                label = "Resting heart rate",
                value = avgHr?.let { "${it.value.toInt()} bpm" } ?: "\u2014",
                supporting = avgHr?.let { "7-day average \u00B7 ${it.tracked} of ${it.of} days" } ?: "Health Connect has none yet",
            )
        }

        if (hasHealthData || anyBurn) {
            RangeChips(
                options = DailyRange.entries.map { it to it.label },
                selected = range,
                onPick = { onRange(it.ordinal) },
            )
        }

        if (hasHealthData) InkPanel(Modifier.fillMaxWidth()) {
            PanelLabel("STEPS")
            Text(
                avgSteps?.let { "avg ${fmtInt(it.value.toInt())}/day \u00B7 ${it.tracked} of $n days, today excluded" }
                    ?: "no days tracked",
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
            )
            Spacer(Modifier.height(8.dp))
            BarChart(steps, goal = STEP_GOAL.toDouble(), startLabel = startLabel, endLabel = "today")
            val hits = steps.count { (it ?: 0.0) >= STEP_GOAL }
            Text(
                "$hits of $n days hit ${fmtInt(STEP_GOAL)}",
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
            )
        }

        if (hasHealthData) InkPanel(Modifier.fillMaxWidth()) {
            PanelLabel("SLEEP")
            Text(
                avgSleep?.let { "avg ${Ledger.sleepText(it.value.toInt())} \u00B7 ${it.tracked} of $n nights" } ?: "no nights tracked",
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
            )
            Spacer(Modifier.height(8.dp))
            BarChart(sleep, IronvellumColors.SystemGreen, startLabel = startLabel, endLabel = "today")
        }

        if (anyBurn) InkPanel(Modifier.fillMaxWidth()) {
            PanelLabel("ACTIVE KCAL")
            val measured = burns.count { it?.confidence == EnergyConfidence.MEASURED }
            Text(
                "${burns.count { it != null }} of $n days \u00B7 $measured measured, faded bars are estimates",
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
            )
            Spacer(Modifier.height(8.dp))
            BarChart(
                burns.map { it?.kcal?.toDouble() },
                faded = burns.map { it != null && it.confidence != EnergyConfidence.MEASURED },
                startLabel = startLabel,
                endLabel = "today",
            )
        }

        if (hasHealthData || anyBurn) InkPanel(Modifier.fillMaxWidth()) {
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

@Composable
private fun TodayNumber(label: String, value: String, caption: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        PanelLabel(label)
        StatValue(value, size = StatSize.Inline)
        Text(caption, style = MaterialTheme.typography.labelSmall, color = IronvellumColors.InkMuted)
    }
}

/** Where the estimates come from, behind a tap instead of repeated in every panel. */
@Composable
private fun EnergyMethodDialog(stats: List<StatEntry>, onDismiss: () -> Unit) {
    val bf = Ledger.latestWithBodyFat(stats)
    val resting = Energy.restingKcalPerDay(bf?.weightKg, bf?.bodyFatPct)
    AlertDialog(
        shape = MaterialTheme.shapes.medium,
        containerColor = IronvellumColors.Vault,
        onDismissRequest = onDismiss,
        title = { Text("How energy is estimated", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "A measured active-calorie figure from Health Connect always wins, and an estimate is never added on top of it.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Estimates use net MET: the Compendium MET minus 1, because Health Connect's active calories leave resting burn out. " +
                        "Walking is costed at 2.5 net MET, lifting by session time.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    if (resting != null) {
                        "Resting burn (Katch-McArdle): ${fmtInt(resting.kcal)} kcal/day, ${resting.basis}."
                    } else {
                        "Log body fat to see a resting burn (Katch-McArdle)."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = { IronvellumButton(label = "Close", onClick = onDismiss, quiet = true) },
    )
}

private fun confidenceWord(confidence: EnergyConfidence): String = when (confidence) {
    EnergyConfidence.MEASURED -> "measured"
    EnergyConfidence.ESTIMATED -> "estimated"
    EnergyConfidence.COARSE -> "rough estimate"
}

private fun shortDate(date: LocalDate): String =
    date.format(java.time.format.DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))

private fun fmtInt(v: Int): String = String.format(Locale.getDefault(), "%,d", v)
