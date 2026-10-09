package com.ironvellum.app.ui.idle

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Diamond
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ironvellum.app.IronvellumApp
import com.ironvellum.app.data.FirstRunPrefs
import com.ironvellum.app.data.IdleInputs
import com.ironvellum.app.data.IdleSnapshot
import com.ironvellum.app.data.Repository
import com.ironvellum.app.data.cloud.Cloud
import com.ironvellum.app.domain.Circle
import com.ironvellum.app.domain.CollectionTab
import com.ironvellum.app.domain.DrawChange
import com.ironvellum.app.domain.Gacha
import com.ironvellum.app.domain.CrestEffects
import com.ironvellum.app.domain.HouseEffects
import com.ironvellum.app.domain.Idle
import com.ironvellum.app.domain.FirstRun
import com.ironvellum.app.domain.Reward
import com.ironvellum.app.domain.RewardRarity
import com.ironvellum.app.domain.VaultState
import com.ironvellum.app.domain.Veil
import com.ironvellum.app.domain.VeilDraw
import com.ironvellum.app.domain.VeilLanding
import com.ironvellum.app.domain.VeilSample
import com.ironvellum.app.domain.Xp
import com.ironvellum.app.domain.fmt
import com.ironvellum.app.domain.hoursToGather
import com.ironvellum.app.ui.components.CelebrationDock
import com.ironvellum.app.ui.components.CrestPlate
import com.ironvellum.app.ui.components.HouseEmblem
import com.ironvellum.app.ui.components.HouseRelicSigil
import com.ironvellum.app.ui.components.InkDivider
import com.ironvellum.app.ui.components.InkIconButton
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.components.InkRail
import com.ironvellum.app.ui.components.InscribedDraw
import com.ironvellum.app.ui.components.InscribedOverlay
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.components.IronvellumDialog
import com.ironvellum.app.ui.components.PushedHeader
import com.ironvellum.app.ui.components.SectionHeader
import com.ironvellum.app.ui.components.inscribedDraw
import com.ironvellum.app.ui.components.plural
import com.ironvellum.app.ui.components.rarityWord
import com.ironvellum.app.ui.dashboard.EssenceFigure
import com.ironvellum.app.ui.dashboard.VeilMotes
import com.ironvellum.app.ui.dashboard.rememberTodayMotion
import com.ironvellum.app.ui.dashboard.rememberVeilNow
import com.ironvellum.app.ui.dashboard.rememberVeilPhase
import com.ironvellum.app.ui.dashboard.veilMotes
import com.ironvellum.app.ui.dashboard.veilStrength
import com.ironvellum.app.ui.ironvellumRepository
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.IronvellumTracking
import com.ironvellum.app.ui.theme.RarityTint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.roundToInt

/** What the figures earned while the app was closed, shown once on arrival. */
data class AwayReport(val essence: Long, val awayMs: Long)

/** Everything the Veil screen renders, resolved from the repository. */
data class IdleUi(
    val snapshot: IdleSnapshot? = null,
    val inputs: IdleInputs? = null,
    /** Banked, unspent inscriptions — earned by levelling, spent here. */
    val rolls: Int = 0,
    /** Owned cosmetic crest frames (frameId set) and the one currently worn. */
    val ownedFrames: Set<String> = emptySet(),
    val equippedFrame: String? = null,
)

class IdleViewModel(private val repo: Repository, private val appContext: Context) : ViewModel() {

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
     * The vault rides its own flow: `combine` tops out at five typed sources and the vault is
     * independent of the roll snapshot anyway. It holds the strongest relic, the houses and what they add.
     */
    val vault: StateFlow<VaultState?> = repo.observeVault()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The lifter level, which says how far off the next milestone crest is. */
    val level: StateFlow<Int> = repo.observeProfile()
        .map { Xp.levelFor(it?.totalXp ?: 0L) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1)

