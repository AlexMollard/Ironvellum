package com.monarch.app.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CutCornerShape
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
import com.monarch.app.data.cloud.CloudSync
import com.monarch.app.data.cloud.Cloud
import com.monarch.app.data.cloud.LeaderboardRow
import com.monarch.app.domain.Titles
import com.monarch.app.ui.components.MonarchButton
import com.monarch.app.ui.components.SystemWindow
import com.monarch.app.ui.monarchAccount
import com.monarch.app.ui.monarchCloudSync
import com.monarch.app.ui.theme.ChakraPetch
import com.monarch.app.ui.theme.MonarchColors
import com.monarch.app.ui.theme.MonarchTracking
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Full snapshot of the leader board gate: config, session, and data state. */
data class LeaderboardUi(
    val configured: Boolean = Cloud.configured,
    val signedIn: Boolean = false,
    val myUserId: String? = null,
    val rows: List<LeaderboardRow> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

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
        Streak -> "${row.streakDays} DAY"
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
    private val accountRepo: com.monarch.app.data.cloud.AccountRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(
        LeaderboardUi(signedIn = accountRepo.account.value != null, myUserId = accountRepo.account.value?.userId),
    )
    val ui = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            accountRepo.account.collect { acct ->
                _ui.value = _ui.value.copy(
                    signedIn = acct != null,
                    myUserId = acct?.userId,
                )
                if (acct != null && _ui.value.rows.isEmpty()) load()
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
            initializer { LeaderboardViewModel(monarchCloudSync(), monarchAccount()) }
        },
    ),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

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
            color = MonarchColors.InkMuted,
            letterSpacing = MonarchTracking.ScreenTitle,
        )
        Text(
            if (ui.rows.isEmpty()) "The board awaits" else "${ui.rows.size} hunters ranked",
            style = MaterialTheme.typography.labelLarge,
            fontFamily = ChakraPetch,
            color = MonarchColors.SystemGreen,
        )
        Spacer(Modifier.height(12.dp))

        when {
            !ui.configured -> NotConfigured()
            !ui.signedIn && ui.loading -> LoadingPanel()
            !ui.signedIn -> NotSignedIn()
            ui.loading && ui.rows.isEmpty() -> LoadingPanel()
            ui.rows.isEmpty() && ui.error == null -> EmptyBoard(onRefresh = viewModel::load)
            ui.rows.isEmpty() && ui.error != null -> ErrorPanel(onRefresh = viewModel::load)
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
                            containerColor = MonarchColors.VaultHigh,
                            color = MonarchColors.EmeraldBright,
                        )
                    },
                ) {
                    // PullToRefreshBox's content slot is a Box: emitted straight
                    // into it, every row stacks at the same origin — the podium
                    // vanished under the pinned self-row. A Column restores flow.
                    Column(Modifier.fillMaxWidth()) {
                        Board(ui, viewModel::load, onOpenFriend)
                    }
                }
            }
        }

        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun NotConfigured() {
    SystemWindow(Modifier.fillMaxWidth(), accent = MonarchColors.DangerRed) {
        Text(
            "RANKING OFFLINE",
            style = MaterialTheme.typography.labelLarge,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = MonarchColors.DangerRed,
            letterSpacing = MonarchTracking.InlineLabel,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "No Supabase endpoint is configured on this device, so no board exists here. The rest of Monarch keeps working — the clouds are simply absent.",
            style = MaterialTheme.typography.bodySmall,
            color = MonarchColors.InkMuted,
        )
    }
}

@Composable
private fun NotSignedIn() {
    SystemWindow(Modifier.fillMaxWidth(), accent = MonarchColors.SovereignGold) {
        Text(
            "SIGN IN TO ENTER THE BOARD",
            style = MaterialTheme.typography.labelLarge,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = MonarchColors.SovereignGold,
            letterSpacing = MonarchTracking.InlineLabel,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Only hunters with a cloud sigil are ranked. Open Settings → Gateway and sign in or awaken an account to claim your rank.",
            style = MaterialTheme.typography.bodySmall,
            color = MonarchColors.InkMuted,
        )
    }
}

@Composable
private fun LoadingPanel() {
    SystemWindow(Modifier.fillMaxWidth(), accent = MonarchColors.Rune) {
        Text(
            "Consulting the board…",
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = ChakraPetch,
            color = MonarchColors.InkMuted,
        )
    }
}

@Composable
private fun EmptyBoard(onRefresh: () -> Unit) {
    SystemWindow(Modifier.fillMaxWidth(), accent = MonarchColors.Emerald) {
        Text(
            "THE BOARD IS YOURS ALONE",
            style = MaterialTheme.typography.labelLarge,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = MonarchColors.EmeraldBright,
            letterSpacing = MonarchTracking.InlineLabel,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "No rivals ranked yet — every throne starts unopposed. Invite allies by hunter name from Settings → Gateway, then return here to see who trains hardest.",
            style = MaterialTheme.typography.bodySmall,
            color = MonarchColors.InkMuted,
        )
        Spacer(Modifier.height(10.dp))
        // Kept explicitly: an empty board has nothing to pull down on.
        RefreshLink(onClick = onRefresh, label = "Check again")
    }
}

