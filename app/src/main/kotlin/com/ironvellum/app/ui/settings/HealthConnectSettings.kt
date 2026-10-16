package com.ironvellum.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironvellum.app.IronvellumApp
import com.ironvellum.app.data.HealthWriteBack
import com.ironvellum.app.data.Words
import com.ironvellum.app.ui.components.InkSpinner
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.components.SettingsCaption
import com.ironvellum.app.ui.components.SettingsGroup
import com.ironvellum.app.ui.components.SettingsSwitchRow
import com.ironvellum.app.ui.components.formatBodyValue
import com.ironvellum.app.ui.components.formatDate
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.domain.fmt
import kotlinx.coroutines.launch

/**
 * Connect & Sync asks for the reads, then pulls today and the activity history.
 * There is no separate history button: the history also syncs on every app
 * start and in the daily worker (HealthSyncWorker). Saving finished workouts back
 * is a separate switch with its own permission, off until the lifter turns it on.
 */
@Composable
internal fun HealthConnectSettings(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val sync by viewModel.sync.collectAsStateWithLifecycle()
    val healthDays by viewModel.healthDays.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { _ -> viewModel.syncFromHealth() }

    val writeBack = remember { (context.applicationContext as IronvellumApp).healthWriteBack }
    val write by writeBack.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    // Null until Health Connect has been asked; the permission can change while the app is away.
    var permitted by remember { mutableStateOf<Boolean?>(null) }
    var refused by remember { mutableStateOf(false) }
    var missing by remember { mutableStateOf(false) }
    val recheck: () -> Unit = { scope.launch { permitted = writeBack.permitted() } }
    LaunchedEffect(Unit) { recheck() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { recheck() }
    val saveLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { granted ->
        val ok = HealthWriteBack.WRITE_PERMISSION in granted
        permitted = ok
        refused = !ok
        writeBack.setEnabled(ok)
    }
    val status = writeStatus(write.enabled, permitted)

    SettingsPage(SettingsSection.HEALTH_CONNECT.title, onBack) {
        SettingsGroup(null, topSpace = 12.dp) {
            SettingsCaption("Reads body, activity and sleep data. Writes only what you turn on below.")
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
                        append("${snapshot.stepsToday} steps today")
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
                        "${"%,d".fmt(healthDays.sumOf { it.steps })} steps · " +
                        "${"%.0f".fmt(healthDays.sumOf { it.distanceKm })} km",
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
        SettingsGroup(null, topSpace = 10.dp) {
            SettingsSwitchRow(
                label = "Save ${Words.sealed.s} ${Words.trial.pl} to Health Connect",
                caption = "${Words.sealed.c} ${Words.trial.pl} are written once, as strength training: start, end, " +
                    "name and public note. Imported ones never are. Turning this off keeps what is already there.",
                checked = status == WriteStatus.ON,
                onCheckedChange = { on ->
                    refused = false
                    missing = false
                    when {
                        !on -> writeBack.setEnabled(false)
                        !sync.available -> missing = true
                        permitted == true -> writeBack.setEnabled(true)
                        else -> saveLauncher.launch(setOf(HealthWriteBack.WRITE_PERMISSION))
                    }
                },
            )
            if (missing) {
                Spacer(Modifier.height(4.dp))
                SettingsCaption("Health Connect is not available on this device.", IronvellumColors.Caution)
            }
            if (refused) {
                Spacer(Modifier.height(4.dp))
                SettingsCaption(
                    "Health Connect did not allow saving. You can allow it in Health Connect under App permissions.",
                    IronvellumColors.Caution,
                )
            }
            if (status == WriteStatus.REVOKED) {
                Spacer(Modifier.height(4.dp))
                SettingsCaption(
                    "Health Connect's permission to save was removed, so nothing is being saved. Turn this on to allow it again.",
                    IronvellumColors.Caution,
                )
            }
            if (status != WriteStatus.OFF) {
                write.lastError?.let {
                    Spacer(Modifier.height(4.dp))
                    SettingsCaption("Last save failed: $it", IronvellumColors.Caution)
                }
                if (write.lastError == null) write.lastOkMs?.let {
                    Spacer(Modifier.height(4.dp))
                    SettingsCaption("Last saved ${formatDate(it, "d MMM, HH:mm")}")
                }
            }
        }
    }
}

/** How the save switch reads: on, off, or on in the app but no longer allowed by Health Connect. */
internal enum class WriteStatus { OFF, ON, REVOKED }

/**
 * [permitted] is what Health Connect says now, null before it has answered. The app's own switch is only a wish:
 * without the permission nothing is saved, and the page must say so rather than show a working switch.
 */
internal fun writeStatus(enabled: Boolean, permitted: Boolean?): WriteStatus = when {
    !enabled -> WriteStatus.OFF
    permitted == false -> WriteStatus.REVOKED
    else -> WriteStatus.ON
}

@Composable
private fun Reading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontFamily = ChakraPetch,
        color = IronvellumColors.InkMuted,
    )
}

