package com.monarch.app.ui.social

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.monarch.app.data.cloud.AccountRepository
import com.monarch.app.data.cloud.FriendRow
import com.monarch.app.data.cloud.CloudSync
import com.monarch.app.data.cloud.FriendSession
import com.monarch.app.ui.components.MonarchButton
import com.monarch.app.ui.components.SectionHeader
import com.monarch.app.ui.components.SystemWindow
import com.monarch.app.ui.components.formatDate
import com.monarch.app.ui.monarchAccount
import com.monarch.app.domain.Titles
import com.monarch.app.ui.monarchCloudSync
import com.monarch.app.ui.theme.ChakraPetch
import com.monarch.app.ui.theme.MonarchColors
import com.monarch.app.ui.theme.MonarchTracking
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
)

internal class HunterViewModel(
    private val cloud: CloudSync,
    private val accountRepo: AccountRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(HunterUi())
    val ui: StateFlow<HunterUi> = _ui.asStateFlow()

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
            val titleId = rows.firstOrNull { it.userId == userId }?.currentTitleId
            _ui.value = _ui.value.copy(wornTitle = titleId?.let { Titles.byId(it)?.name })
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
        factory = viewModelFactory { initializer { HunterViewModel(monarchCloudSync(), monarchAccount()) } },
    ),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    LaunchedEffect(userId) { viewModel.load(userId) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(18.dp))
        Text(
            displayName.ifBlank { "HUNTER" }.uppercase(),
            style = MaterialTheme.typography.headlineMedium,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = MonarchColors.Ink,
            letterSpacing = 1.sp,
        )
        Text(
            "SHARED TRAINING",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            color = MonarchColors.InkMuted,
            letterSpacing = MonarchTracking.SectionHeader,
        )
        // Worn title under the hunter's name; omitted cleanly when bare.
        ui.wornTitle?.let { title ->
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = ChakraPetch,
                color = MonarchColors.SovereignGold,
            )
        }
        // ADD ALLY: hidden for yourself, and settled (PENDING / ALLY) once a
        // friendship row exists so a second tap never re-fires the request.
        if (ui.myUserId != null && ui.myUserId != userId) {
            Spacer(Modifier.height(10.dp))
            val (label, gold) = when (ui.allyState) {
                AllyState.None -> "ADD ALLY" to false
                AllyState.Pending, AllyState.Incoming -> "REQUEST PENDING" to false
                AllyState.Ally -> "ALLY" to true
            }
            MonarchButton(
                label = label,
                onClick = { viewModel.addAlly(userId) },
                gold = gold,
                enabled = ui.allyState == AllyState.None && !ui.allyBusy,
                modifier = Modifier.fillMaxWidth(),
            )
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

        Spacer(Modifier.height(16.dp))
        MonarchButton(label = "Back", onClick = onBack, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(20.dp))
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
