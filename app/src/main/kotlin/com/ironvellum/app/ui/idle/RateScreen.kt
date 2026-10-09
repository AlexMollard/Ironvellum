package com.ironvellum.app.ui.idle

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ironvellum.app.data.IdleInputs
import com.ironvellum.app.data.IdleSnapshot
import com.ironvellum.app.data.Repository
import com.ironvellum.app.domain.HouseProgress
import com.ironvellum.app.domain.Idle
import com.ironvellum.app.domain.RelicHouse
import com.ironvellum.app.domain.RelicHouses
import com.ironvellum.app.domain.VaultState
import com.ironvellum.app.domain.fmt
import com.ironvellum.app.ui.components.InkDivider
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.components.InkRail
import com.ironvellum.app.ui.components.PushedHeader
import com.ironvellum.app.ui.components.SectionHeader
import com.ironvellum.app.ui.components.plural
import com.ironvellum.app.ui.dashboard.FIRST_RUN_VEIL_LINE
import com.ironvellum.app.ui.ironvellumRepository
import com.ironvellum.app.ui.theme.IronvellumColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlin.math.roundToInt

/** What the breakdown reads: the rate with its factors, what feeds them, and the vault. */
data class RateUi(val snapshot: IdleSnapshot? = null, val inputs: IdleInputs? = null, val vault: VaultState? = null)

class RateViewModel(repo: Repository) : ViewModel() {
    val ui: StateFlow<RateUi> = combine(repo.observeIdleSnapshot(), repo.observeIdleInputs(), repo.observeVault()) { s, i, v ->
        RateUi(s, i, v)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RateUi())
}

/**
 * Why the rate is what it is, on one pushed page: the answer first, then the four factors that multiply
 * the base (training, technique, relics, echoes) each with what feeds it and its ceiling, the house bonuses
 * in force and the next, and the curve a time away is paid on. Every figure is a factor of [Idle.rate].
 */
