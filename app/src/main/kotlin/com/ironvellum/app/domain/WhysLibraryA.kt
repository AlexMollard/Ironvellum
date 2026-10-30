package com.ironvellum.app.domain

internal fun MutableMap<String, ExerciseWhy>.libraryWhysA() {
    libraryWhysA1()
    libraryWhysA2()
    libraryWhysA3()
}

private fun MutableMap<String, ExerciseWhy>.libraryWhysA1() {
    put(
        "decline bench press",
        ExerciseWhy(
            purpose = "Loads the lower chest, front delts and triceps with a barbell, and the decline angle shortens the range of motion.",
            pickIt = "Pick it over the flat bench for lower-chest bias and a short stroke; it needs a decline bench, and safeties or a spotter.",
            tags = listOf("Lower chest", "Heavy loading", "Short range"),
        ),
    )
    put(
        "weighted push-up",
        ExerciseWhy(
            purpose = "Builds the chest, front delts, triceps and serratus with bodyweight pressing made harder by added load.",
            pickIt = "Pick it over the plain push-up once reps get easy; no bench needed, but a plate on your back needs a partner to set and stays awkward.",
            tags = listOf("Chest and triceps", "Progressive load", "No bench needed"),
        ),
    )
    put(
        "reverse pec deck",
        ExerciseWhy(
            purpose = "Isolates the rear delts, with the rhomboids and mid traps helping, to balance all the pressing in a week.",
            pickIt = "Pick it over bent-over raises for a fixed path and steady tension with no hinge; it is tied to the machine and trains little else.",
            tags = listOf("Rear delts", "Upper back", "Stable path"),
        ),
    )
    put(
        "barbell upright row",
        ExerciseWhy(
            purpose = "Works the side delts and upper traps together by pulling the bar up toward the chest, with biceps assisting.",
            pickIt = "Pick it to load side delts and traps with heavy weight; stop near chest height, and swap to lateral raises if the shoulders object.",
            tags = listOf("Side delts", "Upper traps", "Heavy loading"),
        ),
    )
    put(
        "dumbbell bent-over row",
        ExerciseWhy(
            purpose = "Builds the mid back and rhomboids, with lats, rear delts and biceps helping, while the hinge holds under load.",
            pickIt = "Pick it over the barbell row for freer arm paths and easy loading; the hinge taxes the lower back, unlike chest-supported rows.",
            tags = listOf("Mid back", "Rear delts", "Hinge strength"),
        ),
    )
    put(
        "close-grip lat pulldown",
        ExerciseWhy(
            purpose = "Pulls a V-bar down to train the lats, with the biceps and mid back helping and a deep stretch overhead.",
            pickIt = "Pick it over the wide-grip pulldown if a wide bar bothers your shoulders or elbows; you can often go heavier, with more biceps share.",
            tags = listOf("Lats", "Neutral grip", "Heavier loads"),
        ),
    )
    put(
        "straight-arm pulldown",
        ExerciseWhy(
            purpose = "Isolates the lats by sweeping straight arms from overhead to the hips, with little help from the biceps.",
            pickIt = "Pick it as a finisher or lat warm-up after rows and pulldowns; the load stays light, so it will not replace a heavy vertical pull.",
            tags = listOf("Lat isolation", "Lat feel", "Low fatigue"),
        ),
    )
    put(
        "skull crusher",
        ExerciseWhy(
            purpose = "Isolates the triceps by extending the elbows lying down, loading the long head in a stretch.",
            pickIt = "Pick it for heavy triceps work with a bar and bench; it loads more than pushdowns but is hard on the elbows, so try an EZ bar or dumbbells.",
            tags = listOf("Triceps mass", "Long head", "Heavy isolation"),
        ),
    )
    put(
        "cable crunch",
        ExerciseWhy(
            purpose = "Trains the abs by curling the spine against cable resistance, so you can add load over time like any lift.",
            pickIt = "Pick it over bodyweight crunches when you need progressive load; keep the hips still and curl the ribs down so the abs do the work.",
            tags = listOf("Abs", "Progressive load", "Spinal flexion"),
        ),
    )
    put(
        "incline cable fly",
        ExerciseWhy(
            purpose = "Targets the upper chest by sweeping the arms up and together, with the cable keeping tension through the stroke.",
            pickIt = "Pick it over incline dumbbell flies for tension at the top; it carries less weight than presses, so use it as a finisher.",
            tags = listOf("Upper chest", "Constant tension", "Chest finisher"),
        ),
    )
    put(
        "decline dumbbell press",
        ExerciseWhy(
            purpose = "Presses the lower chest, front delts and triceps with a separate path for each arm on a decline.",
            pickIt = "Pick it over the decline barbell press for freer shoulders and a deeper stretch; getting heavy dumbbells into place is awkward without help.",
            tags = listOf("Lower chest", "Free arm path", "Even sides"),
        ),
    )
    put(
        "landmine press",
        ExerciseWhy(
            purpose = "Presses a barbell along an arc, working the front delts, upper chest and triceps with the trunk bracing.",
            pickIt = "Pick it over overhead presses if a straight bar path is uncomfortable; it needs a landmine anchor and tops out lighter.",
            tags = listOf("Angled press", "Chest and delts", "Trunk bracing"),
        ),
    )
    put(
        "chest dip",
        ExerciseWhy(
            purpose = "Works the lower chest and triceps by pressing your own bodyweight up between bars with a forward lean.",
            pickIt = "Pick it over the bench for heavy bodyweight pressing you can belt-load; deep dips stress the front shoulder, so stop where it is solid.",
            tags = listOf("Lower chest", "Triceps", "Bodyweight loading"),
        ),
    )
    put(
        "standing dumbbell press",
        ExerciseWhy(
            purpose = "Presses overhead to build the front and side delts and triceps, while standing makes the trunk hold you steady.",
            pickIt = "Pick it over the barbell overhead press for independent arms and easy starting weights; loads run lighter and bells must be lifted up first.",
            tags = listOf("Shoulders", "Triceps", "Standing stability"),
        ),
    )
    put(
        "machine lateral raise",
        ExerciseWhy(
            purpose = "Isolates the side delts with a guided path and even resistance, which builds shoulder width.",
            pickIt = "Pick it over dumbbell raises for tension at the bottom and no swing; it is tied to the machine and fits you only as well as it adjusts.",
            tags = listOf("Side delts", "Constant tension", "Easy to learn"),
        ),
    )
    put(
        "cable front raise",
        ExerciseWhy(
            purpose = "Isolates the front delts by lifting the arm forward against a cable that pulls through the whole range.",
            pickIt = "Pick it over dumbbell front raises for steady tension; pressing already loads the front delts a lot, so most people need very little of it.",
            tags = listOf("Front delts", "Constant tension", "Shoulder isolation"),
        ),
    )
    put(
        "plate front raise",
        ExerciseWhy(
            purpose = "Isolates the front delts by lifting a weight plate to eye level, a simple way to load them with plates alone.",
            pickIt = "Pick it when plates are all you have; the grip is awkward and the load tops out quickly, and pressing already trains the front delts.",
            tags = listOf("Front delts", "Minimal kit", "Easy to load"),
        ),
    )
    put(
        "bent-over rear delt raise",
        ExerciseWhy(
            purpose = "Builds the rear delts, with the rhomboids and mid traps helping, by lifting light dumbbells out wide from a hinge.",
            pickIt = "Pick it over the reverse pec deck when you only have dumbbells; keep the weight light, since momentum takes over fast.",
            tags = listOf("Rear delts", "Upper back", "Dumbbells only"),
        ),
    )
    put(
        "cable rear delt fly",
        ExerciseWhy(
            purpose = "Works the rear delts, with the rhomboids and mid traps, by pulling crossed cables out wide against steady tension.",
            pickIt = "Pick it over bent-over raises for tension at every point of the stroke; it needs a cable tower and drifts into a row if the elbows go back.",
            tags = listOf("Rear delts", "Constant tension", "Upper back"),
        ),
    )
    put(
        "meadows row",
        ExerciseWhy(
            purpose = "Rows a landmine bar one arm at a time, building the mid back, with lats, biceps and rear delts assisting.",
            pickIt = "Pick it over the dumbbell row for a heavier, stretched pull on a thick bar end; it needs a landmine or corner, and some hinge control.",
            tags = listOf("Mid back", "Grip", "Heavy rows"),
        ),
    )
    put(
        "single-arm cable row",
        ExerciseWhy(
            purpose = "Rows one side at a time against constant cable tension, training the mid back and lats with room to rotate slightly.",
            pickIt = "Pick it over the two-arm cable row to even out sides and get a longer stroke; it takes longer, and a twisting torso adds cheat reps.",
            tags = listOf("Mid back", "Even sides", "Constant tension"),
        ),
    )
    put(
        "wide-grip lat pulldown",
        ExerciseWhy(
            purpose = "Pulls a wide bar to the upper chest to train the lats, with the upper back and biceps helping from a full stretch.",
            pickIt = "Pick it as the standard way to scale lat volume or build toward pull-ups; an extremely wide grip shortens the stroke and gains little.",
            tags = listOf("Lats", "Scalable load", "Pull-up builder"),
        ),
    )
    put(
        "neutral-grip pull-up",
        ExerciseWhy(
            purpose = "Builds the lats, upper back and biceps by hauling your bodyweight up with palms facing, which also works the brachialis.",
            pickIt = "Pick it over the overhand pull-up if a wide grip bothers wrists or shoulders; many manage more reps, but it needs parallel handles or rings.",
            tags = listOf("Lats", "Neutral grip", "Biceps"),
        ),
    )
    put(
        "assisted chin-up",
        ExerciseWhy(
            purpose = "Trains the chin-up pattern for the lats, biceps and upper back with part of your bodyweight taken off.",
            pickIt = "Pick it to build reps toward unassisted chin-ups; it loses its point once you manage a few strict reps.",
            tags = listOf("Lats", "Biceps", "Scalable help"),
        ),
    )
    put(
        "weighted chin-up",
        ExerciseWhy(
            purpose = "Adds load to the chin-up for heavy lat and biceps work, a clear test of upper-body pulling strength.",
            pickIt = "Pick it over the plain chin-up once bodyweight sets pass about ten reps; it needs a belt or dumbbell, and heavy volume taxes the elbows.",
            tags = listOf("Lats", "Biceps", "Heavy loading"),
        ),
    )
    put(
        "belt squat",
        ExerciseWhy(
            purpose = "Squats with load hanging from the hips, training the quads, glutes and adductors with no weight on the spine.",
            pickIt = "Pick it when your back or shoulders limit barbell squats; leg work stays heavy, but you need the machine and the carry-over is smaller.",
            tags = listOf("Quads and glutes", "No spinal load", "Leg volume"),
        ),
    )
    put(
        "trap bar deadlift",
        ExerciseWhy(
            purpose = "Lifts from the floor with hands at your sides, working the glutes, hamstrings and quads as the back and grip hold.",
            pickIt = "Pick it over the barbell deadlift for an easier pull to learn with a more upright torso; it needs a trap bar and stretches hamstrings less.",
            tags = listOf("Posterior chain", "Grip", "Easy to learn"),
        ),
    )
    put(
        "reverse lunge",
        ExerciseWhy(
            purpose = "Steps back into a lunge, building the quads and glutes one leg at a time, with the adductors helping.",
            pickIt = "Pick it over the forward lunge for easier balance and control; loads stay below what you squat, since each leg works alone.",
            tags = listOf("Quads and glutes", "Single leg", "Easy balance"),
        ),
    )
    put(
        "cable kickback",
        ExerciseWhy(
            purpose = "Isolates the glutes by driving the leg back against a low cable while standing.",
            pickIt = "Pick it as a glute finisher after hip thrusts or squats; it is light, single-leg work, so it will not replace heavy lower-body lifts.",
            tags = listOf("Glutes", "Isolation", "Single leg"),
        ),
    )
    put(
        "barbell glute bridge",
        ExerciseWhy(
            purpose = "Drives the hips up from the floor under a barbell, loading the glutes, with the hamstrings helping.",
            pickIt = "Pick it over the hip thrust when you lack a bench; the floor shortens the range, so it loads the glutes a little less than a thrust.",
            tags = listOf("Glutes", "Heavy loading", "No bench needed"),
        ),
    )
    put(
        "dumbbell glute bridge",
        ExerciseWhy(
            purpose = "Drives the hips up from the floor with a dumbbell on your lap, loading the glutes, with the hamstrings helping.",
            pickIt = "Pick it over the barbell bridge when you have minimal kit; the dumbbell caps the load, and the floor shortens the range versus a thrust.",
            tags = listOf("Glutes", "Home friendly", "Hip extension"),
        ),
    )
    put(
        "dumbbell hip thrust",
        ExerciseWhy(
            purpose = "Thrusts the hips up from a bench, loading the glutes hardest at the top, with the hamstrings helping.",
            pickIt = "Pick it over the floor bridge for more range; a dumbbell caps the load before a barbell does, and rolling it into place is awkward.",
            tags = listOf("Glutes", "Full hip extension", "Dumbbell only"),
        ),
    )
    put(
        "smith machine hip thrust",
        ExerciseWhy(
            purpose = "Thrusts the hips up under a guided bar, loading the glutes, with the hamstrings helping.",
            pickIt = "Pick it over the free barbell thrust for a guided bar with built-in catches; the fixed track suits some body shapes less.",
            tags = listOf("Glutes", "Heavy loading", "Guided bar"),
        ),
    )
    put(
        "smith machine romanian deadlift",
        ExerciseWhy(
            purpose = "Hinges the bar down your legs on a fixed track, stretching the hamstrings and loading the glutes, with the back holding.",
            pickIt = "Pick it to learn the hinge or push hamstrings with the bar locked in; the track sets your bar line, so it suits some bodies less.",
            tags = listOf("Hamstrings", "Hinge pattern", "Guided path"),
        ),
    )
    put(
        "dumbbell romanian deadlift",
        ExerciseWhy(
            purpose = "Hinges with dumbbells at your thighs, stretching the hamstrings and loading the glutes, with back and grip holding.",
            pickIt = "Pick it over the barbell version for easy setup and a simple start; grip and dumbbell size cap how heavy you can go.",
            tags = listOf("Hamstrings", "Glutes", "Grip"),
        ),
    )
    put(
        "kettlebell romanian deadlift",
        ExerciseWhy(
            purpose = "Hinges with one kettlebell held in front, stretching the hamstrings and loading the glutes with a compact weight.",
            pickIt = "Pick it when a kettlebell is all you have, or to groove the hinge before swings; one bell caps the load below a barbell or dumbbell pair.",
            tags = listOf("Hamstrings", "Hinge pattern", "Simple setup"),
        ),
    )
    put(
        "machine glute kickback",
        ExerciseWhy(
            purpose = "Isolates the glutes by pushing a pedal back with the chest supported against a pad.",
            pickIt = "Pick it over cable kickbacks for stable, easy-to-load work; it is single-leg and lighter than thrusts or squats, and needs the machine.",
            tags = listOf("Glutes", "Stable support", "Isolation"),
        ),
    )
    put(
        "cable standing hip abduction",
        ExerciseWhy(
            purpose = "Lifts the leg out to the side against a cable, working the outer hip muscles and glutes.",
            pickIt = "Pick it over banded versions for a steady, adjustable load; it is light and balance-limited, so hold a support and stay upright.",
            tags = listOf("Outer hips", "Adjustable load", "Hip abduction"),
        ),
    )
    put(
        "cable standing hip adduction",
        ExerciseWhy(
            purpose = "Pulls the leg across the body against a cable, working the adductors on the inner thigh.",
            pickIt = "Pick it to train the adductors directly when squats and lunges feel light; a machine is easier to set, but this needs a cable and balance.",
            tags = listOf("Inner thigh", "Adductors", "Single leg"),
        ),
    )
    put(
        "smith machine bulgarian split squat",
        ExerciseWhy(
            purpose = "Squats down on one leg with the back foot raised, loading the quads and glutes under a guided bar.",
            pickIt = "Pick it over the free Bulgarian split squat when balance limits you; the track steadies you but gives up some of the stability work.",
            tags = listOf("Quads and glutes", "Single leg", "Stable path"),
        ),
    )
    put(
        "smith machine reverse lunge",
        ExerciseWhy(
            purpose = "Steps back under a guided bar, loading the quads and glutes one leg at a time.",
            pickIt = "Pick it over free reverse lunges to load heavily with less balance demand; the track fixes your path, so find a step length that fits it.",
            tags = listOf("Quads and glutes", "Heavy loading", "Stable path"),
        ),
    )
    put(
        "smith machine split squat",
        ExerciseWhy(
            purpose = "Lowers straight down in a staggered stance under a guided bar, loading the quads and glutes with no stepping.",
            pickIt = "Pick it over the reverse lunge for a fixed stance and simple setup; the track removes balance demand, so stabilisers work less.",
            tags = listOf("Quads and glutes", "Stable", "Heavy loading"),
        ),
    )
    put(
        "heel-elevated goblet squat",
        ExerciseWhy(
            purpose = "Squats with heels raised and a dumbbell at the chest, letting the knees travel forward to load the quads deeply.",
            pickIt = "Pick it over the flat-heel goblet squat if ankle mobility limits depth; it biases the quads, but the load is capped by what you can hold.",
            tags = listOf("Quads", "Depth", "Ankle limits"),
        ),
    )
    put(
        "dumbbell sumo squat",
        ExerciseWhy(
            purpose = "Squats in a wide stance with toes turned out, loading the glutes and quads, with the adductors working hard.",
            pickIt = "Pick it over the goblet squat if a wide stance feels better at depth; one dumbbell caps the load, and hip comfort in the stance varies.",
            tags = listOf("Glutes", "Adductors", "Wide stance"),
        ),
    )
    put(
        "dumbbell sumo deadlift",
        ExerciseWhy(
            purpose = "Pulls one dumbbell from the floor in a wide stance, loading the glutes and inner thighs, with back and grip holding on.",
            pickIt = "Pick it over a conventional dumbbell deadlift if a wide stance fits you; the pull is shorter, but one dumbbell caps the load.",
            tags = listOf("Glutes, adductors", "Short pull", "Grip"),
        ),
    )
    put(
        "front-foot elevated split squat",
        ExerciseWhy(
            purpose = "Lowers into a split squat with the front foot raised, deepening the range to load the quads and glutes in a stretch.",
            pickIt = "Pick it over the flat split squat for more range; deeper positions need more hip and knee mobility and usually lighter dumbbells.",
            tags = listOf("Quads", "Glute stretch", "Deep range"),
        ),
    )
    put(
        "deficit reverse lunge",
        ExerciseWhy(
            purpose = "Steps back off a platform so the front leg sinks deeper, loading the quads and glutes at long muscle length.",
            pickIt = "Pick it over the flat reverse lunge for more range; the extra depth demands balance and a lighter load.",
            tags = listOf("Quads and glutes", "Deep range", "Single leg"),
        ),
    )
    put(
        "dumbbell lateral lunge",
        ExerciseWhy(
            purpose = "Steps sideways onto one leg while the other stays straight, loading the quads, glutes and adductors from the side.",
            pickIt = "Pick it over forward lunges for side-to-side strength and adductor stretch; it is awkward at first, so start light and go slowly.",
            tags = listOf("Side-to-side", "Adductors", "Hip mobility"),
        ),
    )
    put(
        "dumbbell curtsy lunge",
        ExerciseWhy(
            purpose = "Steps behind and across into a lunge, loading the quads and glutes, with the crossed stance adding outer-glute work.",
            pickIt = "Pick it for variety or extra outer-hip work; it is harder to balance and lighter than reverse lunges, so treat it as an accessory.",
            tags = listOf("Glutes", "Outer hips", "Single leg"),
        ),
    )
    put(
        "landmine squat",
        ExerciseWhy(
            purpose = "Squats while holding the bar end at your chest, loading the quads and glutes with an upright torso that helps depth.",
            pickIt = "Pick it over the goblet squat for heavier loading in the same upright style; it needs a landmine and tops out below a back squat.",
            tags = listOf("Quads and glutes", "Upright torso", "Easy to learn"),
        ),
    )
}

