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
     * prescribes a profile for: activities, holds, milestone rows, and any
     * user-created movement - improve() passes those through untouched.
     */
    fun profile(exerciseName: String): ExerciseProfile? = profiles[key(exerciseName)]

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
        put("pull-up", ExerciseProfile(
            muscles = mapOf(Muscle.LATS to 1.0, Muscle.UPPER_BACK to 0.5, Muscle.BICEPS to 0.5, Muscle.REAR_DELTS to 0.5, Muscle.FOREARMS to 0.5),
            pattern = MovementPattern.VERTICAL_PULL, compound = true, stretchBias = true,
        ))
        put("chin-up", ExerciseProfile(
            muscles = mapOf(Muscle.LATS to 1.0, Muscle.UPPER_BACK to 0.5, Muscle.BICEPS to 0.5, Muscle.REAR_DELTS to 0.5, Muscle.FOREARMS to 0.5),
            pattern = MovementPattern.VERTICAL_PULL, compound = true, stretchBias = true,
        ))
        put("archer pull-up", ExerciseProfile(
            muscles = mapOf(Muscle.LATS to 1.0, Muscle.UPPER_BACK to 0.5, Muscle.BICEPS to 0.5, Muscle.FOREARMS to 0.5),
            pattern = MovementPattern.VERTICAL_PULL, compound = true, stretchBias = true,
        ))
        put("inverted row", ExerciseProfile(
            muscles = mapOf(Muscle.UPPER_BACK to 1.0, Muscle.LATS to 0.5, Muscle.BICEPS to 0.5, Muscle.REAR_DELTS to 0.5, Muscle.FOREARMS to 0.5),
            pattern = MovementPattern.HORIZONTAL_PULL, compound = true, stretchBias = true,
        ))
        put("door sheet row", ExerciseProfile(
            muscles = mapOf(Muscle.UPPER_BACK to 1.0, Muscle.LATS to 0.5, Muscle.BICEPS to 0.5, Muscle.REAR_DELTS to 0.5, Muscle.FOREARMS to 0.5),
            pattern = MovementPattern.HORIZONTAL_PULL, compound = true, stretchBias = true,
        ))
        put("wrist curl", ExerciseProfile(
            muscles = mapOf(Muscle.FOREARMS to 1.0),
            pattern = MovementPattern.ISOLATION, compound = false, stretchBias = true,
        ))
        put("bicep curl", ExerciseProfile(
            muscles = mapOf(Muscle.BICEPS to 1.0, Muscle.FOREARMS to 0.5),
            pattern = MovementPattern.ISOLATION, compound = false, stretchBias = false,
        ))
        put("barbell row", ExerciseProfile(
            muscles = mapOf(
                Muscle.UPPER_BACK to 1.0, Muscle.LATS to 0.5, Muscle.BICEPS to 0.5,
                Muscle.REAR_DELTS to 0.5, Muscle.LOWER_BACK to 0.5,
                Muscle.FOREARMS to 0.5,
            ),
            pattern = MovementPattern.HORIZONTAL_PULL, compound = true, stretchBias = true,
        ))
        put("dumbbell row", ExerciseProfile(
            muscles = mapOf(Muscle.UPPER_BACK to 1.0, Muscle.LATS to 0.5, Muscle.BICEPS to 0.5, Muscle.REAR_DELTS to 0.5, Muscle.FOREARMS to 0.5),
            pattern = MovementPattern.HORIZONTAL_PULL, compound = true, stretchBias = true,
        ))
        put("lat pulldown", ExerciseProfile(
            muscles = mapOf(Muscle.LATS to 1.0, Muscle.UPPER_BACK to 0.5, Muscle.BICEPS to 0.5, Muscle.REAR_DELTS to 0.5, Muscle.FOREARMS to 0.5),
            pattern = MovementPattern.VERTICAL_PULL, compound = true, stretchBias = true,
        ))
        put("face pull", ExerciseProfile(
            muscles = mapOf(Muscle.REAR_DELTS to 1.0, Muscle.UPPER_BACK to 0.5),
            pattern = MovementPattern.ISOLATION, compound = false, stretchBias = false,
        ))
        // ---- Base catalogue: push ----
        put("dip", ExerciseProfile(
            muscles = mapOf(Muscle.CHEST to 1.0, Muscle.TRICEPS to 1.0, Muscle.FRONT_DELTS to 0.5),
            pattern = MovementPattern.HORIZONTAL_PUSH, compound = true, stretchBias = true,
        ))
        put("push-up", pressFamily(pattern = MovementPattern.HORIZONTAL_PUSH))
        put("archer push-up", pressFamily(MovementPattern.HORIZONTAL_PUSH))
        put("diamond push-up", ExerciseProfile(
            muscles = mapOf(Muscle.TRICEPS to 1.0, Muscle.CHEST to 0.5, Muscle.FRONT_DELTS to 0.5),
            pattern = MovementPattern.HORIZONTAL_PUSH, compound = true, stretchBias = true,
        ))
        put("pike push-up", verticalPress())
        put("handstand push-up", verticalPress())
        put("overhead press", verticalPress())
        put("bench press", pressFamily(MovementPattern.HORIZONTAL_PUSH))
        put("incline bench press", pressFamily(MovementPattern.HORIZONTAL_PUSH))
        // ---- Base catalogue: legs ----
        put("pistol squat", squatProfile(pattern = MovementPattern.LUNGE))
        put("back squat", squatProfile(MovementPattern.SQUAT))
        put("bulgarian split squat", squatProfile(MovementPattern.LUNGE))
        put("single-leg glute bridge", ExerciseProfile(
            muscles = mapOf(Muscle.GLUTES to 1.0, Muscle.HAMSTRINGS to 0.5),
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
            muscles = mapOf(Muscle.CALVES to 0.5),
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
        put("deadlift", ExerciseProfile(
            muscles = mapOf(
                Muscle.HAMSTRINGS to 1.0, Muscle.GLUTES to 1.0, Muscle.LOWER_BACK to 1.0,
                Muscle.UPPER_BACK to 0.5, Muscle.QUADS to 0.5, Muscle.FOREARMS to 0.5,
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
        put("hanging leg raise", coreHanging())
        put("toes-to-bar", coreHanging())
        put("hanging knee raise", ExerciseProfile(
            muscles = mapOf(Muscle.ABS to 1.0, Muscle.FOREARMS to 0.5),
            pattern = MovementPattern.CORE, compound = false, stretchBias = true,
        ))
        put("ab wheel rollout", ExerciseProfile(
            muscles = mapOf(Muscle.ABS to 1.0, Muscle.LATS to 0.5, Muscle.LOWER_BACK to 0.5),
            pattern = MovementPattern.CORE, compound = false, stretchBias = true,
        ))
        put("l-sit", ExerciseProfile(
            muscles = mapOf(Muscle.ABS to 1.0, Muscle.FOREARMS to 0.5, Muscle.QUADS to 0.5),
            pattern = MovementPattern.CORE, compound = false, stretchBias = false,
        ))
        put("dragon flag", ExerciseProfile(
            muscles = mapOf(Muscle.ABS to 1.0, Muscle.LATS to 0.5),
            pattern = MovementPattern.CORE, compound = false, stretchBias = true,
        ))
        // ---- Gym floor: barbell ----
        // stretchBias false: the press loads the triceps with the shoulder
        // neutral, the short-length condition Maeo 2022 compared overhead work
        // against; long-length triceps work is the overhead extension.
        put("close-grip bench press", ExerciseProfile(
            muscles = mapOf(Muscle.TRICEPS to 1.0, Muscle.CHEST to 0.5, Muscle.FRONT_DELTS to 0.5),
            pattern = MovementPattern.HORIZONTAL_PUSH, compound = true, stretchBias = false,
        ))
        put("push press", ExerciseProfile(
            muscles = mapOf(Muscle.FRONT_DELTS to 1.0, Muscle.TRICEPS to 0.5, Muscle.SIDE_DELTS to 0.5, Muscle.QUADS to 0.5),
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
                Muscle.LOWER_BACK to 1.0, Muscle.UPPER_BACK to 0.5, Muscle.GLUTES to 0.5,
                Muscle.HAMSTRINGS to 0.5, Muscle.FOREARMS to 0.5,
            ),
            pattern = MovementPattern.HINGE, compound = true, stretchBias = false,
        ))
        put("pendlay row", ExerciseProfile(
            muscles = mapOf(
                Muscle.UPPER_BACK to 1.0, Muscle.LATS to 0.5, Muscle.BICEPS to 0.5,
                Muscle.REAR_DELTS to 0.5, Muscle.LOWER_BACK to 0.5,
                Muscle.FOREARMS to 0.5,
            ),
            pattern = MovementPattern.HORIZONTAL_PULL, compound = true, stretchBias = true,
        ))
        put("t-bar row", ExerciseProfile(
            muscles = mapOf(
                Muscle.UPPER_BACK to 1.0, Muscle.LATS to 0.5, Muscle.BICEPS to 0.5,
                Muscle.REAR_DELTS to 0.5, Muscle.LOWER_BACK to 0.5,
                Muscle.FOREARMS to 0.5,
            ),
            pattern = MovementPattern.HORIZONTAL_PULL, compound = true, stretchBias = true,
        ))
        put("good morning", ExerciseProfile(
            muscles = mapOf(Muscle.HAMSTRINGS to 1.0, Muscle.LOWER_BACK to 1.0, Muscle.GLUTES to 0.5),
            pattern = MovementPattern.HINGE, compound = true, stretchBias = true,
        ))
        put("barbell lunge", squatProfile(MovementPattern.LUNGE))
        put("barbell step-up", ExerciseProfile(
            muscles = mapOf(Muscle.QUADS to 1.0, Muscle.GLUTES to 1.0),
            pattern = MovementPattern.LUNGE, compound = true, stretchBias = false,
        ))
        put("barbell shrug", ExerciseProfile(
            muscles = mapOf(Muscle.UPPER_BACK to 1.0, Muscle.FOREARMS to 0.5),
            pattern = MovementPattern.ISOLATION, compound = false, stretchBias = false,
        ))
        // ---- Gym floor: dumbbell ----
        put("dumbbell bench press", pressFamily(MovementPattern.HORIZONTAL_PUSH))
        put("incline dumbbell press", pressFamily(MovementPattern.HORIZONTAL_PUSH))
        put("dumbbell shoulder press", verticalPress())
        put("arnold press", ExerciseProfile(
            muscles = mapOf(Muscle.FRONT_DELTS to 1.0, Muscle.SIDE_DELTS to 0.5, Muscle.TRICEPS to 0.5),
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
            muscles = mapOf(Muscle.REAR_DELTS to 1.0, Muscle.UPPER_BACK to 0.5),
            pattern = MovementPattern.ISOLATION, compound = false, stretchBias = false,
        ))
        put("dumbbell fly", ExerciseProfile(
            muscles = mapOf(Muscle.CHEST to 1.0, Muscle.FRONT_DELTS to 0.5),
            pattern = MovementPattern.ISOLATION, compound = false, stretchBias = true,
        ))
        put("hammer curl", ExerciseProfile(
            muscles = mapOf(Muscle.BICEPS to 1.0, Muscle.FOREARMS to 0.5),
            pattern = MovementPattern.ISOLATION, compound = false, stretchBias = false,
        ))
        put("preacher curl", ExerciseProfile(
            muscles = mapOf(Muscle.BICEPS to 1.0),
            pattern = MovementPattern.ISOLATION, compound = false, stretchBias = true,
        ))
        put("dumbbell shrug", ExerciseProfile(
            muscles = mapOf(Muscle.UPPER_BACK to 1.0, Muscle.FOREARMS to 0.5),
            pattern = MovementPattern.ISOLATION, compound = false, stretchBias = false,
        ))
        put("goblet squat", squatProfile(MovementPattern.SQUAT))
        put("walking lunge", squatProfile(MovementPattern.LUNGE))
        put("dumbbell step-up", ExerciseProfile(
            muscles = mapOf(Muscle.QUADS to 1.0, Muscle.GLUTES to 1.0),
            pattern = MovementPattern.LUNGE, compound = true, stretchBias = false,
        ))
        put("triceps kickback", ExerciseProfile(
            muscles = mapOf(Muscle.TRICEPS to 1.0),
            pattern = MovementPattern.ISOLATION, compound = false, stretchBias = false,
        ))
        put("dumbbell pullover", ExerciseProfile(
            muscles = mapOf(Muscle.LATS to 1.0, Muscle.CHEST to 0.5, Muscle.TRICEPS to 0.5),
            pattern = MovementPattern.ISOLATION, compound = false, stretchBias = true,
        ))
        // ---- Gym floor: cable ----
        put("seated cable row", ExerciseProfile(
            muscles = mapOf(Muscle.UPPER_BACK to 1.0, Muscle.LATS to 0.5, Muscle.BICEPS to 0.5, Muscle.REAR_DELTS to 0.5, Muscle.FOREARMS to 0.5),
            pattern = MovementPattern.HORIZONTAL_PULL, compound = true, stretchBias = true,
        ))
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
        put("cable fly", ExerciseProfile(
            muscles = mapOf(Muscle.CHEST to 1.0, Muscle.FRONT_DELTS to 0.5),
            pattern = MovementPattern.ISOLATION, compound = false, stretchBias = true,
        ))
        put("cable lateral raise", ExerciseProfile(
            muscles = mapOf(Muscle.SIDE_DELTS to 1.0),
            pattern = MovementPattern.ISOLATION, compound = false, stretchBias = true,
        ))
        put("cable curl", ExerciseProfile(
            muscles = mapOf(Muscle.BICEPS to 1.0, Muscle.FOREARMS to 0.5),
            pattern = MovementPattern.ISOLATION, compound = false, stretchBias = false,
        ))
        put("cable pull-through", ExerciseProfile(
            muscles = mapOf(Muscle.GLUTES to 1.0, Muscle.HAMSTRINGS to 0.5),
            pattern = MovementPattern.HINGE, compound = false, stretchBias = true,
        ))
        put("woodchop", ExerciseProfile(
            muscles = mapOf(Muscle.ABS to 1.0, Muscle.LOWER_BACK to 0.5),
            pattern = MovementPattern.CORE, compound = false, stretchBias = false,
        ))
        // ---- Gym floor: sled machines ----
        put("leg press", squatProfile(MovementPattern.SQUAT))
        put("hack squat", ExerciseProfile(
            muscles = mapOf(Muscle.QUADS to 1.0, Muscle.GLUTES to 0.5, Muscle.ADDUCTORS to 0.5, Muscle.HAMSTRINGS to 0.0),
            pattern = MovementPattern.SQUAT, compound = true, stretchBias = true,
        ))
        put("chest-supported row", ExerciseProfile(
            muscles = mapOf(Muscle.UPPER_BACK to 1.0, Muscle.LATS to 0.5, Muscle.BICEPS to 0.5, Muscle.REAR_DELTS to 0.5, Muscle.FOREARMS to 0.5),
            pattern = MovementPattern.HORIZONTAL_PULL, compound = true, stretchBias = true,
        ))
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
        put("pec deck", ExerciseProfile(
            muscles = mapOf(Muscle.CHEST to 1.0, Muscle.FRONT_DELTS to 0.5),
            pattern = MovementPattern.ISOLATION, compound = false, stretchBias = true,
        ))
        put("machine chest press", pressFamily(MovementPattern.HORIZONTAL_PUSH))
        put("machine shoulder press", verticalPress())
        put("machine row", ExerciseProfile(
            muscles = mapOf(Muscle.UPPER_BACK to 1.0, Muscle.LATS to 0.5, Muscle.BICEPS to 0.5, Muscle.REAR_DELTS to 0.5, Muscle.FOREARMS to 0.5),
            pattern = MovementPattern.HORIZONTAL_PULL, compound = true, stretchBias = true,
        ))
        put("hip adduction", ExerciseProfile(
            muscles = mapOf(Muscle.ADDUCTORS to 1.0),
            pattern = MovementPattern.ISOLATION, compound = false, stretchBias = true,
        ))
        put("hip abduction", ExerciseProfile(
            muscles = mapOf(Muscle.GLUTES to 1.0),
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
        put("smith machine row", ExerciseProfile(
            muscles = mapOf(Muscle.UPPER_BACK to 1.0, Muscle.LATS to 0.5, Muscle.BICEPS to 0.5, Muscle.REAR_DELTS to 0.5, Muscle.FOREARMS to 0.5),
            pattern = MovementPattern.HORIZONTAL_PULL, compound = true, stretchBias = true,
        ))
        // ---- Assisted machines ----
        put("assisted pull-up", ExerciseProfile(
            muscles = mapOf(Muscle.LATS to 1.0, Muscle.UPPER_BACK to 0.5, Muscle.BICEPS to 0.5, Muscle.REAR_DELTS to 0.5, Muscle.FOREARMS to 0.5),
            pattern = MovementPattern.VERTICAL_PULL, compound = true, stretchBias = true,
        ))
        put("assisted dip", ExerciseProfile(
            muscles = mapOf(Muscle.CHEST to 1.0, Muscle.TRICEPS to 1.0, Muscle.FRONT_DELTS to 0.5),
            pattern = MovementPattern.HORIZONTAL_PUSH, compound = true, stretchBias = true,
        ))
        // ---- Skill-tree rows (REPS-metric, not milestone-priced) ----
        put("scapular pull", ExerciseProfile(
            muscles = mapOf(Muscle.LATS to 0.5, Muscle.UPPER_BACK to 0.5, Muscle.FOREARMS to 0.5),
            pattern = MovementPattern.VERTICAL_PULL, compound = false, stretchBias = true,
        ))
        put("australian pull-up", ExerciseProfile(
            muscles = mapOf(Muscle.UPPER_BACK to 1.0, Muscle.LATS to 0.5, Muscle.BICEPS to 0.5, Muscle.REAR_DELTS to 0.5, Muscle.FOREARMS to 0.5),
            pattern = MovementPattern.HORIZONTAL_PULL, compound = true, stretchBias = true,
        ))
        put("l-sit pull-up", ExerciseProfile(
            muscles = mapOf(Muscle.LATS to 1.0, Muscle.UPPER_BACK to 0.5, Muscle.BICEPS to 0.5, Muscle.ABS to 0.5, Muscle.FOREARMS to 0.5),
            pattern = MovementPattern.VERTICAL_PULL, compound = true, stretchBias = true,
        ))
        put("one-arm negative", ExerciseProfile(
            muscles = mapOf(Muscle.LATS to 1.0, Muscle.UPPER_BACK to 0.5, Muscle.BICEPS to 0.5, Muscle.FOREARMS to 0.5),
            pattern = MovementPattern.VERTICAL_PULL, compound = true, stretchBias = true,
        ))
        put("one-arm pull-up", ExerciseProfile(
            muscles = mapOf(Muscle.LATS to 1.0, Muscle.UPPER_BACK to 0.5, Muscle.BICEPS to 0.5, Muscle.FOREARMS to 0.5),
            pattern = MovementPattern.VERTICAL_PULL, compound = true, stretchBias = true,
        ))
        put("incline push-up", pressFamily(MovementPattern.HORIZONTAL_PUSH))
        put("one-arm negative push-up", pressFamily(MovementPattern.HORIZONTAL_PUSH))
        put("one-arm push-up", pressFamily(MovementPattern.HORIZONTAL_PUSH))
        put("bench dip", ExerciseProfile(
            muscles = mapOf(Muscle.TRICEPS to 1.0, Muscle.CHEST to 0.5, Muscle.FRONT_DELTS to 0.5),
            pattern = MovementPattern.ISOLATION, compound = false, stretchBias = false,
        ))
        put("parallel bar dip", ExerciseProfile(
            muscles = mapOf(Muscle.CHEST to 1.0, Muscle.TRICEPS to 1.0, Muscle.FRONT_DELTS to 0.5),
            pattern = MovementPattern.HORIZONTAL_PUSH, compound = true, stretchBias = true,
        ))
        put("pike press", verticalPress())
        put("wall hspu", verticalPress())
        put("90-degree push-up", ExerciseProfile(
            muscles = mapOf(Muscle.FRONT_DELTS to 1.0, Muscle.TRICEPS to 0.5, Muscle.CHEST to 0.5),
            pattern = MovementPattern.VERTICAL_PUSH, compound = true, stretchBias = true,
        ))
        put("straddle press to handstand", ExerciseProfile(
            muscles = mapOf(Muscle.FRONT_DELTS to 1.0, Muscle.TRICEPS to 0.5, Muscle.ABS to 0.5),
            pattern = MovementPattern.VERTICAL_PUSH, compound = true, stretchBias = true,
        ))
        put("crow → handstand", ExerciseProfile(
            muscles = mapOf(Muscle.FRONT_DELTS to 1.0, Muscle.TRICEPS to 0.5, Muscle.ABS to 0.5),
            pattern = MovementPattern.VERTICAL_PUSH, compound = true, stretchBias = true,
        ))
        put("planche push-up", ExerciseProfile(
            muscles = mapOf(Muscle.FRONT_DELTS to 1.0, Muscle.CHEST to 1.0, Muscle.TRICEPS to 0.5),
            pattern = MovementPattern.HORIZONTAL_PUSH, compound = true, stretchBias = true,
        ))
        put("skin the cat", ExerciseProfile(
            muscles = mapOf(Muscle.LATS to 1.0, Muscle.UPPER_BACK to 0.5, Muscle.BICEPS to 0.5, Muscle.FOREARMS to 0.5),
            pattern = MovementPattern.VERTICAL_PULL, compound = true, stretchBias = true,
        ))
        put("ring row", ExerciseProfile(
            muscles = mapOf(Muscle.UPPER_BACK to 1.0, Muscle.LATS to 0.5, Muscle.BICEPS to 0.5, Muscle.REAR_DELTS to 0.5, Muscle.FOREARMS to 0.5),
            pattern = MovementPattern.HORIZONTAL_PULL, compound = true, stretchBias = true,
        ))
        put("ring dip", ExerciseProfile(
            muscles = mapOf(Muscle.CHEST to 1.0, Muscle.TRICEPS to 1.0, Muscle.FRONT_DELTS to 0.5),
            pattern = MovementPattern.HORIZONTAL_PUSH, compound = true, stretchBias = true,
        ))
        put("ring muscle-up", ExerciseProfile(
            muscles = mapOf(Muscle.LATS to 1.0, Muscle.UPPER_BACK to 0.5, Muscle.BICEPS to 0.5, Muscle.TRICEPS to 0.5, Muscle.FOREARMS to 0.5),
            pattern = MovementPattern.VERTICAL_PULL, compound = true, stretchBias = true,
        ))
        put("kip-up", ExerciseProfile(
            muscles = mapOf(Muscle.ABS to 1.0, Muscle.QUADS to 0.5, Muscle.LOWER_BACK to 0.5),
            pattern = MovementPattern.CORE, compound = false, stretchBias = false,
        ))
        put("muscle-up", ExerciseProfile(
            muscles = mapOf(Muscle.LATS to 1.0, Muscle.UPPER_BACK to 0.5, Muscle.BICEPS to 0.5, Muscle.TRICEPS to 0.5, Muscle.FOREARMS to 0.5),
            pattern = MovementPattern.VERTICAL_PULL, compound = true, stretchBias = true,
        ))
        put("strict muscle-up", ExerciseProfile(
            muscles = mapOf(Muscle.LATS to 1.0, Muscle.UPPER_BACK to 0.5, Muscle.BICEPS to 0.5, Muscle.TRICEPS to 0.5, Muscle.FOREARMS to 0.5),
            pattern = MovementPattern.VERTICAL_PULL, compound = true, stretchBias = true,
        ))
        put("inverted muscle-up", ExerciseProfile(
            muscles = mapOf(Muscle.LATS to 1.0, Muscle.UPPER_BACK to 0.5, Muscle.BICEPS to 0.5, Muscle.FRONT_DELTS to 0.5, Muscle.FOREARMS to 0.5),
            pattern = MovementPattern.VERTICAL_PULL, compound = true, stretchBias = true,
        ))
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
            muscles = mapOf(Muscle.QUADS to 1.0, Muscle.GLUTES to 1.0),
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
        muscles = mapOf(Muscle.ABS to 1.0, Muscle.LATS to 0.5, Muscle.FOREARMS to 0.5),
        pattern = MovementPattern.CORE, compound = false, stretchBias = true,
    )

    /** The measured bench/push-up family credits (Lanza 2024; Kikuchi 2017). */
    private fun pressFamily(pattern: MovementPattern) = ExerciseProfile(
        muscles = mapOf(
            Muscle.CHEST to 1.0, Muscle.FRONT_DELTS to 0.7,
            Muscle.TRICEPS to 0.6, Muscle.SIDE_DELTS to 0.3,
        ),
        pattern = pattern, compound = true, stretchBias = true,
    )

    private fun verticalPress() = ExerciseProfile(
        muscles = mapOf(Muscle.FRONT_DELTS to 1.0, Muscle.TRICEPS to 0.5, Muscle.SIDE_DELTS to 0.5),
        pattern = MovementPattern.VERTICAL_PUSH, compound = true, stretchBias = true,
    )

    /**
     * Squat-pattern shares per Kubo 2019: large quad/glute/adductor growth,
     * HAMSTRINGS EXPLICITLY ZERO - squatting did not meaningfully grow them,
     * so hinge or leg-curl work is the only honest hamstring source.
     */
    private fun squatProfile(pattern: MovementPattern, glutes: Double = 1.0) = ExerciseProfile(
        muscles = mapOf(
            Muscle.QUADS to 1.0, Muscle.GLUTES to glutes,
            Muscle.ADDUCTORS to 0.5, Muscle.HAMSTRINGS to 0.0,
        ),
        pattern = pattern, compound = true, stretchBias = true,
    )
}
