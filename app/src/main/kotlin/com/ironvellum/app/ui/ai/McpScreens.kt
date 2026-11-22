package com.ironvellum.app.ui.ai

import android.content.ClipData
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironvellum.app.R
import com.ironvellum.app.data.Units
import com.ironvellum.app.domain.Loads
import com.ironvellum.app.domain.LoadUnit
import com.ironvellum.app.data.cloud.*
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.domain.WorkoutDraft
import com.ironvellum.app.ui.asString
import com.ironvellum.app.ui.components.*
import com.ironvellum.app.ui.theme.IronvellumColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
internal fun draftTitle() = UiText.themed(R.string.ai_drafts, R.string.ai_drafts__t).asString()

private sealed interface Page {
    data object Loading : Page
    data class Error(val message: Int) : Page
    data class Connections(val data: McpConnections, val disconnected: String? = null) : Page
    data class Drafts(val rows: List<McpRepository.Review>, val nextCursor: String?, val error: Int? = null) : Page
    data class Review(val handle: McpRepository.Review, val error: Int? = null) : Page
    data class Saved(val name: String, val result: McpRepository.Saved) : Page
    data object Rejected : Page
}

@Composable
internal fun McpScreen(
    repository: McpRepository, connections: Boolean, configured: Boolean, onBack: () -> Unit, onSignIn: () -> Unit,
    onOpenWorkout: (Long) -> Unit,
) {
    val revision by repository.scopeChanges.collectAsStateWithLifecycle()
    key(repository, revision, configured) {
        val scope = remember { if (configured) repository.captureScope() else null }
        if (scope == null || !repository.isCurrent(scope)) {
            AiFrame(if (connections) stringResource(R.string.ai_connections) else draftTitle(), onBack) {
                AiStateCard(stringResource(if (configured) R.string.ai_sign_in_title else R.string.ai_setup_title),
                    Icons.Filled.Info, detail = stringResource(if (configured) R.string.ai_signed_out else R.string.ai_unconfigured))
                if (configured) IronvellumButton(stringResource(R.string.ai_sign_in), {
                    if (repository.captureScope() == null) onSignIn()
                }, Modifier.fillMaxWidth())
            }
        } else {
            ScopedPage(repository, scope, connections, onBack, onOpenWorkout)
        }
    }
}

