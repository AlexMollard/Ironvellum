package com.ironvellum.app.ui.program

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ironvellum.app.domain.Muscle
import com.ironvellum.app.domain.ProgramRules
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.IronvellumTracking
import com.ironvellum.app.ui.theme.inkArc
import com.ironvellum.app.ui.theme.inkBorder
import com.ironvellum.app.ui.theme.inkStroke

/** Verdict for one muscle against the tier/focus weekly-set range. */
enum class CoverageLevel { NONE, UNDER, IN_RANGE, OVER }

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
    CoverageLevel.UNDER -> IronvellumColors.DangerRed
    CoverageLevel.IN_RANGE -> IronvellumColors.Emerald
    CoverageLevel.OVER -> IronvellumColors.SovereignGold
}

/**
 * The fill for one tracked muscle. Under target the red deepens with the
 * shortfall - a muscle at 10 of 12 sets reads differently from one at 2 -
 * which is what makes the figure a heat map rather than four flat colours.
 */
private fun regionFill(volume: Double, target: ClosedFloatingPointRange<Double>): Color {
    val level = coverageLevel(volume, target)
    val alpha = when (level) {
        CoverageLevel.NONE -> 0.55f
        CoverageLevel.UNDER -> 0.35f + 0.5f * (volume / target.start).toFloat().coerceIn(0f, 1f)
        else -> 0.9f
    }
    return levelColor(level).copy(alpha = alpha)
}

/**
 * One muscle region: half of it, in figure space, mirrored across the
 * midline to draw both sides. Figure space is proportioned by height: x runs
 * from the midline (0) outwards in units of the figure's height, y from the
 * crown (0) to the soles (1), so the shapes keep their anatomy at any size.
 */
private class Region(val muscle: Muscle, vararg points: Pair<Float, Float>) {
    val points: List<Pair<Float, Float>> = points.toList()
}

/** Muscles drawn but never judged against a range (see [untrackedFill]). */
val UNJUDGED: List<Muscle> = Muscle.entries - ProgramRules.TRACKED.toSet()

/**
 * Every muscle the week leaves short: tracked ones under their range or
 * untrained, plus unjudged ones no set touches at all. The unjudged have no
 * range to fall under, but a week that never works the lower back or
 * forearms is still a gap worth flagging.
 */
fun coverageGaps(volume: Map<Muscle, Double>, target: ClosedFloatingPointRange<Double>): List<Muscle> =
    ProgramRules.TRACKED.filter {
        coverageLevel(volume[it] ?: 0.0, target).let { l -> l == CoverageLevel.UNDER || l == CoverageLevel.NONE }
    } + UNJUDGED.filter { (volume[it] ?: 0.0) <= 0.0 }

/**
 * Muscles drawn but not judged: ProgramRules does not track them (front
 * delts ride on every press, forearms, adductors and lower back ride along
 * as indirect work), so a worked one takes a neutral grey instead of a
 * verdict that would call pressing-heavy weeks "over" on the front delts.
 * The grey is lighter than NONE's so a worked forearm never reads as
 * "missing"; with no sets at all it IS missing, and takes NONE's fill.
 */
private fun untrackedFill(volume: Double): Color =
    if (volume > 0.0) IronvellumColors.InkMuted.copy(alpha = 0.35f) else regionFill(0.0, 1.0..1.0)

private val FRONT = listOf(
    Region(Muscle.FRONT_DELTS, 0.072f to 0.132f, 0.118f to 0.140f, 0.136f to 0.170f, 0.128f to 0.205f, 0.103f to 0.196f, 0.088f to 0.165f),
    Region(Muscle.SIDE_DELTS, 0.120f to 0.140f, 0.146f to 0.158f, 0.158f to 0.196f, 0.148f to 0.228f, 0.133f to 0.207f, 0.138f to 0.172f),
    Region(Muscle.CHEST, 0.012f to 0.150f, 0.070f to 0.141f, 0.098f to 0.170f, 0.103f to 0.212f, 0.072f to 0.243f, 0.012f to 0.238f),
    Region(Muscle.BICEPS, 0.116f to 0.228f, 0.148f to 0.232f, 0.156f to 0.280f, 0.146f to 0.322f, 0.122f to 0.318f, 0.112f to 0.272f),
    Region(Muscle.FOREARMS, 0.121f to 0.338f, 0.160f to 0.338f, 0.166f to 0.395f, 0.154f to 0.448f, 0.131f to 0.448f, 0.121f to 0.392f),
    Region(Muscle.ABS, 0.012f to 0.256f, 0.058f to 0.254f, 0.066f to 0.330f, 0.060f to 0.430f, 0.012f to 0.455f),
    Region(Muscle.QUADS, 0.034f to 0.520f, 0.100f to 0.488f, 0.114f to 0.560f, 0.104f to 0.640f, 0.083f to 0.690f, 0.050f to 0.690f, 0.040f to 0.618f),
    Region(Muscle.ADDUCTORS, 0.008f to 0.500f, 0.030f to 0.518f, 0.038f to 0.615f, 0.027f to 0.640f, 0.010f to 0.560f),
    Region(Muscle.CALVES, 0.084f to 0.730f, 0.098f to 0.790f, 0.086f to 0.868f, 0.060f to 0.868f, 0.050f to 0.800f, 0.060f to 0.742f),
)

