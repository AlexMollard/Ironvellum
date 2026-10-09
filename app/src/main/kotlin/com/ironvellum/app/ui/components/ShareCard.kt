package com.ironvellum.app.ui.components

import android.content.ClipData
import android.content.Context
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.core.content.edit
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironvellum.app.IronvellumApp
import com.ironvellum.app.domain.Crests
import com.ironvellum.app.domain.WorkoutShare
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.IronvellumTracking
import com.ironvellum.app.ui.theme.inkBorder
import androidx.compose.ui.platform.ClipEntry
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Shows the exact text that will be sent, then sends it. A share sheet that
 * hides its payload asks the lifter to trust that nothing private rode along;
 * showing the card is cheaper than that promise.
 *
 * The payload is a few hundred bytes of text, so it travels as an
 * `EXTRA_TEXT` string. Anything that could grow with training history — the
 * full data export — goes through FileProvider instead, because Intent extras
 * share a ~512 KB binder buffer.
 *
 * [render] builds the card for the current "Include exercise notes" choice, so
 * the preview, the clipboard and the share sheet always carry the same text.
 * The switch is offered only when the trial has a note to include ([hasNotes]),
 * starts off, and is remembered between shares.
 */
@Composable
fun ShareCardDialog(
    render: (includeNotes: Boolean) -> String,
    hasNotes: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }
    var includeNotes by remember { mutableStateOf(readIncludeNotes(context)) }
    var signWithCrest by remember { mutableStateOf(readSignWithCrest(context)) }
    // The crest worn now, if any: the card can sign with its name, and only when there is a crest to name.
    val repo = (context.applicationContext as IronvellumApp).repository
    val worn = Crests.byId(repo.observeEquippedFrame().collectAsStateWithLifecycle(null).value)
    val text = remember(render, includeNotes, hasNotes, signWithCrest, worn) {
        WorkoutShare.signed(render(includeNotes && hasNotes), worn.takeIf { signWithCrest })
    }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1_600)
            copied = false
        }
    }

    IronvellumDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share this trial") },
        text = {
            Column {
                Text(
                    text,
                    // Monospace so the block bar and the figures line up here
                    // the same way they do in a chat that uses a mono font.
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = IronvellumColors.Ink,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .background(IronvellumColors.Vault, MaterialTheme.shapes.medium)
                        .inkBorder(IronvellumColors.Rune, MaterialTheme.shapes.medium, 1.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(10.dp),
                )
                if (hasNotes) {
                    SettingsSwitchRow(
                        label = "Include exercise notes",
                        caption = "Prints each note under its movement",
                        checked = includeNotes,
                        onCheckedChange = {
                            includeNotes = it
                            writeIncludeNotes(context, it)
                        },
                    )
                }
                if (worn != null) {
                    SettingsSwitchRow(
                        label = "Sign with my crest",
                        caption = "Adds your worn crest as the last line",
                        checked = signWithCrest,
                        onCheckedChange = {
                            signWithCrest = it
                            writeSignWithCrest(context, it)
                        },
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IronvellumButton(
                        label = if (copied) "COPIED" else "COPY",
                        quiet = true,
                        onClick = {
                            scope.launch {
                                clipboard.setClipEntry(
                                    ClipEntry(ClipData.newPlainText("Ironvellum trial", text)),
                                )
                            }
                            copied = true
                        },
                        modifier = Modifier.weight(1f),
                    )
                    IronvellumButton(
                        label = "SHARE",
                        gold = true,
                        onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, text)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share this trial"))
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { IronvellumButton("Close", quiet = true, onClick = onDismiss) },
    )
}

private const val SHARE_PREFS = "share_card"
private const val KEY_INCLUDE_NOTES = "include_exercise_notes"

/** Off until the lifter turns it on: a note is their own words, not something to publish by default. */
private fun readIncludeNotes(context: Context): Boolean =
    context.getSharedPreferences(SHARE_PREFS, Context.MODE_PRIVATE).getBoolean(KEY_INCLUDE_NOTES, false)

private const val KEY_SIGN_WITH_CREST = "sign_with_crest"

/** On by default: the crest's name is one line, no username, and the preview shows it before anything is sent. */
private fun readSignWithCrest(context: Context): Boolean =
    context.getSharedPreferences(SHARE_PREFS, Context.MODE_PRIVATE).getBoolean(KEY_SIGN_WITH_CREST, true)

private fun writeSignWithCrest(context: Context, on: Boolean) =
    context.getSharedPreferences(SHARE_PREFS, Context.MODE_PRIVATE).edit { putBoolean(KEY_SIGN_WITH_CREST, on) }

private fun writeIncludeNotes(context: Context, on: Boolean) =
    context.getSharedPreferences(SHARE_PREFS, Context.MODE_PRIVATE).edit { putBoolean(KEY_INCLUDE_NOTES, on) }
