package com.ironvellum.app.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import com.ironvellum.app.domain.ExerciseSearch
import com.ironvellum.app.domain.LiftGroup
import com.ironvellum.app.ui.components.InkDivider
import com.ironvellum.app.ui.components.InkPickerSheet
import com.ironvellum.app.ui.components.InkSegmented
import com.ironvellum.app.ui.components.IronvellumDialog
import com.ironvellum.app.ui.components.ListRow
import com.ironvellum.app.ui.components.ListRowHeight
import com.ironvellum.app.ui.components.PickerSectionHeader
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
import com.ironvellum.app.data.cloud.LiftBoardRow
import com.ironvellum.app.domain.Titles
import com.ironvellum.app.domain.Lift
import com.ironvellum.app.domain.LiftBoards
import com.ironvellum.app.domain.fmt
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.ironvellumAccount
import com.ironvellum.app.ui.ironvellumCloudSync
import com.ironvellum.app.ui.ironvellumRepository
import com.ironvellum.app.ui.components.plural
import com.ironvellum.app.ui.theme.Metal
import com.ironvellum.app.ui.theme.inkBorder
import com.ironvellum.app.ui.theme.metalPlate
import com.ironvellum.app.ui.theme.IronvellumColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Full snapshot of the leader board state: config, session, and data state. */
data class LeaderboardUi(
    // Snapshot for ViewModel logic; SocialScreen collects Cloud.config so a
    // backend switch redraws without a restart.
    val configured: Boolean = Cloud.config.value != null,
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
     * False once the cloud says the board does not exist yet (its schema is
     * older than the muster board). Offering a board that cannot load reads
     * as a connection fault, so the picker retires itself instead of showing
     * a standing error.
     */
    val available: Boolean = true,
)

/** Snapshot of the lift boards: every lift's rows in one fetch, filtered locally by lift and window. */
data class LiftsBoardUi(
    val rows: List<LiftBoardRow> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    /** Set once a fetch succeeded, so the first selection loads exactly once. */
    val loaded: Boolean = false,
)

/** Which board the BOARD tab shows; the muster roll is deliberately a separate board, not a metric. */
private enum class Board(val label: String) {
    Training("Training"),
    Lifts("Lifts"),
    Muster("The Veil"),
}

/** Pickable ranking metric; each entry owns its sort key and display formatting. */
private enum class BoardMetric(val label: String) {
    Xp("XP"),
    Level("Level"),
    Streak("Oath"),
    Titles("Deeds"),
    Strength("Strength score"),
    Last7("Last 7 days"),
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
        Xp -> "${grouped(row.totalXp)} XP"
        Level -> "Level ${row.level}"
        Streak -> "${row.streakDays} " + plural(row.streakDays, "day", "days")
        Titles -> "${row.titlesCount} " + plural(row.titlesCount, "deed", "deeds")
        Strength -> "STR ${grouped(row.lifetimeStrength)}"
        Last7 -> "${row.sessionsLast7d} in 7 days"
    }
}

