package com.ironvellum.app.ui.program

import com.ironvellum.app.R
import com.ironvellum.app.data.Units
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.size
import com.ironvellum.app.data.Words
import com.ironvellum.app.ui.components.TermInfo
import com.ironvellum.app.ui.components.Term
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ironvellum.app.domain.MuscleArea
import com.ironvellum.app.domain.Equipment
import com.ironvellum.app.domain.Evidence
import com.ironvellum.app.domain.Gear
import com.ironvellum.app.domain.LoadKit
import com.ironvellum.app.domain.Loads
import com.ironvellum.app.domain.Improvement
import com.ironvellum.app.domain.Muscle
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.ui.asString
import com.ironvellum.app.domain.PlannedEntry
import com.ironvellum.app.domain.PlannedPreset
import com.ironvellum.app.domain.PlanChange
import com.ironvellum.app.domain.ProgramGenerator
import com.ironvellum.app.domain.ProgramRules
import com.ironvellum.app.domain.RoutinePlan
import com.ironvellum.app.domain.VolumeLevel
import com.ironvellum.app.domain.TrainingFocus
import com.ironvellum.app.domain.TrainingSplit
import com.ironvellum.app.ui.components.EntryCard
import com.ironvellum.app.ui.components.InkDivider
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.components.InkSegmented
import com.ironvellum.app.ui.components.ListRow
import com.ironvellum.app.ui.components.StepperRow
import com.ironvellum.app.ui.components.TextAction
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.IronvellumTracking
import com.ironvellum.app.ui.theme.inkBorder

/**
 * Shared preview furniture for every generated program surface - first-run
 * onboarding's review step and the Train -> NEW PRESET builder both render
 * days from here, so the two can never drift into looking like different apps.
 */

private val DAY_NAMES = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

/** ISO weekday to a name; null stays legible instead of crashing on an index. */
fun dayLabel(scheduledDay: Int?): String =
    scheduledDay?.let { DAY_NAMES.getOrNull(it - 1) } ?: "Unscheduled"

/** What an exercise row is, apart from where it sits: the movement and its modifiers. */
internal fun entryKey(entry: PlannedEntry): String = "${entry.exerciseName}|${entry.modifiers}"

/**
 * One proposed training day: a header line (the weekday, the rite, how much work it holds), then one
 * movement row each, written effort-first: the hard sets and how close to failure they are taken, with the
 * rep guide and load at the right. The header folds the day; with [onOpenEntry] a movement row hands the
 * movement to the caller (the Forge opens its day page), without it the row unfolds into the editor in place
 * (first-run review), one row open at a time.
 *
 * [folded] makes the day itself a one-line row that opens its movements on a tap (the Forge's proposal, where
 * seven days would otherwise push the actions off the screen); the default keeps the day open on its own card.
 *
 * [showNote] is off where the caller shows the proposal's notes once for the whole cycle instead of repeating
 * the rest guidance under every day.
 */
