package com.ironvellum.app.domain

/**
 * Per-muscle contribution of every movement the generator may prescribe.
 *
 * THE SCALE (evidence brief section 9.4) - three levels anchored on the
 * fractional-set method of Pelland 2026, which compared exactly 1 / 0.5 / 0
 * and found the fractional model strongest:
 *  - 1.0 direct: the muscle is loaded through a long ROM at or near full
 *    stretch, with measured or hinge-equivalent longitudinal data behind it;
 *  - 0.5 indirect: the muscle assists or works at short length / submaximally;
 *  - 0 absent: not meaningfully loaded (hamstrings from squats - Kubo 2019).
 * Finer sub-levels (0.3 / 0.7) appear ONLY where a longitudinal trial
 * measured them: the bench/push-up press family (pecs 1.0, front delts 0.7,
 * triceps 0.6, side delts 0.3 - Lanza 2024) and the calf raises (standing
 * calf raise half-credits soleus - Kinoshita 2023).
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
 * works the sternal middle with both neighbours assisting. Rows and pulls
 * retract the blade (rhomboids and mid traps); shrugs elevate it (upper
 * traps); overhead lockouts and hinges hold it (traps 0.5). Serratus
 * protracts and upward-rotates the blade (push-ups, overhead and handstand
 * pressing), the rotator cuff externally rotates the humerus, the brachialis
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
     * Case-insensitive lookup. Returns null for anything the generator never
     * prescribes a profile for: activities, most holds, milestone rows, and
     * any user-created movement - improve() passes those through untouched.
     * The few holds profiled here (L-sit, Side Plank) only count toward the
     * coverage of a lifter's own presets; the generator never doses a hold.
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
        put("archer pull-up", verticalPull(rearDelts = false))
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
            pattern = MovementPattern.HORIZONTAL_PUSH, compound = true, stretchBias = true,
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
            muscles = mapOf(Muscle.ROTATOR_CUFF to 1.0, Muscle.TRAPS to 0.5, Muscle.REAR_DELTS to 0.5),
            pattern = MovementPattern.ISOLATION, compound = false, stretchBias = false,
        ))
        put("dumbbell external rotation", ExerciseProfile(
            muscles = mapOf(Muscle.ROTATOR_CUFF to 1.0),
            pattern = MovementPattern.ISOLATION, compound = false, stretchBias = false,
        ))
        // ---- Base catalogue: legs ----
        put("pistol squat", squatProfile(pattern = MovementPattern.LUNGE))
        put("back squat", squatProfile(MovementPattern.SQUAT))
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
            // Driving the knee over the toes is active dorsiflexion.
            muscles = mapOf(Muscle.CALVES to 0.5, Muscle.TIBIALIS to 0.5),
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
            muscles = mapOf(Muscle.ADDUCTORS to 1.0, Muscle.QUADS to 0.5, Muscle.GLUTES to 0.5),
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
        put("deadlift", ExerciseProfile(
            muscles = mapOf(
                Muscle.HAMSTRINGS to 1.0, Muscle.GLUTES to 1.0, Muscle.LOWER_BACK to 1.0,
                Muscle.RHOMBOIDS to 0.5, Muscle.TRAPS to 0.5, Muscle.QUADS to 0.5,
                Muscle.FOREARMS to 0.5,
            ),
            pattern = MovementPattern.HINGE, compound = true, stretchBias = true,
        ))
        put("romanian deadlift", ExerciseProfile(
            muscles = mapOf(Muscle.HAMSTRINGS to 1.0, Muscle.GLUTES to 1.0, Muscle.LOWER_BACK to 0.5),
            pattern = MovementPattern.HINGE, compound = true, stretchBias = true,
        ))
        put("front squat", squatProfile(MovementPattern.SQUAT, glutes = 0.5))
        put("hip thrust", ExerciseProfile(
            muscles = mapOf(Muscle.GLUTES to 1.0, Muscle.HAMSTRINGS to 0.5, Muscle.ADDUCTORS to 0.5),
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
        put("l-sit", ExerciseProfile(
            muscles = mapOf(
                Muscle.ABS to 1.0, Muscle.HIP_FLEXORS to 1.0, Muscle.FOREARMS to 0.5,
                Muscle.QUADS to 0.5,
            ),
            pattern = MovementPattern.CORE, compound = false, stretchBias = false,
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
                Muscle.HAMSTRINGS to 0.5, Muscle.GLUTES to 1.0, Muscle.ADDUCTORS to 1.0,
                Muscle.QUADS to 0.5, Muscle.LOWER_BACK to 0.5,
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
            muscles = mapOf(Muscle.HAMSTRINGS to 1.0, Muscle.LOWER_BACK to 1.0, Muscle.GLUTES to 0.5),
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
                Muscle.TRAPS to 0.5, Muscle.SERRATUS to 0.5,
            ),
            pattern = MovementPattern.VERTICAL_PUSH, compound = true, stretchBias = true,
        ))
        put("lateral raise", ExerciseProfile(
            // Presses grow little medial delt (Lanza 2024 negative control),
            // so the isolation earns its full 1.0.
            muscles = mapOf(Muscle.SIDE_DELTS to 1.0),
            pattern = MovementPattern.ISOLATION, compound = false, stretchBias = false,
        ))
        put("front raise", ExerciseProfile(
            muscles = mapOf(Muscle.FRONT_DELTS to 1.0),
            pattern = MovementPattern.ISOLATION, compound = false, stretchBias = false,
        ))
        put("reverse fly", ExerciseProfile(
            muscles = mapOf(Muscle.REAR_DELTS to 1.0, Muscle.RHOMBOIDS to 0.5, Muscle.ROTATOR_CUFF to 0.5),
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
            // (forearms) works hard beside the brachialis.
            muscles = mapOf(Muscle.BRACHIALIS to 1.0, Muscle.FOREARMS to 0.5, Muscle.BICEPS to 0.5),
            pattern = MovementPattern.ISOLATION, compound = false, stretchBias = false,
        ))
        put("preacher curl", ExerciseProfile(
            muscles = mapOf(Muscle.BICEPS to 1.0, Muscle.BRACHIALIS to 0.5),
            pattern = MovementPattern.ISOLATION, compound = false, stretchBias = true,
        ))
        put("dumbbell shrug", shrug())
        put("goblet squat", squatProfile(MovementPattern.SQUAT))
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
            muscles = mapOf(Muscle.SIDE_DELTS to 1.0),
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
        put("smith machine squat", squatProfile(MovementPattern.SQUAT))
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
        put("l-sit pull-up", ExerciseProfile(
            muscles = mapOf(
                Muscle.LATS to 1.0, Muscle.RHOMBOIDS to 0.5, Muscle.BICEPS to 0.5, Muscle.ABS to 0.5,
                Muscle.HIP_FLEXORS to 0.5, Muscle.BRACHIALIS to 0.5, Muscle.FOREARMS to 0.5,
            ),
            pattern = MovementPattern.VERTICAL_PULL, compound = true, stretchBias = true,
        ))
        put("one-arm negative", verticalPull(rearDelts = false))
        put("one-arm pull-up", verticalPull(rearDelts = false))
        // Hands up, feet down: the body angle of a decline press.
        put("incline push-up", angled(pressFamily(MovementPattern.HORIZONTAL_PUSH, pushUp = true), Muscle.LOWER_CHEST))
        put("one-arm negative push-up", pressFamily(MovementPattern.HORIZONTAL_PUSH, pushUp = true))
        put("one-arm push-up", pressFamily(MovementPattern.HORIZONTAL_PUSH, pushUp = true))
        put("bench dip", ExerciseProfile(
            // Triceps-led; the chest share is the dip's costal fibres.
            muscles = mapOf(Muscle.TRICEPS to 1.0, Muscle.LOWER_CHEST to 0.5, Muscle.FRONT_DELTS to 0.5),
            pattern = MovementPattern.ISOLATION, compound = false, stretchBias = false,
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
            muscles = mapOf(Muscle.LOWER_BACK to 0.5, Muscle.ABS to 0.5, Muscle.FRONT_DELTS to 0.5),
            pattern = MovementPattern.CORE, compound = false, stretchBias = true,
        ))
        put("stand-to-stand bridge", ExerciseProfile(
            muscles = mapOf(Muscle.LOWER_BACK to 1.0, Muscle.FRONT_DELTS to 0.5, Muscle.GLUTES to 0.5),
            pattern = MovementPattern.CORE, compound = false, stretchBias = true,
        ))
        put("bodyweight squat", squatProfile(MovementPattern.SQUAT))
        put("split squat", squatProfile(MovementPattern.LUNGE))
        put("sissy squat", ExerciseProfile(
            muscles = mapOf(Muscle.QUADS to 1.0),
            pattern = MovementPattern.ISOLATION, compound = false, stretchBias = true,
        ))
        put("shrimp squat", ExerciseProfile(
            muscles = mapOf(Muscle.QUADS to 1.0, Muscle.GLUTES to 1.0, Muscle.ABDUCTORS to 0.5),
            pattern = MovementPattern.LUNGE, compound = true, stretchBias = true,
        ))
        put("dragon squat", squatProfile(MovementPattern.LUNGE))
        put("hamstring bridge", ExerciseProfile(
            muscles = mapOf(Muscle.HAMSTRINGS to 1.0, Muscle.GLUTES to 0.5),
            pattern = MovementPattern.HINGE, compound = false, stretchBias = false,
        ))
        put("nordic negative", ExerciseProfile(
            muscles = mapOf(Muscle.HAMSTRINGS to 1.0),
            pattern = MovementPattern.ISOLATION, compound = false, stretchBias = true,
        ))
    }

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
            Muscle.TRAPS to 0.5, Muscle.SERRATUS to 0.5,
        ),
        pattern = MovementPattern.VERTICAL_PUSH, compound = true, stretchBias = true,
    )

    /** Press-to-handstand skills: front delts lead, the serratus holds the blade up. */
    private fun handstandPress() = ExerciseProfile(
        muscles = mapOf(
            Muscle.FRONT_DELTS to 1.0, Muscle.TRICEPS to 0.5, Muscle.ABS to 0.5,
            Muscle.SERRATUS to 0.5,
        ),
        pattern = MovementPattern.VERTICAL_PUSH, compound = true, stretchBias = true,
    )

    /** Pull-ups, chin-ups and pulldowns. The one-arm and archer work has no rear-delt share. */
    private fun verticalPull(rearDelts: Boolean = true) = ExerciseProfile(
        muscles = buildMap {
            put(Muscle.LATS, 1.0)
            put(Muscle.RHOMBOIDS, 0.5)
            put(Muscle.BICEPS, 0.5)
            if (rearDelts) put(Muscle.REAR_DELTS, 0.5)
            put(Muscle.BRACHIALIS, 0.5)
            put(Muscle.FOREARMS, 0.5)
        },
        pattern = MovementPattern.VERTICAL_PULL, compound = true, stretchBias = true,
    )

    /** Muscle-ups: a pull-up with a press on top ([top] is the pressing muscle). */
    private fun muscleUp(top: Muscle) = ExerciseProfile(
        muscles = mapOf(
            Muscle.LATS to 1.0, Muscle.RHOMBOIDS to 0.5, Muscle.BICEPS to 0.5, top to 0.5,
            Muscle.BRACHIALIS to 0.5, Muscle.FOREARMS to 0.5,
        ),
        pattern = MovementPattern.VERTICAL_PULL, compound = true, stretchBias = true,
    )

    /** Horizontal rows: rhomboids and mid traps lead. Bent-over rows also brace the lower back. */
    private fun row(lowerBack: Boolean = false) = ExerciseProfile(
        muscles = buildMap {
            put(Muscle.RHOMBOIDS, 1.0)
            put(Muscle.LATS, 0.5)
            put(Muscle.BICEPS, 0.5)
            put(Muscle.REAR_DELTS, 0.5)
            if (lowerBack) put(Muscle.LOWER_BACK, 0.5)
            put(Muscle.BRACHIALIS, 0.5)
            put(Muscle.FOREARMS, 0.5)
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
     * Squat-pattern shares per Kubo 2019: large quad/glute/adductor growth,
     * HAMSTRINGS EXPLICITLY ZERO - squatting did not meaningfully grow them,
     * so hinge or leg-curl work is the only honest hamstring source. The
     * single-leg (LUNGE) versions add ABDUCTORS 0.5: the glute med holds the
     * pelvis level over one foot.
     */
    private fun squatProfile(pattern: MovementPattern, glutes: Double = 1.0) = ExerciseProfile(
        muscles = buildMap {
            put(Muscle.QUADS, 1.0)
            put(Muscle.GLUTES, glutes)
            put(Muscle.ADDUCTORS, 0.5)
            put(Muscle.HAMSTRINGS, 0.0)
            if (pattern == MovementPattern.LUNGE) put(Muscle.ABDUCTORS, 0.5)
        },
        pattern = pattern, compound = true, stretchBias = true,
    )
}
