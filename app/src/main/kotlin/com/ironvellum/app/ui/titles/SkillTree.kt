package com.ironvellum.app.ui.titles

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.geometry.Offset
import com.ironvellum.app.ui.theme.inkBorder
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.ironvellum.app.ui.theme.InkCircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironvellum.app.domain.Skills
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.inkStroke
import com.ironvellum.app.ui.theme.IronvellumColors

private val RailW = 18.dp
private val NodeDot = 12.dp

private data class Row(
    val skill: Skills.SkillDef,
    val depth: Int,
    /**
     * Ancestor rail columns that still have rows pending below this one, each
     * to the children the rail still has to reach below this row. A rail is
     * the path to those children, so it takes the colour of the best of them.
     */
    val openRails: Map<Int, List<String>>,
    /** Column this row's line arrives from; null for a chain root. */
    val fromDepth: Int?,
    val isLastChild: Boolean,
    /** Siblings after this one, still reached through the parent's rail. */
    val laterSiblings: List<String>,
    /** This skill's own children, reached through the line below its dot. */
    val children: List<String>,
)

/**
 * Depth-first rail tree. A single-child step keeps its parent's column so a
 * linear progression reads as one unbroken trunk; only a fork indents. Each
 * row draws the half-segment above its dot and, when it has children, the
 * half below — so consecutive rows join instead of floating.
 */
private fun rows(line: String): List<Row> {
    val skills = Skills.ALL.filter { it.line == line }
    val inLine = skills.map { it.name }.toSet()
    // A technique hangs under its first prerequisite on this path; one whose
    // prerequisites all sit on other paths is a root here.
    val parentOf = skills.associate { it.name to it.prerequisites.firstOrNull { p -> p in inLine } }
    val childrenOf = skills.groupBy { parentOf[it.name] }
    val roots = skills.filter { parentOf[it.name] == null }
        .sortedBy { it.tier }

    val out = mutableListOf<Row>()

    fun walk(
        skill: Skills.SkillDef,
        depth: Int,
        openRails: Map<Int, List<String>>,
        fromDepth: Int?,
        isLast: Boolean,
        laterSiblings: List<String>,
    ) {
        val kids = childrenOf[skill.name].orEmpty().sortedBy { it.tier }
        // a lone successor stays in this column; a fork steps right
        val childDepth = if (kids.size > 1) depth + 1 else depth
        out += Row(
            skill = skill,
            depth = depth,
            openRails = openRails,
            fromDepth = fromDepth,
            isLastChild = isLast,
            laterSiblings = laterSiblings,
            children = kids.map { it.name },
        )
        kids.forEachIndexed { i, kid ->
            val later = kids.drop(i + 1).map { it.name }
            // a fork's rail stays open through each child's subtree until the
            // last child, carrying the children still below
            val rails = if (kids.size > 1 && later.isNotEmpty()) openRails + (depth to later) else openRails - depth
            walk(kid, childDepth, rails, depth, i == kids.lastIndex, later)
        }
    }

    roots.forEachIndexed { i, root ->
        walk(root, 0, emptyMap(), null, i == roots.lastIndex, emptyList())
    }
    return out
}

/** A path's techniques in the order the tree draws them, top to bottom. */
internal fun treeOrder(line: String): List<Skills.SkillDef> = rows(line).map { it.skill }

/** The locked node dot: 3:1 against the page, dimmer than the open green. */
internal val LockedDot = Color(0xFF64625C)

/** The lighter end of a locked row's background, where its text has the least contrast. */
internal val LockedRowBg = Color(0xFF101512)

/**
 * The colour of a stretch of rail, from the skills it leads to: gold when it
 * reaches a mastered skill (a mastered skill's prerequisite is mastered too,
 * so the whole stretch joins mastered to mastered), green when it reaches
 * skills that are open, and ink when everything below it is still locked.
 */
private fun railColor(leadsTo: List<String>, mastered: Set<String>): Color = when {
    leadsTo.any { it in mastered } -> IronvellumColors.SovereignGold.copy(alpha = 0.85f)
    leadsTo.any { name -> Skills.forName(name)?.let { Skills.unlocked(it, mastered) } == true } ->
        IronvellumColors.SystemGreen.copy(alpha = 0.85f)
    else -> IronvellumColors.Rune
}

/** Every colour one row paints, resolved from the mastered set. */
private data class RailPaint(
    val passing: Map<Int, Color>,
    /** The parent's rail from the top of this row down to this row's branch. */
    val intoRow: Color,
    /** The parent's rail from this row's branch on to later siblings. */
    val pastRow: Color,
    /** The line out of this row's dot toward its children. */
    val below: Color,
)

