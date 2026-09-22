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
import com.ironvellum.app.data.Repository
import com.ironvellum.app.data.cloud.AccountRepository
import com.ironvellum.app.data.cloud.CloudSync
import com.ironvellum.app.data.cloud.FriendRow
import com.ironvellum.app.data.cloud.FriendSession
import com.ironvellum.app.domain.Titles
import com.ironvellum.app.ui.components.SectionHeader
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.components.TrendChart
import com.ironvellum.app.ui.components.formatDate
import java.util.Locale
import com.ironvellum.app.ui.ironvellumAccount
import com.ironvellum.app.ui.ironvellumCloudSync
import com.ironvellum.app.ui.ironvellumRepository
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.inkBorder
import com.ironvellum.app.ui.theme.IronvellumTracking
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal data class LifterUi(
    val loading: Boolean = true,
    val sessions: List<FriendSession> = emptyList(),
    val error: String? = null,
    val myUserId: String? = null,
    /** Null while the ally relationship is UNKNOWN — an offline fetch must
     *  never read as "not allies" or the ADD ALLY button would offer a
     *  duplicate request to someone who already is one. */
    val allyState: AllyState? = null,
    val allyBusy: Boolean = false,
    /** The ally-status read itself failed; shown as a banner, button stays hidden. */
    val allyError: String? = null,
    /** Worn title resolved locally from the leaderboard cache; null when bare or unknown. */
    val wornTitle: String? = null,
    /** The raw title id behind [wornTitle]; feeds the avatar crest's rarity palette. */
    val wornTitleId: String? = null,
)

