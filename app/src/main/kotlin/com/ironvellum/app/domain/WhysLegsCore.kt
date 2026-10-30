package com.ironvellum.app.domain

internal fun MutableMap<String, ExerciseWhy>.legsCoreWhys() {
    legsCoreWhys1()
    legsCoreWhys2()
    legsCoreWhys3()
}

private fun MutableMap<String, ExerciseWhy>.legsCoreWhys1() {
    put(
        "pistol squat",
        ExerciseWhy(
            purpose = "Loads the quads and glutes through full depth on one leg, with the side hip and balance keeping you level.",
            pickIt = "Pick it over the Bulgarian split squat to own one-leg depth with no kit; it needs ankle mobility and balance, and load rarely climbs.",
            tags = listOf("One-leg strength", "Balance", "Bodyweight only"),
        ),
    )
    put(
        "back squat",
        ExerciseWhy(
            purpose = "Loads the quads, glutes and adductors deep in knee bend, with a bar you can add to in small steps.",
            pickIt = "Pick it over the front squat for heavier loads and an easier grip; it needs a rack, bracing practice and hip and ankle mobility.",
            tags = listOf("Quad size", "Heavy loading", "Trunk bracing"),
        ),
    )
    put(
        "bulgarian split squat",
        ExerciseWhy(
            purpose = "Loads the quads and glutes deeply on one leg, with the rear foot raised to stretch the back hip and test balance.",
            pickIt = "Pick it over the walking lunge for a deep one-leg set at moderate weight; it tires the legs fast and the stance takes tuning.",
            tags = listOf("Quad size", "Glute size", "One-leg strength"),
        ),
    )
    put(
        "single-leg glute bridge",
        ExerciseWhy(
            purpose = "Trains the glutes in hip extension on one leg, with the hamstrings helping and the side hip holding the pelvis level.",
            pickIt = "Pick it over the glute bridge once that gets easy; it needs no kit, but it is harder to load than the hip thrust.",
            tags = listOf("Glute strength", "Pelvic control", "Bodyweight only"),
        ),
    )
    put(
        "single-leg calf raise",
        ExerciseWhy(
            purpose = "Loads the calves through a full stretch to a high toe rise, using your whole bodyweight on one side.",
            pickIt = "Pick it over two-leg raises once those are easy, with no machine needed; add a dumbbell when bodyweight alone is too light.",
            tags = listOf("Calf size", "Full stretch", "No machine"),
        ),
    )
    put(
        "nordic curl",
        ExerciseWhy(
            purpose = "Loads the hamstrings hard as they resist your falling torso, working them at long muscle length.",
            pickIt = "Pick it for bodyweight hamstring strength where no leg curl machine exists; it is very hard, so start with the nordic negative.",
            tags = listOf("Hamstring strength", "Long-range loading", "Bodyweight only"),
        ),
    )
    put(
        "knee-to-wall dorsiflexion",
        ExerciseWhy(
            purpose = "Drives the knee forward over the toes to open ankle dorsiflexion range; a mobility drill, not a strength set.",
            pickIt = "Pick it to improve ankle range for squat depth using just a wall; it builds no real strength, so pair it with squat work.",
            tags = listOf("Ankle mobility", "Squat depth", "Bodyweight only"),
        ),
    )
    put(
        "glute bridge",
        ExerciseWhy(
            purpose = "Trains the glutes, with the hamstrings helping, in hip extension from the floor with no kit.",
            pickIt = "Pick it as an easy-to-learn glute starter or warm-up; it is lighter than the hip thrust and quickly outgrown without added load.",
            tags = listOf("Glute strength", "Beginner friendly", "Bodyweight only"),
        ),
    )
    put(
        "back extension",
        ExerciseWhy(
            purpose = "Works the spinal erectors by lifting the torso, with the glutes and hamstrings assisting.",
            pickIt = "Pick it for direct lower-back and posterior-chain work with little coordination; it loads far less than a deadlift but builds endurance.",
            tags = listOf("Lower back", "Posterior chain", "Easy to learn"),
        ),
    )
    put(
        "cossack squat",
        ExerciseWhy(
            purpose = "Lengthens the adductors under load in a deep sideways squat, with the quads and glutes working too.",
            pickIt = "Pick it for inner-thigh strength and side-to-side hip range; it needs mobility and is hard to load, so keep reps controlled.",
            tags = listOf("Inner thighs", "Hip mobility", "Side-to-side"),
        ),
    )
    put(
        "deadlift",
        ExerciseWhy(
            purpose = "Lifts a bar from the floor, loading the glutes, hamstrings and lower back, with the grip and upper back holding on.",
            pickIt = "Pick it over the romanian deadlift for heavy floor pulls; it taxes recovery more and needs solid bracing, so keep reps low.",
            tags = listOf("Posterior chain", "Heavy loading", "Grip"),
        ),
    )
    put(
        "romanian deadlift",
        ExerciseWhy(
            purpose = "Loads the hamstrings and glutes at long length through a hip hinge, with the lower back and grip holding the bar.",
            pickIt = "Pick it over the deadlift for hamstring and glute work at lighter weights; it needs a good hinge and grip often limits it.",
            tags = listOf("Hamstring size", "Hip hinge", "Long-range loading"),
        ),
    )
    put(
        "single-leg romanian deadlift",
        ExerciseWhy(
            purpose = "Loads the hamstrings and glutes at long length on one leg, with the side hip and balance keeping you square.",
            pickIt = "Pick it for hinge work with light weight and a balance challenge; it loads less than the two-leg version, so it suits lighter sets.",
            tags = listOf("Single-leg hinge", "Balance", "Light loads"),
        ),
    )
    put(
        "front squat",
        ExerciseWhy(
            purpose = "Loads the quads deep in knee bend with an upright torso, while the trunk works hard to hold the bar.",
            pickIt = "Pick it over the back squat for quad focus and an upright torso; the rack grip needs wrist and shoulder mobility and loads run lower.",
            tags = listOf("Quad size", "Upright torso", "Trunk strength"),
        ),
    )
    put(
        "hip thrust",
        ExerciseWhy(
            purpose = "Loads the glutes hardest at hip lockout, where a squat or deadlift gives them least resistance.",
            pickIt = "Pick it for glute growth with less spinal load than a deadlift; it needs a bar and a bench or couch to brace on, and skips the quads.",
            tags = listOf("Glute size", "Lockout strength", "Low spinal load"),
        ),
    )
    put(
        "tib raise",
        ExerciseWhy(
            purpose = "Works the tibialis anterior, the shin muscle that lifts the toes, by raising the front of the foot against bodyweight.",
            pickIt = "Pick it for direct shin work that squats and calf raises skip; it is light and cheap, but a small muscle group.",
            tags = listOf("Shin strength", "Bodyweight only", "Ankle balance"),
        ),
    )
    put(
        "side-lying hip abduction",
        ExerciseWhy(
            purpose = "Isolates the hip abductors, the muscles at the side of the hip that level the pelvis, by raising the top leg sideways.",
            pickIt = "Pick it for light, direct side-hip work with no kit; it is light and hard to progress, so move to single-leg lifts as you get stronger.",
            tags = listOf("Side hip", "Pelvic stability", "Bodyweight only"),
        ),
    )
    put(
        "sumo deadlift",
        ExerciseWhy(
            purpose = "Pulls from a wide stance with a more upright torso, loading the glutes and adductors, with the quads and back helping.",
            pickIt = "Pick it over the deadlift if a wide stance suits your hips; the pull is shorter, but the hamstrings and back work less.",
            tags = listOf("Glutes", "Inner thighs", "Shorter pull"),
        ),
    )
    put(
        "rack pull",
        ExerciseWhy(
            purpose = "Pulls a heavy bar from knee height or above, loading the lower back, upper back, traps and grip over a shorter range.",
            pickIt = "Pick it to overload the lockout or heavy upper-back work; it skips the floor start, so it supplements the deadlift, not replaces it.",
            tags = listOf("Lower back", "Upper back", "Grip"),
        ),
    )
    put(
        "good morning",
        ExerciseWhy(
            purpose = "Loads the hamstrings and lower back through a hinge with the bar on your back, so the torso works as a long lever.",
            pickIt = "Pick it for hamstring and back work when you cannot deadlift; the long lever means light loads, so progress slowly.",
            tags = listOf("Hamstrings", "Lower back", "Hip hinge"),
        ),
    )
    put(
        "barbell lunge",
        ExerciseWhy(
            purpose = "Loads the quads and glutes in a long stride with a bar on your back, with the side hip steadying you.",
            pickIt = "Pick it for heavy single-leg work once your balance is good; it needs a rack and space, and demands more balance than a squat.",
            tags = listOf("Quad size", "Glute size", "One-leg strength"),
        ),
    )
    put(
        "barbell step-up",
        ExerciseWhy(
            purpose = "Loads the quads and glutes by driving up onto a box with a bar on your back, one leg at a time.",
            pickIt = "Pick it over the lunge for a shorter, controlled one-leg push; box height sets difficulty, but it needs a rack and balance.",
            tags = listOf("One-leg strength", "Glutes", "Easy to scale"),
        ),
    )
    put(
        "goblet squat",
        ExerciseWhy(
            purpose = "Loads the quads and glutes in a deep squat with a weight at your chest, which counterbalances you upright.",
            pickIt = "Pick it to learn the squat or add light volume; the weight you can hold limits loading, so use a back squat for heavy work.",
            tags = listOf("Squat pattern", "Quad size", "Beginner friendly"),
        ),
    )
    put(
        "walking lunge",
        ExerciseWhy(
            purpose = "Trains the quads and glutes through repeated strides, with the side hip steadying each step.",
            pickIt = "Pick it for high-rep leg work or a finisher; it needs space and balance, and it is harder to load heavily than a split squat.",
            tags = listOf("Quad size", "Glutes", "Balance"),
        ),
    )
    put(
        "dumbbell step-up",
        ExerciseWhy(
            purpose = "Loads the quads and glutes by stepping onto a box with dumbbells in hand, one leg at a time.",
            pickIt = "Pick it over lunges for a simple, stable one-leg set with minimal kit; box height sets difficulty and grip may limit the weight.",
            tags = listOf("One-leg strength", "Glutes", "Minimal kit"),
        ),
    )
    put(
        "cable pull-through",
        ExerciseWhy(
            purpose = "Trains the glutes and hamstrings in a hip hinge, with the cable pulling your hips back from between your legs.",
            pickIt = "Pick it to learn the hinge or add glute work with little spinal load; it is lighter than the romanian deadlift, so use that for heavy sets.",
            tags = listOf("Hip hinge", "Glutes", "Easy to learn"),
        ),
    )
    put(
        "leg press",
        ExerciseWhy(
            purpose = "Loads the quads, glutes and adductors in a machine squat, with the seat taking over balance and trunk bracing.",
            pickIt = "Pick it over the back squat for heavy leg volume with no balance demand; foot height shifts emphasis, but it teaches less bracing.",
            tags = listOf("Quad size", "Stable loading", "Beginner friendly"),
        ),
    )
    put(
        "hack squat",
        ExerciseWhy(
            purpose = "Loads the quads deep on a sled with your back supported, with the glutes and adductors helping.",
            pickIt = "Pick it over the leg press for deeper, quad-focused knee bend; it needs a hack machine, but is easier to learn than a back squat.",
            tags = listOf("Quad size", "Deep knee bend", "Stable loading"),
        ),
    )
    put(
        "leg extension",
        ExerciseWhy(
            purpose = "Isolates the quads by straightening the knee against a pad, including the rectus femoris that squats only partly reach.",
            pickIt = "Pick it to add quad volume after squats or when legs are tired; there is no balance demand, but little carryover to other lifts.",
            tags = listOf("Quad size", "Isolation", "Low fatigue"),
        ),
    )
    put(
        "seated leg curl",
        ExerciseWhy(
            purpose = "Isolates the hamstrings by bending the knee with the hips flexed, which loads them at long length.",
            pickIt = "Pick it over the lying leg curl for hamstring growth, since the seated hip angle stretches them more; it needs the seated machine.",
            tags = listOf("Hamstring size", "Long-range loading", "Isolation"),
        ),
    )
    put(
        "lying leg curl",
        ExerciseWhy(
            purpose = "Isolates the hamstrings by bending the knee while lying face down, with the hips extended.",
            pickIt = "Pick it when the seated machine is not available; the hamstrings are less stretched than seated, but it is simple and common.",
            tags = listOf("Hamstring size", "Isolation", "Common machine"),
        ),
    )
    put(
        "hip adduction",
        ExerciseWhy(
            purpose = "Squeezes the thighs together against pads, isolating the inner-thigh adductors.",
            pickIt = "Pick it for direct inner-thigh work that squats and deadlifts only partly cover; it is simple, but a small priority for most people.",
            tags = listOf("Inner thighs", "Isolation", "Easy to learn"),
        ),
    )
    put(
        "hip abduction",
        ExerciseWhy(
            purpose = "Pushes the legs apart against pads, targeting the glute medius and minimus at the side of the hip.",
            pickIt = "Pick it for direct side-hip work with stable resistance; lunges and single-leg lifts cover much of it, so it is optional.",
            tags = listOf("Side hip", "Isolation", "Easy to learn"),
        ),
    )
    put(
        "seated calf raise",
        ExerciseWhy(
            purpose = "Raises the heels with bent knees, which slackens the gastrocnemius and shifts work to the soleus.",
            pickIt = "Pick it alongside standing raises to cover the soleus; on its own it builds the calf less than standing raises do.",
            tags = listOf("Soleus", "Calf size", "Isolation"),
        ),
    )
    put(
        "standing calf raise",
        ExerciseWhy(
            purpose = "Raises the heels with straight knees, loading the gastrocnemius and soleus through a full stretch.",
            pickIt = "Pick it first for calf size, since the straight knee loads the big gastrocnemius; a step or machine gives full range.",
            tags = listOf("Calf size", "Full stretch", "Heavy loading"),
        ),
    )
    put(
        "smith machine squat",
        ExerciseWhy(
            purpose = "Squats on a fixed bar path, loading the quads, glutes and adductors without free-bar balance.",
            pickIt = "Pick it over the back squat for stable quad work or training alone; the fixed path forces one foot position and hides balance.",
            tags = listOf("Quad size", "Stable loading", "Solo friendly"),
        ),
    )
    put(
        "bodyweight squat",
        ExerciseWhy(
            purpose = "Squats to a full bend under your own load, working the quads, glutes and adductors and teaching the pattern.",
            pickIt = "Pick it to learn the squat, warm up or train with no kit; it is too light for strength once you are conditioned.",
            tags = listOf("Squat pattern", "Bodyweight only", "Beginner friendly"),
        ),
    )
    put(
        "split squat",
        ExerciseWhy(
            purpose = "Lowers on one leg from a fixed long stance, loading the quads and glutes with the side hip steadying you.",
            pickIt = "Pick it over the Bulgarian split squat for an easier setup with the rear foot on the floor; there is less range, but it is steadier.",
            tags = listOf("One-leg strength", "Quad size", "Easy to balance"),
        ),
    )
    put(
        "sissy squat",
        ExerciseWhy(
            purpose = "Leans back with the knees pushed forward, isolating the quads under a long stretch with little hip work.",
            pickIt = "Pick it for bodyweight quad isolation at long length; the knees are loaded hard, so progress carefully or use leg extensions.",
            tags = listOf("Quad size", "Long-range loading", "Bodyweight only"),
        ),
    )
    put(
        "shrimp squat",
        ExerciseWhy(
            purpose = "Squats on one leg with the other foot held behind, loading the quads and glutes to a deep knee bend.",
            pickIt = "Pick it over the pistol squat for a more knee-bent, quad-biased single-leg squat; it needs balance and demands a lot of the knee.",
            tags = listOf("Quad strength", "Single-leg balance", "Bodyweight only"),
        ),
    )
    put(
        "dragon squat",
        ExerciseWhy(
            purpose = "Squats on one leg while the free leg sweeps behind and across, adding hip and ankle demands to the quads and glutes.",
            pickIt = "Pick it as an advanced single-leg move beyond the pistol squat; it needs mobility, balance and knee control, so build up slowly.",
            tags = listOf("One-leg strength", "Mobility", "Advanced move"),
        ),
    )
    put(
        "box pistol squat",
        ExerciseWhy(
            purpose = "Lowers on one leg to a box and stands again, loading the quads and glutes through a set depth.",
            pickIt = "Pick it before the full pistol squat; the box sets depth and gives a fall-back, though it still needs balance and a sturdy box.",
            tags = listOf("One-leg strength", "Adjustable depth", "Pistol prep"),
        ),
    )
    put(
        "elevated pistol squat",
        ExerciseWhy(
            purpose = "Lowers on one leg from a block, so the free leg hangs clear and the range goes deeper than a standard pistol.",
            pickIt = "Pick it for a deeper, harder pistol; the free leg hangs below the block, so the bottom needs more knee, hip and ankle mobility.",
            tags = listOf("Deep range", "One-leg strength", "Advanced move"),
        ),
    )
    put(
        "airborne squat",
        ExerciseWhy(
            purpose = "Squats on one leg with the other held behind you, loading the quads and glutes with a high balance demand.",
            pickIt = "Pick it as the last balance step before the pistol squat; the free leg held behind you removes the counterbalance, so go slowly.",
            tags = listOf("One-leg strength", "Balance", "Advanced move"),
        ),
    )
    put(
        "advanced shrimp squat",
        ExerciseWhy(
            purpose = "Squats on one leg with the rear foot held in both hands, adding range and balance demand for the quads.",
            pickIt = "Pick it beyond the shrimp squat for more difficulty; it needs deep quad flexibility and loads the knee a lot, so ease in.",
            tags = listOf("Quad strength", "Single-leg balance", "Advanced move"),
        ),
    )
    put(
        "deficit advanced shrimp squat",
        ExerciseWhy(
            purpose = "Squats on one leg from a block with the rear foot held in both hands, extending the range of the advanced shrimp squat.",
            pickIt = "Pick it as the hardest shrimp squat; the deficit adds depth and knee stress, so use it only once the advanced shrimp is solid.",
            tags = listOf("Quad strength", "Deep range", "Advanced move"),
        ),
    )
    put(
        "cobra squat",
        ExerciseWhy(
            purpose = "Squats on one leg with the free leg wrapped across and hands pinned behind, adding hip and balance demands to the quads.",
            pickIt = "Pick it as an advanced single-leg move after the dragon squat; mobility and control demands are high, so use low reps.",
            tags = listOf("One-leg strength", "Mobility", "Advanced move"),
        ),
    )
    put(
        "hamstring bridge",
        ExerciseWhy(
            purpose = "Lifts the hips with the heels on the floor, working the hamstrings in a short range with the glutes helping.",
            pickIt = "Pick it for hamstring work on the floor with no kit; it is easier than the nordic curl but limited in load.",
            tags = listOf("Hamstring strength", "Bodyweight only", "Beginner friendly"),
        ),
    )
    put(
        "nordic negative",
        ExerciseWhy(
            purpose = "Lowers the torso slowly from kneeling against anchored ankles, loading the hamstrings at long length.",
            pickIt = "Pick it before the full nordic curl to build the same lowering strength; you still need an anchor and a padded kneeling surface.",
            tags = listOf("Hamstring strength", "Lowering strength", "Nordic prep"),
        ),
    )
    put(
        "pause squat",
        ExerciseWhy(
            purpose = "Holds the bottom of a squat before standing, training the quads and glutes from a dead stop with no rebound.",
            pickIt = "Pick it over the back squat to build strength out of the hole and bracing; the pause means lighter loads and more fatigue.",
            tags = listOf("Bottom strength", "Bracing", "Control"),
        ),
    )
}

