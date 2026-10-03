package com.ironvellum.app.ui.program

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import com.ironvellum.app.ui.components.InkTabs
import com.ironvellum.app.ui.theme.inkBorder
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.ironvellum.app.ui.components.NavChip
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ironvellum.app.data.Repository
import com.ironvellum.app.domain.VolumeLevel
import com.ironvellum.app.domain.MuscleMap
import com.ironvellum.app.domain.PlannedEntry
import com.ironvellum.app.domain.PlannedPreset
import com.ironvellum.app.domain.Muscle
import com.ironvellum.app.domain.ProgramRules
import com.ironvellum.app.domain.TrainingFocus
import com.ironvellum.app.domain.TrainingMode
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.components.InkTabbedPager
import androidx.compose.foundation.pager.rememberPagerState
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.components.SectionHeader
import com.ironvellum.app.ui.ironvellumRepository
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.IronvellumTracking
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/**
 * Weekly muscle coverage: the routine-level heat map. PLANNED reads the
 * scheduled presets, LAST 7 DAYS reads what was actually logged, and both go
 * through [ProgramRules.weeklyVolume] so the two views count identically.
 */

enum class CoverageView(val label: String) {
    PLANNED("PLANNED"),
    LOGGED("LAST 7 DAYS"),
}

data class CoverageUi(
    val plannedPresets: List<PlannedPreset> = emptyList(),
    val loggedPresets: List<PlannedPreset> = emptyList(),
    /** Done sets this week whose movement has no MuscleMap profile. */
    val unattributedLoggedSets: Int = 0,
    /** Preset sets with no profile (planned view honesty count). */
    val unattributedPlannedSets: Int = 0,
    val tier: VolumeLevel = VolumeLevel.LOW,
    val focus: TrainingFocus = TrainingFocus.MUSCLE,
    /** Muscles of the areas she prioritised, whose ceiling rises (ProgramRules.judgedRange). */
    val priorities: Set<Muscle> = emptySet(),
    val hasAnyPreset: Boolean = false,
    val hasLoggedWeek: Boolean = false,
)

