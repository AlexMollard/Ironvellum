package com.ironvellum.app.ui.social

import com.ironvellum.app.ui.components.PushedHeader
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.ironvellum.app.data.cloud.FriendRow
import com.ironvellum.app.domain.fmt
import androidx.compose.foundation.layout.Arrangement
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
import com.ironvellum.app.ui.components.IronvellumDialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.ironvellum.app.data.cloud.FriendSession
import com.ironvellum.app.data.cloud.ReportReason
import com.ironvellum.app.domain.Titles
import com.ironvellum.app.ui.components.SectionHeader
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.components.InkDivider
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.components.ListRow
import com.ironvellum.app.ui.components.SettingsSwitchRow
import com.ironvellum.app.ui.components.TapRow
import com.ironvellum.app.ui.components.TrendChart
import com.ironvellum.app.ui.components.formatDate
import com.ironvellum.app.ui.components.plural
import java.util.Locale
import com.ironvellum.app.ui.ironvellumAccount
import com.ironvellum.app.ui.ironvellumCloudSync
import com.ironvellum.app.ui.ironvellumRepository
import com.ironvellum.app.ui.theme.IronvellumColors
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
    /** Null while unknown: the mute button stays hidden rather than guessing. */
    val muted: Boolean? = null,
    /** One lifter action (remove, mute, block, report) in flight at a time. */
    val actionBusy: Boolean = false,
    /** A refused action, verbatim — rate limits read as the server wrote them. */
    val actionError: String? = null,
    val notice: String? = null,
    /** A block landed: neither side can see the other any more, so the screen leaves. */
    val blocked: Boolean = false,
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
            _ui.value = LifterUi(loading = false, error = "No Ironbound selected.")
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
        // Unknown on failure: a guessed "not muted" would offer MUTE to someone
        // already muted, and the second insert reads as a broken button.
        cloud.mutedIds().onSuccess { ids -> _ui.value = _ui.value.copy(muted = userId in ids) }
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
                .onFailure {
                    _ui.value = _ui.value.copy(
                        allyState = AllyState.None,
                        allyBusy = false,
                        allyError = it.message ?: it::class.simpleName ?: "Unknown failure",
                    )
                }
        }
    }

    fun removeAlly(userId: String) = act {
        cloud.removeFriend(userId).onSuccess {
            _ui.value = _ui.value.copy(allyState = AllyState.None, notice = "No longer allies.")
        }
    }

    fun setMuted(userId: String, mute: Boolean) = act {
        (if (mute) cloud.mute(userId) else cloud.unmute(userId)).onSuccess {
            _ui.value = _ui.value.copy(muted = mute, notice = if (mute) "Muted." else "Unmuted.")
        }
    }

    fun block(userId: String) = act {
        cloud.block(userId).onSuccess { _ui.value = _ui.value.copy(blocked = true) }
    }

    fun report(userId: String, reason: ReportReason, note: String) = act {
        cloud.report(targetUserId = userId, reason = reason, note = note).onSuccess {
            _ui.value = _ui.value.copy(notice = "Report sent — thanks.")
        }
    }

    private fun act(call: suspend () -> Result<Unit>) {
        if (_ui.value.actionBusy) return
        _ui.value = _ui.value.copy(actionBusy = true, actionError = null, notice = null)
        viewModelScope.launch {
            call().onFailure {
                _ui.value = _ui.value.copy(actionError = it.message ?: it::class.simpleName ?: "Unknown failure")
            }
            _ui.value = _ui.value.copy(actionBusy = false)
        }
    }
}