private fun MutableMap<String, ExerciseWhy>.legsCoreWhys2() {
    put(
        "heavy squat",
        ExerciseWhy(
            purpose = "Loads a barbell heavily to thigh-parallel depth or deeper, taxing the quads, glutes, adductors and trunk bracing.",
            pickIt = "Pick it for low-rep strength work; it needs a rack, safeties and a careful warm-up, and higher reps or machines suit volume.",
            tags = listOf("Max strength", "Quads and glutes", "Bracing"),
        ),
    )
    put(
        "double-bodyweight squat",
        ExerciseWhy(
            purpose = "Squats a bar loaded to twice your bodyweight, a recognised strength milestone for the quads, glutes and adductors.",
            pickIt = "Pick it as a strength target, not a starting point; it needs solid form, a rack with safeties and strong trunk bracing.",
            tags = listOf("Strength milestone", "Quad strength", "Bracing"),
        ),
    )
    put(
        "triple-bodyweight squat",
        ExerciseWhy(
            purpose = "Squats a bar loaded to three times your bodyweight, an elite strength milestone for the quads, glutes and trunk.",
            pickIt = "Pick it only as a long-term goal for very strong people; it needs refined form, a rack with safeties and planned recovery.",
            tags = listOf("Elite milestone", "Max strength", "Bracing"),
        ),
    )
    put(
        "volume deadlift",
        ExerciseWhy(
            purpose = "Pulls a bar for higher reps at moderate loading, building glute, hamstring and back work capacity and grip.",
            pickIt = "Pick it over the heavy deadlift for more total pulling with less peak load; form fades with fatigue, so stop short of failure.",
            tags = listOf("Work capacity", "Posterior chain", "Grip"),
        ),
    )
    put(
        "double-bodyweight deadlift",
        ExerciseWhy(
            purpose = "Pulls a bar loaded to twice your bodyweight, a strength milestone for the glutes, hamstrings, back and grip.",
            pickIt = "Pick it as a strength target once form is solid; heavy pulls take more recovery than squats, so use hard sets sparingly.",
            tags = listOf("Strength milestone", "Posterior chain", "Grip"),
        ),
    )
    put(
        "heavy deadlift",
        ExerciseWhy(
            purpose = "Pulls a heavy bar for low reps, loading the glutes, hamstrings, back and grip near their limit.",
            pickIt = "Pick it for low-rep strength; volume deadlifts suit more work, and heavy sets cost recovery, so keep the hard sets few.",
            tags = listOf("Max strength", "Posterior chain", "Grip"),
        ),
    )
    put(
        "triple-bodyweight deadlift",
        ExerciseWhy(
            purpose = "Pulls a bar loaded to three times your bodyweight, an elite strength milestone for the posterior chain and grip.",
            pickIt = "Pick it only as a long-term goal for very strong people; it needs flawless form, strong grip and planned recovery.",
            tags = listOf("Elite milestone", "Max strength", "Grip"),
        ),
    )
    put(
        "hanging leg raise",
        ExerciseWhy(
            purpose = "Lifts straight legs while hanging, working the abs and hip flexors on a long lever while the grip holds you.",
            pickIt = "Pick it over the hanging knee raise once that is easy; straight legs are a longer lever, and grip and hamstring flexibility can limit you.",
            tags = listOf("Ab strength", "Hip flexors", "Grip"),
        ),
    )
    put(
        "hanging knee raise",
        ExerciseWhy(
            purpose = "Curls the knees toward the chest while hanging, working the abs and hip flexors on a shorter lever than straight legs.",
            pickIt = "Pick it over the hanging leg raise while straight legs are too hard; it needs only a bar and leads on to leg raises and toes-to-bar.",
            tags = listOf("Ab strength", "Beginner friendly", "Grip"),
        ),
    )
    put(
        "hanging oblique knee raise",
        ExerciseWhy(
            purpose = "Lifts the knees to one side while hanging, adding the obliques to the abs and hip flexors.",
            pickIt = "Pick it over the plain knee raise to add rotation and side-trunk work; it is harder to keep still, so control the swing.",
            tags = listOf("Obliques", "Trunk control", "Grip"),
        ),
    )
    put(
        "ab wheel rollout",
        ExerciseWhy(
            purpose = "Trains the abs to stop your lower back arching as the wheel stretches you out, with the lats and obliques bracing too.",
            pickIt = "Pick it over the plank for a harder ab set with cheap kit; start from your knees and a short range, since long rolls demand strong bracing.",
            tags = listOf("Anti-extension", "Ab strength", "Cheap kit"),
        ),
    )
    put(
        "l-sit",
        ExerciseWhy(
            purpose = "Holds your legs straight out in front while supported on straight arms, loading the abs and hip flexors in compression.",
            pickIt = "Pick it for a static trunk and shoulder move with a clear path to harder holds; the floor works, bars help, and tuck l-sit scales it down.",
            tags = listOf("Compression", "Triceps", "Learning curve"),
        ),
    )
    put(
        "dragon flag",
        ExerciseWhy(
            purpose = "Lifts and lowers a straight body from the upper back, loading the abs hard on a long lever with the lats bracing.",
            pickIt = "Pick it as an advanced ab move beyond hanging raises and rollouts; it needs strong bracing and a bench, so use shorter levers first.",
            tags = listOf("Ab strength", "Full-body tension", "Advanced move"),
        ),
    )
    put(
        "weighted plank",
        ExerciseWhy(
            purpose = "Holds a plank with a plate on your back, making the abs and obliques brace harder once bodyweight is easy.",
            pickIt = "Pick it over the plain plank once that holds easily; short, loaded holds beat ever longer ones, but it needs a plate or a weight vest.",
            tags = listOf("Ab bracing", "Progressive load", "Trunk strength"),
        ),
    )
    put(
        "plank",
        ExerciseWhy(
            purpose = "Holds a straight body on forearms and toes, with the abs and obliques bracing the trunk against sagging.",
            pickIt = "Pick it as the simplest ab hold with no kit; it tops out as an endurance drill, so move to weighted planks or rollouts when easy.",
            tags = listOf("Trunk endurance", "Bodyweight only", "Beginner friendly"),
        ),
    )
    put(
        "side plank",
        ExerciseWhy(
            purpose = "Holds the body sideways on one forearm, loading the obliques and side hip to keep the trunk level.",
            pickIt = "Pick it over the plank to train the sides of the trunk; it scales with bent knees or added weight and needs no kit.",
            tags = listOf("Obliques", "Side trunk", "Bodyweight only"),
        ),
    )
    put(
        "lying leg raise",
        ExerciseWhy(
            purpose = "Lifts straight legs from the floor, working the abs and hip flexors while the lower back resists arching.",
            pickIt = "Pick it as a floor version of the hanging leg raise when you have no bar; keep the back flat or the hip flexors take over.",
            tags = listOf("Ab strength", "Hip flexors", "No bar needed"),
        ),
    )
    put(
        "dead bug",
        ExerciseWhy(
            purpose = "Lowers opposite arm and leg while the trunk stays still, training the abs to brace the spine against arching.",
            pickIt = "Pick it as a low-load, easy-to-learn ab drill or warm-up; it teaches bracing well but is easy for strong people, who can use rollouts.",
            tags = listOf("Core bracing", "Beginner friendly", "Bodyweight only"),
        ),
    )
    put(
        "woodchop",
        ExerciseWhy(
            purpose = "Rotates the trunk diagonally against cable or weight resistance, working the obliques with the abs and lower back.",
            pickIt = "Pick it to train rotation power, as in throwing and swinging sports; it needs a cable or weight; the pallof press suits stability better.",
            tags = listOf("Obliques", "Rotation", "Sport carryover"),
        ),
    )
    put(
        "pallof press",
        ExerciseWhy(
            purpose = "Presses a cable away from the chest while resisting being twisted, training the obliques and abs to hold anti-rotation.",
            pickIt = "Pick it over the woodchop to train stable bracing without moving the spine; it needs a cable or band, and is easy to scale.",
            tags = listOf("Anti-rotation", "Obliques", "Easy to learn"),
        ),
    )
    put(
        "kip-up",
        ExerciseWhy(
            purpose = "Springs from lying to standing with a hip snap, using the abs, hip flexors, quads and lower back in a quick floor move.",
            pickIt = "Pick it for an explosive floor-to-feet balance and coordination; it builds timing more than strength, so it complements ab work.",
            tags = listOf("Hip snap", "Coordination", "Bodyweight only"),
        ),
    )
    put(
        "handstand-to-bridge",
        ExerciseWhy(
            purpose = "Lowers from a handstand into a back bend, a move that taxes shoulder, spine and wrist range plus balance.",
            pickIt = "Pick it only once the handstand and bridge are solid, as an advanced move for gymnasts; use a soft mat and ideally a spotter.",
            tags = listOf("Back bend", "Handstand balance", "Shoulder range"),
        ),
    )
    put(
        "human flag",
        ExerciseWhy(
            purpose = "Holds the body sideways off a pole, with the lats, obliques and shoulders gripping and bracing the whole line.",
            pickIt = "Pick it as a flagship calisthenics move once pull-ups and side planks are strong; it is very hard, so progress via the tuck human flag.",
            tags = listOf("Obliques", "Lat strength", "Advanced move"),
        ),
    )
    put(
        "hollow hold",
        ExerciseWhy(
            purpose = "Holds a dished body shape on your back, with the abs doing the work and the hip flexors helping.",
            pickIt = "Pick it as a gymnastics-style ab hold that carries over to handstands and levers; it is harder than a plank, so bend the knees to scale.",
            tags = listOf("Ab tension", "Body shape", "Bodyweight only"),
        ),
    )
    put(
        "straddle l-sit",
        ExerciseWhy(
            purpose = "Holds your legs raised and spread wide on straight arms, loading the abs, hip flexors and triceps in compression.",
            pickIt = "Pick it after the l-sit to progress compression work; it needs hip flexibility and strong hip flexors, and leads toward the v-sit.",
            tags = listOf("Compression", "Hip flexors", "Hip flexibility"),
        ),
    )
    put(
        "v-sit",
        ExerciseWhy(
            purpose = "Holds your legs raised above horizontal on straight arms, taking the l-sit compression demand further.",
            pickIt = "Pick it after the straddle l-sit for the next compression step; it needs hamstring flexibility and strong hip flexors, and leads to manna.",
            tags = listOf("Compression", "Hip flexors", "Learning curve"),
        ),
    )
    put(
        "manna",
        ExerciseWhy(
            purpose = "Holds the body horizontal on straight arms with the legs high above the hands, testing compression and shoulders.",
            pickIt = "Pick it as a long-term goal beyond the v-sit; it needs excellent hip flexor strength, hamstring flexibility and shoulder strength.",
            tags = listOf("Compression", "Hip flexors", "Advanced move"),
        ),
    )
    put(
        "toes-to-bar",
        ExerciseWhy(
            purpose = "Lifts straight legs from a hang until the toes touch the bar, working the abs, hip flexors and grip through a big range.",
            pickIt = "Pick it over the hanging leg raise for a bigger range and a gym move; it needs hamstring flexibility and control of the swing.",
            tags = listOf("Ab strength", "Grip", "Full range"),
        ),
    )
    put(
        "deep squat hold",
        ExerciseWhy(
            purpose = "Sits at the bottom of a squat, holding hip, knee and ankle range with the quads, glutes and adductors working lightly.",
            pickIt = "Pick it to get comfortable at the bottom of the squat and build hip and ankle range; it builds little strength, so pair it with squats.",
            tags = listOf("Squat depth", "Hip mobility", "Ankle mobility"),
        ),
    )
    put(
        "pancake",
        ExerciseWhy(
            purpose = "Holds a seated wide-leg forward fold, stretching the adductors and hamstrings to build straddle flexibility.",
            pickIt = "Pick it to open the hips and inner thighs for straddle moves; it is a flexibility drill, not strength, so avoid forcing the range.",
            tags = listOf("Inner-thigh range", "Straddle range", "Bodyweight only"),
        ),
    )
    put(
        "bridge",
        ExerciseWhy(
            purpose = "Presses the hips up into a back bend from the floor, opening the shoulders, spine and hip flexors.",
            pickIt = "Pick it as an entry to back bends and gymnastics moves; it needs shoulder and spine mobility, so start with short holds.",
            tags = listOf("Spine mobility", "Shoulder range", "Bodyweight only"),
        ),
    )
    put(
        "front split",
        ExerciseWhy(
            purpose = "Slides into a lunge-based split, lengthening the hamstrings of the front leg and the hip flexors of the back leg.",
            pickIt = "Pick it to build split range for martial arts, dance or gymnastics; it takes months of regular work, so warm up and ease in.",
            tags = listOf("Hamstring range", "Hip flexor range", "Flexibility"),
        ),
    )
    put(
        "stand-to-stand bridge",
        ExerciseWhy(
            purpose = "Lowers from standing into a bridge and returns, a back bend move testing shoulder, spine and hip range plus control.",
            pickIt = "Pick it once a floor bridge is comfortable; it needs good shoulder and spine mobility and a soft surface, ideally with a spotter.",
            tags = listOf("Back bend", "Control", "Shoulder range"),
        ),
    )
    put(
        "german hang",
        ExerciseWhy(
            purpose = "Hangs from rings or a bar with the body rotated behind the arms, stretching the shoulders and chest under control.",
            pickIt = "Pick it as a shoulder-extension drill before moves like the back lever; go in slowly and only as far as your shoulders allow.",
            tags = listOf("Shoulder range", "Back lever prep", "Grip"),
        ),
    )
    put(
        "wrist prep",
        ExerciseWhy(
            purpose = "Loads the wrists and forearms through gentle weight shifts, preparing them for handstands, planches and floor moves.",
            pickIt = "Pick it as a warm-up before handstand or planche work; it is light and quick, but does not replace progressive strength work.",
            tags = listOf("Wrist conditioning", "Warm-up", "Bodyweight only"),
        ),
    )
    put(
        "negative pull-up",
        ExerciseWhy(
            purpose = "Lowers slowly from chin over the bar, loading the lats, upper back and biceps on the way down toward a first pull-up.",
            pickIt = "Pick it over the lat pulldown when you cannot yet pull up; it needs a bar and a box, and trains the lowering, not the pull.",
            tags = listOf("Lat strength", "Pull-up prep", "Lowering strength"),
        ),
    )
    put(
        "elevated pike push-up",
        ExerciseWhy(
            purpose = "Pushes your head toward the floor with feet raised and hips high, loading the front delts and triceps.",
            pickIt = "Pick it as a step toward wall handstand push-ups; it puts more of your weight on the shoulders than a plain pike, with no wall.",
            tags = listOf("Shoulder strength", "Triceps", "Handstand prep"),
        ),
    )
    put(
        "wall hspu negative",
        ExerciseWhy(
            purpose = "Lowers slowly from a wall handstand, loading the shoulders and triceps with nearly full bodyweight on the way down.",
            pickIt = "Pick it when elevated pike push-ups feel easy; it needs a wall and real shoulder strength, so lower to a mat under your head.",
            tags = listOf("Shoulder strength", "Triceps", "Lowering strength"),
        ),
    )
    put(
        "planche lean",
        ExerciseWhy(
            purpose = "Leans the shoulders forward past the hands in a push-up position, loading the front delts and straight-arm strength.",
            pickIt = "Pick it as the first planche step using only your feet; the lean angle sets difficulty, but the wrists and shoulders need care.",
            tags = listOf("Front delts", "Straight-arm work", "Planche prep"),
        ),
    )
    put(
        "elbow lever",
        ExerciseWhy(
            purpose = "Balances the body on bent arms with the elbows at your hips, building wrist, forearm and core balance.",
            pickIt = "Pick it as an easy entry to arm balances before the planche; it needs wrist tolerance, since the elbows must carry your weight.",
            tags = listOf("Arm balance", "Wrist strength", "Core tension"),
        ),
    )
    put(
        "rto support hold",
        ExerciseWhy(
            purpose = "Holds a straight-arm support on rings turned out, working the triceps and shoulders while the rings demand stability.",
            pickIt = "Pick it over a bar support hold to build ring control; the unstable rings make it harder, and turning out leads toward ring moves.",
            tags = listOf("Ring stability", "Triceps", "Shoulder strength"),
        ),
    )
    put(
        "banded iron cross",
        ExerciseWhy(
            purpose = "Holds an iron cross with a band taking part of your weight, loading the chest, lats and biceps in straight-arm work.",
            pickIt = "Pick it as the first iron cross step; the band makes it possible, but shoulder and elbow loads are high, so it suits experienced ring users.",
            tags = listOf("Straight-arm work", "Chest and lats", "Advanced move"),
        ),
    )
    put(
        "tuck human flag",
        ExerciseWhy(
            purpose = "Holds a sideways flag with the knees tucked, loading the obliques, lats and shoulders on a shorter lever.",
            pickIt = "Pick it before the full human flag; the lever is shorter, but it still needs strong side planks, pulling strength and a solid pole.",
            tags = listOf("Obliques", "Lat strength", "Flag progression"),
        ),
    )
    put(
        "supported pistol squat",
        ExerciseWhy(
            purpose = "Squats on one leg while holding a support, loading the quads and glutes through depth with less balance demand.",
            pickIt = "Pick it before the pistol squat or as an easier single-leg squat; the hand takes some load, so use less of it as you improve.",
            tags = listOf("One-leg strength", "Balance", "Pistol prep"),
        ),
    )
    put(
        "one-leg back lever",
        ExerciseWhy(
            purpose = "Holds a back lever with one leg extended, loading the lats, shoulders, biceps and trunk on a longer lever than the tuck.",
            pickIt = "Pick it after the advanced tuck back lever as a step toward the full lever; it needs strong shoulders and elbows, so ease in.",
            tags = listOf("Lat strength", "Straight-arm work", "Back lever prep"),
        ),
    )
    put(
        "tuck l-sit",
        ExerciseWhy(
            purpose = "Holds the knees tucked while supported on straight arms, working the abs, hip flexors and triceps on a short lever.",
            pickIt = "Pick it as the first l-sit step before straight legs; it builds compression and support without a long lever, with bars or boxes.",
            tags = listOf("Compression", "Triceps", "L-sit prep"),
        ),
    )
    put(
        "half split",
        ExerciseWhy(
            purpose = "Folds over the front leg from a kneeling lunge, stretching the hamstrings of the front leg.",
            pickIt = "Pick it as a gentle step toward the front split; it isolates the hamstring stretch with less demand than the full split.",
            tags = listOf("Hamstring range", "Front split prep", "Bodyweight only"),
        ),
    )
    put(
        "running",
        ExerciseWhy(
            purpose = "Builds endurance and leg conditioning through repeated strides, with the calves, quads, glutes and hamstrings working.",
            pickIt = "Pick it for cheap, flexible aerobic work; the impact is higher than cycling or rowing, so build weekly distance gradually.",
            tags = listOf("Aerobic fitness", "Leg endurance", "Bodyweight only"),
        ),
    )
    put(
        "trail running",
        ExerciseWhy(
            purpose = "Runs on uneven ground, adding balance and side-hip work to aerobic training for the legs.",
            pickIt = "Pick it over road running for varied terrain and softer surfaces; it is slower and needs sturdier footing and shoes.",
            tags = listOf("Aerobic fitness", "Balance", "Leg endurance"),
        ),
    )
    put(
        "cycling",
        ExerciseWhy(
            purpose = "Builds aerobic fitness and leg endurance with the quads, glutes, hamstrings and calves pushing the pedals.",
            pickIt = "Pick it for low-impact aerobic work that suits recovery days or long rides; it needs a bike and loads the legs less than running.",
            tags = listOf("Aerobic fitness", "Low impact", "Leg endurance"),
        ),
    )
}

