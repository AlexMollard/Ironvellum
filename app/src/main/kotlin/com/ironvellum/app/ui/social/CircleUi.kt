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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import android.os.Build
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.ironvellum.app.domain.Titles
import com.ironvellum.app.domain.Circle
import com.ironvellum.app.domain.CircleMember
import com.ironvellum.app.domain.extractInviteCode
import com.ironvellum.app.data.CircleBonusPaid
import com.ironvellum.app.data.CircleGateway
import com.ironvellum.app.data.CircleRead
import com.ironvellum.app.data.CloudCircleGateway
import com.ironvellum.app.ui.components.Achievement
import com.ironvellum.app.ui.components.AchievementOverlay
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.components.InkRail
import com.ironvellum.app.ui.components.InkSpinner
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.components.plural
import com.ironvellum.app.ui.ironvellumAccount
import com.ironvellum.app.ui.ironvellumCloudSync
import com.ironvellum.app.ui.ironvellumCircleBonus
import com.ironvellum.app.ui.program.TapPad
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.IronvellumTracking
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * One snapshot of the lifter's circle for the ALLIES tab.
 *
 * Week anchor: the server's week is Monday-start in UTC and closes at the next
 * UTC Monday 00:00. The reset is shown in the lifter's own clock
 * ([circleResetLabel]), so it never reads a day off for lifters west of UTC.
 */
data class CircleUiState(
    val signedIn: Boolean = false,
    val circle: Circle? = null,
    /** The signed-in lifter's id — decides who sees the goal EDIT affordance. */
    val myUserId: String? = null,
    val loading: Boolean = false,
    /** Failure of the circle READ; the panel offers a retry. */
    val error: String? = null,
    /** A reload that failed while a circle was already on screen: the roster shown is the last good read. */
    val refreshFailed: Boolean = false,
    /** A create/join/leave call in flight — the buttons wait for the server. */
    val actionBusy: Boolean = false,
    /** A server REFUSAL of the last action (full circle, already in one, bad code) — inline, never a toast-only. */
    val actionError: String? = null,
    /** The bonus just paid for a settled week — shown once, then cleared. */
    val paid: CircleBonusPaid? = null,
)

