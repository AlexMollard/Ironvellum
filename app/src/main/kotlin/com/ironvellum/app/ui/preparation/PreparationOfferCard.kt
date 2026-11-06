package com.ironvellum.app.ui.preparation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
fun PreparationOfferCard(state: SessionPreparation, onBegin: () -> Unit, onChange: () -> Unit, onSkip: () -> Unit) {
    val kind = kindLabel(state.snapshot.kind)
    val finished = state.progress.status in listOf(PreparationStatus.DONE, PreparationStatus.SKIPPED)
    InkPanel(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), contentPadding = PaddingValues(12.dp)) {
        Text(kind, style = MaterialTheme.typography.titleMedium, color = IronvellumColors.Ink)
        if (state.snapshot.name.isNotBlank()) PreparationCaption(state.snapshot.name)
        val done = state.progress.completedUnitKeys.size + state.progress.skippedUnitKeys.size
        val total = state.snapshot.steps.sumOf { it.rounds * it.repetitions }
        if (finished) PreparationCaption(preparationText(
            if (state.progress.status == PreparationStatus.DONE) R.string.preparation_finished else R.string.preparation_skipped,
            if (state.progress.status == PreparationStatus.DONE) R.string.preparation_finished__t else R.string.preparation_skipped__t, kind))
        else if (done > 0) PreparationCaption(preparationText(R.string.preparation_progress, R.string.preparation_progress__t, done, total))
        else PreparationCaption(preparationCount(R.plurals.preparation_exercise_count, R.plurals.preparation_exercise_count__t, state.snapshot.steps.size))
        if (state.snapshot.steps.isNotEmpty()) IronvellumButton(
            if (finished) preparationText(R.string.preparation_preview, R.string.preparation_preview__t)
            else preparationText(if (state.progress.hasProgress) R.string.preparation_resume else R.string.preparation_begin,
                if (state.progress.hasProgress) R.string.preparation_resume__t else R.string.preparation_begin__t, kind), onBegin,
        )
        TextAction(preparationText(R.string.preparation_change, R.string.preparation_change__t), onChange)
        if (!finished && state.snapshot.steps.isNotEmpty()) TextAction(preparationText(R.string.preparation_skip, R.string.preparation_skip__t, kind), onSkip)
    }
}
