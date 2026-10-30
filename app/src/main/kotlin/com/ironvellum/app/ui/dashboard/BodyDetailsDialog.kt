package com.ironvellum.app.ui.dashboard

import com.ironvellum.app.R
import androidx.compose.ui.res.stringResource
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.ui.asString
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.ironvellum.app.data.Units
import com.ironvellum.app.domain.BodyLimits
import com.ironvellum.app.domain.DecimalInput
import com.ironvellum.app.domain.Loads
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.components.IronvellumDialog
import com.ironvellum.app.ui.components.decimalKeyboard
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.ironvellumFieldColors

/**
 * Today's one place to add the body figures the scores wait for: height and weight in one sheet, only the
 * ones still missing. Both are checked against the ranges the repository enforces before Save is on, and a
 * refusal ([error]) is said under the fields. Height is saved before the weight (see [DashboardViewModel.saveBody]).
 */
@Composable
internal fun BodyDetailsDialog(
    gap: BodyGap,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (heightCm: Double?, weightKg: Double?) -> Unit,
) {
    val needHeight = gap != BodyGap.WEIGHT
    val needWeight = gap != BodyGap.HEIGHT
    var height by rememberSaveable { mutableStateOf("") }
    var weight by rememberSaveable { mutableStateOf("") }
    val heightReading = if (needHeight) BodyLimits.readHeight(height) else BodyLimits.Reading.VALID
    val weightReading = if (needWeight) BodyLimits.readWeight(weight, Units.current) else BodyLimits.Reading.VALID
    val valid = heightReading == BodyLimits.Reading.VALID && weightReading == BodyLimits.Reading.VALID
    val save = {
        onSave(
            if (needHeight) DecimalInput.parse(height) else null,
            if (needWeight) DecimalInput.parse(weight)?.let { Loads.toKg(it, Units.current) } else null,
        )
    }
    IronvellumDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (gap == BodyGap.BOTH) R.string.bodydetails_title_both else if (needHeight) R.string.bodydetails_title_height else R.string.bodydetails_title_weight)) },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    UiText.themed(R.string.bodydetails_intro, R.string.bodydetails_intro__t).asString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                )
                if (needHeight) {
                    OutlinedTextField(
                        shape = MaterialTheme.shapes.small,
                        colors = ironvellumFieldColors(),
                        value = height,
                        onValueChange = { height = DecimalInput.sanitize(it, maxDecimals = 1, maxLength = 5) },
                        label = { Text(stringResource(R.string.onboarding_profile_height_label)) },
                        singleLine = true,
                        isError = heightReading == BodyLimits.Reading.OUT_OF_RANGE,
                        supportingText = { Text(stringResource(R.string.bodydetails_height_range, *BodyLimits.ends(BodyLimits.HEIGHT_CM).toTypedArray())) },
                        keyboardOptions = decimalKeyboard(if (needWeight) ImeAction.Next else ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { if (valid) save() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (needWeight) {
                    OutlinedTextField(
                        shape = MaterialTheme.shapes.small,
                        colors = ironvellumFieldColors(),
                        value = weight,
                        onValueChange = { weight = DecimalInput.sanitize(it, maxDecimals = 1, maxLength = 5) },
                        label = { Text(stringResource(R.string.onboarding_profile_weight_label, Units.name)) },
                        singleLine = true,
                        isError = weightReading == BodyLimits.Reading.OUT_OF_RANGE,
                        supportingText = { Text(stringResource(R.string.bodydetails_weight_range, *BodyLimits.weightEnds(Units.current).toTypedArray(), Units.name)) },
                        keyboardOptions = decimalKeyboard(ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { if (valid) save() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (error != null) {
                    Text(error, style = MaterialTheme.typography.bodySmall, color = IronvellumColors.DangerRed)
                }
            }
        },
        dismissButton = { IronvellumButton(label = stringResource(R.string.common_cancel), onClick = onDismiss, quiet = true) },
        confirmButton = {
            IronvellumButton(
                label = stringResource(R.string.common_save),
                onClick = save,
                enabled = valid && (height.isNotBlank() || weight.isNotBlank()),
            )
        },
    )
}
