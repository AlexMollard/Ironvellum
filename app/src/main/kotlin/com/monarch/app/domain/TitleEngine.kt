package com.monarch.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

sealed interface TitleRule {
    data object FirstWorkout : TitleRule
    data class Workouts(val count: Int) : TitleRule
    data class ReachLevel(val level: Int) : TitleRule
    data class SetsLogged(val count: Int) : TitleRule
    data class RepsLogged(val count: Int) : TitleRule
    data class SessionStrength(val min: Int) : TitleRule
    data class LifetimeStrength(val min: Int) : TitleRule
    data class StepsInDay(val count: Int) : TitleRule
    data class StepsLifetime(val count: Long) : TitleRule
    data class DistanceKmLifetime(val km: Double) : TitleRule
    data class ActiveKcalInDay(val kcal: Int) : TitleRule
    data class SleepMinutesInNight(val minutes: Int) : TitleRule
    data class StepGoalDays(val days: Int) : TitleRule
    data class SkillsMastered(val count: Int) : TitleRule
    data class PracticeAttempts(val count: Int) : TitleRule
    data class TrainingStreak(val days: Int) : TitleRule
    data class WorkoutsInWeek(val count: Int) : TitleRule
}

data class TitleDef(
    val id: String,
    val name: String,
    val description: String,
    val rule: TitleRule,
)

object Titles {

