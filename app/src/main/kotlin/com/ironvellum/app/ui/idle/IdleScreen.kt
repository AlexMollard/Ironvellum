package com.ironvellum.app.ui.idle

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.outlined.Diamond
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ironvellum.app.IronvellumApp
import com.ironvellum.app.data.IdleInputs
import com.ironvellum.app.data.IdleSnapshot
import com.ironvellum.app.data.Repository
import com.ironvellum.app.data.cloud.Cloud
import com.ironvellum.app.domain.Circle
import com.ironvellum.app.domain.Gacha
import com.ironvellum.app.domain.Idle
import com.ironvellum.app.domain.RewardRarity
import com.ironvellum.app.domain.HouseEffects
import com.ironvellum.app.domain.RelicHouse
import com.ironvellum.app.domain.Reward
import com.ironvellum.app.domain.VaultState
import com.ironvellum.app.domain.Xp
import com.ironvellum.app.domain.RollResult
import com.ironvellum.app.domain.Veil
import com.ironvellum.app.domain.fmt
import com.ironvellum.app.ui.components.AchievementOverlay
import com.ironvellum.app.ui.components.CelebrationPage
import com.ironvellum.app.ui.components.CrestPlate
import com.ironvellum.app.ui.components.HouseEmblem
import com.ironvellum.app.ui.components.HouseRelicSigil
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.components.rarityWord
import com.ironvellum.app.ui.theme.RarityTint
import com.ironvellum.app.ui.theme.TileShape
import com.ironvellum.app.ui.theme.inkBorder
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.style.TextAlign
import kotlin.math.roundToInt
import com.ironvellum.app.ui.components.InkDivider
import com.ironvellum.app.ui.dashboard.FIRST_RUN_VEIL_LINE
import com.ironvellum.app.ui.components.InkRail
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.components.IronvellumDialog
import com.ironvellum.app.ui.components.ListRow
import com.ironvellum.app.ui.components.PushedHeader
import com.ironvellum.app.ui.components.SectionHeader
import com.ironvellum.app.ui.components.inscribedPage
import com.ironvellum.app.ui.components.plural
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import com.ironvellum.app.data.FirstRunPrefs
import com.ironvellum.app.domain.FirstRun
import kotlin.math.ceil


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
     * independent of the roll snapshot anyway. It holds the active relic, the houses and what they add.
     */
    val vault: StateFlow<VaultState?> = repo.observeVault()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The lifter level, which says how far off the next milestone crest is. */
    val level: StateFlow<Int> = repo.observeProfile()
        .map { Xp.levelFor(it?.totalXp ?: 0L) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1)

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

    /** The repository re-checks the price inside its own transaction, so a stale tap buys nothing. */
    fun buyInscription() {
        viewModelScope.launch { repo.buyInscription() }
    }

    /** Cosmetic only — the repository rejects equipping a frame not owned. */
    fun equipFrame(frameId: String?) {
        viewModelScope.launch { repo.equipFrame(frameId) }
    }

    /**
     * Transactional on the repo side — payout and inscription spend land together. A relic comes back
     * with the vault AFTER the draw, a crest with how many crests are now held, for their reveals.
     */
    fun inscribeFigure(onInscribed: (RollResult?, VaultState?, Int?) -> Unit) {
        viewModelScope.launch {
            val result = repo.spendRoll(seed = System.nanoTime())
            val vault = if (result?.reward is Reward.Relic) repo.vaultNow() else null
            val crests = if (result?.reward is Reward.CrestFrame) repo.observeOwnedFrames().first().size else null
            onInscribed(result, vault, crests)
        }
    }
}

/** How often the live essence is recomputed; two decimals move about once a second at realistic rates. */
private const val TICK_MS = 1_000L

