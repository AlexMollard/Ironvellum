package com.ironvellum.app.domain

import kotlin.math.sqrt

/** How a board scores: bodyweight-ratio tiers, or an ordered ladder of skill rungs. */
enum class LiftKind { TIERED, LADDER }

/** Display grouping of the boards, in list order. */
enum class LiftGroup(val label: String) {
    PULL("Pull"),
    PUSH("Push"),
    STATIC("Static"),
    LEGS("Legs"),
    BARBELL("Barbell"),
}

/**
 * The boards the ally boards rank, calisthenics first and barbell last.
 * [wire] is the `lift_marks.lift` value (check constraint in the baseline
 * schema); [label] is the chip text. LADDER boards take their rungs from
 * [LiftBoards.rungs].
 */
enum class Lift(val wire: String, val label: String, val group: LiftGroup, val kind: LiftKind) {
    PULL_UP("pull_up", "Weighted pull-up", LiftGroup.PULL, LiftKind.TIERED),
    ONE_ARM_PULL("one_arm_pull", "One-arm pull-up", LiftGroup.PULL, LiftKind.LADDER),
    MUSCLE_UP("muscle_up", "Muscle-up", LiftGroup.PULL, LiftKind.LADDER),
    DIP("dip", "Weighted dip", LiftGroup.PUSH, LiftKind.TIERED),
    PUSH_UP("push_up", "One-arm push-up", LiftGroup.PUSH, LiftKind.LADDER),
    HSPU("hspu", "Handstand push-up", LiftGroup.PUSH, LiftKind.LADDER),
    FRONT_LEVER("front_lever", "Front lever", LiftGroup.STATIC, LiftKind.LADDER),
    BACK_LEVER("back_lever", "Back lever", LiftGroup.STATIC, LiftKind.LADDER),
    PLANCHE("planche", "Planche", LiftGroup.STATIC, LiftKind.LADDER),
    HANDSTAND("handstand", "Handstand", LiftGroup.STATIC, LiftKind.LADDER),
    L_SIT("l_sit", "L-sit to manna", LiftGroup.STATIC, LiftKind.LADDER),
    HUMAN_FLAG("human_flag", "Human flag", LiftGroup.STATIC, LiftKind.LADDER),
    PISTOL("pistol", "Single-leg squat", LiftGroup.LEGS, LiftKind.LADDER),
    SQUAT("squat", "Squat", LiftGroup.BARBELL, LiftKind.TIERED),
    BENCH("bench", "Bench press", LiftGroup.BARBELL, LiftKind.TIERED),
    DEADLIFT("deadlift", "Deadlift", LiftGroup.BARBELL, LiftKind.TIERED),
    OVERHEAD_PRESS("ohp", "Overhead press", LiftGroup.BARBELL, LiftKind.TIERED),
    ;

    companion object {
        // Never a throw: a row written by a newer build with a lift this one
        // lacks must be skipped by the caller, not crash the whole board.
        fun fromWire(value: String?): Lift? = entries.firstOrNull { it.wire == value }
    }
}

/** step 0..[LiftBoards.MAX_STEP] (a LADDER step is the 1-based rung); recentStep/recentAtMs = best qualifying set in the 7 days before nowMs, null when none. */
data class LiftMark(val lift: Lift, val step: Int, val recentStep: Int?, val recentAtMs: Long?)

/**
 * Bodyweight-relative strength tiers per lift, computed on device.
 *
 * Only the tier step ever leaves the phone. The step is deliberately coarse
 * (11 values): bodyweight cannot be solved from it even against the public set
 * loads an ally sees, which is the privacy point of ranking by tier instead of
 * by ratio.
 *
 * Pure Kotlin: no Room, no network, so it runs in plain JVM tests.
 */
object LiftBoards {
    const val MAX_STEP = 10

    private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000

    /** Ash, Bone, Silver, Gold, Umbral - each spans two steps (I, II). */
    private val TIER_NAMES = listOf("Ash", "Bone", "Silver", "Gold", "Umbral")

    /** 0 "Initiate", 1 "Ash I", 2 "Ash II", 3 "Bone I", ... 9 "Umbral I", 10 "Umbral II". */
    fun tierName(step: Int): String {
        val s = step.coerceIn(0, MAX_STEP)
        if (s == 0) return "Initiate"
        return "${TIER_NAMES[(s - 1) / 2]} ${if ((s - 1) % 2 == 0) "I" else "II"}"
    }

