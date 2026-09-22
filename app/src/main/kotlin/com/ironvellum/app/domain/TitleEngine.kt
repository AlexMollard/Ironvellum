package com.ironvellum.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToLong

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

    // ---- activity deeds ----
    // Activities are anything with a non-REPS metric (duration, distance/time,
    // graded attempts). They earn XP on their own curve and never strength.
    /** Lifetime minutes across all non-REPS activity. */
    data class ActivityMinutes(val minutes: Int) : TitleRule
    /** Lifetime distance across all non-REPS activity, in km. */
    data class ActivityDistanceKm(val km: Double) : TitleRule
    /** How many different activities have ever been logged (done sets). */
    data class DistinctActivities(val count: Int) : TitleRule
    /** Best single-session distance on a Cardio-category activity, in km. */
    data class LongestRun(val km: Double) : TitleRule
    /** Best single-session distance on a Water-category activity, in km. */
    data class LongestSwim(val km: Double) : TitleRule
    /** Hardest climbing grade ever sent. Free text across V/Font/YDS. */
    data class HardestGrade(val grade: String) : TitleRule
    /** Completed sessions that contained any Sport-category work. */
    data class SportSessions(val count: Int) : TitleRule

    // ---- strength milestones ----
    // Recognition only. These never touch XP, tiers, load factors or the
    // strength score; they read the same logged sets through their own lens.

    /**
     * A loaded lift at a multiple of bodyweight. [names] are catalogue
     * exercise names (normalised) whose marked load can honestly stand for
     * the lift; thresholds are the estimated 1RM multiple of the bodyweight
     * IN FORCE at the session, per sex, so a woman must do an equivalently
     * hard lift to wear the same title.
     */
    data class LiftMultiple(val names: Set<String>, val male: Double, val female: Double) : TitleRule
    /** Best rep total on one movement within a single completed session. */
    data class SessionReps(val names: Set<String>, val count: Int) : TitleRule
    /** Longest single static hold, in seconds. */
    data class LongestHold(val seconds: Int) : TitleRule
}


/**
 * How hard a title's rule is to satisfy. Rarity is assigned by reading the
 * rule itself — a first-session deed is Common, sustained training or a real
 * strength milestone is Rare, long streaks / deep skills / heavy loads are
 * Epic, and Masterwork is reserved for the extreme end of a ladder.
 */
enum class TitleRarity { Common, Rare, Epic, Masterwork }

data class TitleDef(
    val id: String,
    val name: String,
    val description: String,
    val rule: TitleRule,
    // No default: a future title must state its rarity explicitly, never
    // silently fall through to Common.
    val rarity: TitleRarity,
    /**
     * What this deed asks of a female lifter, when her bar differs.
     *
     * The skill tree already shows a woman her own standard rather than a
     * man's with a footnote (Skills.femaleStandard), and the deeds follow it:
     * "squat your own bodyweight (0.6x if female)" made her read the men's
     * number first and herself as the exception. Null where the deed is the
     * same feat for everyone - rep and hold counts deliberately are.
     *
     * Stated as "Nx your bodyweight" so it cannot drift from the rule: a test
     * reads the figure back out and compares it to [TitleRule.LiftMultiple].
     */
    val descriptionFemale: String? = null,
) {
    /** The bar as the lifter reading it must clear. */
    fun describeFor(sex: Sex): String =
        if (sex == Sex.FEMALE) descriptionFemale ?: description else description
}

object Titles {

