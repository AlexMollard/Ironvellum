package com.ironvellum.app.ui.train

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ironvellum.app.data.Repository
import com.ironvellum.app.data.cloud.CloudSyncWorker
import com.ironvellum.app.domain.Energy
import com.ironvellum.app.domain.EnergyConfidence
import com.ironvellum.app.domain.EnergyEstimate
import com.ironvellum.app.domain.Exercise
import com.ironvellum.app.domain.ExerciseMetric
import com.ironvellum.app.domain.MovementDifficulty
import com.ironvellum.app.domain.SealedEdit
import com.ironvellum.app.domain.SessionAudience
import com.ironvellum.app.domain.SessionSet
import com.ironvellum.app.domain.TrialDraft
import com.ironvellum.app.domain.WorkoutSession
import com.ironvellum.app.domain.WorkoutShare
import com.ironvellum.app.domain.fmt
import com.ironvellum.app.ui.components.AchievementOverlay
import com.ironvellum.app.ui.components.CelebrationPage
import com.ironvellum.app.ui.components.InfoNotice
import com.ironvellum.app.ui.components.InkChip
import com.ironvellum.app.ui.components.InkDivider
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.components.InkSegmented
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.components.IronvellumDialog
import com.ironvellum.app.ui.components.ListRow
import com.ironvellum.app.ui.components.ListRowHeight
import com.ironvellum.app.ui.components.PushedHeader
import com.ironvellum.app.ui.components.SectionHeader
import com.ironvellum.app.ui.components.ShareCardDialog
import com.ironvellum.app.ui.components.deedPages
import com.ironvellum.app.ui.components.formatDate
import com.ironvellum.app.ui.components.formatLoadKg
import com.ironvellum.app.ui.components.metricTotals
import com.ironvellum.app.ui.components.plural
import com.ironvellum.app.ui.components.setFigure
import com.ironvellum.app.ui.ironvellumRepository
import com.ironvellum.app.ui.theme.IronvellumColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WorkoutDetailUi(
    val session: WorkoutSession? = null,
    val sets: List<SessionSet> = emptyList(),
    /** True once the history stream has emitted at least once. */
    val loaded: Boolean = false,
    /** Estimated burn for the session; null when nothing can be computed. */
    val energy: EnergyEstimate? = null,
    /** Catalogue by id, for unit-correct rendering and the share card. */
    val exercises: Map<Long, Exercise> = emptyMap(),
)

