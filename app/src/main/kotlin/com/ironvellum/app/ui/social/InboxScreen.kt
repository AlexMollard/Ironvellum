package com.ironvellum.app.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.ironvellum.app.data.cloud.AccountRepository
import com.ironvellum.app.data.cloud.CloudSync
import com.ironvellum.app.data.cloud.InboxItem
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.ironvellumAccount
import com.ironvellum.app.ui.ironvellumCloudSync
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.InkCircleShape
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.IronvellumTracking
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
    val error: String? = null,
    /** An accept/decline the server refused, verbatim. */
    val actionError: String? = null,
    val answering: Set<String> = emptySet(),
    val answered: Map<String, RequestAnswer> = emptyMap(),
)

internal class InboxViewModel(
    private val cloud: CloudSync,
    accountRepo: AccountRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(
        InboxUi(signedIn = accountRepo.account.value != null, myUserId = accountRepo.account.value?.userId),
    )
    val ui: StateFlow<InboxUi> = _ui.asStateFlow()

    private fun Throwable.reason(): String = message ?: this::class.simpleName ?: "Unknown failure"

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

    fun decline(userId: String) = answer(userId, RequestAnswer.DECLINED)

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
    viewModel: InboxViewModel = viewModel(
        factory = viewModelFactory { initializer { InboxViewModel(ironvellumCloudSync(), ironvellumAccount()) } },
    ),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    // Every visit to the tab reads the inbox and clears the nav dot.
    LaunchedEffect(ui.signedIn) {
        if (ui.signedIn) viewModel.open()
    }

    Column(Modifier.fillMaxSize()) {
        val err = ui.error
        when {
            !ui.loaded -> InkPanel(Modifier.fillMaxWidth(), accent = IronvellumColors.Rune) {
                Text(
                    "Reading your missives…",
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = ChakraPetch,
                    color = IronvellumColors.InkMuted,
                )
            }
            ui.items.isEmpty() && err != null -> InkPanel(Modifier.fillMaxWidth(), accent = IronvellumColors.DangerRed) {
                Text(
                    err,
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                )
                Spacer(Modifier.height(6.dp))
                SocialRefreshLink(onClick = { viewModel.open(force = true) }, label = "Try again")
            }
            else -> {
                if (err != null) {
                    SocialErrorBanner("The ink has faded — these missives are from your last sync: $err")
                    Spacer(Modifier.height(10.dp))
                }
                ui.actionError?.let {
                    SocialErrorBanner(it)
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
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                        if (ui.items.isEmpty()) {
                            item(key = "empty") { EmptyInbox() }
                        }
                        itemsIndexed(ui.items, key = { index, item -> "${item.occurredAtMs}-${item.actorId}-$index" }) { _, item ->
                            InboxRow(
                                item = item,
                                unread = ui.seenAtMs == null || item.occurredAtMs > ui.seenAtMs!!,
                                answering = item.actorId in ui.answering,
                                answered = ui.answered[item.actorId],
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
                                onDecline = { viewModel.decline(item.actorId) },
                            )
                        }
                        item(key = "foot") { Spacer(Modifier.height(28.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyInbox() {
    InkPanel(Modifier.fillMaxWidth(), accent = IronvellumColors.Emerald) {
        Text(
            "NO MISSIVES",
            style = MaterialTheme.typography.labelLarge,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = IronvellumColors.EmeraldBright,
            letterSpacing = IronvellumTracking.InlineLabel,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Ally requests, remarks and tributes on your trials land here.",
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
    val (icon, line) = when (item) {
        is InboxItem.FriendRequest -> Icons.Outlined.PersonAdd to "wants to be your ally"
        is InboxItem.RequestAccepted -> Icons.Outlined.Handshake to "accepted your ally request"
        is InboxItem.NewComment -> Icons.Outlined.ChatBubbleOutline to "left a remark on ${item.sessionHeadline.orWorkout()}"
        is InboxItem.NewReply -> Icons.Outlined.Forum to "replied on ${item.sessionHeadline.ifBlank { "a trial" }}"
        is InboxItem.NewReaction -> item.reaction.glyph() to "paid ${item.reaction.displayName()} tribute on ${item.sessionHeadline.orWorkout()}"
        is InboxItem.NewCircleMember -> Icons.Outlined.GroupAdd to "joined your circle ${item.circleName}"
        is InboxItem.CircleGoalMet -> Icons.Outlined.EmojiEvents to "sealed the trial that met your circle's weekly goal"
    }
    val open: () -> Unit = when (item) {
        is InboxItem.NewComment -> { { onOpenComments(item.sessionId, item.sessionHeadline.orWorkout(), true) } }
        is InboxItem.NewReply -> { { onOpenComments(item.sessionId, item.sessionHeadline.ifBlank { "a trial" }, false) } }
        is InboxItem.NewReaction -> { { onOpenComments(item.sessionId, item.sessionHeadline.orWorkout(), true) } }
        is InboxItem.NewCircleMember, is InboxItem.CircleGoalMet -> onOpenCircle
        else -> onOpenLifter
    }
    InkPanel(
        Modifier.fillMaxWidth(),
        accent = if (unread) IronvellumColors.SovereignGold else IronvellumColors.Rune,
        onClick = open,
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Unread = a gold dot beside a gold glyph; read items drop to muted
            // ink, so the new ones stand out in a long list.
            Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (unread) IronvellumColors.SovereignGold else IronvellumColors.InkMuted,
                    modifier = Modifier.size(20.dp),
                )
                if (unread) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .size(7.dp)
                            .clip(InkCircleShape(7))
                            .background(IronvellumColors.SovereignGold)
                            // The dot is the only unread cue a screen reader could miss.
                            .semantics { contentDescription = "New" },
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        item.actorName.ifBlank { "An Ironbound" },
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = ChakraPetch,
                        fontWeight = FontWeight.Bold,
                        color = IronvellumColors.Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Text(
                        relativeTime(item.occurredAtMs),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = ChakraPetch,
                        color = if (unread) IronvellumColors.SovereignGold else IronvellumColors.InkMuted,
                        maxLines = 1,
                    )
                }
                Text(
                    line,
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val remark = (item as? InboxItem.NewComment)?.body ?: (item as? InboxItem.NewReply)?.body
                if (!remark.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "“$remark”",
                        style = MaterialTheme.typography.bodyMedium,
                        color = IronvellumColors.Ink,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (item is InboxItem.FriendRequest) {
                    Spacer(Modifier.height(8.dp))
                    when (answered) {
                        RequestAnswer.ACCEPTED -> AnswerLabel("ALLIED", IronvellumColors.SovereignGold)
                        RequestAnswer.DECLINED -> AnswerLabel("DECLINED", IronvellumColors.InkMuted)
                        null -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IronvellumButton(label = "Accept", onClick = onAccept, enabled = !answering)
                            IronvellumButton(label = "Decline", onClick = onDecline, enabled = !answering, quiet = true)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnswerLabel(text: String, tint: androidx.compose.ui.graphics.Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontFamily = ChakraPetch,
        fontWeight = FontWeight.Bold,
        color = tint,
        letterSpacing = IronvellumTracking.InlineLabel,
    )
}

private fun String.orWorkout(): String = ifBlank { "your trial" }
