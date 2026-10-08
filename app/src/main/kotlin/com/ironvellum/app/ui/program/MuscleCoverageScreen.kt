package com.ironvellum.app.ui.program

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ironvellum.app.data.Repository
import com.ironvellum.app.domain.Muscle
import com.ironvellum.app.domain.MuscleArea
import com.ironvellum.app.domain.MuscleMap
import com.ironvellum.app.domain.PlannedEntry
import com.ironvellum.app.domain.PlannedPreset
import com.ironvellum.app.domain.ProgramRules
import com.ironvellum.app.domain.TrainingFocus
import com.ironvellum.app.domain.TrainingMode
import com.ironvellum.app.domain.VolumeLevel
import com.ironvellum.app.ui.components.InkDivider
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.components.InkTabbedPager
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.components.ListRow
import com.ironvellum.app.ui.components.ListRowHeight
import com.ironvellum.app.ui.components.NavChip
import com.ironvellum.app.ui.components.PushedHeader
import com.ironvellum.app.ui.components.SectionHeader
import com.ironvellum.app.ui.ironvellumRepository
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.DotShape
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.IronvellumTracking
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Weekly muscle coverage: the routine-level heat map. The cycle reads the
 * scheduled presets, the last 7 days read what was actually logged, and both go
 * through [ProgramRules.weeklyVolume] so the two views count identically.
 */

enum class CoverageView(val label: String) {
    CYCLE("Cycle"),
    LOGGED("Last 7 days"),
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

/** Sets whose exercise has no MuscleMap profile, so the heat map cannot count them. */
internal fun unattributedSets(presets: List<PlannedPreset>): Int =
    presets.sumOf { p -> p.entries.sumOf { e -> if (MuscleMap.profile(e.exerciseName) == null) e.sets else 0 } }

/**
 * THE count of muscles [presets] leave short against [goal]. The saved Cycle page's dock and the Forge
 * row's count both read it, so a proposal and the page that opens from it can never disagree.
 */
internal fun shortCount(presets: List<PlannedPreset>, goal: CoverageGoal): Int =
    coverageGaps(ProgramRules.weeklyVolume(presets), goal).size

/** "3 muscles short" or "Every muscle covered": the Forge row's words, and the preview page's. */
internal fun shortHeadline(short: Int): String =
    if (short == 0) "Every muscle covered" else "$short ${if (short == 1) "muscle" else "muscles"} short"

internal fun coverageGoal(tier: VolumeLevel, focus: TrainingFocus, priorities: Set<MuscleArea>): CoverageGoal =
    CoverageGoal(tier, focus, priorities.flatMap { it.muscles }.toSet())

/** What the Cycle page reads for a cycle that is not saved yet: the builder's proposal, as the caller's data. */
internal fun previewCoverage(
    presets: List<PlannedPreset>,
    tier: VolumeLevel,
    focus: TrainingFocus,
    priorities: Set<MuscleArea>,
): CoverageUi = CoverageUi(
    plannedPresets = presets,
    unattributedPlannedSets = unattributedSets(presets),
    tier = tier,
    focus = focus,
    priorities = priorities.flatMap { it.muscles }.toSet(),
    hasAnyPreset = presets.isNotEmpty(),
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

        CoverageUi(
            plannedPresets = plannedPresets,
            loggedPresets = loggedPresets,
            unattributedLoggedSets = unattributedSets(loggedPresets),
            unattributedPlannedSets = unattributedSets(plannedPresets),
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
    // One lit muscle per page, kept when the lifter swipes away and back: the cycle and the last 7 days are
    // two different weeks, so each remembers what it was showing. Held here because the pager drops pages
    // it is not near.
    val lit = CoverageView.entries.associateWith { view ->
        rememberSaveable(key = "lit-${view.name}") { mutableStateOf<Muscle?>(null) }
    }

    // The dock reads the page that is open, so it is worked out here and not inside the pager.
    val view = CoverageView.entries[pager.currentPage]
    val goal = CoverageGoal(ui.tier, ui.focus, ui.priorities)
    val presets = if (view == CoverageView.CYCLE) ui.plannedPresets else ui.loggedPresets
    val short = remember(presets, goal) { shortCount(presets, goal) }
    val empty = if (view == CoverageView.CYCLE) !ui.hasAnyPreset else !ui.hasLoggedWeek

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(20.dp))
            PushedHeader("Weekly coverage", onBack)
            Spacer(Modifier.height(12.dp))
        }
        // The switch comes first: drawn after the empty check, an empty last-7-days view hid it and the
        // lifter could not get back to the cycle.
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
            )
        }
        if (!empty && short > 0) {
            CoverageDock(
                short = short,
                scope = if (view == CoverageView.CYCLE) "Your cycle" else "Last 7 days",
                onForge = onGenerateSession,
            )
        }
    }
}

