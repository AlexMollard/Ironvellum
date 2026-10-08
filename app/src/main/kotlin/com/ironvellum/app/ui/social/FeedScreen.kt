package com.ironvellum.app.ui.social

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.MilitaryTech
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ironvellum.app.data.Repository
import com.ironvellum.app.data.cloud.Cloud
import com.ironvellum.app.data.cloud.CloudSync
import com.ironvellum.app.data.cloud.FeedEntry
import com.ironvellum.app.data.cloud.FriendRow
import com.ironvellum.app.data.cloud.Reaction
import com.ironvellum.app.domain.Titles
import com.ironvellum.app.ui.components.InkChip
import com.ironvellum.app.ui.components.InkDivider
import com.ironvellum.app.ui.components.selectedUnderline
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.components.formatDate
import com.ironvellum.app.ui.ironvellumAccount
import com.ironvellum.app.ui.ironvellumCloudSync
import com.ironvellum.app.ui.ironvellumRepository
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Snapshot of the public board: state, entries, paging cursor, and refresh state. */
data class FeedUi(
    // Snapshot for ViewModel logic; SocialScreen collects Cloud.config so a
    // backend switch redraws without a restart.
    val configured: Boolean = Cloud.config.value != null,
    val signedIn: Boolean = false,
    val myUserId: String? = null,
    val entries: List<FeedEntry> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    /** True once a page came back short of the limit — the board's end is reached. */
    val exhausted: Boolean = false,
    val error: String? = null,
    /** Last page fetch failed, scoped to paging so one flaky page never blocks the board. */
    val pagingError: String? = null,
    /** Ally rows for ADD ALLY buttons; fetched with the feed, never per card. */
    val friends: Map<String, FriendRow> = emptyMap(),
    /** Optimistically-sent ally requests, settled from the server once it answers. */
    val requestedAlly: Set<String> = emptySet(),
    /** A sent ally request that the server refused; null once the next attempt starts. */
    val allyError: String? = null,
    /** Per-card reaction failures; keyed by sessionId so the message sits on the tapped card. */
    val reactionErrors: Map<String, String> = emptyMap(),
)

