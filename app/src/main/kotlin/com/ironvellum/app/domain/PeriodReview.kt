package com.ironvellum.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.ZoneId
import java.time.temporal.ChronoUnit

enum class ReviewKind { MONTH, YEAR }

/**
 * A calendar month or year, as dates: [from] is its first day and [to] the first day after it, in the lifter's zone
 * (the same half-open range [PeriodStats] reads, so December into January and a clock change land where they should).
 */
data class ReviewPeriod(val kind: ReviewKind, val from: LocalDate) {
    init {
        require(from == normalised(kind, from)) { "a $kind starts on its first day, not $from" }
    }

    val to: LocalDate get() = if (kind == ReviewKind.MONTH) from.plusMonths(1) else from.plusYears(1)

    /** The last day inside the period. */
    val last: LocalDate get() = to.minusDays(1)

    /** The month before a month, the year before a year. */
    val previous: ReviewPeriod get() = of(kind, from.minusDays(1))

    /** Stable, sortable: "2026-09" for a month, "2026" for a year. Names a period where one is remembered (a dismissed card). */
    val key: String get() = if (kind == ReviewKind.MONTH) "%04d-%02d".format(from.year, from.monthValue) else "%04d".format(from.year)

    companion object {
        private fun normalised(kind: ReviewKind, date: LocalDate): LocalDate =
            if (kind == ReviewKind.MONTH) date.withDayOfMonth(1) else date.withDayOfYear(1)

        /** The month or year holding [date]. */
        fun of(kind: ReviewKind, date: LocalDate): ReviewPeriod = ReviewPeriod(kind, normalised(kind, date))
    }
}

/** When Today offers a review and which finished periods the Stats list holds. Pure, like [RecapSchedule]. */
object ReviewSchedule {
    /** The card shows on the first seven days of a month (of a year, in January). */
    const val WINDOW_DAYS = 7

    /**
     * The review Today offers on [today]: in days 1 to 7 of a month, last month's; in days 1 to 7 of January, last year's
     * instead (December stays in the Stats list, so January shows one card, not two). Null outside that window, when the
     * period had no workouts ([workoutsIn]), or when the lifter [dismissed] it (by [ReviewPeriod.key]).
     */
    fun offeredOn(today: LocalDate, workoutsIn: (ReviewPeriod) -> Int, dismissed: Set<String> = emptySet()): ReviewPeriod? {
        if (today.dayOfMonth > WINDOW_DAYS) return null
        val period = if (today.month == Month.JANUARY) {
            ReviewPeriod.of(ReviewKind.YEAR, today.minusYears(1))
        } else {
            ReviewPeriod.of(ReviewKind.MONTH, today.minusMonths(1))
        }
        if (period.key in dismissed) return null
        return period.takeIf { workoutsIn(it) > 0 }
    }

    /**
     * The finished periods with a workout in them, newest first: the latest [months] months and [years] years. A period
     * is finished once [today] is past its last day; a year comes before the December it ends.
     */
    fun listed(workoutDays: Collection<LocalDate>, today: LocalDate, months: Int = 3, years: Int = 2): List<ReviewPeriod> {
        fun finished(kind: ReviewKind, limit: Int) = workoutDays
            .map { ReviewPeriod.of(kind, it) }
            .distinct()
            .filter { !it.to.isAfter(today) }
            .sortedByDescending { it.from }
            .take(limit)
        return (finished(ReviewKind.MONTH, months) + finished(ReviewKind.YEAR, years))
            .sortedWith(compareByDescending<ReviewPeriod> { it.to }.thenByDescending { it.kind })
    }
}

/** One lift's change over a period: its best estimated 1RM before it ([beforeKg], null for a lift first trained in it) and at its end. */
data class ReviewLift(
    val name: String,
    val variation: String,
    val beforeKg: Double?,
    val afterKg: Double,
    /** Points for a sparkline, oldest first; empty when there is nothing to draw. */
    val series: List<Double>,
    val set: SessionSet,
    val addsToBody: Boolean,
    val atMs: Long,
) {
    val label: String get() = Variation.label(name, variation)
    val isNew: Boolean get() = beforeKg == null
    val gainKg: Double? get() = beforeKg?.let { afterKg - it }
}

