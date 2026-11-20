package com.ironvellum.app.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.ui.res.stringResource
import com.ironvellum.app.IronvellumApp
import com.ironvellum.app.R
import com.ironvellum.app.data.Lines
import com.ironvellum.app.data.InboxNotifier
import com.ironvellum.app.data.Words
import com.ironvellum.app.data.reasonText
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.ui.asString
import com.ironvellum.app.data.cloud.Cloud
import com.ironvellum.app.data.cloud.Inbox
import io.github.jan.supabase.SupabaseClient
import com.ironvellum.app.data.cloud.CircleChange
import com.ironvellum.app.data.cloud.InboxItem
import com.ironvellum.app.ui.components.InkChip
import com.ironvellum.app.ui.components.InkDivider
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.components.SectionHeader
import com.ironvellum.app.ui.components.formatDate
import com.ironvellum.app.ui.components.selectedUnderline
import com.ironvellum.app.ui.components.UndoBar
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.DotShape
import com.ironvellum.app.ui.theme.IronvellumColors
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive


internal interface InboxGateway {
    val owners: Flow<SocialOwner>
    fun owner(): SocialOwner
    suspend fun read(force: Boolean): Result<Inbox>
    suspend fun markSeen(owner: SocialOwner): Result<Unit>
    suspend fun accept(request: RequestIdentity, owner: SocialOwner): Result<Boolean>
    suspend fun decline(request: RequestIdentity, owner: SocialOwner): Result<Boolean>
    fun dismissReadNotification(owner: SocialOwner, throughMs: Long)
}

private class CloudInboxGateway(private val app: IronvellumApp) : InboxGateway {
    private val cloud get() = app.cloudSync
    private val accounts get() = app.accountRepository
    override fun owner() = currentSocialOwner(accounts.account.value?.userId)
    override val owners = combine(accounts.account, Cloud.config) { _, _ -> owner() }.distinctUntilChanged()
    override suspend fun read(force: Boolean) = cloud.inbox(force)
    override suspend fun markSeen(owner: SocialOwner): Result<Unit> {
        val userId = owner.userId ?: return Result.success(Unit)
        val client = owner.backend as? SupabaseClient ?: return Result.success(Unit)
        return cloud.markInboxSeen(userId, client)
    }
    override suspend fun accept(request: RequestIdentity, owner: SocialOwner): Result<Boolean> {
        val me = owner.userId ?: return Result.success(false)
        val client = owner.backend as? SupabaseClient ?: return Result.success(false)
        return cloud.acceptInboxFriendRequest(request.userId, request.createdAt, me, client)
    }
    override suspend fun decline(request: RequestIdentity, owner: SocialOwner): Result<Boolean> {
        val me = owner.userId ?: return Result.success(false)
        val client = owner.backend as? SupabaseClient ?: return Result.success(false)
        return cloud.declineFriendRequest(request.userId, request.createdAt, me, client)
    }

    override fun dismissReadNotification(owner: SocialOwner, throughMs: Long) {
        val backend = Cloud.config.value?.url ?: return
        val userId = owner.userId ?: return
        if (owner() == owner) InboxNotifier.cancelThrough(app, userId, backend, throughMs)
    }
}

/** How an ally request was answered from the inbox row itself. */
internal enum class RequestAnswer { ACCEPTED, DECLINED }
internal data class RequestIdentity(val userId: String, val createdAtMs: Long, val createdAt: String = Instant.ofEpochMilli(createdAtMs).toString())

