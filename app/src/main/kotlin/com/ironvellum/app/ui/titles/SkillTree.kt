package com.ironvellum.app.ui.titles

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
import com.ironvellum.app.ui.theme.inkStroke

/** Width of the left gutter that carries the tier numerals. */
private val GutterW = 28.dp
private val NodeSize = 32.dp
private val TopPad = 4.dp
private val TextGap = 2.dp

/** Room under a row for the lines that join it to the next. */
private val BusGap = 14.dp
private val EdgeW = 2.dp

/** The locked node ring: 3:1 against the page, dimmer than the open green. */
internal val LockedDot = Color(0xFF64625C)

/** The locked node's fill, where its glyph and label have the least contrast. */
internal val LockedRowBg = Color(0xFF101512)

/** What a node is, in shape and glyph as well as colour. */
private enum class NodeState { MASTERED, NEXT, LOCKED }

/**
 * One path as a node graph: round nodes in tier rows (numerals down the left
 * edge), joined by ink lines from each prerequisite to its dependants, with
 * branches side by side. The edges are drawn on a canvas behind the nodes.
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
        val columns = columnsFor((maxWidth - GutterW).value)
        val cellW = (maxWidth - GutterW) / columns
        val layout = remember(line, columns) { treeLayout(line, columns) }

        // Two text lines, scaled with the system font so a large setting grows
        // the rows instead of clipping the label.
        val textH = with(LocalDensity.current) { (LabelLine * 2f).toDp() }
        val tops = remember(layout, textH) {
            layout.levels.runningFold(0.dp) { y, level -> y + levelHeight(level, textH) }
        }
        val at = remember(layout) { layout.nodes.associateBy { it.skill.name } }

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
                fun centreX(n: PlacedSkill) = (GutterW + cellW * (n.x + 0.5f)).toPx()

                layout.levels.forEachIndexed { l, level ->
                    if (level.startsTier && l > 0) {
                        val y = tops[l].toPx() + 1.dp.toPx()
                        inkStroke(Offset(0f, y), Offset(size.width, y), IronvellumColors.Rune.copy(alpha = 0.7f), 1.dp.toPx(), seed = l, taperEnds = false)
                    }
                }

                // Dim lines first, so the lit ones win where they share a stretch.
                layout.edges.sortedBy { edgeRank(it.to, mastered) }.forEach { e ->
                    val parent = at.getValue(e.from)
                    val child = at.getValue(e.to)
                    val px = centreX(parent)
                    val cx = centreX(child)
                    // leaves the parent below its label, lands on the child's top
                    val y0 = (tops[parent.level + 1] - BusGap).toPx()
                    val y1 = (tops[child.level] + TopPad).toPx()
                    // A long edge jogs under its parent and drops down the
                    // child's column, if that column is clear of the rows between.
                    val clearAtChild = (parent.level + 1 until child.level).all { l ->
                        layout.nodes.none { it.level == l && kotlin.math.abs(it.x - child.x) < 0.9f }
                    }
                    val longJog = child.level > parent.level + 1 && clearAtChild
                    val bus = (if (longJog) tops[parent.level + 1] - BusGap / 2 else tops[child.level] - BusGap / 2).toPx()
                    val color = edgeColor(e.to, mastered)
                    val seed = e.hashCode()
                    if (kotlin.math.abs(px - cx) < 0.5f) {
                        inkStroke(Offset(px, y0), Offset(cx, y1), color, stroke, seed, taperEnds = false)
                    } else {
                        inkStroke(Offset(px, y0), Offset(px, bus), color, stroke, seed, taperEnds = false)
                        inkStroke(Offset(px, bus), Offset(cx, bus), color, stroke, seed + 1, taperEnds = false)
                        inkStroke(Offset(cx, bus), Offset(cx, y1), color, stroke, seed + 2, taperEnds = false)
                    }
                }
            }

            layout.levels.forEachIndexed { l, level ->
                if (level.startsTier) {
                    Box(
                        Modifier.offset(y = tops[l] + TopPad).width(GutterW).height(NodeSize),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            Skills.tierLabel(level.tier),
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = ChakraPetch,
                            fontWeight = FontWeight.Bold,
                            color = IronvellumColors.InkMuted,
                        )
                    }
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
                    onClick = { onSelect(skill.name) },
                    modifier = Modifier
                        .offset(x = GutterW + cellW * node.x, y = tops[node.level])
                        .then(if (skill.name == firstNext) Modifier.bringIntoViewRequester(nextRequester) else Modifier),
                )
            }
        }
    }
}

private val LabelLine = 14.sp

private fun levelHeight(level: TreeLevel, textH: Dp): Dp =
    TopPad + NodeSize + TextGap + textH + (if (level.hasCrossNeed) textH else 0.dp) + BusGap

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
    crossNeed: String?,
    width: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = remember(skill.name) { InkCircleShape(skill.name.hashCode() and 0xFF) }
    val textH = with(LocalDensity.current) { (LabelLine * 2f).toDp() }
    // The whole cell is the touch target, so a 32dp node still has a hit area
    // well past 48dp.
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
                    if (state == NodeState.NEXT) 2.dp else 1.5.dp,
                ),
            contentAlignment = Alignment.Center,
        ) {
            // State in a glyph, not the colour alone: check, filled dot, lock.
            when (state) {
                NodeState.MASTERED -> Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = IronvellumColors.Abyss,
                    modifier = Modifier.size(20.dp),
                )
                NodeState.NEXT -> Box(
                    Modifier.size(12.dp).clip(InkCircleShape(3)).background(IronvellumColors.SystemGreen),
                )
                NodeState.LOCKED -> Icon(
                    Icons.Filled.Lock,
                    contentDescription = null,
                    tint = IronvellumColors.InkMuted,
                    modifier = Modifier.size(16.dp),
                )
            }
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
            modifier = Modifier.fillMaxWidth().heightIn(min = textH).padding(horizontal = 2.dp),
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
                modifier = Modifier.fillMaxWidth().height(textH).padding(horizontal = 2.dp),
            )
        }
    }
}