    /**
     * Catalogue exercise names (normalised: lowercase, trimmed) that count for
     * each lift. Only strict variants qualify, so a board never ranks an
     * easier or assisted movement against the real lift:
     *  - Assisted pull-up / dip machines are out (the marked kilo subtracts load).
     *  - Archer pull-up and ring dip are out (load is not bodyweight + added kg).
     *  - Incline bench and push press are out (different lift / leg drive).
     *  - Front squat and sumo deadlift stay in, matching the strength titles
     *    in TitleEngine; they can only understate the back squat / conventional pull.
     */
    private val LIFT_BY_NAME: Map<String, Lift> = mapOf(
        "pull-up" to Lift.PULL_UP,
        "weighted pull-up" to Lift.PULL_UP,
        "chin-up" to Lift.PULL_UP,
        "weighted chin-up" to Lift.PULL_UP,
        "dip" to Lift.DIP,
        "parallel bar dip" to Lift.DIP,
        "weighted dip" to Lift.DIP,
        "back squat" to Lift.SQUAT,
        "front squat" to Lift.SQUAT,
        "bench press" to Lift.BENCH,
        "close-grip bench press" to Lift.BENCH,
        "deadlift" to Lift.DEADLIFT,
        "sumo deadlift" to Lift.DEADLIFT,
        "overhead press" to Lift.OVERHEAD_PRESS,
    )

    /** For these lifts the moved load is bodyweight + [SessionSet.weightKg] (added load). */
    private val BODYWEIGHT_LIFTS = setOf(Lift.PULL_UP, Lift.DIP)

    /**
     * Tier floors as multiples of bodyweight for the estimated 1RM, in tier
     * order (Ash, Bone, Silver, Gold, Umbral) = Strength Level's Beginner,
     * Novice, Intermediate, Advanced, Elite.
     *
     * Source: strengthlevel.com/strength-standards, read 2026-09-30.
     *  - Squat, bench press, deadlift, shoulder press: the published
     *    "Bodyweight Ratio" rows, male and female, copied as is.
     *  - Pull-ups and dips: Strength Level publishes 1RM ADDED load per
     *    bodyweight. These floors restate it as TOTAL moved load over
     *    bodyweight (bodyweight + added), because that is what a pull-up
     *    lifts, taken at the 80 kg male and 60 kg female rows and rounded to
     *    0.05. Neighbouring rows differ by at most about 0.1.
     */
    private val FLOORS_MALE: Map<Lift, List<Double>> = mapOf(
        Lift.PULL_UP to listOf(0.95, 1.2, 1.4, 1.7, 1.95),
        Lift.DIP to listOf(1.05, 1.3, 1.65, 2.0, 2.4),
        Lift.SQUAT to listOf(0.75, 1.25, 1.75, 2.25, 2.75),
        Lift.BENCH to listOf(0.5, 1.0, 1.25, 1.5, 2.0),
        Lift.DEADLIFT to listOf(1.0, 1.5, 2.0, 2.5, 3.25),
        Lift.OVERHEAD_PRESS to listOf(0.35, 0.55, 0.8, 1.05, 1.35),
    )

    private val FLOORS_FEMALE: Map<Lift, List<Double>> = mapOf(
        Lift.PULL_UP to listOf(0.75, 0.95, 1.15, 1.4, 1.65),
        Lift.DIP to listOf(0.75, 1.0, 1.3, 1.6, 1.95),
        Lift.SQUAT to listOf(0.5, 0.75, 1.25, 1.75, 2.25),
        Lift.BENCH to listOf(0.3, 0.5, 0.75, 1.1, 1.45),
        Lift.DEADLIFT to listOf(0.75, 1.0, 1.5, 2.0, 2.5),
        Lift.OVERHEAD_PRESS to listOf(0.2, 0.35, 0.5, 0.7, 0.95),
    )

    private fun floors(lift: Lift, sex: Sex): List<Double> =
        (if (sex == Sex.FEMALE) FLOORS_FEMALE else FLOORS_MALE).getValue(lift)

    /**
     * Step for a bodyweight multiple. Each tier splits at the geometric
     * midpoint between its floor and the next floor; the Umbral ceiling is
     * open, so its split reuses the previous tier's growth ratio.
     */
    internal fun stepFor(lift: Lift, sex: Sex, ratio: Double): Int {
        val f = floors(lift, sex)
        if (ratio < f[0]) return 0
        var tier = 0
        while (tier < f.lastIndex && ratio >= f[tier + 1]) tier++
        val next = if (tier < f.lastIndex) f[tier + 1] else f[tier] * (f[tier] / f[tier - 1])
        val second = ratio >= sqrt(f[tier] * next)
        return 1 + 2 * tier + if (second) 1 else 0
    }

