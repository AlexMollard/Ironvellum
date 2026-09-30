package com.ironvellum.app.ui.social

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
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
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import com.ironvellum.app.domain.Titles
import com.ironvellum.app.domain.Warband
import com.ironvellum.app.domain.WarbandMember
import com.ironvellum.app.data.cloud.CloudSync
import com.ironvellum.app.data.cloud.AccountRepository
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.components.InkSpinner
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.components.plural
import com.ironvellum.app.ui.ironvellumAccount
import com.ironvellum.app.ui.ironvellumCloudSync
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.IronvellumTracking
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * One snapshot of the lifter's warband for the ALLIES tab.
 *
 * Week anchor: the server counts a member's workouts in the Monday-start week
 * of `date_trunc('week', now())` evaluated in UTC, so the header's "Week of"
 * date is derived from the same UTC Monday — using the device's local Monday
 * could label the band's week a day off for lifters west of UTC around the
 * rollover.
 */
data class WarbandUiState(
    val signedIn: Boolean = false,
    val warband: Warband? = null,
    val loading: Boolean = false,
    /** Failure of the band READ; the panel offers a retry. */
    val error: String? = null,
    /** A create/join/leave call in flight — the buttons wait for the server. */
    val actionBusy: Boolean = false,
    /** A server REFUSAL of the last action (full band, already in one, bad code) — inline, never a toast-only. */
    val actionError: String? = null,
)

class WarbandViewModel(
    private val cloudSync: CloudSync,
    accountRepo: AccountRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(WarbandUiState(signedIn = accountRepo.account.value != null))
    val ui = _ui.asStateFlow()

    private fun Throwable.reason(): String = message ?: this::class.simpleName ?: "Unknown failure"

    init {
        // Signing in happens on a sibling tab after this view model may already
        // exist, so a one-shot read of account.value would strand the section;
        // observe and load the moment a session appears.
        viewModelScope.launch {
            accountRepo.account.collect { account ->
                val was = _ui.value.signedIn
                _ui.value = _ui.value.copy(signedIn = account != null)
                when {
                    account != null && !was -> load()
                    account == null -> _ui.value = WarbandUiState(signedIn = false)
                }
            }
        }
        if (_ui.value.signedIn) load()
    }

    /** The band read is cached in CloudSync; mutations invalidate, so this stays honest. */
    fun load() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true, error = null)
            cloudSync.warband()
                .onSuccess { band -> _ui.value = _ui.value.copy(warband = band) }
                .onFailure { _ui.value = _ui.value.copy(error = it.reason()) }
            _ui.value = _ui.value.copy(loading = false)
        }
    }

    fun create(name: String) = act { cloudSync.createWarband(name) }

    fun join(code: String) = act { cloudSync.joinWarband(code) }

    fun leave() = act { cloudSync.leaveWarband() }

    // create/join return the band, leave returns Unit — the reconciliation
    // (a fresh warband read) is identical, so one runner takes either.
    private fun act(call: suspend () -> Result<*>) {
        if (_ui.value.actionBusy) return
        _ui.value = _ui.value.copy(actionBusy = true, actionError = null)
        viewModelScope.launch {
            call()
                .onSuccess {
                    // The server's answer is the truth: the refetch reconciles
                    // membership (and ownership after a handover) exactly.
                    cloudSync.warband(force = true).onSuccess { fresh ->
                        _ui.value = _ui.value.copy(warband = fresh)
                    }
                }
                .onFailure { _ui.value = _ui.value.copy(actionError = it.reason()) }
            _ui.value = _ui.value.copy(actionBusy = false)
        }
    }
}

/**
 * The WARBAND block of the ALLIES tab: the pitch and create/join when the
 * lifter is bandless, the band roster and its invite code when they are not.
 * 3-8 members is enforced server-side; refusals come back as [WarbandUiState.actionError]
 * and render inline under the buttons.
 */
