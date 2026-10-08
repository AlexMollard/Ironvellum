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
import com.ironvellum.app.data.RelicHolding
import com.ironvellum.app.data.cloud.Cloud
import com.ironvellum.app.domain.Circle
import com.ironvellum.app.domain.Gacha
import com.ironvellum.app.domain.Idle
import com.ironvellum.app.domain.RewardRarity
import com.ironvellum.app.domain.Relics
import com.ironvellum.app.domain.RollResult
import com.ironvellum.app.domain.Veil
import com.ironvellum.app.domain.fmt
import com.ironvellum.app.ui.components.AchievementOverlay
import com.ironvellum.app.ui.components.CrestRail
import com.ironvellum.app.ui.components.InkDivider
import com.ironvellum.app.ui.components.InkRail
import com.ironvellum.app.ui.components.IronvellumButton
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
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

    /** Transactional on the repo side — payout and inscription spend land together. */
    fun inscribeFigure(onInscribed: (RollResult?) -> Unit) {
        viewModelScope.launch {
            onInscribed(repo.spendRoll(seed = System.nanoTime()))
        }
    }
}

/** How often the live essence is recomputed; two decimals move about once a second at realistic rates. */
private const val TICK_MS = 1_000L

@Composable
fun IdleScreen(
    onBack: () -> Unit,
    onOpenCircle: () -> Unit = {},
    viewModel: IdleViewModel =
        viewModel(factory = viewModelFactory { initializer { IdleViewModel(ironvellumRepository()) } }),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val away by viewModel.away.collectAsStateWithLifecycle()
    val relics by viewModel.relics.collectAsStateWithLifecycle()
    val offerings by viewModel.offerings.collectAsStateWithLifecycle()
    // The reveal for a spent inscription; null once the overlay finishes so it never re-shows.
    var inscriptionResult by remember { mutableStateOf<RollResult?>(null) }

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
            // Inscriptions are ALWAYS visible. Hiding them until the first
            // level-up made the whole feature undiscoverable.
            InscribeBlock(
                rolls = ui.rolls,
                onInscribe = { viewModel.inscribeFigure { result -> if (result != null) inscriptionResult = result } },
            )
            BuyInscriptionRow(
                cost = Veil.offeringCost(offerings),
                essence = snapshot.state.essence,
                onBuy = viewModel::buyInscription,
            )
            RateBlock(snapshot, inputs)
            Spacer(Modifier.height(14.dp))
            RelicVault(relics = relics)
            CrestCollection(
                owned = ui.ownedFrames,
                equipped = ui.equippedFrame,
                onEquip = viewModel::equipFrame,
            )
            CapNote()
        }

        // Bottom-nav clearance — the last row must never sit under it.
        Spacer(Modifier.height(120.dp))
    }
    inscriptionResult?.let { result ->
        AchievementOverlay(
            pages = listOf(inscribedPage(result)),
            onDone = { inscriptionResult = null },
        )
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
    veilStrength(state.lastCollectedAtMs, now)?.let { strength ->
        InkRail(strength.fraction, Modifier.padding(top = 12.dp), height = 4.dp)
        val hours = ceil(strength.fraction * Idle.FULL_RATE_HOURS).toInt()
        Text(
            if (strength.fraction > 0f) "$hours h of ${Idle.FULL_RATE_HOURS.toInt()} at full strength, then it tapers" else strength.caption,
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
            "that lifts your rate, or a crest worn on your folio.",
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
 * essence is short, so the price is always visible. The full design comes later.
 */
@Composable
private fun BuyInscriptionRow(cost: Long, essence: Long, onBuy: () -> Unit) {
    val affordable = essence >= cost
    ListRow(
        "Buy an inscription · %,d essence".fmt(cost),
        modifier = Modifier
            .alpha(if (affordable) 1f else 0.45f)
            .semantics { if (!affordable) disabled() },
        subline = if (affordable) null else "%,d more essence to go".fmt(cost - essence),
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
                "The Veil gathers essence while you're away. Seal trials to raise the pace; your echoes and Chronicle will fill in as you train."
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
        "What feeds it",
        subline = "${inputs.sessionsLast7d} ${plural(inputs.sessionsLast7d, "trial", "trials")} in 7 days" +
            " · ${"%,.0f".fmt(inputs.volumeLast7d)} kg" +
            " · ${inputs.skillsUnlocked} ${plural(inputs.skillsUnlocked, "technique", "techniques")}" +
            " · oath ${inputs.streakDays} ${plural(inputs.streakDays, "day", "days")}",
    )
    InkDivider()
}

/**
 * RELIC VAULT: every relic an inscription produced. The rate only uses the strongest at full weight, so
 * the rows say what each adds. Collapsed to one row; the list sits behind it.
 */
@Composable
private fun RelicVault(relics: List<RelicHolding>) {
    var open by rememberSaveable { mutableStateOf(false) }
    ListRow(
        "Relic vault",
        subline = if (relics.isEmpty()) {
            "Nothing inscribed yet"
        } else {
            "${relics.size} ${plural(relics.size, "relic", "relics")} · vault total ×%.2f".fmt(
                Relics.effectiveMultiplier(relics.map { it.multiplier }),
            )
        },
        onClickLabel = if (open) "Hide the relic vault" else "Show the relic vault",
        onClick = { open = !open },
    )
    if (open) {
        Text(
            "A relic is a permanent boost to your idle rate, won from an " +
                "inscription. Every relic you own counts: the strongest at full " +
                "weight, the second at a half, the third at a third, and " +
                "so on down the vault.",
            style = MaterialTheme.typography.bodySmall,
            color = IronvellumColors.InkMuted,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        // Only the relics that actually move the rate are listed. An
        // uncapped vault ran to a hundred rows and buried every section
        // below it; the tail contributes fractions of a percent each.
        relics.take(VAULT_ROWS).forEachIndexed { index, relic ->
            ListRow(
                relic.name,
                // Its real contribution, not a flat label: the rank sets the weight, so show what it adds.
                subline = "+%.2f rate · %s".fmt(
                    (relic.multiplier - 1.0) * Relics.weightAt(index),
                    if (index == 0) "full weight" else "%d%% weight".fmt((Relics.weightAt(index) * 100).toInt()),
                ),
                value = "×%.2f".fmt(relic.multiplier),
                valueColor = IronvellumColors.Ink,
            )
        }
        if (relics.size > VAULT_ROWS) {
            Text(
                "+${relics.size - VAULT_ROWS} more held, counted in the vault total",
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
    }
    InkDivider()
}

/**
 * CREST COLLECTION: the whole catalogue as a horizontal rail of large plates, so the per-frame art is
 * actually visible. Collapsed to one row; the rail sits behind it.
 */
@Composable
private fun CrestCollection(
    owned: Set<String>,
    equipped: String?,
    onEquip: (String?) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    ListRow(
        "Crest collection",
        subline = "${owned.size} of ${Gacha.CREST_FRAMES.size} crests inscribed",
        onClickLabel = if (open) "Hide the crest collection" else "Show the crest collection",
        onClick = { open = !open },
    )
    if (open) {
        CrestRail(owned = owned, equipped = equipped, onEquip = onEquip, modifier = Modifier.padding(vertical = 8.dp))
    }
    InkDivider()
}

/** The offline cap, stated plainly. No player should expect three days to pay out. */
@Composable
private fun CapNote() {
    // Unbounded and short: at maxLines = 3 the last clause — the cap
    // itself — was the part the ellipsis cut on a 360dp phone.
    Text(
        "Echoes work at full strength for ${Idle.FULL_RATE_HOURS.toInt()} hours, then taper over " +
            "${Idle.TAPER_WINDOW_HOURS.toInt()} to a tenth. One absence pays at most " +
            "${(Idle.MAX_EFFECTIVE_HOURS / 24).toInt()} days.",
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

/** Relics listed before the tail is summarised: past this each adds < 1%. */
private const val VAULT_ROWS = 12

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
