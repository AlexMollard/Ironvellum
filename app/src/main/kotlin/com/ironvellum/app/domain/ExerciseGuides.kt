package com.ironvellum.app.domain

/** How one catalogue movement is done: the setup, the steps in order, what to think about, what goes wrong. */
data class ExerciseGuide(
    val setup: String,
    val steps: List<String>,
    val cues: List<String>,
    val commonMistakes: List<String>,
)

/**
 * A how-to for every catalogue movement, keyed like [MuscleMap] by the
 * trimmed, lowercased name. Null means a user-created movement.
 *
 * Generated from the reviewed catalogue audit; one builder per group keeps
 * each method well under the JVM's 64 KB method limit.
 */
object ExerciseGuides {

    private fun key(exerciseName: String) = exerciseName.trim().lowercase()

    fun forName(name: String): ExerciseGuide? = guides[key(name)]

    /** Every guide's key, for the catalogue coverage tests. */
    internal val keys: Set<String> get() = guides.keys

    private val guides: Map<String, ExerciseGuide> by lazy {
        buildMap {
            push1()
            push2()
            pull1()
            pull2()
            legs1()
            legs2()
            core()
            progressionSteps()
            activities()
        }
    }

    // ---- Push, part one ----
    private fun MutableMap<String, ExerciseGuide>.push1() {
        put(
            "dip",
            ExerciseGuide(
                setup = "Grip two parallel bars, press up to straight arms and lean your torso slightly forward.",
                steps = listOf(
                    "Lower with control until your upper arms are about parallel to the floor.",
                    "Keep your elbows pointing back and slightly out.",
                    "Press through your hands until your arms are straight.",
                    "Pause briefly at the top, then start the next rep.",
                ),
                cues = listOf(
                    "Chest forward, ribs down",
                    "Shoulders away from your ears",
                    "Smooth, controlled descent",
                ),
                commonMistakes = listOf(
                    "Dropping so deep the shoulders feel pinched",
                    "Shrugging at the top",
                    "Swinging the legs to cheat the rep",
                ),
            ),
        )
        put(
            "push-up",
            ExerciseGuide(
                setup = "Place your hands slightly wider than your shoulders and stand on your toes, body in one straight line.",
                steps = listOf(
                    "Brace your stomach and squeeze your glutes.",
                    "Lower your chest to just above the floor with elbows about 45 degrees from your sides.",
                    "Press the floor away until your arms are straight.",
                    "Keep your neck neutral throughout.",
                ),
                cues = listOf(
                    "Plank body, no sagging",
                    "Elbows tucked, not flared",
                    "Chest touches the same spot each rep",
                ),
                commonMistakes = listOf(
                    "Hips sagging or piking up",
                    "Flaring elbows straight out to the sides",
                    "Stopping well short of full range",
                ),
            ),
        )
        put(
            "archer push-up",
            ExerciseGuide(
                setup = "Start in a wide push-up position with your hands well outside your shoulders and feet planted.",
                steps = listOf(
                    "Shift your weight toward one hand and bend that elbow to lower your chest.",
                    "Keep the opposite arm straight and let it slide out to the side.",
                    "Press back to the middle.",
                    "Repeat to the other side, alternating each rep.",
                ),
                cues = listOf(
                    "Straight arm is the support, not the worker",
                    "Hips level",
                    "Lower slowly on the working side",
                ),
                commonMistakes = listOf(
                    "Twisting the hips toward the working arm",
                    "Bending the straight arm",
                    "Going too wide before you have control",
                ),
            ),
        )
        put(
            "diamond push-up",
            ExerciseGuide(
                setup = "Set up in a push-up position with your hands close together under your chest, thumbs and index fingers near each other.",
                steps = listOf(
                    "Brace your stomach and keep your body in one line.",
                    "Lower your chest toward your hands, elbows brushing close to your ribs.",
                    "Press up until your arms are fully straight.",
                    "If your wrists complain, bring your hands a little wider.",
                ),
                cues = listOf(
                    "Elbows hug the ribs",
                    "Chest to hands",
                    "Full lockout each rep",
                ),
                commonMistakes = listOf(
                    "Flaring the elbows out",
                    "Letting the hips sag",
                    "Cutting the range short",
                ),
            ),
        )
        put(
            "pike push-up",
            ExerciseGuide(
                setup = "Start in a downward-dog shape with your hips high, hands shoulder-width apart and legs nearly straight.",
                steps = listOf(
                    "Bend your elbows and lower the top of your head toward the floor between your hands.",
                    "Keep your hips high and your elbows about 45 degrees from your sides.",
                    "Press back up until your arms are straight.",
                    "Raise your feet onto a step when this feels easy.",
                ),
                cues = listOf(
                    "Hips stay high",
                    "Head travels forward past the hands",
                    "Press the floor away",
                ),
                commonMistakes = listOf(
                    "Letting the hips drop so it becomes a normal push-up",
                    "Flaring elbows wide",
                    "Rushing the lowering phase",
                ),
            ),
        )
        put(
            "handstand push-up",
            ExerciseGuide(
                setup = "Kick up to a freestanding handstand in a clear space, hands shoulder-width apart, with a folded mat in front of your hands.",
                steps = listOf(
                    "Find your balance in the handstand before you start.",
                    "Bend your elbows forward and lower slowly, letting your weight shift slightly onto your fingers.",
                    "Touch your head lightly to the mat in front of your hands, forming a triangle.",
                    "Press back to straight arms, steering the balance with your fingertips.",
                    "Come down by stepping a leg down or cartwheeling out.",
                ),
                cues = listOf(
                    "Head and hands make a triangle",
                    "Ribs down, legs together",
                    "Fingertips steer the balance",
                ),
                commonMistakes = listOf(
                    "Using a wall, which is the Wall HSPU, not this",
                    "Letting the legs drift so the balance goes at the bottom",
                    "Starting before the freestanding handstand is steady",
                ),
            ),
        )
        put(
            "overhead press",
            ExerciseGuide(
                setup = "Stand with the bar at your collarbone, hands just outside your shoulders, feet hip-width and stomach braced.",
                steps = listOf(
                    "Squeeze your glutes and tuck your ribs down.",
                    "Press the bar straight up, moving your head back slightly to let it pass.",
                    "Push your head through once the bar clears it so it ends over your mid-foot.",
                    "Lower the bar back to your collarbone with control.",
                ),
                cues = listOf(
                    "Bar over mid-foot",
                    "Glutes tight, ribs down",
                    "Shrug up at the top",
                ),
                commonMistakes = listOf(
                    "Leaning back to press",
                    "Pushing the bar out in front of you",
                    "Bending the knees to drive it up (that is a push press)",
                ),
            ),
        )
        put(
            "bench press",
            ExerciseGuide(
                setup = "Lie on the bench with your eyes under the bar, feet flat, shoulder blades pinched together and down.",
                steps = listOf(
                    "Grip the bar a little wider than your shoulders and unrack it over your chest.",
                    "Lower the bar to your mid-chest with elbows about 45 to 70 degrees from your torso.",
                    "Press the bar back up over your shoulders.",
                    "Keep your feet planted and your upper back tight throughout.",
                ),
                cues = listOf(
                    "Shoulder blades pinned to the bench",
                    "Bar touches, then drives up",
                    "Wrists stacked over elbows",
                ),
                commonMistakes = listOf(
                    "Bouncing the bar off the chest",
                    "Letting the hips lift off the bench",
                    "Flaring elbows to 90 degrees",
                ),
            ),
        )
        put(
            "incline bench press",
            ExerciseGuide(
                setup = "Set the bench to a low to moderate incline, lie back with your shoulder blades pinched and feet flat.",
                steps = listOf(
                    "Unrack the bar over your upper chest with hands a little wider than your shoulders.",
                    "Lower the bar to your upper chest.",
                    "Press it back up over your shoulders.",
                    "Keep your back against the pad throughout.",
                ),
                cues = listOf(
                    "Bar to upper chest",
                    "Shoulder blades down and back",
                    "Smooth path up and down",
                ),
                commonMistakes = listOf(
                    "Setting the bench so steep it becomes a shoulder press",
                    "Bouncing the bar off the chest",
                    "Flaring the elbows wide",
                ),
            ),
        )
        put(
            "scapular push-up",
            ExerciseGuide(
                setup = "Get into a plank on your hands with arms completely straight and hands under your shoulders.",
                steps = listOf(
                    "Keep your elbows locked straight.",
                    "Let your chest sink slightly by squeezing your shoulder blades together.",
                    "Push the floor away to spread your shoulder blades apart and round your upper back slightly.",
                    "Repeat with small, smooth reps.",
                ),
                cues = listOf(
                    "Arms stay straight",
                    "Push the floor away",
                    "Move only at the shoulder blades",
                ),
                commonMistakes = listOf(
                    "Bending the elbows",
                    "Moving the hips instead of the shoulder blades",
                    "Rushing with tiny jerky reps",
                ),
            ),
        )
        put(
            "close-grip bench press",
            ExerciseGuide(
                setup = "Lie on the bench as for a bench press but take a grip about shoulder-width apart.",
                steps = listOf(
                    "Unrack the bar over your chest with shoulder blades pinched.",
                    "Lower it to your lower chest with elbows tucked close to your sides.",
                    "Press the bar straight back up until your arms are locked out.",
                    "Keep your wrists straight over your forearms.",
                ),
                cues = listOf(
                    "Elbows tucked",
                    "Drive up through the triceps",
                    "Wrists stacked",
                ),
                commonMistakes = listOf(
                    "Gripping so narrow the wrists hurt",
                    "Flaring the elbows",
                    "Bouncing the bar off the chest",
                ),
            ),
        )
        put(
            "push press",
            ExerciseGuide(
                setup = "Stand with the bar at your collarbone, hands just outside your shoulders, feet hip-width and stomach braced.",
                steps = listOf(
                    "Dip a few inches by bending your knees, keeping your torso upright.",
                    "Drive up explosively with your legs.",
                    "Use that momentum to press the bar overhead to straight arms.",
                    "Lower the bar back to your collarbone and reset.",
                ),
                cues = listOf(
                    "Dip straight down, drive straight up",
                    "Legs start it, arms finish it",
                    "Lock out with head through",
                ),
                commonMistakes = listOf(
                    "Leaning back instead of dipping straight down",
                    "Dipping too deep",
                    "Pressing before the legs finish driving",
                ),
            ),
        )
        put(
            "dumbbell bench press",
            ExerciseGuide(
                setup = "Sit on a bench with dumbbells on your thighs, then lie back and kick them up to chest height with your shoulder blades pinched.",
                steps = listOf(
                    "Press the dumbbells up until your arms are straight over your chest.",
                    "Lower them slowly until your elbows are slightly below the bench level.",
                    "Keep your wrists straight and forearms vertical.",
                    "Press back up to start the next rep.",
                ),
                cues = listOf(
                    "Shoulder blades pinched",
                    "Forearms vertical",
                    "Control the lowering",
                ),
                commonMistakes = listOf(
                    "Letting the dumbbells drift out to the sides",
                    "Dropping too deep and straining the shoulders",
                    "Clanking the dumbbells together at the top",
                ),
            ),
        )
        put(
            "incline dumbbell press",
            ExerciseGuide(
                setup = "Set the bench to a low to moderate incline and sit with dumbbells on your thighs, then lie back and bring them to chest height.",
                steps = listOf(
                    "Press the dumbbells up until your arms are straight over your upper chest.",
                    "Lower them slowly to the sides of your upper chest.",
                    "Keep your shoulder blades pinched and back on the pad.",
                    "Press back up for the next rep.",
                ),
                cues = listOf(
                    "Chest up, shoulder blades back",
                    "Elbows about 45 degrees from your torso",
                    "Slow lowering",
                ),
                commonMistakes = listOf(
                    "Using so steep an angle it becomes a shoulder press",
                    "Flaring the elbows wide",
                    "Arching the lower back off the bench",
                ),
            ),
        )
        put(
            "dumbbell shoulder press",
            ExerciseGuide(
                setup = "Sit on a bench with back support (or stand), dumbbells at shoulder height with palms facing forward.",
                steps = listOf(
                    "Brace your stomach and keep your ribs down.",
                    "Press the dumbbells up until your arms are straight above your shoulders.",
                    "Lower them under control to about ear level.",
                    "Keep your wrists over your elbows.",
                ),
                cues = listOf(
                    "Ribs down",
                    "Elbows slightly in front of the body",
                    "Full lockout overhead",
                ),
                commonMistakes = listOf(
                    "Arching the lower back",
                    "Pressing the weights forward instead of up",
                    "Using a short half range",
                ),
            ),
        )
        put(
            "arnold press",
            ExerciseGuide(
                setup = "Sit on a bench with back support, holding dumbbells at chest height with palms facing you.",
                steps = listOf(
                    "Press the dumbbells up while rotating your palms to face forward.",
                    "Finish with your arms straight overhead.",
                    "Reverse the rotation as you lower them.",
                    "End each rep with palms facing you again.",
                ),
                cues = listOf(
                    "Rotate as you press",
                    "Smooth, even tempo",
                    "Ribs down",
                ),
                commonMistakes = listOf(
                    "Jerking the weights up",
                    "Arching the back",
                    "Using too much weight to keep the rotation smooth",
                ),
            ),
        )
        put(
            "lateral raise",
            ExerciseGuide(
                setup = "Stand holding dumbbells at your sides, with a slight bend in your elbows and your chest tall.",
                steps = listOf(
                    "Raise your arms out to the sides until your hands reach shoulder height.",
                    "Lead with your elbows, not your hands.",
                    "Pause briefly at the top.",
                    "Lower slowly back to your sides.",
                ),
                cues = listOf(
                    "Elbows lead",
                    "Slight lean forward, little finger up",
                    "Slow lowering",
                ),
                commonMistakes = listOf(
                    "Swinging the body to throw the weights up",
                    "Lifting past shoulder height and shrugging",
                    "Using weights so heavy the form falls apart",
                ),
            ),
        )
        put(
            "front raise",
            ExerciseGuide(
                setup = "Stand tall holding dumbbells in front of your thighs, palms facing your body.",
                steps = listOf(
                    "Raise one or both arms straight in front of you to shoulder height.",
                    "Keep a slight bend in your elbows.",
                    "Pause briefly at the top.",
                    "Lower slowly back to your thighs.",
                ),
                cues = listOf(
                    "Ribs down, no swinging",
                    "Stop at shoulder height",
                    "Slow lowering",
                ),
                commonMistakes = listOf(
                    "Leaning back to swing the weights up",
                    "Raising above shoulder height",
                    "Letting the weights drop on the way down",
                ),
            ),
        )
        put(
            "dumbbell fly",
            ExerciseGuide(
                setup = "Lie on a bench holding dumbbells over your chest with palms facing each other and a soft bend in your elbows.",
                steps = listOf(
                    "Open your arms out wide in an arc, keeping the elbow bend fixed.",
                    "Lower until you feel a comfortable stretch across your chest.",
                    "Squeeze your chest to bring the dumbbells back together over your chest.",
                    "Keep your shoulder blades pinched on the bench.",
                ),
                cues = listOf(
                    "Hug a big tree",
                    "Fixed elbow bend",
                    "Stretch, then squeeze",
                ),
                commonMistakes = listOf(
                    "Straightening and bending the elbows so it becomes a press",
                    "Dropping too deep and straining the shoulders",
                    "Using weights that are too heavy",
                ),
            ),
        )
        put(
            "triceps kickback",
            ExerciseGuide(
                setup = "Hold a dumbbell, hinge forward at the hips with a flat back and tuck your upper arm against your side.",
                steps = listOf(
                    "Start with your elbow bent at 90 degrees and your upper arm parallel to the floor.",
                    "Straighten your elbow to extend the dumbbell behind you.",
                    "Squeeze your triceps for a second.",
                    "Lower slowly back to 90 degrees.",
                ),
                cues = listOf(
                    "Upper arm stays still",
                    "Squeeze at lockout",
                    "Slow lowering",
                ),
                commonMistakes = listOf(
                    "Swinging the whole arm",
                    "Letting the upper arm drop",
                    "Using a weight so heavy you cannot lock out",
                ),
            ),
        )
        put(
            "triceps pushdown",
            ExerciseGuide(
                setup = "Stand facing a high cable with a rope or bar, elbows tucked at your sides and a slight forward lean.",
                steps = listOf(
                    "Start with your elbows bent at about 90 degrees.",
                    "Push the handle down until your arms are fully straight.",
                    "Squeeze your triceps for a moment.",
                    "Return slowly until your forearms are just past 90 degrees.",
                ),
                cues = listOf(
                    "Elbows pinned to your sides",
                    "Push down, not out",
                    "Slow return",
                ),
                commonMistakes = listOf(
                    "Letting the elbows drift forward",
                    "Leaning over the handle to use body weight",
                    "Using momentum to bounce the weight",
                ),
            ),
        )
        put(
            "overhead cable extension",
            ExerciseGuide(
                setup = "Face away from a low cable with a rope, hold it behind your head with elbows pointing up and one foot forward.",
                steps = listOf(
                    "Keep your elbows close to your head.",
                    "Straighten your arms forward and up to extend the rope.",
                    "Squeeze your triceps at the top.",
                    "Bend your elbows slowly to return behind your head for a good stretch.",
                ),
                cues = listOf(
                    "Elbows stay pointing up",
                    "Full stretch behind the head",
                    "Ribs down",
                ),
                commonMistakes = listOf(
                    "Flaring the elbows wide",
                    "Arching the lower back",
                    "Cutting the stretch short",
                ),
            ),
        )
        put(
            "cable fly",
            ExerciseGuide(
                setup = "Set the pulleys at about chest height, stand in the middle with a handle in each hand and one foot forward.",
                steps = listOf(
                    "Step forward with arms open to the sides and a soft bend in your elbows.",
                    "Bring your hands together in front of your chest in a wide arc.",
                    "Squeeze your chest for a second.",
                    "Open your arms slowly until you feel a stretch across your chest.",
                ),
                cues = listOf(
                    "Hug a big tree",
                    "Fixed elbow bend",
                    "Squeeze, then slow return",
                ),
                commonMistakes = listOf(
                    "Pressing the handles instead of arcing them",
                    "Letting the shoulders roll forward",
                    "Using too much weight",
                ),
            ),
        )
        put(
            "cable lateral raise",
            ExerciseGuide(
                setup = "Stand sideways to a low cable, holding the handle with the far hand and a slight bend in your elbow.",
                steps = listOf(
                    "Raise your arm out to the side until it reaches shoulder height.",
                    "Lead with your elbow.",
                    "Pause briefly at the top.",
                    "Lower slowly back across the front of your body.",
                ),
                cues = listOf(
                    "Elbow leads",
                    "Stay tall, no leaning",
                    "Slow lowering",
                ),
                commonMistakes = listOf(
                    "Swinging the body",
                    "Lifting above shoulder height",
                    "Shrugging the shoulder up",
                ),
            ),
        )
        put(
            "pec deck",
            ExerciseGuide(
                setup = "Sit on the machine with your back flat, adjust the seat so the handles are at chest height and place your arms on the pads.",
                steps = listOf(
                    "Keep your shoulder blades back and chest up.",
                    "Bring the pads together in front of you with a slight bend in your elbows.",
                    "Squeeze your chest for a second.",
                    "Open slowly until you feel a stretch, stopping before the shoulders roll forward.",
                ),
                cues = listOf(
                    "Chest up, shoulders back",
                    "Squeeze together",
                    "Slow return",
                ),
                commonMistakes = listOf(
                    "Rounding the shoulders forward",
                    "Letting the weight stack slam",
                    "Opening so far the shoulders strain",
                ),
            ),
        )
        put(
            "machine chest press",
            ExerciseGuide(
                setup = "Sit with your back flat against the pad and adjust the seat so the handles are at mid-chest height.",
                steps = listOf(
                    "Pinch your shoulder blades back against the pad.",
                    "Press the handles forward until your arms are almost straight.",
                    "Lower slowly until the handles are near your chest.",
                    "Keep your wrists straight.",
                ),
                cues = listOf(
                    "Shoulder blades pinned back",
                    "Press through the whole hand",
                    "Slow return",
                ),
                commonMistakes = listOf(
                    "Letting the shoulders roll forward",
                    "Locking the elbows hard at the end",
                    "Letting the weight slam back",
                ),
            ),
        )
        put(
            "machine shoulder press",
            ExerciseGuide(
                setup = "Sit with your back against the pad and adjust the seat so the handles start at shoulder height.",
                steps = listOf(
                    "Brace your stomach and keep your back on the pad.",
                    "Press the handles up until your arms are almost straight.",
                    "Lower slowly back to shoulder height.",
                    "Keep your wrists over your elbows.",
                ),
                cues = listOf(
                    "Ribs down",
                    "Press straight up",
                    "Slow lowering",
                ),
                commonMistakes = listOf(
                    "Arching the lower back off the pad",
                    "Shrugging the shoulders to the ears",
                    "Using a half range of motion",
                ),
            ),
        )
        put(
            "smith machine bench press",
            ExerciseGuide(
                setup = "Slide a bench under the bar so it lines up with your mid-chest, then lie back with your feet planted and shoulder blades pinched.",
                steps = listOf(
                    "Grip the bar a little wider than your shoulders and twist it to unrack.",
                    "Lower the bar to your mid-chest.",
                    "Press it back up to straight arms.",
                    "Re-rack by twisting the bar back onto the hooks.",
                ),
                cues = listOf(
                    "Shoulder blades pinned back",
                    "Bar over mid-chest",
                    "Control the lowering",
                ),
                commonMistakes = listOf(
                    "Placing the bench too far forward or back",
                    "Bouncing the bar off the chest",
                    "Lifting the hips off the bench",
                ),
            ),
        )
        put(
            "smith machine overhead press",
            ExerciseGuide(
                setup = "Sit with a bench under the bar so it starts at your chin, back upright, feet flat.",
                steps = listOf(
                    "Grip the bar just wider than your shoulders and twist to unrack.",
                    "Press the bar straight up until your arms are straight.",
                    "Lower the bar under control to your chin.",
                    "Re-rack when you finish.",
                ),
                cues = listOf(
                    "Ribs down",
                    "Bar travels straight up",
                    "Slow lowering",
                ),
                commonMistakes = listOf(
                    "Setting the bench so the bar drifts in front of the face",
                    "Arching the lower back",
                    "Letting the bar drop on the way down",
                ),
            ),
        )
        put(
            "assisted dip",
            ExerciseGuide(
                setup = "Set the assistance weight on the machine (more assistance is easier), kneel on the pad and grip the handles with arms straight.",
                steps = listOf(
                    "Lean your torso slightly forward.",
                    "Lower until your upper arms are about parallel to the floor.",
                    "Press back up until your arms are straight.",
                    "Reduce the assistance over time as you get stronger.",
                ),
                cues = listOf(
                    "Chest forward",
                    "Shoulders down",
                    "Smooth control",
                ),
                commonMistakes = listOf(
                    "Using so much assistance the movement is too easy",
                    "Dropping too deep and pinching the shoulders",
                    "Bouncing out of the bottom",
                ),
            ),
        )
        put(
            "incline push-up",
            ExerciseGuide(
                setup = "Place your hands on a bench or sturdy surface a little wider than your shoulders and step back so your body forms a straight line.",
                steps = listOf(
                    "Brace your stomach and squeeze your glutes.",
                    "Lower your chest to the edge of the surface with elbows about 45 degrees from your sides.",
                    "Press away until your arms are straight.",
                    "Use a lower surface as you get stronger.",
                ),
                cues = listOf(
                    "Straight line from head to heels",
                    "Elbows tucked",
                    "Chest touches the surface",
                ),
                commonMistakes = listOf(
                    "Hips sagging",
                    "Flaring the elbows wide",
                    "Stopping short of the bottom",
                ),
            ),
        )
        put(
            "one-arm negative push-up",
            ExerciseGuide(
                setup = "Start at the top of a push-up with one hand under your chest, other arm behind your back, and feet wide.",
                steps = listOf(
                    "Brace your stomach and keep your hips level.",
                    "Lower yourself slowly on one arm over three to five seconds.",
                    "Touch down with your chest and rest.",
                    "Return to the top with two hands, then repeat.",
                ),
                cues = listOf(
                    "Slow, steady descent",
                    "Hips square to the floor",
                    "Feet wide for balance",
                ),
                commonMistakes = listOf(
                    "Dropping fast instead of lowering slowly",
                    "Twisting the hips",
                    "Starting with feet too close together",
                ),
            ),
        )
        put(
            "one-arm push-up",
            ExerciseGuide(
                setup = "Set up in a push-up position with your feet wide, one hand under your chest and the other behind your back.",
                steps = listOf(
                    "Brace your stomach and keep your hips square.",
                    "Lower your chest toward the floor under control.",
                    "Press back up until your arm is straight.",
                    "Switch arms for the next set.",
                ),
                cues = listOf(
                    "Feet wide, hips level",
                    "Elbow tucked close",
                    "Full control top to bottom",
                ),
                commonMistakes = listOf(
                    "Twisting the torso to cheat the rep",
                    "Letting the hips sag",
                    "Rushing before building up with negatives or incline",
                ),
            ),
        )
    }