    /** What pity will do on the next draw, for the buy sheet. */
    val pity: StateFlow<Gacha.Pity> = repo.observePity()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Gacha.Pity())

    private val _away = MutableStateFlow<AwayReport?>(null)

    /** The away haul, banked automatically — there is nothing to claim. */
    val away: StateFlow<AwayReport?> = _away.asStateFlow()

    private val _intro = MutableStateFlow(false)

    /** True while the Veil's one-time introduction is on screen: the first visit of a lifter who has seen none of it. */
    val intro: StateFlow<Boolean> = _intro.asStateFlow()

    /** Marks the introduction seen, for good. */
    fun introSeen() {
        _intro.value = false
        FirstRunPrefs.setVeilIntroSeen(appContext)
    }

    init {
        // Essence banks itself the moment you arrive. Making a player press a
        // button for money they already earned is busywork; what they actually
        // want is to see what the figures did while they were gone.
        viewModelScope.launch {
            val state = repo.observeIdle().first()
            // Decided before the collect below, which banks essence and would make a first visit look like
            // a returning lifter's. Lifetime essence, relics and crests are what a lifter who has been here
            // before has; the answer is kept either way.
            if (!FirstRunPrefs.veilIntroSeen(appContext)) {
                val relics = repo.vaultNow().ownedCount
                val crests = repo.observeOwnedFrames().first().size
                if (FirstRun.veilIntroDue(seen = false, state.lifetimeEssence, relics, crests)) _intro.value = true else introSeen()
            }
            val awayMs = (System.currentTimeMillis() - state.lastCollectedAtMs).coerceAtLeast(0L)
            val banked = repo.collectIdle(System.currentTimeMillis())
            if (banked > 0) _away.value = AwayReport(banked, awayMs)
        }
    }

    /** Extra inscriptions bought so far; sets the price of the next. */
    val offerings: StateFlow<Int> = repo.observeOfferingsMade()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Cosmetic only — the repository rejects equipping a frame not owned. */
    fun equipFrame(frameId: String?) {
        viewModelScope.launch { repo.equipFrame(frameId) }
    }

    private val _reveal = MutableStateFlow<InscribedDraw?>(null)

    /** The reveal on screen, or null. A second draw replaces it; it ends in [done]. */
    internal val reveal: StateFlow<InscribedDraw?> = _reveal.asStateFlow()

    private val _landing = MutableStateFlow<VeilLanding?>(null)

    /**
     * What the Veil says after a reveal is done. It lives as long as this screen's back-stack entry, so it
     * survives a visit to the collection and is gone the next time the Veil is opened.
     */
    val landing: StateFlow<VeilLanding?> = _landing.asStateFlow()

    private val run = mutableListOf<DrawChange>()
    private var serial = 0
    private var drawing = false

    private suspend fun sample(): VeilSample {
        val snapshot = repo.idleSnapshotOnce()
        return VeilSample(snapshot?.rate?.perHour ?: 0.0, snapshot?.state?.figures ?: 0, repo.vaultNow())
    }

    /**
     * Spends one banked inscription. Transactional on the repo side: the payout and the spend land
     * together. The Veil is read before and after so the reveal says what the draw did.
     */
    private suspend fun draw() {
        val before = sample()
        val result = repo.spendRoll(seed = System.nanoTime()) ?: return
        val after = sample()
        val change = VeilDraw.change(before, after, result, repo.observePity().first())
        val crests = if (result.reward is Reward.CrestFrame) repo.observeOwnedFrames().first().size else null
        run += change
        _reveal.value = inscribedDraw(change, after.vault.takeIf { result.reward is Reward.Relic }, crests, ++serial)
    }

    /** Draws one banked inscription: "Inscribe (N)" and "Inscribe again". */
    fun inscribe() {
        if (drawing) return
        drawing = true
        viewModelScope.launch {
            try {
                draw()
            } finally {
                drawing = false
            }
        }
    }

    /**
     * Buys an inscription and draws it at once, as the sheet promises: the existing buy, then the existing
     * spend, in that order. Essence accrued since the last bank is banked first, because the repository
     * prices the buy against the banked balance and the figure on screen includes what is still gathering.
     * A refused buy (the price moved or the balance fell short) draws nothing, so no banked inscription is spent.
     */
    fun buyAndInscribe() {
        if (drawing) return
        drawing = true
        viewModelScope.launch {
            try {
                repo.collectIdle(System.currentTimeMillis())
                if (repo.buyInscription()) draw()
            } finally {
                drawing = false
            }
        }
    }

    /** Closes the reveal and leaves the landing for the Veil to show. */
    fun done() {
        _landing.value = VeilDraw.landing(run.toList()) ?: _landing.value
        run.clear()
        _reveal.value = null
    }
}