@Composable
private fun ScopedPage(
    repository: McpRepository,
    scope: AccountRepository.Scope,
    connections: Boolean,
    onBack: () -> Unit,
    onOpenWorkout: (Long) -> Unit,
) {
    var reviewId by remember { mutableStateOf<String?>(null) }
    val back = { if (reviewId != null) reviewId = null else onBack() }
    BackHandler(reviewId != null) { reviewId = null }
    var refresh by remember { mutableIntStateOf(0) }
    key(reviewId, refresh) {
        var page by remember { mutableStateOf<Page>(Page.Loading) }
        var busy by remember { mutableStateOf(false) }
        val worker = rememberCoroutineScope()
        fun publish(next: Page) {
            if (repository.isCurrent(scope)) page = next
        }
        fun act(operation: suspend () -> Page) {
            if (busy || !repository.isCurrent(scope)) return
            busy = true
            worker.launch {
                try { publish(operation()) }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    val current = page
                    publish(when (current) {
                        is Page.Review -> current.copy(error = problem(e))
                        is Page.Drafts -> current.copy(error = problem(e))
                        else -> Page.Error(problem(e))
                    })
                } finally { busy = false }
            }
        }
        LaunchedEffect(refresh, connections) {
            if (connections) {
                try { repository.retryAcknowledgements(scope) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { /* Pending receipts can be retried on the next visit. */ }
            }
        }
        LaunchedEffect(refresh) {
            publish(Page.Loading)
            try {
                if (!connections) repository.retryAcknowledgements(scope)
                val data = repository.connections(scope)
                if (data.resourceUrl == null) throw UnsupportedMcpBackend()
                val id = reviewId
                publish(when {
                    connections -> Page.Connections(data)
                    id != null -> Page.Review(repository.draft(scope, id))
                    else -> repository.draftsPage(scope).let { Page.Drafts(it.rows, it.nextCursor) }
                })
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { publish(Page.Error(problem(e))) }
        }
        val title = when {
            connections -> stringResource(R.string.ai_connections)
            reviewId != null -> UiText.themed(R.string.ai_review, R.string.ai_review__t).asString()
            else -> draftTitle()
        }
        val current = page
        val actions: (@Composable ColumnScope.() -> Unit)? = if (current is Page.Review) {
            {
                val handle = current.handle
                ReviewActions(handle.envelope.status, current.error, busy,
                    onSave = { act { Page.Saved(handle.envelope.draft.name, repository.save(handle)) } },
                    onReject = { act { repository.reject(handle); Page.Rejected } })
            }
        } else null
        key(current::class) {
            AiFrame(title, back, titleMaxLines = 2, actions = actions) {
                when (current) {
                    Page.Loading -> AiStateCard(stringResource(R.string.ai_loading))
                    is Page.Error -> ConnectedProblem(current.message)
                    is Page.Connections -> Connections(current.data, current.disconnected, busy) { grant ->
                        act {
                            repository.revoke(scope, grant.id)
                            Page.Connections(repository.connections(scope).also { if (it.resourceUrl == null) throw UnsupportedMcpBackend() }, grant.clientName)
                        }
                    }
                    is Page.Drafts -> {
                        if (current.rows.isEmpty()) {
                            AiStateCard(stringResource(R.string.ai_empty_title), Icons.Filled.Info,
                                detail = stringResource(R.string.ai_empty_drafts))
                        } else {
                            AiTitle(stringResource(R.string.ai_ready))
                            AiDetail(stringResource(R.string.ai_draft_hint))
                            current.rows.forEach { item ->
                                val draft = item.envelope
                                AiCard(Modifier.clip(MaterialTheme.shapes.medium).clickable(enabled = !busy, role = Role.Button) {
                                    if (repository.isCurrent(scope)) reviewId = draft.id
                                }) {
                                    AiTitle(draft.draft.name)
                                    AiDraftCounts(draft.draft)
                                    AiDetail(stringResource(R.string.ai_source, draft.clientName))
                                    AiDetail(stringResource(R.string.ai_suggested, aiDate(draft.createdAtMs)))
                                    if (draft.status == DraftStatus.APPROVED) AiDetail(stringResource(R.string.ai_approved))
                                }
                            }
                        }
                        if (current.error != null) ConnectedProblem(current.error)
                        current.nextCursor?.let { cursor ->
                            IronvellumButton(stringResource(R.string.ai_load_older), {
                                act {
                                    val older = repository.draftsPage(scope, cursor)
                                    require(older.nextCursor == null || older.nextCursor != cursor)
                                    Page.Drafts((current.rows + older.rows).distinctBy { it.envelope.id }, older.nextCursor)
                                }
                            }, quiet = true, enabled = !busy)
                        }
                    }
                    is Page.Review -> ReviewBody(current.handle.envelope, current.error)
                    is Page.Saved -> {
                        AiStateCard(stringResource(R.string.ai_saved), Icons.Filled.Check,
                            name = current.name, accent = true,
                            detail = UiText.themed(R.string.ai_saved_info, R.string.ai_saved_info__t).asString())
                        if (current.result.acknowledgementPending)
                            AiStateCard(stringResource(R.string.ai_ack_title), Icons.Filled.Info,
                                detail = stringResource(R.string.ai_ack_pending))
                        IronvellumButton(UiText.themed(R.string.ai_view_workout, R.string.ai_view_workout__t).asString(), {
                            if (repository.isCurrent(scope)) onOpenWorkout(current.result.localPresetId)
                        }, Modifier.fillMaxWidth())
                        IronvellumButton(stringResource(R.string.ai_back_drafts), back, Modifier.fillMaxWidth(), quiet = true)
                    }
                    Page.Rejected -> {
                        AiStateCard(stringResource(R.string.ai_rejected), Icons.Filled.Check)
                        IronvellumButton(stringResource(R.string.ai_back_drafts), back, Modifier.fillMaxWidth())
                    }
                }
                if (busy) AiStateCard(stringResource(R.string.ai_working))
                if (current !is Page.Saved && current != Page.Rejected && current != Page.Loading) {
                    IronvellumButton(stringResource(R.string.ai_refresh), {
                        if (repository.isCurrent(scope)) refresh++
                    }, Modifier.fillMaxWidth(), quiet = true, enabled = !busy)
                }
            }
        }
    }
}

@Composable
private fun ConnectedProblem(message: Int) {
    AiStateCard(stringResource(if (message == R.string.ai_unavailable) R.string.ai_setup_title else R.string.ai_problem_title),
        Icons.Filled.Info, detail = stringResource(message))
}

@Composable
internal fun AiFrame(
    title: String,
    back: (() -> Unit)?,
    titleMaxLines: Int = 1,
    actions: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().imePadding(), contentAlignment = androidx.compose.ui.Alignment.TopCenter) {
        val scroll = rememberScrollState()
        // When enlarged text or a short window needs the room, actions scroll with the body.
        val pinActions = actions != null && maxHeight >= 440.dp * LocalDensity.current.fontScale
        Column(Modifier.widthIn(max = 680.dp).fillMaxSize().padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(12.dp))
            if (back != null) {
                PushedHeader(title, back, titleMaxLines = titleMaxLines)
            } else {
                Text(title, Modifier.heightIn(min = 52.dp).padding(vertical = 12.dp).semantics { heading() },
                    style = MaterialTheme.typography.titleMedium, color = IronvellumColors.Ink)
            }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                content()
                if (!pinActions) actions?.invoke(this)
                Spacer(Modifier.height(8.dp))
            }
            if (pinActions) {
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) { actions(this) }
            }
        }
    }
}