    // ---- Push, part two ----
    private fun MutableMap<String, ExerciseGuide>.push2() {
        put(
            "bench dip",
            ExerciseGuide(
                setup = "Sit on the edge of a bench, hands beside your hips gripping the edge, legs out in front, then slide your hips off the bench.",
                steps = listOf(
                    "Straighten your arms to hold your body up.",
                    "Bend your elbows straight back and lower until your elbows reach about 90 degrees, no deeper.",
                    "Press through your palms until your arms are straight again.",
                    "Keep your hips close to the bench the whole rep.",
                ),
                cues = listOf(
                    "Elbows point straight back",
                    "Shoulders stay down, away from your ears",
                    "Lower only as far as your shoulders feel comfortable",
                ),
                commonMistakes = listOf(
                    "Letting the hips drift far from the bench",
                    "Dropping so low that the front of the shoulder pinches",
                    "Flaring the elbows out to the sides",
                ),
            ),
        )
        put(
            "parallel bar support hold",
            ExerciseGuide(
                setup = "Grip two parallel bars, jump or step up, and straighten your arms so your body hangs between them.",
                steps = listOf(
                    "Lock your elbows and press your body up out of your shoulders.",
                    "Push the bars down so your shoulders sit away from your ears.",
                    "Keep your legs together and slightly in front of you.",
                    "Hold still for the set time, then step or lower down under control.",
                    "Progress by adding seconds, then by leaning forward or lifting the legs.",
                ),
                cues = listOf(
                    "Tall and locked out",
                    "Push the bars away",
                    "Ribs down, body still",
                ),
                commonMistakes = listOf(
                    "Shrugging the shoulders up to the ears",
                    "Bending the elbows slightly to rest",
                    "Swinging instead of holding still",
                ),
            ),
        )
        put(
            "parallel bar dip",
            ExerciseGuide(
                setup = "Support yourself on parallel bars with straight arms, body slightly leaning forward.",
                steps = listOf(
                    "Bend your elbows and lower your body slowly until your upper arms are about parallel to the floor.",
                    "Keep your chest leaning slightly forward and your elbows close to your sides.",
                    "Pause briefly at the bottom without dropping into it.",
                    "Press back up until your arms are straight.",
                    "Start with a lower-body assist or slow lowering reps if you cannot yet do full reps.",
                ),
                cues = listOf(
                    "Lean forward to feel the chest, stay upright to feel the triceps",
                    "Control the way down",
                    "Shoulders away from the ears at the top",
                ),
                commonMistakes = listOf(
                    "Dropping so fast you bounce out of the bottom",
                    "Going deeper than the shoulders can handle",
                    "Swinging the legs to cheat the press",
                ),
            ),
        )
        put(
            "weighted dip",
            ExerciseGuide(
                setup = "Support yourself on parallel bars with a belt, vest or held weight added, arms straight.",
                steps = listOf(
                    "Set the added load so you can control every rep.",
                    "Lower slowly until your upper arms are about parallel to the floor.",
                    "Keep the weight hanging still, not swinging.",
                    "Press up to straight arms.",
                    "Add small amounts of weight only after you hit your target reps cleanly.",
                ),
                cues = listOf(
                    "Slow down, fast up",
                    "Elbows stay close",
                    "Full lockout each rep",
                ),
                commonMistakes = listOf(
                    "Adding weight before bodyweight dips are solid",
                    "Cutting depth as the set gets hard",
                    "Letting the weight swing",
                ),
            ),
        )
        put(
            "wall handstand",
            ExerciseGuide(
                setup = "Start in a push-up position with your feet against the base of a wall.",
                steps = listOf(
                    "Walk your feet up the wall while you walk your hands back toward it.",
                    "Stop with your hands about a hand-length from the wall and your chest facing it.",
                    "Push the floor away so your shoulders stay open and your ears sit between your arms.",
                    "Squeeze your legs, glutes and stomach to keep the body in a straight line, toes lightly on the wall.",
                    "Hold for the set time, then walk your hands out and your feet down.",
                ),
                cues = listOf(
                    "Chest to the wall, belly button in",
                    "Push tall through the shoulders",
                    "Look at the floor between your hands",
                ),
                commonMistakes = listOf(
                    "Turning around and kicking up back-to-wall, which lets the back arch",
                    "Sinking into the shoulders instead of pushing tall",
                    "Hands so far from the wall that the body bends",
                ),
            ),
        )
        put(
            "crow pose",
            ExerciseGuide(
                setup = "Squat low with your hands flat on the floor shoulder-width apart, knees wide.",
                steps = listOf(
                    "Place your knees high on the backs of your upper arms, near the armpits.",
                    "Lean your weight forward until your shoulders move past your wrists.",
                    "Lift one foot, then the other, as you keep leaning forward.",
                    "Round your upper back and straighten your arms as far as you can.",
                    "Lower under control. Progress by lengthening the hold with straighter arms.",
                ),
                cues = listOf(
                    "Knees high on the arms",
                    "Look a little ahead of your hands",
                    "Press the floor away",
                ),
                commonMistakes = listOf(
                    "Resting the knees on bent elbows, which makes it a frog stand",
                    "Looking straight down and tipping onto the face",
                    "Placing the hands too close together",
                ),
            ),
        )
        put(
            "pike press",
            ExerciseGuide(
                setup = "Start in a pike with hands and feet on the floor, hips high and your body shaped like an upside-down V.",
                steps = listOf(
                    "Walk your feet toward your hands so your hips stack over your shoulders.",
                    "Bend your elbows and lower the top of your head toward the floor between your hands.",
                    "Keep your elbows angled slightly forward, not flared wide.",
                    "Press back up until your arms are straight.",
                    "Raise your feet on a box to make it harder.",
                ),
                cues = listOf(
                    "Hips high, head travels between the hands",
                    "Press the floor away at the top",
                    "Smooth and controlled",
                ),
                commonMistakes = listOf(
                    "Staying too flat so it becomes a normal push-up",
                    "Flaring the elbows",
                    "Cutting the lowering phase short",
                ),
            ),
        )
        put(
            "wall hspu",
            ExerciseGuide(
                setup = "Kick up to a wall handstand with your chest facing the wall and hands about a hand-length from it.",
                steps = listOf(
                    "Hold a solid wall handstand to start.",
                    "Bend your elbows and lower your head slowly toward the floor.",
                    "Touch the floor lightly or a small pad with your head.",
                    "Press up until your arms are fully straight.",
                    "Use a pad under the head or a shorter range to build up.",
                ),
                cues = listOf(
                    "Control the way down",
                    "Stay tight from hands to toes",
                    "Lock out at the top",
                ),
                commonMistakes = listOf(
                    "Crashing down instead of lowering under control",
                    "Letting the lower back arch hard",
                    "Kicking with the legs to get up the rep",
                ),
            ),
        )
        put(
            "crow → handstand",
            ExerciseGuide(
                setup = "Start in a crow pose on a soft mat, with a wall nearby if you are learning.",
                steps = listOf(
                    "Lean forward in the crow until the weight sits over your hands.",
                    "Press the floor away and start to straighten your arms.",
                    "Lift your hips up and over your shoulders as your legs rise.",
                    "Extend your legs up to a stacked handstand line.",
                    "Hold a moment, then come down under control, or step down one leg at a time.",
                ),
                cues = listOf(
                    "Push the floor away",
                    "Hips drive up over the shoulders",
                    "Move slowly",
                ),
                commonMistakes = listOf(
                    "Rushing the lift and throwing the legs",
                    "Not leaning far enough forward before starting",
                    "Bending the arms so the shoulders collapse",
                ),
            ),
        )
        put(
            "freestanding handstand",
            ExerciseGuide(
                setup = "Kick up away from any wall in a clear space, hands shoulder-width apart, fingers spread.",
                steps = listOf(
                    "Kick up one leg at a time and bring the legs together.",
                    "Push tall through your shoulders and squeeze your stomach and glutes.",
                    "Balance by pressing through the fingertips or the heels of the hands.",
                    "Look at the floor just ahead of your hands.",
                    "Hold, then come down by cartwheeling out or stepping a leg down.",
                ),
                cues = listOf(
                    "Stacked line from hands to toes",
                    "Fingertips fix a fall forward, heels of the hands fix a fall back",
                    "Breathe slowly",
                ),
                commonMistakes = listOf(
                    "Letting the body bend into a banana shape",
                    "Sinking out of the shoulders as you tire",
                    "Practicing while exhausted, which makes balance sloppy",
                ),
            ),
        )
        put(
            "handstand walk",
            ExerciseGuide(
                setup = "Hold a freestanding handstand comfortably for several seconds first.",
                steps = listOf(
                    "Shift your weight slightly onto one hand.",
                    "Lift the other hand and place it a short distance forward.",
                    "Keep your body straight and legs close together.",
                    "Take small steps and keep your eyes on the floor ahead.",
                    "End by stepping down a leg or cartwheeling out.",
                ),
                cues = listOf(
                    "Small steps",
                    "Stay tall and tight",
                    "Keep your weight just a bit in front of your hands",
                ),
                commonMistakes = listOf(
                    "Taking big, lunging steps",
                    "Letting the legs split apart",
                    "Bending the arms to fix the balance",
                ),
            ),
        )
        put(
            "90-degree push-up",
            ExerciseGuide(
                setup = "Start in a handstand against a wall or on parallel bars with a spotter or a cushion nearby.",
                steps = listOf(
                    "Press into a handstand with your body straight.",
                    "Shift your weight forward so your shoulders lean past your hands.",
                    "Bend your elbows and lower your torso until it is horizontal over your hands.",
                    "Press back up to the handstand.",
                    "Build up with partial range and pause practice first.",
                ),
                cues = listOf(
                    "Lean forward as you lower",
                    "Elbows tucked",
                    "Control the whole rep",
                ),
                commonMistakes = listOf(
                    "Trying it before handstand push-ups are strong",
                    "Flaring the elbows wide",
                    "Dropping fast so the arms cannot stop the descent",
                ),
            ),
        )
        put(
            "one-arm handstand",
            ExerciseGuide(
                setup = "Be solid in a freestanding handstand and try this on a soft surface with space around you.",
                steps = listOf(
                    "Kick into a handstand with your feet slightly apart for balance.",
                    "Shift your weight over one hand while the free arm lifts off the floor.",
                    "Spread your legs and tilt your hips to keep your weight over the supporting hand.",
                    "Hold as long as your balance allows.",
                    "Return the free hand to the floor under control.",
                ),
                cues = listOf(
                    "Weight over the one hand",
                    "Fingers grip and press",
                    "Keep attempts short and fresh",
                ),
                commonMistakes = listOf(
                    "Trying it before two-arm handstands are steady",
                    "Letting the supporting shoulder collapse",
                    "Lifting the free hand too fast",
                ),
            ),
        )
        put(
            "straddle press to handstand",
            ExerciseGuide(
                setup = "Stand with your legs wide, fold forward and place your hands on the floor between your feet.",
                steps = listOf(
                    "Lean your shoulders forward over your hands so your hips lift above them.",
                    "Keep your legs straight and wide and press your hands into the floor.",
                    "Lift your legs up and in toward the center line by using your stomach and shoulders.",
                    "Bring the legs together overhead in a handstand.",
                    "Lower back the same way, slowly.",
                ),
                cues = listOf(
                    "Shoulders lean forward first",
                    "Hips over the hands",
                    "Smooth, not a jump",
                ),
                commonMistakes = listOf(
                    "Jumping with the legs instead of pressing",
                    "Not leaning forward far enough",
                    "Bending the arms during the lift",
                ),
            ),
        )
        put(
            "frog stand",
            ExerciseGuide(
                setup = "Squat low with your hands flat on the floor shoulder-width apart, fingers spread, elbows bent.",
                steps = listOf(
                    "Bend your elbows so they form a shelf.",
                    "Rest your knees on the outsides of your elbows.",
                    "Lean forward until your weight shifts onto your hands.",
                    "Lift your feet off the floor and bring them together, arms staying bent.",
                    "Hold for the set time and lower under control.",
                ),
                cues = listOf(
                    "Knees on the elbow shelf, not up by the armpits",
                    "Lean forward over the hands",
                    "Spread your fingers and press",
                ),
                commonMistakes = listOf(
                    "Straightening the arms, which turns it into a crow",
                    "Looking straight down",
                    "Leaning back instead of holding the weight over the hands",
                ),
            ),
        )
        put(
            "tuck planche",
            ExerciseGuide(
                setup = "Place your hands flat on the floor (or on parallettes), fingers turned slightly out, and lean forward over them.",
                steps = listOf(
                    "Lean your shoulders far past your hands.",
                    "Lock your elbows and push the floor away.",
                    "Tuck your pelvis under and squeeze your glutes and stomach.",
                    "Lift your feet off the floor into the position: knees tucked tight into your chest.",
                    "Hold for the set time, then lower under control. Hold a lean first, then lift your feet one at a time.",
                ),
                cues = listOf(
                    "Lean forward and push the floor away",
                    "Round the upper back, pelvis tucked",
                    "Arms stay straight",
                ),
                commonMistakes = listOf(
                    "Bending the elbows to make it easier",
                    "Hips sagging or piking up",
                    "Not leaning far enough forward",
                ),
            ),
        )
        put(
            "advanced tuck planche",
            ExerciseGuide(
                setup = "Place your hands flat on the floor (or on parallettes), fingers turned slightly out, and lean forward over them.",
                steps = listOf(
                    "Lean your shoulders far past your hands.",
                    "Lock your elbows and push the floor away.",
                    "Tuck your pelvis under and squeeze your glutes and stomach.",
                    "Lift your feet off the floor into the position: back flat and hips level with the shoulders, knees still bent.",
                    "Hold for the set time, then lower under control. Open the hip angle a little at a time once your hold is steady.",
                ),
                cues = listOf(
                    "Lean forward and push the floor away",
                    "Round the upper back, pelvis tucked",
                    "Arms stay straight",
                ),
                commonMistakes = listOf(
                    "Bending the elbows to make it easier",
                    "Hips sagging or piking up",
                    "Not leaning far enough forward",
                ),
            ),
        )
        put(
            "one-leg planche",
            ExerciseGuide(
                setup = "Place your hands flat on the floor (or on parallettes), fingers turned slightly out, and lean forward over them.",
                steps = listOf(
                    "Lean your shoulders far past your hands.",
                    "Lock your elbows and push the floor away.",
                    "Tuck your pelvis under and squeeze your glutes and stomach.",
                    "Lift your feet off the floor into the position: one leg tucked and the other leg extended straight back.",
                    "Hold for the set time, then lower under control. Extend the free leg further as your hold improves.",
                ),
                cues = listOf(
                    "Lean forward and push the floor away",
                    "Round the upper back, pelvis tucked",
                    "Arms stay straight",
                ),
                commonMistakes = listOf(
                    "Bending the elbows to make it easier",
                    "Hips sagging or piking up",
                    "Not leaning far enough forward",
                ),
            ),
        )
        put(
            "straddle planche",
            ExerciseGuide(
                setup = "Place your hands flat on the floor (or on parallettes), fingers turned slightly out, and lean forward over them.",
                steps = listOf(
                    "Lean your shoulders far past your hands.",
                    "Lock your elbows and push the floor away.",
                    "Tuck your pelvis under and squeeze your glutes and stomach.",
                    "Lift your feet off the floor into the position: legs spread wide and straight behind you.",
                    "Hold for the set time, then lower under control. Narrow the straddle gradually as you get stronger.",
                ),
                cues = listOf(
                    "Lean forward and push the floor away",
                    "Round the upper back, pelvis tucked",
                    "Arms stay straight",
                ),
                commonMistakes = listOf(
                    "Bending the elbows to make it easier",
                    "Hips sagging or piking up",
                    "Not leaning far enough forward",
                ),
            ),
        )
        put(
            "full planche",
            ExerciseGuide(
                setup = "Place your hands flat on the floor (or on parallettes), fingers turned slightly out, and lean forward over them.",
                steps = listOf(
                    "Lean your shoulders far past your hands.",
                    "Lock your elbows and push the floor away.",
                    "Tuck your pelvis under and squeeze your glutes and stomach.",
                    "Lift your feet off the floor into the position: body straight and legs together, parallel to the floor.",
                    "Hold for the set time, then lower under control. Treat it as a long-term goal and build with the earlier planche steps first.",
                ),
                cues = listOf(
                    "Lean forward and push the floor away",
                    "Round the upper back, pelvis tucked",
                    "Arms stay straight",
                ),
                commonMistakes = listOf(
                    "Bending the elbows to make it easier",
                    "Hips sagging or piking up",
                    "Not leaning far enough forward",
                ),
            ),
        )
        put(
            "planche push-up",
            ExerciseGuide(
                setup = "Get into a planche lean with your shoulders well past your hands.",
                steps = listOf(
                    "Lift into your best planche lean or tuck.",
                    "Bend your elbows and lower your chest toward the floor while keeping the body line.",
                    "Press back up to straight arms.",
                    "Keep the lean the entire rep.",
                    "Begin with the feet on the floor and lean heavily, then progress to the tuck.",
                ),
                cues = listOf(
                    "Stay leaning forward",
                    "Elbows tucked close",
                    "Slow down, push up",
                ),
                commonMistakes = listOf(
                    "Losing the lean and doing a normal push-up",
                    "Letting the hips sag",
                    "Rushing through the reps",
                ),
            ),
        )
        put(
            "ring support hold",
            ExerciseGuide(
                setup = "Hang two rings at about hip height or higher, grip them, and press up so your arms are straight.",
                steps = listOf(
                    "Hold the rings and jump or step up to a straight-arm support.",
                    "Turn your hands out a little so the palms face forward.",
                    "Lock your elbows and push the rings down and slightly apart.",
                    "Keep your body tight and legs together.",
                    "Hold for the set time, then lower. Add seconds before adding leg lifts or turned-out hands.",
                ),
                cues = listOf(
                    "Push the rings down and out",
                    "Elbows locked",
                    "Shoulders away from your ears",
                ),
                commonMistakes = listOf(
                    "Letting the rings drift wide or flop",
                    "Shrugging the shoulders up",
                    "Bending the elbows slightly",
                ),
            ),
        )
        put(
            "ring dip",
            ExerciseGuide(
                setup = "Hold the rings in a straight-arm support position, body leaning slightly forward.",
                steps = listOf(
                    "Start in a firm support with the rings close to your body.",
                    "Lower yourself slowly while keeping the rings near your ribs.",
                    "Go down until your upper arms are about parallel to the floor.",
                    "Press back up and finish with turned-out hands if you can.",
                    "Learn the movement with an assist or band before moving on to full reps.",
                ),
                cues = listOf(
                    "Rings close to the body",
                    "Control the ring wobble",
                    "Full lockout",
                ),
                commonMistakes = listOf(
                    "Letting the rings swing wide",
                    "Diving too low for the shoulders",
                    "Doing it before standard dips feel easy",
                ),
            ),
        )
        put(
            "iron cross",
            ExerciseGuide(
                setup = "Hang two rings from a high point and be strong in ring support and straight-arm basics before trying this.",
                steps = listOf(
                    "Get into a ring support position with a spotter or an assist band for safety.",
                    "Lower your body slowly while spreading your arms out to the sides.",
                    "Hold with straight arms out wide and the body tall.",
                    "Push the rings down toward your hips and keep your shoulders from rolling forward.",
                    "Lower yourself out of the hold under control.",
                ),
                cues = listOf(
                    "Straight arms, rings pushed down",
                    "Chest up",
                    "Stay tight head to toe",
                ),
                commonMistakes = listOf(
                    "Trying it with no assist or spotter",
                    "Bending the elbows",
                    "Rushing to the full hold instead of using a band-assisted build-up",
                ),
            ),
        )
        put(
            "volume bench press",
            ExerciseGuide(
                setup = "Lie on a bench with your eyes under the bar, feet flat on the floor, and grip the bar slightly wider than your shoulders.",
                steps = listOf(
                    "Pull your shoulder blades together and down and keep them there.",
                    "Unrack the bar and lower it under control to your mid-chest.",
                    "Touch lightly and press without bouncing.",
                    "Press the bar up and slightly back until your arms are straight.",
                    "Re-rack the bar when the set is done.",
                ),
                cues = listOf(
                    "Shoulder blades stay pinned",
                    "Controlled lowering",
                    "Leave a rep or two in reserve",
                ),
                commonMistakes = listOf(
                    "Bouncing the bar off the chest",
                    "Letting the wrists bend back",
                    "Lifting the hips off the bench",
                ),
            ),
        )
        put(
            "paused bench press",
            ExerciseGuide(
                setup = "Lie on a bench with your eyes under the bar, feet flat on the floor, and grip the bar slightly wider than your shoulders.",
                steps = listOf(
                    "Pull your shoulder blades together and down and keep them there.",
                    "Lower the bar under control to the chest.",
                    "Hold the bar still on your chest for a full second, with tension kept in the body.",
                    "Press up as soon as the pause ends.",
                    "Press the bar up and slightly back until your arms are straight.",
                    "Re-rack the bar when the set is done.",
                ),
                cues = listOf(
                    "Stay tight in the pause",
                    "No sinking",
                    "Drive the bar up smoothly",
                ),
                commonMistakes = listOf(
                    "Relaxing on the chest during the pause",
                    "Bouncing out of the pause",
                    "Using too much weight to hold the pause",
                ),
            ),
        )
        put(
            "heavy bench press",
            ExerciseGuide(
                setup = "Lie on a bench with your eyes under the bar, feet flat on the floor, and grip the bar slightly wider than your shoulders.",
                steps = listOf(
                    "Pull your shoulder blades together and down and keep them there.",
                    "Unrack with a spotter or safety arms in place.",
                    "Lower the bar steadily to your mid-chest.",
                    "Press with full effort and keep the bar path smooth.",
                    "Press the bar up and slightly back until your arms are straight.",
                    "Re-rack the bar when the set is done.",
                ),
                cues = listOf(
                    "Stay tight and drive your feet down",
                    "Smooth and strong",
                    "Use a spotter or safeties",
                ),
                commonMistakes = listOf(
                    "Training heavy without safety arms or a spotter",
                    "Flaring the elbows to the sides",
                    "Letting the hips lift",
                ),
            ),
        )
        put(
            "double-bodyweight bench press",
            ExerciseGuide(
                setup = "Lie on a bench with your eyes under the bar, feet flat on the floor, and grip the bar slightly wider than your shoulders.",
                steps = listOf(
                    "Pull your shoulder blades together and down and keep them there.",
                    "Warm up in several steps up to the heavy weight.",
                    "Lower the bar under control to your chest.",
                    "Press the bar up and slightly back until your arms are straight.",
                    "Re-rack the bar when the set is done.",
                ),
                cues = listOf(
                    "Brace hard",
                    "Control the lowering",
                    "Safeties on and a spotter ready",
                ),
                commonMistakes = listOf(
                    "Skipping the warm-up steps",
                    "Lifting without a spotter or safety arms",
                    "Bouncing the bar off the chest",
                ),
            ),
        )
        put(
            "volume overhead press",
            ExerciseGuide(
                setup = "Stand tall with the bar resting on your upper chest, grip just outside your shoulders, elbows slightly in front of the bar.",
                steps = listOf(
                    "Squeeze your glutes and stomach and keep your ribs down.",
                    "Press the bar straight up, moving your head back out of the way.",
                    "Keep the reps smooth and stop with a rep or two left.",
                    "Lock out with the bar over your mid-foot and your head through the arms.",
                    "Lower the bar under control back to the upper chest.",
                ),
                cues = listOf(
                    "Glutes tight",
                    "Head through at the top",
                    "Straight line bar path",
                ),
                commonMistakes = listOf(
                    "Leaning back and turning it into an incline press",
                    "Flaring the ribs",
                    "Letting the elbows drift behind the bar",
                ),
            ),
        )
        put(
            "bodyweight overhead press",
            ExerciseGuide(
                setup = "Stand tall with the bar resting on your upper chest, grip just outside your shoulders, elbows slightly in front of the bar.",
                steps = listOf(
                    "Squeeze your glutes and stomach and keep your ribs down.",
                    "Press the bar straight up, moving your head back out of the way.",
                    "Use a load equal to your body weight only when your form holds.",
                    "Lock out with the bar over your mid-foot and your head through the arms.",
                    "Lower the bar under control back to the upper chest.",
                ),
                cues = listOf(
                    "Glutes tight",
                    "Head through at the top",
                    "Straight line bar path",
                ),
                commonMistakes = listOf(
                    "Leaning back to grind out reps",
                    "Flaring the ribs",
                    "Cutting the lockout short",
                ),
            ),
        )
        put(
            "heavy overhead press",
            ExerciseGuide(
                setup = "Stand tall with the bar resting on your upper chest, grip just outside your shoulders, elbows slightly in front of the bar.",
                steps = listOf(
                    "Squeeze your glutes and stomach and keep your ribs down.",
                    "Press the bar straight up with full effort and move your head back out of the way.",
                    "Keep your stance firm and your body still.",
                    "Lock out with the bar over your mid-foot and your head through the arms.",
                    "Lower the bar under control back to the upper chest.",
                ),
                cues = listOf(
                    "Brace hard",
                    "Push the ceiling away",
                    "Use safety bars or a rack",
                ),
                commonMistakes = listOf(
                    "Leaning back to lift the weight",
                    "Pressing without bracing the core",
                    "Bending the knees to push the bar up",
                ),
            ),
        )
        put(
            "half-again overhead press",
            ExerciseGuide(
                setup = "Stand tall with the bar resting on your upper chest, grip just outside your shoulders, elbows slightly in front of the bar.",
                steps = listOf(
                    "Squeeze your glutes and stomach and keep your ribs down.",
                    "Warm up in several steps to the heavy weight.",
                    "Press the bar straight up and move your head back out of the way.",
                    "Lock out with the bar over your mid-foot and your head through the arms.",
                    "Lower the bar under control back to the upper chest.",
                ),
                cues = listOf(
                    "Brace hard",
                    "Push the ceiling away",
                    "Keep it smooth",
                ),
                commonMistakes = listOf(
                    "Skipping warm-up steps",
                    "Leaning back to complete the rep",
                    "Pressing without safety bars in the rack",
                ),
            ),
        )
    }

