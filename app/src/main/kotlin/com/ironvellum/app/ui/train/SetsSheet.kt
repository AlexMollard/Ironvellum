package com.ironvellum.app.ui.train

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ironvellum.app.data.EffortSetting
import com.ironvellum.app.data.Units
import com.ironvellum.app.data.Words
import com.ironvellum.app.domain.DecimalInput
import com.ironvellum.app.domain.EffortScale
import com.ironvellum.app.domain.LiftLoading
import com.ironvellum.app.domain.LoadKit
import com.ironvellum.app.domain.PlannedSet
import com.ironvellum.app.domain.ProgramRules
import com.ironvellum.app.domain.RestTimer
import com.ironvellum.app.domain.Rounding
import com.ironvellum.app.domain.SchemeBar
import com.ironvellum.app.domain.SchemePlan
import com.ironvellum.app.domain.SchemeSuggestion
import com.ironvellum.app.domain.SchemeText
import com.ironvellum.app.domain.SchemeType
import com.ironvellum.app.domain.SessionClock
import com.ironvellum.app.domain.SetKind
import com.ironvellum.app.domain.SetScheme
import com.ironvellum.app.domain.TrainingFocus
import com.ironvellum.app.domain.WarmupRamp
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.components.IronvellumDialog
import com.ironvellum.app.ui.components.RangeStepperRow
import com.ironvellum.app.ui.components.RowLabel
import com.ironvellum.app.ui.components.ROW_MIN_HEIGHT
import com.ironvellum.app.ui.components.StepperGroup
import com.ironvellum.app.ui.components.SettingsSwitchRow
import com.ironvellum.app.ui.components.StepperPart
import com.ironvellum.app.ui.components.StepperRow
import com.ironvellum.app.ui.components.TextAction
import com.ironvellum.app.ui.components.WholeStepper
import com.ironvellum.app.ui.components.formatSteppedFigure
import com.ironvellum.app.ui.components.stepWhole
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.IronvellumTracking
import com.ironvellum.app.ui.theme.inkBorder

/** The bar a set stands as in a load-shape graphic: Working is plain ink, every other kind wears its own tint. */
private fun SetKind.barColor() = if (this == SetKind.WORKING) IronvellumColors.Ink.copy(alpha = 0.6f) else tint()

