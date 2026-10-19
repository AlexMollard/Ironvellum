package com.ironvellum.app.ui.social

import androidx.compose.ui.res.stringResource
import com.ironvellum.app.R
import com.ironvellum.app.data.Words
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.ui.asString
import com.ironvellum.app.ui.components.PushedHeader
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.sp
import com.ironvellum.app.ui.components.InkDivider
import com.ironvellum.app.ui.components.ListRowHeight
import com.ironvellum.app.ui.components.LocalRowPadding
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
import com.ironvellum.app.ui.components.IronvellumDialog
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
import com.ironvellum.app.ui.ironvellumAccount
import com.ironvellum.app.ui.ironvellumCloudSync
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.IronvellumTracking
import com.ironvellum.app.ui.theme.ironvellumFieldColors
import java.text.DateFormat
import java.util.Date

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
    var confirmSignOut by remember { mutableStateOf(false) }
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
        PushedHeader(stringResource(R.string.acct_title_upper), onBack)

        SettingsGroup(stringResource(R.string.acct_section_profile), rows = true) {
            TapRow(onClickLabel = UiText.themed(R.string.acct_edit_name_description, R.string.acct_edit_name_description__t).asString(), onClick = { editingName = true }) {
                Column(Modifier.weight(1f)) {
                    Text(
                        acct.displayName,
                        style = MaterialTheme.typography.titleMedium,
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
                    stringResource(R.string.acct_edit_button),
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = ChakraPetch,
                    fontWeight = FontWeight.SemiBold,
                    color = IronvellumColors.SystemGreen,
                    letterSpacing = 0.5.sp,
                )
            }
            InkDivider()
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Spacer(Modifier.height(12.dp))
                // Wire values stay public/friends/private; only the labels differ.
                InkSegmented(
                    options = listOf(
                        "public" to stringResource(R.string.acct_visibility_public),
                        "friends" to UiText.themed(R.string.acct_visibility_friends, R.string.acct_visibility_friends__t).asString(),
                        "private" to stringResource(R.string.acct_visibility_private),
                    ),
                    selected = acct.visibility,
                    onPick = viewModel::setVisibility,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    when (acct.visibility) {
                        "public" -> UiText.themed(R.string.acct_visibility_public_caption, R.string.acct_visibility_public_caption__t).asString()
                        "friends" -> UiText.themed(R.string.acct_visibility_friends_caption, R.string.acct_visibility_friends_caption__t).asString()
                        else -> UiText.themed(R.string.acct_visibility_private_caption, R.string.acct_visibility_private_caption__t).asString()
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = IronvellumColors.InkMuted,
                )
            }
            InkDivider()
            // One row, one switch: the whole row toggles.
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = ListRowHeight)
                    .toggleable(
                        value = allyAlerts,
                        role = Role.Switch,
                        onValueChange = { on ->
                            allyAlerts = on
                            InboxNotifier.setEnabled(context, on)
                            if (on && !InboxNotifier.hasPermission(context) &&
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                            ) {
                                askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                    )
                    .padding(LocalRowPadding.current),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(UiText.themed(R.string.acct_alerts_label, R.string.acct_alerts_label__t).asString(), style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.Ink)
                    Text(
                        UiText.themed(R.string.acct_alerts_caption, R.string.acct_alerts_caption__t).asString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = IronvellumColors.InkMuted,
                    )
                }
                Switch(
                    checked = allyAlerts,
                    onCheckedChange = null,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = IronvellumColors.Ink,
                        checkedTrackColor = IronvellumColors.SystemGreen,
                        checkedBorderColor = IronvellumColors.SystemGreen,
                        uncheckedThumbColor = IronvellumColors.InkMuted,
                        uncheckedTrackColor = IronvellumColors.Abyss,
                        uncheckedBorderColor = IronvellumColors.Rune,
                    ),
                )
            }
            // On while Android drops every post is the one state where the
            // switch lies: the grant, the app-wide toggle or the Allies channel
            // is off. Say which way out, not just that something is wrong.
            if (allyAlerts) {
                NotificationBlockedNotice(
                    channelId = Notifications.CHANNEL_ALLIES,
                    access = allyAccess,
                    blockedText = UiText.themed(R.string.acct_alerts_blocked, R.string.acct_alerts_blocked__t).asString(),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        SettingsGroup(stringResource(R.string.acct_section_cloud), rows = true) {
            val outcome = ui.lastSync
            ActionRow(
                label = stringResource(R.string.acct_sync_label),
                subline = if (outcome == null) {
                    UiText.themed(R.string.acct_sync_caption, R.string.acct_sync_caption__t).asString()
                } else {
                    syncSummary(false, outcome.sessions, outcome.sets, outcome.titles, outcome.problems.size)
                },
                enabled = !ui.busy,
                onClick = viewModel::syncNow,
            )
            if (outcome != null && outcome.problems.isNotEmpty()) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    outcome.problems.forEach { problem ->
                        Text(
                            "· ${problem.asString()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = IronvellumColors.InkMuted,
                        )
                    }
                }
            }
            InkDivider()
            // Freshness must be visible without tapping anything: a lifter has
            // to tell at a glance whether they are protected.
            ActionRow(
                label = stringResource(R.string.acct_backup_label),
                subline = ui.lastBackup?.let {
                    DateFormat.getDateTimeInstance().format(Date(it.atMs)) + " · " + formatBytes(it.bytes)
                } ?: stringResource(R.string.acct_backup_none),
                enabled = !ui.busy,
                onClick = viewModel::backUpNow,
            )
            // Next to the row that failed: a refused backup elsewhere on the
            // screen read as a button that simply did nothing.
            ui.backupError?.let {
                Text(
                    it.asString(),
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = ChakraPetch,
                    color = IronvellumColors.DangerRed,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            InkDivider()
            // Same inline-confirm treatment as the account delete below: the
            // destructive step names exactly what it replaces before it runs.
            var confirmRestore by remember { mutableStateOf(false) }
            if (confirmRestore) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        UiText.themed(R.string.acct_restore_warning, R.string.acct_restore_warning__t).asString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = IronvellumColors.DangerRed,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        IronvellumButton(
                            label = stringResource(R.string.acct_restore_confirm_button),
                            onClick = {
                                confirmRestore = false
                                viewModel.restoreFromCloud()
                            },
                            enabled = !ui.busy,
                            modifier = Modifier.weight(1f),
                            danger = true,
                        )
                        IronvellumButton(
                            label = stringResource(R.string.acct_restore_keep_button),
                            onClick = { confirmRestore = false },
                            enabled = !ui.busy,
                            quiet = true,
                        )
                    }
                }
            } else {
                ActionRow(label = stringResource(R.string.acct_restore_label), onClick = { confirmRestore = true })
            }
            ui.lastRestore?.let { outcome ->
                Text(
                    syncSummary(true, outcome.sessions, outcome.sets, outcome.titles, outcome.problems.size),
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = ChakraPetch,
                    color = IronvellumColors.InkMuted,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        SettingsGroup(stringResource(R.string.acct_section_privacy), rows = true) {
            var showBlocked by remember { mutableStateOf(false) }
            TapRow(
                onClickLabel = if (showBlocked) UiText.themed(R.string.acct_blocked_hide_description, R.string.acct_blocked_hide_description__t).asString() else UiText.themed(R.string.acct_blocked_show_description, R.string.acct_blocked_show_description__t).asString(),
                onClick = { showBlocked = !showBlocked },
            ) {
                Text(
                    UiText.themed(R.string.acct_blocked_label, R.string.acct_blocked_label__t).asString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = IronvellumColors.Ink,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    ui.blocked.size.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = IronvellumColors.InkMuted,
                )
                Icon(
                    if (showBlocked) Icons.Outlined.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = IronvellumColors.InkMuted,
                )
            }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                if (showBlocked) {
                    BlockedList(ui.blocked, viewModel::unblock)
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    UiText.themed(R.string.acct_private_note, R.string.acct_private_note__t).asString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                )
            }
        }

        // Withdrawing the data has to be reachable from inside the app: a
        // store listing that reads health data must offer deletion, and the
        // only other way out was to ask someone with database access.
        SettingsGroup(null, rows = true) {
            ActionRow(
                label = stringResource(R.string.acct_signout_label),
                enabled = !ui.busy,
                onClick = { confirmSignOut = true },
            )
            InkDivider()
            if (confirmDelete) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        UiText.themed(R.string.acct_delete_warning, R.string.acct_delete_warning__t).asString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = IronvellumColors.DangerRed,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        IronvellumButton(
                            label = stringResource(R.string.acct_delete_confirm_button),
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
                            label = stringResource(R.string.acct_delete_keep_button),
                            onClick = { confirmDelete = false },
                            enabled = !ui.busy,
                            quiet = true,
                        )
                    }
                }
            } else {
                ActionRow(
                    label = stringResource(R.string.acct_delete_label),
                    subline = stringResource(R.string.acct_delete_caption),
                    labelColor = IronvellumColors.DangerRed,
                    enabled = !ui.busy,
                    onClick = { confirmDelete = true },
                )
            }
        }
        if (confirmSignOut) {
            IronvellumDialog(
                onDismissRequest = { confirmSignOut = false },
                title = { Text(stringResource(R.string.acct_signout_dialog_title)) },
                text = { Text(stringResource(R.string.acct_signout_dialog_text)) },
                confirmButton = {
                    IronvellumButton(
                        label = stringResource(R.string.acct_signout_label),
                        onClick = {
                            confirmSignOut = false
                            // The next lifter must not inherit this one's notification or mark.
                            InboxNotifier.reset(context)
                            viewModel.signOut()
                        },
                    )
                },
                dismissButton = {
                    IronvellumButton(label = stringResource(R.string.common_cancel), onClick = { confirmSignOut = false }, quiet = true)
                },
            )
        }
        Spacer(Modifier.height(8.dp))
        ui.error?.let {
            Spacer(Modifier.height(8.dp))
            SocialErrorBanner(it.asString())
        }
        Spacer(Modifier.height(28.dp))
    }

    if (editingName) {
        EditNameDialog(
            currentName = acct.displayName,
            error = ui.claimError?.asString(),
            busy = ui.claimBusy,
            onSave = viewModel::claimName,
            onDismiss = { editingName = false },
        )
    }
}