@Composable
fun ProposedDay(
    preset: PlannedPreset,
    editable: Boolean,
    equipment: Equipment?,
    onSets: (entryIndex: Int, delta: Int) -> Unit,
    onRemove: (entryIndex: Int) -> Unit,
    onLoad: ((entryIndex: Int, delta: Int) -> Unit)? = null,
    onSwap: ((entryIndex: Int) -> Unit)? = null,
    onOpenEntry: ((entryIndex: Int) -> Unit)? = null,
    showNote: Boolean = true,
    folded: Boolean = false,
    /** A folded day opens on arrival: first-run's review opens its first day so the exercises are in view. */
    startOpen: Boolean = !folded,
) {
    // The open row is remembered by the entry it is, not its position: removing an exercise above it
    // would otherwise slide the open card onto its neighbour.
    var open by rememberSaveable(preset.name, preset.scheduledDay) { mutableStateOf<String?>(null) }
    var dayOpen by rememberSaveable(preset.name, preset.scheduledDay) { mutableStateOf(startOpen) }
    val day = dayLabel(preset.scheduledDay)
    val body: @Composable ColumnScope.() -> Unit = {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .then(
                    if (folded) {
                        Modifier
                            .clickable(
                                onClickLabel = UiText.res(
                                    if (dayOpen) R.string.programpreview_day_fold else R.string.programpreview_day_open,
                                    day,
                                    preset.name,
                                ).asString(),
                                role = Role.Button,
                            ) { dayOpen = !dayOpen }
                            .semantics { stateDescription = if (dayOpen) "Expanded" else "Collapsed" }
                    } else {
                        Modifier
                    },
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val countText = if (dayOpen) hardSetsText(dayHardSets(preset)) else movementsText(preset.entries.size)
            if (LocalDensity.current.fontScale > 1.3f) {
                // Large text: the day, name and count stack in one column instead of sharing a line.
                Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
                    Text(day, style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted)
                    Text(
                        preset.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = IronvellumColors.Ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(countText, style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted)
                }
            } else {
                Text(
                    day,
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                    modifier = Modifier.width(92.dp),
                )
                Text(
                    preset.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = IronvellumColors.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    countText,
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                    maxLines = 1,
                )
            }
            if (folded) {
                Text(
                    if (dayOpen) "⌃" else "›",
                    style = MaterialTheme.typography.labelMedium,
                    color = IronvellumColors.InkMuted,
                    textAlign = TextAlign.End,
                    // A fixed slot, so the open and closed glyphs sit at the same edge.
                    modifier = Modifier.width(16.dp).clearAndSetSemantics {},
                )
            }
        }
        if (dayOpen) {
            // Rest guidance rides every generated preset's note; its papers go to
            // Sources.
            val note = Evidence.split(preset.note).first
            if (showNote && note.isNotBlank()) {
                Text(
                    note,
                    style = MaterialTheme.typography.labelSmall,
                    color = IronvellumColors.InkMuted,
                )
            }
            Spacer(Modifier.height(4.dp))
            preset.entries.forEachIndexed { entryIndex, entry ->
                ProposedEntryRow(
                    entry = entry,
                    dayName = day,
                    equipment = equipment,
                    editable = editable,
                    expanded = open == entryKey(entry),
                    onToggle = {
                        if (onOpenEntry != null) onOpenEntry(entryIndex)
                        else open = if (open == entryKey(entry)) null else entryKey(entry)
                    },
                    onSets = { delta -> onSets(entryIndex, delta) },
                    onLoad = onLoad?.let { f -> { delta: Int -> f(entryIndex, delta) } },
                    onSwap = onSwap?.let { f -> { f(entryIndex) } },
                    onRemove = {
                        open = null
                        onRemove(entryIndex)
                    },
                )
            }
        }
    }
    if (folded) {
        Column(Modifier.fillMaxWidth(), content = body)
    } else {
        // Geometry comes from the theme: InkCoverageTest fails the build on an
        // inline RoundedCornerShape or CircleShape in UI code.
        val dayShape = MaterialTheme.shapes.medium
        Column(
            Modifier
                .fillMaxWidth()
                .clip(dayShape)
                .background(IronvellumColors.Vault)
                .inkBorder(IronvellumColors.Rune, dayShape, 1.dp)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            content = body,
        )
    }
}

/**
 * One movement, effort-first: its name over "3 hard sets · stop 0–2 reps short of failure", the rep guide and
 * load at the right. Open and [editable] it becomes the editor ([EffortEntryEditor]). A row that opens carries
 * a chevron; every row is a 48dp target.
 */
