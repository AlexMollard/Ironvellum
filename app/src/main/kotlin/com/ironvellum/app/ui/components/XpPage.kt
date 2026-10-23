package com.ironvellum.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironvellum.app.R
import com.ironvellum.app.domain.Xp
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.IronvellumTracking
import com.ironvellum.app.ui.theme.Metal

/** The page's glow: a dark emerald while it fills, gold for the level moment. */
private val FillGlow get() = IronvellumColors.FillGlow
private val LevelGlow get() = IronvellumColors.LevelGlow

/** Dark ink on the gold button. */
private val OnGold get() = IronvellumColors.OnGoldFill

/**
 * The XP a seal paid, on a page of its own, before the summary: a big running
 * number, one large bar filling segment by segment, the segment filling now
 * named, and the finished ones stacked below. A level crossing takes the whole
 * page for about a second and a half ([XpTimeline.HOLD_MS]); then a fresh bar
 * takes the rest. Done lists effort and bonuses and waits for Continue; nothing
 * advances by itself. Skip, there until Done, goes straight to the summary.
 *
 * Every figure is the [Xp.Breakdown] the award is the sum of; nothing is priced
 * here. With animations off the clock starts at its end, so the page opens on
 * Done, with the level-up said in text and to a screen reader.
 */
@Composable
internal fun XpRevealPage(
    breakdown: Xp.Breakdown,
    totalXp: Long,
    levelBefore: Int,
    levelAfter: Int,
    /** The tier the level reached when it crossed one, else null. */
    tierAfter: String?,
    inscriptionsBanked: Int,
    /** Shown top left, in capitals: "Pull · sealed". */
    caption: String,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
    motion: Boolean = animatorsOn(LocalContext.current),
) {
    val timeline = remember(breakdown, totalXp) { XpTimeline(breakdown, totalXp) }
    val shades = rememberShades(timeline.lines)
    val t = rememberClock(timeline.endMs, motion)
    val state = timeline.stateAt(t)
    Haptics(
        motion,
        *buildList {
            timeline.holds.forEach { add(it.startMs to HapticFeedbackType.Confirm) }
            add(timeline.endMs to HapticFeedbackType.SegmentTick)
        }.toTypedArray(),
    )
    val spoken = remember(breakdown, levelBefore, levelAfter) { XpReveal.pageSpoken(breakdown, levelBefore, levelAfter) }
    val glow by animateColorAsState(
        if (state.phase == XpTimeline.Phase.CROSSING) LevelGlow else FillGlow,
        if (motion) tween(300) else snap(),
        label = "xp-glow",
    )
    val done = state.phase == XpTimeline.Phase.DONE
    val skipDescription = stringResource(R.string.xp_skip_summary)
    // Back does what the visible control does: Skip until the page is done, then Continue.
    BackHandler { if (done) onContinue() else onSkip() }

    Column(
        Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(glow, IronvellumColors.Abyss),
                        center = Offset(size.width / 2f, size.height * 0.3f),
                        radius = size.maxDimension * 0.7f,
                    ),
                )
            }
            .statusBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 14.dp).height(56.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                caption.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                letterSpacing = IronvellumTracking.InlineLabel,
                color = Dim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // Gone once the page is finished: Continue takes its place at the bottom.
            if (!done) {
                Box(
                    Modifier
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .clickable(role = Role.Button, onClick = onSkip)
                        .semantics { contentDescription = skipDescription },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.xppage_skip_button),
                        fontFamily = ChakraPetch,
                        fontSize = 14.sp,
                        color = Dim,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }
        }
        when (state.phase) {
            XpTimeline.Phase.FILLING -> FillingContent(timeline, state, t, shades, spoken, Modifier.weight(1f))
            XpTimeline.Phase.CROSSING -> CrossingContent(
                timeline, state, t, tierAfter, XpReveal.veilLine(inscriptionsBanked), Modifier.weight(1f),
            )
            XpTimeline.Phase.DONE -> DoneContent(timeline, shades, spoken, levelBefore, levelAfter, Modifier.weight(1f))
        }
        if (done) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(Metal.Fabled.bounds())
                        .clickable(role = Role.Button, onClick = onContinue),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.common_continue).uppercase(),
                        fontFamily = ChakraPetch,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        letterSpacing = 3.sp,
                        color = OnGold,
                    )
                }
            }
        }
    }
}