    private fun isAssisted(set: SessionSet): Boolean =
        set.exerciseName.contains("assisted", ignoreCase = true) ||
            set.modifiers.split(",").any { it.trim().equals("assisted", ignoreCase = true) }

    /** One rung of a LADDER board: a catalogue exercise and the figure that clears it. */
    data class Rung(val exercise: String, val metric: Skills.Metric, val target: Int)

    /**
     * Rung exercise names per LADDER lift, easiest first, in the skill tree's
     * own progression order. The standard (reps or seconds) is read off
     * [Skills.SkillDef.standard], so the board and the tree never disagree.
     *
     * Handstand Walk is deliberately absent from the handstand ladder: its
     * standard is metres, and a workout set cannot carry a distance for it.
     * No rung uses a gym-line skill, so [Skills.femaleStandard] never applies;
     * the calisthenics standards are the same for every lifter.
     */
    private val LADDER_EXERCISES: Map<Lift, List<String>> = mapOf(
        Lift.ONE_ARM_PULL to listOf("Australian Pull-up", "Pull-up", "Archer Pull-up", "One-Arm Negative", "One-Arm Pull-up"),
        Lift.MUSCLE_UP to listOf("Muscle-up", "Strict Muscle-up", "Ring Muscle-up", "Inverted Muscle-up"),
        Lift.PUSH_UP to listOf(
            "Incline Push-up", "Push-up", "Diamond Push-up", "Archer Push-up", "One-Arm Negative Push-up", "One-Arm Push-up",
        ),
        Lift.HSPU to listOf("Pike Press", "Wall HSPU", "Handstand Push-up", "90-Degree Push-up"),
        Lift.FRONT_LEVER to listOf(
            "Front Row Hold", "Tuck Front Lever", "Advanced Tuck Front Lever", "One-Leg Front Lever",
            "Straddle Front Lever", "Front Lever",
        ),
        Lift.BACK_LEVER to listOf("Skin the Cat", "Tuck Back Lever", "Advanced Tuck Back Lever", "Straddle Back Lever", "Back Lever"),
        Lift.PLANCHE to listOf(
            "Frog Stand", "Tuck Planche", "Advanced Tuck Planche", "One-Leg Planche", "Straddle Planche", "Full Planche",
        ),
        Lift.HANDSTAND to listOf("Crow Pose", "Wall Handstand", "Freestanding Handstand", "One-Arm Handstand"),
        Lift.L_SIT to listOf("L-sit", "Straddle L-sit", "V-Sit", "Manna"),
        Lift.HUMAN_FLAG to listOf("Dead Hang", "One-Arm Hang", "Human Flag"),
        Lift.PISTOL to listOf("Split Squat", "Sissy Squat", "Shrimp Squat", "Pistol Squat", "Dragon Squat"),
    )

    private val LADDERS: Map<Lift, List<Rung>> = LADDER_EXERCISES.mapValues { (lift, names) ->
        require(names.size in 1..MAX_STEP) { "${lift.wire} needs 1..$MAX_STEP rungs" }
        names.map { name ->
            val skill = requireNotNull(Skills.forName(name)) { "${lift.wire}: no skill named $name" }
            Rung(name, skill.metric, skill.target)
        }
    }

    /** The rungs of a LADDER lift, easiest first; empty for a TIERED lift. */
    fun rungs(lift: Lift): List<Rung> = LADDERS[lift].orEmpty()

    /** A board an exercise counts toward: [rung] is the 1-based ladder rung of [rungCount], or null on a TIERED board. */
    data class BoardEntry(val lift: Lift, val rung: Int?, val rungCount: Int)

    /** Every board [exerciseName] counts toward: its TIERED lift, and each LADDER rung it is. Empty when none. */
    fun boardsFor(exerciseName: String): List<BoardEntry> {
        val name = Titles.normaliseName(exerciseName)
        return buildList {
            LIFT_BY_NAME[name]?.let { add(BoardEntry(it, null, 0)) }
            for ((lift, rungs) in LADDERS) {
                val index = rungs.indexOfFirst { Titles.normaliseName(it.exercise) == name }
                if (index >= 0) add(BoardEntry(lift, index + 1, rungs.size))
            }
        }
    }

    /** Headline for a step: the tier name on TIERED boards, the rung's exercise on LADDER boards. */
    fun stepLabel(lift: Lift, step: Int): String {
        if (lift.kind == LiftKind.TIERED) return tierName(step)
        return rungs(lift).getOrNull(step - 1)?.exercise ?: "No rung yet"
    }