@Composable
fun IdleScreen(
    onBack: () -> Unit,
    onOpenCircle: () -> Unit = {},
    onOpenVault: () -> Unit = {},
    onOpenCrests: () -> Unit = {},
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
    // The reveal for a spent inscription; null once the overlay finishes so it never re-shows.
    var inscriptionPage by remember { mutableStateOf<CelebrationPage.Inscribed?>(null) }
    var housesOpen by rememberSaveable { mutableStateOf(false) }
    val intro by viewModel.intro.collectAsStateWithLifecycle()

    // The ticker and motes move under the same gate as the Veil on Today: resumed, animators on, not a preview.
    val animate = rememberTodayMotion()
    val now = rememberVeilNow(System.currentTimeMillis(), animate, TICK_MS)

    val snapshot = ui.snapshot
    val inputs = ui.inputs

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        // Opened from Today's footer, not a tab, so it carries its own way out.
        PushedHeader("The Veil", onBack = { onBack() }, modifier = Modifier.padding(top = 8.dp))

        // The circle's pooled week, one line under the header; a tap opens the
        // circle on the ALLIES tab. Refreshes on screen open only.
        CircleBannerLine(onOpenCircle)

        if (snapshot == null || inputs == null) {
            // Brief empty frame while the flows warm up; never fake numbers.
            Text(
                "The echoes are gathering…",
                fontFamily = ChakraPetch,
                color = IronvellumColors.InkMuted,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 18.dp),
            )
        } else {
            EssenceBlock(snapshot, now, animate)
            away?.let { AwayLine(it) }
            ActiveRelicPanel(vault, animate, onOpenVault)
            // Inscriptions are ALWAYS visible. Hiding them until the first
            // level-up made the whole feature undiscoverable.
            InscribeBlock(
                rolls = ui.rolls,
                onInscribe = {
                    viewModel.inscribeFigure { result, vaultAfter, crests ->
                        if (result != null) inscriptionPage = inscribedPage(result, vaultAfter, crests)
                    }
                },
            )
            BuyInscriptionRow(
                cost = Veil.offeringCost(offerings),
                bought = offerings,
                essence = snapshot.state.essence,
                onBuy = viewModel::buyInscription,
            )
            HousesSummary(vault, onOpenVault, onShowHouses = { housesOpen = true })
            NextCrestRow(level, ui.ownedFrames, onOpenCrests)
            RateBlock(snapshot, inputs)
            CapNote(snapshot.rate.effects)
        }

        // Bottom-nav clearance — the last row must never sit under it.
        Spacer(Modifier.height(120.dp))
    }
    inscriptionPage?.let { page ->
        AchievementOverlay(
            pages = listOf(page),
            onDone = { inscriptionPage = null },
            wornCrestId = ui.equippedFrame,
            onWearCrest = { viewModel.equipFrame(it) },
        )
    }
    if (housesOpen) HousesSheet { housesOpen = false }
    if (intro) VeilIntroSheet(onDone = viewModel::introSeen)
}

/**
 * The Veil's one-time introduction, a bottom sheet over the screen the first time it opens. Three rows,
 * one for each thing the screen shows. There is no scroll in the text slot: the dialog scrolls its own
 * column, and three short rows fit.
 */