/** Thousands-grouped whole number, e.g. 11,686: long figures read at a glance. */
internal fun grouped(value: Long): String = "%,d".fmt(value)

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

    // Lift boards keep their own state too. One fetch carries every lift, so
    // the chip and the WEEK / ALL TIME switch re-filter locally; the fetch is
    // lazy, on first selection of LIFTS.
    private val _lifts = MutableStateFlow(LiftsBoardUi())
    val lifts = _lifts.asStateFlow()

    fun loadLifts(force: Boolean = false) {
        if (_lifts.value.loading) return
        if (_lifts.value.loaded && !force) return
        viewModelScope.launch {
            _lifts.value = _lifts.value.copy(loading = true, error = null)
            cloudSync.liftBoard(force)
                .onSuccess { _lifts.value = LiftsBoardUi(rows = it, loaded = true) }
                // Not marked loaded: a server below schema 20 (or a dropped
                // connection) must retry on the next visit, not stay refused.
                .onFailure { _lifts.value = _lifts.value.copy(error = it.reason()) }
            _lifts.value = _lifts.value.copy(loading = false)
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
    val lifts by viewModel.lifts.collectAsStateWithLifecycle()
    val equippedFrame by viewModel.equippedFrame.collectAsStateWithLifecycle()
    var board by remember { mutableStateOf(Board.Training) }
    // The ranking metric is picked from a row under the board picker, on Training only.
    var metric by remember { mutableStateOf(BoardMetric.Xp) }
    var pickingMetric by remember { mutableStateOf(false) }

    // SocialScreen owns the margins, the top gap and the signed-out screen, so
    // every tab starts its content at the same spot under the pills.
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // The picker sits above every state, so a lifter with an empty or
        // failed Training board can still reach Lifts. The Veil is offered
        // only once the cloud actually has the muster board.
        InkSegmented(
            options = Board.entries.filter { it != Board.Muster || muster.available }.map { it to it.label },
            selected = board,
            onPick = {
                board = it
                if (it == Board.Muster) viewModel.loadMuster()
                if (it == Board.Lifts) viewModel.loadLifts()
            },
        )
        if (board == Board.Training) {
            Spacer(Modifier.height(12.dp))
            ListRow(
                label = "Ranked by",
                value = metric.label,
                onClickLabel = "Change ranking",
                onClick = { pickingMetric = true },
            )
            InkDivider()
        }
        Spacer(Modifier.height(12.dp))
        when {
            // Three separate boards behind one tab: training measures what a
            // lifter lifted, lifts ranks tiers per lift, the muster roll
            // measures the vault.
            board == Board.Lifts -> LiftsBoard(
                ui = lifts,
                myUserId = ui.myUserId,
                equippedFrame = equippedFrame,
                onRefresh = { viewModel.loadLifts(force = true) },
                onOpenFriend = onOpenFriend,
            )
            board == Board.Muster && muster.available -> MusterBoard(
                ui = muster,
                myUserId = ui.myUserId,
                equippedFrame = equippedFrame,
                onRefresh = { viewModel.loadMuster(force = true) },
            )
            ui.loading && ui.rows.isEmpty() -> LoadingLine()
            ui.rows.isEmpty() && ui.error == null -> EmptyBoard(onRefresh = viewModel::load)
            ui.rows.isEmpty() -> ErrorPanel(onRefresh = viewModel::load)
            else -> {
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
                    // into it, every row stacks at the same origin, so the podium
                    // vanished under the pinned self-row. A Column restores flow.
                    Column(Modifier.fillMaxWidth()) {
                        Board(ui, metric, viewModel::load, onOpenFriend, equippedFrame)
                    }
                }
            }
        }

        Spacer(Modifier.height(28.dp))
    }

    if (pickingMetric) {
        // A bottom sheet: six short rows, no scroll of its own in the text slot.
        IronvellumDialog(
            onDismissRequest = { pickingMetric = false },
            title = { Text("Ranked by") },
            text = {
                Column {
                    BoardMetric.entries.forEach { candidate ->
                        ChoiceRow(
                            label = candidate.label,
                            selected = candidate == metric,
                            onClick = {
                                metric = candidate
                                pickingMetric = false
                            },
                        )
                    }
                }
            },
            confirmButton = { IronvellumButton(label = "Done", onClick = { pickingMetric = false }) },
        )
    }
}

@Composable
private fun LoadingLine() {
    Text(
        "Reading the reckoning…",
        style = MaterialTheme.typography.bodySmall,
        color = IronvellumColors.InkMuted,
    )
}

@Composable
private fun EmptyBoard(onRefresh: () -> Unit) {
    Text(
        "No standings yet. Add allies by true name on the Allies tab.",
        style = MaterialTheme.typography.bodySmall,
        color = IronvellumColors.InkMuted,
    )
    // Kept explicitly: an empty board has nothing to pull down on.
    SocialRefreshLink(onClick = onRefresh, label = "Try again")
}

/** Load failed with nothing on the board: one muted line and one clean retry. */
@Composable
private fun ErrorPanel(onRefresh: () -> Unit) {
    Text(
        "The Ledger could not read the reckoning. Try again in a moment.",
        style = MaterialTheme.typography.bodySmall,
        color = IronvellumColors.InkMuted,
    )
    SocialRefreshLink(onClick = onRefresh, label = "Try again")
}

