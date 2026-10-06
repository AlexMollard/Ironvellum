package com.ironvellum.app.ui.train

import androidx.compose.material.icons.outlined.FitnessCenter
import com.ironvellum.app.ui.components.InkChip
import com.ironvellum.app.ui.components.InkDivider
import com.ironvellum.app.ui.components.ListRow
import com.ironvellum.app.ui.components.ListRowHeight
import com.ironvellum.app.ui.components.formatDate
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Add
import com.ironvellum.app.ui.theme.inkBorder
import com.ironvellum.app.ui.dashboard.trialLength
import kotlin.math.ceil
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextOverflow
import com.ironvellum.app.domain.TrainFocus
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ironvellum.app.data.Repository
import com.ironvellum.app.domain.VolumeLevel
import com.ironvellum.app.domain.Muscle
import com.ironvellum.app.domain.PlannedPreset
import com.ironvellum.app.domain.ProgramRules
import com.ironvellum.app.domain.TrainingFocus
import com.ironvellum.app.domain.SessionClock
import com.ironvellum.app.domain.WorkoutPreset
import com.ironvellum.app.domain.RoutineCode
import com.ironvellum.app.ui.components.SectionHeader
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.program.CoverageGoal
import com.ironvellum.app.ui.program.coverageGaps
import com.ironvellum.app.ui.program.toPlanned
import com.ironvellum.app.ui.ironvellumRepository
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.IronvellumTracking
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import com.ironvellum.app.ui.launchGuarded
import kotlinx.coroutines.launch
import java.time.LocalDate

data class TrainUi(
    val presets: List<WorkoutPreset> = emptyList(),
    val history: List<Pair<com.ironvellum.app.domain.WorkoutSession, List<com.ironvellum.app.domain.SessionSet>>> = emptyList(),
    /** Scheduled presets (or all of them), in planned form, for the coverage card. */
    val plannedPresets: List<PlannedPreset> = emptyList(),
    val tier: VolumeLevel = VolumeLevel.LOW,
    val focus: TrainingFocus = TrainingFocus.MUSCLE,
    val priorities: Set<com.ironvellum.app.domain.Muscle> = emptySet(),
    /** The lifter's own seconds per set; times the card estimates. */
    val pace: SessionClock.Pace = SessionClock.Pace(),
)

