package com.ironvellum.app.ui.program

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironvellum.app.R
import com.ironvellum.app.data.Seed
import com.ironvellum.app.data.Words
import com.ironvellum.app.domain.FatigueFootprint
import com.ironvellum.app.domain.FatigueFootprints
import com.ironvellum.app.domain.FatigueLevel
import com.ironvellum.app.domain.FatigueTuning
import com.ironvellum.app.domain.FootprintBand
import com.ironvellum.app.domain.FootprintMuscle
import com.ironvellum.app.domain.FootprintTrait
import com.ironvellum.app.domain.Muscle
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.components.PanelLabel
import com.ironvellum.app.ui.theme.CappedFontScale
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.IronvellumTracking
import com.ironvellum.app.ui.theme.inkBorder

/** A band in words. */
internal fun bandWord(band: FootprintBand): String = Words.text(UiText.res(when (band) {
    FootprintBand.STRONG -> R.string.fatigue_band_strong
    FootprintBand.MEDIUM -> R.string.fatigue_band_medium
    FootprintBand.LIGHT -> R.string.fatigue_band_light
}))

/** The fatigue map's own fills, so a band reads like the level it stands beside: Strong as High, Medium as Moderate, Light as Light. */
internal fun bandFill(band: FootprintBand): Color = fatigueLook(when (band) {
    FootprintBand.STRONG -> FatigueLevel.HIGH
    FootprintBand.MEDIUM -> FatigueLevel.MODERATE
    FootprintBand.LIGHT -> FatigueLevel.LIGHT
}).fill

/** "13 hours" under two days, "3 days" from there (rounded). */
internal fun durationWord(hours: Int): String =
    if (hours < HOURS_UNTIL_DAYS) {
        Words.text(UiText.plural(R.plurals.fatigue_footprint_hours, maxOf(1, hours)))
    } else {
        Words.text(UiText.plural(R.plurals.fatigue_footprint_days, Math.round(hours / HOURS_PER_DAY).toInt()))
    }

private const val HOURS_UNTIL_DAYS = 48
private const val HOURS_PER_DAY = 24.0

/** "Typical result: Hamstrings high, back to light in about 3 days." for [muscle] (the lead muscle when none is named). */
internal fun typicalLine(footprint: FatigueFootprint, muscle: Muscle = footprint.lead): String {
    val dose = footprint.muscles.firstOrNull { it.muscle == muscle }?.dose
    val level = (if (dose == null) footprint.typicalLevel else FatigueLevel.of(dose)).let { levelWord(it).lowercase() }
    val hours = if (dose == null) footprint.lightInHours else FatigueFootprints.lightInHours(muscle, dose)
    return if (hours == null) {
        Words.text(UiText.res(R.string.fatigue_footprint_typical_short, muscle.label, level))
    } else {
        Words.text(UiText.res(R.string.fatigue_footprint_typical, muscle.label, level, durationWord(hours)))
    }
}

/** Why the lift leaves what it does, built from its traits, with the comparison lift when there is one. */
internal fun whyLines(footprint: FatigueFootprint): List<String> {
    val why = when {
        footprint.stretchHeavy && footprint.heavyWholeBody -> R.string.fatigue_footprint_why_both
        footprint.stretchHeavy -> R.string.fatigue_footprint_why_long
        footprint.heavyWholeBody -> R.string.fatigue_footprint_why_heavy
        else -> R.string.fatigue_footprint_why_neither
    }
    val line = Words.text(UiText.res(why))
    val comparison = footprint.comparison ?: return listOf(line)
    val compare = when (comparison.trait) {
        FootprintTrait.LOADS_LONG -> R.string.fatigue_footprint_compare_long
        FootprintTrait.HEAVY_WHOLE_BODY -> R.string.fatigue_footprint_compare_heavy
    }
    return listOf(line, Words.text(UiText.res(compare, catalogueName(comparison.alternative))))
}

/** A catalogue key (lowercase) as the catalogue spells it; the key itself when it is not a seeded name. */
internal fun catalogueName(key: String): String =
    Seed.exercises.firstOrNull { it.name.equals(key, ignoreCase = true) }?.name ?: key