/** A skill claimed inside the period: its tier and the XP it paid. */
data class SkillUnlock(val name: String, val tier: Int, val xp: Int, val atMs: Long)

/** What a month's or year's review shows in Today's card and the Stats list: three figures, read cheaply. */
data class ReviewSummary(val period: ReviewPeriod, val workouts: Int, val volumeKg: Double, val liftsGained: Int)

/**
 * A month or a year of training read for the review screen and its share card. The figures are [PeriodStats] over the
 * period, plus what only a longer stretch has: its best week, the lifts that gained or began, the streak at its end and
 * the skills claimed in it. Pure, and every date is a calendar day in the zone it is given.
 *
 * A lifter who started part way through is never shown a full-period figure: [since] says when they began, and nothing
 * is compared with a period they were not training for ([previous] is null then).
 */
data class PeriodReview(
    val period: ReviewPeriod,
    val stats: PeriodStats,
    /** The period before, only when the lifter was training for all of it (so a comparison is fair) and it had workouts. */
    val previous: PeriodStats?,
    /** The lifter's first workout, when it falls after the period began: the review covers only the time since. */
    val since: LocalDate?,
    /** The weeks the period owns that held a workout, oldest first ([PeriodStats.weeks]). */
    val weeks: List<WeekTotal>,
    /** Lifts that gained, the biggest gain first, then lifts first trained in the period. */
    val lifts: List<ReviewLift>,
    /** Hard sets by muscle, the most first; muscles with none are left out. */
    val muscles: List<Pair<Muscle, Double>>,
    /** A year's workouts in each of its twelve months; empty for a month. */
    val monthWorkouts: List<Int>,
    /** The streak as it stood at the period's end (today, in the period itself); 0 when none was alive. */
    val streakDays: Int,
    /** The chain is still alive today. */
    val streakGoing: Boolean,
    /** [streakDays] is the longest the lifter has had up to then. */
    val streakLongest: Boolean,
    /** The longest streak up to the period's end. */
    val longestStreakDays: Int,
    val skills: List<SkillUnlock>,
    /** Workouts a week over the days the lifter was training in the period. */
    val perWeek: Double,
) {
    /** The week the review singles out: the most kilograms moved, then the most workouts, then the earliest. */
    val bestWeek: WeekTotal? get() = PeriodStats.best(weeks)

    val gained: List<ReviewLift> get() = lifts.filter { !it.isNew }
    val newLifts: List<ReviewLift> get() = lifts.filter { it.isNew }

    /** Volume against the period before, in whole percent; null with nothing fair to compare with. */
    val volumeChangePercent: Int?
        get() = previous?.volumeKg?.takeIf { it > 0.0 }?.let { Math.round((stats.volumeKg - it) / it * 100).toInt() }

    companion object {
        private fun dayOf(session: WorkoutSession, zone: ZoneId): LocalDate =
            Instant.ofEpochMilli(session.completedAtMs ?: session.startedAtMs).atZone(zone).toLocalDate()

        private fun startMs(date: LocalDate, zone: ZoneId) = date.atStartOfDay(zone).toInstant().toEpochMilli()

        /**
         * The review of [period] over a completed [history] and the lifter's [practices] (claimed skills have a row).
         * [today] bounds the streak and the weekly pace for a period still running.
         */
        fun build(
            history: List<Pair<WorkoutSession, List<SessionSet>>>,
            exercises: Map<Long, Exercise>,
            bodyweightAt: (Long) -> Double,
            practices: List<SkillPractice>,
            period: ReviewPeriod,
            today: LocalDate,
            zone: ZoneId = ZoneId.systemDefault(),
        ): PeriodReview {
            val stats = PeriodStats.of(history, exercises, bodyweightAt, period.from, period.to, zone)
            val dates = history.map { (session, _) -> dayOf(session, zone) }.toSet()
            val first = dates.minOrNull()
            val since = first?.takeIf { it.isAfter(period.from) }

            val before = period.previous
            val previous = if (first != null && !first.isAfter(before.from)) {
                PeriodStats.of(history, exercises, bodyweightAt, before.from, before.to, zone).takeIf { it.workouts > 0 }
            } else {
                null
            }

            val fromMs = startMs(period.from, zone)
            val toMs = startMs(period.to, zone)
            val gains = LiftRecords.gains(history, exercises, bodyweightAt, fromMs, toMs).sortedByDescending { it.gainKg }.map {
                ReviewLift(it.name, it.variation, it.e1rmKg - it.gainKg, it.e1rmKg, it.series, it.set, it.addsToBody, it.atMs)
            }
            val fresh = LiftRecords.newLifts(history, exercises, bodyweightAt, fromMs, toMs).map {
                ReviewLift(it.name, it.variation, null, it.e1rmKg, it.series, it.set, it.addsToBody, it.atMs)
            }

            val weeks = PeriodStats.weeks(history, exercises, period.from, period.to, zone)
            val asOf = minOf(today, period.last)
            val streak = Streak.current(dates, asOf)
            val longest = Streak.longest(dates, asOf)

            val activeFrom = maxOf(period.from, since ?: period.from)
            val activeDays = (ChronoUnit.DAYS.between(activeFrom, asOf) + 1).coerceAtLeast(1)

            val unlocked = practices
                .filter { it.claimed && it.practicedAtMs in fromMs until toMs }
                .mapNotNull { practice ->
                    Skills.forName(practice.skillName)?.let { def ->
                        SkillUnlock(def.name, def.tier, practice.value.takeIf { it > 0 } ?: def.xp, practice.practicedAtMs)
                    }
                }
                .sortedBy { it.atMs }

            return PeriodReview(
                period = period,
                stats = stats,
                previous = previous,
                since = since,
                weeks = weeks,
                lifts = gains + fresh,
                muscles = stats.muscleSets.filterValues { it > 1e-9 }.entries.sortedByDescending { it.value }.map { it.key to it.value },
                monthWorkouts = if (period.kind == ReviewKind.YEAR) {
                    (0L until 12L).map { PeriodStats.workoutsIn(history, period.from.plusMonths(it), period.from.plusMonths(it + 1), zone) }
                } else {
                    emptyList()
                },
                streakDays = streak,
                streakGoing = streak > 0 && Streak.current(dates, today) > 0,
                streakLongest = streak > 0 && streak >= longest,
                longestStreakDays = longest,
                skills = unlocked,
                perWeek = stats.workouts / (activeDays / 7.0),
            )
        }

        /** Today's card and the Stats list read three figures: no muscles, streak or skills, so it stays cheap on a long history. */
        fun summary(
            history: List<Pair<WorkoutSession, List<SessionSet>>>,
            exercises: Map<Long, Exercise>,
            bodyweightAt: (Long) -> Double,
            period: ReviewPeriod,
            zone: ZoneId = ZoneId.systemDefault(),
        ): ReviewSummary {
            val inside = PeriodStats.within(history, period.from, period.to, zone)
            val fromMs = startMs(period.from, zone)
            val toMs = startMs(period.to, zone)
            return ReviewSummary(
                period = period,
                workouts = inside.size,
                volumeKg = inside.sumOf { (_, sets) -> WorkoutShare.totals(sets, exercises).movedKg.toDouble() },
                liftsGained = LiftRecords.gains(history, exercises, bodyweightAt, fromMs, toMs).size + LiftRecords.newLifts(history, exercises, bodyweightAt, fromMs, toMs).size,
            )
        }
    }
}
