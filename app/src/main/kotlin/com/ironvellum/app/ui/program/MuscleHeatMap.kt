package com.ironvellum.app.ui.program

import androidx.compose.runtime.MutableState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.random.Random
import com.ironvellum.app.domain.Muscle
import com.ironvellum.app.domain.ProgramRules
import com.ironvellum.app.domain.TrainingFocus
import com.ironvellum.app.domain.VolumeLevel
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.InkStyle
import com.ironvellum.app.ui.theme.IronvellumTracking
import com.ironvellum.app.ui.theme.inkBorder

/**
 * Verdict for one muscle against the tier/focus weekly-set range. LIGHT is a
 * helper trained but below its floor: shown, never a gap - the floor is a
 * convention, and a lifter chasing a goal should not be told to pad the week
 * to meet it. A helper at zero is NONE like any muscle: a whole group left
 * untrained is a gap, not a convention.
 */
enum class CoverageLevel { NONE, UNDER, LIGHT, IN_RANGE, OVER }

/**
 * Pure colour decision. Zero volume is NONE (never trained), not UNDER - the
 * tile grid and the body fill must agree that a muscle with no sets at all is
 * a different statement from one a few sets short.
 */
fun coverageLevel(volume: Double, target: ClosedFloatingPointRange<Double>): CoverageLevel = when {
    volume <= 0.0 -> CoverageLevel.NONE
    volume < target.start -> CoverageLevel.UNDER
    volume > target.endInclusive -> CoverageLevel.OVER
    else -> CoverageLevel.IN_RANGE
}

private fun levelColor(level: CoverageLevel): Color = when (level) {
    CoverageLevel.NONE -> IronvellumColors.Bracket
    CoverageLevel.UNDER -> IronvellumColors.Emerald
    CoverageLevel.LIGHT -> IronvellumColors.InkMuted
    CoverageLevel.IN_RANGE -> IronvellumColors.Emerald
    CoverageLevel.OVER -> IronvellumColors.SovereignGold
}

/**
 * Opacity of the green for a muscle under its range. Shortfall is never
 * shown as a warning colour: the fill simply grows toward the full in-range
 * green as the week approaches the target, so 11.5 of 12 sets reads almost
 * done and 2 of 12 reads faint but still green. The floor keeps it apart
 * from the grey of an untrained muscle; it reaches the in-range opacity at
 * the target.
 */
fun underAlpha(volume: Double, target: ClosedFloatingPointRange<Double>): Float {
    val fraction = if (target.start > 0.0) (volume / target.start).toFloat().coerceIn(0f, 1f) else 1f
    return UNDER_FLOOR_ALPHA + (FULL_ALPHA - UNDER_FLOOR_ALPHA) * fraction
}

private const val FULL_ALPHA = 0.9f
private const val UNDER_FLOOR_ALPHA = 0.3f

/**
 * The fill for one tracked muscle. Under target the green deepens with
 * progress toward the range (see [underAlpha]), which is what makes the
 * figure a heat map rather than a few flat colours.
 */
private fun regionFill(level: CoverageLevel, volume: Double, target: ClosedFloatingPointRange<Double>): Color {
    val alpha = when (level) {
        CoverageLevel.NONE -> 0.55f
        CoverageLevel.UNDER -> underAlpha(volume, target)
        CoverageLevel.LIGHT -> 0.35f
        else -> FULL_ALPHA
    }
    return levelColor(level).copy(alpha = alpha)
}

/**
 * The verdict word's colour in the tile lists, matching the figure: under
 * target is a softened green (the word says UNDER, colour never scolds),
 * untrained and light stay neutral, in range green, over gold.
 */
internal fun verdictTextColour(level: CoverageLevel): Color = when (level) {
    CoverageLevel.NONE, CoverageLevel.LIGHT -> IronvellumColors.InkMuted
    CoverageLevel.UNDER -> lerp(IronvellumColors.InkMuted, IronvellumColors.SystemGreen, 0.6f)
    CoverageLevel.IN_RANGE -> IronvellumColors.SystemGreen
    CoverageLevel.OVER -> IronvellumColors.SovereignGold
}

/** Every muscle the map judges: the ranged majors, then the floored helpers. */
val JUDGED: List<Muscle> = ProgramRules.TRACKED + ProgramRules.HELPERS

/**
 * What a week is judged against: the goal's volume level and focus, and the
 * muscles the lifter prioritised (whose ceiling rises - see
 * [ProgramRules.judgedRange]).
 */
data class CoverageGoal(
    val volume: VolumeLevel,
    val focus: TrainingFocus,
    val priorities: Set<Muscle> = emptySet(),
) {
    /** The unraised weekly range, for captions. */
    val target: ClosedFloatingPointRange<Double> get() = ProgramRules.weeklySetTarget(volume, focus)
}

/** The range [muscle] is judged against under [goal]: see [ProgramRules.judgedRange]. */
fun rangeFor(muscle: Muscle, goal: CoverageGoal): ClosedFloatingPointRange<Double> =
    ProgramRules.judgedRange(muscle, goal.volume, goal.focus, goal.priorities)

