package com.ironvellum.app.ui.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType

/**
 * The soft keyboard for a field whose value can be fractional (kg, cm, %, km).
 * Always the decimal numpad, so the point is on the keys. Pair the field's
 * onValueChange with `DecimalInput.sanitize`, which also takes a typed ","
 * for keyboards that only offer a comma.
 */
fun decimalKeyboard(imeAction: ImeAction = ImeAction.Done): KeyboardOptions =
    KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = imeAction)

/**
 * The soft keyboard for a whole-number field (reps, sets, seconds, codes).
 * Some keyboards still show a point on this numpad, so pair the field's
 * onValueChange with `DecimalInput.sanitizeWhole`, which drops it.
 */
fun wholeKeyboard(imeAction: ImeAction = ImeAction.Done): KeyboardOptions =
    KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = imeAction)