class FeedViewModel(
    private val cloudSync: CloudSync,
    private val accountRepo: com.ironvellum.app.data.cloud.AccountRepository,
    private val repo: Repository,
    private val pageSize: Int = 50,
) : ViewModel() {

    private val _ui = MutableStateFlow(
        FeedUi(signedIn = accountRepo.account.value != null, myUserId = accountRepo.account.value?.userId),
    )
    val ui = _ui.asStateFlow()

    // Device-local equipped crest frame; only the signed-in lifter's own card wears it.
    val equippedFrame: StateFlow<String?> = repo.observeEquippedFrame()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Oldest completedAtMs seen — the cursor for the next page. */
    private var oldestMs: Long? = null

    /** Workouts with a reaction call in flight; blocks the double-tap re-fire that drifts the count. */
    private val reactionCallsInFlight = mutableSetOf<String>()

    private fun Throwable.reason(): String = message ?: this::class.simpleName ?: "Unknown failure"

    init {
        // Sign-in happens on a sibling tab AFTER this view model exists, so a
        // one-shot read of account.value strands the feed on "sign in" forever.
        // Observe it instead and load the moment a session appears.
        viewModelScope.launch {
            accountRepo.account.collect { account ->
                val wasSignedIn = _ui.value.signedIn
                _ui.value = _ui.value.copy(
                    signedIn = account != null,
                    myUserId = account?.userId,
                )
                when {
                    account != null && !wasSignedIn -> load()
                    account == null -> _ui.value = _ui.value.copy(entries = emptyList())
                    // Restored offline: the identity came back but the profiles
                    // row never did — retry it on this social read.
                    !account.profileLoaded -> viewModelScope.launch { accountRepo.refreshProfile() }
                }
            }
        }
        load()
    }

    /**
     * [force] is true only from pull-to-refresh: the backing reads are cached,
     * so routine recomposition and paging never re-hit the server.
     */
    fun load(force: Boolean = false) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true, error = null, pagingError = null, exhausted = false)
            cloudSync.feed(limit = pageSize)
                .onSuccess { page ->
                    oldestMs = page.mapNotNull { it.completedAtMs }.minOrNull()
                    _ui.value = _ui.value.copy(entries = page)
                }
                .onFailure { _ui.value = _ui.value.copy(error = it.reason()) }
            _ui.value = _ui.value.copy(loading = false)
            // Ally rows ride along on the same refresh: one cached friends read
            // covers every card's ADD ALLY button.
            cloudSync.friends(force = force).onSuccess { rows ->
                _ui.value = _ui.value.copy(friends = rows.associateBy { it.userId })
            }
        }
    }

    /**
     * Optimistic reaction: chip and counts move now, the network call
     * reconciles. [reaction] null removes the lifter's reaction.
     */
    fun react(entry: FeedEntry, reaction: Reaction?) {
        // One wire call per workout at a time: a second pick before the first
        // answers would compute its delta from a state that may still roll
        // back, drifting the total by one for a single reaction.
        if (entry.sessionId in reactionCallsInFlight) return
        // Decide from current state, not the possibly-stale entry copy.
        val before = _ui.value.entries.firstOrNull { it.sessionId == entry.sessionId } ?: entry
        if (before.myReaction == reaction) return
        reactionCallsInFlight += entry.sessionId
        _ui.value = _ui.value.copy(reactionErrors = _ui.value.reactionErrors - entry.sessionId)
        replaceEntry(entry.sessionId) { it.withReaction(reaction) }
        viewModelScope.launch {
            cloudSync.react(entry.sessionId, reaction).onFailure {
                // Roll back so a refused write never leaves a phantom reaction,
                // and tell the lifter — a silent rollback reads as a dead button.
                replaceEntry(entry.sessionId) { current ->
                    current.copy(
                        likedByMe = before.likedByMe,
                        likeCount = before.likeCount,
                        reactions = before.reactions,
                        myReaction = before.myReaction,
                    )
                }
                _ui.value = _ui.value.copy(reactionErrors = _ui.value.reactionErrors + (entry.sessionId to it.reason()))
            }
            reactionCallsInFlight -= entry.sessionId
        }
    }

    private fun replaceEntry(sessionId: String, change: (FeedEntry) -> FeedEntry) {
        _ui.value = _ui.value.copy(
            entries = _ui.value.entries.map { if (it.sessionId == sessionId) change(it) else it },
        )
    }

    /** Send an ally request from a feed card; optimistic pending, then settled from server truth. */
    fun addAlly(userId: String) {
        // Any existing row means already requested, incoming, or allies — a second
        // tap must never fire another insert.
        if (userId in _ui.value.friends || userId in _ui.value.requestedAlly) return
        _ui.value = _ui.value.copy(requestedAlly = _ui.value.requestedAlly + userId, allyError = null)
        viewModelScope.launch {
            cloudSync.requestFriendById(userId)
                .onSuccess {
                    cloudSync.friends(force = true).onSuccess { rows ->
                        _ui.value = _ui.value.copy(friends = rows.associateBy { it.userId })
                    }
                }
                .onFailure {
                    // The optimistic revert used to be the whole story: the row
                    // snapped back and nothing said why.
                    _ui.value = _ui.value.copy(
                        requestedAlly = _ui.value.requestedAlly - userId,
                        allyError = it.message ?: it::class.simpleName ?: "Unknown failure",
                    )
                }
        }
    }

    /**
     * Fetch the next older page. Guards keep the request one-shot: a page in
     * flight, a board already at its end, or an unchanged cursor (nothing new
     * was fetched since) each short-circuit, so the same window is never
     * re-requested by repeated end-of-list triggers.
     */
    fun loadMore() {
        val current = _ui.value
        // A stale pagingError must not block the next attempt: the error is
        // cleared here and re-set only if this page fails too, so one flaky
        // page never wedges infinite scroll until a full refresh.
        if (current.loading || current.loadingMore || current.exhausted) return
        val cursor = oldestMs ?: return
        _ui.value = current.copy(loadingMore = true, pagingError = null)
        viewModelScope.launch {
            cloudSync.feed(limit = pageSize, beforeMs = cursor)
                .onSuccess { page ->
                    // Nothing arrived means the cursor covers everything left.
                    if (page.isEmpty() || page.size < pageSize) {
                        _ui.value = _ui.value.copy(exhausted = true)
                    }
                    val known = _ui.value.entries.map { it.sessionId }.toSet()
                    _ui.value = _ui.value.copy(entries = _ui.value.entries + page.filter { it.sessionId !in known })
                    page.mapNotNull { it.completedAtMs }.minOrNull()?.let { newest ->
                        // Only advance the cursor; re-requesting the window would duplicate work.
                        if (oldestMs == null || newest < oldestMs!!) oldestMs = newest
                    }
                }
                .onFailure { _ui.value = _ui.value.copy(pagingError = it.reason()) }
            _ui.value = _ui.value.copy(loadingMore = false)
        }
    }
}

