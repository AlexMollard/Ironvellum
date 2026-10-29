package com.ironvellum.app.ui.program

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironvellum.app.R
import com.ironvellum.app.data.Words
import com.ironvellum.app.domain.DayNames
import com.ironvellum.app.domain.ListText
import com.ironvellum.app.domain.PlannedPreset
import com.ironvellum.app.domain.RoutinePlan
import com.ironvellum.app.domain.TrainingSplit
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.ui.asString
import com.ironvellum.app.ui.components.InkSpinner
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.components.IronvellumDialog
import com.ironvellum.app.ui.components.NavChip
import com.ironvellum.app.ui.components.selectedUnderline
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.IronvellumTracking
import com.ironvellum.app.ui.theme.inkBorder

/*
 * The Forge's stateless furniture: the header, the step tabs, the radio rows, the dock and the sheets. Nothing
 * here owns a decision; the screen passes in what is true and hears what was chosen, so every state (failed,
 * saving, days taken, discard) can be put on screen directly.
 */

/**
 * Title left, BACK chip right, with an optional [subtitle] under the title and a [status] word beside it
 * ("Failed", "Saving"): the state is said, never only coloured.
 */
@Composable
internal fun ForgeHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    status: String? = null,
) {
    Row(modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    color = IronvellumColors.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).semantics { heading() },
                )
                if (status != null) {
                    val shape = MaterialTheme.shapes.extraSmall
                    Text(
                        status,
                        style = MaterialTheme.typography.labelSmall,
                        color = IronvellumColors.InkMuted,
                        modifier = Modifier
                            .clip(shape)
                            .inkBorder(IronvellumColors.InkMuted, shape, 1.dp)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = IronvellumColors.InkMuted)
            }
        }
        NavChip(stringResource(R.string.forge_back_button), Icons.AutoMirrored.Filled.ArrowBack, onClick = onBack)
    }
}

/**
 * The three steps as tabs. A step already walked reads "✓ Goal", the rest "2 Kit"; the tab says in words which
 * step it is and whether it is done, so the mark and the colour are only a second channel.
 */
@Composable
internal fun ForgeStepTabs(step: Int, labels: List<String>, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind { drawRect(IronvellumColors.Rune, Offset(0f, size.height - 1.dp.toPx()), Size(size.width, 1.dp.toPx())) }
            .selectableGroup(),
    ) {
        labels.forEachIndexed { index, label ->
            val on = index == step
            val done = index < step
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .selectedUnderline(on)
                    .selectable(selected = on, role = Role.Tab, onClick = { onSelect(index) })
                    .semantics {
                        contentDescription = label
                        stateDescription = Words.text(UiText.res(if (done) R.string.forge_step_of_done else R.string.forge_step_of, index + 1, labels.size))
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = IronvellumColors.SystemGreen)) { append(if (done) "✓ " else "${index + 1} ") }
                        append(label)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = ChakraPetch,
                    fontSize = 14.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (on) IronvellumColors.Ink else IronvellumColors.InkMuted,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

/** A small muted question label above its control. */
@Composable
internal fun ForgeLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = IronvellumColors.InkMuted,
        modifier = modifier.padding(top = 14.dp, bottom = 6.dp).semantics { heading() },
    )
}

@Composable
internal fun ForgeCaption(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = IronvellumColors.InkMuted, modifier = modifier.padding(top = 6.dp))
}

/**
 * One choice from a short row, a radio group: the chosen option is Ink with an Emerald underline and a faint
 * wash, the rest muted. Each option is a 48dp target announced as a radio button with its selected state.
 */
