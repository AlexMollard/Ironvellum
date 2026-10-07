package com.ironvellum.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.inkHairline

/**
 * The app's one pop-up frame: a bottom sheet over the live screen, dimmed by a 60% scrim. It is a
 * flat full-width VaultHigh surface with a 1dp Rune top rule, a `titleMedium` Ink title, the body,
 * then a button row: the dismiss on the left as a text button, the confirm on the right as the
 * primary fill. Danger stays [IronvellumButton]'s DangerRed `danger` look. It rises above the
 * keyboard and the navigation bar; the scrim, a swipe down and back all dismiss.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IronvellumDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        // Wide enough to be full width on a tablet or a landscape phone too.
        sheetMaxWidth = Dp.Unspecified,
        shape = MaterialTheme.shapes.extraSmall,
        containerColor = IronvellumColors.VaultHigh,
        contentColor = IronvellumColors.Ink,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        tonalElevation = 0.dp,
        dragHandle = null,
    ) {
        Column(modifier.fillMaxWidth().imePadding().navigationBarsPadding()) {
            Box(Modifier.fillMaxWidth().height(1.dp).inkHairline(IronvellumColors.Rune, thickness = 1.dp))
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (title != null) ProvideTextStyle(MaterialTheme.typography.titleMedium) { title() }
                if (text != null) text()
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (dismissButton != null) {
                    CompositionLocalProvider(LocalTextButtons provides true) { dismissButton() }
                } else {
                    Spacer(Modifier)
                }
                confirmButton()
            }
        }
    }
}
