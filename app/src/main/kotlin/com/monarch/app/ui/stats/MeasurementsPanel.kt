package com.monarch.app.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.monarch.app.data.Repository
import com.monarch.app.domain.MeasurementEntry
import com.monarch.app.domain.MeasurementSite
import com.monarch.app.domain.Measurements
import com.monarch.app.ui.components.SystemWindow
import com.monarch.app.ui.components.formatDate
import com.monarch.app.ui.monarchRepository
import com.monarch.app.ui.theme.ChakraPetch
import com.monarch.app.ui.theme.MonarchColors
import com.monarch.app.ui.theme.MonarchTracking
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map

data class MeasurementsUi(
    val entries: List<MeasurementEntry> = emptyList(),
)

class MeasurementsViewModel(repo: Repository) : ViewModel() {
    val ui: StateFlow<MeasurementsUi> = repo.observeMeasurements()
        .map { entries -> MeasurementsUi(entries) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MeasurementsUi())
}

/**
 * The BODY tab's measurement rail: one tile per tracked site with the latest
 * reading and the 30-day delta as a neutral arrow plus signed value — the app
 * does not judge direction, since a shrinking waist and a growing arm are both
 * progress. Tapping a site opens its full history.
 */
@Composable
fun MeasurementsPanel(
    onOpenSite: (MeasurementSite) -> Unit,
    viewModel: MeasurementsViewModel = viewModel(
        factory = viewModelFactory { initializer { MeasurementsViewModel(monarchRepository()) } },
    ),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val latest = Measurements.latest(ui.entries)

    val anyReadings = ui.entries.isNotEmpty()

    Column(Modifier.fillMaxWidth()) {
        // Was: every unmeasured tile repeated "No readings yet — take the first
        // measurement." eight times. The instruction lives once here, only while
        // the grid is entirely empty; empty tiles show a muted dash.
        if (!anyReadings) {
            Text(
                "Tap a site to take its first measurement.",
                style = MaterialTheme.typography.labelSmall,
                color = MonarchColors.InkMuted,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        MeasurementSite.entries.chunked(2).forEach { rowSites ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rowSites.forEach { site ->
                    SiteTile(
                        site = site,
                        latest = latest[site],
                        entries = ui.entries,
                        modifier = Modifier.weight(1f),
                        onClick = { onOpenSite(site) },
                    )
                }
                // Keep the last odd tile from stretching across the rail.
                if (rowSites.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "SEALED TO THIS DEVICE — body measurements never leave your phone. " +
                "They are excluded from cloud sync by design; the JSON archive is yours alone.",
            style = MaterialTheme.typography.labelSmall,
            color = MonarchColors.InkMuted,
            modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
        )
    }
}

@Composable
private fun SiteTile(
    site: MeasurementSite,
    latest: MeasurementEntry?,
    entries: List<MeasurementEntry>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    SystemWindow(modifier, onClick = onClick) {
        Text(
            site.label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            color = MonarchColors.SystemGreen,
            letterSpacing = MonarchTracking.InlineLabel,
        )
        Spacer(Modifier.height(4.dp))
        if (latest == null) {
            // Was: a full sentence per empty tile (eight copies of one hint).
            // The instruction now lives once above the grid; a muted dash marks
            // the unmeasured value.
            Text(
                "—",
                style = MaterialTheme.typography.titleLarge,
                fontFamily = ChakraPetch,
                color = MonarchColors.InkMuted,
            )
        } else {
            Text(
                "%.1f".format(latest.valueCm),
                style = MaterialTheme.typography.titleLarge,
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.Bold,
                color = MonarchColors.Ink,
            )
            Text(
                "cm · ${formatDate(latest.takenAtMs, "d MMM")}",
                style = MaterialTheme.typography.labelSmall,
                color = MonarchColors.InkMuted,
            )
            Spacer(Modifier.height(6.dp))
            DeltaLine(entries, site)
        }
    }
}

/**
 * Neutral direction treatment: the app cannot know which direction the user
 * wants (a shrinking waist and a growing arm are both progress), so the delta
 * stays uncoloured — arrow for direction, signed value for magnitude.
 */
@Composable
private fun DeltaLine(entries: List<MeasurementEntry>, site: MeasurementSite) {
    val delta = Measurements.deltaCm(entries, site, days = 30) ?: return
    val arrow = if (delta >= 0.0) "▲" else "▼"
    Text(
        "$arrow ${"%.1f".format(delta)} cm / 30d",
        style = MaterialTheme.typography.labelSmall,
        fontFamily = ChakraPetch,
        color = MonarchColors.InkMuted,
        letterSpacing = MonarchTracking.InlineLabel,
    )
}
