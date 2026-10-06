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
import com.ironvellum.app.ui.components.InkPanel
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
 * One proposed training day: a header line, then one compact row per
 * exercise. The reason, the steppers and the remove pad stay folded until the
 * lifter taps a row, one row open at a time. Unfolded, every row carried a
 * reason line and five pads, and the first-run review read as a wall of
 * controls eight screens long.
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
) {
    // The ink identity is hand-drawn: a geometric RoundedCornerShape here
    // reads as a foreign rectangle, which is why InkCoverageTest fails the
    // build on one.
    val dayShape = MaterialTheme.shapes.medium
    var open by rememberSaveable(preset.name, preset.scheduledDay) { mutableStateOf<Int?>(null) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(dayShape)
            .background(IronvellumColors.Vault)
            .inkBorder(IronvellumColors.Rune, dayShape, 1.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                dayLabel(preset.scheduledDay).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.Bold,
                color = IronvellumColors.Ink,
                letterSpacing = IronvellumTracking.InlineLabel,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                preset.name.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = ChakraPetch,
                color = IronvellumColors.SystemGreen,
                letterSpacing = IronvellumTracking.InlineLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 3.dp),
            )
        }
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

/**
 * One proposed exercise: name and scheme, tappable. Open, it shows why the
 * exercise is there and, when [editable], the set and rep pads and remove.
 * Every pad clears the 44dp touch floor, and each announces its purpose by
 * name, because a bare "−" tells a screen reader nothing.
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
                    if (expanded) "\u25BE" else "\u25B8",
                    style = MaterialTheme.typography.labelMedium,
                    color = IronvellumColors.InkMuted,
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }
            // On the name row, not the stepper row: five 44dp pads plus their
            // captions do not fit a 360dp phone on one line.
            if (expanded && editable) {
                Spacer(Modifier.width(6.dp))
                TapPad("\u2715", "Remove ${entry.exerciseName} from $dayName") { onRemove() }
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
            if (editable) {
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        "SETS",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = ChakraPetch,
                        color = IronvellumColors.InkMuted,
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                    TapPad("\u2212", "Fewer sets for ${entry.exerciseName}") { onSets(-1) }
                    TapPad("+", "More sets for ${entry.exerciseName}") { onSets(1) }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "REPS",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = ChakraPetch,
                        color = IronvellumColors.InkMuted,
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                    TapPad("\u2212", "Fewer reps for ${entry.exerciseName}") { onReps(-1) }
                    TapPad("+", "More reps for ${entry.exerciseName}") { onReps(1) }
                }
            }
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
 * Fractional weekly sets per tracked muscle against the evidence range for
 * this tier and goal. Judgement is carried in WORDS (under / in / over) as
 * well as colour, because "legible without colour alone" is an accessibility
 * floor here, not a nice-to-have.
 */
@Composable
fun WeeklyVolumePanel(
    presets: List<PlannedPreset>,
    tier: VolumeLevel,
    focus: TrainingFocus,
    priorities: Set<MuscleArea> = emptySet(),
) {
    val volume = ProgramRules.weeklyVolume(presets)
    val goal = CoverageGoal(tier, focus, priorities.flatMap { it.muscles }.toSet())
    InkPanel(Modifier.fillMaxWidth()) {
        Text(
            "WEEKLY VOLUME · SETS PER MUSCLE",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            color = IronvellumColors.InkMuted,
            letterSpacing = IronvellumTracking.InlineLabel,
        )
        Spacer(Modifier.height(8.dp))
        JUDGED.forEach { muscle ->
            val sets = volume[muscle] ?: 0.0
            // Helpers are judged against their floor with no ceiling.
            val range = rangeFor(muscle, goal)
            val open = range.endInclusive == Double.MAX_VALUE
            val bound = if (open) "${trim1(range.start)}+" else "${trim1(range.start)}-${trim1(range.endInclusive)}"
            val spoken = if (open) "at least ${trim1(range.start)}" else "${trim1(range.start)} to ${trim1(range.endInclusive)}"
            val level = levelOf(muscle, sets, goal)
            val verdict = when (level) {
                CoverageLevel.NONE -> "UNTRAINED"
                CoverageLevel.UNDER -> "UNDER"
                CoverageLevel.LIGHT -> "LIGHT"
                CoverageLevel.OVER -> "OVER"
                CoverageLevel.IN_RANGE -> "IN RANGE"
            }
            val colour = verdictTextColour(level)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    // One announcement per row: the muscle, the count and the
                    // verdict, instead of three swipes through bare fragments.
                    .semantics {
                        contentDescription =
                            "${muscle.label}: ${trim1(sets)} ${if (trim1(sets) == "1") "set" else "sets"} weekly, " +
                                "$verdict, target $spoken"
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    muscle.label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = ChakraPetch,
                    color = IronvellumColors.Ink,
                    modifier = Modifier
                        .weight(1f)
                        .clearAndSetSemantics {},
                )
                Text(
                    "${trim1(sets)} / $bound",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = ChakraPetch,
                    color = IronvellumColors.InkMuted,
                    modifier = Modifier.clearAndSetSemantics {},
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    verdict,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = ChakraPetch,
                    fontWeight = FontWeight.Bold,
                    color = colour,
                    letterSpacing = IronvellumTracking.InlineLabel,
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }
        }
    }
}

/** "Load set", not the enum's "Load_set". */
private fun changeKindLabel(kind: PlanChange.Kind): String = when (kind) {
    PlanChange.Kind.ADDED -> "Added"
    PlanChange.Kind.REMOVED -> "Removed"
    PlanChange.Kind.SWAPPED -> "Swapped"
    PlanChange.Kind.ADJUSTED -> "Adjusted"
    PlanChange.Kind.LOAD_SET -> "Load set"
}

/** One decimal, trimmed of a trailing .0 - "14" and "14.5", never "14.0000". */
private fun trim1(value: Double): String {
    val rounded = (value * 10).toLong() / 10.0
    return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
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
 * The split question: every split and day count the generator offers
 * ([TrainingSplit.OPTIONS]), two per row. One pick sets both, so no invalid
 * pairing (upper/lower on three days) can be asked for.
 */
@Composable
internal fun SplitPicker(
    split: TrainingSplit,
    days: Int,
    onPick: (TrainingSplit, Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TrainingSplit.OPTIONS.chunked(2).forEach { chunk ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                chunk.forEach { (option, count) ->
                    PickCell(
                        label = "${option.label}\n$count days",
                        selected = option == split && count == days,
                        modifier = Modifier.weight(1f),
                        description = "${option.label}, $count days a week",
                        onClick = { onPick(option, count) },
                    )
                }
            }
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
 * One cell of a pick grid. The chosen cell is VaultHigh with an Emerald check and Ink text;
 * the rest sit on a Rune outline in InkMuted. [role] is Button for a single choice, Checkbox
 * for a toggle.
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
            .background(if (selected) IronvellumColors.VaultHigh else Color.Transparent)
            .inkBorder(IronvellumColors.Rune, shape, 1.dp)
            .clickable(role = role, onClick = onClick)
            // The unscheduled option is drawn as "—", which a screen reader
            // announces as a dash. Say what it means (same rule as the editor).
            .semantics {
                this.selected = selected
                if (description != null) contentDescription = description
            }
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = IronvellumColors.Emerald,
                modifier = Modifier.padding(end = 4.dp).size(14.dp),
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            color = if (selected) IronvellumColors.Ink else IronvellumColors.InkMuted,
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
                    color = IronvellumColors.SovereignGold,
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
