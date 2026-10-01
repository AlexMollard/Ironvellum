package com.ironvellum.app.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironvellum.app.ui.components.InkSegmented
import com.ironvellum.app.ui.components.SettingsCaption
import com.ironvellum.app.ui.components.SettingsGroup

/**
 * The ink treatment is the app's look, so this exists to leave it rather than
 * to opt in. Off restores the original cut-corner geometry - plain rules, even
 * rails, true arcs - not a broken version of the brush.
 */
@Composable
internal fun AppearanceSettings(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val inkOn = profile?.inkStyle ?: false
    SettingsPage(SettingsSection.APPEARANCE.title, onBack) {
        SettingsGroup(null, topSpace = 12.dp) {
            InkSegmented(
                options = listOf(true to "INK", false to "CLEAN"),
                selected = inkOn,
                onPick = { viewModel.setInkStyle(it) },
            )
            Spacer(Modifier.height(8.dp))
            SettingsCaption(if (inkOn) "Hand-drawn edges and paper grain." else "Straight edges and even rules.")
        }
    }
}