    val ALL: List<TitleDef> = listOf(
        // Deeds of war
        TitleDef("awakened", "First Mark", "Complete your first workout.", TitleRule.FirstWorkout, TitleRarity.Common),
        TitleDef("iron_discipline", "Iron Discipline", "Complete 25 workouts.", TitleRule.Workouts(25), TitleRarity.Rare),
        TitleDef("relentless", "Relentless", "Complete 50 workouts.", TitleRule.Workouts(50), TitleRarity.Rare),
        TitleDef("unbroken", "Unbroken", "Complete 100 workouts.", TitleRule.Workouts(100), TitleRarity.Epic),
        TitleDef("hundred_battles", "Hundred Battles", "Complete 250 workouts.", TitleRule.Workouts(250), TitleRarity.Epic),
        TitleDef("eternal_grinder", "Eternal Grinder", "Complete 500 workouts.", TitleRule.Workouts(500), TitleRarity.Masterwork),
        // Power
        TitleDef("shadow_ascendant", "Rising Mark", "Reach level 5.", TitleRule.ReachLevel(5), TitleRarity.Common),
        TitleDef("royal_apex", "Royal Apex", "Reach level 10.", TitleRule.ReachLevel(10), TitleRarity.Common),
        TitleDef("baron_of_shadows", "Keeper of the Roll", "Reach level 20.", TitleRule.ReachLevel(20), TitleRarity.Rare),
        TitleDef("count_of_the_abyss", "Count of the Abyss", "Reach level 35.", TitleRule.ReachLevel(35), TitleRarity.Rare),
        TitleDef("duke_of_shadows", "Warden of the Roll", "Reach level 50.", TitleRule.ReachLevel(50), TitleRarity.Epic),
        TitleDef("sovereign_of_shadow", "Master of the Roll", "Reach level 75.", TitleRule.ReachLevel(75), TitleRarity.Masterwork),
        TitleDef("monarch_of_shadows", "Grand Archivist", "Reach level 100.", TitleRule.ReachLevel(100), TitleRarity.Masterwork),
        // Volume
        TitleDef("gatecrasher", "Doorbreaker", "Log 250 working sets.", TitleRule.SetsLogged(250), TitleRarity.Common),
        TitleDef("storm_of_steel", "Storm of Steel", "Log 500 working sets.", TitleRule.SetsLogged(500), TitleRarity.Rare),
        TitleDef("gate_breaker", "Threshold Breaker", "Log 1,000 working sets.", TitleRule.SetsLogged(1_000), TitleRarity.Rare),
        TitleDef("world_splitter", "World Splitter", "Log 2,500 working sets.", TitleRule.SetsLogged(2_500), TitleRarity.Epic),
        TitleDef("steel_tempest", "Steel Tempest", "Log 10,000 working sets.", TitleRule.SetsLogged(10_000), TitleRarity.Masterwork),
        TitleDef("monarchs_mandate", "Standing Order", "Log 2,000 total reps.", TitleRule.RepsLogged(2_000), TitleRarity.Common),
        TitleDef("ten_thousand_echoes", "Ten Thousand Echoes", "Log 10,000 total reps.", TitleRule.RepsLogged(10_000), TitleRarity.Rare),
        TitleDef("endless_legion", "Endless Legion", "Log 25,000 total reps.", TitleRule.RepsLogged(25_000), TitleRarity.Epic),
        TitleDef("myriad_strikes", "Myriad Strikes", "Log 50,000 total reps.", TitleRule.RepsLogged(50_000), TitleRarity.Masterwork),
        // Body-scaled strength
        TitleDef(
            "iron_ascension",
            "Iron Ascension",
            "Score 1,000 strength in one workout — scaled to your body.",
            TitleRule.SessionStrength(1_000),
            TitleRarity.Rare,
        ),
        TitleDef(
            "titans_verdict",
            "Titan's Verdict",
            "Score 3,000 strength in one workout — scaled to your body.",
            TitleRule.SessionStrength(3_000),
            TitleRarity.Epic,
        ),
        TitleDef(
            "gravitys_rebel",
            "Gravity's Rebel",
            "Reach 50,000 lifetime strength — scaled to your body.",
            TitleRule.LifetimeStrength(50_000),
            TitleRarity.Rare,
        ),
        TitleDef(
            "gravitys_sovereign",
            "Gravity's Master",
            "Reach 250,000 lifetime strength — scaled to your body.",
            TitleRule.LifetimeStrength(250_000),
            TitleRarity.Epic,
        ),
        TitleDef(
            "beyond_gravity",
            "Beyond Gravity",
            "Reach 5,000,000 lifetime strength. The scale gives up.",
            TitleRule.LifetimeStrength(5_000_000),
            TitleRarity.Masterwork,
        ),
        // Steps in a day
        TitleDef(
            "shadow_marcher",
            "Long Marcher",
            "Walk 10,000 steps in a single day.",
            TitleRule.StepsInDay(10_000),
            TitleRarity.Common,
        ),
        TitleDef(
            "tireless",
            "Tireless",
            "Walk 15,000 steps in a single day.",
            TitleRule.StepsInDay(15_000),
            TitleRarity.Common,
        ),
        TitleDef(
            "gate_runner",
            "Road Runner",
            "Walk 20,000 steps in a single day.",
            TitleRule.StepsInDay(20_000),
            TitleRarity.Rare,
        ),
        TitleDef(
            "red_zone_hunter",
            "Red Zone Lifter",
            "Walk 30,000 steps in a single day.",
            TitleRule.StepsInDay(30_000),
            TitleRarity.Epic,
        ),
        // Lifetime steps
        TitleDef(
            "footsteps_in_the_dark",
            "Footsteps in the Dark",
            "Walk 100,000 steps in your lifetime.",
            TitleRule.StepsLifetime(100_000),
            TitleRarity.Common,
        ),
        TitleDef(
            "wandering_soldier",
            "Wandering Soldier",
            "Walk 500,000 steps in your lifetime.",
            TitleRule.StepsLifetime(500_000),
            TitleRarity.Rare,
        ),
        TitleDef(
            "million_march",
            "Million March",
            "Walk 1,000,000 steps in your lifetime.",
            TitleRule.StepsLifetime(1_000_000),
            TitleRarity.Epic,
        ),
        TitleDef(
            "shadow_exodus",
            "Great March",
            "Walk 5,000,000 steps in your lifetime.",
            TitleRule.StepsLifetime(5_000_000),
            TitleRarity.Masterwork,
        ),
        // Distance
        TitleDef(
            "fifty_k_traveler",
            "Fifty-K Traveler",
            "Cover 50 km on foot in your lifetime.",
            TitleRule.DistanceKmLifetime(50.0),
            TitleRarity.Common,
        ),
        TitleDef(
            "path_carver",
            "Path Carver",
            "Cover 250 km on foot in your lifetime.",
            TitleRule.DistanceKmLifetime(250.0),
            TitleRarity.Rare,
        ),
        TitleDef(
            "thousand_gate_runner",
            "Thousand-Kilometre Runner",
            "Cover 1,000 km on foot in your lifetime.",
            TitleRule.DistanceKmLifetime(1_000.0),
            TitleRarity.Epic,
        ),
        // Active calories
        TitleDef(
            "furnace_awake",
            "Furnace Awake",
            "Burn 500 active calories in a single day.",
            TitleRule.ActiveKcalInDay(500),
            TitleRarity.Common,
        ),
        TitleDef(
            "infernal_engine",
            "Infernal Engine",
            "Burn 1,000 active calories in a single day.",
            TitleRule.ActiveKcalInDay(1_000),
            TitleRarity.Rare,
        ),
        // Sleep
        TitleDef(
            "eight_hour_shroud",
            "Eight-Hour Shroud",
            "Sleep 8 hours in a single night.",
            TitleRule.SleepMinutesInNight(480),
            TitleRarity.Common,
        ),
        TitleDef(
            "abyssal_slumber",
            "Abyssal Slumber",
            "Sleep 9 hours in a single night.",
            TitleRule.SleepMinutesInNight(540),
            TitleRarity.Common,
        ),
        // Step-goal consistency
        TitleDef(
            "marching_orders",
            "Marching Orders",
            "Hit a 10,000-step day 10 times.",
            TitleRule.StepGoalDays(10),
            TitleRarity.Rare,
        ),
        TitleDef(
            "cadence_keeper",
            "Cadence Keeper",
            "Hit a 10,000-step day 50 times.",
            TitleRule.StepGoalDays(50),
            TitleRarity.Epic,
        ),
        TitleDef(
            "eternal_vanguard",
            "Eternal Vanguard",
            "Hit a 10,000-step day 100 times.",
            TitleRule.StepGoalDays(100),
            TitleRarity.Masterwork,
        ),
        // Training streaks
        TitleDef(
            "three_day_oath",
            "Three-Day Oath",
            "Train 3 days in a row.",
            TitleRule.TrainingStreak(3),
            TitleRarity.Common,
        ),
        TitleDef(
            "week_of_shadows",
            "Week of Iron",
            "Train 7 days in a row.",
            TitleRule.TrainingStreak(7),
            TitleRarity.Common,
        ),
        TitleDef(
            "fortnight_vigil",
            "Fortnight Vigil",
            "Train 14 days in a row.",
            TitleRule.TrainingStreak(14),
            TitleRarity.Rare,
        ),
        TitleDef(
            "unrelenting_watch",
            "Unrelenting Watch",
            "Train 30 days in a row.",
            TitleRule.TrainingStreak(30),
            TitleRarity.Epic,
        ),
        TitleDef(
            "hundred_day_promise",
            "Hundred-Day Promise",
            "Train 100 days in a row.",
            TitleRule.TrainingStreak(100),
            TitleRarity.Masterwork,
        ),
        // Weekly workout volume
        TitleDef(
            "triple_threat",
            "Triple Threat",
            "Complete 3 workouts in a single week.",
            TitleRule.WorkoutsInWeek(3),
            TitleRarity.Common,
        ),
        TitleDef(
            "fivefold_assault",
            "Fivefold Assault",
            "Complete 5 workouts in a single week.",
            TitleRule.WorkoutsInWeek(5),
            TitleRarity.Rare,
        ),
        TitleDef(
            "six_gate_week",
            "Six-Session Week",
            "Complete 6 workouts in a single week.",
            TitleRule.WorkoutsInWeek(6),
            TitleRarity.Epic,
        ),
        // Skills mastered
        TitleDef(
            "first_technique",
            "First Technique",
            "Master 1 skill.",
            TitleRule.SkillsMastered(1),
            TitleRarity.Common,
        ),
        TitleDef(
            "apprentice_of_five",
            "Apprentice of Five",
            "Master 5 skills.",
            TitleRule.SkillsMastered(5),
            TitleRarity.Rare,
        ),
        TitleDef(
            "ten_folds_form",
            "Tenfold Form",
            "Master 10 skills.",
            TitleRule.SkillsMastered(10),
            TitleRarity.Epic,
        ),
        TitleDef(
            "keeper_of_twenty_five",
            "Keeper of Twenty-Five",
            "Master 25 skills.",
            TitleRule.SkillsMastered(25),
            TitleRarity.Epic,
        ),
        TitleDef(
            "fifty_fanged_style",
            "Fifty-Fanged Style",
            "Master 50 skills.",
            TitleRule.SkillsMastered(50),
            TitleRarity.Masterwork,
        ),
        TitleDef(
            "grandmaster_of_all",
            "Grandmaster of All",
            "Master all 84 skills. The tree is yours.",
            TitleRule.SkillsMastered(84),
            TitleRarity.Masterwork,
        ),
        // Practice attempts
        TitleDef(
            "first_hundred_cuts",
            "First Hundred Cuts",
            "Log 25 practice attempts.",
            TitleRule.PracticeAttempts(25),
            TitleRarity.Common,
        ),
        TitleDef(
            "hundred_cuts_deep",
            "Hundred Cuts Deep",
            "Log 100 practice attempts.",
            TitleRule.PracticeAttempts(100),
            TitleRarity.Rare,
        ),
        TitleDef(
            "five_hundred_repetitions",
            "Five Hundred Repetitions",
            "Log 500 practice attempts.",
            TitleRule.PracticeAttempts(500),
            TitleRarity.Epic,
        ),
        // ---- Activity deeds ----
        // Lifetime activity minutes
        TitleDef("kindled", "Kindled", "Log 60 activity minutes — the fire starts.", TitleRule.ActivityMinutes(60), TitleRarity.Common),
        TitleDef("restless_shadows", "Restless Miles", "Log 600 activity minutes in your lifetime.", TitleRule.ActivityMinutes(600), TitleRarity.Common),
        TitleDef("tireless_wind", "Tireless Wind", "Log 3,000 activity minutes in your lifetime.", TitleRule.ActivityMinutes(3_000), TitleRarity.Rare),
        TitleDef("storm_runner", "Storm Runner", "Log 12,000 activity minutes in your lifetime.", TitleRule.ActivityMinutes(12_000), TitleRarity.Epic),
        TitleDef("wind_that_never_sleeps", "Wind That Never Sleeps", "Log 50,000 activity minutes in your lifetime.", TitleRule.ActivityMinutes(50_000), TitleRarity.Masterwork),
        // Lifetime activity distance
        TitleDef("road_of_shadows", "The Long Road", "Cover 10 km through logged activities.", TitleRule.ActivityDistanceKm(10.0), TitleRarity.Common),
        TitleDef("hundred_gate_march", "Hundred-Kilometre March", "Cover 100 km through logged activities.", TitleRule.ActivityDistanceKm(100.0), TitleRarity.Common),
        TitleDef("horizon_breaker", "Horizon Breaker", "Cover 500 km through logged activities.", TitleRule.ActivityDistanceKm(500.0), TitleRarity.Rare),
        TitleDef("world_walker", "World Walker", "Cover 2,000 km through logged activities.", TitleRule.ActivityDistanceKm(2_000.0), TitleRarity.Epic),
        TitleDef("beyond_the_map", "Beyond the Map", "Cover 10,000 km through logged activities.", TitleRule.ActivityDistanceKm(10_000.0), TitleRarity.Masterwork),
        // Distinct activities tried
        // The ceiling here is the catalogue's activity count (Seed.activities,
        // 43 today). Titles lives in domain and Seed in data, and data already
        // imports domain — reading the count from Seed would close a dependency
        // cycle — so the number is stated here and pinned against Seed by
        // TitleReachabilityTest; growing the catalogue must update both.
        TitleDef("three_paths", "Three Paths", "Try 3 different activities.", TitleRule.DistinctActivities(3), TitleRarity.Common),
        TitleDef("ten_paths", "Ten Paths", "Try 10 different activities.", TitleRule.DistinctActivities(10), TitleRarity.Rare),
        TitleDef("twenty_five_paths", "Twenty-Five Paths", "Try 25 different activities.", TitleRule.DistinctActivities(25), TitleRarity.Rare),
        TitleDef("thirty_five_paths", "Thirty-Five Paths", "Try 35 different activities.", TitleRule.DistinctActivities(35), TitleRarity.Epic),
        TitleDef("walker_of_all_roads", "Walker of All Roads", "Try all 43 activities the catalogue offers. Nothing is foreign to you.", TitleRule.DistinctActivities(43), TitleRarity.Masterwork),
        // Single long runs
        TitleDef("five_k_razor", "Five-K Razor", "Log a 5 km run in a single session.", TitleRule.LongestRun(5.0), TitleRarity.Common),
        TitleDef("ten_k_hunter", "Ten-K Lifter", "Log a 10 km run in a single session.", TitleRule.LongestRun(10.0), TitleRarity.Common),
        TitleDef("half_gate_marathon", "Half Marathon", "Log a 21.1 km run in a single session.", TitleRule.LongestRun(21.1), TitleRarity.Rare),
        TitleDef("gate_marathon", "Marathon", "Log a 42.2 km run in a single session.", TitleRule.LongestRun(42.2), TitleRarity.Epic),
        TitleDef("shadow_ultra", "Ultra", "Log a 100 km run in a single session. The road was never this long.", TitleRule.LongestRun(100.0), TitleRarity.Masterwork),
        // Single long swims
        TitleDef("first_water", "First Water", "Log a 1 km swim in a single session.", TitleRule.LongestSwim(1.0), TitleRarity.Common),
        TitleDef("deep_current", "Deep Current", "Log a 2.5 km swim in a single session.", TitleRule.LongestSwim(2.5), TitleRarity.Rare),
        TitleDef("abyss_lapper", "Abyss Lapper", "Log a 5 km swim in a single session.", TitleRule.LongestSwim(5.0), TitleRarity.Epic),
        TitleDef("leviathan_swimmer", "Leviathan Swimmer", "Log a 10 km swim in a single session.", TitleRule.LongestSwim(10.0), TitleRarity.Masterwork),
        // Hardest climbing grade
        TitleDef("first_send", "First Send", "Send a route graded V1 or harder.", TitleRule.HardestGrade("V1"), TitleRarity.Common),
        TitleDef("chalk_dusted", "Chalk Dusted", "Send a route graded V2 or harder.", TitleRule.HardestGrade("V2"), TitleRarity.Common),
        TitleDef("grip_of_the_abyss", "Grip of the Abyss", "Send a route graded V5 or harder.", TitleRule.HardestGrade("V5"), TitleRarity.Rare),
        TitleDef("vertical_sovereign", "Vertical Master", "Send a route graded V8 or harder.", TitleRule.HardestGrade("V8"), TitleRarity.Epic),
        TitleDef("gravity_defiant", "Gravity Defiant", "Send a route graded V11 or harder. Walls kneel.", TitleRule.HardestGrade("V11"), TitleRarity.Masterwork),
        // Sport sessions
        TitleDef("first_arena", "First Arena", "Complete a session with sport play in it.", TitleRule.SportSessions(1), TitleRarity.Common),
        TitleDef("arena_regular", "Arena Regular", "Complete 10 sessions with sport play in them.", TitleRule.SportSessions(10), TitleRarity.Common),
        TitleDef("field_commander", "Field Commander", "Complete 50 sessions with sport play in them.", TitleRule.SportSessions(50), TitleRarity.Rare),
        TitleDef("champion_of_games", "Champion of Games", "Complete 100 sessions with sport play in them.", TitleRule.SportSessions(100), TitleRarity.Epic),
        // ---- Strength milestones ----
        // Load thresholds are estimated-1RM multiples of bodyweight, read per
        // sex (male / female). Female cells come from the ExRx-derived
        // restatement Skills.femaleBars already ships (squat 0.6/1.25,
        // bench 0.5, press 0.35, deadlift 1.4/2.25); the weighted pull-up has
        // no published ExRx cell, so it scales by the app's own upper-body
        // factor StrengthIndex.UPPER_BODY_FEMALE (Bishop 1983): 0.5 / 1.54
        // rounds down to 0.3. Any set estimates the 1RM (Epley, rep term
        // capped at 12), so a lifter logging fives still earns a one-rep deed.
        TitleDef(
            "iron_standard",
            "Iron Standard",
            "Squat your own bodyweight for an estimated 1RM.",
            TitleRule.LiftMultiple(setOf("back squat", "front squat"), male = 1.0, female = 0.6),
            TitleRarity.Common,
            descriptionFemale = "Squat 0.6x your bodyweight for an estimated 1RM.",
        ),
        TitleDef(
            "bench_mark",
            "Bench Mark",
            "Bench press your bodyweight for an estimated 1RM.",
            TitleRule.LiftMultiple(setOf("bench press", "incline bench press", "close-grip bench press"), male = 1.0, female = 0.5),
            TitleRarity.Rare,
            descriptionFemale = "Bench press 0.5x your bodyweight for an estimated 1RM.",
        ),
        TitleDef(
            "iron_wings",
            "Iron Wings",
            "Weight a pull-up or chin-up with half your bodyweight for an estimated 1RM.",
            TitleRule.LiftMultiple(setOf("pull-up", "chin-up", "archer pull-up"), male = 0.5, female = 0.3),
            TitleRarity.Rare,
            descriptionFemale = "Weight a pull-up or chin-up with 0.3x your bodyweight for an estimated 1RM.",
        ),
        TitleDef(
            "crown_press",
            "Crown Press",
            "Overhead press three quarters of your bodyweight for an estimated 1RM.",
            TitleRule.LiftMultiple(setOf("overhead press", "push press"), male = 0.75, female = 0.35),
            TitleRarity.Rare,
            descriptionFemale = "Overhead press 0.35x your bodyweight for an estimated 1RM.",
        ),
        TitleDef(
            "throne_of_iron",
            "Throne of Iron",
            "Squat double bodyweight for an estimated 1RM.",
            TitleRule.LiftMultiple(setOf("back squat", "front squat"), male = 2.0, female = 1.25),
            TitleRarity.Epic,
            descriptionFemale = "Squat 1.25x your bodyweight for an estimated 1RM.",
        ),
        TitleDef(
            "titans_pull",
            "Titan's Pull",
            "Deadlift double bodyweight for an estimated 1RM.",
            TitleRule.LiftMultiple(setOf("deadlift", "sumo deadlift"), male = 2.0, female = 1.4),
            TitleRarity.Epic,
            descriptionFemale = "Deadlift 1.4x your bodyweight for an estimated 1RM.",
        ),
        TitleDef(
            "atlas",
            "Atlas",
            "Deadlift triple bodyweight for an estimated 1RM. The sky holds itself up.",
            TitleRule.LiftMultiple(setOf("deadlift", "sumo deadlift"), male = 3.0, female = 2.25),
            TitleRarity.Masterwork,
            descriptionFemale = "Deadlift 2.25x your bodyweight for an estimated 1RM. The sky holds itself up.",
        ),
        // Rep-volume feats. The counts are deliberately the same for both
        // sexes: these are submaximal endurance feats of bodyweight work, and
        // muscular endurance gaps between the sexes are far smaller than the
        // maximal-strength gaps the load titles above price in.
        TitleDef(
            "century_of_rungs",
            "Century of Rungs",
            "Log 100 pull-ups or chin-ups in a single session.",
            TitleRule.SessionReps(setOf("pull-up", "chin-up"), count = 100),
            TitleRarity.Rare,
        ),
        TitleDef(
            "two_hundred_suns",
            "Two Hundred Suns",
            "Log 200 push-ups in a single session.",
            TitleRule.SessionReps(setOf("push-up"), count = 200),
            TitleRarity.Rare,
        ),
        TitleDef(
            "unshaking",
            "The Unshaking",
            "Hold a single static position for 240 seconds without letting go.",
            TitleRule.LongestHold(240),
            TitleRarity.Rare,
        ),
    )

