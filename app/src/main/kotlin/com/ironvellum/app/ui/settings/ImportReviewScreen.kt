package com.ironvellum.app.ui.settings

import androidx.activity.compose.BackHandler
import com.ironvellum.app.ui.components.PushedHeader
import com.ironvellum.app.ui.components.DockedActionBar
import com.ironvellum.app.ui.components.InkDivider
import com.ironvellum.app.ui.components.InkRowPanel
import com.ironvellum.app.ui.components.SectionHeader
import com.ironvellum.app.ui.components.TapRow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import com.ironvellum.app.domain.Exercise
import com.ironvellum.app.ui.components.ExercisePickerSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ironvellum.app.domain.CsvWorkoutReader
import com.ironvellum.app.ui.components.InkSegmented
import com.ironvellum.app.ui.components.InkSpinner
import com.ironvellum.app.ui.components.formatDate
import com.ironvellum.app.ui.components.plural
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
 * Full-screen review surface for a parsed Strong/Hevy CSV. Lives inside
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
            PushedHeader("Import from another app", onBack = onDismiss)
            Spacer(Modifier.height(4.dp))

            val range = ui.parsed.dateRange
            Text(
                buildString {
                    append("$trials ${plural(trials, "trial", "trials")}")
                    append(" · ${ui.parsed.totalSets} ${plural(ui.parsed.totalSets, "set", "sets")}")
                    if (range != null) {
                        append(" · from ${formatDate(range.second, "d MMM yyyy")}")
                        append(" to ${formatDate(range.first, "d MMM yyyy")}")
                    }
                },
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
                Text("Weight unit", style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted)
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
                        "Estimated ${ui.selectedUnit?.name?.lowercase()} from your barbell numbers. Check a familiar exercise before importing."
                    } else {
                        "Strong exports carry no unit column, so choose."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                )
            }

            SectionHeader("Exercises we could not match", topPadding = 22.dp)
            if (ui.unmatched.isEmpty()) {
                Text(
                    "Every exercise matched the catalogue.",
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
                "Nothing here replaces your Chronicle. Trials already in the app are skipped; " +
                    "imported trials stay on this device and out of the Tidings.",
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
                ui.importing -> "Importing"
                ui.result != null -> "Done"
                else -> "Import $trials ${plural(trials, "trial", "trials")}"
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
    val action = if (chosen == null) "Choose" else "Change"
    TapRow(onClickLabel = "$action match for ${name.rawName}", onClick = { picking = true }) {
        Column(Modifier.weight(1f)) {
            Text(
                name.rawName,
                style = MaterialTheme.typography.bodyMedium,
                color = IronvellumColors.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (chosen == null) "keep as new exercise · measured in ${name.inferredMetric.lowercase()}" else "maps to $chosen",
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
            title = "Match exercise",
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
                            "Keep as new exercise",
                            style = MaterialTheme.typography.bodyMedium,
                            color = IronvellumColors.SystemGreen,
                        )
                        Text(
                            "measured in ${name.inferredMetric.lowercase()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = IronvellumColors.InkMuted,
                        )
                    }
                }
            },
        )
    }
}
