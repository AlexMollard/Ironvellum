package com.ironvellum.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironvellum.app.R
import com.ironvellum.app.data.Units
import com.ironvellum.app.domain.Xp
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.IronvellumTracking
import com.ironvellum.app.ui.theme.inkBorder

/**
 * The XP a seal paid, told segment by segment: one bar filling in the order of
 * the trial, a segment per movement in emerald shades, then the named bonuses
 * in gold shades, with the rows under it saying what earned what.
 *
 * Everything shown is [Xp.Breakdown], the same breakdown the award is the sum
 * of; this file prices nothing. With animations off the clock the caller hands
 * in is already at its end, so the final state shows at once.
 */
internal object XpReveal {

    /** One segment fills over [FILL_MS] and the next starts [SEG_MS] after it. */
    const val SEG_MS = 420L
    const val FILL_MS = 360L

    /** How long the whole reveal runs for [segments] segments. */
    fun durationMs(segments: Int): Long = segments * SEG_MS + 300L

    /** A segment's own piece of one level's bar. */
    data class Slice(val segment: Int, val xp: Long)

    /** One level's bar: what was already in it and the slices this trial adds, in order. */
    data class LevelBar(val level: Int, val needed: Long, val startXp: Long, val slices: List<Slice>)

    /**
     * The bars the breakdown fills: the lifter's level at the start of the
     * trial first, and a fresh bar for every level it crosses, carrying the
     * rest of the segment that crossed. Slices sum to the segments exactly.
     */
    fun bars(breakdown: Xp.Breakdown, totalAfter: Long): List<LevelBar> {
        val before = (totalAfter - breakdown.total).coerceAtLeast(0L)
        var progress = Xp.progress(before)
        var level = progress.level
        var startXp = progress.intoLevel
        var needed = progress.needed
        var slices = mutableListOf<Slice>()
        val bars = mutableListOf<LevelBar>()
        breakdown.lines.forEachIndexed { index, line ->
            var left = line.xp.toLong()
            while (left > 0) {
                val room = needed - startXp - slices.sumOf { it.xp }
                val take = minOf(left, room)
                if (take > 0) slices += Slice(index, take)
                left -= take
                if (take == room) {
                    bars += LevelBar(level, needed, startXp, slices)
                    level++
                    startXp = 0
                    needed = Xp.xpForNextLevel(level)
                    slices = mutableListOf()
                }
            }
        }
        // A trial that ends exactly on a level boundary still opens the next level's (empty) bar.
        if (slices.isNotEmpty() || bars.isEmpty() || bars.last().level < level) bars += LevelBar(level, needed, startXp, slices)
        return bars
    }

    /** How far segment [index] has filled at [elapsed] ms, 0 to 1, eased. */
    fun progress(elapsed: Long, index: Int): Float =
        FastOutSlowInEasing.transform(((elapsed - index * SEG_MS) / FILL_MS.toFloat()).coerceIn(0f, 1f))

    /** The XP the bar holds at [elapsed] ms: segments filled, or partly, so far. */
    fun filled(lines: List<Xp.Line>, elapsed: Long): Float =
        lines.indices.sumOf { (lines[it].xp * progress(elapsed, it)).toDouble() }.toFloat()

    /** The segment being filled at [elapsed], or -1 before the first and after the last. */
    fun current(lines: List<Xp.Line>, elapsed: Long): Int {
        if (elapsed <= 0 || lines.isEmpty() || elapsed >= lines.size * SEG_MS) return -1
        return (elapsed / SEG_MS).toInt().coerceIn(0, lines.lastIndex)
    }

    /** A movement or an activity is effort; everything else is a bonus. */
    fun isLift(line: Xp.Line): Boolean = line.kind == Xp.Kind.EXERCISE || line.kind == Xp.Kind.ACTIVITY

