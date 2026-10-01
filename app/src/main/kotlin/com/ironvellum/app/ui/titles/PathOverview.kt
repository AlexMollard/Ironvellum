package com.ironvellum.app.ui.titles

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironvellum.app.domain.Skills
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.inkBorder

/** What a path tile reads out loud: name, progress, then where to go next. */
internal fun tileDescription(line: String, done: Int, total: Int, next: String?, recent: Boolean): String =
    buildList {
        add("$line path")
        add("$done of $total mastered")
        add(if (next != null) "next: $next" else "complete")
        if (recent) add("most recent")
    }.joinToString(", ")

/**
 * Every path as a tile, two across: name, progress and the technique to go
 * for next. The path of the most recent attempt or claim is marked, not
 * opened - the lifter chooses where to go.
 */
@Composable
internal fun PathGrid(
    mastered: Set<String>,
    recentLine: String?,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The next technique per path, in the order the graph reads. Four slots is
    // the phone layout; a wider one only re-orders within a row.
    val next = remember(mastered) {
        Skills.LINES.associateWith { treeLayout(it, columns = 4).firstNext(mastered)?.name }
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Skills.LINES.chunked(2).forEach { pair ->
            // fillMaxHeight inside an intrinsic-height row would need the same
            // trick as everywhere else; a fixed minimum keeps the pair level.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { line ->
                    val (done, total) = SkillGuidance.lineProgress(line, mastered)
                    PathTile(
                        line = line,
                        done = done,
                        total = total,
                        next = next[line],
                        recent = line == recentLine,
                        onClick = { onOpen(line) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PathTile(
    line: String,
    done: Int,
    total: Int,
    next: String?,
    recent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.small
    val complete = done >= total
    Column(
        modifier
            .heightIn(min = 96.dp)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF17201C), Color(0xFF111815))))
            .inkBorder(if (recent) IronvellumColors.SovereignGold else IronvellumColors.Bracket, shape, if (recent) 1.5.dp else 1.dp)
            .clickable(role = Role.Button, onClickLabel = "Open path") { onClick() }
            .semantics(mergeDescendants = true) { contentDescription = tileDescription(line, done, total, next, recent) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                line,
                style = MaterialTheme.typography.labelLarge,
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.Bold,
                color = if (recent) IronvellumColors.SovereignGold else IronvellumColors.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "$done/$total",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = ChakraPetch,
                color = IronvellumColors.Ink,
            )
        }
        Spacer(Modifier.height(6.dp))
        ProgressBar(done, total, Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        Text(
            when {
                complete || next == null -> "complete"
                else -> "next: $next"
            },
            style = MaterialTheme.typography.labelSmall,
            fontSize = 11.sp,
            color = if (complete) IronvellumColors.SovereignGold else IronvellumColors.InkMuted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (recent) {
            Text(
                "RECENT",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp,
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.Bold,
                color = IronvellumColors.SovereignGold,
                letterSpacing = 1.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ProgressBar(done: Int, total: Int, modifier: Modifier = Modifier) {
    Box(modifier.height(4.dp).background(IronvellumColors.Rune)) {
        if (done > 0 && total > 0) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(done.toFloat() / total)
                    .background(IronvellumColors.SovereignGold),
            )
        }
    }
}

/** The header over an opened path: back to the grid, the path's name and its progress. */
@Composable
internal fun PathHeader(line: String, mastered: Set<String>, onBack: () -> Unit) {
    val (done, total) = remember(line, mastered) { SkillGuidance.lineProgress(line, mastered) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .clip(MaterialTheme.shapes.small)
                .clickable(role = Role.Button, onClickLabel = "Back to the paths") { onBack() }
                .padding(end = 12.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = IronvellumColors.SovereignGold,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Text(
                "BACK",
                style = MaterialTheme.typography.labelLarge,
                fontFamily = ChakraPetch,
                color = IronvellumColors.SovereignGold,
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            line,
            style = MaterialTheme.typography.labelLarge,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = IronvellumColors.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "$done/$total",
            style = MaterialTheme.typography.labelMedium,
            fontFamily = ChakraPetch,
            color = IronvellumColors.SystemGreen,
            modifier = Modifier.semantics { contentDescription = "$done of $total mastered" },
        )
    }
}