class CircleViewModel(
    private val gateway: CircleGateway,
) : ViewModel() {

    private val _ui = MutableStateFlow(CircleUiState(signedIn = gateway.currentUserId() != null, myUserId = gateway.currentUserId()))
    val ui = _ui.asStateFlow()

    private fun Throwable.reason(): String = message ?: this::class.simpleName ?: "Unknown failure"

    init {
        // Signing in happens on a sibling tab after this view model may already
        // exist, so a one-shot read of the account would strand the section;
        // observe and load the moment a session appears.
        viewModelScope.launch {
            gateway.userId.collect { userId ->
                val was = _ui.value.signedIn
                _ui.value = _ui.value.copy(signedIn = userId != null, myUserId = userId)
                when {
                    userId != null && !was -> load()
                    userId == null -> _ui.value = CircleUiState(signedIn = false)
                }
            }
        }
        if (_ui.value.signedIn) load()
    }

    /**
     * Reads the circle. [force] skips the 30s read cache: pull-to-refresh and
     * coming back to the app ask for it, so a roster is never frozen at its
     * first load. A read already in flight is not doubled up.
     */
    fun load(force: Boolean = false) {
        if (_ui.value.loading) return
        _ui.value = _ui.value.copy(loading = true, error = null)
        viewModelScope.launch {
            gateway.read(force)
                .onSuccess { read -> onRead(read) }
                .onFailure { failure ->
                    // Over a circle already on screen a failed reload must say so:
                    // the roster is then the last good read, not the current one.
                    _ui.value = _ui.value.copy(
                        error = failure.reason(),
                        refreshFailed = _ui.value.circle != null,
                    )
                }
            _ui.value = _ui.value.copy(loading = false)
        }
    }

    /**
     * Records the circle; any bonus the read paid shows once through
     * [CircleUiState.paid]. The pay itself happens in the gateway, so
     * every circle read settles it, not only this screen's.
     */
    private fun onRead(read: CircleRead) {
        _ui.value = _ui.value.copy(
            circle = read.circle,
            error = null,
            refreshFailed = false,
            paid = read.paid ?: _ui.value.paid,
        )
    }

    fun setGoal(perMember: Int, onDone: () -> Unit = {}) = act(onDone) { gateway.setGoal(perMember) }

    fun create(name: String, onDone: () -> Unit = {}) = act(onDone) { gateway.create(name) }

    fun join(code: String, onDone: () -> Unit = {}) = act(onDone) { gateway.join(code) }

    fun leave() = act { gateway.leave() }

    fun rename(name: String, onDone: () -> Unit = {}) = act(onDone) { gateway.rename(name) }

    fun newCode(onDone: () -> Unit = {}) = act(onDone) { gateway.rotateCode() }

    fun remove(userId: String, onDone: () -> Unit = {}) = act(onDone) { gateway.removeMember(userId) }

    // create/join return the circle, leave returns Unit — the reconciliation
    // (a fresh circle read) is identical, so one runner takes either.
    // [onDone] runs only when the server accepted the action, so a dialog
    // stays open, with its inline error, when the answer is a refusal.
    private fun act(onDone: () -> Unit = {}, call: suspend () -> Result<*>) {
        if (_ui.value.actionBusy) return
        _ui.value = _ui.value.copy(actionBusy = true, actionError = null)
        viewModelScope.launch {
            call()
                .onSuccess {
                    onDone()
                    // The server's answer is the truth: the refetch reconciles
                    // membership (and ownership after a handover) exactly. A
                    // failed refetch must not leave the pre-action roster up
                    // as if the action never happened.
                    gateway.read(force = true)
                        .onSuccess { read -> onRead(read) }
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
        _ui.value = _ui.value.copy(paid = null)
    }
}

/**
 * The CIRCLE block of the ALLIES tab: the pitch and create/join when the
 * lifter is circle-less, the circle roster and its invite code when they are not.
 * 2-8 members is enforced server-side; refusals come back as [CircleUiState.actionError]
 * and render inline under the buttons.
 */
@Composable
fun CircleSection(
    onOpenLifter: (userId: String, displayName: String) -> Unit,
    /** Bumped by the host's pull-to-refresh; each bump forces a fresh circle read. */
    refreshSignal: Int = 0,
    viewModel: CircleViewModel? = null,
) {
    // The caller may inject a view model (previews, tests); the default is
    // built over the app-scoped cloud objects.
    val vm = viewModel ?: viewModel(
        factory = viewModelFactory {
            initializer {
                CircleViewModel(
                    CloudCircleGateway(ironvellumCloudSync(), ironvellumAccount(), ironvellumCircleBonus()),
                )
            }
        },
    )
    val ui by vm.ui.collectAsStateWithLifecycle()
    // Coming back to the app reads the circle again (the read cache keeps a
    // quick flip cheap), and a pull on the host forces one.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.load() }
    LaunchedEffect(refreshSignal) {
        if (refreshSignal > 0) vm.load(force = true)
    }
    if (!ui.signedIn) return
    // Locals, not the delegated property: the leave dialog and the error
    // branch need a stable, smart-castable circle/error for one composition.
    val circle = ui.circle
    val loadError = ui.error
    val actionError = ui.actionError

    var showCreate by rememberSaveable { mutableStateOf(false) }
    var showJoin by rememberSaveable { mutableStateOf(false) }
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    var showGoalEditor by rememberSaveable { mutableStateOf(false) }
    var showRename by rememberSaveable { mutableStateOf(false) }
    var confirmRotate by rememberSaveable { mutableStateOf(false) }
    var removeId by rememberSaveable { mutableStateOf<String?>(null) }

    when {
        circle == null && ui.loading -> InkPanel(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                InkSpinner()
                Text(
                    "Reading the circle…",
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = ChakraPetch,
                    color = IronvellumColors.InkMuted,
                )
            }
        }
        circle == null && loadError != null -> Column(Modifier.fillMaxWidth()) {
            SocialErrorBanner(loadError)
            Spacer(Modifier.height(4.dp))
            SocialRefreshLink(onClick = vm::load, label = "Try again")
        }
        else -> {
            if (circle == null) {
                CirclePitch(
                    busy = ui.actionBusy,
                    error = actionError,
                    onCreate = { showCreate = true },
                    onJoin = { showJoin = true },
                )
            } else {
                CircleRoster(
                    circle = circle,
                    refreshFailed = ui.refreshFailed,
                    busy = ui.actionBusy,
                    error = actionError,
                    isOwner = circle.ownerId == ui.myUserId,
                    onOpenLifter = onOpenLifter,
                    onLeave = { confirmLeave = true },
                    onEditGoal = { showGoalEditor = true },
                    onRename = { showRename = true },
                    onRotate = { confirmRotate = true },
                    onRemove = { removeId = it },
                )
            }
        }
    }

    if (showCreate) {
        CircleNameDialog(
            title = "Form a circle",
            confirmLabel = "Create",
            initial = "",
            busy = ui.actionBusy,
            error = actionError,
            onConfirm = { name -> vm.create(name) { showCreate = false } },
            onDismiss = {
                vm.dismissActionError()
                showCreate = false
            },
        )
    }
    if (showRename && circle != null) {
        CircleNameDialog(
            title = "Rename the circle",
            confirmLabel = "Save",
            initial = circle.name,
            busy = ui.actionBusy,
            error = actionError,
            onConfirm = { name -> vm.rename(name) { showRename = false } },
            onDismiss = {
                vm.dismissActionError()
                showRename = false
            },
        )
    }
    if (confirmRotate) {
        AlertDialog(
            shape = MaterialTheme.shapes.medium,
            containerColor = Color(0xFF0D1110),
            onDismissRequest = { confirmRotate = false },
            title = { Text("New circle code?") },
            text = {
                Text(
                    "The current code stops working at once. Members stay; anyone holding " +
                        "the old code can no longer join.",
                )
            },
            confirmButton = {
                IronvellumButton(label = "New code", enabled = !ui.actionBusy, onClick = {
                    vm.newCode { confirmRotate = false }
                })
            },
            dismissButton = {
                IronvellumButton(label = "Keep", onClick = { confirmRotate = false }, quiet = true)
            },
        )
    }
    val removing = circle?.members?.firstOrNull { it.userId == removeId }
    if (removing != null) {
        AlertDialog(
            shape = MaterialTheme.shapes.medium,
            containerColor = Color(0xFF0D1110),
            onDismissRequest = { removeId = null },
            title = { Text("Remove ${removing.displayName}?") },
            text = {
                Text(
                    "They are told, and their days this week still count. They can rejoin " +
                        "with the code unless you change it first.",
                )
            },
            confirmButton = {
                IronvellumButton(label = "Remove", enabled = !ui.actionBusy, onClick = {
                    vm.remove(removing.userId) { removeId = null }
                })
            },
            dismissButton = {
                IronvellumButton(label = "Keep", onClick = { removeId = null }, quiet = true)
            },
        )
    }
    if (showJoin) {
        JoinCircleDialog(
            busy = ui.actionBusy,
            error = actionError,
            onJoin = { code -> vm.join(code) { showJoin = false } },
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
            title = { Text("Leave ${circle?.name ?: "the circle"}?") },
            text = {
                Text(
                    if ((circle?.members?.size ?: 0) > 1) {
                        "You leave the circle and its shared week. If you are the Keeper, the longest-standing remaining member takes over."
                    } else {
                        "You are the last member — leaving deletes the circle and its code."
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
    if (showGoalEditor && circle != null) {
        GoalEditorDialog(
            busy = ui.actionBusy,
            error = actionError,
            perMember = circle.pendingPerMember ?: circle.perMember,
            // Closes only once the server accepted it: a refusal stays on
            // the dialog, in its inline error.
            onSave = { perMember -> vm.setGoal(perMember) { showGoalEditor = false } },
            onDismiss = {
                vm.dismissActionError()
                showGoalEditor = false
            },
        )
    }
    ui.paid?.let { paid ->
        AchievementOverlay(
            items = listOf(
                Achievement(
                    banner = "CIRCLE'S GOAL MET",
                    name = paid.circleName ?: circle?.name ?: "The circle",
                    tagline = "The circle met its weekly goal and you carried your share.",
                    xp = paid.xp,
                ),
            ),
            onDone = vm::dismissPayout,
        )
    }
}

@Composable
private fun CirclePitch(
    busy: Boolean,
    error: String?,
    onCreate: () -> Unit,
    onJoin: () -> Unit,
) {
    InkPanel(Modifier.fillMaxWidth(), accent = IronvellumColors.SovereignGold) {
        Text(
            "YOUR CIRCLE",
            style = MaterialTheme.typography.labelLarge,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = IronvellumColors.SovereignGold,
            letterSpacing = IronvellumTracking.InlineLabel,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Form a circle of up to 8 allies — share one code, chase one week.",
            style = MaterialTheme.typography.bodyMedium,
            color = IronvellumColors.Ink,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            IronvellumButton(label = "Form a circle", onClick = onCreate, enabled = !busy)
            IronvellumButton(label = "Join a circle", onClick = onJoin, enabled = !busy, quiet = true)
        }
        InlineActionError(error)
    }
}

@Composable
private fun CircleRoster(
    circle: Circle,
    refreshFailed: Boolean,
    busy: Boolean,
    error: String?,
    isOwner: Boolean,
    onOpenLifter: (userId: String, displayName: String) -> Unit,
    onLeave: () -> Unit,
    onEditGoal: () -> Unit,
    onRename: () -> Unit,
    onRotate: () -> Unit,
    onRemove: (userId: String) -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    // The server's canonical total: summing what the viewer was handed could
    // differ from the next member's sum and split GOAL MET between phones.
    val total = circle.total
    // Goal 0: fewer than two members were on the roster when the week opened,
    // so there is nothing to meet yet (a warm-up week).
    val goal = circle.goal
    val hasGoal = goal > 0
    val met = hasGoal && total >= goal
    val resets = remember { circleResetLabel(Instant.now(), ZoneId.systemDefault(), Locale.getDefault()) }

    InkPanel(Modifier.fillMaxWidth(), accent = IronvellumColors.Emerald) {
        Text(
            circle.name,
            style = MaterialTheme.typography.titleMedium,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = IronvellumColors.Ink,
        )
        if (refreshFailed) {
            // The roster below is the last good read; say so rather than let a
            // stale week pass for the current one.
            Spacer(Modifier.height(4.dp))
            Text(
                "Could not refresh — pull down to try again.",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = ChakraPetch,
                color = IronvellumColors.InkMuted,
            )
        }
        Spacer(Modifier.height(6.dp))
        // The weekly challenge: the circle's progress toward the owner's goal.
        // At goal the rail reads full and the mark turns gold — the moment the
        // payout overlay fires for contributors.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            InkRail(
                fraction = if (hasGoal) (total.toFloat() / goal).coerceIn(0f, 1f) else 0f,
                modifier = Modifier.weight(1f),
                height = 8.dp,
                seed = circle.id.hashCode(),
            )
            Text(
                if (met) "GOAL MET" else if (hasGoal) "$total / $goal this week" else "WARM-UP WEEK",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.Bold,
                color = if (met) IronvellumColors.SovereignGold else IronvellumColors.InkMuted,
                letterSpacing = IronvellumTracking.InlineLabel,
            )
            if (isOwner) {
                // 44dp minimum touch target via TapPad; the label is announced
                // with the current goal so a screen reader hears what it edits.
                TapPad("EDIT", "Change the weekly goal, currently ${circle.perMember} days each") { onEditGoal() }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "CIRCLE CODE ${circle.code}",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = ChakraPetch,
                color = IronvellumColors.SystemGreen,
                letterSpacing = IronvellumTracking.InlineLabel,
            )
            RowAction("COPY", IronvellumColors.SystemGreen) {
                scope.launch {
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Ironvellum circle code", circle.code)))
                }
                // Android 13+ shows its own confirmation; a second toast would double it.
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    Toast.makeText(context, "Circle code copied", Toast.LENGTH_SHORT).show()
                }
            }
            RowAction("SHARE", IronvellumColors.SystemGreen) {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(
                        Intent.EXTRA_TEXT,
                        "Join my Ironvellum circle ${circle.name} — code ${circle.code}",
                    )
                }
                context.startActivity(Intent.createChooser(intent, "Share circle"))
            }
        }
        if (isOwner) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                RowAction("RENAME", IronvellumColors.SystemGreen, onRename)
                RowAction("NEW CODE", IronvellumColors.SystemGreen, onRotate)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            buildString {
                append("$total ${plural(total, "day", "days")} trained")
                if (hasGoal) append(" · ${circle.perMember} each")
                circle.pendingPerMember?.let { append(" · $it from next week") }
                append(" · weeks met: ${circle.weeksMet}")
                append(" · resets $resets")
            },
            style = MaterialTheme.typography.labelMedium,
            fontFamily = ChakraPetch,
            color = IronvellumColors.InkMuted,
            letterSpacing = IronvellumTracking.InlineLabel,
        )
        Spacer(Modifier.height(8.dp))
        // Members render oldest first (the server's order), the owner wears a mark.
        circle.members.forEach { member ->
            CircleMemberRow(
                member = member,
                isOwner = member.userId == circle.ownerId,
                perMember = circle.perMember,
                onOpenLifter = onOpenLifter,
                onRemove = if (isOwner && member.userId != circle.ownerId) {
                    { onRemove(member.userId) }
                } else {
                    null
                },
            )
        }
        Spacer(Modifier.height(12.dp))
        IronvellumButton(label = "Leave", onClick = onLeave, enabled = !busy, quiet = true)
        InlineActionError(error)
    }
}

@Composable
private fun CircleMemberRow(
    member: CircleMember,
    isOwner: Boolean,
    perMember: Int,
    onOpenLifter: (userId: String, displayName: String) -> Unit,
    onRemove: (() -> Unit)?,
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
            // A member who has not trained shows nothing: a row of zeroes is
            // guilt, not information. A check marks those who have.
            if (member.daysThisWeek >= 1) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(
                        Icons.Outlined.CheckCircle,
                        contentDescription = "Trained this week",
                        tint = IronvellumColors.SystemGreen,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        "${member.daysThisWeek} of $perMember",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = ChakraPetch,
                        color = IronvellumColors.SystemGreen,
                    )
                }
            }
            // The member's share of the week's goal, on the same rail language
            // as the circle's own progress — a slim stroke, not a second counter.
            InkRail(
                fraction = (member.daysThisWeek.toFloat() / perMember).coerceIn(0f, 1f),
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
                    "KEEPER",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = ChakraPetch,
                    color = IronvellumColors.SovereignGold,
                    letterSpacing = IronvellumTracking.InlineLabel,
                )
            }
            onRemove?.let { RowAction("REMOVE", IronvellumColors.DangerRed, it) }
        }
    }
}