    /** "4 hard sets · +17.7 kg added", "3 hard sets · top 60 kg", "3 hard holds", "2 hard · 1 easy": what a movement was made of. */
    fun subline(line: Xp.Line): String {
        if (line.kind == Xp.Kind.ACTIVITY) return ""
        val made = when {
            line.hardSets > 0 && line.easySets > 0 -> uiString(R.string.xp_subline_hard_easy, line.hardSets, line.easySets)
            line.hardSets > 0 -> uiPlural(if (line.hold) R.plurals.xp_subline_hard_holds else R.plurals.xp_subline_hard_sets, line.hardSets)
            line.easySets > 0 -> uiPlural(if (line.hold) R.plurals.xp_subline_easy_holds else R.plurals.xp_subline_easy_sets, line.easySets)
            else -> ""
        }
        // Said in words: a bare "+60 kg" read as XP. A lifted load is the top one; a body's extra is what was added.
        val load = line.topLoadKg?.takeIf { it > 0.0 }?.let { uiString(if (line.externalLoad) R.string.xp_subline_top_load else R.string.xp_subline_added_load, Units.withUnit(it)) }
        return listOfNotNull(made.ifEmpty { null }, load).joinToString(" · ")
    }

    /** The line under a bonus: what earned it. */
    fun bonusNote(line: Xp.Line): String = when (line.kind) {
        Xp.Kind.PEAK, Xp.Kind.RECORD -> line.detail
        Xp.Kind.HONEST_EFFORT -> uiString(R.string.xp_bonus_note_honest)
        Xp.Kind.RITE_AS_PLANNED -> uiString(R.string.xp_bonus_note_rite)
        Xp.Kind.WEEK_COVERED -> uiString(R.string.xp_bonus_note_week)
        Xp.Kind.WELCOME_BACK -> uiPair(R.string.xp_bonus_note_welcome_back, R.string.xp_bonus_note_welcome_back__t, Xp.WELCOME_BACK_DAYS)
        else -> ""
    }

    /** One row spoken whole: what a screen reader says, whatever the animation has reached. */
    fun spoken(line: Xp.Line): String {
        val note = if (isLift(line)) subline(line) else bonusNote(line)
        return if (note.isEmpty()) {
            uiString(R.string.xp_line_spoken, line.label, line.xp)
        } else {
            uiString(R.string.xp_line_spoken_note, line.label, note, line.xp)
        }
    }

    /** The effort part of a breakdown: movements and activities. */
    fun effortOf(breakdown: Xp.Breakdown): Int = breakdown.lines.filter { isLift(it) }.sumOf { it.xp }

    /**
     * What the full-screen page says in one breath, whatever the animation has reached:
     * the total, its split, and the level it left the lifter at.
     */
    fun pageSpoken(breakdown: Xp.Breakdown, levelBefore: Int, levelAfter: Int): String {
        val effort = effortOf(breakdown)
        val level = if (levelAfter > levelBefore) {
            uiString(R.string.xp_page_spoken_level_up, levelBefore, levelAfter)
        } else {
            uiString(R.string.xp_page_spoken_level, levelAfter)
        }
        return uiString(R.string.xp_page_spoken, breakdown.total, effort, breakdown.total - effort, level)
    }

    /** The level moment's quiet line: what the level banked, from the inscriptions actually paid; null when none were. */
    fun veilLine(banked: Int): String? = if (banked <= 0) null else uiPlural(R.plurals.always_xp_veil_line, banked)

    /** What the whole panel says in one breath, for the heading. */
    fun headline(breakdown: Xp.Breakdown, level: Int): String =
        uiPlural(R.plurals.xp_headline, breakdown.lines.size, breakdown.total, breakdown.lines.size, level)

    // Emerald shades for lifts (deep to light) and gold for bonuses, from the lifter's own accents.
    internal fun liftShade(index: Int, count: Int): Color =
        lerp(
            lerp(IronvellumColors.Emerald, Color.Black, 0.35f),
            // On parchment the light end stays a full accent: a pale tint would vanish.
            if (IronvellumColors.light) IronvellumColors.Emerald else lerp(IronvellumColors.EmeraldBright, Color.White, 0.45f),
            if (count <= 1) 0.35f else index / (count - 1f),
        )

    internal fun bonusShade(index: Int, count: Int): Color =
        lerp(
            lerp(IronvellumColors.SovereignGold, Color.Black, 0.30f),
            if (IronvellumColors.light) IronvellumColors.SovereignGold else lerp(IronvellumColors.SovereignGold, Color.White, 0.45f),
            if (count <= 1) 0.35f else index / (count - 1f),
        )
}