    val ALL: List<TitleDef> = listOf(
        // Deeds of war
        TitleDef("awakened", "The Awakened", "Complete your first workout.", TitleRule.FirstWorkout),
        TitleDef("iron_discipline", "Iron Discipline", "Complete 25 workouts.", TitleRule.Workouts(25)),
        TitleDef("relentless", "Relentless", "Complete 50 workouts.", TitleRule.Workouts(50)),
        TitleDef("unbroken", "Unbroken", "Complete 100 workouts.", TitleRule.Workouts(100)),
        TitleDef("hundred_battles", "Hundred Battles", "Complete 250 workouts.", TitleRule.Workouts(250)),
        TitleDef("eternal_grinder", "Eternal Grinder", "Complete 500 workouts.", TitleRule.Workouts(500)),
        // Power
        TitleDef("shadow_ascendant", "Shadow Ascendant", "Reach level 5.", TitleRule.ReachLevel(5)),
        TitleDef("royal_apex", "Royal Apex", "Reach level 10.", TitleRule.ReachLevel(10)),
        TitleDef("baron_of_shadows", "Baron of Shadows", "Reach level 20.", TitleRule.ReachLevel(20)),
        TitleDef("count_of_the_abyss", "Count of the Abyss", "Reach level 35.", TitleRule.ReachLevel(35)),
        TitleDef("duke_of_shadows", "Duke of Shadows", "Reach level 50.", TitleRule.ReachLevel(50)),
        TitleDef("sovereign_of_shadow", "Sovereign of Shadow", "Reach level 75.", TitleRule.ReachLevel(75)),
        TitleDef("monarch_of_shadows", "Monarch of Shadows", "Reach level 100.", TitleRule.ReachLevel(100)),
        // Volume
        TitleDef("gatecrasher", "Gatecrasher", "Log 250 working sets.", TitleRule.SetsLogged(250)),
        TitleDef("storm_of_steel", "Storm of Steel", "Log 500 working sets.", TitleRule.SetsLogged(500)),
        TitleDef("gate_breaker", "Gate Breaker", "Log 1,000 working sets.", TitleRule.SetsLogged(1_000)),
        TitleDef("world_splitter", "World Splitter", "Log 2,500 working sets.", TitleRule.SetsLogged(2_500)),
        TitleDef("steel_tempest", "Steel Tempest", "Log 10,000 working sets.", TitleRule.SetsLogged(10_000)),
        TitleDef("monarchs_mandate", "Monarch's Mandate", "Log 2,000 total reps.", TitleRule.RepsLogged(2_000)),
        TitleDef("ten_thousand_echoes", "Ten Thousand Echoes", "Log 10,000 total reps.", TitleRule.RepsLogged(10_000)),
        TitleDef("endless_legion", "Endless Legion", "Log 25,000 total reps.", TitleRule.RepsLogged(25_000)),
        TitleDef("myriad_strikes", "Myriad Strikes", "Log 50,000 total reps.", TitleRule.RepsLogged(50_000)),
        // Body-scaled strength
        TitleDef(
            "iron_ascension",
            "Iron Ascension",
            "Score 1,000 strength in one workout — scaled to your body.",
            TitleRule.SessionStrength(1_000),
        ),
        TitleDef(
            "titans_verdict",
            "Titan's Verdict",
            "Score 3,000 strength in one workout — scaled to your body.",
            TitleRule.SessionStrength(3_000),
        ),
        TitleDef(
            "gravitys_rebel",
            "Gravity's Rebel",
            "Reach 50,000 lifetime strength — scaled to your body.",
            TitleRule.LifetimeStrength(50_000),
        ),
        TitleDef(
            "gravitys_sovereign",
            "Gravity's Sovereign",
            "Reach 250,000 lifetime strength — scaled to your body.",
            TitleRule.LifetimeStrength(250_000),
        ),
        TitleDef(
            "beyond_gravity",
            "Beyond Gravity",
            "Reach 5,000,000 lifetime strength. The scale gives up.",
            TitleRule.LifetimeStrength(5_000_000),
        ),
        // Steps in a day
        TitleDef(
            "shadow_marcher",
            "Shadow Marcher",
            "Walk 10,000 steps in a single day.",
            TitleRule.StepsInDay(10_000),
        ),
        TitleDef(
            "tireless",
            "Tireless",
            "Walk 15,000 steps in a single day.",
            TitleRule.StepsInDay(15_000),
        ),
        TitleDef(
            "gate_runner",
            "Gate Runner",
            "Walk 20,000 steps in a single day.",
            TitleRule.StepsInDay(20_000),
        ),
        TitleDef(
            "red_zone_hunter",
            "Red Zone Hunter",
            "Walk 30,000 steps in a single day.",
            TitleRule.StepsInDay(30_000),
        ),
        // Lifetime steps
        TitleDef(
            "footsteps_in_the_dark",
            "Footsteps in the Dark",
            "Walk 100,000 steps in your lifetime.",
            TitleRule.StepsLifetime(100_000),
        ),
        TitleDef(
            "wandering_soldier",
            "Wandering Soldier",
            "Walk 500,000 steps in your lifetime.",
            TitleRule.StepsLifetime(500_000),
        ),
        TitleDef(
            "million_march",
            "Million March",
            "Walk 1,000,000 steps in your lifetime.",
            TitleRule.StepsLifetime(1_000_000),
        ),
        TitleDef(
            "shadow_exodus",
            "Shadow Exodus",
            "Walk 5,000,000 steps in your lifetime.",
            TitleRule.StepsLifetime(5_000_000),
        ),
        // Distance
        TitleDef(
            "fifty_k_traveler",
            "Fifty-K Traveler",
            "Cover 50 km on foot in your lifetime.",
            TitleRule.DistanceKmLifetime(50.0),
        ),
        TitleDef(
            "path_carver",
            "Path Carver",
            "Cover 250 km on foot in your lifetime.",
            TitleRule.DistanceKmLifetime(250.0),
        ),
        TitleDef(
            "thousand_gate_runner",
            "Thousand-Gate Runner",
            "Cover 1,000 km on foot in your lifetime.",
            TitleRule.DistanceKmLifetime(1_000.0),
        ),
        // Active calories
        TitleDef(
            "furnace_awake",
            "Furnace Awake",
            "Burn 500 active calories in a single day.",
            TitleRule.ActiveKcalInDay(500),
        ),
        TitleDef(
            "infernal_engine",
            "Infernal Engine",
            "Burn 1,000 active calories in a single day.",
            TitleRule.ActiveKcalInDay(1_000),
        ),
        // Sleep
        TitleDef(
            "eight_hour_shroud",
            "Eight-Hour Shroud",
            "Sleep 8 hours in a single night.",
            TitleRule.SleepMinutesInNight(480),
        ),
        TitleDef(
            "abyssal_slumber",
            "Abyssal Slumber",
            "Sleep 9 hours in a single night.",
            TitleRule.SleepMinutesInNight(540),
        ),
        // Step-goal consistency
        TitleDef(
            "marching_orders",
            "Marching Orders",
            "Hit a 10,000-step day 10 times.",
            TitleRule.StepGoalDays(10),
        ),
        TitleDef(
            "cadence_keeper",
            "Cadence Keeper",
            "Hit a 10,000-step day 50 times.",
            TitleRule.StepGoalDays(50),
        ),
        TitleDef(
            "eternal_vanguard",
            "Eternal Vanguard",
            "Hit a 10,000-step day 100 times.",
            TitleRule.StepGoalDays(100),
        ),
        // Training streaks
        TitleDef(
            "three_day_oath",
            "Three-Day Oath",
            "Train 3 days in a row.",
            TitleRule.TrainingStreak(3),
        ),
        TitleDef(
            "week_of_shadows",
            "Week of Shadows",
            "Train 7 days in a row.",
            TitleRule.TrainingStreak(7),
        ),
        TitleDef(
            "fortnight_vigil",
            "Fortnight Vigil",
            "Train 14 days in a row.",
            TitleRule.TrainingStreak(14),
        ),
        TitleDef(
            "unrelenting_watch",
            "Unrelenting Watch",
            "Train 30 days in a row.",
            TitleRule.TrainingStreak(30),
        ),
        TitleDef(
            "hundred_day_promise",
            "Hundred-Day Promise",
            "Train 100 days in a row.",
            TitleRule.TrainingStreak(100),
        ),
        // Weekly workout volume
        TitleDef(
            "triple_threat",
            "Triple Threat",
            "Complete 3 workouts in a single week.",
            TitleRule.WorkoutsInWeek(3),
        ),
        TitleDef(
            "fivefold_assault",
            "Fivefold Assault",
            "Complete 5 workouts in a single week.",
            TitleRule.WorkoutsInWeek(5),
        ),
        TitleDef(
            "six_gate_week",
            "Six-Gate Week",
            "Complete 6 workouts in a single week.",
            TitleRule.WorkoutsInWeek(6),
        ),
        // Skills mastered
        TitleDef(
            "first_technique",
            "First Technique",
            "Master 1 skill.",
            TitleRule.SkillsMastered(1),
        ),
        TitleDef(
            "apprentice_of_five",
            "Apprentice of Five",
            "Master 5 skills.",
            TitleRule.SkillsMastered(5),
        ),
        TitleDef(
            "ten_folds_form",
            "Tenfold Form",
            "Master 10 skills.",
            TitleRule.SkillsMastered(10),
        ),
        TitleDef(
            "keeper_of_twenty_five",
            "Keeper of Twenty-Five",
            "Master 25 skills.",
            TitleRule.SkillsMastered(25),
        ),
        TitleDef(
            "fifty_fanged_style",
            "Fifty-Fanged Style",
            "Master 50 skills.",
            TitleRule.SkillsMastered(50),
        ),
        TitleDef(
            "grandmaster_of_all",
            "Grandmaster of All",
            "Master all 84 skills. The tree is yours.",
            TitleRule.SkillsMastered(84),
        ),
        // Practice attempts
        TitleDef(
            "first_hundred_cuts",
            "First Hundred Cuts",
            "Log 25 practice attempts.",
            TitleRule.PracticeAttempts(25),
        ),
        TitleDef(
            "hundred_cuts_deep",
            "Hundred Cuts Deep",
            "Log 100 practice attempts.",
            TitleRule.PracticeAttempts(100),
        ),
        TitleDef(
            "five_hundred_repetitions",
            "Five Hundred Repetitions",
            "Log 500 practice attempts.",
            TitleRule.PracticeAttempts(500),
        ),
    )

