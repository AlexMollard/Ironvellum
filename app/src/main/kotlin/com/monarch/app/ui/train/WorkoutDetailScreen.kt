package com.monarch.app.ui.train

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.monarch.app.data.Repository
import com.monarch.app.domain.SessionSet
import com.monarch.app.domain.Exercise
import com.monarch.app.domain.ExerciseMetric
import com.monarch.app.domain.isStrength
import com.monarch.app.domain.Energy
import com.monarch.app.domain.MovementDifficulty
import com.monarch.app.domain.EnergyConfidence
import com.monarch.app.domain.EnergyEstimate
import com.monarch.app.domain.WorkoutSession
import com.monarch.app.domain.WorkoutShare
import com.monarch.app.ui.components.SectionHeader
import com.monarch.app.ui.components.ShareCardDialog
import com.monarch.app.ui.components.SystemWindow
import com.monarch.app.ui.components.formatBodyValue
import com.monarch.app.ui.components.formatDate
import com.monarch.app.ui.components.metricTotals
import com.monarch.app.ui.components.setFigure
import com.monarch.app.ui.monarchRepository
import com.monarch.app.ui.theme.ChakraPetch
import com.monarch.app.ui.theme.inkBorder
import com.monarch.app.ui.theme.MonarchColors
import com.monarch.app.ui.theme.MonarchTracking
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class WorkoutDetailUi(
    val session: WorkoutSession? = null,
    val sets: List<SessionSet> = emptyList(),
    /** True once the history stream has emitted at least once. */
    val loaded: Boolean = false,
    /** Estimated burn for the session; null when nothing can be computed. */
    val energy: EnergyEstimate? = null,
    /** Catalogue by id, for unit-correct rendering and the share card. */
    val exercises: Map<Long, Exercise> = emptyMap(),
)

class WorkoutDetailViewModel(repo: Repository, private val sessionId: Long) : ViewModel() {
    // The log source of truth is the history stream: a session absent from it
    // is genuinely not viewable, so the screen renders "not found" honestly.
    val ui: StateFlow<WorkoutDetailUi> = combine(
        repo.observeHistory(),
        repo.observeStats().map { it.firstOrNull()?.weightKg },
        repo.observeExercises(),
    ) { history, bodyKg, exercises ->
        val hit = history.firstOrNull { it.first.id == sessionId }
        val session = hit?.first
        val sets = hit?.second.orEmpty()
        // Same pure estimate the stats screen uses; minutes come from the wall
        // clock when the session was completed.
        val minutes = session?.completedAtMs
            ?.let { ((it - session.startedAtMs) / 60_000L).toInt().coerceAtLeast(0) }
        val byId = exercises.associateBy { it.id }
        WorkoutDetailUi(
            session = session,
            sets = sets,
            loaded = true,
            energy = session?.let { Energy.sessionKcal(sets, byId, bodyKg, minutes) },
            exercises = byId,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutDetailUi())
}

/**
 * The full record of one workout: nothing hidden. The public note is feed
 * material; the private note is device-only and is labelled as such.
 */
@Composable
fun WorkoutDetailScreen(
    sessionId: Long,
    onBack: () -> Unit,
    viewModel: WorkoutDetailViewModel = viewModel(
        factory = viewModelFactory {
            initializer { WorkoutDetailViewModel(monarchRepository(), sessionId) }
        },
    ),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var shareText by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "SESSION RECORD",
                style = MaterialTheme.typography.labelLarge,
                color = MonarchColors.SystemGreen,
                letterSpacing = MonarchTracking.ScreenTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // Weighted so the two actions beside it keep their width on a
                // narrow phone instead of being pushed off the row.
                modifier = Modifier.weight(1f),
            )
            ui.session?.let { session ->
                IconButton(onClick = {
                    shareText = WorkoutShare.format(session, ui.sets, ui.exercises)
                }) {
                    Icon(
                        Icons.Outlined.IosShare,
                        contentDescription = "Share this workout",
                        tint = MonarchColors.Emerald,
                    )
                }
            }
            Text(
                "BACK",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = ChakraPetch,
                color = MonarchColors.InkMuted,
                letterSpacing = MonarchTracking.InlineLabel,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.extraSmall)
                    .clickable { onBack() }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        Spacer(Modifier.height(12.dp))

