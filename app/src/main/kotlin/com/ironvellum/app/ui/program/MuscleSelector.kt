package com.ironvellum.app.ui.program

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ironvellum.app.R
import com.ironvellum.app.data.Words
import com.ironvellum.app.domain.CustomExercises
import com.ironvellum.app.domain.Muscle
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.ui.components.InkDivider
import com.ironvellum.app.ui.components.InkPickerSheet
import com.ironvellum.app.ui.components.ListRow
import com.ironvellum.app.ui.components.SectionHeader
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.components.selectedUnderline

/**
 * Full-width body input and explicit muscle roles. Diagram taps cycle assist, main, off; the searchable list
 * sets a role directly. Both keep tap order, so the first main muscle remains the dominant one when saved.
 */
@Composable
fun MuscleSelector(
    shares: Map<Muscle, Double>,
    onChange: (Map<Muscle, Double>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var namesOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val figure = BodyFigures.of(LocalBodySex.current)
    val lineFor = { muscle: Muscle -> Words.text(UiText.res(R.string.muscles_selector_line, muscle.label, levelWord(shares[muscle]))) }
    val setRole = { muscle: Muscle, share: Double ->
        onChange(LinkedHashMap(shares).apply {
            if (share == 0.0) remove(muscle) else put(muscle, share)
        })
    }
    Column(modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.muscles_selector_hint),
            style = MaterialTheme.typography.bodySmall,
            color = IronvellumColors.InkMuted,
        )
        Spacer(Modifier.height(16.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            TappableFigure(
                figure = figure,
                height = maxWidth / figureWidth(figure, 1.dp).value,
                fill = { muscle -> shareFill(shares[muscle]) },
                selected = null,
                onSelect = { muscle -> if (muscle != null) onChange(cycled(shares, muscle)) },
                lineFor = lineFor,
                modifier = Modifier.testTag("muscle_selector_figure"),
            )
        }
        if (shares.isNotEmpty()) {
            SectionHeader(stringResource(R.string.muscles_selector_selected), topPadding = 16.dp)
            shares.forEach { (muscle, share) ->
                InkDivider()
                MuscleRoleRow(muscle, share) { setRole(muscle, it) }
            }
            InkDivider()
        }
        ListRow(
            label = stringResource(R.string.muscles_selector_by_name),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
            onClick = { query = ""; namesOpen = true },
        )
    }
    if (namesOpen) {
        val search = query.trim()
        val matches = Muscle.entries.filter { it.label.contains(search, ignoreCase = true) }
        InkPickerSheet(
            title = stringResource(R.string.customex_muscles_label),
            onDismiss = { namesOpen = false },
            query = query,
            onQueryChange = { query = it },
            searchLabel = stringResource(R.string.muscles_selector_search),
            count = matches.size,
            countLabel = { pluralStringResource(R.plurals.muscles_selector_count, it, it) },
        ) {
            items(matches, key = { it.name }) { muscle ->
                MuscleRoleRow(muscle, shares[muscle] ?: 0.0) { setRole(muscle, it) }
                InkDivider()
            }
        }
    }
}

@Composable
private fun MuscleRoleRow(muscle: Muscle, share: Double, onChange: (Double) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        val stacked = maxWidth < 360.dp || LocalDensity.current.fontScale > 1.3f
        val label: @Composable () -> Unit = {
            Text(muscle.label, style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.Ink)
        }
        val roles: @Composable () -> Unit = {
            FlowRow(Modifier.selectableGroup()) {
                for (step in 0..2) {
                    val value = when (step) {
                        1 -> CustomExercises.ASSIST_SHARE
                        2 -> CustomExercises.MAIN_SHARE
                        else -> 0.0
                    }
                    val word = levelWord(value)
                    val description = stringResource(R.string.muscles_selector_role_description, muscle.label, word)
                    Box(
                        Modifier.heightIn(min = 48.dp)
                            .selectable(selected = share == value, role = Role.RadioButton, onClick = { onChange(value) })
                            .semantics { contentDescription = description },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier.selectedUnderline(share == value).heightIn(min = 32.dp)
                                .padding(horizontal = 9.dp, vertical = 5.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                word,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (share == value) IronvellumColors.Ink else IronvellumColors.InkMuted,
                            )
                        }
                    }
                }
            }
        }
        if (stacked) {
            Column(Modifier.padding(top = 10.dp)) {
                label()
                roles()
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { label() }
                roles()
            }
        }
    }
}

private fun levelWord(share: Double?): String = Words.text(
    UiText.res(
        when {
            share == null || share <= 0.0 -> R.string.muscles_level_off
            share >= CustomExercises.MAIN_SHARE -> R.string.muscles_level_main
            else -> R.string.muscles_level_assist
        },
    ),
)

/**
 * [shares] after a tap on [muscle]: off becomes assist, assist becomes main (keeping its place), main becomes
 * off. A muscle newly turned on joins the end, so the first MAIN muscle tapped stays the dominant one.
 */
internal fun cycled(shares: Map<Muscle, Double>, muscle: Muscle): Map<Muscle, Double> {
    val out = LinkedHashMap(shares)
    when (shares[muscle]) {
        null -> out[muscle] = CustomExercises.ASSIST_SHARE
        CustomExercises.ASSIST_SHARE -> out[muscle] = CustomExercises.MAIN_SHARE
        else -> out.remove(muscle)
    }
    return out
}
