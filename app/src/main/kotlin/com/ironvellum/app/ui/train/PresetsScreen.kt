package com.ironvellum.app.ui.train

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Share
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ironvellum.app.R
import com.ironvellum.app.data.Repository
import com.ironvellum.app.data.Units
import com.ironvellum.app.data.Words
import com.ironvellum.app.domain.DayNames
import com.ironvellum.app.domain.Muscle
import com.ironvellum.app.domain.PlannedPreset
import com.ironvellum.app.domain.ProgramRules
import com.ironvellum.app.domain.RoutineCode
import com.ironvellum.app.domain.SessionClock
import com.ironvellum.app.domain.TrainFocus
import com.ironvellum.app.domain.TrainingFocus
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.domain.VolumeLevel
import com.ironvellum.app.domain.WorkoutPreset
import com.ironvellum.app.ui.asString
import com.ironvellum.app.ui.components.InkChip
import com.ironvellum.app.ui.components.InkDivider
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.components.InkRowPanel
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.components.IronvellumDialog
import com.ironvellum.app.ui.components.ListRow
import com.ironvellum.app.ui.components.ListRowHeight
import com.ironvellum.app.ui.components.SectionHeader
import com.ironvellum.app.ui.components.formatDate
import com.ironvellum.app.ui.program.MusclesNowCard
import com.ironvellum.app.ui.program.MuscleCoverageViewModel
import com.ironvellum.app.ui.program.MuscleFatigueViewModel
import com.ironvellum.app.ui.program.rememberDismissedHeavy
import com.ironvellum.app.ui.program.weekLine
import com.ironvellum.app.ui.program.MusclesView
import com.ironvellum.app.ui.dashboard.trialLength
import com.ironvellum.app.ui.ironvellumRepository
import com.ironvellum.app.ui.launchGuarded
import com.ironvellum.app.ui.program.toPlanned
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.IronvellumTracking
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
    private val appContext: android.content.Context,
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
        viewModelScope.launchGuarded("begin ${Words.rite.s}") { onStarted(repo.startSessionFromPreset(presetId)) }
    }

    fun beginQuick(onStarted: (Long) -> Unit) {
        viewModelScope.launch { onStarted(repo.startFreeformSession(Words.openTrial.c)) }
    }

    fun shareRoutine(onRefused: (String) -> Unit, onCode: (String) -> Unit) {
        viewModelScope.launchGuarded("share ${Words.cycle.s}") {
            val routine = repo.sharedRoutine()
            val refusal = RoutineCode.shareRefusal(routine)
            if (refusal != null) onRefused(refusal) else onCode(RoutineCode.encode(routine))
        }
    }

    /** The write is one transaction, so a failure leaves the routine as it was. */
    fun importRoutine(workouts: List<RoutineCode.SharedWorkout>, replace: Boolean, onDone: (String) -> Unit) {
        viewModelScope.launch {
            val summary = runCatching { importSummary(appContext, repo.importSharedRoutine(workouts, replace), replace) }
                .getOrElse { UiText.res(R.string.presets_import_failed).asString(appContext) }
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

/** "MON": the weekday's short name in capitals, blank for a number that is no weekday. */
internal fun dayLabel(day: Int): String = DayNames.short(day)?.uppercase().orEmpty()

/** "82.5 kg", or "BW" for no load (null or 0): the shared load formatter, so every caller spaces and words it alike. */
internal fun formatKg(kg: Double?): String = Units.label(kg)

@Composable
fun PresetsScreen(
    onOpenRite: (Long) -> Unit,
    onNew: () -> Unit,
    onGenerate: (mode: String, presetId: Long?) -> Unit,
    onStartSession: (Long) -> Unit,
    onQuickSession: (Long) -> Unit,
    onOpenExercises: () -> Unit,
    onOpenLog: () -> Unit,
    onOpenMuscles: (MusclesView) -> Unit,
    onOpenWorkout: (Long) -> Unit = {},
    viewModel: PresetsViewModel =
        viewModel(factory = viewModelFactory {
            val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
            initializer { PresetsViewModel(ironvellumRepository(), appContext) }
        }),
    fatigueModel: MuscleFatigueViewModel =
        viewModel(factory = viewModelFactory {
            initializer { MuscleFatigueViewModel(ironvellumRepository()) }
        }),
    coverageModel: MuscleCoverageViewModel =
        viewModel(factory = viewModelFactory {
            initializer { MuscleCoverageViewModel(ironvellumRepository()) }
        }),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val fatigue by fatigueModel.ui.collectAsStateWithLifecycle()
    val coverage by coverageModel.ui.collectAsStateWithLifecycle()
    val dismissedHeavy = rememberDismissedHeavy()
    val live by viewModel.live.collectAsStateWithLifecycle()
    var showNewChooser by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    // Survives only the composition: a stale "Added 3 workouts" after leaving
    // and returning to Train would read as a fresh import.
    var importResult by remember { mutableStateOf<String?>(null) }
    var shareRefusal by remember { mutableStateOf<String?>(null) }

    val zone = java.time.ZoneId.systemDefault()
    val coverageLine = remember(coverage.week, dismissedHeavy.value) { weekLine(coverage.week.tally(dismissedHeavy.value)) }
    val todayDate = LocalDate.now()
    val today = todayDate.dayOfWeek.value
    val todayStart = todayDate.atStartOfDay(zone).toInstant().toEpochMilli()
    val weekStart = todayDate.minusDays((today - 1).toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
    val sessions = ui.history.map { it.first }
    val focus = TrainFocus.resolve(ui.presets, sessions, live, today, todayStart, weekStart)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        // The title, the Begin chip and the tabs are TrainScreen's; this is the Plan tab under them.
        val context = androidx.compose.ui.platform.LocalContext.current
        val share = {
            shareRefusal = null
            viewModel.shareRoutine(onRefused = { shareRefusal = it }) { code -> shareRoutineCode(context, code) }
        }
        importResult?.let { line ->
            Text(line, style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted, modifier = Modifier.padding(top = GROUP_GAP))
        }
        // The lead card: no cycle at all, or a trial under way (a rite's too, as Today shows it), so leaving
        // the trial with Back never loses the way back into it.
        if (focus is TrainFocus.NoCycle || focus is TrainFocus.Live) {
            Spacer(Modifier.height(GROUP_GAP))
            TrainLeadCard(
                focus = focus,
                onContinue = onStartSession,
                onForge = { showNewChooser = true },
            )
        }
        MusclesNowCard(
            ui = fatigue,
            coverageLine = if (!coverage.week.hasAnything()) null else stringResource(R.string.muscles_now_week, coverageLine),
            zone = zone,
            onOpen = { onOpenMuscles(MusclesView.FATIGUE) },
            onOpenCoverage = { onOpenMuscles(MusclesView.COVERAGE) },
            modifier = Modifier.padding(top = SECTION_GAP),
        )
        if (ui.presets.isNotEmpty()) {
        Spacer(Modifier.height(SECTION_GAP))
        val sealedThisWeek = { preset: WorkoutPreset -> sessions.any { it.presetId == preset.id && (it.completedAtMs ?: 0L) >= weekStart } }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                UiText.themed(R.string.presets_cycle_header, R.string.presets_cycle_header__t).asString(),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = ChakraPetch,
                color = IronvellumColors.InkMuted,
                letterSpacing = IronvellumTracking.SectionHeader,
                // Takes the room the chips leave and wraps, so the chips are never pushed off the screen.
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            // Sit with the list they add to and export.
            InkChip(stringResource(R.string.presets_new_button), UiText.themed(R.string.presets_new_description, R.string.presets_new_description__t).asString(), Icons.Outlined.Add) { showNewChooser = true }
            InkChip(stringResource(R.string.common_share), UiText.themed(R.string.routineshare_share_title, R.string.routineshare_share_title__t).asString(), Icons.Outlined.Share, onClick = share)
        }
        shareRefusal?.let { line ->
            Text(line, style = MaterialTheme.typography.bodySmall, color = IronvellumColors.DangerRed, modifier = Modifier.padding(bottom = 8.dp))
        }
        // Each rite is one line, the whole of it one tap away on its page,
        // where it is begun; today's is begun from Today.
        InkRowPanel(Modifier.fillMaxWidth()) {
            ui.presets.forEach { preset ->
                val sealed = sealedThisWeek(preset)
                val isToday = preset.scheduledDay == today
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClickLabel = stringResource(R.string.presets_open_description, preset.name)) { onOpenRite(preset.id) }
                        .heightIn(min = ROW_HEIGHT)
                        .padding(end = 16.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Today is the thin emerald bar at the row's edge; the rest keep the gutter.
                    Box(
                        Modifier
                            .width(TODAY_BAR)
                            .height(28.dp)
                            .background(if (isToday) IronvellumColors.Emerald else Color.Transparent),
                    )
                    Text(
                        preset.scheduledDay?.let(::dayLabel) ?: "—",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = ChakraPetch,
                        letterSpacing = IronvellumTracking.InlineLabel,
                        color = IronvellumColors.InkMuted,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.widthIn(min = DAY_COLUMN),
                    )
                    Text(
                        preset.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = IronvellumColors.Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (sealed) {
                        Text(stringResource(R.string.presets_sealed_mark), style = MaterialTheme.typography.labelMedium, color = IronvellumColors.Emerald)
                    }
                    Text(
                        when {
                            sealed -> Words.sealed.c
                            isToday && focus is TrainFocus.Live -> stringResource(R.string.presets_row_under_way)
                            isToday -> stringResource(R.string.presets_row_today)
                            // "~54 min": the tail of the rite's plan line.
                            else -> SessionClock.planLine(preset.toPlanned().entries, ui.focus, ui.pace.secondsPerSet(preset.id))
                                .substringAfterLast(" · ").lowercase()
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = IronvellumColors.InkMuted,
                        maxLines = 1,
                    )
                    // The row opens the rite, like the rows below it, so it says so.
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = IronvellumColors.InkMuted,
                    )
                }
                if (preset != ui.presets.last()) InkDivider()
            }
        }
        }

        Spacer(Modifier.height(SECTION_GAP))
        // The page scrolls, so it always lists the latest few.
        val trials = ui.history.filter { it.first.completedAtMs != null }
            .sortedByDescending { it.first.completedAtMs }
            .take(RECENT_MAX)
        // The ways out: the catalogue and the record. Rows rather than buttons, so
        // nothing here competes with Begin. Coverage is on the Muscles card above.
        InkRowPanel(Modifier.fillMaxWidth()) {
            WayOutRow(icon = Icons.Outlined.FitnessCenter, label = stringResource(R.string.presets_exercises_label), onClick = onOpenExercises)
            // The recent trials card links the same chronicle from its header,
            // so the row only stands when that card does not.
            if (trials.isEmpty()) {
                InkDivider()
                WayOutRow(icon = Icons.Outlined.History, label = UiText.themed(R.string.explorer_full_history, R.string.explorer_full_history__t).asString(), onClick = onOpenLog)
            }
        }

        if (trials.isNotEmpty()) {
            Spacer(Modifier.height(SECTION_GAP))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    UiText.themed(R.string.presets_recent_header, R.string.presets_recent_header__t).asString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = ChakraPetch,
                    color = IronvellumColors.InkMuted,
                    letterSpacing = IronvellumTracking.SectionHeader,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                // Named for where it goes: "All" alone says nothing to a screen reader.
                InkChip(
                    stringResource(R.string.presets_recent_all_button),
                    stringResource(R.string.presets_recent_open_action),
                    description = UiText.themed(R.string.explorer_full_history, R.string.explorer_full_history__t).asString(),
                    onClick = onOpenLog,
                )
            }
            InkRowPanel(Modifier.fillMaxWidth()) {
                trials.forEach { (trial, sets) ->
                    if (trial != trials.first().first) InkDivider()
                    TrialRow(trial, sets) { onOpenWorkout(trial.id) }
                }
            }
        }
        Spacer(Modifier.height(END_GAP))
    }

    if (showNewChooser) {
        IronvellumDialog(
            onDismissRequest = { showNewChooser = false },
            title = { Text(stringResource(R.string.presets_new_button)) },
            text = {
                Column {
                    // Easiest first, each with one plain line, so someone new
                    // can tell the Forge from a blank page without trying both.
                    val options = buildList {
                        add(Triple(UiText.themed(R.string.presets_new_week_title, R.string.presets_new_week_title__t).asString(), stringResource(R.string.presets_new_week_subtitle)) { showNewChooser = false; onGenerate("week", null) })
                        add(Triple(UiText.themed(R.string.presets_new_session_title, R.string.presets_new_session_title__t).asString(), UiText.themed(R.string.presets_new_session_subtitle, R.string.presets_new_session_subtitle__t).asString()) { showNewChooser = false; onGenerate("session", null) })
                        add(Triple(UiText.themed(R.string.presets_new_template_title, R.string.presets_new_template_title__t).asString(), UiText.themed(R.string.presets_new_template_subtitle, R.string.presets_new_template_subtitle__t).asString()) { showNewChooser = false; onGenerate("template", null) })
                        // Improving needs a target: nothing to improve on an
                        // empty board.
                        if (ui.presets.isNotEmpty()) {
                            add(Triple(UiText.themed(R.string.presets_new_improve_title, R.string.presets_new_improve_title__t).asString(), stringResource(R.string.presets_new_improve_subtitle)) { showNewChooser = false; onGenerate("improve", null) })
                        }
                        add(Triple(UiText.themed(R.string.presets_new_blank_title, R.string.presets_new_blank_title__t).asString(), stringResource(R.string.presets_new_blank_subtitle)) { showNewChooser = false; onNew() })
                        add(Triple(stringResource(R.string.routineshare_import_title), UiText.themed(R.string.presets_new_import_subtitle, R.string.presets_new_import_subtitle__t).asString()) { showNewChooser = false; showImport = true })
                    }
                    options.forEachIndexed { index, (label, detail, action) ->
                        if (index > 0) InkDivider()
                        ListRow(label, subline = detail, contentPadding = PaddingValues(vertical = 8.dp), onClick = action)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                IronvellumButton(label = stringResource(R.string.common_cancel), onClick = { showNewChooser = false }, quiet = true)
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
        done.size.takeIf { it > 0 }?.let { UiText.plural(R.plurals.common_sets_count, it).asString() },
        volumeKg.takeIf { it > 0.0 }?.let {
            if (Units.current == com.ironvellum.app.domain.LoadUnit.LB) Units.volume(it)
            else if (it < 1000) "${it.toInt()} kg" else "${"%.1f".format(java.util.Locale.US, it / 1000)} t"
        },
    ).joinToString(" · ")
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = stringResource(R.string.presets_open_description, trial.label), onClick = onOpen)
            .heightIn(min = TRIAL_ROW_HEIGHT)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.widthIn(min = DAY_TILE), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                formatDate(at, stringResource(R.string.presets_date_weekday)).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = ChakraPetch,
                color = IronvellumColors.InkMuted,
                maxLines = 1,
                softWrap = false,
            )
            Text(formatDate(at, stringResource(R.string.presets_date_day)), style = MaterialTheme.typography.titleMedium, fontFamily = ChakraPetch, color = IronvellumColors.Ink)
        }
        Column(Modifier.weight(1f)) {
            Text(trial.label, style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (detail.isNotEmpty()) {
                Text(detail, style = MaterialTheme.typography.labelSmall, fontFamily = ChakraPetch, color = IronvellumColors.InkMuted, maxLines = 2)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(stringResource(R.string.presets_trial_xp, trial.xpAwarded), style = MaterialTheme.typography.labelMedium, fontFamily = ChakraPetch, color = IronvellumColors.SovereignGold)
            Text(stringResource(R.string.presets_trial_strength, trial.strengthScore), style = MaterialTheme.typography.labelSmall, fontFamily = ChakraPetch, color = IronvellumColors.InkMuted)
        }
    }
}

/**
 * Train's lead, for the two states the cycle cannot show: no cycle at all, and
 * an open trial under way, which belongs to no rite.
 */
@Composable
private fun TrainLeadCard(
    focus: TrainFocus,
    onContinue: (Long) -> Unit,
    onForge: () -> Unit,
) {
    InkPanel(Modifier.fillMaxWidth()) {
        when (focus) {
            is TrainFocus.Live -> {
                Text(
                    UiText.themed(R.string.presets_live_header, R.string.presets_live_header__t).asString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = ChakraPetch,
                    color = IronvellumColors.InkMuted,
                    letterSpacing = IronvellumTracking.SectionHeader,
                    maxLines = 1,
                )
                LeadButton(stringResource(R.string.ritedetail_continue_button, focus.preset?.name ?: focus.session.label)) { onContinue(focus.session.id) }
            }
            else -> {
                Text(UiText.themed(R.string.presets_no_cycle_title, R.string.presets_no_cycle_title__t).asString(), style = MaterialTheme.typography.titleMedium, color = IronvellumColors.Ink)
                Text(
                    UiText.themed(R.string.presets_no_cycle_body, R.string.presets_no_cycle_body__t).asString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                    modifier = Modifier.padding(top = 4.dp),
                )
                LeadButton(UiText.themed(R.string.presets_no_cycle_button, R.string.presets_no_cycle_button__t).asString()) { onForge() }
            }
        }
    }
}

@Composable
private fun LeadButton(label: String, onClick: () -> Unit) {
    Spacer(Modifier.height(12.dp))
    IronvellumButton(label = label, onClick = onClick, modifier = Modifier.fillMaxWidth())
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

/** The thin emerald bar marking today's rite. */
private val TODAY_BAR = 2.dp

/** A recent trial's row, and the calendar tile that leads it. */
private val TRIAL_ROW_HEIGHT = ListRowHeight
private val DAY_TILE = 38.dp

/** The most completed trials Recent lists. */
private const val RECENT_MAX = 5
