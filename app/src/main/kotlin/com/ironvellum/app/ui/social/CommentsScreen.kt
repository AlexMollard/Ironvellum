package com.ironvellum.app.ui.social

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ironvellum.app.data.cloud.AccountRepository
import com.ironvellum.app.data.cloud.CloudSync
import com.ironvellum.app.data.cloud.Comment
import com.ironvellum.app.data.cloud.ReportReason
import com.ironvellum.app.data.cloud.WireLimits
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.components.IronvellumTabPill
import com.ironvellum.app.ui.ironvellumAccount
import com.ironvellum.app.ui.ironvellumCloudSync
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.IronvellumTracking
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class CommentsUi(
    val loading: Boolean = true,
    val comments: List<Comment> = emptyList(),
    /** The thread read failed; shown in place of the list when it is empty. */
    val error: String? = null,
    /** A send, delete or report the server refused — verbatim, so rate limits read as written. */
    val actionError: String? = null,
    /** A quiet confirmation, e.g. that a report went through. */
    val notice: String? = null,
    val sending: Boolean = false,
    val draft: String = "",
    val myUserId: String? = null,
)

internal class CommentsViewModel(
    private val cloud: CloudSync,
    accountRepo: AccountRepository,
    private val sessionId: String,
) : ViewModel() {

    private val _ui = MutableStateFlow(CommentsUi(myUserId = accountRepo.account.value?.userId))
    val ui: StateFlow<CommentsUi> = _ui.asStateFlow()

    /** Comment ids with a delete in flight: a double tap on the confirm must not fire twice. */
    private val deleting = mutableSetOf<String>()

    private fun Throwable.reason(): String = message ?: this::class.simpleName ?: "Unknown failure"

    init {
        viewModelScope.launch {
            accountRepo.account.collect { _ui.value = _ui.value.copy(myUserId = it?.userId) }
        }
        load()
    }

    /** [force] only from the explicit retry: the thread read is cached. */
    fun load(force: Boolean = false) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true, error = null)
            cloud.comments(sessionId, force = force)
                .onSuccess { _ui.value = _ui.value.copy(comments = it) }
                .onFailure { _ui.value = _ui.value.copy(error = it.reason()) }
            _ui.value = _ui.value.copy(loading = false)
        }
    }

    fun onDraft(text: String) {
        _ui.value = _ui.value.copy(draft = text.take(WireLimits.COMMENT_MAX))
    }

    fun send() {
        val current = _ui.value
        if (current.sending || current.draft.isBlank()) return
        _ui.value = current.copy(sending = true, actionError = null, notice = null)
        viewModelScope.launch {
            cloud.addComment(sessionId, current.draft)
                // The draft clears only on success: a refused comment (rate
                // limit, thread full) must not throw away what was typed.
                .onSuccess { comment ->
                    _ui.value = _ui.value.copy(
                        comments = _ui.value.comments.filter { it.id != comment.id } + comment,
                        draft = "",
                    )
                }
                .onFailure { _ui.value = _ui.value.copy(actionError = it.reason()) }
            _ui.value = _ui.value.copy(sending = false)
        }
    }

    fun delete(comment: Comment) {
        if (!deleting.add(comment.id)) return
        _ui.value = _ui.value.copy(actionError = null, notice = null)
        viewModelScope.launch {
            cloud.deleteComment(comment.id)
                .onSuccess { _ui.value = _ui.value.copy(comments = _ui.value.comments.filter { it.id != comment.id }) }
                .onFailure { _ui.value = _ui.value.copy(actionError = it.reason()) }
            deleting -= comment.id
        }
    }

    fun report(comment: Comment, reason: ReportReason, note: String) {
        _ui.value = _ui.value.copy(actionError = null, notice = null)
        viewModelScope.launch {
            cloud.report(
                targetUserId = comment.userId,
                reason = reason,
                note = note,
                sessionId = sessionId,
                commentId = comment.id,
            )
                .onSuccess { _ui.value = _ui.value.copy(notice = "Report sent — thanks.") }
                .onFailure { _ui.value = _ui.value.copy(actionError = it.reason()) }
        }
    }
}

/**
 * One workout's comment thread. [ownerId] is the workout's lifter: they may
 * delete any comment on it, everyone else only their own.
 */
