package com.ironvellum.app.ui.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.ironvellum.app.R
import com.ironvellum.app.ui.components.DockedActionBar
import com.ironvellum.app.ui.components.InkDivider
import com.ironvellum.app.ui.components.ListRow
import com.ironvellum.app.ui.components.SettingsCaption
import com.ironvellum.app.ui.settings.ARCHIVE_MIME_TYPES
import com.ironvellum.app.ui.settings.CSV_MIME_TYPES
import com.ironvellum.app.ui.settings.ImportReviewOverlay
import com.ironvellum.app.ui.settings.RestoreConfirmDialog
import com.ironvellum.app.ui.settings.readPickedText
import com.ironvellum.app.ui.settings.settingsViewModel
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.inkBorder
import com.ironvellum.app.ui.theme.inkDot
import kotlinx.coroutines.launch

/**
 * The front door, shown once before the first setup step. It names the app, says what setup is, and
 * offers the two ways in that are not a fresh start: a backup to restore and another app's CSV. Both
 * reuse Settings' Data flows (the file is read first, a restore is confirmed first) so they cannot
 * drift from what a lifter would meet later in Settings.
 *
 * A restore that brings a body profile and rites finishes setup through [onRestoreFinished]; a CSV
 * import only adds trials, so it returns here for the lifter to begin.
 */
@Composable
internal fun WelcomeScreen(
    onBegin: () -> Unit,
    onRestoreStarted: () -> Unit,
    onRestoreFinished: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = settingsViewModel(checkNotNull(LocalViewModelStoreOwner.current))
    val importUi by settings.import.collectAsStateWithLifecycle()
    val importReview by settings.importReview.collectAsStateWithLifecycle()
    val catalogue by settings.catalogueExercises.collectAsStateWithLifecycle()
    // The archive picked and read, held until the lifter confirms the restore.
    var pendingArchive by remember { mutableStateOf<String?>(null) }

    val archivePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val json = readPickedText(context, uri)
                if (json.isBlank()) settings.reportEmptyImport() else pendingArchive = json
            }
        }
    }
    val csvPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val csv = readPickedText(context, uri)
                if (csv.isBlank()) settings.reportEmptyImport() else settings.startCsvImport(csv)
            }
        }
    }

    // A restore has finished when the importing flag drops; the setup flow then looks at what came back.
    var wasImporting by remember { mutableStateOf(false) }
    LaunchedEffect(importUi.importing) {
        if (wasImporting && !importUi.importing) onRestoreFinished()
        wasImporting = importUi.importing
    }

    pendingArchive?.let { archive ->
        RestoreConfirmDialog(
            onConfirm = {
                pendingArchive = null
                onRestoreStarted()
                settings.importArchive(archive)
            },
            onDismiss = { pendingArchive = null },
        )
    }

    importReview?.let { review ->
        ImportReviewOverlay(
            ui = review,
            exercises = catalogue,
            onPick = settings::chooseImportMapping,
            onUnitPick = settings::setImportUnit,
            onImport = settings::confirmCsvImport,
            onDismiss = settings::dismissImportReview,
        )
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(IronvellumColors.Abyss)
            .statusBarsPadding(),
    ) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Motes()
            Column(
                Modifier.padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                LauncherMark()
                Text(
                    "Ironvellum",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = IronvellumColors.Ink,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 20.dp),
                )
                Text(
                    "Seal each trial. Keep the Ledger. Watch the Veil fill.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = IronvellumColors.InkMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 10.dp).widthIn(max = 300.dp),
                )
                Text(
                    "Three short steps: who you are, how you train, and the cycle the Forge builds for you.",
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 28.dp).widthIn(max = 320.dp),
                )
            }
        }

        // The two ways in that are not a fresh start.
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
            Text(
                "Already train somewhere?",
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
                modifier = Modifier.padding(start = 2.dp, bottom = 4.dp),
            )
            InkDivider()
            ListRow(
                "I have a backup",
                subline = "Restore an Ironvellum archive from a file",
                onClickLabel = "Restore a backup from a file",
                onClick = { archivePicker.launch(ARCHIVE_MIME_TYPES) },
            )
            InkDivider()
            ListRow(
                "Import from Strong or Hevy",
                subline = "Adds your trials from a CSV export. You still set up next.",
                onClickLabel = "Import trials from a CSV file",
                onClick = { csvPicker.launch(CSV_MIME_TYPES) },
            )
            InkDivider()
            // What the last pick said: a file that could not be read, or what a restore or import did.
            importUi.summary?.let {
                Spacer(Modifier.height(8.dp))
                SettingsCaption(it)
            }
            importUi.problems?.let {
                Spacer(Modifier.height(4.dp))
                SettingsCaption(it)
            }
        }

        DockedActionBar(primary = "Begin", onPrimary = onBegin, reserveLink = false)
    }
}

/**
 * The real launcher icon: the adaptive icon's foreground over its #0C0C0B background, cut to the app's
 * own corner shape, with a Rune hairline so it reads against the same ground.
 */
@Composable
private fun LauncherMark() {
    val shape = MaterialTheme.shapes.large
    Box(
        Modifier
            .size(136.dp)
            .clip(shape)
            .background(IronvellumColors.Abyss)
            .inkBorder(IronvellumColors.Rune, shape, 1.dp)
            .semantics { contentDescription = "Ironvellum" },
    ) {
        Image(
            painter = painterResource(R.mipmap.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Faint motes behind the mark, as on the Veil: a fixed field, so the screen never moves on its own. */
@Composable
private fun Motes() {
    Canvas(Modifier.fillMaxSize()) {
        // (x, y, radius dp, alpha) over a 412 x 600 field.
        listOf(
            floatArrayOf(40f, 80f, 2f, .14f), floatArrayOf(92f, 210f, 1.5f, .10f), floatArrayOf(64f, 340f, 2.5f, .10f),
            floatArrayOf(350f, 110f, 2.5f, .10f), floatArrayOf(318f, 260f, 1.5f, .12f), floatArrayOf(372f, 380f, 2f, .14f),
            floatArrayOf(210f, 60f, 1.5f, .10f), floatArrayOf(150f, 420f, 2f, .10f), floatArrayOf(268f, 450f, 1.5f, .12f),
        ).forEach { (x, y, r, a) ->
            inkDot(
                Offset(x / 412f * size.width, y / 600f * size.height),
                r.dp.toPx(),
                IronvellumColors.Ink.copy(alpha = a),
            )
        }
    }
}
