package com.ironvellum.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironvellum.app.IronvellumApp
import com.ironvellum.app.R
import com.ironvellum.app.data.Units
import com.ironvellum.app.data.Words
import com.ironvellum.app.domain.LoadUnit
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.ui.asString
import com.ironvellum.app.ui.components.DockedActionBar
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.components.InkSegmented
import com.ironvellum.app.ui.components.SectionHeader
import com.ironvellum.app.ui.rememberThemedStringsComplete
import com.ironvellum.app.ui.theme.AccentPresets
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.DotShape
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.inkBorder

/**
 * The one calm screen between Welcome and setup step 1: how the app names things, which weight unit it
 * speaks, and the colour pair. It is outside the "Step n of 3" count on purpose, and it writes straight to
 * the settings that Settings edits later ([Words.setThemed], [Units.set], the appearance store), so nothing
 * here is a second copy of a choice. Every control applies live; Continue only moves on.
 */
@Composable
internal fun FirstRunPreferencesScreen(onContinue: () -> Unit, onUnitPicked: (LoadUnit) -> Unit) {
    val context = LocalContext.current.applicationContext
    val appearance = (context as IronvellumApp).appearanceStore
    val darkPalette by appearance.palette.collectAsStateWithLifecycle()
    val lightPalette by appearance.lightPalette.collectAsStateWithLifecycle()
    val palette = if (IronvellumColors.light) lightPalette else darkPalette
    val presets = AccentPresets.forTheme(IronvellumColors.light)

    Column(
        Modifier
            .fillMaxSize()
            .background(IronvellumColors.Abyss)
            .statusBarsPadding(),
    ) {
        // The page is short, so it is centred in the space above Continue (and scrolls when it does not fit),
        // rather than hanging at the top over a blank half screen.
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
        val pageHeight = maxHeight
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = pageHeight)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.onboarding_prefs_title),
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.Bold,
                color = IronvellumColors.Ink,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(4.dp))
            Text(
                UiText.themed(R.string.onboarding_prefs_intro, R.string.onboarding_prefs_intro__t).asString(),
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
            )

            // A language without its themed strings has only the plain words to show, so there is nothing to choose.
            if (rememberThemedStringsComplete()) {
                SectionHeader(stringResource(R.string.onboarding_prefs_names_title), topPadding = 24.dp)
                InkSegmented(
                    options = listOf(false to stringResource(R.string.onboarding_prefs_names_plain), true to stringResource(R.string.onboarding_prefs_names_themed)),
                    selected = Words.themed,
                    onPick = { Words.setThemed(context, it) },
                )
                Spacer(Modifier.height(10.dp))
                InkPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
                    Text(
                        UiText.themed(R.string.onboarding_prefs_names_sample, R.string.onboarding_prefs_names_sample__t).asString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = IronvellumColors.Ink,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    UiText.themed(R.string.onboarding_prefs_names_caption, R.string.onboarding_prefs_names_caption__t).asString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                )
            }

            SectionHeader(stringResource(R.string.onboarding_prefs_unit_title), topPadding = 24.dp)
            InkSegmented(
                options = LoadUnit.entries.map { it to it.label },
                selected = Units.current,
                onPick = {
                    onUnitPicked(it)
                    Units.set(context, it)
                },
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.onboarding_prefs_unit_caption),
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
            )

            SectionHeader(stringResource(R.string.onboarding_prefs_accent_title), topPadding = 24.dp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                presets.forEach { preset ->
                    PaletteSwatch(
                        primary = preset.palette.primary,
                        secondary = preset.palette.secondary,
                        name = preset.name,
                        selected = palette == preset.palette,
                        onClick = { appearance.set(preset.palette) },
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                presets.firstOrNull { it.palette == palette }?.name ?: stringResource(R.string.settings_hub_appearance_custom),
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
            )
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.onboarding_prefs_later),
                style = MaterialTheme.typography.labelSmall,
                color = IronvellumColors.InkMuted,
            )
            Spacer(Modifier.height(24.dp))
        }
        }

        DockedActionBar(
            primary = stringResource(R.string.common_continue),
            onPrimary = onContinue,
            reserveLink = false,
        )
    }
}

/** A colour pair as one disc, primary on the left half and reward on the right; a ring marks the pick. */
@Composable
private fun PaletteSwatch(primary: Int, secondary: Int, name: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = name },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(46.dp)
                .then(if (selected) Modifier.inkBorder(IronvellumColors.Ink, DotShape, 2.dp) else Modifier)
                .padding(5.dp)
                .clip(DotShape),
        ) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxHeight().background(IronvellumColors.swatch(primary)))
                Box(Modifier.weight(1f).fillMaxHeight().background(IronvellumColors.swatch(secondary)))
            }
        }
    }
}
