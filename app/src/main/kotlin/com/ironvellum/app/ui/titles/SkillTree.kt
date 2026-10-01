package com.ironvellum.app.ui.titles

import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.runtime.State
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironvellum.app.domain.Skills
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.InkCircleShape
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.inkBorder

/** The node disc. Room around it is for the glow and the breathing ring. */
private val NodeSize = 54.dp
private val TopPad = 5.dp
private val TextGap = 2.dp

/** Room under a row for the lines that join it to the next. */
private val EdgeGap = 16.dp
private val EdgeW = 2.dp

/** The locked node ring: 3:1 against the page, dimmer than the open green. */
internal val LockedDot = Color(0xFF64625C)

/** The locked node's fill, where its glyph has the least contrast. */
internal val LockedRowBg = Color(0xFF101512)

/** A locked glyph: part of the silhouette, deliberately close to its disc. */
internal val LockedGlyph = Color(0xFF3A3A36)

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
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val columns = columnsFor(maxWidth.value)
        val cellW = maxWidth / columns
        val layout = remember(line, columns) { treeLayout(line, columns) }

        // Two text lines, scaled with the system font so a large setting grows
        // the rows instead of clipping the label.
        val textH = with(LocalDensity.current) { (LabelLine * 2f).toDp() }
        val levelLines = remember(layout) {
            layout.levels.indices.map { l -> layout.nodes.filter { it.level == l }.maxOf { labelLines(it.skill.name) } }
        }
        val tops = remember(layout, textH) {
            layout.levels.runningFold(0.dp) { y, level ->
                y + levelHeight(level, levelLines[layout.levels.indexOf(level)], textH / 2)
            }
        }
        val at = remember(layout) { layout.nodes.associateBy { it.skill.name } }

        // One breathing phase for every open node, so they pulse together.
        val pulse = rememberBreath()

        // Where the eye should land when a path opens. Keyed on the target too:
        // the mastered set can arrive after the first frame, and a claim moves
        // the frontier on.
        val firstNext = remember(layout, mastered) { layout.firstNext(mastered)?.name }
        val nextRequester = remember { BringIntoViewRequester() }
        LaunchedEffect(line, firstNext) {
            if (firstNext != null) nextRequester.bringIntoView()
        }

        Box(Modifier.fillMaxWidth().height(tops.last())) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = EdgeW.toPx()
                fun centreX(n: PlacedSkill) = (cellW * (n.x + 0.5f)).toPx()

                // Dim lines first, so the lit ones win where they share a stretch.
                layout.edges.sortedBy { edgeRank(it.to, mastered) }.forEach { e ->
                    val parent = at.getValue(e.from)
                    val child = at.getValue(e.to)
                    val px = centreX(parent)
                    val cx = centreX(child)
                    // bottom of the parent's disc to the top of the child's
                    val y0 = (tops[parent.level] + TopPad + NodeSize).toPx()
                    val y1 = (tops[child.level] + TopPad).toPx()
                    // A long edge swings out under its parent and drops down the
                    // child's column, if that column is clear of the rows between.
                    val clearAtChild = (parent.level + 1 until child.level).all { l ->
                        layout.nodes.none { it.level == l && kotlin.math.abs(it.x - child.x) < 0.9f }
                    }
                    val longJog = child.level > parent.level + 1 && clearAtChild
                    val path = Path().apply {
                        moveTo(px, y0)
                        if (longJog) {
                            val yj = (tops[parent.level + 1] - EdgeGap / 4).toPx()
                            cubicTo(px, (y0 + yj) / 2, cx, (y0 + yj) / 2, cx, yj)
                            lineTo(cx, y1)
                        } else {
                            val ym = (y0 + y1) / 2
                            cubicTo(px, ym, cx, ym, cx, y1)
                        }
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
                SkillNode(
                    skill = skill,
                    state = when {
                        isMastered -> NodeState.MASTERED
                        unlocked -> NodeState.NEXT
                        else -> NodeState.LOCKED
                    },
                    description = rowDescription(skill, isMastered, unlocked, needs, cue),
                    crossNeed = if (isMastered) null else crossNeedMarker(layout.crossNeeds[skill.name].orEmpty(), mastered),
                    width = cellW,
                    lines = levelLines[node.level],
                    pulse = pulse,
                    onClick = { onSelect(skill.name) },
                    modifier = Modifier
                        .offset(x = cellW * node.x, y = tops[node.level])
                        .then(if (skill.name == firstNext) Modifier.bringIntoViewRequester(nextRequester) else Modifier),
                )
            }
        }
    }
}

