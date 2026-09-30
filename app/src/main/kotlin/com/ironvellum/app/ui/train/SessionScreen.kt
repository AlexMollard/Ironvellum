package com.ironvellum.app.ui.train

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.core.content.ContextCompat
import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import com.ironvellum.app.WorkoutSessionService
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.ui.draw.alpha
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
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
import androidx.compose.ui.graphics.graphicsLayer
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
import com.ironvellum.app.domain.SessionSet
import com.ironvellum.app.domain.SetRecords
import com.ironvellum.app.domain.Sex
import com.ironvellum.app.domain.StrengthIndex
import com.ironvellum.app.domain.WorkoutSession
import com.ironvellum.app.domain.WorkoutShare
import com.ironvellum.app.domain.Xp
import com.ironvellum.app.ui.components.Achievement
import com.ironvellum.app.ui.components.AchievementOverlay
import com.ironvellum.app.ui.components.ShareCardDialog
import com.ironvellum.app.ui.components.ExerciseInfoDialog
import com.ironvellum.app.ui.components.ExercisePickerSheet
import com.ironvellum.app.ui.components.lastLoggedLine
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.components.formatBodyValue
import com.ironvellum.app.ui.components.formatDate
import com.ironvellum.app.ui.components.plural
import com.ironvellum.app.ui.launchGuarded
import com.ironvellum.app.ui.ironvellumRepository
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumTracking
import com.ironvellum.app.ui.theme.inkBorder
import com.ironvellum.app.ui.theme.inkDot
import com.ironvellum.app.ui.theme.IronvellumColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
import com.ironvellum.app.domain.MuscleMap
import com.ironvellum.app.ui.program.RiteMusclesDialog
import com.ironvellum.app.ui.program.ShareLevel
import com.ironvellum.app.ui.program.musclesAt

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

    /** How far the finish has got: celebrate first, then ask about the routine, then leave. */
    enum class Finish { VICTORY, AWARDS, ROUTINE }

    /**
     * The completion result and the finish stage. Held here, not in the
     * screen, so a rotation keeps the victory; the session is completed and
     * paid before either is set.
     */
    private val _completion = MutableStateFlow<Repository.CompletionResult?>(null)
    val completion: StateFlow<Repository.CompletionResult?> = _completion
    private val _finish = MutableStateFlow(Finish.VICTORY)
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

    fun updateSet(setId: Long, reps: Int, weightKg: Double?, done: Boolean) {
        viewModelScope.launch { repo.updateSet(setId, reps, weightKg, done) }
    }

    /** A hold's figure is seconds; writing it as reps is the bug this replaces. */
    fun updateHoldSet(setId: Long, seconds: Int, weightKg: Double?, done: Boolean) {
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
        viewModelScope.launch { repo.updateActivitySet(setId, reps, durationSec, distanceM, grade, weightKg, done) }
    }

    fun addSet(exerciseId: Long, reps: Int, weightKg: Double?, modifiers: String, durationSec: Int? = null) {
        viewModelScope.launch {
            repo.addExtraSet(sessionId, exerciseId, reps, weightKg, modifiers, durationSec)
        }
    }

    fun removeSet(setId: Long) {
        viewModelScope.launch { repo.removeSet(setId) }
    }

    /** Drops a whole exercise from the live trial: the ✕ on a block's only set. */
    fun removeExercise(exerciseId: Long) {
        viewModelScope.launchGuarded("remove exercise") { repo.removeSessionExercise(sessionId, exerciseId) }
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
        viewModelScope.launch {
            runCatching { repo.completeSession(sessionId) }
                .onSuccess {
                    // Completion is the moment a daily user's work becomes
                    // feed/leaderboard-visible; don't wait for a manual push.
                    CloudSyncWorker.pushNow(appContext)
                    // Asked after the XP is banked: the answer can never
                    // change what the session paid.
                    _routineUpdate.value = runCatching { repo.routineUpdateFor(sessionId) }.getOrNull()
                    _completion.value = it
                }
                .onFailure { _claiming.value = false }
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
    val claiming by viewModel.claiming.collectAsStateWithLifecycle()
    val lastLogged by viewModel.lastLogged.collectAsStateWithLifecycle()
    var confirmAbandon by remember { mutableStateOf(false) }
    var confirmClaim by remember { mutableStateOf(false) }
    var showExercisePicker by remember { mutableStateOf(false) }
    var editModifiersFor by remember { mutableStateOf<Long?>(null) }
    var showRiteMuscles by remember { mutableStateOf(false) }
    var infoFor by remember { mutableStateOf<Long?>(null) }
    var editLoadFor by remember { mutableStateOf<SessionSet?>(null) }

    // One route per metric, each passing the set's current values for the
    // columns it does not own, exactly as the row's own callbacks do.
    fun applyLoad(set: SessionSet, kg: Double?) {
        val metric = exercises.firstOrNull { it.id == set.exerciseId }?.metric ?: ExerciseMetric.REPS
        when {
            metric == ExerciseMetric.HOLD -> viewModel.updateHoldSet(set.id, set.durationSec ?: 0, kg, set.done)
            metric.isStrength -> viewModel.updateSet(set.id, set.reps, kg, set.done)
            else -> viewModel.updateActivitySet(set.id, set.reps, set.durationSec, set.distanceM, set.grade, kg, set.done)
        }
    }

    // The lock-screen companion. The service watches the database and stops
    // itself when the session completes or is abandoned - the screen only
    // has to announce that the session is the live one.
    val screenContext = LocalContext.current
    // Android 13+ posts nothing without POST_NOTIFICATIONS, and before this
    // only the reminder toggle asked for it, so most lifters never saw the
    // trial notification. Asked ONCE, the first time a trial opens; a refusal
    // is final here (Settings can still grant it), so there is no nagging.
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
        LaunchedEffect(session.id) { onExit() }
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        Text(
            "TRIAL IN PROGRESS",
            style = MaterialTheme.typography.labelLarge,
            fontFamily = ChakraPetch,
            color = IronvellumColors.SystemGreen,
            letterSpacing = 6.sp,
        )
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                // The whole trial's muscles, from the rows as they stand now.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        session.label,
                        style = MaterialTheme.typography.headlineSmall,
                        color = IronvellumColors.EmeraldBright,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    IconButton(onClick = { showRiteMuscles = true }, modifier = Modifier.size(44.dp)) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = "Muscles in ${session.label}",
                            tint = IronvellumColors.InkMuted,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Text(
                    "Started ${formatDate(session.startedAtMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = IronvellumColors.InkMuted,
                )
            }
            Text(
                "ABANDON",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = ChakraPetch,
                color = IronvellumColors.DangerRed,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.extraSmall)
                    .inkBorder(IronvellumColors.DangerRed.copy(alpha = 0.6f), MaterialTheme.shapes.extraSmall, 1.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { confirmAbandon = true }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        val doneCount = ui.sets.count { it.done }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "$doneCount / ${ui.sets.size} ${plural(ui.sets.size, "set", "sets")} done",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = ChakraPetch,
                color = IronvellumColors.SystemGreen,
                modifier = Modifier.weight(1f),
            )
            SessionElapsed(session.startedAtMs)
        }
        if (ui.sets.isNotEmpty()) {
            val estimate = remember(ui.sets, exercises, focus, pace) {
                val metrics = exercises.associate { it.id to it.metric }
                SessionClock.estimateLine(
                    SessionClock.totalSeconds(ui.sets, { metrics[it] }, focus, pace),
                    SessionClock.remainingSeconds(ui.sets, { metrics[it] }, focus, pace),
                )
            }
            Text(
                estimate,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = ChakraPetch,
                color = IronvellumColors.InkMuted,
                letterSpacing = IronvellumTracking.InlineLabel,
            )
        }

        // Render blocks in the preset's saved exercise order: sort explicitly
        // by exercisePosition instead of trusting the query's emission order.
        val exerciseBlocks = ui.sets.groupBy { it.exerciseId }
            .map { (id, sets) -> sets.minOf { it.exercisePosition } to (id to sets) }
            .sortedBy { it.first }
        exerciseBlocks.forEachIndexed { blockIdx, (_, block) ->
            val (exerciseId, sets) = block
            val first = sets.first()
            val doneSets = sets.filter { it.done }
            val blockMetric = exercises.firstOrNull { it.id == exerciseId }?.metric ?: ExerciseMetric.REPS
            val isHoldBlock = blockMetric == ExerciseMetric.HOLD
            // Bouldering's figure is an attempt count and a run's is a
            // distance; summing either through repScore invented a strength
            // number for work that is not lifting at all.
            val groupStrength = bodyweight?.takeIf { blockMetric.isStrength }?.let { bw ->
                doneSets.sumOf { set ->
                    if (isHoldBlock) {
                        StrengthIndex.holdScore(first.exerciseName, set.durationSec ?: 0, set.weightKg, bw)
                    } else {
                        StrengthIndex.repScore(first.exerciseName, set.reps, set.weightKg, bw)
                    }
                }.toInt()
            }
            Spacer(Modifier.height(14.dp))
            InkPanel(Modifier.fillMaxWidth()) {
                // Modifiers change the profile: a deficit push-up credits the
                // chest at stretch, so the muscles read from both.
                val shares = MuscleMap.profile(first.exerciseName, first.modifiers)?.muscles
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        // The name and a small (i) are one 44dp target opening the
                        // exercise's info. The glyph sits inline, not in a 48dp
                        // IconButton, so the name keeps its width at 360dp.
                        val infoLabel = "About ${first.exerciseName}"
                        Row(
                            Modifier
                                .heightIn(min = 44.dp)
                                .clickable(onClickLabel = infoLabel) { infoFor = exerciseId }
                                .semantics {
                                    contentDescription = infoLabel
                                    role = Role.Button
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                first.exerciseName,
                                style = MaterialTheme.typography.titleMedium,
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
                        // Only real modifiers get a line. "tap to set modifiers"
                        // printed under every movement that had none, beside the
                        // slider glyph in this same header that does exactly that.
                        if (first.modifiers.isNotBlank()) {
                            Text(
                                first.modifiers.split(",").joinToString(" · ") { it.trim() },
                                style = MaterialTheme.typography.labelSmall,
                                color = IronvellumColors.SystemGreen,
                                modifier = Modifier
                                    .clickable { editModifiersFor = exerciseId }
                                    .padding(vertical = 2.dp),
                            )
                        }
                    }
                    // Reorder controls only where a move is possible: disabled
                    // arrows at the ends cost the name ~48dp each at 360dp and
                    // wrapped "Dumbbell Shoulder Press" onto three lines.
                    if (blockIdx > 0) {
                        IconButton(onClick = { viewModel.moveExercise(first.exercisePosition, up = true) }) {
                            Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "Move exercise up", tint = IronvellumColors.InkMuted)
                        }
                    }
                    if (blockIdx < exerciseBlocks.lastIndex) {
                        IconButton(onClick = { viewModel.moveExercise(first.exercisePosition, up = false) }) {
                            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Move exercise down", tint = IronvellumColors.InkMuted)
                        }
                    }
                    IconButton(onClick = { editModifiersFor = exerciseId }) {
                        Icon(Icons.Outlined.Tune, contentDescription = "Edit modifiers", tint = IronvellumColors.InkMuted)
                    }
                    IconButton(onClick = {
                        // A duplicate carries the block's own figure: a hold's
                        // seconds, an activity's duration — a copy of a Yoga set
                        // starting at "10 reps" repeats the seconds-as-reps bug.
                        val copySeconds =
                            when {
                                isHoldBlock -> first.durationSec ?: DEFAULT_HOLD_SECONDS
                                blockMetric == ExerciseMetric.DURATION || blockMetric == ExerciseMetric.DISTANCE_TIME -> first.durationSec
                                else -> null
                            }
                        viewModel.addSet(exerciseId, first.reps, first.weightKg, first.modifiers, copySeconds)
                    }) {
                        Icon(Icons.Filled.Add, contentDescription = "Add set", tint = IronvellumColors.SystemGreen)
                    }
                }
                // The muscles and the STR figure get their own full-width line
                // under the header: beside the name they cut "Single-leg…" to
                // "Sin/gl…", and inside the name's column the four header
                // icons still squeezed "RHOMBOIDS" to "RHOM…".
                val mainMuscles = shares?.let { musclesAt(it, ShareLevel.MAIN) }?.takeIf { it.isNotEmpty() }
                val showStrength = groupStrength != null && groupStrength > 0
                if (mainMuscles != null || showStrength) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(onClickLabel = "About ${first.exerciseName}") { infoFor = exerciseId }
                            .padding(bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            mainMuscles?.joinToString(" · ") { it.label.uppercase() }.orEmpty(),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = ChakraPetch,
                            color = IronvellumColors.InkMuted,
                            letterSpacing = IronvellumTracking.InlineLabel,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (showStrength) {
                            // Labelled, not a gold bolt beside a bare number: the
                            // bolt read as XP, which this is not — it is the
                            // body-scaled strength score for the movement.
                            Text(
                                "$groupStrength STR",
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = ChakraPetch,
                                color = IronvellumColors.SovereignGold,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }
                sets.sortedBy { it.setIndex }.forEachIndexed { position, set ->
                    SetRow(
                        label = "${set.setIndex + 1}",
                        exerciseName = set.exerciseName,
                        setIndex = set.setIndex,
                        records = records,
                        bodyweight = bodyweight,
                        // A hold's figure is its seconds, not its (zero) reps.
                        reps = if (isHoldBlock) (set.durationSec ?: 0) else set.reps,
                        weightKg = set.weightKg,
                        done = set.done,
                        isHold = isHoldBlock,
                        metric = blockMetric,
                        // Weighted Skipping keeps its LOAD column on DURATION;
                        // Running and Bouldering never show one.
                        isWeighted = exercises.firstOrNull { it.id == exerciseId }?.isWeighted ?: false,
                        durationSec = set.durationSec,
                        distanceM = set.distanceM,
                        grade = set.grade.orEmpty(),
                        scoresStrength = blockMetric.isStrength,
                        // LOAD and REPS head the columns once. Repeating them on
                        // every row printed the same two words 36 times in an
                        // 18-set session, on top of identical steppers.
                        showColumnLabels = position == 0,
                        // A PR must also beat this exercise's earlier done sets
                        // today, or a repeat of set 2 as set 3 reads NEW PR twice.
                        bestEarlierThisWorkout = bodyweight?.takeIf { blockMetric.isStrength }?.let { bw ->
                            sets.filter { it.done && it.setIndex < set.setIndex }.maxOfOrNull { earlier ->
                                SetRecords.score(
                                    earlier.exerciseName,
                                    if (isHoldBlock) (earlier.durationSec ?: 0) else earlier.reps,
                                    earlier.weightKg,
                                    bw,
                                    isHoldBlock,
                                )
                            }
                        },
                        onLoadTap = { editLoadFor = set },
                        // Removing a block's only set removes the exercise; the
                        // ✕ is the same everywhere, so a one-off added by
                        // mistake can be taken back out.
                        onRemove = if (sets.size > 1) {
                            { viewModel.removeSet(set.id) }
                        } else {
                            { viewModel.removeExercise(exerciseId) }
                        },
                        onChange = { value, w, d ->
                            if (isHoldBlock) {
                                viewModel.updateHoldSet(set.id, value, w, d)
                            } else {
                                viewModel.updateSet(set.id, value, w, d)
                            }
                        },
                        // The activity route writes every column; each caller
                        // passes the set's CURRENT values for fields its metric
                        // does not own, so no edit erases a neighbour's figure.
                        onActivityChange = { reps, durationSec, distanceM, grade, w, d ->
                            viewModel.updateActivitySet(set.id, reps, durationSec, distanceM, grade, w, d)
                        },
                    )
                }

            }
        }

        Spacer(Modifier.height(10.dp))
        // The screen's only add action: a real button, not faint link text.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            IronvellumButton("Add exercise", onClick = { showExercisePicker = true })
        }
        Spacer(Modifier.height(16.dp))
        SessionNotesEditor(session = session, viewModel = viewModel)

        Spacer(Modifier.height(16.dp))
        // Nothing ticked is nothing to claim: finishing an empty trial is an
        // abandon, and it must never mint the completion bonus.
        val anyDone = doneCount > 0
        // Fewer than half the sets ticked (and more than two sets) is usually
        // a claim tapped by accident mid-workout, so it asks first. Pure UI:
        // the completion call is exactly the one the button made before.
        val claimHasty = doneCount * 2 < ui.sets.size && ui.sets.size > 2
        IronvellumButton(
            label = "Seal the Trial",
            gold = true,
            enabled = anyDone,
            onClick = { if (claimHasty) confirmClaim = true else viewModel.complete() },
            modifier = Modifier.fillMaxWidth(),
        )
        if (!anyDone) {
            Text(
                "Tick a set to seal the trial",
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(24.dp))
    }

    if (confirmAbandon) {
        AlertDialog(
            // Material's dialog container is a 28dp rounded rect - the most
            // obviously stock surface in the app. Give it the ink shape.
            shape = MaterialTheme.shapes.medium,
            containerColor = Color(0xFF0D1110),
            onDismissRequest = { confirmAbandon = false },
            title = { Text("Abandon this trial?") },
            text = { Text("Unsealed trials grant no XP and are erased from the Chronicle.") },
            // Staying is the filled action; abandoning is the quiet one, so a
            // reflex tap on the bright button never erases the trial.
            confirmButton = {
                IronvellumButton(
                    "Abandon",
                    quiet = true,
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
        AlertDialog(
            shape = MaterialTheme.shapes.medium,
            containerColor = Color(0xFF0D1110),
            onDismissRequest = { confirmClaim = false },
            title = { Text("Seal the Trial?") },
            text = {
                val unticked = ui.sets.count { !it.done }
                Text("$unticked ${plural(unticked, "set is", "sets are")} unticked and won't count. Seal anyway?")
            },
            // Claiming is the deliberate action here, so it takes the confirm
            // slot; KEEP GOING is the safe default.
            confirmButton = {
                IronvellumButton(
                    "Seal",
                    onClick = {
                        confirmClaim = false
                        viewModel.complete()
                    },
                )
            },
            dismissButton = {
                IronvellumButton("Keep going", quiet = true, onClick = { confirmClaim = false })
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
                (it.id == target.id || !it.done || it.weightKg == target.weightKg)
        }
        val focus = remember { FocusRequester() }
        LaunchedEffect(target.id) { focus.requestFocus() }
        fun apply(sets: List<SessionSet>) {
            val kg = parsed.getOrNull() ?: return
            sets.forEach { applyLoad(it, kg.takeIf { v -> v > 0.0 }) }
            editLoadFor = null
        }
        AlertDialog(
            shape = MaterialTheme.shapes.medium,
            containerColor = Color(0xFF0D1110),
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
                        "SET ${target.setIndex + 1}",
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
                    onValueChange = { field = it.copy(text = it.text.take(LOAD_INPUT_MAX_CHARS)) },
                    singleLine = true,
                    label = { Text("Load (kg)") },
                    placeholder = { Text("Leave blank for bodyweight") },
                    isError = parsed.isFailure,
                    supportingText = if (parsed.isFailure) {
                        { Text("Enter 0 to ${MAX_LOAD_KG.toInt()} kg.") }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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

    if (showRiteMuscles) {
        RiteMusclesDialog(
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
            selected = current?.modifiers
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.toSet()
                ?: emptySet(),
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
            ExerciseInfoDialog(
                exercise = exercise,
                // Sealed trials only: the live one is never its own "last".
                lastLine = lastLogged[exerciseId]?.let { lastLoggedLine(it, exercise, System.currentTimeMillis()) },
                onDismiss = { infoFor = null },
                onPick = null,
                modifiers = current.modifiers,
            )
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
        val awards = remember(result, sex) { awardsFor(result, sex) }
        when (finish) {
            SessionViewModel.Finish.VICTORY -> VictoryOverlay(
                result = result,
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
                    )
                },
                onContinue = {
                    viewModel.advanceFinish(
                        if (awards.isEmpty()) SessionViewModel.Finish.ROUTINE else SessionViewModel.Finish.AWARDS,
                    )
                },
            )
            SessionViewModel.Finish.AWARDS -> AchievementOverlay(
                items = awards,
                onDone = { viewModel.advanceFinish(SessionViewModel.Finish.ROUTINE) },
            )
            SessionViewModel.Finish.ROUTINE -> {
                val offer = routineUpdate
                if (offer != null) {
                    // A set left unticked still counts as a row, so it never
                    // lowers the set count (RoutineUpdate.propose); the title
                    // says so whenever an offered movement has one.
                    val offered = offer.changes.map { it.before.exerciseId }.toSet()
                    val shortDay = ui.sets.any { !it.done && it.exerciseId in offered }
                    RoutineUpdateDialog(
                        offer = offer,
                        shortDay = shortDay,
                        onUpdate = viewModel::applyRoutineUpdate,
                        onKeep = viewModel::keepPlan,
                    )
                } else {
                    // Answered, or nothing to ask: the finished session is done.
                    LaunchedEffect(result) { onExit() }
                }
            }
        }
    }

    shareText?.let { text ->
        ShareCardDialog(text = text, onDismiss = { shareText = null })
    }
}

/** One page per honour the completion earned, shown after the victory. */
private fun awardsFor(result: Repository.CompletionResult, sex: Sex): List<Achievement> = buildList {
    if (result.levelAfter > result.levelBefore) {
        add(
            Achievement(
                banner = "LEVEL UP",
                tagline = "STRENGTH RANK",
                name = "Level ${result.levelAfter}",
                subtitle = "${result.totalXp} XP TOTAL",
                accent = IronvellumColors.SystemGreen,
            ),
        )
    }
    if (result.classAfter != result.classBefore) {
        add(
            Achievement(
                banner = "ASCENDED",
                tagline = "ASCENSION",
                name = result.classAfter,
                subtitle = "FROM ${result.classBefore.uppercase()}",
            ),
        )
    }
    result.newTitles.forEach { title ->
        add(
            Achievement(
                banner = "DEED EARNED",
                name = title.name,
                subtitle = title.describeFor(sex).uppercase(),
            ),
        )
    }
}

@Composable
private fun VictoryOverlay(
    result: Repository.CompletionResult,
    onShare: () -> Unit,
    onContinue: () -> Unit,
) {
    val scale = remember { Animatable(0.6f) }
    val appear = remember { Animatable(0f) }
    val xpShown = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 380f)) }
        launch { appear.animateTo(1f, tween(350)) }
        launch { xpShown.animateTo(result.xpAwarded.toFloat(), tween(1000)) }
    }
    val sparkle = rememberInfiniteTransition(label = "sparkle")
    val rise by sparkle.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1600)),
        label = "rise",
    )
    val classUp = result.classAfter != result.classBefore
    val levelUp = result.levelAfter > result.levelBefore

    Dialog(
        onDismissRequest = onContinue,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xD005070A)),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                repeat(14) { i ->
                    val seed = i * 0.618f
                    val x = ((seed * 7.13f) % 1f) * size.width
                    val cycle = (rise + seed) % 1f
                    val y = cycle * size.height
                    val alpha = (1f - cycle) * 0.8f
                    // Brushed dots; colour alpha carries the rise-fade.
                    inkDot(
                        center = androidx.compose.ui.geometry.Offset(x, y),
                        radius = (2.5f + (i % 3)) * 2f,
                        color = (if (i % 3 == 0) IronvellumColors.SovereignGold else IronvellumColors.SystemGreen).copy(alpha = alpha),
                        seed = i,
                    )
                }
            }
            InkPanel(
                Modifier
                    .padding(horizontal = 28.dp)
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = scale.value
                        scaleY = scale.value
                        alpha = appear.value
                    },
                accent = IronvellumColors.SovereignGold,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "SEALED",
                        style = MaterialTheme.typography.displaySmall,
                        fontFamily = ChakraPetch,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 10.sp,
                        color = IronvellumColors.SovereignGold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "+${xpShown.value.toInt()} XP",
                        style = MaterialTheme.typography.displaySmall,
                        fontFamily = ChakraPetch,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    // Level progress, not the running total: on a first run
                    // the total is the awarded figure and read as a repeat.
                    val progress = Xp.progress(result.totalXp)
                    Text(
                        "\u23F1 ${result.durationMinutes} min · LEVEL ${progress.level}" +
                            " · ${progress.intoLevel}/${progress.needed} XP",
                        style = MaterialTheme.typography.labelMedium,
                        color = IronvellumColors.InkMuted,
                    )
                    Spacer(Modifier.height(12.dp))
                    if (result.questBonus) {
                        RewardRow("TODAY'S TRIAL", "+${Xp.QUEST_BONUS} bonus")
                        Spacer(Modifier.height(6.dp))
                    }
                    if (levelUp) {
                        RewardRow("LEVEL UP", "${result.levelBefore} → ${result.levelAfter}")
                        Spacer(Modifier.height(6.dp))
                    }
                    if (classUp) {
                        RewardRow("ASCENDED", result.classAfter)
                        Spacer(Modifier.height(6.dp))
                    }
                    result.newTitles.filter { it.name.isNotBlank() }.forEach { title ->
                        RewardRow("DEED EARNED", title.name)
                        Spacer(Modifier.height(4.dp))
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IronvellumButton(
                            label = "Share",
                            quiet = true,
                            onClick = onShare,
                            modifier = Modifier.weight(1f),
                        )
                        IronvellumButton(
                            label = "Continue",
                            onClick = onContinue,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RewardRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = ChakraPetch,
            color = IronvellumColors.InkMuted,
            letterSpacing = 2.sp,
        )
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = IronvellumColors.SovereignGold,
        )
    }
}