@Composable
fun FeedScreen(
    onOpenLifter: (userId: String, displayName: String) -> Unit,
    onOpenComments: (sessionId: String, ownerId: String, headline: String) -> Unit,
    viewModel: FeedViewModel = viewModel(
        factory = viewModelFactory {
            initializer { FeedViewModel(ironvellumCloudSync(), ironvellumAccount(), ironvellumRepository()) }
        },
    ),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val equippedFrame by viewModel.equippedFrame.collectAsStateWithLifecycle()

    // SocialScreen owns the margins, the top gap and the signed-out screen, so
    // every tab starts its content at the same spot under the pills.
    Column(Modifier.fillMaxSize()) {
        val err = ui.error
        when {
            ui.loading && ui.entries.isEmpty() -> LoadingPanel()
            ui.entries.isEmpty() && err != null -> ErrorPanel(err, onRetry = { viewModel.load(force = true) })
            ui.entries.isEmpty() -> EmptyFeed(onRefresh = { viewModel.load(force = true) })
            else -> {
                // A failed refresh must not hide behind yesterday's rows: the
                // banner rides above the list, rows stay in place.
                if (ui.error != null) {
                    SocialErrorBanner("The ink has faded — these tidings are from your last sync: ${ui.error}")
                    Spacer(Modifier.height(10.dp))
                }
                ui.allyError?.let {
                    SocialErrorBanner("Ally request failed: $it")
                    Spacer(Modifier.height(10.dp))
                }
                Feed(
                    ui,
                    onRefresh = { viewModel.load(force = true) },
                    onLoadMore = viewModel::loadMore,
                    onOpenLifter = onOpenLifter,
                    onReact = viewModel::react,
                    onOpenComments = { entry -> onOpenComments(entry.sessionId, entry.userId, entry.headline()) },
                    onAddAlly = viewModel::addAlly,
                    equippedFrame = equippedFrame,
                )
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun LoadingPanel() {
    InkPanel(Modifier.fillMaxWidth()) {
        Text(
            "Reading the tidings…",
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = ChakraPetch,
            color = IronvellumColors.InkMuted,
        )
    }
}

@Composable
private fun EmptyFeed(onRefresh: () -> Unit) {
    Text(
        "No tidings yet. Make your trials public in Account settings and be the first.",
        style = MaterialTheme.typography.bodySmall,
        color = IronvellumColors.InkMuted,
    )
    SocialRefreshLink(onClick = onRefresh, label = "Check again")
}

/**
 * Compact failure note over stale rows — never a dialog, never displacing the
 * list. Shared with the inbox and comments so a refusal reads the same everywhere.
 */
@Composable
internal fun SocialErrorBanner(message: String) {
    // One DangerRed line: no box, no border. Callers that can retry put a
    // SocialRefreshLink ("Try again") right under it.
    Text(
        message,
        style = MaterialTheme.typography.bodySmall,
        color = IronvellumColors.DangerRed,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ErrorPanel(reason: String, onRetry: () -> Unit) {
    Text(
        "The Ledger refused: $reason",
        style = MaterialTheme.typography.bodySmall,
        color = IronvellumColors.InkMuted,
    )
    SocialRefreshLink(onClick = onRetry, label = "Try again")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Feed(
    ui: FeedUi,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenLifter: (String, String) -> Unit,
    onReact: (FeedEntry, Reaction?) -> Unit,
    onOpenComments: (FeedEntry) -> Unit,
    onAddAlly: (String) -> Unit,
    equippedFrame: String?,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            "Every Ironbound's public trials, newest first",
            style = MaterialTheme.typography.labelMedium,
            fontFamily = ChakraPetch,
            color = IronvellumColors.InkMuted,
            modifier = Modifier.weight(1f),
        )
    }
    Spacer(Modifier.height(10.dp))

    val listState = rememberLazyListState()
    val pullState = rememberPullToRefreshState()
    // One trigger at the tail: near the last item, ask for the next page; the
    // VM's guards make repeated triggers harmless.
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            last >= listState.layoutInfo.totalItemsCount - 3 && listState.layoutInfo.totalItemsCount > 0
        }
    }
    // Keyed on the item count too: if an appended page still leaves the last
    // visible item within 3 of the end, `nearEnd` alone would never re-fire and
    // paging would stall. The VM's loadingMore guard keeps this one request.
    LaunchedEffect(nearEnd, ui.entries.size) {
        if (nearEnd) onLoadMore()
    }

    // Swipe-down is the idiom people expect from a feed; the explicit control
    // survives only in the empty/error states, where there is no list to pull.
    PullToRefreshBox(
        isRefreshing = ui.loading,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pullState,
                isRefreshing = ui.loading,
                modifier = Modifier.align(Alignment.TopCenter),
                containerColor = IronvellumColors.Vault,
                color = IronvellumColors.EmeraldBright,
            )
        },
        state = pullState,
    ) {
        LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(ui.entries, key = { it.sessionId }) { entry ->
            FeedCard(
                entry = entry,
                isMe = entry.userId == ui.myUserId,
                equippedFrame = equippedFrame,
                ally = allyStateFor(entry.userId, ui),
                onOpenLifter = onOpenLifter,
                onReact = onReact,
                onOpenComments = onOpenComments,
                reactionError = ui.reactionErrors[entry.sessionId],
                onAddAlly = onAddAlly,
            )
        }
        if (ui.pagingError != null) {
            item(key = "paging-error") {
                Text(
                    "Older trials slipped away — keep pulling to try again.",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = ChakraPetch,
                    color = IronvellumColors.DangerRed,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                )
            }
        }
        if (ui.loadingMore) {
            item(key = "loading-more") {
                Text(
                    "Turning to older trials…",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = ChakraPetch,
                    color = IronvellumColors.InkMuted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                )
            }
        }
        if (ui.exhausted) {
            item(key = "end") {
                Text(
                    "You've reached the first page of the tidings.",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = ChakraPetch,
                    color = IronvellumColors.InkMuted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                )
            }
        }
        }
    }
}
internal enum class AllyState { None, Pending, Incoming, Ally }

