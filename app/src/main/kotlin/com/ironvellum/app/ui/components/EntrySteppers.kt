package com.ironvellum.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ironvellum.app.domain.DecimalInput
import com.ironvellum.app.domain.LoadUnit
import com.ironvellum.app.domain.Loads
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.inkBorder
import com.ironvellum.app.ui.theme.inkStroke
import com.ironvellum.app.ui.train.MAX_LOAD_KG

/**
 * The target-stepping furniture shared by every place a rite's sets, reps or load are edited: the
 * rite editor, the Forge's open exercise row and first-run's cycle review. One card, one stepper,
 * so the three can never drift into looking like different apps.
 */

/** One tap of a count stepper: [delta] more (or fewer), kept within 1..[max]. */
internal fun stepWhole(current: String, delta: Int, max: Int): String =
    ((DecimalInput.parseWhole(current) ?: 0) + delta).coerceIn(1, max).toString()

/** One tap of a kg or km stepper: [delta] steps of [step]; at or below zero it clears (bodyweight, no target). */
internal fun stepDecimal(current: String, delta: Int, step: Double, max: Double = MAX_LOAD_KG): String {
    val next = Math.round(((DecimalInput.parse(current) ?: 0.0) + delta * step) * 100) / 100.0
    return if (next <= 0.0) "" else formatSteppedFigure(next.coerceAtMost(max))
}

/**
 * One tap of a load stepper that walks a grid: the next multiple of [grid] up or down from [current], so a load typed
 * or progressed off the grid (11.5 on a 2.5 grid) lands on it (10, then 7.5) instead of jumping over its round values
 * (11.5 to 9 and back to 11.5). On the grid it is one [grid] step, as [stepDecimal]; at or below zero it clears.
 * [grid] is in the unit of [current], so the lb stepper passes its own grid on the displayed figure.
 */
internal fun stepOnGrid(current: String, delta: Int, grid: Double, max: Double = MAX_LOAD_KG): String {
    val value = DecimalInput.parse(current) ?: 0.0
    val next = if (delta > 0) Loads.stepUp(value, LoadUnit.KG, grid, grid) else Loads.stepDown(value, LoadUnit.KG, grid, grid)
    if (next == null || next <= 0.0) return ""
    return formatSteppedFigure((Math.round(next * 100) / 100.0).coerceAtMost(max))
}

/** "60" for a whole figure, "62.5" otherwise. */
internal fun formatSteppedFigure(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

/**
 * An open exercise's card: the exercise's name as its header (a tap folds it), the [content]
 * (steppers, a reason, a field) and a footer of quiet [actions] under a hairline. [leading] is the
 * rite editor's reorder grip; without one the name sits where the steppers' labels do.
 */
@Composable
internal fun EntryCard(
    name: String,
    foldLabel: String,
    onFold: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    InkPanel(
        modifier.fillMaxWidth().padding(vertical = 6.dp),
        contentPadding = PaddingValues(start = 4.dp, end = 16.dp, top = 6.dp, bottom = 4.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(onClickLabel = foldLabel, role = Role.Button, onClick = onFold),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading?.invoke()
            Text(
                name,
                style = MaterialTheme.typography.titleMedium,
                color = IronvellumColors.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = if (leading == null) 12.dp else 0.dp),
            )
        }
        Column(Modifier.padding(start = 12.dp), content = content)
        InkDivider()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, content = actions)
    }
}

/** A quiet, 44dp-tall text action; [description] is what a screen reader says when the label alone is not enough. */
@Composable
internal fun TextAction(label: String, onClick: () -> Unit, description: String = label) {
    Box(
        Modifier
            .heightIn(min = 44.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { if (description != label) contentDescription = description }
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted)
    }
}

