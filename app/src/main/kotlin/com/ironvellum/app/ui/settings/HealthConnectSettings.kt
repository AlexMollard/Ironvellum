package com.ironvellum.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.intl.Locale as ComposeLocale
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironvellum.app.ui.components.InkSpinner
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.components.SettingsCaption
import com.ironvellum.app.ui.components.SettingsGroup
import com.ironvellum.app.ui.components.formatBodyValue
import com.ironvellum.app.ui.components.formatDate
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors
import java.util.Locale

/**
 * Connect & Sync asks for the reads, then pulls today and the activity history.
 * There is no separate history button: the history also syncs on every app
 * start and in the daily worker (HealthSyncWorker).
 */
@Composable
internal fun HealthConnectSettings(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val sync by viewModel.sync.collectAsStateWithLifecycle()
    val healthDays by viewModel.healthDays.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { _ -> viewModel.syncFromHealth() }

    SettingsPage(SettingsSection.HEALTH_CONNECT.title, onBack) {
        SettingsGroup(null, topSpace = 12.dp) {
            SettingsCaption("Reads body, activity and sleep data. Writes nothing.")
            Spacer(Modifier.height(10.dp))
            if (sync.syncing) {
                InkSpinner()
            } else {
                IronvellumButton(
                    label = "Connect & Sync",
                    onClick = {
                        if (sync.available) {
                            permissionLauncher.launch(healthPermissionRequest(context))
                        } else {
                            viewModel.syncFromHealth()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            sync.result?.let { snapshot ->
                Spacer(Modifier.height(8.dp))
                Reading(
                    buildString {
                        append("⏱ ${snapshot.stepsToday} steps today")
                        // Samsung Health batches its pushes: without the "as of"
                        // the count just looks wrong against the phone's tally.
                        snapshot.stepsAsOfMs?.let { append(" (as of ${formatDate(it, "HH:mm")})") }
                        snapshot.weightKg?.let { append(" · ${formatBodyValue(it)} kg") }
                        snapshot.bodyFatPct?.let { append(" · ${formatBodyValue(it)}% bf") }
                    },
                )
            }
            if (healthDays.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Reading(
                    "${healthDays.size} days synced · " +
                        "${String.format(numberLocale(), "%,d", healthDays.sumOf { it.steps })} steps · " +
                        "${"%.0f".format(healthDays.sumOf { it.distanceKm })} km",
                )
            }
            sync.message?.let {
                Spacer(Modifier.height(4.dp))
                SettingsCaption(it)
            }
            sync.historyMessage?.let {
                Spacer(Modifier.height(4.dp))
                SettingsCaption(it)
            }
        }
    }
}

@Composable
private fun Reading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontFamily = ChakraPetch,
        color = IronvellumColors.SystemGreen,
    )
}

/**
 * The reader's locale, read through Compose's own locale state rather than
 * `Locale.getDefault()`, so formatted numbers recompose when the device locale
 * changes instead of keeping the value captured at first composition.
 */
@Composable
private fun numberLocale(): Locale = ComposeLocale.current.platformLocale