class WorkoutDetailViewModel(
    private val repo: Repository,
    private val sessionId: Long,
    private val appContext: android.content.Context,
) : ViewModel() {
    // The log source of truth is the history stream: a session absent from it
    // is genuinely not viewable, so the screen renders "not found" honestly.
    val ui: StateFlow<WorkoutDetailUi> = combine(
        repo.observeHistory(),
        repo.observeStats().map { it.firstOrNull()?.weightKg },
        repo.observeExercises(),
    ) { history, bodyKg, exercises ->
        val hit = history.firstOrNull { it.first.id == sessionId }
        val session = hit?.first
        val sets = hit?.second.orEmpty()
        // Same pure estimate the stats screen uses; minutes come from the wall
        // clock when the session was completed.
        val minutes = session?.completedAtMs
            ?.let { ((it - session.startedAtMs) / 60_000L).toInt().coerceAtLeast(0) }
        val byId = exercises.associateBy { it.id }
        WorkoutDetailUi(
            session = session,
            sets = sets,
            loaded = true,
            energy = session?.let { Energy.sessionKcal(sets, byId, bodyKg, minutes) },
            exercises = byId,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutDetailUi())

    /**
     * Written locally, then pushed at once: a lifter who hides a workout
     * expects it gone from allies' feeds now, not after the daily sync. The
     * history stream re-emits with the new value, so the picker settles from
     * the database rather than from a local guess.
     */
    fun setAudience(audience: SessionAudience) {
        viewModelScope.launch {
            repo.setSessionAudience(sessionId, audience)
            CloudSyncWorker.pushNow(appContext)
        }
    }

    // ------------------------------------------------------------ amending
    // The draft lives here, not in the composable, so a rotation mid-edit
    // keeps it. Nothing reaches the database until confirmSave().

    private val _draft = MutableStateFlow<TrialDraft?>(null)
    val draft: StateFlow<TrialDraft?> = _draft.asStateFlow()

    /** The settled XP change awaiting the lifter's yes; null when not asking. */
    private val _pendingSave = MutableStateFlow<SealedEdit.Settlement?>(null)
    val pendingSave: StateFlow<SealedEdit.Settlement?> = _pendingSave.asStateFlow()

    private val _amendError = MutableStateFlow<String?>(null)
    val amendError: StateFlow<String?> = _amendError.asStateFlow()

    /** Deeds an in-window amendment newly earned, as the page that announces them once. */
    private val _earnedDeeds = MutableStateFlow<List<CelebrationPage>>(emptyList())
    internal val earnedDeeds: StateFlow<List<CelebrationPage>> = _earnedDeeds.asStateFlow()

    fun dismissEarnedDeeds() {
        _earnedDeeds.value = emptyList()
    }

    fun startAmend() {
        val sets = ui.value.sets
        if (ui.value.session?.completedAtMs == null || sets.isEmpty()) return
        val opened = TrialDraft.of(sets)
        if (opened.hasRepeatedMovement) {
            _amendError.value = "This trial lists one movement twice with different modifiers, so it cannot be amended."
        } else {
            _draft.value = opened
        }
    }

    fun changeDraft(change: (TrialDraft) -> TrialDraft) {
        _draft.update { it?.let(change) }
    }

    fun cancelAmend() {
        _draft.value = null
        _pendingSave.value = null
    }

    fun requestSave() {
        val draft = _draft.value ?: return
        viewModelScope.launch {
            runCatching { repo.previewSealedEdit(sessionId, draft) }
                .onSuccess { _pendingSave.value = it }
                .onFailure { _amendError.value = it.message ?: "The trial could not be amended" }
        }
    }

    fun dismissSave() {
        _pendingSave.value = null
    }

    /** Written locally, then pushed at once, like the audience. */
    fun confirmSave() {
        val draft = _draft.value ?: return
        _pendingSave.value = null
        viewModelScope.launch {
            runCatching { repo.editSealedTrial(sessionId, draft) }
                .onSuccess { result ->
                    _draft.value = null
                    _earnedDeeds.value = deedPages(result.newTitles, repo.observeSex().first())
                    CloudSyncWorker.pushNow(appContext)
                }
                .onFailure { _amendError.value = it.message ?: "The trial could not be amended" }
        }
    }

    fun dismissAmendError() {
        _amendError.value = null
    }

    /**
     * Gives the trial's XP and strength back to the ledger and drops its sets, then leaves the
     * screen: the history stream would otherwise re-emit and show "not found" first.
     */
    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            repo.deleteWorkout(sessionId)
            onDone()
        }
    }
}

/**
 * The full record of one trial: nothing hidden. The shared note is feed material; the private
 * note is device-only and is labelled as such. Deleting lives here, behind a confirm that names
 * the XP and strength it gives back.
 */