internal data class InboxUi(
    val owner: SocialOwner = SocialOwner(null, null),
    val loading: Boolean = false,
    /** True once one fetch answered, so an empty list reads as "clear", not "loading". */
    val loaded: Boolean = false,
    val items: List<InboxItem> = emptyList(),
    /**
     * seen_at as the server held it when these items were fetched. Items after
     * it stay marked unread for this whole visit even though opening the tab
     * already marked them seen — otherwise the marks vanished before anyone
     * could read them.
     */
    val seenAtMs: Long? = null,
    val successfulReads: Long = 0,
    val error: UiText? = null,
    /** An accept/decline the server refused, verbatim. */
    val actionError: UiText? = null,
    val answering: Set<RequestIdentity> = emptySet(),
    val answered: Map<RequestIdentity, RequestAnswer> = emptyMap(),
    /** A decline waiting out its Undo window. */
    val pendingDecline: Pair<RequestIdentity, String>? = null,
) {
    val signedIn: Boolean get() = owner.userId != null
}

internal class InboxViewModel(private val gateway: InboxGateway, private val onAnswered: (SocialOwner) -> Unit = {}) : ViewModel() {
    private var owner = gateway.owner()
    private var requests = requestScope()
    private val _ui = MutableStateFlow(InboxUi(owner = owner))
    val ui: StateFlow<InboxUi> = _ui.asStateFlow()

    private fun Throwable.reason(): UiText = reasonText(UiText.res(R.string.inbox_unknown_failure))

    private fun requestScope() = CoroutineScope(
        viewModelScope.coroutineContext + SupervisorJob(viewModelScope.coroutineContext[Job]),
    )

    private fun refreshOwner() {
        val current = gateway.owner()
        if (current == owner) return
        owner = current
        requests.cancel()
        requests = requestScope()
        _ui.value = InboxUi(owner = current)
    }

    private fun current(expected: SocialOwner, scope: CoroutineScope): Boolean =
        requests === scope && owner == expected && gateway.owner() == expected

    init {
        viewModelScope.launch { gateway.owners.collect { refreshOwner() } }
    }

    /** Read first, then acknowledge only for the account/backend that opened this visit. */
    fun open(force: Boolean = false, acknowledge: Boolean = true) {
        refreshOwner()
        if (!_ui.value.signedIn || _ui.value.loading) return
        val expected = owner
        val scope = requests
        _ui.value = _ui.value.copy(loading = true, error = null)
        scope.launch {
            try {
                val inbox = gateway.read(force).getOrThrow()
                if (!current(expected, scope)) return@launch
                _ui.value = _ui.value.copy(
                    items = inbox.items, seenAtMs = inbox.seenAtMs, loaded = true,
                    successfulReads = _ui.value.successfulReads + 1,
                )
                if (acknowledge) {
                    if (inbox.unread > 0) gateway.markSeen(expected).getOrThrow()
                    if (!current(expected, scope)) return@launch
                    val through = maxOf(inbox.seenAtMs ?: Long.MIN_VALUE, inbox.items.maxOfOrNull { it.occurredAtMs } ?: Long.MIN_VALUE)
                    gateway.dismissReadNotification(expected, through)
                }
            } catch (error: CancellationException) {
                if (current(expected, scope) && scope.isActive) {
                    _ui.value = _ui.value.copy(loaded = true, error = UiText.res(R.string.inbox_unknown_failure))
                }
                throw error
            } catch (error: Exception) {
                if (current(expected, scope)) _ui.value = _ui.value.copy(error = error.reason(), loaded = true)
            } finally {
                if (current(expected, scope)) _ui.value = _ui.value.copy(loading = false)
            }
        }
    }

    fun accept(request: RequestIdentity) = answer(request, RequestAnswer.ACCEPTED)

    /**
     * Decline reads as done at once but the server call waits for the Undo window to close,
     * since a declined request cannot be put back from here. A second decline commits the first.
     */
    fun decline(request: RequestIdentity, name: String) {
        refreshOwner()
        if (request in _ui.value.answering || request in _ui.value.answered) return
        commitDecline()
        _ui.value = _ui.value.copy(pendingDecline = request to name)
    }

    fun undoDecline() {
        _ui.value = _ui.value.copy(pendingDecline = null)
    }

