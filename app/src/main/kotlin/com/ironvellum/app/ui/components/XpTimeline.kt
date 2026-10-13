package com.ironvellum.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import com.ironvellum.app.domain.Xp

/**
 * When everything on the full-screen XP page happens, as plain numbers: the
 * page reads one clock (ms since it landed) and asks this what to show.
 *
 * The page fills one bar at a time. Each [XpReveal.Slice] of [XpReveal.bars] is a [Step]
 * that fills over [XpReveal.FILL_MS], one every [XpReveal.SEG_MS]. After the step that
 * closes a level's bar the page holds for [HOLD_MS] on the level moment, one hold per
 * level crossed, then the next level's bar takes the rest. A clock at [endMs] or past it
 * is the finished page, which is exactly what a clock with animations off starts at.
 *
 * It prices nothing: the slices are [Xp.Breakdown] lines, exactly.
 */
internal class XpTimeline(breakdown: Xp.Breakdown, totalAfter: Long) {

    /** One slice of one bar, filling from [startMs]. */
    data class Step(val bar: Int, val slice: XpReveal.Slice, val startMs: Long)

    /** The level moment that holds after bar [toBar] - 1 filled up, from [startMs]. */
    data class Hold(val toBar: Int, val startMs: Long)

    enum class Phase { FILLING, CROSSING, DONE }

    /** Everything the page draws at one instant. */
    data class State(
        val phase: Phase,
        /** The bar on show: the level being filled, or the fresh one a hold has just opened. */
        val bar: Int,
        /** Index in the breakdown's lines of the segment filling now, -1 when none is. */
        val segment: Int,
        /** The hold being shown, an index into [holds]; -1 outside one. */
        val hold: Int,
        /** The XP counted so far across every bar, 0 up to the total. */
        val counted: Float,
    )

    val lines: List<Xp.Line> = breakdown.lines
    val bars: List<XpReveal.LevelBar> = XpReveal.bars(breakdown, totalAfter)
    val steps: List<Step>
    val holds: List<Hold>
    val endMs: Long

    init {
        val built = mutableListOf<Step>()
        val gaps = mutableListOf<Hold>()
        var at = INTRO_MS
        bars.forEachIndexed { index, bar ->
            val last = index == bars.lastIndex
            bar.slices.forEachIndexed { i, slice ->
                // The rest of the segment that closed the last bar starts inside the hold that opened this one.
                val carried = i == 0 && index > 0 && bars[index - 1].slices.lastOrNull()?.segment == slice.segment
                val start = if (carried) gaps.last().startMs + CARRY_AT_MS else at
                built += Step(index, slice, start)
                if (i == bar.slices.lastIndex && !last) {
                    // The closing step ends first, then the level moment holds.
                    gaps += Hold(index + 1, start + XpReveal.FILL_MS)
                    at = start + XpReveal.FILL_MS + HOLD_MS
                } else {
                    at = start + XpReveal.SEG_MS
                    if (carried) at = maxOf(at, gaps.last().startMs + HOLD_MS)
                }
            }
        }
        steps = built
        holds = gaps
        val lastFill = built.maxOfOrNull { it.startMs + XpReveal.FILL_MS } ?: INTRO_MS
        endMs = maxOf(lastFill, gaps.lastOrNull()?.let { it.startMs + HOLD_MS } ?: 0L) + SETTLE_MS
    }

    /** How far [step] has filled at [t], 0 to 1, eased. */
    fun progress(step: Step, t: Long): Float =
        FastOutSlowInEasing.transform(((t - step.startMs) / XpReveal.FILL_MS.toFloat()).coerceIn(0f, 1f))

    /** How far the slices of bar [bar] have filled at [t], as the bar draws them. */
    fun sliceProgress(bar: Int, t: Long): (XpReveal.Slice) -> Float {
        val mine = steps.filter { it.bar == bar }
        return { slice -> mine.firstOrNull { it.slice == slice }?.let { progress(it, t) } ?: 0f }
    }

    /** Whether every step of line [index] has finished filling at [t]. */
    fun lineDone(index: Int, t: Long): Boolean {
        val mine = steps.filter { it.slice.segment == index }
        return t >= endMs || mine.all { t >= it.startMs + XpReveal.FILL_MS }
    }

    fun stateAt(t: Long): State {
        if (t >= endMs) return State(Phase.DONE, bars.lastIndex, -1, -1, lines.sumOf { it.xp }.toFloat())
        val counted = steps.sumOf { (it.slice.xp * progress(it, t)).toDouble() }.toFloat()
        val holding = holds.indexOfFirst { t >= it.startMs && t < it.startMs + HOLD_MS }
        if (holding >= 0) return State(Phase.CROSSING, holds[holding].toBar, -1, holding, counted)
        val passed = holds.count { t >= it.startMs + HOLD_MS }
        val latest = steps.lastOrNull { it.bar == passed && t >= it.startMs }
        return State(Phase.FILLING, passed, latest?.slice?.segment ?: -1, -1, counted)
    }

    companion object {
        /** The page lands before the first segment starts. */
        const val INTRO_MS = 450L

        /** The level moment holds this long: about a second and a half. */
        const val HOLD_MS = 1_500L

        /** A level's carried-over rest starts filling this far into the hold that opened its bar. */
        const val CARRY_AT_MS = 500L

        /** After the last fill, the page settles before it reads as finished. */
        const val SETTLE_MS = 350L
    }
}