@Composable
private fun SetRow(
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
            modifier = Modifier.padding(end = 2.dp),
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
        SetDeltaBadge(delta, displaySetNo = setIndex + 1)
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
            (if (delta.deltaScore >= 0.0) "▲ +" else "▽ ") + "%.1f".format(delta.deltaScore),
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

private const val LOAD_INPUT_MAX_CHARS = 7

internal fun stepDownKg(kg: Double?): Double? =
    kg?.let { (kotlin.math.ceil(it / LOAD_STEP_KG - 1e-9) - 1) * LOAD_STEP_KG }?.takeIf { it > 0.0 }

internal fun stepUpKg(kg: Double?): Double =
    (kotlin.math.floor((kg ?: 0.0) / LOAD_STEP_KG + 1e-9) + 1) * LOAD_STEP_KG

/**
 * A typed load: blank is bodyweight (0.0), a comma is a decimal point, and
 * the value is kept to the gram. Anything else, or out of range, fails.
 */
internal fun parseLoadKg(text: String): Result<Double> {
    val trimmed = text.trim().replace(',', '.')
    if (trimmed.isEmpty()) return Result.success(0.0)
    val kg = trimmed.toDoubleOrNull()
        ?: return Result.failure(IllegalArgumentException("not a number: $text"))
    if (kg.isNaN() || kg < 0.0 || kg > MAX_LOAD_KG) return Result.failure(IllegalArgumentException("out of range: $text"))
    return Result.success(Math.round(kg * 1000.0) / 1000.0)
}

/** A load as the dialog shows it for editing: no unit, no trailing ".0". */
private fun loadText(kg: Double): String =
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
                indication = null,
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


/**
 * Title + notes editor for the session. Lives at the foot of the active trial,
 * just above "Seal the Trial", so annotations are written while the session is
 * still open and are already persisted (and synced) by the time it completes.
 * Public and private notes are deliberately styled to clash: gold/emerald for
 * what the feed sees, muted + lock glyph for what never leaves the device.
 */
@Composable
private fun SessionNotesEditor(
    session: WorkoutSession,
    viewModel: SessionViewModel,
) {
    // Seeded once per session; repo writes happen on blur, so we don't echo
    // the flow back into the fields (that would fight the cursor). Saveable so
    // a process kill (or rotation) mid-typing doesn't silently drop the note —
    // on restore the saved text wins over the freshly-loaded session values.
    var title by rememberSaveable(session.id) { mutableStateOf(session.title) }
    var publicNote by rememberSaveable(session.id) { mutableStateOf(session.note) }
    var privateNote by rememberSaveable(session.id) { mutableStateOf(session.privateNote) }
    // Signed out there is no feed: saying "shown on the feed" would be false.
    val signedIn = (LocalContext.current.applicationContext as com.ironvellum.app.IronvellumApp)
        .accountRepository.account.collectAsStateWithLifecycle().value != null

    InkPanel(accent = IronvellumColors.SovereignGold) {
        Text(
            "NAME & NOTES",
            style = MaterialTheme.typography.labelLarge,
            fontFamily = ChakraPetch,
            color = IronvellumColors.SovereignGold,
            letterSpacing = IronvellumTracking.SectionHeader,
        )
        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            shape = MaterialTheme.shapes.small,
            value = title,
            onValueChange = { title = it.take(TITLE_CAP) },
            singleLine = true,
            label = {
                FieldLabel(
                    icon = { Icon(Icons.Outlined.Public, null, Modifier.size(14.dp), tint = IronvellumColors.SovereignGold) },
                    text = if (signedIn) "NAME · optional, shown in Tidings" else "NAME · optional",
                    color = IronvellumColors.SovereignGold,
                )
            },
            placeholder = { Text("Name this trial…", style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted) },
            trailingIcon = { CharCounter(title.length, TITLE_CAP, IronvellumColors.SovereignGold) },
            colors = fieldColors(accent = IronvellumColors.SovereignGold),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { if (!it.isFocused) viewModel.setSessionTitle(title.trim()) },
        )
        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            shape = MaterialTheme.shapes.small,
            value = publicNote,
            onValueChange = { publicNote = it.take(PUBLIC_NOTE_CAP) },
            minLines = 2,
            label = {
                FieldLabel(
                    icon = { Icon(Icons.Outlined.Public, null, Modifier.size(14.dp), tint = IronvellumColors.SystemGreen) },
                    text = if (signedIn) "PUBLIC NOTE · every Ironbound in Tidings can read this" else "NOTE · shared with allies once you sign in",
                    color = IronvellumColors.SystemGreen,
                )
            },
            placeholder = { Text("How did the trial go? Share it…", style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted) },
            trailingIcon = { CharCounter(publicNote.length, PUBLIC_NOTE_CAP, IronvellumColors.SystemGreen) },
            colors = fieldColors(accent = IronvellumColors.SystemGreen),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { if (!it.isFocused) viewModel.setSessionNote(publicNote.trim()) },
        )
        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            shape = MaterialTheme.shapes.small,
            value = privateNote,
            onValueChange = { privateNote = it.take(WireLimits.PRIVATE_NOTE_MAX) },
            minLines = 2,
            label = {
                FieldLabel(
                    icon = { Icon(Icons.Outlined.Lock, null, Modifier.size(14.dp), tint = IronvellumColors.InkMuted) },
                    text = "PRIVATE NOTE · never leaves this device",
                    color = IronvellumColors.InkMuted,
                )
            },
            placeholder = { Text("For your eyes only…", style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted) },
            colors = fieldColors(accent = IronvellumColors.InkMuted),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { if (!it.isFocused) viewModel.setSessionPrivateNote(privateNote) },
        )
    }
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
private fun fieldColors(accent: Color) = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = accent,
    unfocusedBorderColor = accent.copy(alpha = 0.4f),
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
        style = MaterialTheme.typography.titleMedium,
        fontFamily = ChakraPetch,
        fontWeight = FontWeight.Bold,
        color = IronvellumColors.SovereignGold,
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