@Composable
private fun VeilIntroSheet(onDone: () -> Unit) {
    val fullHours = HouseEffects.NONE.fullStrengthHours.toInt()
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
 * The roll at a glance: the ticking essence over faint motes (the same ticker and motes the Veil on
 * Today draws), then one plain row of rate, echoes and relic, and the full-strength bar. The total is
 * banked essence PLUS what is accruing right now, so it visibly climbs while you watch.
 */
@Composable
private fun EssenceBlock(snapshot: IdleSnapshot, now: Long, animate: Boolean) {
    val state = snapshot.state
    val rate = snapshot.rate
    val total = state.essence.toDouble() + Idle.accruedExact(state, rate, now).coerceAtLeast(0.0)
    val phase = rememberVeilPhase(animate)
    val motes = remember { veilMotes(9, 5) }

    SectionHeader("Essence", topPadding = 18.dp)
    Box(Modifier.fillMaxWidth().height(EssenceBoxHeight).clipToBounds()) {
        VeilMotes(motes, phase, animate, Modifier.matchParentSize())
        EssenceFigure(
            essence = total.toLong(),
            size = 32.sp,
            animate = animate,
            // Two decimals at every magnitude: whole units froze the counter, at 20 an hour the integer moves once in three minutes.
            text = "%,.2f".fmt(total),
            modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 6.dp),
        )
    }
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "${"%.1f".fmt(rate.perHour)} per hour",
            style = MaterialTheme.typography.bodyMedium,
            color = IronvellumColors.InkMuted,
            modifier = Modifier.weight(1f),
        )
        FigurePair("Echoes", state.figures.toString())
        FigurePair("Relic", "×${"%.2f".fmt(state.relicMultiplier)}")
    }
    // The window is the houses: 24 h, 26 h with a full Iron house.
    val window = rate.effects.fullStrengthHours
    veilStrength(state.lastCollectedAtMs, now, rate.effects)?.let { strength ->
        InkRail(strength.fraction, Modifier.padding(top = 12.dp), height = 4.dp)
        val hours = ceil(strength.fraction * window).toInt()
        Text(
            if (strength.fraction > 0f) "$hours h of ${window.roundToInt()} at full strength, then it tapers" else strength.caption,
            style = MaterialTheme.typography.bodySmall,
            color = IronvellumColors.InkMuted,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** A label and its figure on one line: the label muted, the figure in ink. */
@Composable
private fun FigurePair(label: String, value: String) {
    Text(
        buildAnnotatedString {
            append("$label ")
            withStyle(SpanStyle(color = IronvellumColors.Ink, fontWeight = FontWeight.SemiBold)) { append(value) }
        },
        style = MaterialTheme.typography.bodyMedium,
        color = IronvellumColors.InkMuted,
        maxLines = 1,
    )
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
 * The inscription control, the screen's one primary action. "Inscribe (N)" exists only while inscriptions
 * are banked: at zero there is no dead button, just the path to the next one.
 */
@Composable
private fun InscribeBlock(rolls: Int, onInscribe: () -> Unit) {
    var oddsOpen by rememberSaveable { mutableStateOf(false) }
    if (rolls > 0) {
        Text(
            "$rolls ${plural(rolls, "inscription", "inscriptions")} waiting",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = IronvellumColors.SovereignGold,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
        )
        IronvellumButton(
            label = "Inscribe ($rolls)",
            onClick = onInscribe,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        )
    } else {
        Text(
            "No inscriptions waiting. Level up to earn the next one.",
            style = MaterialTheme.typography.bodyMedium,
            color = IronvellumColors.InkMuted,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
    // An inscription pays figures, a relic OR a crest — a collection screen
    // showing only frames made a relic roll look like a lost crest.
    Text(
        "Each level-up earns one inscription: echoes for the Veil, a relic " +
            "that lifts your whole essence rate, or a crest worn on your folio.",
        style = MaterialTheme.typography.bodySmall,
        color = IronvellumColors.InkMuted,
        modifier = Modifier.padding(top = 8.dp),
    )
    ListRow(
        "Drop rates",
        modifier = Modifier.padding(top = 4.dp),
        onClickLabel = if (oddsOpen) "Hide drop rates" else "Show drop rates",
        onClick = { oddsOpen = !oddsOpen },
    )
    if (oddsOpen) OddsTable()
    InkDivider()
}

/**
 * Essence's one use: an extra inscription at [Veil.offeringCost]. Disabled, not hidden, while the banked
 * essence is short, so the price is always visible.
 */
@Composable
private fun BuyInscriptionRow(cost: Long, bought: Int, essence: Long, onBuy: () -> Unit) {
    val affordable = essence >= cost
    ListRow(
        "Buy an inscription · %,d essence".fmt(cost),
        modifier = Modifier
            .alpha(if (affordable) 1f else 0.45f)
            .semantics { if (!affordable) disabled() },
        subline = if (affordable) {
            "$bought bought so far, the price rises by ${Veil.OFFERING_STEP} each time"
        } else {
            "%,d more essence to go".fmt(cost - essence)
        },
        onClickLabel = "Buy an inscription",
        onClick = if (affordable) onBuy else null,
    )
    InkDivider()
}

/**
 * WHY the rate is what it is: the two factors as plain rows, and what feeds them in one line. A lifter
 * with no history, or one whose rate has decayed to its floor, is told so in a sentence.
 */
@Composable
private fun RateBlock(snapshot: IdleSnapshot, inputs: IdleInputs) {
    val rate = snapshot.rate
    val atFloor = inputs.sessionsLast7d == 0 && inputs.volumeLast7d == 0.0
    // No history at all: nothing sealed, ever, AND no skill unlocks means the
    // lifter has never trained, so the decay line would read as nonsense. A
    // lapsed lifter has sealed trials, just none this week: they get the decay copy.
    val firstRun = atFloor && inputs.skillsUnlocked == 0 && inputs.sealedTrials == 0
    SectionHeader("Why the rate", topPadding = 20.dp)
    if (firstRun || atFloor) {
        Text(
            if (firstRun) {
                FIRST_RUN_VEIL_LINE
            } else {
                "The echoes have heard nothing from you. Your rate has decayed to its floor; return to training and they will rise again."
            },
            style = MaterialTheme.typography.bodySmall,
            color = IronvellumColors.InkMuted,
            modifier = Modifier.padding(bottom = 4.dp),
        )
    }
    InkDivider()
    ListRow("Training factor", value = "×${"%.2f".fmt(rate.trainingFactor)}", valueColor = IronvellumColors.Ink)
    InkDivider()
    ListRow("Technique factor", value = "×${"%.2f".fmt(rate.skillFactor)}", valueColor = IronvellumColors.Ink)
    InkDivider()
    ListRow(
        "Relics",
        subline = "Every relic lifts the whole rate",
        value = "×${"%.2f".fmt(snapshot.state.relicMultiplier)}",
        valueColor = IronvellumColors.Ink,
    )
    InkDivider()
    // A house whose 2-relic bonus is reached lifts its own part of the rate.
    RelicHouse.entries.filter { rate.effects.term(it) > 1.0 }.forEach { house ->
        ListRow(
            "${house.title} · ${house.term} term",
            value = "×${"%.2f".fmt(rate.effects.term(house))}",
            valueColor = IronvellumColors.Ink,
        )
        InkDivider()
    }
    ListRow(
        "What feeds it",
        subline = "${inputs.sessionsLast7d} ${plural(inputs.sessionsLast7d, "trial", "trials")} in 7 days" +
            " · ${"%,.0f".fmt(inputs.volumeLast7d)} kg" +
            " · ${inputs.skillsUnlocked} ${plural(inputs.skillsUnlocked, "technique", "techniques")}" +
            " · oath ${inputs.streakDays} ${plural(inputs.streakDays, "day", "days")}",
    )
    InkDivider()
}

/** The title line of a relic: "Fabled · House of Iron", the rarity in its metal. */
@Composable
private fun TierLine(tier: RewardRarity, house: RelicHouse, modifier: Modifier = Modifier) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = RarityTint.of(tier), fontWeight = FontWeight.SemiBold)) { append(rarityWord(tier)) }
            append(" · ${house.title}")
        },
        style = MaterialTheme.typography.bodySmall,
        color = IronvellumColors.InkMuted,
        modifier = modifier,
    )
}

