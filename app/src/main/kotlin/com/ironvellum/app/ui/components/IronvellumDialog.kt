package com.ironvellum.app.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.ironvellum.app.ui.theme.IronvellumColors

/**
 * The app's one dialog frame: a flat VaultHigh surface with a 1dp Rune top rule, a
 * `titleMedium` Ink title and text buttons (the dismiss on the left, the confirm on the
 * right). Every confirm / dismiss slot reads as a text button, so a dialog never carries a
 * filled emerald slab; danger stays [IronvellumButton]'s DangerRed `danger` look.
 */
@Composable
fun IronvellumDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = { CompositionLocalProvider(LocalTextButtons provides true) { confirmButton() } },
        modifier = modifier.drawWithContent {
            drawContent()
            drawRect(IronvellumColors.Rune, size = Size(size.width, 1.dp.toPx()))
        },
        dismissButton = dismissButton?.let { slot -> { CompositionLocalProvider(LocalTextButtons provides true) { slot() } } },
        title = title?.let { slot -> { ProvideTextStyle(MaterialTheme.typography.titleMedium) { slot() } } },
        text = text,
        // Flat and square: the surface is raised by colour and its top rule, not by shape or shadow.
        shape = MaterialTheme.shapes.small,
        containerColor = IronvellumColors.VaultHigh,
        tonalElevation = 0.dp,
        titleContentColor = IronvellumColors.Ink,
    )
}
