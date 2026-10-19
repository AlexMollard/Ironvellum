package com.ironvellum.app.ui.social

import androidx.compose.ui.res.stringResource
import com.ironvellum.app.R
import com.ironvellum.app.data.Units
import com.ironvellum.app.data.Words
import com.ironvellum.app.domain.Loads
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.ui.asString
import com.ironvellum.app.ui.components.PushedHeader
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import com.ironvellum.app.ui.components.IronvellumDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ironvellum.app.data.cloud.AccountRepository
import com.ironvellum.app.data.cloud.AllyExercise
import com.ironvellum.app.data.cloud.AllySet
import com.ironvellum.app.data.cloud.AllyWorkout
import com.ironvellum.app.data.cloud.CloudSync
import com.ironvellum.app.data.cloud.Comment
import com.ironvellum.app.data.cloud.Liker
import com.ironvellum.app.data.cloud.Reaction
import com.ironvellum.app.data.cloud.ReportReason
import com.ironvellum.app.data.cloud.WireLimits
import com.ironvellum.app.domain.Titles
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.components.IronvellumTabPill
import com.ironvellum.app.ui.components.SectionHeader
import com.ironvellum.app.ui.components.formatDate
import com.ironvellum.app.ui.ironvellumAccount
import com.ironvellum.app.ui.ironvellumCloudSync
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.IronvellumTracking
import com.ironvellum.app.ui.theme.inkBorder
import com.ironvellum.app.ui.theme.ironvellumFieldColors
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The workout's lifter as this screen draws them; the workout row itself carries only an id. */
internal data class OwnerIdentity(
    val userId: String,
    val name: String,
    val level: Int?,
    val titleId: String?,
    val crestId: String? = null,
)

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
    /** Null until the workout read lands; a failed read leaves it null so the thread still works. */
    val workout: AllyWorkout? = null,
    val workoutLoading: Boolean = true,
    val workoutError: String? = null,
    val owner: OwnerIdentity? = null,
    /** Every reaction on the workout, newest first; the lifter's own is the row with [myUserId]. */
    val likers: List<Liker>? = null,
    val likersError: String? = null,
    val reactionError: String? = null,
    /** Bumped on each comment the lifter sends, so the list scrolls to it and never on first load. */
    val sentCount: Int = 0,
)

