package com.ironvellum.app.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.core.graphics.createBitmap
import com.ironvellum.app.MainActivity
import com.ironvellum.app.R
import com.ironvellum.app.data.Notifications
import com.ironvellum.app.data.Units
import com.ironvellum.app.data.Words
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.ui.asString
import com.ironvellum.app.ui.dashboard.DayKind
import com.ironvellum.app.ui.theme.lightAccent
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** The four cell sizes the widgets are drawn for; a launcher picks the largest whose minimum fits the cell. */
internal enum class WidgetSize(val minWidth: Float, val minHeight: Float) {
    /** 2x1, about 170x84 dp. */
    S(110f, 40f),

    /** 4x1, about 360x84 dp. */
    W(250f, 60f),

    /** 4x2, about 360x200 dp. */
    M(250f, 140f),

    /** 4x3 and taller, about 360x300 dp. */
    L(250f, 250f);

    companion object {
        fun of(widthDp: Float, heightDp: Float): WidgetSize =
            entries.lastOrNull { widthDp >= it.minWidth && heightDp >= it.minHeight } ?: S
    }
}

/**
 * The colours one widget draw wears: the dark card, or Vellum when the app's theme choice (System resolved
 * against the phone) is light. The accent is the lifter's own pair for that theme.
 */
private class Skin(
    val light: Boolean,
    val ink: Int, val muted: Int, val rune: Int, val panel: Int,
    val accent: Int, val accentText: Int, val reward: Int, val onFill: Int,
    val tint: Int, val tintBorder: Int,
)

/** What the right-hand slot of a header holds. */
private sealed interface Action {
    data object None : Action
    class Play(val description: String, val target: PendingIntent) : Action
    data object Tick : Action
    class Ghost(val text: String, val target: PendingIntent) : Action
    class Pill(val text: String, val icon: Boolean, val target: PendingIntent) : Action
}

/**
 * The faces of the two widgets, as the approved v4 mockups draw them (docs/TODO.md, item 5). Colours that are the
 * lifter's own (the accent, the reward gold) are read from the theme at draw time and tinted in; the structural
 * paper, ink and rune follow the app's theme choice (Dark, or Vellum for Light). The lists (exercise lines, set
 * ladder, week columns) are small layouts added to a container, so a routine of any length draws.
 */
internal object WidgetFaces {
    // Set at the top of every face, on the thread that draws them all (update() holds a lock).
    private var skin: Skin = darkSkin()
    private val INK get() = skin.ink
    private val MUTED get() = skin.muted
    private val RUNE get() = skin.rune
    private val accent get() = skin.accent
    private val deep get() = skin.accentText
    private val reward get() = skin.reward

    private fun darkSkin(): Skin {
        val accents = com.ironvellum.app.ui.theme.AccentPalette.Default
        return Skin(
            false, 0xFFE8E8E4.toInt(), 0xFFA3A099.toInt(), 0xFF32302B.toInt(), 0xFF171715.toInt(),
            accents.primary, accents.primary, accents.secondary, 0xFF0C0C0B.toInt(), 0xFF10241C.toInt(), 0xFF1F5C46.toInt(),
        )
    }

    private fun skinFor(context: Context): Skin {
        val store = (context.applicationContext as com.ironvellum.app.IronvellumApp).appearanceStore
        if (!store.mode.value.isLight(com.ironvellum.app.ui.theme.isSystemDark(context))) {
            val dark = store.palette.value
            return darkSkin().let { Skin(false, it.ink, it.muted, it.rune, it.panel, dark.primary, dark.primary, dark.secondary, it.onFill, dark.panelTint, dark.panelTintBorder) }
        }
        val roles = store.lightPalette.value.lightAccent()
        return Skin(
            true, 0xFF1F1C17.toInt(), 0xFF5F594D.toInt(), 0xFFD8CFBE.toInt(), 0xFFFBF8F2.toInt(),
            roles.fill, roles.text, roles.reward, com.ironvellum.app.ui.theme.lightAccentForeground(roles.fill), roles.tint, roles.border,
        )
    }

    private const val REQUEST_OPEN = 20
    private const val REQUEST_BEGIN = 21
    private const val REQUEST_EMPTY = 22
    private const val REQUEST_FORGE = 23

    // ---- words ----

