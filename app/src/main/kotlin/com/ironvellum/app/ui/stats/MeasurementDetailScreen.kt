package com.ironvellum.app.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ironvellum.app.data.Repository
import com.ironvellum.app.domain.Ledger
import com.ironvellum.app.domain.MeasurementEntry
import com.ironvellum.app.domain.Measurements
import com.ironvellum.app.domain.MeasurementSite
import com.ironvellum.app.ui.components.InkDivider
import com.ironvellum.app.ui.components.InkListRow
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.components.LedgerSpace
import com.ironvellum.app.ui.components.PanelLabel
import com.ironvellum.app.ui.components.StatSize
import com.ironvellum.app.ui.components.StatValue
import com.ironvellum.app.ui.components.TrendChart
import com.ironvellum.app.ui.components.formatDate
import com.ironvellum.app.ui.components.plural
import com.ironvellum.app.ui.ironvellumRepository
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.IronvellumTracking
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId

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

/** Readings shown before "Show all"; the chart carries every one. */
private const val RECENT_ROWS = 5

/** Keep digits and one point; a decimal comma from the keyboard is read as the point. */
private fun sanitizeCm(raw: String): String = buildString {
    var dotSeen = false
    for (c in raw) {
        when {
            c.isDigit() -> append(c)
            (c == '.' || c == ',') && !dotSeen && isNotEmpty() -> {
                append('.')
                dotSeen = true
            }
        }
    }
}.take(6)

private fun parseCm(raw: String): Double? =
    Ledger.parseDecimal(raw)?.takeIf { it in CM_MIN..CM_MAX }

/**
 * One tape site: its latest value and trend first, then the readings, then
 * logging. Everything here is device-local - the cloud schema has no table
 * for this data on purpose; the tape pane carries the one lock caption.
 */
@Composable
fun MeasurementDetailScreen(
    site: MeasurementSite,
    onBack: () -> Unit,
    viewModel: MeasurementDetailViewModel = viewModel(
        key = "measurement_${site.name}",
        factory = viewModelFactory { initializer { MeasurementDetailViewModel(ironvellumRepository(), site) } },
    ),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    // Saveable: rotation mid-entry used to clear the half-typed reading.
    var readingInput by rememberSaveable(site) { mutableStateOf("") }
    var showAll by rememberSaveable(site) { mutableStateOf(false) }
    val zone = remember { ZoneId.systemDefault() }
    val newestFirst = remember(ui.entries) { ui.entries.sortedByDescending { it.takenAtMs } }
    val oldestFirst = remember(newestFirst) { newestFirst.reversed() }
    val latest = newestFirst.firstOrNull()
    val delta = remember(ui.entries) { Measurements.deltaCm(ui.entries, site, days = 30) }

    Column(Modifier.fillMaxSize().imePadding()) {
        LedgerTopBar(site.label.uppercase(), onBack, backDescription = "Back")
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LedgerSpace.Gutter),
            verticalArrangement = Arrangement.spacedBy(LedgerSpace.Panel),
        ) {
            Spacer(Modifier.height(LedgerSpace.Panel))

            InkPanel(Modifier.fillMaxWidth()) {
                PanelLabel("LATEST")
                if (latest == null) {
                    StatValue("\u2014", size = StatSize.Hero)
                    Text(
                        "This page is blank. Take the first reading below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = IronvellumColors.InkMuted,
                    )
                } else {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        StatValue("%.1f".format(latest.valueCm), size = StatSize.Hero, unit = "cm")
                        delta?.let {
                            Text(
                                "${Ledger.signed(it, "cm")} \u00B7 30D",
                                style = MaterialTheme.typography.titleSmall,
                                fontFamily = ChakraPetch,
                                color = IronvellumColors.InkMuted,
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                        }
                    }
                    Text(
                        "Taken ${formatDate(latest.takenAtMs, "d MMM")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = IronvellumColors.InkMuted,
                    )
                    if (oldestFirst.size >= 2) {
                        Spacer(Modifier.height(8.dp))
                        val dates = oldestFirst.map { Ledger.dateOf(it.takenAtMs, zone) }
                        // Narrow band: a zero-based axis would flatten the line into nothing.
                        TrendChart(
                            oldestFirst.map { it.valueCm },
                            IronvellumColors.Emerald,
                            fromZero = false,
                            positions = Ledger.datePositions(dates),
                            startLabel = formatDate(oldestFirst.first().takenAtMs, "d MMM"),
                            endLabel = formatDate(oldestFirst.last().takenAtMs, "d MMM"),
                            recordMarker = false,
                        )
                    } else {
                        Text(
                            "Two readings draw the line.",
                            style = MaterialTheme.typography.bodySmall,
                            color = IronvellumColors.InkMuted,
                        )
                    }
                }
            }

            if (newestFirst.isNotEmpty()) {
                InkPanel(Modifier.fillMaxWidth()) {
                    PanelLabel("READINGS")
                    val shown = if (showAll) newestFirst else newestFirst.take(RECENT_ROWS)
                    shown.forEachIndexed { i, entry ->
                        if (i > 0) InkDivider()
                        ReadingRow(entry, onDelete = { viewModel.delete(entry.id) })
                    }
                    if (newestFirst.size > RECENT_ROWS) {
                        InkDivider()
                        InkListRow(
                            label = if (showAll) "Show fewer" else "Show all ${newestFirst.size} ${plural(newestFirst.size, "reading", "readings")}",
                            value = null,
                            onClick = { showAll = !showAll },
                        )
                    }
                }
            }

            InkPanel(Modifier.fillMaxWidth()) {
                PanelLabel("LOG A READING")
                // Technique first: the number is only worth comparing if the tape
                // lands in the same place every time.
                Text(
                    site.howTo,
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                )
                val parsed = parseCm(readingInput)
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
                            parsed?.let {
                                viewModel.log(Math.round(it * 10.0) / 10.0)
                                readingInput = ""
                            }
                        },
                    ),
                    singleLine = true,
                    supportingText = {
                        Text(
                            "Metric cm, one decimal \u00B7 ${CM_MIN.toInt()}\u2013${CM_MAX.toInt()} cm",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                IronvellumButton(
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
            Spacer(Modifier.height(96.dp))
        }
    }
}

@Composable
private fun ReadingRow(entry: MeasurementEntry, onDelete: () -> Unit) {
    // Keyed by the reading, so arming one row never arms its neighbour.
    var armed by remember(entry.id) { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().heightIn(min = LedgerSpace.Target),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(
                "${"%.1f".format(entry.valueCm)} cm",
                style = MaterialTheme.typography.titleSmall,
                fontFamily = ChakraPetch,
                color = IronvellumColors.Ink,
            )
            Text(
                formatDate(entry.takenAtMs),
                style = MaterialTheme.typography.labelSmall,
                color = IronvellumColors.InkMuted,
            )
        }
        if (armed) {
            // One tap on a trash icon used to erase the reading outright: arm first.
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
