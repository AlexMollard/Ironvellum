package com.ironvellum.app.ui.titles

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.ironvellum.app.R
import com.ironvellum.app.domain.ArtRef
import com.ironvellum.app.domain.ExerciseArtIndex
import com.ironvellum.app.ui.components.ExerciseAnimation
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.METAL_RIM
import com.ironvellum.app.ui.theme.METAL_WASH
import com.ironvellum.app.ui.theme.Metal
import com.ironvellum.app.ui.theme.emeraldRamp
import com.ironvellum.app.ui.theme.inkBorder

/** Bundled, technique-specific art shared by tree nodes and the About preview. */
@DrawableRes
internal fun techniqueArtwork(name: String): Int? = when (name) {
    "Archer Pull-up" -> R.drawable.skill_archer_pull_up
    "One-Arm Negative" -> R.drawable.skill_one_arm_negative
    "One-Arm Pull-up" -> R.drawable.skill_one_arm_pull_up
    "One-Arm Hang" -> R.drawable.skill_one_arm_hang
    "One-Arm Negative Push-up" -> R.drawable.skill_one_arm_negative_push_up
    "One-Arm Push-up" -> R.drawable.skill_one_arm_push_up
    "Parallel Bar Support Hold" -> R.drawable.skill_parallel_bar_support_hold
    "Wall Handstand" -> R.drawable.skill_wall_handstand
    "Crow Pose" -> R.drawable.skill_crow_pose
    "Pike Press" -> R.drawable.skill_pike_press
    "Wall HSPU Negative" -> R.drawable.skill_wall_hspu_negative
    "Crow → Handstand" -> R.drawable.skill_crow_to_handstand
    "Freestanding Handstand" -> R.drawable.skill_freestanding_handstand
    "Handstand Walk" -> R.drawable.skill_handstand_walk
    "90-Degree Push-up" -> R.drawable.skill_90_degree_push_up
    "One-Arm Handstand" -> R.drawable.skill_one_arm_handstand
    "Straddle Press to Handstand" -> R.drawable.skill_straddle_press_to_handstand
    "Front Row Hold" -> R.drawable.skill_front_row_hold
    "Tuck Front Lever" -> R.drawable.skill_tuck_front_lever
    "Advanced Tuck Front Lever" -> R.drawable.skill_advanced_tuck_front_lever
    "One-Leg Front Lever" -> R.drawable.skill_one_leg_front_lever
    "Straddle Front Lever" -> R.drawable.skill_straddle_front_lever
    "Front Lever" -> R.drawable.skill_front_lever
    "Skin the Cat" -> R.drawable.skill_skin_the_cat
    "Tuck Back Lever" -> R.drawable.skill_tuck_back_lever
    "Advanced Tuck Back Lever" -> R.drawable.skill_advanced_tuck_back_lever
    "One-Leg Back Lever" -> R.drawable.skill_one_leg_back_lever
    "Straddle Back Lever" -> R.drawable.skill_straddle_back_lever
    "Back Lever" -> R.drawable.skill_back_lever
    "One-Arm Front Lever" -> R.drawable.skill_one_arm_front_lever
    "Frog Stand" -> R.drawable.skill_frog_stand
    "Planche Lean" -> R.drawable.skill_planche_lean
    "Elbow Lever" -> R.drawable.skill_elbow_lever
    "Tuck Planche" -> R.drawable.skill_tuck_planche
    "Advanced Tuck Planche" -> R.drawable.skill_advanced_tuck_planche
    "One-Leg Planche" -> R.drawable.skill_one_leg_planche
    "Straddle Planche" -> R.drawable.skill_straddle_planche
    "Full Planche" -> R.drawable.skill_full_planche
    "Planche Push-up" -> R.drawable.skill_planche_push_up
    "Ring Support Hold" -> R.drawable.skill_ring_support_hold
    "RTO Support Hold" -> R.drawable.skill_rto_support_hold
    "Ring Row" -> R.drawable.skill_ring_row
    "Ring Dip" -> R.drawable.skill_ring_dip
    "Ring Muscle-up" -> R.drawable.skill_ring_muscle_up
    "Banded Iron Cross" -> R.drawable.skill_banded_iron_cross
    "Iron Cross" -> R.drawable.skill_iron_cross
    "Kip-up" -> R.drawable.skill_kip_up
    "Muscle-up" -> R.drawable.skill_muscle_up
    "Strict Muscle-up" -> R.drawable.skill_strict_muscle_up
    "Handstand-to-Bridge" -> R.drawable.skill_handstand_to_bridge
    "Tuck Human Flag" -> R.drawable.skill_tuck_human_flag
    "Human Flag" -> R.drawable.skill_human_flag
    "Inverted Muscle-up" -> R.drawable.skill_inverted_muscle_up
    "Dragon Squat" -> R.drawable.skill_dragon_squat
    "Hamstring Bridge" -> R.drawable.skill_hamstring_bridge
    "Nordic Negative" -> R.drawable.skill_nordic_negative
    "Pause Squat" -> R.drawable.skill_pause_squat
    "Heavy Squat" -> R.drawable.skill_heavy_squat
    "Double-Bodyweight Squat" -> R.drawable.skill_double_bodyweight_squat
    "Triple-Bodyweight Squat" -> R.drawable.skill_triple_bodyweight_squat
    "Volume Bench Press" -> R.drawable.skill_volume_bench_press
    "Paused Bench Press" -> R.drawable.skill_paused_bench_press
    "Heavy Bench Press" -> R.drawable.skill_heavy_bench_press
    "Double-Bodyweight Bench Press" -> R.drawable.skill_double_bodyweight_bench_press
    "Volume Overhead Press" -> R.drawable.skill_volume_overhead_press
    "Bodyweight Overhead Press" -> R.drawable.skill_bodyweight_overhead_press
    "Heavy Overhead Press" -> R.drawable.skill_heavy_overhead_press
    "Half-Again Overhead Press" -> R.drawable.skill_half_again_overhead_press
    "Volume Deadlift" -> R.drawable.skill_volume_deadlift
    "Double-Bodyweight Deadlift" -> R.drawable.skill_double_bodyweight_deadlift
    "Heavy Deadlift" -> R.drawable.skill_heavy_deadlift
    "Triple-Bodyweight Deadlift" -> R.drawable.skill_triple_bodyweight_deadlift
    "Tuck L-sit" -> R.drawable.skill_tuck_l_sit
    "Straddle L-sit" -> R.drawable.skill_straddle_l_sit
    "V-Sit" -> R.drawable.skill_v_sit
    "Manna" -> R.drawable.skill_manna
    "Toes-to-Bar" -> R.drawable.skill_toes_to_bar
    "Deep Squat Hold" -> R.drawable.skill_deep_squat_hold
    "Pancake" -> R.drawable.skill_pancake
    "Bridge" -> R.drawable.skill_bridge
    "Half Split" -> R.drawable.skill_half_split
    "Front Split" -> R.drawable.skill_front_split
    "Stand-to-Stand Bridge" -> R.drawable.skill_stand_to_stand_bridge
    "German Hang" -> R.drawable.skill_german_hang
    "Wrist Prep" -> R.drawable.skill_wrist_prep
    else -> null
}

