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
import com.ironvellum.app.domain.TrainingFocus
import com.ironvellum.app.domain.VolumeLevel
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

/** [muscle]'s verdict at [volume]: a helper below its floor, trained or not, is LIGHT. */
fun levelOf(muscle: Muscle, volume: Double, goal: CoverageGoal): CoverageLevel {
    val range = rangeFor(muscle, goal)
    return if (muscle in ProgramRules.HELPERS && volume < range.start) {
        CoverageLevel.LIGHT
    } else {
        coverageLevel(volume, range)
    }
}

/** Every major muscle the week leaves under its range or untrained. Helpers are never gaps. */
fun coverageGaps(volume: Map<Muscle, Double>, goal: CoverageGoal): List<Muscle> =
    JUDGED.filter {
        levelOf(it, volume[it] ?: 0.0, goal).let { l -> l == CoverageLevel.UNDER || l == CoverageLevel.NONE }
    }

private val FRONT = listOf(
    Region(Muscle.TRAPS, 0.029f to 0.145f, 0.058f to 0.150f, 0.088f to 0.155f, 0.099f to 0.159f, 0.064f to 0.164f, 0.036f to 0.162f, 0.025f to 0.155f),
    Region(Muscle.FRONT_DELTS, 0.103f to 0.157f, 0.111f to 0.158f, 0.118f to 0.165f, 0.120f to 0.172f, 0.121f to 0.179f, 0.124f to 0.186f, 0.123f to 0.195f, 0.118f to 0.201f, 0.113f to 0.210f, 0.108f to 0.210f, 0.101f to 0.203f, 0.098f to 0.192f, 0.097f to 0.185f, 0.095f to 0.180f, 0.094f to 0.174f, 0.092f to 0.165f, 0.097f to 0.159f, 0.101f to 0.157f),
    Region(Muscle.SIDE_DELTS, 0.119f to 0.160f, 0.133f to 0.165f, 0.141f to 0.173f, 0.146f to 0.175f, 0.148f to 0.188f, 0.152f to 0.200f, 0.147f to 0.209f, 0.146f to 0.222f, 0.143f to 0.234f, 0.138f to 0.234f, 0.131f to 0.224f, 0.128f to 0.215f, 0.126f to 0.203f, 0.125f to 0.188f, 0.124f to 0.182f, 0.121f to 0.173f, 0.122f to 0.167f, 0.116f to 0.160f),
    Region(Muscle.UPPER_CHEST, 0.009f to 0.169f, 0.051f to 0.163f, 0.084f to 0.166f, 0.094f to 0.178f, 0.087f to 0.181f, 0.051f to 0.179f, 0.009f to 0.181f),
    Region(Muscle.MID_CHEST, 0.006f to 0.182f, 0.053f to 0.180f, 0.089f to 0.182f, 0.095f to 0.186f, 0.093f to 0.191f, 0.049f to 0.204f, 0.008f to 0.208f),
    Region(Muscle.LOWER_CHEST, 0.007f to 0.209f, 0.052f to 0.205f, 0.095f to 0.192f, 0.097f to 0.199f, 0.086f to 0.214f, 0.054f to 0.229f, 0.005f to 0.232f),
    Region(Muscle.SERRATUS, 0.076f to 0.216f, 0.090f to 0.228f, 0.095f to 0.258f, 0.078f to 0.288f, 0.066f to 0.280f, 0.066f to 0.243f),
    Region(Muscle.BICEPS, 0.119f to 0.213f, 0.129f to 0.222f, 0.135f to 0.236f, 0.143f to 0.250f, 0.143f to 0.272f, 0.143f to 0.287f, 0.144f to 0.305f, 0.139f to 0.318f, 0.131f to 0.331f, 0.126f to 0.333f, 0.118f to 0.326f, 0.113f to 0.306f, 0.111f to 0.287f, 0.103f to 0.272f, 0.103f to 0.256f, 0.095f to 0.239f, 0.103f to 0.228f, 0.112f to 0.213f),
    Region(Muscle.BRACHIALIS, 0.150f to 0.238f, 0.155f to 0.251f, 0.158f to 0.264f, 0.158f to 0.277f, 0.158f to 0.290f, 0.158f to 0.303f, 0.155f to 0.316f, 0.153f to 0.329f, 0.150f to 0.342f, 0.149f to 0.342f, 0.145f to 0.329f, 0.145f to 0.316f, 0.145f to 0.303f, 0.145f to 0.290f, 0.145f to 0.277f, 0.145f to 0.264f, 0.146f to 0.251f, 0.145f to 0.238f),
    Region(Muscle.FOREARMS, 0.135f to 0.338f, 0.154f to 0.351f, 0.159f to 0.372f, 0.158f to 0.389f, 0.157f to 0.405f, 0.157f to 0.425f, 0.150f to 0.443f, 0.144f to 0.465f, 0.135f to 0.482f, 0.126f to 0.482f, 0.121f to 0.463f, 0.115f to 0.445f, 0.110f to 0.425f, 0.111f to 0.405f, 0.112f to 0.388f, 0.113f to 0.369f, 0.115f to 0.347f, 0.128f to 0.338f),
    Region(Muscle.ABS, 0.007f to 0.236f, 0.047f to 0.238f, 0.051f to 0.292f, 0.050f to 0.381f, 0.042f to 0.454f, 0.006f to 0.469f),
    Region(Muscle.OBLIQUES, 0.055f to 0.288f, 0.087f to 0.293f, 0.082f to 0.329f, 0.077f to 0.377f, 0.075f to 0.419f, 0.079f to 0.459f, 0.065f to 0.475f, 0.054f to 0.437f, 0.051f to 0.365f),
    Region(Muscle.HIP_FLEXORS, 0.019f to 0.466f, 0.047f to 0.464f, 0.062f to 0.474f, 0.047f to 0.488f, 0.020f to 0.493f),
    Region(Muscle.QUADS, 0.065f to 0.483f, 0.091f to 0.496f, 0.096f to 0.537f, 0.098f to 0.564f, 0.096f to 0.591f, 0.093f to 0.620f, 0.089f to 0.655f, 0.078f to 0.678f, 0.069f to 0.703f, 0.063f to 0.705f, 0.058f to 0.682f, 0.053f to 0.656f, 0.048f to 0.625f, 0.048f to 0.591f, 0.047f to 0.562f, 0.043f to 0.535f, 0.043f to 0.508f, 0.055f to 0.485f),
    Region(Muscle.ADDUCTORS, 0.015f to 0.484f, 0.032f to 0.507f, 0.042f to 0.534f, 0.044f to 0.558f, 0.045f to 0.581f, 0.046f to 0.606f, 0.045f to 0.636f, 0.044f to 0.662f, 0.039f to 0.685f, 0.035f to 0.685f, 0.030f to 0.664f, 0.024f to 0.635f, 0.022f to 0.603f, 0.020f to 0.581f, 0.015f to 0.554f, 0.011f to 0.532f, 0.005f to 0.509f, 0.009f to 0.486f),
    Region(Muscle.TIBIALIS, 0.075f to 0.705f, 0.084f to 0.730f, 0.089f to 0.757f, 0.090f to 0.778f, 0.087f to 0.804f, 0.085f to 0.832f, 0.077f to 0.861f, 0.071f to 0.884f, 0.063f to 0.905f, 0.058f to 0.913f, 0.061f to 0.890f, 0.062f to 0.863f, 0.063f to 0.836f, 0.065f to 0.804f, 0.066f to 0.776f, 0.068f to 0.755f, 0.066f to 0.726f, 0.071f to 0.705f),
    Region(Muscle.CALVES, 0.051f to 0.681f, 0.061f to 0.709f, 0.067f to 0.746f, 0.065f to 0.775f, 0.063f to 0.801f, 0.062f to 0.832f, 0.060f to 0.866f, 0.051f to 0.895f, 0.045f to 0.921f, 0.040f to 0.921f, 0.035f to 0.895f, 0.032f to 0.864f, 0.027f to 0.836f, 0.028f to 0.801f, 0.028f to 0.775f, 0.029f to 0.744f, 0.032f to 0.709f, 0.045f to 0.683f),
)