/** How often the live essence is recomputed; two decimals move about once a second at realistic rates. */
private const val TICK_MS = 1_000L

@Composable
fun IdleScreen(
    onBack: () -> Unit,
    onOpenCircle: () -> Unit = {},
    onOpenCollection: (CollectionTab) -> Unit = {},
    onOpenRate: () -> Unit = {},
    appContext: Context = LocalContext.current,
    viewModel: IdleViewModel =
        viewModel(
            factory = viewModelFactory {
                initializer { IdleViewModel(ironvellumRepository(), appContext.applicationContext) }
            },
        ),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val away by viewModel.away.collectAsStateWithLifecycle()
    val vault by viewModel.vault.collectAsStateWithLifecycle()
    val level by viewModel.level.collectAsStateWithLifecycle()
    val offerings by viewModel.offerings.collectAsStateWithLifecycle()
    val pity by viewModel.pity.collectAsStateWithLifecycle()
    val intro by viewModel.intro.collectAsStateWithLifecycle()
    val reveal by viewModel.reveal.collectAsStateWithLifecycle()
    val landing by viewModel.landing.collectAsStateWithLifecycle()
    var buyOpen by rememberSaveable { mutableStateOf(false) }
    var helpOpen by rememberSaveable { mutableStateOf(false) }

    // The ticker and motes move under the same gate as the Veil on Today: resumed, animators on, not a preview.
    val animate = rememberTodayMotion()
    val now = rememberVeilNow(System.currentTimeMillis(), animate, TICK_MS)

    val snapshot = ui.snapshot
    val inputs = ui.inputs
    // The balance as the figure shows it: banked essence plus what is gathering right now.
    val live = snapshot?.let { it.state.essence.toDouble() + Idle.accruedExact(it.state, it.rate, now).coerceAtLeast(0.0) }
    // The price of the worn crest, read from the same effects the rate is built from and the repository charges with.
    val crest = snapshot?.rate?.effects?.crest ?: CrestEffects.NONE
    val cost = Veil.offeringCost(offerings, crest)

    Column(Modifier.fillMaxSize()) {
        // Opened from Today's footer, not a tab, so it carries its own way out.
        PushedHeader(
            "The Veil",
            onBack = { onBack() },
            modifier = Modifier.padding(horizontal = 16.dp).padding(top = 8.dp),
            actions = {
                InkIconButton(onClick = { helpOpen = true }) {
                    Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = "How the Veil works", tint = IronvellumColors.InkMuted)
                }
            },
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            // The circle's pooled week, one line under the header; a tap opens the
            // circle on the ALLIES tab. Refreshes on screen open only.
            CircleBannerLine(onOpenCircle)

            if (snapshot == null || inputs == null || live == null) {
                // Brief empty frame while the flows warm up; never fake numbers.
                Text(
                    "The echoes are gathering…",
                    fontFamily = ChakraPetch,
                    color = IronvellumColors.InkMuted,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 18.dp),
                )
            } else {
                val land = landing
                if (land != null) {
                    LandingLine(land, onView = { onOpenCollection(land.tab) })
                } else {
                    away?.let { AwayLine(it) }
                }
                EssenceBlock(snapshot, live, now, animate, land?.rateDelta ?: 0.0, onOpenRate)
                StrongestRelicCard(vault, snapshot.state.relicMultiplier, animate)
                CollectionCard(
                    vault = vault,
                    ownedFrames = ui.ownedFrames,
                    level = level,
                    landing = land,
                    animate = animate,
                    onOpen = { onOpenCollection(CollectionTab.Relics) },
                )
            }
            Spacer(Modifier.height(16.dp))
        }
        if (snapshot != null && live != null) {
            VeilDock(
                rolls = ui.rolls,
                cost = cost,
                essence = live.toLong(),
                perHour = snapshot.rate.perHour,
                onInscribe = viewModel::inscribe,
                onBuy = { buyOpen = true },
            )
        }
    }
    if (buyOpen && live != null) {
        VeilBuySheet(
            cost = cost,
            essence = live.toLong(),
            nextCost = Veil.offeringCost(offerings + 1, crest),
            crest = crest,
            ownedFrames = ui.ownedFrames,
            pity = pity,
            onConfirm = {
                buyOpen = false
                viewModel.buyAndInscribe()
            },
            onDismiss = { buyOpen = false },
        )
    }
    reveal?.let { draw ->
        InscribedOverlay(
            draw = draw,
            left = ui.rolls,
            onAgain = viewModel::inscribe,
            onDone = viewModel::done,
            wornCrestId = ui.equippedFrame,
            onWearCrest = { viewModel.equipFrame(it) },
        )
    }
    val fullHours = (snapshot?.rate?.effects ?: HouseEffects.NONE).fullStrengthHours.toInt()
    if (helpOpen) VeilIntroSheet(fullHours, onDone = { helpOpen = false })
    if (intro) VeilIntroSheet(fullHours, onDone = viewModel::introSeen)
}

