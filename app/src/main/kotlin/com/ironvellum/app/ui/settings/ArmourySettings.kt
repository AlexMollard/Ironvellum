package com.ironvellum.app.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironvellum.app.data.ProgramAnswersStore
import com.ironvellum.app.ui.components.SettingsCaption
import com.ironvellum.app.ui.components.SettingsGroup
import com.ironvellum.app.ui.program.GearPicker

/**
 * The same gear question the Forge asks, stored in the same place, so changing
 * it here moves the picker's MY ARMOURY filter and the next forged rite together.
 */
@Composable
internal fun ArmourySettings(onBack: () -> Unit) {
    val context = LocalContext.current
    val savedGear by ProgramAnswersStore.equipment.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { ProgramAnswersStore.get(context.applicationContext) }
    SettingsPage(SettingsSection.ARMOURY.title, onBack) {
        SettingsGroup(null, topSpace = 12.dp) {
            SettingsCaption(
                if (savedGear == null) {
                    "Not set: the Forge and MY ARMOURY offer everything."
                } else {
                    "The Forge and the MY ARMOURY filter use only this."
                },
            )
            Spacer(Modifier.height(10.dp))
            // Unset lights nothing, the same as onboarding: "Full gym" lit
            // with every cell ticked claimed an answer nobody gave.
            GearPicker(
                equipment = savedGear,
                onChange = { ProgramAnswersStore.saveEquipment(context.applicationContext, it) },
            )
        }
    }
}