@Composable
fun WorkoutDetailScreen(
    sessionId: Long,
    onBack: () -> Unit,
    viewModel: WorkoutDetailViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                WorkoutDetailViewModel(
                    ironvellumRepository(),
                    sessionId,
                    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!.applicationContext,
                )
            }
        },
    ),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val pendingSave by viewModel.pendingSave.collectAsStateWithLifecycle()
    val amendError by viewModel.amendError.collectAsStateWithLifecycle()
    val earnedDeeds by viewModel.earnedDeeds.collectAsStateWithLifecycle()
    var shareText by remember { mutableStateOf<String?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var pickAudience by remember { mutableStateOf(false) }
    // Back while amending asks before dropping edits; an untouched draft just closes.
    BackHandler(enabled = draft != null) {
        if (draft != TrialDraft.of(ui.sets)) confirmDiscard = true else viewModel.cancelAmend()
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        PushedHeader(
            ui.session?.let { it.title.ifBlank { it.label } } ?: "Trial",
            onBack = { onBack() },
            actions = {
                ui.session?.takeIf { draft == null }?.let { session ->
                    if (session.completedAtMs != null && ui.sets.isNotEmpty()) {
                        InkChip("Amend", icon = Icons.Outlined.Edit, description = "Amend this trial", onClick = viewModel::startAmend)
                    }
                    InkChip(
                        "Share",
                        icon = Icons.Outlined.IosShare,
                        description = "Share this trial",
                        onClick = { shareText = WorkoutShare.format(session, ui.sets, ui.exercises) },
                    )
                }
            },
        )

        val session = ui.session
        when {
            !ui.loaded -> {
                // Brief startup window before the history stream emits; render
                // structure, not a lie about missing data.
            }
            session == null -> Column(
                Modifier.fillMaxWidth().padding(vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Trial not found",
                    style = MaterialTheme.typography.titleMedium,
                    color = IronvellumColors.Ink,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "This trial may have been deleted.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = IronvellumColors.InkMuted,
                )
            }
            draft != null -> draft?.let { current ->
                TrialAmendEditor(
                    draft = current,
                    exercises = ui.exercises,
                    onChange = viewModel::changeDraft,
                    onSave = viewModel::requestSave,
                    onCancel = viewModel::cancelAmend,
                )
            }
            else -> {
                // Tapping the kcal readout reveals the formula behind it — a
                // number without its basis asks for blind trust.
                var showBasis by remember { mutableStateOf(false) }
                DetailHeader(session, ui.energy, showBasis) { showBasis = !showBasis }
                WorkoutSets(sets = ui.sets, exercises = ui.exercises)
                NotesCard(session)
                Spacer(Modifier.height(12.dp))
                InkDivider()
                ListRow(
                    label = "Who sees this",
                    value = audienceLabel(session.audience),
                    icon = Icons.Outlined.Visibility,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                    onClick = { pickAudience = true },
                )
                if (session.completedAtMs != null) {
                    Spacer(Modifier.height(28.dp))
                    InkDivider()
                    DeleteRow { confirmDelete = true }
                }
            }
        }
        Spacer(Modifier.height(96.dp))
    }

    shareText?.let { text ->
        ShareCardDialog(text = text, onDismiss = { shareText = null })
    }
    pendingSave?.let { settlement ->
        AmendConfirmDialog(settlement, onConfirm = viewModel::confirmSave, onDismiss = viewModel::dismissSave)
    }
    if (pickAudience) {
        ui.session?.let { session ->
            AudienceSheet(session.audience, viewModel::setAudience) { pickAudience = false }
        }
    }
    if (confirmDelete) {
        ui.session?.let { session ->
            IronvellumDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text("Delete this trial?") },
                text = {
                    Text(
                        deleteReturnLine(session),
                        style = MaterialTheme.typography.bodyMedium,
                        color = IronvellumColors.InkMuted,
                    )
                },
                confirmButton = {
                    IronvellumButton(
                        label = "Delete",
                        onClick = {
                            confirmDelete = false
                            viewModel.delete(onDone = onBack)
                        },
                        danger = true,
                    )
                },
                dismissButton = {
                    IronvellumButton(label = "Keep", onClick = { confirmDelete = false }, quiet = true)
                },
            )
        }
    }
    if (confirmDiscard) {
        IronvellumDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard your changes?") },
            text = { Text("Unsaved edits to this trial will be lost.") },
            confirmButton = {
                IronvellumButton(
                    label = "Discard",
                    onClick = {
                        confirmDiscard = false
                        viewModel.cancelAmend()
                    },
                    danger = true,
                )
            },
            dismissButton = {
                IronvellumButton(label = "Keep editing", onClick = { confirmDiscard = false }, quiet = true)
            },
        )
    }
    amendError?.let { message ->
        InfoNotice("Not amended", message, onDismiss = viewModel::dismissAmendError)
    }
    AchievementOverlay(pages = earnedDeeds, onDone = viewModel::dismissEarnedDeeds)
}