/**
 * The Fatigue page's one working block: the figure (front and back, tappable) beside a bar per muscle, a hint, a
 * legend, and the headline panel for the lit muscle with its recovery chart. The figure and the rows share one
 * selection (the most fatigued muscle until the lifter picks another), so a tap on either lights both and the
 * panel follows. The muscle can be picked from the chart's caption too.
 */
@Composable
internal fun ColumnScope.FootprintBody(footprint: FatigueFootprint) {
    if (footprint.stretchHeavy || footprint.heavyWholeBody) {
        FlowRow(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (footprint.stretchHeavy) FootprintTag(Words.text(UiText.res(R.string.fatigue_tag_stretch_heavy)))
            if (footprint.heavyWholeBody) FootprintTag(Words.text(UiText.res(R.string.fatigue_tag_heavy_whole_body)))
        }
    }
    val worked = footprint.muscles
    var picked by rememberSaveable { mutableStateOf<Muscle?>(null) }
    // Always one lit: a tap on the lit muscle, on empty space or on a muscle the footprint leaves out changes nothing.
    val selected = picked?.takeIf { p -> worked.any { it.muscle == p } } ?: worked.first().muscle
    val bands = worked.associate { it.muscle to it.band }
    val top = worked.maxOf { it.dose }.coerceAtLeast(1e-9)
    val figure = BodyFigures.of(LocalBodySex.current)
    Text(
        Words.text(UiText.res(R.string.fatigue_footprint_hint)),
        style = MaterialTheme.typography.bodySmall,
        color = IronvellumColors.InkMuted,
        modifier = Modifier.padding(bottom = 6.dp),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.width(figureWidth(figure, FIGURE_HEIGHT))) {
          Box {
            TappableFigure(
                figure, FIGURE_HEIGHT,
                { muscle -> bands[muscle]?.let(::bandFill) ?: IronvellumColors.Rune },
                selected,
                { muscle -> if (muscle != null && muscle in bands) picked = muscle },
                { muscle -> muscleLine(muscle, bands[muscle]?.let(::bandWord)) },
            )
            CappedFontScale {
                Row(Modifier.fillMaxWidth().clearAndSetSemantics {}) {
                    FigureLabel(stringResource(R.string.bodymap_label_front), Modifier.weight(1f))
                    FigureLabel(stringResource(R.string.bodymap_label_back), Modifier.weight(1f))
                }
            }
          }
            FootprintLegend(bands.values.toSet())
        }
        Column(Modifier.weight(1f)) {
            worked.forEach { m -> FootprintRow(m, (m.dose / top).toFloat().coerceIn(0.05f, 1f), m.muscle == selected) { picked = m.muscle } }
        }
    }
    Spacer(Modifier.size(10.dp))
    FootprintHeadline(footprint, selected) { picked = it }
}

/** One muscle: its name and a bar as long as its share of the footprint's strongest muscle, tinted by band. */
@Composable
private fun FootprintRow(item: FootprintMuscle, share: Float, selected: Boolean, onSelect: () -> Unit) {
    val muscle = item.muscle
    val line = Words.text(UiText.res(R.string.fatigue_footprint_muscle_line, muscle.label, bandWord(item.band)))
    val hint = stringResource(R.string.muscles_figure_select_hint, muscle.label)
    val accent = IronvellumColors.SystemGreen
    val tint = IronvellumColors.AccentTint
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = ROW_HEIGHT)
            .then(
                if (selected) {
                    Modifier.background(tint).drawBehind { drawRect(accent, size = androidx.compose.ui.geometry.Size(2.dp.toPx(), size.height)) }
                } else {
                    Modifier
                },
            )
            .clickable(role = Role.Button, onClickLabel = hint, onClick = onSelect)
            .semantics(mergeDescendants = true) {
                contentDescription = line
                this.selected = selected
            }
            .padding(start = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            muscle.label,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 16.sp),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = IronvellumColors.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(NAME_WIDTH * LocalDensity.current.fontScale.coerceAtLeast(1f)),
        )
        Box(Modifier.weight(1f).height(6.dp).background(IronvellumColors.Rune, MaterialTheme.shapes.extraSmall)) {
            Box(Modifier.fillMaxWidth(share).fillMaxHeight().background(bandFill(item.band), MaterialTheme.shapes.extraSmall))
        }
        Spacer(Modifier.width(6.dp))
    }
}

