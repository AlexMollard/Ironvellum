package com.ironvellum.app.ui.train

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextOverflow
import com.ironvellum.app.domain.Evidence
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
import com.ironvellum.app.domain.MovementDifficulty
import com.ironvellum.app.domain.PlannedPreset
import com.ironvellum.app.domain.ProgramRules
import com.ironvellum.app.domain.TrainingFocus
import com.ironvellum.app.domain.SessionClock
import com.ironvellum.app.domain.WorkoutPreset
import com.ironvellum.app.domain.RoutineCode
import com.ironvellum.app.ui.components.SectionHeader
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.components.formatDate
import com.ironvellum.app.ui.program.BodyHeatMap
import com.ironvellum.app.ui.program.CoverageGoal
import com.ironvellum.app.ui.program.coverageGaps
import com.ironvellum.app.ui.program.toPlanned
import com.ironvellum.app.ui.ironvellumRepository
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.inkBorder
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.IronvellumTracking
import kotlinx.coroutines.flow.SharingStarted
import com.ironvellum.app.ui.components.NavChip
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

    fun shareRoutine(onCode: (String) -> Unit) {
        viewModelScope.launchGuarded("share cycle") { onCode(RoutineCode.encode(repo.sharedRoutine())) }
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

internal fun formatKg(kg: Double?): String =
    when {
        kg == null -> "BW"
        kg == kg.toLong().toDouble() -> "${kg.toLong()}kg"
        else -> "${kg}kg"
    }

@Composable
fun PresetsScreen(
    onEdit: (Long) -> Unit,
    onNew: () -> Unit,
    onGenerate: (mode: String, presetId: Long?) -> Unit,
    onStartSession: (Long) -> Unit,
    onQuickSession: (Long) -> Unit,
    onOpenExercises: () -> Unit,
    onOpenLog: () -> Unit,
    onOpenWorkout: (Long) -> Unit,
    onOpenCoverage: () -> Unit,
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

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(20.dp))
            Text(
                "YOUR RITES",
                style = MaterialTheme.typography.labelLarge,
                color = IronvellumColors.SystemGreen,
                letterSpacing = 6.sp,
            )
            Spacer(Modifier.height(12.dp))
            IronvellumButton(
                label = "Open Trial",
                onClick = { viewModel.beginQuick(onQuickSession) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            WeeklyCoverageCard(
                ui = ui,
                onOpen = onOpenCoverage,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            // The two navigation destinations sat here as full-width primary
            // buttons, fighting the one real action (Quick Session); they are
            // now quiet links below it.
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NavChip(
                    label = "EXERCISES",
                    icon = Icons.Outlined.FitnessCenter,
                    onClick = onOpenExercises,
                    modifier = Modifier.weight(1f),
                )
                NavChip(
                    label = "FULL CHRONICLE",
                    icon = Icons.Outlined.History,
                    onClick = onOpenLog,
                    modifier = Modifier.weight(1f),
                )
            }
            SectionHeader("Rites")
            if (ui.presets.isEmpty()) {
                Text(
                    "No rites are written yet. Build your first one.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            importResult?.let { line ->
                Text(
                    line,
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.SystemGreen,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }
            // The generator's rest-and-effort note is the same for every card,
            // so it prints once here, up to two lines, instead of ellipsising
            // on each card and burying the exercises under repeated prose.
            ui.presets.map { Evidence.split(it.note).first }
                .filter { it.isNotBlank() }
                .distinct()
                .forEach { note ->
                    Text(
                        note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            ui.presets.forEach { preset ->
                InkPanel(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top,
                    ) {
                        // Weighted and capped: a long name used to run into
                        // the day chip instead of wrapping beside it.
                        Text(
                            preset.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = IronvellumColors.Ink,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                        )
                        preset.scheduledDay?.let {
                            Box(
                                Modifier
                                    .clip(MaterialTheme.shapes.extraSmall)
                                    .inkBorder(IronvellumColors.SovereignGold, MaterialTheme.shapes.extraSmall, 1.dp)
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Text(DAY_LABELS[it] ?: "", style = MaterialTheme.typography.labelSmall, color = IronvellumColors.SovereignGold)
                            }
                        }
                    }
                    Text(
                        SessionClock.planLine(preset.toPlanned().entries, ui.focus, ui.pace.secondsPerSet(preset.id)),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = ChakraPetch,
                        color = IronvellumColors.InkMuted,
                        letterSpacing = IronvellumTracking.InlineLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    preset.entries.take(PRESET_CARD_MOVEMENTS).forEach { entry ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(entry.exerciseName, style = MaterialTheme.typography.bodyMedium)
                                if (entry.modifiers.isNotBlank()) {
                                    Text(
                                        entry.modifiers.split(",").joinToString(" · ") { it.trim() },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = IronvellumColors.SystemGreen,
                                    )
                                }
                            }
                            Text(
                                "${entry.targetSets}×${entry.targetReps}" +
                                    (if (MovementDifficulty.isHoldByName(entry.exerciseName)) "s" else "") +
                                    (entry.targetWeightKg?.let { "  @${formatKg(it)}" } ?: ""),
                                style = MaterialTheme.typography.labelLarge,
                                fontFamily = ChakraPetch,
                                color = IronvellumColors.InkMuted,
                            )
                        }
                    }
                    if (preset.entries.size > PRESET_CARD_MOVEMENTS) {
                        Text(
                            "+${preset.entries.size - PRESET_CARD_MOVEMENTS} MORE",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = ChakraPetch,
                            color = IronvellumColors.InkMuted,
                            letterSpacing = IronvellumTracking.InlineLabel,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .heightIn(min = 44.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClickLabel = "Edit ${preset.name}",
                                ) { onEdit(preset.id) },
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(
                                "[ EDIT ]",
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = ChakraPetch,
                                color = IronvellumColors.InkMuted,
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.extraSmall)
                                    .inkBorder(IronvellumColors.InkMuted.copy(alpha = 0.4f), MaterialTheme.shapes.extraSmall, 1.dp)
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                        val resume = live?.takeIf { it.presetId == preset.id }
                        if (resume != null) {
                            IronvellumButton("Continue", onClick = { onStartSession(resume.id) })
                        } else {
                            IronvellumButton("Begin", onClick = { viewModel.begin(preset.id, onStartSession) })
                        }
                    }
                }
            }

            // Sits with the list it exports, and only when there is something
            // to export: an empty board would share a code that imports nothing.
            if (ui.presets.isNotEmpty()) {
                val context = androidx.compose.ui.platform.LocalContext.current
                IronvellumButton(
                    label = "Share cycle",
                    onClick = { viewModel.shareRoutine { code -> shareRoutineCode(context, code) } },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    quiet = true,
                )
            }

            // "New Workout" used to float over the list, and it parked itself on
            // top of a card's BEGIN button. A tile at the end of the workouts
            // covers nothing and needs no clearance spacer underneath.
            InkPanel(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .heightIn(min = 48.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClickLabel = "New Rite",
                    ) { showNewChooser = true },
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = null,
                        tint = IronvellumColors.SystemGreen,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "NEW RITE",
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = ChakraPetch,
                        color = IronvellumColors.SystemGreen,
                        letterSpacing = IronvellumTracking.InlineLabel,
                    )
                }
            }

            // Was "Activity Log", which collided head-on with the Stats
            // screen's ACTIVITY tab (steps and sleep) - the owner kept going
            // there hunting for his workouts.
            SectionHeader("Recent Trials")
            if (ui.history.isEmpty()) {
                Text(
                    "The Chronicle is blank. Seal a trial and it lands here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Only the latest few: this screen is a plain scrolling Column, so
            // every row it lists is composed whether or not it is on screen.
            // After a few years of training that is a thousand rows built to
            // show the top five, and FULL LOG above already leads to
            // the complete, month-grouped history.
            ui.history.take(ACTIVITY_LOG_ROWS).forEach { (session, sets) ->
                // The same rows are tappable in the full log; here they only
                // informed. A completed row now opens what it names.
                InkPanel(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clickable { onOpenWorkout(session.id) },
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(session.label, style = MaterialTheme.typography.titleSmall)
                            Text(
                                formatDate(session.startedAtMs) + " · " +
                                    sets.count { it.done } + "/" + sets.size + " sets · " +
                                    sets.filter { it.done }.sumOf { it.reps } + " reps" +
                                    sets.filter { it.done }.sumOf { it.durationSec ?: 0 }
                                        .let { held -> if (held > 0) " · ${held}s held" else "" },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text("+${session.xpAwarded} XP", style = MaterialTheme.typography.labelLarge, color = IronvellumColors.SovereignGold)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
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
                    val options = buildList {
                        add("Blank Rite" to { showNewChooser = false; onNew() })
                        add("Start from a pattern" to { showNewChooser = false; onGenerate("template", null) })
                        add("Import code" to { showNewChooser = false; showImport = true })
                        add("Forge a Cycle" to { showNewChooser = false; onGenerate("week", null) })
                        add("Forge a Rite" to { showNewChooser = false; onGenerate("session", null) })
                        // Improving needs a target: nothing to improve on an
                        // empty board.
                        if (ui.presets.isNotEmpty()) {
                            add("Temper a Rite" to { showNewChooser = false; onGenerate("improve", null) })
                        }
                    }
                    options.forEach { (label, action) ->
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = IronvellumColors.Ink,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.extraSmall)
                                .clickable(onClick = action)
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                        )
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

/** Enough to show the week's work; the full log carries the rest. */
private const val ACTIVITY_LOG_ROWS = 6

/**
 * The at-a-glance muscle map. The planned routine only - the full view (with
 * the PLANNED / LAST 7 DAYS switch and per-muscle tiles) opens from here.
 * The figure gets a wide aspect so the card stays compact; the shapes are
 * normalised, so they stretch rather than clip.
 */
@Composable
private fun WeeklyCoverageCard(
    ui: TrainUi,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val volume = remember(ui.plannedPresets) { ProgramRules.weeklyVolume(ui.plannedPresets) }
    val goal = CoverageGoal(ui.tier, ui.focus, ui.priorities)
    val gaps = coverageGaps(volume, goal).size
    InkPanel(
        modifier
            // A min height keeps the tappable figure from shrinking to a strip
            // on short panes; the card is the button that opens the full view.
            .heightIn(min = 220.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = "Open weekly coverage",
            ) { onOpen() },
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "WEEKLY COVERAGE",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = ChakraPetch,
                    color = IronvellumColors.SystemGreen,
                    letterSpacing = IronvellumTracking.InlineLabel,
                )
                // The chevron is the tap affordance: without it the card read
                // as a static summary even though it opens the full screen.
                Text(
                    " ›",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = ChakraPetch,
                    color = IronvellumColors.SystemGreen,
                )
            }
            Text(
                when {
                    ui.plannedPresets.isEmpty() -> "NO CYCLE YET"
                    gaps == 0 -> "ALL COVERED"
                    else -> "$gaps SHORT OR MISSING"
                },
                style = MaterialTheme.typography.labelSmall,
                fontFamily = ChakraPetch,
                color = if (gaps > 0) IronvellumColors.SovereignGold else IronvellumColors.SystemGreen,
                letterSpacing = IronvellumTracking.InlineLabel,
            )
        }
        Spacer(Modifier.height(8.dp))
        if (ui.plannedPresets.isEmpty()) {
            Text(
                "Build a cycle to see which muscles it covers.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            BodyHeatMap(
                volume = volume,
                goal = goal,
                modifier = Modifier.fillMaxWidth(),
                figureHeight = 190.dp,
            )
        }
    }
}

/**
 * Per-card movement cap. Presets can hold many exercises and rendering every
 * one turned each card into a wall of identical rows; three movements is
 * enough to recognise the preset. The full manifest still lives in the preset
 * editor and on the Today quest card.
 */
private const val PRESET_CARD_MOVEMENTS = 3

