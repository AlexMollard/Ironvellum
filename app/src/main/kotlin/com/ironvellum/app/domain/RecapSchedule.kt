package com.ironvellum.app.domain

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

/**
 * When the weekly recap is delivered and whether a given run should send it. Pure, like [Summons]: the worker only
 * asks and does what it is told.
 *
 * The rule: Sunday at 19:00 local time, for the week that Sunday ends. A week with no workouts sends nothing, and a week
 * is never delivered twice (the caller keeps the Monday of the last week it delivered).
 */
object RecapSchedule {

    val DELIVERY_DAY: DayOfWeek = DayOfWeek.SUNDAY
    val DELIVERY_TIME: LocalTime = LocalTime.of(19, 0)

    /**
     * How long after 19:00 a held run may still deliver (a phone off all evening, a doze window): past it the moment
     * has gone and the recap waits on Today and Stats instead.
     */
    val GRACE: Duration = Duration.ofHours(24)

    /** What a run does about the week it finds due. */
    sealed interface Verdict {
        /** Nothing is due: the run was held past [GRACE]. */
        data object NotDue : Verdict
        data class AlreadyDelivered(val week: LocalDate) : Verdict
        data class EmptyWeek(val week: LocalDate) : Verdict
        data class Deliver(val week: LocalDate) : Verdict
    }

    private fun at(sunday: LocalDate, now: ZonedDateTime): ZonedDateTime = ZonedDateTime.of(sunday, DELIVERY_TIME, now.zone)

    /** The next Sunday 19:00 strictly after [now], on the local wall clock (a clock change cannot move it). */
    fun nextDelivery(now: ZonedDateTime): ZonedDateTime {
        val sunday = now.toLocalDate().with(TemporalAdjusters.nextOrSame(DELIVERY_DAY))
        val candidate = at(sunday, now)
        return if (candidate.isAfter(now)) candidate else at(sunday.plusWeeks(1), now)
    }

    /** How long to wait from [now] for [nextDelivery]. */
    fun delayUntilNext(now: ZonedDateTime): Duration = Duration.between(now, nextDelivery(now))

    /** The Monday of the week that [sunday] ends. */
    private fun mondayOf(sunday: LocalDate): LocalDate = sunday.minusDays(6)

    /**
     * The week a run at [now] is for: the one ended by the latest Sunday 19:00 at or before [now], while that moment is
     * within [GRACE]. A run held into Monday morning is still the Sunday's recap, never the new week's.
     */
    fun dueWeek(now: ZonedDateTime): LocalDate? {
        val sunday = now.toLocalDate().with(TemporalAdjusters.previousOrSame(DELIVERY_DAY))
        val moment = at(sunday, now).let { if (it.isAfter(now)) at(sunday.minusWeeks(1), now) else it }
        if (Duration.between(moment, now) > GRACE) return null
        return mondayOf(moment.toLocalDate())
    }

    /**
     * The verdict for a run at [now]. [lastDelivered] is the Monday of the last week delivered (null: never);
     * [workoutsIn] counts the workouts of the week starting on the Monday it is given.
     */
    fun decide(now: ZonedDateTime, lastDelivered: LocalDate?, workoutsIn: (LocalDate) -> Int): Verdict {
        val week = dueWeek(now) ?: return Verdict.NotDue
        if (lastDelivered == week) return Verdict.AlreadyDelivered(week)
        if (workoutsIn(week) == 0) return Verdict.EmptyWeek(week)
        return Verdict.Deliver(week)
    }

    /** Last week's recap is offered on Today from Monday through Wednesday, and only when last week had workouts. */
    fun showsOnToday(today: LocalDate, lastWeekWorkouts: Int): Boolean =
        lastWeekWorkouts > 0 && today.dayOfWeek in DayOfWeek.MONDAY..DayOfWeek.WEDNESDAY
}
