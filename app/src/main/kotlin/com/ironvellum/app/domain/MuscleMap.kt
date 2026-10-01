package com.ironvellum.app.domain

/**
 * Per-muscle contribution of every catalogue movement.
 *
 * THE SCALE (evidence brief section 9.4): a share is the sets one set
 * credits the muscle, judged against weekly targets calibrated to direct
 * sets, so 1.0 is the ceiling. Anchored on the fractional-set method of
 * Pelland 2026, which compared 1 / 0.5 / 0 and found the fractional model
 * strongest. Any value from 0 to 1 is valid; these are the common steps:
 *  - 1.0 prime mover: the muscle the set is for, loaded hard (often long);
 *  - 0.75 worked hard but not the lead, with evidence it grows nearly as
 *    much as the lead (squat adductors at depth - Kubo 2019);
 *  - 0.5 meaningful helper: assists, or works at short length / submaximally;
 *  - 0.25 minor synergist or stabiliser: a real but small stimulus;
 *  - 0 explicitly not credited (hamstrings from squats - Kubo 2019).
 * A value off these steps carries a short reason comment where it is set:
 * usually a trial measured it, as in the bench/push-up press family (pecs
 * 1.0, front delts 0.7, triceps 0.6, side delts 0.3 - Lanza 2024).
 * Consumers treat a share of 0.5 or more as "trains the muscle"
 * (generator fills, frequency) and 0.7 or more as main work on the figure.
 *
 * WHAT THE NUMBERS ARE: the 1.0/0.5/0 levels have meta-analytic endorsement;
 * every per-exercise share beyond that is an ESTIMATE from small MRI/US
 * trials (Lanza 2024, Kinoshita 2023, Maeo 2021, Plotkin 2023) or movement
 * anatomy, not a measured effect size. EMG tables were deliberately not used
 * - acute sEMG amplitude does not predict longitudinal hypertrophy
 * (Vigotsky 2022), and Plotkin 2023 is the live counter-example (hip thrust
 * won every EMG site yet only matched the squat for growth).
 *
 * THE SPLIT MUSCLES are movement anatomy on the same 1.0 / 0.5 scale: the
 * pec's clavicular head flexes the shoulder (incline presses), its costal
 * fibres pull the arm down toward the hips (dips), and a flat press or fly
 * works the sternal middle with both neighbours assisting. TRAPS is the
 * whole trapezius: its lower fibres pull the blade down in every pull-up,
 * its middle fibres squeeze it back with the rhomboids in every row, its
 * upper fibres shrug, hold a loaded hinge and upward-rotate overhead (0.5
 * each; 1.0 on shrugs and the prone Y raise). Rows lead with the rhomboids. Serratus
 * protracts and upward-rotates the blade (push-ups, overhead and handstand
 * pressing). The rotator cuff holds the ball of the shoulder in its socket
 * whenever the arm works overhead or pulls under load - pull-ups, rows,
 * overhead and handstand presses, raises (0.5) - and is the mover in
 * external rotation (1.0). The brachialis
 * flexes the elbow in every grip. Map order matters: the FIRST 1.0 entry is
 * the movement's dominant muscle for redundancy and stretch credit.
 */
data class ExerciseProfile(
    val muscles: Map<Muscle, Double>,
    val pattern: MovementPattern,
    val compound: Boolean,
    /**
     * True when the movement loads its target at LONG muscle length - the
     * selection the lengthened-position evidence favours (Maeo 2021, Maeo
     * 2022, Kassiano 2023, Pedrosa 2022, Wolf 2025, Kubo 2019).
     */
    val stretchBias: Boolean,
)

object MuscleMap {

    private fun key(exerciseName: String) = exerciseName.trim().lowercase()

    /**
     * Case-insensitive lookup. Every catalogue row has a profile; null means
     * a user-created movement, which improve() passes through untouched.
     * Holds, activities, load-priced milestones and metre skills are profiled
     * so the lifter sees what they work and so they count toward the
     * coverage of his own presets and logs, but the generator never
     * prescribes them ([ProgramGenerator.eligible]) and improve() leaves
     * them as the lifter wrote them.
     */
    fun profile(exerciseName: String): ExerciseProfile? = profiles[key(exerciseName)]

    /**
     * [exerciseName]'s profile as the lifter actually performs it. Modifiers
     * that change the angle or range move credit; ones that change the load
     * (weighted, assisted, banded, tempo, paused) do not - the same muscles
     * move, just harder or easier.
     *
     * - Angle, on a flat chest movement (sternal 1.0, both neighbours 0.5):
     *   an incline raises the clavicular chest to 1.0 and leaves the rest as
     *   they were - an incline press grew the upper chest more and the other
     *   sites equally (Chaves 2020). A decline raises the costal chest the
     *   same way; no trial measured that one, it mirrors the incline. On a
     *   push-up the body moves, not the bench, so the words flip: feet
     *   "elevated" (or "decline") is the incline, hands up ("incline") the
     *   decline. Both directions at once cancel.
     * - "deficit" extends the bottom of the range, so the movement loads its
     *   target long ([ExerciseProfile.stretchBias]).
     */
    fun profile(exerciseName: String, modifiers: String): ExerciseProfile? {
        val base = profile(exerciseName) ?: return null
        val words = Regex("[a-z]+").findAll(modifiers.lowercase()).map { it.value }.toSet()
        if (words.isEmpty()) return base
        val flatChest = base.muscles[Muscle.MID_CHEST] == 1.0 &&
            base.muscles[Muscle.UPPER_CHEST] == 0.5 && base.muscles[Muscle.LOWER_CHEST] == 0.5
        val bodyMoves = "push-up" in key(exerciseName)
        val up = if (bodyMoves) "elevated" in words || "decline" in words else "incline" in words
        val down = if (bodyMoves) "incline" in words else "decline" in words
        var result = base
        if (flatChest && up != down) result = angled(result, if (up) Muscle.UPPER_CHEST else Muscle.LOWER_CHEST)
        if ("deficit" in words) result = result.copy(stretchBias = true)
        return result
    }

    /** An entry's profile with its modifiers applied - see the two-argument [profile]. */
    fun profile(entry: PlannedEntry): ExerciseProfile? = profile(entry.exerciseName, entry.modifiers)

    /** Every profiled movement, lowercased - exposed for the coverage tests. */
    val keys: Set<String> get() = profiles.keys

    /**
     * Skill-tree movements that are TECHNIQUE practice - balance, transitions,
     * one-arm and muscle-up work - rather than a dose a strength or muscle
     * week can lean on. On device a beginner hypertrophy week opened with
     * "Crow -> Handstand" as its overhead press and "Skin the Cat" as its lat
     * builder. Only a SKILL focus may prescribe these.
     */
    private val technique = setOf(
        "scapular pull", "l-sit pull-up", "one-arm negative", "one-arm pull-up",
        "one-arm negative push-up", "one-arm push-up", "90-degree push-up",
        "straddle press to handstand", "crow → handstand", "planche push-up",
        "skin the cat", "ring muscle-up", "kip-up", "muscle-up", "strict muscle-up",
        "inverted muscle-up", "handstand-to-bridge", "stand-to-stand bridge", "dragon squat",
    )

    fun isTechnique(exerciseName: String): Boolean = key(exerciseName) in technique

