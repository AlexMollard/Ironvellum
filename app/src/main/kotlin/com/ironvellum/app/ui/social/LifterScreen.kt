package com.ironvellum.app.ui.social

import androidx.compose.ui.res.stringResource
import com.ironvellum.app.R
import com.ironvellum.app.data.Words
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.ui.asString
import com.ironvellum.app.ui.components.PushedHeader
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.ironvellum.app.data.cloud.FriendRow
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
    /** The crest they wear, from the same leaderboard row; null when bare or unknown. */
    val wornCrestId: String? = null,
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
            _ui.value = LifterUi(loading = false, error = Words.text(UiText.themed(R.string.lifter_none_selected, R.string.lifter_none_selected__t)))
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
                        error = it.message ?: it::class.simpleName ?: Words.text(UiText.res(R.string.lifter_unknown_error)),
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
                allyError = error.message ?: error::class.simpleName ?: Words.text(UiText.res(R.string.lifter_unknown_error)),
                allyBusy = false,
            )
        }
        cloud.leaderboard().onSuccess { rows ->
            // The lifter's worn title comes from their leaderboard row; the id is
            // kept too so the crest can show its rarity.
            val ally = rows.firstOrNull { it.userId == userId }
            val titleId = ally?.currentTitleId
            _ui.value = _ui.value.copy(
                wornTitle = titleId?.let { Titles.byId(it)?.name },
                wornTitleId = titleId,
                wornCrestId = ally?.crestId,
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
                        allyError = it.message ?: it::class.simpleName ?: Words.text(UiText.res(R.string.lifter_unknown_failure)),
                    )
                }
        }
    }

    fun removeAlly(userId: String) = act {
        cloud.removeFriend(userId).onSuccess {
            _ui.value = _ui.value.copy(allyState = AllyState.None, notice = Words.text(UiText.themed(R.string.lifter_no_longer_allies, R.string.lifter_no_longer_allies__t)))
        }
    }

    fun setMuted(userId: String, mute: Boolean) = act {
        (if (mute) cloud.mute(userId) else cloud.unmute(userId)).onSuccess {
            _ui.value = _ui.value.copy(muted = mute, notice = Words.text(UiText.res(if (mute) R.string.lifter_muted else R.string.lifter_unmuted)))
        }
    }

    fun block(userId: String) = act {
        cloud.block(userId).onSuccess { _ui.value = _ui.value.copy(blocked = true) }
    }

    fun report(userId: String, reason: ReportReason, note: String) = act {
        cloud.report(targetUserId = userId, reason = reason, note = note).onSuccess {
            _ui.value = _ui.value.copy(notice = Words.text(UiText.res(R.string.lifter_report_sent)))
        }
    }

    private fun act(call: suspend () -> Result<Unit>) {
        if (_ui.value.actionBusy) return
        _ui.value = _ui.value.copy(actionBusy = true, actionError = null, notice = null)
        viewModelScope.launch {
            call().onFailure {
                _ui.value = _ui.value.copy(actionError = it.message ?: it::class.simpleName ?: Words.text(UiText.res(R.string.lifter_unknown_failure)))
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
    val named = displayName.isNotBlank()
    val canManage = ui.myUserId != null && !isMe

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(18.dp))

        PushedHeader(Words.folio.c, onBack)
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
            frameId = if (isMe) equippedFrame else ui.wornCrestId,
            trailing = if (canManage) {
                {
                    when (ui.allyState) {
                        null -> {}
                        AllyState.None -> AllyChip(
                            label = if (ui.allyBusy) {
                                stringResource(R.string.lifter_sending)
                            } else {
                                UiText.themed(R.string.lifter_add_ally_button, R.string.lifter_add_ally_button__t).asString()
                            },
                            tappable = !ui.allyBusy,
                            onClick = { viewModel.addAlly(userId) },
                        )
                        AllyState.Pending, AllyState.Incoming -> AllyStatus(stringResource(R.string.lifter_request_pending), check = false)
                        AllyState.Ally -> AllyStatus(Words.ally.c, check = true)
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
            SocialErrorBanner(
                UiText.themed(R.string.lifter_ally_request_failed, R.string.lifter_ally_request_failed__t, ui.allyError.orEmpty()).asString(),
            )
        }
        Spacer(Modifier.height(14.dp))

        when {
            ui.loading -> Text(
                UiText.themed(R.string.lifter_opening, R.string.lifter_opening__t).asString(),
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
            )

            ui.error != null -> Text(
                UiText.themed(R.string.lifter_open_failed, R.string.lifter_open_failed__t, ui.error.orEmpty()).asString(),
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.DangerRed,
            )

            ui.sessions.isEmpty() -> Text(
                UiText.themed(R.string.lifter_nothing_shared, R.string.lifter_nothing_shared__t).asString(),
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
                    add(
                        UiText.themedPlural(
                            R.plurals.lifter_summary_shared,
                            R.plurals.lifter_summary_shared__t,
                            ui.sessions.size,
                        ).asString(),
                    )
                    add(stringResource(R.string.lifter_xp_gain, grouped(ui.sessions.sumOf { it.xpAwarded }.toLong())))
                    add(stringResource(R.string.lifter_summary_per_week, String.format(Locale.ENGLISH, "%.1f", perWeek)))
                    daysSince?.let {
                        add(
                            when (it) {
                                0 -> UiText.themed(R.string.lifter_summary_last_today, R.string.lifter_summary_last_today__t)
                                1 -> UiText.themed(R.string.lifter_summary_last_yesterday, R.string.lifter_summary_last_yesterday__t)
                                else -> UiText.themedPlural(
                                    R.plurals.lifter_summary_last_days_ago,
                                    R.plurals.lifter_summary_last_days_ago__t,
                                    it,
                                )
                            }.asString(),
                        )
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
                            stringResource(R.string.lifter_strength_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = IronvellumColors.Ink,
                            modifier = Modifier.weight(1f),
                        )
                        if (strSeries.size >= 2) {
                            Text(
                                UiText.themed(R.string.lifter_strength_peak, R.string.lifter_strength_peak__t, strSeries.max().toInt()).asString(),
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
                            valueText = { Words.text(UiText.res(R.string.lifter_strength_value, it)) },
                            dateText = { chrono[it].completedAtMs?.let { ms -> formatDate(ms, "d MMM") } },
                        )
                    } else {
                        // A single trial cannot draw a line: say so instead of
                        // leaving a blank canvas.
                        Text(
                            UiText.themed(R.string.lifter_strength_single, R.string.lifter_strength_single__t).asString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = IronvellumColors.InkMuted,
                        )
                    }
                }

                SectionHeader(UiText.themed(R.string.lifter_recent_header, R.string.lifter_recent_header__t).asString(), topPadding = 20.dp)
                // Bounded at the source: CloudSync.friendSessions fetches at
                // most 20 rows, so this plain Column never composes more.
                ui.sessions.forEach { session ->
                    val headline = session.title.ifBlank { session.label }.ifBlank { Words.trial.c }
                    val subline = buildList {
                        session.completedAtMs?.let { add(formatDate(it, "d MMM")) }
                        add(UiText.plural(R.plurals.common_sets_count, session.sets).asString())
                        add(stringResource(R.string.lifter_str_figure, session.strengthScore))
                        if (session.editedAtMs != null) add(stringResource(R.string.lifter_amended))
                    }.joinToString(" · ")
                    ListRow(
                        label = headline,
                        subline = subline,
                        value = stringResource(R.string.lifter_xp_gain, grouped(session.xpAwarded.toLong())),
                        valueColor = IronvellumColors.SovereignGold,
                        onClickLabel = Words.openTrial.c,
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
            SectionHeader(UiText.themed(R.string.lifter_manage_header, R.string.lifter_manage_header__t).asString(), topPadding = 20.dp)
            ui.muted?.let { muted ->
                SettingsSwitchRow(
                    label = stringResource(R.string.lifter_mute_label),
                    caption = UiText.themed(R.string.lifter_mute_caption, R.string.lifter_mute_caption__t).asString(),
                    checked = muted,
                    onCheckedChange = { if (!ui.actionBusy) viewModel.setMuted(userId, it) },
                )
                InkDivider()
            }
            if (ui.allyState == AllyState.Ally) {
                ManageRow(UiText.themed(R.string.lifter_remove_ally_label, R.string.lifter_remove_ally_label__t).asString(), IronvellumColors.Ink) { if (!ui.actionBusy) confirmRemove = true }
            }
            ManageRow(stringResource(R.string.lifter_block_label), IronvellumColors.DangerRed) { if (!ui.actionBusy) confirmBlock = true }
            ManageRow(stringResource(R.string.lifter_report_label), IronvellumColors.DangerRed) { if (!ui.actionBusy) reporting = true }
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
            title = {
                Text(
                    if (named) {
                        UiText.themed(R.string.lifter_remove_title, R.string.lifter_remove_title__t, displayName).asString()
                    } else {
                        UiText.themed(R.string.lifter_remove_title_unnamed, R.string.lifter_remove_title_unnamed__t).asString()
                    },
                )
            },
            text = { Text(UiText.themed(R.string.lifter_remove_body, R.string.lifter_remove_body__t).asString()) },
            confirmButton = {
                IronvellumButton(label = stringResource(R.string.lifter_remove_button), onClick = {
                    confirmRemove = false
                    viewModel.removeAlly(userId)
                }, danger = true)
            },
            dismissButton = {
                IronvellumButton(label = stringResource(R.string.lifter_keep_button), onClick = { confirmRemove = false }, quiet = true)
            },
        )
    }

    if (confirmBlock) {
        IronvellumDialog(
            onDismissRequest = { confirmBlock = false },
            title = {
                Text(
                    if (named) {
                        stringResource(R.string.lifter_block_title, displayName)
                    } else {
                        UiText.themed(R.string.lifter_block_title_unnamed, R.string.lifter_block_title_unnamed__t).asString()
                    },
                )
            },
            text = {
                Text(
                    if (named) {
                        UiText.themed(R.string.lifter_block_body, R.string.lifter_block_body__t, displayName).asString()
                    } else {
                        UiText.themed(R.string.lifter_block_body_unnamed, R.string.lifter_block_body_unnamed__t).asString()
                    },
                )
            },
            confirmButton = {
                IronvellumButton(label = stringResource(R.string.lifter_block_label), onClick = {
                    confirmBlock = false
                    viewModel.block(userId)
                }, danger = true)
            },
            dismissButton = {
                IronvellumButton(label = stringResource(R.string.common_cancel), onClick = { confirmBlock = false }, quiet = true)
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
