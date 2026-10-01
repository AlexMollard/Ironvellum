package com.ironvellum.app.domain

/**
 * Skill movements — techniques, not work. Each skill belongs to a progression
 * line, may require prerequisites, and carries a claim standard: the concrete
 * hold or rep count you must own before the Ledger accepts mastery.
 */
object Skills {

    /** What a practice attempt is measured in, read off the claim standard. */
    enum class Metric { SECONDS, REPS, METRES }

    /** Claim XP per tier: a tier V milestone pays 600. */
    const val XP_PER_TIER = 120

    data class SkillDef(
        val name: String,
        val tier: Int,
        val line: String,
        /**
         * Every skill that must be mastered before this one can be claimed -
         * ALL of them. The parent on this skill's own line comes first, so a
         * view that draws one parent per skill keeps the line's shape.
         */
        val prerequisites: List<String> = emptyList(),
        /** The bar to clear before claiming — the answer to "for how long?". */
        val standard: String,
        /** Why this technique is worth chasing. */
        val why: String,
    ) {
        /**
         * The first (in-line) prerequisite, for views that draw a single
         * parent. Gating never reads this: [unlocked] checks every entry of
         * [prerequisites].
         */
        val requires: String? get() = prerequisites.firstOrNull()

        /** Skills are milestones, not sets: reward scales hard with tier. */
        val xp: Int get() = tier * XP_PER_TIER

        /**
         * Holds are timed, everything else is counted — inferred once, here.
         *
         * A standard is timed when it opens with Hold/Hang, or when its FIRST
         * figure carries a seconds suffix. Matching any "\ds" anywhere read
         * "5 single-arm negatives per side, each 5s to full hang" as a hold,
         * which asked for seconds in the journal and — once XP started reading
         * this — would have divided a rep count by the hold conversion.
         *
         * A minute-denominated standard ("Hold 3 minutes", "10-minute
         * routine") is also timed, and converted to SECONDS here rather than
         * at each call site because every consumer of a SECONDS target treats
         * the number as seconds — the target must already be in seconds.
         */
        val metric: Metric
            get() = when {
                standard.contains("metre", ignoreCase = true) -> Metric.METRES
                standard.startsWith("Hold", ignoreCase = true) ||
                    standard.startsWith("Hang", ignoreCase = true) -> Metric.SECONDS
                Regex("\\d+").find(standard)?.range?.first
                    ?.let { it == Regex("\\d+\\s*s\\b").find(standard)?.range?.first } == true ->
                    Metric.SECONDS
                minutesFigure != null -> Metric.SECONDS
                else -> Metric.REPS
            }

        /** First figure only when it carries a minutes suffix ("3 minutes", "10-minute"); null otherwise. */
        private val minutesFigure: MatchResult?
            get() = Regex("\\d+\\s*-?\\s*(minutes?|min)\\b", RegexOption.IGNORE_CASE)
                .find(standard)
                ?.takeIf { Regex("\\d+").find(standard)?.range?.first == it.range.first }

        /**
         * Figures that state a LOAD or an angle, not a rep count. "5 reps
         * with +25 kg" must chase 5, "1 rep from a 90-Degree hold" must
         * chase 1, and "5 reps at 65 percent of your bodyweight" must chase
         * 5 - taking the max digit run read the plate's number, the angle or
         * the share as the target and asked for twenty-five reps.
         */
        private val nonRepFigures = Regex(
            "\\d+[-\\s]*(?:kgs?|kilos?)(?![a-z])|\\d+[-\\s]*(?:°|degrees?)|\\d+\\s*(?:%|percent)",
            RegexOption.IGNORE_CASE,
        )

        /** The number in the standard: the value a practice attempt chases. */
        val target: Int
            get() = Regex("\\d+")
                .findAll(if (metric == Metric.REPS) nonRepFigures.replace(standard, " ") else standard)
                .map { it.value.toInt() }
                .let { nums ->
                    if (metric == Metric.REPS) nums.maxOrNull()
                    else nums.firstOrNull()?.let { if (minutesFigure != null) it * 60 else it }
                }
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
            why = "Grip endurance and shoulder integrity underneath every pulling technique.",
        ),
        SkillDef(
            "Scapular Pull", 1, "Pull", prerequisites = listOf("Dead Hang"),
            standard = "3 sets of 10 reps, full scapular depression each rep",
            why = "Trains initiating pulls from the scapula instead of the elbow.",
        ),
        SkillDef(
            "Australian Pull-up", 2, "Pull", prerequisites = listOf("Scapular Pull"),
            standard = "3 sets of 12 reps, chest touches the bar, body straight",
            why = "Horizontal pulling volume that builds the back before bodyweight verticals.",
        ),
        SkillDef(
            "Negative Pull-up", 2, "Pull", prerequisites = listOf("Australian Pull-up"),
            standard = "5 negatives, each 5s from chin over the bar to a dead hang",
            why = "The lowering half of the pull-up, owned before the lift itself - the step between rows and the full rep.",
        ),
        SkillDef(
            "Pull-up", 3, "Pull", prerequisites = listOf("Negative Pull-up"),
            standard = "8 clean reps, chin over bar, dead hang each rep",
            why = "The foundation every advanced pulling technique is measured against.",
        ),
        SkillDef(
            "L-sit Pull-up", 4, "Pull", prerequisites = listOf("Pull-up", "L-sit"),
            standard = "5 reps holding an L-sit throughout, no swing",
            why = "Couples pulling strength with core compression under load.",
        ),
        SkillDef(
            "Archer Pull-up", 4, "Pull", prerequisites = listOf("Pull-up"),
            standard = "6 reps per side, working arm fully straightens the other stays locked",
            why = "Shifts load onto one arm — the honest halfway point to single-arm work.",
        ),
        SkillDef(
            "Weighted Pull-up", 4, "Pull", prerequisites = listOf("Pull-up"),
            standard = "5 reps with a third of your bodyweight added, chin over bar every rep",
            why = "External load builds the absolute strength ceiling of the whole path, scaled to the body pulling it.",
        ),
        SkillDef(
            "One-Arm Negative", 4, "Pull", prerequisites = listOf("Archer Pull-up"),
            standard = "5 single-arm negatives per side, each 5s to full hang",
            why = "Eccentric one-arm loading teaches the groove before the concentric exists.",
        ),
        SkillDef(
            "One-Arm Pull-up", 5, "Pull", prerequisites = listOf("One-Arm Negative", "One-Arm Hang"),
            standard = "1 rep per side, chin over bar, no kip, free hand off wrist",
            why = "The classic proof of relative pulling strength.",
        ),
        SkillDef(
            "One-Arm Hang", 2, "Pull", prerequisites = listOf("Dead Hang"),
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
            "Push-up", 2, "Push", prerequisites = listOf("Incline Push-up"),
            standard = "20 clean reps, chest to floor, hips locked in line",
            why = "The baseline press everything above it scales from.",
        ),
        SkillDef(
            "Diamond Push-up", 3, "Push", prerequisites = listOf("Push-up"),
            standard = "15 clean reps, hands together under the sternum",
            why = "Loads the triceps and narrows the base on the way to one-arm work.",
        ),
        SkillDef(
            "Archer Push-up", 4, "Push", prerequisites = listOf("Diamond Push-up"),
            standard = "8 reps per side, working arm bending, other arm straight",
            why = "Puts most of your bodyweight on one arm before the full one-arm press.",
        ),
        SkillDef(
            "One-Arm Negative Push-up", 4, "Push", prerequisites = listOf("Archer Push-up"),
            standard = "5 single-arm negatives per side, each 3s down, hips square",
            why = "Eccentric rehearsal of the one-arm groove without the strength jump.",
        ),
        SkillDef(
            "One-Arm Push-up", 5, "Push", prerequisites = listOf("One-Arm Negative Push-up"),
            standard = "3 reps per side, chest to floor, hips square, feet wide",
            why = "True unilateral press: strength plus anti-rotation control.",
        ),
        SkillDef(
            "Bench Dip", 1, "Push",
            standard = "3 sets of 15 reps, elbows to 90 degrees",
            why = "First straight-down press that wakes the triceps and elbows up.",
        ),
        SkillDef(
            "Parallel Bar Support Hold", 2, "Push", prerequisites = listOf("Bench Dip"),
            standard = "Hold 45s on locked arms, shoulders pressed down, no shrug",
            why = "Owning the top of the dip isometrically before loading the full rep.",
        ),
        SkillDef(
            "Parallel Bar Dip", 3, "Push", prerequisites = listOf("Parallel Bar Support Hold"),
            standard = "10 clean reps, shoulders below elbows at the bottom",
            why = "Full-bodyweight pressing volume that builds the dip chain.",
        ),
        SkillDef(
            "Weighted Dip", 4, "Push", prerequisites = listOf("Parallel Bar Dip"),
            standard = "5 reps with a third of your bodyweight added, full depth every rep",
            why = "Overloaded pressing strength that carries into every ring and planche technique.",
        ),
        // HANDSTAND line — balance, press and its extremes
        SkillDef(
            "Wall Handstand", 1, "Handstand",
            standard = "Chest-to-wall hold, 45s unbroken, straight line",
            why = "Builds the overhead position and shoulder endurance every handstand rests on.",
        ),
        SkillDef(
            "Crow Pose", 1, "Handstand",
            standard = "Hold 30s, knees high on the upper arms, arms as straight as you can, feet off floor",
            why = "First taste of carrying bodyweight on your hands — teaches wrist load and balance.",
        ),
        SkillDef(
            "Pike Press", 2, "Handstand", prerequisites = listOf("Crow Pose"),
            standard = "10 reps, feet on floor, hips stacked over hands, head to floor",
            why = "A pike push-up, despite the name - a vertical press with the feet down, not a press to handstand. The pattern that scales into the handstand push-up.",
        ),
        SkillDef(
            "Elevated Pike Push-up", 3, "Handstand", prerequisites = listOf("Pike Press"),
            standard = "8 reps, feet on a box at hip height, hips stacked over hands, head to floor",
            why = "Raising the feet puts more of your bodyweight over the hands - the bridge from the pike to the wall.",
        ),
        SkillDef(
            "Wall HSPU Negative", 3, "Handstand", prerequisites = listOf("Elevated Pike Push-up", "Wall Handstand"),
            standard = "5 negatives, each 5s from locked arms to head on the floor, chest to the wall",
            why = "Lowers the full bodyweight press under control before you have to press it back up.",
        ),
        SkillDef(
            "Wall HSPU", 4, "Handstand", prerequisites = listOf("Wall HSPU Negative", "Wall Handstand"),
            standard = "5 reps against the wall, full range, head to floor",
            why = "Loads the press at near-full bodyweight without needing balance.",
        ),
        SkillDef(
            "Crow → Handstand", 4, "Handstand", prerequisites = listOf("Freestanding Handstand", "Crow Pose"),
            standard = "3 controlled presses out of crow, no jump",
            why = "Teaches the press line and the hip-to-shoulder shift.",
        ),
        SkillDef(
            "Freestanding Handstand", 3, "Handstand", prerequisites = listOf("Wall Handstand"),
            standard = "Hold 30s free of any support, balancing with fingers",
            why = "The gateway technique: every advanced hand-balance is built on a 30s handstand.",
        ),
        SkillDef(
            "Handstand Walk", 3, "Handstand", prerequisites = listOf("Freestanding Handstand"),
            standard = "10 metres unbroken",
            why = "Proves you can correct balance dynamically, not just hold still.",
        ),
        SkillDef(
            "Handstand Push-up", 4, "Handstand", prerequisites = listOf("Wall HSPU", "Freestanding Handstand"),
            standard = "3 freestanding reps, head touching floor",
            why = "Full-bodyweight vertical press — the strongest pushing feat on the path.",
        ),
        SkillDef(
            "90-Degree Push-up", 5, "Handstand", prerequisites = listOf("Handstand Push-up"),
            standard = "1 rep pressing from a 90° hold back to handstand",
            why = "Elite straight-arm-to-bent transition; brutal shoulder and core demand.",
        ),
        SkillDef(
            "One-Arm Handstand", 5, "Handstand", prerequisites = listOf("Freestanding Handstand"),
            standard = "Hold 5s on one arm",
            why = "The peak of hand balance — total shoulder stability and body control.",
        ),
        SkillDef(
            "Straddle Press to Handstand", 5, "Handstand", prerequisites = listOf("Freestanding Handstand", "Pancake"),
            standard = "3 presses from a straddle fold on the floor to handstand, no jump",
            why = "Straight-arm pressing strength and compression fused into one entry.",
        ),
        // LEVER line — front and back lever families
        SkillDef(
            "Front Row Hold", 1, "Lever",
            standard = "Hold 20s, body horizontal, feet supported",
            why = "A bent-arm row held at the top: shoulder blades pulled back and a rigid body line before any straight-arm lever.",
        ),
        SkillDef(
            "Tuck Front Lever", 3, "Lever", prerequisites = listOf("Front Row Hold", "Pull-up", "Hollow Hold"),
            standard = "Hold 15s, knees tucked, back flat and horizontal",
            why = "First real straight-arm lat load; builds the scapular strength for the full lever.",
        ),
        SkillDef(
            "Advanced Tuck Front Lever", 3, "Lever", prerequisites = listOf("Tuck Front Lever"),
            standard = "Hold 12s with an open hip angle, back flat",
            why = "Doubles the lever arm — the real strength jump on the path.",
        ),
        SkillDef(
            "One-Leg Front Lever", 4, "Lever", prerequisites = listOf("Advanced Tuck Front Lever"),
            standard = "Hold 10s, one leg extended, hips level",
            why = "Adds length asymmetrically so you can load closer to the full shape.",
        ),
        SkillDef(
            "Straddle Front Lever", 4, "Lever", prerequisites = listOf("One-Leg Front Lever"),
            standard = "Hold 8s, legs wide, body flat",
            why = "Last stop before the full lever; teaches keeping the line under real load.",
        ),
        SkillDef(
            "Front Lever", 5, "Lever", prerequisites = listOf("Straddle Front Lever"),
            standard = "Hold 5s, body fully straight and horizontal",
            why = "The benchmark straight-arm pull — few exercises demand more from lats and core.",
        ),
        SkillDef(
            "Skin the Cat", 2, "Lever", prerequisites = listOf("German Hang"),
            standard = "3 reps, full rotation through German hang and back out",
            why = "Shoulder extension and rotation capacity that both lever paths feed on.",
        ),
        SkillDef(
            "Tuck Back Lever", 2, "Lever", prerequisites = listOf("Skin the Cat"),
            standard = "Hold 15s, knees tucked, upside down and horizontal",
            why = "Introduces the shoulder-extension load in its gentlest shape.",
        ),
        SkillDef(
            "Advanced Tuck Back Lever", 3, "Lever", prerequisites = listOf("Tuck Back Lever"),
            standard = "Hold 12s, hips open, back flat",
            why = "Lengthens the lever arm under control on the back side.",
        ),
        SkillDef(
            "One-Leg Back Lever", 3, "Lever", prerequisites = listOf("Advanced Tuck Back Lever"),
            standard = "Hold 10s, one leg extended, hips level",
            why = "Lengthens one side of the back lever at a time - the half-step between the tucks and the straddle.",
        ),
        SkillDef(
            "Straddle Back Lever", 4, "Lever", prerequisites = listOf("One-Leg Back Lever"),
            standard = "Hold 10s, legs wide, body flat",
            why = "Nearly the full back lever — the last safe staging ground.",
        ),
        SkillDef(
            "Back Lever", 5, "Lever", prerequisites = listOf("Straddle Back Lever"),
            standard = "Hold 8s, body fully straight and horizontal",
            why = "Full shoulder-extension strength few people ever own.",
        ),
        SkillDef(
            "One-Arm Front Lever", 5, "Lever", prerequisites = listOf("Front Lever"),
            standard = "Hold 5s on one arm",
            why = "Front lever strength stacked onto single-arm stability.",
        ),
        // PLANCHE line — the straight-arm push summit
        SkillDef(
            "Frog Stand", 1, "Planche",
            standard = "Hold 30s, elbows bent, knees resting on the elbows, feet off floor",
            why = "Introduces the forward lean the whole planche path depends on.",
        ),
        SkillDef(
            "Planche Lean", 2, "Planche", prerequisites = listOf("Frog Stand", "Parallel Bar Support Hold"),
            standard = "Hold 30s, arms locked, shoulders leaned well past the wrists, feet on the floor",
            why = "The planche's straight-arm lean with the feet still down - the shoulder load arrives before the balance does.",
        ),
        SkillDef(
            "Elbow Lever", 2, "Planche", prerequisites = listOf("Frog Stand"),
            standard = "Hold 15s, body horizontal on bent arms, elbows set into the hips, feet off the floor",
            why = "A horizontal balance on bent arms: the first time your whole body floats level over the hands.",
        ),
        SkillDef(
            "Tuck Planche", 3, "Planche", prerequisites = listOf("Planche Lean"),
            standard = "Hold 15s, arms locked straight, knees tucked",
            why = "First locked-arm planche shape — trains the scapular protraction.",
        ),
        SkillDef(
            "Advanced Tuck Planche", 3, "Planche", prerequisites = listOf("Tuck Planche"),
            standard = "Hold 12s, back flat, hips open",
            why = "Big load increase; where planche strength really starts to build.",
        ),
        SkillDef(
            "One-Leg Planche", 4, "Planche", prerequisites = listOf("Advanced Tuck Planche"),
            standard = "Hold 10s, one leg extended",
            why = "Bridges advanced tuck to straddle without wrecking the shape.",
        ),
        SkillDef(
            "Straddle Planche", 4, "Planche", prerequisites = listOf("One-Leg Planche"),
            standard = "Hold 8s, legs wide, hips at shoulder height",
            why = "The planche most people top out at — extreme straight-arm pushing strength.",
        ),
        SkillDef(
            "Full Planche", 5, "Planche", prerequisites = listOf("Straddle Planche"),
            standard = "Hold 5s, body straight and parallel to the floor",
            why = "The hardest pushing hold in calisthenics.",
        ),
        SkillDef(
            "Planche Push-up", 5, "Planche", prerequisites = listOf("Straddle Planche"),
            standard = "3 reps from straddle planche, elbows bending past 90 degrees",
            why = "Turns the hold into dynamic pressing — strength beyond merely owning the shape.",
        ),
        // RINGS line — unstable straight-arm strength
        SkillDef(
            "Ring Support Hold", 1, "Rings",
            standard = "Hold 60s, arms locked, rings turned out, no shaking",
            why = "Stabilises the shoulders on the unstable surface every ring technique needs.",
        ),
        SkillDef(
            "RTO Support Hold", 2, "Rings", prerequisites = listOf("Ring Support Hold"),
            standard = "Hold 30s with the rings turned fully out, palms forward, arms locked",
            why = "Rings turned out (RTO) load the straight arm the way every cross and planche on rings will.",
        ),
        SkillDef(
            "Ring Row", 2, "Rings", prerequisites = listOf("Ring Support Hold"),
            standard = "3 sets of 12 reps, body horizontal, chest to rings",
            why = "Pulling volume on rings that teaches the stabiliser reflexes early.",
        ),
        SkillDef(
            "Ring Dip", 3, "Rings", prerequisites = listOf("Ring Support Hold", "Parallel Bar Dip"),
            standard = "8 clean reps, shoulders below elbows, rings turned out at the top",
            why = "Pressing on unstable rings builds the shoulder control bar work never will.",
        ),
        SkillDef(
            "Ring Muscle-up", 4, "Rings", prerequisites = listOf("Ring Dip", "Ring Row"),
            standard = "3 reps from a dead hang, false grip, slow controlled transition, no swing",
            why = "Links ring pulling and pressing through the hardest transition there is.",
        ),
        SkillDef(
            "Banded Iron Cross", 4, "Rings", prerequisites = listOf("RTO Support Hold", "Ring Dip"),
            standard = "Hold 10s in a cross with a resistance band taking part of the load, arms straight",
            why = "Rehearses the cross at a fraction of the load, so the elbows and chest meet it gradually.",
        ),
        SkillDef(
            "Iron Cross", 5, "Rings", prerequisites = listOf("Banded Iron Cross", "Ring Muscle-up"),
            standard = "Hold 5s on rings, arms fully horizontal",
            why = "Ring strength icon — enormous demand on chest, lats and elbows.",
        ),
        // MOVEMENT line — dynamic combinations
        SkillDef(
            "Kip-up", 2, "Movement",
            standard = "3 kip-ups from flat on your back to standing",
            why = "Explosive hip drive and body awareness in one trick.",
        ),
        SkillDef(
            "Muscle-up", 3, "Movement", prerequisites = listOf("Pull-up", "Parallel Bar Dip"),
            standard = "3 reps, transition through, any kip allowed",
            why = "Links pull and press into one movement over the bar.",
        ),
        SkillDef(
            "Strict Muscle-up", 4, "Movement", prerequisites = listOf("Muscle-up"),
            standard = "3 reps, no kip, slow transition",
            why = "Removes momentum, exposing real transition strength.",
        ),
        SkillDef(
            "Handstand-to-Bridge", 3, "Movement", prerequisites = listOf("Freestanding Handstand", "Bridge"),
            standard = "3 controlled lowerings from handstand to bridge and back up",
            why = "Spinal extension and shoulder flexibility welded to hand-balance control.",
        ),
        SkillDef(
            "Tuck Human Flag", 3, "Movement", prerequisites = listOf("Pull-up", "Pike Press"),
            standard = "Hold 8s, knees tucked, hips level with the hands on the pole",
            why = "The flag's push-pull on the pole with the lever shortened - the top arm pulls, the bottom arm presses.",
        ),
        SkillDef(
            "Human Flag", 4, "Movement", prerequisites = listOf("Tuck Human Flag"),
            standard = "Hold 8s, body horizontal beside the pole",
            why = "Lateral core and shoulder strength that nothing else trains.",
        ),
        SkillDef(
            "Inverted Muscle-up", 5, "Movement", prerequisites = listOf("Strict Muscle-up", "Freestanding Handstand"),
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
            "Split Squat", 2, "Legs", prerequisites = listOf("Bodyweight Squat"),
            standard = "3 sets of 12 reps per leg, rear knee to the floor",
            why = "Staggers the stance to load one leg at a time with balance demands.",
        ),
        SkillDef(
            "Bulgarian Split Squat", 2, "Legs", prerequisites = listOf("Split Squat"),
            standard = "3 sets of 10 reps per leg, rear foot on a bench, front heel down",
            why = "Most of the bodyweight on the front leg - the single-leg base the shrimp and the pistol build on.",
        ),
        SkillDef(
            "Supported Pistol Squat", 2, "Legs", prerequisites = listOf("Bulgarian Split Squat"),
            standard = "8 reps per leg to full depth, one hand on a post for balance",
            why = "The pistol's depth and line with the balance taken off, so strength is the only thing being trained.",
        ),
        SkillDef(
            "Sissy Squat", 2, "Legs", prerequisites = listOf("Split Squat"),
            standard = "12 reps, knees forward, torso in line with thighs",
            why = "Loads the quads through the knee-forward range most people avoid.",
        ),
        SkillDef(
            "Shrimp Squat", 3, "Legs", prerequisites = listOf("Bulgarian Split Squat"),
            standard = "8 reps per leg, rear knee kissing the floor",
            why = "Single-leg strength plus hip-flexor and quad length.",
        ),
        SkillDef(
            "Pistol Squat", 3, "Legs", prerequisites = listOf("Supported Pistol Squat"),
            standard = "8 reps per leg, hamstring to calf, heel down",
            why = "The single-leg strength standard, plus ankle mobility.",
        ),
        SkillDef(
            "Dragon Squat", 4, "Legs", prerequisites = listOf("Pistol Squat"),
            standard = "3 reps per leg, rear leg extended and off the floor",
            why = "Deepest single-leg strength demand there is.",
        ),
        SkillDef(
            "Hamstring Bridge", 1, "Legs",
            standard = "3 sets of 15 reps, heels pulled in, hips fully extended",
            why = "Wakes the hamstrings up in hip extension before eccentric knee flexion.",
        ),
        SkillDef(
            "Nordic Negative", 2, "Legs", prerequisites = listOf("Hamstring Bridge"),
            standard = "5 negatives per set, each 5s from upright to prone",
            why = "Builds the eccentric hamstring strength the full curl is made of.",
        ),
        SkillDef(
            "Nordic Curl", 4, "Legs", prerequisites = listOf("Nordic Negative"),
            standard = "3 reps lowering and pulling back up under control, no hands",
            why = "Eccentric hamstring strength; serious injury insurance.",
        ),
        // SQUAT line — the loaded barbell benchmark (Strength Level standards)
        SkillDef(
            "Back Squat", 1, "Squat",
            standard = "5 reps at your bodyweight, hips below knees every rep",
            why = "The lift every other leg technique is measured against, learned before it is loaded.",
        ),
        SkillDef(
            "Pause Squat", 2, "Squat", prerequisites = listOf("Back Squat"),
            standard = "5 reps at one and a quarter times bodyweight, paused in the hole",
            why = "The pause kills the stretch reflex, exposing squat strength instead of bounce.",
        ),
        SkillDef(
            "Heavy Squat", 3, "Squat", prerequisites = listOf("Pause Squat"),
            standard = "3 reps at one and three-quarter times bodyweight, no grinding",
            why = "Triples at intermediate load are where squatting stops being practice and becomes strength.",
        ),
        SkillDef(
            "Double-Bodyweight Squat", 4, "Squat", prerequisites = listOf("Heavy Squat"),
            standard = "1 rep at two and a quarter times bodyweight, full depth, no wraps",
            why = "Two and a quarter times bodyweight is the advanced squat - a step past the double the name remembers.",
        ),
        SkillDef(
            "Triple-Bodyweight Squat", 5, "Squat", prerequisites = listOf("Double-Bodyweight Squat"),
            standard = "1 rep at two and three-quarter times bodyweight, full depth, competition-legal",
            why = "Two and three-quarter times bodyweight is elite squatting; the name keeps the triple few Ironbound ever reach.",
        ),
        // BENCH line — the upper-body barbell benchmark (Strength Level standards)
        SkillDef(
            "Bench Press", 1, "Bench",
            standard = "5 reps at three quarters of your bodyweight, bar touches the chest",
            why = "The upper-body benchmark lift, owned with a full-range touch before it is loaded.",
        ),
        SkillDef(
            "Volume Bench Press", 2, "Bench", prerequisites = listOf("Bench Press"),
            standard = "5 reps at your bodyweight, touch and go",
            why = "Five reps at full bodyweight in one set is the pressing base the paused triples and heavy singles stand on.",
        ),
        SkillDef(
            "Paused Bench Press", 3, "Bench", prerequisites = listOf("Volume Bench Press"),
            standard = "3 reps at one and a quarter times bodyweight, dead pause on the chest",
            why = "The paused rep is the competition truth: no bounce, no stretch, all you.",
        ),
        SkillDef(
            "Heavy Bench Press", 4, "Bench", prerequisites = listOf("Paused Bench Press"),
            standard = "1 rep at one and a half times bodyweight, no bounce",
            why = "Half again your bodyweight is the bench mark that separates the Ironbound from benchers.",
        ),
        SkillDef(
            "Double-Bodyweight Bench Press", 5, "Bench", prerequisites = listOf("Heavy Bench Press"),
            standard = "1 rep at double bodyweight, paused, no bounce",
            why = "Benching your bodyweight twice is the classic elite upper-body proof.",
        ),
        // PRESS line — strict overhead strength (Strength Level standards)
        SkillDef(
            "Overhead Press", 1, "Press",
            standard = "5 reps at half your bodyweight, strict, no leg drive",
            why = "Strict overhead strength is shoulder health and pressing power in one bar.",
        ),
        SkillDef(
            "Volume Overhead Press", 2, "Press", prerequisites = listOf("Overhead Press"),
            standard = "5 reps at 65 percent of your bodyweight, strict",
            why = "Five strict reps past the novice bar turn a press you can do into a press you own.",
        ),
        SkillDef(
            "Bodyweight Overhead Press", 3, "Press", prerequisites = listOf("Volume Overhead Press"),
            standard = "3 reps at four fifths of your bodyweight, strict, no leg drive",
            why = "A strict triple at four fifths of bodyweight is intermediate pressing - the full bodyweight the name promises is the next rung.",
        ),
        SkillDef(
            "Heavy Overhead Press", 4, "Press", prerequisites = listOf("Bodyweight Overhead Press"),
            standard = "1 rep at your bodyweight, strict",
            why = "Pressing your own bodyweight overhead, strict, is the old-school strongman dividing line.",
        ),
        SkillDef(
            "Half-Again Overhead Press", 5, "Press", prerequisites = listOf("Heavy Overhead Press"),
            standard = "1 rep at one and a quarter times bodyweight, strict, no leg drive",
            why = "A quarter past bodyweight, strict, is elite overhead strength; half again, as the name says, is past even that.",
        ),
        // DEADLIFT line — the hinge benchmark (Strength Level standards)
        SkillDef(
            "Deadlift", 1, "Deadlift",
            standard = "5 reps at your bodyweight, flat back, no straps",
            why = "The purest full-body strength test there is, learned hinge-first before it is loaded.",
        ),
        SkillDef(
            "Volume Deadlift", 2, "Deadlift", prerequisites = listOf("Deadlift"),
            standard = "5 reps at one and a half times bodyweight, flat back",
            why = "Repeatable pulls past bodyweight build the back that every heavier pull rides on.",
        ),
        SkillDef(
            "Double-Bodyweight Deadlift", 3, "Deadlift", prerequisites = listOf("Volume Deadlift"),
            standard = "1 rep at double bodyweight, no hitch",
            why = "Twice bodyweight off the floor is the deadlift's rite of passage.",
        ),
        SkillDef(
            "Heavy Deadlift", 4, "Deadlift", prerequisites = listOf("Double-Bodyweight Deadlift"),
            standard = "1 rep at two and a half times bodyweight, no hitch",
            why = "Two and a half times bodyweight is years-of-work strength on the bar.",
        ),
        SkillDef(
            "Triple-Bodyweight Deadlift", 5, "Deadlift", prerequisites = listOf("Heavy Deadlift"),
            standard = "1 rep at triple bodyweight, no hitch",
            why = "Triple-bodyweight deadlifting is elite powerlifting, reached by very few who ever try.",
        ),
        // CORE line — compression and hanging core
        SkillDef(
            "Hollow Hold", 1, "Core",
            standard = "Hold 60s, lower back pressed flat, shoulders and feet off the floor",
            why = "The foundational body line every lever, press and handstand passes through.",
        ),
        SkillDef(
            "Tuck L-sit", 1, "Core", prerequisites = listOf("Hollow Hold"),
            standard = "Hold 20s, knees tucked to the chest, hips and feet off the floor",
            why = "The L-sit's support and compression with the legs folded short - where the full L starts.",
        ),
        SkillDef(
            "L-sit", 2, "Core", prerequisites = listOf("Tuck L-sit"),
            standard = "Hold 20s, legs straight and parallel to the floor",
            why = "Compression strength that unlocks V-sit, manna and press work.",
        ),
        SkillDef(
            "Straddle L-sit", 3, "Core", prerequisites = listOf("L-sit"),
            standard = "Hold 15s, legs wide and above parallel to the floor",
            why = "Legs above parallel ask the abs and hip flexors to compress harder than the L - the honest half-step to the V-sit.",
        ),
        SkillDef(
            "V-Sit", 4, "Core", prerequisites = listOf("Straddle L-sit"),
            standard = "Hold 10s on your hands, hips off the floor, legs above horizontal",
            why = "Extreme compression — the step toward manna.",
        ),
        SkillDef(
            "Manna", 5, "Core", prerequisites = listOf("V-Sit"),
            standard = "Hold 5s, hips above hands, legs overhead",
            why = "Rarest compression technique in bodyweight training.",
        ),
        SkillDef(
            "Hanging Knee Raise", 1, "Core",
            standard = "3 sets of 12 reps, knees to chest, no swing",
            why = "First hanging core load that teaches the pelvis to curl under control.",
        ),
        SkillDef(
            "Hanging Leg Raise", 2, "Core", prerequisites = listOf("Hanging Knee Raise"),
            standard = "3 sets of 10 reps, legs straight to horizontal, no swing",
            why = "Straight-leg compression from a hang — the road to toes-to-bar.",
        ),
        SkillDef(
            "Toes-to-Bar", 3, "Core", prerequisites = listOf("Hanging Leg Raise"),
            standard = "3 sets of 8 reps, feet touch the bar, controlled descent",
            why = "Full-range hanging compression that also loads grip and lats.",
        ),
        SkillDef(
            "Dragon Flag", 4, "Core", prerequisites = listOf("Toes-to-Bar"),
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
            "Pancake", 3, "Mobility",
            standard = "Chest flat to the floor in a straddle fold, hold 60s",
            why = "Straddle hip flexion that feeds straddle levers, planches and splits.",
        ),
        SkillDef(
            "Bridge", 2, "Mobility",
            standard = "Hold 60s, arms straight, shoulders over hands",
            why = "Spinal extension that offsets years of pressing and hanging volume.",
        ),
        SkillDef(
            "Half Split", 2, "Mobility",
            standard = "Hold 60s per side, front leg straight, hips square, chest folding toward the knee",
            why = "The front split's hamstring half, owned before the hips have to open as well.",
        ),
        SkillDef(
            "Front Split", 3, "Mobility", prerequisites = listOf("Half Split"),
            standard = "Full split on the floor, both sides, hold 30s each",
            why = "End-range hip flexor and hamstring length that protects kicks and splits.",
        ),
        SkillDef(
            "Stand-to-Stand Bridge", 4, "Mobility", prerequisites = listOf("Bridge"),
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
            standard = "10-minute drill: rocks, pulses and stretches both directions, pain-free",
            why = "Keeps wrists healthy enough to load every handstand and planche technique.",
        ),
    )

    val BY_NAME: Map<String, SkillDef> = ALL.associateBy { it.name }

    fun forName(name: String): SkillDef? = BY_NAME[name]

    val LINES: List<String> = ALL.map { it.line }.distinct()

    /** The barbell lines: every rung is a loaded lift, whatever its name says. */
    val GYM_LINES: Set<String> = setOf("Squat", "Bench", "Press", "Deadlift")

    /**
     * Whether a skill's movement is done with external load. A weighted
     * calisthenics milestone says so in its name; a barbell line's rungs
     * (Pause Squat, Heavy Deadlift) never do, so the line decides those.
     */
    fun isWeighted(def: SkillDef): Boolean = def.name.contains("Weighted") || def.line in GYM_LINES

    fun tierLabel(tier: Int): String = when (tier) {
        1 -> "I"
        2 -> "II"
        3 -> "III"
        4 -> "IV"
        5 -> "V"
        else -> "?"
    }

    /**
     * A skill is unlocked when every prerequisite is mastered. This gates NEW
     * claims only: a claim already on the ledger is never re-checked, so a
     * prerequisite added later cannot take an earned skill back.
     */
    fun unlocked(def: SkillDef, mastered: Set<String>): Boolean =
        def.prerequisites.all { it in mastered }

    /** Skills that list [name] among their prerequisites. */
    fun dependantsOf(name: String): List<SkillDef> = ALL.filter { name in it.prerequisites }

    /**
     * Skills that become claimable the moment [name] is mastered on top of
     * [mastered]: dependants whose other prerequisites are already met and
     * that are not themselves mastered yet.
     */
    fun unlockedBy(name: String, mastered: Set<String> = emptySet()): List<SkillDef> {
        val after = mastered + name
        return dependantsOf(name).filter { it.name !in after && unlocked(it, after) }
    }

    /**
     * Tier each skill sat at when it was last claimable at a different tier.
     * Claims made before the claim row recorded what it paid were paid at
     * THAT tier, so an unclaim must refund that figure: refunding the new
     * tier's XP would take more than was paid (a revocation) or less (a
     * farm). Entries are permanent - an old claim can surface from an
     * archive restore at any time.
     */
    private val tierWhenUnstamped: Map<String, Int> = mapOf(
        // Re-tiered by the October 2026 progression audit.
        "Weighted Pull-up" to 5,
        "Wall HSPU" to 2,
        "Crow → Handstand" to 2,
        "Tuck Front Lever" to 2,
        "One-Leg Front Lever" to 3,
        "Tuck Planche" to 2,
        "One-Leg Planche" to 3,
        "Nordic Curl" to 3,
        "Pancake" to 1,
    )

    /**
     * The XP an unclaim of [name] must take back. [stampedXp] is what the
     * claim row recorded it paid; 0 means a claim from before stamping,
     * which paid [tierWhenUnstamped] (or the current tier, if unchanged).
     */
    fun claimRefund(name: String, stampedXp: Int): Int {
        if (stampedXp > 0) return stampedXp
        val tier = tierWhenUnstamped[name] ?: forName(name)?.tier ?: return 0
        return tier * XP_PER_TIER
    }

    /**
     * Published female bodyweight-multiple bars for the gym lines, keyed by
     * skill name. The male prose in the standards names male multiples, which
     * would ceiling a female lifter out of tiers IV-V on Bench and Press - her
     * published elite sits below those bars - so the detail view renders this
     * bar for a FEMALE profile instead.
     *
     * Source: Strength Level's bodyweight-multiple standards
     * (https://strengthlevel.com/strength-standards/<lift>/kg, read
     * 2026-10-01), beginner/novice/intermediate/advanced/elite 1RM:
     * - squat: male 0.75/1.25/1.75/2.25/2.75, female 0.5/0.75/1.25/1.75/2.25
     * - bench: male 0.5/1.0/1.25/1.5/2.0, female 0.3/0.5/0.75/1.1/1.45
     * - press: male 0.35/0.55/0.8/1.05/1.35, female 0.2/0.35/0.5/0.7/0.95
     * - deadlift: male 1.0/1.5/2.0/2.5/3.25, female 0.75/1.0/1.5/2.0/2.5
     * Each female bar is the female figure at the level the male bar sits
     * at; a male bar between two levels (tier I squat and bench, the press
     * line, tier V deadlift) is interpolated between the same two female
     * levels and rounded to 0.05.
     */
    private val femaleBars: Map<String, String> = mapOf(
        // Squat line
        "Back Squat" to "0.65x bodyweight",
        "Pause Squat" to "0.75x bodyweight",
        "Heavy Squat" to "1.25x bodyweight",
        "Double-Bodyweight Squat" to "1.75x bodyweight",
        "Triple-Bodyweight Squat" to "2.25x bodyweight",
        // Bench line
        "Bench Press" to "0.4x bodyweight",
        "Volume Bench Press" to "0.5x bodyweight",
        "Paused Bench Press" to "0.75x bodyweight",
        "Heavy Bench Press" to "1.1x bodyweight",
        "Double-Bodyweight Bench Press" to "1.45x bodyweight",
        // Press line
        "Overhead Press" to "0.3x bodyweight",
        "Volume Overhead Press" to "0.4x bodyweight",
        "Bodyweight Overhead Press" to "0.5x bodyweight",
        "Heavy Overhead Press" to "0.65x bodyweight",
        "Half-Again Overhead Press" to "0.85x bodyweight",
        // Deadlift line
        "Deadlift" to "0.75x bodyweight",
        "Volume Deadlift" to "1x bodyweight",
        "Double-Bodyweight Deadlift" to "1.5x bodyweight",
        "Heavy Deadlift" to "2x bodyweight",
        "Triple-Bodyweight Deadlift" to "2.35x bodyweight",
    )

    /**
     * The published female bar for a loaded-lift skill, ready to render;
     * null when the skill has no sex-specific bar. Claims stay honours-based:
     * this informs the lifter, it never gates or spends anything.
     */
    fun femaleStandard(name: String): String? = femaleBars[name]
}
