package com.ironvellum.app.domain

/**
 * Curated map from Strong/Hevy default exercise names to Ironvellum's seed
 * catalogue names.
 *
 * Matching order in [resolve]:
 *  1. exact / NOCASE match against the catalogue (Strong's `Bench Press
 *     (Barbell)` style suffix stripped first);
 *  2. this alias table;
 *  3. nothing. NO fuzzy auto-matching: a wrong silent match corrupts a
 *     lifter's strength history, so anything unresolved goes to review.
 */
object ImportAliases {

    /** Equipment suffixes Strong and Hevy append to default names. */
    private val EQUIPMENT_SUFFIX = Regex(
        "\\s*\\((?:Barbell|Dumbbell|Machine|Cable|Cable - Straight Bar|Smith Machine|" +
            "Bodyweight|Leverage Machine|Lever Machine|Assisted|Weighted|Band|Kettlebell)\\)\\s*$",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Alias -> catalogue name. Only entries a suffix strip CANNOT produce:
     * naming conventions that genuinely differ between the apps and the
     * catalogue. Keep it curated — additions here are statements that the two
     * names mean the same movement.
     */
    private val aliases: Map<String, String> = mapOf(
        // Strong / Hevy name (lowercased, suffix stripped) -> catalogue
        "bench press" to "Bench Press",
        "pull up" to "Pull-up",
        "pull up (weighted)" to "Pull-up",
        "chin up" to "Chin-up",
        "chin-up" to "Chin-up",
        "dip" to "Dip",
        "push up" to "Push-up",
        "push-up" to "Push-up",
        "overhead press" to "Overhead Press",
        "standing overhead press" to "Overhead Press",
        "shoulder press" to "Overhead Press",
        "barbell row" to "Barbell Row",
        "bent over row" to "Barbell Row",
        "dumbbell row" to "Dumbbell Row",
        "lat pull down" to "Lat Pulldown",
        "lat pulldown" to "Lat Pulldown",
        "face pull" to "Face Pull",
        "squat" to "Back Squat",
        "deadlift" to "Deadlift",
        "romanian deadlift" to "Romanian Deadlift",
        "front squat" to "Front Squat",
        "hip thrust" to "Hip Thrust",
        "bulgarian split squat" to "Bulgarian Split Squat",
        "lateral raise" to "Lateral Raise",
        "front raise" to "Front Raise",
        "reverse fly" to "Reverse Fly",
        "hammer curl" to "Hammer Curl",
        "preacher curl" to "Preacher Curl",
        "bicep curl" to "Bicep Curl",
        "wrist curl" to "Wrist Curl",
        "triceps pushdown" to "Triceps Pushdown",
        "seated cable row" to "Seated Cable Row",
        "cable curl" to "Cable Curl",
        "cable fly" to "Cable Fly",
        "cable pull through" to "Cable Pull-Through",
        "leg press" to "Leg Press",
        "hack squat" to "Hack Squat",
        "leg extension" to "Leg Extension",
        "seated leg curl" to "Seated Leg Curl",
        "lying leg curl" to "Lying Leg Curl",
        "pec deck" to "Pec Deck",
        "chest supported row" to "Chest-Supported Row",
        "incline bench press" to "Incline Bench Press",
        "incline dumbbell press" to "Incline Dumbbell Press",
        "dumbbell bench press" to "Dumbbell Bench Press",
        "dumbbell shoulder press" to "Dumbbell Shoulder Press",
        "close grip bench press" to "Close-Grip Bench Press",
        "push press" to "Push Press",
        "sumo deadlift" to "Sumo Deadlift",
        "good morning" to "Good Morning",
        "walking lunge" to "Walking Lunge",
        "goblet squat" to "Goblet Squat",
        "barbell shrug" to "Barbell Shrug",
        "dumbbell shrug" to "Dumbbell Shrug",
        "dumbbell fly" to "Dumbbell Fly",
        "dumbbell pullover" to "Dumbbell Pullover",
        "triceps kickback" to "Triceps Kickback",
        "arnold press" to "Arnold Press",
        "pistol squat" to "Pistol Squat",
        "glute bridge" to "Glute Bridge",
        "single leg glute bridge" to "Single-Leg Glute Bridge",
        "single leg calf raise" to "Single-Leg Calf Raise",
        "standing calf raise" to "Standing Calf Raise",
        "seated calf raise" to "Seated Calf Raise",
        "hip adduction" to "Hip Adduction",
        "hip abduction" to "Hip Abduction",
        "hanging leg raise" to "Hanging Leg Raise",
        "hanging knee raise" to "Hanging Knee Raise",
        "ab wheel rollout" to "Ab Wheel Rollout",
        "ab rollout" to "Ab Wheel Rollout",
        "plank" to "Plank",
        "side plank" to "Side Plank",
        "dragon flag" to "Dragon Flag",
        "woodchop" to "Woodchop",
        "machine chest press" to "Machine Chest Press",
        "machine shoulder press" to "Machine Shoulder Press",
        "machine row" to "Machine Row",
        "running" to "Running",
        "walking" to "Walking",
        "cycling" to "Cycling",
        "rowing" to "Rowing",
        "swimming" to "Swimming",
        "hiking" to "Hiking",
        "yoga" to "Yoga",
        "stretching" to "Stretching",
        "boxing" to "Boxing",
        "bouldering" to "Bouldering",
        "muscle up" to "Muscle-up",
        "ring dip" to "Ring Dip",
        "ring row" to "Ring Row",
        "ring muscle up" to "Ring Muscle-up",
        "handstand push up" to "Handstand Push-up",
        "l sit" to "L-sit",
        "dead hang" to "Dead Hang",
        "front lever" to "Front Lever",
        "back lever" to "Back Lever",
        "inverted row" to "Inverted Row",
        "army press" to "Overhead Press",
    )

    /** Strips a Strong/Hevy equipment suffix; `"Bench Press (Barbell)"` -> `"Bench Press"`. */
    fun stripEquipment(name: String): String = EQUIPMENT_SUFFIX.replace(name.trim(), "").trim()

    /**
     * The best curated resolution for a raw app name, or null when it must go
     * to review. Catalogue is matched NOCASE on both the raw and the stripped
     * name, aliases on the lowercased stripped name.
     */
    fun resolve(rawName: String, catalogue: Collection<String>): String? {
        val raw = rawName.trim()
        if (raw.isEmpty()) return null
        val stripped = stripEquipment(raw)
        catalogue.firstOrNull { it.equals(raw, ignoreCase = true) }?.let { return it }
        catalogue.firstOrNull { it.equals(stripped, ignoreCase = true) }?.let { return it }
        return aliases[stripped.lowercase()]
    }
}
