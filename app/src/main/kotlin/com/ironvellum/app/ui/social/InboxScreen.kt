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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.ui.res.stringResource
import com.ironvellum.app.R
import com.ironvellum.app.data.Lines
import com.ironvellum.app.data.Words
import com.ironvellum.app.data.reasonText
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.ui.asString
import com.ironvellum.app.data.cloud.AccountRepository
import com.ironvellum.app.data.cloud.CloudSync
import com.ironvellum.app.data.cloud.CircleChange
import com.ironvellum.app.data.cloud.InboxItem
import com.ironvellum.app.ui.components.InkChip
import com.ironvellum.app.ui.components.InkDivider
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.components.UndoBar
import com.ironvellum.app.ui.ironvellumAccount
import com.ironvellum.app.ui.ironvellumCloudSync
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.DotShape
import com.ironvellum.app.ui.theme.IronvellumColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** How an ally request was answered from the inbox row itself. */
internal enum class RequestAnswer { ACCEPTED, DECLINED }

internal data class InboxUi(
    val signedIn: Boolean = false,
    val myUserId: String? = null,
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
    val error: UiText? = null,
    /** An accept/decline the server refused, verbatim. */
    val actionError: UiText? = null,
    val answering: Set<String> = emptySet(),
    val answered: Map<String, RequestAnswer> = emptyMap(),
    /** A decline waiting out its Undo window: (actor id, name). The server call fires when it closes. */
    val pendingDecline: Pair<String, String>? = null,
)

