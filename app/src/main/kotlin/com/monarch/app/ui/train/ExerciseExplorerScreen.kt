package com.monarch.app.ui.train

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import com.monarch.app.ui.theme.ChakraPetch
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.monarch.app.domain.Exercise
import com.monarch.app.domain.ExerciseHistory
import com.monarch.app.domain.Skills
import com.monarch.app.ui.components.ExercisePickerPanel
import com.monarch.app.ui.components.MonarchButton
import com.monarch.app.ui.components.SectionHeader
import com.monarch.app.ui.components.SystemWindow
import com.monarch.app.ui.components.TrendChart
import com.monarch.app.ui.components.formatDate
import com.monarch.app.ui.monarchRepository
import com.monarch.app.ui.theme.ChakraPetch
import com.monarch.app.ui.theme.MonarchColors
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

data class ExplorerUi(
    val exercises: List<Exercise> = emptyList(),
    val selected: Exercise? = null,
    val history: ExerciseHistory? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ExerciseExplorerViewModel(private val repo: Repository) : ViewModel() {

    private val selected = MutableStateFlow<Exercise?>(null)

    val ui: StateFlow<ExplorerUi> = combine(
        repo.observeExercises(),
        selected,
        selected.flatMapLatest { ex ->
            if (ex == null) flowOf(null) else repo.observeExerciseHistory(ex.id)
        },
    ) { exercises, sel, history ->
        ExplorerUi(exercises, sel, history)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExplorerUi())

    fun pick(exercise: Exercise) {
        selected.value = exercise
    }
}

@Composable
fun ExerciseExplorerScreen(
    onBack: () -> Unit,
    viewModel: ExerciseExplorerViewModel =
        viewModel(factory = viewModelFactory { initializer { ExerciseExplorerViewModel(monarchRepository()) } }),
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
                "MOVEMENT RECORDS",
                style = MaterialTheme.typography.labelLarge,
                color = MonarchColors.SystemGreen,
                letterSpacing = 6.sp,
            )
            Text(
                "BACK",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = ChakraPetch,
                color = MonarchColors.InkMuted,
                letterSpacing = 2.sp,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.extraSmall)
                    .border(1.dp, MonarchColors.Rune, MaterialTheme.shapes.extraSmall)
                    .clickable { onBack() }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        Spacer(Modifier.height(12.dp))

        val selectedExercise = ui.selected
        if (selectedExercise == null) {
            SystemWindow(Modifier.fillMaxWidth()) {
                ExercisePickerPanel(
                    exercises = ui.exercises,
                    onPick = viewModel::pick,
                    onDismiss = onBack,
                )
            }
        } else {
            SystemWindow(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                onClick = { viewModel.pick(selectedExercise) },
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            selectedExercise.name,
                            style = MaterialTheme.typography.titleLarge,
                            color = MonarchColors.SovereignGold,
                        )
                        val skill = Skills.forName(selectedExercise.name)
                        Text(
                            buildString {
                                append(selectedExercise.muscleGroup.name.lowercase())
                                append("  ·  ")
                                append(if (selectedExercise.isWeighted) "weighted" else "bodyweight")
                                if (skill != null) {
                                    append("  ·  skill tier ${Skills.tierLabel(skill.tier)}")
                                }
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MonarchColors.SystemGreen,
                        )
                    }
                    Text(
                        "SWITCH",
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = ChakraPetch,
                        color = MonarchColors.InkMuted,
                        letterSpacing = 3.sp,
                    )
                }
            }

            val history = ui.history
            if (history == null || history.isEmpty) {
                SystemWindow(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    Text(
                        "No sets logged yet - train this movement to open its record.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MonarchColors.InkMuted,
                    )
                }
            } else {
                Spacer(Modifier.height(14.dp))
                StatGrid(history)
                Spacer(Modifier.height(14.dp))
                ScoreChart(history)
                Spacer(Modifier.height(14.dp))
                RepsChart(history)
                SectionHeader("Set Log")
                SetLog(history)
            }
        }
        Spacer(Modifier.height(96.dp))
    }
}

@Composable
private fun StatCard(label: String, value: String, hint: String = "") {
    SystemWindow(Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            color = MonarchColors.SystemGreen,
            letterSpacing = 2.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = MonarchColors.SovereignGold,
        )
        if (hint.isNotBlank()) {
            Text(hint, style = MaterialTheme.typography.labelSmall, color = MonarchColors.InkMuted)
        }
    }
}