@Composable
fun WarbandSection(
    onOpenLifter: (userId: String, displayName: String) -> Unit,
    viewModel: WarbandViewModel = viewModel(
        factory = viewModelFactory {
            initializer { WarbandViewModel(ironvellumCloudSync(), ironvellumAccount()) }
        },
    ),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    if (!ui.signedIn) return
    // Locals, not the delegated property: the leave dialog and the error
    // branch need a stable, smart-castable band/error for one composition.
    val band = ui.warband
    val loadError = ui.error
    val actionError = ui.actionError

    var showCreate by remember { mutableStateOf(false) }
    var showJoin by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }

    when {
        band == null && ui.loading -> InkPanel(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                InkSpinner()
                Text(
                    "Mustering the band…",
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = ChakraPetch,
                    color = IronvellumColors.InkMuted,
                )
            }
        }
        band == null && loadError != null -> Column(Modifier.fillMaxWidth()) {
            SocialErrorBanner(loadError)
            Spacer(Modifier.height(4.dp))
            SocialRefreshLink(onClick = viewModel::load, label = "Try again")
        }
        else -> {
            if (band == null) {
                WarbandPitch(
                    busy = ui.actionBusy,
                    error = actionError,
                    onCreate = { showCreate = true },
                    onJoin = { showJoin = true },
                )
            } else {
                WarbandRoster(
                    band = band,
                    busy = ui.actionBusy,
                    error = actionError,
                    onOpenLifter = onOpenLifter,
                    onLeave = { confirmLeave = true },
                )
            }
        }
    }

    if (showCreate) {
        CreateWarbandDialog(
            busy = ui.actionBusy,
            error = actionError,
            onCreate = viewModel::create,
            onDismiss = { showCreate = false },
        )
    }
    if (showJoin) {
        JoinWarbandDialog(
            busy = ui.actionBusy,
            error = actionError,
            onJoin = viewModel::join,
            onDismiss = { showJoin = false },
        )
    }
    if (confirmLeave) {
        AlertDialog(
            shape = MaterialTheme.shapes.medium,
            containerColor = Color(0xFF0D1110),
            onDismissRequest = { confirmLeave = false },
            title = { Text("Leave ${band?.name ?: "the band"}?") },
            text = {
                Text(
                    if ((band?.members?.size ?: 0) > 1) {
                        "You leave the band and its shared week. If you are the owner, the longest-standing remaining member takes over."
                    } else {
                        "You are the last member — leaving deletes the band and its code."
                    }
                )
            },
            confirmButton = {
                IronvellumButton(label = "Leave", onClick = {
                    confirmLeave = false
                    viewModel.leave()
                })
            },
            dismissButton = {
                IronvellumButton(label = "Stay", onClick = { confirmLeave = false }, quiet = true)
            },
        )
    }
}

@Composable
private fun WarbandPitch(
    busy: Boolean,
    error: String?,
    onCreate: () -> Unit,
    onJoin: () -> Unit,
) {
    InkPanel(Modifier.fillMaxWidth(), accent = IronvellumColors.SovereignGold) {
        Text(
            "WARBAND",
            style = MaterialTheme.typography.labelLarge,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = IronvellumColors.SovereignGold,
            letterSpacing = IronvellumTracking.InlineLabel,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Form a band of up to 8 — share one code, chase one week.",
            style = MaterialTheme.typography.bodyMedium,
            color = IronvellumColors.Ink,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            IronvellumButton(label = "Create", onClick = onCreate, enabled = !busy)
            IronvellumButton(label = "Join", onClick = onJoin, enabled = !busy, quiet = true)
        }
        InlineActionError(error)
    }
}

@Composable
private fun WarbandRoster(
    band: Warband,
    busy: Boolean,
    error: String?,
    onOpenLifter: (userId: String, displayName: String) -> Unit,
    onLeave: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    // The week header is the client summing the members' counts — the server
    // does not hand back a total.
    val total = band.members.sumOf { it.workoutsThisWeek }
    val monday = remember { weekMondayLabel() }

    InkPanel(Modifier.fillMaxWidth(), accent = IronvellumColors.Emerald) {
        Text(
            band.name,
            style = MaterialTheme.typography.titleMedium,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = IronvellumColors.Ink,
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "CODE ${band.code}",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = ChakraPetch,
                color = IronvellumColors.SystemGreen,
                letterSpacing = IronvellumTracking.InlineLabel,
            )
            RowAction("COPY", IronvellumColors.SystemGreen) {
                scope.launch {
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Ironvellum band code", band.code)))
                }
                Toast.makeText(context, "Band code copied", Toast.LENGTH_SHORT).show()
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Week of $monday · $total ${plural(total, "workout", "workouts")} across the band",
            style = MaterialTheme.typography.labelMedium,
            fontFamily = ChakraPetch,
            color = IronvellumColors.InkMuted,
            letterSpacing = IronvellumTracking.InlineLabel,
        )
        Spacer(Modifier.height(8.dp))
        // Members render oldest first (the server's order), the owner wears a mark.
        band.members.forEach { member ->
            WarbandMemberRow(member = member, isOwner = member.userId == band.ownerId, onOpenLifter = onOpenLifter)
        }
        Spacer(Modifier.height(12.dp))
        IronvellumButton(label = "Leave", onClick = onLeave, enabled = !busy, quiet = true)
        InlineActionError(error)
    }
}