    // ---- Pull, part one ----
    private fun MutableMap<String, ExerciseGuide>.pull1() {
        put(
            "pull-up",
            ExerciseGuide(
                setup = "Hang from a bar with an overhand grip a little wider than your shoulders and your arms straight.",
                steps = listOf(
                    "Pull your shoulder blades down to start.",
                    "Drive your elbows down toward your ribs until your chin clears the bar.",
                    "Pause briefly at the top.",
                    "Lower under control until your arms are fully straight.",
                    "Rest, then repeat for the next rep.",
                ),
                cues = listOf(
                    "Elbows to your pockets",
                    "Chest up to the bar",
                    "Smooth lowering",
                ),
                commonMistakes = listOf(
                    "Kipping or swinging for momentum",
                    "Stopping short of straight arms at the bottom",
                    "Shrugging your shoulders up to your ears",
                ),
            ),
        )
        put(
            "chin-up",
            ExerciseGuide(
                setup = "Hang from a bar with an underhand grip about shoulder width and your arms straight.",
                steps = listOf(
                    "Pull your shoulder blades down to start.",
                    "Pull your elbows down and forward until your chin clears the bar.",
                    "Hold the top for a moment.",
                    "Lower slowly to straight arms.",
                    "Repeat without bouncing at the bottom.",
                ),
                cues = listOf(
                    "Chest to the bar",
                    "Squeeze your elbows into your sides",
                    "Control the way down",
                ),
                commonMistakes = listOf(
                    "Craning your neck to reach the bar",
                    "Half reps that skip the straight-arm bottom",
                    "Swinging your legs for momentum",
                ),
            ),
        )
        put(
            "archer pull-up",
            ExerciseGuide(
                setup = "Hang from a wide overhand grip with your arms straight and your body still.",
                steps = listOf(
                    "Pull up while leaning toward one hand.",
                    "Keep the opposite arm nearly straight so it acts as a guide.",
                    "Bring your chin level with the working hand.",
                    "Lower under control to the middle.",
                    "Switch sides on the next rep or finish all reps one side first.",
                ),
                cues = listOf(
                    "Pull to one hand",
                    "Straight arm stays long",
                    "Slow and level",
                ),
                commonMistakes = listOf(
                    "Bending both arms equally, which makes it a normal pull-up",
                    "Twisting your body instead of leaning",
                    "Dropping fast on the way down",
                ),
            ),
        )
        put(
            "inverted row",
            ExerciseGuide(
                setup = "Lie under a sturdy bar or rings set about waist height, grip it overhand, and hold your body straight with your heels on the floor.",
                steps = listOf(
                    "Brace your abs and squeeze your glutes so your body stays in one line.",
                    "Pull your chest toward the bar by driving your elbows back.",
                    "Squeeze your shoulder blades together at the top.",
                    "Lower until your arms are straight.",
                    "Walk your feet back under the bar to make it easier, or forward to make it harder.",
                ),
                cues = listOf(
                    "Chest to the bar",
                    "Body like a plank",
                    "Elbows back, not out",
                ),
                commonMistakes = listOf(
                    "Letting your hips sag",
                    "Shrugging toward your ears",
                    "Cutting the reps short at the bottom",
                ),
            ),
        )
        put(
            "door sheet row",
            ExerciseGuide(
                setup = "Loop a strong sheet or towel around a closed door's handles or posts, hold both ends, and lean back with your arms straight and your feet near the door.",
                steps = listOf(
                    "Check the door and the sheet are secure before you lean on them.",
                    "Keep your body straight and lean back with straight arms.",
                    "Pull your chest toward your hands by driving your elbows back.",
                    "Squeeze your shoulder blades together at the top.",
                    "Lower slowly until your arms are straight.",
                ),
                cues = listOf(
                    "Pull elbows to your back pockets",
                    "Body stays in one line",
                    "Slow lowering",
                ),
                commonMistakes = listOf(
                    "Using a door or sheet that could slip or tear",
                    "Letting your hips sag or bend",
                    "Yanking with your arms and not your back",
                ),
            ),
        )
        put(
            "active bar hang",
            ExerciseGuide(
                setup = "Grab a bar with an overhand grip about shoulder width and hang with your feet off the floor.",
                steps = listOf(
                    "Hang with your arms straight.",
                    "Pull your shoulder blades down and slightly back so your shoulders move away from your ears.",
                    "Keep your ribs down and your legs still.",
                    "Hold for the planned time, then step down.",
                    "Add time each session before making it harder.",
                ),
                cues = listOf(
                    "Shoulders away from ears",
                    "Grip hard, body still",
                    "Long arms",
                ),
                commonMistakes = listOf(
                    "Hanging loose in the shoulders for the whole hold",
                    "Swinging or kicking",
                    "Gripping so hard you hold your breath",
                ),
            ),
        )
        put(
            "wrist curl",
            ExerciseGuide(
                setup = "Sit with your forearms on your thighs, palms up, and hold a weight with your wrists hanging past your knees.",
                steps = listOf(
                    "Let the weight roll down toward your fingertips.",
                    "Close your fingers and curl the weight up using your wrists.",
                    "Pause with your wrists fully bent.",
                    "Lower slowly back to the stretched position.",
                    "Keep your forearms still against your legs.",
                ),
                cues = listOf(
                    "Only the wrists move",
                    "Full stretch, full squeeze",
                    "Slow lowering",
                ),
                commonMistakes = listOf(
                    "Lifting your forearms off your legs",
                    "Using too much weight and bouncing",
                    "Short reps",
                ),
            ),
        )
        put(
            "bicep curl",
            ExerciseGuide(
                setup = "Stand tall holding weights or a bar with your palms facing forward and your arms at your sides.",
                steps = listOf(
                    "Keep your elbows beside your ribs.",
                    "Curl the weight up until your forearms are vertical or a bit past.",
                    "Squeeze your biceps at the top.",
                    "Lower slowly until your arms are fully straight.",
                    "Repeat without swinging.",
                ),
                cues = listOf(
                    "Elbows pinned",
                    "Slow lowering",
                    "Full stretch at the bottom",
                ),
                commonMistakes = listOf(
                    "Swinging your torso",
                    "Letting your elbows drift forward",
                    "Cutting the bottom short",
                ),
            ),
        )
        put(
            "barbell row",
            ExerciseGuide(
                setup = "Stand holding a bar overhand just outside your knees, hinge forward at your hips with a flat back, and let your arms hang straight.",
                steps = listOf(
                    "Brace your abs and keep your back flat.",
                    "Pull the bar toward your lower ribs by driving your elbows back.",
                    "Squeeze your shoulder blades together at the top.",
                    "Lower under control until your arms are straight.",
                    "Hold the same torso angle for every rep.",
                ),
                cues = listOf(
                    "Elbows to the ceiling",
                    "Flat back, braced core",
                    "Bar close to your legs",
                ),
                commonMistakes = listOf(
                    "Standing up and jerking the weight",
                    "Rounding your lower back",
                    "Pulling with your arms instead of your back",
                ),
            ),
        )
        put(
            "dumbbell row",
            ExerciseGuide(
                setup = "Put one hand and the same-side knee on a bench, keep your back flat, and hold a dumbbell in your free hand with your arm hanging straight.",
                steps = listOf(
                    "Keep your hips and shoulders square to the floor.",
                    "Pull the dumbbell toward your hip by driving your elbow up and back.",
                    "Squeeze your shoulder blade toward your spine at the top.",
                    "Lower until your arm is fully straight.",
                    "Finish all reps, then swap sides.",
                ),
                cues = listOf(
                    "Elbow to the hip",
                    "No twisting",
                    "Let the shoulder blade stretch at the bottom",
                ),
                commonMistakes = listOf(
                    "Rotating your torso to heave the weight",
                    "Pulling toward your chest instead of your hip",
                    "Cutting the bottom short",
                ),
            ),
        )
        put(
            "lat pulldown",
            ExerciseGuide(
                setup = "Sit at the machine with your thighs under the pads, grip the bar overhand a bit wider than your shoulders, and lean back slightly.",
                steps = listOf(
                    "Pull your shoulder blades down first.",
                    "Pull the bar to your upper chest by driving your elbows down.",
                    "Squeeze your back at the bottom.",
                    "Let the bar rise slowly until your arms are straight and your shoulders stretch.",
                    "Repeat without swinging.",
                ),
                cues = listOf(
                    "Elbows to your pockets",
                    "Chest up to the bar",
                    "Slow return",
                ),
                commonMistakes = listOf(
                    "Leaning far back and pulling with momentum",
                    "Pulling the bar behind your neck",
                    "Letting the weight yank your shoulders up",
                ),
            ),
        )
        put(
            "face pull",
            ExerciseGuide(
                setup = "Set a rope on a cable at about head height, hold the ends with your thumbs back, and step away until your arms are straight.",
                steps = listOf(
                    "Stand tall with your ribs down and your knees soft.",
                    "Pull the rope toward your face, splitting the ends apart.",
                    "Finish with your elbows high and your hands beside your ears.",
                    "Pause and squeeze your upper back.",
                    "Return slowly to straight arms.",
                ),
                cues = listOf(
                    "Pull apart, not just back",
                    "Elbows high",
                    "Light and controlled",
                ),
                commonMistakes = listOf(
                    "Using so much weight you lean back",
                    "Letting your elbows drop low",
                    "Shrugging your shoulders up",
                ),
            ),
        )
        put(
            "prone y raise",
            ExerciseGuide(
                setup = "Lie face down on a bench or the floor with your arms stretched overhead in a Y shape and your thumbs up, holding light weights or nothing.",
                steps = listOf(
                    "Keep your forehead near the surface and your neck long.",
                    "Pull your shoulder blades down and back.",
                    "Lift your arms off the surface in the Y position.",
                    "Pause at the top, then lower slowly.",
                    "Keep the weight light and the reps clean.",
                ),
                cues = listOf(
                    "Thumbs to the ceiling",
                    "Shoulders away from your ears",
                    "Slow and smooth",
                ),
                commonMistakes = listOf(
                    "Using too much weight and arching your lower back",
                    "Shrugging your shoulders up",
                    "Swinging your arms up",
                ),
            ),
        )
        put(
            "pendlay row",
            ExerciseGuide(
                setup = "Stand with a loaded bar over your mid-foot, hinge until your back is flat and nearly parallel to the floor, and grip the bar overhand outside your knees.",
                steps = listOf(
                    "Brace your abs and keep your back flat.",
                    "Pull the bar explosively to your lower chest.",
                    "Lower it to rest fully on the floor each rep.",
                    "Reset your back and brace before the next pull.",
                    "Keep your torso angle the same every rep.",
                ),
                cues = listOf(
                    "Dead stop on the floor",
                    "Flat back",
                    "Drive elbows back",
                ),
                commonMistakes = listOf(
                    "Standing up as you pull",
                    "Letting your hips rise first",
                    "Bouncing the bar off the floor",
                ),
            ),
        )
        put(
            "t-bar row",
            ExerciseGuide(
                setup = "Straddle a landmine bar or T-bar, hinge forward with a flat back, and hold the handle with your arms straight.",
                steps = listOf(
                    "Brace your abs and keep your chest up.",
                    "Pull the handle toward your chest by driving your elbows back.",
                    "Squeeze your shoulder blades together at the top.",
                    "Lower until your arms are straight.",
                    "Keep your torso angle steady.",
                ),
                cues = listOf(
                    "Chest up",
                    "Elbows back",
                    "Controlled lowering",
                ),
                commonMistakes = listOf(
                    "Rounding your back",
                    "Using hip thrust and body English to lift",
                    "Cutting the stretch at the bottom",
                ),
            ),
        )
        put(
            "barbell shrug",
            ExerciseGuide(
                setup = "Stand holding a bar in front of your thighs with an overhand grip just outside your legs and your arms straight.",
                steps = listOf(
                    "Keep your arms straight and your chest tall.",
                    "Lift your shoulders straight up toward your ears.",
                    "Pause at the top for a second.",
                    "Lower slowly until your shoulders are fully down.",
                    "Repeat without bending your elbows.",
                ),
                cues = listOf(
                    "Shoulders to ears",
                    "Pause at the top",
                    "Straight arms",
                ),
                commonMistakes = listOf(
                    "Rolling your shoulders in circles",
                    "Bending your elbows to help",
                    "Using very short reps",
                ),
            ),
        )
        put(
            "reverse fly",
            ExerciseGuide(
                setup = "Hinge forward at your hips with a flat back, hold light dumbbells under your chest with a slight bend in your elbows.",
                steps = listOf(
                    "Keep your neck in line with your spine.",
                    "Raise your arms out to the sides in a wide arc.",
                    "Stop when your hands reach shoulder height and squeeze your upper back.",
                    "Lower slowly back to the start.",
                    "Keep your torso still the whole time.",
                ),
                cues = listOf(
                    "Lead with your elbows",
                    "Open like wings",
                    "Light and controlled",
                ),
                commonMistakes = listOf(
                    "Swinging your torso to lift",
                    "Shrugging your shoulders up",
                    "Using weights that are too heavy",
                ),
            ),
        )
        put(
            "hammer curl",
            ExerciseGuide(
                setup = "Stand holding dumbbells at your sides with your palms facing each other.",
                steps = listOf(
                    "Keep your elbows beside your ribs.",
                    "Curl the weights up with your palms still facing each other.",
                    "Squeeze at the top.",
                    "Lower slowly to straight arms.",
                    "Repeat without swinging.",
                ),
                cues = listOf(
                    "Thumbs up",
                    "Elbows pinned",
                    "Slow lowering",
                ),
                commonMistakes = listOf(
                    "Swinging your body",
                    "Letting your elbows drift forward",
                    "Rushing the lowering",
                ),
            ),
        )
        put(
            "reverse curl",
            ExerciseGuide(
                setup = "Stand holding a bar or dumbbells with an overhand grip, palms down, and your arms at your sides.",
                steps = listOf(
                    "Keep your elbows beside your ribs and your wrists straight.",
                    "Curl the weight up while your palms stay facing down.",
                    "Squeeze at the top.",
                    "Lower slowly to straight arms.",
                    "Use a lighter weight than your normal curl.",
                ),
                cues = listOf(
                    "Wrists stay straight",
                    "Elbows pinned",
                    "Slow lowering",
                ),
                commonMistakes = listOf(
                    "Letting your wrists bend back",
                    "Swinging your torso",
                    "Going too heavy",
                ),
            ),
        )
        put(
            "dumbbell external rotation",
            ExerciseGuide(
                setup = "Lie on your side with a light dumbbell in your top hand, elbow bent 90 degrees and tucked against your ribs, forearm across your belly.",
                steps = listOf(
                    "Keep your elbow pinned to your side.",
                    "Rotate your forearm up and away, leading with the back of your hand.",
                    "Stop when your forearm points up or just short of that.",
                    "Lower slowly to the start.",
                    "Finish all reps, then swap sides.",
                ),
                cues = listOf(
                    "Elbow stays glued",
                    "Light weight, slow",
                    "Only the shoulder rotates",
                ),
                commonMistakes = listOf(
                    "Letting your elbow float away from your ribs",
                    "Using a heavy weight",
                    "Twisting your torso",
                ),
            ),
        )
        put(
            "preacher curl",
            ExerciseGuide(
                setup = "Sit at a preacher bench with your upper arms flat on the pad and your armpits against the top, holding a bar or dumbbells.",
                steps = listOf(
                    "Start with your arms nearly straight but not locked.",
                    "Curl the weight up until your forearms are near vertical.",
                    "Squeeze your biceps at the top.",
                    "Lower slowly until your arms are almost straight.",
                    "Keep your upper arms on the pad.",
                ),
                cues = listOf(
                    "Upper arms on the pad",
                    "Slow and stretched at the bottom",
                    "No swinging",
                ),
                commonMistakes = listOf(
                    "Lifting your elbows off the pad",
                    "Dropping the weight fast at the bottom",
                    "Locking out hard at the bottom",
                ),
            ),
        )
        put(
            "dumbbell shrug",
            ExerciseGuide(
                setup = "Stand holding dumbbells at your sides with your arms straight and palms facing in.",
                steps = listOf(
                    "Keep your arms straight and your chest tall.",
                    "Lift your shoulders straight up toward your ears.",
                    "Pause at the top.",
                    "Lower slowly until your shoulders are fully down.",
                    "Repeat without bending your elbows.",
                ),
                cues = listOf(
                    "Shoulders to ears",
                    "Pause at the top",
                    "Straight arms",
                ),
                commonMistakes = listOf(
                    "Rolling your shoulders",
                    "Bending your elbows",
                    "Short reps",
                ),
            ),
        )
        put(
            "dumbbell pullover",
            ExerciseGuide(
                setup = "Lie across or along a bench with your upper back supported, holding one dumbbell with both hands above your chest.",
                steps = listOf(
                    "Keep a slight bend in your elbows.",
                    "Lower the dumbbell in an arc back behind your head until you feel a deep stretch.",
                    "Keep your ribs down and your hips steady.",
                    "Pull the dumbbell back over your chest using your back.",
                    "Repeat slowly.",
                ),
                cues = listOf(
                    "Big arc, soft elbows",
                    "Ribs down",
                    "Pull with your back",
                ),
                commonMistakes = listOf(
                    "Letting your lower back arch up",
                    "Bending and straightening your elbows like a press",
                    "Using a weight that pulls your shoulders out of control",
                ),
            ),
        )
        put(
            "seated cable row",
            ExerciseGuide(
                setup = "Sit at the cable row with your feet braced and knees slightly bent, grip the handle, and sit tall with your arms straight.",
                steps = listOf(
                    "Keep your chest up and your back flat.",
                    "Pull the handle to your lower ribs by driving your elbows back.",
                    "Squeeze your shoulder blades together.",
                    "Let the handle return slowly until your arms are straight and your shoulders stretch forward.",
                    "Keep your torso still.",
                ),
                cues = listOf(
                    "Elbows back",
                    "Chest tall",
                    "Slow return",
                ),
                commonMistakes = listOf(
                    "Rocking your torso to pull",
                    "Shrugging your shoulders",
                    "Rounding your back at the stretch",
                ),
            ),
        )
        put(
            "cable curl",
            ExerciseGuide(
                setup = "Stand facing a low cable with a bar or handle in your hands, palms up, and your elbows at your sides.",
                steps = listOf(
                    "Step back so the cable has tension.",
                    "Keep your elbows beside your ribs.",
                    "Curl the handle up to your chest area.",
                    "Squeeze at the top.",
                    "Lower slowly until your arms are straight.",
                ),
                cues = listOf(
                    "Elbows pinned",
                    "Constant tension",
                    "Slow lowering",
                ),
                commonMistakes = listOf(
                    "Leaning back to cheat",
                    "Letting your elbows drift forward",
                    "Letting the weight stack crash down",
                ),
            ),
        )
        put(
            "chest-supported row",
            ExerciseGuide(
                setup = "Lie face down on an incline bench or sit at the chest pad with your chest against the support and a weight in your hands.",
                steps = listOf(
                    "Let your arms hang straight with your shoulder blades stretched forward.",
                    "Pull your elbows back and up toward the ceiling.",
                    "Squeeze your shoulder blades together at the top.",
                    "Lower under control to straight arms.",
                    "Keep your chest on the pad.",
                ),
                cues = listOf(
                    "Chest stays on the pad",
                    "Elbows back",
                    "Pause at the squeeze",
                ),
                commonMistakes = listOf(
                    "Lifting your chest off the pad",
                    "Shrugging your shoulders",
                    "Dropping fast on the way down",
                ),
            ),
        )
        put(
            "machine row",
            ExerciseGuide(
                setup = "Sit at the machine with your chest against the pad, set the seat so your arms reach the handles straight, and grip them.",
                steps = listOf(
                    "Sit tall with your chest on the pad.",
                    "Pull the handles back by driving your elbows behind you.",
                    "Squeeze your shoulder blades together.",
                    "Return slowly until your arms are straight and your shoulders stretch forward.",
                    "Keep your head and neck relaxed.",
                ),
                cues = listOf(
                    "Elbows back",
                    "Chest on the pad",
                    "Slow return",
                ),
                commonMistakes = listOf(
                    "Pulling with your hands and shrugging",
                    "Leaning back to move the weight",
                    "Short reps that skip the stretch",
                ),
            ),
        )
    }

