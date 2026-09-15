package com.monarch.app.ui.dashboard

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
import androidx.compose.foundation.shape.CutCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import com.monarch.app.R
import com.monarch.app.data.Repository
import com.monarch.app.domain.ArmyClass
import com.monarch.app.domain.HealthDay
import com.monarch.app.domain.STEP_GOAL
import com.monarch.app.domain.PlayerProfile
import com.monarch.app.domain.Rank
import com.monarch.app.domain.Streak
import com.monarch.app.domain.TitleDef
import com.monarch.app.domain.Titles
import com.monarch.app.domain.UnlockedTitle
import com.monarch.app.domain.WorkoutPreset
import com.monarch.app.domain.WorkoutSession
import com.monarch.app.domain.Xp
import com.monarch.app.ui.components.Achievement
import com.monarch.app.ui.components.AchievementOverlay
import com.monarch.app.ui.components.MonarchButton
import com.monarch.app.ui.components.HunterSigil
import com.monarch.app.ui.components.SectionHeader
import com.monarch.app.ui.components.SystemWindow
import com.monarch.app.ui.components.XpBar
import com.monarch.app.ui.theme.ChakraPetch
import com.monarch.app.ui.theme.MonarchColors
import com.monarch.app.ui.theme.MonarchTracking
import com.monarch.app.ui.components.formatDate
import com.monarch.app.ui.monarchRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import androidx.compose.foundation.layout.heightIn

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
        val history = values[4] as List<Pair<WorkoutSession, List<com.monarch.app.domain.SessionSet>>>
        @Suppress("UNCHECKED_CAST")
        val healthDays = values[5] as List<HealthDay>
        val today = LocalDate.now()
        val doneDates = history
            .map {
                Instant.ofEpochMilli(it.first.completedAtMs ?: it.first.startedAtMs)
                    .atZone(ZoneId.systemDefault()).toLocalDate()
            }
            .toSet()
        val records = (0L..730L).map { offset ->
            val date = today.minusDays(offset)
            Streak.DayRecord(
                date = date,
                scheduledDay = presets.firstOrNull { it.scheduledDay == date.dayOfWeek.value }?.scheduledDay,
                completed = date in doneDates,
            )
        }
        DashboardUi(
            profile = profile,
            recent = recent,
            unlockedCount = titles.size,
            presets = presets,
            streak = Streak.current(records, today),
            stepsToday = healthDays.firstOrNull { it.date == today }?.steps ?: 0,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUi())

    val selected: StateFlow<Int> = selectedDay

    /** Titles awarded by startup reconciliation, still owed their celebration. */
    val pendingCelebrations: StateFlow<List<TitleDef>> = repo.pendingCelebrations

    fun celebrationsSeen() = repo.clearPendingCelebrations()

    fun selectDay(day: Int) {
        selectedDay.value = day
    }

    fun beginPreset(presetId: Long, onStarted: (Long) -> Unit) {
        viewModelScope.launch { onStarted(repo.startSessionFromPreset(presetId)) }
    }
}

private val DAY_LABELS = linkedMapOf(1 to "MON", 2 to "TUE", 3 to "WED", 4 to "THU", 5 to "FRI", 6 to "SAT", 7 to "SUN")

