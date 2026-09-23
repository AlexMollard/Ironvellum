package com.ironvellum.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.unit.sp
import com.ironvellum.app.domain.CsvWorkoutReader
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.components.InkSegmented
import com.ironvellum.app.ui.components.InkSpinner
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.components.formatDate
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.IronvellumTracking

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
 */
@Composable
fun ImportReviewOverlay(
    ui: ImportReviewUi,
    catalogueNames: List<String>,
    onPick: (rawName: String, catalogueName: String?) -> Unit,
    onUnitPick: (CsvWorkoutReader.WeightUnit) -> Unit,
    onImport: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(24.dp))
            Text(
                "IMPORT FROM ANOTHER APP",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = ChakraPetch,
                color = IronvellumColors.SystemGreen,
                letterSpacing = IronvellumTracking.InlineLabel,
            )
            Spacer(Modifier.height(6.dp))

            val range = ui.parsed.dateRange
            Text(
                buildString {
                    append("${ui.parsed.workouts.size} workouts · ${ui.parsed.totalSets} sets")
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
                Text(it.message, style = MaterialTheme.typography.labelSmall, color = IronvellumColors.InkMuted)
            }

            // Strong only: the file carries no unit column, so the lifter
            // confirms. The pre-selection is the barbell heuristic, labelled
            // as an estimate — never a silent guess.
            if (ui.source == CsvWorkoutReader.Source.STRONG) {
                Spacer(Modifier.height(14.dp))
                InkPanel(Modifier.fillMaxWidth()) {
                    Text(
                        "WERE THESE WEIGHTS IN KG OR LB?",
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = ChakraPetch,
                        color = IronvellumColors.SystemGreen,
                    )
                    Spacer(Modifier.height(8.dp))
                    InkSegmented<CsvWorkoutReader.WeightUnit>(
                        options = listOf(
                            CsvWorkoutReader.WeightUnit.KG to "KG",
                            CsvWorkoutReader.WeightUnit.LB to "LB",
                        ),
                        selected = ui.selectedUnit ?: CsvWorkoutReader.WeightUnit.KG,
                        onPick = onUnitPick,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (ui.unitGuessed) {
                            "Estimated ${ui.selectedUnit?.name?.lowercase()} from your barbell numbers — check a familiar lift before importing."
                        } else {
                            "Strong exports carry no unit column, so choose."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = IronvellumColors.InkMuted,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                "MOVEMENTS WE COULD NOT MATCH",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = ChakraPetch,
                color = IronvellumColors.SystemGreen,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(6.dp))
            if (ui.unmatched.isEmpty()) {
                Text(
                    "Every movement matched the catalogue.",
                    style = MaterialTheme.typography.labelSmall,
                    color = IronvellumColors.InkMuted,
                )
            }
            ui.unmatched.forEach { name ->
                UnmatchedRow(
                    name = name,
                    choices = ui.choices,
                    catalogueNames = catalogueNames,
                    onPick = onPick,
                )
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(20.dp))
            if (ui.importing) {
                InkSpinner()
            } else {
                IronvellumButton(
                    label = if (ui.result == null) "IMPORT" else "DONE",
                    onClick = if (ui.result == null) onImport else onDismiss,
                    gold = true,
                    enabled = ui.parsed.workouts.isNotEmpty(),
                )
            }
            ui.result?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.labelSmall, color = IronvellumColors.InkMuted)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Nothing here replaces your log. Workouts already in the app are skipped; " +
                    "imported history stays on this device and off the public feed.",
                style = MaterialTheme.typography.labelSmall,
                color = IronvellumColors.InkMuted,
            )
            Spacer(Modifier.height(10.dp))
            IronvellumButton(
                label = "CANCEL",
                onClick = onDismiss,
                quiet = true,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun UnmatchedRow(
    name: UnmatchedImportName,
    choices: Map<String, String?>,
    catalogueNames: List<String>,
    onPick: (String, String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val chosen = choices[name.rawName]
    InkPanel(Modifier.fillMaxWidth()) {
        Text(
            name.rawName,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            when (chosen) {
                null -> "keep as NEW movement · measured in ${name.inferredMetric.lowercase()}"
                else -> "maps to $chosen"
            },
            style = MaterialTheme.typography.labelSmall,
            color = IronvellumColors.InkMuted,
        )
        Spacer(Modifier.height(8.dp))
        Box {
            Text(
                if (chosen == null) "CHOOSE" else "CHANGE",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = ChakraPetch,
                color = IronvellumColors.SystemGreen,
                modifier = Modifier
                    .clickable { expanded = true }
                    .padding(vertical = 4.dp),
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text("KEEP AS NEW MOVEMENT") },
                    onClick = {
                        expanded = false
                        onPick(name.rawName, null)
                    },
                )
                catalogueNames.forEach { catalogueName ->
                    DropdownMenuItem(
                        text = { Text(catalogueName) },
                        onClick = {
                            expanded = false
                            onPick(name.rawName, catalogueName)
                        },
                    )
                }
            }
        }
    }
}
