package com.ironvellum.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.social.PushedHeader

/** The Settings sub-screens, each its own `settings/{section}` route under the hub. */
enum class SettingsSection(val key: String, val title: String) {
    PROFILE("profile", "PROFILE"),
    TRAINING_MODE("training_mode", "TRAINING MODE"),
    ARMOURY("armoury", "ARMOURY"),
    SUMMONS("summons", "THE SUMMONS"),
    APPEARANCE("appearance", "APPEARANCE"),
    HEALTH_CONNECT("health_connect", "HEALTH CONNECT"),
    DATA("data", "DATA"),
    ADVANCED("advanced", "ADVANCED"),
    ;

    companion object {
        fun fromKey(key: String?): SettingsSection? = entries.firstOrNull { it.key == key }
    }
}

/**
 * One sub-screen. [onBack] serves both the BACK label and system back, so a
 * screen that commits on leaving (Profile) only has to wrap it once.
 */
@Composable
fun SettingsSectionScreen(section: SettingsSection, onBack: () -> Unit, viewModel: SettingsViewModel) {
    when (section) {
        SettingsSection.PROFILE -> ProfileSettings(viewModel, onBack)
        SettingsSection.TRAINING_MODE -> TrainingModeSettings(viewModel, onBack)
        SettingsSection.ARMOURY -> ArmourySettings(onBack)
        SettingsSection.SUMMONS -> SummonsSettings(onBack)
        SettingsSection.APPEARANCE -> AppearanceSettings(viewModel, onBack)
        SettingsSection.HEALTH_CONNECT -> HealthConnectSettings(viewModel, onBack)
        SettingsSection.DATA -> DataSettings(viewModel, onBack)
        SettingsSection.ADVANCED -> AdvancedSettings(viewModel, onBack)
    }
}

/** The pushed-screen frame every sub-screen shares: title and BACK, then scrolling content. */
@Composable
internal fun SettingsPage(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    BackHandler(onBack = onBack)
    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        // Same top as the hub, so the title does not jump between them.
        Spacer(Modifier.height(8.dp))
        PushedHeader(title, onBack)
        content()
        Spacer(Modifier.height(24.dp))
    }
}

/** The confirm every Settings action that changes or deletes data goes through. */
@Composable
internal fun SettingsConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    danger: Boolean = false,
) {
    AlertDialog(
        // Material's dialog container is a 28dp rounded rect - the most
        // obviously stock surface in the app. Give it the ink shape.
        shape = MaterialTheme.shapes.medium,
        containerColor = Color(0xFF0D1110),
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { IronvellumButton(label = confirmLabel, onClick = onConfirm, danger = danger) },
        dismissButton = { IronvellumButton(label = dismissLabel, onClick = onDismiss, quiet = true) },
    )
}
