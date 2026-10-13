package com.ironvellum.app.ui.program

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import com.ironvellum.app.ui.components.InkDivider
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.components.ListRowHeight
import com.ironvellum.app.ui.components.selectedUnderline
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.IronvellumTracking

/*
 * Optimise rites, stateless: the radio rows, the change cards with their Keep / Skip, the impact list. Nothing
 * here owns a decision; the screen passes in what is true and hears what was chosen, so every state can be put
 * on screen directly. Every state is a word as well as a mark, and every control is at least 48dp.
 */

/** The radio mark: a ring, filled when chosen. Decorative; the row says its state in words. Path-only. */
@Composable
internal fun RadioMark(selected: Boolean, modifier: Modifier = Modifier, size: Dp = 20.dp) {
    val color = if (selected) IronvellumColors.Emerald else IronvellumColors.InkMuted
    Canvas(modifier.size(size).clearAndSetSemantics {}) {
        val w = this.size.minDimension
        val stroke = w * 0.1f
        val r = (w - stroke) / 2f
        val c = Offset(w / 2f, w / 2f)
        drawPath(Path().apply { addOval(Rect(c, r)) }, color, style = Stroke(stroke))
        if (selected) drawPath(Path().apply { addOval(Rect(c, r * 0.5f)) }, color)
    }
}

/** A small caps tag with no box and no tap: "BEST MATCH". */
@Composable
internal fun OptTag(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontFamily = ChakraPetch,
        fontSize = 10.sp,
        letterSpacing = IronvellumTracking.InlineLabel,
        color = IronvellumColors.SystemGreen,
        modifier = Modifier.clearAndSetSemantics {},
    )
}

/**
 * One ranked movement as a radio row: its number, name, a "Best match" tag on the first, one line of why and,
 * when given, what it would be prescribed. The chosen row carries the thin Emerald left bar and faint tint of
 * every active row, and a filled ring; it reads "chosen" as its state description.
 */
@Composable
internal fun RankedOptionRow(
    rank: Int,
    count: Int,
    name: String,
    reason: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    line: String? = null,
) {
    val best = rank == 1
    val spoken = buildString {
        append("Option $rank of $count: $name")
        if (best) append(", best match")
        if (reason.isNotBlank()) append(". $reason")
    }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .background(if (selected) IronvellumColors.Ink.copy(alpha = 0.05f) else Color.Transparent)
            .drawBehind {
                if (selected) drawRect(IronvellumColors.Emerald, Offset.Zero, Size(2.dp.toPx(), size.height))
            }
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .semantics(mergeDescendants = true) {
                contentDescription = spoken
                stateDescription = if (selected) "Chosen" else "Not chosen"
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "$rank",
            style = MaterialTheme.typography.labelMedium,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.SemiBold,
            color = IronvellumColors.InkMuted,
            modifier = Modifier.width(14.dp),
        )
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = IronvellumColors.Ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (best) OptTag("Best match")
            }
            if (reason.isNotBlank()) {
                Text(reason, style = MaterialTheme.typography.labelSmall, color = IronvellumColors.InkMuted)
            }
            if (line != null) {
                Text(line, style = MaterialTheme.typography.labelSmall, fontFamily = ChakraPetch, color = IronvellumColors.SystemGreen)
            }
        }
        RadioMark(selected)
    }
}

/** A radio group of [RankedOptionRow]s, announced as one group named [label]. */
@Composable
internal fun RankedOptions(
    label: String,
    names: List<String>,
    reasons: List<String>,
    lines: List<String?>,
    chosen: Int,
    onChoose: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    InkPanel(modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
        Column(Modifier.fillMaxWidth().selectableGroup().semantics { contentDescription = label }) {
            names.forEachIndexed { index, name ->
                if (index > 0) InkDivider()
                RankedOptionRow(
                    rank = index + 1,
                    count = names.size,
                    name = name,
                    reason = reasons.getOrElse(index) { "" },
                    line = lines.getOrNull(index),
                    selected = chosen == index,
                    onSelect = { onChoose(index) },
                )
            }
        }
    }
}

/**
 * Keep or Skip for one change, a two-way radio group on a rule: the chosen half is Ink on a faint wash with an
 * Emerald underline and a mark (a check for Keep, a cross for Skip), the other InkMuted. 48dp tall, and each
 * half says which change it is for.
 */
@Composable
internal fun KeepSkip(number: Int, keep: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .selectableGroup()
            .semantics { contentDescription = "Change $number: keep or skip" }
            .drawBehind { drawRect(IronvellumColors.Rune, Offset.Zero, Size(size.width, 1.dp.toPx())) },
    ) {
        for (value in listOf(true, false)) {
            val on = keep == value
            val label = if (value) "Keep" else "Skip"
            Row(
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .background(if (on) IronvellumColors.Ink.copy(alpha = 0.05f) else Color.Transparent)
                    .selectedUnderline(on)
                    .selectable(selected = on, role = Role.RadioButton, onClick = { onChange(value) })
                    .semantics { contentDescription = "$label change $number" },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    if (value) Icons.Filled.Check else Icons.Filled.Close,
                    contentDescription = null,
                    tint = if (on && value) IronvellumColors.Emerald else IronvellumColors.InkMuted,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    label,
                    fontFamily = ChakraPetch,
                    fontSize = 13.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (on) IronvellumColors.Ink else IronvellumColors.InkMuted,
                )
            }
        }
    }
}

