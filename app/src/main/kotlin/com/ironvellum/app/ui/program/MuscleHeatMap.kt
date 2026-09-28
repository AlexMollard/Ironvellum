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
import com.ironvellum.app.ui.theme.inkBorder
import com.ironvellum.app.ui.theme.inkStroke

/**
 * Verdict for one muscle against the tier/focus weekly-set range. LIGHT is a
 * helper below its floor: shown, never a gap - the floor is a convention, and
 * a lifter chasing a goal should not be told to pad the week to meet it.
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
    CoverageLevel.UNDER -> IronvellumColors.DangerRed
    CoverageLevel.LIGHT -> IronvellumColors.InkMuted
    CoverageLevel.IN_RANGE -> IronvellumColors.Emerald
    CoverageLevel.OVER -> IronvellumColors.SovereignGold
}

/**
 * The fill for one tracked muscle. Under target the red deepens with the
 * shortfall - a muscle at 10 of 12 sets reads differently from one at 2 -
 * which is what makes the figure a heat map rather than four flat colours.
 */
private fun regionFill(level: CoverageLevel, volume: Double, target: ClosedFloatingPointRange<Double>): Color {
    val alpha = when (level) {
        CoverageLevel.NONE -> 0.55f
        CoverageLevel.UNDER -> 0.35f + 0.5f * (volume / target.start).toFloat().coerceIn(0f, 1f)
        CoverageLevel.LIGHT -> 0.35f
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

/** Every muscle the map judges: the ranged majors, then the floored helpers. */
val JUDGED: List<Muscle> = ProgramRules.TRACKED + ProgramRules.HELPERS

/**
 * The range a muscle is judged against: the goal's weekly range for the
 * majors, the helper floor with no ceiling for the helpers (see
 * ProgramRules.HELPERS) - so a pressing-heavy week is never "over" on the
 * front delts.
 */
fun rangeFor(muscle: Muscle, target: ClosedFloatingPointRange<Double>): ClosedFloatingPointRange<Double> =
    if (muscle in ProgramRules.HELPERS) ProgramRules.HELPER_RANGE else target

/** [muscle]'s verdict at [volume]: a helper below its floor, trained or not, is LIGHT. */
fun levelOf(muscle: Muscle, volume: Double, target: ClosedFloatingPointRange<Double>): CoverageLevel {
    val range = rangeFor(muscle, target)
    return if (muscle in ProgramRules.HELPERS && volume < range.start) {
        CoverageLevel.LIGHT
    } else {
        coverageLevel(volume, range)
    }
}

/** Every major muscle the week leaves under its range or untrained. Helpers are never gaps. */
fun coverageGaps(volume: Map<Muscle, Double>, target: ClosedFloatingPointRange<Double>): List<Muscle> =
    JUDGED.filter {
        levelOf(it, volume[it] ?: 0.0, target).let { l -> l == CoverageLevel.UNDER || l == CoverageLevel.NONE }
    }

private val FRONT = listOf(
    Region(Muscle.TRAPS, 0.042f to 0.122f, 0.066f to 0.129f, 0.093f to 0.136f, 0.102f to 0.141f, 0.074f to 0.143f, 0.050f to 0.138f, 0.040f to 0.130f),
    Region(Muscle.FRONT_DELTS, 0.112f to 0.146f, 0.121f to 0.153f, 0.129f to 0.161f, 0.132f to 0.168f, 0.134f to 0.176f, 0.135f to 0.183f, 0.133f to 0.191f, 0.130f to 0.198f, 0.125f to 0.206f, 0.120f to 0.206f, 0.114f to 0.198f, 0.110f to 0.191f, 0.108f to 0.183f, 0.106f to 0.176f, 0.106f to 0.168f, 0.106f to 0.161f, 0.108f to 0.153f, 0.110f to 0.146f),
    Region(Muscle.SIDE_DELTS, 0.133f to 0.150f, 0.148f to 0.159f, 0.155f to 0.167f, 0.160f to 0.175f, 0.164f to 0.184f, 0.163f to 0.193f, 0.161f to 0.201f, 0.158f to 0.209f, 0.155f to 0.218f, 0.152f to 0.218f, 0.147f to 0.209f, 0.145f to 0.201f, 0.144f to 0.193f, 0.143f to 0.184f, 0.140f to 0.175f, 0.138f to 0.167f, 0.137f to 0.159f, 0.131f to 0.150f),
    Region(Muscle.UPPER_CHEST, 0.010f to 0.151f, 0.056f to 0.144f, 0.094f to 0.152f, 0.106f to 0.169f, 0.098f to 0.172f, 0.058f to 0.167f, 0.010f to 0.169f),
    Region(Muscle.MID_CHEST, 0.010f to 0.174f, 0.060f to 0.172f, 0.100f to 0.177f, 0.107f to 0.182f, 0.098f to 0.189f, 0.056f to 0.201f, 0.010f to 0.204f),
    Region(Muscle.LOWER_CHEST, 0.010f to 0.209f, 0.056f to 0.206f, 0.098f to 0.194f, 0.101f to 0.200f, 0.088f to 0.214f, 0.058f to 0.226f, 0.010f to 0.229f),
    Region(Muscle.SERRATUS, 0.090f to 0.218f, 0.104f to 0.230f, 0.105f to 0.258f, 0.098f to 0.284f, 0.084f to 0.278f, 0.078f to 0.244f),
    Region(Muscle.BICEPS, 0.132f to 0.222f, 0.140f to 0.234f, 0.144f to 0.246f, 0.147f to 0.258f, 0.149f to 0.270f, 0.148f to 0.282f, 0.146f to 0.294f, 0.142f to 0.306f, 0.135f to 0.318f, 0.130f to 0.318f, 0.125f to 0.306f, 0.121f to 0.294f, 0.119f to 0.282f, 0.117f to 0.270f, 0.117f to 0.258f, 0.118f to 0.246f, 0.120f to 0.234f, 0.126f to 0.222f),
    Region(Muscle.BRACHIALIS, 0.162f to 0.258f, 0.165f to 0.268f, 0.166f to 0.278f, 0.166f to 0.288f, 0.165f to 0.298f, 0.164f to 0.308f, 0.162f to 0.318f, 0.160f to 0.328f, 0.158f to 0.338f, 0.156f to 0.338f, 0.153f to 0.328f, 0.153f to 0.318f, 0.153f to 0.308f, 0.154f to 0.298f, 0.155f to 0.288f, 0.156f to 0.278f, 0.157f to 0.268f, 0.159f to 0.258f),
    Region(Muscle.FOREARMS, 0.144f to 0.340f, 0.161f to 0.353f, 0.167f to 0.367f, 0.166f to 0.380f, 0.164f to 0.393f, 0.161f to 0.406f, 0.157f to 0.419f, 0.152f to 0.433f, 0.146f to 0.446f, 0.139f to 0.446f, 0.134f to 0.433f, 0.129f to 0.419f, 0.126f to 0.406f, 0.125f to 0.393f, 0.124f to 0.380f, 0.124f to 0.367f, 0.126f to 0.353f, 0.137f to 0.340f),
    Region(Muscle.ABS, 0.010f to 0.242f, 0.052f to 0.242f, 0.060f to 0.300f, 0.060f to 0.380f, 0.052f to 0.448f, 0.010f to 0.462f),
    Region(Muscle.OBLIQUES, 0.070f to 0.296f, 0.101f to 0.300f, 0.096f to 0.330f, 0.090f to 0.370f, 0.089f to 0.405f, 0.094f to 0.438f, 0.080f to 0.452f, 0.068f to 0.420f, 0.066f to 0.360f),
    Region(Muscle.HIP_FLEXORS, 0.028f to 0.466f, 0.062f to 0.464f, 0.076f to 0.474f, 0.060f to 0.488f, 0.034f to 0.488f),
    Region(Muscle.QUADS, 0.077f to 0.488f, 0.100f to 0.514f, 0.110f to 0.539f, 0.112f to 0.565f, 0.109f to 0.591f, 0.103f to 0.617f, 0.096f to 0.642f, 0.086f to 0.668f, 0.076f to 0.694f, 0.070f to 0.694f, 0.065f to 0.668f, 0.062f to 0.642f, 0.058f to 0.617f, 0.056f to 0.591f, 0.054f to 0.565f, 0.050f to 0.539f, 0.051f to 0.514f, 0.065f to 0.488f),
    Region(Muscle.ADDUCTORS, 0.025f to 0.515f, 0.039f to 0.532f, 0.046f to 0.549f, 0.049f to 0.566f, 0.049f to 0.583f, 0.048f to 0.599f, 0.047f to 0.616f, 0.045f to 0.633f, 0.043f to 0.650f, 0.040f to 0.650f, 0.036f to 0.633f, 0.032f to 0.616f, 0.029f to 0.599f, 0.027f to 0.583f, 0.025f to 0.566f, 0.020f to 0.549f, 0.016f to 0.532f, 0.020f to 0.515f),
    Region(Muscle.TIBIALIS, 0.086f to 0.730f, 0.093f to 0.747f, 0.097f to 0.765f, 0.097f to 0.782f, 0.095f to 0.800f, 0.091f to 0.818f, 0.086f to 0.835f, 0.081f to 0.853f, 0.075f to 0.870f, 0.072f to 0.870f, 0.074f to 0.853f, 0.075f to 0.835f, 0.077f to 0.818f, 0.077f to 0.800f, 0.078f to 0.782f, 0.079f to 0.765f, 0.079f to 0.747f, 0.083f to 0.730f),
    Region(Muscle.CALVES, 0.055f to 0.722f, 0.063f to 0.742f, 0.068f to 0.761f, 0.067f to 0.781f, 0.065f to 0.801f, 0.062f to 0.821f, 0.059f to 0.841f, 0.054f to 0.860f, 0.049f to 0.880f, 0.046f to 0.880f, 0.043f to 0.860f, 0.040f to 0.841f, 0.038f to 0.821f, 0.035f to 0.801f, 0.035f to 0.781f, 0.037f to 0.761f, 0.041f to 0.742f, 0.050f to 0.722f),
)

private val BACK = listOf(
    Region(Muscle.TRAPS, 0.010f to 0.118f, 0.040f to 0.122f, 0.070f to 0.132f, 0.100f to 0.142f, 0.092f to 0.150f, 0.060f to 0.152f, 0.026f to 0.168f, 0.017f to 0.230f, 0.013f to 0.290f, 0.008f to 0.305f),
    Region(Muscle.RHOMBOIDS, 0.026f to 0.176f, 0.048f to 0.162f, 0.066f to 0.176f, 0.066f to 0.214f, 0.044f to 0.240f, 0.026f to 0.236f),
    Region(Muscle.ROTATOR_CUFF, 0.068f to 0.160f, 0.096f to 0.152f, 0.110f to 0.160f, 0.106f to 0.196f, 0.078f to 0.214f, 0.068f to 0.198f),
    Region(Muscle.REAR_DELTS, 0.125f to 0.148f, 0.143f to 0.157f, 0.154f to 0.165f, 0.159f to 0.174f, 0.163f to 0.183f, 0.162f to 0.192f, 0.158f to 0.201f, 0.153f to 0.209f, 0.147f to 0.218f, 0.139f to 0.218f, 0.131f to 0.209f, 0.126f to 0.201f, 0.123f to 0.192f, 0.121f to 0.183f, 0.119f to 0.174f, 0.120f to 0.165f, 0.121f to 0.157f, 0.121f to 0.148f),
    Region(Muscle.LATS, 0.020f to 0.272f, 0.062f to 0.232f, 0.105f to 0.236f, 0.107f to 0.262f, 0.104f to 0.292f, 0.098f to 0.326f, 0.092f to 0.360f, 0.070f to 0.382f, 0.030f to 0.366f),
    Region(Muscle.TRICEPS, 0.142f to 0.218f, 0.156f to 0.233f, 0.163f to 0.247f, 0.166f to 0.262f, 0.166f to 0.276f, 0.163f to 0.290f, 0.158f to 0.305f, 0.152f to 0.320f, 0.143f to 0.334f, 0.136f to 0.334f, 0.128f to 0.320f, 0.124f to 0.305f, 0.121f to 0.290f, 0.118f to 0.276f, 0.116f to 0.262f, 0.117f to 0.247f, 0.122f to 0.233f, 0.133f to 0.218f),
    Region(Muscle.FOREARMS, 0.144f to 0.340f, 0.161f to 0.353f, 0.167f to 0.367f, 0.166f to 0.380f, 0.164f to 0.393f, 0.161f to 0.406f, 0.157f to 0.419f, 0.152f to 0.433f, 0.146f to 0.446f, 0.139f to 0.446f, 0.134f to 0.433f, 0.129f to 0.419f, 0.126f to 0.406f, 0.125f to 0.393f, 0.124f to 0.380f, 0.124f to 0.367f, 0.126f to 0.353f, 0.137f to 0.340f),
    Region(Muscle.LOWER_BACK, 0.010f to 0.376f, 0.036f to 0.380f, 0.066f to 0.398f, 0.068f to 0.446f, 0.010f to 0.456f),
    Region(Muscle.ABDUCTORS, 0.054f to 0.466f, 0.092f to 0.458f, 0.100f to 0.470f, 0.103f to 0.484f, 0.080f to 0.488f, 0.054f to 0.484f),
    Region(Muscle.GLUTES, 0.006f to 0.468f, 0.099f to 0.502f, 0.103f to 0.502f, 0.108f to 0.505f, 0.111f to 0.535f, 0.112f to 0.565f, 0.070f to 0.584f, 0.030f to 0.578f, 0.019f to 0.535f, 0.008f to 0.505f),
    Region(Muscle.HAMSTRINGS, 0.076f to 0.592f, 0.093f to 0.605f, 0.101f to 0.619f, 0.103f to 0.632f, 0.101f to 0.646f, 0.096f to 0.659f, 0.089f to 0.673f, 0.080f to 0.686f, 0.069f to 0.700f, 0.060f to 0.700f, 0.049f to 0.686f, 0.041f to 0.673f, 0.036f to 0.659f, 0.033f to 0.646f, 0.032f to 0.632f, 0.035f to 0.619f, 0.044f to 0.605f, 0.062f to 0.592f),
    Region(Muscle.CALVES, 0.069f to 0.720f, 0.088f to 0.740f, 0.097f to 0.761f, 0.096f to 0.781f, 0.093f to 0.802f, 0.087f to 0.823f, 0.079f to 0.843f, 0.070f to 0.863f, 0.059f to 0.884f, 0.052f to 0.884f, 0.047f to 0.863f, 0.042f to 0.843f, 0.038f to 0.823f, 0.035f to 0.802f, 0.033f to 0.781f, 0.034f to 0.761f, 0.042f to 0.740f, 0.059f to 0.720f),
)

/** Every muscle the figure can colour, front or back. */
internal val DRAWN: Set<Muscle> = (FRONT + BACK).map { it.muscle }.toSet()

/**
 * Right half of the silhouette, crown to crotch, in figure space: a muscular
 * male build, because the map is a lifter's. Traps rise in a curve into a
 * short neck, delts cap the shoulder, lats flare into a narrow waist over
 * straight hips, thighs and calves swell; the jaw is square and the pec
 * regions are flat plates. A hip flare and round pecs read as a mixed
 * physique on device. Head, hands and feet belong to the one outline - a
 * head circle on a flat shoulder line, wedge hands and triangle feet read as
 * a mannequin. Palms face forward (the anatomical position muscle charts
 * use), so the thumb sits on the outside of each hand.
 */
private val HALF_OUTLINE = listOf(
    0.000f to 0.000f, // crown
    0.026f to 0.004f,
    0.042f to 0.016f,
    0.050f to 0.034f,
    0.052f to 0.054f, // temple
    0.055f to 0.062f, // ear
    0.054f to 0.074f,
    0.050f to 0.080f,
    0.047f to 0.092f, // cheek
    0.042f to 0.104f, // jaw angle
    0.037f to 0.113f, // neck
    0.050f to 0.121f, // traps rise into the neck
    0.068f to 0.127f,
    0.090f to 0.133f,
    0.110f to 0.139f, // traps meet the shoulder
    0.134f to 0.145f,
    0.157f to 0.160f,
    0.168f to 0.184f, // deltoid cap
    0.167f to 0.210f,
    0.170f to 0.240f,
    0.172f to 0.275f, // upper arm
    0.168f to 0.312f,
    0.165f to 0.333f, // elbow, outer
    0.172f to 0.360f, // forearm swell
    0.171f to 0.398f,
    0.168f to 0.428f,
    0.164f to 0.452f, // wrist, outer
    0.166f to 0.466f,
    0.172f to 0.478f,
    0.179f to 0.494f,
    0.181f to 0.505f, // thumb tip
    0.176f to 0.509f,
    0.170f to 0.498f, // thumb crotch
    0.169f to 0.512f,
    0.169f to 0.545f,
    0.165f to 0.552f, // index finger
    0.161f to 0.549f,
    0.160f to 0.523f,
    0.158f to 0.556f,
    0.153f to 0.561f, // middle finger
    0.148f to 0.558f,
    0.148f to 0.525f,
    0.146f to 0.554f,
    0.141f to 0.558f, // ring finger
    0.137f to 0.555f,
    0.137f to 0.523f,
    0.135f to 0.546f,
    0.131f to 0.550f, // little finger
    0.127f to 0.546f,
    0.124f to 0.515f, // heel of the hand
    0.121f to 0.472f,
    0.121f to 0.452f, // wrist, inner
    0.117f to 0.420f,
    0.118f to 0.400f,
    0.119f to 0.365f, // forearm, inner
    0.113f to 0.332f, // elbow, inner
    0.114f to 0.290f,
    0.108f to 0.228f, // armpit
    0.113f to 0.250f, // lat flare
    0.109f to 0.290f,
    0.102f to 0.330f,
    0.096f to 0.370f,
    0.095f to 0.405f, // waist
    0.100f to 0.440f,
    0.106f to 0.470f, // hip
    0.116f to 0.520f, // thigh sweep
    0.117f to 0.580f,
    0.108f to 0.640f,
    0.094f to 0.700f, // knee, outer
    0.096f to 0.722f,
    0.103f to 0.768f, // calf, outer
    0.099f to 0.812f,
    0.088f to 0.860f,
    0.074f to 0.900f,
    0.064f to 0.922f, // ankle, outer
    0.069f to 0.938f,
    0.077f to 0.960f,
    0.083f to 0.978f, // little toe
    0.082f to 0.988f,
    0.072f to 0.993f,
    0.058f to 0.996f,
    0.044f to 0.997f, // toe line
    0.032f to 0.994f,
    0.026f to 0.986f, // big toe
    0.025f to 0.966f, // arch
    0.029f to 0.942f,
    0.034f to 0.922f, // ankle, inner
    0.030f to 0.860f,
    0.027f to 0.790f, // calf, inner
    0.034f to 0.700f, // knee, inner
    0.030f to 0.676f,
    0.020f to 0.560f, // thigh, inner
    0.000f to 0.505f, // crotch
)

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

    regions.forEach { region ->
        val sets = volume[region.muscle] ?: 0.0
        val fill = regionFill(levelOf(region.muscle, sets, target), sets, rangeFor(region.muscle, target))
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

/** Screen-reader summary over every judged muscle, each against its own range. */
private fun coverageSummary(volume: Map<Muscle, Double>, target: ClosedFloatingPointRange<Double>): String {
    val byLevel = JUDGED.groupBy { levelOf(it, volume[it] ?: 0.0, target) }
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
