package com.ironvellum.app.ui.program

import androidx.compose.runtime.MutableState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ironvellum.app.R
import com.ironvellum.app.data.Words
import com.ironvellum.app.domain.Muscle
import com.ironvellum.app.domain.MuscleLoad
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.ui.theme.CappedFontScale
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.IronvellumTracking
import com.ironvellum.app.ui.theme.inkBorder

private const val FULL_ALPHA = 0.9f
private const val FAINT_ALPHA = 0.3f

/** How a region is marked beyond its fill, so a state never rests on colour alone. */
internal enum class RegionDecor { NONE, HATCH, CROSS_HATCH, DASHED }

/** One muscle's drawn look: its [fill], the [decor] laid over it and the [ink] the decor is drawn in. */
internal data class RegionLook(val fill: Color, val decor: RegionDecor, val ink: Color)

/**
 * The figure's look for a coverage state. Covered is a solid fill; below the minimum is a faint fill with
 * diagonal hatching; heavy is a faint amber fill, cross-hatched and outlined; not trained is bare paper with
 * a dashed outline. The four differ by pattern, never only by hue.
 */
internal fun regionLook(state: CoverageState): RegionLook = when (state) {
    CoverageState.NOT_TRAINED -> RegionLook(IronvellumColors.Rune, RegionDecor.DASHED, IronvellumColors.InkMuted)
    CoverageState.BELOW_MINIMUM ->
        RegionLook(IronvellumColors.Emerald.copy(alpha = 0.18f), RegionDecor.HATCH, IronvellumColors.Emerald)
    CoverageState.COVERED -> RegionLook(IronvellumColors.Emerald.copy(alpha = FULL_ALPHA), RegionDecor.NONE, IronvellumColors.Emerald)
    CoverageState.HEAVY ->
        RegionLook(IronvellumColors.Caution.copy(alpha = 0.22f), RegionDecor.CROSS_HATCH, IronvellumColors.Caution)
}

/** A muscle the page does not judge (the neck): bare paper, no pattern. */
private val UNJUDGED_LOOK = RegionLook(IronvellumColors.Rune, RegionDecor.NONE, IronvellumColors.InkMuted)

/**
 * Front and back figures side by side in ONE Canvas of fixed [figureHeight], each judged muscle drawn in its
 * coverage state ([regionLook]) with a legend beneath. One Canvas with an explicit height is deliberate: a
 * per-figure RowScope weight inside a Column was ignored by Compose and the second figure was clipped away.
 */
@Composable
internal fun CoverageFigure(
    loads: Map<Muscle, MuscleLoad>,
    dismissed: Set<Muscle>,
    modifier: Modifier = Modifier,
    figureHeight: Dp = 320.dp,
    selection: MutableState<Muscle?> = rememberMuscleSelection(),
    interactive: Boolean = true,
) {
    val tally = remember(loads, dismissed) { coverageTally(loads, dismissed) }
    val description = coverageSummary(tally)
    val figure = BodyFigures.of(LocalBodySex.current)
    val look = { muscle: Muscle -> tally.states[muscle]?.let(::regionLook) ?: UNJUDGED_LOOK }
    FigureBlock(
        figure, figureHeight, { look(it).fill }, selection,
        { coverageMuscleLine(it, loads[it], it in dismissed) },
        modifier.semantics { contentDescription = description },
        interactive = interactive,
        decor = look,
    ) { CoverageLegend(showHeavy = tally.heavy.isNotEmpty()) }
}

/** How much of a muscle one exercise works, in the words the figure's legend uses. */
enum class ShareLevel(val label: String) { MAIN("MAIN"), ASSIST("ASSIST") }

/**
 * MuscleMap shares run 0 to 1 (1.0 prime mover, 0.75 worked hard, 0.5
 * helper, 0.25 minor). 0.7 and up reads as main work - the 0.75 step and
 * the bench press's measured 0.7 front delts - and anything lower, the
 * press's 0.6 triceps included, as assisting.
 */
fun shareLevel(share: Double): ShareLevel = if (share >= 0.7) ShareLevel.MAIN else ShareLevel.ASSIST

internal fun shareFill(share: Double?): Color = when {
    share == null || share <= 0.0 -> IronvellumColors.Rune
    shareLevel(share) == ShareLevel.MAIN -> IronvellumColors.Emerald.copy(alpha = 0.9f)
    else -> IronvellumColors.Emerald.copy(alpha = 0.4f)
}

/**
 * The same front and back figure, filled by what ONE exercise works: bright
 * where it is main work, faint where it assists, bare where it does nothing.
 */
@Composable
fun ExerciseMuscleMap(
    shares: Map<Muscle, Double>,
    exerciseName: String,
    modifier: Modifier = Modifier,
    figureHeight: Dp = 220.dp,
    selection: MutableState<Muscle?> = rememberMuscleSelection(),
) {
    val figure = BodyFigures.of(LocalBodySex.current)
    val fill = { muscle: Muscle -> shareFill(shares[muscle]) }
    val lineFor = { muscle: Muscle -> exerciseMuscleLine(muscle, shares[muscle], exerciseName) }
    // The muscle nodes speak for the figure; the labels and legend only repeat what the caller's summary says.
    FigureBlock(figure, figureHeight, fill, selection, lineFor, modifier, quietKey = true) {
        listOf(1.0 to "MAIN", 0.5 to "ASSIST", null to stringResource(R.string.bodymap_key_not_worked)).forEach { (share, label) ->
            LegendKey(shareFill(share), label)
        }
    }
}