    private fun dayShort(day: Int) = DayOfWeek.of(day).getDisplayName(TextStyle.SHORT, Locale.getDefault())

    private fun dayFull(day: Int) = DayOfWeek.of(day).getDisplayName(TextStyle.FULL, Locale.getDefault())

    // Each helper describes its text; a face turns it into a String as it draws (UiText.asString(context)), so the
    // words follow the language and the Themed names switch at that moment.

    private fun sets(n: Int) = UiText.plural(R.plurals.common_sets_count, n)

    private fun next(state: WidgetState): UiText? =
        state.next?.let { UiText.res(R.string.widget_next, it.name, dayShort(it.day)) }

    private fun progress(state: WidgetState) = UiText.plural(R.plurals.widget_progress_sets, state.setsTotal, state.setsDone, state.setsTotal)

    private fun streakChip(state: WidgetState) =
        UiText.themedPlural(R.plurals.widget_streak_chip, R.plurals.widget_streak_chip__t, state.streak)

    private fun streakCaption() = UiText.themed(R.string.widget_streak_caption, R.string.widget_streak_caption__t)

    private fun beginLabel(state: WidgetState): UiText {
        val title = state.title.orEmpty().trim()
        return when {
            state.kind == DayKind.LIVE -> UiText.res(R.string.widget_resume)
            title.isEmpty() -> UiText.res(R.string.widget_begin)
            else -> UiText.res(R.string.widget_begin_named, title)
        }
    }

    private fun noPlan() = UiText.themed(R.string.widget_no_plan_yet, R.string.widget_no_plan_yet__t)

    private fun load(kg: Double?, weighted: Boolean, context: Context): String =
        if (kg != null && kg > 0.0 && !weighted) context.getString(R.string.widget_load_added, Units.withUnit(kg))
        else Units.label(kg, weighted)

    private fun duration(context: Context, minutes: Int) =
        if (minutes >= 60) context.getString(R.string.widget_duration_hm, minutes / 60, minutes % 60)
        else context.getString(R.string.widget_duration_m, minutes)

    // ---- spans: a RemoteViews carries them across, so one TextView can hold a coloured or heavier run ----

    private fun CharSequence.span(start: Int, end: Int, vararg spans: Any): CharSequence =
        if (start < 0 || end > length || start >= end) this
        else SpannableString(this).also { s -> spans.forEach { s.setSpan(it, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) } }

    private fun CharSequence.emphasise(part: String, vararg spans: Any): CharSequence {
        val at = toString().indexOf(part)
        return if (at < 0) this else span(at, at + part.length, *spans)
    }

    private val medium get() = TypefaceSpan("sans-serif-medium")

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

    // ---- small helpers ----

    private fun RemoteViews.text(id: Int, value: CharSequence) = setTextViewText(id, value)

    /** A launcher re-applies a new RemoteViews to the view it already holds, so a list is emptied before it is filled. */
    private fun RemoteViews.fresh(container: Int): Int {
        removeAllViews(container)
        return container
    }

    private fun RemoteViews.show(id: Int, visible: Boolean, hidden: Int = View.GONE) =
        setViewVisibility(id, if (visible) View.VISIBLE else hidden)

    private fun RemoteViews.tint(id: Int, color: Int) = setInt(id, "setColorFilter", color)

    private fun RemoteViews.color(id: Int, color: Int) = setTextColor(id, color)

    private fun describe(vararg parts: String?) = parts.filterNotNull().filter { it.isNotBlank() }.joinToString(". ")

    private fun view(context: Context, layout: Int) = RemoteViews(context.packageName, layout)

    /** The card and everything the layout files fix at the dark values. */
    private fun RemoteViews.skinned(): RemoteViews {
        setInt(android.R.id.background, "setBackgroundResource", if (skin.light) R.drawable.widget_bg_light else R.drawable.widget_bg)
        color(R.id.h_title, INK)
        color(R.id.h_meta, MUTED)
        color(R.id.h_streak, INK)
        tint(R.id.h_streak_icon, reward)
        color(R.id.k_title, INK)
        color(R.id.k_meta, MUTED)
        color(R.id.k_label, MUTED)
        tint(R.id.k_rule, RUNE)
        return this
    }