@Composable
private fun Board(
    ui: LeaderboardUi,
    metric: BoardMetric,
    onRefresh: () -> Unit,
    onOpenFriend: (String, String) -> Unit,
    equippedFrame: String?,
) {
    val sorted = remember(ui.rows, metric) { sortRows(ui.rows, metric) }
    val podiumCount = minOf(3, sorted.size)
    val rest = sorted.drop(podiumCount)
    // How many non-podium rows we show before collapsing into the pinned self-row.
    val visibleLimit = 8
    val myIndex = sorted.indexOfFirst { it.userId == ui.myUserId }
    val myRank = if (myIndex >= 0) myIndex + 1 else 0
    val meVisible = myIndex in 0 until (podiumCount + visibleLimit)
    val visible = rest.take(visibleLimit)

    // Retry link only survives in the error state; the banner below carries
    // the message so stale rows are never silently served.
    if (ui.error != null) {
        SocialErrorBanner("The ink has faded — these standings are from your last sync: ${ui.error}")
        SocialRefreshLink(onClick = onRefresh, label = "Try again")
    }

    Spacer(Modifier.height(4.dp))
    Podium(sorted.take(podiumCount), metric, ui.myUserId, equippedFrame)
    Spacer(Modifier.height(12.dp))

    if (sorted.size == 1) {
        Text(
            "You stand alone in the reckoning. Add allies from the Allies tab to fill it.",
            style = MaterialTheme.typography.bodySmall,
            color = IronvellumColors.InkMuted,
            modifier = Modifier.padding(bottom = 10.dp),
        )
    }

    // The list below the podium starts at rank 4, so no lifter shows twice.
    if (visible.isNotEmpty()) {
        InkPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
            visible.forEachIndexed { index, row ->
                if (index > 0) InkDivider()
                TrainingRow(podiumCount + index + 1, row, metric, row.userId == ui.myUserId, equippedFrame, onOpenFriend)
            }
        }
    }

    // Always answer "where am I": if the board is long enough that my row was collapsed
    // out of the visible slice, pin my actual rank to the bottom.
    if (!meVisible && myRank > 0) {
        val myRow = sorted[myIndex]
        Spacer(Modifier.height(16.dp))
        Text(
            "Your standing",
            style = MaterialTheme.typography.labelMedium,
            color = IronvellumColors.InkMuted,
            modifier = Modifier.padding(start = 2.dp, bottom = 6.dp),
        )
        InkPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
            TrainingRow(myRank, myRow, metric, true, equippedFrame, onOpenFriend)
        }
    }
}

/** One training row: the one-fact subline is the level, or the XP when the board already ranks by level. */
@Composable
private fun TrainingRow(
    rank: Int,
    row: LeaderboardRow,
    metric: BoardMetric,
    isMe: Boolean,
    equippedFrame: String?,
    onOpenFriend: (String, String) -> Unit,
) {
    BoardRow(
        rank = rank,
        userId = row.userId,
        displayName = row.displayName,
        level = row.level,
        titleId = row.currentTitleId,
        subline = if (metric == BoardMetric.Level) "${grouped(row.totalXp)} XP" else "Level ${row.level}",
        figure = metric.format(row),
        isMe = isMe,
        equippedFrame = equippedFrame,
        crestId = row.crestId,
        onClick = { onOpenFriend(row.userId, row.displayName) },
    )
}

/**
 * Three-column podium, flattened: second, first, third on one floor, stepped heights, Vault cards
 * with an iron rim and faint wash. First place alone takes a gold rim, wash and avatar ring (the
 * owner approved it); second and third stay iron, so there are still no bronze or silver medals.
 * Missing lifters render as open ally slots, never blank boxes.
 */
