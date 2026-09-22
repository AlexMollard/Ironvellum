package com.ironvellum.app.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextAlign
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
import com.ironvellum.app.data.cloud.LeaderboardRow
import com.ironvellum.app.data.cloud.ShadowBoardRow
import com.ironvellum.app.domain.Titles
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.ironvellumAccount
import com.ironvellum.app.ui.ironvellumCloudSync
import com.ironvellum.app.ui.ironvellumRepository
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.components.InkRail
import com.ironvellum.app.ui.components.plural
import com.ironvellum.app.ui.theme.inkBorder
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.IronvellumTracking
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Full snapshot of the leader board state: config, session, and data state. */
data class LeaderboardUi(
    val configured: Boolean = Cloud.configured,
    val signedIn: Boolean = false,
    val myUserId: String? = null,
    val rows: List<LeaderboardRow> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

/** Snapshot of the muster roll board: rows, loading and failure state, independent of the training board. */
data class MusterBoardUi(
    val rows: List<ShadowBoardRow> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    /** Set once a fetch has completed, so the first selection can trigger a lazy load exactly once. */
    val loaded: Boolean = false,
    /**
     * False once the cloud says the board does not exist yet (migration 0008
     * unapplied). Offering a board that cannot load reads as a connection
     * fault, so the picker retires itself instead of showing a standing error.
     */
    val available: Boolean = true,
)

/** Which board the BOARD tab shows; the muster roll is deliberately a separate board, not a metric. */
private enum class Board(val label: String) {
    Training("TRAINING"),
    Muster("MUSTER ROLL"),
}

/** Pickable ranking metric; each entry owns its sort key and display formatting. */
private enum class BoardMetric(val label: String) {
    Xp("XP"),
    Level("LEVEL"),
    Streak("STREAK"),
    Titles("TITLES"),
    Strength("STRENGTH"),
    Last7("7-DAY"),
    ;

    /** The raw value this metric ranks by. */
    fun value(row: LeaderboardRow): Long = when (this) {
        Xp -> row.totalXp
        Level -> row.level.toLong()
        Streak -> row.streakDays.toLong()
        Titles -> row.titlesCount.toLong()
        Strength -> row.lifetimeStrength
        Last7 -> row.sessionsLast7d.toLong()
    }

    /** The big per-row display of this metric's value. */
    fun format(row: LeaderboardRow): String = when (this) {
        Xp -> "${row.totalXp} XP"
        Level -> "LV ${row.level}"
        Streak -> plural(row.streakDays, "DAY", "DAYS")
        Titles -> "${row.titlesCount} TITLES"
        Strength -> "STR ${row.lifetimeStrength}"
        Last7 -> "${row.sessionsLast7d} IN 7D"
    }
}

/** Local re-sort for the selected metric; XP breaks ties, then name for stability. No network call. */
private fun sortRows(rows: List<LeaderboardRow>, metric: BoardMetric): List<LeaderboardRow> = rows.sortedWith(
    compareByDescending<LeaderboardRow> { metric.value(it) }
        .thenByDescending { it.totalXp }
        .thenBy { it.displayName },
)

/** Worn title name resolved locally from the id; null when bare so callers omit the segment cleanly. */
private fun wornTitle(currentTitleId: String?): String? =
    currentTitleId?.let { Titles.byId(it)?.name }

class LeaderboardViewModel(
    private val cloudSync: CloudSync,
    private val accountRepo: com.ironvellum.app.data.cloud.AccountRepository,
    private val repo: Repository,
) : ViewModel() {

    private val _ui = MutableStateFlow(
        LeaderboardUi(signedIn = accountRepo.account.value != null, myUserId = accountRepo.account.value?.userId),
    )
    val ui = _ui.asStateFlow()

    // The muster roll keeps its own board and its own flow: it never rides the
    // training leaderboard's state, and the fetch is lazy — only when the
    // player first selects MUSTER ROLL, not on every screen entry.
    private val _muster = MutableStateFlow(MusterBoardUi())
    val muster = _muster.asStateFlow()

    fun loadMuster(force: Boolean = false) {
        if (_muster.value.loading) return
        if (_muster.value.loaded && !force) return
        viewModelScope.launch {
            _muster.value = _muster.value.copy(loading = true, error = null)
            cloudSync.shadowBoard(force)
                .onSuccess { _muster.value = MusterBoardUi(rows = it, loaded = true) }
                .onFailure { error ->
                    val reason = error.reason()
                    _muster.value = _muster.value.copy(
                        loaded = true,
                        error = reason,
                        // Distinguish "the cloud has no such board" from a
                        // transient network failure: only the former retires
                        // the picker, a dropped connection must stay retryable.
                        available = !reason.contains("not live yet"),
                    )
                }
            _muster.value = _muster.value.copy(loading = false)
        }
    }

    // Device-local equipped crest frame; only this lifter's own avatar ever wears it.
    val equippedFrame: StateFlow<String?> = repo.observeEquippedFrame()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        // One probe at construction decides whether MUSTER ROLL is offered at
        // all. Without it the player's first tap is the discovery mechanism,
        // and that tap looks like a board that cannot connect.
        loadMuster()
        viewModelScope.launch {
            accountRepo.account.collect { acct ->
                _ui.value = _ui.value.copy(
                    signedIn = acct != null,
                    myUserId = acct?.userId,
                )
                if (acct != null && _ui.value.rows.isEmpty()) load()
                // Restored offline: identity without the profiles row — retry
                // it on this social read.
                if (acct?.profileLoaded == false) {
                    viewModelScope.launch { accountRepo.refreshProfile() }
                }
            }
        }
    }

    private fun Throwable.reason(): String = message ?: this::class.simpleName ?: "Unknown failure"

    fun load() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true, error = null)
            cloudSync.leaderboard()
                .onSuccess { _ui.value = _ui.value.copy(rows = it) }
                .onFailure { _ui.value = _ui.value.copy(error = it.reason()) }
            _ui.value = _ui.value.copy(loading = false)
        }
    }

}

