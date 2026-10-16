package com.ironvellum.app.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import com.ironvellum.app.MainActivity
import com.ironvellum.app.R
import com.ironvellum.app.data.Notifications
import com.ironvellum.app.data.Words
import com.ironvellum.app.ui.components.plural
import com.ironvellum.app.ui.dashboard.DayKind
import com.ironvellum.app.ui.theme.IronvellumColors
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/**
 * The faces of the two widgets, as the approved mockups draw them. Colours that are the lifter's own
 * (the accent, the reward gold) are read from the theme at draw time and tinted in; the structural
 * paper, ink and rune are fixed, as in the app.
 */
internal object WidgetFaces {
    private const val INK = 0xFFE8E8E4.toInt()
    private const val MUTED = 0xFFA3A099.toInt()
    private const val RUNE = 0xFF32302B.toInt()

    private const val REQUEST_OPEN = 20
    private const val REQUEST_BEGIN = 21
    private const val REQUEST_EMPTY = 22
    private const val REQUEST_FORGE = 23

    private val accent get() = IronvellumColors.Emerald.toArgb()
    private val reward get() = IronvellumColors.SovereignGold.toArgb()

    // ---- words ----

    private fun dayShort(day: Int) = DayOfWeek.of(day).getDisplayName(TextStyle.SHORT, Locale.getDefault())

    private fun sets(n: Int) = "$n ${plural(n, "set", "sets")}"

    private fun next(state: WidgetState): String? = state.next?.let { "Next: ${it.name} · ${dayShort(it.day)}" }

    /** An empty workout has no sets to count until the first is added. */
    private fun liveSets(state: WidgetState) =
        if (state.setsTotal == 0) "No sets yet" else "${state.setsDone} of ${state.setsTotal} ${plural(state.setsTotal, "set", "sets")} done"

    private fun streakChip(state: WidgetState) = "${state.streak}-day ${Words.oath.s}"

    private fun beginLabel(state: WidgetState) =
        if (state.kind == DayKind.LIVE) "Resume" else "Begin ${state.title.orEmpty()}".trim()

    private fun noPlan() = "No ${Words.cycle.s} yet"

    private fun emptyLabel() = "Begin ${Words.openTrial.a}"

    private fun forgeLabel() = "${Words.forge.c} ${Words.cycle.a}"

    // ---- intents ----