/** The big "+358": counted up while filling, the finished total once done. Read as the final figures, never mid-count. */
@Composable
private fun TotalBlock(counted: Int, size: Int, caption: String, spoken: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = spoken
                liveRegion = LiveRegionMode.Polite
                heading()
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "+${count(counted)}",
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            fontSize = size.sp,
            style = TextStyle(fontFeatureSettings = "tnum"),
            color = IronvellumColors.EmeraldText,
            maxLines = 1,
        )
        Text(
            caption,
            style = MaterialTheme.typography.labelMedium,
            letterSpacing = IronvellumTracking.InlineLabel,
            color = Dim,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** "LEVEL 16" on the left, "785 / 1600" on the right, over the large bar. */
@Composable
private fun LevelBar(
    timeline: XpTimeline,
    bar: Int,
    t: Long,
    done: Boolean,
    shades: List<Color>,
    current: Int,
    label: String,
    modifier: Modifier = Modifier,
) {
    val level = timeline.bars[bar]
    val progressOf = if (done) ({ _: XpReveal.Slice -> 1f }) else timeline.sliceProgress(bar, t)
    val into = level.startXp + level.slices.sumOf { (it.xp * progressOf(it)).toDouble() }.toLong()
    Column(modifier.fillMaxWidth()) {
        XpBar(level, shades, progressOf, current, Modifier, height = 20.dp)
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                letterSpacing = IronvellumTracking.InlineLabel,
                color = IronvellumColors.SovereignGold,
            )
            Text(
                "${count(into)} / ${count(level.needed)}",
                style = MaterialTheme.typography.bodySmall,
                color = Dim,
            )
        }
    }
}