@Composable
fun LeaderboardScreen(
    onOpenFriend: (userId: String, displayName: String) -> Unit,
    viewModel: LeaderboardViewModel = viewModel(
        factory = viewModelFactory {
            initializer { LeaderboardViewModel(ironvellumCloudSync(), ironvellumAccount(), ironvellumRepository()) }
        },
    ),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val muster by viewModel.muster.collectAsStateWithLifecycle()
    val equippedFrame by viewModel.equippedFrame.collectAsStateWithLifecycle()
    var board by remember { mutableStateOf(Board.Training) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        Text(
            "RANKING",
            style = MaterialTheme.typography.labelLarge,
            fontFamily = ChakraPetch,
            color = IronvellumColors.InkMuted,
            letterSpacing = IronvellumTracking.ScreenTitle,
        )
        // Only a count when there is something counted: "The board awaits" was
        // a second empty-state line above a panel that explains the emptiness.
        if (ui.rows.isNotEmpty()) {
            Text(
                plural(ui.rows.size, "lifter ranked", "lifters ranked"),
                style = MaterialTheme.typography.labelLarge,
                fontFamily = ChakraPetch,
                color = IronvellumColors.SystemGreen,
            )
        }
        Spacer(Modifier.height(12.dp))

        when {
            !ui.configured -> NotConfigured()
            !ui.signedIn && ui.loading -> LoadingPanel()
            !ui.signedIn -> NotSignedIn()
            ui.loading && ui.rows.isEmpty() && board == Board.Training -> LoadingPanel()
            ui.rows.isEmpty() && ui.error == null && board == Board.Training -> EmptyBoard(onRefresh = viewModel::load)
            ui.rows.isEmpty() && ui.error != null && board == Board.Training -> ErrorPanel(onRefresh = viewModel::load)
            else -> {
                // Two separate boards behind one tab: the training board measures
                // what a lifter lifted; the muster roll board measures the vault.
                // The picker only appears once the cloud actually has the board —
                // until then this is exactly the training board it always was.
                if (muster.available) {
                    BoardSelector(
                        selected = board,
                        onPick = {
                            board = it
                            if (it == Board.Muster) viewModel.loadMuster()
                        },
                    )
                    Spacer(Modifier.height(12.dp))
                }
                if (board == Board.Muster && muster.available) {
                    MusterBoard(
                        ui = muster,
                        myUserId = ui.myUserId,
                        equippedFrame = equippedFrame,
                        onRefresh = { viewModel.loadMuster(force = true) },
                    )
                } else {
                    // Pull-to-refresh replaces the old REFRESH button for the normal signed-in board.
                    // The gesture needs content to grab: in the empty/error states there is nothing to
                    // pull (or the list just failed), so those states keep an explicit retry link.
                    val pullState = remember { PullToRefreshState() }
                    PullToRefreshBox(
                        isRefreshing = ui.loading,
                        onRefresh = { viewModel.load() },
                        state = pullState,
                        modifier = Modifier.fillMaxWidth(),
                        indicator = {
                            // House palette: dark vault plate with emerald stroke instead of default Material.
                            PullToRefreshDefaults.Indicator(
                                state = pullState,
                                isRefreshing = ui.loading,
                                modifier = Modifier.align(Alignment.TopCenter),
                                containerColor = IronvellumColors.VaultHigh,
                                color = IronvellumColors.EmeraldBright,
                            )
                        },
                    ) {
                        // PullToRefreshBox's content slot is a Box: emitted straight
                        // into it, every row stacks at the same origin — the podium
                        // vanished under the pinned self-row. A Column restores flow.
                        Column(Modifier.fillMaxWidth()) {
                            Board(ui, viewModel::load, onOpenFriend, equippedFrame)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun NotConfigured() {
    InkPanel(Modifier.fillMaxWidth(), accent = IronvellumColors.DangerRed) {
        Text(
            "RANKING OFFLINE",
            style = MaterialTheme.typography.labelLarge,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = IronvellumColors.DangerRed,
            letterSpacing = IronvellumTracking.InlineLabel,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "No Supabase endpoint is configured on this device, so no board exists here. The rest of Ironvellum keeps working — the clouds are simply absent.",
            style = MaterialTheme.typography.bodySmall,
            color = IronvellumColors.InkMuted,
        )
    }
}

@Composable
private fun NotSignedIn() {
    InkPanel(Modifier.fillMaxWidth(), accent = IronvellumColors.SovereignGold) {
        Text(
            "SIGN IN TO ENTER THE BOARD",
            style = MaterialTheme.typography.labelLarge,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = IronvellumColors.SovereignGold,
            letterSpacing = IronvellumTracking.InlineLabel,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            // Was three lines saying who is ranked, where to go, and what to do
            // there. One instruction is the whole message.
            "Sign in on the ALLIES tab to claim your rank.",
            style = MaterialTheme.typography.bodySmall,
            color = IronvellumColors.InkMuted,
        )
    }
}

@Composable
private fun LoadingPanel() {
    InkPanel(Modifier.fillMaxWidth(), accent = IronvellumColors.Rune) {
        Text(
            "Consulting the board…",
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = ChakraPetch,
            color = IronvellumColors.InkMuted,
        )
    }
}

@Composable
private fun EmptyBoard(onRefresh: () -> Unit) {
    InkPanel(Modifier.fillMaxWidth(), accent = IronvellumColors.Emerald) {
        Text(
            "THE BOARD IS YOURS ALONE",
            style = MaterialTheme.typography.labelLarge,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = IronvellumColors.EmeraldBright,
            letterSpacing = IronvellumTracking.InlineLabel,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "No rivals ranked yet — every top spot starts unopposed. Invite allies by lifter name from the ALLIES tab, then return here to see who trains hardest.",
            style = MaterialTheme.typography.bodySmall,
            color = IronvellumColors.InkMuted,
        )
        Spacer(Modifier.height(10.dp))
        // Kept explicitly: an empty board has nothing to pull down on.
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
            .background(Brush.verticalGradient(listOf(IronvellumColors.VaultHigh, IronvellumColors.Vault)), shape)
            .inkBorder(IronvellumColors.DangerRed, shape, 1.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            message,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = ChakraPetch,
            color = IronvellumColors.DangerRed,
        )
    }
}

/** Load failed with nothing on the board: name the failure and offer one clean retry. */
@Composable
private fun ErrorPanel(onRefresh: () -> Unit) {
    InkPanel(Modifier.fillMaxWidth(), accent = IronvellumColors.DangerRed) {
        Text(
            "THE BOARD FLICKERED",
            style = MaterialTheme.typography.labelLarge,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = IronvellumColors.DangerRed,
            letterSpacing = IronvellumTracking.InlineLabel,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "The Ledger could not summon the rankings. Stand fast and try again.",
            style = MaterialTheme.typography.bodySmall,
            color = IronvellumColors.InkMuted,
        )
        Spacer(Modifier.height(12.dp))
        RefreshLink(onClick = onRefresh, label = "Retry")
    }
}

@Composable
private fun Board(
    ui: LeaderboardUi,
    onRefresh: () -> Unit,
    onOpenFriend: (String, String) -> Unit,
    equippedFrame: String?,
) {
    var metric by remember { mutableStateOf(BoardMetric.Xp) }
    val sorted = remember(ui.rows, metric) { sortRows(ui.rows, metric) }
    val podiumCount = minOf(3, sorted.size)
    val rest = sorted.drop(podiumCount)
    // How many non-podium rows we show before collapsing into the pinned self-row.
    val visibleLimit = 8
    val myIndex = sorted.indexOfFirst { it.userId == ui.myUserId }
    val myRank = if (myIndex >= 0) myIndex + 1 else 0
    val meVisible = myIndex in 0 until (podiumCount + visibleLimit)
    val visible = rest.take(visibleLimit)

    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            "Standings by ${metric.label.lowercase()}",
            style = MaterialTheme.typography.labelMedium,
            fontFamily = ChakraPetch,
            color = IronvellumColors.InkMuted,
            modifier = Modifier.weight(1f),
        )
        // Retry link only survives in the error state; the banner below carries
        // the message so stale rows are never silently served.
        if (ui.error != null) RefreshLink(onClick = onRefresh, label = "Retry")
    }
    if (ui.error != null) {
        InlineErrorBanner("The board flickered — these standings are the last synced ones: ${ui.error}")
        Spacer(Modifier.height(10.dp))
    }
    Spacer(Modifier.height(10.dp))

    MetricChips(selected = metric, onPick = { metric = it })
    Spacer(Modifier.height(12.dp))

    Podium(sorted.take(podiumCount), metric, ui.myUserId, equippedFrame)
    Spacer(Modifier.height(14.dp))

    if (sorted.size == 1) {
        Text(
            "You stand alone on the board. Recruit allies from the ALLIES tab to raise the stakes.",
            style = MaterialTheme.typography.labelMedium,
            fontFamily = ChakraPetch,
            color = IronvellumColors.InkMuted,
            modifier = Modifier.padding(bottom = 10.dp),
        )
    }

    visible.forEachIndexed { index, row ->
        RankRow(
            rank = podiumCount + index + 1,
            row = row,
            metric = metric,
            leaderValue = metric.value(sorted.first()) ,
            isMe = row.userId == ui.myUserId,
            equippedFrame = equippedFrame,
            onOpenFriend = { onOpenFriend(row.userId, row.displayName) },
        )
        Spacer(Modifier.height(10.dp))
    }

    // Always answer "where am I": if the board is long enough that my row was collapsed
    // out of the visible slice, pin my actual rank to the bottom.
    if (!meVisible && myRank > 0) {
        val myRow = sorted[myIndex]
        Spacer(Modifier.height(4.dp))
        Text(
            "— YOUR STANDING —",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            color = IronvellumColors.SovereignGold,
            letterSpacing = IronvellumTracking.InlineLabel,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        RankRow(
            rank = myRank,
            row = myRow,
            metric = metric,
            leaderValue = metric.value(sorted.first()),
            isMe = true,
            equippedFrame = equippedFrame,
            onOpenFriend = { onOpenFriend(myRow.userId, myRow.displayName) },
        )
    }
}

/** Horizontally scrollable chip rail; labels never wrap. */
@Composable
private fun MetricChips(selected: BoardMetric, onPick: (BoardMetric) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BoardMetric.entries.forEach { candidate ->
            val active = candidate == selected
            Text(
                candidate.label,
                maxLines = 1,
                softWrap = false,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = ChakraPetch,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                color = if (active) IronvellumColors.Abyss else IronvellumColors.InkMuted,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .background(if (active) IronvellumColors.SovereignGold else Color(0xFF141A18))
                    .inkBorder(if (active) IronvellumColors.SovereignGold else IronvellumColors.Rune, MaterialTheme.shapes.small, 1.dp)
                    .clickable { onPick(candidate) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

/** Three-column podium; missing lifters render as open ally slots, never blank boxes. */
@Composable
private fun Podium(
    top: List<LeaderboardRow>,
    metric: BoardMetric,
    myUserId: String?,
    equippedFrame: String?,
) {
    // Visual order silver / gold / bronze; plinth heights and emblem sizes step down from the crown.
    // Bottom alignment is what makes this read as a podium: the plinths share a
    // floor and step down from the crown, instead of hanging from a ragged top.
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        // Floors step 2 / 1 / 3 so the leader stands tallest; the avatar steps
        // with it so rank reads from size as well as height.
        PodiumSlot(
            row = top.getOrNull(1),
            rank = 2,
            metric = metric,
            isMe = top.getOrNull(1)?.userId == myUserId,
            equippedFrame = equippedFrame,
            plinthHeight = 26.dp,
            emblemSize = 24.sp,
            avatarSize = 44.dp,
            modifier = Modifier.weight(1f),
        )
        PodiumSlot(
            row = top.getOrNull(0),
            rank = 1,
            metric = metric,
            isMe = top.getOrNull(0)?.userId == myUserId,
            equippedFrame = equippedFrame,
            plinthHeight = 52.dp,
            emblemSize = 34.sp,
            avatarSize = 56.dp,
            modifier = Modifier.weight(1f),
        )
        PodiumSlot(
            row = top.getOrNull(2),
            rank = 3,
            metric = metric,
            isMe = top.getOrNull(2)?.userId == myUserId,
            equippedFrame = equippedFrame,
            plinthHeight = 14.dp,
            emblemSize = 20.sp,
            avatarSize = 40.dp,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PodiumSlot(
    row: LeaderboardRow?,
    rank: Int,
    metric: BoardMetric,
    isMe: Boolean,
    equippedFrame: String?,
    plinthHeight: androidx.compose.ui.unit.Dp,
    emblemSize: androidx.compose.ui.unit.TextUnit,
    avatarSize: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val accent = when (rank) {
        1 -> IronvellumColors.SovereignGold
        2 -> IronvellumColors.EmeraldBright
        else -> Color(0xFFB08A5A) // bronze — no palette token exists for it
    }
    val emblem = when (rank) {
        1 -> "\u2654" // white king — the top seat
        2 -> "\u265B" // queen
        else -> "\u265C" // rook
    }
    val shape = MaterialTheme.shapes.small
    // ONE card per slot, not a cap welded to a plinth: the two-box version left a
    // visible seam and squeezed an IdentityRow so hard that the lifter's NAME was
    // ellipsized away entirely, leaving bare initials. A podium slot is a vertical
    // card, so it is built as one.
    Column(
        modifier
            .clip(shape)
            .background(
                if (row != null) {
                    Brush.verticalGradient(listOf(IronvellumColors.VaultHigh, IronvellumColors.Abyss))
                } else {
                    Brush.verticalGradient(listOf(IronvellumColors.Vault, IronvellumColors.Abyss))
                },
            )
            .inkBorder(if (row != null) accent else IronvellumColors.Rune, shape, 1.dp)
            .padding(horizontal = 6.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (row != null) {
            // Rank emblem crowns the card instead of floating in an empty box below.
            Text(
                emblem,
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.Bold,
                fontSize = emblemSize,
                color = accent,
            )
            Spacer(Modifier.height(6.dp))
            LifterAvatar(
                userId = row.userId,
                displayName = row.displayName,
                size = avatarSize,
                isMe = isMe,
                level = row.level,
                titleId = row.currentTitleId,
                frameId = if (isMe) equippedFrame else null,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (isMe) "YOU" else row.displayName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.Bold,
                color = if (isMe) IronvellumColors.SovereignGold else IronvellumColors.Ink,
                modifier = Modifier.fillMaxWidth(),
            )
            wornTitle(row.currentTitleId)?.let { title ->
                Text(
                    title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = ChakraPetch,
                    color = IronvellumColors.SovereignGold,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                metric.format(row),
                maxLines = 1,
                softWrap = false,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
        } else {
            Text(
                rank.toString(),
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = IronvellumColors.InkMuted,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "ALLY SLOT\nOPEN",
                maxLines = 2,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = ChakraPetch,
                color = IronvellumColors.InkMuted,
            )
        }
        // The stepped floor: rank 1 stands tallest, so the trio reads as a podium.
        Spacer(Modifier.height(plinthHeight))
    }
}

@Composable
private fun RankRow(
    rank: Int,
    row: LeaderboardRow,
    metric: BoardMetric,
    leaderValue: Long,
    isMe: Boolean,
    equippedFrame: String?,
    onOpenFriend: () -> Unit,
) {
    // Podium ranks get distinct emblems: gold for first place, silvered emerald for 2, bronze for 3.
    val accent = when {
        isMe -> IronvellumColors.SovereignGold
        rank == 1 -> IronvellumColors.SovereignGold
        rank == 2 -> IronvellumColors.EmeraldBright
        rank == 3 -> Color(0xFFB08A5A) // bronze — no palette token exists for it
        else -> IronvellumColors.Rune
    }
    val fill = if (isMe) {
        Brush.verticalGradient(listOf(Color(0xFF2A2312), Color(0xFF171307)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFF141A18), Color(0xFF0E1312)))
    }
    // Intensity bar: this row's metric value relative to the current leader on that metric,
    // so relative standing is visible without reading numbers. Zero leader → zero-width fill.
    val intensity = if (leaderValue > 0) (metric.value(row).toFloat() / leaderValue).coerceIn(0f, 1f) else 0f
    val extras = buildList {
        if (metric != BoardMetric.Level) add("LV ${row.level}")
        if (metric != BoardMetric.Xp) add("${row.totalXp} XP")
        if (metric != BoardMetric.Streak) add("${row.streakDays}-day streak")
        if (metric != BoardMetric.Titles) add("${row.titlesCount} titles")
        if (metric != BoardMetric.Strength) add("lifetime strength ${row.lifetimeStrength}")
        if (metric != BoardMetric.Last7) add("${row.sessionsLast7d} in 7 days")
    }.joinToString(" · ")

    InkPanel(
        modifier = Modifier.fillMaxWidth(),
        accent = accent,
        onClick = onOpenFriend,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "#$rank",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.Bold,
                color = if (rank <= 3 || isMe) accent else IronvellumColors.InkMuted,
                modifier = Modifier.padding(top = 2.dp),
            )
            // Shared identity component: avatar crest, name, worn title and level chip.
            IdentityRow(
                displayName = if (isMe) "${row.displayName} — YOU" else row.displayName,
                userId = row.userId,
                wornTitle = wornTitle(row.currentTitleId),
                titleId = row.currentTitleId,
                level = row.level,
                size = IdentitySize.Standard,
                frameId = if (isMe) equippedFrame else null,
                isMe = isMe,
                trailing = {
                    Text(
                        metric.format(row),
                        maxLines = 1,
                        softWrap = false,
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = ChakraPetch,
                        fontWeight = FontWeight.Bold,
                        color = accent,
                    )
                },
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            extras,
            // Two lines, because one clipped "lifetime ..." mid-word and
            // the rest of the stats were repeated below to compensate.
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            color = IronvellumColors.InkMuted,
        )
        Spacer(Modifier.height(8.dp))
        // Intensity bar: this row's metric value relative to the current leader on that metric,
        // so relative standing is visible without reading numbers. Zero leader → zero-width fill.
        InkRail(
            fraction = intensity,
            height = 3.dp,
            track = IronvellumColors.Abyss,
            fill = Brush.horizontalGradient(listOf(accent, IronvellumColors.EmeraldBright)),
            seed = row.userId.hashCode(),
        )
    }
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
            tint = IronvellumColors.Emerald,
            modifier = Modifier.size(14.dp),
        )
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.SemiBold,
            color = IronvellumColors.Emerald,
            letterSpacing = IronvellumTracking.InlineLabel,
        )
    }
}


/** Segmented TRAINING / MUSTER ROLL picker, styled after the metric chips. */
@Composable
private fun BoardSelector(selected: Board, onPick: (Board) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Board.entries.forEach { candidate ->
            val active = candidate == selected
            Text(
                candidate.label,
                maxLines = 1,
                softWrap = false,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = ChakraPetch,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                color = if (active) IronvellumColors.Abyss else IronvellumColors.InkMuted,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .background(if (active) IronvellumColors.SovereignGold else Color(0xFF141A18))
                    .inkBorder(if (active) IronvellumColors.SovereignGold else IronvellumColors.Rune, MaterialTheme.shapes.small, 1.dp)
                    .clickable { onPick(candidate) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

/** The muster roll board: banked essence rankings, deliberately separate from the training board. */
@Composable
private fun MusterBoard(
    ui: MusterBoardUi,
    myUserId: String?,
    equippedFrame: String?,
    onRefresh: () -> Unit,
) {
    when {
        ui.loading && ui.rows.isEmpty() -> LoadingPanel()
        ui.rows.isEmpty() && ui.error != null -> MusterErrorPanel(ui.error, onRefresh)
        ui.rows.isEmpty() -> MusterEmptyPanel(onRefresh)
        else -> {
            val pullState = remember { PullToRefreshState() }
            PullToRefreshBox(
                isRefreshing = ui.loading,
                onRefresh = onRefresh,
                state = pullState,
                modifier = Modifier.fillMaxWidth(),
                indicator = {
                    PullToRefreshDefaults.Indicator(
                        state = pullState,
                        isRefreshing = ui.loading,
                        modifier = Modifier.align(Alignment.TopCenter),
                        containerColor = IronvellumColors.VaultHigh,
                        color = IronvellumColors.EmeraldBright,
                    )
                },
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        "This board ranks the muster roll — essence banked, figures inscribed — and stands apart from the training board by design.",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = ChakraPetch,
                        color = IronvellumColors.InkMuted,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                    // Stale rows must still tell the truth about the last fetch.
                    if (ui.error != null) {
                        InlineErrorBanner("The muster ranks may be stale: ${ui.error}")
                        Spacer(Modifier.height(10.dp))
                    }
                    ui.rows.forEachIndexed { index, row ->
                        MusterRankRow(
                            rank = index + 1,
                            row = row,
                            isMe = row.userId == myUserId,
                            equippedFrame = equippedFrame,
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
        }
    }
}

/** One muster roll row: rank, identity, banked essence as the headline, roll figures beneath. */
@Composable
private fun MusterRankRow(
    rank: Int,
    row: ShadowBoardRow,
    isMe: Boolean,
    equippedFrame: String?,
) {
    val accent = if (isMe) IronvellumColors.SovereignGold else if (rank == 1) IronvellumColors.SovereignGold else IronvellumColors.Rune
    InkPanel(modifier = Modifier.fillMaxWidth(), accent = accent) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "#$rank",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.Bold,
                color = if (rank <= 3 || isMe) accent else IronvellumColors.InkMuted,
                modifier = Modifier.padding(top = 2.dp),
            )
            IdentityRow(
                displayName = if (isMe) "${row.displayName} — YOU" else row.displayName,
                userId = row.userId,
                wornTitle = wornTitle(row.currentTitleId),
                titleId = row.currentTitleId,
                level = row.level,
                size = IdentitySize.Standard,
                // The equipped crest frame is worn by the local lifter alone.
                frameId = if (isMe) equippedFrame else null,
                isMe = isMe,
                trailing = {
                    Text(
                        "${formatEssence(row.essence)} ESS",
                        maxLines = 1,
                        softWrap = false,
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = ChakraPetch,
                        fontWeight = FontWeight.Bold,
                        color = accent,
                    )
                },
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "${row.shadows} figures · ${formatRate(row.ratePerHour)}",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            color = IronvellumColors.InkMuted,
        )
    }
}

/** The muster fetch failed with nothing to show: name the failure, offer one clean retry. */
@Composable
private fun MusterErrorPanel(message: String?, onRefresh: () -> Unit) {
    InkPanel(Modifier.fillMaxWidth(), accent = IronvellumColors.DangerRed) {
        Text(
            "THE ROLL IS VEILED",
            style = MaterialTheme.typography.labelLarge,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = IronvellumColors.DangerRed,
            letterSpacing = IronvellumTracking.InlineLabel,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            message ?: "The muster board could not be summoned. Stand fast and try again.",
            style = MaterialTheme.typography.bodySmall,
            color = IronvellumColors.InkMuted,
        )
        Spacer(Modifier.height(12.dp))
        IronvellumButton(label = "RETRY", onClick = onRefresh)
    }
}

/** The muster board answered, but no lifter has banked essence yet. */
@Composable
private fun MusterEmptyPanel(onRefresh: () -> Unit) {
    InkPanel(Modifier.fillMaxWidth(), accent = IronvellumColors.Emerald) {
        Text(
            "THE ROLL SLEEPS",
            style = MaterialTheme.typography.labelLarge,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = IronvellumColors.EmeraldBright,
            letterSpacing = IronvellumTracking.InlineLabel,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "No lifter has inscribed figures yet — the vault is empty and every top spot here is unclaimed.",
            style = MaterialTheme.typography.bodySmall,
            color = IronvellumColors.InkMuted,
        )
        Spacer(Modifier.height(10.dp))
        RefreshLink(onClick = onRefresh, label = "Check again")
    }
}

/** Thousands-separated essence, e.g. 12,480. */
private fun formatEssence(value: Long): String =
    java.text.NumberFormat.getIntegerInstance().format(value)

/** Extraction rate as e.g. 218.2/H. */
private fun formatRate(ratePerHour: Double): String = "%.1f/H".format(ratePerHour)
