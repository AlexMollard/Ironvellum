package com.ironvellum.app.ui.train

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.text.SpanStyle
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.key
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import com.ironvellum.app.ui.components.formatLoadKg
import com.ironvellum.app.ui.components.lastLoggedFigure
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.core.content.ContextCompat
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import com.ironvellum.app.WorkoutSessionService
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.ui.draw.alpha
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Public
import com.ironvellum.app.ui.components.IronvellumDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Icon
import com.ironvellum.app.ui.components.InkIconButton
import com.ironvellum.app.ui.components.UndoBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
import com.ironvellum.app.ui.theme.InkPressIndication
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import android.os.SystemClock
import com.ironvellum.app.data.Notifications
import com.ironvellum.app.data.RestClock
import com.ironvellum.app.ui.components.NotificationBlockedNotice
import com.ironvellum.app.ui.components.rememberNotificationAccess
import com.ironvellum.app.domain.RestTimer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ironvellum.app.data.Repository
import com.ironvellum.app.data.cloud.CloudSyncWorker
import com.ironvellum.app.data.cloud.WireLimits
import com.ironvellum.app.domain.PlannedEntry
import com.ironvellum.app.domain.Exercise
import com.ironvellum.app.domain.ExerciseMetric
import com.ironvellum.app.domain.LastLogged
import com.ironvellum.app.domain.isStrength
import com.ironvellum.app.domain.RoutineUpdate
import com.ironvellum.app.domain.SealedReopen
import com.ironvellum.app.domain.SessionSet
import com.ironvellum.app.domain.SessionPeaks
import com.ironvellum.app.domain.SetRecords
import com.ironvellum.app.domain.Sex
import com.ironvellum.app.domain.WorkoutSession
import com.ironvellum.app.domain.WorkoutShare
import com.ironvellum.app.domain.workingNumber
import com.ironvellum.app.ui.components.ShareCardDialog
import com.ironvellum.app.ui.components.ExerciseInfoSheet
import com.ironvellum.app.ui.components.ExercisePickerSheet
import com.ironvellum.app.ui.components.lastLoggedLine
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.components.NavChip
import com.ironvellum.app.ui.components.formatBodyValue
import com.ironvellum.app.ui.components.formatDate
import com.ironvellum.app.ui.components.plural
import com.ironvellum.app.ui.components.decimalKeyboard
import com.ironvellum.app.ui.launchGuarded
import com.ironvellum.app.ui.ironvellumRepository
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumTracking
import com.ironvellum.app.ui.theme.inkBorder
import com.ironvellum.app.ui.theme.IronvellumColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import androidx.compose.runtime.produceState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.ironvellum.app.data.ProgramAnswersStore
import com.ironvellum.app.domain.SessionClock
import com.ironvellum.app.domain.TrainingFocus
import com.ironvellum.app.domain.WEIGHTED_MODIFIER
import com.ironvellum.app.domain.applicableModifiers
import com.ironvellum.app.domain.fmt
import com.ironvellum.app.domain.DecimalInput
import com.ironvellum.app.ui.program.RiteMusclesSheet

data class SessionUi(
    val session: WorkoutSession? = null,
    val sets: List<SessionSet> = emptyList(),
)

