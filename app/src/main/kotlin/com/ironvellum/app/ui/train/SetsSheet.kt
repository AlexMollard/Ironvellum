package com.ironvellum.app.ui.train

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ironvellum.app.data.Units
import com.ironvellum.app.domain.DecimalInput
import com.ironvellum.app.domain.LiftLoading
import com.ironvellum.app.domain.LoadKit
import com.ironvellum.app.domain.RestTimer
import com.ironvellum.app.domain.SchemeBar
import com.ironvellum.app.domain.SchemePlan
import com.ironvellum.app.domain.SchemeSuggestion
import com.ironvellum.app.domain.SchemeText
import com.ironvellum.app.domain.SchemeType
import com.ironvellum.app.domain.SetKind
import com.ironvellum.app.domain.SetScheme
import com.ironvellum.app.domain.WarmupRamp
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.components.IronvellumDialog
import com.ironvellum.app.ui.components.SettingsSwitchRow
import com.ironvellum.app.ui.components.StepperRow
import com.ironvellum.app.ui.components.TextAction
import com.ironvellum.app.ui.components.WholeStepper
import com.ironvellum.app.ui.components.formatSteppedFigure
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.inkBorder

/** The bar a set stands as in a load-shape graphic: Working is plain ink, every other kind wears its own tint. */
private fun SetKind.barColor() = if (this == SetKind.WORKING) IronvellumColors.Ink.copy(alpha = 0.6f) else tint()

/** A lift's sets as a row of bars, tallest the heaviest: the mini graphic on the rite row and on each scheme card. */
@Composable
internal fun SchemeBars(bars: List<SchemeBar>, modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 20.dp) {
    Row(modifier.height(height), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        bars.forEach { bar ->
            Box(
                Modifier
                    .width(if (bars.size > 8) 5.dp else 8.dp)
                    .height(height * bar.level.coerceIn(0.1f, 1f))
                    .background(bar.kind.barColor(), MaterialTheme.shapes.extraSmall),
            )
        }
    }
}

/** The sets a scheme gives a lift of [sets] straight sets, as bars: what a rite row and a card draw. */
internal fun schemeBars(scheme: SetScheme, sets: Int): List<SchemeBar> = SchemeText.bars(scheme, sets.coerceAtLeast(1))

/**
 * The one "Sets for <lift>" sheet. It lays the lift's sets out as one of four schemes (Straight, Top set + back-offs,
 * Drop sets, Custom), marks the one that suits the movement, shows the settings of the chosen one, and previews the
 * sets the trial will make. Done hands back the entry with the scheme, and the set count, reps and load it implies;
 * dismissing keeps the entry as it was.
 */