    fun byId(id: String): TitleDef? = ALL.firstOrNull { it.id == id }

    /** The worn title's rarity, or null for a null/unknown id. */
    fun rarityOf(titleId: String?): TitleRarity? = titleId?.let { id -> byId(id)?.rarity }

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
        val activityMinutes: Int = 0, // lifetime minutes across all non-REPS activity
        val activityDistanceKm: Double = 0.0,
        val distinctActivities: Int = 0, // how many different activities ever logged
        val bestRunKm: Double = 0.0,
        val bestSwimKm: Double = 0.0,
        val hardestGrade: String = "", // raw text of hardest recognised climb sent
        val sportSessions: Int = 0, // completed sessions containing any Sport-category work
        // Sex scales the bar of every LiftMultiple deed, exactly as the
        // strength score scales the feat; defaults to MALE so a ledger built
        // without a profile never falsely strips a female lifter of reach.
        val sex: Sex = Sex.MALE,
        // Normalised exercise name -> best marked-load e1RM as a multiple of
        // the bodyweight in force at that session. Empty when no weigh-in
        // history was supplied: an unknown bodyweight can never half-satisfy
        // a load deed.
        val bestLiftMultiple: Map<String, Double> = emptyMap(),
        // Normalised exercise name -> best rep total inside one session.
        val bestSessionReps: Map<String, Int> = emptyMap(),
        val bestHoldSeconds: Int = 0,
    )

    /** Catalogue names drift in case and padding between screens; keys never do. */
    fun normaliseName(name: String): String = name.lowercase().trim()

    /**
     * The one place a ledger is assembled. Unlocking used to build a partial
     * ledger at session-complete (no steps, no skills), so every step, activity
     * and skill title was unreachable no matter what the Codex displayed.
     *
     * CALLERS MUST pass the full exercise catalogue in [exercises] (id ->
     * Exercise). Metric and category live on the Exercise, not the set, so
     * without the catalogue every activity field would silently stay zero
     * (sets from unknown exercises are treated as REPS/lifting — never
     * activity, never strength pollution).
     */
    fun ledgerOf(
        totalXp: Long,
        history: List<Pair<WorkoutSession, List<SessionSet>>>,
        healthDays: List<HealthDay>,
        practices: List<SkillPractice>,
        // No default: an omitted catalogue would zero every activity deed
        // without erroring, which is the exact silent-failure class that made
        // step and skill deeds unreachable before this was centralised.
        exercises: Map<Long, Exercise>,
        // ISO weekdays a preset is scheduled for. Empty means "no schedule
        // known", which trainingStreakDays reads as the strict consecutive
        // rule — never as "no streak".
        scheduledWeekdays: Set<Int> = emptySet(),
        // Weigh-in history for the bodyweight IN FORCE at each session — the
        // load deeds compare against that, not today's weight, or a lifter
        // who gained weight would silently lose a title she earned. Null (or
        // an empty history) leaves every load field at its empty default.
        bodyweightAt: ((Long) -> Double)? = null,
        sex: Sex = Sex.MALE,
    ): Ledger {
        val doneSets = history.flatMap { (_, sets) -> sets.filter { it.done } }
        val metricOf: (SessionSet) -> ExerciseMetric =
            { exercises[it.exerciseId]?.metric ?: ExerciseMetric.REPS }
        val categoryOf: (SessionSet) -> String? = { exercises[it.exerciseId]?.category }
        // An "activity" is cardio/sport/climbing. Reps AND static holds are
        // strength work: bucketing a hold here would count its seconds as
        // "active minutes" and drop it out of the lifting ledger entirely.
        val activitySets = doneSets.filterNot { metricOf(it).isStrength }
        val zone = ZoneId.systemDefault()
        val workoutDates = history
            .map {
                Instant.ofEpochMilli(it.first.completedAtMs ?: it.first.startedAtMs)
                    .atZone(zone).toLocalDate()
            }
            .toSet()
        val claimed = practices.filter { it.claimed }
        // Best single-session distance per category, so a 10 km run inside a
        // mixed session still counts as a 10 km run.
        fun bestSessionKm(category: String): Double =
            history.maxOfOrNull { (_, sets) ->
                sets.filter { it.done && categoryOf(it) == category }
                    .sumOf { it.distanceM ?: 0.0 }
            }?.div(1000.0) ?: 0.0
        // ---- strength milestones ----
        // The marked kilo is the load the deed is about (bar load for the
        // free-weight band; machines pass through loadFactor like every other
        // consumer). Epley converts any honest set to an estimated 1RM, with
        // the rep term capped at 12 because extrapolating beyond that flatters
        // volume into imaginary strength. Assisted machines are excluded:
        // their marked kilo SUBTRACTS load, so counting it would award deeds
        // for doing less. Bodyweight-rep moves (unweighted pull-ups) mark no
        // kilo and so can never satisfy a load deed — the rep deeds carry them.
        val liftMultiples = HashMap<String, Double>()
        val bestSessionReps = HashMap<String, Int>()
        var bestHoldSeconds = 0
        for ((session, sets) in history) {
            val repsThisSession = HashMap<String, Int>()
            for (set in sets.filter { it.done }) {
                val exercise = exercises[set.exerciseId] ?: continue
                if (!metricOf(set).isStrength) continue
                val name = normaliseName(exercise.name)
                repsThisSession[name] = (repsThisSession[name] ?: 0) + set.reps
                if (exercise.metric == ExerciseMetric.HOLD) {
                    val held = set.durationSec ?: 0
                    if (held > bestHoldSeconds) bestHoldSeconds = held
                } else if (set.reps > 0 && bodyweightAt != null &&
                    !exercise.name.contains("assisted", ignoreCase = true)
                ) {
                    val bodyweight = bodyweightAt(session.startedAtMs)
                    if (bodyweight > 0.0) {
                        val e1rm = (set.weightKg ?: 0.0).coerceAtLeast(0.0) *
                            MovementDifficulty.loadFactor(exercise.name) *
                            (1.0 + minOf(set.reps, 12) / 30.0)
                        val multiple = e1rm / bodyweight
                        if (multiple > (liftMultiples[name] ?: 0.0)) liftMultiples[name] = multiple
                    }
                }
            }
            for ((name, reps) in repsThisSession) {
                if (reps > (bestSessionReps[name] ?: 0)) bestSessionReps[name] = reps
            }
        }
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
            // Sum seconds before dividing so a 30s set isn't floored to zero.
            activityMinutes = activitySets.sumOf { (it.durationSec ?: 0) }.div(60),
            activityDistanceKm = activitySets.sumOf { it.distanceM ?: 0.0 }.div(1000.0),
            distinctActivities = activitySets.map { it.exerciseId }.distinct().size,
            bestRunKm = bestSessionKm("Cardio"),
            bestSwimKm = bestSessionKm("Water"),
            // Only recognised grades can hold the record — garbage text can
            // never take the hardest-climb crown or award a deed.
            hardestGrade = doneSets
                .filter { s ->
                    !s.grade.isNullOrBlank() &&
                        (exercises[s.exerciseId]?.metric == ExerciseMetric.ATTEMPTS_GRADE)
                }
                // Only recognised grades may hold the record — garbage text
                // can never take the crown or satisfy a HardestGrade deed.
                .mapNotNull { s -> s.grade?.takeIf { g -> GradeRank.rank(g) != null } }
                .maxByOrNull { g -> GradeRank.rank(g) ?: Int.MIN_VALUE } ?: "",
            sportSessions = history.count { (_, sets) ->
                sets.any { it.done && exercises[it.exerciseId]?.category == "Sport" }
            },
            bestWeekWorkouts = bestWeekWorkouts(workoutDates),
            // This was never assigned, so it defaulted to 0 and every
            // TrainingStreak deed (7/14/30/100 days) was unreachable.
            trainingStreakDays = trainingStreakDays(workoutDates, scheduledWeekdays),
            sex = sex,
            bestLiftMultiple = liftMultiples,
            bestSessionReps = bestSessionReps,
            bestHoldSeconds = bestHoldSeconds,
        )
    }

    /** How far back a streak walk looks — the same two years [Streak.current] allows. */
    const val STREAK_WINDOW_DAYS = 730

    /**
     * Consecutive training days, under ONE rule: [Streak.current]. Today
     * already walked the schedule-aware rule while this function walked a
     * date-only one, so the same lifter could read a 12-day streak on the home
     * screen and a 3-day streak on the leaderboard and in the idle rate.
     *
     * [scheduledWeekdays] are the ISO weekdays (1 = Monday) a preset is
     * scheduled for. A day that is scheduled and not trained breaks the
     * streak; an unscheduled day is rest and neither grows nor breaks it.
     * With NO schedule at all every day counts as a training day, which is the
     * strict consecutive rule — otherwise nothing could ever break a streak
     * and the number would just be a lifetime count of training days.
     *
     * [today] is a parameter so the boundaries are testable; production callers
     * take the default and stay device-local, matching the zone the dates
     * themselves were bucketed in.
     */
    fun trainingStreakDays(
        dates: Set<LocalDate>,
        scheduledWeekdays: Set<Int> = emptySet(),
        today: LocalDate = LocalDate.now(),
    ): Int {
        if (dates.isEmpty()) return 0
        val records = (0 until STREAK_WINDOW_DAYS).map { offset ->
            val date = today.minusDays(offset.toLong())
            val scheduled = scheduledWeekdays.isEmpty() || date.dayOfWeek.value in scheduledWeekdays
            Streak.DayRecord(
                date = date,
                scheduledDay = if (scheduled) date.dayOfWeek.value else null,
                completed = date in dates,
            )
        }
        return Streak.current(records, today)
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
        is TitleRule.ActivityMinutes -> ledger.activityMinutes >= rule.minutes
        is TitleRule.ActivityDistanceKm -> ledger.activityDistanceKm >= rule.km
        is TitleRule.DistinctActivities -> ledger.distinctActivities >= rule.count
        is TitleRule.LongestRun -> ledger.bestRunKm >= rule.km
        is TitleRule.LongestSwim -> ledger.bestSwimKm >= rule.km
        is TitleRule.HardestGrade -> {
            // Both sides must be recognised; an unrecognised rule grade or
            // ledger grade can never satisfy the deed.
            val sent = GradeRank.rank(ledger.hardestGrade)
            val asked = GradeRank.rank(rule.grade)
            sent != null && asked != null && sent >= asked
        }
        is TitleRule.SportSessions -> ledger.sportSessions >= rule.count
        is TitleRule.LiftMultiple -> {
            val bar = if (ledger.sex == Sex.FEMALE) rule.female else rule.male
            rule.names.any { (ledger.bestLiftMultiple[normaliseName(it)] ?: 0.0) >= bar }
        }
        is TitleRule.SessionReps ->
            rule.names.any { (ledger.bestSessionReps[normaliseName(it)] ?: 0) >= rule.count }
        is TitleRule.LongestHold -> ledger.bestHoldSeconds >= rule.seconds
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
        is TitleRule.ActivityMinutes ->
            Progress(ledger.activityMinutes.toLong(), rule.minutes.toLong(), "activity minutes")
        is TitleRule.ActivityDistanceKm ->
            Progress(ledger.activityDistanceKm.toLong(), rule.km.toLong(), "activity km lifetime")
        is TitleRule.DistinctActivities ->
            Progress(ledger.distinctActivities.toLong(), rule.count.toLong(), "activities tried")
        is TitleRule.LongestRun ->
            // Floored, deliberately. Rounding read "5 of 5 km" for a 4.9 km
            // run on a title that is NOT earned — a progress line claiming
            // completion is a worse lie than one a kilometre short.
            Progress(ledger.bestRunKm.toLong(), rule.km.toLong(), "km in one run")
        is TitleRule.LongestSwim ->
            Progress(ledger.bestSwimKm.toLong(), rule.km.toLong(), "km in one swim")
        is TitleRule.HardestGrade ->
            Progress(
                (GradeRank.rank(ledger.hardestGrade) ?: 0).toLong(),
                (GradeRank.rank(rule.grade) ?: 0).toLong(),
                "hardest grade",
            )
        is TitleRule.SportSessions ->
            Progress(ledger.sportSessions.toLong(), rule.count.toLong(), "sport sessions")
        is TitleRule.TrainingStreak ->
            Progress(ledger.trainingStreakDays.toLong(), rule.days.toLong(), "day streak")
        is TitleRule.WorkoutsInWeek ->
            Progress(ledger.bestWeekWorkouts.toLong(), rule.count.toLong(), "workouts in a week")
        is TitleRule.LiftMultiple -> {
            // Floored to whole percent like LongestRun floors to whole km:
            // rounding 0.4999x up to the bar would claim a deed not earned.
            val bar = if (ledger.sex == Sex.FEMALE) rule.female else rule.male
            val best = rule.names.maxOfOrNull { ledger.bestLiftMultiple[normaliseName(it)] ?: 0.0 } ?: 0.0
            Progress((best * 100).toLong(), (bar * 100).toLong(), "% of bodyweight (estimated 1RM)")
        }
        is TitleRule.SessionReps -> {
            val best = rule.names.maxOfOrNull { ledger.bestSessionReps[normaliseName(it)] ?: 0 } ?: 0
            Progress(best.toLong(), rule.count.toLong(), "reps in one session")
        }
        is TitleRule.LongestHold ->
            Progress(ledger.bestHoldSeconds.toLong(), rule.seconds.toLong(), "seconds in one hold")
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
        is TitleRule.LiftMultiple, is TitleRule.SessionReps, is TitleRule.LongestHold -> "Strength"
        is TitleRule.ActivityMinutes, is TitleRule.ActivityDistanceKm,
        is TitleRule.DistinctActivities, is TitleRule.LongestRun, is TitleRule.LongestSwim,
        is TitleRule.HardestGrade, is TitleRule.SportSessions -> "Activities"
    }

    /** Returns definitions whose rules are met and that are not in [already]. */
    fun newlyUnlocked(ledger: Ledger, already: Set<String>): List<TitleDef> =
        ALL.filter { it.id !in already && satisfied(it.rule, ledger) }
}

