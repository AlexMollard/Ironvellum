package com.monarch.app.ui.idle
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import com.monarch.app.domain.Gacha
import kotlin.random.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.geometry.Size
import androidx.compose.animation.core.Animatable
import com.monarch.app.ui.social.CrestFrameTreatment
import com.monarch.app.ui.social.crestFrameTreatment
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
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.StrokeCap
import com.monarch.app.data.IdleInputs
import com.monarch.app.data.IdleSnapshot
import com.monarch.app.data.Repository
import com.monarch.app.data.RelicHolding
import com.monarch.app.domain.Idle
import com.monarch.app.domain.IdleRate
import com.monarch.app.domain.IdleState
import com.monarch.app.domain.Reward
import com.monarch.app.domain.RewardRarity
import com.monarch.app.domain.RollResult
import com.monarch.app.ui.components.Achievement
import com.monarch.app.ui.components.AchievementOverlay
import com.monarch.app.ui.components.MonarchButton
import com.monarch.app.ui.components.SectionHeader
import com.monarch.app.ui.components.SystemWindow
import com.monarch.app.ui.monarchRepository
import com.monarch.app.ui.theme.ChakraPetch
import com.monarch.app.ui.components.InkRail
import com.monarch.app.ui.theme.inkBorder
import com.monarch.app.ui.theme.MonarchColors
import com.monarch.app.ui.theme.MonarchTracking
import com.monarch.app.ui.components.RelicSigil
import com.monarch.app.ui.components.CrestRail
import com.monarch.app.ui.components.ShadowBackdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.width
import com.monarch.app.domain.Relics
import java.util.Locale


/** What the shadows earned while the app was closed, shown once on arrival. */
data class AwayReport(val essence: Long, val awayMs: Long)

/** Everything the Shadow screen renders, resolved from the repository. */
data class IdleUi(
    val snapshot: IdleSnapshot? = null,
    val inputs: IdleInputs? = null,
    /** Banked, unspent shadow draws — earned by levelling, spent here. */
    val rolls: Int = 0,
    /** Owned cosmetic crest frames (frameId set) and the one currently worn. */
    val ownedFrames: Set<String> = emptySet(),
    val equippedFrame: String? = null,
)

class IdleViewModel(private val repo: Repository) : ViewModel() {