    private val profiles: Map<String, ExerciseProfile> = buildMap {
        // ---- Base catalogue: pull ----
        // Every bar, handle, sheet or ring pull credits FOREARMS 0.5: the
        // grip holds the whole load for the whole set. Without it a week of
        // weighted pull-ups and rows reads "forearms untrained" on coverage.
        // Every pull and row also credits BRACHIALIS 0.5: it flexes the
        // elbow whatever the grip, where the biceps depends on supination.
        put("pull-up", verticalPull())
        put("chin-up", verticalPull())
        put("archer pull-up", verticalPull())
        put("inverted row", row())
        put("door sheet row", row())
        put("wrist curl", ExerciseProfile(
            muscles = mapOf(Muscle.FOREARMS to 1.0),
            pattern = MovementPattern.ISOLATION, compound = false, stretchBias = true,
        ))
        put("bicep curl", curl())
        put("barbell row", row(lowerBack = true))
        put("dumbbell row", row())
        put("lat pulldown", verticalPull())
        put("face pull", ExerciseProfile(
            // External rotation at the end of the pull works the cuff; the
            // high elbow line shrugs the traps into it.
            muscles = mapOf(
                Muscle.REAR_DELTS to 1.0, Muscle.RHOMBOIDS to 0.5,
                Muscle.TRAPS to 0.5, Muscle.ROTATOR_CUFF to 0.5,
            ),
            pattern = MovementPattern.ISOLATION, compound = false, stretchBias = false,
        ))
        // ---- Base catalogue: push ----
        put("dip", dip())
        put("push-up", pressFamily(MovementPattern.HORIZONTAL_PUSH, pushUp = true))
        put("archer push-up", pressFamily(MovementPattern.HORIZONTAL_PUSH, pushUp = true))
        put("diamond push-up", ExerciseProfile(
            muscles = mapOf(
                Muscle.TRICEPS to 1.0, Muscle.MID_CHEST to 0.5, Muscle.FRONT_DELTS to 0.5,
                Muscle.SERRATUS to 0.5,
            ),
            // Triceps loaded with the shoulder neutral, not long: as the close-grip bench.
            pattern = MovementPattern.HORIZONTAL_PUSH, compound = true, stretchBias = false,
        ))
        put("pike push-up", verticalPress())
        put("handstand push-up", verticalPress())
        put("overhead press", verticalPress())
        put("bench press", pressFamily(MovementPattern.HORIZONTAL_PUSH))
        put("incline bench press", inclinePress())
        // Protraction past the lockout of a push-up: the serratus's own job.
        put("scapular push-up", ExerciseProfile(
            muscles = mapOf(Muscle.SERRATUS to 1.0),
            pattern = MovementPattern.ISOLATION, compound = false, stretchBias = false,
        ))
        // ---- Base catalogue: shoulder health ----
        put("prone y raise", ExerciseProfile(
            // The lower and middle trapezius lead prone overhead elevation
            // (Ekstrom 2003); the delts and cuff assist and steady it.
            muscles = mapOf(
                Muscle.TRAPS to 1.0, Muscle.REAR_DELTS to 0.5, Muscle.SIDE_DELTS to 0.5,
                Muscle.ROTATOR_CUFF to 0.5,
            ),
            pattern = MovementPattern.ISOLATION, compound = false, stretchBias = false,
        ))
        put("dumbbell external rotation", ExerciseProfile(
            muscles = mapOf(Muscle.ROTATOR_CUFF to 1.0),
            pattern = MovementPattern.ISOLATION, compound = false, stretchBias = false,
        ))
        // ---- Base catalogue: legs ----
        put("pistol squat", squatProfile(pattern = MovementPattern.LUNGE))
        put("back squat", deepSquat())
        put("bulgarian split squat", squatProfile(MovementPattern.LUNGE))
        put("single-leg glute bridge", ExerciseProfile(
            // One leg: the glute med holds the pelvis level against the drop.
            muscles = mapOf(Muscle.GLUTES to 1.0, Muscle.HAMSTRINGS to 0.5, Muscle.ABDUCTORS to 0.5),
            pattern = MovementPattern.HINGE, compound = false, stretchBias = false,
        ))
        put("single-leg calf raise", ExerciseProfile(
            muscles = mapOf(Muscle.CALVES to 1.0),
            pattern = MovementPattern.ISOLATION, compound = false, stretchBias = true,
        ))
        put("nordic curl", ExerciseProfile(
            muscles = mapOf(Muscle.HAMSTRINGS to 1.0),
            pattern = MovementPattern.ISOLATION, compound = false, stretchBias = true,
        ))
        put("knee-to-wall dorsiflexion", ExerciseProfile(
            // Driving the knee over the toes is active dorsiflexion against
            // nothing but the shin, and the calves are stretched, not loaded:
            // a mobility drill, so 0.25 each. It is the one mobility drill
            // the generator can prescribe, and at the helper 0.5 it filled
            // calf deficits "at full stretch" as if it were a calf raise.
            muscles = mapOf(Muscle.CALVES to 0.25, Muscle.TIBIALIS to 0.25),
            pattern = MovementPattern.ISOLATION, compound = false, stretchBias = true,
        ))
        put("glute bridge", ExerciseProfile(
            muscles = mapOf(Muscle.GLUTES to 1.0, Muscle.HAMSTRINGS to 0.5),
            pattern = MovementPattern.HINGE, compound = false, stretchBias = false,
        ))
        // Direct work for two helper muscles no bodyweight week reached:
        // the erectors lift the torso here (prone, or over a couch edge),
        // and the Cossack squat lengthens the adductors under load.
        put("back extension", ExerciseProfile(
            muscles = mapOf(Muscle.LOWER_BACK to 1.0, Muscle.GLUTES to 0.5, Muscle.HAMSTRINGS to 0.5),
            pattern = MovementPattern.HINGE, compound = false, stretchBias = false,
        ))
        put("cossack squat", ExerciseProfile(
            // A sideways lunge: the glute med works as hard as in a forward
            // lunge (39% vs 42% MVIC, DiStefano 2009), so it gets the same 0.5.
            muscles = mapOf(Muscle.ADDUCTORS to 1.0, Muscle.QUADS to 0.5, Muscle.GLUTES to 0.5, Muscle.ABDUCTORS to 0.5),
            pattern = MovementPattern.LUNGE, compound = true, stretchBias = true,
        ))
        // Dorsiflexion against bodyweight, back to a wall: the one direct
        // tibialis movement, and it needs no gear.
        put("tib raise", ExerciseProfile(
            muscles = mapOf(Muscle.TIBIALIS to 1.0),
            pattern = MovementPattern.ISOLATION, compound = false, stretchBias = false,
        ))
        // Side-lying, the hip abducts in the frontal plane with the glute
        // max's line of pull behind it: glute med/min and TFL only. No glute
        // share, so it serves the abductors even on a week whose glutes sit
        // at the top of their range.
        put("side-lying hip abduction", ExerciseProfile(
            muscles = mapOf(Muscle.ABDUCTORS to 1.0),
            pattern = MovementPattern.ISOLATION, compound = false, stretchBias = false,
        ))
        // Every hinge from a flexed hip credits ADDUCTORS 0.5: the posterior
        // adductor magnus is a hip extensor, lengthened as the torso folds
        // and working as it rises (movement anatomy; no longitudinal trial
        // measured it, so the helper share and not the lead).
        put("deadlift", deadlift())
        put("romanian deadlift", ExerciseProfile(
            // The load hangs from the hands, so the upper traps and the grip
            // hold it as in a deadlift.
            muscles = mapOf(
                Muscle.HAMSTRINGS to 1.0, Muscle.GLUTES to 1.0, Muscle.LOWER_BACK to 0.5, Muscle.TRAPS to 0.5,
                Muscle.FOREARMS to 0.5, Muscle.ADDUCTORS to 0.5,
            ),
            pattern = MovementPattern.HINGE, compound = true, stretchBias = true,
        ))
        put("single-leg romanian deadlift", ExerciseProfile(
            // The same hinge on one leg: the stance leg's hamstrings and glutes
            // still extend the hip from full stretch. The glute med holds the
            // pelvis level over one foot, as in every single-leg movement. The
            // erectors keep their helper share - they still hold the torso -
            // though the spine carries less than a bilateral pull. Not the
            // minor 0.25: no trial measured it, and the generator's
            // lower-back tie-break would then hand the RDL's slots to this
            // balance-limited twin.
            muscles = mapOf(
                Muscle.HAMSTRINGS to 1.0, Muscle.GLUTES to 1.0, Muscle.ABDUCTORS to 0.5, Muscle.ADDUCTORS to 0.5,
                Muscle.LOWER_BACK to 0.5, Muscle.TRAPS to 0.5, Muscle.FOREARMS to 0.5,
            ),
            pattern = MovementPattern.HINGE, compound = true, stretchBias = true,
        ))
        put("front squat", squatProfile(MovementPattern.SQUAT, glutes = 0.5))
        put("hip thrust", ExerciseProfile(
            // Adductors 0.25: the thrust extends the hip short of the deep
            // flexion that lengthens the adductor magnus, and Plotkin 2023
            // grew the adductors more with the squat than with the thrust.
            muscles = mapOf(Muscle.GLUTES to 1.0, Muscle.HAMSTRINGS to 0.5, Muscle.ADDUCTORS to 0.25),
            pattern = MovementPattern.HINGE, compound = true, stretchBias = false,
        ))
        // ---- Base catalogue: core ----
        // Leg raises flex the hip through its whole range: the hip flexors
        // move the legs while the abs curl the pelvis. ABS stays first so it
        // remains the dominant muscle these movements are chosen for.
        put("hanging leg raise", coreHanging())
        put("toes-to-bar", coreHanging())
        put("hanging knee raise", ExerciseProfile(
            muscles = mapOf(Muscle.ABS to 1.0, Muscle.HIP_FLEXORS to 1.0, Muscle.FOREARMS to 0.5),
            pattern = MovementPattern.CORE, compound = false, stretchBias = true,
        ))
        put("ab wheel rollout", ExerciseProfile(
            muscles = mapOf(
                Muscle.ABS to 1.0, Muscle.LATS to 0.5, Muscle.LOWER_BACK to 0.5,
                Muscle.SERRATUS to 0.5, Muscle.OBLIQUES to 0.5,
            ),
            pattern = MovementPattern.CORE, compound = false, stretchBias = true,
        ))
        put("dragon flag", ExerciseProfile(
            muscles = mapOf(
                Muscle.ABS to 1.0, Muscle.LATS to 0.5, Muscle.OBLIQUES to 0.5,
                Muscle.HIP_FLEXORS to 0.5,
            ),
            pattern = MovementPattern.CORE, compound = false, stretchBias = true,
        ))
        put("side plank", ExerciseProfile(
            // A hold: coverage credit for the lifter's own presets only.
            muscles = mapOf(Muscle.OBLIQUES to 1.0, Muscle.ABS to 0.5, Muscle.ABDUCTORS to 0.5),
            pattern = MovementPattern.CORE, compound = false, stretchBias = false,
        ))
        put("lying leg raise", ExerciseProfile(
            // Floor work: the back is supported, so the abs are not loaded long.
            muscles = mapOf(Muscle.ABS to 1.0, Muscle.HIP_FLEXORS to 1.0),
            pattern = MovementPattern.CORE, compound = false, stretchBias = false,
        ))
        put("dead bug", ExerciseProfile(
            muscles = mapOf(Muscle.ABS to 1.0, Muscle.HIP_FLEXORS to 0.5, Muscle.OBLIQUES to 0.5),
            pattern = MovementPattern.CORE, compound = false, stretchBias = false,
        ))
        // ---- Skill-tree strength holds ----
        // A hold set counts as ONE set, however many seconds it lasts. The
        // coverage dose is hard sets (Pelland 2026), and a rep set is not
        // scaled by its reps either: a 3-rep and a 12-rep set both count 1
        // (Lopez 2021 found low and high loads grew muscle alike). The
        // project's hold conversion, MovementDifficulty.SECONDS_PER_REP_EQUIVALENT,
        // prices EFFORT for XP and the strength score; applied here it would
        // turn a near-maximal 10 s tuck lever into a fifth of a set while a
        // 3-rep pull-up set counted whole.
        put("l-sit", lSit(triceps = true))
        // Wider or higher legs load the same compression harder.
        put("straddle l-sit", lSit(triceps = true))
        put("v-sit", lSit())
        // The lever and planche lines. No longitudinal trial measured growth
        // from any of them, so every share is movement anatomy on the 1.0 /
        // 0.5 scale, and a helper is listed only where its joint action is
        // plain - the conservative reading. Every step of a line shares one
        // profile: a longer lever loads the same muscles harder, as a
        // weighted pull-up is still a pull-up. Not stretch biased: the
        // lengthened-position trials were all dynamic, none held a position.
        // Compound: shoulder, trunk and hip are braced together, which also
        // keeps the compound rest these holds were timed with while
        // unprofiled.
        // Feet on the floor carry part of the body: a submaximal front
        // lever shape, so nothing earns more than the helper share.
        put("front row hold", ExerciseProfile(
            muscles = mapOf(
                Muscle.LATS to 0.5, Muscle.RHOMBOIDS to 0.5, Muscle.REAR_DELTS to 0.5,
                Muscle.FOREARMS to 0.5,
            ),
            pattern = MovementPattern.VERTICAL_PULL, compound = true, stretchBias = false,
        ))
        put("tuck front lever", frontLever())
        put("advanced tuck front lever", frontLever())
        put("one-leg front lever", frontLever())
        put("straddle front lever", frontLever())
        put("front lever", frontLever())
        put("one-arm front lever", frontLever())
        put("tuck back lever", backLever())
        put("advanced tuck back lever", backLever())
        put("straddle back lever", backLever())
        put("back lever", backLever())
        put("tuck planche", planche())
        put("advanced tuck planche", planche())
        put("one-leg planche", planche())
        put("straddle planche", planche())
        put("full planche", planche())
        // ---- Gym floor: barbell ----
        // stretchBias false: the press loads the triceps with the shoulder
        // neutral, the short-length condition Maeo 2022 compared overhead work
        // against; long-length triceps work is the overhead extension.
        put("close-grip bench press", ExerciseProfile(
            muscles = mapOf(Muscle.TRICEPS to 1.0, Muscle.MID_CHEST to 0.5, Muscle.FRONT_DELTS to 0.5),
            pattern = MovementPattern.HORIZONTAL_PUSH, compound = true, stretchBias = false,
        ))
        put("push press", ExerciseProfile(
            muscles = mapOf(
                Muscle.FRONT_DELTS to 1.0, Muscle.TRICEPS to 0.5, Muscle.SIDE_DELTS to 0.5,
                Muscle.QUADS to 0.5, Muscle.TRAPS to 0.5, Muscle.SERRATUS to 0.5,
            ),
            pattern = MovementPattern.VERTICAL_PUSH, compound = true, stretchBias = true,
        ))
        put("sumo deadlift", ExerciseProfile(
            muscles = mapOf(
                Muscle.GLUTES to 1.0, Muscle.ADDUCTORS to 1.0, Muscle.HAMSTRINGS to 0.5,
                Muscle.QUADS to 0.5, Muscle.LOWER_BACK to 0.5,
                // The grip holds the bar through the whole pull, as in the deadlift.
                Muscle.FOREARMS to 0.5,
            ),
            pattern = MovementPattern.HINGE, compound = true, stretchBias = true,
        ))
        put("rack pull", ExerciseProfile(
            muscles = mapOf(
                Muscle.LOWER_BACK to 1.0, Muscle.RHOMBOIDS to 0.5, Muscle.TRAPS to 0.5,
                Muscle.GLUTES to 0.5, Muscle.HAMSTRINGS to 0.5, Muscle.FOREARMS to 0.5,
            ),
            pattern = MovementPattern.HINGE, compound = true, stretchBias = false,
        ))
        put("pendlay row", row(lowerBack = true))
        put("t-bar row", row(lowerBack = true))
        put("good morning", ExerciseProfile(
            // A hinge from a flexed hip: the adductor magnus extends it (see romanian deadlift).
            muscles = mapOf(Muscle.HAMSTRINGS to 1.0, Muscle.LOWER_BACK to 1.0, Muscle.GLUTES to 0.5, Muscle.ADDUCTORS to 0.5),
            pattern = MovementPattern.HINGE, compound = true, stretchBias = true,
        ))
        put("barbell lunge", squatProfile(MovementPattern.LUNGE))
        put("barbell step-up", stepUp())
        // Elevation is the upper traps' job; the rhomboids retract the
        // blade, which a shrug does not ask for, so they get no share.
        put("barbell shrug", shrug())
        // ---- Gym floor: dumbbell ----
        put("dumbbell bench press", pressFamily(MovementPattern.HORIZONTAL_PUSH))
        put("incline dumbbell press", inclinePress())
        put("dumbbell shoulder press", verticalPress())
        put("arnold press", ExerciseProfile(
            muscles = mapOf(
                Muscle.FRONT_DELTS to 1.0, Muscle.SIDE_DELTS to 0.5, Muscle.TRICEPS to 0.5,
                // The cuff steadies overhead work, as in every other press.
                Muscle.TRAPS to 0.5, Muscle.SERRATUS to 0.5, Muscle.ROTATOR_CUFF to 0.5,
            ),
            pattern = MovementPattern.VERTICAL_PUSH, compound = true, stretchBias = true,
        ))
        put("lateral raise", ExerciseProfile(
            // Presses grow little medial delt (Lanza 2024 negative control),
            // so the isolation earns its full 1.0.
            // The supraspinatus starts every raise, so the cuff assists.
            muscles = mapOf(Muscle.SIDE_DELTS to 1.0, Muscle.ROTATOR_CUFF to 0.5),
            pattern = MovementPattern.ISOLATION, compound = false, stretchBias = false,
        ))
        put("front raise", ExerciseProfile(
            // Shoulder flexion: the clavicular chest assists.
            muscles = mapOf(Muscle.FRONT_DELTS to 1.0, Muscle.UPPER_CHEST to 0.5),
            pattern = MovementPattern.ISOLATION, compound = false, stretchBias = false,
        ))
        put("reverse fly", ExerciseProfile(
            // The middle trapezius retracts the blade with the rhomboids.
            muscles = mapOf(
                Muscle.REAR_DELTS to 1.0, Muscle.RHOMBOIDS to 0.5, Muscle.TRAPS to 0.5,
                Muscle.ROTATOR_CUFF to 0.5,
            ),
            pattern = MovementPattern.ISOLATION, compound = false, stretchBias = false,
        ))
        put("dumbbell fly", fly())
        put("hammer curl", ExerciseProfile(
            // Neutral grip: the biceps is mechanically weaker, so the
            // brachialis and brachioradialis take the lead.
            muscles = mapOf(Muscle.BRACHIALIS to 1.0, Muscle.BICEPS to 0.5, Muscle.FOREARMS to 0.5),
            pattern = MovementPattern.ISOLATION, compound = false, stretchBias = false,
        ))
        put("reverse curl", ExerciseProfile(
            // Pronated grip: weaker still for the biceps; the brachioradialis
            // (forearms) is the strongest flexor here, a co-prime mover with
            // the brachialis.
            muscles = mapOf(Muscle.BRACHIALIS to 1.0, Muscle.FOREARMS to 1.0, Muscle.BICEPS to 0.5),
            pattern = MovementPattern.ISOLATION, compound = false, stretchBias = false,
        ))
        put("preacher curl", ExerciseProfile(
            // The brachioradialis and grip work in every curl.
            muscles = mapOf(Muscle.BICEPS to 1.0, Muscle.BRACHIALIS to 0.5, Muscle.FOREARMS to 0.5),
            pattern = MovementPattern.ISOLATION, compound = false, stretchBias = true,
        ))
        put("dumbbell shrug", shrug())
        put("goblet squat", deepSquat())
        put("walking lunge", squatProfile(MovementPattern.LUNGE))
        put("dumbbell step-up", stepUp())
        put("triceps kickback", ExerciseProfile(
            muscles = mapOf(Muscle.TRICEPS to 1.0),
            pattern = MovementPattern.ISOLATION, compound = false, stretchBias = false,
        ))
        put("dumbbell pullover", ExerciseProfile(
            muscles = mapOf(
                Muscle.LATS to 1.0, Muscle.MID_CHEST to 0.5, Muscle.TRICEPS to 0.5,
                Muscle.SERRATUS to 0.5,
            ),
            pattern = MovementPattern.ISOLATION, compound = false, stretchBias = true,
        ))
        // ---- Gym floor: cable ----
        put("seated cable row", row())
        put("triceps pushdown", ExerciseProfile(
            // Short-length elbow extension: the long head is better loaded
            // overhead (Maeo 2022), so no stretch bias here.
            muscles = mapOf(Muscle.TRICEPS to 1.0),
            pattern = MovementPattern.ISOLATION, compound = false, stretchBias = false,
        ))
        put("overhead cable extension", ExerciseProfile(
            muscles = mapOf(Muscle.TRICEPS to 1.0),
            pattern = MovementPattern.ISOLATION, compound = false, stretchBias = true,
        ))
        put("cable fly", fly())
        put("cable lateral raise", ExerciseProfile(
            muscles = mapOf(Muscle.SIDE_DELTS to 1.0, Muscle.ROTATOR_CUFF to 0.5),
            pattern = MovementPattern.ISOLATION, compound = false, stretchBias = true,
        ))
        put("cable curl", ExerciseProfile(
            muscles = mapOf(Muscle.BICEPS to 1.0, Muscle.BRACHIALIS to 0.5, Muscle.FOREARMS to 0.5),
            pattern = MovementPattern.ISOLATION, compound = false, stretchBias = false,
        ))
        put("cable pull-through", ExerciseProfile(
            muscles = mapOf(Muscle.GLUTES to 1.0, Muscle.HAMSTRINGS to 0.5),
            pattern = MovementPattern.HINGE, compound = false, stretchBias = true,
        ))
        // Rotation (woodchop) and anti-rotation (Pallof press) are the
        // obliques' work; the rectus assists.
        put("woodchop", ExerciseProfile(
            muscles = mapOf(Muscle.OBLIQUES to 1.0, Muscle.ABS to 0.5, Muscle.LOWER_BACK to 0.5),
            pattern = MovementPattern.CORE, compound = false, stretchBias = false,
        ))
        put("pallof press", ExerciseProfile(
            muscles = mapOf(Muscle.OBLIQUES to 1.0, Muscle.ABS to 0.5),
            pattern = MovementPattern.CORE, compound = false, stretchBias = false,
        ))
        // ---- Gym floor: sled machines ----
        put("leg press", squatProfile(MovementPattern.SQUAT))
        put("hack squat", ExerciseProfile(
            muscles = mapOf(Muscle.QUADS to 1.0, Muscle.GLUTES to 0.5, Muscle.ADDUCTORS to 0.5, Muscle.HAMSTRINGS to 0.0),
            pattern = MovementPattern.SQUAT, compound = true, stretchBias = true,
        ))
        put("chest-supported row", row())
        // ---- Gym floor: pin-stack machines ----
        put("leg extension", ExerciseProfile(
            // Lengthened-partial leg extensions won the regional-growth
            // comparison (Pedrosa 2022): the deep knee flexion is the point.
            muscles = mapOf(Muscle.QUADS to 1.0),
            pattern = MovementPattern.ISOLATION, compound = false, stretchBias = true,
        ))
        put("seated leg curl", ExerciseProfile(
            // Hips flexed = hamstrings long: +14.1% vs +9.3% for the prone
            // version over 12 weeks, within-participant MRI (Maeo 2021).
            muscles = mapOf(Muscle.HAMSTRINGS to 1.0),
            pattern = MovementPattern.ISOLATION, compound = false, stretchBias = true,
        ))
        put("lying leg curl", ExerciseProfile(
            muscles = mapOf(Muscle.HAMSTRINGS to 1.0),
            pattern = MovementPattern.ISOLATION, compound = false, stretchBias = false,
        ))
        put("pec deck", fly())
        put("machine chest press", pressFamily(MovementPattern.HORIZONTAL_PUSH))
        put("machine shoulder press", verticalPress())
        put("machine row", row())
        put("hip adduction", ExerciseProfile(
            muscles = mapOf(Muscle.ADDUCTORS to 1.0),
            pattern = MovementPattern.ISOLATION, compound = false, stretchBias = true,
        ))
        put("hip abduction", ExerciseProfile(
            // Glute med/min and TFL are the abductors; the upper glute max
            // fibres assist.
            muscles = mapOf(Muscle.ABDUCTORS to 1.0, Muscle.GLUTES to 0.5),
            pattern = MovementPattern.ISOLATION, compound = false, stretchBias = false,
        ))
        put("seated calf raise", ExerciseProfile(
            // Knees bent = gastrocnemius slack: it grew 1.7%/0.6% seated vs
            // 12.4%/9.2% standing (Kinoshita 2023), so the seated raise is
            // half credit against the standing one.
            muscles = mapOf(Muscle.CALVES to 0.5),
            pattern = MovementPattern.ISOLATION, compound = false, stretchBias = true,
        ))
        put("standing calf raise", ExerciseProfile(
            // Knee extended: gastroc grew ~7x more than seated (Kinoshita
            // 2023); soleus half-credited, folded into the CALVES 1.0.
            muscles = mapOf(Muscle.CALVES to 1.0),
            pattern = MovementPattern.ISOLATION, compound = false, stretchBias = true,
        ))
        // ---- Gym floor: Smith machine ----
        put("smith machine squat", deepSquat())
        put("smith machine bench press", pressFamily(MovementPattern.HORIZONTAL_PUSH))
        put("smith machine overhead press", verticalPress())
        put("smith machine row", row())
        // ---- Assisted machines ----
        put("assisted pull-up", verticalPull())
        put("assisted dip", dip())
        // ---- Skill-tree rows (REPS-metric, not milestone-priced) ----
        put("scapular pull", ExerciseProfile(
            muscles = mapOf(Muscle.LATS to 0.5, Muscle.RHOMBOIDS to 0.5, Muscle.FOREARMS to 0.5),
            pattern = MovementPattern.VERTICAL_PULL, compound = false, stretchBias = true,
        ))
        put("australian pull-up", row())
        // A pull-up held in an L: the pull-up's own credits plus the trunk
        // and hip flexors that hold the legs up.
        put("l-sit pull-up", verticalPull().let { pull ->
            pull.copy(muscles = pull.muscles + mapOf(Muscle.ABS to 0.5, Muscle.HIP_FLEXORS to 0.5))
        })
        put("one-arm negative", verticalPull(rearDelts = false))
        put("one-arm pull-up", verticalPull(rearDelts = false))
        // Hands up, feet down: the body angle of a decline press.
        put("incline push-up", angled(pressFamily(MovementPattern.HORIZONTAL_PUSH, pushUp = true), Muscle.LOWER_CHEST))
        put("one-arm negative push-up", pressFamily(MovementPattern.HORIZONTAL_PUSH, pushUp = true))
        put("one-arm push-up", pressFamily(MovementPattern.HORIZONTAL_PUSH, pushUp = true))
        put("bench dip", ExerciseProfile(
            // Triceps-led; the chest share is the dip's costal fibres. Elbow
            // and shoulder extension together: a press, like the other dips.
            muscles = mapOf(Muscle.TRICEPS to 1.0, Muscle.LOWER_CHEST to 0.5, Muscle.FRONT_DELTS to 0.5),
            pattern = MovementPattern.HORIZONTAL_PUSH, compound = true, stretchBias = false,
        ))
        put("parallel bar dip", dip())
        put("pike press", verticalPress())
        put("wall hspu", verticalPress())
        put("90-degree push-up", ExerciseProfile(
            // Pressing out of a planche-lean: shoulder flexion work, the
            // clavicular chest's line rather than the sternal one.
            muscles = mapOf(
                Muscle.FRONT_DELTS to 1.0, Muscle.TRICEPS to 0.5, Muscle.UPPER_CHEST to 0.5,
                Muscle.SERRATUS to 0.5,
            ),
            pattern = MovementPattern.VERTICAL_PUSH, compound = true, stretchBias = true,
        ))
        put("straddle press to handstand", handstandPress())
        put("crow → handstand", handstandPress())
        put("planche push-up", ExerciseProfile(
            // The lean drives the hands toward the hips: the costal fibres
            // join the sternal chest, and the blade stays protracted.
            muscles = mapOf(
                Muscle.FRONT_DELTS to 1.0, Muscle.MID_CHEST to 1.0, Muscle.LOWER_CHEST to 0.5,
                Muscle.TRICEPS to 0.5, Muscle.SERRATUS to 0.5,
            ),
            pattern = MovementPattern.HORIZONTAL_PUSH, compound = true, stretchBias = true,
        ))
        put("skin the cat", verticalPull(rearDelts = false))
        put("ring row", row())
        put("ring dip", dip())
        put("ring muscle-up", muscleUp(Muscle.TRICEPS))
        put("kip-up", ExerciseProfile(
            muscles = mapOf(Muscle.ABS to 1.0, Muscle.QUADS to 0.5, Muscle.LOWER_BACK to 0.5),
            pattern = MovementPattern.CORE, compound = false, stretchBias = false,
        ))
        put("muscle-up", muscleUp(Muscle.TRICEPS))
        put("strict muscle-up", muscleUp(Muscle.TRICEPS))
        put("inverted muscle-up", muscleUp(Muscle.FRONT_DELTS))
        put("handstand-to-bridge", ExerciseProfile(
            // The same loaded arch as the stand-to-stand bridge; the glutes
            // extend the hips and the triceps lock the arms overhead.
            muscles = mapOf(
                Muscle.LOWER_BACK to 1.0, Muscle.FRONT_DELTS to 0.5, Muscle.GLUTES to 0.5,
                Muscle.ABS to 0.5, Muscle.TRICEPS to 0.5,
            ),
            pattern = MovementPattern.CORE, compound = false, stretchBias = true,
        ))
        put("stand-to-stand bridge", ExerciseProfile(
            muscles = mapOf(Muscle.LOWER_BACK to 1.0, Muscle.FRONT_DELTS to 0.5, Muscle.GLUTES to 0.5),
            pattern = MovementPattern.CORE, compound = false, stretchBias = true,
        ))
        put("bodyweight squat", deepSquat())
        put("split squat", squatProfile(MovementPattern.LUNGE))
        put("sissy squat", ExerciseProfile(
            muscles = mapOf(Muscle.QUADS to 1.0),
            pattern = MovementPattern.ISOLATION, compound = false, stretchBias = true,
        ))
        // A single-leg squat to full depth like the pistol, so the same
        // squat shares: it had lost the adductors every other squat credits.
        put("shrimp squat", squatProfile(MovementPattern.LUNGE))
        put("dragon squat", squatProfile(MovementPattern.LUNGE))
        put("hamstring bridge", ExerciseProfile(
            muscles = mapOf(Muscle.HAMSTRINGS to 1.0, Muscle.GLUTES to 0.5),
            pattern = MovementPattern.HINGE, compound = false, stretchBias = false,
        ))
        put("nordic negative", ExerciseProfile(
            muscles = mapOf(Muscle.HAMSTRINGS to 1.0),
            pattern = MovementPattern.ISOLATION, compound = false, stretchBias = true,
        ))
        // ---- Load-priced milestones ----
        // The lift itself at a stated load: the same muscles as the plain
        // movement. The generator never prescribes these rows.
        put("weighted pull-up", verticalPull())
        put("weighted dip", dip())
        for (name in listOf("pause squat", "heavy squat", "double-bodyweight squat", "triple-bodyweight squat")) {
            put(name, deepSquat())
        }
        for (name in listOf(
            "volume bench press", "paused bench press", "heavy bench press", "double-bodyweight bench press",
        )) {
            put(name, pressFamily(MovementPattern.HORIZONTAL_PUSH))
        }
        for (name in listOf(
            "volume overhead press", "bodyweight overhead press", "heavy overhead press", "half-again overhead press",
        )) {
            put(name, verticalPress())
        }
        for (name in listOf(
            "volume deadlift", "double-bodyweight deadlift", "heavy deadlift", "triple-bodyweight deadlift",
        )) {
            put(name, deadlift())
        }
        // ---- Other holds ----
        // Hangs: the grip holds the whole body, so the forearms lead; the
        // cuff holds the shoulder, and an active hang pulls the blade down.
        put("dead hang", hold(Muscle.FOREARMS to 1.0, Muscle.ROTATOR_CUFF to 0.5, pattern = MovementPattern.VERTICAL_PULL))
        put("active bar hang", hold(
            Muscle.FOREARMS to 1.0, Muscle.LATS to 0.5, Muscle.TRAPS to 0.5, Muscle.ROTATOR_CUFF to 0.5,
            pattern = MovementPattern.VERTICAL_PULL,
        ))
        // One arm: the trunk resists the twist as well.
        put("one-arm hang", hold(
            Muscle.FOREARMS to 1.0, Muscle.LATS to 0.5, Muscle.ROTATOR_CUFF to 0.5, Muscle.OBLIQUES to 0.5,
            pattern = MovementPattern.VERTICAL_PULL,
        ))
        // Planks: the abs resist extension, the obliques assist - single
        // trunk bracing like the side plank.
        put("plank", hold(Muscle.ABS to 1.0, Muscle.OBLIQUES to 0.5, pattern = MovementPattern.CORE, compound = false))
        put("weighted plank", hold(Muscle.ABS to 1.0, Muscle.OBLIQUES to 0.5, pattern = MovementPattern.CORE, compound = false))
        put("hollow hold", hold(
            Muscle.ABS to 1.0, Muscle.HIP_FLEXORS to 0.5, Muscle.OBLIQUES to 0.5,
            pattern = MovementPattern.CORE, compound = false,
        ))
        put("parallel bar support hold", supportHold())
        // Rings shake: the cuff and the biceps hold the arms turned out.
        put("ring support hold", supportHold(Muscle.ROTATOR_CUFF, Muscle.BICEPS))
        put("wall handstand", handstandHold())
        put("freestanding handstand", handstandHold(Muscle.ABS))
        put("one-arm handstand", handstandHold(Muscle.ABS, Muscle.OBLIQUES))
        // Walking on the hands: the handstand hold, shifted from hand to hand.
        put("handstand walk", handstandHold(Muscle.ABS))
        put("crow pose", armBalance())
        put("frog stand", armBalance())
        // Straight arms held out to the sides on rings: the lats and the
        // costal chest pull the arms down, the biceps and cuff hold the joint.
        put("iron cross", hold(
            Muscle.LATS to 1.0, Muscle.LOWER_CHEST to 0.5, Muscle.MID_CHEST to 0.5, Muscle.BICEPS to 0.5,
            Muscle.ROTATOR_CUFF to 0.5, Muscle.FOREARMS to 0.5,
            pattern = MovementPattern.VERTICAL_PULL,
        ))
        // The body held sideways off a pole: the obliques lead, the top arm
        // pulls, the bottom arm pushes.
        put("human flag", hold(
            Muscle.OBLIQUES to 1.0, Muscle.LATS to 0.5, Muscle.SIDE_DELTS to 0.5, Muscle.TRICEPS to 0.5,
            Muscle.FOREARMS to 0.5,
            pattern = MovementPattern.CORE,
        ))
        // The deepest compression, hands pressing down behind the hips.
        put("manna", hold(
            Muscle.ABS to 1.0, Muscle.HIP_FLEXORS to 1.0, Muscle.REAR_DELTS to 0.5, Muscle.TRICEPS to 0.5,
            Muscle.TRAPS to 0.5, Muscle.FOREARMS to 0.5,
            pattern = MovementPattern.CORE,
        ))
        // ---- Mobility holds ----
        // The muscles held long or working to hold the position, all at the
        // helper share. A squat hold, like every squat, credits no hamstrings.
        put("deep squat hold", hold(
            Muscle.QUADS to 0.5, Muscle.GLUTES to 0.5, Muscle.ADDUCTORS to 0.5, Muscle.CALVES to 0.5,
            pattern = MovementPattern.SQUAT,
        ))
        put("pancake", hold(
            Muscle.ADDUCTORS to 0.5, Muscle.HAMSTRINGS to 0.5, Muscle.HIP_FLEXORS to 0.5, Muscle.LOWER_BACK to 0.5,
            pattern = MovementPattern.HINGE,
        ))
        put("bridge", hold(
            Muscle.LOWER_BACK to 1.0, Muscle.GLUTES to 0.5, Muscle.FRONT_DELTS to 0.5, Muscle.TRICEPS to 0.5,
            pattern = MovementPattern.CORE,
        ))
        put("front split", hold(
            Muscle.HAMSTRINGS to 0.5, Muscle.HIP_FLEXORS to 0.5, Muscle.GLUTES to 0.5,
            pattern = MovementPattern.LUNGE,
        ))
        // Hanging with the arms behind: the back lever's shoulder flexors, held
        // long. A hang from the bar, so the hanging family's pattern, not a press.
        put("german hang", hold(
            Muscle.FRONT_DELTS to 0.5, Muscle.UPPER_CHEST to 0.5, Muscle.BICEPS to 0.5, Muscle.ROTATOR_CUFF to 0.5,
            Muscle.FOREARMS to 0.5,
            pattern = MovementPattern.VERTICAL_PULL,
        ))
        put("wrist prep", hold(Muscle.FOREARMS to 1.0, pattern = MovementPattern.ISOLATION, compound = false))
        // ---- Activities: cardio ----
        val gait = arrayOf(Muscle.CALVES, Muscle.QUADS, Muscle.GLUTES, Muscle.HAMSTRINGS, Muscle.HIP_FLEXORS)
        put("running", activity(MovementPattern.LUNGE, *gait))
        put("treadmill", activity(MovementPattern.LUNGE, *gait))
        // Uneven ground: the glute med steadies every landing.
        put("trail running", activity(MovementPattern.LUNGE, *gait, Muscle.ABDUCTORS))
        put("walking", activity(MovementPattern.LUNGE, Muscle.CALVES, Muscle.GLUTES, Muscle.QUADS, Muscle.TIBIALIS))
        put("hiking", activity(MovementPattern.LUNGE, Muscle.QUADS, Muscle.GLUTES, Muscle.CALVES, Muscle.HAMSTRINGS))
        val pedal = arrayOf(Muscle.QUADS, Muscle.GLUTES, Muscle.HAMSTRINGS, Muscle.CALVES)
        put("cycling", activity(MovementPattern.LUNGE, *pedal))
        put("indoor cycling", activity(MovementPattern.LUNGE, *pedal))
        put("elliptical", activity(MovementPattern.LUNGE, *pedal))
        // The handles push and pull with the pedals.
        put("assault bike", activity(MovementPattern.LUNGE, *pedal, Muscle.LATS, Muscle.TRICEPS))
        put("stair climbing", activity(MovementPattern.LUNGE, Muscle.QUADS, Muscle.GLUTES, Muscle.CALVES))
        put("versaclimber", activity(MovementPattern.LUNGE, Muscle.QUADS, Muscle.GLUTES, Muscle.CALVES, Muscle.LATS))
        // The legs drive, then the back and arms finish the stroke.
        put("rowing", activity(
            MovementPattern.HORIZONTAL_PULL, Muscle.QUADS, Muscle.GLUTES, Muscle.HAMSTRINGS, Muscle.LATS,
            Muscle.RHOMBOIDS, Muscle.BICEPS, Muscle.LOWER_BACK, Muscle.FOREARMS,
        ))
        put("ski erg", activity(MovementPattern.HORIZONTAL_PULL, Muscle.LATS, Muscle.TRICEPS, Muscle.ABS, Muscle.GLUTES))
        val rope = arrayOf(Muscle.CALVES, Muscle.QUADS, Muscle.FOREARMS)
        put("skipping", activity(MovementPattern.LUNGE, *rope))
        put("jump rope intervals", activity(MovementPattern.LUNGE, *rope))
        // A heavy rope loads the shoulders turning it.
        put("weighted skipping", activity(MovementPattern.LUNGE, *rope, Muscle.FRONT_DELTS, Muscle.SIDE_DELTS))
        // ---- Activities: water ----
        // The serratus protracts and upward-rotates the blade through each stroke.
        val stroke = arrayOf(
            Muscle.LATS, Muscle.FRONT_DELTS, Muscle.TRICEPS, Muscle.ROTATOR_CUFF, Muscle.HIP_FLEXORS, Muscle.SERRATUS,
        )
        put("swimming", activity(MovementPattern.HORIZONTAL_PULL, *stroke))
        // Treading water is the eggbeater kick: the adductors and quads.
        put("water polo", activity(MovementPattern.HORIZONTAL_PULL, *stroke, Muscle.ADDUCTORS, Muscle.QUADS))
        // ---- Activities: climbing ----
        val climb = arrayOf(Muscle.FOREARMS, Muscle.LATS, Muscle.BRACHIALIS, Muscle.BICEPS, Muscle.CALVES)
        put("bouldering", activity(MovementPattern.HORIZONTAL_PULL, *climb, Muscle.ABS))
        put("sport climbing", activity(MovementPattern.HORIZONTAL_PULL, *climb))
        put("top rope", activity(MovementPattern.HORIZONTAL_PULL, *climb))
        // ---- Activities: sport ----
        // Running sports: sprints, cuts and jumps.
        val field = arrayOf(Muscle.QUADS, Muscle.GLUTES, Muscle.HAMSTRINGS, Muscle.CALVES)
        put("football (soccer)", activity(MovementPattern.LUNGE, *field, Muscle.ADDUCTORS, Muscle.HIP_FLEXORS))
        put("basketball", activity(MovementPattern.LUNGE, *field))
        put("rugby", activity(MovementPattern.LUNGE, *field, Muscle.TRAPS, Muscle.LOWER_BACK))
        put("volleyball", activity(MovementPattern.LUNGE, Muscle.QUADS, Muscle.CALVES, Muscle.GLUTES, Muscle.FRONT_DELTS, Muscle.ROTATOR_CUFF))
        put("cricket", activity(
            MovementPattern.LUNGE, Muscle.QUADS, Muscle.GLUTES, Muscle.HAMSTRINGS, Muscle.OBLIQUES,
            Muscle.FRONT_DELTS, Muscle.ROTATOR_CUFF,
        ))
        // Racquet sports: court footwork, a rotating trunk and a swinging arm.
        val racquet = arrayOf(
            Muscle.QUADS, Muscle.CALVES, Muscle.GLUTES, Muscle.OBLIQUES, Muscle.FRONT_DELTS,
            Muscle.ROTATOR_CUFF, Muscle.FOREARMS,
        )
        put("tennis", activity(MovementPattern.LUNGE, *racquet))
        put("badminton", activity(MovementPattern.LUNGE, *racquet))
        put("squash", activity(MovementPattern.LUNGE, *racquet))
        put("table tennis", activity(MovementPattern.LUNGE, Muscle.FOREARMS, Muscle.OBLIQUES, Muscle.QUADS, Muscle.CALVES))
        put("golf", activity(MovementPattern.CORE, Muscle.OBLIQUES, Muscle.GLUTES, Muscle.LOWER_BACK, Muscle.FOREARMS))
        // Striking: punches protract and extend the arm off a rotating trunk;
        // kicks add the hips.
        val punch = arrayOf(Muscle.FRONT_DELTS, Muscle.TRICEPS, Muscle.SERRATUS, Muscle.OBLIQUES, Muscle.CALVES)
        val kick = arrayOf(Muscle.HIP_FLEXORS, Muscle.GLUTES, Muscle.QUADS, Muscle.ABDUCTORS)
        put("boxing", activity(MovementPattern.HORIZONTAL_PUSH, *punch))
        put("kickboxing", activity(MovementPattern.HORIZONTAL_PUSH, *punch, *kick))
        put("karate", activity(MovementPattern.HORIZONTAL_PUSH, *punch, *kick))
        put("martial arts class", activity(MovementPattern.HORIZONTAL_PUSH, *punch, *kick))
        // Grappling: gripping, pulling and bracing a resisting body.
        val grapple = arrayOf(
            Muscle.FOREARMS, Muscle.LATS, Muscle.BICEPS, Muscle.ABS, Muscle.GLUTES, Muscle.ADDUCTORS,
            Muscle.QUADS, Muscle.TRAPS,
        )
        put("brazilian jiu-jitsu", activity(MovementPattern.CORE, *grapple))
        put("wrestling", activity(MovementPattern.CORE, *grapple))
        put("judo", activity(MovementPattern.CORE, *grapple))
        // Board and snow sports: a flexed-knee stance balanced over the feet.
        put("skateboarding", activity(MovementPattern.LUNGE, Muscle.QUADS, Muscle.GLUTES, Muscle.CALVES, Muscle.TIBIALIS, Muscle.ABDUCTORS))
        put("snowboarding", activity(MovementPattern.LUNGE, Muscle.QUADS, Muscle.GLUTES, Muscle.CALVES, Muscle.TIBIALIS, Muscle.OBLIQUES))
        put("skiing", activity(MovementPattern.LUNGE, Muscle.QUADS, Muscle.GLUTES, Muscle.ADDUCTORS, Muscle.ABDUCTORS, Muscle.CALVES))
        // Paddling prone, then the pop-up and the stance.
        put("surfing", activity(
            MovementPattern.HORIZONTAL_PULL, Muscle.LATS, Muscle.TRAPS, Muscle.REAR_DELTS, Muscle.LOWER_BACK,
            Muscle.TRICEPS, Muscle.QUADS,
        ))
        put("dancing", activity(MovementPattern.LUNGE, Muscle.CALVES, Muscle.QUADS, Muscle.GLUTES, Muscle.HIP_FLEXORS))
        // ---- Activities: mobility ----
        put("yoga", activity(
            MovementPattern.CORE, Muscle.ABS, Muscle.TRICEPS, Muscle.FRONT_DELTS, Muscle.QUADS, Muscle.GLUTES,
            Muscle.HAMSTRINGS,
        ))
        put("pilates", activity(MovementPattern.CORE, Muscle.ABS, Muscle.OBLIQUES, Muscle.HIP_FLEXORS, Muscle.GLUTES, Muscle.LOWER_BACK))
        put("stretching", activity(
            MovementPattern.CORE, Muscle.HAMSTRINGS, Muscle.HIP_FLEXORS, Muscle.CALVES, Muscle.ADDUCTORS,
            Muscle.LOWER_BACK,
        ))
        put("mobility flow", activity(
            MovementPattern.CORE, Muscle.HIP_FLEXORS, Muscle.GLUTES, Muscle.ADDUCTORS, Muscle.LOWER_BACK,
            Muscle.ROTATOR_CUFF, Muscle.ABS,
        ))
    }