private fun MutableMap<String, ExerciseWhy>.legsCoreWhys3() {
    put(
        "rowing",
        ExerciseWhy(
            purpose = "Builds aerobic fitness with a full-body stroke, the legs driving first and the back and arms finishing.",
            pickIt = "Pick it over cycling or running for low-impact work that uses the back and arms; form matters, so learn the drive sequence first.",
            tags = listOf("Aerobic fitness", "Full body", "Low impact"),
        ),
    )
    put(
        "hiking",
        ExerciseWhy(
            purpose = "Builds aerobic fitness and leg endurance over varied terrain, using the quads, glutes, calves and hamstrings on hills.",
            pickIt = "Pick it for long, low-intensity time on your feet outdoors; it is slow for fitness gains, but a loaded pack adds work.",
            tags = listOf("Leg endurance", "Low impact", "Outdoors"),
        ),
    )
    put(
        "walking",
        ExerciseWhy(
            purpose = "Builds easy aerobic fitness and daily movement, working the calves, glutes and quads at low intensity.",
            pickIt = "Pick it as the lowest-barrier activity or for recovery days; it does little for strength or high-end fitness, so add hills or pace.",
            tags = listOf("Low impact", "Easy recovery", "Bodyweight only"),
        ),
    )
    put(
        "skipping",
        ExerciseWhy(
            purpose = "Builds calf spring and aerobic fitness with repeated light jumps over a rope.",
            pickIt = "Pick it for compact, time-efficient cardio and footwork; it needs a rope and some coordination, and the landings load the calves.",
            tags = listOf("Aerobic fitness", "Coordination", "Calf endurance"),
        ),
    )
    put(
        "weighted skipping",
        ExerciseWhy(
            purpose = "Skips with a heavier rope, adding shoulder and forearm work to the usual calf and aerobic load.",
            pickIt = "Pick it over plain skipping for upper-body conditioning; the heavy rope turns slower, and the shoulders tire quickly.",
            tags = listOf("Shoulder endurance", "Aerobic fitness", "Coordination"),
        ),
    )
    put(
        "jump rope intervals",
        ExerciseWhy(
            purpose = "Alternates hard and easy bouts of rope jumping, building aerobic fitness, calf endurance and coordination.",
            pickIt = "Pick it over steady skipping for short, hard conditioning blocks; it needs a rope and a forgiving surface.",
            tags = listOf("Intervals", "Calf endurance", "Aerobic fitness"),
        ),
    )
    put(
        "stair climbing",
        ExerciseWhy(
            purpose = "Climbs steps continuously, loading the quads, glutes and calves against your bodyweight with every step.",
            pickIt = "Pick it over running for more glute and quad work with less pounding; it needs stairs or a machine, and tall steps tire the legs fast.",
            tags = listOf("Leg endurance", "Glutes and quads", "Aerobic fitness"),
        ),
    )
    put(
        "elliptical",
        ExerciseWhy(
            purpose = "Moves the legs on a gliding path, building aerobic fitness with little impact while the quads, glutes and calves work.",
            pickIt = "Pick it over running for a low-impact option; the fixed path is less like real gait, and you steer little of the movement.",
            tags = listOf("Low impact", "Aerobic fitness", "Leg endurance"),
        ),
    )
    put(
        "assault bike",
        ExerciseWhy(
            purpose = "Combines pedalling with pushing and pulling handles, giving a full-body aerobic effort on the legs, lats and triceps.",
            pickIt = "Pick it for hard intervals where resistance rises with your effort; it is very tiring, so keep the hard bouts short.",
            tags = listOf("Intervals", "Full body", "Low impact"),
        ),
    )
    put(
        "treadmill",
        ExerciseWhy(
            purpose = "Runs or walks on a belt, building aerobic fitness with the calves, quads, glutes and hamstrings.",
            pickIt = "Pick it over outdoor running for a set pace and incline in any weather; the surface is steady, but less varied than outdoors.",
            tags = listOf("Aerobic fitness", "Pace control", "Weather proof"),
        ),
    )
    put(
        "ski erg",
        ExerciseWhy(
            purpose = "Pulls two handles down in a skiing-style motion, building aerobic fitness with the lats, triceps, abs and glutes.",
            pickIt = "Pick it over rowing for more upper-body and trunk emphasis; the legs help but lead less, and the rhythm takes practice.",
            tags = listOf("Aerobic fitness", "Lats and triceps", "Low impact"),
        ),
    )
    put(
        "indoor cycling",
        ExerciseWhy(
            purpose = "Builds aerobic fitness and leg endurance on a stationary bike, with the quads, glutes, hamstrings and calves pedalling.",
            pickIt = "Pick it over road cycling for controlled rides in any weather or a class; set the bike up to fit you.",
            tags = listOf("Aerobic fitness", "Low impact", "Weather proof"),
        ),
    )
    put(
        "versaclimber",
        ExerciseWhy(
            purpose = "Climbs with the arms and legs driving together, building aerobic fitness with the quads, glutes, calves and lats.",
            pickIt = "Pick it over stair climbing for a full-body, low-impact climb; it needs a machine and the effort climbs fast.",
            tags = listOf("Full body", "Low impact", "Aerobic fitness"),
        ),
    )
    put(
        "swimming",
        ExerciseWhy(
            purpose = "Builds aerobic fitness with a full-body, low-impact stroke, working the lats, shoulders, triceps and hip flexors.",
            pickIt = "Pick it over running or cycling for low-impact, full-body work; form limits you at first, and it needs pool access.",
            tags = listOf("Low impact", "Aerobic fitness", "Full body"),
        ),
    )
    put(
        "water polo",
        ExerciseWhy(
            purpose = "Combines swimming, treading water and throwing in a team game that works the shoulders, lats, adductors and legs.",
            pickIt = "Pick it for team-based, high-intensity pool work; it is hard on the shoulders, and treading water is tough for weak swimmers.",
            tags = listOf("Aerobic fitness", "Shoulder endurance", "Team sport"),
        ),
    )
    put(
        "bouldering",
        ExerciseWhy(
            purpose = "Climbs short, hard routes without a rope, working the forearms, lats, biceps and abs in brief efforts with rests.",
            pickIt = "Pick it over rope climbing for short, powerful problems; the grip load is high, so rest well between attempts.",
            tags = listOf("Grip", "Pulling strength", "Problem solving"),
        ),
    )
    put(
        "sport climbing",
        ExerciseWhy(
            purpose = "Climbs longer routes on a rope, building forearm, lat and biceps endurance along with form.",
            pickIt = "Pick it over bouldering for sustained effort and endurance; it needs a partner and kit, and asks more commitment than top rope.",
            tags = listOf("Grip endurance", "Pulling endurance", "Form"),
        ),
    )
    put(
        "top rope",
        ExerciseWhy(
            purpose = "Climbs a route with a rope anchored above, working the forearms, lats, biceps and calves with a lower risk of a fall.",
            pickIt = "Pick it to build climbing volume or learn with a belayer; the rope above adds safety, but you need a partner and a gym or crag.",
            tags = listOf("Grip endurance", "Pulling endurance", "Beginner friendly"),
        ),
    )
    put(
        "football (soccer)",
        ExerciseWhy(
            purpose = "Combines sprints, turns, kicks and jumps over a match, building aerobic fitness and leg conditioning.",
            pickIt = "Pick it for team-based, mixed-intensity cardio; it needs a pitch and players, and the cuts and tackles are hard on the joints.",
            tags = listOf("Aerobic fitness", "Sprint ability", "Team sport"),
        ),
    )
    put(
        "basketball",
        ExerciseWhy(
            purpose = "Mixes sprints, jumps and quick changes of direction, working the quads, glutes, hamstrings and calves.",
            pickIt = "Pick it for intermittent, jumping cardio with others; it needs a court and players, and the landings load knees and ankles.",
            tags = listOf("Jumping", "Aerobic fitness", "Agility"),
        ),
    )
    put(
        "tennis",
        ExerciseWhy(
            purpose = "Builds court footwork, trunk rotation and arm swing, with bursts of lateral movement.",
            pickIt = "Pick it for social, stop-start cardio; intensity depends on the opponent and rally length, and one arm does most of the swinging.",
            tags = listOf("Footwork", "Aerobic fitness", "Agility"),
        ),
    )
    put(
        "badminton",
        ExerciseWhy(
            purpose = "Builds quick lateral footwork, lunges and overhead swings in short, fast rallies.",
            pickIt = "Pick it over tennis for faster rallies in a smaller space; it needs a court and partner, and the overhead hits tax the shoulder.",
            tags = listOf("Footwork", "Agility", "Aerobic fitness"),
        ),
    )
    put(
        "squash",
        ExerciseWhy(
            purpose = "Builds court speed, lunges and quick direction changes with a rotating trunk and swinging arm in a small, fast game.",
            pickIt = "Pick it for short, high-intensity bursts; it needs a court and partner, and the lunging and stopping are hard on the knees.",
            tags = listOf("Agility", "Aerobic fitness", "Short bursts"),
        ),
    )
    put(
        "cricket",
        ExerciseWhy(
            purpose = "Combines running, throwing, batting and bowling, using the legs, trunk and shoulders in short bursts.",
            pickIt = "Pick it for social, low-to-moderate intensity sport; much of a match is standing, so cardio is modest unless you bowl or field actively.",
            tags = listOf("Throwing", "Hand-eye control", "Team sport"),
        ),
    )
    put(
        "rugby",
        ExerciseWhy(
            purpose = "Combines sprinting, tackling, contact and carrying, loading the legs, back and traps in repeated heavy efforts.",
            pickIt = "Pick it for team-based, high-intensity work; it needs a team and the collision load is high, so build strength first.",
            tags = listOf("Power", "Aerobic fitness", "Team sport"),
        ),
    )
    put(
        "volleyball",
        ExerciseWhy(
            purpose = "Repeats jumps for spiking and blocking, with arm swings that work the legs, calves and shoulders.",
            pickIt = "Pick it for jumping and overhead practice in a social format; it needs a court and players, and the landings load ankles and knees.",
            tags = listOf("Jumping", "Shoulder fitness", "Team sport"),
        ),
    )
    put(
        "table tennis",
        ExerciseWhy(
            purpose = "Builds hand-eye coordination and quick footwork with light trunk rotation and forearm work.",
            pickIt = "Pick it for low-impact, coordination-based play and reaction speed; it adds little cardio or strength, so treat it as light activity.",
            tags = listOf("Hand-eye control", "Low impact", "Reaction speed"),
        ),
    )
    put(
        "golf",
        ExerciseWhy(
            purpose = "Builds rotational trunk control and walking volume, with the obliques, glutes and lower back swinging the club.",
            pickIt = "Pick it for a social, low-intensity outing; swings are one-sided and repeated, and walking the course adds most of the conditioning.",
            tags = listOf("Rotation", "Walking volume", "Low impact"),
        ),
    )
    put(
        "boxing",
        ExerciseWhy(
            purpose = "Builds punching endurance and footwork, with the shoulders, triceps, serratus and trunk working in rounds.",
            pickIt = "Pick it for intense conditioning; bag work needs no partner, but get form first before sparring.",
            tags = listOf("Aerobic fitness", "Punch endurance", "Coordination"),
        ),
    )
    put(
        "kickboxing",
        ExerciseWhy(
            purpose = "Combines punches and kicks, adding the hip flexors, glutes and quads to the punching muscles in rounds.",
            pickIt = "Pick it over boxing for more leg and hip work; kicks demand hip mobility, so warm up thoroughly.",
            tags = listOf("Aerobic fitness", "Hip mobility", "Coordination"),
        ),
    )
    put(
        "brazilian jiu-jitsu",
        ExerciseWhy(
            purpose = "Grapples on the ground, building grip, pulling and trunk strength through resisted positional rolling.",
            pickIt = "Pick it for coordination-based live practice; it needs a gym and partner, and heavy grip and contact use can tire the fingers and joints.",
            tags = listOf("Grip", "Trunk strength", "Coordination"),
        ),
    )
    put(
        "wrestling",
        ExerciseWhy(
            purpose = "Grapples standing and on the floor, building leg drive, grip, trunk and pulling strength against a resisting partner.",
            pickIt = "Pick it for short, intense bouts of full-body effort; it needs a club and coach, and the contact load is high.",
            tags = listOf("Full-body effort", "Grip", "Leg drive"),
        ),
    )
    put(
        "judo",
        ExerciseWhy(
            purpose = "Combines throws and ground grappling, building grip, pulling and hip strength against a moving partner.",
            pickIt = "Pick it over wrestling for a throw-and-grip emphasis; learn breakfalls first, and a club is needed.",
            tags = listOf("Grip", "Hip power", "Balance"),
        ),
    )
    put(
        "karate",
        ExerciseWhy(
            purpose = "Builds striking form, kicking range and balance through forms and sparring.",
            pickIt = "Pick it for form-based training with clear progression; intensity depends on the class, so add strength work for fitness.",
            tags = listOf("Form", "Balance", "Kicking range"),
        ),
    )
    put(
        "skateboarding",
        ExerciseWhy(
            purpose = "Builds balance and lower-leg control, with the quads, glutes, calves and shins holding a bent-knee stance.",
            pickIt = "Pick it for balance and playful coordination practice; falls are a real risk, so wear protection.",
            tags = listOf("Balance", "Coordination", "Leg endurance"),
        ),
    )
    put(
        "surfing",
        ExerciseWhy(
            purpose = "Builds paddling endurance in the lats, upper back and shoulders, plus pop-up power and balance in the legs.",
            pickIt = "Pick it for shoulder endurance and balance; it depends on surf and travel, so time spent paddling varies a lot.",
            tags = listOf("Paddling endurance", "Balance", "Shoulder endurance"),
        ),
    )
    put(
        "snowboarding",
        ExerciseWhy(
            purpose = "Holds a flexed-knee stance while carving, working the quads, glutes and calves with trunk balance.",
            pickIt = "Pick it for leg endurance and balance on the mountain; falls and cost are high, and leg strength beforehand helps.",
            tags = listOf("Leg endurance", "Balance", "Core control"),
        ),
    )
    put(
        "skiing",
        ExerciseWhy(
            purpose = "Holds a flexed-knee stance while turning, working the quads, glutes and thigh muscles for long stretches.",
            pickIt = "Pick it for sustained leg work on the mountain; long runs fatigue the quads, so leg strength helps and falls are a risk.",
            tags = listOf("Quad endurance", "Balance", "Leg endurance"),
        ),
    )
    put(
        "dancing",
        ExerciseWhy(
            purpose = "Builds aerobic fitness, coordination and rhythm, with the calves, quads, glutes and hip flexors working.",
            pickIt = "Pick it for enjoyable cardio and coordination; intensity varies by style, so choose a faster class for more effort.",
            tags = listOf("Aerobic fitness", "Coordination", "Rhythm"),
        ),
    )
    put(
        "martial arts class",
        ExerciseWhy(
            purpose = "Mixes striking, kicking and drills in a class, building conditioning, coordination and balance.",
            pickIt = "Pick it for coached, varied conditioning; the style sets the demands, so check it matches your goals.",
            tags = listOf("Conditioning", "Coordination", "Form"),
        ),
    )
    put(
        "yoga",
        ExerciseWhy(
            purpose = "Moves through held poses, building flexibility, balance and light strength in the trunk, legs and shoulders.",
            pickIt = "Pick it for mobility and low-intensity recovery; it builds little maximal strength, so keep lifting alongside.",
            tags = listOf("Flexibility", "Balance", "Body control"),
        ),
    )
    put(
        "pilates",
        ExerciseWhy(
            purpose = "Builds trunk control with slow, precise moves for the abs, obliques, glutes and hip flexors.",
            pickIt = "Pick it for controlled core work and body awareness; loading is light, so it complements heavy lifting.",
            tags = listOf("Core control", "Body awareness", "Low impact"),
        ),
    )
    put(
        "stretching",
        ExerciseWhy(
            purpose = "Holds lengthening positions for the hamstrings, hip flexors, calves and inner thighs to improve range of motion.",
            pickIt = "Pick it for flexibility goals or a cooldown; it adds little strength, so use a mobility flow for active range.",
            tags = listOf("Flexibility", "Range of motion", "Bodyweight only"),
        ),
    )
    put(
        "mobility flow",
        ExerciseWhy(
            purpose = "Moves through active joint ranges for the hips, spine and shoulders, mixing light strength with flexibility.",
            pickIt = "Pick it over static stretching for warm-ups or active range; it is gentle and does not replace strength training.",
            tags = listOf("Active range", "Warm-up", "Joint mobility"),
        ),
    )
}
