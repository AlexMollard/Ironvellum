package com.ironvellum.app.ui.social

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.res.stringResource
import com.ironvellum.app.R
import com.ironvellum.app.data.Lines
import com.ironvellum.app.data.Words
import com.ironvellum.app.data.reasonText
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.ui.asString
import com.ironvellum.app.ui.components.IronvellumDialog
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
import com.ironvellum.app.ui.components.CelebrationPage
import com.ironvellum.app.ui.components.AchievementOverlay
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.components.InkDivider
import com.ironvellum.app.ui.components.ListRow
import com.ironvellum.app.ui.components.ListRowHeight
import com.ironvellum.app.ui.components.InkSpinner
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.ironvellumAccount
import com.ironvellum.app.ui.ironvellumCloudSync
import com.ironvellum.app.ui.ironvellumCircleBonus
import com.ironvellum.app.ui.program.TapPad
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.IronvellumTracking
import com.ironvellum.app.ui.theme.ironvellumFieldColors
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
    val error: UiText? = null,
    /** A reload that failed while a circle was already on screen: the roster shown is the last good read. */
    val refreshFailed: Boolean = false,
    /** A create/join/leave call in flight — the buttons wait for the server. */
    val actionBusy: Boolean = false,
    /** A server REFUSAL of the last action (full circle, already in one, bad code) — inline, never a toast-only. */
    val actionError: UiText? = null,
    /** The bonus just paid for a settled week — shown once, then cleared. */
    val paid: CircleBonusPaid? = null,
)

