package com.ironvellum.app.ui.recap

import com.ironvellum.app.R
import com.ironvellum.app.data.Units
import com.ironvellum.app.data.Words
import com.ironvellum.app.domain.Loads
import com.ironvellum.app.domain.PeriodReview
import com.ironvellum.app.domain.ReviewKind
import com.ironvellum.app.domain.ReviewPeriod
import com.ironvellum.app.domain.ReviewSummary
import com.ironvellum.app.domain.UiText
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The words and figures of a month or year in review, in one place for the screen, the Today card, the Stats list and the share card. */
object ReviewText {

    private fun pattern(id: Int, locale: Locale) = DateTimeFormatter.ofPattern(Words.text(UiText.res(id)), locale)

    /** "September", the month's own name. */
    fun monthName(date: LocalDate, locale: Locale = Locale.getDefault()): String = pattern(R.string.review_pattern_month, locale).format(date)

    /** "September 2026", or "2026" for a year. */
    fun name(period: ReviewPeriod, locale: Locale = Locale.getDefault()): String = pattern(
        if (period.kind == ReviewKind.MONTH) R.string.review_pattern_month_year else R.string.review_pattern_year,
        locale,
    ).format(period.from)

    /** "September" or "2026": the short name a button or a comparison uses. */
    fun short(period: ReviewPeriod, locale: Locale = Locale.getDefault()): String = pattern(
        if (period.kind == ReviewKind.MONTH) R.string.review_pattern_month else R.string.review_pattern_year,
        locale,
    ).format(period.from)

    /** "September 2026 in review". */
    fun named(period: ReviewPeriod, locale: Locale = Locale.getDefault()): String = Words.text(UiText.res(R.string.review_named, name(period, locale)))

    /** The screen's title: "Month in review" or "Year in review". */
    fun titleRes(period: ReviewPeriod): Int = if (period.kind == ReviewKind.MONTH) R.string.review_month_title else R.string.review_year_title

    /** What [since] says it covers: a month name for a year ("March"), a day for a month ("14 Sep"). */
    private fun sinceArg(period: ReviewPeriod, since: LocalDate, locale: Locale): String =
        if (period.kind == ReviewKind.YEAR) monthName(since, locale) else pattern(R.string.recap_pattern_date, locale).format(since)

    /** "Since you started in March": a lifter who began part way through, as a sentence; null for a full period. */
    fun sinceCaption(review: PeriodReview, locale: Locale = Locale.getDefault()): String? {
        val since = review.since ?: return null
        val id = if (review.period.kind == ReviewKind.YEAR) R.string.review_since_month else R.string.review_since_day
        return Words.text(UiText.res(id, sinceArg(review.period, since, locale)))
    }

    /** The line under the screen's title: the period, and for a part period how far back it counts. */
    fun subtitle(review: PeriodReview, locale: Locale = Locale.getDefault()): String {
        val since = review.since ?: return name(review.period, locale)
        val id = if (review.period.kind == ReviewKind.YEAR) R.string.review_subtitle_since_month else R.string.review_subtitle_since_day
        return Words.text(UiText.res(id, name(review.period, locale), sinceArg(review.period, since, locale)))
    }

    /** "+18% vs August", or null with nothing fair to compare with. */
    fun comparison(review: PeriodReview, locale: Locale = Locale.getDefault()): String? {
        val percent = review.volumeChangePercent ?: return null
        val figure = Words.text(UiText.res(R.string.recap_percent_delta, RecapText.signed(percent)))
        return Words.text(UiText.res(R.string.review_vs_period, figure, short(review.period.previous, locale)))
    }

    /** Kilograms as a share card and a summary say them: "74.9 t" from 10,000 kg up, else "5,310 kg". */
    fun volumeShort(kg: Double, locale: Locale = Locale.getDefault()): String {
        val (figure, unit) = RecapCard.volumeFigure(kg, locale)
        return "$figure $unit"
    }

    /** "16 workouts · 74.9 t": what Today's card says under its label. */
    fun summary(summary: ReviewSummary): UiText =
        UiText.join(listOf(RecapText.workouts(summary.workouts), UiText.Raw(volumeShort(summary.volumeKg))), " · ")

    /** "16 workouts · 74.9 t · 5 lifts gained": a row in the Stats list. */
    fun listLine(summary: ReviewSummary): UiText = UiText.join(
        buildList {
            add(RecapText.workouts(summary.workouts))
            add(UiText.Raw(volumeShort(summary.volumeKg)))
            if (summary.liftsGained > 0) add(UiText.plural(R.plurals.review_lifts_gained_count, summary.liftsGained))
        },
        " · ",
    )

    /** A best week as the row and the card say it: "14–20 Sep · 4 workouts · 17,980 kg". */
    fun bestWeek(week: com.ironvellum.app.domain.WeekTotal, locale: Locale = Locale.getDefault()): UiText = UiText.res(
        R.string.review_best_week_line,
        RecapText.compactRange(week.weekStart, week.weekEnd, locale),
        RecapText.workouts(week.workouts),
        UiText.Raw(RecapText.volume(week.volumeKg, locale) + " " + RecapText.unit()),
    )

    /** The weekly pace with one decimal: "3.7". */
    fun pace(review: PeriodReview, locale: Locale = Locale.getDefault()): String = String.format(locale, "%.1f", review.perWeek)

    /** A lift's load as a plain figure in the lifter's unit, one decimal, no unit: "112.5". */
    fun figure(kg: Double): String = Loads.fixed(kg, Units.current, 1)
}
