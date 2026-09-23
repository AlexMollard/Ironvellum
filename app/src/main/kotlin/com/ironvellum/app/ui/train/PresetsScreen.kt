package com.ironvellum.app.ui.train

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Button
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
import com.ironvellum.app.data.Repository
import com.ironvellum.app.domain.MovementDifficulty
import com.ironvellum.app.domain.WorkoutPreset
import com.ironvellum.app.ui.components.SectionHeader
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.components.formatDate
import com.ironvellum.app.ui.ironvellumRepository
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.inkBorder
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.IronvellumTracking
import kotlinx.coroutines.flow.SharingStarted
import com.ironvellum.app.ui.components.NavChip
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import com.ironvellum.app.ui.launchGuarded
import kotlinx.coroutines.launch

data class TrainUi(
    val presets: List<WorkoutPreset> = emptyList(),
    val history: List<Pair<com.ironvellum.app.domain.WorkoutSession, List<com.ironvellum.app.domain.SessionSet>>> = emptyList(),
)

class PresetsViewModel(private val repo: Repository) : ViewModel() {

    val ui: StateFlow<TrainUi> = combine(
        repo.observePresets(),
        repo.observeHistory(),
    ) { presets, history ->
        TrainUi(presets, history)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrainUi())

    fun begin(presetId: Long, onStarted: (Long) -> Unit) {
        viewModelScope.launchGuarded("begin preset") { onStarted(repo.startSessionFromPreset(presetId)) }
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
    onOpenWorkout: (Long) -> Unit,
    viewModel: PresetsViewModel =
        viewModel(factory = viewModelFactory { initializer { PresetsViewModel(ironvellumRepository()) } }),
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
                color = IronvellumColors.SystemGreen,
                letterSpacing = 6.sp,
            )
            Spacer(Modifier.height(12.dp))
            IronvellumButton(
                label = "Quick Session",
                onClick = { viewModel.beginQuick(onQuickSession) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            // The two navigation destinations sat here as full-width primary
            // buttons, fighting the one real action (Quick Session); they are
            // now quiet links below it.
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NavChip(
                    label = "EXPLORER",
                    icon = Icons.Outlined.FitnessCenter,
                    onClick = onOpenExercises,
                    modifier = Modifier.weight(1f),
                )
                NavChip(
                    label = "FULL LOG",
                    icon = Icons.Outlined.History,
                    onClick = onOpenLog,
                    modifier = Modifier.weight(1f),
                )
            }
            SectionHeader("Presets")
            if (ui.presets.isEmpty()) {
                Text(
                    "No presets forged yet. Build your first training day.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ui.presets.forEach { preset ->
                InkPanel(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(preset.name, style = MaterialTheme.typography.titleMedium, color = IronvellumColors.Ink)
                        preset.scheduledDay?.let {
                            Box(
                                Modifier
                                    .clip(MaterialTheme.shapes.extraSmall)
                                    .inkBorder(IronvellumColors.SovereignGold, MaterialTheme.shapes.extraSmall, 1.dp)
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Text(DAY_LABELS[it] ?: "", style = MaterialTheme.typography.labelSmall, color = IronvellumColors.SovereignGold)
                            }
                        }
                    }
                    if (preset.note.isNotBlank()) {
                        Text(preset.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(8.dp))
                    preset.entries.take(PRESET_CARD_MOVEMENTS).forEach { entry ->
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
                                        color = IronvellumColors.SystemGreen,
                                    )
                                }
                            }
                            Text(
                                "${entry.targetSets}×${entry.targetReps}" +
                                    (if (MovementDifficulty.isHoldByName(entry.exerciseName)) "s" else "") +
                                    (entry.targetWeightKg?.let { "  @${formatKg(it)}" } ?: ""),
                                style = MaterialTheme.typography.labelLarge,
                                fontFamily = ChakraPetch,
                                color = IronvellumColors.InkMuted,
                            )
                        }
                    }
                    if (preset.entries.size > PRESET_CARD_MOVEMENTS) {
                        Text(
                            "+${preset.entries.size - PRESET_CARD_MOVEMENTS} MORE",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = ChakraPetch,
                            color = IronvellumColors.InkMuted,
                            letterSpacing = IronvellumTracking.InlineLabel,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "[ EDIT ]",
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = ChakraPetch,
                            color = IronvellumColors.InkMuted,
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.extraSmall)
                                .inkBorder(IronvellumColors.InkMuted.copy(alpha = 0.4f), MaterialTheme.shapes.extraSmall, 1.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { onEdit(preset.id) }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                        IronvellumButton("Begin", onClick = { viewModel.begin(preset.id, onStartSession) })
                    }
                }
            }

            // "New Preset" used to float over the list, and it parked itself on
            // top of a card's BEGIN button. A tile at the end of the presets
            // covers nothing and needs no clearance spacer underneath.
            InkPanel(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .heightIn(min = 48.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClickLabel = "New preset",
                    ) { onNew() },
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = null,
                        tint = IronvellumColors.SystemGreen,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "NEW PRESET",
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = ChakraPetch,
                        color = IronvellumColors.SystemGreen,
                        letterSpacing = IronvellumTracking.InlineLabel,
                    )
                }
            }

            // Was "Activity Log", which collided head-on with the Stats
            // screen's ACTIVITY tab (steps and sleep) - the owner kept going
            // there hunting for his workouts.
            SectionHeader("Recent Workouts")
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
            // show the top five, and FULL LOG above already leads to
            // the complete, month-grouped history.
            ui.history.take(ACTIVITY_LOG_ROWS).forEach { (session, sets) ->
                // The same rows are tappable in the full log; here they only
                // informed. A completed row now opens what it names.
                InkPanel(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clickable { onOpenWorkout(session.id) },
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(session.label, style = MaterialTheme.typography.titleSmall)
                            Text(
                                formatDate(session.startedAtMs) + " · " +
                                    sets.count { it.done } + "/" + sets.size + " sets · " +
                                    sets.filter { it.done }.sumOf { it.reps } + " reps" +
                                    sets.filter { it.done }.sumOf { it.durationSec ?: 0 }
                                        .let { held -> if (held > 0) " · ${held}s held" else "" },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text("+${session.xpAwarded} XP", style = MaterialTheme.typography.labelLarge, color = IronvellumColors.SovereignGold)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

/** Enough to show the week's work; the full log carries the rest. */
private const val ACTIVITY_LOG_ROWS = 6

/**
 * Per-card movement cap. Presets can hold many exercises and rendering every
 * one turned each card into a wall of identical rows; three movements is
 * enough to recognise the preset. The full manifest still lives in the preset
 * editor and on the Today quest card.
 */
private const val PRESET_CARD_MOVEMENTS = 3

