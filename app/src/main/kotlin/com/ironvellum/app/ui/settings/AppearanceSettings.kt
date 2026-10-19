package com.ironvellum.app.ui.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironvellum.app.R
import com.ironvellum.app.data.Lines
import com.ironvellum.app.data.Words
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.ui.asString
import com.ironvellum.app.ui.components.InkDivider
import com.ironvellum.app.ui.components.InkRowPanel
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.components.IronvellumDialog
import com.ironvellum.app.ui.components.ListRowHeight
import com.ironvellum.app.ui.components.SectionHeader
import com.ironvellum.app.ui.components.SettingsCaption
import com.ironvellum.app.ui.components.SettingsSwitchRow
import com.ironvellum.app.ui.rememberThemedStringsComplete
import com.ironvellum.app.ui.theme.AccentPalette
import com.ironvellum.app.ui.theme.AccentPresets
import com.ironvellum.app.ui.theme.DotShape
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.ThemeMode
import com.ironvellum.app.ui.theme.accentHex
import com.ironvellum.app.ui.theme.deepenForLight
import com.ironvellum.app.ui.theme.inkBorder
import com.ironvellum.app.ui.theme.inkDot
import com.ironvellum.app.ui.theme.parseAccentHex
import kotlin.math.roundToInt

/** The accent pair of the theme in force: the dark pair or the Vellum pair. */
@Composable
internal fun SettingsViewModel.currentAppearance(): AccentPalette {
    val dark by darkAppearance.collectAsStateWithLifecycle()
    val light by lightAppearance.collectAsStateWithLifecycle()
    return if (IronvellumColors.light) light else dark
}

@Composable
internal fun AppearanceSettings(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val palette = viewModel.currentAppearance()
    val mode by viewModel.themeMode.collectAsStateWithLifecycle()
    val light = IronvellumColors.light
    val presets = AccentPresets.forTheme(light)
    var editingPrimary by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val context = LocalContext.current
    SettingsPage(SettingsSection.APPEARANCE.title, onBack) {
        SectionHeader(stringResource(R.string.settings_appearance_theme_title), topPadding = 12.dp)
        SettingsCaption(stringResource(R.string.settings_appearance_theme_caption))
        Spacer(Modifier.height(8.dp))
        InkRowPanel(Modifier.fillMaxWidth()) {
            Column(Modifier.selectableGroup()) {
                listOf(
                    ThemeMode.SYSTEM to stringResource(R.string.settings_appearance_theme_system),
                    ThemeMode.DARK to stringResource(R.string.settings_appearance_theme_dark),
                    ThemeMode.LIGHT to stringResource(R.string.settings_appearance_theme_light),
                ).forEach { (option, name) ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = ListRowHeight)
                            .selectable(selected = mode == option, role = Role.RadioButton, onClick = { viewModel.setThemeMode(option) })
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Canvas(Modifier.size(16.dp)) {
                            inkDot(center, 6.dp.toPx(), IronvellumColors.Rune)
                            if (mode == option) inkDot(center, 3.dp.toPx(), IronvellumColors.Emerald)
                        }
                        Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        // A language without its themed strings has only the plain words to show, so there is nothing to switch.
        if (rememberThemedStringsComplete()) {
            SettingsSwitchRow(
                label = stringResource(R.string.settings_appearance_themed_names_label),
                caption = Lines.themedNamesCaption,
                checked = Words.themed,
                onCheckedChange = { Words.setThemed(context.applicationContext, it) },
            )
        }
        SectionHeader(stringResource(R.string.settings_appearance_pairs_title), topPadding = 16.dp)
        // With the pairs it describes, not under the Themed names row above.
        SettingsCaption(stringResource(R.string.settings_appearance_pairs_caption))
        Spacer(Modifier.height(8.dp))
        InkRowPanel(Modifier.fillMaxWidth()) {
            Column(Modifier.selectableGroup()) {
                presets.forEach { preset ->
                    val selection by animateFloatAsState(
                        targetValue = if (palette == preset.palette) 1f else 0f,
                        animationSpec = tween(180),
                        label = "colour pair selection",
                    )
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = ListRowHeight)
                            .selectable(
                                selected = palette == preset.palette,
                                role = Role.RadioButton,
                                onClick = { viewModel.setAppearance(preset.palette) },
                            ).padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Canvas(Modifier.size(16.dp)) {
                            inkDot(center, 6.dp.toPx(), IronvellumColors.Rune)
                            if (selection > 0f) inkDot(center, 3.dp.toPx() * selection, IronvellumColors.swatch(preset.palette.primary).copy(alpha = selection))
                        }
                        Text(preset.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        AccentSwatch(preset.palette.primary)
                        AccentSwatch(preset.palette.secondary)
                    }
                }
            }
            InkDivider()
            AccentRow(stringResource(R.string.settings_appearance_primary_label), stringResource(R.string.settings_appearance_primary_caption), palette.primary) { editingPrimary = true }
            AccentRow(stringResource(R.string.settings_appearance_reward_label), UiText.themed(R.string.settings_appearance_reward_caption, R.string.settings_appearance_reward_caption__t).asString(), palette.secondary) { editingPrimary = false }
        }
        Spacer(Modifier.height(16.dp))
        AccentPreview(palette)
        Spacer(Modifier.height(12.dp))
        IronvellumButton(stringResource(R.string.settings_appearance_reset_button, presets[0].name.lowercase()), { viewModel.setAppearance(presets[0].palette) }, quiet = true)
    }
    editingPrimary?.let { primary ->
        AccentPickerDialog(
            palette = palette,
            primary = primary,
            onApply = { colour ->
                viewModel.setAppearance(if (primary) palette.copy(primary = colour) else palette.copy(secondary = colour))
                editingPrimary = null
            },
            onDismiss = { editingPrimary = null },
        )
    }
}

@Composable
private fun AccentSwatch(colour: Int) {
    Box(Modifier.size(20.dp).background(IronvellumColors.swatch(colour), DotShape).inkBorder(IronvellumColors.InkMuted, DotShape, 1.dp))
}

@Composable
private fun AccentRow(label: String, subline: String, colour: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = ListRowHeight)
            .clickable(role = Role.Button, onClickLabel = "Edit $label", onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            SettingsCaption(subline)
        }
        Text(accentHex(colour), style = MaterialTheme.typography.labelMedium, color = IronvellumColors.InkMuted)
        AccentSwatch(colour)
    }
}

