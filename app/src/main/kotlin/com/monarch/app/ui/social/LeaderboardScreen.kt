package com.monarch.app.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.monarch.app.data.cloud.Cloud
import com.monarch.app.data.cloud.CloudSync
import com.monarch.app.data.cloud.FriendSession
import com.monarch.app.data.cloud.LeaderboardRow
import com.monarch.app.ui.components.MonarchButton
import com.monarch.app.ui.components.SectionHeader
import com.monarch.app.ui.components.SystemWindow
import com.monarch.app.ui.components.formatDate
import com.monarch.app.ui.monarchAccount
import com.monarch.app.ui.monarchCloudSync
import com.monarch.app.ui.theme.ChakraPetch
import com.monarch.app.ui.theme.MonarchColors
import com.monarch.app.ui.theme.MonarchTracking
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Full snapshot of the leader board gate: config, session, data, and per-friend drill state. */
data class LeaderboardUi(
    val configured: Boolean = Cloud.configured,
    val signedIn: Boolean = false,
    val myUserId: String? = null,
    val rows: List<LeaderboardRow> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    /** Which friend's panel is expanded, and what was loaded into it. */
    val openFriendUserId: String? = null,
    val friendSessions: Map<String, List<FriendSession>> = emptyMap(),
    val friendSessionErrors: Map<String, String> = emptyMap(),
)

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
        load()
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

    /** Expand/collapse a friend's recent sessions; loads on first open. */
    fun toggleFriend(userId: String) {
        val current = _ui.value.openFriendUserId
        if (current == userId) {
            _ui.value = _ui.value.copy(openFriendUserId = null)
            return
        }
        _ui.value = _ui.value.copy(openFriendUserId = userId)
        if (userId in _ui.value.friendSessions || userId in _ui.value.friendSessionErrors) return
        viewModelScope.launch {
            cloudSync.friendSessions(userId)
                .onSuccess { sessions ->
                    _ui.value = _ui.value.copy(friendSessions = _ui.value.friendSessions + (userId to sessions))
                }
                .onFailure { reason ->
                    _ui.value = _ui.value.copy(friendSessionErrors = _ui.value.friendSessionErrors + (userId to reason.reason()))
                }
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
            else -> Board(ui, viewModel::toggleFriend, viewModel::load, onOpenFriend)
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
        RefreshLink(onClick = onRefresh, label = "Check again")
    }
}

@Composable
private fun Board(
    ui: LeaderboardUi,
    onToggleFriend: (String) -> Unit,
    onRefresh: () -> Unit,
    onOpenFriend: (String, String) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            if (ui.error != null) "The board flickered: ${ui.error}" else "Standings, newest push first",
            style = MaterialTheme.typography.labelMedium,
            fontFamily = ChakraPetch,
            color = if (ui.error != null) MonarchColors.DangerRed else MonarchColors.InkMuted,
            modifier = Modifier.weight(1f),
        )
        RefreshLink(onClick = onRefresh, label = "Refresh")
    }
    Spacer(Modifier.height(10.dp))

    ui.rows.forEachIndexed { index, row ->
        RankRow(
            rank = index + 1,
            row = row,
            isMe = row.userId == ui.myUserId,
            expanded = ui.openFriendUserId == row.userId,
            sessions = ui.friendSessions[row.userId],
            sessionError = ui.friendSessionErrors[row.userId],
            onToggle = { onToggleFriend(row.userId) },
            onOpenFriend = { onOpenFriend(row.userId, row.displayName) },
        )
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun RankRow(
    rank: Int,
    row: LeaderboardRow,
    isMe: Boolean,
    expanded: Boolean,
    sessions: List<FriendSession>?,
    sessionError: String?,
    onToggle: () -> Unit,
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
    val emblem = when (rank) {
        1 -> "\u2654" // white king — the sovereign seat
        2 -> "\u265B" // queen
        3 -> "\u265C" // rook
        else -> rank.toString()
    }
    val fill = if (isMe) {
        Brush.verticalGradient(listOf(Color(0xFF2A2312), Color(0xFF171307)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFF141A18), Color(0xFF0E1312)))
    }

    SystemWindow(
        modifier = Modifier.fillMaxWidth(),
        accent = accent,
        onClick = {
            onToggle()
            onOpenFriend()
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(Brush.linearGradient(listOf(MonarchColors.VaultHigh, MonarchColors.Vault)))
                    .border(1.dp, accent, MaterialTheme.shapes.extraSmall),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    emblem,
                    fontFamily = ChakraPetch,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (rank <= 3) 20.sp else 14.sp,
                    color = if (rank <= 3 || isMe) accent else MonarchColors.InkMuted,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    if (isMe) "${row.displayName} — YOU" else row.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = ChakraPetch,
                    fontWeight = FontWeight.Bold,
                    color = if (isMe) MonarchColors.SovereignGold else MonarchColors.Ink,
                )
                Text(
                    "LV ${row.level} · ${row.totalXp} XP · ${row.streakDays}-day streak · ${row.sessionsLast7d} in 7 days",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = ChakraPetch,
                    color = MonarchColors.InkMuted,
                )
            }
        }
        if (row.titlesCount > 0 || row.lifetimeStrength > 0) {
            Spacer(Modifier.height(6.dp))
            Text(
                "${row.titlesCount} titles · lifetime strength $row.lifetimeStrength",
                style = MaterialTheme.typography.labelSmall,
                color = MonarchColors.SystemGreen,
            )
        }
        if (expanded) {
            Spacer(Modifier.height(12.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(MonarchColors.Abyss)
                    .border(1.dp, MonarchColors.Rune, MaterialTheme.shapes.extraSmall)
                    .padding(10.dp),
            ) {
                when {
                    sessionError != null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Outlined.Lock, contentDescription = null, tint = MonarchColors.InkMuted, modifier = Modifier.size(14.dp))
                        Text(
                            if (sessionError.contains("denied", ignoreCase = true) || sessionError.contains("permission", ignoreCase = true)) {
                                "Not shared with you — this hunter's visibility does not include you."
                            } else {
                                "The System refused: $sessionError"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MonarchColors.InkMuted,
                        )
                    }
                    sessions == null -> Text(
                        "Drawing their recent sessions…",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = ChakraPetch,
                        color = MonarchColors.InkMuted,
                    )
                    sessions.isEmpty() -> Text(
                        "No recent sessions recorded.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MonarchColors.InkMuted,
                    )
                    else -> sessions.forEach { session ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    session.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontFamily = ChakraPetch,
                                    color = MonarchColors.Ink,
                                )
                                Text(
                                    session.completedAtMs?.let { formatDate(it) } ?: "unfinished",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MonarchColors.InkMuted,
                                )
                            }
                            Text(
                                "+${session.xpAwarded} XP · STR ${session.strengthScore} · ${session.sets} sets",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = ChakraPetch,
                                color = MonarchColors.SystemGreen,
                            )
                        }
                    }
                }
            }
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
