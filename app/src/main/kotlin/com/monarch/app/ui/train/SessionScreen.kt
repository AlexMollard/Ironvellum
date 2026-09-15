package com.monarch.app.ui.train

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
import com.monarch.app.ui.theme.InkCircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.monarch.app.data.Repository
import com.monarch.app.data.cloud.WireLimits
import com.monarch.app.domain.Exercise
import com.monarch.app.domain.SessionSet
import com.monarch.app.domain.SetRecords
import com.monarch.app.domain.StrengthIndex
import com.monarch.app.domain.WorkoutSession
import com.monarch.app.domain.Xp
import com.monarch.app.ui.components.Achievement
import com.monarch.app.ui.components.AchievementOverlay
import com.monarch.app.ui.components.ExercisePickerPanel
import com.monarch.app.ui.components.MonarchButton
import com.monarch.app.ui.components.SystemWindow
import com.monarch.app.ui.components.formatDate
import com.monarch.app.ui.monarchRepository
import com.monarch.app.ui.theme.ChakraPetch
import com.monarch.app.ui.theme.MonarchTracking
import com.monarch.app.ui.theme.inkBorder
import com.monarch.app.ui.theme.inkDot
import com.monarch.app.ui.theme.MonarchColors
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
    private val sessionId: Long,
) : ViewModel() {

    // Records exclude THIS session: a live set must never become its own benchmark.
    val records: StateFlow<Map<Pair<String, Int>, SetRecords.Record>> = combine(
        repo.observeHistory(),
        repo.observeStats().map { SetRecords.bodyweightLookup(it) },
    ) { history, bodyweightAt ->
        SetRecords.records(history, bodyweightAt, excludeSessionId = sessionId)
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

    val bodyweight: StateFlow<Double?> = repo.observeStats()
        .map { it.firstOrNull()?.weightKg }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun updateSet(setId: Long, reps: Int, weightKg: Double?, done: Boolean) {
        viewModelScope.launch { repo.updateSet(setId, reps, weightKg, done) }
    }

    fun addSet(exerciseId: Long, reps: Int, weightKg: Double?, modifiers: String) {
        viewModelScope.launch { repo.addExtraSet(sessionId, exerciseId, reps, weightKg, modifiers) }
    }

    fun removeSet(setId: Long) {
        viewModelScope.launch { repo.removeSet(setId) }
    }

    fun setModifiers(exerciseId: Long, modifiers: String) {
        viewModelScope.launch { repo.setExerciseModifiers(sessionId, exerciseId, modifiers) }
    }

    fun addExercise(exerciseId: Long, name: String) {
        viewModelScope.launch { repo.addExtraSet(sessionId, exerciseId, 10, null, "") }
    }

    fun complete(onResult: (Repository.CompletionResult) -> Unit) {
        viewModelScope.launch { onResult(repo.completeSession(sessionId)) }
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

@Composable
fun SessionScreen(
    sessionId: Long,
    onExit: () -> Unit,
    viewModel: SessionViewModel =
        viewModel(factory = viewModelFactory { initializer { SessionViewModel(monarchRepository(), sessionId) } }),
) {
    val records by viewModel.records.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val exercises by viewModel.exercises.collectAsStateWithLifecycle()
    val bodyweight by viewModel.bodyweight.collectAsStateWithLifecycle()
    var completion by remember { mutableStateOf<Repository.CompletionResult?>(null) }
    var confirmAbandon by remember { mutableStateOf(false) }
    var showExercisePicker by remember { mutableStateOf(false) }
    var editModifiersFor by remember { mutableStateOf<Long?>(null) }

    val session = ui.session
    if (session == null) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Spacer(Modifier.height(20.dp))
            Text("Summoning session…", style = MaterialTheme.typography.bodySmall, color = MonarchColors.InkMuted)
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
            color = MonarchColors.SystemGreen,
            letterSpacing = 6.sp,
        )
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(session.label, style = MaterialTheme.typography.headlineSmall, color = MonarchColors.EmeraldBright)
                Text(
                    "Started ${formatDate(session.startedAtMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MonarchColors.InkMuted,
                )
            }
            Text(
                "ABANDON",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = ChakraPetch,
                color = MonarchColors.DangerRed,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.extraSmall)
                    .inkBorder(MonarchColors.DangerRed.copy(alpha = 0.6f), MaterialTheme.shapes.extraSmall, 1.dp)
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
            color = MonarchColors.SystemGreen,
        )

        ui.sets.groupBy { it.exerciseId }.forEach { (exerciseId, sets) ->
            val first = sets.first()
            val doneSets = sets.filter { it.done }
            val groupStrength = bodyweight?.let { bw ->
                doneSets.sumOf { StrengthIndex.repScore(it.reps, it.weightKg, bw) }.toInt()
            }
            Spacer(Modifier.height(14.dp))
            SystemWindow(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(first.exerciseName, style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (first.modifiers.isBlank()) "tap to set modifiers"
                            else first.modifiers.split(",").joinToString(" · ") { it.trim() },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (first.modifiers.isBlank()) MonarchColors.InkMuted else MonarchColors.SystemGreen,
                            modifier = Modifier
                                .clickable { editModifiersFor = exerciseId }
                                .padding(vertical = 2.dp),
                        )
                    }
                    if (groupStrength != null && groupStrength > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Bolt,
                                contentDescription = null,
                                tint = MonarchColors.SovereignGold,
                                modifier = Modifier.height(16.dp),
                            )
                            Text(
                                "$groupStrength",
                                style = MaterialTheme.typography.labelLarge,
                                fontFamily = ChakraPetch,
                                color = MonarchColors.SovereignGold,
                            )
                        }
                    }
                    IconButton(onClick = { editModifiersFor = exerciseId }) {
                        Icon(Icons.Outlined.Tune, contentDescription = "Edit modifiers", tint = MonarchColors.InkMuted)
                    }
                    IconButton(onClick = { viewModel.addSet(exerciseId, first.reps, first.weightKg, first.modifiers) }) {
                        Icon(Icons.Filled.Add, contentDescription = "Add set", tint = MonarchColors.SystemGreen)
                    }
                }
                sets.sortedBy { it.setIndex }.forEach { set ->
                    SetRow(
                        label = "${set.setIndex + 1}",
                        exerciseName = set.exerciseName,
                        setIndex = set.setIndex,
                        records = records,
                        bodyweight = bodyweight,
                        reps = set.reps,
                        weightKg = set.weightKg,
                        done = set.done,
                        // the final set stays: drop the exercise instead of emptying it
                        onRemove = if (sets.size > 1) ({ viewModel.removeSet(set.id) }) else null,
                        onChange = { r, w, d -> viewModel.updateSet(set.id, r, w, d) },
                    )
                }

            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            "+ add a one-off exercise",
            style = MaterialTheme.typography.labelLarge,
            fontFamily = ChakraPetch,
            color = MonarchColors.SystemGreen,
            modifier = Modifier
                .clip(MaterialTheme.shapes.extraSmall)
                .clickable { showExercisePicker = true }
                .padding(horizontal = 4.dp, vertical = 6.dp),
        )
        Spacer(Modifier.height(16.dp))
        SessionNotesEditor(session = session, viewModel = viewModel)

        Spacer(Modifier.height(16.dp))
        MonarchButton(
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
                }) { Text("Abandon", color = MonarchColors.DangerRed) }
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
                    onPick = { exercise ->
                        viewModel.addExercise(exercise.id, exercise.name)
                        showExercisePicker = false
                    },
                    onDismiss = { showExercisePicker = false },
                )
            },
            confirmButton = {},
        )
    }

    var awards by remember { mutableStateOf<List<Achievement>>(emptyList()) }

    completion?.let { result ->
        VictoryOverlay(
            result = result,
            onContinue = {
                awards = buildList {
                    if (result.levelAfter > result.levelBefore) {
                        add(
                            Achievement(
                                banner = "LEVEL UP",
                                tagline = "HUNTER RANK",
                                name = "Level ${result.levelAfter}",
                                subtitle = "${result.totalXp} XP TOTAL",
                                accent = MonarchColors.SystemGreen,
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
                                subtitle = title.description.uppercase(),
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
}

@Composable
private fun VictoryOverlay(
    result: Repository.CompletionResult,
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
                        color = (if (i % 3 == 0) MonarchColors.SovereignGold else MonarchColors.SystemGreen).copy(alpha = alpha),
                        seed = i,
                    )
                }
            }
            SystemWindow(
                Modifier
                    .padding(horizontal = 28.dp)
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = scale.value
                        scaleY = scale.value
                        alpha = appear.value
                    },
                accent = MonarchColors.SovereignGold,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "VICTORY",
                        style = MaterialTheme.typography.displaySmall,
                        fontFamily = ChakraPetch,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 10.sp,
                        color = MonarchColors.SovereignGold,
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
                        color = MonarchColors.InkMuted,
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
                    MonarchButton(
                        label = "Continue",
                        onClick = onContinue,
                        modifier = Modifier.fillMaxWidth(),
                    )
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
            color = MonarchColors.InkMuted,
            letterSpacing = 2.sp,
        )
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = MonarchColors.SovereignGold,
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
    onRemove: (() -> Unit)? = null,
    onChange: (Int, Double?, Boolean) -> Unit,
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
                    onValueChange = { onChange(reps, weightKg, it) },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(26.dp)
                    .background(
                        if (done) MonarchColors.SystemGreen.copy(alpha = 0.18f) else Color.Transparent,
                        MaterialTheme.shapes.extraSmall,
                    )
                    .inkBorder(
                        if (done) MonarchColors.SystemGreen else MonarchColors.Bracket,
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
                        color = MonarchColors.SystemGreen,
                    )
                }
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = ChakraPetch,
            color = MonarchColors.SystemGreen,
            modifier = Modifier.padding(end = 2.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                "LOAD",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = ChakraPetch,
                fontSize = 8.sp,
                letterSpacing = 1.sp,
                color = MonarchColors.InkMuted,
            )
            Spacer(Modifier.height(2.dp))
            Stepper(
                value = formatKg(weightKg),
                onMinus = { onChange(reps, stepDownKg(weightKg), done) },
                onPlus = { onChange(reps, stepUpKg(weightKg), done) },
                dimmed = done,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                "REPS",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = ChakraPetch,
                fontSize = 8.sp,
                letterSpacing = 1.sp,
                color = MonarchColors.InkMuted,
            )
            Spacer(Modifier.height(2.dp))
            Stepper(
                value = reps.toString(),
                onMinus = { onChange((reps - 1).coerceAtLeast(0), weightKg, done) },
                onPlus = { onChange(reps + 1, weightKg, done) },
                dimmed = done,
                modifier = Modifier.fillMaxWidth(),
            )
        }
            onRemove?.let { remove ->
                Text(
                    "✕",
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = ChakraPetch,
                    color = MonarchColors.InkMuted,
                    modifier = Modifier
                        .clickable { remove() }
                        .padding(horizontal = 6.dp, vertical = 10.dp),
                )
            }
        }
        // Fixed-height delta line under the steppers: always allocated, so
        // live digit changes never reflow the row mid-set.
        val delta = bodyweight?.let { bw ->
            SetRecords.delta(records, exerciseName, setIndex, reps, weightKg, bw)
        }
        SetDeltaBadge(delta, displaySetNo = setIndex + 1)
    }
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
                tint = MonarchColors.SovereignGold,
                modifier = Modifier.size(12.dp),
            )
            Text(
                "NEW PR",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = ChakraPetch,
                color = MonarchColors.SovereignGold,
            )
            record?.let {
                Text(
                    "was ${it.reps}×${prLoad(it.weightKg)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MonarchColors.InkMuted,
                )
            }
        } else if (record == null) {
            Text(
                "first set $displaySetNo on record",
                style = MaterialTheme.typography.labelSmall,
                color = MonarchColors.InkMuted,
            )
        } else {
            Text(
                "PR ${record.reps}×${prLoad(record.weightKg)}",
                style = MaterialTheme.typography.labelSmall,
                color = MonarchColors.InkMuted,
            )
            // Shortfall is muted, never red: a lighter back-off set is normal.
            val colour = if (delta.deltaScore >= 0.0) MonarchColors.EmeraldBright else MonarchColors.InkMuted
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
            .background(MonarchColors.Abyss)
            .inkBorder(MonarchColors.Rune, MaterialTheme.shapes.extraSmall, 1.dp)
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
            color = if (dimmed) MonarchColors.InkMuted else MonarchColors.Ink,
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
        color = MonarchColors.SystemGreen,
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
    // the flow back into the fields (that would fight the cursor).
    var title by remember(session.id) { mutableStateOf(session.title) }
    var publicNote by remember(session.id) { mutableStateOf(session.note) }
    var privateNote by remember(session.id) { mutableStateOf(session.privateNote) }

    SystemWindow(accent = MonarchColors.SovereignGold) {
        Text(
            "CHRONICLE",
            style = MaterialTheme.typography.labelLarge,
            fontFamily = ChakraPetch,
            color = MonarchColors.SovereignGold,
            letterSpacing = MonarchTracking.SectionHeader,
        )
        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            shape = MaterialTheme.shapes.small,
            value = title,
            onValueChange = { title = it.take(TITLE_CAP) },
            singleLine = true,
            label = {
                FieldLabel(
                    icon = { Icon(Icons.Outlined.Public, null, Modifier.size(14.dp), tint = MonarchColors.SovereignGold) },
                    text = "TITLE · optional, shown on the feed",
                    color = MonarchColors.SovereignGold,
                )
            },
            placeholder = { Text("Name this trial…", style = MaterialTheme.typography.bodySmall, color = MonarchColors.InkMuted) },
            trailingIcon = { CharCounter(title.length, TITLE_CAP, MonarchColors.SovereignGold) },
            colors = fieldColors(accent = MonarchColors.SovereignGold),
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
                    icon = { Icon(Icons.Outlined.Public, null, Modifier.size(14.dp), tint = MonarchColors.SystemGreen) },
                    text = "PUBLIC NOTE · every hunter on the feed can read this",
                    color = MonarchColors.SystemGreen,
                )
            },
            placeholder = { Text("How did the trial go? Share it…", style = MaterialTheme.typography.bodySmall, color = MonarchColors.InkMuted) },
            trailingIcon = { CharCounter(publicNote.length, PUBLIC_NOTE_CAP, MonarchColors.SystemGreen) },
            colors = fieldColors(accent = MonarchColors.SystemGreen),
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
                    icon = { Icon(Icons.Outlined.Lock, null, Modifier.size(14.dp), tint = MonarchColors.InkMuted) },
                    text = "PRIVATE NOTE · never leaves this device",
                    color = MonarchColors.InkMuted,
                )
            },
            placeholder = { Text("For your eyes only…", style = MaterialTheme.typography.bodySmall, color = MonarchColors.InkMuted) },
            colors = fieldColors(accent = MonarchColors.InkMuted),
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
        color = if (near) MonarchColors.DangerRed else accent,
    )
}
@Composable
private fun fieldColors(accent: Color) = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = accent,
    unfocusedBorderColor = accent.copy(alpha = 0.4f),
    focusedLabelColor = accent,
    unfocusedLabelColor = MonarchColors.InkMuted,
    cursorColor = accent,
    focusedTextColor = MonarchColors.Ink,
    unfocusedTextColor = MonarchColors.Ink,
)

/** Canonical modifier vocabulary — free text drifted ("defecit" vs "deficit"). */
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
                    color = MonarchColors.InkMuted,
                    letterSpacing = 2.sp,
                )
                Text(exerciseName, style = MaterialTheme.typography.titleMedium, color = MonarchColors.Ink)
            }
        },
        text = {
            Column {
                Text(
                    "Applies to every set of this movement.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MonarchColors.InkMuted,
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
                                    .inkBorder(if (on) MonarchColors.SystemGreen else MonarchColors.Rune, shape, 1.dp)
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
                                    color = if (on) MonarchColors.Ink else MonarchColors.InkMuted,
                                    maxLines = 1,
                                )
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        },
        confirmButton = { MonarchButton("Save", onClick = { onConfirm(picked) }) },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