internal class CommentsViewModel(
    private val cloud: CloudSync,
    private val accountRepo: AccountRepository,
    private val sessionId: String,
    private val ownerId: String = "",
) : ViewModel() {

    private val _ui = MutableStateFlow(CommentsUi(myUserId = accountRepo.account.value?.userId))
    val ui: StateFlow<CommentsUi> = _ui.asStateFlow()

    /** Comment ids with a delete in flight: a double tap on the confirm must not fire twice. */
    private val deleting = mutableSetOf<String>()

    /** A reaction call in flight: a second pick before it answers would roll back to a state that is already stale. */
    private var reacting = false

    private fun Throwable.reason(): String = message ?: this::class.simpleName ?: Words.text(UiText.res(R.string.comments_unknown_failure))

    init {
        viewModelScope.launch {
            accountRepo.account.collect { _ui.value = _ui.value.copy(myUserId = it?.userId) }
        }
        load()
        loadWorkout()
        loadReactions()
        // The route's owner id is enough to name the lifter before the workout lands.
        resolveOwner(ownerId)
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

    /** The sets. A failure here never touches the thread: comments load and send on their own. */
    fun loadWorkout(force: Boolean = false) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(workoutLoading = true, workoutError = null)
            cloud.workout(sessionId, force = force)
                .onSuccess { workout ->
                    _ui.value = _ui.value.copy(workout = workout)
                    resolveOwner(workout.userId)
                }
                .onFailure { _ui.value = _ui.value.copy(workoutError = it.reason()) }
            _ui.value = _ui.value.copy(workoutLoading = false)
        }
    }

    fun loadReactions() {
        viewModelScope.launch {
            cloud.likers(sessionId)
                // A pick in flight owns the list: landing an older read on top
                // of the optimistic row made the chip flicker back and forth.
                .onSuccess { if (!reacting) _ui.value = _ui.value.copy(likers = it, likersError = null) }
                .onFailure { _ui.value = _ui.value.copy(likersError = it.reason()) }
        }
    }

    private var resolvedOwner: String? = null

    /**
     * Name, level and worn title for the workout's lifter. Allies are in the
     * cached friends read; the signed-in lifter is not in it, so their own
     * account fills in. Anyone else keeps a plain "Lifter" — the tap still
     * opens them, the profile screen has the real name.
     */
    private fun resolveOwner(userId: String) {
        if (userId.isBlank() || resolvedOwner == userId) return
        resolvedOwner = userId
        val me = accountRepo.account.value
        if (me?.userId == userId) {
            _ui.value = _ui.value.copy(owner = OwnerIdentity(userId, me.displayName, null, null))
            return
        }
        viewModelScope.launch {
            val row = cloud.friends().getOrNull()?.firstOrNull { it.userId == userId }
            _ui.value = _ui.value.copy(
                owner = OwnerIdentity(userId, row?.displayName ?: Words.lifter.c, row?.level, row?.currentTitleId, row?.crestId),
            )
        }
    }

    /**
     * Optimistic: the chip moves now and the call reconciles. [reaction] null
     * removes the lifter's own. A refusal restores the previous list, so a
     * rejected write never leaves a phantom reaction behind.
     */
    fun react(reaction: Reaction?) {
        val current = _ui.value
        val me = current.myUserId ?: return
        if (reacting) return
        val before = current.likers
        if (before?.firstOrNull { it.userId == me }?.reaction == reaction) return
        val name = accountRepo.account.value?.displayName ?: Words.text(UiText.res(R.string.comments_you))
        reacting = true
        val others = before.orEmpty().filter { it.userId != me }
        _ui.value = current.copy(
            reactionError = null,
            likers = if (reaction == null) others
            else listOf(Liker(me, name, System.currentTimeMillis(), reaction)) + others,
        )
        viewModelScope.launch {
            cloud.react(sessionId, reaction)
                .onSuccess { reacting = false; loadReactions() }
                .onFailure {
                    reacting = false
                    _ui.value = _ui.value.copy(likers = before, reactionError = it.reason())
                }
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
                        sentCount = _ui.value.sentCount + 1,
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
                .onSuccess { _ui.value = _ui.value.copy(notice = Words.text(UiText.res(R.string.comments_report_sent))) }
                .onFailure { _ui.value = _ui.value.copy(actionError = it.reason()) }
        }
    }
}