@Composable
private fun Podium(
    top: List<LeaderboardRow>,
    metric: BoardMetric,
    myUserId: String?,
    equippedFrame: String?,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        PodiumSlot(
            row = top.getOrNull(1),
            rank = 2,
            metric = metric,
            isMe = top.getOrNull(1)?.userId == myUserId,
            equippedFrame = equippedFrame,
            minHeight = 150.dp,
            openHeight = 108.dp,
            avatarSize = 44.dp,
            modifier = Modifier.weight(1f),
        )
        PodiumSlot(
            row = top.getOrNull(0),
            rank = 1,
            metric = metric,
            isMe = top.getOrNull(0)?.userId == myUserId,
            equippedFrame = equippedFrame,
            minHeight = 186.dp,
            openHeight = 120.dp,
            avatarSize = 52.dp,
            modifier = Modifier.weight(1f),
        )
        PodiumSlot(
            row = top.getOrNull(2),
            rank = 3,
            metric = metric,
            isMe = top.getOrNull(2)?.userId == myUserId,
            equippedFrame = equippedFrame,
            minHeight = 128.dp,
            openHeight = 92.dp,
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
    minHeight: Dp,
    openHeight: Dp,
    avatarSize: Dp,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.medium
    // The metric figure is pinned to the floor of the card, so the stepped heights read as a
    // podium; the column above leaves room for it. An open slot is an outline only: no fill,
    // a shorter step, and no figure to leave room for, so it reads as a vacancy, not a card.
    val open = row == null
    Box(
        modifier
            .heightIn(min = if (open) openHeight else minHeight)
            .then(
                if (open) {
                    Modifier.inkBorder(IronvellumColors.Rune, shape, 1.dp)
                } else if (rank == 1) {
                    Modifier.metalPlate(Metal.Fabled, shape, rimAlpha = 0.7f)
                } else {
                    Modifier.metalPlate(Metal.Common, shape, rimAlpha = 0.5f)
                },
            ),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, end = 6.dp, top = 12.dp, bottom = if (open) 12.dp else 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                rank.toString(),
                style = if (rank == 1) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                // The one reward-accent mark on the board: first place.
                color = if (rank == 1) IronvellumColors.SovereignGold else IronvellumColors.InkMuted,
            )
            Spacer(Modifier.height(6.dp))
            if (row != null) {
                LifterAvatar(
                    displayName = row.displayName,
                    size = avatarSize,
                    frameId = if (isMe) equippedFrame else row.crestId,
                    // Your own mark is always the gold one; otherwise first place alone wears gold.
                    ring = if (isMe) AvatarRing.Own else if (rank == 1) AvatarRing.First else AvatarRing.Iron,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (isMe) "You" else row.displayName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = IronvellumColors.Ink,
                    modifier = Modifier.fillMaxWidth(),
                )
                wornTitle(row.currentTitleId)?.let { title ->
                    Text(
                        title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = IronvellumColors.InkMuted,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Ally slot open",
                    maxLines = 2,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = IronvellumColors.InkMuted.copy(alpha = 0.7f),
                )
            }
        }
        if (row != null) {
            Text(
                metric.format(row),
                maxLines = 1,
                softWrap = false,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (rank == 1) FontWeight.Bold else FontWeight.SemiBold,
                color = IronvellumColors.Ink,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
            )
        }
    }
}

/**
 * The one board row: rank, avatar, name over a one-fact subline, the metric trailing. A row in
 * the signed-in lifter's own place carries a thin primary bar on its left and a faint tint, and
 * says "You" in the subline. The rank is InkMuted, except a first place, which takes the reward accent.
 */
@Composable
private fun BoardRow(
    rank: Int,
    userId: String,
    displayName: String,
    level: Int,
    titleId: String?,
    subline: String,
    figure: String,
    isMe: Boolean,
    equippedFrame: String?,
    crestId: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(
                if (isMe) {
                    Modifier
                        .background(IronvellumColors.Ink.copy(alpha = 0.04f))
                        .drawBehind { drawRect(IronvellumColors.Emerald, size = Size(3.dp.toPx(), size.height)) }
                } else {
                    Modifier
                },
            )
            .then(if (onClick != null) Modifier.clickable(onClickLabel = "Open folio", role = Role.Button, onClick = onClick) else Modifier)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            rank.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = if (rank == 1) IronvellumColors.SovereignGold else IronvellumColors.InkMuted,
            modifier = Modifier.width(22.dp),
        )
        LifterAvatar(
            displayName = displayName,
            size = 36.dp,
            // The equipped crest frame is worn by the local lifter alone.
            frameId = if (isMe) equippedFrame else crestId,
            ring = if (isMe) AvatarRing.Own else AvatarRing.Iron,
        )
        Column(Modifier.weight(1f)) {
            Text(
                displayName.ifBlank { "Ironbound" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isMe) FontWeight.SemiBold else FontWeight.Medium,
                color = IronvellumColors.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (isMe) "You · $subline" else subline,
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            figure,
            maxLines = 2,
            textAlign = TextAlign.End,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = IronvellumColors.Ink,
            modifier = Modifier.widthIn(max = 140.dp),
        )
    }
}

