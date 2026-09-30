package com.ironvellum.app.ui.social

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.text.style.TextAlign
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
import android.content.Intent
import android.widget.Toast
import com.ironvellum.app.domain.Titles
import com.ironvellum.app.domain.Warband
import com.ironvellum.app.domain.WarbandMember
import com.ironvellum.app.data.Repository
import com.ironvellum.app.data.WarbandGoalPayoutStore
import com.ironvellum.app.data.cloud.CloudSync
import com.ironvellum.app.data.cloud.AccountRepository
import com.ironvellum.app.ui.components.Achievement
import com.ironvellum.app.ui.components.AchievementOverlay
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.components.InkRail
import com.ironvellum.app.ui.components.InkSpinner
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.components.plural
import com.ironvellum.app.ui.ironvellumAccount
import com.ironvellum.app.ui.ironvellumCloudSync
import com.ironvellum.app.ui.ironvellumRepository
import com.ironvellum.app.ui.program.TapPad
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
    /** The signed-in lifter's id — decides who sees the goal EDIT affordance. */
    val myUserId: String? = null,
    val loading: Boolean = false,
    /** Failure of the band READ; the panel offers a retry. */
    val error: String? = null,
    /** A create/join/leave call in flight — the buttons wait for the server. */
    val actionBusy: Boolean = false,
    /** A server REFUSAL of the last action (full band, already in one, bad code) — inline, never a toast-only. */
    val actionError: String? = null,
    /** XP just paid for the band's weekly goal — shown once, then cleared. */
    val payoutXp: Int? = null,
)

class WarbandViewModel(
    private val cloudSync: CloudSync,
    private val accountRepo: AccountRepository,
    private val repository: Repository,
    private val payoutStore: WarbandGoalPayoutStore,
) : ViewModel() {

    private val _ui = MutableStateFlow(WarbandUiState(signedIn = accountRepo.account.value != null, myUserId = accountRepo.account.value?.userId))
    val ui = _ui.asStateFlow()

    private fun Throwable.reason(): String = message ?: this::class.simpleName ?: "Unknown failure"

    init {
        // Signing in happens on a sibling tab after this view model may already
        // exist, so a one-shot read of account.value would strand the section;
        // observe and load the moment a session appears.
        viewModelScope.launch {
            accountRepo.account.collect { account ->
                val was = _ui.value.signedIn
                _ui.value = _ui.value.copy(signedIn = account != null, myUserId = account?.userId)
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
                .onSuccess { band -> onBand(band) }
                .onFailure { _ui.value = _ui.value.copy(error = it.reason()) }
            _ui.value = _ui.value.copy(loading = false)
        }
    }

    /**
     * Records the band and, when this lifter contributed to a met weekly goal,
     * pays the once-per-band+week bonus; the overlay reads [WarbandUiState.payoutXp].
     */
    private suspend fun onBand(band: Warband?) {
        _ui.value = _ui.value.copy(warband = band)
        val me = band?.members?.firstOrNull { it.userId == _ui.value.myUserId } ?: return
        repository.maybePayBandGoalBonus(band, me.userId, payoutStore)?.let { xp ->
            _ui.value = _ui.value.copy(payoutXp = xp)
        }
    }

    fun setGoal(goal: Int) = act { cloudSync.setWarbandGoal(goal) }

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
                    // membership (and ownership after a handover) exactly. A
                    // failed refetch must not leave the pre-action roster up
                    // as if the action never happened.
                    cloudSync.warband(force = true)
                        .onSuccess { fresh -> onBand(fresh) }
                        .onFailure { _ui.value = _ui.value.copy(actionError = it.reason()) }
                }
                .onFailure { _ui.value = _ui.value.copy(actionError = it.reason()) }
            _ui.value = _ui.value.copy(actionBusy = false)
        }
    }

    /** The dialogs' refusals are transient context: dismissing them clears the message. */
    fun dismissActionError() {
        _ui.value = _ui.value.copy(actionError = null)
    }

    /** The payout overlay is a one-shot moment: dismissed once acknowledged. */
    fun dismissPayout() {
        _ui.value = _ui.value.copy(payoutXp = null)
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
    viewModel: WarbandViewModel? = null,
) {
    val app = LocalContext.current.applicationContext
    // The store needs an application context, which a CreationExtras factory
    // cannot see — the caller may inject a view model (previews, tests), so
    // the default is built here where the context is composable-readable.
    val vm = viewModel ?: viewModel(
        factory = viewModelFactory {
            initializer {
                WarbandViewModel(
                    ironvellumCloudSync(),
                    ironvellumAccount(),
                    ironvellumRepository(),
                    WarbandGoalPayoutStore.from(app),
                )
            }
        },
    )
    val ui by vm.ui.collectAsStateWithLifecycle()
    if (!ui.signedIn) return
    // Locals, not the delegated property: the leave dialog and the error
    // branch need a stable, smart-castable band/error for one composition.
    val band = ui.warband
    val loadError = ui.error
    val actionError = ui.actionError

    var showCreate by remember { mutableStateOf(false) }
    var showJoin by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var showGoalEditor by remember { mutableStateOf(false) }

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
            SocialRefreshLink(onClick = vm::load, label = "Try again")
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
                    isOwner = band.ownerId == ui.myUserId,
                    onOpenLifter = onOpenLifter,
                    onLeave = { confirmLeave = true },
                    onEditGoal = { showGoalEditor = true },
                )
            }
        }
    }

    if (showCreate) {
        CreateWarbandDialog(
            busy = ui.actionBusy,
            error = actionError,
            onCreate = vm::create,
            onDismiss = {
                vm.dismissActionError()
                showCreate = false
            },
        )
    }
    if (showJoin) {
        JoinWarbandDialog(
            busy = ui.actionBusy,
            error = actionError,
            onJoin = vm::join,
            onDismiss = {
                vm.dismissActionError()
                showJoin = false
            },
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
                    vm.leave()
                })
            },
            dismissButton = {
                IronvellumButton(label = "Stay", onClick = { confirmLeave = false }, quiet = true)
            },
        )
    }
    if (showGoalEditor && band != null) {
        GoalEditorDialog(
            busy = ui.actionBusy,
            error = actionError,
            goal = band.weeklyGoal,
            onSave = {
                showGoalEditor = false
                vm.setGoal(it)
            },
            onDismiss = {
                vm.dismissActionError()
                showGoalEditor = false
            },
        )
    }
    ui.payoutXp?.let { xp ->
        AchievementOverlay(
            items = listOf(
                Achievement(
                    banner = "BAND GOAL MET",
                    name = band?.name ?: "The band",
                    tagline = "The band hit its weekly goal and you pulled your weight.",
                    xp = xp,
                ),
            ),
            onDone = vm::dismissPayout,
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
    isOwner: Boolean,
    onOpenLifter: (userId: String, displayName: String) -> Unit,
    onLeave: () -> Unit,
    onEditGoal: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    // The week header is the client summing the members' counts — the server
    // does not hand back a total.
    val total = band.members.sumOf { it.workoutsThisWeek }
    // Goal absent (an older server answer) falls back to the Warband default silently.
    val goal = band.weeklyGoal.coerceAtLeast(1)
    val met = total >= goal
    val monday = remember { weekMondayLabel() }

    InkPanel(Modifier.fillMaxWidth(), accent = IronvellumColors.Emerald) {
        Text(
            band.name,
            style = MaterialTheme.typography.titleMedium,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = IronvellumColors.Ink,
        )
        Spacer(Modifier.height(6.dp))
        // The weekly challenge: the band's progress toward the owner's goal.
        // At goal the rail reads full and the mark turns gold — the moment the
        // payout overlay fires for contributors.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            InkRail(
                fraction = (total.toFloat() / goal).coerceIn(0f, 1f),
                modifier = Modifier.weight(1f),
                height = 8.dp,
                seed = band.id.hashCode(),
            )
            Text(
                if (met) "GOAL MET" else "$total / $goal this week",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.Bold,
                color = if (met) IronvellumColors.SovereignGold else IronvellumColors.InkMuted,
                letterSpacing = IronvellumTracking.InlineLabel,
            )
            if (isOwner) {
                // 44dp minimum touch target via TapPad; the label is announced
                // with the current goal so a screen reader hears what it edits.
                TapPad("EDIT", "Change the weekly goal, currently $goal") { onEditGoal() }
            }
        }
        Spacer(Modifier.height(8.dp))
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
            RowAction("SHARE", IronvellumColors.SystemGreen) {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(
                        Intent.EXTRA_TEXT,
                        "Join my Ironvellum band ${band.name} — code ${band.code}",
                    )
                }
                context.startActivity(Intent.createChooser(intent, "Share band"))
            }
        }
        Spacer(Modifier.height(8.dp))
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
            WarbandMemberRow(
                member = member,
                isOwner = member.userId == band.ownerId,
                goal = goal,
                onOpenLifter = onOpenLifter,
            )
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
    goal: Int,
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
            // The member's share of the week's goal, on the same rail language
            // as the band's own progress — a slim stroke, not a second counter.
            InkRail(
                fraction = (member.workoutsThisWeek.toFloat() / goal).coerceIn(0f, 1f),
                modifier = Modifier.width(96.dp),
                height = 3.dp,
                seed = member.userId.hashCode(),
            )
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

