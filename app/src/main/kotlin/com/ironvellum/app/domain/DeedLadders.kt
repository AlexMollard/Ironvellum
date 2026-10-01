package com.ironvellum.app.domain

/**
 * A series of deeds that ask for the same thing in growing amounts: trials
 * sealed 1 / 25 / 50 / 100 / 250 / 500. The codex shows one row per ladder
 * instead of one panel per deed, so the catalogue can grow rungs without
 * growing the screen.
 *
 * [rungs] run easiest to hardest. A deed that is not part of a series is a
 * ladder of one.
 */
data class DeedLadder(
    val key: String,
    val category: String,
    val title: String,
    val rungs: List<TitleDef>,
) {
    /** The easiest rung not yet earned, or null when the ladder is complete. */
    fun next(earned: Set<String>): TitleDef? = rungs.firstOrNull { it.id !in earned }

    /** One state per rung, in rung order. */
    fun states(earned: Set<String>): List<RungState> {
        val next = next(earned)
        return rungs.map {
            when {
                it.id in earned -> RungState.Earned
                it == next -> RungState.Next
                else -> RungState.Locked
            }
        }
    }
}

enum class RungState { Earned, Next, Locked }

/** A deed category as the codex lists it: its name and one plain line saying what it counts. */
data class DeedCategory(val name: String, val blurb: String)

object DeedLadders {

    /** Every category [Titles.category] can return, in the order the codex lists them. */
    val CATEGORIES: List<DeedCategory> = listOf(
        DeedCategory("Trials", "Trials sealed and oaths kept"),
        DeedCategory("Level", "Levels you have reached"),
        DeedCategory("Volume", "Sets and reps logged"),
        DeedCategory("Strength", "Lifts and strength scores"),
        DeedCategory("Steps", "Steps, distance and daily burn"),
        DeedCategory("Recovery", "Nights of sleep"),
        DeedCategory("Mastery", "Techniques mastered and attempts"),
        DeedCategory("Activities", "Time, distance and variety"),
    )

    /** The ladders of the shipped catalogue, derived once. */
    val ALL: List<DeedLadder> by lazy { derive(Titles.ALL) }

    /**
     * Groups [defs] into ladders: same rule type and same metric, differing
     * only in threshold. Every deed lands in exactly one ladder, rungs are
     * sorted by threshold (catalogue order breaks ties), and ladders keep the
     * order in which their first deed appears in [defs] (groupBy preserves it).
     */
    fun derive(defs: List<TitleDef>): List<DeedLadder> =
        defs.withIndex()
            .groupBy { family(it.value.rule).key }
            .values
            .map { group ->
                val rungs = group
                    .sortedWith(compareBy({ threshold(it.value.rule) }, { it.index }))
                    .map { it.value }
                val fam = family(rungs.first().rule)
                DeedLadder(
                    key = fam.key,
                    category = Titles.category(rungs.first().rule),
                    // A ladder of one is just the deed: its own name says it best.
                    title = if (rungs.size == 1) rungs.first().name else fam.title,
                    rungs = rungs,
                )
            }

    private data class Family(val key: String, val title: String)

    private fun movement(names: Set<String>): String =
        names.first().replaceFirstChar { it.uppercase() }

