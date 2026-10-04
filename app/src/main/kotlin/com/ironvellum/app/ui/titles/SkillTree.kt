package com.ironvellum.app.ui.titles

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.runtime.State
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.withFrameNanos
import android.os.SystemClock
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironvellum.app.domain.Skills
import com.ironvellum.app.ui.components.animatorsOn
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.InkCircleShape
import com.ironvellum.app.ui.theme.InkEdgeShape
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.inkArc
import com.ironvellum.app.ui.theme.inkBorder

/** The node disc. Room around it is for the glow and the breathing ring. */
private val NodeSize = NODE_DP.dp
private val TopPad = TOP_PAD_DP.dp
private val TextGap = TEXT_GAP_DP.dp
private val EdgeW = 2.dp

/** The locked node ring: 3:1 against the page, dimmer than the open green. */
internal val LockedDot = Color(0xFF64625C)

/** The locked node's fill, where its glyph has the least contrast. */
internal val LockedRowBg = Color(0xFF101512)

/** A locked glyph: part of the silhouette, deliberately close to its disc. */
internal val LockedGlyph = Color(0xFF3A3A36)

private fun inkCorners(r: androidx.compose.ui.unit.Dp, salt: Int) =
    InkEdgeShape(salt, CornerSize(r), CornerSize(r), CornerSize(r), CornerSize(r))

/** What a node is, in shape, glyph and badge as well as colour. */
private enum class NodeState { MASTERED, NEXT, LOCKED }

/**
 * One path as a node graph: game-style discs in tier rows, each carrying the
 * pictogram of its movement and a tier chip, joined by ink lines from the
 * bottom of each prerequisite to the top of its dependants. The edges are
 * drawn on a canvas behind the nodes.
 */