private val BACK = listOf(
    Region(Muscle.TRAPS, 0.010f to 0.140f, 0.038f to 0.144f, 0.065f to 0.151f, 0.091f to 0.157f, 0.082f to 0.163f, 0.054f to 0.167f, 0.023f to 0.178f, 0.015f to 0.230f, 0.010f to 0.290f, 0.006f to 0.305f),
    Region(Muscle.RHOMBOIDS, 0.023f to 0.181f, 0.043f to 0.173f, 0.058f to 0.181f, 0.057f to 0.214f, 0.039f to 0.243f, 0.017f to 0.248f),
    Region(Muscle.ROTATOR_CUFF, 0.060f to 0.170f, 0.092f to 0.159f, 0.106f to 0.166f, 0.101f to 0.202f, 0.068f to 0.231f, 0.059f to 0.204f),
    Region(Muscle.REAR_DELTS, 0.113f to 0.159f, 0.128f to 0.165f, 0.138f to 0.171f, 0.143f to 0.174f, 0.146f to 0.183f, 0.146f to 0.192f, 0.143f to 0.201f, 0.138f to 0.209f, 0.133f to 0.218f, 0.125f to 0.218f, 0.117f to 0.209f, 0.111f to 0.201f, 0.109f to 0.192f, 0.107f to 0.185f, 0.106f to 0.178f, 0.107f to 0.172f, 0.108f to 0.166f, 0.109f to 0.159f),
    Region(Muscle.LATS, 0.014f to 0.271f, 0.054f to 0.229f, 0.095f to 0.229f, 0.091f to 0.253f, 0.087f to 0.291f, 0.081f to 0.329f, 0.077f to 0.370f, 0.057f to 0.385f, 0.022f to 0.369f),
    Region(Muscle.TRICEPS, 0.128f to 0.218f, 0.142f to 0.233f, 0.149f to 0.247f, 0.152f to 0.262f, 0.154f to 0.276f, 0.152f to 0.290f, 0.148f to 0.305f, 0.143f to 0.320f, 0.135f to 0.334f, 0.128f to 0.334f, 0.121f to 0.320f, 0.117f to 0.305f, 0.113f to 0.290f, 0.109f to 0.276f, 0.108f to 0.262f, 0.105f to 0.247f, 0.107f to 0.233f, 0.119f to 0.218f),
    Region(Muscle.FOREARMS, 0.135f to 0.340f, 0.156f to 0.342f, 0.159f to 0.364f, 0.158f to 0.378f, 0.159f to 0.393f, 0.156f to 0.411f, 0.153f to 0.433f, 0.144f to 0.449f, 0.135f to 0.462f, 0.126f to 0.462f, 0.120f to 0.449f, 0.112f to 0.433f, 0.111f to 0.411f, 0.113f to 0.393f, 0.113f to 0.378f, 0.113f to 0.360f, 0.114f to 0.338f, 0.129f to 0.340f),
    Region(Muscle.LOWER_BACK, 0.006f to 0.371f, 0.029f to 0.376f, 0.058f to 0.396f, 0.056f to 0.455f, 0.006f to 0.467f),
    Region(Muscle.ABDUCTORS, 0.039f to 0.459f, 0.079f to 0.440f, 0.082f to 0.464f, 0.087f to 0.483f, 0.065f to 0.488f, 0.040f to 0.483f),
    Region(Muscle.GLUTES, 0.005f to 0.468f, 0.082f to 0.503f, 0.089f to 0.499f, 0.092f to 0.505f, 0.096f to 0.537f, 0.098f to 0.571f, 0.060f to 0.590f, 0.025f to 0.585f, 0.013f to 0.538f, 0.007f to 0.506f),
    Region(Muscle.HAMSTRINGS, 0.065f to 0.593f, 0.088f to 0.593f, 0.095f to 0.616f, 0.091f to 0.635f, 0.089f to 0.650f, 0.087f to 0.665f, 0.083f to 0.686f, 0.073f to 0.698f, 0.061f to 0.709f, 0.053f to 0.707f, 0.038f to 0.701f, 0.031f to 0.684f, 0.028f to 0.665f, 0.027f to 0.650f, 0.026f to 0.635f, 0.025f to 0.617f, 0.037f to 0.605f, 0.054f to 0.593f),
    Region(Muscle.CALVES, 0.061f to 0.716f, 0.083f to 0.729f, 0.088f to 0.760f, 0.090f to 0.781f, 0.086f to 0.804f, 0.084f to 0.830f, 0.075f to 0.857f, 0.065f to 0.885f, 0.054f to 0.908f, 0.045f to 0.908f, 0.039f to 0.885f, 0.032f to 0.863f, 0.028f to 0.832f, 0.028f to 0.804f, 0.028f to 0.782f, 0.029f to 0.760f, 0.033f to 0.723f, 0.052f to 0.716f),
)