    // ---- Pull, part two ----
    private fun MutableMap<String, ExerciseGuide>.pull2() {
        put(
            "smith machine row",
            ExerciseGuide(
                setup = "Set the bar at about hip height, hinge forward with a flat back and take an overhand grip just outside your knees.",
                steps = listOf(
                    "Unhook the bar and let it hang at arm length.",
                    "Pull the bar toward your lower ribs.",
                    "Squeeze your shoulder blades together at the top.",
                    "Lower slowly until your arms are straight again.",
                    "Repeat for the set, then rehook the bar.",
                ),
                cues = listOf(
                    "Drive your elbows back, not your hands up",
                    "Keep your torso still",
                    "Control the lowering",
                ),
                commonMistakes = listOf(
                    "Using hip swing to throw the weight up",
                    "Shrugging toward your ears",
                    "Cutting the stretch short at the bottom",
                ),
            ),
        )
        put(
            "assisted pull-up",
            ExerciseGuide(
                setup = "Choose an assist that lets you finish every rep, step on the pad or loop a band, and take an overhand grip a little wider than your shoulders.",
                steps = listOf(
                    "Start with your arms straight and your shoulders pulled down.",
                    "Pull your chest toward the bar.",
                    "Pause when your chin clears the bar.",
                    "Lower slowly to straight arms.",
                    "Reduce the assist as you get stronger.",
                ),
                cues = listOf(
                    "Elbows down and in",
                    "Chest up to the bar",
                    "Full stretch at the bottom",
                ),
                commonMistakes = listOf(
                    "Using the assist to bounce out of the bottom",
                    "Half reps that stop short of straight arms",
                    "Kipping or swinging",
                ),
            ),
        )
        put(
            "dead hang",
            ExerciseGuide(
                setup = "Grab a bar with an overhand grip about shoulder width, then lift your feet so you hang freely.",
                steps = listOf(
                    "Hang with your arms straight.",
                    "Pull your shoulders gently down and away from your ears.",
                    "Breathe steadily and keep your body still.",
                    "Stop when your grip starts to slip.",
                    "Add time each session before adding any weight.",
                ),
                cues = listOf(
                    "Squeeze the bar hard",
                    "Active shoulders, not a limp hang",
                    "Stay relaxed and breathe",
                ),
                commonMistakes = listOf(
                    "Letting the body swing",
                    "Dropping off the bar early without warning",
                    "Shrugging up into the ears",
                ),
            ),
        )
        put(
            "scapular pull",
            ExerciseGuide(
                setup = "Hang from a bar with an overhand grip and straight arms.",
                steps = listOf(
                    "Start in a passive hang with your shoulders up by your ears.",
                    "Pull your shoulder blades down without bending your elbows.",
                    "Your body rises a few centimetres.",
                    "Pause for a moment, then slowly let the shoulders rise again.",
                    "Repeat for the set.",
                ),
                cues = listOf(
                    "Straight arms the whole time",
                    "Shoulders away from the ears",
                    "Move slowly",
                ),
                commonMistakes = listOf(
                    "Bending the elbows into a mini pull-up",
                    "Swinging for momentum",
                    "Rushing the lowering",
                ),
            ),
        )
        put(
            "australian pull-up",
            ExerciseGuide(
                setup = "Set a bar at waist height, lie under it and grab it overhand a little wider than your shoulders with your body straight.",
                steps = listOf(
                    "Hang with arms straight and heels on the floor.",
                    "Squeeze your glutes to keep a straight line.",
                    "Pull your chest to the bar.",
                    "Pause, then lower under control.",
                    "Walk your feet forward to make it harder.",
                ),
                cues = listOf(
                    "Chest to the bar",
                    "Squeeze the shoulder blades",
                    "Body stiff like a plank",
                ),
                commonMistakes = listOf(
                    "Sagging hips",
                    "Reaching with the chin instead of the chest",
                    "Short range at the bottom",
                ),
            ),
        )
        put(
            "l-sit pull-up",
            ExerciseGuide(
                setup = "Hang from a bar with an overhand grip and lift your straight legs in front of you.",
                steps = listOf(
                    "Raise your legs until they are close to parallel with the floor.",
                    "Pull your chest toward the bar without letting the legs drop.",
                    "Pause at the top.",
                    "Lower with the legs still raised.",
                    "Stop the set when the legs sink.",
                ),
                cues = listOf(
                    "Toes pointed and knees locked",
                    "Chest to the bar",
                    "Brace your abs the whole rep",
                ),
                commonMistakes = listOf(
                    "Letting the legs sink as you pull",
                    "Swinging",
                    "Cutting the range short",
                ),
            ),
        )
        put(
            "weighted pull-up",
            ExerciseGuide(
                setup = "Attach a belt or hold a weight between your feet, then take an overhand grip a little wider than your shoulders.",
                steps = listOf(
                    "Hang with straight arms and shoulders down.",
                    "Pull your chest toward the bar.",
                    "Pause with your chin over the bar.",
                    "Lower slowly to a full hang.",
                    "Stop the set if form breaks.",
                ),
                cues = listOf(
                    "Elbows down and in",
                    "Smooth, no swing",
                    "Full range every rep",
                ),
                commonMistakes = listOf(
                    "Adding weight before bodyweight reps are solid",
                    "Kipping",
                    "Half reps",
                ),
            ),
        )
        put(
            "one-arm negative",
            ExerciseGuide(
                setup = "Use a bar you can reach standing on a box, then take a firm grip with one hand and hold the other arm across your body.",
                steps = listOf(
                    "Step up so your chin is over the bar with one hand holding.",
                    "Take your feet off the support.",
                    "Lower yourself as slowly as you can.",
                    "Stop at a full hang and step back up.",
                    "Switch arms.",
                ),
                cues = listOf(
                    "Take 5 seconds or more to lower",
                    "Keep your shoulder pulled down",
                    "Brace your core to stop spinning",
                ),
                commonMistakes = listOf(
                    "Dropping fast at the end",
                    "Letting the shoulder shrug up",
                    "Doing too many reps and losing control",
                ),
            ),
        )
        put(
            "one-arm pull-up",
            ExerciseGuide(
                setup = "Hang from a bar with one hand and cross the other arm over your chest.",
                steps = listOf(
                    "Pull your shoulder down to start.",
                    "Pull your elbow down toward your ribs.",
                    "Keep your body from twisting.",
                    "Pause with your chin over the bar.",
                    "Lower slowly and switch arms.",
                ),
                cues = listOf(
                    "Elbow to the hip",
                    "Squeeze the glutes and brace",
                    "Keep it slow",
                ),
                commonMistakes = listOf(
                    "Using the free hand to help",
                    "Swinging or twisting",
                    "Jumping into it before negatives are solid",
                ),
            ),
        )
        put(
            "one-arm hang",
            ExerciseGuide(
                setup = "Grab a bar firmly with one hand, then lift your feet.",
                steps = listOf(
                    "Hang with your shoulder pulled gently down.",
                    "Keep your body still and facing forward.",
                    "Hold for the planned time.",
                    "Drop and rest.",
                    "Repeat with the other arm.",
                ),
                cues = listOf(
                    "Pack the shoulder, do not hang loose",
                    "Squeeze the bar",
                    "Resist twisting",
                ),
                commonMistakes = listOf(
                    "Hanging with a shrugged shoulder",
                    "Letting the body spin",
                    "Going to total failure and dropping off",
                ),
            ),
        )
        put(
            "front row hold",
            ExerciseGuide(
                setup = "Hang from a bar or rings and pull up into the top of a row with your body leaning back.",
                steps = listOf(
                    "Pull your elbows down toward your ribs.",
                    "Lean back with your body straight.",
                    "Hold with your chest high.",
                    "Stay as long as you can with good form.",
                    "Lower slowly.",
                ),
                cues = listOf(
                    "Chest toward the bar",
                    "Squeeze the shoulder blades",
                    "Body in a line",
                ),
                commonMistakes = listOf(
                    "Letting the hips sag",
                    "Shrugging",
                    "Holding past good form",
                ),
            ),
        )
        put(
            "tuck front lever",
            ExerciseGuide(
                setup = "Hang from a bar or rings and pull your knees up tight to your chest.",
                steps = listOf(
                    "Pull your shoulders down.",
                    "Lift your hips up until your back is flat and parallel with the floor.",
                    "Keep your knees tucked and arms straight.",
                    "Hold for the planned time.",
                    "Lower slowly.",
                ),
                cues = listOf(
                    "Keep your arms straight and your shoulders pulled down",
                    "Squeeze your glutes and abs so the body stays in one line",
                    "Back flat, hips at shoulder height",
                ),
                commonMistakes = listOf(
                    "Letting the hips sag or pike",
                    "Bending the elbows to cheat the hold",
                    "Holding until form breaks instead of ending the set early",
                ),
            ),
        )
        put(
            "advanced tuck front lever",
            ExerciseGuide(
                setup = "Hang from a bar or rings, pull into a tuck front lever, then open your hips so your back stays flat.",
                steps = listOf(
                    "Pull your shoulders down.",
                    "Raise your hips and flatten your back.",
                    "Extend the hips so the thighs go out while the knees stay bent.",
                    "Hold your back flat.",
                    "Lower with control.",
                ),
                cues = listOf(
                    "Keep your arms straight and your shoulders pulled down",
                    "Squeeze your glutes and abs so the body stays in one line",
                    "Thighs and back stay level",
                ),
                commonMistakes = listOf(
                    "Letting the hips sag or pike",
                    "Bending the elbows to cheat the hold",
                    "Holding until form breaks instead of ending the set early",
                ),
            ),
        )
        put(
            "one-leg front lever",
            ExerciseGuide(
                setup = "Hang from a bar or rings and get into a tuck front lever.",
                steps = listOf(
                    "Pull your shoulders down.",
                    "Raise your hips and flatten your back.",
                    "Extend one leg straight out while the other stays tucked.",
                    "Hold with your body in a line.",
                    "Lower and switch legs.",
                ),
                cues = listOf(
                    "Keep your arms straight and your shoulders pulled down",
                    "Squeeze your glutes and abs so the body stays in one line",
                    "Straight leg in line with your body",
                ),
                commonMistakes = listOf(
                    "Letting the hips sag or pike",
                    "Bending the elbows to cheat the hold",
                    "Holding until form breaks instead of ending the set early",
                ),
            ),
        )
        put(
            "straddle front lever",
            ExerciseGuide(
                setup = "Hang from a bar or rings and get into a tuck front lever.",
                steps = listOf(
                    "Pull your shoulders down.",
                    "Raise your hips and flatten your back.",
                    "Extend both legs out wide in a straddle.",
                    "Hold with your body flat.",
                    "Lower with control.",
                ),
                cues = listOf(
                    "Keep your arms straight and your shoulders pulled down",
                    "Squeeze your glutes and abs so the body stays in one line",
                    "Wider legs make it easier",
                ),
                commonMistakes = listOf(
                    "Letting the hips sag or pike",
                    "Bending the elbows to cheat the hold",
                    "Holding until form breaks instead of ending the set early",
                ),
            ),
        )
        put(
            "front lever",
            ExerciseGuide(
                setup = "Hang from a bar or rings with straight arms.",
                steps = listOf(
                    "Pull your shoulders down.",
                    "Lift your body until it is flat and parallel with the floor.",
                    "Keep legs together and straight.",
                    "Hold the position.",
                    "Lower with control.",
                ),
                cues = listOf(
                    "Keep your arms straight and your shoulders pulled down",
                    "Squeeze your glutes and abs so the body stays in one line",
                    "Think about pushing the bar down toward your hips",
                ),
                commonMistakes = listOf(
                    "Letting the hips sag or pike",
                    "Bending the elbows to cheat the hold",
                    "Holding until form breaks instead of ending the set early",
                ),
            ),
        )
        put(
            "skin the cat",
            ExerciseGuide(
                setup = "Hang from rings or a bar with an overhand grip and tuck your knees to your chest.",
                steps = listOf(
                    "Pull your hips up and back over the bar.",
                    "Rotate your legs and body backward until your arms are behind you.",
                    "Lower your feet toward the floor, keeping your arms straight.",
                    "Reverse the path to return to the start.",
                    "Keep it slow.",
                ),
                cues = listOf(
                    "Tuck tight",
                    "Keep your arms straight",
                    "Move slowly through the stretch",
                ),
                commonMistakes = listOf(
                    "Rushing through the stretched position",
                    "Letting go when the shoulders reach their limit",
                    "Skipping the warm-up",
                ),
            ),
        )
        put(
            "tuck back lever",
            ExerciseGuide(
                setup = "Hang from rings or a bar, tuck your knees in tight and turn upside down.",
                steps = listOf(
                    "Start in a tucked inverted hang.",
                    "Lower your hips slowly until your back is parallel to the floor.",
                    "Keep your arms straight.",
                    "Hold the tuck.",
                    "Return to the inverted hang.",
                ),
                cues = listOf(
                    "Keep your arms straight and your body in a flat line",
                    "Push your chest forward and squeeze your glutes",
                ),
                commonMistakes = listOf(
                    "Letting the hips drop or the back arch hard",
                    "Bending the elbows",
                    "Skipping the skin-the-cat warm-up so the shoulders are not ready",
                ),
            ),
        )
        put(
            "advanced tuck back lever",
            ExerciseGuide(
                setup = "Hang from rings or a bar and get into a tuck back lever.",
                steps = listOf(
                    "Start in a tucked inverted hang.",
                    "Lower until your back is flat.",
                    "Open your hips so your thighs go out while the knees stay bent.",
                    "Hold with straight arms.",
                    "Return to the inverted hang.",
                ),
                cues = listOf(
                    "Keep your arms straight and your body in a flat line",
                    "Push your chest forward and squeeze your glutes",
                ),
                commonMistakes = listOf(
                    "Letting the hips drop or the back arch hard",
                    "Bending the elbows",
                    "Skipping the skin-the-cat warm-up so the shoulders are not ready",
                ),
            ),
        )
        put(
            "straddle back lever",
            ExerciseGuide(
                setup = "Hang from rings or a bar and get into a tuck back lever.",
                steps = listOf(
                    "Start in a tucked inverted hang.",
                    "Lower until your body is flat.",
                    "Spread your legs out wide in a straddle.",
                    "Hold with straight arms.",
                    "Return to the inverted hang.",
                ),
                cues = listOf(
                    "Keep your arms straight and your body in a flat line",
                    "Push your chest forward and squeeze your glutes",
                ),
                commonMistakes = listOf(
                    "Letting the hips drop or the back arch hard",
                    "Bending the elbows",
                    "Skipping the skin-the-cat warm-up so the shoulders are not ready",
                ),
            ),
        )
        put(
            "back lever",
            ExerciseGuide(
                setup = "Hang from rings or a bar and turn upside down with your legs together.",
                steps = listOf(
                    "Start in an inverted hang.",
                    "Lower your body slowly until it is flat, face down.",
                    "Keep your arms straight and your legs together.",
                    "Hold the position.",
                    "Return to the inverted hang.",
                ),
                cues = listOf(
                    "Keep your arms straight and your body in a flat line",
                    "Push your chest forward and squeeze your glutes",
                ),
                commonMistakes = listOf(
                    "Letting the hips drop or the back arch hard",
                    "Bending the elbows",
                    "Skipping the skin-the-cat warm-up so the shoulders are not ready",
                ),
            ),
        )
        put(
            "ring row",
            ExerciseGuide(
                setup = "Set rings at waist height and hold them with arms straight and heels on the floor, body leaning back.",
                steps = listOf(
                    "Squeeze your glutes to keep your body straight.",
                    "Pull the rings to the sides of your chest.",
                    "Turn your palms in or up as you pull.",
                    "Pause, then lower slowly.",
                    "Walk your feet forward to make it harder.",
                ),
                cues = listOf(
                    "Elbows close to your ribs",
                    "Squeeze the shoulder blades",
                    "Body like a plank",
                ),
                commonMistakes = listOf(
                    "Sagging hips",
                    "Shrugging the shoulders",
                    "Letting the rings drift apart",
                ),
            ),
        )
        put(
            "ring muscle-up",
            ExerciseGuide(
                setup = "Hang from rings with a false grip, wrists over the rings, arms straight and body still.",
                steps = listOf(
                    "Start from a dead hang with no swing.",
                    "Pull slowly, drawing the rings to your lower chest and keeping them close.",
                    "Lean your chest forward over the rings as your elbows travel back.",
                    "Turn over into the bottom of a ring dip without losing the false grip.",
                    "Press out to straight arms, then lower back down slowly.",
                ),
                cues = listOf(
                    "Dead hang, no swing",
                    "Rings stay close to the body",
                    "Slow through the turnover",
                ),
                commonMistakes = listOf(
                    "Kipping or swinging the legs to get over",
                    "Losing the false grip mid-pull",
                    "Letting one elbow pop over first",
                ),
            ),
        )
        put(
            "one-arm front lever",
            ExerciseGuide(
                setup = "Hang from a bar with one hand and keep the other hand close to your body.",
                steps = listOf(
                    "Pull your shoulder down.",
                    "Lift your body until flat.",
                    "Twist your hips and shoulders slightly to stay square.",
                    "Hold with your body in a line.",
                    "Lower slowly and switch arms.",
                ),
                cues = listOf(
                    "Keep your arms straight and your shoulders pulled down",
                    "Squeeze your glutes and abs so the body stays in one line",
                    "Stay square to the floor",
                ),
                commonMistakes = listOf(
                    "Letting the hips sag or pike",
                    "Bending the elbows to cheat the hold",
                    "Holding until form breaks instead of ending the set early",
                ),
            ),
        )
        put(
            "muscle-up",
            ExerciseGuide(
                setup = "Grab a bar with an overhand grip and use a small swing.",
                steps = listOf(
                    "Pull explosively toward your belly.",
                    "Pull the bar to your lower chest.",
                    "Lean forward and turn over the bar.",
                    "Press out to straight arms.",
                    "Lower slowly.",
                ),
                cues = listOf(
                    "Pull to your belly button, not your chin",
                    "Keep the bar or rings close to your body through the turnover",
                ),
                commonMistakes = listOf(
                    "Swinging so hard the pull-up is skipped",
                    "Letting the bar drift away on the transition",
                    "Pressing out on a locked, shrugged shoulder",
                ),
            ),
        )
        put(
            "strict muscle-up",
            ExerciseGuide(
                setup = "Grab a bar with an overhand grip and hang still, with no swing.",
                steps = listOf(
                    "Pull strongly with straight legs.",
                    "Pull the bar to your lower chest.",
                    "Lean forward and turn over the bar.",
                    "Press out to straight arms.",
                    "Lower slowly.",
                ),
                cues = listOf(
                    "Pull to your belly button, not your chin",
                    "Keep the bar or rings close to your body through the turnover",
                ),
                commonMistakes = listOf(
                    "Swinging so hard the pull-up is skipped",
                    "Letting the bar drift away on the transition",
                    "Pressing out on a locked, shrugged shoulder",
                ),
            ),
        )
        put(
            "inverted muscle-up",
            ExerciseGuide(
                setup = "Hang from a bar with an overhand grip, with a soft surface below and a solid freestanding handstand already in hand.",
                steps = listOf(
                    "Pull hard and drive your hips up toward the bar, as in a back pullover.",
                    "Keep pulling as your chest passes the bar and turn your shoulders forward over it.",
                    "Let your legs keep rising behind you while your hips come over the hands.",
                    "Press from the bent-arm support up to straight arms in a handstand on the bar.",
                    "Balance briefly, then lower your legs back down to the support.",
                ),
                cues = listOf(
                    "Hips to the bar first",
                    "Hands under the shoulders before you press",
                    "Stack the line on top",
                ),
                commonMistakes = listOf(
                    "Pressing before the hips are over the hands",
                    "Piking at the top so the handstand never stacks",
                    "Trying it before the freestanding handstand is solid",
                ),
            ),
        )
    }

