package com.ironvellum.app.ui.train

import com.ironvellum.app.ui.asString
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.R
import androidx.compose.ui.res.stringResource
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import com.ironvellum.app.ui.components.IronvellumDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ironvellum.app.data.Repository
import com.ironvellum.app.domain.RoutineCode
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.ironvellumFieldColors

/**
 * Copies [code] first, then opens the chooser: some targets (notes apps,
 * a chat that truncates long extras) drop or mangle the shared text, and the
 * clipboard copy is the lifter's guaranteed second route.
 */
internal fun shareRoutineCode(context: Context, code: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard?.setPrimaryClip(
        ClipData.newPlainText(UiText.themed(R.string.routineshare_clip_label, R.string.routineshare_clip_label__t).asString(context), code),
    )
    Toast.makeText(context, UiText.themed(R.string.routineshare_copied, R.string.routineshare_copied__t).asString(context), Toast.LENGTH_SHORT).show()
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(
            Intent.EXTRA_TEXT,
            UiText.themed(R.string.routineshare_message, R.string.routineshare_message__t, code).asString(context),
        )
    }
    context.startActivity(Intent.createChooser(intent, UiText.themed(R.string.routineshare_share_title, R.string.routineshare_share_title__t).asString(context)))
}

/** The one-line outcome shown under the Workouts header after an import. */
internal fun importSummary(context: Context, result: Repository.ImportedRoutine, replaced: Boolean): String {
    val skipped = result.skippedExercises
    val skippedText = if (skipped.isEmpty()) "" else " " + UiText.res(R.string.routineshare_skipped, skipped.joinToString(", ")).asString(context)
    if (result.added == 0) {
        return UiText.res(R.string.routineshare_nothing_imported).asString(context) + skippedText
    }
    val head = if (replaced) {
        UiText.themedPlural(R.plurals.routineshare_replaced, R.plurals.routineshare_replaced__t, result.added).asString(context)
    } else {
        UiText.themedPlural(R.plurals.routineshare_added, R.plurals.routineshare_added__t, result.added).asString(context)
    }
    return head + skippedText
}

/** "Pull day · 5 exercises", the one-line preview of a decoded workout. */
@Composable
internal fun previewLine(workout: RoutineCode.SharedWorkout): String {
    val n = workout.entries.size
    return UiText.plural(R.plurals.routineshare_preview_line, n, workout.name, n).asString()
}

/**
 * Paste a code, see what it holds, then add it beside the current workouts
 * (the safe default) or replace the routine after a confirm.
 *
 * The field starts from the clipboard when that holds a code, since the usual
 * path is "copy in the chat app, switch here". It is read in an effect, not
 * during composition: Android only hands the clipboard to a focused window,
 * and a read before the dialog gains focus returns nothing.
 */
@Composable
internal fun ImportRoutineDialog(
    currentWorkouts: Int,
    onImport: (workouts: List<RoutineCode.SharedWorkout>, replace: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    var confirmReplace by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (text.isBlank()) {
            val clip = runCatching {
                context.getSystemService(ClipboardManager::class.java)
                    ?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
            }.getOrNull()
            if (clip != null && clip.contains(RoutineCode.PREFIX)) text = clip
        }
    }

    val decoded = remember(text) { if (text.isBlank()) null else RoutineCode.decode(text) }
    val workouts = decoded?.getOrNull()

    IronvellumDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.routineshare_import_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    shape = MaterialTheme.shapes.small,
                    colors = ironvellumFieldColors(),
                    value = text,
                    onValueChange = { text = it.take(RoutineCode.MAX_DECODED_BYTES) },
                    label = { Text(UiText.themed(R.string.routineshare_code_label, R.string.routineshare_code_label__t).asString()) },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
                decoded?.exceptionOrNull()?.let { error ->
                    Text(
                        error.message.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = IronvellumColors.DangerRed,
                    )
                }
                workouts?.forEach { workout ->
                    Text(
                        previewLine(workout),
                        style = MaterialTheme.typography.bodyMedium,
                        color = IronvellumColors.Ink,
                    )
                }
                if (workouts != null) {
                    Spacer(Modifier.height(4.dp))
                    IronvellumButton(
                        label = UiText.themed(R.string.routineshare_add_button, R.string.routineshare_add_button__t).asString(),
                        onClick = { onImport(workouts, false) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // Nothing to replace on an empty board; "Add" already
                    // covers it, and a confirm about deleting 0 workouts is noise.
                    if (currentWorkouts > 0) {
                        // 16dp clear of the emerald Add (8dp column gap + this), so a
                        // slip of the thumb does not land on the destructive one.
                        Spacer(Modifier.height(8.dp))
                        IronvellumButton(
                            label = UiText.themed(R.string.routineshare_replace_my_button, R.string.routineshare_replace_my_button__t).asString(),
                            onClick = { confirmReplace = true },
                            modifier = Modifier.fillMaxWidth(),
                            danger = true,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            IronvellumButton(label = stringResource(R.string.common_cancel), onClick = onDismiss, quiet = true)
        },
    )

    if (confirmReplace && workouts != null) {
        IronvellumDialog(
            onDismissRequest = { confirmReplace = false },
            title = { Text(UiText.themed(R.string.routineshare_replace_title, R.string.routineshare_replace_title__t).asString()) },
            text = {
                Text(
                    UiText.themedPlural(
                        R.plurals.routineshare_replace_body, R.plurals.routineshare_replace_body__t,
                        currentWorkouts, currentWorkouts, workouts.size,
                    ).asString(),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                IronvellumButton(
                    label = stringResource(R.string.routineshare_replace_button),
                    onClick = { confirmReplace = false; onImport(workouts, true) },
                    danger = true,
                )
            },
            dismissButton = {
                IronvellumButton(label = stringResource(R.string.common_cancel), onClick = { confirmReplace = false }, quiet = true)
            },
        )
    }
}
