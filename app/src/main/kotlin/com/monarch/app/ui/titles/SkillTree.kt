package com.monarch.app.ui.titles

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monarch.app.domain.Skills
import com.monarch.app.ui.theme.ChakraPetch
import com.monarch.app.ui.theme.MonarchColors

private val RailW = 18.dp
private val NodeDot = 12.dp

private data class Row(
    val skill: Skills.SkillDef,
    val depth: Int,
    /** Ancestor rail columns that still have rows pending below this one. */
    val openRails: Set<Int>,
    /** Column this row's line arrives from; null for a chain root. */
    val fromDepth: Int?,
    val isLastChild: Boolean,
    /** True when a line must continue downward out of this row's dot. */
    val continuesBelow: Boolean,
    val branches: Int,
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
    val childrenOf = skills.groupBy { it.requires }
    val roots = skills.filter { it.requires == null || it.requires !in inLine }
        .sortedBy { it.tier }

    val out = mutableListOf<Row>()

    fun walk(
        skill: Skills.SkillDef,
        depth: Int,
        openRails: Set<Int>,
        fromDepth: Int?,
        isLast: Boolean,
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
            continuesBelow = kids.isNotEmpty(),
            branches = kids.size,
        )
        val nextOpen = if (kids.size > 1) openRails + depth else openRails
        kids.forEachIndexed { i, kid ->
            val lastKid = i == kids.lastIndex
            walk(
                kid,
                childDepth,
                if (lastKid) nextOpen - depth else nextOpen,
                depth,
                lastKid,
            )
        }
    }

    roots.forEachIndexed { i, root ->
        walk(root, 0, emptySet(), null, i == roots.lastIndex)
    }
    return out
}

@Composable
fun SkillTreeGraph(
    line: String,
    mastered: Set<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val treeRows = remember(line) { rows(line) }

    Column(modifier.fillMaxWidth()) {
        treeRows.forEach { row ->
            SkillRow(
                row = row,
                mastered = row.skill.name in mastered,
                unlocked = Skills.unlocked(row.skill, mastered),
                onClick = { onSelect(row.skill.name) },
            )
        }
    }
}

@Composable
private fun SkillRow(
    row: Row,
    mastered: Boolean,
    unlocked: Boolean,
    onClick: () -> Unit,
) {
    val accent = when {
        mastered -> MonarchColors.SovereignGold
        unlocked -> MonarchColors.SystemGreen
        else -> MonarchColors.Rune
    }
    val shape = CutCornerShape(topStart = 7.dp, bottomEnd = 7.dp)

    val rowH = 58.dp
    val dotOffset = RailW / 2

    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().height(rowH),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Geometry: the dot for column d sits at d*RailW + RailW/2. Each row
        // paints the segment above its dot and, if it has successors, the one
        // below — so neighbouring rows meet exactly at the row boundary.
        Box(Modifier.width(RailW * (row.depth + 1)).height(rowH)) {
            Canvas(Modifier.fillMaxSize()) {
                val rail = RailW.toPx()
                val stroke = 2f.dp.toPx()
                val mid = size.height / 2f
                val dotX = row.depth * rail + rail / 2f
                val color = accent.copy(alpha = 0.85f)

                // ancestor rails passing straight through this row
                row.openRails.forEach { d ->
                    val x = d * rail + rail / 2f
                    if (x != dotX) {
                        drawLine(
                            MonarchColors.Rune,
                            Offset(x, 0f),
                            Offset(x, size.height),
                            strokeWidth = stroke,
                        )
                    }
                }

                row.fromDepth?.let { from ->
                    if (from == row.depth) {
                        // same column: a straight trunk segment into the dot
                        drawLine(color, Offset(dotX, 0f), Offset(dotX, mid), strokeWidth = stroke)
                    } else {
                        val parentX = from * rail + rail / 2f
                        // parent's column drops in, continuing past this row
                        // when more siblings follow
                        drawLine(
                            color,
                            Offset(parentX, 0f),
                            Offset(parentX, if (row.isLastChild) mid else size.height),
                            strokeWidth = stroke,
                        )
                        drawLine(color, Offset(parentX, mid), Offset(dotX, mid), strokeWidth = stroke)
                    }
                }

                // hand the line to the row below
                if (row.continuesBelow) {
                    drawLine(color, Offset(dotX, mid), Offset(dotX, size.height), strokeWidth = stroke)
                }
            }
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = dotOffset - NodeDot / 2)
                    .size(NodeDot)
                    .clip(CircleShape)
                    .background(
                        when {
                            mastered -> MonarchColors.SovereignGold
                            unlocked -> MonarchColors.SystemGreen
                            else -> Color(0xFF243029)
                        },
                    ),
            )
        }
        Spacer(Modifier.width(6.dp))

        androidx.compose.foundation.layout.Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp, horizontal = 2.dp)
                .clip(shape)
                .background(
                    when {
                        mastered -> Brush.horizontalGradient(listOf(Color(0xFF3A2A08), Color(0xFF14170F)))
                        unlocked -> Brush.horizontalGradient(listOf(Color(0xFF15251F), Color(0xFF0D1310)))
                        else -> Brush.horizontalGradient(listOf(Color(0xFF101512), Color(0xFF0C100E)))
                    },
                )
                .border(1.dp, accent.copy(alpha = if (unlocked || mastered) 1f else 0.45f), shape)
                .clickable { onClick() }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.width(0.dp).weight(1f)) {
                Text(
                    row.skill.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = when {
                        mastered -> MonarchColors.SovereignGold
                        unlocked -> MonarchColors.Ink
                        else -> MonarchColors.InkMuted
                    },
                    maxLines = 1,
                )
                Text(
                    when {
                        mastered -> "MASTERED"
                        unlocked -> row.skill.standard
                        else -> "LOCKED · needs ${row.skill.requires}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    color = if (mastered) MonarchColors.SystemGreen else MonarchColors.InkMuted,
                    maxLines = 1,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    Skills.tierLabel(row.skill.tier),
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = ChakraPetch,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                    letterSpacing = 1.sp,
                )
                if (row.branches > 1) {
                    Text(
                        "${row.branches} paths",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 8.sp,
                        color = MonarchColors.InkMuted,
                    )
                }
            }
        }
    }
}