/**
 * One workout as an ally sees it: who, what they lifted, reactions, and the
 * comment thread. [ownerId] is the workout's lifter: they may delete any
 * comment on it, everyone else only their own. The route's id can be empty
 * (deep links), so the fetched workout's own lifter wins when it arrives.
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
            initializer { CommentsViewModel(ironvellumCloudSync(), ironvellumAccount(), sessionId, ownerId) }
        },
    ),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf<Comment?>(null) }
    var reporting by remember { mutableStateOf<Comment?>(null) }
    val listState = rememberLazyListState()
    val lifterId = ui.workout?.userId ?: ownerId
    val isMe = ui.myUserId != null && lifterId == ui.myUserId
    // Land on the sent comment: the thread reads oldest first, and a sent
    // comment appearing below the fold looked like it had not been posted.
    // Keyed on sends, not on the list, so opening the screen stays on the workout.
    LaunchedEffect(ui.sentCount) {
        if (ui.sentCount > 0) listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
    }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        PushedHeader(Words.trial.u, onBack)

        val err = ui.error
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            item(key = "workout") {
                WorkoutHeaderPanel(
                    ui = ui,
                    headline = headline,
                    isMe = isMe,
                    onOpenLifter = {
                        val owner = ui.owner
                        onOpenLifter(owner?.userId ?: lifterId, owner?.name ?: Words.lifter.c)
                    },
                    onRetry = { viewModel.loadWorkout(force = true) },
                )
            }
            ui.workout?.let { workout ->
                item(key = "work-header") { SectionHeader(stringResource(R.string.comments_work_header)) }
                if (workout.exercises.isEmpty()) {
                    item(key = "work-empty") {
                        Text(
                            UiText.themed(R.string.comments_work_empty, R.string.comments_work_empty__t).asString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = IronvellumColors.InkMuted,
                        )
                    }
                }
                items(workout.exercises, key = { "ex-${it.name}-${workout.exercises.indexOf(it)}" }) { exercise ->
                    ExercisePanel(exercise)
                }
            }
            item(key = "reactions-header") { SectionHeader(Words.tribute.upl) }
            item(key = "reactions") {
                ReactionsPanel(
                    ui = ui,
                    onPick = viewModel::react,
                    onRetry = viewModel::loadReactions,
                )
            }
            item(key = "comments-header") { SectionHeader(Words.remark.upl) }
            when {
                ui.loading && ui.comments.isEmpty() -> item(key = "comments-loading") {
                    InkPanel(Modifier.fillMaxWidth()) {
                        Text(
                            UiText.themed(R.string.comments_thread_loading, R.string.comments_thread_loading__t).asString(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = ChakraPetch,
                            color = IronvellumColors.InkMuted,
                        )
                    }
                }
                ui.comments.isEmpty() && err != null -> item(key = "comments-error") {
                    InkPanel(Modifier.fillMaxWidth()) {
                        Text(
                            err,
                            style = MaterialTheme.typography.bodySmall,
                            color = IronvellumColors.InkMuted,
                        )
                        Spacer(Modifier.height(6.dp))
                        SocialRefreshLink(onClick = { viewModel.load(force = true) }, label = stringResource(R.string.comments_try_again))
                    }
                }
                ui.comments.isEmpty() -> item(key = "comments-empty") {
                    InkPanel(Modifier.fillMaxWidth()) {
                        Text(
                            UiText.themed(R.string.comments_empty, R.string.comments_empty__t).asString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = IronvellumColors.InkMuted,
                        )
                    }
                }
                else -> items(ui.comments, key = { it.id }) { comment ->
                    val mine = comment.userId == ui.myUserId
                    CommentRow(
                        comment = comment,
                        mine = mine,
                        canDelete = ui.myUserId != null && (mine || lifterId == ui.myUserId),
                        canReport = ui.myUserId != null && !mine,
                        onOpenLifter = { onOpenLifter(comment.userId, comment.authorName) },
                        onDelete = { confirmDelete = comment },
                        onReport = { reporting = comment },
                    )
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
                colors = ironvellumFieldColors(),
                value = ui.draft,
                onValueChange = viewModel::onDraft,
                label = { Text(Words.remark.c) },
                maxLines = 4,
                modifier = Modifier.weight(1f),
            )
            IronvellumButton(
                label = stringResource(if (ui.sending) R.string.comments_sending_button else R.string.comments_send_button),
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
        IronvellumDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(UiText.themed(R.string.comments_delete_title, R.string.comments_delete_title__t).asString()) },
            text = { Text(stringResource(R.string.comments_delete_body)) },
            confirmButton = {
                IronvellumButton(label = stringResource(R.string.comments_delete_button), onClick = {
                    confirmDelete = null
                    viewModel.delete(comment)
                }, danger = true)
            },
            dismissButton = {
                IronvellumButton(label = stringResource(R.string.comments_keep_button), onClick = { confirmDelete = null }, quiet = true)
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

/** Lifter, headline, date, stat strip and note in one panel; the headline alone while the workout loads or fails. */
@Composable
private fun WorkoutHeaderPanel(
    ui: CommentsUi,
    headline: String,
    isMe: Boolean,
    onOpenLifter: () -> Unit,
    onRetry: () -> Unit,
) {
    val workout = ui.workout
    val owner = ui.owner
    InkPanel(Modifier.fillMaxWidth()) {
        if (owner != null) {
            IdentityRow(
                displayName = owner.name,
                userId = owner.userId,
                wornTitle = owner.titleId?.let { Titles.byId(it)?.name },
                level = owner.level,
                size = IdentitySize.Standard,
                isMe = isMe,
                frameId = owner.crestId,
                onClick = onOpenLifter,
            )
            Spacer(Modifier.height(10.dp))
        }
        Text(
            workout?.headline?.ifBlank { null } ?: headline.ifBlank { Words.trial.c },
            style = MaterialTheme.typography.headlineSmall,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = IronvellumColors.Ink,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        val whenMs = workout?.let { it.completedAtMs ?: it.startedAtMs }
        if (whenMs != null) {
            Text(
                formatDate(whenMs),
                style = MaterialTheme.typography.labelMedium,
                color = IronvellumColors.InkMuted,
            )
        }
        when {
            workout != null -> {
                val done = workout.exercises.flatMap { it.sets }.filter { it.done }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    WorkoutStat("${done.size}", stringResource(R.string.comments_stat_sets))
                    WorkoutStat("${done.filter { it.countsReps() }.sumOf { it.reps }}", stringResource(R.string.comments_stat_reps))
                    WorkoutStat("+${workout.xpAwarded}", stringResource(R.string.comments_stat_xp), IronvellumColors.Emerald)
                    WorkoutStat("${workout.strengthScore}", stringResource(R.string.comments_stat_strength), IronvellumColors.SovereignGold)
                }
                if (workout.note.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        workout.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = IronvellumColors.InkMuted,
                    )
                }
            }
            ui.workoutError != null -> {
                Spacer(Modifier.height(10.dp))
                SocialErrorBanner(ui.workoutError)
                Spacer(Modifier.height(4.dp))
                SocialRefreshLink(onClick = onRetry, label = stringResource(R.string.comments_try_again))
            }
            else -> {
                Spacer(Modifier.height(8.dp))
                Text(
                    UiText.themed(R.string.comments_workout_loading, R.string.comments_workout_loading__t).asString(),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = ChakraPetch,
                    color = IronvellumColors.InkMuted,
                )
            }
        }
    }
}