@Composable
fun RateScreen(
    onBack: () -> Unit,
    onOpenCollection: () -> Unit,
    viewModel: RateViewModel =
        viewModel(factory = viewModelFactory { initializer { RateViewModel(ironvellumRepository()) } }),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        PushedHeader("Why this rate", onBack = onBack, modifier = Modifier.padding(horizontal = 16.dp).padding(top = 8.dp))
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            val snapshot = ui.snapshot
            val inputs = ui.inputs
            if (snapshot != null && inputs != null) {
                Breakdown(snapshot, inputs, ui.vault, onOpenCollection)
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun Breakdown(snapshot: IdleSnapshot, inputs: IdleInputs, vault: VaultState?, onOpenCollection: () -> Unit) {
    val rate = snapshot.rate
    val state = snapshot.state
    val houses = rate.effects
    val atFloor = inputs.sessionsLast7d == 0 && inputs.volumeLast7d == 0.0
    // No history at all: nothing sealed, ever, AND no skill unlocks means the lifter has never trained, so the
    // decay line would read as nonsense. A lapsed lifter has sealed trials, just none this week: the decay copy.
    val firstRun = atFloor && inputs.skillsUnlocked == 0 && inputs.sealedTrials == 0

    // The answer first.
    Row(Modifier.padding(start = 2.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "%.1f".fmt(rate.perHour),
            style = TextStyle(fontFeatureSettings = "tnum"),
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
            color = IronvellumColors.Ink,
        )
        Text("essence per hour", style = MaterialTheme.typography.bodyLarge, color = IronvellumColors.InkMuted, modifier = Modifier.padding(bottom = 6.dp))
    }
    Text(
        "It starts at ${Idle.FLOOR.roundToInt()} an hour and four things multiply it.",
        style = MaterialTheme.typography.bodySmall,
        color = IronvellumColors.InkMuted,
        modifier = Modifier.padding(start = 2.dp, top = 2.dp),
    )
    if (firstRun || atFloor) {
        Text(
            if (firstRun) {
                FIRST_RUN_VEIL_LINE
            } else {
                "The echoes have heard nothing from you. Your rate has decayed to its floor; return to training and they will rise again."
            },
            style = MaterialTheme.typography.bodySmall,
            color = IronvellumColors.InkMuted,
            modifier = Modifier.padding(start = 2.dp, top = 8.dp),
        )
    }

    // The four multipliers, each with its ceiling where it has one. A house's pair bonus lifts a term past
    // the plain ceiling, so each ceiling is what THIS lifter can reach.
    val trainingMax = 1.0 + (Idle.MAX_TRAINING_FACTOR - 1.0 + houses.crest.trainingCapBonus / Idle.FLOOR) * maxOf(houses.term(RelicHouse.Iron), houses.term(RelicHouse.Vigil))
    val skillMax = 1.0 + houses.skillCeiling * houses.term(RelicHouse.Craft)
    InkPanel(Modifier.padding(top = 14.dp), contentPadding = PaddingValues(0.dp)) {
        FactorRow(
            "Training",
            rate.trainingFactor,
            "${inputs.sessionsLast7d} ${plural(inputs.sessionsLast7d, "trial", "trials")} in 7 days" +
                " · ${"%,.0f".fmt(inputs.volumeLast7d)} kg · oath ${inputs.streakDays} ${plural(inputs.streakDays, "day", "days")}",
            ceiling = trainingMax,
        )
        InkDivider()
        FactorRow(
            "Technique",
            rate.skillFactor,
            "${inputs.skillsUnlocked} ${plural(inputs.skillsUnlocked, "technique", "techniques")} unlocked, kept for good",
            ceiling = skillMax,
        )
        InkDivider()
        val held = vault?.ownedCount ?: 0
        val strongest = vault?.active
        FactorRow(
            "Relics",
            state.relicMultiplier,
            if (strongest == null) {
                "No relics yet. Your first inscription is a relic."
            } else {
                "$held ${plural(held, "relic", "relics")}. The strongest, ${strongest.relic.name}, lifts it by " +
                    "${(((strongest.owned?.multiplier ?: 1.0) - 1.0) * 100).roundToInt()}%."
            },
            ceiling = null,
        )
        InkDivider()
        FactorRow(
            "Echoes",
            rate.echoFactor,
            "${"%,d".fmt(state.figures)} ${plural(state.figures, "echo", "echoes")}. Every ${Idle.ECHO_BONUS_PER.roundToInt()} add " +
                "${"%.1f".fmt(houses.crest.echoStep * 100).removeSuffix(".0")}%, up to ${(houses.crest.echoCap * 100).roundToInt()}%.",
            ceiling = 1.0 + houses.crest.echoCap,
        )
    }

    if (vault != null) {
        SectionHeader("House bonuses", topPadding = 18.dp)
        vault.houses.forEach { HouseBonusRow(it, vault.effects.term(it.house), onOpenCollection) }
        InkDivider()
    }

    SectionHeader("While you are away", topPadding = 18.dp)
    AwayCurve(houses.fullStrengthHours, houses.minEfficiency, houses.taperWindowHours)
    Text(
        "One absence pays at most ${houses.maxEffectiveHours.roundToInt()} h of work. Opening the Veil banks it and starts the clock again.",
        style = MaterialTheme.typography.bodySmall,
        color = IronvellumColors.InkMuted,
        modifier = Modifier.padding(start = 2.dp, top = 6.dp),
    )
}

/** One multiplier of the rate: its name and factor, what feeds it, and a rail to its ceiling when it has one. */
@Composable
private fun FactorRow(label: String, factor: Double, feeds: String, ceiling: Double?) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = IronvellumColors.Ink, modifier = Modifier.weight(1f))
            Text(
                "×${"%.2f".fmt(factor)}",
                style = TextStyle(fontFeatureSettings = "tnum"),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = IronvellumColors.Ink,
            )
        }
        Text(feeds, style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted, modifier = Modifier.padding(top = 2.dp))
        if (ceiling != null && ceiling > 1.0) {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                InkRail(
                    ((factor - 1.0) / (ceiling - 1.0)).toFloat().coerceIn(0f, 1f),
                    Modifier
                        .weight(1f)
                        .semantics { contentDescription = "$label factor, ${"%.2f".fmt(factor)} of ${"%.2f".fmt(ceiling)}" },
                    height = 4.dp,
                )
                Text("max ×${"%.2f".fmt(ceiling)}", style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted)
            }
        }
    }
}

/** What the house's term in the rate is called: where its 2-relic bonus lands. */
private fun termWhere(house: RelicHouse): String = when (house) {
    RelicHouse.Iron -> "Lifting term, inside Training"
    RelicHouse.Vigil -> "Consistency term, inside Training"
    RelicHouse.Craft -> "Technique term, inside Technique"
    RelicHouse.Return -> "Comeback term, on the time away"
}

