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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.monarch.app.data.Repository
import com.monarch.app.data.cloud.AccountRepository
import com.monarch.app.data.cloud.CloudSync
import com.monarch.app.data.cloud.FriendRow
import com.monarch.app.data.cloud.FriendSession
import com.monarch.app.domain.Titles
import com.monarch.app.ui.components.SectionHeader
import com.monarch.app.ui.components.SystemWindow
import com.monarch.app.ui.components.TrendChart
import com.monarch.app.ui.components.formatDate
import com.monarch.app.ui.monarchAccount
import com.monarch.app.ui.monarchCloudSync
import com.monarch.app.ui.monarchRepository
import com.monarch.app.ui.theme.ChakraPetch
import com.monarch.app.ui.theme.MonarchColors
import com.monarch.app.ui.theme.MonarchTracking
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal data class HunterUi(
    val loading: Boolean = true,
    val sessions: List<FriendSession> = emptyList(),
    val error: String? = null,
    val myUserId: String? = null,
    val allyState: AllyState = AllyState.None,
    val allyBusy: Boolean = false,
    /** Worn title resolved locally from the leaderboard cache; null when bare or unknown. */
    val wornTitle: String? = null,
    /** The raw title id behind [wornTitle]; feeds the avatar crest's rarity palette. */
    val wornTitleId: String? = null,
)

internal class HunterViewModel(
    private val cloud: CloudSync,
    private val accountRepo: AccountRepository,
    private val repo: Repository,
) : ViewModel() {

    private val _ui = MutableStateFlow(HunterUi())
    val ui: StateFlow<HunterUi> = _ui.asStateFlow()

    // Device-local equipped crest frame; only worn when viewing one's own profile.
    val equippedFrame: StateFlow<String?> = repo.observeEquippedFrame()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun load(userId: String) {
        if (userId.isBlank()) {
            _ui.value = HunterUi(loading = false, error = "No hunter selected.")
            return
        }
        _ui.value = HunterUi(myUserId = accountRepo.account.value?.userId)
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true, error = null)
            cloud.friendSessions(userId)
                .onSuccess { _ui.value = _ui.value.copy(loading = false, sessions = it) }
                .onFailure {
                    _ui.value = _ui.value.copy(
                        loading = false,
                        // The real reason, not "failed" — RLS refusals read very
                        // differently from being offline.
                        error = it.message ?: it::class.simpleName ?: "Unknown error",
                    )
                }
            refreshSocial(userId)
        }
    }

    /**
     * Ally state and worn title ride on cached reads (friends has a 30s TTL,
     * and the board tab usually already warmed the leaderboard row), so
     * visiting a hunter costs no extra server chatter.
     */
    private suspend fun refreshSocial(userId: String) {
        cloud.friends().onSuccess { rows ->
            val row = rows.firstOrNull { it.userId == userId }
            val state = when {
                row == null -> AllyState.None
                row.accepted -> AllyState.Ally
                row.incoming -> AllyState.Incoming
                else -> AllyState.Pending
            }
            _ui.value = _ui.value.copy(allyState = state, allyBusy = false)
        }
        cloud.leaderboard().onSuccess { rows ->
            // The hunter's worn title comes from their leaderboard row; the id is
            // kept too so the crest can show its rarity.
            val titleId = rows.firstOrNull { it.userId == userId }?.currentTitleId
            _ui.value = _ui.value.copy(
                wornTitle = titleId?.let { Titles.byId(it)?.name },
                wornTitleId = titleId,
            )
        }
    }

    /** Send the ally request; the button settles from server truth once it lands. */
    fun addAlly(userId: String) {
        val current = _ui.value
        if (current.allyState != AllyState.None || current.allyBusy) return
        _ui.value = current.copy(allyState = AllyState.Pending, allyBusy = true)
        viewModelScope.launch {
            cloud.requestFriendById(userId)
                .onSuccess {
                    cloud.friends(force = true).onSuccess { rows ->
                        val row = rows.firstOrNull { it.userId == userId }
                        _ui.value = _ui.value.copy(
                            allyState = when {
                                row == null -> AllyState.None
                                row.accepted -> AllyState.Ally
                                row.incoming -> AllyState.Incoming
                                else -> AllyState.Pending
                            },
                            allyBusy = false,
                        )
                    }
                }
                .onFailure { _ui.value = _ui.value.copy(allyState = AllyState.None, allyBusy = false) }
        }
    }
}

