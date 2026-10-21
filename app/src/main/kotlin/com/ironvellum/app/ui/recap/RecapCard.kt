package com.ironvellum.app.ui.recap

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path as ComposePath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.toPath
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.PathParser
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withTranslation
import com.ironvellum.app.R
import com.ironvellum.app.data.Units
import com.ironvellum.app.data.Words
import com.ironvellum.app.domain.LoadUnit
import com.ironvellum.app.domain.Loads
import com.ironvellum.app.domain.RecapKind
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.domain.WeeklyRecap
import com.ironvellum.app.ui.theme.IronvellumColors
import java.io.ByteArrayOutputStream
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** Which optional parts the card carries. Body weight, measurements and photos have no switch: they are never on it. */
data class RecapCardOptions(val records: Boolean = true, val streak: Boolean = true)

/** The colours a card is drawn in: the app's own palette as it is now, dark or light, with the accent chosen. */
data class RecapCardPalette(
    val page: Int,
    val ink: Int,
    val muted: Int,
    val rune: Int,
    val accent: Int,
    val accentText: Int,
    val onAccent: Int,
    val gold: Int,
) {
    companion object {
        private fun Color.argb() = toArgb()

        /** The palette of the theme in force: the share card follows the lifter's theme and accent. */
        fun current() = RecapCardPalette(
            page = IronvellumColors.Abyss.argb(),
            ink = IronvellumColors.Ink.argb(),
            muted = IronvellumColors.InkMuted.argb(),
            rune = IronvellumColors.Rune.argb(),
            accent = IronvellumColors.Emerald.argb(),
            accentText = IronvellumColors.EmeraldBright.argb(),
            onAccent = IronvellumColors.OnEmerald.argb(),
            gold = IronvellumColors.SovereignGold.argb(),
        )
    }
}

/**
 * The weekly recap as a picture: 1080 x 1350, drawn straight onto a canvas (no window, no composition), so a worker, a test
 * and the share screen all produce the same pixels. Sets, volume, time, new records and the streak only: no body weight,
 * no measurements, no photos, no name. A month or year in review can draw its own totals on the same frame
 * ([header], [stat], [footer]); the numbers below are the design's, in pixels.
 */
object RecapCard {
    const val WIDTH = 1080
    const val HEIGHT = 1350

    private const val EDGE = 4f
    private const val MARGIN = 76f
    private const val RIGHT = WIDTH - MARGIN
    private const val ROW = 104f
    private const val FOOTER_TOP = 1164f
    private const val MAX_RECORDS = 3

    private const val ICON_DIAMOND = "M12 2l9 10-9 10L3 12z M12 7l4.5 5-4.5 5-4.5-5z"