@Composable
fun SkillTreeGraph(
    line: String,
    mastered: Set<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** Best logged effort per technique, for the "best 40/60s" cue read out on open nodes. */
    best: Map<String, SkillGuidance.Effort> = emptyMap(),
    /** The lifter, so a load-bearing standard is judged against their bodyweight. */
    bodyweightKg: Double? = null,
    female: Boolean = false,
    /**
     * Scroll the enclosing page so this technique sits in the upper third of the
     * view; null leaves it alone. Set it (and set it again after clearing it to
     * repeat) to jump to a skill, e.g. a prerequisite followed from another path.
     * Without it the tree opens on [openTarget] and then leaves scrolling to the user.
     */
    scrollToSkill: String? = null,
    /**
     * The vertical scroll of the page holding this tree, and how far down that page the tree starts.
     * The graph scrolls exactly this page to its target. Without it there is no auto-scroll: a
     * bring-into-view request would also be honoured by every pager around the tree, and dragged
     * the Codex back to PATHS whenever its tabs swept past this page.
     */
    pageScroll: ScrollState? = null,
    treeTopInPage: Dp = 0.dp,
    /** Taps a "needs X (Path)" caption: the callee takes the lifter to that technique, wherever it lives. */
    onOpenPrerequisite: (String) -> Unit = {},
    /**
     * Scroll-to-skill seam: name a technique of this path and the graph scrolls it into view,
     * then calls [onFocusHandled]. A name that is not on this path is ignored, so one request
     * can be offered to every page of a pager and only the owning path acts on it.
     */
    focus: String? = null,
    onFocusHandled: () -> Unit = {},
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val columns = columnsFor(maxWidth.value)
        val cellW = maxWidth / columns
        val layout = remember(line, columns) { treeLayout(line, columns) }

        // Label lines scale with the system font so a large setting grows the
        // rows instead of clipping the label; the geometry reads the same height.
        val uiDensity = LocalDensity.current
        val lineH = with(uiDensity) { LabelLine.toDp().value }
        // The label's line count is measured, not guessed from its length: the
        // real cell width and the system font scale decide where a name wraps.
        // Bold is the widest weight a label takes (the open node), so the row
        // is never reserved short.
        val measurer = rememberTextMeasurer()
        val labelStyle = MaterialTheme.typography.labelSmall.copy(
            fontSize = LabelSize,
            lineHeight = LabelLine,
            letterSpacing = 0.sp,
            fontWeight = FontWeight.Bold,
        )
        val measured = remember(layout, cellW, uiDensity, labelStyle) {
            val textW = with(uiDensity) { (cellW - LabelPad * 2).roundToPx() }.coerceAtLeast(1)
            layout.nodes.associate { n ->
                n.skill.name to measurer.measure(
                    n.skill.name, labelStyle, constraints = Constraints(maxWidth = textW), maxLines = MAX_LABEL_LINES,
                ).lineCount.coerceIn(1, MAX_LABEL_LINES)
            }
        }
        // The marker under a name ("needs L-sit (Core)") wraps too, at its own size; its longest form is reserved.
        val crossStyle = MaterialTheme.typography.labelSmall.copy(fontSize = CrossSize, lineHeight = LabelLine, letterSpacing = 0.sp)
        val measuredCross = remember(layout, cellW, uiDensity, crossStyle) {
            val textW = with(uiDensity) { (cellW - LabelPad * 2).roundToPx() }.coerceAtLeast(1)
            layout.nodes.associate { n ->
                n.skill.name to (layout.crossMarker(n.skill.name)?.let {
                    measurer.measure(it, crossStyle, constraints = Constraints(maxWidth = textW), maxLines = MAX_LABEL_LINES)
                        .lineCount.coerceIn(1, MAX_LABEL_LINES)
                } ?: 0)
            }
        }
        val metrics = remember(layout, cellW, lineH, measured, measuredCross) {
            treeMetrics(layout, cellW.value, lineH, crossLinesOf = { measuredCross.getValue(it.skill.name) }) {
                measured.getValue(it.skill.name)
            }
        }
        val tops = metrics.tops
        val levelLines = metrics.levelLines

        // One breathing phase for every open node, so they pulse together.
        val pulse = rememberBreath()

        // Where the eye should land when a path opens: the next open technique, an
        // explicit request winning (a prerequisite followed from elsewhere, via [focus] or
        // [scrollToSkill]). Only [pageScroll] moves, so the node lands in the upper third of its own
        // page and no pager around it is touched. The open target is keyed so a late-arriving mastered
        // set still moves it, but only while the path is settling; after that the lifter's own scroll
        // is left alone. Saved with the page, so swiping back to a path does not yank it again.
        val openTarget = remember(layout, mastered) { layout.openTarget(mastered) }
        val density = LocalDensity.current.density
        val openedAt = rememberSaveable(line) { SystemClock.uptimeMillis() }
        // A focus request names any technique; only the page that owns it acts on it.
        val focusName = focus?.takeIf { f -> layout.nodes.any { it.skill.name == f } }
        val requested = focusName ?: scrollToSkill
        val goTo = requested ?: openTarget
        LaunchedEffect(line, goTo, requested != null) {
            val page = pageScroll ?: return@LaunchedEffect
            val y = goTo?.let { layout.discTopOf(it, metrics) } ?: return@LaunchedEffect
            if (requested == null && SystemClock.uptimeMillis() - openedAt > SETTLE_MS) return@LaunchedEffect
            withFrameNanos { }
            withFrameNanos { }
            val top = (treeTopInPage.value + y - SCROLL_LEAD_DP) * density
            page.animateScrollTo((top - page.viewportSize * UPPER_SHARE).toInt().coerceIn(0, page.maxValue))
            if (focusName != null) onFocusHandled()
        }

        Box(Modifier.fillMaxWidth().height(tops.last().dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = EdgeW.toPx()
                val dp = density
                // Dim lines first, so the lit ones win where they share a stretch.
                layout.edges.sortedBy { edgeRank(it.to, mastered) }.forEach { e ->
                    val path = Path()
                    layout.edgeCurve(e, metrics).forEachIndexed { i, c ->
                        if (i == 0) path.moveTo(c.p0.x * dp, c.p0.y * dp)
                        path.cubicTo(c.c1.x * dp, c.c1.y * dp, c.c2.x * dp, c.c2.y * dp, c.p3.x * dp, c.p3.y * dp)
                    }
                    drawPath(path, edgeColor(e.to, mastered), style = Stroke(stroke, cap = StrokeCap.Round))
                }
            }

            layout.nodes.forEach { node ->
                val skill = node.skill
                val isMastered = skill.name in mastered
                val unlocked = Skills.unlocked(skill, mastered)
                val needs = skill.firstUnmetPrerequisite(mastered)
                val cue = if (isMastered) null else SkillGuidance.progressCue(skill, best[skill.name], bodyweightKg, female)
                val openNeeds = if (isMastered) emptyList() else layout.crossNeeds[skill.name].orEmpty().filter { it.skill !in mastered }
                val crossLines = if (skill.name in layout.crossNeeds) metrics.crossLines[node.level] else 0
                // The caption is a sibling of the node, not a child: the node merges its
                // descendants into one button, which would swallow the caption's own tap.
                Column(
                    Modifier.offset(x = cellW * node.x, y = tops[node.level].dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    SkillNode(
                        skill = skill,
                        state = when {
                            isMastered -> NodeState.MASTERED
                            unlocked -> NodeState.NEXT
                            else -> NodeState.LOCKED
                        },
                        description = rowDescription(skill, isMastered, unlocked, needs, cue),
                        width = cellW,
                        lines = levelLines[node.level],
                        pulse = pulse,
                        onClick = { onSelect(skill.name) },
                    )
                    // The marker room is always reserved, so the line leaving this node starts below it
                    // whether or not anything is still outstanding.
                    if (crossLines > 0) {
                        val marker = crossNeedMarker(openNeeds, mastered)
                        if (marker != null) {
                            CrossNeedCaption(marker, openNeeds.first(), cellW, crossLines) { onOpenPrerequisite(openNeeds.first().skill) }
                        } else {
                            Spacer(Modifier.height(with(LocalDensity.current) { (LabelLine * crossLines).toDp() }))
                        }
                    }
                }
            }
        }
    }
}

