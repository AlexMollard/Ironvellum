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
        // Done sets grouped per movement, then read through the SAME
        // weeklyVolume rule as the plan, so the two views can be compared.
        val loggedSets = recent.flatMap { (_, sets) -> sets.filter { it.done } }
        val byExercise = loggedSets.groupBy { it.exerciseName }
        val loggedPresets = if (byExercise.isEmpty()) {
            emptyList()
        } else {
            listOf(
                PlannedPreset(
                    name = "Last 7 days",
                    note = "",
                    scheduledDay = null,
                    entries = byExercise.map { (name, sets) ->
                        PlannedEntry(
                            exerciseName = name,
                            sets = sets.size,
                            // Coverage only counts sets; the average reps
                            // here is display filler for the data class.
                            reps = if (sets.isEmpty()) 10 else sets.sumOf { it.reps } / sets.size,
                            targetWeightKg = null,
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
    val target = ProgramRules.weeklySetTarget(ui.tier, ui.focus)
    val tracked = ProgramRules.TRACKED.sortedBy { volume[it] ?: 0.0 }
    val helpers = ProgramRules.HELPERS.sortedBy { volume[it] ?: 0.0 }
    val underCount = coverageGaps(volume, target).size
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
                        "No presets forged yet. Build a training week and it will be mapped here."
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
                target = target,
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
                "$unattributed sets could not be attributed to a muscle profile and are not counted.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))

        SectionHeader("Sets per muscle")
        tracked.forEach { muscle -> MuscleRow(muscle, volume[muscle] ?: 0.0, target) }

        // Judged against a floor, not the range: see ProgramRules.HELPERS.
        Spacer(Modifier.height(12.dp))
        SectionHeader("Helper muscles")
        Text(
            "Your other lifts do most of this work - presses the front delts, every grip the forearms, " +
                "hinges the lower back, squats the adductors. No study sets a dose for them, so they " +
                "need a floor of ${trimSets(ProgramRules.HELPER_FLOOR_SETS)} sets a week, not a range.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        helpers.forEach { muscle -> MuscleRow(muscle, volume[muscle] ?: 0.0, ProgramRules.HELPER_RANGE) }

        if (underCount > 0) {
            Spacer(Modifier.height(12.dp))
            Text(
                "$underCount muscles are short of target this week. One session can cover the gaps.",
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

/** One muscle's sets against its range; an open-ended floor reads "3+". */
@Composable
private fun MuscleRow(muscle: Muscle, sets: Double, range: ClosedFloatingPointRange<Double>) {
    val level = coverageLevel(sets, range)
    val verdict = when (level) {
        CoverageLevel.NONE -> "UNTRAINED"
        CoverageLevel.UNDER -> "UNDER"
        CoverageLevel.IN_RANGE -> "IN RANGE"
        CoverageLevel.OVER -> "OVER"
    }
    val colour = when (level) {
        CoverageLevel.IN_RANGE -> IronvellumColors.SystemGreen
        CoverageLevel.OVER -> IronvellumColors.SovereignGold
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
            .padding(vertical = 2.dp),
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
}

/**
 * Strength and skill targets are 5-15 at every volume level, so naming the
 * level there says nothing - "lean volume, strength" read as a contradiction.
 */
internal fun targetCaption(volume: VolumeLevel, focus: TrainingFocus, target: ClosedFloatingPointRange<Double>): String {
    val sets = "${trimSets(target.start)}-${trimSets(target.endInclusive)} sets per muscle a week"
    return when (focus) {
        TrainingFocus.STRENGTH -> "Target for strength training: $sets"
        TrainingFocus.SKILL -> "Target for skill training: $sets"
        else -> "Target for muscle growth at ${volume.label.lowercase()} volume: $sets"
    }
}

/** One decimal, trimmed of a trailing .0 - "14" and "14.5", never "14.0000". */
private fun trimSets(value: Double): String {
    val rounded = (value * 10).toLong() / 10.0
    return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
}