/** A typeable count (sets, reps, seconds); [subject] names whose count it is for a screen reader ("Fewer sets for Dip"). */
@Composable
internal fun WholeStepper(
    label: String,
    value: String,
    noun: String,
    error: Boolean,
    maxDigits: Int,
    max: Int,
    subject: String? = null,
    onValue: (String) -> Unit,
) = StepperRow(
    label = label,
    value = value,
    unit = null,
    less = "Fewer $noun" + subject?.let { " for $it" }.orEmpty(),
    more = "More $noun" + subject?.let { " for $it" }.orEmpty(),
    error = error,
    decimal = false,
    maxDigits = maxDigits,
    onValue = onValue,
    onLess = { onValue(stepWhole(value, -1, max)) },
    onMore = { onValue(stepWhole(value, 1, max)) },
)

@Composable
internal fun DecimalStepper(
    label: String,
    value: String,
    unit: String,
    less: String,
    more: String,
    step: Double,
    error: Boolean,
    max: Double = MAX_LOAD_KG,
    /** A load: the pads walk the [step] grid ([stepOnGrid]) rather than add it to whatever is there. */
    onGrid: Boolean = false,
    emptyText: String = "–",
    caption: String? = null,
    onValue: (String) -> Unit,
) = StepperRow(
    label = label,
    value = value,
    unit = unit,
    less = less,
    more = more,
    error = error,
    decimal = true,
    maxDigits = 0,
    onValue = onValue,
    onLess = { onValue(if (onGrid) stepOnGrid(value, -1, step, max) else stepDecimal(value, -1, step, max)) },
    onMore = { onValue(if (onGrid) stepOnGrid(value, 1, step, max) else stepDecimal(value, 1, step, max)) },
    emptyText = emptyText,
    caption = caption,
)

/**
 * A label, then - value +. The value is still typeable when [onValue] is given, so 62.5 kg does
 * not take twenty taps; without it the figure is plain text between the pads. [caption] is a quiet
 * second line under the label ("stop with this many in hand"). The pads draw 34dp inside a 40 x 48dp hit area,
 * the figure carries an underline directly under its digits, and the unit sits after the figure, outside it,
 * so every row's + lines up at the row's right edge.
 */
@Composable
internal fun StepperRow(
    label: String,
    value: String,
    unit: String?,
    less: String,
    more: String,
    error: Boolean,
    decimal: Boolean,
    maxDigits: Int,
    onValue: ((String) -> Unit)?,
    onLess: () -> Unit,
    onMore: () -> Unit,
    /** What an empty figure reads: a dash between two minus-looking pads read as a third one, so loads say "none". */
    emptyText: String = "–",
    caption: String? = null,
) {
    Row(Modifier.fillMaxWidth().heightIn(min = ROW_MIN_HEIGHT), verticalAlignment = Alignment.CenterVertically) {
        RowLabel(label, caption, Modifier.weight(1f))
        StepperGroup(label, StepperPart(value, less, more, onLess, onMore, onValue, maxDigits), unit, error, decimal, emptyText)
    }
}

/** The 46dp row height the compact steppers and the Sets sheet's rows share. */
internal val ROW_MIN_HEIGHT = 46.dp

/** A row's label, with its quiet [caption] under it. */
@Composable
internal fun RowLabel(label: String, caption: String?, modifier: Modifier = Modifier) {
    Column(modifier.padding(end = 8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.Ink)
        if (caption != null) Text(caption, style = MaterialTheme.typography.labelSmall, color = IronvellumColors.InkMuted)
    }
}

/** One stepper's figure and what its pads do; [onValue] makes the figure typeable. */
internal class StepperPart(
    val value: String,
    val less: String,
    val more: String,
    val onLess: () -> Unit,
    val onMore: () -> Unit,
    val onValue: ((String) -> Unit)? = null,
    val maxDigits: Int = 0,
)

