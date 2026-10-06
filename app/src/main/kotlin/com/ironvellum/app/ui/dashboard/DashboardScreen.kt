package com.ironvellum.app.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Straighten
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ironvellum.app.data.IdleSnapshot
import com.ironvellum.app.data.Repository
import com.ironvellum.app.domain.Exercise
import com.ironvellum.app.domain.Idle
import com.ironvellum.app.domain.LiftRecord
import com.ironvellum.app.domain.LiftRecords
import com.ironvellum.app.domain.PlayerProfile
import com.ironvellum.app.domain.Rank
import com.ironvellum.app.domain.RankBreakdown
import com.ironvellum.app.domain.SessionClock
import com.ironvellum.app.domain.SessionSet
import com.ironvellum.app.domain.Sex
import com.ironvellum.app.domain.TitleDef
import com.ironvellum.app.domain.Titles
import com.ironvellum.app.domain.TrainFocus
import com.ironvellum.app.domain.TrainingFocus
import com.ironvellum.app.domain.WorkoutPreset
import com.ironvellum.app.domain.WorkoutSession
import com.ironvellum.app.domain.Xp
import com.ironvellum.app.domain.fmt
import com.ironvellum.app.ui.components.AchievementOverlay
import com.ironvellum.app.ui.components.InkDivider
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.components.InkRail
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.components.LifterSigil
import com.ironvellum.app.ui.components.ListRow
import com.ironvellum.app.ui.components.ListRowHeight
import com.ironvellum.app.ui.components.RankSheet
import com.ironvellum.app.ui.components.Term
import com.ironvellum.app.ui.components.TermDialog
import com.ironvellum.app.ui.components.deedAchievement
import com.ironvellum.app.ui.components.formatDate
import com.ironvellum.app.ui.components.plural
import com.ironvellum.app.ui.ironvellumRepository
import com.ironvellum.app.ui.launchGuarded
import com.ironvellum.app.ui.program.toPlanned
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.IronvellumTracking
import com.ironvellum.app.ui.theme.inkDot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/** Which of height and weight the scores still need. */
enum class BodyGap { HEIGHT, WEIGHT, BOTH }

/** The Veil at a glance on Today: its live snapshot and the inscriptions waiting to be spent. */
data class VeilGlance(val snapshot: IdleSnapshot, val inscriptions: Int)

class DashboardUi(
    val profile: PlayerProfile? = null,
    val presets: List<WorkoutPreset> = emptyList(),
    val streak: Int = 0,
    /** The focus the plan line is timed at. */
    val focus: TrainingFocus = TrainingFocus.MUSCLE,
    /** The lifter's own seconds per set; times the plan line. */
    val pace: SessionClock.Pace = SessionClock.Pace(),
    /** Weekdays (1 = Monday) whose scheduled rite was sealed this week, to the trial that sealed it. */
    val weekDone: Map<Int, WorkoutSession> = emptyMap(),
    /** The sets of each trial in [weekDone], by trial id, so a sealed card can read what was done. */
    val sealedSets: Map<Long, List<SessionSet>> = emptyMap(),
    /** The catalogue by id, for each movement's metric. */
    val exercises: Map<Long, Exercise> = emptyMap(),
    /** Lifts whose peak was set lately AND beat an earlier trial: a first trial is not a peak. */
    val newPeaks: List<LiftRecord> = emptyList(),
)

/**
 * Weekdays of the week containing [today] whose scheduled rite is sealed this
 * week, by [TrainFocus.sealing], mapped to the trial that sealed it. Only
 * completed trials count, from the whole history, so a long cycle cannot push
 * Monday's tick out and an unsealed trial cannot light one.
 */
internal fun weekDoneDays(
    sessions: List<WorkoutSession>,
    presets: List<WorkoutPreset>,
    today: LocalDate,
    zone: ZoneId,
): Map<Int, WorkoutSession> {
    val weekStart = today.with(DayOfWeek.MONDAY).atStartOfDay(zone).toInstant().toEpochMilli()
    return presets.mapNotNull { preset ->
        val day = preset.scheduledDay ?: return@mapNotNull null
        TrainFocus.sealing(preset, sessions, weekStart)?.let { day to it }
    }.toMap()
}

/**
 * Peaks to name on Today: set within the Ledger's fresh window, and higher than
 * an earlier trial of the same lift - the first trial of anything is its best
 * by default and is not news.
 */