/**
 * How the Veil works, a bottom sheet over the screen: shown once the first time it opens, and again from
 * the "?" in the header. Three short rows, one for each thing the screen shows. There is no scroll in the
 * text slot: the dialog scrolls its own column, and three short rows fit.
 */
@Composable
private fun VeilIntroSheet(fullHours: Int, onDone: () -> Unit) {
    IronvellumDialog(
        onDismissRequest = onDone,
        title = { Text("How the Veil works") },
        text = {
            Column {
                IntroRow(
                    Icons.Outlined.WaterDrop,
                    "Essence",
                    "It gathers on its own while you are away. Full strength for $fullHours hours after you last collected, then it tapers.",
                )
                InkDivider()
                IntroRow(Icons.Outlined.StarBorder, "Inscriptions", "Each level-up earns one. Spend it on echoes, a relic or a crest.")
                InkDivider()
                IntroRow(Icons.Outlined.Diamond, "Relics", "They lift your rate. Only the strongest counts in full.")
            }
        },
        confirmButton = { IronvellumButton(label = "Got it", onClick = onDone) },
    )
}

@Composable
private fun IntroRow(icon: ImageVector, title: String, body: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(icon, contentDescription = null, tint = IronvellumColors.InkMuted, modifier = Modifier.padding(top = 1.dp).size(24.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = IronvellumColors.Ink)
            Text(body, style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

private val EssenceBoxHeight = 72.dp

/**
 * What is accruing: the ticking essence over faint motes (the same ticker and motes the Veil on Today
 * draws), the rate with the way into its breakdown, and the full-strength bar. The total is banked
 * essence PLUS what is gathering right now, so it visibly climbs while you watch. [rateDelta] is what the
 * draws just made did to the rate, as a gold chip beside it; 0 shows none.
 */
@Composable
private fun EssenceBlock(snapshot: IdleSnapshot, total: Double, now: Long, animate: Boolean, rateDelta: Double, onOpenRate: () -> Unit) {
    val state = snapshot.state
    val rate = snapshot.rate
    val phase = rememberVeilPhase(animate)
    val motes = remember { veilMotes(9, 5) }

    SectionHeader("Essence", topPadding = 14.dp)
    Box(Modifier.fillMaxWidth().height(EssenceBoxHeight).clipToBounds()) {
        VeilMotes(motes, phase, animate, Modifier.matchParentSize())
        EssenceFigure(
            essence = total.toLong(),
            size = 36.sp,
            animate = animate,
            // Two decimals at every magnitude: whole units froze the counter, at 20 an hour the integer moves once in three minutes.
            text = "%,.2f".fmt(total),
            modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 6.dp),
        )
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = IronvellumColors.Ink)) { append("%.1f".fmt(rate.perHour)) }
                append(" per hour")
                if (rateDelta != 0.0) {
                    append("  ")
                    withStyle(SpanStyle(color = IronvellumColors.SovereignGold, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)) {
                        append("${if (rateDelta > 0) "+" else "−"}${"%.1f".fmt(kotlin.math.abs(rateDelta))}")
                    }
                }
            },
            style = MaterialTheme.typography.bodyLarge,
            color = IronvellumColors.InkMuted,
            modifier = Modifier.weight(1f),
        )
        Row(
            Modifier
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClickLabel = "Open why this rate", onClick = onOpenRate),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Why this rate", style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.SystemGreen)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = IronvellumColors.SystemGreen, modifier = Modifier.size(18.dp))
        }
    }
    // The window is the houses: 24 h, 26 h with a full Iron house.
    val window = rate.effects.fullStrengthHours
    veilStrength(state.lastCollectedAtMs, now, rate.effects)?.let { strength ->
        InkRail(strength.fraction, height = 4.dp)
        val hours = ceil(strength.fraction * window).toInt()
        Text(
            if (strength.fraction > 0f) "$hours h of ${window.roundToInt()} at full strength, then it tapers" else strength.caption,
            style = MaterialTheme.typography.bodySmall,
            color = IronvellumColors.InkMuted,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/**
 * What the figures earned while you were gone. Banked already, so this is a report, not a transaction;
 * one gold line, because it is earned.
 */
@Composable
private fun AwayLine(report: AwayReport) {
    Text(
        "+%,d essence banked while you were away, %s".fmt(report.essence, formatAway(report.awayMs)),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        color = IronvellumColors.SovereignGold,
        modifier = Modifier.padding(top = 14.dp),
    )
}

/**
 * Where the last draw went: one gold line, "Crown of Iron joined your collection", and the way to it.
 * Shown after a reveal is done, in the place the arrival report holds, and gone the next visit.
 */
@Composable
private fun LandingLine(landing: VeilLanding, onView: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            landing.line,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = IronvellumColors.SovereignGold,
            modifier = Modifier.weight(1f),
        )
        Box(
            Modifier
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClickLabel = "View the collection", onClick = onView)
                .padding(start = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("View", style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.SystemGreen)
        }
    }
}

