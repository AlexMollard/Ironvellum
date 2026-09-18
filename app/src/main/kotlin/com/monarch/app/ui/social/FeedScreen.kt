package com.monarch.app.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WorkspacePremium
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.alpha
import com.monarch.app.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.monarch.app.data.Repository
import com.monarch.app.data.cloud.Cloud
import com.monarch.app.data.cloud.CloudSync
import com.monarch.app.data.cloud.FeedEntry
import com.monarch.app.data.cloud.FriendRow
import com.monarch.app.data.cloud.Liker
import com.monarch.app.domain.Titles
import com.monarch.app.ui.components.MonarchButton
import com.monarch.app.ui.components.SystemWindow
import com.monarch.app.ui.components.formatDate
import com.monarch.app.ui.monarchAccount
import com.monarch.app.ui.monarchCloudSync
import com.monarch.app.ui.monarchRepository
import com.monarch.app.ui.theme.ChakraPetch
import com.monarch.app.ui.theme.inkBorder
import com.monarch.app.ui.theme.MonarchColors
import com.monarch.app.ui.theme.MonarchTracking
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Snapshot of the public board: gate, entries, paging cursor, and refresh state. */
data class FeedUi(
    val configured: Boolean = Cloud.configured,
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
    val likers: Map<String, List<Liker>> = emptyMap(),
    val likersErrors: Map<String, String> = emptyMap(),
    val likersLoadingSessionId: String? = null,
    /** Per-card like failures; keyed by sessionId so the message sits on the tapped card. */
    val likeErrors: Map<String, String> = emptyMap(),
)