    /** Send the waiting decline now: its window closed, another decline began, or the tab was left. */
    fun commitDecline() {
        refreshOwner()
        val (request, _) = _ui.value.pendingDecline ?: return
        _ui.value = _ui.value.copy(pendingDecline = null)
        answer(request, RequestAnswer.DECLINED)
    }

    private fun answer(request: RequestIdentity, answer: RequestAnswer) {
        refreshOwner()
        if (!_ui.value.signedIn || request in _ui.value.answering || request in _ui.value.answered) return
        val expected = owner
        val scope = requests
        _ui.value = _ui.value.copy(answering = _ui.value.answering + request, actionError = null)
        scope.launch {
            try {
                val answered = when (answer) {
                    RequestAnswer.ACCEPTED -> gateway.accept(request, expected).getOrThrow()
                    RequestAnswer.DECLINED -> gateway.decline(request, expected).getOrThrow()
                }
                if (!current(expected, scope)) return@launch
                if (answered) {
                    _ui.value = _ui.value.copy(answered = _ui.value.answered + (request to answer))
                    onAnswered(expected)
                } else {
                    // A read started before the conditional delete can still contain the stale request.
                    _ui.first { !it.loading }
                    if (!current(expected, scope)) return@launch
                    _ui.value = _ui.value.copy(items = _ui.value.items.filterNot {
                        it is InboxItem.FriendRequest && it.actorId == request.userId && it.createdAt == request.createdAt
                    })
                    open(force = true, acknowledge = false)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (current(expected, scope)) _ui.value = _ui.value.copy(actionError = error.reason())
            } finally {
                if (current(expected, scope)) _ui.value = _ui.value.copy(answering = _ui.value.answering - request)
            }
        }
    }
}

/**
 * What happened to the lifter's workouts and alliances lately: ally requests,
 * accepted requests, comments and reactions. Derived on the server, 30 days deep.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InboxScreen(
    onOpenLifter: (userId: String, displayName: String) -> Unit,
    onOpenComments: (sessionId: String, ownerId: String, headline: String) -> Unit,
    /** A circle missive opens the circle itself, not the member's profile. */
    onOpenCircle: () -> Unit,
    onAnswered: (SocialOwner) -> Unit = {},
    /** False while the page is only peeking in during a swipe: reading the inbox waits for the visit. */
    active: Boolean = true,
    viewModel: InboxViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                InboxViewModel(CloudInboxGateway(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as IronvellumApp), onAnswered)
            }
        },
    ),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    var unreadOnly by rememberSaveable(ui.owner, active) { mutableStateOf(false) }
    var visitSeenAt by rememberSaveable(ui.owner, active) { mutableStateOf<Long?>(null) }
    // Restore the visit cutoff independently of the ViewModel's rows.
    LaunchedEffect(ui.owner, active) {
        val owner = ui.owner
        if (owner.userId == null || !active) return@LaunchedEffect
        if (visitSeenAt != null && viewModel.ui.value.loaded) return@LaunchedEffect
        val current = viewModel.ui.first { it.owner != owner || !it.loading }
        if (current.owner != owner) return@LaunchedEffect
        viewModel.open(force = true)
        if (visitSeenAt == null) {
            val firstRead = viewModel.ui.first { it.owner == current.owner && it.successfulReads > current.successfulReads }
            visitSeenAt = firstRead.seenAtMs ?: Long.MIN_VALUE
        }
    }
    // Leaving the tab (a swipe or navigation) lets a waiting decline go through.
    val commit by rememberUpdatedState(viewModel::commitDecline)
    LaunchedEffect(active) { if (!active) commit() }
    DisposableEffect(Unit) { onDispose { commit() } }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            val err = ui.error?.asString()
            when {
                !ui.loaded -> InkPanel(Modifier.fillMaxWidth()) {
                    Text(
                        UiText.themed(R.string.inbox_loading, R.string.inbox_loading__t).asString(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = ChakraPetch,
                        color = IronvellumColors.InkMuted,
                    )
                }
                ui.items.isEmpty() && err != null -> InkPanel(Modifier.fillMaxWidth()) {
                    Text(
                        err,
                        style = MaterialTheme.typography.bodySmall,
                        color = IronvellumColors.InkMuted,
                    )
                    Spacer(Modifier.height(6.dp))
                    SocialRefreshLink(onClick = { viewModel.open(force = true) }, label = stringResource(R.string.inbox_try_again))
                }
                else -> {
                    if (err != null) {
                        SocialErrorBanner(Lines.inboxStale(err))
                        Spacer(Modifier.height(10.dp))
                    }
                    ui.actionError?.let {
                        SocialErrorBanner(it.asString())
                        Spacer(Modifier.height(10.dp))
                    }
                    val pullState = rememberPullToRefreshState()
                    PullToRefreshBox(
                        isRefreshing = ui.loading,
                        onRefresh = { viewModel.open(force = true) },
                        modifier = Modifier.fillMaxSize(),
                        state = pullState,
                        indicator = {
                            PullToRefreshDefaults.Indicator(
                                state = pullState,
                                isRefreshing = ui.loading,
                                modifier = Modifier.align(Alignment.TopCenter),
                                containerColor = IronvellumColors.Vault,
                                color = IronvellumColors.EmeraldBright,
                            )
                        },
                    ) {
                        val seenAt = visitSeenAt ?: Long.MAX_VALUE
                        val visible = ui.items.filter { !unreadOnly || it.occurredAtMs > seenAt }
                        val requests = visible.filterIsInstance<InboxItem.FriendRequest>()
                        val activity = visible.filterNot { it is InboxItem.FriendRequest }
                        Column(Modifier.fillMaxSize().verticalScroll(scrollState)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                listOf(false, true).forEach { unread ->
                                    Text(
                                        stringResource(if (unread) R.string.social_inbox_filter_unread else R.string.social_inbox_filter_all),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (unreadOnly == unread) IronvellumColors.Ink else IronvellumColors.InkMuted,
                                        modifier = Modifier.heightIn(min = 48.dp)
                                            .selectedUnderline(unreadOnly == unread)
                                            .selectable(selected = unreadOnly == unread, role = Role.Checkbox) { unreadOnly = unread }
                                            .wrapContentHeight(),
                                    )
                                }
                            }
                            if (ui.items.isEmpty()) {
                                EmptyInbox()
                            } else if (visible.isEmpty()) {
                                Text(stringResource(R.string.social_inbox_no_unread), color = IronvellumColors.InkMuted, style = MaterialTheme.typography.bodySmall)
                            } else {
                                if (requests.isNotEmpty()) {
                                    SectionHeader(stringResource(R.string.social_inbox_requests))
                                    InboxRows(requests, ui, seenAt, onOpenLifter, onOpenCircle, onOpenComments, viewModel)
                                }
                                activity.groupBy { Instant.ofEpochMilli(it.occurredAtMs).atZone(ZoneId.systemDefault()).toLocalDate() }
                                    .forEach { (_, items) ->
                                        SectionHeader(formatDate(items.first().occurredAtMs, "MMM d, yyyy"))
                                        InboxRows(items, ui, seenAt, onOpenLifter, onOpenCircle, onOpenComments, viewModel)
                                    }
                            }
                            Spacer(Modifier.height(28.dp))
                        }
                    }
                }
            }
        }
        ui.pendingDecline?.let { (_, name) ->
            UndoBar(
                message = stringResource(R.string.inbox_declining_message, name),
                onUndo = viewModel::undoDecline,
                onExpired = viewModel::commitDecline,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
            )
        }
    }
}