/** A lift's sets as a row of bars, tallest the heaviest: the mini graphic on the rite row and on each scheme tile. */
@Composable
internal fun SchemeBars(bars: List<SchemeBar>, modifier: Modifier = Modifier, height: Dp = 20.dp) {
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

/** The sets a scheme gives a lift of [sets] straight sets, as bars: what a rite row draws. */
internal fun schemeBars(scheme: SetScheme, sets: Int): List<SchemeBar> = SchemeText.bars(scheme, sets.coerceAtLeast(1))

/**
 * The one "Sets for <lift>" sheet. A row of four tiles picks the scheme (Straight, Top set + back-offs, Drops,
 * Custom), "You'll do" draws the sets the trial will make, and the settings of the chosen scheme sit in one card per
 * role of set. Done hands back the entry with the scheme, and the set count, reps and load it implies; dismissing
 * keeps the entry as it was.
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
    val loadLabel = if (lift.bodyweightLift) "Added load" else "Load"
    val loadEmpty = LoadTargetStepperPlaceholder(lift.bodyweightLift)

    IronvellumDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sets for ${entry.exerciseName.ifBlank { "this lift" }}") },
        text = {
            // Tall enough to fill the sheet whatever the scheme, so switching schemes never changes its height.
            Column(Modifier.heightIn(min = LocalConfiguration.current.screenHeightDp.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SchemeType.entries.forEach { type ->
                        SchemeTile(
                            type = type,
                            selected = scheme.type == type,
                            enabled = !(inSuperset && type == SchemeType.DROPS),
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        ) {
                            scheme = when (type) {
                                SchemeType.CUSTOM -> scheme.copy(type = type, custom = scheme.custom.ifEmpty { scheme.kinds(setCount) })
                                SchemeType.TOP_BACKOFF -> scheme.copy(type = type).fitTo(setCount)
                                else -> scheme.copy(type = type)
                            }
                        }
                    }
                }
                if (inSuperset) Text("Not inside a superset", style = MaterialTheme.typography.labelSmall, color = IronvellumColors.InkMuted)
                if (suggestion.type != scheme.type && !(inSuperset && suggestion.type == SchemeType.DROPS)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                        Text("SUGGESTED", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = IronvellumColors.SystemGreen)
                        Text(suggestion.reason, style = MaterialTheme.typography.labelSmall, color = IronvellumColors.InkMuted, modifier = Modifier.weight(1f))
                    }
                }
                YoullDo(effective, setCount, repsFrom, DecimalInput.parse(weight), lift, entry.exerciseName)
                when (scheme.type) {
                    SchemeType.TOP_BACKOFF -> TopBackoffCards(
                        scheme, {
                            scheme = it
                            // The set count follows the back-offs, so flipping to another scheme and back keeps it.
                            sets = it.workingSets(setCount).toString()
                        },
                        weight, { weight = it }, entry.exerciseName, worn, lift,
                        fixedMode,
                        { on ->
                            // Fixed starts on the load "% lighter" gives right now, so the preview does not jump and Done stays on.
                            if (on) fixedText = formatSteppedFigure(SchemePlan.seedFixedBackoff(DecimalInput.parse(weight), scheme, lift))
                            fixedMode = on
                        },
                        fixedText, { fixedText = it },
                        effective,
                    )
                    else -> {
                        RoleCard(
                            if (scheme.type == SchemeType.DROPS) "WORKING SETS" else "EVERY SET",
                            if (scheme.type == SchemeType.CUSTOM) null else "$setCount sets",
                            SetKind.WORKING.barColor(),
                        ) {
                            if (scheme.type != SchemeType.CUSTOM) WholeStepper("Sets", sets, "sets", false, 2, 30) { sets = it }
                            LoadTargetStepper(weight, loadGridLb(entry.exerciseName), false, worn, label = loadLabel, emptyText = loadEmpty) { weight = it }
                            if (scheme.type == SchemeType.CUSTOM) {
                                WholeStepper("Reps", reps, "reps", false, 3, 500) { reps = it }
                            } else {
                                val fromPart = StepperPart(
                                    reps, "Fewer reps", "More reps",
                                    onLess = { reps = stepWhole(reps, -1, 500) }, onMore = { reps = stepWhole(reps, 1, 500) },
                                    onValue = { reps = it }, maxDigits = 3,
                                )
                                if (repsUp != null && repsUp!! > repsFrom) {
                                    RangeStepperRow(
                                        "Reps",
                                        from = fromPart,
                                        to = StepperPart(
                                            repsUp.toString(), "Lower reps range top", "Higher reps range top",
                                            onLess = { repsUp = SetScheme.stepRepsUp(repsFrom, repsUp, -1) },
                                            onMore = { repsUp = SetScheme.stepRepsUp(repsFrom, repsUp, 1) },
                                        ),
                                    )
                                } else {
                                    // One figure until the lifter asks for a range; the range's top steps down to this again.
                                    Row(Modifier.fillMaxWidth().heightIn(min = ROW_MIN_HEIGHT), verticalAlignment = Alignment.CenterVertically) {
                                        RowLabel("Reps", null, Modifier.weight(1f))
                                        Box(
                                            Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClickLabel = "Add a reps range") {
                                                repsUp = SetScheme.stepRepsUp(repsFrom, null, 2)
                                            }.padding(horizontal = 8.dp),
                                            contentAlignment = Alignment.Center,
                                        ) { Text("Add range", style = MaterialTheme.typography.bodySmall, color = IronvellumColors.SystemGreen) }
                                        StepperGroup("Reps", fromPart)
                                    }
                                }
                            }
                        }
                        if (scheme.type == SchemeType.DROPS) {
                            RoleCard("DROPS", dropsHeader(scheme), SetKind.DROP.tint()) {
                                IntStepper("Last sets that drop", scheme.dropSets, "drop sets", 1, setCount) { scheme = scheme.copy(dropSets = it) }
                                IntStepper("Drops after each", scheme.dropCount, "drops", 1, SetScheme.MAX_DROPS) { scheme = scheme.copy(dropCount = it) }
                                IntStepper(
                                    "Each drop is lighter by", scheme.dropPercent, "drop percent",
                                    SetScheme.PERCENT_RANGE.first, SetScheme.PERCENT_RANGE.last, step = 5, unit = "%",
                                ) { scheme = scheme.copy(dropPercent = it) }
                            }
                        }
                        if (scheme.type == SchemeType.CUSTOM) CustomSets(scheme, inSuperset) { scheme = scheme.copy(custom = it) }
                    }
                }
                MoreLink(if (scheme.type == SchemeType.TOP_BACKOFF) "Rest, warm-up ramp" else "Warm-up ramp", more) { more = !more }
                if (more) {
                    RoleCard(if (scheme.type == SchemeType.TOP_BACKOFF) "REST, WARM-UP RAMP" else "WARM-UP RAMP", null, IronvellumColors.Bracket) {
                        if (scheme.type == SchemeType.TOP_BACKOFF) {
                            IntStepper(
                                "Rest after the top set", scheme.restAfterTopSec, "top set rest",
                                SetScheme.REST_RANGE_SEC.first, SetScheme.REST_RANGE_SEC.last, step = 15, text = RestTimer.clock(scheme.restAfterTopSec),
                            ) { scheme = scheme.copy(restAfterTopSec = it) }
                        }
                        SettingsSwitchRow(
                            label = "Warm-up ramp",
                            caption = when {
                                (DecimalInput.parse(weight) ?: 0.0) > 0.0 -> "Start the ${Words.trial.s} with lighter warm-up sets for this lift."
                                WarmupRamp.isBarbell(entry.exerciseName) -> "No load is set yet, so the ramp is the empty bar. Set a load for the full ramp."
                                else -> "No load is set yet, so there is nothing to ramp up to. Set a load above."
                            },
                            checked = scheme.warmupRamp,
                            onCheckedChange = { scheme = scheme.copy(warmupRamp = it) },
                        )
                    }
                }
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

/** The bars a tile draws for a scheme: its shape, not this lift's own sets. */
private fun glyph(type: SchemeType): List<SchemeBar> = when (type) {
    SchemeType.STRAIGHT -> List(3) { SchemeBar(SetKind.WORKING, 0.8f) }
    SchemeType.TOP_BACKOFF -> listOf(SchemeBar(SetKind.WARMUP, 0.45f), SchemeBar(SetKind.TOP, 1f), SchemeBar(SetKind.BACKOFF, 0.85f), SchemeBar(SetKind.BACKOFF, 0.85f))
    SchemeType.DROPS -> List(3) { SchemeBar(SetKind.WORKING, 0.8f) } + listOf(SchemeBar(SetKind.DROP, 0.6f), SchemeBar(SetKind.DROP, 0.45f))
    SchemeType.CUSTOM -> listOf(
        SchemeBar(SetKind.WARMUP, 0.4f), SchemeBar(SetKind.WARMUP, 0.6f), SchemeBar(SetKind.TOP, 1f),
        SchemeBar(SetKind.BACKOFF, 0.8f), SchemeBar(SetKind.FAILURE, 0.7f),
    )
}

private fun SchemeType.tileLabel() = when (this) {
    SchemeType.STRAIGHT -> "Straight"
    SchemeType.TOP_BACKOFF -> "Top + back-offs"
    SchemeType.DROPS -> "Drops"
    SchemeType.CUSTOM -> "Custom"
}

/** One of the four scheme tiles: the scheme's shape over its name, outlined in the accent when it is the chosen one. */
@Composable
private fun SchemeTile(type: SchemeType, selected: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = MaterialTheme.shapes.extraSmall
    Column(
        modifier
            .alpha(if (enabled) 1f else 0.5f)
            .background(if (selected) IronvellumColors.VaultHigh else IronvellumColors.Vault, shape)
            .inkBorder(if (selected) IronvellumColors.SystemGreen else IronvellumColors.Rune, shape, 1.dp)
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) { this.selected = selected }
            .padding(horizontal = 4.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) {
        SchemeBars(glyph(type), height = 24.dp)
        Text(
            type.tileLabel(),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) IronvellumColors.Ink else IronvellumColors.InkMuted,
            textAlign = TextAlign.Center,
        )
    }
}

