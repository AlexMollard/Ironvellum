package com.ironvellum.app.ui.dashboard

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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ironvellum.app.data.IdleSnapshot
import com.ironvellum.app.data.Repository
import com.ironvellum.app.domain.Exercise
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
import com.ironvellum.app.domain.PlannedPreset
import com.ironvellum.app.domain.ProgramRules
import com.ironvellum.app.domain.WorkoutPreset
import com.ironvellum.app.domain.WorkoutSession
import com.ironvellum.app.domain.Xp
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

/** Trials whose SEALED stamp has already thudded in during this process: it lands once, not on every visit. */
private val stampedTrials = mutableSetOf<Long>()

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

    TodayContent(
        ui = ui,
        selectedDay = selectedDay,
        today = LocalDate.now(),
        live = live,
        liveSets = liveSets,
        bodyGap = bodyGap,
        strengthRank = strengthRank,
        rankBreakdown = rankBreakdown,
        veil = veil,
        actions = TodayActions(
            onStartSession = onStartSession,
            onOpenRite = onOpenRite,
            onOpenForge = onOpenForge,
            onOpenCodex = onOpenCodex,
            onOpenSettings = onOpenSettings,
            onOpenLedger = onOpenLedger,
            onOpenGarrison = onOpenGarrison,
            onOpenWorkout = onOpenWorkout,
            onSetHeight = onSetHeight,
            onOpenLift = onOpenLift,
            onSelectDay = viewModel::selectDay,
            onBeginPreset = { presetId -> viewModel.beginPreset(presetId, onStartSession) },
            onBeginOpen = { viewModel.beginOpen(onStartSession) },
        ),
    )

    // Titles reconciled at startup (health data, imports) have no session to
    // celebrate in, so the moment is paid out here on the first screen.
    val owed by viewModel.pendingCelebrations.collectAsStateWithLifecycle()
    val sex by viewModel.sex.collectAsStateWithLifecycle()
    AchievementOverlay(
        items = owed.map { deedAchievement(it, sex) },
        onDone = { viewModel.celebrationsSeen() },
        wornTitleId = ui.profile?.currentTitleId,
        onWear = viewModel::wearTitle,
    )
}

/** What Today's controls do. Every one defaults to nothing, so a test can render the page alone. */
internal class TodayActions(
    val onStartSession: (Long) -> Unit = {},
    val onOpenRite: (Long) -> Unit = {},
    val onOpenForge: () -> Unit = {},
    val onOpenCodex: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onOpenLedger: () -> Unit = {},
    val onOpenGarrison: () -> Unit = {},
    val onOpenWorkout: (Long) -> Unit = {},
    val onSetHeight: () -> Unit = {},
    val onOpenLift: (String) -> Unit = {},
    val onSelectDay: (Int) -> Unit = {},
    val onBeginPreset: (Long) -> Unit = {},
    val onBeginOpen: () -> Unit = {},
)

/**
 * Today with everything handed in, so a test can set up any state. It does not scroll while it can
 * fit: [TodayLayout] shares the height it is given between the parts and sheds exercise rows first.
 */