/**
 * Opacity of a muscle's emerald on a rite's figure: [sets] relative to the most
 * worked muscle in that rite ([top]). A rite is a fraction of a week, so this
 * is a share of the rite, never a verdict against the weekly range.
 */
fun riteAlpha(sets: Double, top: Double): Float {
    if (sets <= 0.0 || top <= 0.0) return 0f
    return FAINT_ALPHA + (FULL_ALPHA - FAINT_ALPHA) * (sets / top).toFloat().coerceIn(0f, 1f)
}

/**
 * The same front and back figure, filled by what ONE rite works: the emerald
 * strengthens with the sets the rite gives a muscle, relative to its most worked
 * muscle, and untouched muscles stay bare.
 */
@Composable
fun RiteMuscleMap(
    sets: Map<Muscle, Double>,
    riteName: String,
    modifier: Modifier = Modifier,
    figureHeight: Dp = 220.dp,
    selection: MutableState<Muscle?> = rememberMuscleSelection(),
) {
    val top = sets.values.maxOrNull() ?: 0.0
    val figure = BodyFigures.of(LocalBodySex.current)
    val fill = { muscle: Muscle ->
        val alpha = riteAlpha(sets[muscle] ?: 0.0, top)
        if (alpha <= 0f) IronvellumColors.Rune else IronvellumColors.Emerald.copy(alpha = alpha)
    }
    val lineFor = { muscle: Muscle -> riteMuscleLine(muscle, sets[muscle] ?: 0.0, riteName) }
    FigureBlock(figure, figureHeight, fill, selection, lineFor, modifier, quietKey = true) {
        LegendKey(IronvellumColors.Emerald.copy(alpha = riteAlpha(0.01, 1.0)), stringResource(R.string.bodymap_key_fewer))
        LegendKey(IronvellumColors.Emerald.copy(alpha = riteAlpha(1.0, 1.0)), stringResource(R.string.bodymap_key_more))
        LegendKey(IronvellumColors.Rune, stringResource(R.string.bodymap_key_none))
    }
}

/**
 * Every figure's one layout: FRONT and BACK sit inside the figure over each half, the lit muscle's line
 * appears only while one is lit, and the colour key is the single row beneath. [quietKey] hides the key
 * and labels from TalkBack where the muscle nodes and the caller's summary already say it all.
 */
@Composable
private fun FigureBlock(
    figure: BodyFigure,
    height: Dp,
    fill: (Muscle) -> Color,
    selection: MutableState<Muscle?>,
    lineFor: (Muscle) -> String,
    modifier: Modifier,
    interactive: Boolean = true,
    quietKey: Boolean = false,
    decor: ((Muscle) -> RegionLook)? = null,
    key: @Composable () -> Unit,
) {
    val quiet = if (quietKey) Modifier.clearAndSetSemantics {} else Modifier
    Column(modifier) {
        Box {
            TappableFigure(figure, height, fill, selection.value, { selection.value = it }, lineFor, interactive = interactive, decor = decor)
            // Left-aligned in each half so the word clears the head at the half's centre.
            // Capped: the words sit beside the figures' heads in half the phone's width and cannot grow with the text.
            CappedFontScale {
                Row(Modifier.fillMaxWidth().then(quiet)) {
                    FigureLabel(stringResource(R.string.bodymap_label_front), Modifier.weight(1f))
                    FigureLabel(stringResource(R.string.bodymap_label_back), Modifier.weight(1f))
                }
            }
        }
        if (interactive && selection.value != null) SelectedMuscleLine(selection.value, lineFor)
        FlowRow(
            Modifier.fillMaxWidth().padding(top = 6.dp).then(quiet),
            // From the page margin, wrapping at the margin: centred keys started off it and crowded three to a row.
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) { key() }
    }
}

