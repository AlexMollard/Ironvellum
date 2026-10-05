package com.ironvellum.app.ui.train

import androidx.compose.material.icons.outlined.FitnessCenter
import com.ironvellum.app.ui.components.formatDate
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccessibilityNew
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
import androidx.compose.material3.Icon
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
import com.ironvellum.app.ui.theme.inkHairline
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
    val focus = TrainFocus.resolve(ui.presets, sessions, live, today, todayStart)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Text(
            "TRAIN",
            style = MaterialTheme.typography.labelLarge,
            color = IronvellumColors.SystemGreen,
            letterSpacing = 6.sp,
            // The one heading on an empty board, where the cycle's is hidden.
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(16.dp))
        // The screen leads with the one thing to do now: today's rite, the
        // trial under way, or what a rest day allows. Open Trial used to be
        // the lead button, which put the side door ahead of the plan.
        TrainLeadCard(
            focus = focus,
            ui = ui,
            today = today,
            onOpenRite = onOpenRite,
            onBegin = { id -> viewModel.begin(id, onStartSession) },
            onContinue = onStartSession,
            onForge = { onGenerate("week", null) },
            last = sessions.filter { it.completedAtMs != null }.maxByOrNull { it.completedAtMs ?: 0L },
        )
        Spacer(Modifier.height(GROUP_GAP))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GROUP_GAP)) {
            // No second trial while one is under way.
            if (focus !is TrainFocus.Live) {
                IronvellumButton(
                    label = "Open Trial",
                    onClick = { viewModel.beginQuick(onQuickSession) },
                    quiet = true,
                    modifier = Modifier.weight(1f),
                )
            }
            IronvellumButton(label = "New Rite", onClick = { showNewChooser = true }, quiet = true, modifier = Modifier.weight(1f))
        }

        importResult?.let { line ->
            Text(line, style = MaterialTheme.typography.bodySmall, color = IronvellumColors.SystemGreen, modifier = Modifier.padding(top = GROUP_GAP))
        }
        // With no rites the lead card already says so and offers the Forge;
        // an empty list under it would say it twice.
        if (ui.presets.isNotEmpty()) {
        Spacer(Modifier.height(SECTION_GAP))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "YOUR CYCLE",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = ChakraPetch,
                color = IronvellumColors.InkMuted,
                letterSpacing = IronvellumTracking.SectionHeader,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            // Sits with the list it exports.
            run {
                val context = androidx.compose.ui.platform.LocalContext.current
                Text(
                    "SHARE ›",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = ChakraPetch,
                    color = IronvellumColors.SystemGreen,
                    letterSpacing = IronvellumTracking.InlineLabel,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.extraSmall)
                        .clickable(onClickLabel = "Share cycle") {
                            shareRefusal = null
                            viewModel.shareRoutine(onRefused = { shareRefusal = it }) { code -> shareRoutineCode(context, code) }
                        }
                        .heightIn(min = 44.dp)
                        .padding(start = 8.dp)
                        .wrapContentHeight(Alignment.CenterVertically),
                )
            }
        }
        shareRefusal?.let { line ->
            Text(line, style = MaterialTheme.typography.bodySmall, color = IronvellumColors.DangerRed, modifier = Modifier.padding(bottom = 8.dp))
        }
        // One line a rite: the week at a glance. The whole rite - every
        // movement, its note, Begin and Edit - is one tap away on its page.
        InkPanel(Modifier.fillMaxWidth()) {
            ui.presets.forEach { preset ->
                val sealedThisWeek = sessions.any { it.presetId == preset.id && (it.completedAtMs ?: 0L) >= weekStart }
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
                            sealedThisWeek -> IronvellumColors.SystemGreen
                            else -> IronvellumColors.InkMuted
                        },
                        modifier = Modifier.width(36.dp),
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
                            sealedThisWeek -> "SEALED"
                            isToday -> "TODAY"
                            // "~54 MIN": the tail of the rite's plan line.
                            else -> SessionClock.planLine(preset.toPlanned().entries, ui.focus, ui.pace.secondsPerSet(preset.id))
                                .substringAfterLast(" · ")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = ChakraPetch,
                        letterSpacing = IronvellumTracking.InlineLabel,
                        color = when {
                            sealedThisWeek -> IronvellumColors.SystemGreen
                            isToday -> IronvellumColors.SovereignGold
                            else -> IronvellumColors.InkMuted
                        },
                        maxLines = 1,
                    )
                }
                if (preset != ui.presets.last()) RowRule(seed = preset.id.toInt())
            }
        }
        }

        Spacer(Modifier.height(SECTION_GAP))
        // The ways out: what the cycle covers, the catalogue, the record. Rows
        // rather than buttons, so nothing here competes with Begin. The body
        // map was a third of the screen on every visit; its row says the one
        // fact it answers and opens the full map.
        val volume = remember(ui.plannedPresets) { ProgramRules.weeklyVolume(ui.plannedPresets) }
        val gaps = coverageGaps(volume, CoverageGoal(ui.tier, ui.focus, ui.priorities)).size
        InkPanel(Modifier.fillMaxWidth()) {
            WayOutRow(
                icon = Icons.Outlined.AccessibilityNew,
                label = "WEEKLY COVERAGE",
                detail = when {
                    ui.plannedPresets.isEmpty() -> "No cycle yet"
                    gaps == 0 -> "Every muscle covered"
                    else -> "$gaps short or missing"
                },
                detailColor = if (gaps > 0) IronvellumColors.SovereignGold else IronvellumColors.InkMuted,
                onClick = onOpenCoverage,
            )
            RowRule(seed = 41)
            WayOutRow(icon = Icons.Outlined.FitnessCenter, label = "EXERCISES", onClick = onOpenExercises)
            RowRule(seed = 42)
            WayOutRow(icon = Icons.Outlined.History, label = "FULL CHRONICLE", onClick = onOpenLog)
        }
        Spacer(Modifier.height(END_GAP))
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
        accent = when (focus) {
            is TrainFocus.Sealed -> IronvellumColors.SovereignGold
            is TrainFocus.Live, is TrainFocus.Begin -> IronvellumColors.SystemGreen
            else -> IronvellumColors.Rune
        },
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
                "Your cycle is unwritten. The Forge builds a whole week for you.",
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
            TrainFocus.NoCycle -> LeadButton("Forge a Cycle") { onForge() }
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

@Composable
private fun RowRule(seed: Int) {
    Box(Modifier.fillMaxWidth().height(2.dp).inkHairline(IronvellumColors.Rune, seed = seed))
}

/** One way out of Train: icon, label, an optional fact, and a chevron. */
@Composable
private fun WayOutRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    detail: String? = null,
    detailColor: androidx.compose.ui.graphics.Color = IronvellumColors.InkMuted,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraSmall)
            .clickable(onClickLabel = label.lowercase().replaceFirstChar { it.uppercase() }, onClick = onClick)
            .heightIn(min = ROW_HEIGHT),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = IronvellumColors.SystemGreen, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = ChakraPetch,
                color = IronvellumColors.Ink,
                letterSpacing = IronvellumTracking.InlineLabel,
            )
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = detailColor) }
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = IronvellumColors.InkMuted)
    }
}

/** Train's rhythm: panels sit a section apart; controls inside a group, a group gap. */
private val SECTION_GAP = 16.dp
private val GROUP_GAP = 12.dp

/** The page's end clears the raised Train plate, which overhangs the bar by 14dp. */
private val END_GAP = 40.dp

/** A list row: the 48dp touch target, the same in both panels. */
private val ROW_HEIGHT = 48.dp
