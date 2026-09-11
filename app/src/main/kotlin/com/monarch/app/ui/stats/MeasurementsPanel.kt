package com.monarch.app.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.monarch.app.domain.MeasurementGoal
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class MeasurementsUi(
    val entries: List<MeasurementEntry> = emptyList(),
    val goals: List<MeasurementGoal> = emptyList(),
)

class MeasurementsViewModel(repo: Repository) : ViewModel() {
    val ui: StateFlow<MeasurementsUi> = combine(
        repo.observeMeasurements(),
        repo.observeMeasurementGoals(),
    ) { entries, goals ->
        MeasurementsUi(entries, goals)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MeasurementsUi())
}

/**
 * The BODY tab's measurement rail: one tile per tracked site with the latest
 * reading, the 30-day delta coloured by whether it moves TOWARD the site's
 * goal (a shrinking waist and a growing arm are both progress), and a slim
 * meter when a goal exists. Tapping a site opens its full history.
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

    Column(Modifier.fillMaxWidth()) {
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
                        goal = ui.goals.firstOrNull { it.site == site },
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
    goal: MeasurementGoal?,
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
            // Never show 0.0 for a site nobody has measured yet.
            Text(
                "No readings yet — take the first measurement.",
                style = MaterialTheme.typography.bodySmall,
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
            DeltaLine(entries, site, goal)
            goal?.let {
                val progress = Measurements.progress(it, entries)
                Spacer(Modifier.height(8.dp))
                GoalMeter(progress.fraction, progress.achieved)
            }
        }
    }
}

/**
 * Direction colour follows the GOAL, not the sign of the change: growing an
 * arm toward its target and shrinking a waist toward its target are both
 * SystemGreen; moving away from the target is DangerRed; no goal is neutral.
 */
@Composable
private fun DeltaLine(
    entries: List<MeasurementEntry>,
    site: MeasurementSite,
    goal: MeasurementGoal?,
) {
    val delta = Measurements.deltaCm(entries, site, days = 30) ?: return
    val toward = goal?.let { if (Measurements.progress(it, entries).shrinking) delta < 0.0 else delta > 0.0 }
    val color = when {
        goal == null -> MonarchColors.InkMuted
        toward == true -> MonarchColors.SystemGreen
        else -> MonarchColors.DangerRed
    }
    val arrow = if (delta >= 0.0) "▲" else "▼"
    Text(
        "$arrow ${"%.1f".format(kotlin.math.abs(delta))} cm / 30d",
        style = MaterialTheme.typography.labelSmall,
        fontFamily = ChakraPetch,
        color = color,
        letterSpacing = MonarchTracking.InlineLabel,
    )
}

/** Slim meter matching the house progress bar: green→gold fill, gold when achieved. */
@Composable
private fun GoalMeter(fraction: Float, achieved: Boolean) {
    val clamped = fraction.coerceIn(0f, 1f)
    Box(
        Modifier
            .fillMaxWidth()
            .height(5.dp)
            .background(Color(0xFF1E2A24)),
    ) {
        if (clamped > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(clamped)
                    .height(5.dp)
                    .background(
                        if (achieved) {
                            Brush.horizontalGradient(
                                listOf(MonarchColors.SovereignGold, MonarchColors.EmeraldBright),
                            )
                        } else {
                            Brush.horizontalGradient(
                                listOf(MonarchColors.SystemGreen, MonarchColors.SovereignGold),
                            )
                        },
                    ),
            )
        }
    }
}