@Composable
private fun StatGrid(history: ExerciseHistory) {
    val rows = listOf(
        listOf(
            "SESSIONS" to history.sessions.toString(),
            "COMPLETED SETS" to history.completedSets.toString(),
        ),
        listOf(
            "TOTAL REPS" to history.totalReps.toString(),
            "TOTAL VOLUME" to "${formatKg(history.totalVolumeKg)} kg",
        ),
        listOf(
            "HEAVIEST SET" to (history.heaviestWeightKg?.let { "${formatKg(it)} kg × ${history.heaviestReps}" } ?: "BW"),
            "BEST SET (REPS × LOAD)" to "${history.bestSetReps} × ${formatKg(history.bestSetLoadKg)} kg",
        ),
        listOf(
            "BEST STRENGTH SCORE" to (if (history.bestScore > 0.0) formatKg(history.bestScore) else "—"),
            "DAYS SINCE LAST" to (history.daysSinceLast?.toString() ?: "—"),
        ),
        listOf(
            "FIRST LOGGED" to (history.firstLoggedAtMs?.let { formatDate(it) } ?: "—"),
            "LAST LOGGED" to (history.lastLoggedAtMs?.let { formatDate(it) } ?: "—"),
        ),
    )
    rows.forEach { pair ->
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            pair.forEachIndexed { index, (label, value) ->
                val hint = when (label) {
                    "HEAVIEST SET" -> history.heaviestAtMs?.let { formatDate(it, "MMM d") } ?: ""
                    "BEST SET (REPS × LOAD)" -> history.bestSetAtMs?.let { formatDate(it, "MMM d") } ?: ""
                    "BEST STRENGTH SCORE" -> history.bestScoreAtMs?.let { formatDate(it, "MMM d") } ?: ""
                    else -> ""
                }
                androidx.compose.foundation.layout.Box(Modifier.weight(1f)) {
                    StatCard(label, value, hint)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

private fun formatKg(v: Double): String =
    if (v >= 100.0) v.toInt().toString() else String.format(java.util.Locale.US, "%.1f", v)

/** Best-set strength score per session, hand-drawn line. */
@Composable
private fun ScoreChart(history: ExerciseHistory) {
    val values = history.series.map { it.bestSetScore }
    SystemWindow(Modifier.fillMaxWidth()) {
        Text(
            "STRENGTH SCORE PER SESSION",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            color = MonarchColors.SystemGreen,
            letterSpacing = 2.sp,
        )
        if (values.size < 2) {
            Spacer(Modifier.height(8.dp))
            Text(
                "One session logged — a trend needs at least two.",
                style = MaterialTheme.typography.bodySmall,
                color = MonarchColors.InkMuted,
            )
        } else {
            // Scores sit in a narrow band, so this one scales to its own span.
            TrendChart(
                values,
                color = MonarchColors.SovereignGold,
                fromZero = false,
                modifier = Modifier.fillMaxWidth().height(110.dp).padding(top = 8.dp),
            )
        }
        Text(
            "best set score per session · ${history.series.size} sessions",
            style = MaterialTheme.typography.labelSmall,
            color = MonarchColors.InkMuted,
        )
    }
}

/** Total completed reps per session, hand-drawn bars. */
@Composable
private fun RepsChart(history: ExerciseHistory) {
    val values = history.series.map { it.totalReps.toDouble() }
    SystemWindow(Modifier.fillMaxWidth()) {
        Text(
            "TOTAL REPS PER SESSION",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            color = MonarchColors.SystemGreen,
            letterSpacing = 2.sp,
        )
        if (values.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "No completed sets yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MonarchColors.InkMuted,
            )
        } else {
            // Rep counts are volume, so zero-based like every other count.
            TrendChart(
                values,
                modifier = Modifier.fillMaxWidth().height(110.dp).padding(top = 8.dp),
            )
        }
        Text(
            "completed reps per session · oldest to newest",
            style = MaterialTheme.typography.labelSmall,
            color = MonarchColors.InkMuted,
        )
    }
}

@Composable
private fun SetLog(history: ExerciseHistory) {
    val byDay = history.entries.groupBy { formatDate(it.atMs, "EEE · MMM d, yyyy") }
    byDay.forEach { (day, sets) ->
        SystemWindow(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
            Text(
                day,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = ChakraPetch,
                color = MonarchColors.SovereignGold,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(6.dp))
            sets.forEach { set ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "SET ${set.setIndex + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = ChakraPetch,
                        color = MonarchColors.InkMuted,
                    )
                    Text(
                        "${set.reps} reps · " + (set.weightKg?.let { "${formatKg(it)} kg" } ?: "BW"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MonarchColors.Ink,
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        if (set.modifiers.isNotBlank()) {
                            Text(
                                set.modifiers.split(",").joinToString(" · ") { it.trim() },
                                style = MaterialTheme.typography.labelSmall,
                                color = MonarchColors.SystemGreen,
                            )
                        }
                        if (!set.done) {
                            Text(
                                "✕ not completed",
                                style = MaterialTheme.typography.labelSmall,
                                color = MonarchColors.InkMuted,
                            )
                        }
                    }
                }
            }
        }
    }
}