@Composable
internal fun AiTitle(text: String) = Text(text, style = MaterialTheme.typography.titleMedium, color = IronvellumColors.Ink)

@Composable
internal fun AiStatus(res: Int) = Text(stringResource(res),
    Modifier.padding(vertical = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
    style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.Ink)

internal fun aiDate(ms: Long) = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(ms))

@Composable
private fun permission(scope: String) = when (scope) {
    "training:read" -> stringResource(R.string.ai_training)
    "progress:read" -> stringResource(R.string.ai_progress)
    "savedworkouts:read" -> UiText.themed(R.string.ai_saved_scope, R.string.ai_saved_scope__t).asString()
    "workoutdrafts:create" -> UiText.themed(R.string.ai_create_scope, R.string.ai_create_scope__t).asString()
    else -> stringResource(R.string.ai_unknown_scope)
}

@Composable
private fun Connections(data: McpConnections, disconnected: String?, busy: Boolean, revoke: (McpConnection) -> Unit) {
    val clipboard = LocalClipboard.current
    val worker = rememberCoroutineScope()
    val address = data.resourceUrl ?: return
    AiCard {
        AiTitle(stringResource(R.string.ai_server))
        AiDetail(stringResource(R.string.ai_server_hint))
        SelectionContainer { AiDetail(address) }
        val copy = stringResource(R.string.ai_copy)
        IronvellumButton(copy, {
            worker.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(copy, address))) }
        }, Modifier.fillMaxWidth(), quiet = true)
    }
    if (disconnected != null)
        AiStateCard(stringResource(R.string.ai_revoked, disconnected), Icons.Filled.Check)
    AiCard {
        AiDetail(stringResource(R.string.ai_disclose))
        AiDetail(stringResource(R.string.ai_freshness))
    }
    AiTitle(stringResource(R.string.ai_apps))
    if (data.connections.isEmpty())
        AiStateCard(stringResource(R.string.ai_no_apps), Icons.Filled.Info)
    data.connections.forEach { grant ->
        AiCard {
            AiTitle(grant.clientName)
            AiDetail(stringResource(R.string.ai_unverified))
            grant.scopes.forEach { AiDetail(permission(it)) }
            when {
                grant.revokedAtMs != null -> AiStatus(R.string.ai_disconnected)
                grant.expiresAtMs <= System.currentTimeMillis() -> AiStatus(R.string.ai_expired)
                else -> {
                    AiDetail(stringResource(R.string.ai_expires, aiDate(grant.expiresAtMs)))
                    IronvellumButton(stringResource(R.string.ai_disconnect, grant.clientName), { revoke(grant) },
                        Modifier.fillMaxWidth(), danger = true, enabled = !busy)
                }
            }
        }
    }
    AiDisclosure(stringResource(R.string.ai_audit)) {
        if (data.recentAccess.isEmpty()) AiDetail(stringResource(R.string.ai_no_access))
        data.recentAccess.forEachIndexed { index, entry ->
            if (index > 0) InkDivider()
            AiTitle(entry.clientName)
            AiDetail(stringResource(R.string.ai_unverified))
            val action = when (entry.tool) {
                "list_workouts" -> R.string.ai_audit_workouts
                "get_workout" -> R.string.ai_audit_workout
                "get_progress" -> R.string.ai_audit_progress
                "list_saved_workouts" -> R.string.ai_audit_saved
                "search_movements" -> R.string.ai_audit_search
                "create_workout_draft" -> R.string.ai_audit_create
                "get_workout_draft" -> R.string.ai_audit_draft
                else -> R.string.ai_audit_unknown
            }
            AiDetail(stringResource(R.string.ai_audit_line, stringResource(action),
                stringResource(if (entry.result == "ok") R.string.ai_allowed else R.string.ai_unsuccessful), aiDate(entry.atMs)))
        }
    }
}