@Composable
private fun InboxRows(
    items: List<InboxItem>,
    ui: InboxUi,
    seenAt: Long,
    onOpenLifter: (String, String) -> Unit,
    onOpenCircle: () -> Unit,
    onOpenComments: (String, String, String) -> Unit,
    viewModel: InboxViewModel,
) {
    InkPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
        items.forEachIndexed { index, item ->
            if (index > 0) InkDivider()
            val request = RequestIdentity(item.actorId, item.occurredAtMs, (item as? InboxItem.FriendRequest)?.createdAt ?: Instant.ofEpochMilli(item.occurredAtMs).toString())
            InboxRow(
                item = item,
                unread = item.occurredAtMs > seenAt,
                answering = request in ui.answering || ui.pendingDecline?.first == request,
                answered = ui.answered[request],
                onOpenLifter = { onOpenLifter(item.actorId, item.actorName) },
                onOpenCircle = onOpenCircle,
                onOpenComments = { sessionId, headline, mine ->
                    ui.owner.userId?.let { me -> onOpenComments(sessionId, if (mine) me else "", headline) }
                },
                onAccept = { viewModel.accept(request) },
                onDecline = { viewModel.decline(request, item.actorName.ifBlank { "${Words.lifter.cat}" }) },
            )
        }
    }
}