    val ui: StateFlow<IdleUi> = combine(
        repo.observeIdleSnapshot(),
        repo.observeIdleInputs(),
        repo.observeRolls(),
        repo.observeOwnedFrames(),
        repo.observeEquippedFrame(),
    ) { snapshot, inputs, rolls, ownedFrames, equippedFrame ->
        IdleUi(
            snapshot = snapshot,
            inputs = inputs,
            rolls = rolls,
            ownedFrames = ownedFrames,
            equippedFrame = equippedFrame,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IdleUi())

    /**
     * Relics ride their own flow: `combine` tops out at five typed sources and
     * the vault is independent of the army snapshot anyway.
     */
    val relics: StateFlow<List<RelicHolding>> = repo.observeRelics()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _away = MutableStateFlow<AwayReport?>(null)


    /** The away haul, banked automatically — there is nothing to claim. */
    val away: StateFlow<AwayReport?> = _away.asStateFlow()

    init {
        // Essence banks itself the moment you arrive. Making a player press a
        // button for money they already earned is busywork; what they actually
        // want is to see what the shadows did while they were gone.
        viewModelScope.launch {
            val state = repo.observeIdle().first()
            val awayMs = (System.currentTimeMillis() - state.lastCollectedAtMs).coerceAtLeast(0L)
            val banked = repo.collectIdle(System.currentTimeMillis())
            if (banked > 0) _away.value = AwayReport(banked, awayMs)
        }
    }

    /** Cosmetic only — the repository rejects equipping a frame not owned. */
    fun equipFrame(frameId: String?) {
        viewModelScope.launch { repo.equipFrame(frameId) }
    }

    /** Transactional on the repo side — payout and roll spend land together. */
    fun draw(onDrawn: (RollResult?) -> Unit) {
        viewModelScope.launch {
            onDrawn(repo.spendRoll(seed = System.nanoTime()))
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
    val away by viewModel.away.collectAsStateWithLifecycle()
    val relics by viewModel.relics.collectAsStateWithLifecycle()
    // The reveal for a spent draw; null once the overlay finishes so it never re-shows.
    var drawResult by remember { mutableStateOf<RollResult?>(null) }

    val snapshot = ui.snapshot
    val inputs = ui.inputs

    Box(Modifier.fillMaxSize()) {
        // Atmosphere behind everything: gradient wash, a breathing glow under
        // the hero, and an ambient field that thickens with the army. The page
        // was flat black with one moving number — this is what gives it depth.
        ShadowBackdrop(
            shadows = snapshot?.state?.shadows ?: 0,
            active = (snapshot?.rate?.perHour ?: 0.0) > 0.0,
            modifier = Modifier.matchParentSize(),
        )
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
            // Draws and the collection are ALWAYS visible. Hiding them until
            // the first level-up made the whole feature undiscoverable — a
            // collection screen should show the vault, not an empty wall.
            SectionHeader("SHADOW DRAW")
            DrawWindow(
                rolls = ui.rolls,
                onDraw = { viewModel.draw { result -> if (result != null) drawResult = result } },
            )
            CrestCollection(
                owned = ui.ownedFrames,
                equipped = ui.equippedFrame,
                onEquip = viewModel::equipFrame,
            )
            RelicVault(relics = relics)
            away?.let { AwayWindow(it) }
            CapWindow()
        }

        // Bottom-nav clearance — the collect button must never sit under it.
        Spacer(Modifier.height(120.dp))
    }
    }
    drawResult?.let { result ->
        AchievementOverlay(
            items = listOf(achievementFor(result)),
            onDone = { drawResult = null },
        )
    }
}

/**
 * House-voice copy per rarity tier — the same draw, four different weights of
 * silence. Accent uses only sanctioned palette tokens.
 */
private fun achievementFor(result: RollResult): Achievement {
    val (tagline, accent) = when (result.rarity) {
        RewardRarity.Common -> "A WHISPER IN THE DARK" to MonarchColors.InkMuted
        RewardRarity.Rare -> "THE DARK STIRS" to MonarchColors.SystemGreen
        RewardRarity.Epic -> "THE DARK BENDS" to MonarchColors.Emerald
        RewardRarity.Sovereign -> "THE MONARCH ANSWERS" to MonarchColors.SovereignGold
    }
    val notes = when (val reward = result.reward) {
        is Reward.Shadows -> listOf("ARMY +${reward.count} SHADOWS")
        is Reward.Relic -> listOf("RATE MULTIPLIER ×%.2f".format(reward.multiplier))
        is Reward.CrestFrame -> listOf("CREST FRAME UNLOCKED", "EQUIP IT ON YOUR HUNTER IDENTITY")
    }
    return Achievement(
        banner = "SHADOW DRAWN",
        tagline = tagline,
        name = rewardName(result.reward),
        subtitle = result.rarity.name.uppercase(),
        notes = notes,
        accent = accent,
        // Only relics. A shadows payout is a number, not an object, and a
        // crest already has its own plate treatment in the collection — a
        // generic sigil there would misrepresent the frame that was won.
        sigilSeed = (result.reward as? Reward.Relic)?.name,
    )
}

/**
 * The draw control. Rendered only while rolls are banked — at zero it is
 * absent entirely, never a disabled stub — so levelling is the only way in.
 */
@Composable
private fun DrawWindow(rolls: Int, onDraw: () -> Unit) {
    var oddsOpen by remember { mutableStateOf(false) }
    SystemWindow(accent = MonarchColors.SovereignGold) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (rolls > 0) "$rolls DRAW${if (rolls == 1) "" else "S"} WAITING" else "NO DRAWS BANKED",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = ChakraPetch,
                    letterSpacing = MonarchTracking.InlineLabel,
                    color = if (rolls > 0) MonarchColors.SovereignGold else MonarchColors.InkMuted,
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = if (oddsOpen) "Hide drop rates" else "Show drop rates",
                    tint = if (oddsOpen) MonarchColors.SovereignGold else MonarchColors.InkMuted,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { oddsOpen = !oddsOpen },
                )
            }
            if (oddsOpen) {
                OddsTable()
            }
            // A draw pays shadows, a relic OR a crest — a collection screen
            // showing only frames made a relic roll look like a lost crest.
            Text(
                "Every rank-up earns one draw. A draw yields shadows for the " +
                    "army, a relic that lifts your rate, or a crest frame worn " +
                    "on your hunter.",
                style = MaterialTheme.typography.bodySmall,
                color = MonarchColors.InkMuted,
            )
            if (rolls > 0) {
                MonarchButton(
                    label = "Draw shadow ($rolls)",
                    onClick = onDraw,
                    enabled = true,
                    gold = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                // No dead button: state the path to the next draw instead.
                Text(
                    "RANK UP TO EARN THE NEXT DRAW",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = ChakraPetch,
                    letterSpacing = MonarchTracking.InlineLabel,
                    color = MonarchColors.InkMuted,
                )
            }
        }
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
            // Hero row: the climbing total on the left, the rate gauge on the
            // right at a FIXED size. The dial previously lived in a weighted
            // Row slot with no height, so its arc collapsed to a sliver and was
            // invisible on device — a gauge needs a square box, not a weight.
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.weight(1f)) {
                    // Drifting shadow motes BEHIND the hero number: the army is
                    // visibly present, not just a figure on a panel.
                    ShadowMotes(shadows = state.shadows, modifier = Modifier.matchParentSize())
                    Text(
                        liveEssence(state.essence, pendingExact),
                        fontFamily = ChakraPetch,
                        fontWeight = FontWeight.Bold,
                        fontSize = 44.sp,
                        color = MonarchColors.Ink,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
                RateDial(
                    perHour = rate.perHour,
                    trainingFactor = rate.trainingFactor,
                    modifier = Modifier.size(112.dp),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
            }
        }
    }
}