class MuscleCoverageViewModel(
    private val repo: Repository,
    appContext: android.content.Context,
) : ViewModel() {

    /** What she last told the generator; when present it outranks the
     *  history/profile guesses, which said "beginner, strength" minutes
     *  after she accepted an INTERMEDIATE / MUSCLE week. */
    private val savedAnswers = com.ironvellum.app.data.ProgramAnswersStore.get(appContext)

    /** Tier is derived from history, so it is a one-shot read, not a flow. */
    private val tierFlow = flow {
        emit(
            savedAnswers?.volume
                ?: ProgramRules.suggestVolume(repo.firstSessionEpochDay(), LocalDate.now().toEpochDay()),
        )
    }

    val ui: StateFlow<CoverageUi> = combine(
        repo.observePresets(),
        repo.observeHistory(),
        repo.observeProfile(),
        tierFlow,
    ) { presets, history, profile, tier ->
        // Scheduled days are the routine; with none scheduled, the whole
        // board is the routine.
        val scheduled = presets.filter { it.scheduledDay != null }.ifEmpty { presets }
        val plannedPresets = scheduled.map { it.toPlanned() }

        val cutoff = Instant.now().minus(java.time.Duration.ofDays(7)).toEpochMilli()
        val recent = history.filter { (session, _) -> session.startedAtMs >= cutoff }
        // Done sets grouped per movement and modifiers, then read through
        // the SAME weeklyVolume rule as the plan, so the two views can be
        // compared - a feet-elevated push-up credits the upper chest here too.
        val loggedSets = recent.flatMap { (_, sets) -> sets.filter { it.done } }
        val byExercise = loggedSets.groupBy { it.exerciseName to it.modifiers }
        val loggedPresets = if (byExercise.isEmpty()) {
            emptyList()
        } else {
            listOf(
                PlannedPreset(
                    name = "Last 7 days",
                    note = "",
                    scheduledDay = null,
                    entries = byExercise.map { (key, sets) ->
                        PlannedEntry(
                            exerciseName = key.first,
                            sets = sets.size,
                            // Coverage only counts sets; the average reps
                            // here is display filler for the data class.
                            reps = if (sets.isEmpty()) 10 else sets.sumOf { it.reps } / sets.size,
                            targetWeightKg = null,
                            modifiers = key.second,
                        )
                    },
                ),
            )
        }

        fun unattributed(presets: List<PlannedPreset>): Int =
            presets.sumOf { p -> p.entries.sumOf { e -> if (MuscleMap.profile(e.exerciseName) == null) e.sets else 0 } }

        CoverageUi(
            plannedPresets = plannedPresets,
            loggedPresets = loggedPresets,
            unattributedLoggedSets = unattributed(loggedPresets),
            unattributedPlannedSets = unattributed(plannedPresets),
            tier = tier,
            focus = savedAnswers?.focus ?: when (profile?.trainingMode) {
                TrainingMode.STRENGTH -> TrainingFocus.STRENGTH
                else -> TrainingFocus.MUSCLE
            },
            priorities = savedAnswers?.priorities.orEmpty().flatMap { it.muscles }.toSet(),
            hasAnyPreset = presets.isNotEmpty(),
            hasLoggedWeek = loggedSets.isNotEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CoverageUi())
}

@Composable
fun MuscleCoverageScreen(
    onBack: () -> Unit,
    onGenerateSession: () -> Unit,
    viewModel: MuscleCoverageViewModel =
        viewModel(factory = viewModelFactory {
            val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
            initializer { MuscleCoverageViewModel(ironvellumRepository(), appContext) }
        }),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val pager = rememberPagerState { CoverageView.entries.size }
    var openExercise by rememberSaveable { mutableStateOf<String?>(null) }
    // One lit muscle per page, kept when the lifter swipes away and back: PLANNED and LAST 7 DAYS are two
    // different weeks, so each remembers what it was showing. Held here because the pager drops pages it
    // is not near.
    val lit = CoverageView.entries.associateWith { view ->
        rememberSaveable(key = "lit-${view.name}") { mutableStateOf<Muscle?>(null) }
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "WEEKLY COVERAGE",
                    style = MaterialTheme.typography.labelLarge,
                    color = IronvellumColors.SystemGreen,
                    letterSpacing = IronvellumTracking.ScreenTitle,
                )
                NavChip("BACK", Icons.AutoMirrored.Filled.ArrowBack, onClick = { onBack() })
            }
            Spacer(Modifier.height(12.dp))
        }
        // The switch comes first: drawn after the empty check, an empty LAST
        // 7 DAYS view hid it and the lifter could not get back to PLANNED.
        InkTabbedPager(
            labels = CoverageView.entries.map { it.label },
            state = pager,
            modifier = Modifier.weight(1f),
            tabsModifier = Modifier.padding(horizontal = 16.dp),
            tabsGap = 12.dp,
        ) { page ->
            CoveragePage(
                view = CoverageView.entries[page],
                ui = ui,
                lit = lit.getValue(CoverageView.entries[page]),
                openExercise = openExercise,
                onOpenExercise = { openExercise = it },
                onGenerateSession = onGenerateSession,
            )
        }
    }
}

