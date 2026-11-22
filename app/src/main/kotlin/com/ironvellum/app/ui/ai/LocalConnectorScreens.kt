package com.ironvellum.app.ui.ai

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ironvellum.app.R
import com.ironvellum.app.data.localconnector.*
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.ui.asString
import com.ironvellum.app.ui.components.*
import com.ironvellum.app.ui.settings.sharePreparedFile
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private object CreateLocalTrainingDocument : ActivityResultContracts.CreateDocument(TRAINING_FILE_MIME) {
    override fun createIntent(context: Context, input: String): Intent =
        super.createIntent(context, input).putExtra(Intent.EXTRA_LOCAL_ONLY, true)
}

internal sealed interface LocalPage {
    data object Tools : LocalPage
    data class Preview(val export: LocalContextExport, @StringRes val status: Int? = null) : LocalPage {
        // A later preview cannot change the bytes behind an earlier chooser's read grant.
        val fileName get() = "ironvellum-training-${export.summary.snapshotId}.json"
    }
    data class Review(val handle: LocalDraftReview) : LocalPage
    data class Saved(val name: String?, val repeated: Boolean) : LocalPage
    data class FileError(val problem: Problem, val exporting: Boolean = false, val chooseAgain: Boolean = false,
        val review: Review? = null) : LocalPage
}

internal data class Problem(@StringRes val title: Int, @StringRes val detail: Int)

internal enum class LocalWork(@StringRes val status: Int, val cancellable: Boolean = false) {
    BUILD(R.string.local_ai_building, cancellable = true),
    READ(R.string.local_ai_reading, cancellable = true),
    SHARE(R.string.local_ai_sharing),
    WRITE(R.string.local_ai_writing),
    SAVE(R.string.local_ai_saving),
}

/** Back stack scoped. Preview bytes and a review handle survive rotation in memory only. */
internal class LocalConnectorViewModel(private val repo: LocalConnectorRepository, private val resolver: ContentResolver) : ViewModel() {
    var page by mutableStateOf<LocalPage>(LocalPage.Tools)
        private set
    var work by mutableStateOf<LocalWork?>(null)
        private set
    private var job: Job? = null
    private var pendingOutput: LocalPage.Preview? = null

    fun back() {
        if (work?.cancellable == false) return
        job?.cancel()
        work = null
        page = LocalPage.Tools
    }

    fun preview() = run(LocalWork.BUILD, exportError(LocalFailure.STORAGE)) {
        try {
            LocalPage.Preview(withContext(Dispatchers.IO) { repo.exportContext() })
        } catch (e: LocalExportException) {
            exportError(e.reason)
        }
    }

    fun share(context: Context, preview: LocalPage.Preview) {
        if (page !== preview || pendingOutput != null) return
        run(LocalWork.SHARE, preview.copy(status = R.string.local_ai_share_error)) {
            sharePreparedFile(context, context.getString(R.string.local_ai_share), preview.fileName, TRAINING_FILE_MIME) {
                stageTrainingShare(File(context.cacheDir, "exports"), preview.fileName) { it.write(preview.export.bytes) }
            }
            preview.copy(status = null)
        }
    }

    fun chooseDestination(preview: LocalPage.Preview, launch: (String) -> Unit) {
        if (page !== preview || work != null || pendingOutput != null) return
        pendingOutput = preview
        try {
            launch(preview.fileName)
        } catch (_: Exception) {
            pendingOutput = null
            page = preview.copy(status = R.string.local_ai_write_error)
        }
    }

    fun destinationChosen(uri: Uri?) {
        val preview = pendingOutput
        pendingOutput = null
        if (uri == null) return
        if (preview == null || page !== preview) {
            page = LocalPage.FileError(Problem(R.string.local_ai_preview_lost, R.string.local_ai_preview_again), exporting = true)
            return
        }
        run(LocalWork.WRITE, preview.copy(status = R.string.local_ai_write_error)) {
            withContext(Dispatchers.IO) { writeTrainingUri(resolver, uri, preview.export.bytes) }
            preview.copy(status = R.string.local_ai_file_saved)
        }
    }

    fun pickerUnavailable() {
        page = LocalPage.FileError(Problem(R.string.local_ai_picker_error, R.string.local_ai_picker_again))
    }