        val session = ui.session
        when {
            !ui.loaded -> {
                // Brief startup window before the history stream emits; render
                // structure, not a lie about missing data.
            }
            session == null -> SystemWindow(Modifier.fillMaxWidth(), accent = MonarchColors.DangerRed) {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "SESSION NOT FOUND",
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = ChakraPetch,
                        fontWeight = FontWeight.Bold,
                        color = MonarchColors.DangerRed,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "This record is missing from the chronicle. It may have been removed.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MonarchColors.InkMuted,
                    )
                }
            }
            else -> {
                // Tapping the kcal readout reveals the formula behind it — a
                // number without its basis asks for blind trust.
                var showBasis by remember { mutableStateOf(false) }
                DetailHeader(session, ui.energy, showBasis) { showBasis = !showBasis }
                if (session.note.isNotBlank()) {
                    SectionHeader("FIELD NOTE · SHARED")
                    SystemWindow(Modifier.fillMaxWidth(), accent = MonarchColors.Emerald) {
                        Text(
                            session.note,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MonarchColors.Ink,
                        )
                    }
                }
                if (session.privateNote.isNotBlank()) {
                    SectionHeader("SEALED NOTE · PRIVATE")
                    SystemWindow(Modifier.fillMaxWidth(), accent = MonarchColors.SovereignGold) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Lock,
                                contentDescription = "Private",
                                tint = MonarchColors.SovereignGold,
                            )
                            Text(
                                "  NEVER LEAVES THIS DEVICE",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = ChakraPetch,
                                color = MonarchColors.SovereignGold,
                                letterSpacing = MonarchTracking.InlineLabel,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            session.privateNote,
                            style = MaterialTheme.typography.bodyMedium,
                            // Muted ink: the sealed note reads quieter than the
                            // shared one, matching its device-only nature.
                            color = MonarchColors.InkMuted,
                        )
                    }
                }
                WorkoutSets(sets = ui.sets, exercises = ui.exercises)
            }
        }
        Spacer(Modifier.height(96.dp))
    }

    shareText?.let { text ->
        ShareCardDialog(text = text, onDismiss = { shareText = null })
    }
}

@Composable
private fun DetailHeader(
    session: WorkoutSession,
    energy: EnergyEstimate?,
    showBasis: Boolean,
    onToggleBasis: () -> Unit,
) {
    val durationMin = session.completedAtMs?.let { done ->
        ((done - session.startedAtMs) / 60_000L).coerceAtLeast(0)
    }
    SystemWindow(Modifier.fillMaxWidth()) {
        Text(
            session.title.ifBlank { session.label },
            style = MaterialTheme.typography.headlineSmall,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = MonarchColors.Ink,
        )
        Text(
            formatDate(session.completedAtMs ?: session.startedAtMs),
            style = MaterialTheme.typography.labelMedium,
            color = MonarchColors.InkMuted,
        )
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            LedgerStat(
                // "0h 0m" for a sub-minute session read as broken data.
                when {
                    durationMin == null -> "—"
                    durationMin < 1L -> "<1m"
                    durationMin < 60L -> "${durationMin}m"
                    else -> "${durationMin / 60}h ${durationMin % 60}m"
                },
                "DURATION",
            )
            LedgerStat("+${session.xpAwarded}", "XP", MonarchColors.Emerald)
            LedgerStat("${session.strengthScore}", "STRENGTH", MonarchColors.SovereignGold)
            if (energy != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clip(MaterialTheme.shapes.extraSmall).clickable(onClick = onToggleBasis)) {
                    Text(
                        "\u2248${energy.kcal}",
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = ChakraPetch,
                        fontWeight = FontWeight.Bold,
                        color = MonarchColors.SystemGreen,
                    )
                    Text(
                        energyConfidenceLabel(energy.confidence),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = ChakraPetch,
                        color = MonarchColors.InkMuted,
                        letterSpacing = MonarchTracking.InlineLabel,
                    )
                }
            }
        }
        if (showBasis && energy != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                energyBasisCopy(energy),
                style = MaterialTheme.typography.bodySmall,
                color = MonarchColors.InkMuted,
            )
            energy.missing.forEach { name ->
                Spacer(Modifier.height(4.dp))
                Text(
                    "Log $name for a sharper estimate.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MonarchColors.SovereignGold,
                )
            }
        }
    }
}

private fun energyConfidenceLabel(confidence: EnergyConfidence): String = when (confidence) {
    EnergyConfidence.MEASURED -> "KCAL · MEASURED"
    EnergyConfidence.ESTIMATED -> "KCAL · EST."
    EnergyConfidence.COARSE -> "KCAL · ROUGH"
}

private fun energyBasisCopy(energy: EnergyEstimate): String = when (energy.confidence) {
    EnergyConfidence.MEASURED -> "Measured by Health Connect — not an estimate."
    EnergyConfidence.ESTIMATED -> "Estimated: ${energy.basis}. MET values come from the Compendium of Physical Activities."
    // Lifting logs reps, not minutes, so working time is inferred from set count.
    EnergyConfidence.COARSE -> "Rough estimate: ${energy.basis}. Lifting sets have no logged minutes, so working time is inferred from set count — treat this as a ballpark."
}