    /** The conventional pull: a hinge from a flexed hip, so the adductor magnus extends it too (see romanian deadlift). */
    private fun deadlift() = ExerciseProfile(
        muscles = mapOf(
            Muscle.HAMSTRINGS to 1.0, Muscle.GLUTES to 1.0, Muscle.LOWER_BACK to 1.0,
            Muscle.RHOMBOIDS to 0.5, Muscle.TRAPS to 0.5, Muscle.QUADS to 0.5,
            Muscle.FOREARMS to 0.5, Muscle.ADDUCTORS to 0.5,
        ),
        pattern = MovementPattern.HINGE, compound = true, stretchBias = true,
    )

    /**
     * Holds and activities outside the lever and planche lines. Every share
     * is the helper 0.5 unless a muscle plainly leads: no longitudinal trial
     * measured growth from any of them, and not stretch biased - the
     * lengthened-position trials were all dynamic.
     */
    private fun hold(
        vararg muscles: Pair<Muscle, Double>,
        pattern: MovementPattern,
        compound: Boolean = true,
    ) = ExerciseProfile(muscles = linkedMapOf(*muscles), pattern = pattern, compound = compound, stretchBias = false)

    /**
     * Cardio, sport, climbing, water and mobility sessions: the prime movers,
     * dominant first, each at the helper 0.5 - practised submaximally, not
     * sets taken near failure. Compound: the whole body works together.
     * A pulling activity takes HORIZONTAL_PULL, never VERTICAL_PULL: the
     * pull-up spacing rule ([ProgramGenerator.isPullUpVariant]) must not
     * read a swim or a climb as a pull-up.
     */
    private fun activity(pattern: MovementPattern, vararg muscles: Muscle) = ExerciseProfile(
        muscles = linkedMapOf(*muscles.map { it to 0.5 }.toTypedArray()),
        pattern = pattern, compound = true, stretchBias = false,
    )