/**
 * Coverage of the cycle the builder is proposing, before it is saved: the Cycle page's own content
 * ([CoveragePage]) fed the proposal instead of the saved rites. No Forge dock and no Last 7 days tab:
 * the lifter is already forging.
 */
@Composable
internal fun CoveragePreviewScreen(
    presets: List<PlannedPreset>,
    tier: VolumeLevel,
    focus: TrainingFocus,
    priorities: Set<MuscleArea>,
    onBack: () -> Unit,
) {
    val ui = remember(presets, tier, focus, priorities) { previewCoverage(presets, tier, focus, priorities) }
    val short = remember(ui) { shortCount(ui.plannedPresets, CoverageGoal(ui.tier, ui.focus, ui.priorities)) }
    val lit = rememberSaveable(key = "lit-preview") { mutableStateOf<Muscle?>(null) }
    var openExercise by rememberSaveable { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(20.dp))
            PushedHeader("Coverage of this cycle", onBack)
            Spacer(Modifier.height(8.dp))
            Text(
                shortHeadline(short),
                style = MaterialTheme.typography.titleMedium,
                color = IronvellumColors.Ink,
            )
            Spacer(Modifier.height(8.dp))
        }
        Box(Modifier.weight(1f)) {
            CoveragePage(
                view = CoverageView.CYCLE,
                ui = ui,
                lit = lit,
                openExercise = openExercise,
                onOpenExercise = { openExercise = it },
            )
        }
    }
}

/** What the page is short of, and the way to forge a rite that fills it. */
@Composable
private fun CoverageDock(short: Int, scope: String, onForge: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(IronvellumColors.VaultHigh)
            .drawBehind { drawRect(IronvellumColors.Rune, size = Size(size.width, 1.dp.toPx())) }
            .navigationBarsPadding()
            .padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                shortHeadline(short),
                style = MaterialTheme.typography.titleMedium,
                color = IronvellumColors.Ink,
            )
            Text(scope, style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted)
        }
        IronvellumButton(label = "Forge a rite", onClick = onForge)
    }
}