@Composable
internal fun SetsSheet(
    entry: EditorEntry,
    lift: LiftLoading,
    /** The vest and belt this movement is loaded with; null steps the load as ever. */
    worn: LoadKit?,
    inSuperset: Boolean,
    onDone: (EditorEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    val suggestion = remember(entry.exerciseName, inSuperset, lift.bodyweightLift) {
        SchemeSuggestion.forLift(entry.exerciseName, inSuperset, lift.bodyweightLift)
    }
    var scheme by remember { mutableStateOf(entry.scheme) }
    var sets by remember { mutableStateOf(entry.sets) }
    var reps by remember { mutableStateOf(entry.reps) }
    var weight by remember { mutableStateOf(entry.weight) }
    // The top of the working reps' range, from the lift's own scheme; the floor is the reps above.
    var repsUp by remember { mutableStateOf(entry.scheme.workingReps?.last) }
    var fixedMode by remember { mutableStateOf(entry.scheme.backoffFixedKg != null) }
    var fixedText by remember { mutableStateOf(entry.scheme.backoffFixedKg?.let(::formatSteppedFigure) ?: "") }
    var more by remember { mutableStateOf(false) }

    val setCount = DecimalInput.parseWhole(sets)?.coerceIn(1, 30) ?: 3
    val repsFrom = DecimalInput.parseWhole(reps)?.coerceAtLeast(1) ?: 1
    val effective = scheme.copy(
        backoffFixedKg = if (fixedMode) DecimalInput.parse(fixedText)?.takeIf { it > 0.0 } else null,
        workingReps = repsUp?.takeIf { it > repsFrom }?.let { repsFrom..it },
    )
    val noFixedYet = scheme.type == SchemeType.TOP_BACKOFF && fixedMode && effective.backoffFixedKg == null

    IronvellumDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sets for ${entry.exerciseName.ifBlank { "this lift" }}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SchemeType.entries.forEach { type ->
                    val blocked = inSuperset && type == SchemeType.DROPS
                    val shown = when (type) {
                        // Top + back-offs keeps the lifter's set count: one top set, the rest back-offs.
                        SchemeType.TOP_BACKOFF -> effective.copy(type = type).let { if (scheme.type == type) it else it.fitTo(setCount) }
                        SchemeType.DROPS -> scheme.copy(type = type)
                        else -> SetScheme(type = type, custom = scheme.custom)
                    }
                    SchemeCard(
                        type = type,
                        selected = scheme.type == type,
                        suggested = suggestion.type == type,
                        blockedReason = if (blocked) "Not inside a superset" else null,
                        bars = schemeBars(
                            if (type == SchemeType.CUSTOM && shown.custom.isEmpty()) shown.copy(custom = scheme.kinds(setCount)) else shown,
                            setCount,
                        ),
                    ) {
                        scheme = when (type) {
                            SchemeType.CUSTOM -> scheme.copy(type = type, custom = scheme.custom.ifEmpty { scheme.kinds(setCount) })
                            SchemeType.TOP_BACKOFF -> scheme.copy(type = type).fitTo(setCount)
                            else -> scheme.copy(type = type)
                        }
                    }
                }
                Text(
                    "Suggested: " + suggestion.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                )
                when (scheme.type) {
                    SchemeType.STRAIGHT, SchemeType.DROPS -> WholeStepper("Sets", sets, "sets", false, 2, 30) { sets = it }
                    else -> Unit
                }
                if (scheme.type != SchemeType.TOP_BACKOFF) {
                    LoadTargetStepper(weight, loadGridLb(entry.exerciseName), false, worn, label = "Load") { weight = it }
                }
                when (scheme.type) {
                    SchemeType.TOP_BACKOFF -> TopBackoffSettings(
                        scheme, {
                            scheme = it
                            // The set count follows the back-offs, so flipping to another scheme and back keeps it.
                            sets = it.workingSets(setCount).toString()
                        }, weight, { weight = it }, entry.exerciseName, worn,
                        fixedMode,
                        { on ->
                            // Fixed starts on the load "% lighter" gives right now, so the preview does not jump and Done stays on.
                            if (on) fixedText = formatSteppedFigure(SchemePlan.seedFixedBackoff(DecimalInput.parse(weight), scheme, lift))
                            fixedMode = on
                        },
                        fixedText, { fixedText = it },
                    )
                    SchemeType.DROPS -> {
                        IntStepper("Sets that drop", scheme.dropSets, "drop sets", 1, setCount) { scheme = scheme.copy(dropSets = it) }
                        IntStepper("Drops each", scheme.dropCount, "drops", 1, SetScheme.MAX_DROPS) { scheme = scheme.copy(dropCount = it) }
                        IntStepper("Each drop lighter by", scheme.dropPercent, "drop percent", SetScheme.PERCENT_RANGE.first, SetScheme.PERCENT_RANGE.last, step = 5, unit = "%") {
                            scheme = scheme.copy(dropPercent = it)
                        }
                    }
                    SchemeType.CUSTOM -> CustomSets(scheme, inSuperset) { scheme = scheme.copy(custom = it) }
                    SchemeType.STRAIGHT -> Unit
                }
                TextAction(if (more) "Less" else "More: rest, reps, warm-up ramp", { more = !more })
                if (more) {
                    if (scheme.type != SchemeType.TOP_BACKOFF) {
                        WholeStepper("Reps, from", reps, "reps", false, 3, 500) { reps = it }
                        RepsUpStepper("Reps, up to", "reps", repsUp, repsFrom) { repsUp = it }
                    }
                    if (scheme.type == SchemeType.TOP_BACKOFF) {
                        IntStepper(
                            "Rest after the top set", scheme.restAfterTopSec, "top set rest",
                            SetScheme.REST_RANGE_SEC.first, SetScheme.REST_RANGE_SEC.last, step = 15, text = RestTimer.clock(scheme.restAfterTopSec),
                        ) { scheme = scheme.copy(restAfterTopSec = it) }
                    }
                    SettingsSwitchRow(
                        label = "Warm-up ramp",
                        caption = when {
                            (DecimalInput.parse(weight) ?: 0.0) > 0.0 -> "Start the trial with lighter warm-up sets for this lift."
                            WarmupRamp.isBarbell(entry.exerciseName) -> "No load is set yet, so the ramp is the empty bar. Set a load for the full ramp."
                            else -> "No load is set yet, so there is nothing to ramp up to. Set a load above."
                        },
                        checked = scheme.warmupRamp,
                        onCheckedChange = { scheme = scheme.copy(warmupRamp = it) },
                    )
                }
                YoullDo(effective, setCount, repsFrom, DecimalInput.parse(weight), lift)
            }
        },
        confirmButton = {
            IronvellumButton(
                label = "Done",
                enabled = !noFixedYet,
                onClick = {
                    val chosen = effective.normalised().let { it.fitTo(it.workingSets(setCount)) }
                    onDone(
                        entry.copy(
                            scheme = chosen,
                            sets = chosen.workingSets(setCount).toString(),
                            reps = if (chosen.type == SchemeType.TOP_BACKOFF) chosen.topReps.first.toString() else reps,
                            weight = weight,
                        ),
                    )
                },
            )
        },
        dismissButton = { IronvellumButton(label = "Cancel", onClick = onDismiss, quiet = true) },
    )
}

