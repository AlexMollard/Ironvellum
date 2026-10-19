package com.ironvellum.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ironvellum.app.R
import com.ironvellum.app.domain.CsvWorkoutReader
import com.ironvellum.app.domain.Exercise
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.ui.asString
import com.ironvellum.app.ui.components.DockedActionBar
import com.ironvellum.app.ui.components.ExercisePickerSheet
import com.ironvellum.app.ui.components.InkDivider
import com.ironvellum.app.ui.components.InkRowPanel
import com.ironvellum.app.ui.components.InkSegmented
import com.ironvellum.app.ui.components.InkSpinner
import com.ironvellum.app.ui.components.PushedHeader
import com.ironvellum.app.ui.components.SectionHeader
import com.ironvellum.app.ui.components.TapRow
import com.ironvellum.app.ui.components.formatDate
import com.ironvellum.app.ui.theme.IronvellumColors

/**
 * One review row per movement name the curated aliases could NOT resolve.
 * The lifter picks a catalogue movement, or keeps the imported name as a NEW
 * movement (its metric inferred from the columns the file filled). Nothing
 * here is optional to answer: the IMPORT button stays enabled either way,
 * because "keep as new" is always a valid choice — but no name is silently
 * matched to something it is not.
 */
data class UnmatchedImportName(
    val rawName: String,
    /** Metric a NEW movement would take, inferred from the file's columns. */
    val inferredMetric: String,
)

/**
 * State of the in-flight CSV review, held by SettingsViewModel. `choices`
 * maps each unmatched raw name to the chosen catalogue name, or null for
 * KEEP AS NEW MOVEMENT.
 */
data class ImportReviewUi(
    val source: CsvWorkoutReader.Source,
    val parsed: CsvWorkoutReader.ParsedImport,
    /** Strong only: the current unit the file's Weight column is read as. */
    val selectedUnit: CsvWorkoutReader.WeightUnit? = null,
    /** True when the pre-selection came from the barbell heuristic. */
    val unitGuessed: Boolean = false,
    val unmatched: List<UnmatchedImportName> = emptyList(),
    val choices: Map<String, String?> = emptyMap(),
    val importing: Boolean = false,
    val result: String? = null,
)

/**
 * Full-screen review surface for a parsed Strong/Hevy/FitNotes CSV. Lives inside
 * Settings (an overlay, not a nav route) because the parsed payload is held
 * in the SettingsViewModel — a route would need a process-wide holder to
 * survive navigation, and this review is a step of the Settings import flow.
 * System back closes the review, not Data underneath it.
 */
