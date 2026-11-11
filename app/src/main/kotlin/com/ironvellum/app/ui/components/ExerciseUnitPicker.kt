package com.ironvellum.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ironvellum.app.R
import com.ironvellum.app.data.Units
import com.ironvellum.app.domain.LoadUnit
import com.ironvellum.app.ui.theme.IronvellumColors

/** The same preference from About and a workout; changing it never rewrites a recorded load. */
@Composable
internal fun ExerciseUnitPicker(exerciseName: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val selected = Units.overrideFor(exerciseName)
    IronvellumDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.exercise_unit_title), style = MaterialTheme.typography.titleMedium)
                Text(exerciseName, color = IronvellumColors.InkMuted, style = MaterialTheme.typography.bodySmall)
            }
        },
        text = {
            Column(Modifier.selectableGroup()) {
                val options = listOf(
                    Triple(null, stringResource(R.string.exercise_unit_default, Units.name), stringResource(R.string.exercise_unit_default_hint)),
                    Triple(LoadUnit.KG, LoadUnit.KG.label, stringResource(R.string.exercise_unit_kg_hint)),
                    Triple(LoadUnit.LB, LoadUnit.LB.label, stringResource(R.string.exercise_unit_lb_hint)),
                )
                options.forEachIndexed { index, (unit, label, hint) ->
                    if (index > 0) InkDivider()
                    Row(
                        Modifier.fillMaxWidth()
                            .selectable(selected = selected == unit, role = Role.RadioButton, onClick = {
                                Units.setExercise(context, exerciseName, unit)
                                onDismiss()
                            })
                            .heightIn(min = 64.dp)
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RadioButton(selected = selected == unit, onClick = null)
                        Column(Modifier.weight(1f)) {
                            Text(label, color = IronvellumColors.Ink, style = MaterialTheme.typography.bodyMedium)
                            Text(hint, color = IronvellumColors.InkMuted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.infosheet_close_button)) } },
    )
}