@Composable
internal fun <T> ForgeRadioRow(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    onPick: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .selectableGroup()
            .semantics { contentDescription = label }
            .drawBehind { drawRect(IronvellumColors.Rune, Offset(0f, size.height - 1.dp.toPx()), Size(size.width, 1.dp.toPx())) },
    ) {
        options.forEach { (value, text) ->
            val on = value == selected
            Box(
                Modifier
                    // A four-way row gives a longer label more of the width, so "Upper / lower" stays on one line.
                    .weight(if (options.size > 3) text.length + 4f else 1f)
                    .heightIn(min = 48.dp)
                    .fillMaxHeight()
                    .background(if (on) IronvellumColors.Ink.copy(alpha = 0.05f) else androidx.compose.ui.graphics.Color.Transparent)
                    .selectedUnderline(on)
                    .selectable(selected = on, role = Role.RadioButton, onClick = { onPick(value) })
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text,
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = ChakraPetch,
                    fontSize = 13.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (on) IronvellumColors.Ink else IronvellumColors.InkMuted,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * The bar docked under every Forge page: the primary action, then a muted [note] under it. [busy] swaps the
 * label for [busyLabel] with a spinner and takes the button away from the finger; a disabled button is
 * announced disabled. [link] is a quiet second action (replace the cycle).
 */
@Composable
internal fun ForgeDock(
    primary: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
    busyLabel: String = stringResource(R.string.forge_dock_busy_label),
    note: String? = null,
    link: String? = null,
    linkDanger: Boolean = false,
    onLink: () -> Unit = {},
    above: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val live = enabled && !busy
    val applying = stringResource(R.string.forge_dock_busy_description)
    Column(
        modifier
            .fillMaxWidth()
            .background(IronvellumColors.VaultHigh)
            .drawBehind { drawRect(IronvellumColors.Rune, size = Size(size.width, 1.dp.toPx())) }
            .navigationBarsPadding()
            .padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 14.dp),
    ) {
        above?.invoke(this)
        Box(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(if (live) IronvellumColors.Emerald else IronvellumColors.Rune)
                .clickable(enabled = live, role = Role.Button, onClick = onPrimary)
                .semantics { if (busy) stateDescription = applying },
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // No spinner: the ellipsis says it is working, and an endless animation would never let a test settle.
                Text(
                    if (busy) busyLabel else primary,
                    fontFamily = ChakraPetch,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = if (live) MaterialTheme.colorScheme.onPrimary else IronvellumColors.InkMuted,
                )
            }
        }
        if (note != null) {
            Text(
                note,
                style = MaterialTheme.typography.labelSmall,
                color = IronvellumColors.InkMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
        if (link != null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(enabled = !busy, role = Role.Button, onClick = onLink),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    link,
                    fontFamily = ChakraPetch,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = if (busy) IronvellumColors.InkMuted else if (linkDanger) IronvellumColors.DangerRed else IronvellumColors.SystemGreen,
                )
            }
        }
    }
}

/** The failed page's card: what failed, why it is safe, and the way to try again. */
@Composable
internal fun ForgeFailedCard(message: String, onTryAgain: () -> Unit, modifier: Modifier = Modifier) {
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .inkBorder(IronvellumColors.DangerRed, shape, 1.dp)
            .padding(16.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        Text(
            UiText.themed(R.string.forge_failed_title, R.string.forge_failed_title__t).asString(),
            style = MaterialTheme.typography.titleMedium,
            color = IronvellumColors.DangerRed,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(4.dp))
        Text(
            UiText.res(R.string.forge_failed_body, message).asString(),
            style = MaterialTheme.typography.bodySmall,
            color = IronvellumColors.InkMuted,
        )
        Spacer(Modifier.height(8.dp))
        IronvellumButton(label = stringResource(R.string.forge_try_again), onClick = onTryAgain, quiet = true, modifier = Modifier.fillMaxWidth())
    }
}

/**
 * What a failed generation cleared, shown greyed and inert: the days and how many movements each held. It is
 * a picture of the past, not a proposal; nothing in it can be opened, edited or applied.
 */
@Composable
internal fun ForgePreviousProposal(plan: RoutinePlan, modifier: Modifier = Modifier) {
    val clearedDescription = stringResource(R.string.forge_previous_description)
    Column(
        modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = clearedDescription },
    ) {
        Text(
            stringResource(R.string.forge_previous_caption),
            style = MaterialTheme.typography.labelSmall,
            color = IronvellumColors.InkMuted,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        plan.presets.forEach { preset: PlannedPreset ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(dayLabel(preset.scheduledDay), style = MaterialTheme.typography.bodySmall, color = IronvellumColors.Rune, modifier = Modifier.width(92.dp))
                Text(preset.name, style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.Rune, modifier = Modifier.weight(1f))
                Text(movementsText(preset.entries.size), style = MaterialTheme.typography.bodySmall, color = IronvellumColors.Rune)
            }
        }
    }
}

/** "Mon", "Tue" ... for the days sheet; null reads as no day. */
internal fun dayShort(day: Int?): String = day?.let { DayNames.short(it) } ?: "—"

/** "Mon, Wed and Fri". */
internal fun joinDays(days: List<Int>): String {
    return ListText.and(days.sorted().map { dayShort(it) })
}

/**
 * The days-already-taken sheet: the new week's days against the saved cycle's, and three honest ways out. Fit
 * around my days moves what clashes and deletes nothing; Replace my cycle deletes the saved rites (and says
 * how many); Cancel changes nothing.
 */
