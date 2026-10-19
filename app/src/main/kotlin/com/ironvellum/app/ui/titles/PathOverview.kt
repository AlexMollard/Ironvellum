package com.ironvellum.app.ui.titles

import com.ironvellum.app.R
import com.ironvellum.app.data.Words
import com.ironvellum.app.ui.components.PushedHeader
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ironvellum.app.domain.Skills
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.ui.asString
import com.ironvellum.app.ui.components.InkDivider
import com.ironvellum.app.ui.components.InkRail
import com.ironvellum.app.ui.components.InkRowPanel
import com.ironvellum.app.ui.components.ListRow
import com.ironvellum.app.ui.theme.IronvellumColors

/** What a path tile reads out loud: name, progress, then where to go next. */
internal fun tileDescription(line: String, done: Int, total: Int, status: String, recent: Boolean): String =
    buildList {
        add(Words.text(UiText.themed(R.string.path_tile_name, R.string.path_tile_name__t, line)))
        add(Words.text(UiText.plural(R.plurals.path_mastered_of_total, total, done, total)))
        add(status)
        if (recent) add(Words.text(UiText.res(R.string.path_tile_recent_description)))
    }.joinToString(", ")

/**
 * The line under a path tile: "complete" only when every technique is
 * mastered. An open technique reads "next: ..."; a path whose remaining
 * techniques all wait on another path says which one, rather than "complete".
 */
internal fun pathStatus(done: Int, total: Int, next: String?, blocker: CrossNeed?): String = when {
    done >= total -> Words.text(UiText.res(R.string.path_status_complete))
    next != null -> Words.text(UiText.res(R.string.path_status_next, next))
    blocker != null -> Words.text(UiText.res(R.string.path_status_locked_needs, blocker.skill, blocker.line))
    else -> Words.text(UiText.res(R.string.path_status_locked))
}

/** [pathStatus] for a path as the tile reads it, from the graph's own reading order. */
internal fun pathStatusOf(line: String, mastered: Set<String>): String = pathReading(line, mastered).status

/** A path tile's status line, the technique it waits for on another path, and whether a technique is open to go for next. */
private class PathReading(val status: String, val blocker: CrossNeed?, val nextUp: Boolean)

/**
 * The tile's status line and, when the path is stuck waiting on another one,
 * the technique it waits for, so the tile can offer to jump there.
 */
private fun pathReading(line: String, mastered: Set<String>): PathReading {
    val (done, total) = SkillGuidance.lineProgress(line, mastered)
    val layout = treeLayout(line, columns = 4)
    val next = layout.firstNext(mastered)?.name
    val blocker = layout.firstBlocker(mastered)
    val status = pathStatus(done, total, next, blocker)
    return PathReading(status, blocker.takeIf { done < total && next == null }, next != null)
}

/**
 * Every path as one row of one card: name, how many techniques are mastered and the one to go
 * for next, a thin progress rail. The path of the most recent attempt or claim carries a quiet
 * "Recent" tag; it is marked, not opened - the lifter chooses where to go.
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
    InkRowPanel(modifier.fillMaxWidth()) {
        Skills.LINES.forEachIndexed { i, line ->
            if (i > 0) InkDivider()
            val (done, total) = SkillGuidance.lineProgress(line, mastered)
            PathRow(
                line = line,
                done = done,
                total = total,
                status = reading.getValue(line).status,
                nextUp = reading.getValue(line).nextUp,
                recent = line == recentLine,
                blocker = reading.getValue(line).blocker,
                onOpenBlocker = onOpenNeed,
                onClick = { onOpen(line) },
            )
        }
    }
}

@Composable
private fun PathRow(
    line: String,
    done: Int,
    total: Int,
    status: String,
    nextUp: Boolean,
    recent: Boolean,
    blocker: CrossNeed?,
    onOpenBlocker: (CrossNeed) -> Unit,
    onClick: () -> Unit,
) {
    // The row is one button; a blocked path's "needs ..." line is its own, so the
    // line sits outside the row's merged semantics and keeps its own click.
    Column(Modifier.fillMaxWidth()) {
        Column {
            ListRow(
                label = line,
                subline = pathSubline(done, total, status, blocker, nextUp),
                value = if (recent) UiText.res(R.string.path_recent_tag).asString() else null,
                onClickLabel = UiText.themed(R.string.path_open_description, R.string.path_open_description__t).asString(),
                onClick = onClick,
                modifier = Modifier.semantics(mergeDescendants = true) {
                    contentDescription = tileDescription(line, done, total, status, recent)
                },
            )
            InkRail(
                fraction = if (total == 0) 0f else done.toFloat() / total,
                height = 3.dp,
                // Emerald while in progress; gold only once every technique is mastered.
                fill = SolidColor(if (total > 0 && done >= total) IronvellumColors.SovereignGold else IronvellumColors.Emerald),
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = if (blocker != null) 0.dp else 10.dp),
            )
        }
        if (blocker != null) {
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.SystemGreen,
                textDecoration = TextDecoration.Underline,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button, onClickLabel = UiText.res(R.string.titles_open_description, blocker.skill).asString()) { onOpenBlocker(blocker) }
                    .padding(horizontal = 16.dp)
                    .wrapContentHeight(Alignment.CenterVertically),
            )
        }
    }
}

/** "5 of 11 mastered", then the technique to go for next; a path waiting on another says so on its own link line. */
private fun pathSubline(done: Int, total: Int, status: String, blocker: CrossNeed?, nextUp: Boolean): String =
    if (done < total && blocker == null && nextUp) Words.text(UiText.plural(R.plurals.path_subline_next, total, done, total, status))
    else Words.text(UiText.plural(R.plurals.path_mastered_of_total, total, done, total))

/** The header over an opened path: back to the list, the path's name, and its progress once as a subline. */
@Composable
internal fun PathHeader(line: String, mastered: Set<String>, onBack: () -> Unit) {
    val (done, total) = remember(line, mastered) { SkillGuidance.lineProgress(line, mastered) }
    Column {
        PushedHeader(line, onBack)
        Text(
            UiText.plural(R.plurals.path_mastered_of_total, total, done, total).asString(),
            style = MaterialTheme.typography.bodySmall,
            color = IronvellumColors.InkMuted,
        )
    }
}
