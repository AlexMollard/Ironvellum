package com.ironvellum.app.domain

internal fun MutableMap<String, ExerciseWhy>.pushPullWhys() {
    pushPullWhys1()
    pushPullWhys2()
    pushPullWhys3()
}

private fun MutableMap<String, ExerciseWhy>.pushPullWhys1() {
    put(
        "dip",
        ExerciseWhy(
            purpose = "Loads the lower chest and triceps through a deep stretch, with the front delts helping, using your own bodyweight.",
            pickIt = "Pick it over the push-up when bodyweight presses get easy; it needs bars and shoulder comfort at depth, and extra load takes a belt.",
            tags = listOf("Chest and triceps", "Loadable weight", "Deep stretch"),
        ),
    )
    put(
        "push-up",
        ExerciseWhy(
            purpose = "Trains the chest, front delts and triceps with the serratus and trunk holding a rigid plank, needing no kit.",
            pickIt = "Pick it when you have no bench or weights; it scales easily by angle or tempo, but heavy loading is awkward next to a bench press.",
            tags = listOf("Bodyweight only", "Chest and triceps", "Easy to scale"),
        ),
    )
    put(
        "archer push-up",
        ExerciseWhy(
            purpose = "Shifts most load onto one arm while the other stays straight, building chest and triceps toward one-arm pushing.",
            pickIt = "Pick it as the step between push-ups and the one-arm push-up; it demands wrist and shoulder control, and each rep is harder on one side.",
            tags = listOf("One-arm prep", "Chest and triceps", "Unilateral load"),
        ),
    )
    put(
        "diamond push-up",
        ExerciseWhy(
            purpose = "Moves the work toward the triceps, with the inner chest and front delts assisting, by bringing the hands close together.",
            pickIt = "Pick it for triceps emphasis without kit; it is harder on the wrists and elbows than a standard push-up, so keep sets moderate.",
            tags = listOf("Triceps", "Bodyweight only", "Lockout strength"),
        ),
    )
    put(
        "pike push-up",
        ExerciseWhy(
            purpose = "Presses your bodyweight overhead at an angle, loading the shoulders and triceps as a step toward handstand push-ups.",
            pickIt = "Pick it before wall handstand push-ups when you cannot yet press vertically; it is gentler than going upside down but capped by bodyweight.",
            tags = listOf("Shoulders", "Handstand prep", "Bodyweight only"),
        ),
    )
    put(
        "handstand push-up",
        ExerciseWhy(
            purpose = "Presses your full bodyweight overhead while inverted, loading the shoulders and triceps hard and demanding balance.",
            pickIt = "Pick it when pike push-ups are easy and you can hold a handstand; it is the strict bodyweight overhead press but needs balance and space.",
            tags = listOf("Overhead strength", "Balance", "Shoulders"),
        ),
    )
    put(
        "overhead press",
        ExerciseWhy(
            purpose = "Builds the shoulders and triceps pressing a barbell up, with the upper chest and traps helping and the trunk braced.",
            pickIt = "Pick it as your main standing press when you have a barbell; it loads more than dumbbells but is less forgiving of tight shoulders.",
            tags = listOf("Shoulders", "Triceps", "Full-body bracing"),
        ),
    )
    put(
        "bench press",
        ExerciseWhy(
            purpose = "Loads the chest, front delts and triceps with a barbell, the standard way to build pressing strength with heavy weights.",
            pickIt = "Pick it as your main horizontal press when you have a bench and rack; it loads more than dumbbells, but you need safeties or a spotter.",
            tags = listOf("Chest strength", "Heavy loading", "Triceps"),
        ),
    )
    put(
        "incline bench press",
        ExerciseWhy(
            purpose = "Shifts the barbell press toward the upper chest and front delts by setting the bench to a low or moderate incline.",
            pickIt = "Pick it to bring up the upper chest after flat benching; it usually takes lighter weights, and a steep angle turns it into a shoulder press.",
            tags = listOf("Upper chest", "Front delts", "Heavy loading"),
        ),
    )
    put(
        "scapular push-up",
        ExerciseWhy(
            purpose = "Trains the serratus anterior to move the shoulder blades around the ribcage, with the arms staying straight.",
            pickIt = "Pick it as a low-fatigue shoulder blade drill or warm-up; it will not build the chest or triceps, so it does not replace a push-up.",
            tags = listOf("Serratus", "Shoulder blades", "Low fatigue"),
        ),
    )
    put(
        "close-grip bench press",
        ExerciseWhy(
            purpose = "Shifts the bench press toward the triceps by narrowing the grip, while the chest and front delts keep helping.",
            pickIt = "Pick it over the flat bench when triceps lockout is your limit; keep the grip about shoulder width, since narrower is harder on the wrists.",
            tags = listOf("Triceps", "Lockout strength", "Heavy loading"),
        ),
    )
    put(
        "push press",
        ExerciseWhy(
            purpose = "Adds a leg drive to the barbell overhead press, so you can move heavier weights through the shoulders and triceps.",
            pickIt = "Pick it over the strict overhead press to overload the lockout or pass a sticking point; the leg drive means less shoulder work per rep.",
            tags = listOf("Heavy overhead", "Leg drive", "Power"),
        ),
    )
    put(
        "dumbbell bench press",
        ExerciseWhy(
            purpose = "Presses the chest, front delts and triceps with a longer stretch than a bar and each arm working on its own.",
            pickIt = "Pick it over the barbell bench for range of motion and to expose side-to-side gaps; setup is fiddlier and you cannot load it as heavily.",
            tags = listOf("Chest stretch", "Balanced sides", "Free path"),
        ),
    )
    put(
        "incline dumbbell press",
        ExerciseWhy(
            purpose = "Presses toward the upper chest and front delts with free dumbbells, letting each arm find its own path.",
            pickIt = "Pick it over the incline barbell press when your shoulders prefer free paths; it loads lighter, and a high incline becomes shoulder work.",
            tags = listOf("Upper chest", "Free path", "Balanced sides"),
        ),
    )
    put(
        "dumbbell shoulder press",
        ExerciseWhy(
            purpose = "Builds the shoulders and triceps pressing dumbbells overhead, with each arm moving through its own path.",
            pickIt = "Pick it over the barbell overhead press for a freer path or a seated setup; it loads lighter, and heavy sets are awkward to start.",
            tags = listOf("Shoulders", "Triceps", "Seated option"),
        ),
    )
    put(
        "arnold press",
        ExerciseWhy(
            purpose = "Presses dumbbells overhead while rotating the palms, working the front and side delts through a longer path.",
            pickIt = "Pick it as a variation on the dumbbell shoulder press with more delt range; it needs lighter weights and a smooth tempo, not max loads.",
            tags = listOf("Delt range", "Shoulders", "Variety"),
        ),
    )
    put(
        "lateral raise",
        ExerciseWhy(
            purpose = "Isolates the side delts by lifting the arms out to the sides, adding shoulder width that presses cover only partly.",
            pickIt = "Pick it to add direct side delt work after pressing; the weights stay light and it builds little else, so treat it as an accessory.",
            tags = listOf("Shoulder width", "Side delts", "Light loading"),
        ),
    )
    put(
        "front raise",
        ExerciseWhy(
            purpose = "Isolates the front delts by lifting the arms straight in front, a direct add-on to pressing.",
            pickIt = "Pick it only if your front delts lag; presses already work them hard, so it is optional next to the lateral raise.",
            tags = listOf("Front delts", "Isolation", "Light loading"),
        ),
    )
    put(
        "dumbbell fly",
        ExerciseWhy(
            purpose = "Stretches the chest by moving the arms in a wide arc, working the pecs with little triceps help.",
            pickIt = "Pick it after a press for a deep chest stretch; cable flys hold tension through the arc, and heavy loads stress the shoulders at the bottom.",
            tags = listOf("Chest stretch", "Isolation", "Light loading"),
        ),
    )
    put(
        "triceps kickback",
        ExerciseWhy(
            purpose = "Isolates the triceps through elbow extension, with the load peaking at lockout when the arm is straight behind you.",
            pickIt = "Pick it when you have dumbbells but no cable; it works hardest at the top and needs light weights, so a pushdown loads the triceps more.",
            tags = listOf("Triceps", "Lockout", "Light loading"),
        ),
    )
    put(
        "triceps pushdown",
        ExerciseWhy(
            purpose = "Isolates the triceps by pushing a cable handle down against steady tension, with little shoulder movement.",
            pickIt = "Pick it as a joint-friendly triceps accessory; the overhead extension stretches the long head more, but this loads simply near failure.",
            tags = listOf("Triceps", "Steady tension", "Easy to load"),
        ),
    )
    put(
        "overhead cable extension",
        ExerciseWhy(
            purpose = "Works the triceps with the arms raised overhead, which stretches the long head more than a pushdown.",
            pickIt = "Pick it over the pushdown to bias the long head through a longer range; it needs a cable and enough shoulder mobility to reach overhead.",
            tags = listOf("Triceps stretch", "Long head", "Cable tension"),
        ),
    )
    put(
        "cable fly",
        ExerciseWhy(
            purpose = "Works the chest by drawing the arms together against a cable, keeping tension through the whole arc.",
            pickIt = "Pick it over the dumbbell fly for constant tension; the pec deck is easier to set up, while the cable stance takes some balance.",
            tags = listOf("Chest", "Constant tension", "Isolation"),
        ),
    )
    put(
        "cable lateral raise",
        ExerciseWhy(
            purpose = "Isolates the side delts with a cable, keeping tension on them at the bottom where a dumbbell gives almost none.",
            pickIt = "Pick it over the dumbbell lateral raise for smoother tension; it needs a cable and one arm at a time, and the weights stay light.",
            tags = listOf("Side delts", "Constant tension", "Shoulder width"),
        ),
    )
    put(
        "pec deck",
        ExerciseWhy(
            purpose = "Isolates the chest by squeezing the arms together on a machine, with steady resistance and almost no balance demand.",
            pickIt = "Pick it for simple, stable chest work close to failure; it is less loadable than a press and gives the triceps little work.",
            tags = listOf("Chest isolation", "Stable", "Easy to learn"),
        ),
    )
    put(
        "machine chest press",
        ExerciseWhy(
            purpose = "Presses with the chest, front delts and triceps on a guided path, with no balancing needed.",
            pickIt = "Pick it for chest volume with little setup or no spotter; the fixed path is less adaptable than dumbbells or a barbell bench press.",
            tags = listOf("Chest", "Stable", "No spotter needed"),
        ),
    )
    put(
        "machine shoulder press",
        ExerciseWhy(
            purpose = "Presses with the shoulders and triceps on a guided path, so you can work hard without balancing the weight.",
            pickIt = "Pick it for overhead volume without a spotter or balance demand; the fixed path suits some shoulders less well than free dumbbells.",
            tags = listOf("Shoulders", "Stable", "No spotter needed"),
        ),
    )
    put(
        "smith machine bench press",
        ExerciseWhy(
            purpose = "Presses a bar on a fixed track, working the chest, front delts and triceps with less stabilising than a free barbell.",
            pickIt = "Pick it for solo benching near failure with built-in stops; the fixed path may not suit your shoulders, so adjust bench and foot position.",
            tags = listOf("Chest", "Built-in safety", "Fixed path"),
        ),
    )
    put(
        "smith machine overhead press",
        ExerciseWhy(
            purpose = "Presses overhead on a fixed bar path, working the shoulders and triceps with less stabilising than a free barbell.",
            pickIt = "Pick it for seated pressing near failure on your own; the fixed path suits some shoulders better than others and builds less trunk control.",
            tags = listOf("Shoulders", "Built-in safety", "Fixed path"),
        ),
    )
    put(
        "assisted dip",
        ExerciseWhy(
            purpose = "Works the dip pattern with a counterweight taking some of your bodyweight, so chest and triceps get a full-range rep.",
            pickIt = "Pick it while you cannot yet do strict dips; reduce the assistance over time and move to bar dips once you can do several reps.",
            tags = listOf("Dip prep", "Chest and triceps", "Scalable"),
        ),
    )
    put(
        "incline push-up",
        ExerciseWhy(
            purpose = "Trains the push-up pattern with the hands raised, which lightens the load on the chest, shoulders and triceps.",
            pickIt = "Pick it when floor push-ups are too hard; lower the surface over time until you reach the floor, then move to harder variations.",
            tags = listOf("Beginner-friendly", "Scalable", "Chest and triceps"),
        ),
    )
    put(
        "one-arm negative push-up",
        ExerciseWhy(
            purpose = "Builds toward the one-arm push-up by lowering slowly on one arm, loading the chest and triceps eccentrically.",
            pickIt = "Pick it once archer push-ups feel manageable; keep reps low and feet wide, since the slow lowering is demanding on the wrist and shoulder.",
            tags = listOf("Eccentric strength", "One-arm prep", "Control"),
        ),
    )
    put(
        "one-arm push-up",
        ExerciseWhy(
            purpose = "Loads the chest, front delts and triceps on one arm, with the trunk resisting a twist, a hard bodyweight press.",
            pickIt = "Pick it once archer and negative versions are solid; it takes patience, and the trunk demand makes it as much coordination as strength.",
            tags = listOf("Unilateral press", "Trunk control", "Bodyweight move"),
        ),
    )
    put(
        "bench dip",
        ExerciseWhy(
            purpose = "Presses your bodyweight with hands behind you on a bench, loading the triceps with lower chest and front delts helping.",
            pickIt = "Pick it when you have no dip bars; it scales with bent knees, but the hands-behind position is hard on the shoulders, so go shallow.",
            tags = listOf("Triceps", "No bars needed", "Scalable"),
        ),
    )
    put(
        "parallel bar support hold",
        ExerciseWhy(
            purpose = "Holds you up on straight arms between bars, training the triceps, chest and traps to stabilise the shoulders for dips.",
            pickIt = "Pick it as the first step toward bar dips when you cannot yet lower and press; it builds the position, not the full range of a dip.",
            tags = listOf("Dip foundation", "Shoulder stability", "Straight arms"),
        ),
    )
    put(
        "parallel bar dip",
        ExerciseWhy(
            purpose = "Loads the lower chest and triceps through a deep stretch; leaning forward favours chest, upright favours triceps.",
            pickIt = "Pick it over bench dips for a full-range bodyweight press with real loading potential; it needs bars and shoulders comfortable at depth.",
            tags = listOf("Chest and triceps", "Bodyweight press", "Deep stretch"),
        ),
    )
    put(
        "weighted dip",
        ExerciseWhy(
            purpose = "Adds load to the dip, overloading the lower chest and triceps beyond what bodyweight allows.",
            pickIt = "Pick it once bodyweight dips feel easy for ten or so reps; it is a heavy press without a barbell, but needs a belt and shoulder comfort.",
            tags = listOf("Progressive load", "Chest and triceps", "Heavy pressing"),
        ),
    )
    put(
        "wall handstand",
        ExerciseWhy(
            purpose = "Holds you upside down against a wall, loading the shoulders, traps and serratus, and teaching the handstand line.",
            pickIt = "Pick it as the first handstand step before going freestanding or doing wall push-ups; the wall removes balance but not the shoulder load.",
            tags = listOf("Overhead stability", "Shoulders", "Body line"),
        ),
    )
    put(
        "crow pose",
        ExerciseWhy(
            purpose = "Balances your knees on your arms, loading the triceps, shoulders and abs and teaching a forward weight shift.",
            pickIt = "Pick it as an early arm-balance step toward handstands and planche; it trains balance more than strength, and your wrists need warming up.",
            tags = listOf("Balance", "Wrist strength", "Core tension"),
        ),
    )
    put(
        "pike press",
        ExerciseWhy(
            purpose = "Presses upward from a pike with your hips high, loading the front delts and triceps with a vertical push.",
            pickIt = "Pick it as a step after pike push-ups toward handstand push-ups; the more vertical your torso, the harder it gets, so adjust your feet.",
            tags = listOf("Shoulders", "Vertical press", "Handstand prep"),
        ),
    )
    put(
        "wall hspu",
        ExerciseWhy(
            purpose = "Presses your bodyweight overhead while upside down against a wall, loading the front delts and triceps hard.",
            pickIt = "Pick it over freestanding versions for strict handstand push-ups, since the wall removes the balance; you need space and a reliable kick-up.",
            tags = listOf("Overhead strength", "Shoulders", "Wall support"),
        ),
    )
    put(
        "crow → handstand",
        ExerciseWhy(
            purpose = "Presses from a crow into a handstand, training shoulder strength, wrist control and the lean handstand presses need.",
            pickIt = "Pick it when crow is solid and you want a controlled way into a handstand; it is a balance drill, so use a soft surface and a wall nearby.",
            tags = listOf("Balance control", "Pressing strength", "Wrist control"),
        ),
    )
    put(
        "freestanding handstand",
        ExerciseWhy(
            purpose = "Holds you upside down without a wall, training balance and the shoulders, traps and serratus under bodyweight.",
            pickIt = "Pick it after wall handstands feel steady; it trains balance more than raw strength, and short fresh attempts beat long tired ones.",
            tags = listOf("Balance", "Shoulder stability", "Body awareness"),
        ),
    )
    put(
        "handstand walk",
        ExerciseWhy(
            purpose = "Moves you on your hands in small steps, training balance, wrist control and the shoulders in a dynamic way.",
            pickIt = "Pick it once a freestanding hold lasts several seconds; it adds balance work that a static hold does not, but keep the sets short and fresh.",
            tags = listOf("Dynamic balance", "Wrist control", "Shoulders"),
        ),
    )
    put(
        "90-degree push-up",
        ExerciseWhy(
            purpose = "Presses from a deep, forward-leaning handstand push-up, loading the front delts, triceps and upper chest at long range.",
            pickIt = "Pick it only once strict handstand push-ups are solid; it demands strength and balance, and puts high load on the wrists and shoulders.",
            tags = listOf("Shoulders", "Deep range", "Advanced pressing"),
        ),
    )
    put(
        "one-arm handstand",
        ExerciseWhy(
            purpose = "Balances your whole bodyweight on a single hand, an advanced test of shoulder strength, balance and trunk control.",
            pickIt = "Pick it only after a long freestanding handstand; progress is slow and mostly about balance, so keep practice short and the floor soft.",
            tags = listOf("Elite balance", "Shoulder control", "Trunk control"),
        ),
    )
    put(
        "straddle press to handstand",
        ExerciseWhy(
            purpose = "Lifts you into a handstand with straight arms and wide legs, loading the shoulders and abs through the forward lean.",
            pickIt = "Pick it to enter a handstand under control instead of kicking up; it needs hamstring flexibility and forward lean strength, and builds both.",
            tags = listOf("Handstand entry", "Shoulders", "Core control"),
        ),
    )
    put(
        "frog stand",
        ExerciseWhy(
            purpose = "Balances your knees on your elbows with bent arms, training the weight shift over the hands with triceps, delts and abs.",
            pickIt = "Pick it as a gentler arm balance than crow; it is a good first balance, but your wrists need warming up and the balance takes practice.",
            tags = listOf("Balance", "Wrist strength", "Entry-level move"),
        ),
    )
    put(
        "tuck planche",
        ExerciseWhy(
            purpose = "Holds your body horizontal on straight arms with knees tucked, loading the front delts, upper chest and biceps.",
            pickIt = "Pick it as the first planche step; it is hard on the wrists and elbows, so build hold time slowly and practise leaning first.",
            tags = listOf("Planche entry", "Shoulder lean", "Straight arms"),
        ),
    )
    put(
        "advanced tuck planche",
        ExerciseWhy(
            purpose = "Holds a flatter back with hips open and knees still tucked, raising the load on the front delts, upper chest and biceps.",
            pickIt = "Pick it after the tuck planche holds for several seconds; it keeps the lean and straight arms, with a longer lever than the tuck.",
            tags = listOf("Planche prep", "Front delts", "Straight arms"),
        ),
    )
}

