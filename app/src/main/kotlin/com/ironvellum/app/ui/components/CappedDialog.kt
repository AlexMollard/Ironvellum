package com.ironvellum.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** The widest the app's content runs: on a tablet it sits centred at this width instead of stretching. */
internal val ContentMaxWidth = 600.dp

/** Test tag of the capped box inside [CappedDialog]. */
internal const val CAPPED_DIALOG_TAG = "cappedDialogContent"

/**
 * A full-screen [Dialog] (no platform width) whose content is capped at [ContentMaxWidth] and centred, so on a
 * tablet it is not left-aligned in the window. The one place the cap lives for overlays; [content] fills the capped box.
 */
@Composable
fun CappedDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false),
    content: @Composable () -> Unit,
) {
    // A dialog is its own window and reads the window's density, not the caller's: pass the caller's on, so a
    // density the caller overrides (the tests' tablet width) reaches the cap too. It is the same value otherwise.
    val density = LocalDensity.current
    Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        CompositionLocalProvider(LocalDensity provides density) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.widthIn(max = ContentMaxWidth).fillMaxSize().testTag(CAPPED_DIALOG_TAG)) { content() }
            }
        }
    }
}