@Composable
internal fun CommentsScreen(
    sessionId: String,
    ownerId: String,
    headline: String,
    onBack: () -> Unit,
    onOpenLifter: (userId: String, displayName: String) -> Unit,
    viewModel: CommentsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { CommentsViewModel(ironvellumCloudSync(), ironvellumAccount(), sessionId) }
        },
    ),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf<Comment?>(null) }
    var reporting by remember { mutableStateOf<Comment?>(null) }
    val listState = rememberLazyListState()
    // Land on the newest comment: the thread reads oldest first, and a sent
    // comment appearing below the fold looked like it had not been posted.
    LaunchedEffect(ui.comments.size) {
        if (ui.comments.isNotEmpty()) listState.animateScrollToItem(ui.comments.lastIndex)
    }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        PushedHeader("COMMENTS", onBack)
        Text(
            headline,
            style = MaterialTheme.typography.titleMedium,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = IronvellumColors.Ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(10.dp))

        val err = ui.error
        Column(Modifier.weight(1f)) {
            when {
                ui.loading && ui.comments.isEmpty() -> InkPanel(Modifier.fillMaxWidth(), accent = IronvellumColors.Rune) {
                    Text(
                        "Reading the thread…",
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = ChakraPetch,
                        color = IronvellumColors.InkMuted,
                    )
                }
                ui.comments.isEmpty() && err != null -> InkPanel(Modifier.fillMaxWidth(), accent = IronvellumColors.DangerRed) {
                    Text(
                        err,
                        style = MaterialTheme.typography.bodySmall,
                        color = IronvellumColors.InkMuted,
                    )
                    Spacer(Modifier.height(6.dp))
                    SocialRefreshLink(onClick = { viewModel.load(force = true) }, label = "Try again")
                }
                ui.comments.isEmpty() -> InkPanel(Modifier.fillMaxWidth(), accent = IronvellumColors.Emerald) {
                    Text(
                        "No comments yet — say something first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = IronvellumColors.InkMuted,
                    )
                }
                else -> LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(ui.comments, key = { it.id }) { comment ->
                        val mine = comment.userId == ui.myUserId
                        CommentRow(
                            comment = comment,
                            mine = mine,
                            canDelete = ui.myUserId != null && (mine || ownerId == ui.myUserId),
                            canReport = ui.myUserId != null && !mine,
                            onOpenLifter = { onOpenLifter(comment.userId, comment.authorName) },
                            onDelete = { confirmDelete = comment },
                            onReport = { reporting = comment },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        ui.actionError?.let {
            SocialErrorBanner(it)
            Spacer(Modifier.height(6.dp))
        }
        ui.notice?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = ChakraPetch,
                color = IronvellumColors.EmeraldBright,
            )
            Spacer(Modifier.height(6.dp))
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                shape = MaterialTheme.shapes.small,
                value = ui.draft,
                onValueChange = viewModel::onDraft,
                label = { Text("Comment") },
                maxLines = 4,
                modifier = Modifier.weight(1f),
            )
            IronvellumButton(
                label = if (ui.sending) "Sending…" else "Send",
                onClick = viewModel::send,
                enabled = ui.draft.isNotBlank() && !ui.sending && ui.myUserId != null,
            )
        }
        Text(
            "${ui.draft.length} / ${WireLimits.COMMENT_MAX}",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            color = if (ui.draft.length >= WireLimits.COMMENT_MAX) IronvellumColors.SovereignGold else IronvellumColors.InkMuted,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp),
        )
    }

    confirmDelete?.let { comment ->
        AlertDialog(
            shape = MaterialTheme.shapes.medium,
            containerColor = Color(0xFF0D1110),
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete this comment?") },
            text = { Text("It disappears for everyone. This can't be undone.") },
            confirmButton = {
                IronvellumButton(label = "Delete", onClick = {
                    confirmDelete = null
                    viewModel.delete(comment)
                })
            },
            dismissButton = {
                IronvellumButton(label = "Keep", onClick = { confirmDelete = null }, quiet = true)
            },
        )
    }

    reporting?.let { comment ->
        ReportDialog(
            lifterName = comment.authorName,
            onDismiss = { reporting = null },
            onSend = { reason, note ->
                reporting = null
                viewModel.report(comment, reason, note)
            },
        )
    }
}