private val BACK = listOf(
    Region(Muscle.UPPER_BACK, 0.012f to 0.112f, 0.042f to 0.108f, 0.100f to 0.138f, 0.098f to 0.168f, 0.058f to 0.228f, 0.012f to 0.268f),
    Region(Muscle.REAR_DELTS, 0.102f to 0.140f, 0.136f to 0.150f, 0.155f to 0.190f, 0.142f to 0.218f, 0.112f to 0.192f),
    Region(Muscle.LATS, 0.020f to 0.272f, 0.062f to 0.232f, 0.100f to 0.192f, 0.110f to 0.240f, 0.100f to 0.318f, 0.070f to 0.378f, 0.030f to 0.362f),
    Region(Muscle.TRICEPS, 0.116f to 0.220f, 0.150f to 0.222f, 0.160f to 0.270f, 0.150f to 0.320f, 0.125f to 0.320f, 0.113f to 0.262f),
    Region(Muscle.FOREARMS, 0.121f to 0.338f, 0.160f to 0.338f, 0.166f to 0.395f, 0.154f to 0.448f, 0.131f to 0.448f, 0.121f to 0.392f),
    Region(Muscle.LOWER_BACK, 0.012f to 0.370f, 0.034f to 0.376f, 0.070f to 0.392f, 0.074f to 0.448f, 0.012f to 0.460f),
    Region(Muscle.GLUTES, 0.008f to 0.470f, 0.080f to 0.460f, 0.114f to 0.500f, 0.110f to 0.560f, 0.070f to 0.580f, 0.010f to 0.572f),
    Region(Muscle.HAMSTRINGS, 0.020f to 0.592f, 0.070f to 0.590f, 0.108f to 0.582f, 0.100f to 0.660f, 0.084f to 0.700f, 0.042f to 0.700f, 0.026f to 0.650f),
    Region(Muscle.CALVES, 0.036f to 0.730f, 0.090f to 0.730f, 0.098f to 0.790f, 0.085f to 0.870f, 0.055f to 0.880f, 0.036f to 0.800f),
)

/** Right half of the silhouette, crown-side neck to crotch, in figure space. */
private val HALF_OUTLINE = listOf(
    0.030f to 0.104f, // neck
    0.070f to 0.126f, // trapezius slope
    0.128f to 0.142f, // shoulder
    0.154f to 0.170f,
    0.162f to 0.225f, // upper arm, outer
    0.166f to 0.330f, // elbow, outer
    0.171f to 0.398f, // forearm, outer
    0.158f to 0.458f, // wrist
    0.164f to 0.500f, // hand
    0.140f to 0.522f,
    0.126f to 0.470f, // wrist, inner
    0.118f to 0.400f, // forearm, inner
    0.113f to 0.330f, // elbow, inner
    0.108f to 0.228f, // armpit
    0.104f to 0.300f, // flank
    0.092f to 0.400f, // waist
    0.112f to 0.475f, // hip
    0.118f to 0.560f, // thigh, outer
    0.092f to 0.700f, // knee, outer
    0.099f to 0.785f, // calf, outer
    0.068f to 0.930f, // ankle, outer
    0.084f to 0.978f, // foot
    0.026f to 0.985f,
    0.030f to 0.930f, // ankle, inner
    0.030f to 0.785f, // calf, inner
    0.034f to 0.700f, // knee, inner
    0.020f to 0.560f, // thigh, inner
    0.000f to 0.505f, // crotch
)

private const val HEAD_CY = 0.056f
private const val HEAD_R = 0.046f

/**
 * Front and back figures side by side in ONE Canvas of fixed [figureHeight],
 * each tracked muscle filled by its weekly coverage against [target], plus
 * labels and a legend. One Canvas with an explicit height is deliberate: the
 * previous version gave each figure a RowScope weight inside a Column, which
 * Compose ignored, so the first figure took the whole card and the second
 * was clipped away.
 */
