package com.monarch.app.domain

/**
 * Skill movements — techniques, not work. Each skill belongs to a progression
 * line, may require a prerequisite, and carries a claim standard: the concrete
 * hold or rep count you must own before the System accepts mastery.
 */
object Skills {

    /** What a practice attempt is measured in, read off the claim standard. */
    enum class Metric { SECONDS, REPS, METRES }

    data class SkillDef(
        val name: String,
        val tier: Int,
        val line: String,
        val requires: String? = null,
        /** The bar to clear before claiming — the answer to "for how long?". */
        val standard: String,
        /** Why this technique is worth chasing. */
        val why: String,
    ) {
        /** Skills are milestones, not sets: reward scales hard with tier. */
        val xp: Int get() = tier * 120

        /**
         * Holds are timed, everything else is counted — inferred once, here.
         *
         * A standard is timed when it opens with Hold/Hang, or when its FIRST
         * figure carries a seconds suffix. Matching any "\ds" anywhere read
         * "5 single-arm negatives per side, each 5s to full hang" as a hold,
         * which asked for seconds in the journal and — once XP started reading
         * this — would have divided a rep count by the hold conversion.
         */
        val metric: Metric
            get() = when {
                standard.contains("metre", ignoreCase = true) -> Metric.METRES
                standard.startsWith("Hold", ignoreCase = true) ||
                    standard.startsWith("Hang", ignoreCase = true) -> Metric.SECONDS
                Regex("\\d+").find(standard)?.range?.first
                    ?.let { it == Regex("\\d+\\s*s\\b").find(standard)?.range?.first } == true ->
                    Metric.SECONDS
                else -> Metric.REPS
            }

        /** The number in the standard: the value a practice attempt chases. */
        val target: Int
            get() = Regex("\\d+").findAll(standard)
                .map { it.value.toInt() }
                .let { nums -> if (metric == Metric.REPS) nums.maxOrNull() else nums.firstOrNull() }
                ?: 0

        val unit: String
            get() = when (metric) {
                Metric.SECONDS -> "s"
                Metric.REPS -> "reps"
                Metric.METRES -> "m"
            }
    }