class SessionViewModel(
    private val repo: Repository,
    private val appContext: Context,
    private val sessionId: Long,
) : ViewModel() {

    // Records exclude THIS session: a live set must never become its own benchmark.
    val records: StateFlow<Map<Pair<String, Int>, SetRecords.Record>> = combine(
        repo.observeHistory(),
        repo.observeStats().map { SetRecords.bodyweightLookup(it) },
        repo.observeExercises(),
    ) { history, bodyweightAt, catalogue ->
        // Without the metric a hold scores on its (now zero) reps and can
        // never hold a record.
        val metrics = catalogue.associate { it.id to it.metric }
        SetRecords.records(history, bodyweightAt, excludeSessionId = sessionId) { metrics[it.exerciseId] }
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val ui: StateFlow<SessionUi> = combine(
        repo.observeSession(sessionId),
        repo.observeSessionSets(sessionId),
    ) { session, sets ->
        SessionUi(session, sets)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionUi())

    val exercises: StateFlow<List<Exercise>> =
        repo.observeExercises().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Why a movement's load or reps moved since last trial, by exercise: the
     * progression engine's own reason, for the changes only. History does not
     * change while a trial is live, so it is read once.
     */
    val reasons: StateFlow<Map<Long, String>> = flow { emit(repo.trialReasons(sessionId)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Each exercise's top set of its last sealed trial, for the info dialog. */
    val lastLogged: StateFlow<Map<Long, LastLogged>> =
        repo.observeLastLogged().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /**
     * The reader's own bar, for the deeds the victory overlay names: a woman
     * must not be congratulated in the wording of the men's standard.
     */
    val sex: StateFlow<Sex> = repo.observeBodyProfile()
        .map { it.second }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Sex.MALE)

    val bodyweight: StateFlow<Double?> = repo.observeStats()
        .map { it.firstOrNull()?.weightKg }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** What the lifter last told the generator, else the profile mode; times the estimate. */
    private val savedFocus = ProgramAnswersStore.get(appContext)?.focus
    val focus: StateFlow<TrainingFocus> = repo.observeProfile()
        .map { SessionClock.focusFor(savedFocus, it?.trainingMode) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionClock.focusFor(savedFocus, null))

    /**
     * The lifter's own seconds per set for this session: this preset's pace,
     * else his overall pace, else null (the rule-based figure).
     */
    val pace: StateFlow<Int?> = combine(repo.observeHistory(), repo.observeSession(sessionId)) { history, session ->
        SessionClock.pace(history).secondsPerSet(session?.presetId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * How far the finish has got: the level-up page, the deeds page and the
     * summary (each only when the seal earned it, see [CelebrationFlow]), then
     * the question about the routine, then leave.
     */
    enum class Finish { LEVEL, DEEDS, SUMMARY, ROUTINE }

    /**
     * The completion result and the finish stage. Held here, not in the
     * screen, so a rotation keeps the victory; the session is completed and
     * paid before either is set.
     */
    private val _completion = MutableStateFlow<Repository.CompletionResult?>(null)
    val completion: StateFlow<Repository.CompletionResult?> = _completion
    private val _finish = MutableStateFlow(Finish.LEVEL)

    /** What the sealed trial set, frozen at the seal so a rotation keeps it. */
    private val _peaks = MutableStateFlow<List<SessionPeaks.Peak>>(emptyList())
    val peaks: StateFlow<List<SessionPeaks.Peak>> = _peaks
    val finish: StateFlow<Finish> = _finish

    fun advanceFinish(to: Finish) {
        _finish.value = to
    }

    /**
     * The offer to bring the preset in line with what was just done, asked
     * once the celebrations are over. Computed after the session is
     * completed, so losing it loses nothing but the offer.
     */
    private val _routineUpdate = MutableStateFlow<Repository.RoutineUpdateOffer?>(null)
    val routineUpdate: StateFlow<Repository.RoutineUpdateOffer?> = _routineUpdate

    /** Writes [accepted], each already narrowed to the fields the lifter ticked. */
    fun applyRoutineUpdate(accepted: List<RoutineUpdate.Change>) {
        val offer = _routineUpdate.value ?: return
        _routineUpdate.value = null
        if (accepted.isEmpty()) return
        // The screen may leave straight after; the write must not die with it.
        viewModelScope.launchGuarded("update rite") {
            withContext(NonCancellable) { repo.applyRoutineUpdate(offer.presetId, accepted) }
        }
    }

    fun keepPlan() {
        _routineUpdate.value = null
    }

    /** The title worn now, so a deed the seal already dressed you in reads WORN. */
    val wornTitleId: StateFlow<String?> = repo.observeProfile()
        .map { it?.currentTitleId }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun wearTitle(titleId: String) {
        viewModelScope.launchGuarded("wear title") { repo.equipTitle(titleId) }
    }

    /** This trial's rest between sets, while one runs; shared with the trial service. */
    val rest: StateFlow<RestTimer?> = RestClock.timer
        .map { it?.takeIf { t -> t.sessionId == sessionId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun extendRest() = RestClock.extend(sessionId)

    fun shortenRest() = RestClock.shorten(sessionId)

    fun skipRest() = RestClock.cancel(sessionId)

    /** A set ticked done, with sets still waiting, starts its movement's prescribed rest. */
    private fun noteTick(setId: Long, done: Boolean) {
        val sets = ui.value.sets
        if (!RestTimer.startsRest(sets, setId, done)) return
        val set = sets.first { it.id == setId }
        RestClock.start(sessionId, RestTimer.restSeconds(set.exerciseName, focus.value))
    }

    fun updateSet(setId: Long, reps: Int, weightKg: Double?, done: Boolean) {
        noteTick(setId, done)
        viewModelScope.launch { repo.updateSet(setId, reps, weightKg, done) }
    }

    /** A hold's figure is seconds; writing it as reps is the bug this replaces. */
    fun updateHoldSet(setId: Long, seconds: Int, weightKg: Double?, done: Boolean) {
        noteTick(setId, done)
        viewModelScope.launch { repo.updateHoldSet(setId, seconds, weightKg, done) }
    }

    /**
     * One route for every activity edit. The caller hands back the set's whole
     * shape — including the fields its metric does not edit — because the
     * repository writes all columns together and a null here would erase a
     * duration, distance or grade a neighbouring stepper did not touch.
     */
    fun updateActivitySet(
        setId: Long,
        reps: Int,
        durationSec: Int?,
        distanceM: Double?,
        grade: String?,
        weightKg: Double?,
        done: Boolean,
    ) {
        noteTick(setId, done)
        viewModelScope.launch { repo.updateActivitySet(setId, reps, durationSec, distanceM, grade, weightKg, done) }
    }

    fun addSet(exerciseId: Long, reps: Int, weightKg: Double?, modifiers: String, durationSec: Int? = null) {
        viewModelScope.launch {
            repo.addExtraSet(sessionId, exerciseId, reps, weightKg, modifiers, durationSec)
        }
    }

    /** [onRemoved] gets what went, for the Undo bar; it is not called when nothing was removed. */
    fun removeSet(setId: Long, onRemoved: (Repository.RemovedSets) -> Unit) {
        viewModelScope.launch { repo.removeSet(setId)?.let(onRemoved) }
    }

    /** Drops a whole exercise from the live trial: the ✕ on a block's only set. */
    fun removeExercise(exerciseId: Long, onRemoved: (Repository.RemovedSets) -> Unit) {
        viewModelScope.launchGuarded("remove exercise") { repo.removeSessionExercise(sessionId, exerciseId)?.let(onRemoved) }
    }

    /** Undo for either removal above. */
    fun restoreSets(removed: Repository.RemovedSets) {
        viewModelScope.launchGuarded("restore sets") { repo.restoreSets(removed) }
    }

    /** "Mark as warm-up" and "Count as a working set" on a set's edit row. */
    fun setWarmup(setId: Long, warmup: Boolean) {
        viewModelScope.launchGuarded("mark warm-up") { repo.setWarmup(setId, warmup) }
    }

    fun setModifiers(exerciseId: Long, modifiers: String) {
        viewModelScope.launch { repo.setExerciseModifiers(sessionId, exerciseId, modifiers) }
    }

    /**
     * A movement added mid-session starts at a sane default: ten reps, or a
     * [DEFAULT_HOLD_SECONDS] hold. Adding a hold with "10 reps" is how the
     * seconds-as-reps confusion started.
     */
    fun addExercise(exerciseId: Long, isHold: Boolean) {
        viewModelScope.launch {
            if (isHold) {
                repo.addExtraSet(sessionId, exerciseId, 0, null, "", DEFAULT_HOLD_SECONDS)
            } else {
                repo.addExtraSet(sessionId, exerciseId, 10, null, "")
            }
        }
    }

    fun moveExercise(exercisePosition: Int, up: Boolean) {
        viewModelScope.launchGuarded("move exercise") { repo.moveSessionExercise(sessionId, exercisePosition, up) }
    }

    /**
     * True from the Claim Victory tap until the completion result lands (or
     * the claim fails). The session row reads completed a beat before the
     * result arrives; without this the screen would take that for a trial
     * finished in an earlier process and leave before the victory.
     */
    private val _claiming = MutableStateFlow(false)
    val claiming: StateFlow<Boolean> = _claiming

    fun complete() {
        // One completion per session, whatever the button does. The repository
        // already refuses a second one (a `check` inside the transaction, and
        // two concurrent callers pay exactly once — DoubleCompletionTest), but
        // it refuses by THROWING, and this launch has no catch: a second tap
        // landing before the victory overlay replaces the button would take the
        // exception straight into viewModelScope. So the second tap is dropped
        // here, and a genuine failure is surfaced rather than crashing.
        if (_claiming.value) return
        _claiming.value = true
        // Judged on the live trial's own figures, by the rule its badges used.
        val metrics = exercises.value.associate { it.id to it.metric }
        val peaks = SessionPeaks.of(ui.value.sets, records.value, bodyweight.value) { metrics[it.exerciseId] }
        viewModelScope.launch {
            runCatching { repo.completeSession(sessionId) }
                .onSuccess {
                    RestClock.cancel(sessionId)
                    // Completion is the moment a daily user's work becomes
                    // feed/leaderboard-visible; don't wait for a manual push.
                    CloudSyncWorker.pushNow(appContext)
                    // Asked after the XP is banked: the answer can never
                    // change what the session paid.
                    _routineUpdate.value = runCatching { repo.routineUpdateFor(sessionId) }.getOrNull()
                    _peaks.value = peaks
                    _completion.value = it
                }
                .onFailure { _claiming.value = false }
        }
    }

    /**
     * "Sealed too soon? Keep going": takes the seal back (see
     * [Repository.reopenSealedTrial]) and puts the screen back to a live trial.
     * [claiming] stays up until the live row is back, so the "already sealed"
     * guard on the screen never sees a sealed trial with no result and leaves.
     */
    fun reopen(onReopened: () -> Unit, onRefused: (SealedReopen.Refusal) -> Unit) {
        viewModelScope.launchGuarded("reopen trial") {
            when (val decision = repo.reopenSealedTrial(sessionId)) {
                is SealedReopen.Decision.Refuse -> onRefused(decision.reason)
                is SealedReopen.Decision.Reopen -> {
                    ui.first { it.session?.completedAtMs == null }
                    _routineUpdate.value = null
                    _peaks.value = emptyList()
                    _finish.value = Finish.LEVEL
                    _completion.value = null
                    _claiming.value = false
                    // The seal stopped the trial notification; the live trial wants it back.
                    WorkoutSessionService.start(appContext, sessionId)
                    onReopened()
                }
            }
        }
    }

    // Save-on-blur handlers: one write per field edit, never per keystroke.
    fun setSessionTitle(title: String) {
        viewModelScope.launch { repo.setSessionTitle(sessionId, title) }
    }

    fun setSessionNote(note: String) {
        viewModelScope.launch { repo.setSessionNote(sessionId, note) }
    }

    fun setSessionPrivateNote(privateNote: String) {
        viewModelScope.launch { repo.setSessionPrivateNote(sessionId, privateNote) }
    }

    fun abandon(onDone: () -> Unit) {
        viewModelScope.launch {
            RestClock.cancel(sessionId)
            repo.abandonSession(sessionId)
            onDone()
        }
    }
}

/**
 * The factory needs a Context for the post-completion cloud push, and
 * `LocalContext` can only be read from a composable — not from inside the
 * `initializer` lambda, which runs later. Read it here and hand the
 * application context (never an Activity) to the view model.
 */
@Composable
private fun rememberSessionViewModel(sessionId: Long): SessionViewModel {
    // The repository provider is a CreationExtras extension, so it must stay
    // inside the initializer; only the Context has to be read out here.
    val appContext = LocalContext.current.applicationContext
    return viewModel(
        key = "session-$sessionId",
        factory = viewModelFactory { initializer { SessionViewModel(ironvellumRepository(), appContext, sessionId) } },
    )
}

@Composable
fun SessionScreen(
    sessionId: Long,
    onExit: () -> Unit,
    // Where a sealed trial lands: Today, not the rite page that began it,
    // which would only offer the same Begin again.
    onSealed: () -> Unit,
    /** Bumped by the trial notification's Seal action: open with the seal prompt. */
    sealRequest: Int = 0,
    /** The request above was taken, shown or not: the next visit must not repeat it. */
    onSealRequestServed: () -> Unit = {},
    viewModel: SessionViewModel = rememberSessionViewModel(sessionId),
) {
    val records by viewModel.records.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val exercises by viewModel.exercises.collectAsStateWithLifecycle()
    val bodyweight by viewModel.bodyweight.collectAsStateWithLifecycle()
    val sex by viewModel.sex.collectAsStateWithLifecycle()
    val focus by viewModel.focus.collectAsStateWithLifecycle()
    val pace by viewModel.pace.collectAsStateWithLifecycle()
    val routineUpdate by viewModel.routineUpdate.collectAsStateWithLifecycle()
    val completion by viewModel.completion.collectAsStateWithLifecycle()
    val finish by viewModel.finish.collectAsStateWithLifecycle()
    val peaks by viewModel.peaks.collectAsStateWithLifecycle()
    val wornTitleId by viewModel.wornTitleId.collectAsStateWithLifecycle()
    val claiming by viewModel.claiming.collectAsStateWithLifecycle()
    val lastLogged by viewModel.lastLogged.collectAsStateWithLifecycle()
    val reasons by viewModel.reasons.collectAsStateWithLifecycle()
    val rest by viewModel.rest.collectAsStateWithLifecycle()
    var confirmAbandon by remember { mutableStateOf(false) }
    var confirmClaim by remember { mutableStateOf(false) }
    var confirmReopen by remember { mutableStateOf(false) }
    var reopenRefusal by remember { mutableStateOf<SealedReopen.Refusal?>(null) }
    var showExercisePicker by remember { mutableStateOf(false) }
    var editModifiersFor by remember { mutableStateOf<Long?>(null) }
    var showRiteMuscles by remember { mutableStateOf(false) }
    var infoFor by remember { mutableStateOf<Long?>(null) }
    var editLoadFor by remember { mutableStateOf<SessionSet?>(null) }
    var typing by remember { mutableStateOf<Pair<SessionSet, FigureKind>?>(null) }
    var showName by rememberSaveable { mutableStateOf(false) }
    var showNote by rememberSaveable { mutableStateOf(false) }
    // The exercise the lifter opened by hand; null follows the next unlogged set.
    var openOverride by rememberSaveable { mutableStateOf<Long?>(null) }
    // A logged set opened for editing or un-logging.
    var editingSetId by rememberSaveable { mutableStateOf<Long?>(null) }
    var undo by remember { mutableStateOf<UndoPrompt?>(null) }
    var undoSerial by remember { mutableIntStateOf(0) }
    // Bumped when a seal is declined or fails, so the slider comes back to the start.
    var slideReset by remember { mutableIntStateOf(0) }

    fun metricOf(exerciseId: Long): ExerciseMetric =
        exercises.firstOrNull { it.id == exerciseId }?.metric ?: ExerciseMetric.REPS

    // One route per metric, each passing the set's current values for the
    // columns it does not own, so no edit erases a neighbour's figure. Every
    // write the screen makes goes through here: the steppers, the log button,
    // Undo, un-log and the typed entries.
    fun commit(set: SessionSet) {
        val metric = metricOf(set.exerciseId)
        when {
            metric == ExerciseMetric.HOLD -> viewModel.updateHoldSet(set.id, set.durationSec ?: 0, set.weightKg, set.done)
            metric.isStrength -> viewModel.updateSet(set.id, set.reps, set.weightKg, set.done)
            else -> viewModel.updateActivitySet(set.id, set.reps, set.durationSec, set.distanceM, set.grade, set.weightKg, set.done)
        }
    }

    fun applyLoad(set: SessionSet, kg: Double?) = commit(set.copy(weightKg = kg))

    // The lock-screen companion. The service watches the database and stops
    // itself when the session completes or is abandoned - the screen only
    // has to announce that the session is the live one.
    val screenContext = LocalContext.current
    // Android 13+ posts nothing without POST_NOTIFICATIONS, and before this
    // only the reminder toggle asked for it, so most lifters never saw the
    // trial notification. Asked ONCE, the first time a trial opens; a refusal
    // is final here (Settings can still grant it), so there is no nagging.
    val restAccess = rememberNotificationAccess(Notifications.CHANNEL_REST)
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // Re-announce so the service posts now, not at the next ticked set.
        if (granted) WorkoutSessionService.start(screenContext, sessionId)
    }
    LaunchedEffect(sessionId) {
        WorkoutSessionService.start(screenContext, sessionId)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(screenContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            val prefs = screenContext.getSharedPreferences(TRIAL_PREFS, Context.MODE_PRIVATE)
            if (!prefs.getBoolean(KEY_ASKED_NOTIFICATIONS, false)) {
                prefs.edit().putBoolean(KEY_ASKED_NOTIFICATIONS, true).apply()
                askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val session = ui.session
    if (session == null) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Spacer(Modifier.height(20.dp))
            Text("Opening the trial…", style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted)
        }
        return
    }

    // Claimed in an earlier process (killed mid-victory): the XP is banked and
    // the live trial must not come back with a Seal the Trial that can only
    // fail. This claim's own result is still on its way while [claiming].
    if (session.completedAtMs != null && completion == null && !claiming) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Spacer(Modifier.height(20.dp))
            Text("Trial already sealed.", style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted)
        }
        LaunchedEffect(session.id) { onSealed() }
        return
    }

    // The notification's Seal action: the same confirmation the slider gives,
    // since sealing pays out and shows the victory here, not in the shade.
    // Waits for the sets, and only offers what the slider would allow.
    var servedSealRequest by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(sealRequest, ui.sets.isNotEmpty()) {
        if (sealRequest <= servedSealRequest || ui.sets.isEmpty()) return@LaunchedEffect
        servedSealRequest = sealRequest
        onSealRequestServed()
        if (ui.sets.any { it.done } && completion == null && !claiming) confirmClaim = true
    }

    // Render blocks in the rite's saved exercise order: sort explicitly by
    // exercisePosition instead of trusting the query's emission order.
    val blocks = remember(ui.sets) {
        ui.sets.groupBy { it.exerciseId }
            .map { (id, sets) -> TrialBlock(id, sets.minOf { it.exercisePosition }, sets.sortedBy { it.setIndex }) }
            .sortedBy { it.position }
    }
    val doneCount = ui.sets.count { it.done }
    // A warm-up is stored unticked and never counts: it is neither to log nor logged.
    val workCount = ui.sets.count { !it.warmup }
    val unlogged = ui.sets.count { it.isPending }
    // Nothing logged is nothing to seal: finishing an empty trial is an
    // abandon, and it must never mint the completion bonus.
    val anyDone = doneCount > 0
    val allLogged = workCount > 0 && unlogged == 0
    // One exercise is open: the one holding the next unlogged set, unless the
    // lifter opened another. The footer logs the open card's next set.
    val nextBlock = blocks.firstOrNull { block -> block.sets.any { it.isPending } }
    val openBlock = blocks.firstOrNull { it.id == openOverride } ?: nextBlock ?: blocks.lastOrNull()
    val activeSet = openBlock?.sets?.firstOrNull { it.isPending } ?: nextBlock?.sets?.firstOrNull { it.isPending }
    LaunchedEffect(openBlock?.id) { editingSetId = null }

    val seekOpen = remember { BringIntoViewRequester() }
    LaunchedEffect(openBlock?.id) {
        withFrameNanos { }
        seekOpen.bringIntoView()
    }
    LaunchedEffect(undo?.serial) {
        if (undo != null) {
            delay(UNDO_MS)
            undo = null
        }
    }
    LaunchedEffect(claiming) {
        // A claim that failed lets go; the slider has to come back to the start.
        if (!claiming && completion == null) slideReset++
    }

    fun logActive() {
        val set = activeSet ?: return
        commit(set.copy(done = true))
        undoSerial++
        undo = UndoPrompt.Logged(undoSerial, set.id, set.workingNumber(ui.sets))
        // The card moves on once its last set is logged.
        if (openBlock != null && openBlock.sets.none { it.isPending && it.id != set.id }) openOverride = null
    }

    fun offerRemovedUndo(message: String, removed: Repository.RemovedSets) {
        undoSerial++
        undo = UndoPrompt.Removed(undoSerial, message, removed)
    }

    fun undoLog(prompt: UndoPrompt.Logged) {
        ui.sets.firstOrNull { it.id == prompt.setId }?.let { commit(it.copy(done = false)) }
        // The rest belonged to the set that was just taken back.
        viewModel.skipRest()
        undo = null
    }

    // The slider is the only way to seal. Any unlogged set asks first.
    // Reads the live state, not this composition's snapshot: the slider holds
    // on to the reference it was first given, from before any set was logged.
    fun requestSeal() {
        val done = ui.sets.count { it.done }
        if (done == 0 || completion != null || claiming) return
        if (ui.sets.any { it.isPending }) confirmClaim = true else viewModel.complete()
    }

    val signedIn = (LocalContext.current.applicationContext as com.ironvellum.app.IronvellumApp)
        .accountRepository.account.collectAsStateWithLifecycle().value != null
    val minutesLeft = remember(ui.sets, exercises, focus, pace) {
        val metrics = exercises.associate { it.id to it.metric }
        SessionClock.remainingSeconds(ui.sets, { metrics[it] }, focus, pace)
            .takeIf { it > 0 }?.let { SessionClock.minutes(it) }
    }
    val scroll = rememberScrollState()

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TrialHeader(
                overline = if (session.presetId != null) "${session.label} · rite" else null,
                done = doneCount,
                total = workCount,
                minutesLeft = minutesLeft,
                startedAtMs = session.startedAtMs,
                onBack = onExit,
                onMuscles = { showRiteMuscles = true },
                onAbandon = { confirmAbandon = true },
            )
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scroll)
                    .padding(horizontal = 16.dp),
            ) {
                rest?.let {
                    // The countdown in the footer still runs, but the buzz at its end cannot arrive.
                    NotificationBlockedNotice(
                        channelId = Notifications.CHANNEL_REST,
                        access = restAccess,
                        blockedText = "Rest-over alerts are blocked, so the end of a rest will not buzz.",
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                var previousFolded = false
                blocks.forEachIndexed { blockIdx, block ->
                    key(block.id) {
                        val first = block.first
                        val metric = metricOf(block.id)
                        val exercise = exercises.firstOrNull { it.id == block.id }
                        val weighted = exercise?.isWeighted ?: false
                        if (block.id == openBlock?.id) {
                            Spacer(Modifier.height(12.dp))
                            val modifiersEditable = canEditModifiers(
                                exercise?.let(::applicableModifiers).orEmpty(),
                                first.modifiers,
                            )
                            val peak = remember(records, first.exerciseName) {
                                val name = first.exerciseName.lowercase().trim()
                                records.filterKeys { it.first == name }.values.maxByOrNull { it.score }
                            }
                            OpenExerciseCard(
                                block = block,
                                metric = metric,
                                weighted = weighted,
                                reference = referenceLine(exercise, metric, lastLogged[block.id], peak),
                                reason = reasons[block.id],
                                modifiersEditable = modifiersEditable,
                                activeSetId = if (activeSet?.exerciseId == block.id) activeSet.id else null,
                                editingSetId = editingSetId,
                                newPeaks = newPeakSetIds(block.sets, metric, records, bodyweight),
                                canMoveUp = blockIdx > 0,
                                canMoveDown = blockIdx < blocks.lastIndex,
                                onInfo = { infoFor = block.id },
                                onEditModifiers = { editModifiersFor = block.id },
                                onMove = { up -> viewModel.moveExercise(first.exercisePosition, up) },
                                onEditLoad = if (metric.isStrength || (metric == ExerciseMetric.DURATION && weighted)) {
                                    { editLoadFor = block.sets.firstOrNull { it.isPending } ?: block.sets.last() }
                                } else {
                                    null
                                },
                                onAddSet = {
                                    // A duplicate carries the block's own figure: a hold's
                                    // seconds, an activity's duration — a copy of a Yoga set
                                    // starting at "10 reps" repeats the seconds-as-reps bug.
                                    // Never a warm-up's figure: the new set is a working one.
                                    val base = block.sets.firstOrNull { !it.warmup } ?: first
                                    val copySeconds = when {
                                        metric == ExerciseMetric.HOLD -> base.durationSec ?: DEFAULT_HOLD_SECONDS
                                        metric == ExerciseMetric.DURATION || metric == ExerciseMetric.DISTANCE_TIME -> base.durationSec
                                        else -> null
                                    }
                                    viewModel.addSet(block.id, base.reps, base.weightKg, base.modifiers, copySeconds)
                                },
                                // Removing a block's only set removes the exercise, so a
                                // one-off added by mistake can be taken back out.
                                onRemoveSet = { set ->
                                    if (block.sets.size > 1) {
                                        viewModel.removeSet(set.id) { offerRemovedUndo("Set removed", it) }
                                    } else {
                                        viewModel.removeExercise(block.id) { offerRemovedUndo("${first.exerciseName} removed", it) }
                                    }
                                },
                                onRemoveExercise = {
                                    viewModel.removeExercise(block.id) { offerRemovedUndo("${first.exerciseName} removed", it) }
                                },
                                onEdit = ::commit,
                                onTypeLoad = { editLoadFor = it },
                                onTypeFigure = { set, kind -> typing = set to kind },
                                onToggleEdit = { set -> editingSetId = if (editingSetId == set.id) null else set.id },
                                onToggleWarmup = { set -> viewModel.setWarmup(set.id, !set.warmup) },
                                onUnlog = { set ->
                                    commit(set.copy(done = false))
                                    editingSetId = null
                                },
                                modifier = Modifier.bringIntoViewRequester(seekOpen),
                            )
                            Spacer(Modifier.height(12.dp))
                            previousFolded = false
                        } else {
                            if (previousFolded) FolderRule()
                            FoldedExerciseRow(
                                name = first.exerciseName,
                                subline = foldedSubline(block, metric, weighted),
                                done = block.sets.count { it.done },
                                total = block.sets.count { !it.warmup },
                                onOpen = { openOverride = block.id },
                            )
                            previousFolded = true
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                TrialEndRows(
                    nameValue = session.title.ifBlank { null }
                        ?: "Defaults to ${session.label} · ${formatDate(session.startedAtMs, "d MMM")}",
                    nameSet = session.title.isNotBlank(),
                    noteValue = session.note.lineSequence().firstOrNull { it.isNotBlank() }
                        ?: session.privateNote.lineSequence().firstOrNull { it.isNotBlank() },
                    onAddExercise = { showExercisePicker = true },
                    onName = { showName = true },
                    onNote = { showNote = true },
                )
                if (!allLogged) {
                    // Sealing early: the same slider the footer shows once every set is logged.
                    Spacer(Modifier.height(32.dp))
                    SlideToSeal(
                        label = if (anyDone) {
                            "$unlogged ${plural(unlogged, "set", "sets")} left · slide to seal"
                        } else {
                            "Log a set to seal the trial"
                        },
                        enabled = anyDone && !claiming,
                        onSealed = ::requestSeal,
                        resetKey = slideReset,
                        trackColor = IronvellumColors.Vault,
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
            TrialFooter(
                allLogged = allLogged,
                rest = rest,
                nextSetNumber = activeSet?.workingNumber(ui.sets),
                nextLine = activeSet?.let { set ->
                    val figure = setFigureText(set, metricOf(set.exerciseId), exercises.firstOrNull { it.id == set.exerciseId }?.isWeighted ?: false)
                    // Named only when it is not the card on screen.
                    val where = if (set.exerciseId != openBlock?.id) " · ${set.exerciseName}" else ""
                    "Set ${set.workingNumber(ui.sets)}$where · $figure"
                },
                onLog = ::logActive,
                onExtend = viewModel::extendRest,
                onShorten = viewModel::shortenRest,
                onSkip = viewModel::skipRest,
                onSealed = ::requestSeal,
                slideReset = slideReset,
            )
        }
        undo?.let { prompt ->
            UndoBar(
                message = prompt.message,
                onUndo = {
                    when (prompt) {
                        is UndoPrompt.Logged -> undoLog(prompt)
                        is UndoPrompt.Removed -> {
                            viewModel.restoreSets(prompt.removed)
                            undo = null
                        }
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = FOOTER_HEIGHT + 8.dp),
            )
        }
    }

    if (confirmAbandon) {
        IronvellumDialog(
            onDismissRequest = { confirmAbandon = false },
            title = { Text("Abandon this trial?") },
            text = { Text("Unsealed trials grant no XP and are erased from the Chronicle.") },
            // Staying is the filled action; abandoning wears the danger style, so a
            // reflex tap on the bright button never erases the trial.
            confirmButton = {
                IronvellumButton(
                    "Abandon",
                    danger = true,
                    onClick = {
                        confirmAbandon = false
                        viewModel.abandon(onExit)
                    },
                )
            },
            dismissButton = {
                IronvellumButton("Keep going", onClick = { confirmAbandon = false })
            },
        )
    }

    if (confirmClaim) {
        val declined: () -> Unit = {
            confirmClaim = false
            slideReset++
        }
        IronvellumDialog(
            onDismissRequest = declined,
            title = {
                Text(
                    if (unlogged == 0) {
                        "Seal the trial?"
                    } else {
                        "Seal with $unlogged ${plural(unlogged, "set", "sets")} unlogged?"
                    },
                )
            },
            text = {
                Text(
                    if (unlogged == 0) {
                        "Every set is logged. Seal it now?"
                    } else {
                        "${plural(unlogged, "It", "They")} won't count."
                    },
                )
            },
            // Sealing is the deliberate action here, so it takes the confirm
            // slot; KEEP GOING is the safe default.
            confirmButton = {
                IronvellumButton(
                    if (unlogged == 0) "Seal" else "Seal anyway",
                    onClick = {
                        confirmClaim = false
                        viewModel.complete()
                    },
                )
            },
            dismissButton = {
                IronvellumButton("Keep going", quiet = true, onClick = declined)
            },
        )
    }

    editLoadFor?.let { target ->
        val initial = target.weightKg?.let { loadText(it) }.orEmpty()
        var field by remember(target.id) { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
        val parsed = parseLoadKg(field.text)
        // This set, every later set of the exercise not yet ticked off (a
        // working weight usually holds for the rest of the exercise), and every
        // later TICKED set logged at this set's old load: correcting a load
        // typed wrong on all five ticked sets was five dialogs. A ticked set at
        // a different load was a deliberate change and is left alone.
        val following = ui.sets.filter {
            it.exerciseId == target.exerciseId && it.setIndex >= target.setIndex &&
                (it.id == target.id || (!it.warmup && (!it.done || it.weightKg == target.weightKg)))
        }
        val focus = remember { FocusRequester() }
        LaunchedEffect(target.id) { focus.requestFocus() }
        fun apply(sets: List<SessionSet>) {
            val kg = parsed.getOrNull() ?: return
            sets.forEach { applyLoad(it, kg.takeIf { v -> v > 0.0 }) }
            editLoadFor = null
        }
        IronvellumDialog(
            // No Cancel: back and an outside tap already dismiss, and a third
            // button wrapped the row into a ragged stack at 360dp.
            onDismissRequest = { editLoadFor = null },
            title = {
                Column {
                    Text(
                        target.exerciseName,
                        style = MaterialTheme.typography.titleMedium,
                        color = IronvellumColors.Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "SET ${target.workingNumber(ui.sets)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = ChakraPetch,
                        color = IronvellumColors.InkMuted,
                        letterSpacing = 2.sp,
                    )
                }
            },
            text = {
                OutlinedTextField(
                    shape = MaterialTheme.shapes.small,
                    value = field,
                    onValueChange = { field = cleanLoadInput(it) },
                    singleLine = true,
                    label = { Text("Load (kg)") },
                    placeholder = { Text("Leave blank for bodyweight") },
                    isError = parsed.isFailure,
                    supportingText = if (parsed.isFailure) {
                        { Text("Enter 0 to ${MAX_LOAD_KG.toInt()} kg.") }
                    } else {
                        null
                    },
                    keyboardOptions = decimalKeyboard(),
                    colors = fieldColors(accent = IronvellumColors.SystemGreen),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val many = following.size > 1
                    IronvellumButton(
                        "This set",
                        quiet = many,
                        enabled = parsed.isSuccess,
                        onClick = { apply(listOf(target)) },
                    )
                    if (many) {
                        IronvellumButton(
                            "All ${following.size} sets",
                            enabled = parsed.isSuccess,
                            onClick = { apply(following) },
                        )
                    }
                }
            },
        )
    }

    typing?.let { (target, kind) ->
        FigureEntryDialog(
            set = target,
            number = target.workingNumber(ui.sets),
            kind = kind,
            onApply = { n ->
                // The set as it stands now, not as it stood when the dialog opened.
                val current = ui.sets.firstOrNull { it.id == target.id } ?: target
                commit(current.withTypedFigure(kind, n))
                typing = null
            },
            onDismiss = { typing = null },
        )
    }

    if (showName) {
        TrialNameDialog(
            current = session.title,
            signedIn = signedIn,
            hint = "Defaults to ${session.label} · ${formatDate(session.startedAtMs, "d MMM")}",
            onSave = { name ->
                viewModel.setSessionTitle(name)
                showName = false
            },
            onDismiss = { showName = false },
        )
    }

    if (showNote) {
        TrialNoteDialog(
            publicNote = session.note,
            privateNote = session.privateNote,
            signedIn = signedIn,
            onSave = { publicNote, privateNote ->
                viewModel.setSessionNote(publicNote)
                viewModel.setSessionPrivateNote(privateNote)
                showNote = false
            },
            onDismiss = { showNote = false },
        )
    }

    if (showRiteMuscles) {
        RiteMusclesSheet(
            title = session.label,
            entries = ui.sets.groupBy { it.exercisePosition }.toSortedMap().values.map { rows ->
                val first = rows.first()
                PlannedEntry(first.exerciseName, sets = rows.size, reps = first.reps, targetWeightKg = null, modifiers = first.modifiers)
            },
            onDismiss = { showRiteMuscles = false },
        )
    }
    editModifiersFor?.let { exerciseId ->
        val current = ui.sets.firstOrNull { it.exerciseId == exerciseId }
        ModifierPickerDialog(
            exerciseName = current?.exerciseName ?: "Exercise",
            applicable = exercises.firstOrNull { it.id == exerciseId }?.let(::applicableModifiers).orEmpty(),
            selected = modifierTokens(current?.modifiers),
            onConfirm = { picked ->
                viewModel.setModifiers(exerciseId, picked.joinToString(", "))
                editModifiersFor = null
            },
            onDismiss = { editModifiersFor = null },
        )
    }

    infoFor?.let { exerciseId ->
        val exercise = exercises.firstOrNull { it.id == exerciseId }
        val current = ui.sets.firstOrNull { it.exerciseId == exerciseId }
        // The block can vanish under an open dialog (last set deleted); then
        // there is nothing to show and the dialog simply does not draw.
        if (exercise != null && current != null) {
            ExerciseInfoSheet(
                exercise = exercise,
                // Sealed trials only: the live one is never its own "last".
                lastLine = lastLogged[exerciseId]?.let { lastLoggedLine(it, exercise, System.currentTimeMillis()) },
                onDismiss = { infoFor = null },
                onPick = null,
                modifiers = current.modifiers,
            )
        } else {
            // Forget the block, or re-adding it later would reopen the sheet unasked.
            LaunchedEffect(exerciseId) { infoFor = null }
        }
    }

    if (showExercisePicker) {
        ExercisePickerSheet(
            exercises = exercises,
            onPick = { exercise ->
                viewModel.addExercise(exercise.id, exercise.metric == ExerciseMetric.HOLD)
                showExercisePicker = false
            },
            onDismiss = { showExercisePicker = false },
        )
    }

    var shareText by remember { mutableStateOf<String?>(null) }

    // Celebrate first, then ask about the routine, then leave. The stage lives
    // in the view model, so a rotation resumes where the lifter was; the
    // session is already completed and paid, whatever the answer.
    completion?.let { result ->
        val hasLevel = CelebrationFlow.hasLevelUp(result)
        val hasDeeds = CelebrationFlow.deedsOf(result).isNotEmpty()
        when (val stage = CelebrationFlow.resolve(finish, hasLevel, hasDeeds)) {
            SessionViewModel.Finish.ROUTINE -> {
                val offer = routineUpdate
                if (offer != null) {
                    // A set left unticked still counts as a row, so it never
                    // lowers the set count (RoutineUpdate.propose); the title
                    // says so whenever an offered movement has one.
                    val offered = offer.changes.map { it.before.exerciseId }.toSet()
                    val shortDay = ui.sets.any { it.isPending && it.exerciseId in offered }
                    RoutineUpdateDialog(
                        offer = offer,
                        shortDay = shortDay,
                        onUpdate = viewModel::applyRoutineUpdate,
                        onKeep = viewModel::keepPlan,
                    )
                } else {
                    // Answered, or nothing to ask: the finished session is done.
                    LaunchedEffect(result) { onSealed() }
                }
            }
            else -> TrialCelebration(
                stage = stage,
                result = result,
                title = session.title.ifBlank { session.label },
                peaks = peaks,
                totals = remember(ui.sets, exercises) { WorkoutShare.totals(ui.sets, exercises.associateBy { it.id }) },
                sex = sex,
                wornTitleId = wornTitleId,
                reopenUntilMs = session.completedAtMs?.let { it + SealedReopen.WINDOW_MS },
                onContinue = { viewModel.advanceFinish(CelebrationFlow.after(stage, hasLevel, hasDeeds)) },
                onSkipToSummary = { viewModel.advanceFinish(SessionViewModel.Finish.SUMMARY) },
                onWear = viewModel::wearTitle,
                onShare = {
                    shareText = WorkoutShare.format(
                        // The session row in the flow may not have refreshed yet;
                        // the completion result carries the authoritative figures.
                        session.copy(
                            completedAtMs = session.completedAtMs ?: System.currentTimeMillis(),
                            xpAwarded = result.xpAwarded,
                            strengthScore = result.strengthScore,
                        ),
                        ui.sets,
                        exercises.associateBy { it.id },
                        peaks = peaks.map { it.exerciseName },
                    )
                },
                onReopen = { confirmReopen = true },
            )
        }
    }

    if (confirmReopen) {
        IronvellumDialog(
            onDismissRequest = { confirmReopen = false },
            title = { Text("Reopen this trial?") },
            text = { Text("The XP it paid is taken back until you seal again.") },
            // Keeping it sealed is the filled action: a reflex tap changes nothing.
            confirmButton = {
                IronvellumButton("Keep it sealed", onClick = { confirmReopen = false })
            },
            dismissButton = {
                IronvellumButton(
                    "Reopen",
                    quiet = true,
                    onClick = {
                        confirmReopen = false
                        viewModel.reopen(
                            onReopened = { slideReset++ },
                            onRefused = { reopenRefusal = it },
                        )
                    },
                )
            },
        )
    }

    reopenRefusal?.let { reason ->
        IronvellumDialog(
            onDismissRequest = { reopenRefusal = null },
            title = { Text("This trial stays sealed") },
            text = { Text(reopenRefusalText(reason)) },
            confirmButton = { IronvellumButton("OK", onClick = { reopenRefusal = null }) },
        )
    }

    shareText?.let { text ->
        ShareCardDialog(text = text, onDismiss = { shareText = null })
    }
}

/** Why a trial cannot be reopened, in the words the dialog shows. */
private fun reopenRefusalText(reason: SealedReopen.Refusal): String = when (reason) {
    SealedReopen.Refusal.NOT_SEALED -> "This trial is already open."
    SealedReopen.Refusal.WINDOW_CLOSED -> "Ten minutes have passed. Amend it from the Chronicle instead."
    SealedReopen.Refusal.ANOTHER_LIVE -> "Another trial is open. Seal or abandon it first."
    SealedReopen.Refusal.AMENDED -> "It was amended after sealing, so it cannot be taken back."
    SealedReopen.Refusal.LEDGER_SHORT -> "The XP it paid has been spent, so it cannot be taken back."
}

@Composable
internal fun SetRow(
    label: String,
    exerciseName: String,
    setIndex: Int,
    records: Map<Pair<String, Int>, SetRecords.Record>,
    bodyweight: Double?,
    reps: Int,
    weightKg: Double?,
    done: Boolean,
    /** True when [reps] is seconds held: the column counts time, not repetitions. */
    isHold: Boolean = false,
    /** Which columns this row renders; REPS is the lifting default. */
    metric: ExerciseMetric = ExerciseMetric.REPS,
    /** Weighted DURATION work (Weighted Skipping) keeps its LOAD column. */
    isWeighted: Boolean = false,
    durationSec: Int? = null,
    distanceM: Double? = null,
    grade: String = "",
    /**
     * False for activity work: its figure is not strength, so it never gets a
     * PR line at all.
     */
    scoresStrength: Boolean = true,
    /** Best score of this exercise's earlier done sets today; a PR must beat it too. */
    bestEarlierThisWorkout: Double? = null,
    showColumnLabels: Boolean = true,
    /**
     * Keep the PR line's height even while it is empty, so ticking a set does
     * not nudge the rows beneath it. The block's last row has nothing beneath,
     * so it passes false and the card ends at the steppers.
     */
    reserveDeltaLine: Boolean = true,
    onRemove: (() -> Unit)? = null,
    onChange: (Int, Double?, Boolean) -> Unit,
    /** Opens the typed-load dialog; the stepper's ± only walk the plate grid. */
    onLoadTap: () -> Unit = {},
    /**
     * The activity route: writes the whole set shape. Every caller passes the
     * set's CURRENT values for the fields its metric does not edit — a null
     * for an unowned field is what used to erase a duration on a checkbox tick.
     */
    onActivityChange: (reps: Int, durationSec: Int?, distanceM: Double?, grade: String?, weightKg: Double?, done: Boolean) -> Unit =
        { _, _, _, _, _, _ -> },
) {
    // The controls are one row; the PR delta is a line UNDER them. It used to be
    // a sibling inside the row calling fillMaxWidth(), which ate the whole width
    // and starved the two weight(1f) stepper columns to zero - LOAD and REPS
    // wrapped one letter per line and the steppers vanished.
    Column(
        Modifier
            .fillMaxWidth()
            .alpha(if (done) 0.6f else 1f)
            .padding(vertical = 3.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
        // Material's checkbox is a rounded square with a machine-drawn tick,
        // the last stock control in the app. Swapping it for a drawn plate is a
        // styling change; the SEMANTICS are not optional, so they are restored
        // explicitly here:
        //  - toggleable(role = Role.Checkbox) carries checked state and the role
        //    announcement that a bare clickable drops entirely,
        //  - the outer 48dp box keeps Material's minimum touch target, which a
        //    26dp visual would otherwise shrink (and which also restores the
        //    row spacing Material's own 48dp reservation gave this row).
        Box(
            Modifier
                // 48dp reserved, 42dp effective: the set row's own
                // padding(vertical = 3.dp) clips it, and Compose delivers touch
                // only within the parent's bounds, so requiredSize cannot
                // reclaim it (measured on device: 126px = 42dp either way).
                // Material's Checkbox was clipped identically here, so this is
                // the pre-existing row geometry, not a regression - widening it
                // means changing the row's padding, which moves every set row.
                .size(48.dp)
                .toggleable(
                    value = done,
                    role = Role.Checkbox,
                    // The tick passes the set's CURRENT duration, distance,
                    // grade and load straight back through: this line is the
                    // whole fix for the historical bug where ticking done
                    // rewrote the row and wiped an activity's seconds.
                    onValueChange = {
                        if (metric.isStrength) {
                            onChange(reps, weightKg, it)
                        } else {
                            onActivityChange(reps, durationSec, distanceM, grade, weightKg, it)
                        }
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(26.dp)
                    .background(
                        if (done) IronvellumColors.SystemGreen.copy(alpha = 0.18f) else Color.Transparent,
                        MaterialTheme.shapes.extraSmall,
                    )
                    .inkBorder(
                        if (done) IronvellumColors.SystemGreen else IronvellumColors.Bracket,
                        MaterialTheme.shapes.extraSmall,
                        1.5.dp,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (done) {
                    Text(
                        "\u2713",
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = ChakraPetch,
                        color = IronvellumColors.SystemGreen,
                    )
                }
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = ChakraPetch,
            color = IronvellumColors.SystemGreen,
            // Fixed width and centred: Chakra Petch's digits are proportional, so
            // a bare "1" was narrower than "2" and shifted every column of the
            // ticked row about 5px against the unticked one.
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 14.dp),
        )
        if (metric.isStrength) {
            // LOAD gets the wider share: "102.5kg" is the longest figure in the
            // row and was drawn over its own − and + at 360dp.
            Column(Modifier.weight(1.25f)) {
                if (showColumnLabels) ColumnLabel("LOAD")
                Stepper(
                    // A barbell lift with no load yet is unset, not bodyweight:
                    // "BW" on a first bench press read as an instruction. A bare
                    // unit, because Chakra Petch has no dash glyph.
                    value = if (isWeighted && (weightKg ?: 0.0) <= 0.0) "kg" else formatKg(weightKg),
                    what = "load",
                    onMinus = { onChange(reps, stepDownKg(weightKg), done) },
                    onPlus = { onChange(reps, stepUpKg(weightKg), done) },
                    onValueClick = onLoadTap,
                    dimmed = done,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Column(Modifier.weight(1f)) {
                if (showColumnLabels) ColumnLabel(if (isHold) "SECONDS" else "REPS")
                // A hold steps in 5s: tapping + fifty-nine times to reach a
                // minute is not an input method.
                val step = if (isHold) HOLD_STEP_SECONDS else 1
                Stepper(
                    value = if (isHold) "${reps}s" else reps.toString(),
                    what = if (isHold) "seconds" else "reps",
                    onMinus = { onChange((reps - step).coerceAtLeast(0), weightKg, done) },
                    onPlus = { onChange(reps + step, weightKg, done) },
                    dimmed = done,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else when (metric) {
            ExerciseMetric.DURATION -> {
                // ActivityScore pays a load bonus here, so Weighted Skipping
                // keeps its LOAD column; plain Yoga does not.
                if (isWeighted) {
                    Column(Modifier.weight(1f)) {
                        if (showColumnLabels) ColumnLabel("LOAD")
                        Stepper(
                            value = formatKg(weightKg),
                            what = "load",
                            onMinus = { onActivityChange(reps, durationSec, distanceM, grade, stepDownKg(weightKg), done) },
                            onPlus = { onActivityChange(reps, durationSec, distanceM, grade, stepUpKg(weightKg), done) },
                            onValueClick = onLoadTap,
                            dimmed = done,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Column(Modifier.weight(1f)) {
                    if (showColumnLabels) ColumnLabel("MINUTES")
                    val minutes = (durationSec ?: 0) / 60
                    Stepper(
                        value = minutes.toString(),
                        what = "minutes",
                        onMinus = {
                            onActivityChange(reps, (minutes - DURATION_STEP_MINUTES).coerceAtLeast(0) * 60, distanceM, grade, weightKg, done)
                        },
                        onPlus = {
                            onActivityChange(reps, (minutes + DURATION_STEP_MINUTES) * 60, distanceM, grade, weightKg, done)
                        },
                        dimmed = done,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            ExerciseMetric.DISTANCE_TIME -> {
                // ActivityScore ignores weight for this metric, so there is no
                // LOAD column to sit there dead.
                Column(Modifier.weight(1f)) {
                    if (showColumnLabels) ColumnLabel("KM")
                    val km = (distanceM ?: 0.0) / 1000.0
                    Stepper(
                        value = formatBodyValue(km),
                        what = "distance",
                        onMinus = {
                            onActivityChange(reps, durationSec, ((km - DISTANCE_STEP_KM).coerceAtLeast(0.0)) * 1000.0, grade, weightKg, done)
                        },
                        onPlus = {
                            onActivityChange(reps, durationSec, (km + DISTANCE_STEP_KM) * 1000.0, grade, weightKg, done)
                        },
                        dimmed = done,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Column(Modifier.weight(1f)) {
                    if (showColumnLabels) ColumnLabel("MINUTES")
                    val minutes = (durationSec ?: 0) / 60
                    Stepper(
                        value = minutes.toString(),
                        what = "minutes",
                        onMinus = {
                            onActivityChange(reps, (minutes - DURATION_STEP_MINUTES).coerceAtLeast(0) * 60, distanceM, grade, weightKg, done)
                        },
                        onPlus = {
                            onActivityChange(reps, (minutes + DURATION_STEP_MINUTES) * 60, distanceM, grade, weightKg, done)
                        },
                        dimmed = done,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            ExerciseMetric.ATTEMPTS_GRADE -> {
                // The attempt count rides the reps column (as ActivityScore
                // reads it), but calling that column REPS invited typing a
                // lift's numbers into a bouldering problem.
                Column(Modifier.weight(1f)) {
                    if (showColumnLabels) ColumnLabel("ATTEMPTS")
                    Stepper(
                        value = reps.toString(),
                        what = "attempts",
                        onMinus = { onActivityChange((reps - 1).coerceAtLeast(0), durationSec, distanceM, grade, weightKg, done) },
                        onPlus = { onActivityChange(reps + 1, durationSec, distanceM, grade, weightKg, done) },
                        dimmed = done,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Column(Modifier.weight(1f)) {
                    if (showColumnLabels) ColumnLabel("GRADE")
                    // Capped at the server's ceiling here so a long entry
                    // cannot be typed at all, not truncated after the fact.
                    OutlinedTextField(
                        shape = MaterialTheme.shapes.small,
                        value = grade,
                        onValueChange = { onActivityChange(reps, durationSec, distanceM, it.take(WireLimits.GRADE_MAX), weightKg, done) },
                        singleLine = true,
                        placeholder = { Text("V5", style = MaterialTheme.typography.labelSmall, color = IronvellumColors.InkMuted) },
                        textStyle = MaterialTheme.typography.labelLarge.copy(
                            fontFamily = ChakraPetch,
                            fontWeight = FontWeight.Bold,
                            color = IronvellumColors.Ink,
                        ),
                        colors = fieldColors(accent = IronvellumColors.SystemGreen),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            ExerciseMetric.REPS, ExerciseMetric.HOLD -> {}
        }
            onRemove?.let { remove ->
                Text(
                    "✕",
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = ChakraPetch,
                    color = IronvellumColors.InkMuted,
                    modifier = Modifier
                        .clickable { remove() }
                        .padding(horizontal = 6.dp, vertical = 10.dp),
                )
            }
        }
        // Fixed-height delta line under the steppers: always allocated, so
        // ticking a set never reflows the row. Only a DONE set speaks: under
        // an undone one the figure is still a plan, and a fresh session read
        // "NEW PR" under every one of its seventeen sets.
        val delta = bodyweight?.takeIf { scoresStrength && done }?.let { bw ->
            SetRecords.delta(
                records, exerciseName, setIndex, reps, weightKg, bw,
                isHold = isHold,
                bestEarlierThisWorkout = bestEarlierThisWorkout,
            )
        }
        if (reserveDeltaLine || delta != null) SetDeltaBadge(delta, displaySetNo = setIndex + 1)
    }
}

/** Shared 10sp column heading for the activity stepper columns. */
@Composable
private fun ColumnLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontFamily = ChakraPetch,
        fontSize = 10.sp,
        letterSpacing = 1.sp,
        color = IronvellumColors.InkMuted,
    )
    Spacer(Modifier.height(2.dp))
}

/** Glanceable per-set-position PR readout. Copy is deliberately telegraphic. */
@Composable
private fun SetDeltaBadge(delta: SetRecords.Delta?, displaySetNo: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(16.dp)
            .padding(start = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (delta == null) return@Row
        val record = delta.record
        if (record == null) {
            Text(
                "Set $displaySetNo — first peak",
                style = MaterialTheme.typography.labelSmall,
                color = IronvellumColors.InkMuted,
            )
            return@Row
        }
        if (delta.isRecord) {
            Icon(
                Icons.Filled.Bolt,
                contentDescription = "New peak",
                tint = IronvellumColors.SovereignGold,
                modifier = Modifier.size(12.dp),
            )
            Text(
                "NEW PEAK",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = ChakraPetch,
                color = IronvellumColors.SovereignGold,
            )
        }
        // Any set above its position's record says "was", the record it
        // passed, whether or not an earlier set today already took NEW PEAK:
        // equal sets read the same line, one of them with the gold mark. "PEAK"
        // stays for a set at or under the record it is chasing.
        val beaten = delta.deltaScore > 0.0
        Text(
            (if (beaten) "was " else "PEAK ") + "${record.reps}×${prLoad(record.weightKg)}",
            style = MaterialTheme.typography.labelSmall,
            color = IronvellumColors.InkMuted,
        )
        // Shortfall is muted, never red: a lighter back-off set is normal.
        val colour = if (delta.deltaScore >= 0.0) IronvellumColors.EmeraldBright else IronvellumColors.InkMuted
        Text(
            (if (delta.deltaScore >= 0.0) "▲ +" else "▽ ") + "%.1f".fmt(delta.deltaScore),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            color = colour,
        )
    }
}

/** "40kg" or plain "BW" when the PR was a pure bodyweight set. formatKg already carries the unit. */
private fun prLoad(weightKg: Double?): String =
    weightKg?.let { formatKg(it) } ?: "BW"

/** The ± buttons walk the plate grid, so 15.2 kg steps to 17.5 or 15, never 17.7 or 12.7. */
internal const val LOAD_STEP_KG = 2.5

/** Heavier than any lift on record; a guard against a stray extra digit. */
internal const val MAX_LOAD_KG = 500.0

internal const val LOAD_INPUT_MAX_CHARS = 7

/** Loads are kept to the gram, see [parseLoadKg]. */
internal const val LOAD_INPUT_DECIMALS = 3

internal fun cleanLoadInput(typed: TextFieldValue): TextFieldValue =
    typed.copy(text = DecimalInput.sanitize(typed.text, LOAD_INPUT_DECIMALS, LOAD_INPUT_MAX_CHARS))

internal fun stepDownKg(kg: Double?): Double? =
    kg?.let { (kotlin.math.ceil(it / LOAD_STEP_KG - 1e-9) - 1) * LOAD_STEP_KG }?.takeIf { it > 0.0 }

internal fun stepUpKg(kg: Double?): Double =
    (kotlin.math.floor((kg ?: 0.0) / LOAD_STEP_KG + 1e-9) + 1) * LOAD_STEP_KG

/**
 * A typed load: blank is bodyweight (0.0), a comma is a decimal point, and
 * the value is kept to the gram. Anything else, or out of range, fails.
 */
internal fun parseLoadKg(text: String): Result<Double> {
    if (text.isBlank()) return Result.success(0.0)
    val kg = DecimalInput.parse(text)
        ?: return Result.failure(IllegalArgumentException("not a number: $text"))
    if (kg < 0.0 || kg > MAX_LOAD_KG) return Result.failure(IllegalArgumentException("out of range: $text"))
    return Result.success(Math.round(kg * 1000.0) / 1000.0)
}

/** A load as the dialog shows it for editing: no unit, no trailing ".0". */
internal fun loadText(kg: Double): String =
    if (kg == kg.toLong().toDouble()) kg.toLong().toString() else kg.toString()

/**
 * A ± stepper. The drawn frame stays [STEPPER_FRAME_HEIGHT] tall; the box
 * around it is [STEPPER_HIT_HEIGHT] of touch target, which the set row's 48dp
 * tick already reserves, so the row grows no taller. Each ± zone takes half
 * the width (the figure's own tap target, when there is one, sits on top of
 * the middle and wins there).
 */
@Composable
private fun Stepper(
    value: String,
    /** What the figure is, for the ± announcements: "Decrease load". */
    what: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    dimmed: Boolean = false,
    /** When set, tapping the figure itself opens exact entry. */
    onValueClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Box(modifier.height(STEPPER_HIT_HEIGHT), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(STEPPER_FRAME_HEIGHT)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(IronvellumColors.Abyss)
                .inkBorder(IronvellumColors.Rune, MaterialTheme.shapes.extraSmall, 1.dp),
        )
        Row(Modifier.fillMaxSize()) {
            StepZone("−", "Decrease $what", onMinus, Alignment.CenterStart, Modifier.weight(1f))
            StepZone("+", "Increase $what", onPlus, Alignment.CenterEnd, Modifier.weight(1f))
        }
        // A load's "kg" drops to a small suffix and a five-character figure
        // ("102.5") steps down a size, so the figure fits between the − and +
        // glyphs of a 360dp row instead of being drawn over them.
        val unitAt = if (value.length > 2 && value.endsWith("kg")) value.length - 2 else value.length
        val number = value.substring(0, unitAt)
        val unit = value.substring(unitAt)
        val figure = @Composable {
            Text(
                buildAnnotatedString {
                    append(number)
                    if (unit.isNotEmpty()) {
                        withStyle(SpanStyle(fontSize = 9.sp, fontWeight = FontWeight.Normal)) { append(unit) }
                    }
                },
                style = MaterialTheme.typography.labelLarge,
                fontSize = if (number.length >= 5) 12.sp else TextUnit.Unspecified,
                // The theme's label tracking pushed "12.5kg" into the glyphs.
                letterSpacing = if (number.length >= 4) 0.5.sp else TextUnit.Unspecified,
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.Bold,
                color = if (dimmed) IronvellumColors.InkMuted else IronvellumColors.Ink,
                maxLines = 1,
                softWrap = false,
            )
        }
        if (onValueClick != null) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .clickable(onClickLabel = "Type a load", role = Role.Button, onClick = onValueClick)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) { figure() }
        } else {
            figure()
        }
    }
}

/** Half a stepper: the whole 44dp-tall half is the target, the glyph sits at the frame's edge. */
@Composable
private fun StepZone(symbol: String, description: String, onClick: () -> Unit, glyphAt: Alignment, modifier: Modifier) {
    Box(
        modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = InkPressIndication,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = description },
        contentAlignment = glyphAt,
    ) {
        Text(
            symbol,
            style = MaterialTheme.typography.titleMedium,
            fontFamily = ChakraPetch,
            color = IronvellumColors.SystemGreen,
            modifier = Modifier
                .clearAndSetSemantics {}
                .padding(horizontal = 9.dp),
        )
    }
}

/** The drawn stepper frame: what the old 2dp-padded glyph row measured. */
private val STEPPER_FRAME_HEIGHT = 30.dp

/** The stepper's touch height; the set row's 48dp tick box already reserves it. */
private val STEPPER_HIT_HEIGHT = 44.dp


private const val TITLE_CAP = 80

/** Where the one-time trial notification ask is remembered. */
private const val TRIAL_PREFS = "trial"
private const val KEY_ASKED_NOTIFICATIONS = "asked_post_notifications"
private const val PUBLIC_NOTE_CAP = 500


/** One exercise of the live trial: its sets in order, and where the rite put it. */
private class TrialBlock(val id: Long, val position: Int, val sets: List<SessionSet>) {
    val first: SessionSet get() = sets.first()
}

/**
 * What the one Undo bar offers: "Set n logged", "Set removed" or "Back squat
 * removed". [serial] tells two offers apart, so each gets its own timeout.
 */
private sealed interface UndoPrompt {
    val serial: Int
    val message: String

    data class Logged(override val serial: Int, val setId: Long, val setNumber: Int) : UndoPrompt {
        override val message: String get() = "Set $setNumber logged"
    }

    data class Removed(override val serial: Int, override val message: String, val removed: Repository.RemovedSets) : UndoPrompt
}

/** What a stepper moves. LOAD has its own entry dialog; the others share one. */
private enum class FigureKind { LOAD, REPS, SECONDS, MINUTES, KM, ATTEMPTS }

/** A stepper's figure, with the set as it would stand after a − or a +. */
private class Figure(
    val kind: FigureKind,
    val value: String,
    val unit: String,
    /** For the ± announcements: "Decrease load". */
    val what: String,
    val minus: SessionSet,
    val plus: SessionSet,
)

private enum class RowState { DONE, WARMUP, EDITING, ACTIVE, UPCOMING }

/** How long Undo stays up after a set is logged or removed. */
private const val UNDO_MS = 5_000L

/** Four digits is more than any reps, seconds, minutes or attempts figure needs. */
private const val FIGURE_INPUT_MAX = 9_999

/** A stepper's figure: room for "102.5" between its glyphs, and no more. */
private val FIGURE_WIDTH = 56.dp

/** Every footer state is this tall, so the bar never shifts as its slot changes. */
private val FOOTER_HEIGHT = 76.dp

private fun figuresFor(set: SessionSet, metric: ExerciseMetric, weighted: Boolean): List<Figure> {
    fun load(): Figure {
        val kg = set.weightKg?.takeIf { it > 0.0 }
        return Figure(
            kind = FigureKind.LOAD,
            // A bodyweight movement reads BW; a weighted one with no load yet reads 0.
            value = kg?.let { formatLoadKg(it) } ?: if (weighted) "0" else "BW",
            unit = if (kg != null || weighted) "kg" else "",
            what = "load",
            minus = set.copy(weightKg = stepDownKg(set.weightKg)),
            plus = set.copy(weightKg = stepUpKg(set.weightKg)),
        )
    }
    fun minutes(): Figure {
        val minutes = (set.durationSec ?: 0) / 60
        return Figure(
            FigureKind.MINUTES, minutes.toString(), "min", "minutes",
            set.copy(durationSec = (minutes - DURATION_STEP_MINUTES).coerceAtLeast(0) * 60),
            set.copy(durationSec = (minutes + DURATION_STEP_MINUTES) * 60),
        )
    }
    return when (metric) {
        ExerciseMetric.REPS -> listOf(
            load(),
            Figure(
                FigureKind.REPS, set.reps.toString(), "reps", "reps",
                set.copy(reps = (set.reps - 1).coerceAtLeast(0)),
                set.copy(reps = set.reps + 1),
            ),
        )
        // A hold steps in 5s: tapping + fifty-nine times to reach a minute is
        // not an input method. Its figure is seconds, kept in durationSec.
        ExerciseMetric.HOLD -> {
            val seconds = set.durationSec ?: 0
            listOf(
                load(),
                Figure(
                    FigureKind.SECONDS, seconds.toString(), "sec", "seconds",
                    set.copy(durationSec = (seconds - HOLD_STEP_SECONDS).coerceAtLeast(0)),
                    set.copy(durationSec = seconds + HOLD_STEP_SECONDS),
                ),
            )
        }
        // ActivityScore pays a load bonus here, so Weighted Skipping keeps its
        // load; plain Yoga does not.
        ExerciseMetric.DURATION -> listOfNotNull(if (weighted) load() else null, minutes())
        // ActivityScore ignores weight for this metric, so no load sits there dead.
        ExerciseMetric.DISTANCE_TIME -> {
            val km = (set.distanceM ?: 0.0) / 1000.0
            listOf(
                Figure(
                    FigureKind.KM, formatBodyValue(km), "km", "distance",
                    set.copy(distanceM = (km - DISTANCE_STEP_KM).coerceAtLeast(0.0) * 1000.0),
                    set.copy(distanceM = (km + DISTANCE_STEP_KM) * 1000.0),
                ),
                minutes(),
            )
        }
        // The attempt count rides the reps column, as ActivityScore reads it;
        // the grade is typed in its own field beside it.
        ExerciseMetric.ATTEMPTS_GRADE -> listOf(
            Figure(
                FigureKind.ATTEMPTS, set.reps.toString(), "attempts", "attempts",
                set.copy(reps = (set.reps - 1).coerceAtLeast(0)),
                set.copy(reps = set.reps + 1),
            ),
        )
    }
}

private fun SessionSet.typedFigure(kind: FigureKind): Int = when (kind) {
    FigureKind.REPS, FigureKind.ATTEMPTS -> reps
    FigureKind.SECONDS -> durationSec ?: 0
    FigureKind.MINUTES -> (durationSec ?: 0) / 60
    FigureKind.LOAD, FigureKind.KM -> 0
}

private fun SessionSet.withTypedFigure(kind: FigureKind, n: Int): SessionSet = when (kind) {
    FigureKind.REPS, FigureKind.ATTEMPTS -> copy(reps = n)
    FigureKind.SECONDS -> copy(durationSec = n)
    FigureKind.MINUTES -> copy(durationSec = n * 60)
    FigureKind.LOAD, FigureKind.KM -> this
}

private fun FigureKind.typable(): Boolean = this != FigureKind.KM

/** "10 kg", or "BW" for a bodyweight set. */
internal fun kgLabel(kg: Double?): String = kg?.takeIf { it > 0.0 }?.let { "${formatLoadKg(it)} kg" } ?: "BW"

/** A set as its row reads: "10 kg × 6". */
internal fun setFigureText(set: SessionSet, metric: ExerciseMetric, weighted: Boolean): String = when (metric) {
    ExerciseMetric.REPS -> "${kgLabel(set.weightKg)} × ${set.reps}"
    ExerciseMetric.HOLD -> "${kgLabel(set.weightKg)} × ${set.durationSec ?: 0}s"
    ExerciseMetric.DURATION ->
        "${(set.durationSec ?: 0) / 60} min" +
            if (weighted && (set.weightKg ?: 0.0) > 0.0) " · ${kgLabel(set.weightKg)}" else ""
    ExerciseMetric.DISTANCE_TIME ->
        "${formatBodyValue((set.distanceM ?: 0.0) / 1000.0)} km · ${(set.durationSec ?: 0) / 60} min"
    ExerciseMetric.ATTEMPTS_GRADE ->
        "${set.reps} ${plural(set.reps, "attempt", "attempts")}" +
            (set.grade?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "")
}

/** The reference line's figure: "6 × 7.5 kg". */
private fun repsAtLoad(figure: Int, kg: Double?, hold: Boolean): String =
    "$figure${if (hold) "s" else ""} × ${kgLabel(kg)}"

/**
 * "Last 6 × 7.5 kg · Peak 6 × 10 kg", the peak in gold. Last is the top set of
 * the last sealed trial, Peak the best set at any position; both are known
 * before the set is logged, which is when they are useful.
 */
private fun referenceLine(
    exercise: Exercise?,
    metric: ExerciseMetric,
    last: LastLogged?,
    peak: SetRecords.Record?,
): AnnotatedString? {
    val hold = metric == ExerciseMetric.HOLD
    val lastText = last?.let {
        if (metric.isStrength) repsAtLoad(if (hold) it.durationSec ?: it.reps else it.reps, it.weightKg, hold)
        else exercise?.let { e -> lastLoggedFigure(it, e) }
    }
    val peakText = peak?.takeIf { metric.isStrength }?.let { repsAtLoad(it.reps, it.weightKg, hold) }
    if (lastText == null && peakText == null) return null
    return buildAnnotatedString {
        if (lastText != null) append("Last $lastText")
        if (lastText != null && peakText != null) append(" · ")
        if (peakText != null) {
            append("Peak ")
            withStyle(SpanStyle(color = IronvellumColors.SovereignGold)) { append(peakText) }
        }
    }
}

/**
 * The logged sets that beat their position's record and today's earlier sets:
 * a PEAK must also beat this exercise's earlier done sets today, or a repeat
 * of set 2 as set 3 reads NEW PEAK twice. Activity work never scores strength.
 */
private fun newPeakSetIds(
    sets: List<SessionSet>,
    metric: ExerciseMetric,
    records: Map<Pair<String, Int>, SetRecords.Record>,
    bodyweight: Double?,
): Set<Long> {
    val bw = bodyweight?.takeIf { metric.isStrength } ?: return emptySet()
    val hold = metric == ExerciseMetric.HOLD
    fun figure(set: SessionSet) = if (hold) set.durationSec ?: 0 else set.reps
    return sets.filter { it.done }.filter { set ->
        val bestEarlier = sets.filter { it.done && it.setIndex < set.setIndex }.maxOfOrNull { earlier ->
            SetRecords.score(earlier.exerciseName, figure(earlier), earlier.weightKg, bw, hold)
        }
        SetRecords.delta(
            records, set.exerciseName, set.setIndex, figure(set), set.weightKg, bw,
            isHold = hold,
            bestEarlierThisWorkout = bestEarlier,
        ).isRecord
    }.map { it.id }.toSet()
}

/** A folded exercise's second line, by how far along it is. */
private fun foldedSubline(block: TrialBlock, metric: ExerciseMetric, weighted: Boolean): String {
    val done = block.sets.filter { it.done }
    val total = block.sets.count { !it.warmup }
    val first = block.sets.firstOrNull { !it.warmup } ?: block.first
    return when {
        done.size == total -> {
            // Best by load then reps; an activity's best is its longest figure.
            val best = if (metric.isStrength) {
                done.maxWith(compareBy<SessionSet>({ it.weightKg ?: 0.0 }, { if (metric == ExerciseMetric.HOLD) it.durationSec ?: 0 else it.reps }))
            } else {
                done.maxBy { (it.durationSec ?: 0) + it.reps }
            }
            "$total/$total · best ${setFigureText(best, metric, weighted)}"
        }
        done.isNotEmpty() -> "${done.size}/$total ${plural(total, "set", "sets")}"
        else -> when (metric) {
            ExerciseMetric.REPS -> "$total × ${first.reps}" + (first.weightKg?.takeIf { it > 0.0 }?.let { " · ${formatLoadKg(it)} kg" } ?: "")
            ExerciseMetric.HOLD -> "$total × ${first.durationSec ?: 0}s" + (first.weightKg?.takeIf { it > 0.0 }?.let { " · ${formatLoadKg(it)} kg" } ?: "")
            ExerciseMetric.DURATION -> "$total × ${(first.durationSec ?: 0) / 60} min"
            ExerciseMetric.DISTANCE_TIME, ExerciseMetric.ATTEMPTS_GRADE -> "$total ${plural(total, "set", "sets")}"
        }
    }
}

/**
 * Title, the segmented progress and the clock. Leaving is free: the trial
 * stays live, and Today / Train offer Resume. Abandon, and the trial's muscles,
 * wait behind the overflow menu.
 */
@Composable
private fun TrialHeader(
    overline: String?,
    done: Int,
    total: Int,
    minutesLeft: Int?,
    startedAtMs: Long,
    onBack: () -> Unit,
    onMuscles: () -> Unit,
    onAbandon: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (overline != null) {
                    Text(
                        overline,
                        style = MaterialTheme.typography.labelSmall,
                        color = IronvellumColors.InkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    "Trial in progress",
                    style = MaterialTheme.typography.titleMedium,
                    color = IronvellumColors.Ink,
                )
            }
            NavChip(
                "BACK",
                Icons.AutoMirrored.Filled.ArrowBack,
                onClick = onBack,
                modifier = Modifier.semantics { onClick(label = "Leave, the trial keeps running", action = null) },
            )
            var menuOpen by remember { mutableStateOf(false) }
            Box {
                InkIconButton(onClick = { menuOpen = true }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More options", tint = IronvellumColors.InkMuted)
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    shape = MaterialTheme.shapes.small,
                    containerColor = IronvellumColors.VaultHigh,
                ) {
                    DropdownMenuItem(
                        text = { Text("Muscles worked", color = IronvellumColors.Ink) },
                        onClick = {
                            menuOpen = false
                            onMuscles()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Abandon trial", color = IronvellumColors.DangerRed) },
                        onClick = {
                            menuOpen = false
                            onAbandon()
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        // One straight segment per set, a 3dp gap between; logged ones are emerald.
        Row(
            Modifier.fillMaxWidth().height(3.dp).clearAndSetSemantics {},
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            if (total == 0) Box(Modifier.weight(1f).fillMaxHeight().background(IronvellumColors.Rune))
            repeat(total) { index ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(if (index < done) IronvellumColors.Emerald else IronvellumColors.Rune),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$done / $total ${plural(total, "set", "sets")}",
                style = MaterialTheme.typography.labelMedium,
                color = IronvellumColors.InkMuted,
            )
            if (minutesLeft != null) {
                Text(
                    " · ~$minutesLeft min left",
                    style = MaterialTheme.typography.labelMedium,
                    color = IronvellumColors.InkMuted,
                )
            }
            Spacer(Modifier.weight(1f))
            SessionElapsed(startedAtMs)
        }
    }
}

/** The one exercise being worked: its figures, then its sets. The only boxed thing on the screen. */
@Composable
private fun OpenExerciseCard(
    block: TrialBlock,
    metric: ExerciseMetric,
    weighted: Boolean,
    reference: AnnotatedString?,
    reason: String?,
    modifiersEditable: Boolean,
    activeSetId: Long?,
    editingSetId: Long?,
    newPeaks: Set<Long>,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onInfo: () -> Unit,
    onEditModifiers: () -> Unit,
    onMove: (up: Boolean) -> Unit,
    onEditLoad: (() -> Unit)?,
    onAddSet: () -> Unit,
    onRemoveSet: (SessionSet) -> Unit,
    onRemoveExercise: () -> Unit,
    onEdit: (SessionSet) -> Unit,
    onTypeLoad: (SessionSet) -> Unit,
    onTypeFigure: (SessionSet, FigureKind) -> Unit,
    onToggleEdit: (SessionSet) -> Unit,
    onToggleWarmup: (SessionSet) -> Unit,
    onUnlog: (SessionSet) -> Unit,
    modifier: Modifier = Modifier,
) {
    val first = block.first
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(IronvellumColors.Vault)
            .inkBorder(IronvellumColors.Rune, shape, 1.dp)
            .padding(vertical = 8.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            // The name and a small (i) are one 44dp target opening the
            // exercise's info. The glyph sits inline, not in a 48dp
            // IconButton, so the name keeps its width at 360dp.
            val infoLabel = "About ${first.exerciseName}"
            Row(
                Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clickable(onClickLabel = infoLabel, onClick = onInfo)
                    .semantics {
                        contentDescription = infoLabel
                        role = Role.Button
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    first.exerciseName,
                    style = MaterialTheme.typography.titleMedium,
                    color = IronvellumColors.Ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = null,
                    tint = IronvellumColors.InkMuted,
                    modifier = Modifier.padding(start = 4.dp).size(18.dp),
                )
            }
            ExerciseMenu(
                name = first.exerciseName,
                onMoveUp = if (canMoveUp) ({ onMove(true) }) else null,
                onMoveDown = if (canMoveDown) ({ onMove(false) }) else null,
                onModifiers = if (modifiersEditable) onEditModifiers else null,
                onEditLoad = onEditLoad,
                onAddSet = onAddSet,
                // Only a set not yet logged: removing a logged one loses its log.
                onRemoveLastSet = block.sets.last().takeIf { block.sets.size > 1 && !it.done }?.let { last -> { onRemoveSet(last) } },
                onRemoveExercise = onRemoveExercise,
            )
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            if (reference != null) {
                Text(
                    reference,
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                )
            }
            // Only real modifiers get a line.
            if (first.modifiers.isNotBlank()) {
                Text(
                    first.modifiers.split(",").joinToString(" · ") { it.trim() },
                    style = MaterialTheme.typography.labelSmall,
                    color = IronvellumColors.SystemGreen,
                    modifier = Modifier
                        .clickable(enabled = modifiersEditable, onClick = onEditModifiers)
                        .padding(vertical = 2.dp),
                )
            }
            // Why the load moved, so a deload or a step up is never a
            // surprise: only where the engine changed the prescription.
            if (reason != null) {
                Text(
                    reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        block.sets.forEach { set ->
            key(set.id) {
                val state = when {
                    (set.done || set.warmup) && set.id == editingSetId -> RowState.EDITING
                    set.warmup -> RowState.WARMUP
                    set.done -> RowState.DONE
                    set.id == activeSetId -> RowState.ACTIVE
                    else -> RowState.UPCOMING
                }
                TrialSetRow(
                    set = set,
                    metric = metric,
                    weighted = weighted,
                    state = state,
                    // Warm-ups read W and the working sets count from 1 after them, as in the amend editor.
                    label = if (set.warmup) "W" else "${set.workingNumber(block.sets)}",
                    newPeak = set.id in newPeaks,
                    onEdit = onEdit,
                    onTypeLoad = { onTypeLoad(set) },
                    onTypeFigure = { kind -> onTypeFigure(set, kind) },
                    onToggleEdit = { onToggleEdit(set) },
                    onToggleWarmup = { onToggleWarmup(set) },
                    onUnlog = { onUnlog(set) },
                    onRemove = { onRemoveSet(set) },
                )
            }
        }
    }
}

/** Reorder, tune, add and remove: the exercise's own actions, behind one small menu. */
@Composable
private fun ExerciseMenu(
    name: String,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    onModifiers: (() -> Unit)?,
    onEditLoad: (() -> Unit)?,
    onAddSet: () -> Unit,
    onRemoveLastSet: (() -> Unit)?,
    onRemoveExercise: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        InkIconButton(onClick = { open = true }, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Filled.MoreVert, contentDescription = "More for $name", tint = IronvellumColors.InkMuted)
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = MaterialTheme.shapes.small,
            containerColor = IronvellumColors.VaultHigh,
        ) {
            @Composable
            fun item(label: String, color: Color = IronvellumColors.Ink, action: () -> Unit) {
                DropdownMenuItem(
                    text = { Text(label, color = color) },
                    onClick = {
                        open = false
                        action()
                    },
                )
            }
            item("Add set", action = onAddSet)
            onEditLoad?.let { item("Edit load", action = it) }
            onModifiers?.let { item("Edit modifiers", action = it) }
            onMoveUp?.let { item("Move up", action = it) }
            onMoveDown?.let { item("Move down", action = it) }
            onRemoveLastSet?.let { item("Remove last set", action = it) }
            item("Remove exercise", IronvellumColors.DangerRed, onRemoveExercise)
        }
    }
}

/**
 * One set of the open card. A logged set is a muted line (tap it to edit or
 * un-log); the next set is the active row, with its steppers on one line and a
 * thin emerald bar; the rest wait, dimmer. Nothing here is boxed.
 */
@Composable
private fun TrialSetRow(
    set: SessionSet,
    metric: ExerciseMetric,
    weighted: Boolean,
    state: RowState,
    label: String,
    newPeak: Boolean,
    onEdit: (SessionSet) -> Unit,
    onTypeLoad: () -> Unit,
    onTypeFigure: (FigureKind) -> Unit,
    onToggleEdit: () -> Unit,
    onToggleWarmup: () -> Unit,
    onUnlog: () -> Unit,
    onRemove: () -> Unit,
) {
    if (state == RowState.ACTIVE || state == RowState.EDITING) {
        val figures = figuresFor(set, metric, weighted)
        Column(
            Modifier
                .fillMaxWidth()
                .background(IronvellumColors.Ink.copy(alpha = 0.04f))
                .drawBehind { drawRect(IronvellumColors.Emerald, size = Size(2.dp.toPx(), size.height)) }
                .padding(vertical = 4.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = ChakraPetch,
                    color = IronvellumColors.Ink,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(22.dp),
                )
                figures.forEach { figure ->
                    FigureStepper(
                        figure = figure,
                        onEdit = onEdit,
                        onType = when {
                            figure.kind == FigureKind.LOAD -> onTypeLoad
                            figure.kind.typable() -> ({ onTypeFigure(figure.kind) })
                            else -> null
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (metric == ExerciseMetric.ATTEMPTS_GRADE) {
                    GradeField(
                        value = set.grade.orEmpty(),
                        // Capped at the server's ceiling here so a long entry
                        // cannot be typed at all, not truncated after the fact.
                        onChange = { onEdit(set.copy(grade = it.take(WireLimits.GRADE_MAX))) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (state == RowState.EDITING) {
                Row(Modifier.fillMaxWidth().padding(start = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                    // A warm-up was never logged, so there is nothing to un-log.
                    if (!set.warmup) RowAction("Un-log", IronvellumColors.Ink, onUnlog)
                    RowAction("Remove", IronvellumColors.DangerRed, onRemove)
                    Spacer(Modifier.weight(1f))
                    RowAction("Close", IronvellumColors.InkMuted, onToggleEdit)
                }
                Row(Modifier.fillMaxWidth().padding(start = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                    RowAction(if (set.warmup) "Count as a working set" else "Mark as warm-up", IronvellumColors.Ink, onToggleWarmup)
                }
            }
        }
        return
    }
    val done = state == RowState.DONE
    // A warm-up reads as a muted line like a logged set, and opens the same edit row.
    val editable = done || state == RowState.WARMUP
    val tone = if (editable) IronvellumColors.InkMuted else IronvellumColors.InkMuted.copy(alpha = 0.7f)
    Row(
        Modifier
            .fillMaxWidth()
            .then(
                if (editable) {
                    Modifier.clickable(onClickLabel = if (set.warmup) "Edit warm-up" else "Edit set $label", onClick = onToggleEdit)
                } else {
                    Modifier
                },
            )
            .heightIn(min = if (editable) 48.dp else 44.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (done) "✓" else "",
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = ChakraPetch,
            color = tone,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(18.dp),
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = ChakraPetch,
            color = tone,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(start = 6.dp).width(22.dp),
        )
        Text(
            setFigureText(set, metric, weighted),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = tone,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 8.dp),
        )
        if (newPeak) {
            // A tag, not a control: small gold text, never boxed, never tappable.
            Icon(
                Icons.Filled.Bolt,
                contentDescription = null,
                tint = IronvellumColors.SovereignGold,
                modifier = Modifier.size(11.dp),
            )
            Spacer(Modifier.width(3.dp))
            Text(
                "NEW PEAK",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.SemiBold,
                color = IronvellumColors.SovereignGold,
            )
        }
    }
}

/** − number +, plain glyphs on 44dp targets; tapping the number types it. */
@Composable
private fun FigureStepper(
    figure: Figure,
    onEdit: (SessionSet) -> Unit,
    onType: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    // The glyphs hug the figure, and the group sits centred in its half: spread
    // to the edges, one figure's + read as the next figure's −.
    Row(modifier, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        StepButton("−", "Decrease ${figure.what}") { onEdit(figure.minus) }
        Column(
            Modifier
                .widthIn(min = FIGURE_WIDTH)
                .heightIn(min = 48.dp)
                .then(
                    if (onType != null) {
                        Modifier.clickable(onClickLabel = "Type ${figure.what}", role = Role.Button, onClick = onType)
                    } else {
                        Modifier
                    },
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                figure.value,
                style = MaterialTheme.typography.titleLarge,
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.Bold,
                // A five-character figure ("102.5") steps down so it fits between the glyphs.
                fontSize = if (figure.value.length >= 5) 18.sp else 22.sp,
                color = IronvellumColors.Ink,
                maxLines = 1,
                softWrap = false,
            )
            if (figure.unit.isNotEmpty()) {
                Text(figure.unit, style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = IronvellumColors.InkMuted)
            }
        }
        StepButton("+", "Increase ${figure.what}") { onEdit(figure.plus) }
    }
}

@Composable
private fun StepButton(symbol: String, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(width = 44.dp, height = 48.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = InkPressIndication,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            symbol,
            style = MaterialTheme.typography.titleLarge,
            fontFamily = ChakraPetch,
            color = IronvellumColors.InkMuted,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

/** A bouldering grade is typed text, not a count; it sits on a single rule, not in a box. */
@Composable
private fun GradeField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 8.dp)) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium.copy(
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.Bold,
                color = IronvellumColors.Ink,
            ),
            cursorBrush = SolidColor(IronvellumColors.Ink),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Grade" },
            decorationBox = { field ->
                Box(Modifier.heightIn(min = 46.dp), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text("V5", style = MaterialTheme.typography.labelSmall, color = IronvellumColors.InkMuted)
                    }
                    field()
                }
            },
        )
        Box(Modifier.fillMaxWidth().height(1.dp).background(IronvellumColors.Rune))
    }
}

@Composable
private fun RowAction(label: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = color)
    }
}

/**
 * An exercise that is not open, on one line: how far along it is says it all.
 * Not started shows its prescription, partly done "1/3 sets" and a tiny bar,
 * complete a small emerald tick and its best set. Tapping opens it.
 */
@Composable
private fun FoldedExerciseRow(
    name: String,
    subline: String,
    done: Int,
    total: Int,
    onOpen: () -> Unit,
) {
    val complete = total > 0 && done == total
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clickable(onClickLabel = "Open $name", onClick = onOpen)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(28.dp)) {
            if (complete) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = IronvellumColors.Emerald,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(
                name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (complete) IronvellumColors.InkMuted else IronvellumColors.Ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subline,
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (done in 1 until total) {
            Row(Modifier.padding(end = 12.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(total.coerceAtMost(MINI_BAR_SEGMENTS)) { index ->
                    Box(
                        Modifier
                            .size(width = 10.dp, height = 3.dp)
                            .background(if (index < done) IronvellumColors.Emerald else IronvellumColors.Rune),
                    )
                }
            }
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = IronvellumColors.InkMuted,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** A folded exercise's bar stops growing here; a long block just reads as a long bar. */
private const val MINI_BAR_SEGMENTS = 8

/** A straight 1dp rule between folded rows, inset to the text. */
@Composable
private fun FolderRule() {
    Box(Modifier.fillMaxWidth().padding(start = 32.dp).height(1.dp).background(IronvellumColors.Rune))
}

/**
 * The ways to add to the trial: another exercise, a name, a note. Rows, not
 * fields: each opens its own entry, and shows what is set.
 */
@Composable
private fun TrialEndRows(
    nameValue: String,
    nameSet: Boolean,
    noteValue: String?,
    onAddExercise: () -> Unit,
    onName: () -> Unit,
    onNote: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        FolderRuleFull()
        EndRow(Icons.Filled.Add, "Add exercise", null, false, onAddExercise)
        FolderRuleFull()
        EndRow(Icons.Outlined.Edit, "Name this trial", nameValue, nameSet, onName)
        FolderRuleFull()
        EndRow(Icons.Outlined.EditNote, "Add a note", noteValue ?: "Public or private", noteValue != null, onNote)
        FolderRuleFull()
    }
}

@Composable
private fun FolderRuleFull() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(IronvellumColors.Rune))
}

@Composable
private fun EndRow(icon: ImageVector, label: String, value: String?, valueSet: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = IronvellumColors.InkMuted, modifier = Modifier.size(18.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.Ink)
        if (value != null) {
            Text(
                value,
                style = MaterialTheme.typography.bodySmall,
                color = if (valueSet) IronvellumColors.Ink else IronvellumColors.InkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * The docked bar, on raised paper with a top rule. One slot, three states, all
 * the same height so nothing moves when it changes: the rest while one runs,
 * the next set when ready, slide to seal once every set is logged.
 */
@Composable
private fun TrialFooter(
    allLogged: Boolean,
    rest: RestTimer?,
    nextSetNumber: Int?,
    nextLine: String?,
    onLog: () -> Unit,
    onExtend: () -> Unit,
    onShorten: () -> Unit,
    onSkip: () -> Unit,
    onSealed: () -> Unit,
    slideReset: Int,
) {
    val now = restNow(rest)
    Box(
        Modifier
            .fillMaxWidth()
            .height(FOOTER_HEIGHT)
            .background(IronvellumColors.VaultHigh)
            .drawBehind { drawRect(IronvellumColors.Rune, size = Size(size.width, 1.dp.toPx())) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            allLogged -> SlideToSeal(label = "Slide to seal", onSealed = onSealed, resetKey = slideReset)
            rest != null && !rest.isOver(now) -> RestStrip(rest, now, nextSetNumber, onExtend, onShorten, onSkip)
            nextLine != null && nextSetNumber != null -> NextSetStrip(nextSetNumber, nextLine, onLog)
            // No sets at all: an open trial that has not begun. Nothing to log or seal yet.
            else -> SlideToSeal(label = "Log a set to seal the trial", onSealed = onSealed, enabled = false)
        }
    }
}

/** The rest between sets, counting down, with its moves: a little less, a little more, or none at all. */
@Composable
private fun RestStrip(timer: RestTimer, now: Long, nextSetNumber: Int?, onExtend: () -> Unit, onShorten: () -> Unit, onSkip: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(
                timer.label(now),
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                color = IronvellumColors.Ink,
                maxLines = 1,
            )
            Text(
                if (nextSetNumber != null) "REST · SET $nextSetNumber NEXT" else "REST",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = ChakraPetch,
                color = IronvellumColors.InkMuted,
                letterSpacing = IronvellumTracking.InlineLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 12.dp),
            )
            FooterAction("\u2212${RestTimer.SHORTEN_SECONDS}", IronvellumColors.Ink, onShorten)
            FooterAction("+${RestTimer.EXTEND_SECONDS}", IronvellumColors.Ink, onExtend)
            FooterAction("Skip", IronvellumColors.InkMuted, onSkip)
        }
        // The sweep: what is left of the rest.
        val left = (timer.remainingMs(now).toFloat() / timer.totalMs.coerceAtLeast(1L)).coerceIn(0f, 1f)
        Box(Modifier.fillMaxWidth().height(3.dp).background(IronvellumColors.Rune)) {
            Box(Modifier.fillMaxWidth(left).fillMaxHeight().background(IronvellumColors.InkMuted))
        }
    }
}

@Composable
private fun FooterAction(label: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .defaultMinSize(minWidth = 48.dp)
            .fillMaxHeight()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = color)
    }
}

/** What is next, and the one emerald control: tick the set. Reads as a row you tick, not a slab. */
@Composable
private fun NextSetStrip(setNumber: Int, line: String, onLog: () -> Unit) {
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                "NEXT",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = ChakraPetch,
                color = IronvellumColors.InkMuted,
                letterSpacing = IronvellumTracking.InlineLabel,
            )
            Text(
                line,
                style = MaterialTheme.typography.bodyLarge,
                color = IronvellumColors.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            Modifier
                .size(52.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(IronvellumColors.Emerald)
                .clickable(role = Role.Button, onClick = onLog)
                .semantics { contentDescription = "Log set $setNumber" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(26.dp))
        }
    }
}

/** The clock of the rest in the footer: ticks while one runs, and only then. */
@Composable
private fun restNow(timer: RestTimer?): Long {
    var now by remember(timer) { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(timer) {
        while (timer != null && !timer.isOver(now)) {
            delay(250)
            now = SystemClock.elapsedRealtime()
        }
    }
    return now
}

/** Typed reps, seconds, minutes or attempts: the stepper's number, exactly. */
@Composable
private fun FigureEntryDialog(
    set: SessionSet,
    number: Int,
    kind: FigureKind,
    onApply: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val initial = set.typedFigure(kind).toString()
    var field by remember(set.id, kind) { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    val parsed = field.text.toIntOrNull()?.takeIf { it in 0..FIGURE_INPUT_MAX }
    val focus = remember { FocusRequester() }
    LaunchedEffect(set.id, kind) { focus.requestFocus() }
    IronvellumDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    set.exerciseName,
                    style = MaterialTheme.typography.titleMedium,
                    color = IronvellumColors.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "Set $number",
                    style = MaterialTheme.typography.labelSmall,
                    color = IronvellumColors.InkMuted,
                )
            }
        },
        text = {
            OutlinedTextField(
                shape = MaterialTheme.shapes.small,
                value = field,
                onValueChange = { field = it.copy(text = it.text.filter(Char::isDigit).take(4)) },
                singleLine = true,
                label = {
                    Text(
                        when (kind) {
                            FigureKind.SECONDS -> "Seconds"
                            FigureKind.MINUTES -> "Minutes"
                            FigureKind.ATTEMPTS -> "Attempts"
                            else -> "Reps"
                        },
                    )
                },
                isError = parsed == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = fieldColors(accent = IronvellumColors.SystemGreen),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = {
            IronvellumButton("Save", enabled = parsed != null, onClick = { parsed?.let(onApply) })
        },
    )
}

/**
 * Names this trial, never the rite it came from. Shown in Tidings once sealed,
 * so a blank name falls back to the rite's own. Saved on Save, trimmed.
 */
@Composable
private fun TrialNameDialog(
    current: String,
    signedIn: Boolean,
    hint: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by rememberSaveable { mutableStateOf(current) }
    IronvellumDialog(
        onDismissRequest = onDismiss,
        title = { Text("Name this trial") },
        text = {
            OutlinedTextField(
                shape = MaterialTheme.shapes.small,
                value = draft,
                onValueChange = { draft = it.take(TITLE_CAP) },
                singleLine = true,
                label = {
                    FieldLabel(
                        icon = { Icon(Icons.Outlined.Public, null, Modifier.size(14.dp), tint = IronvellumColors.InkMuted) },
                        text = if (signedIn) "Name · optional, shown in Tidings" else "Name · optional",
                        color = IronvellumColors.InkMuted,
                    )
                },
                placeholder = { Text(hint, style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted) },
                trailingIcon = { CharCounter(draft.length, TITLE_CAP, IronvellumColors.SystemGreen) },
                colors = fieldColors(accent = IronvellumColors.SystemGreen, unfocusedBorder = IronvellumColors.Rune),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { IronvellumButton("Save", onClick = { onSave(draft.trim()) }) },
        dismissButton = { IronvellumButton("Cancel", quiet = true, onClick = onDismiss) },
    )
}

/**
 * The public note, which every Ironbound in Tidings can read, and the private
 * one, which never leaves the device. The private note is saved as typed.
 */
@Composable
private fun TrialNoteDialog(
    publicNote: String,
    privateNote: String,
    signedIn: Boolean,
    onSave: (publicNote: String, privateNote: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var pub by rememberSaveable { mutableStateOf(publicNote) }
    var priv by rememberSaveable { mutableStateOf(privateNote) }
    IronvellumDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a note") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    shape = MaterialTheme.shapes.small,
                    value = pub,
                    onValueChange = { pub = it.take(PUBLIC_NOTE_CAP) },
                    minLines = 2,
                    label = {
                        FieldLabel(
                            icon = { Icon(Icons.Outlined.Public, null, Modifier.size(14.dp), tint = IronvellumColors.SystemGreen) },
                            text = if (signedIn) "Public note · every Ironbound in Tidings can read this" else "Note · shared with allies once you sign in",
                            color = IronvellumColors.SystemGreen,
                        )
                    },
                    placeholder = { Text("How did the trial go? Share it…", style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted) },
                    trailingIcon = { CharCounter(pub.length, PUBLIC_NOTE_CAP, IronvellumColors.SystemGreen) },
                    colors = fieldColors(accent = IronvellumColors.SystemGreen, unfocusedBorder = IronvellumColors.Rune),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    shape = MaterialTheme.shapes.small,
                    value = priv,
                    onValueChange = { priv = it.take(WireLimits.PRIVATE_NOTE_MAX) },
                    minLines = 2,
                    label = {
                        FieldLabel(
                            icon = { Icon(Icons.Outlined.Lock, null, Modifier.size(14.dp), tint = IronvellumColors.InkMuted) },
                            text = "Private note · never leaves this device",
                            color = IronvellumColors.InkMuted,
                        )
                    },
                    placeholder = { Text("For your eyes only…", style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted) },
                    colors = fieldColors(accent = IronvellumColors.InkMuted, focusedBorder = IronvellumColors.SystemGreen, unfocusedBorder = IronvellumColors.Rune),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { IronvellumButton("Save", onClick = { onSave(pub.trim(), priv) }) },
        dismissButton = { IronvellumButton("Cancel", quiet = true, onClick = onDismiss) },
    )
}


@Composable
private fun FieldLabel(
    icon: @Composable () -> Unit,
    text: String,
    color: Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        icon()
        Text(text, style = MaterialTheme.typography.labelSmall, fontFamily = ChakraPetch, color = color)
    }
}

/** Counter only appears as the cap nears — no noise while there's room. */
@Composable
private fun CharCounter(length: Int, cap: Int, accent: Color) {
    if (length <= cap * 4 / 5) return
    val near = length >= cap * 9 / 10
    Text(
        "${cap - length}",
        style = MaterialTheme.typography.labelSmall,
        fontFamily = ChakraPetch,
        color = if (near) IronvellumColors.DangerRed else accent,
    )
}
@Composable
internal fun fieldColors(
    accent: Color,
    focusedBorder: Color = accent,
    unfocusedBorder: Color = accent.copy(alpha = 0.4f),
) = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = focusedBorder,
    unfocusedBorderColor = unfocusedBorder,
    focusedLabelColor = accent,
    unfocusedLabelColor = IronvellumColors.InkMuted,
    cursorColor = accent,
    focusedTextColor = IronvellumColors.Ink,
    unfocusedTextColor = IronvellumColors.Ink,
)

/**
 * The live workout clock. Read from [startedAtMs] every tick, never counted
 * locally, so leaving and returning shows the true figure; ticks only while
 * the screen is started, and is its own composable so the tick recomposes
 * this text alone, not the whole session.
 */
@Composable
private fun SessionElapsed(startedAtMs: Long) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val now by produceState(System.currentTimeMillis(), startedAtMs, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                value = System.currentTimeMillis()
                // Land on the next whole second of elapsed time.
                delay(1_000L - (value - startedAtMs).mod(1_000L))
            }
        }
    }
    Text(
        SessionClock.elapsedLabel(now - startedAtMs),
        style = MaterialTheme.typography.titleSmall,
        fontFamily = ChakraPetch,
        fontWeight = FontWeight.Bold,
        color = IronvellumColors.Ink,
        maxLines = 1,
    )
}

/** Canonical modifier vocabulary — free text drifted ("defecit" vs "deficit"). */
/** A hold steps in fives; a minute is twelve taps, not sixty. */
private const val HOLD_STEP_SECONDS = 5

/** Duration work steps in 5-minute bites — tapping to a Boxing round one
 *  minute at a time is the same trap the hold step fixed. */
private const val DURATION_STEP_MINUTES = 5

/** Distance steps in half-kilometres: coarse enough to be usable, fine
 *  enough to record a real run. */
private const val DISTANCE_STEP_KM = 0.5

/** Starting seconds for a hold added mid-session. */
private const val DEFAULT_HOLD_SECONDS = 30

/** A comma-separated modifier string as its tokens, in order. */
private fun modifierTokens(modifiers: String?): Set<String> =
    modifiers.orEmpty().split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()

/**
 * Whether the picker has anything to show: a modifier that fits, or one
 * already set that the lifter can remove. "weighted" alone is not enough -
 * the load sets it, the picker only notes it.
 */
private fun canEditModifiers(applicable: List<String>, modifiers: String?): Boolean =
    applicable.isNotEmpty() || modifierTokens(modifiers).any { !it.equals(WEIGHTED_MODIFIER, ignoreCase = true) }

@Composable
private fun ModifierPickerDialog(
    exerciseName: String,
    applicable: List<String>,
    selected: Set<String>,
    onConfirm: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var picked by remember(exerciseName) { mutableStateOf(selected) }
    // Every token already set stays visible and removable, fitting or not:
    // a legacy "banded" or "hold seconds" is never dropped behind his back.
    val weighted = selected.any { it.equals(WEIGHTED_MODIFIER, ignoreCase = true) }
    val options = applicable + selected.filter { token ->
        !token.equals(WEIGHTED_MODIFIER, ignoreCase = true) && applicable.none { it.equals(token, ignoreCase = true) }
    }
    IronvellumDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    "MODIFIERS",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = ChakraPetch,
                    color = IronvellumColors.InkMuted,
                    letterSpacing = 2.sp,
                )
                Text(exerciseName, style = MaterialTheme.typography.titleMedium, color = IronvellumColors.Ink)
            }
        },
        text = {
            Column {
                Text(
                    "Applies to every set of this exercise.",
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                )
                if (weighted) {
                    Text(
                        "weighted · set by load",
                        style = MaterialTheme.typography.labelSmall,
                        color = IronvellumColors.SystemGreen,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
                options.chunked(3).forEach { row ->
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        row.forEach { option ->
                            val on = picked.any { it.equals(option, ignoreCase = true) }
                            val shape = MaterialTheme.shapes.extraSmall
                            Box(
                                Modifier
                                    .weight(1f)
                                    .background(
                                        if (on) {
                                            Brush.verticalGradient(listOf(androidx.compose.ui.graphics.lerp(IronvellumColors.Emerald, IronvellumColors.Vault, 0.6f), androidx.compose.ui.graphics.lerp(IronvellumColors.Emerald, IronvellumColors.Vault, 0.8f)))
                                        } else {
                                            Brush.verticalGradient(listOf(Color(0xFF151C19), Color(0xFF0F1412)))
                                        },
                                        shape,
                                    )
                                    .inkBorder(if (on) IronvellumColors.SystemGreen else IronvellumColors.Rune, shape, 1.dp)
                                    .clickable {
                                        picked = if (on) {
                                            picked.filterNot { it.equals(option, ignoreCase = true) }.toSet()
                                        } else {
                                            picked + option
                                        }
                                    }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                // Centred and never wrapped: "hold seconds" wrapped,
                                // so it measured the full cell and sat start-aligned.
                                // At 360dp or a large font it shrinks to fit.
                                Text(
                                    option,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = ChakraPetch,
                                    color = if (on) IronvellumColors.Ink else IronvellumColors.InkMuted,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    autoSize = TextAutoSize.StepBased(
                                        minFontSize = 8.sp,
                                        maxFontSize = MaterialTheme.typography.labelSmall.fontSize,
                                    ),
                                )
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        },
        confirmButton = { IronvellumButton("Save", onClick = { onConfirm(picked) }) },
        dismissButton = { IronvellumButton("Cancel", quiet = true, onClick = onDismiss) },
    )
}

/** A field's label and its "before → after" figures, as the update dialog lists them. */
private fun fieldLine(change: RoutineUpdate.Change, field: RoutineUpdate.Field): Pair<String, String> {
    val (before, after) = change
    val unit = if (change.isHold) "s" else ""
    return when (field) {
        RoutineUpdate.Field.SETS -> "Sets" to "${before.targetSets} → ${after.targetSets}"
        RoutineUpdate.Field.REPS ->
            (if (change.isHold) "Hold" else "Reps") to "${before.targetReps}$unit → ${after.targetReps}$unit"
        RoutineUpdate.Field.LOAD -> "Load" to "${formatKg(before.targetWeightKg)} → ${formatKg(after.targetWeightKg)}"
        RoutineUpdate.Field.MODIFIERS ->
            "Modifiers" to "${before.modifiers.ifBlank { "None" }} → ${after.modifiers.ifBlank { "None" }}"
    }
}

/**
 * Offers to bring the preset in line with the session just finished, one
 * toggle per changed field so the lifter can take the reps and leave the
 * load. Fields start ticked per [RoutineUpdate.Change.defaultTicks]: a set
 * count lowered by deleted sets is offered unticked; unticked sets never
 * lower it. The session is already completed and paid when this shows;
 * dismissing it keeps the plan.
 */
@Composable
private fun RoutineUpdateDialog(
    offer: Repository.RoutineUpdateOffer,
    shortDay: Boolean,
    onUpdate: (List<RoutineUpdate.Change>) -> Unit,
    onKeep: () -> Unit,
) {
    var ticked by remember(offer) {
        mutableStateOf(offer.changes.associateWith { it.defaultTicks() })
    }
    IronvellumDialog(
        onDismissRequest = onKeep,
        title = {
            Column {
                Text(
                    offer.presetName,
                    style = MaterialTheme.typography.titleMedium,
                    color = IronvellumColors.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (shortDay) {
                        "Unticked sets don't lower the set count. Tick the changes to keep."
                    } else {
                        "Bring your rite in line with today's trial?"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                )
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                offer.changes.forEach { change ->
                    Text(
                        change.before.exerciseName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = IronvellumColors.Ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
                    )
                    change.fields.forEach { field ->
                        val accepted = ticked[change].orEmpty()
                        val on = field in accepted
                        val (label, figures) = fieldLine(change, field)
                        val shape = MaterialTheme.shapes.extraSmall
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 4.dp)
                                .background(
                                    if (on) {
                                        Brush.verticalGradient(listOf(androidx.compose.ui.graphics.lerp(IronvellumColors.Emerald, IronvellumColors.Vault, 0.6f), androidx.compose.ui.graphics.lerp(IronvellumColors.Emerald, IronvellumColors.Vault, 0.8f)))
                                    } else {
                                        Brush.verticalGradient(listOf(Color(0xFF151C19), Color(0xFF0F1412)))
                                    },
                                    shape,
                                )
                                .inkBorder(if (on) IronvellumColors.SystemGreen else IronvellumColors.Rune, shape, 1.dp)
                                .toggleable(value = on, role = Role.Checkbox) {
                                    ticked = ticked + (change to if (on) accepted - field else accepted + field)
                                }
                                .heightIn(min = 44.dp)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // The set rows' own tick box: an empty slot read as
                            // a label, not something to tick.
                            Box(
                                Modifier
                                    .size(22.dp)
                                    .background(
                                        if (on) IronvellumColors.SystemGreen.copy(alpha = 0.18f) else Color.Transparent,
                                        MaterialTheme.shapes.extraSmall,
                                    )
                                    .inkBorder(
                                        if (on) IronvellumColors.SystemGreen else IronvellumColors.Bracket,
                                        MaterialTheme.shapes.extraSmall,
                                        1.5.dp,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (on) {
                                    Text(
                                        "\u2713",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontFamily = ChakraPetch,
                                        color = IronvellumColors.SystemGreen,
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                label.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = ChakraPetch,
                                letterSpacing = 1.sp,
                                color = if (on) IronvellumColors.Ink else IronvellumColors.InkMuted,
                                modifier = Modifier.width(76.dp),
                            )
                            Text(
                                figures,
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = ChakraPetch,
                                color = if (on) IronvellumColors.Ink else IronvellumColors.InkMuted,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        },
        // Stacked full width on purpose: side by side the two labels wrapped
        // into a ragged pair at 360dp.
        confirmButton = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Preset order, whatever order the toggles were flipped in.
                val narrowed = offer.changes.mapNotNull { it.only(ticked[it].orEmpty()) }
                IronvellumButton(
                    "Update rite",
                    enabled = narrowed.isNotEmpty(),
                    onClick = { onUpdate(narrowed) },
                    modifier = Modifier.fillMaxWidth(),
                )
                IronvellumButton("Keep rite", quiet = true, onClick = onKeep, modifier = Modifier.fillMaxWidth())
            }
        },
    )
}