private val LabelLine = 12.sp
private val LabelSize = LABEL_SP.sp
private val CrossSize = CROSS_SP.sp

/** Side padding inside a label plate. */
private val LabelPad = LABEL_PAD_DP.dp

/** How long after a path opens its target may still move (the lifter data can arrive a beat late). */
private const val SETTLE_MS = 2500L

/** Clear space kept above the target disc (its tier chip and halo) when scrolling to it. */
private const val SCROLL_LEAD_DP = 16f

/** Where the target lands in its page, as a share of the page's visible height from the top. */
private const val UPPER_SHARE = 0.25f

/**
 * A 0..1 breath, 2s each way, for the open nodes. Held still at mid-breath when
 * the system animation scale is off, so reduced motion gets a calm ring rather
 * than a loop.
 */
@Composable
private fun rememberBreath(): State<Float> {
    val context = LocalContext.current
    val animated = remember {
        animatorsOn(context)
    }
    if (!animated) return remember { mutableFloatStateOf(0.5f) }
    return rememberInfiniteTransition(label = "treeBreath").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "treeBreathPhase",
    )
}

/** A line's colour from the dependant it leads to: gold once mastered, green when open, ink while locked. */
private fun edgeColor(to: String, mastered: Set<String>): Color = when {
    to in mastered -> IronvellumColors.SovereignGold.copy(alpha = 0.85f)
    Skills.forName(to)?.let { Skills.unlocked(it, mastered) } == true ->
        IronvellumColors.SystemGreen.copy(alpha = 0.85f)
    else -> IronvellumColors.Bracket
}

private fun edgeRank(to: String, mastered: Set<String>): Int = when {
    to in mastered -> 2
    Skills.forName(to)?.let { Skills.unlocked(it, mastered) } == true -> 1
    else -> 0
}

