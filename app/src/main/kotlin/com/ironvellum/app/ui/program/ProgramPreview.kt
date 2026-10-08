package com.ironvellum.app.ui.program

import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.size
import com.ironvellum.app.ui.components.TermInfo
import com.ironvellum.app.ui.components.Term
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ironvellum.app.domain.MuscleArea
import com.ironvellum.app.domain.Equipment
import com.ironvellum.app.domain.Evidence
import com.ironvellum.app.domain.Gear
import com.ironvellum.app.domain.Improvement
import com.ironvellum.app.domain.Muscle
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

private fun entryScheme(entry: PlannedEntry): String = buildString {
    append("${entry.sets}\u00D7${entry.reps}")
    entry.targetWeightKg?.let { append(" @ ${formatKg(it)} kg") }
}

private fun formatKg(kg: Double): String =
    if (kg == kg.toLong().toDouble()) kg.toLong().toString() else kg.toString()

/**
 * One proposed training day: a header line (the weekday, the rite, how many exercises), then one
 * compact row per exercise. The reason, the steppers and the remove pad stay folded until the
 * lifter taps a row, one row open at a time. Unfolded, every row carried a reason line and five
 * pads, and the first-run review read as a wall of controls eight screens long.
 *
 * [folded] makes the day itself a one-line row that opens its exercises on a tap (the builder's
 * proposal, where seven days would otherwise push the actions off the screen); the default keeps
 * the day open on its own card, as first-run review shows it.
 *
 * [showNote] is off where the caller shows the plan's notes once for the
 * whole routine instead of repeating the rest guidance under every day.
 */