/**
 * [muscle]'s verdict at [volume]. Zero sets is NONE for every muscle; a
 * helper trained but below its floor is LIGHT.
 */
fun levelOf(muscle: Muscle, volume: Double, goal: CoverageGoal): CoverageLevel {
    val range = rangeFor(muscle, goal)
    return if (muscle in ProgramRules.HELPERS && volume > 0.0 && volume < range.start) {
        CoverageLevel.LIGHT
    } else {
        coverageLevel(volume, range)
    }
}

/**
 * Every judged muscle the week leaves untrained, plus every major muscle
 * under its range. A trained helper below its floor (LIGHT) is not a gap.
 */
fun coverageGaps(volume: Map<Muscle, Double>, goal: CoverageGoal): List<Muscle> =
    JUDGED.filter {
        levelOf(it, volume[it] ?: 0.0, goal).let { l -> l == CoverageLevel.UNDER || l == CoverageLevel.NONE }
    }

/** Every muscle both bodies can colour, front or back. */
internal val DRAWN: Set<Muscle> by lazy { BodyFigures.MALE.drawn intersect BodyFigures.FEMALE.drawn }

/**
 * Front and back figures side by side in ONE Canvas of fixed [figureHeight],
 * each tracked muscle filled by its weekly coverage against [goal], plus
 * labels and a legend. One Canvas with an explicit height is deliberate: the
 * previous version gave each figure a RowScope weight inside a Column, which
 * Compose ignored, so the first figure took the whole card and the second
 * was clipped away.
 */
@Composable
fun BodyHeatMap(
    volume: Map<Muscle, Double>,
    goal: CoverageGoal,
    modifier: Modifier = Modifier,
    figureHeight: Dp = 320.dp,
    selection: MutableState<Muscle?> = rememberMuscleSelection(),
    lineFor: (Muscle) -> String = { muscleLine(it) },
) {
    val description = coverageSummary(volume, goal)
    val figure = BodyFigures.of(LocalBodySex.current)
    val fill = { muscle: Muscle ->
        val sets = volume[muscle] ?: 0.0
        regionFill(levelOf(muscle, sets, goal), sets, rangeFor(muscle, goal))
    }
    Column(modifier.semantics { contentDescription = description }) {
        TappableFigure(figure, figureHeight, fill, selection.value, { selection.value = it }, lineFor)
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            FigureLabel("FRONT", Modifier.weight(1f))
            FigureLabel("BACK", Modifier.weight(1f))
        }
        SelectedMuscleLine(selection.value, lineFor)
        Legend(Modifier.padding(top = 4.dp))
    }
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

private fun shareFill(share: Double?): Color = when {
    share == null || share <= 0.0 -> IronvellumColors.Bracket.copy(alpha = 0.55f)
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
    Column(modifier) {
        TappableFigure(figure, figureHeight, fill, selection.value, { selection.value = it }, lineFor)
        Row(Modifier.fillMaxWidth().padding(top = 4.dp).clearAndSetSemantics {}) {
            FigureLabel("FRONT", Modifier.weight(1f))
            FigureLabel("BACK", Modifier.weight(1f))
        }
        SelectedMuscleLine(selection.value, lineFor)
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp).clearAndSetSemantics {},
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(1.0 to "MAIN", 0.5 to "ASSIST", null to "NOT WORKED").forEach { (share, label) ->
                LegendKey(shareFill(share), label)
            }
        }
    }
}

/**
 * Opacity of a muscle's green on a rite's figure: [sets] relative to the most
 * worked muscle in that rite ([top]). A rite is a fraction of a week, so this
 * is a share of the rite, never a verdict against the weekly range.
 */
fun riteAlpha(sets: Double, top: Double): Float {
    if (sets <= 0.0 || top <= 0.0) return 0f
    return UNDER_FLOOR_ALPHA + (FULL_ALPHA - UNDER_FLOOR_ALPHA) * (sets / top).toFloat().coerceIn(0f, 1f)
}