/** One view's page: its own scroll and numbers, both read through the same rules. */
@Composable
internal fun CoveragePage(
    view: CoverageView,
    ui: CoverageUi,
    lit: MutableState<Muscle?>,
    openExercise: String?,
    onOpenExercise: (String?) -> Unit,
) {
    val presets = if (view == CoverageView.CYCLE) ui.plannedPresets else ui.loggedPresets
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
    val empty = if (view == CoverageView.CYCLE) !ui.hasAnyPreset else !ui.hasLoggedWeek
    val unattributed = if (view == CoverageView.CYCLE) ui.unattributedPlannedSets else ui.unattributedLoggedSets
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    var inRangeOpen by rememberSaveable(view.name) { mutableStateOf(false) }
    var byExerciseOpen by rememberSaveable(view.name) { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 16.dp),
    ) {
        if (empty) {
            Text(
                if (view == CoverageView.CYCLE) {
                    "No rites are written yet. Build a cycle and it will be mapped here."
                } else {
                    "The Chronicle is blank for the last 7 days. Seal a trial and it is mapped here."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = IronvellumColors.InkMuted,
                modifier = Modifier.padding(top = 8.dp),
            )
            Spacer(Modifier.height(20.dp))
            return@Column
        }

        BodyHeatMap(
            volume = volume,
            goal = goal,
            modifier = Modifier.fillMaxWidth(),
            figureHeight = 260.dp,
            selection = lit,
            lineFor = { coverageMuscleLine(it, volume[it] ?: 0.0, goal) },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            targetCaption(ui.tier, ui.focus, target),
            style = MaterialTheme.typography.bodySmall,
            color = IronvellumColors.InkMuted,
        )
        if (unattributed > 0) {
            Text(
                "$unattributed sets not counted: no muscle data for those exercises.",
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
            )
        }
        Spacer(Modifier.height(12.dp))

        // The one place a lit muscle narrows anything: the list gives way to the rites and exercises that
        // train it, and clearing brings it back.
        val litMuscle = lit.value
        if (litMuscle != null) {
            MuscleFilter(
                muscle = litMuscle,
                rites = remember(presets, litMuscle) { ritesTraining(presets, litMuscle) },
                byRite = view == CoverageView.CYCLE,
                onClear = { lit.value = null },
            )
        } else {
            // A row does what tapping the muscle on the figure does, so the page scrolls up to show it lit.
            val pick = { muscle: Muscle ->
                lit.value = muscle
                scope.launch { scroll.animateScrollTo(0) }
                Unit
            }
            // The muscles that need a look come first, least trained leading; the ones in range fold away.
            val ordered = remember(volume, goal) { JUDGED.sortedWith(shortFirst(volume, goal)) }
            val (needing, inRange) = ordered.partition {
                levelOf(it, volume[it] ?: 0.0, goal) != CoverageLevel.IN_RANGE
            }
            InkPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp)) {
                needing.forEachIndexed { index, muscle ->
                    if (index > 0) InkDivider()
                    MuscleRow(muscle, volume[muscle] ?: 0.0, goal) { pick(muscle) }
                }
                if (inRange.isNotEmpty()) {
                    if (needing.isNotEmpty()) InkDivider()
                    ListRow(
                        label = "${inRange.size} ${if (inRange.size == 1) "muscle" else "muscles"} in range",
                        value = if (inRangeOpen) "Hide" else null,
                        onClickLabel = if (inRangeOpen) "Hide the muscles in range" else "Show the muscles in range",
                        onClick = { inRangeOpen = !inRangeOpen },
                    )
                    if (inRangeOpen) {
                        inRange.forEach { muscle ->
                            InkDivider()
                            MuscleRow(muscle, volume[muscle] ?: 0.0, goal) { pick(muscle) }
                        }
                    }
                }
            }

            if (exercisesInView.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                InkDivider()
                ListRow(
                    label = "By exercise",
                    value = if (byExerciseOpen) "Hide" else null,
                    onClickLabel = if (byExerciseOpen) "Hide the exercises" else "Show the muscles each exercise works",
                    onClick = { byExerciseOpen = !byExerciseOpen },
                )
                InkDivider()
                if (byExerciseOpen) {
                    Spacer(Modifier.height(8.dp))
                    InkPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp)) {
                        exercisesInView.forEachIndexed { index, (name, modifiers, shares) ->
                            if (index > 0) InkDivider()
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

/**
 * One muscle's week as a row: a dot at the verdict's strength, its name, and its sets against its range
 * ("6 of 12-18 sets", a helper's open floor "2 of 3+ sets") with the verdict. Tapped, it lights the muscle.
 */
@Composable
private fun MuscleRow(
    muscle: Muscle,
    sets: Double,
    goal: CoverageGoal,
    onPick: () -> Unit,
) {
    val level = levelOf(muscle, sets, goal)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = ListRowHeight)
            .clickable(onClickLabel = "Show what trains ${muscle.label}", role = Role.Button, onClick = onPick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(10.dp).clip(DotShape).background(verdictDot(level)))
        Text(
            muscle.label,
            style = MaterialTheme.typography.bodyMedium,
            color = IronvellumColors.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = IronvellumColors.Ink)) { append(trimSets(sets)) }
                append(" of ${targetLabel(rangeFor(muscle, goal))} sets · ${verdictLabel(level)}")
            },
            style = MaterialTheme.typography.labelMedium,
            color = IronvellumColors.InkMuted,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/** The dot's strength steps up with the verdict, the same emerald the figure is drawn in; over is plain Ink. */
private fun verdictDot(level: CoverageLevel): Color = when (level) {
    CoverageLevel.NONE -> IronvellumColors.Emerald.copy(alpha = 0.1f)
    CoverageLevel.UNDER -> IronvellumColors.Emerald.copy(alpha = 0.28f)
    CoverageLevel.LIGHT -> IronvellumColors.Emerald.copy(alpha = 0.5f)
    CoverageLevel.IN_RANGE -> IronvellumColors.Emerald.copy(alpha = 0.9f)
    CoverageLevel.OVER -> IronvellumColors.Ink
}

private fun verdictLabel(level: CoverageLevel): String = when (level) {
    CoverageLevel.NONE -> "Untrained"
    CoverageLevel.UNDER -> "Under"
    CoverageLevel.LIGHT -> "Light"
    CoverageLevel.IN_RANGE -> "In range"
    CoverageLevel.OVER -> "Over"
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
            color = if (shareLevel(credit.share) == ShareLevel.MAIN) IronvellumColors.Ink else IronvellumColors.InkMuted,
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
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = ListRowHeight)
                .clickable(
                    onClickLabel = if (open) "Hide muscles for $name" else "Show muscles for $name",
                    role = Role.Button,
                    onClick = onToggle,
                )
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                        style = MaterialTheme.typography.bodySmall,
                        color = IronvellumColors.InkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                musclesAt(shares, ShareLevel.MAIN).joinToString(" · ") { it.label },
                style = MaterialTheme.typography.labelMedium,
                color = IronvellumColors.InkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f, fill = false),
            )
            Icon(
                if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = IronvellumColors.InkMuted,
                modifier = Modifier.size(20.dp),
            )
        }
        if (open) {
            ExerciseMuscles(shares, name, Modifier.fillMaxWidth().padding(bottom = 10.dp))
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
