package com.ironvellum.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ironvellum.app.R
import com.ironvellum.app.ui.theme.IronvellumColors

/**
 * The prompt a base exercise gets when a modifier or load makes it a dedicated exercise of its own
 * ([com.ironvellum.app.domain.ExerciseTwins]): the rite editor and the live trial ask the same words.
 * [notes] are extra lines for the trial (what stays as it was, a superset that gives way).
 */
@Composable
fun TwinSwitchDialog(
    twinName: String,
    notes: List<String> = emptyList(),
    /** False when the dedicated exercise is not in the catalogue, so there is nothing to switch to. */
    canSwitch: Boolean = true,
    onSwitch: () -> Unit,
    onKeep: () -> Unit,
    onDismiss: () -> Unit,
) {
    IronvellumDialog(
        onDismissRequest = onDismiss,
        text = {
            Column {
                Text(
                    stringResource(R.string.twin_switch_prompt, twinName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = IronvellumColors.Ink,
                )
                notes.forEach {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted, modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = { IronvellumButton(stringResource(R.string.twin_switch_button), enabled = canSwitch, onClick = onSwitch) },
        dismissButton = { IronvellumButton(stringResource(R.string.twin_keep_button), quiet = true, onClick = onKeep) },
    )
}
