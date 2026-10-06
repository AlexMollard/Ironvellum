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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironvellum.app.domain.Skills
import com.ironvellum.app.ui.components.NavChip
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.inkBorder

/** What a path tile reads out loud: name, progress, then where to go next. */
internal fun tileDescription(line: String, done: Int, total: Int, status: String, recent: Boolean): String =
    buildList {
        add("$line path")
        add("$done of $total mastered")
        add(status)
        if (recent) add("most recent")
    }.joinToString(", ")

/**
 * The line under a path tile: "complete" only when every technique is
 * mastered. An open technique reads "next: ..."; a path whose remaining
 * techniques all wait on another path says which one, rather than "complete".
 */
internal fun pathStatus(done: Int, total: Int, next: String?, blocker: CrossNeed?): String = when {
    done >= total -> "complete"
    next != null -> "next: $next"
    blocker != null -> "locked: needs ${blocker.skill} (${blocker.line})"
    else -> "locked"
}

/** [pathStatus] for a path as the tile reads it, from the graph's own reading order. */
internal fun pathStatusOf(line: String, mastered: Set<String>): String = pathReading(line, mastered).first

/**
 * The tile's status line and, when the path is stuck waiting on another one,
 * the technique it waits for, so the tile can offer to jump there.
 */
private fun pathReading(line: String, mastered: Set<String>): Pair<String, CrossNeed?> {
    val (done, total) = SkillGuidance.lineProgress(line, mastered)
    val layout = treeLayout(line, columns = 4)
    val next = layout.firstNext(mastered)?.name
    val blocker = layout.firstBlocker(mastered)
    val status = pathStatus(done, total, next, blocker)
    return status to blocker.takeIf { done < total && next == null }
}

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
    /** A path stuck on another path's technique: jump to that technique. */
    onOpenNeed: (CrossNeed) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The status per path, in the order the graph reads. Four slots is the
    // phone layout; a wider one only re-orders within a row.
    val reading = remember(mastered) { Skills.LINES.associateWith { pathReading(it, mastered) } }
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
                        status = reading.getValue(line).first,
                        recent = line == recentLine,
                        blocker = reading.getValue(line).second,
                        onOpenBlocker = onOpenNeed,
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
    status: String,
    recent: Boolean,
    blocker: CrossNeed?,
    onOpenBlocker: (CrossNeed) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.medium
    val complete = done >= total
    // The tile is one button; a blocked path's "needs ..." line is its own, so the
    // line sits outside the tile's merged semantics and keeps its own click.
    Column(
        modifier
            .heightIn(min = 96.dp)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF17201C), Color(0xFF111815))))
            .inkBorder(if (recent) IronvellumColors.SovereignGold else IronvellumColors.Bracket, shape, if (recent) 1.5.dp else 1.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = "Open path") { onClick() }
            .semantics(mergeDescendants = true) { contentDescription = tileDescription(line, done, total, status, recent) }
            .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = if (blocker != null) 0.dp else 10.dp),
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
        if (blocker == null) {
            Text(
                status,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp,
                color = if (complete) IronvellumColors.SovereignGold else IronvellumColors.InkMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
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
    if (blocker != null) {
        Text(
            status,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 11.sp,
            color = IronvellumColors.SystemGreen,
            textDecoration = TextDecoration.Underline,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClickLabel = "Open ${blocker.skill}") { onOpenBlocker(blocker) }
                .padding(horizontal = 12.dp)
                .wrapContentHeight(Alignment.CenterVertically),
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
        NavChip("BACK", Icons.AutoMirrored.Filled.ArrowBack, onClick = onBack)
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