/** Load failed with nothing on the board: name the failure and offer one clean retry. */
@Composable
private fun ErrorPanel(onRefresh: () -> Unit) {
    SystemWindow(Modifier.fillMaxWidth(), accent = MonarchColors.DangerRed) {
        Text(
            "THE BOARD FLICKERED",
            style = MaterialTheme.typography.labelLarge,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = MonarchColors.DangerRed,
            letterSpacing = MonarchTracking.InlineLabel,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "The System could not summon the rankings. Stand fast and try again.",
            style = MaterialTheme.typography.bodySmall,
            color = MonarchColors.InkMuted,
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
            if (ui.error != null) "The board flickered: ${ui.error}" else "Standings by ${metric.label.lowercase()}",
            style = MaterialTheme.typography.labelMedium,
            fontFamily = ChakraPetch,
            color = if (ui.error != null) MonarchColors.DangerRed else MonarchColors.InkMuted,
            modifier = Modifier.weight(1f),
        )
        // Retry link only survives in the error state; pull-to-refresh covers the healthy path.
        if (ui.error != null) RefreshLink(onClick = onRefresh, label = "Retry")
    }
    Spacer(Modifier.height(10.dp))

    MetricChips(selected = metric, onPick = { metric = it })
    Spacer(Modifier.height(12.dp))

    Podium(sorted.take(podiumCount), metric, ui.myUserId)
    Spacer(Modifier.height(14.dp))

    if (sorted.size == 1) {
        Text(
            "You stand alone on the board. Recruit allies from the GUILD tab to raise the stakes.",
            style = MaterialTheme.typography.labelMedium,
            fontFamily = ChakraPetch,
            color = MonarchColors.InkMuted,
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
            color = MonarchColors.SovereignGold,
            letterSpacing = MonarchTracking.InlineLabel,
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
                color = if (active) MonarchColors.Abyss else MonarchColors.InkMuted,
                modifier = Modifier
                    .clip(CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp))
                    .background(if (active) MonarchColors.SovereignGold else Color(0xFF141A18))
                    .border(
                        1.dp,
                        if (active) MonarchColors.SovereignGold else MonarchColors.Rune,
                        CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                    )
                    .clickable { onPick(candidate) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

/** Three-column podium; missing hunters render as open ally slots, never blank boxes. */
@Composable
private fun Podium(
    top: List<LeaderboardRow>,
    metric: BoardMetric,
    myUserId: String?,
) {
    // Visual order silver / gold / bronze; plinth heights and emblem sizes step down from the crown.
    // Bottom alignment is what makes this read as a podium: the plinths share a
    // floor and step down from the crown, instead of hanging from a ragged top.
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        // Floors step 2 / 1 / 3 so the sovereign stands tallest; the avatar steps
        // with it so rank reads from size as well as height.
        PodiumSlot(
            row = top.getOrNull(1),
            rank = 2,
            metric = metric,
            isMe = top.getOrNull(1)?.userId == myUserId,
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
    plinthHeight: androidx.compose.ui.unit.Dp,
    emblemSize: androidx.compose.ui.unit.TextUnit,
    avatarSize: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val accent = when (rank) {
        1 -> MonarchColors.SovereignGold
        2 -> MonarchColors.EmeraldBright
        else -> Color(0xFFB08A5A) // bronze — no palette token exists for it
    }
    val emblem = when (rank) {
        1 -> "\u2654" // white king — the sovereign seat
        2 -> "\u265B" // queen
        else -> "\u265C" // rook
    }
    val shape = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp)
    // ONE card per slot, not a cap welded to a plinth: the two-box version left a
    // visible seam and squeezed an IdentityRow so hard that the hunter's NAME was
    // ellipsized away entirely, leaving bare initials. A podium slot is a vertical
    // card, so it is built as one.
    Column(
        modifier
            .clip(shape)
            .background(
                if (row != null) {
                    Brush.verticalGradient(listOf(MonarchColors.VaultHigh, MonarchColors.Abyss))
                } else {
                    Brush.verticalGradient(listOf(MonarchColors.Vault, MonarchColors.Abyss))
                },
            )
            .border(1.dp, if (row != null) accent else MonarchColors.Rune, shape)
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
            HunterAvatar(
                userId = row.userId,
                displayName = row.displayName,
                size = avatarSize,
                isMe = isMe,
                level = row.level,
                titleId = row.currentTitleId,
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
                color = if (isMe) MonarchColors.SovereignGold else MonarchColors.Ink,
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
                    color = MonarchColors.SovereignGold,
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
                color = MonarchColors.Rune,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "ALLY SLOT\nOPEN",
                maxLines = 2,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = ChakraPetch,
                color = MonarchColors.InkMuted,
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
    onOpenFriend: () -> Unit,
) {
    // Podium ranks get distinct emblems: gold for the sovereign, silvered emerald for 2, bronze for 3.
    val accent = when {
        isMe -> MonarchColors.SovereignGold
        rank == 1 -> MonarchColors.SovereignGold
        rank == 2 -> MonarchColors.EmeraldBright
        rank == 3 -> Color(0xFFB08A5A) // bronze — no palette token exists for it
        else -> MonarchColors.Rune
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

    SystemWindow(
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
                color = if (rank <= 3 || isMe) accent else MonarchColors.InkMuted,
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
            color = MonarchColors.InkMuted,
        )
        Spacer(Modifier.height(8.dp))
        // Intensity bar: this row's metric value relative to the current leader on that metric,
        // so relative standing is visible without reading numbers. Zero leader → zero-width fill.
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(MonarchColors.Abyss),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(intensity)
                    .height(3.dp)
                    .background(Brush.horizontalGradient(listOf(accent, MonarchColors.EmeraldBright))),
            )
        }
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
