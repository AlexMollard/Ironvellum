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
import com.ironvellum.app.ui.components.AchievementOverlay
import com.ironvellum.app.ui.components.deedAchievement
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.components.LifterSigil
import com.ironvellum.app.ui.components.SectionHeader
import com.ironvellum.app.ui.components.InkPanel
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
    val recent: List<WorkoutSession> = emptyList(),
    val unlockedCount: Int = 0,
    val presets: List<WorkoutPreset> = emptyList(),
    val streak: Int = 0,
    val stepsToday: Int = 0,
    /** When Health Connect was last read; the step count is only as fresh as this. */
    val stepsSyncedAtMs: Long? = null,
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
        repo.observeRecentSessions(5),
        repo.observeUnlockedTitles(),
        repo.observePresets(),
        repo.observeHistory(),
        repo.observeHealthDays(),
        selectedDay,
        repo.observeHealthSyncedAt(),
        repo.observeExercises(),
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val profile = values[0] as PlayerProfile?
        @Suppress("UNCHECKED_CAST")
        val recent = values[1] as List<WorkoutSession>
        @Suppress("UNCHECKED_CAST")
        val titles = values[2] as List<UnlockedTitle>
        @Suppress("UNCHECKED_CAST")
        val presets = values[3] as List<WorkoutPreset>
        @Suppress("UNCHECKED_CAST")
        val history = values[4] as List<Pair<WorkoutSession, List<com.ironvellum.app.domain.SessionSet>>>
        @Suppress("UNCHECKED_CAST")
        val healthDays = values[5] as List<HealthDay>
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
            recent = recent,
            unlockedCount = titles.size,
            presets = presets,
            streak = Titles.trainingStreakDays(
                doneDates,
                today,
            ),
            stepsToday = healthDays.firstOrNull { it.date == today }?.steps ?: 0,
            stepsSyncedAtMs = values[7] as Long?,
            focus = SessionClock.focusFor(savedFocus, profile?.trainingMode),
            pace = SessionClock.pace(history),
            weekDone = weekDoneDays(history.map { it.first }, presets, today, ZoneId.systemDefault()),
            newPeaks = newPeaks(history, values[8] as List<com.ironvellum.app.domain.Exercise>, System.currentTimeMillis()),
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

    /** Strength Rank from the lift boards, "Unranked" without one; blank until first read. */
    val strengthRank: StateFlow<String> = repo.observeStrengthRank()
        .map { it ?: Rank.UNRANKED }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

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
private fun ColumnScope.Manifest(entries: List<com.ironvellum.app.domain.PresetEntry>, done: Boolean, fill: Boolean) {
    BoxWithConstraints(if (fill) Modifier.weight(1f) else Modifier) {
        val fits = if (fill) (maxHeight / QUEST_ROW_HEIGHT).toInt() else entries.size
        // Below two slots there is no room for both: one real movement beats a
        // line saying how many there are.
        val moves = entries.take(if (fits < entries.size) (fits - 1).coerceAtLeast(1) else fits)
        Column(
            verticalArrangement = if (fill) Arrangement.SpaceEvenly else Arrangement.Top,
            modifier = if (fill) Modifier.fillMaxHeight() else Modifier,
        ) {
            moves.forEachIndexed { index, entry ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp),
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
            if (hidden > 0 && fits >= 2) {
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

/** "Wed" for 3. */
private fun dayName(day: Int): String =
    DAY_LABELS[day].orEmpty().lowercase().replaceFirstChar { it.uppercase() }

/** One line of RECENT: what, and what it came to. */
@Composable
private fun RecentRow(left: String, right: String, rightColor: Color, onClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraSmall)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            left,
            style = MaterialTheme.typography.bodySmall,
            color = IronvellumColors.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            right,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            color = rightColor,
            letterSpacing = IronvellumTracking.InlineLabel,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

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
    val roomForGauges = linesOfRoom >= 400f
    // A 320dp-class phone reports ~693, a 360dp one ~780, the owner's ~891.
    // Below this the page is still whole but has no dp to spare, so the
    // informative half of it gets thinner rather than the quest card starving.
    val tightHome = linesOfRoom < 740f
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(
                        Modifier
                            .weight(1f)
                            // the worn title is also the way to change it:
                            // there was no route from here to the Codex board
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { onOpenCodex() },
                    ) {
                        Text(
                            (profile?.name ?: "Ironbound").uppercase(),
                            style = MaterialTheme.typography.headlineMedium,
                            fontFamily = ChakraPetch,
                            fontWeight = FontWeight.Bold,
                            // headlineMedium inherits a near-black onSurface here
                            color = IronvellumColors.Ink,
                            letterSpacing = 1.sp,
                            maxLines = 1,
                        )
                        val worn = profile?.currentTitleId?.let { Titles.byId(it)?.name }
                        // Strength Rank only: ascension names the level on
                        // the sigil, so it is never a stat beside this one.
                        Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f, fill = false)) {
                        Text(
                            if (strengthRank.isEmpty()) "STRENGTH RANK" else "STRENGTH RANK · $strengthRank",
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = ChakraPetch,
                            // No tracking: labelMedium's 2sp is pure letter
                            // spacing that wrapped the line at 360dp and
                            // crowded the worn title underneath it.
                            letterSpacing = 0.sp,
                            color = IronvellumColors.SystemGreen,
                            // Earned, so it wraps at a large font scale
                            // rather than clipping mid-word.
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        }
                        TermInfo(Term.RANKS)
                        }
                        // A third line only when it says something: with no
                        // title worn AND none earned, "NO TITLE EARNED YET" was
                        // a line telling the lifter about a thing they cannot
                        // do yet - and the line the rank had to wrap around.
                        val titleLine = worn?.uppercase()
                            ?: "TAP TO WEAR A TITLE".takeIf { ui.unlockedCount > 0 }
                        if (titleLine != null) {
                            Text(
                                titleLine,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = ChakraPetch,
                                color = if (worn != null) IronvellumColors.SovereignGold else IronvellumColors.InkMuted,
                                letterSpacing = IronvellumTracking.InlineLabel,
                                maxLines = 1,
                                // Ellipsis, not a hard cut: a truncated title should
                                // look truncated rather than misspelt.
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    // The settings gear: settings left the bottom nav, so
                    // this fixed-size tap target rides at the end of the
                    // identity strip — the weighted name column absorbs it,
                    // so the player card itself never moves.
                    // The glyph stays 22dp; the TARGET is 48dp. .size() before
                    // .clickable() made the tappable area the glyph itself.
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
                    // Level and crest as one insignia: the worn crest supplies
                    // the plate this level sits on.
                    LifterSigil(level = progress.level, frameId = equippedFrame)
                }
                Spacer(Modifier.height(10.dp))
                XpBar(progress.intoLevel, progress.needed)
                // The rail's one caption is the next ascension, not the XP: "25
                // / 200 XP", "LV 2 → 3" and "175 XP TO GO" were one fact three
                // ways beside the sigil, which already names the current one.
                ArmyClass.nextFor(progress.level)?.let { next ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${next.title.uppercase()} AT ${next.level}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = ChakraPetch,
                        color = IronvellumColors.InkMuted,
                        letterSpacing = IronvellumTracking.InlineLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.End),
                    )
                }
            }
        }

        // A lifter on the largest display size AND 2x text has roughly 320dp
        // by 390dp of usable space. Everything above the quest panel is
        // unweighted, so it wins the measure pass and the panel's own button
        // ends up measured at zero height — the primary action, gone. The step
        // gauge is the largest thing that is purely informative (the same count
        // is on Stats), so it is what gives way. Scrolling the page instead is
        // not an option here: the panel below is weighted, and a weight inside
        // a scrolling column gets an infinite height and collapses.
        // Height in dp alone is the wrong measure: the largest display size
        // still reports 693dp tall, it is the 2x TEXT inside it that overflows.
        // What matters is how many lines of text the screen can hold, so divide
        // the height by the font scale. Stock reads 891, largest display with
        // 2x text reads 347 — the only configuration measured to lose the
        // button, and the only one that drops the gauges.

        if (roomForGauges) {
            Spacer(Modifier.height(if (tightHome) 10.dp else 16.dp))
        }

        // Gauge cluster: one radial dial carries the day's steps, the column
        // beside it carries the counters — different shapes, one panel.
        //
        // The dial is 104dp of pure readout. On a 320dp-class screen that is
        // the difference between the quest card listing movements and listing
        // none, so there it becomes a rail like its neighbours: same two
        // numbers, a third of the height. The count and the goal stay on the
        // screen either way.
        if (roomForGauges) {
        AnimatedVisibility(shown, enter = fadeIn(tween(300, delayMillis = 90))) {
            InkPanel(Modifier.fillMaxWidth()) {
                if (tightHome) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        GaugeStat(
                            label = "STEPS",
                            // Grouped, like the dial it replaces: "10000" read as a raw field.
                            value = "${"%,d".fmt(ui.stepsToday)} / ${"%,d".fmt(STEP_GOAL)}",
                            accent = if (ui.stepsToday >= STEP_GOAL) IronvellumColors.SovereignGold
                            else IronvellumColors.EmeraldBright,
                            fraction = (ui.stepsToday.toFloat() / STEP_GOAL).coerceIn(0f, 1f),
                        )
                        StepsAsOf(ui.stepsSyncedAtMs, today)
                        GaugeStat(
                            label = "OATH",
                            value = if (ui.streak > 0) "${ui.streak}d" else "—",
                            accent = if (ui.streak > 0) IronvellumColors.SovereignGold else IronvellumColors.InkMuted,
                            fraction = (ui.streak / 7f).coerceIn(0f, 1f),
                            note = if (ui.streak > 0) null else "seal a trial to swear it",
                        )
                    }
                } else {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        StepGauge(
                            steps = ui.stepsToday,
                            goal = STEP_GOAL,
                            modifier = Modifier.size(104.dp),
                        )
                        StepsAsOf(ui.stepsSyncedAtMs, today)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        GaugeStat(
                            label = "OATH",
                            value = if (ui.streak > 0) "${ui.streak}d" else "—",
                            accent = if (ui.streak > 0) IronvellumColors.SovereignGold else IronvellumColors.InkMuted,
                            fraction = (ui.streak / 7f).coerceIn(0f, 1f),
                            note = if (ui.streak > 0) null else "seal a trial to swear it",
                        )
                        GaugeStat(
                            label = "DEEDS",
                            value = "${ui.unlockedCount}/${Titles.ALL.size}",
                            accent = IronvellumColors.EmeraldBright,
                            fraction = (ui.unlockedCount.toFloat() / Titles.ALL.size).coerceIn(0f, 1f),
                        )
                        // PROGRAMS counted the user's own preset list - a number
                        // they set, not one they earn, and the Train tab is a list
                        // of exactly those. Two rails beside the dial, both of
                        // things that move.
                    }
                }
                }
            }
        }
        }

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
                        Text(
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
                                modifier = Modifier.padding(top = 6.dp),
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
                    Manifest(selectedPreset.entries, done = questSessionToday != null, fill = !shortWindow)
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
        // Not the trial the day card is already reporting as sealed.
        val lastSealed = ui.recent.firstOrNull { it.completedAtMs != null && it.id != questSessionToday?.id }
        if (lastSealed != null || ui.newPeaks.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            InkPanel(Modifier.fillMaxWidth()) {
                Text(
                    "RECENT",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = ChakraPetch,
                    color = IronvellumColors.InkMuted,
                    letterSpacing = IronvellumTracking.SectionHeader,
                    modifier = Modifier.semantics { heading() },
                )
                ui.newPeaks.take(if (lastSealed != null) 1 else 2).forEach { peak ->
                    RecentRow(
                        left = "NEW PEAK · ${peak.name}",
                        right = "${"%.0f".format(java.util.Locale.US, peak.bestE1rmKg)} KG",
                        rightColor = IronvellumColors.EmeraldBright,
                    )
                }
                lastSealed?.let { trial ->
                    RecentRow(
                        left = "${trial.label} · ${formatDate(trial.completedAtMs ?: trial.startedAtMs, "MMM d")}",
                        right = "+${trial.xpAwarded} XP",
                        rightColor = IronvellumColors.SovereignGold,
                        onClick = { onOpenWorkout(trial.id) },
                    )
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

/**
 * Radial day gauge: sweep of the step goal with the count in the middle.
 * A dial next to flat meters is what stops the page reading as stacked cards.
 */
@Composable
private fun StepGauge(steps: Int, goal: Int, modifier: Modifier = Modifier) {
    val fraction = (steps.toFloat() / goal).coerceIn(0f, 1f)
    val hit = steps >= goal
    val ring = if (hit) IronvellumColors.SovereignGold else IronvellumColors.EmeraldBright
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 7.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            // Brushed sweeps: a constant-width, constant-radius ring is as
            // machine-made as a ruled line. The gradient is dropped because a
            // brush carries one colour at a time; the ring hue still reports
            // whether the goal was met.
            val centre = Offset(size.width / 2f, size.height / 2f)
            val radius = (minOf(arcSize.width, arcSize.height)) / 2f
            inkArc(centre, radius, 135f, 270f, IronvellumColors.Rune, stroke, seed = 71, taperEnds = false)
            if (fraction > 0f) {
                inkArc(centre, radius, 135f, 270f * fraction, ring, stroke, seed = 73)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "%,d".fmt(steps),
                style = MaterialTheme.typography.titleLarge,
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.Bold,
                color = if (hit) IronvellumColors.SovereignGold else IronvellumColors.Ink,
            )
            Text(
                "/ ${"%,d".fmt(goal)}",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = ChakraPetch,
                color = IronvellumColors.InkMuted,
            )
            Text(
                "STEPS",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = ChakraPetch,
                color = IronvellumColors.InkMuted,
                letterSpacing = IronvellumTracking.InlineLabel,
                fontSize = 8.sp,
            )
        }
    }
}

/** The steps' age under the count; nothing until Health Connect has been read. */
@Composable
private fun StepsAsOf(syncedAtMs: Long?, today: LocalDate) {
    val caption = stepsAsOfCaption(syncedAtMs, today) ?: return
    Text(
        caption,
        style = MaterialTheme.typography.labelSmall,
        fontFamily = ChakraPetch,
        color = IronvellumColors.InkMuted,
        fontSize = 9.sp,
        maxLines = 1,
    )
}

/** Counter row with its own hairline meter, so the cluster reads as instruments. */
@Composable
private fun GaugeStat(label: String, value: String, accent: Color, fraction: Float, note: String? = null) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = ChakraPetch,
                color = IronvellumColors.InkMuted,
                letterSpacing = IronvellumTracking.InlineLabel,
            )
            Text(
                value,
                style = MaterialTheme.typography.labelLarge,
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
        }
        Spacer(Modifier.height(3.dp))
        InkRail(
            fraction = fraction,
            height = 3.dp,
            fill = Brush.horizontalGradient(listOf(accent, accent)),
            seed = label.hashCode(),
        )
        // A caption under the rail: for a bare "—" it names what would move it.
        if (note != null) {
            Text(
                note,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = ChakraPetch,
                color = IronvellumColors.InkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
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
