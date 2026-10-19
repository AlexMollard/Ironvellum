package com.ironvellum.app.ui.idle

import com.ironvellum.app.data.Units
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
import androidx.compose.ui.res.stringResource
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
import com.ironvellum.app.R
import com.ironvellum.app.data.IdleInputs
import com.ironvellum.app.data.IdleSnapshot
import com.ironvellum.app.data.Repository
import com.ironvellum.app.data.Words
import com.ironvellum.app.domain.HouseProgress
import com.ironvellum.app.domain.Idle
import com.ironvellum.app.domain.RelicHouse
import com.ironvellum.app.domain.RelicHouses
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.domain.VaultState
import com.ironvellum.app.domain.fmt
import com.ironvellum.app.ui.asString
import com.ironvellum.app.ui.components.InkDivider
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.components.InkRail
import com.ironvellum.app.ui.components.PushedHeader
import com.ironvellum.app.ui.components.SectionHeader
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
        PushedHeader(stringResource(R.string.idle_why_rate_label), onBack = onBack, modifier = Modifier.padding(horizontal = 16.dp).padding(top = 8.dp))
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
        Text(stringResource(R.string.idle_rate_unit_label), style = MaterialTheme.typography.bodyLarge, color = IronvellumColors.InkMuted, modifier = Modifier.padding(bottom = 6.dp))
    }
    Text(
        stringResource(R.string.idle_rate_intro, Idle.FLOOR.roundToInt()),
        style = MaterialTheme.typography.bodySmall,
        color = IronvellumColors.InkMuted,
        modifier = Modifier.padding(start = 2.dp, top = 2.dp),
    )
    if (firstRun || atFloor) {
        Text(
            if (firstRun) {
                FIRST_RUN_VEIL_LINE
            } else {
                stringResource(R.string.idle_rate_decayed)
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
            stringResource(R.string.idle_rate_training_label),
            rate.trainingFactor,
            UiText.res(
                R.string.idle_rate_training_feeds,
                UiText.themedPlural(R.plurals.idle_rate_sessions, R.plurals.idle_rate_sessions__t, inputs.sessionsLast7d),
                Units.volume(inputs.volumeLast7d),
                UiText.themedPlural(R.plurals.idle_rate_streak, R.plurals.idle_rate_streak__t, inputs.streakDays),
            ).asString(),
            ceiling = trainingMax,
        )
        InkDivider()
        FactorRow(
            Words.technique.c,
            rate.skillFactor,
            UiText.themedPlural(R.plurals.idle_rate_skills_unlocked, R.plurals.idle_rate_skills_unlocked__t, inputs.skillsUnlocked).asString(),
            ceiling = skillMax,
        )
        InkDivider()
        val held = vault?.ownedCount ?: 0
        val strongest = vault?.active
        FactorRow(
            stringResource(R.string.idle_rate_relics_label),
            state.relicMultiplier,
            if (strongest == null) {
                stringResource(R.string.idle_rate_relics_none)
            } else {
                UiText.plural(
                    R.plurals.idle_rate_relics_feeds,
                    held,
                    held,
                    strongest.relic.name,
                    (((strongest.owned?.multiplier ?: 1.0) - 1.0) * 100).roundToInt(),
                ).asString()
            },
            ceiling = null,
        )
        InkDivider()
        FactorRow(
            stringResource(R.string.idle_rate_echoes_label),
            rate.echoFactor,
            UiText.plural(
                R.plurals.idle_rate_echoes_feeds,
                state.figures,
                state.figures,
                Idle.ECHO_BONUS_PER.roundToInt(),
                "%.1f".fmt(houses.crest.echoStep * 100).removeSuffix(".0"),
                (houses.crest.echoCap * 100).roundToInt(),
            ).asString(),
            ceiling = 1.0 + houses.crest.echoCap,
        )
    }

    if (vault != null) {
        SectionHeader(stringResource(R.string.idle_rate_houses_header), topPadding = 18.dp)
        vault.houses.forEach { HouseBonusRow(it, vault.effects.term(it.house), onOpenCollection) }
        InkDivider()
    }

    SectionHeader(stringResource(R.string.idle_rate_away_header), topPadding = 18.dp)
    AwayCurve(houses.fullStrengthHours, houses.minEfficiency, houses.taperWindowHours)
    Text(
        stringResource(R.string.always_idle_rate_away_note, houses.maxEffectiveHours.roundToInt()),
        style = MaterialTheme.typography.bodySmall,
        color = IronvellumColors.InkMuted,
        modifier = Modifier.padding(start = 2.dp, top = 6.dp),
    )
}

