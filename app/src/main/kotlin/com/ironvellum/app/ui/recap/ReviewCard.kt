package com.ironvellum.app.ui.recap

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.text.TextPaint
import android.text.TextUtils
import com.ironvellum.app.R
import com.ironvellum.app.data.Units
import com.ironvellum.app.domain.Loads
import com.ironvellum.app.domain.PeriodReview
import com.ironvellum.app.domain.ReviewKind
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.ui.recap.RecapCard.EDGE
import com.ironvellum.app.ui.recap.RecapCard.FOOTER_TOP
import com.ironvellum.app.ui.recap.RecapCard.HEIGHT
import com.ironvellum.app.ui.recap.RecapCard.MARGIN
import com.ironvellum.app.ui.recap.RecapCard.MAX_RECORDS
import com.ironvellum.app.ui.recap.RecapCard.RIGHT
import com.ironvellum.app.ui.recap.RecapCard.ROW
import com.ironvellum.app.ui.recap.RecapCard.WIDTH
import com.ironvellum.app.ui.recap.RecapCard.fill
import com.ironvellum.app.ui.recap.RecapCard.resolved
import com.ironvellum.app.ui.recap.RecapCard.stroke
import com.ironvellum.app.ui.recap.RecapCard.text
import androidx.core.graphics.createBitmap
import java.util.Locale

/**
 * A month or year in review as a picture, on the weekly recap's frame: 1080 x 1350, drawn straight onto a canvas, in the
 * app's theme and its Themed names setting (the palette and the words are handed in or read as they are now). Workouts,
 * volume, the lifts that gained, and one line each for the most trained muscle (the best week, for a year) and the streak.
 * No body weight, measurements, photos or name. [RecapCardOptions.records] is the lifts, [RecapCardOptions.streak] the streak.
 */
object ReviewCard {
    private const val FIGURE_BASELINE = 700f
    private const val FOOTER_VALUE = FOOTER_TOP + 2f + 116f

    fun render(
        context: Context,
        review: PeriodReview,
        options: RecapCardOptions = RecapCardOptions(),
        palette: RecapCardPalette = RecapCardPalette.current(),
        locale: Locale = Locale.getDefault(),
    ): Bitmap {
        val bitmap = createBitmap(WIDTH, HEIGHT)
        val canvas = Canvas(bitmap)
        val fonts = RecapCard.Fonts(context)
        canvas.drawColor(palette.page)
        canvas.drawRect(EDGE / 2, EDGE / 2, WIDTH - EDGE / 2, HEIGHT - EDGE / 2, stroke(palette.rune, EDGE))

        val year = review.period.kind == ReviewKind.YEAR
        val range = if (year) ReviewText.named(review.period, locale) else ReviewText.name(review.period, locale)
        RecapCard.header(canvas, context, fonts, palette, range)
        canvas.drawRect(MARGIN, 180f, RIGHT, 182f, fill(palette.rune))

        // The headline: how many workouts, and against what (or since when, for a lifter who started part way through).
        val stats = review.stats
        val count = stats.workouts.toString()
        val big = text(fonts.bold, 260f, palette.ink)
        canvas.drawText(count, MARGIN, 470f, big)
        canvas.drawText(
            UiText.themedPlural(R.plurals.recap_hero_unit, R.plurals.recap_hero_unit__t, stats.workouts).resolved(),
            MARGIN + big.measureText(count) + 24f, 470f, text(fonts.semibold, 72f, palette.ink),
        )
        val versus = review.volumeChangePercent?.let {
            val percent = UiText.res(R.string.recap_percent_delta, RecapText.signed(it)).resolved()
            UiText.res(R.string.review_card_volume_vs, percent, ReviewText.short(review.period.previous, locale)).resolved()
        } ?: ReviewText.sinceCaption(review, locale) ?: context.getString(if (year) R.string.review_card_this_year else R.string.review_card_this_month)
        canvas.drawText(versus, MARGIN, 528f, text(fonts.medium, 34f, palette.muted))

        // The volume, as the weekly card's figures print it: tonnes from 10,000 kg up.
        val (volume, unit) = RecapCard.volumeFigure(stats.volumeKg, locale)
        val figure = text(fonts.bold, 120f, palette.ink)
        canvas.drawText(volume, MARGIN, FIGURE_BASELINE, figure)
        canvas.drawText(
            UiText.res(R.string.review_card_lifted, unit).resolved(),
            MARGIN + figure.measureText(volume) + 20f, FIGURE_BASELINE, text(fonts.medium, 40f, palette.muted),
        )

        lifts(canvas, context, fonts, palette, review, options, locale)
        footer(canvas, context, fonts, palette, review, options, locale)
        return bitmap
    }