    // ---- Legs, part one ----
    private fun MutableMap<String, ExerciseGuide>.legs1() {
        put(
            "pistol squat",
            ExerciseGuide(
                setup = "Stand on one leg with the other leg straight out in front, arms forward for balance.",
                steps = listOf(
                    "Brace your stomach and pick a spot to stare at.",
                    "Sit back and down on the standing leg, keeping the free leg off the floor.",
                    "Lower as far as you can control, heel flat.",
                    "Drive through the whole foot to stand back up.",
                    "Finish all reps on one side, then switch.",
                ),
                cues = listOf(
                    "Whole foot glued to the floor",
                    "Knee tracks over the toes",
                    "Hold a post or sit to a box to start",
                ),
                commonMistakes = listOf(
                    "Heel lifting as you drop",
                    "Collapsing into the bottom instead of lowering under control",
                    "Knee caving inward",
                ),
            ),
        )
        put(
            "back squat",
            ExerciseGuide(
                setup = "Set the bar on your upper back, grip it firmly, and step back with feet about shoulder width and toes slightly out.",
                steps = listOf(
                    "Take a big breath and brace your stomach.",
                    "Push your hips back and bend your knees together.",
                    "Lower until your thighs are at least parallel or as deep as you can keep a flat back.",
                    "Drive up through your whole foot, keeping your chest tall.",
                    "Breathe out at the top and reset for the next rep.",
                ),
                cues = listOf(
                    "Knees follow the line of your toes",
                    "Chest up, brace tight",
                    "Push the floor away",
                ),
                commonMistakes = listOf(
                    "Knees caving inward on the way up",
                    "Hips shooting up before the chest",
                    "Heels lifting off the floor",
                ),
            ),
        )
        put(
            "bulgarian split squat",
            ExerciseGuide(
                setup = "Stand a stride length in front of a bench and rest the top of your back foot on it.",
                steps = listOf(
                    "Hold your weight, if any, and stand tall on the front leg.",
                    "Lower straight down until your back knee nearly touches the floor.",
                    "Keep your front foot flat and your torso upright or leaning slightly forward.",
                    "Press through the front foot to stand.",
                    "Finish all reps, then switch legs.",
                ),
                cues = listOf(
                    "Drop straight down, not forward",
                    "Front heel stays planted",
                    "Lean forward slightly to feel more glutes",
                ),
                commonMistakes = listOf(
                    "Standing too close to the bench",
                    "Pushing off the back foot",
                    "Front knee diving inward",
                ),
            ),
        )
        put(
            "single-leg glute bridge",
            ExerciseGuide(
                setup = "Lie on your back with one foot flat near your hips and the other leg held straight or hugged to your chest.",
                steps = listOf(
                    "Press your planted heel into the floor.",
                    "Lift your hips until your body forms a straight line from shoulder to knee.",
                    "Squeeze your glute hard at the top for a moment.",
                    "Lower slowly without touching down fully.",
                    "Finish all reps, then switch legs.",
                ),
                cues = listOf(
                    "Ribs down, squeeze the glute",
                    "Push through the heel",
                    "Keep hips level",
                ),
                commonMistakes = listOf(
                    "Arching the lower back to get higher",
                    "Hips twisting or dropping on one side",
                    "Pushing through the toes so the knee takes over",
                ),
            ),
        )
        put(
            "single-leg calf raise",
            ExerciseGuide(
                setup = "Stand on one foot on the edge of a step, holding a wall or rail for balance.",
                steps = listOf(
                    "Let your heel drop below the step until you feel a stretch.",
                    "Rise up as high as you can onto the ball of your foot.",
                    "Pause for a second at the top.",
                    "Lower slowly back into the full stretch.",
                    "Finish all reps, then switch legs.",
                ),
                cues = listOf(
                    "Full stretch at the bottom",
                    "Pause at the top",
                    "Slow lowering",
                ),
                commonMistakes = listOf(
                    "Bouncing out of the bottom",
                    "Short, half-range reps",
                    "Letting the ankle roll outward",
                ),
            ),
        )
        put(
            "nordic curl",
            ExerciseGuide(
                setup = "Kneel on a pad with your ankles held firmly under a bar, a heavy object, or by a partner, body straight from knees to head.",
                steps = listOf(
                    "Keep your hips extended and your body in one straight line.",
                    "Lower toward the floor slowly, resisting with your hamstrings all the way down.",
                    "Stop just above the floor without touching it with your hands.",
                    "Pull yourself back up to kneeling with your hamstrings alone.",
                    "Practise the lowering as a Nordic Negative until you can reverse it.",
                ),
                cues = listOf(
                    "Hips stay forward, no folding",
                    "Slow down, strong up",
                    "No hands",
                ),
                commonMistakes = listOf(
                    "Bending at the hips to shorten the lever",
                    "Pushing off the floor to get back up",
                    "Anchoring the feet loosely so they slip",
                ),
            ),
        )
        put(
            "knee-to-wall dorsiflexion",
            ExerciseGuide(
                setup = "Stand facing a wall with one foot a short distance back from it, toes pointing forward.",
                steps = listOf(
                    "Keep the front heel flat on the floor.",
                    "Bend the knee forward to touch the wall.",
                    "Hold the touch for a second.",
                    "Return and slide the foot slightly farther back as it gets easy.",
                    "Finish all reps, then switch feet.",
                ),
                cues = listOf(
                    "Heel stays down",
                    "Knee travels over the middle toes",
                    "Smooth, controlled reps",
                ),
                commonMistakes = listOf(
                    "Lifting the heel",
                    "Knee caving inward",
                    "Forcing the range with a bouncing motion",
                ),
            ),
        )
        put(
            "glute bridge",
            ExerciseGuide(
                setup = "Lie on your back with knees bent, feet flat and hip width apart, arms by your sides.",
                steps = listOf(
                    "Press through your heels.",
                    "Lift your hips until your body makes a straight line from shoulders to knees.",
                    "Squeeze your glutes tight at the top.",
                    "Lower slowly back down.",
                    "Repeat for all reps.",
                ),
                cues = listOf(
                    "Ribs down",
                    "Push through your heels",
                    "Squeeze at the top",
                ),
                commonMistakes = listOf(
                    "Arching the lower back at the top",
                    "Feet too far away so the hamstrings take over",
                    "Rushing the lowering",
                ),
            ),
        )
        put(
            "back extension",
            ExerciseGuide(
                setup = "Set the pad just below your hip crease and hook your heels under the rollers, body straight.",
                steps = listOf(
                    "Cross your arms on your chest or hold a weight to it.",
                    "Bend forward at the hips with a flat back until you feel a stretch behind your legs.",
                    "Raise your torso until your body is in one straight line.",
                    "Pause briefly and lower with control.",
                    "Repeat for all reps.",
                ),
                cues = listOf(
                    "Hinge at the hips",
                    "Stop at a straight line",
                    "Squeeze glutes at the top",
                ),
                commonMistakes = listOf(
                    "Swinging up and leaning back past straight",
                    "Rounding the back at the bottom",
                    "Using momentum instead of control",
                ),
            ),
        )
        put(
            "cossack squat",
            ExerciseGuide(
                setup = "Stand with feet about twice shoulder width and toes slightly out.",
                steps = listOf(
                    "Shift your weight to one side and bend that knee.",
                    "Sit down onto that leg while the other leg stays straight with the foot flat or toes up.",
                    "Go as low as you can with your chest up.",
                    "Push back up through the bent leg and shift to the other side.",
                    "Alternate sides for all reps.",
                ),
                cues = listOf(
                    "Sit back into the hip",
                    "Straight leg stays long",
                    "Chest tall",
                ),
                commonMistakes = listOf(
                    "Heel lifting on the bent leg",
                    "Rounding the back to go deeper",
                    "Knee caving inward",
                ),
            ),
        )
        put(
            "deadlift",
            ExerciseGuide(
                setup = "Stand with the bar over the middle of your feet, feet hip width apart.",
                steps = listOf(
                    "Bend down and grip the bar just outside your legs.",
                    "Pull your chest up, flatten your back and brace your stomach.",
                    "Push the floor away and keep the bar against your legs as it rises.",
                    "Stand fully tall by squeezing your glutes.",
                    "Reverse the motion and lower with control.",
                ),
                cues = listOf(
                    "Push the floor away",
                    "Bar stays close to the legs",
                    "Stay braced and flat-backed",
                ),
                commonMistakes = listOf(
                    "Rounding the lower back",
                    "Jerking the bar off the floor",
                    "Bar drifting away from the body",
                ),
            ),
        )
        put(
            "romanian deadlift",
            ExerciseGuide(
                setup = "Hold the bar at hip height with an overhand grip, feet hip width apart, knees softly bent.",
                steps = listOf(
                    "Brace and push your hips straight back.",
                    "Slide the bar down your thighs with a flat back.",
                    "Lower until you feel a strong hamstring stretch, usually around mid-shin.",
                    "Drive your hips forward to stand tall.",
                    "Squeeze your glutes at the top and repeat.",
                ),
                cues = listOf(
                    "Hips back, not down",
                    "Bar skims the legs",
                    "Flat back",
                ),
                commonMistakes = listOf(
                    "Bending the knees into a squat",
                    "Rounding the back at the bottom",
                    "Letting the bar swing away from the legs",
                ),
            ),
        )
        put(
            "single-leg romanian deadlift",
            ExerciseGuide(
                setup = "Stand on one leg holding a weight in the opposite hand or both hands, standing knee slightly soft.",
                steps = listOf(
                    "Brace and tilt forward from the hip, sending the free leg straight back.",
                    "Keep your hips square to the floor.",
                    "Lower until your torso is near parallel or you feel a strong hamstring stretch.",
                    "Squeeze your glute to stand back up.",
                    "Finish all reps, then switch legs.",
                ),
                cues = listOf(
                    "Hips stay square",
                    "Long line from head to heel",
                    "Pick a spot to stare at",
                ),
                commonMistakes = listOf(
                    "Opening the hips and twisting",
                    "Rounding the back",
                    "Bending the standing knee too much",
                ),
            ),
        )
        put(
            "front squat",
            ExerciseGuide(
                setup = "Rest the bar on the front of your shoulders with elbows high, feet shoulder width apart.",
                steps = listOf(
                    "Brace your stomach and keep elbows pointing forward.",
                    "Sit straight down between your knees.",
                    "Go as low as you can keep your chest tall.",
                    "Drive up through your whole foot.",
                    "Keep the elbows high all the way to the top.",
                ),
                cues = listOf(
                    "Elbows high",
                    "Chest tall",
                    "Sit straight down",
                ),
                commonMistakes = listOf(
                    "Elbows dropping so the bar rolls forward",
                    "Torso folding forward",
                    "Heels lifting",
                ),
            ),
        )
        put(
            "hip thrust",
            ExerciseGuide(
                setup = "Sit with your upper back against a bench, the bar or weight resting across your hips, feet flat.",
                steps = listOf(
                    "Tuck your chin and brace your stomach.",
                    "Drive through your heels and lift your hips.",
                    "Stop when your torso is flat and shins are about vertical.",
                    "Squeeze your glutes hard for a second.",
                    "Lower under control and repeat.",
                ),
                cues = listOf(
                    "Chin tucked, ribs down",
                    "Shins vertical at the top",
                    "Squeeze at the top",
                ),
                commonMistakes = listOf(
                    "Over-arching the lower back at the top",
                    "Feet too close or too far",
                    "Using momentum instead of glutes",
                ),
            ),
        )
        put(
            "tib raise",
            ExerciseGuide(
                setup = "Lean your back against a wall with feet a short step out in front, heels on the floor.",
                steps = listOf(
                    "Keep your legs straight and heels planted.",
                    "Pull your toes up toward your shins as high as you can.",
                    "Pause briefly at the top.",
                    "Lower your toes slowly back down.",
                    "Repeat for all reps.",
                ),
                cues = listOf(
                    "Heels stay down",
                    "Pull toes high",
                    "Slow lowering",
                ),
                commonMistakes = listOf(
                    "Bending the knees to cheat",
                    "Short, quick reps",
                    "Walking the feet farther out until you lose balance",
                ),
            ),
        )
        put(
            "side-lying hip abduction",
            ExerciseGuide(
                setup = "Lie on your side with your legs straight and stacked, head resting on your arm.",
                steps = listOf(
                    "Keep your hips stacked and your top toes pointing forward.",
                    "Lift the top leg straight up about 30 to 45 degrees.",
                    "Pause at the top.",
                    "Lower slowly without letting it rest on the bottom leg.",
                    "Finish all reps, then switch sides.",
                ),
                cues = listOf(
                    "Toes forward or slightly down",
                    "Hips stay stacked",
                    "Slow and controlled",
                ),
                commonMistakes = listOf(
                    "Rolling the hips backward",
                    "Swinging the leg",
                    "Lifting too high so the back takes over",
                ),
            ),
        )
        put(
            "sumo deadlift",
            ExerciseGuide(
                setup = "Stand with a wide stance, toes turned out, the bar over the middle of your feet.",
                steps = listOf(
                    "Reach down and grip the bar between your legs.",
                    "Pull your chest up, brace, and push your knees out over your toes.",
                    "Push the floor apart and stand up with the bar close to your body.",
                    "Squeeze your glutes at the top.",
                    "Lower with control back to the floor.",
                ),
                cues = listOf(
                    "Knees out over the toes",
                    "Chest up",
                    "Push the floor apart",
                ),
                commonMistakes = listOf(
                    "Knees caving in",
                    "Hips shooting up first",
                    "Bar drifting away from the body",
                ),
            ),
        )
        put(
            "rack pull",
            ExerciseGuide(
                setup = "Set the bar on the rack at about knee height and stand with it over the middle of your feet.",
                steps = listOf(
                    "Grip the bar just outside your legs.",
                    "Pull your chest up, flatten your back and brace.",
                    "Drive your hips forward and stand tall.",
                    "Squeeze your upper back and glutes at the top.",
                    "Lower with control back to the rack.",
                ),
                cues = listOf(
                    "Chest up and braced",
                    "Bar stays close",
                    "Finish with the hips, not the back",
                ),
                commonMistakes = listOf(
                    "Leaning back at the top",
                    "Rounding the upper back",
                    "Dropping the bar from the top",
                ),
            ),
        )
        put(
            "good morning",
            ExerciseGuide(
                setup = "Rest the bar across your upper back as for a squat, feet shoulder width apart, knees softly bent.",
                steps = listOf(
                    "Brace your stomach and keep your back flat.",
                    "Push your hips back and tilt your torso forward.",
                    "Lower until you feel a strong hamstring stretch, torso near parallel.",
                    "Drive your hips forward to stand back up.",
                    "Keep the weight light and the pace steady.",
                ),
                cues = listOf(
                    "Hips back",
                    "Flat back the whole time",
                    "Light weight, slow tempo",
                ),
                commonMistakes = listOf(
                    "Rounding the back",
                    "Squatting down instead of hinging",
                    "Using too much weight",
                ),
            ),
        )
        put(
            "barbell lunge",
            ExerciseGuide(
                setup = "Set the bar on your upper back and stand with feet hip width apart.",
                steps = listOf(
                    "Step one foot forward into a long stride.",
                    "Lower straight down until the back knee almost touches the floor.",
                    "Keep your torso tall and your front heel planted.",
                    "Push through the front foot to return to standing.",
                    "Alternate legs or finish one side first.",
                ),
                cues = listOf(
                    "Drop straight down",
                    "Front heel down",
                    "Chest tall",
                ),
                commonMistakes = listOf(
                    "Stride too short, knee travels far past the toes",
                    "Leaning forward",
                    "Front knee caving inward",
                ),
            ),
        )
        put(
            "barbell step-up",
            ExerciseGuide(
                setup = "Set the bar on your upper back and stand facing a sturdy box or bench about knee height.",
                steps = listOf(
                    "Place one whole foot on the box.",
                    "Drive through that foot to stand up on top.",
                    "Bring the other foot up to stand tall.",
                    "Step back down with control.",
                    "Finish all reps on one leg, then switch.",
                ),
                cues = listOf(
                    "Push through the top foot",
                    "Stand fully tall",
                    "Slow step down",
                ),
                commonMistakes = listOf(
                    "Pushing off the bottom foot",
                    "Box too high to keep your back flat",
                    "Leaning far forward",
                ),
            ),
        )
        put(
            "goblet squat",
            ExerciseGuide(
                setup = "Hold a dumbbell or kettlebell tight against your chest, feet shoulder width apart, toes slightly out.",
                steps = listOf(
                    "Brace your stomach and keep elbows pointing down.",
                    "Sit straight down between your knees.",
                    "Go as deep as you can keep your chest tall.",
                    "Press through your whole foot to stand.",
                    "Repeat for all reps.",
                ),
                cues = listOf(
                    "Chest tall",
                    "Elbows inside the knees",
                    "Whole foot on the floor",
                ),
                commonMistakes = listOf(
                    "Heels lifting",
                    "Torso folding forward",
                    "Knees caving inward",
                ),
            ),
        )
        put(
            "walking lunge",
            ExerciseGuide(
                setup = "Stand tall with feet together, weights at your sides or on your shoulders, with space to walk forward.",
                steps = listOf(
                    "Step forward into a long stride.",
                    "Lower until your back knee nearly touches the floor.",
                    "Push through the front foot and bring your back leg through into the next step.",
                    "Keep your torso upright as you alternate legs.",
                    "Walk the full distance or count steps as reps.",
                ),
                cues = listOf(
                    "Long steps",
                    "Torso tall",
                    "Control each lowering",
                ),
                commonMistakes = listOf(
                    "Short, choppy steps",
                    "Letting the front knee cave in",
                    "Leaning forward and rushing",
                ),
            ),
        )
        put(
            "dumbbell step-up",
            ExerciseGuide(
                setup = "Hold a dumbbell in each hand at your sides and stand facing a sturdy box or bench about knee height.",
                steps = listOf(
                    "Place one whole foot on the box.",
                    "Drive through that foot to stand tall on top.",
                    "Bring the other foot up beside it.",
                    "Step back down slowly.",
                    "Finish all reps on one leg, then switch.",
                ),
                cues = listOf(
                    "Push through the top foot",
                    "Stand fully tall",
                    "Slow step down",
                ),
                commonMistakes = listOf(
                    "Pushing off the bottom foot",
                    "Using a box so high the hips tilt",
                    "Rushing down",
                ),
            ),
        )
        put(
            "cable pull-through",
            ExerciseGuide(
                setup = "Face away from a low cable with the rope between your legs, walk forward a few steps and stand with feet a bit wider than hips.",
                steps = listOf(
                    "Hold the rope with both hands and let it pull through your legs.",
                    "Push your hips straight back with a flat back until you feel a hamstring stretch.",
                    "Drive your hips forward and squeeze your glutes.",
                    "Stand tall without leaning back.",
                    "Reverse slowly and repeat.",
                ),
                cues = listOf(
                    "Hips back, then forward",
                    "Arms stay relaxed",
                    "Squeeze glutes at the top",
                ),
                commonMistakes = listOf(
                    "Squatting instead of hinging",
                    "Pulling with the arms",
                    "Leaning back at the top",
                ),
            ),
        )
    }

