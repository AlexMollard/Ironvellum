package com.ironvellum.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ironvellum.app.domain.DecimalInput
import com.ironvellum.app.ui.theme.IronvellumColors
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
internal fun stepDecimal(current: String, delta: Int, step: Double): String {
    val next = Math.round(((DecimalInput.parse(current) ?: 0.0) + delta * step) * 100) / 100.0
    return if (next <= 0.0) "" else formatSteppedFigure(next.coerceAtMost(MAX_LOAD_KG))
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
    onLess = { onValue(stepDecimal(value, -1, step)) },
    onMore = { onValue(stepDecimal(value, 1, step)) },
)

/**
 * A label, then − value +. The value is still typeable when [onValue] is given, so 62.5 kg does
 * not take twenty taps; without it the figure is plain text between the pads.
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
) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted, modifier = Modifier.weight(1f))
        StepButton("−", less, onLess)
        Box(Modifier.width(56.dp).heightIn(min = 44.dp), contentAlignment = Alignment.Center) {
            val figure = MaterialTheme.typography.titleMedium.copy(
                color = if (error) IronvellumColors.DangerRed else IronvellumColors.Ink,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            if (onValue != null) {
                BasicTextField(
                    value = value,
                    onValueChange = { input ->
                        onValue(
                            if (decimal) DecimalInput.sanitize(input, maxDecimals = 2, maxLength = 7)
                            else DecimalInput.sanitizeWhole(input, maxDigits),
                        )
                    },
                    singleLine = true,
                    textStyle = figure,
                    cursorBrush = SolidColor(IronvellumColors.SystemGreen),
                    keyboardOptions = if (decimal) decimalKeyboard(ImeAction.Next) else wholeKeyboard(ImeAction.Next),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
                )
            } else {
                Text(
                    value,
                    style = figure,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "$label, $value" },
                )
            }
            if (value.isEmpty()) {
                Text(
                    "–",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (error) IronvellumColors.DangerRed else IronvellumColors.InkMuted,
                )
            }
        }
        StepButton("+", more, onMore)
        // Every row keeps the unit's slot so the figures line up down the card.
        Text(
            unit.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = IronvellumColors.InkMuted,
            modifier = Modifier.width(28.dp).padding(start = 4.dp),
        )
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
