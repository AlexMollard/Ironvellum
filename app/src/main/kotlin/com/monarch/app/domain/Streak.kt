package com.monarch.app.domain

import java.time.LocalDate

/**
 * Streak = consecutive scheduled training days completed. Rest days (days
 * without a scheduled preset) neither grow nor break the streak; a scheduled
 * day that was skipped breaks it, counting back from today (today itself only
 * breaks the streak if the day is over or it is scheduled and a later day was
 * already missed — i.e. today is forgiving).
 */
object Streak {

    data class DayRecord(val date: LocalDate, val scheduledDay: Int?, val completed: Boolean)

    fun current(
        records: List<DayRecord>,
        today: LocalDate,
    ): Int {
        val byDate = records.associateBy { it.date }
        var streak = 0
        var date = today
        // Walk back at most ~2 years; enough for any realistic streak.
        repeat(730) {
            val record = byDate[date]
            if (record != null) {
                if (record.completed) {
                    streak++
                } else {
                    val isToday = date == today
                    val scheduled = record.scheduledDay != null
                    if (scheduled && !isToday) return streak
                    // unscheduled but completed (freeform) still counts;
                    // today without a completion just waits.
                }
            }
            date = date.minusDays(1)
        }
        return streak
    }
}