private fun MutableMap<String, ExerciseWhy>.libraryWhysA2() {
    put(
        "landmine romanian deadlift",
        ExerciseWhy(
            purpose = "Hinges with the bar end at your thighs, stretching the hamstrings and loading the glutes along a gentle arc.",
            pickIt = "Pick it over the barbell version for an easy-to-learn hinge; it needs a landmine anchor and loads top out lighter than a barbell.",
            tags = listOf("Hamstrings", "Glutes", "Hinge pattern"),
        ),
    )
    put(
        "kettlebell swing",
        ExerciseWhy(
            purpose = "Snaps the hips to swing a kettlebell, training explosive glute and hamstring power, with the back and grip holding.",
            pickIt = "Pick it for hip power and conditioning; it needs a good hinge to learn, and loads stay below deadlifts, so it will not replace them.",
            tags = listOf("Hip power", "Glutes and hams", "Conditioning"),
        ),
    )
    put(
        "glute-focused back extension",
        ExerciseWhy(
            purpose = "Extends the hips over a back extension bench, working the glutes, hamstrings and lower back; the cues favour the glutes.",
            pickIt = "Pick it for bodyweight hip extension on a bench you may already have; adding load means holding a plate.",
            tags = listOf("Glutes", "Lower back", "Hip extension"),
        ),
    )
    put(
        "reverse hyperextension",
        ExerciseWhy(
            purpose = "Swings the legs up behind you with the torso supported, working the lower back, glutes and hamstrings.",
            pickIt = "Pick it over back extensions for hip extension with the torso fixed; it needs a specific machine, and swung weight should be controlled.",
            tags = listOf("Lower back", "Glutes", "Hip extension"),
        ),
    )
    put(
        "donkey calf raise",
        ExerciseWhy(
            purpose = "Raises the heels with the hips hinged and calves stretched under load, building the calf muscles.",
            pickIt = "Pick it for a deep stretch on straight legs; it needs a partner or machine, and a standing calf raise does the job with easier setup.",
            tags = listOf("Calves", "Deep stretch", "Loaded stretch"),
        ),
    )
    put(
        "leg press calf raise",
        ExerciseWhy(
            purpose = "Presses the balls of the feet on a leg press with straight legs, working the calves under heavy load.",
            pickIt = "Pick it when a leg press is already there; loading and balance are easy, though the sled limits how far the heels can drop.",
            tags = listOf("Calves", "Easy to load", "Stable"),
        ),
    )
    put(
        "wall sit",
        ExerciseWhy(
            purpose = "Holds a squat position against a wall, building quad endurance with an isometric hold, with the glutes helping.",
            pickIt = "Pick it for kit-free leg work or a quick burnout; there is no moving range or easy way to add load, so it supplements squats.",
            tags = listOf("Quad endurance", "Bodyweight only", "Isometric"),
        ),
    )
    put(
        "jump squat",
        ExerciseWhy(
            purpose = "Explodes up from a squat, training leg power in the quads and glutes and the control to land.",
            pickIt = "Pick it for speed and power, not size; keep landings soft and loads light, as impact adds up fast.",
            tags = listOf("Leg power", "Explosiveness", "Landing control"),
        ),
    )
    put(
        "incline dumbbell curl",
        ExerciseWhy(
            purpose = "Curls with the arms behind the torso on an incline bench, loading the biceps in a stretched position.",
            pickIt = "Pick it over a standard curl for a long-muscle biceps stimulus; it needs an incline bench, and the stretch means lighter dumbbells.",
            tags = listOf("Biceps", "Stretch position", "Strict form"),
        ),
    )
    put(
        "concentration curl",
        ExerciseWhy(
            purpose = "Curls a dumbbell seated with the elbow braced on the thigh, isolating the biceps with little room to swing.",
            pickIt = "Pick it for strict, one-arm biceps focus; it is slower and lighter than barbell or incline curls, so it works best as a finisher.",
            tags = listOf("Biceps", "No swing", "Single arm"),
        ),
    )
    put(
        "ez-bar curl",
        ExerciseWhy(
            purpose = "Curls a cambered bar, loading the biceps, with the brachialis and forearms assisting.",
            pickIt = "Pick it over the straight-bar curl if a straight bar bothers your wrists; the angled grip is more comfortable, though it varies by person.",
            tags = listOf("Biceps", "Wrist comfort", "Heavy loading"),
        ),
    )
    put(
        "spider curl",
        ExerciseWhy(
            purpose = "Curls with your chest on a bench and arms hanging forward, loading the biceps with no way to swing.",
            pickIt = "Pick it to remove momentum from the curl; it uses a bench and lighter weights, and stretches the biceps less than an incline curl.",
            tags = listOf("Biceps", "No swing", "Strict reps"),
        ),
    )
    put(
        "rope hammer curl",
        ExerciseWhy(
            purpose = "Curls a cable rope with a neutral grip, working the brachialis, biceps and forearms against steady tension.",
            pickIt = "Pick it over dumbbell hammer curls for constant tension; it needs a cable pulley and the load stays lighter.",
            tags = listOf("Brachialis", "Forearms", "Constant tension"),
        ),
    )
    put(
        "drag curl",
        ExerciseWhy(
            purpose = "Drags the bar up the torso with the elbows travelling back, working the biceps with less help from the front delts.",
            pickIt = "Pick it to keep curls strict and close to the body; the range is short, so use lighter weight and learn it before loading.",
            tags = listOf("Biceps", "Strict reps", "Low shoulder help"),
        ),
    )
    put(
        "rope triceps pushdown",
        ExerciseWhy(
            purpose = "Presses a cable rope down and apart, isolating the triceps against steady tension.",
            pickIt = "Pick it over the straight-bar pushdown to spread the rope at the bottom; keep elbows at your sides, and loads stay below presses or dips.",
            tags = listOf("Triceps", "Constant tension", "Easy to load"),
        ),
    )
    put(
        "two dumbbell skullcrusher",
        ExerciseWhy(
            purpose = "Lowers two dumbbells beside the head and extends the elbows, isolating the triceps with a free path for each arm.",
            pickIt = "Pick it over the barbell skull crusher for wrist and elbow freedom; both arms must be controlled, and kicking heavy dumbbells up is awkward.",
            tags = listOf("Triceps", "Free path", "Even sides"),
        ),
    )
    put(
        "one-arm dumbbell skull crusher",
        ExerciseWhy(
            purpose = "Extends one elbow at a time lying down, isolating the triceps with the other hand steadying.",
            pickIt = "Pick it to even out arm differences with full focus on one side; it takes twice the time of the two-dumbbell version.",
            tags = listOf("Triceps", "Even sides", "Single arm"),
        ),
    )
    put(
        "dumbbell overhead triceps extension",
        ExerciseWhy(
            purpose = "Lowers a dumbbell behind the head and extends, loading the triceps with the long head stretched overhead.",
            pickIt = "Pick it over pushdowns to train the triceps in a stretch; heavy sets stress the elbows, so keep the load moderate.",
            tags = listOf("Long head", "Stretch", "Triceps mass"),
        ),
    )
    put(
        "one-arm dumbbell triceps extension",
        ExerciseWhy(
            purpose = "Extends one arm overhead with a dumbbell, loading the triceps with the long head stretched, one side at a time.",
            pickIt = "Pick it over the two-hand version to fix side differences and keep the elbow in place; it takes longer, and the load stays light.",
            tags = listOf("Long head", "Even sides", "Single arm"),
        ),
    )
    put(
        "wrist extension",
        ExerciseWhy(
            purpose = "Lifts a light dumbbell with the back of the hand up, training the wrist extensors on top of the forearm.",
            pickIt = "Pick it to balance forearm work done by curls and rows; the muscles are small, so use light loads and higher reps.",
            tags = listOf("Forearms", "Grip balance", "Light load"),
        ),
    )
    put(
        "crunch",
        ExerciseWhy(
            purpose = "Curls the ribs toward the pelvis, training the abs with a small, easy-to-learn movement.",
            pickIt = "Pick it as a simple ab exercise needing no kit; it gets easy fast, so move to weighted or cable crunches to keep progressing.",
            tags = listOf("Abs", "Bodyweight only", "Easy to learn"),
        ),
    )
    put(
        "reverse crunch",
        ExerciseWhy(
            purpose = "Curls the pelvis toward the ribs, training the abs from the lower end, with the neck out of the picture.",
            pickIt = "Pick it over the crunch if your neck strains; swinging the legs cheats the rep, so lift the hips slowly.",
            tags = listOf("Abs", "Neck-free", "Bodyweight only"),
        ),
    )
    put(
        "russian twist",
        ExerciseWhy(
            purpose = "Rotates the trunk side to side while seated, training the obliques, with the abs and lower back assisting.",
            pickIt = "Pick it for rotation work without kit; turn from the ribs, not the arms, and use Pallof presses for a harder anti-rotation option.",
            tags = listOf("Obliques", "Rotation", "Bodyweight only"),
        ),
    )
    put(
        "bicycle crunch",
        ExerciseWhy(
            purpose = "Brings each elbow toward the opposite knee in turn, training the abs and obliques.",
            pickIt = "Pick it for a dynamic bodyweight ab move; keep it slow, as speed turns it into leg flailing, and it does not suit heavy loading.",
            tags = listOf("Abs and obliques", "Bodyweight only", "Coordination"),
        ),
    )
    put(
        "mountain climber",
        ExerciseWhy(
            purpose = "Drives the knees toward the chest from a plank, raising your heart rate as abs, hip flexors, shoulders and quads work.",
            pickIt = "Pick it for conditioning in bodyweight circuits; it builds little strength, and fast reps fall apart unless the plank stays stiff.",
            tags = listOf("Conditioning", "Plank stability", "Bodyweight only"),
        ),
    )
    put(
        "bird dog",
        ExerciseWhy(
            purpose = "Reaches the opposite arm and leg while the spine stays still, training the back extensors, glutes and shoulders.",
            pickIt = "Pick it to practise bracing the trunk with moving limbs; for real strength gains, planks and loaded work demand more.",
            tags = listOf("Trunk control", "Lower back", "Glutes"),
        ),
    )
    put(
        "half-kneeling pallof press",
        ExerciseWhy(
            purpose = "Presses a cable out from the chest and resists the sideways pull, training the obliques and abs to resist rotation.",
            pickIt = "Pick it over the standing Pallof for a narrower base that challenges hips and trunk; it moves less load and needs a cable.",
            tags = listOf("Anti-rotation", "Obliques", "Hip stability"),
        ),
    )
    put(
        "cable pallof hold",
        ExerciseWhy(
            purpose = "Holds a cable at the chest while resisting the twist, training the trunk to resist rotation with an isometric.",
            pickIt = "Pick it over the Pallof press for a simpler hold with no moving arms; the load is small, so time under tension is the progression.",
            tags = listOf("Anti-rotation", "Obliques", "Static strength"),
        ),
    )
    put(
        "captain's chair knee raise",
        ExerciseWhy(
            purpose = "Raises the knees on forearm pads with the back supported, working the abs and hip flexors without a grip.",
            pickIt = "Pick it over hanging knee raises when grip or swinging is the limit; it needs the station and has less trunk demand than hanging.",
            tags = listOf("Abs", "Hip flexors", "No grip needed"),
        ),
    )
    put(
        "decline sit-up",
        ExerciseWhy(
            purpose = "Curls up on a decline bench, training the abs and hip flexors with extra range and gravity as resistance.",
            pickIt = "Pick it over the floor crunch for more resistance; hip flexors take over if you yank up, so go slowly and curl the spine.",
            tags = listOf("Abs", "Extra resistance", "Range"),
        ),
    )
    put(
        "weighted crunch",
        ExerciseWhy(
            purpose = "Crunches with a plate held on the chest, training the abs with added load.",
            pickIt = "Pick it over the plain crunch once bodyweight is easy; the range is short and loads stay small, so cable crunches progress further.",
            tags = listOf("Abs", "Progressive load", "Simple setup"),
        ),
    )
    put(
        "weighted russian twist",
        ExerciseWhy(
            purpose = "Rotates the trunk with a dumbbell, loading the obliques, with the abs and lower back assisting.",
            pickIt = "Pick it over the bodyweight twist when that is easy; keep the weight moderate, since too heavy turns it into an arm swing.",
            tags = listOf("Obliques", "Progressive load", "Rotation"),
        ),
    )
    put(
        "dumbbell side bend",
        ExerciseWhy(
            purpose = "Bends sideways holding a dumbbell, directly loading the obliques.",
            pickIt = "Pick it for simple direct oblique work with one dumbbell; carries and Pallof presses train the same area without bending the spine.",
            tags = listOf("Obliques", "Simple setup", "Dumbbell only"),
        ),
    )
    put(
        "treadmill incline walk",
        ExerciseWhy(
            purpose = "Walks uphill at a steep gradient, working the glutes, calves, quads and hamstrings at low impact.",
            pickIt = "Pick it for low-impact conditioning and leg endurance; it builds little strength compared with loaded work and needs a treadmill.",
            tags = listOf("Low impact", "Conditioning", "Glutes and calves"),
        ),
    )
    put(
        "battle ropes",
        ExerciseWhy(
            purpose = "Whips heavy ropes in waves, training shoulder and trunk endurance, grip and conditioning.",
            pickIt = "Pick it for short, hard conditioning intervals with little impact; it tires you more than it builds strength, and needs ropes and an anchor.",
            tags = listOf("Conditioning", "Grip", "Low impact"),
        ),
    )
    put(
        "knee push-up",
        ExerciseWhy(
            purpose = "Presses from the knees, training the chest, front delts and triceps with less bodyweight than a full push-up.",
            pickIt = "Pick it over the full push-up while strict reps are out of reach; leave it for full push-ups once you reach about ten clean ones.",
            tags = listOf("Chest and triceps", "Beginner friendly", "Bodyweight only"),
        ),
    )
    put(
        "wide push-up",
        ExerciseWhy(
            purpose = "Presses with the hands wide, shifting more of the work onto the chest and somewhat away from the triceps.",
            pickIt = "Pick it over the standard push-up for chest bias; wide hands can bother shoulders, so narrow them if they do.",
            tags = listOf("Chest", "Wide hands", "Bodyweight only"),
        ),
    )
    put(
        "decline push-up",
        ExerciseWhy(
            purpose = "Presses with feet raised, shifting load toward the upper chest and front delts, with more bodyweight on the arms.",
            pickIt = "Pick it over the standard push-up when that is easy and you lack weights; height jumps difficulty fast, so raise the feet gradually.",
            tags = listOf("Upper chest", "Front delts", "Bodyweight only"),
        ),
    )
    put(
        "feet-elevated pike push-up",
        ExerciseWhy(
            purpose = "Presses from an inverted V with feet raised, building the front delts and triceps with a handstand-like pattern.",
            pickIt = "Pick it over the floor pike push-up for more load; it leads toward handstand push-ups, and needs a bench and shoulder strength.",
            tags = listOf("Shoulders", "Triceps", "Handstand path"),
        ),
    )
    put(
        "typewriter push-up",
        ExerciseWhy(
            purpose = "Slides across the bottom of a wide push-up, putting more load on one arm at a time, with chest and triceps working.",
            pickIt = "Pick it for harder bodyweight pressing on the way to archer push-ups; it asks for solid wide push-ups first and some shoulder tolerance.",
            tags = listOf("Chest", "Single-arm load", "Bodyweight only"),
        ),
    )
    put(
        "explosive push-up",
        ExerciseWhy(
            purpose = "Pushes off the floor with speed, training pressing power in the chest, triceps and front delts.",
            pickIt = "Pick it for power once your push-ups are solid; use low reps early in a set, and take care landing on your wrists.",
            tags = listOf("Pressing power", "Explosiveness", "Bodyweight only"),
        ),
    )
    put(
        "hindu push-up",
        ExerciseWhy(
            purpose = "Sweeps the chest through a dive and arch, working the chest, shoulders and triceps in a flowing arc.",
            pickIt = "Pick it as a flowing push-up that mixes pressing with shoulder and spine mobility; it loads little, so standard push-ups win for strength.",
            tags = listOf("Shoulders", "Mobility", "Bodyweight only"),
        ),
    )
    put(
        "push-up shoulder tap",
        ExerciseWhy(
            purpose = "Taps a shoulder from a push-up plank, training the trunk to resist twisting, with the chest and shoulders holding.",
            pickIt = "Pick it for trunk stability in a plank; it is not a main pressing builder, and wide feet make it easier.",
            tags = listOf("Trunk stability", "Shoulders", "Bodyweight only"),
        ),
    )
    put(
        "wall push-up",
        ExerciseWhy(
            purpose = "Presses away from a wall, the lightest push-up for the chest, front delts and triceps.",
            pickIt = "Pick it as a first step before incline or knee push-ups; the load is low, so you will outgrow it quickly.",
            tags = listOf("Beginner friendly", "Chest", "Bodyweight only"),
        ),
    )
    put(
        "wall walk",
        ExerciseWhy(
            purpose = "Walks the feet up a wall into a handstand-style hold, loading the shoulders and triceps with the trunk bracing.",
            pickIt = "Pick it as a stepping stone to handstand push-ups; it needs a clear wall, strong wrists and shoulders, and a stiff trunk.",
            tags = listOf("Overhead strength", "Shoulders", "Handstand control"),
        ),
    )
    put(
        "towel row",
        ExerciseWhy(
            purpose = "Rows toward a towel anchored on a post, training the mid back and lats, with the biceps and forearms assisting.",
            pickIt = "Pick it when you have no bars or machines; the towel taxes your grip, and foot position scales the difficulty.",
            tags = listOf("Back", "Grip", "Home friendly"),
        ),
    )
    put(
        "prone t raise",
        ExerciseWhy(
            purpose = "Lifts the arms out to a T while lying face down, training the rear delts, mid traps and rhomboids.",
            pickIt = "Pick it as a no-kit upper back accessory; there is no load, so reps and holds do the work, and other rear delt lifts progress further.",
            tags = listOf("Rear delts", "Upper back", "Bodyweight only"),
        ),
    )
    put(
        "superman",
        ExerciseWhy(
            purpose = "Lifts the arms and legs off the floor face down, training the back extensors and glutes.",
            pickIt = "Pick it as an easy, kit-free back extensor hold; it is light, so move to back extensions or Romanian deadlifts for strength.",
            tags = listOf("Lower back", "Glutes", "Bodyweight only"),
        ),
    )
    put(
        "reverse snow angel",
        ExerciseWhy(
            purpose = "Sweeps the arms in an arc while face down, training the rear delts, with the mid back and traps helping.",
            pickIt = "Pick it as a bodyweight rear delt drill with a long path; it has no external load, and band pull-aparts add resistance.",
            tags = listOf("Rear delts", "Upper back", "Bodyweight only"),
        ),
    )
    put(
        "commando pull-up",
        ExerciseWhy(
            purpose = "Pulls up from a staggered neutral grip, training the lats, upper back and biceps while alternating sides.",
            pickIt = "Pick it over the standard pull-up for variety and a neutral grip; it is harder to keep square, so alternate sides evenly.",
            tags = listOf("Lats", "Biceps", "Neutral grip"),
        ),
    )
}

