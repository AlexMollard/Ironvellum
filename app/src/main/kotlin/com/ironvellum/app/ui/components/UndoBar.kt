package com.ironvellum.app.ui.components

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.inkBorder
import kotlinx.coroutines.delay

/**
 * The app's one Undo bar: a flat VaultHigh strip with a 1dp Rune border, a message and a
 * sentence-case "Undo". Reversible actions happen at once and offer this instead of a confirm.
 *
 * With [onExpired] the bar times itself out after [undoWindowMs] (longer when accessibility
 * services ask for more time) and calls it; [key] restarts that clock for a new offer. Without
 * it the caller owns the lifetime.
 */
@Composable
fun UndoBar(
    message: String,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
    onExpired: (() -> Unit)? = null,
    key: Any = message,
) {
    if (onExpired != null) {
        val window = undoWindowMs(LocalContext.current)
        LaunchedEffect(key) {
            delay(window)
            onExpired()
        }
    }
    val shape = MaterialTheme.shapes.small
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(IronvellumColors.VaultHigh)
            .inkBorder(IronvellumColors.Rune, shape, 1.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = IronvellumColors.Ink,
            modifier = Modifier.weight(1f),
        )
        Box(
            Modifier
                .defaultMinSize(minWidth = 72.dp)
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClick = onUndo)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Undo", style = MaterialTheme.typography.labelLarge, color = IronvellumColors.SystemGreen)
        }
    }
}

private const val UNDO_WINDOW_MS = 6_000L

/** [UNDO_WINDOW_MS], stretched to the system's recommended timeout when accessibility services are on. */
private fun undoWindowMs(context: Context): Long {
    val manager = context.getSystemService(AccessibilityManager::class.java) ?: return UNDO_WINDOW_MS
    val flags = AccessibilityManager.FLAG_CONTENT_CONTROLS or AccessibilityManager.FLAG_CONTENT_TEXT
    return manager.getRecommendedTimeoutMillis(UNDO_WINDOW_MS.toInt(), flags).toLong().coerceAtLeast(UNDO_WINDOW_MS)
}
