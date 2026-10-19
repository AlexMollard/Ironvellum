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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ironvellum.app.R
import com.ironvellum.app.data.EffortSetting
import com.ironvellum.app.data.Units
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
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.domain.WarmupRamp
import com.ironvellum.app.ui.asString
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
    val loadLabel = stringResource(if (lift.bodyweightLift) R.string.setssheet_load_added_label else R.string.setssheet_load_label)
    val loadEmpty = LoadTargetStepperPlaceholder(lift.bodyweightLift)

    IronvellumDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (entry.exerciseName.isBlank()) {
                    stringResource(R.string.setssheet_title_blank)
                } else {
                    UiText.res(R.string.setssheet_title, entry.exerciseName).asString()
                },
            )
        },
        text = {
            // Tall enough to fill the sheet whatever the scheme, so switching schemes never changes its height.
            Column(Modifier.heightIn(min = LocalConfiguration.current.screenHeightDp.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val tiles: @Composable (List<SchemeType>) -> Unit = { types ->
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        types.forEach { type ->
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
                }
                // Four tiles share one row until large text makes their names break inside a word: then two rows of two.
                if (LocalDensity.current.fontScale > 1.3f) {
                    tiles(SchemeType.entries.take(2))
                    tiles(SchemeType.entries.drop(2))
                } else {
                    tiles(SchemeType.entries)
                }
                if (inSuperset) Text(stringResource(R.string.setssheet_not_in_superset), style = MaterialTheme.typography.labelSmall, color = IronvellumColors.InkMuted)
                if (suggestion.type != scheme.type && !(inSuperset && suggestion.type == SchemeType.DROPS)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                        Text(stringResource(R.string.setssheet_suggested).uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = IronvellumColors.SystemGreen)
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
                            stringResource(if (scheme.type == SchemeType.DROPS) R.string.setssheet_role_working else R.string.setssheet_role_every),
                            if (scheme.type == SchemeType.CUSTOM) null else UiText.plural(R.plurals.common_sets_count, setCount).asString(),
                            SetKind.WORKING.barColor(),
                        ) {
                            if (scheme.type != SchemeType.CUSTOM) WholeStepper(stringResource(R.string.setssheet_sets_label), sets, stringResource(R.string.setssheet_noun_sets), false, 2, 30) { sets = it }
                            LoadTargetStepper(weight, loadGridLb(entry.exerciseName), false, worn, label = loadLabel, emptyText = loadEmpty) { weight = it }
                            if (scheme.type == SchemeType.CUSTOM) {
                                WholeStepper(stringResource(R.string.setssheet_reps_label), reps, stringResource(R.string.setssheet_noun_reps), false, 3, 500) { reps = it }
                            } else {
                                val fromPart = StepperPart(
                                    reps, stringResource(R.string.setssheet_reps_fewer_description), stringResource(R.string.setssheet_reps_more_description),
                                    onLess = { reps = stepWhole(reps, -1, 500) }, onMore = { reps = stepWhole(reps, 1, 500) },
                                    onValue = { reps = it }, maxDigits = 3,
                                )
                                if (repsUp != null && repsUp!! > repsFrom) {
                                    RangeStepperRow(
                                        stringResource(R.string.setssheet_reps_label),
                                        from = fromPart,
                                        to = StepperPart(
                                            repsUp.toString(), stringResource(R.string.setssheet_reps_top_lower_description), stringResource(R.string.setssheet_reps_top_higher_description),
                                            onLess = { repsUp = SetScheme.stepRepsUp(repsFrom, repsUp, -1) },
                                            onMore = { repsUp = SetScheme.stepRepsUp(repsFrom, repsUp, 1) },
                                        ),
                                    )
                                } else {
                                    // One figure until the lifter asks for a range; the range's top steps down to this again.
                                    Row(Modifier.fillMaxWidth().heightIn(min = ROW_MIN_HEIGHT), verticalAlignment = Alignment.CenterVertically) {
                                        RowLabel(stringResource(R.string.setssheet_reps_label), null, Modifier.weight(1f))
                                        Box(
                                            Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClickLabel = stringResource(R.string.setssheet_reps_range_add_description)) {
                                                repsUp = SetScheme.stepRepsUp(repsFrom, null, 2)
                                            }.padding(horizontal = 8.dp),
                                            contentAlignment = Alignment.Center,
                                        ) { Text(stringResource(R.string.setssheet_reps_range_add_button), style = MaterialTheme.typography.bodySmall, color = IronvellumColors.SystemGreen) }
                                        StepperGroup(stringResource(R.string.setssheet_reps_label), fromPart)
                                    }
                                }
                            }
                        }
                        if (scheme.type == SchemeType.DROPS) {
                            RoleCard(stringResource(R.string.setssheet_role_drops), dropsHeader(scheme).asString(), SetKind.DROP.tint()) {
                                IntStepper(
                                    stringResource(R.string.setssheet_dropsets_label), scheme.dropSets,
                                    stringResource(R.string.setssheet_dropsets_fewer_description), stringResource(R.string.setssheet_dropsets_more_description),
                                    1, setCount,
                                ) { scheme = scheme.copy(dropSets = it) }
                                IntStepper(
                                    stringResource(R.string.setssheet_dropcount_label), scheme.dropCount,
                                    stringResource(R.string.setssheet_dropcount_fewer_description), stringResource(R.string.setssheet_dropcount_more_description),
                                    1, SetScheme.MAX_DROPS,
                                ) { scheme = scheme.copy(dropCount = it) }
                                IntStepper(
                                    stringResource(R.string.setssheet_droppercent_label), scheme.dropPercent,
                                    stringResource(R.string.setssheet_droppercent_fewer_description), stringResource(R.string.setssheet_droppercent_more_description),
                                    SetScheme.PERCENT_RANGE.first, SetScheme.PERCENT_RANGE.last, step = 5, unit = "%",
                                ) { scheme = scheme.copy(dropPercent = it) }
                            }
                        }
                        if (scheme.type == SchemeType.CUSTOM) CustomSets(scheme, inSuperset) { scheme = scheme.copy(custom = it) }
                    }
                }
                MoreLink(stringResource(if (scheme.type == SchemeType.TOP_BACKOFF) R.string.setssheet_rest_ramp_label else R.string.setssheet_ramp_label), more) { more = !more }
                if (more) {
                    RoleCard(stringResource(if (scheme.type == SchemeType.TOP_BACKOFF) R.string.setssheet_rest_ramp_label else R.string.setssheet_ramp_label), null, IronvellumColors.Bracket) {
                        if (scheme.type == SchemeType.TOP_BACKOFF) {
                            IntStepper(
                                stringResource(R.string.setssheet_toprest_label), scheme.restAfterTopSec,
                                stringResource(R.string.setssheet_toprest_fewer_description), stringResource(R.string.setssheet_toprest_more_description),
                                SetScheme.REST_RANGE_SEC.first, SetScheme.REST_RANGE_SEC.last, step = 15, text = RestTimer.clock(scheme.restAfterTopSec),
                            ) { scheme = scheme.copy(restAfterTopSec = it) }
                        }
                        SettingsSwitchRow(
                            label = stringResource(R.string.setssheet_ramp_label),
                            caption = when {
                                (DecimalInput.parse(weight) ?: 0.0) > 0.0 -> UiText.themed(R.string.setssheet_ramp_caption, R.string.setssheet_ramp_caption__t).asString()
                                WarmupRamp.isBarbell(entry.exerciseName) -> stringResource(R.string.setssheet_ramp_caption_bar)
                                else -> stringResource(R.string.setssheet_ramp_caption_none)
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
                label = stringResource(R.string.common_done),
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
        dismissButton = { IronvellumButton(label = stringResource(R.string.common_cancel), onClick = onDismiss, quiet = true) },
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

@Composable
private fun SchemeType.tileLabel() = stringResource(
    when (this) {
        SchemeType.STRAIGHT -> R.string.setssheet_tile_straight
        SchemeType.TOP_BACKOFF -> R.string.setssheet_tile_top_backoff
        SchemeType.DROPS -> R.string.setssheet_tile_drops
        SchemeType.CUSTOM -> R.string.setssheet_tile_custom
    },
)

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
internal fun dropsHeader(scheme: SetScheme): UiText =
    if (scheme.dropSets == 1) {
        UiText.plural(R.plurals.setssheet_drops_after_last, scheme.dropCount)
    } else {
        UiText.plural(R.plurals.setssheet_drops_after_each, scheme.dropCount, scheme.dropCount, scheme.dropSets)
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
                title.uppercase(),
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
            .clickable(role = Role.Button, onClickLabel = UiText.res(if (open) R.string.setssheet_more_hide_description else R.string.setssheet_more_show_description, label).asString(), onClick = onClick),
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
    RoleCard(stringResource(R.string.setssheet_role_top), UiText.plural(R.plurals.common_sets_count, 1).asString(), SetKind.TOP.tint()) {
        LoadTargetStepper(
            weight, loadGridLb(exerciseName), false, worn,
            label = stringResource(if (bodyweight) R.string.setssheet_load_added_label else R.string.setssheet_load_label), emptyText = LoadTargetStepperPlaceholder(bodyweight), onKgText = onWeight,
        )
        IntRangeStepper(
            stringResource(R.string.setssheet_reps_label), scheme.topReps, 1, max,
            stringResource(R.string.setssheet_topreps_fewer_description), stringResource(R.string.setssheet_topreps_more_description),
            stringResource(R.string.setssheet_topreps_top_fewer_description), stringResource(R.string.setssheet_topreps_top_more_description),
        ) { onScheme(scheme.copy(topReps = it)) }
        EffortChips(scheme.topRir) { onScheme(scheme.copy(topRir = it)) }
    }
    RoleCard(stringResource(R.string.setssheet_role_backoffs), UiText.plural(R.plurals.common_sets_count, scheme.backoffSets).asString(), SetKind.BACKOFF.tint()) {
        IntStepper(
            stringResource(R.string.setssheet_sets_label), scheme.backoffSets,
            stringResource(R.string.setssheet_backoffsets_fewer_description), stringResource(R.string.setssheet_backoffsets_more_description),
            1, SetScheme.MAX_BACKOFF_SETS,
        ) { onScheme(scheme.copy(backoffSets = it)) }
        val resolved = if (fixedMode) null else backoffCaption(DecimalInput.parse(weight), effective, lift).asString()
        Row(Modifier.fillMaxWidth().heightIn(min = 46.dp), verticalAlignment = Alignment.CenterVertically) {
            RowLabel(stringResource(R.string.setssheet_load_label), resolved, Modifier.weight(1f))
            Segment(UiText.res(R.string.setssheet_backoff_lighter, scheme.backoffPercent).asString(), !fixedMode) { onFixedMode(false) }
            Segment(stringResource(R.string.setssheet_backoff_fixed), fixedMode) { onFixedMode(true) }
        }
        if (fixedMode) {
            LoadTargetStepper(fixedText, loadGridLb(exerciseName), fixedText.isBlank(), worn, label = stringResource(R.string.setssheet_backoff_load_label), onKgText = onFixedText)
        } else {
            IntStepper(
                stringResource(R.string.setssheet_backoffpercent_label), scheme.backoffPercent,
                stringResource(R.string.setssheet_backoffpercent_fewer_description), stringResource(R.string.setssheet_backoffpercent_more_description),
                SetScheme.PERCENT_RANGE.first, SetScheme.PERCENT_RANGE.last, step = 5, unit = "%",
            ) { onScheme(scheme.copy(backoffPercent = it)) }
        }
        IntRangeStepper(
            stringResource(R.string.setssheet_reps_label), scheme.backoffReps, 1, max,
            stringResource(R.string.setssheet_backoffreps_fewer_description), stringResource(R.string.setssheet_backoffreps_more_description),
            stringResource(R.string.setssheet_backoffreps_top_fewer_description), stringResource(R.string.setssheet_backoffreps_top_more_description),
        ) { onScheme(scheme.copy(backoffReps = it)) }
    }
}

/** What the back-offs' load works out to right now: "90 kg, rounded to your plates". */
private fun backoffCaption(topKg: Double?, scheme: SetScheme, lift: LiftLoading): UiText {
    val planned = SchemePlan.backoff(topKg, scheme.topReps.first, scheme, lift)
    val kg = planned.weightKg
    return when {
        kg != null -> {
            val figure = (if (lift.bodyweightLift) "+" else "") + Units.withUnit(kg)
            when (planned.rounding) {
                Rounding.VEST_BLOCKS -> UiText.res(R.string.setssheet_backoff_rounded_vest, figure)
                Rounding.BELT_PLATES -> UiText.res(R.string.setssheet_backoff_rounded_belt, figure)
                null -> if (lift.bodyweightLift) UiText.Raw(figure) else UiText.res(R.string.setssheet_backoff_rounded_plates, figure)
            }
        }
        lift.bodyweightLift -> UiText.res(R.string.setssheet_backoff_bodyweight)
        else -> UiText.res(R.string.setssheet_backoff_need_top)
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
    val label = stringResource(if (rpe) R.string.setssheet_effort_rpe_label else R.string.setssheet_effort_reps_left_label)
    val description = UiText.res(
        if (rpe) R.string.setssheet_effort_rpe_description else R.string.setssheet_effort_reps_left_description,
        rirRangeSpoken(range, rpe),
    ).asString()
    val inRange = stringResource(R.string.setssheet_effort_in_range)
    val outOfRange = stringResource(R.string.setssheet_effort_out_of_range)
    val from = minOf(range.first, 4)
    val to = minOf(range.last, 4)
    val base = MaterialTheme.shapes.extraSmall
    Column(
        Modifier
            .fillMaxWidth()
            .semantics { contentDescription = description },
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
                        .clickable(role = Role.Button, onClickLabel = UiText.res(R.string.setssheet_effort_stop_description, rirRangeSpoken(i..(if (i == 4) 10 else i), rpe)).asString()) {
                            onRange(nextRirRange(range, i))
                        }
                        .semantics {
                            this.selected = on
                            stateDescription = if (on) inRange else outOfRange
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
        Text(rirRangeLine(range, rpe).asString(), style = MaterialTheme.typography.labelSmall, color = IronvellumColors.InkMuted)
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
internal fun rirRangeLine(range: IntRange, rpe: Boolean): UiText {
    val lo = minOf(range.first, 4)
    val open = range.last >= 4
    return if (!rpe) {
        when {
            lo == 0 && open -> UiText.res(R.string.setssheet_line_reps_any)
            open -> UiText.res(R.string.setssheet_line_reps_or_more, lo)
            lo == range.last -> UiText.plural(R.plurals.setssheet_line_reps_left, lo)
            else -> UiText.res(R.string.setssheet_line_reps_range, lo, range.last)
        }
    } else {
        when {
            lo == 0 && open -> UiText.res(R.string.setssheet_line_rpe_any)
            open -> UiText.res(R.string.setssheet_line_rpe_or_lower, rpeOf(lo))
            lo == range.last -> UiText.res(R.string.setssheet_line_rpe_one, rpeOf(lo))
            else -> UiText.res(R.string.setssheet_line_rpe_range, rpeOf(range.last), rpeOf(lo))
        }
    }
}

/** The range as a screen reader says it: "0 to 1", "2", "4 or more" (RPE: "RPE 9 to 10", "RPE 6 or lower"). */
internal fun rirRangeSpoken(range: IntRange, rpe: Boolean): UiText {
    val lo = minOf(range.first, 4)
    val open = range.last >= 4
    return if (!rpe) {
        when {
            lo == 0 && open -> UiText.res(R.string.setssheet_spoken_reps_any)
            open -> UiText.res(R.string.setssheet_spoken_reps_or_more, lo)
            lo == range.last -> UiText.Raw(lo.toString())
            else -> UiText.res(R.string.setssheet_spoken_reps_range, lo, range.last)
        }
    } else {
        when {
            lo == 0 && open -> UiText.res(R.string.setssheet_spoken_rpe_any)
            open -> UiText.res(R.string.setssheet_spoken_rpe_or_lower, rpeOf(lo))
            lo == range.last -> UiText.res(R.string.setssheet_spoken_rpe_one, rpeOf(lo))
            else -> UiText.res(R.string.setssheet_spoken_rpe_range, rpeOf(range.last), rpeOf(lo))
        }
    }
}

/** A whole-number range as two compact steppers. */
@Composable
private fun IntRangeStepper(
    label: String,
    range: IntRange,
    min: Int,
    max: Int,
    fromLess: String,
    fromMore: String,
    toLess: String,
    toMore: String,
    onRange: (IntRange) -> Unit,
) = RangeStepperRow(
    label,
    from = StepperPart(
        range.first.toString(), fromLess, fromMore,
        onLess = { onRange((range.first - 1).coerceAtLeast(min)..range.last) },
        onMore = { (range.first + 1).coerceAtMost(max).let { onRange(it..maxOf(it, range.last)) } },
    ),
    to = StepperPart(
        range.last.toString(), toLess, toMore,
        onLess = { onRange(range.first..(range.last - 1).coerceAtLeast(range.first)) },
        onMore = { onRange(range.first..(range.last + 1).coerceAtMost(max)) },
    ),
)

/** A whole number between [min] and [max], moved in [step]s by the pads; [text] overrides how it reads ("3:00"). */
@Composable
private fun IntStepper(
    label: String,
    value: Int,
    less: String,
    more: String,
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
    less = less,
    more = more,
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
    less = UiText.res(R.string.setssheet_repsup_lower_description, noun).asString(),
    more = UiText.res(R.string.setssheet_repsup_higher_description, noun).asString(),
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
    RoleCard(stringResource(R.string.setssheet_role_types), UiText.plural(R.plurals.common_sets_count, scheme.custom.size).asString(), SetKind.TOP.tint()) {
        scheme.custom.forEachIndexed { index, kind ->
            // A drop follows a set, so the first set cannot be one.
            val allowed = options.filter { index > 0 || it != SetKind.DROP }
            val rowDescription = UiText.res(R.string.setssheet_custom_row_description, index + 1, kind.label).asString()
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 46.dp)
                    .clickable(role = Role.Button, onClickLabel = UiText.res(R.string.setssheet_custom_change_description, index + 1).asString()) {
                        val next = allowed[(allowed.indexOf(kind) + 1) % allowed.size]
                        onKinds(scheme.custom.mapIndexed { i, k -> if (i == index) next else k })
                    }
                    .semantics { contentDescription = rowDescription },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(UiText.res(R.string.setssheet_custom_set, index + 1).asString(), style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.Ink, modifier = Modifier.weight(1f))
                Text(kind.label + "  ▾", style = MaterialTheme.typography.bodyMedium, color = kind.tint())
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (scheme.custom.size < SetScheme.MAX_CUSTOM_SETS) {
                TextAction(stringResource(R.string.setssheet_custom_add_button), { onKinds(scheme.custom + SetKind.WORKING) })
            }
            if (scheme.custom.size > 1) {
                TextAction(stringResource(R.string.setssheet_custom_remove_button), { onKinds(scheme.custom.dropLast(1)) })
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
@Composable
private fun SetKind.roleWord() = when (this) {
    SetKind.TOP -> stringResource(R.string.setssheet_word_top).uppercase()
    SetKind.BACKOFF -> stringResource(R.string.setssheet_word_back).uppercase()
    SetKind.WORKING -> stringResource(R.string.setssheet_word_set).uppercase()
    SetKind.DROP -> stringResource(R.string.setssheet_word_drop).uppercase()
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
                stringResource(R.string.setssheet_youll_do).uppercase() + if (kg != null && kg > 0.0) " · ${Units.name}" else "",
                style = MaterialTheme.typography.labelMedium,
                color = IronvellumColors.InkMuted,
            )
            Text(UiText.res(R.string.setssheet_youll_minutes, SessionClock.minutes(seconds)).asString(), style = MaterialTheme.typography.labelMedium, color = IronvellumColors.InkMuted)
        }
        if (scheme.warmupRamp) Text(stringResource(R.string.setssheet_youll_ramp_first), style = MaterialTheme.typography.labelSmall, color = IronvellumColors.InkMuted)
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
                        if (count > 1) UiText.plural(R.plurals.setssheet_run_label, count, set.kind.label, count).asString() else set.kind.label,
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