class CircleViewModel(
    private val gateway: CircleGateway,
) : ViewModel() {

    private val _ui = MutableStateFlow(CircleUiState(signedIn = gateway.currentUserId() != null, myUserId = gateway.currentUserId()))
    val ui = _ui.asStateFlow()

    private fun Throwable.reason(): UiText = reasonText(UiText.res(R.string.circle_unknown_failure))

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
                .onFailure { failure ->
                    _ui.value = _ui.value.copy(actionError = failure.reason())
                    // A refusal usually means the roster on screen is behind: the
                    // keys were passed on, or the member already left. Read it again
                    // so the screen shows what the server holds; the refusal stays.
                    gateway.read(force = true).onSuccess { read -> onRead(read) }
                }
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
    val loadError = ui.error?.asString()
    val actionError = ui.actionError?.asString()

    var showManage by rememberSaveable { mutableStateOf(false) }
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
                    UiText.themed(R.string.circle_loading, R.string.circle_loading__t).asString(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = ChakraPetch,
                    color = IronvellumColors.InkMuted,
                )
            }
        }
        circle == null && loadError != null -> Column(Modifier.fillMaxWidth()) {
            SocialErrorBanner(loadError)
            Spacer(Modifier.height(4.dp))
            SocialRefreshLink(onClick = vm::load, label = stringResource(R.string.circle_try_again))
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
                    error = actionError,
                    myUserId = ui.myUserId,
                    isOwner = circle.ownerId == ui.myUserId,
                    onOpenLifter = onOpenLifter,
                    onManage = { showManage = true },
                    onRemove = { removeId = it },
                )
            }
        }
    }

    if (showManage && circle != null) {
        ManageCircleSheet(
            circle = circle,
            isOwner = circle.ownerId == ui.myUserId,
            onDismiss = { showManage = false },
            onRename = { showManage = false; showRename = true },
            onRotate = { showManage = false; confirmRotate = true },
            onEditGoal = { showManage = false; showGoalEditor = true },
            onLeave = { showManage = false; confirmLeave = true },
        )
    }
    if (showCreate) {
        CircleNameDialog(
            title = UiText.themed(R.string.circle_form_button, R.string.circle_form_button__t).asString(),
            confirmLabel = stringResource(R.string.circle_create_button),
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
            title = UiText.themed(R.string.circle_rename_title, R.string.circle_rename_title__t).asString(),
            confirmLabel = stringResource(R.string.common_save),
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
        IronvellumDialog(
            onDismissRequest = {
                vm.dismissActionError()
                confirmRotate = false
            },
            title = { Text(UiText.themed(R.string.circle_rotate_title, R.string.circle_rotate_title__t).asString()) },
            text = {
                Column {
                    Text(stringResource(R.string.circle_rotate_body))
                    InlineActionError(actionError)
                }
            },
            confirmButton = {
                IronvellumButton(label = stringResource(R.string.circle_new_code_button), enabled = !ui.actionBusy, onClick = {
                    vm.newCode { confirmRotate = false }
                })
            },
            dismissButton = {
                IronvellumButton(label = stringResource(R.string.circle_keep_button), onClick = {
                    vm.dismissActionError()
                    confirmRotate = false
                }, quiet = true)
            },
        )
    }
    val removing = circle?.members?.firstOrNull { it.userId == removeId }
    if (removing != null) {
        IronvellumDialog(
            onDismissRequest = {
                vm.dismissActionError()
                removeId = null
            },
            title = { Text(stringResource(R.string.circle_remove_title, removing.displayName)) },
            text = {
                Column {
                    Text(stringResource(R.string.circle_remove_body))
                    InlineActionError(actionError)
                }
            },
            confirmButton = {
                IronvellumButton(label = stringResource(R.string.circle_remove_button), enabled = !ui.actionBusy, danger = true, onClick = {
                    vm.remove(removing.userId) { removeId = null }
                })
            },
            dismissButton = {
                IronvellumButton(label = stringResource(R.string.circle_keep_button), onClick = {
                    vm.dismissActionError()
                    removeId = null
                }, quiet = true)
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
        IronvellumDialog(
            onDismissRequest = { confirmLeave = false },
            title = {
                Text(
                    circle?.name?.let { stringResource(R.string.circle_leave_title, it) }
                        ?: UiText.themed(R.string.circle_leave_unnamed_title, R.string.circle_leave_unnamed_title__t).asString(),
                )
            },
            text = {
                Text(
                    if ((circle?.members?.size ?: 0) > 1) {
                        UiText.themed(R.string.circle_leave_body, R.string.circle_leave_body__t).asString()
                    } else {
                        UiText.themed(R.string.circle_leave_last_body, R.string.circle_leave_last_body__t).asString()
                    }
                )
            },
            confirmButton = {
                IronvellumButton(label = stringResource(R.string.circle_leave_button), danger = true, onClick = {
                    confirmLeave = false
                    vm.leave()
                })
            },
            dismissButton = {
                IronvellumButton(label = stringResource(R.string.circle_stay_button), onClick = { confirmLeave = false }, quiet = true)
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
            pages = listOf(CelebrationPage.GoalMet(name = paid.circleName ?: circle?.name ?: UiText.themed(R.string.circle_paid_name_fallback, R.string.circle_paid_name_fallback__t).asString(), xp = paid.xp)),
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
    InkPanel(Modifier.fillMaxWidth()) {
        Text(
            UiText.themed(R.string.circle_pitch, R.string.circle_pitch__t).asString(),
            style = MaterialTheme.typography.bodyMedium,
            color = IronvellumColors.Ink,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            IronvellumButton(label = UiText.themed(R.string.circle_form_button, R.string.circle_form_button__t).asString(), onClick = onCreate, enabled = !busy)
            IronvellumButton(label = UiText.themed(R.string.circle_join_button, R.string.circle_join_button__t).asString(), onClick = onJoin, enabled = !busy, quiet = true)
        }
        InlineActionError(error)
    }
}

@Composable
private fun CircleRoster(
    circle: Circle,
    refreshFailed: Boolean,
    error: String?,
    myUserId: String?,
    isOwner: Boolean,
    onOpenLifter: (userId: String, displayName: String) -> Unit,
    onManage: () -> Unit,
    onRemove: (userId: String) -> Unit,
) {
    val context = LocalContext.current

    // The server's canonical total: summing what the viewer was handed could
    // differ from the next member's sum and split GOAL MET between phones.
    val total = circle.total
    // Goal 0: fewer than two members were on the roster when the week opened,
    // so there is nothing to meet yet (a warm-up week).
    val goal = circle.goal
    val hasGoal = goal > 0
    val met = hasGoal && total >= goal

    InkPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    circle.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = ChakraPetch,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = IronvellumColors.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                // The weekly challenge: the circle's progress toward the owner's goal.
                // Gold only once it is met, the moment the payout overlay fires.
                Text(
                    if (met) {
                        stringResource(R.string.circle_goal_met)
                    } else if (hasGoal) {
                        stringResource(R.string.circle_progress_week, total, goal)
                    } else {
                        stringResource(R.string.circle_warmup_week)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontSize = 13.sp,
                    color = if (met) IronvellumColors.SovereignGold else IronvellumColors.InkMuted,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            if (refreshFailed) {
                // The roster below is the last good read; say so rather than let a
                // stale week pass for the current one.
                Spacer(Modifier.height(4.dp))
                Text(
                    Lines.rosterStale,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = ChakraPetch,
                    color = IronvellumColors.InkMuted,
                )
            }
            if (hasGoal) {
                Spacer(Modifier.height(10.dp))
                SegmentedRail(done = total, segments = goal)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The text takes what SHARE leaves and wraps, so at 360dp or a
                // large font scale the action is never squeezed out of the row.
                Text(
                    stringResource(R.string.circle_code_label, circle.code),
                    style = MaterialTheme.typography.labelMedium,
                    color = IronvellumColors.InkMuted,
                    modifier = Modifier.weight(1f),
                )
                RowAction(
                    stringResource(R.string.common_share),
                    IronvellumColors.SystemGreen,
                    contentDescription = UiText.themed(R.string.circle_share_code_description, R.string.circle_share_code_description__t).asString(),
                ) {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(
                            Intent.EXTRA_TEXT,
                            UiText.themed(R.string.circle_share_text, R.string.circle_share_text__t, circle.name, circle.code).asString(context),
                        )
                    }
                    context.startActivity(Intent.createChooser(intent, UiText.themed(R.string.circle_share_chooser_title, R.string.circle_share_chooser_title__t).asString(context)))
                }
            }
        }
        // Members render oldest first (the server's order), the owner wears a mark.
        circle.members.forEach { member ->
            InkDivider()
            CircleMemberRow(
                member = member,
                isMe = member.userId == myUserId,
                isKeeper = member.userId == circle.ownerId,
                perMember = circle.perMember,
                onOpenLifter = onOpenLifter,
                onRemove = if (isOwner && member.userId != circle.ownerId) {
                    { onRemove(member.userId) }
                } else {
                    null
                },
            )
        }
        InkDivider()
        ListRow(
            label = UiText.themed(R.string.circle_manage_label, R.string.circle_manage_label__t).asString(),
            value = stringResource(if (isOwner) R.string.circle_manage_value_owner else R.string.circle_manage_value_member),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            onClick = onManage,
        )
        InlineActionError(error)
    }
}

/**
 * The week's goal as one straight rail cut into [segments] days: [done] of them
 * Emerald, the rest Rune. Past 20 segments the gaps shrink so each stays visible.
 */
@Composable
private fun SegmentedRail(done: Int, segments: Int, modifier: Modifier = Modifier) {
    val count = segments.coerceAtLeast(1)
    val filled = done.coerceIn(0, count)
    val railDescription = UiText.themedPlural(
        R.plurals.circle_goal_rail_description,
        R.plurals.circle_goal_rail_description__t,
        count,
        filled,
        count,
    ).asString()
    Canvas(
        modifier
            .fillMaxWidth()
            .height(4.dp)
            .semantics {
                contentDescription = railDescription
                progressBarRangeInfo = ProgressBarRangeInfo(filled.toFloat(), 0f..count.toFloat())
            },
    ) {
        val gap = (if (count > 20) 1.dp else 3.dp).toPx()
        val width = (size.width - gap * (count - 1)) / count
        for (i in 0 until count) {
            drawRect(
                color = if (i < filled) IronvellumColors.Emerald else IronvellumColors.Rune,
                topLeft = Offset(i * (width + gap), 0f),
                size = Size(width, size.height),
            )
        }
    }
}

@Composable
private fun CircleMemberRow(
    member: CircleMember,
    isMe: Boolean,
    isKeeper: Boolean,
    perMember: Int,
    onOpenLifter: (userId: String, displayName: String) -> Unit,
    onRemove: (() -> Unit)?,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { onOpenLifter(member.userId, member.displayName) }
            .heightIn(min = 48.dp)
            .padding(start = 16.dp, end = if (onRemove != null) 8.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LifterAvatar(
            displayName = member.displayName,
            size = 32.dp,
            frameId = member.crestId,
            ring = if (isMe) AvatarRing.Own else AvatarRing.Iron,
        )
        val role = listOfNotNull(stringResource(R.string.circle_member_you).takeIf { isMe }, Words.keeper.c.takeIf { isKeeper }).joinToString(" · ")
        // The name and the role share one line, the role small and muted.
        Text(
            buildAnnotatedString {
                append(member.displayName.ifBlank { Words.lifter.c })
                if (role.isNotEmpty()) {
                    withStyle(SpanStyle(color = IronvellumColors.InkMuted, fontSize = 12.sp)) { append("   $role") }
                }
            },
            style = MaterialTheme.typography.bodyLarge,
            color = IronvellumColors.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        // A member who has not trained shows nothing: a row of zeroes is
        // guilt, not information. A tick marks those who met the week's goal.
        if (member.daysThisWeek >= 1) {
            Text(
                stringResource(R.string.circle_member_days, member.daysThisWeek, perMember),
                style = MaterialTheme.typography.labelMedium,
                color = IronvellumColors.InkMuted,
            )
            if (member.daysThisWeek >= perMember) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = stringResource(R.string.circle_goal_met_description),
                    tint = IronvellumColors.Emerald,
                    modifier = Modifier.size(16.dp),
                )
            } else {
                // Keeps "n of n" in one column whether or not the tick shows, as the mockup does.
                Spacer(Modifier.width(16.dp))
            }
        }
        onRemove?.let {
            RowAction(
                stringResource(R.string.circle_remove_button),
                IronvellumColors.DangerRed,
                contentDescription = UiText.themed(
                    R.string.circle_remove_member_description,
                    R.string.circle_remove_member_description__t,
                    member.displayName,
                ).asString(),
                onClick = it,
            )
        }
    }
}

/**
 * Everything that changes the circle itself, behind one row: the Keeper renames
 * it, makes a new code or edits the goal; anyone can leave. The week's facts the
 * roster no longer carries (weeks met, reset time) ride here as one muted line.
 */
@Composable
private fun ManageCircleSheet(
    circle: Circle,
    isOwner: Boolean,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onRotate: () -> Unit,
    onEditGoal: () -> Unit,
    onLeave: () -> Unit,
) {
    val resets = remember { circleResetLabel(Instant.now(), ZoneId.systemDefault(), Locale.getDefault()) }
    IronvellumDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.circle_manage_title, circle.name)) },
        text = {
            Column {
                Text(
                    buildList {
                        add(UiText.plural(R.plurals.circle_manage_each, circle.perMember).asString())
                        circle.pendingPerMember?.let { add(UiText.res(R.string.circle_manage_pending, it).asString()) }
                        add(stringResource(R.string.circle_manage_weeks_met, circle.weeksMet))
                        add(stringResource(R.string.circle_manage_resets, resets))
                    }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                )
                if (isOwner) {
                    Spacer(Modifier.height(8.dp))
                    ListRow(UiText.themed(R.string.circle_rename_title, R.string.circle_rename_title__t).asString(), onClick = onRename)
                    ListRow(stringResource(R.string.circle_new_code_button), onClick = onRotate)
                    ListRow(
                        stringResource(R.string.circle_weekly_goal_label),
                        value = UiText.plural(R.plurals.common_days_count, circle.perMember).asString(),
                        onClick = onEditGoal,
                    )
                }
            }
        },
        confirmButton = { IronvellumButton(label = stringResource(R.string.common_done), onClick = onDismiss) },
        dismissButton = {
            IronvellumButton(
                label = UiText.themed(R.string.circle_leave_circle_button, R.string.circle_leave_circle_button__t).asString(),
                onClick = onLeave,
                danger = true,
            )
        },
    )
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

    IronvellumDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    shape = MaterialTheme.shapes.small,
                    colors = ironvellumFieldColors(),
                    value = name,
                    onValueChange = { name = it.take(24) },
                    label = { Text(UiText.themed(R.string.circle_name_label, R.string.circle_name_label__t).asString()) },
                    singleLine = true,
                    enabled = !busy,
                    isError = name.isNotBlank() && !valid,
                    supportingText = {
                        if (name.isNotBlank() && !valid) {
                            Text(stringResource(R.string.circle_name_error))
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
            IronvellumButton(label = stringResource(R.string.common_cancel), onClick = onDismiss, quiet = true, enabled = !busy)
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

    IronvellumDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(UiText.themed(R.string.circle_join_button, R.string.circle_join_button__t).asString()) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    shape = MaterialTheme.shapes.small,
                    colors = ironvellumFieldColors(),
                    value = code,
                    onValueChange = { raw ->
                        code = raw.uppercase().filter { it.isLetterOrDigit() }.take(8)
                    },
                    label = { Text(UiText.themed(R.string.circle_code_field_label, R.string.circle_code_field_label__t).asString()) },
                    singleLine = true,
                    enabled = !busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    UiText.themed(R.string.circle_join_hint, R.string.circle_join_hint__t).asString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                )
                InlineActionError(error)
            }
        },
        confirmButton = {
            IronvellumButton(
                label = stringResource(R.string.circle_join_confirm),
                onClick = { onJoin(code) },
                enabled = valid && !busy,
            )
        },
        dismissButton = {
            IronvellumButton(label = stringResource(R.string.common_cancel), onClick = onDismiss, quiet = true, enabled = !busy)
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
    var value by rememberSaveable { mutableIntStateOf(perMember) }
    val valueDescription = UiText.plural(R.plurals.circle_goal_value_description, value).asString()

    IronvellumDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.circle_goal_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.circle_goal_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    TapPad("−", stringResource(R.string.circle_goal_lower_description, value), minSize = 48.dp) {
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
                                contentDescription = valueDescription
                            },
                        textAlign = TextAlign.Center,
                    )
                    TapPad("+", stringResource(R.string.circle_goal_raise_description, value), minSize = 48.dp) {
                        value = (value + 1).coerceIn(GOAL_RANGE.first, GOAL_RANGE.last)
                    }
                }
                InlineActionError(error)
            }
        },
        confirmButton = {
            IronvellumButton(
                label = stringResource(R.string.common_save),
                onClick = { onSave(value) },
                enabled = !busy,
            )
        },
        dismissButton = {
            IronvellumButton(label = stringResource(R.string.common_cancel), onClick = onDismiss, quiet = true, enabled = !busy)
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