/** "1 more relic adds 2 h of full strength", or null once the house is complete. */
private fun nextBonusText(house: HouseProgress): String? {
    val next = house.next ?: return null
    val left = house.toNext
    return "$left more ${plural(left, "relic adds", "relics add")} " +
        if (next.needed == RelicHouses.FULL) house.house.fullGain else "+5% to the ${house.house.term} term"
}

/**
 * A house at the 2-relic bonus reads in force with its multiplier on the term; one short of it reads quiet,
 * with what the next relic adds and the way to the collection.
 */
@Composable
private fun HouseBonusRow(house: HouseProgress, term: Double, onOpenCollection: () -> Unit) {
    val inForce = house.owned >= RelicHouses.PAIR
    InkDivider()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (inForce) {
            Icon(Icons.Filled.Check, contentDescription = "In force", tint = IronvellumColors.SystemGreen, modifier = Modifier.size(14.dp))
        } else {
            Text("·", style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.InkMuted, textAlign = TextAlign.Center, modifier = Modifier.size(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(house.house.title, style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.Ink)
            Text(
                if (inForce) {
                    "${termWhere(house.house)}. ${nextBonusText(house)?.plus(".") ?: "Complete."}"
                } else {
                    "${nextBonusText(house)}."
                },
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
            )
        }
        if (inForce) {
            Text("×${"%.2f".fmt(term)}", style = TextStyle(fontFeatureSettings = "tnum"), fontWeight = FontWeight.SemiBold, color = IronvellumColors.Ink)
        } else {
            Text(
                "Collection",
                style = MaterialTheme.typography.bodyMedium,
                color = IronvellumColors.SystemGreen,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button, onClickLabel = "Open the collection", onClick = onOpenCollection)
                    .padding(horizontal = 4.dp)
                    .wrapCenter(),
            )
        }
    }
}

private fun Modifier.wrapCenter(): Modifier = this.then(Modifier.heightIn(min = 48.dp))

/**
 * The curve a time away is paid on: full pace for [fullHours], then a straight fall to [floor] over the taper,
 * then held. Drawn as paths on a flat baseline; the axis runs a day past the end of the taper.
 */
@Composable
private fun AwayCurve(fullHours: Double, floor: Double, window: Double = Idle.TAPER_WINDOW_HOURS) {
    val taperEnd = fullHours + window
    val axisEnd = taperEnd + 24.0
    val description = "Full pace for ${fullHours.roundToInt()} hours, then tapering to ${(floor * 100).roundToInt()}% by ${taperEnd.roundToInt()} hours"
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 2.dp)
            .semantics { contentDescription = description },
    ) {
        val w = size.width
        val pad = 8.dp.toPx()
        fun x(h: Double) = pad + (w - 2 * pad) * (h / axisEnd).toFloat()
        fun y(efficiency: Double) = 56.dp.toPx() - 46.dp.toPx() * efficiency.toFloat()
        val base = Path().apply { moveTo(pad, 56.dp.toPx()); lineTo(w - pad, 56.dp.toPx()) }
        drawPath(base, IronvellumColors.Rune, style = Stroke(1.dp.toPx()))
        val marks = Path().apply {
            listOf(fullHours, taperEnd).forEach { h ->
                moveTo(x(h), 56.dp.toPx())
                lineTo(x(h), 6.dp.toPx())
            }
        }
        drawPath(marks, IronvellumColors.Rune, style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 4.dp.toPx()))))
        val full = Path().apply { moveTo(pad, y(1.0)); lineTo(x(fullHours), y(1.0)) }
        drawPath(full, IronvellumColors.Emerald, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        val taper = Path().apply {
            moveTo(x(fullHours), y(1.0))
            lineTo(x(taperEnd), y(floor))
            lineTo(w - pad, y(floor))
        }
        drawPath(taper, IronvellumColors.InkMuted, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp)) {
        Text("0 h", style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted, modifier = Modifier.weight(fullHours.toFloat()))
        Text("${fullHours.roundToInt()} h, full pace ends", style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted, modifier = Modifier.weight(window.toFloat()))
        Text("${taperEnd.roundToInt()} h, ${(floor * 100).roundToInt()}%", style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted, modifier = Modifier.weight(24f))
    }
}