    /** Handstand holds: the front delts hold the arms overhead, the blade upward-rotated. */
    private fun handstandHold(vararg extra: Muscle) = hold(
        Muscle.FRONT_DELTS to 1.0, Muscle.TRAPS to 0.5, Muscle.SERRATUS to 0.5, Muscle.TRICEPS to 0.5,
        Muscle.ROTATOR_CUFF to 0.5, Muscle.FOREARMS to 0.5, *extra.map { it to 0.5 }.toTypedArray(),
        pattern = MovementPattern.VERTICAL_PUSH,
    )

    /** Support holds at the top of a dip: the blade held down, the elbows locked. */
    private fun supportHold(vararg extra: Muscle) = hold(
        Muscle.LOWER_CHEST to 0.5, Muscle.TRICEPS to 0.5, Muscle.TRAPS to 0.5, Muscle.FRONT_DELTS to 0.5,
        *extra.map { it to 0.5 }.toTypedArray(),
        pattern = MovementPattern.HORIZONTAL_PUSH,
    )

    /** Bent-arm balances (Crow, Frog Stand): the knees rest on the arms, the trunk holds the tuck. */
    private fun armBalance() = hold(
        Muscle.TRICEPS to 0.5, Muscle.FRONT_DELTS to 0.5, Muscle.SERRATUS to 0.5, Muscle.ABS to 0.5,
        Muscle.FOREARMS to 0.5,
        pattern = MovementPattern.HORIZONTAL_PUSH,
    )

