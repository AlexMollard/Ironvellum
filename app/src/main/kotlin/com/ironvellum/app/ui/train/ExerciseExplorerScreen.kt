package com.ironvellum.app.ui.train

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ironvellum.app.data.Repository
import com.ironvellum.app.domain.Exercise
import com.ironvellum.app.domain.ExerciseHistory
import com.ironvellum.app.domain.ExerciseMetric
import com.ironvellum.app.domain.ExerciseSetEntry
import com.ironvellum.app.domain.SetRecords
import com.ironvellum.app.domain.Skills
import com.ironvellum.app.domain.fmt
import com.ironvellum.app.ui.components.ExercisePickerPanel
import com.ironvellum.app.ui.components.InkChip
import com.ironvellum.app.ui.components.InkDivider
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.components.InkRowPanel
import com.ironvellum.app.ui.components.ListRow
import com.ironvellum.app.ui.components.PushedHeader
import com.ironvellum.app.ui.components.TrendChart
import com.ironvellum.app.ui.components.formatDate
import com.ironvellum.app.ui.components.formatLoadKg
import com.ironvellum.app.ui.components.plural
import com.ironvellum.app.ui.ironvellumRepository
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.IronvellumTracking
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.util.Locale

data class ExplorerUi(
    val exercises: List<Exercise> = emptyList(),
    val selected: Exercise? = null,
    val history: ExerciseHistory? = null,
    // Best-ever performance per set position (0-based setIndex) of the selected movement.
    val setRecords: Map<Int, SetRecords.Record> = emptyMap(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class ExerciseExplorerViewModel(private val repo: Repository) : ViewModel() {

    // Bodyweight history comes from the same stat feed the session screen uses; no second path.
    private val statsFlow = repo.observeStats()

    private val selected = MutableStateFlow<Exercise?>(null)

    val ui: StateFlow<ExplorerUi> = combine(
        repo.observeExercises(),
        selected,
        selected.flatMapLatest { ex ->
            if (ex == null) flowOf(null) else repo.observeExerciseHistory(ex.id)
        },
        repo.observeHistory(),
        statsFlow,
    ) { exercises, sel, history, allHistory, stats ->
        val records = if (sel == null) {
            emptyMap()
        } else {
            SetRecords.records(allHistory, SetRecords.bodyweightLookup(stats)) { sel.metric }
                .filterKeys { (name, _) -> name.equals(sel.name, ignoreCase = true) }
                .mapKeys { (_, record) -> record.setIndex }
        }
        ExplorerUi(exercises, sel, history, records)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExplorerUi())

    fun pick(exercise: Exercise) {
        selected.value = exercise
    }

    /** Back to the list, so the lifter can choose another movement. */
    fun clear() {
        selected.value = null
    }
}

@Composable
fun ExerciseExplorerScreen(
    onBack: () -> Unit,
    initialName: String? = null,
    onOpenChronicle: () -> Unit = {},
    viewModel: ExerciseExplorerViewModel =
        viewModel(factory = viewModelFactory { initializer { ExerciseExplorerViewModel(ironvellumRepository()) } }),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    // Opened on a lift from the Ledger's peaks: select it once the list is in.
    var picked by rememberSaveable { mutableStateOf(initialName == null) }
    androidx.compose.runtime.LaunchedEffect(ui.exercises) {
        if (picked || ui.exercises.isEmpty()) return@LaunchedEffect
        ui.exercises.firstOrNull { it.name.equals(initialName, ignoreCase = true) }?.let(viewModel::pick)
        picked = true
    }

    val selectedExercise = ui.selected
    Column(
        Modifier
            .fillMaxSize()
            // The list scrolls itself and must fill what is left of the screen;
            // inside a scrolling column it could only take a fixed height.
            .then(if (selectedExercise != null) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(20.dp))

        if (selectedExercise == null) {
            PushedHeader("EXERCISES", onBack)
            Spacer(Modifier.height(12.dp))
            // The screen's own header names it and BACK leaves it, so this is
            // the bare list. No panel around it: its rows are already cards.
            ExercisePickerPanel(
                exercises = ui.exercises,
                onPick = viewModel::pick,
                modifier = Modifier.weight(1f),
            )
        } else {
            // The movement names the screen; Switch returns to the list.
            PushedHeader(selectedExercise.name, onBack) {
                InkChip("Switch", onClick = viewModel::clear)
            }
            val skill = Skills.forName(selectedExercise.name)
            Text(
                buildString {
                    append(selectedExercise.muscleGroup.name.lowercase().replaceFirstChar { it.uppercase() })
                    append(" · ")
                    append(if (selectedExercise.isWeighted) "weighted" else "bodyweight")
                    if (skill != null) append(" · technique tier ${Skills.tierLabel(skill.tier)}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
            )
            Spacer(Modifier.height(14.dp))

            val history = ui.history
            if (history == null || history.isEmpty) {
                Text(
                    "The Chronicle holds no sets for this yet. Seal a trial with it and they appear.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = IronvellumColors.InkMuted,
                )
            } else {
                PeakCard(history)
                Spacer(Modifier.height(10.dp))
                ScoreChart(history)
                if (ui.setRecords.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    SetRecordPanel(ui.setRecords, history.exercise.metric == ExerciseMetric.HOLD)
                }
                Spacer(Modifier.height(14.dp))
                Text("Recent sets", style = MaterialTheme.typography.titleMedium, color = IronvellumColors.Ink)
                Spacer(Modifier.height(8.dp))
                RecentSets(history, onOpenChronicle)
            }
        }
        Spacer(Modifier.height(if (selectedExercise == null) 8.dp else 96.dp))
    }
}

/** One set as the lifter reads it: "100 kg × 5", "BW × 12", or for a hold "45 s · 20 kg". */
internal fun setText(reps: Int, weightKg: Double?, hold: Boolean): String =
    if (hold) {
        "$reps s" + (weightKg?.let { " · ${formatLoadKg(it)} kg" } ?: "")
    } else {
        (weightKg?.let { "${formatLoadKg(it)} kg" } ?: "BW") + " × $reps"
    }

/** The short form for a line of sets: "100 × 5", "BW × 12", "45 s · 20". */
internal fun compactSet(reps: Int, weightKg: Double?, hold: Boolean): String =
    if (hold) "$reps s" + (weightKg?.let { " · ${formatLoadKg(it)}" } ?: "")
    else (weightKg?.let { formatLoadKg(it) } ?: "BW") + " × $reps"

/** The done set with the best strength score: the one the hero names. Null when none scored. */
internal fun peakSet(history: ExerciseHistory): ExerciseSetEntry? =
    history.entries.filter { it.done && it.score > 0.0 }.maxByOrNull { it.score }

/**
 * Trials per week over the span from the first trial to [nowMs], never shorter than a
 * week. Null below two trials: one trial has no rate, only a date.
 */
internal fun trialsPerWeek(trials: Int, firstMs: Long?, nowMs: Long): Double? {
    if (trials < 2 || firstMs == null) return null
    val weeks = ((nowMs - firstMs) / WEEK_MS.toDouble()).coerceAtLeast(1.0)
    return trials / weeks
}

private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000

/** One gold hero (the peak) over one card of 52dp stat rows. */
@Composable
private fun PeakCard(history: ExerciseHistory) {
    val hold = history.exercise.metric == ExerciseMetric.HOLD
    val peak = peakSet(history)
    val frequency = remember(history) { trialsPerWeek(history.sessions, history.firstLoggedAtMs, System.currentTimeMillis()) }
    InkRowPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp)) {
            Text(
                "PEAK",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = ChakraPetch,
                color = IronvellumColors.InkMuted,
                letterSpacing = IronvellumTracking.InlineLabel,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    // Gold is for an earned peak; with nothing scored there is no peak to name.
                    peak?.let { setText(it.reps, it.weightKg, hold) } ?: "—",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (peak != null) IronvellumColors.SovereignGold else IronvellumColors.InkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (peak != null) {
                    Text(
                        "score ${formatScore(history.bestScore)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = IronvellumColors.InkMuted,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 8.dp, bottom = 5.dp),
                    )
                }
            }
        }
        val heavyKg = history.heaviestWeightKg
        // Only when it is a different set from the peak; otherwise it repeats the hero.
        if (heavyKg != null && history.completedSets > 0 &&
            (peak == null || heavyKg != peak.weightKg || history.heaviestReps != peak.reps)
        ) {
            InkDivider()
            ListRow("Heaviest", value = setText(history.heaviestReps, heavyKg, hold))
        }
        if (hold) {
            InkDivider()
            ListRow("Time held", value = "${"%,d".fmt(history.totalReps)} s")
        } else {
            history.totalVolumeKg?.let { volume ->
                InkDivider()
                ListRow("Volume", subline = "All time", value = "${"%,d".fmt(Math.round(volume))} kg")
            }
        }
        frequency?.let {
            InkDivider()
            ListRow("Frequency", value = String.format(Locale.US, "%.1f a week", it))
        }
        InkDivider()
        ListRow("Trials", value = history.sessions.toString())
    }
}

private fun formatScore(v: Double): String =
    if (v >= 100.0) v.toInt().toString() else String.format(Locale.US, "%.1f", v)

/** One flat line of best-set score per trial; gold only on the peak point, which the chart draws. */
@Composable
private fun ScoreChart(history: ExerciseHistory) {
    val values = history.series.map { it.bestSetScore }
    InkPanel(Modifier.fillMaxWidth()) {
        Text("Strength per trial", style = MaterialTheme.typography.titleSmall, color = IronvellumColors.Ink)
        Text(
            if (values.size < 2) "Best set score" else
                "Best set score, ${values.size} ${plural(values.size, "trial", "trials")}",
            style = MaterialTheme.typography.bodySmall,
            color = IronvellumColors.InkMuted,
        )
        if (values.size < 2) {
            Spacer(Modifier.height(8.dp))
            Text(
                "One trial written — a trend needs at least two.",
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
            )
        } else {
            // Scores sit in a narrow band, so this one scales to its own span.
            TrendChart(
                values,
                fromZero = false,
                modifier = Modifier.fillMaxWidth().height(110.dp).padding(top = 8.dp),
                startLabel = formatDate(history.series.first().atMs, "d MMM"),
                endLabel = formatDate(history.series.last().atMs, "d MMM"),
                valueText = { "%.1f score".fmt(it) },
                dateText = { formatDate(history.series[it].atMs, "d MMM") },
            )
        }
    }
}

/** A folded row: the standing best for each set position of the movement, opened on tap. */
@Composable
private fun SetRecordPanel(records: Map<Int, SetRecords.Record>, isHold: Boolean) {
    var open by rememberSaveable { mutableStateOf(false) }
    InkRowPanel(Modifier.fillMaxWidth()) {
        ListRow(
            "Peaks by set",
            subline = if (isHold) "Best hold for each set number" else "Best weight × reps for each set number",
            onClick = { open = !open },
        )
        if (open) {
            records.keys.sorted().forEach { setIndex ->
                val record = records[setIndex] ?: return@forEach
                InkDivider()
                ListRow(
                    "Set ${setIndex + 1}",
                    subline = formatDate(record.achievedAtMs, "d MMM yyyy"),
                    value = setText(record.reps, record.weightKg, isHold),
                )
            }
        }
    }
}

/** The latest few days of sets in one card, then the way into the whole Chronicle. */
@Composable
private fun RecentSets(history: ExerciseHistory, onOpenChronicle: () -> Unit) {
    val hold = history.exercise.metric == ExerciseMetric.HOLD
    val days = history.entries
        .filter { it.done }
        .sortedWith(compareByDescending<ExerciseSetEntry> { it.atMs }.thenBy { it.setIndex })
        .groupBy { formatDate(it.atMs, "EEE d MMM") }
        .entries.take(RECENT_DAYS)
    InkRowPanel(Modifier.fillMaxWidth()) {
        days.forEach { (day, sets) ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(day, style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted)
                Text(
                    sets.joinToString(" · ") { compactSet(it.reps, it.weightKg, hold) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = IronvellumColors.Ink,
                )
            }
        }
        InkDivider()
        ListRow("Full chronicle", onClick = onOpenChronicle)
    }
}

/** Recent training days; the Chronicle holds the rest. */
private const val RECENT_DAYS = 3