class PresetsViewModel(
    private val repo: Repository,
    appContext: android.content.Context,
) : ViewModel() {

    /** What she last told the generator; when present it outranks the
     *  history/profile guesses, which said "beginner, strength" minutes
     *  after she accepted an INTERMEDIATE / MUSCLE week. */
    private val savedAnswers = com.ironvellum.app.data.ProgramAnswersStore.get(appContext)

    /** Training age is derived once, not per emission; it only grows. */
    private val tierFlow = flow {
        emit(
            savedAnswers?.volume
                ?: ProgramRules.suggestVolume(repo.firstSessionEpochDay(), LocalDate.now().toEpochDay()),
        )
    }

    val ui: StateFlow<TrainUi> = combine(
        repo.observePresets(),
        repo.observeHistory(),
        repo.observeProfile(),
        tierFlow,
    ) { presets, history, profile, tier ->
        // The coverage card maps the ROUTINE: scheduled days when they exist,
        // the whole board when nothing is scheduled.
        val routine = presets.filter { it.scheduledDay != null }.ifEmpty { presets }
        TrainUi(
            presets = weekOrder(presets),
            history = history,
            plannedPresets = routine.map { it.toPlanned() },
            tier = tier,
            focus = SessionClock.focusFor(savedAnswers?.focus, profile?.trainingMode),
            priorities = savedAnswers?.priorities.orEmpty().flatMap { it.muscles }.toSet(),
            pace = SessionClock.pace(history),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrainUi())

    /** The unfinished session: its preset's button reads Continue. */
    val live: StateFlow<com.ironvellum.app.domain.WorkoutSession?> = repo.observeLiveSession()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun begin(presetId: Long, onStarted: (Long) -> Unit) {
        viewModelScope.launchGuarded("begin rite") { onStarted(repo.startSessionFromPreset(presetId)) }
    }

    fun beginQuick(onStarted: (Long) -> Unit) {
        viewModelScope.launch { onStarted(repo.startFreeformSession("Open Trial")) }
    }

    fun shareRoutine(onRefused: (String) -> Unit, onCode: (String) -> Unit) {
        viewModelScope.launchGuarded("share cycle") {
            val routine = repo.sharedRoutine()
            val refusal = RoutineCode.shareRefusal(routine)
            if (refusal != null) onRefused(refusal) else onCode(RoutineCode.encode(routine))
        }
    }

    /** The write is one transaction, so a failure leaves the routine as it was. */
    fun importRoutine(workouts: List<RoutineCode.SharedWorkout>, replace: Boolean, onDone: (String) -> Unit) {
        viewModelScope.launch {
            val summary = runCatching { importSummary(repo.importSharedRoutine(workouts, replace), replace) }
                .getOrElse { "Import failed \u2014 nothing was changed." }
            onDone(summary)
        }
    }
}

/**
 * The board in week order: Mon..Sun by scheduled day, unscheduled workouts
 * last. Stable, so ties keep the incoming (by-name) order.
 */
internal fun weekOrder(presets: List<WorkoutPreset>): List<WorkoutPreset> =
    presets.sortedBy { it.scheduledDay ?: Int.MAX_VALUE }

private val DAY_LABELS = mapOf(1 to "MON", 2 to "TUE", 3 to "WED", 4 to "THU", 5 to "FRI", 6 to "SAT", 7 to "SUN")

internal fun dayLabel(day: Int): String = DAY_LABELS[day].orEmpty()

internal fun formatKg(kg: Double?): String =
    when {
        kg == null -> "BW"
        kg == kg.toLong().toDouble() -> "${kg.toLong()}kg"
        else -> "${kg}kg"
    }

@Composable
fun PresetsScreen(
    onOpenRite: (Long) -> Unit,
    onNew: () -> Unit,
    onGenerate: (mode: String, presetId: Long?) -> Unit,
    onStartSession: (Long) -> Unit,
    onQuickSession: (Long) -> Unit,
    onOpenExercises: () -> Unit,
    onOpenLog: () -> Unit,
    onOpenCoverage: () -> Unit,
    onOpenWorkout: (Long) -> Unit = {},
    viewModel: PresetsViewModel =
        viewModel(factory = viewModelFactory {
            val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
            initializer { PresetsViewModel(ironvellumRepository(), appContext) }
        }),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val live by viewModel.live.collectAsStateWithLifecycle()
    var showNewChooser by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    // Survives only the composition: a stale "Added 3 workouts" after leaving
    // and returning to Train would read as a fresh import.
    var importResult by remember { mutableStateOf<String?>(null) }
    var shareRefusal by remember { mutableStateOf<String?>(null) }

    val zone = java.time.ZoneId.systemDefault()
    val todayDate = LocalDate.now()
    val today = todayDate.dayOfWeek.value
    val todayStart = todayDate.atStartOfDay(zone).toInstant().toEpochMilli()
    val weekStart = todayDate.minusDays((today - 1).toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
    val sessions = ui.history.map { it.first }
    val focus = TrainFocus.resolve(ui.presets, sessions, live, today, todayStart, weekStart)

    var recentRows by remember { mutableStateOf(0) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewport = maxHeight
        val available = ui.history.count { it.first.completedAtMs != null }.coerceAtMost(RECENT_MAX)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                // The page's own min height would make the content read as at
                // least the viewport, hiding any room left; measure it bare.
                .wrapContentHeight(Alignment.Top)
                .onSizeChanged { size ->
                    val slack = viewport - with(density) { size.height.toDp() }
                    // One trial always shows when there is one - a near miss on the
                    // fit left a third of the screen bare - and more fill the room.
                    recentRows = when {
                        // Over: give back whole rows until it fits.
                        slack < 0.dp -> recentRows - ceil(-slack / RECENT_PITCH).toInt()
                        // Under: take whole rows; the first also pays for the card.
                        else -> recentRows + ((slack - if (recentRows == 0) RECENT_CHROME else 0.dp) / RECENT_PITCH).toInt().coerceAtLeast(0)
                    }.coerceIn(minOf(1, available), available)
                }
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(12.dp))
            val context = androidx.compose.ui.platform.LocalContext.current
            val share = {
                shareRefusal = null
                viewModel.shareRoutine(onRefused = { shareRefusal = it }) { code -> shareRoutineCode(context, code) }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "TRAIN",
                    style = MaterialTheme.typography.headlineSmall,
                    fontFamily = ChakraPetch,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = IronvellumColors.Ink,
                    letterSpacing = 1.sp,
                    // The one heading on an empty board, where the cycle's is hidden.
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                // No second trial while one is under way.
                if (focus !is TrainFocus.Live) InkChip("Open trial", "Begin an open trial", Icons.Outlined.PlayArrow) { viewModel.beginQuick(onQuickSession) }
            }
            importResult?.let { line ->
                Text(line, style = MaterialTheme.typography.bodySmall, color = IronvellumColors.SystemGreen, modifier = Modifier.padding(top = GROUP_GAP))
            }
            // The lead card is kept for the two states the cycle cannot show: no
            // cycle at all, and an open trial under way, which belongs to no rite.
            if (focus is TrainFocus.NoCycle || (focus is TrainFocus.Live && focus.preset == null)) {
                Spacer(Modifier.height(GROUP_GAP))
                TrainLeadCard(
                    focus = focus,
                    ui = ui,
                    today = today,
                    onOpenRite = onOpenRite,
                    onBegin = { id -> viewModel.begin(id, onStartSession) },
                    onContinue = onStartSession,
                    onForge = { showNewChooser = true },
                    last = sessions.filter { it.completedAtMs != null }.maxByOrNull { it.completedAtMs ?: 0L },
                )
            }
            if (ui.presets.isNotEmpty()) {
            Spacer(Modifier.height(SECTION_GAP))
            val sealedThisWeek = { preset: WorkoutPreset -> sessions.any { it.presetId == preset.id && (it.completedAtMs ?: 0L) >= weekStart } }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "YOUR CYCLE",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = ChakraPetch,
                    color = IronvellumColors.InkMuted,
                    letterSpacing = IronvellumTracking.SectionHeader,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.weight(1f))
                // Sit with the list they add to and export.
                InkChip("New", "New rite", Icons.Outlined.Add) { showNewChooser = true }
                InkChip("Share", "Share cycle", Icons.Outlined.Share, onClick = share)
            }
            shareRefusal?.let { line ->
                Text(line, style = MaterialTheme.typography.bodySmall, color = IronvellumColors.DangerRed, modifier = Modifier.padding(bottom = 8.dp))
            }
            // Each rite is one line, the whole of it one tap away on its page,
            // where it is begun; today's is begun from Today.
            InkPanel(Modifier.fillMaxWidth()) {
                ui.presets.forEach { preset ->
                    val sealed = sealedThisWeek(preset)
                    val isToday = preset.scheduledDay == today
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.extraSmall)
                            .clickable(onClickLabel = "Open ${preset.name}") { onOpenRite(preset.id) }
                            .heightIn(min = ROW_HEIGHT),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            preset.scheduledDay?.let(::dayLabel) ?: "—",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = ChakraPetch,
                            letterSpacing = IronvellumTracking.InlineLabel,
                            color = when {
                                isToday -> IronvellumColors.SovereignGold
                                sealed -> IronvellumColors.SystemGreen
                                else -> IronvellumColors.InkMuted
                            },
                            modifier = Modifier.width(DAY_COLUMN),
                        )
                        Text(
                            preset.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isToday) IronvellumColors.EmeraldBright else IronvellumColors.Ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            when {
                                sealed -> "\u2713 Sealed"
                                isToday && focus is TrainFocus.Live -> "Under way"
                                isToday -> "Today"
                                // "~54 min": the tail of the rite's plan line.
                                else -> SessionClock.planLine(preset.toPlanned().entries, ui.focus, ui.pace.secondsPerSet(preset.id))
                                    .substringAfterLast(" · ").lowercase()
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = ChakraPetch,
                            color = when {
                                sealed -> IronvellumColors.SystemGreen
                                isToday -> IronvellumColors.SovereignGold
                                else -> IronvellumColors.InkMuted
                            },
                            maxLines = 1,
                        )
                    }
                    if (preset != ui.presets.last()) InkDivider()
                }
            }
            }

            Spacer(Modifier.height(SECTION_GAP))
            // The latest trials fill what the page has left, a whole row at a
            // time. Measured, not estimated - content over the viewport is
            // exactly the rows to give back - so it settles in a pass.
            val trials = ui.history.filter { it.first.completedAtMs != null }
                .sortedByDescending { it.first.completedAtMs }
                .take(recentRows)
            val volume = remember(ui.plannedPresets) { ProgramRules.weeklyVolume(ui.plannedPresets) }
            val gaps = coverageGaps(volume, CoverageGoal(ui.tier, ui.focus, ui.priorities)).size
            // The ways out: what the cycle covers, the catalogue, the record. Rows
            // rather than buttons, so nothing here competes with Begin.
            InkPanel(Modifier.fillMaxWidth()) {
                WayOutRow(
                    icon = Icons.Outlined.AccessibilityNew,
                    label = "Weekly coverage",
                    detail = when {
                        ui.plannedPresets.isEmpty() -> "No cycle yet"
                        gaps == 0 -> "Every muscle covered"
                        else -> "$gaps short or missing"
                    },
                    detailColor = if (gaps > 0) IronvellumColors.SovereignGold else IronvellumColors.InkMuted,
                    onClick = onOpenCoverage,
                )
                InkDivider()
                WayOutRow(icon = Icons.Outlined.FitnessCenter, label = "Exercises", onClick = onOpenExercises)
                // The recent trials card links the same chronicle from its header,
                // so the row only stands when that card does not.
                if (trials.isEmpty()) {
                    InkDivider()
                    WayOutRow(icon = Icons.Outlined.History, label = "Full chronicle", onClick = onOpenLog)
                }
            }

            if (trials.isNotEmpty()) {
                Spacer(Modifier.height(SECTION_GAP))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "RECENT TRIALS",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = ChakraPetch,
                        color = IronvellumColors.InkMuted,
                        letterSpacing = IronvellumTracking.SectionHeader,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                    // Named for where it goes: "All" alone says nothing to a screen reader.
                    InkChip("All", "Open", description = "Full chronicle", onClick = onOpenLog)
                }
                InkPanel(Modifier.fillMaxWidth()) {
                    trials.forEach { (trial, sets) ->
                        if (trial != trials.first().first) InkDivider()
                        TrialRow(trial, sets) { onOpenWorkout(trial.id) }
                    }
                }
            }
            Spacer(Modifier.height(END_GAP))
        }
    }

    if (showNewChooser) {
        // Material's dialog container is a 28dp rounded rect - the most
        // obviously stock surface in the app. Give it the ink shape and the
        // dark container the session dialogs use.
        AlertDialog(
            shape = MaterialTheme.shapes.medium,
            containerColor = androidx.compose.ui.graphics.Color(0xFF0D1110),
            onDismissRequest = { showNewChooser = false },
            title = {
                Text(
                    "NEW RITE",
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = ChakraPetch,
                    color = IronvellumColors.SystemGreen,
                    letterSpacing = IronvellumTracking.InlineLabel,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Easiest first, each with one plain line, so someone new
                    // can tell the Forge from a blank page without trying both.
                    val options = buildList {
                        add(Triple("Forge a Cycle", "A full week built from your answers.") { showNewChooser = false; onGenerate("week", null) })
                        add(Triple("Forge a Rite", "One rite, built for today.") { showNewChooser = false; onGenerate("session", null) })
                        add(Triple("Start from a pattern", "A hand-written weekly cycle to adjust.") { showNewChooser = false; onGenerate("template", null) })
                        // Improving needs a target: nothing to improve on an
                        // empty board.
                        if (ui.presets.isNotEmpty()) {
                            add(Triple("Temper a Rite", "Improve one you already have.") { showNewChooser = false; onGenerate("improve", null) })
                        }
                        add(Triple("Blank Rite", "Pick every exercise yourself.") { showNewChooser = false; onNew() })
                        add(Triple("Import code", "Paste a cycle someone shared with you.") { showNewChooser = false; showImport = true })
                    }
                    options.forEach { (label, detail, action) ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.extraSmall)
                                .clickable(onClick = action)
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                        ) {
                            Text(label, style = MaterialTheme.typography.bodyLarge, color = IronvellumColors.Ink)
                            Text(detail, style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                IronvellumButton(label = "Cancel", onClick = { showNewChooser = false }, quiet = true)
            },
        )
    }

    if (showImport) {
        ImportRoutineDialog(
            currentWorkouts = ui.presets.size,
            onImport = { workouts, replace ->
                showImport = false
                viewModel.importRoutine(workouts, replace) { importResult = it }
            },
            onDismiss = { showImport = false },
        )
    }
}

/** "Wed" for 3. */
private fun dayName(day: Int): String = dayLabel(day).lowercase().replaceFirstChar { it.uppercase() }

@Composable
private fun TrialRow(
    trial: com.ironvellum.app.domain.WorkoutSession,
    sets: List<com.ironvellum.app.domain.SessionSet>,
    onOpen: () -> Unit,
) {
    val at = trial.completedAtMs ?: trial.startedAtMs
    val done = sets.filter { it.done }
    val volumeKg = done.sumOf { (it.weightKg ?: 0.0) * it.reps }
    val detail = listOfNotNull(
        trialLength(trial),
        done.size.takeIf { it > 0 }?.let { if (it == 1) "1 set" else "$it sets" },
        volumeKg.takeIf { it > 0.0 }?.let { if (it < 1000) "${it.toInt()} kg" else "${"%.1f".format(java.util.Locale.US, it / 1000)} t" },
    ).joinToString(" · ")
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraSmall)
            .clickable(onClickLabel = "Open ${trial.label}", onClick = onOpen)
            .heightIn(min = TRIAL_ROW_HEIGHT),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            Modifier
                .width(DAY_TILE)
                .inkBorder(IronvellumColors.Rune, MaterialTheme.shapes.extraSmall, 1.dp)
                .padding(vertical = 3.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                formatDate(at, "EEE").uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = ChakraPetch,
                color = IronvellumColors.InkMuted,
                fontSize = 9.sp,
            )
            Text(formatDate(at, "d"), style = MaterialTheme.typography.titleMedium, fontFamily = ChakraPetch, color = IronvellumColors.Ink)
        }
        Column(Modifier.weight(1f)) {
            Text(trial.label, style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (detail.isNotEmpty()) {
                Text(detail, style = MaterialTheme.typography.labelSmall, fontFamily = ChakraPetch, color = IronvellumColors.InkMuted, maxLines = 1)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("+${trial.xpAwarded} XP", style = MaterialTheme.typography.labelMedium, fontFamily = ChakraPetch, color = IronvellumColors.SovereignGold)
            Text("STR ${trial.strengthScore}", style = MaterialTheme.typography.labelSmall, fontFamily = ChakraPetch, color = IronvellumColors.InkMuted)
        }
    }
}