@Composable
private fun FillingContent(
    timeline: XpTimeline,
    state: XpTimeline.State,
    t: Long,
    shades: List<Color>,
    spoken: String,
    modifier: Modifier,
) {
    val lines = timeline.lines
    val line = lines.getOrNull(state.segment)
    Column(modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
        Spacer(Modifier.weight(0.35f))
        TotalBlock(state.counted.toInt(), 64, stringResource(R.string.victory_row_xp), spoken)
        Spacer(Modifier.height(36.dp))
        LevelBar(timeline, state.bar, t, false, shades, state.segment, stringResource(R.string.xp_level_label, timeline.bars[state.bar].level).uppercase())
        Spacer(Modifier.height(32.dp))
        // The segment filling now, named big; its slot keeps its height between segments.
        Column(
            Modifier.fillMaxWidth().height(72.dp).clearAndSetSemantics { },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (line != null) {
                Text(
                    "${line.label} +${line.xp}",
                    fontFamily = ChakraPetch,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 26.sp,
                    color = if (XpReveal.isLift(line)) IronvellumColors.EmeraldText else IronvellumColors.SovereignGold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val note = if (XpReveal.isLift(line)) XpReveal.subline(line) else XpReveal.bonusNote(line)
                if (note.isNotEmpty()) {
                    Text(
                        note,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Dim,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        Spacer(Modifier.weight(0.2f))
        // Finished segments stack from the bottom; the oldest scroll off the top when they outgrow the room.
        Box(
            Modifier.fillMaxWidth().weight(1f).clipToBounds(),
            contentAlignment = Alignment.BottomStart,
        ) {
            Column(Modifier.fillMaxWidth().wrapContentHeight(align = Alignment.Bottom, unbounded = true)) {
                lines.indices.filter { timeline.lineDone(it, t) }.forEach { i ->
                    XpRow(
                        lines[i],
                        shades[i],
                        RowState.DONE,
                        subline = if (XpReveal.isLift(lines[i])) XpReveal.subline(lines[i]) else XpReveal.bonusNote(lines[i]),
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun CrossingContent(
    timeline: XpTimeline,
    state: XpTimeline.State,
    t: Long,
    tierAfter: String?,
    veil: String?,
    modifier: Modifier,
) {
    val hold = timeline.holds.getOrNull(state.hold) ?: return
    val from = timeline.bars[hold.toBar - 1].level
    val to = timeline.bars[hold.toBar].level
    val last = hold.toBar == timeline.bars.lastIndex
    val shown = phase(t, hold.startMs, 250)
    val move = "$from → $to"
    val levelUpDescription = stringResource(R.string.xppage_level_up_description, from, to)
    Column(
        modifier.fillMaxWidth().padding(horizontal = 18.dp).graphicsLayer { alpha = shown },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Column(
            Modifier.fillMaxWidth().clearAndSetSemantics {
                // The quiet line is part of what is said, or a screen reader never hears it.
                contentDescription = levelUpDescription + if (last && veil != null) ". $veil" else ""
                liveRegion = LiveRegionMode.Assertive
                heading()
            },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.xp_level_up_tag).uppercase(),
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                letterSpacing = IronvellumTracking.InlineLabel,
                color = IronvellumColors.SovereignGold,
            )
            Text(
                move,
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.Bold,
                fontSize = if (move.length <= 7) 80.sp else 60.sp,
                style = TextStyle(brush = Metal.Fabled.bounds(), fontFeatureSettings = "tnum"),
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
            )
            if (last && tierAfter != null) {
                Text(tierAfter, style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.SovereignGold)
            }
            if (last && veil != null) {
                Text(
                    veil,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Dim,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        // The fresh bar, with the rest of the segment that crossed already in it.
        LevelBar(timeline, hold.toBar, t, false, rememberShades(timeline.lines), -1, stringResource(R.string.xp_level_label, to).uppercase())
        Spacer(Modifier.height(72.dp))
    }
}

@Composable
private fun DoneContent(
    timeline: XpTimeline,
    shades: List<Color>,
    spoken: String,
    levelBefore: Int,
    levelAfter: Int,
    modifier: Modifier,
) {
    val lines = timeline.lines
    val total = lines.sumOf { it.xp }
    val effort = lines.filter { XpReveal.isLift(it) }.sumOf { it.xp }
    val efforts = lines.indices.filter { XpReveal.isLift(lines[it]) }
    val bonuses = lines.indices.filter { !XpReveal.isLift(lines[it]) }
    val level = timeline.bars.last().level
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        TotalBlock(total, 56, stringResource(R.string.xppage_total_caption, effort, total - effort), spoken)
        Spacer(Modifier.height(22.dp))
        // With animations off the level moment never plays, so the level-up is said here.
        LevelBar(timeline, timeline.bars.lastIndex, 0L, true, shades, -1, if (levelAfter > levelBefore) {
            stringResource(R.string.xppage_level_up_label, level).uppercase()
        } else {
            stringResource(R.string.xp_level_label, level).uppercase()
        })
        Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            if (efforts.isNotEmpty()) SectionLabel(stringResource(R.string.xp_section_effort).uppercase(), IronvellumColors.Emerald)
            efforts.forEach { XpRow(lines[it], shades[it], RowState.DONE, subline = XpReveal.subline(lines[it])) }
            if (bonuses.isNotEmpty()) SectionLabel(stringResource(R.string.xp_section_bonuses).uppercase(), IronvellumColors.SovereignGold)
            bonuses.forEach { XpRow(lines[it], shades[it], RowState.DONE, subline = XpReveal.bonusNote(lines[it])) }
        }
        Spacer(Modifier.height(12.dp))
    }
}