    val ALL: List<SkillDef> = listOf(
        // PULL line — bar hang to one-arm pull-up
        SkillDef(
            "Dead Hang", 1, "Pull",
            standard = "Hang 60s from a bar, arms straight, shoulders active",
            why = "Grip endurance and shoulder integrity underneath every pulling skill.",
        ),
        SkillDef(
            "Scapular Pull", 1, "Pull", requires = "Dead Hang",
            standard = "3 sets of 10 reps, full scapular depression each rep",
            why = "Trains initiating pulls from the scapula instead of the elbow.",
        ),
        SkillDef(
            "Australian Pull-up", 2, "Pull", requires = "Scapular Pull",
            standard = "3 sets of 12 reps, chest touches the bar, body straight",
            why = "Horizontal pulling volume that builds the back before bodyweight verticals.",
        ),
        SkillDef(
            "Pull-up", 3, "Pull", requires = "Australian Pull-up",
            standard = "8 clean reps, chin over bar, dead hang each rep",
            why = "The foundation every advanced pulling skill is measured against.",
        ),
        SkillDef(
            "L-sit Pull-up", 4, "Pull", requires = "Pull-up",
            standard = "5 reps holding an L-sit throughout, no swing",
            why = "Couples pulling strength with core compression under load.",
        ),
        SkillDef(
            "Archer Pull-up", 4, "Pull", requires = "Pull-up",
            standard = "6 reps per side, working arm fully straightens the other stays locked",
            why = "Shifts load onto one arm — the honest halfway point to single-arm work.",
        ),
        SkillDef(
            "Weighted Pull-up", 5, "Pull", requires = "Archer Pull-up",
            standard = "5 reps with +25 kg, chin over bar every rep",
            why = "External load builds the absolute strength ceiling of the whole line.",
        ),
        SkillDef(
            "One-Arm Negative", 4, "Pull", requires = "Archer Pull-up",
            standard = "5 single-arm negatives per side, each 5s to full hang",
            why = "Eccentric one-arm loading teaches the groove before the concentric exists.",
        ),
        SkillDef(
            "One-Arm Pull-up", 5, "Pull", requires = "One-Arm Negative",
            standard = "1 rep per side, chin over bar, no kip, free hand off wrist",
            why = "The classic proof of relative pulling strength.",
        ),
        SkillDef(
            "One-Arm Hang", 2, "Pull", requires = "Dead Hang",
            standard = "Hang 30s per arm, shoulder packed and active",
            why = "Grip and shoulder integrity for every one-arm feat above it.",
        ),
        // PUSH line — push-up and dip progressions
        SkillDef(
            "Incline Push-up", 1, "Push",
            standard = "3 sets of 15 reps, hands elevated, chest to hands",
            why = "Grooves the pressing pattern at a fraction of bodyweight.",
        ),
        SkillDef(
            "Push-up", 2, "Push", requires = "Incline Push-up",
            standard = "20 clean reps, chest to floor, hips locked in line",
            why = "The baseline press everything above it scales from.",
        ),
        SkillDef(
            "Diamond Push-up", 3, "Push", requires = "Push-up",
            standard = "15 clean reps, hands together under the sternum",
            why = "Loads the triceps and narrows the base on the way to one-arm work.",
        ),
        SkillDef(
            "Archer Push-up", 4, "Push", requires = "Diamond Push-up",
            standard = "8 reps per side, working arm bending, other arm straight",
            why = "Puts most of your bodyweight on one arm before the full one-arm press.",
        ),
        SkillDef(
            "One-Arm Negative Push-up", 4, "Push", requires = "Archer Push-up",
            standard = "5 single-arm negatives per side, each 3s down, hips square",
            why = "Eccentric rehearsal of the one-arm groove without the strength jump.",
        ),
        SkillDef(
            "One-Arm Push-up", 5, "Push", requires = "One-Arm Negative Push-up",
            standard = "3 reps per side, chest to floor, hips square, feet wide",
            why = "True unilateral press: strength plus anti-rotation control.",
        ),
        SkillDef(
            "Bench Dip", 1, "Push",
            standard = "3 sets of 15 reps, elbows to 90 degrees",
            why = "First straight-down press that wakes the triceps and elbows up.",
        ),
        SkillDef(
            "Parallel Bar Dip", 2, "Push", requires = "Bench Dip",
            standard = "10 clean reps, shoulders below elbows at the bottom",
            why = "Full-bodyweight pressing volume that builds the dip chain.",
        ),
        SkillDef(
            "Weighted Dip", 4, "Push", requires = "Parallel Bar Dip",
            standard = "5 reps with +20 kg, full depth every rep",
            why = "Overloaded pressing strength that carries into every ring and planche skill.",
        ),
        // HANDSTAND line — balance, press and its extremes
        SkillDef(
            "Wall Handstand", 1, "Handstand",
            standard = "Chest-to-wall hold, 45s unbroken, straight line",
            why = "Builds the overhead position and shoulder endurance every handstand rests on.",
        ),
        SkillDef(
            "Crow Pose", 1, "Handstand",
            standard = "Hold 30s, knees on arms, feet off floor",
            why = "First taste of carrying bodyweight on your hands — teaches wrist load and balance.",
        ),
        SkillDef(
            "Pike Press", 2, "Handstand", requires = "Crow Pose",
            standard = "10 reps, feet on floor, hips stacked over hands, head to floor",
            why = "The vertical press pattern that scales into the handstand push-up.",
        ),
        SkillDef(
            "Wall HSPU", 2, "Handstand", requires = "Pike Press",
            standard = "5 reps against the wall, full range, head to floor",
            why = "Loads the press at near-full bodyweight without needing balance.",
        ),
        SkillDef(
            "Crow → Handstand", 2, "Handstand", requires = "Crow Pose",
            standard = "3 controlled presses out of crow, no jump",
            why = "Teaches the press line and the hip-to-shoulder shift.",
        ),
        SkillDef(
            "Freestanding Handstand", 3, "Handstand", requires = "Wall Handstand",
            standard = "Hold 30s free of any support, balancing with fingers",
            why = "The gateway skill: every advanced hand-balance is built on a 30s handstand.",
        ),
        SkillDef(
            "Handstand Walk", 3, "Handstand", requires = "Freestanding Handstand",
            standard = "10 metres unbroken",
            why = "Proves you can correct balance dynamically, not just hold still.",
        ),
        SkillDef(
            "Handstand Push-up", 4, "Handstand", requires = "Wall HSPU",
            standard = "3 freestanding reps, head touching floor",
            why = "Full-bodyweight vertical press — the strongest pushing feat on the line.",
        ),
        SkillDef(
            "90-Degree Push-up", 5, "Handstand", requires = "Handstand Push-up",
            standard = "1 rep pressing from a 90° hold back to handstand",
            why = "Elite straight-arm-to-bent transition; brutal shoulder and core demand.",
        ),
        SkillDef(
            "One-Arm Handstand", 5, "Handstand", requires = "Freestanding Handstand",
            standard = "Hold 5s on one arm",
            why = "The peak of hand balance — total shoulder stability and body control.",
        ),
        SkillDef(
            "Straddle Press to Handstand", 5, "Handstand", requires = "Freestanding Handstand",
            standard = "3 presses from a straddle fold on the floor to handstand, no jump",
            why = "Straight-arm pressing strength and compression fused into one entry.",
        ),
        // LEVER line — front and back lever families
        SkillDef(
            "Front Row Hold", 1, "Lever",
            standard = "Hold 20s, body horizontal, feet supported",
            why = "Teaches the straight-arm pulling shape that levers are made of.",
        ),
        SkillDef(
            "Tuck Front Lever", 2, "Lever", requires = "Front Row Hold",
            standard = "Hold 15s, knees tucked, back flat and horizontal",
            why = "First real straight-arm lat load; builds the scapular strength for the full lever.",
        ),
        SkillDef(
            "Advanced Tuck Front Lever", 3, "Lever", requires = "Tuck Front Lever",
            standard = "Hold 12s with an open hip angle, back rounded flat",
            why = "Doubles the lever arm — the real strength jump on the line.",
        ),
        SkillDef(
            "One-Leg Front Lever", 3, "Lever", requires = "Advanced Tuck Front Lever",
            standard = "Hold 10s, one leg extended, hips level",
            why = "Adds length asymmetrically so you can load closer to the full shape.",
        ),
        SkillDef(
            "Straddle Front Lever", 4, "Lever", requires = "One-Leg Front Lever",
            standard = "Hold 10s, legs wide, body flat",
            why = "Last stop before the full lever; teaches keeping the line under real load.",
        ),
        SkillDef(
            "Front Lever", 5, "Lever", requires = "Straddle Front Lever",
            standard = "Hold 8s, body fully straight and horizontal",
            why = "The benchmark straight-arm pull — few movements demand more from lats and core.",
        ),
        SkillDef(
            "Skin the Cat", 2, "Lever", requires = "One-Arm Hang",
            standard = "3 reps, full rotation through German hang and back out",
            why = "Shoulder extension and rotation capacity that both lever lines feed on.",
        ),
        SkillDef(
            "Tuck Back Lever", 2, "Lever", requires = "Skin the Cat",
            standard = "Hold 15s, knees tucked, upside down and horizontal",
            why = "Introduces the shoulder-extension load in its gentlest shape.",
        ),
        SkillDef(
            "Advanced Tuck Back Lever", 3, "Lever", requires = "Tuck Back Lever",
            standard = "Hold 12s, hips open, back flat",
            why = "Lengthens the lever arm under control on the back side.",
        ),
        SkillDef(
            "Straddle Back Lever", 4, "Lever", requires = "Advanced Tuck Back Lever",
            standard = "Hold 10s, legs wide, body flat",
            why = "Nearly the full back lever — the last safe staging ground.",
        ),
        SkillDef(
            "Back Lever", 5, "Lever", requires = "Straddle Back Lever",
            standard = "Hold 8s, body fully straight and horizontal",
            why = "Full shoulder-extension strength few people ever own.",
        ),
        // PLANCHE line — the straight-arm push summit
        SkillDef(
            "Frog Stand", 1, "Planche",
            standard = "Hold 30s, arms straight-ish, feet off floor",
            why = "Introduces the forward lean the whole planche line depends on.",
        ),
        SkillDef(
            "Tuck Planche", 2, "Planche", requires = "Frog Stand",
            standard = "Hold 15s, arms locked straight, knees tucked",
            why = "First locked-arm planche shape — trains the scapular protraction.",
        ),
        SkillDef(
            "Advanced Tuck Planche", 3, "Planche", requires = "Tuck Planche",
            standard = "Hold 12s, back flat, hips open",
            why = "Big load increase; where planche strength really starts to build.",
        ),
        SkillDef(
            "One-Leg Planche", 3, "Planche", requires = "Advanced Tuck Planche",
            standard = "Hold 8s, one leg extended",
            why = "Bridges advanced tuck to straddle without wrecking the shape.",
        ),
        SkillDef(
            "Straddle Planche", 4, "Planche", requires = "One-Leg Planche",
            standard = "Hold 8s, legs wide, hips at shoulder height",
            why = "The planche most people top out at — extreme straight-arm pushing strength.",
        ),
        SkillDef(
            "Full Planche", 5, "Planche", requires = "Straddle Planche",
            standard = "Hold 5s, body straight and parallel to the floor",
            why = "The hardest pushing hold in calisthenics.",
        ),
        SkillDef(
            "Planche Push-up", 5, "Planche", requires = "Straddle Planche",
            standard = "3 reps from straddle planche, elbows bending past 90 degrees",
            why = "Turns the hold into dynamic pressing — strength beyond merely owning the shape.",
        ),
        // RINGS line — unstable straight-arm strength
        SkillDef(
            "Ring Support Hold", 1, "Rings",
            standard = "Hold 60s, arms locked, rings turned out, no shaking",
            why = "Stabilises the shoulders on the unstable surface every ring skill needs.",
        ),
        SkillDef(
            "Ring Row", 2, "Rings", requires = "Ring Support Hold",
            standard = "3 sets of 12 reps, body horizontal, chest to rings",
            why = "Pulling volume on rings that teaches the stabiliser reflexes early.",
        ),
        SkillDef(
            "Ring Dip", 3, "Rings", requires = "Ring Row",
            standard = "8 clean reps, shoulders below elbows, rings turned out at the top",
            why = "Pressing on unstable rings builds the shoulder control bar work never will.",
        ),
        SkillDef(
            "Ring Muscle-up", 4, "Rings", requires = "Ring Dip",
            standard = "3 reps, false grip, slow controlled transition",
            why = "Links ring pulling and pressing through the hardest transition there is.",
        ),
        SkillDef(
            "Iron Cross", 5, "Rings", requires = "Ring Muscle-up",
            standard = "Hold 5s on rings, arms fully horizontal",
            why = "Ring strength icon — enormous demand on chest, lats and elbows.",
        ),
        SkillDef(
            "One-Arm Front Lever", 5, "Rings", requires = "Front Lever",
            standard = "Hold 5s on one arm",
            why = "Front lever strength stacked onto single-arm stability.",
        ),
        // MOVEMENT line — dynamic combinations
        SkillDef(
            "Kip-up", 2, "Movement",
            standard = "3 kip-ups from flat on your back to standing",
            why = "Explosive hip drive and body awareness in one trick.",
        ),
        SkillDef(
            "Muscle-up", 3, "Movement", requires = "Pull-up",
            standard = "3 reps, transition through, any kip allowed",
            why = "Links pull and press into one movement over the bar.",
        ),
        SkillDef(
            "Strict Muscle-up", 4, "Movement", requires = "Muscle-up",
            standard = "3 reps, no kip, slow transition",
            why = "Removes momentum, exposing real transition strength.",
        ),
        SkillDef(
            "Handstand-to-Bridge", 3, "Movement", requires = "Freestanding Handstand",
            standard = "3 controlled lowerings from handstand to bridge and back up",
            why = "Spinal extension and shoulder flexibility welded to hand-balance control.",
        ),
        SkillDef(
            "Human Flag", 4, "Movement", requires = "One-Arm Hang",
            standard = "Hold 8s, body horizontal beside the pole",
            why = "Lateral core and shoulder strength that nothing else trains.",
        ),
        SkillDef(
            "Inverted Muscle-up", 5, "Movement", requires = "Strict Muscle-up",
            standard = "1 rep pulling from a hang into a handstand on the bar",
            why = "Combines pulling, transition and hand balance at the top end.",
        ),
        // LEGS line — single-leg strength and hamstrings
        SkillDef(
            "Bodyweight Squat", 1, "Legs",
            standard = "3 sets of 25 reps, full depth, heels down",
            why = "Baseline knee and hip mobility before any single-leg loading.",
        ),
        SkillDef(
            "Split Squat", 2, "Legs", requires = "Bodyweight Squat",
            standard = "3 sets of 12 reps per leg, rear knee to the floor",
            why = "Staggers the stance to load one leg at a time with balance demands.",
        ),
        SkillDef(
            "Sissy Squat", 2, "Legs", requires = "Split Squat",
            standard = "12 reps, knees forward, torso in line with thighs",
            why = "Loads the quads through the knee-forward range most people avoid.",
        ),
        SkillDef(
            "Shrimp Squat", 3, "Legs", requires = "Sissy Squat",
            standard = "8 reps per leg, rear knee kissing the floor",
            why = "Single-leg strength plus hip-flexor and quad length.",
        ),
        SkillDef(
            "Pistol Squat", 3, "Legs", requires = "Shrimp Squat",
            standard = "8 reps per leg, hamstring to calf, heel down",
            why = "The single-leg strength standard, plus ankle mobility.",
        ),
        SkillDef(
            "Dragon Squat", 4, "Legs", requires = "Pistol Squat",
            standard = "3 reps per leg, rear leg extended and off the floor",
            why = "Deepest single-leg strength demand there is.",
        ),
        SkillDef(
            "Hamstring Bridge", 1, "Legs",
            standard = "3 sets of 15 reps, heels pulled in, hips fully extended",
            why = "Wakes the hamstrings up in hip extension before eccentric knee flexion.",
        ),
        SkillDef(
            "Nordic Negative", 2, "Legs", requires = "Hamstring Bridge",
            standard = "5 negatives per set, each 5s from upright to prone",
            why = "Builds the eccentric hamstring strength the full curl is made of.",
        ),
        SkillDef(
            "Nordic Curl", 3, "Legs", requires = "Nordic Negative",
            standard = "3 reps lowering and pulling back up under control, no hands",
            why = "Eccentric hamstring strength; serious injury insurance.",
        ),
        // CORE line — compression and hanging core
        SkillDef(
            "Hollow Hold", 1, "Core",
            standard = "Hold 60s, lower back pressed flat, shoulders and feet off the floor",
            why = "The foundational body line every lever, press and handstand passes through.",
        ),
        SkillDef(
            "L-sit", 2, "Core", requires = "Hollow Hold",
            standard = "Hold 20s, legs straight and parallel to the floor",
            why = "Compression strength that unlocks V-sit, manna and press work.",
        ),
        SkillDef(
            "V-Sit", 4, "Core", requires = "L-sit",
            standard = "Hold 10s, legs above horizontal",
            why = "Extreme compression — the step toward manna.",
        ),
        SkillDef(
            "Manna", 5, "Core", requires = "V-Sit",
            standard = "Hold 5s, hips above hands, legs overhead",
            why = "Rarest compression skill in bodyweight training.",
        ),
        SkillDef(
            "Hanging Knee Raise", 1, "Core",
            standard = "3 sets of 12 reps, knees to chest, no swing",
            why = "First hanging core load that teaches the pelvis to curl under control.",
        ),
        SkillDef(
            "Hanging Leg Raise", 2, "Core", requires = "Hanging Knee Raise",
            standard = "3 sets of 10 reps, legs straight to horizontal, no swing",
            why = "Straight-leg compression from a hang — the road to toes-to-bar.",
        ),
        SkillDef(
            "Toes-to-Bar", 3, "Core", requires = "Hanging Leg Raise",
            standard = "3 sets of 8 reps, feet touch the bar, controlled descent",
            why = "Full-range hanging compression that also loads grip and lats.",
        ),
        SkillDef(
            "Dragon Flag", 4, "Core", requires = "Toes-to-Bar",
            standard = "5 controlled reps, body straight throughout",
            why = "Hardest anterior-core lever — protects your spine under everything else.",
        ),
        // MOBILITY line — positions that keep the body capable
        SkillDef(
            "Deep Squat Hold", 1, "Mobility",
            standard = "Hold 3 minutes, heels down, chest up, arms inside the knees",
            why = "Restores the resting squat: ankle, hip and knee range in one position.",
        ),
        SkillDef(
            "Pancake", 1, "Mobility",
            standard = "Chest flat to the floor in a straddle fold, hold 60s",
            why = "Straddle hip flexion that feeds straddle levers, planches and splits.",
        ),
        SkillDef(
            "Bridge", 2, "Mobility",
            standard = "Hold 60s, arms straight, shoulders over hands",
            why = "Spinal extension that offsets years of pressing and hanging volume.",
        ),
        SkillDef(
            "Front Split", 3, "Mobility", requires = "Pancake",
            standard = "Full split on the floor, both sides, hold 30s each",
            why = "End-range hip flexor and hamstring length that protects kicks and splits.",
        ),
        SkillDef(
            "Stand-to-Stand Bridge", 4, "Mobility", requires = "Bridge",
            standard = "3 reps lowering from standing to bridge and standing back up",
            why = "Dynamic spinal strength and control, not just a static pose.",
        ),
        SkillDef(
            "German Hang", 1, "Mobility",
            standard = "Hold 60s in full shoulder extension, arms behind the back",
            why = "Shoulder-extension range that skin the cat and back levers depend on.",
        ),
        SkillDef(
            "Wrist Prep", 1, "Mobility",
            standard = "10-minute routine: rocks, pulses and stretches both directions, pain-free",
            why = "Keeps wrists healthy enough to load every handstand and planche skill.",
        ),
    )

    val BY_NAME: Map<String, SkillDef> = ALL.associateBy { it.name }

    fun forName(name: String): SkillDef? = BY_NAME[name]

    val LINES: List<String> = ALL.map { it.line }.distinct()

    fun tierLabel(tier: Int): String = when (tier) {
        1 -> "I"
        2 -> "II"
        3 -> "III"
        4 -> "IV"
        5 -> "V"
        else -> "?"
    }

    /** A skill is unlocked when its prerequisite (if any) is mastered. */
    fun unlocked(def: SkillDef, mastered: Set<String>): Boolean =
        def.requires == null || def.requires in mastered

    /** Skills that become claimable the moment [name] is mastered. */
    fun unlockedBy(name: String): List<SkillDef> = ALL.filter { it.requires == name }
}