    /** The card for [recap], as a bitmap. [palette] defaults to the app's theme as it is now. */
    fun render(
        context: Context,
        recap: WeeklyRecap,
        options: RecapCardOptions = RecapCardOptions(),
        palette: RecapCardPalette = RecapCardPalette.current(),
        locale: Locale = Locale.getDefault(),
    ): Bitmap {
        val bitmap = createBitmap(WIDTH, HEIGHT)
        val canvas = Canvas(bitmap)
        val fonts = Fonts(context)
        canvas.drawColor(palette.page)
        canvas.drawRect(EDGE / 2, EDGE / 2, WIDTH - EDGE / 2, HEIGHT - EDGE / 2, stroke(palette.rune, EDGE))

        header(canvas, context, fonts, palette, RecapText.cardRange(recap.weekStart, recap.weekEnd, locale))
        val stats = recap.stats
        canvas.drawRect(MARGIN, 180f, RIGHT, 182f, fill(palette.rune))

        // The headline: how many workouts, and against what.
        val count = stats.workouts.toString()
        val big = text(fonts.bold, 260f, palette.ink)
        canvas.drawText(count, MARGIN, 470f, big)
        val unit = text(fonts.semibold, 72f, palette.ink)
        canvas.drawText(
            UiText.themedPlural(R.plurals.recap_hero_unit, R.plurals.recap_hero_unit__t, stats.workouts).resolved(),
            MARGIN + big.measureText(count) + 24f, 470f, unit,
        )
        val before = recap.previous.workouts
        val versus = when {
            recap.kind == RecapKind.LIGHT || before == 0 -> context.getString(R.string.recap_card_this_week)
            stats.workouts > before -> context.getString(R.string.recap_card_up, before)
            stats.workouts < before -> context.getString(R.string.recap_card_down, before)
            else -> context.getString(R.string.recap_card_same)
        }
        canvas.drawText(versus, MARGIN, 528f, text(fonts.medium, 34f, palette.muted))

        // Sets, volume, time.
        val columnWidth = (RIGHT - MARGIN - 64f) / 3f
        stat(canvas, fonts, palette, MARGIN, context.getString(R.string.recap_stat_sets_label), stats.sets.toString(), null)
        val (volume, volumeUnit) = volumeFigure(stats.volumeKg, locale)
        stat(canvas, fonts, palette, MARGIN + columnWidth + 32f, context.getString(R.string.recap_stat_volume_label), volume, volumeUnit)
        val (time, timeUnit) = timeFigure(stats.minutes)
        stat(canvas, fonts, palette, MARGIN + 2 * (columnWidth + 32f), context.getString(R.string.recap_stat_time_label), time, timeUnit)

        // New records, as many as fit above the footer.
        val records = if (options.records) stats.records.take(MAX_RECORDS) else emptyList()
        if (records.isNotEmpty()) {
            canvas.drawText(
                context.getString(R.string.recap_card_records_title).uppercase(locale), MARGIN, 794f,
                text(fonts.medium, 26f, palette.muted, spacing = 4f),
            )
            val name = text(fonts.semibold, 40f, palette.ink)
            val set = text(fonts.medium, 34f, palette.muted, align = Paint.Align.RIGHT)
            val gain = text(fonts.bold, 40f, palette.accentText, align = Paint.Align.RIGHT)
            records.forEachIndexed { i, record ->
                val top = 817f + i * ROW
                canvas.drawRect(MARGIN, top, RIGHT, top + 2f, fill(palette.rune))
                val centre = top + 2f + (ROW - 2f) / 2f
                glyph(canvas, Icons.Outlined.EmojiEvents, MARGIN, centre - 24f, 48f, palette.gold)
                val setText = RecapText.set(record.set, record.addsToBody)
                val setRight = RIGHT - 150f - 24f
                val nameLeft = MARGIN + 48f + 24f
                val room = setRight - set.measureText(setText) - 24f - nameLeft
                canvas.drawText(TextUtils.ellipsize(record.label, TextPaint(name), room, TextUtils.TruncateAt.END).toString(), nameLeft, centre + 14f, name)
                canvas.drawText(setText, setRight, centre + 12f, set)
                canvas.drawText("+" + Loads.fixed(record.gainKg, Units.current, 1), RIGHT, centre + 14f, gain)
            }
        }

        if (options.streak && recap.streak.days > 0) footer(canvas, context, fonts, palette, recap, locale)
        return bitmap
    }

    /** The PNG bytes of [bitmap]. */
    fun png(bitmap: Bitmap): ByteArray = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()

    private fun UiText.resolved(): String = Words.text(this)

    private fun header(canvas: Canvas, context: Context, fonts: Fonts, palette: RecapCardPalette, range: String) {
        icon(canvas, ICON_DIAMOND, MARGIN, 76f, 64f, palette.accent, 1.6f)
        canvas.drawText(context.getString(R.string.app_name).uppercase(Locale.getDefault()), MARGIN + 64f + 20f, 120f, text(fonts.semibold, 32f, palette.ink, spacing = 8f))
        canvas.drawText(range.uppercase(Locale.getDefault()), RIGHT, 118f, text(fonts.medium, 30f, palette.muted, spacing = 3f, align = Paint.Align.RIGHT))
    }

    private fun stat(canvas: Canvas, fonts: Fonts, palette: RecapCardPalette, x: Float, label: String, value: String, unit: String?) {
        canvas.drawText(label.uppercase(Locale.getDefault()), x, 600f, text(fonts.medium, 26f, palette.muted, spacing = 4f))
        val figure = text(fonts.bold, 84f, palette.ink)
        canvas.drawText(value, x, 686f, figure)
        if (unit != null) canvas.drawText(unit, x + figure.measureText(value) + 8f, 686f, text(fonts.medium, 34f, palette.muted))
    }