@Composable
fun ProposedEntryRow(
    entry: PlannedEntry,
    dayName: String,
    equipment: Equipment?,
    editable: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    onSets: (delta: Int) -> Unit,
    onRemove: () -> Unit,
    onLoad: ((delta: Int) -> Unit)? = null,
    onSwap: (() -> Unit)? = null,
) {
    // Why this lift and dose, and where the load came from - without the
    // citations, which [SourcesPanel] lists once per screen.
    val reasons = listOfNotNull(entry.why, entry.loadNote)
        .map { Evidence.split(it).first }
        .filter { it.isNotBlank() }
    val opens = editable || reasons.isNotEmpty()
    // Large text: the rep guide and load go under the name rather than squeezing it beside them.
    val stackDetail = LocalDensity.current.fontScale > 1.3f
    val detail: @Composable () -> Unit = {
        Text(
            movementDetail(entry, entry.targetWeightKg?.let { Units.withUnit(it) }),
            style = MaterialTheme.typography.labelMedium,
            fontFamily = ChakraPetch,
            color = IronvellumColors.SystemGreen,
            textAlign = if (stackDetail) TextAlign.Start else TextAlign.End,
        )
    }
    if (expanded && editable) {
        EffortEntryEditor(
            entry = entry,
            dayName = dayName,
            equipment = equipment,
            onFold = onToggle,
            onSets = onSets,
            onRemove = onRemove,
            onLoad = onLoad,
            onSwap = onSwap,
        )
        return
    }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .then(
                    if (opens) {
                        Modifier.clickable(
                            onClickLabel = if (expanded) "Close ${entry.exerciseName}" else "Open ${entry.exerciseName}",
                            role = Role.Button,
                            onClick = onToggle,
                        )
                    } else {
                        Modifier
                    },
                )
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    entry.exerciseName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = IronvellumColors.Ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    movementLine(entry),
                    style = MaterialTheme.typography.labelSmall,
                    color = IronvellumColors.InkMuted,
                )
                if (stackDetail) detail()
            }
            if (!stackDetail) {
                Spacer(Modifier.width(8.dp))
                detail()
            }
            // Collapsed-state hint: the row gave no visible sign it opens. Decorative - the clickable
            // already announces open/close - and only on rows that actually open.
            if (opens) {
                Spacer(Modifier.width(6.dp))
                Text(
                    if (expanded) "⌃" else "›",
                    style = MaterialTheme.typography.labelMedium,
                    color = IronvellumColors.InkMuted,
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }
        }
        if (expanded) {
            reasons.forEach { line ->
                // A load worked out from a 1-rep max says what that is.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        line,
                        style = MaterialTheme.typography.bodySmall,
                        color = IronvellumColors.InkMuted,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if ("1-rep max" in line) TermInfo(Term.ONE_REP_MAX)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/**
 * The small tap target used by the proposal editor. Not IronvellumButton: a stepper needs a control that stays
 * narrow while clearing the 48dp touch floor, which the 48dp box guarantees on both axes.
 */
@Composable
fun TapPad(
    label: String,
    description: String,
    minSize: androidx.compose.ui.unit.Dp = 48.dp,
    onClick: () -> Unit,
) {
    val shape = MaterialTheme.shapes.extraSmall
    Box(
        Modifier
            .clip(shape)
            .background(IronvellumColors.VaultHigh)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = minSize)
            .widthIn(min = minSize)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontFamily = ChakraPetch,
            color = IronvellumColors.SystemGreen,
            modifier = Modifier.semantics { contentDescription = description },
        )
    }
}

/** "Load set", not the enum's "Load_set". */
private fun changeKindLabel(kind: PlanChange.Kind): String = Words.text(
    UiText.res(
        when (kind) {
            PlanChange.Kind.ADDED -> R.string.programpreview_change_added
            PlanChange.Kind.REMOVED -> R.string.programpreview_change_removed
            PlanChange.Kind.SWAPPED -> R.string.programpreview_change_swapped
            PlanChange.Kind.ADJUSTED -> R.string.programpreview_change_adjusted
            PlanChange.Kind.LOAD_SET -> R.string.programpreview_change_load_set
        },
    ),
)

/**
 * The improve pass, shown honestly: what left (struck through, named as
 * removed), what arrived (named as added/swapped), and the reason for every
 * line. An empty changes list is a result too - "no changes needed" - and it
 * gets its own quiet panel rather than an empty box. [stillShort] names the
 * muscles the week leaves under target even so, so the panel never claims
 * the week is complete above a volume list that says otherwise.
 */