@Composable
private fun CommentRow(
    comment: Comment,
    mine: Boolean,
    canDelete: Boolean,
    canReport: Boolean,
    onOpenLifter: () -> Unit,
    onDelete: () -> Unit,
    onReport: () -> Unit,
) {
    InkPanel(Modifier.fillMaxWidth(), accent = if (mine) IronvellumColors.SovereignGold else IronvellumColors.Rune) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                comment.authorName.ifBlank { "LIFTER" },
                style = MaterialTheme.typography.labelLarge,
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.Bold,
                color = if (mine) IronvellumColors.SovereignGold else IronvellumColors.EmeraldBright,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .heightIn(min = 44.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .clickable(role = Role.Button, onClickLabel = "Open lifter") { onOpenLifter() }
                    .wrapContentHeight(),
            )
            Text(
                relativeTime(comment.createdAtMs),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = ChakraPetch,
                color = IronvellumColors.InkMuted,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            if (canReport) RowAction("REPORT", IronvellumColors.InkMuted, onReport)
            if (canDelete) RowAction("DELETE", IronvellumColors.DangerRed, onDelete)
        }
        Text(
            comment.body,
            style = MaterialTheme.typography.bodyMedium,
            color = IronvellumColors.Ink,
        )
    }
}

/** A small text action on a row: 44dp tall, visually a label, never a slab. */
@Composable
internal fun RowAction(label: String, tint: Color, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        fontFamily = ChakraPetch,
        fontWeight = FontWeight.SemiBold,
        color = tint,
        letterSpacing = IronvellumTracking.InlineLabel,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clip(MaterialTheme.shapes.extraSmall)
            .clickable(role = Role.Button) { onClick() }
            .wrapContentHeight()
            .padding(horizontal = 8.dp),
    )
}

/** Title left, BACK right: the header every pushed screen uses (see WorkoutLogScreen). */
@Composable
internal fun PushedHeader(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = IronvellumColors.SystemGreen,
            letterSpacing = IronvellumTracking.ScreenTitle,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            "BACK",
            style = MaterialTheme.typography.labelMedium,
            fontFamily = ChakraPetch,
            color = IronvellumColors.InkMuted,
            letterSpacing = IronvellumTracking.InlineLabel,
            modifier = Modifier
                .heightIn(min = 44.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .clickable(role = Role.Button) { onBack() }
                .wrapContentHeight()
                .padding(horizontal = 12.dp),
        )
    }
    Spacer(Modifier.height(8.dp))
}

/**
 * Reason plus an optional note, for a lifter or one of their comments. Only
 * the Ironvellum owner reads reports, and the lifter is never told who sent one.
 */
@Composable
internal fun ReportDialog(
    lifterName: String,
    onDismiss: () -> Unit,
    onSend: (ReportReason, String) -> Unit,
) {
    var reason by remember { mutableStateOf<ReportReason?>(null) }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        shape = MaterialTheme.shapes.medium,
        containerColor = Color(0xFF0D1110),
        onDismissRequest = onDismiss,
        title = { Text("Report ${lifterName.ifBlank { "this lifter" }}") },
        text = {
            Column {
                Text(
                    "Only the Ironvellum owner sees reports. They aren't told who sent it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                )
                Spacer(Modifier.height(10.dp))
                // Two rows of two: four pills in one row clipped CHEATING on a
                // 360dp dialog.
                ReportReason.entries.chunked(2).forEach { pair ->
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        pair.forEach { option ->
                            IronvellumTabPill(
                                label = option.label(),
                                selected = option == reason,
                                modifier = Modifier.heightIn(min = 44.dp),
                                onClick = { reason = option },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    shape = MaterialTheme.shapes.small,
                    value = note,
                    onValueChange = { note = it.take(WireLimits.REPORT_NOTE_MAX) },
                    label = { Text("Note (optional)") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "${note.length} / ${WireLimits.REPORT_NOTE_MAX}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = ChakraPetch,
                    color = IronvellumColors.InkMuted,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        },
        confirmButton = {
            IronvellumButton(
                label = "Send report",
                onClick = { reason?.let { onSend(it, note.trim()) } },
                enabled = reason != null,
            )
        },
        dismissButton = {
            IronvellumButton(label = "Cancel", onClick = onDismiss, quiet = true)
        },
    )
}

private fun ReportReason.label(): String = when (this) {
    ReportReason.SPAM -> "SPAM"
    ReportReason.ABUSE -> "ABUSE"
    ReportReason.CHEATING -> "CHEATING"
    ReportReason.OTHER -> "OTHER"
}