/**
 * Cross-system climbing-grade ordering. Grades are free text, so this parses
 * V-scale, Font and YDS into one approximate hardness index (higher = harder).
 * Anchors follow common conversion charts: 6A ≈ V0, 7A ≈ V4, 8A ≈ V8;
 * YDS is anchored 5.11a ≈ V2 (5.12a ≈ V6); each YDS step ≈ one V-grade. The index is
 * only ever used to order a single "hardest send" — never for scoring.
 *
 * Unrecognised text (anything that doesn't parse, e.g. "insane", "5.crap",
 * "V99", "6Z+") returns null: it never ranks above a recognised grade, never
 * wins hardestGrade, and never satisfies a HardestGrade deed.
 */
object GradeRank {
    fun rank(text: String): Int? {
        val g = text.trim().uppercase()
        // V-scale: VB, V0..V17
        if (g == "VB") return 0
        Regex("^V(\\d{1,2})$").find(g)?.let { m ->
            val n = m.groupValues[1].toInt()
            return if (n in 0..17) n + 1 else null
        }
        Regex("^([4-8])([ABC])(\\+)?$").find(g)?.let { m ->
            val idx = (m.groupValues[1].toInt() - 4) * 4 +
                (m.groupValues[2][0] - 'A') +
                (if (m.groupValues[3] == "+") 1 else 0)
            return idx - 7 // 6A -> V0
        }
        // YDS: 5.0 through 5.15 with optional a-d
        Regex("^5\\.(\\d{1,2})([ABCD])?$").find(g)?.let { m ->
            val minor = m.groupValues[1].toInt()
            if (minor !in 0..15) return null
            val letter = if (m.groupValues[2].isEmpty()) 0 else m.groupValues[2][0] - 'A'
            // No floor: 5.9 must still rank below 5.10a; negative just means
            // "easier than VB", which no deed or anchor ever reaches into.
            return (minor - 11) * 4 + letter + 3 // 5.12a ≈ V6
        }
        return null
    }
}
