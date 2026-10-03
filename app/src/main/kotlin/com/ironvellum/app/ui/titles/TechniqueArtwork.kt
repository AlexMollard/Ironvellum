package com.ironvellum.app.ui.titles

import androidx.annotation.DrawableRes
import com.ironvellum.app.R

/** Bundled, technique-specific art shared by tree nodes and the About preview. */
@DrawableRes
internal fun techniqueArtwork(name: String): Int? = when (name) {
    "Dead Hang" -> R.drawable.skill_dead_hang
    "Scapular Pull" -> R.drawable.skill_scapular_pull
    "Australian Pull-up" -> R.drawable.skill_australian_pull_up
    "Negative Pull-up" -> R.drawable.skill_negative_pull_up
    "Pull-up" -> R.drawable.skill_pull_up
    "L-sit Pull-up" -> R.drawable.skill_l_sit_pull_up
    "Archer Pull-up" -> R.drawable.skill_archer_pull_up
    "Weighted Pull-up" -> R.drawable.skill_weighted_pull_up
    "One-Arm Negative" -> R.drawable.skill_one_arm_negative
    "One-Arm Pull-up" -> R.drawable.skill_one_arm_pull_up
    "One-Arm Hang" -> R.drawable.skill_one_arm_hang
    "Incline Push-up" -> R.drawable.skill_incline_push_up
    "Push-up" -> R.drawable.skill_push_up
    "Diamond Push-up" -> R.drawable.skill_diamond_push_up
    "Archer Push-up" -> R.drawable.skill_archer_push_up
    "One-Arm Negative Push-up" -> R.drawable.skill_one_arm_negative_push_up
    "One-Arm Push-up" -> R.drawable.skill_one_arm_push_up
    "Bench Dip" -> R.drawable.skill_bench_dip
    "Parallel Bar Support Hold" -> R.drawable.skill_parallel_bar_support_hold
    "Parallel Bar Dip" -> R.drawable.skill_parallel_bar_dip
    "Weighted Dip" -> R.drawable.skill_weighted_dip
    "Wall Handstand" -> R.drawable.skill_wall_handstand
    "Crow Pose" -> R.drawable.skill_crow_pose
    "Pike Press" -> R.drawable.skill_pike_press
    "Elevated Pike Push-up" -> R.drawable.skill_elevated_pike_push_up
    "Wall HSPU Negative" -> R.drawable.skill_wall_hspu_negative
    "Wall HSPU" -> R.drawable.skill_wall_hspu
    "Crow → Handstand" -> R.drawable.skill_crow_to_handstand
    "Freestanding Handstand" -> R.drawable.skill_freestanding_handstand
    "Handstand Walk" -> R.drawable.skill_handstand_walk
    "Handstand Push-up" -> R.drawable.skill_handstand_push_up
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
    "Bodyweight Squat" -> R.drawable.skill_bodyweight_squat
    "Split Squat" -> R.drawable.skill_split_squat
    "Bulgarian Split Squat" -> R.drawable.skill_bulgarian_split_squat
    "Supported Pistol Squat" -> R.drawable.skill_supported_pistol_squat
    "Sissy Squat" -> R.drawable.skill_sissy_squat
    "Shrimp Squat" -> R.drawable.skill_shrimp_squat
    "Pistol Squat" -> R.drawable.skill_pistol_squat
    "Dragon Squat" -> R.drawable.skill_dragon_squat
    "Hamstring Bridge" -> R.drawable.skill_hamstring_bridge
    "Nordic Negative" -> R.drawable.skill_nordic_negative
    "Nordic Curl" -> R.drawable.skill_nordic_curl
    "Back Squat" -> R.drawable.skill_back_squat
    "Pause Squat" -> R.drawable.skill_pause_squat
    "Heavy Squat" -> R.drawable.skill_heavy_squat
    "Double-Bodyweight Squat" -> R.drawable.skill_double_bodyweight_squat
    "Triple-Bodyweight Squat" -> R.drawable.skill_triple_bodyweight_squat
    "Bench Press" -> R.drawable.skill_bench_press
    "Volume Bench Press" -> R.drawable.skill_volume_bench_press
    "Paused Bench Press" -> R.drawable.skill_paused_bench_press
    "Heavy Bench Press" -> R.drawable.skill_heavy_bench_press
    "Double-Bodyweight Bench Press" -> R.drawable.skill_double_bodyweight_bench_press
    "Overhead Press" -> R.drawable.skill_overhead_press
    "Volume Overhead Press" -> R.drawable.skill_volume_overhead_press
    "Bodyweight Overhead Press" -> R.drawable.skill_bodyweight_overhead_press
    "Heavy Overhead Press" -> R.drawable.skill_heavy_overhead_press
    "Half-Again Overhead Press" -> R.drawable.skill_half_again_overhead_press
    "Deadlift" -> R.drawable.skill_deadlift
    "Volume Deadlift" -> R.drawable.skill_volume_deadlift
    "Double-Bodyweight Deadlift" -> R.drawable.skill_double_bodyweight_deadlift
    "Heavy Deadlift" -> R.drawable.skill_heavy_deadlift
    "Triple-Bodyweight Deadlift" -> R.drawable.skill_triple_bodyweight_deadlift
    "Hollow Hold" -> R.drawable.skill_hollow_hold
    "Tuck L-sit" -> R.drawable.skill_tuck_l_sit
    "L-sit" -> R.drawable.skill_l_sit
    "Straddle L-sit" -> R.drawable.skill_straddle_l_sit
    "V-Sit" -> R.drawable.skill_v_sit
    "Manna" -> R.drawable.skill_manna
    "Hanging Knee Raise" -> R.drawable.skill_hanging_knee_raise
    "Hanging Leg Raise" -> R.drawable.skill_hanging_leg_raise
    "Toes-to-Bar" -> R.drawable.skill_toes_to_bar
    "Dragon Flag" -> R.drawable.skill_dragon_flag
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