@Composable
private fun ReviewBody(envelope: DraftEnvelope, error: Int?) {
    val draft = envelope.draft
    AiCard {
        AiTitle(draft.name)
        AiDraftCounts(draft)
        AiDetail(stringResource(R.string.ai_source, envelope.clientName))
    }
    when (envelope.status) {
        DraftStatus.PENDING -> Unit
        DraftStatus.APPROVED -> AiStateCard(stringResource(R.string.ai_approved), Icons.Filled.Info)
        DraftStatus.ACCEPTED -> AiStateCard(stringResource(R.string.ai_accepted), Icons.Filled.Check)
        DraftStatus.REJECTED -> AiStateCard(stringResource(R.string.ai_rejected), Icons.Filled.Close)
        DraftStatus.EXPIRED -> AiStateCard(stringResource(R.string.ai_expired_draft), Icons.Filled.Info)
    }
    if (error != null) ConnectedProblem(error)
    DraftTargets(draft)
    AiDisclosure(stringResource(R.string.local_ai_draft_details)) {
        AiDetail(stringResource(R.string.ai_suggested, aiDate(envelope.createdAtMs)))
        AiDetail(stringResource(R.string.ai_expiry, aiDate(envelope.expiresAtMs)))
    }
}

@Composable
private fun ReviewActions(state: DraftStatus, error: Int?, busy: Boolean, onSave: () -> Unit, onReject: () -> Unit) {
    if (state.canSave) {
        AiDetail(UiText.themed(R.string.ai_save_info, R.string.ai_save_info__t).asString())
        val label = if (state == DraftStatus.APPROVED) stringResource(R.string.ai_finish)
            else UiText.themed(R.string.ai_save, R.string.ai_save__t).asString()
        IronvellumButton(label, onSave, Modifier.fillMaxWidth(), enabled = !busy && error == null)
    }
    if (state.canReject) IronvellumButton(stringResource(R.string.ai_reject), onReject, Modifier.fillMaxWidth(),
        danger = true, enabled = !busy && error != R.string.ai_stale)
}

@Composable
internal fun AiDraftCounts(draft: WorkoutDraft) {
    val movements = draft.entries.size
    val sets = draft.entries.sumOf { it.sets }
    AiDetail(stringResource(R.string.local_ai_draft_counts,
        pluralStringResource(R.plurals.effort_movements, movements, movements),
        pluralStringResource(R.plurals.common_sets_count, sets, sets)))
}

@Composable
internal fun DraftTargets(draft: WorkoutDraft, movementName: (String) -> String = { it.replaceFirstChar { c -> c.titlecase() } }) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AiTitle(stringResource(R.string.ai_movements))
        draft.entries.forEach { entry ->
            AiCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                AiTitle(movementName(entry.exerciseKey))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AiDetail(stringResource(R.string.ai_targets,
                        pluralStringResource(R.plurals.common_sets_count, entry.sets, entry.sets),
                        pluralStringResource(R.plurals.common_reps_count, entry.reps, entry.reps)))
                    val kg = entry.loadKg
                    if (kg == null) {
                        AiDetail(stringResource(R.string.ai_no_load))
                    } else {
                        val unit = Units.forExercise(entry.exerciseKey)
                        if (unit != LoadUnit.KG) AiDetail(Loads.withUnit(kg.toDouble(), unit))
                        // Keep the canonical value visible even when the preferred unit rounds it.
                        AiDetail(stringResource(R.string.ai_load, if (kg.scale() > 128) kg.toString() else kg.toPlainString()))
                    }
                }
            }
        }
    }
    SettingsGroup(stringResource(R.string.ai_preparation), rows = true) {
        listOf(R.string.ai_warmup to draft.warmup, R.string.ai_cooldown to draft.cooldown).forEachIndexed { i, (label, choice) ->
            if (i > 0) InkDivider()
            ListRow(stringResource(R.string.ai_prep_line, stringResource(label),
                stringResource(if (choice == WorkoutDraft.Preparation.AUTO) R.string.ai_auto else R.string.ai_none)))
        }
    }
}
