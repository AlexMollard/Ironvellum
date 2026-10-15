package com.ironvellum.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironvellum.app.IronvellumApp
import com.ironvellum.app.data.Units
import com.ironvellum.app.data.Words
import com.ironvellum.app.domain.LoadUnit
import com.ironvellum.app.ui.components.DockedActionBar
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.components.InkSegmented
import com.ironvellum.app.ui.components.SectionHeader
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
    val palette by appearance.palette.collectAsStateWithLifecycle()

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
                "Make it yours",
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.Bold,
                color = IronvellumColors.Ink,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Choose how your ${Words.trial.pl} and ${Words.rite.pl} are named, the weight unit and the colours.",
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
            )

            SectionHeader("Names", topPadding = 24.dp)
            InkSegmented(
                options = listOf(false to "Plain", true to "Themed"),
                selected = Words.themed,
                onPick = { Words.setThemed(context, it) },
            )
            Spacer(Modifier.height(10.dp))
            InkPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
                Text(
                    "Begin ${Words.trial.s}  ·  Your ${Words.rite.pl}  ·  ${Words.peak.cpl}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = IronvellumColors.Ink,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (Words.themed) "The app's own fantasy names." else "Everyday gym words.",
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
            )

            SectionHeader("Weight unit", topPadding = 24.dp)
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
                "Loads, plates and your body weight. Height stays in cm.",
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
            )

            SectionHeader("Accent colour", topPadding = 24.dp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                AccentPresets.entries.forEach { preset ->
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
                AccentPresets.entries.firstOrNull { it.palette == palette }?.name ?: "Custom colours",
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
            )
            Spacer(Modifier.height(24.dp))
            Text(
                "You can change these later in Settings.",
                style = MaterialTheme.typography.labelSmall,
                color = IronvellumColors.InkMuted,
            )
            Spacer(Modifier.height(24.dp))
        }
        }

        DockedActionBar(
            primary = "Continue",
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
                Box(Modifier.weight(1f).fillMaxHeight().background(Color(primary)))
                Box(Modifier.weight(1f).fillMaxHeight().background(Color(secondary)))
            }
        }
    }
}