private fun MutableMap<String, ExerciseWhy>.libraryWhysA3() {
    put(
        "towel pull-up",
        ExerciseWhy(
            purpose = "Pulls up on a towel draped over the bar, training the lats, upper back and biceps, with a big demand on grip.",
            pickIt = "Pick it over the standard pull-up to train grip; the towel makes grip the limiter, so expect fewer reps.",
            tags = listOf("Grip", "Lats", "Biceps"),
        ),
    )
    put(
        "forward lunge",
        ExerciseWhy(
            purpose = "Steps forward into a lunge, building the quads and glutes one leg at a time.",
            pickIt = "Pick it over the reverse lunge for a more demanding step and stop on the front leg; the reverse version is easier to control.",
            tags = listOf("Quads and glutes", "Single leg", "Bodyweight only"),
        ),
    )
    put(
        "lateral lunge",
        ExerciseWhy(
            purpose = "Steps sideways onto one leg, loading the quads and glutes, with the adductors working hard.",
            pickIt = "Pick it for side-to-side strength and adductor stretch that forward lunges miss; load is only bodyweight, so it suits balance and mobility.",
            tags = listOf("Side-to-side", "Adductors", "Hip mobility"),
        ),
    )
    put(
        "curtsy lunge",
        ExerciseWhy(
            purpose = "Steps behind and across into a lunge, loading the quads and glutes, with the crossed stance adding outer-glute work.",
            pickIt = "Pick it for variety and outer-hip work with no kit; it is harder to balance than a reverse lunge, so keep the hips square.",
            tags = listOf("Glutes", "Outer hips", "Bodyweight only"),
        ),
    )
    put(
        "skater squat",
        ExerciseWhy(
            purpose = "Squats on one leg with the other knee bent behind, building the quads and glutes, with balance as part of the work.",
            pickIt = "Pick it before pistol squats as a step toward single-leg squats; it is harder than lunges, and a shallow range is fine to start.",
            tags = listOf("One-leg strength", "Balance", "Bodyweight only"),
        ),
    )
    put(
        "single-leg box squat",
        ExerciseWhy(
            purpose = "Sits back onto a box on one leg, building the quads and glutes with a controlled descent.",
            pickIt = "Pick it over the skater squat for a clear depth target; the box makes it easier to control, and lowering the box makes it harder.",
            tags = listOf("One-leg strength", "Depth control", "Pistol progression"),
        ),
    )
    put(
        "step-down",
        ExerciseWhy(
            purpose = "Lowers one foot toward the floor from a box on the other leg, loading the quads and glutes while they lengthen.",
            pickIt = "Pick it for slow, controlled single-leg work; it is light, and box height scales it, so start low and go slowly.",
            tags = listOf("Quads", "Knee control", "Single leg"),
        ),
    )
    put(
        "calf raise",
        ExerciseWhy(
            purpose = "Rises onto the toes, training the calves with the ankles doing the work.",
            pickIt = "Pick it as the simplest calf exercise, anywhere; once bodyweight is easy, go single-leg or add weight to keep progressing.",
            tags = listOf("Calves", "Bodyweight only", "Ankle strength"),
        ),
    )
    put(
        "glute bridge march",
        ExerciseWhy(
            purpose = "Alternates lifting feet at the top of a bridge, training the glutes while the hips resist rotation.",
            pickIt = "Pick it as a lighter bridge variation that adds hip stability; for more glute load, move to the plain bridge or a hip thrust.",
            tags = listOf("Glutes", "Hip stability", "Bodyweight only"),
        ),
    )
    put(
        "frog pump",
        ExerciseWhy(
            purpose = "Presses the hips up with soles together and knees wide, working the glutes through a short range.",
            pickIt = "Pick it as a bodyweight glute pump for high reps; it rarely overloads, so use hip thrusts for strength.",
            tags = listOf("Glutes", "High reps", "Bodyweight only"),
        ),
    )
    put(
        "donkey kick",
        ExerciseWhy(
            purpose = "Kicks one leg up from all fours, working the glutes with bodyweight.",
            pickIt = "Pick it for bodyweight glute isolation; it is light, and cable or machine kickbacks give a load you can adjust.",
            tags = listOf("Glutes", "Bodyweight only", "Isolation"),
        ),
    )
    put(
        "fire hydrant",
        ExerciseWhy(
            purpose = "Lifts the knee out to the side from all fours, working the outer hips and glutes.",
            pickIt = "Pick it for a bodyweight hip warm-up or burnout; a band adds resistance when it feels easy.",
            tags = listOf("Outer hips", "Bodyweight only", "Hip control"),
        ),
    )
    put(
        "clamshell",
        ExerciseWhy(
            purpose = "Opens the top knee while lying on your side, working the outer hip muscles and glutes with very low load.",
            pickIt = "Pick it as a gentle warm-up drill; standing, loaded hip abduction is more demanding, so it suits a start more than a strength block.",
            tags = listOf("Outer hips", "Activation", "Bodyweight only"),
        ),
    )
    put(
        "hip airplane",
        ExerciseWhy(
            purpose = "Rotates the hip over a planted foot, training hip control and balance, with the glutes and adductors lightly working.",
            pickIt = "Pick it as a hip control and mobility drill, not a strength builder; use lunges or Romanian deadlifts to load the hips.",
            tags = listOf("Hip control", "Balance", "Mobility"),
        ),
    )
    put(
        "side-lying leg raise",
        ExerciseWhy(
            purpose = "Lifts the top leg while lying on your side, working the outer hip muscles and glutes.",
            pickIt = "Pick it for simple, kit-free hip abduction; it is light, so use bands or cable versions once it feels easy.",
            tags = listOf("Outer hips", "Bodyweight only", "Simple setup"),
        ),
    )
    put(
        "lying hamstring walkout",
        ExerciseWhy(
            purpose = "Walks the feet away from a bridge, training the hamstrings as they lengthen, with the glutes holding the bridge.",
            pickIt = "Pick it for hamstrings without a machine; it sits between a bridge and a leg curl, and shortening the walk makes it easier.",
            tags = listOf("Hamstrings", "Bodyweight only", "Hip hold"),
        ),
    )
    put(
        "towel hamstring curl",
        ExerciseWhy(
            purpose = "Slides the heels in on a towel from a bridge, training the hamstrings.",
            pickIt = "Pick it for hamstring work at home; it needs a smooth floor and gets hard fast, so shorten the range if you cramp.",
            tags = listOf("Hamstrings", "Home friendly", "Hip hold"),
        ),
    )
    put(
        "stability ball hamstring curl",
        ExerciseWhy(
            purpose = "Rolls a ball in with the heels from a bridge, training the hamstrings while the glutes hold the hips up.",
            pickIt = "Pick it over the towel curl for a smooth, steadier path; it needs a ball and balance, and is harder than a seated leg curl.",
            tags = listOf("Hamstrings", "Core control", "Home friendly"),
        ),
    )
    put(
        "banded glute bridge",
        ExerciseWhy(
            purpose = "Bridges with a band above the knees, loading the glutes and outer hips as you push out against it.",
            pickIt = "Pick it over the plain bridge to add outer hip effort; the band is light resistance, so it does not replace a loaded thrust.",
            tags = listOf("Glutes", "Outer hips", "Light kit"),
        ),
    )
    put(
        "banded hip thrust",
        ExerciseWhy(
            purpose = "Thrusts the hips up from a bench with a band above the knees, loading the glutes and outer hips.",
            pickIt = "Pick it over the plain hip thrust to add outer hip effort cheaply; the band is light, so add weight to progress.",
            tags = listOf("Glutes", "Outer hips", "Light kit"),
        ),
    )
    put(
        "banded frog pump",
        ExerciseWhy(
            purpose = "Presses the hips up with soles together and a band above the knees, working the glutes with added resistance.",
            pickIt = "Pick it over the plain frog pump when that feels easy; the band adds modest load, and hip thrusts handle heavier loading.",
            tags = listOf("Glutes", "High reps", "Light kit"),
        ),
    )
    put(
        "banded clamshell",
        ExerciseWhy(
            purpose = "Opens the top knee against a band while lying on your side, working the outer hips and glutes.",
            pickIt = "Pick it over the plain clamshell when that feels too easy; keep the feet together and the hips stacked.",
            tags = listOf("Outer hips", "Activation", "Light kit"),
        ),
    )
    put(
        "banded lateral walk",
        ExerciseWhy(
            purpose = "Steps sideways in a quarter squat against a band, working the outer hips and glutes.",
            pickIt = "Pick it as a warm-up or finisher for the outer hips; it needs space, and the load is light.",
            tags = listOf("Outer hips", "Warm-up", "Light kit"),
        ),
    )
    put(
        "banded monster walk",
        ExerciseWhy(
            purpose = "Steps forward and out in a quarter squat against a band, working the outer hips and glutes.",
            pickIt = "Pick it over the lateral walk to work the hips with forward travel as well; it needs space, and stays low in load.",
            tags = listOf("Outer hips", "Glutes", "Light kit"),
        ),
    )
    put(
        "banded squat",
        ExerciseWhy(
            purpose = "Squats with a band above the knees, working the quads and glutes while you push out against the band.",
            pickIt = "Pick it over the bodyweight squat to cue the knees out and warm up the hips; the band is light, so goblet squats suit strength better.",
            tags = listOf("Quads and glutes", "Knee tracking", "Light kit"),
        ),
    )
    put(
        "banded donkey kick",
        ExerciseWhy(
            purpose = "Kicks one leg back against a band from all fours, working the glutes.",
            pickIt = "Pick it over the plain donkey kick when that feels easy; the band adds modest resistance, and you can keep it at home.",
            tags = listOf("Glutes", "Hip extension", "Light kit"),
        ),
    )
    put(
        "banded fire hydrant",
        ExerciseWhy(
            purpose = "Lifts the knee out against a band from all fours, working the outer hips and glutes.",
            pickIt = "Pick it over the plain fire hydrant when it feels too easy; the band adds modest resistance, so keep the hips level.",
            tags = listOf("Outer hips", "Glutes", "Light kit"),
        ),
    )
    put(
        "banded kickback",
        ExerciseWhy(
            purpose = "Kicks the leg back against a band anchored at the ankle, working the glutes.",
            pickIt = "Pick it over the cable kickback when you lack a cable; tension is highest at the end of the stroke, and the load stays light.",
            tags = listOf("Glutes", "Portable", "Isolation"),
        ),
    )
    put(
        "banded standing hip abduction",
        ExerciseWhy(
            purpose = "Lifts the leg out to the side against a band, working the outer hips and glutes.",
            pickIt = "Pick it over the cable version when you have no machine; tension rises through the stroke, and balance limits how hard you can go.",
            tags = listOf("Outer hips", "Portable", "Hip abduction"),
        ),
    )
    put(
        "banded seated hip abduction",
        ExerciseWhy(
            purpose = "Presses the knees apart against a band while seated, working the outer hips and glutes.",
            pickIt = "Pick it for a seated, stable hip abduction; the load is light, which suits warm-ups before squats and thrusts.",
            tags = listOf("Outer hips", "Seated", "Light kit"),
        ),
    )
    put(
        "band pull-apart",
        ExerciseWhy(
            purpose = "Pulls a band apart at chest height, working the rear delts, rhomboids and mid traps.",
            pickIt = "Pick it for easy upper-back volume or warm-ups anywhere; the load is light, so use a reverse pec deck or cable fly for heavier sets.",
            tags = listOf("Rear delts", "Upper back", "Portable"),
        ),
    )
    put(
        "banded face pull",
        ExerciseWhy(
            purpose = "Pulls a band toward the face, working the rear delts and upper back, with the cuff helping.",
            pickIt = "Pick it over the cable face pull when you lack a cable; the band is lighter and changes tension through the stroke.",
            tags = listOf("Rear delts", "Upper back", "Portable"),
        ),
    )
    put(
        "banded row",
        ExerciseWhy(
            purpose = "Rows against a band from a seated position, training the mid back and lats, with the biceps and rear delts helping.",
            pickIt = "Pick it for portable rowing; the tension rises through the pull, and loads stay lighter than a cable or barbell row.",
            tags = listOf("Mid back", "Portable", "Scalable load"),
        ),
    )
    put(
        "banded lat pulldown",
        ExerciseWhy(
            purpose = "Pulls a band from overhead down to the chest, training the lats, with the upper back and biceps helping.",
            pickIt = "Pick it when you lack a machine; the load is limited, so use lat pulldowns or pull-ups for heavier work.",
            tags = listOf("Lats", "Portable", "Scalable load"),
        ),
    )
    put(
        "banded pallof press",
        ExerciseWhy(
            purpose = "Presses a band out from the chest while resisting the twist, training the obliques and abs to prevent rotation.",
            pickIt = "Pick it over the cable Pallof when you lack a cable; tension rises as you press, so your distance from the anchor sets the difficulty.",
            tags = listOf("Anti-rotation", "Obliques", "Portable"),
        ),
    )
    put(
        "banded woodchop",
        ExerciseWhy(
            purpose = "Pulls a band diagonally across the body, training the obliques and abs through a rotating, chopping path.",
            pickIt = "Pick it for rotation work with minimal kit; control the path, and use Pallof presses when you want to resist twisting instead.",
            tags = listOf("Obliques", "Rotation", "Portable"),
        ),
    )
    put(
        "banded dead bug",
        ExerciseWhy(
            purpose = "Lowers the opposite arm and leg while holding a band taut, training the abs, with the hip flexors helping.",
            pickIt = "Pick it over the plain dead bug when that is easy; the band adds shoulder tension, so keep your low back down and move slowly.",
            tags = listOf("Abs", "Back control", "Light kit"),
        ),
    )
    put(
        "hollow rock",
        ExerciseWhy(
            purpose = "Rocks in a hollow body shape, training the abs and obliques with constant tension.",
            pickIt = "Pick it as a gymnastics-style ab drill; the shape is hard to hold, and bending the knees makes it easier.",
            tags = listOf("Abs", "Tension control", "Bodyweight only"),
        ),
    )
    put(
        "v-up",
        ExerciseWhy(
            purpose = "Folds the body to bring the hands toward the toes, training the abs and hip flexors.",
            pickIt = "Pick it over crunches for a harder bodyweight move; the hip flexors take over if you swing, so lower slowly.",
            tags = listOf("Abs", "Hip flexors", "Bodyweight only"),
        ),
    )
    put(
        "flutter kick",
        ExerciseWhy(
            purpose = "Kicks the legs in small alternating strokes, training the abs and hip flexors with the back kept flat.",
            pickIt = "Pick it for endurance-style ab work; the back arches if the legs drop too low, so raise them higher to keep it flat.",
            tags = listOf("Abs", "Hip flexors", "Bodyweight only"),
        ),
    )
    put(
        "toe touch",
        ExerciseWhy(
            purpose = "Reaches toward the toes from a legs-up position, training the abs.",
            pickIt = "Pick it as a beginner-friendly crunch with the legs up; the range is short, so move to weighted or cable crunches later.",
            tags = listOf("Abs", "Easy to scale", "Bodyweight only"),
        ),
    )
    put(
        "plank shoulder tap",
        ExerciseWhy(
            purpose = "Taps a shoulder from a plank, training the abs and obliques to resist twisting.",
            pickIt = "Pick it over the static plank when that is easy; wider feet make it easier, and hips that sway mean you should slow down.",
            tags = listOf("Anti-rotation", "Abs", "Bodyweight only"),
        ),
    )
    put(
        "plank jack",
        ExerciseWhy(
            purpose = "Jumps the feet out and in from a plank, working the abs and obliques, with the shoulders holding you up.",
            pickIt = "Pick it for conditioning on top of the plank; it is a high-impact move for wrists, so land lightly.",
            tags = listOf("Conditioning", "Abs", "Bodyweight only"),
        ),
    )
    put(
        "bear plank",
        ExerciseWhy(
            purpose = "Holds the knees just off the floor on all fours, training the abs and obliques, with the shoulders and quads helping.",
            pickIt = "Pick it as a step between kneeling and a full plank; the hold is short and hard, so build time gradually.",
            tags = listOf("Abs", "Plank stability", "Bodyweight only"),
        ),
    )
    put(
        "bear crawl",
        ExerciseWhy(
            purpose = "Crawls with the knees hovering, training the shoulders, quads, abs and coordination.",
            pickIt = "Pick it for full-body conditioning with no kit; it needs space, and a level back matters more than speed.",
            tags = listOf("Conditioning", "Coordination", "Bodyweight only"),
        ),
    )
    put(
        "crab walk",
        ExerciseWhy(
            purpose = "Walks on hands and feet facing up, working the triceps, glutes and trunk, with the shoulders loaded.",
            pickIt = "Pick it for a playful change of position; wrists and shoulders carry weight in an unusual spot, so keep the pace modest.",
            tags = listOf("Conditioning", "Triceps", "Bodyweight only"),
        ),
    )
    put(
        "inchworm",
        ExerciseWhy(
            purpose = "Walks the hands out to a plank and back, training the trunk and shoulders, with a stretch for the hamstrings.",
            pickIt = "Pick it as a warm-up or mobility drill; the load is low, so it will not build strength like loaded work.",
            tags = listOf("Warm-up", "Shoulders", "Hamstring stretch"),
        ),
    )
    put(
        "seated knee tuck",
        ExerciseWhy(
            purpose = "Tucks the knees toward the chest while seated, training the abs and hip flexors.",
            pickIt = "Pick it over the reverse crunch for an easier, supported version; the seated lean means a shorter range of motion.",
            tags = listOf("Abs", "Hip flexors", "Bodyweight only"),
        ),
    )
    put(
        "side plank hip dip",
        ExerciseWhy(
            purpose = "Dips and lifts the hips from a side plank, training the obliques and abs.",
            pickIt = "Pick it over the static side plank for a dynamic version; keep your elbow under the shoulder, and slow the dips down.",
            tags = listOf("Obliques", "Abs", "Bodyweight only"),
        ),
    )
    put(
        "copenhagen plank",
        ExerciseWhy(
            purpose = "Holds a side plank with the top leg on a bench, training the adductors and obliques.",
            pickIt = "Pick it to train the adductors hard; it is advanced and groin-intensive, so start with short holds or a bent top knee.",
            tags = listOf("Adductors", "Obliques", "Trunk stability"),
        ),
    )
}
