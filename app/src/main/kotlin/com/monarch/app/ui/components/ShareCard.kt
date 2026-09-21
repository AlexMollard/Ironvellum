package com.monarch.app.ui.components

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monarch.app.ui.theme.ChakraPetch
import com.monarch.app.ui.theme.MonarchColors
import com.monarch.app.ui.theme.MonarchTracking
import com.monarch.app.ui.theme.inkBorder
import androidx.compose.ui.platform.ClipEntry
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Shows the exact text that will be sent, then sends it. A share sheet that
 * hides its payload asks the hunter to trust that nothing private rode along;
 * showing the card is cheaper than that promise.
 *
 * The payload is a few hundred bytes of text, so it travels as an
 * `EXTRA_TEXT` string. Anything that could grow with training history — the
 * full data export — goes through FileProvider instead, because Intent extras
 * share a ~512 KB binder buffer.
 */
@Composable
fun ShareCardDialog(
    text: String,
    onDismiss: () -> Unit,
    title: String = "SHARE THIS TRIAL",
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1_600)
            copied = false
        }
    }

    AlertDialog(
        shape = MaterialTheme.shapes.medium,
        containerColor = Color(0xFF0D1110),
        onDismissRequest = onDismiss,
        title = {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                fontFamily = ChakraPetch,
                color = MonarchColors.SystemGreen,
                letterSpacing = MonarchTracking.ScreenTitle,
            )
        },
        text = {
            Column {
                Text(
                    text,
                    // Monospace so the block bar and the figures line up here
                    // the same way they do in a chat that uses a mono font.
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = MonarchColors.Ink,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .background(MonarchColors.Vault, MaterialTheme.shapes.extraSmall)
                        .inkBorder(MonarchColors.Rune, MaterialTheme.shapes.extraSmall, 1.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(10.dp),
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MonarchButton(
                        label = if (copied) "COPIED" else "COPY",
                        onClick = {
                            scope.launch {
                                clipboard.setClipEntry(
                                    ClipEntry(ClipData.newPlainText("Monarch workout", text)),
                                )
                            }
                            copied = true
                        },
                        modifier = Modifier.weight(1f),
                    )
                    MonarchButton(
                        label = "SHARE",
                        gold = true,
                        onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, text)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share workout"))
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