    /** The metric a rule measures, ignoring how much of it is asked for. */
    private fun family(rule: TitleRule): Family = when (rule) {
        TitleRule.FirstWorkout, is TitleRule.Workouts -> Family("trials", "Trials sealed")
        is TitleRule.WorkoutsInWeek -> Family("trials-week", "Trials in a week")
        is TitleRule.TrainingStreak -> Family("oath", "Oath kept")
        is TitleRule.ReachLevel -> Family("level", "Level reached")
        is TitleRule.SetsLogged -> Family("sets", "Sets logged")
        is TitleRule.RepsLogged -> Family("reps", "Reps logged")
        is TitleRule.SessionStrength -> Family("strength-trial", "Strength in one trial")
        is TitleRule.LifetimeStrength -> Family("strength-life", "Lifetime strength")
        is TitleRule.StepsInDay -> Family("steps-day", "Steps in a day")
        is TitleRule.StepsLifetime -> Family("steps-life", "Lifetime steps")
        is TitleRule.DistanceKmLifetime -> Family("distance-foot", "Distance on foot")
        is TitleRule.ActiveKcalInDay -> Family("kcal-day", "Active calories in a day")
        is TitleRule.StepGoalDays -> Family("step-goal-days", "10,000-step days")
        is TitleRule.SleepMinutesInNight -> Family("sleep", "Sleep in a night")
        is TitleRule.SkillsMastered -> Family("techniques", "Techniques mastered")
        is TitleRule.PracticeAttempts -> Family("attempts", "Journal attempts")
        is TitleRule.ActivityMinutes -> Family("activity-minutes", "Activity time")
        is TitleRule.ActivityDistanceKm -> Family("activity-km", "Activity distance")
        is TitleRule.DistinctActivities -> Family("activities-tried", "Activities tried")
        is TitleRule.LongestRun -> Family("run", "Longest run")
        is TitleRule.LongestSwim -> Family("swim", "Longest swim")
        is TitleRule.HardestGrade -> Family("climb", "Hardest climb")
        is TitleRule.SportSessions -> Family("sport", "Trials with sport play")
        // A lift is the same ladder only when it is the same movements.
        is TitleRule.LiftMultiple ->
            Family("lift:" + rule.names.sorted().joinToString(","), "${movement(rule.names)} by bodyweight")
        is TitleRule.SessionReps ->
            Family("reps-trial:" + rule.names.sorted().joinToString(","), "${movement(rule.names)} reps in one trial")
        is TitleRule.LongestHold -> Family("hold", "Longest hold")
    }

    /** How much of the metric the rule asks for; only comparable within one family. */
    private fun threshold(rule: TitleRule): Double = when (rule) {
        TitleRule.FirstWorkout -> 1.0
        is TitleRule.Workouts -> rule.count.toDouble()
        is TitleRule.ReachLevel -> rule.level.toDouble()
        is TitleRule.SetsLogged -> rule.count.toDouble()
        is TitleRule.RepsLogged -> rule.count.toDouble()
        is TitleRule.SessionStrength -> rule.min.toDouble()
        is TitleRule.LifetimeStrength -> rule.min.toDouble()
        is TitleRule.StepsInDay -> rule.count.toDouble()
        is TitleRule.StepsLifetime -> rule.count.toDouble()
        is TitleRule.DistanceKmLifetime -> rule.km
        is TitleRule.ActiveKcalInDay -> rule.kcal.toDouble()
        is TitleRule.SleepMinutesInNight -> rule.minutes.toDouble()
        is TitleRule.StepGoalDays -> rule.days.toDouble()
        is TitleRule.SkillsMastered -> rule.count.toDouble()
        is TitleRule.PracticeAttempts -> rule.count.toDouble()
        is TitleRule.TrainingStreak -> rule.days.toDouble()
        is TitleRule.WorkoutsInWeek -> rule.count.toDouble()
        is TitleRule.ActivityMinutes -> rule.minutes.toDouble()
        is TitleRule.ActivityDistanceKm -> rule.km
        is TitleRule.DistinctActivities -> rule.count.toDouble()
        is TitleRule.LongestRun -> rule.km
        is TitleRule.LongestSwim -> rule.km
        is TitleRule.HardestGrade -> (GradeRank.rank(rule.grade) ?: 0).toDouble()
        is TitleRule.SportSessions -> rule.count.toDouble()
        is TitleRule.LiftMultiple -> rule.male
        is TitleRule.SessionReps -> rule.count.toDouble()
        is TitleRule.LongestHold -> rule.seconds.toDouble()
    }
}
