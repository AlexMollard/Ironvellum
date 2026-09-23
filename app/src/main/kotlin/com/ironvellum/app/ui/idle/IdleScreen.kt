package com.ironvellum.app.ui.idle
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import com.ironvellum.app.domain.Gacha
import kotlin.random.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.geometry.Size
import androidx.compose.animation.core.Animatable
import com.ironvellum.app.ui.social.CrestFrameTreatment
import com.ironvellum.app.ui.social.crestFrameTreatment
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import com.ironvellum.app.ui.theme.InkCircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.res.painterResource
import com.ironvellum.app.R
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
import com.ironvellum.app.data.IdleInputs
import com.ironvellum.app.data.IdleSnapshot
import com.ironvellum.app.data.Repository
import com.ironvellum.app.data.RelicHolding
import com.ironvellum.app.domain.Idle
import com.ironvellum.app.domain.IdleRate
import com.ironvellum.app.domain.IdleState
import com.ironvellum.app.domain.Reward
import com.ironvellum.app.domain.RewardRarity
import com.ironvellum.app.domain.RollResult
import com.ironvellum.app.ui.components.Achievement
import com.ironvellum.app.ui.components.AchievementOverlay
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.components.SectionHeader
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.ironvellumRepository
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.components.InkRail
import com.ironvellum.app.ui.theme.inkBorder
import com.ironvellum.app.ui.theme.inkDot
import com.ironvellum.app.ui.theme.inkStroke
import com.ironvellum.app.ui.theme.inkArc
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.IronvellumTracking
import com.ironvellum.app.ui.components.RelicSigil
import com.ironvellum.app.ui.components.CrestRail
import com.ironvellum.app.ui.components.MusterBackdrop
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
import com.ironvellum.app.domain.Relics
import java.util.Locale


/** What the figures earned while the app was closed, shown once on arrival. */
data class AwayReport(val essence: Long, val awayMs: Long)

