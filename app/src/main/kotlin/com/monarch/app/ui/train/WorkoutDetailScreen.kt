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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.monarch.app.data.Repository
import com.monarch.app.domain.SessionSet
import com.monarch.app.domain.WorkoutSession
import com.monarch.app.ui.components.SectionHeader
import com.monarch.app.ui.components.SystemWindow
import com.monarch.app.ui.components.formatDate
import com.monarch.app.ui.monarchRepository
import com.monarch.app.ui.theme.ChakraPetch
import com.monarch.app.ui.theme.MonarchColors
import com.monarch.app.ui.theme.MonarchTracking
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class WorkoutDetailUi(
    val session: WorkoutSession? = null,
    val sets: List<SessionSet> = emptyList(),
    /** True once the history stream has emitted at least once. */
    val loaded: Boolean = false,
)

class WorkoutDetailViewModel(repo: Repository, private val sessionId: Long) : ViewModel() {
    // The log source of truth is the history stream: a session absent from it
    // is genuinely not viewable, so the screen renders "not found" honestly.
    val ui: StateFlow<WorkoutDetailUi> = repo.observeHistory()
        .map { history ->
            val hit = history.firstOrNull { it.first.id == sessionId }
            WorkoutDetailUi(session = hit?.first, sets = hit?.second.orEmpty(), loaded = true)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutDetailUi())
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
            )
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
                DetailHeader(session)
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
                WorkoutSets(sets = ui.sets)
            }
        }
        Spacer(Modifier.height(96.dp))
    }
}

@Composable
private fun DetailHeader(session: WorkoutSession) {
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
                if (durationMin == null) "—" else "${durationMin / 60}h ${durationMin % 60}m",
                "DURATION",
            )
            LedgerStat("+${session.xpAwarded}", "XP", MonarchColors.Emerald)
            LedgerStat("${session.strengthScore}", "STRENGTH", MonarchColors.SovereignGold)
        }
    }
}

@Composable
private fun WorkoutSets(sets: List<SessionSet>) {
    SectionHeader("THE WORK")
    // Per-exercise groups in position order, sets in set order within each.
    val groups = sets
        .groupBy { it.exercisePosition }
        .toSortedMap()
        .map { (_, groupSets) -> groupSets.sortedBy { it.setIndex } }
    groups.forEach { groupSets ->
        val name = groupSets.firstOrNull()?.exerciseName.orEmpty()
        val totalReps = groupSets.filter { it.done }.sumOf { it.reps }
        val volumeKg = groupSets
            .filter { it.done }
            .sumOf { set -> (set.weightKg ?: 0.0) * set.reps }

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
                        append("${totalReps} reps")
                        if (volumeKg > 0.0) append(" · ${"%.0f".format(volumeKg)} kg vol")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = ChakraPetch,
                    color = MonarchColors.InkMuted,
                    letterSpacing = MonarchTracking.InlineLabel,
                )
            }
            Spacer(Modifier.height(10.dp))
            groupSets.forEach { set ->
                SetRow(set)
                Spacer(Modifier.height(6.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun SetRow(set: SessionSet) {
    val weight = if (set.weightKg == null || set.weightKg == 0.0) "BW" else "${"%.1f".format(set.weightKg)} kg"
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraSmall)
            .background(if (set.done) MonarchColors.VaultHigh else Color.Transparent)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            // Completed sets get the emerald diamond; skipped ones stay bare.
            if (set.done) "\u25C6  SET ${set.setIndex + 1}" else "\u25C7  SET ${set.setIndex + 1}",
            style = MaterialTheme.typography.labelMedium,
            fontFamily = ChakraPetch,
            color = if (set.done) MonarchColors.Emerald else MonarchColors.InkMuted,
            letterSpacing = MonarchTracking.InlineLabel,
            modifier = Modifier.weight(1f),
        )
        if (set.modifiers.isNotBlank()) {
            Text(
                set.modifiers.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = ChakraPetch,
                color = MonarchColors.SovereignGold,
                letterSpacing = MonarchTracking.InlineLabel,
                modifier = Modifier.padding(end = 12.dp),
            )
        }
        Text(
            "${set.reps} × $weight",
            style = MaterialTheme.typography.labelLarge,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = if (set.done) MonarchColors.Ink else MonarchColors.InkMuted,
        )
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