/**
 * The same front and back figure, filled by what ONE rite works: the green
 * deepens with the sets the rite gives a muscle, relative to its most worked
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
        if (alpha <= 0f) IronvellumColors.Bracket.copy(alpha = 0.55f) else IronvellumColors.Emerald.copy(alpha = alpha)
    }
    val lineFor = { muscle: Muscle -> riteMuscleLine(muscle, sets[muscle] ?: 0.0, riteName) }
    Column(modifier) {
        TappableFigure(figure, figureHeight, fill, selection.value, { selection.value = it }, lineFor)
        Row(Modifier.fillMaxWidth().padding(top = 4.dp).clearAndSetSemantics {}) {
            FigureLabel("FRONT", Modifier.weight(1f))
            FigureLabel("BACK", Modifier.weight(1f))
        }
        SelectedMuscleLine(selection.value, lineFor)
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp).clearAndSetSemantics {},
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LegendKey(IronvellumColors.Emerald.copy(alpha = riteAlpha(0.01, 1.0)), "FEWER")
            LegendKey(IronvellumColors.Emerald.copy(alpha = riteAlpha(1.0, 1.0)), "MORE")
            LegendKey(IronvellumColors.Bracket.copy(alpha = 0.55f), "NONE")
        }
    }
}

internal fun DrawScope.drawFigure(
    g: FigureGeometry,
    view: FigureView,
    fill: (Muscle) -> Color,
    seed: Int,
    selected: Muscle? = null,
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
        val fill = fill(region.muscle)
        for (side in listOf(1f, -1f)) {
            val path = polygon(region.points, side)
            drawPath(path, fill)
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
                    seed,
                )
            }
        }
    }

    // The hand-drawn outline goes on last, in the app's one brush. The skin
    // line is already smooth, so the ink follows its points as they are and
    // the wander lives in the path rather than in straight chords.
    inkOutline(outline, IronvellumColors.InkMuted, 1.4.dp.toPx(), seed)
}

/**
 * [samples] (a closed curve) as ink: runs of about 26px, each stroked as one
 * path with its own weight and alpha, the joints between runs nudged
 * perpendicular to the line. The same wobble [inkStroke] gives a straight run,
 * but following the curve instead of cutting its corners.
 */
private fun DrawScope.inkOutline(samples: List<Offset>, color: Color, widthPx: Float, seed: Int) {
    val closed = samples + samples.first()
    if (!InkStyle.enabled) {
        drawPath(Path().apply { moveTo(closed[0].x, closed[0].y); closed.drop(1).forEach { lineTo(it.x, it.y) } }, color, style = Stroke(widthPx, cap = StrokeCap.Round, join = StrokeJoin.Round))
        return
    }
    val runs = ArrayList<IntRange>()
    var start = 0
    var length = 0f
    for (i in 1 until closed.size) {
        length += (closed[i] - closed[i - 1]).getDistance()
        if (length >= 26f || i == closed.lastIndex) {
            runs.add(start..i)
            start = i
            length = 0f
        }
    }
    val rng = Random(seed)
    val drift = (widthPx * 0.55f).coerceAtMost(1.6f)
    val joints = FloatArray(runs.size + 1) { (rng.nextFloat() - 0.5f) * 2f * drift }
    joints[runs.size] = joints[0]
    runs.forEachIndexed { r, run ->
        val weight = 0.5f + rng.nextFloat() * 0.5f
        val path = Path()
        for (i in run) {
            val before = closed[maxOf(i - 1, 0)]
            val after = closed[minOf(i + 1, closed.lastIndex)]
            val dx = after.x - before.x
            val dy = after.y - before.y
            val len = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
            val t = (i - run.first) / (run.last - run.first).coerceAtLeast(1).toFloat()
            val off = joints[r] * (1f - t) + joints[r + 1] * t
            val x = closed[i].x - dy / len * off
            val y = closed[i].y + dx / len * off
            if (i == run.first) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path,
            color.copy(alpha = color.alpha * (0.55f + 0.45f * weight)),
            style = Stroke(widthPx * (0.55f + 0.45f * weight), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

/** Screen-reader summary over every judged muscle, each against its own range. */
private fun coverageSummary(volume: Map<Muscle, Double>, goal: CoverageGoal): String {
    val byLevel = JUDGED.groupBy { levelOf(it, volume[it] ?: 0.0, goal) }
    fun names(muscles: List<Muscle>) = muscles.joinToString { it.label.lowercase() }
    val parts = buildList {
        byLevel[CoverageLevel.NONE]?.let { add("${names(it)} untrained") }
        byLevel[CoverageLevel.UNDER]?.let { add("${names(it)} under target") }
        byLevel[CoverageLevel.LIGHT]?.let { add("${names(it)} light") }
        byLevel[CoverageLevel.IN_RANGE]?.let { add("${names(it)} in range") }
        byLevel[CoverageLevel.OVER]?.let { add("${names(it)} over target") }
    }
    return "Muscle coverage: " + parts.joinToString("; ")
}

@Composable
private fun FigureLabel(text: String, modifier: Modifier) {
    Text(
        text,
        modifier = modifier,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.labelSmall,
        fontFamily = ChakraPetch,
        color = IronvellumColors.InkMuted,
        letterSpacing = IronvellumTracking.InlineLabel,
    )
}

@Composable
private fun Legend(modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(
            CoverageLevel.NONE to "NONE",
            CoverageLevel.UNDER to "UNDER",
            CoverageLevel.LIGHT to "LIGHT",
            CoverageLevel.IN_RANGE to "IN RANGE",
            CoverageLevel.OVER to "OVER",
        ).forEach { (level, label) ->
            val swatch = if (level == CoverageLevel.UNDER) {
                levelColor(level).copy(alpha = underAlpha(0.6, 0.0..1.0))
            } else {
                levelColor(level).copy(alpha = 0.9f)
            }
            LegendKey(swatch, label)
        }
    }
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