/**
 * The owner's weekly-goal editor: a 5–50 stepper, no free-text field, so the
 * value can never leave the server's accepted range. A refused save comes
 * back through [error] and renders inline, like the other band dialogs.
 */
@Composable
private fun GoalEditorDialog(
    busy: Boolean,
    error: String?,
    goal: Int,
    onSave: (goal: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(goal) }

    AlertDialog(
        shape = MaterialTheme.shapes.medium,
        containerColor = Color(0xFF0D1110),
        onDismissRequest = onDismiss,
        title = { Text("Weekly goal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Workouts the whole band aims for this week.",
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    TapPad("−", "Lower the weekly goal, currently $value") {
                        value = (value - 1).coerceIn(GOAL_RANGE.first, GOAL_RANGE.last)
                    }
                    Text(
                        "$value",
                        style = MaterialTheme.typography.headlineSmall,
                        fontFamily = ChakraPetch,
                        fontWeight = FontWeight.Bold,
                        color = IronvellumColors.Ink,
                        modifier = Modifier.widthIn(min = 48.dp),
                        textAlign = TextAlign.Center,
                    )
                    TapPad("+", "Raise the weekly goal, currently $value") {
                        value = (value + 1).coerceIn(GOAL_RANGE.first, GOAL_RANGE.last)
                    }
                }
                InlineActionError(error)
            }
        },
        confirmButton = {
            IronvellumButton(
                label = "Save",
                onClick = { onSave(value) },
                enabled = !busy,
            )
        },
        dismissButton = {
            IronvellumButton(label = "Cancel", onClick = onDismiss, quiet = true, enabled = !busy)
        },
    )
}

/** The server's accepted goal range (CloudSync.setWarbandGoal mirrors it). */
private val GOAL_RANGE = 5..50

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
