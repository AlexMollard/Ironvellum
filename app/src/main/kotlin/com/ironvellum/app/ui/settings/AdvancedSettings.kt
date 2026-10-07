package com.ironvellum.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironvellum.app.data.CrashJournal
import com.ironvellum.app.data.cloud.CloudConfig
import com.ironvellum.app.data.cloud.ProbeResult
import com.ironvellum.app.ui.components.InkDivider
import com.ironvellum.app.ui.components.InkRowPanel
import com.ironvellum.app.ui.components.InkSpinner
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.components.ListRow
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.ironvellumFieldColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** "Ironvellum shared cloud", or the lifter's own project named by its host. */
internal fun backendLabel(backend: CloudConfig?): String = when {
    backend == null -> "No cloud on this build"
    backend.isDefault -> "Ironvellum shared cloud"
    else -> "Your own backend · " + backend.url.removePrefix("https://").removeSuffix("/")
}

/** What most people never need: the cloud backend override, the crash log, and sync coverage. */
@Composable
internal fun AdvancedSettings(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sync by viewModel.sync.collectAsStateWithLifecycle()
    // Inputs are saveable across rotation; the probe result lives in the
    // ViewModel so a TEST survives the same.
    val cloudConfig by viewModel.cloudConfig.collectAsStateWithLifecycle()
    val cloudUi by viewModel.cloud.collectAsStateWithLifecycle()
    var cloudFieldsShown by rememberSaveable { mutableStateOf(false) }
    var cloudUrl by rememberSaveable { mutableStateOf("") }
    var cloudKey by rememberSaveable { mutableStateOf("") }
    var confirmCloudSwitch by remember { mutableStateOf(false) }
    var confirmSharedSwitch by remember { mutableStateOf(false) }
    var confirmClearCrashes by remember { mutableStateOf(false) }
    val cloudInputValid = cloudUrl.trim().isNotBlank() && cloudKey.trim().isNotBlank()
    // Read off the main thread: these list a directory, and doing that during
    // composition is main-thread disk I/O on every visit — which is what
    // StrictMode reported. The counts arrive a frame later.
    var crashCount by remember { mutableIntStateOf(0) }
    var latestCrash by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        viewModel.crashSummary().let { (count, latest) ->
            crashCount = count
            latestCrash = latest
        }
    }

    if (confirmCloudSwitch) {
        SettingsConfirmDialog(
            title = "Use this backend?",
            text = "You'll be signed out. Your training stays on this phone and uploads to the new backend when you sign in there.",
            confirmLabel = "Switch",
            dismissLabel = "Stay",
            onConfirm = {
                confirmCloudSwitch = false
                viewModel.switchBackend(cloudUrl, cloudKey)
                cloudFieldsShown = false
                cloudUrl = ""
                cloudKey = ""
            },
            onDismiss = { confirmCloudSwitch = false },
        )
    }
    if (confirmSharedSwitch) {
        SettingsConfirmDialog(
            title = "Return to the shared cloud?",
            text = "You'll be signed out. Your training stays on this phone and uploads to the Ironvellum shared cloud when you sign in there.",
            confirmLabel = "Switch",
            dismissLabel = "Stay",
            onConfirm = {
                confirmSharedSwitch = false
                viewModel.useSharedCloud()
            },
            onDismiss = { confirmSharedSwitch = false },
        )
    }
    if (confirmClearCrashes) {
        SettingsConfirmDialog(
            title = "Clear the crash log?",
            text = "Deletes the stored crash reports. Share them first if you meant to send them.",
            confirmLabel = "Clear",
            dismissLabel = "Keep",
            danger = true,
            onConfirm = {
                confirmClearCrashes = false
                scope.launch {
                    viewModel.clearCrashLog().let { (count, latest) ->
                        crashCount = count
                        latestCrash = latest
                    }
                }
            },
            onDismiss = { confirmClearCrashes = false },
        )
    }

    SettingsPage(SettingsSection.ADVANCED.title, onBack) {
        Spacer(Modifier.height(12.dp))
        InkRowPanel(Modifier.fillMaxWidth()) {
            Text(
                backendLabel(cloudConfig),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = ChakraPetch,
                color = IronvellumColors.Ink,
                modifier = Modifier.padding(16.dp),
            )
            InkDivider()
            if (cloudUi.switching) {
                InkSpinner(Modifier.padding(16.dp))
            } else {
                if (cloudConfig?.isDefault == false) {
                    ListRow("Use shared cloud", onClick = { confirmSharedSwitch = true })
                    InkDivider()
                }
                ListRow("Use my own backend", onClick = { cloudFieldsShown = !cloudFieldsShown })
                if (cloudFieldsShown) {
                    InkDivider()
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        OutlinedTextField(
                            shape = MaterialTheme.shapes.small,
                            colors = ironvellumFieldColors(),
                            value = cloudUrl,
                            onValueChange = { cloudUrl = it },
                            label = { Text("Project URL (https://…supabase.co)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            shape = MaterialTheme.shapes.small,
                            colors = ironvellumFieldColors(),
                            value = cloudKey,
                            onValueChange = { cloudKey = it },
                            label = { Text("Publishable (anon) key") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        cloudUi.probeFor(cloudUrl, cloudKey)?.let { result ->
                            Spacer(Modifier.height(6.dp))
                            Text(
                                when (result) {
                                    is ProbeResult.Ready ->
                                        "Backend ready — schema v${result.version}. Save to move your cloud here."
                                    is ProbeResult.Outdated ->
                                        "The backend is live but its schema is v${result.have}; apply the migrations up to v${result.need}."
                                    ProbeResult.NoSchema ->
                                        "Reached the project, but its database migrations have not been applied."
                                    ProbeResult.BadKey ->
                                        "The key was refused — copy the publishable (anon) key, not the secret one."
                                    ProbeResult.Unreachable ->
                                        "Could not reach the backend — check the URL, your connection, or resume the paused project."
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (result is ProbeResult.Ready) IronvellumColors.SystemGreen else IronvellumColors.InkMuted,
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (cloudUi.testing) {
                                InkSpinner(Modifier.weight(1f))
                            } else {
                                IronvellumButton(
                                    label = "Test",
                                    onClick = { viewModel.testBackend(cloudUrl, cloudKey) },
                                    enabled = cloudInputValid,
                                    quiet = true,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            IronvellumButton(
                                label = "Save",
                                onClick = { confirmCloudSwitch = true },
                                // Saving an unprobed backend would strand the lifter on
                                // a project that cannot hold their data — TEST first.
                                enabled = cloudInputValid && cloudUi.canSave(cloudUrl, cloudKey),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
            InkDivider()
            ListRow(
                "Crash log",
                subline = if (crashCount == 0) {
                    "The crash log is blank."
                } else {
                    "$crashCount crash record${if (crashCount == 1) "" else "s"} · latest $latestCrash"
                },
            )
            if (crashCount > 0) {
                InkDivider()
                // Share is a quiet link; Clear is red and sits at the far edge, well apart from it.
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    IronvellumButton(
                        label = "Share",
                        onClick = {
                            scope.launch {
                                val text = withContext(Dispatchers.IO) {
                                    CrashJournal.recent().joinToString("\n\n")
                                }
                                // Same file route as the export: twenty stack traces
                                // is not 0.9 MB, but there is no reason for two
                                // sharing paths with different failure modes.
                                shareExport(context, "Share Ironvellum crash log", "ironvellum_crash_log.txt", text, "text/plain")
                            }
                        },
                        quiet = true,
                    )
                    IronvellumButton(
                        label = "Clear",
                        onClick = { confirmClearCrashes = true },
                        danger = true,
                    )
                }
            }
            InkDivider()
            ListRow(
                "Health Connect coverage",
                subline = sync.coverage ?: "Days filled per metric; appears after Connect & Sync.",
            )
        }
    }
}