@Composable
fun ImportReviewOverlay(
    ui: ImportReviewUi,
    exercises: List<Exercise>,
    onPick: (rawName: String, catalogueName: String?) -> Unit,
    onUnitPick: (CsvWorkoutReader.WeightUnit) -> Unit,
    onImport: () -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val trials = ui.parsed.workouts.size
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(8.dp))
            PushedHeader(stringResource(R.string.settings_data_import_label), onBack = onDismiss)
            Spacer(Modifier.height(4.dp))

            val range = ui.parsed.dateRange
            Text(
                listOfNotNull(
                    UiText.themedPlural(R.plurals.settings_import_review_sessions, R.plurals.settings_import_review_sessions__t, trials).asString(),
                    pluralStringResource(R.plurals.common_sets_count, ui.parsed.totalSets, ui.parsed.totalSets),
                    range?.let {
                        stringResource(
                            R.string.settings_import_review_range,
                            formatDate(it.second, "d MMM yyyy"),
                            formatDate(it.first, "d MMM yyyy"),
                        )
                    },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
            )
            ui.parsed.problems.firstOrNull()?.let {
                Spacer(Modifier.height(4.dp))
                Text(it.message, style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted)
            }

            // Strong only: the file carries no unit column, so the lifter
            // confirms. The pre-selection is the barbell heuristic, labelled
            // as an estimate — never a silent guess.
            if (ui.source == CsvWorkoutReader.Source.STRONG) {
                Spacer(Modifier.height(18.dp))
                Text(stringResource(R.string.settings_import_review_unit_label), style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted)
                Spacer(Modifier.height(6.dp))
                InkSegmented<CsvWorkoutReader.WeightUnit>(
                    options = listOf(
                        CsvWorkoutReader.WeightUnit.KG to "kg",
                        CsvWorkoutReader.WeightUnit.LB to "lb",
                    ),
                    selected = ui.selectedUnit ?: CsvWorkoutReader.WeightUnit.KG,
                    onPick = onUnitPick,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (ui.unitGuessed) {
                        stringResource(R.string.settings_import_review_unit_estimated, ui.selectedUnit?.name?.lowercase().orEmpty())
                    } else {
                        stringResource(R.string.settings_import_review_unit_choose)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                )
            }

            SectionHeader(stringResource(R.string.settings_import_review_unmatched_title), topPadding = 22.dp)
            if (ui.unmatched.isEmpty()) {
                Text(
                    stringResource(R.string.settings_import_review_all_matched),
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                )
            } else {
                InkRowPanel {
                    ui.unmatched.forEachIndexed { i, name ->
                        if (i > 0) InkDivider()
                        UnmatchedRow(
                            name = name,
                            chosen = ui.choices[name.rawName],
                            exercises = exercises,
                            onPick = onPick,
                        )
                    }
                }
            }

            ui.result?.let {
                Spacer(Modifier.height(14.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted)
            }
            Spacer(Modifier.height(14.dp))
            Text(
                UiText.themed(R.string.settings_import_review_note, R.string.settings_import_review_note__t).asString(),
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
            )
            if (ui.importing) {
                Spacer(Modifier.height(14.dp))
                InkSpinner()
            }
            Spacer(Modifier.height(16.dp))
        }
        DockedActionBar(
            primary = when {
                ui.importing -> stringResource(R.string.settings_import_review_importing)
                ui.result != null -> stringResource(R.string.common_done)
                else -> UiText.themedPlural(R.plurals.settings_import_review_button, R.plurals.settings_import_review_button__t, trials).asString()
            },
            onPrimary = if (ui.result == null) onImport else onDismiss,
            primaryEnabled = !ui.importing && (ui.result != null || trials > 0),
            reserveLink = false,
        )
    }
}

@Composable
private fun UnmatchedRow(
    name: UnmatchedImportName,
    chosen: String?,
    exercises: List<Exercise>,
    onPick: (String, String?) -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    val action = stringResource(if (chosen == null) R.string.settings_import_review_choose else R.string.settings_import_review_change)
    val actionLabel = stringResource(
        if (chosen == null) R.string.settings_import_review_choose_label else R.string.settings_import_review_change_label,
        name.rawName,
    )
    TapRow(onClickLabel = actionLabel, onClick = { picking = true }) {
        Column(Modifier.weight(1f)) {
            Text(
                name.rawName,
                style = MaterialTheme.typography.bodyMedium,
                color = IronvellumColors.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (chosen == null) {
                    stringResource(R.string.settings_import_review_keep_new_measured, name.inferredMetric.lowercase())
                } else {
                    stringResource(R.string.settings_import_review_maps_to, chosen)
                },
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
            )
        }
        Text(
            action,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = IronvellumColors.SystemGreen,
        )
    }
    if (picking) {
        ExercisePickerSheet(
            exercises = exercises,
            title = stringResource(R.string.settings_import_review_match_title),
            // Old history is often gym work the saved gear cannot do; the
            // chip is still there, but the mapping starts unfiltered.
            defaultMyGear = false,
            onPick = { exercise ->
                picking = false
                onPick(name.rawName, exercise.name)
            },
            onDismiss = { picking = false },
            topContent = {
                item(key = "keep_new") {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button) {
                                picking = false
                                onPick(name.rawName, null)
                            }
                            .heightIn(min = 52.dp)
                            .padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            stringResource(R.string.settings_import_review_keep_new),
                            style = MaterialTheme.typography.bodyMedium,
                            color = IronvellumColors.SystemGreen,
                        )
                        Text(
                            stringResource(R.string.settings_import_review_measured_in, name.inferredMetric.lowercase()),
                            style = MaterialTheme.typography.bodySmall,
                            color = IronvellumColors.InkMuted,
                        )
                    }
                }
            },
        )
    }
}