/** "needs L-sit (Core)" for the first prerequisite still on another path; null when none is outstanding. */
internal fun crossNeedMarker(needs: List<CrossNeed>, mastered: Set<String>): String? {
    val open = needs.filter { it.skill !in mastered }
    val first = open.firstOrNull() ?: return null
    return "needs ${first.skill} (${first.line})" + if (open.size > 1) " +${open.size - 1}" else ""
}

/** What a screen reader hears for one node: name, tier, state, then what to do next. */
internal fun rowDescription(
    skill: Skills.SkillDef,
    mastered: Boolean,
    unlocked: Boolean,
    needs: String?,
    cue: String?,
): String = buildList {
    add(skill.name)
    add("tier ${Skills.tierLabel(skill.tier)}")
    when {
        mastered -> add("mastered")
        unlocked -> {
            add("available, next")
            add("standard ${skill.standard}")
            cue?.let(::add)
        }
        else -> add("locked, needs ${needs ?: "its prerequisite"}")
    }
}.joinToString(", ")

@Composable
private fun SkillNode(
    skill: Skills.SkillDef,
    state: NodeState,
    description: String,
    width: Dp,
    lines: Int,
    pulse: State<Float>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = remember(skill.name) { InkCircleShape(skill.name.hashCode() and 0xFF) }
    val textH = with(LocalDensity.current) { (LabelLine * 2f).toDp() }
    // The page tone: a label on it hides its own node's line passing under, so the line never cuts text.
    val plateColour = MaterialTheme.colorScheme.background
    val chip = remember { inkCorners(3.dp, 13) }
    val plate = remember { inkCorners(4.dp, 11) }
    // The whole cell is the touch target, so the node's hit area is well past 48dp.
    Column(
        modifier
            .width(width)
            .clickable(role = Role.Button, onClickLabel = "Open technique") { onClick() }
            // One sentence for the whole node; the visible texts stay in the
            // merged node so it is still found by its name.
            .semantics(mergeDescendants = true) { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.height(TopPad))
        Box(
            Modifier
                .size(NodeSize)
                .drawBehind {
                    val r = size.minDimension / 2f
                    when (state) {
                        // a soft halo, the gold of an earned thing
                        NodeState.MASTERED -> drawRect(
                            Brush.radialGradient(
                                0.62f to IronvellumColors.SovereignGold.copy(alpha = 0.42f),
                                1f to Color.Transparent,
                                center = center,
                                radius = r * 1.55f,
                            ),
                            topLeft = Offset(-r, -r),
                            size = androidx.compose.ui.geometry.Size(size.width + 2 * r, size.height + 2 * r),
                        )
                        // a ring that breathes outward from the disc
                        NodeState.NEXT -> {
                            val t = pulse.value
                            inkArc(
                                center, r + 2.dp.toPx() + 5.dp.toPx() * t, 0f, 360f,
                                IronvellumColors.SystemGreen.copy(alpha = 0.55f - 0.4f * t), 1.5.dp.toPx(),
                                seed = 3, taperEnds = false,
                            )
                        }
                        NodeState.LOCKED -> Unit
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            val artwork = techniqueArtwork(skill.name)
            val art = when (state) {
                NodeState.MASTERED -> ArtState.MASTERED
                NodeState.NEXT -> ArtState.OPEN
                NodeState.LOCKED -> ArtState.LOCKED
            }
            Box(
                Modifier
                    .size(NodeSize)
                    .clip(shape)
                    .background(
                        // Engraved art keeps one dark ground in every state, so the cream figure reads the
                        // same on all of them; the state is the ring. A glyph node keeps its gold fill.
                        when {
                            artwork != null && state == NodeState.LOCKED -> LockedRowBg
                            artwork != null -> Color(0xFF15251F)
                            state == NodeState.MASTERED -> IronvellumColors.SovereignGold
                            state == NodeState.NEXT -> Color(0xFF15251F)
                            else -> LockedRowBg
                        },
                    )
                    .inkBorder(
                        artRing(art),
                        shape,
                        if (state == NodeState.NEXT || (artwork != null && state == NodeState.MASTERED)) 3.dp else 1.5.dp,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (artwork != null) {
                    Image(
                        painter = painterResource(artwork),
                        contentDescription = null,
                        colorFilter = artFilter(art),
                        alpha = artAlpha(art),
                        modifier = Modifier.size(NodeSize - 10.dp),
                    )
                } else {
                    SkillGlyph(
                        family = glyphFamily(skill),
                        color = when (state) {
                            NodeState.MASTERED -> IronvellumColors.Abyss
                            NodeState.NEXT -> IronvellumColors.Ink
                            NodeState.LOCKED -> LockedGlyph
                        },
                        modifier = Modifier.size(40.dp),
                    )
                }
            }

            // State as a badge, not the colour alone: a check, or a lock.
            if (state != NodeState.NEXT) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 3.dp, y = 3.dp)
                        .size(20.dp)
                        .clip(InkCircleShape(5))
                        .background(IronvellumColors.Abyss)
                        .inkBorder(if (state == NodeState.MASTERED) IronvellumColors.SovereignGold else LockedDot, InkCircleShape(5), 1.5.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (state == NodeState.MASTERED) Icons.Filled.Check else Icons.Filled.Lock,
                        contentDescription = null,
                        tint = if (state == NodeState.MASTERED) IronvellumColors.SovereignGold else IronvellumColors.InkMuted,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }

            // The tier, as a small ink chip on the node's shoulder.
            Text(
                Skills.tierLabel(skill.tier),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                lineHeight = 11.sp,
                letterSpacing = 0.sp,
                color = IronvellumColors.Abyss,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = (-5).dp, y = (-3).dp)
                    .clip(chip)
                    .background(if (state == NodeState.LOCKED) IronvellumColors.InkMuted else IronvellumColors.Ink)
                    .padding(horizontal = 3.dp, vertical = 1.dp),
            )
        }
        Box(Modifier.height(TextGap))
        Text(
            skill.name,
            style = MaterialTheme.typography.labelSmall,
            fontSize = LabelSize,
            lineHeight = LabelLine,
            letterSpacing = 0.sp,
            fontWeight = if (state == NodeState.NEXT) FontWeight.Bold else FontWeight.Normal,
            color = when (state) {
                NodeState.MASTERED -> IronvellumColors.SovereignGold
                NodeState.NEXT -> IronvellumColors.Ink
                NodeState.LOCKED -> IronvellumColors.InkMuted
            },
            textAlign = TextAlign.Center,
            maxLines = MAX_LABEL_LINES,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .widthIn(max = width)
                .heightIn(min = textH / 2 * lines)
                .clip(plate)
                .background(plateColour)
                .padding(horizontal = LabelPad),
        )
    }
}

/**
 * The "needs L-sit (Core)" line under a locked node, as a link to that technique. It fills the
 * lines the tree's rows reserve for it; the touch target is Compose's own 48dp minimum around
 * it, so the rows do not grow.
 */
@Composable
private fun CrossNeedCaption(text: String, need: CrossNeed, width: Dp, lines: Int, onClick: () -> Unit) {
    val textH = with(LocalDensity.current) { (LabelLine * 2f).toDp() }
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontSize = CrossSize,
        lineHeight = LabelLine,
        letterSpacing = 0.sp,
        color = IronvellumColors.SystemGreen,
        textDecoration = TextDecoration.Underline,
        textAlign = TextAlign.Center,
        maxLines = MAX_LABEL_LINES,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .widthIn(max = width)
            // The rows reserve [lines] for this marker; a minimum, so a rounding pixel never cuts the last line.
            .heightIn(min = textH / 2 * lines)
            .clip(remember { inkCorners(4.dp, 11) })
            .background(MaterialTheme.colorScheme.background)
            .clickable(role = Role.Button, onClickLabel = "Open ${need.skill}, ${need.line} path") { onClick() }
            .padding(horizontal = 3.dp),
    )
}