private val MODIFIER_OPTIONS = listOf(
    "weighted", "assisted", "deficit", "elevated", "incline", "decline",
    "tempo", "paused", "banded", "one-arm", "archer", "hold seconds",
)

@Composable
private fun ModifierPickerDialog(
    exerciseName: String,
    selected: Set<String>,
    onConfirm: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var picked by remember(exerciseName) { mutableStateOf(selected) }
    AlertDialog(
        // Material's dialog container is a 28dp rounded rect - the most
        // obviously stock surface in the app. Give it the ink shape.
        shape = MaterialTheme.shapes.medium,
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0D1110),
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
                Spacer(Modifier.height(10.dp))
                MODIFIER_OPTIONS.chunked(3).forEach { row ->
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        row.forEach { option ->
                            val on = option in picked
                            val shape = MaterialTheme.shapes.extraSmall
                            Box(
                                Modifier
                                    .weight(1f)
                                    .background(
                                        if (on) {
                                            Brush.verticalGradient(listOf(Color(0xFF2C7A5A), Color(0xFF1B4D3A)))
                                        } else {
                                            Brush.verticalGradient(listOf(Color(0xFF151C19), Color(0xFF0F1412)))
                                        },
                                        shape,
                                    )
                                    .inkBorder(if (on) IronvellumColors.SystemGreen else IronvellumColors.Rune, shape, 1.dp)
                                    .clickable {
                                        picked = if (on) picked - option else picked + option
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
    AlertDialog(
        shape = MaterialTheme.shapes.medium,
        onDismissRequest = onKeep,
        containerColor = Color(0xFF0D1110),
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
                                        Brush.verticalGradient(listOf(Color(0xFF2C7A5A), Color(0xFF1B4D3A)))
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