    /** Fills the action slot: exactly one of its four faces shows. */
    private fun RemoteViews.action(context: Context, action: Action) {
        show(R.id.act_play, action is Action.Play)
        show(R.id.act_tick, action is Action.Tick)
        show(R.id.act_ghost, action is Action.Ghost)
        show(R.id.act_pill, action is Action.Pill)
        show(R.id.act, action !is Action.None)
        tint(R.id.act_play_bg, accent)
        tint(R.id.act_play_icon, skin.onFill)
        tint(R.id.act_tick, deep)
        tint(R.id.act_ghost_ring, RUNE)
        tint(R.id.act_ghost_face, skin.panel)
        color(R.id.act_ghost_text, deep)
        listOf(R.id.act_pill_l, R.id.act_pill_c, R.id.act_pill_r).forEach { tint(it, accent) }
        tint(R.id.act_pill_icon, skin.onFill)
        color(R.id.act_pill_text, skin.onFill)
        when (action) {
            is Action.Play -> {
                setOnClickPendingIntent(R.id.act_play, action.target)
                setContentDescription(R.id.act_play, action.description)
            }
            Action.Tick -> setContentDescription(R.id.act_tick, context.getString(R.string.widget_kicker_done))
            is Action.Ghost -> {
                text(R.id.act_ghost_text, action.text)
                setOnClickPendingIntent(R.id.act_ghost, action.target)
                setContentDescription(R.id.act_ghost, action.text)
            }
            is Action.Pill -> {
                text(R.id.act_pill_text, action.text)
                show(R.id.act_pill_icon, action.icon)
                setOnClickPendingIntent(R.id.act_pill, action.target)
                setContentDescription(R.id.act_pill, action.text)
            }
            Action.None -> Unit
        }
    }

    // ---- Today ----

    private fun dayTitle(state: WidgetState, context: Context): String = when (state.kind) {
        DayKind.RESPITE -> Words.respite.c
        DayKind.NO_CYCLE -> noPlan().asString(context)
        else -> state.title.orEmpty()
    }

    private fun todayAction(context: Context, state: WidgetState, size: WidgetSize): Action = when (state.kind) {
        DayKind.BEGIN, DayKind.LIVE -> Action.Play(beginLabel(state).asString(context), begin(context))
        DayKind.SEALED -> Action.Tick
        DayKind.RESPITE ->
            if (size == WidgetSize.S) Action.None
            else Action.Ghost(UiText.themed(R.string.widget_workout_button, R.string.widget_workout_button__t).asString(context), beginEmpty(context))
        else ->
            if (size == WidgetSize.S) Action.None
            else Action.Pill(UiText.themed(R.string.widget_plan_pill, R.string.widget_plan_pill__t).asString(context), false, forge(context))
    }

    private fun todayMeta(context: Context, state: WidgetState, size: WidgetSize): CharSequence {
        fun UiText.str() = asString(context)
        val small = size == WidgetSize.S
        return when (state.kind) {
            DayKind.BEGIN -> {
                val sets = sets(state.sets)
                when {
                    small -> state.estimateMinutes?.let { UiText.res(R.string.widget_s_meta_begin, sets, it).str() } ?: sets.str()
                    state.estimateMinutes != null -> UiText.res(
                        R.string.widget_meta_day_begin_estimate, dayShort(state.dayOfWeek),
                        UiText.plural(R.plurals.common_exercises_count, state.exercises), sets, state.estimateMinutes,
                    ).str()
                    else -> UiText.res(
                        R.string.widget_meta_day_begin, dayShort(state.dayOfWeek),
                        UiText.plural(R.plurals.common_exercises_count, state.exercises), sets,
                    ).str()
                }
            }
            DayKind.LIVE -> when {
                state.setsTotal == 0 -> UiText.res(R.string.widget_live_none).str()
                small -> {
                    val text = UiText.res(R.string.widget_s_meta_live, state.setsDone, sets(state.setsTotal)).str()
                    text.emphasise(state.setsDone.toString(), ForegroundColorSpan(deep), medium)
                }
                else -> {
                    val progress = progress(state).str()
                    val current = state.rows.firstOrNull { it.current }?.name
                    val text = if (size == WidgetSize.W && current != null) UiText.res(R.string.widget_meta_live_now, progress, current).str()
                    else UiText.res(R.string.widget_meta_live, progress, state.liveMinutes).str()
                    text.emphasise(progress, ForegroundColorSpan(deep), medium)
                }
            }
            DayKind.SEALED -> when {
                small -> UiText.res(R.string.widget_s_meta_done, state.sealedMinutes).str()
                state.records > 0 -> UiText.res(
                    R.string.widget_meta_done_records, sets(state.sealedSets), state.sealedMinutes,
                    UiText.plural(R.plurals.widget_records_count, state.records),
                ).str()
                else -> UiText.res(R.string.widget_meta_done, sets(state.sealedSets), state.sealedMinutes).str()
            }
            DayKind.RESPITE -> state.next?.let {
                if (small) next(state)!!.str()
                else UiText.res(R.string.widget_meta_rest, dayShort(state.dayOfWeek), it.name, dayFull(it.day)).str()
            } ?: if (small) UiText.res(R.string.widget_free_day).str()
            else UiText.res(R.string.widget_meta_rest_free, dayShort(state.dayOfWeek)).str()
            else -> UiText.themed(R.string.widget_plan_hint, R.string.widget_plan_hint__t).str()
        }
    }

