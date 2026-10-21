package com.ironvellum.app.ui.recap

import com.ironvellum.app.R
import com.ironvellum.app.data.Units
import com.ironvellum.app.data.Words
import com.ironvellum.app.domain.LiftRecords
import com.ironvellum.app.domain.SessionSet
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.domain.WeeklyRecap
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * The words and figures of a recap, one place for the screen, the notification, the Today link, the Stats list and the
 * share card, so "4 h 52" and "Mon 12 – Sun 18 Oct 2026" read the same wherever they appear. Reads the lifter's load
 * unit and the Themed names switch as they are now.
 */
object RecapText {

    private fun pattern(id: Int, locale: Locale) = DateTimeFormatter.ofPattern(Words.text(UiText.res(id)), locale)

    /** "Mon 12 Oct" */
    fun day(date: LocalDate, locale: Locale = Locale.getDefault()): String = pattern(R.string.recap_pattern_day_month, locale).format(date)

    /** "Thursday": the weekday of an ISO day number (1 = Monday). */
    fun weekday(day: Int, locale: Locale = Locale.getDefault()): String = DayOfWeek.of(day).getDisplayName(TextStyle.FULL, locale)

    /** "Mon 12 – Sun 18 Oct 2026", the first day carrying its month (and year) only when the week crosses one. */
    fun range(start: LocalDate, end: LocalDate, locale: Locale = Locale.getDefault()): String {
        val first = when {
            start.year != end.year -> R.string.recap_pattern_day_month_year
            start.month != end.month -> R.string.recap_pattern_day_month
            else -> R.string.recap_pattern_day
        }
        return Words.text(UiText.res(R.string.recap_range, pattern(first, locale).format(start), pattern(R.string.recap_pattern_day_month_year, locale).format(end)))
    }

    /** "12 – 18 Oct", for a week in a list. */
    fun shortRange(start: LocalDate, end: LocalDate, locale: Locale = Locale.getDefault()): String {
        val first = if (start.month != end.month || start.year != end.year) R.string.recap_pattern_date else R.string.recap_pattern_number
        return Words.text(UiText.res(R.string.recap_range, pattern(first, locale).format(start), pattern(R.string.recap_pattern_date, locale).format(end)))
    }

    private fun compact(start: LocalDate, end: LocalDate, endPattern: Int, locale: Locale): String {
        val first = if (start.month != end.month || start.year != end.year) R.string.recap_pattern_date else R.string.recap_pattern_number
        return Words.text(UiText.res(R.string.recap_range_compact, pattern(first, locale).format(start), pattern(endPattern, locale).format(end)))
    }

    /** "5–11 Oct", the week beside a figure. */
    fun compactRange(start: LocalDate, end: LocalDate, locale: Locale = Locale.getDefault()): String =
        compact(start, end, R.string.recap_pattern_date, locale)

    /** "12–18 Oct 2026", the share card's date line (the card sets it in capitals). */
    fun cardRange(start: LocalDate, end: LocalDate, locale: Locale = Locale.getDefault()): String =
        compact(start, end, R.string.recap_pattern_date_year, locale)

    /** A whole number with its sign: "+11", "-3", "0". */
    fun signed(n: Int): String = if (n > 0) "+$n" else n.toString()

    /** "4 h 52", or "58 min" under an hour. */
    fun duration(minutes: Long): String =
        if (minutes >= 60) Words.text(UiText.res(R.string.recap_time_hours_minutes, minutes / 60, minutes % 60))
        else Words.text(UiText.res(R.string.recap_time_minutes, minutes))

    /** "4 h 52 min", the time with its unit spelled out, for running text. */
    fun durationLong(minutes: Long): String =
        if (minutes >= 60) Words.text(UiText.res(R.string.recap_time_hours_minutes_long, minutes / 60, minutes % 60))
        else Words.text(UiText.res(R.string.recap_time_minutes, minutes))

    /** The volume's figure in the lifter's unit with thousands separators, no unit: "18,420". */
    fun volume(kg: Double, locale: Locale = Locale.getDefault()): String = String.format(locale, "%,d", Units.amount(kg).toLong())

    /** The volume's unit: "kg" or "lb". */
    fun unit(): String = Units.current.label

    /** A load to one place with its unit: "99.0 kg". */
    fun load(kg: Double): String = Units.fixed(kg, 1)

    /** A gain with its sign: "+3.0 kg". */
    fun gain(kg: Double): String = "+" + load(kg)

    /** The load of a set: "82.5 kg", or "+12.5 kg" when it is added to the body. */
    private fun setLoad(set: SessionSet, addsToBody: Boolean): String {
        val figure = Units.withUnit(set.weightKg ?: 0.0)
        return if (addsToBody) "+$figure" else figure
    }

    /** A set as it was lifted: "82.5 kg × 6", or "+12.5 kg × 7" when the load is added to the body. */
    fun set(set: SessionSet, addsToBody: Boolean): String =
        Words.text(UiText.res(R.string.recap_set_figure, setLoad(set, addsToBody), set.reps))

    fun workouts(n: Int): UiText = UiText.themedPlural(R.plurals.recap_workouts_count, R.plurals.recap_workouts_count__t, n)

    fun records(n: Int): UiText = UiText.plural(R.plurals.recap_records_count, n)

    /** "4 workouts · 3 records · +15%", the one line the Today link and the Stats list carry; records and the change only when there are any. */
    fun summary(recap: WeeklyRecap, withVolumeWord: Boolean = false): UiText {
        val stats = recap.stats
        val parts = buildList {
            add(workouts(stats.workouts))
            if (stats.records.isNotEmpty()) add(records(stats.records.size))
            recap.volumeChangePercent?.let { percent ->
                val figure = UiText.res(R.string.recap_percent_delta, signed(percent))
                add(if (withVolumeWord) UiText.res(R.string.recap_list_volume, figure) else figure)
            }
        }
        return UiText.join(parts, " · ")
    }

    /** A record row's second line: the set that made it and its day, "82.5 kg × 6 · Mon 12 Oct". */
    fun recordSet(record: LiftRecords.RecordGain, locale: Locale = Locale.getDefault(), zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): String {
        val date = java.time.Instant.ofEpochMilli(record.atMs).atZone(zone).toLocalDate()
        return Words.text(UiText.res(R.string.recap_record_set, setLoad(record.set, record.addsToBody), record.set.reps, day(date, locale)))
    }
}