    fun byId(id: String): TitleDef? = ALL.firstOrNull { it.id == id }

    data class Ledger(
        val totalXp: Long,
        val workouts: Int,
        val sets: Int,
        val reps: Int,
        val sessionStrength: Int = 0,
        val lifetimeStrength: Int = 0,
        val stepsBestDay: Int = 0,
        val stepsLifetime: Long = 0,
        val distanceKmLifetime: Double = 0.0,
        val activeKcalBestDay: Int = 0,
        val sleepBestMinutes: Int = 0,
        val stepGoalDays: Int = 0,
        val skillsMastered: Int = 0,
        val practiceAttempts: Int = 0,
        val trainingStreakDays: Int = 0,
        val bestWeekWorkouts: Int = 0,
    )

    /**
     * The one place a ledger is assembled. Unlocking used to build a partial
     * ledger at session-complete (no steps, no skills), so every step, activity
     * and skill title was unreachable no matter what the Codex displayed.
     */
    fun ledgerOf(
        totalXp: Long,
        history: List<Pair<WorkoutSession, List<SessionSet>>>,
        healthDays: List<HealthDay>,
        practices: List<SkillPractice>,
    ): Ledger {
        val doneSets = history.flatMap { (_, sets) -> sets.filter { it.done } }
        val zone = ZoneId.systemDefault()
        val workoutDates = history
            .map {
                Instant.ofEpochMilli(it.first.completedAtMs ?: it.first.startedAtMs)
                    .atZone(zone).toLocalDate()
            }
            .toSet()
        val claimed = practices.filter { it.claimed }
        return Ledger(
            totalXp = totalXp,
            workouts = history.size,
            sets = doneSets.size,
            reps = doneSets.sumOf { it.reps },
            sessionStrength = history.maxOfOrNull { (s, _) -> s.strengthScore } ?: 0,
            lifetimeStrength = history.sumOf { (s, _) -> s.strengthScore },
            stepsBestDay = healthDays.maxOfOrNull { it.steps } ?: 0,
            stepsLifetime = healthDays.sumOf { it.steps.toLong() },
            distanceKmLifetime = healthDays.sumOf { it.distanceKm },
            activeKcalBestDay = healthDays.maxOfOrNull { it.activeKcal } ?: 0,
            sleepBestMinutes = healthDays.maxOfOrNull { it.sleepMinutes } ?: 0,
            stepGoalDays = healthDays.count { it.steps >= STEP_GOAL },
            skillsMastered = claimed.size,
            practiceAttempts = practices.count { !it.claimed },
            trainingStreakDays = trainingStreakDays(workoutDates),
            bestWeekWorkouts = bestWeekWorkouts(workoutDates),
        )
    }