    fun today(context: Context, state: WidgetState, size: WidgetSize): RemoteViews {
        skin = skinFor(context)
        val layout = when (size) {
            WidgetSize.S -> R.layout.widget_today_s
            WidgetSize.W -> R.layout.widget_today_w
            else -> R.layout.widget_today_big
        }
        val v = view(context, layout).skinned()
        val title = dayTitle(state, context)
        val meta = todayMeta(context, state, size)
        v.text(R.id.h_title, title)
        v.text(R.id.h_meta, meta)
        v.show(R.id.h_chip, state.kind != DayKind.NO_CYCLE && state.streak > 0)
        v.text(R.id.h_streak, state.streak.toString())
        v.action(context, todayAction(context, state, size))

        if (size == WidgetSize.S || size == WidgetSize.W) {
            val (done, total) = when (state.kind) {
                DayKind.BEGIN -> 0 to state.sets
                DayKind.LIVE -> state.setsDone to state.setsTotal
                DayKind.SEALED -> state.sealedSets to state.sealedSets
                else -> 0 to 0
            }
            v.ladder(context, R.id.ladder, if (size == WidgetSize.S) R.layout.widget_seg4 else R.layout.widget_seg5, done, total)
            v.setContentDescription(R.id.ladder, if (state.kind == DayKind.LIVE) progress(state).asString(context) else "")
        } else {
            v.todayBody(context, state, size == WidgetSize.L)
        }

        v.setOnClickPendingIntent(android.R.id.background, open(context))
        v.setContentDescription(
            android.R.id.background,
            describe(title, meta.toString(), if (state.kind == DayKind.NO_CYCLE) null else streakChip(state).asString(context)),
        )
        return v
    }

    /** One segment per set, [done] of them in the accent. */
    private fun RemoteViews.ladder(context: Context, container: Int, layout: Int, done: Int, total: Int) {
        show(fresh(container), total > 0)
        for (i in 0 until total) {
            val seg = view(context, layout)
            seg.tint(R.id.seg, if (i < done) accent else RUNE)
            addView(container, seg)
        }
    }

    private fun List<ExRow>.window(cap: Int): List<ExRow> {
        if (size <= cap) return this
        val at = indexOfFirst { it.current }.coerceAtLeast(0)
        val start = (at - 1).coerceIn(0, size - cap)
        return subList(start, start + cap)
    }

    private enum class Lead { CHECK, PLAY, DOT, DIAMOND }

    private fun RemoteViews.lead(lead: Lead) {
        show(R.id.row_check, lead == Lead.CHECK)
        show(R.id.row_play, lead == Lead.PLAY)
        show(R.id.row_dot, lead == Lead.DOT)
        show(R.id.row_dia, lead == Lead.DIAMOND)
        tint(R.id.row_check, deep)
        tint(R.id.row_play, deep)
        tint(R.id.row_dot, RUNE)
        tint(R.id.row_dia, deep)
    }

