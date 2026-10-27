package com.ironvellum.app.domain

/**
 * Temper's recovery finding: a routine that keeps starting on a muscle that is still tired. It reads logged history, not
 * the plan: of a routine's last [WORKOUTS] completed workouts within [LOOKBACK_DAYS], it rebuilds the fatigue report at
 * each start (only what came before it counts) and looks at the routine's main muscles. A muscle that was High or above
 * at the start of at least [NEEDED] of them is the finding. Pure, so every rule is a JVM test.
 */
object RecoveryFinding {

    const val WORKOUTS = 3
    const val NEEDED = 2
    const val LOOKBACK_DAYS = 42L

    private const val MS_PER_DAY = 24L * 3_600_000L

    /** One workout of the routine: when it began and the muscle's level just before it. */
    data class Start(val sessionId: Long, val startedAtMs: Long, val level: FatigueLevel)

    /**
     * The earlier workout that put the most dose into the muscle, as the latest tired start saw it. [presetId] is its
     * routine (null for a freestyle workout), [exerciseName] and [modifiers] the lift that loaded the muscle most there,
     * and [liftResidual] what that lift still held of the muscle at the start it explains.
     */
    data class Cause(
        val sessionId: Long,
        val atMs: Long,
        val presetId: Long?,
        val exerciseName: String,
        val modifiers: String,
        val liftResidual: Double,
    )

    data class Finding(
        val presetId: Long,
        val rite: String,
        val muscle: Muscle,
        /** The routine's last workouts in the window, oldest first, each with the muscle's level at its start. */
        val starts: List<Start>,
        val cause: Cause?,
        /** The muscle's residual at the latest start, for the projection a trim shows. */
        val residual: Double,
    ) {
        /** How many of the [starts] began at High or above. */
        val tiredStarts: Int get() = starts.count { FatigueAdvice.triggers(it.level) }
    }

    /**
     * The level [muscle] would have started the latest workout at had the cause lift been [sets] - 1 sets instead of
     * [sets]: its remaining share is taken off in proportion. An estimate, so the screen says "about".
     */
    fun projected(finding: Finding, sets: Int): FatigueLevel {
        val lift = finding.cause?.liftResidual ?: return FatigueLevel.of(finding.residual)
        if (sets < 2) return FatigueLevel.of(finding.residual)
        return FatigueLevel.of((finding.residual - lift / sets).coerceAtLeast(0.0))
    }

    /**
     * One finding per routine at most, the most often tired muscle first. [isKept] is the standing "Keep as is"; a
     * routine with fewer than [NEEDED] workouts in the window has nothing to find.
     */
    fun find(
        presets: List<WorkoutPreset>,
        history: List<Pair<WorkoutSession, List<SessionSet>>>,
        exercises: Map<Long, Exercise>,
        nowMs: Long,
        experience: LiftingExperience = LiftingExperience.UNANSWERED,
        isKept: (presetId: Long, muscle: Muscle) -> Boolean = { _, _ -> false },
    ): List<Finding> {
        if (history.isEmpty()) return emptyList()
        val first = FatigueInput.firstWorkoutMs(history, null)
        val from = nowMs - LOOKBACK_DAYS * MS_PER_DAY
        return presets.mapNotNull { preset ->
            val mains = preset.working.flatMap { FatigueAdvice.mainMuscles(it.exerciseName, it.modifiers) }.toSet()
            if (mains.isEmpty()) return@mapNotNull null
            val workouts = history
                .filter { (session, _) -> session.presetId == preset.id && session.completedAtMs != null && session.startedAtMs in from..nowMs }
                .map { it.first }
                .sortedByDescending { it.startedAtMs }
                .take(WORKOUTS)
                .reversed()
            if (workouts.size < NEEDED) return@mapNotNull null
            val reports = workouts.map { workout ->
                val before = history.filter { (session, _) -> (session.completedAtMs ?: session.startedAtMs) <= workout.startedAtMs }
                val sessions = FatigueInput.sessions(before, null, exercises, workout.startedAtMs)
                workout to FatigueModel.estimate(sessions, workout.startedAtMs, first, experience)
            }
            val tired = mains.filter { !isKept(preset.id, it) }.mapNotNull { muscle ->
                val levels = reports.map { (workout, report) -> report.levelAt(muscle, workout.startedAtMs) }
                val count = levels.count { FatigueAdvice.triggers(it) }
                if (count < NEEDED) null else Triple(muscle, count, reports.sumOf { (w, r) -> r.residual(muscle, w.startedAtMs) })
            }
            val (muscle, _, _) = tired.maxWithOrNull(
                compareBy<Triple<Muscle, Int, Double>> { it.second }.thenBy { it.third }.thenByDescending { it.first.ordinal },
            ) ?: return@mapNotNull null
            val starts = reports.map { (workout, report) -> Start(workout.id, workout.startedAtMs, report.levelAt(muscle, workout.startedAtMs)) }
            val (latestTired, report) = reports.last { (workout, r) -> FatigueAdvice.triggers(r.levelAt(muscle, workout.startedAtMs)) }
            Finding(
                presetId = preset.id,
                rite = preset.name,
                muscle = muscle,
                starts = starts,
                cause = causeOf(report, muscle, latestTired.startedAtMs, history),
                residual = report.residual(muscle, latestTired.startedAtMs),
            )
        }.sortedWith(compareByDescending<Finding> { it.tiredStarts }.thenBy { it.rite })
    }

    /** The session that put the most dose into [muscle] as [report] saw it at [atMs]; the most recent on a tie. */
    private fun causeOf(
        report: FatigueReport,
        muscle: Muscle,
        atMs: Long,
        history: List<Pair<WorkoutSession, List<SessionSet>>>,
    ): Cause? {
        val bySession = report.contributions[muscle].orEmpty().filter { it.sessionAtMs <= atMs }.groupBy { it.sessionId }
        val (sessionId, lifts) = bySession.maxWithOrNull(
            compareBy<Map.Entry<Long, List<FatigueContribution>>> { it.value.sumOf { c -> c.dose } }
                .thenBy { it.value.maxOf { c -> c.sessionAtMs } },
        )?.toPair() ?: return null
        val top = lifts.maxByOrNull { it.dose } ?: return null
        return Cause(
            sessionId = sessionId,
            atMs = top.sessionAtMs,
            presetId = history.firstOrNull { it.first.id == sessionId }?.first?.presetId,
            exerciseName = top.exerciseName,
            modifiers = top.modifiers,
            liftResidual = top.residualAt(atMs),
        )
    }
}