/** One choice in a picker: label over an optional subline, a muted [trailing] note and a check on the open one. */
@Composable
private fun ChoiceRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    subline: String? = null,
    trailing: String? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = ListRowHeight)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = IronvellumColors.Ink,
            )
            if (subline != null) {
                Text(
                    subline,
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Text(trailing, style = MaterialTheme.typography.labelMedium, color = IronvellumColors.InkMuted)
        }
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = "Selected", tint = IronvellumColors.Emerald, modifier = Modifier.size(20.dp))
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
        ui.loading && ui.rows.isEmpty() -> LoadingLine()
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
                        "Ranks Veil progress — echoes inscribed — separate from the training reckoning.",
                        style = MaterialTheme.typography.bodySmall,
                        color = IronvellumColors.InkMuted,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                    // Stale rows must still tell the truth about the last fetch.
                    if (ui.error != null) {
                        SocialErrorBanner("The ink has faded — these standings are from your last sync: ${ui.error}")
                        Spacer(Modifier.height(10.dp))
                    }
                    InkPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
                        ui.rows.forEachIndexed { index, row ->
                            if (index > 0) InkDivider()
                            BoardRow(
                                rank = index + 1,
                                userId = row.userId,
                                displayName = row.displayName,
                                level = row.level,
                                titleId = row.currentTitleId,
                                subline = "${row.shadows} echoes · ${formatRate(row.ratePerHour)}",
                                figure = "${formatEssence(row.essence)} essence",
                                isMe = row.userId == myUserId,
                                equippedFrame = equippedFrame,
                                crestId = row.crestId,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The muster fetch failed with nothing to show: one muted line and one clean retry. */
@Composable
private fun MusterErrorPanel(message: String?, onRefresh: () -> Unit) {
    Text(
        message ?: "The Ledger could not read the Veil's reckoning. Try again in a moment.",
        style = MaterialTheme.typography.bodySmall,
        color = IronvellumColors.InkMuted,
    )
    SocialRefreshLink(onClick = onRefresh, label = "Try again")
}

/** The muster board answered, but no lifter has banked essence yet. */
@Composable
private fun MusterEmptyPanel(onRefresh: () -> Unit) {
    Text(
        "No Ironbound has inscribed echoes yet. The Veil is empty and every top standing here is unclaimed.",
        style = MaterialTheme.typography.bodySmall,
        color = IronvellumColors.InkMuted,
    )
    SocialRefreshLink(onClick = onRefresh, label = "Check again")
}

/** Thousands-separated essence, e.g. 12,480. */
private fun formatEssence(value: Long): String =
    "%,d".fmt(value)

/** Extraction rate as e.g. 218.2/h. */
private fun formatRate(ratePerHour: Double): String = "%.1f/h".fmt(ratePerHour)

/** Which slice of a lift board ranks: the best set of the last 7 days, or the best ever. */
private enum class LiftWindow(val label: String) {
    Week("Week"),
    AllTime("All time"),
}

/** One lifter's place on one lift board; [step] is the tier step that ranked them, never a ratio. */
private data class LiftStanding(val rank: Int, val row: LiftBoardRow, val step: Int)

/**
 * Ranks one lift for a window, best tier first. Ties share a rank (1, 1, 3) so
 * two lifters on the same tier are never ordered by an arbitrary name sort
 * that reads as one beating the other. WEEK drops lifters with no qualifying
 * set in the last 7 days rather than ranking their stale tier.
 */
private fun liftStandings(rows: List<LiftBoardRow>, lift: Lift, window: LiftWindow): List<LiftStanding> {
    val ranked = rows
        .filter { it.lift == lift }
        .mapNotNull { row ->
            val step = if (window == LiftWindow.Week) row.recentStep else row.step
            step?.let { row to it }
        }
        .sortedWith(
            compareByDescending<Pair<LiftBoardRow, Int>> { it.second }
                .thenBy { it.first.displayName.lowercase() },
        )
    var rank = 0
    return ranked.mapIndexed { index, (row, step) ->
        if (index == 0 || step != ranked[index - 1].second) rank = index + 1
        LiftStanding(rank, row, step)
    }
}

/** The lift boards: a Row that picks the lift, Week / All time, ranked by tier name only. */
@Composable
private fun LiftsBoard(
    ui: LiftsBoardUi,
    myUserId: String?,
    equippedFrame: String?,
    onRefresh: () -> Unit,
    onOpenFriend: (String, String) -> Unit,
) {
    var lift by remember { mutableStateOf(Lift.entries.first()) }
    var window by remember { mutableStateOf(LiftWindow.AllTime) }
    val standings = remember(ui.rows, lift, window) { liftStandings(ui.rows, lift, window) }

    when {
        ui.loading && ui.rows.isEmpty() -> LoadingLine()
        // Nothing to filter yet (e.g. the server is below schema 20): say why
        // and offer the retry, without controls that would act on no data.
        ui.rows.isEmpty() && ui.error != null -> {
            SocialErrorBanner(ui.error)
            SocialRefreshLink(onClick = onRefresh, label = "Try again")
        }
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
                        "Rungs only — bodyweight stays on each phone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = IronvellumColors.InkMuted,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                    // Stale rows must still tell the truth about the last fetch.
                    if (ui.error != null) {
                        SocialErrorBanner("The ink has faded — these standings are from your last sync: ${ui.error}")
                        Spacer(Modifier.height(10.dp))
                    }
                    LiftPicker(
                        selected = lift,
                        rankedCount = { liftStandings(ui.rows, it, window).size },
                        onPick = { lift = it },
                    )
                    InkDivider()
                    Spacer(Modifier.height(12.dp))
                    InkSegmented(
                        options = LiftWindow.entries.map { it to it.label },
                        selected = window,
                        onPick = { window = it },
                    )
                    Spacer(Modifier.height(12.dp))
                    if (standings.isEmpty()) {
                        LiftEmptyPanel(onRefresh)
                    } else {
                        InkPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
                            standings.forEachIndexed { index, standing ->
                                if (index > 0) InkDivider()
                                val row = standing.row
                                BoardRow(
                                    rank = standing.rank,
                                    userId = row.userId,
                                    displayName = row.displayName,
                                    level = row.level,
                                    titleId = row.currentTitleId,
                                    subline = LiftBoards.stepDetail(row.lift, standing.step) ?: "Level ${row.level}",
                                    figure = LiftBoards.stepLabel(row.lift, standing.step),
                                    isMe = row.userId == myUserId,
                                    equippedFrame = equippedFrame,
                                    crestId = row.crestId,
                                    onClick = { onOpenFriend(row.userId, row.displayName) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * One Row naming the current board; tapping it opens the shared picker
 * sheet, grouped Pull / Push / Static / Legs / Barbell and searchable by
 * board or rung name ("fl" finds the front lever board). Each row says how
 * many lifters it ranks, so an empty board is visible before it is opened.
 */
@Composable
private fun LiftPicker(
    selected: Lift,
    rankedCount: (Lift) -> Int,
    onPick: (Lift) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    ListRow(
        label = selected.label,
        subline = selected.group.label,
        onClickLabel = "Choose a reckoning",
        onClick = { open = true },
    )
    if (!open) return

    var query by remember { mutableStateOf("") }
    val matches = Lift.entries.mapNotNull { lift ->
        val names = listOf(lift.label) + LiftBoards.rungs(lift).map { it.exercise }
        names.mapNotNull { ExerciseSearch.rank(it, query) }.minOrNull()?.let { lift to it }
    }
    // Board order within a group is progression order; a query re-sorts by how well it matched.
    val shown = if (query.isBlank()) matches.map { it.first } else matches.sortedBy { it.second }.map { it.first }
    InkPickerSheet(
        title = "Choose a reckoning",
        onDismiss = { open = false },
        query = query,
        onQueryChange = { query = it },
        searchLabel = "Search reckonings",
        count = shown.size,
    ) {
        LiftGroup.entries.forEach { group ->
            val inGroup = shown.filter { it.group == group }
            if (inGroup.isEmpty()) return@forEach
            item(key = "group-${group.name}") { PickerSectionHeader(group.label.uppercase()) }
            inGroup.forEach { lift ->
                item(key = lift.wire) {
                    BoardPickerRow(lift, lift == selected, rankedCount(lift)) {
                        open = false
                        onPick(lift)
                    }
                }
            }
        }
    }
}

@Composable
private fun BoardPickerRow(lift: Lift, isSelected: Boolean, ranked: Int, onClick: () -> Unit) {
    val rungs = LiftBoards.rungs(lift)
    val detail = if (rungs.isEmpty()) {
        "Bodyweight rungs"
    } else {
        "${rungs.size} rungs · ${rungs.first().exercise} to ${rungs.last().exercise}"
    }
    ChoiceRow(
        label = lift.label,
        selected = isSelected,
        onClick = onClick,
        subline = detail,
        trailing = if (ranked == 0) "none yet" else "$ranked ranked",
    )
}

/** The board answered, but nobody is ranked on it in this window. */
@Composable
private fun LiftEmptyPanel(onRefresh: () -> Unit) {
    Text(
        "No allies stand here yet. Log one of its exercises to claim a standing.",
        style = MaterialTheme.typography.bodySmall,
        color = IronvellumColors.InkMuted,
    )
    SocialRefreshLink(onClick = onRefresh, label = "Check again")
}