/**
 * The animated drawing a technique uses in place of a still picture (see [ExerciseArtIndex.forTechnique]). A technique has
 * one or the other: this where the upstream drawing is the same movement, [techniqueArtwork] where it is not.
 */
internal fun techniqueAnimation(name: String): ArtRef? = ExerciseArtIndex.forTechnique(name)

/** Where a technique stands for the lifter, as its art shows it: earned, open to claim, or out of reach. */
internal enum class ArtState { MASTERED, OPEN, LOCKED }

/** The state's own metal at [alpha]: the gold ramp earned, the emerald ramp open, Iron locked. The same ring the tree draws. */
internal fun artBrush(state: ArtState, alpha: Float = 1f): Brush = when (state) {
    ArtState.MASTERED -> Metal.Fabled.bounds(alpha)
    ArtState.OPEN -> emeraldRamp(alpha)
    ArtState.LOCKED -> Metal.Common.bounds(alpha)
}

/**
 * A locked technique's engraving drained of its parchment and dimmed, so the art says "not yet" in the
 * tree and the sheet alike instead of looking merely faded.
 */
internal fun artFilter(state: ArtState): ColorFilter? =
    if (state == ArtState.LOCKED) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) else null

internal fun artAlpha(state: ArtState): Float = if (state == ArtState.LOCKED) 0.5f else 1f

/**
 * A technique's engraving set in a plate rather than left floating on the sheet: a dark paper ground under a
 * faint wash of the state's metal, and a gradient rim. No floor shadow: hangs, levers and holds have no
 * floor, and a shadow under them read as the figure floating.
 * The plate takes the art's own shape (most are 2:3 portraits), never squarer than 1:1.
 */
@Composable
internal fun TechniquePlate(@DrawableRes artwork: Int, state: ArtState, modifier: Modifier = Modifier) {
    val painter = painterResource(artwork)
    val ratio = painter.intrinsicSize.let { if (it.height > 0f) it.width / it.height else 2f / 3f }.coerceIn(2f / 3f, 1f)
    val shape = MaterialTheme.shapes.medium
    Box(
        modifier
            .aspectRatio(ratio)
            .clip(shape)
            .background(IronvellumColors.Abyss)
            // A flat wash of the state's metal behind the figure; none for a locked one.
            .then(if (state != ArtState.LOCKED) Modifier.background(artBrush(state, METAL_WASH)) else Modifier)
            .inkBorder(artBrush(state, METAL_RIM), shape, 1.dp),
    ) {
        Image(
            painter = painter,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            colorFilter = artFilter(state),
            alpha = artAlpha(state),
            modifier = Modifier.fillMaxSize().padding(10.dp),
        )
    }
}

/** [TechniquePlate] for a technique that has an animated drawing: the same plate, the figure moving inside it. */
@Composable
internal fun TechniqueAnimationPlate(animation: ArtRef, state: ArtState, modifier: Modifier = Modifier) {
    val shape = MaterialTheme.shapes.medium
    Box(
        modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(IronvellumColors.Abyss)
            // The same flat wash and gradient rim as [TechniquePlate]; none for a locked one.
            .then(if (state != ArtState.LOCKED) Modifier.background(artBrush(state, METAL_WASH)) else Modifier)
            .inkBorder(artBrush(state, METAL_RIM), shape, 1.dp),
    ) {
        ExerciseAnimation(animation, Modifier.fillMaxSize().padding(10.dp), alpha = artAlpha(state))
    }
}