/** "1 drop after the last set", "2 drops after each of the last 3 sets". */
internal fun dropsHeader(scheme: SetScheme): String {
    val drops = if (scheme.dropCount == 1) "1 drop" else "${scheme.dropCount} drops"
    return drops + if (scheme.dropSets == 1) " after the last set" else " after each of the last ${scheme.dropSets} sets"
}

/** A group of settings for one role of set: a 3dp left edge in the role's colour, its name and count on the first line. */
@Composable
private fun RoleCard(title: String, count: String?, color: Color, content: @Composable ColumnScope.() -> Unit) {
    val shape = MaterialTheme.shapes.extraSmall
    Column(
        Modifier
            .fillMaxWidth()
            .background(IronvellumColors.Vault, shape)
            .inkBorder(IronvellumColors.Rune, shape, 1.dp)
            .drawBehind { drawRect(color, size = Size(3.dp.toPx(), size.height)) }
            .padding(start = 15.dp, end = 12.dp, top = 10.dp, bottom = 4.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = IronvellumTracking.InlineLabel,
                color = if (color == IronvellumColors.Bracket) IronvellumColors.InkMuted else color,
            )
            if (count != null) {
                Text(
                    count, style = MaterialTheme.typography.labelMedium, color = IronvellumColors.InkMuted,
                    textAlign = TextAlign.End, modifier = Modifier.weight(1f).padding(start = 12.dp),
                )
            }
        }
        content()
    }
}