@Composable
private fun SchemeCard(
    type: SchemeType,
    selected: Boolean,
    suggested: Boolean,
    blockedReason: String?,
    bars: List<SchemeBar>,
    onClick: () -> Unit,
) {
    val enabled = blockedReason == null
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .inkBorder(if (selected) IronvellumColors.SystemGreen else IronvellumColors.Rune, MaterialTheme.shapes.extraSmall, 1.dp)
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                type.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (enabled) IronvellumColors.Ink else IronvellumColors.InkMuted,
            )
            if (blockedReason != null) {
                Text(blockedReason, style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted)
            } else if (suggested) {
                Text("SUGGESTED", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = IronvellumColors.SystemGreen)
            }
        }
        SchemeBars(bars)
    }
}

@Composable
private fun TopBackoffSettings(
    scheme: SetScheme,
    onScheme: (SetScheme) -> Unit,
    weight: String,
    onWeight: (String) -> Unit,
    exerciseName: String,
    worn: LoadKit?,
    fixedMode: Boolean,
    onFixedMode: (Boolean) -> Unit,
    fixedText: String,
    onFixedText: (String) -> Unit,
) {
    LoadTargetStepper(weight, loadGridLb(exerciseName), false, worn, label = "Top set load", onKgText = onWeight)
    Text(
        "${1 + scheme.backoffSets} sets in all: 1 top set and ${scheme.backoffSets} ${if (scheme.backoffSets == 1) "back-off" else "back-offs"}.",
        style = MaterialTheme.typography.labelSmall,
        color = IronvellumColors.InkMuted,
    )
    val max = SetScheme.REPS_RANGE.last
    IntStepper("Top reps, from", scheme.topReps.first, "top reps", 1, max) {
        onScheme(scheme.copy(topReps = it..maxOf(it, scheme.topReps.last)))
    }
    IntStepper("Top reps, up to", scheme.topReps.last, "top reps", scheme.topReps.first, max) {
        onScheme(scheme.copy(topReps = scheme.topReps.first..it))
    }
    IntStepper("Reps left, from", scheme.topRir.first, "reps left", 0, 10) {
        onScheme(scheme.copy(topRir = it..maxOf(it, scheme.topRir.last)))
    }
    IntStepper("Reps left, up to", scheme.topRir.last, "reps left", scheme.topRir.first, 10) {
        onScheme(scheme.copy(topRir = scheme.topRir.first..it))
    }
    IntStepper("Back-off sets", scheme.backoffSets, "back-off sets", 1, SetScheme.MAX_BACKOFF_SETS) { onScheme(scheme.copy(backoffSets = it)) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Back-off load", style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted, modifier = Modifier.weight(1f))
        ChoiceChip("% lighter", !fixedMode) { onFixedMode(false) }
        ChoiceChip("Fixed", fixedMode) { onFixedMode(true) }
    }
    if (fixedMode) {
        LoadTargetStepper(fixedText, loadGridLb(exerciseName), fixedText.isBlank(), worn, label = "Back-off load", onKgText = onFixedText)
    } else {
        IntStepper("Lighter than the top by", scheme.backoffPercent, "back-off percent", SetScheme.PERCENT_RANGE.first, SetScheme.PERCENT_RANGE.last, step = 5, unit = "%") {
            onScheme(scheme.copy(backoffPercent = it))
        }
    }
    IntStepper("Back-off reps, from", scheme.backoffReps.first, "back-off reps", 1, max) {
        onScheme(scheme.copy(backoffReps = it..maxOf(it, scheme.backoffReps.last)))
    }
    IntStepper("Back-off reps, up to", scheme.backoffReps.last, "back-off reps", scheme.backoffReps.first, max) {
        onScheme(scheme.copy(backoffReps = scheme.backoffReps.first..it))
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = 44.dp)
            .inkBorder(if (selected) IronvellumColors.SystemGreen else IronvellumColors.Rune, MaterialTheme.shapes.extraSmall, 1.dp)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = if (selected) IronvellumColors.Ink else IronvellumColors.InkMuted)
    }
}