    // ---- Legs, part two ----
    private fun MutableMap<String, ExerciseGuide>.legs2() {
        put(
            "leg press",
            ExerciseGuide(
                setup = "Sit in the machine with your back and hips flat on the pad and your feet shoulder-width apart in the middle of the platform.",
                steps = listOf(
                    "Release the safety handles and hold the grips.",
                    "Lower the platform until your knees are bent deeply without your hips lifting off the pad.",
                    "Press through your whole foot until your legs are almost straight.",
                    "Lower under control and repeat for the set.",
                ),
                cues = listOf(
                    "Hips stay glued to the pad",
                    "Knees track in line with your toes",
                ),
                commonMistakes = listOf(
                    "Locking the knees hard at the top",
                    "Cutting the depth short so the hips peel off the pad",
                ),
            ),
        )
        put(
            "hack squat",
            ExerciseGuide(
                setup = "Stand on the platform with your back flat on the pad and your feet about shoulder-width apart, shoulders under the pads.",
                steps = listOf(
                    "Unhook the safety and stand tall.",
                    "Bend your knees and lower until your thighs pass parallel.",
                    "Drive through your whole foot to stand back up.",
                    "Stop just short of locking out, then start the next rep.",
                ),
                cues = listOf(
                    "Keep your back on the pad",
                    "Knees follow your toes",
                ),
                commonMistakes = listOf(
                    "Heels lifting off the platform",
                    "Bouncing out of the bottom",
                ),
            ),
        )
        put(
            "leg extension",
            ExerciseGuide(
                setup = "Sit with your back against the pad, the roller on your lower shins and the knee joint lined up with the machine pivot.",
                steps = listOf(
                    "Hold the handles and straighten your legs until they are fully extended.",
                    "Squeeze your thighs for a beat at the top.",
                    "Lower slowly until your knees are bent about 90 degrees.",
                    "Repeat without letting the weights rest.",
                ),
                cues = listOf(
                    "Slow on the way down",
                    "Squeeze at the top",
                ),
                commonMistakes = listOf(
                    "Swinging the weight up with momentum",
                    "Lifting your hips off the seat",
                ),
            ),
        )
        put(
            "seated leg curl",
            ExerciseGuide(
                setup = "Sit with your back against the pad, the thigh pad snug on your legs and the roller just above your heels.",
                steps = listOf(
                    "Hold the handles and start with your legs straight.",
                    "Curl your heels down and back as far as you can.",
                    "Pause briefly at the bottom.",
                    "Let the legs rise slowly back to the start.",
                ),
                cues = listOf(
                    "Pull heels toward your glutes",
                    "Slow return",
                ),
                commonMistakes = listOf(
                    "Rushing the lowering phase",
                    "Leaning forward to cheat the weight",
                ),
            ),
        )
        put(
            "lying leg curl",
            ExerciseGuide(
                setup = "Lie face down with the roller just above your heels and your knees at the edge of the pad.",
                steps = listOf(
                    "Hold the handles and keep your hips pressed into the pad.",
                    "Curl your heels toward your glutes.",
                    "Squeeze at the top.",
                    "Lower slowly until your legs are almost straight.",
                ),
                cues = listOf(
                    "Hips stay down",
                    "Control the lowering",
                ),
                commonMistakes = listOf(
                    "Hips popping up off the pad",
                    "Using a jerk to start each rep",
                ),
            ),
        )
        put(
            "hip adduction",
            ExerciseGuide(
                setup = "Sit with your back against the pad and your legs apart against the inside pads, set to a range you can control.",
                steps = listOf(
                    "Hold the handles.",
                    "Squeeze your legs together until the pads meet.",
                    "Pause for a beat.",
                    "Open slowly back to the start.",
                ),
                cues = listOf(
                    "Squeeze from the inner thighs",
                    "Slow opening",
                ),
                commonMistakes = listOf(
                    "Letting the weights slam back",
                    "Choosing a starting width that pinches the hips",
                ),
            ),
        )
        put(
            "hip abduction",
            ExerciseGuide(
                setup = "Sit with your back against the pad and your legs together with the outer pads against your knees.",
                steps = listOf(
                    "Hold the handles and sit tall.",
                    "Push your knees apart as far as you comfortably can.",
                    "Pause for a beat.",
                    "Return slowly without letting the weights rest.",
                ),
                cues = listOf(
                    "Push with the outsides of your hips",
                    "Stay tall",
                ),
                commonMistakes = listOf(
                    "Swinging the weight with your torso",
                    "Using a half range of motion",
                ),
            ),
        )
        put(
            "seated calf raise",
            ExerciseGuide(
                setup = "Sit with the balls of your feet on the platform and the pad resting on your lower thighs.",
                steps = listOf(
                    "Release the safety and lower your heels as far as comfortable.",
                    "Pause in the stretch.",
                    "Press up onto the balls of your feet as high as you can.",
                    "Squeeze at the top, then lower slowly.",
                ),
                cues = listOf(
                    "Full stretch at the bottom",
                    "Pause at the top",
                ),
                commonMistakes = listOf(
                    "Bouncing out of the bottom",
                    "Short, partial reps",
                ),
            ),
        )
        put(
            "standing calf raise",
            ExerciseGuide(
                setup = "Stand with the balls of your feet on a step or platform, shoulders under the pads or holding a support.",
                steps = listOf(
                    "Lower your heels below the step until you feel a deep stretch.",
                    "Pause in the stretch.",
                    "Rise as high as you can on your toes.",
                    "Squeeze at the top and lower slowly.",
                ),
                cues = listOf(
                    "Full range every rep",
                    "Straight knees throughout",
                ),
                commonMistakes = listOf(
                    "Bouncing through the bottom",
                    "Bending the knees to cheat the lift",
                ),
            ),
        )
        put(
            "smith machine squat",
            ExerciseGuide(
                setup = "Set the bar at upper-back height and step under it with your feet slightly forward of the bar, hands gripping the bar.",
                steps = listOf(
                    "Unhook the bar and stand tall.",
                    "Bend your knees and hips and lower until your thighs are parallel or deeper.",
                    "Drive up through your whole foot.",
                    "Rack the bar by twisting it onto the hooks when the set is done.",
                ),
                cues = listOf(
                    "Chest up",
                    "Knees follow toes",
                ),
                commonMistakes = listOf(
                    "Standing so far forward or back that the knees or back strain",
                    "Leaning on the bar instead of driving the legs",
                ),
            ),
        )
        put(
            "bodyweight squat",
            ExerciseGuide(
                setup = "Stand with your feet shoulder-width apart and toes turned out slightly.",
                steps = listOf(
                    "Hold your arms out in front for balance.",
                    "Push your hips back and bend your knees to lower.",
                    "Go down until your thighs are at least parallel.",
                    "Drive through your whole foot to stand tall.",
                ),
                cues = listOf(
                    "Chest proud",
                    "Knees track over toes",
                ),
                commonMistakes = listOf(
                    "Heels lifting",
                    "Knees caving inward",
                ),
            ),
        )
        put(
            "split squat",
            ExerciseGuide(
                setup = "Take a long stance with one foot forward and the other foot back on its toes, feet hip-width apart.",
                steps = listOf(
                    "Keep your torso upright.",
                    "Bend both knees and lower your back knee toward the floor.",
                    "Stop just above the floor.",
                    "Drive through your front foot to rise, finish the reps and swap legs.",
                ),
                cues = listOf(
                    "Straight down and up",
                    "Front heel stays planted",
                ),
                commonMistakes = listOf(
                    "Stance too short so the front knee drifts far forward",
                    "Pushing off the back foot",
                ),
            ),
        )
        put(
            "sissy squat",
            ExerciseGuide(
                setup = "Stand holding a support with your feet close together.",
                steps = listOf(
                    "Rise onto your toes.",
                    "Bend your knees forward and lean your torso back as one line from knees to shoulders.",
                    "Lower as far as you can control.",
                    "Push your knees back and return to standing.",
                ),
                cues = listOf(
                    "Knees drive forward",
                    "Hips stay extended",
                ),
                commonMistakes = listOf(
                    "Folding at the hips instead of keeping the line",
                    "Dropping fast with no control",
                ),
            ),
        )
        put(
            "shrimp squat",
            ExerciseGuide(
                setup = "Stand on one leg and bend the other knee behind you, holding that foot with your hand.",
                steps = listOf(
                    "Hold the rear foot and keep your chest up.",
                    "Lower slowly until the back knee lightly touches the floor.",
                    "Pause for a beat.",
                    "Drive through your standing foot to stand up and repeat, then swap.",
                ),
                cues = listOf(
                    "Slow descent",
                    "Stay balanced over the whole foot",
                ),
                commonMistakes = listOf(
                    "Dropping onto the knee",
                    "Letting the standing knee collapse inward",
                ),
            ),
        )
        put(
            "dragon squat",
            ExerciseGuide(
                setup = "Stand on one leg with the other leg ready to sweep behind and across.",
                steps = listOf(
                    "Bend your standing knee to start lowering.",
                    "Sweep the free leg behind and across your body as you go down.",
                    "Lower until you are low and controlled.",
                    "Drive through the standing foot to stand and unwind, then swap sides.",
                ),
                cues = listOf(
                    "Slow and controlled",
                    "Keep your standing heel down",
                ),
                commonMistakes = listOf(
                    "Rushing the descent",
                    "Letting the standing knee cave in",
                ),
            ),
        )
        put(
            "hamstring bridge",
            ExerciseGuide(
                setup = "Lie on your back with your heels on the floor and your knees bent about 90 degrees.",
                steps = listOf(
                    "Push your hips up by pressing your heels into the floor.",
                    "Squeeze your hamstrings and glutes at the top.",
                    "Hold briefly with a straight line from knees to shoulders.",
                    "Lower slowly.",
                ),
                cues = listOf(
                    "Drive through the heels",
                    "Ribs down",
                ),
                commonMistakes = listOf(
                    "Arching the lower back at the top",
                    "Pushing from the toes",
                ),
            ),
        )
        put(
            "nordic negative",
            ExerciseGuide(
                setup = "Kneel on a pad with your ankles anchored by a partner or under a fixed bar and your body straight from knees to head.",
                steps = listOf(
                    "Hold your arms ready to catch yourself.",
                    "Keep a straight line from your knees to your head.",
                    "Lower your torso toward the floor as slowly as you can.",
                    "Catch yourself with your hands, then push back up and repeat.",
                ),
                cues = listOf(
                    "Hips locked in line",
                    "Lower as slowly as possible",
                ),
                commonMistakes = listOf(
                    "Bending at the hips",
                    "Dropping fast instead of controlling the lowering",
                ),
            ),
        )
        put(
            "pause squat",
            ExerciseGuide(
                setup = "Set the safety pins just below your bottom position, then set the bar on your upper back with your feet shoulder-width apart and your brace set.",
                steps = listOf(
                    "Take a big breath and brace.",
                    "Squat down to the bottom.",
                    "Hold the bottom for 1 to 3 seconds without relaxing.",
                    "Drive up out of the hole and finish the rep.",
                ),
                cues = listOf(
                    "Stay tight in the pause",
                    "Drive up hard after the pause",
                ),
                commonMistakes = listOf(
                    "Relaxing and bouncing out of the pause",
                    "Chest folding forward",
                ),
            ),
        )
        put(
            "heavy squat",
            ExerciseGuide(
                setup = "Set the safety pins just below your bottom position, then set the bar on your upper back with your feet shoulder-width apart and your brace set.",
                steps = listOf(
                    "Take a big breath and brace your trunk.",
                    "Bend your hips and knees to lower under control.",
                    "Reach at least thigh-parallel depth.",
                    "Drive up through your whole foot and breathe out near the top.",
                ),
                cues = listOf(
                    "Brace before every rep",
                    "Knees follow toes",
                ),
                commonMistakes = listOf(
                    "Losing your brace under heavy load",
                    "Cutting depth as the weights get heavier",
                ),
            ),
        )
        put(
            "double-bodyweight squat",
            ExerciseGuide(
                setup = "Set the safety pins just below your bottom position, then set the bar on your upper back with your feet shoulder-width apart and your brace set.",
                steps = listOf(
                    "Take a big breath and brace your trunk.",
                    "Bend your hips and knees to lower under control.",
                    "Reach at least thigh-parallel depth.",
                    "Drive up through your whole foot and breathe out near the top.",
                ),
                cues = listOf(
                    "Brace before every rep",
                    "Knees follow toes",
                ),
                commonMistakes = listOf(
                    "Losing your brace under heavy load",
                    "Cutting depth as the weights get heavier",
                ),
            ),
        )
        put(
            "triple-bodyweight squat",
            ExerciseGuide(
                setup = "Set the safety pins just below your bottom position, then set the bar on your upper back with your feet shoulder-width apart and your brace set.",
                steps = listOf(
                    "Take a big breath and brace your trunk.",
                    "Bend your hips and knees to lower under control.",
                    "Reach at least thigh-parallel depth.",
                    "Drive up through your whole foot and breathe out near the top.",
                ),
                cues = listOf(
                    "Brace before every rep",
                    "Knees follow toes",
                ),
                commonMistakes = listOf(
                    "Losing your brace under heavy load",
                    "Cutting depth as the weights get heavier",
                ),
            ),
        )
        put(
            "volume deadlift",
            ExerciseGuide(
                setup = "Stand with the bar over the middle of your feet and your feet about hip-width apart.",
                steps = listOf(
                    "Hinge your hips back, bend your knees and grip the bar just outside your knees.",
                    "Brace, pull your chest up and make your back flat.",
                    "Push the floor away and stand tall, keeping the bar close to your legs.",
                    "Lower the bar under control by pushing your hips back.",
                ),
                cues = listOf(
                    "Push the floor away",
                    "Bar stays close to your body",
                ),
                commonMistakes = listOf(
                    "Rounding the lower back",
                    "Jerking the bar off the floor",
                ),
            ),
        )
        put(
            "double-bodyweight deadlift",
            ExerciseGuide(
                setup = "Stand with the bar over the middle of your feet and your feet about hip-width apart.",
                steps = listOf(
                    "Hinge your hips back, bend your knees and grip the bar just outside your knees.",
                    "Brace, pull your chest up and make your back flat.",
                    "Push the floor away and stand tall, keeping the bar close to your legs.",
                    "Lower the bar under control by pushing your hips back.",
                ),
                cues = listOf(
                    "Push the floor away",
                    "Bar stays close to your body",
                ),
                commonMistakes = listOf(
                    "Rounding the lower back",
                    "Jerking the bar off the floor",
                ),
            ),
        )
        put(
            "heavy deadlift",
            ExerciseGuide(
                setup = "Stand with the bar over the middle of your feet and your feet about hip-width apart.",
                steps = listOf(
                    "Hinge your hips back, bend your knees and grip the bar just outside your knees.",
                    "Brace, pull your chest up and make your back flat.",
                    "Push the floor away and stand tall, keeping the bar close to your legs.",
                    "Lower the bar under control by pushing your hips back.",
                ),
                cues = listOf(
                    "Push the floor away",
                    "Bar stays close to your body",
                ),
                commonMistakes = listOf(
                    "Rounding the lower back",
                    "Jerking the bar off the floor",
                ),
            ),
        )
        put(
            "triple-bodyweight deadlift",
            ExerciseGuide(
                setup = "Stand with the bar over the middle of your feet and your feet about hip-width apart.",
                steps = listOf(
                    "Hinge your hips back, bend your knees and grip the bar just outside your knees.",
                    "Brace, pull your chest up and make your back flat.",
                    "Push the floor away and stand tall, keeping the bar close to your legs.",
                    "Lower the bar under control by pushing your hips back.",
                ),
                cues = listOf(
                    "Push the floor away",
                    "Bar stays close to your body",
                ),
                commonMistakes = listOf(
                    "Rounding the lower back",
                    "Jerking the bar off the floor",
                ),
            ),
        )
    }

    // ---- Core, holds and skills ----
    private fun MutableMap<String, ExerciseGuide>.core() {
        put(
            "hanging leg raise",
            ExerciseGuide(
                setup = "Hang from a bar with an overhand grip, arms straight and shoulders pulled slightly down.",
                steps = listOf(
                    "Brace your abs and tuck your pelvis under so your lower back flattens.",
                    "Raise your straight legs in front of you until they reach hip height or higher.",
                    "Pause briefly at the top.",
                    "Lower your legs slowly without letting your body swing.",
                    "Start the next rep from a dead hang.",
                ),
                cues = listOf(
                    "Curl your pelvis up, do not just lift the legs",
                    "Slow on the way down",
                ),
                commonMistakes = listOf(
                    "Swinging to build momentum",
                    "Bending the knees so far the move becomes a knee raise",
                    "Letting the shoulders shrug up to the ears",
                ),
            ),
        )
        put(
            "hanging knee raise",
            ExerciseGuide(
                setup = "Hang from a bar with arms straight and shoulders pulled slightly down.",
                steps = listOf(
                    "Brace your abs and tilt your pelvis backward.",
                    "Pull your knees up toward your chest.",
                    "Pause briefly at the top.",
                    "Lower your legs slowly to a straight hang.",
                    "Keep your body still between reps.",
                ),
                cues = listOf(
                    "Bring the knees to the ribs",
                    "No swinging",
                ),
                commonMistakes = listOf(
                    "Using momentum from the legs",
                    "Raising the knees only to hip height with no pelvic curl",
                ),
            ),
        )
        put(
            "ab wheel rollout",
            ExerciseGuide(
                setup = "Kneel on a mat holding the wheel handles under your shoulders, with your back slightly rounded and abs tight.",
                steps = listOf(
                    "Brace your abs and keep your ribs pulled down.",
                    "Roll the wheel forward slowly, reaching your arms ahead of you.",
                    "Stop where you can still keep your lower back from sagging.",
                    "Pull the wheel back by tightening your abs and lats until your hips sit over your knees.",
                    "Reset your brace before the next rep.",
                ),
                cues = listOf(
                    "Ribs down, hips tucked",
                    "Move only as far as your lower back stays flat",
                ),
                commonMistakes = listOf(
                    "Letting the lower back sag as you extend",
                    "Hinging at the hips instead of rolling the wheel",
                    "Going too far, too soon",
                ),
            ),
        )
        put(
            "l-sit",
            ExerciseGuide(
                setup = "Sit between two parallel bars, boxes or the floor with your hands beside your hips, arms straight.",
                steps = listOf(
                    "Press down hard through your hands and lift your hips and seat off the surface.",
                    "Straighten your knees and raise both legs in front of you.",
                    "Hold with your legs as close to parallel with the floor as you can.",
                    "Breathe steadily and keep your shoulders pushed down away from your ears.",
                    "Progress by moving from tucked knees to one straight leg, then both.",
                ),
                cues = listOf(
                    "Push the floor away",
                    "Toes pointed, knees locked",
                ),
                commonMistakes = listOf(
                    "Shrugging the shoulders up",
                    "Bending the knees and calling it done",
                    "Leaning back so the legs are not truly lifted",
                ),
            ),
        )
        put(
            "dragon flag",
            ExerciseGuide(
                setup = "Lie on a bench or floor and grip a fixed support behind your head, with your shoulders and upper back as the only contact points.",
                steps = listOf(
                    "Pull your legs and hips up until your body forms a straight line over your shoulders.",
                    "Keep your body rigid and lower it slowly as one piece.",
                    "Stop just above the surface.",
                    "Reverse the move, or bend the knees to come back up.",
                    "Start with bent knees or one leg tucked if the full version is too hard.",
                ),
                cues = listOf(
                    "Body stiff as a plank",
                    "Take at least three seconds to lower",
                ),
                commonMistakes = listOf(
                    "Bending at the hips as you lower",
                    "Lifting the neck off the surface",
                    "Dropping fast with no control",
                ),
            ),
        )
        put(
            "weighted plank",
            ExerciseGuide(
                setup = "Set up in a forearm plank and have a partner or yourself place a plate on your upper back.",
                steps = listOf(
                    "Place your elbows under your shoulders and your body in a straight line.",
                    "Squeeze your glutes and brace your abs.",
                    "Hold the position with the plate steady on your back.",
                    "Breathe steadily and end the set when your hips sag.",
                    "Build up the load slowly across weeks.",
                ),
                cues = listOf(
                    "Ribs down, glutes tight",
                    "Straight line from head to heels",
                ),
                commonMistakes = listOf(
                    "Hips sagging or piking up",
                    "Loading the plate before you can hold plain plank cleanly",
                ),
            ),
        )
        put(
            "plank",
            ExerciseGuide(
                setup = "Lie face down, then prop yourself on your forearms with elbows under your shoulders and toes on the floor.",
                steps = listOf(
                    "Lift your hips so your body forms a straight line from head to heels.",
                    "Squeeze your glutes and brace your abs as if bracing for a light punch.",
                    "Keep your neck long and your eyes on the floor.",
                    "Hold for the target time while breathing steadily.",
                    "Stop the set when your hips sag.",
                ),
                cues = listOf(
                    "Ribs down, glutes tight",
                    "Push the floor away with your forearms",
                ),
                commonMistakes = listOf(
                    "Hips sagging or sticking up",
                    "Holding your breath",
                    "Letting the head drop",
                ),
            ),
        )
        put(
            "side plank",
            ExerciseGuide(
                setup = "Lie on your side with your forearm on the floor under your shoulder and your legs stacked.",
                steps = listOf(
                    "Lift your hips so your body forms a straight line from head to feet.",
                    "Keep your top hip stacked over the bottom one.",
                    "Hold the position while breathing steadily.",
                    "Lower with control and repeat on the other side.",
                    "Make it easier by bending your knees.",
                ),
                cues = listOf(
                    "Push the floor away through your forearm",
                    "Hips high and forward",
                ),
                commonMistakes = listOf(
                    "Hips dropping toward the floor",
                    "Rolling the chest forward or back",
                    "Skipping the weaker side",
                ),
            ),
        )
        put(
            "lying leg raise",
            ExerciseGuide(
                setup = "Lie on your back with your legs straight and your hands under your hips or by your sides.",
                steps = listOf(
                    "Press your lower back into the floor and brace your abs.",
                    "Raise your straight legs until they point at the ceiling.",
                    "Lower them slowly toward the floor without letting your back arch.",
                    "Stop before your lower back lifts off the floor.",
                    "Bend the knees slightly if you cannot keep your back flat.",
                ),
                cues = listOf(
                    "Lower back stays glued down",
                    "Slow on the way down",
                ),
                commonMistakes = listOf(
                    "Letting the lower back arch off the floor",
                    "Dropping the legs quickly",
                    "Using momentum at the bottom",
                ),
            ),
        )
        put(
            "dead bug",
            ExerciseGuide(
                setup = "Lie on your back with arms pointing at the ceiling and hips and knees bent to 90 degrees.",
                steps = listOf(
                    "Press your lower back gently into the floor and brace your abs.",
                    "Slowly lower one arm overhead and the opposite leg toward the floor.",
                    "Stop before your lower back lifts.",
                    "Return to the start and switch sides.",
                    "Move slowly and alternate each rep.",
                ),
                cues = listOf(
                    "Keep your lower back flat",
                    "Breathe out as you extend",
                ),
                commonMistakes = listOf(
                    "Letting the lower back arch",
                    "Moving too fast",
                    "Flaring the ribs up",
                ),
            ),
        )
        put(
            "woodchop",
            ExerciseGuide(
                setup = "Stand side-on to a cable or hold a weight with both hands, feet shoulder-width apart.",
                steps = listOf(
                    "Start with the weight beside one hip and arms mostly straight.",
                    "Rotate your torso and pivot your back foot as you pull the weight diagonally up across your body.",
                    "Finish with the weight above the opposite shoulder.",
                    "Control the weight back down along the same path.",
                    "Complete all reps on one side, then switch.",
                ),
                cues = listOf(
                    "Turn from the torso, not the arms",
                    "Pivot the back foot",
                ),
                commonMistakes = listOf(
                    "Pulling with the arms only",
                    "Using too much weight and losing control",
                    "Leaning back",
                ),
            ),
        )
        put(
            "pallof press",
            ExerciseGuide(
                setup = "Stand side-on to a cable or band set at chest height, holding the handle at your chest with both hands.",
                steps = listOf(
                    "Step away until the cable is taut and set your feet shoulder-width apart.",
                    "Brace your abs and keep your hips and shoulders facing forward.",
                    "Press the handle straight out until your arms are extended.",
                    "Hold for a moment while the cable tries to twist you.",
                    "Bring the handle back to your chest and finish all reps before switching sides.",
                ),
                cues = listOf(
                    "Do not let the cable turn you",
                    "Squeeze glutes and stay tall",
                ),
                commonMistakes = listOf(
                    "Letting the torso rotate toward the cable",
                    "Leaning away from the anchor",
                    "Using a weight so heavy the press becomes a shove",
                ),
            ),
        )
        put(
            "kip-up",
            ExerciseGuide(
                setup = "Lie on your back on a firm, flat surface with your legs straight and your arms by your sides.",
                steps = listOf(
                    "Swing your legs up and back overhead while pushing your hands into the floor by your ears.",
                    "Snap your hips and legs forward fast.",
                    "Land on your feet with your knees bent.",
                    "Stand up tall.",
                    "Practice the leg swing on its own before you try the full move.",
                ),
                cues = listOf(
                    "Snap the hips forward",
                    "Hands flat by the ears",
                ),
                commonMistakes = listOf(
                    "Not generating enough momentum",
                    "Landing with the hips too far back",
                    "Using the neck to push",
                ),
            ),
        )
        put(
            "handstand-to-bridge",
            ExerciseGuide(
                setup = "Warm up your shoulders, wrists and spine well, and practice on a soft mat with a spotter if possible.",
                steps = listOf(
                    "Kick up into a handstand and hold it steady.",
                    "Open your shoulders and let your feet drift over your head.",
                    "Arch your back and lower your feet toward the floor in a controlled curve.",
                    "Land in a bridge with your hands and feet on the floor.",
                    "Practice the bridge and wall handstand separately first.",
                ),
                cues = listOf(
                    "Reach long through the arms",
                    "Lower slowly",
                ),
                commonMistakes = listOf(
                    "Collapsing into the lower back",
                    "Rushing the lowering phase",
                    "Skipping the bridge strength work",
                ),
            ),
        )
        put(
            "human flag",
            ExerciseGuide(
                setup = "Stand beside a vertical pole or post and grip it with one hand high and the other low.",
                steps = listOf(
                    "Push the low hand into the pole and pull with the top hand.",
                    "Lift your body off the floor sideways.",
                    "Keep your body in a straight line from head to feet.",
                    "Hold as long as you can control.",
                    "Progress by starting with tucked knees or one foot supported.",
                ),
                cues = listOf(
                    "Pull with the top arm, push with the bottom",
                    "Body stiff as a board",
                ),
                commonMistakes = listOf(
                    "Bending at the waist",
                    "Letting the hips sag",
                    "Loose grip",
                ),
            ),
        )
        put(
            "hollow hold",
            ExerciseGuide(
                setup = "Lie on your back with your legs straight and arms overhead.",
                steps = listOf(
                    "Press your lower back into the floor and brace your abs.",
                    "Lift your head, shoulders and legs a few inches off the floor.",
                    "Keep your lower back flat the whole time.",
                    "Hold for the target time while breathing steadily.",
                    "Progress by bending the knees and arms in to make it easier.",
                ),
                cues = listOf(
                    "Lower back stays on the floor",
                    "Long body, banana shape",
                ),
                commonMistakes = listOf(
                    "Lower back arching",
                    "Holding your breath",
                    "Legs too low for your strength",
                ),
            ),
        )
        put(
            "straddle l-sit",
            ExerciseGuide(
                setup = "Sit on the floor or between parallettes with your legs spread wide and your hands planted between your thighs, arms straight.",
                steps = listOf(
                    "Press down through your hands and lift your seat off the surface.",
                    "Spread your legs wide and lift them above parallel with the floor.",
                    "Pull your thighs up toward your chest with your stomach and hip flexors.",
                    "Keep your shoulders pushed down and breathe steadily.",
                    "Lower the legs to the floor under control.",
                ),
                cues = listOf(
                    "Push the floor away",
                    "Legs high, not just wide",
                    "Compress: thighs toward the chest",
                ),
                commonMistakes = listOf(
                    "Letting the legs sit at or below parallel",
                    "Bending the knees",
                    "Leaning back so the hips slide behind the hands",
                ),
            ),
        )
        put(
            "v-sit",
            ExerciseGuide(
                setup = "Sit between parallettes or on the floor with your hands flat beside your hips, fingers forward, legs straight.",
                steps = listOf(
                    "Press down through straight arms and lift your hips and legs off the floor, as in an L-sit.",
                    "Push your shoulders down and lean your torso slightly back.",
                    "Lift your straight legs higher than horizontal, toes reaching toward your face.",
                    "Keep pushing the floor away so your hips stay up between your hands.",
                    "Hold for the set time, then lower to an L-sit and sit down.",
                ),
                cues = listOf(
                    "Push the floor away",
                    "Legs high, knees locked",
                    "Shoulders down, hips forward",
                ),
                commonMistakes = listOf(
                    "Sitting on the floor in a boat pose instead of supporting on the hands",
                    "Bending the arms or shrugging up into the ears",
                    "Letting the legs drop back to horizontal",
                ),
            ),
        )
        put(
            "manna",
            ExerciseGuide(
                setup = "Sit in a pike with your hands on the floor or parallettes beside your hips.",
                steps = listOf(
                    "Press down through your hands and lift your hips and legs off the floor.",
                    "Rotate your hips up and back so your legs rise above your hands.",
                    "Hold your body in a horizontal line with straight arms.",
                    "Keep your shoulders pushed down and slightly back.",
                    "Progress by working on a strong L-sit first.",
                ),
                cues = listOf(
                    "Push the floor away",
                    "Hips high",
                ),
                commonMistakes = listOf(
                    "Bent arms",
                    "Shrugging",
                    "Skipping the L-sit prerequisite",
                ),
            ),
        )
        put(
            "toes-to-bar",
            ExerciseGuide(
                setup = "Hang from a bar with an overhand grip and arms straight.",
                steps = listOf(
                    "Brace your abs and keep your shoulders pulled down.",
                    "Lift your toes to the bar in one controlled move.",
                    "Touch the bar with your toes.",
                    "Lower your legs under control to a dead hang.",
                    "Reset your body before the next rep.",
                ),
                cues = listOf(
                    "Curl hips up, do not swing",
                    "Control the descent",
                ),
                commonMistakes = listOf(
                    "Swinging wildly",
                    "Skipping the hang between reps",
                    "Bending the knees to cheat",
                ),
            ),
        )
        put(
            "deep squat hold",
            ExerciseGuide(
                setup = "Stand with your feet slightly wider than shoulder-width and your toes turned slightly out.",
                steps = listOf(
                    "Sit down as low as comfortable, keeping your whole foot flat on the floor.",
                    "Keep your chest up and push your knees out over your toes.",
                    "Hold the bottom position while breathing calmly.",
                    "Hold onto a post or door frame for balance if needed.",
                    "Stand up when the time is done.",
                ),
                cues = listOf(
                    "Chest tall, knees out",
                    "Heels stay down",
                ),
                commonMistakes = listOf(
                    "Heels lifting off the floor",
                    "Knees caving in",
                    "Rounding the back too much",
                ),
            ),
        )
        put(
            "pancake",
            ExerciseGuide(
                setup = "Sit on the floor with your legs spread wide and your knees pointing at the ceiling.",
                steps = listOf(
                    "Sit tall on your sit bones.",
                    "Hinge forward from your hips with a long back.",
                    "Walk your hands ahead of you as far as is comfortable.",
                    "Hold with steady breathing and relaxed shoulders.",
                    "Return slowly to upright.",
                ),
                cues = listOf(
                    "Hinge from the hips",
                    "Long spine, not round",
                ),
                commonMistakes = listOf(
                    "Rounding the back to reach farther",
                    "Rolling the knees inward",
                    "Forcing the stretch",
                ),
            ),
        )
        put(
            "bridge",
            ExerciseGuide(
                setup = "Lie on your back with knees bent, feet flat near your hips and hands beside your ears.",
                steps = listOf(
                    "Press through your hands and feet to lift your hips.",
                    "Straighten your arms and push your chest toward the wall behind you.",
                    "Press your hips high and let your head hang between your arms.",
                    "Hold for the target time while breathing steadily.",
                    "Come down slowly and rest.",
                ),
                cues = listOf(
                    "Push the floor away",
                    "Open the chest and shoulders",
                ),
                commonMistakes = listOf(
                    "Collapsing into the lower back",
                    "Keeping the elbows bent",
                    "Knees falling out",
                ),
            ),
        )
        put(
            "front split",
            ExerciseGuide(
                setup = "Warm up with a few lunges and light leg swings first.",
                steps = listOf(
                    "Kneel with your front leg stretched forward.",
                    "Slide your front heel forward and your back knee backward until you feel a firm stretch.",
                    "Keep your hips level and facing forward.",
                    "Hold with steady breathing and use your hands or blocks for support.",
                    "Release slowly and switch legs.",
                ),
                cues = listOf(
                    "Hips square",
                    "Ease in, never bounce",
                ),
                commonMistakes = listOf(
                    "Twisting the hips open",
                    "Forcing the stretch",
                    "Bouncing",
                ),
            ),
        )
        put(
            "stand-to-stand bridge",
            ExerciseGuide(
                setup = "Practice on a soft surface after a good warm-up, ideally with a spotter.",
                steps = listOf(
                    "Stand with your back to the floor, arms overhead.",
                    "Lean back slowly, reaching your hands toward the floor behind you.",
                    "Place your hands on the floor and settle into a bridge.",
                    "Press back up by pushing through your feet and hands.",
                    "Stand up tall to finish.",
                ),
                cues = listOf(
                    "Reach overhead and back",
                    "Move slowly",
                ),
                commonMistakes = listOf(
                    "Dropping into the bridge",
                    "Bending the arms",
                    "Locking the knees at the bottom",
                ),
            ),
        )
        put(
            "german hang",
            ExerciseGuide(
                setup = "Hang from rings or a bar with an overhand grip, low enough that your feet can reach the floor, and warm up your shoulders first.",
                steps = listOf(
                    "Hang with your arms straight.",
                    "Tuck your knees and rotate your body backward through your arms, as in a skin the cat.",
                    "Lower your hips toward the floor behind you slowly until your arms point back, stopping well before any pinching in the shoulders.",
                    "Keep your shoulders relaxed and your grip firm.",
                    "Hold briefly with a comfortable stretch, then return slowly.",
                ),
                cues = listOf(
                    "Only go as far as comfortable",
                    "Keep a firm grip",
                ),
                commonMistakes = listOf(
                    "Dropping into the position quickly",
                    "Forcing range your shoulders are not ready for",
                    "Gripping too loosely",
                ),
            ),
        )
        put(
            "wrist prep",
            ExerciseGuide(
                setup = "Kneel on all fours with your hands flat on the floor under your shoulders.",
                steps = listOf(
                    "Place your palms on the floor with fingers pointing forward or back.",
                    "Rock your weight gently forward and backward over your hands.",
                    "Hold the stretch for the target time, keeping the heel of the hand on the floor.",
                    "Try fingers pointing sideways or turned out as your wrists allow.",
                    "Ease off if you feel sharp pain.",
                ),
                cues = listOf(
                    "Move slowly, no bouncing",
                    "Palms stay flat",
                ),
                commonMistakes = listOf(
                    "Rushing into deep positions",
                    "Letting the palms lift off the floor",
                ),
            ),
        )
    }

