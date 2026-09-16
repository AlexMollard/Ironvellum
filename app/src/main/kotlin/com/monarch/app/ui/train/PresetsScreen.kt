package com.monarch.app.ui.train

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.monarch.app.data.Repository
import com.monarch.app.domain.WorkoutPreset
import com.monarch.app.ui.components.SectionHeader
import com.monarch.app.ui.components.MonarchButton
import com.monarch.app.ui.components.SystemWindow
import com.monarch.app.ui.components.formatDate
import com.monarch.app.ui.monarchRepository
import com.monarch.app.ui.theme.ChakraPetch
import com.monarch.app.ui.theme.inkBorder
import com.monarch.app.ui.theme.MonarchColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TrainUi(
    val presets: List<WorkoutPreset> = emptyList(),
    val history: List<Pair<com.monarch.app.domain.WorkoutSession, List<com.monarch.app.domain.SessionSet>>> = emptyList(),
)

class PresetsViewModel(private val repo: Repository) : ViewModel() {

    val ui: StateFlow<TrainUi> = combine(
        repo.observePresets(),
        repo.observeHistory(),
    ) { presets, history ->
        TrainUi(presets, history)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrainUi())

    fun begin(presetId: Long, onStarted: (Long) -> Unit) {
        viewModelScope.launch { onStarted(repo.startSessionFromPreset(presetId)) }
    }

    fun beginQuick(onStarted: (Long) -> Unit) {
        viewModelScope.launch { onStarted(repo.startFreeformSession("Quick Session")) }
    }
}

private val DAY_LABELS = mapOf(1 to "MON", 2 to "TUE", 3 to "WED", 4 to "THU", 5 to "FRI", 6 to "SAT", 7 to "SUN")

internal fun formatKg(kg: Double?): String =
    when {
        kg == null -> "BW"
        kg == kg.toLong().toDouble() -> "${kg.toLong()}kg"
        else -> "${kg}kg"
    }

@Composable
fun PresetsScreen(
    onEdit: (Long) -> Unit,
    onNew: () -> Unit,
    onStartSession: (Long) -> Unit,
    onQuickSession: (Long) -> Unit,
    onOpenExercises: () -> Unit,
    onOpenLog: () -> Unit,
    viewModel: PresetsViewModel =
        viewModel(factory = viewModelFactory { initializer { PresetsViewModel(monarchRepository()) } }),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(20.dp))
            Text(
                "TRAINING GROUNDS",
                style = MaterialTheme.typography.labelLarge,
                color = MonarchColors.SystemGreen,
                letterSpacing = 6.sp,
            )
            Spacer(Modifier.height(12.dp))
            MonarchButton(
                label = "Quick Session",
                onClick = { viewModel.beginQuick(onQuickSession) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            MonarchButton(
                label = "Exercise Explorer",
                onClick = onOpenExercises,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            MonarchButton(
                label = "Full Workout Log",
                onClick = onOpenLog,
                modifier = Modifier.fillMaxWidth(),
            )
            SectionHeader("Presets")
            if (ui.presets.isEmpty()) {
                Text(
                    "No presets forged yet. Build your first training day.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ui.presets.forEach { preset ->
                SystemWindow(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(preset.name, style = MaterialTheme.typography.titleMedium, color = MonarchColors.Ink)
                        preset.scheduledDay?.let {
                            Box(
                                Modifier
                                    .clip(MaterialTheme.shapes.extraSmall)
                                    .inkBorder(MonarchColors.SovereignGold, MaterialTheme.shapes.extraSmall, 1.dp)
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Text(DAY_LABELS[it] ?: "", style = MaterialTheme.typography.labelSmall, color = MonarchColors.SovereignGold)
                            }
                        }
                    }
                    if (preset.note.isNotBlank()) {
                        Text(preset.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(8.dp))
                    preset.entries.forEach { entry ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(entry.exerciseName, style = MaterialTheme.typography.bodyMedium)
                                if (entry.modifiers.isNotBlank()) {
                                    Text(
                                        entry.modifiers.split(",").joinToString(" · ") { it.trim() },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MonarchColors.SystemGreen,
                                    )
                                }
                            }
                            Text(
                                "${entry.targetSets}×${entry.targetReps}" + (entry.targetWeightKg?.let { "  @${formatKg(it)}" } ?: ""),
                                style = MaterialTheme.typography.labelLarge,
                                fontFamily = ChakraPetch,
                                color = MonarchColors.InkMuted,
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "[ EDIT ]",
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = ChakraPetch,
                            color = MonarchColors.InkMuted,
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.extraSmall)
                                .inkBorder(MonarchColors.InkMuted.copy(alpha = 0.4f), MaterialTheme.shapes.extraSmall, 1.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { onEdit(preset.id) }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                        MonarchButton("Begin", onClick = { viewModel.begin(preset.id, onStartSession) })
                    }
                }
            }

            SectionHeader("Activity Log")
            if (ui.history.isEmpty()) {
                Text(
                    "No completed campaigns yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Only the latest few: this screen is a plain scrolling Column, so
            // every row it lists is composed whether or not it is on screen.
            // After a few years of training that is a thousand rows built to
            // show the top five, and FULL WORKOUT LOG above already leads to
            // the complete, month-grouped history.
            ui.history.take(ACTIVITY_LOG_ROWS).forEach { (session, sets) ->
                SystemWindow(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(session.label, style = MaterialTheme.typography.titleSmall)
                            Text(
                                formatDate(session.startedAtMs) + " · " +
                                    sets.count { it.done } + "/" + sets.size + " sets · " +
                                    sets.filter { it.done }.sumOf { it.reps } + " reps",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text("+${session.xpAwarded} XP", style = MaterialTheme.typography.labelLarge, color = MonarchColors.SovereignGold)
                    }
                }
            }
            // Clears the floating button. One text scale app-wide, so one
            // clearance is enough.
            Spacer(Modifier.height(128.dp))
        }
        ExtendedFloatingActionButton(
            onClick = onNew,
            // The M3 FAB's container shape comes from its own defaults, not the theme.
            shape = MaterialTheme.shapes.small,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text("  New Preset")
        }
    }
}

/** Enough to show the week's work; the full log carries the rest. */
private const val ACTIVITY_LOG_ROWS = 6