@Composable
fun BeforeAfter(improvement: Improvement, stillShort: List<Muscle> = emptyList()) {
    if (improvement.changes.isEmpty()) {
        InkPanel(Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.programpreview_no_changes_title),
                style = MaterialTheme.typography.titleSmall,
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.Bold,
                color = IronvellumColors.SystemGreen,
                letterSpacing = IronvellumTracking.InlineLabel,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (stillShort.isEmpty()) {
                    stringResource(R.string.programpreview_no_changes_fits)
                } else {
                    UiText.res(
                        R.string.programpreview_no_changes_short,
                        ProgramGenerator.joinWithAnd(stillShort.map { it.label.lowercase() }).replaceFirstChar { it.uppercase() },
                    ).asString()
                },
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
            )
        }
        return
    }
    InkPanel(Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.programpreview_before_label),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            color = IronvellumColors.InkMuted,
            letterSpacing = IronvellumTracking.InlineLabel,
        )
        Spacer(Modifier.height(4.dp))
        improvement.before.entries.forEach { entry ->
            val removed = improvement.changes.any {
                it.kind == PlanChange.Kind.REMOVED && it.exerciseName == entry.exerciseName
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(
                    entry.exerciseName,
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                    textDecoration = if (removed) TextDecoration.LineThrough else null,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    UiText.res(R.string.programpreview_sets_reps, entry.sets, entry.reps).asString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = ChakraPetch,
                    color = IronvellumColors.InkMuted,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.programpreview_after_label),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            color = IronvellumColors.SystemGreen,
            letterSpacing = IronvellumTracking.InlineLabel,
        )
        Spacer(Modifier.height(4.dp))
        improvement.after.entries.forEach { entry ->
            val kind = improvement.changes
                .firstOrNull { it.exerciseName == entry.exerciseName && it.kind != PlanChange.Kind.REMOVED }
                ?.kind
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(
                    entry.exerciseName,
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.Ink,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    UiText.res(
                        when (kind) {
                            PlanChange.Kind.ADDED -> R.string.programpreview_sets_reps_added
                            PlanChange.Kind.SWAPPED -> R.string.programpreview_sets_reps_swapped
                            PlanChange.Kind.ADJUSTED, PlanChange.Kind.LOAD_SET -> R.string.programpreview_sets_reps_adjusted
                            else -> R.string.programpreview_sets_reps
                        },
                        entry.sets,
                        entry.reps,
                    ).asString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = ChakraPetch,
                    color = IronvellumColors.SystemGreen,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.programpreview_changed_label),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            color = IronvellumColors.InkMuted,
            letterSpacing = IronvellumTracking.InlineLabel,
        )
        improvement.changes.forEach { change ->
            Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Text(
                    "${changeKindLabel(change.kind)} · ${change.exerciseName}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = ChakraPetch,
                    color = IronvellumColors.Ink,
                )
                Text(
                    Evidence.split(change.detail).first,
                    style = MaterialTheme.typography.labelSmall,
                    color = IronvellumColors.InkMuted,
                )
            }
        }
    }
}

/** Every note and reason a plan carries, for [SourcesPanel]. */
internal fun planTexts(presets: List<PlannedPreset>, routineNote: String = ""): List<String> =
    listOf(routineNote) +
        presets.flatMap { preset -> listOf(preset.note) + preset.entries.flatMap { listOfNotNull(it.why, it.loadNote) } }

/**
 * The plan's notes, each said once: the day notes (the shared rest
 * guidance, plus any day's own extension - a note that another note merely
 * extends is dropped rather than printed twice), then the routine-wide
 * advice ([RoutinePlan.note]) that no single workout carries.
 */
internal fun planNotes(plan: RoutinePlan): List<String> {
    val notes = plan.presets.map { Evidence.split(it.note).first.trim() }.filter { it.isNotBlank() }.distinct()
    val routine = Evidence.split(plan.note).first.trim()
    return notes.filter { n -> notes.none { it != n && it.startsWith(n) } } + listOf(routine).filter { it.isNotBlank() }
}

/**
 * The papers behind [texts], once each, at the foot of the screen: the
 * reasons above read clean, and the evidence is a tap away. Closed by default.
 */