/** What deleting pays back, said plainly: the ledger loses exactly these two figures. */
private fun deleteReturnLine(session: WorkoutSession): String = when {
    session.xpAwarded > 0 && session.strengthScore > 0 ->
        "+${session.xpAwarded} XP and ${"%,d".fmt(session.strengthScore)} strength will be taken back."
    session.xpAwarded > 0 -> "+${session.xpAwarded} XP will be taken back."
    session.strengthScore > 0 -> "${"%,d".fmt(session.strengthScore)} strength will be taken back."
    else -> "This trial will be removed from the Chronicle."
}

private fun audienceLabel(audience: SessionAudience): String = when (audience) {
    SessionAudience.PROFILE -> "Folio"
    SessionAudience.FRIENDS -> "Allies"
    SessionAudience.PRIVATE -> "Only me"
}

/**
 * Who may see this one trial in the cloud. The stricter of this and the profile visibility wins,
 * so Folio is "whatever my profile says". The line below names the setting it follows.
 */
@Composable
private fun AudienceSheet(audience: SessionAudience, onPick: (SessionAudience) -> Unit, onDone: () -> Unit) {
    IronvellumDialog(
        onDismissRequest = onDone,
        title = { Text("Who sees this trial") },
        text = {
            Column {
                InkSegmented(
                    options = listOf(
                        SessionAudience.PROFILE to "Folio",
                        SessionAudience.FRIENDS to "Allies",
                        SessionAudience.PRIVATE to "Only me",
                    ),
                    selected = audience,
                    onPick = onPick,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    when (audience) {
                        SessionAudience.PROFILE -> "Follows your folio's visibility on the ALLIES tab."
                        SessionAudience.FRIENDS -> "Only allies see it, even on a public folio."
                        SessionAudience.PRIVATE -> "Only you see it."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                )
            }
        },
        confirmButton = { IronvellumButton(label = "Done", onClick = onDone) },
    )
}

@Composable
private fun DeleteRow(onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = ListRowHeight)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Outlined.Delete, contentDescription = null, tint = IronvellumColors.DangerRed, modifier = Modifier.size(22.dp))
        Text("Delete trial", style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.DangerRed)
    }
}

/** The shared and private notes in one neutral card, each captioned with who can read it. */
@Composable
private fun NotesCard(session: WorkoutSession) {
    if (session.note.isBlank() && session.privateNote.isBlank()) return
    Spacer(Modifier.height(16.dp))
    InkPanel(Modifier.fillMaxWidth()) {
        if (session.note.isNotBlank()) {
            Text(session.note, style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.Ink)
            Spacer(Modifier.height(6.dp))
            NoteCaption(Icons.Outlined.IosShare, "Shared note · allies see it")
        }
        if (session.note.isNotBlank() && session.privateNote.isNotBlank()) Spacer(Modifier.height(16.dp))
        if (session.privateNote.isNotBlank()) {
            Text(session.privateNote, style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.Ink)
            Spacer(Modifier.height(6.dp))
            NoteCaption(Icons.Filled.Lock, "Private note · only you see it")
        }
    }
}

@Composable
private fun NoteCaption(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, contentDescription = null, tint = IronvellumColors.InkMuted, modifier = Modifier.size(14.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted)
    }
}