/**
 * The relic setting the rate, large on its rarity plate with what it does, and the way into the vault.
 * The plate turns slowly while motion is on; with none it is the same picture, still.
 */
@Composable
private fun ActiveRelicPanel(vault: VaultState?, animate: Boolean, onOpenVault: () -> Unit) {
    val active = vault?.active
    InkPanel(Modifier.padding(top = 12.dp), contentPadding = PaddingValues(0.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
            Text("Active relic", style = MaterialTheme.typography.labelMedium, color = IronvellumColors.InkMuted, modifier = Modifier.weight(1f))
            Text("Sets your relic rate", style = MaterialTheme.typography.labelMedium, color = IronvellumColors.InkMuted)
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (active != null) {
                HouseRelicSigil(active.relic.id, active.tier, Modifier.size(150.dp), ringed = true, spin = animate)
                Text(active.relic.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = IronvellumColors.Ink, textAlign = TextAlign.Center)
                TierLine(active.tier, active.relic.house, Modifier.padding(top = 2.dp))
                Text(
                    active.relic.house.effect(active.owned?.multiplier ?: 1.0),
                    style = MaterialTheme.typography.bodyMedium,
                    color = IronvellumColors.InkMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
                )
            } else {
                HouseRelicSigil("iron.crown", RewardRarity.Epic, Modifier.size(120.dp), owned = false)
                Text("No relic yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = IronvellumColors.Ink)
                Text(
                    "Your first inscription is a relic. It lifts your whole essence rate.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = IronvellumColors.InkMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
                )
            }
        }
        InkDivider()
        ListRow(
            "Relic vault",
            value = vault?.let { "${it.ownedCount} of ${it.total}" },
            onClickLabel = "Open the relic vault",
            onClick = onOpenVault,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

/** The four houses in a row, each how many of its four relics are held; a tap opens the vault. */
@Composable
private fun HousesSummary(vault: VaultState?, onOpenVault: () -> Unit, onShowHouses: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Relic houses", style = MaterialTheme.typography.labelMedium, color = IronvellumColors.InkMuted, modifier = Modifier.weight(1f).semantics { heading() })
        HousesLink(onShowHouses)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (vault?.houses ?: emptyList()).forEach { house ->
            Column(
                Modifier
                    .weight(1f)
                    .background(IronvellumColors.Vault, TileShape)
                    .inkBorder(IronvellumColors.Rune, TileShape, 1.dp)
                    .clickable(role = Role.Button, onClickLabel = "Open the relic vault", onClick = onOpenVault)
                    .heightIn(min = 72.dp)
                    .padding(top = 8.dp, bottom = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                HouseEmblem(house.house, if (house.owned > 0) IronvellumColors.SystemGreen else IronvellumColors.Bracket, Modifier.size(32.dp))
                Text(house.house.label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = IronvellumColors.Ink)
                Text("${house.owned} / ${house.size}", style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted)
            }
        }
    }
}

/**
 * The next crest the ladder pays and how many levels off it is, opening the collection. Past the
 * last milestone, or with the ladder owned, it is the plain collection row.
 */
@Composable
private fun NextCrestRow(level: Int, owned: Set<String>, onOpenCrests: () -> Unit) {
    val next = (level / Veil.MILESTONE_EVERY + 1) * Veil.MILESTONE_EVERY
    val crest = Veil.milestoneCrest(next, owned)?.takeIf { next / Veil.MILESTONE_EVERY <= Veil.CREST_LADDER.size }
    Spacer(Modifier.height(12.dp))
    InkDivider()
    if (crest == null) {
        ListRow(
            "Crest collection",
            value = "${owned.size} of ${Gacha.CREST_FRAMES.size}",
            onClickLabel = "Open the crest collection",
            onClick = onOpenCrests,
        )
    } else {
        val toGo = next - level
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClickLabel = "Open the crest collection", onClick = onOpenCrests)
                .heightIn(min = 64.dp)
                .padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CrestPlate(crest, Modifier.size(40.dp), owned = false)
            Column(Modifier.weight(1f)) {
                Text("Crest at level $next · $toGo ${plural(toGo, "level", "levels")}", style = MaterialTheme.typography.bodyLarge, color = IronvellumColors.Ink)
                InkRail(
                    ((level - (next - Veil.MILESTONE_EVERY)).toFloat() / Veil.MILESTONE_EVERY).coerceIn(0f, 1f),
                    Modifier.padding(top = 6.dp).clearAndSetSemantics {},
                    height = 4.dp,
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = IronvellumColors.InkMuted)
        }
    }
    InkDivider()
}

/** The offline cap, stated plainly, from the houses in force: a full Iron house is 26 hours, a full Vigil house 4 days. */
@Composable
private fun CapNote(houses: HouseEffects) {
    // Unbounded and short: at maxLines = 3 the last clause — the cap
    // itself — was the part the ellipsis cut on a 360dp phone.
    Text(
        "Echoes work at full strength for ${houses.fullStrengthHours.roundToInt()} hours, then taper over " +
            "${Idle.TAPER_WINDOW_HOURS.toInt()} to ${(houses.minEfficiency * 100).roundToInt()}% of the pace. " +
            "One absence pays at most ${(houses.maxEffectiveHours / 24).roundToInt()} days.",
        style = MaterialTheme.typography.bodySmall,
        color = IronvellumColors.InkMuted,
        modifier = Modifier.padding(top = 14.dp),
    )
}

/**
 * What an inscription can actually pay, read straight off [Gacha.DROP_TABLE] —
 * the same table the roller uses, so the odds shown can never drift from the
 * odds rolled.
 */
@Composable
private fun OddsTable() {
    Column(
        Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Gacha.DROP_TABLE.forEach { odds ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        rarityLabel(odds.rarity),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = IronvellumColors.InkMuted,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        formatChance(odds.chance),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = IronvellumColors.InkMuted,
                    )
                }
                if (odds.figureChance > 0.0) {
                    OddsLine(
                        "${odds.figuresLow}–${odds.figuresHigh} echoes",
                        formatChance(odds.figureChance),
                    )
                }
                if (odds.relicChance > 0.0) {
                    OddsLine(
                        "Relic ×%.2f–×%.2f".fmt(odds.relicLow, odds.relicHigh),
                        formatChance(odds.relicChance),
                    )
                }
                if (odds.frameChance > 0.0) {
                    OddsLine("Crest", formatChance(odds.frameChance))
                }
            }
        }
        Text(
            "Rarity odds are per inscription; the second number is the split inside " +
                "that rarity. Echoes join the Veil, a relic lifts your rate " +
                "for good, a crest is worn on your folio.",
            style = MaterialTheme.typography.bodySmall,
            color = IronvellumColors.InkMuted,
        )
        // Pity changes the real odds, so it is stated here. The table above
        // would otherwise be quietly wrong about what the roller does.
        Text(
            "After ${Gacha.PITY_AFTER} inscriptions that yield only echoes, the next one " +
                "is guaranteed a relic or a crest. A relic always lands within ${Gacha.RELIC_PITY} " +
                "inscriptions, and your first inscription is a relic.",
            style = MaterialTheme.typography.bodySmall,
            color = IronvellumColors.InkMuted,
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
            color = IronvellumColors.Ink,
        )
    }
}

/** Whole percents where possible: "1%" reads better than "1.0%". */
private fun formatChance(fraction: Double): String {
    val pct = fraction * 100.0
    return if (pct % 1.0 == 0.0) "${pct.toInt()}%" else "%.1f%%".fmt(pct)
}

private fun rarityLabel(rarity: RewardRarity): String =
    if (rarity == RewardRarity.Epic) "Fabled" else rarity.name

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