@Composable
internal fun SourcesPanel(texts: List<String>) {
    val sources = texts.flatMap { Evidence.split(it).second }.distinct()
    if (sources.isEmpty()) return
    var open by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Text(
            UiText.res(if (open) R.string.programpreview_sources_open else R.string.programpreview_sources_closed, sources.size).asString(),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            color = IronvellumColors.InkMuted,
            letterSpacing = IronvellumTracking.InlineLabel,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(onClickLabel = stringResource(if (open) R.string.programpreview_sources_hide else R.string.programpreview_sources_show)) { open = !open }
                .padding(vertical = 14.dp),
        )
        if (open) {
            sources.forEach { source ->
                Text(
                    UiText.res(R.string.programpreview_source_citation, source.citation, source.doi).asString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = IronvellumColors.InkMuted,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
        }
    }
}

/**
 * The split question: the split the generator offers ([TrainingSplit.OPTIONS]) as one segmented
 * row, then, for a split that fits more than one day count, how many days. One pick sets both,
 * so no invalid pairing (upper/lower on three days) can be asked for.
 */
@Composable
internal fun SplitPicker(
    split: TrainingSplit,
    days: Int,
    onPick: (TrainingSplit, Int) -> Unit,
) {
    fun daysFor(option: TrainingSplit) = TrainingSplit.OPTIONS.filter { it.first == option }.map { it.second }
    Column {
        InkSegmented(
            options = TrainingSplit.OPTIONS.map { it.first }.distinct().map { it to it.pickerLabel },
            selected = split,
            onPick = { picked ->
                val counts = daysFor(picked)
                onPick(picked, if (days in counts) days else counts.first())
            },
        )
        val counts = daysFor(split)
        if (counts.size > 1) {
            Spacer(Modifier.height(8.dp))
            InkSegmented(
                options = counts.map { it to "$it days" },
                selected = days,
                onPick = { onPick(split, it) },
            )
        }
    }
}

/** What the picked split does with the week, in one line. */
internal fun splitCaption(split: TrainingSplit, days: Int): String = Words.text(
    when (split) {
        TrainingSplit.FULL_BODY ->
            UiText.themed(R.string.programpreview_split_full_body, R.string.programpreview_split_full_body__t)
        TrainingSplit.UPPER_LOWER ->
            UiText.res(R.string.programpreview_split_upper_lower)
        TrainingSplit.PUSH_PULL_LEGS -> if (days == 3) {
            UiText.res(R.string.programpreview_split_ppl3)
        } else {
            UiText.themed(R.string.programpreview_split_ppl6, R.string.programpreview_split_ppl6__t)
        }
        TrainingSplit.UPPER_LOWER_PPL ->
            UiText.res(R.string.programpreview_split_hybrid)
    },
)

/**
 * One cell of a pick grid, drawn as an outlined chip: SystemGreen text on a half-strength SystemGreen
 * outline when off, Ink on a tinted fill with a check and a full outline when chosen. [role] is Button
 * for a single choice, Checkbox for a toggle.
 */
@Composable
internal fun PickCell(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    description: String? = null,
    role: Role = Role.Button,
    onClick: () -> Unit,
) {
    val shape = MaterialTheme.shapes.small
    Row(
        modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(if (selected) IronvellumColors.SystemGreen.copy(alpha = 0.16f) else Color.Transparent)
            .inkBorder(
                if (selected) IronvellumColors.SystemGreen else IronvellumColors.SystemGreen.copy(alpha = 0.55f),
                shape,
                1.dp,
            )
            .clickable(role = role, onClick = onClick)
            // The unscheduled option is drawn as "—", which a screen reader
            // announces as a dash. Say what it means (same rule as the editor).
            .semantics {
                this.selected = selected
                if (description != null) contentDescription = description
            }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = IronvellumColors.Ink,
                modifier = Modifier.padding(end = 4.dp).size(14.dp),
            )
        } else if (role == Role.Checkbox) {
            // An empty box says the cell can be switched on before it is tapped.
            Box(
                Modifier
                    .padding(end = 6.dp)
                    .size(14.dp)
                    .inkBorder(IronvellumColors.SystemGreen.copy(alpha = 0.55f), MaterialTheme.shapes.extraSmall, 1.dp),
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            color = if (selected) IronvellumColors.Ink else IronvellumColors.SystemGreen,
        )
    }
}

/** Default per-dumbbell ceiling asked about when the toggle goes on. */
private const val DEFAULT_DUMBBELL_KG = 24.0