@Composable
fun DashboardScreen(
    onStartSession: (Long) -> Unit,
    onOpenPresets: () -> Unit,
    onOpenCodex: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: DashboardViewModel =
        viewModel(factory = viewModelFactory { initializer { DashboardViewModel(monarchRepository()) } }),
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
    Column(
        Modifier
            .fillMaxSize()
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
                        colorFilter = ColorFilter.tint(MonarchColors.SystemGreen),
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
                            (profile?.name ?: "Hunter").uppercase(),
                            style = MaterialTheme.typography.headlineMedium,
                            fontFamily = ChakraPetch,
                            fontWeight = FontWeight.Bold,
                            // headlineMedium inherits a near-black onSurface here
                            color = MonarchColors.Ink,
                            letterSpacing = 1.sp,
                            maxLines = 1,
                        )
                        val worn = profile?.currentTitleId?.let { Titles.byId(it)?.name }
                        Text(
                            "${Rank.forLevel(progress.level)} · ${ArmyClass.forLevel(progress.level).title}",
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = ChakraPetch,
                            color = MonarchColors.SystemGreen,
                            maxLines = 1,
                        )
                        Text(
                            worn?.uppercase() ?: if (ui.unlockedCount > 0) "TAP TO WEAR A TITLE"
                            else "NO TITLE EARNED YET",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = ChakraPetch,
                            color = if (worn != null) MonarchColors.SovereignGold else MonarchColors.InkMuted,
                            letterSpacing = MonarchTracking.InlineLabel,
                            maxLines = 1,
                        )
                    }
                    // The System gear: settings left the bottom nav, so
                    // this fixed-size tap target rides at the end of the
                    // identity strip — the weighted name column absorbs it,
                    // so the player card itself never moves.
                    Icon(
                        Icons.Outlined.Settings,
                        contentDescription = "System",
                        tint = MonarchColors.InkMuted,
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { onOpenSettings() }
                            .padding(2.dp),
                    )
                    // Rank and crest as one insignia: the worn crest supplies
                    // the plate this level sits on.
                    HunterSigil(level = progress.level, frameId = equippedFrame)
                }
                Spacer(Modifier.height(10.dp))
                XpBar(progress.intoLevel, progress.needed)
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        "LV ${progress.level} → ${progress.level + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = ChakraPetch,
                        color = MonarchColors.InkMuted,
                        letterSpacing = MonarchTracking.InlineLabel,
                    )
                    Text(
                        "${progress.needed - progress.intoLevel} XP TO GO",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = ChakraPetch,
                        color = MonarchColors.InkMuted,
                        letterSpacing = MonarchTracking.InlineLabel,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Gauge cluster: one radial dial carries the day's steps, the column
        // beside it carries the counters — different shapes, one panel.
        AnimatedVisibility(shown, enter = fadeIn(tween(300, delayMillis = 90))) {
            SystemWindow(Modifier.fillMaxWidth()) {
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
                            accent = if (ui.streak > 0) MonarchColors.SovereignGold else MonarchColors.InkMuted,
                            fraction = (ui.streak / 7f).coerceIn(0f, 1f),
                        )
                        GaugeStat(
                            label = "TITLES",
                            value = "${ui.unlockedCount}/${Titles.ALL.size}",
                            accent = MonarchColors.EmeraldBright,
                            fraction = (ui.unlockedCount.toFloat() / Titles.ALL.size).coerceIn(0f, 1f),
                        )
                        GaugeStat(
                            label = "PROGRAMS",
                            value = "${ui.presets.size}",
                            accent = MonarchColors.SystemGreen,
                            fraction = (ui.presets.size / 7f).coerceIn(0f, 1f),
                        )
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
                                isSelected -> MonarchColors.Ink
                                isToday -> MonarchColors.SovereignGold
                                preset != null -> MonarchColors.SystemGreen
                                else -> MonarchColors.InkMuted
                            },
                        )
                        Spacer(Modifier.height(5.dp))
                        Box(
                            Modifier
                                .width(if (isSelected) 22.dp else 14.dp)
                                .height(if (isSelected) 3.dp else 2.dp)
                                .background(
                                    when {
                                        isToday -> MonarchColors.SovereignGold
                                        preset != null -> MonarchColors.SystemGreen
                                        else -> MonarchColors.Rune
                                    },
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
        val questDoneToday = selectedPreset != null && isTodaySelected && ui.recent.any {
            it.presetId == selectedPreset.id && (it.completedAtMs ?: 0L) >= todayStart
        }

        // The quest panel takes every remaining pixel: the exercise list grows
        // into the slack instead of leaving dead space above the nav bar.
        SystemWindow(
            Modifier.fillMaxWidth().weight(1f),
            accent = when {
                questDoneToday -> MonarchColors.SovereignGold
                isTodaySelected -> MonarchColors.SystemGreen
                else -> MonarchColors.Rune
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
                    color = if (isTodaySelected) MonarchColors.SovereignGold else MonarchColors.InkMuted,
                    letterSpacing = MonarchTracking.SectionHeader,
                )
                if (selectedPreset != null) {
                    Text(
                        "${selectedPreset.entries.size} MOVES · ${selectedPreset.entries.sumOf { it.targetSets }} SETS",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = ChakraPetch,
                        color = MonarchColors.InkMuted,
                        letterSpacing = MonarchTracking.InlineLabel,
                    )
                }
            }
            if (selectedPreset != null) {
                Text(
                    selectedPreset.name.uppercase(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontFamily = ChakraPetch,
                    fontWeight = FontWeight.Bold,
                    color = MonarchColors.EmeraldBright,
                    letterSpacing = 1.sp,
                )
                if (selectedPreset.note.isNotBlank()) {
                    Text(
                        selectedPreset.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MonarchColors.InkMuted,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.height(10.dp))
                if (questDoneToday) {
                    // The done state replaces the manifest entirely: the
                    // SpaceEvenly rows Column is weighted, so rendering both
                    // made six exercises collide with the complete text.
                    Column(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "QUEST COMPLETE",
                            style = MaterialTheme.typography.headlineSmall,
                            fontFamily = ChakraPetch,
                            fontWeight = FontWeight.Bold,
                            color = MonarchColors.SovereignGold,
                            letterSpacing = 1.sp,
                        )
                        Text(
                            "The System is satisfied. A new quest rises tomorrow.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MonarchColors.InkMuted,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            "6 MOVES · 22 SETS · DONE",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = ChakraPetch,
                            letterSpacing = MonarchTracking.InlineLabel,
                            color = MonarchColors.InkMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                } else {
                    Column(
                        // rows spread into the panel's slack instead of leaving a
                        // void above the button, with hairlines making it a manifest
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        selectedPreset.entries.forEachIndexed { index, entry ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Box(Modifier.size(4.dp).background(MonarchColors.SystemGreen))
                                Text(
                                    entry.exerciseName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MonarchColors.Ink,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                )
                                Text(
                                    "${entry.targetSets}\u00D7${entry.targetReps}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontFamily = ChakraPetch,
                                    color = MonarchColors.SystemGreen,
                                )
                            }
                            if (index != selectedPreset.entries.lastIndex) {
                                Box(Modifier.fillMaxWidth().height(1.dp).background(MonarchColors.Rune))
                            }
                        }
                    }
                    MonarchButton(
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
                    color = MonarchColors.SystemGreen,
                    letterSpacing = 1.sp,
                )
                Text(
                    "Nothing scheduled. Recover, or pick another day above.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MonarchColors.InkMuted,
                )
                // The rest-day art was drawn for this panel and never wired in,
                // leaving the quest card's slack as dead space.
                Spacer(Modifier.weight(1f))
                Image(
                    painter = painterResource(R.drawable.art_empty_quests),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
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
                        enabled = last.completedAtMs == null,
                    ) { onStartSession(last.id) }
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    if (last.completedAtMs == null) "RESUME · ${last.label}" else "LAST · ${last.label}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = ChakraPetch,
                    color = if (last.completedAtMs == null) MonarchColors.SovereignGold
                    else MonarchColors.InkMuted,
                    letterSpacing = MonarchTracking.InlineLabel,
                    maxLines = 1,
                )
                Text(
                    formatDate(last.completedAtMs ?: last.startedAtMs, "MMM d"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MonarchColors.InkMuted,
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
                    tint = MonarchColors.InkMuted,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    "ROUTINE, PRESETS & FULL LOG",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = ChakraPetch,
                    color = MonarchColors.InkMuted,
                    letterSpacing = MonarchTracking.SectionHeader,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
    }

    // Titles reconciled at startup (health data, imports) have no session to
    // celebrate in, so the moment is paid out here on the first screen.
    val owed by viewModel.pendingCelebrations.collectAsStateWithLifecycle()
    AchievementOverlay(
        items = owed.map { def ->
            Achievement(
                banner = "TITLE EARNED",
                tagline = "DEED CLAIMED",
                name = def.name,
                subtitle = def.description.uppercase(),
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
    val ring = if (hit) MonarchColors.SovereignGold else MonarchColors.EmeraldBright
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 7.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = MonarchColors.Rune,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            if (fraction > 0f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(MonarchColors.SystemGreen, ring, MonarchColors.SystemGreen),
                    ),
                    startAngle = 135f,
                    sweepAngle = 270f * fraction,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "%,d".format(steps),
                style = MaterialTheme.typography.titleLarge,
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.Bold,
                color = if (hit) MonarchColors.SovereignGold else MonarchColors.Ink,
            )
            Text(
                "/ ${"%,d".format(goal)}",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = ChakraPetch,
                color = MonarchColors.InkMuted,
            )
            Text(
                "STEPS",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = ChakraPetch,
                color = MonarchColors.InkMuted,
                letterSpacing = MonarchTracking.InlineLabel,
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
                color = MonarchColors.InkMuted,
                letterSpacing = MonarchTracking.InlineLabel,
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
        Box(Modifier.fillMaxWidth().height(2.dp).background(MonarchColors.Rune)) {
            if (fraction > 0f) {
                Box(Modifier.fillMaxWidth(fraction).height(2.dp).background(accent))
            }
        }
    }
}

/** Flat progress track for the step goal — same gradient language as the XP bar. */
@Composable
private fun GoalTrack(fraction: Float) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .background(Color(0xFF101A14))
            .border(1.dp, MonarchColors.Rune),
    ) {
        if (fraction > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(6.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(MonarchColors.SystemGreen, MonarchColors.EmeraldBright),
                        ),
                    ),
            )
        }
    }
}


/** Class emblem art escalates with the ladder: soldier -> knight -> commander -> monarch. */
private fun classEmblem(level: Int): Int = when {
    level >= 70 -> R.drawable.ic_rank_monarch
    level >= 40 -> R.drawable.ic_rank_commander
    level >= 15 -> R.drawable.ic_rank_knight
    else -> R.drawable.ic_rank_soldier
}
@Composable
private fun HeroStat(value: String, label: String, valueColor: Color = MonarchColors.Ink) {
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
            color = MonarchColors.InkMuted,
            letterSpacing = MonarchTracking.InlineLabel,
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
            .border(1.dp, if (isToday) MonarchColors.SystemGreen else MonarchColors.Rune, MaterialTheme.shapes.extraSmall),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            DAY_LABELS[day] ?: "",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            color = if (isToday) MonarchColors.SystemGreen else MonarchColors.InkMuted,
        )
    }
}
