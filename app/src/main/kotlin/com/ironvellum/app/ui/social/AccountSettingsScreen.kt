package com.ironvellum.app.ui.social

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.LocalContext
import com.ironvellum.app.data.InboxNotifier
import com.ironvellum.app.data.Notifications
import com.ironvellum.app.ui.components.NotificationBlockedNotice
import com.ironvellum.app.ui.components.rememberNotificationAccess
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ironvellum.app.data.cloud.BlockedLifter
import com.ironvellum.app.ui.components.InkSegmented
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.components.SettingsGroup
import com.ironvellum.app.ui.components.TapRow
import com.ironvellum.app.ui.components.plural
import com.ironvellum.app.ui.ironvellumAccount
import com.ironvellum.app.ui.ironvellumCloudSync
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.IronvellumTracking
import java.text.DateFormat
import java.util.Date
import com.ironvellum.app.domain.fmt

/**
 * Everything about the account that is not social: name, visibility, cloud
 * sync, backup, blocked lifters, sign out. It lives behind the gear on the
 * ALLIES header so that tab stays a list of lifters instead of a settings dump.
 *
 * Pushed over the social route, so leaving the account (sign out or delete)
 * pops back to the Allies tab, which then renders the sign-in form.
 */
@Composable
fun AccountSettingsScreen(
    onBack: () -> Unit,
    viewModel: AccountViewModel = viewModel(
        factory = viewModelFactory {
            initializer { AccountViewModel(ironvellumAccount(), ironvellumCloudSync()) }
        },
    ),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    // Pop only after an account was actually seen: a restored session is
    // still null for its first frames, and popping on that left the lifter
    // bounced off this screen the moment it opened.
    var hadAccount by remember { mutableStateOf(ui.account != null) }
    LaunchedEffect(ui.account) {
        if (ui.account != null) hadAccount = true else if (hadAccount) onBack()
    }
    val acct = ui.account ?: return

    var editingName by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var allyAlerts by remember { mutableStateOf(InboxNotifier.enabled(context)) }
    // Re-read on every resume: a block lifted in system settings shows at once.
    val allyAccess = rememberNotificationAccess(Notifications.CHANNEL_ALLIES)
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        PushedHeader("ACCOUNT", onBack)

        SettingsGroup("PROFILE") {
            TapRow(onClickLabel = "Edit true name", onClick = { editingName = true }) {
                Column(Modifier.weight(1f)) {
                    Text(
                        acct.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = ChakraPetch,
                        fontWeight = FontWeight.Bold,
                        color = IronvellumColors.Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        acct.email,
                        style = MaterialTheme.typography.labelMedium,
                        color = IronvellumColors.InkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    "EDIT",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = ChakraPetch,
                    fontWeight = FontWeight.SemiBold,
                    color = IronvellumColors.SystemGreen,
                    letterSpacing = IronvellumTracking.InlineLabel,
                )
            }
            Spacer(Modifier.height(12.dp))
            // Wire values stay public/friends/private; only the labels say ALLIES.
            InkSegmented(
                options = listOf("public" to "PUBLIC", "friends" to "ALLIES", "private" to "PRIVATE"),
                selected = acct.visibility,
                onPick = viewModel::setVisibility,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                when (acct.visibility) {
                    "public" -> "Every Ironbound can read your trials."
                    "friends" -> "Only your allies can read your trials."
                    else -> "No one but you can read your trials."
                },
                style = MaterialTheme.typography.labelSmall,
                color = IronvellumColors.InkMuted,
            )
        }

        SettingsGroup("CLOUD") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Sync",
                        style = MaterialTheme.typography.titleSmall,
                        fontFamily = ChakraPetch,
                        color = IronvellumColors.Ink,
                    )
                    val outcome = ui.lastSync
                    Text(
                        if (outcome == null) {
                            "Uploads after each trial"
                        } else {
                            buildString {
                                append("Pushed ${outcome.sessions} ${plural(outcome.sessions, "trial", "trials")} · ")
                                append("${outcome.sets} ${plural(outcome.sets, "set", "sets")} · ")
                                append("${outcome.titles} ${plural(outcome.titles, "title", "titles")}")
                                if (outcome.problems.isNotEmpty()) append(" · ${outcome.problems.size} skipped")
                            }
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = when {
                            outcome == null -> IronvellumColors.InkMuted
                            outcome.problems.isEmpty() -> IronvellumColors.Emerald
                            else -> IronvellumColors.SovereignGold
                        },
                    )
                }
                IronvellumButton(label = "Sync", onClick = viewModel::syncNow, enabled = !ui.busy)
            }
            ui.lastSync?.problems?.forEach { problem ->
                Spacer(Modifier.height(4.dp))
                Text(
                    "· $problem",
                    style = MaterialTheme.typography.labelSmall,
                    color = IronvellumColors.InkMuted,
                )
            }
        }

        SettingsGroup("BACKUP") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Cloud backup",
                        style = MaterialTheme.typography.titleSmall,
                        fontFamily = ChakraPetch,
                        color = IronvellumColors.Ink,
                    )
                    // Freshness must be visible without tapping anything: a
                    // lifter has to tell at a glance whether they are protected.
                    Text(
                        ui.lastBackup?.let {
                            DateFormat.getDateTimeInstance().format(Date(it.atMs)) + " · " + formatBytes(it.bytes)
                        } ?: "No backup yet",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (ui.lastBackup != null) IronvellumColors.Emerald else IronvellumColors.SovereignGold,
                    )
                }
                IronvellumButton(label = "Back up", onClick = viewModel::backUpNow, enabled = !ui.busy)
            }
            // Next to the button that failed: a refused backup elsewhere on the
            // screen read as a button that simply did nothing.
            ui.backupError?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = ChakraPetch,
                    color = IronvellumColors.DangerRed,
                )
            }
            Spacer(Modifier.height(8.dp))
            // Same inline-confirm treatment as the account delete below: the
            // destructive step names exactly what it replaces before it runs.
            var confirmRestore by remember { mutableStateOf(false) }
            if (confirmRestore) {
                Text(
                    "This replaces EVERYTHING logged on this phone — trials, " +
                        "titles, techniques, stats and readings — with the cloud " +
                        "archive. Anything not in that archive is lost for good.",
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.DangerRed,
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    IronvellumButton(
                        label = "Replace my data",
                        onClick = {
                            confirmRestore = false
                            viewModel.restoreFromCloud()
                        },
                        enabled = !ui.busy,
                        modifier = Modifier.weight(1f),
                        danger = true,
                    )
                    IronvellumButton(
                        label = "Keep mine",
                        onClick = { confirmRestore = false },
                        enabled = !ui.busy,
                        quiet = true,
                    )
                }
            } else {
                TapRow(onClickLabel = "Restore from cloud", onClick = { confirmRestore = true }) {
                    Text(
                        "Restore from cloud",
                        style = MaterialTheme.typography.titleSmall,
                        fontFamily = ChakraPetch,
                        color = IronvellumColors.Ink,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = IronvellumColors.InkMuted,
                    )
                }
            }
            ui.lastRestore?.let { outcome ->
                Spacer(Modifier.height(8.dp))
                Text(
                    buildString {
                        append("Restored ${outcome.sessions} ${plural(outcome.sessions, "trial", "trials")} · ")
                        append("${outcome.sets} ${plural(outcome.sets, "set", "sets")} · ")
                        append("${outcome.titles} ${plural(outcome.titles, "title", "titles")}")
                        if (outcome.problems.isNotEmpty()) append(" · ${outcome.problems.size} skipped")
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = ChakraPetch,
                    color = if (outcome.problems.isEmpty()) IronvellumColors.Emerald else IronvellumColors.SovereignGold,
                )
            }
        }

        SettingsGroup("NOTIFICATIONS") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Ally activity",
                        style = MaterialTheme.typography.titleSmall,
                        fontFamily = ChakraPetch,
                        color = IronvellumColors.Ink,
                    )
                    Text(
                        "Requests, remarks and tributes",
                        style = MaterialTheme.typography.labelMedium,
                        color = IronvellumColors.InkMuted,
                    )
                }
                Box(Modifier.width(120.dp)) {
                    InkSegmented(
                        options = listOf("on" to "ON", "off" to "OFF"),
                        selected = if (allyAlerts) "on" else "off",
                        onPick = { pick ->
                            val on = pick == "on"
                            allyAlerts = on
                            InboxNotifier.setEnabled(context, on)
                            if (on && !InboxNotifier.hasPermission(context) &&
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                            ) {
                                askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                    )
                }
            }
            // ON while Android drops every post is the one state where the
            // switch lies: the grant, the app-wide toggle or the Allies channel
            // is off. Say which way out, not just that something is wrong.
            if (allyAlerts) {
                NotificationBlockedNotice(
                    channelId = Notifications.CHANNEL_ALLIES,
                    access = allyAccess,
                    blockedText = "Ally activity won't arrive until you allow notifications.",
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        SettingsGroup("PRIVACY") {
            var showBlocked by remember { mutableStateOf(false) }
            TapRow(
                onClickLabel = if (showBlocked) "Hide blocked Ironbound" else "Show blocked Ironbound",
                onClick = { showBlocked = !showBlocked },
            ) {
                Text(
                    "Blocked Ironbound",
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = ChakraPetch,
                    color = IronvellumColors.Ink,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    ui.blocked.size.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = ChakraPetch,
                    color = IronvellumColors.InkMuted,
                )
                Icon(
                    if (showBlocked) Icons.Outlined.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = IronvellumColors.InkMuted,
                )
            }
            if (showBlocked) {
                BlockedList(ui.blocked, viewModel::unblock)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Readings and private notes never leave this device.",
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
            )
        }

        Spacer(Modifier.height(24.dp))
        IronvellumButton(
            label = "Sign out",
            onClick = {
                // The next lifter must not inherit this one's notification or mark.
                InboxNotifier.reset(context)
                viewModel.signOut()
            },
            enabled = !ui.busy,
            quiet = true,
            modifier = Modifier.fillMaxWidth(),
        )

        // Withdrawing the data has to be reachable from inside the app: a
        // store listing that reads health data must offer deletion, and the
        // only other way out was to ask someone with database access.
        Spacer(Modifier.height(20.dp))
        Text(
            if (confirmDelete) {
                "This deletes your account, folio, synced trials, titles, allies " +
                    "and cloud backup for good, and signs you out. Training on this " +
                    "phone stays on this phone."
            } else {
                "Deletes your cloud account and everything synced. Your on-device training is untouched."
            },
            style = MaterialTheme.typography.bodySmall,
            color = IronvellumColors.InkMuted,
        )
        Spacer(Modifier.height(8.dp))
        if (confirmDelete) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IronvellumButton(
                    label = "Delete account",
                    onClick = {
                        confirmDelete = false
                        InboxNotifier.reset(context)
                        viewModel.deleteCloudData()
                    },
                    enabled = !ui.busy,
                    modifier = Modifier.weight(1f),
                    danger = true,
                )
                IronvellumButton(
                    label = "Keep it",
                    onClick = { confirmDelete = false },
                    enabled = !ui.busy,
                    quiet = true,
                )
            }
        } else {
            IronvellumButton(
                label = "Delete my cloud account",
                onClick = { confirmDelete = true },
                enabled = !ui.busy,
                quiet = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        ui.error?.let {
            Spacer(Modifier.height(8.dp))
            SocialErrorBanner(it)
        }
        Spacer(Modifier.height(28.dp))
    }

    if (editingName) {
        EditNameDialog(
            currentName = acct.displayName,
            error = ui.claimError,
            busy = ui.claimBusy,
            onSave = viewModel::claimName,
            onDismiss = { editingName = false },
        )
    }
}

/**
 * Rename through the same path as the claim-your-name panel, so the server's
 * refusal (taken, invalid) shows in its own words inside the dialog. The
 * dialog closes only when a save finished without an error: closing on click
 * hid a refused name behind a screen that still showed the old one.
 */
@Composable
private fun EditNameDialog(
    currentName: String,
    error: String?,
    busy: Boolean,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(currentName) }
    var submitted by remember { mutableStateOf(false) }
    // Mirror the server rule exactly: letters, digits, spaces; 2..24 trimmed.
    val cleaned = name.filter { it.isLetterOrDigit() || it == ' ' }.trim()
    val valid = cleaned.length in 2..24

    LaunchedEffect(busy) {
        if (submitted && !busy) {
            submitted = false
            if (error == null) onDismiss()
        }
    }

    AlertDialog(
        shape = MaterialTheme.shapes.medium,
        containerColor = Color(0xFF0D1110),
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("True name") },
        text = {
            Column {
                OutlinedTextField(
                    shape = MaterialTheme.shapes.small,
                    value = name,
                    onValueChange = { name = it.take(24) },
                    label = { Text("True name (2–24)") },
                    singleLine = true,
                    enabled = !busy,
                    isError = name.isNotBlank() && !valid,
                    supportingText = {
                        if (name.isNotBlank() && !valid) Text("2–24 characters, letters and numbers")
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = ChakraPetch,
                        color = IronvellumColors.DangerRed,
                    )
                }
            }
        },
        confirmButton = {
            IronvellumButton(
                label = "Save",
                onClick = {
                    submitted = true
                    onSave(cleaned)
                },
                enabled = valid && !busy,
            )
        },
        dismissButton = {
            IronvellumButton(label = "Cancel", onClick = onDismiss, enabled = !busy, quiet = true)
        },
    )
}

/**
 * Lifters this account blocked, with the way back. The name comes from the
 * block row itself: a blocked profile is unreadable, so it cannot be looked up.
 */
@Composable
private fun BlockedList(blocked: List<BlockedLifter>, onUnblock: (String) -> Unit) {
    if (blocked.isEmpty()) {
        Text(
            "Nobody blocked. Block an Ironbound from their folio.",
            style = MaterialTheme.typography.bodySmall,
            color = IronvellumColors.InkMuted,
            modifier = Modifier.padding(vertical = 4.dp),
        )
    }
    blocked.forEach { lifter ->
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                lifter.displayName.ifBlank { "Hidden Ironbound" },
                style = MaterialTheme.typography.titleSmall,
                fontFamily = ChakraPetch,
                color = IronvellumColors.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IronvellumButton(label = "Unblock", onClick = { onUnblock(lifter.userId) }, quiet = true)
        }
    }
}

/** Bytes to a short human label for the backup-freshness line. */
private fun formatBytes(bytes: Int): String =
    if (bytes < 1024) "$bytes B" else "%.1f KB".fmt(bytes / 1024f)
