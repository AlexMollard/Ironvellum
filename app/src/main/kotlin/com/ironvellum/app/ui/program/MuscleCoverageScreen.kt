package com.ironvellum.app.ui.program

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.ironvellum.app.ui.components.InkSegmented
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.components.SectionHeader
import com.ironvellum.app.ui.ironvellumRepository
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.inkBorder
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
    var view by remember { mutableStateOf(CoverageView.PLANNED) }

    val presets = if (view == CoverageView.PLANNED) ui.plannedPresets else ui.loggedPresets
    val volume = remember(presets) { ProgramRules.weeklyVolume(presets) }
    val credits = remember(presets) { ProgramRules.muscleCredits(presets) }
    // Each exercise once, in routine order, keyed with its modifiers because
    // a deficit push-up works the chest differently from a flat one.
    val exercisesInView = remember(presets) {
        presets.flatMap { it.entries }
            .map { it.exerciseName to it.modifiers }
            .distinct()
            .mapNotNull { (name, modifiers) -> MuscleMap.profile(name, modifiers)?.let { Triple(name, modifiers, it.muscles) } }
    }
    var openMuscle by rememberSaveable { mutableStateOf<Muscle?>(null) }
    var openExercise by rememberSaveable { mutableStateOf<String?>(null) }
    val goal = CoverageGoal(ui.tier, ui.focus, ui.priorities)
    val target = goal.target
    val tracked = ProgramRules.TRACKED.sortedBy { volume[it] ?: 0.0 }
    val helpers = ProgramRules.HELPERS.sortedBy { volume[it] ?: 0.0 }
    val underCount = coverageGaps(volume, goal).size
    val empty = if (view == CoverageView.PLANNED) !ui.hasAnyPreset else !ui.hasLoggedWeek
    val unattributed = if (view == CoverageView.PLANNED) ui.unattributedPlannedSets else ui.unattributedLoggedSets

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                "WEEKLY COVERAGE",
                style = MaterialTheme.typography.labelLarge,
                color = IronvellumColors.SystemGreen,
                letterSpacing = IronvellumTracking.ScreenTitle,
            )
            Text(
                "BACK",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = ChakraPetch,
                color = IronvellumColors.InkMuted,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.extraSmall)
                    .inkBorder(IronvellumColors.Rune, MaterialTheme.shapes.extraSmall, 1.dp)
                    .clickable { onBack() }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        Spacer(Modifier.height(12.dp))

        if (empty) {
            InkPanel(Modifier.fillMaxWidth()) {
                Text(
                    if (view == CoverageView.PLANNED) {
                        "No workouts yet. Build a routine and it will be mapped here."
                    } else {
                        "Nothing logged in the last 7 days."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(20.dp))
            return@Column
        }

        InkSegmented(
            options = CoverageView.entries.map { it to it.label },
            selected = view,
            onPick = { view = it },
        )
        Spacer(Modifier.height(12.dp))

        InkPanel(Modifier.fillMaxWidth()) {
            BodyHeatMap(
                volume = volume,
                goal = goal,
                modifier = Modifier.fillMaxWidth(),
                figureHeight = 380.dp,
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

        SectionHeader("What each exercise trains")
        Text(
            "Tap an exercise to see every muscle it works.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        exercisesInView.forEach { (name, modifiers, shares) ->
            val key = "$name|$modifiers"
            ExerciseRow(
                name = name,
                modifiers = modifiers,
                shares = shares,
                open = openExercise == key,
                onToggle = { openExercise = if (openExercise == key) null else key },
            )
        }

        Spacer(Modifier.height(12.dp))
        SectionHeader("Sets per muscle")
        Text(
            "Tap a muscle to see which exercises train it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        tracked.forEach { muscle ->
            MuscleRow(
                muscle, volume[muscle] ?: 0.0, goal, credits[muscle].orEmpty(),
                open = openMuscle == muscle,
                onToggle = { openMuscle = if (openMuscle == muscle) null else muscle },
            )
        }

        // Judged against a floor, not the range: see ProgramRules.HELPERS.
        Spacer(Modifier.height(12.dp))
        SectionHeader("Helper muscles")
        Text(
            "Mostly trained by your other exercises. Under " +
                "${trimSets(ProgramRules.HELPER_FLOOR_SETS)} sets a week reads light, not short.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        helpers.forEach { muscle ->
            MuscleRow(
                muscle, volume[muscle] ?: 0.0, goal, credits[muscle].orEmpty(),
                open = openMuscle == muscle,
                onToggle = { openMuscle = if (openMuscle == muscle) null else muscle },
            )
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
                label = "Generate one workout",
                onClick = onGenerateSession,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(20.dp))
    }
}

/**
 * One muscle's sets against its own range; a helper's open-ended floor reads
 * "3+". Tapped open, it lists the exercises behind the number: each one's
 * sets times its share, main work counting in full and assisting work partly,
 * so the lines add up to the total above them.
 */
@Composable
private fun MuscleRow(
    muscle: Muscle,
    sets: Double,
    goal: CoverageGoal,
    credits: List<ProgramRules.MuscleCredit>,
    open: Boolean,
    onToggle: () -> Unit,
) {
    val range = rangeFor(muscle, goal)
    val level = levelOf(muscle, sets, goal)
    val verdict = when (level) {
        CoverageLevel.NONE -> "UNTRAINED"
        CoverageLevel.UNDER -> "UNDER"
        CoverageLevel.LIGHT -> "LIGHT"
        CoverageLevel.IN_RANGE -> "IN RANGE"
        CoverageLevel.OVER -> "OVER"
    }
    val colour = when (level) {
        CoverageLevel.IN_RANGE -> IronvellumColors.SystemGreen
        CoverageLevel.OVER -> IronvellumColors.SovereignGold
        CoverageLevel.LIGHT -> IronvellumColors.InkMuted
        else -> IronvellumColors.DangerRed
    }
    val bound = if (range.endInclusive == Double.MAX_VALUE) {
        "${trimSets(range.start)}+"
    } else {
        "${trimSets(range.start)}-${trimSets(range.endInclusive)}"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clickable(
                onClickLabel = if (open) "Hide what trains ${muscle.label}" else "Show what trains ${muscle.label}",
                onClick = onToggle,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            muscle.label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            color = IronvellumColors.Ink,
            modifier = Modifier.weight(1f),
        )
        Text(
            "${trimSets(sets)} / $bound",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            color = IronvellumColors.InkMuted,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            verdict,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            color = colour,
            letterSpacing = IronvellumTracking.InlineLabel,
        )
    }
    if (open) {
        Column(Modifier.fillMaxWidth().padding(start = 12.dp, bottom = 8.dp)) {
            if (credits.isEmpty()) {
                Text(
                    "Nothing in this view trains it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                )
            }
            credits.forEach { credit ->
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
        }
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
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clickable(
                onClickLabel = if (open) "Hide muscles for $name" else "Show muscles for $name",
                onClick = onToggle,
            ),
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
    }
    if (open) {
        ExerciseMuscles(shares, Modifier.fillMaxWidth().padding(bottom = 10.dp))
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
        TrainingFocus.SKILL -> "Skill target: $sets"
        else -> "Muscle target, ${volume.label.lowercase()} volume: $sets"
    }
}

/** One decimal, trimmed of a trailing .0 - "14" and "14.5", never "14.0000". */
private fun trimSets(value: Double): String {
    val rounded = (value * 10).toLong() / 10.0
    return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
}