/**
 * Shadow motes: a handful of dots drifting behind the essence hero. The
 * count scales with the army (minOf(12, shadows)); nothing at zero shadows.
 * One shared infinite phase (linear, 12s loop) drives every mote; per-mote
 * offsets come from a SEEDED Random remembered across recompositions, so the
 * drift is deterministic per composition and never jumps.
 */
@Composable
private fun ShadowMotes(shadows: Int, modifier: Modifier = Modifier) {
    if (shadows <= 0) return
    val count = minOf(12, shadows)
    // Seed constant, not Random(): recomposition must never re-shuffle motes.
    val motes = remember(count) {
        Random(MOTE_SEED).let { rng ->
            List(count) {
                Mote(
                    x = rng.nextFloat(),
                    y = rng.nextFloat(),
                    radius = 1.5f + rng.nextFloat() * 2.5f,
                    phase = rng.nextFloat(),
                    drift = 6f + rng.nextFloat() * 10f, // px excursion either way
                )
            }
        }
    }
    val transition = rememberInfiniteTransition(label = "motes")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "motePhase",
    )
    Canvas(modifier) {
        motes.forEach { mote ->
            // Smooth Lissajous-style wander around the mote's home point.
            val angle = (t + mote.phase) * 2f * PI.toFloat()
            val x = mote.x * size.width + cos(angle) * mote.drift
            val y = mote.y * size.height + sin(angle * 0.7f) * mote.drift * 0.6f
            // Breathing alpha in the same slow register as the drift.
            val alpha = 0.12f + 0.18f * (0.5f + 0.5f * sin(angle * 1.3f))
            drawCircle(
                color = MonarchColors.EmeraldBright.copy(alpha = alpha),
                radius = mote.radius,
                center = Offset(x, y),
            )
        }
    }
}