    // ---- Progression steps added by the October 2026 skill audit ----
    private fun MutableMap<String, ExerciseGuide>.progressionSteps() {
        put(
            "negative pull-up",
            ExerciseGuide(
                setup = "Stand on a box under a bar so you can start with your chin already over it, hands just wider than your shoulders.",
                steps = listOf(
                    "Step or jump to the top position, chin over the bar and elbows bent.",
                    "Take your feet off the box and hold the top for a moment.",
                    "Lower yourself slowly, taking about five seconds to reach straight arms.",
                    "Finish in a full dead hang with your shoulders still active.",
                    "Step back onto the box and repeat.",
                ),
                cues = listOf(
                    "Slow all the way down",
                    "Shoulders stay down, away from the ears",
                    "Legs still, no swing",
                ),
                commonMistakes = listOf(
                    "Dropping through the bottom half",
                    "Jumping into the next rep without resetting",
                    "Letting the shoulders shrug up at the bottom",
                ),
            ),
        )
        put(
            "elevated pike push-up",
            ExerciseGuide(
                setup = "Put your feet on a box or bench about hip height and walk your hands back until your hips sit high over your shoulders.",
                steps = listOf(
                    "Set your hands shoulder-width apart, fingers spread.",
                    "Bend your elbows and lower the top of your head toward the floor in front of your hands.",
                    "Keep your hips stacked over your hands as you lower.",
                    "Touch your head lightly to the floor.",
                    "Press back up until your arms are straight.",
                ),
                cues = listOf(
                    "Hips over hands",
                    "Head and hands make a triangle",
                    "Elbows angled forward, not flared",
                ),
                commonMistakes = listOf(
                    "Letting the hips drift back toward the box",
                    "Flaring the elbows wide",
                    "Shortening the range as the set gets hard",
                ),
            ),
        )
        put(
            "wall hspu negative",
            ExerciseGuide(
                setup = "Walk your feet up a wall into a chest-to-wall handstand, hands about a hand-length from the wall, a folded mat under your head.",
                steps = listOf(
                    "Lock your arms and hold a straight line with your chest facing the wall.",
                    "Bend your elbows and lower slowly, taking about five seconds.",
                    "Keep your elbows tracking forward and your body tight.",
                    "Touch your head lightly to the mat.",
                    "Come down off the wall, then walk back up for the next rep.",
                ),
                cues = listOf(
                    "Five slow seconds down",
                    "Ribs in, glutes squeezed",
                    "Head lands in front of the hands",
                ),
                commonMistakes = listOf(
                    "Collapsing the last part of the way",
                    "Letting the lower back arch off the wall",
                    "Trying to press back up before the negatives are smooth",
                ),
            ),
        )
        put(
            "planche lean",
            ExerciseGuide(
                setup = "Get into a push-up position on the floor or parallettes, fingers turned slightly out, feet together on the floor.",
                steps = listOf(
                    "Lock your elbows and push the floor away so your upper back rounds slightly.",
                    "Lean your shoulders forward past your wrists while your feet stay on the floor.",
                    "Point your toes so you can lean further.",
                    "Hold the lean with straight arms for the set time.",
                    "Lean further forward over time as the hold gets easy.",
                ),
                cues = listOf(
                    "Arms straight, always",
                    "Shoulders far past the hands",
                    "Push the floor away",
                ),
                commonMistakes = listOf(
                    "Bending the elbows to lean further",
                    "Letting the hips sag",
                    "Shrugging the shoulders toward the ears",
                ),
            ),
        )
        put(
            "elbow lever",
            ExerciseGuide(
                setup = "Squat with your hands flat on the floor, fingers pointing out to the sides or back, elbows close together.",
                steps = listOf(
                    "Bend your elbows and set them into your belly just beside your hip bones.",
                    "Lean forward so your weight moves onto your hands.",
                    "Straighten your legs behind you and lift your feet off the floor.",
                    "Hold your body level from head to feet, balancing on your elbows.",
                    "Lower your feet under control.",
                ),
                cues = listOf(
                    "Elbows dig into the hips",
                    "Squeeze the legs together",
                    "Look slightly ahead",
                ),
                commonMistakes = listOf(
                    "Placing the elbows too far apart to support the hips",
                    "Letting the legs drop below the head",
                    "Holding past the point where the wrists hurt",
                ),
            ),
        )
        put(
            "rto support hold",
            ExerciseGuide(
                setup = "Set rings at hip height and press up to a support with straight arms, rings beside your hips.",
                steps = listOf(
                    "Lock your elbows and press your shoulders down.",
                    "Turn the rings out until your palms face forward.",
                    "Keep the rings close to your sides and your body still.",
                    "Hold the turned-out position for the set time.",
                    "Turn back to neutral and step down.",
                ),
                cues = listOf(
                    "Palms forward, elbows locked",
                    "Shoulders down, chest proud",
                    "Squeeze the rings into your sides",
                ),
                commonMistakes = listOf(
                    "Letting the rings turn back in as you tire",
                    "Bending the elbows to make it easier",
                    "Shrugging up into the ears",
                ),
            ),
        )
        put(
            "banded iron cross",
            ExerciseGuide(
                setup = "Hang rings high, loop a resistance band over the ring straps so it runs under your arms, and press to a ring support.",
                steps = listOf(
                    "Turn the rings out with straight arms.",
                    "Lower slowly, letting the arms open out to the sides as the band takes some of the load.",
                    "Stop with your arms straight out to the sides at shoulder height.",
                    "Hold the cross shape with your elbows locked.",
                    "Press back up to the support, using a lighter band as you get stronger.",
                ),
                cues = listOf(
                    "Elbows locked the whole time",
                    "Pull the rings down and in",
                    "Lower slowly",
                ),
                commonMistakes = listOf(
                    "Bending the elbows, which loads them badly",
                    "Dropping into the cross fast",
                    "Using a band so strong the muscles never work",
                ),
            ),
        )
        put(
            "tuck human flag",
            ExerciseGuide(
                setup = "Stand beside a vertical pole, grip it with your top hand at head height and your bottom hand at hip height.",
                steps = listOf(
                    "Pull hard with the top arm and press hard with the bottom arm, both arms straight.",
                    "Jump or kick your feet up off the floor.",
                    "Tuck your knees in toward your chest.",
                    "Hold your hips level with your hands, body sideways to the floor.",
                    "Lower your feet under control and switch sides.",
                ),
                cues = listOf(
                    "Pull with the top, push with the bottom",
                    "Arms straight",
                    "Hips stay up",
                ),
                commonMistakes = listOf(
                    "Bending the bottom arm",
                    "Letting the hips sink below the hands",
                    "Hands set too close together on the pole",
                ),
            ),
        )
        put(
            "supported pistol squat",
            ExerciseGuide(
                setup = "Stand on one leg beside a post, door frame or rack upright and hold it lightly with one hand.",
                steps = listOf(
                    "Lift your other leg straight out in front of you.",
                    "Sit down on the standing leg as deep as you can go, heel flat.",
                    "Use the hand only for balance, not to pull yourself up.",
                    "Stand back up by pushing through the whole foot.",
                    "Finish the reps, then switch legs.",
                ),
                cues = listOf(
                    "Heel stays down",
                    "Knee tracks over the toes",
                    "Hand balances, legs lift",
                ),
                commonMistakes = listOf(
                    "Pulling up with the arm",
                    "Letting the knee cave in",
                    "Stopping short of full depth",
                ),
            ),
        )
        put(
            "one-leg back lever",
            ExerciseGuide(
                setup = "Hang from rings or a bar and get into an advanced tuck back lever.",
                steps = listOf(
                    "Start in a tucked inverted hang.",
                    "Lower until your back is flat.",
                    "Extend one leg straight back in line with your body while the other stays tucked.",
                    "Hold with straight arms and level hips.",
                    "Return to the inverted hang and switch legs.",
                ),
                cues = listOf(
                    "Keep your arms straight and your body in a flat line",
                    "Push your chest forward and squeeze your glutes",
                ),
                commonMistakes = listOf(
                    "Letting the hips drop or the back arch hard",
                    "Bending the elbows",
                    "Twisting the hips toward the extended leg",
                ),
            ),
        )
        put(
            "tuck l-sit",
            ExerciseGuide(
                setup = "Sit between parallettes, two boxes or on the floor with your hands beside your hips, arms straight.",
                steps = listOf(
                    "Press down through your hands and lift your seat off the surface.",
                    "Pull your knees up tight toward your chest.",
                    "Keep your feet off the floor and your shoulders pushed down.",
                    "Hold for the set time, breathing steadily.",
                    "Extend one leg at a time as it gets easy.",
                ),
                cues = listOf(
                    "Push the floor away",
                    "Knees to chest",
                    "Shoulders down, chest tall",
                ),
                commonMistakes = listOf(
                    "Shrugging the shoulders up",
                    "Letting the feet touch down",
                    "Leaning back so the hips slide behind the hands",
                ),
            ),
        )
        put(
            "half split",
            ExerciseGuide(
                setup = "Kneel on a soft mat and step one foot forward into a lunge, then shift your hips back over the back knee.",
                steps = listOf(
                    "Straighten the front leg with the heel on the floor and toes up.",
                    "Keep your hips square and stacked over the back knee.",
                    "Fold your chest toward the front knee with a long back.",
                    "Hold with steady breathing for the set time.",
                    "Release slowly and switch legs.",
                ),
                cues = listOf(
                    "Hips square",
                    "Long back, fold from the hips",
                    "Ease in, never bounce",
                ),
                commonMistakes = listOf(
                    "Rounding the back to reach the knee",
                    "Letting the hips twist open",
                    "Bending the front knee to cheat the range",
                ),
            ),
        )
    }

