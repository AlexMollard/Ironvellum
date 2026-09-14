package com.monarch.app.ui.idle
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
    // Frame-rate heartbeat: the pending figure climbs continuously rather than
    // stepping once a second, so the screen reads as alive.
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameMillis { nowMs = System.currentTimeMillis() }
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
            // Exact, unrounded accrual so both the hero total and the pending
            // figure climb every frame instead of jumping on a poll.
            val pendingExact = Idle.accruedExact(snapshot.state, snapshot.rate, nowMs)
            ArmyWindow(snapshot.state, snapshot.rate, pendingExact)
            SectionHeader("WHY THE RATE")
            RateWindow(snapshot.rate, inputs)
            SectionHeader("PENDING")
            CollectWindow(
                pending = pendingExact,
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

/**
 * The army at a glance. The headline total is banked essence PLUS what is
 * accruing right now, so the number visibly climbs while you watch it — a
 * static total was the main reason this screen felt dead.
 */
@Composable
private fun ArmyWindow(state: IdleState, rate: IdleRate, pendingExact: Double) {
    SystemWindow(accent = MonarchColors.Emerald) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "ESSENCE",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = ChakraPetch,
                    letterSpacing = MonarchTracking.InlineLabel,
                    color = MonarchColors.InkMuted,
                    modifier = Modifier.weight(1f),
                )
                LivePulse(active = rate.perHour > 0.0)
            }
            Text(
                liveEssence(state.essence, pendingExact),
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.Bold,
                fontSize = 40.sp,
                color = MonarchColors.Ink,
                maxLines = 1,
                softWrap = false,
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

/**
 * Slow breathing dot: proof the army is working right now. Deliberately a
 * gentle 1.6s sine — the house rule forbids hard flashing indicators.
 */
@Composable
private fun LivePulse(active: Boolean) {
    if (!active) return
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .size(8.dp)
                .alpha(alpha)
                .background(MonarchColors.EmeraldBright, CircleShape),
        )
        Text(
            "WORKING",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            letterSpacing = MonarchTracking.InlineLabel,
            color = MonarchColors.EmeraldBright,
            modifier = Modifier.alpha(alpha),
        )
    }
}

/**
 * Banked + accruing, with two decimals below a thousand so the digits actually
 * move at a realistic rate (10-60 essence/hour is a fraction per second).
 */
private fun liveEssence(banked: Long, pendingExact: Double): String {
    val total = banked.toDouble() + pendingExact.coerceAtLeast(0.0)
    return if (total < 1_000) "%.2f".format(total) else formatEssence(total.toLong())
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
private fun CollectWindow(pending: Double, justCollected: Long?, onCollect: () -> Unit) {
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
                // Two decimals: at 10-60 essence/hour the integer part moves
                // once a minute, which read as frozen.
                "+" + if (pending < 1_000) "%.2f".format(pending) else formatEssence(pending.toLong()),
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
                color = MonarchColors.EmeraldBright,
                maxLines = 1,
                softWrap = false,
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
                label = if (pending >= 1.0) "Collect essence" else "The vault is empty",
                onClick = onCollect,
                enabled = pending >= 1.0,
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
            "The shadows hold full strength for ${Idle.FULL_RATE_HOURS.toInt()} hours, " +
                "then tire over the next ${Idle.TAPER_WINDOW_HOURS.toInt()} — but they never stop, " +
                "labouring on at a tenth of their strength until you return.",
            style = MaterialTheme.typography.bodyMedium,
            color = MonarchColors.InkMuted,
            textAlign = TextAlign.Start,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun formatEssence(value: Long): String = String.format("%,d", value)