@Composable
private fun DetailHeader(
    session: WorkoutSession,
    energy: EnergyEstimate?,
    showBasis: Boolean,
    onToggleBasis: () -> Unit,
) {
    Text(
        listOfNotNull(formatDate(session.completedAtMs ?: session.startedAtMs, "EEE d MMM"), trialDuration(session))
            .joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
        color = IronvellumColors.InkMuted,
        modifier = Modifier.padding(start = 2.dp, top = 2.dp),
    )
    session.editedAtMs?.let { amended ->
        Text(
            "Amended ${formatDate(amended)}",
            style = MaterialTheme.typography.bodySmall,
            color = IronvellumColors.InkMuted,
            modifier = Modifier.padding(start = 2.dp),
        )
    }
    Row(
        Modifier.fillMaxWidth().padding(start = 2.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // XP is the one earned figure, so it alone is gold; strength is a plain figure.
        Figure("+${session.xpAwarded}", "XP", IronvellumColors.SovereignGold)
        if (session.strengthScore > 0) Figure("%,d".fmt(session.strengthScore), "STR", IronvellumColors.Ink)
        Spacer(Modifier.weight(1f))
        if (energy != null) {
            Text(
                when (energy.confidence) {
                    EnergyConfidence.MEASURED -> "${energy.kcal} kcal measured"
                    EnergyConfidence.ESTIMATED -> "about ${energy.kcal} kcal"
                    EnergyConfidence.COARSE -> "roughly ${energy.kcal} kcal"
                },
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier
                    .clickable(role = Role.Button, onClickLabel = "Show how this was worked out", onClick = onToggleBasis)
                    .heightIn(min = 48.dp)
                    .wrapContentHeight(Alignment.CenterVertically),
            )
        }
    }
    if (showBasis && energy != null) {
        Text(
            energyBasisCopy(energy),
            style = MaterialTheme.typography.bodySmall,
            color = IronvellumColors.InkMuted,
            modifier = Modifier.padding(start = 2.dp),
        )
        energy.missing.forEach { name ->
            Spacer(Modifier.height(4.dp))
            Text(
                "Add a $name reading for a sharper estimate.",
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
                modifier = Modifier.padding(start = 2.dp),
            )
        }
    }
}

/** A headline figure: the number large, its unit small beside it. */
@Composable
private fun Figure(value: String, unit: String, color: Color) {
    Text(
        buildAnnotatedString {
            append(value)
            withStyle(SpanStyle(fontSize = 12.sp, letterSpacing = 0.5.sp)) { append(" $unit") }
        },
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = color,
    )
}

private fun energyBasisCopy(energy: EnergyEstimate): String = when (energy.confidence) {
    EnergyConfidence.MEASURED -> "Measured by Health Connect — not an estimate."
    EnergyConfidence.ESTIMATED -> "Estimated: ${energy.basis}. MET values come from the Compendium of Physical Activities."
    // Lifting logs reps, not minutes, so working time is inferred from set count.
    EnergyConfidence.COARSE -> "Rough estimate: ${energy.basis}. Sets carry no minutes, so working time is inferred from set count."
}

@Composable
private fun WorkoutSets(sets: List<SessionSet>, exercises: Map<Long, Exercise>) {
    SectionHeader("The work")
    // Per-exercise groups in position order, sets in set order within each.
    val groups = sets
        .groupBy { it.exercisePosition }
        .toSortedMap()
        .map { (_, groupSets) -> groupSets.sortedBy { it.setIndex } }
    // Supersets are numbered in the order the trial reached them.
    val supersetNumber = groups.mapNotNull { it.firstOrNull()?.supersetGroup }.distinct()
        .withIndex().associate { (i, group) -> group to i + 1 }
    // The first movement opens; the rest fold to one line each until tapped.
    var open by remember { mutableStateOf(setOf(0)) }
    groups.forEachIndexed { groupIndex, groupSets ->
        val first = groupSets.first()
        val hold = MovementDifficulty.isHoldSet(exercises[first.exerciseId]?.metric, first.exerciseName, first.modifiers)
        // A legacy hold catalogued as REPS still holds seconds, so the hold
        // detection overrides the catalogue before anything counts reps.
        val metric = if (hold) ExerciseMetric.HOLD else exercises[first.exerciseId]?.metric ?: ExerciseMetric.REPS
        val weighted = exercises[first.exerciseId]?.isWeighted == true
        val working = groupSets.filter { !it.warmup }.ifEmpty { groupSets }
        val doneWorking = working.filter { it.done }
        // Seconds are seconds, attempts are attempts: never summed as reps.
        val totals = metricTotals(doneWorking) { metric }
        val summary = when {
            doneWorking.isEmpty() -> null
            metric == ExerciseMetric.REPS ->
                "best " + setText(doneWorking.maxWith(compareBy({ it.weightKg ?: 0.0 }, { it.reps })), metric, hold, weighted)
            metric == ExerciseMetric.HOLD ->
                "best " + setText(doneWorking.maxBy { it.durationSec ?: it.reps }, metric, hold, weighted)
            metric == ExerciseMetric.ATTEMPTS_GRADE ->
                // Grades are free text with no ordering, so show the last one entered.
                "${totals.attempts} ${plural(totals.attempts, "attempt", "attempts")}" +
                    (groupSets.lastOrNull { !it.grade.isNullOrBlank() }?.grade?.let { " · $it" } ?: "")
            metric == ExerciseMetric.DISTANCE_TIME ->
                setFigure(metric, 0, totals.secondsWorked, totals.km * 1000.0).figure
            else -> setFigure(metric, 0, totals.secondsWorked, null).figure
        }
        val subline = listOfNotNull(
            supersetNumber[first.supersetGroup]?.let { "Superset $it" },
            "${doneWorking.size}/${working.size}",
            summary,
        ).joinToString(" · ")
        val expanded = groupIndex in open
        val toggle = { open = if (expanded) open - groupIndex else open + groupIndex }
        if (expanded) {
            Spacer(Modifier.height(10.dp))
            InkPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                ExerciseRow(first.exerciseName, subline, doneWorking.size == working.size && doneWorking.isNotEmpty(), true, 0.dp, toggle)
                // Warm-ups read as such and working sets count from 1 after them.
                var number = 0
                groupSets.forEach { set ->
                    val figure = setText(set, metric, hold, weighted) +
                        set.modifiers.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
                    when {
                        set.warmup -> SetLine("", "Warm-up · $figure", muted = true)
                        else -> SetLine((++number).toString(), if (set.done) figure else "$figure · not done", muted = !set.done)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        } else {
            if (groupIndex > 0 && (groupIndex - 1) !in open) InkDivider()
            ExerciseRow(first.exerciseName, subline, doneWorking.size == working.size && doneWorking.isNotEmpty(), false, 4.dp, toggle)
        }
    }
}

/** One movement's header: its single emerald check when every working set is done, name and a one-line summary. */
@Composable
private fun ExerciseRow(
    name: String,
    subline: String,
    allDone: Boolean,
    expanded: Boolean,
    horizontalPadding: Dp,
    onToggle: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = if (expanded) "Fold the sets" else "Show the sets", onClick = onToggle)
            .heightIn(min = 56.dp)
            .padding(horizontal = horizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(18.dp)) {
            if (allDone) Icon(Icons.Filled.Check, contentDescription = "All sets done", tint = IronvellumColors.Emerald, modifier = Modifier.fillMaxSize())
        }
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium, color = IronvellumColors.Ink)
            Text(subline, style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted)
        }
        Icon(
            if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = IronvellumColors.InkMuted,
        )
    }
}