    /** A one-line exercise row (4x2), or a recent-workout row (the 36dp layout). */
    private fun line(
        context: Context, layout: Int, lead: Lead, name: String, right: CharSequence,
        dim: Boolean = false, now: Boolean = false, star: Boolean = false, pips: Pair<Int, Int>? = null, nowPip: Boolean = false,
    ): RemoteViews {
        val r = view(context, layout)
        r.lead(lead)
        r.text(R.id.row_name, if (now) name.span(0, name.length, medium) else name)
        r.color(R.id.row_name, if (dim) MUTED else INK)
        r.text(R.id.row_right, right)
        r.color(R.id.row_right, MUTED)
        r.show(R.id.row_bg, now)
        r.tint(R.id.row_bg, skin.tint)
        r.show(R.id.row_star, star)
        r.tint(R.id.row_star, reward)
        if (pips != null) {
            r.show(R.id.row_pips, true)
            val (done, total) = pips
            for (i in 0 until total) {
                val p = view(context, R.layout.widget_pip)
                if (i < done) {
                    p.tint(R.id.pip, accent)
                } else {
                    p.tint(R.id.pip, if (nowPip && i == done) deep else RUNE)
                    p.show(R.id.pip_face, true)
                    p.tint(R.id.pip_face, if (now) skin.tint else skin.panel)
                }
                r.addView(R.id.row_pips, p)
            }
        }
        return r
    }

    /** A 4x3 row: the name and load, then one box per set. */
    private fun twoLine(
        context: Context, lead: Lead, name: String, right: CharSequence, boxes: List<Triple<Int, Int, Boolean>>,
        dim: Boolean = false, now: Boolean = false, star: Boolean = false,
    ): RemoteViews {
        val r = view(context, R.layout.widget_row44)
        r.lead(lead)
        r.text(R.id.row_name, if (now) name.span(0, name.length, StyleSpan(android.graphics.Typeface.BOLD)) else name)
        r.color(R.id.row_name, if (dim) MUTED else INK)
        r.text(R.id.row_right, right)
        r.color(R.id.row_right, MUTED)
        r.show(R.id.row_bg, now)
        r.tint(R.id.row_bg, skin.tint)
        r.show(R.id.row_star, star)
        r.tint(R.id.row_star, reward)
        for ((reps, state, _) in boxes) r.addView(R.id.row_boxes, box(context, reps, state, if (now) skin.tint else skin.panel))
        return r
    }

    /** [state]: 0 to do, 1 done, 2 the set under way. [face] is the colour inside the ring: the row's own. */
    private fun box(context: Context, reps: Int, state: Int, face: Int): RemoteViews {
        val b = view(context, R.layout.widget_box)
        b.text(R.id.box_text, if (state == 0) reps.toString() else reps.toString().span(0, reps.toString().length, medium))
        when (state) {
            1 -> {
                b.tint(R.id.box_ring, skin.tint)
                b.show(R.id.box_face, false)
                b.color(R.id.box_text, deep)
            }
            2 -> {
                b.tint(R.id.box_ring, deep)
                b.show(R.id.box_face, false)
                b.show(R.id.box_face_now, true)
                b.tint(R.id.box_face_now, face)
                b.color(R.id.box_text, INK)
            }
            else -> {
                b.tint(R.id.box_ring, RUNE)
                b.tint(R.id.box_face, face)
                b.color(R.id.box_text, MUTED)
            }
        }
        return b
    }

    private const val ROW_SLOTS = 5