@Composable
private fun WorkoutSets(sets: List<SessionSet>, exercises: Map<Long, Exercise>) {
    SectionHeader("THE WORK")
    // Per-exercise groups in position order, sets in set order within each.
    val groups = sets
        .groupBy { it.exercisePosition }
        .toSortedMap()
        .map { (_, groupSets) -> groupSets.sortedBy { it.setIndex } }
    groups.forEach { groupSets ->
        val name = groupSets.firstOrNull()?.exerciseName.orEmpty()
        val hold = groupSets.firstOrNull()?.let { set ->
            MovementDifficulty.isHoldSet(exercises[set.exerciseId]?.metric, set.exerciseName, set.modifiers)
        } == true
        // A legacy hold catalogued as REPS still holds seconds, so the hold
        // detection overrides the catalogue before anything counts reps.
        val metric = if (hold) ExerciseMetric.HOLD
        else groupSets.firstOrNull()?.let { exercises[it.exerciseId]?.metric } ?: ExerciseMetric.REPS
        val doneInGroup = groupSets.filter { it.done }
        // Seconds are seconds, attempts are attempts. Summing them as reps is
        // what printed "140 reps" for a session that held two thirds of that
        // figure and "7 reps" for 7 boulder problems.
        val totals = metricTotals(doneInGroup) { metric }
        val volumeKg = if (metric.isStrength) doneInGroup.sumOf { set -> (set.weightKg ?: 0.0) * set.reps } else 0.0
        SystemWindow(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    name.uppercase(),
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = ChakraPetch,
                    fontWeight = FontWeight.SemiBold,
                    color = MonarchColors.SystemGreen,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    buildString {
                        when (metric) {
                            ExerciseMetric.HOLD -> append("${totals.heldSeconds}s held")
                            ExerciseMetric.REPS -> append("${totals.reps} reps")
                            ExerciseMetric.ATTEMPTS_GRADE -> {
                                append("${totals.attempts} attempts")
                                // Grades are free text with no ordering, so the
                                // group shows the last one entered rather than
                                // pretending to rank them.
                                groupSets.lastOrNull { !it.grade.isNullOrBlank() }
                                    ?.grade?.let { append(" · $it") }
                            }
                            ExerciseMetric.DISTANCE_TIME ->
                                append(setFigure(metric, 0, totals.secondsWorked, totals.km * 1000.0).figure)
                            ExerciseMetric.DURATION ->
                                append(setFigure(metric, 0, totals.secondsWorked, null).figure)
                        }
                        if (volumeKg > 0.0) append(" · ${"%.0f".format(volumeKg)} kg vol")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = ChakraPetch,
                    color = MonarchColors.InkMuted,
                    letterSpacing = MonarchTracking.InlineLabel,
                )
            }
            Spacer(Modifier.height(10.dp))
            // Sets as a wrapping strip, not one full-width row each: a
            // five-movement session produced seventeen identical bars and a
            // screen you had to scroll to read a single number off.
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                groupSets.forEach { set -> SetChip(set, metric, hold) }
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun SetChip(set: SessionSet, metric: ExerciseMetric, hold: Boolean) {
    val weight = if (set.weightKg == null || set.weightKg == 0.0) "BW" else "${"%.1f".format(set.weightKg)} kg"
    Column(
        Modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .background(if (set.done) MonarchColors.VaultHigh else Color.Transparent)
            .inkBorder(if (set.done) MonarchColors.Emerald.copy(alpha = 0.35f) else MonarchColors.Rune, MaterialTheme.shapes.extraSmall, 1.dp)
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Text(
            when {
                hold -> "${set.durationSec ?: set.reps}s" +
                    if (set.weightKg != null && set.weightKg > 0.0) " × $weight" else ""
                metric == ExerciseMetric.ATTEMPTS_GRADE ->
                    // The grade is the climb's identity; the weight slot is not
                    // (a boulder problem carries no load).
                    set.reps.toString() + set.grade?.takeIf { it.isNotBlank() }?.let { " × $it" }.orEmpty()
                metric == ExerciseMetric.DISTANCE_TIME ->
                    setFigure(metric, set.reps, set.durationSec, set.distanceM).figure
                metric == ExerciseMetric.DURATION ->
                    setFigure(metric, set.reps, set.durationSec, null).figure
                else -> "${set.reps} × $weight"
            },
            style = MaterialTheme.typography.labelLarge,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = if (set.done) MonarchColors.Ink else MonarchColors.InkMuted,
            maxLines = 1,
            softWrap = false,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                // Completed sets get the emerald diamond; skipped ones stay bare.
                if (set.done) "\u25C6 ${set.setIndex + 1}" else "\u25C7 ${set.setIndex + 1}",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = ChakraPetch,
                color = if (set.done) MonarchColors.Emerald else MonarchColors.InkMuted,
                letterSpacing = MonarchTracking.InlineLabel,
                maxLines = 1,
                softWrap = false,
            )
            if (set.modifiers.isNotBlank()) {
                Text(
                    " · ${set.modifiers.uppercase()}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = ChakraPetch,
                    color = MonarchColors.SovereignGold,
                    letterSpacing = MonarchTracking.InlineLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun LedgerStat(value: String, label: String, accent: Color = MonarchColors.Ink) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = accent,
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