/** The link row that opens the rest and warm-up settings. */
@Composable
private fun MoreLink(label: String, open: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClickLabel = if (open) "Hide $label" else "Show $label", onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = IronvellumColors.SystemGreen)
        Icon(
            if (open) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = IronvellumColors.SystemGreen,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun TopBackoffCards(
    scheme: SetScheme,
    onScheme: (SetScheme) -> Unit,
    weight: String,
    onWeight: (String) -> Unit,
    exerciseName: String,
    worn: LoadKit?,
    lift: LiftLoading,
    fixedMode: Boolean,
    onFixedMode: (Boolean) -> Unit,
    fixedText: String,
    onFixedText: (String) -> Unit,
    effective: SetScheme,
) {
    val max = SetScheme.REPS_RANGE.last
    val bodyweight = lift.bodyweightLift
    RoleCard("TOP SET", "1 set", SetKind.TOP.tint()) {
        LoadTargetStepper(
            weight, loadGridLb(exerciseName), false, worn,
            label = if (bodyweight) "Added load" else "Load", emptyText = LoadTargetStepperPlaceholder(bodyweight), onKgText = onWeight,
        )
        IntRangeStepper("Reps", "top reps", scheme.topReps, 1, max) { onScheme(scheme.copy(topReps = it)) }
        EffortChips(scheme.topRir) { onScheme(scheme.copy(topRir = it)) }
    }
    RoleCard("BACK-OFFS", "${scheme.backoffSets} ${if (scheme.backoffSets == 1) "set" else "sets"}", SetKind.BACKOFF.tint()) {
        IntStepper("Sets", scheme.backoffSets, "back-off sets", 1, SetScheme.MAX_BACKOFF_SETS) { onScheme(scheme.copy(backoffSets = it)) }
        val resolved = if (fixedMode) null else backoffCaption(DecimalInput.parse(weight), effective, lift)
        Row(Modifier.fillMaxWidth().heightIn(min = 46.dp), verticalAlignment = Alignment.CenterVertically) {
            RowLabel("Load", resolved, Modifier.weight(1f))
            Segment("${scheme.backoffPercent}% lighter", !fixedMode) { onFixedMode(false) }
            Segment("Fixed", fixedMode) { onFixedMode(true) }
        }
        if (fixedMode) {
            LoadTargetStepper(fixedText, loadGridLb(exerciseName), fixedText.isBlank(), worn, label = "Back-off load", onKgText = onFixedText)
        } else {
            IntStepper(
                "Lighter than the top by", scheme.backoffPercent, "back-off percent",
                SetScheme.PERCENT_RANGE.first, SetScheme.PERCENT_RANGE.last, step = 5, unit = "%",
            ) { onScheme(scheme.copy(backoffPercent = it)) }
        }
        IntRangeStepper("Reps", "back-off reps", scheme.backoffReps, 1, max) { onScheme(scheme.copy(backoffReps = it)) }
    }
}

/** What the back-offs' load works out to right now: "90 kg, rounded to your plates". */
private fun backoffCaption(topKg: Double?, scheme: SetScheme, lift: LiftLoading): String {
    val planned = SchemePlan.backoff(topKg, scheme.topReps.first, scheme, lift)
    val kg = planned.weightKg
    return when {
        kg != null -> {
            val where = when (planned.rounding) {
                Rounding.VEST_BLOCKS -> "your vest blocks"
                Rounding.BELT_PLATES -> "your belt plates"
                null -> if (lift.bodyweightLift) null else "your plates"
            }
            (if (lift.bodyweightLift) "+" else "") + Units.withUnit(kg) + (where?.let { ", rounded to $it" } ?: "")
        }
        lift.bodyweightLift -> "bodyweight, two reps fewer than the top set"
        else -> "set the top load first"
    }
}

/** A choice of two or three, drawn 34dp inside a 48dp hit area: the chosen one outlined in the accent. */
@Composable
private fun Segment(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .heightIn(min = 34.dp)
                .background(if (selected) IronvellumColors.VaultHigh else Color.Transparent, MaterialTheme.shapes.extraSmall)
                .inkBorder(if (selected) IronvellumColors.SystemGreen else IronvellumColors.Rune, MaterialTheme.shapes.extraSmall, 1.dp)
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = if (selected) IronvellumColors.Ink else IronvellumColors.InkMuted)
        }
    }
}