    private fun lifts(canvas: Canvas, context: Context, fonts: RecapCard.Fonts, palette: RecapCardPalette, review: PeriodReview, options: RecapCardOptions, locale: Locale) {
        // The gains first, then lifts that began, as many as fit above the footer.
        val rows = if (options.records) review.lifts.take(MAX_RECORDS) else emptyList()
        if (rows.isEmpty()) return
        canvas.drawText(
            context.getString(R.string.review_card_lifts_title).uppercase(locale), MARGIN, 794f,
            text(fonts.medium, 26f, palette.muted, spacing = 4f),
        )
        val name = text(fonts.semibold, 40f, palette.ink)
        val change = text(fonts.medium, 32f, palette.muted, align = Paint.Align.RIGHT)
        val gain = text(fonts.bold, 40f, palette.accentText, align = Paint.Align.RIGHT)
        rows.forEachIndexed { i, lift ->
            val top = 817f + i * ROW
            canvas.drawRect(MARGIN, top, RIGHT, top + 2f, fill(palette.rune))
            val centre = top + 2f + (ROW - 2f) / 2f
            val gainText = lift.gainKg?.let { "+" + Loads.fixed(it, Units.current, 1) } ?: context.getString(R.string.review_card_new)
            val before = lift.beforeKg
            val changeText = if (before != null) {
                UiText.res(R.string.review_lift_change, ReviewText.figure(before), RecapText.load(lift.afterKg)).resolved()
            } else {
                UiText.res(R.string.review_lift_new_sub, RecapText.load(lift.afterKg)).resolved()
            }
            val changeRight = RIGHT - 150f - 24f
            val room = changeRight - change.measureText(changeText) - 24f - MARGIN
            canvas.drawText(TextUtils.ellipsize(lift.label, TextPaint(name), room, TextUtils.TruncateAt.END).toString(), MARGIN, centre + 14f, name)
            canvas.drawText(changeText, changeRight, centre + 12f, change)
            gain.color = if (lift.isNew) palette.gold else palette.accentText
            canvas.drawText(gainText, RIGHT, centre + 14f, gain)
        }
    }

    /** Two figures under a rule: the most trained muscle (a year: its best week) and the streak (a year: its longest). */
    private fun footer(canvas: Canvas, context: Context, fonts: RecapCard.Fonts, palette: RecapCardPalette, review: PeriodReview, options: RecapCardOptions, locale: Locale) {
        val year = review.period.kind == ReviewKind.YEAR
        val first: Pair<String, String>? = if (year) {
            review.bestWeek?.let { week ->
                val days = RecapText.compactRange(week.weekStart, week.weekEnd, locale)
                context.getString(R.string.review_card_best_week) to "$days · ${ReviewText.volumeShort(week.volumeKg, locale)}"
            }
        } else {
            review.muscles.firstOrNull()?.let { (muscle, sets) ->
                context.getString(R.string.review_card_most_trained) to UiText.res(R.string.review_card_muscle_sets, muscle.label, setsText(sets)).resolved()
            }
        }
        val second: Pair<String, String>? = if (options.streak && review.streakDays > 0) {
            val days = if (year) review.longestStreakDays else review.streakDays
            val label = UiText.themed(
                if (year) R.string.review_card_longest_streak else R.string.review_card_streak,
                if (year) R.string.review_card_longest_streak__t else R.string.review_card_streak__t,
            ).resolved()
            label to UiText.plural(R.plurals.recap_streak_count, days).resolved()
        } else {
            null
        }
        if (first == null && second == null) return
        canvas.drawRect(MARGIN, FOOTER_TOP, RIGHT, FOOTER_TOP + 2f, fill(palette.rune))
        val label = text(fonts.medium, 24f, palette.muted, spacing = 4f)
        val value = text(fonts.bold, 44f, palette.ink)
        val column = (RIGHT - MARGIN) / 2f
        listOfNotNull(first, second).forEachIndexed { i, (title, figure) ->
            val x = MARGIN + i * column
            canvas.drawText(title.uppercase(locale), x, FOOTER_TOP + 2f + 62f, label)
            val room = column - 24f
            canvas.drawText(TextUtils.ellipsize(figure, TextPaint(value), room, TextUtils.TruncateAt.END).toString(), x, FOOTER_VALUE, value)
        }
    }
}