    private fun RemoteViews.todayBody(context: Context, state: WidgetState, big: Boolean) {
        val body = fresh(R.id.body)
        fun UiText.str() = asString(context)
        val rowLayout = R.layout.widget_row26
        var padAfter = 0
        when (state.kind) {
            DayKind.BEGIN -> state.rows.window(5).also { padAfter = ROW_SLOTS - it.size }.forEach { r ->
                addView(
                    body,
                    if (big) twoLine(context, Lead.DOT, r.name, load(r.loadKg, r.weighted, context), r.reps.map { Triple(it, 0, false) })
                    else line(context, rowLayout, Lead.DOT, r.name, plan(context, r)),
                )
            }
            DayKind.LIVE -> state.rows.window(5).also { padAfter = ROW_SLOTS - it.size }.forEach { r ->
                val lead = if (r.finished) Lead.CHECK else if (r.current) Lead.PLAY else Lead.DOT
                if (big) {
                    val boxes = r.reps.mapIndexed { i, reps ->
                        Triple(if (i < r.done.size) r.done[i] else reps, if (i < r.done.size) 1 else if (r.current && i == r.done.size) 2 else 0, false)
                    }
                    addView(body, twoLine(context, lead, r.name, load(r.loadKg, r.weighted, context), boxes, dim = r.finished, now = r.current))
                } else {
                    addView(
                        body,
                        line(context, rowLayout, lead, r.name, plan(context, r), dim = r.finished, now = r.current, pips = r.done.size to r.reps.size, nowPip = r.current),
                    )
                }
            }
            DayKind.SEALED -> {
                state.rows.window(5).also { padAfter = ROW_SLOTS - it.size }.forEach { r ->
                    val best = UiText.res(R.string.widget_row_best, load(r.bestKg, r.weighted, context), r.bestReps).str()
                    addView(
                        body,
                        if (big) twoLine(context, Lead.CHECK, r.name, best, r.done.map { Triple(it, 1, false) }, star = r.record)
                        else line(context, rowLayout, Lead.CHECK, r.name, best, star = r.record),
                    )
                }
                if (big) {
                    next(state)?.str()?.let { text ->
                        padRows(context, body, padAfter)
                        padAfter = 0
                        val nextLine = view(context, R.layout.widget_next_line)
                        nextLine.text(R.id.next, text.emphasise(state.next!!.name, ForegroundColorSpan(INK), StyleSpan(android.graphics.Typeface.BOLD)))
                        nextLine.color(R.id.next, MUTED)
                        addView(body, nextLine)
                    }
                }
            }
            DayKind.RESPITE -> {
                val next = state.next
                if (next != null) {
                    val label = view(context, R.layout.widget_label)
                    label.text(R.id.label, UiText.res(R.string.widget_next_label, dayShort(next.day), next.name, sets(state.nextSets)).str())
                    label.color(R.id.label, MUTED)
                    addView(body, label)
                }
                state.nextRows.take(4).also { padAfter = 4 - it.size }.forEach { r ->
                    addView(
                        body,
                        if (big) twoLine(context, Lead.DOT, r.name, load(r.loadKg, r.weighted, context), r.reps.map { Triple(it, 0, false) }, dim = true)
                        else line(context, rowLayout, Lead.DOT, r.name, plan(context, r), dim = true),
                    )
                }
            }
            else -> {
                val label = view(context, R.layout.widget_label)
                label.text(R.id.label, context.getString(R.string.widget_recent_label))
                label.color(R.id.label, MUTED)
                addView(body, label)
                state.recent.take(if (big) 4 else 3).also { padAfter = (if (big) 4 else 3) - it.size }.forEach { r ->
                    val right = UiText.res(R.string.widget_recent_when, dayShort(r.day), sets(r.sets)).str()
                    addView(body, line(context, if (big) R.layout.widget_row36 else rowLayout, Lead.DIAMOND, r.name, right))
                }
                padRows(context, body, padAfter)
                padAfter = 0
                val ghost = view(context, R.layout.widget_ghost_btn)
                ghost.text(R.id.gb_text, UiText.themed(R.string.widget_empty_workout, R.string.widget_empty_workout__t).str())
                ghost.color(R.id.gb_text, deep)
                ghost.tint(R.id.gb_ring, RUNE)
                ghost.tint(R.id.gb_face, skin.panel)
                ghost.setOnClickPendingIntent(R.id.gb, beginEmpty(context))
                addView(body, ghost)
            }
        }
        padRows(context, body, padAfter)
    }

    /** Rows share the body's height; empty slots keep a short plan's rows the size of a full one's. */
    private fun RemoteViews.padRows(context: Context, body: Int, slots: Int) {
        repeat(slots.coerceAtLeast(0)) { addView(body, view(context, R.layout.widget_spacer)) }
    }

    private fun plan(context: Context, r: ExRow): String =
        UiText.res(R.string.widget_row_plan, r.reps.size, r.reps.firstOrNull() ?: 0, load(r.loadKg, r.weighted, context)).asString(context)

    // ---- Streak & week ----

