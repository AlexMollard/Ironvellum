package com.ironvellum.app.ui.train

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
import com.ironvellum.app.ui.theme.InkCircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.ui.draw.alpha
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.onFocusChanged
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
import com.ironvellum.app.domain.Exercise
import com.ironvellum.app.domain.ExerciseMetric
import com.ironvellum.app.domain.isStrength
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
import com.ironvellum.app.ui.components.ExercisePickerPanel
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.components.formatBodyValue
import com.ironvellum.app.ui.components.formatDate
import com.ironvellum.app.ui.launchGuarded
import com.ironvellum.app.ui.ironvellumRepository
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumTracking
import com.ironvellum.app.ui.theme.inkBorder
import com.ironvellum.app.ui.theme.inkDot
import com.ironvellum.app.ui.theme.IronvellumColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
     * The reader's own bar, for the deeds the victory overlay names: a woman
     * must not be congratulated in the wording of the men's standard.
     */
    val sex: StateFlow<Sex> = repo.observeBodyProfile()
        .map { it.second }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Sex.MALE)

    // Most-recent-first ids from completed sessions; the picker preserves the order.
    val recentExerciseIds: StateFlow<List<Long>> =
        repo.observeRecentExerciseIds().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val bodyweight: StateFlow<Double?> = repo.observeStats()
        .map { it.firstOrNull()?.weightKg }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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

    private var completing = false

    fun complete(onResult: (Repository.CompletionResult) -> Unit) {
        // One completion per session, whatever the button does. The repository
        // already refuses a second one (a `check` inside the transaction, and
        // two concurrent callers pay exactly once — DoubleCompletionTest), but
        // it refuses by THROWING, and this launch has no catch: a second tap
        // landing before the victory overlay replaces the button would take the
        // exception straight into viewModelScope. So the second tap is dropped
        // here, and a genuine failure is surfaced rather than crashing.
        if (completing) return
        completing = true
        viewModelScope.launch {
            runCatching { repo.completeSession(sessionId) }
                .onSuccess {
                    // Completion is the moment a daily user's work becomes
                    // feed/leaderboard-visible; don't wait for a manual push.
                    CloudSyncWorker.pushNow(appContext)
                    onResult(it)
                }
                .onFailure { completing = false }
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
    val recentExerciseIds by viewModel.recentExerciseIds.collectAsStateWithLifecycle()
    val bodyweight by viewModel.bodyweight.collectAsStateWithLifecycle()
    val sex by viewModel.sex.collectAsStateWithLifecycle()
    var completion by remember { mutableStateOf<Repository.CompletionResult?>(null) }
    var confirmAbandon by remember { mutableStateOf(false) }
    var showExercisePicker by remember { mutableStateOf(false) }
    var editModifiersFor by remember { mutableStateOf<Long?>(null) }

    val session = ui.session
    if (session == null) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Spacer(Modifier.height(20.dp))
            Text("Summoning session…", style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted)
        }
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
                Text(session.label, style = MaterialTheme.typography.headlineSmall, color = IronvellumColors.EmeraldBright)
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
        Text(
            "$doneCount / ${ui.sets.size} sets conquered",
            style = MaterialTheme.typography.labelMedium,
            fontFamily = ChakraPetch,
            color = IronvellumColors.SystemGreen,
        )

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
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(first.exerciseName, style = MaterialTheme.typography.titleMedium)
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
                    if (groupStrength != null && groupStrength > 0) {
                        // Labelled, not a gold bolt beside a bare number: the
                        // bolt read as XP, which this is not — it is the
                        // body-scaled strength score for the movement.
                        Text(
                            "$groupStrength STR",
                            style = MaterialTheme.typography.labelLarge,
                            fontFamily = ChakraPetch,
                            color = IronvellumColors.SovereignGold,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                    // Reorder controls: the ends are disabled, not inert, so the
                    // lifter can see the boundary of the ordering.
                    IconButton(
                        onClick = { viewModel.moveExercise(first.exercisePosition, up = true) },
                        enabled = blockIdx > 0,
                    ) {
                        Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "Move exercise up", tint = IronvellumColors.InkMuted)
                    }
                    IconButton(
                        onClick = { viewModel.moveExercise(first.exercisePosition, up = false) },
                        enabled = blockIdx < exerciseBlocks.lastIndex,
                    ) {
                        Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Move exercise down", tint = IronvellumColors.InkMuted)
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
                        // the final set stays: drop the exercise instead of emptying it
                        onRemove = if (sets.size > 1) ({ viewModel.removeSet(set.id) }) else null,
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
        Text(
            "+ add a one-off exercise",
            style = MaterialTheme.typography.labelLarge,
            fontFamily = ChakraPetch,
            color = IronvellumColors.SystemGreen,
            modifier = Modifier
                .clip(MaterialTheme.shapes.extraSmall)
                .clickable { showExercisePicker = true }
                .padding(horizontal = 4.dp, vertical = 6.dp),
        )
        Spacer(Modifier.height(16.dp))
        SessionNotesEditor(session = session, viewModel = viewModel)

        Spacer(Modifier.height(16.dp))
        IronvellumButton(
            label = "Claim Victory",
            gold = true,
            onClick = { viewModel.complete { completion = it } },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))
    }

    if (confirmAbandon) {
        AlertDialog(
            // Material's dialog container is a 28dp rounded rect - the most
            // obviously stock surface in the app. Give it the ink shape.
            shape = MaterialTheme.shapes.medium,
            onDismissRequest = { confirmAbandon = false },
            title = { Text("Abandon this trial?") },
            text = { Text("Unfinished sessions grant no XP and are erased from the record.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmAbandon = false
                    viewModel.abandon(onExit)
                }) { Text("Abandon", color = IronvellumColors.DangerRed) }
            },
            dismissButton = {
                TextButton(onClick = { confirmAbandon = false }) { Text("Keep fighting") }
            },
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

    if (showExercisePicker) {
        AlertDialog(
            // Material's dialog container is a 28dp rounded rect - the most
            // obviously stock surface in the app. Give it the ink shape.
            shape = MaterialTheme.shapes.medium,
            onDismissRequest = { showExercisePicker = false },
            containerColor = Color(0xFF0D1110),
            title = {},
            text = {
                ExercisePickerPanel(
                    exercises = exercises,
                    recentIds = recentExerciseIds,
                    onPick = { exercise ->
                        viewModel.addExercise(exercise.id, exercise.metric == ExerciseMetric.HOLD)
                        showExercisePicker = false
                    },
                    onDismiss = { showExercisePicker = false },
                )
            },
            confirmButton = {},
        )
    }

    var awards by remember { mutableStateOf<List<Achievement>>(emptyList()) }

    var shareText by remember { mutableStateOf<String?>(null) }

    completion?.let { result ->
        VictoryOverlay(
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
                awards = buildList {
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
                                banner = "CLASS UNLOCKED",
                                tagline = "PROMOTION",
                                name = result.classAfter,
                                subtitle = "FROM ${result.classBefore.uppercase()}",
                            ),
                        )
                    }
                    result.newTitles.forEach { title ->
                        add(
                            Achievement(
                                banner = "TITLE EARNED",
                                name = title.name,
                                subtitle = title.describeFor(sex).uppercase(),
                            ),
                        )
                    }
                }
                completion = null
                if (awards.isEmpty()) onExit()
            },
        )
    }

    if (awards.isNotEmpty()) {
        AchievementOverlay(items = awards, onDone = { awards = emptyList(); onExit() })
    }

    shareText?.let { text ->
        ShareCardDialog(text = text, onDismiss = { shareText = null })
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
                        "VICTORY",
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
                    Text(
                        "\u23F1 ${result.durationMinutes} min  ·  total ${result.totalXp} XP",
                        style = MaterialTheme.typography.labelMedium,
                        color = IronvellumColors.InkMuted,
                    )
                    Spacer(Modifier.height(12.dp))
                    if (result.questBonus) {
                        RewardRow("TODAY'S QUEST", "+${Xp.QUEST_BONUS} bonus")
                        Spacer(Modifier.height(6.dp))
                    }
                    if (levelUp) {
                        RewardRow("LEVEL UP", "${result.levelBefore} → ${result.levelAfter}")
                        Spacer(Modifier.height(6.dp))
                    }
                    if (classUp) {
                        RewardRow("CLASS UNLOCKED", result.classAfter)
                        Spacer(Modifier.height(6.dp))
                    }
                    result.newTitles.forEach { title ->
                        RewardRow("TITLE", title.name)
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
     * False for activity work. Records exclude it, so without this the badge
     * read "NEW PR" on every climbing set forever: no stored record means
     * [SetRecords.delta] reports the first one.
     */
    scoresStrength: Boolean = true,
    showColumnLabels: Boolean = true,
    onRemove: (() -> Unit)? = null,
    onChange: (Int, Double?, Boolean) -> Unit,
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
            Column(Modifier.weight(1f)) {
                if (showColumnLabels) ColumnLabel("LOAD")
                Stepper(
                    value = formatKg(weightKg),
                    onMinus = { onChange(reps, stepDownKg(weightKg), done) },
                    onPlus = { onChange(reps, stepUpKg(weightKg), done) },
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
                            onMinus = { onActivityChange(reps, durationSec, distanceM, grade, stepDownKg(weightKg), done) },
                            onPlus = { onActivityChange(reps, durationSec, distanceM, grade, stepUpKg(weightKg), done) },
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
        // live digit changes never reflow the row mid-set.
        val delta = bodyweight?.takeIf { scoresStrength }?.let { bw ->
            SetRecords.delta(records, exerciseName, setIndex, reps, weightKg, bw, isHold = isHold)
        }
        SetDeltaBadge(delta, displaySetNo = setIndex + 1)
    }
}

/** Shared 8sp column heading for the activity stepper columns. */
@Composable
private fun ColumnLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontFamily = ChakraPetch,
        fontSize = 8.sp,
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
        if (delta.isRecord) {
            Icon(
                Icons.Filled.Bolt,
                contentDescription = "New record",
                tint = IronvellumColors.SovereignGold,
                modifier = Modifier.size(12.dp),
            )
            Text(
                "NEW PR",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = ChakraPetch,
                color = IronvellumColors.SovereignGold,
            )
            record?.let {
                Text(
                    "was ${it.reps}×${prLoad(it.weightKg)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = IronvellumColors.InkMuted,
                )
            }
        } else if (record == null) {
            Text(
                "first set $displaySetNo on record",
                style = MaterialTheme.typography.labelSmall,
                color = IronvellumColors.InkMuted,
            )
        } else {
            Text(
                "PR ${record.reps}×${prLoad(record.weightKg)}",
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
}

/** "40kg" or plain "BW" when the PR was a pure bodyweight set. */
private fun prLoad(weightKg: Double?): String =
    weightKg?.let { "${formatKg(it)}kg" } ?: "BW"

private fun stepDownKg(kg: Double?): Double? = kg?.minus(2.5)?.takeIf { it > 0.0 }

private fun stepUpKg(kg: Double?): Double = (kg ?: 0.0) + 2.5

@Composable
private fun Stepper(
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    dimmed: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .background(IronvellumColors.Abyss)
            .inkBorder(IronvellumColors.Rune, MaterialTheme.shapes.extraSmall, 1.dp)
            .padding(horizontal = 2.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        StepIcon("−", onMinus)
        Text(
            value,
            style = MaterialTheme.typography.labelLarge,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = if (dimmed) IronvellumColors.InkMuted else IronvellumColors.Ink,
        )
        StepIcon("+", onPlus)
    }
}

@Composable
private fun StepIcon(symbol: String, onClick: () -> Unit) {
    Text(
        symbol,
        style = MaterialTheme.typography.titleMedium,
        fontFamily = ChakraPetch,
        color = IronvellumColors.SystemGreen,
        modifier = Modifier
            .clip(InkCircleShape(7))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 2.dp),
    )
}


private const val TITLE_CAP = 80
private const val PUBLIC_NOTE_CAP = 500


/**
 * Title + notes editor for the session. Lives at the foot of the active trial,
 * just above "Claim Victory", so annotations are written while the session is
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

    InkPanel(accent = IronvellumColors.SovereignGold) {
        Text(
            "CHRONICLE",
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
                    text = "TITLE · optional, shown on the feed",
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
                    text = "PUBLIC NOTE · every lifter on the feed can read this",
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
                    "Applies to every set of this movement.",
                    style = MaterialTheme.typography.labelSmall,
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
                                Text(
                                    option,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = ChakraPetch,
                                    color = if (on) IronvellumColors.Ink else IronvellumColors.InkMuted,
                                    maxLines = 1,
                                )
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        },
        confirmButton = { IronvellumButton("Save", onClick = { onConfirm(picked) }) },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