    private fun launch(context: Context, request: Int, extra: (Intent) -> Unit): PendingIntent =
        PendingIntent.getActivity(
            context, request,
            Intent(context, MainActivity::class.java).also(extra)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** Tapping the widget anywhere: the app on Today. */
    private fun open(context: Context) =
        launch(context, REQUEST_OPEN) { it.putExtra(Notifications.EXTRA_OPEN_TAB, Notifications.TAB_HOME) }

    /** Begin today's rite, or resume the trial under way: resolved when tapped (MainActivity.beginFromWidget). */
    private fun begin(context: Context) =
        launch(context, REQUEST_BEGIN) { it.putExtra(Notifications.EXTRA_OPEN_TAB, Notifications.TAB_BEGIN) }

    private fun beginEmpty(context: Context) =
        launch(context, REQUEST_EMPTY) {
            it.putExtra(Notifications.EXTRA_OPEN_TAB, Notifications.TAB_BEGIN).putExtra(Notifications.EXTRA_EMPTY, true)
        }

    private fun forge(context: Context) =
        launch(context, REQUEST_FORGE) { it.putExtra(Notifications.EXTRA_OPEN_TAB, Notifications.TAB_FORGE) }

    /** What the face's main button does, or null for a face with none. */
    private fun primary(context: Context, state: WidgetState): PendingIntent? = when (state.kind) {
        DayKind.BEGIN, DayKind.LIVE -> begin(context)
        DayKind.RESPITE -> beginEmpty(context)
        DayKind.NO_CYCLE -> forge(context)
        else -> null
    }

    // ---- small helpers ----

    private fun RemoteViews.text(id: Int, value: String) = setTextViewText(id, value)

    private fun RemoteViews.show(id: Int, visible: Boolean, hidden: Int = View.GONE) =
        setViewVisibility(id, if (visible) View.VISIBLE else hidden)

    private fun RemoteViews.tint(id: Int, color: Int) = setInt(id, "setColorFilter", color)

    private fun RemoteViews.fillButton(label: String, sp: Float) {
        listOf(R.id.btn_fill_l, R.id.btn_fill_c, R.id.btn_fill_r).forEach { tint(it, accent) }
        text(R.id.btn_fill_text, label)
        setTextViewTextSize(R.id.btn_fill_text, TypedValue.COMPLEX_UNIT_SP, sp)
    }

    /** The 3dp bar of the live trial's sets: drawn as a tiny bitmap so it can wear the lifter's accent. */
    private fun bar(done: Int, total: Int): Bitmap {
        val width = 300
        val bitmap = Bitmap.createBitmap(width, 3, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply { color = RUNE }
        canvas.drawRect(0f, 0f, width.toFloat(), 3f, paint)
        if (total > 0) {
            paint.color = accent
            canvas.drawRect(0f, 0f, width * done.coerceIn(0, total) / total.toFloat(), 3f, paint)
        }
        return bitmap
    }

    private fun describe(vararg parts: String?) = parts.filterNotNull().filter { it.isNotBlank() }.joinToString(". ")

    // ---- widget A: the day card ----

    fun day(context: Context, state: WidgetState): RemoteViews {
        val v = RemoteViews(context.packageName, R.layout.widget_a)
        val live = state.kind == DayKind.LIVE
        val kicker = when (state.kind) {
            DayKind.LIVE -> "In progress · ${state.liveMinutes} min"
            DayKind.SEALED -> "Done today"
            DayKind.NO_CYCLE -> "Today"
            else -> "Today · ${dayShort(state.dayOfWeek)}"
        }
        val title = when (state.kind) {
            DayKind.RESPITE -> Words.respite.c
            DayKind.NO_CYCLE -> noPlan()
            else -> state.title.orEmpty()
        }
        val meta = when (state.kind) {
            DayKind.BEGIN -> listOfNotNull(
                "${state.exercises} ${plural(state.exercises, "exercise", "exercises")}",
                sets(state.sets),
                state.estimateMinutes?.let { "about $it min" },
            ).joinToString(" · ")
            DayKind.LIVE -> liveSets(state)
            DayKind.SEALED -> "${sets(state.sealedSets)} · ${state.sealedMinutes} min"
            DayKind.RESPITE -> next(state) ?: "A free day"
            else -> "Build one and today's ${Words.rite.s} shows here."
        }
        v.text(R.id.a_kicker, kicker)
        v.setTextColor(R.id.a_kicker, if (live) accent else MUTED)
        // No streak to show on a first day: the chip would only say "0-day streak".
        v.show(R.id.a_streak_chip, state.kind != DayKind.NO_CYCLE && state.streak > 0)
        v.text(R.id.a_streak, streakChip(state))
        v.tint(R.id.a_streak_icon, reward)
        v.text(R.id.a_title, title)
        v.setTextViewTextSize(R.id.a_title, TypedValue.COMPLEX_UNIT_SP, if (state.kind == DayKind.NO_CYCLE) 20f else 22f)
        v.show(R.id.a_tick, state.kind == DayKind.SEALED)
        v.tint(R.id.a_tick, accent)
        v.show(R.id.a_progress, live)
        if (live) v.setImageViewBitmap(R.id.a_progress, bar(state.setsDone, state.setsTotal))
        v.text(R.id.a_meta, meta)

        // The bottom: Begin or Resume filled, the quiet outline for the two faces with nothing to begin, or the footer.
        v.show(R.id.btn_fill, state.canStart)
        v.fillButton(beginLabel(state), 15f)
        val outline = state.kind == DayKind.RESPITE || state.kind == DayKind.NO_CYCLE
        v.show(R.id.btn_out, outline)
        v.text(R.id.btn_out_text, if (state.kind == DayKind.RESPITE) emptyLabel() else forgeLabel())
        v.setTextColor(R.id.btn_out_text, accent)
        v.show(R.id.a_footer, state.kind == DayKind.SEALED)
        v.text(R.id.a_next, next(state).orEmpty())

        v.setOnClickPendingIntent(android.R.id.background, open(context))
        primary(context, state)?.let { target ->
            v.setOnClickPendingIntent(if (outline) R.id.btn_out else R.id.btn_fill, target)
        }
        if (state.canStart) v.setContentDescription(R.id.btn_fill, beginLabel(state))
        v.setContentDescription(
            android.R.id.background,
            describe(kicker, title, meta, if (state.kind == DayKind.NO_CYCLE) null else streakChip(state)),
        )
        return v
    }

    fun dayCompact(context: Context, state: WidgetState): RemoteViews {
        val v = RemoteViews(context.packageName, R.layout.widget_a_compact)
        val title = when (state.kind) {
            DayKind.RESPITE -> Words.respite.c
            DayKind.NO_CYCLE -> "No ${Words.cycle.s}"
            else -> state.title.orEmpty()
        }
        // A streak of zero is left out, as the 4x2 chip leaves it out.
        val streaked = state.kind != DayKind.NO_CYCLE && state.streak > 0
        val after = when (state.kind) {
            DayKind.BEGIN -> sets(state.sets)
            DayKind.LIVE -> if (state.setsTotal == 0) "no sets yet" else "${state.setsDone}/${state.setsTotal} ${plural(state.setsTotal, "set", "sets")}"
            DayKind.SEALED -> "done"
            DayKind.RESPITE -> "rest"
            else -> "Tap to ${Words.forge.s} one"
        }
        val sub = if (streaked) "${state.streak} · $after" else after.replaceFirstChar { it.uppercase() }
        v.text(R.id.a_title, title)
        v.text(R.id.a_sub, sub)
        v.show(R.id.a_streak_icon, streaked)
        v.tint(R.id.a_streak_icon, reward)
        square(context, v, state)
        v.setOnClickPendingIntent(android.R.id.background, open(context))
        v.setContentDescription(
            android.R.id.background,
            describe(title, if (streaked) streakChip(state) else null, sub.takeIf { state.kind != DayKind.NO_CYCLE }),
        )
        return v
    }

    /** The 2x1 faces' 48dp square: a play button, a tick once the day is done, nothing on a free day. */
    private fun square(context: Context, v: RemoteViews, state: WidgetState) {
        val button = state.canStart
        val done = state.kind == DayKind.SEALED
        v.show(R.id.w_square, button || done)
        v.show(R.id.w_square_bg, button)
        v.show(R.id.w_square_icon, button)
        v.show(R.id.w_square_tick, done)
        v.tint(R.id.w_square_bg, accent)
        v.tint(R.id.w_square_tick, accent)
        if (button) {
            v.setOnClickPendingIntent(R.id.w_square, begin(context))
            v.setContentDescription(R.id.w_square, beginLabel(state))
        } else if (done) {
            v.setContentDescription(R.id.w_square, "Done today")
        }
    }

    // ---- widget B: streak and week ----

    private val DAY_CELLS = intArrayOf(
        R.id.b_cell_bg1, R.id.b_cell_bg2, R.id.b_cell_bg3, R.id.b_cell_bg4, R.id.b_cell_bg5, R.id.b_cell_bg6, R.id.b_cell_bg7,
    )
    private val DAY_LABELS = intArrayOf(R.id.b_day1, R.id.b_day2, R.id.b_day3, R.id.b_day4, R.id.b_day5, R.id.b_day6, R.id.b_day7)
    private val DAY_LABELS_BOLD = intArrayOf(R.id.b_dayb1, R.id.b_dayb2, R.id.b_dayb3, R.id.b_dayb4, R.id.b_dayb5, R.id.b_dayb6, R.id.b_dayb7)
    private val DAY_MARKS = intArrayOf(R.id.b_mark1, R.id.b_mark2, R.id.b_mark3, R.id.b_mark4, R.id.b_mark5, R.id.b_mark6, R.id.b_mark7)
    private val DOTS = intArrayOf(R.id.b_dot1, R.id.b_dot2, R.id.b_dot3, R.id.b_dot4, R.id.b_dot5, R.id.b_dot6, R.id.b_dot7)

    private fun weekTitle(state: WidgetState) = when (state.kind) {
        DayKind.RESPITE -> Words.respite.c
        DayKind.NO_CYCLE -> noPlan()
        else -> state.title.orEmpty()
    }

    private fun weekMeta(state: WidgetState) = when (state.kind) {
        DayKind.BEGIN -> "Today · ${sets(state.sets)}"
        DayKind.LIVE -> "In progress · " + if (state.setsTotal == 0) "no sets yet" else "${state.setsDone} of ${state.setsTotal} ${plural(state.setsTotal, "set", "sets")}"
        DayKind.SEALED -> "Done today · ${sets(state.sealedSets)}"
        DayKind.RESPITE -> next(state) ?: "A free day"
        else -> "Build one in the app"
    }

    fun week(context: Context, state: WidgetState): RemoteViews {
        val v = RemoteViews(context.packageName, R.layout.widget_b)
        val title = weekTitle(state)
        val meta = weekMeta(state)
        v.text(R.id.b_number, state.streak.toString())
        v.setTextColor(R.id.b_number, reward)
        v.text(R.id.b_label, "day ${Words.oath.s}")
        v.text(R.id.b_title, title)
        v.text(R.id.b_meta, meta)

        for (i in 0 until 7) {
            val mark = state.week[i]
            val today = i + 1 == state.dayOfWeek
            val letter = DayOfWeek.of(i + 1).getDisplayName(TextStyle.NARROW, Locale.getDefault())
            v.show(DAY_CELLS[i], today)
            v.show(DAY_LABELS[i], !today)
            v.show(DAY_LABELS_BOLD[i], today)
            v.text(DAY_LABELS[i], letter)
            v.text(DAY_LABELS_BOLD[i], letter)
            val color = if (mark == DayMark.FREE) MUTED else INK
            v.setTextColor(DAY_LABELS[i], color)
            v.setTextColor(DAY_LABELS_BOLD[i], color)
            // A day on the plan is an outline, a day trained is filled; a free day leaves its place empty.
            v.show(DAY_MARKS[i], mark != DayMark.FREE, hidden = View.INVISIBLE)
            if (mark == DayMark.DONE) {
                v.setImageViewResource(DAY_MARKS[i], R.drawable.widget_diamond)
                v.tint(DAY_MARKS[i], accent)
            } else {
                v.setImageViewResource(DAY_MARKS[i], R.drawable.widget_diamond_outline)
                v.tint(DAY_MARKS[i], INK)
            }
        }

        // The bottom: Begin or Resume, or one quiet line that says what is next (and, where it can, does it).
        v.show(R.id.btn_fill, state.canStart)
        v.fillButton(beginLabel(state), 14f)
        v.show(R.id.b_line, !state.canStart)
        val line = when (state.kind) {
            DayKind.SEALED -> next(state).orEmpty()
            DayKind.RESPITE -> "Tap to ${emptyLabel().replaceFirstChar { it.lowercase() }}"
            else -> "Tap to ${Words.forge.s} ${Words.cycle.a}"
        }
        v.text(R.id.b_line, line)

        v.setOnClickPendingIntent(android.R.id.background, open(context))
        primary(context, state)?.let { target ->
            v.setOnClickPendingIntent(if (state.canStart) R.id.btn_fill else R.id.b_line, target)
        }
        if (state.canStart) v.setContentDescription(R.id.btn_fill, beginLabel(state))
        v.setContentDescription(
            android.R.id.background,
            describe("${state.streak} day ${Words.oath.s}", title, meta, weekSummary(state)),
        )
        return v
    }

    private fun weekSummary(state: WidgetState): String {
        val done = state.week.count { it == DayMark.DONE }
        val planned = state.week.count { it == DayMark.PLANNED }
        return "This week: $done done, $planned still to do"
    }

    fun weekCompact(context: Context, state: WidgetState): RemoteViews {
        val v = RemoteViews(context.packageName, R.layout.widget_b_compact)
        v.text(R.id.b_number, state.streak.toString())
        v.setTextColor(R.id.b_number, reward)
        v.text(R.id.b_label, "day ${Words.oath.s}")
        for (i in 0 until 7) {
            when (state.week[i]) {
                DayMark.DONE -> {
                    v.setImageViewResource(DOTS[i], R.drawable.widget_diamond)
                    v.tint(DOTS[i], accent)
                }
                DayMark.PLANNED -> {
                    v.setImageViewResource(DOTS[i], R.drawable.widget_diamond_outline)
                    v.tint(DOTS[i], INK)
                }
                DayMark.FREE -> {
                    v.setImageViewResource(DOTS[i], R.drawable.widget_diamond_outline)
                    v.tint(DOTS[i], MUTED)
                }
            }
        }
        square(context, v, state)
        v.setOnClickPendingIntent(android.R.id.background, open(context))
        v.setContentDescription(
            android.R.id.background,
            describe("${state.streak} day ${Words.oath.s}", weekTitle(state), weekMeta(state), weekSummary(state)),
        )
        return v
    }
}