/**
 * Reps left as five chips, 0 1 2 3 4+, that read as one range: the chosen chips join into a single bar and the line under
 * them says it in words. "4+" is four up to ten. A lifter who reads effort as RPE sees the RPE equivalents (10 is none
 * left, 7 is three, 6 or less is four or more) and the same range, worded as an RPE.
 */
@Composable
private fun EffortChips(range: IntRange, onRange: (IntRange) -> Unit) {
    val rpe = EffortSetting.enabled && EffortSetting.scale == EffortScale.RPE
    val faces = if (rpe) listOf("10", "9", "8", "7", "≤6") else listOf("0", "1", "2", "3", "4+")
    val label = if (rpe) "RPE" else "Reps left"
    val from = minOf(range.first, 4)
    val to = minOf(range.last, 4)
    val base = MaterialTheme.shapes.extraSmall
    Column(
        Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "$label, ${rirRangeSpoken(range, rpe)}" },
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            RowLabel(label, null, Modifier.weight(1f))
            faces.forEachIndexed { i, face ->
                val on = i in from..to
                val shape = if (!on) base else base.copy(
                    topStart = if (i == from) base.topStart else ZeroCornerSize,
                    bottomStart = if (i == from) base.bottomStart else ZeroCornerSize,
                    topEnd = if (i == to) base.topEnd else ZeroCornerSize,
                    bottomEnd = if (i == to) base.bottomEnd else ZeroCornerSize,
                )
                Box(
                    Modifier
                        .width(34.dp)
                        .height(48.dp)
                        .clickable(role = Role.Button, onClickLabel = "Stop with ${rirRangeSpoken(i..(if (i == 4) 10 else i), rpe)}") {
                            onRange(nextRirRange(range, i))
                        }
                        .semantics {
                            this.selected = on
                            stateDescription = if (on) "In the range" else "Not in the range"
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    // A chosen chip fills its whole width so neighbours touch: the run reads as one bar.
                    Box(
                        Modifier
                            .width(if (on) 34.dp else 30.dp)
                            .height(30.dp)
                            .background(if (on) IronvellumColors.Emerald else IronvellumColors.VaultHigh, shape)
                            .then(if (on) Modifier else Modifier.inkBorder(IronvellumColors.Rune, shape, 1.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            face,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = if (on) MaterialTheme.colorScheme.onPrimary else IronvellumColors.Ink,
                        )
                    }
                }
            }
        }
        Text(rirRangeLine(range, rpe), style = MaterialTheme.typography.labelSmall, color = IronvellumColors.InkMuted)
    }
}

