package com.monarch.app.ui.idle

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.monarch.app.data.IdleInputs
import com.monarch.app.data.IdleSnapshot
import com.monarch.app.data.Repository
import com.monarch.app.domain.Idle
import com.monarch.app.domain.IdleRate
import com.monarch.app.domain.IdleState
import com.monarch.app.ui.components.MonarchButton
import com.monarch.app.ui.components.SectionHeader
import com.monarch.app.ui.components.SystemWindow
import com.monarch.app.ui.monarchRepository
import com.monarch.app.ui.theme.ChakraPetch
import com.monarch.app.ui.theme.MonarchColors
import com.monarch.app.ui.theme.MonarchTracking
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Everything the Shadow screen renders, resolved from the repository. */
data class IdleUi(
    val snapshot: IdleSnapshot? = null,
    val inputs: IdleInputs? = null,
)

class IdleViewModel(private val repo: Repository) : ViewModel() {

    val ui: StateFlow<IdleUi> = combine(
        repo.observeIdleSnapshot(),
        repo.observeIdleInputs(),
    ) { snapshot, inputs ->
        IdleUi(snapshot = snapshot, inputs = inputs)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IdleUi())

    /** Transactional on the repo side — double-tap safe. */
    fun collect(onCollected: (Long) -> Unit) {
        viewModelScope.launch {
            onCollected(repo.collectIdle(System.currentTimeMillis()))
        }
    }
}

@Composable
fun IdleScreen(
    viewModel: IdleViewModel =
        viewModel(factory = viewModelFactory { initializer { IdleViewModel(monarchRepository()) } }),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    // Heartbeat so the pending figure visibly climbs while the screen is open.
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(1_000)
        }
    }
    // Last collect result, shown briefly so the banked amount has a landing moment.
    var justCollected by remember { mutableStateOf<Long?>(null) }

    val snapshot = ui.snapshot
    val inputs = ui.inputs

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        SectionHeader("THE SHADOW ARMY")

        if (snapshot == null || inputs == null) {
            // Brief empty frame while the flows warm up; never fake numbers.
            SystemWindow {
                Text(
                    "The shadows are assembling...",
                    fontFamily = ChakraPetch,
                    color = MonarchColors.InkMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            ArmyWindow(snapshot.state, snapshot.rate)
            SectionHeader("WHY THE RATE")
            RateWindow(snapshot.rate, inputs)
            val pending = Idle.accrued(snapshot.state, snapshot.rate, nowMs)
            SectionHeader("PENDING")
            CollectWindow(
                pending = pending,
                justCollected = justCollected,
                onCollect = {
                    viewModel.collect { amount ->
                        if (amount > 0) justCollected = amount
                    }
                },
            )
            CapWindow()
        }

        // Bottom-nav clearance — the collect button must never sit under it.
        Spacer(Modifier.height(120.dp))
    }
}

/** The army at a glance: banked essence, shadow count, active rate per hour. */
@Composable
private fun ArmyWindow(state: IdleState, rate: IdleRate) {
    SystemWindow(accent = MonarchColors.Emerald) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "ESSENCE",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = ChakraPetch,
                letterSpacing = MonarchTracking.InlineLabel,
                color = MonarchColors.InkMuted,
            )
            Text(
                formatEssence(state.essence),
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.Bold,
                fontSize = 40.sp,
                color = MonarchColors.Ink,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ArmyStat(
                    label = "SHADOWS",
                    value = state.shadows.toString(),
                    modifier = Modifier.weight(1f),
                )
                ArmyStat(
                    label = "RELIC",
                    value = "×${"%.2f".format(state.relicMultiplier)}",
                    modifier = Modifier.weight(1f),
                )
                ArmyStat(
                    label = "RATE",
                    value = "${"%.1f".format(rate.perHour)}/H",
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ArmyStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(
                Brush.verticalGradient(listOf(MonarchColors.VaultHigh, MonarchColors.Vault)),
                MaterialTheme.shapes.small,
            )
            .padding(12.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            letterSpacing = MonarchTracking.InlineLabel,
            color = MonarchColors.InkMuted,
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = MonarchColors.Emerald,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * WHY the rate is what it is. Training drives everything here: the inputs are
 * shown raw so a decaying rate is legible, not mysterious.
 */
@Composable
private fun RateWindow(rate: IdleRate, inputs: IdleInputs) {
    val atFloor = inputs.sessionsLast7d == 0 && inputs.volumeLast7d == 0.0
    SystemWindow(accent = if (atFloor) MonarchColors.SovereignGold else MonarchColors.Emerald) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (atFloor) {
                Text(
                    "The shadows have heard nothing from you. Your rate has decayed to its floor — return to training and they will rise again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MonarchColors.Ink,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Text(
                    "Your training drives this rate. Idle time only collects at the pace your shadows have earned.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MonarchColors.InkMuted,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            RateRow("SESSIONS · 7 DAYS", "${inputs.sessionsLast7d}")
            RateRow("VOLUME · 7 DAYS", "${"%,.0f".format(inputs.volumeLast7d)} KG")
            RateRow("SKILLS UNLOCKED", "${inputs.skillsUnlocked}")
            RateRow("STREAK", "${inputs.streakDays} D")
            RateRow("TRAINING FACTOR", "×${"%.2f".format(rate.trainingFactor)}")
            RateRow("SKILL FACTOR", "×${"%.2f".format(rate.skillFactor)}")
        }
    }
}

@Composable
private fun RateRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = ChakraPetch,
            letterSpacing = MonarchTracking.InlineLabel,
            color = MonarchColors.InkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = MaterialTheme.typography.labelLarge,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = MonarchColors.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The single obvious action. Pending resets to zero the moment collect banks it. */
@Composable
private fun CollectWindow(pending: Long, justCollected: Long?, onCollect: () -> Unit) {
    SystemWindow(accent = MonarchColors.SovereignGold) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "AWAITING COLLECTION",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = ChakraPetch,
                letterSpacing = MonarchTracking.InlineLabel,
                color = MonarchColors.InkMuted,
            )
            Text(
                "+${formatEssence(pending)}",
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
                color = MonarchColors.EmeraldBright,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            justCollected?.let { amount ->
                // Gentle one-line landing for the banked amount; no flash, no snap.
                Text(
                    "+${formatEssence(amount)} essence banked",
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = ChakraPetch,
                    color = MonarchColors.SovereignGold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            MonarchButton(
                label = if (pending > 0) "Collect essence" else "The vault is empty",
                onClick = onCollect,
                enabled = pending > 0,
                gold = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** The offline cap, stated plainly. No player should expect three days to pay out. */
@Composable
private fun CapWindow() {
    SystemWindow(accent = MonarchColors.Bracket) {
        Text(
            "The shadows labour hardest for the first ${Idle.FULL_RATE_HOURS.toInt()} hours, " +
                "tire as the day wears on, and rest entirely after ${Idle.OFFLINE_CAP_HOURS}. " +
                "Return once a day and little is lost.",
            style = MaterialTheme.typography.bodyMedium,
            color = MonarchColors.InkMuted,
            textAlign = TextAlign.Start,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun formatEssence(value: Long): String = String.format("%,d", value)
