package com.ironvellum.app.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ironvellum.app.domain.Ledger
import com.ironvellum.app.domain.MeasurementEntry
import com.ironvellum.app.domain.MeasurementSite
import com.ironvellum.app.domain.Measurements
import com.ironvellum.app.ui.components.InkDivider
import com.ironvellum.app.ui.components.InkListRow
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.components.LedgerSpace
import com.ironvellum.app.ui.components.formatDate
import com.ironvellum.app.ui.theme.IronvellumColors

/**
 * The tape pane: every site as one divided row inside one panel - latest
 * reading, when it was taken and the 30-day change as a plain signed number.
 * The app does not judge direction (a shrinking waist and a growing arm are
 * both progress), so the delta stays muted. Tapping a site opens its history.
 */
@Composable
internal fun TapePage(
    entries: List<MeasurementEntry>,
    onOpenSite: (MeasurementSite) -> Unit,
    onBack: () -> Unit,
) {
    val latest = Measurements.latest(entries)
    Column(Modifier.fillMaxSize()) {
        LedgerTopBar("TAPE MEASUREMENTS", onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LedgerSpace.Gutter),
            verticalArrangement = Arrangement.spacedBy(LedgerSpace.Panel),
        ) {
            Spacer(Modifier.height(LedgerSpace.Panel))
            InkPanel(Modifier.fillMaxWidth()) {
                MeasurementSite.entries.forEachIndexed { i, site ->
                    if (i > 0) InkDivider()
                    val reading = latest[site]
                    InkListRow(
                        label = site.label,
                        value = reading?.let { "%.1f cm".format(it.valueCm) } ?: "\u2014",
                        supporting = reading?.let { r ->
                            val delta = Measurements.deltaCm(entries, site, days = 30)
                            formatDate(r.takenAtMs, "d MMM") +
                                (delta?.let { " \u00B7 ${Ledger.signed(it, "cm")} / 30d" } ?: "")
                        } ?: "tap to take a first reading",
                        onClick = { onOpenSite(site) },
                    )
                }
            }
            // The one privacy caption on the Ledger: the detail pages no longer repeat it.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = IronvellumColors.InkMuted,
                )
                Text(
                    "Stays on this device. Readings are excluded from cloud sync by design.",
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                )
            }
            Spacer(Modifier.height(LedgerSpace.Section))
        }
    }
}