/** Away duration in the coarsest honest unit: "3 h 20 m", "2 days". */
private fun formatAway(ms: Long): String {
    val minutes = ms / 60_000
    val hours = minutes / 60
    val days = hours / 24
    return when {
        days >= 1 -> if (days == 1L) "1 day" else "$days days"
        hours >= 1 -> "$hours h ${minutes % 60} m"
        // Banking twice in a row read as "banked over 0 m".
        minutes < 1 -> "moments"
        else -> "$minutes m"
    }
}

/**
 * What I own, part one: the strongest relic on its tier's plate with its OWN lift, then, set apart under a
 * rule, what every relic together lift the rate by. The two are different numbers and never share a line.
 */
@Composable
private fun StrongestRelicCard(vault: VaultState?, stacked: Double, animate: Boolean) {
    val strongest = vault?.active
    InkPanel(Modifier.padding(top = 16.dp), contentPadding = PaddingValues(0.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (strongest != null) {
                HouseRelicSigil(strongest.relic.id, strongest.tier, Modifier.size(104.dp), ringed = true, animate = animate)
            } else {
                HouseRelicSigil("iron.crown", RewardRarity.Epic, Modifier.size(104.dp), owned = false)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "STRONGEST RELIC",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = ChakraPetch,
                    color = IronvellumColors.InkMuted,
                    letterSpacing = IronvellumTracking.InlineLabel,
                )
                if (strongest != null) {
                    Text(
                        strongest.relic.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = IronvellumColors.Ink,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = RarityTint.of(strongest.tier), fontWeight = FontWeight.SemiBold)) { append(rarityWord(strongest.tier)) }
                            append(" · ${strongest.relic.house.title}")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = IronvellumColors.InkMuted,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Text(
                        "Lifts your essence rate by ${(((strongest.owned?.multiplier ?: 1.0) - 1.0) * 100).roundToInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = IronvellumColors.Ink,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                } else {
                    Text(
                        "No relic yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = IronvellumColors.Ink,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Text(
                        "Your first inscription is a relic. It lifts your essence rate.",
                        style = MaterialTheme.typography.bodySmall,
                        color = IronvellumColors.InkMuted,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
        val held = vault?.ownedCount ?: 0
        if (held > 1) {
            InkDivider()
            Row(
                Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Your $held relics together", style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted, modifier = Modifier.weight(1f))
                Text("×${"%.2f".fmt(stacked)} rate", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = IronvellumColors.Ink)
            }
        }
    }
}

/**
 * What I own, part two: everything collected behind one door. The four houses as emblems with their
 * counts (a complete house in gold), what the last draw added marked New, and how far the next milestone
 * crest is. The whole card opens the collection.
 */
@Composable
private fun CollectionCard(
    vault: VaultState?,
    ownedFrames: Set<String>,
    level: Int,
    landing: VeilLanding?,
    animate: Boolean,
    onOpen: () -> Unit,
) {
    val relics = vault?.ownedCount ?: 0
    val total = vault?.total ?: 16
    val crests = ownedFrames.size
    val summary = "$relics of $total relics · $crests of ${Gacha.CREST_FRAMES.size} crests"
    InkPanel(
        Modifier.padding(top = 12.dp).semantics { contentDescription = "Open your collection, $summary" },
        onClick = onOpen,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Collection", style = MaterialTheme.typography.titleSmall, color = IronvellumColors.Ink)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = IronvellumColors.InkMuted, modifier = Modifier.size(18.dp))
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (vault?.houses ?: emptyList()).forEach { house ->
                val done = house.owned == house.size
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    HouseEmblem(
                        house.house,
                        if (house.owned > 0) IronvellumColors.SystemGreen else IronvellumColors.Bracket,
                        Modifier.size(30.dp),
                        complete = done,
                    )
                    Text(house.house.label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = IronvellumColors.Ink)
                    Text("${house.owned} / ${house.size}", style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted)
                }
            }
        }
        if (landing?.fresh != null) {
            Spacer(Modifier.height(8.dp))
            InkDivider()
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val relicId = landing.freshRelicId
                val crestId = landing.freshCrestId
                if (relicId != null && landing.freshTier != null) {
                    HouseRelicSigil(relicId, landing.freshTier!!, Modifier.size(32.dp))
                } else if (crestId != null) {
                    CrestPlate(crestId, Modifier.size(32.dp), animate = animate)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = IronvellumColors.SovereignGold, fontWeight = FontWeight.SemiBold)) { append("New") }
                            append(" · ${landing.fresh}")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = IronvellumColors.Ink,
                    )
                    landing.freshNote?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted) }
                }
            }
        }
        NextCrestRow(level, ownedFrames, animate)
    }
}

