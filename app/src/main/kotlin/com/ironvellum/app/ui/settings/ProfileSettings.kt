package com.ironvellum.app.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironvellum.app.domain.Sex
import com.ironvellum.app.domain.DecimalInput
import com.ironvellum.app.ui.components.InkSegmented
import com.ironvellum.app.ui.components.SettingsCaption
import com.ironvellum.app.ui.components.SettingsGroup
import com.ironvellum.app.ui.components.decimalKeyboard
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.ironvellumFieldColors

/**
 * Name, height and sex. The text fields save on IME Done and on leaving the
 * screen, with the result inline under the field: no SAVE buttons.
 */
@Composable
internal fun ProfileSettings(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val signedIn by viewModel.signedIn.collectAsStateWithLifecycle()
    val renaming by viewModel.renaming.collectAsStateWithLifecycle()
    val renameError by viewModel.renameError.collectAsStateWithLifecycle()
    val nameSaved by viewModel.nameSaved.collectAsStateWithLifecycle()
    val bodyProfile by viewModel.bodyProfile.collectAsStateWithLifecycle()
    val heightStatus by viewModel.heightStatus.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current
    LaunchedEffect(Unit) { viewModel.clearSavedTicks() }

    // Saveable with the stored value as the initial value only: on restore the
    // loaded profile must not overwrite a typed-but-unsaved edit.
    var name by rememberSaveable(profile?.name) { mutableStateOf(profile?.name ?: "") }
    // A whole-number height reads "180", not "180.0" — the decimal adds nothing.
    var heightInput by rememberSaveable(bodyProfile.first) {
        mutableStateOf(bodyProfile.first?.let { SettingsSummaries.formatHeightCm(it) } ?: "")
    }

    // Leaving commits whatever is typed, the same as Done. Not onDispose:
    // that also fires on rotation, which is not the lifter leaving.
    val leave = {
        viewModel.rename(name)
        viewModel.setHeight(heightInput)
        onBack()
    }
    // Back is not the only way out: a notification tap or the app going to the
    // background also ends the visit. Both commits are no-ops when nothing changed.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        viewModel.rename(name)
        viewModel.setHeight(heightInput)
    }

    SettingsPage(SettingsSection.PROFILE.title, leave) {
        SettingsGroup("NAME", topSpace = 12.dp) {
            OutlinedTextField(
                shape = MaterialTheme.shapes.small,
                colors = ironvellumFieldColors(),
                value = name,
                onValueChange = {
                    name = it.take(24)
                    viewModel.clearRenameError()
                },
                label = { Text("Your name") },
                isError = renameError != null,
                enabled = !renaming,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    viewModel.rename(name)
                    focus.clearFocus()
                }),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            FieldResult(saved = nameSaved, error = renameError)
            SettingsCaption(
                if (signedIn) "Shown on Today and in the Reckoning." else "Shown on Today. Sign in to join the Reckoning.",
            )
        }

        SettingsGroup("BODY") {
            OutlinedTextField(
                shape = MaterialTheme.shapes.small,
                colors = ironvellumFieldColors(),
                value = heightInput,
                onValueChange = {
                    heightInput = DecimalInput.sanitize(it, maxDecimals = 1, maxLength = 5)
                    viewModel.clearHeightStatus()
                },
                label = { Text("Height (cm)") },
                isError = heightStatus.error != null,
                singleLine = true,
                keyboardOptions = decimalKeyboard(),
                keyboardActions = KeyboardActions(onDone = {
                    viewModel.setHeight(heightInput)
                    focus.clearFocus()
                }),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            FieldResult(saved = heightStatus.saved, error = heightStatus.error)
            SettingsCaption("Used for BMI, FFMI and step length.")
            Spacer(Modifier.height(14.dp))
            InkSegmented(
                options = Sex.entries.map { it to it.name },
                selected = bodyProfile.second,
                onPick = { viewModel.setSex(it) },
            )
            Spacer(Modifier.height(8.dp))
            SettingsCaption("Sets the body-fat formula and strength scaling.")
        }
    }
}

/** The inline outcome of a commit: a Saved tick or the refusal, in the field's own place. */
@Composable
private fun FieldResult(saved: Boolean, error: String?) {
    when {
        error != null -> {
            SettingsCaption(error, color = IronvellumColors.DangerRed)
            Spacer(Modifier.height(4.dp))
        }
        saved -> {
            SettingsCaption("✓ Saved", color = IronvellumColors.Emerald)
            Spacer(Modifier.height(4.dp))
        }
    }
}