    fun open(uri: Uri?) {
        if (uri == null) return
        val readError = LocalPage.FileError(Problem(R.string.local_ai_read_error, R.string.local_ai_read_again), chooseAgain = true)
        run(LocalWork.READ, readError) {
            val result = try {
                val bytes = readDraftUri(resolver, uri)
                withContext(Dispatchers.IO) { repo.inspectDraft(bytes) }
            } catch (_: DraftFileTooLarge) {
                LocalInspection.Rejected(LocalFailure.FILE_LIMIT)
            }
            when (result) {
                is LocalInspection.Ready -> LocalPage.Review(result.review)
                is LocalInspection.AlreadySaved -> alreadySaved(result.localPresetId)
                is LocalInspection.Rejected -> if (result.reason == LocalFailure.STORAGE) readError else LocalPage.FileError(draftProblem(result.reason))
            }
        }
    }

    fun save(review: LocalPage.Review) {
        if (page !== review) return
        val storageError = LocalPage.FileError(draftProblem(LocalFailure.STORAGE), review = review)
        run(LocalWork.SAVE, storageError) {
            when (val result = withContext(Dispatchers.IO) { repo.saveDraft(review.handle) }) {
                is LocalSave.Saved -> LocalPage.Saved(review.handle.proposal.draft.name, repeated = false)
                is LocalSave.AlreadySaved -> alreadySaved(result.localPresetId)
                is LocalSave.Rejected -> when (result.reason) {
                    LocalFailure.STORAGE -> storageError
                    LocalFailure.WRONG_DATASET -> LocalPage.FileError(Problem(R.string.local_ai_changed, R.string.local_ai_changed_hint))
                    else -> LocalPage.FileError(draftProblem(result.reason))
                }
            }
        }
    }

    fun resume(review: LocalPage.Review) { page = review }

    private suspend fun alreadySaved(id: Long): LocalPage.Saved = LocalPage.Saved(repo.savedPresetName(id), repeated = true)

    private fun run(action: LocalWork, fallback: LocalPage, operation: suspend () -> LocalPage) {
        if (work != null) return
        work = action
        job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            val running = coroutineContext.job
            try {
                val next = try {
                    operation()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    fallback
                }
                // A blocking document provider can fail after cancellation and a new selection.
                if (job === running && running.isActive) page = next
            } finally {
                if (job === running) {
                    work = null
                    job = null
                }
            }
        }
        job?.start()
    }
}

private fun exportError(reason: LocalFailure) = LocalPage.FileError(
    when (reason) {
        LocalFailure.FILE_LIMIT -> Problem(R.string.local_ai_context_limit, R.string.local_ai_context_limit_hint)
        LocalFailure.INVALID_HISTORY -> Problem(R.string.local_ai_export_error, R.string.local_ai_history_error)
        else -> Problem(R.string.local_ai_export_error, R.string.local_ai_export_again)
    }, exporting = true,
)

private fun draftProblem(reason: LocalFailure): Problem = when (reason) {
    LocalFailure.INVALID_FILE -> Problem(R.string.local_ai_invalid, R.string.local_ai_invalid_hint)
    LocalFailure.UNSUPPORTED_FORMAT -> Problem(R.string.local_ai_unsupported, R.string.local_ai_unsupported_hint)
    LocalFailure.FILE_LIMIT -> Problem(R.string.local_ai_draft_limit, R.string.local_ai_draft_limit_hint)
    LocalFailure.WRONG_DATASET -> Problem(R.string.local_ai_foreign, R.string.local_ai_foreign_hint)
    LocalFailure.UNKNOWN_EXPORT -> Problem(R.string.local_ai_unknown, R.string.local_ai_unknown_hint)
    LocalFailure.CONFLICT -> Problem(R.string.local_ai_conflict, R.string.local_ai_conflict_hint)
    LocalFailure.EXPIRED -> Problem(R.string.local_ai_expired, R.string.local_ai_expired_hint)
    LocalFailure.FUTURE -> Problem(R.string.local_ai_future, R.string.local_ai_future_hint)
    LocalFailure.MOVEMENT -> Problem(R.string.local_ai_movement, R.string.ai_movement_missing)
    LocalFailure.CATALOGUE_LIMIT -> Problem(R.string.local_ai_movement, R.string.ai_catalogue_limit)
    LocalFailure.STORAGE, LocalFailure.INVALID_HISTORY -> Problem(R.string.local_ai_save_error, R.string.local_ai_save_again)
}

