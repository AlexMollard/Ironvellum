package com.ironvellum.app.ui.program

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ironvellum.app.R
import com.ironvellum.app.data.Seed
import com.ironvellum.app.data.Words
import com.ironvellum.app.domain.FatigueFootprint
import com.ironvellum.app.domain.FatigueLevel
import com.ironvellum.app.domain.FootprintBand
import com.ironvellum.app.domain.FootprintTrait
import com.ironvellum.app.domain.Muscle
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.ui.components.InkPanel
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

/** "Typical result: Hamstrings high, back to light in about 3 days." */
internal fun typicalLine(footprint: FatigueFootprint): String {
    val muscle = footprint.lead.label
    val level = levelWord(footprint.typicalLevel).lowercase()
    val hours = footprint.lightInHours
    return if (hours == null) {
        Words.text(UiText.res(R.string.fatigue_footprint_typical_short, muscle, level))
    } else {
        Words.text(UiText.res(R.string.fatigue_footprint_typical, muscle, level, durationWord(hours)))
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

/** The tags, the figure filled by band, and a row per muscle: one section of the exercise sheet's Fatigue page. */
@Composable
internal fun ColumnScope.FootprintBody(footprint: FatigueFootprint) {
    if (footprint.stretchHeavy || footprint.heavyWholeBody) {
        FlowRow(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (footprint.stretchHeavy) FootprintTag(Words.text(UiText.res(R.string.fatigue_tag_stretch_heavy)))
            if (footprint.heavyWholeBody) FootprintTag(Words.text(UiText.res(R.string.fatigue_tag_heavy_whole_body)))
        }
    }
    val bands = footprint.muscles.associate { it.muscle to it.band }
    val figure = BodyFigures.of(LocalBodySex.current)
    // The rows below say every muscle in words, so the figure's labels and legend stay out of a screen reader's way.
    FigureBlock(
        figure, FIGURE_HEIGHT,
        { muscle -> bands[muscle]?.let(::bandFill) ?: IronvellumColors.Rune },
        rememberMuscleSelection(),
        { muscle -> muscleLine(muscle, bands[muscle]?.let(::bandWord)) },
        Modifier.fillMaxWidth(),
        quietKey = true,
    ) {}
    Spacer(Modifier.size(8.dp))
    Text(
        Words.text(UiText.res(R.string.fatigue_footprint_where)),
        style = MaterialTheme.typography.bodySmall,
        color = IronvellumColors.InkMuted,
        modifier = Modifier.padding(bottom = 4.dp),
    )
    footprint.muscles.forEach { FootprintRow(it.muscle, it.band) }
}

@Composable
private fun FootprintRow(muscle: Muscle, band: FootprintBand) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(12.dp).background(bandFill(band), MaterialTheme.shapes.extraSmall).inkBorder(IronvellumColors.InkMuted, MaterialTheme.shapes.extraSmall, 1.dp).clearAndSetSemantics {})
        Spacer(Modifier.width(10.dp))
        Text(muscle.label, style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.Ink, modifier = Modifier.weight(1f))
        Text(bandWord(band), style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted)
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

/** The panel under the figure: what three hard sets at one rep left typically do, and for how long. */
@Composable
internal fun ColumnScope.FootprintTypical(footprint: FatigueFootprint) {
    InkPanel(Modifier.fillMaxWidth()) {
        Text(
            Words.text(UiText.res(R.string.fatigue_footprint_typical_head)),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = IronvellumColors.Ink,
        )
        Spacer(Modifier.size(4.dp))
        Text(typicalLine(footprint), style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.Ink)
        Spacer(Modifier.size(6.dp))
        Text(Words.text(UiText.res(R.string.fatigue_footprint_typical_note)), style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted)
    }
}

/** "How this is estimated ›", opening the same explainer the breakdown sheet opens. */
@Composable
internal fun ColumnScope.FootprintExplainLink() {
    var open by remember { mutableStateOf(false) }
    Text(
        Words.text(UiText.res(R.string.fatigue_explainer_link)),
        style = MaterialTheme.typography.labelLarge,
        color = IronvellumColors.SystemGreen,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button) { open = true }
            .padding(vertical = 14.dp),
    )
    if (open) FatigueExplainerSheet { open = false }
}

private val FIGURE_HEIGHT = 180.dp
