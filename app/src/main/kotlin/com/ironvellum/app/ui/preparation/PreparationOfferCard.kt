package com.ironvellum.app.ui.preparation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ironvellum.app.R
import com.ironvellum.app.domain.*
import com.ironvellum.app.ui.components.*
import com.ironvellum.app.ui.theme.IronvellumColors

internal val PreparationProgress.hasProgress: Boolean
    get() = status != PreparationStatus.OPEN || completedUnitKeys.isNotEmpty() || skippedUnitKeys.isNotEmpty()

internal fun warmupOffered(state: SessionPreparation, enabled: Boolean): Boolean =
    state.progress.hasProgress || state.snapshot.steps.isNotEmpty() && (state.snapshot.sourceRoutineId != null || state.snapshot.legacyStepCount > 0 || enabled)

@Composable
fun PreparationOfferCard(state: SessionPreparation, onBegin: () -> Unit, onChange: () -> Unit, onSkip: () -> Unit, onView: () -> Unit = onBegin) {
    val kind = kindLabel(state.snapshot.kind)
    val finished = state.progress.status in listOf(PreparationStatus.DONE, PreparationStatus.SKIPPED)
    InkPanel(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), contentPadding = PaddingValues(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(kind, style = MaterialTheme.typography.labelSmall, color = IronvellumColors.InkMuted)
                Text(if (finished) preparationText(
                    if (state.progress.status == PreparationStatus.DONE) R.string.preparation_finished else R.string.preparation_skipped,
                    if (state.progress.status == PreparationStatus.DONE) R.string.preparation_finished__t else R.string.preparation_skipped__t, kind)
                    else preparationMetadata(state.snapshot), style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted)
            }
            PreparationMenu { close ->
                DropdownMenuItem(text = { Text(preparationText(R.string.preparation_change, R.string.preparation_change__t)) }, onClick = { close(); onChange() })
                if (!finished && state.snapshot.steps.isNotEmpty()) DropdownMenuItem(
                    text = { Text(preparationText(R.string.preparation_skip, R.string.preparation_skip__t, kind)) }, onClick = { close(); onSkip() })
            }
        }
        @Composable fun Title(modifier: Modifier) {
            Text(state.snapshot.name, style = MaterialTheme.typography.titleMedium, color = IronvellumColors.Ink,
                modifier = modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onView).padding(vertical = 10.dp))
        }
        @Composable fun Action() {
            if (state.snapshot.steps.isNotEmpty()) {
                if (finished) TextAction(preparationText(R.string.preparation_view, R.string.preparation_view__t), onView)
                else IronvellumButton(preparationText(if (state.progress.hasProgress) R.string.preparation_resume else R.string.preparation_begin,
                    if (state.progress.hasProgress) R.string.preparation_resume__t else R.string.preparation_begin__t, kind), onBegin)
            }
        }
        if (LocalDensity.current.fontScale > 1.3f) {
            Title(Modifier.fillMaxWidth())
            Action()
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Title(Modifier.weight(1f))
                Action()
            }
        }
    }
}
