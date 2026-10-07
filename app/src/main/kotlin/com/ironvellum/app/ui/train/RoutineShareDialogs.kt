package com.ironvellum.app.ui.train

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
    clipboard?.setPrimaryClip(ClipData.newPlainText("Ironvellum cycle", code))
    Toast.makeText(context, "Cycle code copied", Toast.LENGTH_SHORT).show()
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(
            Intent.EXTRA_TEXT,
            "Ironvellum cycle \u2014 paste into Train \u203A New Rite \u203A Import code\n$code",
        )
    }
    context.startActivity(Intent.createChooser(intent, "Share cycle"))
}

/** The one-line outcome shown under the Workouts header after an import. */
internal fun importSummary(result: Repository.ImportedRoutine, replaced: Boolean): String {
    val skipped = result.skippedExercises
    val skippedText = if (skipped.isEmpty()) "" else " Skipped, not in your exercise list: ${skipped.joinToString(", ")}."
    if (result.added == 0) {
        return "Nothing imported \u2014 none of those exercises are in your exercise list.$skippedText"
    }
    val count = if (result.added == 1) "1 rite" else "${result.added} rites"
    val head = if (replaced) "Cycle replaced with $count." else "Added $count."
    return head + skippedText
}

/** "Pull day · 5 exercises", the one-line preview of a decoded workout. */
internal fun previewLine(workout: RoutineCode.SharedWorkout): String {
    val n = workout.entries.size
    return "${workout.name} \u00B7 $n ${if (n == 1) "exercise" else "exercises"}"
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
        title = { Text("Import code") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    shape = MaterialTheme.shapes.small,
                    colors = ironvellumFieldColors(),
                    value = text,
                    onValueChange = { text = it.take(RoutineCode.MAX_DECODED_BYTES) },
                    label = { Text("Cycle code") },
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
                        label = "Add as extra rites",
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
                            label = "Replace my cycle",
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
            IronvellumButton(label = "Cancel", onClick = onDismiss, quiet = true)
        },
    )

    if (confirmReplace && workouts != null) {
        IronvellumDialog(
            onDismissRequest = { confirmReplace = false },
            title = { Text("Replace my cycle?") },
            text = {
                val now = if (currentWorkouts == 1) "your 1 rite" else "all $currentWorkouts of your rites"
                Text(
                    "This deletes $now and your cycle, then adds the ${workouts.size} from this code. " +
                        "Sealed trials stay in the Chronicle.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                IronvellumButton(
                    label = "Replace",
                    onClick = { confirmReplace = false; onImport(workouts, true) },
                    danger = true,
                )
            },
            dismissButton = {
                IronvellumButton(label = "Cancel", onClick = { confirmReplace = false }, quiet = true)
            },
        )
    }
}