internal fun DrawScope.drawFigure(
    g: FigureGeometry,
    view: FigureView,
    fill: (Muscle) -> Color,
    seed: Int,
    selected: Muscle? = null,
    outlineWidth: Dp = 1.4.dp,
    decor: ((Muscle) -> RegionLook)? = null,
) {
    val figure = g.figure

    fun polygon(points: List<Pair<Float, Float>>, side: Float = 1f) = Path().apply {
        points.forEachIndexed { i, (x, y) ->
            val p = g.toCanvas(view, x, y, side)
            if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
        }
        close()
    }

    // Body paper first, so untrained regions read as part of a figure.
    val outline = figure.fullOutline.map { (x, y) -> g.toCanvas(view, x, y) }
    drawPath(polygon(figure.fullOutline), IronvellumColors.VaultHigh)
    // Hair is decoration, in the darkest ink and never a region, so it takes no colour and no tap.
    figure.hair(view).forEach { hair ->
        for (side in listOf(1f, -1f)) drawPath(polygon(hair, side), IronvellumColors.Abyss)
    }

    figure.regions(view).forEach { region ->
        val look = decor?.invoke(region.muscle)
        val fill = look?.fill ?: fill(region.muscle)
        for (side in listOf(1f, -1f)) {
            val path = polygon(region.points, side)
            drawPath(path, fill)
            if (look != null) drawDecor(path, look)
            // A paper-coloured seam between neighbouring muscles keeps the
            // regions legible when two of them share a verdict colour.
            drawPath(path, IronvellumColors.Abyss, style = Stroke(width = 1.2.dp.toPx()))
        }
    }

    // The lit muscle: a soft green halo under a crisp ink line, on every piece and both sides, over the
    // seams so neighbours cannot cut it.
    if (selected != null) {
        figure.regionsOf(selected, view).forEach { region ->
            for (side in listOf(1f, -1f)) {
                drawPath(
                    polygon(region.points, side),
                    IronvellumColors.SystemGreen.copy(alpha = 0.45f),
                    style = Stroke(width = 7.dp.toPx(), join = StrokeJoin.Round),
                )
                inkOutline(
                    region.points.map { (x, y) -> g.toCanvas(view, x, y, side) },
                    IronvellumColors.SystemGreen,
                    2.5.dp.toPx(),
                )
            }
        }
    }

    // The outline goes on last. The skin line is already smooth, so it is
    // stroked through its points as they are.
    inkOutline(outline, IronvellumColors.InkMuted, outlineWidth.toPx())
}

/**
 * The pattern a state carries inside its region: hatching clipped to the muscle, a dashed outline, or a solid
 * heavy outline. Built from paths only, so the ruled-drawing scan stays honest.
 */
private fun DrawScope.drawDecor(region: Path, look: RegionLook) {
    val width = 1.dp.toPx()
    if (look.decor == RegionDecor.DASHED) {
        drawPath(
            region, look.ink,
            style = Stroke(width = 1.2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
        )
        return
    }
    if (look.decor == RegionDecor.NONE) return
    val box = region.getBounds()
    val gap = 4.dp.toPx()
    fun lines(rising: Boolean) = Path().apply {
        var x = box.left - box.height
        while (x < box.right) {
            if (rising) { moveTo(x, box.bottom); lineTo(x + box.height, box.top) } else { moveTo(x, box.top); lineTo(x + box.height, box.bottom) }
            x += gap
        }
    }
    clipPath(region) {
        drawPath(lines(rising = true), look.ink.copy(alpha = 0.85f), style = Stroke(width))
        if (look.decor == RegionDecor.CROSS_HATCH) drawPath(lines(rising = false), look.ink.copy(alpha = 0.85f), style = Stroke(width))
    }
    // Heavy also carries a solid outline: the pattern alone is easy to miss on a small muscle.
    if (look.decor == RegionDecor.CROSS_HATCH) drawPath(region, look.ink, style = Stroke(width = 1.6.dp.toPx(), join = StrokeJoin.Round))
}

/** [samples] (a closed curve) stroked as one round-joined path. */
private fun DrawScope.inkOutline(samples: List<Offset>, color: Color, widthPx: Float) {
    val closed = samples + samples.first()
    drawPath(Path().apply { moveTo(closed[0].x, closed[0].y); closed.drop(1).forEach { lineTo(it.x, it.y) } }, color, style = Stroke(widthPx, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** Screen-reader summary over every judged muscle: each state by name, covered ones as a count. */
private fun coverageSummary(tally: CoverageTally): String {
    fun names(muscles: List<Muscle>) = muscles.joinToString { it.label.lowercase() }
    val parts = buildList {
        if (tally.heavy.isNotEmpty()) add(Words.text(UiText.res(R.string.bodymap_summary_heavy, names(tally.heavy))))
        if (tally.notTrained.isNotEmpty()) add(Words.text(UiText.res(R.string.bodymap_summary_not_trained, names(tally.notTrained))))
        if (tally.below.isNotEmpty()) add(Words.text(UiText.res(R.string.bodymap_summary_below, names(tally.below))))
        add(Words.text(UiText.res(R.string.bodymap_summary_covered, tally.covered.size)))
    }
    return Words.text(UiText.res(R.string.bodymap_summary, parts.joinToString("; ")))
}

@Composable
private fun FigureLabel(text: String, modifier: Modifier) {
    Text(
        text,
        modifier = modifier,
        textAlign = TextAlign.Start,
        style = MaterialTheme.typography.labelSmall,
        fontFamily = ChakraPetch,
        color = IronvellumColors.InkMuted,
        letterSpacing = IronvellumTracking.InlineLabel,
    )
}

@Composable
private fun LegendKey(colour: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(colour)
                .inkBorder(IronvellumColors.InkMuted, MaterialTheme.shapes.extraSmall, 1.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            color = IronvellumColors.InkMuted,
            letterSpacing = IronvellumTracking.InlineLabel,
        )
    }
}
