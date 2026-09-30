package com.ironvellum.app.domain

import kotlin.math.sqrt

/**
 * The six lifts the ally boards rank. [wire] is the `lift_marks.lift` value
 * (check constraint in the baseline schema); [label] is the chip text.
 */
enum class Lift(val wire: String, val label: String) {
    PULL_UP("pull_up", "Pull-up"),
    DIP("dip", "Dip"),
    SQUAT("squat", "Squat"),
    BENCH("bench", "Bench press"),
    DEADLIFT("deadlift", "Deadlift"),
    OVERHEAD_PRESS("ohp", "Overhead press"),
    ;

    companion object {
        // Never a throw: a row written by a newer build with a lift this one
        // lacks must be skipped by the caller, not crash the whole board.
        fun fromWire(value: String?): Lift? = entries.firstOrNull { it.wire == value }
    }
}

/** step 0..[LiftBoards.MAX_STEP]; recentStep/recentAtMs = best qualifying set in the 7 days before nowMs, null when none. */
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

    /** Iron, Bronze, Silver, Gold, Mythic - each spans two steps (I, II). */
    private val TIER_NAMES = listOf("Iron", "Bronze", "Silver", "Gold", "Mythic")

    /** 0 "Initiate", 1 "Iron I", 2 "Iron II", 3 "Bronze I", ... 9 "Mythic I", 10 "Mythic II". */
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
     * order (Iron, Bronze, Silver, Gold, Mythic) = Strength Level's Beginner,
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
     * midpoint between its floor and the next floor; the Mythic ceiling is
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

    /** One mark per lift with at least one qualifying set; empty when bodyweight is unknown (bodyweightAt returns <= 0). */
    fun marks(
        history: List<Pair<WorkoutSession, List<SessionSet>>>,
        bodyweightAt: (Long) -> Double,
        sex: Sex,
        nowMs: Long,
    ): List<LiftMark> {
        val best = HashMap<Lift, Int>()
        val recentBest = HashMap<Lift, Int>()
        val recentAt = HashMap<Lift, Long>()
        for ((session, sets) in history) {
            val bodyweight = bodyweightAt(session.startedAtMs)
            if (bodyweight <= 0.0) continue
            val workoutAt = session.completedAtMs ?: session.startedAtMs
            val isRecent = workoutAt <= nowMs && workoutAt > nowMs - WEEK_MS
            for (set in sets) {
                if (!set.done) continue
                // Epley's rep term stops being honest past 12 (ProgramRules.MAX_E1RM_REPS).
                if (set.reps < 1 || set.reps > ProgramRules.MAX_E1RM_REPS) continue
                if (isAssisted(set)) continue
                val lift = LIFT_BY_NAME[Titles.normaliseName(set.exerciseName)] ?: continue
                val added = (set.weightKg ?: 0.0).coerceAtLeast(0.0)
                val load = if (lift in BODYWEIGHT_LIFTS) bodyweight + added else added
                if (load <= 0.0) continue
                val e1rm = load * (1.0 + set.reps / 30.0)
                val step = stepFor(lift, sex, e1rm / bodyweight)
                if (step > (best[lift] ?: -1)) best[lift] = step
                if (isRecent && step > (recentBest[lift] ?: -1)) {
                    recentBest[lift] = step
                    recentAt[lift] = workoutAt
                } else if (isRecent && step == recentBest[lift] && workoutAt > (recentAt[lift] ?: 0L)) {
                    recentAt[lift] = workoutAt
                }
            }
        }
        return Lift.entries.mapNotNull { lift ->
            val step = best[lift] ?: return@mapNotNull null
            LiftMark(lift, step, recentBest[lift], recentAt[lift])
        }
    }
}