    fun week(context: Context, state: WidgetState, size: WidgetSize): RemoteViews {
        skin = skinFor(context)
        val layout = when (size) {
            WidgetSize.S -> R.layout.widget_week_s
            WidgetSize.W -> R.layout.widget_week_w
            WidgetSize.M -> R.layout.widget_week_m
            WidgetSize.L -> R.layout.widget_week_l
        }
        val v = view(context, layout).skinned()
        fun UiText.str() = asString(context)
        val title: String
        val meta: CharSequence
        val action: Action
        when (state.kind) {
            DayKind.BEGIN -> {
                title = state.title.orEmpty()
                meta = (state.estimateMinutes?.let { UiText.res(R.string.widget_week_meta_begin, sets(state.sets), it) }
                    ?: UiText.res(R.string.widget_week_today, sets(state.sets))).str()
                action = Action.Pill(UiText.res(R.string.widget_begin).str(), true, begin(context))
            }
            DayKind.LIVE -> {
                title = state.title.orEmpty()
                meta = if (state.setsTotal == 0) UiText.res(R.string.widget_live_none).str()
                else UiText.res(R.string.widget_meta_live, progress(state).str(), state.liveMinutes).str()
                action = Action.Pill(UiText.res(R.string.widget_resume).str(), true, begin(context))
            }
            DayKind.SEALED -> {
                title = state.title.orEmpty()
                meta = UiText.res(R.string.widget_meta_done, sets(state.sealedSets), state.sealedMinutes).str()
                action = Action.Tick
            }
            DayKind.RESPITE -> {
                title = Words.respite.c
                meta = next(state)?.str() ?: UiText.res(R.string.widget_free_day).str()
                action = Action.Ghost(UiText.themed(R.string.widget_workout_button, R.string.widget_workout_button__t).str(), beginEmpty(context))
            }
            else -> {
                title = noPlan().str()
                meta = UiText.themedPlural(R.plurals.widget_week_workouts, R.plurals.widget_week_workouts__t, state.weekDone).str()
                action = Action.Ghost(UiText.themed(R.string.widget_plan_pill, R.string.widget_plan_pill__t).str(), forge(context))
            }
        }
        v.text(R.id.k_number, state.streak.toString())
        v.color(R.id.k_number, if (state.streak > 0) reward else MUTED)
        v.text(R.id.k_label, if (size == WidgetSize.S) UiText.themed(R.string.widget_streak_caption, R.string.widget_streak_caption__t).str() else streakCaption().str())
        if (size == WidgetSize.M || size == WidgetSize.L) {
            v.text(R.id.k_title, title)
            v.text(R.id.k_meta, meta)
            v.action(context, action)
            v.totals(context, state)
        }
        v.weekColumns(context, state, size)
        v.setOnClickPendingIntent(android.R.id.background, open(context))
        v.setContentDescription(
            android.R.id.background,
            describe(streakChip(state).str(), title.takeIf { size == WidgetSize.M || size == WidgetSize.L }, meta.toString().takeIf { size == WidgetSize.M || size == WidgetSize.L }, weekSummary(context, state)),
        )
        return v
    }

    private fun weekSummary(context: Context, state: WidgetState): String {
        val done = state.week.count { it == DayMark.DONE }
        val planned = state.week.count { it == DayMark.PLANNED }
        return context.getString(R.string.widget_week_summary, done, planned)
    }

    private fun RemoteViews.totals(context: Context, state: WidgetState) {
        fun UiText.str() = asString(context)
        fun bold(text: String, figure: String): CharSequence = text.emphasise(figure, ForegroundColorSpan(INK), StyleSpan(android.graphics.Typeface.BOLD))
        val workouts = if (state.kind == DayKind.NO_CYCLE) {
            UiText.themedPlural(R.plurals.widget_totals_workouts_only, R.plurals.widget_totals_workouts_only__t, state.weekDone).str()
        } else {
            UiText.themedPlural(R.plurals.widget_totals_workouts, R.plurals.widget_totals_workouts__t, state.weekPlanned, state.weekDone, state.weekPlanned).str()
        }
        text(R.id.t_workouts, bold(workouts, state.weekDone.toString()))
        text(R.id.t_sets, bold(sets(state.weekSets).str(), state.weekSets.toString()))
        val time = duration(context, state.weekMinutes)
        text(R.id.t_time, bold(UiText.res(R.string.widget_totals_time, time).str(), time))
        listOf(R.id.t_workouts, R.id.t_sets, R.id.t_time).forEach { color(it, MUTED) }
    }