/** - figure + as one compact unit: no label, so a range can put two of them in a row. */
@Composable
internal fun StepperGroup(
    name: String,
    part: StepperPart,
    unit: String? = null,
    error: Boolean = false,
    decimal: Boolean = false,
    emptyText: String = "–",
    valueWidth: Dp = 48.dp,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CompactStepButton("−", part.less, part.onLess)
        val underline = IronvellumColors.InkMuted.copy(alpha = 0.55f)
        Box(
            Modifier
                .width(valueWidth)
                .height(30.dp)
                .drawBehind {
                    val y = size.height - 3.dp.toPx()
                    val inset = if (size.width > 40.dp.toPx()) 10.dp.toPx() else 4.dp.toPx()
                    inkStroke(Offset(inset, y), Offset(size.width - inset, y), underline, 1.dp.toPx())
                },
            contentAlignment = Alignment.Center,
        ) {
            val figure = MaterialTheme.typography.titleMedium.copy(
                color = if (error) IronvellumColors.DangerRed else IronvellumColors.Ink,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            val typed = part.onValue
            if (typed != null) {
                BasicTextField(
                    value = part.value,
                    onValueChange = { input ->
                        typed(
                            if (decimal) DecimalInput.sanitize(input, maxDecimals = 2, maxLength = 7)
                            else DecimalInput.sanitizeWhole(input, part.maxDigits),
                        )
                    },
                    singleLine = true,
                    textStyle = figure,
                    cursorBrush = SolidColor(IronvellumColors.SystemGreen),
                    keyboardOptions = if (decimal) decimalKeyboard(ImeAction.Next) else wholeKeyboard(ImeAction.Next),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = name },
                )
            } else {
                Text(
                    part.value,
                    style = figure,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "$name, ${part.value}" },
                )
            }
            if (part.value.isEmpty()) {
                Text(
                    emptyText,
                    style = if (emptyText.length > 1) MaterialTheme.typography.bodySmall else MaterialTheme.typography.titleMedium,
                    color = if (error) IronvellumColors.DangerRed else IronvellumColors.InkMuted,
                )
            }
        }
        if (unit != null && part.value.isNotEmpty()) {
            Text(unit, style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted, modifier = Modifier.padding(start = 3.dp))
        }
        CompactStepButton("+", part.more, part.onMore)
    }
}

/**
 * A label, then a range as two steppers around a dash: "Reps  - 4 +  -  - 6 +". The two figures are narrower than a
 * lone stepper's so the row fits a 360dp phone inside a card.
 */
@Composable
internal fun RangeStepperRow(label: String, from: StepperPart, to: StepperPart, caption: String? = null, toEmpty: String = "–") {
    Row(Modifier.fillMaxWidth().heightIn(min = ROW_MIN_HEIGHT), verticalAlignment = Alignment.CenterVertically) {
        RowLabel(label, caption, Modifier.weight(1f))
        StepperGroup("$label, from", from, valueWidth = RANGE_VALUE_WIDTH)
        Text("–", style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.InkMuted)
        StepperGroup("$label, up to", to, emptyText = toEmpty, valueWidth = RANGE_VALUE_WIDTH)
    }
}

private val RANGE_VALUE_WIDTH = 32.dp

/** A compact stepper pad: 34dp drawn, inside a 40 x 48dp hit area, so a range of two steppers fits a phone. */
@Composable
internal fun CompactStepButton(glyph: String, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .width(40.dp)
            .height(48.dp)
            .clickable(onClickLabel = description, onClick = onClick)
            .semantics {
                contentDescription = description
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .background(IronvellumColors.VaultHigh, MaterialTheme.shapes.extraSmall)
                .inkBorder(IronvellumColors.Rune, MaterialTheme.shapes.extraSmall, 1.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(glyph, style = MaterialTheme.typography.titleMedium, color = IronvellumColors.Ink)
        }
    }
}

/** One fixed 44dp stepper control; the glyph alone reads to TalkBack as a dash or a cross. */
@Composable
internal fun StepButton(glyph: String, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clickable(onClickLabel = description, onClick = onClick)
            .semantics {
                contentDescription = description
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, style = MaterialTheme.typography.titleLarge, color = IronvellumColors.InkMuted)
    }
}