internal class LifterViewModel(
    private val cloud: CloudSync,
    private val accountRepo: AccountRepository,
    private val repo: Repository,
) : ViewModel() {

    private val _ui = MutableStateFlow(LifterUi())
    val ui: StateFlow<LifterUi> = _ui.asStateFlow()

    // Device-local equipped crest frame; only worn when viewing one's own profile.
    val equippedFrame: StateFlow<String?> = repo.observeEquippedFrame()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun load(userId: String) {
        if (userId.isBlank()) {
            _ui.value = LifterUi(loading = false, error = "No lifter selected.")
            return
        }
        _ui.value = LifterUi(myUserId = accountRepo.account.value?.userId)
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
     * visiting a lifter costs no extra server chatter.
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
            _ui.value = _ui.value.copy(allyState = state, allyBusy = false, allyError = null)
        }.onFailure { error ->
            // Leave allyState unknown: guessing None would offer ADD ALLY to an
            // existing ally, so the button stays hidden until the read answers.
            _ui.value = _ui.value.copy(
                allyError = error.message ?: error::class.simpleName ?: "Unknown error",
                allyBusy = false,
            )
        }
        cloud.leaderboard().onSuccess { rows ->
            // The lifter's worn title comes from their leaderboard row; the id is
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

/** One lifter's shared training, reached from the feed or the leaderboard. */
@Composable
internal fun LifterScreen(
    userId: String,
    displayName: String,
    onBack: () -> Unit,
    viewModel: LifterViewModel = viewModel(
        factory = viewModelFactory { initializer { LifterViewModel(ironvellumCloudSync(), ironvellumAccount(), ironvellumRepository()) } },
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
                level = null, // level is not in LifterUi; omitted rather than fetched
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
                // Unknown until the friends read answers — no chip, never a
                // premature ADD ALLY offer.
                null -> {}
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
            // The ally read itself failed: say so instead of silently guessing.
            if (ui.allyError != null && ui.allyState == null) {
                Spacer(Modifier.height(6.dp))
                InlineErrorBanner("Ally status unknown — the Ledger did not answer: ${ui.allyError}")
            }
        }
        Spacer(Modifier.height(14.dp))

        when {
            ui.loading -> InkPanel(Modifier.fillMaxWidth()) {
                Text(
                    "Consulting the record…",
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                )
            }

            ui.error != null -> InkPanel(Modifier.fillMaxWidth(), accent = IronvellumColors.DangerRed) {
                Text(
                    "RECORD SEALED",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = ChakraPetch,
                    color = IronvellumColors.DangerRed,
                    letterSpacing = IronvellumTracking.SectionHeader,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    ui.error.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                )
            }

            ui.sessions.isEmpty() -> InkPanel(Modifier.fillMaxWidth()) {
                Text(
                    "NOTHING SHARED",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = ChakraPetch,
                    color = IronvellumColors.SovereignGold,
                    letterSpacing = IronvellumTracking.SectionHeader,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "This lifter has no sessions you may read — either none are logged, " +
                        "or their visibility does not include you.",
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                )
            }

            else -> {
                // Stat strip built only from data already in LifterUi
                // (sessions list) — fills the former dead space below the list.
                val xpShared = ui.sessions.sumOf { it.xpAwarded }
                val bestStr = ui.sessions.maxOf { it.strengthScore }
                InkPanel(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        Stat("SHARED", ui.sessions.size.toString(), IronvellumColors.SystemGreen)
                        Stat("XP SHARED", "+$xpShared", IronvellumColors.EmeraldBright)
                        Stat("BEST STR SHARED", bestStr.toString(), IronvellumColors.SovereignGold)
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

                InkPanel(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        Stat(
                            "LAST HUNT",
                            daysSince?.let { "${it}D AGO" } ?: "UNRECORDED",
                            IronvellumColors.SystemGreen,
                        )
                        Stat("CADENCE SHARED", String.format(Locale.ENGLISH, "%.1f", perWeek) + "/WK", IronvellumColors.EmeraldBright)
                        Stat("SETS MOVED", ui.sessions.sumOf { it.sets }.toString(), IronvellumColors.SovereignGold)
                    }
                }

                // The house line chart: strength across sessions, oldest to newest.
                InkPanel(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                    Text(
                        "STRENGTH LINE",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = ChakraPetch,
                        color = IronvellumColors.InkMuted,
                        letterSpacing = IronvellumTracking.InlineLabel,
                    )
                    Spacer(Modifier.height(6.dp))
                    if (strSeries.size >= 2) {
                        TrendChart(strSeries, IronvellumColors.SystemGreen)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${strSeries.size} sessions on the line · best ${strSeries.max().toInt()}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = ChakraPetch,
                            color = IronvellumColors.InkMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else {
                        // A single hunt cannot draw a line — say so instead of
                        // leaving a blank canvas.
                        Text(
                            "One hunt on record — the line begins with the next.",
                            style = MaterialTheme.typography.bodySmall,
                            color = IronvellumColors.InkMuted,
                        )
                    }
                }

                // The signature hunt, framed gold so the record has a summit.
                InkPanel(Modifier.fillMaxWidth().padding(bottom = 10.dp), accent = IronvellumColors.SovereignGold) {
                    Text(
                        "STRONGEST HUNT",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = ChakraPetch,
                        color = IronvellumColors.SovereignGold,
                        letterSpacing = IronvellumTracking.InlineLabel,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        best.title.ifBlank { best.label },
                        style = MaterialTheme.typography.titleSmall,
                        fontFamily = ChakraPetch,
                        fontWeight = FontWeight.Bold,
                        color = IronvellumColors.EmeraldBright,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "STR ${best.strengthScore} · +${best.xpAwarded} XP" +
                            (best.completedAtMs?.let { " · ${formatDate(it, "MMM d") }" } ?: ""),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = ChakraPetch,
                        color = IronvellumColors.InkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                SectionHeader("Recent sessions")
                ui.sessions.forEach { session ->
                    InkPanel(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
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
                                color = IronvellumColors.EmeraldBright,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            session.completedAtMs?.let {
                                Text(
                                    formatDate(it, "MMM d"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = IronvellumColors.InkMuted,
                                )
                            }
                        }
                        if (session.note.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                session.note,
                                style = MaterialTheme.typography.bodySmall,
                                color = IronvellumColors.InkMuted,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Stat("SETS", session.sets.toString(), IronvellumColors.SystemGreen)
                            Stat("XP", "+${session.xpAwarded}", IronvellumColors.EmeraldBright)
                            Stat("STR", session.strengthScore.toString(), IronvellumColors.SovereignGold)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
    }
}

/** Compact failure note — never a dialog, never displacing the record. */
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

/** Small ghost back control in the header row — bordered, never a gradient slab. */
@Composable
private fun GhostBackButton(onBack: () -> Unit) {
    val shape = MaterialTheme.shapes.small
    Box(
        Modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(IronvellumColors.VaultHigh, IronvellumColors.Vault)), shape)
            .inkBorder(IronvellumColors.Rune, shape, 1.dp)
            .clickable(onClick = onBack)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "←",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.Bold,
                color = IronvellumColors.EmeraldBright,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "BACK",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.Bold,
                color = IronvellumColors.InkMuted,
                letterSpacing = IronvellumTracking.InlineLabel,
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
    val accent = if (gold) IronvellumColors.SovereignGold else IronvellumColors.EmeraldBright
    Box(
        Modifier
            .then(if (tappable) Modifier.clickable(onClick = onClick) else Modifier)
            .background(Brush.verticalGradient(listOf(IronvellumColors.VaultHigh, IronvellumColors.Vault)), shape)
            .inkBorder(accent, shape, 1.dp)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = accent,
            letterSpacing = IronvellumTracking.InlineLabel,
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
            color = IronvellumColors.InkMuted,
            letterSpacing = IronvellumTracking.InlineLabel,
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