@Composable
internal fun ForgeAddConflictSheet(
    conflict: DayConflict,
    savedRites: Int,
    takenDays: List<Int>,
    onFit: () -> Unit,
    onReplace: () -> Unit,
    onCancel: () -> Unit,
) {
    IronvellumDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.forge_conflict_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    UiText.themed(R.string.forge_conflict_taken, R.string.forge_conflict_taken__t, joinDays(takenDays)).asString() + " " +
                        UiText.plural(R.plurals.forge_conflict_clash, conflict.clashes, numberWord(conflict.clashes)).asString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                )
                conflict.placements.forEach { row ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 44.dp)
                            .semantics(mergeDescendants = true) {},
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(dayShort(row.day), style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.Ink, modifier = Modifier.width(40.dp))
                        Text(row.rite, style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.Ink, modifier = Modifier.weight(1f))
                        Text(
                            when {
                                row.takenBy == null -> stringResource(R.string.forge_conflict_free)
                                row.movedTo != null -> UiText.res(R.string.forge_conflict_taken_moved, row.takenBy.toString(), dayShort(row.movedTo)).asString()
                                else -> UiText.res(R.string.forge_conflict_taken_by, row.takenBy.toString()).asString()
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = IronvellumColors.InkMuted,
                        )
                    }
                }
                Text(
                    if (conflict.fitsAround) {
                        stringResource(R.string.forge_conflict_fit_note)
                    } else {
                        UiText.themed(R.string.forge_conflict_nofit, R.string.forge_conflict_nofit__t).asString()
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = IronvellumColors.InkMuted,
                )
                QuietAction(UiText.themed(R.string.forge_replace_link, R.string.forge_replace_link__t).asString(), danger = true, onClick = onReplace)
                Text(
                    UiText.themedPlural(R.plurals.forge_conflict_delete, R.plurals.forge_conflict_delete__t, savedRites).asString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = IronvellumColors.InkMuted,
                )
            }
        },
        confirmButton = {
            // A disabled Fit says why in the line above; the tap does nothing rather than half-work.
            IronvellumButton(label = stringResource(R.string.forge_conflict_fit_button), onClick = onFit, enabled = conflict.fitsAround)
        },
        dismissButton = { IronvellumButton(label = stringResource(R.string.common_cancel), onClick = onCancel, quiet = true) },
    )
}

private fun numberWord(n: Int): UiText = when (n) {
    1 -> UiText.res(R.string.forge_number_one)
    2 -> UiText.res(R.string.forge_number_two)
    3 -> UiText.res(R.string.forge_number_three)
    4 -> UiText.res(R.string.forge_number_four)
    5 -> UiText.res(R.string.forge_number_five)
    6 -> UiText.res(R.string.forge_number_six)
    else -> UiText.Raw("$n")
}

/** The discard sheet: what is about to be lost, counted. */
@Composable
internal fun ForgeDiscardSheet(edits: PlanEdits, answersChanged: Boolean, onKeep: () -> Unit, onDiscard: () -> Unit) {
    IronvellumDialog(
        onDismissRequest = onKeep,
        title = { Text(stringResource(R.string.forge_discard_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.forge_discard_body))
                val lines = edits.lines() + if (answersChanged) listOf(stringResource(R.string.forge_discard_answers)) else emptyList()
                lines.forEach { line ->
                    Text(
                        "• $line",
                        style = MaterialTheme.typography.bodySmall,
                        color = IronvellumColors.InkMuted,
                    )
                }
            }
        },
        confirmButton = { IronvellumButton(label = stringResource(R.string.forge_discard_button), onClick = onDiscard, danger = true) },
        dismissButton = { IronvellumButton(label = stringResource(R.string.forge_discard_keep), onClick = onKeep, quiet = true) },
    )
}

/** The split the lifter picked and the days it runs, in one line: "4 days a week · Upper / lower". */
internal fun cycleSubtitle(days: Int, split: TrainingSplit): String =
    Words.text(UiText.plural(R.plurals.forge_cycle_subtitle, days, days, split.label))

/** Picking a split keeps the day count when the split fits it, else takes the split's first. */
internal fun daysAfterSplit(split: TrainingSplit, days: Int): Int =
    if (days in split.dayOptions) days else split.dayOptions.first()

/** Picking a day count keeps the split when it fits that many days, else the first split offered for it. */
internal fun splitAfterDays(split: TrainingSplit, days: Int): TrainingSplit =
    if (days in split.dayOptions) split else TrainingSplit.OPTIONS.firstOrNull { it.second == days }?.first ?: TrainingSplit.forDays(days)

/** Every split the Forge offers, in display order. */
internal val FORGE_SPLITS: List<TrainingSplit> = TrainingSplit.OPTIONS.map { it.first }.distinct()

/** Every day count the Forge offers. */
internal val FORGE_DAYS: List<Int> = TrainingSplit.OPTIONS.map { it.second }.distinct().sorted()