    /**
     * L-sit holds: the hip flexors lift the legs while the abs curl the
     * pelvis. [triceps] when the hands press the floor: the elbows stay
     * locked under the whole body, as in the manna.
     */
    private fun lSit(triceps: Boolean = false) = ExerciseProfile(
        muscles = linkedMapOf(Muscle.ABS to 1.0, Muscle.HIP_FLEXORS to 1.0).apply {
            if (triceps) put(Muscle.TRICEPS, 0.5)
            put(Muscle.FOREARMS, 0.5)
            put(Muscle.QUADS, 0.5)
        },
        pattern = MovementPattern.CORE, compound = false, stretchBias = false,
    )

    /**
     * Front levers: a straight-arm shoulder extension held against the whole
     * body, so the lats lead. The rear delts and the triceps' long head
     * extend the shoulder beside them; the rhomboids and traps pin the blade
     * back and down; the abs hold the body flat; the grip and the cuff hold
     * the load as in every bar pull. No biceps or brachialis: the elbow is
     * locked, not flexing.
     */
    private fun frontLever() = ExerciseProfile(
        muscles = mapOf(
            Muscle.LATS to 1.0, Muscle.REAR_DELTS to 0.5, Muscle.RHOMBOIDS to 0.5,
            Muscle.TRAPS to 0.5, Muscle.TRICEPS to 0.5, Muscle.ABS to 0.5,
            Muscle.FOREARMS to 0.5, Muscle.ROTATOR_CUFF to 0.5,
        ),
        pattern = MovementPattern.VERTICAL_PULL, compound = true, stretchBias = false,
    )