internal class InboxViewModel(
    private val cloud: CloudSync,
    accountRepo: AccountRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(
        InboxUi(signedIn = accountRepo.account.value != null, myUserId = accountRepo.account.value?.userId),
    )
    val ui: StateFlow<InboxUi> = _ui.asStateFlow()

    private fun Throwable.reason(): UiText = reasonText(UiText.res(R.string.inbox_unknown_failure))

    init {
        viewModelScope.launch {
            accountRepo.account.collect { account ->
                val switched = account?.userId != _ui.value.myUserId
                _ui.value = if (switched) {
                    // A different lifter (or none): nothing of the last one's inbox may linger.
                    InboxUi(signedIn = account != null, myUserId = account?.userId)
                } else {
                    _ui.value.copy(signedIn = account != null)
                }
            }
        }
    }

    /**
     * Fetch, then mark seen. The order matters: marking first would move
     * seen_at past every item and nothing would ever show as new.
     */
    fun open(force: Boolean = false) {
        if (!_ui.value.signedIn || _ui.value.loading) return
        _ui.value = _ui.value.copy(loading = true, error = null)
        viewModelScope.launch {
            cloud.inbox(force = force)
                .onSuccess { inbox ->
                    _ui.value = _ui.value.copy(items = inbox.items, seenAtMs = inbox.seenAtMs, loaded = true)
                    if (inbox.unread > 0) cloud.markInboxSeen()
                }
                .onFailure { _ui.value = _ui.value.copy(error = it.reason(), loaded = true) }
            _ui.value = _ui.value.copy(loading = false)
        }
    }

    fun accept(userId: String) = answer(userId, RequestAnswer.ACCEPTED)

    /**
     * Decline reads as done at once but the server call waits for the Undo window to close,
     * since a declined request cannot be put back from here. A second decline commits the first.
     */
    fun decline(userId: String, name: String) {
        if (userId in _ui.value.answering || userId in _ui.value.answered) return
        commitDecline()
        _ui.value = _ui.value.copy(pendingDecline = userId to name)
    }

    fun undoDecline() {
        _ui.value = _ui.value.copy(pendingDecline = null)
    }

    /** Send the waiting decline now: its window closed, another decline began, or the tab was left. */
    fun commitDecline() {
        val (userId, _) = _ui.value.pendingDecline ?: return
        _ui.value = _ui.value.copy(pendingDecline = null)
        answer(userId, RequestAnswer.DECLINED)
    }

    private fun answer(userId: String, answer: RequestAnswer) {
        if (userId in _ui.value.answering || userId in _ui.value.answered) return
        _ui.value = _ui.value.copy(answering = _ui.value.answering + userId, actionError = null)
        viewModelScope.launch {
            val call = when (answer) {
                RequestAnswer.ACCEPTED -> cloud.acceptFriend(userId)
                // Declining deletes the pending row, the same call that ends an alliance.
                RequestAnswer.DECLINED -> cloud.removeFriend(userId)
            }
            call
                .onSuccess { _ui.value = _ui.value.copy(answered = _ui.value.answered + (userId to answer)) }
                .onFailure { _ui.value = _ui.value.copy(actionError = it.reason()) }
            _ui.value = _ui.value.copy(answering = _ui.value.answering - userId)
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
    /** False while the page is only peeking in during a swipe: reading the inbox waits for the visit. */
    active: Boolean = true,
    viewModel: InboxViewModel = viewModel(
        factory = viewModelFactory { initializer { InboxViewModel(ironvellumCloudSync(), ironvellumAccount()) } },
    ),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    // Every visit to the tab reads the inbox and clears the nav dot.
    LaunchedEffect(ui.signedIn, active) {
        if (ui.signedIn && active) viewModel.open()
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
                        // The inbox is capped at 30 days, so one scrolling column inside one
                        // card is enough: rows divided by InkDivider, as everywhere else.
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            if (ui.items.isEmpty()) {
                                EmptyInbox()
                            } else {
                                InkPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
                                    ui.items.forEachIndexed { index, item ->
                                        if (index > 0) InkDivider()
                                        InboxRow(
                                            item = item,
                                            unread = ui.seenAtMs == null || item.occurredAtMs > ui.seenAtMs!!,
                                            answering = item.actorId in ui.answering,
                                            answered = ui.answered[item.actorId]
                                                ?: RequestAnswer.DECLINED.takeIf { ui.pendingDecline?.first == item.actorId },
                                            onOpenLifter = { onOpenLifter(item.actorId, item.actorName) },
                                            onOpenCircle = onOpenCircle,
                                            onOpenComments = { sessionId, headline, mine ->
                                                // Remarks and tributes are on the caller's own trials, so
                                                // the caller is the owner. A reply sits on someone else's:
                                                // it opens with no owner, and the thread reads the real
                                                // one off the trial, rather than lending the caller the
                                                // owner's moderation for a moment.
                                                ui.myUserId?.let { me -> onOpenComments(sessionId, if (mine) me else "", headline) }
                                            },
                                            onAccept = { viewModel.accept(item.actorId) },
                                            onDecline = { viewModel.decline(item.actorId, item.actorName.ifBlank { "${Words.lifter.cat}" }) },
                                        )
                                    }
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
                message = stringResource(R.string.inbox_declined_message, name),
                onUndo = viewModel::undoDecline,
                onExpired = viewModel::commitDecline,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
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
    val open: () -> Unit = when (item) {
        is InboxItem.NewComment -> { { onOpenComments(item.sessionId, item.sessionHeadline.orWorkout(), true) } }
        is InboxItem.NewReply -> { { onOpenComments(item.sessionId, item.sessionHeadline.ifBlank { Words.trial.a }, false) } }
        is InboxItem.NewReaction -> { { onOpenComments(item.sessionId, item.sessionHeadline.orWorkout(), true) } }
        is InboxItem.NewCircleMember, is InboxItem.CircleGoalMet, is InboxItem.CircleNotice -> onOpenCircle
        else -> onOpenLifter
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(role = Role.Button, onClick = open)
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
                    null -> Row(verticalAlignment = Alignment.CenterVertically) {
                        InkChip(
                            label = stringResource(R.string.inbox_accept_button),
                            onClick = { if (!answering) onAccept() },
                        )
                        Text(
                            stringResource(R.string.inbox_decline_button),
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = ChakraPetch,
                            color = IronvellumColors.InkMuted,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .clickable(enabled = !answering, role = Role.Button, onClick = onDecline)
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