@Composable
fun SkillTreeGraph(
    line: String,
    mastered: Set<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** Best logged effort per technique, for the "best 40/60s" cue on open rows. */
    best: Map<String, SkillGuidance.Effort> = emptyMap(),
    /** The lifter, so a load-bearing standard is judged against their bodyweight. */
    bodyweightKg: Double? = null,
    female: Boolean = false,
) {
    val treeRows = remember(line) { rows(line) }
    // The first open, unmastered technique in drawing order: where the eye
    // should land when a path opens.
    val firstNext = remember(line, mastered) {
        SkillGuidance.frontier(treeRows.map { it.skill }, mastered).firstOrNull()?.name
    }
    val nextRequester = remember { BringIntoViewRequester() }
    // Keyed on the target too: the mastered set can arrive after the first
    // frame, and a claim moves the frontier on.
    LaunchedEffect(line, firstNext) {
        if (firstNext != null) nextRequester.bringIntoView()
    }

    Column(modifier.fillMaxWidth()) {
        treeRows.forEach { row ->
            val isMastered = row.skill.name in mastered
            SkillRow(
                row = row,
                mastered = isMastered,
                unlocked = Skills.unlocked(row.skill, mastered),
                next = SkillGuidance.isFrontier(row.skill, mastered),
                needs = row.skill.firstUnmetPrerequisite(mastered),
                cue = if (isMastered) null else SkillGuidance.progressCue(row.skill, best[row.skill.name], bodyweightKg, female),
                paint = RailPaint(
                    passing = row.openRails.mapValues { (_, below) -> railColor(below, mastered) },
                    intoRow = railColor(listOf(row.skill.name) + row.laterSiblings, mastered),
                    pastRow = railColor(row.laterSiblings, mastered),
                    below = railColor(row.children, mastered),
                ),
                onClick = { onSelect(row.skill.name) },
                modifier = if (row.skill.name == firstNext) Modifier.bringIntoViewRequester(nextRequester) else Modifier,
            )
        }
    }
}