/** One lifter's shared training, reached from the feed or the leaderboard. */
@Composable
internal fun LifterScreen(
    userId: String,
    displayName: String,
    onBack: () -> Unit,
    onOpenTrial: (sessionId: String, ownerId: String, headline: String) -> Unit,
    viewModel: LifterViewModel = viewModel(
        factory = viewModelFactory { initializer { LifterViewModel(ironvellumCloudSync(), ironvellumAccount(), ironvellumRepository()) } },
    ),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val equippedFrame by viewModel.equippedFrame.collectAsStateWithLifecycle()
    LaunchedEffect(userId) { viewModel.load(userId) }
    val isMe = ui.myUserId == userId
    // A block ends visibility both ways, so this record has nothing left to show.
    LaunchedEffect(ui.blocked) { if (ui.blocked) onBack() }
    var confirmRemove by remember { mutableStateOf(false) }
    var confirmBlock by remember { mutableStateOf(false) }
    var reporting by remember { mutableStateOf(false) }
    val name = displayName.ifBlank { "this Ironbound" }
    val canManage = ui.myUserId != null && !isMe

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(18.dp))

        PushedHeader("Folio", onBack)
        // The ally state sits beside the name: the one offer ("Add ally") is a chip, the
        // settled states are InkMuted text so they never read as a button. Unknown until the
        // friends read answers: nothing is shown, never a premature offer.
        IdentityRow(
            displayName = displayName,
            userId = userId,
            wornTitle = ui.wornTitle,
            level = null, // level is not in LifterUi; omitted rather than fetched
            size = IdentitySize.Hero,
            isMe = isMe,
            frameId = if (isMe) equippedFrame else null,
            trailing = if (canManage) {
                {
                    when (ui.allyState) {
                        null -> {}
                        AllyState.None -> AllyChip(
                            label = if (ui.allyBusy) "Sending…" else "Add ally",
                            tappable = !ui.allyBusy,
                            onClick = { viewModel.addAlly(userId) },
                        )
                        AllyState.Pending, AllyState.Incoming -> AllyStatus("Request pending", check = false)
                        AllyState.Ally -> AllyStatus("Ally", check = true)
                    }
                }
            } else {
                null
            },
            modifier = Modifier.fillMaxWidth(),
        )
        // The ally read itself failed, or a sent request was refused: say
        // so instead of silently guessing or snapping the button back.
        if (canManage && ui.allyError != null && (ui.allyState == null || ui.allyState == AllyState.None)) {
            Spacer(Modifier.height(6.dp))
            SocialErrorBanner("Ally request failed: ${ui.allyError}")
        }
        Spacer(Modifier.height(14.dp))

        when {
            ui.loading -> Text(
                "Opening the folio…",
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
            )

            ui.error != null -> Text(
                "The folio won't open. ${ui.error.orEmpty()}",
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.DangerRed,
            )

            ui.sessions.isEmpty() -> Text(
                "Nothing shared. This Ironbound has no trials you may read: either none are sealed, " +
                    "or their visibility does not include you.",
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
            )

            else -> {
                // Everything below is derived from the sessions already in state:
                // no extra server chatter.
                val dayMs = 24L * 60 * 60 * 1000
                val now = System.currentTimeMillis()
                val times = ui.sessions.mapNotNull { it.completedAtMs }.sorted()
                val daysSince = times.lastOrNull()?.let { ((now - it) / dayMs).toInt() }
                val perWeek = times.count { now - it <= 28 * dayMs } / 4.0
                val chrono = ui.sessions.sortedBy { it.completedAtMs ?: Long.MAX_VALUE }
                val strSeries = chrono.map { it.strengthScore.toDouble() }

                val summary = buildList {
                    add("${ui.sessions.size} ${plural(ui.sessions.size, "trial", "trials")} shared")
                    add("+${grouped(ui.sessions.sumOf { it.xpAwarded }.toLong())} XP")
                    add(String.format(Locale.ENGLISH, "%.1f", perWeek) + " a week")
                    daysSince?.let {
                        add("last trial " + when (it) { 0 -> "today"; 1 -> "yesterday"; else -> "$it days ago" })
                    }
                }.joinToString(" · ")
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                )
                Spacer(Modifier.height(14.dp))

                // The one strength chart; TrendChart marks the peak gold on its own.
                InkPanel(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Strength line",
                            style = MaterialTheme.typography.titleSmall,
                            color = IronvellumColors.Ink,
                            modifier = Modifier.weight(1f),
                        )
                        if (strSeries.size >= 2) {
                            Text(
                                "PEAK ${strSeries.max().toInt()}",
                                style = MaterialTheme.typography.labelSmall,
                                color = IronvellumColors.SovereignGold,
                                letterSpacing = IronvellumTracking.InlineLabel,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (strSeries.size >= 2) {
                        TrendChart(
                            strSeries,
                            startLabel = chrono.first().completedAtMs?.let { formatDate(it, "d MMM") },
                            endLabel = chrono.last().completedAtMs?.let { formatDate(it, "d MMM") },
                            valueText = { "%.0f strength".fmt(it) },
                            dateText = { chrono[it].completedAtMs?.let { ms -> formatDate(ms, "d MMM") } },
                        )
                    } else {
                        // A single trial cannot draw a line: say so instead of
                        // leaving a blank canvas.
                        Text(
                            "One trial on record. The line begins with the next.",
                            style = MaterialTheme.typography.bodySmall,
                            color = IronvellumColors.InkMuted,
                        )
                    }
                }

                SectionHeader("Recent trials", topPadding = 20.dp)
                // Bounded at the source: CloudSync.friendSessions fetches at
                // most 20 rows, so this plain Column never composes more.
                ui.sessions.forEach { session ->
                    val headline = session.title.ifBlank { session.label }.ifBlank { "Trial" }
                    val subline = buildList {
                        session.completedAtMs?.let { add(formatDate(it, "d MMM")) }
                        add("${session.sets} ${plural(session.sets, "set", "sets")}")
                        add("STR ${session.strengthScore}")
                        if (session.editedAtMs != null) add("amended")
                    }.joinToString(" · ")
                    ListRow(
                        label = headline,
                        subline = subline,
                        value = "+${grouped(session.xpAwarded.toLong())} XP",
                        valueColor = IronvellumColors.SovereignGold,
                        onClickLabel = "Open trial",
                        onClick = if (session.id.isNotBlank()) {
                            { onOpenTrial(session.id, userId, headline) }
                        } else {
                            null
                        },
                    )
                    InkDivider()
                }
            }
        }

        // Last, after the record: these are rare, heavy actions, and at the top
        // they crowded out the training the screen exists to show.
        if (canManage) {
            SectionHeader("Manage folio", topPadding = 20.dp)
            ui.muted?.let { muted ->
                SettingsSwitchRow(
                    label = "Mute",
                    caption = "Hides their trials, remarks and tributes. They aren't told.",
                    checked = muted,
                    onCheckedChange = { if (!ui.actionBusy) viewModel.setMuted(userId, it) },
                )
                InkDivider()
            }
            if (ui.allyState == AllyState.Ally) {
                ManageRow("Remove ally", IronvellumColors.Ink) { if (!ui.actionBusy) confirmRemove = true }
            }
            ManageRow("Block", IronvellumColors.DangerRed) { if (!ui.actionBusy) confirmBlock = true }
            ManageRow("Report", IronvellumColors.DangerRed) { if (!ui.actionBusy) reporting = true }
            ui.notice?.let {
                Spacer(Modifier.height(10.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    color = IronvellumColors.InkMuted,
                )
            }
            ui.actionError?.let {
                Spacer(Modifier.height(10.dp))
                SocialErrorBanner(it)
            }
        }

        Spacer(Modifier.height(20.dp))
    }

    if (confirmRemove) {
        IronvellumDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove $name as an ally?") },
            text = { Text("Their allies-only trials leave your tidings. Either of you can send a new request later.") },
            confirmButton = {
                IronvellumButton(label = "Remove", onClick = {
                    confirmRemove = false
                    viewModel.removeAlly(userId)
                }, danger = true)
            },
            dismissButton = {
                IronvellumButton(label = "Keep", onClick = { confirmRemove = false }, quiet = true)
            },
        )
    }

    if (confirmBlock) {
        IronvellumDialog(
            onDismissRequest = { confirmBlock = false },
            title = { Text("Block $name?") },
            text = {
                Text(
                    "You and $name will no longer see each other's trials, remarks or tributes, " +
                        "any alliance ends, and neither of you can send an ally request. Unblock any time under Allies.",
                )
            },
            confirmButton = {
                IronvellumButton(label = "Block", onClick = {
                    confirmBlock = false
                    viewModel.block(userId)
                }, danger = true)
            },
            dismissButton = {
                IronvellumButton(label = "Cancel", onClick = { confirmBlock = false }, quiet = true)
            },
        )
    }

    if (reporting) {
        ReportDialog(
            lifterName = displayName,
            onDismiss = { reporting = false },
            onSend = { reason, note ->
                reporting = false
                viewModel.report(userId, reason, note)
            },
        )
    }
}

/** A settled ally state beside the name: InkMuted text, with a check once allied. No box, no tap. */
@Composable
private fun AllyStatus(label: String, check: Boolean) {
    Row(
        Modifier.padding(start = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (check) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = IronvellumColors.InkMuted, modifier = Modifier.size(14.dp))
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = IronvellumColors.InkMuted)
    }
}

/** One plain end-of-list action: a 52dp row with no chevron, [color] Ink or DangerRed, a rule beneath. */
@Composable
private fun ManageRow(label: String, color: Color, onClick: () -> Unit) {
    TapRow(onClickLabel = label, onClick = onClick) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = color)
    }
    InkDivider()
}