    private fun footer(canvas: Canvas, context: Context, fonts: Fonts, palette: RecapCardPalette, recap: WeeklyRecap, locale: Locale) {
        canvas.drawRect(MARGIN, FOOTER_TOP, RIGHT, FOOTER_TOP + 2f, fill(palette.rune))
        val centre = FOOTER_TOP + 2f + 36f + 36f
        glyph(canvas, Icons.Outlined.LocalFireDepartment, MARGIN, centre - 28f, 56f, palette.accent)
        canvas.drawText(
            UiText.plural(R.plurals.recap_streak_count, recap.streak.days).resolved(),
            MARGIN + 56f + 20f, centre + 16f, text(fonts.bold, 44f, palette.ink),
        )
        val letter = text(fonts.bold, 30f, palette.muted, align = Paint.Align.CENTER)
        val left = RIGHT - (7 * 72f + 6 * 10f)
        for (i in 0 until 7) {
            val date = recap.weekStart.plusDays(i.toLong())
            val worked = date in recap.stats.days
            val x = left + i * 82f
            val cell = plate(x, centre - 36f, 72f, 12f)
            if (worked) canvas.drawPath(cell, fill(palette.accent)) else canvas.drawPath(plate(x + 1f, centre - 35f, 70f, 12f), stroke(palette.rune, 2f))
            letter.color = if (worked) palette.onAccent else palette.muted
            canvas.drawText(DayOfWeek.of(date.dayOfWeek.value).getDisplayName(TextStyle.NARROW, locale), x + 36f, centre + 11f, letter)
        }
    }

    /** A square [size] px with its corners cut [cut] px, at [x], [y]. */
    private fun plate(x: Float, y: Float, size: Float, cut: Float): Path = Path().apply {
        moveTo(x + cut, y)
        lineTo(x + size - cut, y)
        lineTo(x + size, y + cut)
        lineTo(x + size, y + size - cut)
        lineTo(x + size - cut, y + size)
        lineTo(x + cut, y + size)
        lineTo(x, y + size - cut)
        lineTo(x, y + cut)
        close()
    }

    /** A material icon (a 24 grid of filled shapes) drawn [size] px square at [x], [y]: the icons the app already uses, not new art. */
    private fun glyph(canvas: Canvas, icon: ImageVector, x: Float, y: Float, size: Float, color: Int) {
        val shape = ComposePath()
        fun add(group: VectorGroup) {
            group.forEach { node ->
                when (node) {
                    is VectorPath -> shape.addPath(node.pathData.toPath())
                    is VectorGroup -> add(node)
                }
            }
        }
        add(icon.root)
        val scale = size / 24f
        canvas.withTranslation(x, y) {
            scale(scale, scale)
            drawPath(shape.asAndroidPath(), fill(color))
        }
    }

    /** An SVG path on a 24 grid, stroked [width] (in grid units) at [size] px. */
    private fun icon(canvas: Canvas, data: String, x: Float, y: Float, size: Float, color: Int, width: Float) {
        val scale = size / 24f
        val path = PathParser.createPathFromPathData(data)
        canvas.withTranslation(x, y) {
            scale(scale, scale)
            drawPath(
                path,
                stroke(color, width).apply {
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                },
            )
        }
    }

    /** Volume as the card shows it: tonnes (or thousands of pounds) from 10,000 up, else the plain figure. */
    internal fun volumeFigure(kg: Double, locale: Locale): Pair<String, String> {
        val unit = Units.current
        val amount = Units.amount(kg)
        return when {
            amount >= 10_000 && unit == LoadUnit.KG -> String.format(locale, "%.1f", amount / 1000) to UiText.res(R.string.recap_card_unit_tonnes).resolved()
            amount >= 10_000 -> String.format(locale, "%.1f", amount / 1000) to UiText.res(R.string.recap_card_unit_klb).resolved()
            else -> String.format(locale, "%,d", amount.toLong()) to unit.label
        }
    }

    /** Time as the card shows it: "4:52" hours, or minutes alone under an hour. */
    internal fun timeFigure(minutes: Long): Pair<String, String> =
        if (minutes >= 60) String.format(Locale.ROOT, "%d:%02d", minutes / 60, minutes % 60) to UiText.res(R.string.recap_card_unit_hours).resolved()
        else minutes.toString() to UiText.res(R.string.recap_card_unit_minutes).resolved()

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }

    private fun stroke(color: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = width
    }

    private fun text(face: Typeface, size: Float, color: Int, spacing: Float = 0f, align: Paint.Align = Paint.Align.LEFT) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = face
            textSize = size
            this.color = color
            textAlign = align
            letterSpacing = spacing / size
        }

    /** Chakra Petch, the app's own face, in the three weights it ships. */
    private class Fonts(context: Context) {
        val medium: Typeface = ResourcesCompat.getFont(context, R.font.chakra_petch_medium) ?: Typeface.DEFAULT
        val semibold: Typeface = ResourcesCompat.getFont(context, R.font.chakra_petch_semibold) ?: Typeface.DEFAULT_BOLD
        val bold: Typeface = ResourcesCompat.getFont(context, R.font.chakra_petch_bold) ?: Typeface.DEFAULT_BOLD
    }
}