@Composable
fun BodyHeatMap(
    volume: Map<Muscle, Double>,
    target: ClosedFloatingPointRange<Double>,
    modifier: Modifier = Modifier,
    figureHeight: Dp = 320.dp,
) {
    val description = coverageSummary(volume, target)
    Column(modifier.semantics { contentDescription = description }) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(figureHeight)
                .clearAndSetSemantics {},
        ) {
            val h = size.height
            val halfWidth = size.width / 2f
            drawFigure(FRONT, Offset(halfWidth * 0.5f, 0f), h, volume, target, seed = 11)
            drawFigure(BACK, Offset(halfWidth * 1.5f, 0f), h, volume, target, seed = 23)
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            FigureLabel("FRONT", Modifier.weight(1f))
            FigureLabel("BACK", Modifier.weight(1f))
        }
        Legend(Modifier.padding(top = 8.dp))
    }
}

private fun DrawScope.drawFigure(
    regions: List<Region>,
    origin: Offset,
    height: Float,
    volume: Map<Muscle, Double>,
    target: ClosedFloatingPointRange<Double>,
    seed: Int,
) {
    fun at(x: Float, y: Float) = Offset(origin.x + x * height, origin.y + y * height)

    // Body paper first, so untrained regions read as part of a figure.
    val body = Path().apply {
        val right = HALF_OUTLINE
        val left = HALF_OUTLINE.reversed().map { (x, y) -> -x to y }
        smoothClosed(this, (right + left).map { (x, y) -> at(x, y) })
    }
    drawPath(body, IronvellumColors.VaultHigh)

    val tracked = ProgramRules.TRACKED.toSet()
    regions.forEach { region ->
        val sets = volume[region.muscle] ?: 0.0
        val fill = if (region.muscle in tracked) regionFill(sets, target) else untrackedFill(sets)
        for (side in listOf(1f, -1f)) {
            val path = Path()
            smoothClosed(path, region.points.map { (x, y) -> at(x * side, y) })
            drawPath(path, fill)
            // A paper-coloured seam between neighbouring muscles keeps the
            // regions legible when two of them share a verdict colour.
            drawPath(path, IronvellumColors.Abyss, style = Stroke(width = 1.2.dp.toPx()))
        }
    }

    // The hand-drawn outline goes on last, in the app's one brush.
    val outline = HALF_OUTLINE + HALF_OUTLINE.reversed().map { (x, y) -> -x to y }
    (outline + outline.first()).zipWithNext().forEachIndexed { i, (a, b) ->
        inkStroke(
            from = at(a.first, a.second),
            to = at(b.first, b.second),
            color = IronvellumColors.InkMuted,
            widthPx = 1.4.dp.toPx(),
            seed = seed + i,
            taperEnds = false,
        )
    }
    inkArc(
        center = at(0f, HEAD_CY),
        radius = HEAD_R * height,
        startDeg = 0f,
        sweepDeg = 360f,
        color = IronvellumColors.InkMuted,
        widthPx = 1.4.dp.toPx(),
        seed = seed,
        taperEnds = false,
    )
}

/**
 * Closed, rounded outline through [points]: quadratic curves between edge
 * midpoints with each vertex as control point. Straight polygons read as a
 * wireframe; rounded ones read as muscle.
 */
private fun smoothClosed(path: Path, points: List<Offset>) {
    if (points.size < 3) return
    fun mid(a: Offset, b: Offset) = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)
    val start = mid(points.last(), points.first())
    path.moveTo(start.x, start.y)
    points.indices.forEach { i ->
        val p = points[i]
        val next = points[(i + 1) % points.size]
        val m = mid(p, next)
        path.quadraticTo(p.x, p.y, m.x, m.y)
    }
    path.close()
}

/**
 * Screen-reader summary over the TRACKED muscles only - the untracked ones
 * are drawn but never judged, so announcing them "untrained" would be false.
 */
private fun coverageSummary(volume: Map<Muscle, Double>, target: ClosedFloatingPointRange<Double>): String {
    val byLevel = ProgramRules.TRACKED.groupBy { coverageLevel(volume[it] ?: 0.0, target) }
    fun names(muscles: List<Muscle>) = muscles.joinToString { it.label.lowercase() }
    val parts = buildList {
        (byLevel[CoverageLevel.NONE].orEmpty() + UNJUDGED.filter { (volume[it] ?: 0.0) <= 0.0 })
            .takeIf { it.isNotEmpty() }?.let { add("${names(it)} untrained") }
        byLevel[CoverageLevel.UNDER]?.let { add("${names(it)} under target") }
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
            CoverageLevel.IN_RANGE to "IN RANGE",
            CoverageLevel.OVER to "OVER",
        ).forEach { (level, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(levelColor(level).copy(alpha = 0.9f))
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
    }
}