/** The shade of every segment: lifts in emerald, bonuses in gold. */
@Composable
internal fun rememberShades(lines: List<Xp.Line>): List<Color> = remember(lines) {
    val lifts = lines.count { XpReveal.isLift(it) }
    val bonuses = lines.size - lifts
    var l = 0
    var b = 0
    lines.map { if (XpReveal.isLift(it)) XpReveal.liftShade(l++, lifts) else XpReveal.bonusShade(b++, bonuses) }
}

/**
 * The panel itself. [t] is the page's clock in ms and [startMs] when this
 * panel's reveal begins on it; a clock already at its end shows the final state.
 */
@Composable
internal fun XpRevealPanel(
    breakdown: Xp.Breakdown,
    totalXp: Long,
    levelBefore: Int,
    levelAfter: Int,
    t: Long,
    startMs: Long,
    modifier: Modifier = Modifier,
    /** The finished panel at once, with no clock: the summary after the full-screen page already played it. */
    final: Boolean = false,
) {
    val lines = breakdown.lines
    val shades = rememberShades(lines)
    val bars = remember(breakdown, totalXp) { XpReveal.bars(breakdown, totalXp) }
    val elapsed = (t - startMs).coerceAtLeast(0L)
    val end = lines.size * XpReveal.SEG_MS
    val settled = final || elapsed >= end
    val current = if (settled) -1 else XpReveal.current(lines, elapsed)
    val filled = if (settled) breakdown.total.toFloat() else XpReveal.filled(lines, elapsed)
    val crossing = bars.size > 1
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier
            .fillMaxWidth()
            .background(IronvellumColors.Vault, shape)
            .inkBorder(IronvellumColors.Emerald, shape, 1.dp)
            .padding(horizontal = 10.dp, vertical = 14.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .clearAndSetSemantics {
                    contentDescription = XpReveal.headline(breakdown, levelAfter)
                    liveRegion = LiveRegionMode.Polite
                    heading()
                },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                stringResource(R.string.xp_gain_label, count(filled.toInt())),
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.Bold,
                fontSize = 34.sp,
                style = TextStyle(fontFeatureSettings = "tnum"),
                color = IronvellumColors.Emerald,
                maxLines = 1,
            )
            Text(
                stringResource(R.string.xp_level_label, if (crossing && settled) levelAfter else levelBefore).uppercase(),
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                letterSpacing = IronvellumTracking.InlineLabel,
                color = IronvellumColors.SovereignGold,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        bars.forEachIndexed { i, bar ->
            if (crossing) {
                Text(
                    stringResource(R.string.xp_level_label, bar.level).uppercase(),
                    fontFamily = ChakraPetch,
                    fontSize = 11.sp,
                    letterSpacing = IronvellumTracking.InlineLabel,
                    color = if (i == bars.lastIndex) IronvellumColors.SovereignGold else Dim,
                    modifier = Modifier.padding(start = 8.dp, top = if (i == 0) 0.dp else 6.dp, bottom = 2.dp),
                )
            }
            XpBar(
                bar,
                shades,
                progressOf = { slice -> if (settled) 1f else XpReveal.progress(elapsed, slice.segment) },
                current = current,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = if (crossing) 4.dp else 10.dp),
            )
        }
        val last = bars.last()
        val into = last.startXp + last.slices.sumOf { slice ->
            val p = if (settled) 1f else XpReveal.progress(elapsed, slice.segment)
            (slice.xp * p).toDouble()
        }.toLong()
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val caption = when {
                current >= 0 -> "▲ ${lines[current].label} +${lines[current].xp}"
                else -> {
                    val effort = lines.filter { XpReveal.isLift(it) }.sumOf { it.xp }
                    stringResource(R.string.xp_effort_bonus_caption, effort, breakdown.total - effort)
                }
            }
            Text(
                caption,
                style = MaterialTheme.typography.bodySmall,
                color = if (current >= 0) IronvellumColors.Emerald else Dim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).clearAndSetSemantics { },
            )
            Text(
                stringResource(R.string.xp_progress_label, count(into), count(last.needed)),
                style = MaterialTheme.typography.bodySmall,
                color = Dim,
                maxLines = 1,
                modifier = Modifier.padding(start = 8.dp).clearAndSetSemantics { },
            )
        }
        val efforts = lines.indices.filter { XpReveal.isLift(lines[it]) }
        val bonuses = lines.indices.filter { !XpReveal.isLift(lines[it]) }
        if (efforts.isNotEmpty()) SectionLabel(stringResource(R.string.xp_section_effort).uppercase(), IronvellumColors.Emerald)
        efforts.forEach { XpRow(lines[it], shades[it], rowState(elapsed, it, settled, current), subline = XpReveal.subline(lines[it])) }
        if (bonuses.isNotEmpty()) SectionLabel(stringResource(R.string.xp_section_bonuses).uppercase(), IronvellumColors.SovereignGold)
        bonuses.forEach { XpRow(lines[it], shades[it], rowState(elapsed, it, settled, current), subline = XpReveal.bonusNote(lines[it])) }
    }
}

