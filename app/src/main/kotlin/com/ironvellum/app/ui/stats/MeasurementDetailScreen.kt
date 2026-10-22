package com.ironvellum.app.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.res.stringResource
import com.ironvellum.app.R
import com.ironvellum.app.data.Lines
import com.ironvellum.app.data.Words
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.ui.asString
import com.ironvellum.app.ui.components.InkIconButton
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
import com.ironvellum.app.domain.fmt
import com.ironvellum.app.domain.DecimalInput
import com.ironvellum.app.ui.components.InkDivider
import com.ironvellum.app.ui.components.InkListRow
import com.ironvellum.app.ui.components.InkRowPanel
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.components.LedgerSpace
import com.ironvellum.app.ui.components.PushedHeader
import com.ironvellum.app.ui.components.rememberZoneId
import com.ironvellum.app.ui.components.PanelLabel
import com.ironvellum.app.ui.components.StatSize
import com.ironvellum.app.ui.components.StatValue
import com.ironvellum.app.ui.components.TrendChart
import com.ironvellum.app.ui.components.UndoBar
import com.ironvellum.app.ui.components.formatDate
import com.ironvellum.app.ui.ironvellumRepository
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.IronvellumTracking
import com.ironvellum.app.ui.theme.ironvellumFieldColors
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

    fun restore(entry: MeasurementEntry) {
        viewModelScope.launch { repo.restoreMeasurement(entry) }
    }

}

/** Metric cm, one decimal, sane human range so a typo cannot poison the chart. */
private val CM_MIN = 10.0
private val CM_MAX = 250.0

/** Readings shown before "Show all"; the chart carries every one. */
private const val RECENT_ROWS = 5

/** Keep digits and one point; a decimal comma from the keyboard is read as the point. */
private fun sanitizeCm(raw: String): String = DecimalInput.sanitize(raw, maxDecimals = 1, maxLength = 6)

private fun parseCm(raw: String): Double? =
    Ledger.parseDecimal(raw)?.takeIf { it in CM_MIN..CM_MAX }

