package com.ironvellum.app.ui.settings

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironvellum.app.domain.WorkoutCsvWriter
import com.ironvellum.app.ui.components.InkDivider
import com.ironvellum.app.ui.components.InkRowPanel
import com.ironvellum.app.ui.components.InkSpinner
import com.ironvellum.app.ui.components.ListRow
import com.ironvellum.app.ui.components.SettingsCaption
import com.ironvellum.app.ui.theme.IronvellumColors
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Export and the two imports, as rows in one panel. Restoring an archive replaces
 * everything, so it is the last row, in red, and keeps its confirm.
 */
@Composable
internal fun DataSettings(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val exporting by viewModel.exporting.collectAsStateWithLifecycle()
    val exportError by viewModel.exportError.collectAsStateWithLifecycle()
    val importUi by viewModel.import.collectAsStateWithLifecycle()
    val importReview by viewModel.importReview.collectAsStateWithLifecycle()
    val catalogueExercises by viewModel.catalogueExercises.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // The archive picked and read, held until the lifter confirms the restore.
    var pendingArchive by remember { mutableStateOf<String?>(null) }
    val exportTrials: (WorkoutCsvWriter.Period) -> Unit = { period ->
        viewModel.exportCsv(period) { fileName, csv ->
            scope.launch { shareExport(context, "Export trials", fileName, csv, "text/csv") }
        }
    }

    // Import replaces everything: the file is read first, so an empty or
    // unreadable one is reported before the destructive confirm, not after it.
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                // stream reads can stall on slow providers — never block the main thread
                val json = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)
                        ?.use { stream -> stream.readBytes().toString(Charsets.UTF_8) }
                        .orEmpty()
                }
                if (json.isBlank()) viewModel.reportEmptyImport() else pendingArchive = json
            }
        }
    }

    // CSV import: providers mislabel CSVs wildly, so offer every mime that
    // could be one and let the header sniff decide.
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val csv = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)
                        ?.use { stream -> stream.readBytes().toString(Charsets.UTF_8) }
                        .orEmpty()
                }
                if (csv.isBlank()) viewModel.reportEmptyImport() else viewModel.startCsvImport(csv)
            }
        }
    }

    pendingArchive?.let { archive ->
        SettingsConfirmDialog(
            title = "Restore this archive?",
            text = "Replaces your rites and cycle, Chronicle, readings, deeds and Journal on this device. This cannot be undone.",
            confirmLabel = "Restore",
            dismissLabel = "Keep local data",
            danger = true,
            onConfirm = {
                pendingArchive = null
                viewModel.importArchive(archive)
            },
            onDismiss = { pendingArchive = null },
        )
    }

    SettingsPage(SettingsSection.DATA.title, onBack) {
        Spacer(Modifier.height(12.dp))
        InkRowPanel(Modifier.fillMaxWidth()) {
            if (exporting) {
                InkSpinner(Modifier.padding(16.dp))
            } else {
                ListRow(
                    "Export archive",
                    subline = "A full JSON archive of everything on this device.",
                    onClick = {
                        viewModel.exportJson { json ->
                            scope.launch { shareExport(context, "Export Ironvellum data", "ironvellum_export.json", json) }
                        }
                    },
                )
                InkDivider()
                ListRow("Export trials this week", onClick = { exportTrials(WorkoutCsvWriter.Period.WEEK) })
                InkDivider()
                ListRow("Export trials this month", onClick = { exportTrials(WorkoutCsvWriter.Period.MONTH) })
                InkDivider()
                ListRow("Export trials this year", onClick = { exportTrials(WorkoutCsvWriter.Period.YEAR) })
            }
            InkDivider()
            ListRow(
                "Import from another app",
                subline = "Adds trials from a Strong or Hevy CSV export.",
                onClick = {
                    csvLauncher.launch(
                        arrayOf(
                            "text/csv",
                            "text/comma-separated-values",
                            "application/csv",
                            "application/vnd.ms-excel",
                            "text/plain",
                            "*/*",
                        ),
                    )
                },
            )
            InkDivider()
            if (importUi.importing) {
                InkSpinner(Modifier.padding(16.dp))
            } else {
                // Restore is the one destructive row: last, red, and behind its confirm.
                ListRow(
                    "Restore from a file",
                    subline = "Replaces all data on this device with an archive.",
                    sublineColor = IronvellumColors.DangerRed,
                    onClick = if (exporting) null else ({ importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }),
                )
            }
        }
        exportError?.let {
            Spacer(Modifier.height(8.dp))
            SettingsCaption(it, color = IronvellumColors.DangerRed)
        }
        importUi.summary?.let {
            Spacer(Modifier.height(8.dp))
            SettingsCaption(it)
        }
        importUi.problems?.let {
            Spacer(Modifier.height(4.dp))
            SettingsCaption(it)
        }
    }

    importReview?.let { review ->
        ImportReviewOverlay(
            ui = review,
            exercises = catalogueExercises,
            onPick = viewModel::chooseImportMapping,
            onUnitPick = viewModel::setImportUnit,
            onImport = viewModel::confirmCsvImport,
            onDismiss = viewModel::dismissImportReview,
        )
    }
}

/**
 * Shares the export as a FILE, not as an intent extra.
 *
 * Measured: five years of training exports ~0.9 MB of JSON, and binder caps a
 * transaction near 1 MB — `EXTRA_TEXT` would throw TransactionTooLargeException
 * on the one action whose whole purpose is getting a lifter's data out. Staged
 * in the cache directory the FileProvider exposes, so the receiving app reads it
 * through a content:// URI instead.
 */
internal suspend fun shareExport(
    context: Context,
    title: String,
    fileName: String,
    text: String,
    mimeType: String = "application/json",
) {
    val uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        // One name, overwritten: the cache is not an archive, and a stale
        // export left behind is a copy of everything the lifter has done.
        val file = File(dir, fileName)
        file.writeText(text)
        FileProvider.getUriForFile(context, "${context.packageName}.exports", file)
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_TITLE, fileName)
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, title))
}