/** A small sample of each colour's job, with readable ink even for a very dark custom colour. */
@Composable
private fun AccentPreview(palette: AccentPalette) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_appearance_preview_action), style = MaterialTheme.typography.labelLarge, color = IronvellumColors.Emerald)
        }
        Column(Modifier.weight(1f)) {
            Text(UiText.themed(R.string.settings_appearance_preview_record, R.string.settings_appearance_preview_record__t).asString(), style = MaterialTheme.typography.labelMedium, color = IronvellumColors.Ink)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AccentSwatch(palette.secondary)
                Text(stringResource(R.string.settings_appearance_preview_xp), style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.SovereignGold)
            }
        }
    }
}

@Composable
private fun AccentPickerDialog(palette: AccentPalette, primary: Boolean, onApply: (Int) -> Unit, onDismiss: () -> Unit) {
    val initial = if (primary) palette.primary else palette.secondary
    var hex by rememberSaveable(initial) { mutableStateOf(accentHex(initial)) }
    // Keep channels separately: choosing a hue must still work at zero saturation or brightness.
    val initialHsv = remember(initial) { FloatArray(3).also { android.graphics.Color.colorToHSV(initial, it) } }
    var hue by rememberSaveable(initial) { mutableFloatStateOf(initialHsv[0]) }
    var saturation by rememberSaveable(initial) { mutableFloatStateOf(initialHsv[1]) }
    var brightness by rememberSaveable(initial) { mutableFloatStateOf(initialHsv[2]) }
    val hsv = floatArrayOf(hue, saturation, brightness)
    val preview = android.graphics.Color.HSVToColor(hsv)
    val parsed = parseAccentHex(hex)
    fun updateChannel(channel: Int, value: Float) {
        when (channel) {
            0 -> hue = value
            1 -> saturation = value
            else -> brightness = value
        }
        hex = accentHex(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, brightness)))
    }
    val hexLabel = stringResource(R.string.settings_appearance_picker_hex_label)
    val hexError = stringResource(R.string.settings_appearance_picker_hex_error)
    IronvellumDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (primary) R.string.settings_appearance_primary_label else R.string.settings_appearance_reward_label), style = MaterialTheme.typography.titleMedium, color = IronvellumColors.Ink, modifier = Modifier.semantics { heading() }) },
        text = {
            ColourPad(hue, saturation, brightness) { nextSaturation, nextBrightness ->
                saturation = nextSaturation
                brightness = nextBrightness
                hex = accentHex(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, brightness)))
            }
            HueStrip(hue) { updateChannel(0, it) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AccentSwatch(initial)
                    Text(stringResource(R.string.settings_appearance_picker_current), style = MaterialTheme.typography.labelSmall, color = IronvellumColors.InkMuted)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AccentSwatch(preview)
                    Text(stringResource(R.string.settings_appearance_picker_new), style = MaterialTheme.typography.labelSmall, color = IronvellumColors.InkMuted)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.settings_appearance_picker_hex_label), style = MaterialTheme.typography.labelSmall, color = IronvellumColors.InkMuted)
                    BasicTextField(
                        value = hex,
                        onValueChange = { value ->
                            hex = value
                            parseAccentHex(value)?.let { colour ->
                                val next = FloatArray(3).also { android.graphics.Color.colorToHSV(colour, it) }
                                hue = next[0]; saturation = next[1]; brightness = next[2]
                            }
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = IronvellumColors.Ink),
                        cursorBrush = SolidColor(IronvellumColors.Ink),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { parsed?.let(onApply) }),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("accent-hex")
                            .semantics {
                                contentDescription = hexLabel
                                if (parsed == null) error(hexError)
                            },
                    )
                    Box(Modifier.fillMaxWidth().height(1.dp).background(if (parsed == null) IronvellumColors.DangerRed else IronvellumColors.Rune))
                }
            }
            if (parsed == null) {
                Text(hexError, style = MaterialTheme.typography.bodySmall, color = IronvellumColors.DangerRed)
            } else if (IronvellumColors.light) {
                if (deepenForLight(preview) != preview or 0xFF000000.toInt()) SettingsCaption(stringResource(R.string.settings_appearance_picker_darkened))
            } else if ((Color(preview).luminance() + 0.05f) / (IronvellumColors.Vault.luminance() + 0.05f) < 3f) {
                SettingsCaption(stringResource(R.string.settings_appearance_picker_low_contrast))
            }
        },
        dismissButton = { IronvellumButton(stringResource(R.string.common_cancel), onDismiss, quiet = true) },
        confirmButton = { IronvellumButton(stringResource(R.string.common_apply), { parsed?.let(onApply) }, enabled = parsed != null) },
    )
}
/** Two colour dimensions share one surface; its actions also work without dragging. */
@Composable
private fun ColourPad(hue: Float, saturation: Float, brightness: Float, onPick: (Float, Float) -> Unit) {
    fun adjustSaturation(delta: Float): Boolean {
        onPick((saturation + delta).coerceIn(0f, 1f), brightness)
        return true
    }
    fun adjustBrightness(delta: Float): Boolean {
        onPick(saturation, (brightness + delta).coerceIn(0f, 1f))
        return true
    }
    val padLabel = stringResource(R.string.settings_appearance_pad_label)
    val padState = stringResource(
        R.string.settings_appearance_pad_state,
        (saturation * 100).roundToInt(),
        (brightness * 100).roundToInt(),
    )
    val moreSaturation = stringResource(R.string.settings_appearance_pad_more_saturation)
    val lessSaturation = stringResource(R.string.settings_appearance_pad_less_saturation)
    val moreBrightness = stringResource(R.string.settings_appearance_pad_more_brightness)
    val lessBrightness = stringResource(R.string.settings_appearance_pad_less_brightness)
    val hueColour = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))
    val selectedColour = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, brightness)))
    Canvas(
        Modifier.fillMaxWidth().height(160.dp)
            .colourGesture { x, y -> onPick(x, 1f - y) }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) false else when (event.key) {
                    Key.DirectionLeft -> adjustSaturation(-0.05f)
                    Key.DirectionRight -> adjustSaturation(0.05f)
                    Key.DirectionUp -> adjustBrightness(0.05f)
                    Key.DirectionDown -> adjustBrightness(-0.05f)
                    else -> false
                }
            }
            .focusable()
            .semantics {
                contentDescription = padLabel
                stateDescription = padState
                customActions = listOf(
                    CustomAccessibilityAction(moreSaturation) { adjustSaturation(0.05f) },
                    CustomAccessibilityAction(lessSaturation) { adjustSaturation(-0.05f) },
                    CustomAccessibilityAction(moreBrightness) { adjustBrightness(0.05f) },
                    CustomAccessibilityAction(lessBrightness) { adjustBrightness(-0.05f) },
                )
            },
    ) {
        drawRect(hueColour)
        drawRect(Brush.horizontalGradient(listOf(Color.White, Color.Transparent)))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
        val radius = 7.dp.toPx()
        val cursor = Offset(
            (saturation * size.width).coerceIn(radius, size.width - radius),
            ((1f - brightness) * size.height).coerceIn(radius, size.height - radius),
        )
        inkDot(cursor, radius, Color.Black)
        inkDot(cursor, radius - 1.dp.toPx(), IronvellumColors.Ink)
        inkDot(cursor, radius - 3.dp.toPx(), selectedColour)
    }
}