/** One multiplier of the rate: its name and factor, what feeds it, and a rail to its ceiling when it has one. */
@Composable
private fun FactorRow(label: String, factor: Double, feeds: String, ceiling: Double?) {
    val factorDescription = stringResource(R.string.idle_rate_factor_description, label, "%.2f".fmt(factor), "%.2f".fmt(ceiling ?: 0.0))
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
                        .semantics { contentDescription = factorDescription },
                    height = 4.dp,
                )
                Text(stringResource(R.string.idle_rate_max, "%.2f".fmt(ceiling)), style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted)
            }
        }
    }
}

/** What the house's term in the rate is called: where its 2-relic bonus lands. */
private fun termWhere(house: RelicHouse): UiText = when (house) {
    RelicHouse.Iron -> UiText.res(R.string.idle_rate_term_iron)
    RelicHouse.Vigil -> UiText.res(R.string.idle_rate_term_vigil)
    RelicHouse.Craft -> UiText.themed(R.string.idle_rate_term_craft, R.string.idle_rate_term_craft__t)
    RelicHouse.Return -> UiText.res(R.string.idle_rate_term_return)
}

/** "1 more relic adds 2 h of full strength", or null once the house is complete. */
private fun nextBonusText(house: HouseProgress): UiText? {
    val next = house.next ?: return null
    val left = house.toNext
    return if (next.needed == RelicHouses.FULL) {
        UiText.plural(R.plurals.idle_rate_next_gain, left, left, house.house.fullGain)
    } else {
        UiText.plural(R.plurals.idle_rate_next_term, left, left, house.house.term)
    }
}

/**
 * A house at the 2-relic bonus reads in force with its multiplier on the term; one short of it reads quiet,
 * with what the next relic adds and the way to the collection.
 */
@Composable
private fun HouseBonusRow(house: HouseProgress, term: Double, onOpenCollection: () -> Unit) {
    val inForce = house.owned >= RelicHouses.PAIR
    val collectionLabel = stringResource(R.string.idle_rate_collection_description)
    InkDivider()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (inForce) {
            Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.idle_rate_in_force_description), tint = IronvellumColors.SystemGreen, modifier = Modifier.size(14.dp))
        } else {
            Text("·", style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.InkMuted, textAlign = TextAlign.Center, modifier = Modifier.size(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(house.house.title, style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.Ink)
            Text(
                run {
                    val next = nextBonusText(house)
                    when {
                        inForce && next != null -> UiText.res(R.string.idle_rate_house_in_force, termWhere(house.house), next)
                        inForce -> UiText.res(R.string.idle_rate_house_complete, termWhere(house.house))
                        else -> UiText.res(R.string.idle_rate_house_next, next ?: UiText.Raw(""))
                    }.asString()
                },
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
            )
        }
        if (inForce) {
            Text("×${"%.2f".fmt(term)}", style = TextStyle(fontFeatureSettings = "tnum"), fontWeight = FontWeight.SemiBold, color = IronvellumColors.Ink)
        } else {
            Text(
                stringResource(R.string.idle_collection_title),
                style = MaterialTheme.typography.bodyMedium,
                color = IronvellumColors.SystemGreen,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button, onClickLabel = collectionLabel, onClick = onOpenCollection)
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
    val description = stringResource(R.string.idle_rate_curve_description, fullHours.roundToInt(), (floor * 100).roundToInt(), taperEnd.roundToInt())
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
        Text(stringResource(R.string.idle_rate_axis_start), style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted, modifier = Modifier.weight(fullHours.toFloat()))
        Text(stringResource(R.string.idle_rate_axis_full_ends, fullHours.roundToInt()), style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted, modifier = Modifier.weight(window.toFloat()))
        Text(stringResource(R.string.idle_rate_axis_taper_end, taperEnd.roundToInt(), (floor * 100).roundToInt()), style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted, modifier = Modifier.weight(24f))
    }
}
