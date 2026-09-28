package com.ironvellum.app.ui.program

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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.ironvellum.app.domain.Improvement
import com.ironvellum.app.domain.Muscle
import com.ironvellum.app.domain.PlannedEntry
import com.ironvellum.app.domain.PlannedPreset
import com.ironvellum.app.domain.PlanChange
import com.ironvellum.app.domain.ProgramGenerator
import com.ironvellum.app.domain.ProgramRules
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
    append("${entry.sets} x ${entry.reps}")
    entry.targetWeightKg?.let { append(" @ ${formatKg(it)} kg") }
}

private fun formatKg(kg: Double): String =
    if (kg == kg.toLong().toDouble()) kg.toLong().toString() else kg.toString()

/**
 * One proposed training day. The day and its focus lead, in the app's crest
 * voice; a brush hairline separates the plan from its movement list.
 */
@Composable
fun ProposedDay(
    preset: PlannedPreset,
    editable: Boolean,
    onSets: (entryIndex: Int, delta: Int) -> Unit,
    onReps: (entryIndex: Int, delta: Int) -> Unit,
    onRemove: (entryIndex: Int) -> Unit,
) {
    // The ink identity is hand-drawn: a geometric RoundedCornerShape here
    // reads as a foreign rectangle, which is why InkCoverageTest fails the
    // build on one.
    val dayShape = MaterialTheme.shapes.extraSmall
    Column(
        Modifier
            .fillMaxWidth()
            .clip(dayShape)
            .background(IronvellumColors.Vault)
            .inkBorder(IronvellumColors.Rune, dayShape, 1.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            dayLabel(preset.scheduledDay).uppercase(),
            style = MaterialTheme.typography.titleMedium,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = IronvellumColors.EmeraldBright,
            letterSpacing = IronvellumTracking.InlineLabel,
        )
        Text(
            preset.name.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            color = IronvellumColors.SystemGreen,
            letterSpacing = IronvellumTracking.InlineLabel,
        )
        // Rest + RIR guidance rides every generated preset's note; it belongs
        // with the session, not buried per movement.
        if (preset.note.isNotBlank()) {
            Text(
                preset.note,
                style = MaterialTheme.typography.labelSmall,
                color = IronvellumColors.InkMuted,
            )
        }
        Spacer(Modifier.height(8.dp))
        preset.entries.forEachIndexed { entryIndex, entry ->
            ProposedEntryRow(
                entry = entry,
                dayName = dayLabel(preset.scheduledDay),
                editable = editable,
                onSets = { delta -> onSets(entryIndex, delta) },
                onReps = { delta -> onReps(entryIndex, delta) },
                onRemove = { onRemove(entryIndex) },
            )
        }
    }
}

/**
 * One proposed movement. Name and scheme are the content; the edit cluster
 * sits beneath, captioned, so the plan is what the eye lands on and the
 * glyphs read as annotation. The steppers are 32dp tappable boxes - well
 * over the 24dp accessibility floor - and each announces its purpose by
 * name, because a bare "-" tells a screen reader nothing.
 */
@Composable
fun ProposedEntryRow(
    entry: PlannedEntry,
    dayName: String,
    editable: Boolean,
    onSets: (delta: Int) -> Unit,
    onReps: (delta: Int) -> Unit,
    onRemove: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                entry.exerciseName,
                style = MaterialTheme.typography.bodyMedium,
                color = IronvellumColors.Ink,
                modifier = Modifier.weight(1f),
            )
            Text(
                entryScheme(entry),
                style = MaterialTheme.typography.labelMedium,
                fontFamily = ChakraPetch,
                color = IronvellumColors.SystemGreen,
                letterSpacing = IronvellumTracking.InlineLabel,
            )
        }
        // The generator's evidence travels with the movement: why this lift and
        // dose, and where the load came from. Quiet by design - it answers the
        // question when she asks it, it does not shout over the plan.
        if (entry.why.isNotBlank()) {
            Text(
                entry.why,
                style = MaterialTheme.typography.labelSmall,
                color = IronvellumColors.InkMuted,
            )
        }
        entry.loadNote?.let { note ->
            if (note.isNotBlank()) {
                Text(
                    note,
                    style = MaterialTheme.typography.labelSmall,
                    color = IronvellumColors.InkMuted,
                )
            }
        }
        if (editable) {
            Spacer(Modifier.height(4.dp))
            Row(
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
                TapPad("-", "Fewer sets for ${entry.exerciseName}") { onSets(-1) }
                TapPad("+", "More sets for ${entry.exerciseName}") { onSets(1) }
                Spacer(Modifier.width(10.dp))
                Text(
                    "REPS",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = ChakraPetch,
                    color = IronvellumColors.InkMuted,
                    modifier = Modifier.clearAndSetSemantics {},
                )
                TapPad("-", "Fewer reps for ${entry.exerciseName}") { onReps(-1) }
                TapPad("+", "More reps for ${entry.exerciseName}") { onReps(1) }
                Spacer(Modifier.weight(1f))
                TapPad("x", "Remove ${entry.exerciseName} from $dayName") { onRemove() }
            }
        }
    }
}

