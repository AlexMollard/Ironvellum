package com.ironvellum.app.ui.ai

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
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
    data class Saved(val name: String, val acknowledgementPending: Boolean) : Page
    data object Rejected : Page
}

@Composable
internal fun McpScreen(
    repository: McpRepository, connections: Boolean, configured: Boolean, onBack: () -> Unit, onSignIn: () -> Unit,
) {
    val revision by repository.scopeChanges.collectAsStateWithLifecycle()
    key(revision, configured) {
        val scope = remember { repository.captureScope() }
        if (scope == null || !repository.isCurrent(scope)) {
            AiFrame(if (connections) stringResource(R.string.ai_connections) else draftTitle(), onBack) {
                AiStatus(if (configured) R.string.ai_signed_out else R.string.ai_unconfigured)
                if (configured) IronvellumButton(stringResource(R.string.ai_sign_in), onSignIn)
            }
        } else {
            ScopedPage(repository, scope, connections, onBack)
        }
    }
}

@Composable
private fun ScopedPage(repository: McpRepository, scope: AccountRepository.Scope, connections: Boolean, onBack: () -> Unit) {
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
        AiFrame(title, back) {
            when (val current = page) {
                Page.Loading -> AiStatus(R.string.ai_loading)
                is Page.Error -> AiStatus(current.message)
                is Page.Connections -> Connections(current.data, current.disconnected, busy) { grant ->
                    act {
                        repository.revoke(scope, grant.id)
                        Page.Connections(repository.connections(scope).also { if (it.resourceUrl == null) throw UnsupportedMcpBackend() }, grant.clientName)
                    }
                }
                is Page.Drafts -> {
                    SettingsCaption(stringResource(R.string.ai_draft_hint))
                    if (current.rows.isEmpty()) AiStatus(R.string.ai_empty_drafts)
                    else SettingsGroup(stringResource(R.string.ai_ready), rows = true) {
                        current.rows.forEach { item ->
                            val draft = item.envelope
                            SettingsValueRow(draft.draft.name, draft.clientName) { reviewId = draft.id }
                            SettingsCaption(stringResource(R.string.ai_suggested, aiDate(draft.createdAtMs)))
                            if (draft.status == DraftStatus.APPROVED) SettingsCaption(stringResource(R.string.ai_approved))
                            InkDivider()
                        }
                    }
                    if (current.error != null) AiStatus(current.error)
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
                is Page.Review -> {
                    val handle = current.handle
                    ReviewBody(handle.envelope, current.error, busy,
                        onSave = { act { Page.Saved(handle.envelope.draft.name, repository.save(handle).acknowledgementPending) } },
                        onReject = { act { repository.reject(handle); Page.Rejected } })
                }
                is Page.Saved -> {
                    AiStatus(R.string.ai_saved)
                    AiTitle(current.name)
                    SettingsCaption(UiText.themed(R.string.ai_saved_info, R.string.ai_saved_info__t).asString())
                    if (current.acknowledgementPending) AiStatus(R.string.ai_ack_pending)
                }
                Page.Rejected -> AiStatus(R.string.ai_rejected)
            }
            if (busy) AiStatus(R.string.ai_working)
            Spacer(Modifier.height(12.dp))
            if (page is Page.Saved || page == Page.Rejected) {
                IronvellumButton(stringResource(R.string.ai_back_drafts), back)
            } else if (page != Page.Loading) {
                IronvellumButton(stringResource(R.string.ai_refresh), { refresh++ }, quiet = true, enabled = !busy)
            }
        }
    }
}

