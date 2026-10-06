package com.ironvellum.app.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import com.ironvellum.app.ui.theme.inkDot
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.ironvellum.app.R
import com.ironvellum.app.data.Repository
import com.ironvellum.app.domain.ArmyClass
import com.ironvellum.app.domain.HealthDay
import com.ironvellum.app.domain.STEP_GOAL
import com.ironvellum.app.domain.PlayerProfile
import com.ironvellum.app.domain.Rank
import com.ironvellum.app.domain.RankBreakdown
import com.ironvellum.app.domain.Streak
import com.ironvellum.app.domain.Sex
import com.ironvellum.app.domain.TitleDef
import com.ironvellum.app.domain.Titles
import com.ironvellum.app.domain.UnlockedTitle
import com.ironvellum.app.domain.WorkoutPreset
import com.ironvellum.app.domain.WorkoutSession
import com.ironvellum.app.domain.SessionClock
import com.ironvellum.app.domain.TrainFocus
import com.ironvellum.app.domain.TrainingFocus
import com.ironvellum.app.domain.Xp
import com.ironvellum.app.domain.fmt
import com.ironvellum.app.ui.program.toPlanned
import androidx.compose.ui.unit.Dp
import com.ironvellum.app.ui.program.RiteMuscleMap
import com.ironvellum.app.domain.ProgramRules
import com.ironvellum.app.domain.PlannedPreset
import com.ironvellum.app.ui.components.AchievementOverlay
import com.ironvellum.app.ui.components.deedAchievement
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.components.LifterSigil
import com.ironvellum.app.ui.components.SectionHeader
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.components.RankSheet
import com.ironvellum.app.ui.components.XpBar
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.components.InkRail
import com.ironvellum.app.ui.theme.inkHairline
import com.ironvellum.app.ui.theme.inkArc
import com.ironvellum.app.ui.theme.inkBorder
import com.ironvellum.app.ui.theme.FIXED_FONT_SCALE
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.IronvellumTracking
import com.ironvellum.app.ui.components.formatDate
import com.ironvellum.app.ui.components.plural
import com.ironvellum.app.ui.ironvellumRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import com.ironvellum.app.ui.launchGuarded
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.BoxWithConstraints
import com.ironvellum.app.ui.components.Term
import com.ironvellum.app.ui.components.TermInfo


/** Which of height and weight the scores still need. */
enum class BodyGap { HEIGHT, WEIGHT, BOTH }

/** The Garrison's footer figures on Today. */
data class GarrisonGlance(val perHour: Double, val inscriptions: Int)
class DashboardUi(
    val profile: PlayerProfile? = null,
    val unlockedCount: Int = 0,
    val presets: List<WorkoutPreset> = emptyList(),
    val streak: Int = 0,
    val stepsToday: Int = 0,
    /** The focus the quest estimate is timed at. */
    val focus: TrainingFocus = TrainingFocus.MUSCLE,
    /** The lifter's own seconds per set; times the quest estimate. */
    val pace: SessionClock.Pace = SessionClock.Pace(),
    /** Weekdays (1 = Monday) whose scheduled rite was sealed this week, to the trial that sealed it. */
    val weekDone: Map<Int, WorkoutSession> = emptyMap(),
    /** Lifts whose peak was set lately AND beat an earlier trial: a first trial is not a peak. */
    val newPeaks: List<com.ironvellum.app.domain.LiftRecord> = emptyList(),
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
    val weekStart = today.with(java.time.DayOfWeek.MONDAY).atStartOfDay(zone).toInstant().toEpochMilli()
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
    history: List<Pair<WorkoutSession, List<com.ironvellum.app.domain.SessionSet>>>,
    exercises: List<com.ironvellum.app.domain.Exercise>,
    nowMs: Long,
    limit: Int = 2,
): List<com.ironvellum.app.domain.LiftRecord> =
    com.ironvellum.app.domain.LiftRecords.board(
        sessions = history.map { it.first }.filter { it.completedAtMs != null },
        sessionSets = history.associate { it.first.id to it.second },
        exercises = exercises.associateBy { it.id },
        nowMs = nowMs,
    )
        .filter { com.ironvellum.app.domain.LiftRecords.isFresh(it, nowMs) && it.series.indexOfFirst { v -> v == it.bestE1rmKg } > 0 }
        .sortedByDescending { it.bestAtMs }
        .take(limit)