/**
 * One tappable account row: the [label] in `bodyMedium`, an optional InkMuted [subline],
 * 52dp tall and no chevron because it acts rather than opens. [labelColor] is DangerRed for a
 * destructive row; a disabled row reads InkMuted.
 */
@Composable
private fun ActionRow(
    label: String,
    subline: String? = null,
    enabled: Boolean = true,
    labelColor: Color = IronvellumColors.Ink,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = ListRowHeight)
            .clickable(enabled = enabled, onClickLabel = label, role = Role.Button, onClick = onClick)
            .padding(LocalRowPadding.current),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) labelColor else IronvellumColors.InkMuted,
            )
            if (subline != null) {
                Text(subline, style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted)
            }
        }
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

    IronvellumDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(Words.trueName.c) },
        text = {
            Column {
                OutlinedTextField(
                    shape = MaterialTheme.shapes.small,
                    colors = ironvellumFieldColors(),
                    value = name,
                    onValueChange = { name = it.take(24) },
                    label = { Text(UiText.themed(R.string.acct_claim_field_label, R.string.acct_claim_field_label__t).asString()) },
                    singleLine = true,
                    enabled = !busy,
                    isError = name.isNotBlank() && !valid,
                    supportingText = {
                        if (name.isNotBlank() && !valid) Text(stringResource(R.string.acct_name_rule))
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
                label = stringResource(R.string.common_save),
                onClick = {
                    submitted = true
                    onSave(cleaned)
                },
                enabled = valid && !busy,
            )
        },
        dismissButton = {
            IronvellumButton(label = stringResource(R.string.common_cancel), onClick = onDismiss, enabled = !busy, quiet = true)
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
            UiText.themed(R.string.acct_blocked_empty, R.string.acct_blocked_empty__t).asString(),
            style = MaterialTheme.typography.bodySmall,
            color = IronvellumColors.InkMuted,
            modifier = Modifier.padding(vertical = 4.dp),
        )
    }
    blocked.forEach { lifter ->
        Row(
            Modifier.fillMaxWidth().heightIn(min = ListRowHeight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                lifter.displayName.ifBlank { UiText.themed(R.string.cloud_hidden_lifter, R.string.cloud_hidden_lifter__t).asString() },
                style = MaterialTheme.typography.bodyMedium,
                color = IronvellumColors.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IronvellumButton(label = stringResource(R.string.acct_unblock_button), onClick = { onUnblock(lifter.userId) }, quiet = true)
        }
    }
}

/** Bytes to a short human label for the backup-freshness line. */
private fun formatBytes(bytes: Int): String =
    if (bytes < 1024) Words.text(UiText.res(R.string.acct_size_bytes, bytes))
    else Words.text(UiText.res(R.string.acct_size_kb, bytes / 1024f))

/** "Pushed 3 workouts · 12 sets · 2 titles · 1 skipped": the result line under Sync and under Restore. */
@Composable
private fun syncSummary(restored: Boolean, sessions: Int, sets: Int, titles: Int, problems: Int): String {
    val head = if (restored) {
        UiText.themedPlural(R.plurals.acct_sync_restored, R.plurals.acct_sync_restored__t, sessions)
    } else {
        UiText.themedPlural(R.plurals.acct_sync_pushed, R.plurals.acct_sync_pushed__t, sessions)
    }
    val parts = mutableListOf(
        head.asString(),
        UiText.plural(R.plurals.common_sets_count, sets).asString(),
        UiText.plural(R.plurals.acct_titles_count, titles).asString(),
    )
    if (problems > 0) parts += UiText.plural(R.plurals.acct_sync_skipped, problems).asString()
    return parts.joinToString(" · ")
}