    /**
     * Back levers: the arm is held behind the body and the flexors and
     * adductors stop it going further - the dip's bottom position with
     * straight arms. No muscle is shown to lead, so all share the helper
     * 0.5 (conservative): the costal chest drawing the arm to the hips, the
     * front delts and the biceps' long head flexing the shoulder, the lats
     * adducting it, and the grip.
     */
    private fun backLever() = ExerciseProfile(
        muscles = mapOf(
            Muscle.LOWER_CHEST to 0.5, Muscle.FRONT_DELTS to 0.5, Muscle.BICEPS to 0.5,
            Muscle.LATS to 0.5, Muscle.FOREARMS to 0.5,
        ),
        pattern = MovementPattern.HORIZONTAL_PUSH, compound = true, stretchBias = false,
    )

    /**
     * Planches: a straight-arm shoulder flexion hold leaning past the hands,
     * so the front delts lead. The clavicular chest and the biceps' long
     * head flex the shoulder beside them, the serratus holds the blade
     * protracted, the abs hold the body flat. No triceps share (conservative):
     * the elbow is locked, not extending.
     */
    private fun planche() = ExerciseProfile(
        muscles = mapOf(
            Muscle.FRONT_DELTS to 1.0, Muscle.UPPER_CHEST to 0.5, Muscle.SERRATUS to 0.5,
            Muscle.BICEPS to 0.5, Muscle.ABS to 0.5,
        ),
        pattern = MovementPattern.HORIZONTAL_PUSH, compound = true, stretchBias = false,
    )