/** Resolve a lifter's ally button state from the cached friends rows plus optimistic requests. */
private fun allyStateFor(userId: String, ui: FeedUi): AllyState {
    val row = ui.friends[userId] ?: return if (userId in ui.requestedAlly) AllyState.Pending else AllyState.None
    return when {
        row.accepted -> AllyState.Ally
        row.incoming -> AllyState.Incoming
        else -> AllyState.Pending // a row we requested and they have not accepted yet
    }
}
@Composable
private fun FeedCard(
    entry: FeedEntry,
    isMe: Boolean,
    equippedFrame: String?,
    ally: AllyState,
    reactionError: String?,
    onOpenLifter: (String, String) -> Unit,
    onReact: (FeedEntry, Reaction?) -> Unit,
    onOpenComments: (FeedEntry) -> Unit,
    onAddAlly: (String) -> Unit,
) {
    // The picker opens inline under the action row rather than as a popup:
    // a menu anchored to a 44dp chip covered the stat strip it reacts to.
    var picking by remember { mutableStateOf(false) }
    // The whole card opens the workout: the identity row, chips and ally chip
    // keep their own clickables, which consume the tap before the panel sees it.
    InkPanel(Modifier.fillMaxWidth(), onClick = { onOpenComments(entry) }) {
        Column {
            // Identity header carries only the LV chip, so the worn title keeps a
            // wide column and sits directly under the name. The ally control is
            // bulky, so it rides the action row at the card's foot instead.
            IdentityRow(
                displayName = entry.displayName,
                userId = entry.userId,
                wornTitle = entry.currentTitleId?.let { Titles.byId(it)?.name },
                titleId = entry.currentTitleId,
                level = entry.level,
                size = IdentitySize.Hero,
                isMe = isMe,
                frameId = if (isMe) equippedFrame else null,
                onClick = { onOpenLifter(entry.userId, entry.displayName) },
            )
            // User-authored title, falling back to the drill label when untitled.
            // Generic hunt glyph: no activity-type field exists in FeedEntry yet.
            if (entry.title.isNotBlank() || entry.label.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    entry.title.ifBlank { entry.label },
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = ChakraPetch,
                    fontWeight = FontWeight.Bold,
                    color = IronvellumColors.Ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (entry.note.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    entry.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            MovementLine(entry)

            Spacer(Modifier.height(10.dp))
            StatLine(entry)

            // The stamp has its own line: the action row now carries reaction,
            // comment and ally controls, and a stamp beside all three pushed the
            // ally chip off a 360dp card. relativeTime already falls back to the
            // date for anything older than yesterday, so stamp() prints it once.
            entry.completedAtMs?.let { ms ->
                Spacer(Modifier.height(6.dp))
                Text(
                    // An amended workout says so, or an ally who saw it before
                    // is left wondering why the figures moved.
                    stamp(ms) + if (entry.editedAtMs != null) " · amended" else "",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = ChakraPetch,
                    color = IronvellumColors.InkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(4.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Optimistic: the button shows the lifter's own reaction the
                // moment it is picked; the VM reconciles or rolls back.
                CountButton(
                    icon = (entry.myReaction ?: Reaction.SALUTE).glyph(),
                    iconDescription = entry.myReaction?.let { "Your tribute: ${it.displayName()}" } ?: "Pay tribute",
                    count = entry.likeCount,
                    lit = entry.myReaction != null || picking,
                    expanded = picking,
                    onClick = { picking = !picking },
                )
                CountButton(
                    icon = Icons.Outlined.ChatBubbleOutline,
                    iconDescription = "Remarks",
                    count = entry.commentCount,
                    lit = false,
                    onClick = { onOpenComments(entry) },
                )
                Spacer(Modifier.weight(1f))
                if (!isMe) {
                    // The one offer is a chip; a settled state is plain muted text.
                    when (ally) {
                        AllyState.None -> InkChip(label = "Add ally", icon = Icons.Outlined.PersonAdd) { onAddAlly(entry.userId) }
                        AllyState.Pending, AllyState.Incoming -> AllyNote("Pending")
                        AllyState.Ally -> AllyNote("Ally")
                    }
                }
            }
            if (picking) {
                ReactionPicker(
                    current = entry.myReaction,
                    counts = entry.reactions,
                    onPick = { reaction ->
                        picking = false
                        onReact(entry, reaction)
                    },
                )
            }
            // Reserved-height failure line: the space exists whether or not a
            // reaction failed, so surfacing the message never reflows the card.
            Box(Modifier.fillMaxWidth().height(18.dp)) {
                if (reactionError != null) {
                    Text(
                        "Tribute not saved: $reactionError",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = ChakraPetch,
                        color = IronvellumColors.DangerRed,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }

}

/**
 * Icon + count as an unboxed 44dp button. [lit] (the lifter's own tribute, or the picker open)
 * reads in Ink; everything else is InkMuted. [expanded] tells a screen reader the picker is open.
 */
@Composable
private fun CountButton(
    icon: ImageVector,
    iconDescription: String,
    count: Int,
    lit: Boolean,
    onClick: () -> Unit,
    expanded: Boolean? = null,
) {
    val tint = if (lit) IronvellumColors.Ink else IronvellumColors.InkMuted
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clip(MaterialTheme.shapes.extraSmall)
            .clickable(role = Role.Button) { onClick() }
            .then(if (expanded != null) Modifier.semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" } else Modifier)
            .padding(horizontal = 8.dp),
    ) {
        Icon(icon, contentDescription = iconDescription, tint = tint, modifier = Modifier.size(18.dp))
        Text(
            "$count",
            style = MaterialTheme.typography.labelMedium,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.SemiBold,
            color = tint,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** A settled ally state beside the action buttons: muted text, no outline, no tap. */
@Composable
private fun AllyNote(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        fontFamily = ChakraPetch,
        color = IronvellumColors.InkMuted,
        modifier = Modifier.padding(end = 8.dp),
    )
}

/**
 * The three tributes plus Remove once one is set, as a row of 48dp radio options under a hairline;
 * the chosen one carries the shared primary underline. Tapping the lifter's current tribute again
 * is a no-op rather than a removal: a toggle hidden in the selected option deleted tributes people
 * meant to confirm.
 */
@Composable
internal fun ReactionPicker(current: Reaction?, counts: Map<Reaction, Int>, onPick: (Reaction?) -> Unit) {
    InkDivider(Modifier.padding(top = 4.dp))
    Row(
        Modifier
            .fillMaxWidth()
            .selectableGroup(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Reaction.entries.forEachIndexed { index, reaction ->
            val on = reaction == current
            val count = counts[reaction] ?: 0
            PickerOption(
                label = if (count > 0) "${reaction.displayName()} $count" else reaction.displayName(),
                icon = reaction.glyph(),
                on = on,
                first = index == 0,
                onClick = { onPick(reaction) },
            )
        }
        Spacer(Modifier.weight(1f))
        if (current != null) {
            Text(
                "Remove",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = ChakraPetch,
                color = IronvellumColors.InkMuted,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button) { onPick(null) }
                    .padding(start = 12.dp)
                    .wrapContentHeight(),
            )
        }
    }
}

@Composable
private fun PickerOption(label: String, icon: ImageVector, on: Boolean, first: Boolean, onClick: () -> Unit) {
    val tint = if (on) IronvellumColors.Ink else IronvellumColors.InkMuted
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .heightIn(min = 48.dp)
            .selectedUnderline(on)
            .selectable(selected = on, role = Role.RadioButton, onClick = onClick)
            .padding(start = if (first) 0.dp else 12.dp, end = 12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = ChakraPetch,
            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
            color = tint,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** One glyph per reaction, shared by the feed, the likers list and the inbox. */
internal fun Reaction.glyph(): ImageVector = when (this) {
    Reaction.SALUTE -> Icons.Outlined.MilitaryTech
    Reaction.IRON -> Icons.Outlined.FitnessCenter
    Reaction.FLAME -> Icons.Outlined.LocalFireDepartment
}

internal fun Reaction.displayName(): String = when (this) {
    Reaction.SALUTE -> "Honour"
    Reaction.IRON -> "Iron"
    Reaction.FLAME -> "Flame"
}

/** What a workout is called on the comments screen and in the inbox. */
internal fun FeedEntry.headline(): String = title.ifBlank { label }.ifBlank { "Trial" }

/**
 * The entry as it reads once [next] replaces the lifter's reaction. like_count
 * stays the total of every kind, so it moves only when a reaction appears or
 * disappears — switching kind moves the per-kind counts, never the total.
 */
private fun FeedEntry.withReaction(next: Reaction?): FeedEntry {
    val previous = myReaction
    val counts = reactions.toMutableMap()
    if (previous != null) {
        val left = (counts[previous] ?: 0) - 1
        if (left > 0) counts[previous] = left else counts.remove(previous)
    }
    if (next != null) counts[next] = (counts[next] ?: 0) + 1
    val delta = (if (next != null) 1 else 0) - (if (previous != null) 1 else 0)
    return copy(
        myReaction = next,
        likedByMe = next != null,
        reactions = counts,
        likeCount = (likeCount + delta).coerceAtLeast(0),
    )
}

/**
 * One line of figures: "12 sets · 96 reps · +140 XP". XP is the only gold on the card, as earned.
 * The middle figure follows whichever figure the trial's payload actually carries.
 */
@Composable
private fun StatLine(entry: FeedEntry) {
    // `reps_done` arrives as one number with no metric split, so a climb's attempts are only
    // identifiable when the hunt has a hardest grade and no load-bearing set at all.
    val climbOnly = entry.bestSet.isNullOrBlank() && !entry.hardestGrade.isNullOrBlank()
    val timed = !climbOnly && entry.repsDone == 0 && entry.heldSeconds > 0
    val middle = when {
        climbOnly && entry.repsDone > 0 -> "${entry.repsDone} attempts"
        timed -> "${entry.heldSeconds}s held"
        entry.repsDone == 0 && (entry.distanceM ?: 0.0) > 0 -> "${formatDistance(entry.distanceM ?: 0.0)} covered"
        entry.repsDone == 0 && (entry.durationSec ?: 0) >= 60 -> formatDuration(entry.durationSec ?: 0)
        entry.repsDone == 0 && entry.setsDone > 0 -> null
        else -> "${entry.repsDone} reps"
    }
    val plain = listOfNotNull("${entry.setsDone} ${if (entry.setsDone == 1) "set" else "sets"}", middle).joinToString(" · ")
    Text(
        buildAnnotatedString {
            append(plain)
            append(" · ")
            withStyle(SpanStyle(color = IronvellumColors.SovereignGold, fontWeight = FontWeight.SemiBold)) {
                append("+${entry.xpAwarded} XP")
            }
        },
        style = MaterialTheme.typography.labelMedium,
        fontFamily = ChakraPetch,
        color = IronvellumColors.Ink,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * What was trained: the heaviest-volume movement line plus a headline chip for
 * the best set. Both fields are null for pre-migration sessions, so the whole
 * block vanishes cleanly instead of rendering empty chips.
 */
@Composable
private fun MovementLine(entry: FeedEntry) {
    val movements = entry.topMovements?.takeIf { it.isNotBlank() }
    // The view decides a hunt's shape: `best_set` is null unless a genuinely
    // load-bearing set exists, so a run no longer reports "1 x BW" and this
    // screen needs no cardio special-case of its own. A run's substance is its
    // distance, a climb's is its grade — headline whichever the hunt has.
    // The view writes "8 x 100.0 kg"; the card shows the app's own "8×100.0 kg".
    val headline = entry.bestSet?.takeIf { it.isNotBlank() }?.let { "Best ${it.replace(" x ", "×")}" }
        ?: entry.hardestGrade?.takeIf { it.isNotBlank() }?.let { "Hardest $it" }
        ?: entry.distanceM?.takeIf { it > 0 }?.let { "${formatDistance(it)} covered" }
    // One muted line: the best set leads, then the exercise count and duration.
    val meta = buildList {
        headline?.let { add(it) }
        if (entry.movementCount > 0) {
            add("${entry.movementCount} ${if (entry.movementCount == 1) "exercise" else "exercises"}")
        }
        entry.durationSec?.takeIf { it >= 60 }?.let { add(formatDuration(it)) }
    }
    // Bail only when there is NOTHING to show.
    if (movements == null && meta.isEmpty()) return
    if (movements != null) {
        Spacer(Modifier.height(10.dp))
        Text(
            movements,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = ChakraPetch,
            // The movements ARE the post's substance, so they read at full ink.
            color = IronvellumColors.Ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
    if (meta.isNotEmpty()) {
        Spacer(Modifier.height(3.dp))
        Text(
            meta.joinToString(" · "),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            color = IronvellumColors.InkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}


/** 48m under the hour, "1h 12m" past it, whole hours drop the zero minutes. */
/** Metres read as km past 1000 — "10.0 KM" beats "10000 M" on a card. */
private fun formatDistance(metres: Double): String =
    if (metres >= 1000) String.format(Locale.ENGLISH, "%.1f KM", metres / 1000) else "${metres.toInt()} M"

private fun formatDuration(sec: Int): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        else -> "${m}m"
    }
}

/**
 * Human-fresh timestamp: minutes under the hour, hours today, "yesterday",
 * then an absolute date via the shared formatter. Shared with the comments
 * and inbox rows so every social surface dates things the same way.
 */
internal fun relativeTime(ms: Long): String {
    val now = java.time.LocalDateTime.now()
    val then = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDateTime()
    val minutes = ChronoUnit.MINUTES.between(then, now)
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        ChronoUnit.HOURS.between(then, now) < 24 && then.toLocalDate() == now.toLocalDate() ->
            "${ChronoUnit.HOURS.between(then, now)}h ago"
        then.toLocalDate() == now.toLocalDate().minusDays(1) -> "yesterday"
        else -> formatDate(ms, "MMM d")
    }
}

/**
 * One stamp per card. Recent sessions read relatively ("2h ago"); anything older
 * than yesterday gets the absolute date and time. Printing both produced
 * "Sept 11 · Sept 11 · 14:53", because relativeTime already falls back to the
 * date itself.
 */
private fun stamp(ms: Long): String {
    val relative = relativeTime(ms)
    val absolute = formatDate(ms)
    return if (relative == formatDate(ms, "MMM d")) absolute else "$relative · $absolute"
}

@Composable
internal fun SocialRefreshLink(onClick: () -> Unit, label: String) {
    // A sentence-case text link, 44dp tall.
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clip(MaterialTheme.shapes.extraSmall)
            .clickable(role = Role.Button) { onClick() }
            .padding(horizontal = 2.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.SemiBold,
            color = IronvellumColors.SystemGreen,
            letterSpacing = 0.5.sp,
        )
    }
}