/** The one legend under the rows: a swatch and the word for each band the footprint uses. */
@Composable
private fun FootprintLegend(present: Set<FootprintBand>) {
    FlowRow(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        FootprintBand.entries.filter { it in present }.forEach { band ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(bandFill(band), MaterialTheme.shapes.extraSmall))
                Spacer(Modifier.width(4.dp))
                Text(bandWord(band), style = MaterialTheme.typography.labelSmall, fontFamily = ChakraPetch, color = IronvellumColors.InkMuted)
            }
        }
    }
}

@Composable
private fun FootprintTag(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontFamily = ChakraPetch,
        letterSpacing = IronvellumTracking.InlineLabel,
        color = IronvellumColors.Caution,
        modifier = Modifier.inkBorder(IronvellumColors.Caution, MaterialTheme.shapes.extraSmall, 1.dp).padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

/** The panel under the figure: what three hard sets at one rep left typically do to [muscle], and for how long, with its recovery curve. */
@Composable
private fun FootprintHeadline(footprint: FatigueFootprint, muscle: Muscle, onPick: (Muscle) -> Unit) {
    val dose = footprint.muscles.firstOrNull { it.muscle == muscle }?.dose ?: 0.0
    val hours = FatigueFootprints.lightInHours(muscle, dose)
    InkPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 2.dp)) {
        Text(
            Words.text(UiText.res(R.string.fatigue_footprint_typical_head)),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = IronvellumColors.Ink,
        )
        Spacer(Modifier.size(2.dp))
        Text(typicalLine(footprint, muscle), style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.Ink)
        Spacer(Modifier.size(2.dp))
        Text(Words.text(UiText.res(R.string.fatigue_footprint_typical_note)), style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted)
        RecoveryCaption(muscle, footprint.muscles.map { it.muscle }, onPick)
        RecoveryChart(muscle, dose, hours, Modifier.fillMaxWidth().height(CHART_HEIGHT))
        Spacer(Modifier.size(6.dp))
    }
}

/** "{Muscle} recovery, first 24 hours ▾": the chart's title, and a 48dp button listing the muscles. */
@Composable
private fun RecoveryCaption(muscle: Muscle, muscles: List<Muscle>, onPick: (Muscle) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val caption = Words.text(UiText.res(R.string.fatigue_recovery_caption, muscle.label)).uppercase()
    Box {
        Row(
            Modifier
                .heightIn(min = 48.dp)
                .clickable(role = Role.DropdownList, onClickLabel = stringResource(R.string.fatigue_recovery_pick_hint)) { open = true }
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                caption,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = ChakraPetch,
                color = IronvellumColors.InkMuted,
                letterSpacing = IronvellumTracking.InlineLabel,
            )
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = IronvellumColors.InkMuted)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            muscles.forEach { item ->
                DropdownMenuItem(text = { Text(item.label) }, onClick = { open = false; onPick(item) })
            }
        }
    }
}

/**
 * The lit muscle's residual over its first 24 hours, from the same half-life rule the estimate reads: one two-pixel
 * line, a dashed line at the light boundary and a dot where the line crosses it. Axis and ticks stay quiet, and the
 * tick words are in text colour, not the series'.
 */