    private fun RemoteViews.weekColumns(context: Context, state: WidgetState, size: WidgetSize) {
        val layout = when (size) {
            WidgetSize.S -> R.layout.widget_wcol_s
            WidgetSize.W -> R.layout.widget_wcol_w
            WidgetSize.M -> R.layout.widget_wcol_m
            WidgetSize.L -> R.layout.widget_wcol_l
        }
        val big = size == WidgetSize.M || size == WidgetSize.L
        val vmax = maxOf(18, state.daySets.maxOrNull() ?: 0)
        val cols = fresh(R.id.cols)
        for (i in 0 until 7) {
            val mark = state.week[i]
            val today = i + 1 == state.dayOfWeek
            val c = view(context, layout)
            val letter = DayOfWeek.of(i + 1).getDisplayName(TextStyle.NARROW, Locale.getDefault())
            c.show(R.id.c_fill, today)
            c.show(R.id.c_ring, today)
            c.tint(R.id.c_fill, skin.tint)
            c.tint(R.id.c_ring, skin.tintBorder)
            c.text(R.id.c_letter, if (!big && today) letter.span(0, letter.length, StyleSpan(android.graphics.Typeface.BOLD)) else letter)
            c.color(R.id.c_letter, if (!big && today) INK else MUTED)
            c.color(R.id.c_date, if (today || mark != DayMark.FREE) INK else MUTED)
            c.text(R.id.c_date, state.dates.getOrNull(i)?.toString().orEmpty())
            when (mark) {
                DayMark.DONE -> {
                    c.setImageViewResource(R.id.c_mark, R.drawable.widget_diamond)
                    c.tint(R.id.c_mark, deep)
                }
                DayMark.PLANNED -> {
                    c.setImageViewResource(R.id.c_mark, R.drawable.widget_diamond_outline)
                    c.tint(R.id.c_mark, MUTED)
                }
                DayMark.FREE -> if (big) c.show(R.id.c_mark, false, View.INVISIBLE) else {
                    c.setImageViewResource(R.id.c_mark, R.drawable.widget_dot)
                    c.tint(R.id.c_mark, RUNE)
                }
            }
            if (big) {
                val name = state.dayNames.getOrNull(i) ?: context.getString(R.string.widget_week_rest)
                c.text(R.id.c_name, name)
                c.color(R.id.c_name, if (mark == DayMark.DONE || today) INK else MUTED)
            }
            if (size == WidgetSize.L) {
                c.setImageViewBitmap(R.id.c_bar, dayBar(context, state.daySets.getOrElse(i) { 0 }, mark == DayMark.PLANNED, vmax))
            }
            addView(cols, c)
        }
    }

    /** One day's sets as a bar, its count above it: drawn as a bitmap because a bar's height is data, and a RemoteViews cannot size a view. */
    private fun dayBar(context: Context, sets: Int, planned: Boolean, vmax: Int): Bitmap {
        val metrics = context.resources.displayMetrics
        val dp = metrics.density
        val bitmap = createBitmap((24 * dp).roundToInt(), (80 * dp).roundToInt())
        val canvas = Canvas(bitmap)
        val left = (24 * dp - 18 * dp) / 2f
        val bottom = 80 * dp
        if (sets > 0) {
            val height = (64f * sets / vmax).roundToInt().coerceAtLeast(2) * dp
            canvas.drawRoundRect(
                RectF(left, bottom - height, left + 18 * dp, bottom), 3 * dp, 3 * dp,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent },
            )
            val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = MUTED
                textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 10f, metrics)
                textAlign = Paint.Align.CENTER
                fontFeatureSettings = "tnum"
            }
            canvas.drawText(sets.toString(), 12 * dp, bottom - height - 3 * dp - text.fontMetrics.descent, text)
        } else if (planned) {
            val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = RUNE
                style = Paint.Style.STROKE
                strokeWidth = dp
            }
            canvas.drawRoundRect(
                RectF(left + dp / 2, bottom - 40 * dp + dp / 2, left + 18 * dp - dp / 2, bottom - dp / 2), 3 * dp, 3 * dp, stroke,
            )
        }
        return bitmap
    }
}