private fun MutableMap<String, ExerciseWhy>.pushPullWhys2() {
    put(
        "one-leg planche",
        ExerciseWhy(
            purpose = "Holds a planche with one leg extended and the other tucked, loading the front delts, upper chest and biceps further.",
            pickIt = "Pick it between the advanced tuck and straddle planche as a half-step in lever length; keep holds short, as wrists and elbows take the load.",
            tags = listOf("Planche prep", "Lever length", "Straight arms"),
        ),
    )
    put(
        "straddle planche",
        ExerciseWhy(
            purpose = "Holds your body horizontal with your legs wide, a long lever that loads the front delts, upper chest and biceps hard.",
            pickIt = "Pick it when one-leg holds feel solid; the wide legs make it easier than the full planche, and it asks a lot of the wrists and elbows.",
            tags = listOf("Planche prep", "Front delts", "Straight arms"),
        ),
    )
    put(
        "full planche",
        ExerciseWhy(
            purpose = "Holds your body horizontal with the legs together, the full lever for the front delts, chest and biceps.",
            pickIt = "Pick it only after straddle holds are long and clean; it is a multi-year goal, so progress slowly and respect your wrists and elbows.",
            tags = listOf("Advanced move", "Front delts", "Straight arms"),
        ),
    )
    put(
        "planche push-up",
        ExerciseWhy(
            purpose = "Presses up from a planche lean, loading the front delts, triceps and upper chest with the shoulders far past the hands.",
            pickIt = "Pick it once you can lean forward and hold a planche; it builds pressing strength beyond holds, but it is hard on the wrists and elbows.",
            tags = listOf("Planche strength", "Front delts", "Pressing"),
        ),
    )
    put(
        "ring support hold",
        ExerciseWhy(
            purpose = "Holds you up on straight arms between rings, working triceps, chest and traps while the rings demand control.",
            pickIt = "Pick it before ring dips, once bar support is steady; the unstable rings are harder on the shoulders than the parallel-bar hold.",
            tags = listOf("Ring stability", "Shoulder control", "Dip foundation"),
        ),
    )
    put(
        "ring dip",
        ExerciseWhy(
            purpose = "Loads the lower chest and triceps with the rings adding instability, so stabilising muscles work harder than on bars.",
            pickIt = "Pick it over bar dips for a harder variant and ring move; it needs good shoulder control, and the wobble limits the weight you can add.",
            tags = listOf("Chest and triceps", "Ring control", "Shoulder stability"),
        ),
    )
    put(
        "iron cross",
        ExerciseWhy(
            purpose = "Holds your arms straight out on rings, loading the lats, chest, biceps and shoulder joint in an extreme position.",
            pickIt = "Pick it only after solid ring support and long straight-arm work; it is an elite move with high shoulder and elbow stress, so go slowly.",
            tags = listOf("Elite move", "Straight arms", "Lats and chest"),
        ),
    )
    put(
        "volume bench press",
        ExerciseWhy(
            purpose = "Builds chest, front delt and triceps size and work capacity with a rep target at a moderate load, short of failure.",
            pickIt = "Pick it over the heavy bench for practice and growth rather than max strength; it is easier to recover from, but skips top-end loads.",
            tags = listOf("Chest size", "Work capacity", "Form practice"),
        ),
    )
    put(
        "paused bench press",
        ExerciseWhy(
            purpose = "Builds control and strength from a dead stop on the chest, so the chest, delts and triceps move the bar without bounce.",
            pickIt = "Pick it over the standard bench to fix bounce and a weak start off the chest; you use lighter weights, but it teaches tight, honest reps.",
            tags = listOf("Control", "Start strength", "No bounce"),
        ),
    )
    put(
        "heavy bench press",
        ExerciseWhy(
            purpose = "Trains maximal pressing strength with a heavy load for low reps, loading the chest, front delts and triceps.",
            pickIt = "Pick it when strength, not volume, is the goal; it needs a spotter or safeties and good form, and is harder to recover from.",
            tags = listOf("Max strength", "Low reps", "Chest and triceps"),
        ),
    )
    put(
        "double-bodyweight bench press",
        ExerciseWhy(
            purpose = "Trains pressing strength at twice your bodyweight, with the chest, front delts and triceps working near their limit.",
            pickIt = "Pick it only after you have built up to a heavy bench; it is a long-term strength target and demands a spotter, safeties and good form.",
            tags = listOf("Max strength", "Long-term goal", "Chest and triceps"),
        ),
    )
    put(
        "volume overhead press",
        ExerciseWhy(
            purpose = "Builds shoulder and triceps size and work capacity with a rep target at a moderate load, a few reps short of failure.",
            pickIt = "Pick it over the heavy press for practice and growth rather than max strength; it is easier on recovery, but does not train top-end loads.",
            tags = listOf("Shoulder size", "Work capacity", "Form practice"),
        ),
    )
    put(
        "bodyweight overhead press",
        ExerciseWhy(
            purpose = "Presses a barbell equal to your own bodyweight overhead, loading the shoulders and triceps with the trunk braced.",
            pickIt = "Pick it as a milestone once you press heavy consistently; it needs good form and a rack, and suits only free overhead mobility.",
            tags = listOf("Shoulder strength", "Milestone", "Full-body bracing"),
        ),
    )
    put(
        "heavy overhead press",
        ExerciseWhy(
            purpose = "Trains maximal overhead strength with a heavy load for low reps, loading the shoulders and triceps bracing the trunk.",
            pickIt = "Pick it when strength, not volume, is the goal; it needs a rack with safety bars and good form, and is harder to recover from.",
            tags = listOf("Max strength", "Low reps", "Shoulders"),
        ),
    )
    put(
        "half-again overhead press",
        ExerciseWhy(
            purpose = "Presses a barbell one and a half times your bodyweight overhead, a very heavy load for the shoulders and triceps.",
            pickIt = "Pick it only after bodyweight pressing is easy; it is a long-term strength target, and demands safety bars, good form and patience.",
            tags = listOf("Max strength", "Long-term goal", "Shoulders"),
        ),
    )
    put(
        "pull-up",
        ExerciseWhy(
            purpose = "Builds the lats, upper back and biceps by lifting your whole bodyweight, the standard test of relative pulling strength.",
            pickIt = "Pick it over the lat pulldown once you can do a few strict reps; it trains grip and trunk too, but load rises only with added weight.",
            tags = listOf("Back width", "Grip", "Relative strength"),
        ),
    )
    put(
        "chin-up",
        ExerciseWhy(
            purpose = "Lifts your bodyweight with the palms facing you, loading the lats and upper back with more biceps help than a pull-up.",
            pickIt = "Pick it over the pull-up when that is too hard, since the underhand grip lets the biceps help more; the lats still do most of the work.",
            tags = listOf("Biceps", "Lats", "Easier start"),
        ),
    )
    put(
        "archer pull-up",
        ExerciseWhy(
            purpose = "Shifts most of your weight onto one arm while the other stays straight, loading the lats, upper back and biceps.",
            pickIt = "Pick it as the bridge between pull-ups and the one-arm pull-up; it needs strong strict pull-ups first and is harder on one side per rep.",
            tags = listOf("One-arm prep", "Lats", "Unilateral load"),
        ),
    )
    put(
        "inverted row",
        ExerciseWhy(
            purpose = "Pulls your chest to a bar under your body, working the upper back, rear delts and biceps as a horizontal pull.",
            pickIt = "Pick it to balance pressing without a pull-up bar, or to build toward pull-ups; lower the bar to make it harder, but loading is limited.",
            tags = listOf("Upper back", "Scalable", "Balances pressing"),
        ),
    )
    put(
        "tuck front lever row",
        ExerciseWhy(
            purpose = "Rows your chest to the bar from a tucked front lever, loading the lats, upper back and abs with straight-arm control.",
            pickIt = "Pick it once you can hold a tuck front lever and want to pull in it; it is a hard move that builds on the lever and the pull-up.",
            tags = listOf("Lever pulling", "Lats", "Core tension"),
        ),
    )
    put(
        "front lever row",
        ExerciseWhy(
            purpose = "Rows your chest to the bar from a full front lever, a demanding pull for the lats, upper back and abs.",
            pickIt = "Pick it only once you can hold a front lever and still pull from it; it is an advanced move that takes serious lats and trunk strength.",
            tags = listOf("Advanced pulling", "Lats", "Core tension"),
        ),
    )
    put(
        "door sheet row",
        ExerciseWhy(
            purpose = "Rows against a door with a sheet or towel, working the upper back and biceps with no gym kit.",
            pickIt = "Pick it when you have no bar, rings or weights; it is a basic home option that scales by foot position, but is less sturdy than a ring row.",
            tags = listOf("Bodyweight only", "Upper back", "Home option"),
        ),
    )
    put(
        "active bar hang",
        ExerciseWhy(
            purpose = "Hangs from a bar with the shoulders pulled down, training the grip, lats and shoulder control, a base for pulling.",
            pickIt = "Pick it before pull-ups to learn shoulder position; it differs from a dead hang by holding the blades down, which is more demanding.",
            tags = listOf("Grip", "Shoulder control", "Pull-up base"),
        ),
    )
    put(
        "wrist curl",
        ExerciseWhy(
            purpose = "Isolates the forearm flexors, adding grip and wrist strength that heavy pulling relies on.",
            pickIt = "Pick it as a small accessory when grip or forearm size limits your pulling; hangs train grip too, but this works the forearm more directly.",
            tags = listOf("Forearms", "Grip", "Isolation"),
        ),
    )
    put(
        "bicep curl",
        ExerciseWhy(
            purpose = "Isolates the biceps through elbow flexion, with the brachialis and forearms helping, for arm size beyond rows.",
            pickIt = "Pick it for direct biceps work after your pulls; it is light and simple to progress, but does not train the back like a chin-up.",
            tags = listOf("Biceps", "Arm size", "Isolation"),
        ),
    )
    put(
        "barbell row",
        ExerciseWhy(
            purpose = "Builds the lats, rhomboids and mid back with the lower back holding a hinge, loaded heavy with a barbell.",
            pickIt = "Pick it over the chest-supported row for heavy back loading and a stronger hinge; it needs a firm back position, and cheating creeps in.",
            tags = listOf("Heavy rowing", "Mid back", "Lower back"),
        ),
    )
    put(
        "dumbbell row",
        ExerciseWhy(
            purpose = "Pulls one dumbbell at a time, working the lats, upper back and biceps with a long stretch and easy setup.",
            pickIt = "Pick it over the barbell row for one-arm focus and range; it is easy on the lower back, but heavy loading is limited by available weights.",
            tags = listOf("Lats", "One arm at a time", "Easy setup"),
        ),
    )
    put(
        "lat pulldown",
        ExerciseWhy(
            purpose = "Pulls a bar to your upper chest, loading the lats, biceps and upper back, and teaching the vertical pull.",
            pickIt = "Pick it while you cannot yet do pull-ups, or to add back volume after them; the weight is easy to adjust, but it skips trunk control.",
            tags = listOf("Back width", "Scalable", "Beginner-friendly"),
        ),
    )
    put(
        "face pull",
        ExerciseWhy(
            purpose = "Pulls a cable toward your face, working the rear delts, upper back and rotator cuff, a counterweight to pressing.",
            pickIt = "Pick it as a light accessory for the rear delts and upper back after pressing; keep the weight low, since it will not build much strength.",
            tags = listOf("Rear delts", "Upper back", "Shoulder balance"),
        ),
    )
    put(
        "prone y raise",
        ExerciseWhy(
            purpose = "Lifts the arms overhead in a Y while lying face down, working the lower traps and rear delts with very light loads.",
            pickIt = "Pick it as a light warm-up or accessory for the lower traps; it does not build much on its own, so keep the weights light or use none.",
            tags = listOf("Lower traps", "Rear delts", "Light loading"),
        ),
    )
    put(
        "pendlay row",
        ExerciseWhy(
            purpose = "Pulls a barbell from the floor on every rep, working the lats, mid back and lower back with a dead stop and strict form.",
            pickIt = "Pick it over the barbell row for explosive, strict reps with no momentum; the floor reset costs you the tension between reps.",
            tags = listOf("Dead-stop rows", "Mid back", "Explosive pull"),
        ),
    )
    put(
        "t-bar row",
        ExerciseWhy(
            purpose = "Pulls a landmine bar with a close grip, loading the lats, mid back and lower back, often with an easier neutral grip.",
            pickIt = "Pick it over the barbell row when a neutral grip suits you better; it needs a landmine or T-bar setup, and the stance is a bit fiddly.",
            tags = listOf("Mid back", "Neutral grip", "Heavy rowing"),
        ),
    )
    put(
        "barbell shrug",
        ExerciseWhy(
            purpose = "Lifts the shoulders toward the ears under heavy load, working the upper traps and the forearms through grip.",
            pickIt = "Pick it over dumbbell shrugs for the heaviest loads; the bar sits in front of the thighs, and grip may limit you before the traps do.",
            tags = listOf("Upper traps", "Heavy loading", "Grip"),
        ),
    )
    put(
        "reverse fly",
        ExerciseWhy(
            purpose = "Opens the arms wide while hinged forward, isolating the rear delts and the mid back with light weights.",
            pickIt = "Pick it as a rear-delt accessory; it keeps the weights light and the tempo strict, and a cable or machine version holds tension more evenly.",
            tags = listOf("Rear delts", "Upper back", "Light loading"),
        ),
    )
    put(
        "hammer curl",
        ExerciseWhy(
            purpose = "Curls with the palms facing each other, loading the brachialis and forearms along with the biceps.",
            pickIt = "Pick it over the bicep curl for brachialis and forearm work and a comfortable wrist; the biceps get a bit less work than in a palms-up curl.",
            tags = listOf("Brachialis", "Forearms", "Neutral grip"),
        ),
    )
    put(
        "reverse curl",
        ExerciseWhy(
            purpose = "Curls with the palms down, loading the brachialis and forearm extensors more than a standard curl, and training grip.",
            pickIt = "Pick it as an accessory for the forearms and grip; the biceps work with lighter weights than a standard curl, so it will not replace one.",
            tags = listOf("Forearms", "Brachialis", "Grip"),
        ),
    )
    put(
        "dumbbell external rotation",
        ExerciseWhy(
            purpose = "Rotates the arm outward against light resistance, isolating the rotator cuff muscles that centre the shoulder joint.",
            pickIt = "Pick it as a light accessory for the rotator cuff; keep the weight very light, since it builds little else and heavy loads defeat the point.",
            tags = listOf("Rotator cuff", "Shoulder control", "Light loading"),
        ),
    )
    put(
        "preacher curl",
        ExerciseWhy(
            purpose = "Curls with the upper arms braced on a pad, isolating the biceps and brachialis and limiting swing.",
            pickIt = "Pick it over the standing curl for strict biceps work with a long stretch; it needs a preacher bench, and the bottom position needs care.",
            tags = listOf("Biceps", "Strict form", "Long stretch"),
        ),
    )
    put(
        "dumbbell shrug",
        ExerciseWhy(
            purpose = "Lifts the shoulders toward the ears with dumbbells, working the upper traps and the forearms through grip.",
            pickIt = "Pick it over the barbell shrug for a freer arm position at the sides; the loading is lighter, and grip often limits you first.",
            tags = listOf("Upper traps", "Grip", "Free arm path"),
        ),
    )
    put(
        "dumbbell pullover",
        ExerciseWhy(
            purpose = "Arcs a dumbbell overhead, stretching and loading the lats, with the chest, triceps and serratus helping.",
            pickIt = "Pick it for a lat stretch after rows and pulldowns; it takes a light, controlled load, and the stretched position is demanding on shoulders.",
            tags = listOf("Lat stretch", "Lats", "Serratus"),
        ),
    )
    put(
        "seated cable row",
        ExerciseWhy(
            purpose = "Pulls a cable handle to your torso, working the lats, mid back and biceps with a stable seat and steady tension.",
            pickIt = "Pick it over free-weight rows for steady tension and little to learn; it loads easily, though a rocking torso steals work from the back.",
            tags = listOf("Mid back", "Steady tension", "Easy to load"),
        ),
    )
    put(
        "cable curl",
        ExerciseWhy(
            purpose = "Curls against steady cable tension, working the biceps and brachialis with constant load through the whole range.",
            pickIt = "Pick it over the dumbbell curl for constant tension; it needs a cable stack, and the weights are light so it suits finishing sets.",
            tags = listOf("Biceps", "Constant tension", "Easy to load"),
        ),
    )
    put(
        "chest-supported row",
        ExerciseWhy(
            purpose = "Rows with your chest on a pad, working the lats, mid back and rear delts with no lower back or momentum.",
            pickIt = "Pick it over the barbell row to pull hard without lower-back fatigue; it loads a little lighter, and the pad limits how far you can stretch.",
            tags = listOf("Mid back", "No lower back", "Strict form"),
        ),
    )
    put(
        "machine row",
        ExerciseWhy(
            purpose = "Pulls on a guided path with your chest on a pad, working the lats, mid back and biceps with no balance demand.",
            pickIt = "Pick it for simple, stable back volume near failure; it is less adaptable than a cable row or free weights, but easy to learn.",
            tags = listOf("Mid back", "Stable", "Easy to learn"),
        ),
    )
    put(
        "smith machine row",
        ExerciseWhy(
            purpose = "Rows a bar on a fixed track, working the lats, rhomboids and upper back with the lower back bracing in a hinge.",
            pickIt = "Pick it for heavy rowing with a guided bar when you lack a free barbell; the fixed path suits some arms less, so adjust your stance.",
            tags = listOf("Mid back", "Guided path", "Heavy rowing"),
        ),
    )
    put(
        "assisted pull-up",
        ExerciseWhy(
            purpose = "Works the pull-up pattern with a counterweight or band taking some bodyweight, so the lats and biceps get full reps.",
            pickIt = "Pick it while you cannot yet do strict pull-ups; reduce the assistance over time and move to bodyweight reps once you can do several.",
            tags = listOf("Pull-up prep", "Lats", "Scalable"),
        ),
    )
    put(
        "dead hang",
        ExerciseWhy(
            purpose = "Hangs from a bar with straight arms, training grip endurance and stretching the shoulders and lats.",
            pickIt = "Pick it to build grip before pull-ups or as a simple finisher; the active hang trains shoulder control more, while this one is mostly grip.",
            tags = listOf("Grip endurance", "Shoulder stretch", "Easy to learn"),
        ),
    )
    put(
        "scapular pull",
        ExerciseWhy(
            purpose = "Moves only the shoulder blades from a hang, teaching the lats and lower traps to start a pull-up.",
            pickIt = "Pick it if you cannot yet do a pull-up or start with the arms; it is a small form drill, so keep it short and do not chase fatigue.",
            tags = listOf("Pull-up start", "Shoulder blades", "Form"),
        ),
    )
    put(
        "australian pull-up",
        ExerciseWhy(
            purpose = "Pulls your chest to a bar under your body, working the lats, rhomboids and biceps, an easier lead-in to pull-ups.",
            pickIt = "Pick it as a stepping stone toward pull-ups, or for extra volume after them; lower the bar for more load, but it stays lighter.",
            tags = listOf("Pull-up prep", "Upper back", "Scalable"),
        ),
    )
    put(
        "l-sit pull-up",
        ExerciseWhy(
            purpose = "Pulls up with your legs held out in front, adding abs and hip flexors to the lats and biceps of a pull-up.",
            pickIt = "Pick it once strict pull-ups are easy and you want trunk work in the same rep; the legs make the pull harder, so expect fewer reps.",
            tags = listOf("Core tension", "Lats", "Harder pull-up"),
        ),
    )
}