/**
 * One tape site: the logging form first, then one card holding the latest value, its
 * trend and the readings. Everything here is device-local - the cloud schema has no table
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
    val zone = rememberZoneId()
    val newestFirst = remember(ui.entries) { ui.entries.sortedByDescending { it.takenAtMs } }
    val oldestFirst = remember(newestFirst) { newestFirst.reversed() }
    // Two readings on one day would share an x on the chart: plot each day's latest.
    val chartEntries = remember(oldestFirst, zone) { Ledger.latestPerDay(oldestFirst, zone) { it.takenAtMs } }
    val latest = newestFirst.firstOrNull()
    val delta = remember(ui.entries) { Measurements.deltaCm(ui.entries, site, days = 30) }
    // The reading just deleted, for the Undo bar; it belongs to this page and goes with it.
    var lastDeleted by remember { mutableStateOf<MeasurementEntry?>(null) }

    Column(Modifier.fillMaxSize().imePadding()) {
        PushedHeader(site.label.uppercase(), onBack, Modifier.padding(top = 12.dp, start = LedgerSpace.Gutter, end = LedgerSpace.Gutter), backDescription = stringResource(R.string.measurement_back_description))
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LedgerSpace.Gutter),
            verticalArrangement = Arrangement.spacedBy(LedgerSpace.Panel),
        ) {
            Spacer(Modifier.height(LedgerSpace.Panel))

            // Form first: the page is for taking a reading, the history follows it.
            InkPanel(Modifier.fillMaxWidth()) {
                // Technique first: the number is only worth comparing if the tape
                // lands in the same place every time.
                Text(
                    site.howTo,
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                val parsed = parseCm(readingInput)
                OutlinedTextField(
                    shape = MaterialTheme.shapes.small,
                    colors = ironvellumFieldColors(),
                    value = readingInput,
                    onValueChange = { readingInput = sanitizeCm(it) },
                    label = { Text(stringResource(R.string.measurement_circumference_label)) },
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
                            stringResource(R.string.measurement_input_hint, CM_MIN.toInt(), CM_MAX.toInt()),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                IronvellumButton(
                    UiText.themed(R.string.measurement_log_button, R.string.measurement_log_button__t).asString(),
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

            if (latest == null) {
                Text(
                    Lines.firstReadingPrompt,
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                )
            } else {
                // The latest value, its trend and every reading are one card: one home for the figure.
                InkRowPanel(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        PanelLabel(Words.reading.upl)
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            StatValue("%.1f".fmt(latest.valueCm), size = StatSize.Hero, unit = stringResource(R.string.stats_unit_cm))
                            delta?.let {
                                Text(
                                    stringResource(R.string.measurement_delta_30d, Ledger.signed(it, stringResource(R.string.stats_unit_cm))),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontFamily = ChakraPetch,
                                    color = IronvellumColors.InkMuted,
                                    modifier = Modifier.padding(bottom = 4.dp),
                                )
                            }
                        }
                        Text(
                            stringResource(R.string.measurement_taken_on, formatDate(latest.takenAtMs, "d MMM")),
                            style = MaterialTheme.typography.labelSmall,
                            color = IronvellumColors.InkMuted,
                        )
                        if (chartEntries.size >= 2) {
                            Spacer(Modifier.height(8.dp))
                            val dates = chartEntries.map { Ledger.dateOf(it.takenAtMs, zone) }
                            val shortPattern = stringResource(R.string.stats_short_date_pattern)
                            // Narrow band: a zero-based axis would flatten the line into nothing.
                            TrendChart(
                                chartEntries.map { it.valueCm },
                                IronvellumColors.Emerald,
                                fromZero = false,
                                positions = Ledger.datePositions(dates),
                                startLabel = formatDate(chartEntries.first().takenAtMs, shortPattern),
                                endLabel = formatDate(chartEntries.last().takenAtMs, shortPattern),
                                recordMarker = false,
                                valueText = { Words.text(UiText.res(R.string.stats_cm_value, "%.1f".fmt(it))) },
                                dateText = { formatDate(chartEntries[it].takenAtMs, shortPattern) },
                                atMs = chartEntries.map { it.takenAtMs },
                            )
                        } else {
                            Text(
                                UiText.themed(R.string.measurement_chart_two_days, R.string.measurement_chart_two_days__t).asString(),
                                style = MaterialTheme.typography.bodySmall,
                                color = IronvellumColors.InkMuted,
                            )
                        }
                    }
                    val shown = if (showAll) newestFirst else newestFirst.take(RECENT_ROWS)
                    shown.forEach { entry ->
                        InkDivider()
                        ReadingRow(entry, onDelete = {
                            lastDeleted = entry
                            viewModel.delete(entry.id)
                        })
                    }
                    if (newestFirst.size > RECENT_ROWS) {
                        InkDivider()
                        InkListRow(
                            label = if (showAll) {
                                stringResource(R.string.measurement_show_fewer)
                            } else {
                                UiText.themedPlural(R.plurals.measurement_show_all, R.plurals.measurement_show_all__t, newestFirst.size).asString()
                            },
                            value = null,
                            onClick = { showAll = !showAll },
                        )
                    }
                }
            }
            Spacer(Modifier.height(96.dp))
        }
        lastDeleted?.let { gone ->
            UndoBar(
                UiText.themed(R.string.stats_reading_deleted, R.string.stats_reading_deleted__t).asString(),
                onUndo = {
                    viewModel.restore(gone)
                    lastDeleted = null
                },
                modifier = Modifier.padding(horizontal = LedgerSpace.Gutter, vertical = LedgerSpace.Panel),
                onExpired = { lastDeleted = null },
                key = gone.id,
            )
        }
    }
}

@Composable
private fun ReadingRow(entry: MeasurementEntry, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = LedgerSpace.Target).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(
                stringResource(R.string.stats_cm_value, "%.1f".fmt(entry.valueCm)),
                style = MaterialTheme.typography.titleSmall,
                fontFamily = ChakraPetch,
                color = IronvellumColors.Ink,
            )
            Text(
                formatDate(entry.takenAtMs, stringResource(R.string.stats_history_date_pattern)),
                style = MaterialTheme.typography.labelSmall,
                color = IronvellumColors.InkMuted,
            )
        }
        // Immediate: the bar under the page offers Undo.
        InkIconButton(onClick = onDelete) {
            Icon(Icons.Outlined.Delete, contentDescription = UiText.themed(R.string.stats_delete_reading, R.string.stats_delete_reading__t).asString(), tint = IronvellumColors.InkMuted)
        }
    }
}
