package com.ironvellum.app.ui.settings

import com.ironvellum.app.ui.asString
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironvellum.app.BuildConfig
import com.ironvellum.app.R
import com.ironvellum.app.data.CrashJournal
import com.ironvellum.app.data.Words
import com.ironvellum.app.data.cloud.CloudConfig
import com.ironvellum.app.data.cloud.ProbeResult
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.ui.components.InkDivider
import com.ironvellum.app.ui.components.InkRowPanel
import com.ironvellum.app.ui.components.InkSpinner
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.components.IronvellumDialog
import com.ironvellum.app.ui.components.ListRow
import com.ironvellum.app.ui.components.formatDate
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.ironvellumFieldColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** "Ironvellum shared cloud", or the lifter's own project named by its host. */
internal fun backendLabel(backend: CloudConfig?): String = when {
    backend == null -> Words.text(UiText.res(R.string.settings_advanced_cloud_none))
    backend.isDefault -> Words.text(UiText.res(R.string.settings_advanced_cloud_shared))
    else -> Words.text(UiText.res(R.string.settings_advanced_cloud_own, backend.url.removePrefix("https://").removeSuffix("/")))
}

/** What most people never need: the cloud backend override, the crash log, and sync coverage. */
@Composable
internal fun AdvancedSettings(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val crashShareTitle = stringResource(R.string.settings_advanced_crash_share_title)
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
            title = stringResource(R.string.settings_advanced_switch_title),
            text = stringResource(R.string.settings_advanced_switch_text),
            confirmLabel = stringResource(R.string.settings_advanced_switch_button),
            dismissLabel = stringResource(R.string.settings_advanced_stay_button),
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
            title = stringResource(R.string.settings_advanced_return_title),
            text = stringResource(R.string.settings_advanced_return_text),
            confirmLabel = stringResource(R.string.settings_advanced_switch_button),
            dismissLabel = stringResource(R.string.settings_advanced_stay_button),
            onConfirm = {
                confirmSharedSwitch = false
                viewModel.useSharedCloud()
            },
            onDismiss = { confirmSharedSwitch = false },
        )
    }
    if (confirmClearCrashes) {
        SettingsConfirmDialog(
            title = stringResource(R.string.settings_advanced_clear_title),
            text = stringResource(R.string.settings_advanced_clear_text),
            confirmLabel = stringResource(R.string.common_clear),
            dismissLabel = stringResource(R.string.settings_advanced_keep_button),
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

    if (BuildConfig.DEBUG) {
        val rescore by viewModel.rescore.collectAsStateWithLifecycle()
        rescore?.let { RescoreDialog(it, viewModel) }
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
                    ListRow(stringResource(R.string.settings_advanced_use_shared_row), onClick = { confirmSharedSwitch = true })
                    InkDivider()
                }
                ListRow(stringResource(R.string.settings_advanced_use_own_row), onClick = { cloudFieldsShown = !cloudFieldsShown })
                if (cloudFieldsShown) {
                    InkDivider()
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        OutlinedTextField(
                            shape = MaterialTheme.shapes.small,
                            colors = ironvellumFieldColors(),
                            value = cloudUrl,
                            onValueChange = { cloudUrl = it },
                            label = { Text(stringResource(R.string.settings_advanced_url_label)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            shape = MaterialTheme.shapes.small,
                            colors = ironvellumFieldColors(),
                            value = cloudKey,
                            onValueChange = { cloudKey = it },
                            label = { Text(stringResource(R.string.settings_advanced_key_label)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        cloudUi.probeFor(cloudUrl, cloudKey)?.let { result ->
                            Spacer(Modifier.height(6.dp))
                            Text(
                                when (result) {
                                    is ProbeResult.Ready ->
                                        stringResource(R.string.settings_advanced_probe_ready, result.version)
                                    is ProbeResult.Outdated ->
                                        stringResource(R.string.settings_advanced_probe_outdated, result.have, result.need)
                                    ProbeResult.NoSchema ->
                                        stringResource(R.string.settings_advanced_probe_no_schema)
                                    ProbeResult.BadKey ->
                                        stringResource(R.string.settings_advanced_probe_bad_key)
                                    ProbeResult.Unreachable ->
                                        stringResource(R.string.settings_advanced_probe_unreachable)
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
                                    label = stringResource(R.string.settings_advanced_test_button),
                                    onClick = { viewModel.testBackend(cloudUrl, cloudKey) },
                                    enabled = cloudInputValid,
                                    quiet = true,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            IronvellumButton(
                                label = stringResource(R.string.common_save),
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
                stringResource(R.string.settings_advanced_crash_label),
                subline = if (crashCount == 0) {
                    stringResource(R.string.settings_advanced_crash_empty)
                } else {
                    pluralStringResource(R.plurals.settings_advanced_crash_summary, crashCount, crashCount, latestCrash)
                },
            )
            if (crashCount > 0) {
                InkDivider()
                // Share is a quiet link; Clear is red and sits at the far edge, well apart from it.
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    IronvellumButton(
                        label = stringResource(R.string.common_share),
                        onClick = {
                            scope.launch {
                                val text = withContext(Dispatchers.IO) {
                                    CrashJournal.recent().joinToString("\n\n")
                                }
                                // Same file route as the export: twenty stack traces
                                // is not 0.9 MB, but there is no reason for two
                                // sharing paths with different failure modes.
                                shareExport(context, crashShareTitle, "ironvellum_crash_log.txt", text, "text/plain")
                            }
                        },
                        quiet = true,
                    )
                    IronvellumButton(
                        label = stringResource(R.string.common_clear),
                        onClick = { confirmClearCrashes = true },
                        danger = true,
                    )
                }
            }
            InkDivider()
            ListRow(
                stringResource(R.string.settings_advanced_coverage_label),
                subline = sync.coverage ?: stringResource(R.string.settings_advanced_coverage_empty),
            )
        }
        // Debug builds only: BuildConfig.DEBUG is a constant, so a release build has no trace of this group.
        if (BuildConfig.DEBUG) {
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.settings_advanced_dev_header),
                style = MaterialTheme.typography.labelMedium,
                color = IronvellumColors.InkMuted,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            )
            InkRowPanel(Modifier.fillMaxWidth()) {
                ListRow(
                    stringResource(R.string.settings_advanced_dev_rescore_label),
                    subline = UiText.themed(R.string.settings_advanced_dev_rescore_caption, R.string.settings_advanced_dev_rescore_caption__t).asString(),
                    onClick = viewModel::previewRescore,
                )
            }
        }
    }
}

/** The rescore's preview (per trial, old to new, the total and the level) and, on confirm, its result. Debug builds only. */
@Composable
private fun RescoreDialog(ui: SettingsViewModel.RescoreUi, viewModel: SettingsViewModel) {
    val plan = ui.plan
    val changed = plan?.changed.orEmpty()
    IronvellumDialog(
        onDismissRequest = { if (!ui.busy) viewModel.dismissRescore() },
        title = {
            Text(
                when {
                    ui.applied -> stringResource(R.string.settings_advanced_dev_rescored_title)
                    plan != null -> stringResource(R.string.settings_advanced_dev_rescore_title)
                    ui.error != null -> stringResource(R.string.settings_advanced_dev_failed_title)
                    else -> UiText.themed(R.string.settings_advanced_dev_pricing_title, R.string.settings_advanced_dev_pricing_title__t).asString()
                },
            )
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                if (ui.error != null) Text(ui.error)
                if (plan == null && ui.error == null) InkSpinner(Modifier.padding(16.dp))
                if (plan != null) {
                    val signed = { n: Long -> (if (n > 0) "+" else "") + n }
                    val held = if (plan.clamped) " " + stringResource(R.string.settings_advanced_dev_held) else ""
                    val due = if (plan.inscriptionsDue > 0) ", " + UiText.plural(R.plurals.settings_advanced_dev_inscriptions_due, plan.inscriptionsDue).asString() else ""
                    Text(
                        UiText.themedPlural(R.plurals.settings_advanced_dev_changed, R.plurals.settings_advanced_dev_changed__t, plan.changes.size, changed.size, plan.changes.size).asString() + "\n" +
                            UiText.themed(R.string.settings_advanced_dev_xp_line, R.string.settings_advanced_dev_xp_line__t, signed(plan.delta), plan.totalBefore, plan.totalAfter, signed(plan.applied)).asString() + "\n" +
                            stringResource(R.string.settings_advanced_dev_level, plan.levelBefore, plan.levelAfter, held + due),
                        style = MaterialTheme.typography.bodyMedium,
                        color = IronvellumColors.Ink,
                    )
                    if (changed.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                            items(changed, key = { it.sessionId }) { c ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Text(
                                        stringResource(
                                            if (c.amended) R.string.settings_advanced_dev_row_amended else R.string.settings_advanced_dev_row,
                                            formatDate(c.startedAtMs, "d MMM yy"), c.label,
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = IronvellumColors.InkMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        stringResource(R.string.settings_advanced_dev_xp_change, c.oldXp, c.newXp),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = IronvellumColors.Ink,
                                        modifier = Modifier.padding(start = 8.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            when {
                ui.applied || (plan == null && !ui.busy) -> IronvellumButton(label = stringResource(R.string.settings_advanced_dev_close_button), onClick = viewModel::dismissRescore)
                plan != null && !ui.busy -> IronvellumButton(
                    label = stringResource(R.string.common_apply),
                    onClick = viewModel::applyRescore,
                    enabled = changed.isNotEmpty() || plan.applied != 0L,
                    danger = true,
                )
                else -> InkSpinner(Modifier)
            }
        },
        dismissButton = if (ui.applied || ui.busy) null else {
            { IronvellumButton(label = stringResource(R.string.common_cancel), onClick = viewModel::dismissRescore, quiet = true) }
        },
    )
}
