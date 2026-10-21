package com.ironvellum.app.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

/** How a recap reads: nothing logged, one workout in an otherwise busier habit, or an ordinary week. */
enum class RecapKind { EMPTY, LIGHT, FULL }

/** Where a muscle's hard sets stand against the 4-set minimum and the plan's aim. */
enum class AimState { NOT_TRAINED, BELOW_MINIMUM, UNDER_PLAN, ON_AIM }

/** One muscle's week: its hard [sets], the plan's [aim] for it (null with no plan) and the verdict. */
data class MuscleRow(val muscle: Muscle, val sets: Double, val aim: IntRange?, val state: AimState)

/** The streak as the recap tells it, as of the week's last day (or today, in the current week). */
data class RecapStreak(
    val days: Int,
    /** The streak is the longest the lifter has had. */
    val longest: Boolean,
    /** Tomorrow the chain closes: the last workout was exactly [Streak.MAX_GAP_DAYS] days ago. */
    val atRisk: Boolean,
    val lastWorkout: LocalDate?,
    /** The last day a workout still keeps it going; null when no streak is alive. */
    val trainBy: LocalDate?,
)

/** What the plan holds for the coming week, and what the week just ended left short of it. */
data class RecapNext(
    val planned: Int,
    val firstName: String,
    /** ISO weekday (1 = Monday) of the first planned workout. */
    val firstDay: Int,
    val short: List<Muscle>,
    /** The planned workout that already trains the short muscles most, where to add sets for them. */
    val addName: String?,
    val addDay: Int?,
)

/**
 * One calendar week, Monday to Sunday, read for the recap screen, the Sunday notification and the share card.
 * [stats] are the figures of [PeriodStats] for the week and [previous] those of the week before it.
 */
data class WeeklyRecap(
    val weekStart: LocalDate,
    val stats: PeriodStats,
    val previous: PeriodStats,
    val kind: RecapKind,
    /** Every tracked muscle, the most hard sets first. */
    val muscles: List<MuscleRow>,
    val streak: RecapStreak,
    val next: RecapNext?,
) {
    val weekEnd: LocalDate get() = weekStart.plusDays(6)

    /** Muscles within the plan's aim (or past the minimum, with no plan). */
    val onAim: List<MuscleRow> get() = muscles.filter { it.state == AimState.ON_AIM }

    /** Volume against the week before, in whole percent; null when there was no volume to compare with. */
    val volumeChangePercent: Int?
        get() = previous.volumeKg.takeIf { it > 0.0 }?.let { ((stats.volumeKg - it) / it * 100).roundToInt() }

    companion object {
        /** The aim is the plan's weekly sets for a muscle give or take a fifth: 5 sets is 4 to 6, 10 is 8 to 12. */
        private const val AIM_SPREAD = 0.2

        /** The Monday of the week holding [date]. */
        fun weekStartOf(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        /** The plan's aim for a muscle that gets [planned] fractional sets a week; null when the plan never trains it. */
        fun aimFor(planned: Double): IntRange? {
            if (planned <= 0.0) return null
            val low = (planned * (1 - AIM_SPREAD)).roundToInt().coerceAtLeast(1)
            val high = (planned * (1 + AIM_SPREAD)).roundToInt().coerceAtLeast(low)
            return low..high
        }

        /** The week's verdict for a muscle with [sets] hard sets under [aim]. */
        fun stateOf(sets: Double, aim: IntRange?): AimState = when {
            sets <= 1e-9 -> AimState.NOT_TRAINED
            sets < ProgramRules.COVERED_MIN - 1e-9 -> AimState.BELOW_MINIMUM
            aim != null && sets < aim.first - 1e-9 -> AimState.UNDER_PLAN
            else -> AimState.ON_AIM
        }

        /**
         * The recap of the week starting [weekStart] (a Monday).
         *
         * @param plan the scheduled workouts of the lifter's plan, for the aim and the coming week; empty with no plan.
         * @param today the day the recap is read on: the streak is taken as of the week's end or today, whichever is first.
         */
        fun build(
            history: List<Pair<WorkoutSession, List<SessionSet>>>,
            exercises: Map<Long, Exercise>,
            plan: List<PlannedPreset>,
            weekStart: LocalDate,
            today: LocalDate,
            zone: ZoneId = ZoneId.systemDefault(),
        ): WeeklyRecap {
            val stats = PeriodStats.of(history, exercises, weekStart, weekStart.plusDays(7), zone)
            val previous = PeriodStats.of(history, exercises, weekStart.minusDays(7), weekStart, zone)
            val kind = when {
                stats.workouts == 0 -> RecapKind.EMPTY
                stats.workouts == 1 && previous.workouts >= 2 -> RecapKind.LIGHT
                else -> RecapKind.FULL
            }

            val planned = ProgramRules.weeklyVolume(plan)
            val muscles = ProgramRules.TRACKED.map { muscle ->
                val sets = stats.muscleSets[muscle] ?: 0.0
                val aim = aimFor(planned[muscle] ?: 0.0)
                MuscleRow(muscle, sets, aim, stateOf(sets, aim))
            }.sortedByDescending { it.sets }

            val asOf = minOf(today, weekStart.plusDays(6))
            val dates = history.map { (session, _) ->
                java.time.Instant.ofEpochMilli(session.completedAtMs ?: session.startedAtMs).atZone(zone).toLocalDate()
            }.toSet()
            val days = Streak.current(dates, asOf)
            val last = dates.filter { !it.isAfter(asOf) }.maxOrNull()
            val streak = RecapStreak(
                days = days,
                longest = days > 0 && days >= Streak.longest(dates, asOf),
                atRisk = Streak.atRisk(dates, asOf),
                lastWorkout = last,
                trainBy = if (days > 0 && last != null) last.plusDays(Streak.MAX_GAP_DAYS) else null,
            )

            return WeeklyRecap(weekStart, stats, previous, kind, muscles, streak, next(plan, muscles, weekStart, today))
        }

        /** The coming-week card: only for the week just ended (or the one before), and only when the plan has days. */
        private fun next(plan: List<PlannedPreset>, muscles: List<MuscleRow>, weekStart: LocalDate, today: LocalDate): RecapNext? {
            if (!today.isBefore(weekStart.plusDays(14))) return null
            val days = plan.filter { it.scheduledDay != null }.sortedBy { it.scheduledDay }
            val first = days.firstOrNull() ?: return null
            val short = muscles.filter { it.state == AimState.NOT_TRAINED || it.state == AimState.BELOW_MINIMUM }.map { it.muscle }
            val add = if (short.isEmpty()) null else days
                .map { preset -> preset to ProgramRules.weeklyVolume(listOf(preset)).filterKeys { it in short }.values.sum() }
                .filter { it.second > 0.0 }
                .maxByOrNull { it.second }
                ?.first
            return RecapNext(days.size, first.name, first.scheduledDay!!, short, add?.name, add?.scheduledDay)
        }
    }
}