@Composable
private fun EmptyInbox() {
    // One quiet plate, the same card the list would sit in: a plain title and one muted line.
    InkPanel(Modifier.fillMaxWidth()) {
        Text(
            UiText.themed(R.string.inbox_empty_title, R.string.inbox_empty_title__t).asString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = IronvellumColors.Ink,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            UiText.themed(R.string.inbox_empty_body, R.string.inbox_empty_body__t).asString(),
            style = MaterialTheme.typography.bodySmall,
            color = IronvellumColors.InkMuted,
        )
    }
}

@Composable
private fun InboxRow(
    item: InboxItem,
    unread: Boolean,
    answering: Boolean,
    answered: RequestAnswer?,
    onOpenLifter: () -> Unit,
    onOpenCircle: () -> Unit,
    onOpenComments: (sessionId: String, headline: String, mine: Boolean) -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    val unreadDescription = stringResource(R.string.inbox_unread_description)
    // Each line is one whole sentence per mode; a workout with no title gets its own wording
    // rather than a concept word spliced into the titled one.
    val (icon, line) = when (item) {
        is InboxItem.FriendRequest -> Icons.Outlined.PersonAdd to
            UiText.themed(R.string.inbox_line_friend_request, R.string.inbox_line_friend_request__t)
        is InboxItem.RequestAccepted -> Icons.Outlined.Handshake to
            UiText.themed(R.string.inbox_line_request_accepted, R.string.inbox_line_request_accepted__t)
        is InboxItem.NewComment -> Icons.Outlined.ChatBubbleOutline to (
            if (item.sessionHeadline.isBlank()) {
                UiText.themed(R.string.inbox_line_comment_untitled, R.string.inbox_line_comment_untitled__t)
            } else {
                UiText.themed(R.string.inbox_line_comment, R.string.inbox_line_comment__t, item.sessionHeadline)
            }
            )
        is InboxItem.NewReply -> Icons.Outlined.Forum to (
            if (item.sessionHeadline.isBlank()) {
                UiText.themed(R.string.inbox_line_reply_untitled, R.string.inbox_line_reply_untitled__t)
            } else {
                UiText.res(R.string.inbox_line_reply, item.sessionHeadline)
            }
            )
        is InboxItem.NewReaction -> item.reaction.glyph() to (
            if (item.sessionHeadline.isBlank()) {
                UiText.themed(R.string.inbox_line_reaction_untitled, R.string.inbox_line_reaction_untitled__t, item.reaction.displayName())
            } else {
                UiText.themed(R.string.inbox_line_reaction, R.string.inbox_line_reaction__t, item.reaction.displayName(), item.sessionHeadline)
            }
            )
        is InboxItem.NewCircleMember -> Icons.Outlined.GroupAdd to
            UiText.themed(R.string.inbox_line_circle_joined, R.string.inbox_line_circle_joined__t, item.circleName)
        is InboxItem.CircleGoalMet -> Icons.Outlined.EmojiEvents to
            UiText.themed(R.string.inbox_line_circle_goal_met, R.string.inbox_line_circle_goal_met__t)
        is InboxItem.CircleNotice -> Icons.Outlined.GroupAdd to when (item.change) {
            CircleChange.LEFT -> UiText.themed(R.string.inbox_line_circle_left, R.string.inbox_line_circle_left__t, item.circleName)
            CircleChange.KEEPER -> UiText.res(R.string.inbox_line_circle_keys, item.circleName)
            CircleChange.REMOVED -> UiText.res(R.string.inbox_line_circle_removed, item.circleName)
        }
    }
    val open: (() -> Unit)? = when (item) {
        is InboxItem.NewComment -> { { onOpenComments(item.sessionId, item.sessionHeadline.orWorkout(), true) } }
        is InboxItem.NewReply -> { { onOpenComments(item.sessionId, item.sessionHeadline.ifBlank { Words.trial.a }, false) } }
        is InboxItem.NewReaction -> { { onOpenComments(item.sessionId, item.sessionHeadline.orWorkout(), true) } }
        is InboxItem.NewCircleMember, is InboxItem.CircleGoalMet -> onOpenCircle
        is InboxItem.CircleNotice -> null
        else -> onOpenLifter
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .then(if (open != null) Modifier.clickable(role = Role.Button, onClick = open) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Unread = a primary-accent dot on the glyph and a bolder name; the glyph stays muted
        // either way, so the new ones stand out without a second colour.
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = null,
                tint = IronvellumColors.InkMuted,
                modifier = Modifier.size(20.dp),
            )
            if (unread) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(7.dp)
                        .clip(DotShape)
                        .background(IronvellumColors.Emerald)
                        // The dot is the only unread cue a screen reader could miss.
                        .semantics { contentDescription = unreadDescription },
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    item.actorName.ifBlank { "${Words.lifter.cat}" },
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = ChakraPetch,
                    fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Medium,
                    color = IronvellumColors.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    relativeTime(item.occurredAtMs),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = ChakraPetch,
                    color = IronvellumColors.InkMuted,
                    maxLines = 1,
                )
            }
            Text(
                line.asString(),
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val remark = (item as? InboxItem.NewComment)?.body ?: (item as? InboxItem.NewReply)?.body
            if (!remark.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.inbox_remark_quote, remark),
                    style = MaterialTheme.typography.bodyMedium,
                    color = IronvellumColors.Ink,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (item is InboxItem.FriendRequest) {
                when (answered) {
                    RequestAnswer.ACCEPTED -> AnswerLabel(UiText.themed(R.string.inbox_answer_accepted, R.string.inbox_answer_accepted__t).asString())
                    RequestAnswer.DECLINED -> AnswerLabel(stringResource(R.string.inbox_answer_declined))
                    null -> if (!answering) Row(verticalAlignment = Alignment.CenterVertically) {
                        InkChip(
                            label = stringResource(R.string.inbox_accept_button),
                            onClick = onAccept,
                        )
                        Text(
                            stringResource(R.string.inbox_decline_button),
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = ChakraPetch,
                            color = IronvellumColors.InkMuted,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .clickable(role = Role.Button, onClick = onDecline)
                                .padding(horizontal = 12.dp)
                                .wrapContentHeight(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnswerLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontFamily = ChakraPetch,
        color = IronvellumColors.InkMuted,
        modifier = Modifier.padding(top = 4.dp),
    )
}

private fun String.orWorkout(): String =
    ifBlank { Words.text(UiText.themed(R.string.inbox_headline_your_trial, R.string.inbox_headline_your_trial__t)) }