/** The colour rail is slim, while its touch target remains 44dp high. */
@Composable
private fun HueStrip(hue: Float, onPick: (Float) -> Unit) {
    val hueLabel = stringResource(R.string.settings_appearance_hue_label)
    val hueState = stringResource(R.string.settings_appearance_hue_state, hue.roundToInt())
    Canvas(
        Modifier.fillMaxWidth().height(44.dp)
            .colourGesture { x, _ -> onPick(x * 359f) }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) false else when (event.key) {
                    Key.DirectionLeft -> { onPick((hue - 5f).coerceAtLeast(0f)); true }
                    Key.DirectionRight -> { onPick((hue + 5f).coerceAtMost(359f)); true }
                    else -> false
                }
            }
            .focusable()
            .semantics {
                contentDescription = hueLabel
                stateDescription = hueState
                progressBarRangeInfo = ProgressBarRangeInfo(hue, 0f..359f)
                setProgress { value -> onPick(value.coerceIn(0f, 359f)); true }
            },
    ) {
        val trackHeight = 8.dp.toPx()
        drawRect(
            Brush.horizontalGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)),
            topLeft = Offset(0f, (size.height - trackHeight) / 2f),
            size = Size(size.width, trackHeight),
        )
        val radius = 7.dp.toPx()
        val cursor = Offset((hue / 359f * size.width).coerceIn(radius, size.width - radius), size.height / 2f)
        inkDot(cursor, radius, Color.Black)
        inkDot(cursor, radius - 1.dp.toPx(), IronvellumColors.Ink)
        inkDot(cursor, radius - 3.dp.toPx(), Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f))))
    }
}

/** Stable pointer input keeps a drag alive while the selected colour recomposes. */
@Composable
private fun Modifier.colourGesture(onPick: (Float, Float) -> Unit): Modifier {
    val currentPick by rememberUpdatedState(onPick)
    return pointerInput(Unit) {
        awaitEachGesture {
            fun pick(position: Offset) {
                currentPick((position.x / size.width).coerceIn(0f, 1f), (position.y / size.height).coerceIn(0f, 1f))
            }
            val down = awaitFirstDown(requireUnconsumed = false)
            pick(down.position)
            down.consume()
            drag(down.id) { change ->
                pick(change.position)
                change.consume()
            }
        }
    }
}