@Composable
private fun WarbandMemberRow(
    member: WarbandMember,
    isOwner: Boolean,
    onOpenLifter: (userId: String, displayName: String) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IdentityRow(
            displayName = member.displayName,
            userId = member.userId,
            wornTitle = member.titleId?.let { Titles.byId(it)?.name },
            level = member.level,
            titleId = member.titleId,
            size = IdentitySize.Compact,
            onClick = { onOpenLifter(member.userId, member.displayName) },
            modifier = Modifier.weight(1f),
        )
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (member.workoutsThisWeek >= 1) {
                    Icon(
                        Icons.Outlined.CheckCircle,
                        contentDescription = "Trained this week",
                        tint = IronvellumColors.SystemGreen,
                        modifier = Modifier.size(14.dp),
                    )
                }
                Text(
                    "${member.workoutsThisWeek} this week",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = ChakraPetch,
                    color = if (member.workoutsThisWeek >= 1) IronvellumColors.SystemGreen else IronvellumColors.InkMuted,
                )
            }
            member.lastWorkoutAtMs?.let { ms ->
                Text(
                    relativeTime(ms),
                    style = MaterialTheme.typography.labelSmall,
                    color = IronvellumColors.InkMuted,
                )
            }
            if (isOwner) {
                Text(
                    "OWNER",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = ChakraPetch,
                    color = IronvellumColors.SovereignGold,
                    letterSpacing = IronvellumTracking.InlineLabel,
                )
            }
        }
    }
}

@Composable
private fun CreateWarbandDialog(
    busy: Boolean,
    error: String?,
    onCreate: (name: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    // Mirror the server check exactly: 1..24 characters, no length games.
    val cleaned = name.trim()
    val valid = cleaned.length in 1..24

    AlertDialog(
        shape = MaterialTheme.shapes.medium,
        containerColor = Color(0xFF0D1110),
        onDismissRequest = onDismiss,
        title = { Text("Create a band") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    shape = MaterialTheme.shapes.small,
                    value = name,
                    onValueChange = { name = it.take(24) },
                    label = { Text("Band name (1–24)") },
                    singleLine = true,
                    enabled = !busy,
                    isError = name.isNotBlank() && !valid,
                    supportingText = {
                        if (name.isNotBlank() && !valid) {
                            Text("1–24 characters")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                InlineActionError(error)
            }
        },
        confirmButton = {
            IronvellumButton(
                label = "Create",
                onClick = { onCreate(cleaned) },
                enabled = valid && !busy,
            )
        },
        dismissButton = {
            IronvellumButton(label = "Cancel", onClick = onDismiss, quiet = true, enabled = !busy)
        },
    )
}

/**
 * Join by invite code. The field is uppercase and capped at 8; the code is
 * paste-friendly — the usual path is "copy the code in the chat app, switch
 * here", so the clipboard is read in an effect (Android only hands it to a
 * focused window) and an unambiguous-looking 8-character run pre-fills.
 */
@Composable
private fun JoinWarbandDialog(
    busy: Boolean,
    error: String?,
    onJoin: (code: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var code by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (code.isBlank()) {
            val clip = runCatching {
                context.getSystemService(ClipboardManager::class.java)
                    ?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
            }.getOrNull()
            clip?.let { text ->
                CODE_CANDIDATE.findAll(text.uppercase()).firstOrNull()?.let { code = it.value }
            }
        }
    }

    val valid = code.length == 8

    AlertDialog(
        shape = MaterialTheme.shapes.medium,
        containerColor = Color(0xFF0D1110),
        onDismissRequest = onDismiss,
        title = { Text("Join a band") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    shape = MaterialTheme.shapes.small,
                    value = code,
                    onValueChange = { raw ->
                        code = raw.uppercase().filter { it.isLetterOrDigit() }.take(8)
                    },
                    label = { Text("Invite code (8 characters)") },
                    singleLine = true,
                    enabled = !busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth(),
                )
                InlineActionError(error)
            }
        },
        confirmButton = {
            IronvellumButton(
                label = "Join",
                onClick = { onJoin(code) },
                enabled = valid && !busy,
            )
        },
        dismissButton = {
            IronvellumButton(label = "Cancel", onClick = onDismiss, quiet = true, enabled = !busy)
        },
    )
}

/** The server's alphabet is unambiguous (no 0/O/1/I/L), so a run of 8 of those is a code. */
private val CODE_CANDIDATE = Regex("[2-9A-HJ-NP-Z]{8}")

/** The UTC Monday the band's week started on, in the header's short form. */
internal fun weekMondayLabel(): String {
    val monday = LocalDate.now(ZoneOffset.UTC).with(DayOfWeek.MONDAY)
    return monday.format(java.time.format.DateTimeFormatter.ofPattern("MMM d"))
}

/** The inline refusal line under the create/join/leave controls. */
@Composable
private fun InlineActionError(error: String?) {
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