@Composable
private fun WorkoutStat(value: String, label: String, accent: Color = IronvellumColors.Ink) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = accent,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            color = IronvellumColors.InkMuted,
            letterSpacing = IronvellumTracking.InlineLabel,
        )
    }
}

/** True for a plain reps set: holds, distance and climbs measure something else, and summing them as reps is what printed "140 reps" for a plank. */
private fun AllySet.countsReps(): Boolean =
    (durationSec ?: 0) <= 0 && (distanceM ?: 0.0) <= 0.0 && grade.isNullOrBlank()

/**
 * The figure for one set. The catalogue is not on this device for someone
 * else's workout, so the shape of the row decides: distance, then grade,
 * then hold seconds, else reps × load. Added load on a bodyweight exercise
 * reads as its plain kg here — the lifter's bodyweight is private.
 */
internal fun AllySet.figure(): String {
    val load = weightKg?.takeIf { it > 0.0 }?.let { Units.withUnit(it) }
    val metres = distanceM?.takeIf { it > 0.0 }
    val seconds = durationSec?.takeIf { it > 0 }
    return when {
        metres != null -> (if (metres >= 1000) String.format(Locale.US, "%.1f km", metres / 1000) else "${metres.toInt()} m") +
            (seconds?.let { " · ${it}s" }.orEmpty())
        !grade.isNullOrBlank() -> "$reps × $grade"
        seconds != null -> "${seconds}s" + (load?.let { " × $it" }.orEmpty())
        else -> "$reps × ${load ?: Loads.bodyweight()}"
    }
}