/** What a screen reader hears for one tree row: name, tier, state, then what to do next. */
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
private fun SkillRow(
    row: Row,
    mastered: Boolean,
    unlocked: Boolean,
    next: Boolean,
    needs: String?,
    cue: String?,
    paint: RailPaint,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = when {
        mastered -> IronvellumColors.SovereignGold
        unlocked -> IronvellumColors.SystemGreen
        else -> IronvellumColors.Rune
    }
    val shape = MaterialTheme.shapes.small

    val dotOffset = RailW / 2

    // At least 58dp, taller when a long name or standard wraps; the rail box
    // fills whatever height the row settles on, so the strokes still meet.
    androidx.compose.foundation.layout.Row(
        modifier.fillMaxWidth().heightIn(min = 58.dp).height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Geometry: the dot for column d sits at d*RailW + RailW/2. Each row
        // paints the segment above its dot and, if it has successors, the one
        // below — so neighbouring rows meet exactly at the row boundary.
        Box(Modifier.width(RailW * (row.depth + 1)).fillMaxHeight()) {
            Canvas(Modifier.fillMaxSize()) {
                val rail = RailW.toPx()
                val stroke = 2f.dp.toPx()
                // Each row paints its own half-segments, so consecutive rows
                // meet exactly at the row boundary — where antialiasing left a
                // faint seam. Every segment that runs off the top or bottom of
                // the row extends half a stroke width past it, overlapping the
                // neighbour's end instead of kissing it.
                val over = stroke / 2f
                val top = -over
                val bottom = size.height + over
                val mid = size.height / 2f
                val dotX = row.depth * rail + rail / 2f
                val color = accent.copy(alpha = 0.85f)

                // ancestor rails passing straight through this row; the
                // parent's own column is drawn by the drop-in below
                row.openRails.forEach { (d, _) ->
                    val x = d * rail + rail / 2f
                    if (x != dotX && d != row.fromDepth) {
                        // Ancestor rail: brushed, and never tapered - it runs
                        // through the row rather than starting or ending in it.
                        inkStroke(
                            Offset(x, top),
                            Offset(x, bottom),
                            paint.passing.getValue(d),
                            stroke,
                            seed = d * 17,
                            taperEnds = false,
                        )
                    }
                }

                row.fromDepth?.let { from ->
                    if (from == row.depth) {
                        // same column: a straight trunk segment into the dot
                        inkStroke(Offset(dotX, top), Offset(dotX, mid), color, stroke, seed = row.depth * 7, taperEnds = false)
                    } else {
                        val parentX = from * rail + rail / 2f
                        // parent's column drops in to this row's branch, then
                        // carries on past it when more siblings follow
                        inkStroke(Offset(parentX, top), Offset(parentX, mid), paint.intoRow, stroke, seed = from * 11, taperEnds = false)
                        if (!row.isLastChild) {
                            inkStroke(Offset(parentX, mid), Offset(parentX, bottom), paint.pastRow, stroke, seed = from * 13, taperEnds = false)
                        }
                        inkStroke(Offset(parentX, mid), Offset(dotX, mid), color, stroke, seed = from * 5, taperEnds = false)
                    }
                }

                // hand the line to the row below
                if (row.children.isNotEmpty()) {
                    inkStroke(Offset(dotX, mid), Offset(dotX, bottom), paint.below, stroke, seed = row.depth * 3, taperEnds = false)
                }
            }
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = dotOffset - NodeDot / 2)
                    .size(NodeDot)
                    .clip(InkCircleShape(7))
                    .background(
                        when {
                            mastered -> IronvellumColors.SovereignGold
                            unlocked -> IronvellumColors.SystemGreen
                            else -> LockedDot
                        },
                    ),
            )
        }
        Spacer(Modifier.width(6.dp))

        val description = rowDescription(row.skill, mastered, unlocked, needs, cue)
        androidx.compose.foundation.layout.Row(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(vertical = 4.dp, horizontal = 2.dp)
                .clip(shape)
                .background(
                    when {
                        mastered -> Brush.horizontalGradient(listOf(Color(0xFF3A2A08), Color(0xFF14170F)))
                        unlocked -> Brush.horizontalGradient(listOf(Color(0xFF15251F), Color(0xFF0D1310)))
                        else -> Brush.horizontalGradient(listOf(LockedRowBg, Color(0xFF0C100E)))
                    },
                )
                .inkBorder(accent.copy(alpha = if (unlocked || mastered) 1f else 0.45f), shape, 1.dp)
                .clickable(role = Role.Button, onClickLabel = "Open technique") { onClick() }
                // One sentence for the whole row; the visible texts stay in
                // the merged node so the row is still found by its name.
                .semantics(mergeDescendants = true) { contentDescription = description }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.width(0.dp).weight(1f)) {
                Text(
                    row.skill.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = when {
                        mastered -> IronvellumColors.SovereignGold
                        unlocked -> IronvellumColors.Ink
                        else -> IronvellumColors.InkMuted
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when {
                        mastered -> "MASTERED"
                        unlocked -> row.skill.standard
                        // "LOCKED" said nothing the muted ink and the
                        // prerequisite did not already say, on every locked row.
                        else -> "needs ${needs ?: "its prerequisite"}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    // 9sp was unreadably small; 11sp matches the smallest body
                    // copy anywhere in the app.
                    fontSize = 11.sp,
                    color = if (mastered) IronvellumColors.SystemGreen else IronvellumColors.InkMuted,
                    // Two lines, then an ellipsis: one line cut most standards
                    // off before the part that says how to clear them.
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (cue != null && unlocked) {
                    Text(
                        cue,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 11.sp,
                        fontFamily = ChakraPetch,
                        color = IronvellumColors.SystemGreen,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                // State in a glyph and a word, not the colour alone.
                androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                    when {
                        mastered -> Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = IronvellumColors.SovereignGold,
                            modifier = Modifier.size(14.dp),
                        )
                        !unlocked -> Icon(
                            Icons.Filled.Lock,
                            contentDescription = null,
                            tint = IronvellumColors.InkMuted,
                            modifier = Modifier.size(14.dp),
                        )
                        else -> Unit
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        Skills.tierLabel(row.skill.tier),
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = ChakraPetch,
                        fontWeight = FontWeight.Bold,
                        // Rune ink on a locked row read at about 1.4:1.
                        color = if (mastered || unlocked) accent else IronvellumColors.InkMuted,
                        letterSpacing = 1.sp,
                    )
                }
                if (next) {
                    Text(
                        "NEXT",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 11.sp,
                        fontFamily = ChakraPetch,
                        fontWeight = FontWeight.Bold,
                        color = IronvellumColors.SystemGreen,
                        letterSpacing = 1.sp,
                        maxLines = 1,
                    )
                }
                if (row.children.size > 1) {
                    Text(
                        "opens ${row.children.size}",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 11.sp,
                        color = IronvellumColors.InkMuted,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