    /** Consecutive days with a completed workout, counting back from today. */
    fun trainingStreakDays(dates: Set<LocalDate>): Int {
        if (dates.isEmpty()) return 0
        val today = LocalDate.now()
        var day = if (today in dates) today else today.minusDays(1)
        var streak = 0
        while (day in dates) {
            streak++
            day = day.minusDays(1)
        }
        return streak
    }

    /** Most workouts inside any rolling 7-day window. */
    fun bestWeekWorkouts(dates: Set<LocalDate>): Int =
        dates.maxOfOrNull { start ->
            dates.count { !it.isBefore(start) && it.isBefore(start.plusDays(7)) }
        } ?: 0

    fun satisfied(rule: TitleRule, ledger: Ledger): Boolean = when (rule) {
        TitleRule.FirstWorkout -> ledger.workouts >= 1
        is TitleRule.Workouts -> ledger.workouts >= rule.count
        is TitleRule.ReachLevel -> Xp.levelFor(ledger.totalXp) >= rule.level
        is TitleRule.SetsLogged -> ledger.sets >= rule.count
        is TitleRule.RepsLogged -> ledger.reps >= rule.count
        is TitleRule.SessionStrength -> ledger.sessionStrength >= rule.min
        is TitleRule.LifetimeStrength -> ledger.lifetimeStrength >= rule.min
        is TitleRule.StepsInDay -> ledger.stepsBestDay >= rule.count
        is TitleRule.StepsLifetime -> ledger.stepsLifetime >= rule.count
        is TitleRule.DistanceKmLifetime -> ledger.distanceKmLifetime >= rule.km
        is TitleRule.ActiveKcalInDay -> ledger.activeKcalBestDay >= rule.kcal
        is TitleRule.SleepMinutesInNight -> ledger.sleepBestMinutes >= rule.minutes
        is TitleRule.StepGoalDays -> ledger.stepGoalDays >= rule.days
        is TitleRule.SkillsMastered -> ledger.skillsMastered >= rule.count
        is TitleRule.PracticeAttempts -> ledger.practiceAttempts >= rule.count
        is TitleRule.TrainingStreak -> ledger.trainingStreakDays >= rule.days
        is TitleRule.WorkoutsInWeek -> ledger.bestWeekWorkouts >= rule.count
    }