/** Forms or renames a circle: one name field, mirroring the server's 1-24 check. */
@Composable
private fun CircleNameDialog(
    title: String,
    confirmLabel: String,
    initial: String,
    busy: Boolean,
    error: String?,
    onConfirm: (name: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial) }
    // Mirror the server check exactly: 1..24 characters, no length games.
    val cleaned = name.trim()
    val valid = cleaned.length in 1..24

    AlertDialog(
        shape = MaterialTheme.shapes.medium,
        containerColor = Color(0xFF0D1110),
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    shape = MaterialTheme.shapes.small,
                    value = name,
                    onValueChange = { name = it.take(24) },
                    label = { Text("Circle name (1–24)") },
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
                label = confirmLabel,
                onClick = { onConfirm(cleaned) },
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
private fun JoinCircleDialog(
    busy: Boolean,
    error: String?,
    onJoin: (code: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var code by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (code.isBlank()) {
            val clip = runCatching {
                context.getSystemService(ClipboardManager::class.java)
                    ?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
            }.getOrNull()
            clip?.let { text -> extractInviteCode(text)?.let { code = it } }
        }
    }

    val valid = code.length == 8

    AlertDialog(
        shape = MaterialTheme.shapes.medium,
        containerColor = Color(0xFF0D1110),
        onDismissRequest = onDismiss,
        title = { Text("Join a circle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    shape = MaterialTheme.shapes.small,
                    value = code,
                    onValueChange = { raw ->
                        code = raw.uppercase().filter { it.isLetterOrDigit() }.take(8)
                    },
                    label = { Text("Circle code (8 characters)") },
                    singleLine = true,
                    enabled = !busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Your days count toward the circle's goal from next Monday.",
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
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
 * The Keeper's weekly-goal editor: a 1–7 stepper, no free-text field, so the
 * value can never leave the server's accepted range. A refused save comes
 * back through [error] and renders inline, like the other circle dialogs.
 */
@Composable
private fun GoalEditorDialog(
    busy: Boolean,
    error: String?,
    perMember: Int,
    onSave: (perMember: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by rememberSaveable { mutableStateOf(perMember) }

    AlertDialog(
        shape = MaterialTheme.shapes.medium,
        containerColor = Color(0xFF0D1110),
        onDismissRequest = onDismiss,
        title = { Text("Weekly goal per member") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Days each member aims to train per week. A change starts next Monday.",
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    TapPad("−", "Lower the weekly goal, currently $value", minSize = 48.dp) {
                        value = (value - 1).coerceIn(GOAL_RANGE.first, GOAL_RANGE.last)
                    }
                    Text(
                        "$value",
                        style = MaterialTheme.typography.headlineSmall,
                        fontFamily = ChakraPetch,
                        fontWeight = FontWeight.Bold,
                        color = IronvellumColors.Ink,
                        modifier = Modifier
                            .widthIn(min = 48.dp)
                            // Announced as it changes, so a screen reader hears the new goal.
                            .semantics {
                                liveRegion = LiveRegionMode.Polite
                                contentDescription = "$value ${plural(value, "day", "days")} per week"
                            },
                        textAlign = TextAlign.Center,
                    )
                    TapPad("+", "Raise the weekly goal, currently $value", minSize = 48.dp) {
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

/** The server's accepted goal range (CloudSync.setCircleGoal mirrors it). */
private val GOAL_RANGE = Circle.PER_MEMBER_RANGE

/**
 * When the circle's week closes, in the lifter's own clock. The server's week
 * ends at the next UTC Monday 00:00, which is a different weekday and hour
 * almost everywhere else on earth.
 */
internal fun circleResetLabel(now: Instant, zone: ZoneId, locale: Locale): String {
    val nextMonday = now.atZone(ZoneOffset.UTC).toLocalDate().with(TemporalAdjusters.next(DayOfWeek.MONDAY))
    return nextMonday.atStartOfDay(ZoneOffset.UTC)
        .withZoneSameInstant(zone)
        .format(DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", locale))
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
