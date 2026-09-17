package com.monarch.app.ui.stats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
import com.monarch.app.domain.Measurements
import com.monarch.app.domain.MeasurementSite
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MeasurementDetailUi(
    val entries: List<MeasurementEntry> = emptyList(),
)

class MeasurementDetailViewModel(
    private val repo: Repository,
    private val site: MeasurementSite,
) : ViewModel() {
    val ui: StateFlow<MeasurementDetailUi> = repo.observeMeasurements()
        .map { entries ->
            MeasurementDetailUi(entries = Measurements.history(entries, site))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MeasurementDetailUi())

    fun log(valueCm: Double) {
        viewModelScope.launch { repo.logMeasurement(site, valueCm) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repo.deleteMeasurement(id) }
    }

}

/** Metric cm, one decimal, sane human range so a typo cannot poison the chart. */
private val CM_MIN = 10.0
private val CM_MAX = 250.0

/** Recent readings shown; the chart and full record live across the account's life. */
private const val READING_ROWS = 20

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
 * Full record for one measurement site: history chart, logging, and
 * per-reading delete. Everything here is device-local — the cloud schema has
 * no table for this data on purpose.
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
    // Saveable: rotation mid-entry used to clear the half-typed reading.
    var readingInput by rememberSaveable(site) { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
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
                TrendChart(series, MonarchColors.SystemGreen, fromZero = false)
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

        SectionHeader("Log a reading")
        SystemWindow(Modifier.fillMaxWidth()) {
            // Technique first: the number is only worth comparing if the tape
            // lands in the same place every time.
            Text(
                "HOW TO MEASURE",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = ChakraPetch,
                color = MonarchColors.SovereignGold,
                letterSpacing = MonarchTracking.InlineLabel,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                site.howTo,
                style = MaterialTheme.typography.bodySmall,
                color = MonarchColors.InkMuted,
            )
            Spacer(Modifier.height(12.dp))
            val parsedNow = parseCm(readingInput)
            OutlinedTextField(
                shape = MaterialTheme.shapes.small,
                value = readingInput,
                onValueChange = { readingInput = sanitizeCm(it) },
                label = { Text("Circumference (cm)") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done,
                ),
                // The submit button can sit under the keyboard, so the IME's own
                // Done key logs the reading rather than stranding the entry.
                keyboardActions = KeyboardActions(
                    onDone = {
                        parsedNow?.let {
                            viewModel.log(Math.round(it * 10.0) / 10.0)
                            readingInput = ""
                        }
                    },
                ),
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
            // Bounded render inside verticalScroll: this composes every row for
            // the life of the account unless capped.
            ui.entries.sortedByDescending { it.takenAtMs }.take(READING_ROWS).forEach { entry ->
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