    /** How far along a rule is: current value, target, and the unit's name. */
    data class Progress(val current: Long, val target: Long, val unit: String) {
        val fraction: Float get() = if (target <= 0) 1f else (current.toFloat() / target).coerceIn(0f, 1f)
        val remaining: Long get() = (target - current).coerceAtLeast(0)
    }

    fun progress(rule: TitleRule, ledger: Ledger): Progress = when (rule) {
        TitleRule.FirstWorkout -> Progress(ledger.workouts.toLong().coerceAtMost(1), 1, "workout")
        is TitleRule.Workouts -> Progress(ledger.workouts.toLong(), rule.count.toLong(), "workouts")
        is TitleRule.ReachLevel ->
            Progress(Xp.levelFor(ledger.totalXp).toLong(), rule.level.toLong(), "level")
        is TitleRule.SetsLogged -> Progress(ledger.sets.toLong(), rule.count.toLong(), "sets")
        is TitleRule.RepsLogged -> Progress(ledger.reps.toLong(), rule.count.toLong(), "reps")
        is TitleRule.SessionStrength ->
            Progress(ledger.sessionStrength.toLong(), rule.min.toLong(), "strength in one workout")
        is TitleRule.LifetimeStrength ->
            Progress(ledger.lifetimeStrength.toLong(), rule.min.toLong(), "lifetime strength")
        is TitleRule.StepsInDay ->
            Progress(ledger.stepsBestDay.toLong(), rule.count.toLong(), "steps in a day")
        is TitleRule.StepsLifetime ->
            Progress(ledger.stepsLifetime, rule.count, "steps lifetime")
        is TitleRule.DistanceKmLifetime ->
            Progress(ledger.distanceKmLifetime.toLong(), rule.km.toLong(), "km lifetime")
        is TitleRule.ActiveKcalInDay ->
            Progress(ledger.activeKcalBestDay.toLong(), rule.kcal.toLong(), "active kcal in a day")
        is TitleRule.SleepMinutesInNight ->
            Progress(ledger.sleepBestMinutes.toLong(), rule.minutes.toLong(), "minutes slept in a night")
        is TitleRule.StepGoalDays ->
            Progress(ledger.stepGoalDays.toLong(), rule.days.toLong(), "days hitting 10,000 steps")
        is TitleRule.SkillsMastered ->
            Progress(ledger.skillsMastered.toLong(), rule.count.toLong(), "skills mastered")
        is TitleRule.PracticeAttempts ->
            Progress(ledger.practiceAttempts.toLong(), rule.count.toLong(), "practice attempts")
        is TitleRule.TrainingStreak ->
            Progress(ledger.trainingStreakDays.toLong(), rule.days.toLong(), "day streak")
        is TitleRule.WorkoutsInWeek ->
            Progress(ledger.bestWeekWorkouts.toLong(), rule.count.toLong(), "workouts in a week")
    }

    /** Rule family, for grouping the codex by the kind of deed it demands. */
    fun category(rule: TitleRule): String = when (rule) {
        TitleRule.FirstWorkout, is TitleRule.Workouts, is TitleRule.WorkoutsInWeek -> "Campaigns"
        is TitleRule.ReachLevel -> "Ascension"
        is TitleRule.SetsLogged, is TitleRule.RepsLogged -> "Volume"
        is TitleRule.SessionStrength, is TitleRule.LifetimeStrength -> "Strength"
        is TitleRule.StepsInDay, is TitleRule.StepsLifetime, is TitleRule.DistanceKmLifetime,
        is TitleRule.ActiveKcalInDay, is TitleRule.StepGoalDays -> "Movement"
        is TitleRule.SleepMinutesInNight -> "Recovery"
        is TitleRule.SkillsMastered, is TitleRule.PracticeAttempts -> "Mastery"
        is TitleRule.TrainingStreak -> "Campaigns"
    }

    /** Returns definitions whose rules are met and that are not in [already]. */
    fun newlyUnlocked(ledger: Ledger, already: Set<String>): List<TitleDef> =
        ALL.filter { it.id !in already && satisfied(it.rule, ledger) }
}