@Composable
internal fun LocalConnectorScreen(
    repository: LocalConnectorRepository,
    onBack: () -> Unit,
    onConnections: () -> Unit,
    onCloudDrafts: () -> Unit,
) {
    val context = LocalContext.current
    val model: LocalConnectorViewModel = viewModel(factory = viewModelFactory {
        initializer { LocalConnectorViewModel(repository, context.applicationContext.contentResolver) }
    })
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), model::open)
    val create = rememberLauncherForActivityResult(CreateLocalTrainingDocument, model::destinationChosen)
    val choose = {
        try {
            open.launch(arrayOf(TRAINING_FILE_MIME, "text/plain", "application/octet-stream"))
        } catch (_: Exception) {
            model.pickerUnavailable()
        }
    }
    val page = model.page
    val work = model.work
    val inside = page != LocalPage.Tools || work != null
    val back = if (inside) model::back else onBack
    BackHandler(inside, onBack = back)
    val title = when {
        work == LocalWork.BUILD || page is LocalPage.Preview || page is LocalPage.FileError && page.exporting -> stringResource(R.string.local_ai_share)
        work == LocalWork.READ || page is LocalPage.FileError -> localText(R.string.local_ai_draft, R.string.local_ai_draft__t)
        page is LocalPage.Review -> localText(R.string.ai_review, R.string.ai_review__t)
        page is LocalPage.Saved && page.repeated -> stringResource(R.string.local_ai_already_saved)
        page is LocalPage.Saved -> localText(R.string.local_ai_saved, R.string.local_ai_saved__t)
        else -> stringResource(R.string.local_ai_tools)
    }
    key(page::class, work == LocalWork.READ || work == LocalWork.BUILD) {
        AiFrame(title, back, titleMaxLines = 3) {
            if (work != null) AiStatus(work.status)
            if (work?.cancellable == true) {
                IronvellumButton(stringResource(R.string.common_cancel), back, Modifier.fillMaxWidth(), quiet = true)
            } else when (page) {
                LocalPage.Tools -> LocalTools(model::preview, choose, onConnections, onCloudDrafts)
                is LocalPage.Preview -> ExportPreview(page, work == null, { model.share(context, page) },
                    { model.chooseDestination(page) { create.launch(it) } })
                is LocalPage.Review -> ReviewDraft(page.handle, work == null, { model.save(page) }, back)
                is LocalPage.Saved -> LocalSaved(page, back)
                is LocalPage.FileError -> {
                    AiTitle(stringResource(page.problem.title))
                    AiStatus(page.problem.detail)
                    when {
                        page.review != null -> Primary(stringResource(R.string.local_ai_review_again), { model.resume(page.review) })
                        page.chooseAgain -> Primary(stringResource(R.string.local_ai_choose), choose)
                        else -> Primary(stringResource(R.string.local_ai_back), back)
                    }
                }
            }
        }
    }
}

@Composable
internal fun LocalAiRow(@StringRes title: Int, @StringRes subtitle: Int, @StringRes themed: Int = subtitle, onClick: () -> Unit) {
    val label = stringResource(title)
    ListRow(label, subline = localText(subtitle, themed),
        onClickLabel = stringResource(R.string.settings_row_open_label, label), onClick = onClick)
}

@Composable
private fun LocalTools(preview: () -> Unit, open: () -> Unit, connections: () -> Unit, cloudDrafts: () -> Unit) {
    SettingsCaption(stringResource(R.string.local_ai_intro))
    SettingsGroup(stringResource(R.string.local_ai_device), rows = true) {
        LocalAiRow(R.string.local_ai_share, R.string.local_ai_share_hint, onClick = preview)
        InkDivider()
        val title = localText(R.string.local_ai_import, R.string.local_ai_import__t)
        ListRow(title, subline = stringResource(R.string.local_ai_import_hint),
            onClickLabel = stringResource(R.string.settings_row_open_label, title), onClick = open)
    }
    SettingsGroup(stringResource(R.string.local_ai_cloud), rows = true) {
        LocalAiRow(R.string.local_ai_cloud_connections, R.string.local_ai_cloud_connections_hint, onClick = connections)
        InkDivider()
        val title = localText(R.string.local_ai_cloud_drafts, R.string.local_ai_cloud_drafts__t)
        ListRow(title, subline = stringResource(R.string.ai_draft_hint),
            onClickLabel = stringResource(R.string.settings_row_open_label, title), onClick = cloudDrafts)
    }
}