class FeedViewModel(
    private val cloudSync: CloudSync,
    private val accountRepo: com.monarch.app.data.cloud.AccountRepository,
    private val repo: Repository,
    private val pageSize: Int = 50,
) : ViewModel() {

    private val _ui = MutableStateFlow(
        FeedUi(signedIn = accountRepo.account.value != null, myUserId = accountRepo.account.value?.userId),
    )
    val ui = _ui.asStateFlow()

    // Device-local equipped crest frame; only the signed-in hunter's own card wears it.
    val equippedFrame: StateFlow<String?> = repo.observeEquippedFrame()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Oldest completedAtMs seen — the cursor for the next page. */
    private var oldestMs: Long? = null

    /** Sessions with a like/unlike call in flight; blocks the double-tap re-fire that drifts the count. */
    private val likeCallsInFlight = mutableSetOf<String>()

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

    /** Optimistic like toggle: the count flips now, the network call reconciles. */
    fun toggleLike(entry: FeedEntry) {
        // One wire call per session at a time: a rapid double-tap before
        // recomposition would pass the same stale entry twice and drift the
        // optimistic count by two for a single like.
        if (entry.sessionId in likeCallsInFlight) return
        likeCallsInFlight += entry.sessionId
        // Decide the toggle from current state, not the possibly-stale entry copy.
        val wasLiked = _ui.value.entries.firstOrNull { it.sessionId == entry.sessionId }?.likedByMe ?: entry.likedByMe
        _ui.value = _ui.value.copy(likeErrors = _ui.value.likeErrors - entry.sessionId)
        applyLike(entry.sessionId, !wasLiked)
        viewModelScope.launch {
            val call = if (wasLiked) cloudSync.unlike(entry.sessionId) else cloudSync.like(entry.sessionId)
            call.onFailure {
                // Roll back so a refused write never leaves a phantom heart, and
                // tell the hunter — a silent rollback reads as a dead button.
                applyLike(entry.sessionId, wasLiked)
                _ui.value = _ui.value.copy(likeErrors = _ui.value.likeErrors + (entry.sessionId to it.reason()))
            }
            likeCallsInFlight -= entry.sessionId
        }
    }

    private fun applyLike(sessionId: String, liked: Boolean) {
        _ui.value = _ui.value.copy(
            entries = _ui.value.entries.map { current ->
                if (current.sessionId != sessionId) {
                    current
                } else {
                    current.copy(
                        likedByMe = liked,
                        likeCount = (current.likeCount + if (liked) 1 else -1).coerceAtLeast(0),
                    )
                }
            },
        )
    }

    /** Owner-only: fetch who liked a session, once — repeat opens read the cache. */
    fun loadLikers(sessionId: String) {
        if (sessionId in _ui.value.likers || sessionId in _ui.value.likersErrors) return
        if (_ui.value.likersLoadingSessionId == sessionId) return
        _ui.value = _ui.value.copy(likersLoadingSessionId = sessionId)
        viewModelScope.launch {
            cloudSync.likers(sessionId)
                .onSuccess { names -> _ui.value = _ui.value.copy(likers = _ui.value.likers + (sessionId to names)) }
                .onFailure { reason -> _ui.value = _ui.value.copy(likersErrors = _ui.value.likersErrors + (sessionId to reason.reason())) }

            _ui.value = _ui.value.copy(likersLoadingSessionId = null)
        }
    }
    /**
     * Retry a failed likers fetch: evict the cached error so loadLikers' guard
     * opens — without the eviction the dialog would show the same failure forever.
     */
    fun retryLikers(sessionId: String) {
        _ui.value = _ui.value.copy(likersErrors = _ui.value.likersErrors - sessionId)
        loadLikers(sessionId)
    }

    /** Closing the dialog drops the cached failure so the next open gets a fresh attempt. */
    fun dismissLikersError(sessionId: String) {
        _ui.value = _ui.value.copy(likersErrors = _ui.value.likersErrors - sessionId)
    }

    /** Send an ally request from a feed card; optimistic pending, then settled from server truth. */
    fun addAlly(userId: String) {
        // Any existing row means already requested, incoming, or allies — a second
        // tap must never fire another insert.
        if (userId in _ui.value.friends || userId in _ui.value.requestedAlly) return
        _ui.value = _ui.value.copy(requestedAlly = _ui.value.requestedAlly + userId)
        viewModelScope.launch {
            cloudSync.requestFriendById(userId)
                .onSuccess {
                    cloudSync.friends(force = true).onSuccess { rows ->
                        _ui.value = _ui.value.copy(friends = rows.associateBy { it.userId })
                    }
                }
                .onFailure { _ui.value = _ui.value.copy(requestedAlly = _ui.value.requestedAlly - userId) }
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
    onOpenHunter: (userId: String, displayName: String) -> Unit,
    viewModel: FeedViewModel = viewModel(
        factory = viewModelFactory {
            initializer { FeedViewModel(monarchCloudSync(), monarchAccount(), monarchRepository()) }
        },
    ),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val equippedFrame by viewModel.equippedFrame.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        Text(
            "THE FRONTLINE",
            style = MaterialTheme.typography.labelLarge,
            fontFamily = ChakraPetch,
            color = MonarchColors.InkMuted,
            letterSpacing = MonarchTracking.ScreenTitle,
        )
        // A subtitle only when it counts something. Every other case had a
        // panel below saying the same thing in a whole sentence, so the line
        // above it was decoration.
        if (ui.configured && ui.signedIn && ui.entries.isNotEmpty()) {
            Text(
                "${ui.entries.size} hunts witnessed",
                style = MaterialTheme.typography.labelLarge,
                fontFamily = ChakraPetch,
                color = MonarchColors.SystemGreen,
            )
        }
        Spacer(Modifier.height(12.dp))

        val err = ui.error
        when {
            !ui.configured -> NotConfigured()
            !ui.signedIn -> NotSignedIn()
            ui.loading && ui.entries.isEmpty() -> LoadingPanel()
            ui.entries.isEmpty() && err != null -> ErrorPanel(err, onRetry = { viewModel.load(force = true) })
            ui.entries.isEmpty() -> EmptyFeed(onRefresh = { viewModel.load(force = true) })
            else -> {
                // A failed refresh must not hide behind yesterday's rows: the
                // banner rides above the list, rows stay in place.
                if (ui.error != null) {
                    InlineErrorBanner("The newest fetch failed — these hunts are the last synced board: ${ui.error}")
                    Spacer(Modifier.height(10.dp))
                }
                Feed(
                    ui,
                    onRetryLikers = viewModel::retryLikers,
                    onLikersClosed = viewModel::dismissLikersError,
                    onRefresh = { viewModel.load(force = true) },
                    onLoadMore = viewModel::loadMore,
                    onOpenHunter = onOpenHunter,
                    onToggleLike = viewModel::toggleLike,
                    onShowLikers = viewModel::loadLikers,
                    onAddAlly = viewModel::addAlly,
                    equippedFrame = equippedFrame,
                )
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun NotConfigured() {
    SystemWindow(Modifier.fillMaxWidth(), accent = MonarchColors.DangerRed) {
        Text(
            "FEED OFFLINE",
            style = MaterialTheme.typography.labelLarge,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = MonarchColors.DangerRed,
            letterSpacing = MonarchTracking.InlineLabel,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "No Supabase endpoint is configured on this device, so the frontline is silent. Everything you log locally still counts.",
            style = MaterialTheme.typography.bodySmall,
            color = MonarchColors.InkMuted,
        )
    }
}

@Composable
private fun NotSignedIn() {
    SystemWindow(Modifier.fillMaxWidth(), accent = MonarchColors.SovereignGold) {
        Text(
            "SIGN IN TO WATCH THE FRONTLINE",
            style = MaterialTheme.typography.labelLarge,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = MonarchColors.SovereignGold,
            letterSpacing = MonarchTracking.InlineLabel,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Sign in on the ALLIES tab to join the watch.",
            style = MaterialTheme.typography.bodySmall,
            color = MonarchColors.InkMuted,
        )
    }
}

@Composable
private fun LoadingPanel() {
    SystemWindow(Modifier.fillMaxWidth(), accent = MonarchColors.Rune) {
        Text(
            "Scouting the frontline…",
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = ChakraPetch,
            color = MonarchColors.InkMuted,
        )
    }
}

@Composable
private fun EmptyFeed(onRefresh: () -> Unit) {
    SystemWindow(Modifier.fillMaxWidth(), accent = MonarchColors.Emerald) {
        Text(
            "NOBODY HAS MARCHED YET",
            style = MaterialTheme.typography.labelLarge,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = MonarchColors.EmeraldBright,
            letterSpacing = MonarchTracking.InlineLabel,
        )
        Spacer(Modifier.height(8.dp))
        Image(
            painter = painterResource(R.drawable.art_empty_board),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(150.dp)
                .alpha(0.55f),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "No hunts are public yet. Make yours visible from the account screen's visibility setting, then be the first name on the board.",
            style = MaterialTheme.typography.bodySmall,
            color = MonarchColors.InkMuted,
        )
        Spacer(Modifier.height(10.dp))
        RefreshLink(onClick = onRefresh, label = "Check again")
    }
}

/** Compact failure note over stale rows — never a dialog, never displacing the list. */
@Composable
private fun InlineErrorBanner(message: String) {
    val shape = MaterialTheme.shapes.small
    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(MonarchColors.VaultHigh, MonarchColors.Vault)), shape)
            .inkBorder(MonarchColors.DangerRed, shape, 1.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            message,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = ChakraPetch,
            color = MonarchColors.DangerRed,
        )
    }
}

@Composable
private fun ErrorPanel(reason: String, onRetry: () -> Unit) {
    SystemWindow(Modifier.fillMaxWidth(), accent = MonarchColors.DangerRed) {
        Text(
            "THE FEED STUTTERED",
            style = MaterialTheme.typography.labelLarge,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = MonarchColors.DangerRed,
            letterSpacing = MonarchTracking.InlineLabel,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "The System refused: $reason",
            style = MaterialTheme.typography.bodySmall,
            color = MonarchColors.InkMuted,
        )
        Spacer(Modifier.height(10.dp))
        RefreshLink(onClick = onRetry, label = "Try again")
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Feed(
    ui: FeedUi,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenHunter: (String, String) -> Unit,
    onToggleLike: (FeedEntry) -> Unit,
    onShowLikers: (String) -> Unit,
    onAddAlly: (String) -> Unit,
    onRetryLikers: (String) -> Unit,
    onLikersClosed: (String) -> Unit,
    equippedFrame: String?,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            "Every hunter's public hunts, newest first",
            style = MaterialTheme.typography.labelMedium,
            fontFamily = ChakraPetch,
            color = MonarchColors.InkMuted,
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
                containerColor = MonarchColors.Vault,
                color = MonarchColors.EmeraldBright,
            )
        },
        state = pullState,
    ) {
        LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(ui.entries, key = { it.sessionId }) { entry ->
            FeedCard(
                entry = entry,
                isMe = entry.userId == ui.myUserId,
                equippedFrame = equippedFrame,
                ally = allyStateFor(entry.userId, ui),
                likers = ui.likers[entry.sessionId],
                likersError = ui.likersErrors[entry.sessionId],
                onOpenHunter = onOpenHunter,
                onToggleLike = onToggleLike,
                likeError = ui.likeErrors[entry.sessionId],
                onRetryLikers = onRetryLikers,
                onLikersClosed = onLikersClosed,
                onShowLikers = onShowLikers,
                onAddAlly = onAddAlly,
            )
        }
        if (ui.pagingError != null) {
            item(key = "paging-error") {
                Text(
                    "Older hunts slipped away — keep pulling to try again.",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = ChakraPetch,
                    color = MonarchColors.DangerRed,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                )
            }
        }
        if (ui.loadingMore) {
            item(key = "loading-more") {
                Text(
                    "Drawing in older hunts…",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = ChakraPetch,
                    color = MonarchColors.InkMuted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                )
            }
        }
        if (ui.exhausted) {
            item(key = "end") {
                Text(
                    "You've reached the founding of the board.",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = ChakraPetch,
                    color = MonarchColors.InkMuted,
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

/** Resolve a hunter's ally button state from the cached friends rows plus optimistic requests. */
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
    likers: List<Liker>?,
    likersError: String?,
    likeError: String?,
    onOpenHunter: (String, String) -> Unit,
    onToggleLike: (FeedEntry) -> Unit,
    onShowLikers: (String) -> Unit,
    onAddAlly: (String) -> Unit,
    onRetryLikers: (String) -> Unit,
    onLikersClosed: (String) -> Unit,
) {
    val accent = if (isMe) MonarchColors.SovereignGold else MonarchColors.Emerald
    var showLikers by remember { mutableStateOf(false) }
    SystemWindow(Modifier.fillMaxWidth(), accent = accent) {
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
                onClick = { onOpenHunter(entry.userId, entry.displayName) },
            )
            // User-authored title, falling back to the drill label when untitled.
            // Generic hunt glyph: no activity-type field exists in FeedEntry yet.
            if (entry.title.isNotBlank() || entry.label.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        Icons.Outlined.FitnessCenter,
                        contentDescription = null,
                        tint = MonarchColors.EmeraldBright,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        entry.title.ifBlank { entry.label },
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = ChakraPetch,
                        fontWeight = FontWeight.Bold,
                        color = MonarchColors.Ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (entry.note.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    entry.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MonarchColors.InkMuted,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            MovementLine(entry)

            Spacer(Modifier.height(10.dp))
            StatStrip(entry)

            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Like toggle: optimistic count, heart reflects likedByMe instantly.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(MonarchColors.Abyss)
                        .inkBorder(if (entry.likedByMe) MonarchColors.SovereignGold else MonarchColors.Rune, MaterialTheme.shapes.extraSmall, 1.dp)
                        .clickable { onToggleLike(entry) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Icon(
                        if (entry.likedByMe) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = if (entry.likedByMe) "Liked" else "Like",
                        tint = if (entry.likedByMe) MonarchColors.SovereignGold else MonarchColors.InkMuted,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        "${entry.likeCount}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = ChakraPetch,
                        fontWeight = FontWeight.SemiBold,
                        color = if (entry.likedByMe) MonarchColors.SovereignGold else MonarchColors.Ink,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
                if (!isMe) {
                    Spacer(Modifier.width(8.dp))
                    AllyChip(ally) { onAddAlly(entry.userId) }
                }
                // Owner-only: the count's story is theirs to read. Non-owners
                // get no likers list. Timestamp rides the same row, right-aligned.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (isMe) {
                        Text(
                            "WHO CHEERED",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = ChakraPetch,
                            color = MonarchColors.EmeraldBright,
                            letterSpacing = MonarchTracking.InlineLabel,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.extraSmall)
                                .clickable {
                                    showLikers = true
                                    onShowLikers(entry.sessionId)
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                        )
                    }
                    entry.completedAtMs?.let { ms ->
                        // relativeTime already falls back to the date for
                        // anything older than yesterday, so printing both
                        // rendered "Sept 11 · Sept 11 · 14:53".
                        Text(
                            stamp(ms),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = ChakraPetch,
                            color = MonarchColors.InkMuted,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
            // Reserved-height failure line: the space exists whether or not a
            // like failed, so surfacing the message never reflows the card.
            Box(Modifier.fillMaxWidth().height(18.dp)) {
                if (likeError != null) {
                    Text(
                        "The System refused your cheer",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = ChakraPetch,
                        color = MonarchColors.DangerRed,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }

    if (showLikers) {
        // Dismissal evicts the cached failure so the next open retries fresh
        // instead of showing the same error forever.
        Dialog(onDismissRequest = {
            showLikers = false
            onLikersClosed(entry.sessionId)
        }) {
            SystemWindow(Modifier.fillMaxWidth(), accent = MonarchColors.SovereignGold) {
                Text(
                    "CHEERS FROM THE GUILD",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = ChakraPetch,
                    fontWeight = FontWeight.Bold,
                    color = MonarchColors.SovereignGold,
                    letterSpacing = MonarchTracking.SectionHeader,
                )
                Spacer(Modifier.height(10.dp))
                when {
                    likers == null && likersError == null -> Text(
                        "Summoning the cheers…",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = ChakraPetch,
                        color = MonarchColors.InkMuted,
                    )
                    likersError != null -> Column {
                        Text(
                            "The System refused: $likersError",
                            style = MaterialTheme.typography.bodySmall,
                            color = MonarchColors.DangerRed,
                        )
                        Spacer(Modifier.height(8.dp))
                        MonarchButton(
                            label = "TRY AGAIN",
                            onClick = { onRetryLikers(entry.sessionId) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    likers!!.isEmpty() -> Text(
                        "No cheers yet — your deeds still speak for themselves.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MonarchColors.InkMuted,
                    )
                    else -> likers.forEach { liker ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                liker.displayName,
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = ChakraPetch,
                                color = MonarchColors.Ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                relativeTime(liker.likedAtMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = MonarchColors.InkMuted,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                MonarchButton(
                    label = "Close",
                    onClick = {
                        showLikers = false
                        onLikersClosed(entry.sessionId)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
    }
        }
}

/**
 * Ally state as a status chip riding the identity row's trailing slot. Only a
 * genuine ADD ALLY offer is tappable; settled states render inert so they never
 * look like a primary CTA.
 */
@Composable
private fun AllyChip(ally: AllyState, onAddAlly: () -> Unit) {
    val (label, tint) = when (ally) {
        AllyState.None -> "ADD ALLY" to MonarchColors.EmeraldBright
        AllyState.Pending -> "PENDING" to MonarchColors.InkMuted
        AllyState.Incoming -> "PENDING" to MonarchColors.SovereignGold
        AllyState.Ally -> "ALLY" to MonarchColors.SovereignGold
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .background(MonarchColors.Abyss)
            .inkBorder(if (ally == AllyState.None) MonarchColors.Emerald else MonarchColors.Rune, MaterialTheme.shapes.extraSmall, 1.dp)
            .clickable(enabled = ally == AllyState.None) { onAddAlly() }
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Icon(
            Icons.Outlined.PersonAdd,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(12.dp),
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.SemiBold,
            color = tint,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** Icon + short-value stat strip — the card's spine, not a footnote. */
@Composable
private fun StatStrip(entry: FeedEntry) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraSmall)
            // Gradient plate gives the spine weight over the flat card body.
            .background(Brush.linearGradient(listOf(MonarchColors.VaultHigh, MonarchColors.Vault)))
            .inkBorder(MonarchColors.Rune, MaterialTheme.shapes.extraSmall, 1.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // FOUR stats, fixed. Six crammed into one row wrapped "MOVES" to
        // "MOVE/S" and truncated XP to "+6…" on a 1080px screen; movement count
        // and duration ride the movement line instead.
        Stat(Icons.Outlined.FitnessCenter, "${entry.setsDone}", "SETS", modifier = Modifier.weight(1f))
        Stat(Icons.Outlined.Repeat, "${entry.repsDone}", "REPS", modifier = Modifier.weight(1f))
        Stat(Icons.Outlined.Bolt, "+${entry.xpAwarded}", "XP", tint = MonarchColors.Emerald, modifier = Modifier.weight(1f))
        Stat(Icons.Outlined.WorkspacePremium, "${entry.strengthScore}", "STR", tint = MonarchColors.SovereignGold, modifier = Modifier.weight(1f))
    }
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
    val headline = entry.bestSet?.takeIf { it.isNotBlank() }?.let { "BEST $it" }
        ?: entry.hardestGrade?.takeIf { it.isNotBlank() }?.let { "HARDEST $it" }
        ?: entry.distanceM?.takeIf { it > 0 }?.let { "${formatDistance(it)} COVERED" }
    val meta = buildList {
        if (entry.movementCount > 0) {
            add("${entry.movementCount} ${if (entry.movementCount == 1) "MOVEMENT" else "MOVEMENTS"}")
        }
        entry.durationSec?.takeIf { it >= 60 }?.let { add(formatDuration(it)) }
    }
    // Bail only when there is NOTHING to show. Computing `meta` after an early
    // return dropped the movement/duration line for a hunt that had no movement
    // names and no headline — the one case where meta was all it had.
    if (movements == null && headline == null && meta.isEmpty()) return
    // Meta-only hunts skip the row entirely: an empty Row with just a weighted
    // Spacer would draw a blank band above the meta line.
    if (movements != null || headline != null) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
        if (movements != null) {
            Icon(
                Icons.Outlined.Category,
                contentDescription = null,
                tint = MonarchColors.EmeraldBright,
                modifier = Modifier.size(16.dp),
            )
            Text(
                movements,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = ChakraPetch,
                // The movements ARE the post's substance, so they read at full
                // ink rather than the muted tone used for incidental meta.
                color = MonarchColors.Ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        if (headline != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(MonarchColors.Abyss)
                    .inkBorder(MonarchColors.Rune, MaterialTheme.shapes.extraSmall, 1.dp)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.TrendingUp,
                    contentDescription = null,
                    tint = MonarchColors.SovereignGold,
                    modifier = Modifier.size(12.dp),
                )
                Text(
                    headline,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = ChakraPetch,
                    fontWeight = FontWeight.SemiBold,
                    color = MonarchColors.SovereignGold,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
        }
    }
    if (meta.isNotEmpty()) {
        Spacer(Modifier.height(4.dp))
        Text(
            meta.joinToString("  ·  "),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            // Rune is the border token (#2A2D35): as text on Vault it was
            // near-invisible. InkMuted is the muted TEXT token.
            color = MonarchColors.InkMuted,
            letterSpacing = MonarchTracking.InlineLabel,
            maxLines = 1,
            softWrap = false,
        )
    }
}


/** 48m under the hour, "1h 12m" past it, whole hours drop the zero minutes. */
/** Metres read as km past 1000 — "10.0 KM" beats "10000 M" on a card. */
private fun formatDistance(metres: Double): String =
    if (metres >= 1000) "%.1f KM".format(metres / 1000) else "${metres.toInt()} M"

private fun formatDuration(sec: Int): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        else -> "${m}m"
    }
}

@Composable
private fun Stat(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
    tint: Color = MonarchColors.InkMuted,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(14.dp))
        Column {
            Text(
                value,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.SemiBold,
                color = MonarchColors.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = ChakraPetch,
                fontSize = 9.sp,
                color = MonarchColors.InkMuted,
                letterSpacing = MonarchTracking.InlineLabel,
            )
        }
    }
}

/**
 * Human-fresh timestamp: minutes under the hour, hours today, "yesterday",
 * then an absolute date via the shared formatter.
 */
private fun relativeTime(ms: Long): String {
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
 * One stamp per card. Recent hunts read relatively ("2h ago"); anything older
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
private fun RefreshLink(onClick: () -> Unit, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .clickable { onClick() }
            .padding(vertical = 4.dp, horizontal = 2.dp),
    ) {
        Icon(
            Icons.Outlined.Refresh,
            contentDescription = null,
            tint = MonarchColors.Emerald,
            modifier = Modifier.size(14.dp),
        )
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.SemiBold,
            color = MonarchColors.Emerald,
            letterSpacing = MonarchTracking.InlineLabel,
        )
    }
}