@Composable
internal fun AiFrame(title: String, back: () -> Unit, titleMaxLines: Int = 1, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(16.dp))
        PushedHeader(title, back, titleMaxLines = titleMaxLines)
        content()
        Spacer(Modifier.height(32.dp))
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
    val clipboard = LocalClipboardManager.current
    val address = data.resourceUrl ?: return
    SettingsGroup(stringResource(R.string.ai_server)) {
        SettingsCaption(stringResource(R.string.ai_server_hint))
        AiTitle(address)
        IronvellumButton(stringResource(R.string.ai_copy), { clipboard.setText(AnnotatedString(address)) }, quiet = true)
    }
    if (disconnected != null) Text(stringResource(R.string.ai_revoked, disconnected), color = IronvellumColors.Ink)
    SettingsGroup(stringResource(R.string.ai_apps)) {
        if (data.connections.isEmpty()) SettingsCaption(stringResource(R.string.ai_no_apps))
        data.connections.forEach { grant ->
            AiTitle(grant.clientName)
            SettingsCaption(stringResource(R.string.ai_unverified))
            grant.scopes.forEach { SettingsCaption(permission(it)) }
            when {
                grant.revokedAtMs != null -> AiStatus(R.string.ai_disconnected)
                grant.expiresAtMs <= System.currentTimeMillis() -> AiStatus(R.string.ai_expired)
                else -> {
                    SettingsCaption(stringResource(R.string.ai_expires, aiDate(grant.expiresAtMs)))
                    IronvellumButton(stringResource(R.string.ai_disconnect, grant.clientName), { revoke(grant) }, danger = true, enabled = !busy)
                }
            }
        }
    }
    SettingsCaption(stringResource(R.string.ai_disclose))
    SettingsCaption(stringResource(R.string.ai_freshness))
    SettingsGroup(stringResource(R.string.ai_audit)) {
        if (data.recentAccess.isEmpty()) SettingsCaption(stringResource(R.string.ai_no_access))
        data.recentAccess.forEach {
            AiTitle(it.clientName)
            val action = when (it.tool) {
                "list_workouts" -> R.string.ai_audit_workouts
                "get_workout" -> R.string.ai_audit_workout
                "get_progress" -> R.string.ai_audit_progress
                "list_saved_workouts" -> R.string.ai_audit_saved
                "search_movements" -> R.string.ai_audit_search
                "create_workout_draft" -> R.string.ai_audit_create
                "get_workout_draft" -> R.string.ai_audit_draft
                else -> R.string.ai_audit_unknown
            }
            SettingsCaption(stringResource(R.string.ai_audit_line, stringResource(action),
                stringResource(if (it.result == "ok") R.string.ai_allowed else R.string.ai_unsuccessful), aiDate(it.atMs)))
        }
    }
}

@Composable
private fun ReviewBody(envelope: DraftEnvelope, error: Int?, busy: Boolean, onSave: () -> Unit, onReject: () -> Unit) {
    val draft = envelope.draft
    SettingsCaption(stringResource(R.string.ai_source, envelope.clientName))
    AiTitle(draft.name)
    SettingsCaption(stringResource(R.string.ai_suggested, aiDate(envelope.createdAtMs)))
    val state = envelope.status
    when (state) {
        DraftStatus.PENDING -> SettingsCaption(stringResource(R.string.ai_expiry, aiDate(envelope.expiresAtMs)))
        DraftStatus.APPROVED -> AiStatus(R.string.ai_approved)
        DraftStatus.ACCEPTED -> AiStatus(R.string.ai_accepted)
        DraftStatus.REJECTED -> AiStatus(R.string.ai_rejected)
        DraftStatus.EXPIRED -> AiStatus(R.string.ai_expired_draft)
    }
    DraftTargets(draft)
    if (error != null) AiStatus(error)
    if (state.canSave) {
        SettingsCaption(UiText.themed(R.string.ai_save_info, R.string.ai_save_info__t).asString())
        val label = if (state == DraftStatus.APPROVED) stringResource(R.string.ai_finish)
            else UiText.themed(R.string.ai_save, R.string.ai_save__t).asString()
        IronvellumButton(label, onSave, Modifier.fillMaxWidth(), enabled = !busy && error == null)
    }
    if (state.canReject) IronvellumButton(stringResource(R.string.ai_reject), onReject, danger = true, enabled = !busy && error != R.string.ai_stale)
}

@Composable
internal fun DraftTargets(draft: WorkoutDraft, movementName: (String) -> String = { it.replaceFirstChar { c -> c.titlecase() } }) {
    SettingsGroup(stringResource(R.string.ai_movements)) {
        draft.entries.forEachIndexed { i, entry ->
            if (i > 0) InkDivider()
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    AiTitle(movementName(entry.exerciseKey))
                    SettingsCaption(stringResource(R.string.ai_targets,
                        pluralStringResource(R.plurals.common_sets_count, entry.sets, entry.sets),
                        pluralStringResource(R.plurals.common_reps_count, entry.reps, entry.reps)))
                }
                val kg = entry.loadKg
                // An exact decimal can be long. Let it wrap without squeezing out the movement or rep targets.
                Column(Modifier.widthIn(max = 132.dp), horizontalAlignment = androidx.compose.ui.Alignment.End) {
                    if (kg == null) {
                        SettingsCaption(stringResource(R.string.ai_no_load))
                    } else {
                        val unit = Units.forExercise(entry.exerciseKey)
                        if (unit != LoadUnit.KG) SettingsCaption(Loads.withUnit(kg.toDouble(), unit))
                        // Keep the actual reviewed target visible even when the preferred unit rounds it.
                        SettingsCaption(stringResource(R.string.ai_load, if (kg.scale() > 80) kg.toString() else kg.toPlainString()))
                    }
                }
            }
        }
    }
    SettingsGroup(stringResource(R.string.ai_preparation)) {
        listOf(R.string.ai_warmup to draft.warmup, R.string.ai_cooldown to draft.cooldown).forEach { (label, choice) ->
            Text(stringResource(R.string.ai_prep_line, stringResource(label),
                stringResource(if (choice == WorkoutDraft.Preparation.AUTO) R.string.ai_auto else R.string.ai_none)), color = IronvellumColors.Ink)
        }
    }
}