@Composable
internal fun TodayContent(
    ui: DashboardUi,
    selectedDay: Int,
    today: LocalDate,
    live: WorkoutSession?,
    liveSets: List<SessionSet>,
    bodyGap: BodyGap?,
    strengthRank: String,
    rankBreakdown: RankBreakdown?,
    veil: VeilGlance?,
    actions: TodayActions,
    nowMs: Long = System.currentTimeMillis(),
    /** Whether the Veil may move; tests hand in false to keep Compose idle. */
    motion: Boolean = rememberTodayMotion(),
) {
    var rankOpen by remember { mutableStateOf(false) }
    var oathOpen by remember { mutableStateOf(false) }
    val profile = ui.profile
    val progress = Xp.progress(profile?.totalXp ?: 0L)
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
    val sealedSets = sealedTrial?.let { ui.sealedSets[it.id].orEmpty() }.orEmpty()
    val cardRows: List<DayRow> = when (kind) {
        DayKind.LIVE -> trialRows(liveSets, ui.exercises, sealed = false)
        DayKind.SEALED -> trialRows(sealedSets, ui.exercises, sealed = true)
        DayKind.BEGIN, DayKind.PLANNED -> plannedRows(selectedPreset!!.entries)
        DayKind.RESPITE, DayKind.NO_CYCLE -> emptyList()
    }

    // Header: the name and what is worn are also the way to the Codex (there
    // was no other route from here to its board), then the rank link and the gear.
    val head: @Composable () -> Unit = {
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier
                    .weight(1f)
                    .clickable(onClickLabel = "Open the Codex") { actions.onOpenCodex() },
            ) {
                NameRow(
                    sigil = { LifterMark(Modifier.testTag("lifter-mark")) },
                    name = {
                        Text(
                            profile?.name ?: "Ironbound",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = IronvellumColors.Ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    // Strength Rank only: ascension names the level on the rail
                    // below, so it is never a stat beside this one.
                    rank = {
                        Row(
                            Modifier
                                .heightIn(min = 44.dp)
                                .clickable(role = Role.Button, onClickLabel = "Show Strength Rank") { rankOpen = true }
                                .padding(start = 10.dp, end = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // The one gold mark on the header: a rank is earned, "Unranked" has none.
                            if (strengthRank.isNotEmpty() && strengthRank != Rank.UNRANKED) {
                                RankLozenge()
                                Spacer(Modifier.width(8.dp))
                            }
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
                    },
                )
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
                    .clickable(role = Role.Button, onClickLabel = "Open settings") { actions.onOpenSettings() },
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

        WeekRail(
            selectedDay = selectedDay,
            today = today.dayOfWeek.value,
            scheduled = ui.presets.mapNotNull { it.scheduledDay }.toSet(),
            done = ui.weekDone.keys,
            onSelect = actions.onSelectDay,
        )
    }

    // The day card: the only boxed area, sized to what it holds. `rows` is where its exercise block goes.
    val cardClick: (() -> Unit)? = when (kind) {
        DayKind.SEALED -> sealedTrial?.let { trial -> { actions.onOpenWorkout(trial.id) } }
        DayKind.LIVE -> selectedPreset?.takeIf { it.id == liveTrial?.presetId }?.let { rite -> { actions.onOpenRite(rite.id) } }
        DayKind.BEGIN, DayKind.PLANNED -> selectedPreset?.let { rite -> { actions.onOpenRite(rite.id) } }
        DayKind.RESPITE -> nextRite?.let { rite -> { actions.onOpenRite(rite.id) } }
        DayKind.NO_CYCLE -> null
    }
    // The rite's muscle focus, as the rite pages read it: sets per muscle, from the rite's own movements.
    val glyphSets = remember(selectedPreset) {
        selectedPreset?.let { ProgramRules.weeklyVolume(listOf(PlannedPreset(it.name, "", null, it.toPlanned().entries))) }.orEmpty()
    }
    val daysKept = ui.streak
    val card: @Composable (rows: @Composable () -> Unit) -> Unit = { rows ->
        InkPanel(Modifier.fillMaxWidth().padding(top = 8.dp), onClick = cardClick) {
            when (kind) {
                DayKind.LIVE -> {
                    val trial = liveTrial!!
                    val ofToday = selectedPreset != null && selectedPreset.id == trial.presetId
                    RiteHeader(
                        title = trial.label,
                        narrator = narratorLine(kind, liveDone, liveWork.size, daysKept),
                        meta = AnnotatedString("${if (ofToday) "Today's trial" else "Under way"} · $liveDone of ${liveWork.size} sets logged"),
                        corner = if (ofToday) ({ RiteGlyph(glyphSets) }) else null,
                    )
                    SegmentBar(liveDone, liveWork.size)
                    rows()
                    Spacer(Modifier.height(12.dp))
                    IronvellumButton(
                        label = "Continue ${trial.label}",
                        onClick = { actions.onStartSession(trial.id) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                DayKind.SEALED -> {
                    val trial = sealedTrial!!
                    val rite = selectedPreset!!
                    val done = sealedSets.count { it.done && !it.warmup }
                    // The stamp thuds in once, the first time this process shows a rite sealed just now.
                    val thud = LocalTodayLive.current && LocalTodayMotion.current &&
                        remember(trial.id) { stampIsFresh(trial.completedAtMs, nowMs) && stampedTrials.add(trial.id) }
                    RiteHeader(
                        title = rite.name,
                        narrator = narratorLine(kind, daysKept = daysKept),
                        meta = buildAnnotatedString {
                            withStyle(SpanStyle(color = IronvellumColors.SovereignGold, fontWeight = FontWeight.SemiBold)) { append("Sealed") }
                            append(" · ${cardRows.size} ${plural(cardRows.size, "exercise", "exercises")} · $done ${plural(done, "set", "sets")}")
                            trialLength(trial)?.let { append(" · $it") }
                            append(" · ")
                            withStyle(SpanStyle(color = IronvellumColors.SovereignGold)) { append("+${trial.xpAwarded} XP") }
                        },
                        corner = { SealedStamp(date = formatDate(trial.completedAtMs ?: trial.startedAtMs, "EEE d MMM"), thud = thud) },
                    )
                    rows()
                    nextRite?.takeIf { it.id != rite.id }?.let { next ->
                        NextLink(next, chevron = false) { actions.onSelectDay(next.scheduledDay!!) }
                    }
                }
                DayKind.BEGIN, DayKind.PLANNED -> {
                    val rite = selectedPreset!!
                    RiteHeader(
                        title = rite.name,
                        narrator = narratorLine(kind, daysKept = daysKept),
                        meta = AnnotatedString(
                            (if (isTodaySelected) "Today's trial" else dayLong(selectedDay)) + " · " +
                                sentencePlan(SessionClock.planLine(rite.toPlanned().entries, ui.focus, ui.pace.secondsPerSet(rite.id))),
                        ),
                        corner = { RiteGlyph(glyphSets) },
                    )
                    rows()
                    // Today is where the day's rite is begun: Train only plans it.
                    if (kind == DayKind.BEGIN) {
                        Spacer(Modifier.height(12.dp))
                        IronvellumButton(
                            label = "Begin ${rite.name}",
                            onClick = { actions.onBeginPreset(rite.id) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                DayKind.RESPITE -> {
                    RiteHeader(
                        title = "Respite",
                        narrator = narratorLine(kind, daysKept = daysKept),
                        meta = null,
                        lead = { LedgerMotif() },
                    )
                    nextRite?.let { next -> NextLink(next, chevron = true) { actions.onSelectDay(next.scheduledDay!!) } }
                }
                DayKind.NO_CYCLE -> {
                    RiteHeader(title = "No cycle yet", narrator = narratorLine(kind), meta = null)
                    Spacer(Modifier.height(16.dp))
                    IronvellumButton(label = "Forge a cycle", onClick = actions.onOpenForge, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }

    // Plain rows under the card, divided by hairlines: no panels. Never dropped to make room.
    val peak = ui.newPeaks.firstOrNull()
    val plainRows = buildList<@Composable () -> Unit> {
        if (kind == DayKind.NO_CYCLE) {
            add {
                // Not everyone wants a plan first: a trial can be logged
                // exercise by exercise with no cycle at all.
                ListRow(
                    label = "Begin an open trial",
                    subline = "Log exercises as you go, no cycle needed",
                    onClick = actions.onBeginOpen,
                )
            }
        }
        // A trial under way off the card (another day selected) is one tap from here.
        if (liveTrial != null && kind != DayKind.LIVE) {
            add {
                ListRow(
                    label = "Continue ${liveTrial.label}",
                    subline = "$liveDone of ${liveWork.size} sets logged",
                    onClick = { actions.onStartSession(liveTrial.id) },
                )
            }
        }
        // Height lives in Settings and weight is a Ledger reading, so the link
        // goes to whichever one is still missing, height first.
        bodyGap?.let { gap ->
            add { BodyGapRow(gap, onFix = if (gap == BodyGap.WEIGHT) actions.onOpenLedger else actions.onSetHeight) }
        }
        if (peak != null) add { PeakRow(peak) { actions.onOpenLift(peak.name) } }
    }
    val plain: @Composable () -> Unit = {
        if (plainRows.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                plainRows.forEachIndexed { index, row ->
                    if (index > 0) InkDivider()
                    row()
                }
            }
        }
    }

    CompositionLocalProvider(LocalTodayMotion provides motion) {
        TodayLayout(
            rows = cardRows,
            head = head,
            card = card,
            plain = plain,
            veil = { full ->
                val form = when {
                    !full -> VeilForm.COMPACT
                    kind == DayKind.RESPITE -> VeilForm.HERO
                    else -> VeilForm.FULL
                }
                VeilSection(veil, form, nowMs, actions.onOpenGarrison)
            },
        )
    }
    if (rankOpen) RankSheet(rankBreakdown) { rankOpen = false }
    if (oathOpen) TermDialog(Term.OATH) { oathOpen = false }
}

/** Beneath this the exercise rows stop tightening and start folding into "+N more". Rows are not tap targets, the card is. */
private val TIGHT_ROW = 40.dp

/** The gap above the card's exercise block. */
private val ROWS_TOP_PAD = 10.dp

/** The "+N more" line's least height. */
private val MORE_LINE = 28.dp

/** Clear of the raised Train plate, which stands 10dp above the bar. */
private val BOTTOM_CLEARANCE = 28.dp

/** The page's side margin; the card pads its content another 16dp inside it. */
private val GUTTER = 16.dp

/** One pass of the page: head, card, plain rows, Veil (absent when probing the rest), bottom clearance. */
@Composable
private fun TodayPage(
    head: @Composable () -> Unit,
    card: @Composable (rows: @Composable () -> Unit) -> Unit,
    plain: @Composable () -> Unit,
    rows: @Composable () -> Unit,
    veil: (@Composable () -> Unit)?,
    live: Boolean = false,
) {
    // Only the page that is shown is live: a measuring probe must neither animate nor spend a one-off.
    CompositionLocalProvider(LocalTodayLive provides live) {
        Column(Modifier.fillMaxWidth().padding(horizontal = GUTTER)) {
            head()
            card(rows)
            plain()
            veil?.invoke()
            Spacer(Modifier.height(BOTTOM_CLEARANCE))
        }
    }
}

/**
 * Lays Today out to the height it is given, never scrolling while it can fit. It measures the page
 * without its exercise rows and Veil, one row, the "+N more" line and both Veil forms, hands those
 * heights to [fitToday] and composes the page once with what fits. The probes sit in slots of their
 * own and are never placed.
 *
 * Degradation, in order: exercise rows tighten from 52dp toward 40dp; the Veil's full form (the hero on
 * a respite day) gives way to the compact one; rows fold into "+N more". Plain rows are never dropped.
 * Every Veil form carries its reserved inscriptions line, so the probes already include it.
 */
@Composable
private fun TodayLayout(
    rows: List<DayRow>,
    head: @Composable () -> Unit,
    card: @Composable (rows: @Composable () -> Unit) -> Unit,
    plain: @Composable () -> Unit,
    veil: @Composable (full: Boolean) -> Unit,
) {
    SubcomposeLayout(Modifier.fillMaxSize()) { constraints ->
        val width = constraints.maxWidth
        val bounded = constraints.hasBoundedHeight
        val available = if (bounded) constraints.maxHeight else Int.MAX_VALUE / 4
        val loose = Constraints(minWidth = width, maxWidth = width)
        // A probe stays composed while it is measured but is never placed, so it must expose nothing
        // to semantics: a screen reader and a test would otherwise meet every probed part twice.
        fun heightOf(slot: Any, content: @Composable () -> Unit): Int =
            subcompose(slot) { Box(Modifier.clearAndSetSemantics {}) { content() } }.sumOf { it.measure(loose).height }

        val chrome = heightOf("chrome") { TodayPage(head, card, plain, rows = {}, veil = null) }
        val veilCompact = heightOf("veilCompact") { Column(Modifier.fillMaxWidth().padding(horizontal = GUTTER)) { veil(false) } }
        val veilFull = heightOf("veilFull") { Column(Modifier.fillMaxWidth().padding(horizontal = GUTTER)) { veil(true) } }
        // A row sits inside the page gutter and the card's own padding; only its content height matters here.
        val rowContent =
            if (rows.isEmpty()) 0
            else heightOf("row") { Column(Modifier.fillMaxWidth().padding(horizontal = GUTTER * 2)) { ExerciseRow(rows.first(), height = null, divider = false) } }
        val moreLine =
            if (rows.isEmpty()) 0
            else heightOf("more") { Column(Modifier.fillMaxWidth().padding(horizontal = GUTTER * 2)) { MoreLine(hidden = 1, divider = false) } }
        val naturalRow = maxOf(ListRowHeight.roundToPx(), rowContent)
        val tightRow = maxOf(TIGHT_ROW.roundToPx(), rowContent)

        val fit = fitToday(
            TodayBudget(
                available = available,
                chrome = chrome,
                rowCount = rows.size,
                rowsTopPad = ROWS_TOP_PAD.roundToPx(),
                naturalRow = naturalRow,
                tightRow = tightRow,
                moreLine = moreLine,
                veilFull = veilFull,
                veilCompact = veilCompact,
            ),
        )
        val placeables = if (fit != null) {
            val rowHeight = fit.rowHeight.toDp()
            subcompose("page") {
                TodayPage(head, card, plain, rows = { ExerciseBlock(rows, fit.rows, rowHeight, hidden = rows.size - fit.rows) }, veil = { veil(fit.fullVeil) }, live = true)
            }.map { it.measure(Constraints(minWidth = width, maxWidth = width, maxHeight = available)) }
        } else {
            // The least this page can be still does not fit (a very large font, a very small screen):
            // scroll the whole of it. Scrolling is a worse day than a squeezed one, but clipping a row,
            // a button or the Veil is worse than scrolling.
            val rowHeight = naturalRow.toDp()
            subcompose("scroll") {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    TodayPage(head, card, plain, rows = { ExerciseBlock(rows, rows.size, rowHeight, hidden = 0) }, veil = { veil(true) }, live = true)
                }
            }.map { it.measure(Constraints.fixed(width, available)) }
        }
        layout(width, if (bounded) available else placeables.maxOf { it.height }) {
            placeables.forEach { it.place(0, 0) }
        }
    }
}

/**
 * The week at a glance: one letter per day, the scheduled ones in Ink, today bold, a small gold seal
 * under a day whose rite is sealed (the same test as the card's SEALED state) and an emerald underline
 * under the day being read.
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
                    if (isDone) SealMark(14.dp)
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

/**
 * The day's movements: the first [shown] of [rows], each [rowHeight] tall with its hairline inside that
 * height, then a muted "+N more" line for the [hidden] rest. Nothing at all when there is nothing to show.
 */
@Composable
private fun ExerciseBlock(rows: List<DayRow>, shown: Int, rowHeight: Dp, hidden: Int) {
    if (shown == 0 && hidden == 0) return
    Column(Modifier.fillMaxWidth().padding(top = ROWS_TOP_PAD).testTag("today-rows")) {
        rows.take(shown).forEachIndexed { index, row -> ExerciseRow(row, rowHeight, divider = index > 0) }
        if (hidden > 0) MoreLine(hidden, divider = shown > 0)
    }
}

/** One movement folded to a line: a check when done, a mini bar when part way. [height] null measures its content. */
@Composable
private fun ExerciseRow(row: DayRow, height: Dp?, divider: Boolean) {
    Box(Modifier.fillMaxWidth().then(if (height != null) Modifier.height(height) else Modifier)) {
        if (divider) InkDivider(Modifier.align(Alignment.TopStart))
        Row(Modifier.fillMaxWidth().align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
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

/** "+3 more": the rows that did not fit; the card's own tap opens the rite or trial that lists them. */
@Composable
private fun MoreLine(hidden: Int, divider: Boolean) {
    Box(Modifier.fillMaxWidth().heightIn(min = MORE_LINE), contentAlignment = Alignment.CenterStart) {
        if (divider) InkDivider(Modifier.align(Alignment.TopStart))
        Text(
            "+$hidden more",
            style = MaterialTheme.typography.bodySmall,
            color = IronvellumColors.InkMuted,
            maxLines = 1,
            modifier = Modifier.padding(start = 26.dp),
        )
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

/**
 * A fresh peak as one 52dp plain row: the lift, "New peak" in gold over when and how far it rose, and
 * the set that did it. Opens the lift.
 */
@Composable
private fun PeakRow(peak: LiftRecord, onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = "Open ${peak.name}", onClick = onOpen)
            .heightIn(min = ListRowHeight)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                peak.name,
                style = MaterialTheme.typography.bodyMedium,
                color = IronvellumColors.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = IronvellumColors.SovereignGold)) { append("New peak") }
                    append(" · ${shortWhen(peak.bestAtMs)} · up ${peakGainText(peak)}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            peakSetText(peak),
            style = MaterialTheme.typography.labelMedium,
            color = IronvellumColors.Ink,
            maxLines = 1,
        )
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = IronvellumColors.InkMuted)
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