/** Stepper bounds for the dumbbell max, in 1 kg steps. */
private const val MIN_DUMBBELL_KG = 1.0
private const val MAX_DUMBBELL_KG = 80.0

/**
 * Saves [Equipment] into instance state: a data class with a set is not
 * bundle-saveable on its own, and losing the answer to a config change reads
 * as the app forgetting what you own.
 */
val EquipmentSaver = listSaver<Equipment, Any>(
    save = {
        listOf(
            it.fullGym,
            it.gear.map { gear -> gear.name },
            it.dumbbellMaxKg ?: -1.0,
            it.dumbbellPair,
            it.kit.vestMaxKg ?: -1.0,
            it.kit.vestBlockKg,
            it.kit.dipBelt,
        )
    },
    restore = { saved ->
        @Suppress("UNCHECKED_CAST")
        Equipment(
            fullGym = saved[0] as Boolean,
            gear = (saved[1] as List<String>).map(Gear::valueOf).toSet(),
            dumbbellMaxKg = (saved[2] as Double).takeIf { kg -> kg >= 0.0 },
            dumbbellPair = saved[3] as Boolean,
            // Instance state saved before the kit existed has four entries: no vest, no belt.
            kit = if (saved.size >= 7) {
                LoadKit((saved[4] as Double).takeIf { kg -> kg >= 0.0 }, saved[5] as Double, saved[6] as Boolean)
            } else {
                LoadKit.NONE
            },
        )
    },
)

/** [EquipmentSaver] for an answer that may still be unset (an empty list). */
val OptionalEquipmentSaver = Saver<Equipment?, Any>(
    save = { eq -> eq?.let { with(EquipmentSaver) { save(it) } } ?: emptyList<Any>() },
    restore = { saved -> (saved as List<*>).takeIf { it.isNotEmpty() }?.let { EquipmentSaver.restore(it) } },
)

/**
 * The gear question, shared by onboarding and the builder so the two can
 * never drift: two preset cells (Full gym / Nothing) over a toggle grid of
 * the eight gear items. A toggle tap clears the full-gym answer and flips
 * that one item - the owner's real kit is "pull-up bar but no rings", which
 * no coarse level could say. With dumbbells on, a sub-row asks one or a pair
 * and steps the per-dumbbell max in 1 kg, because the load cap the generator
 * applies is only honest if the number is the lifter's own.
 */