private data class Mote(
    val x: Float,
    val y: Float,
    val radius: Float,
    val phase: Float,
    val drift: Float,
)

/** Stable seed so the mote field is identical every session and composition. */
private const val MOTE_SEED = 867_5309L

/**
 * The RATE dial: an arc whose sweep is the army's rate against the FULL
 * TRAINED rate. The reference max is DERIVED, not hardcoded: the floor rate
 * is recovered from the live rate by dividing out the observed factors and
 * relic (perHour = floor * trainingFactor * skillFactor * relic), then the
 * full sweep is floor * 4 — the x4 multiplier a committed training week
 * reaches, per Idle's documented balance. The sweep tweens in on open; with
 * animations disabled it simply snaps to its final, readable position.
 */
@Composable
private fun RateDial(
    perHour: Double,
    trainingFactor: Double,
    modifier: Modifier = Modifier,
) {
    // The gauge tracks TRAINING saturation against Idle's documented x4 cap —
    // the one input the hunter actually moves. Measuring the live rate against
    // a relic-free maximum pinned the needle at full for anyone holding a
    // relic, which is why it read as stuck.
    val fraction = (trainingFactor / Idle.MAX_TRAINING_FACTOR).coerceIn(0.0, 1.0).toFloat()
    // Animatable from 0 so the sweep tweens into position on open;
    // animateFloatAsState would start AT target and never animate.
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(fraction) {
        sweep.animateTo(fraction, tween(900, easing = FastOutSlowInEasing))
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 8.dp.toPx()
            val inset = stroke
            val span = 260f
            val start = -220f
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            // Track is deliberately NOT green: a Rune track under a green-to-
            // green sweep gradient left no readable edge, so the needle's
            // position was invisible.
            drawArc(
                color = MonarchColors.Vault,
                startAngle = start,
                sweepAngle = span,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = MonarchColors.EmeraldBright,
                startAngle = start,
                sweepAngle = span * sweep.value,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            // Quarter ticks on the track: a gauge with no scale can't be read
            // even once the fill is legible.
            val cx = size.width / 2f
            val cy = size.height / 2f
            val radius = (size.width - inset * 2) / 2f
            for (i in 0..4) {
                val a = ((start + span * i / 4f) * PI / 180.0).toFloat()
                val outer = radius + stroke * 0.62f
                val innerR = radius + stroke * 0.18f
                drawLine(
                    color = MonarchColors.Rune,
                    start = Offset(cx + cos(a) * innerR, cy + sin(a) * innerR),
                    end = Offset(cx + cos(a) * outer, cy + sin(a) * outer),
                    strokeWidth = stroke * 0.18f,
                )
            }
            // The needle tip: an unambiguous marker for where the value sits.
            val tip = ((start + span * sweep.value) * PI / 180.0).toFloat()
            val tipAt = Offset(cx + cos(tip) * radius, cy + sin(tip) * radius)
            drawCircle(color = MonarchColors.Ink, radius = stroke * 0.62f, center = tipAt)
            drawCircle(color = MonarchColors.EmeraldBright, radius = stroke * 0.34f, center = tipAt)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "RATE",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = ChakraPetch,
                letterSpacing = MonarchTracking.InlineLabel,
                color = MonarchColors.InkMuted,
            )
            Text(
                "${"%.1f".format(perHour)}",
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = MonarchColors.EmeraldBright,
                maxLines = 1,
                softWrap = false,
            )
            Text(
                "PER HOUR",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = ChakraPetch,
                letterSpacing = MonarchTracking.InlineLabel,
                color = MonarchColors.InkMuted,
            )
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
 * Banked + accruing, always to two decimals so the digits visibly move at
 * realistic rates (10-60 essence/hour is a fraction per second).
 */
private fun liveEssence(banked: Long, pendingExact: Double): String {
    val total = banked.toDouble() + pendingExact.coerceAtLeast(0.0)
    // Two decimals at EVERY magnitude: falling back to whole units past 1,000
    // froze the counter again — at 20/hour the integer moves once every three
    // minutes. Grouped so six figures stay readable.
    return "%,.2f".format(total)
}

@Composable
private fun ArmyStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(
                Brush.verticalGradient(listOf(MonarchColors.VaultHigh, MonarchColors.Vault)),
                MaterialTheme.shapes.small,
            )
            // Clipped to an ink shape but never inked: without the bleed-plus-firm
            // border pass this tile read flat beside every other inked surface.
            .inkBorder(MonarchColors.Rune, MaterialTheme.shapes.small)
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
            // Each factor's bar is its SHARE of the two combined, so the
            // relative weight of training vs. permanent skill reads at a glance.
            FactorRow(
                label = "TRAINING FACTOR",
                value = "×${"%.2f".format(rate.trainingFactor)}",
                // How far up its OWN ceiling, not its share of the product.
                share = ((rate.trainingFactor - 1.0) / (Idle.MAX_TRAINING_FACTOR - 1.0)).toFloat(),
            )
            FactorRow(
                label = "SKILL FACTOR",
                value = "×${"%.2f".format(rate.skillFactor)}",
                share = ((rate.skillFactor - 1.0) / (Idle.MAX_SKILL_FACTOR - 1.0)).toFloat(),
            )
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

/**
 * What the shadows earned while you were gone. Banked already — this is a
 * report, not a transaction. The old collect button asked the player to claim
 * essence they had already earned, which is busywork, not a game.
 */
@Composable
private fun AwayWindow(report: AwayReport) {
    SectionHeader("WHILE YOU WERE AWAY")
    SystemWindow(accent = MonarchColors.SovereignGold) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "+%,d".format(report.essence),
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
                color = MonarchColors.SovereignGold,
                maxLines = 1,
                softWrap = false,
            )
            Text(
                "ESSENCE BANKED OVER ${formatAway(report.awayMs)}",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = ChakraPetch,
                letterSpacing = MonarchTracking.InlineLabel,
                color = MonarchColors.InkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Away duration in the coarsest honest unit: "3 h 20 m", "2 days". */
private fun formatAway(ms: Long): String {
    val minutes = ms / 60_000
    val hours = minutes / 60
    val days = hours / 24
    return when {
        days >= 1 -> if (days == 1L) "1 DAY" else "$days DAYS"
        hours >= 1 -> "$hours H ${minutes % 60} M"
        else -> "$minutes M"
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

// Grouping separators follow the reader's locale, stated explicitly so the
// format never silently depends on the JVM default.
private fun formatEssence(value: Long): String = String.format(Locale.getDefault(), "%,d", value)

/** Display name per reward type — `Reward` has no shared name property. */
private fun rewardName(reward: Reward): String = when (reward) {
    is Reward.Shadows -> "${reward.count} Shadows"
    is Reward.Relic -> reward.name
    is Reward.CrestFrame -> reward.name
}

/**
 * A factor row with a share bar beneath it. The bar fills from 0 to the
 * factor's share over 600ms on first composition — a gentle establish move,
 * not a flash; with animations disabled it simply starts full.
 */
@Composable
private fun FactorRow(label: String, value: String, share: Float) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        RateRow(label, value)
        // Animatable from 0 (not animateFloatAsState, which starts AT target
        // on first composition and would never animate) — a gentle establish
        // fill, 600ms; with animations disabled it lands at full instantly.
        val fill = remember { Animatable(0f) }
        LaunchedEffect(share) {
            fill.animateTo(share.coerceIn(0.05f, 1f), tween(600, easing = FastOutSlowInEasing))
        }
        InkRail(
            fraction = fill.value,
            height = 4.dp,
            fill = Brush.horizontalGradient(
                listOf(MonarchColors.Emerald, MonarchColors.EmeraldBright),
            ),
            seed = label.hashCode(),
        )
    }
}
/**
 * CREST COLLECTION: the whole catalogue as a horizontal rail of large plates,
 * so the per-frame art is actually visible. A vertical list of ten 40.dp
 * swatches showed none of it and read as filler.
 */
@Composable
private fun CrestCollection(
    owned: Set<String>,
    equipped: String?,
    onEquip: (String?) -> Unit,
) {
    SectionHeader("CREST COLLECTION")
    Text(
        "${owned.size} OF ${Gacha.CREST_FRAMES.size} CRESTS DRAWN",
        style = MaterialTheme.typography.labelSmall,
        fontFamily = ChakraPetch,
        letterSpacing = MonarchTracking.InlineLabel,
        color = MonarchColors.InkMuted,
        modifier = Modifier.padding(bottom = 10.dp),
    )
    CrestRail(owned = owned, equipped = equipped, onEquip = onEquip)
}

/**
 * A small swatch using the avatar's frame treatment. Locked frames pass
 * `treatment = null`: a dim plate still shows the SHAPE of what could drop,
 * without revealing its colours.
 */
@Composable
private fun CrestSwatch(treatment: CrestFrameTreatment?, locked: Boolean = false) {
    val t = treatment
    Box(
        Modifier
            .size(40.dp)
            .background(
                if (t == null) {
                    Brush.verticalGradient(listOf(MonarchColors.Vault, MonarchColors.Abyss))
                } else {
                    Brush.verticalGradient(listOf(t.plateTop, t.plateBottom))
                },
                MaterialTheme.shapes.small,
            )
            .border(
                if (t == null) 1.dp else t.frameWidth,
                if (t == null) MonarchColors.Rune else t.frameColor,
                MaterialTheme.shapes.small,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Optional outer ring, inset like the avatar's, for double-ring frames.
        t?.outerRing?.let { ring ->
            Box(
                Modifier
                    .size(46.dp)
                    .border(1.5.dp, ring, MaterialTheme.shapes.small),
            )
        }
        Text(
            if (t == null) "\u25C7" else "\u25C6",
            style = MaterialTheme.typography.titleSmall,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = if (t == null) MonarchColors.InkMuted else t.initialColor,
        )
    }
}

/**
 * RELIC VAULT: every relic a draw produced. The rate only uses the strongest,
 * so the top row is marked ACTIVE and the rest read as history — without this
 * a relic draw left nothing to look at but a bare multiplier on the army panel.
 */
@Composable
private fun RelicVault(relics: List<RelicHolding>) {
    SectionHeader("RELIC VAULT")
    SystemWindow(accent = MonarchColors.Emerald) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "A relic is a permanent boost to your idle rate, won from a " +
                    "draw. Every relic you own counts: the strongest at full " +
                    "weight, the second at a half, the third at a third, and " +
                    "so on down the vault.",
                style = MaterialTheme.typography.bodySmall,
                color = MonarchColors.InkMuted,
            )
            if (relics.isNotEmpty()) {
                Text(
                    "VAULT TOTAL \u00d7%.2f".format(
                        Relics.effectiveMultiplier(relics.map { it.multiplier }),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = ChakraPetch,
                    letterSpacing = MonarchTracking.InlineLabel,
                    color = MonarchColors.EmeraldBright,
                )
            }
            if (relics.isEmpty()) {
                Text(
                    "Nothing drawn yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MonarchColors.InkMuted,
                )
            } else {
                // Only the relics that actually move the rate are listed. An
                // uncapped vault ran to a hundred rows and buried every section
                // below it; the tail contributes fractions of a percent each.
                relics.take(VAULT_ROWS).forEachIndexed { index, relic ->
                    val isActive = index == 0
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Procedural sigil: the relic's own name is the seed,
                        // so a continuous multiplier still gets unique art.
                        RelicSigil(
                            name = relic.name,
                            accent = if (isActive) MonarchColors.EmeraldBright else MonarchColors.InkMuted,
                            spin = isActive,
                            modifier = Modifier.size(if (isActive) 64.dp else 44.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                relic.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontFamily = ChakraPetch,
                                fontWeight = FontWeight.SemiBold,
                                color = MonarchColors.Ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                // Its real contribution, not a flat label: the
                                // rank sets the weight, so show what it adds.
                                "+%.2f RATE \u00b7 %s".format(
                                    (relic.multiplier - 1.0) * Relics.weightAt(index),
                                    if (isActive) "FULL WEIGHT" else "%d%% WEIGHT".format(
                                        (Relics.weightAt(index) * 100).toInt(),
                                    ),
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = ChakraPetch,
                                letterSpacing = MonarchTracking.InlineLabel,
                                color = if (isActive) MonarchColors.EmeraldBright else MonarchColors.InkMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            "×%.2f".format(relic.multiplier),
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = ChakraPetch,
                            fontWeight = FontWeight.Bold,
                            color = if (isActive) MonarchColors.EmeraldBright else MonarchColors.InkMuted,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
                if (relics.size > VAULT_ROWS) {
                    Text(
                        "+${relics.size - VAULT_ROWS} MORE HELD \u00b7 COUNTED IN THE VAULT TOTAL",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = ChakraPetch,
                        letterSpacing = MonarchTracking.InlineLabel,
                        color = MonarchColors.InkMuted,
                    )
                }
            }
        }
    }
}


/**
 * What a draw can actually pay, read straight off [Gacha.DROP_TABLE] — the same
 * table the roller uses, so the odds shown can never drift from the odds rolled.
 */
@Composable
private fun OddsTable() {
    Column(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Gacha.DROP_TABLE.forEach { odds ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        odds.rarity.name.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = ChakraPetch,
                        letterSpacing = MonarchTracking.InlineLabel,
                        color = rarityAccent(odds.rarity),
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        formatChance(odds.chance),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = ChakraPetch,
                        fontWeight = FontWeight.Bold,
                        color = rarityAccent(odds.rarity),
                    )
                }
                if (odds.shadowChance > 0.0) {
                    OddsLine(
                        "${odds.shadowsLow}\u2013${odds.shadowsHigh} shadows",
                        formatChance(odds.shadowChance),
                    )
                }
                if (odds.relicChance > 0.0) {
                    OddsLine(
                        "Relic \u00d7%.2f\u2013\u00d7%.2f".format(odds.relicLow, odds.relicHigh),
                        formatChance(odds.relicChance),
                    )
                }
                if (odds.frameChance > 0.0) {
                    OddsLine("Crest frame", formatChance(odds.frameChance))
                }
            }
        }
        Text(
            "Rarity odds are per draw. The second figure is the split inside " +
                "that rarity. Shadows join the army, a relic lifts your rate " +
                "for good, a crest is worn on your hunter.",
            style = MaterialTheme.typography.bodySmall,
            color = MonarchColors.InkMuted,
        )
    }
}

@Composable
private fun OddsLine(label: String, chance: String) {
    Row(Modifier.fillMaxWidth().padding(start = 10.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MonarchColors.InkMuted,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            chance,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = ChakraPetch,
            color = MonarchColors.Ink,
        )
    }
}

/** Whole percents where possible: "1%" reads better than "1.0%". */
private fun formatChance(fraction: Double): String {
    val pct = fraction * 100.0
    return if (pct % 1.0 == 0.0) "${pct.toInt()}%" else "%.1f%%".format(pct)
}

private fun rarityAccent(rarity: RewardRarity): Color = when (rarity) {
    RewardRarity.Common -> MonarchColors.InkMuted
    RewardRarity.Rare -> MonarchColors.SystemGreen
    RewardRarity.Epic -> MonarchColors.Emerald
    RewardRarity.Sovereign -> MonarchColors.SovereignGold
}

/** Relics listed before the tail is summarised: past this each adds < 1%. */
private const val VAULT_ROWS = 12