/** Everything the Muster screen renders, resolved from the repository. */
data class IdleUi(
    val snapshot: IdleSnapshot? = null,
    val inputs: IdleInputs? = null,
    /** Banked, unspent inscriptions — earned by levelling, spent here. */
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
     * the vault is independent of the roll snapshot anyway.
     */
    val relics: StateFlow<List<RelicHolding>> = repo.observeRelics()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _away = MutableStateFlow<AwayReport?>(null)


    /** The away haul, banked automatically — there is nothing to claim. */
    val away: StateFlow<AwayReport?> = _away.asStateFlow()

    init {
        // Essence banks itself the moment you arrive. Making a player press a
        // button for money they already earned is busywork; what they actually
        // want is to see what the figures did while they were gone.
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

    /** Transactional on the repo side — payout and inscription spend land together. */
    fun inscribeFigure(onInscribed: (RollResult?) -> Unit) {
        viewModelScope.launch {
            onInscribed(repo.spendRoll(seed = System.nanoTime()))
        }
    }
}

@Composable
fun IdleScreen(
    viewModel: IdleViewModel =
        viewModel(factory = viewModelFactory { initializer { IdleViewModel(ironvellumRepository()) } }),
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
    // The reveal for a spent inscription; null once the overlay finishes so it never re-shows.
    var inscriptionResult by remember { mutableStateOf<RollResult?>(null) }

    val snapshot = ui.snapshot
    val inputs = ui.inputs

    Box(Modifier.fillMaxSize()) {
        // Atmosphere behind everything: gradient wash, a breathing glow under
        // the hero, and an ambient field that thickens with the roll. The page
        // was flat black with one moving number — this is what gives it depth.
        MusterBackdrop(
            figures = snapshot?.state?.figures ?: 0,
            active = (snapshot?.rate?.perHour ?: 0.0) > 0.0,
            modifier = Modifier.matchParentSize(),
        )
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        SectionHeader("THE MUSTER ROLL")

        if (snapshot == null || inputs == null) {
            // Brief empty frame while the flows warm up; never fake numbers.
            InkPanel {
                Text(
                    "The figures are assembling...",
                    fontFamily = ChakraPetch,
                    color = IronvellumColors.InkMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            // Exact, unrounded accrual so both the hero total and the pending
            // figure climb every frame instead of jumping on a poll.
            val pendingExact = Idle.accruedExact(snapshot.state, snapshot.rate, nowMs)
            RollWindow(snapshot.state, snapshot.rate, pendingExact)
            SectionHeader("WHY THE RATE")
            RateWindow(snapshot.rate, inputs)
            // Inscriptions and the collection are ALWAYS visible. Hiding them until
            // the first level-up made the whole feature undiscoverable — a
            // collection screen should show the vault, not an empty wall.
            SectionHeader("INSCRIBE")
            DrawWindow(
                rolls = ui.rolls,
                onInscribe = { viewModel.inscribeFigure { result -> if (result != null) inscriptionResult = result } },
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
    inscriptionResult?.let { result ->
        AchievementOverlay(
            items = listOf(achievementFor(result)),
            onDone = { inscriptionResult = null },
        )
    }
}

/**
 * House-voice copy per rarity tier — the same inscription, four different
 * weights of silence. Accent uses only sanctioned palette tokens.
 */
private fun achievementFor(result: RollResult): Achievement {
    val (tagline, accent) = when (result.rarity) {
        RewardRarity.Common -> "A WHISPER IN THE DARK" to IronvellumColors.InkMuted
        RewardRarity.Rare -> "THE DARK STIRS" to IronvellumColors.SystemGreen
        RewardRarity.Epic -> "THE DARK BENDS" to IronvellumColors.Emerald
        RewardRarity.Masterwork -> "THE LEDGER ANSWERS" to IronvellumColors.SovereignGold
    }
    val notes = when (val reward = result.reward) {
        is Reward.Figures -> listOf("ROLL +${reward.count} FIGURES")
        is Reward.Relic -> listOf("RATE MULTIPLIER ×%.2f".format(reward.multiplier))
        is Reward.CrestFrame -> listOf("CREST FRAME UNLOCKED", "EQUIP IT ON YOUR LIFTER IDENTITY")
    }
    return Achievement(
        banner = "INSCRIBED",
        tagline = tagline,
        name = rewardName(result.reward),
        subtitle = result.rarity.name.uppercase(),
        notes = notes,
        accent = accent,
        // Only relics. A figures payout is a number, not an object, and a
        // crest already has its own plate treatment in the collection — a
        // generic sigil there would misrepresent the frame that was won.
        sigilSeed = (result.reward as? Reward.Relic)?.name,
    )
}

/**
 * The inscription control. Rendered only while rolls are banked — at zero it is
 * absent entirely, never a disabled stub — so levelling is the only way in.
 */
@Composable
private fun DrawWindow(rolls: Int, onInscribe: () -> Unit) {
    var oddsOpen by remember { mutableStateOf(false) }
    InkPanel(accent = IronvellumColors.SovereignGold) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (rolls > 0) "$rolls INSCRIPTION${if (rolls == 1) "" else "S"} WAITING" else "NO INSCRIPTIONS BANKED",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = ChakraPetch,
                    letterSpacing = IronvellumTracking.InlineLabel,
                    color = if (rolls > 0) IronvellumColors.SovereignGold else IronvellumColors.InkMuted,
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = if (oddsOpen) "Hide drop rates" else "Show drop rates",
                    tint = if (oddsOpen) IronvellumColors.SovereignGold else IronvellumColors.InkMuted,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { oddsOpen = !oddsOpen },
                )
            }
            if (oddsOpen) {
                OddsTable()
            }
            // An inscription pays figures, a relic OR a crest — a collection screen
            // showing only frames made a relic roll look like a lost crest.
            Text(
                "Every rank-up earns one inscription. An inscription yields " +
                    "figures for the roll, a relic that lifts your rate, or a " +
                    "crest frame worn on your lifter.",
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
            )
            if (rolls > 0) {
                IronvellumButton(
                    label = "Inscribe figure ($rolls)",
                    onClick = onInscribe,
                    enabled = true,
                    gold = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                // No dead button: state the path to the next inscription instead.
                Text(
                    "RANK UP TO EARN THE NEXT INSCRIPTION",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = ChakraPetch,
                    letterSpacing = IronvellumTracking.InlineLabel,
                    color = IronvellumColors.InkMuted,
                )
            }
        }
    }
}

/**
 * The roll at a glance. The headline total is banked essence PLUS what is
 * accruing right now, so the number visibly climbs while you watch it — a
 * static total was the main reason this screen felt dead.
 */
@Composable
private fun RollWindow(state: IdleState, rate: IdleRate, pendingExact: Double) {
    InkPanel(accent = IronvellumColors.Emerald) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "ESSENCE",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = ChakraPetch,
                    letterSpacing = IronvellumTracking.InlineLabel,
                    color = IronvellumColors.InkMuted,
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
                    // Drifting figure motes BEHIND the hero number: the roll is
                    // visibly present, not just a number on a panel.
                    FigureMotes(figures = state.figures, modifier = Modifier.matchParentSize())
                    Text(
                        liveEssence(state.essence, pendingExact),
                        fontFamily = ChakraPetch,
                        fontWeight = FontWeight.Bold,
                        fontSize = 44.sp,
                        color = IronvellumColors.Ink,
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
                RollStat(
                    label = "FIGURES",
                    value = state.figures.toString(),
                    modifier = Modifier.weight(1f),
                )
                RollStat(
                    label = "RELIC",
                    value = "×${"%.2f".format(state.relicMultiplier)}",
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Figure motes: a handful of dots drifting behind the essence hero. The
 * count scales with the roll (minOf(12, figures)); nothing at zero figures.
 * One shared infinite phase (linear, 12s loop) drives every mote; per-mote
 * offsets come from a SEEDED Random remembered across recompositions, so the
 * drift is deterministic per composition and never jumps.
 */
@Composable
private fun FigureMotes(figures: Int, modifier: Modifier = Modifier) {
    if (figures <= 0) return
    val count = minOf(12, figures)
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
                color = IronvellumColors.EmeraldBright.copy(alpha = alpha),
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
 * The RATE dial: an arc whose sweep is the roll's rate against the FULL
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
    // the one input the lifter actually moves. Measuring the live rate against
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
            // Brushed sweeps, same reason as the step gauge: a machined ring
            // is a straight line bent into a circle.
            val gaugeCentre = Offset(size.width / 2f, size.height / 2f)
            val gaugeRadius = minOf(arcSize.width, arcSize.height) / 2f
            // Rune, not Vault: against this panel a Vault track was invisible,
            // so the sweep read as a stroke floating in space with no scale.
            inkArc(gaugeCentre, gaugeRadius, start, span, IronvellumColors.Rune, stroke, seed = 81, taperEnds = false)
            inkArc(gaugeCentre, gaugeRadius, start, span * sweep.value, IronvellumColors.EmeraldBright, stroke, seed = 83)
            // Quarter ticks on the track: a gauge with no scale can't be read
            // even once the fill is legible.
            // Same centre and radius as the arcs above: deriving these from
            // width alone agreed only because the dial is square, and would
            // have floated the ticks off the ring the moment it wasn't.
            val cx = gaugeCentre.x
            val cy = gaugeCentre.y
            val radius = gaugeRadius
            for (i in 0..4) {
                val a = ((start + span * i / 4f) * PI / 180.0).toFloat()
                val outer = radius + stroke * 0.62f
                val innerR = radius + stroke * 0.18f
                inkStroke(
                    from = Offset(cx + cos(a) * innerR, cy + sin(a) * innerR),
                    to = Offset(cx + cos(a) * outer, cy + sin(a) * outer),
                    color = IronvellumColors.Rune,
                    widthPx = stroke * 0.18f,
                    seed = 89,
                )
            }
            // The needle tip: an unambiguous marker for where the value sits.
            // Drawn in the sweep's own colour, not ringed in near-white ink -
            // a pale ring around a coloured centre is the exact shape of a
            // stock slider handle, and it was the one machined-looking object
            // left on a hand-drawn dial.
            val tip = ((start + span * sweep.value) * PI / 180.0).toFloat()
            val tipAt = Offset(cx + cos(tip) * radius, cy + sin(tip) * radius)
            inkDot(center = tipAt, radius = stroke * 0.52f, color = IronvellumColors.EmeraldBright, seed = 91)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "RATE",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = ChakraPetch,
                letterSpacing = IronvellumTracking.InlineLabel,
                color = IronvellumColors.InkMuted,
            )
            Text(
                "${"%.1f".format(perHour)}",
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = IronvellumColors.EmeraldBright,
                maxLines = 1,
                softWrap = false,
            )
            Text(
                "PER HOUR",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = ChakraPetch,
                letterSpacing = IronvellumTracking.InlineLabel,
                color = IronvellumColors.InkMuted,
            )
        }
    }
}


/**
 * Slow breathing dot: proof the roll is working right now. Deliberately a
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
                .background(IronvellumColors.EmeraldBright, InkCircleShape(7)),
        )
        Text(
            "WORKING",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            letterSpacing = IronvellumTracking.InlineLabel,
            color = IronvellumColors.EmeraldBright,
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
private fun RollStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(
                Brush.verticalGradient(listOf(IronvellumColors.VaultHigh, IronvellumColors.Vault)),
                MaterialTheme.shapes.small,
            )
            // Clipped to an ink shape but never inked: without the bleed-plus-firm
            // border pass this tile read flat beside every other inked surface.
            .inkBorder(IronvellumColors.Rune, MaterialTheme.shapes.small, 1.dp)
            .padding(12.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            letterSpacing = IronvellumTracking.InlineLabel,
            color = IronvellumColors.InkMuted,
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = IronvellumColors.Emerald,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * WHY the rate is what it is. The prose and the two factor multipliers stay
 * visible; the raw inputs they are computed from sit one tap away.
 */
@Composable
private fun RateWindow(rate: IdleRate, inputs: IdleInputs) {
    val atFloor = inputs.sessionsLast7d == 0 && inputs.volumeLast7d == 0.0
    InkPanel(accent = if (atFloor) IronvellumColors.SovereignGold else IronvellumColors.Emerald) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (atFloor) {
                Text(
                    "The figures have heard nothing from you. Your rate has decayed to its floor — return to training and they will rise again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = IronvellumColors.Ink,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Text(
                    "Your training drives this rate. Idle time only collects at the pace your figures have earned.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = IronvellumColors.InkMuted,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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
            // The four raw inputs (sessions, volume, skills, streak) used to
            // sit as a six-row dump — a diagnostics panel, not a game screen.
            // They now live one tap behind this disclosure.
            var inputsOpen by rememberSaveable { mutableStateOf(false) }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { inputsOpen = !inputsOpen }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (inputsOpen) "▾ WHAT FEEDS IT" else "▸ WHAT FEEDS IT",
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = ChakraPetch,
                    fontWeight = FontWeight.Bold,
                    color = IronvellumColors.InkMuted,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (inputsOpen) {
                RateRow("SESSIONS · 7 DAYS", "${inputs.sessionsLast7d}")
                RateRow("VOLUME · 7 DAYS", "${"%,.0f".format(inputs.volumeLast7d)} KG")
                RateRow("SKILLS UNLOCKED", "${inputs.skillsUnlocked}")
                RateRow("STREAK", "${inputs.streakDays} D")
            }
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
            letterSpacing = IronvellumTracking.InlineLabel,
            color = IronvellumColors.InkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = MaterialTheme.typography.labelLarge,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = IronvellumColors.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * What the figures earned while you were gone. Banked already — this is a
 * report, not a transaction. The old collect button asked the player to claim
 * essence they had already earned, which is busywork, not a game.
 */
@Composable
private fun AwayWindow(report: AwayReport) {
    SectionHeader("WHILE YOU WERE AWAY")
    InkPanel(accent = IronvellumColors.SovereignGold) {
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
                color = IronvellumColors.SovereignGold,
                maxLines = 1,
                softWrap = false,
            )
            Text(
                "ESSENCE BANKED OVER ${formatAway(report.awayMs)}",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = ChakraPetch,
                letterSpacing = IronvellumTracking.InlineLabel,
                color = IronvellumColors.InkMuted,
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
        // Banking twice in a row read as "BANKED OVER 0 M".
        minutes < 1 -> "MOMENTS"
        else -> "$minutes M"
    }
}

/** The offline cap, stated plainly. No player should expect three days to pay out. */
@Composable
private fun CapWindow() {
    InkPanel(accent = IronvellumColors.Bracket) {
        Text(
            "The figures hold full strength for ${Idle.FULL_RATE_HOURS.toInt()} hours, " +
                "then tire over the next ${Idle.TAPER_WINDOW_HOURS.toInt()} and labour on at a tenth. " +
                "One absence never pays more than ${(Idle.MAX_EFFECTIVE_HOURS / 24).toInt()} full days of work.",
            style = MaterialTheme.typography.bodyMedium,
            color = IronvellumColors.InkMuted,
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
    is Reward.Figures -> "${reward.count} Figures"
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
                listOf(IronvellumColors.Emerald, IronvellumColors.EmeraldBright),
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
        "${owned.size} OF ${Gacha.CREST_FRAMES.size} CRESTS INSCRIBED",
        style = MaterialTheme.typography.labelSmall,
        fontFamily = ChakraPetch,
        letterSpacing = IronvellumTracking.InlineLabel,
        color = IronvellumColors.InkMuted,
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
                    Brush.verticalGradient(listOf(IronvellumColors.Vault, IronvellumColors.Abyss))
                } else {
                    Brush.verticalGradient(listOf(t.plateTop, t.plateBottom))
                },
                MaterialTheme.shapes.small,
            )
            .inkBorder(if (t == null) IronvellumColors.Rune else t.frameColor, MaterialTheme.shapes.small, if (t == null) 1.dp else t.frameWidth),
        contentAlignment = Alignment.Center,
    ) {
        // Optional outer ring, inset like the avatar's, for double-ring frames.
        t?.outerRing?.let { ring ->
            Box(
                Modifier
                    .size(46.dp)
                    .inkBorder(ring, MaterialTheme.shapes.small, 1.5.dp),
            )
        }
        Text(
            if (t == null) "\u25C7" else "\u25C6",
            style = MaterialTheme.typography.titleSmall,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = if (t == null) IronvellumColors.InkMuted else t.initialColor,
        )
    }
}

/**
 * RELIC VAULT: every relic an inscription produced. The rate only uses the
 * strongest, so the top row is marked ACTIVE and the rest read as history —
 * without this a relic inscription left nothing to look at but a bare
 * multiplier on the roll panel.
 */
@Composable
private fun RelicVault(relics: List<RelicHolding>) {
    SectionHeader("RELIC VAULT")
    InkPanel(accent = IronvellumColors.Emerald) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "A relic is a permanent boost to your idle rate, won from an " +
                    "inscription. Every relic you own counts: the strongest at full " +
                    "weight, the second at a half, the third at a third, and " +
                    "so on down the vault.",
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
            )
            if (relics.isNotEmpty()) {
                Text(
                    "VAULT TOTAL \u00d7%.2f".format(
                        Relics.effectiveMultiplier(relics.map { it.multiplier }),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = ChakraPetch,
                    letterSpacing = IronvellumTracking.InlineLabel,
                    color = IronvellumColors.EmeraldBright,
                )
            }
            if (relics.isEmpty()) {
                Image(
                    painter = painterResource(R.drawable.art_empty_muster),
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(132.dp)
                        .alpha(0.55f),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Nothing inscribed yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
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
                            accent = if (isActive) IronvellumColors.EmeraldBright else IronvellumColors.InkMuted,
                            spin = isActive,
                            modifier = Modifier.size(if (isActive) 64.dp else 44.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                relic.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontFamily = ChakraPetch,
                                fontWeight = FontWeight.SemiBold,
                                color = IronvellumColors.Ink,
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
                                letterSpacing = IronvellumTracking.InlineLabel,
                                color = if (isActive) IronvellumColors.EmeraldBright else IronvellumColors.InkMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            "×%.2f".format(relic.multiplier),
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = ChakraPetch,
                            fontWeight = FontWeight.Bold,
                            color = if (isActive) IronvellumColors.EmeraldBright else IronvellumColors.InkMuted,
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
                        letterSpacing = IronvellumTracking.InlineLabel,
                        color = IronvellumColors.InkMuted,
                    )
                }
            }
        }
    }
}


/**
 * What an inscription can actually pay, read straight off [Gacha.DROP_TABLE] —
 * the same table the roller uses, so the odds shown can never drift from the
 * odds rolled.
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
                        letterSpacing = IronvellumTracking.InlineLabel,
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
                if (odds.figureChance > 0.0) {
                    OddsLine(
                        "${odds.figuresLow}\u2013${odds.figuresHigh} figures",
                        formatChance(odds.figureChance),
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
            "Rarity odds are per inscription. The second figure is the split inside " +
                "that rarity. Figures join the roll, a relic lifts your rate " +
                "for good, a crest is worn on your lifter.",
            style = MaterialTheme.typography.bodySmall,
            color = IronvellumColors.InkMuted,
        )
        // Pity changes the real odds, so it is stated here. The table above
        // would otherwise be quietly wrong about what the roller does.
        Text(
            "After ${Gacha.PITY_AFTER} inscriptions that yield only figures, the next one " +
                "is guaranteed a relic or a crest.",
            style = MaterialTheme.typography.bodySmall,
            color = IronvellumColors.SovereignGold,
        )
    }
}

@Composable
private fun OddsLine(label: String, chance: String) {
    Row(Modifier.fillMaxWidth().padding(start = 10.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = IronvellumColors.InkMuted,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            chance,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = ChakraPetch,
            color = IronvellumColors.Ink,
        )
    }
}

/** Whole percents where possible: "1%" reads better than "1.0%". */
private fun formatChance(fraction: Double): String {
    val pct = fraction * 100.0
    return if (pct % 1.0 == 0.0) "${pct.toInt()}%" else "%.1f%%".format(pct)
}

private fun rarityAccent(rarity: RewardRarity): Color = when (rarity) {
    RewardRarity.Common -> IronvellumColors.InkMuted
    RewardRarity.Rare -> IronvellumColors.SystemGreen
    RewardRarity.Epic -> IronvellumColors.Emerald
    RewardRarity.Masterwork -> IronvellumColors.SovereignGold
}

/** Relics listed before the tail is summarised: past this each adds < 1%. */
private const val VAULT_ROWS = 12