class DashboardViewModel(
    private val repo: Repository,
    /** What the lifter last told the generator; times the quest estimate. */
    private val savedFocus: TrainingFocus? = null,
) : ViewModel() {

    private val selectedDay = MutableStateFlow(LocalDate.now().dayOfWeek.value)

    /** The worn crest, shown on the player card. Device-local, its own flow. */
    val equippedFrame: StateFlow<String?> = repo.observeEquippedFrame()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The unfinished session: the quest button continues it instead of starting another. */
    val live: StateFlow<WorkoutSession?> = repo.observeLiveSession()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val ui: StateFlow<DashboardUi> = combine(
        repo.observeProfile(),
        repo.observeUnlockedTitles(),
        repo.observePresets(),
        repo.observeHistory(),
        repo.observeHealthDays(),
        selectedDay,
        repo.observeExercises(),
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val profile = values[0] as PlayerProfile?
        @Suppress("UNCHECKED_CAST")
        val titles = values[1] as List<UnlockedTitle>
        @Suppress("UNCHECKED_CAST")
        val presets = values[2] as List<WorkoutPreset>
        @Suppress("UNCHECKED_CAST")
        val history = values[3] as List<Pair<WorkoutSession, List<com.ironvellum.app.domain.SessionSet>>>
        @Suppress("UNCHECKED_CAST")
        val healthDays = values[4] as List<HealthDay>
        val today = LocalDate.now()
        val doneDates = history
            .map {
                Instant.ofEpochMilli(it.first.completedAtMs ?: it.first.startedAtMs)
                    .atZone(ZoneId.systemDefault()).toLocalDate()
            }
            .toSet()
        // One streak rule for the whole app: the Today screen used to build its
        // own day records while the deeds, the idle rate and the leaderboard
        // used Titles.trainingStreakDays, so the same lifter could read two
        // different streaks.
        DashboardUi(
            profile = profile,
            unlockedCount = titles.size,
            presets = presets,
            streak = Titles.trainingStreakDays(
                doneDates,
                today,
            ),
            stepsToday = healthDays.firstOrNull { it.date == today }?.steps ?: 0,
            focus = SessionClock.focusFor(savedFocus, profile?.trainingMode),
            pace = SessionClock.pace(history),
            weekDone = weekDoneDays(history.map { it.first }, presets, today, ZoneId.systemDefault()),
            newPeaks = newPeaks(history, values[6] as List<com.ironvellum.app.domain.Exercise>, System.currentTimeMillis()),
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

    /** The Garrison at a glance: its live rate and the inscriptions waiting to be spent. */
    val garrison: StateFlow<GarrisonGlance?> = combine(repo.observeIdleRate(), repo.observeRolls()) { rate, rolls ->
        GarrisonGlance(perHour = rate.perHour, inscriptions = rolls)
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

private val DAY_LABELS = linkedMapOf(1 to "MON", 2 to "TUE", 3 to "WED", 4 to "THU", 5 to "FRI", 6 to "SAT", 7 to "SUN")

/**
 * "as of 14:05" for a sync today, "as of Sep 28" for an older one: Health
 * Connect trails the phone's own count, so the figure carries its age.
 */
internal fun stepsAsOfCaption(syncedAtMs: Long?, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): String? {
    if (syncedAtMs == null) return null
    val at = Instant.ofEpochMilli(syncedAtMs).atZone(zone)
    val pattern = if (at.toLocalDate() == today) "HH:mm" else "MMM d"
    return "as of " + at.format(java.time.format.DateTimeFormatter.ofPattern(pattern, java.util.Locale.US))
}

/**
 * Pitch of one manifest row, measured on device: 12sp of label between 3dp of
 * padding, plus the 2dp hairline under it. Fixed, because the app pins a single
 * text scale - which is what makes "how many rows fit" arithmetic instead of a
 * measure pass. Measured at 23.4dp and rounded up: over-counting rows clips the
 * last one, under-counting only wastes a slot.
 */
private val QUEST_ROW_HEIGHT = 24.dp

/** The widest a manifest row is spaced when the card has room to spare. */
private val QUEST_ROW_MAX_PITCH = 44.dp

/** A manifest row's pitch when the muscle map shares the card: a list, not a spread. */
private val QUEST_ROW_COMFORT = 32.dp

/**
 * The day card's muscle map: the figure's floor and full height, the FRONT /
 * BACK labels and key that ride with it, and the gap above it. Below the floor
 * the muscles stop being legible, so the map is left out rather than shrunk.
 */
private val DAY_MAP_FIGURE_MIN = 120.dp
private val DAY_MAP_FIGURE_MAX = 220.dp
private val DAY_MAP_KEY = 28.dp
private val DAY_MAP_GAP = 12.dp

/** One line of the run-together movement names: bodySmall's 16sp line, rounded up. */
private val QUEST_LINE_HEIGHT = 18.dp

/** The rest-day art's two sizes: it steps between them, never scales. */
private val REST_ART_LARGE = 200.dp
private val REST_ART_SMALL = 96.dp

/** RESPITE, the oath line and the 44dp "Next:" target, as measured on device. */
private val REST_TEXT_HEIGHT = 112.dp

/**
 * The day's movements, whole rows only, spread over the card's remaining
 * height when [fill]. A clipped half row reads as a rendering fault, so rows
 * that do not fit give way to a "+N MORE" line in the last slot. [done] marks
 * them as sealed rather than ahead.
 */
@Composable
private fun ColumnScope.Manifest(
    entries: List<com.ironvellum.app.domain.PresetEntry>,
    done: Boolean,
    fill: Boolean,
    rowPitch: Dp = QUEST_ROW_HEIGHT,
) {
    BoxWithConstraints(if (fill) Modifier.weight(1f) else Modifier) {
        val fits = if (fill) (maxHeight / QUEST_ROW_HEIGHT).toInt() else entries.size
        // Too short for a list that says anything: one row read as if the rite
        // were a single movement. Name them all, run together, instead.
        if (fits < minOf(entries.size, 3)) {
            Text(
                (if (done) "✓ " else "") + entries.joinToString(" · ") { it.exerciseName },
                style = MaterialTheme.typography.bodySmall,
                color = if (done) IronvellumColors.InkMuted else IronvellumColors.Ink,
                maxLines = (maxHeight / QUEST_LINE_HEIGHT).toInt().coerceAtLeast(1),
                overflow = TextOverflow.Ellipsis,
            )
            return@BoxWithConstraints
        }
        val moves = entries.take(if (fits < entries.size) fits - 1 else fits)
        // Spaced to the card, but never further apart than a list reads:
        // five rows strung across a tall card looked like five loose lines.
        val slots = moves.size + if (moves.size < entries.size) 1 else 0
        val pitch = if (fill) minOf(maxHeight / slots.coerceAtLeast(1), QUEST_ROW_MAX_PITCH) else rowPitch
        Column(
            verticalArrangement = Arrangement.Top,
            modifier = if (fill) Modifier.fillMaxHeight() else Modifier,
        ) {
            moves.forEachIndexed { index, entry ->
                Row(
                    Modifier.fillMaxWidth().height(pitch - 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (done) {
                        Text("\u2713", style = MaterialTheme.typography.labelMedium, color = IronvellumColors.SovereignGold)
                    } else {
                        Box(Modifier.size(4.dp).background(IronvellumColors.SystemGreen))
                    }
                    Text(
                        entry.exerciseName,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (done) IronvellumColors.InkMuted else IronvellumColors.Ink,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                    )
                    Text(
                        "${entry.targetSets}\u00D7${entry.targetReps}",
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = ChakraPetch,
                        color = if (done) IronvellumColors.InkMuted else IronvellumColors.SystemGreen,
                    )
                }
                if (index != moves.lastIndex) {
                    Box(Modifier.fillMaxWidth().height(2.dp).inkHairline(IronvellumColors.Rune, seed = index))
                }
            }
            val hidden = entries.size - moves.size
            if (hidden > 0) {
                Text(
                    "+$hidden MORE",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = ChakraPetch,
                    letterSpacing = IronvellumTracking.InlineLabel,
                    color = IronvellumColors.InkMuted,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/** Points of a peak's climb drawn beside it: enough to read the trend, few enough to stay a glance. */
private const val PEAK_SPARK_POINTS = 8

/** "Sat" within the week, "Oct 3" beyond it. */
private fun shortWhen(ms: Long): String =
    if (System.currentTimeMillis() - ms < 6L * 24 * 60 * 60 * 1000) formatDate(ms, "EEE") else formatDate(ms, "MMM d")

/** "48m", "1h 12m"; null for a trial with no sensible length (imports carry none). */
internal fun trialLength(trial: WorkoutSession): String? {
    val minutes = ((trial.completedAtMs ?: return null) - trial.startedAtMs) / 60_000
    return when {
        minutes < 1 || minutes > 24 * 60 -> null
        minutes < 60 -> "${minutes}m"
        else -> "${minutes / 60}h ${minutes % 60}m"
    }
}

/** RECENT's label, with what it is about on the right. */
@Composable
private fun RecentHeader(label: String, right: String, rightColor: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            color = IronvellumColors.InkMuted,
            letterSpacing = IronvellumTracking.SectionHeader,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        Text(right, style = MaterialTheme.typography.labelSmall, fontFamily = ChakraPetch, color = rightColor, letterSpacing = IronvellumTracking.InlineLabel)
    }
}

/** A peak's climb: each trial's best, oldest first, ending on the dot that is the peak. */
@Composable
private fun PeakSpark(series: List<Double>, modifier: Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        if (series.size < 2) return@Canvas
        val lo = series.min()
        val span = (series.max() - lo).takeIf { it > 0.0 } ?: 1.0
        val inset = 4.dp.toPx()
        val points = series.mapIndexed { i, v ->
            androidx.compose.ui.geometry.Offset(
                inset + (size.width - 2 * inset) * i / (series.size - 1),
                size.height - inset - ((v - lo) / span).toFloat() * (size.height - 2 * inset),
            )
        }
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(path, IronvellumColors.SystemGreen, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
        inkDot(points.last(), 3.dp.toPx(), IronvellumColors.EmeraldBright)
    }
}

/** "Wed" for 3. */
private fun dayName(day: Int): String =
    DAY_LABELS[day].orEmpty().lowercase().replaceFirstChar { it.uppercase() }

@Composable
private fun RestDayArt(modifier: Modifier) {
    Image(
        painter = painterResource(R.drawable.art_empty_quests),
        contentDescription = null,
        modifier = modifier.alpha(0.6f),
    )
}

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
    /** SET HEIGHT: straight to Settings → Profile, where height lives. */
    onSetHeight: () -> Unit = onOpenSettings,
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
    val equippedFrame by viewModel.equippedFrame.collectAsStateWithLifecycle()
    val live by viewModel.live.collectAsStateWithLifecycle()
    val bodyGap by viewModel.bodyGap.collectAsStateWithLifecycle()
    val strengthRank by viewModel.strengthRank.collectAsStateWithLifecycle()
    val rankBreakdown by viewModel.rankBreakdown.collectAsStateWithLifecycle()
    var rankOpen by remember { mutableStateOf(false) }
    val profile = ui.profile
    val progress = Xp.progress(profile?.totalXp ?: 0L)
    val today = LocalDate.now()
    val selectedPreset = ui.presets.firstOrNull { it.scheduledDay == selectedDay }
    val isTodaySelected = selectedDay == today.dayOfWeek.value

    val shown = remember { MutableTransitionState(false).apply { targetState = true } }

    // One screen, no scrolling, and not a stack of identical slabs: an
    // identity strip, a gauge cluster (radial steps beside stacked counters),
    // a hairline week rail, then the quest panel owning the remaining height.
    // containerSize, not screenHeightDp: it reports the WINDOW, so this
    // still holds in split screen where the app owns half the display.
    val density = LocalDensity.current
    val windowHeightDp = with(density) { LocalWindowInfo.current.containerSize.height.toDp().value }
    // The scale is pinned app-wide, so this is just the window's height in
    // design-size text lines.
    val linesOfRoom = windowHeightDp / FIXED_FONT_SCALE
    // Below this the column cannot hold the dashboard at all: landscape
    // measures ~411, a small display at 2x text ~347, a 320dp phone 693,
    // stock portrait 891. Only there does the page scroll; everywhere else it
    // is one screen, filled.
    val shortWindow = linesOfRoom < 620f

    Column(
        Modifier
            .fillMaxSize()
            .then(if (shortWindow) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(18.dp))

        // Identity strip: no panel around it, so the page opens on the player
        // rather than on a border.
        AnimatedVisibility(shown, enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 12 }) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // The name and what is worn are also the way to the Codex:
                    // there was no other route from here to its board.
                    Column(
                        Modifier
                            .weight(1f)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { onOpenCodex() },
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                (profile?.name ?: "Ironbound").uppercase(),
                                style = MaterialTheme.typography.headlineSmall,
                                fontFamily = ChakraPetch,
                                fontWeight = FontWeight.Bold,
                                color = IronvellumColors.Ink,
                                letterSpacing = 1.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            // Strength Rank only: ascension names the level on
                            // the chip below, so it is never a stat beside this one.
                            Text(
                                strengthRank.ifEmpty { "Unranked" },
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = ChakraPetch,
                                letterSpacing = 0.sp,
                                color = IronvellumColors.SystemGreen,
                                maxLines = 1,
                                modifier = Modifier
                                    .padding(start = 10.dp)
                                    .heightIn(min = 32.dp)
                                    .clickable(role = Role.Button, onClickLabel = "Show Strength Rank") { rankOpen = true }
                                    .wrapContentHeight(Alignment.CenterVertically),
                            )
                            TermInfo(Term.RANKS)
                        }
                        if (rankOpen) RankSheet(rankBreakdown) { rankOpen = false }
                        profile?.currentTitleId?.let { Titles.byId(it)?.name }?.let { worn ->
                            Text(
                                worn.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = ChakraPetch,
                                color = IronvellumColors.SovereignGold,
                                letterSpacing = IronvellumTracking.InlineLabel,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    // The settings gear: the glyph stays 22dp; the TARGET is 48dp.
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(MaterialTheme.shapes.small)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { onOpenSettings() },
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
                Spacer(Modifier.height(6.dp))
                // The level rides the bar it is climbing, like a game HUD: the
                // worn crest still supplies the chip it sits on.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LifterSigil(level = progress.level, frameId = equippedFrame, compact = true)
                    Spacer(Modifier.width(8.dp))
                    XpBar(progress.intoLevel, progress.needed, Modifier.weight(1f))
                }
            }
        }

        // The day's counters as one ruled line rather than a panel: a dial of
        // mostly empty arc cost the day card its movement list. The full
        // figures live on the Ledger.
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HudStat(
                "STEPS",
                "%,d".fmt(ui.stepsToday),
                if (ui.stepsToday >= STEP_GOAL) IronvellumColors.SovereignGold else IronvellumColors.EmeraldBright,
            )
            HudDivider()
            HudStat(
                "OATH",
                if (ui.streak > 0) "${ui.streak}d" else "—",
                if (ui.streak > 0) IronvellumColors.SovereignGold else IronvellumColors.InkMuted,
            )
            HudDivider()
            HudStat(
                "DEEDS",
                "${ui.unlockedCount}/${Titles.ALL.size}",
                IronvellumColors.EmeraldBright,
            )
        }
        Box(Modifier.fillMaxWidth().height(2.dp).inkHairline(IronvellumColors.Rune, seed = 9))

        Spacer(Modifier.height(12.dp))

        // The tick answers the same question the quest panel does: did THIS
        // day's scheduled workout get logged this week. It used to light for
        // any workout logged that weekday, so a lifter who ran a different
        // workout saw a bright tick above a quest still offering to start.
        fun questDoneFor(day: Int): Boolean = day in ui.weekDone

        // Week rail: a hairline, not a fourth card, under how the week stands.
        val scheduled = ui.presets.mapNotNull { it.scheduledDay }.toSet()
        if (scheduled.isNotEmpty()) {
            Text(
                "THIS WEEK · ${ui.weekDone.size} OF ${scheduled.size}",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = ChakraPetch,
                color = IronvellumColors.InkMuted,
                letterSpacing = IronvellumTracking.SectionHeader,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
        AnimatedVisibility(shown, enter = fadeIn(tween(300, delayMillis = 140))) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                DAY_LABELS.forEach { (day, label) ->
                    val preset = ui.presets.firstOrNull { it.scheduledDay == day }
                    val isToday = day == today.dayOfWeek.value
                    val isSelected = day == selectedDay
                    // The rail must answer "did I train this week?" at a
                    // glance: a done day is struck bright, a missed scheduled
                    // day stays dim. No red, no nag - the ledger, not guilt.
                    val isDone = questDoneFor(day)
                    Column(
                        Modifier
                            .weight(1f)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { viewModel.selectDay(day) }
                            .heightIn(min = 48.dp)
                            .semantics(mergeDescendants = true) {
                                contentDescription = java.time.DayOfWeek.of(day)
                                    .getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault()) +
                                    if (isDone) ", done" else ""
                            }
                            .padding(vertical = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            label.take(1) + if (isDone) "\u2713" else "",
                            style = MaterialTheme.typography.titleSmall,
                            fontFamily = ChakraPetch,
                            fontWeight = if (isSelected || isDone) FontWeight.Bold else FontWeight.Normal,
                            color = when {
                                // Done outranks selection: a selected day that
                                // hides its tick answers neither question.
                                isDone -> IronvellumColors.EmeraldBright
                                isSelected -> IronvellumColors.Ink
                                isToday -> IronvellumColors.SovereignGold
                                preset != null -> IronvellumColors.SystemGreen
                                else -> IronvellumColors.InkMuted
                            },
                        )
                        Spacer(Modifier.height(5.dp))
                        // A brushed mark rather than a filled rectangle: this
                        // is the day the lifter is standing on, so it should
                        // look struck by hand.
                        Box(
                            Modifier
                                .width(if (isSelected) 24.dp else if (isDone) 18.dp else 15.dp)
                                .height(if (isSelected) 5.dp else 4.dp)
                                .inkHairline(
                                    color = when {
                                        isDone -> IronvellumColors.EmeraldBright
                                        isSelected -> IronvellumColors.Ink
                                        isToday -> IronvellumColors.SovereignGold
                                        preset != null -> IronvellumColors.SystemGreen
                                        else -> IronvellumColors.Rune
                                    },
                                    seed = day,
                                    thickness = if (isSelected || isDone) 3.dp else 2.dp,
                                ),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        // Height lives in Settings and weight is a Ledger reading, so the
        // button goes to whichever one is still missing, height first.
        bodyGap?.let { gap ->
            BodyGapStrip(
                gap = gap,
                onFix = if (gap == BodyGap.WEIGHT) onOpenLedger else onSetHeight,
            )
            Spacer(Modifier.height(10.dp))
        }
        // Today's quest counts as done when THIS preset was sealed this week,
        // the same rule as the tick above it, so a rite taken early is not
        // offered again on its day.
        // Keep the session itself, not only the fact that one exists: the done
        // card reports what it actually earned.
        val questSessionToday = if (isTodaySelected) ui.weekDone[selectedDay] else null
        // The next scheduled rite after the selected day, wrapping the week.
        val nextRite = (1..7).map { (selectedDay - 1 + it) % 7 + 1 }
            .firstNotNullOfOrNull { day -> ui.presets.firstOrNull { it.scheduledDay == day } }

        // The day card takes the page's remaining height, so Today is one full
        // screen with no scroll. What it holds is drawn at fixed sizes - whole
        // movement rows, the rest-day art at one of two sizes - and only the
        // gaps between them grow: stretched content was why the art changed
        // size from day to day. A tap opens the rite it shows - on a rest day,
        // the next one - where it can be read whole and begun on Train.
        InkPanel(
            if (shortWindow) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().weight(1f),
            accent = when {
                questSessionToday != null -> IronvellumColors.SovereignGold
                isTodaySelected -> IronvellumColors.SystemGreen
                else -> IronvellumColors.Rune
            },
            onClick = (selectedPreset ?: nextRite)?.let { rite -> { onOpenRite(rite.id) } },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        questSessionToday != null -> "TODAY · SEALED"
                        isTodaySelected -> "TODAY · ${DAY_LABELS[selectedDay].orEmpty()}"
                        else -> DAY_LABELS[selectedDay].orEmpty()
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = ChakraPetch,
                    color = if (isTodaySelected) IronvellumColors.SovereignGold else IronvellumColors.InkMuted,
                    letterSpacing = IronvellumTracking.SectionHeader,
                    maxLines = 1,
                )
                Spacer(Modifier.weight(1f))
                // Sealed, what it earned sits in the header, so a short card
                // still has room for what comes next.
                if (questSessionToday != null && live == null) {
                    Text(
                        "+${questSessionToday.xpAwarded} XP · ${questSessionToday.strengthScore} STR",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = ChakraPetch,
                        color = IronvellumColors.SovereignGold,
                        letterSpacing = IronvellumTracking.InlineLabel,
                        maxLines = 1,
                    )
                }
                // A trial under way is continued from here in one tap; the last
                // sealed one now lives in RECENT below.
                live?.let { trial ->
                    Text(
                        "RESUME · ${trial.label}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = ChakraPetch,
                        color = IronvellumColors.SovereignGold,
                        letterSpacing = IronvellumTracking.InlineLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .clip(MaterialTheme.shapes.extraSmall)
                            .clickable { onStartSession(trial.id) }
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                    )
                }
            }
            when {
                selectedPreset != null -> {
                    Text(
                        selectedPreset.name.uppercase(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontFamily = ChakraPetch,
                        fontWeight = FontWeight.Bold,
                        color = IronvellumColors.EmeraldBright,
                        letterSpacing = 1.sp,
                    )
                    if (questSessionToday != null) {
                        if (live != null) Text(
                            "+${questSessionToday.xpAwarded} XP · ${questSessionToday.strengthScore} STR",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = ChakraPetch,
                            color = IronvellumColors.SovereignGold,
                            letterSpacing = IronvellumTracking.InlineLabel,
                        )
                        nextRite?.takeIf { it.id != selectedPreset.id }?.let { next ->
                            Text(
                                "Next: ${next.name} · ${dayName(next.scheduledDay!!)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = IronvellumColors.InkMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    } else {
                        Text(
                            SessionClock.planLine(selectedPreset.toPlanned().entries, ui.focus, ui.pace.secondsPerSet(selectedPreset.id)),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = ChakraPetch,
                            color = IronvellumColors.InkMuted,
                            letterSpacing = IronvellumTracking.InlineLabel,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    // Sealed, the same list reads as what was done.
                    val done = questSessionToday != null
                    if (shortWindow) {
                        Manifest(selectedPreset.entries, done = done, fill = false)
                    } else {
                        // With room to spare under the whole list, the rite's
                        // muscle map fills it, as on its own page. The list
                        // always wins: the map is the first thing to go.
                        val sets = remember(selectedPreset) {
                            ProgramRules.weeklyVolume(listOf(PlannedPreset(selectedPreset.name, "", null, selectedPreset.toPlanned().entries)))
                        }
                        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                            val mapRoom = maxHeight - QUEST_ROW_COMFORT * selectedPreset.entries.size - DAY_MAP_GAP
                            Column(Modifier.fillMaxSize()) {
                                if (sets.values.any { it > 0.0 } && mapRoom >= DAY_MAP_FIGURE_MIN + DAY_MAP_KEY) {
                                    Manifest(selectedPreset.entries, done = done, fill = false, rowPitch = QUEST_ROW_COMFORT)
                                    Spacer(Modifier.height(DAY_MAP_GAP))
                                    RiteMuscleMap(
                                        sets,
                                        selectedPreset.name,
                                        Modifier.fillMaxWidth(),
                                        figureHeight = (mapRoom - DAY_MAP_KEY).coerceAtMost(DAY_MAP_FIGURE_MAX),
                                    )
                                } else {
                                    Manifest(selectedPreset.entries, done = done, fill = true)
                                }
                            }
                        }
                    }
                }
                ui.presets.isEmpty() -> {
                    // A lifter who skipped onboarding has no routine at all:
                    // name the real state and offer the way out.
                    Text(
                        "NO CYCLE YET",
                        style = MaterialTheme.typography.headlineSmall,
                        fontFamily = ChakraPetch,
                        fontWeight = FontWeight.Bold,
                        color = IronvellumColors.SystemGreen,
                        letterSpacing = 1.sp,
                    )
                    Text(
                        "Your cycle is unwritten. Build one and its rites land here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = IronvellumColors.InkMuted,
                    )
                    Spacer(if (shortWindow) Modifier.height(12.dp) else Modifier.weight(1f))
                    IronvellumButton(label = "Build a Cycle", onClick = onOpenForge, modifier = Modifier.fillMaxWidth())
                    // Not everyone wants a plan first: a trial can be logged
                    // exercise by exercise with no cycle at all.
                    Spacer(Modifier.height(8.dp))
                    IronvellumButton(
                        label = "Begin an Open Trial",
                        onClick = { viewModel.beginOpen(onStartSession) },
                        quiet = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                else -> {
                    // The rest-day art is drawn at one of two fixed sizes, never
                    // scaled, so every rest day draws it the same: large under the
                    // text when the card has the height, small beside it when
                    // RECENT has taken that height.
                    @Composable
                    fun RespiteText() {
                        Text(
                            "RESPITE",
                            style = MaterialTheme.typography.headlineSmall,
                            fontFamily = ChakraPetch,
                            fontWeight = FontWeight.Bold,
                            color = IronvellumColors.SystemGreen,
                            letterSpacing = 1.sp,
                        )
                        // Owner rule: a rest day keeps the streak and says so.
                        Text(
                            if (ui.streak > 0) "Your oath holds through respite — ${plural(ui.streak, "1 day", "${ui.streak} days")} kept." else "A day of respite.",
                            style = MaterialTheme.typography.bodySmall,
                            color = IronvellumColors.InkMuted,
                        )
                        nextRite?.let { next ->
                            val nextDay = next.scheduledDay!!
                            Text(
                                "Next: ${next.name} · ${dayName(nextDay)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = IronvellumColors.SystemGreen,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .heightIn(min = 44.dp)
                                    .clickable(onClickLabel = "Show ${dayName(nextDay)}") { viewModel.selectDay(nextDay) }
                                    .wrapContentHeight(Alignment.CenterVertically),
                            )
                        }
                    }
                    if (shortWindow) {
                        RespiteText()
                        RestDayArt(Modifier.align(Alignment.CenterHorizontally).padding(vertical = 12.dp).size(REST_ART_SMALL))
                    } else {
                        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                            if (maxHeight >= REST_TEXT_HEIGHT + REST_ART_LARGE + 8.dp) {
                                Column(Modifier.fillMaxHeight()) {
                                    RespiteText()
                                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                        RestDayArt(Modifier.size(REST_ART_LARGE))
                                    }
                                }
                            } else {
                                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) { RespiteText() }
                                    RestDayArt(Modifier.padding(start = 12.dp).size(REST_ART_SMALL))
                                }
                            }
                        }
                    }
                }
            }
        }

        // What happened lately: the last sealed trial and any fresh peak. Gone
        // entirely before there is anything to show, rather than an empty box.
        // A fresh peak is news, so Today names it - the climb that earned it
        // drawn beside the number. Past trials themselves live on Train.
        val peak = ui.newPeaks.firstOrNull()
        if (peak != null) {
            Spacer(Modifier.height(12.dp))
            InkPanel(Modifier.fillMaxWidth()) {
                val best = peak.series.indexOfFirst { it == peak.bestE1rmKg }
                val gain = peak.bestE1rmKg - (peak.series.take(best).maxOrNull() ?: peak.bestE1rmKg)
                RecentHeader("NEW PEAK", shortWhen(peak.bestAtMs), IronvellumColors.InkMuted)
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text(peak.name, style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                "${"%.0f".format(java.util.Locale.US, peak.bestE1rmKg)} KG",
                                style = MaterialTheme.typography.titleLarge,
                                fontFamily = ChakraPetch,
                                fontWeight = FontWeight.Bold,
                                color = IronvellumColors.EmeraldBright,
                            )
                            if (gain >= 0.5) {
                                Text(
                                    "+${"%.0f".format(java.util.Locale.US, gain)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontFamily = ChakraPetch,
                                    color = IronvellumColors.SystemGreen,
                                    modifier = Modifier.padding(start = 6.dp, bottom = 3.dp),
                                )
                            }
                        }
                    }
                    PeakSpark(peak.series.takeLast(PEAK_SPARK_POINTS), Modifier.size(width = 88.dp, height = 36.dp))
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // The Garrison's way in. It left the nav bar and took the footer that
        // used to repeat the Train tab ("routine, workouts & full log"), so
        // Today spends one slim strip on it: the live rate, and the
        // inscriptions waiting, which are the reason to go there at all.
        val garrison by viewModel.garrison.collectAsStateWithLifecycle()
        val waiting = garrison?.inscriptions ?: 0
        Row(
            Modifier
                .fillMaxWidth()
                // A filled plate with a green edge and a chevron, so it reads as
                // a control to press rather than a caption under the quest.
                .clip(MaterialTheme.shapes.extraSmall)
                .background(Brush.verticalGradient(listOf(Color(0xFF16221C), Color(0xFF111914))))
                .inkBorder(
                    if (waiting > 0) IronvellumColors.SovereignGold.copy(alpha = 0.7f) else IronvellumColors.Emerald.copy(alpha = 0.55f),
                    MaterialTheme.shapes.extraSmall,
                    1.dp,
                )
                .clickable(onClickLabel = "Open the Veil") { onOpenGarrison() }
                .heightIn(min = 54.dp)
                .padding(start = 14.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Outlined.Shield,
                contentDescription = null,
                tint = IronvellumColors.EmeraldBright,
                modifier = Modifier.size(18.dp),
            )
            Text(
                "THE VEIL",
                fontFamily = ChakraPetch,
                color = IronvellumColors.Ink,
                style = MaterialTheme.typography.labelMedium,
                letterSpacing = IronvellumTracking.InlineLabel,
                modifier = Modifier.weight(1f),
            )
            garrison?.let { g ->
                Text(
                    "%.1f/H".format(java.util.Locale.US, g.perHour),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = ChakraPetch,
                    color = IronvellumColors.EmeraldBright,
                    letterSpacing = IronvellumTracking.InlineLabel,
                )
            }
            if (waiting > 0) {
                Text(
                    "· $waiting ${if (waiting == 1) "INSCRIPTION" else "INSCRIPTIONS"}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = ChakraPetch,
                    color = IronvellumColors.SovereignGold,
                    letterSpacing = IronvellumTracking.InlineLabel,
                    maxLines = 1,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = IronvellumColors.EmeraldBright,
                modifier = Modifier.size(20.dp),
            )
        }
        // Clear of the raised Train plate, which stands 10dp above the bar.
        Spacer(Modifier.height(28.dp))
    }

    // Titles reconciled at startup (health data, imports) have no session to
    // celebrate in, so the moment is paid out here on the first screen.
    val owed by viewModel.pendingCelebrations.collectAsStateWithLifecycle()
    val sex by viewModel.sex.collectAsStateWithLifecycle()
    val wornTitleId = viewModel.ui.collectAsStateWithLifecycle().value.profile?.currentTitleId
    AchievementOverlay(
        items = owed.map { deedAchievement(it, sex) },
        onDone = { viewModel.celebrationsSeen() },
        wornTitleId = wornTitleId,
        onWear = viewModel::wearTitle,
    )
}

/** One of the day's counters: its label, then its value. */
@Composable
private fun HudStat(label: String, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            color = IronvellumColors.InkMuted,
            letterSpacing = IronvellumTracking.InlineLabel,
            maxLines = 1,
        )
        Text(
            value,
            style = MaterialTheme.typography.labelLarge,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

/** The rule between two counters. */
@Composable
private fun HudDivider() {
    Box(Modifier.padding(horizontal = 6.dp).size(width = 1.dp, height = 14.dp).background(IronvellumColors.Rune))
}

/** Step-goal track: the same inked rail as every other progress bar. */
@Composable
private fun GoalTrack(fraction: Float) {
    InkRail(
        fraction = fraction,
        height = 6.dp,
        fill = Brush.horizontalGradient(
            listOf(IronvellumColors.SystemGreen, IronvellumColors.EmeraldBright),
        ),
        seed = 17,
    )
}


@Composable
private fun HeroStat(value: String, label: String, valueColor: Color = IronvellumColors.Ink) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = valueColor,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            color = IronvellumColors.InkMuted,
            letterSpacing = IronvellumTracking.InlineLabel,
        )
    }
}

/** Leading 32.dp day tile on quest-board rows; mirrors the weekly rail's cell treatment. */
@Composable
private fun DayTile(day: Int, isToday: Boolean) {
    Box(
        Modifier
            .size(32.dp)
            .clip(MaterialTheme.shapes.extraSmall)
            .background(
                if (isToday) Brush.verticalGradient(listOf(Color(0xFF1E3A2C), Color(0xFF16281E)))
                else Brush.verticalGradient(listOf(Color(0xFF121B16), Color(0xFF0F1712)))
            )
            .inkBorder(if (isToday) IronvellumColors.SystemGreen else IronvellumColors.Rune, MaterialTheme.shapes.extraSmall, 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            DAY_LABELS[day] ?: "",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            color = if (isToday) IronvellumColors.SystemGreen else IronvellumColors.InkMuted,
        )
    }
}

/** One quiet line and one button: what the scores wait for, and where to add it. */
@Composable
private fun BodyGapStrip(gap: BodyGap, onFix: () -> Unit) {
    InkPanel(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                when (gap) {
                    BodyGap.BOTH -> "Add your height and weight to unlock your scores."
                    BodyGap.HEIGHT -> "Add your height to unlock BMI, FFMI and your scores."
                    BodyGap.WEIGHT -> "Add a weight reading to unlock your scores."
                },
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.Ink,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            IronvellumButton(
                label = if (gap == BodyGap.WEIGHT) "Add reading" else "Set height",
                onClick = onFix,
                quiet = true,
            )
        }
    }
}