/** Every muscle the figure can colour, front or back. */
internal val DRAWN: Set<Muscle> = (FRONT + BACK).map { it.muscle }.toSet()

/**
 * Right half of the silhouette, crown to crotch, in figure space: a muscular
 * male build, because the map is a lifter's. A straight neck column drops
 * from the jaw before the traps curve out of it (with the traps starting at
 * the jaw, the smoothing swallowed the neck), delts cap the shoulder, lats flare into a narrow waist over
 * straight hips, thighs and calves swell; the jaw is square and the pec
 * regions are flat plates. A hip flare and round pecs read as a mixed
 * physique on device. Head, hands and feet belong to the one outline - a
 * head circle on a flat shoulder line, wedge hands and triangle feet read as
 * a mannequin. Palms face forward (the anatomical position muscle charts
 * use), so the thumb sits on the outside of each hand. The arms hang clear
 * of the lats and the waist sits well inside the shoulders: arms glued to the
 * torso over a straight-sided trunk read as one wide block, "fat" to a
 * first-time viewer.
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
    0.049f to 0.089f, // cheek
    0.046f to 0.098f, // jaw angle
    0.036f to 0.107f,
    0.031f to 0.118f, // neck
    0.031f to 0.130f,
    0.037f to 0.140f,
    0.050f to 0.146f, // traps rise into the neck
    0.067f to 0.150f,
    0.087f to 0.153f,
    0.106f to 0.156f, // traps meet the shoulder
    0.125f to 0.160f,
    0.142f to 0.167f,
    0.151f to 0.184f, // deltoid cap
    0.152f to 0.210f,
    0.156f to 0.240f,
    0.159f to 0.275f, // upper arm
    0.158f to 0.312f,
    0.155f to 0.333f, // elbow, outer
    0.161f to 0.360f, // forearm swell
    0.160f to 0.398f,
    0.157f to 0.428f,
    0.153f to 0.452f, // wrist, outer
    0.155f to 0.466f,
    0.161f to 0.478f,
    0.168f to 0.494f,
    0.170f to 0.505f, // thumb tip
    0.165f to 0.509f,
    0.159f to 0.498f, // thumb crotch
    0.158f to 0.512f,
    0.158f to 0.545f,
    0.154f to 0.552f, // index finger
    0.150f to 0.549f,
    0.149f to 0.523f,
    0.147f to 0.556f,
    0.142f to 0.561f, // middle finger
    0.137f to 0.558f,
    0.137f to 0.525f,
    0.135f to 0.554f,
    0.130f to 0.558f, // ring finger
    0.126f to 0.555f,
    0.126f to 0.523f,
    0.124f to 0.546f,
    0.120f to 0.550f, // little finger
    0.116f to 0.546f,
    0.113f to 0.515f, // heel of the hand
    0.110f to 0.472f,
    0.110f to 0.452f, // wrist, inner
    0.108f to 0.420f,
    0.110f to 0.400f,
    0.112f to 0.365f, // forearm, inner
    0.107f to 0.332f, // elbow, inner
    0.107f to 0.290f,
    0.094f to 0.228f, // armpit
    0.095f to 0.250f, // lat flare
    0.088f to 0.290f,
    0.082f to 0.330f,
    0.078f to 0.370f,
    0.077f to 0.405f, // waist
    0.080f to 0.440f,
    0.084f to 0.470f, // hip
    0.098f to 0.520f, // thigh sweep
    0.100f to 0.580f,
    0.093f to 0.640f,
    0.082f to 0.700f, // knee, outer
    0.084f to 0.722f,
    0.092f to 0.768f, // calf, outer
    0.089f to 0.812f,
    0.079f to 0.860f,
    0.067f to 0.900f,
    0.058f to 0.922f, // ankle, outer
    0.062f to 0.938f,
    0.070f to 0.960f,
    0.076f to 0.978f, // little toe
    0.075f to 0.988f,
    0.065f to 0.993f,
    0.051f to 0.996f,
    0.037f to 0.997f, // toe line
    0.030f to 0.994f,
    0.025f to 0.986f, // big toe
    0.024f to 0.966f, // arch
    0.028f to 0.942f,
    0.032f to 0.922f, // ankle, inner
    0.028f to 0.860f,
    0.025f to 0.790f, // calf, inner
    0.031f to 0.700f, // knee, inner
    0.027f to 0.676f,
    0.018f to 0.560f, // thigh, inner
    0.000f to 0.505f, // crotch
)

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
) {
    val description = coverageSummary(volume, goal)
    Column(modifier.semantics { contentDescription = description }) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(figureHeight)
                .clearAndSetSemantics {},
        ) {
            val h = size.height
            val halfWidth = size.width / 2f
            drawFigure(FRONT, Offset(halfWidth * 0.5f, 0f), h, volume, goal, seed = 11)
            drawFigure(BACK, Offset(halfWidth * 1.5f, 0f), h, volume, goal, seed = 23)
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
    goal: CoverageGoal,
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
        val fill = regionFill(levelOf(region.muscle, sets, goal), sets, rangeFor(region.muscle, goal))
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