internal fun newPeaks(
    history: List<Pair<WorkoutSession, List<SessionSet>>>,
    exercises: List<Exercise>,
    nowMs: Long,
    limit: Int = 2,
): List<LiftRecord> =
    LiftRecords.board(
        sessions = history.map { it.first }.filter { it.completedAtMs != null },
        sessionSets = history.associate { it.first.id to it.second },
        exercises = exercises.associateBy { it.id },
        nowMs = nowMs,
    )
        .filter { LiftRecords.isFresh(it, nowMs) && it.series.indexOfFirst { v -> v == it.bestE1rmKg } > 0 }
        .sortedByDescending { it.bestAtMs }
        .take(limit)

class DashboardViewModel(
    private val repo: Repository,
    /** What the lifter last told the generator; times the plan line. */
    private val savedFocus: TrainingFocus? = null,
) : ViewModel() {

    private val selectedDay = MutableStateFlow(LocalDate.now().dayOfWeek.value)

    /** The unfinished session: the day card continues it instead of starting another. */
    val live: StateFlow<WorkoutSession?> = repo.observeLiveSession()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The live trial's sets, for "n of m sets logged" and its rows. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val liveSets: StateFlow<List<SessionSet>> = live
        .flatMapLatest { trial -> if (trial == null) flowOf(emptyList()) else repo.observeSessionSets(trial.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val ui: StateFlow<DashboardUi> = combine(
        repo.observeProfile(),
        repo.observePresets(),
        repo.observeHistory(),
        repo.observeExercises(),
    ) { profile, presets, history, exercises ->
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        val doneDates = history
            .map {
                Instant.ofEpochMilli(it.first.completedAtMs ?: it.first.startedAtMs)
                    .atZone(zone).toLocalDate()
            }
            .toSet()
        val weekDone = weekDoneDays(history.map { it.first }, presets, today, zone)
        val sealedIds = weekDone.values.map { it.id }.toSet()
        // One streak rule for the whole app: the Today screen used to build its
        // own day records while the deeds, the idle rate and the leaderboard
        // used Titles.trainingStreakDays, so the same lifter could read two
        // different streaks.
        DashboardUi(
            profile = profile,
            presets = presets,
            streak = Titles.trainingStreakDays(doneDates, today),
            focus = SessionClock.focusFor(savedFocus, profile?.trainingMode),
            pace = SessionClock.pace(history),
            weekDone = weekDone,
            sealedSets = history.filter { it.first.id in sealedIds }.associate { it.first.id to it.second },
            exercises = exercises.associateBy { it.id },
            newPeaks = newPeaks(history, exercises, System.currentTimeMillis()),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUi())

    val selected: StateFlow<Int> = selectedDay

    /** Titles awarded by startup reconciliation, still owed their celebration. */
    val pendingCelebrations: StateFlow<List<TitleDef>> = repo.pendingCelebrations

    /**
     * The reader's own bar. A deed's wording differs by sex, and the celebration
     * must not tell a woman she cleared the men's standard.
     */
    val sex: StateFlow<Sex> = repo.observeBodyProfile()
        .map { it.second }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Sex.MALE)

    private val breakdownFlow = repo.observeRankBreakdown()
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    /** Strength Rank band, "Unranked" without one; blank until first read. */
    val strengthRank: StateFlow<String> = breakdownFlow
        .map { it?.band ?: Rank.UNRANKED }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    /** The rank written out for [RankSheet]; null while unranked. */
    val rankBreakdown: StateFlow<RankBreakdown?> = breakdownFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The Veil at a glance: its snapshot (essence, rate, echoes, relic) and the inscriptions waiting. */
    val veil: StateFlow<VeilGlance?> = combine(repo.observeIdleSnapshot(), repo.observeRolls()) { snapshot, rolls ->
        VeilGlance(snapshot, rolls)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * What the body-scaled numbers still wait for. Skipping the Binding leaves
     * height and weight unset, and every score reads a dash until both exist.
     */
    val bodyGap: StateFlow<BodyGap?> = combine(repo.observeBodyProfile(), repo.observeStats()) { body, stats ->
        val noHeight = (body.first ?: 0.0) <= 0.0
        val noWeight = stats.isEmpty()
        when {
            noHeight && noWeight -> BodyGap.BOTH
            noHeight -> BodyGap.HEIGHT
            noWeight -> BodyGap.WEIGHT
            else -> null
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun celebrationsSeen() = repo.clearPendingCelebrations()

    fun wearTitle(titleId: String) {
        viewModelScope.launchGuarded("wear title") { repo.equipTitle(titleId) }
    }

    fun selectDay(day: Int) {
        selectedDay.value = day
    }

    fun beginOpen(onStarted: (Long) -> Unit) {
        viewModelScope.launchGuarded("begin open trial") { onStarted(repo.startFreeformSession("Open Trial")) }
    }

    fun beginPreset(presetId: Long, onStarted: (Long) -> Unit) {
        viewModelScope.launchGuarded("begin rite") { onStarted(repo.startSessionFromPreset(presetId)) }
    }
}

/**
 * "as of 14:05" for a sync today, "as of Sep 28" for an older one: Health
 * Connect trails the phone's own count, so the figure carries its age.
 */
internal fun stepsAsOfCaption(syncedAtMs: Long?, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): String? {
    if (syncedAtMs == null) return null
    val at = Instant.ofEpochMilli(syncedAtMs).atZone(zone)
    val pattern = if (at.toLocalDate() == today) "HH:mm" else "MMM d"
    return "as of " + at.format(java.time.format.DateTimeFormatter.ofPattern(pattern, Locale.US))
}

/** "48m", "1h 12m"; null for a trial with no sensible length (imports carry none). */
internal fun trialLength(trial: WorkoutSession): String? {
    val minutes = ((trial.completedAtMs ?: return null) - trial.startedAtMs) / 60_000
    return when {
        minutes < 1 || minutes > 24 * 60 -> null
        minutes < 60 -> "${minutes}m"
        else -> "${minutes / 60}h ${minutes % 60}m"
    }
}

/** Points of a peak's climb drawn beside it: enough to read the trend, few enough to stay a glance. */
private const val PEAK_SPARK_POINTS = 8

/** A movement's mini bar stops growing here; a long block just reads as a long bar. */
private const val MINI_BAR_SEGMENTS = 8

/** The week rail's letters, Monday first. */
private val WEEK_LETTERS = listOf("M", "T", "W", "T", "F", "S", "S")

/** "Sat" within the week, "Oct 3" beyond it. */
private fun shortWhen(ms: Long): String =
    if (System.currentTimeMillis() - ms < 6L * 24 * 60 * 60 * 1000) formatDate(ms, "EEE") else formatDate(ms, "MMM d")

/** "Wed" for 3. */
private fun dayName(day: Int): String = DayOfWeek.of(day).getDisplayName(TextStyle.SHORT, Locale.getDefault())

/** "Wednesday" for 3. */
private fun dayLong(day: Int): String = DayOfWeek.of(day).getDisplayName(TextStyle.FULL, Locale.getDefault())

@Composable
fun DashboardScreen(
    onStartSession: (Long) -> Unit,
    onOpenPresets: () -> Unit,
    onOpenRite: (Long) -> Unit,
    onOpenForge: () -> Unit,
    onOpenCodex: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLedger: () -> Unit,
    onOpenGarrison: () -> Unit,
    onOpenWorkout: (Long) -> Unit,
    /** Set height: straight to Settings → Profile, where height lives. */
    onSetHeight: () -> Unit = onOpenSettings,
    /** A fresh peak's row opens that lift. */
    onOpenLift: (String) -> Unit = { onOpenLedger() },
    viewModel: DashboardViewModel =
        viewModel(factory = viewModelFactory {
            // The saved answers sit behind a Context only a composable can read;
            // they are read once, when the view model is made.
            val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
            initializer {
                DashboardViewModel(
                    ironvellumRepository(),
                    com.ironvellum.app.data.ProgramAnswersStore.get(appContext)?.focus,
                )
            }
        }),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val selectedDay by viewModel.selected.collectAsStateWithLifecycle()
    // A cached process outlives midnight: the lifter opened the app Monday
    // night, Android parked it, and Thursday's open resumed Monday's
    // selection. On each return to the foreground, follow the calendar — but
    // only when the day actually changed, so a quick app-switch preserves the
    // day she was reading.
    val launchDay = remember { LocalDate.now().dayOfWeek.value }
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_START &&
                LocalDate.now().dayOfWeek.value != launchDay
            ) {
                viewModel.selectDay(LocalDate.now().dayOfWeek.value)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val live by viewModel.live.collectAsStateWithLifecycle()
    val liveSets by viewModel.liveSets.collectAsStateWithLifecycle()
    val bodyGap by viewModel.bodyGap.collectAsStateWithLifecycle()
    val strengthRank by viewModel.strengthRank.collectAsStateWithLifecycle()
    val rankBreakdown by viewModel.rankBreakdown.collectAsStateWithLifecycle()
    val veil by viewModel.veil.collectAsStateWithLifecycle()
    var rankOpen by remember { mutableStateOf(false) }
    var oathOpen by remember { mutableStateOf(false) }
    val profile = ui.profile
    val progress = Xp.progress(profile?.totalXp ?: 0L)
    val today = LocalDate.now()
    val selectedPreset = ui.presets.firstOrNull { it.scheduledDay == selectedDay }
    val isTodaySelected = selectedDay == today.dayOfWeek.value
    // The tick, the card and the sealed state all answer one question: was THIS
    // day's scheduled rite sealed this week. A rite taken early is not offered
    // again on its day, and a sealed past day reads as sealed.
    val sealedTrial = ui.weekDone[selectedDay]
    // The next scheduled rite after the selected day, wrapping the week.
    val nextRite = (1..7).map { (selectedDay - 1 + it) % 7 + 1 }
        .firstNotNullOfOrNull { day -> ui.presets.firstOrNull { it.scheduledDay == day } }
    val liveTrial = live
    val kind = dayKind(ui.presets.isNotEmpty(), selectedPreset, isTodaySelected, liveTrial, sealedTrial)
    val liveWork = liveSets.filter { !it.warmup }
    val liveDone = liveWork.count { it.done }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(8.dp))

        // Header: the name and what is worn are also the way to the Codex (there
        // was no other route from here to its board), then the rank link and the gear.
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier
                    .weight(1f)
                    .clickable(onClickLabel = "Open the Codex") { onOpenCodex() },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        profile?.name ?: "Ironbound",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = IronvellumColors.Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    // Strength Rank only: ascension names the level on the rail
                    // below, so it is never a stat beside this one.
                    Row(
                        Modifier
                            .heightIn(min = 44.dp)
                            .clickable(role = Role.Button, onClickLabel = "Show Strength Rank") { rankOpen = true }
                            .padding(start = 10.dp, end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            strengthRank.ifEmpty { Rank.UNRANKED },
                            style = MaterialTheme.typography.labelLarge,
                            color = IronvellumColors.SystemGreen,
                            maxLines = 1,
                        )
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = IronvellumColors.SystemGreen,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                profile?.currentTitleId?.let { Titles.byId(it)?.name }?.let { worn ->
                    Text(
                        worn,
                        style = MaterialTheme.typography.bodySmall,
                        color = IronvellumColors.InkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            // The settings gear: the glyph stays 22dp; the TARGET is 48dp.
            Box(
                Modifier
                    .size(48.dp)
                    .clickable(role = Role.Button, onClickLabel = "Open settings") { onOpenSettings() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Settings,
                    contentDescription = "Settings",
                    tint = IronvellumColors.InkMuted,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        if (rankOpen) RankSheet(rankBreakdown) { rankOpen = false }

        // The level rides a thin rail it is climbing.
        Row(Modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically) {
            LifterSigil(level = progress.level, frameId = null, compact = true)
            InkRail(
                fraction = if (progress.needed <= 0) 0f else (progress.intoLevel.toFloat() / progress.needed).coerceIn(0f, 1f),
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                height = 4.dp,
            )
            // One text node: "58 / 400 XP".
            Text(
                "${progress.intoLevel} / ${progress.needed} XP",
                style = MaterialTheme.typography.labelSmall,
                color = IronvellumColors.InkMuted,
                maxLines = 1,
            )
        }

        // The oath, the one counter Today keeps: steps live in the Ledger, deeds in the Codex.
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clickable(role = Role.Button, onClickLabel = "Explain ${Term.OATH.title}") { oathOpen = true },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = IronvellumColors.Ink, fontWeight = FontWeight.Medium)) { append("Oath") }
                    append(
                        if (ui.streak > 0) " · ${ui.streak} ${plural(ui.streak, "day", "days")} kept"
                        else " · none yet, seal a trial to begin",
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
            )
        }
        if (oathOpen) TermDialog(Term.OATH) { oathOpen = false }

        WeekRail(
            selectedDay = selectedDay,
            today = today.dayOfWeek.value,
            scheduled = ui.presets.mapNotNull { it.scheduledDay }.toSet(),
            done = ui.weekDone.keys,
            onSelect = viewModel::selectDay,
        )

        // The day card: the only boxed area, sized to what it holds.
        val cardClick: (() -> Unit)? = when (kind) {
            DayKind.SEALED -> sealedTrial?.let { trial -> { onOpenWorkout(trial.id) } }
            DayKind.LIVE -> selectedPreset?.takeIf { it.id == liveTrial?.presetId }?.let { rite -> { onOpenRite(rite.id) } }
            DayKind.BEGIN, DayKind.PLANNED -> selectedPreset?.let { rite -> { onOpenRite(rite.id) } }
            DayKind.RESPITE -> nextRite?.let { rite -> { onOpenRite(rite.id) } }
            DayKind.NO_CYCLE -> null
        }
        InkPanel(Modifier.fillMaxWidth().padding(top = 8.dp), onClick = cardClick) {
            when (kind) {
                DayKind.LIVE -> {
                    val trial = liveTrial!!
                    CardLabel(if (selectedPreset != null && selectedPreset.id == trial.presetId) "Today's trial" else "Under way")
                    CardTitle(trial.label, chevron = cardClick != null)
                    PlanText("$liveDone of ${liveWork.size} sets logged")
                    SegmentBar(liveDone, liveWork.size)
                    DayRows(trialRows(liveSets, ui.exercises, sealed = false))
                    Spacer(Modifier.height(12.dp))
                    IronvellumButton(
                        label = "Continue ${trial.label}",
                        onClick = { onStartSession(trial.id) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                DayKind.SEALED -> {
                    val trial = sealedTrial!!
                    val rite = selectedPreset!!
                    val sets = ui.sealedSets[trial.id].orEmpty()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = IronvellumColors.SovereignGold,
                            modifier = Modifier.padding(end = 5.dp).size(13.dp),
                        )
                        CardLabel("Sealed", IronvellumColors.SovereignGold)
                        Spacer(Modifier.weight(1f))
                        Text(
                            "+${trial.xpAwarded} XP",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = IronvellumColors.SovereignGold,
                            maxLines = 1,
                        )
                    }
                    CardTitle(rite.name, chevron = true)
                    val done = sets.count { it.done && !it.warmup }
                    PlanText(listOfNotNull("$done ${plural(done, "set", "sets")}", trialLength(trial)).joinToString(" · "))
                    DayRows(trialRows(sets, ui.exercises, sealed = true))
                    nextRite?.takeIf { it.id != rite.id }?.let { next ->
                        NextLink(next, chevron = false) { viewModel.selectDay(next.scheduledDay!!) }
                    }
                }
                DayKind.BEGIN, DayKind.PLANNED -> {
                    val rite = selectedPreset!!
                    CardLabel(if (isTodaySelected) "Today's trial" else dayLong(selectedDay))
                    CardTitle(rite.name, chevron = true)
                    PlanText(sentencePlan(SessionClock.planLine(rite.toPlanned().entries, ui.focus, ui.pace.secondsPerSet(rite.id))))
                    DayRows(plannedRows(rite.entries))
                    // Today is where the day's rite is begun: Train only plans it.
                    if (kind == DayKind.BEGIN) {
                        Spacer(Modifier.height(12.dp))
                        IronvellumButton(
                            label = "Begin ${rite.name}",
                            onClick = { viewModel.beginPreset(rite.id, onStartSession) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                DayKind.RESPITE -> {
                    CardLabel(if (isTodaySelected) "Today" else dayLong(selectedDay))
                    CardTitle("Respite", chevron = false)
                    Text(
                        "A day the cycle leaves free.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = IronvellumColors.InkMuted,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    nextRite?.let { next -> NextLink(next, chevron = true) { viewModel.selectDay(next.scheduledDay!!) } }
                }
                DayKind.NO_CYCLE -> {
                    CardTitle("No cycle yet", chevron = false)
                    Text(
                        "Your cycle is unwritten. Forge one and its rites land here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = IronvellumColors.InkMuted,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    IronvellumButton(label = "Forge a cycle", onClick = onOpenForge, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        // Plain rows under the card, divided by hairlines: no panels.
        val peak = ui.newPeaks.firstOrNull()
        val rows = buildList<@Composable () -> Unit> {
            if (kind == DayKind.NO_CYCLE) {
                add {
                    // Not everyone wants a plan first: a trial can be logged
                    // exercise by exercise with no cycle at all.
                    ListRow(
                        label = "Begin an open trial",
                        subline = "Log exercises as you go, no cycle needed",
                        onClick = { viewModel.beginOpen(onStartSession) },
                    )
                }
            }
            // A trial under way off the card (another day selected) is one tap from here.
            if (liveTrial != null && kind != DayKind.LIVE) {
                add {
                    ListRow(
                        label = "Continue ${liveTrial.label}",
                        subline = "$liveDone of ${liveWork.size} sets logged",
                        onClick = { onStartSession(liveTrial.id) },
                    )
                }
            }
            // Height lives in Settings and weight is a Ledger reading, so the link
            // goes to whichever one is still missing, height first.
            bodyGap?.let { gap ->
                add { BodyGapRow(gap, onFix = if (gap == BodyGap.WEIGHT) onOpenLedger else onSetHeight) }
            }
            if (peak != null) add { PeakRow(peak) { onOpenLift(peak.name) } }
        }
        if (rows.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                rows.forEachIndexed { index, row ->
                    if (index > 0) InkDivider()
                    row()
                }
            }
        }

        VeilSection(veil, hero = kind == DayKind.RESPITE, onOpen = onOpenGarrison)
        // Clear of the raised Train plate, which stands 10dp above the bar.
        Spacer(Modifier.height(28.dp))
    }

    // Titles reconciled at startup (health data, imports) have no session to
    // celebrate in, so the moment is paid out here on the first screen.
    val owed by viewModel.pendingCelebrations.collectAsStateWithLifecycle()
    val sex by viewModel.sex.collectAsStateWithLifecycle()
    AchievementOverlay(
        items = owed.map { deedAchievement(it, sex) },
        onDone = { viewModel.celebrationsSeen() },
        wornTitleId = profile?.currentTitleId,
        onWear = viewModel::wearTitle,
    )
}

/**
 * The week at a glance: one letter per day, the scheduled ones in Ink, today bold, a small check
 * under a day whose rite is sealed and an emerald underline under the day being read.
 */
@Composable
private fun WeekRail(
    selectedDay: Int,
    today: Int,
    scheduled: Set<Int>,
    done: Set<Int>,
    onSelect: (Int) -> Unit,
) {
    Row(Modifier.fillMaxWidth()) {
        WEEK_LETTERS.forEachIndexed { index, letter ->
            val day = index + 1
            val isDone = day in done
            val isSelected = day == selectedDay
            Column(
                Modifier
                    .weight(1f)
                    .heightIn(min = 60.dp)
                    .clickable(role = Role.Tab) { onSelect(day) }
                    .semantics(mergeDescendants = true) {
                        selected = isSelected
                        contentDescription = dayLong(day) + if (isDone) ", done" else ""
                    }
                    .padding(top = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    letter,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (day == today) FontWeight.Bold else FontWeight.Normal,
                    color = if (day in scheduled) IronvellumColors.Ink else IronvellumColors.InkMuted,
                )
                // A fixed slot, so a tick never moves the letters.
                Box(Modifier.height(16.dp), contentAlignment = Alignment.Center) {
                    if (isDone) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = IronvellumColors.Emerald,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
                Box(
                    Modifier
                        .padding(top = 2.dp)
                        .size(width = 22.dp, height = 2.dp)
                        .background(if (isSelected) IronvellumColors.Emerald else Color.Transparent),
                )
            }
        }
    }
}

/** The card's one caps label. */
@Composable
private fun CardLabel(text: String, color: Color = IronvellumColors.InkMuted) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontFamily = ChakraPetch,
        color = color,
        letterSpacing = IronvellumTracking.InlineLabel,
        maxLines = 1,
    )
}

/** The card's subject: a rite name, with a chevron when tapping the card opens something. */
@Composable
private fun CardTitle(text: String, chevron: Boolean) {
    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            color = IronvellumColors.Ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (chevron) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = IronvellumColors.InkMuted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun PlanText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = IronvellumColors.InkMuted,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 2.dp),
    )
}

/** One straight segment per set, [done] of [total] filled, with 3dp between. */
@Composable
private fun SegmentBar(done: Int, total: Int) {
    if (total <= 0) return
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(total) { index ->
            Box(
                Modifier
                    .weight(1f)
                    .height(3.dp)
                    .background(if (index < done) IronvellumColors.Emerald else IronvellumColors.Rune),
            )
        }
    }
}

/** The day's movements, 52dp rows folded to one line: a check when done, a mini bar when part way. */
@Composable
private fun DayRows(rows: List<DayRow>) {
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        rows.forEachIndexed { index, row ->
            if (index > 0) InkDivider()
            Row(
                Modifier.fillMaxWidth().heightIn(min = ListRowHeight),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(26.dp)) {
                    if (row.checked) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = IronvellumColors.Emerald,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
                Text(
                    row.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (row.checked) IronvellumColors.InkMuted else IronvellumColors.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (row.partly) {
                    Row(Modifier.padding(start = 8.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        repeat(row.total.coerceAtMost(MINI_BAR_SEGMENTS)) { segment ->
                            Box(
                                Modifier
                                    .size(width = 8.dp, height = 3.dp)
                                    .background(if (segment < row.done) IronvellumColors.Emerald else IronvellumColors.Rune),
                            )
                        }
                    }
                }
                Text(
                    row.value,
                    style = MaterialTheme.typography.labelMedium,
                    color = IronvellumColors.InkMuted,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

/** "Next: Full Body A · Fri", a quiet link that shows that day. */
@Composable
private fun NextLink(next: WorkoutPreset, chevron: Boolean, onClick: () -> Unit) {
    val day = next.scheduledDay!!
    Row(
        Modifier
            .heightIn(min = 44.dp)
            .clickable(role = Role.Button, onClickLabel = "Show ${dayName(day)}", onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "Next: ${next.name} · ${dayName(day)}",
            style = MaterialTheme.typography.bodyMedium,
            color = IronvellumColors.SystemGreen,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (chevron) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = IronvellumColors.SystemGreen,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/** A fresh peak: gold tag, the lift, its figure and the climb that earned it. Opens the lift. */
@Composable
private fun PeakRow(peak: LiftRecord, onOpen: () -> Unit) {
    val best = peak.series.indexOfFirst { it == peak.bestE1rmKg }
    val gain = peak.bestE1rmKg - (peak.series.take(best).maxOrNull() ?: peak.bestE1rmKg)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = ListRowHeight)
            .clickable(role = Role.Button, onClickLabel = "Open ${peak.name}", onClick = onOpen)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "New peak · ${shortWhen(peak.bestAtMs)}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = IronvellumColors.SovereignGold,
                maxLines = 1,
            )
            Text(
                peak.name,
                style = MaterialTheme.typography.bodyMedium,
                color = IronvellumColors.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "${"%.0f".format(Locale.US, peak.bestE1rmKg)} kg",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = IronvellumColors.SovereignGold,
                maxLines = 1,
            )
            if (gain >= 0.5) {
                Text(
                    "+${"%.0f".format(Locale.US, gain)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = IronvellumColors.InkMuted,
                    modifier = Modifier.padding(start = 4.dp, bottom = 3.dp),
                )
            }
        }
        PeakSpark(peak.series.takeLast(PEAK_SPARK_POINTS), Modifier.size(width = 72.dp, height = 28.dp))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = IronvellumColors.InkMuted,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** A peak's climb in grey, each trial's best oldest first, ending on the gold dot that is the peak. */
@Composable
private fun PeakSpark(series: List<Double>, modifier: Modifier) {
    Canvas(modifier) {
        if (series.size < 2) return@Canvas
        val lo = series.min()
        val span = (series.max() - lo).takeIf { it > 0.0 } ?: 1.0
        val inset = 4.dp.toPx()
        val points = series.mapIndexed { i, v ->
            Offset(
                inset + (size.width - 2 * inset) * i / (series.size - 1),
                size.height - inset - ((v - lo) / span).toFloat() * (size.height - 2 * inset),
            )
        }
        val path = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(path, IronvellumColors.InkMuted, style = Stroke(width = 1.8.dp.toPx()))
        inkDot(points.last(), 3.2.dp.toPx(), IronvellumColors.SovereignGold)
    }
}

/** One 52dp row: what the scores wait for, and a green link to where it is added. */
@Composable
private fun BodyGapRow(gap: BodyGap, onFix: () -> Unit) {
    val (label, subline) = when (gap) {
        BodyGap.BOTH -> "Add your height and weight" to "Unlocks your scores"
        BodyGap.HEIGHT -> "Add your height" to "Unlocks BMI, FFMI and your scores"
        BodyGap.WEIGHT -> "Add a weight reading" to "Unlocks your scores"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = ListRowHeight)
            .clickable(role = Role.Button, onClick = onFix)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Outlined.Straighten, contentDescription = null, tint = IronvellumColors.InkMuted, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.Ink)
            Text(subline, style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted)
        }
        Text(
            if (gap == BodyGap.WEIGHT) "Add reading" else "Set height",
            style = MaterialTheme.typography.labelMedium,
            color = IronvellumColors.SystemGreen,
            maxLines = 1,
        )
    }
}

/**
 * The Veil under the day card, unboxed behind a hairline: essence, the hourly rate, the full-strength
 * bar, and inscriptions waiting with the way to inscribe them. On a respite day it grows to the main
 * element: a large essence figure with echoes and the relic multiplier. Tapping it opens the Veil.
 */
@Composable
private fun VeilSection(veil: VeilGlance?, hero: Boolean, onOpen: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        InkDivider()
        Column(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClickLabel = "Open the Veil", onClick = onOpen)
                .padding(top = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "The Veil",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = IronvellumColors.Ink,
                    modifier = Modifier.weight(1f),
                )
                veil?.let {
                    Text(
                        "${rateLabel(it.snapshot.rate.perHour)}/h",
                        style = MaterialTheme.typography.labelMedium,
                        color = IronvellumColors.InkMuted,
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = IronvellumColors.InkMuted,
                    modifier = Modifier.padding(start = 6.dp).size(18.dp),
                )
            }
            if (veil != null) {
                val state = veil.snapshot.state
                // Banked plus what is accruing now: the same headline the Veil shows. Read when
                // this recomposes, not every frame.
                val now = System.currentTimeMillis()
                val essence = Idle.collect(state, veil.snapshot.rate, now).essence
                Row(Modifier.padding(top = if (hero) 14.dp else 8.dp), verticalAlignment = Alignment.Bottom) {
                    Text(
                        "%,d".fmt(essence),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        fontSize = if (hero) 44.sp else 22.sp,
                        color = IronvellumColors.Ink,
                        maxLines = 1,
                    )
                    Text(
                        "essence",
                        style = MaterialTheme.typography.bodySmall,
                        color = IronvellumColors.InkMuted,
                        modifier = Modifier.padding(start = 6.dp, bottom = 4.dp),
                    )
                }
                veilStrength(state.lastCollectedAtMs, now)?.let { strength ->
                    InkRail(
                        fraction = strength.fraction,
                        modifier = Modifier.padding(top = 12.dp),
                        height = 3.dp,
                        fill = SolidColor(IronvellumColors.InkMuted),
                    )
                    Text(
                        strength.caption,
                        style = MaterialTheme.typography.bodySmall,
                        color = IronvellumColors.InkMuted,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                if (hero) {
                    Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                        VeilFigure("Echoes", state.figures.toString())
                        if (state.relicMultiplier > 1.0) VeilFigure("Relic", "×${"%.2f".fmt(state.relicMultiplier)}")
                    }
                }
            }
        }
        val waiting = veil?.inscriptions ?: 0
        if (waiting > 0) {
            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "$waiting ${plural(waiting, "inscription", "inscriptions")} waiting",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = IronvellumColors.SovereignGold,
                    modifier = Modifier.weight(1f),
                )
                // The Veil is where inscriptions are spent: the same destination as the section.
                Text(
                    "Inscribe",
                    style = MaterialTheme.typography.bodyMedium,
                    color = IronvellumColors.SystemGreen,
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .clickable(role = Role.Button, onClickLabel = "Inscribe in the Veil", onClick = onOpen)
                        .padding(start = 12.dp)
                        .wrapContentHeight(Alignment.CenterVertically),
                )
            }
        }
    }
}

/** A small label over a figure. */
@Composable
private fun VeilFigure(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = IronvellumColors.InkMuted)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = IronvellumColors.Ink)
    }
}