private fun MutableMap<String, ExerciseWhy>.pushPullWhys3() {
    put(
        "weighted pull-up",
        ExerciseWhy(
            purpose = "Adds load to the pull-up, overloading the lats, upper back and biceps beyond what bodyweight allows.",
            pickIt = "Pick it once bodyweight pull-ups feel easy at about ten reps; it builds heavy pulling strength, but needs a belt or a held weight.",
            tags = listOf("Progressive load", "Lats", "Pulling strength"),
        ),
    )
    put(
        "one-arm negative",
        ExerciseWhy(
            purpose = "Lowers slowly on one arm from the top of a pull-up, loading the lats, upper back and biceps toward a one-arm pull-up.",
            pickIt = "Pick it once weighted or archer pull-ups are strong; the slow lowering is hard on the elbow and shoulder, so keep reps low.",
            tags = listOf("One-arm prep", "Eccentric strength", "Lats"),
        ),
    )
    put(
        "one-arm pull-up",
        ExerciseWhy(
            purpose = "Pulls your full bodyweight with one arm, loading the lats, upper back and biceps, elite level, trunk resisting twist.",
            pickIt = "Pick it only after long work with weighted pulls, archers and negatives; it takes patience, and the elbow and shoulder bear high loads.",
            tags = listOf("Elite strength", "Unilateral pull", "Trunk control"),
        ),
    )
    put(
        "one-arm hang",
        ExerciseWhy(
            purpose = "Hangs from one hand, training grip with the forearms, lats and obliques resisting a twist and holding the shoulder.",
            pickIt = "Pick it after solid two-arm hangs to build grip and shoulder control for one-arm pulling; build time slowly, as the joints take more load.",
            tags = listOf("Grip", "Shoulder control", "One-arm prep"),
        ),
    )
    put(
        "front row hold",
        ExerciseWhy(
            purpose = "Holds the top of a row under a bar or rings, working the lats, rhomboids and rear delts with the body leaning back.",
            pickIt = "Pick it as a step toward front lever rows or to practise the squeeze at the top; it builds time under tension rather than reps.",
            tags = listOf("Isometric pulling", "Lats", "Body line"),
        ),
    )
    put(
        "tuck front lever",
        ExerciseWhy(
            purpose = "Holds your body horizontal in a hang with knees tucked, loading the lats, abs, rear delts and triceps on straight arms.",
            pickIt = "Pick it as the first front lever step; it builds straight-arm pulling strength that rows do not, and is hard on the elbows, so go slowly.",
            tags = listOf("Lever entry", "Lats", "Core tension"),
        ),
    )
    put(
        "advanced tuck front lever",
        ExerciseWhy(
            purpose = "Holds a flatter back with the hips open and knees still tucked, a harder lever for the lats, abs and shoulders.",
            pickIt = "Pick it after the tuck front lever holds for several seconds; it needs a flat back and trunk strength, with a longer lever than the tuck.",
            tags = listOf("Lever prep", "Lats", "Core tension"),
        ),
    )
    put(
        "one-leg front lever",
        ExerciseWhy(
            purpose = "Holds a front lever with one leg extended and the other tucked, a longer lever for the lats, abs and shoulders.",
            pickIt = "Pick it between the advanced tuck and straddle front lever when you need a half-step; it needs strong straight arms and a flat body line.",
            tags = listOf("Lever progression", "Lever length", "Core tension"),
        ),
    )
    put(
        "straddle front lever",
        ExerciseWhy(
            purpose = "Holds your body horizontal with wide legs, loading the lats, abs and shoulders with a lever easier than the full one.",
            pickIt = "Pick it when one-leg holds are solid and the full lever is out of reach; wider legs make it easier, but the elbows still take real stress.",
            tags = listOf("Lever prep", "Lats", "Core tension"),
        ),
    )
    put(
        "front lever",
        ExerciseWhy(
            purpose = "Holds your body straight and horizontal under a bar, the full lever loading the lats, abs and upper back, arms straight.",
            pickIt = "Pick it only after straddle holds are solid; it is a long-term move that builds straight-arm pulling strength, so protect the elbows.",
            tags = listOf("Advanced move", "Lats", "Core tension"),
        ),
    )
    put(
        "skin the cat",
        ExerciseWhy(
            purpose = "Rolls backward through a hang to a deep shoulder stretch and back, working the lats and shoulder mobility.",
            pickIt = "Pick it for shoulder mobility and as a step toward back levers and muscle-ups; go slowly, since the end range is demanding.",
            tags = listOf("Shoulder mobility", "Control", "Back lever prep"),
        ),
    )
    put(
        "tuck back lever",
        ExerciseWhy(
            purpose = "Holds your body horizontal, face down and tucked, loading the chest, front delts and biceps in a shoulder stretch.",
            pickIt = "Pick it as the first back lever step; it needs good shoulder extension and care at the end range, so build hold time slowly.",
            tags = listOf("Lever entry", "Shoulder stretch", "Biceps"),
        ),
    )
    put(
        "advanced tuck back lever",
        ExerciseWhy(
            purpose = "Holds a flatter back with the hips open and knees still tucked, a harder lever for the chest, front delts and biceps.",
            pickIt = "Pick it after the tuck back lever is steady; it needs shoulder mobility and a flat body line, with a longer lever than the tuck.",
            tags = listOf("Lever prep", "Chest and delts", "Biceps"),
        ),
    )
    put(
        "straddle back lever",
        ExerciseWhy(
            purpose = "Holds your body horizontal facing down with the legs wide, a long lever that loads the chest, front delts and biceps.",
            pickIt = "Pick it when advanced tuck holds are long and clean; wide legs make it easier than the full lever, but shoulders and elbows take the stress.",
            tags = listOf("Lever prep", "Chest and delts", "Biceps"),
        ),
    )
    put(
        "back lever",
        ExerciseWhy(
            purpose = "Holds your body straight and horizontal facing down, loading the chest, front delts and biceps in a shoulder stretch.",
            pickIt = "Pick it only after straddle holds are solid; it is a long-term move, and the shoulders and elbows need slow, careful progress.",
            tags = listOf("Advanced move", "Shoulder stretch", "Biceps"),
        ),
    )
    put(
        "ring row",
        ExerciseWhy(
            purpose = "Pulls your chest to rings under your body, working the upper back, lats and biceps, plus stability work.",
            pickIt = "Pick it over the inverted row for a freer wrist path and more stabiliser work; it needs rings, and the wobble limits how much you can load.",
            tags = listOf("Upper back", "Free wrist path", "Scalable"),
        ),
    )
    put(
        "ring muscle-up",
        ExerciseWhy(
            purpose = "Pulls and presses from a hang to support above the rings, loading the lats, biceps, triceps and shoulders in one move.",
            pickIt = "Pick it only once strict pull-ups and ring dips are strong; the false grip and turnover are the hard part, and it is harder than on a bar.",
            tags = listOf("Pull and press", "False grip", "Advanced move"),
        ),
    )
    put(
        "one-arm front lever",
        ExerciseWhy(
            purpose = "Holds a front lever on one arm, a very high demand on the lats, abs and shoulder with the trunk resisting a twist.",
            pickIt = "Pick it only after a long full front lever; it is an elite move with heavy shoulder and elbow stress, so build it very slowly.",
            tags = listOf("Elite move", "Lats", "Trunk control"),
        ),
    )
    put(
        "muscle-up",
        ExerciseWhy(
            purpose = "Pulls and presses from a hang to support above the bar, working the lats, biceps, triceps and shoulders with a swing.",
            pickIt = "Pick it once strong pull-ups and dips are in place and you want to link them; the swing makes it easier to learn than the strict version.",
            tags = listOf("Pull and press", "Power", "Coordination"),
        ),
    )
    put(
        "strict muscle-up",
        ExerciseWhy(
            purpose = "Pulls and presses from a dead hang to support above the bar, no swing, loading the lats, biceps, triceps and shoulders.",
            pickIt = "Pick it over the swinging muscle-up for real strength; it needs strong weighted pull-ups and dips, and a smooth turnover.",
            tags = listOf("Strict strength", "Pull and press", "Coordination"),
        ),
    )
    put(
        "inverted muscle-up",
        ExerciseWhy(
            purpose = "Pulls from a hang to a handstand over the bar, loading the lats, biceps and front delts as the pull turns into a press.",
            pickIt = "Pick it only with a solid freestanding handstand and strict muscle-ups; it is an elite move, so use a soft surface and a spotter.",
            tags = listOf("Elite move", "Handstand strength", "Pull and press"),
        ),
    )
}
