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
import com.ironvellum.app.domain.TrainingFocus
import com.ironvellum.app.domain.Xp
import com.ironvellum.app.ui.program.toPlanned
import com.ironvellum.app.ui.components.Achievement
import com.ironvellum.app.ui.components.AchievementOverlay
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.wrapContentHeight
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
)

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
 * measure pass. Measured at 23.4dp on device and rounded up: over-counting rows clips the
 * last one, under-counting only wastes a slot.
 */
private val QUEST_ROW_HEIGHT = 24.dp

@Composable
fun DashboardScreen(
    onStartSession: (Long) -> Unit,
    onOpenPresets: () -> Unit,
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
    // stock portrait 891.
    //
    // 520 was too low. Measured at 533 the page still refused to scroll, the
    // quest card's unweighted text ate its slot, and the Accept button was
    // measured down to a 14dp bar with no label on it - the exact failure the
    // weighted manifest exists to prevent, one threshold below where it was
    // being watched for.
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
                        // Strength Rank and ascension are two labelled values,
                        // never joined into one phrase.
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
                        Text(
                            "ASCENSION · ${ArmyClass.forLevel(progress.level).title}",
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = ChakraPetch,
                            letterSpacing = 0.sp,
                            color = IronvellumColors.SystemGreen,
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
                // No caption under the rail. It read "25 / 200 XP", then "LV 2
                // → 3", then "175 XP TO GO" - one fact three ways, when the
                // sigil beside it already stamps the level.
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
                            value = "${"%,d".format(ui.stepsToday)} / ${"%,d".format(STEP_GOAL)}",
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
        val weekZone = ZoneId.systemDefault()
        val weekMonday = today.with(java.time.DayOfWeek.MONDAY)
        fun questDoneFor(day: Int): Boolean {
            val preset = ui.presets.firstOrNull { it.scheduledDay == day } ?: return false
            val date = weekMonday.plusDays((day - 1).toLong())
            val start = date.atStartOfDay(weekZone).toInstant().toEpochMilli()
            val end = date.plusDays(1).atStartOfDay(weekZone).toInstant().toEpochMilli()
            return ui.recent.any { session ->
                val at = session.completedAtMs ?: session.startedAtMs
                session.presetId == preset.id && at >= start && at < end
            }
        }

        // Week rail: a hairline, not a fourth card.
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
                            .padding(vertical = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
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
        // Today's quest counts as done when a session started from THIS preset
        // was completed today — otherwise the panel kept offering the same
        // quest after it was already finished.
        val todayStart = java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        // Keep the session itself, not only the fact that one exists: the done
        // card reports what it actually earned.
        val questSessionToday = selectedPreset?.takeIf { isTodaySelected }?.let { preset ->
            ui.recent.firstOrNull {
                it.presetId == preset.id && (it.completedAtMs ?: 0L) >= todayStart
            }
        }
        val questDoneToday = questSessionToday != null

        // The quest panel takes every remaining pixel - which is what keeps its
        // button measured and on screen - and the manifest inside spreads into
        // them, so the slack becomes row spacing instead of a void.
        //
        // On a short window (landscape, or a small display at 2x text) a
        // weighted panel is measured after the unweighted content above it,
        // gets nothing, and takes the button down with it. There it wraps its
        // content and the page scrolls instead.
        InkPanel(
            if (shortWindow) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().weight(1f),
            accent = when {
                questDoneToday -> IronvellumColors.SovereignGold
                isTodaySelected -> IronvellumColors.SystemGreen
                else -> IronvellumColors.Rune
            },
        ) {
            // The day and the plan line each get a line: side by side, the
            // plan's time estimate was the part that fell off ("~31…").
            // The last workout shares the day's line, right-aligned: as its own
            // row between the card and the Garrison strip it read as a stray
            // caption squeezed between two panels.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (isTodaySelected) "TODAY · ${DAY_LABELS[selectedDay].orEmpty()}"
                    else DAY_LABELS[selectedDay].orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = ChakraPetch,
                    color = if (isTodaySelected) IronvellumColors.SovereignGold else IronvellumColors.InkMuted,
                    letterSpacing = IronvellumTracking.SectionHeader,
                    maxLines = 1,
                )
                Spacer(Modifier.weight(1f))
                ui.recent.firstOrNull()?.let { last ->
                    val live = last.completedAtMs == null
                    Text(
                        if (live) "RESUME · ${last.label}"
                        else "LAST · ${last.label} · ${formatDate(last.completedAtMs ?: last.startedAtMs, "MMM d")}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = ChakraPetch,
                        color = if (live) IronvellumColors.SovereignGold else IronvellumColors.InkMuted,
                        letterSpacing = IronvellumTracking.InlineLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .clip(MaterialTheme.shapes.extraSmall)
                            // A finished workout opens its record; a live one
                            // is continued.
                            .clickable { if (live) onStartSession(last.id) else onOpenWorkout(last.id) }
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                    )
                }
            }
            if (selectedPreset != null) {
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
            if (selectedPreset != null) {
                if (questDoneToday) {
                    // The done state replaces the manifest entirely: the
                    // SpaceEvenly rows Column is weighted, so rendering both
                    // made six exercises collide with the complete text.
                    Column(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            selectedPreset.name.uppercase(),
                            style = MaterialTheme.typography.headlineSmall,
                            fontFamily = ChakraPetch,
                            fontWeight = FontWeight.Bold,
                            color = IronvellumColors.EmeraldBright,
                            letterSpacing = 1.sp,
                        )
                        if (selectedPreset.note.isNotBlank()) {
                            Text(
                                selectedPreset.note,
                                style = MaterialTheme.typography.bodySmall,
                                color = IronvellumColors.InkMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            "SEALED",
                            style = MaterialTheme.typography.headlineSmall,
                            fontFamily = ChakraPetch,
                            fontWeight = FontWeight.Bold,
                            color = IronvellumColors.SovereignGold,
                            letterSpacing = 1.sp,
                        )
                        Text(
                            "The ink is dry on today's trial. Tomorrow's page waits.",
                            style = MaterialTheme.typography.bodySmall,
                            color = IronvellumColors.InkMuted,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            // Was the literal "6 MOVES · 22 SETS · DONE": a
                            // layout stand-in that shipped, telling every lifter
                            // the same invented tally whatever she trained.
                            "+${questSessionToday.xpAwarded} XP · ${questSessionToday.strengthScore} STR · DONE",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = ChakraPetch,
                            letterSpacing = IronvellumTracking.InlineLabel,
                            color = IronvellumColors.InkMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                } else {
                    Text(
                        selectedPreset.name.uppercase(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontFamily = ChakraPetch,
                        fontWeight = FontWeight.Bold,
                        color = IronvellumColors.EmeraldBright,
                        letterSpacing = 1.sp,
                    )
                    if (selectedPreset.note.isNotBlank()) {
                        Text(
                            selectedPreset.note,
                            style = MaterialTheme.typography.bodySmall,
                            color = IronvellumColors.InkMuted,
                            // The note also lives on the Train card, so it is
                            // the lifter's own words: two lines, readable,
                            // before any mark that more follows.
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    // The manifest is the ONLY flexible child, so the button
                    // below is measured first and always lands on screen. With
                    // the header unweighted and the button last, a large system
                    // font scale let the header eat the card and the button was
                    // measured at zero height: the app's primary action simply
                    // vanished at 2.0x.
                    //
                    // Whole rows only. A scroll here clipped the last movement
                    // through its middle - on a 320dp screen the card had room
                    // for one and a half rows, and half a row reads as a
                    // rendering fault rather than as "there is more". Text does
                    // not scale (the app pins one font size), so a row is a
                    // constant height and how many fit is arithmetic.
                    // The card owns the page's slack (that is what keeps its
                    // button measured), so the manifest spends it: rows spread
                    // over the slot rather than stacking at the top and leaving
                    // a void above the button.
                    //
                    // No weight once the page scrolls, though: a weighted child
                    // in an unbounded column is measured with no space at all,
                    // and the manifest rendered as nothing. Unweighted it sees
                    // an infinite slot, lists every movement, and the page
                    // scrolls - which is the deal at that size.
                    BoxWithConstraints(if (shortWindow) Modifier else Modifier.weight(1f)) {
                        val fits = (maxHeight / QUEST_ROW_HEIGHT).toInt()
                        val all = selectedPreset.entries
                        // The space is the cap. A fixed limit of three left a
                        // band of dead card below the button on a roomy screen
                        // and showed nothing at all on a cramped one.
                        //
                        // When rows are dropped the "+N MORE" line takes the
                        // last slot, so it is never the thing that clips. Below
                        // two slots there is no room for both: one real movement
                        // beats a line saying how many there are, since the
                        // header already reads "5 MOVES · 18 SETS".
                        val moves = all.take(if (fits < all.size) (fits - 1).coerceAtLeast(1) else fits)
                        Column(verticalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxHeight()) {
                            moves.forEachIndexed { index, entry ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Box(Modifier.size(4.dp).background(IronvellumColors.SystemGreen))
                                    Text(
                                        entry.exerciseName,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = IronvellumColors.Ink,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                    )
                                    Text(
                                        "${entry.targetSets}\u00D7${entry.targetReps}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontFamily = ChakraPetch,
                                        color = IronvellumColors.SystemGreen,
                                    )
                                }
                                if (index != moves.lastIndex) {
                                    Box(Modifier.fillMaxWidth().height(2.dp).inkHairline(IronvellumColors.Rune, seed = index))
                                }
                            }
                            val hidden = all.size - moves.size
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
                    // A clipped last row sitting flush against the button read
                    // as the button covering the row. The gap makes the clip
                    // look like scrolling, which is what it is.
                    Spacer(Modifier.height(10.dp))
                    val resume = live
                    IronvellumButton(
                        // "Start Anyway" read as an apology: the day header
                        // already says which day this is, so the button just
                        // states the act. A trial already under way is
                        // continued, never offered as a fresh start.
                        label = when {
                            resume != null -> "Continue ${resume.label}"
                            isTodaySelected -> "Begin Trial"
                            else -> "Begin Trial"
                        },
                        onClick = {
                            if (resume != null) onStartSession(resume.id)
                            else viewModel.beginPreset(selectedPreset.id, onStartSession)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else if (ui.presets.isEmpty()) {
                // A lifter who skipped onboarding has no routine at all: every
                // day reads REST DAY and "pick another day above" dead-ends on
                // the same card. Name the real state and offer the way out.
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
                Spacer(Modifier.weight(1f))
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
            } else {
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
                // The next scheduled day after this one, wrapping the week.
                val next = (1..7).map { (selectedDay - 1 + it) % 7 + 1 }
                    .firstNotNullOfOrNull { day -> ui.presets.firstOrNull { it.scheduledDay == day } }
                if (next != null) {
                    val nextDay = next.scheduledDay!!
                    val dayName = DAY_LABELS[nextDay].orEmpty().lowercase().replaceFirstChar { it.uppercase() }
                    Text(
                        "Next: ${next.name} · $dayName",
                        style = MaterialTheme.typography.bodySmall,
                        color = IronvellumColors.SystemGreen,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .heightIn(min = 44.dp)
                            .clickable(onClickLabel = "Show $dayName") { viewModel.selectDay(nextDay) }
                            .wrapContentHeight(Alignment.CenterVertically),
                    )
                }
                // The rest-day art was drawn for this panel. It lives in this
                // branch only: outside it, it rendered on training days too
                // and its weighted spacers starved the manifest to zero rows.
                if (shortWindow) Spacer(Modifier.weight(1f))
                Image(
                    painter = painterResource(R.drawable.art_empty_quests),
                    contentDescription = null,
                    // Explicit size: fillMaxWidth + heightIn let the intrinsic
                    // size win and it rendered postage-stamp small. The rest-day
                    // panel owns the page's slack, so the art gets most of it —
                    // but only when there IS slack: below the short-window
                    // threshold the page scrolls, so the art gives the room back.
                    // In the weighted panel the art takes what slack is left, up to
                    // 280dp: a fixed 280dp pushed the panel's own button off screen
                    // once the body prompt sat above it.
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .then(if (shortWindow) Modifier.size(160.dp) else Modifier.weight(1f).fillMaxWidth().heightIn(max = 280.dp).padding(vertical = 8.dp))
                        .alpha(0.6f),
                )
                if (shortWindow) Spacer(Modifier.weight(1f))
                // A respite is a suggestion, not a lock: someone who wants to
                // train today can take the next rite early. Once a trial is
                // sealed today the offer is spent.
                val trainedToday = ui.recent.any { (it.completedAtMs ?: 0L) >= todayStart }
                if (isTodaySelected && next != null && !trainedToday) {
                    IronvellumButton(
                        label = "Begin ${next.name} now",
                        onClick = { viewModel.beginPreset(next.id, onStartSession) },
                        quiet = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        // The Garrison's way in. It left the nav bar and took the footer that
        // used to repeat the Train tab ("routine, workouts & full log"), so
        // Today spends one slim strip on it: the live rate, and the
        // inscriptions waiting, which are the reason to go there at all.
        val garrison by viewModel.garrison.collectAsStateWithLifecycle()
        val waiting = garrison?.inscriptions ?: 0
        Row(
            Modifier
                .padding(top = 8.dp)
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
        // The page's side margin again below the last strip, so it sits clear
        // of the nav bar instead of reading as content cut off mid-scroll.
        Spacer(Modifier.height(16.dp))
    }

    // Titles reconciled at startup (health data, imports) have no session to
    // celebrate in, so the moment is paid out here on the first screen.
    val owed by viewModel.pendingCelebrations.collectAsStateWithLifecycle()
    val sex by viewModel.sex.collectAsStateWithLifecycle()
    AchievementOverlay(
        items = owed.map { def ->
            Achievement(
                // Noun contract: a deed is earned; "Claim" belongs to
                // technique mastery only.
                banner = "DEED EARNED",
                name = def.name,
                subtitle = def.describeFor(sex).uppercase(),
            )
        },
        onDone = { viewModel.celebrationsSeen() },
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
                "%,d".format(steps),
                style = MaterialTheme.typography.titleLarge,
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.Bold,
                color = if (hit) IronvellumColors.SovereignGold else IronvellumColors.Ink,
            )
            Text(
                "/ ${"%,d".format(goal)}",
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
                    BodyGap.WEIGHT -> "Log your weight to unlock your scores."
                },
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.Ink,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            IronvellumButton(
                label = if (gap == BodyGap.WEIGHT) "Log weight" else "Set height",
                onClick = onFix,
                quiet = true,
            )
        }
    }
}
