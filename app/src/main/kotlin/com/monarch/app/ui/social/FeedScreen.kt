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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.monarch.app.data.cloud.FeedEntry
import com.monarch.app.ui.components.SystemWindow
import com.monarch.app.ui.components.formatDate
import com.monarch.app.ui.monarchAccount
import com.monarch.app.ui.monarchCloudSync
import com.monarch.app.ui.theme.ChakraPetch
import com.monarch.app.ui.theme.MonarchColors
import com.monarch.app.ui.theme.MonarchTracking
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
)

class FeedViewModel(
    private val cloudSync: CloudSync,
    accountRepo: com.monarch.app.data.cloud.AccountRepository,
    private val pageSize: Int = 50,
) : ViewModel() {

    private val _ui = MutableStateFlow(
        FeedUi(signedIn = accountRepo.account.value != null, myUserId = accountRepo.account.value?.userId),
    )
    val ui = _ui.asStateFlow()

    /** Oldest completedAtMs seen — the cursor for the next page. */
    private var oldestMs: Long? = null

    private fun Throwable.reason(): String = message ?: this::class.simpleName ?: "Unknown failure"

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true, error = null, exhausted = false)
            cloudSync.feed(limit = pageSize)
                .onSuccess { page ->
                    oldestMs = page.mapNotNull { it.completedAtMs }.minOrNull()
                    _ui.value = _ui.value.copy(entries = page)
                }
                .onFailure { _ui.value = _ui.value.copy(error = it.reason()) }
            _ui.value = _ui.value.copy(loading = false)
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
        if (current.loading || current.loadingMore || current.exhausted || current.error != null) return
        val cursor = oldestMs ?: return
        _ui.value = current.copy(loadingMore = true)
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
                .onFailure { _ui.value = _ui.value.copy(error = it.reason()) }
            _ui.value = _ui.value.copy(loadingMore = false)
        }
    }
}