/**
 * The reps-left range after a tap on chip [i] (0 to 4, the last being "4 or more"). With one value chosen, a tap on
 * another stretches the range to cover both; with a range chosen, a tap starts over with that one value; a tap on the
 * only value keeps it. "4+" reaches ten.
 */
internal fun nextRirRange(range: IntRange, i: Int): IntRange {
    val a0 = minOf(range.first, 4)
    val b0 = minOf(range.last, 4)
    if (a0 != b0) return if (i == 4) 4..10 else i..i
    if (i == a0) return range
    val lo = minOf(a0, i)
    val hi = maxOf(b0, i)
    return lo..(if (hi == 4) (if (b0 == 4) maxOf(range.last, 4) else 10) else hi)
}

/** The RPE a count of reps left stands for: 3 left is RPE 7, 4 or more is RPE 6. */
private fun rpeOf(rir: Int) = 10 - minOf(rir, 4)

/** The line under the chips: "Stop with 0–1 reps left", "Stop around RPE 9–10". */
internal fun rirRangeLine(range: IntRange, rpe: Boolean): String {
    val lo = minOf(range.first, 4)
    val open = range.last >= 4
    return if (!rpe) {
        when {
            lo == 0 && open -> "Stop with any number of reps left"
            open -> "Stop with $lo or more reps left"
            lo == range.last -> "Stop with $lo ${if (lo == 1) "rep" else "reps"} left"
            else -> "Stop with $lo–${range.last} reps left"
        }
    } else {
        when {
            lo == 0 && open -> "Stop at any RPE"
            open -> "Stop around RPE ${rpeOf(lo)} or lower"
            lo == range.last -> "Stop around RPE ${rpeOf(lo)}"
            else -> "Stop around RPE ${rpeOf(range.last)}–${rpeOf(lo)}"
        }
    }
}

/** The range as a screen reader says it: "0 to 1", "2", "4 or more" (RPE: "RPE 9 to 10", "RPE 6 or lower"). */
internal fun rirRangeSpoken(range: IntRange, rpe: Boolean): String {
    val lo = minOf(range.first, 4)
    val open = range.last >= 4
    return if (!rpe) {
        when {
            lo == 0 && open -> "any number"
            open -> "$lo or more"
            lo == range.last -> "$lo"
            else -> "$lo to ${range.last}"
        }
    } else {
        when {
            lo == 0 && open -> "any RPE"
            open -> "RPE ${rpeOf(lo)} or lower"
            lo == range.last -> "RPE ${rpeOf(lo)}"
            else -> "RPE ${rpeOf(range.last)} to ${rpeOf(lo)}"
        }
    }
}

