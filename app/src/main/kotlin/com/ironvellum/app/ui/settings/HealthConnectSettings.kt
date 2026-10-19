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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironvellum.app.IronvellumApp
import com.ironvellum.app.R
import com.ironvellum.app.data.HealthWriteBack
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.domain.fmt
import com.ironvellum.app.ui.asString
import com.ironvellum.app.ui.components.InkSpinner
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.components.SettingsCaption
import com.ironvellum.app.ui.components.SettingsGroup
import com.ironvellum.app.ui.components.SettingsSwitchRow
import com.ironvellum.app.ui.components.formatBodyValue
import com.ironvellum.app.ui.components.formatDate
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors
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
            SettingsCaption(stringResource(R.string.settings_health_intro))
            Spacer(Modifier.height(10.dp))
            if (sync.syncing) {
                InkSpinner()
            } else {
                IronvellumButton(
                    label = stringResource(R.string.settings_health_connect_button),
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
                        // Samsung Health batches its pushes: without the "as of"
                        // the count just looks wrong against the phone's tally.
                        val steps = snapshot.stepsToday.toInt()
                        val asOf = snapshot.stepsAsOfMs
                        append(
                            if (asOf == null) {
                                pluralStringResource(R.plurals.settings_health_steps_today, steps, snapshot.stepsToday)
                            } else {
                                pluralStringResource(
                                    R.plurals.settings_health_steps_today_as_of,
                                    steps,
                                    snapshot.stepsToday,
                                    formatDate(asOf, "HH:mm"),
                                )
                            },
                        )
                        snapshot.weightKg?.let { append(" · " + stringResource(R.string.settings_health_weight, formatBodyValue(it))) }
                        snapshot.bodyFatPct?.let { append(" · " + stringResource(R.string.settings_health_body_fat, formatBodyValue(it))) }
                    },
                )
            }
            if (healthDays.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Reading(
                    listOf(
                        pluralStringResource(R.plurals.settings_health_days_synced, healthDays.size, healthDays.size),
                        stringResource(R.string.settings_health_steps_total, "%,d".fmt(healthDays.sumOf { it.steps })),
                        stringResource(R.string.settings_health_distance_total, "%.0f".fmt(healthDays.sumOf { it.distanceKm })),
                    ).joinToString(" · "),
                )
            }
            sync.message?.let {
                Spacer(Modifier.height(4.dp))
                SettingsCaption(it.asString())
            }
            sync.historyMessage?.let {
                Spacer(Modifier.height(4.dp))
                SettingsCaption(it.asString())
            }
        }
        SettingsGroup(null, topSpace = 10.dp) {
            SettingsSwitchRow(
                label = UiText.themed(R.string.settings_health_save_label, R.string.settings_health_save_label__t).asString(),
                caption = UiText.themed(R.string.settings_health_save_caption, R.string.settings_health_save_caption__t).asString(),
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
                SettingsCaption(stringResource(R.string.settings_health_unavailable), IronvellumColors.Caution)
            }
            if (refused) {
                Spacer(Modifier.height(4.dp))
                SettingsCaption(
                    stringResource(R.string.settings_health_refused),
                    IronvellumColors.Caution,
                )
            }
            if (status == WriteStatus.REVOKED) {
                Spacer(Modifier.height(4.dp))
                SettingsCaption(
                    stringResource(R.string.settings_health_revoked),
                    IronvellumColors.Caution,
                )
            }
            if (status != WriteStatus.OFF) {
                write.lastError?.let {
                    Spacer(Modifier.height(4.dp))
                    SettingsCaption(stringResource(R.string.settings_health_last_failed, it.asString()), IronvellumColors.Caution)
                }
                if (write.lastError == null) write.lastOkMs?.let {
                    Spacer(Modifier.height(4.dp))
                    SettingsCaption(stringResource(R.string.settings_health_last_saved, formatDate(it, "d MMM, HH:mm")))
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

