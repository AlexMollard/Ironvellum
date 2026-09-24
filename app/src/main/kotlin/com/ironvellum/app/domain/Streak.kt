package com.ironvellum.app.domain

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Streak = consecutive scheduled training days completed, with a catch-up
 * window so rest days and one bad day cannot erase a month.
 *
 *  - Rest days (days without a scheduled preset) never grow or break it.
 *  - Today never breaks it (today is forgiving).
 *  - A missed scheduled day inside the CURRENT week is still open: you can
 *    still train it late, so it does not break the streak yet.
 *  - A missed scheduled day in a PAST week is forgiven when a later day in
 *    that same week was completed — the week got trained around the slip.
 *    The week closing with the slip untrained is what breaks the streak,
 *    so a washed week costs the streak, one bad Tuesday does not.
 */
object Streak {

    data class DayRecord(val date: LocalDate, val scheduledDay: Int?, val completed: Boolean)

    fun current(
        records: List<DayRecord>,
        today: LocalDate,
    ): Int {
        val byDate = records.associateBy { it.date }
        val thisWeekStart = today.with(DayOfWeek.MONDAY)
        var streak = 0
        var date = today
        // Completions already walked past inside the week [date] falls in.
        // The walk runs backward, so these are all LATER than [date]: a slip
        // with one of these behind it was trained around, not skipped.
        var weekCompletions = 0
        var weekStart = today.with(DayOfWeek.MONDAY)
        // Walk back at most ~2 years; enough for any realistic streak.
        repeat(730) {
            val start = date.with(DayOfWeek.MONDAY)
            if (start != weekStart) {
                weekStart = start
                weekCompletions = 0
            }
            val record = byDate[date]
            if (record != null) {
                if (record.completed) {
                    streak++
                    weekCompletions++
                } else if (record.scheduledDay != null) {
                    val isToday = date == today
                    val stillOpen = date >= thisWeekStart
                    if (!isToday && !stillOpen && weekCompletions == 0) return streak
                    // rescued (trained around), today, or still open: keep walking.
                }
            }
            date = date.minusDays(1)
        }
        return streak
    }
}
