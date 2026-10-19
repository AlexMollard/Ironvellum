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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironvellum.app.R
import com.ironvellum.app.data.FirstRunPrefs
import com.ironvellum.app.data.Units
import com.ironvellum.app.data.Words
import com.ironvellum.app.domain.DecimalInput
import com.ironvellum.app.domain.Sex
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.ui.asString
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
    val latestWeightKg by viewModel.latestWeightKg.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current
    val context = LocalContext.current
    val sexUnset by FirstRunPrefs.sexUnsetFlow(context).collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.clearSavedTicks() }

    // Saveable with the stored value as the initial value only: on restore the
    // loaded profile must not overwrite a typed-but-unsaved edit.
    var name by rememberSaveable(profile?.name) { mutableStateOf(profile?.name?.let(Words::displayName) ?: "") }
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
        SettingsGroup(null, topSpace = 12.dp) {
            OutlinedTextField(
                shape = MaterialTheme.shapes.small,
                colors = ironvellumFieldColors(),
                value = name,
                onValueChange = {
                    name = it.take(24)
                    viewModel.clearRenameError()
                },
                label = { Text(stringResource(R.string.settings_profile_name_label)) },
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
            FieldResult(saved = nameSaved, error = renameError?.asString())
            SettingsCaption(
                if (signedIn) {
                    UiText.themed(R.string.settings_profile_name_caption_signed_in, R.string.settings_profile_name_caption_signed_in__t).asString()
                } else {
                    UiText.themed(R.string.settings_profile_name_caption_signed_out, R.string.settings_profile_name_caption_signed_out__t).asString()
                },
            )
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                shape = MaterialTheme.shapes.small,
                colors = ironvellumFieldColors(),
                value = heightInput,
                onValueChange = {
                    heightInput = DecimalInput.sanitize(it, maxDecimals = 1, maxLength = 5)
                    viewModel.clearHeightStatus()
                },
                label = { Text(stringResource(R.string.settings_profile_height_label)) },
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
            FieldResult(saved = heightStatus.saved, error = heightStatus.error?.asString())
            SettingsCaption(stringResource(R.string.settings_profile_height_caption))
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                shape = MaterialTheme.shapes.small,
                colors = ironvellumFieldColors(),
                value = latestWeightKg?.let { Units.withUnit(it) } ?: stringResource(R.string.settings_profile_weight_empty),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.settings_profile_weight_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            SettingsCaption(UiText.themed(R.string.settings_profile_weight_caption, R.string.settings_profile_weight_caption__t).asString())
            Spacer(Modifier.height(14.dp))
            InkSegmented<Sex?>(
                options = Sex.entries.map { it to it.label },
                selected = if (sexUnset) null else bodyProfile.second,
                onPick = {
                    if (it == null) return@InkSegmented
                    FirstRunPrefs.setSexUnset(context, false)
                    viewModel.setSex(it)
                },
            )
            Spacer(Modifier.height(8.dp))
            SettingsCaption(stringResource(R.string.settings_profile_sex_caption))
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
            SettingsCaption(stringResource(R.string.settings_saved_confirmation), color = IronvellumColors.InkMuted)
            Spacer(Modifier.height(4.dp))
        }
    }
}