@Composable
internal fun GearPicker(
    equipment: Equipment?,
    onChange: (Equipment) -> Unit,
) {
    // Null is "not answered yet": no cell lit, so the lifter has to choose.
    val current = equipment ?: Equipment.NOTHING
    // One column at a large text size: in half the width a name like "Parallettes" breaks inside the word.
    val columns = if (LocalDensity.current.fontScale > 1.3f) 1 else 2
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            PickCell(
                label = stringResource(R.string.programpreview_gear_full_gym),
                selected = equipment?.fullGym == true,
                modifier = Modifier.weight(1f),
                description = stringResource(R.string.programpreview_gear_full_gym_description),
                onClick = { onChange(Equipment.FULL_GYM.copy(kit = current.kit)) },
            )
            PickCell(
                label = stringResource(R.string.programpreview_gear_nothing),
                selected = nothingSelected(equipment),
                modifier = Modifier.weight(1f),
                description = stringResource(R.string.programpreview_gear_nothing_description),
                onClick = { onChange(Equipment.NOTHING.copy(kit = current.kit)) },
            )
        }
        Gear.entries.chunked(columns).forEach { chunk ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                chunk.forEach { gear ->
                    val on = gear in current.gear
                    PickCell(
                        label = gear.label,
                        selected = on,
                        modifier = Modifier.weight(1f),
                        description = UiText.res(
                            if (on) R.string.programpreview_gear_on_description else R.string.programpreview_gear_off_description,
                            gear.label,
                        ).asString(),
                        role = Role.Checkbox,
                        onClick = {
                            val gearSet = if (on) current.gear - gear else current.gear + gear
                            // First dumbbell tap sets the max so the stepper has a number to edit.
                            val maxKg = current.dumbbellMaxKg
                                ?: if (gear == Gear.DUMBBELLS && !on) DEFAULT_DUMBBELL_KG else null
                            onChange(current.copy(fullGym = false, gear = gearSet, dumbbellMaxKg = maxKg))
                        },
                    )
                }
            }
        }
        if (equipment != null && !equipment.fullGym && Gear.DUMBBELLS in equipment.gear) {
            val maxKg = equipment.dumbbellMaxKg ?: DEFAULT_DUMBBELL_KG
            val oneOrPair: @Composable RowScope.() -> Unit = {
                PickCell(
                    label = stringResource(R.string.programpreview_gear_one),
                    selected = !equipment.dumbbellPair,
                    modifier = Modifier.weight(1f),
                    description = stringResource(R.string.programpreview_gear_one_description),
                    onClick = { onChange(equipment.copy(dumbbellPair = false)) },
                )
                PickCell(
                    label = stringResource(R.string.programpreview_gear_pair),
                    selected = equipment.dumbbellPair,
                    modifier = Modifier.weight(1f),
                    description = stringResource(R.string.programpreview_gear_pair_description),
                    onClick = { onChange(equipment.copy(dumbbellPair = true)) },
                )
            }
            val lower: @Composable () -> Unit = {
                TapPad(
                    label = "−",
                    description = UiText.res(R.string.programpreview_dumbbell_lower_description, maxKg.toInt()).asString(),
                    onClick = {
                        onChange(equipment.copy(dumbbellMaxKg = (maxKg - 1.0).coerceAtLeast(MIN_DUMBBELL_KG)))
                    },
                )
            }
            val raise: @Composable () -> Unit = {
                TapPad(
                    label = "+",
                    description = UiText.res(R.string.programpreview_dumbbell_raise_description, maxKg.toInt()).asString(),
                    onClick = {
                        onChange(equipment.copy(dumbbellMaxKg = (maxKg + 1.0).coerceAtMost(MAX_DUMBBELL_KG)))
                    },
                )
            }
            val maxText: @Composable (Modifier) -> Unit = { m ->
                Text(
                    UiText.res(R.string.programpreview_dumbbell_max, maxKg.toInt()).asString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = IronvellumColors.Ink,
                    modifier = m,
                )
            }
            if (LocalDensity.current.fontScale > 1.3f) {
                // Large text: one or pair on one line, the max stepper on the next, so no label is squeezed.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) { oneOrPair() }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    lower()
                    maxText(Modifier.weight(1f).wrapContentWidth(Alignment.CenterHorizontally))
                    raise()
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    oneOrPair()
                    lower()
                    maxText(Modifier)
                    raise()
                }
            }
        }
        WornKitPicker(current, onChange)
        if (equipment != null) {
            Text(
                gearCaption(equipment) + kitCaption(equipment.kit),
                style = MaterialTheme.typography.labelSmall,
                color = IronvellumColors.InkMuted,
            )
        }
    }
}

/**
 * The vest and dip belt, beside the gear: they are not [Gear] (a full gym does not come with a vest), they
 * only set the increments loaded bodyweight lifts move in. A vest asks for its top load and the size of
 * its blocks; a belt takes 2.5 kg plates with no ceiling, so it needs no number.
 */
