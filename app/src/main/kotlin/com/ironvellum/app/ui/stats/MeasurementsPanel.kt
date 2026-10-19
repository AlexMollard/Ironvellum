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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ironvellum.app.R
import com.ironvellum.app.data.Lines
import com.ironvellum.app.domain.Ledger
import com.ironvellum.app.domain.MeasurementEntry
import com.ironvellum.app.domain.MeasurementSite
import com.ironvellum.app.domain.Measurements
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.domain.fmt
import com.ironvellum.app.ui.asString
import com.ironvellum.app.ui.components.InkDivider
import com.ironvellum.app.ui.components.InkListRow
import com.ironvellum.app.ui.components.InkRowPanel
import com.ironvellum.app.ui.components.LedgerSpace
import com.ironvellum.app.ui.components.PushedHeader
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
        PushedHeader(Lines.tapeReadings.uppercase(), onBack, Modifier.padding(top = 12.dp, start = LedgerSpace.Gutter, end = LedgerSpace.Gutter), backDescription = UiText.themed(R.string.stats_back_to_ledger, R.string.stats_back_to_ledger__t).asString())
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LedgerSpace.Gutter),
            verticalArrangement = Arrangement.spacedBy(LedgerSpace.Panel),
        ) {
            Spacer(Modifier.height(LedgerSpace.Panel))
            // One hint for the whole list, not one on every empty site.
            if (MeasurementSite.entries.any { latest[it] == null }) {
                Text(
                    UiText.themed(R.string.measurement_tape_hint, R.string.measurement_tape_hint__t).asString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                )
            }
            InkRowPanel(Modifier.fillMaxWidth()) {
                MeasurementSite.entries.forEachIndexed { i, site ->
                    if (i > 0) InkDivider()
                    val reading = latest[site]
                    InkListRow(
                        label = site.label,
                        value = reading?.let { stringResource(R.string.stats_cm_value, "%.1f".fmt(it.valueCm)) } ?: stringResource(R.string.stats_dash),
                        supporting = reading?.let { r ->
                            val delta = Measurements.deltaCm(entries, site, days = 30)
                            val taken = formatDate(r.takenAtMs, "d MMM")
                            delta?.let { stringResource(R.string.measurement_row_delta, taken, Ledger.signed(it, stringResource(R.string.stats_unit_cm))) } ?: taken
                        },
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
                    UiText.themed(R.string.measurement_tape_privacy, R.string.measurement_tape_privacy__t).asString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                )
            }
            Spacer(Modifier.height(LedgerSpace.Section))
        }
    }
}