/** One set as a plain 44dp row: its number, then the figure. */
@Composable
private fun SetLine(label: String, text: String, muted: Boolean) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = IronvellumColors.InkMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(18.dp),
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (muted) IronvellumColors.InkMuted else IronvellumColors.Ink,
        )
    }
}

/** One set's figure in its own unit: "90 kg × 5", "45s", "12 reps", a climb's attempts. */
private fun setText(set: SessionSet, metric: ExerciseMetric, hold: Boolean, weighted: Boolean): String {
    val kg = set.weightKg ?: 0.0
    return when {
        hold -> "${set.durationSec ?: set.reps}s" + if (kg > 0.0) " × ${formatLoadKg(kg)} kg" else ""
        metric == ExerciseMetric.ATTEMPTS_GRADE ->
            // The grade is the climb's identity; the weight slot is not (a boulder problem carries no load).
            "${set.reps} ${plural(set.reps, "attempt", "attempts")}" + set.grade?.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
        metric == ExerciseMetric.DISTANCE_TIME -> setFigure(metric, set.reps, set.durationSec, set.distanceM).figure
        metric == ExerciseMetric.DURATION -> setFigure(metric, set.reps, set.durationSec, null).figure
        // A barbell lift with no load is unset, not bodyweight.
        weighted && kg <= 0.0 -> "${set.reps} reps"
        kg > 0.0 -> "${formatLoadKg(kg)} kg × ${set.reps}"
        else -> "${set.reps} reps"
    }
}