@Composable
private fun WornKitPicker(current: Equipment, onChange: (Equipment) -> Unit) {
    val kit = current.kit
    val unit = Units.current
    val vestOn = kit.vestMaxKg != null
    val vest: @Composable (Modifier) -> Unit = { cell ->
        PickCell(
            label = stringResource(R.string.programpreview_vest_label),
            selected = vestOn,
            modifier = cell,
            description = stringResource(if (vestOn) R.string.programpreview_vest_on_description else R.string.programpreview_vest_off_description),
            role = Role.Checkbox,
            onClick = {
                onChange(current.copy(kit = kit.copy(vestMaxKg = if (vestOn) null else LoadKit.DEFAULT_VEST_KG)))
            },
        )
    }
    val belt: @Composable (Modifier) -> Unit = { cell ->
        PickCell(
            label = stringResource(R.string.programpreview_belt_label),
            selected = kit.dipBelt,
            modifier = cell,
            description = stringResource(if (kit.dipBelt) R.string.programpreview_belt_on_description else R.string.programpreview_belt_off_description),
            role = Role.Checkbox,
            onClick = { onChange(current.copy(kit = kit.copy(dipBelt = !kit.dipBelt))) },
        )
    }
    if (LocalDensity.current.fontScale > 1.3f) {
        // Stacked, like the gear above, so the names have the whole width.
        vest(Modifier.fillMaxWidth())
        belt(Modifier.fillMaxWidth())
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            vest(Modifier.weight(1f))
            belt(Modifier.weight(1f))
        }
    }
    val maxKg = kit.vestMaxKg ?: return
    KitStepperRow(
        label = stringResource(R.string.programpreview_vest_top_label),
        value = UiText.res(R.string.programpreview_vest_top_value, Loads.withUnit(maxKg, unit)).asString(),
        onLess = { onChange(current.copy(kit = kit.copy(vestMaxKg = LoadKit.stepVestMax(maxKg, -1, unit)))) },
        onMore = { onChange(current.copy(kit = kit.copy(vestMaxKg = LoadKit.stepVestMax(maxKg, 1, unit)))) },
    )
    KitStepperRow(
        label = stringResource(R.string.programpreview_vest_block_label),
        value = Loads.withUnit(kit.vestBlockKg, unit),
        onLess = { onChange(current.copy(kit = kit.copy(vestBlockKg = LoadKit.stepVestBlock(kit.vestBlockKg, -1, unit)))) },
        onMore = { onChange(current.copy(kit = kit.copy(vestBlockKg = LoadKit.stepVestBlock(kit.vestBlockKg, 1, unit)))) },
    )
}

/** A label, then − value +, on the dumbbell stepper's pads. */
@Composable
private fun KitStepperRow(label: String, value: String, onLess: () -> Unit, onMore: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = IronvellumColors.InkMuted, modifier = Modifier.weight(1f))
        TapPad(label = "−", description = UiText.res(R.string.programpreview_kit_lower_description, label.lowercase(), value).asString(), onClick = onLess)
        Text(value, style = MaterialTheme.typography.labelSmall, color = IronvellumColors.Ink)
        TapPad(label = "+", description = UiText.res(R.string.programpreview_kit_raise_description, label.lowercase(), value).asString(), onClick = onMore)
    }
}

/** What the current answer opens up, in plain words. */
/** The "Nothing" cell is lit only for an explicit empty answer, never for an unanswered one. */
internal fun nothingSelected(equipment: Equipment?): Boolean =
    equipment != null && !equipment.fullGym && equipment.gear.isEmpty()

/** The vest and belt, appended to the gear caption: "" when the lifter owns neither. */
private fun kitCaption(kit: LoadKit): String {
    val vestKg = kit.vestMaxKg
    val caption = when {
        vestKg != null && kit.dipBelt ->
            UiText.res(R.string.programpreview_kit_caption_both, Loads.withUnit(vestKg, Units.current))
        vestKg != null -> UiText.res(R.string.programpreview_kit_caption_vest, Loads.withUnit(vestKg, Units.current))
        kit.dipBelt -> UiText.res(R.string.programpreview_kit_caption_belt)
        else -> return ""
    }
    return " " + Words.text(caption)
}

private fun gearCaption(equipment: Equipment): String = Words.text(
    when {
        equipment.fullGym -> UiText.res(R.string.programpreview_gear_caption_full)
        equipment.gear.isEmpty() -> UiText.res(R.string.programpreview_gear_caption_floor)
        else -> {
            val owned = equipment.gear.joinToString(", ") { it.label.lowercase() }
            if (Gear.DUMBBELLS in equipment.gear) {
                val max = equipment.dumbbellMaxKg?.toInt() ?: DEFAULT_DUMBBELL_KG.toInt()
                UiText.res(
                    if (equipment.dumbbellPair) R.string.programpreview_gear_caption_plus_pair else R.string.programpreview_gear_caption_plus_one,
                    owned,
                    max,
                )
            } else {
                UiText.res(R.string.programpreview_gear_caption_plus, owned)
            }
        }
    },
)