/** The next crest the ladder pays and how many levels off it is. Past the last milestone, or with the ladder owned, nothing. */
@Composable
private fun NextCrestRow(level: Int, owned: Set<String>, animate: Boolean) {
    val (next, crest) = Veil.nextMilestone(level, owned) ?: return
    val toGo = next - level
    Spacer(Modifier.height(8.dp))
    InkDivider()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CrestPlate(crest, Modifier.size(32.dp), owned = false, animate = animate)
        Column(Modifier.weight(1f)) {
            Text("Next crest at level $next", style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.Ink)
            InkRail(
                ((level - (next - Veil.MILESTONE_EVERY)).toFloat() / Veil.MILESTONE_EVERY).coerceIn(0f, 1f),
                Modifier.padding(top = 6.dp),
                height = 4.dp,
            )
        }
        Text("$toGo ${plural(toGo, "level", "levels")}", style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted)
    }
}

/**
 * What I can do, always docked: one primary, and never more than one.
 *  - with inscriptions banked: "Inscribe (N)", and "Or buy another" as a link while the balance covers it;
 *  - with none banked and the price in hand: "Buy an inscription · price";
 *  - short of the price: no button, a progress line saying how far and how long.
 */
@Composable
private fun VeilDock(
    rolls: Int,
    cost: Long,
    essence: Long,
    perHour: Double,
    onInscribe: () -> Unit,
    onBuy: () -> Unit,
) {
    val affordable = essence >= cost
    val price = "%,d essence".fmt(cost)
    when {
        rolls > 0 -> CelebrationDock(
            primary = "Inscribe ($rolls)",
            onPrimary = onInscribe,
            link = "Or buy another · $price".takeIf { affordable },
            onLink = onBuy,
            caption = {
                Text(
                    "$rolls ${plural(rolls, "inscription", "inscriptions")} waiting",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = IronvellumColors.SovereignGold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                )
            },
        )
        affordable -> CelebrationDock(
            primary = "Buy an inscription · $price",
            onPrimary = onBuy,
            reserveLink = false,
            caption = { NoneWaiting(Modifier.padding(bottom = 10.dp)) },
        )
        else -> CelebrationDock(
            primary = null,
            onPrimary = {},
            caption = {
                NoneWaiting()
                InkRail((essence.toFloat() / cost).coerceIn(0f, 1f), Modifier.padding(top = 10.dp), height = 4.dp)
                val hours = hoursToGather((cost - essence).toDouble(), perHour)
                Text(
                    "%,d of %,d".fmt(essence, cost) + (hours?.let { " · about $it h at ${"%.1f".fmt(perHour)}/h" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
            },
        )
    }
}

@Composable
private fun NoneWaiting(modifier: Modifier = Modifier) {
    Text(
        "No inscriptions waiting. Every level-up earns one.",
        style = MaterialTheme.typography.bodySmall,
        color = IronvellumColors.InkMuted,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * The circle's pooled week on the Veil screen: "Circle · <name> · N / goal
 * this week" as one quiet row; a tap opens the circle on the ALLIES tab. The
 * server's canonical total, as that header shows it, and a crossed goal adds
 * the gold "goal met" mark. Hidden when signed out or circle-less; a failed
 * read hides the line — it is decoration here, and the ALLIES tab carries the
 * honest error state.
 * One read per screen entry; nothing keeps it fresh while the screen is closed.
 */
@Composable
private fun CircleBannerLine(onOpen: () -> Unit) {
    val app = LocalContext.current.applicationContext as IronvellumApp
    val configured = Cloud.config.collectAsStateWithLifecycle().value != null
    val account by app.accountRepository.account.collectAsStateWithLifecycle()
    if (!configured || account == null) return

    var circle by remember(account?.userId) { mutableStateOf<Circle?>(null) }
    LaunchedEffect(account?.userId) {
        circle = app.circleBonus.read().getOrNull()?.circle
    }
    circle?.let { b ->
        val total = b.total
        // Goal 0: no two-member roster yet this week, so nothing to measure.
        val goal = b.goal
        val met = goal > 0 && total >= goal
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.extraSmall)
                    .clickable(role = Role.Button, onClickLabel = "Open the circle", onClick = onOpen)
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Circle · ${b.name}" + if (goal > 0) " · $total / $goal this week" + if (met) " · goal met" else "" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (met) IronvellumColors.SovereignGold else IronvellumColors.InkMuted,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = IronvellumColors.InkMuted,
                    modifier = Modifier.size(18.dp),
                )
            }
            InkDivider()
        }
    }
}
