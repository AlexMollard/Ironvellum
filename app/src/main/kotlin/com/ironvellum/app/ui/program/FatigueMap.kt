package com.ironvellum.app.ui.program

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ironvellum.app.R
import com.ironvellum.app.data.Words
import com.ironvellum.app.domain.DayNames
import com.ironvellum.app.domain.FatigueLevel
import com.ironvellum.app.domain.FatigueReport
import com.ironvellum.app.domain.LightBy
import com.ironvellum.app.domain.Muscle
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.IronvellumTracking
import java.time.ZoneId
import java.util.Locale

/**
 * How a muscle at [level] is drawn: five steps of the accent, strongest last. Light and Moderate are 0.30 apart
 * so they stay separable at legend-swatch size; High is hatched and Very high cross-hatched in the caution tone,
 * so a level is never carried by colour alone.
 */
internal fun fatigueLook(level: FatigueLevel): RegionLook = when (level) {
    FatigueLevel.FRESH -> RegionLook(IronvellumColors.Rune, RegionDecor.NONE, IronvellumColors.InkMuted)
    FatigueLevel.LIGHT -> RegionLook(IronvellumColors.Emerald.copy(alpha = 0.30f), RegionDecor.NONE, IronvellumColors.Emerald)
    FatigueLevel.MODERATE -> RegionLook(IronvellumColors.Emerald.copy(alpha = 0.60f), RegionDecor.NONE, IronvellumColors.Emerald)
    // The hatch is drawn in the paper's own darkest tone: it stays readable on the strong fill in either theme.
    FatigueLevel.HIGH -> RegionLook(IronvellumColors.Emerald.copy(alpha = 0.85f), RegionDecor.HATCH, IronvellumColors.Abyss)
    FatigueLevel.VERY_HIGH -> RegionLook(IronvellumColors.Emerald.copy(alpha = 0.95f), RegionDecor.CROSS_HATCH, IronvellumColors.Caution)
}

/** The level in words, in the current language. */
internal fun levelWord(level: FatigueLevel): String = Words.text(UiText.res(levelRes(level)))

private fun levelRes(level: FatigueLevel): Int = when (level) {
    FatigueLevel.FRESH -> R.string.fatigue_level_fresh
    FatigueLevel.LIGHT -> R.string.fatigue_level_light
    FatigueLevel.MODERATE -> R.string.fatigue_level_moderate
    FatigueLevel.HIGH -> R.string.fatigue_level_high
    FatigueLevel.VERY_HIGH -> R.string.fatigue_level_very_high
}

/** "Fresh by Monday"; null when the muscle is already fresh. [locale] names the weekday. */
internal fun freshByLine(freshBy: LightBy, locale: Locale = Locale.getDefault()): String? = when (freshBy) {
    LightBy.None -> null
    LightBy.Today -> Words.text(UiText.res(R.string.fatigue_fresh_by_today))
    LightBy.Tomorrow -> Words.text(UiText.res(R.string.fatigue_fresh_by_tomorrow))
    is LightBy.Weekday -> Words.text(UiText.res(R.string.fatigue_fresh_by_weekday, DayNames.short(freshBy.day.value, locale).orEmpty()))
    LightBy.OverAWeek -> Words.text(UiText.res(R.string.fatigue_fresh_by_over_week))
}

/** What a screen reader hears for the figure: every muscle at Moderate or above, grouped by level, strongest first. */
internal fun fatigueSummary(levels: Map<Muscle, FatigueLevel>): String {
    val groups = FatigueLevel.entries.reversed().filter { it >= FatigueLevel.MODERATE }.mapNotNull { level ->
        val names = Muscle.entries.filter { levels[it] == level }.joinToString { it.label.lowercase() }
        if (names.isEmpty()) null else Words.text(UiText.res(R.string.fatigue_summary_level, levelWord(level).lowercase(), names))
    }
    val body = if (groups.isEmpty()) Words.text(UiText.res(R.string.fatigue_summary_none)) else groups.joinToString("; ")
    return Words.text(UiText.res(R.string.fatigue_summary, body))
}

/** Every muscle's level at the report's own "now". */
internal fun levelsOf(report: FatigueReport): Map<Muscle, FatigueLevel> =
    Muscle.entries.associateWith { FatigueLevel.of(report.residual(it)) }

/**
 * The Fatigue map: the one body figure shared with Coverage, each muscle filled by its level, with the five
 * levels as a legend beneath. A tap lights a muscle in [selection].
 */
@Composable
internal fun FatigueMap(
    report: FatigueReport,
    selection: MutableState<Muscle?>,
    modifier: Modifier = Modifier,
    figureHeight: Dp = 260.dp,
) {
    val levels = remember(report) { levelsOf(report) }
    val description = remember(levels) { fatigueSummary(levels) }
    val figure = BodyFigures.of(LocalBodySex.current)
    val look = { muscle: Muscle -> fatigueLook(levels[muscle] ?: FatigueLevel.FRESH) }
    FigureBlock(
        figure, figureHeight, { look(it).fill }, selection,
        { muscleLine(it, levelWord(levels[it] ?: FatigueLevel.FRESH)) },
        modifier.semantics { contentDescription = description },
        decor = look,
        selectedLine = false,
    ) {
        FatigueLevel.entries.forEach { FatigueLegendKey(it) }
    }
}

/** A swatch drawn with the level's own look, hatching included, and its word. */
@Composable
internal fun FatigueLegendKey(level: FatigueLevel) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FatigueSwatch(level)
        Spacer(Modifier.width(4.dp))
        Text(
            levelWord(level),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            color = IronvellumColors.InkMuted,
            letterSpacing = IronvellumTracking.InlineLabel,
        )
    }
}

/** A small square in the level's own look, hatching included: decorative, the word beside it says the level. */
@Composable
internal fun FatigueSwatch(level: FatigueLevel) {
    val look = fatigueLook(level)
    Canvas(Modifier.size(SWATCH).clearAndSetSemantics {}) {
        val box = Path().apply { addRect(Rect(0f, 0f, size.width, size.height)) }
        drawPath(box, look.fill)
        drawDecor(box, look)
        drawPath(box, IronvellumColors.InkMuted, style = Stroke(1.dp.toPx()))
    }
}

private val SWATCH = 14.dp
