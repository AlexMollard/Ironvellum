package com.ironvellum.app.domain

import java.time.LocalDate

/**
 * Streak = the unbroken rolling chain of workouts.
 *
 * One rule: never let a week pass without a workout. Walk back from the most
 * recent session; every gap of more than [MAX_GAP_DAYS] days closes the chain.
 * The value is the chain's span in calendar days up to today, so it grows
 * every morning the chain is alive and drops to zero only when a whole week
 * slipped by untrained. Rest days, rest weeks under the gap, schedules and
 * missed-scheduled-day guilt have no part in it — the last workout is the
 * only anchor.
 */
object Streak {

    const val MAX_GAP_DAYS = 7L

    fun current(dates: Set<LocalDate>, today: LocalDate): Int {
        // A backwards device clock leaves dates ahead of today; they are not
        // training yet, so they neither anchor nor inflate the chain.
        val sorted = dates.filter { !it.isAfter(today) }.sortedDescending()
        if (sorted.isEmpty()) return 0
        val last = sorted.first()
        if (today.toEpochDay() - last.toEpochDay() > MAX_GAP_DAYS) return 0
        var chainEnd = last
        for (date in sorted.drop(1)) {
            if (chainEnd.toEpochDay() - date.toEpochDay() > MAX_GAP_DAYS) break
            chainEnd = date
        }
        return (today.toEpochDay() - chainEnd.toEpochDay()).toInt() + 1
    }
}