    private fun coreHanging() = ExerciseProfile(
        muscles = mapOf(
            Muscle.ABS to 1.0, Muscle.HIP_FLEXORS to 1.0, Muscle.OBLIQUES to 0.5,
            Muscle.LATS to 0.5, Muscle.FOREARMS to 0.5,
        ),
        pattern = MovementPattern.CORE, compound = false, stretchBias = true,
    )

    /**
     * The measured bench/push-up family credits (Lanza 2024; Kikuchi 2017),
     * with the flat press's sternal chest at 1.0 and the clavicular and
     * costal regions assisting at 0.5. A push-up leaves the shoulder blade
     * free, so the serratus protracts it through every rep; a bench pins it.
     */
    private fun pressFamily(pattern: MovementPattern, pushUp: Boolean = false) = ExerciseProfile(
        muscles = buildMap {
            put(Muscle.MID_CHEST, 1.0)
            put(Muscle.UPPER_CHEST, 0.5)
            put(Muscle.LOWER_CHEST, 0.5)
            put(Muscle.FRONT_DELTS, 0.7)
            put(Muscle.TRICEPS, 0.6)
            put(Muscle.SIDE_DELTS, 0.3)
            if (pushUp) put(Muscle.SERRATUS, 0.5)
        },
        pattern = pattern, compound = true, stretchBias = true,
    )