    /** "rung 4 of 6" for a LADDER step; null on TIERED boards and for step 0. */
    fun stepDetail(lift: Lift, step: Int): String? {
        val rungs = rungs(lift)
        if (rungs.isEmpty() || step < 1) return null
        return "rung ${step.coerceAtMost(rungs.size)} of ${rungs.size}"
    }

    /** The highest rung of [lift] cleared by a set of [exercise] worth [reps] / [seconds]; 0 when none. */
    private fun rungCleared(lift: Lift, exercise: String, reps: Int, seconds: Int?): Int {
        val rungs = LADDERS[lift] ?: return 0
        val index = rungs.indexOfFirst { Titles.normaliseName(it.exercise) == exercise }
        if (index < 0) return 0
        val rung = rungs[index]
        val figure = when (rung.metric) {
            Skills.Metric.REPS -> reps
            Skills.Metric.SECONDS -> seconds ?: 0
            Skills.Metric.METRES -> 0
        }
        return if (figure >= rung.target && rung.target > 0) index + 1 else 0
    }

    /**
     * One mark per lift with at least one qualifying set. TIERED lifts need a
     * known bodyweight (bodyweightAt returns > 0); LADDER lifts never do.
     *
     * LADDER step = the highest rung whose exercise has a done, non-assisted
     * set meeting the rung's standard, so a higher rung implies every lower
     * one. [practices] are the local skill-practice records: a practice
     * attempt at a rung's skill counts exactly like a logged set (a lifter
     * who drills a lever in the skill tree, not in a workout, still ranks).
     * Claim rows are ignored: a claim is an honours mark, not a measured figure.
     */
    fun marks(
        history: List<Pair<WorkoutSession, List<SessionSet>>>,
        bodyweightAt: (Long) -> Double,
        sex: Sex,
        nowMs: Long,
        practices: List<SkillPractice> = emptyList(),
    ): List<LiftMark> {
        val best = HashMap<Lift, Int>()
        val recentBest = HashMap<Lift, Int>()
        val recentAt = HashMap<Lift, Long>()

        fun record(lift: Lift, step: Int, at: Long, isRecent: Boolean) {
            if (step > (best[lift] ?: -1)) best[lift] = step
            if (isRecent && step > (recentBest[lift] ?: -1)) {
                recentBest[lift] = step
                recentAt[lift] = at
            } else if (isRecent && step == recentBest[lift] && at > (recentAt[lift] ?: 0L)) {
                recentAt[lift] = at
            }
        }

        fun recent(at: Long) = at <= nowMs && at > nowMs - WEEK_MS

        for ((session, sets) in history) {
            val bodyweight = bodyweightAt(session.startedAtMs)
            val workoutAt = session.completedAtMs ?: session.startedAtMs
            val isRecent = recent(workoutAt)
            for (set in sets) {
                if (!set.done) continue
                if (isAssisted(set)) continue
                val name = Titles.normaliseName(set.exerciseName)

                for (lift in LADDERS.keys) {
                    val step = rungCleared(lift, name, set.reps, set.durationSec)
                    if (step > 0) record(lift, step, workoutAt, isRecent)
                }

                if (bodyweight <= 0.0) continue
                // Epley's rep term stops being honest past 12 (ProgramRules.MAX_E1RM_REPS).
                if (set.reps < 1 || set.reps > ProgramRules.MAX_E1RM_REPS) continue
                val lift = LIFT_BY_NAME[name] ?: continue
                val added = (set.weightKg ?: 0.0).coerceAtLeast(0.0)
                val load = if (lift in BODYWEIGHT_LIFTS) bodyweight + added else added
                if (load <= 0.0) continue
                val e1rm = load * (1.0 + set.reps / 30.0)
                record(lift, stepFor(lift, sex, e1rm / bodyweight), workoutAt, isRecent)
            }
        }

        for (practice in practices) {
            if (practice.claimed) continue
            val name = Titles.normaliseName(practice.skillName)
            val isRecent = recent(practice.practicedAtMs)
            for (lift in LADDERS.keys) {
                // A practice value is seconds for a SECONDS rung, reps otherwise.
                val step = rungCleared(lift, name, practice.value, practice.value)
                if (step > 0) record(lift, step, practice.practicedAtMs, isRecent)
            }
        }

        return Lift.entries.mapNotNull { lift ->
            val step = best[lift] ?: return@mapNotNull null
            LiftMark(lift, step, recentBest[lift], recentAt[lift])
        }
    }
}
