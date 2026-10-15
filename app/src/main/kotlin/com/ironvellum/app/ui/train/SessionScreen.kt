package com.ironvellum.app.ui.train

import com.ironvellum.app.data.Words
import com.ironvellum.app.domain.GearRequirements
import com.ironvellum.app.domain.MovementDifficulty
import com.ironvellum.app.domain.Progression
import com.ironvellum.app.domain.LoadKit
import com.ironvellum.app.domain.Loads
import com.ironvellum.app.domain.LoadUnit
import com.ironvellum.app.data.Units
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.text.SpanStyle
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.width
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
import com.ironvellum.app.ui.theme.InkPressIndication
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
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
import com.ironvellum.app.data.FirstRunPrefs
import com.ironvellum.app.data.LiveTrialActions
import com.ironvellum.app.data.Notifications
import com.ironvellum.app.data.TrialPrefs
import com.ironvellum.app.domain.FirstRun
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
import com.ironvellum.app.domain.RestAlert
import com.ironvellum.app.domain.SetClock
import com.ironvellum.app.domain.LiftLoading
import com.ironvellum.app.domain.SchemeText
import com.ironvellum.app.domain.SchemeType
import com.ironvellum.app.domain.SetScheme
import com.ironvellum.app.domain.TopMark
import com.ironvellum.app.domain.TrialHints
import android.widget.Toast
import com.ironvellum.app.domain.SetKind
import com.ironvellum.app.domain.Effort
import com.ironvellum.app.ui.components.animatorsOn
import com.ironvellum.app.domain.EffortScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import com.ironvellum.app.domain.Supersets
import com.ironvellum.app.data.EffortSetting
import com.ironvellum.app.domain.WarmupRamp
import com.ironvellum.app.domain.Exercise
import com.ironvellum.app.domain.ExerciseMetric
import com.ironvellum.app.domain.LastLogged
import com.ironvellum.app.domain.isStrength
import com.ironvellum.app.domain.RiteSwap
import com.ironvellum.app.domain.RoutineUpdate
import com.ironvellum.app.domain.WarmupStep
import com.ironvellum.app.data.WarmupStore
import com.ironvellum.app.domain.SealedReopen
import com.ironvellum.app.domain.SessionSet
import com.ironvellum.app.domain.RepBook
import com.ironvellum.app.domain.RepMax
import com.ironvellum.app.domain.SessionPeaks
import com.ironvellum.app.domain.SessionXp
import com.ironvellum.app.domain.SetRecords
import com.ironvellum.app.domain.Sex
import com.ironvellum.app.domain.WornLoad
import com.ironvellum.app.domain.WorkoutSession
import com.ironvellum.app.domain.WorkoutShare
import com.ironvellum.app.domain.workingNumber
import com.ironvellum.app.ui.components.ShareCardDialog
import com.ironvellum.app.ui.components.ExerciseInfoSheet
import com.ironvellum.app.ui.components.ExercisePickerSheet
import com.ironvellum.app.ui.components.lastLoggedLine
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.components.InkChip
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
import kotlinx.coroutines.flow.distinctUntilChanged
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
import com.ironvellum.app.domain.VolumeLevel
import com.ironvellum.app.domain.SessionClock
import com.ironvellum.app.domain.TrainingFocus
import com.ironvellum.app.domain.WEIGHTED_MODIFIER
import com.ironvellum.app.domain.Variation
import com.ironvellum.app.domain.modifierChips
import com.ironvellum.app.domain.modifierTokens
import com.ironvellum.app.domain.toggleModifier
import com.ironvellum.app.ui.components.ModifierChipGrid
import com.ironvellum.app.domain.applicableModifiers
import com.ironvellum.app.domain.fmt
import com.ironvellum.app.domain.DecimalInput
import com.ironvellum.app.domain.EXERCISE_NOTE_MAX
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

    /** What the lifter had lifted before this trial: the one [RepMax] peak rule's benchmark, for the badges and the victory panel. */
    val peakBook: StateFlow<RepBook> = combine(repo.observeHistory(), repo.observeExercises()) { history, catalogue ->
        val metrics = catalogue.associate { it.id to it.metric }
        RepMax.book(history, excludeSessionId = sessionId) { metrics[it.exerciseId] }
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /**
     * The live tally's pricer: this trial judged against history as it stands before it,
     * the same [SessionXp] the seal prices with, so the running total is what the seal awards.
     */
    val xpBook: StateFlow<SessionXp?> = combine(
        repo.observeHistory(),
        repo.observeStats().map { SetRecords.bodyweightLookup(it) },
        repo.observeExercises(),
        repo.observeSession(sessionId).map { it?.startedAtMs },
    ) { history, bodyweightAt, catalogue, startedAtMs ->
        // Judged as the seal judges it: against the trials that began before this one.
        SessionXp(catalogue.associateBy { it.id }, history, bodyweightAt, excludeSessionId = sessionId, beforeMs = startedAtMs)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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

    /** The rite's scheme of each lift in this trial that has one, for the set hints; re-read as lifts come and go. */
    val schemes: StateFlow<Map<Long, SetScheme>> = repo.observeSessionSets(sessionId)
        .map { sets -> sets.map { it.exerciseId }.distinct().sorted() }
        .distinctUntilChanged()
        .map { ids -> repo.trialSchemes(sessionId, ids) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Each lift's last sealed top set: what a new top set is asked to beat. */
    val lastTops: StateFlow<Map<Long, TopMark>> = repo.observeSessionSets(sessionId)
        .map { sets -> sets.groupBy { it.exerciseId }.mapValues { (_, rows) -> rows.first().modifiers } }
        .distinctUntilChanged()
        .map { modifiers -> repo.lastTopMarks(modifiers) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** "Make set 1 a top set": today only. [onRefused] when the lift cannot take one (see [Repository.makeTopSet]). */
    fun makeTopSet(exerciseId: Long, onRefused: () -> Unit) {
        viewModelScope.launchGuarded("make top set") { if (!repo.makeTopSet(sessionId, exerciseId)) onRefused() }
    }

    /** "Add a drop to the last set": today only. [onRefused] in a superset or when there is no set to follow. */
    fun addDropAfter(setId: Long, onRefused: () -> Unit) {
        viewModelScope.launchGuarded("add drop") { if (!repo.addDropAfter(setId)) onRefused() }
    }

    /** "End drops" on a drop chain: the drops still to do are taken out. */
    fun endDropsAfter(setId: Long) {
        viewModelScope.launchGuarded("end drops") { repo.endDropsAfter(setId) }
    }

    /** This trial's own note on each exercise. */
    /** What each movement's rite prescribed, for the share card's squares; empty until loaded. */
    val shareTargets: StateFlow<Map<Long, Int>> =
        flow { emit(repo.exerciseTargets(sessionId)) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val exerciseNotes: StateFlow<Map<Long, String>> =
        repo.observeExerciseNotes(sessionId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Each exercise's note from the last sealed trial that left one, re-read only when the exercises change. */
    val lastNotes: StateFlow<Map<Long, String>> = repo.observeSessionSets(sessionId)
        .map { sets -> sets.map { it.exerciseId }.distinct().sorted() }
        .distinctUntilChanged()
        .map { ids -> repo.lastExerciseNotes(sessionId, ids) }
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
     * How far the finish has got: the XP page (with the level-up inside it), the
     * level-up page of a seal that has no XP page, the deeds page and the summary
     * (each only when the seal earned it, see [CelebrationFlow]), then the question
     * about the routine, then leave. XP must stay first: it is where every seal starts.
     */
    enum class Finish { XP, LEVEL, DEEDS, SUMMARY, ROUTINE }

    /**
     * The completion result and the finish stage. Held here, not in the
     * screen, so a rotation keeps the victory; the session is completed and
     * paid before either is set.
     */
    private val _completion = MutableStateFlow<Repository.CompletionResult?>(null)
    val completion: StateFlow<Repository.CompletionResult?> = _completion
    private val _finish = MutableStateFlow(Finish.XP)

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

    /**
     * Writes [accepted], each already narrowed to the fields the lifter ticked, and the [swaps], [additions] and
     * superset [links] they chose to keep.
     */
    fun applyRoutineUpdate(
        accepted: List<RoutineUpdate.Change>,
        swaps: List<RiteSwap.Swap>,
        additions: List<RoutineUpdate.Addition>,
        links: List<RoutineUpdate.Link>,
    ) {
        val offer = _routineUpdate.value ?: return
        _routineUpdate.value = null
        if (accepted.isEmpty() && swaps.isEmpty() && additions.isEmpty() && links.isEmpty()) return
        // The screen may leave straight after; the write must not die with it.
        viewModelScope.launchGuarded("update ${Words.rite.s}") {
            withContext(NonCancellable) { repo.applyRoutineUpdate(offer.presetId, accepted, swaps, additions, links) }
        }
    }

    private val warmupStore = WarmupStore(appContext)

    /** Whether the general warm-up reminder is on; the rite's own warm-up exercises show either way. */
    val warmupEnabled: Boolean = warmupStore.enabled

    /** How this trial answered the warm-up step. */
    private val _warmup = MutableStateFlow(warmupStore.stateFor(sessionId))
    val warmup: StateFlow<WarmupStep.State> = _warmup

    fun answerWarmup(state: WarmupStep.State) {
        warmupStore.setState(sessionId, state)
        _warmup.value = state
    }

    /** The rite's warm-up exercises, for the step to list; empty for a rite with none or a trial of no rite. */
    private val _warmupItems = MutableStateFlow<List<WarmupStep.Item>>(emptyList())
    val warmupItems: StateFlow<List<WarmupStep.Item>> = _warmupItems

    /** The ones ticked so far (exercise ids). */
    private val _warmupTicked = MutableStateFlow(warmupStore.tickedFor(sessionId))
    val warmupTicked: StateFlow<Set<Long>> = _warmupTicked

    init {
        viewModelScope.launch { _warmupItems.value = repo.trialWarmups(sessionId) }
    }

    fun tickWarmup(exerciseId: Long) {
        val next = _warmupTicked.value.let { if (exerciseId in it) it - exerciseId else it + exerciseId }
        warmupStore.setTicked(sessionId, next)
        _warmupTicked.value = next
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

    private val actions = LiveTrialActions(repo, appContext, sessionId)

    /**
     * The clocks running on timed sets, by set id. Held here, not in the row, so a card folding
     * or the screen turning does not stop a hold being timed. In memory only: a trial reopened
     * after the process died starts its timers fresh.
     */
    private val _clocks = MutableStateFlow<Map<Long, SetClock>>(emptyMap())
    val clocks: StateFlow<Map<Long, SetClock>> = _clocks

    fun setClock(setId: Long, clock: SetClock?) {
        _clocks.value = if (clock == null) _clocks.value - setId else _clocks.value + (setId to clock)
    }

    fun extendRest() = RestClock.extend(sessionId)

    fun shortenRest() = RestClock.shorten(sessionId)

    fun skipRest() = RestClock.cancel(sessionId)

    /**
     * Every set write the screen makes: the steppers, the log button, Undo, un-log and the
     * typed entries. [LiveTrialActions] routes it by metric, passing the set's current values
     * for the columns its metric does not own, and starts the rest a tick earns, the same
     * path the lock-screen "Set done" takes.
     */
    fun commit(set: SessionSet, metric: ExerciseMetric) {
        viewModelScope.launch { actions.commit(set, metric) }
    }

    /** "Warm up" on an exercise's menu; [onAdded] gets the new sets' ids for the Undo bar, and is not called when none were added. */
    fun addWarmupRamp(exerciseId: Long, onAdded: (List<Long>) -> Unit) {
        viewModelScope.launchGuarded("add warm-up ramp") { repo.addWarmupRamp(sessionId, exerciseId).takeIf { it.isNotEmpty() }?.let(onAdded) }
    }

    /** Undo for the ramp. */
    fun removeSets(setIds: List<Long>) {
        viewModelScope.launchGuarded("remove warm-up ramp") { repo.removeSets(setIds) }
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

    /** The kind tag's menu, and "Mark as warm-up" / "Count as a working set" on a set's edit row. */
    fun setKind(setId: Long, kind: SetKind) {
        viewModelScope.launchGuarded("change set type") { repo.setKind(setId, kind) }
    }

    fun setModifiers(exerciseId: Long, modifiers: String) {
        viewModelScope.launch { repo.setExerciseModifiers(sessionId, exerciseId, modifiers) }
    }

    /**
     * A movement added mid-session starts at a sane default: ten reps, or a
     * [DEFAULT_HOLD_SECONDS] hold. Adding a hold with "10 reps" is how the
     * seconds-as-reps confusion started. A loaded lift starts on the plan builder's own load
     * ([Repository.addedLoadKg]), not at 0 kg.
     */
    fun addExercise(exerciseId: Long, isHold: Boolean) {
        viewModelScope.launch {
            if (isHold) {
                repo.addExtraSet(sessionId, exerciseId, 0, null, "", DEFAULT_HOLD_SECONDS)
            } else {
                val level = ProgramAnswersStore.get(appContext)?.volume ?: VolumeLevel.STANDARD
                repo.addExtraSet(sessionId, exerciseId, 10, repo.addedLoadKg(exerciseId, 10, level), "")
            }
        }
    }

    fun moveExercise(exercisePosition: Int, up: Boolean) {
        viewModelScope.launchGuarded("move exercise") { repo.moveSessionExercise(sessionId, exercisePosition, up) }
    }

    /** "Superset with <next exercise>" on an exercise's menu. */
    fun linkWithNext(exerciseId: Long) {
        viewModelScope.launchGuarded("link superset") { repo.linkSupersetWithNext(sessionId, exerciseId) }
    }

    /** A chip on the effort strip: rates a logged set, or clears its rating when [rpe] is null. */
    fun setRpe(setId: Long, rpe: Int?) {
        viewModelScope.launchGuarded("log effort") { repo.setRpe(setId, rpe) }
    }

    /** Unlink on a superset's strip. */
    fun unlink(exerciseId: Long) {
        viewModelScope.launchGuarded("unlink superset") { repo.unlinkSuperset(sessionId, exerciseId) }
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
        val byId = exercises.value.associateBy { it.id }
        val peaks = SessionPeaks.of(ui.value.sets, peakBook.value, bodyweight.value) { byId[it.exerciseId] }
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
        viewModelScope.launchGuarded("reopen ${Words.trial.s}") {
            when (val decision = repo.reopenSealedTrial(sessionId)) {
                is SealedReopen.Decision.Refuse -> onRefused(decision.reason)
                is SealedReopen.Decision.Reopen -> {
                    ui.first { it.session?.completedAtMs == null }
                    _routineUpdate.value = null
                    _peaks.value = emptyList()
                    _finish.value = Finish.XP
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

    fun setExerciseNote(exerciseId: Long, note: String) {
        viewModelScope.launch { repo.setExerciseNote(sessionId, exerciseId, note) }
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
    val peakBook by viewModel.peakBook.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val exercises by viewModel.exercises.collectAsStateWithLifecycle()
    val bodyweight by viewModel.bodyweight.collectAsStateWithLifecycle()
    val xpBook by viewModel.xpBook.collectAsStateWithLifecycle()
    val sex by viewModel.sex.collectAsStateWithLifecycle()
    val focus by viewModel.focus.collectAsStateWithLifecycle()
    val pace by viewModel.pace.collectAsStateWithLifecycle()
    val routineUpdate by viewModel.routineUpdate.collectAsStateWithLifecycle()
    val warmup by viewModel.warmup.collectAsStateWithLifecycle()
    val completion by viewModel.completion.collectAsStateWithLifecycle()
    val finish by viewModel.finish.collectAsStateWithLifecycle()
    val peaks by viewModel.peaks.collectAsStateWithLifecycle()
    val wornTitleId by viewModel.wornTitleId.collectAsStateWithLifecycle()
    val claiming by viewModel.claiming.collectAsStateWithLifecycle()
    val lastLogged by viewModel.lastLogged.collectAsStateWithLifecycle()
    val exerciseNotes by viewModel.exerciseNotes.collectAsStateWithLifecycle()
    val shareTargets by viewModel.shareTargets.collectAsStateWithLifecycle()
    val lastNotes by viewModel.lastNotes.collectAsStateWithLifecycle()
    val reasons by viewModel.reasons.collectAsStateWithLifecycle()
    val schemes by viewModel.schemes.collectAsStateWithLifecycle()
    val lastTops by viewModel.lastTops.collectAsStateWithLifecycle()
    val rest by viewModel.rest.collectAsStateWithLifecycle()
    val clocks by viewModel.clocks.collectAsStateWithLifecycle()
    var confirmAbandon by remember { mutableStateOf(false) }
    var confirmClaim by remember { mutableStateOf(false) }
    var confirmReopen by remember { mutableStateOf(false) }
    var reopenRefusal by remember { mutableStateOf<SealedReopen.Refusal?>(null) }
    var showExercisePicker by remember { mutableStateOf(false) }
    var editModifiersFor by remember { mutableStateOf<Long?>(null) }
    var showRiteMuscles by remember { mutableStateOf(false) }
    var infoFor by remember { mutableStateOf<Long?>(null) }
    var editLoadFor by remember { mutableStateOf<SessionSet?>(null) }
    var platesFor by remember { mutableStateOf<Long?>(null) }
    var typing by remember { mutableStateOf<Pair<SessionSet, FigureKind>?>(null) }
    var showName by rememberSaveable { mutableStateOf(false) }
    var showNote by rememberSaveable { mutableStateOf(false) }
    // The exercise whose own note is being written.
    var noteForExercise by rememberSaveable { mutableStateOf<Long?>(null) }
    // The exercise the lifter opened by hand; null follows the next unlogged set.
    var openOverride by rememberSaveable { mutableStateOf<Long?>(null) }
    // A logged set opened for editing or un-logging.
    var editingSetId by rememberSaveable { mutableStateOf<Long?>(null) }
    var undo by remember { mutableStateOf<UndoPrompt?>(null) }
    var undoSerial by remember { mutableIntStateOf(0) }
    // An exercise with logged sets asks before it goes; one with none just offers Undo.
    var confirmRemoveExercise by remember { mutableStateOf<Long?>(null) }
    // The docked footer's height as drawn, so the Undo bar always sits just above it however tall the rest bar is.
    var footerPx by remember { mutableIntStateOf(0) }
    // The set just logged, whose effort the strip asks for (when Settings turns that on); the next set logged takes it over.
    var effortFor by rememberSaveable { mutableStateOf<Long?>(null) }
    var gainSerial by rememberSaveable { mutableIntStateOf(0) }
    // Bumped when a seal is declined or fails, so the slider comes back to the start.
    var slideReset by remember { mutableIntStateOf(0) }

    fun metricOf(exerciseId: Long): ExerciseMetric =
        exercises.firstOrNull { it.id == exerciseId }?.metric ?: ExerciseMetric.REPS

    // Every write the screen makes goes through here: the steppers, the log
    // button, Undo, un-log and the typed entries. The view model routes it by
    // metric (LiveTrialActions), the way the lock-screen "Set done" does.
    val haptic = LocalHapticFeedback.current
    fun commit(set: SessionSet) {
        // A tick the lifter feels: only a set going from not logged to logged. The system's
        // own haptics setting decides whether it is felt at all.
        if (set.done && ui.sets.firstOrNull { it.id == set.id }?.done == false) {
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
        }
        viewModel.commit(set, metricOf(set.exerciseId))
    }

    fun applyLoad(set: SessionSet, kg: Double?) = commit(set.copy(weightKg = kg))

    // The lock-screen companion. The service watches the database and stops
    // itself when the session completes or is abandoned - the screen only
    // has to announce that the session is the live one.
    val screenContext = LocalContext.current
    // Android 13+ posts nothing without POST_NOTIFICATIONS, and before this
    // only the reminder toggle asked for it, so most lifters never saw the
    // trial notification. An in-app sheet says why, and only its Allow fires
    // the system prompt, which gets one chance: that is what the asked flag
    // spends. "Not now" spends nothing and asks again on the next trial, up to
    // FirstRun.MAX_NOTIFICATION_ASKS times in all (Settings can still grant it).
    val trialPrefs = remember { TrialPrefs(screenContext) }
    // The pocket lock: a dim cover that ignores touches until held. Survives rotation.
    var locked by rememberSaveable { mutableStateOf(false) }
    // A phone that sleeps between sets hides the rest timer; the setting defaults on.
    val view = LocalView.current
    val keepAwake = remember { trialPrefs.keepScreenOn }
    DisposableEffect(view, keepAwake) {
        val before = view.keepScreenOn
        view.keepScreenOn = keepAwake
        onDispose { view.keepScreenOn = before }
    }
    var restOffMovements by remember { mutableStateOf(trialPrefs.restOffNames()) }
    val restAccess = rememberNotificationAccess(Notifications.restChannel(trialPrefs.restAlert))
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // Re-announce so the service posts now, not at the next ticked set.
        if (granted) WorkoutSessionService.start(screenContext, sessionId)
    }
    var notificationSheet by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(sessionId) {
        WorkoutSessionService.start(screenContext, sessionId)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(screenContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED &&
            FirstRun.askForNotifications(
                asked = FirstRunPrefs.notificationsAsked(screenContext),
                notNowCount = FirstRunPrefs.notificationsNotNow(screenContext),
            )
        ) {
            notificationSheet = true
        }
    }
    if (notificationSheet) {
        NotificationAskSheet(
            onAllow = {
                notificationSheet = false
                FirstRunPrefs.setNotificationsAsked(screenContext)
                askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
            onNotNow = {
                notificationSheet = false
                FirstRunPrefs.addNotificationsNotNow(screenContext)
            },
        )
    }

    val session = ui.session
    if (session == null) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Spacer(Modifier.height(20.dp))
            Text("Opening the ${Words.trial.s}…", style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted)
        }
        return
    }

    // Claimed in an earlier process (killed mid-victory): the XP is banked and
    // the live trial must not come back with a Seal the Trial that can only
    // fail. This claim's own result is still on its way while [claiming].
    if (session.completedAtMs != null && completion == null && !claiming) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Spacer(Modifier.height(20.dp))
            Text("${Words.trial.c} already ${Words.sealed.s}.", style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted)
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
    // The next set is the next in order, or inside a superset the next in the round; the lock-screen
    // "Set done" and the notification take it from the same rule.
    val nextSet = remember(ui.sets) { Supersets.nextSet(ui.sets) }
    val nextBlock = blocks.firstOrNull { it.id == nextSet?.exerciseId }
    val openBlock = blocks.firstOrNull { it.id == openOverride } ?: nextBlock ?: blocks.lastOrNull()
    val activeSet = openBlock?.sets?.firstOrNull { it.isPending } ?: nextSet
    val groups = remember(blocks) { blocks.map { b -> b.sets.firstNotNullOfOrNull { it.supersetGroup } } }
    val supersetRuns = remember(groups) { Supersets.runs(groups) }
    val supersetNumbers = remember(groups) { Supersets.numbers(groups) }
    // The logged set whose effort is being asked, while it is still logged and counted.
    val effortSet = effortFor?.let { id -> ui.sets.firstOrNull { it.id == id } }?.takeIf { it.done && !it.warmup }
    // shortcut: the rest-bar chips cover rep sets only (a hold falls back to the under-row strip); add a hold chip set if holds need rating from the rest bar.
    // Flips to false once when the rest runs out, so the effort question can move off the rest bar.
    // remember(rest), not produceState: a produceState keeps the old value for a frame after a new rest starts, so the footer drew once without the effort chips and then grew.
    val restRunningState = remember(rest) { mutableStateOf(rest?.let { !it.isOver(SystemClock.elapsedRealtime()) } ?: false) }
    LaunchedEffect(rest) {
        val left = rest?.let { it.endsAtMs - SystemClock.elapsedRealtime() } ?: 0L
        if (left > 0) {
            delay(left)
            restRunningState.value = false
        }
    }
    val restRunning = restRunningState.value
    // A set logged any other way (the lock screen, the notification) takes the question over; a set taken back loses it.
    val loggedIds = ui.sets.filter { it.done && !it.warmup }.map { it.id }
    var loggedSeen by remember { mutableStateOf<Set<Long>?>(null) }
    LaunchedEffect(loggedIds) {
        val now = loggedIds.toSet()
        val seen = loggedSeen
        loggedSeen = now
        val next = effortTarget(seen, now, effortFor, loggedIds)
        if (next != effortFor) effortFor = next
    }
    // Under the row (no rest bar) the question does not wait for ever: minutes later it is not about the set in hand.
    LaunchedEffect(effortFor, restRunning) {
        if (effortFor != null && !restRunning) {
            delay(EFFORT_ASK_MS)
            effortFor = null
        }
    }
    val effortAsk = effortSet?.let {
        effortAsk(restRunning, allLogged, metricOf(it.exerciseId) == ExerciseMetric.REPS, EffortSetting.enabled)
    } ?: EffortAsk.NONE
    val restChipsOn = effortAsk == EffortAsk.REST_BAR
    val underRowEffort = effortSet?.takeIf { effortAsk == EffortAsk.UNDER_ROW }
    // The live tally: what the ticked sets have earned, priced by the one function the seal uses.
    val doneForTally = remember(ui.sets) { ui.sets.filter { it.done && !it.warmup } }
    // shortcut: the tally re-prices every prefix of the done sets (O(n^2) in sets, fine at trial size); price incrementally if trials reach hundreds of sets.
    val tally = remember(xpBook, doneForTally, bodyweight) {
        xpBook?.tally(doneForTally, bodyweight) ?: SessionXp.Tally.EMPTY
    }
    var gain by remember { mutableStateOf<LiveGain?>(null) }
    var gainSeen by remember { mutableStateOf<Set<Long>?>(null) }
    LaunchedEffect(tally) {
        val seen = gainSeen
        gainSeen = tally.gains.keys
        // The first read is a trial coming back, not a tick: nothing floats for it.
        if (seen == null) return@LaunchedEffect
        val fresh = tally.gains.keys - seen
        val id = fresh.lastOrNull() ?: return@LaunchedEffect
        val set = ui.sets.firstOrNull { it.id == id } ?: return@LaunchedEffect
        val amount = tally.gains.getValue(id)
        if (amount > 0) gain = LiveGain(++gainSerial, set.exerciseName, amount)
    }
    // In a superset the card follows the round, so ticking one movement opens its partner, whichever
    // door the tick came through.
    LaunchedEffect(nextSet?.id) {
        val at = blocks.indexOfFirst { it.id == nextSet?.exerciseId }
        if (nextSet != null && Supersets.runAt(groups, at) != null) openOverride = null
    }
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

    // [seconds] is a timed set's figure when its clock logged it; otherwise the set's own.
    // Ticking the row (no [seconds]) logs a hold at the figure in its row with no timer needed; a
    // hold being counted stops into the adjust step first, and an open adjust step logs its figure.
    fun logActive(seconds: Int? = null) {
        val set = activeSet ?: return
        var logSeconds = seconds
        if (seconds == null) {
            val clock = viewModel.clocks.value[set.id]
            if (clock != null) {
                val tick = clock.onRowLog(SystemClock.elapsedRealtime())
                if (!tick.log) {
                    viewModel.setClock(set.id, tick.clock)
                    return
                }
                logSeconds = tick.seconds
            }
        }
        commit(set.copy(durationSec = logSeconds ?: set.durationSec, done = true))
        viewModel.setClock(set.id, null)
        effortFor = set.id
        undoSerial++
        undo = UndoPrompt.Logged(undoSerial, set.id, set.workingNumber(ui.sets))
        // In a superset the round moves to the partner whose turn it is; otherwise the card moves on once its last set is logged.
        val turn = Supersets.turnAfter(ui.sets, set.exerciseId)
        if (turn != null) {
            openOverride = turn
        } else if (openBlock != null && openBlock.sets.none { it.isPending && it.id != set.id }) {
            openOverride = null
        }
    }

    fun offerRemovedUndo(message: String, removed: Repository.RemovedSets) {
        undoSerial++
        undo = UndoPrompt.Removed(undoSerial, message, removed)
    }

    fun offerRampUndo(setIds: List<Long>) {
        undoSerial++
        undo = UndoPrompt.Ramp(undoSerial, setIds)
    }

    fun undoLog(prompt: UndoPrompt.Logged) {
        ui.sets.firstOrNull { it.id == prompt.setId }?.let { commit(it.copy(done = false)) }
        if (effortFor == prompt.setId) effortFor = null
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

    // Once sealed the live trial is not drawn under the finish pages (the routine question opens over this screen,
    // and it read "Trial in progress" behind a sealed trial).
    if (completion != null) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${Words.trial.c} ${Words.sealed.s}", style = MaterialTheme.typography.titleMedium, color = IronvellumColors.Ink)
            Text(session.title.ifBlank { session.label }, style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted)
        }
    } else Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TrialHeader(
                title = session.title.ifBlank { session.label },
                gain = gain,
                motion = animatorsOn(LocalContext.current),
                onGainGone = { id -> if (gain?.serial == id) gain = null },
                scrolled = scroll.canScrollBackward,
                done = doneCount,
                total = workCount,
                minutesLeft = minutesLeft,
                startedAtMs = session.startedAtMs,
                xp = tally.total,
                onBack = onExit,
                onMuscles = { showRiteMuscles = true },
                onLock = { locked = true },
                onAbandon = { confirmAbandon = true },
            )
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scroll)
                    .padding(horizontal = 16.dp),
            ) {
                // Always there while blocked, never only during a rest: a line that came and went with every rest
                // moved the whole screen and sat under the XP toast. The countdown still runs; only its alert cannot arrive.
                NotificationBlockedNotice(
                    channelId = Notifications.restChannel(trialPrefs.restAlert),
                    access = restAccess,
                    blockedText = "Notifications are off, so the rest timer will not alert you when a rest ends.",
                    modifier = Modifier.padding(top = 8.dp),
                    compact = true,
                )
                // Before the first set is logged; a logged set takes it away for good.
                val warmupItems by viewModel.warmupItems.collectAsStateWithLifecycle()
                val warmupTicked by viewModel.warmupTicked.collectAsStateWithLifecycle()
                // Slides shut instead of vanishing: the whole list used to jump up under the finger on the first tick.
                AnimatedVisibility((viewModel.warmupEnabled || warmupItems.isNotEmpty()) && ui.sets.isNotEmpty() && doneCount == 0) {
                    val warmupText = remember(ui.sets, exercises) {
                        val groupOf = exercises.associate { it.id to it.muscleGroup }
                        WarmupStep.text(ui.sets.map { it.exerciseId }.distinct().mapNotNull { groupOf[it] })
                    }
                    Box(Modifier.fillMaxWidth().animateContentSize()) {
                    WarmupRow(
                        text = warmupText.takeIf { viewModel.warmupEnabled },
                        items = warmupItems,
                        ticked = warmupTicked,
                        state = warmup,
                        onTick = viewModel::tickWarmup,
                        onAnswer = viewModel::answerWarmup,
                    )
                    }
                }
                var previousFolded = false

                @Composable
                fun BlockItem(blockIdx: Int, block: TrialBlock) {
                    val run = supersetRuns.firstOrNull { blockIdx in it }
                    val rail = run?.let { RailPosition.of(blockIdx, it) }
                    // A superset's members share a thin rail in the left gutter, one segment per member.
                    Column(if (rail != null) Modifier.supersetRail(rail, if (block.id == openBlock?.id) OPEN_TITLE_CENTER else FOLDED_TITLE_CENTER) else Modifier) {
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
                            val peak = remember(records, first.exerciseName, first.modifiers) {
                                // The peak of this lift in this variation: an assisted pull-up's peak is not the pull-up's.
                                val lift = Variation.liftKey(first.exerciseName, first.modifiers)
                                records.filterKeys { it.first == lift }.values.maxByOrNull { it.score }
                            }
                            OpenExerciseCard(
                                block = block,
                                metric = metric,
                                weighted = weighted,
                                reference = referenceLine(exercise, metric, lastLogged[block.id], peak),
                                reason = reasons[block.id],
                                note = exerciseNotes[block.id],
                                lastNote = lastNotes[block.id],
                                onNote = { noteForExercise = block.id },
                                modifiersEditable = modifiersEditable,
                                activeSetId = if (activeSet?.exerciseId == block.id) activeSet.id else null,
                                editingSetId = editingSetId,
                                newPeaks = newPeakSetIds(block.sets, metric, peakBook),
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
                                // Before anything is logged, on a loaded movement with no warm-up yet.
                                onWarmup = if (
                                    exercise != null && block.sets.none { it.warmup || it.done } &&
                                    WarmupRamp.planFor(
                                        exercise.name, exercise.muscleGroup.name, metric, first.weightKg,
                                        kit = ProgramAnswersStore.kit, weighted = weighted, unit = Units.current,
                                    ).isNotEmpty()
                                ) {
                                    { viewModel.addWarmupRamp(block.id, ::offerRampUndo) }
                                } else {
                                    null
                                },
                                restOff = if (metric == ExerciseMetric.REPS || metric == ExerciseMetric.HOLD) restOffMovements.contains(first.exerciseName.trim().lowercase()) else null,
                                onRestOff = { off ->
                                    trialPrefs.setRestOffFor(first.exerciseName, off)
                                    restOffMovements = trialPrefs.restOffNames()
                                    // Turning it off mid-rest ends the rest that is running.
                                    if (off) viewModel.skipRest()
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
                                    if (block.sets.any { it.done && !it.warmup }) {
                                        confirmRemoveExercise = block.id
                                    } else {
                                        viewModel.removeExercise(block.id) { offerRemovedUndo("${first.exerciseName} removed", it) }
                                    }
                                },
                                onPlates = if (GearRequirements.isBarbellLift(first.exerciseName)) ({ platesFor = block.id }) else null,
                                superset = supersetNumbers[blockIdx]?.let { number ->
                                    SupersetLine(number, Supersets.turnAfter(ui.sets, block.id)?.let { turn -> blocks.firstOrNull { it.id == turn }?.first?.exerciseName })
                                },
                                onUnlink = { viewModel.unlink(block.id) },
                                supersetWith = blocks.getOrNull(blockIdx + 1)?.takeIf { Supersets.canLink(groups, blockIdx, blockIdx + 1) }
                                    ?.first?.exerciseName,
                                onSuperset = { viewModel.linkWithNext(block.id) },
                                effortSetId = underRowEffort?.id,
                                onRpe = { set, rpe -> viewModel.setRpe(set.id, rpe) },
                                hints = if (metric == ExerciseMetric.REPS || metric == ExerciseMetric.HOLD) {
                                    val scheme = schemes[block.id]
                                        ?: if (block.sets.any { it.kind == SetKind.TOP }) SetScheme(type = SchemeType.TOP_BACKOFF) else SetScheme.DEFAULT
                                    val lift = LiftLoading(
                                        stepKg = Progression.weightStepKg(exercise?.muscleGroup?.name.orEmpty(), first.exerciseName),
                                        worn = WornLoad.forLift(ProgramAnswersStore.kit, first.exerciseName, weighted, metric),
                                        unit = Units.current,
                                        bodyweightKg = bodyweight,
                                        bodyweightLift = !weighted,
                                        bodyShare = MovementDifficulty.bodyShare(first.exerciseName),
                                    )
                                    val pendingTop = block.sets.firstOrNull { it.kind == SetKind.TOP && it.isPending }
                                    val wouldPeak = pendingTop != null && newPeakSetIds(listOf(pendingTop.copy(done = true)), metric, peakBook).isNotEmpty()
                                    TrialHints.forLift(block.sets, scheme, lastTops[block.id], wouldPeak, lift, { kg -> kgLabel(kg, weighted) }, hold = metric == ExerciseMetric.HOLD)
                                } else {
                                    emptyMap()
                                },
                                onMakeTopSet = run {
                                    val working = block.sets.filter { !it.warmup }
                                    if (metric == ExerciseMetric.REPS && first.supersetGroup == null && working.size >= 2 && working.none { it.kind == SetKind.TOP }) {
                                        { viewModel.makeTopSet(block.id) { Toast.makeText(screenContext, "This lift cannot take a top set.", Toast.LENGTH_SHORT).show() } }
                                    } else {
                                        null
                                    }
                                },
                                onAddDrop = block.sets.lastOrNull { !it.warmup }?.takeIf { metric == ExerciseMetric.REPS && first.supersetGroup == null }?.let { last ->
                                    { viewModel.addDropAfter(last.id) { Toast.makeText(screenContext, "A drop cannot be added here.", Toast.LENGTH_SHORT).show() } }
                                },
                                onEndDrops = viewModel::endDropsAfter,
                                xpBySet = tally.gains,
                                onEdit = ::commit,
                                timed = true,
                                clocks = clocks,
                                timedAlert = trialPrefs.restAlert,
                                onClock = viewModel::setClock,
                                onTimedLog = { seconds -> logActive(seconds) },
                                onTypeLoad = { editLoadFor = it },
                                onTypeFigure = { set, kind -> typing = set to kind },
                                onToggleEdit = { set -> editingSetId = if (editingSetId == set.id) null else set.id },
                                onSetKind = { set, kind -> viewModel.setKind(set.id, kind) },
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
                                subline = supersetNumbers[blockIdx]?.let { "Superset $it · " }.orEmpty() + foldedSubline(block, metric, weighted),
                                done = block.sets.count { it.done },
                                total = block.sets.count { !it.warmup },
                                hasNote = exerciseNotes[block.id] != null,
                                onOpen = { openOverride = block.id },
                            )
                            previousFolded = true
                            // The card moved on (its last set, or a superset partner opening), so the question
                            // about the set just logged sits under that set's own exercise, never on another card.
                            if (underRowEffort != null && underRowEffort.exerciseId == block.id) {
                                EffortStrip(
                                    title = "How hard was set ${underRowEffort.workingNumber(ui.sets)}?",
                                    rpe = underRowEffort.rpe,
                                    onPick = { viewModel.setRpe(underRowEffort.id, it) },
                                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                                )
                                previousFolded = false
                            }
                        }
                    }
                    }
                }
                blocks.forEachIndexed { blockIdx, block -> BlockItem(blockIdx, block) }
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
                            "$unlogged ${plural(unlogged, "set", "sets")} left · slide to ${Words.seal.s}"
                        } else {
                            "Log a set to ${Words.seal.s} the ${Words.trial.s}"
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
                modifier = Modifier.onSizeChanged { footerPx = it.height },
                allLogged = allLogged,
                rest = rest,
                nextSetNumber = activeSet?.workingNumber(ui.sets),
                nextLine = activeSet?.let { set ->
                    val figure = setFigureText(set, metricOf(set.exerciseId), exercises.firstOrNull { it.id == set.exerciseId }?.isWeighted ?: false)
                    // Named only when it is not the card on screen.
                    val where = if (set.exerciseId != openBlock?.id) " · ${set.exerciseName}" else ""
                    "Set ${set.workingNumber(ui.sets)}$where · $figure"
                },
                noRestYet = activeSet?.let { Supersets.midRound(ui.sets, it) } == true,
                onLog = ::logActive,
                onExtend = viewModel::extendRest,
                onShorten = viewModel::shortenRest,
                onSkip = viewModel::skipRest,
                onSealed = ::requestSeal,
                slideReset = slideReset,
                effortSet = effortSet.takeIf { restChipsOn },
                // Names the exercise: in a superset the question is about the set just done, not the card now open.
                effortLabel = effortSet?.let { it.exerciseName + if (it.kind == SetKind.TOP) " top set" else " set ${it.workingNumber(ui.sets)}" }.orEmpty(),
                onRate = { rpe -> effortSet?.let { viewModel.setRpe(it.id, rpe) } },
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
                        is UndoPrompt.Ramp -> {
                            viewModel.removeSets(prompt.setIds)
                            undo = null
                        }
                        is UndoPrompt.Added -> {
                            viewModel.removeExercise(prompt.exerciseId) { }
                            undo = null
                        }
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = with(LocalDensity.current) { footerPx.toDp() } + 8.dp),
            )
        }
        // Last child, so nothing above it (the Undo bar included) can take a touch while locked.
        if (locked) {
            PocketLockOverlay(
                rest = rest,
                nextLine = activeSet?.let { set ->
                    "Set ${set.workingNumber(ui.sets)} · ${set.exerciseName} · " +
                        setFigureText(set, metricOf(set.exerciseId), exercises.firstOrNull { it.id == set.exerciseId }?.isWeighted ?: false)
                },
                onUnlock = { locked = false },
            )
        }
    }

    if (confirmAbandon) {
        IronvellumDialog(
            onDismissRequest = { confirmAbandon = false },
            title = { Text("Abandon this ${Words.trial.s}?") },
            text = { Text("${Words.unsealed.c} ${Words.trial.pl} grant no XP and are erased from ${Words.theChronicle.t}.") },
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

    confirmRemoveExercise?.let { exerciseId ->
        val rows = ui.sets.filter { it.exerciseId == exerciseId }
        val name = rows.firstOrNull()?.exerciseName
        if (name == null) {
            confirmRemoveExercise = null
        } else {
            val logged = rows.count { it.done && !it.warmup }
            IronvellumDialog(
                onDismissRequest = { confirmRemoveExercise = null },
                title = { Text("Remove $name?") },
                text = { Text("${if (logged == 1) "Its 1 logged set goes" else "Its $logged logged sets go"} with it. Undo stays on screen for a few seconds.") },
                // Keeping it is the filled action: a reflex tap removes nothing.
                confirmButton = { IronvellumButton("Keep it", onClick = { confirmRemoveExercise = null }) },
                dismissButton = {
                    IronvellumButton(
                        "Remove",
                        danger = true,
                        onClick = {
                            confirmRemoveExercise = null
                            viewModel.removeExercise(exerciseId) { offerRemovedUndo("$name removed", it) }
                        },
                    )
                },
            )
        }
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
                        "${Words.seal.c} the ${Words.trial.s}?"
                    } else {
                        "${Words.seal.c} with $unlogged ${plural(unlogged, "set", "sets")} unlogged?"
                    },
                )
            },
            text = {
                Text(
                    if (unlogged == 0) {
                        "Every set is logged. ${Words.seal.c} it now?"
                    } else {
                        "${plural(unlogged, "It", "They")} won't count."
                    },
                )
            },
            // Sealing is the deliberate action here, so it takes the confirm
            // slot; KEEP GOING is the safe default.
            confirmButton = {
                IronvellumButton(
                    if (unlogged == 0) Words.seal.c else "${Words.seal.c} anyway",
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

    platesFor?.let { exerciseId ->
        val sets = ui.sets.filter { it.exerciseId == exerciseId && !it.warmup }
        val set = sets.firstOrNull { it.isPending } ?: sets.lastOrNull()
        if (set != null) {
            PlateSheet(
                exerciseName = set.exerciseName,
                setLabel = "set ${set.workingNumber(ui.sets)}",
                loadKg = set.weightKg,
                onUse = { kg ->
                    commit(set.copy(weightKg = kg))
                    platesFor = null
                },
                onDismiss = { platesFor = null },
            )
        }
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
                // A top set's load is not the back-offs' (nor a drop's): the sets that follow are the ones of its own kind.
                (it.id == target.id || (!it.warmup && it.kind == target.kind && (!it.done || it.weightKg == target.weightKg)))
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
                    label = { Text("Load (${Units.name})") },
                    placeholder = { Text("Leave blank for bodyweight") },
                    isError = parsed.isFailure,
                    supportingText = if (parsed.isFailure) {
                        { Text("Enter 0 to ${Loads.ceilingText(MAX_LOAD_KG, Units.current)} ${Units.name}.") }
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

    noteForExercise?.let { exerciseId ->
        val name = ui.sets.firstOrNull { it.exerciseId == exerciseId }?.exerciseName
        if (name == null) {
            noteForExercise = null
        } else {
            ExerciseNoteDialog(
                name = name,
                current = exerciseNotes[exerciseId].orEmpty(),
                lastNote = lastNotes[exerciseId],
                onSave = {
                    viewModel.setExerciseNote(exerciseId, it)
                    noteForExercise = null
                },
                onDismiss = { noteForExercise = null },
            )
        }
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
            current = current?.modifiers.orEmpty(),
            onConfirm = { picked ->
                viewModel.setModifiers(exerciseId, picked)
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
                // It lands at the end of the trial, out of sight: say so, with the way back.
                undoSerial++
                undo = UndoPrompt.Added(undoSerial, exercise.id, exercise.name)
            },
            onDismiss = { showExercisePicker = false },
        )
    }

    var shareRender by remember { mutableStateOf<((Boolean, Boolean) -> String)?>(null) }

    // Celebrate first, then ask about the routine, then leave. The stage lives
    // in the view model, so a rotation resumes where the lifter was; the
    // session is already completed and paid, whatever the answer.
    completion?.let { result ->
        val hasXp = CelebrationFlow.hasXpPage(result)
        val hasLevel = CelebrationFlow.hasLevelUp(result)
        val hasDeeds = CelebrationFlow.deedsOf(result).isNotEmpty()
        when (val stage = CelebrationFlow.resolve(finish, hasXp, hasLevel, hasDeeds)) {
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
                        weightedOf = { id -> exercises.firstOrNull { it.id == id }?.isWeighted ?: false },
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
                onContinue = { viewModel.advanceFinish(CelebrationFlow.after(stage, hasXp, hasLevel, hasDeeds)) },
                onSkipToSummary = { viewModel.advanceFinish(SessionViewModel.Finish.SUMMARY) },
                onWear = viewModel::wearTitle,
                onShare = {
                    // The session row in the flow may not have refreshed yet;
                    // the completion result carries the authoritative figures.
                    val sealed = session.copy(
                        completedAtMs = session.completedAtMs ?: System.currentTimeMillis(),
                        xpAwarded = result.xpAwarded,
                        strengthScore = result.strengthScore,
                    )
                    val sets = ui.sets
                    val catalogue = exercises.associateBy { it.id }
                    val notes = exerciseNotes
                    val targets = shareTargets
                    shareRender = { includeNotes, includeEffort ->
                        WorkoutShare.format(
                            sealed,
                            sets,
                            catalogue,
                            peaks = peaks,
                            targets = targets,
                            exerciseNotes = notes,
                            includeNotes = includeNotes,
                            unit = Units.current,
                            includeEffort = includeEffort,
                            effortScale = EffortSetting.scale,
                        )
                    }
                },
                onReopen = { confirmReopen = true },
            )
        }
    }

    if (confirmReopen) {
        IronvellumDialog(
            onDismissRequest = { confirmReopen = false },
            title = { Text("Reopen this ${Words.trial.s}?") },
            text = { Text("The XP it paid is taken back until you ${Words.seal.s} again.") },
            // Keeping it sealed is the filled action: a reflex tap changes nothing.
            confirmButton = {
                IronvellumButton("Keep it ${Words.sealed.s}", onClick = { confirmReopen = false })
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
            title = { Text("This ${Words.trial.s} stays ${Words.sealed.s}") },
            text = { Text(reopenRefusalText(reason)) },
            confirmButton = { IronvellumButton("OK", onClick = { reopenRefusal = null }) },
        )
    }

    shareRender?.let { render ->
        ShareCardDialog(
            render = render,
            hasNotes = exerciseNotes.isNotEmpty(),
            hasEffort = ui.sets.any { it.rpe != null },
            onDismiss = { shareRender = null },
        )
    }
}

/** Why a trial cannot be reopened, in the words the dialog shows. */
private fun reopenRefusalText(reason: SealedReopen.Refusal): String = when (reason) {
    SealedReopen.Refusal.NOT_SEALED -> "This ${Words.trial.s} is already open."
    SealedReopen.Refusal.WINDOW_CLOSED -> "Ten minutes have passed. Amend it from ${Words.theChronicle.t} instead."
    SealedReopen.Refusal.ANOTHER_LIVE -> "Another ${Words.trial.s} is open. ${Words.seal.c} or abandon it first."
    SealedReopen.Refusal.AMENDED -> "It was amended after ${Words.sealing.s}, so it cannot be taken back."
    SealedReopen.Refusal.LEDGER_SHORT -> "The XP it paid has been spent, so it cannot be taken back."
}

/** The ± buttons walk the plate grid, so 15.2 kg steps to 17.5 or 15, never 17.7 or 12.7. */
internal const val LOAD_STEP_KG = 2.5

/** Heavier than any lift on record; a guard against a stray extra digit. */
internal const val MAX_LOAD_KG = 500.0

internal const val LOAD_INPUT_MAX_CHARS = 7

/** Loads are kept to the gram, see [parseLoadKg]. */
internal const val LOAD_INPUT_DECIMALS = 3

internal fun cleanLoadInput(typed: TextFieldValue): TextFieldValue =
    typed.copy(text = DecimalInput.sanitize(typed.text, LOAD_INPUT_DECIMALS, LOAD_INPUT_MAX_CHARS))

/**
 * One step down the load. A [worn] kit (the vest on a bodyweight lift, see [WornLoad.forLift]) steps in its
 * own blocks and belt plates; without one it is the movement's grid, as ever.
 */
internal fun stepDownKg(kg: Double?, unit: LoadUnit = Units.current, gridLb: Double = LOAD_STEP_LB, worn: LoadKit? = null): Double? =
    if (worn != null) {
        if (kg == null) null else worn.previousKg(kg, unit)
    } else {
        Loads.stepDown(kg, unit, gridLb, LOAD_STEP_KG)
    }

/**
 * One step up the load. At a full vest with no belt the kit has no next load, and a button that does nothing
 * reads as broken: the lifter is overriding the plan by hand, so the movement's own grid takes over there.
 */
internal fun stepUpKg(kg: Double?, unit: LoadUnit = Units.current, gridLb: Double = LOAD_STEP_LB, worn: LoadKit? = null): Double =
    worn?.nextKg(kg ?: 0.0, unit) ?: Loads.stepUp(kg, unit, gridLb, LOAD_STEP_KG)

/** In lb the ± buttons walk 5 lb on compounds and 2.5 lb on small work, from the movement's kg progression step. */
internal const val LOAD_STEP_LB = 5.0

internal fun loadGridLb(exerciseName: String): Double = Loads.lbGrid(Progression.weightStepKg("", exerciseName))

/**
 * A typed load: blank is bodyweight (0.0), a comma is a decimal point, and
 * the value is kept to the gram. Anything else, or out of range, fails.
 */
internal fun parseLoadKg(text: String, unit: LoadUnit = Units.current): Result<Double> =
    Loads.parse(text, unit, MAX_LOAD_KG)

/** A load as the dialog shows it for editing: no unit, no trailing ".0". */
internal fun loadText(kg: Double, unit: LoadUnit = Units.current): String =
    if (unit == LoadUnit.LB) Loads.fieldText(kg, unit)
    else if (kg == kg.toLong().toDouble()) kg.toLong().toString() else kg.toString()


private const val TITLE_CAP = 80

private const val PUBLIC_NOTE_CAP = 500


/** One exercise of the live trial: its sets in order, and where the rite put it. */
internal class TrialBlock(val id: Long, val position: Int, val sets: List<SessionSet>) {
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

    /** "Plank added at the end": an exercise added mid-trial lands last, and Undo takes it back out. */
    data class Added(override val serial: Int, val exerciseId: Long, val name: String) : UndoPrompt {
        override val message: String get() = "$name added at the end"
    }

    /** "Warm-up added": Undo takes the ramp's sets back out. */
    data class Ramp(override val serial: Int, val setIds: List<Long>) : UndoPrompt {
        override val message: String get() = "Warm-up added"
    }
}

/** What a stepper moves. LOAD has its own entry dialog; the others share one. */
internal enum class FigureKind { LOAD, REPS, SECONDS, MINUTES, KM, ATTEMPTS }

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

/** How long Undo stays up after a set is logged or removed: long enough to read it and reach for it between sets. */
private const val UNDO_MS = 8_000L

/** How long the question about a logged set waits, once no rest bar is asking it, before it is dropped as stale. */
private const val EFFORT_ASK_MS = 90_000L

/** Four digits is more than any reps, seconds, minutes or attempts figure needs. */
private const val FIGURE_INPUT_MAX = 9_999

/** A stepper's figure: room for "102.5" between its glyphs, and no more. */
private val FIGURE_WIDTH = 56.dp

/** Every footer state is this tall, so the bar never shifts as its slot changes. */
private val FOOTER_HEIGHT = 76.dp

/** The rest's own row: the footer less its 12dp padding, the 6dp gap and the 3dp sweep. */
private val REST_ROW_HEIGHT = 43.dp

/** The same row while the effort chips show: the soft-ceiling line needs the room the sweep gap borrows otherwise. */
private val REST_ROW_HEIGHT_WITH_EFFORT = 52.dp

/** What the rest bar's effort question adds to the footer: its title line and 44dp chips. */
private val EFFORT_BLOCK_HEIGHT = 68.dp

/** The collapsed "rated" line keeps a full touch target for Change. */
private val EFFORT_DONE_HEIGHT = 44.dp

/** The room the log button takes at the end of the footer: the rest bar's chips stop short of it so a tap there can never rate a set. */
private val LOG_BUTTON_SLOT = 60.dp

private fun figuresFor(set: SessionSet, metric: ExerciseMetric, weighted: Boolean): List<Figure> {
    val worn = WornLoad.forLift(ProgramAnswersStore.kit, set.exerciseName, weighted, metric)
    fun load(): Figure {
        val kg = set.weightKg?.takeIf { it > 0.0 }
        return Figure(
            kind = FigureKind.LOAD,
            // A bodyweight movement reads BW; a weighted one with no load yet reads 0.
            value = kg?.let { Units.format(it) } ?: if (weighted) "0" else "BW",
            // A loaded lift with nothing on it says so, where the unit would sit.
            unit = if (kg != null) Units.name else if (weighted) "set load" else "",
            what = "load",
            minus = set.copy(weightKg = stepDownKg(set.weightKg, gridLb = loadGridLb(set.exerciseName), worn = worn)),
            plus = set.copy(weightKg = stepUpKg(set.weightKg, gridLb = loadGridLb(set.exerciseName), worn = worn)),
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

internal fun SessionSet.withTypedFigure(kind: FigureKind, n: Int): SessionSet = when (kind) {
    FigureKind.REPS, FigureKind.ATTEMPTS -> copy(reps = n)
    FigureKind.SECONDS -> copy(durationSec = n)
    FigureKind.MINUTES -> copy(durationSec = n * 60)
    FigureKind.LOAD, FigureKind.KM -> this
}

private fun FigureKind.typable(): Boolean = this != FigureKind.KM

/** "10 kg", or "BW" for a bodyweight set. */
internal fun kgLabel(kg: Double?, weighted: Boolean = false): String = Units.label(kg, weighted)

/** A set as its row reads: "10 kg × 6". */
internal fun setFigureText(set: SessionSet, metric: ExerciseMetric, weighted: Boolean): String = when (metric) {
    ExerciseMetric.REPS -> "${kgLabel(set.weightKg, weighted)} × ${set.reps}"
    ExerciseMetric.HOLD -> "${kgLabel(set.weightKg, weighted)} × ${set.durationSec ?: 0}s"
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
private fun repsAtLoad(figure: Int, kg: Double?, hold: Boolean, weighted: Boolean): String =
    "$figure${if (hold) "s" else ""} × ${kgLabel(kg, weighted)}"

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
        (if (metric.isStrength) repsAtLoad(if (hold) it.durationSec ?: it.reps else it.reps, it.weightKg, hold, exercise?.isWeighted == true)
        else exercise?.let { e -> lastLoggedFigure(it, e) })
            ?.let { figure -> figure + (it.rpe?.let { rpe -> " · ${Effort.value(rpe, EffortSetting.scale)}" } ?: "") }
    }
    val peakText = peak?.takeIf { metric.isStrength }?.let { repsAtLoad(it.reps, it.weightKg, hold, exercise?.isWeighted == true) }
    if (lastText == null && peakText == null) return null
    return buildAnnotatedString {
        if (lastText != null) append("Last $lastText")
        if (lastText != null && peakText != null) append(" · ")
        if (peakText != null) {
            append("${Words.peak.c} ")
            withStyle(SpanStyle(color = IronvellumColors.SovereignGold)) { append(peakText) }
        }
    }
}

/**
 * The logged sets that are a peak by [RepMax], the one rule the victory panel and the XP bonus also use: a set
 * must beat history and this exercise's earlier done sets today, so a repeat of set 2 as set 3 reads NEW PEAK
 * once. Activity work never scores strength.
 */
private fun newPeakSetIds(
    sets: List<SessionSet>,
    metric: ExerciseMetric,
    book: RepBook,
): Set<Long> {
    if (!metric.isStrength || sets.isEmpty()) return emptySet()
    return RepMax.blocks(sets).flatMap { block ->
        RepMax.hits(block, book[RepMax.key(block.first())].orEmpty(), metric == ExerciseMetric.HOLD)
    }.map { it.set.id }.toSet()
}

/** A folded exercise's second line, by how far along it is. */
internal fun foldedSubline(block: TrialBlock, metric: ExerciseMetric, weighted: Boolean): String {
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
            "$total/$total · best ${setFigureText(best, metric, weighted)}" + (best.rpe?.let { " · ${Effort.value(it, EffortSetting.scale)}" } ?: "")
        }
        done.isNotEmpty() -> "${done.size}/$total ${plural(total, "set", "sets")}"
        else -> when (metric) {
            ExerciseMetric.REPS -> "$total × ${first.reps}" + (first.weightKg?.takeIf { it > 0.0 }?.let { " · ${Units.withUnit(it)}" } ?: if (weighted) " · set a load" else "")
            ExerciseMetric.HOLD -> "$total × ${first.durationSec ?: 0}s" + (first.weightKg?.takeIf { it > 0.0 }?.let { " · ${Units.withUnit(it)}" } ?: "")
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
    /** The rite's name (or the trial's own): the page's identity, so it leads. */
    title: String,
    /** What the tick just earned, shown in the stats line for a moment: a slot of its own, so it never sits over anything. */
    gain: LiveGain?,
    motion: Boolean,
    onGainGone: (serial: Int) -> Unit,
    /** The list has scrolled under the header: a rule says where the header ends. */
    scrolled: Boolean,
    done: Int,
    total: Int,
    minutesLeft: Int?,
    startedAtMs: Long,
    /** XP the ticked sets have earned so far; the chip shows once there is some. */
    xp: Int,
    onBack: () -> Unit,
    onMuscles: () -> Unit,
    onLock: () -> Unit,
    onAbandon: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${Words.trial.c} in progress",
                    style = MaterialTheme.typography.labelSmall,
                    color = IronvellumColors.InkMuted,
                    maxLines = 1,
                )
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = IronvellumColors.Ink,
                    // One line, cut with an ellipsis: a wide XP chip never pushes it onto a second line.
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (xp > 0) {
                // The gap the title keeps from the chip; the chip itself never shrinks.
                Spacer(Modifier.width(12.dp))
                XpChip(xp)
            }
            NavChip(
                "BACK",
                Icons.AutoMirrored.Filled.ArrowBack,
                onClick = onBack,
                modifier = Modifier.semantics { onClick(label = "Leave, the ${Words.trial.s} keeps running", action = null) },
            )
            InkIconButton(onClick = onLock, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Outlined.Lock, contentDescription = "Lock the screen", tint = IronvellumColors.InkMuted)
            }
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
                        text = { Text("Abandon ${Words.trial.s}", color = IronvellumColors.DangerRed) },
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
            // One slot takes all the room the clock leaves, so a long lift name gives way, never the XP.
            Box(Modifier.weight(1f)) {
                if (gain != null) {
                    LiveGainTag(gain, motion, onGainGone)
                } else if (minutesLeft != null) {
                    Text(
                        " · ~$minutesLeft min left",
                        style = MaterialTheme.typography.labelMedium,
                        color = IronvellumColors.InkMuted,
                    )
                }
            }
            SessionElapsed(startedAtMs)
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(if (scrolled) IronvellumColors.Rune else Color.Transparent))
    }
}

/** The one exercise being worked: its figures, then its sets. The only boxed thing on the screen. */
@Composable
internal fun OpenExerciseCard(
    block: TrialBlock,
    metric: ExerciseMetric,
    weighted: Boolean,
    reference: AnnotatedString?,
    reason: String?,
    note: String?,
    lastNote: String?,
    onNote: (() -> Unit)?,
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
    onWarmup: (() -> Unit)?,
    /** This movement's rest switch (null where none applies, as in an amendment or for an activity). */
    restOff: Boolean?,
    onRestOff: (Boolean) -> Unit,
    onAddSet: () -> Unit,
    onRemoveSet: (SessionSet) -> Unit,
    onRemoveExercise: () -> Unit,
    onEdit: (SessionSet) -> Unit,
    onTypeLoad: (SessionSet) -> Unit,
    onTypeFigure: (SessionSet, FigureKind) -> Unit,
    onToggleEdit: (SessionSet) -> Unit,
    onSetKind: (SessionSet, SetKind) -> Unit,
    onUnlog: (SessionSet) -> Unit,
    modifier: Modifier = Modifier,
    /** The sealed trial's amend mode: any set can open for editing, and there is no live-only chrome. */
    amend: Boolean = false,
    onChangeExercise: (() -> Unit)? = null,
    onLog: (SessionSet) -> Unit = {},
    footer: (@Composable () -> Unit)? = null,
    onPlates: (() -> Unit)? = null,
    /** The logged set the effort strip sits under (live trial, right after a tick); null when none is being asked. */
    effortSetId: Long? = null,
    /** A pick on an effort strip, here or on an opened logged set: the rating, or null to clear it. */
    onRpe: (SessionSet, Int?) -> Unit = { _, _ -> },
    /** The live trial only: what each ticked set added to the XP tally, by set id. */
    xpBySet: Map<Long, Int> = emptyMap(),
    /** The live trial only: this exercise's superset (its number and the partner whose turn follows), and Unlink. */
    superset: SupersetLine? = null,
    onUnlink: () -> Unit = {},
    /** The live trial only: the next exercise, when "Superset with" it is on offer, and the link. */
    supersetWith: String? = null,
    onSuperset: (() -> Unit)? = null,
    /** The live trial only: the active timed set gets a countdown or a stopwatch. An amendment has none. */
    timed: Boolean = false,
    clocks: Map<Long, SetClock> = emptyMap(),
    timedAlert: RestAlert = RestAlert.DEFAULT,
    onClock: (setId: Long, SetClock?) -> Unit = { _, _ -> },
    /** Logs the active set with the seconds its countdown ended on. */
    onTimedLog: (seconds: Int) -> Unit = {},
    /** The live trial only: a muted line under a set still to log saying what it is for, by set id. */
    hints: Map<Long, String> = emptyMap(),
    /** "Make set 1 a top set", today only; null in an amendment, in a superset, with fewer than two sets or when the lift has a top set. */
    onMakeTopSet: (() -> Unit)? = null,
    /** "Add a drop to the last set", today only; null in an amendment or a superset. */
    onAddDrop: (() -> Unit)? = null,
    /** "End drops": takes out the drops still to do after the given set; null in an amendment. */
    onEndDrops: ((afterSetId: Long) -> Unit)? = null,
) {
    val first = block.first
    val shape = MaterialTheme.shapes.medium
    // Once working sets are being logged the warm-ups fold into one ticked row, which opens on a tap.
    var warmupsOpen by remember(first.exerciseId) { mutableStateOf(false) }
    val foldWarmups = !amend && !warmupsOpen && block.sets.any { it.done && !it.warmup } &&
        block.sets.none { it.warmup && it.id == editingSetId }
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
            onChangeExercise?.let { change ->
                InkChip("Change", clickLabel = "Change exercise", icon = Icons.Filled.SwapHoriz, onClick = change)
            }
            // In the header, not on a row of its own: with no last-time line beside it, a lone chip left a
            // card-wide empty row above the sets.
            onPlates?.let { InkChip("Plates", clickLabel = "Open the plate calculator", onClick = it) }
            ExerciseMenu(
                name = first.exerciseName,
                onMoveUp = if (canMoveUp) ({ onMove(true) }) else null,
                onMoveDown = if (canMoveDown) ({ onMove(false) }) else null,
                onModifiers = if (modifiersEditable) onEditModifiers else null,
                onEditLoad = onEditLoad,
                // Amending has its own Add set row under the sets.
                onWarmup = if (amend) null else onWarmup,
                onAddSet = if (amend) null else onAddSet,
                // Only a set not yet logged: removing a logged one loses its log.
                // An amendment may drop any set; Save is the commit.
                onRemoveLastSet = block.sets.last().takeIf { block.sets.size > 1 && (amend || !it.done) }?.let { last -> { onRemoveSet(last) } },
                onRemoveExercise = onRemoveExercise,
                restOff = restOff.takeIf { !amend },
                onRestOff = onRestOff,
                supersetWith = supersetWith.takeIf { !amend },
                onSuperset = onSuperset.takeIf { !amend },
                onMakeTopSet = onMakeTopSet.takeIf { !amend },
                onAddDrop = onAddDrop.takeIf { !amend },
            )
        }
        superset?.let { SupersetStrip(it, first.exerciseName, onUnlink) }
        Column(Modifier.padding(horizontal = 16.dp)) {
            if (reference != null) {
                Text(
                    reference,
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                )
            }
            // What the last trial that left a note on this exercise said.
            if (lastNote != null) {
                Text(
                    "Last time: $lastNote",
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
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
        // The superset strip's own height already sits above the first row.
        Spacer(Modifier.height(if (superset != null) 0.dp else 8.dp))
        block.sets.forEach { set ->
            if (set.warmup && foldWarmups) {
                if (set.id == block.sets.first { it.warmup }.id) {
                    WarmupFoldRow(block.sets.count { it.warmup }) { warmupsOpen = true }
                }
                return@forEach
            }
            key(set.id) {
                val state = when {
                    (set.done || set.warmup || amend) && set.id == editingSetId -> RowState.EDITING
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
                    onSetKind = { kind -> onSetKind(set, kind) },
                    onUnlog = { onUnlog(set) },
                    onRemove = { onRemoveSet(set) },
                    amend = amend,
                    onLog = { onLog(set) },
                    timed = timed,
                    clock = clocks[set.id],
                    timedAlert = timedAlert,
                    onClock = { onClock(set.id, it) },
                    onTimedLog = onTimedLog,
                    onRpe = { onRpe(set, it) },
                    xpGain = xpBySet[set.id]?.takeIf { it > 0 },
                )
                hints[set.id]?.takeIf { state == RowState.ACTIVE || state == RowState.UPCOMING }?.let { hint ->
                    Text(
                        hint,
                        style = MaterialTheme.typography.labelSmall,
                        color = IronvellumColors.InkMuted,
                        modifier = Modifier.fillMaxWidth().padding(start = 44.dp, end = 16.dp, bottom = 4.dp),
                    )
                }
                if (set.id == effortSetId && state == RowState.DONE) {
                    EffortStrip(
                        title = "How hard was set ${set.workingNumber(block.sets)}?",
                        rpe = set.rpe,
                        onPick = { onRpe(set, it) },
                    )
                }
            }
        }
        // A drop chain in hand: the drops follow straight on, and a tap ends them.
        val nextPending = block.sets.firstOrNull { it.isPending }
        if (onEndDrops != null && nextPending?.kind == SetKind.DROP) {
            block.sets.lastOrNull { it.setIndex < nextPending.setIndex && it.done }?.let { anchor ->
                DropStrip(first.exerciseName) { onEndDrops(anchor.id) }
            }
        }
        footer?.invoke()
        if (onNote != null) ExerciseNoteRow(first.exerciseName, note, onNote)
    }
}

/** The folded warm-ups of a lift whose working sets are under way: one ticked line, opening the sets on a tap. */
@Composable
private fun WarmupFoldRow(count: Int, onOpen: () -> Unit) {
    val text = "Warm-up · $count ${plural(count, "set", "sets")}"
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clickable(onClickLabel = "Show the warm-up sets", role = Role.Button, onClick = onOpen)
            .semantics { contentDescription = "$text, done" }
            .padding(start = 12.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("W", style = MaterialTheme.typography.bodySmall, fontFamily = ChakraPetch, color = IronvellumColors.InkMuted, textAlign = TextAlign.Center, modifier = Modifier.width(24.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.InkMuted, modifier = Modifier.weight(1f).padding(start = 8.dp))
        Icon(Icons.Filled.Check, contentDescription = null, tint = IronvellumColors.Emerald, modifier = Modifier.padding(end = 10.dp).size(16.dp))
        Text("▾", style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted)
    }
}

/** "Drops follow straight on" with End drops: the chain after a set, which asks no rest between its sets. */
@Composable
private fun DropStrip(name: String, onEnd: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Drops follow straight on, no rest",
            style = MaterialTheme.typography.labelSmall,
            color = SetKind.DROP.tint(),
            modifier = Modifier.weight(1f),
        )
        Box(
            Modifier
                .heightIn(min = 44.dp)
                .clickable(onClickLabel = "End the drops for $name", role = Role.Button, onClick = onEnd)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("End drops", style = MaterialTheme.typography.labelMedium, color = IronvellumColors.InkMuted)
        }
    }
}

/** This exercise's own note for the trial: one muted line, or a quiet invitation to write one. */
@Composable
private fun ExerciseNoteRow(name: String, note: String?, onClick: () -> Unit) {
    val label = if (note == null) "Add a note for $name" else "Edit note for $name"
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = label
                role = Role.Button
            }
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Outlined.EditNote, contentDescription = null, tint = IronvellumColors.InkMuted, modifier = Modifier.size(16.dp))
        Text(
            note ?: "Add a note",
            style = MaterialTheme.typography.bodySmall,
            color = IronvellumColors.InkMuted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/** What an open card says of its superset: which one it is, and the partner whose turn follows. */
internal class SupersetLine(val number: Int, val next: String?)

/** "Superset 1 · Barbell row next" under the exercise's name, with Unlink, which ends the whole group. */
@Composable
private fun SupersetStrip(line: SupersetLine, name: String, onUnlink: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp).offset(y = (-4).dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Filled.Link, contentDescription = null, tint = IronvellumColors.SystemGreen, modifier = Modifier.size(14.dp))
        Text(
            "Superset ${line.number}" + (line.next?.let { " · $it next" } ?: ""),
            style = MaterialTheme.typography.labelSmall,
            color = IronvellumColors.InkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box(
            Modifier
                .heightIn(min = 36.dp)
                .clickable(onClickLabel = "Unlink $name from its superset", role = Role.Button, onClick = onUnlink)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Unlink", style = MaterialTheme.typography.labelSmall, color = IronvellumColors.InkMuted)
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
    onWarmup: (() -> Unit)?,
    onAddSet: (() -> Unit)?,
    onRemoveLastSet: (() -> Unit)?,
    onRemoveExercise: () -> Unit,
    /** The movement's own rest switch: whether its rest is off, and flipping it; null for an activity. */
    restOff: Boolean? = null,
    onRestOff: (Boolean) -> Unit = {},
    /** "Superset with <supersetWith>", when the next exercise can be linked. */
    supersetWith: String? = null,
    onSuperset: (() -> Unit)? = null,
    /** Today-only set shapes: "Make set 1 a top set" and "Add a drop to the last set". */
    onMakeTopSet: (() -> Unit)? = null,
    onAddDrop: (() -> Unit)? = null,
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
            // Four groups, a rule between: the sets, the lift's own settings, where it sits, and the one way out.
            // The two set shapes say "today" in their names: they change this trial, never the rite.
            var groups = 0
            @Composable
            fun group(content: @Composable () -> Unit) {
                if (groups++ > 0) HorizontalDivider(color = IronvellumColors.Rune)
                content()
            }
            if (listOf(onAddSet, onRemoveLastSet, onWarmup, onMakeTopSet, onAddDrop).any { it != null }) group {
                onAddSet?.let { item("Add set", action = it) }
                onRemoveLastSet?.let { item("Remove last set", action = it) }
                onWarmup?.let { item("Warm up", action = it) }
                onMakeTopSet?.let { item("Make set 1 a top set (today)", action = it) }
                onAddDrop?.let { item("Add a drop to the last set (today)", action = it) }
            }
            if (listOf(onEditLoad, onModifiers, restOff).any { it != null }) group {
                onEditLoad?.let { item("Edit load", action = it) }
                onModifiers?.let { item("Edit modifiers", action = it) }
                restOff?.let { off -> item(if (off) "Turn rest timer on" else "Turn rest timer off") { onRestOff(!off) } }
            }
            if (onMoveUp != null || onMoveDown != null || (supersetWith != null && onSuperset != null)) group {
                onMoveUp?.let { item("Move up", action = it) }
                onMoveDown?.let { item("Move down", action = it) }
                if (supersetWith != null && onSuperset != null) item("Superset with $supersetWith", action = onSuperset)
            }
            group { item("Remove exercise", IronvellumColors.DangerRed, onRemoveExercise) }
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
    onSetKind: (SetKind) -> Unit,
    onUnlog: () -> Unit,
    onRemove: () -> Unit,
    amend: Boolean = false,
    onLog: () -> Unit = {},
    timed: Boolean = false,
    clock: SetClock? = null,
    timedAlert: RestAlert = RestAlert.DEFAULT,
    onClock: (SetClock?) -> Unit = {},
    onTimedLog: (seconds: Int) -> Unit = {},
    onRpe: (Int?) -> Unit = {},
    xpGain: Int? = null,
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
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                SetNumberCell(
                    label = label,
                    kind = set.kind,
                    onKind = onSetKind,
                    tone = IronvellumColors.Ink,
                    showHint = true,
                    numberStyle = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.width(24.dp),
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
            // A hold or duration counts down to its figure and a distance run has a stopwatch;
            // only on the set in hand, and only in the live trial.
            if (timed && state == RowState.ACTIVE && (SetClock.countsDown(metric) || SetClock.countsUp(metric))) {
                TimedSetControl(
                    metric = metric,
                    targetSec = set.durationSec ?: 0,
                    clock = clock,
                    alert = timedAlert,
                    onClock = onClock,
                    onLog = onTimedLog,
                    onFill = { seconds -> onEdit(set.copy(durationSec = seconds)) },
                    modifier = Modifier.padding(start = 36.dp, end = 4.dp),
                )
            }
            if (state == RowState.EDITING) {
                Row(Modifier.fillMaxWidth().padding(start = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                    // A warm-up was never logged, so there is nothing to un-log.
                    // An amendment can also open a set that was never logged.
                    if (!set.warmup) {
                        if (set.done) RowAction("Un-log", IronvellumColors.Ink, onUnlog) else RowAction("Log set", IronvellumColors.Ink, onLog)
                    }
                    RowAction("Remove", IronvellumColors.DangerRed, onRemove)
                    Spacer(Modifier.weight(1f))
                    RowAction("Close", IronvellumColors.InkMuted, onToggleEdit)
                }
                Row(Modifier.fillMaxWidth().padding(start = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                    RowAction(
                        if (set.warmup) "Count as a working set" else "Mark as warm-up",
                        IronvellumColors.Ink,
                    ) { onSetKind(if (set.warmup) SetKind.WORKING else SetKind.WARMUP) }
                }
                // A logged set can be rated, or re-rated, here: a rep set always (the rest bar asks those
                // by default), anything else while Settings asks for effort or the set already has one.
                if (set.done && !set.warmup && (metric == ExerciseMetric.REPS || EffortSetting.enabled || set.rpe != null)) {
                    EffortStrip(title = "How hard was set $label?", rpe = set.rpe, onPick = onRpe)
                }
            }
        }
        return
    }
    val done = state == RowState.DONE
    // A warm-up reads as a muted line like a logged set, and opens the same edit row.
    val editable = done || state == RowState.WARMUP || amend
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
            // The number column starts where the active row's does, so the numbers run in one line down the card.
            .padding(start = 12.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SetNumberCell(
            label = label,
            kind = set.kind,
            onKind = onSetKind,
            tone = tone,
            modifier = Modifier.width(24.dp),
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
                "NEW ${Words.peak.u}",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.SemiBold,
                color = IronvellumColors.SovereignGold,
            )
        }
        // How hard it was, muted at the right: "RPE 7" or "2 RIR". Shown even with the question off.
        if (done) {
            set.rpe?.let {
                Text(
                    Effort.value(it, EffortSetting.scale),
                    style = MaterialTheme.typography.labelSmall,
                    color = tone,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            // What this set added to the trial's XP; the header chip carries the running total.
            if (xpGain != null) {
                Text(
                    "+$xpGain XP",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = ChakraPetch,
                    fontWeight = FontWeight.SemiBold,
                    color = IronvellumColors.EmeraldDeep,
                    maxLines = 1,
                    modifier = Modifier
                        .padding(start = 10.dp)
                        .semantics { contentDescription = "$xpGain XP" },
                )
            }
            // The tick closes the row, as it does a folded exercise's.
            Icon(
                Icons.Filled.Check,
                contentDescription = "Logged",
                tint = IronvellumColors.Emerald,
                modifier = Modifier.padding(start = 10.dp).size(16.dp),
            )
        }
    }
}

/**
 * "How hard was set 2?" with the scale's chips under it: ≤5 6 7 8 9 10 on RPE, 5+ 4 3 2 1 0 on RIR, one
 * tap. Skipping costs nothing: nothing here blocks the next set, and tapping the chosen chip again clears
 * it. The line beneath reads the chosen chip ("RPE 8: about two reps left"), or what the scale means.
 */
@Composable
internal fun EffortStrip(title: String, rpe: Int?, onPick: (Int?) -> Unit, modifier: Modifier = Modifier) {
    val scale = EffortSetting.scale
    Column(
        modifier
            .fillMaxWidth()
            .background(IronvellumColors.Ink.copy(alpha = 0.04f))
            .padding(start = 44.dp, end = 16.dp, bottom = 8.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(scale.label, style = MaterialTheme.typography.labelSmall, color = IronvellumColors.InkMuted, letterSpacing = 2.sp)
        }
        val shape = MaterialTheme.shapes.small
        Row(Modifier.fillMaxWidth().selectableGroup().semantics { contentDescription = "${scale.label} for ${title.removePrefix("How hard was ").removeSuffix("?")}" }) {
            Effort.CHIPS.forEach { value ->
                val chosen = rpe == value
                Box(
                    Modifier
                        .weight(1f)
                        .height(44.dp)
                        .selectable(selected = chosen, role = Role.RadioButton, onClick = { onPick(if (chosen) null else value) })
                        .padding(horizontal = 2.dp, vertical = 4.dp)
                        .clip(shape)
                        .background(if (chosen) IronvellumColors.Emerald.copy(alpha = 0.16f) else Color.Transparent)
                        .inkBorder(if (chosen) IronvellumColors.Emerald else IronvellumColors.Rune, shape, 1.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        Effort.chip(value, scale),
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = ChakraPetch,
                        fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal,
                        color = if (chosen) IronvellumColors.Ink else IronvellumColors.InkMuted,
                    )
                }
            }
        }
        Text(
            rpe?.let { Effort.caption(it, scale) } ?: Effort.hint(scale),
            style = MaterialTheme.typography.labelSmall,
            color = IronvellumColors.InkMuted,
            modifier = Modifier.padding(top = 2.dp),
        )
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
                .then(if (onType != null) Modifier.clickable(onClickLabel = "Type ${figure.what}", role = Role.Button, onClick = onType) else Modifier),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // The rule sits right under the digits and is only as wide as they are: the mark of a field that can be typed, not only stepped.
            Column(Modifier.width(IntrinsicSize.Min), horizontalAlignment = Alignment.CenterHorizontally) {
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
                if (onType != null) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(IronvellumColors.InkMuted.copy(alpha = 0.55f)))
                }
            }
            if (figure.unit.isNotEmpty()) {
                Text(figure.unit, style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = IronvellumColors.InkMuted, modifier = Modifier.padding(top = 2.dp))
            }
        }
        StepButton("+", "Increase ${figure.what}") { onEdit(figure.plus) }
    }
}

@Composable
private fun StepButton(symbol: String, description: String, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Box(
        Modifier
            .size(width = 44.dp, height = 48.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = InkPressIndication,
                role = Role.Button,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onClick()
                },
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
internal fun RowAction(label: String, color: Color, onClick: () -> Unit) {
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
internal fun FoldedExerciseRow(
    name: String,
    subline: String,
    done: Int,
    total: Int,
    hasNote: Boolean,
    onOpen: () -> Unit,
) {
    val complete = total > 0 && done == total
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clickable(onClickLabel = if (hasNote) "Open $name. Has a note." else "Open $name", onClick = onOpen)
            // Starts where an open card's title does (the card's own 16dp inside the screen's), not further in.
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
        if (hasNote) {
            Icon(
                Icons.Outlined.EditNote,
                contentDescription = "Has a note",
                tint = IronvellumColors.InkMuted,
                modifier = Modifier.padding(end = 10.dp).size(14.dp),
            )
        }
        if (complete) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "Done",
                tint = IronvellumColors.Emerald,
                modifier = Modifier.padding(end = 6.dp).size(16.dp),
            )
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
internal fun FolderRule() {
    Box(Modifier.fillMaxWidth().padding(start = 16.dp).height(1.dp).background(IronvellumColors.Rune))
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
        EndRow(Icons.Outlined.Edit, "Name this ${Words.trial.s}", nameValue, nameSet, onName)
        FolderRuleFull()
        EndRow(Icons.Outlined.EditNote, "Add a note", noteValue ?: "Share it, or keep it private", noteValue != null, onNote)
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
    modifier: Modifier = Modifier,
    allLogged: Boolean,
    rest: RestTimer?,
    nextSetNumber: Int?,
    nextLine: String?,
    /** Mid-round in a superset: this set does not start a rest, and the next one is not waiting on one. */
    noRestYet: Boolean,
    onLog: () -> Unit,
    onExtend: () -> Unit,
    onShorten: () -> Unit,
    onSkip: () -> Unit,
    onSealed: () -> Unit,
    slideReset: Int,
    /** The set just logged, whose effort the rest bar asks; null when it is not asking. */
    effortSet: SessionSet? = null,
    effortLabel: String = "",
    onRate: (Int?) -> Unit = {},
) {
    val now = restNow(rest)
    Box(
        modifier
            .fillMaxWidth()
            // Every state is at least this tall; the rest grows only while its effort chips show.
            .heightIn(min = FOOTER_HEIGHT)
            .background(IronvellumColors.VaultHigh)
            .drawBehind { drawRect(IronvellumColors.Rune, size = Size(size.width, 1.dp.toPx())) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            allLogged -> SlideToSeal(label = "Slide to ${Words.seal.s}", onSealed = onSealed, resetKey = slideReset)
            rest != null && !rest.isOver(now) -> RestStrip(rest, now, nextSetNumber, onExtend, onShorten, onSkip, effortSet, effortLabel, onRate)
            nextLine != null && nextSetNumber != null -> NextSetStrip(nextSetNumber, nextLine, noRestYet, onLog)
            // No sets at all: an open trial that has not begun. Nothing to log or seal yet.
            else -> SlideToSeal(label = "Log a set to ${Words.seal.s} the ${Words.trial.s}", onSealed = onSealed, enabled = false)
        }
    }
}

/** The rest between sets, counting down, with its moves: a little less, a little more, or none at all. */
@Composable
private fun RestStrip(
    timer: RestTimer,
    now: Long,
    nextSetNumber: Int?,
    onExtend: () -> Unit,
    onShorten: () -> Unit,
    onSkip: () -> Unit,
    effortSet: SessionSet? = null,
    effortLabel: String = "",
    onRate: (Int?) -> Unit = {},
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(if (effortSet != null) REST_ROW_HEIGHT_WITH_EFFORT else REST_ROW_HEIGHT), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        timer.label(now),
                        fontFamily = ChakraPetch,
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp,
                        lineHeight = 28.sp,
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
                }
                // The soft ceiling: past it more rest adds nothing; +15 still extends.
                if (timer.maxSeconds > 0) {
                    Text(
                        "Max suggested ${RestTimer.clock(timer.maxSeconds)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = IronvellumColors.InkMuted,
                        lineHeight = 12.sp,
                        maxLines = 1,
                    )
                }
            }
            FooterAction("\u2212${RestTimer.SHORTEN_SECONDS}", IronvellumColors.Ink, onShorten)
            FooterAction("+${RestTimer.EXTEND_SECONDS}", IronvellumColors.Ink, onExtend)
            FooterAction("Skip", IronvellumColors.InkMuted, onSkip)
        }
        if (effortSet != null) RestEffort(effortSet, effortLabel, onRate)
        // The gap above the sweep keeps the soft ceiling's line off the bar.
        Spacer(Modifier.height(6.dp))
        // The sweep: what is left of the rest.
        val left = (timer.remainingMs(now).toFloat() / timer.totalMs.coerceAtLeast(1L)).coerceIn(0f, 1f)
        Box(Modifier.fillMaxWidth().height(3.dp).background(IronvellumColors.Rune)) {
            Box(Modifier.fillMaxWidth(left).fillMaxHeight().background(IronvellumColors.InkMuted))
        }
    }
}

/**
 * The rest bar's effort question: "Set 2: reps left?" with one-tap chips on the lifter's own scale
 * (RIR 0 1 2 3 4+, or RPE ≤6 7 8 9 10). A tap rates the set just logged and the chips collapse to a quiet
 * "RIR 2 ✓" with Change; the block keeps its height either way and the rest keeps counting.
 */
@Composable
private fun RestEffort(set: SessionSet, label: String, onRate: (Int?) -> Unit) {
    val scale = EffortSetting.scale
    var reopened by remember(set.id) { mutableStateOf(false) }
    val rpe = set.rpe
    // Grows and shrinks in place between the question and the quiet "rated" line: no empty band left behind.
    Box(Modifier.fillMaxWidth().animateContentSize()) {
        if (rpe != null && !reopened) {
            Row(
                Modifier.fillMaxWidth().height(EFFORT_DONE_HEIGHT),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "$label \u00B7 ${Effort.value(rpe, scale)} \u2713",
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = "$label rated ${Effort.caption(rpe, scale)}" },
                )
                Text(
                    "Change",
                    style = MaterialTheme.typography.labelMedium,
                    color = IronvellumColors.InkMuted,
                    modifier = Modifier
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .clickable(onClickLabel = "Change the rating", role = Role.Button) { reopened = true }
                        .padding(horizontal = 8.dp)
                        .wrapContentHeight(Alignment.CenterVertically),
                )
            }
        } else {
            Column(Modifier.fillMaxWidth().height(EFFORT_BLOCK_HEIGHT)) {
                Row(Modifier.fillMaxWidth().height(24.dp).padding(end = LOG_BUTTON_SLOT), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (scale == EffortScale.RIR) "$label: reps left?" else "$label: how hard?",
                        style = MaterialTheme.typography.bodySmall,
                        color = IronvellumColors.Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(scale.label, style = MaterialTheme.typography.labelSmall, color = IronvellumColors.InkMuted, letterSpacing = 2.sp)
                }
                val shape = MaterialTheme.shapes.small
                // Stops short of the end of the bar, where the log button sits between sets: a tap meant to log
                // the next set must never land on a chip and rate the one before.
                Row(Modifier.fillMaxWidth().padding(end = LOG_BUTTON_SLOT).selectableGroup()) {
                    restEffortChips(scale).forEach { (value, face) ->
                        val chosen = rpe == value
                        Box(
                            Modifier
                                .weight(1f)
                                .height(44.dp)
                                .selectable(
                                    selected = chosen,
                                    role = Role.RadioButton,
                                    onClick = {
                                        reopened = false
                                        onRate(if (chosen) null else value)
                                    },
                                )
                                .semantics { contentDescription = restEffortSpoken(value, scale) }
                                .padding(horizontal = 2.dp, vertical = 4.dp)
                                .clip(shape)
                                .background(if (chosen) IronvellumColors.Emerald.copy(alpha = 0.16f) else Color.Transparent)
                                .inkBorder(if (chosen) IronvellumColors.Emerald else IronvellumColors.Rune, shape, 1.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                face,
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = ChakraPetch,
                                fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal,
                                color = if (chosen) IronvellumColors.Ink else IronvellumColors.InkMuted,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The rest bar's chips, left to right: the stored RPE and what the chip says. The same six as the strip under a row, [Effort.CHIPS], so one range reads everywhere. */
internal fun restEffortChips(scale: EffortScale): List<Pair<Int, String>> = Effort.CHIPS.map { it to Effort.chip(it, scale) }

/**
 * The set the effort question is about: the one just logged. A set logged some other way than the log button (the
 * lock screen, the notification) takes the question over from the older one, so a stale prompt never lingers over
 * a set that is no longer the latest, and a set taken back has none. [seen] is the logged ids the last time this
 * ran (null on the first read, a trial coming back: nothing is taken over then); [order] is the sets in trial order.
 */
internal fun effortTarget(seen: Set<Long>?, now: Set<Long>, current: Long?, order: List<Long>): Long? {
    if (current != null && current !in now) return null
    if (seen == null) return current
    return order.lastOrNull { it in now && it !in seen } ?: current
}

/** Where the live trial asks how hard the set just logged was. */
internal enum class EffortAsk { REST_BAR, UNDER_ROW, NONE }

/**
 * The rest bar asks while the rest counts down, for a set counted in reps, whatever Settings says.
 * Once no rest bar shows (the last set, the rest ran out, no rest timer) a rep set is still asked
 * under its row; any other set only when Settings asks for effort.
 */
internal fun effortAsk(restRunning: Boolean, allLogged: Boolean, reps: Boolean, settingOn: Boolean): EffortAsk = when {
    restRunning && !allLogged && reps -> EffortAsk.REST_BAR
    reps || settingOn -> EffortAsk.UNDER_ROW
    else -> EffortAsk.NONE
}

private fun restEffortSpoken(rpe: Int, scale: EffortScale): String = when (scale) {
    EffortScale.RIR -> if (rpe <= Effort.MIN) "5 or more reps in reserve" else "${Effort.rir(rpe)} reps in reserve"
    EffortScale.RPE -> if (rpe <= Effort.MIN) "RPE 5 or less" else "RPE $rpe"
}

/** What a tick just earned, floating briefly: the lift and its XP. */
private data class LiveGain(val serial: Int, val name: String, val amount: Int)

/**
 * " · Pull-up +24 XP" in the header's stats line for a moment after a tick, in the place the minutes left take
 * back afterwards. It has a slot of its own, so it is never laid over a card or a notice, and nothing moves;
 * the XP chip's live region is what a screen reader announces. With animations off it shows and goes without fading.
 */
@Composable
private fun LiveGainTag(gain: LiveGain, motion: Boolean, onGone: (serial: Int) -> Unit, modifier: Modifier = Modifier) {
    val alpha = remember(gain.serial) { Animatable(1f) }
    LaunchedEffect(gain.serial) {
        delay(1_100)
        if (motion) alpha.animateTo(0f, tween(500))
        else delay(500)
        onGone(gain.serial)
    }
    Row(
        modifier
            .graphicsLayer { this.alpha = alpha.value }
            .clearAndSetSemantics { },
    ) {
        val style = MaterialTheme.typography.labelMedium
        Text(
            " · ${gain.name}",
            style = style,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.SemiBold,
            color = IronvellumColors.Emerald,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(
            " +${gain.amount} XP",
            style = style,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.SemiBold,
            color = IronvellumColors.Emerald,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** "+164 XP" in the trial header: what the ticked sets have earned so far. A polite live region, so it is announced as it grows. */
@Composable
private fun XpChip(xp: Int) {
    val shape = MaterialTheme.shapes.small
    Text(
        "+$xp XP",
        style = MaterialTheme.typography.labelLarge,
        fontFamily = ChakraPetch,
        fontWeight = FontWeight.SemiBold,
        color = IronvellumColors.Emerald,
        maxLines = 1,
        modifier = Modifier
            .padding(end = 8.dp)
            .clip(shape)
            .background(IronvellumColors.Emerald.copy(alpha = 0.10f))
            .inkBorder(IronvellumColors.EmeraldDeep, shape, 1.dp)
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .semantics {
                contentDescription = "$xp XP earned so far"
                liveRegion = LiveRegionMode.Polite
            },
    )
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
private fun NextSetStrip(setNumber: Int, line: String, noRestYet: Boolean, onLog: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                if (noRestYet) "NEXT · NO REST YET" else "NEXT",
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
internal fun restNow(timer: RestTimer?): Long {
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
internal fun FigureEntryDialog(
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
        title = { Text("Name this ${Words.trial.s}") },
        text = {
            OutlinedTextField(
                shape = MaterialTheme.shapes.small,
                value = draft,
                onValueChange = { draft = it.take(TITLE_CAP) },
                singleLine = true,
                label = {
                    FieldLabel(
                        icon = { Icon(Icons.Outlined.Public, null, Modifier.size(14.dp), tint = IronvellumColors.InkMuted) },
                        text = if (signedIn) "Name · optional, shown in ${Words.tidings.t}" else "Name · optional",
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
 * One exercise's note for this trial, in a sheet of its own. It stays on the
 * device; the note the last trial left on the same exercise sits beneath the
 * field. Saved on Save, trimmed; a blank note clears it.
 */
@Composable
private fun ExerciseNoteDialog(
    name: String,
    current: String,
    lastNote: String?,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by rememberSaveable { mutableStateOf(current) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    IronvellumDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(name)
                Text("Note for this ${Words.trial.s}", style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    shape = MaterialTheme.shapes.small,
                    value = draft,
                    onValueChange = { draft = it.take(EXERCISE_NOTE_MAX) },
                    minLines = 4,
                    trailingIcon = { CharCounter(draft.length, EXERCISE_NOTE_MAX, IronvellumColors.SystemGreen) },
                    colors = fieldColors(accent = IronvellumColors.SystemGreen, unfocusedBorder = IronvellumColors.Rune),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus)
                        .semantics { contentDescription = "Note for $name" },
                )
                if (lastNote != null) {
                    Text("Last time: $lastNote", style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted)
                }
            }
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
            Column {
                OutlinedTextField(
                    shape = MaterialTheme.shapes.small,
                    value = pub,
                    onValueChange = { pub = it.take(PUBLIC_NOTE_CAP) },
                    minLines = 2,
                    label = {
                        FieldLabel(
                            icon = { Icon(Icons.Outlined.Public, null, Modifier.size(14.dp), tint = IronvellumColors.SystemGreen) },
                            text = if (signedIn) "Public note · every ${Words.lifter.t} in ${Words.tidings.t} can read this" else "Note · shared with ${Words.ally.pl} once you sign in",
                            color = IronvellumColors.SystemGreen,
                        )
                    },
                    placeholder = { Text("How did the ${Words.trial.s} go? Share it…", style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted) },
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
    current: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var picked by remember(exerciseName) { mutableStateOf(current) }
    // Every token already set stays visible and removable, fitting or not
    // (modifierChips flags it): a legacy "banded" is never dropped behind his back.
    val weighted = modifierTokens(picked).any { it.equals(WEIGHTED_MODIFIER, ignoreCase = true) }
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
                ModifierChipGrid(
                    chips = modifierChips(applicable, picked),
                    onToggle = { picked = toggleModifier(picked, it) },
                )
            }
        },
        confirmButton = { IronvellumButton("Save", onClick = { onConfirm(picked) }) },
        dismissButton = { IronvellumButton("Cancel", quiet = true, onClick = onDismiss) },
    )
}

/** A field's label and its "before → after" figures, as the update dialog lists them. */
private fun fieldLine(change: RoutineUpdate.Change, field: RoutineUpdate.Field, weighted: Boolean): Pair<String, String> {
    val (before, after) = change
    val unit = if (change.isHold) "s" else ""
    return when (field) {
        RoutineUpdate.Field.SETS -> "Sets" to "${before.targetSets} → ${after.targetSets}"
        RoutineUpdate.Field.REPS ->
            (if (change.isHold) "Hold" else "Reps") to "${before.targetReps}$unit → ${after.targetReps}$unit"
        // "60 kg", spaced like every other load on the screen (formatKg's "60kg" is the compact rite-row form).
        RoutineUpdate.Field.LOAD -> "Load" to "${Units.label(before.targetWeightKg, weighted)} → ${Units.label(after.targetWeightKg, weighted)}"
        RoutineUpdate.Field.MODIFIERS ->
            "Modifiers" to "${before.modifiers.ifBlank { "None" }} → ${after.modifiers.ifBlank { "None" }}"
        RoutineUpdate.Field.SCHEME ->
            "Sets" to "${SchemeText.clause(before.scheme, before.targetSets)} → ${SchemeText.clause(after.scheme, after.targetSets)}"
    }
}

/**
 * One tickable line of the rite offer: the set rows' own tick box, a label
 * and the figures it would write.
 */
@Composable
private fun ToggleLine(
    on: Boolean,
    label: String,
    figures: String,
    labelWidth: androidx.compose.ui.unit.Dp = 76.dp,
    onToggle: () -> Unit,
) {
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
            .toggleable(value = on, role = Role.Checkbox) { onToggle() }
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
            modifier = Modifier.width(labelWidth),
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

/**
 * The general warm-up before the first set: tick it done, skip it, or tap the
 * collapsed line to open it again. It is a reminder, never a set.
 */
@Composable
private fun WarmupRow(
    /** The general reminder; null when that setting is off and only the rite's own warm-up exercises show. */
    text: String?,
    /** The rite's warm-up exercises, each tickable. */
    items: List<WarmupStep.Item>,
    ticked: Set<Long>,
    state: WarmupStep.State,
    onTick: (Long) -> Unit,
    onAnswer: (WarmupStep.State) -> Unit,
) {
    if (state != WarmupStep.State.OPEN) {
        Text(
            if (state == WarmupStep.State.DONE) "Warmed up" else "Warm-up skipped",
            style = MaterialTheme.typography.bodySmall,
            color = IronvellumColors.InkMuted,
            modifier = Modifier
                .padding(top = 12.dp)
                .clickable(role = Role.Button, onClickLabel = "Open the warm-up again") { onAnswer(WarmupStep.State.OPEN) }
                .padding(vertical = 8.dp),
        )
        return
    }
    Column(
        Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .background(IronvellumColors.Ink.copy(alpha = 0.04f))
            .drawBehind { drawRect(IronvellumColors.Emerald, size = Size(2.dp.toPx(), size.height)) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            "WARM UP",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            letterSpacing = 1.sp,
            color = IronvellumColors.InkMuted,
        )
        if (text != null) Text(text, style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.Ink)
        items.forEach { item ->
            val on = item.exerciseId in ticked
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .toggleable(value = on, role = Role.Checkbox, onValueChange = { onTick(item.exerciseId) }),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Checkbox(
                    checked = on,
                    onCheckedChange = null,
                    colors = CheckboxDefaults.colors(checkedColor = IronvellumColors.Emerald, uncheckedColor = IronvellumColors.InkMuted),
                )
                Text(
                    item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (on) IronvellumColors.InkMuted else IronvellumColors.Ink,
                    modifier = Modifier.weight(1f),
                )
                Text(item.line(::formatKg), style = MaterialTheme.typography.labelMedium, color = IronvellumColors.InkMuted)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IronvellumButton("Done", onClick = { onAnswer(WarmupStep.State.DONE) }, modifier = Modifier.weight(1f))
            IronvellumButton("Skip", quiet = true, onClick = { onAnswer(WarmupStep.State.SKIPPED) }, modifier = Modifier.weight(1f))
        }
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
    weightedOf: (Long) -> Boolean,
    onUpdate: (List<RoutineUpdate.Change>, List<RiteSwap.Swap>, List<RoutineUpdate.Addition>, List<RoutineUpdate.Link>) -> Unit,
    onKeep: () -> Unit,
) {
    var ticked by remember(offer) {
        mutableStateOf(offer.changes.associateWith { it.defaultTicks() })
    }
    // Entry ids of the swaps to keep; none to start with, so a swap is "just this once" until chosen.
    var kept by remember(offer) { mutableStateOf(emptySet<Long>()) }
    // Exercises added and superset links made today: one-offs are common, so both start unticked.
    var addedKept by remember(offer) { mutableStateOf(emptySet<Long>()) }
    var linksKept by remember(offer) { mutableStateOf(emptySet<RoutineUpdate.Link>()) }
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
                    if (offer.changes.isEmpty()) {
                        if (offer.swaps.isNotEmpty()) "Keep today's swaps in your ${Words.rite.s}?" else "Keep today's changes in your ${Words.rite.s}?"
                    } else if (shortDay) {
                        "Unticked sets don't lower the set count. Tick the changes to keep."
                    } else {
                        "Bring your ${Words.rite.s} in line with today's ${Words.trial.s}?"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                )
            }
        },
        text = {
            Column {
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
                        val (label, figures) = fieldLine(change, field, weightedOf(change.before.exerciseId))
                        ToggleLine(on, label, figures) {
                            ticked = ticked + (change to if (on) accepted - field else accepted + field)
                        }
                    }
                }
                offer.swaps.forEach { swap ->
                    val on = swap.entry.id in kept
                    Spacer(Modifier.height(6.dp))
                    ToggleLine(
                        on = on,
                        label = if (on) "Keep in ${Words.rite.s}" else "Just this once",
                        figures = swap.line,
                        labelWidth = 104.dp,
                    ) { kept = if (on) kept - swap.entry.id else kept + swap.entry.id }
                }
                offer.additions.forEach { add ->
                    val on = add.entry.exerciseId in addedKept
                    Spacer(Modifier.height(6.dp))
                    ToggleLine(
                        on = on,
                        label = if (on) "Add to ${Words.rite.s}" else "Just this once",
                        figures = "${add.line} · " + SchemeText.figure(
                            add.entry.scheme, add.entry.targetSets, add.entry.targetReps, add.entry.targetWeightKg,
                            load = { Units.label(it, true) }, hold = add.isHold,
                        ),
                        labelWidth = 104.dp,
                    ) { addedKept = if (on) addedKept - add.entry.exerciseId else addedKept + add.entry.exerciseId }
                }
                offer.links.forEach { link ->
                    // A link to an added lift is only a link once that lift is kept.
                    val needs = link.exerciseIds.filter { id -> offer.additions.any { it.entry.exerciseId == id } }
                    val ready = needs.all { it in addedKept }
                    val on = link in linksKept && ready
                    Spacer(Modifier.height(6.dp))
                    ToggleLine(
                        on = on,
                        label = if (on) "Link in ${Words.rite.s}" else "Just this once",
                        figures = "Superset: ${link.line}" + if (ready) "" else " (needs the added lift)",
                        labelWidth = 104.dp,
                    ) { if (ready) linksKept = if (on) linksKept - link else linksKept + link }
                }
            }
        },
        // Stacked full width on purpose: side by side the two labels wrapped
        // into a ragged pair at 360dp.
        confirmButton = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Preset order, whatever order the toggles were flipped in.
                val narrowed = offer.changes.mapNotNull { it.only(ticked[it].orEmpty()) }
                val keptSwaps = offer.swaps.filter { it.entry.id in kept }
                val keptAdditions = offer.additions.filter { it.entry.exerciseId in addedKept }
                val keptLinks = offer.links.filter { link ->
                    link in linksKept && link.exerciseIds.all { id ->
                        offer.additions.none { it.entry.exerciseId == id } || id in addedKept
                    }
                }
                IronvellumButton(
                    "Update ${Words.rite.s}",
                    enabled = narrowed.isNotEmpty() || keptSwaps.isNotEmpty() || keptAdditions.isNotEmpty() || keptLinks.isNotEmpty(),
                    onClick = { onUpdate(narrowed, keptSwaps, keptAdditions, keptLinks) },
                    modifier = Modifier.fillMaxWidth(),
                )
                IronvellumButton("Keep ${Words.rite.s}", quiet = true, onClick = onKeep, modifier = Modifier.fillMaxWidth())
            }
        },
    )
}
