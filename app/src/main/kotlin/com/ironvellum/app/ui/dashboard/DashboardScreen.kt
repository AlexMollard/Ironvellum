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
import androidx.compose.material.icons.outlined.Edit
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
import androidx.compose.ui.graphics.ColorFilter
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
import com.ironvellum.app.domain.Xp
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

data class DashboardUi(
    val profile: PlayerProfile? = null,
    val recent: List<WorkoutSession> = emptyList(),
    val unlockedCount: Int = 0,
    val presets: List<WorkoutPreset> = emptyList(),
    val streak: Int = 0,
    val stepsToday: Int = 0,
)

class DashboardViewModel(private val repo: Repository) : ViewModel() {

    private val selectedDay = MutableStateFlow(LocalDate.now().dayOfWeek.value)

    /** The worn crest, shown on the player card. Device-local, its own flow. */
    val equippedFrame: StateFlow<String?> = repo.observeEquippedFrame()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val ui: StateFlow<DashboardUi> = combine(
        repo.observeProfile(),
        repo.observeRecentSessions(5),
        repo.observeUnlockedTitles(),
        repo.observePresets(),
        repo.observeHistory(),
        repo.observeHealthDays(),
        selectedDay,
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
                presets.mapNotNull { it.scheduledDay }.toSet(),
                today,
            ),
            stepsToday = healthDays.firstOrNull { it.date == today }?.steps ?: 0,
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

    fun celebrationsSeen() = repo.clearPendingCelebrations()

    fun selectDay(day: Int) {
        selectedDay.value = day
    }

    fun beginPreset(presetId: Long, onStarted: (Long) -> Unit) {
        viewModelScope.launchGuarded("begin preset") { onStarted(repo.startSessionFromPreset(presetId)) }
    }
}