@Composable
fun FeedScreen(
    onOpenHunter: (userId: String, displayName: String) -> Unit,
    viewModel: FeedViewModel = viewModel(
        factory = viewModelFactory {
            initializer { FeedViewModel(monarchCloudSync(), monarchAccount()) }
        },
    ),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

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
        val subtitle = when {
            !ui.configured -> "No gate connection"
            !ui.signedIn -> "A sigil is required"
            ui.entries.isEmpty() -> "The world holds its breath"
            else -> "${ui.entries.size} hunts witnessed"
        }
        Text(
            subtitle,
            style = MaterialTheme.typography.labelLarge,
            fontFamily = ChakraPetch,
            color = MonarchColors.SystemGreen,
        )
        Spacer(Modifier.height(12.dp))

        val err = ui.error
        when {
            !ui.configured -> NotConfigured()
            !ui.signedIn -> NotSignedIn()
            ui.loading && ui.entries.isEmpty() -> LoadingPanel()
            ui.entries.isEmpty() && err != null -> ErrorPanel(err, onRetry = viewModel::load)
            ui.entries.isEmpty() -> EmptyFeed(onRefresh = viewModel::load)
            else -> Feed(ui, viewModel::load, viewModel::loadMore, onOpenHunter)
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
            "The public feed streams every hunter's shared hunts, and it only opens for awakened accounts. Open Settings → Gateway and sign in to join the watch.",
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
        Text(
            "No hunts are public yet. Make yours visible from the account screen's visibility setting, then be the first name on the board.",
            style = MaterialTheme.typography.bodySmall,
            color = MonarchColors.InkMuted,
        )
        Spacer(Modifier.height(10.dp))
        RefreshLink(onClick = onRefresh, label = "Check again")
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

@Composable
private fun Feed(
    ui: FeedUi,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenHunter: (String, String) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            if (ui.error != null) "Older hunts still shown — the newest fetch failed: ${ui.error}"
            else "Every hunter's public hunts, newest first",
            style = MaterialTheme.typography.labelMedium,
            fontFamily = ChakraPetch,
            color = if (ui.error != null) MonarchColors.DangerRed else MonarchColors.InkMuted,
            modifier = Modifier.weight(1f),
        )
        RefreshLink(onClick = onRefresh, label = "Refresh")
    }
    Spacer(Modifier.height(10.dp))

    val listState = rememberLazyListState()
    // One trigger at the tail: near the last item, ask for the next page; the
    // VM's guards make repeated triggers harmless.
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            last >= listState.layoutInfo.totalItemsCount - 3 && listState.layoutInfo.totalItemsCount > 0
        }
    }
    LaunchedEffect(nearEnd) {
        if (nearEnd) onLoadMore()
    }

    LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(ui.entries, key = { it.sessionId }) { entry ->
            FeedCard(entry = entry, isMe = entry.userId == ui.myUserId, onOpenHunter = onOpenHunter)
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

@Composable
private fun FeedCard(
    entry: FeedEntry,
    isMe: Boolean,
    onOpenHunter: (String, String) -> Unit,
) {
    val accent = if (isMe) MonarchColors.SovereignGold else MonarchColors.Emerald
    SystemWindow(Modifier.fillMaxWidth(), accent = accent) {
        Column {
            // Hunter strip: tap anywhere here to open the hunter's profile.
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.extraSmall)
                    .clickable { onOpenHunter(entry.userId, entry.displayName) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(Brush.linearGradient(listOf(MonarchColors.VaultHigh, MonarchColors.Vault)))
                        .border(1.dp, accent, MaterialTheme.shapes.extraSmall),
                    contentAlignment = Alignment.Center,
                ) {
                    // Two-letter seal from the hunter's name — consistent and never a wrong pictogram.
                    Text(
                        entry.displayName.take(2).uppercase(),
                        fontFamily = ChakraPetch,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = accent,
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        if (isMe) "${entry.displayName} — YOU" else entry.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontFamily = ChakraPetch,
                        fontWeight = FontWeight.Bold,
                        color = if (isMe) MonarchColors.SovereignGold else MonarchColors.Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // Level badge.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(MonarchColors.Abyss)
                        .border(1.dp, MonarchColors.Rune, MaterialTheme.shapes.extraSmall)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        "LV ${entry.level}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = ChakraPetch,
                        fontWeight = FontWeight.SemiBold,
                        color = MonarchColors.SystemGreen,
                    )
                }
            }

            entry.completedAtMs?.let { ms ->
                Spacer(Modifier.height(6.dp))
                Text(
                    relativeTime(ms),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = ChakraPetch,
                    color = MonarchColors.InkMuted,
                )
            }

            // User-authored title, falling back to the drill label when untitled.
            if (entry.title.isNotBlank() || entry.label.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    entry.title.ifBlank { entry.label },
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = ChakraPetch,
                    fontWeight = FontWeight.Bold,
                    color = MonarchColors.Ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
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

            Spacer(Modifier.height(10.dp))
            StatStrip(entry)
        }
    }
}

/** Icon + short-value stat strip: sets, reps, XP, strength. */
@Composable
private fun StatStrip(entry: FeedEntry) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraSmall)
            .background(MonarchColors.Abyss)
            .border(1.dp, MonarchColors.Rune, MaterialTheme.shapes.extraSmall)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Stat(Icons.Outlined.FitnessCenter, "${entry.setsDone}", "SETS")
        Stat(Icons.Outlined.Repeat, "${entry.repsDone}", "REPS")
        Stat(Icons.Outlined.Bolt, "+${entry.xpAwarded}", "XP", tint = MonarchColors.Emerald)
        Stat(Icons.Outlined.WorkspacePremium, "${entry.strengthScore}", "STR", tint = MonarchColors.SovereignGold)
    }
}

@Composable
private fun Stat(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, label: String, tint: Color = MonarchColors.InkMuted) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(14.dp))
        Column {
            Text(
                value,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.SemiBold,
                color = MonarchColors.Ink,
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