/** One view's page: its own scroll and numbers, both read through the same rules. */
@Composable
private fun CoveragePage(
    view: CoverageView,
    ui: CoverageUi,
    lit: MutableState<Muscle?>,
    openExercise: String?,
    onOpenExercise: (String?) -> Unit,
    onGenerateSession: () -> Unit,
) {
    val presets = if (view == CoverageView.PLANNED) ui.plannedPresets else ui.loggedPresets
    val volume = remember(presets) { ProgramRules.weeklyVolume(presets) }
    // Each exercise once, in routine order, keyed with its modifiers because
    // a deficit push-up works the chest differently from a flat one.
    val exercisesInView = remember(presets) {
        presets.flatMap { it.entries }
            .map { it.exerciseName to it.modifiers }
            .distinct()
            .mapNotNull { (name, modifiers) -> MuscleMap.profile(name, modifiers)?.let { Triple(name, modifiers, it.muscles) } }
    }
    val goal = CoverageGoal(ui.tier, ui.focus, ui.priorities)
    val target = goal.target
    val underCount = coverageGaps(volume, goal).size
    val empty = if (view == CoverageView.PLANNED) !ui.hasAnyPreset else !ui.hasLoggedWeek
    val unattributed = if (view == CoverageView.PLANNED) ui.unattributedPlannedSets else ui.unattributedLoggedSets
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 16.dp),
    ) {
        if (empty) {
            InkPanel(Modifier.fillMaxWidth()) {
                Text(
                    if (view == CoverageView.PLANNED) {
                        "No rites are written yet. Build a cycle and it will be mapped here."
                    } else {
                        "The Chronicle is blank for the last 7 days. Seal a trial and it is mapped here."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(20.dp))
            return@Column
        }

        InkPanel(Modifier.fillMaxWidth()) {
            BodyHeatMap(
                volume = volume,
                goal = goal,
                modifier = Modifier.fillMaxWidth(),
                figureHeight = 380.dp,
                selection = lit,
                lineFor = { coverageMuscleLine(it, volume[it] ?: 0.0, goal) },
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            targetCaption(ui.tier, ui.focus, target),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (unattributed > 0) {
            Text(
                "$unattributed sets not counted: no muscle data for those exercises.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))

        // The one place a lit muscle narrows anything: the lists give way to the rites and exercises that
        // train it, and clearing brings them back.
        val litMuscle = lit.value
        if (litMuscle != null) {
            MuscleFilter(
                muscle = litMuscle,
                rites = remember(presets, litMuscle) { ritesTraining(presets, litMuscle) },
                byRite = view == CoverageView.PLANNED,
                onClear = { lit.value = null },
            )
        } else {
            // One list at a time, so the page is a figure and a screenful, not three stacked lists. A swipe
            // here turns the list; past its last list it hands on to the PLANNED / LAST 7 DAYS pager.
            val lists = rememberPagerState { 2 }
            InkTabs(
                listOf("MUSCLES", "EXERCISES"),
                selectedIndex = lists.currentPage,
                onSelect = { scope.launch { lists.animateScrollToPage(it) } },
                indicatorPosition = { lists.currentPage + lists.currentPageOffsetFraction },
            )
            Spacer(Modifier.height(12.dp))
            HorizontalPager(lists, verticalAlignment = Alignment.Top) { list ->
            Column {
            if (list == 0) {
                // A tile does what tapping the muscle on the figure does, so the page scrolls up to show it lit.
                val pick = { muscle: Muscle ->
                    lit.value = muscle
                    scope.launch { scroll.animateScrollTo(0) }
                    Unit
                }
                MuscleTiles(ProgramRules.TRACKED.sortedWith(shortFirst(volume, goal)), volume, goal, pick)
                // Judged against a floor, not the range: see ProgramRules.HELPERS.
                Text(
                    "HELPERS",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = ChakraPetch,
                    color = IronvellumColors.InkMuted,
                    letterSpacing = IronvellumTracking.InlineLabel,
                    modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
                )
                Text(
                    "Mostly trained by your other exercises. Under " +
                        "${trimSets(ProgramRules.HELPER_FLOOR_SETS)} sets a week reads light; none at all is a gap.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                MuscleTiles(ProgramRules.HELPERS.sortedWith(shortFirst(volume, goal)), volume, goal, pick)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    exercisesInView.forEach { (name, modifiers, shares) ->
                        val key = "$name|$modifiers"
                        ExerciseRow(
                            name = name,
                            modifiers = modifiers,
                            shares = shares,
                            open = openExercise == key,
                            onToggle = { onOpenExercise(if (openExercise == key) null else key) },
                        )
                    }
                }
            }
            }
            }
        }

        if (underCount > 0) {
            Spacer(Modifier.height(12.dp))
            Text(
                "$underCount ${if (underCount == 1) "muscle" else "muscles"} short this week.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            IronvellumButton(
                label = "Forge a Rite",
                onClick = onGenerateSession,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(20.dp))
    }
}

/** Gaps first - untrained, then under, then light - and the least trained first within each. */
private fun shortFirst(volume: Map<Muscle, Double>, goal: CoverageGoal): Comparator<Muscle> =
    compareBy<Muscle>(
        {
            when (levelOf(it, volume[it] ?: 0.0, goal)) {
                CoverageLevel.NONE -> 0
                CoverageLevel.UNDER -> 1
                CoverageLevel.LIGHT -> 2
                CoverageLevel.OVER -> 3
                CoverageLevel.IN_RANGE -> 4
            }
        },
        { volume[it] ?: 0.0 },
    )

/** [muscles] two to a row. */
@Composable
private fun MuscleTiles(
    muscles: List<Muscle>,
    volume: Map<Muscle, Double>,
    goal: CoverageGoal,
    onPick: (Muscle) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        muscles.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { MuscleTile(it, volume[it] ?: 0.0, goal, { onPick(it) }, Modifier.weight(1f).fillMaxHeight()) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/**
 * One muscle's week as a tile: its name, its sets against its range ("6 / 12–18", a helper's open floor
 * "2 / 3+"), and the verdict, with a bar in the verdict's colour down its edge. Tapped, it lights the muscle.
 */
@Composable
private fun MuscleTile(
    muscle: Muscle,
    sets: Double,
    goal: CoverageGoal,
    onPick: () -> Unit,
    modifier: Modifier,
) {
    val level = levelOf(muscle, sets, goal)
    val colour = verdictTextColour(level)
    val shape = MaterialTheme.shapes.small
    Row(
        modifier
            .heightIn(min = 56.dp)
            .clip(shape)
            .background(IronvellumColors.VaultHigh)
            .inkBorder(IronvellumColors.Bracket, shape, 1.dp)
            .clickable(onClickLabel = "Show what trains ${muscle.label}", role = Role.Button, onClick = onPick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(colour))
        Column(Modifier.weight(1f).padding(start = 10.dp, top = 8.dp, bottom = 8.dp)) {
            Text(
                muscle.label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = ChakraPetch,
                color = IronvellumColors.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${trimSets(sets)} / ${targetLabel(rangeFor(muscle, goal))}",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = ChakraPetch,
                color = IronvellumColors.InkMuted,
            )
            Text(
                verdictLabel(level),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = ChakraPetch,
                color = colour,
                letterSpacing = IronvellumTracking.InlineLabel,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = IronvellumColors.InkMuted,
            modifier = Modifier.padding(end = 6.dp).size(18.dp),
        )
    }
}

private fun verdictLabel(level: CoverageLevel): String = when (level) {
    CoverageLevel.NONE -> "UNTRAINED"
    CoverageLevel.UNDER -> "UNDER"
    CoverageLevel.LIGHT -> "LIGHT"
    CoverageLevel.IN_RANGE -> "IN RANGE"
    CoverageLevel.OVER -> "OVER"
}

/** One exercise's credit to a muscle: its name, MAIN or ASSIST, and sets times share. */
@Composable
private fun CreditRow(credit: ProgramRules.MuscleCredit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                credit.exerciseName,
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (credit.modifiers.isNotBlank()) {
                Text(
                    credit.modifiers.split(",").joinToString(" · ") { it.trim() },
                    style = MaterialTheme.typography.labelSmall,
                    color = IronvellumColors.InkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            shareLevel(credit.share).label,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            color = if (shareLevel(credit.share) == ShareLevel.MAIN) IronvellumColors.SystemGreen else IronvellumColors.InkMuted,
            letterSpacing = IronvellumTracking.InlineLabel,
        )
        Text(
            "${credit.sets}×${trimSets(credit.share)} = ${trimSets(credit.credited)}",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            color = IronvellumColors.InkMuted,
            textAlign = TextAlign.End,
            modifier = Modifier.width(96.dp),
        )
    }
}

/**
 * What a lit muscle narrows the page to: every rite that trains it with its sets, and under each the
 * exercises behind that number. The LAST 7 DAYS page has no rites (its week is one pooled list), so there
 * [byRite] is false and the exercises stand alone.
 */
@Composable
private fun MuscleFilter(
    muscle: Muscle,
    rites: List<RiteContribution>,
    byRite: Boolean,
    onClear: () -> Unit,
) {
    // The way back sits beside the heading, in reach the moment the page narrows, not past a long list.
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        SectionHeader("Trains ${muscle.label}", Modifier.weight(1f))
        NavChip("CLEAR", Icons.Filled.Close, onClick = onClear, modifier = Modifier.padding(bottom = 4.dp))
    }
    if (rites.isEmpty()) {
        Text(
            "Nothing in this view trains it.",
            style = MaterialTheme.typography.bodySmall,
            color = IronvellumColors.InkMuted,
        )
    } else if (byRite) {
        rites.forEach { rite ->
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    rite.rite,
                    style = MaterialTheme.typography.bodyMedium,
                    color = IronvellumColors.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    riteSetsLabel(rite.credited),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = ChakraPetch,
                    color = IronvellumColors.SystemGreen,
                    letterSpacing = IronvellumTracking.InlineLabel,
                )
            }
            Column(Modifier.fillMaxWidth().padding(start = 12.dp)) {
                rite.exercises.forEach { CreditRow(it) }
            }
        }
    } else {
        rites.flatMap { it.exercises }.sortedByDescending { it.credited }.forEach { CreditRow(it) }
    }
}

/**
 * One exercise in the view and its main muscles; tapped open, the figure and
 * the full MAIN / ASSIST lists from [ExerciseMuscles].
 */
@Composable
private fun ExerciseRow(
    name: String,
    modifiers: String,
    shares: Map<Muscle, Double>,
    open: Boolean,
    onToggle: () -> Unit,
) {
    val shape = MaterialTheme.shapes.small
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(IronvellumColors.VaultHigh)
            .inkBorder(if (open) IronvellumColors.SystemGreen else IronvellumColors.Bracket, shape, 1.dp),
    ) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(
                onClickLabel = if (open) "Hide muscles for $name" else "Show muscles for $name",
                role = Role.Button,
                onClick = onToggle,
            )
            .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                name,
                style = MaterialTheme.typography.bodyMedium,
                color = IronvellumColors.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (modifiers.isNotBlank()) {
                Text(
                    modifiers.split(",").joinToString(" · ") { it.trim() },
                    style = MaterialTheme.typography.labelSmall,
                    color = IronvellumColors.InkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            musclesAt(shares, ShareLevel.MAIN).joinToString(" · ") { it.label },
            style = MaterialTheme.typography.labelSmall,
            color = IronvellumColors.SystemGreen,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
        Icon(
            if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = IronvellumColors.InkMuted,
            modifier = Modifier.padding(start = 4.dp).size(20.dp),
        )
    }
    if (open) {
        ExerciseMuscles(shares, name, Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 10.dp))
    }
    }
}

/**
 * Strength and skill targets are 5-15 at every volume level, so naming the
 * level there says nothing - "lean volume, strength" read as a contradiction.
 */
internal fun targetCaption(volume: VolumeLevel, focus: TrainingFocus, target: ClosedFloatingPointRange<Double>): String {
    val sets = "${trimSets(target.start)}-${trimSets(target.endInclusive)} sets per muscle a week"
    return when (focus) {
        TrainingFocus.STRENGTH -> "Strength target: $sets"
        TrainingFocus.SKILL -> "Technique target: $sets"
        else -> "Muscle target, ${volume.label.lowercase()} volume: $sets"
    }
}

/** One decimal, trimmed of a trailing .0 - "14" and "14.5", never "14.0000". */
internal fun trimSets(value: Double): String {
    val rounded = (value * 10).toLong() / 10.0
    return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
}