/** One hunter's shared training, reached from the feed or the leaderboard. */
@Composable
internal fun HunterScreen(
    userId: String,
    displayName: String,
    onBack: () -> Unit,
    viewModel: HunterViewModel = viewModel(
        factory = viewModelFactory { initializer { HunterViewModel(monarchCloudSync(), monarchAccount(), monarchRepository()) } },
    ),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val equippedFrame by viewModel.equippedFrame.collectAsStateWithLifecycle()
    LaunchedEffect(userId) { viewModel.load(userId) }
    val isMe = ui.myUserId == userId

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(18.dp))

        // Header row: small ghost back control beside the hero identity — the
        // full-width gradient BACK slab is gone; back is an affordance, not a
        // billboard.
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GhostBackButton(onBack)
            IdentityRow(
                displayName = displayName,
                userId = userId,
                wornTitle = ui.wornTitle,
                level = null, // level is not in HunterUi; omitted rather than fetched
                titleId = ui.wornTitleId,
                size = IdentitySize.Hero,
                isMe = isMe,
                frameId = if (isMe) equippedFrame else null,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))

        // ADD ALLY stays tappable (a real action); settled states render as a
        // non-tappable status chip so they never read as a CTA.
        if (ui.myUserId != null && !isMe) {
            Spacer(Modifier.height(6.dp))
            when (ui.allyState) {
                AllyState.None -> AllyChip(
                    label = if (ui.allyBusy) "SENDING…" else "ADD ALLY",
                    tappable = !ui.allyBusy,
                    gold = false,
                    onClick = { viewModel.addAlly(userId) },
                )
                AllyState.Pending, AllyState.Incoming -> AllyChip(
                    label = "REQUEST PENDING",
                    tappable = false,
                    gold = false,
                    onClick = {},
                )
                AllyState.Ally -> AllyChip(
                    label = "ALLY",
                    tappable = false,
                    gold = true,
                    onClick = {},
                )
            }
        }
        Spacer(Modifier.height(14.dp))

        when {
            ui.loading -> SystemWindow(Modifier.fillMaxWidth()) {
                Text(
                    "Consulting the record…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MonarchColors.InkMuted,
                )
            }

            ui.error != null -> SystemWindow(Modifier.fillMaxWidth(), accent = MonarchColors.DangerRed) {
                Text(
                    "RECORD SEALED",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = ChakraPetch,
                    color = MonarchColors.DangerRed,
                    letterSpacing = MonarchTracking.SectionHeader,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    ui.error.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MonarchColors.InkMuted,
                )
            }

            ui.sessions.isEmpty() -> SystemWindow(Modifier.fillMaxWidth()) {
                Text(
                    "NOTHING SHARED",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = ChakraPetch,
                    color = MonarchColors.SovereignGold,
                    letterSpacing = MonarchTracking.SectionHeader,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "This hunter has no sessions you may read — either none are logged, " +
                        "or their visibility does not include you.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MonarchColors.InkMuted,
                )
            }

            else -> {
                // Stat strip built only from data already in HunterUi
                // (sessions list) — fills the former dead space below the list.
                val xpShared = ui.sessions.sumOf { it.xpAwarded }
                val bestStr = ui.sessions.maxOf { it.strengthScore }
                SystemWindow(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        Stat("SHARED", ui.sessions.size.toString(), MonarchColors.SystemGreen)
                        Stat("XP SHARED", "+$xpShared", MonarchColors.EmeraldBright)
                        Stat("BEST STR", bestStr.toString(), MonarchColors.SovereignGold)
                    }
                }

                // ---- derived record: cadence, strength line, strongest hunt ----
                // All computed from the sessions already in state — no extra
                // server chatter, and the former dead space carries rhythm.
                val dayMs = 24L * 60 * 60 * 1000
                val now = System.currentTimeMillis()
                val times = ui.sessions.mapNotNull { it.completedAtMs }.sorted()
                val daysSince = times.lastOrNull()?.let { ((now - it) / dayMs).toInt() }
                val perWeek = times.count { now - it <= 28 * dayMs } / 4.0
                val chrono = ui.sessions.sortedBy { it.completedAtMs ?: Long.MAX_VALUE }
                val strSeries = chrono.map { it.strengthScore.toDouble() }
                val best = chrono.maxBy { it.strengthScore }

                SystemWindow(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        Stat(
                            "LAST HUNT",
                            daysSince?.let { "${it}D AGO" } ?: "UNRECORDED",
                            MonarchColors.SystemGreen,
                        )
                        Stat("CADENCE", "${"%.1f".format(perWeek)}/WK", MonarchColors.EmeraldBright)
                        Stat("SETS MOVED", ui.sessions.sumOf { it.sets }.toString(), MonarchColors.SovereignGold)
                    }
                }

                // The house line chart: strength across hunts, oldest to newest.
                SystemWindow(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                    Text(
                        "STRENGTH LINE",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = ChakraPetch,
                        color = MonarchColors.InkMuted,
                        letterSpacing = MonarchTracking.InlineLabel,
                    )
                    Spacer(Modifier.height(6.dp))
                    if (strSeries.size >= 2) {
                        TrendChart(strSeries, MonarchColors.SystemGreen)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${strSeries.size} hunts on the line · best ${strSeries.max().toInt()}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = ChakraPetch,
                            color = MonarchColors.InkMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else {
                        // A single hunt cannot draw a line — say so instead of
                        // leaving a blank canvas.
                        Text(
                            "One hunt on record — the line begins with the next.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MonarchColors.InkMuted,
                        )
                    }
                }

                // The signature hunt, framed gold so the record has a summit.
                SystemWindow(Modifier.fillMaxWidth().padding(bottom = 10.dp), accent = MonarchColors.SovereignGold) {
                    Text(
                        "STRONGEST HUNT",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = ChakraPetch,
                        color = MonarchColors.SovereignGold,
                        letterSpacing = MonarchTracking.InlineLabel,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        best.title.ifBlank { best.label },
                        style = MaterialTheme.typography.titleSmall,
                        fontFamily = ChakraPetch,
                        fontWeight = FontWeight.Bold,
                        color = MonarchColors.EmeraldBright,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "STR ${best.strengthScore} · +${best.xpAwarded} XP" +
                            (best.completedAtMs?.let { " · ${formatDate(it, "MMM d") }" } ?: ""),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = ChakraPetch,
                        color = MonarchColors.InkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                SectionHeader("Recent hunts")
                ui.sessions.forEach { session ->
                    SystemWindow(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                session.title.ifBlank { session.label },
                                style = MaterialTheme.typography.titleSmall,
                                fontFamily = ChakraPetch,
                                fontWeight = FontWeight.Bold,
                                color = MonarchColors.EmeraldBright,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            session.completedAtMs?.let {
                                Text(
                                    formatDate(it, "MMM d"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MonarchColors.InkMuted,
                                )
                            }
                        }
                        if (session.note.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                session.note,
                                style = MaterialTheme.typography.bodySmall,
                                color = MonarchColors.InkMuted,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Stat("SETS", session.sets.toString(), MonarchColors.SystemGreen)
                            Stat("XP", "+${session.xpAwarded}", MonarchColors.EmeraldBright)
                            Stat("STR", session.strengthScore.toString(), MonarchColors.SovereignGold)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
    }
}

/** Small ghost back control in the header row — bordered, never a gradient slab. */
@Composable
private fun GhostBackButton(onBack: () -> Unit) {
    val shape = MaterialTheme.shapes.small
    Box(
        Modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(MonarchColors.VaultHigh, MonarchColors.Vault)), shape)
            .border(1.dp, MonarchColors.Rune, shape)
            .clickable(onClick = onBack)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "←",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.Bold,
                color = MonarchColors.EmeraldBright,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "BACK",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.Bold,
                color = MonarchColors.InkMuted,
                letterSpacing = MonarchTracking.InlineLabel,
            )
        }
    }
}

/**
 * Ally status chip: compact and clearly non-CTA. Only the ADD ALLY state is
 * actually tappable; ALLY / REQUEST PENDING are pure status.
 */
@Composable
private fun AllyChip(
    label: String,
    tappable: Boolean,
    gold: Boolean,
    onClick: () -> Unit,
) {
    val shape = MaterialTheme.shapes.small
    val accent = if (gold) MonarchColors.SovereignGold else MonarchColors.EmeraldBright
    Box(
        Modifier
            .then(if (tappable) Modifier.clickable(onClick = onClick) else Modifier)
            .background(Brush.verticalGradient(listOf(MonarchColors.VaultHigh, MonarchColors.Vault)), shape)
            .border(1.dp, accent, shape)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = accent,
            letterSpacing = MonarchTracking.InlineLabel,
        )
    }
}

@Composable
private fun Stat(label: String, value: String, accent: androidx.compose.ui.graphics.Color) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            color = MonarchColors.InkMuted,
            letterSpacing = MonarchTracking.InlineLabel,
        )
        Text(
            value,
            style = MaterialTheme.typography.labelLarge,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = accent,
        )
    }
}