    // ---- Activities ----
    private fun MutableMap<String, ExerciseGuide>.activities() {
        put(
            "running",
            ExerciseGuide(
                setup = "Wear running shoes and pick a flat route or path.",
                steps = listOf(
                    "Warm up for 5 to 10 minutes at an easy pace first.",
                    "Start at a pace where you can speak in short sentences.",
                    "Land with your foot under your body, not far in front.",
                    "Keep your arms bent and swinging forward and back.",
                    "Hold the pace for your target time or distance.",
                    "Cool down for 5 minutes and log your time.",
                ),
                cues = listOf(
                    "Tall posture, relaxed shoulders",
                    "Quick, light steps",
                ),
                commonMistakes = listOf(
                    "Starting too fast",
                    "Overstriding with the foot far ahead",
                ),
            ),
        )
        put(
            "trail running",
            ExerciseGuide(
                setup = "Wear trail shoes and choose a route you can see well.",
                steps = listOf(
                    "Warm up for 5 to 10 minutes at an easy pace first.",
                    "Start slower than you would on the road.",
                    "Shorten your stride on rough or steep ground.",
                    "Walk the steep climbs and run the flats and descents.",
                    "Keep your eyes a few steps ahead to pick your footing.",
                    "Cool down for 5 minutes and log your time.",
                ),
                cues = listOf(
                    "Short, quick steps",
                    "Look ahead, not at your feet",
                ),
                commonMistakes = listOf(
                    "Running steep climbs at full effort",
                    "Staring straight down at the ground",
                ),
            ),
        )
        put(
            "cycling",
            ExerciseGuide(
                setup = "Set the saddle so your knee is slightly bent at the bottom of the pedal stroke.",
                steps = listOf(
                    "Warm up for 5 to 10 minutes at an easy pace first.",
                    "Start in an easy gear and spin at a steady rhythm.",
                    "Push and pull through the whole pedal circle.",
                    "Raise the gear or pace for harder efforts.",
                    "Keep your hands light on the bars.",
                    "Cool down for 5 minutes and log your time.",
                ),
                cues = listOf(
                    "Smooth circles, not stomps",
                    "Relaxed grip and shoulders",
                ),
                commonMistakes = listOf(
                    "Saddle too low or too high",
                    "Grinding a gear that is too heavy",
                ),
            ),
        )
        put(
            "rowing",
            ExerciseGuide(
                setup = "Strap in your feet, sit tall and grip the handle with straight arms.",
                steps = listOf(
                    "Warm up for 5 to 10 minutes at an easy pace first.",
                    "Drive with your legs first.",
                    "Lean back slightly, then pull the handle to your lower ribs.",
                    "Reverse the order: arms out, lean forward, then bend the knees.",
                    "Rest briefly at the front, then repeat at a steady rate.",
                    "Cool down for 5 minutes and log your time.",
                ),
                cues = listOf(
                    "Legs, body, arms on the way back",
                    "Arms, body, legs on the way in",
                ),
                commonMistakes = listOf(
                    "Pulling with the arms before the legs have pushed",
                    "Rounding the lower back at the catch",
                ),
            ),
        )
        put(
            "hiking",
            ExerciseGuide(
                setup = "Wear sturdy footwear and carry water.",
                steps = listOf(
                    "Warm up for 5 to 10 minutes at an easy pace first.",
                    "Start at an easy pace on the flat.",
                    "Take short steps up the hills and keep your weight over your feet.",
                    "Step down gently, with knees soft, on descents.",
                    "Take short breaks to drink and eat.",
                    "Cool down for 5 minutes and log your time.",
                ),
                cues = listOf(
                    "Steady breathing",
                    "Short steps uphill",
                ),
                commonMistakes = listOf(
                    "Going out too hard on the first climb",
                    "Wearing worn-out or unsuitable shoes",
                ),
            ),
        )
        put(
            "walking",
            ExerciseGuide(
                setup = "Wear comfortable shoes and pick a safe route.",
                steps = listOf(
                    "Warm up for 5 to 10 minutes at an easy pace first.",
                    "Stand tall with your eyes ahead.",
                    "Walk at a brisk pace where you can talk but not sing.",
                    "Swing your arms naturally.",
                    "Keep going for your target time or distance.",
                    "Cool down for 5 minutes and log your time.",
                ),
                cues = listOf(
                    "Roll heel to toe",
                    "Relaxed shoulders",
                ),
                commonMistakes = listOf(
                    "Slouching or looking down at a phone",
                    "Strolling too slowly to raise your heart rate",
                ),
            ),
        )
        put(
            "skipping",
            ExerciseGuide(
                setup = "Hold the rope handles at hip height and stand on a smooth, forgiving surface.",
                steps = listOf(
                    "Warm up with 2 minutes of easy bounces.",
                    "Turn the rope with your wrists, not your arms.",
                    "Hop just high enough to clear the rope, landing on the balls of your feet.",
                    "Work in rounds, for example 30 to 60 seconds on and a short rest.",
                    "Cool down with a slow walk.",
                ),
                cues = listOf(
                    "Small, quick hops",
                    "Elbows close, hands at hip height",
                ),
                commonMistakes = listOf(
                    "Jumping too high",
                    "Swinging the rope with big arm circles",
                ),
            ),
        )
        put(
            "weighted skipping",
            ExerciseGuide(
                setup = "Use a light weighted rope and a smooth, forgiving surface.",
                steps = listOf(
                    "Warm up with 2 minutes of an unweighted rope or easy bounces.",
                    "Turn the rope with your wrists and keep your elbows tucked.",
                    "Hop low and land softly on the balls of your feet.",
                    "Work in short rounds, such as 20 to 40 seconds with rests.",
                    "Stop a round when your wrists or shoulders tire and your form slips.",
                ),
                cues = listOf(
                    "Stay tall, shoulders down",
                    "Quiet landings",
                ),
                commonMistakes = listOf(
                    "Using a rope that is too heavy",
                    "Letting the shoulders hunch as you tire",
                ),
            ),
        )
        put(
            "jump rope intervals",
            ExerciseGuide(
                setup = "Hold the rope handles at hip height and stand on a smooth, forgiving surface.",
                steps = listOf(
                    "Warm up with 2 minutes of easy bounces.",
                    "Skip hard for a set time, such as 30 seconds.",
                    "Rest for a set time, such as 30 seconds.",
                    "Repeat for your planned number of rounds.",
                    "Cool down with slow bounces or a walk.",
                ),
                cues = listOf(
                    "Fast but small hops",
                    "Turn the rope from the wrists",
                ),
                commonMistakes = listOf(
                    "Going all-out too early and fading",
                    "Landing flat-footed or on the heels",
                ),
            ),
        )
        put(
            "stair climbing",
            ExerciseGuide(
                setup = "Use a stair climber or a flight of stairs with a handrail nearby.",
                steps = listOf(
                    "Warm up for 5 to 10 minutes at an easy pace first.",
                    "Start with a slow, steady pace.",
                    "Place your whole foot on each step and push through it.",
                    "Stand tall and use the rail only for balance.",
                    "Build to a pace you can hold for your target time.",
                    "Cool down for 5 minutes and log your time.",
                ),
                cues = listOf(
                    "Whole foot on the step",
                    "Upright chest",
                ),
                commonMistakes = listOf(
                    "Leaning heavily on the rails",
                    "Taking tiny toe-only steps",
                ),
            ),
        )
        put(
            "elliptical",
            ExerciseGuide(
                setup = "Stand tall on the pedals and hold the handles lightly.",
                steps = listOf(
                    "Warm up for 5 to 10 minutes at an easy pace first.",
                    "Start at low resistance and a smooth pace.",
                    "Push and pull the handles in rhythm with your legs.",
                    "Raise resistance or pace for harder work.",
                    "Keep your weight over your feet.",
                    "Cool down for 5 minutes and log your time.",
                ),
                cues = listOf(
                    "Smooth, even strides",
                    "Stay upright",
                ),
                commonMistakes = listOf(
                    "Hanging on the handles for support",
                    "Leaning forward with a rounded back",
                ),
            ),
        )
        put(
            "assault bike",
            ExerciseGuide(
                setup = "Adjust the saddle so your knee is slightly bent at the bottom of the pedal stroke.",
                steps = listOf(
                    "Warm up for 5 to 10 minutes at an easy pace first.",
                    "Start easy to get a feel for the pedals and handles.",
                    "Push and pull the handles while you pedal.",
                    "For intervals, go hard for a set time, then rest fully.",
                    "Keep breathing rhythmically throughout.",
                    "Cool down for 5 minutes and log your time.",
                ),
                cues = listOf(
                    "Use arms and legs together",
                    "Sit tall",
                ),
                commonMistakes = listOf(
                    "Starting every interval at 100 percent and fading",
                    "Only using the legs",
                ),
            ),
        )
        put(
            "treadmill",
            ExerciseGuide(
                setup = "Stand on the side rails, start the belt slowly, then step on.",
                steps = listOf(
                    "Warm up for 5 to 10 minutes at an easy pace first.",
                    "Begin walking and raise the speed gradually.",
                    "Run near the middle of the belt with a relaxed arm swing.",
                    "Keep a low incline, around 1 percent, for flat running.",
                    "Slow down gradually before stepping off.",
                    "Cool down for 5 minutes and log your time.",
                ),
                cues = listOf(
                    "Tall posture",
                    "Light, quick steps",
                ),
                commonMistakes = listOf(
                    "Holding the rails while running",
                    "Running too far forward on the belt",
                ),
            ),
        )
        put(
            "ski erg",
            ExerciseGuide(
                setup = "Stand facing the machine with feet hip-width apart and grip the handles overhead.",
                steps = listOf(
                    "Warm up for 5 to 10 minutes at an easy pace first.",
                    "Start with arms up and a slight bend in the knees.",
                    "Pull the handles down toward your hips while you hinge forward and bend your knees.",
                    "Let your arms rise back overhead as you stand tall.",
                    "Repeat at a steady rhythm.",
                    "Cool down for 5 minutes and log your time.",
                ),
                cues = listOf(
                    "Pull with your whole body",
                    "Tall at the top, hinged at the bottom",
                ),
                commonMistakes = listOf(
                    "Pulling with the arms only",
                    "Rounding the back at the bottom",
                ),
            ),
        )
        put(
            "indoor cycling",
            ExerciseGuide(
                setup = "Adjust the saddle and handlebars to a comfortable position.",
                steps = listOf(
                    "Warm up for 5 to 10 minutes at an easy pace first.",
                    "Start with light resistance and a steady rhythm.",
                    "Add resistance for climbs and ease off for recovery.",
                    "Stay seated for most of the session.",
                    "Keep your upper body relaxed.",
                    "Cool down for 5 minutes and log your time.",
                ),
                cues = listOf(
                    "Smooth pedal circles",
                    "Relaxed hands",
                ),
                commonMistakes = listOf(
                    "Too much resistance too early",
                    "Bouncing in the saddle",
                ),
            ),
        )
        put(
            "versaclimber",
            ExerciseGuide(
                setup = "Stand on the pedals and hold the handles, one hand and the opposite foot forward.",
                steps = listOf(
                    "Warm up for 5 to 10 minutes at an easy pace first.",
                    "Start slowly, moving opposite arm and leg together.",
                    "Reach up and push down in a smooth climbing rhythm.",
                    "Increase the speed as you settle in.",
                    "Keep your chest tall and your core braced.",
                    "Cool down for 5 minutes and log your time.",
                ),
                cues = listOf(
                    "Opposite arm and leg together",
                    "Smooth, even strokes",
                ),
                commonMistakes = listOf(
                    "Short, jerky strokes",
                    "Hanging on the handles with locked arms",
                ),
            ),
        )
        put(
            "swimming",
            ExerciseGuide(
                setup = "Pick a lane with space and wear goggles.",
                steps = listOf(
                    "Warm up for 5 to 10 minutes at an easy pace first.",
                    "Push off the wall in a long, streamlined body line.",
                    "Reach forward and pull the water back with each arm.",
                    "Kick gently from the hips and breathe to the side every few strokes.",
                    "Rest at the wall between lengths.",
                    "Cool down for 5 minutes and log your time.",
                ),
                cues = listOf(
                    "Long, smooth strokes",
                    "Breathe out under the water",
                ),
                commonMistakes = listOf(
                    "Lifting the head to look forward",
                    "Kicking from the knees only",
                ),
            ),
        )
        put(
            "water polo",
            ExerciseGuide(
                setup = "Join a game or drill session with a ball, in water deep enough to tread.",
                steps = listOf(
                    "Warm up for 5 to 10 minutes at an easy pace first.",
                    "Tread water with an egg-beater kick to stay high.",
                    "Swim short bursts with the head up to follow the ball.",
                    "Pass and shoot with a quick arm motion.",
                    "Take rests as needed because effort is high.",
                    "Cool down for 5 minutes and log your time.",
                ),
                cues = listOf(
                    "Strong kick keeps you high",
                    "Quick bursts, then recover",
                ),
                commonMistakes = listOf(
                    "Relying on the arms to stay afloat",
                    "Overreaching with the throwing shoulder",
                ),
            ),
        )
        put(
            "bouldering",
            ExerciseGuide(
                setup = "Wear climbing shoes, chalk up and check the mat below the problem.",
                steps = listOf(
                    "Warm up on easy problems for 10 minutes.",
                    "Study the route before you start.",
                    "Keep arms mostly straight and push with your legs.",
                    "Rest 2 to 3 minutes between attempts.",
                    "Stop when your grip fades or your form slips.",
                ),
                cues = listOf(
                    "Hips close to the wall",
                    "Watch your feet",
                ),
                commonMistakes = listOf(
                    "Pulling with bent arms the whole time",
                    "Skipping the warm-up",
                ),
            ),
        )
        put(
            "sport climbing",
            ExerciseGuide(
                setup = "Wear climbing shoes and a harness and check your knot and partner.",
                steps = listOf(
                    "Warm up on easy routes first.",
                    "Plan the route from the ground.",
                    "Climb with straight arms, resting on good holds.",
                    "Clip each draw calmly.",
                    "Rest between routes.",
                ),
                cues = listOf(
                    "Straight arms, quiet feet",
                    "Breathe and shake out on rests",
                ),
                commonMistakes = listOf(
                    "Gripping too hard",
                    "Climbing without a safety check",
                ),
            ),
        )
        put(
            "top rope",
            ExerciseGuide(
                setup = "Wear climbing shoes and a harness and check the knot and belayer.",
                steps = listOf(
                    "Warm up on an easy route.",
                    "Plan your moves from the ground.",
                    "Climb with straight arms and push with your legs.",
                    "Tell your belayer when you want to be lowered.",
                    "Rest between climbs.",
                ),
                cues = listOf(
                    "Straight arms, quiet feet",
                    "Look for the next hold",
                ),
                commonMistakes = listOf(
                    "Skipping the partner check",
                    "Pulling only with the arms",
                ),
            ),
        )
        put(
            "football (soccer)",
            ExerciseGuide(
                setup = "Wear boots suited to the pitch.",
                steps = listOf(
                    "Warm up for 5 to 10 minutes at an easy pace first.",
                    "Jog and do dynamic leg swings.",
                    "Play at your normal intensity with short sprints and recovery.",
                    "Stay hydrated.",
                    "Cool down for 5 minutes and log your time.",
                ),
                cues = listOf(
                    "Stay on your toes",
                    "Move into space",
                ),
                commonMistakes = listOf(
                    "Sprinting cold",
                    "Skipping hydration",
                ),
            ),
        )
        put(
            "basketball",
            ExerciseGuide(
                setup = "Wear court shoes with ankle support.",
                steps = listOf(
                    "Warm up for 5 to 10 minutes at an easy pace first.",
                    "Warm up with jogging and shots.",
                    "Play or run drills with short bursts.",
                    "Land softly after jumps.",
                    "Cool down for 5 minutes and log your time.",
                ),
                cues = listOf(
                    "Land softly",
                    "Stay low on defence",
                ),
                commonMistakes = listOf(
                    "Landing stiff-legged",
                    "Skipping the warm-up",
                ),
            ),
        )
        put(
            "tennis",
            ExerciseGuide(
                setup = "Wear court shoes and bring plenty of water.",
                steps = listOf(
                    "Warm up for 5 to 10 minutes at an easy pace first.",
                    "Warm up with mini rallies.",
                    "Split-step before each shot.",
                    "Turn your hips and shoulders through each swing.",
                    "Cool down for 5 minutes and log your time.",
                ),
                cues = listOf(
                    "Split-step",
                    "Rotate through the hips",
                ),
                commonMistakes = listOf(
                    "Arm-only swings",
                    "Flat-footed movement",
                ),
            ),
        )
        put(
            "badminton",
            ExerciseGuide(
                setup = "Wear court shoes.",
                steps = listOf(
                    "Warm up for 5 to 10 minutes at an easy pace first.",
                    "Warm up with arm circles and light rallies.",
                    "Split-step before each shot.",
                    "Use your wrist and forearm for quick shots.",
                    "Cool down for 5 minutes and log your time.",
                ),
                cues = listOf(
                    "Light feet",
                    "Quick recovery to the centre",
                ),
                commonMistakes = listOf(
                    "Staying flat-footed",
                    "Over-swinging",
                ),
            ),
        )
        put(
            "squash",
            ExerciseGuide(
                setup = "Wear court shoes and protective eyewear.",
                steps = listOf(
                    "Warm up for 5 to 10 minutes at an easy pace first.",
                    "Warm up with easy rallies.",
                    "Return to the centre of the court after each shot.",
                    "Lunge to reach wide shots.",
                    "Cool down for 5 minutes and log your time.",
                ),
                cues = listOf(
                    "Return to the T",
                    "Low lunges",
                ),
                commonMistakes = listOf(
                    "Skipping eye protection",
                    "Staying out of position",
                ),
            ),
        )
        put(
            "cricket",
            ExerciseGuide(
                setup = "Wear suitable protection.",
                steps = listOf(
                    "Warm up for 5 to 10 minutes at an easy pace first.",
                    "Warm up with jogging and arm swings.",
                    "Use your legs and trunk, not just the arm, when bowling or batting.",
                    "Stay hydrated.",
                    "Cool down for 5 minutes and log your time.",
                ),
                cues = listOf(
                    "Drive from the legs",
                    "Rotate the trunk",
                ),
                commonMistakes = listOf(
                    "Bowling cold",
                    "Using only the arm",
                ),
            ),
        )
        put(
            "rugby",
            ExerciseGuide(
                setup = "Wear a mouthguard.",
                steps = listOf(
                    "Warm up for 5 to 10 minutes at an easy pace first.",
                    "Warm up with jogging and mobility.",
                    "Keep your body low in contact.",
                    "Practise tackling technique with a coach.",
                    "Cool down for 5 minutes and log your time.",
                ),
                cues = listOf(
                    "Low and strong",
                    "Head up",
                ),
                commonMistakes = listOf(
                    "Tackling with the head down",
                    "Skipping the warm-up",
                ),
            ),
        )
        put(
            "volleyball",
            ExerciseGuide(
                setup = "Wear court shoes.",
                steps = listOf(
                    "Warm up for 5 to 10 minutes at an easy pace first.",
                    "Warm up with jumps and shoulder circles.",
                    "Bend the knees before a jump.",
                    "Land softly on both feet.",
                    "Cool down for 5 minutes and log your time.",
                ),
                cues = listOf(
                    "Soft landings",
                    "Hit with a full arm swing",
                ),
                commonMistakes = listOf(
                    "Landing stiff",
                    "Skipping the shoulder warm-up",
                ),
            ),
        )
        put(
            "table tennis",
            ExerciseGuide(
                setup = "Stand a little back from the table with knees bent.",
                steps = listOf(
                    "Warm up for 5 to 10 minutes at an easy pace first.",
                    "Warm up with gentle rallies.",
                    "Keep a slight knee bend and stay on your toes.",
                    "Use a short wrist and forearm swing.",
                    "Cool down for 5 minutes and log your time.",
                ),
                cues = listOf(
                    "Light feet",
                    "Short, quick strokes",
                ),
                commonMistakes = listOf(
                    "Standing flat-footed",
                    "Big arm swings",
                ),
            ),
        )
        put(
            "golf",
            ExerciseGuide(
                setup = "Bring a bag or use a cart if the course is long.",
                steps = listOf(
                    "Warm up for 5 to 10 minutes at an easy pace first.",
                    "Warm up with trunk rotations and easy swings.",
                    "Rotate through your hips and trunk.",
                    "Walk the course for extra effort.",
                    "Cool down for 5 minutes and log your time.",
                ),
                cues = listOf(
                    "Turn through the hips",
                    "Balanced finish",
                ),
                commonMistakes = listOf(
                    "Swinging hard without warming up",
                    "Using only the arms",
                ),
            ),
        )
        put(
            "boxing",
            ExerciseGuide(
                setup = "Wrap your hands and use gloves.",
                steps = listOf(
                    "Warm up for 5 to 10 minutes at an easy pace first.",
                    "Warm up with skipping and shadow boxing.",
                    "Keep your guard up and feet shoulder-width apart.",
                    "Throw punches by turning from the feet and hips.",
                    "Work in timed rounds.",
                    "Cool down for 5 minutes and log your time.",
                ),
                cues = listOf(
                    "Guard up",
                    "Turn the hips",
                ),
                commonMistakes = listOf(
                    "Dropping the guard",
                    "Punching with the arm only",
                ),
            ),
        )
        put(
            "kickboxing",
            ExerciseGuide(
                setup = "Wear wraps and gloves, plus shin guards for sparring.",
                steps = listOf(
                    "Warm up for 5 to 10 minutes at an easy pace first.",
                    "Warm up with skipping and mobility.",
                    "Keep a balanced stance.",
                    "Throw kicks by pivoting the supporting foot.",
                    "Work in rounds.",
                    "Cool down for 5 minutes and log your time.",
                ),
                cues = listOf(
                    "Pivot the supporting foot",
                    "Guard up",
                ),
                commonMistakes = listOf(
                    "Kicking without pivoting",
                    "Overreaching",
                ),
            ),
        )
        put(
            "brazilian jiu-jitsu",
            ExerciseGuide(
                setup = "Wear a gi or fitted clothing and trim your nails.",
                steps = listOf(
                    "Warm up for 5 to 10 minutes at an easy pace first.",
                    "Warm up with shrimping and rolls.",
                    "Drill technique before rolling.",
                    "Tap early.",
                    "Cool down for 5 minutes and log your time.",
                ),
                cues = listOf(
                    "Tap early",
                    "Relax between moves",
                ),
                commonMistakes = listOf(
                    "Muscling every move",
                    "Ignoring pain instead of tapping",
                ),
            ),
        )
        put(
            "wrestling",
            ExerciseGuide(
                setup = "Wear fitted clothing and use a mat.",
                steps = listOf(
                    "Warm up for 5 to 10 minutes at an easy pace first.",
                    "Warm up with mobility and bridges.",
                    "Keep your stance low.",
                    "Drill takedowns with a partner.",
                    "Cool down for 5 minutes and log your time.",
                ),
                cues = listOf(
                    "Low stance",
                    "Head up",
                ),
                commonMistakes = listOf(
                    "Standing tall",
                    "Neglecting the neck warm-up",
                ),
            ),
        )
        put(
            "judo",
            ExerciseGuide(
                setup = "Wear a gi and train on mats.",
                steps = listOf(
                    "Warm up for 5 to 10 minutes at an easy pace first.",
                    "Warm up and practise breakfalls.",
                    "Learn throws with a partner.",
                    "Keep your grip and balance.",
                    "Cool down for 5 minutes and log your time.",
                ),
                cues = listOf(
                    "Break the balance first",
                    "Tuck your chin when falling",
                ),
                commonMistakes = listOf(
                    "Falling on straight arms",
                    "Forcing throws",
                ),
            ),
        )
        put(
            "karate",
            ExerciseGuide(
                setup = "Train barefoot on a safe floor.",
                steps = listOf(
                    "Warm up for 5 to 10 minutes at an easy pace first.",
                    "Warm up with mobility.",
                    "Practise stances and kata.",
                    "Throw strikes from the hips.",
                    "Cool down for 5 minutes and log your time.",
                ),
                cues = listOf(
                    "Strong stance",
                    "Breathe out on strikes",
                ),
                commonMistakes = listOf(
                    "Locking the elbows or knees on strikes",
                    "Skipping the warm-up",
                ),
            ),
        )
        put(
            "skateboarding",
            ExerciseGuide(
                setup = "Wear a helmet and pads.",
                steps = listOf(
                    "Warm up for 5 to 10 minutes at an easy pace first.",
                    "Warm up with ankle and hip mobility.",
                    "Start with basic pushing and balance.",
                    "Bend your knees.",
                    "Learn to fall safely.",
                    "Cool down for 5 minutes and log your time.",
                ),
                cues = listOf(
                    "Knees bent",
                    "Eyes forward",
                ),
                commonMistakes = listOf(
                    "Skating without a helmet",
                    "Trying advanced tricks too soon",
                ),
            ),
        )
        put(
            "surfing",
            ExerciseGuide(
                setup = "Check conditions and use a leash.",
                steps = listOf(
                    "Warm up for 5 to 10 minutes at an easy pace first.",
                    "Warm up with shoulder circles and hip mobility.",
                    "Paddle with long strokes.",
                    "Pop up in one smooth motion.",
                    "Rest between waves.",
                    "Cool down for 5 minutes and log your time.",
                ),
                cues = listOf(
                    "Long paddle strokes",
                    "Pop up in one motion",
                ),
                commonMistakes = listOf(
                    "Skipping shoulder warm-up",
                    "Surfing beyond your skill",
                ),
            ),
        )
        put(
            "snowboarding",
            ExerciseGuide(
                setup = "Wear a helmet and wrist guards.",
                steps = listOf(
                    "Warm up for 5 to 10 minutes at an easy pace first.",
                    "Warm up.",
                    "Keep your knees bent and weight centred.",
                    "Look where you want to go.",
                    "Rest regularly.",
                    "Cool down for 5 minutes and log your time.",
                ),
                cues = listOf(
                    "Knees bent",
                    "Look ahead",
                ),
                commonMistakes = listOf(
                    "Leaning back",
                    "Skipping rests",
                ),
            ),
        )
        put(
            "skiing",
            ExerciseGuide(
                setup = "Wear a helmet and set bindings correctly.",
                steps = listOf(
                    "Warm up for 5 to 10 minutes at an easy pace first.",
                    "Warm up.",
                    "Stay in a slight crouch.",
                    "Turn using your legs, with the upper body facing downhill.",
                    "Rest regularly.",
                    "Cool down for 5 minutes and log your time.",
                ),
                cues = listOf(
                    "Hands forward",
                    "Shins against boot tongues",
                ),
                commonMistakes = listOf(
                    "Sitting back",
                    "Riding while tired",
                ),
            ),
        )
        put(
            "dancing",
            ExerciseGuide(
                setup = "Wear comfortable shoes and clothes.",
                steps = listOf(
                    "Warm up for 5 to 10 minutes at an easy pace first.",
                    "Warm up with gentle movement.",
                    "Learn the steps slowly.",
                    "Build up speed and intensity.",
                    "Cool down for 5 minutes and log your time.",
                ),
                cues = listOf(
                    "Stay light on your feet",
                    "Keep your core braced",
                ),
                commonMistakes = listOf(
                    "Starting at full intensity",
                    "Locking the knees",
                ),
            ),
        )
        put(
            "martial arts class",
            ExerciseGuide(
                setup = "Wear class-appropriate clothes.",
                steps = listOf(
                    "Warm up for 5 to 10 minutes at an easy pace first.",
                    "Warm up with the group.",
                    "Follow the instructor.",
                    "Practise technique before speed.",
                    "Cool down for 5 minutes and log your time.",
                ),
                cues = listOf(
                    "Listen to your instructor",
                    "Technique first",
                ),
                commonMistakes = listOf(
                    "Skipping the warm-up",
                    "Training through pain",
                ),
            ),
        )
        put(
            "yoga",
            ExerciseGuide(
                setup = "Lay a mat on a non-slip floor and wear comfortable clothes.",
                steps = listOf(
                    "Start with a few slow breaths.",
                    "Move through poses at your own pace.",
                    "Hold each pose for several breaths.",
                    "Finish with a resting pose.",
                ),
                cues = listOf(
                    "Breathe slowly",
                    "Move to a mild stretch, never to pain",
                ),
                commonMistakes = listOf(
                    "Forcing a deeper stretch",
                    "Holding your breath",
                ),
            ),
        )
        put(
            "pilates",
            ExerciseGuide(
                setup = "Lay a mat on the floor and lie on your back.",
                steps = listOf(
                    "Start with slow breathing and gently brace your core.",
                    "Move through controlled exercises such as the hundred and leg circles.",
                    "Keep movements small and smooth.",
                    "Finish with a gentle stretch.",
                ),
                cues = listOf(
                    "Control over speed",
                    "Keep the lower back steady",
                ),
                commonMistakes = listOf(
                    "Rushing the reps",
                    "Arching the lower back",
                ),
            ),
        )
        put(
            "stretching",
            ExerciseGuide(
                setup = "Stretch on a mat after a short warm-up.",
                steps = listOf(
                    "Move into a stretch until you feel mild tension.",
                    "Hold each stretch for 30 seconds.",
                    "Breathe slowly and relax.",
                    "Switch sides and repeat.",
                ),
                cues = listOf(
                    "Mild tension, not pain",
                    "Breathe out as you ease deeper",
                ),
                commonMistakes = listOf(
                    "Bouncing in the stretch",
                    "Pushing into sharp pain",
                ),
            ),
        )
        put(
            "mobility flow",
            ExerciseGuide(
                setup = "Use a mat or clear floor space.",
                steps = listOf(
                    "Start with slow breathing.",
                    "Move your hips, spine and shoulders through full, gentle ranges.",
                    "Link movements together in a smooth flow.",
                    "Repeat each movement for several slow reps.",
                ),
                cues = listOf(
                    "Slow and controlled",
                    "Move through your full range",
                ),
                commonMistakes = listOf(
                    "Rushing through movements",
                    "Forcing end ranges",
                ),
            ),
        )
    }
}