private val LabelLine = 12.sp

private fun levelHeight(level: TreeLevel, lines: Int, lineH: Dp): Dp =
    TopPad + NodeSize + TextGap + lineH * lines + (if (level.hasCrossNeed) lineH * 2 else 0.dp) + EdgeGap

/** A short name fits one line under a node; anything longer is given two. An estimate: the gap below absorbs a miss. */
internal fun labelLines(name: String): Int = if (name.length <= 12) 1 else 2

/**
 * A 0..1 breath, 2s each way, for the open nodes. Held still at mid-breath when
 * the system animation scale is off, so reduced motion gets a calm ring rather
 * than a loop.
 */
@Composable
private fun rememberBreath(): State<Float> {
    val context = LocalContext.current
    val animated = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
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

/** The page tone behind the tree: a label on it hides a line passing under, so the line never cuts text. */
private val LabelPlate = Color(0xFF0D0E11)

@Composable
private fun SkillNode(
    skill: Skills.SkillDef,
    state: NodeState,
    description: String,
    crossNeed: String?,
    width: Dp,
    lines: Int,
    pulse: State<Float>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = remember(skill.name) { InkCircleShape(skill.name.hashCode() and 0xFF) }
    val textH = with(LocalDensity.current) { (LabelLine * 2f).toDp() }
    val plate = RoundedCornerShape(4.dp)
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
                        NodeState.MASTERED -> drawCircle(
                            Brush.radialGradient(
                                0.62f to IronvellumColors.SovereignGold.copy(alpha = 0.42f),
                                1f to Color.Transparent,
                                center = center,
                                radius = r * 1.55f,
                            ),
                            radius = r * 1.55f,
                        )
                        // a ring that breathes outward from the disc
                        NodeState.NEXT -> {
                            val t = pulse.value
                            drawCircle(
                                IronvellumColors.SystemGreen.copy(alpha = 0.55f - 0.4f * t),
                                radius = r + 2.dp.toPx() + 5.dp.toPx() * t,
                                style = Stroke(1.5.dp.toPx()),
                            )
                        }
                        NodeState.LOCKED -> Unit
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(NodeSize)
                    .clip(shape)
                    .background(
                        when (state) {
                            NodeState.MASTERED -> IronvellumColors.SovereignGold
                            NodeState.NEXT -> Color(0xFF15251F)
                            NodeState.LOCKED -> LockedRowBg
                        },
                    )
                    .inkBorder(
                        when (state) {
                            NodeState.MASTERED -> IronvellumColors.SovereignGold
                            NodeState.NEXT -> IronvellumColors.SystemGreen
                            NodeState.LOCKED -> LockedDot
                        },
                        shape,
                        if (state == NodeState.NEXT) 3.dp else 1.5.dp,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                SkillGlyph(
                    family = glyphFamily(skill),
                    color = when (state) {
                        NodeState.MASTERED -> IronvellumColors.Abyss
                        NodeState.NEXT -> IronvellumColors.Ink
                        NodeState.LOCKED -> LockedGlyph
                    },
                    modifier = Modifier.size(32.dp),
                )
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
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (state == NodeState.LOCKED) IronvellumColors.InkMuted else IronvellumColors.Ink)
                    .padding(horizontal = 3.dp, vertical = 1.dp),
            )
        }
        Box(Modifier.height(TextGap))
        Text(
            skill.name,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 11.sp,
            lineHeight = LabelLine,
            letterSpacing = 0.sp,
            fontWeight = if (state == NodeState.NEXT) FontWeight.Bold else FontWeight.Normal,
            color = when (state) {
                NodeState.MASTERED -> IronvellumColors.SovereignGold
                NodeState.NEXT -> IronvellumColors.Ink
                NodeState.LOCKED -> IronvellumColors.InkMuted
            },
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .widthIn(max = width)
                .heightIn(min = textH / 2 * lines)
                .clip(plate)
                .background(LabelPlate)
                .padding(horizontal = 3.dp),
        )
        if (crossNeed != null) {
            Text(
                crossNeed,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                lineHeight = LabelLine,
                letterSpacing = 0.sp,
                color = IronvellumColors.InkMuted,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .widthIn(max = width)
                    .height(textH)
                    .clip(plate)
                    .background(LabelPlate)
                    .padding(horizontal = 3.dp),
            )
        }
    }
}