private val DAY_LABELS = linkedMapOf(1 to "MON", 2 to "TUE", 3 to "WED", 4 to "THU", 5 to "FRI", 6 to "SAT", 7 to "SUN")

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
    onOpenCodex: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenWorkout: (Long) -> Unit,
    viewModel: DashboardViewModel =
        viewModel(factory = viewModelFactory { initializer { DashboardViewModel(ironvellumRepository()) } }),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val selectedDay by viewModel.selected.collectAsStateWithLifecycle()
    val equippedFrame by viewModel.equippedFrame.collectAsStateWithLifecycle()
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
                    Image(
                        painter = painterResource(classEmblem(progress.level)),
                        contentDescription = ArmyClass.forLevel(progress.level).title,
                        modifier = Modifier.size(46.dp),
                        // the rank art is near-black on the void background
                        colorFilter = ColorFilter.tint(IronvellumColors.SystemGreen),
                    )
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
                            (profile?.name ?: "Lifter").uppercase(),
                            style = MaterialTheme.typography.headlineMedium,
                            fontFamily = ChakraPetch,
                            fontWeight = FontWeight.Bold,
                            // headlineMedium inherits a near-black onSurface here
                            color = IronvellumColors.Ink,
                            letterSpacing = 1.sp,
                            maxLines = 1,
                        )
                        val worn = profile?.currentTitleId?.let { Titles.byId(it)?.name }
                        Text(
                            // "the " costs four characters and says nothing:
                            // without it "Intermediate · Grand Marshal" holds
                            // one line on a 320dp screen, where it used to wrap
                            // under itself.
                            "${Rank.forLevel(progress.level)} · " +
                                ArmyClass.forLevel(progress.level).title.removePrefix("the "),
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = ChakraPetch,
                            // No tracking: labelMedium's 2sp over
                            // "Intermediate · Grand Marshal" is 56dp of pure
                            // letter spacing, which wrapped the line in two at
                            // 360dp and crowded the worn title underneath it.
                            letterSpacing = 0.sp,
                            color = IronvellumColors.SystemGreen,
                            // One line CLIPPED mid-word at a large font scale:
                            // "Intermediate · Grand" instead of the full
                            // "Intermediate · Grand Marshal". The class is
                            // earned, so it wraps rather than vanishes.
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
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
                    // Rank and crest as one insignia: the worn crest supplies
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
                        GaugeStat(
                            label = "STREAK",
                            value = if (ui.streak > 0) "${ui.streak}d" else "—",
                            accent = if (ui.streak > 0) IronvellumColors.SovereignGold else IronvellumColors.InkMuted,
                            fraction = (ui.streak / 7f).coerceIn(0f, 1f),
                        )
                    }
                } else {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    StepGauge(
                        steps = ui.stepsToday,
                        goal = STEP_GOAL,
                        modifier = Modifier.size(104.dp),
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        GaugeStat(
                            label = "STREAK",
                            value = if (ui.streak > 0) "${ui.streak}d" else "—",
                            accent = if (ui.streak > 0) IronvellumColors.SovereignGold else IronvellumColors.InkMuted,
                            fraction = (ui.streak / 7f).coerceIn(0f, 1f),
                        )
                        GaugeStat(
                            label = "TITLES",
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
                            label.take(1),
                            style = MaterialTheme.typography.titleSmall,
                            fontFamily = ChakraPetch,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = when {
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
                                .width(if (isSelected) 24.dp else 15.dp)
                                .height(if (isSelected) 5.dp else 4.dp)
                                .inkHairline(
                                    color = when {
                                        isToday -> IronvellumColors.SovereignGold
                                        preset != null -> IronvellumColors.SystemGreen
                                        else -> IronvellumColors.Rune
                                    },
                                    seed = day,
                                    thickness = if (isSelected) 3.dp else 2.dp,
                                ),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
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
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (isTodaySelected) "TODAY · ${DAY_LABELS[selectedDay].orEmpty()}"
                    else DAY_LABELS[selectedDay].orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = ChakraPetch,
                    color = if (isTodaySelected) IronvellumColors.SovereignGold else IronvellumColors.InkMuted,
                    letterSpacing = IronvellumTracking.SectionHeader,
                    // Unweighted, these two collided into each other at a large
                    // font scale instead of yielding. The day keeps what it
                    // needs; the tally gives way, since it repeats what the
                    // manifest below already shows.
                    maxLines = 1,
                )
                if (selectedPreset != null) {
                    Text(
                        "${selectedPreset.entries.size} MOVES · ${selectedPreset.entries.sumOf { it.targetSets }} SETS",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = ChakraPetch,
                        color = IronvellumColors.InkMuted,
                        letterSpacing = IronvellumTracking.InlineLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 8.dp).weight(1f, fill = false),
                        textAlign = TextAlign.End,
                    )
                }
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
                            )
                        }
                        Text(
                            "QUEST COMPLETE",
                            style = MaterialTheme.typography.headlineSmall,
                            fontFamily = ChakraPetch,
                            fontWeight = FontWeight.Bold,
                            color = IronvellumColors.SovereignGold,
                            letterSpacing = 1.sp,
                        )
                        Text(
                            "The Ledger is satisfied. A new quest rises tomorrow.",
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
                            maxLines = 1,
                            // Without this the note was sliced mid-word with
                            // no mark that anything followed.
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
                    IronvellumButton(
                        label = if (isTodaySelected) "Accept Quest" else "Start Anyway",
                        onClick = { viewModel.beginPreset(selectedPreset.id, onStartSession) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                Text(
                    "REST DAY",
                    style = MaterialTheme.typography.headlineSmall,
                    fontFamily = ChakraPetch,
                    fontWeight = FontWeight.Bold,
                    color = IronvellumColors.SystemGreen,
                    letterSpacing = 1.sp,
                )
                Text(
                    "Nothing scheduled. Recover, or pick another day above.",
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                )
                // The rest-day art was drawn for this panel and never wired in,
                // leaving the quest card's slack as dead space.
                Spacer(Modifier.weight(1f))
                Image(
                    painter = painterResource(R.drawable.art_empty_quests),
                    contentDescription = null,
                    // Explicit size: fillMaxWidth + heightIn let the intrinsic
                    // size win and it rendered postage-stamp small. The rest-day
                    // panel owns the page's slack, so the art gets most of it —
                    // but only when there IS slack: below the short-window
                    // threshold the page scrolls and the quest button has to
                    // stay reachable, so the art gives the room back.
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(if (shortWindow) 160.dp else 280.dp)
                        .alpha(0.6f),
                )
                Spacer(Modifier.weight(1f))
            }
        }

        // Last result and the way into the full routine: one quiet line each.
        ui.recent.firstOrNull()?.let { last ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        // A finished session still renders this row, so leaving
                        // it enabled=false made the dashboard's normal state a
                        // dead control: completed sessions go to their detail.
                        if (last.completedAtMs == null) onStartSession(last.id) else onOpenWorkout(last.id)
                    }
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    if (last.completedAtMs == null) "RESUME · ${last.label}" else "LAST · ${last.label}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = ChakraPetch,
                    color = if (last.completedAtMs == null) IronvellumColors.SovereignGold
                    else IronvellumColors.InkMuted,
                    letterSpacing = IronvellumTracking.InlineLabel,
                    maxLines = 1,
                )
                Text(
                    formatDate(last.completedAtMs ?: last.startedAtMs, "MMM d"),
                    style = MaterialTheme.typography.labelSmall,
                    color = IronvellumColors.InkMuted,
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onOpenPresets() }
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = null,
                    tint = IronvellumColors.InkMuted,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    "ROUTINE, PRESETS & FULL LOG",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = ChakraPetch,
                    color = IronvellumColors.InkMuted,
                    letterSpacing = IronvellumTracking.SectionHeader,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
    }

    // Titles reconciled at startup (health data, imports) have no session to
    // celebrate in, so the moment is paid out here on the first screen.
    val owed by viewModel.pendingCelebrations.collectAsStateWithLifecycle()
    val sex by viewModel.sex.collectAsStateWithLifecycle()
    AchievementOverlay(
        items = owed.map { def ->
            Achievement(
                banner = "TITLE EARNED",
                tagline = "DEED CLAIMED",
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

/** Counter row with its own hairline meter, so the cluster reads as instruments. */
@Composable
private fun GaugeStat(label: String, value: String, accent: Color, fraction: Float) {
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


/** Class emblem art escalates with the ladder: soldier -> knight -> commander -> grand marshal. */
private fun classEmblem(level: Int): Int = when {
    level >= 70 -> R.drawable.ic_rank_grand_marshal
    level >= 40 -> R.drawable.ic_rank_commander
    level >= 15 -> R.drawable.ic_rank_knight
    else -> R.drawable.ic_rank_soldier
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