@Composable
private fun ExercisePanel(exercise: AllyExercise) {
    val sets = exercise.sets.sortedBy { it.setIndex }
    val done = sets.filter { it.done }
    val reps = done.filter { it.countsReps() }.sumOf { it.reps }
    InkPanel(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                exercise.name.uppercase(),
                style = MaterialTheme.typography.titleSmall,
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.SemiBold,
                color = IronvellumColors.SystemGreen,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(
                UiText.plural(R.plurals.common_sets_count, done.size).asString() +
                    if (reps > 0) " · " + UiText.plural(R.plurals.common_reps_count, reps).asString() else "",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = ChakraPetch,
                color = IronvellumColors.InkMuted,
                letterSpacing = IronvellumTracking.InlineLabel,
            )
        }
        Spacer(Modifier.height(10.dp))
        // A wrapping strip, not a bar per set: same shape as the local detail screen.
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            sets.forEach { SetChip(it) }
        }
    }
}

@Composable
private fun SetChip(set: AllySet) {
    Column(
        Modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .background(if (set.done) IronvellumColors.VaultHigh else Color.Transparent)
            .inkBorder(if (set.done) IronvellumColors.Emerald.copy(alpha = 0.35f) else IronvellumColors.Rune, MaterialTheme.shapes.extraSmall, 1.dp)
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Text(
            set.figure(),
            style = MaterialTheme.typography.labelLarge,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = if (set.done) IronvellumColors.Ink else IronvellumColors.InkMuted,
            maxLines = 1,
            softWrap = false,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                // Completed sets get the emerald diamond; skipped ones stay bare.
                if (set.done) "\u25C6 ${set.setIndex + 1}" else "\u25C7 ${set.setIndex + 1}",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = ChakraPetch,
                color = if (set.done) IronvellumColors.Emerald else IronvellumColors.InkMuted,
                letterSpacing = IronvellumTracking.InlineLabel,
                maxLines = 1,
                softWrap = false,
            )
            if (set.modifiers.isNotBlank()) {
                Text(
                    " · ${set.modifiers.uppercase()}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = ChakraPetch,
                    color = IronvellumColors.SovereignGold,
                    letterSpacing = IronvellumTracking.InlineLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The lifter's own reaction control, then everyone who reacted. Anyone who can see the workout can read the list. */
@Composable
private fun ReactionsPanel(ui: CommentsUi, onPick: (Reaction?) -> Unit, onRetry: () -> Unit) {
    val likers = ui.likers
    val mine = likers?.firstOrNull { it.userId == ui.myUserId }?.reaction
    val counts = likers.orEmpty().mapNotNull { it.reaction }.groupingBy { it }.eachCount()
    InkPanel(Modifier.fillMaxWidth()) {
        if (ui.myUserId != null) {
            ReactionPicker(current = mine, counts = counts, onPick = onPick)
        }
        ui.reactionError?.let {
            Spacer(Modifier.height(6.dp))
            Text(
                UiText.themed(R.string.comments_reaction_error, R.string.comments_reaction_error__t, it).asString(),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = ChakraPetch,
                color = IronvellumColors.DangerRed,
            )
        }
        Spacer(Modifier.height(10.dp))
        when {
            likers == null && ui.likersError != null -> {
                SocialErrorBanner(ui.likersError)
                Spacer(Modifier.height(4.dp))
                SocialRefreshLink(onClick = onRetry, label = stringResource(R.string.comments_try_again))
            }
            likers == null -> Text(
                UiText.themed(R.string.comments_reactions_loading, R.string.comments_reactions_loading__t).asString(),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = ChakraPetch,
                color = IronvellumColors.InkMuted,
            )
            likers.isEmpty() -> Text(
                UiText.themed(R.string.comments_reactions_empty, R.string.comments_reactions_empty__t).asString(),
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
            )
            else -> {
                Text(
                    UiText.themed(R.string.comments_reactions_who, R.string.comments_reactions_who__t).asString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = ChakraPetch,
                    color = IronvellumColors.EmeraldBright,
                    letterSpacing = IronvellumTracking.InlineLabel,
                )
                likers.forEach { liker ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        liker.reaction?.let { reaction ->
                            Icon(
                                reaction.glyph(),
                                contentDescription = reaction.displayName(),
                                tint = IronvellumColors.SovereignGold,
                                modifier = Modifier.padding(end = 8.dp).size(16.dp),
                            )
                        }
                        Text(
                            liker.displayName,
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = ChakraPetch,
                            color = IronvellumColors.Ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            relativeTime(liker.likedAtMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = IronvellumColors.InkMuted,
                        )
                    }
                }
            }
        }
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
    InkPanel(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                comment.authorName.ifBlank { Words.lifter.u },
                style = MaterialTheme.typography.labelLarge,
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.Bold,
                color = if (mine) IronvellumColors.SovereignGold else IronvellumColors.EmeraldBright,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .heightIn(min = 48.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .clickable(role = Role.Button, onClickLabel = UiText.themed(R.string.comments_open_profile_label, R.string.comments_open_profile_label__t).asString()) { onOpenLifter() }
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
            if (canReport) RowAction(stringResource(R.string.comments_report_button), IronvellumColors.SystemGreen, onClick = onReport)
            if (canDelete) RowAction(stringResource(R.string.comments_delete_button), IronvellumColors.DangerRed, onClick = onDelete)
        }
        Text(
            comment.body,
            style = MaterialTheme.typography.bodyMedium,
            color = IronvellumColors.Ink,
        )
    }
}

/**
 * A text link on a row: sentence case, `labelLarge` at 0.5sp, a 48dp target, never a slab.
 * [tint] is SystemGreen, or DangerRed for a destructive action.
 * [contentDescription] is what a screen reader says in place of the bare label,
 * for a row that repeats it ("Remove" on every member): name what it acts on.
 */
@Composable
internal fun RowAction(
    label: String,
    tint: Color,
    contentDescription: String? = null,
    onClick: () -> Unit,
) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        fontFamily = ChakraPetch,
        fontWeight = FontWeight.SemiBold,
        color = tint,
        letterSpacing = 0.5.sp,
        textAlign = TextAlign.Center,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .widthIn(min = 48.dp)
            .clip(MaterialTheme.shapes.extraSmall)
            .clickable(role = Role.Button) { onClick() }
            .wrapContentHeight()
            .padding(horizontal = 8.dp)
            .then(
                if (contentDescription == null) Modifier
                else Modifier.semantics { this.contentDescription = contentDescription },
            ),
    )
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
    IronvellumDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (lifterName.isBlank()) {
                    UiText.themed(R.string.comments_report_title_unnamed, R.string.comments_report_title_unnamed__t).asString()
                } else {
                    stringResource(R.string.comments_report_title, lifterName)
                },
            )
        },
        text = {
            Column {
                Text(
                    stringResource(R.string.comments_report_note),
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
                                modifier = Modifier.heightIn(min = 48.dp),
                                onClick = { reason = option },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    shape = MaterialTheme.shapes.small,
                    colors = ironvellumFieldColors(),
                    value = note,
                    onValueChange = { note = it.take(WireLimits.REPORT_NOTE_MAX) },
                    label = { Text(stringResource(R.string.comments_report_note_label)) },
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
                label = stringResource(R.string.comments_report_send_button),
                onClick = { reason?.let { onSend(it, note.trim()) } },
                enabled = reason != null,
            )
        },
        dismissButton = {
            IronvellumButton(label = stringResource(R.string.common_cancel), onClick = onDismiss, quiet = true)
        },
    )
}

@Composable
private fun ReportReason.label(): String = stringResource(
    when (this) {
        ReportReason.SPAM -> R.string.comments_report_reason_spam
        ReportReason.ABUSE -> R.string.comments_report_reason_abuse
        ReportReason.CHEATING -> R.string.comments_report_reason_cheating
        ReportReason.OTHER -> R.string.comments_report_reason_other
    },
)