/** A whole number between [min] and [max], moved in [step]s by the pads; [text] overrides how it reads ("3:00"). */
@Composable
private fun IntStepper(
    label: String,
    value: Int,
    noun: String,
    min: Int,
    max: Int,
    step: Int = 1,
    unit: String? = null,
    text: String = value.toString(),
    onValue: (Int) -> Unit,
) = StepperRow(
    label = label,
    value = text,
    unit = unit,
    less = "Fewer $noun",
    more = "More $noun",
    error = false,
    decimal = false,
    maxDigits = 0,
    onValue = null,
    onLess = { onValue((value - step).coerceAtLeast(min)) },
    onMore = { onValue((value + step).coerceAtMost(max)) },
)

/**
 * The top of a rep (or seconds) range that starts at [floor]: "–" while there is none, one more rep a tap from there, and
 * back to none when it is brought down to the floor. [SetScheme.stepRepsUp] moves it.
 */
@Composable
internal fun RepsUpStepper(label: String, noun: String, up: Int?, floor: Int, onUp: (Int?) -> Unit) = StepperRow(
    label = label,
    value = up?.takeIf { it > floor }?.toString() ?: "–",
    unit = null,
    less = "Lower $noun range top",
    more = "Higher $noun range top",
    error = false,
    decimal = false,
    maxDigits = 0,
    onValue = null,
    onLess = { onUp(SetScheme.stepRepsUp(floor, up, -1)) },
    onMore = { onUp(SetScheme.stepRepsUp(floor, up, 1)) },
)

/** One row per set of a Custom scheme: tap a row to cycle its type; add or take off the last set. */
@Composable
private fun CustomSets(scheme: SetScheme, inSuperset: Boolean, onKinds: (List<SetKind>) -> Unit) {
    val options = listOf(SetKind.WORKING, SetKind.TOP, SetKind.BACKOFF, SetKind.DROP).filter { !(inSuperset && it == SetKind.DROP) }
    scheme.custom.forEachIndexed { index, kind ->
        // A drop follows a set, so the first set cannot be one.
        val allowed = options.filter { index > 0 || it != SetKind.DROP }
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClickLabel = "Change set ${index + 1} type") {
                    val next = allowed[(allowed.indexOf(kind) + 1) % allowed.size]
                    onKinds(scheme.custom.mapIndexed { i, k -> if (i == index) next else k })
                }
                .semantics { contentDescription = "Set ${index + 1}, ${kind.label}" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Set ${index + 1}", style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted, modifier = Modifier.weight(1f))
            Text(kind.label + "  ▾", style = MaterialTheme.typography.bodyMedium, color = kind.tint())
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (scheme.custom.size < SetScheme.MAX_CUSTOM_SETS) {
            TextAction("Add a set", { onKinds(scheme.custom + SetKind.WORKING) })
        }
        if (scheme.custom.size > 1) {
            TextAction("Remove last set", { onKinds(scheme.custom.dropLast(1)) })
        }
    }
}

/** The live "You'll do" preview: the sets the trial will begin with, from the same planner the trial uses. */
@Composable
private fun YoullDo(scheme: SetScheme, sets: Int, reps: Int, kg: Double?, lift: LiftLoading) {
    val planned = SchemePlan.plan(scheme.normalised(), sets, reps, kg, lift)
    Column(
        Modifier
            .fillMaxWidth()
            .inkBorder(IronvellumColors.Rune, MaterialTheme.shapes.extraSmall, 1.dp)
            .padding(12.dp)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("You'll do", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = IronvellumColors.InkMuted)
        if (scheme.warmupRamp) Text("Warm-up ramp first", style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted)
        planned.forEach { set ->
            Row(Modifier.fillMaxWidth()) {
                Text(set.kind.label, style = MaterialTheme.typography.bodySmall, color = set.kind.tint(), modifier = Modifier.weight(1f))
                val repsText = set.range?.let { SchemeText.reps(it) } ?: set.reps.toString()
                val load = set.weightKg?.let { (if (lift.bodyweightLift) "+" else "") + Units.withUnit(it) } ?: if (lift.bodyweightLift) "bodyweight" else "no load set"
                Text("$repsText × $load", style = MaterialTheme.typography.bodySmall, color = IronvellumColors.Ink)
            }
        }
    }
}