internal enum class RowState { PENDING, CURRENT, DONE }

internal fun rowState(elapsed: Long, index: Int, settled: Boolean, current: Int): RowState = when {
    settled -> RowState.DONE
    index == current -> RowState.CURRENT
    index < current -> RowState.DONE
    else -> RowState.PENDING
}

@Composable
internal fun SectionLabel(text: String, color: Color) {
    Text(
        text,
        fontFamily = ChakraPetch,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        letterSpacing = IronvellumTracking.InlineLabel,
        color = color,
        modifier = Modifier
            .padding(start = 8.dp, end = 8.dp, top = 10.dp, bottom = 2.dp)
            .semantics { heading() },
    )
}

@Composable
internal fun XpRow(line: Xp.Line, shade: Color, state: RowState, subline: String) {
    val shown = state != RowState.PENDING
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (state == RowState.CURRENT) IronvellumColors.Emerald.copy(alpha = 0.08f) else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 7.dp)
            // The whole row, finished: a reader hears the result, not the animation's progress.
            .clearAndSetSemantics { contentDescription = XpReveal.spoken(line) },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).rotate(45f).background(shade.copy(alpha = if (shown) 1f else 0.32f)))
        Column(Modifier.weight(1f)) {
            Text(
                line.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = IronvellumColors.Ink.copy(alpha = if (shown) 1f else 0.32f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subline.isNotEmpty()) {
                Text(
                    subline,
                    style = MaterialTheme.typography.bodySmall,
                    color = Dim.copy(alpha = if (shown) 1f else 0.32f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            if (shown) "+${line.xp}" else "—",
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            style = TextStyle(fontFeatureSettings = "tnum"),
            color = if (shown) shade else Dim.copy(alpha = 0.32f),
        )
    }
}

/**
 * One level's bar: flat slices in their shades, 2dp dark gaps between, the top-left corner cut.
 * [progressOf] says how far each slice has filled, 0 to 1, and [current] is the segment filling now (-1 for none).
 */
@Composable
internal fun XpBar(
    bar: XpReveal.LevelBar,
    shades: List<Color>,
    progressOf: (XpReveal.Slice) -> Float,
    current: Int,
    modifier: Modifier,
    height: Dp = 14.dp,
) {
    val track = IronvellumColors.Rune
    val before = IronvellumColors.Bracket
    val gap = IronvellumColors.PlateGround
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .drawBehind {
                val cut = Path().apply {
                    moveTo(6.dp.toPx(), 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    lineTo(0f, 6.dp.toPx())
                    close()
                }
                clipPath(cut) {
                    drawRect(track, size = size)
                    val perXp = size.width / bar.needed.toFloat()
                    drawRect(before, size = Size(bar.startXp * perXp, size.height))
                    var x = bar.startXp * perXp
                    val seam = 2.dp.toPx()
                    for (slice in bar.slices) {
                        val p = progressOf(slice)
                        val w = slice.xp * perXp * p
                        if (w <= 0f) continue
                        val alpha = if (slice.segment == current) 0.55f else 1f
                        drawRect(shades[slice.segment].copy(alpha = alpha), Offset(x, 0f), Size(w, size.height))
                        drawRect(gap, Offset(x + w - seam.coerceAtMost(w), 0f), Size(seam.coerceAtMost(w), size.height))
                        x += w
                    }
                }
            }
            .clearAndSetSemantics { },
    )
}