@Composable
private fun ExportPreview(page: LocalPage.Preview, enabled: Boolean, share: () -> Unit, saveFile: () -> Unit) {
    val summary = page.export.summary
    val coverage = summary.coverage
    SettingsCaption(stringResource(R.string.local_ai_snapshot, aiDate(summary.exportedAtMs)))
    SettingsGroup(stringResource(R.string.local_ai_contents), rows = true) {
        CoverageRow(localText(R.string.local_ai_completed, R.string.local_ai_completed__t),
            stringResource(R.string.local_ai_recent), coverage.workoutsIncluded, coverage.workoutsTruncated)
        InkDivider()
        CoverageRow(stringResource(R.string.local_ai_sets), localText(R.string.local_ai_sets_hint, R.string.local_ai_sets_hint__t),
            coverage.setsIncluded, coverage.setsTruncated + coverage.setsUnsupported)
        InkDivider()
        CoverageRow(localText(R.string.local_ai_presets, R.string.local_ai_presets__t), stringResource(R.string.local_ai_names),
            coverage.savedWorkoutsIncluded, coverage.savedWorkoutsTruncated + coverage.savedWorkoutsUnsupported)
    }
    Spacer(Modifier.height(8.dp))
    if (coverage.setsTruncated > 0 || coverage.setsUnsupported > 0) {
        SettingsCaption(stringResource(R.string.local_ai_set_omissions, coverage.setsTruncated, coverage.setsUnsupported))
    }
    if (coverage.savedWorkoutsTruncated > 0 || coverage.savedWorkoutsUnsupported > 0) {
        SettingsCaption(localText(R.string.local_ai_preset_omissions, R.string.local_ai_preset_omissions__t,
            coverage.savedWorkoutsTruncated, coverage.savedWorkoutsUnsupported))
    }
    SettingsCaption(localText(R.string.local_ai_coverage, R.string.local_ai_coverage__t))
    SectionHeader(stringResource(R.string.local_ai_private), topPadding = 16.dp)
    SettingsCaption(localText(R.string.local_ai_private_hint, R.string.local_ai_private_hint__t))
    Spacer(Modifier.height(12.dp))
    SettingsCaption(stringResource(R.string.local_ai_transfer))
    page.status?.let { AiStatus(it) }
    Spacer(Modifier.height(8.dp))
    Primary(stringResource(R.string.local_ai_share), share, enabled)
    Spacer(Modifier.height(8.dp))
    IronvellumButton(stringResource(R.string.local_ai_save_file), saveFile, enabled = enabled, quiet = true)
}

@Composable
private fun CoverageRow(title: String, details: String, included: Int, omitted: Long) {
    ListRow(title, subline = details, value = stringResource(R.string.local_ai_count, included, omitted), wrapValue = true)
}

@Composable
private fun ReviewDraft(review: LocalDraftReview, enabled: Boolean, save: () -> Unit, discard: () -> Unit) {
    val proposal = review.proposal
    SettingsCaption(stringResource(R.string.local_ai_sender, proposal.sender))
    AiTitle(proposal.draft.name)
    SettingsCaption(stringResource(if (review.isLatestExport) R.string.local_ai_snapshot else R.string.local_ai_older,
        aiDate(review.exportedAtMs)))
    SettingsCaption(stringResource(R.string.local_ai_created, aiDate(proposal.createdAtMs)))
    SettingsCaption(stringResource(R.string.ai_expiry, aiDate(proposal.expiresAtMs)))
    DraftTargets(proposal.draft) { LocalCatalogue.movements[it]?.name ?: it }
    SettingsCaption(localText(R.string.ai_save_info, R.string.ai_save_info__t))
    Spacer(Modifier.height(8.dp))
    Primary(localText(R.string.ai_save, R.string.ai_save__t), save, enabled)
    IronvellumButton(stringResource(R.string.local_ai_discard), discard, enabled = enabled, danger = true)
}

@Composable
private fun LocalSaved(page: LocalPage.Saved, back: () -> Unit) {
    page.name?.let { AiTitle(it) }
    val detail = when {
        !page.repeated -> localText(R.string.local_ai_saved_hint, R.string.local_ai_saved_hint__t)
        page.name == null -> localText(R.string.local_ai_deleted, R.string.local_ai_deleted__t)
        else -> stringResource(R.string.local_ai_repeated)
    }
    Spacer(Modifier.height(8.dp))
    SettingsCaption(detail)
    Spacer(Modifier.height(8.dp))
    SettingsCaption(stringResource(R.string.local_ai_receipt_hint))
    Spacer(Modifier.height(8.dp))
    Primary(stringResource(R.string.common_done), back)
}

@Composable
private fun Primary(label: String, onClick: () -> Unit, enabled: Boolean = true) =
    IronvellumButton(label, onClick, Modifier.fillMaxWidth(), enabled = enabled)

@Composable
private fun localText(@StringRes plain: Int, @StringRes themed: Int, vararg arguments: Any) =
    UiText.themed(plain, themed, *arguments).asString()