    /**
     * A flat chest movement pressed at an angle: [region] rises to 1.0 and
     * leads (first in the map, so it is the dominant muscle); every other
     * share stays - Chaves 2020 found the incline grew the other sites as
     * much as the flat press did.
     */
    private fun angled(profile: ExerciseProfile, region: Muscle) = profile.copy(
        muscles = linkedMapOf(region to 1.0) + profile.muscles.filterKeys { it != region },
    )

    /** Incline presses: the flat press with the clavicular chest leading (Chaves 2020). */
    private fun inclinePress() = angled(pressFamily(MovementPattern.HORIZONTAL_PUSH), Muscle.UPPER_CHEST)

    /**
     * Every dip: the costal chest leads with the triceps (the arm drives
     * down toward the hips), the sternal chest assists. LOWER_CHEST first,
     * so the chest region is the dip's dominant muscle.
     */
    private fun dip() = ExerciseProfile(
        muscles = mapOf(
            Muscle.LOWER_CHEST to 1.0, Muscle.TRICEPS to 1.0, Muscle.MID_CHEST to 0.5,
            Muscle.FRONT_DELTS to 0.5,
        ),
        pattern = MovementPattern.HORIZONTAL_PUSH, compound = true, stretchBias = true,
    )

    /** Flyes and the pec deck: horizontal adduction, sternal-led like the flat press. */
    private fun fly() = ExerciseProfile(
        muscles = mapOf(
            Muscle.MID_CHEST to 1.0, Muscle.UPPER_CHEST to 0.5, Muscle.LOWER_CHEST to 0.5,
            Muscle.FRONT_DELTS to 0.5,
        ),
        pattern = MovementPattern.ISOLATION, compound = false, stretchBias = true,
    )

    /**
     * Overhead pressing: the blade must upward-rotate to reach lockout,
     * which is the traps' and the serratus's work (0.5 each). No upper-chest
     * share: past about 90 degrees of flexion the clavicular pec is a minor
     * contributor, and no longitudinal trial credits it here.
     */
    private fun verticalPress() = ExerciseProfile(
        muscles = mapOf(
            Muscle.FRONT_DELTS to 1.0, Muscle.TRICEPS to 0.5, Muscle.SIDE_DELTS to 0.5,
            Muscle.TRAPS to 0.5, Muscle.SERRATUS to 0.5, Muscle.ROTATOR_CUFF to 0.5,
        ),
        pattern = MovementPattern.VERTICAL_PUSH, compound = true, stretchBias = true,
    )

    /** Press-to-handstand skills: front delts lead, the serratus holds the blade up. */
    private fun handstandPress() = ExerciseProfile(
        muscles = mapOf(
            Muscle.FRONT_DELTS to 1.0, Muscle.TRICEPS to 0.5, Muscle.ABS to 0.5,
            Muscle.SERRATUS to 0.5, Muscle.ROTATOR_CUFF to 0.5,
        ),
        pattern = MovementPattern.VERTICAL_PUSH, compound = true, stretchBias = true,
    )

    /** Pull-ups, chin-ups, pulldowns and their archer / L-sit variants. The one-arm work has no rear-delt share. */
    private fun verticalPull(rearDelts: Boolean = true) = ExerciseProfile(
        muscles = buildMap {
            put(Muscle.LATS, 1.0)
            put(Muscle.RHOMBOIDS, 0.5)
            put(Muscle.TRAPS, 0.5)
            put(Muscle.BICEPS, 0.5)
            if (rearDelts) put(Muscle.REAR_DELTS, 0.5)
            put(Muscle.BRACHIALIS, 0.5)
            put(Muscle.FOREARMS, 0.5)
            put(Muscle.ROTATOR_CUFF, 0.5)
        },
        pattern = MovementPattern.VERTICAL_PULL, compound = true, stretchBias = true,
    )

    /** Muscle-ups: a pull-up with a press on top ([top] is the pressing muscle). */
    private fun muscleUp(top: Muscle) = ExerciseProfile(
        muscles = mapOf(
            Muscle.LATS to 1.0, Muscle.RHOMBOIDS to 0.5, Muscle.TRAPS to 0.5, Muscle.BICEPS to 0.5, top to 0.5,
            Muscle.BRACHIALIS to 0.5, Muscle.FOREARMS to 0.5, Muscle.ROTATOR_CUFF to 0.5,
        ),
        pattern = MovementPattern.VERTICAL_PULL, compound = true, stretchBias = true,
    )

    /** Horizontal rows: the rhomboids lead, the mid traps squeeze beside them. Bent-over rows also brace the lower back. */
    private fun row(lowerBack: Boolean = false) = ExerciseProfile(
        muscles = buildMap {
            put(Muscle.RHOMBOIDS, 1.0)
            put(Muscle.TRAPS, 0.5)
            put(Muscle.LATS, 0.5)
            put(Muscle.BICEPS, 0.5)
            put(Muscle.REAR_DELTS, 0.5)
            if (lowerBack) put(Muscle.LOWER_BACK, 0.5)
            put(Muscle.BRACHIALIS, 0.5)
            put(Muscle.FOREARMS, 0.5)
            put(Muscle.ROTATOR_CUFF, 0.5)
        },
        pattern = MovementPattern.HORIZONTAL_PULL, compound = true, stretchBias = true,
    )

    /** Supinated curls: the biceps leads, the brachialis flexes beside it. */
    private fun curl() = ExerciseProfile(
        muscles = mapOf(Muscle.BICEPS to 1.0, Muscle.BRACHIALIS to 0.5, Muscle.FOREARMS to 0.5),
        pattern = MovementPattern.ISOLATION, compound = false, stretchBias = false,
    )

    private fun shrug() = ExerciseProfile(
        muscles = mapOf(Muscle.TRAPS to 1.0, Muscle.FOREARMS to 0.5),
        pattern = MovementPattern.ISOLATION, compound = false, stretchBias = false,
    )

    /** Step-ups: one leg, so the glute med steadies the pelvis. */
    private fun stepUp() = ExerciseProfile(
        muscles = mapOf(Muscle.QUADS to 1.0, Muscle.GLUTES to 1.0, Muscle.ABDUCTORS to 0.5),
        pattern = MovementPattern.LUNGE, compound = true, stretchBias = false,
    )

    /**
     * Bilateral free squats taken to depth (the guides ask for thighs at
     * least parallel): ADDUCTORS 0.75. Kubo 2019's full squat grew the
     * adductors about as much as the glutes, its half squat well under
     * half that; parallel-or-deeper sits between, so the worked-hard step
     * and not a lead. The leg press and hack squat keep 0.5 (the pad limits
     * depth), the front squat too (its upright torso is also why its glutes
     * are 0.5), and the single-leg squats 0.5 (no trial measured them).
     */
    private fun deepSquat() = squatProfile(MovementPattern.SQUAT, adductors = 0.75)

    /**
     * Squat-pattern shares per Kubo 2019: large quad/glute/adductor growth,
     * HAMSTRINGS EXPLICITLY ZERO - squatting did not meaningfully grow them,
     * so hinge or leg-curl work is the only honest hamstring source. The
     * single-leg (LUNGE) versions add ABDUCTORS 0.5: the glute med holds the
     * pelvis level over one foot.
     */
    private fun squatProfile(pattern: MovementPattern, glutes: Double = 1.0, adductors: Double = 0.5) = ExerciseProfile(
        muscles = buildMap {
            put(Muscle.QUADS, 1.0)
            put(Muscle.GLUTES, glutes)
            put(Muscle.ADDUCTORS, adductors)
            put(Muscle.HAMSTRINGS, 0.0)
            if (pattern == MovementPattern.LUNGE) put(Muscle.ABDUCTORS, 0.5)
        },
        pattern = pattern, compound = true, stretchBias = true,
    )
}