@Composable
fun ProposedDay(
    preset: PlannedPreset,
    editable: Boolean,
    onSets: (entryIndex: Int, delta: Int) -> Unit,
    onReps: (entryIndex: Int, delta: Int) -> Unit,
    onRemove: (entryIndex: Int) -> Unit,
    showNote: Boolean = true,
    folded: Boolean = false,
    /** A folded day opens on arrival: first-run's review opens its first day so the exercises are in view. */
    startOpen: Boolean = !folded,
) {
    var open by rememberSaveable(preset.name, preset.scheduledDay) { mutableStateOf<Int?>(null) }
    var dayOpen by rememberSaveable(preset.name, preset.scheduledDay) { mutableStateOf(startOpen) }
    val body: @Composable ColumnScope.() -> Unit = {
        val count = preset.entries.size
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .then(
                    if (folded) {
                        Modifier.clickable(
                            onClickLabel = if (dayOpen) "Fold ${preset.name}" else "Open ${preset.name}",
                            role = Role.Button,
                        ) { dayOpen = !dayOpen }
                    } else {
                        Modifier
                    },
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                dayLabel(preset.scheduledDay),
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
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(
                "$count ${if (count == 1) "exercise" else "exercises"}",
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
                maxLines = 1,
            )
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
                    dayName = dayLabel(preset.scheduledDay),
                    editable = editable,
                    expanded = open == entryIndex,
                    onToggle = { open = if (open == entryIndex) null else entryIndex },
                    onSets = { delta -> onSets(entryIndex, delta) },
                    onReps = { delta -> onReps(entryIndex, delta) },
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
 * One proposed exercise: name and scheme, tappable. Open and [editable], it is the rite editor's
 * card (the same steppers, [EntryCard]): why the exercise is there, Sets and Reps with their pads,
 * and Remove. Every pad clears the 44dp touch floor and names its exercise, because a bare "-" tells
 * a screen reader nothing.
 */
@Composable
fun ProposedEntryRow(
    entry: PlannedEntry,
    dayName: String,
    editable: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    onSets: (delta: Int) -> Unit,
    onReps: (delta: Int) -> Unit,
    onRemove: () -> Unit,
) {
    // Why this lift and dose, and where the load came from - without the
    // citations, which [SourcesPanel] lists once per screen.
    val reasons = listOfNotNull(entry.why, entry.loadNote)
        .map { Evidence.split(it).first }
        .filter { it.isNotBlank() }
    val opens = editable || reasons.isNotEmpty()
    val reasonLines: @Composable () -> Unit = {
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
    }
    if (expanded && editable) {
        EntryCard(
            name = entry.exerciseName,
            foldLabel = "Close ${entry.exerciseName}",
            onFold = onToggle,
            actions = {
                Spacer(Modifier.weight(1f))
                TextAction("Remove exercise", onRemove, description = "Remove ${entry.exerciseName} from $dayName")
            },
        ) {
            reasonLines()
            StepperRow(
                label = "Sets",
                value = entry.sets.toString(),
                unit = null,
                less = "Fewer sets for ${entry.exerciseName}",
                more = "More sets for ${entry.exerciseName}",
                error = false,
                decimal = false,
                maxDigits = 0,
                onValue = null,
                onLess = { onSets(-1) },
                onMore = { onSets(1) },
            )
            StepperRow(
                label = "Reps",
                value = entry.reps.toString(),
                unit = null,
                less = "Fewer reps for ${entry.exerciseName}",
                more = "More reps for ${entry.exerciseName}",
                error = false,
                decimal = false,
                maxDigits = 0,
                onValue = null,
                onLess = { onReps(-1) },
                onMore = { onReps(1) },
            )
        }
        return
    }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .then(
                    if (opens) {
                        Modifier.clickable(
                            onClickLabel = if (expanded) "Close ${entry.exerciseName}" else "Open ${entry.exerciseName}",
                            onClick = onToggle,
                        )
                    } else {
                        Modifier
                    },
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                entry.exerciseName,
                style = MaterialTheme.typography.bodyMedium,
                color = IronvellumColors.Ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                entryScheme(entry),
                style = MaterialTheme.typography.labelMedium,
                fontFamily = ChakraPetch,
                color = IronvellumColors.SystemGreen,
                letterSpacing = IronvellumTracking.InlineLabel,
            )
            // Collapsed-state hint: onboarding's prose says "Tap an exercise
            // to adjust it", but the row gave no visible sign it opens.
            // Decorative - the clickable already announces open/close - and
            // only on rows that actually open.
            if (opens) {
                Spacer(Modifier.width(6.dp))
                Text(
                    if (expanded) "▾" else "▸",
                    style = MaterialTheme.typography.labelMedium,
                    color = IronvellumColors.InkMuted,
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }
        }
        if (expanded) {
            reasonLines()
            Spacer(Modifier.height(8.dp))
        }
    }
}

/**
 * The small tap target used by the proposal editor. Not IronvellumButton: four
 * steppers per exercise row need a control that stays narrow while clearing
 * the 44dp touch floor, which the 44dp box guarantees on both axes.
 */
@Composable
fun TapPad(
    label: String,
    description: String,
    minSize: androidx.compose.ui.unit.Dp = 44.dp,
    onClick: () -> Unit,
) {
    val shape = MaterialTheme.shapes.extraSmall
    Box(
        Modifier
            .clip(shape)
            .background(IronvellumColors.VaultHigh)
            .clickable(onClick = onClick)
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

/**
 * The week's coverage in one row: how many muscles the proposal leaves short, and a way into the
 * coverage of that same proposal for the full picture. Judgement stays in words (a count, not a colour),
 * because "legible without colour alone" is an accessibility floor here, not a nice-to-have.
 */
@Composable
fun CoverageSummaryRow(
    presets: List<PlannedPreset>,
    tier: VolumeLevel,
    focus: TrainingFocus,
    priorities: Set<MuscleArea>,
    onOpen: () -> Unit,
) {
    val short = remember(presets, tier, focus, priorities) {
        shortCount(presets, coverageGoal(tier, focus, priorities))
    }
    InkDivider()
    ListRow(
        label = shortHeadline(short),
        value = "Coverage",
        onClickLabel = "Open coverage of this cycle",
        onClick = onOpen,
    )
    InkDivider()
}

/** "Load set", not the enum's "Load_set". */
private fun changeKindLabel(kind: PlanChange.Kind): String = when (kind) {
    PlanChange.Kind.ADDED -> "Added"
    PlanChange.Kind.REMOVED -> "Removed"
    PlanChange.Kind.SWAPPED -> "Swapped"
    PlanChange.Kind.ADJUSTED -> "Adjusted"
    PlanChange.Kind.LOAD_SET -> "Load set"
}

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
                "NO CHANGES NEEDED",
                style = MaterialTheme.typography.titleSmall,
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.Bold,
                color = IronvellumColors.SystemGreen,
                letterSpacing = IronvellumTracking.InlineLabel,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (stillShort.isEmpty()) {
                    "Already fits your goal. Nothing changed."
                } else {
                    "Already fits your goal. " +
                        "${ProgramGenerator.joinWithAnd(stillShort.map { it.label.lowercase() }).replaceFirstChar { it.uppercase() }} " +
                        "stay short — another day would cover them."
                },
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
            )
        }
        return
    }
    InkPanel(Modifier.fillMaxWidth()) {
        Text(
            "BEFORE",
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
                    "${entry.sets}\u00D7${entry.reps}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = ChakraPetch,
                    color = IronvellumColors.InkMuted,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "AFTER",
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
                    "${entry.sets}\u00D7${entry.reps}" + when (kind) {
                        PlanChange.Kind.ADDED -> "  · added"
                        PlanChange.Kind.SWAPPED -> "  · swapped in"
                        PlanChange.Kind.ADJUSTED, PlanChange.Kind.LOAD_SET -> "  · adjusted"
                        else -> ""
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = ChakraPetch,
                    color = IronvellumColors.SystemGreen,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "WHAT CHANGED, AND WHY",
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
            "SOURCES (${sources.size})  ${if (open) "-" else "+"}",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            color = IronvellumColors.InkMuted,
            letterSpacing = IronvellumTracking.InlineLabel,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(onClickLabel = if (open) "Hide sources" else "Show sources") { open = !open }
                .padding(vertical = 14.dp),
        )
        if (open) {
            sources.forEach { source ->
                Text(
                    "${source.citation} doi:${source.doi}",
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
            options = TrainingSplit.OPTIONS.map { it.first }.distinct().map { it to it.label },
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
internal fun splitCaption(split: TrainingSplit, days: Int): String = when (split) {
    TrainingSplit.FULL_BODY ->
        "Every muscle, every rite. Fewest days, longest rites."
    TrainingSplit.UPPER_LOWER ->
        "Upper, then lower, twice each. Every muscle twice a week."
    TrainingSplit.PUSH_PULL_LEGS -> if (days == 3) {
        "Each muscle once a week. Fine for growth, less practice per exercise."
    } else {
        "Push, pull, legs twice over. Short rites, every muscle twice a week."
    }
    TrainingSplit.UPPER_LOWER_PPL ->
        "PPL is push / pull / legs: those three days, then upper and lower. Every muscle twice in five days."
}

/**
 * The volume question's caption: the weekly range the level buys for this
 * goal, and who it usually suits. Strength ranges do not move with the level
 * (the strength curve saturates early - Pelland 2026), and the caption says so.
 */
internal fun volumeCaption(volume: VolumeLevel, focus: TrainingFocus): String {
    val range = ProgramRules.weeklySetTarget(volume, focus)
    val sets = "${range.start.toInt()}-${range.endInclusive.toInt()} sets per muscle a week"
    if (focus == TrainingFocus.STRENGTH || focus == TrainingFocus.SKILL) {
        // "level" is the XP level everywhere else, so name the setting itself.
        return "$sets on any setting. Standard and High add exercises."
    }
    return when (volume) {
        VolumeLevel.LOW -> "$sets. Enough for a first year."
        VolumeLevel.STANDARD -> "$sets. After a year or so of training."
        VolumeLevel.HIGH -> "$sets. For years of training."
    }
}

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
        )
    },
    restore = { saved ->
        @Suppress("UNCHECKED_CAST")
        Equipment(
            fullGym = saved[0] as Boolean,
            gear = (saved[1] as List<String>).map(Gear::valueOf).toSet(),
            dumbbellMaxKg = (saved[2] as Double).takeIf { kg -> kg >= 0.0 },
            dumbbellPair = saved[3] as Boolean,
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
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            PickCell(
                label = "Full gym",
                selected = equipment?.fullGym == true,
                modifier = Modifier.weight(1f),
                description = "Full gym: every machine and cable",
                onClick = { onChange(Equipment.FULL_GYM) },
            )
            PickCell(
                label = "Nothing",
                selected = nothingSelected(equipment),
                modifier = Modifier.weight(1f),
                description = "Nothing: floor work only",
                onClick = { onChange(Equipment.NOTHING) },
            )
        }
        Gear.entries.chunked(2).forEach { chunk ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                chunk.forEach { gear ->
                    val on = gear in current.gear
                    PickCell(
                        label = gear.label,
                        selected = on,
                        modifier = Modifier.weight(1f),
                        description = "${gear.label}, ${if (on) "on" else "off"}",
                        onClick = {
                            val gearSet = if (on) current.gear - gear else current.gear + gear
                            // First dumbbell tap sets the max so the stepper has a number to edit.
                            val maxKg = current.dumbbellMaxKg
                                ?: if (gear == Gear.DUMBBELLS && !on) DEFAULT_DUMBBELL_KG else null
                            onChange(Equipment(false, gearSet, maxKg, current.dumbbellPair))
                        },
                    )
                }
            }
        }
        if (equipment != null && !equipment.fullGym && Gear.DUMBBELLS in equipment.gear) {
            val maxKg = equipment.dumbbellMaxKg ?: DEFAULT_DUMBBELL_KG
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                PickCell(
                    label = "One",
                    selected = !equipment.dumbbellPair,
                    modifier = Modifier.weight(1f),
                    description = "One dumbbell",
                    onClick = { onChange(equipment.copy(dumbbellPair = false)) },
                )
                PickCell(
                    label = "Pair",
                    selected = equipment.dumbbellPair,
                    modifier = Modifier.weight(1f),
                    description = "A pair of dumbbells",
                    onClick = { onChange(equipment.copy(dumbbellPair = true)) },
                )
                TapPad(
                    label = "−",
                    description = "Lower dumbbell max, currently up to ${maxKg.toInt()} kg",
                    onClick = {
                        onChange(equipment.copy(dumbbellMaxKg = (maxKg - 1.0).coerceAtLeast(MIN_DUMBBELL_KG)))
                    },
                )
                Text(
                    "Up to ${maxKg.toInt()} kg",
                    style = MaterialTheme.typography.labelSmall,
                    color = IronvellumColors.Ink,
                )
                TapPad(
                    label = "+",
                    description = "Raise dumbbell max, currently up to ${maxKg.toInt()} kg",
                    onClick = {
                        onChange(equipment.copy(dumbbellMaxKg = (maxKg + 1.0).coerceAtMost(MAX_DUMBBELL_KG)))
                    },
                )
            }
        }
        if (equipment != null) {
            Text(
                gearCaption(equipment),
                style = MaterialTheme.typography.labelSmall,
                color = IronvellumColors.InkMuted,
            )
        }
    }
}

/** What the current answer opens up, in plain words. */
/** The "Nothing" cell is lit only for an explicit empty answer, never for an unanswered one. */
internal fun nothingSelected(equipment: Equipment?): Boolean =
    equipment != null && !equipment.fullGym && equipment.gear.isEmpty()

private fun gearCaption(equipment: Equipment): String = when {
    equipment.fullGym -> "Barbells, machines and cables."
    equipment.gear.isEmpty() -> "Floor work only."
    else -> {
        val owned = equipment.gear.joinToString(", ") { it.label.lowercase() }
        val pair = if (Gear.DUMBBELLS in equipment.gear) {
            val max = equipment.dumbbellMaxKg?.toInt() ?: DEFAULT_DUMBBELL_KG.toInt()
            " (${if (equipment.dumbbellPair) "pair" else "one"}, up to $max kg)"
        } else {
            ""
        }
        "Floor work plus $owned$pair."
    }
}