/**
 * One suggested change as a card: its kind in caps, "Best match" and the option number when it is a ranked
 * pick, the one-line title, why, and a chevron into the detail. Under it, "N more options" when there are
 * any, then Keep / Skip.
 */
@Composable
internal fun ChangeCard(
    number: Int,
    change: OptChange,
    keep: Boolean,
    chosen: Int,
    onOpen: () -> Unit,
    onKeep: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val option = change.options.getOrElse(chosen) { change.options.first() }
    val more = change.options.size - 1
    InkPanel(modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp)
                    .clickable(
                        onClickLabel = "Open change $number",
                        role = Role.Button,
                        onClick = onOpen,
                    )
                    .semantics(mergeDescendants = true) {
                        contentDescription = "Open change $number: ${change.kind.label}, ${titleFor(change, option)}"
                    }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            change.kind.label.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = ChakraPetch,
                            fontSize = 10.sp,
                            letterSpacing = IronvellumTracking.InlineLabel,
                            color = IronvellumColors.SystemGreen,
                        )
                        if (change.options.size > 1 && chosen == 0) OptTag("Best match")
                        if (change.options.size > 1 && chosen > 0) OptTag("Option ${chosen + 1}")
                        if (!keep) OptTag("Skipped")
                    }
                    Text(
                        titleFor(change, option),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = IronvellumColors.Ink,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Text(change.detail, style = MaterialTheme.typography.labelSmall, color = IronvellumColors.InkMuted)
                }
                Text(
                    "›",
                    style = MaterialTheme.typography.titleMedium,
                    color = IronvellumColors.InkMuted,
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }
            if (more > 0) {
                InkDivider()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable(
                            onClickLabel = "Show $more more options for change $number",
                            role = Role.Button,
                            onClick = onOpen,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "$more more ${if (more == 1) "option" else "options"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = IronvellumColors.SystemGreen,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "Ranked ${(2..change.options.size).joinToString(" and ")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = IronvellumColors.InkMuted,
                    )
                }
            }
        }
        KeepSkip(number, keep, onKeep)
    }
}

/** The title a card shows for the chosen option: a swap names its pick, the others keep their own wording. */
internal fun titleFor(change: OptChange, option: OptOption): String =
    if (change.kind == OptKind.SWAP && change.title.contains(" → ")) {
        change.title.substringBefore(" → ") + " → " + option.label
    } else {
        change.title
    }

/**
 * What a change does to the week, one row per muscle that moves: a mark for the state it ends in, the muscle,
 * and "Covered, 6 → Below minimum, 3 hard sets" as words. The list is a live region so the effect is read out
 * when the lifter picks another option.
 */
@Composable
internal fun ImpactList(rows: List<ImpactRow>, modifier: Modifier = Modifier, emptyLine: String = "No muscle changes coverage.") {
    Column(modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
        if (rows.isEmpty()) {
            Text(emptyLine, style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted, modifier = Modifier.padding(vertical = 8.dp))
        }
        rows.forEachIndexed { index, row ->
            if (index > 0) InkDivider()
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = ListRowHeight)
                    .semantics(mergeDescendants = true) { contentDescription = row.spoken }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CoverageMark(row.afterState)
                Column(Modifier.weight(1f)) {
                    Text(row.muscle.label, style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.Ink)
                    Text(row.text, style = MaterialTheme.typography.labelSmall, color = IronvellumColors.InkMuted)
                }
            }
        }
    }
}

/** A heading for a block of the page, announced as one. */
@Composable
internal fun OptLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = IronvellumColors.InkMuted,
        modifier = modifier.padding(top = 18.dp, bottom = 8.dp).semantics { heading() },
    )
}

/**
 * One finding on the hub: a state mark, the title, a subline in words and a chevron. The whole row is one
 * 52dp-plus target, announced with its state.
 */
@Composable
internal fun FindingRow(
    state: CoverageState?,
    title: String,
    subline: String,
    onClickLabel: String,
    spoken: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = spoken
                if (state != null) stateDescription = state.label
            }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state != null) CoverageMark(state)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.Ink)
            Text(subline, style = MaterialTheme.typography.labelSmall, color = IronvellumColors.InkMuted)
        }
        Text(
            "›",
            style = MaterialTheme.typography.titleMedium,
            color = IronvellumColors.InkMuted,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

/** A quiet box of muted words: what a page cannot do, or nothing is wrong. */
@Composable
internal fun OptNote(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted, modifier = modifier.padding(vertical = 4.dp))
}