@Composable
private fun RecoveryChart(muscle: Muscle, dose: Double, crossesAt: Int?, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val ink = IronvellumColors.Ink
    val muted = IronvellumColors.InkMuted
    val grid = IronvellumColors.Rune
    val series = IronvellumColors.Emerald
    val tick = MaterialTheme.typography.labelSmall.copy(color = ink)
    val lightWord = Words.text(UiText.res(R.string.fatigue_level_light))
    val lightStyle = MaterialTheme.typography.labelSmall.copy(color = muted)
    val tickHours = buildList {
        add(0)
        if (crossesAt != null && crossesAt in 3..9) add(crossesAt)
        add(12)
        if (crossesAt != null && crossesAt in 15..21) add(crossesAt)
        add(CHART_HOURS)
    }
    val tickWords = tickHours.associateWith { Words.text(UiText.res(R.string.fatigue_recovery_hours_tick, it)) }
    val level = FatigueLevel.of(dose)
    val spoken = if (crossesAt == null) {
        Words.text(UiText.res(R.string.fatigue_recovery_description_light, muscle.label, levelWord(level)))
    } else {
        Words.text(UiText.res(R.string.fatigue_recovery_description, muscle.label, levelWord(level), durationWord(crossesAt)))
    }
    Canvas(modifier.semantics { contentDescription = spoken; role = Role.Image }) {
        val tickBand = 16.dp.toPx()
        val plotH = size.height - tickBand - 2.dp.toPx()
        val plotW = size.width
        val start = FatigueFootprints.residualFraction(muscle, dose, 0.0)
        val yMax = (maxOf(start, FatigueTuning.LIGHT_BELOW) * 1.1).toFloat()
        fun y(fraction: Double) = plotH * (1f - (fraction / yMax).toFloat())
        fun x(hours: Double) = (hours / CHART_HOURS * plotW).toFloat()

        // The light boundary, dashed, with its word on the side the curve is not.
        val lightY = y(FatigueTuning.LIGHT_BELOW)
        drawPath(rule(0f, lightY, plotW, lightY), muted, style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))))
        val word = measurer.measure(lightWord, lightStyle)
        val curveHighAtEnd = FatigueFootprints.residualFraction(muscle, dose, CHART_HOURS.toDouble()) > FatigueTuning.LIGHT_BELOW
        val wordY = if (curveHighAtEnd) lightY + 2.dp.toPx() else lightY - word.size.height - 2.dp.toPx()
        drawText(word, topLeft = Offset(plotW - word.size.width, wordY))

        // The axis, then the line.
        drawPath(rule(0f, plotH, plotW, plotH), grid, style = Stroke(1.dp.toPx()))
        val curve = Path()
        val steps = CHART_HOURS * 2
        for (i in 0..steps) {
            val h = i / 2.0
            val point = Offset(x(h), y(FatigueFootprints.residualFraction(muscle, dose, h)))
            if (i == 0) curve.moveTo(point.x, point.y) else curve.lineTo(point.x, point.y)
        }
        drawPath(curve, series, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Where it comes back to light.
        if (crossesAt != null && crossesAt <= CHART_HOURS) {
            val at = Offset(x(crossesAt.toDouble()), lightY)
            drawPath(rule(at.x, at.y, at.x, plotH), grid, style = Stroke(1.dp.toPx()))
            drawPath(Path().apply { addOval(Rect(at, 4.dp.toPx())) }, ink)
        }

        // Tick words in text colour, centred on their hour and kept inside the plot.
        tickHours.forEach { h ->
            val laid = measurer.measure(tickWords.getValue(h), tick)
            val left = (x(h.toDouble()) - laid.size.width / 2f).coerceIn(0f, plotW - laid.size.width)
            drawText(laid, topLeft = Offset(left, plotH + 2.dp.toPx()))
        }
    }
}

/** A straight line as a path: the chart draws paths only, like every figure (InkCoverageTest scans for the rest). */
private fun rule(x0: Float, y0: Float, x1: Float, y1: Float) = Path().apply { moveTo(x0, y0); lineTo(x1, y1) }

private const val CHART_HOURS = 24
private val CHART_HEIGHT = 60.dp
private val ROW_HEIGHT = 18.dp
private val NAME_WIDTH = 92.dp

/**
 * WHY IT LEAVES THIS MUCH BEHIND with its text, and "How this is estimated ›" on the heading row, opening the same
 * explainer the breakdown sheet opens.
 */
@Composable
internal fun ColumnScope.FootprintWhy(why: String) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        PanelLabel(Words.text(UiText.res(R.string.fatigue_footprint_why_title)), Modifier.weight(1f))
        Text(
            Words.text(UiText.res(R.string.fatigue_explainer_link)),
            style = MaterialTheme.typography.labelLarge,
            color = IronvellumColors.SystemGreen,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button) { open = true }
                .padding(start = 12.dp)
                .wrapContentHeight(),
        )
    }
    Text(why, style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.Ink)
    if (open) FatigueExplainerSheet { open = false }
}

private val FIGURE_HEIGHT = 136.dp
