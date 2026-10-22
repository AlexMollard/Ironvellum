package com.ironvellum.app.domain

/** What the fatigue model reads from the log: the last 28 days of workouts and the live one, nothing older. */
object FatigueInput {

    private const val MS_PER_DAY = 24L * 3_600_000L

    private fun at(session: WorkoutSession) = session.completedAtMs ?: session.startedAtMs

    /**
     * The last [FatigueTuning.HISTORY_DAYS] days of [history] as [FatigueSession]s, plus the [live] workout timed at
     * [nowMs], so the figure moves as sets are ticked. Strength sets only ([HardSets.logged]); which of them are hard
     * is the model's call ([HardSets.isHard]), so an unticked or warm-up set rides along and counts for nothing.
     */
    fun sessions(
        history: List<Pair<WorkoutSession, List<SessionSet>>>,
        live: Pair<WorkoutSession, List<SessionSet>>?,
        exercises: Map<Long, Exercise>,
        nowMs: Long,
    ): List<FatigueSession> {
        val from = nowMs - FatigueTuning.HISTORY_DAYS * MS_PER_DAY
        val past = history.filter { (session, _) -> at(session) >= from }
            .map { (session, sets) -> FatigueSession(session.id, at(session), HardSets.logged(at(session), sets, exercises)) }
        val now = live?.let { (session, sets) -> FatigueSession(session.id, nowMs, HardSets.logged(nowMs, sets, exercises)) }
        return if (now == null) past else past + now
    }

    /** The earliest start of any completed workout (imported or restored included) or the live one; null when there is none. */
    fun firstWorkoutMs(history: List<Pair<WorkoutSession, List<SessionSet>>>, live: WorkoutSession?): Long? =
        (history.map { it.first.startedAtMs } + listOfNotNull(live?.startedAtMs)).minOrNull()
}