/**
 * The small tap target used by the proposal editor. Not IronvellumButton: five
 * steppers per movement row need a control that stays narrow while clearing
 * the 24dp touch floor, which the 32dp box guarantees on both axes.
 */
@Composable
fun TapPad(
    label: String,
    description: String,
    onClick: () -> Unit,
) {
    val shape = MaterialTheme.shapes.extraSmall
    Box(
        Modifier
            .clip(shape)
            .background(IronvellumColors.VaultHigh)
            .clickable(onClick = onClick)
            .heightIn(min = 32.dp)
            .widthIn(min = 32.dp)
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
) {
    val volume = ProgramRules.weeklyVolume(presets)
    val target = ProgramRules.weeklySetTarget(tier, focus)
    InkPanel(Modifier.fillMaxWidth()) {
        Text(
            "WEEKLY VOLUME - SETS PER MUSCLE",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            color = IronvellumColors.InkMuted,
            letterSpacing = IronvellumTracking.InlineLabel,
        )
        Spacer(Modifier.height(8.dp))
        ProgramRules.TRACKED.forEach { muscle ->
            val sets = volume[muscle] ?: 0.0
            val verdict = when {
                sets < target.start -> "UNDER"
                sets > target.endInclusive -> "OVER"
                else -> "IN RANGE"
            }
            val colour = when {
                sets < target.start -> IronvellumColors.SovereignGold
                sets > target.endInclusive -> IronvellumColors.SovereignGold
                else -> IronvellumColors.SystemGreen
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    // One announcement per row: the muscle, the count and the
                    // verdict, instead of three swipes through bare fragments.
                    .semantics {
                        contentDescription =
                            "${muscle.label}: ${trim1(sets)} sets weekly, $verdict, target ${trim1(target.start)} to ${trim1(target.endInclusive)}"
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
                    "${trim1(sets)} / ${trim1(target.start)}-${trim1(target.endInclusive)}",
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
                    "This workout already matches the evidence for your goal. Nothing was altered."
                } else {
                    "This workout's movements, reps and sets already fit your goal. " +
                        "${ProgramGenerator.joinWithAnd(stillShort.map { it.label.lowercase() }).replaceFirstChar { it.uppercase() }} " +
                        "stay under target for the week: this session has no room left for them " +
                        "inside about ${ProgramRules.SESSION_BUDGET_SECONDS / 60} minutes, or does not " +
                        "train them. Another day would."
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
                    "${entry.sets} x ${entry.reps}",
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
                    "${entry.sets} x ${entry.reps}" + when (kind) {
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
                    "${change.kind.name.lowercase().replaceFirstChar { it.uppercase() }} - ${change.exerciseName}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = ChakraPetch,
                    color = IronvellumColors.Ink,
                )
                Text(
                    change.detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = IronvellumColors.InkMuted,
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
        "Every muscle, every session. The fewest days, so each one runs longest."
    TrainingSplit.UPPER_LOWER ->
        "Upper body, lower body, twice each: every muscle trained twice a week."
    TrainingSplit.PUSH_PULL_LEGS -> if (days == 3) {
        "Each muscle once a week. Weekly sets drive growth, not frequency (Pelland 2026), " +
            "but strength practice drops to once a week (Grgic 2018)."
    } else {
        "Push, pull and legs twice over: short, focused sessions, every muscle twice a week."
    }
    TrainingSplit.UPPER_LOWER_PPL ->
        "Push, pull and legs, then upper and lower: every muscle twice in five days."
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
        return "$sets at every level: strength needs less volume. Higher levels add movements per session."
    }
    return when (volume) {
        VolumeLevel.LOW -> "$sets. Plenty in a first year, or when time is short."
        VolumeLevel.STANDARD -> "$sets. The usual dose after a year or so of steady training."
        VolumeLevel.HIGH -> "$sets. For years of training: more sets still help, by less each time."
    }
}

/** One of the small drawn pick cells, in the preset editor's own style. */
@Composable
internal fun PickCell(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    description: String? = null,
    onClick: () -> Unit,
) {
    val shape = MaterialTheme.shapes.small
    OutlinedButton(
        shape = shape,
        onClick = onClick,
        // The unscheduled option is drawn as "—", which a screen reader
        // announces as a dash. Say what it means (same rule as the editor).
        modifier = modifier.then(
            if (description != null) Modifier.semantics { contentDescription = description } else Modifier,
        ),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            color = if (selected) IronvellumColors.SovereignGold else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
