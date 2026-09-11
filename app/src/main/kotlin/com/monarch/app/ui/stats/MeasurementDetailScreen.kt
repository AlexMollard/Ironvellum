package com.monarch.app.ui.stats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.monarch.app.data.Repository
import com.monarch.app.domain.MeasurementEntry
import com.monarch.app.domain.MeasurementGoal
import com.monarch.app.domain.MeasurementSite
import com.monarch.app.domain.Measurements
import com.monarch.app.ui.components.MonarchButton
import com.monarch.app.ui.components.SectionHeader
import com.monarch.app.ui.components.SystemWindow
import com.monarch.app.ui.components.TrendChart
import com.monarch.app.ui.components.formatDate
import com.monarch.app.ui.monarchRepository
import com.monarch.app.ui.theme.ChakraPetch
import com.monarch.app.ui.theme.MonarchColors
import com.monarch.app.ui.theme.MonarchTracking
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MeasurementDetailUi(
    val entries: List<MeasurementEntry> = emptyList(),
    val goal: MeasurementGoal? = null,
)

class MeasurementDetailViewModel(
    private val repo: Repository,
    private val site: MeasurementSite,
) : ViewModel() {
    val ui: StateFlow<MeasurementDetailUi> = combine(
        repo.observeMeasurements(),
        repo.observeMeasurementGoals(),
    ) { entries, goals ->
        MeasurementDetailUi(
            entries = Measurements.history(entries, site),
            goal = goals.firstOrNull { it.site == site },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MeasurementDetailUi())

    fun log(valueCm: Double) {
        viewModelScope.launch { repo.logMeasurement(site, valueCm) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repo.deleteMeasurement(id) }
    }

    fun setGoal(targetCm: Double) {
        viewModelScope.launch { repo.setMeasurementGoal(site, targetCm) }
    }

    fun clearGoal() {
        viewModelScope.launch { repo.clearMeasurementGoal(site) }
    }
}

/** Metric cm, one decimal, sane human range so a typo cannot poison the chart. */
private val CM_MIN = 10.0
private val CM_MAX = 250.0

/** Strip anything that is not a digit or a single decimal point. */
private fun sanitizeCm(raw: String): String = buildString {
    var dotSeen = false
    for (c in raw) {
        when {
            c.isDigit() -> append(c)
            c == '.' && !dotSeen && isNotEmpty() -> {
                append(c)
                dotSeen = true
            }
        }
    }
}.take(6)

private fun parseCm(raw: String): Double? =
    raw.toDoubleOrNull()?.takeIf { it in CM_MIN..CM_MAX }

/**
 * Full record for one measurement site: history chart, goal progress with its
 * baseline, logging, goal editing, and per-reading delete. Everything here is
 * device-local — the cloud schema has no table for this data on purpose.
 */
@Composable
fun MeasurementDetailScreen(
    site: MeasurementSite,
    onBack: () -> Unit,
    viewModel: MeasurementDetailViewModel = viewModel(
        key = "measurement_${site.name}",
        factory = viewModelFactory { initializer { MeasurementDetailViewModel(monarchRepository(), site) } },
    ),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var readingInput by remember(site) { mutableStateOf("") }
    var goalInput by remember(site) { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "MEASUREMENT · ${site.label.uppercase()}",
                style = MaterialTheme.typography.labelLarge,
                fontFamily = ChakraPetch,
                color = MonarchColors.SystemGreen,
                letterSpacing = MonarchTracking.ScreenTitle,
            )
            Text(
                "BACK",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = ChakraPetch,
                color = MonarchColors.InkMuted,
                letterSpacing = MonarchTracking.InlineLabel,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.extraSmall)
                    .clickable { onBack() }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        Spacer(Modifier.height(12.dp))

        // The privacy promise, stated where the data lives.
        SystemWindow(Modifier.fillMaxWidth(), accent = MonarchColors.Emerald) {
            Text(
                "SEALED TO THIS DEVICE",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = ChakraPetch,
                color = MonarchColors.SystemGreen,
                letterSpacing = MonarchTracking.InlineLabel,
            )
            Text(
                "Body measurements never leave your phone. They are excluded from " +
                    "cloud sync by design — the JSON archive on this device is the only copy.",
                style = MaterialTheme.typography.bodySmall,
                color = MonarchColors.InkMuted,
            )
        }

        SectionHeader("History")
        SystemWindow(Modifier.fillMaxWidth()) {
            val series = ui.entries.sortedBy { it.takenAtMs }.map { it.valueCm }
            if (series.size >= 2) {
                // fromZero = false: circumferences live in a narrow band and a
                // zero-based axis would flatten the line into nothing.
                TrendChart(series, MonarchColors.SystemGreen, goal = ui.goal?.targetCm, fromZero = false)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("min ${"%.1f".format(series.min())} cm", style = MaterialTheme.typography.labelSmall, color = MonarchColors.InkMuted)
                    Text("max ${"%.1f".format(series.max())} cm", style = MaterialTheme.typography.labelSmall, color = MonarchColors.InkMuted)
                }
            } else {
                Text(
                    "Two readings unlock the line.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MonarchColors.InkMuted,
                )
            }
        }

        SectionHeader("Goal")
        SystemWindow(Modifier.fillMaxWidth(), accent = MonarchColors.SovereignGold) {
            val goal = ui.goal
            if (goal == null) {
                Text(
                    "No goal set for ${site.label.lowercase()}. Set a target and the rail " +
                        "will track your progress toward it — growing or shrinking, whichever the target demands.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MonarchColors.InkMuted,
                )
            } else {
                val progress = Measurements.progress(goal, ui.entries)
                GoalRow("TARGET", "%.1f cm".format(goal.targetCm))
                GoalRow("START BASELINE", "%.1f cm · ${formatDate(goal.setAtMs, "d MMM yyyy")}".format(goal.startCm))
                if (progress.achieved) {
                    GoalRow("STATE", "ACHIEVED · ${goal.achievedAtMs?.let { formatDate(it, "d MMM yyyy") } ?: ""}", MonarchColors.SovereignGold)
                } else {
                    GoalRow(
                        "REMAINING",
                        "%.1f cm to go · ${"%.0f".format(progress.fraction * 100)}% of the way".format(progress.remainingCm),
                    )
                }
            }
        }

        SectionHeader("Log a reading")
        SystemWindow(Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = readingInput,
                onValueChange = { readingInput = sanitizeCm(it) },
                label = { Text("Circumference (cm)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                supportingText = {
                    Text(
                        "Metric cm, one decimal · ${CM_MIN.toInt()}–${CM_MAX.toInt()} cm",
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            val parsed = parseCm(readingInput)
            MonarchButton(
                "Log reading",
                onClick = {
                    parsed?.let {
                        viewModel.log(Math.round(it * 10.0) / 10.0)
                        readingInput = ""
                    }
                },
                enabled = parsed != null,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        SectionHeader("Goal editor")
        SystemWindow(Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = goalInput,
                onValueChange = { goalInput = sanitizeCm(it) },
                label = { Text("Target (cm)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            val target = parseCm(goalInput)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MonarchButton(
                    "Set goal",
                    onClick = {
                        target?.let {
                            viewModel.setGoal(Math.round(it * 10.0) / 10.0)
                            goalInput = ""
                        }
                    },
                    enabled = target != null,
                    modifier = Modifier.weight(1f),
                )
                if (ui.goal != null) {
                    MonarchButton(
                        "Clear goal",
                        onClick = { viewModel.clearGoal() },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Direction is never asked for — the goal knows whether this site grows or shrinks.",
                style = MaterialTheme.typography.labelSmall,
                color = MonarchColors.InkMuted,
            )
        }

        SectionHeader("Readings")
        if (ui.entries.isEmpty()) {
            SystemWindow(Modifier.fillMaxWidth()) {
                Text(
                    "No readings yet — take the first measurement.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MonarchColors.InkMuted,
                )
            }
        } else {
            ui.entries.sortedByDescending { it.takenAtMs }.forEach { entry ->
                SystemWindow(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                "${"%.1f".format(entry.valueCm)} cm",
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = ChakraPetch,
                                fontWeight = FontWeight.Bold,
                                color = MonarchColors.Ink,
                            )
                            Text(
                                formatDate(entry.takenAtMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = MonarchColors.InkMuted,
                            )
                        }
                        IconButton(onClick = { viewModel.delete(entry.id) }) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = "Delete reading",
                                tint = MonarchColors.InkMuted,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(96.dp))
    }
}

@Composable
private fun GoalRow(label: String, value: String, color: androidx.compose.ui.graphics.Color = MonarchColors.Ink) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            color = MonarchColors.InkMuted,
            letterSpacing = MonarchTracking.InlineLabel,
        )
        Text(
            value,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = ChakraPetch,
            color = color,
        )
    }
}
