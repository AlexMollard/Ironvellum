package com.ironvellum.app.ui.program

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ironvellum.app.R
import com.ironvellum.app.data.Words
import com.ironvellum.app.domain.FatigueLevel
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.ui.components.InfoAction
import com.ironvellum.app.ui.components.InfoSheet
import com.ironvellum.app.ui.theme.IronvellumColors

/**
 * "How fatigue is estimated": what is counted, how fast each kind of muscle recovers, the five levels, and the
 * plain statement that this is an estimate and soreness is no measure of a good session. Static copy: every
 * number in it is also a constant in [com.ironvellum.app.domain.FatigueTuning], said here in words.
 */
@Composable
internal fun FatigueExplainerSheet(onDismiss: () -> Unit) {
    InfoSheet(
        title = t(R.string.fatigue_explainer_title),
        onDismiss = onDismiss,
        actions = listOf(InfoAction(t(R.string.fatigue_explainer_done_button), onDismiss)),
    ) {
        text(null, Words.text(UiText.themed(R.string.fatigue_explainer_intro, R.string.fatigue_explainer_intro__t)))
        steps(
            t(R.string.fatigue_explainer_counts_title),
            listOf(
                t(R.string.fatigue_explainer_count_share),
                t(R.string.fatigue_explainer_count_effort),
                t(R.string.fatigue_explainer_count_kind),
                t(R.string.fatigue_explainer_count_new),
            ),
        )
        // Each pace is a name over its muscles: rows() gives the name a column too narrow for these words.
        section(t(R.string.fatigue_explainer_pace_title)) {
            listOf(
                R.string.fatigue_explainer_fast_label to R.string.fatigue_explainer_fast_value,
                R.string.fatigue_explainer_medium_label to R.string.fatigue_explainer_medium_value,
                R.string.fatigue_explainer_slow_label to R.string.fatigue_explainer_slow_value,
            ).forEach { (label, value) ->
                Text(t(label), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = IronvellumColors.Ink, modifier = Modifier.padding(top = 8.dp))
                Text(t(value), style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.InkMuted)
            }
        }
        text(null, t(R.string.fatigue_explainer_pace_note), IronvellumColors.InkMuted)
        section(t(R.string.fatigue_explainer_levels_title)) {
            FlowRow(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) { FatigueLevel.entries.forEach { FatigueLegendKey(it) } }
        }
        section(t(R.string.fatigue_explainer_cant_title)) {
            Text(
                Words.text(UiText.themed(R.string.fatigue_explainer_soreness, R.string.fatigue_explainer_soreness__t)),
                style = MaterialTheme.typography.bodyMedium,
                color = IronvellumColors.Ink,
            )
            Text(
                t(R.string.fatigue_explainer_estimate),
                style = MaterialTheme.typography.bodyMedium,
                color = IronvellumColors.InkMuted,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** A string resource as the sheet content builders need it: they are not composable, so no stringResource. */
private fun t(@StringRes id: Int, vararg args: Any): String = Words.text(UiText.res(id, *args))