/** A whole-number range as two compact steppers. */
@Composable
private fun IntRangeStepper(label: String, noun: String, range: IntRange, min: Int, max: Int, onRange: (IntRange) -> Unit) = RangeStepperRow(
    label,
    from = StepperPart(
        range.first.toString(), "Fewer $noun", "More $noun",
        onLess = { onRange((range.first - 1).coerceAtLeast(min)..range.last) },
        onMore = { (range.first + 1).coerceAtMost(max).let { onRange(it..maxOf(it, range.last)) } },
    ),
    to = StepperPart(
        range.last.toString(), "Fewer $noun at the top of the range", "More $noun at the top of the range",
        onLess = { onRange(range.first..(range.last - 1).coerceAtLeast(range.first)) },
        onMore = { onRange(range.first..(range.last + 1).coerceAtMost(max)) },
    ),
)

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

/** A card of one row per set of a Custom scheme: tap a row to cycle its type; add or take off the last set. */
@Composable
private fun CustomSets(scheme: SetScheme, inSuperset: Boolean, onKinds: (List<SetKind>) -> Unit) {
    val options = listOf(SetKind.WORKING, SetKind.TOP, SetKind.BACKOFF, SetKind.DROP).filter { !(inSuperset && it == SetKind.DROP) }
    RoleCard("SET TYPES", "${scheme.custom.size} ${if (scheme.custom.size == 1) "set" else "sets"}", SetKind.TOP.tint()) {
        scheme.custom.forEachIndexed { index, kind ->
            // A drop follows a set, so the first set cannot be one.
            val allowed = options.filter { index > 0 || it != SetKind.DROP }
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 46.dp)
                    .clickable(role = Role.Button, onClickLabel = "Change set ${index + 1} type") {
                        val next = allowed[(allowed.indexOf(kind) + 1) % allowed.size]
                        onKinds(scheme.custom.mapIndexed { i, k -> if (i == index) next else k })
                    }
                    .semantics { contentDescription = "Set ${index + 1}, ${kind.label}" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Set ${index + 1}", style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.Ink, modifier = Modifier.weight(1f))
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
}

/** A set's bar in the "You'll do" drawing: top gold, back-offs mint, working sets the accent, drops its darker green. */
private fun SetKind.previewColor() = when (this) {
    SetKind.WORKING -> IronvellumColors.Emerald
    else -> barColor()
}

/** The short word over a bar in the "You'll do" drawing. */
private fun SetKind.roleWord() = when (this) {
    SetKind.TOP -> "TOP"
    SetKind.BACKOFF -> "BACK"
    SetKind.WORKING -> "SET"
    SetKind.DROP -> "DROP"
    else -> label.uppercase()
}

private const val DRAWN_SETS_MAX = 5
private val BAR_AREA = 64.dp

/**
 * The live "You'll do" drawing: one bar per working set the trial will begin with, from the same planner the trial uses.
 * Tall by load, coloured by role, the role over it and "reps x load" under it. Past five sets the bars stay and the
 * figures fold into one line per run of alike sets. The minutes are the sets' clock time, the rest after a top set the
 * scheme's own.
 */
@Composable
private fun YoullDo(scheme: SetScheme, sets: Int, reps: Int, kg: Double?, lift: LiftLoading, exerciseName: String) {
    val normalised = scheme.normalised()
    val planned = SchemePlan.plan(normalised, sets, reps, kg, lift)
    val bodyKg = if (lift.bodyweightLift) lift.movedBodyKg else 0.0
    val totals = planned.map { bodyKg + (it.weightKg ?: 0.0) }
    val heaviest = totals.maxOrNull() ?: 0.0
    val levels = SchemeText.bars(normalised, sets).filter { it.kind != SetKind.WARMUP }.map { it.level }
    // Squared, so a set a fifth lighter is visibly shorter; the heaviest is always the full height.
    fun height(i: Int): Float = (if (heaviest > 0.0) (totals[i] / heaviest).let { it * it }.toFloat() else levels.getOrNull(i) ?: 0.8f).coerceIn(0.25f, 1f)
    fun figure(set: PlannedSet, withUnit: Boolean): String {
        val repsText = set.range?.let { SchemeText.reps(it) } ?: set.reps.toString()
        val load = set.weightKg?.let { (if (lift.bodyweightLift) "+" else "") + (if (withUnit) Units.withUnit(it) else Units.format(it)) }
            ?: if (lift.bodyweightLift) "BW" else null
        return if (load == null) repsText else "$repsText × $load"
    }
    // shortcut: priced at the general rest windows; read the lifter's training mode (SessionClock.focusFor) if this must match the trial's estimate.
    val setSeconds = ProgramRules.setSeconds(TrainingFocus.GENERAL, exerciseName, null, kg != null && kg > 0.0)
    val seconds = planned.sumOf {
        when (it.kind) {
            SetKind.TOP -> ProgramRules.SET_WORK_SECONDS + SchemePlan.restAfterTop(normalised)
            SetKind.DROP -> ProgramRules.SET_WORK_SECONDS
            else -> setSeconds
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(IronvellumColors.Vault, MaterialTheme.shapes.extraSmall)
            .inkBorder(IronvellumColors.Rune, MaterialTheme.shapes.extraSmall, 1.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "You'll do".uppercase() + if (kg != null && kg > 0.0) " · ${Units.name}" else "",
                style = MaterialTheme.typography.labelMedium,
                color = IronvellumColors.InkMuted,
            )
            Text("~${SessionClock.minutes(seconds)} min", style = MaterialTheme.typography.labelMedium, color = IronvellumColors.InkMuted)
        }
        if (scheme.warmupRamp) Text("Warm-up ramp first", style = MaterialTheme.typography.labelSmall, color = IronvellumColors.InkMuted)
        if (planned.size <= DRAWN_SETS_MAX) {
            val narrow = planned.size > 3
            Row(Modifier.fillMaxWidth().height(BAR_AREA + if (narrow) 66.dp else 46.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                planned.forEachIndexed { i, set ->
                    val colour = set.kind.previewColor()
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.Bottom)) {
                        Text(set.kind.roleWord(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = colour.copy(alpha = 1f))
                        Box(Modifier.fillMaxWidth().height(BAR_AREA * height(i)).background(colour.copy(alpha = 0.9f), MaterialTheme.shapes.extraSmall))
                        Text(
                            figure(set, withUnit = false).let { if (narrow) it.replace(" × ", "\n× ") else it },
                            style = MaterialTheme.typography.bodySmall,
                            color = IronvellumColors.Ink,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        } else {
            SchemeBars(
                planned.mapIndexed { i, set -> SchemeBar(set.kind, height(i)) },
                Modifier.fillMaxWidth(),
                height = 40.dp,
            )
            // One line per run of alike sets: "Working, 4 sets" over "8 x 60 kg".
            var i = 0
            while (i < planned.size) {
                val set = planned[i]
                val line = figure(set, withUnit = true)
                var end = i
                while (end + 1 < planned.size && planned[end + 1].kind == set.kind && figure(planned[end + 1], withUnit = true) == line) end++
                val count = end - i + 1
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        set.kind.label + if (count > 1) ", $count sets" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = set.kind.tint(),
                        modifier = Modifier.weight(1f),
                    )
                    Text(line, style = MaterialTheme.typography.bodySmall, color = IronvellumColors.Ink)
                }
                i = end + 1
            }
        }
    }
}