/**
 * Train's lead: the one rite to act on now, with its button. The body opens
 * the rite's page; the button begins or continues it there and then.
 */
@Composable
private fun TrainLeadCard(
    focus: TrainFocus,
    ui: TrainUi,
    today: Int,
    onOpenRite: (Long) -> Unit,
    onBegin: (Long) -> Unit,
    onContinue: (Long) -> Unit,
    onForge: () -> Unit,
    last: com.ironvellum.app.domain.WorkoutSession?,
) {
    val rite = when (focus) {
        is TrainFocus.Live -> focus.preset
        is TrainFocus.Begin -> focus.preset
        is TrainFocus.Sealed -> focus.preset
        is TrainFocus.Respite -> focus.next
        TrainFocus.NoCycle -> null
    }
    InkPanel(
        Modifier.fillMaxWidth(),
        onClick = rite?.let { { onOpenRite(it.id) } },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                when (focus) {
                    is TrainFocus.Live -> "TRIAL IN PROGRESS"
                    else -> "TODAY · ${dayLabel(today)}"
                },
                style = MaterialTheme.typography.labelSmall,
                fontFamily = ChakraPetch,
                color = IronvellumColors.SovereignGold,
                letterSpacing = IronvellumTracking.SectionHeader,
                maxLines = 1,
            )
            Spacer(Modifier.weight(1f))
            if (focus !is TrainFocus.Live && last != null) {
                Text(
                    "LAST · ${last.label} · ${formatDate(last.completedAtMs ?: last.startedAtMs, "MMM d")}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = ChakraPetch,
                    color = IronvellumColors.InkMuted,
                    letterSpacing = IronvellumTracking.InlineLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
        Text(
            when (focus) {
                is TrainFocus.Live -> (focus.preset?.name ?: focus.session.label).uppercase()
                is TrainFocus.Begin -> focus.preset.name.uppercase()
                is TrainFocus.Sealed -> focus.preset.name.uppercase()
                is TrainFocus.Respite -> "RESPITE"
                TrainFocus.NoCycle -> "NO CYCLE YET"
            },
            style = MaterialTheme.typography.headlineSmall,
            fontFamily = ChakraPetch,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            color = IronvellumColors.EmeraldBright,
            letterSpacing = 1.sp,
        )
        val detail = when (focus) {
            is TrainFocus.Begin, is TrainFocus.Live -> rite?.let {
                SessionClock.planLine(it.toPlanned().entries, ui.focus, ui.pace.secondsPerSet(it.id))
            }
            is TrainFocus.Sealed -> "SEALED · +${focus.session.xpAwarded} XP · ${focus.session.strengthScore} STR"
            is TrainFocus.Respite -> focus.next?.let { "NEXT · ${it.name.uppercase()} · ${dayLabel(focus.nextDay ?: 0)}" }
            TrainFocus.NoCycle -> null
        }
        detail?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = ChakraPetch,
                color = if (focus is TrainFocus.Sealed) IronvellumColors.SovereignGold else IronvellumColors.InkMuted,
                letterSpacing = IronvellumTracking.InlineLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        // The first few movements, enough to recognise the rite; its page
        // carries the whole list.
        if (focus is TrainFocus.Begin || (focus is TrainFocus.Live && rite != null)) {
            rite?.entries?.let { entries ->
                Text(
                    entries.take(LEAD_MOVEMENTS).joinToString(" · ") { it.exerciseName } +
                        (if (entries.size > LEAD_MOVEMENTS) "  +${entries.size - LEAD_MOVEMENTS}" else ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.Ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        if (focus is TrainFocus.Respite && focus.next == null) {
            Text(
                "No rite falls on a day yet. Open one below or start an open trial.",
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (focus is TrainFocus.NoCycle) {
            Text(
                "Your cycle is unwritten. Forge a week, start from a pattern, or import one.",
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        when (focus) {
            is TrainFocus.Live -> LeadButton("Continue ${focus.preset?.name ?: focus.session.label}") { onContinue(focus.session.id) }
            is TrainFocus.Begin -> LeadButton("Begin ${focus.preset.name}") { onBegin(focus.preset.id) }
            // A respite is a suggestion, not a lock: the next rite can be taken
            // early, until a trial is sealed today.
            is TrainFocus.Respite -> if (focus.canTakeEarly && focus.next != null) {
                LeadButton("Begin ${focus.next.name} early", quiet = true) { onBegin(focus.next.id) }
            }
            TrainFocus.NoCycle -> LeadButton("Build a Cycle") { onForge() }
            is TrainFocus.Sealed -> Unit
        }
    }
}

@Composable
private fun LeadButton(label: String, quiet: Boolean = false, onClick: () -> Unit) {
    Spacer(Modifier.height(12.dp))
    IronvellumButton(label = label, onClick = onClick, quiet = quiet, modifier = Modifier.fillMaxWidth())
}

/** Movements named on the lead card before "+N". */
private const val LEAD_MOVEMENTS = 3

/** One way out of Train: icon, label, an optional fact, and a chevron. */
@Composable
private fun WayOutRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    detail: String? = null,
    detailColor: androidx.compose.ui.graphics.Color = IronvellumColors.InkMuted,
) {
    ListRow(label, icon = icon, subline = detail, sublineColor = detailColor, onClickLabel = label, onClick = onClick)
}

/** Train's rhythm, shared with the rite page: panels sit a section apart; controls inside a group, a group gap. */
internal val SECTION_GAP = 16.dp
internal val GROUP_GAP = 12.dp

/** The page's end clears the raised Train plate, which overhangs the bar by 10dp, with room to spare. */
private val END_GAP = 24.dp

/** A list row: the shared 52dp row height, the same in both panels. */
internal val ROW_HEIGHT = ListRowHeight

/** The cycle list's weekday column. */
private val DAY_COLUMN = 36.dp

/** A recent trial's row, and the calendar tile that leads it. */
private val TRIAL_ROW_HEIGHT = 56.dp
private val DAY_TILE = 38.dp

/**
 * Recent trials: a row and its rule, what the card costs before its first row
 * (the gap, the header and its 44dp link, the panel's padding), and the most it lists.
 */
private val RECENT_PITCH = TRIAL_ROW_HEIGHT + 2.dp
private val RECENT_CHROME = 92.dp
private const val RECENT_MAX = 6
